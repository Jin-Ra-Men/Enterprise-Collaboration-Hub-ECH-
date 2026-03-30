/* ==========================================================================
 * ECH Frontend — app.js  (Slack-inspired)
 * - 로그인/로그아웃 (JWT, sessionStorage)
 * - 사이드바: 내 채널 목록 / DM 목록
 * - 채팅: 메시지 내역 로드 + Socket.io 실시간 수신/전송
 * - 파일 첨부 업로드 (현재 채널 기반)
 * - 채널/DM 만들기 (조직도 사용자 검색)
 * - 관리자 전용: 배포 관리, 앱 설정
 * ========================================================================== */

const API_BASE   = "http://localhost:8080";
const SOCKET_URL = "http://localhost:3001";
const TOKEN_KEY  = "ech_token";
const USER_KEY   = "ech_user";
const WS_KEY     = "ECH"; // 기본 워크스페이스 키
const MAX_MSGS   = 300;
const THEME_KEY  = "ech_theme";
const VALID_THEMES = ["dark", "light", "blue"];

function getCurrentTheme() {
  return document.documentElement.getAttribute("data-theme") || "dark";
}

function syncThemeOptions() {
  const cur = getCurrentTheme();
  document.querySelectorAll(".theme-option-btn").forEach((btn) => {
    btn.classList.toggle("active", btn.dataset.theme === cur);
  });
}

function applyTheme(theme, { persistLocal = true } = {}) {
  const t = VALID_THEMES.includes(theme) ? theme : "dark";
  if (t === "dark") {
    document.documentElement.removeAttribute("data-theme");
  } else {
    document.documentElement.setAttribute("data-theme", t);
  }
  if (persistLocal) {
    try {
      localStorage.setItem(THEME_KEY, t);
    } catch (e) {
      /* ignore */
    }
  }
  syncThemeOptions();
}

function initTheme() {
  let saved = "dark";
  try {
    saved = localStorage.getItem(THEME_KEY) || "dark";
  } catch (e) {
    /* ignore */
  }
  if (!VALID_THEMES.includes(saved)) saved = "dark";
  applyTheme(saved);
}

async function saveThemePreference(theme) {
  if (!currentUser) return;
  try {
    const res = await apiFetch("/api/auth/me/theme", {
      method: "PUT",
      body: JSON.stringify({ theme }),
    });
    if (!res.ok) {
      const json = await res.json().catch(() => ({}));
      alert(json.error?.message || "테마 저장에 실패했습니다.");
    }
  } catch (e) {
    console.error("테마 저장 실패", e);
    alert("테마 저장 중 오류가 발생했습니다.");
  }
}

/** employeeNo(string) -> ONLINE | AWAY | OFFLINE */
const presenceByEmployeeNo = new Map();

/* ── 전역 상태 ── */
let socket         = null;
let currentUser    = null;
let activeChannelId = null;
let activeChannelType = null; // PUBLIC / PRIVATE / DM
let pendingFile    = null;
let selectedMembers = [];     // 채널/DM 생성 시 선택된 사용자
let selectedDmMembers = [];
let selectedAddMembers = [];  // 기존 채널에 추가할 사용자
let orgPickerContext = null;  // member | dm | channelMember
let orgPickerEmbedElId = null; // member/dm/channelMember 조직도 체크박스가 그려진 엘리먼트 id
/** 프로필 모달에 표시 중인 사용자 사번 (DM 보내기용) */
let profileViewEmployeeNo = null;

/* ── DOM 참조 ── */
const loginPage      = document.getElementById("loginPage");
const mainApp        = document.getElementById("mainApp");
const loginForm      = document.getElementById("loginForm");
const loginErrorEl   = document.getElementById("loginError");
const loginBtn       = document.getElementById("loginBtn");
const logoutBtn      = document.getElementById("logoutBtn");
const themeSettingsBtn = document.getElementById("themeSettingsBtn");
const sidebarAvatar  = document.getElementById("sidebarAvatar");
const sidebarUserName = document.getElementById("sidebarUserName");
const channelListEl  = document.getElementById("channelList");
const dmListEl       = document.getElementById("dmList");
const messagesEl     = document.getElementById("messages");
const messageInputEl = document.getElementById("messageInput");

/* ==========================================================================
 * 유틸
 * ========================================================================== */
function getToken()  { return sessionStorage.getItem(TOKEN_KEY); }
function getUser()   { const r = sessionStorage.getItem(USER_KEY); return r ? JSON.parse(r) : null; }
function saveSession(token, user) {
  sessionStorage.setItem(TOKEN_KEY, token);
  sessionStorage.setItem(USER_KEY, JSON.stringify(user));
}
function clearSession() {
  sessionStorage.removeItem(TOKEN_KEY);
  sessionStorage.removeItem(USER_KEY);
}

/** 인증된 다운로드로 만든 이미지 blob URL (채널 전환·목록 갱신 시 revoke) */
const imageAttachmentBlobUrls = new Map();

function revokeImageAttachmentBlobUrls() {
  imageAttachmentBlobUrls.forEach((url) => {
    try {
      URL.revokeObjectURL(url);
    } catch {
      /* ignore */
    }
  });
  imageAttachmentBlobUrls.clear();
}

let pendingComposerPreviewUrl = null;
let imageLightboxEscapeBound = false;

async function apiFetch(path, opts = {}) {
  const token = getToken();
  const headers = { "Content-Type": "application/json", ...(opts.headers || {}) };
  if (token) headers["Authorization"] = `Bearer ${token}`;
  return fetch(`${API_BASE}${path}`, { ...opts, headers });
}

function escHtml(s) {
  return String(s)
    .replace(/&/g,"&amp;").replace(/</g,"&lt;").replace(/>/g,"&gt;")
    .replace(/"/g,"&quot;");
}

/** 채팅 메시지 옆 시각 — 24시간제 HH:mm (오전/오후 문구 없음) */
function fmtTime(iso) {
  if (!iso) return "";
  const d = new Date(iso);
  const h = String(d.getHours()).padStart(2, "0");
  const m = String(d.getMinutes()).padStart(2, "0");
  return `${h}:${m}`;
}

function fmtDate(iso) {
  if (!iso) return "-";
  return new Date(iso).toLocaleString("ko-KR");
}

function fmtSize(bytes) {
  if (!bytes) return "0 B";
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024*1024) return `${(bytes/1024).toFixed(1)} KB`;
  return `${(bytes/1024/1024).toFixed(1)} MB`;
}

function presenceCssClass(status) {
  const s = String(status || "").toUpperCase();
  if (s === "ONLINE") return "presence-online";
  if (s === "AWAY") return "presence-away";
  return "presence-offline";
}

function presenceTitle(status) {
  const s = String(status || "").toUpperCase();
  if (s === "ONLINE") return "온라인";
  if (s === "AWAY") return "자리비움";
  return "오프라인";
}

async function fetchPresenceSnapshot() {
  try {
    const res = await fetch(`${SOCKET_URL}/presence`);
    const json = await res.json();
    (json.data || []).forEach((p) => {
      const emp = String(p.employeeNo || "").trim();
      if (emp) presenceByEmployeeNo.set(emp, String(p.status || "OFFLINE").toUpperCase());
    });
  } catch (e) {
    console.warn("프레즌스 스냅샷 실패", e);
  }
}

function refreshPresenceDots() {
  document.querySelectorAll("[data-presence-user]").forEach((el) => {
    const emp = String(el.dataset.presenceUser || "").trim();
    const st = presenceByEmployeeNo.get(emp) || "OFFLINE";
    el.className = `presence-dot ${presenceCssClass(st)}`;
    el.title = presenceTitle(st);
  });
}

async function openUserProfile(employeeNo) {
  const emp = String(employeeNo || "").trim();
  if (!emp || !currentUser) return;
  try {
    const res  = await apiFetch(`/api/users/profile?employeeNo=${encodeURIComponent(emp)}`);
    const json = await res.json();
    if (!res.ok) {
      alert(json.error?.message || "프로필을 불러올 수 없습니다.");
      return;
    }
    const u = json.data;
    profileViewEmployeeNo = u.employeeNo != null ? String(u.employeeNo).trim() : emp;
    document.getElementById("profileModalName").textContent = u.name || "-";
    document.getElementById("profileAvatarLg").textContent = avatarInitials(u.name || "?");
    document.getElementById("profileModalEmpNo").textContent = u.employeeNo || "-";
    document.getElementById("profileModalEmail").textContent = u.email || "-";
    document.getElementById("profileModalDept").textContent = u.department || "-";
    const jl = u.jobLevel != null && String(u.jobLevel).trim() !== "";
    document.getElementById("profileModalJobLevel").textContent = jl ? String(u.jobLevel).trim() : "-";
    const posDt = document.getElementById("profileJobPositionDt");
    const posDd = document.getElementById("profileModalJobPosition");
    const hasPos = u.jobPosition != null && String(u.jobPosition).trim() !== "";
    if (posDt && posDd) {
      if (hasPos) {
        posDt.classList.remove("hidden");
        posDd.classList.remove("hidden");
        posDd.textContent = String(u.jobPosition).trim();
      } else {
        posDt.classList.add("hidden");
        posDd.classList.add("hidden");
        posDd.textContent = "";
      }
    }
    const titleDt = document.getElementById("profileJobTitleDt");
    const titleDd = document.getElementById("profileModalJobTitle");
    const hasTitle = u.jobTitle != null && String(u.jobTitle).trim() !== "";
    if (titleDt && titleDd) {
      if (hasTitle) {
        titleDt.classList.remove("hidden");
        titleDd.classList.remove("hidden");
        titleDd.textContent = String(u.jobTitle).trim();
      } else {
        titleDt.classList.add("hidden");
        titleDd.classList.add("hidden");
        titleDd.textContent = "";
      }
    }
    const dmBtn = document.getElementById("btnProfileDm");
    if (dmBtn) {
      const self = String(profileViewEmployeeNo) === String(currentUser.employeeNo || "").trim();
      dmBtn.disabled = self;
      dmBtn.title = self ? "자기 자신과는 DM을 시작할 수 없습니다." : "";
    }
    openModal("modalUserProfile");
  } catch (e) {
    console.error(e);
    alert("프로필 요청 중 오류가 발생했습니다.");
  }
}

