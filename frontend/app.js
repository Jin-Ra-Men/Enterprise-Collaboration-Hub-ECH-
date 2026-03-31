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
const SIDEBAR_COLLAPSED_KEY = "ech_sidebar_collapsed";
/** 퀵 레일에 표시할 최대 대화 수(미읽음 우선 후 최근 순) */
const QUICK_RAIL_MAX_ITEMS = 15;

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
/** GET /api/channels/{id} 의 createdByEmployeeNo (멤버 내보내기 버튼 표시용) */
let activeChannelCreatorEmployeeNo = null;
/** 현재 채널 멤버만 @자동완성 (전사 검색 대신). selectChannel 시 비움 → loadChannelMembers에서 채움 */
let activeChannelMemberMentionList = [];
let pendingFile    = null;
// 스레드(댓글) 모달 상태
let threadPendingFile = null;
let threadPendingFilePreviewUrl = null;
let threadRootMessageId = null;
// 스레드 모달 내부에서 렌더링 중인지 여부(메인용 댓글 요약 버튼 숨김용)
let isRenderingThreadModal = false;
// 타임라인 답글 모드(메시지 입력창으로 답글 생성)
let replyComposerTargetMessageId = null;
let replyComposerOriginalPlaceholder = "";
// 메시지 우클릭 컨텍스트 메뉴 상태
let contextMenuSelectedMessageId = null;
let contextMenuSelectedRootMessageId = null;
let contextMenuSelectedIsReply = false;
// 타임라인에서 로드된 ROOT 메시지 캐시(스레드 모달 렌더용)
let timelineRootMessageById = new Map();
// loadMessages(preserveScroll=true)일 때만 렌더 함수의 자동 스크롤을 억제/복원하기 위한 비율
let pendingScrollRestoreRatio = null;
let selectedMembers = [];     // 채널/DM 생성 시 선택된 사용자
let selectedDmMembers = [];
let selectedAddMembers = [];  // 기존 채널에 추가할 사용자
/** 다른 채널에 새 메시지 시 목록 갱신 디바운스 */
let refreshChannelListTimer = null;
let windowFocusChannelsTimer = null;
let orgPickerContext = null;  // member | dm | channelMember
let orgPickerEmbedElId = null; // member/dm/channelMember 조직도 체크박스가 그려진 엘리먼트 id
/** 프로필 모달에 표시 중인 사용자 사번 (DM 보내기용) */
let profileViewEmployeeNo = null;
/** 좌측 하단 프레즌스 메뉴 이벤트(재로그인 시 중복 바인딩 방지) */
let sidebarPresenceUiBound = false;
/** 사이드바 섹션 토글·접기 버튼은 initEvents가 여러 번 호출돼도 한 번만 바인딩 */
let sidebarSectionTogglesBound = false;
let sidebarCollapseBound = false;

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
const messageContextMenuEl = document.getElementById("messageContextMenu");
const threadRootContainerEl = document.getElementById("threadRootContainer");
const threadCommentsContainerEl = document.getElementById("threadCommentsContainer");
const threadFileInputEl = document.getElementById("threadFileInput");
const threadBtnAttachEl = document.getElementById("threadBtnAttach");
const threadMessageInputEl = document.getElementById("threadMessageInput");
const threadBtnSendEl = document.getElementById("threadBtnSend");
const threadFilePreviewEl = document.getElementById("threadFilePreview");
const threadFilePreviewThumbEl = document.getElementById("threadFilePreviewThumb");
const threadFilePreviewNameEl = document.getElementById("threadFilePreviewName");
const threadBtnClearFileEl = document.getElementById("threadBtnClearFile");
const replyComposerBannerEl = document.getElementById("replyComposerBanner");
const replyComposerBannerTitleEl = document.getElementById("replyComposerBannerTitle");
const replyComposerBannerPreviewEl = document.getElementById("replyComposerBannerPreview");
const replyComposerBannerCloseEl = document.getElementById("replyComposerBannerClose");

if (messageInputEl && replyComposerOriginalPlaceholder === "") {
  replyComposerOriginalPlaceholder = messageInputEl.placeholder || "메시지 입력…";
}

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

/** 멘션 토큰 `@{사번|표시명}` → 이스케이프된 @표시명 span (XSS 방지) */
function formatMessageWithMentions(text) {
  const s = text == null ? "" : String(text);
  const re = /@\{([^}]*)\}/g;
  const parts = [];
  let last = 0;
  let m;
  while ((m = re.exec(s)) !== null) {
    parts.push(escHtml(s.slice(last, m.index)));
    const inner = m[1];
    const pipe = inner.indexOf("|");
    const emp = (pipe >= 0 ? inner.slice(0, pipe) : inner).trim();
    const label = pipe >= 0 ? inner.slice(pipe + 1).trim() : emp;
    const safeEmp = escHtml(emp);
    const safeLabel = escHtml(label || emp);
    parts.push(
      `<span class="msg-mention msg-user-trigger" data-employee-no="${safeEmp}" role="button" tabindex="0" title="프로필">@${safeLabel}</span>`
    );
    last = m.index + m[0].length;
  }
  parts.push(escHtml(s.slice(last)));
  return parts.join("");
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
    if (!emp) return;
    const st = presenceByEmployeeNo.get(emp) || "OFFLINE";
    const prCl = presenceCssClass(st);
    const tip = presenceTitle(st);
    if (el.classList.contains("sidebar-dm-presence")) {
      el.className = `presence-dot ${prCl} sidebar-dm-presence`;
      el.dataset.presenceUser = emp;
      el.title = `${tip} · ${emp}`;
      return;
    }
    el.className = `presence-dot ${prCl}`;
    el.title = tip;
  });
  refreshSidebarUserStatusLine();
}

/** 좌측 하단 본인 프레즌스 문구(점은 CSS ::before 한 개만) */
function refreshSidebarUserStatusLine() {
  const el = document.getElementById("sidebarUserStatus");
  if (!el || !currentUser?.employeeNo) return;
  const emp = String(currentUser.employeeNo).trim();
  if (!emp) return;
  const st = presenceByEmployeeNo.get(emp) || "OFFLINE";
  el.classList.remove("online", "away", "offline");
  if (st === "ONLINE") el.classList.add("online");
  else if (st === "AWAY") el.classList.add("away");
  else el.classList.add("offline");
  el.textContent = st === "ONLINE" ? "온라인" : st === "AWAY" ? "자리비움" : "오프라인";
}

function closeSidebarPresenceMenu() {
  const menu = document.getElementById("sidebarPresenceMenu");
  const btn = document.getElementById("sidebarUserStatus");
  if (menu) menu.classList.add("hidden");
  if (btn) btn.setAttribute("aria-expanded", "false");
}

function toggleSidebarPresenceMenu() {
  if (!currentUser?.employeeNo) return;
  const menu = document.getElementById("sidebarPresenceMenu");
  const btn = document.getElementById("sidebarUserStatus");
  if (!menu || !btn) return;
  menu.classList.toggle("hidden");
  const isOpen = !menu.classList.contains("hidden");
  btn.setAttribute("aria-expanded", isOpen ? "true" : "false");
}

/** 본인 프레즌스 전송(온라인 / 자리비움). 서버·타 사용자에 `presence:update` 반영 */
function emitMyPresenceStatus(status) {
  const raw = String(status ?? "").trim().toUpperCase();
  if (raw !== "ONLINE" && raw !== "AWAY") return;
  if (!currentUser?.employeeNo) return;
  const emp = String(currentUser.employeeNo).trim();
  if (!emp) return;
  closeSidebarPresenceMenu();
  if (!socket?.connected) {
    console.warn("[presence] 소켓 미연결 — 상태가 서버에 반영되지 않을 수 있습니다.");
  } else {
    socket.emit("presence:set", { employeeNo: emp, status: raw });
  }
  presenceByEmployeeNo.set(emp, raw);
  refreshPresenceDots();
}

