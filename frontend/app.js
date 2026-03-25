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

function syncThemeChips() {
  const cur = getCurrentTheme();
  document.querySelectorAll(".theme-chip").forEach((btn) => {
    btn.classList.toggle("active", btn.dataset.theme === cur);
  });
}

function applyTheme(theme) {
  const t = VALID_THEMES.includes(theme) ? theme : "dark";
  if (t === "dark") {
    document.documentElement.removeAttribute("data-theme");
  } else {
    document.documentElement.setAttribute("data-theme", t);
  }
  try {
    localStorage.setItem(THEME_KEY, t);
  } catch (e) {
    /* ignore */
  }
  syncThemeChips();
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

function onThemeChipClick(e) {
  const chip = e.target.closest(".theme-chip");
  if (!chip || !chip.dataset.theme) return;
  e.preventDefault();
  applyTheme(chip.dataset.theme);
}

/** userId(number) -> ONLINE | AWAY | OFFLINE */
const presenceByUserId = new Map();

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
/** 프로필 모달에 표시 중인 사용자 ID (DM 보내기용) */
let profileViewUserId = null;

/* ── DOM 참조 ── */
const loginPage      = document.getElementById("loginPage");
const mainApp        = document.getElementById("mainApp");
const loginForm      = document.getElementById("loginForm");
const loginErrorEl   = document.getElementById("loginError");
const loginBtn       = document.getElementById("loginBtn");
const logoutBtn      = document.getElementById("logoutBtn");
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
      presenceByUserId.set(Number(p.userId), String(p.status || "OFFLINE").toUpperCase());
    });
  } catch (e) {
    console.warn("프레즌스 스냅샷 실패", e);
  }
}

function refreshPresenceDots() {
  document.querySelectorAll("[data-presence-user]").forEach((el) => {
    const uid = Number(el.dataset.presenceUser);
    const st = presenceByUserId.get(uid) || "OFFLINE";
    el.className = `presence-dot ${presenceCssClass(st)}`;
    el.title = presenceTitle(st);
  });
}