/** 프로필·기타에서 동일 플로우로 DM 채널 생성 후 입장 */
async function startDmWithUser(peerEmployeeNo, displayName) {
  if (!currentUser) return;
  const peer = String(peerEmployeeNo || "").trim();
  if (!peer) return;
  if (peer === String(currentUser.employeeNo || "").trim()) {
    alert("자기 자신과는 DM을 할 수 없습니다.");
    return;
  }
  const dmName =
    displayName && displayName !== "-" ? displayName : peer;
  try {
    const res = await apiFetch("/api/channels", {
      method: "POST",
      body: JSON.stringify({
        workspaceKey: WS_KEY,
        name: dmName,
        description: dmName,
        channelType: "DM",
        createdByEmployeeNo: currentUser.employeeNo,
        dmPeerEmployeeNos: [peer],
      }),
    });
    const json = await res.json();
    if (!res.ok) {
      alert("DM 생성 실패: " + (json.error?.message || ""));
      return;
    }
    const channelId = json.data?.channelId;
    closeModal("modalUserProfile");
    await loadMyChannels();
    selectChannel(channelId, dmName, "DM");
  } catch (e) {
    console.error(e);
    alert("DM 생성 중 오류 발생");
  }
}

async function loadChannelFiles(channelId) {
  const listEl = document.getElementById("channelFilesList");
  const emptyEl = document.getElementById("channelFilesEmpty");
  if (!currentUser || !listEl) return;
  listEl.innerHTML = "";
  if (emptyEl) emptyEl.classList.add("hidden");
  try {
    const res  = await apiFetch(`/api/channels/${channelId}/files?employeeNo=${encodeURIComponent(currentUser.employeeNo)}`);
    const json = await res.json();
    if (!res.ok) return;
    const files = json.data || [];
    if (files.length === 0) {
      if (emptyEl) emptyEl.classList.remove("hidden");
      return;
    }
    files.forEach((f) => {
      const li = document.createElement("li");
      li.className = "channel-file-item";
      const who = f.uploaderName ? escHtml(f.uploaderName) : `emp#${f.uploadedByEmployeeNo}`;
      li.innerHTML = `
        <span class="channel-file-icon">📎</span>
        <span class="channel-file-meta">
          <span class="channel-file-name">${escHtml(f.originalFilename || "")}</span>
          <span class="channel-file-sub">${who} · ${fmtSize(f.sizeBytes)} · ${fmtDate(f.createdAt)}</span>
        </span>
        <button type="button" class="btn-channel-file-dl" data-file-id="${f.id}">다운로드</button>`;
      li.querySelector(".btn-channel-file-dl").addEventListener("click", () => {
        downloadChannelFile(f.id, f.originalFilename);
      });
      listEl.appendChild(li);
    });
  } catch (e) {
    console.error("첨부 목록 로드 실패", e);
  }
}

async function downloadChannelFile(fileId, filename, channelId) {
  const ch = channelId != null ? channelId : activeChannelId;
  if (!ch || !currentUser) return;
  try {
    const res = await fetch(
      `${API_BASE}/api/channels/${ch}/files/${fileId}/download?employeeNo=${encodeURIComponent(currentUser.employeeNo)}`,
      { headers: { Authorization: `Bearer ${getToken()}` } }
    );
    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      const code = err.error?.code;
      const msg = err.error?.message;
      alert(
        msg ||
          (code === "FILE_IO_ERROR"
            ? "파일을 읽는 중 오류가 발생했습니다."
            : "다운로드에 실패했습니다.")
      );
      return;
    }
    const blob = await res.blob();
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = filename || "download";
    a.click();
    URL.revokeObjectURL(url);
  } catch (e) {
    console.error(e);
    alert("다운로드 중 오류가 발생했습니다.");
  }
}

const ORG_EMBED_IDS = {
  member: "orgTreeEmbedMember",
  dm: "orgTreeEmbedDm",
  channelMember: "orgTreeEmbedAdd",
};

let orgPickerCompanies = [];
let orgPickerTeamIndex = new Map(); // key -> { companyName, divisionName, teamName, users }
let orgPickerSelectedTeamKey = null;

function buildTeamKey(companyName, divisionName, teamName) {
  return `${companyName}||${divisionName}||${teamName}`;
}

function normalizeSearchKeyword(keyword) {
  return (keyword || "").toString().trim();
}

function matchUserForSearch(user, searchType, keyword) {
  const kw = normalizeSearchKeyword(keyword).toLowerCase();
  if (!kw) return true;

  const name = (user?.name || "").toLowerCase();
  const department = (user?.department || "").toLowerCase();
  const email = (user?.email || "").toLowerCase();
  const empNo = (user?.employeeNo || "").toLowerCase();

  switch (searchType) {
    case "NAME":
      return name.includes(kw);
    case "DEPARTMENT":
      return department.includes(kw);
    case "EMAIL":
      return email.includes(kw);
    case "EMP_NO":
      return empNo.includes(kw);
    default:
      return true;
  }
}

function filterUsers(users, searchType, keyword) {
  const kw = normalizeSearchKeyword(keyword);
  if (!kw) return users;
  return (users || []).filter((u) => matchUserForSearch(u, searchType, kw));
}

function getMemberPickerRightSearchType() {
  return document.getElementById("addMemberTopSearchType")?.value || "NAME";
}
function getMemberPickerRightSearchKeyword() {
  return document.getElementById("addMemberTopSearchInput")?.value || "";
}

function ensureOrgCompanySelectListener() {
  const sel = document.getElementById("companySelect");
  if (!sel || sel.dataset.echOrgBound) return;
  sel.dataset.echOrgBound = "1";
  sel.addEventListener("change", () => {
    loadOrgTree(orgPickerContext, orgPickerEmbedElId);
  });
}

function buildOrganizationTreeUrlByCompanyGroupCode(companyGroupCode) {
  const code = companyGroupCode === undefined || companyGroupCode === null
    ? "ORGROOT"
    : String(companyGroupCode);
  if (!code || code === "ORGROOT") return "/api/user-directory/organization";
  const trimmed = code.trim();
  if (!trimmed) return "/api/user-directory/organization";
  return `/api/user-directory/organization?companyGroupCode=${encodeURIComponent(trimmed)}`;
}

/** DB 기반 회사 셀렉트 — org_groups(COMPANY) 기준 옵션 */
async function loadOrganizationCompanyFiltersIntoSelect() {
  const sel = document.getElementById("companySelect");
  if (!sel) return;
  const previous = sel.value || "ORGROOT";
  sel.innerHTML = '<option value="ORGROOT">불러오는 중…</option>';
  try {
    const res = await apiFetch("/api/user-directory/organization-filters");
    const json = await res.json();
    if (!res.ok) {
      throw new Error(json.error?.message || "회사 목록을 불러오지 못했습니다.");
    }
    const opts = json.data?.options || [];
    sel.innerHTML = "";
    opts.forEach((o) => {
      const opt = document.createElement("option");
      opt.value = o.companyGroupCode == null ? "ORGROOT" : String(o.companyGroupCode);
      opt.textContent = o.label || "";
      sel.appendChild(opt);
    });
    const valid = Array.from(sel.options).some((op) => op.value === previous);
    sel.value = valid ? previous : "ORGROOT";
  } catch (e) {
    console.error(e);
    sel.innerHTML = "";
    const o = document.createElement("option");
    o.value = "ORGROOT";
    o.textContent = "전체 (그룹사 공용)";
    sel.appendChild(o);
  }
}

function renderOrgTreeLeft() {
  const treeEl = document.getElementById("orgTreeEmbedAdd");
  if (!treeEl) return;
  treeEl.innerHTML = "";
  treeEl.className = "org-tree-embedded ech-ui-tree";

  if (!orgPickerCompanies.length) {
    treeEl.innerHTML = '<p class="empty-notice">표시할 조직이 없습니다.</p>';
    return;
  }

  const selectedKey = orgPickerSelectedTeamKey;
  const scroll = document.createElement("div");
  scroll.className = "ech-tree-scroll";
  const root = document.createElement("div");
  root.className = "ech-tree-root";

  orgPickerCompanies.forEach((co) => {
    const coBlock = document.createElement("div");
    coBlock.className = "ech-tree-co";

    const row1 = document.createElement("div");
    row1.className = "ech-tree-row ech-tree-row--lv1";
    row1.innerHTML = `
      <span class="ech-tree-line ech-tree-line--root" aria-hidden="true"></span>
      <span class="ech-tree-ico ech-tree-ico--building" aria-hidden="true"></span>
      <span class="ech-tree-label">${escHtml(co.name)}</span>`;
    coBlock.appendChild(row1);

    const divWrap = document.createElement("div");
    divWrap.className = "ech-tree-co-children";

    (co.divisions || []).forEach((div) => {
      const det = document.createElement("details");
      det.className = "ech-tree-details ech-tree-details--div";
      det.open = true;
      const sum = document.createElement("summary");
      sum.className = "ech-tree-summary ech-tree-row ech-tree-row--lv2";
      sum.innerHTML = `
        <span class="ech-tree-line ech-tree-line--branch" aria-hidden="true"></span>
        <span class="ech-tree-ico ech-tree-ico--sitemap" aria-hidden="true"></span>
        <span class="ech-tree-label">${escHtml(div.name)}</span>`;
      det.appendChild(sum);

      const teamBox = document.createElement("div");
      teamBox.className = "ech-tree-div-body";

      (div.teams || []).forEach((team) => {
        const teamKey = buildTeamKey(co.name, div.name, team.name);
        const btn = document.createElement("button");
        btn.type = "button";
        btn.className = `ech-tree-row ech-tree-row--lv3 ech-tree-team${teamKey === selectedKey ? " is-selected" : ""}`;
        btn.dataset.teamKey = teamKey;
        btn.innerHTML = `
          <span class="ech-tree-line ech-tree-line--branch" aria-hidden="true"></span>
          <span class="ech-tree-ico ech-tree-ico--users" aria-hidden="true"></span>
          <span class="ech-tree-label">${escHtml(team.name)}</span>
          `;
        btn.addEventListener("click", () => {
          orgPickerSelectedTeamKey = teamKey;
          renderOrgTreeLeft();
          renderMemberListRight();
        });
        teamBox.appendChild(btn);
      });

      det.appendChild(teamBox);
      divWrap.appendChild(det);
    });

    coBlock.appendChild(divWrap);
    root.appendChild(coBlock);
  });

  scroll.appendChild(root);
  treeEl.appendChild(scroll);
}