/** DM 줄 왼쪽: 상대 사번이 있으면 프레즌스 점, 없으면 기본 ● 아이콘 */
function dmSidebarLeadingHtml(peerEmployeeNos) {
  const peers = Array.isArray(peerEmployeeNos)
    ? peerEmployeeNos.map((e) => String(e || "").trim()).filter(Boolean)
    : [];
  if (peers.length === 0) {
    return `<span class="item-icon dm-type-icon" aria-hidden="true" title="다이렉트 메시지">●</span>`;
  }
  const maxDots = 3;
  const shown = peers.slice(0, maxDots);
  const dots = shown
    .map((emp) => {
      const st = presenceByEmployeeNo.get(emp) || "OFFLINE";
      const prCl = presenceCssClass(st);
      const tip = `${presenceTitle(st)} · ${emp}`;
      return `<span class="presence-dot ${prCl} sidebar-dm-presence" data-presence-user="${escHtml(emp)}" title="${escHtml(tip)}"></span>`;
    })
    .join("");
  const more =
    peers.length > maxDots
      ? `<span class="sidebar-dm-presence-more" title="+${peers.length - maxDots}명">+${peers.length - maxDots}</span>`
      : "";
  return `<span class="sidebar-dm-presence-wrap">${dots}${more}</span>`;
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
  applySidebarCollapsedState();

  sidebarUserName.textContent = `${user.name}`;
  sidebarAvatar.textContent = avatarInitials(user.name);

  /** 다른 계정 재로그인 시 이전 세션 프레즌스 캐시 제거 */
  presenceByEmployeeNo.clear();

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
  closeSidebarPresenceMenu();
  revokeImageAttachmentBlobUrls();
  closeModal("modalImagePreview");
  clearFilePreview();
  activeChannelId = null;
  activeChannelMemberMentionList = [];
  currentUser     = null;
  clearSession();

  // DOM 상태 완전 초기화 (다음 로그인 계정이 달라도 깨끗하게 시작)
  channelListEl.innerHTML = "";
  dmListEl.innerHTML      = "";
  const quickRailScroll = document.getElementById("quickRailScroll");
  if (quickRailScroll) quickRailScroll.innerHTML = "";
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
/** 섹션 목록 `hidden` 여부에 맞춰 화살표 ▾(펼침) / ▸(접힘) */
function syncSectionToggleChevron(btn, targetEl) {
  const chev = btn.querySelector(".section-toggle-chevron");
  if (!chev || !targetEl) return;
  chev.textContent = targetEl.classList.contains("hidden") ? "▸" : "▾";
}

function syncAllSidebarSectionChevrons() {
  document.querySelectorAll(".section-toggle[data-target]").forEach((btn) => {
    const id = btn.dataset.target;
    if (!id) return;
    const t = document.getElementById(id);
    if (t) syncSectionToggleChevron(btn, t);
  });
}

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

function scheduleRefreshMyChannels() {
  if (!currentUser) return;
  if (refreshChannelListTimer) clearTimeout(refreshChannelListTimer);
  refreshChannelListTimer = setTimeout(() => {
    refreshChannelListTimer = null;
    loadMyChannels();
  }, 400);
}

async function markChannelReadUpTo(channelId, lastReadMessageId) {
  if (!currentUser || channelId == null || lastReadMessageId == null) return;
  const mid = Number(lastReadMessageId);
  if (!Number.isFinite(mid)) return;
  try {
    const res = await apiFetch(`/api/channels/${channelId}/read-state`, {
      method: "PUT",
      body: JSON.stringify({ employeeNo: currentUser.employeeNo, lastReadMessageId: mid }),
    });
    if (!res.ok) {
      const j = await res.json().catch(() => ({}));
      console.warn("read-state 갱신 실패", res.status, j.error?.message || "");
    }
  } catch (e) {
    console.warn("read-state 갱신 오류", e);
  }
}

/** API 메시지 목록에서 루트 메시지 id 최댓값 (읽음 포인터 갱신용) */
function maxRootMessageIdFromList(msgs) {
  if (!msgs || msgs.length === 0) return null;
  let max = null;
  for (const m of msgs) {
    const pid = m.parentMessageId ?? m.parent_message_id;
    if (pid != null && pid !== "") continue;
    const id = Number(m.messageId ?? m.message_id);
    if (!Number.isFinite(id)) continue;
    if (max === null || id > max) max = id;
  }
  return max;
}

function formatUnreadBadgeCount(n) {
  const v = Number(n);
  if (!Number.isFinite(v) || v <= 0) return "";
  return v > 99 ? "99+" : String(v);
}

/** 목록 정렬: 최근 활동(루트 메시지 기준 시각) — API `lastMessageAt`, 없으면 `createdAt` */
function channelActivityTimeMs(ch) {
  const lm = ch.lastMessageAt ?? ch.last_message_at;
  const ca = ch.createdAt ?? ch.created_at;
  const raw = lm || ca || "";
  if (!raw) return 0;
  const ms = Date.parse(String(raw));
  return Number.isFinite(ms) ? ms : 0;
}

function setSidebarCollapsedUi(collapsed) {
  mainApp.classList.toggle("sidebar-collapsed", collapsed);
  const btn = document.getElementById("btnSidebarEdgeToggle");
  const icon = btn?.querySelector(".sidebar-edge-toggle-icon");
  if (btn) {
    btn.setAttribute("aria-expanded", collapsed ? "false" : "true");
    const t = collapsed ? "사이드바 펼치기" : "사이드바 접기";
    btn.title = t;
    btn.setAttribute("aria-label", t);
  }
  if (icon) {
    icon.textContent = collapsed ? "›" : "‹";
  }
}

function applySidebarCollapsedState() {
  let collapsed = false;
  try {
    collapsed = localStorage.getItem(SIDEBAR_COLLAPSED_KEY) === "1";
  } catch (e) {
    /* ignore */
  }
  setSidebarCollapsedUi(collapsed);
}

function toggleSidebarCollapsed() {
  const next = !mainApp.classList.contains("sidebar-collapsed");
  try {
    localStorage.setItem(SIDEBAR_COLLAPSED_KEY, next ? "1" : "0");
  } catch (e) {
    /* ignore */
  }
  setSidebarCollapsedUi(next);
}

/** 퀵 레일용 짧은 캡션(한 줄) */
function quickRailCaption(name) {
  const s = String(name || "").trim();
  if (s.length <= 7) return s;
  return `${s.slice(0, 6)}…`;
}

function compareQuickRailChannel(a, b) {
  const ua = Number(a.unreadCount ?? 0) > 0 ? 1 : 0;
  const ub = Number(b.unreadCount ?? 0) > 0 ? 1 : 0;
  if (ua !== ub) return ub - ua;
  return channelActivityTimeMs(b) - channelActivityTimeMs(a);
}

/**
 * 퀵 레일(`#quickRailScroll`): 워크스페이스 아래·검색~목록과 같은 세로 구간.
 * 미읽음 대화를 최상단(배지), 그 아래 `lastMessageAt` 기준 최근 대화(최대 `QUICK_RAIL_MAX_ITEMS`).
 */
function renderQuickUnreadList(channels) {
  const el = document.getElementById("quickRailScroll");
  if (!el) return;
  el.innerHTML = "";
  const sorted = [...(channels || [])].sort(compareQuickRailChannel);
  const picked = sorted.slice(0, QUICK_RAIL_MAX_ITEMS);
  if (picked.length === 0) {
    const emptyHint = document.createElement("div");
    emptyHint.className = "quick-rail-empty";
    emptyHint.textContent = "참여 중인 대화가 없습니다";
    el.appendChild(emptyHint);
    return;
  }
  picked.forEach((ch) => {
    const btn = document.createElement("button");
    btn.type = "button";
    btn.className = "quick-rail-link channel-item";
    btn.dataset.channelId = String(ch.channelId);
    btn.dataset.channelType = ch.channelType;
    const displayName =
      ch.channelType === "DM" && ch.description
        ? ch.description
        : ch.name;
    btn.dataset.channelName = displayName;
    const badgeTxt = formatUnreadBadgeCount(Number(ch.unreadCount ?? 0));
    const cap = quickRailCaption(displayName);
    const badgeHtml = badgeTxt
      ? `<em class="quick-rail-badge" aria-hidden="true">${escHtml(badgeTxt)}</em>`
      : "";
    const leadHtml =
      ch.channelType === "DM"
        ? `<span class="quick-rail-dm-lead">${dmSidebarLeadingHtml(ch.dmPeerEmployeeNos)}</span>`
        : `<span class="quick-rail-icon">${ch.channelType === "PRIVATE" ? "🔒" : "#"}</span>`;
    btn.innerHTML = `${leadHtml}<span class="quick-rail-label">${escHtml(cap)}</span>${badgeHtml}`;
    btn.setAttribute("data-tooltip-title", displayName);
    btn.title = displayName;
    const al = badgeTxt
      ? `${displayName}, 미읽음 ${badgeTxt}건`
      : displayName;
    btn.setAttribute("aria-label", al);
    if (ch.channelId === activeChannelId) btn.classList.add("active");
    btn.addEventListener("click", () => selectChannel(ch.channelId, displayName, ch.channelType));
    el.appendChild(btn);
  });
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

    const unread = Number(ch.unreadCount ?? 0);
    const badgeTxt = formatUnreadBadgeCount(unread);
    const badgeHtml = badgeTxt
      ? `<span class="channel-unread-badge" aria-label="미읽음 ${badgeTxt}건">${escHtml(badgeTxt)}</span>`
      : "";

    if (ch.channelType === "DM") {
      const dmLead = dmSidebarLeadingHtml(ch.dmPeerEmployeeNos);
      li.innerHTML = `${dmLead}<span class="item-label">${escHtml(displayName)}</span>${badgeHtml}`;
      dmListEl.appendChild(li);
    } else {
      const icon = ch.channelType === "PRIVATE" ? "🔒" : "#";
      li.innerHTML = `<span class="item-icon">${icon}</span><span class="item-label">${escHtml(ch.name)}</span>${badgeHtml}`;
      channelListEl.appendChild(li);
    }

    if (ch.channelId === activeChannelId) li.classList.add("active");

    li.addEventListener("click", () => selectChannel(ch.channelId, displayName, ch.channelType));
  });

  renderQuickUnreadList(channels);
  refreshPresenceDots();
}

/* ==========================================================================
 * 채널 선택 / 메시지 로드
 * ========================================================================== */
