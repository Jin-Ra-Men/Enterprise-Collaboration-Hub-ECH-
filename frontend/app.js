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

/* ── 전역 상태 ── */
let socket         = null;
let currentUser    = null;
let activeChannelId = null;
let activeChannelType = null; // PUBLIC / PRIVATE / DM
let pendingFile    = null;
let selectedMembers = [];     // 채널/DM 생성 시 선택된 사용자
let selectedDmMembers = [];

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

function fmtTime(iso) {
  if (!iso) return "";
  const d = new Date(iso);
  return d.toLocaleTimeString("ko-KR", { hour: "2-digit", minute: "2-digit" });
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
  if (socket) { socket.disconnect(); socket = null; }
  activeChannelId = null;
  currentUser     = null;
  clearSession();

  // DOM 상태 완전 초기화 (다음 로그인 계정이 달라도 깨끗하게 시작)
  channelListEl.innerHTML = "";
  dmListEl.innerHTML      = "";
  messagesEl.innerHTML    = "";
  lastSenderId            = null;
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
    li.dataset.channelName = ch.name;

    if (ch.channelType === "DM") {
      li.innerHTML = `<span class="item-icon">●</span><span class="item-label">${escHtml(ch.name)}</span>`;
      dmListEl.appendChild(li);
    } else {
      const icon = ch.channelType === "PRIVATE" ? "🔒" : "#";
      li.innerHTML = `<span class="item-icon">${icon}</span><span class="item-label">${escHtml(ch.name)}</span>`;
      channelListEl.appendChild(li);
    }

    if (ch.channelId === activeChannelId) li.classList.add("active");

    li.addEventListener("click", () => selectChannel(ch.channelId, ch.name, ch.channelType));
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

  messageInputEl.focus();
}

async function loadMessages(channelId) {
  if (!currentUser) return;
  try {
    const res  = await apiFetch(`/api/channels/${channelId}/messages?userId=${currentUser.userId}&limit=50`);
    const json = await res.json();
    messagesEl.innerHTML = "";
    lastSenderId = null;
    if (!res.ok) { appendSystemMsg("메시지 로드 실패: " + (json.error?.message || "")); return; }
    const msgs = json.data || [];
    if (msgs.length === 0) {
      appendSystemMsg("아직 메시지가 없습니다. 첫 메시지를 보내보세요! 👋");
    } else {
      msgs.forEach(m => appendMessage(m));
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
      const li = document.createElement("li");
      li.className = "member-list-item";
      li.innerHTML = `
        <div class="member-avatar-sm">${avatarInitials(m.name || "?")}</div>
        <span>${escHtml(m.name || "알 수 없음")}</span>
        <span class="member-role-badge">${m.role || ""}</span>`;
      listEl.appendChild(li);
    });
  } catch (err) {
    console.error("멤버 로드 실패", err);
  }
}

/* ==========================================================================
 * 메시지 렌더링
 * ========================================================================== */
let lastSenderId = null;

function appendMessage(msg) {
  const senderIdNum = Number(msg.senderId);
  const isMine =
    currentUser && Number(currentUser.userId) === senderIdNum;
  const senderName = msg.senderName || `user#${senderIdNum}`;

  // 같은 발신자의 연속 메시지는 이름/아바타 생략 (API number / 소켓 string 혼용 대비)
  const isContinued =
    lastSenderId !== null && Number(lastSenderId) === senderIdNum;
  lastSenderId = senderIdNum;

  const div = document.createElement("div");
  div.className = `msg-row ${isMine ? "msg-mine" : "msg-other"} ${isContinued ? "msg-continued" : ""}`;

  if (isContinued) {
    div.innerHTML = `
      <div class="msg-spacer"></div>
      <div class="msg-body">
        <p class="msg-text">${escHtml(msg.text)}</p>
      </div>`;
  } else {
    const initials = avatarInitials(senderName);
    div.innerHTML = `
      <div class="msg-avatar">${initials}</div>
      <div class="msg-body">
        <div class="msg-meta">
          <span class="msg-sender">${escHtml(senderName)}</span>
          <span class="msg-time">${fmtTime(msg.createdAt)}</span>
        </div>
        <p class="msg-text">${escHtml(msg.text)}</p>
      </div>`;
  }

  messagesEl.appendChild(div);
  trimMessages();
  messagesEl.scrollTop = messagesEl.scrollHeight;
}