function renderMemberListRight() {
  const listEl = document.getElementById("addMemberMemberList");
  if (!listEl) return;

  listEl.innerHTML = "";

  const rightType = getMemberPickerRightSearchType();
  const rightKeyword = getMemberPickerRightSearchKeyword();
  const hasKeyword = !!normalizeSearchKeyword(rightKeyword);

  let candidateUsers = [];
  if (hasKeyword) {
    // 검색어가 있으면 "선택된 회사" 범위 내 전체 팀 구성원을 대상으로 검색한다.
    // (loadOrgTree는 companySelect로 필터된 company 트리만 내려주므로 여기서 team.users를 합치면 된다.)
    const byEmployeeNo = new Map();
    for (const [, team] of orgPickerTeamIndex.entries()) {
      for (const u of (team.users || [])) {
        const e = String(u.employeeNo || "").trim();
        if (e) byEmployeeNo.set(e, u);
      }
    }
    candidateUsers = Array.from(byEmployeeNo.values());
  } else {
    if (!orgPickerSelectedTeamKey || !orgPickerTeamIndex.has(orgPickerSelectedTeamKey)) {
      listEl.innerHTML = '<li class="empty-notice">부서를 선택하세요.</li>';
      return;
    }
    const team = orgPickerTeamIndex.get(orgPickerSelectedTeamKey);
    candidateUsers = (team.users || []);
  }

  const allUsers = candidateUsers.filter(
    (u) => String(u.employeeNo || "").trim() !== String(currentUser?.employeeNo || "").trim()
  );
  const filtered = filterUsers(allUsers, rightType, rightKeyword);

  if (!filtered.length) {
    const li = document.createElement("li");
    li.className = "empty-notice";
    li.textContent = rightKeyword ? "검색 결과가 없습니다." : "선택 부서원 목록이 비어 있습니다.";
    listEl.appendChild(li);
    return;
  }

  filtered
    .slice()
    .sort((a, b) => String(a.name || "").localeCompare(String(b.name || ""), "ko-KR"))
    .forEach((u) => {
      const emp = String(u.employeeNo || "").trim();
      const selected = isUserAlreadySelected(emp, orgPickerContext);
      const item = document.createElement("li");
      item.className = "member-picker-member-item";
      item.innerHTML = `
        <div class="member-picker-member-main">
          <div class="member-picker-member-name">${escHtml(u.name || "")}</div>
          <div class="member-picker-member-sub">${escHtml(u.department || "")}${u.email ? " · " + escHtml(u.email) : ""}</div>
          <div class="member-picker-member-sub">${escHtml("사번: " + (u.employeeNo || ""))}</div>
        </div>
        <button type="button" class="member-picker-member-btn" data-employee-no="${escHtml(emp)}">
          ${selected ? "제외" : "추가"}
        </button>
      `;
      const btn = item.querySelector("button[data-employee-no]");
      btn.addEventListener("click", () => {
        if (selected) removeSelectedMember(emp, orgPickerContext);
        else addSelectedMember(u, orgPickerContext);
        renderMemberListRight();
      });
      listEl.appendChild(item);
    });
}

function renderPickerSelectedMembers(context) {
  const wrap = document.getElementById("pickerSelectedMembersWrap");
  if (!wrap) return;
  wrap.innerHTML = "";

  const listRef = context === "dm"
    ? selectedDmMembers
    : (context === "channelMember" ? selectedAddMembers : selectedMembers);

  (listRef || []).forEach((user) => {
    const emp = String(user.employeeNo || "").trim();
    const tag = document.createElement("span");
    tag.className = "member-tag";
    tag.dataset.uid = emp;
    tag.innerHTML = `${escHtml(user.name)} <button type="button" data-uid="${escHtml(emp)}">✕</button>`;
    tag.querySelector("button").addEventListener("click", () => {
      removeSelectedMember(emp, context);
    });
    wrap.appendChild(tag);
  });
}

async function loadOrgTree(context, embedElIdOverride = null) {
  orgPickerContext = context;

  // 이 팝업은 항상 `orgTreeEmbedAdd` / `addMemberMemberList`를 사용합니다.
  orgPickerEmbedElId = embedElIdOverride ?? "orgTreeEmbedAdd";

  orgPickerTeamIndex = new Map();
  orgPickerCompanies = [];
  orgPickerSelectedTeamKey = null;

  // 상단 검색 초기화
  const topInput = document.getElementById("addMemberTopSearchInput");
  const topTypeEl = document.getElementById("addMemberTopSearchType");
  if (topInput) topInput.value = "";
  if (topTypeEl) topTypeEl.value = "NAME";

  ensureOrgCompanySelectListener();
  await loadOrganizationCompanyFiltersIntoSelect();

  try {
    const rawSel = document.getElementById("companySelect")?.value || "ORGROOT";
    const res = await apiFetch(buildOrganizationTreeUrlByCompanyGroupCode(rawSel));
    const json = await res.json();
    if (!res.ok) {
      const treeEl = document.getElementById("orgTreeEmbedAdd");
      if (treeEl) treeEl.innerHTML = `<p class="empty-notice">${escHtml(json.error?.message || "오류")}</p>`;
      return;
    }

    const root = json.data || {};
    const companies = root.companies || [];
    orgPickerCompanies = companies;

    if (!orgPickerCompanies.length) {
      orgPickerSelectedTeamKey = null;
      renderOrgTreeLeft();
      renderMemberListRight();
      return;
    }

    // team(users) 인덱싱
    orgPickerCompanies.forEach((co) => {
      (co.divisions || []).forEach((div) => {
        (div.teams || []).forEach((team) => {
          const key = buildTeamKey(co.name, div.name, team.name);
          orgPickerTeamIndex.set(key, {
            companyName: co.name,
            divisionName: div.name,
            teamName: team.name,
            users: (team.users || []).map((u) => ({
              ...u,
              email: u.email || "",
              department: u.department || "",
              employeeNo: u.employeeNo || "",
            })),
          });
        });
      });
    });

    // 기본 선택: 현재 사용자의 속한 팀(부서)으로 맞춤
    const myEmp = String(currentUser?.employeeNo || "").trim();
    let defaultKey = null;
    for (const [key, team] of orgPickerTeamIndex.entries()) {
      const hasMe = (team.users || []).some(
        (u) => String(u.employeeNo || "").trim() === myEmp && myEmp !== ""
      );
      if (hasMe) {
        defaultKey = key;
        break;
      }
    }
    if (!defaultKey && currentUser?.department) {
      const myDept = String(currentUser.department);
      for (const [key, team] of orgPickerTeamIndex.entries()) {
        const teamName = String(team.teamName || "");
        if (teamName === myDept) {
          defaultKey = key;
          break;
        }
      }
    }
    if (!defaultKey) defaultKey = orgPickerTeamIndex.keys().next().value || null;
    orgPickerSelectedTeamKey = defaultKey;

    renderOrgTreeLeft();
    renderMemberListRight();
    renderPickerSelectedMembers(context);
  } catch (e) {
    console.error(e);
    const treeEl = document.getElementById("orgTreeEmbedAdd");
    if (treeEl) treeEl.innerHTML = '<p class="empty-notice">조직도를 불러오지 못했습니다.</p>';
    const listEl = document.getElementById("addMemberMemberList");
    if (listEl) listEl.innerHTML = '';
  }
}

function isUserAlreadySelected(employeeNo, context) {
  const ref = context === "dm"
    ? selectedDmMembers
    : (context === "channelMember" ? selectedAddMembers : selectedMembers);
  const want = String(employeeNo || "").trim();
  return ref.some((u) => String(u.employeeNo || "").trim() === want);
}

function syncOrgCheckbox(employeeNo, context, checked) {
  const id = orgPickerEmbedElId ?? ORG_EMBED_IDS[context];
  if (!id) return;
  const root = document.getElementById(id);
  if (!root) return;
  const want = String(employeeNo || "").trim();
  root.querySelectorAll(".org-user-checkbox").forEach((cb) => {
    if (String(cb.getAttribute("data-employee-no") || "").trim() === want) {
      cb.checked = checked;
    }
  });
}

function avatarInitials(name) {
  if (!name) return "?";
  const parts = name.trim().split(/\s+/);
  return parts.length >= 2
    ? (parts[0][0] + parts[1][0]).toUpperCase()
    : name.slice(0,2).toUpperCase();
}

/* ==========================================================================
 * 화면 전환
 * ========================================================================== */
function showLogin() {
  loginPage.classList.remove("hidden");
  mainApp.classList.add("hidden");
  document.getElementById("loginId").value = "";
  document.getElementById("loginPassword").value = "";
  hideLoginError();
}

function showMain(user) {
  currentUser = user;
  loginPage.classList.add("hidden");
  mainApp.classList.remove("hidden");
  const preferredTheme = VALID_THEMES.includes(user?.themePreference)
    ? user.themePreference
    : (localStorage.getItem(THEME_KEY) || "dark");
  applyTheme(preferredTheme);

  sidebarUserName.textContent = `${user.name}`;
  sidebarAvatar.textContent = avatarInitials(user.name);

  // 로그인 계정이 바뀌어도 정확한 권한 상태를 반영하도록 항상 초기화 후 설정
  document.getElementById("adminSection").classList.add("hidden");
  if (user.role === "ADMIN") {
    document.getElementById("adminSection").classList.remove("hidden");
  }

  initSocket();
  loadMyChannels();
  initEvents();
}

function showView(viewId) {
  ["viewWelcome","viewChat","viewReleases","viewSettings"].forEach(id => {
    const el = document.getElementById(id);
    if (el) el.classList.add("hidden");
  });
  const target = document.getElementById(viewId);
  if (target) target.classList.remove("hidden");
}

/* ==========================================================================
 * 로그인 / 로그아웃
 * ========================================================================== */
loginForm.addEventListener("submit", async (e) => {
  e.preventDefault();
  hideLoginError();
  const loginId  = document.getElementById("loginId").value.trim();
  const password = document.getElementById("loginPassword").value;
  if (!loginId || !password) { showLoginError("사원번호/이메일과 비밀번호를 입력하세요."); return; }

  loginBtn.disabled  = true;
  loginBtn.textContent = "로그인 중...";
  try {
    const res  = await fetch(`${API_BASE}/api/auth/login`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ loginId, password }),
    });
    const json = await res.json();
    if (!res.ok || json.success === false) {
      showLoginError(json.error?.message || "로그인 실패");
      return;
    }
    const { token, ...user } = json.data;
    saveSession(token, user);
    showMain(user);
  } catch {
    showLoginError("서버에 연결할 수 없습니다.");
  } finally {
    loginBtn.disabled  = false;
    loginBtn.textContent = "로그인";
  }
});

logoutBtn.addEventListener("click", () => {
  if (!confirm("로그아웃 하시겠습니까?")) return;
  if (socket) { socket.disconnect(); socket = null; }
  revokeImageAttachmentBlobUrls();
  closeModal("modalImagePreview");
  clearFilePreview();
  activeChannelId = null;
  currentUser     = null;
  clearSession();

  // DOM 상태 완전 초기화 (다음 로그인 계정이 달라도 깨끗하게 시작)
  channelListEl.innerHTML = "";
  dmListEl.innerHTML      = "";
  messagesEl.innerHTML    = "";
  document.getElementById("adminSection").classList.add("hidden");
  showView("viewWelcome");
  showLogin();
});