async function selectChannel(channelId, channelName, channelType) {
  revokeImageAttachmentBlobUrls();
  closeModal("modalImagePreview");
  activeChannelId   = channelId;
  activeChannelType = channelType;
  activeChannelCreatorEmployeeNo = null;
  activeChannelMemberMentionList = [];

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

async function loadMessages(channelId, { preserveScroll = false } = {}) {
  if (!currentUser) return;
  revokeImageAttachmentBlobUrls();
  try {
    if (preserveScroll && messagesEl) {
      const prevH = messagesEl.scrollHeight || 1;
      const prevTop = messagesEl.scrollTop || 0;
      pendingScrollRestoreRatio = prevH > 0 ? prevTop / prevH : 0;
    } else {
      pendingScrollRestoreRatio = null;
    }

    const empQ = encodeURIComponent(currentUser.employeeNo);
    const timelineUrl = `/api/channels/${channelId}/messages/timeline?employeeNo=${empQ}&limit=50`;
    const legacyUrl = `/api/channels/${channelId}/messages?employeeNo=${empQ}&limit=50`;

    let res = await apiFetch(timelineUrl);
    let json = await res.json().catch(() => ({}));
    let usedTimeline = true;

    // 구버전 백엔드(timeline 미구현)는 404(NoHandlerFound)로 떨어질 수 있음 → 루트 메시지 API로 폴백
    if (!res.ok && res.status === 404) {
      usedTimeline = false;
      res = await apiFetch(legacyUrl);
      json = await res.json().catch(() => ({}));
    }

    messagesEl.innerHTML = "";
    if (!res.ok) {
      appendSystemMsg("메시지 로드 실패: " + (json.error?.message || ""));
      return;
    }

    const msgs = json.data || [];
    timelineRootMessageById.clear();

    if (usedTimeline) {
      msgs.forEach((m) => {
        if (!m || m.isReply) return;
        const mid = m.messageId ?? m.message_id;
        if (mid != null) timelineRootMessageById.set(Number(mid), m);
      });
      if (msgs.length === 0) {
        appendSystemMsg("아직 메시지가 없습니다. 첫 메시지를 보내보세요! 👋");
      } else {
        renderTimelineMessages(msgs);
      }
    } else {
      msgs.forEach((m) => {
        if (!m) return;
        const pid = m.parentMessageId ?? m.parent_message_id;
        if (pid != null && pid !== "") return;
        const mid = m.messageId ?? m.message_id;
        if (mid != null) timelineRootMessageById.set(Number(mid), m);
      });
      if (msgs.length === 0) {
        appendSystemMsg("아직 메시지가 없습니다. 첫 메시지를 보내보세요! 👋");
      } else {
        renderMessages(msgs);
      }
    }

    // preserveScroll=true이면 render에서 자동 스크롤을 억제했으므로 여기서 비율로 복원한다.
    if (preserveScroll && pendingScrollRestoreRatio != null && Number.isFinite(pendingScrollRestoreRatio)) {
      const newH = messagesEl.scrollHeight || 0;
      const nextTop = Math.max(0, Math.min(newH, newH * pendingScrollRestoreRatio));
      messagesEl.scrollTop = nextTop;
      pendingScrollRestoreRatio = null;
    }

    const maxId = maxRootMessageIdFromList(msgs);
    if (maxId != null) {
      await markChannelReadUpTo(channelId, maxId);
    }
    await loadMyChannels();
  } catch (err) {
    appendSystemMsg("메시지 로드 중 오류 발생");
    console.error(err);
  } finally {
    pendingScrollRestoreRatio = null;
  }
}

async function loadChannelMembers(channelId) {
  try {
    const res  = await apiFetch(`/api/channels/${channelId}`);
    const json = await res.json();
    if (!res.ok) return;
    const creatorEmp = String(json.data?.createdByEmployeeNo || "").trim();
    activeChannelCreatorEmployeeNo = creatorEmp || null;
    const members = json.data?.members || [];
    document.getElementById("chatMemberCount").textContent = `멤버 ${members.length}명`;

    const myEmpMention = currentUser ? String(currentUser.employeeNo || "").trim() : "";
    activeChannelMemberMentionList = members
      .map((m) => ({
        employeeNo: String(m.employeeNo || "").trim(),
        name: String(m.name || "").trim(),
      }))
      .filter((m) => m.employeeNo && m.employeeNo !== myEmpMention);

    const listEl = document.getElementById("memberList");
    listEl.innerHTML = "";
    const myEmp = currentUser ? String(currentUser.employeeNo || "").trim() : "";
    const canKickOthers = myEmp !== "" && creatorEmp !== "" && myEmp === creatorEmp;

    const normalizeOrgValueForDisplay = (v) => {
      const s = v == null ? "" : String(v).trim();
      if (!s) return "";
      const up = s.toUpperCase();
      // DB placeholder 값(예: display_name='TEAM')이 그대로 내려올 때를 대비
      if (up === "TEAM" || up === "JOB_LEVEL" || up === "JOB_POSITION" || up === "JOB_TITLE") return "";
      return s;
    };

    members.forEach(m => {
      const emp = String(m.employeeNo || "").trim();
      const pr = presenceByEmployeeNo.get(emp) || "OFFLINE";
      const prCl = presenceCssClass(pr);
      const department = normalizeOrgValueForDisplay(m.department);
      const jobLevel = normalizeOrgValueForDisplay(m.jobLevel);
      const deptParts = [department, jobLevel].filter(x => x != null && String(x).trim() !== "");
      // 조직/직급이 비어 있는 경우 레거시/시드 불일치로 인해,
      // 최소한 직위/직책이라도 보여주도록 보정한다.
      let orgLine = deptParts.length ? deptParts.map(x => String(x).trim()).join(" · ") : "";
      if (!orgLine) {
        const jobPosition = normalizeOrgValueForDisplay(m.jobPosition);
        const jobTitle = normalizeOrgValueForDisplay(m.jobTitle);
        const fallbackParts = [jobPosition, jobTitle].filter(x => x != null && String(x).trim() !== "");
        orgLine = fallbackParts.length ? fallbackParts.map(x => String(x).trim()).join(" · ") : "조직 미지정";
      }
      const posHtml =
        m.jobPosition != null && String(m.jobPosition).trim() !== ""
          ? `<span class="member-position-txt">${escHtml(String(m.jobPosition).trim())}</span>`
          : "";
      const dutyHtml =
        m.jobTitle != null && String(m.jobTitle).trim() !== ""
          ? `<span class="member-duty-txt">${escHtml(String(m.jobTitle).trim())}</span>`
          : "";
      const showKick = canKickOthers && emp !== "" && emp !== creatorEmp;
      const kickBtnHtml = showKick
        ? `<button type="button" class="btn-member-kick" data-kick-emp="${escHtml(emp)}" data-kick-name="${escHtml(m.name || emp)}" title="채널에서 내보내기">내보내기</button>`
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
            <span class="member-org-txt" title="${escHtml(orgLine)}">조직/직급: ${escHtml(orgLine)}</span>
            ${posHtml}
            ${dutyHtml}
          </span>
        </button>
        ${kickBtnHtml}`;
      li.querySelector(".member-profile-btn").addEventListener("click", () => openUserProfile(emp));
      const kickBtn = li.querySelector(".btn-member-kick");
      if (kickBtn) {
        kickBtn.addEventListener("click", (ev) => {
          ev.stopPropagation();
          removeChannelMemberFromPanel(kickBtn.dataset.kickEmp, kickBtn.dataset.kickName || emp);
        });
      }
      listEl.appendChild(li);
    });
    refreshPresenceDots();
  } catch (err) {
    console.error("멤버 로드 실패", err);
  }
}

async function removeChannelMemberFromPanel(targetEmp, displayName) {
  if (!activeChannelId || !targetEmp) return;
  const label = displayName && String(displayName).trim() ? String(displayName).trim() : targetEmp;
  if (!confirm(`「${label}」님을 이 채널에서 내보낼까요?`)) return;
  try {
    const res = await apiFetch(
      `/api/channels/${activeChannelId}/members?targetEmployeeNo=${encodeURIComponent(targetEmp)}`,
      { method: "DELETE" }
    );
    const json = await res.json().catch(() => ({}));
    if (!res.ok) {
      appendSystemMsg(`내보내기 실패: ${json.error?.message || res.status}`);
      return;
    }
    await loadChannelMembers(activeChannelId);
    await loadMyChannels();
    await loadMessages(activeChannelId);
  } catch (e) {
    console.error(e);
    appendSystemMsg("내보내기 중 오류가 발생했습니다.");
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

/** 타임라인 API: 원글에 달린 댓글 개수(양수만) */
function threadCommentCountFromMsg(msg) {
  const raw = msg.threadCommentCount ?? msg.thread_comment_count;
  if (raw == null || raw === "") return 0;
  const n = Number(raw);
  return Number.isFinite(n) && n > 0 ? Math.floor(n) : 0;
}

/** 댓글 요약 줄용: 「오늘 오전 1:13에」 형태 */
function fmtThreadCommentRelativeLabel(iso) {
  if (!iso) return "";
  const d = new Date(iso);
  if (!Number.isFinite(d.getTime())) return "";
  const now = new Date();
  const startToday = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
  const startThat = new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime();
  const diffDays = Math.round((startThat - startToday) / 86400000);
  const hh = d.getHours();
  const mm = String(d.getMinutes()).padStart(2, "0");
  const ampm = hh < 12 ? "오전" : "오후";
  const h12 = hh % 12 === 0 ? 12 : hh % 12;
  const timePart = `${ampm} ${h12}:${mm}`;
  if (diffDays === 0) return `오늘 ${timePart}에`;
  if (diffDays === -1) return `어제 ${timePart}에`;
  if (diffDays < -1 && diffDays >= -6) return `${-diffDays}일 전 ${timePart}에`;
  return `${d.toLocaleDateString("ko-KR", { month: "numeric", day: "numeric" })} ${timePart}에`;
}

/** 메시지 하단: N개의 댓글 + 마지막 댓글 시각(클릭 시 스레드 모달) */
function attachThreadCommentFooter(row, msg) {
  if (isRenderingThreadModal) return;
  const n = threadCommentCountFromMsg(msg);
  if (n <= 0) return;
  const lastAt = msg.lastCommentAt ?? msg.last_comment_at ?? null;
  const lastName = String(msg.lastCommentSenderName ?? msg.last_comment_sender_name ?? "").trim();
  const initials = avatarInitials(lastName || "?");
  const timeLabel = fmtThreadCommentRelativeLabel(lastAt);
  const rootId = row.dataset.rootMessageId || row.dataset.messageId;
  if (!rootId) return;

  const btn = document.createElement("button");
  btn.type = "button";
  btn.className = "msg-thread-summary";
  btn.dataset.openThreadRoot = rootId;
  btn.innerHTML = `
    <span class="msg-thread-summary-avatar" aria-hidden="true">${escHtml(initials)}</span>
    <span class="msg-thread-summary-main">
      <span class="msg-thread-summary-count">${n}개의 댓글</span>
      <span class="msg-thread-summary-time">${escHtml(timeLabel)}</span>
    </span>
  `;
  btn.addEventListener("click", (e) => {
    e.preventDefault();
    e.stopPropagation();
    openThreadModal(Number(rootId), { targetCommentMessageId: null });
  });
  const body = row.querySelector(".msg-body");
  if (body) body.appendChild(btn);
}

function snippetForReplyComposerBanner(messageId) {
  const id = Number(messageId);
  if (!Number.isFinite(id)) return { senderLabel: "메시지", snippet: "" };
  const row = document.querySelector(`.msg-row.msg-chat[data-message-id="${id}"]`);
  let senderLabel = "메시지";
  let snippet = "";
  if (row) {
    const sBtn = row.querySelector(".msg-sender");
    if (sBtn && sBtn.textContent) senderLabel = String(sBtn.textContent).trim() || senderLabel;
    const tEl = row.querySelector(".msg-text");
    if (tEl && tEl.textContent) snippet = String(tEl.textContent).trim().slice(0, 160);
    if (!snippet) {
      const fn = row.querySelector(".msg-attach-name");
      if (fn && fn.textContent) snippet = "📎 " + String(fn.textContent).trim();
    }
  } else {
    const cached = timelineRootMessageById.get(id);
    if (cached) {
      senderLabel = String(cached.senderName || cached.sender_name || senderLabel).trim() || senderLabel;
      const bodyText = cached.text ?? cached.body ?? "";
      if (bodyText) {
        const filePayload = tryParseFilePayload({ text: bodyText, messageType: cached.messageType });
        snippet = filePayload?.originalFilename
          ? "📎 " + String(filePayload.originalFilename).trim()
          : String(bodyText).replace(/\s+/g, " ").trim().slice(0, 160);
      }
    }
  }
  return { senderLabel, snippet: snippet || "(미리보기 없음)" };
}

function updateReplyComposerBanner() {
  if (!replyComposerBannerEl) return;
  if (replyComposerTargetMessageId == null || !Number.isFinite(replyComposerTargetMessageId)) {
    replyComposerBannerEl.classList.add("hidden");
    return;
  }
  const { senderLabel, snippet } = snippetForReplyComposerBanner(replyComposerTargetMessageId);
  if (replyComposerBannerTitleEl) {
    replyComposerBannerTitleEl.textContent = `${senderLabel}에게 답장`;
  }
  if (replyComposerBannerPreviewEl) {
    replyComposerBannerPreviewEl.textContent = snippet;
  }
  replyComposerBannerEl.classList.remove("hidden");
}

/** 직전 채팅 행(시스템·날짜선 뒤에 있어도 탐색) */
function findLastChatRowIn(container) {
  let el = container.lastElementChild;
  while (el) {
    if (el.classList?.contains("msg-row") && el.classList?.contains("msg-chat")) {
      return el;
    }
    el = el.previousElementSibling;
  }
  return null;
}

/**
 * 타임라인 상 마지막 캘린더일(로컬 YYYY-MM-DD).
 * 마지막 채팅 행의 dateKey 우선, 없으면 가장 가까운 날짜 구분선.
 */
function lastTimelineDateKey(container) {
  const lastChat = findLastChatRowIn(container);
  if (lastChat?.dataset?.dateKey) {
    return lastChat.dataset.dateKey;
  }
  let el = container.lastElementChild;
  while (el) {
    if (el.classList?.contains("msg-date-divider")) {
      return el.dataset.dateKey || "";
    }
    el = el.previousElementSibling;
  }
  return "";
}

/** 다음 줄이 같은 사람·같은 분이면 현재 줄에는 시각 미표시 */
function shouldShowMessageTime(msgs, i) {
  const cur = msgs[i];
  const next = msgs[i + 1];
  const curMt = String(cur.messageType || cur.message_type || "").toUpperCase();
  if (curMt === "SYSTEM") return false;
  if (!next) return true;
  const nextMt = String(next.messageType || next.message_type || "").toUpperCase();
  if (nextMt === "SYSTEM") return true;
  if (String(next.senderId) !== String(cur.senderId)) return true;
  if (minuteKey(next.createdAt) !== minuteKey(cur.createdAt)) return true;
  return false;
}

/** 아바타+이름 행: 이전 메시지와 발신자가 다를 때만 */
function shouldShowAvatarForMessage(msgs, i) {
  if (i === 0) return true;
  const cur = msgs[i];
  const prev = msgs[i - 1];
  const curMt = String(cur.messageType || cur.message_type || "").toUpperCase();
  const prevMt = String(prev.messageType || prev.message_type || "").toUpperCase();
  if (prevMt === "SYSTEM" || curMt === "SYSTEM") return true;
  return String(prev.senderId) !== String(cur.senderId);
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
  const mid = msg.messageId ?? msg.message_id;
  if (mid != null) {
    div.dataset.messageId = String(mid);
    const pid = msg.parentMessageId ?? msg.parent_message_id;
    div.dataset.rootMessageId = pid == null ? String(mid) : String(pid);
  }

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

  attachThreadCommentFooter(div, msg);
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
  const mid = msg.messageId ?? msg.message_id;
  if (mid != null) {
    div.dataset.messageId = String(mid);
    const pid = msg.parentMessageId ?? msg.parent_message_id;
    div.dataset.rootMessageId = pid == null ? String(mid) : String(pid);
  }

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
  attachThreadCommentFooter(div, msg);
  return div;
}

function createMessageRowElement(msg, { showAvatar, showTime }) {
  const mt = String(msg.messageType || msg.message_type || "").toUpperCase();
  if (mt === "SYSTEM") {
    const div = document.createElement("div");
    div.className = "msg-system";
    if (msg.messageId != null) div.dataset.messageId = String(msg.messageId);
    div.textContent = msg.text || "";
    return div;
  }

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
  const mid = msg.messageId ?? msg.message_id;
  if (mid != null) {
    div.dataset.messageId = String(mid);
    const pid = msg.parentMessageId ?? msg.parent_message_id;
    div.dataset.rootMessageId = pid == null ? String(mid) : String(pid);
  }

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
          <span class="msg-text">${formatMessageWithMentions(msg.text)}</span>${timeHtml}
        </div>
      </div>`;
  } else {
    div.innerHTML = `
      <div class="msg-spacer"></div>
      <div class="msg-body">
        <div class="msg-content-row">
          <span class="msg-text">${formatMessageWithMentions(msg.text)}</span>${timeHtml}
        </div>
      </div>`;
  }
  attachThreadCommentFooter(div, msg);
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
  // 댓글 전송 등에서 스크롤 위치를 유지해야 하는 경우(= loadMessages(preserveScroll=true))
  // render 단계에서 자동 하단 스크롤을 하지 않고 loadMessages에서 복원한다.
  if (pendingScrollRestoreRatio == null) {
    messagesEl.scrollTop = messagesEl.scrollHeight;
  }
}

function createReplyTimelineRowElement(tlMsg, { showAvatar, showTime }) {
  const row = createMessageRowElement(tlMsg, { showAvatar, showTime });
  row.classList.add("msg-has-reply");

  row.dataset.isReply = "true";
  row.dataset.replyToKind = tlMsg.replyToKind || "";
  row.dataset.replyToMessageId = tlMsg.replyToMessageId != null ? String(tlMsg.replyToMessageId) : "";
  row.dataset.replyToRootMessageId = tlMsg.replyToRootMessageId != null ? String(tlMsg.replyToRootMessageId) : "";
  if (tlMsg.replyToRootMessageId != null) {
    row.dataset.rootMessageId = String(tlMsg.replyToRootMessageId);
  }

  const preview = String(tlMsg.replyToPreview || "").trim();
  const fallbackKind = tlMsg.replyToKind === "COMMENT" ? "댓글" : "원글";
  const targetName = String(
    tlMsg.replyToSenderName ?? tlMsg.reply_to_sender_name ?? ""
  ).trim();
  const titleName = targetName || fallbackKind;
  const snippet = preview || "(미리보기 없음)";

  const replyToBlock = document.createElement("div");
  replyToBlock.className = "msg-reply-to";
  replyToBlock.innerHTML = `
    <button type="button" class="msg-reply-to-card" aria-label="답장 대상으로 이동">
      <div class="msg-reply-to-card-title">${escHtml(titleName)}에게 답장</div>
      <div class="msg-reply-to-card-snippet">${escHtml(snippet)}</div>
    </button>
  `;

  const body = row.querySelector(".msg-body");
  if (body) {
    const meta = row.querySelector(".msg-meta");
    if (meta) meta.insertAdjacentElement("afterend", replyToBlock);
    else body.prepend(replyToBlock);
  }

  const cardBtn = replyToBlock.querySelector(".msg-reply-to-card");
  if (cardBtn) {
    cardBtn.addEventListener("click", (e) => {
      e.preventDefault();
      e.stopPropagation();
      jumpToReplyTarget({
        replyToKind: row.dataset.replyToKind,
        replyToMessageId: row.dataset.replyToMessageId,
        replyToRootMessageId: row.dataset.replyToRootMessageId,
      });
    });
  }
  return row;
}

function renderTimelineMessages(items) {
  messagesEl.innerHTML = "";
  if (!items || !items.length) return;

  let prevDk = null;
  items.forEach((m, i) => {
    const dk = dateKeyLocal(m.createdAt);
    if (prevDk !== dk) {
      messagesEl.appendChild(createDateDividerElement(m.createdAt));
      prevDk = dk;
    }
    const showAvatar = shouldShowAvatarForMessage(items, i);
    const showTime = shouldShowMessageTime(items, i);

    const row = m.isReply
      ? createReplyTimelineRowElement(m, { showAvatar, showTime })
      : createMessageRowElement(m, { showAvatar, showTime });
    const mid = m.messageId ?? m.message_id;
    if (mid != null) row.id = `msg-${mid}`;
    messagesEl.appendChild(row);
  });
  trimMessages();
  refreshPresenceDots();
  if (pendingScrollRestoreRatio == null) {
    messagesEl.scrollTop = messagesEl.scrollHeight;
  }
}

/* ==========================================================================
 * 스레드 모달 / 답글 이동 / 컨텍스트 메뉴
 * ========================================================================== */
function hideMessageContextMenu() {
  if (!messageContextMenuEl) return;
  messageContextMenuEl.classList.add("hidden");
}

function setReplyComposerTarget(messageId) {
  if (!messageId || !Number.isFinite(Number(messageId))) return;
  replyComposerTargetMessageId = Number(messageId);
  if (messageInputEl) messageInputEl.placeholder = "답글 입력…";
  updateReplyComposerBanner();
  messageInputEl?.focus?.();
}

function clearReplyComposerTarget() {
  replyComposerTargetMessageId = null;
  if (messageInputEl) messageInputEl.placeholder = replyComposerOriginalPlaceholder || "메시지 보내기… @이름 멘션 · 이미지 Ctrl+V";
  if (replyComposerBannerEl) replyComposerBannerEl.classList.add("hidden");
}

function clearThreadFilePreview() {
  if (threadPendingFilePreviewUrl) {
    try {
      URL.revokeObjectURL(threadPendingFilePreviewUrl);
    } catch {
      /* ignore */
    }
  }
  threadPendingFilePreviewUrl = null;
  threadPendingFile = null;
  if (threadFilePreviewThumbEl) {
    threadFilePreviewThumbEl.classList.add("hidden");
    threadFilePreviewThumbEl.removeAttribute("src");
  }
  if (threadFilePreviewEl) threadFilePreviewEl.classList.add("hidden");
  if (threadFilePreviewNameEl) threadFilePreviewNameEl.textContent = "";
}

function setThreadComposerPendingFile(file) {
  if (!file) return;
  threadPendingFile = file;
  if (threadFilePreviewNameEl) threadFilePreviewNameEl.textContent = file.name || "첨부파일";
  const isImage = String(file.type || "").toLowerCase().startsWith("image/");
  if (threadFilePreviewThumbEl && threadFilePreviewEl) {
    const url = URL.createObjectURL(file);
    if (threadPendingFilePreviewUrl) {
      try {
        URL.revokeObjectURL(threadPendingFilePreviewUrl);
      } catch {
        /* ignore */
      }
    }
    threadPendingFilePreviewUrl = url;
    if (isImage) {
      threadFilePreviewThumbEl.src = url;
      threadFilePreviewThumbEl.classList.remove("hidden");
    } else {
      threadFilePreviewThumbEl.src = "";
      threadFilePreviewThumbEl.classList.add("hidden");
    }
    threadFilePreviewEl.classList.remove("hidden");
  }
}

async function jumpToReplyTarget({ replyToKind, replyToMessageId, replyToRootMessageId }) {
  const kind = String(replyToKind || "").trim();
  const rootId = Number(replyToRootMessageId);
  const targetId = Number(replyToMessageId);

  if (!Number.isFinite(rootId) || !kind) return;

  if (kind === "ROOT") {
    const el = document.getElementById(`msg-${rootId}`);
    if (el) el.scrollIntoView({ behavior: "smooth", block: "center" });
    hideMessageContextMenu();
    return;
  }

  if (kind === "COMMENT") {
    await openThreadModal(rootId, { targetCommentMessageId: Number.isFinite(targetId) ? targetId : null });
  }
}

async function openThreadModal(rootMessageId, { targetCommentMessageId = null } = {}) {
  if (!activeChannelId || !currentUser) return;
  const rootId = Number(rootMessageId);
  if (!Number.isFinite(rootId)) return;

  threadRootMessageId = rootId;
  // 댓글 모달 입력으로 전환하므로 답글 모드를 해제한다.
  if (replyComposerTargetMessageId != null) clearReplyComposerTarget();
  if (threadMessageInputEl) threadMessageInputEl.value = "";
  clearThreadFilePreview();
  hideMessageContextMenu();
  openModal("modalThread");
  isRenderingThreadModal = true;

  // ROOT 메시지(타임라인에서 캐시로 재사용)
  const rootMsg = timelineRootMessageById.get(rootId);
  threadRootContainerEl.innerHTML = "";
  threadCommentsContainerEl.innerHTML = "";

  if (!rootMsg) {
    threadRootContainerEl.textContent = "원글을 찾을 수 없습니다.";
    isRenderingThreadModal = false;
    return;
  }

  const rootRow = createMessageRowElement(rootMsg, { showAvatar: true, showTime: true });
  threadRootContainerEl.appendChild(rootRow);

  try {
    const res = await apiFetch(`/api/channels/${activeChannelId}/messages/${rootId}/replies`);
    const json = await res.json().catch(() => ({}));
    if (!res.ok) {
      threadCommentsContainerEl.textContent = "댓글 불러오기 실패";
      isRenderingThreadModal = false;
      return;
    }

    const children = Array.isArray(json.data) ? json.data : [];
    const comments = children.filter((m) => String(m.messageType || m.message_type || "").toUpperCase().startsWith("COMMENT"));

    // 각 COMMENT 아래의 REPLY를 병렬 조회
    const commentReplyPairs = await Promise.all(
      comments.map(async (c) => {
        const cid = Number(c.messageId ?? c.message_id);
        let nestedReplies = [];
        try {
          const rr = await apiFetch(`/api/channels/${activeChannelId}/messages/${cid}/replies`);
          const rj = await rr.json().catch(() => ({}));
          const nested = Array.isArray(rj.data) ? rj.data : [];
          nestedReplies = nested.filter((m) => String(m.messageType || m.message_type || "").toUpperCase().startsWith("REPLY"));
        } catch {
          nestedReplies = [];
        }
        return { comment: c, replies: nestedReplies, commentId: cid };
      })
    );

    threadCommentsContainerEl.innerHTML = "";
    for (const { comment, replies, commentId } of commentReplyPairs) {
      const node = document.createElement("div");
      node.className = "thread-comment-node";

      const commentRow = createMessageRowElement(comment, { showAvatar: true, showTime: true });
      commentRow.id = `thread-msg-${commentId}`;
      node.appendChild(commentRow);

      if (replies && replies.length) {
        const repliesWrap = document.createElement("div");
        repliesWrap.className = "thread-comment-replies";
        const filePayload = tryParseFilePayload(comment);
        const replyToPreview = filePayload
          ? String(filePayload.originalFilename || "첨부파일")
          : mentionPreviewForToastClient(comment.text || "", 160);

        replies.forEach((r) => {
          const rid = Number(r.messageId ?? r.message_id);
          const replyItem = {
            ...r,
            isReply: true,
            replyToKind: "COMMENT",
            replyToMessageId: commentId,
            replyToRootMessageId: rootId,
            replyToPreview: replyToPreview,
          };
          const replyRow = createReplyTimelineRowElement(replyItem, { showAvatar: false, showTime: true });
          replyRow.id = `thread-msg-${rid}`;
          repliesWrap.appendChild(replyRow);
        });

        node.appendChild(repliesWrap);
      }

      threadCommentsContainerEl.appendChild(node);
    }
    if (targetCommentMessageId != null) {
      const targetEl = document.getElementById(`thread-msg-${targetCommentMessageId}`);
      if (targetEl) targetEl.scrollIntoView({ behavior: "smooth", block: "center" });
    }
  } catch (e) {
    threadCommentsContainerEl.textContent = "스레드 로딩 중 오류";
    console.error(e);
    isRenderingThreadModal = false;
  }
  isRenderingThreadModal = false;
}

async function sendThreadComment() {
  if (!activeChannelId || !currentUser) return;
  const rootId = threadRootMessageId;
  if (!Number.isFinite(Number(rootId))) return;

  const text = threadMessageInputEl?.value?.trim?.() || "";

  try {
    // 댓글 파일이 있으면 먼저 업로드(텍스트가 있으면 모달은 즉시 리로드하지 않음)
    if (threadPendingFile) {
      const reloadMode = text ? "none" : "thread";
      await uploadFile(threadPendingFile, {
        parentMessageId: rootId,
        threadKind: "COMMENT",
        reloadMode,
      });
      clearThreadFilePreview();
      if (!text) {
        threadMessageInputEl.value = "";
        return;
      }
    }

    if (!text) return;

    const res = await apiFetch(
      `/api/channels/${activeChannelId}/messages/${rootId}/comments`,
      {
        method: "POST",
        body: JSON.stringify({ senderId: currentUser.employeeNo, text }),
      }
    );
    const json = await res.json().catch(() => ({}));
    if (!res.ok) throw new Error(json.error?.message || "댓글 전송 실패");

    const createdId = json.data?.messageId ?? null;
    threadMessageInputEl.value = "";
    await openThreadModal(rootId, { targetCommentMessageId: createdId });
    if (activeChannelId) await loadMessages(activeChannelId, { preserveScroll: true });
  } catch (e) {
    appendSystemMsg("댓글 전송 실패: " + (e?.message || "오류"));
    console.error(e);
  }
}

// 스레드 모달: 이벤트 바인딩
if (threadBtnAttachEl && threadFileInputEl) {
  threadBtnAttachEl.addEventListener("click", () => threadFileInputEl.click());
}
if (threadBtnClearFileEl) {
  threadBtnClearFileEl.addEventListener("click", clearThreadFilePreview);
}
if (replyComposerBannerCloseEl) {
  replyComposerBannerCloseEl.addEventListener("click", () => clearReplyComposerTarget());
}
if (threadFileInputEl) {
  threadFileInputEl.addEventListener("change", () => {
    const file = threadFileInputEl.files?.[0] || null;
    if (!file) return;
    setThreadComposerPendingFile(file);
    // 같은 파일을 다시 선택해도 change 이벤트가 뜨도록 초기화
    threadFileInputEl.value = "";
  });
}
if (threadBtnSendEl) {
  threadBtnSendEl.addEventListener("click", sendThreadComment);
}
if (threadMessageInputEl) {
  threadMessageInputEl.addEventListener("keydown", (e) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      sendThreadComment();
    }
  });
}

// 메시지 우클릭 컨텍스트 메뉴 이벤트(댓글/답글)
messagesEl?.addEventListener("contextmenu", (e) => {
  const row = e.target.closest(".msg-row.msg-chat");
  if (!row) return;
  const mid = row.dataset.messageId ? Number(row.dataset.messageId) : null;
  const rootMid = row.dataset.rootMessageId ? Number(row.dataset.rootMessageId) : mid;
  if (!Number.isFinite(mid) || !Number.isFinite(rootMid)) return;

  contextMenuSelectedMessageId = mid;
  contextMenuSelectedRootMessageId = rootMid;
  contextMenuSelectedIsReply = row.dataset.isReply === "true";

  const commentBtn = messageContextMenuEl?.querySelector('[data-action="comment"]');
  const replyBtn = messageContextMenuEl?.querySelector('[data-action="reply"]');
  if (commentBtn) commentBtn.disabled = false;
  if (replyBtn) replyBtn.disabled = contextMenuSelectedIsReply;

  const menuW = 190;
  const x = Math.min(e.clientX, window.innerWidth - menuW);
  const y = Math.min(e.clientY, window.innerHeight - 90);
  if (messageContextMenuEl) {
    messageContextMenuEl.style.left = `${x}px`;
    messageContextMenuEl.style.top = `${y}px`;
    messageContextMenuEl.classList.remove("hidden");
  }
  e.preventDefault();
});

document.addEventListener("click", (e) => {
  if (!messageContextMenuEl || messageContextMenuEl.classList.contains("hidden")) return;
  const inMenu = e.target && messageContextMenuEl.contains(e.target);
  if (!inMenu) hideMessageContextMenu();
});

if (messageContextMenuEl) {
  messageContextMenuEl.addEventListener("click", async (e) => {
    const btn = e.target.closest(".context-menu-item");
    if (!btn) return;
    const action = btn.dataset.action;

    const rootId = contextMenuSelectedRootMessageId;
    const selectedId = contextMenuSelectedMessageId;
    hideMessageContextMenu();

    if (action === "comment") {
      await openThreadModal(rootId, { targetCommentMessageId: null });
      threadMessageInputEl?.focus?.();
      return;
    }

    if (action === "reply") {
      if (contextMenuSelectedIsReply) return;
      setReplyComposerTarget(selectedId);
      closeMentionSuggest();
      return;
    }
  });
}

/** 소켓으로 도착한 한 줄: 직전 줄과 같은 사람·같은 분이면 직전 줄에서 시각 제거 후 추가 */
function appendMessageRealtime(msg) {
  const sid = String(msg.senderId ?? "").trim();
  const mk = minuteKey(msg.createdAt);
  const dk = dateKeyLocal(msg.createdAt);

  const prevDk = lastTimelineDateKey(messagesEl);
  if (dk && prevDk !== dk) {
    messagesEl.appendChild(createDateDividerElement(msg.createdAt));
  }

  // speech block 분리를 위해, 직전 DOM이 시스템/날짜선이면 "연속 발화"로 보지 않는다.
  const lastEl = messagesEl.lastElementChild;
  const adjacentPrevChat =
    lastEl && lastEl.classList?.contains("msg-row") && lastEl.classList?.contains("msg-chat")
      ? lastEl
      : null;
  if (
    adjacentPrevChat &&
    adjacentPrevChat.dataset.senderId === sid &&
    adjacentPrevChat.dataset.minuteKey === mk
  ) {
    const timeEl = adjacentPrevChat.querySelector(".msg-time");
    if (timeEl) timeEl.remove();
  }

  const beforeAppendChat = adjacentPrevChat;
  const showAvatar = !beforeAppendChat || beforeAppendChat.dataset.senderId !== sid;
  const row = createMessageRowElement(msg, { showAvatar, showTime: true });
  const mid = msg.messageId ?? msg.message_id;
  if (mid != null) row.id = `msg-${mid}`;
  messagesEl.appendChild(row);
  trimMessages();
  refreshPresenceDots();
  messagesEl.scrollTop = messagesEl.scrollHeight;
}

/**
 * 채팅 영역 시스템 한 줄. 소켓·API 중복 표시 방지용 messageId(선택) 지원.
 * @param {{ messageId?: number|string }} [options]
 */
function appendSystemMsg(text, options = {}) {
  const mid = options.messageId != null && options.messageId !== "" ? String(options.messageId) : "";
  if (mid) {
    const existing = messagesEl.querySelector(`.msg-system[data-message-id="${mid}"]`);
    if (existing) return;
  }
  if (options.createdAt) {
    const dk = dateKeyLocal(options.createdAt);
    const prevDk = lastTimelineDateKey(messagesEl);
    if (dk && prevDk !== dk) {
      messagesEl.appendChild(createDateDividerElement(options.createdAt));
    }
  }
  const div = document.createElement("div");
  div.className = "msg-system";
  if (mid) div.dataset.messageId = mid;
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
    if (currentUser?.employeeNo) {
      socket.emit("presence:set", { employeeNo: currentUser.employeeNo, status: "ONLINE" });
    }
    if (activeChannelId) socket.emit("channel:join", activeChannelId);
    await fetchPresenceSnapshot();
    refreshPresenceDots();
    if (activeChannelId) loadChannelMembers(activeChannelId);
  });

  socket.on("reconnect", async () => {
    if (currentUser?.employeeNo) {
      socket.emit("presence:set", { employeeNo: currentUser.employeeNo, status: "ONLINE" });
    }
    if (activeChannelId) socket.emit("channel:join", activeChannelId);
    await fetchPresenceSnapshot();
    refreshPresenceDots();
    if (activeChannelId) loadChannelMembers(activeChannelId);
  });

  /** Realtime 서버가 presence:set 직후 내려주는 전체 목록(타 클라이언트와 상태 일치) */
  socket.on("presence:snapshot", (payload) => {
    const rows = payload?.data;
    if (!Array.isArray(rows)) return;
    presenceByEmployeeNo.clear();
    rows.forEach((p) => {
      const emp = String(p?.employeeNo ?? "").trim();
      if (emp) presenceByEmployeeNo.set(emp, String(p.status || "OFFLINE").toUpperCase());
    });
    refreshPresenceDots();
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
    const cid = Number(msg.channelId);
    if (cid === activeChannelId) {
      appendMessageRealtime(msg);
      // `mention:notify` 경로가 누락/지연되는 경우 대비:
      // 현재 채널에서 내 멘션이 포함된 메시지를 받으면 클라이언트가 폴백 토스트를 띄운다.
      maybeShowMentionToastFromMessage(msg);
      if (msg.messageId != null) {
        markChannelReadUpTo(activeChannelId, msg.messageId).then(() => loadMyChannels());
      }
    } else {
      scheduleRefreshMyChannels();
    }
  });

  socket.on("channel:system", (p) => {
    const cid = Number(p.channelId);
    if (cid !== activeChannelId) {
      scheduleRefreshMyChannels();
      return;
    }
    appendSystemMsg(p.text || "", { messageId: p.messageId, createdAt: p.createdAt });
    const mid = p.messageId != null ? Number(p.messageId) : NaN;
    if (Number.isFinite(mid)) {
      markChannelReadUpTo(cid, mid).then(() => loadMyChannels());
    }
  });

  socket.on("message:error", (err) => {
    // sendMessage()가 이미 소켓 실패 → API 폴백을 수행하는 중일 수 있어,
    // 이때 뜨는 `message:error`는 사용자에게 중복 오류로 보일 수 있다.
    if (socketSendInFlight) return;
    appendSystemMsg("전송 실패: " + (err?.message || "알 수 없는 오류"));
  });

  socket.on("mention:notify", (p) => {
    pushMentionToast(p);
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
    }, 8000);

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
  if (m.messageId != null) {
    await markChannelReadUpTo(channelId, m.messageId);
    await loadMyChannels();
  }
}

