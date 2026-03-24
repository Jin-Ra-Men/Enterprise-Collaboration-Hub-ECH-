/* =====================================================================
 * ECH Frontend — app.js
 * - 로그인/로그아웃 처리
 * - JWT 토큰 관리 (sessionStorage)
 * - 모든 API 호출에 Authorization 헤더 자동 첨부
 * - Socket.io 실시간 메시지
 * - 관리자: 배포 버전 관리 (업로드/활성화/롤백/이력)
 * ===================================================================== */

const API_BASE = "http://localhost:8080";
const SOCKET_URL = "http://localhost:3001";
const TOKEN_KEY = "ech_token";
const USER_KEY = "ech_user";
const MAX_VISIBLE_MESSAGES = 200;

/* ── 상태 ── */
let socket = null;
let joinedChannel = null;

/* ── DOM 참조 ── */
const loginPage = document.getElementById("loginPage");
const mainApp = document.getElementById("mainApp");
const loginForm = document.getElementById("loginForm");
const loginIdEl = document.getElementById("loginId");
const loginPasswordEl = document.getElementById("loginPassword");
const loginErrorEl = document.getElementById("loginError");
const loginBtn = document.getElementById("loginBtn");
const logoutBtn = document.getElementById("logoutBtn");
const userInfoBadge = document.getElementById("userInfoBadge");
const messagesEl = document.getElementById("messages");
const messageForm = document.getElementById("messageForm");
const channelIdEl = document.getElementById("channelId");
const messageInputEl = document.getElementById("messageInput");

/* ─────────────────────────────────────────
 * 토큰/세션 유틸
 * ───────────────────────────────────────── */
function saveSession(token, user) {
  sessionStorage.setItem(TOKEN_KEY, token);
  sessionStorage.setItem(USER_KEY, JSON.stringify(user));
}

function clearSession() {
  sessionStorage.removeItem(TOKEN_KEY);
  sessionStorage.removeItem(USER_KEY);
}

function getToken() {
  return sessionStorage.getItem(TOKEN_KEY);
}

function getUser() {
  const raw = sessionStorage.getItem(USER_KEY);
  return raw ? JSON.parse(raw) : null;
}

/* API 호출 헬퍼 — Authorization 헤더 자동 첨부 */
async function apiFetch(path, options = {}) {
  const token = getToken();
  const headers = { "Content-Type": "application/json", ...(options.headers || {}) };
  if (token) {
    headers["Authorization"] = `Bearer ${token}`;
  }
  const res = await fetch(`${API_BASE}${path}`, { ...options, headers });
  return res;
}

/* ─────────────────────────────────────────
 * 화면 전환
 * ───────────────────────────────────────── */
function showLogin() {
  loginPage.classList.remove("hidden");
  mainApp.classList.add("hidden");
  loginIdEl.value = "";
  loginPasswordEl.value = "";
  hideLoginError();
}

function showMain(user) {
  loginPage.classList.add("hidden");
  mainApp.classList.remove("hidden");
  userInfoBadge.textContent = `${user.name} (${user.department || user.role})`;

  // 관리자 전용 메뉴 노출
  if (user.role === "ADMIN") {
    document.querySelectorAll(".admin-only").forEach((el) => el.classList.remove("hidden"));
  }

  initSocket(user);
  initTabs();
}

function showLoginError(msg) {
  loginErrorEl.textContent = msg;
  loginErrorEl.classList.remove("hidden");
}

function hideLoginError() {
  loginErrorEl.textContent = "";
  loginErrorEl.classList.add("hidden");
}

/* ─────────────────────────────────────────
 * 로그인
 * ───────────────────────────────────────── */
loginForm.addEventListener("submit", async (e) => {
  e.preventDefault();
  hideLoginError();

  const loginId = loginIdEl.value.trim();
  const password = loginPasswordEl.value;
  if (!loginId || !password) {
    showLoginError("사원번호/이메일과 비밀번호를 모두 입력해 주세요.");
    return;
  }

  loginBtn.disabled = true;
  loginBtn.textContent = "로그인 중...";

  try {
    const res = await fetch(`${API_BASE}/api/auth/login`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ loginId, password }),
    });
    const json = await res.json();

    if (!res.ok || json.status === "error") {
      showLoginError(json.error?.message || "로그인에 실패했습니다.");
      return;
    }

    const { token, ...user } = json.data;
    saveSession(token, user);
    showMain(user);
  } catch (err) {
    showLoginError("서버에 연결할 수 없습니다. 백엔드 서버 실행 여부를 확인해 주세요.");
  } finally {
    loginBtn.disabled = false;
    loginBtn.textContent = "로그인";
  }
});