themeSettingsBtn?.addEventListener("click", () => {
  syncThemeOptions();
  openModal("modalThemePicker");
});

function showLoginError(msg) {
  loginErrorEl.textContent = msg;
  loginErrorEl.classList.remove("hidden");
}
function hideLoginError() {
  loginErrorEl.textContent = "";
  loginErrorEl.classList.add("hidden");
}

/* ==========================================================================
 * 사이드바 채널 목록
 * ========================================================================== */
async function loadMyChannels() {
  if (!currentUser) return;
  try {
    const res  = await apiFetch(`/api/channels?employeeNo=${encodeURIComponent(currentUser.employeeNo)}`);
    const json = await res.json();
    if (!res.ok) return;
    const channels = json.data || [];
    renderChannelList(channels);
  } catch (err) {
    console.error("채널 목록 로드 실패", err);
  }
}

function renderChannelList(channels) {
  channelListEl.innerHTML = "";
  dmListEl.innerHTML      = "";

  channels.forEach(ch => {
    const li  = document.createElement("li");
    li.className = "sidebar-item channel-item";
    li.dataset.channelId   = ch.channelId;
    li.dataset.channelType = ch.channelType;
    const displayName =
      ch.channelType === "DM" && ch.description
        ? ch.description
        : ch.name;
    li.dataset.channelName = displayName;

    if (ch.channelType === "DM") {
      li.innerHTML = `<span class="item-icon">●</span><span class="item-label">${escHtml(displayName)}</span>`;
      dmListEl.appendChild(li);
    } else {
      const icon = ch.channelType === "PRIVATE" ? "🔒" : "#";
      li.innerHTML = `<span class="item-icon">${icon}</span><span class="item-label">${escHtml(ch.name)}</span>`;
      channelListEl.appendChild(li);
    }

    if (ch.channelId === activeChannelId) li.classList.add("active");

    li.addEventListener("click", () => selectChannel(ch.channelId, displayName, ch.channelType));
  });
}

/* ==========================================================================
 * 채널 선택 / 메시지 로드
 * ========================================================================== */
async function selectChannel(channelId, channelName, channelType) {
  revokeImageAttachmentBlobUrls();
  closeModal("modalImagePreview");
  activeChannelId   = channelId;
  activeChannelType = channelType;

  // 사이드바 active 표시
  document.querySelectorAll(".channel-item").forEach(el => {
    el.classList.toggle("active", Number(el.dataset.channelId) === channelId);
  });

  // 헤더 업데이트
  const prefix = channelType === "DM" ? "●" : (channelType === "PRIVATE" ? "🔒" : "#");
  document.getElementById("chatChannelPrefix").textContent = prefix;
  document.getElementById("chatChannelName").textContent   = channelName;
  document.getElementById("chatMemberCount").textContent   = "";
  document.getElementById("memberPanel").classList.add("hidden");
  document.getElementById("memberList").innerHTML = "";

  showView("viewChat");
  messagesEl.innerHTML = "";
  appendSystemMsg("메시지를 불러오는 중...");

  // 소켓 채널 입장
  joinSocketChannel(channelId);

  // 메시지 내역 로드
  await loadMessages(channelId);

  // 멤버 목록 로드
  loadChannelMembers(channelId);

  loadChannelFiles(channelId);

  messageInputEl.focus();
}

async function loadMessages(channelId) {
  if (!currentUser) return;
  revokeImageAttachmentBlobUrls();
  try {
    const res  = await apiFetch(`/api/channels/${channelId}/messages?employeeNo=${encodeURIComponent(currentUser.employeeNo)}&limit=50`);
    const json = await res.json();
    messagesEl.innerHTML = "";
    if (!res.ok) { appendSystemMsg("메시지 로드 실패: " + (json.error?.message || "")); return; }
    const msgs = json.data || [];
    if (msgs.length === 0) {
      appendSystemMsg("아직 메시지가 없습니다. 첫 메시지를 보내보세요! 👋");
    } else {
      renderMessages(msgs);
    }
  } catch (err) {
    appendSystemMsg("메시지 로드 중 오류 발생");
    console.error(err);
  }
}

async function loadChannelMembers(channelId) {
  try {
    const res  = await apiFetch(`/api/channels/${channelId}`);
    const json = await res.json();
    if (!res.ok) return;
    const members = json.data?.members || [];
    document.getElementById("chatMemberCount").textContent = `멤버 ${members.length}명`;

    const listEl = document.getElementById("memberList");
    listEl.innerHTML = "";
    members.forEach(m => {
      const emp = String(m.employeeNo || "").trim();
      const pr = presenceByEmployeeNo.get(emp) || "OFFLINE";
      const prCl = presenceCssClass(pr);
      const deptParts = [m.department, m.jobLevel].filter(x => x != null && String(x).trim() !== "");
      const orgLine = deptParts.length ? deptParts.map(x => String(x).trim()).join(" · ") : "조직 미지정";
      const posHtml =
        m.jobPosition != null && String(m.jobPosition).trim() !== ""
          ? `<span class="member-position-txt">${escHtml(String(m.jobPosition).trim())}</span>`
          : "";
      const dutyHtml =
        m.jobTitle != null && String(m.jobTitle).trim() !== ""
          ? `<span class="member-duty-txt">${escHtml(String(m.jobTitle).trim())}</span>`
          : "";
      const li = document.createElement("li");
      li.className = "member-list-item";
      li.innerHTML = `
        <div class="member-avatar-wrap">
          <span class="member-avatar-sm">${avatarInitials(m.name || "?")}</span>
          <span class="presence-dot ${prCl}" data-presence-user="${escHtml(emp)}" title="${presenceTitle(pr)}"></span>
        </div>
        <button type="button" class="member-profile-btn" data-employee-no="${escHtml(emp)}">
          <span class="member-name-wrap">
            <span class="member-name-txt">${escHtml(m.name || "알 수 없음")}</span>
            <span class="member-org-txt">${escHtml(orgLine)}</span>
            ${posHtml}
            ${dutyHtml}
          </span>
        </button>`;
      li.querySelector(".member-profile-btn").addEventListener("click", () => openUserProfile(emp));
      listEl.appendChild(li);
    });
    refreshPresenceDots();
  } catch (err) {
    console.error("멤버 로드 실패", err);
  }
}

/* ==========================================================================
 * 메시지 렌더링 (Slack 유사: 같은 분·같은 사람 묶음 → 시간은 마지막 줄만 / 분이 바뀌면 줄마다 시간)
 * ========================================================================== */

/** 로컬 시간 기준 동일 캘린더 분 키 */
function minuteKey(iso) {
  if (!iso) return "";
  const d = new Date(iso);
  const y = d.getFullYear();
  const mo = String(d.getMonth() + 1).padStart(2, "0");
  const da = String(d.getDate()).padStart(2, "0");
  const h = String(d.getHours()).padStart(2, "0");
  const mi = String(d.getMinutes()).padStart(2, "0");
  return `${y}-${mo}-${da}T${h}:${mi}`;
}

/** 날짜 구분선용 로컬 YYYY-MM-DD */
function dateKeyLocal(iso) {
  if (!iso) return "";
  const d = new Date(iso);
  const y = d.getFullYear();
  const mo = String(d.getMonth() + 1).padStart(2, "0");
  const da = String(d.getDate()).padStart(2, "0");
  return `${y}-${mo}-${da}`;
}

function fmtDateDividerLabel(iso) {
  if (!iso) return "";
  const d = new Date(iso);
  return d.toLocaleDateString("ko-KR", { weekday: "long", year: "numeric", month: "long", day: "numeric" });
}

function createDateDividerElement(iso) {
  const div = document.createElement("div");
  div.className = "msg-date-divider";
  div.dataset.dateKey = dateKeyLocal(iso);
  div.innerHTML = `<span class="msg-date-pill">${escHtml(fmtDateDividerLabel(iso))}</span>`;
  return div;
}

/** 다음 줄이 같은 사람·같은 분이면 현재 줄에는 시각 미표시 */
function shouldShowMessageTime(msgs, i) {
  const cur = msgs[i];
  const next = msgs[i + 1];
  if (!next) return true;
  if (String(next.senderId) !== String(cur.senderId)) return true;
  if (minuteKey(next.createdAt) !== minuteKey(cur.createdAt)) return true;
  return false;
}

/** 아바타+이름 행: 이전 메시지와 발신자가 다를 때만 */
function shouldShowAvatarForMessage(msgs, i) {
  if (i === 0) return true;
  return String(msgs[i - 1].senderId) !== String(msgs[i].senderId);
}

/** API·소켓 메시지에서 파일 첨부 JSON 추출 */
function tryParseFilePayload(msg) {
  if (!msg) return null;
  const t = msg.messageType || msg.message_type;
  if (t && String(t).toUpperCase() === "FILE" && msg.text) {
    try {
      const o = JSON.parse(msg.text);
      if (o && o.kind === "FILE" && o.fileId != null) return o;
    } catch {
      return null;
    }
  }
  if (msg.text && typeof msg.text === "string") {
    const s = msg.text.trim();
    if (s.startsWith("{") && s.includes('"kind"') && s.includes("FILE")) {
      try {
        const o = JSON.parse(s);
        if (o && o.kind === "FILE" && o.fileId != null) return o;
      } catch {
        return null;
      }
    }
  }
  return null;
}

async function getAuthedImageBlobUrl(channelId, fileId) {
  const key = `${channelId}_${fileId}`;
  if (imageAttachmentBlobUrls.has(key)) {
    return imageAttachmentBlobUrls.get(key);
  }
  if (!currentUser) {
    throw new Error("Not signed in");
  }
  const res = await fetch(
    `${API_BASE}/api/channels/${channelId}/files/${fileId}/download?employeeNo=${encodeURIComponent(currentUser.employeeNo)}`,
    { headers: { Authorization: `Bearer ${getToken()}` } }
  );
  if (!res.ok) {
    throw new Error("Image fetch failed");
  }
  const blob = await res.blob();
  const url = URL.createObjectURL(blob);
  imageAttachmentBlobUrls.set(key, url);
  return url;
}

const INLINE_IMAGE_EXT = /\.(jpe?g|png|gif|webp|bmp|svg)$/i;

function isImageFilePayload(payload) {
  if (!payload) return false;
  const ct = String(payload.contentType || "").trim().toLowerCase();
  if (ct.startsWith("image/")) return true;
  return INLINE_IMAGE_EXT.test(String(payload.originalFilename || ""));
}