/* ==========================================================================
 * @멘션 자동완성 (토큰 `@{사번|표시명}`)
 * ========================================================================== */
let mentionSuggestTimer = null;
let mentionSuggestResults = [];
let mentionSelectedIndex = 0;
// 동일 메시지에 대해 토스트가 중복 표시되지 않도록(mention:notify + 폴백 경로) 제어한다.
const shownMentionToastMessageIds = new Set();

// socket으로 전송 시도 중이면, server가 내려주는 `message:error` 시스템 문구가
// (API 폴백 성공 케이스에서도) 중복으로 남을 수 있다. 이 플래그로 억제한다.
let socketSendInFlight = false;

// composer 화면에는 `@표시명`만 보이게 삽입하고,
// 전송 시에만 `@{사번|표시명}` 토큰으로 변환하기 위한 매핑.
// key: 표시명(display), value: 사번(employeeNo)
const mentionDisplayToEmployeeNo = new Map();

function getMentionQueryAtCaret(value, caret) {
  const before = value.slice(0, caret);
  const at = before.lastIndexOf("@");
  if (at < 0) return null;
  const afterAt = before.slice(at + 1);
  if (/\s/.test(afterAt)) return null;
  return { start: at, query: afterAt };
}

function getMentionSuggestEl() {
  return document.getElementById("mentionSuggest");
}