/* ─────────────────────────────────────────
 * 로그아웃
 * ───────────────────────────────────────── */
logoutBtn.addEventListener("click", () => {
  if (socket) {
    socket.disconnect();
    socket = null;
    joinedChannel = null;
  }
  clearSession();
  messagesEl.innerHTML = "";
  showLogin();
});

/* ─────────────────────────────────────────
 * 메시지 UI
 * ───────────────────────────────────────── */
function trimMessageList() {
  while (messagesEl.children.length > MAX_VISIBLE_MESSAGES) {
    messagesEl.removeChild(messagesEl.firstChild);
  }
}

function appendMessage(message, type = "normal") {
  const item = document.createElement("p");
  item.classList.add("msg-item");
  if (type === "error") {
    item.classList.add("msg-error");
    item.textContent = `⚠ ${message}`;
  } else if (type === "system") {
    item.classList.add("msg-system");
    item.textContent = message;
  } else {
    const user = getUser();
    const isMine = user && message.senderId === user.userId;
    item.classList.add(isMine ? "msg-mine" : "msg-other");
    item.textContent = `user#${message.senderId}: ${message.text}`;
  }
  messagesEl.appendChild(item);
  messagesEl.scrollTop = messagesEl.scrollHeight;
  trimMessageList();
}

/* ─────────────────────────────────────────
 * Socket.io
 * ───────────────────────────────────────── */
function initSocket(user) {
  if (socket) {
    socket.disconnect();
  }
  socket = io(SOCKET_URL);

  socket.on("connect", () => {
    appendMessage("실시간 서버에 연결되었습니다.", "system");
    const channelId = Number(channelIdEl.value.trim() || "1");
    if (Number.isInteger(channelId) && channelId > 0) {
      joinChannel(channelId);
    }
  });

  socket.on("disconnect", () => {
    appendMessage("실시간 서버와 연결이 끊어졌습니다.", "system");
  });

  socket.on("message:new", (msg) => appendMessage(msg));

  socket.on("message:error", (err) => {
    appendMessage(err.message || "메시지 전송 오류", "error");
  });
}

function joinChannel(channelId) {
  if (!socket) return;
  if (joinedChannel === channelId) return;
  socket.emit("channel:join", channelId);
  joinedChannel = channelId;
  appendMessage(`채널 #${channelId}에 입장했습니다.`, "system");
}

/* ─────────────────────────────────────────
 * 메시지 전송
 * ───────────────────────────────────────── */
messageForm.addEventListener("submit", (e) => {
  e.preventDefault();
  if (!socket) return;

  const channelId = Number(channelIdEl.value.trim());
  const text = messageInputEl.value.trim();
  const user = getUser();

  if (!Number.isInteger(channelId) || channelId <= 0 || !text || !user) return;

  joinChannel(channelId);
  socket.emit("message:send", { channelId, senderId: user.userId, text });
  messageInputEl.value = "";
});

/* ─────────────────────────────────────────
 * 통합 검색
 * ───────────────────────────────────────── */
const TYPE_ICON = { MESSAGE: "💬", FILE: "📎", WORK_ITEM: "✅", KANBAN_CARD: "📋" };
const TYPE_LABEL = { MESSAGE: "메시지", FILE: "파일", WORK_ITEM: "업무", KANBAN_CARD: "칸반" };

const searchModal = document.getElementById("searchModal");
const searchResults = document.getElementById("searchResults");
const searchModalTitle = document.getElementById("searchModalTitle");
const searchTypeSelect = document.getElementById("searchTypeSelect");

document.getElementById("searchModalClose").addEventListener("click", closeSearchModal);
document.getElementById("searchBackdrop").addEventListener("click", closeSearchModal);
document.addEventListener("keydown", (e) => { if (e.key === "Escape") closeSearchModal(); });

function openSearchModal() { searchModal.classList.remove("hidden"); }
function closeSearchModal() { searchModal.classList.add("hidden"); }