function openImageLightbox(blobUrl, fileId, filename, channelId) {
  const img = document.getElementById("imagePreviewLarge");
  const cap = document.getElementById("imagePreviewCaption");
  const dl = document.getElementById("imagePreviewDownload");
  if (!img || !cap || !dl) return;
  img.src = blobUrl;
  img.alt = filename || "image";
  cap.textContent = filename || "";
  dl.onclick = () => downloadChannelFile(fileId, filename, channelId);
  openModal("modalImagePreview");
}

function createImageAttachmentRowFromMsg(msg, payload, { showAvatar, showTime }) {
  const emp = String(msg.senderId ?? "").trim();
  const channelId = Number(msg.channelId) || activeChannelId;
  const isMine =
    currentUser && String(currentUser.employeeNo || "").trim() === emp;
  const senderName = msg.senderName || (emp ? `emp#${emp}` : "?");
  const pr = presenceByEmployeeNo.get(emp) || "OFFLINE";
  const prCl = presenceCssClass(pr);
  const prTip = presenceTitle(pr);
  const fileMeta = {
    id: Number(payload.fileId),
    originalFilename: payload.originalFilename,
    sizeBytes: payload.sizeBytes,
  };
  const timeHtml = showTime
    ? `<span class="msg-time">${escHtml(fmtTime(msg.createdAt))}</span>`
    : "";
  const div = document.createElement("div");
  div.className = `msg-row msg-chat ${isMine ? "msg-mine" : "msg-other"} msg-has-attachment msg-has-image${showAvatar ? "" : " msg-continued"}`;
  div.dataset.senderId = emp;
  div.dataset.minuteKey = minuteKey(msg.createdAt);
  div.dataset.dateKey = dateKeyLocal(msg.createdAt);

  const imageBlock = `
        <div class="msg-content-row msg-attachment-image">
          <button type="button" class="msg-inline-image-btn" title="크게 보기" aria-label="이미지 크게 보기">
            <div class="msg-inline-image-placeholder">불러오는 중…</div>
          </button>
          <div class="msg-image-caption-row">
            <span class="msg-attach-name">${escHtml(fileMeta.originalFilename || "이미지")}</span>
            <span class="msg-attach-meta">${fmtSize(fileMeta.sizeBytes || 0)}</span>
            <button type="button" class="btn-attach-dl">다운로드</button>${timeHtml}
          </div>
        </div>`;

  if (showAvatar) {
    const initials = avatarInitials(senderName);
    div.innerHTML = `
      <div class="msg-avatar-wrap">
        <button type="button" class="msg-avatar msg-user-trigger" data-employee-no="${escHtml(emp)}" title="프로필 보기">${initials}</button>
        <span class="presence-dot ${prCl}" data-presence-user="${escHtml(emp)}" title="${prTip}"></span>
      </div>
      <div class="msg-body">
        <div class="msg-meta">
          <button type="button" class="msg-sender msg-user-trigger" data-employee-no="${escHtml(emp)}">${escHtml(senderName)}</button>
        </div>
        ${imageBlock}
      </div>`;
  } else {
    div.innerHTML = `
      <div class="msg-spacer"></div>
      <div class="msg-body">
        ${imageBlock}
      </div>`;
  }

  const dlBtn = div.querySelector(".btn-attach-dl");
  dlBtn.addEventListener("click", () => {
    downloadChannelFile(fileMeta.id, fileMeta.originalFilename, channelId);
  });

  const imgBtn = div.querySelector(".msg-inline-image-btn");
  const placeholder = div.querySelector(".msg-inline-image-placeholder");

  getAuthedImageBlobUrl(channelId, fileMeta.id)
    .then((url) => {
      const im = document.createElement("img");
      im.className = "msg-inline-image";
      im.alt = fileMeta.originalFilename || "첨부 이미지";
      im.src = url;
      im.loading = "lazy";
      placeholder.replaceWith(im);
      imgBtn.addEventListener("click", () => {
        openImageLightbox(url, fileMeta.id, fileMeta.originalFilename, channelId);
      });
    })
    .catch(() => {
      placeholder.textContent = "이미지를 불러올 수 없습니다";
    });

  return div;
}

function createFileAttachmentRowFromMsg(msg, payload, { showAvatar, showTime }) {
  if (isImageFilePayload(payload)) {
    return createImageAttachmentRowFromMsg(msg, payload, { showAvatar, showTime });
  }
  const emp = String(msg.senderId ?? "").trim();
  const isMine =
    currentUser && String(currentUser.employeeNo || "").trim() === emp;
  const senderName = msg.senderName || (emp ? `emp#${emp}` : "?");
  const pr = presenceByEmployeeNo.get(emp) || "OFFLINE";
  const prCl = presenceCssClass(pr);
  const prTip = presenceTitle(pr);
  const channelId = Number(msg.channelId) || activeChannelId;
  const fileMeta = {
    id: Number(payload.fileId),
    originalFilename: payload.originalFilename,
    sizeBytes: payload.sizeBytes,
    uploadedByEmployeeNo: emp,
    uploaderName: senderName,
  };
  const timeHtml = showTime
    ? `<span class="msg-time">${escHtml(fmtTime(msg.createdAt))}</span>`
    : "";
  const div = document.createElement("div");
  div.className = `msg-row msg-chat ${isMine ? "msg-mine" : "msg-other"} msg-has-attachment${showAvatar ? "" : " msg-continued"}`;
  div.dataset.senderId = emp;
  div.dataset.minuteKey = minuteKey(msg.createdAt);
  div.dataset.dateKey = dateKeyLocal(msg.createdAt);

  if (showAvatar) {
    const initials = avatarInitials(senderName);
    div.innerHTML = `
      <div class="msg-avatar-wrap">
        <button type="button" class="msg-avatar msg-user-trigger" data-employee-no="${escHtml(emp)}" title="프로필 보기">${initials}</button>
        <span class="presence-dot ${prCl}" data-presence-user="${escHtml(emp)}" title="${prTip}"></span>
      </div>
      <div class="msg-body">
        <div class="msg-meta">
          <button type="button" class="msg-sender msg-user-trigger" data-employee-no="${escHtml(emp)}">${escHtml(senderName)}</button>
        </div>
        <div class="msg-content-row msg-attachment-inline">
          <span class="msg-attach-icon" aria-hidden="true">📎</span>
          <span class="msg-attach-name">${escHtml(fileMeta.originalFilename || "첨부파일")}</span>
          <span class="msg-attach-meta">${fmtSize(fileMeta.sizeBytes || 0)}</span>
          <button type="button" class="btn-attach-dl">다운로드</button>${timeHtml}
        </div>
      </div>`;
  } else {
    div.innerHTML = `
      <div class="msg-spacer"></div>
      <div class="msg-body">
        <div class="msg-content-row msg-attachment-inline">
          <span class="msg-attach-icon" aria-hidden="true">📎</span>
          <span class="msg-attach-name">${escHtml(fileMeta.originalFilename || "첨부파일")}</span>
          <span class="msg-attach-meta">${fmtSize(fileMeta.sizeBytes || 0)}</span>
          <button type="button" class="btn-attach-dl">다운로드</button>${timeHtml}
        </div>
      </div>`;
  }
  div.querySelector(".btn-attach-dl").addEventListener("click", () => {
    downloadChannelFile(fileMeta.id, fileMeta.originalFilename, channelId);
  });
  return div;
}

function createMessageRowElement(msg, { showAvatar, showTime }) {
  const filePayload = tryParseFilePayload(msg);
  if (filePayload) {
    return createFileAttachmentRowFromMsg(msg, filePayload, { showAvatar, showTime });
  }

  const emp = String(msg.senderId ?? "").trim();
  const isMine =
    currentUser && String(currentUser.employeeNo || "").trim() === emp;
  const senderName = msg.senderName || (emp ? `emp#${emp}` : "?");
  const pr = presenceByEmployeeNo.get(emp) || "OFFLINE";
  const prCl = presenceCssClass(pr);
  const prTip = presenceTitle(pr);
  const div = document.createElement("div");
  div.className = `msg-row msg-chat ${isMine ? "msg-mine" : "msg-other"}${showAvatar ? "" : " msg-continued"}`;
  div.dataset.senderId = emp;
  div.dataset.minuteKey = minuteKey(msg.createdAt);
  div.dataset.dateKey = dateKeyLocal(msg.createdAt);

  const timeHtml = showTime
    ? `<span class="msg-time">${escHtml(fmtTime(msg.createdAt))}</span>`
    : "";

  if (showAvatar) {
    const initials = avatarInitials(senderName);
    div.innerHTML = `
      <div class="msg-avatar-wrap">
        <button type="button" class="msg-avatar msg-user-trigger" data-employee-no="${escHtml(emp)}" title="프로필 보기">${initials}</button>
        <span class="presence-dot ${prCl}" data-presence-user="${escHtml(emp)}" title="${prTip}"></span>
      </div>
      <div class="msg-body">
        <div class="msg-meta">
          <button type="button" class="msg-sender msg-user-trigger" data-employee-no="${escHtml(emp)}">${escHtml(senderName)}</button>
        </div>
        <div class="msg-content-row">
          <span class="msg-text">${escHtml(msg.text)}</span>${timeHtml}
        </div>
      </div>`;
  } else {
    div.innerHTML = `
      <div class="msg-spacer"></div>
      <div class="msg-body">
        <div class="msg-content-row">
          <span class="msg-text">${escHtml(msg.text)}</span>${timeHtml}
        </div>
      </div>`;
  }
  return div;
}

function renderMessages(msgs) {
  messagesEl.innerHTML = "";
  if (!msgs.length) return;
  let prevDk = null;
  msgs.forEach((m, i) => {
    const dk = dateKeyLocal(m.createdAt);
    if (prevDk !== dk) {
      messagesEl.appendChild(createDateDividerElement(m.createdAt));
      prevDk = dk;
    }
    const showAvatar = shouldShowAvatarForMessage(msgs, i);
    const showTime = shouldShowMessageTime(msgs, i);
    messagesEl.appendChild(createMessageRowElement(m, { showAvatar, showTime }));
  });
  trimMessages();
  refreshPresenceDots();
  messagesEl.scrollTop = messagesEl.scrollHeight;
}