function closeMentionSuggest() {
  const el = getMentionSuggestEl();
  if (el) {
    el.classList.add("hidden");
    el.innerHTML = "";
  }
  mentionSuggestResults = [];
  mentionSelectedIndex = 0;
}

function isMentionSuggestOpen() {
  const el = getMentionSuggestEl();
  return el && !el.classList.contains("hidden") && mentionSuggestResults.length > 0;
}

function renderMentionSuggestList() {
  const el = getMentionSuggestEl();
  if (!el) return;
  el.innerHTML = "";
  mentionSuggestResults.forEach((u, i) => {
    const row = document.createElement("button");
    row.type = "button";
    row.className = "mention-suggest-item" + (i === mentionSelectedIndex ? " is-active" : "");
    row.dataset.index = String(i);
    row.innerHTML = `<span class="mention-suggest-name">${escHtml(u.name || "")}</span><span class="mention-suggest-meta">${escHtml(u.employeeNo || "")}</span>`;
    row.addEventListener("mousedown", (ev) => {
      ev.preventDefault();
      mentionPickIndex(i);
    });
    el.appendChild(row);
  });
}

function mentionSelectMove(delta) {
  if (!mentionSuggestResults.length) return;
  mentionSelectedIndex = (mentionSelectedIndex + delta + mentionSuggestResults.length) % mentionSuggestResults.length;
  renderMentionSuggestList();
}