async function openUserProfile(userId) {
  if (!userId || !currentUser) return;
  try {
    const res  = await apiFetch(`/api/users/profile?userId=${encodeURIComponent(userId)}`);
    const json = await res.json();
    if (!res.ok) {
      alert(json.error?.message || "프로필을 불러올 수 없습니다.");
      return;
    }
    const u = json.data;
    profileViewUserId = u.userId != null ? u.userId : userId;
    document.getElementById("profileModalName").textContent = u.name || "-";
    document.getElementById("profileAvatarLg").textContent = avatarInitials(u.name || "?");
    document.getElementById("profileModalEmpNo").textContent = u.employeeNo || "-";
    document.getElementById("profileModalEmail").textContent = u.email || "-";
    document.getElementById("profileModalDept").textContent = u.department || "-";
    const jr = u.jobRank != null && String(u.jobRank).trim() !== "";
    document.getElementById("profileModalJobRank").textContent = jr ? String(u.jobRank).trim() : "-";
    const dutyDt = document.getElementById("profileDutyTitleDt");
    const dutyDd = document.getElementById("profileModalDutyTitle");
    const hasDuty = u.dutyTitle != null && String(u.dutyTitle).trim() !== "";
    if (dutyDt && dutyDd) {
      if (hasDuty) {
        dutyDt.classList.remove("hidden");
        dutyDd.classList.remove("hidden");
        dutyDd.textContent = String(u.dutyTitle).trim();
      } else {
        dutyDt.classList.add("hidden");
        dutyDd.classList.add("hidden");
        dutyDd.textContent = "";
      }
    }
    const dmBtn = document.getElementById("btnProfileDm");
    if (dmBtn) {
      const self = Number(profileViewUserId) === Number(currentUser.userId);
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
async function startDmWithUser(userId, displayName) {
  if (!currentUser || userId == null || userId === "") return;
  const uid = Number(userId);
  if (uid === Number(currentUser.userId)) {
    alert("자기 자신과는 DM을 할 수 없습니다.");
    return;
  }
  const dmName =
    displayName && displayName !== "-" ? displayName : `user#${userId}`;
  try {
    const res = await apiFetch("/api/channels", {
      method: "POST",
      body: JSON.stringify({
        workspaceKey: WS_KEY,
        name: dmName,
        description: dmName,
        channelType: "DM",
        createdByUserId: currentUser.userId,
        dmPeerUserIds: [uid],
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
    const res  = await apiFetch(`/api/channels/${channelId}/files?userId=${currentUser.userId}`);
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
      const who = f.uploaderName ? escHtml(f.uploaderName) : `user#${f.uploadedByUserId}`;
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

async function downloadChannelFile(fileId, filename) {
  if (!activeChannelId || !currentUser) return;
  try {
    const res = await fetch(
      `${API_BASE}/api/channels/${activeChannelId}/files/${fileId}/download?userId=${currentUser.userId}`,
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

async function loadOrgTree(context, embedElIdOverride = null) {
  const elId = embedElIdOverride ?? ORG_EMBED_IDS[context];
  const el = elId ? document.getElementById(elId) : null;
  if (!el) return;
  orgPickerContext = context;
  orgPickerEmbedElId = elId;
  el.innerHTML = '<p class="empty-notice">불러오는 중...</p>';
  try {
    const res  = await apiFetch("/api/user-directory/organization");
    const json = await res.json();
    if (!res.ok) {
      el.innerHTML = `<p class="empty-notice">${escHtml(json.error?.message || "오류")}</p>`;
      return;
    }
    const root = json.data || {};
    const companies = root.companies || [];
    el.innerHTML = "";
    if (companies.length === 0) {
      el.innerHTML = '<p class="empty-notice">표시할 사용자가 없습니다.</p>';
      return;
    }
    companies.forEach((co) => {
      const detCo = document.createElement("details");
      detCo.className = "org-lvl org-lvl-company";
      detCo.open = true;
      detCo.innerHTML = `<summary><span class="org-lvl-label">${escHtml(co.name)}</span></summary>`;
      const wrapCo = document.createElement("div");
      wrapCo.className = "org-lvl-body";
      (co.divisions || []).forEach((div) => {
        const detDiv = document.createElement("details");
        detDiv.className = "org-lvl org-lvl-division";
        detDiv.open = true;
        detDiv.innerHTML = `<summary><span class="org-lvl-label">${escHtml(div.name)}</span></summary>`;
        const wrapDiv = document.createElement("div");
        wrapDiv.className = "org-lvl-body";
        (div.teams || []).forEach((team) => {
          const detTeam = document.createElement("details");
          detTeam.className = "org-lvl org-lvl-team";
          detTeam.open = true;
          const uCount = (team.users || []).length;
          detTeam.innerHTML = `<summary><span class="org-lvl-label">${escHtml(team.name)}</span> <span class="org-dept-count">(${uCount})</span></summary>`;
          const ul = document.createElement("ul");
          ul.className = "org-user-list";
          (team.users || []).forEach((u) => {
            if (u.userId === currentUser?.userId) return;
            const checked = isUserAlreadySelected(u.userId, context) ? "checked" : "";
            const li = document.createElement("li");
            const payload = JSON.stringify({
              userId: u.userId,
              name: u.name,
              email: u.email || "",
              department: u.department || "",
              employeeNo: u.employeeNo || "",
            });
            li.dataset.userPayload = payload;
            li.innerHTML = `
              <label class="org-user-check">
                <input type="checkbox" class="org-user-checkbox" data-user-id="${u.userId}" ${checked}/>
                <span>
                  <span class="org-user-name">${escHtml(u.name)}</span>
                  <span class="org-user-email">${escHtml([u.department, u.email].filter(Boolean).join(" · "))}</span>
                </span>
              </label>`;
            const cb = li.querySelector(".org-user-checkbox");
            cb.addEventListener("change", () => {
              let userObj;
              try {
                userObj = JSON.parse(li.dataset.userPayload || "{}");
              } catch {
                return;
              }
              if (cb.checked) {
                addSelectedMember(userObj, context);
              } else {
                removeSelectedMember(userObj.userId, context);
              }
            });
            ul.appendChild(li);
          });
          if (ul.children.length === 0) return;
          detTeam.appendChild(ul);
          wrapDiv.appendChild(detTeam);
        });
        detDiv.appendChild(wrapDiv);
        wrapCo.appendChild(detDiv);
      });
      detCo.appendChild(wrapCo);
      el.appendChild(detCo);
    });
  } catch (e) {
    console.error(e);
    el.innerHTML = '<p class="empty-notice">조직도를 불러오지 못했습니다.</p>';
  }
}

function isUserAlreadySelected(userId, context) {
  const ref = context === "dm"
    ? selectedDmMembers
    : (context === "channelMember" ? selectedAddMembers : selectedMembers);
  return ref.some((u) => Number(u.userId) === Number(userId));
}

function syncOrgCheckbox(userId, context, checked) {
  const id = orgPickerEmbedElId ?? ORG_EMBED_IDS[context];
  if (!id) return;
  const root = document.getElementById(id);
  if (!root) return;
  const cb = root.querySelector(`.org-user-checkbox[data-user-id="${userId}"]`);
  if (cb) cb.checked = checked;
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
    const res  = await apiFetch(`/api/channels?userId=${currentUser.userId}`);
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
  try {
    const res  = await apiFetch(`/api/channels/${channelId}/messages?userId=${currentUser.userId}&limit=50`);
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
      const uid = Number(m.userId);
      const pr = presenceByUserId.get(uid) || "OFFLINE";
      const prCl = presenceCssClass(pr);
      const deptParts = [m.department, m.jobRank].filter(x => x != null && String(x).trim() !== "");
      const orgLine = deptParts.length ? deptParts.map(x => String(x).trim()).join(" · ") : "조직 미지정";
      const dutyHtml =
        m.dutyTitle != null && String(m.dutyTitle).trim() !== ""
          ? `<span class="member-duty-txt">${escHtml(String(m.dutyTitle).trim())}</span>`
          : "";
      const li = document.createElement("li");
      li.className = "member-list-item";
      li.innerHTML = `
        <div class="member-avatar-wrap">
          <span class="member-avatar-sm">${avatarInitials(m.name || "?")}</span>
          <span class="presence-dot ${prCl}" data-presence-user="${uid}" title="${presenceTitle(pr)}"></span>
        </div>
        <button type="button" class="member-profile-btn" data-user-id="${uid}">
          <span class="member-name-wrap">
            <span class="member-name-txt">${escHtml(m.name || "알 수 없음")}</span>
            <span class="member-org-txt">${escHtml(orgLine)}</span>
            ${dutyHtml}
          </span>
        </button>`;
      li.querySelector(".member-profile-btn").addEventListener("click", () => openUserProfile(uid));
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
  if (Number(next.senderId) !== Number(cur.senderId)) return true;
  if (minuteKey(next.createdAt) !== minuteKey(cur.createdAt)) return true;
  return false;
}

/** 아바타+이름 행: 이전 메시지와 발신자가 다를 때만 */
function shouldShowAvatarForMessage(msgs, i) {
  if (i === 0) return true;
  return Number(msgs[i - 1].senderId) !== Number(msgs[i].senderId);
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

function createFileAttachmentRowFromMsg(msg, payload, { showAvatar, showTime }) {
  const senderIdNum = Number(msg.senderId);
  const isMine = currentUser && Number(currentUser.userId) === senderIdNum;
  const senderName = msg.senderName || `user#${senderIdNum}`;
  const pr = presenceByUserId.get(senderIdNum) || "OFFLINE";
  const prCl = presenceCssClass(pr);
  const prTip = presenceTitle(pr);
  const fileMeta = {
    id: Number(payload.fileId),
    originalFilename: payload.originalFilename,
    sizeBytes: payload.sizeBytes,
    uploadedByUserId: senderIdNum,
    uploaderName: senderName,
  };
  const timeHtml = showTime
    ? `<span class="msg-time">${escHtml(fmtTime(msg.createdAt))}</span>`
    : "";
  const div = document.createElement("div");
  div.className = `msg-row msg-chat ${isMine ? "msg-mine" : "msg-other"} msg-has-attachment${showAvatar ? "" : " msg-continued"}`;
  div.dataset.senderId = String(senderIdNum);
  div.dataset.minuteKey = minuteKey(msg.createdAt);
  div.dataset.dateKey = dateKeyLocal(msg.createdAt);

  if (showAvatar) {
    const initials = avatarInitials(senderName);
    div.innerHTML = `
      <div class="msg-avatar-wrap">
        <button type="button" class="msg-avatar msg-user-trigger" data-user-id="${senderIdNum}" title="프로필 보기">${initials}</button>
        <span class="presence-dot ${prCl}" data-presence-user="${senderIdNum}" title="${prTip}"></span>
      </div>
      <div class="msg-body">
        <div class="msg-meta">
          <button type="button" class="msg-sender msg-user-trigger" data-user-id="${senderIdNum}">${escHtml(senderName)}</button>
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
    downloadChannelFile(fileMeta.id, fileMeta.originalFilename);
  });
  return div;
}

function createMessageRowElement(msg, { showAvatar, showTime }) {
  const filePayload = tryParseFilePayload(msg);
  if (filePayload) {
    return createFileAttachmentRowFromMsg(msg, filePayload, { showAvatar, showTime });
  }

  const senderIdNum = Number(msg.senderId);
  const isMine =
    currentUser && Number(currentUser.userId) === senderIdNum;
  const senderName = msg.senderName || `user#${senderIdNum}`;
  const pr = presenceByUserId.get(senderIdNum) || "OFFLINE";
  const prCl = presenceCssClass(pr);
  const prTip = presenceTitle(pr);
  const div = document.createElement("div");
  div.className = `msg-row msg-chat ${isMine ? "msg-mine" : "msg-other"}${showAvatar ? "" : " msg-continued"}`;
  div.dataset.senderId = String(senderIdNum);
  div.dataset.minuteKey = minuteKey(msg.createdAt);
  div.dataset.dateKey = dateKeyLocal(msg.createdAt);

  const timeHtml = showTime
    ? `<span class="msg-time">${escHtml(fmtTime(msg.createdAt))}</span>`
    : "";

  if (showAvatar) {
    const initials = avatarInitials(senderName);
    div.innerHTML = `
      <div class="msg-avatar-wrap">
        <button type="button" class="msg-avatar msg-user-trigger" data-user-id="${senderIdNum}" title="프로필 보기">${initials}</button>
        <span class="presence-dot ${prCl}" data-presence-user="${senderIdNum}" title="${prTip}"></span>
      </div>
      <div class="msg-body">
        <div class="msg-meta">
          <button type="button" class="msg-sender msg-user-trigger" data-user-id="${senderIdNum}">${escHtml(senderName)}</button>
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
  const sid = Number(msg.senderId);
  const mk = minuteKey(msg.createdAt);
  const dk = dateKeyLocal(msg.createdAt);
  let rows = messagesEl.querySelectorAll(".msg-row.msg-chat");
  let last = rows[rows.length - 1];
  const lastDk = last ? last.dataset.dateKey : null;
  if (!last || lastDk !== dk) {
    messagesEl.appendChild(createDateDividerElement(msg.createdAt));
  }
  rows = messagesEl.querySelectorAll(".msg-row.msg-chat");
  last = rows[rows.length - 1];
  if (last && Number(last.dataset.senderId) === sid && last.dataset.minuteKey === mk) {
    const timeEl = last.querySelector(".msg-time");
    if (timeEl) timeEl.remove();
  }
  rows = messagesEl.querySelectorAll(".msg-row.msg-chat");
  last = rows[rows.length - 1];
  const showAvatar = !last || Number(last.dataset.senderId) !== sid;
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
    if (currentUser) socket.emit("presence:set", { userId: currentUser.userId, status: "ONLINE" });
    if (activeChannelId) socket.emit("channel:join", activeChannelId);
    refreshPresenceDots();
    if (activeChannelId) loadChannelMembers(activeChannelId);
  });

  socket.on("reconnect", async () => {
    await fetchPresenceSnapshot();
    if (currentUser) socket.emit("presence:set", { userId: currentUser.userId, status: "ONLINE" });
    if (activeChannelId) socket.emit("channel:join", activeChannelId);
    refreshPresenceDots();
    if (activeChannelId) loadChannelMembers(activeChannelId);
  });

  socket.on("presence:update", (p) => {
    if (!p || p.userId == null) return;
    presenceByUserId.set(Number(p.userId), String(p.status || "OFFLINE").toUpperCase());
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

  socket.emit("message:send",
    { channelId: activeChannelId, senderId: currentUser.userId, text },
    (ack) => {
      if (ack && !ack.ok) {
        appendSystemMsg("전송 실패: " + (ack.message || "오류"));
      }
    }
  );
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
  pendingFile = file;
  document.getElementById("filePreview").classList.remove("hidden");
  document.getElementById("filePreviewName").textContent = `📎 ${file.name} (${fmtSize(file.size)})`;
  e.target.value = "";
});

document.getElementById("btnClearFile").addEventListener("click", clearFilePreview);

function clearFilePreview() {
  pendingFile = null;
  document.getElementById("filePreview").classList.add("hidden");
  document.getElementById("filePreviewName").textContent = "";
}

async function uploadFile(file) {
  if (!activeChannelId || !currentUser) return;
  const formData = new FormData();
  formData.append("file", file);
  try {
    const res  = await fetch(`${API_BASE}/api/channels/${activeChannelId}/files/upload?userId=${currentUser.userId}`, {
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
    if (u.userId === currentUser?.userId) return; // 본인 제외
    const li = document.createElement("li");
    li.className = "user-search-item";
    li.innerHTML = `
      <div class="user-search-avatar">${avatarInitials(u.name)}</div>
      <div class="user-search-info">
        <span class="user-search-name">${escHtml(u.name)}</span>
        <span class="user-search-dept">${escHtml([u.department, u.email, u.employeeNo, "ID:" + u.userId].filter(Boolean).join(" · "))}</span>
      </div>
      <button class="btn-add-member" data-uid="${u.userId}">추가</button>`;
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

  if (listRef.find(m => m.userId === user.userId)) return;
  listRef.push(user);

  const wrap = document.getElementById(wrapId);
  const tag  = document.createElement("span");
  tag.className   = "member-tag";
  tag.dataset.uid = user.userId;
  tag.innerHTML   = `${escHtml(user.name)} <button type="button" data-uid="${user.userId}">✕</button>`;
  tag.querySelector("button").addEventListener("click", () => {
    removeSelectedMember(user.userId, context);
  });
  wrap.appendChild(tag);
  syncOrgCheckbox(user.userId, context, true);
}

function removeSelectedMember(userId, context) {
  const listRef = context === "dm"
    ? selectedDmMembers
    : (context === "channelMember" ? selectedAddMembers : selectedMembers);
  const wrapId = context === "dm"
    ? "selectedDmMembersWrap"
    : (context === "channelMember" ? "selectedAddMembersWrap" : "selectedMembersWrap");
  const idx = listRef.findIndex(m => m.userId === userId);
  if (idx >= 0) listRef.splice(idx, 1);
  const wrap = document.getElementById(wrapId);
  const tag = wrap?.querySelector(`.member-tag[data-uid="${userId}"]`);
  if (tag) tag.remove();
  syncOrgCheckbox(userId, context, false);
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
        createdByUserId: currentUser.userId,
      }),
    });
    const json = await res.json();
    if (!res.ok) { alert("채널 생성 실패: " + (json.error?.message || "")); return; }

    const channelId = json.data?.channelId;
    // 선택한 멤버 추가
    for (const m of selectedMembers) {
      await apiFetch(`/api/channels/${channelId}/members`, {
        method: "POST",
        body: JSON.stringify({ userId: m.userId, memberRole: "MEMBER" }),
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
        createdByUserId: currentUser.userId,
        dmPeerUserIds: selectedDmMembers.map((m) => m.userId),
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
  document.getElementById("addMemberSearchInput").value = "";
  document.getElementById("addMemberSearchResults").innerHTML = "";
  document.getElementById("selectedAddMembersWrap").innerHTML = "";
  openModal("modalAddChannelMembers");
});
document.getElementById("btnOpenAddMemberPicker").addEventListener("click", async () => {
  openModal("modalAddMemberPicker");
  document.getElementById("addMemberSearchInput").value = "";
  document.getElementById("addMemberSearchResults").innerHTML = "";
  await loadOrgTree("channelMember");
});
document.getElementById("btnCloseAddMemberPicker").addEventListener("click", () => {
  closeModal("modalAddMemberPicker");
});
async function searchUsersInMemberPicker() {
  const inputEl = document.getElementById("addMemberSearchInput");
  const resultEl = document.getElementById("addMemberSearchResults");
  if (!inputEl || !resultEl) return;
  const context = orgPickerContext || "channelMember";
  const keyword = inputEl.value.trim();
  if (!keyword) return;

  try {
    const res  = await apiFetch(`/api/users/search?q=${encodeURIComponent(keyword)}`);
    const json = await res.json();
    if (!res.ok) {
      resultEl.innerHTML = `<li class="search-empty">${escHtml(json.error?.message || "오류")}</li>`;
      return;
    }
    renderUserSearchResults(json.data || [], resultEl, context);
  } catch {
    resultEl.innerHTML = '<li class="search-empty">검색 오류</li>';
  }
}

document.getElementById("btnSearchAddMember").addEventListener("click", () => searchUsersInMemberPicker());
document.getElementById("addMemberSearchInput").addEventListener("keydown", (e) => {
  if (e.key === "Enter") {
    e.preventDefault();
    searchUsersInMemberPicker();
  }
});

document.getElementById("btnOpenAddMemberPickerForCreateChannel")?.addEventListener("click", async () => {
  openModal("modalAddMemberPicker");
  // 멤버 추가는 팝업에서만 수행 (컨텍스트=member)
  document.getElementById("addMemberSearchInput").value = "";
  document.getElementById("addMemberSearchResults").innerHTML = "";
  await loadOrgTree("member", "orgTreeEmbedAdd");
});

document.getElementById("btnOpenAddMemberPickerForCreateDm")?.addEventListener("click", async () => {
  openModal("modalAddMemberPicker");
  // DM 생성은 팝업에서만 수행 (컨텍스트=dm)
  document.getElementById("addMemberSearchInput").value = "";
  document.getElementById("addMemberSearchResults").innerHTML = "";
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
      body: JSON.stringify({ userId: u.userId, memberRole: "MEMBER" }),
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
  await startDmWithUser(profileViewUserId, name);
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
          body: JSON.stringify({ actorUserId: currentUser?.userId, note: "수동 활성화" }),
        });
        alert(r.ok ? "활성화 완료" : "활성화 실패");
        loadReleases();
      });
    });
    tbody.querySelectorAll(".btn-delete").forEach(btn => {
      btn.addEventListener("click", async () => {
        if (!confirm("이 릴리즈 파일을 삭제하시겠습니까?")) return;
        const r = await apiFetch(`/api/admin/releases/${btn.dataset.id}?actorUserId=${currentUser?.userId || ""}`, {
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
  if (currentUser?.userId) fd.append("uploadedBy", currentUser.userId);
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
          body: JSON.stringify({ value, updatedBy: currentUser?.userId }),
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
    if (!t || t.dataset.userId == null) return;
    e.preventDefault();
    openUserProfile(Number(t.dataset.userId));
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
      showMain(user);
    } else {
      clearSession();
      showLogin();
    }
  } catch {
    clearSession();
    showLogin();
  }
})();

document.addEventListener("click", onThemeChipClick);
initTheme();