/** 소켓으로 도착한 한 줄: 직전 줄과 같은 사람·같은 분이면 직전 줄에서 시각 제거 후 추가 */
function appendMessageRealtime(msg) {
  const sid = String(msg.senderId ?? "").trim();
  const mk = minuteKey(msg.createdAt);
  const dk = dateKeyLocal(msg.createdAt);
  const isChatRow = (el) =>
    !!el && el.classList?.contains("msg-row") && el.classList.contains("msg-chat");

  const prevEl = messagesEl.lastElementChild;
  const prevChat = isChatRow(prevEl) ? prevEl : null;
  const prevChatDateKey = prevChat ? prevChat.dataset.dateKey : null;
  if (!prevChat || prevChatDateKey !== dk) {
    messagesEl.appendChild(createDateDividerElement(msg.createdAt));
  }

  const adjacentPrevEl = messagesEl.lastElementChild;
  const adjacentPrevChat = isChatRow(adjacentPrevEl) ? adjacentPrevEl : null;
  if (
    adjacentPrevChat &&
    adjacentPrevChat.dataset.senderId === sid &&
    adjacentPrevChat.dataset.minuteKey === mk
  ) {
    const timeEl = adjacentPrevChat.querySelector(".msg-time");
    if (timeEl) timeEl.remove();
  }

  const beforeAppendEl = messagesEl.lastElementChild;
  const beforeAppendChat = isChatRow(beforeAppendEl) ? beforeAppendEl : null;
  const showAvatar = !beforeAppendChat || beforeAppendChat.dataset.senderId !== sid;
  messagesEl.appendChild(
    createMessageRowElement(msg, { showAvatar, showTime: true })
  );
  trimMessages();
  refreshPresenceDots();
  messagesEl.scrollTop = messagesEl.scrollHeight;
}

function appendSystemMsg(text) {
  const div = document.createElement("div");
  div.className = "msg-system";
  div.textContent = text;
  messagesEl.appendChild(div);
  messagesEl.scrollTop = messagesEl.scrollHeight;
}

function trimMessages() {
  while (messagesEl.children.length > MAX_MSGS) {
    messagesEl.removeChild(messagesEl.firstChild);
  }
}

/* ==========================================================================
 * Socket.io
 * ========================================================================== */
function initSocket() {
  if (socket) socket.disconnect();
  socket = io(SOCKET_URL, {
    reconnection: true,
    reconnectionAttempts: Infinity,
    reconnectionDelay: 1000,
    reconnectionDelayMax: 15000,
    randomizationFactor: 0.3,
    timeout: 10000,
  });

  socket.on("connect", async () => {
    await fetchPresenceSnapshot();
    if (currentUser?.employeeNo) {
      socket.emit("presence:set", { employeeNo: currentUser.employeeNo, status: "ONLINE" });
    }
    if (activeChannelId) socket.emit("channel:join", activeChannelId);
    refreshPresenceDots();
    if (activeChannelId) loadChannelMembers(activeChannelId);
  });

  socket.on("reconnect", async () => {
    await fetchPresenceSnapshot();
    if (currentUser?.employeeNo) {
      socket.emit("presence:set", { employeeNo: currentUser.employeeNo, status: "ONLINE" });
    }
    if (activeChannelId) socket.emit("channel:join", activeChannelId);
    refreshPresenceDots();
    if (activeChannelId) loadChannelMembers(activeChannelId);
  });

  socket.on("presence:update", (p) => {
    const emp = String(p?.employeeNo ?? "").trim();
    if (!emp) return;
    presenceByEmployeeNo.set(emp, String(p.status || "OFFLINE").toUpperCase());
    refreshPresenceDots();
  });

  socket.on("disconnect", (reason) => {
    appendSystemMsg(`연결이 끊어졌습니다. (${reason})`);
  });

  socket.on("message:new", (msg) => {
    // pg bigint → string 가능성 대비 Number()로 비교
    if (Number(msg.channelId) === activeChannelId) {
      appendMessageRealtime(msg);
    }
  });

  socket.on("message:error", (err) => {
    appendSystemMsg("전송 실패: " + (err?.message || "알 수 없는 오류"));
  });
}

function joinSocketChannel(channelId) {
  if (!socket) return;
  socket.emit("channel:join", channelId);
}

async function sendMessageViaSocket(channelId, senderId, text) {
  if (!socket || !socket.connected) {
    const err = new Error("소켓 연결이 준비되지 않았습니다.");
    err.code = "SOCKET_UNAVAILABLE";
    throw err;
  }
  return new Promise((resolve, reject) => {
    let finished = false;
    const timer = setTimeout(() => {
      if (finished) return;
      finished = true;
      const err = new Error("실시간 ACK 응답이 지연되었습니다.");
      err.code = "SOCKET_ACK_TIMEOUT";
      reject(err);
    }, 2500);

    socket.emit(
      "message:send",
      { channelId, senderId, text },
      (ack) => {
        if (finished) return;
        finished = true;
        clearTimeout(timer);
        if (ack && ack.ok) {
          resolve(ack);
          return;
        }
        const err = new Error((ack && ack.message) || "실시간 전송 실패");
        err.code = (ack && ack.code) || "SOCKET_ACK_FAILED";
        reject(err);
      }
    );
  });
}

async function sendMessageViaApi(channelId, senderId, text) {
  const res = await apiFetch(`/api/channels/${channelId}/messages`, {
    method: "POST",
    body: JSON.stringify({ senderId, text }),
  });
  const json = await res.json();
  if (!res.ok) {
    throw new Error(json.error?.message || "메시지 API 전송 실패");
  }
  const m = json.data;
  appendMessageRealtime({
    messageId: m.messageId,
    channelId: m.channelId,
    senderId: m.senderId,
    senderName: m.senderName,
    text: m.text,
    createdAt: m.createdAt,
  });
}

/* ==========================================================================
 * 메시지 전송
 * ========================================================================== */
async function sendMessage() {
  if (!activeChannelId || !currentUser) return;
  const text = messageInputEl.value.trim();

  // 파일이 있으면 파일 먼저 업로드
  if (pendingFile) {
    await uploadFile(pendingFile);
    clearFilePreview();
    if (!text) { messageInputEl.value = ""; return; }
  }

  if (!text) return;
  try {
    await sendMessageViaSocket(activeChannelId, currentUser.employeeNo, text);
  } catch (socketErr) {
    // 소켓 저장이 실패해도 API 경로로 한 번 더 시도해 전송 유실을 줄인다.
    console.warn("[sendMessage] socket 전송 실패, API 폴백 시도:", socketErr);
    try {
      await sendMessageViaApi(activeChannelId, currentUser.employeeNo, text);
      appendSystemMsg("실시간 경로 오류로 API 경로로 전송했습니다.");
    } catch (apiErr) {
      appendSystemMsg("전송 실패: " + (apiErr?.message || "오류"));
      return;
    }
  }
  messageInputEl.value = "";
}

document.getElementById("btnSend").addEventListener("click", sendMessage);
messageInputEl.addEventListener("keydown", (e) => {
  if (e.key === "Enter" && !e.shiftKey) {
    e.preventDefault();
    sendMessage();
  }
});

/* ==========================================================================
 * 파일 업로드
 * ========================================================================== */
document.getElementById("btnAttach").addEventListener("click", () => {
  document.getElementById("fileInput").click();
});

document.getElementById("fileInput").addEventListener("change", (e) => {
  const file = e.target.files[0];
  if (!file) return;
  if (pendingComposerPreviewUrl) {
    try {
      URL.revokeObjectURL(pendingComposerPreviewUrl);
    } catch {
      /* ignore */
    }
    pendingComposerPreviewUrl = null;
  }
  pendingFile = file;
  const thumb = document.getElementById("filePreviewThumb");
  if (thumb) {
    if (file.type && file.type.startsWith("image/")) {
      pendingComposerPreviewUrl = URL.createObjectURL(file);
      thumb.src = pendingComposerPreviewUrl;
      thumb.classList.remove("hidden");
    } else {
      thumb.classList.add("hidden");
      thumb.removeAttribute("src");
    }
  }
  document.getElementById("filePreview").classList.remove("hidden");
  document.getElementById("filePreviewName").textContent = `📎 ${file.name} (${fmtSize(file.size)})`;
  e.target.value = "";
});

document.getElementById("btnClearFile").addEventListener("click", clearFilePreview);

function clearFilePreview() {
  if (pendingComposerPreviewUrl) {
    try {
      URL.revokeObjectURL(pendingComposerPreviewUrl);
    } catch {
      /* ignore */
    }
    pendingComposerPreviewUrl = null;
  }
  pendingFile = null;
  const thumb = document.getElementById("filePreviewThumb");
  if (thumb) {
    thumb.classList.add("hidden");
    thumb.removeAttribute("src");
  }
  document.getElementById("filePreview").classList.add("hidden");
  document.getElementById("filePreviewName").textContent = "";
}

async function uploadFile(file) {
  if (!activeChannelId || !currentUser) return;
  const formData = new FormData();
  formData.append("file", file);
  try {
    const res  = await fetch(`${API_BASE}/api/channels/${activeChannelId}/files/upload?employeeNo=${encodeURIComponent(currentUser.employeeNo)}`, {
      method: "POST",
      headers: { Authorization: `Bearer ${getToken()}` },
      body: formData,
    });
    const json = await res.json();
    if (res.ok) {
      if (activeChannelId) {
        await loadMessages(activeChannelId);
        loadChannelFiles(activeChannelId);
      }
    } else {
      appendSystemMsg(`파일 업로드 실패: ${json.error?.message || "오류"}`);
    }
  } catch {
    appendSystemMsg("파일 업로드 중 서버 오류");
  }
}

/* ==========================================================================
 * 채널 만들기 모달
 * ========================================================================== */
document.getElementById("btnCreateChannel").addEventListener("click", async () => {
  selectedMembers = [];
  document.getElementById("newChannelName").value = "";
  document.getElementById("newChannelDesc").value = "";
  document.getElementById("newChannelType").value = "PUBLIC";
  document.getElementById("selectedMembersWrap").innerHTML = "";
  openModal("modalCreateChannel");
});

// 채널 생성 모달은 + 버튼 기반으로 멤버를 선택하므로 인라인 검색 핸들러는 연결하지 않습니다.

async function searchUsers(context, forcedKeyword = null) {
  const inputEl =
    context === "dm"
      ? document.getElementById("dmSearchInput")
      : (context === "channelMember"
        ? document.getElementById("addMemberSearchInput")
        : document.getElementById("memberSearchInput"));
  const resultEl =
    context === "dm"
      ? document.getElementById("dmSearchResults")
      : (context === "channelMember"
        ? document.getElementById("addMemberSearchResults")
        : document.getElementById("userSearchResults"));
  if (!inputEl || !resultEl) return;
  const isAll = forcedKeyword === "__all__";
  const keyword = isAll ? "" : (forcedKeyword ?? inputEl.value.trim());
  if (!isAll && !keyword) return;

  try {
    const queryPart = keyword ? `?q=${encodeURIComponent(keyword)}` : "";
    const res  = await apiFetch(`/api/users/search${queryPart}`);
    const json = await res.json();
    renderUserSearchResults(json.data || [], resultEl, context);
  } catch {
    resultEl.innerHTML = '<li class="search-empty">검색 오류</li>';
  }
}