function mentionPickSelected() {
  mentionPickIndex(mentionSelectedIndex);
}

function mentionPickIndex(i) {
  const u = mentionSuggestResults[i];
  if (!u || !messageInputEl) return;
  const emp = String(u.employeeNo || "").trim();
  if (!emp) return;
  const display = String(u.name || emp).replace(/[|}]/g, "").trim() || emp;
  const v = messageInputEl.value;
  const caret = messageInputEl.selectionStart;
  const info = getMentionQueryAtCaret(v, caret);
  if (!info) return;
  mentionDisplayToEmployeeNo.set(display, emp);
  const visible = `@${display}`;
  const next = v.slice(0, info.start) + visible + " " + v.slice(caret);
  messageInputEl.value = next;
  const pos = info.start + visible.length + 1;
  messageInputEl.setSelectionRange(pos, pos);
  messageInputEl.focus();
  closeMentionSuggest();
}

function applyMentionTokensForSend(text) {
  if (!text) return text;
  let out = String(text);
  for (const [display, emp] of mentionDisplayToEmployeeNo.entries()) {
    const visible = `@${display}`;
    const token = `@{${emp}|${display}}`;
    if (out.includes(visible)) {
      out = out.split(visible).join(token);
    }
  }
  return out;
}

async function fetchMentionUsers(query) {
  const qRaw = String(query || "").trim();
  if (!qRaw || !currentUser || !activeChannelId) return [];
  const q = qRaw.toLowerCase();
  let list = activeChannelMemberMentionList;
  if (!list.length) {
    try {
      const res = await apiFetch(`/api/channels/${activeChannelId}`);
      const json = await res.json();
      if (res.ok) {
        const members = json.data?.members || [];
        const myEmp = String(currentUser.employeeNo || "").trim();
        activeChannelMemberMentionList = members
          .map((m) => ({
            employeeNo: String(m.employeeNo || "").trim(),
            name: String(m.name || "").trim(),
          }))
          .filter((m) => m.employeeNo && m.employeeNo !== myEmp);
        list = activeChannelMemberMentionList;
      }
    } catch {
      return [];
    }
  }
  if (!list.length) return [];
  const matches = list.filter((u) => {
    const emp = (u.employeeNo || "").toLowerCase();
    const nm = (u.name || "").toLowerCase();
    return emp.includes(q) || nm.includes(q);
  });
  return matches.slice(0, 8);
}