function appendSystemMsg(text) {
  lastSenderId = null;
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

  socket.on("connect", () => {
    if (currentUser) socket.emit("presence:set", { userId: currentUser.userId, status: "ONLINE" });
    if (activeChannelId) socket.emit("channel:join", activeChannelId);
  });

  socket.on("reconnect", () => {
    if (currentUser) socket.emit("presence:set", { userId: currentUser.userId, status: "ONLINE" });
    if (activeChannelId) socket.emit("channel:join", activeChannelId);
  });

  socket.on("disconnect", (reason) => {
    appendSystemMsg(`연결이 끊어졌습니다. (${reason})`);
  });

  socket.on("message:new", (msg) => {
    // pg bigint → string 가능성 대비 Number()로 비교
    if (Number(msg.channelId) === activeChannelId) {
      appendMessage(msg);
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
  appendSystemMsg(`파일 업로드 중: ${file.name}`);
  try {
    const res  = await fetch(`${API_BASE}/api/channels/${activeChannelId}/files/upload?userId=${currentUser.userId}`, {
      method: "POST",
      headers: { Authorization: `Bearer ${getToken()}` },
      body: formData,
    });
    const json = await res.json();
    if (res.ok) {
      appendSystemMsg(`파일 업로드 완료: ${json.data?.originalFilename || file.name}`);
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
document.getElementById("btnCreateChannel").addEventListener("click", () => {
  selectedMembers = [];
  document.getElementById("newChannelName").value = "";
  document.getElementById("newChannelDesc").value = "";
  document.getElementById("newChannelType").value = "PUBLIC";
  document.getElementById("userSearchResults").innerHTML = "";
  document.getElementById("selectedMembersWrap").innerHTML = "";
  document.getElementById("memberSearchInput").value = "";
  openModal("modalCreateChannel");
});

document.getElementById("btnSearchMember").addEventListener("click", () => searchUsers("member"));
document.getElementById("memberSearchInput").addEventListener("keydown", (e) => {
  if (e.key === "Enter") { e.preventDefault(); searchUsers("member"); }
});

async function searchUsers(context) {
  const inputEl   = context === "dm" ? document.getElementById("dmSearchInput") : document.getElementById("memberSearchInput");
  const resultEl  = context === "dm" ? document.getElementById("dmSearchResults") : document.getElementById("userSearchResults");
  const keyword   = inputEl.value.trim();
  if (!keyword) return;

  try {
    const res  = await apiFetch(`/api/users/search?q=${encodeURIComponent(keyword)}`);
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
        <span class="user-search-dept">${escHtml(u.department || "")}</span>
      </div>
      <button class="btn-add-member">추가</button>`;
    li.querySelector(".btn-add-member").addEventListener("click", () => {
      addSelectedMember(u, context);
      li.remove();
    });
    listEl.appendChild(li);
  });
}

function addSelectedMember(user, context) {
  const listRef = context === "dm" ? selectedDmMembers : selectedMembers;
  const wrapId  = context === "dm" ? "selectedDmMembersWrap" : "selectedMembersWrap";

  if (listRef.find(m => m.userId === user.userId)) return;
  listRef.push(user);

  const wrap = document.getElementById(wrapId);
  const tag  = document.createElement("span");
  tag.className   = "member-tag";
  tag.dataset.uid = user.userId;
  tag.innerHTML   = `${escHtml(user.name)} <button data-uid="${user.userId}">✕</button>`;
  tag.querySelector("button").addEventListener("click", () => {
    const idx = listRef.findIndex(m => m.userId === user.userId);
    if (idx >= 0) listRef.splice(idx, 1);
    tag.remove();
  });
  wrap.appendChild(tag);
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
document.getElementById("btnCreateDm").addEventListener("click", () => {
  selectedDmMembers = [];
  document.getElementById("dmSearchInput").value = "";
  document.getElementById("dmSearchResults").innerHTML = "";
  document.getElementById("selectedDmMembersWrap").innerHTML = "";
  openModal("modalCreateDm");
});

document.getElementById("btnDmSearch").addEventListener("click", () => searchUsers("dm"));
document.getElementById("dmSearchInput").addEventListener("keydown", (e) => {
  if (e.key === "Enter") { e.preventDefault(); searchUsers("dm"); }
});

document.getElementById("btnConfirmCreateDm").addEventListener("click", async () => {
  if (selectedDmMembers.length === 0) { alert("대화 상대를 선택하세요."); return; }
  if (!currentUser) return;

  const allMembers = [currentUser, ...selectedDmMembers];
  const dmName     = selectedDmMembers.map(m => m.name).join(", ");

  try {
    const res = await apiFetch("/api/channels", {
      method: "POST",
      body: JSON.stringify({
        workspaceKey: WS_KEY,
        name: dmName,
        description: "",
        channelType: "DM",
        createdByUserId: currentUser.userId,
      }),
    });
    const json = await res.json();
    if (!res.ok) { alert("DM 생성 실패: " + (json.error?.message || "")); return; }

    const channelId = json.data?.channelId;
    for (const m of selectedDmMembers) {
      await apiFetch(`/api/channels/${channelId}/members`, {
        method: "POST",
        body: JSON.stringify({ userId: m.userId, memberRole: "MEMBER" }),
      });
    }

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