function renderUserSearchResults(users, listEl, context) {
  listEl.innerHTML = "";
  if (users.length === 0) {
    listEl.innerHTML = '<li class="search-empty">검색 결과 없음</li>';
    return;
  }
  users.forEach(u => {
    if (String(u.employeeNo || "").trim() === String(currentUser?.employeeNo || "").trim()) return;
    const li = document.createElement("li");
    li.className = "user-search-item";
    li.innerHTML = `
      <div class="user-search-avatar">${avatarInitials(u.name)}</div>
      <div class="user-search-info">
        <span class="user-search-name">${escHtml(u.name)}</span>
        <span class="user-search-dept">${escHtml([u.department, u.email, u.employeeNo].filter(Boolean).join(" · "))}</span>
      </div>
      <button class="btn-add-member" data-uid="${escHtml(String(u.employeeNo || "").trim())}">추가</button>`;
    li.querySelector(".btn-add-member").addEventListener("click", () => {
      addSelectedMember(u, context);
      li.remove();
    });
    listEl.appendChild(li);
  });
}

function addSelectedMember(user, context) {
  const listRef = context === "dm"
    ? selectedDmMembers
    : (context === "channelMember" ? selectedAddMembers : selectedMembers);
  const wrapId = context === "dm"
    ? "selectedDmMembersWrap"
    : (context === "channelMember" ? "selectedAddMembersWrap" : "selectedMembersWrap");

  const emp = String(user.employeeNo || "").trim();
  if (!emp) return;
  if (listRef.find((m) => String(m.employeeNo || "").trim() === emp)) return;
  listRef.push(user);

  const wrap = document.getElementById(wrapId);
  const tag  = document.createElement("span");
  tag.className   = "member-tag";
  tag.dataset.uid = emp;
  tag.innerHTML   = `${escHtml(user.name)} <button type="button" data-uid="${escHtml(emp)}">✕</button>`;
  tag.querySelector("button").addEventListener("click", () => {
    removeSelectedMember(emp, context);
  });
  wrap.appendChild(tag);
  syncOrgCheckbox(emp, context, true);
  renderPickerSelectedMembers(context);
}

function removeSelectedMember(employeeNo, context) {
  const listRef = context === "dm"
    ? selectedDmMembers
    : (context === "channelMember" ? selectedAddMembers : selectedMembers);
  const wrapId = context === "dm"
    ? "selectedDmMembersWrap"
    : (context === "channelMember" ? "selectedAddMembersWrap" : "selectedMembersWrap");
  const want = String(employeeNo || "").trim();
  const idx = listRef.findIndex((m) => String(m.employeeNo || "").trim() === want);
  if (idx >= 0) listRef.splice(idx, 1);
  const wrap = document.getElementById(wrapId);
  const tag = wrap
    ? Array.from(wrap.querySelectorAll(".member-tag")).find((t) => t.dataset.uid === want)
    : null;
  if (tag) tag.remove();
  syncOrgCheckbox(want, context, false);
  renderPickerSelectedMembers(context);
}

document.getElementById("btnConfirmCreateChannel").addEventListener("click", async () => {
  const name     = document.getElementById("newChannelName").value.trim();
  const desc     = document.getElementById("newChannelDesc").value.trim();
  const type     = document.getElementById("newChannelType").value;
  if (!name) { alert("채널 이름을 입력하세요."); return; }
  if (!currentUser) return;

  try {
    const res = await apiFetch("/api/channels", {
      method: "POST",
      body: JSON.stringify({
        workspaceKey: WS_KEY,
        name,
        description: desc,
        channelType: type,
        createdByEmployeeNo: currentUser.employeeNo,
      }),
    });
    const json = await res.json();
    if (!res.ok) { alert("채널 생성 실패: " + (json.error?.message || "")); return; }

    const channelId = json.data?.channelId;
    // 선택한 멤버 추가
    for (const m of selectedMembers) {
      await apiFetch(`/api/channels/${channelId}/members`, {
        method: "POST",
        body: JSON.stringify({ employeeNo: m.employeeNo, memberRole: "MEMBER" }),
      });
    }

    closeModal("modalCreateChannel");
    await loadMyChannels();
    selectChannel(channelId, name, type);
  } catch {
    alert("채널 생성 중 오류 발생");
  }
});

/* ==========================================================================
 * DM 만들기 모달
 * ========================================================================== */
document.getElementById("btnCreateDm").addEventListener("click", async () => {
  selectedDmMembers = [];
  document.getElementById("selectedDmMembersWrap").innerHTML = "";
  openModal("modalCreateDm");
});

// DM 생성 모달도 + 버튼 기반으로 멤버를 선택합니다.

document.getElementById("btnConfirmCreateDm").addEventListener("click", async () => {
  if (selectedDmMembers.length === 0) { alert("대화 상대를 선택하세요."); return; }
  if (!currentUser) return;

  const dmName = selectedDmMembers.map(m => m.name).join(", ");

  try {
    const res = await apiFetch("/api/channels", {
      method: "POST",
      body: JSON.stringify({
        workspaceKey: WS_KEY,
        name: dmName,
        description: dmName,
        channelType: "DM",
        createdByEmployeeNo: currentUser.employeeNo,
        dmPeerEmployeeNos: selectedDmMembers.map((m) => m.employeeNo),
      }),
    });
    const json = await res.json();
    if (!res.ok) { alert("DM 생성 실패: " + (json.error?.message || "")); return; }

    const channelId = json.data?.channelId;

    closeModal("modalCreateDm");
    await loadMyChannels();
    selectChannel(channelId, dmName, "DM");
  } catch {
    alert("DM 생성 중 오류 발생");
  }
});

/* ==========================================================================
 * 멤버 패널 토글
 * ========================================================================== */
document.getElementById("btnShowMembers").addEventListener("click", () => {
  document.getElementById("memberPanel").classList.toggle("hidden");
});
document.getElementById("btnCloseMemberPanel").addEventListener("click", () => {
  document.getElementById("memberPanel").classList.add("hidden");
});
document.getElementById("btnOpenFileHub").addEventListener("click", async () => {
  if (!activeChannelId) return;
  await loadChannelFiles(activeChannelId);
  openModal("modalFileHub");
});

document.getElementById("btnAddMembersLater").addEventListener("click", async () => {
  if (!activeChannelId) {
    alert("채널을 먼저 선택하세요.");
    return;
  }
  selectedAddMembers = [];
  document.getElementById("selectedAddMembersWrap").innerHTML = "";
  openModal("modalAddChannelMembers");
});
document.getElementById("btnOpenAddMemberPicker").addEventListener("click", async () => {
  openModal("modalAddMemberPicker");
  await loadOrgTree("channelMember");
});
document.getElementById("btnCloseAddMemberPicker").addEventListener("click", () => {
  closeModal("modalAddMemberPicker");
});

function searchMembersInPicker() {
  renderMemberListRight();
}

document.getElementById("btnSearchTop")?.addEventListener("click", searchMembersInPicker);
document.getElementById("addMemberTopSearchInput")?.addEventListener("keydown", (e) => {
  if (e.key === "Enter") { e.preventDefault(); searchMembersInPicker(); }
});

document.getElementById("btnOpenAddMemberPickerForCreateChannel")?.addEventListener("click", async () => {
  openModal("modalAddMemberPicker");
  await loadOrgTree("member", "orgTreeEmbedAdd");
});

document.getElementById("btnOpenAddMemberPickerForCreateDm")?.addEventListener("click", async () => {
  openModal("modalAddMemberPicker");
  await loadOrgTree("dm", "orgTreeEmbedAdd");
});
document.getElementById("btnConfirmAddMembers").addEventListener("click", async () => {
  if (!activeChannelId) return;
  if (!selectedAddMembers.length) {
    alert("추가할 사용자를 선택하세요.");
    return;
  }
  const failed = [];
  for (const u of selectedAddMembers) {
    const res = await apiFetch(`/api/channels/${activeChannelId}/members`, {
      method: "POST",
      body: JSON.stringify({ employeeNo: u.employeeNo, memberRole: "MEMBER" }),
    });
    if (!res.ok) failed.push(u.name);
  }
  closeModal("modalAddChannelMembers");
  await loadChannelMembers(activeChannelId);
  if (failed.length) {
    appendSystemMsg(`일부 구성원 추가 실패: ${failed.join(", ")}`);
  } else {
    appendSystemMsg("구성원 추가 완료");
  }
});
document.getElementById("btnProfileDm").addEventListener("click", async () => {
  const name = document.getElementById("profileModalName")?.textContent?.trim() || "";
  const peerEmployeeNo = document.getElementById("profileModalEmpNo")?.textContent?.trim() || "";
  await startDmWithUser(profileViewEmployeeNo || peerEmployeeNo, name);
});

/* ==========================================================================
 * 모달 유틸
 * ========================================================================== */
function openModal(id)  { document.getElementById(id).classList.remove("hidden"); }
function closeModal(id) { document.getElementById(id).classList.add("hidden"); }

document.querySelectorAll(".modal-close, .btn-cancel").forEach(btn => {
  btn.addEventListener("click", () => {
    const modalId = btn.dataset.modal;
    if (modalId) closeModal(modalId);
  });
});
document.querySelectorAll(".modal-overlay").forEach(overlay => {
  overlay.addEventListener("click", (e) => {
    if (e.target === overlay) closeModal(overlay.id);
  });
});

document.addEventListener("click", async (e) => {
  const t = e.target.closest(".theme-option-btn");
  if (!t || !t.dataset.theme) return;
  e.preventDefault();
  const theme = VALID_THEMES.includes(t.dataset.theme) ? t.dataset.theme : "dark";
  applyTheme(theme);
  if (currentUser) {
    currentUser.themePreference = theme;
    const token = getToken();
    if (token) saveSession(token, currentUser);
    await saveThemePreference(theme);
  }
  closeModal("modalThemePicker");
});

/* ==========================================================================
 * 관리자: 배포 관리 / 설정
 * ========================================================================== */
document.getElementById("navReleases").addEventListener("click", () => {
  showView("viewReleases");
  loadReleases();
});
document.getElementById("navSettings").addEventListener("click", () => {
  showView("viewSettings");
  loadSettings();
});
document.getElementById("refreshReleasesBtn").addEventListener("click", loadReleases);
document.getElementById("refreshSettingsBtn").addEventListener("click", loadSettings);