function scheduleMentionSuggestUpdate() {
  if (!messageInputEl || !document.getElementById("viewChat") || document.getElementById("viewChat").classList.contains("hidden")) {
    closeMentionSuggest();
    return;
  }
  if (mentionSuggestTimer) clearTimeout(mentionSuggestTimer);
  mentionSuggestTimer = setTimeout(async () => {
    mentionSuggestTimer = null;
    const v = messageInputEl.value;
    const caret = messageInputEl.selectionStart;
    const info = getMentionQueryAtCaret(v, caret);
    const el = getMentionSuggestEl();
    if (!info || !el) {
      closeMentionSuggest();
      return;
    }
    const users = await fetchMentionUsers(info.query);
    if (!users.length) {
      closeMentionSuggest();
      return;
    }
    mentionSuggestResults = users;
    mentionSelectedIndex = 0;
    el.classList.remove("hidden");
    renderMentionSuggestList();
  }, 200);
}

function pushMentionToast(p) {
  const stack = document.getElementById("mentionToastStack");
  if (!stack || !p) return;
  if (p.messageId != null) {
    const mid = String(p.messageId);
    if (shownMentionToastMessageIds.has(mid)) return;
    shownMentionToastMessageIds.add(mid);
  }
  const cid = Number(p.channelId);
  if (!Number.isFinite(cid)) return;
  const channelName = String(p.channelName || "채널");
  const channelType = String(p.channelType || "PUBLIC");
  const senderName = String(p.senderName || "");
  const preview = String(p.messagePreview || "").slice(0, 160);
  const toast = document.createElement("button");
  toast.type = "button";
  toast.className = "mention-toast";
  toast.innerHTML = `<span class="mention-toast-title">멘션</span><span class="mention-toast-sub">${escHtml(senderName)} · ${escHtml(channelName)}</span><span class="mention-toast-preview">${escHtml(preview)}</span>`;
  toast.addEventListener("click", () => {
    toast.remove();
    selectChannel(cid, channelName, channelType);
  });
  stack.appendChild(toast);
  setTimeout(() => {
    if (toast.parentNode) toast.remove();
  }, 25_000);
}

function extractMentionEmployeeNosFromTextClient(text) {
  const s = String(text || "");
  const re = /@\{([^}]*)\}/g;
  const out = new Set();
  let m;
  while ((m = re.exec(s)) !== null && out.size < 20) {
    const inner = String(m[1] ?? "");
    const pipe = inner.indexOf("|");
    const emp = (pipe >= 0 ? inner.slice(0, pipe) : inner).trim();
    if (emp) out.add(emp);
  }
  return [...out];
}

function mentionPreviewForToastClient(body, maxLen = 120) {
  const s = String(body || "")
    .replace(/@\{([^}|]+)\|([^}]+)\}/g, "@$2")
    .replace(/@\{([^}]+)\}/g, "@$1");
  return s.length <= maxLen ? s : s.slice(0, maxLen) + "…";
}

function maybeShowMentionToastFromMessage(msg) {
  if (!currentUser || !msg) return;
  const receiverEmp = String(currentUser.employeeNo ?? "").trim();
  if (!receiverEmp) return;

  const senderEmp = String(msg.senderId ?? "").trim();
  if (senderEmp && receiverEmp === senderEmp) return; // 자기 멘션은 토스트 제외

  const cid = Number(msg.channelId);
  if (!Number.isFinite(cid)) return;
  if (activeChannelId == null || Number(activeChannelId) !== cid) return; // 폴백은 현재 채널에서만

  const mentioned = extractMentionEmployeeNosFromTextClient(msg.text || "");
  if (!mentioned.includes(receiverEmp)) return;

  const channelName =
    document.getElementById("chatChannelName")?.textContent || "채널";
  const channelType = activeChannelType || "PUBLIC";

  pushMentionToast({
    channelId: cid,
    channelName,
    channelType,
    senderName: String(msg.senderName || ""),
    messagePreview: mentionPreviewForToastClient(msg.text || "", 160),
    messageId: msg.messageId != null ? Number(msg.messageId) : null,
  });
}

/* ==========================================================================
 * 메시지 전송
 * ========================================================================== */
async function sendMessage() {
  if (!activeChannelId || !currentUser) return;
  const text = messageInputEl.value.trim();
  const preparedText = applyMentionTokensForSend(text);
  closeMentionSuggest();

  // 답글 모드: 선택된 메시지를 parent로 해서 REPLY 저장
  if (replyComposerTargetMessageId != null) {
    const parentMessageId = replyComposerTargetMessageId;

    if (pendingFile) {
      await uploadFile(pendingFile, {
        parentMessageId,
        threadKind: "REPLY",
        reloadMode: "timeline",
      });
      clearFilePreview();
      if (!preparedText) {
        messageInputEl.value = "";
        clearReplyComposerTarget();
        mentionDisplayToEmployeeNo.clear();
        return;
      }
    }

    if (!preparedText) return;

    try {
      const res = await apiFetch(
        `/api/channels/${activeChannelId}/messages/${parentMessageId}/replies`,
        {
          method: "POST",
          body: JSON.stringify({ senderId: currentUser.employeeNo, text: preparedText }),
        }
      );
      const json = await res.json().catch(() => ({}));
      if (!res.ok) throw new Error(json.error?.message || "답글 전송 실패");

      messageInputEl.value = "";
      clearReplyComposerTarget();
      mentionDisplayToEmployeeNo.clear();
      await loadMessages(activeChannelId);
    } catch (e) {
      appendSystemMsg("답글 전송 실패: " + (e?.message || "오류"));
      console.error(e);
    }
    return;
  }

  // 파일이 있으면 파일 먼저 업로드
  if (pendingFile) {
    await uploadFile(pendingFile);
    clearFilePreview();
    if (!preparedText) { messageInputEl.value = ""; mentionDisplayToEmployeeNo.clear(); return; }
  }

  if (!preparedText) return;
  try {
    socketSendInFlight = true;
    await sendMessageViaSocket(activeChannelId, currentUser.employeeNo, preparedText);
  } catch (socketErr) {
    // 소켓 저장이 실패해도 API 경로로 한 번 더 시도해 전송 유실을 줄인다.
    console.warn("[sendMessage] socket 전송 실패, API 폴백 시도:", socketErr);
    try {
      await sendMessageViaApi(activeChannelId, currentUser.employeeNo, preparedText);
    } catch (apiErr) {
      appendSystemMsg("전송 실패: " + (apiErr?.message || "오류"));
      return;
    }
  } finally {
    socketSendInFlight = false;
  }
  messageInputEl.value = "";
  mentionDisplayToEmployeeNo.clear();
}

