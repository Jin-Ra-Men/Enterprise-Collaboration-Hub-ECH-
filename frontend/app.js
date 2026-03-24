/* =====================================================================
 * ECH Frontend — app.js
 * - 로그인/로그아웃 처리
 * - JWT 토큰 관리 (sessionStorage)
 * - 모든 API 호출에 Authorization 헤더 자동 첨부
 * - Socket.io 실시간 메시지
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
  initSocket(user);
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