const STATUS_LABEL = { UPLOADED: "대기", ACTIVE: "운영중", PREVIOUS: "이전", DEPRECATED: "폐기" };
const STATUS_CLASS = { UPLOADED: "st-uploaded", ACTIVE: "st-active", PREVIOUS: "st-prev", DEPRECATED: "st-dep" };
const ACTION_LABEL = { ACTIVATED: "활성화", ROLLED_BACK: "롤백" };

async function loadReleases() {
  try {
    const res  = await apiFetch("/api/admin/releases");
    const json = await res.json();
    const tbody = document.getElementById("releaseTableBody");
    tbody.innerHTML = "";
    (json.data || []).forEach(r => {
      const tr = document.createElement("tr");
      tr.innerHTML = `
        <td><strong>${escHtml(r.version)}</strong></td>
        <td>${escHtml(r.fileName)}</td>
        <td>${fmtSize(r.fileSize)}</td>
        <td><span class="status-badge ${STATUS_CLASS[r.status] || ""}">${STATUS_LABEL[r.status] || r.status}</span></td>
        <td>${fmtDate(r.uploadedAt)}</td>
        <td>${fmtDate(r.activatedAt)}</td>
        <td class="action-cell">
          ${r.status !== "ACTIVE" && r.status !== "DEPRECATED"
            ? `<button class="btn-sm btn-activate" data-id="${r.id}" data-ver="${escHtml(r.version)}">활성화</button>`
            : ""}
          ${r.status === "UPLOADED" || r.status === "DEPRECATED"
            ? `<button class="btn-sm btn-danger btn-delete" data-id="${r.id}">삭제</button>`
            : ""}
        </td>`;
      tbody.appendChild(tr);
    });
    tbody.querySelectorAll(".btn-activate").forEach(btn => {
      btn.addEventListener("click", async () => {
        if (!confirm(`v${btn.dataset.ver}을 운영 버전으로 활성화하시겠습니까?`)) return;
        const r = await apiFetch(`/api/admin/releases/${btn.dataset.id}/activate`, {
          method: "POST",
          body: JSON.stringify({ actorEmployeeNo: currentUser?.employeeNo, note: "수동 활성화" }),
        });
        alert(r.ok ? "활성화 완료" : "활성화 실패");
        loadReleases();
      });
    });
    tbody.querySelectorAll(".btn-delete").forEach(btn => {
      btn.addEventListener("click", async () => {
        if (!confirm("이 릴리즈 파일을 삭제하시겠습니까?")) return;
        const r = await apiFetch(`/api/admin/releases/${btn.dataset.id}?actorEmployeeNo=${encodeURIComponent(currentUser?.employeeNo || "")}`, {
          method: "DELETE",
        });
        alert(r.ok ? "삭제 완료" : "삭제 실패");
        loadReleases();
      });
    });
  } catch (e) { console.error("릴리즈 로드 실패", e); }

  try {
    const res  = await apiFetch("/api/admin/releases/history");
    const json = await res.json();
    const hbody = document.getElementById("deployHistoryBody");
    hbody.innerHTML = "";
    (json.data || []).forEach(h => {
      const tr = document.createElement("tr");
      tr.innerHTML = `
        <td>${fmtDate(h.createdAt)}</td>
        <td><span class="action-badge">${ACTION_LABEL[h.action] || h.action}</span></td>
        <td>${h.fromVersion || "-"}</td>
        <td><strong>${escHtml(h.toVersion)}</strong></td>
        <td>${escHtml(h.note || "-")}</td>`;
      hbody.appendChild(tr);
    });
  } catch (e) { console.error("배포 이력 로드 실패", e); }
}

document.getElementById("releaseUploadForm").addEventListener("submit", async (e) => {
  e.preventDefault();
  const version = document.getElementById("releaseVersion").value.trim();
  const file    = document.getElementById("releaseFile").files[0];
  const desc    = document.getElementById("releaseDescription").value.trim();
  const statusEl = document.getElementById("releaseUploadStatus");
  if (!version || !file) { statusEl.textContent = "버전과 파일을 모두 입력하세요."; return; }
  const fd = new FormData();
  fd.append("version", version);
  fd.append("file", file);
  if (desc) fd.append("description", desc);
  if (currentUser?.employeeNo) fd.append("uploadedByEmployeeNo", currentUser.employeeNo);
  statusEl.textContent = "업로드 중...";
  try {
    const res  = await fetch(`${API_BASE}/api/admin/releases`, {
      method: "POST",
      headers: { Authorization: `Bearer ${getToken()}` },
      body: fd,
    });
    const json = await res.json();
    statusEl.textContent = res.ok
      ? `완료: v${json.data?.version || ""} 업로드 성공`
      : `실패: ${json.error?.message || "오류"}`;
    if (res.ok) { document.getElementById("releaseUploadForm").reset(); loadReleases(); }
  } catch { statusEl.textContent = "서버 연결 오류"; }
});

async function loadSettings() {
  const listEl = document.getElementById("settingsList");
  if (!listEl) return;
  listEl.innerHTML = "";
  try {
    const res  = await apiFetch("/api/admin/settings");
    const json = await res.json();
    (json.data || []).forEach(s => {
      const div = document.createElement("div");
      div.className = "setting-item";
      div.innerHTML = `
        <div class="setting-info">
          <span class="setting-key">${escHtml(s.key)}</span>
          <p class="setting-desc">${escHtml(s.description || "")}</p>
          <span class="setting-meta">최종 수정: ${fmtDate(s.updatedAt)}</span>
        </div>
        <div class="setting-edit">
          <input class="setting-value-input" type="text" value="${escHtml(s.value || "")}" data-key="${escHtml(s.key)}" />
          <button class="btn-sm btn-save-setting" data-key="${escHtml(s.key)}">저장</button>
        </div>`;
      listEl.appendChild(div);
    });
    listEl.querySelectorAll(".btn-save-setting").forEach(btn => {
      btn.addEventListener("click", async () => {
        const key   = btn.dataset.key;
        const input = listEl.querySelector(`.setting-value-input[data-key="${key}"]`);
        const value = input?.value.trim() || "";
        const r     = await apiFetch(`/api/admin/settings/${encodeURIComponent(key)}`, {
          method: "PUT",
          body: JSON.stringify({ value, updatedBy: currentUser?.employeeNo }),
        });
        const j = await r.json();
        alert(r.ok ? `"${key}" 설정이 저장되었습니다.` : `저장 실패: ${j.error?.message || "오류"}`);
      });
    });
  } catch { listEl.innerHTML = '<p class="empty-notice">설정 로드 실패</p>'; }
}

/* ==========================================================================
 * 통합 검색
 * ========================================================================== */
const TYPE_ICON  = { MESSAGE: "💬", FILE: "📎", WORK_ITEM: "✅", KANBAN_CARD: "📋" };
const TYPE_LABEL = { MESSAGE: "메시지", FILE: "파일", WORK_ITEM: "업무", KANBAN_CARD: "칸반" };

document.getElementById("searchForm").addEventListener("submit", async (e) => {
  e.preventDefault();
  const q = document.getElementById("searchInput").value.trim();
  if (q.length < 2) return;
  document.getElementById("searchTypeSelect").value = "ALL";
  await runSearch(q, "ALL");
  openModal("searchModal");
});

document.getElementById("searchTypeSelect").addEventListener("change", () => {
  const q = document.getElementById("searchInput").value.trim();
  if (q.length >= 2) runSearch(q, document.getElementById("searchTypeSelect").value);
});

async function runSearch(q, type) {
  const resultsEl = document.getElementById("searchResults");
  resultsEl.innerHTML = '<p class="empty-notice">검색 중...</p>';
  document.getElementById("searchModalTitle").textContent = `"${q}" 검색 결과`;
  openModal("searchModal");
  try {
    const res  = await apiFetch(`/api/search?q=${encodeURIComponent(q)}&type=${type}&limit=30`);
    const json = await res.json();
    if (!res.ok) { resultsEl.innerHTML = `<p class="empty-notice">${json.error?.message || "오류"}</p>`; return; }
    const items = json.data?.items || [];
    if (items.length === 0) { resultsEl.innerHTML = '<p class="empty-notice">검색 결과가 없습니다.</p>'; return; }
    resultsEl.innerHTML = "";
    items.forEach(item => {
      const div = document.createElement("div");
      div.className = "search-item";
      div.innerHTML = `
        <div class="search-item-type">
          <span class="search-type-badge">${TYPE_ICON[item.type] || ""} ${TYPE_LABEL[item.type] || item.type}</span>
        </div>
        <div class="search-item-body">
          <p class="search-item-title">${escHtml(item.title || "")}</p>
          ${item.preview ? `<p class="search-item-preview">${escHtml(item.preview)}</p>` : ""}
          <p class="search-item-meta">${escHtml(item.contextName || "")} · ${fmtDate(item.createdAt)}</p>
        </div>`;
      resultsEl.appendChild(div);
    });
  } catch { resultsEl.innerHTML = '<p class="empty-notice">서버 연결 오류</p>'; }
}

/* ==========================================================================
 * 기타 이벤트
 * ========================================================================== */
function initEvents() {
  if (!imageLightboxEscapeBound) {
    imageLightboxEscapeBound = true;
    document.addEventListener("keydown", (e) => {
      if (e.key !== "Escape") return;
      const mod = document.getElementById("modalImagePreview");
      if (mod && !mod.classList.contains("hidden")) {
        closeModal("modalImagePreview");
      }
    });
  }

  // 사이드바 섹션 접기/펼치기
  document.querySelectorAll(".section-toggle").forEach(btn => {
    btn.addEventListener("click", () => {
      const targetId = btn.dataset.target;
      if (!targetId) return;
      const target = document.getElementById(targetId);
      if (target) target.classList.toggle("hidden");
    });
  });

  messagesEl.addEventListener("click", (e) => {
    const t = e.target.closest(".msg-user-trigger");
    const emp = t && String(t.dataset.employeeNo || "").trim();
    if (!emp) return;
    e.preventDefault();
    openUserProfile(emp);
  });
}

/* ==========================================================================
 * 초기화
 * ========================================================================== */
(async function init() {
  const token = getToken();
  const user  = getUser();
  if (!token || !user) { showLogin(); return; }

  try {
    const res = await apiFetch("/api/auth/me");
    if (res.ok) {
      const json = await res.json();
      const meUser = json?.data ? { ...user, ...json.data } : user;
      saveSession(token, meUser);
      showMain(meUser);
    } else {
      clearSession();
      showLogin();
    }
  } catch {
    clearSession();
    showLogin();
  }
})();
initTheme();