document.getElementById("btnSend").addEventListener("click", sendMessage);
messageInputEl.addEventListener("keydown", (e) => {
  if (isMentionSuggestOpen()) {
    if (e.key === "ArrowDown") {
      e.preventDefault();
      mentionSelectMove(1);
      return;
    }
    if (e.key === "ArrowUp") {
      e.preventDefault();
      mentionSelectMove(-1);
      return;
    }
    if (e.key === "Enter" || e.key === "Tab") {
      e.preventDefault();
      mentionPickSelected();
      return;
    }
    if (e.key === "Escape") {
      e.preventDefault();
      closeMentionSuggest();
      if (replyComposerTargetMessageId != null) clearReplyComposerTarget();
      return;
    }
  }
  if (e.key === "Escape") {
    if (replyComposerTargetMessageId != null) {
      e.preventDefault();
      clearReplyComposerTarget();
    }
    return;
  }
  if (e.key === "Enter" && !e.shiftKey) {
    e.preventDefault();
    sendMessage();
  }
});
messageInputEl.addEventListener("input", () => {
  scheduleMentionSuggestUpdate();
});
messageInputEl.addEventListener("blur", () => {
  setTimeout(() => closeMentionSuggest(), 250);
});

/* ==========================================================================
 * 파일 업로드
 * ========================================================================== */
document.getElementById("btnAttach").addEventListener("click", () => {
  document.getElementById("fileInput").click();
});

function setComposerPendingFile(file) {
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
}

document.getElementById("fileInput").addEventListener("change", (e) => {
  const file = e.target.files[0];
  if (!file) return;
  setComposerPendingFile(file);
  e.target.value = "";
});

/**
 * 채팅 패널 포커스 시 클립보드 이미지(Ctrl+V)를 첨부 미리보기와 동일 경로로 연결한다.
 * 캡처 단계에서 처리해 메시지 입력란·메시지 목록 등 하위 요소에 포커스가 있어도 동작한다.
 */
function handleChatImagePaste(e) {
  if (!activeChannelId || !currentUser) return;
  const overlay = e.target && e.target.closest(".modal-overlay");
  if (overlay && !overlay.classList.contains("hidden")) return;

  const dt = e.clipboardData;
  if (!dt) return;

  let imageFile = null;
  const items = dt.items;
  if (items && items.length > 0) {
    for (let i = 0; i < items.length; i++) {
      const it = items[i];
      if (it.kind === "file" && it.type && it.type.startsWith("image/")) {
        const f = it.getAsFile();
        if (f && f.size > 0) {
          imageFile = f;
          break;
        }
      }
    }
  }
  if (!imageFile && dt.files && dt.files.length > 0) {
    for (let i = 0; i < dt.files.length; i++) {
      const f = dt.files[i];
      if (f.type && f.type.startsWith("image/")) {
        imageFile = f;
        break;
      }
    }
  }
  if (!imageFile) return;

  e.preventDefault();
  const mime = imageFile.type || "image/png";
  let ext = "png";
  if (mime === "image/jpeg" || mime === "image/jpg") ext = "jpg";
  else if (mime === "image/gif") ext = "gif";
  else if (mime === "image/webp") ext = "webp";
  else if (mime === "image/png") ext = "png";

  const rawName = imageFile.name ? String(imageFile.name).trim() : "";
  const generic =
    !rawName || rawName === "image.png" || rawName.toLowerCase() === "image.jpeg" || rawName === "clipboard.png";
  const name = generic ? `pasted-image-${Date.now()}.${ext}` : rawName;
  const file =
    generic || !imageFile.name ? new File([imageFile], name, { type: mime }) : imageFile;
  setComposerPendingFile(file);
}

const viewChatEl = document.getElementById("viewChat");
if (viewChatEl) {
  viewChatEl.addEventListener("paste", handleChatImagePaste, true);
}

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

async function uploadFile(
  file,
  { parentMessageId = null, threadKind = null, reloadMode = "timeline" } = {}
) {
  if (!activeChannelId || !currentUser || !file) return;
  const formData = new FormData();
  formData.append("file", file);

  const params = new URLSearchParams();
  params.set("employeeNo", String(currentUser.employeeNo));
  if (parentMessageId != null && Number.isFinite(Number(parentMessageId))) {
    params.set("parentMessageId", String(parentMessageId));
  }
  if (threadKind != null && String(threadKind).trim()) {
    params.set("threadKind", String(threadKind).trim());
  }

  try {
    const res = await fetch(
      `${API_BASE}/api/channels/${activeChannelId}/files/upload?${params.toString()}`,
      {
        method: "POST",
        headers: { Authorization: `Bearer ${getToken()}` },
        body: formData,
      }
    );
    const json = await res.json().catch(() => ({}));
    if (!res.ok) {
      appendSystemMsg(`파일 업로드 실패: ${json.error?.message || "오류"}`);
      return;
    }

    if (reloadMode === "none") return;
    if (reloadMode === "thread") {
      if (threadRootMessageId != null) {
        await openThreadModal(threadRootMessageId, { targetCommentMessageId: null });
      }
      if (activeChannelId) await loadMessages(activeChannelId);
      return;
    }

    // 기본: 메인 타임라인 + 파일 허브 갱신
    await loadMessages(activeChannelId);
    loadChannelFiles(activeChannelId);
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
  await loadMessages(activeChannelId);
  if (failed.length) {
    appendSystemMsg(`일부 구성원 추가 실패: ${failed.join(", ")}`);
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
      const pm = document.getElementById("sidebarPresenceMenu");
      if (pm && !pm.classList.contains("hidden")) {
        closeSidebarPresenceMenu();
        return;
      }
      const mod = document.getElementById("modalImagePreview");
      if (mod && !mod.classList.contains("hidden")) {
        closeModal("modalImagePreview");
      }
    });
  }

  if (!sidebarPresenceUiBound) {
    sidebarPresenceUiBound = true;
    document.getElementById("sidebarUserStatus")?.addEventListener("click", (e) => {
      e.stopPropagation();
      toggleSidebarPresenceMenu();
    });
    document.getElementById("sidebarPresenceMenu")?.addEventListener("click", (e) => {
      const opt = e.target.closest(".sidebar-presence-option");
      if (!opt) return;
      e.preventDefault();
      e.stopPropagation();
      const st = opt.getAttribute("data-presence-status") || opt.dataset.presenceStatus;
      if (st) emitMyPresenceStatus(st);
    });
    document.addEventListener("click", (e) => {
      const menuEl = document.getElementById("sidebarPresenceMenu");
      const btnEl = document.getElementById("sidebarUserStatus");
      if (!menuEl || menuEl.classList.contains("hidden")) return;
      if (menuEl.contains(e.target) || btnEl?.contains(e.target)) return;
      closeSidebarPresenceMenu();
    });
  }

  // 사이드바 섹션 접기/펼치기
  if (!sidebarSectionTogglesBound) {
    sidebarSectionTogglesBound = true;
    document.querySelectorAll(".section-toggle").forEach((btn) => {
      btn.addEventListener("click", () => {
        const targetId = btn.dataset.target;
        if (!targetId) return;
        const target = document.getElementById(targetId);
        if (target) {
          target.classList.toggle("hidden");
          syncSectionToggleChevron(btn, target);
        }
      });
    });
    syncAllSidebarSectionChevrons();
  }

  if (!sidebarCollapseBound) {
    sidebarCollapseBound = true;
    document.getElementById("btnSidebarEdgeToggle")?.addEventListener("click", (e) => {
      e.stopPropagation();
      toggleSidebarCollapsed();
    });
  }

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
      try {
        const errJson = await res.json();
        console.error("/api/auth/me 실패", res.status, errJson);
      } catch {
        console.error("/api/auth/me 실패", res.status, "(본문 파싱 불가)");
      }
      clearSession();
      showLogin();
    }
  } catch {
    clearSession();
    showLogin();
  }
})();
initTheme();

function scheduleSidebarAndPresenceSync() {
  if (!currentUser) return;
  if (windowFocusChannelsTimer) clearTimeout(windowFocusChannelsTimer);
  windowFocusChannelsTimer = setTimeout(() => {
    windowFocusChannelsTimer = null;
    loadMyChannels();
    fetchPresenceSnapshot().then(() => {
      if (socket?.connected && currentUser?.employeeNo) {
        const emp = String(currentUser.employeeNo).trim();
        const prev = presenceByEmployeeNo.get(emp) || "ONLINE";
        const toSend = prev === "AWAY" ? "AWAY" : "ONLINE";
        socket.emit("presence:set", { employeeNo: emp, status: toSend });
      }
      refreshPresenceDots();
      if (activeChannelId) loadChannelMembers(activeChannelId);
    });
  }, 500);
}

window.addEventListener("focus", scheduleSidebarAndPresenceSync);
document.addEventListener("visibilitychange", () => {
  if (document.visibilityState === "visible") scheduleSidebarAndPresenceSync();
});