searchTypeSelect.addEventListener("change", () => {
  const q = document.getElementById("searchInput").value.trim();
  if (q.length >= 2) runSearch(q, searchTypeSelect.value);
});

document.getElementById("searchForm").addEventListener("submit", async (e) => {
  e.preventDefault();
  const q = document.getElementById("searchInput").value.trim();
  if (q.length < 2) return;
  searchTypeSelect.value = "ALL";
  await runSearch(q, "ALL");
  openSearchModal();
});

async function runSearch(q, type) {
  searchResults.innerHTML = '<p class="search-loading">검색 중...</p>';
  openSearchModal();
  searchModalTitle.textContent = `"${q}" 검색 결과`;

  try {
    const res = await apiFetch(`/api/search?q=${encodeURIComponent(q)}&type=${type}&limit=30`);
    const json = await res.json();

    if (!res.ok) {
      searchResults.innerHTML = `<p class="search-empty">${json.error?.message || "검색 오류"}</p>`;
      return;
    }

    const items = json.data?.items || [];
    if (items.length === 0) {
      searchResults.innerHTML = '<p class="search-empty">검색 결과가 없습니다.</p>';
      return;
    }

    searchResults.innerHTML = "";
    items.forEach((item) => {
      const div = document.createElement("div");
      div.className = "search-item";
      div.innerHTML = `
        <div class="search-item-type">
          <span class="search-type-badge type-${item.type.toLowerCase()}">${TYPE_ICON[item.type] || ""} ${TYPE_LABEL[item.type] || item.type}</span>
        </div>
        <div class="search-item-body">
          <p class="search-item-title">${escapeHtml(item.title || "")}</p>
          ${item.preview ? `<p class="search-item-preview">${escapeHtml(item.preview)}</p>` : ""}
          <p class="search-item-meta">${item.contextName || ""} · ${fmtDate(item.createdAt)}</p>
        </div>`;
      searchResults.appendChild(div);
    });
  } catch (err) {
    searchResults.innerHTML = '<p class="search-empty">서버 연결 오류</p>';
  }
}

function escapeHtml(str) {
  return str.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}

/* ─────────────────────────────────────────
 * 탭 전환
 * ───────────────────────────────────────── */
function initTabs() {
  document.querySelectorAll(".tab-btn").forEach((btn) => {
    btn.addEventListener("click", () => {
      const tabName = btn.dataset.tab;
      document.querySelectorAll(".tab-btn").forEach((b) => b.classList.remove("active"));
      document.querySelectorAll(".tab-panel").forEach((p) => p.classList.add("hidden"));
      btn.classList.add("active");
      document.getElementById(`tab-${tabName}`).classList.remove("hidden");
      if (tabName === "releases") loadReleases();
    });
  });
}

/* ─────────────────────────────────────────
 * 배포 관리
 * ───────────────────────────────────────── */
const STATUS_LABEL = { UPLOADED: "대기", ACTIVE: "운영중", PREVIOUS: "이전", DEPRECATED: "폐기" };
const STATUS_CLASS = { UPLOADED: "status-uploaded", ACTIVE: "status-active", PREVIOUS: "status-prev", DEPRECATED: "status-dep" };
const ACTION_LABEL = { ACTIVATED: "활성화", ROLLED_BACK: "롤백" };

function fmtSize(bytes) {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

function fmtDate(iso) {
  if (!iso) return "-";
  return new Date(iso).toLocaleString("ko-KR");
}

async function loadReleases() {
  const user = getUser();

  try {
    const res = await apiFetch("/api/admin/releases");
    const json = await res.json();
    const tbody = document.getElementById("releaseTableBody");
    tbody.innerHTML = "";
    (json.data || []).forEach((r) => {
      const tr = document.createElement("tr");
      tr.innerHTML = `
        <td><strong>${r.version}</strong></td>
        <td>${r.fileName}</td>
        <td>${fmtSize(r.fileSize)}</td>
        <td><span class="status-badge ${STATUS_CLASS[r.status] || ""}">${STATUS_LABEL[r.status] || r.status}</span></td>
        <td>${fmtDate(r.uploadedAt)}</td>
        <td>${fmtDate(r.activatedAt)}</td>
        <td class="action-cell">
          ${r.status !== "ACTIVE" && r.status !== "DEPRECATED"
            ? `<button class="btn-sm btn-activate" data-id="${r.id}" data-ver="${r.version}">활성화</button>`
            : ""}
          ${r.status === "UPLOADED" || r.status === "DEPRECATED"
            ? `<button class="btn-sm btn-danger btn-delete" data-id="${r.id}">삭제</button>`
            : ""}
        </td>`;
      tbody.appendChild(tr);
    });

    tbody.querySelectorAll(".btn-activate").forEach((btn) => {
      btn.addEventListener("click", async () => {
        if (!confirm(`v${btn.dataset.ver}을 운영 버전으로 활성화하시겠습니까?`)) return;
        const r = await apiFetch(`/api/admin/releases/${btn.dataset.id}/activate`, {
          method: "POST",
          body: JSON.stringify({ actorUserId: user ? user.userId : null, note: "수동 활성화" }),
        });
        alert(r.ok ? "활성화 완료" : "활성화 실패");
        loadReleases();
      });
    });

    tbody.querySelectorAll(".btn-delete").forEach((btn) => {
      btn.addEventListener("click", async () => {
        if (!confirm("이 릴리즈 파일을 삭제하시겠습니까?")) return;
        const uid = user ? user.userId : "";
        const r = await apiFetch(`/api/admin/releases/${btn.dataset.id}?actorUserId=${uid}`, {
          method: "DELETE",
        });
        alert(r.ok ? "삭제 완료" : "삭제 실패");
        loadReleases();
      });
    });
  } catch (e) {
    console.error("릴리즈 목록 로드 실패", e);
  }

  try {
    const res = await apiFetch("/api/admin/releases/history");
    const json = await res.json();
    const hbody = document.getElementById("deployHistoryBody");
    hbody.innerHTML = "";
    (json.data || []).forEach((h) => {
      const tr = document.createElement("tr");
      tr.innerHTML = `
        <td>${fmtDate(h.createdAt)}</td>
        <td><span class="action-badge action-${h.action.toLowerCase()}">${ACTION_LABEL[h.action] || h.action}</span></td>
        <td>${h.fromVersion || "-"}</td>
        <td><strong>${h.toVersion}</strong></td>
        <td>${h.note || "-"}</td>`;
      hbody.appendChild(tr);
    });
  } catch (e) {
    console.error("배포 이력 로드 실패", e);
  }
}

document.getElementById("refreshReleasesBtn").addEventListener("click", loadReleases);

document.getElementById("releaseUploadForm").addEventListener("submit", async (e) => {
  e.preventDefault();
  const user = getUser();
  const version = document.getElementById("releaseVersion").value.trim();
  const file = document.getElementById("releaseFile").files[0];
  const description = document.getElementById("releaseDescription").value.trim();
  const statusEl = document.getElementById("releaseUploadStatus");

  if (!version || !file) {
    statusEl.textContent = "버전과 파일을 모두 입력하세요.";
    return;
  }

  const formData = new FormData();
  formData.append("version", version);
  formData.append("file", file);
  if (description) formData.append("description", description);
  if (user && user.userId) formData.append("uploadedBy", user.userId);

  statusEl.textContent = "업로드 중...";
  try {
    const res = await fetch(`${API_BASE}/api/admin/releases`, {
      method: "POST",
      headers: { Authorization: `Bearer ${getToken()}` },
      body: formData,
    });
    const json = await res.json();
    if (res.ok) {
      statusEl.textContent = `완료: v${json.data ? json.data.version : ""} 업로드 성공`;
      document.getElementById("releaseUploadForm").reset();
      loadReleases();
    } else {
      statusEl.textContent = `실패: ${json.error ? json.error.message : "업로드 오류"}`;
    }
  } catch (err) {
    statusEl.textContent = "서버 연결 오류";
  }
});

/* ─────────────────────────────────────────
 * 초기화 — 이미 토큰이 있으면 바로 메인으로
 * ───────────────────────────────────────── */
(async function init() {
  const token = getToken();
  const user = getUser();

  if (!token || !user) {
    showLogin();
    return;
  }

  // 토큰 유효성 서버 검증
  try {
    const res = await apiFetch("/api/auth/me");
    if (res.ok) {
      showMain(user);
    } else {
      clearSession();
      showLogin();
    }
  } catch {
    // 서버 미기동 시 오프라인 상태로 로그인 화면 표시
    clearSession();
    showLogin();
  }
})();
