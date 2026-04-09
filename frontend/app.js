/* ==========================================================================
 * ECH Frontend — app.js  (Slack-inspired)
 * - 로그인/로그아웃 (JWT, sessionStorage)
 * - 사이드바: 내 채널 목록 / DM 목록
 * - 채팅: 메시지 내역 로드 + Socket.io 실시간 수신/전송
 * - 파일 첨부 업로드 (현재 채널 기반)
 * - 채널/DM 만들기 (조직도 사용자 검색)
 * - 관리자 전용: 배포 관리, 앱 설정
 * ========================================================================== */

function readEchMeta(name) {
  try {
    const el = document.querySelector(`meta[name="${name}"]`);
    const c = el?.getAttribute("content");
    if (c == null) return "";
    const t = String(c).trim();
    return t.length ? t : "";
  } catch {
    return "";
  }
}

/** REST API 베이스(기본: 현재 페이지 origin). 메타 `ech-api-base` 또는 운영 프록시에서 주입 가능. */
function resolveApiBase() {
  const meta = readEchMeta("ech-api-base");
  if (meta) return meta.replace(/\/$/, "");
  try {
    const o = window.location?.origin;
    // Electron `loadFile` → origin이 `file://` 또는 불투명 origin 문자열 `"null"` 인 경우가 있어 REST URL로 쓰면 fetch가 깨짐
    if (o && o !== "null" && /^https?:\/\//i.test(o)) return o.replace(/\/$/, "");
  } catch {
    /* ignore */
  }
  return "http://localhost:8080";
}

/**
 * Socket.io 실시간 서버 URL.
 * 우선순위: localStorage `ech_realtime_url` > meta `ech-realtime-url` > `{pageProto}//{hostname}:3001`
 * (127.0.0.1로 열면 127.0.0.1:3001로 붙어 localhost 고정 시 흔한 불일치를 방지)
 */
function resolveSocketUrl() {
  try {
    const ls = localStorage.getItem("ech_realtime_url");
    if (ls && String(ls).trim()) return String(ls).trim().replace(/\/$/, "");
  } catch {
    /* ignore */
  }
  const meta = readEchMeta("ech-realtime-url");
  if (meta) return meta.replace(/\/$/, "");
  try {
    const { protocol, hostname } = window.location;
    if (protocol === "file:" || protocol === "chrome:" || protocol === "app:") {
      return "http://localhost:3001";
    }
    const host = hostname || "localhost";
    const scheme = protocol === "https:" ? "https" : "http";
    return `${scheme}://${host}:3001`;
  } catch {
    /* ignore */
  }
  return "http://localhost:3001";
}

const API_BASE = resolveApiBase();
const SOCKET_URL = resolveSocketUrl();
const TOKEN_KEY  = "ech_token";
const USER_KEY   = "ech_user";
const WS_KEY     = "ECH"; // 기본 워크스페이스 키
/** 타임라인 API 한 번에 요청하는 최대 행 수(서버 상한 200) */
const TIMELINE_PAGE_LIMIT = 100;
/** 하단 근처에 있을 때만 오래된 DOM 노드를 정리(위로 히스토리 탐색 중에는 유지) */
const MAX_CHAT_DOM_NODES = 4000;
const HARD_MAX_CHAT_DOM_NODES = 10000;
const THEME_KEY  = "ech_theme";
const VALID_THEMES = ["dark", "light"];
const SIDEBAR_COLLAPSED_KEY = "ech_sidebar_collapsed";
const LOGIN_REMEMBER_KEY = "ech_login_remember";
const LOGIN_SAVED_ID_KEY = "ech_saved_login_id";
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
  let saved = null;
  try {
    saved = localStorage.getItem(THEME_KEY);
  } catch (e) {
    saved = null;
  }
  if (saved === null || saved === "") {
    applyTheme("light", { persistLocal: false });
    return;
  }
  if (saved === "blue") {
    saved = "dark";
    try {
      localStorage.setItem(THEME_KEY, "dark");
    } catch (e2) {
      /* ignore */
    }
  }
  if (!VALID_THEMES.includes(saved)) saved = "light";
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
      await uiAlert(json.error?.message || "테마 저장에 실패했습니다.");
    }
  } catch (e) {
    console.error("테마 저장 실패", e);
    await uiAlert("테마 저장 중 오류가 발생했습니다.");
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
let activeChannelMemberCount = 0;
/** 현재 채널 멤버만 @자동완성 (전사 검색 대신). selectChannel 시 비움 → loadChannelMembers에서 채움 */
let activeChannelMemberMentionList = [];
/** DM 채팅 헤더: 본인 제외 멤버 사번(사이드바와 동일한 프레즌스 점 표시용) */
let activeDmPeerEmployeeNos = [];
/** `modalImageDownloadChoice` 닫기 시 Promise resolve */
let imageDownloadChoiceResolve = null;
let imageDownloadChoiceEscHandler = null;
/** 현재 채널 멤버의 조직/직급 캐시(employeeNo -> 조직표시문구) */
const activeChannelMemberOrgLineByEmployeeNo = new Map();
/** 미확인 멘션 목록(사이드바 전용) */
let mentionInboxItems = [];
/** Sidebar: work items where user is assignee on ≥1 kanban card (`GET .../sidebar/by-assigned-cards`). */
let mySidebarWorkItems = [];
/** `loadMyChannels` 간 내 업무 사이드바 스냅샷 비교용(변경 시 토스트). */
let lastWorkSidebarSig = null;
/** Last loaded channel work items for work hub (new card parent select + labels). */
let lastChannelWorkItemsForHub = [];
/** 메인 입력창 대기 첨부(다중 이미지·파일) */
let pendingFilesQueue = [];
let composerPendingSequence = 0;
// 스레드(댓글) 모달 상태
let threadPendingFilesQueue = [];
let threadPendingSequence = 0;
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
let memberContextSelectedEmployeeNo = "";
// 타임라인에서 로드된 ROOT 메시지 캐시(스레드 모달 렌더용)
let timelineRootMessageById = new Map();
/** 타임라인 `hasMoreOlder`(서버 페이지네이션) */
let chatTimelineHasMoreOlder = false;
let chatTimelineLoadingOlder = false;
let messageListScrollHandlerBound = false;
/** 마지막 읽은 위치 앵커 저장(디바운스) 바인딩 여부 */
// loadMessages(preserveScroll=true)일 때만 렌더 함수의 자동 스크롤을 억제/복원하기 위한 비율
let pendingScrollRestoreRatio = null;
/** 채널별 저장된 마지막 읽은 위치 앵커 복원 시 render 단계 하단 고정 1회 생략 */
let skipAutoScrollToBottomOnce = false;
/** 채널 전환 시 유지: 입력 텍스트·답글 대상·대기 첨부 파일 */
const composerDraftByChannelId = new Map();
/** 열람 중 채널 실시간 읽음 처리 디바운스 */
let markChannelCaughtUpTimer = null;
let selectedMembers = [];     // 채널/DM 생성 시 선택된 사용자
let selectedDmMembers = [];
let selectedAddMembers = [];  // 기존 채널에 추가할 사용자
let activeWorkHubBoardId = null;
let activeWorkHubFirstColumnId = null;
let activeWorkHubColumns = [];
/** Per-channel generation for `GET .../kanban/channels/{id}/board` — stale responses must not call `renderKanbanBoard`. */
const kanbanBoardFetchGenByChannelId = Object.create(null);

function bumpKanbanBoardFetchGeneration(channelId) {
  const k = String(channelId);
  const next = (kanbanBoardFetchGenByChannelId[k] || 0) + 1;
  kanbanBoardFetchGenByChannelId[k] = next;
  return next;
}

function kanbanBoardFetchIsStale(channelId, myGen) {
  if (getWorkHubChannelId() !== Number(channelId)) return true;
  const current = kanbanBoardFetchGenByChannelId[String(channelId)] || 0;
  return current !== myGen;
}
/** 칸반 카드 담당자 표시명 캐시(사번 → 이름) */
const kanbanAssigneeNameCache = Object.create(null);
let kanbanBoardAssigneeUiBound = false;
/** Source column id at kanban card drag start (cross-column DnD sync). */
let kanbanDnDSourceColumnId = null;
/** Sidebar / quick rail: context menu target channel */
let sidebarCtxChannelId = null;
let sidebarCtxChannelName = "";
let sidebarCtxChannelType = "PUBLIC";
/** `loadMyChannels` 직후 스냅샷 — 알림 음소거 토글 시 목록 아이콘만 다시 그릴 때 사용 */
let lastSidebarChannelsSnapshot = [];
/** 신규 카드 폼에 미리 넣을 담당자 `{ employeeNo, name }` */
let pendingNewKanbanCardAssignees = [];
let kanbanNewCardAssigneeUiBound = false;
/** 채널 허브 담당 검색용 멤버 목록 (`GET /api/channels/{id}`) */
let workHubChannelMembersForAssignee = [];
/** 업무 허브: 저장 시 반영할 업무 상태 변경 (workItemId → status) */
let workHubPendingWorkStatus = new Map();
let workHubPendingWorkTitle = new Map();
let workHubPendingWorkDescription = new Map();
/** 업무 허브: 저장 시 반영할 칸반 카드 컬럼 이동 (cardId → columnId) */
let workHubPendingCardColumn = new Map();
/** 업무 허브: 저장 시 반영할 칸반 카드 정렬값 (cardId -> sortOrder) */
let workHubPendingCardSortOrder = new Map();
let workHubPendingCardTitle = new Map();
let workHubPendingCardDescription = new Map();
/** 업무 허브: 저장 시 반영할 업무 항목 삭제 ID */
let workHubPendingWorkDeleteIds = new Set();
/** 저장 시 POST /restore 로 반영 */
let workHubPendingWorkRestoreIds = new Set();
/** 저장 시 DELETE hard 로 반영 */
let workHubPendingWorkPurgeIds = new Set();
/** 업무 허브: 저장 시 반영할 칸반 카드 삭제 ID */
let workHubPendingCardDeleteIds = new Set();
/** 업무 허브: 저장 시 생성할 신규 업무 draft */
let workHubPendingNewWorkItems = [];
/** 업무 허브: 저장 시 생성할 신규 카드 draft */
let workHubPendingNewKanbanCards = [];
/** 업무 허브: 저장 시 반영할 카드 담당 추가/삭제 */
let workHubPendingCardAssigneeAdd = new Map();
let workHubPendingCardAssigneeRemove = new Map();
let workHubWorkListDeleteBound = false;
let workHubSelectedWorkItemMeta = null;
let workHubSelectedKanbanCardMeta = null;
let workHubDetailKanbanAssignees = [];
let workHubDetailKanbanAssigneesInitial = [];
/** 다른 채널에 새 메시지 시 목록 갱신 디바운스 */
let refreshChannelListTimer = null;
let windowFocusChannelsTimer = null;
let orgPickerContext = null;  // member | dm | channelMember
let orgPickerEmbedElId = null; // member/dm/channelMember 조직도 체크박스가 그려진 엘리먼트 id
/** 프로필 모달에 표시 중인 사용자 사번 (DM 보내기용) */
let profileViewEmployeeNo = null;
/** 업무/칸반 모달 상태 */
let workHubBoardId = null;
let workHubColumns = [];
/** When set, Work Hub loads/saves for this channel without switching the main chat view. */
let workHubScopedChannelId = null;
/** 업무·칸반 모달 내 업무 목록에서 강조할 행 (`data-work-item-id`와 동일 문자열). */
let workHubSelectedListWorkItemKey = null;

function getWorkHubChannelId() {
  const scoped = workHubScopedChannelId != null ? Number(workHubScopedChannelId) : null;
  if (scoped != null && Number.isFinite(scoped) && scoped > 0) return scoped;
  return activeChannelId != null ? Number(activeChannelId) : null;
}

function clearWorkHubScopedChannel() {
  workHubScopedChannelId = null;
  workHubSelectedListWorkItemKey = null;
}

/** 좌측 하단 프레즌스 메뉴 이벤트(재로그인 시 중복 바인딩 방지) */
let sidebarPresenceUiBound = false;
let welcomeDashboardShellBound = false;
/** 사이드바 섹션 토글·접기 버튼은 initEvents가 여러 번 호출돼도 한 번만 바인딩 */
let sidebarSectionTogglesBound = false;
let sidebarCollapseBound = false;
let mentionInboxUiBound = false;

/* ── DOM 참조 ── */
const loginPage      = document.getElementById("loginPage");
const mainApp        = document.getElementById("mainApp");
const loginForm      = document.getElementById("loginForm");
const loginErrorEl   = document.getElementById("loginError");
const loginBtn       = document.getElementById("loginBtn");
const themeSettingsBtn = document.getElementById("themeSettingsBtn");
const sidebarAvatar  = document.getElementById("sidebarAvatar");
const sidebarUserName = document.getElementById("sidebarUserName");
const channelListEl  = document.getElementById("channelList");
const dmListEl       = document.getElementById("dmList");
const myKanbanListEl = document.getElementById("myKanbanList");
const messagesEl     = document.getElementById("messages");
const messageInputEl = document.getElementById("messageInput");
const messageContextMenuEl = document.getElementById("messageContextMenu");
const memberContextMenuEl = document.getElementById("memberContextMenu");
const channelSidebarContextMenuEl = document.getElementById("channelSidebarContextMenu");
const btnMemberCtxDelegateEl = document.getElementById("btnMemberCtxDelegate");
const threadRootContainerEl = document.getElementById("threadRootContainer");
const threadCommentsContainerEl = document.getElementById("threadCommentsContainer");
const threadFileInputEl = document.getElementById("threadFileInput");
const threadBtnAttachEl = document.getElementById("threadBtnAttach");
const threadMessageInputEl = document.getElementById("threadMessageInput");
const threadBtnSendEl = document.getElementById("threadBtnSend");
const threadFilePreviewEl = document.getElementById("threadFilePreview");
const threadFilePreviewThumbEl = document.getElementById("threadFilePreviewThumb");
const threadFilePreviewNameEl = document.getElementById("threadFilePreviewName");
const threadFilePreviewUploadStatusEl = document.getElementById("threadFilePreviewUploadStatus");
const threadBtnClearFileEl = document.getElementById("threadBtnClearFile");
const btnSendEl = document.getElementById("btnSend");
const filePreviewUploadStatusEl = document.getElementById("filePreviewUploadStatus");
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
  revokeImageAttachmentBlobUrls();
  composerDraftByChannelId.clear();
}

function mentionInboxStorageKey() {
  const emp = String(currentUser?.employeeNo || "").trim();
  if (!emp) return "";
  return `ech_mention_inbox_${emp}`;
}

function loadMentionInboxFromStorage() {
  const key = mentionInboxStorageKey();
  if (!key) return [];
  try {
    const raw = localStorage.getItem(key);
    const parsed = raw ? JSON.parse(raw) : [];
    if (!Array.isArray(parsed)) return [];
    return parsed
      .filter((x) => x && Number.isFinite(Number(x.channelId)))
      .map((x) => ({
        id: String(x.id || ""),
        messageId: x.messageId != null && Number.isFinite(Number(x.messageId)) ? Number(x.messageId) : null,
        channelId: Number(x.channelId),
        channelName: String(x.channelName || "채널"),
        channelType: String(x.channelType || "PUBLIC"),
        senderName: String(x.senderName || ""),
        messagePreview: String(x.messagePreview || ""),
        createdAt: String(x.createdAt || ""),
      }));
  } catch {
    return [];
  }
}

function saveMentionInboxToStorage() {
  const key = mentionInboxStorageKey();
  if (!key) return;
  try {
    localStorage.setItem(key, JSON.stringify(mentionInboxItems.slice(0, 100)));
  } catch {
    /* ignore */
  }
}

function renderMentionInboxList() {
  const listEl = document.getElementById("mentionList");
  const badgeEl = document.getElementById("mentionUnreadBadge");
  if (!listEl || !badgeEl) return;
  listEl.innerHTML = "";
  const count = mentionInboxItems.length;
  badgeEl.textContent = String(Math.min(count, 99));
  badgeEl.classList.toggle("hidden", count === 0);
  if (count === 0) {
    const li = document.createElement("li");
    li.className = "sidebar-item sidebar-item-empty";
    li.textContent = "미확인 멘션 없음";
    listEl.appendChild(li);
    return;
  }
  mentionInboxItems.forEach((it) => {
    const li = document.createElement("li");
    li.className = "sidebar-item mention-item";
    li.dataset.mentionId = it.id;
    li.innerHTML = `
      <span class="mention-item-title">${escHtml(`${it.senderName || "알림"} · ${it.channelName}`)}</span>
      <span class="mention-item-preview">${escHtml(it.messagePreview || "(미리보기 없음)")}</span>
    `;
    listEl.appendChild(li);
  });
}

function enqueueUnreadMention(payload) {
  if (!payload) return;
  const channelId = Number(payload.channelId);
  if (!Number.isFinite(channelId)) return;
  const senderEmp = String(payload.senderEmployeeNo || "").trim();
  const myEmp = String(currentUser?.employeeNo || "").trim();
  if (senderEmp && myEmp && senderEmp === myEmp) return;
  const messageId = payload.messageId != null && Number.isFinite(Number(payload.messageId))
    ? Number(payload.messageId)
    : null;
  if (messageId != null && mentionInboxItems.some((x) => x.messageId === messageId)) return;
  const id = messageId != null
    ? `m_${messageId}`
    : `c_${channelId}_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;
  mentionInboxItems.unshift({
    id,
    messageId,
    channelId,
    channelName: String(payload.channelName || "채널"),
    channelType: String(payload.channelType || "PUBLIC"),
    senderName: String(payload.senderName || ""),
    messagePreview: String(payload.messagePreview || "").slice(0, 200),
    createdAt: String(payload.createdAt || new Date().toISOString()),
  });
  mentionInboxItems = mentionInboxItems.slice(0, 100);
  saveMentionInboxToStorage();
  renderMentionInboxList();
}

function removeUnreadMentionById(id) {
  const target = String(id || "").trim();
  if (!target) return null;
  const idx = mentionInboxItems.findIndex((x) => x.id === target);
  if (idx < 0) return null;
  const [picked] = mentionInboxItems.splice(idx, 1);
  saveMentionInboxToStorage();
  renderMentionInboxList();
  return picked;
}

async function openUnreadMentionById(id) {
  const item = removeUnreadMentionById(id);
  if (!item) return;
  await selectChannel(item.channelId, item.channelName, item.channelType, {
    targetMessageId: item.messageId,
  });
}

/** 인증된 다운로드로 만든 이미지 blob URL (채널 재입장 시 재사용 — 네트워크 재요청 방지) */
const imageAttachmentBlobUrls = new Map();
const MAX_CACHED_IMAGE_BLOBS = 120;
/** 이미지 모아보기 그리드: 스크롤 영역에 들어올 때만 썸네일 요청 */
let fileHubImageGridObserver = null;

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

/** Map 삽입 순서 기준으로 오래된 blob부터 제거(장시간 다채널 탐색 시 메모리 상한) */
function trimImageBlobCacheIfNeeded() {
  while (imageAttachmentBlobUrls.size > MAX_CACHED_IMAGE_BLOBS) {
    const firstKey = imageAttachmentBlobUrls.keys().next().value;
    if (firstKey == null) break;
    const url = imageAttachmentBlobUrls.get(firstKey);
    try {
      if (url) URL.revokeObjectURL(url);
    } catch {
      /* ignore */
    }
    imageAttachmentBlobUrls.delete(firstKey);
  }
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

function appDialogElements() {
  return {
    modal: document.getElementById("modalAppDialog"),
    title: document.getElementById("appDialogTitle"),
    badge: document.getElementById("appDialogBadge"),
    message: document.getElementById("appDialogMessage"),
    input: document.getElementById("appDialogInput"),
    ok: document.getElementById("btnAppDialogOk"),
    cancel: document.getElementById("btnAppDialogCancel"),
    close: document.getElementById("btnCloseAppDialog"),
  };
}

function openAppDialog({ title = "알림", message = "", mode = "alert", defaultValue = "" }) {
  const els = appDialogElements();
  if (!els.modal || !els.title || !els.message || !els.ok || !els.cancel || !els.input || !els.badge) {
    if (mode === "confirm") return Promise.resolve(window.confirm(message));
    if (mode === "prompt") return Promise.resolve(window.prompt(message, defaultValue || ""));
    window.alert(message);
    return Promise.resolve(true);
  }
  return new Promise((resolve) => {
    const cleanup = () => {
      els.ok.removeEventListener("click", onOk);
      els.cancel.removeEventListener("click", onCancel);
      if (els.close) els.close.removeEventListener("click", onCancel);
      els.modal.removeEventListener("click", onOverlay);
      document.removeEventListener("keydown", onEsc);
      els.modal.classList.add("hidden");
      els.modal.removeAttribute("data-dialog-mode");
      els.modal.removeAttribute("data-dialog-tone");
      els.input.classList.add("hidden");
    };
    const finish = (v) => {
      cleanup();
      resolve(v);
    };
    const onOk = () => {
      if (mode === "prompt") {
        finish(String(els.input.value || "").trim());
        return;
      }
      finish(true);
    };
    const onCancel = () => finish(mode === "prompt" ? null : false);
    const onOverlay = (e) => {
      if (e.target === els.modal) onCancel();
    };
    const onEsc = (e) => {
      if (e.key === "Escape") onCancel();
    };
    const txt = String(message || "");
    const isDanger = mode === "confirm" && /(삭제|폐쇄|나가기|내보내기|활성화)/.test(txt);
    els.modal.dataset.dialogMode = mode;
    els.modal.dataset.dialogTone = isDanger ? "danger" : "normal";
    els.title.textContent = title;
    els.message.textContent = txt;
    els.badge.textContent = mode === "prompt" ? "✎" : mode === "confirm" ? "?" : "i";
    els.cancel.classList.toggle("hidden", mode === "alert");
    els.ok.textContent = mode === "confirm" ? "확인" : "확인";
    if (mode === "prompt") {
      els.input.classList.remove("hidden");
      els.input.value = String(defaultValue || "");
      setTimeout(() => els.input.focus(), 0);
      els.input.onkeydown = (e) => {
        if (e.key === "Enter") {
          e.preventDefault();
          onOk();
        }
      };
    } else {
      els.input.classList.add("hidden");
      els.input.onkeydown = null;
      setTimeout(() => els.ok.focus(), 0);
    }
    els.ok.addEventListener("click", onOk);
    els.cancel.addEventListener("click", onCancel);
    if (els.close) els.close.addEventListener("click", onCancel);
    els.modal.addEventListener("click", onOverlay);
    document.addEventListener("keydown", onEsc);
    els.modal.classList.remove("hidden");
  });
}

function uiAlert(message, title = "알림") {
  return openAppDialog({ title, message, mode: "alert" });
}
function uiConfirm(message, title = "확인") {
  return openAppDialog({ title, message, mode: "confirm" });
}
function uiPrompt(message, defaultValue = "", title = "입력") {
  return openAppDialog({ title, message, mode: "prompt", defaultValue });
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
  return parts.join("").replace(/\n/g, "<br>");
}

function isMessageFromMe(msg) {
  if (!msg || !currentUser) return false;
  const emp = String(msg.senderId ?? msg.sender_id ?? "").trim();
  const me = String(currentUser.employeeNo || "").trim();
  return emp !== "" && me !== "" && emp === me;
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
    const res = await fetch(`${SOCKET_URL}/presence`, { cache: "no-store" });
    if (!res.ok) {
      console.warn("[ECH] 프레즌스 HTTP 오류", SOCKET_URL, "status=", res.status);
      return;
    }
    const json = await res.json();
    (json.data || []).forEach((p) => {
      const emp = String(p.employeeNo || "").trim();
      if (emp) presenceByEmployeeNo.set(emp, String(p.status || "OFFLINE").toUpperCase());
    });
  } catch (e) {
    const name = e && e.name;
    const msg = e && e.message;
    console.warn(
      "[ECH] 프레즌스 스냅샷 실패 —",
      SOCKET_URL,
      name ? `${name}: ${msg}` : msg || e,
      "| 대개 원인: Realtime 서버 미기동(포트 3001). 저장소 `realtime`에서 `npm install` 후 `npm run dev` 실행."
    );
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

/** DM 줄 왼쪽·채팅 헤더: 상대 사번이 있으면 프레즌스 점(없으면 기본 ●) */
function updateChatHeaderDmPresence() {
  const prefixEl = document.getElementById("chatChannelPrefix");
  if (!prefixEl) return;
  if (String(activeChannelType || "").toUpperCase() !== "DM") return;
  prefixEl.className = "chat-channel-prefix chat-header-dm-prefix";
  prefixEl.innerHTML = dmSidebarLeadingHtml(activeDmPeerEmployeeNos);
  refreshPresenceDots();
}

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
      await uiAlert(json.error?.message || "프로필을 불러올 수 없습니다.");
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
    const subEl = document.getElementById("profileModalSubtitle");
    if (subEl) {
      const dept = (u.department && String(u.department).trim()) || "";
      const jt = jl ? String(u.jobLevel).trim() : "";
      const parts = [dept, jt].filter(Boolean);
      subEl.textContent = parts.length ? parts.join(" · ") : "—";
    }
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
    await uiAlert("프로필 요청 중 오류가 발생했습니다.");
  }
}

/** 프로필·기타에서 동일 플로우로 DM 채널 생성 후 입장 */
async function startDmWithUser(peerEmployeeNo, displayName) {
  if (!currentUser) return;
  const peer = String(peerEmployeeNo || "").trim();
  if (!peer) return;
  if (peer === String(currentUser.employeeNo || "").trim()) {
    await uiAlert("자기 자신과는 DM을 할 수 없습니다.");
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
      await uiAlert("DM 생성 실패: " + (json.error?.message || ""));
      return;
    }
    const channelId = json.data?.channelId;
    closeModal("modalUserProfile");
    await loadMyChannels();
    selectChannel(channelId, dmName, "DM");
  } catch (e) {
    console.error(e);
    await uiAlert("DM 생성 중 오류 발생");
  }
}

function filterImageFilesForHub(files) {
  if (!Array.isArray(files)) return [];
  return files.filter((f) => isImageContentType(f.contentType, f.originalFilename));
}

function filterNonImageFilesForHub(files) {
  if (!Array.isArray(files)) return [];
  return files.filter((f) => !isImageContentType(f.contentType, f.originalFilename));
}

function setFileHubTab(tab) {
  const allPane = document.getElementById("fileHubPaneAll");
  const imgPane = document.getElementById("fileHubPaneImages");
  document.querySelectorAll(".file-hub-tab[data-file-hub-tab]").forEach((b) => {
    const on = b.dataset.fileHubTab === tab;
    b.classList.toggle("active", on);
    b.setAttribute("aria-selected", on ? "true" : "false");
  });
  if (allPane) allPane.classList.toggle("hidden", tab !== "all");
  if (imgPane) imgPane.classList.toggle("hidden", tab !== "images");
  if (tab === "images" && fileHubImageGridObserver) {
    requestAnimationFrame(() => {
      document.querySelectorAll("#channelImageGrid .channel-image-grid-item").forEach((cell) => {
        if (!cell.querySelector(".channel-image-thumb-placeholder")) return;
        fileHubImageGridObserver.unobserve(cell);
        fileHubImageGridObserver.observe(cell);
      });
    });
  }
}

function renderFileHubImageGrid(channelId, files) {
  const grid = document.getElementById("channelImageGrid");
  const emptyImg = document.getElementById("channelImagesEmpty");
  const scrollRoot = document.getElementById("fileHubPaneImages");
  if (!grid) return;
  fileHubImageGridObserver?.disconnect();
  fileHubImageGridObserver = null;
  grid.innerHTML = "";
  const imgs = filterImageFilesForHub(files);
  if (imgs.length === 0) {
    emptyImg?.classList.remove("hidden");
    return;
  }
  emptyImg?.classList.add("hidden");
  const cid = Number(channelId);
  fileHubImageGridObserver = new IntersectionObserver(
    (entries) => {
      entries.forEach((entry) => {
        if (!entry.isIntersecting) return;
        const cell = entry.target;
        const load = cell._loadHubThumb;
        if (typeof load === "function") {
          fileHubImageGridObserver?.unobserve(cell);
          load();
        }
      });
    },
    { root: scrollRoot, rootMargin: "160px 0px", threshold: 0 }
  );
  imgs.forEach((f) => {
    const cell = document.createElement("div");
    cell.className = "channel-image-grid-item";
    const safeName = escHtml(f.originalFilename || "이미지");
    cell.innerHTML = `
      <button type="button" class="channel-image-thumb-btn" title="크게 보기" aria-label="${safeName}">
        <div class="channel-image-thumb-placeholder">…</div>
      </button>
      <div class="channel-image-thumb-meta">${escHtml(f.uploaderName || f.uploadedByEmployeeNo || "")} · ${escHtml(fmtDate(f.createdAt))}</div>`;
    const btn = cell.querySelector(".channel-image-thumb-btn");
    const ph = cell.querySelector(".channel-image-thumb-placeholder");
    const fileMeta = {
      id: f.id,
      sizeBytes: f.sizeBytes,
      previewSizeBytes: f.previewSizeBytes,
      contentType: f.contentType,
      originalFilename: f.originalFilename,
      hasPreview: f.hasPreview,
    };
    cell._loadHubThumb = () => {
      ph.textContent = "불러오는 중…";
      getAuthedPreviewBlobUrl(cid, f.id)
        .then((thumbUrl) => {
          const im = document.createElement("img");
          im.className = "channel-image-thumb-img";
          im.alt = f.originalFilename || "첨부 이미지";
          im.src = thumbUrl;
          im.loading = "lazy";
          ph.replaceWith(im);
          btn.addEventListener("click", () => {
            void openChannelImageLightbox(cid, f.id, f.originalFilename, fileMeta);
          });
        })
        .catch(() => {
          ph.textContent = "로드 실패";
        });
    };
    fileHubImageGridObserver.observe(cell);
    grid.appendChild(cell);
  });
}

/** 채널/DM 공통: 첨부 목록 API로 전체 파일 + 이미지 그리드 갱신 */
async function refreshChannelFileHubData(channelId) {
  const listEl = document.getElementById("channelFilesList");
  const emptyEl = document.getElementById("channelFilesEmpty");
  const emptyImg = document.getElementById("channelImagesEmpty");
  const grid = document.getElementById("channelImageGrid");
  if (!currentUser || !listEl) return;
  listEl.innerHTML = "";
  if (grid) grid.innerHTML = "";
  if (emptyEl) emptyEl.classList.add("hidden");
  if (emptyImg) emptyImg.classList.add("hidden");
  try {
    const res = await apiFetch(
      `/api/channels/${channelId}/files?employeeNo=${encodeURIComponent(currentUser.employeeNo)}`
    );
    const json = await res.json();
    if (!res.ok) return;
    if (Number(channelId) !== Number(activeChannelId)) return;
    const files = json.data || [];
    if (files.length === 0) {
      if (emptyEl) {
        emptyEl.textContent = "첨부 파일이 없습니다.";
        emptyEl.classList.remove("hidden");
      }
      emptyImg?.classList.remove("hidden");
      return;
    }
    const nonImageFiles = filterNonImageFilesForHub(files);
    if (nonImageFiles.length === 0) {
      if (emptyEl) {
        emptyEl.textContent = "이미지는 「이미지」 탭에서만 표시됩니다.";
        emptyEl.classList.remove("hidden");
      }
    } else {
      emptyEl?.classList.add("hidden");
    }
    nonImageFiles.forEach((f) => {
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
        void maybeDownloadChannelImageWithChoice(f.id, f.originalFilename, channelId, {
          sizeBytes: f.sizeBytes,
          previewSizeBytes: f.previewSizeBytes,
          contentType: f.contentType,
          originalFilename: f.originalFilename,
        });
      });
      listEl.appendChild(li);
    });
    renderFileHubImageGrid(channelId, files);
  } catch (e) {
    console.error("첨부 목록 로드 실패", e);
  }
}

async function downloadChannelFile(fileId, filename, channelId, variant = "original") {
  const ch = channelId != null ? channelId : activeChannelId;
  if (!ch || !currentUser) return;
  const v = variant === "preview" ? "preview" : "original";
  try {
    const res = await fetch(
      `${API_BASE}/api/channels/${ch}/files/${fileId}/download?employeeNo=${encodeURIComponent(currentUser.employeeNo)}&variant=${encodeURIComponent(v)}`,
      { headers: { Authorization: `Bearer ${getToken()}` } }
    );
    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      const code = err.error?.code;
      const msg = err.error?.message;
      await uiAlert(
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
    const dlName =
      v === "preview"
        ? String(filename || "image").replace(/\.[^.]+$/, "") + "_preview.jpg"
        : filename || "download";
    a.download = dlName;
    a.click();
    URL.revokeObjectURL(url);
  } catch (e) {
    console.error(e);
    await uiAlert("다운로드 중 오류가 발생했습니다.");
  }
}

function uniqueZipEntryName(usedSet, originalName) {
  const raw = String(originalName || "file").replace(/[/\\]/g, "_").trim() || "file";
  if (!usedSet.has(raw)) {
    usedSet.add(raw);
    return raw;
  }
  const dot = raw.lastIndexOf(".");
  const stem = dot > 0 ? raw.slice(0, dot) : raw;
  const ext = dot > 0 ? raw.slice(dot) : "";
  let i = 2;
  let candidate;
  do {
    candidate = `${stem} (${i})${ext}`;
    i += 1;
  } while (usedSet.has(candidate));
  usedSet.add(candidate);
  return candidate;
}

/** 여러 첨부를 ZIP 한 번에 다운로드(일괄저장). JSZip 없으면 순차 다운로드로 폴백. */
async function batchDownloadChannelImageFiles(channelId, metas) {
  const ch = channelId != null ? Number(channelId) : Number(activeChannelId);
  if (!Number.isFinite(ch) || !currentUser || !Array.isArray(metas) || metas.length === 0) return;
  const JSZipCtor = typeof globalThis !== "undefined" ? globalThis.JSZip : undefined;
  if (typeof JSZipCtor !== "function") {
    for (const m of metas) {
      const fid = Number(m?.id ?? m?.fileId);
      if (!Number.isFinite(fid)) continue;
      const fn = m?.originalFilename || "download";
      await downloadChannelFile(fid, fn, ch, "original");
      await new Promise((r) => setTimeout(r, 120));
    }
    return;
  }
  try {
    const zip = new JSZipCtor();
    const used = new Set();
    for (const m of metas) {
      const fid = Number(m?.id ?? m?.fileId);
      if (!Number.isFinite(fid)) continue;
      const blob = await fetchChannelFileBlob(ch, fid, "original");
      const entryName = uniqueZipEntryName(used, m?.originalFilename || "file");
      zip.file(entryName, blob);
    }
    const outBlob = await zip.generateAsync({ type: "blob", compression: "DEFLATE" });
    const url = URL.createObjectURL(outBlob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `attachments-${ch}-${Date.now()}.zip`;
    a.click();
    setTimeout(() => {
      try {
        URL.revokeObjectURL(url);
      } catch {
        /* ignore */
      }
    }, 180000);
  } catch (e) {
    console.error(e);
    await uiAlert("ZIP 압축 중 오류가 발생했습니다.");
  }
}

async function fetchChannelFileBlob(channelId, fileId, variant = "original") {
  const ch = channelId != null ? channelId : activeChannelId;
  if (!ch || !currentUser) throw new Error("no channel");
  const v = variant === "preview" ? "preview" : "original";
  const res = await fetch(
    `${API_BASE}/api/channels/${ch}/files/${fileId}/download?employeeNo=${encodeURIComponent(currentUser.employeeNo)}&variant=${encodeURIComponent(v)}`,
    { headers: { Authorization: `Bearer ${getToken()}` } }
  );
  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error(err.error?.message || "download failed");
  }
  return res.blob();
}

async function openChannelFileBlobInNewTab(channelId, fileId, filename, variant = "original") {
  try {
    const blob = await fetchChannelFileBlob(channelId, fileId, variant);
    const url = URL.createObjectURL(blob);
    const w = window.open(url, "_blank", "noopener,noreferrer");
    if (!w) await uiAlert("팝업이 차단되었습니다. 브라우저에서 팝업을 허용해 주세요.");
    setTimeout(() => {
      try {
        URL.revokeObjectURL(url);
      } catch {
        /* ignore */
      }
    }, 180000);
  } catch (e) {
    console.error(e);
    await uiAlert("파일을 열 수 없습니다.");
  }
}

async function saveChannelFileAndOpenInNewTab(channelId, fileId, filename, variant = "original") {
  const fn = filename || "download";
  try {
    const blob = await fetchChannelFileBlob(channelId, fileId, variant);
    const electronApi = typeof window !== "undefined" ? window.electronAPI : null;
    if (electronApi && typeof electronApi.saveFileAndOpenWithDefaultApp === "function") {
      const buf = await blob.arrayBuffer();
      let result = { ok: false };
      try {
        result = await electronApi.saveFileAndOpenWithDefaultApp({ filename: fn, buffer: buf });
      } catch (e) {
        console.error(e);
        result = { ok: false, error: String(e?.message || e) };
      }
      if (result?.canceled) return;
      if (result?.ok) return;
      if (result?.error) await uiAlert(`저장 후 열기에 실패했습니다: ${result.error}`);
      return;
    }
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = fn;
    a.click();
    setTimeout(() => {
      try {
        window.open(url, "_blank", "noopener,noreferrer");
      } catch {
        /* ignore */
      }
    }, 350);
    setTimeout(() => {
      try {
        URL.revokeObjectURL(url);
      } catch {
        /* ignore */
      }
    }, 180000);
  } catch (e) {
    console.error(e);
    await uiAlert("파일을 저장/열기에 실패했습니다.");
  }
}

function attachmentIconEmoji(filename, contentType) {
  const fn = String(filename || "").toLowerCase();
  const ct = String(contentType || "").trim().toLowerCase();
  if (ct.startsWith("image/")) return "🖼️";
  if (ct.includes("pdf") || fn.endsWith(".pdf")) return "📕";
  if (fn.endsWith(".zip") || fn.endsWith(".7z") || fn.endsWith(".rar")) return "🗜️";
  if (fn.endsWith(".xlsx") || fn.endsWith(".xls") || fn.endsWith(".csv")) return "📊";
  if (fn.endsWith(".doc") || fn.endsWith(".docx")) return "📘";
  if (fn.endsWith(".ppt") || fn.endsWith(".pptx")) return "📙";
  if (fn.endsWith(".txt") || fn.endsWith(".md") || fn.endsWith(".log")) return "📄";
  if (fn.endsWith(".html") || fn.endsWith(".htm")) return "🌐";
  return "📎";
}

async function fetchChannelFileDownloadInfo(channelId, fileId) {
  if (!channelId || !fileId || !currentUser) return null;
  try {
    const res = await apiFetch(
      `/api/channels/${channelId}/files/${fileId}/download-info?employeeNo=${encodeURIComponent(currentUser.employeeNo)}`
    );
    const json = await res.json().catch(() => ({}));
    if (!res.ok) return null;
    return json.data || null;
  } catch {
    return null;
  }
}

const INLINE_IMAGE_EXT = /\.(jpe?g|png|gif|webp|bmp|svg)$/i;

function isImageContentType(contentType, filename) {
  const ct = String(contentType || "").trim().toLowerCase();
  if (ct.startsWith("image/")) return true;
  return INLINE_IMAGE_EXT.test(String(filename || ""));
}

/** 약 512KB 이상 이미지 다운로드 시 원본/압축 선택 */
const LARGE_IMAGE_DOWNLOAD_BYTES = 512 * 1024;
const DOWNLOAD_COMPRESS_MAX_EDGE = 4096;
const DOWNLOAD_COMPRESS_JPEG_QUALITY = 0.85;

function eligibleForCompressedImageDownload(contentType, filename) {
  const ct = String(contentType || "").trim().toLowerCase();
  if (ct.includes("gif") || /\.gif$/i.test(String(filename || ""))) return false;
  if (ct.includes("svg") || /\.svg$/i.test(String(filename || ""))) return false;
  return true;
}

/** 라이트박스: 서버에 미리보기가 있으면 원본 대신 JPEG 미리보기로 먼저 표시(대용량 디코딩 부담 감소) */
function hasServerPreviewForLightbox(meta) {
  if (!meta) return false;
  if (meta.hasPreview === false) return false;
  if (!eligibleForCompressedImageDownload(meta.contentType || "", meta.originalFilename)) return false;
  const sizeBytes = Number(meta.sizeBytes) || 0;
  const previewSizeBytes = Number(meta.previewSizeBytes) || 0;
  return previewSizeBytes > 0 && sizeBytes > 0 && previewSizeBytes < sizeBytes;
}

async function enrichChannelFileMetaForLightbox(channelId, fileMeta) {
  const id = Number(fileMeta?.id ?? fileMeta?.fileId);
  if (!channelId || !id) return fileMeta;
  const need =
    fileMeta.hasPreview === undefined ||
    fileMeta.previewSizeBytes == null ||
    fileMeta.sizeBytes == null;
  if (!need) return fileMeta;
  const info = await fetchChannelFileDownloadInfo(channelId, id);
  if (!info) return fileMeta;
  return {
    ...fileMeta,
    sizeBytes: fileMeta.sizeBytes ?? info.sizeBytes,
    previewSizeBytes: fileMeta.previewSizeBytes ?? info.previewSizeBytes,
    contentType: fileMeta.contentType ?? info.contentType,
    originalFilename: fileMeta.originalFilename ?? info.originalFilename,
    hasPreview: fileMeta.hasPreview ?? info.hasPreview,
  };
}

function finishImageDownloadChoice(v) {
  if (typeof imageDownloadChoiceResolve === "function") {
    const r = imageDownloadChoiceResolve;
    imageDownloadChoiceResolve = null;
    r(v);
  }
  if (imageDownloadChoiceEscHandler) {
    document.removeEventListener("keydown", imageDownloadChoiceEscHandler);
    imageDownloadChoiceEscHandler = null;
  }
  document.getElementById("modalImageDownloadChoice")?.classList.add("hidden");
}

/**
 * @param {object} opts
 * @param {string} opts.filename
 * @param {number} opts.originalSizeBytes
 * @param {number} [opts.previewSizeBytes] 서버 미리보기가 있을 때
 * @param {boolean} [opts.hasServerPreview]
 */
function openImageDownloadChoice({ filename, originalSizeBytes, previewSizeBytes, hasServerPreview }) {
  return new Promise((resolve) => {
    imageDownloadChoiceResolve = resolve;
    const msgEl = document.getElementById("imageDownloadChoiceMessage");
    const fn = String(filename || "image");
    const origStr = fmtSizeOrUnknown(originalSizeBytes);
    const prevStr =
      hasServerPreview && previewSizeBytes != null && Number(previewSizeBytes) > 0
        ? fmtSize(Number(previewSizeBytes))
        : null;
    if (msgEl) {
      const noteHtml = hasServerPreview && prevStr
        ? `※ <strong>원본 파일</strong>은 서버에 저장된 풀 해상도입니다. <strong>압축본(미리보기)</strong>은 업로드 시 서버에 따로 저장된 JPEG입니다. 버튼 옆 용량은 각각의 저장 크기입니다.`
        : `※ 서버에 미리보기가 없는 예전 첨부입니다. <strong>압축본</strong>은 브라우저에서 원본을 받아 JPEG로 다시 인코딩합니다(용량은 실행 후 확인).`;
      msgEl.innerHTML = [
        `<div class="image-download-choice-file">`,
        `<span class="image-download-choice-k">파일명</span>`,
        `<span class="image-download-choice-v">${escHtml(fn)}</span>`,
        `</div>`,
        `<p class="image-download-choice-hint">받을 방식을 선택하세요.</p>`,
        `<p class="image-download-choice-note">${noteHtml}</p>`,
      ].join("");
    }
    const origSizeEl = document.getElementById("imageDownloadOriginalSize");
    const compSizeEl = document.getElementById("imageDownloadCompressedSize");
    if (origSizeEl) origSizeEl.textContent = origStr;
    if (compSizeEl) compSizeEl.textContent = prevStr || "JPEG 재인코딩(예상 감소)";
    imageDownloadChoiceEscHandler = (e) => {
      if (e.key === "Escape") closeModal("modalImageDownloadChoice");
    };
    document.addEventListener("keydown", imageDownloadChoiceEscHandler);
    openModal("modalImageDownloadChoice");
  });
}

async function downloadChannelFileCompressedAsJpeg(fileId, filename, channelId) {
  const ch = channelId != null ? channelId : activeChannelId;
  if (!ch || !currentUser) return;
  try {
    const res = await fetch(
      `${API_BASE}/api/channels/${ch}/files/${fileId}/download?employeeNo=${encodeURIComponent(currentUser.employeeNo)}&variant=original`,
      { headers: { Authorization: `Bearer ${getToken()}` } }
    );
    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      const msg = err.error?.message;
      await uiAlert(msg || "다운로드에 실패했습니다.");
      return;
    }
    const blob = await res.blob();
    let bmp;
    try {
      bmp = await createImageBitmap(blob);
    } catch {
      await uiAlert("이미지를 디코딩할 수 없습니다. 원본 다운로드를 이용해 주세요.");
      return;
    }
    const maxEdge = DOWNLOAD_COMPRESS_MAX_EDGE;
    const w = bmp.width;
    const h = bmp.height;
    const scale = Math.min(1, maxEdge / Math.max(w, h));
    const tw = Math.max(1, Math.round(w * scale));
    const th = Math.max(1, Math.round(h * scale));
    const canvas = document.createElement("canvas");
    canvas.width = tw;
    canvas.height = th;
    const ctx = canvas.getContext("2d");
    if (!ctx) {
      bmp.close();
      await uiAlert("이미지를 처리할 수 없습니다.");
      return;
    }
    ctx.drawImage(bmp, 0, 0, tw, th);
    bmp.close();
    const outBlob = await new Promise((resolve) => {
      canvas.toBlob((b) => resolve(b), "image/jpeg", DOWNLOAD_COMPRESS_JPEG_QUALITY);
    });
    if (!outBlob) {
      await uiAlert("압축본을 만들 수 없습니다.");
      return;
    }
    const base = String(filename || "image").replace(/\.[^.]+$/, "") || "image";
    const downloadName = `${base}.jpg`;
    const url = URL.createObjectURL(outBlob);
    const a = document.createElement("a");
    a.href = url;
    a.download = downloadName;
    a.click();
    URL.revokeObjectURL(url);
  } catch (e) {
    console.error(e);
    await uiAlert("압축본 저장 중 오류가 발생했습니다.");
  }
}

async function maybeDownloadChannelImageWithChoice(fileId, filename, channelId, meta = {}) {
  const ch = channelId != null ? channelId : activeChannelId;
  const fn = filename || meta.originalFilename || "download";
  let sizeBytes = Number(meta.sizeBytes) || 0;
  let previewSizeBytes = Number(meta.previewSizeBytes) || 0;
  let contentType = String(meta.contentType || "").trim();
  let info = null;
  if (ch && fileId) {
    info = await fetchChannelFileDownloadInfo(ch, fileId);
    if (info) {
      if (!sizeBytes) sizeBytes = Number(info.sizeBytes) || 0;
      if (!previewSizeBytes && info.previewSizeBytes != null) {
        previewSizeBytes = Number(info.previewSizeBytes) || 0;
      }
      if (!contentType) contentType = String(info.contentType || "").trim();
    }
  }
  const hasServerPreview =
    info?.hasPreview === true &&
    previewSizeBytes > 0 &&
    previewSizeBytes < sizeBytes;

  if (!isImageContentType(contentType, fn)) {
    return downloadChannelFile(fileId, fn, ch);
  }

  if (hasServerPreview && eligibleForCompressedImageDownload(contentType, fn)) {
    const choice = await openImageDownloadChoice({
      filename: fn,
      originalSizeBytes: sizeBytes,
      previewSizeBytes,
      hasServerPreview: true,
    });
    if (choice === null) return;
    if (choice === "original") return downloadChannelFile(fileId, fn, ch, "original");
    if (choice === "preview") return downloadChannelFile(fileId, fn, ch, "preview");
    return;
  }

  const eligibleLegacy =
    sizeBytes >= LARGE_IMAGE_DOWNLOAD_BYTES && eligibleForCompressedImageDownload(contentType, fn);
  if (eligibleLegacy) {
    const choice = await openImageDownloadChoice({
      filename: fn,
      originalSizeBytes: sizeBytes,
      previewSizeBytes: null,
      hasServerPreview: false,
    });
    if (choice === null) return;
    if (choice === "original") return downloadChannelFile(fileId, fn, ch, "original");
    if (choice === "preview") return downloadChannelFileCompressedAsJpeg(fileId, fn, ch);
    return;
  }

  return downloadChannelFile(fileId, fn, ch, "original");
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

/**
 * 조직 인원 목록 공통 정렬: 직책 "팀장" 최우선 → 직급(부장→차장→과장→대리→사원→인턴→기타) → 이름 가나다.
 * user-directory 응답(jobLevel/jobTitle)과 관리자 사용자 목록(jobLevelDisplayName/jobTitleDisplayName) 모두 지원.
 */
function sortOrgDirectoryMembers(users) {
  const JOB_LEVEL_ORDER = ["부장", "차장", "과장", "대리", "사원", "인턴"];
  const levelRank = (level) => {
    if (!level) return 999;
    const idx = JOB_LEVEL_ORDER.findIndex((l) => String(level).includes(l));
    return idx >= 0 ? idx : 998;
  };
  const jobLevelOf = (u) => u.jobLevel ?? u.jobLevelDisplayName ?? "";
  const jobTitleOf = (u) => u.jobTitle ?? u.jobTitleDisplayName ?? "";
  return [...users].sort((a, b) => {
    const aIsLeader = String(jobTitleOf(a)).includes("팀장") ? 0 : 1;
    const bIsLeader = String(jobTitleOf(b)).includes("팀장") ? 0 : 1;
    if (aIsLeader !== bIsLeader) return aIsLeader - bIsLeader;
    const rankDiff = levelRank(jobLevelOf(a)) - levelRank(jobLevelOf(b));
    if (rankDiff !== 0) return rankDiff;
    return String(a.name || "").localeCompare(String(b.name || ""), "ko-KR");
  });
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

  sortOrgDirectoryMembers(filtered).forEach((u) => {
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
  applyTheme("light", { persistLocal: false });
  const idEl = document.getElementById("loginId");
  const pwEl = document.getElementById("loginPassword");
  const rememberEl = document.getElementById("loginRememberId");
  if (idEl) {
    idEl.value = "";
    try {
      if (localStorage.getItem(LOGIN_REMEMBER_KEY) === "1") {
        idEl.value = localStorage.getItem(LOGIN_SAVED_ID_KEY) || "";
        if (rememberEl) rememberEl.checked = true;
      }
    } catch (e) {
      /* ignore */
    }
  }
  if (pwEl) pwEl.value = "";
  if (rememberEl && localStorage.getItem(LOGIN_REMEMBER_KEY) !== "1") rememberEl.checked = false;
  hideLoginError();
}

function showMain(user) {
  currentUser = user;
  lastWorkSidebarSig = null;
  lastSidebarChannelsSnapshot = [];
  loginPage.classList.add("hidden");
  mainApp.classList.remove("hidden");
  let preferredTheme = VALID_THEMES.includes(user?.themePreference)
    ? user.themePreference
    : (localStorage.getItem(THEME_KEY) || "light");
  if (preferredTheme === "blue") preferredTheme = "dark";
  applyTheme(VALID_THEMES.includes(preferredTheme) ? preferredTheme : "light");
  applySidebarCollapsedState();

  sidebarUserName.textContent = `${user.name}`;
  sidebarAvatar.textContent = avatarInitials(user.name);
  const appHeaderAvatarEl = document.getElementById("appHeaderAvatar");
  if (appHeaderAvatarEl) appHeaderAvatarEl.textContent = avatarInitials(user.name);
  const welcomeHeroTitleEl = document.getElementById("welcomeHeroTitle");
  if (welcomeHeroTitleEl) {
    const nm = String(user.name || "").trim();
    const first = nm ? nm.split(/\s+/)[0] : "";
    welcomeHeroTitleEl.textContent = first ? `안녕하세요, ${first}님!` : "환영합니다!";
  }

  /** 다른 계정 재로그인 시 이전 세션 프레즌스 캐시 제거 */
  presenceByEmployeeNo.clear();
  mentionInboxItems = loadMentionInboxFromStorage();
  renderMentionInboxList();

  // 로그인 계정이 바뀌어도 정확한 권한 상태를 반영하도록 항상 초기화 후 설정
  document.getElementById("adminSection").classList.add("hidden");
  if (user.role === "ADMIN") {
    document.getElementById("adminSection").classList.remove("hidden");
  }

  initSocket();
  loadMyChannels();
  initEvents();
  syncTopNavFromMainView();
}

/** Stratos-style top bar: highlight matches welcome vs. modal (work hub / org chart). */
function setTopNavActive(key) {
  const pairs = [
    ["dashboard", "btnTopNavDashboard"],
    ["projects", "btnTopNavProjects"],
    ["team", "btnTopNavTeam"],
  ];
  pairs.forEach(([k, bid]) => {
    const btn = document.getElementById(bid);
    if (!btn) return;
    if (k === key) btn.classList.add("app-shell-nav-link--active");
    else btn.classList.remove("app-shell-nav-link--active");
  });
}

function syncTopNavFromMainView() {
  const welcome = document.getElementById("viewWelcome");
  if (welcome && !welcome.classList.contains("hidden")) {
    setTopNavActive("dashboard");
  } else {
    ["btnTopNavDashboard", "btnTopNavProjects", "btnTopNavTeam"].forEach((bid) => {
      document.getElementById(bid)?.classList.remove("app-shell-nav-link--active");
    });
  }
}

function showView(viewId) {
  ["viewWelcome","viewChat","viewReleases","viewSettings","viewUserManagement","viewOrgManagement"].forEach(id => {
    const el = document.getElementById(id);
    if (el) el.classList.add("hidden");
  });
  const target = document.getElementById(viewId);
  if (target) target.classList.remove("hidden");
  syncTopNavFromMainView();
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
    try {
      const rememberEl = document.getElementById("loginRememberId");
      if (rememberEl?.checked) {
        localStorage.setItem(LOGIN_REMEMBER_KEY, "1");
        localStorage.setItem(LOGIN_SAVED_ID_KEY, loginId);
      } else {
        localStorage.removeItem(LOGIN_REMEMBER_KEY);
        localStorage.removeItem(LOGIN_SAVED_ID_KEY);
      }
    } catch (e) {
      /* ignore */
    }
    showMain(user);
  } catch {
    showLoginError("서버에 연결할 수 없습니다.");
  } finally {
    loginBtn.disabled  = false;
    loginBtn.textContent = "로그인";
  }
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
    const [channelRes, workSidebarRes] = await Promise.all([
      apiFetch(`/api/channels?employeeNo=${encodeURIComponent(currentUser.employeeNo)}`),
      apiFetch(
        `/api/work-items/sidebar/by-assigned-cards?employeeNo=${encodeURIComponent(currentUser.employeeNo)}&limit=30`
      ),
    ]);
    const channelJson = await channelRes.json().catch(() => ({}));
    const workSidebarJson = await workSidebarRes.json().catch(() => ({}));
    if (!channelRes.ok) return;
    const rawChannelRows = Array.isArray(channelJson.data) ? channelJson.data : [];
    const channels = normalizeSidebarChannels(rawChannelRows);
    joinAllChannelSocketRooms(rawChannelRows);
    mySidebarWorkItems = workSidebarRes.ok && Array.isArray(workSidebarJson.data) ? workSidebarJson.data : [];
    const nextWorkSig = JSON.stringify(
      (mySidebarWorkItems || []).map((r) => ({
        id: Number(r.workItemId ?? 0),
        iu: r.inUse !== false,
      }))
    );
    if (lastWorkSidebarSig !== null && lastWorkSidebarSig !== nextWorkSig) {
      pushActivityToast({
        title: "업무 항목 변경",
        locationLine: "내 업무 항목",
        preview: "담당 업무·칸반 목록이 갱신되었습니다.",
      });
    }
    lastWorkSidebarSig = nextWorkSig;
    lastSidebarChannelsSnapshot = channels;
    renderChannelList(channels);
  } catch (err) {
    console.error("채널 목록 로드 실패", err);
  }
}

function normalizeSidebarChannels(channels) {
  const byKey = new Map();
  const others = [];
  for (const ch of channels) {
    if (String(ch.channelType || "").toUpperCase() !== "DM") {
      others.push(ch);
      continue;
    }
    const label = String(ch.description || ch.name || "").trim().toLowerCase();
    const key = `${label}::${String(ch.workspaceKey || "")}`;
    if (!label) {
      others.push(ch);
      continue;
    }
    const prev = byKey.get(key);
    if (!prev) {
      byKey.set(key, ch);
      continue;
    }
    const prevScore = (Array.isArray(prev.dmPeerEmployeeNos) && prev.dmPeerEmployeeNos.length ? 10 : 0)
      + (Number(prev.memberCount || 0) > 1 ? 5 : 0)
      + (prev.lastMessageAt ? 1 : 0);
    const nextScore = (Array.isArray(ch.dmPeerEmployeeNos) && ch.dmPeerEmployeeNos.length ? 10 : 0)
      + (Number(ch.memberCount || 0) > 1 ? 5 : 0)
      + (ch.lastMessageAt ? 1 : 0);
    if (nextScore > prevScore) byKey.set(key, ch);
  }
  return [...others, ...Array.from(byKey.values())];
}

function scheduleRefreshMyChannels() {
  if (!currentUser) return;
  if (refreshChannelListTimer) clearTimeout(refreshChannelListTimer);
  refreshChannelListTimer = setTimeout(() => {
    refreshChannelListTimer = null;
    loadMyChannels();
  }, 400);
}

/** 채널 최신 루트까지 읽음(미읽음 배지). 대량 히스토리에서도 스크롤 없이 정리. */
async function markChannelReadCaughtUp(channelId) {
  if (!currentUser || channelId == null) return;
  try {
    const res = await apiFetch(`/api/channels/${channelId}/read-state/mark-latest-root`, {
      method: "POST",
      body: JSON.stringify({ employeeNo: currentUser.employeeNo }),
    });
    if (!res.ok) {
      const j = await res.json().catch(() => ({}));
      console.warn("read-state mark-latest-root 실패", res.status, j.error?.message || "");
    }
  } catch (e) {
    console.warn("read-state mark-latest-root 오류", e);
  }
}

function scheduleMarkChannelReadCaughtUp(channelId) {
  if (!channelId || !currentUser) return;
  if (markChannelCaughtUpTimer) clearTimeout(markChannelCaughtUpTimer);
  markChannelCaughtUpTimer = setTimeout(() => {
    markChannelCaughtUpTimer = null;
    markChannelReadCaughtUp(channelId).then(() => loadMyChannels());
  }, 320);
}

function clearChatReadAnchorUi() {
  if (!messagesEl) return;
  messagesEl.querySelectorAll(".msg-read-anchor-divider").forEach((el) => el.remove());
  messagesEl.querySelectorAll(".msg-read-anchor-highlight").forEach((el) => el.classList.remove("msg-read-anchor-highlight"));
}

/**
 * 채널의 현재 읽음 포인터(lastReadMessageId)를 백엔드에서 가져온다.
 * 이 값은 markChannelReadCaughtUp 호출 *전*에 조회해야 유효하다.
 */
async function fetchLastReadMessageId(channelId) {
  try {
    const empQ = encodeURIComponent(currentUser.employeeNo);
    const res = await apiFetch(`/api/channels/${channelId}/read-state?employeeNo=${empQ}`);
    if (!res.ok) return null;
    const json = await res.json().catch(() => ({}));
    const mid = Number(json?.data?.lastReadMessageId ?? json?.lastReadMessageId);
    return Number.isFinite(mid) && mid > 0 ? mid : null;
  } catch {
    return null;
  }
}

/**
 * firstMsgEl 이후 DOM에 이어지는 채팅 행 중, 현재 사용자가 아닌 발신자가 하나라도 있는지.
 * lastRead 조회가 읽음 갱신보다 앞서는 타이밍(첨부 직후 loadMessages 등)에서
 * 내 메시지 위에만 "새 메시지" 선이 뜨는 것을 막는다.
 */
function hasChatMessageFromOtherAfter(firstMsgEl, myEmp) {
  if (!firstMsgEl || !myEmp) return false;
  let el = firstMsgEl;
  while (el) {
    if (el.classList?.contains("msg-row") && el.classList?.contains("msg-chat")) {
      const sid = String(el.dataset.senderId || "").trim();
      if (sid && sid !== myEmp) return true;
    }
    el = el.nextElementSibling;
  }
  return false;
}

/**
 * lastReadMid 이후에 새 메시지가 있을 경우 "새 메시지" 구분선을 삽입하고
 * 첫 번째 새 메시지 앞에 위치시킨 뒤 그 구분선을 반환한다.
 * 삽입하지 않은 경우 null을 반환한다.
 */
function showNewMsgsDivider(lastReadMid) {
  if (!messagesEl || lastReadMid == null || !Number.isFinite(Number(lastReadMid))) return null;
  clearChatReadAnchorUi();

  const lastReadEl = findMessageRowElementByMessageId(Number(lastReadMid));
  if (!lastReadEl) return null;

  // lastReadEl 다음에 오는 첫 번째 루트 메시지 행(= 첫 번째 미읽음 메시지) 탐색
  let firstNewEl = lastReadEl.nextElementSibling;
  while (firstNewEl && !String(firstNewEl.id || "").startsWith("msg-")) {
    firstNewEl = firstNewEl.nextElementSibling;
  }
  if (!firstNewEl) return null; // 새 메시지 없음(이미 최신까지 읽은 상태)

  const myEmp = String(currentUser?.employeeNo || "").trim();
  if (myEmp && !hasChatMessageFromOtherAfter(firstNewEl, myEmp)) {
    return null;
  }

  const div = document.createElement("div");
  div.className = "msg-system msg-read-anchor-divider";
  div.dataset.anchorMessageId = String(lastReadMid);
  div.textContent = "새 메시지";
  messagesEl.insertBefore(div, firstNewEl);
  firstNewEl.classList.add("msg-read-anchor-highlight");
  return div;
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

function quickRailPinsStorageKey() {
  const emp = String(currentUser?.employeeNo || "").trim();
  return emp ? `ech_quick_rail_pinned_${emp}` : null;
}

function readQuickRailPinnedChannelIds() {
  const key = quickRailPinsStorageKey();
  if (!key) return [];
  try {
    const raw = localStorage.getItem(key);
    const arr = raw ? JSON.parse(raw) : [];
    if (!Array.isArray(arr)) return [];
    return arr.map(Number).filter((x) => Number.isFinite(x) && x > 0);
  } catch {
    return [];
  }
}

function saveQuickRailPinnedChannelIds(ids) {
  const key = quickRailPinsStorageKey();
  if (!key) return;
  try {
    localStorage.setItem(key, JSON.stringify(ids));
  } catch {
    /* ignore */
  }
}

function isQuickRailChannelPinned(cid) {
  return readQuickRailPinnedChannelIds().includes(Number(cid));
}

function toggleQuickRailChannelPin(cid) {
  const id = Number(cid);
  if (!Number.isFinite(id) || id <= 0) return;
  const order = readQuickRailPinnedChannelIds();
  const idx = order.indexOf(id);
  if (idx >= 0) {
    saveQuickRailPinnedChannelIds(order.filter((x) => x !== id));
  } else {
    order.push(id);
    saveQuickRailPinnedChannelIds(order);
  }
}

/**
 * 퀵 레일(`#quickRailScroll`): 워크스페이스 아래·검색~목록과 같은 세로 구간.
 * 우클릭으로 고정한 채널은 순서가 유지되고, 나머지는 미읽음·최근 순(최대 `QUICK_RAIL_MAX_ITEMS`).
 */
function renderQuickUnreadList(channels) {
  const el = document.getElementById("quickRailScroll");
  if (!el) return;
  el.innerHTML = "";
  const list = [...(channels || [])];
  const byId = new Map(list.map((ch) => [Number(ch.channelId), ch]));
  const pinIds = readQuickRailPinnedChannelIds().filter((id) => byId.has(id));
  const pinnedChannels = pinIds.map((id) => byId.get(id)).filter(Boolean);
  const pinSet = new Set(pinIds);
  const unpinned = list.filter((ch) => !pinSet.has(Number(ch.channelId)));
  unpinned.sort(compareQuickRailChannel);
  const merged = [...pinnedChannels, ...unpinned];
  const picked = merged.slice(0, QUICK_RAIL_MAX_ITEMS);
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
    if (isQuickRailChannelPinned(ch.channelId)) btn.classList.add("quick-rail-link-pinned");
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
    const muteRailHtml = quickRailNotifyMutedHtml(ch.channelId);
    const leadHtml =
      ch.channelType === "DM"
        ? `<span class="quick-rail-dm-lead">${dmSidebarLeadingHtml(ch.dmPeerEmployeeNos)}</span>`
        : `<span class="quick-rail-icon">${ch.channelType === "PRIVATE" ? "🔒" : "#"}</span>`;
    btn.innerHTML = `${leadHtml}<span class="quick-rail-label">${escHtml(cap)}</span>${muteRailHtml}${badgeHtml}`;
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

    const muteHtml = notifyMutedIconHtml(ch.channelId);
    if (ch.channelType === "DM") {
      const dmLead = dmSidebarLeadingHtml(ch.dmPeerEmployeeNos);
      li.innerHTML = `${dmLead}<span class="item-label">${escHtml(displayName)}</span>${muteHtml}${badgeHtml}`;
      dmListEl.appendChild(li);
    } else {
      const icon = ch.channelType === "PRIVATE" ? "🔒" : "#";
      li.innerHTML = `<span class="item-icon">${icon}</span><span class="item-label">${escHtml(ch.name)}</span>${muteHtml}${badgeHtml}`;
      channelListEl.appendChild(li);
    }

    if (ch.channelId === activeChannelId) li.classList.add("active");

    li.addEventListener("click", () => selectChannel(ch.channelId, displayName, ch.channelType));
  });

  renderQuickUnreadList(channels);
  renderMyWorkItemsSidebar(channels);
  refreshPresenceDots();
}

/**
 * 사이드바·업무 목록에 보여줄 채널 라벨(DM은 표시용 description 우선, API가 내부명만 줄 때 보정).
 */
function displayChannelLabelForWorkSidebar(channels, channelId, apiChannelName) {
  const cid = Number(channelId);
  if (Number.isFinite(cid) && cid > 0 && Array.isArray(channels)) {
    const ch = channels.find((c) => Number(c.channelId) === cid);
    if (ch) {
      const ct = String(ch.channelType || "").toUpperCase();
      if (ct === "DM") {
        const d = String(ch.description || "").trim();
        if (d) return d;
      }
      const n = String(ch.name || "").trim();
      if (n) return n;
    }
  }
  const raw = String(apiChannelName || "").trim();
  if (raw && !/^_/u.test(raw)) return raw;
  return "다이렉트 메시지";
}

function renderMyWorkItemsSidebar(channels) {
  if (!myKanbanListEl) return;
  myKanbanListEl.innerHTML = "";
  if (!Array.isArray(mySidebarWorkItems) || !mySidebarWorkItems.length) {
    myKanbanListEl.innerHTML = `<li class="sidebar-item sidebar-item-empty">담당 세부업무가 있는 업무 없음</li>`;
    return;
  }
  const channelTypeById = new Map((channels || []).map((ch) => [Number(ch.channelId), String(ch.channelType || "PUBLIC")]));
  mySidebarWorkItems.forEach((row) => {
    const li = document.createElement("li");
    const inactive = row.inUse === false;
    const channelLabel = displayChannelLabelForWorkSidebar(channels, row.channelId, row.channelName);
    li.className = inactive
      ? "sidebar-item assigned-kanban-item sidebar-work-item-inactive"
      : "sidebar-item assigned-kanban-item";
    li.dataset.workItemId = String(Number(row.workItemId ?? 0));
    li.dataset.channelId = String(Number(row.channelId ?? 0));
    li.dataset.channelType = channelTypeById.get(Number(row.channelId ?? 0)) || "PUBLIC";
    li.dataset.channelName = channelLabel;
    li.title = `${String(row.title || "(제목 없음)")} · ${channelLabel}`;
    li.innerHTML = `<span class="item-icon">📋</span><span class="item-label">${escHtml(String(row.title || "(제목 없음)"))}</span>
      <span class="assigned-kanban-meta">${escHtml(channelLabel)}</span>`;
    li.addEventListener("click", async () => {
      const cid = Number(row.channelId ?? 0);
      const wid = Number(row.workItemId ?? 0);
      if (!cid || !wid) return;
      workHubScopedChannelId = cid;
      workHubSelectedListWorkItemKey = String(wid);
      clearWorkHubPendingMaps();
      clearPendingNewKanbanAssignees();
      try {
        await Promise.all([loadWorkHubChannelMembersForAssignee(), loadChannelWorkItems(), loadChannelKanbanBoard()]);
        ensureWorkHubWorkListDeleteBound();
        openModal("modalWorkHub");
        setTimeout(() => {
          applyWorkHubWorkListSelection();
          const rowEl = document.querySelector(`#channelWorkItemsList .channel-work-item[data-work-item-id="${wid}"]`);
          if (!rowEl) return;
          rowEl.scrollIntoView({ behavior: "smooth", block: "center" });
          rowEl.classList.add("work-item-row-highlight");
          setTimeout(() => rowEl.classList.remove("work-item-row-highlight"), 2200);
        }, 120);
      } catch (e) {
        clearWorkHubScopedChannel();
        await uiAlert(e?.message || "업무/칸반 정보를 불러오지 못했습니다.");
      }
    });
    myKanbanListEl.appendChild(li);
  });
}

function saveComposerDraftForChannel(channelId) {
  if (channelId == null || !messageInputEl) return;
  const cid = String(channelId);
  composerDraftByChannelId.set(cid, {
    text: messageInputEl.value,
    replyTargetMessageId: replyComposerTargetMessageId,
    pendingFiles: pendingFilesQueue.slice(),
  });
}

function restoreComposerDraftForChannel(channelId) {
  if (!messageInputEl || channelId == null) return;
  const cid = String(channelId);
  const d = composerDraftByChannelId.get(cid);
  messageInputEl.value = d?.text ?? "";
  if (messageInputEl.value.trim()) {
    clearChatReadAnchorUi();
  }
  mentionDisplayToEmployeeNo.clear();
  if (d?.replyTargetMessageId != null && Number.isFinite(Number(d.replyTargetMessageId))) {
    setReplyComposerTarget(Number(d.replyTargetMessageId));
  } else {
    clearReplyComposerTarget();
  }
  if (d?.pendingFiles?.length) {
    setComposerPendingFiles(d.pendingFiles);
  } else {
    clearFilePreview();
  }
  scheduleComposerInputHeight();
}

function scheduleComposerInputHeight() {
  if (!messageInputEl || messageInputEl.tagName !== "TEXTAREA") return;
  requestAnimationFrame(() => {
    if (!messageInputEl || messageInputEl.tagName !== "TEXTAREA") return;
    messageInputEl.style.height = "auto";
    const max = 200;
    messageInputEl.style.height = `${Math.min(messageInputEl.scrollHeight, max)}px`;
  });
}

function scheduleThreadComposerInputHeight() {
  const el = threadMessageInputEl;
  if (!el || el.tagName !== "TEXTAREA") return;
  requestAnimationFrame(() => {
    if (!threadMessageInputEl || threadMessageInputEl.tagName !== "TEXTAREA") return;
    threadMessageInputEl.style.height = "auto";
    const max = 200;
    threadMessageInputEl.style.height = `${Math.min(threadMessageInputEl.scrollHeight, max)}px`;
  });
}

/* ==========================================================================
 * 채널 선택 / 메시지 로드
 * ========================================================================== */
async function selectChannel(channelId, channelName, channelType, options = {}) {
  closeModal("modalImagePreview");
  const prevChannelId = activeChannelId;
  const switchingChannel =
    prevChannelId == null || Number(prevChannelId) !== Number(channelId);
  if (switchingChannel && prevChannelId != null) {
    saveComposerDraftForChannel(prevChannelId);
    clearFilePreview();
  }
  activeChannelId   = channelId;
  activeChannelType = channelType;
  activeChannelCreatorEmployeeNo = null;
  activeChannelMemberCount = 0;
  activeChannelMemberMentionList = [];

  if (switchingChannel) {
    messageInputEl.value = "";
    clearReplyComposerTarget();
    mentionDisplayToEmployeeNo.clear();
    if (prevChannelId == null) {
      clearFilePreview();
    }
  }

  // 사이드바 active 표시
  document.querySelectorAll(".channel-item").forEach(el => {
    el.classList.toggle("active", Number(el.dataset.channelId) === channelId);
  });

  // 헤더 업데이트 (DM은 멤버 로드 후 `updateChatHeaderDmPresence`로 프레즌스 점 표시)
  activeDmPeerEmployeeNos = [];
  const prefixEl = document.getElementById("chatChannelPrefix");
  if (prefixEl) {
    if (String(channelType || "").toUpperCase() === "DM") {
      prefixEl.className = "chat-channel-prefix chat-header-dm-prefix";
      prefixEl.innerHTML = "";
    } else {
      prefixEl.className = "chat-channel-prefix";
      const prefix = channelType === "PRIVATE" ? "🔒" : "#";
      prefixEl.textContent = prefix;
    }
  }
  document.getElementById("chatChannelName").textContent   = channelName;
  document.getElementById("chatMemberCount").textContent   = "";
  document.getElementById("chatHeaderMeta")?.classList.add("hidden");
  document.getElementById("memberPanel").classList.add("hidden");
  document.getElementById("memberList").innerHTML = "";
  document.getElementById("btnAddMembersLater")?.classList.add("hidden");
  document.getElementById("btnRenameChannel")?.classList.add("hidden");
  document.getElementById("btnRenameDm")?.classList.add("hidden");
  document.getElementById("btnCloseChannel")?.classList.add("hidden");
  document.getElementById("btnLeaveChannel")?.classList.remove("hidden");

  showView("viewChat");
  messagesEl.innerHTML = "";
  appendSystemMsg("메시지를 불러오는 중...");

  // 소켓 채널 입장
  joinSocketChannel(channelId);

  // 메시지/멤버를 함께 로드하고, 멤버 조직 캐시는 멤버 로드 완료 시점에 화면에 동기화한다.
  const membersPromise = loadChannelMembers(channelId);

  // 메시지 내역 로드
  await loadMessages(channelId);

  // 멤버 목록/조직 정보 로드 완료 대기(조직/직급 라벨 들쭉날쭉 방지)
  await membersPromise;

  const targetMid = options?.targetMessageId;
  if (targetMid != null && Number.isFinite(Number(targetMid))) {
    focusMessageByIdInCurrentTimeline(Number(targetMid));
  }

  refreshChannelFileHubData(channelId);

  if (switchingChannel) {
    restoreComposerDraftForChannel(channelId);
  }
  messageInputEl.focus();
  syncHeaderNotifyButton();
}

/** 단일 행 `id` 또는 이미지 묶음 행 내 `data-message-id`로 메시지 행 탐색 */
function findMessageRowElementByMessageId(messageId) {
  const id = Number(messageId);
  if (!Number.isFinite(id) || !messagesEl) return null;
  const byId = document.getElementById(`msg-${id}`);
  if (byId && byId.classList?.contains("msg-row")) return byId;
  const hit = messagesEl.querySelector(`.msg-attach-group-item[data-message-id="${id}"]`);
  if (hit) return hit.closest(".msg-row.msg-chat");
  const rowMatch = messagesEl.querySelector(`.msg-row.msg-chat[data-message-id="${id}"]`);
  if (rowMatch) return rowMatch;
  const grouped = messagesEl.querySelectorAll(".msg-row.msg-attachment-group");
  for (const r of grouped) {
    const raw = String(r.dataset.attachmentGroupIds || r.dataset.imageGroupIds || "");
    if (!raw) continue;
    const parts = raw.split(",").map((s) => Number(String(s).trim()));
    if (parts.some((n) => n === id)) return r;
  }
  return null;
}

function focusMessageByIdInCurrentTimeline(messageId) {
  if (!messagesEl || !Number.isFinite(Number(messageId))) return;
  const el = findMessageRowElementByMessageId(Number(messageId));
  if (!el) return;
  el.scrollIntoView({ behavior: "smooth", block: "center" });
  el.classList.add("msg-mention-focus");
  setTimeout(() => el.classList.remove("msg-mention-focus"), 1400);
}

function syncSenderOrgLabelsInMessageList() {
  if (!messagesEl) return;
  const rows = messagesEl.querySelectorAll(".msg-row.msg-chat[data-sender-id]");
  rows.forEach((row) => {
    const emp = String(row.dataset.senderId || "").trim();
    if (!emp) return;
    const block = row.querySelector(".msg-sender-block");
    if (!block) return;
    const orgLine = String(activeChannelMemberOrgLineByEmployeeNo.get(emp) || "").trim();
    let sub = block.querySelector(".msg-sender-sub");
    if (!orgLine) {
      if (sub) sub.remove();
      return;
    }
    if (!sub) {
      sub = document.createElement("span");
      sub.className = "msg-sender-sub";
      block.appendChild(sub);
    }
    sub.textContent = orgLine;
  });
}

async function loadMessages(channelId, { preserveScroll = false, skipNewMsgsDivider = false } = {}) {
  if (!currentUser) return;
  chatTimelineHasMoreOlder = false;
  chatTimelineLoadingOlder = false;
  try {
    if (preserveScroll && messagesEl) {
      const prevH = messagesEl.scrollHeight || 1;
      const prevTop = messagesEl.scrollTop || 0;
      pendingScrollRestoreRatio = prevH > 0 ? prevTop / prevH : 0;
    } else {
      pendingScrollRestoreRatio = null;
    }

    const empQ = encodeURIComponent(currentUser.employeeNo);
    const timelineUrl = `/api/channels/${channelId}/messages/timeline?employeeNo=${empQ}&limit=${TIMELINE_PAGE_LIMIT}`;
    const legacyUrl = `/api/channels/${channelId}/messages?employeeNo=${empQ}&limit=50`;

    // read-state 조회는 메시지 로드와 병렬로 실행 — markChannelReadCaughtUp 전에 해야 유효하다.
    const readStateProm = !preserveScroll ? fetchLastReadMessageId(channelId) : Promise.resolve(null);

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
      skipAutoScrollToBottomOnce = false;
      appendSystemMsg("메시지 로드 실패: " + (json.error?.message || ""));
      return;
    }

    // 백엔드 read-state가 있으면 자동 하단 스크롤 억제(새 메시지 구분선 위치로 스크롤)
    const lastReadMidFromServer = await readStateProm;
    skipAutoScrollToBottomOnce = !preserveScroll && lastReadMidFromServer != null;

    let msgs = [];
    if (usedTimeline) {
      const page = parseTimelinePagePayload(json);
      msgs = page.items;
      chatTimelineHasMoreOlder = page.hasMoreOlder;
    } else {
      msgs = json.data || [];
    }
    timelineRootMessageById.clear();

    if (usedTimeline) {
      msgs.forEach((m) => {
        if (!m || m.isReply) return;
        const mid = m.messageId ?? m.message_id;
        if (mid != null) timelineRootMessageById.set(Number(mid), m);
      });
      if (msgs.length === 0) {
        skipAutoScrollToBottomOnce = false;
        appendSystemMsg("아직 메시지가 없습니다. 첫 메시지를 보내보세요! 👋");
      } else {
        renderTimelineMessages(msgs);
      }
      ensureMessagesScrollLoadOlder();
    } else {
      msgs.forEach((m) => {
        if (!m) return;
        const pid = m.parentMessageId ?? m.parent_message_id;
        if (pid != null && pid !== "") return;
        const mid = m.messageId ?? m.message_id;
        if (mid != null) timelineRootMessageById.set(Number(mid), m);
      });
      if (msgs.length === 0) {
        skipAutoScrollToBottomOnce = false;
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

    if (!preserveScroll && msgs.length > 0) {
      if (!skipNewMsgsDivider) {
        showNewMsgsDivider(lastReadMidFromServer);
      }
      requestAnimationFrame(() => {
        if (messagesEl) messagesEl.scrollTop = messagesEl.scrollHeight;
      });
    }

    // 앵커/구분선 표시가 끝난 뒤 읽음 처리 — 이 전에 처리하면 저장 포인터가 최신으로 바뀐다.
    await markChannelReadCaughtUp(channelId);
    await loadMyChannels();
  } catch (err) {
    appendSystemMsg("메시지 로드 중 오류 발생");
    console.error(err);
  } finally {
    pendingScrollRestoreRatio = null;
    skipAutoScrollToBottomOnce = false;
  }
}

function syncChannelActionButtons() {
  const myEmp = String(currentUser?.employeeNo || "").trim();
  const creatorEmp = String(activeChannelCreatorEmployeeNo || "").trim();
  const isCreator = myEmp !== "" && creatorEmp !== "" && myEmp === creatorEmp;
  const isDm = String(activeChannelType || "").toUpperCase() === "DM";
  document.getElementById("btnLeaveChannel")?.classList.toggle("hidden", !activeChannelId);
  document.getElementById("btnRenameDm")?.classList.toggle("hidden", !(isDm && activeChannelMemberCount > 2));
  document.getElementById("btnRenameChannel")?.classList.toggle("hidden", isDm || !isCreator);
  document.getElementById("btnCloseChannel")?.classList.toggle("hidden", isDm || !isCreator);
}

async function loadChannelMembers(channelId) {
  try {
    const res  = await apiFetch(`/api/channels/${channelId}`);
    const json = await res.json();
    if (Number(channelId) !== Number(activeChannelId)) return;
    if (!res.ok) {
      activeDmPeerEmployeeNos = [];
      updateChatHeaderDmPresence();
      return;
    }
    const creatorEmp = String(json.data?.createdByEmployeeNo || "").trim();
    activeChannelCreatorEmployeeNo = creatorEmp || null;
    const members = json.data?.members || [];
    activeChannelMemberCount = members.length;
    syncChannelActionButtons();
    activeChannelMemberOrgLineByEmployeeNo.clear();
    const countLine = document.getElementById("chatMemberCount");
    const metaEl = document.getElementById("chatHeaderMeta");
    if (countLine && metaEl) {
      if (members.length > 0) {
        countLine.textContent = `팀원 ${members.length}명`;
        metaEl.classList.remove("hidden");
      } else {
        countLine.textContent = "";
        metaEl.classList.add("hidden");
      }
    }

    const myEmpMention = currentUser ? String(currentUser.employeeNo || "").trim() : "";
    activeChannelMemberMentionList = members
      .map((m) => ({
        employeeNo: String(m.employeeNo || "").trim(),
        name: String(m.name || "").trim(),
      }))
      .filter((m) => m.employeeNo && m.employeeNo !== myEmpMention);

    if (String(activeChannelType || "").toUpperCase() === "DM") {
      activeDmPeerEmployeeNos = members
        .map((m) => String(m.employeeNo || "").trim())
        .filter((emp) => emp && emp !== myEmpMention);
    } else {
      activeDmPeerEmployeeNos = [];
    }
    updateChatHeaderDmPresence();

    const listEl = document.getElementById("memberList");
    listEl.innerHTML = "";
    const myEmp = currentUser ? String(currentUser.employeeNo || "").trim() : "";
    const canKickOthers = myEmp !== "" && creatorEmp !== "" && myEmp === creatorEmp;
    const canAddMembers = String(activeChannelType || "").toUpperCase() === "DM" || canKickOthers;
    const addBtn = document.getElementById("btnAddMembersLater");
    if (addBtn) addBtn.classList.toggle("hidden", !canAddMembers);

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
      let orgLine = deptParts.length ? deptParts.map(x => String(x).trim()).join(" · ") : "조직 미지정";
      if (emp) {
        activeChannelMemberOrgLineByEmployeeNo.set(emp, orgLine);
      }
      const jobPosition = normalizeOrgValueForDisplay(m.jobPosition);
      const jobTitle = normalizeOrgValueForDisplay(m.jobTitle);
      const posHtml =
        jobPosition
          ? `<span class="member-position-txt">${escHtml(jobPosition)}</span>`
          : "";
      const dutyHtml =
        jobTitle
          ? `<span class="member-duty-txt">${escHtml(jobTitle)}</span>`
          : "";
      const showKick = canKickOthers && emp !== "" && emp !== creatorEmp;
      const ownerBadgeHtml = emp !== "" && emp === creatorEmp
        ? `<span class="member-role-badge owner">관리자</span>`
        : "";
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
        <div class="member-meta-wrap">
          <button type="button" class="member-profile-btn" data-employee-no="${escHtml(emp)}">
            <span class="member-name-wrap">
              <span class="member-name-txt">${escHtml(m.name || "알 수 없음")}</span>
              ${ownerBadgeHtml}
            </span>
          </button>
          <div class="member-org-line">${escHtml(orgLine)}</div>
          ${posHtml}
          ${dutyHtml}
        </div>
        ${kickBtnHtml}`;
      li.querySelector(".member-profile-btn").addEventListener("click", () => openUserProfile(emp));
      const kickBtn = li.querySelector(".btn-member-kick");
      if (kickBtn) {
        kickBtn.addEventListener("click", (ev) => {
          ev.stopPropagation();
          removeChannelMemberFromPanel(kickBtn.dataset.kickEmp, kickBtn.dataset.kickName || emp);
        });
      }
      li.addEventListener("contextmenu", (ev) => {
        const isDm = String(activeChannelType || "").toUpperCase() === "DM";
        if (isDm || !canKickOthers || !emp || emp === creatorEmp || emp === myEmp) return;
        ev.preventDefault();
        memberContextSelectedEmployeeNo = emp;
        if (!memberContextMenuEl) return;
        memberContextMenuEl.style.left = `${ev.clientX}px`;
        memberContextMenuEl.style.top = `${ev.clientY}px`;
        memberContextMenuEl.classList.remove("hidden");
      });
      listEl.appendChild(li);
    });
    // 메시지를 먼저 렌더한 경우에도 발신자 조직/직급 라벨을 즉시 동기화한다.
    syncSenderOrgLabelsInMessageList();
    refreshPresenceDots();
  } catch (err) {
    console.error("멤버 로드 실패", err);
  }
}

async function removeChannelMemberFromPanel(targetEmp, displayName) {
  if (!activeChannelId || !targetEmp) return;
  const label = displayName && String(displayName).trim() ? String(displayName).trim() : targetEmp;
  if (!(await uiConfirm(`「${label}」님을 이 채널에서 내보낼까요?`))) return;
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
  let row = document.querySelector(`.msg-row.msg-chat[data-message-id="${id}"]`);
  if (!row && messagesEl) {
    const hit = messagesEl.querySelector(`.msg-attach-group-item[data-message-id="${id}"]`);
    row = hit?.closest(".msg-row.msg-chat") || null;
  }
  let senderLabel = "메시지";
  let snippet = "";
  if (row) {
    const sBtn = row.querySelector(".msg-sender");
    if (sBtn && sBtn.textContent) senderLabel = String(sBtn.textContent).trim() || senderLabel;
    const tEl = row.querySelector(".msg-text");
    if (tEl) {
      const raw = typeof tEl.innerText === "string" ? tEl.innerText : tEl.textContent;
      if (raw) snippet = String(raw).replace(/\s+/g, " ").trim().slice(0, 160);
    }
    if (!snippet) {
      const fn = row.querySelector(".msg-attach-name");
      if (fn && fn.textContent) snippet = "📎 " + String(fn.textContent).trim();
    }
    if (!snippet && row.classList.contains("msg-attachment-group")) {
      const nameEl = row.querySelector(`.msg-attach-group-item[data-message-id="${id}"] .msg-attach-name`);
      if (nameEl && nameEl.textContent) snippet = "📎 " + String(nameEl.textContent).trim();
    }
    if (!snippet) {
      const tBtn = row.querySelector(`.msg-attach-group-item[data-message-id="${id}"] .msg-attach-image-thumb-btn`);
      if (tBtn && tBtn.title) snippet = "📎 " + String(tBtn.title).trim();
    }
    if (!snippet) {
      const tBtn = row.querySelector(".msg-attach-image-thumb-btn");
      if (tBtn && tBtn.title) snippet = "📎 " + String(tBtn.title).trim();
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
  if (dateKeyLocal(next.createdAt) !== dateKeyLocal(cur.createdAt)) return true;
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
  if (dateKeyLocal(prev.createdAt) !== dateKeyLocal(cur.createdAt)) return true;
  if (String(prev.senderId) !== String(cur.senderId)) return true;
  if (tryParseFilePayload(prev)) return true;
  return false;
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

function fmtSizeOrUnknown(n) {
  const v = Number(n) || 0;
  return v > 0 ? fmtSize(v) : "알 수 없음";
}

/** 썸네일·인라인: 서버 `/preview`(미리보기 있으면 그 파일, 없으면 원본). */
async function getAuthedPreviewBlobUrl(channelId, fileId) {
  const key = `${channelId}_${fileId}_thumb`;
  if (imageAttachmentBlobUrls.has(key)) {
    return imageAttachmentBlobUrls.get(key);
  }
  if (!currentUser) {
    throw new Error("Not signed in");
  }
  const res = await fetch(
    `${API_BASE}/api/channels/${channelId}/files/${fileId}/preview?employeeNo=${encodeURIComponent(currentUser.employeeNo)}`,
    { headers: { Authorization: `Bearer ${getToken()}` } }
  );
  if (!res.ok) {
    throw new Error("Image fetch failed");
  }
  const blob = await res.blob();
  const url = URL.createObjectURL(blob);
  imageAttachmentBlobUrls.set(key, url);
  trimImageBlobCacheIfNeeded();
  return url;
}

/** 라이트박스·클라이언트 재인코딩 소스: 항상 원본 스트림. */
async function getAuthedFullImageBlobUrl(channelId, fileId) {
  const key = `${channelId}_${fileId}_full`;
  if (imageAttachmentBlobUrls.has(key)) {
    return imageAttachmentBlobUrls.get(key);
  }
  if (!currentUser) {
    throw new Error("Not signed in");
  }
  const res = await fetch(
    `${API_BASE}/api/channels/${channelId}/files/${fileId}/download?employeeNo=${encodeURIComponent(currentUser.employeeNo)}&variant=original`,
    { headers: { Authorization: `Bearer ${getToken()}` } }
  );
  if (!res.ok) {
    throw new Error("Image fetch failed");
  }
  const blob = await res.blob();
  const url = URL.createObjectURL(blob);
  imageAttachmentBlobUrls.set(key, url);
  trimImageBlobCacheIfNeeded();
  return url;
}

function isImageFilePayload(payload) {
  if (!payload) return false;
  const ct = String(payload.contentType || "").trim().toLowerCase();
  if (ct.startsWith("image/")) return true;
  return INLINE_IMAGE_EXT.test(String(payload.originalFilename || ""));
}

function getParentMessageIdForMsg(msg) {
  if (!msg) return null;
  const v = msg.parentMessageId ?? msg.parent_message_id;
  if (v == null || v === "") return null;
  return String(v);
}

/** 타임라인에서 FILE 첨부 묶음으로 합칠 수 있는 루트 메시지(답글 행 제외) */
function isTimelineFileAttachmentRoot(msg) {
  if (!msg) return false;
  if (msg.isReply === true) return false;
  const mt = String(msg.messageType || msg.message_type || "").toUpperCase();
  if (mt.startsWith("REPLY")) return false;
  return !!tryParseFilePayload(msg);
}

function canMergeConsecutiveFileAttachments(a, b) {
  if (!a || !b) return false;
  if (!tryParseFilePayload(a) || !tryParseFilePayload(b)) return false;
  if (String(a.senderId ?? "") !== String(b.senderId ?? "")) return false;
  if (dateKeyLocal(a.createdAt) !== dateKeyLocal(b.createdAt)) return false;
  if (minuteKey(a.createdAt) !== minuteKey(b.createdAt)) return false;
  if (getParentMessageIdForMsg(a) !== getParentMessageIdForMsg(b)) return false;
  if (threadCommentCountFromMsg(a) > 0 || threadCommentCountFromMsg(b) > 0) return false;
  return true;
}

/**
 * 같은 분·같은 발신자의 연속 FILE 첨부 2건 이상이면 한 묶음으로 표시한다.
 * @returns {{ group: object[], endIdx: number } | null}
 */
function tryConsumeFileAttachmentGroup(msgs, startIdx) {
  const first = msgs[startIdx];
  if (!isTimelineFileAttachmentRoot(first)) return null;
  const group = [first];
  let j = startIdx + 1;
  while (j < msgs.length) {
    const next = msgs[j];
    if (!isTimelineFileAttachmentRoot(next)) break;
    if (!canMergeConsecutiveFileAttachments(group[group.length - 1], next)) break;
    group.push(next);
    j++;
  }
  if (group.length < 2) return null;
  return { group, endIdx: startIdx + group.length - 1 };
}

function openImageLightbox(blobUrl, fileId, filename, channelId, fileMeta = {}, opts = {}) {
  const showsPreview = opts.showsPreview === true;
  const img = document.getElementById("imagePreviewLarge");
  const cap = document.getElementById("imagePreviewCaption");
  const dl = document.getElementById("imagePreviewDownload");
  const loadOrig = document.getElementById("imagePreviewLoadOriginal");
  if (!img || !cap || !dl) return;
  img.src = blobUrl;
  img.alt = filename || "image";
  cap.textContent = filename || "";
  dl.onclick = () =>
    void maybeDownloadChannelImageWithChoice(fileId, filename, channelId, fileMeta);
  if (loadOrig) {
    if (showsPreview && channelId != null && fileId != null) {
      loadOrig.classList.remove("hidden");
      loadOrig.disabled = false;
      loadOrig.onclick = () => {
        void (async () => {
          loadOrig.disabled = true;
          try {
            const fullUrl = await getAuthedFullImageBlobUrl(channelId, fileId);
            img.src = fullUrl;
            loadOrig.classList.add("hidden");
          } catch {
            await uiAlert("원본을 불러올 수 없습니다.");
            loadOrig.disabled = false;
          }
        })();
      };
    } else {
      loadOrig.classList.add("hidden");
      loadOrig.onclick = null;
    }
  }
  openModal("modalImagePreview");
}

/**
 * 채널 첨부 이미지 크게 보기: 서버 미리보기가 있으면 라이트박스에 먼저 표시(원본은 「원본 보기」).
 */
async function openChannelImageLightbox(channelId, fileId, filename, fileMeta = {}) {
  const fid = Number(fileId);
  const cid = Number(channelId);
  if (!currentUser || !Number.isFinite(cid) || !Number.isFinite(fid)) return;
  let meta = { ...fileMeta, id: fid };
  meta = await enrichChannelFileMetaForLightbox(cid, meta);
  const fn = filename || meta.originalFilename || "image";
  let showPreview = hasServerPreviewForLightbox(meta);
  let url;
  if (showPreview) {
    try {
      url = await getAuthedPreviewBlobUrl(cid, fid);
    } catch {
      showPreview = false;
    }
  }
  if (!showPreview) {
    try {
      url = await getAuthedFullImageBlobUrl(cid, fid);
    } catch {
      await uiAlert("이미지를 불러올 수 없습니다.");
      return;
    }
  }
  openImageLightbox(url, fid, fn, cid, meta, { showsPreview: showPreview });
}

/** 비이미지 첨부: 파일명·크기 + 저장 / 저장 후 열기 (이미지는 그리드+라이트박스로 분리) */
function buildAttachmentCardHtml(fileMeta, { messageId, rootId }) {
  const fn = String(fileMeta.originalFilename || "파일");
  const icon = attachmentIconEmoji(fn, fileMeta.contentType);
  const sizeLabel = fmtSize(fileMeta.sizeBytes || 0);
  const mid = messageId != null ? String(messageId) : "";
  const rid = rootId != null ? String(rootId) : mid;
  return `
      <div class="msg-attach-group-item msg-attach-card" ${mid ? `data-message-id="${escHtml(mid)}"` : ""} ${rid ? `data-root-message-id="${escHtml(rid)}"` : ""}>
        <div class="msg-attach-card-head">
          <span class="msg-attach-card-icon" aria-hidden="true">${icon}</span>
          <span class="msg-attach-name">${escHtml(fn)}</span>
          <span class="msg-attach-meta">(${escHtml(sizeLabel)})</span>
        </div>
        <div class="msg-attach-card-actions">
          <button type="button" class="msg-attach-action" data-action="save">저장</button>
          <span class="msg-attach-action-sep" aria-hidden="true">|</span>
          <button type="button" class="msg-attach-action" data-action="save-open">저장 후 열기</button>
        </div>
      </div>`;
}

function wireAttachmentCardActions(cardEl, fileMeta, channelId) {
  if (!cardEl || !fileMeta) return;
  const uid = Number(fileMeta.id);
  if (!Number.isFinite(uid)) return;
  const fn = fileMeta.originalFilename || "download";
  const saveBtn = cardEl.querySelector('[data-action="save"]');
  const saveOpenBtn = cardEl.querySelector('[data-action="save-open"]');
  if (saveBtn) {
    saveBtn.addEventListener("click", () => {
      void maybeDownloadChannelImageWithChoice(uid, fn, channelId, fileMeta);
    });
  }
  if (saveOpenBtn) {
    saveOpenBtn.addEventListener("click", () => {
      void saveChannelFileAndOpenInNewTab(channelId, uid, fn, "original");
    });
  }
}

function partitionAttachmentMessages(msgs) {
  const images = [];
  const files = [];
  for (const m of msgs) {
    const p = tryParseFilePayload(m);
    if (!p) continue;
    const meta = {
      id: Number(p.fileId),
      originalFilename: p.originalFilename,
      sizeBytes: p.sizeBytes,
      previewSizeBytes: p.previewSizeBytes,
      contentType: p.contentType,
      hasPreview: p.hasPreview,
    };
    if (isImageFilePayload(p)) images.push({ msg: m, meta });
    else files.push({ msg: m, meta });
  }
  return { images, files };
}

function buildImageGridHtml(imageEntries) {
  return imageEntries
    .map(({ msg, meta }) => {
      const mid2 = msg.messageId ?? msg.message_id;
      const pid = msg.parentMessageId ?? msg.parent_message_id;
      const rootForItem =
        pid != null && pid !== "" && String(pid).trim() !== ""
          ? String(pid).trim()
          : mid2 != null
            ? String(mid2)
            : "";
      const fn = meta.originalFilename || "이미지";
      return `
      <div class="msg-attach-group-item msg-attach-image-cell" ${mid2 != null ? `data-message-id="${escHtml(String(mid2))}"` : ""} ${rootForItem ? `data-root-message-id="${escHtml(rootForItem)}"` : ""}>
        <button type="button" class="msg-attach-image-thumb-btn" title="${escHtml(fn)}" aria-label="이미지 크게 보기">
          <span class="msg-image-thumb-placeholder">불러오는 중…</span>
        </button>
      </div>`;
    })
    .join("");
}

function wireImageGridThumbs(scopeEl, imageEntries, channelId) {
  if (!scopeEl || !imageEntries.length) return;
  const cells = scopeEl.querySelectorAll(".msg-attach-image-cell");
  cells.forEach((cellEl, idx) => {
    const entry = imageEntries[idx];
    if (!entry?.meta || !Number.isFinite(entry.meta.id)) return;
    const { meta } = entry;
    const btn = cellEl.querySelector(".msg-attach-image-thumb-btn");
    const ph = cellEl.querySelector(".msg-image-thumb-placeholder");
    if (!btn || !ph) return;
    getAuthedPreviewBlobUrl(channelId, meta.id)
      .then((url) => {
        const img = document.createElement("img");
        img.className = "msg-attach-image-thumb-img";
        img.alt = meta.originalFilename || "";
        img.src = url;
        img.loading = "lazy";
        ph.replaceWith(img);
        btn.addEventListener("click", () => {
          void openChannelImageLightbox(channelId, meta.id, meta.originalFilename, meta);
        });
      })
      .catch(() => {
        ph.textContent = "불러오기 실패";
      });
  });
}

function createFileAttachmentRowFromMsg(msg, payload, { showAvatar, showTime }) {
  const emp = String(msg.senderId ?? "").trim();
  const isMine = isMessageFromMe(msg);
  const senderName = msg.senderName || (emp ? `emp#${emp}` : "?");
  const pr = presenceByEmployeeNo.get(emp) || "OFFLINE";
  const prCl = presenceCssClass(pr);
  const prTip = presenceTitle(pr);
  const channelId = Number(msg.channelId) || activeChannelId;
  const fileMeta = {
    id: Number(payload.fileId),
    originalFilename: payload.originalFilename,
    sizeBytes: payload.sizeBytes,
    previewSizeBytes: payload.previewSizeBytes,
    contentType: payload.contentType,
    hasPreview: payload.hasPreview,
    uploadedByEmployeeNo: emp,
    uploaderName: senderName,
  };
  const timeHtml = showTime
    ? `<span class="msg-time">${escHtml(fmtTime(msg.createdAt))}</span>`
    : "";
  const mid = msg.messageId ?? msg.message_id;
  const pid = msg.parentMessageId ?? msg.parent_message_id;
  const rootForCard = pid == null || pid === "" ? mid : pid;
  const isImg = isImageFilePayload(payload);
  let listBlock;
  if (isImg) {
    const imageEntries = [{ msg, meta: fileMeta }];
    const gridInner = buildImageGridHtml(imageEntries);
    listBlock = `
        <div class="msg-content-row msg-attachment-list-wrap">
          <div class="msg-attachment-image-bundle">
            <div class="msg-attachment-image-grid">${gridInner}</div>
          </div>
          <div class="msg-attachment-list-footer">${timeHtml}</div>
        </div>`;
  } else {
    const cardHtml = buildAttachmentCardHtml(fileMeta, { messageId: mid, rootId: rootForCard });
    listBlock = `
        <div class="msg-content-row msg-attachment-list-wrap">
          <div class="msg-attachment-list-inner">${cardHtml}</div>
          <div class="msg-attachment-list-footer">${timeHtml}</div>
        </div>`;
  }

  const div = document.createElement("div");
  div.className = `msg-row msg-chat ${isMine ? "msg-mine" : "msg-other"} msg-has-attachment${showAvatar ? "" : " msg-continued"}`;
  div.dataset.senderId = emp;
  div.dataset.minuteKey = minuteKey(msg.createdAt);
  div.dataset.dateKey = dateKeyLocal(msg.createdAt);
  if (mid != null) {
    div.dataset.messageId = String(mid);
    div.dataset.rootMessageId = pid == null ? String(mid) : String(pid);
  }

  const senderOrgLine = activeChannelMemberOrgLineByEmployeeNo.get(emp) || "";
  const senderOrgHtml = senderOrgLine
    ? `<span class="msg-sender-sub">${escHtml(senderOrgLine)}</span>`
    : "";

  if (isMine) {
    div.innerHTML = `
      <div class="msg-body msg-body--mine">
        ${listBlock}
      </div>`;
  } else if (showAvatar) {
    const initials = avatarInitials(senderName);
    div.innerHTML = `
      <div class="msg-avatar-wrap">
        <button type="button" class="msg-avatar msg-user-trigger" data-employee-no="${escHtml(emp)}" title="프로필 보기">${initials}</button>
        <span class="presence-dot ${prCl}" data-presence-user="${escHtml(emp)}" title="${prTip}"></span>
      </div>
      <div class="msg-body">
        <div class="msg-meta">
          <div class="msg-sender-block">
            <button type="button" class="msg-sender msg-user-trigger" data-employee-no="${escHtml(emp)}">${escHtml(senderName)}</button>
            ${senderOrgHtml}
          </div>
        </div>
        ${listBlock}
      </div>`;
  } else {
    div.innerHTML = `
      <div class="msg-spacer"></div>
      <div class="msg-body">
        ${listBlock}
      </div>`;
  }

  if (isImg) {
    const bundle = div.querySelector(".msg-attachment-image-bundle");
    wireImageGridThumbs(bundle, [{ msg, meta: fileMeta }], channelId);
  } else {
    const card = div.querySelector(".msg-attach-card");
    wireAttachmentCardActions(card, fileMeta, channelId);
  }
  attachThreadCommentFooter(div, msg);
  return div;
}

function createFileAttachmentGroupRowFromMsgs(msgs, { showAvatar, showTime }) {
  if (!msgs || msgs.length < 2) {
    const one = msgs && msgs[0];
    const p = one && tryParseFilePayload(one);
    if (one && p) return createFileAttachmentRowFromMsg(one, p, { showAvatar, showTime });
    return document.createElement("div");
  }
  const first = msgs[0];
  const last = msgs[msgs.length - 1];
  const channelId = Number(first.channelId) || activeChannelId;
  const emp = String(first.senderId ?? "").trim();
  const isMine = isMessageFromMe(first);
  const senderName = first.senderName || (emp ? `emp#${emp}` : "?");
  const pr = presenceByEmployeeNo.get(emp) || "OFFLINE";
  const prCl = presenceCssClass(pr);
  const prTip = presenceTitle(pr);
  const timeHtml = showTime
    ? `<span class="msg-time">${escHtml(fmtTime(last.createdAt))}</span>`
    : "";

  const fileMetas = msgs.map((m) => {
    const payload = tryParseFilePayload(m);
    return {
      id: Number(payload.fileId),
      originalFilename: payload.originalFilename,
      sizeBytes: payload.sizeBytes,
      previewSizeBytes: payload.previewSizeBytes,
      contentType: payload.contentType,
      hasPreview: payload.hasPreview,
    };
  });

  const { images: imageEntries, files: fileEntries } = partitionAttachmentMessages(msgs);
  const bodyParts = [];
  if (imageEntries.length) {
    bodyParts.push(
      `<div class="msg-attachment-image-bundle"><div class="msg-attachment-image-grid">${buildImageGridHtml(imageEntries)}</div></div>`
    );
  }
  if (fileEntries.length) {
    const fileCardsHtml = fileEntries
      .map(({ msg: m, meta }) => {
        const mid2 = m.messageId ?? m.message_id;
        const pid = m.parentMessageId ?? m.parent_message_id;
        const rootForCard = pid == null || pid === "" ? mid2 : pid;
        return buildAttachmentCardHtml(meta, { messageId: mid2, rootId: rootForCard });
      })
      .join("");
    bodyParts.push(`<div class="msg-attachment-group-cards">${fileCardsHtml}</div>`);
  }

  const stripHtml = `
        <div class="msg-content-row msg-attachment-list-wrap msg-attachment-group-wrap">
          ${bodyParts.join("")}
          <div class="msg-attachment-group-footer">
            <button type="button" class="btn-attach-batch-dl">일괄저장</button>
            ${timeHtml}
          </div>
        </div>`;

  const senderOrgLine = activeChannelMemberOrgLineByEmployeeNo.get(emp) || "";
  const senderOrgHtml = senderOrgLine
    ? `<span class="msg-sender-sub">${escHtml(senderOrgLine)}</span>`
    : "";

  const div = document.createElement("div");
  div.className = `msg-row msg-chat ${isMine ? "msg-mine" : "msg-other"} msg-has-attachment msg-attachment-group${showAvatar ? "" : " msg-continued"}`;
  div.dataset.senderId = emp;
  div.dataset.minuteKey = minuteKey(first.createdAt);
  div.dataset.dateKey = dateKeyLocal(first.createdAt);
  const midFirst = first.messageId ?? first.message_id;
  if (midFirst != null) {
    div.dataset.messageId = String(midFirst);
    const pid0 = first.parentMessageId ?? first.parent_message_id;
    div.dataset.rootMessageId = pid0 == null ? String(midFirst) : String(pid0);
  }
  const idList = [];
  for (const m of msgs) {
    const id = m.messageId ?? m.message_id;
    if (id != null) idList.push(String(id));
  }
  if (idList.length) div.dataset.attachmentGroupIds = idList.join(",");

  if (isMine) {
    div.innerHTML = `
      <div class="msg-body msg-body--mine">
        ${stripHtml}
      </div>`;
  } else if (showAvatar) {
    const initials = avatarInitials(senderName);
    div.innerHTML = `
      <div class="msg-avatar-wrap">
        <button type="button" class="msg-avatar msg-user-trigger" data-employee-no="${escHtml(emp)}" title="프로필 보기">${initials}</button>
        <span class="presence-dot ${prCl}" data-presence-user="${escHtml(emp)}" title="${prTip}"></span>
      </div>
      <div class="msg-body">
        <div class="msg-meta">
          <div class="msg-sender-block">
            <button type="button" class="msg-sender msg-user-trigger" data-employee-no="${escHtml(emp)}">${escHtml(senderName)}</button>
            ${senderOrgHtml}
          </div>
        </div>
        ${stripHtml}
      </div>`;
  } else {
    div.innerHTML = `
      <div class="msg-spacer"></div>
      <div class="msg-body">
        ${stripHtml}
      </div>`;
  }

  const batchBtn = div.querySelector(".btn-attach-batch-dl");
  if (batchBtn) {
    batchBtn.addEventListener("click", () => {
      void batchDownloadChannelImageFiles(channelId, fileMetas);
    });
  }

  const bundle = div.querySelector(".msg-attachment-image-bundle");
  if (bundle && imageEntries.length) {
    wireImageGridThumbs(bundle, imageEntries, channelId);
  }
  div.querySelectorAll(".msg-attachment-group-cards .msg-attach-card").forEach((card, idx) => {
    const meta = fileEntries[idx]?.meta;
    if (meta) wireAttachmentCardActions(card, meta, channelId);
  });

  attachThreadCommentFooter(div, first);
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
  const senderOrgLine = activeChannelMemberOrgLineByEmployeeNo.get(emp) || "";
  const senderOrgHtml = senderOrgLine
    ? `<span class="msg-sender-sub">${escHtml(senderOrgLine)}</span>`
    : "";
  const isMine = isMessageFromMe(msg);
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

  if (isMine) {
    div.innerHTML = `
      <div class="msg-body msg-body--mine">
        <div class="msg-content-row">
          <span class="msg-text">${formatMessageWithMentions(msg.text)}</span>${timeHtml}
        </div>
      </div>`;
  } else if (showAvatar) {
    const initials = avatarInitials(senderName);
    div.innerHTML = `
      <div class="msg-avatar-wrap">
        <button type="button" class="msg-avatar msg-user-trigger" data-employee-no="${escHtml(emp)}" title="프로필 보기">${initials}</button>
        <span class="presence-dot ${prCl}" data-presence-user="${escHtml(emp)}" title="${prTip}"></span>
      </div>
      <div class="msg-body">
        <div class="msg-meta">
          <div class="msg-sender-block">
            <button type="button" class="msg-sender msg-user-trigger" data-employee-no="${escHtml(emp)}">${escHtml(senderName)}</button>
            ${senderOrgHtml}
          </div>
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
  let i = 0;
  while (i < msgs.length) {
    const m = msgs[i];
    const dk = dateKeyLocal(m.createdAt);
    if (prevDk !== dk) {
      messagesEl.appendChild(createDateDividerElement(m.createdAt));
      prevDk = dk;
    }
    const group = tryConsumeFileAttachmentGroup(msgs, i);
    if (group) {
      const showAvatar = shouldShowAvatarForMessage(msgs, i);
      const showTime = shouldShowMessageTime(msgs, group.endIdx);
      messagesEl.appendChild(createFileAttachmentGroupRowFromMsgs(group.group, { showAvatar, showTime }));
      i = group.endIdx + 1;
    } else {
      const showAvatar = shouldShowAvatarForMessage(msgs, i);
      const showTime = shouldShowMessageTime(msgs, i);
      messagesEl.appendChild(createMessageRowElement(m, { showAvatar, showTime }));
      i++;
    }
  }
  trimMessages();
  refreshPresenceDots();
  // 댓글 전송 등에서 스크롤 위치를 유지해야 하는 경우(= loadMessages(preserveScroll=true))
  // render 단계에서 자동 하단 스크롤을 하지 않고 loadMessages에서 복원한다.
  if (pendingScrollRestoreRatio == null && !skipAutoScrollToBottomOnce) {
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
  let i = 0;
  while (i < items.length) {
    const m = items[i];
    const dk = dateKeyLocal(m.createdAt);
    if (prevDk !== dk) {
      messagesEl.appendChild(createDateDividerElement(m.createdAt));
      prevDk = dk;
    }
    const showAvatar = shouldShowAvatarForMessage(items, i);
    const showTime = shouldShowMessageTime(items, i);

    let row;
    if (m.isReply) {
      row = createReplyTimelineRowElement(m, { showAvatar, showTime });
      i++;
    } else {
      const group = tryConsumeFileAttachmentGroup(items, i);
      if (group) {
        const showTimeG = shouldShowMessageTime(items, group.endIdx);
        row = createFileAttachmentGroupRowFromMsgs(group.group, { showAvatar, showTime: showTimeG });
        i = group.endIdx + 1;
      } else {
        row = createMessageRowElement(m, { showAvatar, showTime });
        i++;
      }
    }
    const mid = row.dataset?.messageId
      ? Number(row.dataset.messageId)
      : m.messageId ?? m.message_id;
    if (mid != null && Number.isFinite(mid)) row.id = `msg-${mid}`;
    messagesEl.appendChild(row);
  }
  trimMessages();
  refreshPresenceDots();
  if (pendingScrollRestoreRatio == null && !skipAutoScrollToBottomOnce) {
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
function hideMemberContextMenu() {
  if (!memberContextMenuEl) return;
  memberContextMenuEl.classList.add("hidden");
  memberContextSelectedEmployeeNo = "";
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
  threadPendingSequence++;
  if (threadPendingFilePreviewUrl) {
    try {
      URL.revokeObjectURL(threadPendingFilePreviewUrl);
    } catch {
      /* ignore */
    }
  }
  threadPendingFilePreviewUrl = null;
  threadPendingFilesQueue = [];
  if (threadFilePreviewThumbEl) {
    threadFilePreviewThumbEl.classList.add("hidden");
    threadFilePreviewThumbEl.removeAttribute("src");
  }
  if (threadFilePreviewEl) threadFilePreviewEl.classList.add("hidden");
  if (threadFilePreviewNameEl) threadFilePreviewNameEl.textContent = "";
  setFilePreviewUploadStatus("thread", "");
}

function setThreadComposerPendingFile(file) {
  if (!file) return;
  setThreadComposerPendingFiles([file]);
}

function setThreadComposerPendingFiles(files) {
  const list = normalizePendingFileList(files);
  if (list.length === 0) return;
  threadPendingSequence++;
  const mySeq = threadPendingSequence;
  threadPendingFilesQueue = list;
  if (threadFilePreviewNameEl) threadFilePreviewNameEl.textContent = formatPendingFilesLabel(list);
  setFilePreviewUploadStatus("thread", "");
  const firstImage = list.find((f) => String(f.type || "").toLowerCase().startsWith("image/"));
  const displayFile = firstImage || list[0];
  const isImage = String(displayFile.type || "").toLowerCase().startsWith("image/");
  if (threadFilePreviewThumbEl && threadFilePreviewEl) {
    if (threadPendingFilePreviewUrl) {
      try {
        URL.revokeObjectURL(threadPendingFilePreviewUrl);
      } catch {
        /* ignore */
      }
    }
    threadPendingFilePreviewUrl = null;
    if (isImage) {
      threadFilePreviewThumbEl.classList.add("hidden");
      threadFilePreviewThumbEl.removeAttribute("src");
      void buildImagePreviewObjectUrl(displayFile).then((url) => {
        if (mySeq !== threadPendingSequence) {
          try {
            URL.revokeObjectURL(url);
          } catch {
            /* ignore */
          }
          return;
        }
        if (threadPendingFilePreviewUrl) {
          try {
            URL.revokeObjectURL(threadPendingFilePreviewUrl);
          } catch {
            /* ignore */
          }
        }
        threadPendingFilePreviewUrl = url;
        threadFilePreviewThumbEl.src = url;
        threadFilePreviewThumbEl.classList.remove("hidden");
      });
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
    const el = findMessageRowElementByMessageId(rootId) || document.getElementById(`msg-${rootId}`);
    if (el) el.scrollIntoView({ behavior: "smooth", block: "center" });
    hideMessageContextMenu();
    return;
  }

  if (kind === "COMMENT") {
    await openThreadModal(rootId, { targetCommentMessageId: Number.isFinite(targetId) ? targetId : null });
  }
}

/** 타임라인 캐시 밖 원글을 스레드 모달용으로 로드 */
async function fetchChannelMessageForModal(channelId, messageId) {
  const emp = String(currentUser?.employeeNo || "").trim();
  const ch = Number(channelId);
  const mid = Number(messageId);
  if (!emp || !Number.isFinite(ch) || !Number.isFinite(mid)) return null;
  try {
    const res = await apiFetch(
      `/api/channels/${ch}/messages/${mid}?employeeNo=${encodeURIComponent(emp)}`
    );
    const json = await res.json().catch(() => ({}));
    if (!res.ok) return null;
    return json.data ?? null;
  } catch {
    return null;
  }
}

function focusThreadMessageRowById(messageId) {
  const id = Number(messageId);
  if (!Number.isFinite(id)) return;
  const run = () => {
    const targetEl = document.getElementById(`thread-msg-${id}`);
    if (!targetEl) return;
    targetEl.scrollIntoView({ behavior: "smooth", block: "center" });
    targetEl.classList.add("msg-mention-focus");
    setTimeout(() => targetEl.classList.remove("msg-mention-focus"), 1400);
  };
  requestAnimationFrame(() => requestAnimationFrame(() => setTimeout(run, 50)));
}

async function openThreadModal(rootMessageId, { targetCommentMessageId = null } = {}) {
  if (!activeChannelId || !currentUser) return;
  const rootId = Number(rootMessageId);
  if (!Number.isFinite(rootId)) return;

  threadRootMessageId = rootId;
  // 댓글 모달 입력으로 전환하므로 답글 모드를 해제한다.
  if (replyComposerTargetMessageId != null) clearReplyComposerTarget();
  if (threadMessageInputEl) threadMessageInputEl.value = "";
  scheduleThreadComposerInputHeight();
  clearThreadFilePreview();
  hideMessageContextMenu();
  openModal("modalThread");
  isRenderingThreadModal = true;

  // ROOT 메시지(타임라인에서 캐시로 재사용; 없으면 단건 API)
  let rootMsg = timelineRootMessageById.get(rootId);
  threadRootContainerEl.innerHTML = "";
  threadCommentsContainerEl.innerHTML = "";

  if (!rootMsg) {
    rootMsg = await fetchChannelMessageForModal(activeChannelId, rootId);
    if (rootMsg) timelineRootMessageById.set(rootId, rootMsg);
  }

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
      focusThreadMessageRowById(targetCommentMessageId);
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
    if (threadPendingFilesQueue.length > 0) {
      const n = threadPendingFilesQueue.length;
      for (let i = 0; i < n; i++) {
        const isLast = i === n - 1;
        const reloadMode = text ? "none" : isLast ? "thread" : "none";
        await uploadFile(threadPendingFilesQueue[i], {
          parentMessageId: rootId,
          threadKind: "COMMENT",
          reloadMode,
          progressContext: "thread",
          batchIndex: i + 1,
          batchTotal: n,
          skipNewMsgsDividerAfterReload: true,
        });
      }
      clearThreadFilePreview();
      if (!text) {
        threadMessageInputEl.value = "";
        scheduleThreadComposerInputHeight();
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
    scheduleThreadComposerInputHeight();
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
    const files = normalizePendingFileList(threadFileInputEl.files);
    if (files.length === 0) return;
    setThreadComposerPendingFiles(files);
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
  threadMessageInputEl.addEventListener("input", () => {
    scheduleThreadComposerInputHeight();
  });
}

// 메시지 우클릭 컨텍스트 메뉴 이벤트(댓글/답글)
messagesEl?.addEventListener("contextmenu", (e) => {
  const row = e.target.closest(".msg-row.msg-chat");
  if (!row) return;
  let mid = row.dataset.messageId ? Number(row.dataset.messageId) : null;
  let rootMid = row.dataset.rootMessageId ? Number(row.dataset.rootMessageId) : mid;
  const hitGroupItem = e.target.closest(".msg-attach-group-item[data-message-id]");
  if (hitGroupItem && row.classList.contains("msg-attachment-group")) {
    const itemMid = Number(hitGroupItem.dataset.messageId);
    const itemRoot = hitGroupItem.dataset.rootMessageId
      ? Number(hitGroupItem.dataset.rootMessageId)
      : itemMid;
    if (Number.isFinite(itemMid)) mid = itemMid;
    if (Number.isFinite(itemRoot)) rootMid = itemRoot;
  }
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
  if (messageContextMenuEl && !messageContextMenuEl.classList.contains("hidden")) {
    const inMessageMenu = e.target && messageContextMenuEl.contains(e.target);
    if (!inMessageMenu) hideMessageContextMenu();
  }
  if (memberContextMenuEl && !memberContextMenuEl.classList.contains("hidden")) {
    const inMemberMenu = e.target && memberContextMenuEl.contains(e.target);
    if (!inMemberMenu) hideMemberContextMenu();
  }
  if (channelSidebarContextMenuEl && !channelSidebarContextMenuEl.classList.contains("hidden")) {
    const inCh = e.target && channelSidebarContextMenuEl.contains(e.target);
    if (!inCh) closeChannelSidebarContextMenu();
  }
});

document.addEventListener(
  "contextmenu",
  (ev) => {
    const item = ev.target.closest(
      "#channelList .channel-item, #dmList .channel-item, #quickRailScroll .channel-item"
    );
    if (!item || !mainApp || mainApp.classList.contains("hidden")) return;
    ev.preventDefault();
    const cid = Number(item.dataset.channelId);
    if (!Number.isFinite(cid)) return;
    const name = String(item.dataset.channelName || "").trim() || "채널";
    const ctype = String(item.dataset.channelType || "PUBLIC");
    openChannelSidebarContextMenu(ev.clientX, ev.clientY, cid, name, ctype);
  },
  true
);

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

if (btnMemberCtxDelegateEl) {
  btnMemberCtxDelegateEl.addEventListener("click", async () => {
    const targetEmp = String(memberContextSelectedEmployeeNo || "").trim();
    hideMemberContextMenu();
    if (!activeChannelId || !targetEmp) return;
    const res = await apiFetch(`/api/channels/${activeChannelId}/delegate-manager`, {
      method: "POST",
      body: JSON.stringify({ targetEmployeeNo: targetEmp }),
    });
    const json = await res.json().catch(() => ({}));
    if (!res.ok) {
      await uiAlert(json.error?.message || "관리자 위임에 실패했습니다.");
      return;
    }
    await loadChannelMembers(activeChannelId);
    await loadMyChannels();
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
  const prevIsAttachment =
    beforeAppendChat && beforeAppendChat.classList.contains("msg-has-attachment");
  const showAvatar =
    !beforeAppendChat ||
    beforeAppendChat.dataset.senderId !== sid ||
    prevIsAttachment;
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

function isMessagesElNearBottom(thresholdPx = 140) {
  if (!messagesEl) return true;
  const rest = messagesEl.scrollHeight - messagesEl.scrollTop - messagesEl.clientHeight;
  return rest <= thresholdPx;
}

function trimMessages() {
  if (!messagesEl) return;
  while (messagesEl.children.length > HARD_MAX_CHAT_DOM_NODES) {
    messagesEl.removeChild(messagesEl.firstChild);
  }
  while (messagesEl.children.length > MAX_CHAT_DOM_NODES && isMessagesElNearBottom()) {
    messagesEl.removeChild(messagesEl.firstChild);
  }
}

function parseTimelinePagePayload(json) {
  const d = json?.data;
  if (Array.isArray(d)) {
    return { items: d, hasMoreOlder: false };
  }
  return {
    items: Array.isArray(d?.items) ? d.items : [],
    hasMoreOlder: !!d?.hasMoreOlder,
  };
}

function mergeAdjacentDuplicateDateDividers(container) {
  if (!container) return;
  let changed = true;
  while (changed) {
    changed = false;
    let el = container.firstElementChild;
    while (el && el.nextElementSibling) {
      const next = el.nextElementSibling;
      if (
        el.classList.contains("msg-date-divider") &&
        next.classList.contains("msg-date-divider") &&
        el.dataset.dateKey &&
        el.dataset.dateKey === next.dataset.dateKey
      ) {
        next.remove();
        changed = true;
        continue;
      }
      el = next;
    }
  }
}

function getOldestVisibleTimelineMessageId() {
  if (!messagesEl) return null;
  const row = messagesEl.querySelector('.msg-row[id^="msg-"]');
  if (!row?.id?.startsWith("msg-")) return null;
  const n = Number(row.id.slice(4));
  return Number.isFinite(n) ? n : null;
}

function mergeTimelineRootsIntoCache(items) {
  if (!Array.isArray(items)) return;
  items.forEach((m) => {
    if (!m || m.isReply) return;
    const mid = m.messageId ?? m.message_id;
    if (mid != null) timelineRootMessageById.set(Number(mid), m);
  });
}

function prependTimelineMessages(items) {
  if (!messagesEl || !items || !items.length) return;
  const prevH = messagesEl.scrollHeight;
  const prevTop = messagesEl.scrollTop;
  const frag = document.createDocumentFragment();
  let prevDk = null;
  let i = 0;
  while (i < items.length) {
    const m = items[i];
    const dk = dateKeyLocal(m.createdAt);
    if (prevDk !== dk) {
      frag.appendChild(createDateDividerElement(m.createdAt));
      prevDk = dk;
    }
    const showAvatar = shouldShowAvatarForMessage(items, i);
    const showTime = shouldShowMessageTime(items, i);
    const mt = String(m.messageType || m.message_type || "").toUpperCase();
    const isReplyRow = !!m.isReply || mt.startsWith("REPLY");
    let row;
    if (isReplyRow) {
      row = createReplyTimelineRowElement(m, { showAvatar, showTime });
      i++;
    } else {
      const group = tryConsumeFileAttachmentGroup(items, i);
      if (group) {
        const showTimeG = shouldShowMessageTime(items, group.endIdx);
        row = createFileAttachmentGroupRowFromMsgs(group.group, { showAvatar, showTime: showTimeG });
        i = group.endIdx + 1;
      } else {
        row = createMessageRowElement(m, { showAvatar, showTime });
        i++;
      }
    }
    const mid =
      row.dataset?.messageId != null && row.dataset.messageId !== ""
        ? Number(row.dataset.messageId)
        : m.messageId ?? m.message_id;
    if (mid != null && Number.isFinite(Number(mid))) row.id = `msg-${mid}`;
    frag.appendChild(row);
  }
  messagesEl.insertBefore(frag, messagesEl.firstChild);
  mergeAdjacentDuplicateDateDividers(messagesEl);
  messagesEl.scrollTop = prevTop + (messagesEl.scrollHeight - prevH);
  refreshPresenceDots();
}

function ensureMessagesScrollLoadOlder() {
  if (!messagesEl || messageListScrollHandlerBound) return;
  messageListScrollHandlerBound = true;
  let debounceT = null;
  messagesEl.addEventListener(
    "scroll",
    () => {
      if (debounceT) clearTimeout(debounceT);
      debounceT = setTimeout(() => {
        if (!messagesEl || messagesEl.scrollTop > 100) return;
        loadOlderTimelinePage();
      }, 120);
    },
    { passive: true }
  );
}

async function loadOlderTimelinePage() {
  if (!activeChannelId || !currentUser || chatTimelineLoadingOlder || !chatTimelineHasMoreOlder) return;
  const beforeId = getOldestVisibleTimelineMessageId();
  if (beforeId == null) return;
  const channelIdForRequest = activeChannelId;
  chatTimelineLoadingOlder = true;
  const loadingEl = document.getElementById("msgHistoryLoading");
  if (loadingEl) loadingEl.classList.remove("hidden");
  try {
    const empQ = encodeURIComponent(currentUser.employeeNo);
    const url = `/api/channels/${channelIdForRequest}/messages/timeline?employeeNo=${empQ}&limit=${TIMELINE_PAGE_LIMIT}&beforeMessageId=${beforeId}`;
    const res = await apiFetch(url);
    const json = await res.json().catch(() => ({}));
    if (!res.ok) return;
    if (Number(channelIdForRequest) !== Number(activeChannelId)) return;
    const { items, hasMoreOlder } = parseTimelinePagePayload(json);
    chatTimelineHasMoreOlder = hasMoreOlder;
    if (!items.length) {
      chatTimelineHasMoreOlder = false;
      return;
    }
    mergeTimelineRootsIntoCache(items);
    prependTimelineMessages(items);
  } catch (e) {
    console.error("이전 메시지 로드 실패", e);
  } finally {
    chatTimelineLoadingOlder = false;
    if (loadingEl) loadingEl.classList.add("hidden");
  }
}

/* ==========================================================================
 * Socket.io
 * ========================================================================== */
/** 실시간 연결 실패 시 사용자에게 한 번만 안내(멘션·프레즌스 미동작 원인 파악용) */
function pushRealtimeNoticeToast(message) {
  const stack = document.getElementById("mentionToastStack");
  if (!stack || !message) return;
  const t = document.createElement("div");
  t.className = "mention-toast mention-toast-notice";
  t.setAttribute("role", "status");
  t.textContent = String(message);
  stack.appendChild(t);
  setTimeout(() => {
    if (t.parentNode) t.remove();
  }, 14_000);
}

function initSocket() {
  if (socket) socket.disconnect();
  let realtimeConnectErrorToasted = false;
  socket = io(SOCKET_URL, {
    reconnection: true,
    reconnectionAttempts: Infinity,
    reconnectionDelay: 1000,
    reconnectionDelayMax: 15000,
    randomizationFactor: 0.3,
    timeout: 10000,
  });

  socket.on("connect_error", (err) => {
    console.warn("[ECH] Realtime connect_error", SOCKET_URL, err?.message || err);
    if (!realtimeConnectErrorToasted) {
      realtimeConnectErrorToasted = true;
      pushRealtimeNoticeToast(
        `실시간 연결 실패: ${SOCKET_URL} (xhr poll 등) — 보통 Node Realtime이 꺼져 있을 때 납니다. 저장소 루트의 realtime 폴더에서 npm install 후 npm run dev 를 실행하세요(기본 :3001). 방화벽·프록시 사용 시 URL은 meta ech-realtime-url 또는 localStorage ech_realtime_url 로 지정 가능합니다.`
      );
    }
  });

  socket.on("connect", async () => {
    realtimeConnectErrorToasted = false;
    if (currentUser?.employeeNo) {
      socket.emit("presence:set", { employeeNo: currentUser.employeeNo, status: "ONLINE" });
    }
    if (lastSidebarChannelsSnapshot?.length) {
      joinAllChannelSocketRooms(lastSidebarChannelsSnapshot);
    } else if (activeChannelId) {
      socket.emit("channel:join", activeChannelId);
    }
    await fetchPresenceSnapshot();
    refreshPresenceDots();
    if (activeChannelId) loadChannelMembers(activeChannelId);
  });

  socket.on("reconnect", async () => {
    if (currentUser?.employeeNo) {
      socket.emit("presence:set", { employeeNo: currentUser.employeeNo, status: "ONLINE" });
    }
    if (lastSidebarChannelsSnapshot?.length) {
      joinAllChannelSocketRooms(lastSidebarChannelsSnapshot);
    } else if (activeChannelId) {
      socket.emit("channel:join", activeChannelId);
    }
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
    const active = activeChannelId != null ? Number(activeChannelId) : NaN;
    if (Number.isFinite(cid) && Number.isFinite(active) && cid === active) {
      appendMessageRealtime(msg);
      // `mention:notify` 경로가 누락/지연되는 경우 대비:
      // 현재 채널에서 내 멘션이 포함된 메시지를 받으면 클라이언트가 폴백 토스트를 띄운다.
      maybeShowMentionToastFromMessage(msg);
      if (msg.messageId != null) {
        scheduleMarkChannelReadCaughtUp(activeChannelId);
      }
      // 같은 채널이어도 탭/창이 가려졌을 때만 일반 토스트(가시 상태면 생략 — 파일 선택창 등으로 포커스만 잃은 경우 포함)
      // 내 멘션은 위에서 maybeShowMentionToastFromMessage → pushMentionToast가 이미 처리 — 중복 알림 방지
      if (typeof document !== "undefined" && document.hidden) {
        const myEmp = String(currentUser.employeeNo || "").trim();
        const mentioned = extractMentionEmployeeNosFromTextClient(msg.text || "");
        if (!myEmp || !mentioned.includes(myEmp)) {
          pushNewMessageToast(msg);
        }
      }
    } else {
      pushNewMessageToast(msg);
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
      scheduleMarkChannelReadCaughtUp(cid);
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

/**
 * 참여 중인 모든 채널/DM 룸에 join — `message:new`는 룸 단위 브로드캐스트이므로,
 * 현재 탭 채널만 join하면 다른 대화 토스트를 받지 못한다.
 */
function joinAllChannelSocketRooms(channelRows) {
  if (!socket?.connected || !channelRows?.length) return;
  const seen = new Set();
  for (const ch of channelRows) {
    const id = Number(ch.channelId ?? ch.channel_id);
    if (!Number.isFinite(id) || id <= 0) continue;
    if (seen.has(id)) continue;
    seen.add(id);
    socket.emit("channel:join", id);
  }
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
    await markChannelReadCaughtUp(channelId);
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
const shownNewMessageToastIds = new Set();

// OS Notification helper for background mode.
// - Desktop(Electron): main process Notification (no browser permission prompt)
// - Web: Web Notifications API (permission can be required)
let osNotificationPermissionRequestedOnce = false;
const osNotificationOnClickHandlersByTag = new Map();
let osNotificationClickBridgeBound = false;

function isInBackgroundForOsNotification() {
  const hidden = typeof document !== "undefined" ? !!document.hidden : false;
  const hasFocusFn = typeof document !== "undefined" && typeof document.hasFocus === "function";
  const focus = hasFocusFn ? !!document.hasFocus() : true;
  return hidden || !focus;
}

async function ensureOsNotificationPermissionFromUserGesture() {
  // Electron mode typically does not require Notification permission.
  if (typeof window !== "undefined" && window.electronAPI?.showOsNotification) return true;
  if (typeof window === "undefined" || !("Notification" in window)) return false;
  if (Notification.permission === "granted") return true;
  if (Notification.permission === "denied") return false;
  if (osNotificationPermissionRequestedOnce) return false;
  osNotificationPermissionRequestedOnce = true;
  try {
    const res = await Notification.requestPermission();
    return res === "granted";
  } catch {
    return false;
  }
}

function isEchElectronClient() {
  return typeof window !== "undefined" && typeof window.electronAPI?.showOsNotification === "function";
}

function showOsNotificationIfAllowed({ tag, title, body, onClick }) {
  if (!tag) tag = String(Date.now());

  // Background-only rule.
  if (!isInBackgroundForOsNotification()) return;

  // Electron mode: forward to main process and wire click back to renderer by tag.
  if (typeof window !== "undefined" && window.electronAPI?.showOsNotification) {
    if (typeof onClick === "function") {
      osNotificationOnClickHandlersByTag.set(String(tag), onClick);
    }
    if (!osNotificationClickBridgeBound && typeof window.electronAPI?.onOsNotificationClick === "function") {
      osNotificationClickBridgeBound = true;
      window.electronAPI.onOsNotificationClick(({ tag: clickedTag }) => {
        const cb = osNotificationOnClickHandlersByTag.get(String(clickedTag));
        if (typeof cb === "function") {
          try {
            cb();
          } finally {
            osNotificationOnClickHandlersByTag.delete(String(clickedTag));
          }
        } else {
          osNotificationOnClickHandlersByTag.delete(String(clickedTag));
        }
      });
    }
    window.electronAPI.showOsNotification({ tag: String(tag), title: String(title || ""), body: String(body || "") });
    return;
  }

  if (typeof window === "undefined" || !("Notification" in window)) return;
  if (Notification.permission !== "granted") return;
  if (!isInBackgroundForOsNotification()) return;

  const n = new Notification(String(title || ""), { body: String(body || ""), tag: String(tag || "") });
  if (typeof onClick === "function") {
    n.onclick = () => {
      try {
        window.focus?.();
        onClick();
      } catch {
        // ignore
      }
      try {
        n.close();
      } catch {
        // ignore
      }
    };
  }
  setTimeout(() => {
    try {
      n.close();
    } catch {
      // ignore
    }
  }, 10_000);
}

function notifyMutedStorageKey() {
  const emp = String(currentUser?.employeeNo || "").trim();
  return emp ? `ech_notify_muted_channels_${emp}` : null;
}

function getMutedChannelIdsSet() {
  const key = notifyMutedStorageKey();
  if (!key) return new Set();
  try {
    const raw = localStorage.getItem(key);
    const arr = raw ? JSON.parse(raw) : [];
    return new Set(Array.isArray(arr) ? arr.map((x) => Number(x)).filter((x) => Number.isFinite(x)) : []);
  } catch {
    return new Set();
  }
}

function saveMutedChannelIds(set) {
  const key = notifyMutedStorageKey();
  if (!key) return;
  try {
    localStorage.setItem(key, JSON.stringify([...set].sort((a, b) => a - b)));
  } catch {
    /* ignore */
  }
}

function isChannelNotifyMuted(channelId) {
  const id = Number(channelId);
  if (!Number.isFinite(id)) return false;
  return getMutedChannelIdsSet().has(id);
}

function setChannelNotifyMuted(channelId, muted) {
  const id = Number(channelId);
  if (!Number.isFinite(id)) return;
  const s = getMutedChannelIdsSet();
  if (muted) s.add(id);
  else s.delete(id);
  saveMutedChannelIds(s);
  refreshSidebarMuteIndicators();
}

/** 벨+슬래시 SVG(일반 알림 끔 표시). size: 픽셀 너비·높이 */
function notifyMutedBellSvg(size = 14) {
  const s = Number(size) || 14;
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${s}" height="${s}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9"/><path d="M10.3 21a1.94 1.94 0 0 0 3.4 0"/><line x1="2" y1="2" x2="22" y2="22"/></svg>`;
}

function notifyMutedIconHtml(channelId) {
  if (!isChannelNotifyMuted(channelId)) return "";
  return `<span class="channel-notify-muted-icon" title="이 채팅방 일반 알림 끔" aria-label="일반 알림 끔">${notifyMutedBellSvg(14)}</span>`;
}

function quickRailNotifyMutedHtml(channelId) {
  if (!isChannelNotifyMuted(channelId)) return "";
  return `<span class="quick-rail-notify-muted-icon" title="일반 알림 끔" aria-hidden="true">${notifyMutedBellSvg(12)}</span>`;
}

function refreshSidebarMuteIndicators() {
  if (!currentUser || !Array.isArray(lastSidebarChannelsSnapshot) || lastSidebarChannelsSnapshot.length === 0) {
    return;
  }
  renderChannelList(lastSidebarChannelsSnapshot);
}

function syncHeaderNotifyButton() {
  const btn = document.getElementById("btnHeaderNotifyToggle");
  if (!btn) return;
  if (!activeChannelId) {
    btn.classList.add("hidden");
    return;
  }
  btn.classList.remove("hidden");
  const muted = isChannelNotifyMuted(activeChannelId);
  const iconEl = btn.querySelector(".btn-member-panel-action-icon");
  const labelEl = btn.querySelector(".btn-member-panel-action-label");
  if (iconEl && labelEl) {
    iconEl.textContent = muted ? "🔔" : "🔕";
    labelEl.textContent = muted ? "알림 켜기" : "알림 끄기";
  } else {
    btn.textContent = muted ? "🔔 알림 켜기" : "🔕 알림 끄기";
  }
  btn.title = muted
    ? "일반 메시지 토스트 다시 받기(멘션·미읽음 배지는 항상 동작)"
    : "다른 채에서 온 일반 메시지 토스트만 끔 · 멘션 토스트·미읽음 배지는 유지";
}

function closeChannelSidebarContextMenu() {
  channelSidebarContextMenuEl?.classList.add("hidden");
}

function syncSidebarCtxNotifyButtonLabel() {
  const btn = document.getElementById("btnSidebarCtxToggleNotify");
  if (!btn || sidebarCtxChannelId == null) return;
  const muted = isChannelNotifyMuted(sidebarCtxChannelId);
  btn.textContent = muted ? "알림 켜기" : "알림 끄기";
}

function syncSidebarCtxQuickRailPinLabel() {
  const btn = document.getElementById("btnSidebarCtxQuickRailPin");
  if (!btn || sidebarCtxChannelId == null) return;
  const pinned = isQuickRailChannelPinned(sidebarCtxChannelId);
  btn.textContent = pinned ? "퀵 레일 고정 해제" : "퀵 레일에 고정";
}

function openChannelSidebarContextMenu(clientX, clientY, channelId, channelName, channelType) {
  if (!channelSidebarContextMenuEl) return;
  sidebarCtxChannelId = channelId;
  sidebarCtxChannelName = String(channelName || "");
  sidebarCtxChannelType = String(channelType || "PUBLIC");
  syncSidebarCtxNotifyButtonLabel();
  syncSidebarCtxQuickRailPinLabel();
  hideMemberContextMenu();
  hideMessageContextMenu();
  const menuW = 220;
  const x = Math.min(clientX, window.innerWidth - menuW - 8);
  const y = Math.min(clientY, window.innerHeight - 88);
  channelSidebarContextMenuEl.style.left = `${x}px`;
  channelSidebarContextMenuEl.style.top = `${y}px`;
  channelSidebarContextMenuEl.classList.remove("hidden");
}

/** 일반 활동 알림(업무 사이드바 변경 등). */
function pushActivityToast({ title, locationLine, preview, onClick }) {
  const stack = document.getElementById("mentionToastStack");
  if (!stack) return;
  const toast = document.createElement("button");
  toast.type = "button";
  toast.className = "mention-toast mention-toast-activity";
  toast.innerHTML = `<span class="mention-toast-title">${escHtml(String(title || "알림"))}</span><span class="mention-toast-loc">${escHtml(String(locationLine || ""))}</span><span class="mention-toast-preview">${escHtml(String(preview || ""))}</span>`;
  if (typeof onClick === "function") {
    toast.addEventListener("click", () => {
      toast.remove();
      onClick();
    });
  } else {
    toast.addEventListener("click", () => toast.remove());
  }
  stack.appendChild(toast);
  setTimeout(() => {
    if (toast.parentNode) toast.remove();
  }, 12_000);
}

/** 다른 채널/DM의 신규 일반 메시지 토스트. 알림 끄기 시에만 억제(미읽음 배지·멘션 토스트와 무관). */
function pushNewMessageToast(msg) {
  if (!msg || !currentUser) return;
  const cid = Number(msg.channelId);
  if (!Number.isFinite(cid)) return;
  if (isChannelNotifyMuted(cid)) return;
  const myEmp = String(currentUser.employeeNo || "").trim();
  const sender = String(msg.senderId ?? msg.sender_id ?? "").trim();
  if (sender && myEmp && sender === myEmp) return;
  // 동일 채널이고 탭/창이 보이는 동안에는 토스트 생략(파일 선택 대화상자 등으로 포커스만 잃은 경우는 document.hidden 이 false)
  if (activeChannelId != null && Number(activeChannelId) === cid && typeof document !== "undefined" && !document.hidden) return;
  if (msg.messageId != null) {
    const mid = String(msg.messageId);
    if (shownNewMessageToastIds.has(mid)) return;
    shownNewMessageToastIds.add(mid);
  }
  const row = document.querySelector(`.channel-item[data-channel-id="${cid}"]`);
  const displayName = String(row?.dataset.channelName || "").trim() || "채팅";
  const channelType = String(row?.dataset.channelType || "PUBLIC");
  const isDm = channelType === "DM";
  const locationText = isDm ? `DM · ${displayName}` : `채널 · #${displayName}`;
  const senderName = String(msg.senderName || msg.sender_name || "").trim() || "알 수 없음";
  const preview = String(msg.text || "").replace(/\s+/g, " ").trim().slice(0, 160);

  // OS notification for background mode (general message mute already applied via early returns).
  const midNumForOs = msg.messageId != null ? Number(msg.messageId) : null;
  if (Number.isFinite(midNumForOs)) {
    showOsNotificationIfAllowed({
      tag: `ech_os_msg_${cid}_${midNumForOs}`,
      title: "새 메시지",
      body: `${senderName}: ${preview || "(내용 없음)"}`,
      onClick: () => selectChannel(cid, displayName, channelType),
    });
  } else {
    showOsNotificationIfAllowed({
      tag: `ech_os_msg_${cid}_${Date.now()}`,
      title: "새 메시지",
      body: `${senderName}: ${preview || "(내용 없음)"}`,
      onClick: () => selectChannel(cid, displayName, channelType),
    });
  }

  if (isEchElectronClient()) return;

  const stack = document.getElementById("mentionToastStack");
  if (!stack) return;
  const toast = document.createElement("button");
  toast.type = "button";
  toast.className = "mention-toast mention-toast-msg";
  toast.innerHTML = `<span class="mention-toast-title">새 메시지</span><span class="mention-toast-sub">${escHtml(senderName)}</span><span class="mention-toast-loc">${escHtml(locationText)}</span><span class="mention-toast-preview">${escHtml(preview || "(내용 없음)")}</span>`;
  toast.addEventListener("click", () => {
    toast.remove();
    // Request permission from user gesture (click).
    void ensureOsNotificationPermissionFromUserGesture();
    selectChannel(cid, displayName, channelType);
  });
  stack.appendChild(toast);
  setTimeout(() => {
    if (toast.parentNode) toast.remove();
  }, 18_000);
}

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
  const cid = Number(p.channelId);
  if (!Number.isFinite(cid)) return;
  if (p.messageId != null) {
    const mid = String(p.messageId);
    if (shownMentionToastMessageIds.has(mid)) return;
    shownMentionToastMessageIds.add(mid);
  }
  const channelName = String(p.channelName || "채널");
  const channelType = String(p.channelType || "PUBLIC");
  const senderName = String(p.senderName || "");
  const preview = String(p.messagePreview || "").slice(0, 160);
  const isDm = channelType === "DM";
  const locationText = isDm
    ? `DM · ${channelName}`
    : `채널 · #${channelName}`;
  const senderText = senderName || "알 수 없음";
  const isDifferentChannel = activeChannelId == null || Number(activeChannelId) !== cid;
  if (isDifferentChannel) {
    enqueueUnreadMention({
      channelId: cid,
      channelName,
      channelType,
      senderName,
      senderEmployeeNo: String(p.senderEmployeeNo || ""),
      messagePreview: preview,
      messageId: p.messageId != null ? Number(p.messageId) : null,
      createdAt: p.createdAt || new Date().toISOString(),
    });
  }

  // OS notification for mentions (mute ignored). Only fires in background.
  const midNumForOs = p.messageId != null ? Number(p.messageId) : null;
  if (Number.isFinite(midNumForOs)) {
    showOsNotificationIfAllowed({
      tag: `ech_os_mention_${cid}_${midNumForOs}`,
      title: "새 멘션",
      body: `${senderText}: ${preview || "(내용 없음)"}`,
      onClick: () =>
        selectChannel(cid, channelName, channelType, {
          targetMessageId: midNumForOs,
        }),
    });
  } else {
    showOsNotificationIfAllowed({
      tag: `ech_os_mention_${cid}_${Date.now()}`,
      title: "새 멘션",
      body: `${senderText}: ${preview || "(내용 없음)"}`,
      onClick: () => selectChannel(cid, channelName, channelType),
    });
  }

  if (isEchElectronClient()) return;

  const toast = document.createElement("button");
  toast.type = "button";
  toast.className = "mention-toast";
  toast.innerHTML = `<span class="mention-toast-title">새 멘션</span><span class="mention-toast-sub">${escHtml(senderText)}</span><span class="mention-toast-loc">${escHtml(locationText)}</span><span class="mention-toast-preview">${escHtml(preview)}</span>`;
  toast.addEventListener("click", () => {
    toast.remove();
    // Request permission from user gesture (click).
    void ensureOsNotificationPermissionFromUserGesture();
    if (p.messageId != null) {
      removeUnreadMentionById(`m_${Number(p.messageId)}`);
    }
    selectChannel(cid, channelName, channelType, {
      targetMessageId: p.messageId != null ? Number(p.messageId) : null,
    });
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
    senderEmployeeNo: String(msg.senderId || ""),
    messagePreview: mentionPreviewForToastClient(msg.text || "", 160),
    messageId: msg.messageId != null ? Number(msg.messageId) : null,
    createdAt: msg.createdAt || new Date().toISOString(),
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

    if (pendingFilesQueue.length > 0) {
      const n = pendingFilesQueue.length;
      for (let i = 0; i < n; i++) {
        const isLast = i === n - 1;
        await uploadFile(pendingFilesQueue[i], {
          parentMessageId,
          threadKind: "REPLY",
          reloadMode: isLast ? "timeline" : "none",
          batchIndex: i + 1,
          batchTotal: n,
          skipNewMsgsDividerAfterReload: true,
        });
      }
      clearFilePreview();
      if (!preparedText) {
        messageInputEl.value = "";
        scheduleComposerInputHeight();
        clearReplyComposerTarget();
        mentionDisplayToEmployeeNo.clear();
        clearChatReadAnchorUi();
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

      clearChatReadAnchorUi();
      messageInputEl.value = "";
      scheduleComposerInputHeight();
      clearReplyComposerTarget();
      mentionDisplayToEmployeeNo.clear();
      await loadMessages(activeChannelId, { skipNewMsgsDivider: true });
    } catch (e) {
      appendSystemMsg("답글 전송 실패: " + (e?.message || "오류"));
      console.error(e);
    }
    return;
  }

  // 파일이 있으면 파일 먼저 업로드(다중 시 순차, 마지막만 타임라인 갱신)
  if (pendingFilesQueue.length > 0) {
    const n = pendingFilesQueue.length;
    for (let i = 0; i < n; i++) {
      const isLast = i === n - 1;
      await uploadFile(pendingFilesQueue[i], {
        reloadMode: isLast ? "timeline" : "none",
        batchIndex: i + 1,
        batchTotal: n,
        skipNewMsgsDividerAfterReload: true,
      });
    }
    clearFilePreview();
    if (!preparedText) {
      messageInputEl.value = "";
      scheduleComposerInputHeight();
      mentionDisplayToEmployeeNo.clear();
      clearChatReadAnchorUi();
      return;
    }
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
  clearChatReadAnchorUi();
  messageInputEl.value = "";
  scheduleComposerInputHeight();
  mentionDisplayToEmployeeNo.clear();
}

btnSendEl?.addEventListener("click", sendMessage);
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
  if (messageInputEl.value.trim()) {
    clearChatReadAnchorUi();
  }
  scheduleComposerInputHeight();
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

function setFilePreviewUploadStatus(context, text) {
  const el = context === "thread" ? threadFilePreviewUploadStatusEl : filePreviewUploadStatusEl;
  if (el) el.textContent = text || "";
}

const IMAGE_PREVIEW_MAX_EDGE = 1024;
const IMAGE_PREVIEW_SMALL_BYTES = 256 * 1024;
const UPLOAD_COMPRESS_MIN_BYTES = 2 * 1024 * 1024;
const UPLOAD_MAX_EDGE = 4096;

async function buildImagePreviewObjectUrl(file) {
  if (!file || !String(file.type || "").toLowerCase().startsWith("image/")) {
    return URL.createObjectURL(file);
  }
  if (file.size <= IMAGE_PREVIEW_SMALL_BYTES) {
    return URL.createObjectURL(file);
  }
  try {
    let bmp;
    try {
      bmp = await createImageBitmap(file, {
        resizeWidth: IMAGE_PREVIEW_MAX_EDGE,
        resizeHeight: IMAGE_PREVIEW_MAX_EDGE,
      });
    } catch {
      bmp = await createImageBitmap(file);
    }
    const w = bmp.width;
    const h = bmp.height;
    const canvas = document.createElement("canvas");
    canvas.width = w;
    canvas.height = h;
    const ctx = canvas.getContext("2d");
    if (!ctx) {
      bmp.close();
      return URL.createObjectURL(file);
    }
    ctx.drawImage(bmp, 0, 0);
    bmp.close();
    const blob = await new Promise((resolve) => {
      canvas.toBlob((b) => resolve(b), "image/jpeg", 0.85);
    });
    if (!blob) return URL.createObjectURL(file);
    return URL.createObjectURL(blob);
  } catch {
    return URL.createObjectURL(file);
  }
}

async function maybeCompressImageForUpload(file) {
  if (!file || !String(file.type || "").toLowerCase().startsWith("image/")) return file;
  const mime = String(file.type).toLowerCase();
  if (mime === "image/gif") return file;
  if (file.size < UPLOAD_COMPRESS_MIN_BYTES) return file;

  try {
    const bmp = await createImageBitmap(file, {
      resizeWidth: UPLOAD_MAX_EDGE,
      resizeHeight: UPLOAD_MAX_EDGE,
    });
    const canvas = document.createElement("canvas");
    canvas.width = bmp.width;
    canvas.height = bmp.height;
    const ctx = canvas.getContext("2d");
    if (!ctx) {
      bmp.close();
      return file;
    }
    ctx.drawImage(bmp, 0, 0);
    bmp.close();
    const blob = await new Promise((resolve) => {
      canvas.toBlob((b) => resolve(b), "image/jpeg", 0.82);
    });
    if (!blob || blob.size >= file.size * 0.92) return file;
    const base = String(file.name || "image").replace(/\.[^.]+$/, "") || "image";
    return new File([blob], `${base}.jpg`, { type: "image/jpeg" });
  } catch {
    return file;
  }
}

function uploadFileWithProgress(url, formData, token, onProgress) {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open("POST", url);
    xhr.setRequestHeader("Authorization", `Bearer ${token}`);
    xhr.upload.onprogress = (e) => {
      if (e.lengthComputable && typeof onProgress === "function") onProgress(e.loaded / e.total);
    };
    xhr.onload = () => {
      let json = {};
      try {
        json = JSON.parse(xhr.responseText || "{}");
      } catch {
        /* ignore */
      }
      resolve({
        ok: xhr.status >= 200 && xhr.status < 300,
        status: xhr.status,
        json,
      });
    };
    xhr.onerror = () => reject(new Error("network"));
    xhr.send(formData);
  });
}

function normalizePendingFileList(input) {
  if (!input) return [];
  if (Array.isArray(input)) return input.filter((f) => f && f instanceof File);
  if (typeof FileList !== "undefined" && input instanceof FileList) return Array.from(input).filter(Boolean);
  return [];
}

function formatPendingFilesLabel(files) {
  if (!files || files.length === 0) return "";
  if (files.length === 1) {
    const f = files[0];
    return `📎 ${f.name} (${fmtSize(f.size)})`;
  }
  const head = files
    .slice(0, 3)
    .map((f) => f.name)
    .join(", ");
  const more = files.length > 3 ? ` 외 ${files.length - 3}개` : "";
  return `📎 ${files.length}개 파일: ${head}${more}`;
}

function setComposerPendingFile(file) {
  if (!file) return;
  setComposerPendingFiles([file]);
}

function setComposerPendingFiles(files) {
  const list = normalizePendingFileList(files);
  if (list.length === 0) return;
  composerPendingSequence++;
  const mySeq = composerPendingSequence;
  if (pendingComposerPreviewUrl) {
    try {
      URL.revokeObjectURL(pendingComposerPreviewUrl);
    } catch {
      /* ignore */
    }
    pendingComposerPreviewUrl = null;
  }
  pendingFilesQueue = list;
  const firstImage = list.find((f) => String(f.type || "").toLowerCase().startsWith("image/"));
  const displayFile = firstImage || list[0];
  const thumb = document.getElementById("filePreviewThumb");
  if (thumb) {
    if (displayFile.type && displayFile.type.startsWith("image/")) {
      thumb.classList.add("hidden");
      thumb.removeAttribute("src");
      void buildImagePreviewObjectUrl(displayFile).then((url) => {
        if (mySeq !== composerPendingSequence) {
          try {
            URL.revokeObjectURL(url);
          } catch {
            /* ignore */
          }
          return;
        }
        if (pendingComposerPreviewUrl) {
          try {
            URL.revokeObjectURL(pendingComposerPreviewUrl);
          } catch {
            /* ignore */
          }
        }
        pendingComposerPreviewUrl = url;
        thumb.src = url;
        thumb.classList.remove("hidden");
      });
    } else {
      thumb.classList.add("hidden");
      thumb.removeAttribute("src");
    }
  }
  document.getElementById("filePreview").classList.remove("hidden");
  document.getElementById("filePreviewName").textContent = formatPendingFilesLabel(list);
  setFilePreviewUploadStatus("composer", "");
}

document.getElementById("fileInput").addEventListener("change", (e) => {
  const files = normalizePendingFileList(e.target?.files);
  if (files.length === 0) return;
  setComposerPendingFiles(files);
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

  const imageFiles = [];
  const items = dt.items;
  if (items && items.length > 0) {
    for (let i = 0; i < items.length; i++) {
      const it = items[i];
      if (it.kind === "file" && it.type && it.type.startsWith("image/")) {
        const f = it.getAsFile();
        if (f && f.size > 0) imageFiles.push(f);
      }
    }
  }
  if (imageFiles.length === 0 && dt.files && dt.files.length > 0) {
    for (let i = 0; i < dt.files.length; i++) {
      const f = dt.files[i];
      if (f.type && f.type.startsWith("image/")) imageFiles.push(f);
    }
  }
  if (imageFiles.length === 0) return;

  e.preventDefault();
  const named = imageFiles.map((imageFile, idx) => {
    const mime = imageFile.type || "image/png";
    let ext = "png";
    if (mime === "image/jpeg" || mime === "image/jpg") ext = "jpg";
    else if (mime === "image/gif") ext = "gif";
    else if (mime === "image/webp") ext = "webp";
    else if (mime === "image/png") ext = "png";

    const rawName = imageFile.name ? String(imageFile.name).trim() : "";
    const generic =
      !rawName ||
      rawName === "image.png" ||
      rawName.toLowerCase() === "image.jpeg" ||
      rawName === "clipboard.png";
    const name = generic ? `pasted-image-${Date.now()}-${idx}.${ext}` : rawName;
    return generic || !imageFile.name
      ? new File([imageFile], name, { type: mime })
      : imageFile;
  });
  setComposerPendingFiles(named);
}

const viewChatEl = document.getElementById("viewChat");

function isModalOverlayBlockingChatDrop() {
  try {
    const open = document.querySelector(".modal-overlay:not(.hidden)");
    return !!open;
  } catch {
    return false;
  }
}

if (viewChatEl) {
  viewChatEl.addEventListener("paste", handleChatImagePaste, true);

  viewChatEl.addEventListener("dragover", (e) => {
    if (!activeChannelId || !currentUser) return;
    if (isModalOverlayBlockingChatDrop()) return;
    e.preventDefault();
    e.dataTransfer.dropEffect = "copy";
    viewChatEl.classList.add("view-chat--drag-over");
  });

  viewChatEl.addEventListener("dragleave", (e) => {
    if (!viewChatEl.contains(e.relatedTarget)) {
      viewChatEl.classList.remove("view-chat--drag-over");
    }
  });

  viewChatEl.addEventListener("drop", (e) => {
    e.preventDefault();
    viewChatEl.classList.remove("view-chat--drag-over");
    if (!activeChannelId || !currentUser) return;
    if (isModalOverlayBlockingChatDrop()) return;
    const dt = e.dataTransfer;
    if (!dt || !dt.files || dt.files.length === 0) return;
    const files = normalizePendingFileList(dt.files);
    if (files.length === 0) return;
    setComposerPendingFiles(files);
  });

  document.addEventListener(
    "dragend",
    () => {
      viewChatEl.classList.remove("view-chat--drag-over");
    },
    true
  );
}

document.getElementById("btnClearFile").addEventListener("click", clearFilePreview);

function clearFilePreview() {
  composerPendingSequence++;
  if (pendingComposerPreviewUrl) {
    try {
      URL.revokeObjectURL(pendingComposerPreviewUrl);
    } catch {
      /* ignore */
    }
    pendingComposerPreviewUrl = null;
  }
  pendingFilesQueue = [];
  const thumb = document.getElementById("filePreviewThumb");
  if (thumb) {
    thumb.classList.add("hidden");
    thumb.removeAttribute("src");
  }
  document.getElementById("filePreview").classList.add("hidden");
  document.getElementById("filePreviewName").textContent = "";
  setFilePreviewUploadStatus("composer", "");
}

async function uploadFile(
  file,
  {
    parentMessageId = null,
    threadKind = null,
    reloadMode = "timeline",
    progressContext = "composer",
    batchIndex = 0,
    batchTotal = 0,
    /** 본인 전송 직후 타임라인 갱신 시 「새 메시지」 구분선을 다시 넣지 않음 */
    skipNewMsgsDividerAfterReload = false,
  } = {}
) {
  if (!activeChannelId || !currentUser || !file) return;

  const ctx = progressContext === "thread" ? "thread" : "composer";
  const sendBtn = ctx === "thread" ? threadBtnSendEl : btnSendEl;
  const batchPrefix =
    Number(batchTotal) > 1 && Number(batchIndex) > 0 ? `[${batchIndex}/${batchTotal}] ` : "";

  const params = new URLSearchParams();
  params.set("employeeNo", String(currentUser.employeeNo));
  if (parentMessageId != null && Number.isFinite(Number(parentMessageId))) {
    params.set("parentMessageId", String(parentMessageId));
  }
  if (threadKind != null && String(threadKind).trim()) {
    params.set("threadKind", String(threadKind).trim());
  }

  const uploadUrl = `${API_BASE}/api/channels/${activeChannelId}/files/upload?${params.toString()}`;
  const token = getToken();

  try {
    if (sendBtn) sendBtn.disabled = true;
    const showPrep =
      String(file.type || "").toLowerCase().startsWith("image/") &&
      file.size >= UPLOAD_COMPRESS_MIN_BYTES;
    setFilePreviewUploadStatus(ctx, batchPrefix + (showPrep ? "이미지 준비 중…" : ""));
    await new Promise((r) => requestAnimationFrame(() => r()));

    const prepared = await maybeCompressImageForUpload(file);
    setFilePreviewUploadStatus(ctx, batchPrefix + "업로드 중… 0%");

    const formData = new FormData();
    formData.append("file", file, file.name || "upload");
    if (prepared !== file) {
      const pvName =
        prepared instanceof File && prepared.name ? prepared.name : "preview.jpg";
      formData.append("preview", prepared, pvName);
    }

    const { ok, json } = await uploadFileWithProgress(uploadUrl, formData, token, (p) => {
      setFilePreviewUploadStatus(ctx, batchPrefix + `업로드 중 ${Math.round(p * 100)}%`);
    });

    if (!ok) {
      appendSystemMsg(`파일 업로드 실패: ${json.error?.message || "오류"}`);
      return;
    }

    if (reloadMode === "none") return;
    if (reloadMode === "thread") {
      if (threadRootMessageId != null) {
        await openThreadModal(threadRootMessageId, { targetCommentMessageId: null });
      }
      if (activeChannelId) {
        await loadMessages(activeChannelId, {
          skipNewMsgsDivider: skipNewMsgsDividerAfterReload,
        });
      }
      return;
    }

    await Promise.all([
      loadMessages(activeChannelId, { skipNewMsgsDivider: skipNewMsgsDividerAfterReload }),
      refreshChannelFileHubData(activeChannelId),
    ]);
  } catch (e) {
    console.warn(e);
    appendSystemMsg("파일 업로드 중 서버 오류");
  } finally {
    setFilePreviewUploadStatus(ctx, "");
    if (sendBtn) sendBtn.disabled = false;
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
  // 조직도 우측 목록의 "제외/추가" 버튼 상태를 즉시 동기화한다.
  renderMemberListRight();
}

document.getElementById("btnConfirmCreateChannel").addEventListener("click", async () => {
  const name     = document.getElementById("newChannelName").value.trim();
  const desc     = document.getElementById("newChannelDesc").value.trim();
  const type     = document.getElementById("newChannelType").value;
  if (!name) { await uiAlert("채널 이름을 입력하세요."); return; }
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
    if (!res.ok) { await uiAlert("채널 생성 실패: " + (json.error?.message || "")); return; }

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
    await uiAlert("채널 생성 중 오류 발생");
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
  if (selectedDmMembers.length === 0) { await uiAlert("대화 상대를 선택하세요."); return; }
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
    if (!res.ok) { await uiAlert("DM 생성 실패: " + (json.error?.message || "")); return; }

    const channelId = json.data?.channelId;

    closeModal("modalCreateDm");
    await loadMyChannels();
    selectChannel(channelId, dmName, "DM");
  } catch {
    await uiAlert("DM 생성 중 오류 발생");
  }
});

function threadHubPreviewFromTimelineItem(item) {
  const fp = tryParseFilePayload({
    text: item.text,
    messageType: item.messageType ?? item.message_type,
  });
  if (fp) return String(fp.originalFilename || "첨부파일").trim() || "첨부파일";
  return mentionPreviewForToastClient(String(item.text || ""), 120);
}

function cacheRootMessageForThreadModal(item) {
  const id = Number(item.messageId ?? item.message_id);
  if (!Number.isFinite(id)) return;
  timelineRootMessageById.set(id, {
    messageId: id,
    senderId: item.senderId ?? item.sender_id,
    senderName: item.senderName ?? item.sender_name,
    text: item.text,
    createdAt: item.createdAt ?? item.created_at,
    messageType: item.messageType ?? item.message_type,
    parentMessageId: null,
  });
}

/** 채널/DM: 스레드 활동이 있는 원글 목록(최근 활동 순) */
async function refreshThreadHubData(channelId) {
  const listEl = document.getElementById("threadHubList");
  const emptyEl = document.getElementById("threadHubEmpty");
  const errEl = document.getElementById("threadHubError");
  if (!currentUser || !listEl) return;
  listEl.innerHTML = "";
  if (emptyEl) emptyEl.classList.add("hidden");
  if (errEl) {
    errEl.classList.add("hidden");
    errEl.textContent = "";
  }
  try {
    const res = await apiFetch(
      `/api/channels/${channelId}/messages/threads?employeeNo=${encodeURIComponent(currentUser.employeeNo)}&limit=50`
    );
    const json = await res.json().catch(() => ({}));
    if (!res.ok) {
      if (errEl) {
        errEl.textContent = json.error?.message || "목록을 불러오지 못했습니다.";
        errEl.classList.remove("hidden");
      }
      return;
    }
    if (Number(channelId) !== Number(activeChannelId)) return;
    const rows = Array.isArray(json.data) ? json.data : [];
    if (rows.length === 0) {
      emptyEl?.classList.remove("hidden");
      return;
    }
    rows.forEach((item) => {
      const rootId = Number(item.messageId ?? item.message_id);
      if (!Number.isFinite(rootId)) return;
      const n = threadCommentCountFromMsg(item);
      const lastAt =
        item.lastCommentAt ??
        item.last_comment_at ??
        item.createdAt ??
        item.created_at;
      const lastName = String(item.lastCommentSenderName ?? item.last_comment_sender_name ?? "").trim();
      const snippet = threadHubPreviewFromTimelineItem(item);
      const author = escHtml(String(item.senderName ?? item.sender_name ?? "").trim() || "?");
      const row = document.createElement("button");
      row.type = "button";
      row.className = "thread-hub-row";
      row.dataset.rootMessageId = String(rootId);
      const timePart = lastAt ? escHtml(fmtTime(lastAt)) : "";
      const actInner = lastName
        ? `${escHtml(lastName)}${timePart ? ` · ${timePart}` : ""}`
        : timePart;
      row.innerHTML = `
        <span class="thread-hub-row-main">
          <span class="thread-hub-row-author">${author}</span>
          <span class="thread-hub-row-snippet">${escHtml(snippet)}</span>
        </span>
        <span class="thread-hub-row-meta">
          <span class="thread-hub-row-count">${n}개의 댓글</span>
          <span class="thread-hub-row-activity">${actInner || "—"}</span>
        </span>`;
      row.addEventListener("click", () => {
        closeModal("modalThreadHub");
        cacheRootMessageForThreadModal(item);
        openThreadModal(rootId);
      });
      listEl.appendChild(row);
    });
  } catch (e) {
    console.error("스레드 목록 로드 실패", e);
    if (errEl) {
      errEl.textContent = "목록을 불러오지 못했습니다.";
      errEl.classList.remove("hidden");
    }
  }
}

/* ==========================================================================
 * 멤버 패널 토글
 * ========================================================================== */
document.getElementById("btnHeaderMenu").addEventListener("click", () => {
  document.getElementById("memberPanel").classList.toggle("hidden");
});
document.getElementById("btnCloseMemberPanel").addEventListener("click", () => {
  document.getElementById("memberPanel").classList.add("hidden");
});
document.getElementById("btnOpenFileHub")?.addEventListener("click", async () => {
  if (!activeChannelId) return;
  await refreshChannelFileHubData(activeChannelId);
  setFileHubTab("all");
  openModal("modalFileHub");
});

document.getElementById("btnOpenImageHub")?.addEventListener("click", async () => {
  if (!activeChannelId) return;
  await refreshChannelFileHubData(activeChannelId);
  setFileHubTab("images");
  openModal("modalFileHub");
});

document.getElementById("btnOpenThreadHub")?.addEventListener("click", async () => {
  if (!activeChannelId) return;
  await refreshThreadHubData(activeChannelId);
  openModal("modalThreadHub");
});

document.querySelectorAll(".file-hub-tab[data-file-hub-tab]").forEach((btn) => {
  btn.addEventListener("click", () => setFileHubTab(btn.dataset.fileHubTab));
});

const WORK_ITEM_STATUS_LABEL = { OPEN: "대기", IN_PROGRESS: "진행 중", DONE: "완료" };

function normalizeWorkStatusLabel(status) {
  const s = String(status || "OPEN").toUpperCase();
  if (s === "IN_PROGRESS") return "IN_PROGRESS";
  if (s === "DONE") return "DONE";
  return "OPEN";
}

/** 업무 허브 UI: 비활성/복원·완전삭제 대기 상태를 반영한 inUse */
function effectiveWorkItemInUseForUi(workItemId) {
  const wid = Number(workItemId);
  if (!wid) return true;
  if (workHubPendingWorkPurgeIds.has(wid)) return false;
  if (workHubPendingWorkRestoreIds.has(wid)) return true;
  const w = lastChannelWorkItemsForHub.find((x) => Number(x.id) === wid);
  return w ? w.inUse !== false : true;
}

function queueWorkItemRestore(workItemId) {
  const id = Number(workItemId);
  if (!id) return;
  workHubPendingWorkRestoreIds.add(id);
  workHubPendingWorkPurgeIds.delete(id);
  workHubPendingWorkDeleteIds.delete(id);
  void loadChannelWorkItems();
  void loadChannelKanbanBoard();
}

function queueWorkItemPurge(workItemId) {
  const id = Number(workItemId);
  if (!id) return;
  workHubPendingWorkPurgeIds.add(id);
  workHubPendingWorkRestoreIds.delete(id);
  workHubPendingWorkDeleteIds.delete(id);
  workHubPendingWorkStatus.delete(id);
  workHubPendingWorkTitle.delete(id);
  workHubPendingWorkDescription.delete(id);
  clearPendingKanbanStateForWorkItem(id);
  removeDraftKanbanCardsForWorkItem(id);
  void loadChannelWorkItems();
  void loadChannelKanbanBoard();
}

function cancelWorkItemPurgePending(workItemId) {
  const id = Number(workItemId);
  if (!id) return;
  workHubPendingWorkPurgeIds.delete(id);
  void loadChannelWorkItems();
  void loadChannelKanbanBoard();
}

function cancelWorkItemRestorePending(workItemId) {
  const id = Number(workItemId);
  if (!id) return;
  workHubPendingWorkRestoreIds.delete(id);
  void loadChannelWorkItems();
  void loadChannelKanbanBoard();
}

/** 소프트 삭제 예정(저장 전) 취소 */
function cancelWorkItemDeletePending(workItemId) {
  const id = Number(workItemId);
  if (!id) return;
  workHubPendingWorkDeleteIds.delete(id);
  void loadChannelWorkItems();
}

/** 칸반 카드 ID 수집: 서버 보드 컬럼 기준(workItem 연결) */
function collectKanbanCardIdsForWorkItem(workItemId) {
  const wi = Number(workItemId);
  if (!Number.isFinite(wi) || wi <= 0) return [];
  const ids = [];
  for (const col of activeWorkHubColumns || []) {
    const cards = Array.isArray(col?.cards) ? col.cards : [];
    for (const c of cards) {
      const cwi = Number(c.workItemId ?? c.work_item_id);
      if (cwi !== wi) continue;
      const cid = Number(c.id);
      if (Number.isFinite(cid)) ids.push(cid);
    }
  }
  return ids;
}

/** 완전 삭제 시 서버에서 카드가 사라지므로, 저장 시 PUT 대상에서 제거한다. */
function clearPendingKanbanStateForCardIds(cardIds) {
  if (!Array.isArray(cardIds) || !cardIds.length) return;
  for (const raw of cardIds) {
    const cid = Number(raw);
    if (!Number.isFinite(cid)) continue;
    workHubPendingCardColumn.delete(cid);
    workHubPendingCardSortOrder.delete(cid);
    workHubPendingCardTitle.delete(cid);
    workHubPendingCardDescription.delete(cid);
    workHubPendingCardDeleteIds.delete(cid);
    workHubPendingCardAssigneeAdd.delete(cid);
    workHubPendingCardAssigneeRemove.delete(cid);
  }
}

function clearPendingKanbanStateForWorkItem(workItemId) {
  clearPendingKanbanStateForCardIds(collectKanbanCardIdsForWorkItem(workItemId));
}

function removeDraftKanbanCardsForWorkItem(workItemId) {
  const wi = Number(workItemId);
  if (!Number.isFinite(wi)) return;
  workHubPendingNewKanbanCards = workHubPendingNewKanbanCards.filter((d) => Number(d.workItemId) !== wi);
}

function applyWorkHubWorkListSelection() {
  const listEl = document.getElementById("channelWorkItemsList");
  if (!listEl || workHubSelectedListWorkItemKey == null) return;
  const key = String(workHubSelectedListWorkItemKey);
  listEl.querySelectorAll(".channel-work-item--selected").forEach((el) => el.classList.remove("channel-work-item--selected"));
  const esc =
    typeof CSS !== "undefined" && typeof CSS.escape === "function"
      ? CSS.escape(key)
      : key.replace(/\\/g, "\\\\").replace(/"/g, '\\"');
  const row = listEl.querySelector(`.channel-work-item[data-work-item-id="${esc}"]`);
  if (row) row.classList.add("channel-work-item--selected");
}

function renderChannelWorkItems(items) {
  const listEl = document.getElementById("channelWorkItemsList");
  if (!listEl) return;
  const savedItems = Array.isArray(items) ? items : [];
  const draftItems = workHubPendingNewWorkItems.map((d, i) => ({
    id: `draft-${i}`,
    title: d.title,
    description: d.description,
    status: d.status || "OPEN",
    _isDraft: true,
    _draftIdx: i,
  }));
  const visibleItems = [...draftItems, ...savedItems];
  if (!visibleItems.length) {
    listEl.innerHTML = `<li class="empty-notice">등록된 업무 항목이 없습니다.</li>`;
    return;
  }
  listEl.innerHTML = "";
  visibleItems.forEach((item) => {
    const li = document.createElement("li");
    const isDraft = item._isDraft === true;
    const id = isDraft ? String(item.id) : Number(item.id);
    const idNum = !isDraft ? Number(item.id) : NaN;
    const pendingPurge = !isDraft && workHubPendingWorkPurgeIds.has(idNum);
    const pendingRestore = !isDraft && workHubPendingWorkRestoreIds.has(idNum);
    const pendingDelete = !isDraft && workHubPendingWorkDeleteIds.has(idNum);
    const serverInactive = !isDraft && item.inUse === false;
    const inactive = !isDraft && !effectiveWorkItemInUseForUi(idNum);
    let rowExtraClass = "";
    if (pendingPurge) rowExtraClass = " channel-work-item--pending-purge";
    else if (pendingRestore) rowExtraClass = " channel-work-item--pending-restore";
    else if (pendingDelete) rowExtraClass = " channel-work-item--pending-delete";
    li.className = inactive
      ? `channel-work-item channel-work-item-inactive${rowExtraClass}`
      : `channel-work-item${rowExtraClass}`;
    li.setAttribute("data-work-item-id", String(id));
    const baseTitle = !isDraft && workHubPendingWorkTitle.has(Number(id))
      ? workHubPendingWorkTitle.get(Number(id))
      : item.title;
    const baseDesc = !isDraft && workHubPendingWorkDescription.has(Number(id))
      ? workHubPendingWorkDescription.get(Number(id))
      : item.description;
    const base = normalizeWorkStatusLabel(item.status);
    const pending = !isDraft ? workHubPendingWorkStatus.get(id) : null;
    const status = pending != null ? normalizeWorkStatusLabel(pending) : base;

    let pendingStrip = "";
    let hubActions = "";
    if (!isDraft) {
      const idAttr = escHtml(String(id));
      if (pendingPurge) {
        pendingStrip = `<div class="channel-work-item-pending-strip" role="status"><span class="work-pending-badge work-pending-badge--purge">완전 삭제 예정 · 저장 시 반영</span></div>`;
        hubActions = `<div class="channel-work-item-hub-actions"><button type="button" class="btn-secondary btn-sm work-item-hub-cancel-purge" data-work-item-id="${idAttr}">완전 삭제 취소</button></div>`;
      } else if (pendingRestore) {
        pendingStrip = `<div class="channel-work-item-pending-strip" role="status"><span class="work-pending-badge work-pending-badge--restore">복원 예정 · 저장 시 반영</span></div>`;
        hubActions = `<div class="channel-work-item-hub-actions"><button type="button" class="btn-secondary btn-sm work-item-hub-cancel-restore" data-work-item-id="${idAttr}">복원 취소</button></div>`;
      } else if (pendingDelete) {
        pendingStrip = `<div class="channel-work-item-pending-strip" role="status"><span class="work-pending-badge work-pending-badge--delete">삭제 예정 · 저장 시 반영</span></div>`;
        hubActions = `<div class="channel-work-item-hub-actions"><button type="button" class="btn-secondary btn-sm work-item-hub-cancel-delete" data-work-item-id="${idAttr}">삭제 취소</button></div>`;
      } else if (serverInactive) {
        hubActions = `<div class="channel-work-item-hub-actions"><button type="button" class="btn-secondary btn-sm work-item-hub-restore" data-work-item-id="${idAttr}">복원</button><button type="button" class="btn-danger btn-sm work-item-hub-purge" data-work-item-id="${idAttr}">완전 삭제</button></div>`;
      }
    }

    li.innerHTML = `
      <div class="channel-work-item-head">
        <strong class="channel-work-item-title">${escHtml(baseTitle || "(제목 없음)")}</strong>
        <div class="channel-work-item-head-actions">
          <select class="work-item-status-select" data-work-item-id="${escHtml(String(id))}" data-is-draft="${isDraft ? "1" : "0"}" aria-label="업무 상태">
            <option value="OPEN" ${status === "OPEN" ? "selected" : ""}>${escHtml(WORK_ITEM_STATUS_LABEL.OPEN)}</option>
            <option value="IN_PROGRESS" ${status === "IN_PROGRESS" ? "selected" : ""}>${escHtml(WORK_ITEM_STATUS_LABEL.IN_PROGRESS)}</option>
            <option value="DONE" ${status === "DONE" ? "selected" : ""}>${escHtml(WORK_ITEM_STATUS_LABEL.DONE)}</option>
          </select>
          <button type="button" class="btn-icon-delete work-item-delete-btn" data-work-item-id="${escHtml(String(id))}" data-is-draft="${isDraft ? "1" : "0"}" title="삭제" aria-label="삭제">✕</button>
        </div>
      </div>
      <div class="channel-work-item-meta">${escHtml(baseDesc || "설명 없음")}</div>
      ${pendingStrip}
      ${hubActions}
    `;
    listEl.appendChild(li);
  });
  applyWorkHubWorkListSelection();
  listEl.querySelectorAll(".work-item-status-select").forEach((sel) => {
    sel.addEventListener("change", () => {
      const isDraft = sel.dataset.isDraft === "1";
      if (isDraft) {
        const raw = String(sel.dataset.workItemId || "");
        const idx = Number(raw.replace("draft-", ""));
        if (!Number.isFinite(idx) || !workHubPendingNewWorkItems[idx]) return;
        workHubPendingNewWorkItems[idx].status = sel.value;
        return;
      }
      const workItemId = Number(sel.dataset.workItemId);
      if (!workItemId) return;
      workHubPendingWorkStatus.set(workItemId, sel.value);
    });
  });
}

function ensureWorkHubWorkListDeleteBound() {
  const listEl = document.getElementById("channelWorkItemsList");
  if (!listEl || workHubWorkListDeleteBound) return;
  workHubWorkListDeleteBound = true;
  listEl.addEventListener("click", async (e) => {
    const cancelPurgeBtn = e.target.closest(".work-item-hub-cancel-purge");
    if (cancelPurgeBtn) {
      e.preventDefault();
      e.stopPropagation();
      cancelWorkItemPurgePending(cancelPurgeBtn.dataset.workItemId);
      return;
    }
    const cancelRestoreBtn = e.target.closest(".work-item-hub-cancel-restore");
    if (cancelRestoreBtn) {
      e.preventDefault();
      e.stopPropagation();
      cancelWorkItemRestorePending(cancelRestoreBtn.dataset.workItemId);
      return;
    }
    const cancelDeleteBtn = e.target.closest(".work-item-hub-cancel-delete");
    if (cancelDeleteBtn) {
      e.preventDefault();
      e.stopPropagation();
      cancelWorkItemDeletePending(cancelDeleteBtn.dataset.workItemId);
      return;
    }
    const hubRestoreBtn = e.target.closest(".work-item-hub-restore");
    if (hubRestoreBtn) {
      e.preventDefault();
      e.stopPropagation();
      queueWorkItemRestore(hubRestoreBtn.dataset.workItemId);
      return;
    }
    const hubPurgeBtn = e.target.closest(".work-item-hub-purge");
    if (hubPurgeBtn) {
      e.preventDefault();
      e.stopPropagation();
      if (!(await uiConfirm("연결된 칸반 카드까지 모두 삭제합니다. 저장 시 서버에 반영됩니다. 계속할까요?"))) return;
      queueWorkItemPurge(hubPurgeBtn.dataset.workItemId);
      return;
    }
    const row = e.target.closest(".channel-work-item");
    if (
      row &&
      !e.target.closest(".work-item-status-select") &&
      !e.target.closest(".work-item-delete-btn") &&
      !e.target.closest(".channel-work-item-hub-actions")
    ) {
      const rawId = String(row.querySelector(".work-item-delete-btn")?.dataset.workItemId || "");
      const isDraft = row.querySelector(".work-item-delete-btn")?.dataset.isDraft === "1";
      workHubSelectedListWorkItemKey = isDraft ? rawId : String(Number(rawId));
      listEl.querySelectorAll(".channel-work-item--selected").forEach((el) => el.classList.remove("channel-work-item--selected"));
      row.classList.add("channel-work-item--selected");
      openWorkItemDetailModal(rawId, isDraft);
      return;
    }
    const del = e.target.closest(".work-item-delete-btn");
    if (!del || !currentUser) return;
    e.preventDefault();
    const isDraft = del.dataset.isDraft === "1";
    const raw = String(del.dataset.workItemId || "");
    const id = isDraft ? raw : Number(raw);
    if (!id) return;
    if (!(await uiConfirm("이 업무 항목을 삭제할까요?"))) return;
    if (isDraft) {
      const idx = Number(raw.replace("draft-", ""));
      if (!Number.isFinite(idx) || !workHubPendingNewWorkItems[idx]) return;
      workHubPendingNewWorkItems.splice(idx, 1);
    } else {
      workHubPendingWorkStatus.delete(Number(id));
      workHubPendingWorkTitle.delete(Number(id));
      workHubPendingWorkDescription.delete(Number(id));
      workHubPendingWorkDeleteIds.add(Number(id));
    }
    await loadChannelWorkItems();
  });
}

function clearWorkHubPendingMaps() {
  workHubPendingWorkStatus.clear();
  workHubPendingWorkTitle.clear();
  workHubPendingWorkDescription.clear();
  workHubPendingCardColumn.clear();
  workHubPendingCardSortOrder.clear();
  workHubPendingCardTitle.clear();
  workHubPendingCardDescription.clear();
  workHubPendingWorkDeleteIds.clear();
  workHubPendingWorkRestoreIds.clear();
  workHubPendingWorkPurgeIds.clear();
  workHubPendingCardDeleteIds.clear();
  workHubPendingCardAssigneeAdd.clear();
  workHubPendingCardAssigneeRemove.clear();
  workHubPendingNewWorkItems = [];
  workHubPendingNewKanbanCards = [];
}

function getEffectiveSavedCardsByColumn(cols) {
  const flat = cols.flatMap((c) => (Array.isArray(c.cards) ? c.cards : []));
  const kept = flat.filter((card) => !workHubPendingCardDeleteIds.has(Number(card.id)));
  return kept.map((card) => {
    const id = Number(card.id);
    return {
      ...card,
      _effectiveColumnId: Number(workHubPendingCardColumn.get(id) ?? card.columnId),
      _effectiveSortOrder: Number(workHubPendingCardSortOrder.get(id) ?? card.sortOrder ?? 0),
    };
  });
}

/** Map kanban column → card `status` (API). Default boards: sortOrder 0/1/2 → OPEN / IN_PROGRESS / DONE. */
function statusForKanbanColumnId(columnId) {
  if (!columnId || !Array.isArray(activeWorkHubColumns) || !activeWorkHubColumns.length) return "OPEN";
  const sorted = [...activeWorkHubColumns].sort(
    (a, b) => Number(a.sortOrder ?? 0) - Number(b.sortOrder ?? 0)
  );
  const idx = sorted.findIndex((c) => Number(c.id) === Number(columnId));
  if (idx === 0) return "OPEN";
  if (idx === 1) return "IN_PROGRESS";
  if (idx === 2) return "DONE";
  const col = sorted[idx];
  if (!col) return "OPEN";
  const name = String(col.name || "").toLowerCase();
  if (name.includes("완료") || name.includes("done")) return "DONE";
  if (name.includes("진행") || name.includes("progress") || name.includes("in_progress")) return "IN_PROGRESS";
  return "OPEN";
}

/**
 * Linked work-item row uses `workHubPendingWorkStatus` on save. If only the card column moves (DnD/select),
 * the work item can stay stale (e.g. IN_PROGRESS) and overwrite user intent. Align pending work status to column.
 */
function syncPendingWorkItemStatusFromKanbanColumn(workItemId, columnId) {
  const wi = Number(workItemId);
  const col = Number(columnId);
  if (!Number.isFinite(wi) || wi <= 0 || !Number.isFinite(col) || col <= 0) return;
  workHubPendingWorkStatus.set(wi, statusForKanbanColumnId(col));
}

/** Persist saved-card column + order for the affected columns only (reduces pending scope). */
function syncKanbanBoardPartial(boardEl, columnIds) {
  const idSet = new Set(columnIds.map(Number).filter((x) => x > 0));
  if (!boardEl || !idSet.size) return;
  const columns = [...boardEl.querySelectorAll(".kanban-column")].filter((colEl) =>
    idSet.has(Number(colEl.dataset.columnId || 0))
  );
  columns.forEach((colEl) => {
    const colId = Number(colEl.dataset.columnId || 0);
    if (!colId) return;
    const listEl = colEl.querySelector(".kanban-card-list");
    if (!listEl) return;
    [...listEl.querySelectorAll(".kanban-card-item")].forEach((cardEl, i) => {
      const del = cardEl.querySelector(".kanban-card-delete-btn");
      if (!del || del.dataset.isDraft === "1") return;
      const cid = Number(del.dataset.cardId || "");
      if (Number.isFinite(cid)) {
        workHubPendingCardColumn.set(cid, colId);
        workHubPendingCardSortOrder.set(cid, i);
        cardEl.dataset.renderColumnId = String(colId);
        const wi = Number(cardEl.dataset.workItemId || "");
        syncPendingWorkItemStatusFromKanbanColumn(wi, colId);
      }
    });
  });
}

/** After DnD, reconcile every column from DOM so pending maps and row `<select>` match the column the card sits in. */
function syncKanbanBoardFromDomFull(boardEl) {
  if (!boardEl) return;
  const ids = [...boardEl.querySelectorAll(".kanban-column")]
    .map((c) => Number(c.dataset.columnId || 0))
    .filter((x) => x > 0);
  syncKanbanBoardPartial(boardEl, ids);
}

function isEditableDropTarget(target) {
  const el = target instanceof Element ? target : null;
  if (!el) return false;
  const hit = el.closest('input, textarea, select, [contenteditable=""], [contenteditable="true"], [contenteditable="plaintext-only"]');
  return !!hit;
}

/** Prevent accidental drop of a kanban card onto search inputs / textareas (would insert drag text). */
function ensureKanbanDragInputGuard() {
  if (ensureKanbanDragInputGuard._done) return;
  ensureKanbanDragInputGuard._done = true;
  const hub = document.getElementById("modalWorkHub");
  if (!hub) return;
  const isKanbanCardDrag = (ev) => {
    const types = ev.dataTransfer?.types;
    if (!types) return false;
    if (typeof types.contains === "function") return types.contains("application/x-ech-kanban-card");
    return Array.from(types).includes("application/x-ech-kanban-card");
  };
  hub.addEventListener(
    "dragover",
    (ev) => {
      if (!ev.target.closest("#channelKanbanBoard")) return;
      if (
        isEditableDropTarget(ev.target) &&
        (isKanbanCardDrag(ev) || document.querySelector("#channelKanbanBoard .kanban-card-dragging"))
      ) {
        ev.preventDefault();
        try {
          ev.dataTransfer.dropEffect = "none";
        } catch (_) {
          /* ignore */
        }
      }
    },
    true
  );
  hub.addEventListener(
    "drop",
    (ev) => {
      if (!ev.target.closest("#channelKanbanBoard")) return;
      if (
        isEditableDropTarget(ev.target) &&
        (isKanbanCardDrag(ev) || document.querySelector("#channelKanbanBoard .kanban-card-dragging"))
      ) {
        ev.preventDefault();
        ev.stopPropagation();
      }
    },
    true
  );
}

/** Global safety: if pointer misses board and lands on any editable control, do not insert drag payload text. */
if (!window._echKanbanGlobalDropGuard) {
  window._echKanbanGlobalDropGuard = true;
  const isKanbanDragActive = () =>
    !!document.querySelector("#channelKanbanBoard .kanban-card-dragging");
  document.addEventListener(
    "dragover",
    (ev) => {
      if (!isKanbanDragActive()) return;
      if (!isEditableDropTarget(ev.target)) return;
      ev.preventDefault();
      try {
        ev.dataTransfer.dropEffect = "none";
      } catch (_) {
        /* ignore */
      }
    },
    true
  );
  document.addEventListener(
    "drop",
    (ev) => {
      if (!isKanbanDragActive()) return;
      if (!isEditableDropTarget(ev.target)) return;
      ev.preventDefault();
      ev.stopPropagation();
    },
    true
  );
}

/**
 * After DnD, some browsers keep the previous `selectedIndex` on a moved `<select>`; assigning
 * `sel.value` can also no-op if types/spaces differ. Force selection by option index.
 */
function applyKanbanColumnSelectToColumnId(sel, columnIdNum) {
  if (!sel || !Number.isFinite(columnIdNum) || columnIdNum <= 0) return false;
  const want = String(Number(columnIdNum));
  const opts = sel.options;
  for (let i = 0; i < opts.length; i++) {
    if (String(opts[i].value) === want) {
      sel.selectedIndex = i;
      return true;
    }
  }
  try {
    sel.value = want;
  } catch (_) {
    /* ignore */
  }
  return String(sel.value) === want;
}

/** One delegated listener survives `<select>` replacement after DnD (avoids stale listeners on replaced nodes). */
function ensureKanbanBoardColumnSelectChangeDelegated(boardEl) {
  if (!boardEl || boardEl.dataset.echKanbanColSelectDelegated === "1") return;
  boardEl.dataset.echKanbanColSelectDelegated = "1";
  boardEl.addEventListener("change", (e) => {
    const sel = e.target.closest(".kanban-card-column-select");
    if (!sel || !boardEl.contains(sel)) return;
    const isDraftLocal = sel.dataset.isDraft === "1";
    const raw = String(sel.dataset.cardId || "");
    const targetColumnId = Number(sel.value);
    if (!targetColumnId) return;
    const bumpCid = getWorkHubChannelId();
    if (bumpCid) bumpKanbanBoardFetchGeneration(bumpCid);
    if (isDraftLocal) {
      const idx = Number(raw.replace("draft-card-", ""));
      const d = Number.isFinite(idx) ? workHubPendingNewKanbanCards[idx] : null;
      if (!d) return;
      d.columnId = targetColumnId;
      syncPendingWorkItemStatusFromKanbanColumn(d.workItemId, targetColumnId);
    } else {
      const cid = Number(raw);
      if (!cid) return;
      workHubPendingCardColumn.set(cid, targetColumnId);
      const wi = Number(sel.closest(".kanban-card-item")?.dataset.workItemId || "");
      syncPendingWorkItemStatusFromKanbanColumn(wi, targetColumnId);
    }
    void Promise.all([loadChannelKanbanBoard(), loadChannelWorkItems()]);
  });
}

/**
 * Replace each column `<select>` with a fresh element after DnD so Chrome does not keep stale UI state
 * when the card lived under a `draggable` ancestor (whole-card drag UX).
 */
function rebuildKanbanCardColumnSelectDom(boardEl) {
  if (!boardEl || !Array.isArray(activeWorkHubColumns) || !activeWorkHubColumns.length) return;
  boardEl.querySelectorAll(".kanban-card-item").forEach((cardEl) => {
    const row = cardEl.querySelector(".kanban-card-move-row");
    const del = cardEl.querySelector(".kanban-card-delete-btn");
    const oldSel = cardEl.querySelector(".kanban-card-column-select");
    if (!row || !del || !oldSel) return;
    const colId = Number(cardEl.closest(".kanban-column")?.dataset.columnId || 0);
    if (!colId) return;
    const rawId = String(oldSel.dataset.cardId || "");
    const isDraft = oldSel.dataset.isDraft === "1";
    const newSel = document.createElement("select");
    newSel.className = "kanban-card-column-select";
    newSel.dataset.cardId = rawId;
    newSel.dataset.isDraft = isDraft ? "1" : "0";
    for (const c of activeWorkHubColumns) {
      const opt = document.createElement("option");
      opt.value = String(Number(c.id));
      opt.textContent = String(c.name || "컬럼");
      newSel.appendChild(opt);
    }
    applyKanbanColumnSelectToColumnId(newSel, colId);
    oldSel.replaceWith(newSel);
    cardEl.dataset.renderColumnId = String(colId);
    if (isDraft === "1") {
      const idx = Number(rawId.replace("draft-card-", ""));
      const d = Number.isFinite(idx) ? workHubPendingNewKanbanCards[idx] : null;
      if (d) syncPendingWorkItemStatusFromKanbanColumn(d.workItemId, colId);
    } else {
      const cid = Number(del.dataset.cardId || "");
      if (Number.isFinite(cid)) {
        workHubPendingCardColumn.set(cid, colId);
        const wi = Number(cardEl.dataset.workItemId || "");
        syncPendingWorkItemStatusFromKanbanColumn(wi, colId);
      }
    }
  });
}

/** Keep per-card column dropdown in sync with the `.kanban-column` that contains the card (avoids stale <select> after DOM moves). */
function syncKanbanCardColumnSelectsFromDom(boardEl) {
  if (!boardEl) return;
  boardEl.querySelectorAll(".kanban-card-item").forEach((cardEl) => {
    const del = cardEl.querySelector(".kanban-card-delete-btn");
    const sel = cardEl.querySelector(".kanban-card-column-select");
    if (!del || !sel || del.dataset.isDraft === "1") return;
    const colFromCard = Number(cardEl.closest(".kanban-column")?.dataset.columnId || 0);
    const colFromSel = Number(sel.closest(".kanban-column")?.dataset.columnId || 0);
    const colId = colFromCard > 0 ? colFromCard : colFromSel;
    if (!colId) return;
    cardEl.dataset.renderColumnId = String(colId);
    applyKanbanColumnSelectToColumnId(sel, colId);
    const cid = Number(del.dataset.cardId || "");
    if (Number.isFinite(cid)) {
      workHubPendingCardColumn.set(cid, colId);
      const wi = Number(cardEl.dataset.workItemId || "");
      syncPendingWorkItemStatusFromKanbanColumn(wi, colId);
    }
  });
}

/** Rebuild draft queue order and column ids from full board DOM (draft-card-* indices). */
function syncKanbanDraftsOrderFromDom(boardEl) {
  if (!boardEl || !workHubPendingNewKanbanCards.length) return;
  const draftEntries = [];
  boardEl.querySelectorAll(".kanban-column").forEach((colEl) => {
    const colId = Number(colEl.dataset.columnId || 0);
    if (!colId) return;
    const listEl = colEl.querySelector(".kanban-card-list");
    if (!listEl) return;
    [...listEl.querySelectorAll(".kanban-card-item")].forEach((cardEl) => {
      const del = cardEl.querySelector(".kanban-card-delete-btn");
      if (del?.dataset.isDraft === "1") {
        const idx = Number(String(del.dataset.cardId || "").replace("draft-card-", ""));
        if (Number.isFinite(idx) && workHubPendingNewKanbanCards[idx]) {
          draftEntries.push({ idx, colId });
        }
      }
    });
  });
  if (!draftEntries.length) return;
  workHubPendingNewKanbanCards = draftEntries
    .map(({ idx, colId }) => {
      const d = workHubPendingNewKanbanCards[idx];
      if (!d) return null;
      syncPendingWorkItemStatusFromKanbanColumn(d.workItemId, colId);
      return { ...d, columnId: colId };
    })
    .filter(Boolean);
}

async function flushWorkHubSave() {
  const hubCh = getWorkHubChannelId();
  if (!hubCh || !currentUser) return false;
  try {
    const hasPending =
      workHubPendingWorkStatus.size > 0 ||
      workHubPendingWorkTitle.size > 0 ||
      workHubPendingWorkDescription.size > 0 ||
      workHubPendingWorkRestoreIds.size > 0 ||
      workHubPendingWorkPurgeIds.size > 0 ||
      workHubPendingCardColumn.size > 0 ||
      workHubPendingCardSortOrder.size > 0 ||
      workHubPendingWorkDeleteIds.size > 0 ||
      workHubPendingCardDeleteIds.size > 0 ||
      workHubPendingCardAssigneeAdd.size > 0 ||
      workHubPendingCardAssigneeRemove.size > 0 ||
      workHubPendingNewWorkItems.length > 0 ||
      workHubPendingNewKanbanCards.length > 0;
    if (!hasPending) {
      await uiAlert("저장할 변경 사항이 없습니다.");
      return false;
    }

    for (const draft of workHubPendingNewWorkItems) {
      const res = await apiFetch(`/api/channels/${hubCh}/work-items`, {
        method: "POST",
        body: JSON.stringify({
          createdByEmployeeNo: currentUser.employeeNo,
          title: draft.title,
          description: draft.description,
          status: draft.status || "OPEN",
        }),
      });
      const j = await res.json().catch(() => ({}));
      if (!res.ok) {
        throw new Error(j.error?.message || "업무 생성에 실패했습니다.");
      }
    }
    workHubPendingNewWorkItems = [];

    for (const rid of [...workHubPendingWorkRestoreIds]) {
      const res = await apiFetch(
        `/api/work-items/${Number(rid)}/restore?actorEmployeeNo=${encodeURIComponent(currentUser.employeeNo)}`,
        { method: "POST" }
      );
      const j = await res.json().catch(() => ({}));
      if (!res.ok) {
        throw new Error(j.error?.message || `업무 복원에 실패했습니다. (${rid})`);
      }
    }
    workHubPendingWorkRestoreIds.clear();

    const pendingWorkIds = new Set([
      ...Array.from(workHubPendingWorkStatus.keys()),
      ...Array.from(workHubPendingWorkTitle.keys()),
      ...Array.from(workHubPendingWorkDescription.keys()),
    ]);
    for (const workItemIdRaw of pendingWorkIds.values()) {
      const workItemId = Number(workItemIdRaw);
      if (workHubPendingWorkDeleteIds.has(Number(workItemId))) continue;
      if (workHubPendingWorkPurgeIds.has(Number(workItemId))) continue;
      const status = workHubPendingWorkStatus.get(workItemId);
      const title = workHubPendingWorkTitle.get(workItemId);
      const description = workHubPendingWorkDescription.get(workItemId);
      const res = await apiFetch(`/api/work-items/${Number(workItemId)}`, {
        method: "PUT",
        body: JSON.stringify({
          actorEmployeeNo: currentUser.employeeNo,
          ...(status != null ? { status } : {}),
          ...(title != null ? { title } : {}),
          ...(description != null ? { description } : {}),
        }),
      });
      const j = await res.json().catch(() => ({}));
      if (!res.ok) {
        throw new Error(j.error?.message || `업무 상태 저장에 실패했습니다. (${workItemId})`);
      }
    }
    workHubPendingWorkStatus.clear();
    workHubPendingWorkTitle.clear();
    workHubPendingWorkDescription.clear();
    for (const workItemId of workHubPendingWorkDeleteIds.values()) {
      if (workHubPendingWorkPurgeIds.has(Number(workItemId))) continue;
      const res = await apiFetch(
        `/api/work-items/${Number(workItemId)}?actorEmployeeNo=${encodeURIComponent(currentUser.employeeNo)}`,
        { method: "DELETE" }
      );
      const j = await res.json().catch(() => ({}));
      if (!res.ok) {
        throw new Error(j.error?.message || `업무 삭제에 실패했습니다. (${workItemId})`);
      }
    }
    workHubPendingWorkDeleteIds.clear();

    const purgeIdsSnapshot = [...workHubPendingWorkPurgeIds];
    const cardIdsBeforePurgeByWork = new Map();
    for (const wid of purgeIdsSnapshot) {
      cardIdsBeforePurgeByWork.set(Number(wid), collectKanbanCardIdsForWorkItem(wid));
    }
    for (const workItemId of purgeIdsSnapshot) {
      const res = await apiFetch(
        `/api/work-items/${Number(workItemId)}?actorEmployeeNo=${encodeURIComponent(currentUser.employeeNo)}&hard=true`,
        { method: "DELETE" }
      );
      const j = await res.json().catch(() => ({}));
      if (!res.ok) {
        throw new Error(j.error?.message || `업무 완전 삭제에 실패했습니다. (${workItemId})`);
      }
    }
    for (const wid of purgeIdsSnapshot) {
      const cids = cardIdsBeforePurgeByWork.get(Number(wid));
      clearPendingKanbanStateForCardIds(Array.isArray(cids) ? cids : []);
      removeDraftKanbanCardsForWorkItem(wid);
    }
    workHubPendingWorkPurgeIds.clear();

    for (const draft of workHubPendingNewKanbanCards) {
      const targetColumnId = Number(draft.columnId || activeWorkHubFirstColumnId);
      if (!activeWorkHubBoardId || !targetColumnId) {
        throw new Error("칸반 보드를 불러온 뒤에 카드를 추가할 수 있습니다.");
      }
      const res = await apiFetch(
        `/api/kanban/boards/${activeWorkHubBoardId}/columns/${targetColumnId}/cards`,
        {
          method: "POST",
          body: JSON.stringify({
            actorEmployeeNo: currentUser.employeeNo,
            workItemId: Number(draft.workItemId),
            title: draft.title,
            description: draft.description,
            status: statusForKanbanColumnId(targetColumnId),
            assigneeEmployeeNos: Array.isArray(draft.assigneeEmployeeNos) ? draft.assigneeEmployeeNos : [],
          }),
        }
      );
      const j = await res.json().catch(() => ({}));
      if (!res.ok) {
        throw new Error(j.error?.message || "카드 생성에 실패했습니다.");
      }
    }
    workHubPendingNewKanbanCards = [];

    const pendingCardIds = new Set([
      ...Array.from(workHubPendingCardColumn.keys()),
      ...Array.from(workHubPendingCardSortOrder.keys()),
      ...Array.from(workHubPendingCardTitle.keys()),
      ...Array.from(workHubPendingCardDescription.keys()),
    ]);
    for (const cardIdStr of pendingCardIds.values()) {
      const cardId = Number(cardIdStr);
      if (workHubPendingCardDeleteIds.has(cardId)) continue;
      const pendingColumnId = workHubPendingCardColumn.get(cardId);
      const pendingSortOrder = workHubPendingCardSortOrder.get(cardId);
      const pendingTitle = workHubPendingCardTitle.get(cardId);
      const pendingDescription = workHubPendingCardDescription.get(cardId);
      if (pendingColumnId == null && pendingSortOrder == null && pendingTitle == null && pendingDescription == null) continue;
      const res = await apiFetch(`/api/kanban/cards/${cardId}`, {
        method: "PUT",
        body: JSON.stringify({
          actorEmployeeNo: currentUser.employeeNo,
          ...(pendingColumnId != null
            ? { columnId: Number(pendingColumnId), status: statusForKanbanColumnId(Number(pendingColumnId)) }
            : {}),
          ...(pendingSortOrder != null ? { sortOrder: Number(pendingSortOrder) } : {}),
          ...(pendingTitle != null ? { title: pendingTitle } : {}),
          ...(pendingDescription != null ? { description: pendingDescription } : {}),
        }),
      });
      const j = await res.json().catch(() => ({}));
      if (!res.ok) {
        throw new Error(j.error?.message || `카드 이동에 실패했습니다. (${cardId})`);
      }
    }
    workHubPendingCardColumn.clear();
    workHubPendingCardSortOrder.clear();
    workHubPendingCardTitle.clear();
    workHubPendingCardDescription.clear();
    for (const cardId of workHubPendingCardDeleteIds.values()) {
      const res = await apiFetch(
        `/api/kanban/cards/${Number(cardId)}?actorEmployeeNo=${encodeURIComponent(currentUser.employeeNo)}`,
        { method: "DELETE" }
      );
      const j = await res.json().catch(() => ({}));
      if (!res.ok) {
        throw new Error(j.error?.message || `카드 삭제에 실패했습니다. (${cardId})`);
      }
    }
    workHubPendingCardDeleteIds.clear();

    // 담당자 변경은 저장 직전에 보드 최신 상태를 다시 받아 기준을 맞춘다.
    // (작업 중 렌더 상태와 서버 상태가 어긋나도 해제/추가 diff가 안정적으로 계산되게 함)
    if (workHubPendingCardAssigneeAdd.size > 0 || workHubPendingCardAssigneeRemove.size > 0) {
      await loadChannelKanbanBoard();
    }
    const normalizedAssigneeOps = normalizePendingCardAssigneeOps();
    for (const [cardId, empSet] of normalizedAssigneeOps.remove.entries()) {
      for (const emp of empSet.values()) {
        const res = await apiFetch(
          `/api/kanban/cards/${Number(cardId)}/assignees/${encodeURIComponent(emp)}?actorEmployeeNo=${encodeURIComponent(currentUser.employeeNo)}`,
          { method: "DELETE" }
        );
        const j = await res.json().catch(() => ({}));
        if (!res.ok) {
          throw new Error(j.error?.message || `담당 해제 저장에 실패했습니다. (${cardId})`);
        }
      }
    }
    workHubPendingCardAssigneeRemove.clear();

    for (const [cardId, empSet] of normalizedAssigneeOps.add.entries()) {
      for (const emp of empSet.values()) {
        const res = await apiFetch(`/api/kanban/cards/${Number(cardId)}/assignees`, {
          method: "POST",
          body: JSON.stringify({
            actorEmployeeNo: currentUser.employeeNo,
            assigneeEmployeeNo: emp,
          }),
        });
        const j = await res.json().catch(() => ({}));
        if (!res.ok) {
          throw new Error(j.error?.message || `담당 추가 저장에 실패했습니다. (${cardId})`);
        }
      }
    }
    workHubPendingCardAssigneeAdd.clear();

    await Promise.all([loadChannelWorkItems(), loadChannelKanbanBoard()]);
    scheduleRefreshMyChannels();
    return true;
  } catch (e) {
    await uiAlert(e?.message || "저장에 실패했습니다.");
    await Promise.all([loadChannelWorkItems(), loadChannelKanbanBoard()]).catch(() => {});
    return false;
  }
}

/** Kanban assignee employeeNo: trim; matching is case-insensitive (API vs DOM can differ slightly). */
function normKanbanEmpNo(e) {
  return String(e ?? "").trim();
}

function kanbanEmpMatchKey(e) {
  const s = normKanbanEmpNo(e);
  return s ? s.toLowerCase() : "";
}

/** Map matchKey -> canonical employeeNo as returned by API (for correct DELETE URL). */
function kanbanAssigneeCanonMapFromList(list) {
  const m = new Map();
  if (!Array.isArray(list)) return m;
  for (const x of list) {
    const canon = normKanbanEmpNo(x);
    const k = kanbanEmpMatchKey(canon);
    if (!k) continue;
    m.set(k, canon);
  }
  return m;
}

function kanbanPendingRemovesHas(remSet, assigneeEmp) {
  if (!remSet || !remSet.size) return false;
  const t = kanbanEmpMatchKey(assigneeEmp);
  if (!t) return false;
  for (const r of remSet) {
    if (kanbanEmpMatchKey(r) === t) return true;
  }
  return false;
}

function kanbanEmpListHas(list, emp) {
  const k = kanbanEmpMatchKey(emp);
  if (!k) return false;
  return list.some((x) => kanbanEmpMatchKey(x) === k);
}

function kanbanDeleteFromEmpSet(set, emp) {
  if (!set || !set.size) return;
  const t = kanbanEmpMatchKey(emp);
  if (!t) return;
  for (const x of [...set]) {
    if (kanbanEmpMatchKey(x) === t) set.delete(x);
  }
}

/** Pending assignee map may use numeric or string keys; normalize lookup by card id. */
function kanbanPendingAssigneeMapGet(map, cardId) {
  const n = Number(cardId);
  if (!Number.isFinite(n)) return undefined;
  if (map.has(n)) return map.get(n);
  for (const k of map.keys()) {
    if (Number(k) === n) return map.get(k);
  }
  return undefined;
}

function findWorkHubKanbanCardById(cardId) {
  const id = Number(cardId);
  if (!Number.isFinite(id)) return null;
  for (const col of activeWorkHubColumns) {
    const cards = Array.isArray(col?.cards) ? col.cards : [];
    const found = cards.find((c) => {
      const cid = c?.id ?? c?.cardId;
      if (cid == null) return false;
      return Number(cid) === id;
    });
    if (found) return found;
  }
  return null;
}

function normalizePendingCardAssigneeOps() {
  const addByCard = new Map();
  const removeByCard = new Map();
  const snapAdd = new Map(
    [...workHubPendingCardAssigneeAdd.entries()].map(([cid, s]) => [cid, new Set([...s].map(normKanbanEmpNo).filter(Boolean))])
  );
  const snapRem = new Map(
    [...workHubPendingCardAssigneeRemove.entries()].map(([cid, s]) => [cid, new Set([...s].map(normKanbanEmpNo).filter(Boolean))])
  );
  const cardIds = new Set([
    ...Array.from(snapAdd.keys()).map((x) => Number(x)),
    ...Array.from(snapRem.keys()).map((x) => Number(x)),
  ]);
  for (const cardId of cardIds.values()) {
    if (!Number.isFinite(cardId) || workHubPendingCardDeleteIds.has(cardId)) continue;
    const cardObj = findWorkHubKanbanCardById(cardId);
    const base = cardObj && Array.isArray(cardObj.assigneeEmployeeNos) ? cardObj.assigneeEmployeeNos : [];
    const baseMap = kanbanAssigneeCanonMapFromList(base);
    const nextMap = new Map(baseMap);
    const remPending = kanbanPendingAssigneeMapGet(snapRem, cardId);
    if (remPending) {
      for (const r of remPending) {
        const k = kanbanEmpMatchKey(r);
        if (k) nextMap.delete(k);
      }
    }
    const addPending = kanbanPendingAssigneeMapGet(snapAdd, cardId);
    if (addPending) {
      for (const a of addPending) {
        const canon = normKanbanEmpNo(a);
        const k = kanbanEmpMatchKey(canon);
        if (k) nextMap.set(k, canon);
      }
    }
    const removeFinal = new Set();
    for (const [k, canonB] of baseMap) {
      if (!nextMap.has(k)) removeFinal.add(canonB);
    }
    if (!removeFinal.size && remPending?.size && !cardObj) {
      for (const r of remPending) {
        const c = normKanbanEmpNo(r);
        if (c) removeFinal.add(c);
      }
    }
    const addFinal = new Set();
    for (const [k, canonN] of nextMap) {
      if (!baseMap.has(k)) addFinal.add(canonN);
    }
    if (addFinal.size) addByCard.set(cardId, addFinal);
    if (removeFinal.size) removeByCard.set(cardId, removeFinal);
  }
  workHubPendingCardAssigneeAdd = addByCard;
  workHubPendingCardAssigneeRemove = removeByCard;
  return { add: addByCard, remove: removeByCard };
}

async function loadChannelWorkItems() {
  const cid = getWorkHubChannelId();
  if (!cid || !currentUser) return;
  const res = await apiFetch(
    `/api/channels/${cid}/work-items?employeeNo=${encodeURIComponent(currentUser.employeeNo)}&limit=50`
  );
  const json = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error(json.error?.message || "업무 목록 조회 실패");
  }
  lastChannelWorkItemsForHub = Array.isArray(json.data) ? json.data : [];
  renderChannelWorkItems(lastChannelWorkItemsForHub);
  populateKanbanNewCardWorkItemSelect();
}

function populateKanbanNewCardWorkItemSelect() {
  const sel = document.getElementById("kanbanNewCardWorkItemSelect");
  if (!sel) return;
  const list = lastChannelWorkItemsForHub.filter((w) => {
    const wid = Number(w.id);
    if (workHubPendingWorkDeleteIds.has(wid)) return false;
    return effectiveWorkItemInUseForUi(wid);
  });
  sel.innerHTML =
    `<option value="">업무 항목 선택(필수)</option>` +
    list
      .map((w) => `<option value="${Number(w.id)}">${escHtml(String(w.title || "(제목 없음)"))}</option>`)
      .join("");
}

function workItemTitleForKanbanCard(workItemId) {
  const wid = Number(workItemId);
  if (!Number.isFinite(wid)) return "";
  const w = lastChannelWorkItemsForHub.find((x) => Number(x.id) === wid);
  return w ? String(w.title || "").trim() || `업무 #${wid}` : `업무 #${wid}`;
}

async function getUserDisplayNameForKanban(employeeNo) {
  const emp = String(employeeNo || "").trim();
  if (!emp) return "";
  if (kanbanAssigneeNameCache[emp]) return kanbanAssigneeNameCache[emp];
  try {
    const res = await apiFetch(`/api/users/profile?employeeNo=${encodeURIComponent(emp)}`);
    const json = await res.json().catch(() => ({}));
    const name = res.ok && json.data?.name ? String(json.data.name).trim() : "";
    if (name) {
      kanbanAssigneeNameCache[emp] = name;
      return name;
    }
  } catch {
    /* ignore */
  }
  kanbanAssigneeNameCache[emp] = emp;
  return emp;
}

async function enrichKanbanAssigneeLabels(boardEl) {
  const labels = boardEl.querySelectorAll(".kanban-assignee-label[data-emp]");
  await Promise.all(
    Array.from(labels).map(async (el) => {
      const emp = el.getAttribute("data-emp");
      const name = await getUserDisplayNameForKanban(emp);
      el.textContent = name;
    })
  );
}

async function loadWorkHubChannelMembersForAssignee() {
  workHubChannelMembersForAssignee = [];
  const cid = getWorkHubChannelId();
  if (!cid || !currentUser) return;
  const res = await apiFetch(`/api/channels/${cid}`);
  const json = await res.json().catch(() => ({}));
  if (!res.ok) return;
  workHubChannelMembersForAssignee = Array.isArray(json.data?.members) ? json.data.members : [];
}

function filterChannelMembersForAssigneeKeyword(keyword) {
  const q = String(keyword || "").trim().toLowerCase();
  let list = workHubChannelMembersForAssignee.filter((m) => {
    const emp = String(m.employeeNo || "").trim();
    return !!emp;
  });
  if (q) {
    list = list.filter((m) => {
      const name = String(m.name || "").toLowerCase();
      const emp = String(m.employeeNo || "").toLowerCase();
      const dept = String(m.department || "").toLowerCase();
      return name.includes(q) || emp.includes(q) || dept.includes(q);
    });
  }
  return list.slice(0, 40);
}

async function fetchUsersForKanbanAssigneeSuggest(keyword) {
  if (!workHubChannelMembersForAssignee.length && getWorkHubChannelId()) {
    await loadWorkHubChannelMembersForAssignee();
  }
  return filterChannelMembersForAssigneeKeyword(keyword);
}

function resetKanbanSuggestActive(ul) {
  if (!ul) return;
  ul.querySelectorAll(".kanban-suggest-active").forEach((el) => el.classList.remove("kanban-suggest-active"));
  ul._kanbanActiveIdx = -1;
}

/** 디바운스된 suggest가 ⌄ 키 직후 실행되며 목록을 다시 그려 하이라이트가 지워지는 것을 막음 */
function clearKanbanAssigneeSuggestDebounceTimers(input) {
  if (!input) return;
  clearTimeout(input._assignSuggestTimer);
  clearTimeout(input._newCardSuggestT);
  clearTimeout(input._detailAssignTimer);
}

function bindKanbanAssigneeSuggestKeyboard(modal) {
  if (!modal || modal.dataset.kanbanSuggestKb) return;
  modal.dataset.kanbanSuggestKb = "1";
  modal.addEventListener("keydown", (e) => {
    const input = e.target.closest(".kanban-assignee-search, #kanbanNewCardAssigneeSearch, #kanbanCardDetailAssigneeSearch");
    if (!input) return;
    const ul = input.closest(".kanban-assignee-add")?.querySelector(".kanban-assignee-suggest");
    if (!ul || ul.classList.contains("hidden")) return;
    const buttons = [
      ...ul.querySelectorAll(
        "button.kanban-assignee-pick, button.kanban-assignee-pick-new, button.kanban-detail-assignee-pick"
      ),
    ];
    if (buttons.length === 0) return;
    let idx = typeof ul._kanbanActiveIdx === "number" ? ul._kanbanActiveIdx : -1;
    if (e.key === "ArrowDown") {
      e.preventDefault();
      clearKanbanAssigneeSuggestDebounceTimers(input);
      if (idx >= 0) buttons[idx].classList.remove("kanban-suggest-active");
      idx = idx < buttons.length - 1 ? idx + 1 : 0;
      ul._kanbanActiveIdx = idx;
      buttons[idx].classList.add("kanban-suggest-active");
      buttons[idx].scrollIntoView({ block: "nearest" });
      return;
    }
    if (e.key === "ArrowUp") {
      e.preventDefault();
      clearKanbanAssigneeSuggestDebounceTimers(input);
      if (idx >= 0) buttons[idx].classList.remove("kanban-suggest-active");
      idx = idx > 0 ? idx - 1 : buttons.length - 1;
      ul._kanbanActiveIdx = idx;
      buttons[idx].classList.add("kanban-suggest-active");
      buttons[idx].scrollIntoView({ block: "nearest" });
      return;
    }
    if (e.key === "Enter" && idx >= 0) {
      e.preventDefault();
      clearKanbanAssigneeSuggestDebounceTimers(input);
      buttons[idx].click();
      return;
    }
    if (e.key === "Enter" && idx < 0) {
      e.preventDefault();
      return;
    }
    if (e.key === "Escape") {
      ul.classList.add("hidden");
      ul.innerHTML = "";
      resetKanbanSuggestActive(ul);
    }
  });
}

function bindModalWorkHubKanbanSuggestKeyboard() {
  bindKanbanAssigneeSuggestKeyboard(document.getElementById("modalWorkHub"));
  bindKanbanAssigneeSuggestKeyboard(document.getElementById("modalKanbanCardDetail"));
}

async function runKanbanAssigneeSuggest(input, ul) {
  const q = String(input.value || "").trim();
  if (!q) {
    ul.classList.add("hidden");
    ul.innerHTML = "";
    return;
  }
  const cardIdRaw = String(input.dataset.cardId || "");
  const isDraft = input.dataset.isDraft === "1";
  const cardId = isDraft ? cardIdRaw : Number(cardIdRaw);
  const assignedRaw = String(input.dataset.assignedEmp || "");
  const assigned = new Set(assignedRaw.split("|").map((s) => s.trim()).filter(Boolean));
  if (!currentUser || !cardIdRaw) {
    ul.classList.add("hidden");
    ul.innerHTML = "";
    return;
  }
  const users = await fetchUsersForKanbanAssigneeSuggest(q);
  if (String(input.value || "").trim() !== q) return;
  const keepIdx = typeof ul._kanbanActiveIdx === "number" ? ul._kanbanActiveIdx : -1;
  const list = users
    .filter((u) => {
      const emp = String(u.employeeNo || "").trim();
      return emp && !assigned.has(emp);
    })
    .slice(0, 12);
  if (list.length === 0) {
    ul.innerHTML =
      '<li class="kanban-assignee-suggest-empty">' +
      (q ? "검색 결과가 없습니다" : "추가할 사용자가 없습니다") +
      "</li>";
    ul.classList.remove("hidden");
    ul._kanbanActiveIdx = -1;
    return;
  }
  ul.innerHTML = list
    .map(
      (u) =>
        `<li><button type="button" class="kanban-assignee-pick" data-card-id="${escHtml(cardIdRaw)}" data-is-draft="${isDraft ? "1" : "0"}" data-pick-emp="${escHtml(String(u.employeeNo || "").trim())}">
          <span class="kanban-assignee-suggest-name">${escHtml(u.name || "")}</span><span class="kanban-assignee-suggest-meta">${escHtml([u.department || "", u.jobLevel || ""].filter(Boolean).join(" · ") || "소속 미지정")}</span>
        </button></li>`
    )
    .join("");
  ul.classList.remove("hidden");
  const picks = ul.querySelectorAll("button.kanban-assignee-pick");
  ul._kanbanActiveIdx = -1;
  if (keepIdx >= 0 && keepIdx < picks.length) {
    ul._kanbanActiveIdx = keepIdx;
    picks[keepIdx].classList.add("kanban-suggest-active");
    picks[keepIdx].scrollIntoView({ block: "nearest" });
  }
}

function renderPendingNewKanbanAssigneeChips() {
  const wrap = document.getElementById("kanbanNewCardAssigneeChips");
  if (!wrap) return;
  if (!pendingNewKanbanCardAssignees.length) {
    wrap.innerHTML = "";
    return;
  }
  wrap.innerHTML = pendingNewKanbanCardAssignees
    .map(
      (u) =>
        `<span class="kanban-assignee-chip">
      <span class="kanban-assignee-label">${escHtml(u.name || u.employeeNo)}</span>
      <button type="button" class="kanban-new-card-assignee-remove" data-emp="${escHtml(u.employeeNo)}" title="선택 해제">✕</button>
    </span>`
    )
    .join("");
  wrap.querySelectorAll(".kanban-new-card-assignee-remove").forEach((btn) => {
    btn.addEventListener("click", () => {
      const emp = String(btn.dataset.emp || "").trim();
      pendingNewKanbanCardAssignees = pendingNewKanbanCardAssignees.filter((x) => x.employeeNo !== emp);
      renderPendingNewKanbanAssigneeChips();
    });
  });
}

function clearPendingNewKanbanAssignees() {
  pendingNewKanbanCardAssignees = [];
  const input = document.getElementById("kanbanNewCardAssigneeSearch");
  if (input) input.value = "";
  const ul = document.getElementById("kanbanNewCardAssigneeSuggest");
  if (ul) {
    ul.classList.add("hidden");
    ul.innerHTML = "";
  }
  renderPendingNewKanbanAssigneeChips();
}

function openWorkItemDetailModal(rawId, isDraft) {
  const titleEl = document.getElementById("workItemDetailTitle");
  const descEl = document.getElementById("workItemDetailDesc");
  const statusEl = document.getElementById("workItemDetailStatus");
  if (!titleEl || !descEl || !statusEl) return;
  if (isDraft) {
    const idx = Number(String(rawId).replace("draft-", ""));
    const item = Number.isFinite(idx) ? workHubPendingNewWorkItems[idx] : null;
    if (!item) return;
    workHubSelectedWorkItemMeta = { rawId, isDraft: true, idx };
    titleEl.value = String(item.title || "");
    descEl.value = String(item.description || "");
    statusEl.value = normalizeWorkStatusLabel(item.status || "OPEN");
  } else {
    const id = Number(rawId);
    if (!id) return;
    const row = document.querySelector(`#channelWorkItemsList .work-item-delete-btn[data-work-item-id="${id}"]`)?.closest(".channel-work-item");
    workHubSelectedWorkItemMeta = { rawId: String(id), isDraft: false, id };
    titleEl.value = workHubPendingWorkTitle.get(id) ?? String(row?.querySelector(".channel-work-item-title")?.textContent || "");
    descEl.value = workHubPendingWorkDescription.get(id) ?? String(row?.querySelector(".channel-work-item-meta")?.textContent || "").replace(/^설명 없음$/, "");
    statusEl.value = normalizeWorkStatusLabel(workHubPendingWorkStatus.get(id) || row?.querySelector(".work-item-status-select")?.value || "OPEN");
  }
  const inactiveWrap = document.getElementById("workItemDetailInactiveActions");
  if (inactiveWrap) {
    if (isDraft) {
      inactiveWrap.classList.add("hidden");
    } else {
      const rowItem = lastChannelWorkItemsForHub.find((x) => Number(x.id) === Number(rawId));
      const showInactive = rowItem != null && !effectiveWorkItemInUseForUi(Number(rawId));
      inactiveWrap.classList.toggle("hidden", !showInactive);
    }
  }
  openModal("modalWorkItemDetail");
}

function openKanbanCardDetailModal(rawId, isDraft) {
  const titleEl = document.getElementById("kanbanCardDetailTitle");
  const descEl = document.getElementById("kanbanCardDetailDesc");
  const colEl = document.getElementById("kanbanCardDetailColumn");
  if (!titleEl || !descEl || !colEl) return;
  colEl.innerHTML = activeWorkHubColumns.map((c) => `<option value="${Number(c.id)}">${escHtml(c.name || "")}</option>`).join("");
  if (isDraft) {
    const idx = Number(String(rawId).replace("draft-card-", ""));
    const card = Number.isFinite(idx) ? workHubPendingNewKanbanCards[idx] : null;
    if (!card) return;
    workHubSelectedKanbanCardMeta = { rawId, isDraft: true, idx };
    titleEl.value = String(card.title || "");
    descEl.value = String(card.description || "");
    colEl.value = String(Number(card.columnId || activeWorkHubFirstColumnId || 0));
    workHubDetailKanbanAssignees = Array.isArray(card.assigneeEmployeeNos) ? [...new Set(card.assigneeEmployeeNos.map((x) => String(x).trim()).filter(Boolean))] : [];
    workHubDetailKanbanAssigneesInitial = [...workHubDetailKanbanAssignees];
  } else {
    const id = Number(rawId);
    if (!id) return;
    const el = document.querySelector(`.kanban-card-item[data-kanban-card-id="${id}"]`);
    workHubSelectedKanbanCardMeta = { rawId: String(id), isDraft: false, id };
    titleEl.value = workHubPendingCardTitle.get(id) ?? String(el?.querySelector(".kanban-card-item-header strong")?.textContent || "");
    descEl.value = workHubPendingCardDescription.get(id) ?? String(el?.querySelector("p")?.textContent || "");
    const pendingCol = workHubPendingCardColumn.get(id);
    const selVal = pendingCol != null ? pendingCol : Number(el?.querySelector(".kanban-card-column-select")?.value || 0);
    colEl.value = String(Number(selVal || activeWorkHubFirstColumnId || 0));
    const curr = [...(el?.querySelectorAll(".kanban-assignee-remove[data-assignee-emp]") || [])]
      .map((btn) => String(btn.dataset.assigneeEmp || "").trim())
      .filter(Boolean);
    workHubDetailKanbanAssignees = [...new Set(curr)];
    workHubDetailKanbanAssigneesInitial = [...workHubDetailKanbanAssignees];
  }
  renderKanbanCardDetailAssigneeChips();
  openModal("modalKanbanCardDetail");
}

function getEffectiveKanbanCardAssignees(cardId) {
  const id = Number(cardId);
  if (!id) return [];
  let base = [];
  for (const col of activeWorkHubColumns) {
    const cards = Array.isArray(col.cards) ? col.cards : [];
    const found = cards.find((c) => Number(c.id) === id);
    if (found) {
      base = Array.isArray(found.assigneeEmployeeNos) ? found.assigneeEmployeeNos : [];
      break;
    }
  }
  const m = kanbanAssigneeCanonMapFromList(base);
  const rem = workHubPendingCardAssigneeRemove.get(id);
  if (rem) {
    for (const emp of rem.values()) {
      const k = kanbanEmpMatchKey(emp);
      if (k) m.delete(k);
    }
  }
  const add = workHubPendingCardAssigneeAdd.get(id);
  if (add) {
    for (const emp of add.values()) {
      const canon = normKanbanEmpNo(emp);
      const k = kanbanEmpMatchKey(canon);
      if (k) m.set(k, canon);
    }
  }
  return [...m.values()];
}

function renderKanbanCardDetailAssigneeChips() {
  const wrap = document.getElementById("kanbanCardDetailAssigneeChips");
  if (!wrap) return;
  const resolveName = (emp) => {
    const e = String(emp || "").trim();
    if (!e) return "";
    if (kanbanAssigneeNameCache[e]) return kanbanAssigneeNameCache[e];
    const m = workHubChannelMembersForAssignee.find((x) => String(x.employeeNo || "").trim() === e);
    if (m?.name) {
      kanbanAssigneeNameCache[e] = String(m.name).trim();
      return kanbanAssigneeNameCache[e];
    }
    return e;
  };
  if (!workHubDetailKanbanAssignees.length) {
    wrap.classList.add("kanban-assignee-chips-empty");
    wrap.innerHTML = `<span class="muted kanban-assignee-empty-label">담당 없음</span>`;
    return;
  }
  wrap.classList.remove("kanban-assignee-chips-empty");
  wrap.innerHTML = workHubDetailKanbanAssignees
    .map((emp) => `<span class="kanban-assignee-chip">
      <span class="kanban-assignee-label">${escHtml(resolveName(emp))}</span>
      <button type="button" class="kanban-detail-assignee-remove" data-emp="${escHtml(emp)}" title="담당 해제">✕</button>
    </span>`)
    .join("");
  wrap.querySelectorAll(".kanban-detail-assignee-remove").forEach((btn) => {
    btn.addEventListener("click", () => {
      const emp = String(btn.dataset.emp || "").trim();
      workHubDetailKanbanAssignees = workHubDetailKanbanAssignees.filter((x) => x !== emp);
      renderKanbanCardDetailAssigneeChips();
      void runKanbanCardDetailAssigneeSuggest();
    });
  });
}

async function runKanbanCardDetailAssigneeSuggest() {
  const input = document.getElementById("kanbanCardDetailAssigneeSearch");
  const ul = document.getElementById("kanbanCardDetailAssigneeSuggest");
  if (!input || !ul || !currentUser) return;
  const q = String(input.value || "").trim();
  if (!q) {
    ul.classList.add("hidden");
    ul.innerHTML = "";
    return;
  }
  const assigned = new Set(workHubDetailKanbanAssignees);
  const users = await fetchUsersForKanbanAssigneeSuggest(q);
  if (String(input.value || "").trim() !== q) return;
  const keepIdx = typeof ul._kanbanActiveIdx === "number" ? ul._kanbanActiveIdx : -1;
  const list = users
    .filter((u) => {
      const emp = String(u.employeeNo || "").trim();
      return emp && !assigned.has(emp);
    })
    .slice(0, 12);
  if (!list.length) {
    ul.innerHTML = `<li class="kanban-assignee-suggest-empty">${q ? "검색 결과가 없습니다" : "추가할 사용자가 없습니다"}</li>`;
    ul.classList.remove("hidden");
    ul._kanbanActiveIdx = -1;
    return;
  }
  ul.innerHTML = list
    .map(
      (u) =>
        `<li><button type="button" class="kanban-detail-assignee-pick" data-emp="${escHtml(String(u.employeeNo || "").trim())}">
      <span class="kanban-assignee-suggest-name">${escHtml(u.name || "")}</span><span class="kanban-assignee-suggest-meta">${escHtml([u.department || "", u.jobLevel || ""].filter(Boolean).join(" · ") || "소속 미지정")}</span>
    </button></li>`
    )
    .join("");
  ul.classList.remove("hidden");
  const detailPicks = ul.querySelectorAll("button.kanban-detail-assignee-pick");
  ul._kanbanActiveIdx = -1;
  if (keepIdx >= 0 && keepIdx < detailPicks.length) {
    ul._kanbanActiveIdx = keepIdx;
    detailPicks[keepIdx].classList.add("kanban-suggest-active");
    detailPicks[keepIdx].scrollIntoView({ block: "nearest" });
  }
  ul.querySelectorAll(".kanban-detail-assignee-pick").forEach((btn) => {
    btn.addEventListener("click", () => {
      const emp = String(btn.dataset.emp || "").trim();
      if (!emp || workHubDetailKanbanAssignees.includes(emp)) return;
      workHubDetailKanbanAssignees.push(emp);
      input.value = "";
      ul.classList.add("hidden");
      ul.innerHTML = "";
      renderKanbanCardDetailAssigneeChips();
      void runKanbanCardDetailAssigneeSuggest();
    });
  });
}

async function runNewKanbanCardAssigneeSuggest(input, ul) {
  const q = String(input.value || "").trim();
  if (!q) {
    ul.classList.add("hidden");
    ul.innerHTML = "";
    return;
  }
  if (!currentUser) {
    ul.classList.add("hidden");
    ul.innerHTML = "";
    return;
  }
  const pendingEmp = new Set(pendingNewKanbanCardAssignees.map((x) => x.employeeNo));
  const users = await fetchUsersForKanbanAssigneeSuggest(q);
  if (String(input.value || "").trim() !== q) return;
  const keepIdx = typeof ul._kanbanActiveIdx === "number" ? ul._kanbanActiveIdx : -1;
  const list = users
    .filter((u) => {
      const emp = String(u.employeeNo || "").trim();
      return emp && !pendingEmp.has(emp);
    })
    .slice(0, 12);
  if (list.length === 0) {
    ul.innerHTML =
      '<li class="kanban-assignee-suggest-empty">' +
      (q ? "검색 결과가 없습니다" : "추가할 사용자가 없습니다") +
      "</li>";
    ul.classList.remove("hidden");
    ul._kanbanActiveIdx = -1;
    return;
  }
  ul.innerHTML = list
    .map(
      (u) =>
        `<li><button type="button" class="kanban-assignee-pick-new" data-pick-emp="${escHtml(String(u.employeeNo || "").trim())}" data-pick-name="${escHtml(u.name || "")}">
          <span class="kanban-assignee-suggest-name">${escHtml(u.name || "")}</span><span class="kanban-assignee-suggest-meta">${escHtml([u.department || "", u.jobLevel || ""].filter(Boolean).join(" · ") || "소속 미지정")}</span>
        </button></li>`
    )
    .join("");
  ul.classList.remove("hidden");
  const newPicks = ul.querySelectorAll("button.kanban-assignee-pick-new");
  ul._kanbanActiveIdx = -1;
  if (keepIdx >= 0 && keepIdx < newPicks.length) {
    ul._kanbanActiveIdx = keepIdx;
    newPicks[keepIdx].classList.add("kanban-suggest-active");
    newPicks[keepIdx].scrollIntoView({ block: "nearest" });
  }
}

function ensureKanbanNewCardAssigneeUiBound() {
  if (kanbanNewCardAssigneeUiBound) return;
  const input = document.getElementById("kanbanNewCardAssigneeSearch");
  const ul = document.getElementById("kanbanNewCardAssigneeSuggest");
  if (!input || !ul) return;
  kanbanNewCardAssigneeUiBound = true;
  input.addEventListener("input", () => {
    clearTimeout(input._newCardSuggestT);
    input._newCardSuggestT = setTimeout(() => void runNewKanbanCardAssigneeSuggest(input, ul), 140);
  });
  ul.addEventListener("click", (e) => {
    const pick = e.target.closest(".kanban-assignee-pick-new");
    if (!pick) return;
    e.preventDefault();
    const emp = String(pick.dataset.pickEmp || "").trim();
    const name = String(pick.dataset.pickName || "").trim() || emp;
    if (!emp || pendingNewKanbanCardAssignees.some((x) => x.employeeNo === emp)) return;
    pendingNewKanbanCardAssignees.push({ employeeNo: emp, name });
    input.value = "";
    ul.classList.add("hidden");
    ul.innerHTML = "";
    renderPendingNewKanbanAssigneeChips();
  });
}

function ensureKanbanBoardAssigneeUiBound() {
  const root = document.getElementById("channelKanbanBoard");
  if (!root || kanbanBoardAssigneeUiBound) return;
  kanbanBoardAssigneeUiBound = true;
  root.addEventListener("click", async (e) => {
    const cardItem = e.target.closest(".kanban-card-item");
    if (
      cardItem &&
      !e.target.closest(".kanban-card-delete-btn") &&
      !e.target.closest(".kanban-card-column-select") &&
      !e.target.closest(".kanban-assignee-add") &&
      !e.target.closest(".kanban-assignee-remove")
    ) {
      const raw = String(cardItem.dataset.cardRawId || "");
      const isDraft = cardItem.dataset.isDraft === "1";
      openKanbanCardDetailModal(raw, isDraft);
      return;
    }
    const delCard = e.target.closest(".kanban-card-delete-btn");
    if (delCard) {
      e.preventDefault();
      const cardIdRaw = String(delCard.dataset.cardId || "");
      const isDraft = delCard.dataset.isDraft === "1";
      const cardId = isDraft ? cardIdRaw : Number(cardIdRaw);
      if (!cardId || !currentUser) return;
      if (!(await uiConfirm("이 칸반 카드를 삭제할까요?"))) return;
      if (isDraft) {
        const idx = Number(cardIdRaw.replace("draft-card-", ""));
        if (!Number.isFinite(idx) || !workHubPendingNewKanbanCards[idx]) return;
        workHubPendingNewKanbanCards.splice(idx, 1);
      } else {
        const cid = Number(cardId);
        workHubPendingCardColumn.delete(cid);
        workHubPendingCardSortOrder.delete(cid);
        workHubPendingCardTitle.delete(cid);
        workHubPendingCardDescription.delete(cid);
        workHubPendingCardAssigneeAdd.delete(cid);
        workHubPendingCardAssigneeRemove.delete(cid);
        workHubPendingCardDeleteIds.add(cid);
      }
      await loadChannelKanbanBoard();
      return;
    }
    const rem = e.target.closest(".kanban-assignee-remove");
    if (rem) {
      e.preventDefault();
      e.stopPropagation();
      const cardIdRaw = String(rem.dataset.cardId || "");
      const isDraft = rem.dataset.isDraft === "1";
      const cardId = isDraft ? cardIdRaw : Number(cardIdRaw);
      const assigneeEmp = String(
        rem.getAttribute("data-assignee-emp") || rem.dataset.assigneeEmp || ""
      ).trim();
      if (!cardId || !assigneeEmp || !currentUser) return;
      if (isDraft) {
        const idx = Number(cardIdRaw.replace("draft-card-", ""));
        if (!Number.isFinite(idx) || !workHubPendingNewKanbanCards[idx]) return;
        const d = workHubPendingNewKanbanCards[idx];
        d.assigneeEmployeeNos = (d.assigneeEmployeeNos || []).filter((x) => x !== assigneeEmp);
      } else {
        const cid = Number(cardId);
        if (!workHubPendingCardAssigneeRemove.has(cid)) {
          workHubPendingCardAssigneeRemove.set(cid, new Set());
        }
        workHubPendingCardAssigneeRemove.get(cid).add(assigneeEmp);
        if (workHubPendingCardAssigneeAdd.has(cid)) {
          kanbanDeleteFromEmpSet(workHubPendingCardAssigneeAdd.get(cid), assigneeEmp);
        }
      }
      await loadChannelKanbanBoard();
      return;
    }
    const pick = e.target.closest(".kanban-assignee-pick");
    if (pick) {
      e.preventDefault();
      const cardIdRaw = String(pick.dataset.cardId || "");
      const isDraft = pick.dataset.isDraft === "1";
      const cardId = isDraft ? cardIdRaw : Number(cardIdRaw);
      const emp = String(pick.dataset.pickEmp || "").trim();
      if (!cardId || !emp || !currentUser) return;
      if (isDraft) {
        const idx = Number(cardIdRaw.replace("draft-card-", ""));
        if (!Number.isFinite(idx) || !workHubPendingNewKanbanCards[idx]) return;
        const d = workHubPendingNewKanbanCards[idx];
        const next = new Set(d.assigneeEmployeeNos || []);
        next.add(emp);
        d.assigneeEmployeeNos = [...next];
      } else {
        const cid = Number(cardId);
        if (!workHubPendingCardAssigneeAdd.has(cid)) {
          workHubPendingCardAssigneeAdd.set(cid, new Set());
        }
        workHubPendingCardAssigneeAdd.get(cid).add(emp);
        if (workHubPendingCardAssigneeRemove.has(cid)) {
          kanbanDeleteFromEmpSet(workHubPendingCardAssigneeRemove.get(cid), emp);
        }
      }
      const inp = root.querySelector(`.kanban-assignee-search[data-card-id="${cardIdRaw}"]`);
      if (inp) inp.value = "";
      await loadChannelKanbanBoard();
      return;
    }
  });
  root.addEventListener("input", (e) => {
    const input = e.target.closest(".kanban-assignee-search");
    if (!input) return;
    const ul = input.closest(".kanban-assignee-add")?.querySelector(".kanban-assignee-suggest");
    if (!ul) return;
    clearTimeout(input._assignSuggestTimer);
    input._assignSuggestTimer = setTimeout(() => void runKanbanAssigneeSuggest(input, ul), 140);
  });
  if (!window._echKanbanSuggestDocClick) {
    window._echKanbanSuggestDocClick = true;
    document.addEventListener("click", (ev) => {
      if (ev.target.closest?.(".kanban-assignee-add")) return;
      document.querySelectorAll("#channelKanbanBoard .kanban-assignee-suggest").forEach((u) => {
        u.classList.add("hidden");
        u.innerHTML = "";
      });
      const ulNew = document.getElementById("kanbanNewCardAssigneeSuggest");
      if (ulNew) {
        ulNew.classList.add("hidden");
        ulNew.innerHTML = "";
      }
    });
  }
}

function renderKanbanBoard(board) {
  const boardEl = document.getElementById("channelKanbanBoard");
  if (!boardEl) return;
  ensureKanbanDragInputGuard();
  ensureKanbanBoardColumnSelectChangeDelegated(boardEl);
  const cols = Array.isArray(board?.columns) ? board.columns : [];
  activeWorkHubColumns = cols;
  activeWorkHubFirstColumnId = cols.length ? Number(cols[0].id) : null;
  if (!cols.length) {
    boardEl.innerHTML = `<div class="empty-notice">칸반 컬럼이 없습니다.</div>`;
    return;
  }
  const draftCards = workHubPendingNewKanbanCards.map((d, i) => ({
    id: `draft-card-${i}`,
    columnId: Number(d.columnId || activeWorkHubFirstColumnId),
    title: d.title,
    description: d.description || "",
    workItemId: d.workItemId,
    workItemInUse: effectiveWorkItemInUseForUi(Number(d.workItemId)),
    assigneeEmployeeNos: Array.isArray(d.assigneeEmployeeNos) ? d.assigneeEmployeeNos : [],
    _isDraft: true,
  }));
  const effectiveSavedCards = getEffectiveSavedCardsByColumn(cols);
  boardEl.innerHTML = cols
    .map((col) => {
      const savedCards = effectiveSavedCards
        .filter((c) => Number(c._effectiveColumnId) === Number(col.id))
        .sort((a, b) => {
          const so = Number(a._effectiveSortOrder) - Number(b._effectiveSortOrder);
          if (so !== 0) return so;
          return Number(a.id) - Number(b.id);
        });
      const cards = [
        ...savedCards,
        ...draftCards.filter((dc) => Number(dc.columnId) === Number(col.id)),
      ];
      const options = cols
        .map((c) => `<option value="${Number(c.id)}">${escHtml(c.name || "")}</option>`)
        .join("");
      return `
      <section class="kanban-column" data-column-id="${Number(col.id)}">
        <h5>${escHtml(col.name || "컬럼")}</h5>
        <div class="kanban-card-list">
          ${
            cards.length
              ? cards
                  .map((card) => {
                    const isDraft = card._isDraft === true;
                    const cardRawId = String(card.id);
                    const cardNumId = Number(card.id);
                    const baseAssigns = Array.isArray(card.assigneeEmployeeNos) ? card.assigneeEmployeeNos : [];
                    let assigns = [...baseAssigns];
                    if (!isDraft && Number.isFinite(cardNumId)) {
                      const add = workHubPendingCardAssigneeAdd.get(cardNumId);
                      const rem = workHubPendingCardAssigneeRemove.get(cardNumId);
                      if (add && add.size) {
                        const m = kanbanAssigneeCanonMapFromList(assigns);
                        add.forEach((x) => {
                          const c = normKanbanEmpNo(x);
                          const k = kanbanEmpMatchKey(c);
                          if (k) m.set(k, c);
                        });
                        assigns = [...m.values()];
                      }
                      if (rem && rem.size) {
                        assigns = assigns.filter((x) => !kanbanPendingRemovesHas(rem, x));
                      }
                    }
                    const assignedPipe = assigns
                      .map((x) => String(x || "").trim())
                      .filter(Boolean)
                      .join("|");
                    const assigneesHtml = assigns.length
                      ? `<div class="kanban-assignee-chips">${assigns
                          .map((raw) => {
                            const emp = String(raw || "").trim();
                            if (!emp) return "";
                            return `<span class="kanban-assignee-chip">
          <span class="kanban-assignee-label" data-emp="${escHtml(emp)}">${escHtml(emp)}</span>
          <button type="button" class="kanban-assignee-remove" data-card-id="${escHtml(cardRawId)}" data-is-draft="${isDraft ? "1" : "0"}" data-assignee-emp="${escHtml(emp)}" title="담당 해제">✕</button>
        </span>`;
                          })
                          .filter(Boolean)
                          .join("")}</div>`
                      : `<div class="kanban-assignee-chips kanban-assignee-chips-empty"><span class="muted kanban-assignee-empty-label">담당 없음</span></div>`;
                    const effectiveTitle = !isDraft && Number.isFinite(cardNumId) && workHubPendingCardTitle.has(cardNumId)
                      ? workHubPendingCardTitle.get(cardNumId)
                      : card.title;
                    const effectiveDesc = !isDraft && Number.isFinite(cardNumId) && workHubPendingCardDescription.has(cardNumId)
                      ? workHubPendingCardDescription.get(cardNumId)
                      : card.description;
                    const workItemIdVal = isDraft
                      ? Number(card.workItemId || 0)
                      : Number(card.workItemId ?? card.work_item_id ?? 0);
                    const wiInactive =
                      Number(workItemIdVal) > 0 && !effectiveWorkItemInUseForUi(workItemIdVal);
                    const workRef =
                      Number.isFinite(workItemIdVal) && workItemIdVal > 0
                        ? `<div class="kanban-card-work-ref muted">${escHtml(workItemTitleForKanbanCard(workItemIdVal))}</div>`
                        : "";
                    return `
              <article class="kanban-card-item${wiInactive ? " kanban-card-item-inactive" : ""}" data-kanban-card-id="${isDraft ? "" : Number(card.id)}" data-render-column-id="${Number(col.id)}" data-work-item-id="${Number.isFinite(workItemIdVal) && workItemIdVal > 0 ? workItemIdVal : ""}" data-card-raw-id="${escHtml(cardRawId)}" data-is-draft="${isDraft ? "1" : "0"}">
                <div class="kanban-card-item-header">
                  <strong>${escHtml(effectiveTitle || "(제목 없음)")}</strong>
                  <button type="button" class="btn-icon-delete kanban-card-delete-btn" data-card-id="${escHtml(cardRawId)}" data-is-draft="${isDraft ? "1" : "0"}" title="삭제" aria-label="삭제">✕</button>
                </div>
                ${workRef}
                <p>${escHtml(effectiveDesc || "")}</p>
                <div class="kanban-card-assignees">
                  ${assigneesHtml}
                  <div class="kanban-assignee-add">
                    <input type="search" class="kanban-assignee-search" data-card-id="${escHtml(cardRawId)}" data-is-draft="${isDraft ? "1" : "0"}" data-assigned-emp="${escHtml(assignedPipe)}" placeholder="채널 멤버 검색 (↑↓·Enter)" autocomplete="off" />
                    <ul class="kanban-assignee-suggest hidden" role="listbox" aria-label="담당자 검색 결과"></ul>
                  </div>
                </div>
                <div class="kanban-card-move-row">
                  <select class="kanban-card-column-select" data-card-id="${escHtml(cardRawId)}" data-is-draft="${isDraft ? "1" : "0"}">
                    ${options}
                  </select>
                </div>
              </article>`;
                  })
                  .join("")
              : `<div class="empty-notice">카드 없음</div>`
          }
        </div>
      </section>`;
    })
    .join("");

  boardEl.querySelectorAll(".kanban-card-column-select").forEach((sel) => {
    const isDraft = sel.dataset.isDraft === "1";
    const rawId = String(sel.dataset.cardId || "");
    if (isDraft) {
      const idx = Number(rawId.replace("draft-card-", ""));
      const d = Number.isFinite(idx) ? workHubPendingNewKanbanCards[idx] : null;
      const draftCol = Number(d?.columnId || activeWorkHubFirstColumnId);
      if (draftCol > 0) applyKanbanColumnSelectToColumnId(sel, draftCol);
      else sel.value = String(draftCol || "");
    } else {
      const cardId = Number(rawId);
      const card = effectiveSavedCards.find((c) => Number(c.id) === cardId);
      const article = sel.closest(".kanban-card-item");
      const fromRender = Number(article?.dataset.renderColumnId || 0);
      const hostCol = Number(sel.closest(".kanban-column")?.dataset.columnId || 0);
      const pending = workHubPendingCardColumn.get(cardId);
      const baseCol = Number(card?.columnId || 0);
      const resolved =
        fromRender > 0
          ? fromRender
          : hostCol > 0
            ? hostCol
            : pending != null
              ? Number(pending)
              : baseCol;
      if (Number(resolved) > 0) applyKanbanColumnSelectToColumnId(sel, Number(resolved));
      else sel.value = String(resolved || "");
      if (hostCol > 0 && Number.isFinite(cardId)) {
        workHubPendingCardColumn.set(cardId, hostCol);
        if (article) article.dataset.renderColumnId = String(hostCol);
        const wi = Number(article?.dataset.workItemId || "");
        syncPendingWorkItemStatusFromKanbanColumn(wi, hostCol);
      }
    }
  });
  // Whole-card drag; cancel when starting from controls so `<select>`/검색/버튼 remain usable. After drop, `rebuildKanbanCardColumnSelectDom` refreshes `<select>` nodes for Chrome.
  boardEl.querySelectorAll(".kanban-card-item").forEach((cardEl) => {
    cardEl.setAttribute("draggable", "true");
    cardEl.addEventListener("dragstart", (ev) => {
      const t = ev.target;
      if (
        t instanceof Element &&
        t.closest(
          "input, textarea, select, button, label, .kanban-assignee-add, .kanban-assignee-suggest, a[href]"
        )
      ) {
        ev.preventDefault();
        return;
      }
      const dt = ev.dataTransfer;
      if (dt) {
        dt.setData("application/x-ech-kanban-card", "1");
        try {
          dt.setData("text/plain", "");
        } catch (_) {
          /* ignore */
        }
        dt.effectAllowed = "move";
        try {
          const cr = cardEl.getBoundingClientRect();
          dt.setDragImage(cardEl, ev.clientX - cr.left, ev.clientY - cr.top);
        } catch (_) {
          /* ignore */
        }
      }
      cardEl.classList.add("kanban-card-dragging");
      const srcCol = Number(cardEl.closest(".kanban-column")?.dataset.columnId || 0) || null;
      kanbanDnDSourceColumnId = srcCol;
      cardEl.dataset.dragSourceColumnId = srcCol != null ? String(srcCol) : "";
    });
    cardEl.addEventListener("dragend", () => {
      setTimeout(() => {
        cardEl.classList.remove("kanban-card-dragging");
        delete cardEl.dataset.dragSourceColumnId;
        kanbanDnDSourceColumnId = null;
        boardEl.querySelectorAll(".kanban-column.kanban-column-drag-over").forEach((c) =>
          c.classList.remove("kanban-column-drag-over")
        );
      }, 0);
    });
  });
  /** DnD on whole column: `.kanban-card-list` used to be height = cards only, so empty space below cards never received dragover/drop. */
  boardEl.querySelectorAll(".kanban-column").forEach((colEl) => {
    const listEl = colEl.querySelector(".kanban-card-list");
    if (!listEl) return;
    const clearColumnDragOver = () => {
      boardEl.querySelectorAll(".kanban-column.kanban-column-drag-over").forEach((c) =>
        c.classList.remove("kanban-column-drag-over")
      );
    };
    const handleDragOver = (ev) => {
      ev.preventDefault();
      try {
        ev.dataTransfer.dropEffect = "move";
      } catch (_) {
        /* ignore */
      }
      const dragging = boardEl.querySelector(".kanban-card-dragging");
      if (!dragging) return;
      clearColumnDragOver();
      colEl.classList.add("kanban-column-drag-over");
      listEl.querySelector(".empty-notice")?.remove();
      const cards = [...listEl.querySelectorAll(".kanban-card-item:not(.kanban-card-dragging)")];
      cards.forEach((c) => c.classList.remove("kanban-drop-before"));
      if (!cards.length) {
        listEl.appendChild(dragging);
        return;
      }
      const next = cards.find((c) => ev.clientY <= c.getBoundingClientRect().top + c.offsetHeight / 2);
      if (next) {
        next.classList.add("kanban-drop-before");
        listEl.insertBefore(dragging, next);
      } else {
        listEl.appendChild(dragging);
      }
    };
    colEl.addEventListener("dragenter", (ev) => {
      ev.preventDefault();
    });
    colEl.addEventListener("dragover", handleDragOver);
    colEl.addEventListener("dragleave", (ev) => {
      if (!colEl.contains(ev.relatedTarget)) {
        colEl.classList.remove("kanban-column-drag-over");
      }
    });
    colEl.addEventListener("drop", async (ev) => {
      ev.preventDefault();
      ev.stopPropagation();
      colEl.classList.remove("kanban-column-drag-over");
      boardEl.querySelectorAll(".kanban-drop-before").forEach((c) => c.classList.remove("kanban-drop-before"));
      // Consecutive DnD: invalidate any in-flight board GET before rAF/sync — otherwise a slower
      // response from the previous drop can render while DOM already reflects the next drop.
      const preBumpCid = getWorkHubChannelId();
      if (preBumpCid) bumpKanbanBoardFetchGeneration(preBumpCid);
      await new Promise((r) => requestAnimationFrame(() => requestAnimationFrame(r)));
      syncKanbanBoardFromDomFull(boardEl);
      syncKanbanDraftsOrderFromDom(boardEl);
      syncKanbanCardColumnSelectsFromDom(boardEl);
      // DnD + dragend ordering: a later macrotask pass re-reads the tree so `<select>` matches the host column
      // after the dragged node (and its controls) have fully settled.
      await new Promise((r) => setTimeout(r, 0));
      syncKanbanBoardFromDomFull(boardEl);
      syncKanbanDraftsOrderFromDom(boardEl);
      rebuildKanbanCardColumnSelectDom(boardEl);
      // Do not call `loadChannelKanbanBoard` here: DnD already moved the DOM. A full re-render from GET
      // races with rapid consecutive drops and can rebuild `<select>` from stale server + pending timing.
      await loadChannelWorkItems();
    });
  });
  ensureKanbanBoardAssigneeUiBound();
  void enrichKanbanAssigneeLabels(boardEl);
}

async function loadChannelKanbanBoard() {
  const cid = getWorkHubChannelId();
  if (!cid || !currentUser) return;
  const myGen = bumpKanbanBoardFetchGeneration(cid);
  const res = await apiFetch(
    `/api/kanban/channels/${cid}/board?employeeNo=${encodeURIComponent(currentUser.employeeNo)}`
  );
  const json = await res.json().catch(() => ({}));
  if (kanbanBoardFetchIsStale(cid, myGen)) return;
  if (!res.ok) {
    throw new Error(json.error?.message || "칸반 보드 조회 실패");
  }
  const board = json.data || {};
  if (kanbanBoardFetchIsStale(cid, myGen)) return;
  activeWorkHubBoardId = Number(board.id) || null;
  renderKanbanBoard(board);
}

document.getElementById("btnOpenWorkHub")?.addEventListener("click", async () => {
  if (!activeChannelId || !currentUser) {
    await uiAlert("채널을 먼저 선택하세요.");
    return;
  }
  workHubScopedChannelId = null;
  try {
    clearWorkHubPendingMaps();
    clearPendingNewKanbanAssignees();
    await Promise.all([
      loadWorkHubChannelMembersForAssignee(),
      loadChannelWorkItems(),
      loadChannelKanbanBoard(),
    ]);
    ensureWorkHubWorkListDeleteBound();
    openModal("modalWorkHub");
  } catch (e) {
    await uiAlert(e?.message || "업무/칸반 정보를 불러오지 못했습니다.");
  }
});

document.getElementById("btnTopNavDashboard")?.addEventListener("click", () => {
  void clearActiveChannelAndReload();
});
document.getElementById("btnTopNavProjects")?.addEventListener("click", () => {
  document.getElementById("btnOpenWorkHub")?.click();
});
document.getElementById("btnTopNavTeam")?.addEventListener("click", () => {
  document.getElementById("btnOrgChart")?.click();
});

document.getElementById("workItemCreateForm")?.addEventListener("submit", (e) => e.preventDefault());
document.getElementById("kanbanCardCreateForm")?.addEventListener("submit", (e) => e.preventDefault());

document.getElementById("btnQueueWorkItem")?.addEventListener("click", () => {
  const titleEl = document.getElementById("workItemTitleInput");
  const descEl = document.getElementById("workItemDescInput");
  const statusEl = document.getElementById("workItemStatusSelect");
  const title = String(titleEl?.value || "").trim();
  if (!title) {
    void uiAlert("업무 제목을 입력하세요.");
    return;
  }
  workHubPendingNewWorkItems.push({
    title,
    description: String(descEl?.value || "").trim() || null,
    status: statusEl?.value || "OPEN",
  });
  if (titleEl) titleEl.value = "";
  if (descEl) descEl.value = "";
  if (statusEl) statusEl.value = "OPEN";
  void loadChannelWorkItems();
});

document.getElementById("btnQueueKanbanCard")?.addEventListener("click", () => {
  const titleEl = document.getElementById("kanbanCardTitleInput");
  const descEl = document.getElementById("kanbanCardDescInput");
  const wiSel = document.getElementById("kanbanNewCardWorkItemSelect");
  const workItemId = Number(wiSel?.value || 0);
  const title = String(titleEl?.value || "").trim();
  if (!Number.isFinite(workItemId) || workItemId <= 0) {
    void uiAlert("연결할 업무 항목을 선택하세요. (새 업무는 먼저 저장하세요.)");
    return;
  }
  if (!title) {
    void uiAlert("카드 제목을 입력하세요.");
    return;
  }
  workHubPendingNewKanbanCards.push({
    workItemId,
    title,
    description: String(descEl?.value || "").trim() || null,
    assigneeEmployeeNos: pendingNewKanbanCardAssignees.map((x) => x.employeeNo),
    columnId: Number(activeWorkHubFirstColumnId) || null,
  });
  if (titleEl) titleEl.value = "";
  if (descEl) descEl.value = "";
  clearPendingNewKanbanAssignees();
  void loadChannelKanbanBoard();
});

document.getElementById("btnWorkHubSave")?.addEventListener("click", async () => {
  if (!(await uiConfirm("저장하시겠습니까?"))) return;
  const ok = await flushWorkHubSave();
  if (!ok) return;
});

document.getElementById("btnSaveWorkItemDetail")?.addEventListener("click", async () => {
  const meta = workHubSelectedWorkItemMeta;
  if (!meta) return;
  const title = String(document.getElementById("workItemDetailTitle")?.value || "").trim();
  const description = String(document.getElementById("workItemDetailDesc")?.value || "").trim();
  const status = normalizeWorkStatusLabel(document.getElementById("workItemDetailStatus")?.value || "OPEN");
  if (!title) {
    await uiAlert("업무 제목을 입력하세요.");
    return;
  }
  if (meta.isDraft) {
    const d = workHubPendingNewWorkItems[meta.idx];
    if (!d) return;
    d.title = title;
    d.description = description || null;
    d.status = status;
  } else {
    const id = Number(meta.id);
    workHubPendingWorkTitle.set(id, title);
    workHubPendingWorkDescription.set(id, description || null);
    workHubPendingWorkStatus.set(id, status);
  }
  closeModal("modalWorkItemDetail");
  await loadChannelWorkItems();
});

document.getElementById("btnWorkItemRestore")?.addEventListener("click", async () => {
  const meta = workHubSelectedWorkItemMeta;
  if (!meta || meta.isDraft || !currentUser) return;
  const id = Number(meta.id);
  if (!id) return;
  queueWorkItemRestore(id);
  closeModal("modalWorkItemDetail");
  document.getElementById("workItemDetailInactiveActions")?.classList.add("hidden");
});

document.getElementById("btnWorkItemPurge")?.addEventListener("click", async () => {
  const meta = workHubSelectedWorkItemMeta;
  if (!meta || meta.isDraft || !currentUser) return;
  const id = Number(meta.id);
  if (!id) return;
  if (!(await uiConfirm("연결된 칸반 카드까지 모두 삭제합니다. 저장 시 서버에 반영됩니다. 계속할까요?"))) return;
  queueWorkItemPurge(id);
  closeModal("modalWorkItemDetail");
  document.getElementById("workItemDetailInactiveActions")?.classList.add("hidden");
});

document.getElementById("btnSaveKanbanCardDetail")?.addEventListener("click", async () => {
  const meta = workHubSelectedKanbanCardMeta;
  if (!meta) return;
  const title = String(document.getElementById("kanbanCardDetailTitle")?.value || "").trim();
  const description = String(document.getElementById("kanbanCardDetailDesc")?.value || "").trim();
  const columnId = Number(document.getElementById("kanbanCardDetailColumn")?.value || 0);
  if (!title) {
    await uiAlert("카드 제목을 입력하세요.");
    return;
  }
  if (meta.isDraft) {
    const d = workHubPendingNewKanbanCards[meta.idx];
    if (!d) return;
    d.title = title;
    d.description = description || null;
    d.columnId = columnId || d.columnId;
    d.assigneeEmployeeNos = [...new Set(workHubDetailKanbanAssignees)];
    const wid = Number(d.workItemId || 0);
    if (wid > 0 && d.columnId) syncPendingWorkItemStatusFromKanbanColumn(wid, Number(d.columnId));
  } else {
    const id = Number(meta.id);
    workHubPendingCardTitle.set(id, title);
    workHubPendingCardDescription.set(id, description || null);
    if (columnId) {
      workHubPendingCardColumn.set(id, columnId);
      const row = document.querySelector(`.kanban-card-item[data-kanban-card-id="${id}"]`);
      const wi = Number(row?.dataset.workItemId || "");
      syncPendingWorkItemStatusFromKanbanColumn(wi, columnId);
    }
    const nextArr = workHubDetailKanbanAssignees.map(normKanbanEmpNo).filter(Boolean);
    const prevArr = getEffectiveKanbanCardAssignees(id);
    const addSet = new Set(workHubPendingCardAssigneeAdd.get(id) || []);
    const removeSet = new Set(workHubPendingCardAssigneeRemove.get(id) || []);
    for (const emp of prevArr) {
      if (!kanbanEmpListHas(nextArr, emp)) {
        removeSet.add(normKanbanEmpNo(emp));
        kanbanDeleteFromEmpSet(addSet, emp);
      }
    }
    for (const emp of nextArr) {
      if (!kanbanEmpListHas(prevArr, emp)) {
        addSet.add(emp);
        kanbanDeleteFromEmpSet(removeSet, emp);
      }
    }
    if (addSet.size) workHubPendingCardAssigneeAdd.set(id, addSet);
    else workHubPendingCardAssigneeAdd.delete(id);
    if (removeSet.size) workHubPendingCardAssigneeRemove.set(id, removeSet);
    else workHubPendingCardAssigneeRemove.delete(id);
  }
  closeModal("modalKanbanCardDetail");
  await Promise.all([loadChannelKanbanBoard(), loadChannelWorkItems()]);
});

document.getElementById("kanbanCardDetailAssigneeSearch")?.addEventListener("input", (e) => {
  clearTimeout(e.target._detailAssignTimer);
  e.target._detailAssignTimer = setTimeout(() => void runKanbanCardDetailAssigneeSuggest(), 140);
});

async function clearActiveChannelAndReload() {
  if (activeChannelId != null) {
    saveComposerDraftForChannel(activeChannelId);
    clearFilePreview();
  }
  if (messageInputEl) {
    messageInputEl.value = "";
    clearReplyComposerTarget();
    mentionDisplayToEmployeeNo.clear();
    scheduleComposerInputHeight();
  }
  activeChannelId = null;
  activeChannelType = null;
  activeChannelCreatorEmployeeNo = null;
  activeChannelMemberCount = 0;
  activeChannelMemberMentionList = [];
  showView("viewWelcome");
  syncHeaderNotifyButton();
  await loadMyChannels();
}

document.getElementById("btnRenameDm")?.addEventListener("click", async () => {
  if (!activeChannelId || !currentUser) return;
  const name = String(await uiPrompt("DM 이름을 입력하세요.", document.getElementById("chatChannelName")?.textContent || "", "DM 이름 변경") || "").trim();
  if (!name) return;
  const res = await apiFetch(`/api/channels/${activeChannelId}/dm-name`, {
    method: "PUT",
    body: JSON.stringify({ name }),
  });
  const json = await res.json().catch(() => ({}));
  if (!res.ok) {
    await uiAlert(json.error?.message || "DM 이름 변경에 실패했습니다.");
    return;
  }
  await loadMyChannels();
  document.getElementById("chatChannelName").textContent = name;
});

document.getElementById("btnRenameChannel")?.addEventListener("click", async () => {
  if (!activeChannelId || !currentUser) return;
  const currentName = document.getElementById("chatChannelName")?.textContent || "";
  const nextName = String(await uiPrompt("새 채널명을 입력하세요.", currentName, "채널 이름 변경") || "").trim();
  if (!nextName) return;
  const res = await apiFetch(`/api/channels/${activeChannelId}/name`, {
    method: "PUT",
    body: JSON.stringify({ name: nextName }),
  });
  const json = await res.json().catch(() => ({}));
  if (!res.ok) {
    await uiAlert(json.error?.message || "채널 이름 변경에 실패했습니다.");
    return;
  }
  document.getElementById("chatChannelName").textContent = nextName;
  await loadChannelMembers(activeChannelId);
  await loadMyChannels();
});

async function leaveChannelAsMember(channelId, channelDisplayName, channelType) {
  if (!channelId || !currentUser) return;
  const label = String(channelDisplayName || "").trim() || "채팅방";
  if (!(await uiConfirm(`「${label}」에서 나가시겠습니까?`))) return;
  const mine = String(currentUser.employeeNo || "").trim();
  const isDm = String(channelType || "").toUpperCase() === "DM";
  let creatorEmp = "";
  if (Number(channelId) === Number(activeChannelId) && activeChannelCreatorEmployeeNo) {
    creatorEmp = String(activeChannelCreatorEmployeeNo || "").trim();
  } else {
    try {
      const res = await apiFetch(`/api/channels/${channelId}`);
      const j = await res.json().catch(() => ({}));
      if (res.ok && j.data) creatorEmp = String(j.data.createdByEmployeeNo || "").trim();
    } catch {
      /* ignore */
    }
  }
  const isCreator = mine !== "" && creatorEmp !== "" && mine === creatorEmp && !isDm;
  if (isCreator) {
    await uiAlert("관리자는 멤버를 우클릭해 먼저 관리자 위임을 완료한 뒤 나갈 수 있습니다.");
    return;
  }
  const res = await apiFetch(`/api/channels/${channelId}/leave`, {
    method: "POST",
    body: JSON.stringify({}),
  });
  const json = await res.json().catch(() => ({}));
  if (!res.ok) {
    await uiAlert(json.error?.message || "채팅방 나가기에 실패했습니다.");
    return;
  }
  const leftCid = Number(channelId);
  if (Number.isFinite(leftCid)) {
    saveQuickRailPinnedChannelIds(readQuickRailPinnedChannelIds().filter((x) => x !== leftCid));
  }
  if (Number(channelId) === Number(activeChannelId)) {
    await clearActiveChannelAndReload();
  } else {
    await loadMyChannels();
  }
}

document.getElementById("btnLeaveChannel")?.addEventListener("click", async () => {
  if (!activeChannelId || !currentUser) return;
  const name = document.getElementById("chatChannelName")?.textContent || "";
  await leaveChannelAsMember(activeChannelId, name, activeChannelType);
});

document.getElementById("btnHeaderNotifyToggle")?.addEventListener("click", async () => {
  if (!activeChannelId || !currentUser) return;
  const muted = isChannelNotifyMuted(activeChannelId);
  const nextMuted = !muted;
  setChannelNotifyMuted(activeChannelId, nextMuted);
  syncHeaderNotifyButton();
  // Request OS notification permission once when enabling general notifications.
  if (!nextMuted) {
    await ensureOsNotificationPermissionFromUserGesture();
  }
});

document.getElementById("btnSidebarCtxToggleNotify")?.addEventListener("click", async (e) => {
  e.stopPropagation();
  if (sidebarCtxChannelId == null || !currentUser) return;
  const muted = isChannelNotifyMuted(sidebarCtxChannelId);
  const nextMuted = !muted;
  setChannelNotifyMuted(sidebarCtxChannelId, nextMuted);
  syncSidebarCtxNotifyButtonLabel();
  syncHeaderNotifyButton();
  closeChannelSidebarContextMenu();
  // Request OS notification permission once when enabling general notifications.
  if (!nextMuted) {
    await ensureOsNotificationPermissionFromUserGesture();
  }
});

document.getElementById("btnSidebarCtxQuickRailPin")?.addEventListener("click", (e) => {
  e.stopPropagation();
  if (sidebarCtxChannelId == null || !currentUser) return;
  toggleQuickRailChannelPin(sidebarCtxChannelId);
  closeChannelSidebarContextMenu();
  if (Array.isArray(lastSidebarChannelsSnapshot) && lastSidebarChannelsSnapshot.length) {
    renderQuickUnreadList(lastSidebarChannelsSnapshot);
  }
});

document.getElementById("btnSidebarCtxLeave")?.addEventListener("click", async (e) => {
  e.stopPropagation();
  const id = sidebarCtxChannelId;
  const name = sidebarCtxChannelName;
  const ctype = sidebarCtxChannelType;
  closeChannelSidebarContextMenu();
  if (id == null) return;
  await leaveChannelAsMember(id, name, ctype);
});

document.getElementById("btnCloseChannel")?.addEventListener("click", async () => {
  if (!activeChannelId || !currentUser) return;
  if (!(await uiConfirm("이 채널을 폐쇄할까요? (복구 불가)"))) return;
  const res = await apiFetch(`/api/channels/${activeChannelId}`, { method: "DELETE" });
  const json = await res.json().catch(() => ({}));
  if (!res.ok) {
    await uiAlert(json.error?.message || "채널 폐쇄에 실패했습니다.");
    return;
  }
  await clearActiveChannelAndReload();
});

document.getElementById("btnAddMembersLater").addEventListener("click", async () => {
  if (!activeChannelId) {
    await uiAlert("채널을 먼저 선택하세요.");
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
    await uiAlert("추가할 사용자를 선택하세요.");
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
function openModal(id) {
  const el = document.getElementById(id);
  if (el) el.classList.remove("hidden");
  if (id === "modalWorkHub") setTopNavActive("projects");
  else if (id === "modalOrgChart") setTopNavActive("team");
}
function closeModal(id) {
  document.getElementById(id)?.classList.add("hidden");
  if (id === "modalImageDownloadChoice") {
    if (typeof imageDownloadChoiceResolve === "function") {
      const r = imageDownloadChoiceResolve;
      imageDownloadChoiceResolve = null;
      r(null);
    }
    if (imageDownloadChoiceEscHandler) {
      document.removeEventListener("keydown", imageDownloadChoiceEscHandler);
      imageDownloadChoiceEscHandler = null;
    }
  }
  if (id === "modalWorkHub") clearWorkHubScopedChannel();
  if (id === "modalWorkHub" || id === "modalOrgChart") syncTopNavFromMainView();
}

document.querySelectorAll(".modal-close, .btn-cancel, .btn-secondary[data-modal]").forEach(btn => {
  btn.addEventListener("click", () => {
    const modalId = btn.dataset.modal;
    if (modalId) closeModal(modalId);
  });
});
document.querySelectorAll(".modal-overlay").forEach(overlay => {
  overlay.addEventListener("click", (e) => {
    if (e.target === overlay) {
      if (overlay.id === "modalAppUpdate") return;
      closeModal(overlay.id);
    }
  });
});

document.getElementById("btnImageDownloadOriginal")?.addEventListener("click", () => {
  finishImageDownloadChoice("original");
});
document.getElementById("btnImageDownloadCompressed")?.addEventListener("click", () => {
  finishImageDownloadChoice("preview");
});
document.getElementById("btnImageDownloadCancel")?.addEventListener("click", () => {
  finishImageDownloadChoice(null);
});

(function setupElectronAutoUpdateModal() {
  const api = window.electronAPI;
  if (!api || typeof api.onUpdateDownloaded !== "function") return;
  api.onUpdateDownloaded((payload) => {
    const verEl = document.getElementById("modalAppUpdateVersion");
    if (verEl) {
      const v = payload && payload.version != null ? String(payload.version).trim() : "";
      verEl.textContent = v ? `설치 예정 버전: ${v}` : "";
    }
    openModal("modalAppUpdate");
  });
  const btnNow = document.getElementById("btnAppUpdateNow");
  if (btnNow) {
    btnNow.addEventListener("click", () => {
      if (typeof api.installUpdateAndRestart === "function") void api.installUpdateAndRestart();
    });
  }
})();

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
document.getElementById("navOrgManagement").addEventListener("click", () => {
  showView("viewOrgManagement");
  void loadOrgManagementPage();
});
document.getElementById("navUserManagement").addEventListener("click", () => {
  showView("viewUserManagement");
  void loadAdminUserPage();
});
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

document.getElementById("btnAddSetting")?.addEventListener("click", async () => {
  const keyEl = document.getElementById("newSettingKey");
  const valEl = document.getElementById("newSettingValue");
  const descEl = document.getElementById("newSettingDesc");
  const key = keyEl?.value?.trim() || "";
  if (!key) {
    await uiAlert("설정 키를 입력하세요.");
    return;
  }
  if (!/^[a-zA-Z0-9._-]+$/.test(key)) {
    await uiAlert("키는 영문·숫자·점·하이픈·밑줄만 사용할 수 있습니다.");
    return;
  }
  const body = {
    key,
    value: valEl?.value?.trim() ?? "",
    description: descEl?.value?.trim() || null,
    updatedBy: currentUser?.employeeNo || null,
  };
  try {
    const r = await apiFetch("/api/admin/settings", {
      method: "POST",
      body: JSON.stringify(body),
    });
    const j = await r.json();
    if (r.ok) {
      await uiAlert(`설정 "${key}" 이(가) 추가되었습니다.`);
      keyEl.value = "";
      if (valEl) valEl.value = "";
      if (descEl) descEl.value = "";
      loadSettings();
    } else {
      await uiAlert(`추가 실패: ${j.error?.message || "오류"}`);
    }
  } catch {
    await uiAlert("서버 연결 오류");
  }
});

const STATUS_LABEL = { UPLOADED: "대기", ACTIVE: "운영중", PREVIOUS: "이전", DEPRECATED: "폐기" };
const STATUS_CLASS = { UPLOADED: "st-uploaded", ACTIVE: "st-active", PREVIOUS: "st-prev", DEPRECATED: "st-dep" };
const ACTION_LABEL = { ACTIVATED: "활성화", ROLLED_BACK: "롤백" };

let cachedReleasesList = [];
let cachedDeployHistoryCount = 0;

function updateReleaseInsightMetrics() {
  const totalEl = document.getElementById("releaseMetricTotal");
  const activeEl = document.getElementById("releaseMetricActiveVersion");
  const histEl = document.getElementById("releaseMetricHistory");
  if (!totalEl || !activeEl || !histEl) return;
  const list = cachedReleasesList;
  const active = list.find(r => r.status === "ACTIVE");
  totalEl.textContent = `${list.length}건`;
  activeEl.textContent = active?.version ?? "—";
  histEl.textContent = `${cachedDeployHistoryCount}건`;
}

async function loadReleases() {
  try {
    const res  = await apiFetch("/api/admin/releases");
    const json = await res.json();
    cachedReleasesList = json.data || [];
    const tbody = document.getElementById("releaseTableBody");
    if (tbody) {
      tbody.innerHTML = "";
      cachedReleasesList.forEach(r => {
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
          if (!(await uiConfirm(`v${btn.dataset.ver}을 운영 버전으로 활성화하시겠습니까?`))) return;
          const r = await apiFetch(`/api/admin/releases/${btn.dataset.id}/activate`, {
            method: "POST",
            body: JSON.stringify({ actorEmployeeNo: currentUser?.employeeNo, note: "수동 활성화" }),
          });
          await uiAlert(r.ok ? "활성화 완료" : "활성화 실패");
          loadReleases();
        });
      });
      tbody.querySelectorAll(".btn-delete").forEach(btn => {
        btn.addEventListener("click", async () => {
          if (!(await uiConfirm("이 릴리즈 파일을 삭제하시겠습니까?"))) return;
          const r = await apiFetch(`/api/admin/releases/${btn.dataset.id}?actorEmployeeNo=${encodeURIComponent(currentUser?.employeeNo || "")}`, {
            method: "DELETE",
          });
          await uiAlert(r.ok ? "삭제 완료" : "삭제 실패");
          loadReleases();
        });
      });
    }
  } catch (e) {
    console.error("릴리즈 로드 실패", e);
    cachedReleasesList = [];
  }

  try {
    const res  = await apiFetch("/api/admin/releases/history");
    const json = await res.json();
    const historyRows = json.data || [];
    cachedDeployHistoryCount = historyRows.length;
    const hbody = document.getElementById("deployHistoryBody");
    if (hbody) {
      hbody.innerHTML = "";
      historyRows.forEach(h => {
        const tr = document.createElement("tr");
        tr.innerHTML = `
        <td>${fmtDate(h.createdAt)}</td>
        <td><span class="action-badge">${ACTION_LABEL[h.action] || h.action}</span></td>
        <td>${h.fromVersion || "-"}</td>
        <td><strong>${escHtml(h.toVersion)}</strong></td>
        <td>${escHtml(h.note || "-")}</td>`;
        hbody.appendChild(tr);
      });
    }
  } catch (e) {
    console.error("배포 이력 로드 실패", e);
    cachedDeployHistoryCount = 0;
  }
  updateReleaseInsightMetrics();
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
        await uiAlert(r.ok ? `"${key}" 설정이 저장되었습니다.` : `저장 실패: ${j.error?.message || "오류"}`);
      });
    });
  } catch { listEl.innerHTML = '<p class="empty-notice">설정 로드 실패</p>'; }
}

/* ==========================================================================
 * 통합 검색
 * ========================================================================== */
const TYPE_ICON  = { MESSAGE: "💬", COMMENT: "🧵", CHANNEL: "#", FILE: "📎", WORK_ITEM: "✅", KANBAN_CARD: "📋" };
const TYPE_LABEL = { MESSAGE: "메시지", COMMENT: "댓글", CHANNEL: "채널", FILE: "파일", WORK_ITEM: "업무", KANBAN_CARD: "칸반" };
const SEARCH_TYPE_ITEM_MAP = {
  ALL: null,
  MESSAGES: ["MESSAGE"],
  COMMENTS: ["COMMENT"],
  CHANNELS: ["CHANNEL"],
  FILES: ["FILE"],
  WORK_ITEMS: ["WORK_ITEM"],
  KANBAN_CARDS: ["KANBAN_CARD"],
};

function resolveChannelMetaForSelect(channelId, fallbackName) {
  const cid = Number(channelId);
  const fallback = { channelId: cid, channelName: fallbackName || "채널", channelType: "PUBLIC" };
  if (!Number.isFinite(cid)) return fallback;
  const el = document.querySelector(`.channel-item[data-channel-id="${cid}"]`);
  if (!el) return fallback;
  return {
    channelId: cid,
    channelName: el.dataset.channelName || fallback.channelName,
    channelType: el.dataset.channelType || fallback.channelType,
  };
}

async function handleSearchResultClick(item) {
  if (!item) return;
  const type = String(item.type || "").toUpperCase();
  const contextId = Number(item.contextId);
  const id = Number(item.id);

  // 메시지 검색: 채널 이동 후 타임라인에서 해당 메시지 포커스
  if (type === "MESSAGE" && Number.isFinite(contextId) && Number.isFinite(id)) {
    const meta = resolveChannelMetaForSelect(contextId, item.contextName || "채널");
    await selectChannel(meta.channelId, meta.channelName, meta.channelType, { targetMessageId: id });
    closeModal("searchModal");
    return;
  }

  // 댓글 검색: 채널 이동 후 스레드 모달에서 해당 댓글(또는 답글) 행으로 스크롤·강조
  if (type === "COMMENT" && Number.isFinite(contextId) && Number.isFinite(id)) {
    const meta = resolveChannelMetaForSelect(contextId, item.contextName || "채널");
    const rootId = Number(item.threadRootMessageId);
    const channelOpts = Number.isFinite(rootId) ? {} : { targetMessageId: id };
    await selectChannel(meta.channelId, meta.channelName, meta.channelType, channelOpts);
    closeModal("searchModal");
    if (Number.isFinite(rootId)) {
      await openThreadModal(rootId, { targetCommentMessageId: id });
    }
    return;
  }

  // 채널 검색 결과: 해당 채널 진입
  if (type === "CHANNEL" && Number.isFinite(id)) {
    const meta = resolveChannelMetaForSelect(id, item.title || item.contextName || "채널");
    await selectChannel(meta.channelId, meta.channelName, meta.channelType);
    closeModal("searchModal");
    return;
  }

  // 업무 항목: 채널 이동 후 업무·칸반 허브에서 해당 행 강조
  if (type === "WORK_ITEM" && Number.isFinite(contextId) && Number.isFinite(id)) {
    const meta = resolveChannelMetaForSelect(contextId, item.contextName || "채널");
    await selectChannel(meta.channelId, meta.channelName, meta.channelType);
    closeModal("searchModal");
    clearWorkHubPendingMaps();
    clearPendingNewKanbanAssignees();
    await Promise.all([
      loadWorkHubChannelMembersForAssignee(),
      loadChannelWorkItems(),
      loadChannelKanbanBoard(),
    ]);
    ensureWorkHubWorkListDeleteBound();
    openModal("modalWorkHub");
    requestAnimationFrame(() => {
      const row = document.querySelector(`#channelWorkItemsList [data-work-item-id="${id}"]`);
      if (row) {
        row.scrollIntoView({ block: "nearest", behavior: "smooth" });
        row.classList.add("channel-work-item-highlight");
        setTimeout(() => row.classList.remove("channel-work-item-highlight"), 2200);
      }
    });
    return;
  }

  // 칸반 카드: 채널 연동 보드만 허브로 이동 후 카드 강조
  if (type === "KANBAN_CARD" && Number.isFinite(id)) {
    const chId = Number(item.relatedChannelId);
    if (!Number.isFinite(chId)) {
      await uiAlert("채널에 연결된 칸반 카드만 업무·칸반 창에서 열 수 있습니다.");
      return;
    }
    const meta = resolveChannelMetaForSelect(chId, item.contextName || "채널");
    await selectChannel(meta.channelId, meta.channelName, meta.channelType);
    closeModal("searchModal");
    clearWorkHubPendingMaps();
    clearPendingNewKanbanAssignees();
    await Promise.all([
      loadWorkHubChannelMembersForAssignee(),
      loadChannelWorkItems(),
      loadChannelKanbanBoard(),
    ]);
    ensureWorkHubWorkListDeleteBound();
    openModal("modalWorkHub");
    requestAnimationFrame(() => {
      const cardEl = document.querySelector(`#channelKanbanBoard .kanban-card-item[data-kanban-card-id="${id}"]`);
      if (cardEl) {
        cardEl.scrollIntoView({ block: "nearest", behavior: "smooth" });
        cardEl.classList.add("kanban-card-highlight");
        setTimeout(() => cardEl.classList.remove("kanban-card-highlight"), 2200);
      }
    });
    return;
  }

  // 파일 검색 결과: 이미지면 라이트박스, 아니면 다운로드
  if (type === "FILE" && Number.isFinite(contextId) && Number.isFinite(id)) {
    const info = await fetchChannelFileDownloadInfo(contextId, id);
    const filename = info?.originalFilename || item.title || "download";
    if (isImageContentType(info?.contentType || "", filename)) {
      try {
        await openChannelImageLightbox(contextId, id, filename, {
          sizeBytes: info?.sizeBytes,
          previewSizeBytes: info?.previewSizeBytes,
          contentType: info?.contentType,
          originalFilename: info?.originalFilename || filename,
          hasPreview: info?.hasPreview,
        });
      } catch {
        await uiAlert("이미지를 불러올 수 없습니다.");
      }
    } else {
      await downloadChannelFile(id, filename, contextId);
    }
    return;
  }
}

document.getElementById("searchForm").addEventListener("submit", async (e) => {
  e.preventDefault();
  const q = document.getElementById("searchInput").value.trim();
  if (q.length === 0) return;
  const selectedType = document.getElementById("searchTypeSelect").value || "ALL";
  const modalInput = document.getElementById("searchModalInput");
  if (modalInput) modalInput.value = q;
  await runSearch(q, selectedType);
});

document.getElementById("searchTypeSelect").addEventListener("change", () => {
  const qModal = document.getElementById("searchModalInput")?.value?.trim?.() || "";
  const qSidebar = document.getElementById("searchInput").value.trim();
  const q = qModal || qSidebar;
  if (q.length === 1) void runSearch(q, document.getElementById("searchTypeSelect").value);
  else if (q.length >= 2) void runSearch(q, document.getElementById("searchTypeSelect").value);
});

document.getElementById("searchModalSubmitBtn")?.addEventListener("click", async () => {
  const q = document.getElementById("searchModalInput")?.value?.trim?.() || "";
  if (q.length === 0) return;
  document.getElementById("searchInput").value = q;
  const selectedType = document.getElementById("searchTypeSelect").value || "ALL";
  await runSearch(q, selectedType);
});

document.getElementById("searchModalInput")?.addEventListener("keydown", async (e) => {
  if (e.key !== "Enter") return;
  e.preventDefault();
  const q = document.getElementById("searchModalInput")?.value?.trim?.() || "";
  if (q.length === 0) return;
  document.getElementById("searchInput").value = q;
  const selectedType = document.getElementById("searchTypeSelect").value || "ALL";
  await runSearch(q, selectedType);
});

async function runSearch(q, type) {
  const resultsEl = document.getElementById("searchResults");
  const qt = String(q || "").trim();
  if (qt.length === 0) return;
  if (qt.length === 1) {
    resultsEl.innerHTML = '<p class="empty-notice">검색어는 두글자 이상부터 가능합니다.</p>';
    const mt = document.getElementById("searchModalTitle");
    if (mt) mt.textContent = "검색";
    const modalInput = document.getElementById("searchModalInput");
    if (modalInput && modalInput.value !== qt) modalInput.value = qt;
    openModal("searchModal");
    return;
  }
  resultsEl.innerHTML = '<p class="empty-notice">검색 중...</p>';
  document.getElementById("searchModalTitle").textContent = `"${qt}" 검색 결과`;
  const modalInput = document.getElementById("searchModalInput");
  if (modalInput && modalInput.value !== qt) modalInput.value = qt;
  openModal("searchModal");
  try {
    const res  = await apiFetch(`/api/search?q=${encodeURIComponent(qt)}&type=${type}&limit=30`);
    const json = await res.json();
    if (!res.ok) { resultsEl.innerHTML = `<p class="empty-notice">${json.error?.message || "오류"}</p>`; return; }
    const rawItems = Array.isArray(json.data?.items) ? json.data.items : [];
    const allowTypes = SEARCH_TYPE_ITEM_MAP[String(type || "ALL").toUpperCase()] || null;
    const items = allowTypes
      ? rawItems.filter((it) => allowTypes.includes(String(it?.type || "").toUpperCase()))
      : rawItems;
    if (items.length === 0) { resultsEl.innerHTML = '<p class="empty-notice">검색 결과가 없습니다.</p>'; return; }
    resultsEl.innerHTML = "";
    items.forEach(item => {
      const div = document.createElement("div");
      div.className = "search-item search-item-clickable";
      div.tabIndex = 0;
      const titleText = String(item.title || "").trim();
      const previewText = String(item.preview || "").trim();
      const showPreview = previewText && previewText !== titleText;
      div.innerHTML = `
        <div class="search-item-type">
          <span class="search-type-badge">${TYPE_ICON[item.type] || ""} ${TYPE_LABEL[item.type] || item.type}</span>
        </div>
        <div class="search-item-body">
          <p class="search-item-title">${escHtml(item.title || "")}</p>
          ${showPreview ? `<p class="search-item-preview">${escHtml(item.preview)}</p>` : ""}
          <p class="search-item-meta">${escHtml(item.contextName || "")} · ${fmtDate(item.createdAt)}</p>
        </div>`;
      div.addEventListener("click", () => { handleSearchResultClick(item); });
      div.addEventListener("keydown", (e) => {
        if (e.key === "Enter" || e.key === " ") {
          e.preventDefault();
          handleSearchResultClick(item);
        }
      });
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

  if (!mentionInboxUiBound) {
    mentionInboxUiBound = true;
    document.getElementById("mentionList")?.addEventListener("click", (e) => {
      const row = e.target.closest(".mention-item[data-mention-id]");
      if (!row) return;
      e.preventDefault();
      openUnreadMentionById(row.dataset.mentionId);
    });
  }

  messagesEl.addEventListener("click", (e) => {
    const t = e.target.closest(".msg-user-trigger");
    const emp = t && String(t.dataset.employeeNo || "").trim();
    if (!emp) return;
    e.preventDefault();
    openUserProfile(emp);
  });

  ensureKanbanNewCardAssigneeUiBound();
  bindModalWorkHubKanbanSuggestKeyboard();

  if (!welcomeDashboardShellBound) {
    welcomeDashboardShellBound = true;
    const focusWelcomeChannelSection = () => {
      const channelList = document.getElementById("channelList");
      const btn = document.querySelector("#channelSectionHeader .section-toggle");
      if (channelList && btn) {
        channelList.classList.remove("hidden");
        syncSectionToggleChevron(btn, channelList);
      }
      channelList?.querySelector(".sidebar-item")?.focus?.();
    };
    const syncHeaderSearchToSidebar = () => {
      const hi = document.getElementById("appHeaderSearchInput");
      const si = document.getElementById("searchInput");
      if (hi && si) si.value = hi.value;
    };
    const syncSidebarSearchToHeader = () => {
      const hi = document.getElementById("appHeaderSearchInput");
      const si = document.getElementById("searchInput");
      if (hi && si) hi.value = si.value;
    };
    const focusSearchInputs = () => {
      const hi = document.getElementById("appHeaderSearchInput");
      const si = document.getElementById("searchInput");
      if (window.matchMedia("(min-width:768px)").matches && hi) {
        hi.focus();
        hi.select?.();
      } else if (si) {
        si.focus();
        si.select?.();
      }
    };
    document.getElementById("btnWelcomeFocusChannel")?.addEventListener("click", focusWelcomeChannelSection);
    document.getElementById("btnWelcomeFocusChannel2")?.addEventListener("click", focusWelcomeChannelSection);
    document.getElementById("btnWelcomeFocusSearch")?.addEventListener("click", focusSearchInputs);
    document.getElementById("welcomeCardThreads")?.addEventListener("click", focusWelcomeChannelSection);
    document.getElementById("welcomeCardKanban")?.addEventListener("click", focusWelcomeChannelSection);
    document.getElementById("welcomeCardOrg")?.addEventListener("click", () => {
      document.getElementById("btnOrgChart")?.click();
    });
    ["welcomeCardThreads", "welcomeCardKanban", "welcomeCardOrg"].forEach((id) => {
      document.getElementById(id)?.addEventListener("keydown", (e) => {
        if (e.key !== "Enter" && e.key !== " ") return;
        e.preventDefault();
        document.getElementById(id)?.click();
      });
    });
    document.getElementById("appHeaderSearchInput")?.addEventListener("input", syncHeaderSearchToSidebar);
    document.getElementById("searchInput")?.addEventListener("input", syncSidebarSearchToHeader);
    document.getElementById("appHeaderSearchInput")?.addEventListener("keydown", (e) => {
      if (e.key === "Enter") {
        e.preventDefault();
        syncHeaderSearchToSidebar();
        document.getElementById("searchForm")?.requestSubmit?.();
      }
    });
    document.getElementById("btnAppHeaderTheme")?.addEventListener("click", () => {
      themeSettingsBtn?.click();
    });
  }
}

/* ==========================================================================
 * AD 자동 로그인 (Electron 전용)
 * ========================================================================== */
async function tryAdAutoLogin() {
  if (typeof window.electronAPI?.getWindowsUsername !== "function") return false;
  try {
    const windowsUsername = await window.electronAPI.getWindowsUsername();
    if (!windowsUsername || !windowsUsername.trim()) return false;
    const res = await fetch(`${API_BASE}/api/auth/ad-login`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ employeeNo: windowsUsername.trim() }),
    });
    const json = await res.json().catch(() => ({}));
    if (!res.ok || json.success === false) {
      console.warn("[ECH] AD 자동 로그인 실패:", json.error?.message || res.status);
      return false;
    }
    const { token, ...user } = json.data;
    saveSession(token, user);
    showMain(user);
    return true;
  } catch (e) {
    console.warn("[ECH] AD 자동 로그인 오류:", e?.message || e);
    return false;
  }
}

/* ==========================================================================
 * 초기화
 * ========================================================================== */
(async function init() {
  const token = getToken();
  const user  = getUser();
  if (!token || !user) {
    // Electron 환경에서 AD 자동 로그인 시도
    const adOk = await tryAdAutoLogin();
    if (!adOk) showLogin();
    return;
  }

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
      // 토큰 만료 시에도 AD 자동 로그인 재시도
      const adOk = await tryAdAutoLogin();
      if (!adOk) showLogin();
    }
  } catch {
    clearSession();
    const adOk = await tryAdAutoLogin();
    if (!adOk) showLogin();
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

/* ==========================================================================
 * 관리자 - 사용자 관리
 * ========================================================================== */
let adminUserList = [];          // 서버에서 로드한 원본 목록
let adminUserOrgOptions = null;  // 조직 드롭다운 옵션 (teams/jobLevels/...)
let adminUserOrgTree = [];       // 전체 조직 그룹
let adminUserSelectedOrgCode = null;         // 좌측 패널에서 선택한 조직 코드 (null = 전체)
let userOrgPanelExpanded = new Set();        // 좌측 패널 트리 펼침 상태
/** employeeNo → { op: 'create'|'update'|'delete', data: {...} } */
const adminUserPendingChanges = new Map();

async function loadAdminUserPage() {
  try {
    const [usersRes, orgRes, treeRes] = await Promise.all([
      apiFetch("/api/admin/users"),
      apiFetch("/api/admin/users/org-options"),
      apiFetch("/api/admin/org-groups"),
    ]);
    const usersJson = await usersRes.json().catch(() => ({}));
    const orgJson   = await orgRes.json().catch(() => ({}));
    const treeJson  = await treeRes.json().catch(() => ({}));
    if (!usersRes.ok) throw new Error(usersJson.error?.message || "사용자 목록 조회 실패");
    if (!orgRes.ok)   throw new Error(orgJson.error?.message   || "조직 정보 조회 실패");
    adminUserList       = usersJson.data || [];
    adminUserOrgOptions = orgJson.data  || { teams: [], jobLevels: [], jobPositions: [], jobTitles: [] };
    adminUserOrgTree    = treeJson.data || [];
    // 처음 로드 시 모든 조직 노드 펼침
    userOrgPanelExpanded = new Set(adminUserOrgTree.map(g => g.groupCode));
    adminUserSelectedOrgCode = null;
    adminUserPendingChanges.clear();
    renderAdminUserView();
  } catch (e) {
    await uiAlert(e?.message || "사용자 목록을 불러오지 못했습니다.");
  }
}

function renderAdminUserView() {
  renderUserOrgPanel();
  renderAdminUserTable();
}

// ── 좌측 조직 선택 패널 렌더 ─────────────────────────────────────────────
function getAllDescendantTeamCodes(orgCode, structural) {
  const result = [];
  function traverse(code) {
    const kids = structural.filter(g => g.memberOfGroupCode === code);
    for (const kid of kids) {
      if (kid.groupType === "TEAM") result.push(kid.groupCode);
      traverse(kid.groupCode);
    }
  }
  traverse(orgCode);
  return result;
}

function renderUserOrgPanel() {
  const container = document.getElementById("userOrgTree");
  if (!container) return;

  const structural = adminUserOrgTree.filter(g => ["COMPANY","DIVISION","TEAM"].includes(g.groupType));
  const orgMap  = new Map(structural.map(g => [g.groupCode, g]));
  const getKids = (code) => structural.filter(g => g.memberOfGroupCode === code);
  const roots   = structural.filter(g => g.groupType === "COMPANY" && (!g.memberOfGroupCode || !orgMap.has(g.memberOfGroupCode)));
  const allUsers = buildEffectiveAdminUserList();

  const activeUsers = (list) => list.filter(u => adminUserPendingChanges.get(u.employeeNo)?.op !== "delete");

  function countForOrg(orgCode) {
    if (!orgMap.has(orgCode)) return 0;
    // 해당 노드에 직속 배정된 인원 수만 표시
    return activeUsers(allUsers).filter(u => {
      const tc = adminUserPendingChanges.get(u.employeeNo)?.data?.teamGroupCode ?? u.teamGroupCode;
      return tc === orgCode;
    }).length;
  }

  const parts = [];
  const totalCount = activeUsers(allUsers).length;
  const allSel = adminUserSelectedOrgCode === null;
  parts.push(`<div class="uorg-item${allSel ? " selected" : ""}" data-uorg-select="__all__">
    <span class="uorg-label">📋 전체</span>
    <span class="uorg-count">${totalCount}</span>
  </div>`);

  const TYPE_LABEL = { COMPANY:"회사", DIVISION:"본부", TEAM:"팀" };
  const TYPE_CLS   = { COMPANY:"ot-company", DIVISION:"ot-division", TEAM:"ot-team" };

  function renderNode(g, depth) {
    const isExp  = userOrgPanelExpanded.has(g.groupCode);
    const kids   = getKids(g.groupCode);
    const count  = countForOrg(g.groupCode);
    const isSel  = adminUserSelectedOrgCode === g.groupCode;
    const indent = 10 + depth * 16;
    parts.push(`<div class="uorg-item${isSel ? " selected" : ""}" data-uorg-select="${escHtml(g.groupCode)}" style="padding-left:${indent}px">
      ${kids.length ? `<button class="uorg-toggle" data-uorg-toggle="${escHtml(g.groupCode)}">${isExp ? "▾" : "▸"}</button>` : `<span class="uorg-bullet">·</span>`}
      <span class="org-type-badge ${TYPE_CLS[g.groupType] || ""} uorg-badge">${TYPE_LABEL[g.groupType] || g.groupType}</span>
      <span class="uorg-label">${escHtml(g.displayName)}</span>
      ${count > 0 ? `<span class="uorg-count">${count}</span>` : ""}
    </div>`);
    if (isExp) for (const kid of kids) renderNode(kid, depth + 1);
  }

  for (const root of roots) renderNode(root, 0);

  const unassignedCount = activeUsers(allUsers).filter(u => {
    const tc = adminUserPendingChanges.get(u.employeeNo)?.data?.teamGroupCode ?? u.teamGroupCode;
    return !tc;
  }).length;
  if (unassignedCount > 0) {
    const isSel = adminUserSelectedOrgCode === "__unassigned__";
    parts.push(`<div class="uorg-item${isSel ? " selected" : ""}" data-uorg-select="__unassigned__" style="padding-left:10px">
      <span class="uorg-bullet">·</span>
      <span class="uorg-label">미배정</span>
      <span class="uorg-count">${unassignedCount}</span>
    </div>`);
  }

  container.innerHTML = parts.join("");
}

// 좌측 패널 클릭: 조직 선택 or 토글
document.getElementById("userOrgTree").addEventListener("click", (e) => {
  const toggleBtn = e.target.closest("[data-uorg-toggle]");
  if (toggleBtn) {
    const code = toggleBtn.dataset.uorgToggle;
    if (userOrgPanelExpanded.has(code)) userOrgPanelExpanded.delete(code);
    else userOrgPanelExpanded.add(code);
    renderUserOrgPanel();
    return;
  }
  const item = e.target.closest("[data-uorg-select]");
  if (item) {
    const code = item.dataset.uorgSelect;
    adminUserSelectedOrgCode = code === "__all__" ? null : code;
    renderAdminUserView();
  }
});

/** org options 기반 인라인 셀렉트 HTML 생성 헬퍼 */
function buildOrgInlineSelect(field, empNo, currentCode, options, isDeleted) {
  if (isDeleted) {
    const found = options.find(o => o.groupCode === currentCode);
    return `<span class="text-muted">${found ? escHtml(found.displayName) : "-"}</span>`;
  }
  const opts = options.map(o =>
    `<option value="${escHtml(o.groupCode)}" ${o.groupCode === currentCode ? "selected" : ""}>${escHtml(o.displayName)}</option>`
  ).join("");
  return `<select class="inline-select inline-select-org" data-field="${field}" data-emp="${escHtml(empNo)}">
    <option value="">(없음)</option>
    ${opts}
  </select>`;
}

function renderAdminUserTable() {
  const tbody = document.getElementById("adminUserTableBody");
  if (!tbody) return;

  const allRows = buildEffectiveAdminUserList();
  const structural = adminUserOrgTree.filter(g => ["COMPANY","DIVISION","TEAM"].includes(g.groupType));

  // 좌측 패널 선택에 따른 필터링
  let rows;
  let panelTitle = "전체";
  if (adminUserSelectedOrgCode === null) {
    rows = allRows;
  } else if (adminUserSelectedOrgCode === "__unassigned__") {
    rows = allRows.filter(u => {
      const tc = adminUserPendingChanges.get(u.employeeNo)?.data?.teamGroupCode ?? u.teamGroupCode;
      return !tc;
    });
    panelTitle = "미배정";
  } else {
    const org = adminUserOrgTree.find(g => g.groupCode === adminUserSelectedOrgCode);
    // 회사/본부/팀 모두 해당 노드에 직속 배정된 인원만 표시
    rows = allRows.filter(u => {
      const tc = adminUserPendingChanges.get(u.employeeNo)?.data?.teamGroupCode ?? u.teamGroupCode;
      return tc === adminUserSelectedOrgCode;
    });
    panelTitle = org?.displayName || adminUserSelectedOrgCode;
  }

  // 우측 패널 헤더 업데이트
  const titleEl = document.getElementById("userListPanelTitle");
  const countEl = document.getElementById("userListPanelCount");
  const metricScopeEl = document.getElementById("adminUserMetricScope");
  const metricCountEl = document.getElementById("adminUserMetricCount");
  const activeCount = rows.filter(u => adminUserPendingChanges.get(u.employeeNo)?.op !== "delete").length;
  if (titleEl) titleEl.textContent = panelTitle;
  if (countEl) countEl.textContent = `${activeCount}명`;
  if (metricScopeEl) metricScopeEl.textContent = panelTitle;
  if (metricCountEl) metricCountEl.textContent = `${activeCount}명`;

  const opts = adminUserOrgOptions || { teams: [], jobLevels: [], jobPositions: [], jobTitles: [] };

  if (!rows.length) {
    tbody.innerHTML = `<tr><td colspan="10" style="text-align:center;color:var(--text-muted);padding:24px">등록된 사용자가 없습니다.</td></tr>`;
    updateAdminUserPendingBanner();
    return;
  }

  const sortedRows = sortOrgDirectoryMembers(rows);
  tbody.innerHTML = sortedRows.map(row => renderUserRow(row, opts, 0)).join("");
  updateAdminUserPendingBanner();
}

function buildEffectiveAdminUserList() {
  const map = new Map(adminUserList.map(u => [u.employeeNo, { ...u }]));
  for (const [empNo, change] of adminUserPendingChanges) {
    if (change.op === "create")  map.set(empNo, change.data);
    else if (change.op === "update" && map.has(empNo))
      map.set(empNo, { ...map.get(empNo), ...change.data });
    // delete: 행은 남기되 renderAdminUserTable에서 스타일 처리
  }
  return [...map.values()];
}

function updateAdminUserPendingBanner() {
  const count = adminUserPendingChanges.size;
  const banner   = document.getElementById("adminUserPendingBanner");
  const countEl  = document.getElementById("adminUserPendingCount");
  const mirrorEl = document.getElementById("adminUserPendingCountMirror");
  if (banner)  banner.classList.toggle("hidden", count === 0);
  if (countEl) countEl.textContent = String(count);
  if (mirrorEl) mirrorEl.textContent = String(count);
}

// ── 사용자 공통 행 렌더 헬퍼 ──────────────────────────────────────────────
function renderUserRow(row, opts, indentPx) {
  const pending   = adminUserPendingChanges.get(row.employeeNo);
  const isNew     = pending?.op === "create";
  const isDeleted = pending?.op === "delete";
  const isModified= pending?.op === "update";
  const eff       = pending?.data ?? row;
  let rowClass = isNew ? "admin-row-new" : isDeleted ? "admin-row-deleted" : isModified ? "admin-row-modified" : "";

  const curRole   = eff.role   ?? "MEMBER";
  const curStatus = eff.status ?? "ACTIVE";
  const curTeam   = eff.teamGroupCode          ?? null;
  const curLevel  = eff.jobLevelGroupCode      ?? null;
  const curPos    = eff.jobPositionGroupCode   ?? null;
  const curTitle  = eff.jobTitleGroupCode      ?? null;
  const emp       = row.employeeNo;

  const createdAt = row.createdAt
    ? new Date(row.createdAt).toLocaleDateString("ko-KR", { year:"2-digit", month:"2-digit", day:"2-digit" })
    : "-";

  const roleSelect = isDeleted
    ? `<span class="text-muted">${curRole==="ADMIN"?"관리자":curRole==="MANAGER"?"매니저":"일반"}</span>`
    : `<select class="inline-select" data-field="role" data-emp="${escHtml(emp)}">
         <option value="MEMBER"  ${curRole==="MEMBER"  ?"selected":""}>일반</option>
         <option value="MANAGER" ${curRole==="MANAGER" ?"selected":""}>매니저</option>
         <option value="ADMIN"   ${curRole==="ADMIN"   ?"selected":""}>관리자</option>
       </select>`;

  const statusSelect = isDeleted
    ? `<span class="badge badge-inactive">삭제예정</span>`
    : `<select class="inline-select status-select" data-field="status" data-emp="${escHtml(emp)}">
         <option value="ACTIVE"   ${curStatus==="ACTIVE"  ?"selected":""}>사용</option>
         <option value="INACTIVE" ${curStatus==="INACTIVE"?"selected":""}>미사용</option>
       </select>`;

  const teamSel  = buildOrgInlineSelect("teamGroupCode",        emp, curTeam,  opts.teams,        isDeleted);
  const levelSel = buildOrgInlineSelect("jobLevelGroupCode",    emp, curLevel, opts.jobLevels,    isDeleted);
  const posSel   = buildOrgInlineSelect("jobPositionGroupCode", emp, curPos,   opts.jobPositions, isDeleted);
  const titleSel = buildOrgInlineSelect("jobTitleGroupCode",    emp, curTitle, opts.jobTitles,    isDeleted);

  const firstCellStyle = indentPx > 0 ? ` style="padding-left:${indentPx}px"` : "";
  return `<tr class="${rowClass}" data-emp="${escHtml(emp)}" title="우클릭: 편집/삭제">
    <td${firstCellStyle}><code class="emp-code">${escHtml(emp)}</code></td>
    <td>${escHtml(row.name || "")}</td>
    <td class="cell-email">${escHtml(row.email || "")}</td>
    <td>${teamSel}</td>
    <td>${levelSel}</td>
    <td>${posSel}</td>
    <td>${titleSel}</td>
    <td>${roleSelect}</td>
    <td>${statusSelect}</td>
    <td class="cell-date">${createdAt}</td>
  </tr>`;
}


function openAdminUserEditModal(employeeNo) {
  const isNew = !employeeNo;
  document.getElementById("adminUserEditTitle").textContent = isNew ? "새 사용자 등록" : "사용자 편집";
  const empNoInput = document.getElementById("auEmpNo");
  empNoInput.disabled = false; // 편집 시에도 사원번호 수정 가능
  // 변경 경고 초기화
  document.getElementById("auEmpNoChangeWarn").classList.add("hidden");
  empNoInput.dataset.originalEmpNo = employeeNo || "";

  // 폼 초기화
  document.getElementById("auEmpNo").value = "";
  document.getElementById("auName").value  = "";
  document.getElementById("auEmail").value = "";
  document.getElementById("auRole").value  = "MEMBER";
  document.getElementById("auStatus").value = "ACTIVE";
  document.getElementById("adminUserEditError").textContent = "";
  document.getElementById("adminUserEditError").classList.add("hidden");

  // 조직 드롭다운 채우기
  populateAdminUserOrgDropdowns();

  if (!isNew) {
    const pending = adminUserPendingChanges.get(employeeNo);
    const base    = adminUserList.find(u => u.employeeNo === employeeNo);
    const data    = pending?.data ?? base ?? {};
    document.getElementById("auEmpNo").value       = data.employeeNo        || "";
    document.getElementById("auName").value         = data.name              || "";
    document.getElementById("auEmail").value        = data.email             || "";
    document.getElementById("auRole").value         = data.role              || "MEMBER";
    document.getElementById("auStatus").value       = data.status            || "ACTIVE";
    document.getElementById("auTeam").value         = data.teamGroupCode     || "";
    document.getElementById("auJobLevel").value     = data.jobLevelGroupCode || "";
    document.getElementById("auJobPosition").value  = data.jobPositionGroupCode || "";
    document.getElementById("auJobTitle").value     = data.jobTitleGroupCode || "";
  }

  document.getElementById("btnAdminUserEditConfirm").dataset.editEmp = employeeNo || "";
  openModal("modalAdminUserEdit");
}

function populateAdminUserOrgDropdowns() {
  if (!adminUserOrgOptions) return;
  const fill = (selId, opts) => {
    const sel = document.getElementById(selId);
    if (!sel) return;
    const prev = sel.value;
    sel.innerHTML = `<option value="">선택 안함</option>` +
      (opts || []).map(o => `<option value="${escHtml(o.groupCode)}">${escHtml(o.displayName)}</option>`).join("");
    sel.value = prev;
  };
  fill("auTeam",        adminUserOrgOptions.teams        || []);
  fill("auJobLevel",    adminUserOrgOptions.jobLevels    || []);
  fill("auJobPosition", adminUserOrgOptions.jobPositions || []);
  fill("auJobTitle",    adminUserOrgOptions.jobTitles    || []);
}

function resolveOrgDisplayName(groupCode, type) {
  if (!adminUserOrgOptions || !groupCode) return null;
  const map = { TEAM: "teams", JOB_LEVEL: "jobLevels", JOB_POSITION: "jobPositions", JOB_TITLE: "jobTitles" };
  const arr = adminUserOrgOptions[map[type]] || [];
  return arr.find(o => o.groupCode === groupCode)?.displayName || null;
}

// 사용자 관리 버튼/테이블 이벤트 위임
document.getElementById("btnAdminUserNew").addEventListener("click", () => openAdminUserEditModal(null));
document.getElementById("btnAdminUserReset").addEventListener("click", () => {
  adminUserPendingChanges.clear();
  renderAdminUserTable();
});

// ── 인라인 셀렉트(역할/상태) 즉시 반영 ─────────────────────────────────────
// org 그룹코드 필드 → 대응 displayName 필드 매핑
const ORG_CODE_TO_DISPLAY = {
  teamGroupCode:        "teamDisplayName",
  jobLevelGroupCode:    "jobLevelDisplayName",
  jobPositionGroupCode: "jobPositionDisplayName",
  jobTitleGroupCode:    "jobTitleDisplayName",
};
// org 그룹코드 필드 → adminUserOrgOptions 키 매핑
const ORG_CODE_TO_OPTS_KEY = {
  teamGroupCode:        "teams",
  jobLevelGroupCode:    "jobLevels",
  jobPositionGroupCode: "jobPositions",
  jobTitleGroupCode:    "jobTitles",
};

document.getElementById("adminUserTableBody").addEventListener("change", (e) => {
  const sel = e.target.closest("select[data-field]");
  if (!sel) return;
  const empNo = sel.dataset.emp;
  const field = sel.dataset.field;
  const value = sel.value;

  const base     = adminUserList.find(u => u.employeeNo === empNo);
  const existing = adminUserPendingChanges.get(empNo);
  if (existing?.op === "delete") return;

  // org 필드 변경 시 displayName 도 함께 갱신
  const extraFields = {};
  const displayField = ORG_CODE_TO_DISPLAY[field];
  if (displayField) {
    const optsKey  = ORG_CODE_TO_OPTS_KEY[field];
    const optsList = adminUserOrgOptions?.[optsKey] ?? [];
    const matched  = optsList.find(o => o.groupCode === value);
    extraFields[displayField] = matched?.displayName ?? null;
  }

  if (existing) {
    existing.data = { ...existing.data, [field]: value, ...extraFields };
    if (existing.op !== "create") existing.op = "update";
    adminUserPendingChanges.set(empNo, existing);
  } else if (base) {
    adminUserPendingChanges.set(empNo, { op: "update", data: { ...base, [field]: value, ...extraFields } });
  }

  const row = sel.closest("tr[data-emp]");
  if (row) {
    row.classList.remove("admin-row-modified", "admin-row-new");
    const p = adminUserPendingChanges.get(empNo);
    if (p?.op === "update") row.classList.add("admin-row-modified");
    else if (p?.op === "create") row.classList.add("admin-row-new");
  }
  updateAdminUserPendingBanner();
});

// ── 우클릭 컨텍스트 메뉴 ──────────────────────────────────────────────────
let adminUserCtxEmp = null;

document.getElementById("adminUserTableBody").addEventListener("contextmenu", (e) => {
  e.preventDefault();
  const row = e.target.closest("tr[data-emp]");
  if (!row) return;
  adminUserCtxEmp = row.dataset.emp;

  const isDeleted = adminUserPendingChanges.get(adminUserCtxEmp)?.op === "delete";
  const menu = document.getElementById("adminUserContextMenu");
  menu.querySelector("[data-action=ctx-delete]").classList.toggle("hidden", isDeleted);
  menu.querySelector("[data-action=ctx-restore]").classList.toggle("hidden", !isDeleted);

  let x = e.clientX, y = e.clientY;
  // 화면 경계 보정
  const mw = 160, mh = 100;
  if (x + mw > window.innerWidth)  x = window.innerWidth  - mw - 4;
  if (y + mh > window.innerHeight) y = window.innerHeight - mh - 4;
  menu.style.left = x + "px";
  menu.style.top  = y + "px";
  menu.classList.remove("hidden");
});

document.getElementById("adminUserContextMenu").addEventListener("click", (e) => {
  const btn = e.target.closest("[data-action]");
  if (!btn) return;
  const empNo  = adminUserCtxEmp;
  const action = btn.dataset.action;
  document.getElementById("adminUserContextMenu").classList.add("hidden");
  adminUserCtxEmp = null;

  if (action === "ctx-edit") { openAdminUserEditModal(empNo); return; }
  if (action === "ctx-restore") { adminUserPendingChanges.delete(empNo); renderAdminUserTable(); return; }
  if (action === "ctx-delete") {
    const base     = adminUserList.find(u => u.employeeNo === empNo);
    const isNewRow = adminUserPendingChanges.get(empNo)?.op === "create";
    if (isNewRow) adminUserPendingChanges.delete(empNo);
    else if (base) adminUserPendingChanges.set(empNo, { op: "delete", data: base });
    renderAdminUserTable();
  }
});

document.addEventListener("click", () => {
  document.getElementById("adminUserContextMenu")?.classList.add("hidden");
});

// 사원번호 입력 시 변경 경고 표시
document.getElementById("auEmpNo").addEventListener("input", (e) => {
  const original = e.target.dataset.originalEmpNo || "";
  const warn = document.getElementById("auEmpNoChangeWarn");
  if (warn) warn.classList.toggle("hidden", !original || e.target.value.trim() === original);
});

document.getElementById("btnAdminUserEditConfirm").addEventListener("click", () => {
  const empNoEl  = document.getElementById("auEmpNo");
  const editEmp  = document.getElementById("btnAdminUserEditConfirm").dataset.editEmp;
  const originalEmpNo = empNoEl.dataset.originalEmpNo || editEmp || "";
  const isNew    = !editEmp;

  const newEmpNo = empNoEl.value.trim();
  const name     = document.getElementById("auName").value.trim();
  const email    = document.getElementById("auEmail").value.trim();
  const role     = document.getElementById("auRole").value;
  const status   = document.getElementById("auStatus").value;
  const teamGroupCode         = document.getElementById("auTeam").value;
  const jobLevelGroupCode     = document.getElementById("auJobLevel").value;
  const jobPositionGroupCode  = document.getElementById("auJobPosition").value;
  const jobTitleGroupCode     = document.getElementById("auJobTitle").value;

  const errEl = document.getElementById("adminUserEditError");
  if (!newEmpNo) { errEl.textContent = "사원번호를 입력하세요."; errEl.classList.remove("hidden"); return; }
  if (!name)     { errEl.textContent = "이름을 입력하세요.";     errEl.classList.remove("hidden"); return; }
  if (!email)    { errEl.textContent = "이메일을 입력하세요.";   errEl.classList.remove("hidden"); return; }
  errEl.classList.add("hidden");

  // 신규: 중복 검사
  if (isNew && adminUserList.some(u => u.employeeNo === newEmpNo) && adminUserPendingChanges.get(newEmpNo)?.op !== "delete") {
    errEl.textContent = "이미 존재하는 사원번호입니다."; errEl.classList.remove("hidden"); return;
  }
  // 편집: 새 사원번호가 다른 기존 사용자와 충돌하는지 검사
  if (!isNew && newEmpNo !== originalEmpNo) {
    const conflict = adminUserList.some(u => u.employeeNo === newEmpNo)
                   || [...adminUserPendingChanges.values()].some(c => c.data?.employeeNo === newEmpNo && c.op !== "delete");
    if (conflict) { errEl.textContent = "이미 존재하는 사원번호입니다."; errEl.classList.remove("hidden"); return; }
  }

  const data = {
    employeeNo: newEmpNo, name, email, role, status,
    teamGroupCode, jobLevelGroupCode, jobPositionGroupCode, jobTitleGroupCode,
    teamDisplayName:        resolveOrgDisplayName(teamGroupCode,        "TEAM"),
    jobLevelDisplayName:    resolveOrgDisplayName(jobLevelGroupCode,    "JOB_LEVEL"),
    jobPositionDisplayName: resolveOrgDisplayName(jobPositionGroupCode, "JOB_POSITION"),
    jobTitleDisplayName:    resolveOrgDisplayName(jobTitleGroupCode,    "JOB_TITLE"),
  };

  // 사원번호가 변경된 경우: 원래 키로 pending 저장 (저장 시 URL = 원래 사원번호, body.employeeNo = 새 사원번호)
  const pendingKey = isNew ? newEmpNo : originalEmpNo;
  adminUserPendingChanges.set(pendingKey, { op: isNew ? "create" : "update", data });
  closeModal("modalAdminUserEdit");
  renderAdminUserView();
});

document.getElementById("btnAdminUserSave").addEventListener("click", async () => {
  if (adminUserPendingChanges.size === 0) { await uiAlert("저장할 변경사항이 없습니다."); return; }
  if (!(await uiConfirm(`변경사항 ${adminUserPendingChanges.size}건을 저장하시겠습니까?`))) return;

  const errors = [];
  for (const [empNo, change] of adminUserPendingChanges) {
    try {
      if (change.op === "delete") {
        const res = await apiFetch(`/api/admin/users/${encodeURIComponent(empNo)}`, { method: "DELETE" });
        if (!res.ok) {
          const j = await res.json().catch(() => ({}));
          errors.push(`[삭제 ${empNo}] ${j.error?.message || res.status}`);
        }
      } else if (change.op === "create") {
        const res = await apiFetch("/api/admin/users", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(change.data),
        });
        if (!res.ok) {
          const j = await res.json().catch(() => ({}));
          errors.push(`[등록 ${empNo}] ${j.error?.message || res.status}`);
        }
      } else if (change.op === "update") {
        const res = await apiFetch(`/api/admin/users/${encodeURIComponent(empNo)}`, {
          method: "PUT",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(change.data),
        });
        if (!res.ok) {
          const j = await res.json().catch(() => ({}));
          errors.push(`[수정 ${empNo}] ${j.error?.message || res.status}`);
        }
      }
    } catch (e) {
      errors.push(`[${empNo}] 네트워크 오류`);
    }
  }

  if (errors.length) {
    await uiAlert("일부 저장 실패:\n" + errors.join("\n"));
  }

  // 저장 후 목록 새로고침
  await loadAdminUserPage();
});

/* ==========================================================================
 * 관리자 - 조직 관리
 * ========================================================================== */
let orgGroupList = [];                       // 서버에서 로드한 원본 목록
let orgEditingCode = null;                   // 현재 편집 중인 코드 (null = 신규)
const orgPendingChanges = new Map();         // groupCode → { op, data }
let orgExpandedSet = new Set();              // 현재 펼쳐진 노드 코드 집합

// ─── 데이터 헬퍼 ──────────────────────────────────────────────────────────
function buildEffectiveOrgList() {
  const map = new Map(orgGroupList.map(g => [g.groupCode, { ...g }]));
  for (const [code, change] of orgPendingChanges) {
    if (change.op === "create")       map.set(code, { ...change.data, _pending: "create" });
    else if (change.op === "update")  map.set(code, { ...map.get(code), ...change.data, _pending: "update" });
    else if (change.op === "delete" && map.has(code)) map.set(code, { ...map.get(code), _pending: "delete" });
  }
  return [...map.values()];
}

function updateOrgPendingBanner() {
  const count   = orgPendingChanges.size;
  const banner  = document.getElementById("orgPendingBanner");
  const countEl = document.getElementById("orgPendingCount");
  const mirrorEl = document.getElementById("orgPendingCountMirror");
  if (banner)  banner.classList.toggle("hidden", count === 0);
  if (countEl) countEl.textContent = String(count);
  if (mirrorEl) mirrorEl.textContent = String(count);
}

/** 조직 관리 상단 인사이트 카드: 현재 탭·항목 수 */
function updateOrgInsightMetrics() {
  const eff = buildEffectiveOrgList();
  const structural = eff.filter(g => ["COMPANY", "DIVISION", "TEAM"].includes(g.groupType));
  const jobLevels = eff.filter(g => g.groupType === "JOB_LEVEL");
  const jobPositions = eff.filter(g => g.groupType === "JOB_POSITION");
  const jobTitles = eff.filter(g => g.groupType === "JOB_TITLE");
  const active = document.querySelector("#viewOrgManagement .org-tab.active");
  const tab = active?.dataset?.tab || "structure";
  const LABEL = {
    structure: "조직 구조",
    joblevel: "직급",
    jobposition: "직위",
    jobtitle: "직책"
  };
  const labelEl = document.getElementById("orgMetricTabLabel");
  const countEl = document.getElementById("orgMetricItemCount");
  if (labelEl) labelEl.textContent = LABEL[tab] || tab;
  let n = 0;
  if (tab === "structure") n = structural.length;
  else if (tab === "joblevel") n = jobLevels.length;
  else if (tab === "jobposition") n = jobPositions.length;
  else if (tab === "jobtitle") n = jobTitles.length;
  if (countEl) countEl.textContent = `${n}개`;
}

// ─── 로드 ──────────────────────────────────────────────────────────────────
async function loadOrgManagementPage() {
  try {
    const res  = await apiFetch("/api/admin/org-groups");
    const json = await res.json().catch(() => ({}));
    if (!res.ok) throw new Error(json.error?.message || "조직 목록 조회 실패");
    orgGroupList = json.data || [];
    // 새로 로드 시 모든 노드를 펼침 (기존 expand 상태 초기화)
    orgExpandedSet = new Set(orgGroupList.map(g => g.groupCode));
    orgPendingChanges.clear();
    renderOrgManagement();
  } catch (e) {
    await uiAlert(e?.message || "조직 목록을 불러오지 못했습니다.");
  }
}

// ─── 렌더 ──────────────────────────────────────────────────────────────────
function renderOrgManagement() {
  const eff = buildEffectiveOrgList();
  const structural  = eff.filter(g => ["COMPANY","DIVISION","TEAM"].includes(g.groupType));
  const jobLevels   = eff.filter(g => g.groupType === "JOB_LEVEL");
  const jobPositions= eff.filter(g => g.groupType === "JOB_POSITION");
  const jobTitles   = eff.filter(g => g.groupType === "JOB_TITLE");

  const treeEl = document.getElementById("orgTreeContainer");
  if (treeEl) treeEl.innerHTML = buildOrgTreeHtml(structural) || `<p class="org-empty">등록된 조직이 없습니다.<br><small>위 [+ 회사 추가] 버튼으로 최상위 조직을 추가하세요.</small></p>`;

  renderOrgFlatList("orgJobLevelList",    jobLevels);
  renderOrgFlatList("orgJobPositionList", jobPositions);
  renderOrgFlatList("orgJobTitleList",    jobTitles);
  updateOrgPendingBanner();
  updateOrgInsightMetrics();
}

function buildOrgTreeHtml(structural) {
  const map      = new Map(structural.map(g => [g.groupCode, g]));
  const roots    = structural.filter(g => !g.memberOfGroupCode || !map.has(g.memberOfGroupCode));
  const getKids  = (code) => structural.filter(g => g.memberOfGroupCode === code);

  const TYPE_LABEL = { COMPANY:"회사", DIVISION:"본부/부서", TEAM:"팀" };
  const TYPE_CLASS = { COMPANY:"ot-company", DIVISION:"ot-division", TEAM:"ot-team" };

  function renderNode(g, depth) {
    const kids       = getKids(g.groupCode);
    const isExpanded = orgExpandedSet.has(g.groupCode);
    const typeLabel  = TYPE_LABEL[g.groupType] || g.groupType;
    const typeCls    = TYPE_CLASS[g.groupType] || "";
    const inactiveCls = g.isActive ? "" : "org-node-inactive";
    const pendingCls  = g._pending === "create" ? "org-pending-create"
                      : g._pending === "update" ? "org-pending-update"
                      : g._pending === "delete" ? "org-pending-delete" : "";
    const indent   = depth * 22;

    const toggleBtn = kids.length
      ? `<button class="org-tree-toggle-btn" data-org-toggle="${escHtml(g.groupCode)}" title="${isExpanded ? "접기" : "펼치기"}">${isExpanded ? "▼" : "▶"}</button>`
      : `<span class="org-tree-bullet">·</span>`;

    const deleteLabel = g._pending === "delete" ? "복원" : "삭제";
    const deleteAction = g._pending === "delete" ? "org-restore" : "org-delete";

    const node = `
      <div class="org-tree-node ${inactiveCls} ${pendingCls}" data-code="${escHtml(g.groupCode)}" style="margin-left:${indent}px">
        <div class="org-tree-row">
          ${toggleBtn}
          <span class="org-type-badge ${typeCls}">${typeLabel}</span>
          <span class="org-display-name">${escHtml(g.displayName)}</span>
          <code class="org-code-small">${escHtml(g.groupCode)}</code>
          ${!g.isActive ? '<span class="org-badge-inactive">비활성</span>' : ""}
          ${g._pending ? `<span class="org-pending-badge">${g._pending === "create" ? "추가" : g._pending === "update" ? "수정" : "삭제예정"}</span>` : ""}
          <div class="org-node-btns">
            ${g.groupType !== "TEAM" && g._pending !== "delete" ? `<button class="btn-xs btn-secondary" data-action="org-add-child" data-parent="${escHtml(g.groupCode)}" data-ptype="${escHtml(g.groupType)}">+ 하위</button>` : ""}
            ${g._pending !== "delete" ? `<button class="btn-xs btn-secondary" data-action="org-edit" data-code="${escHtml(g.groupCode)}">편집</button>` : ""}
            <button class="btn-xs ${g._pending === "delete" ? "btn-secondary" : "btn-danger"}" data-action="${deleteAction}" data-code="${escHtml(g.groupCode)}">${deleteLabel}</button>
          </div>
        </div>
      </div>`;
    const childrenHtml = isExpanded ? kids.map(k => renderNode(k, depth + 1)).join("") : "";
    return node + childrenHtml;
  }
  return roots.map(r => renderNode(r, 0)).join("");
}

function renderOrgFlatList(containerId, groups) {
  const el = document.getElementById(containerId);
  if (!el) return;
  const visible = groups.filter(g => g._pending !== "delete");
  const deleted = groups.filter(g => g._pending === "delete");
  const all = [...visible.sort((a,b) => a.sortOrder - b.sortOrder || a.displayName.localeCompare(b.displayName)), ...deleted];
  if (!all.length) { el.innerHTML = `<p class="org-empty">항목이 없습니다.</p>`; return; }
  el.innerHTML = all.map(g => {
    const pendingCls = g._pending === "create" ? "org-pending-create"
                     : g._pending === "update" ? "org-pending-update"
                     : g._pending === "delete" ? "org-pending-delete" : "";
    const deleteLabel  = g._pending === "delete" ? "복원" : "삭제";
    const deleteAction = g._pending === "delete" ? "org-restore" : "org-delete";
    return `
    <div class="org-flat-item ${g.isActive ? "" : "org-node-inactive"} ${pendingCls}" data-code="${escHtml(g.groupCode)}">
      <span class="org-display-name">${escHtml(g.displayName)}</span>
      <code class="org-code-small">${escHtml(g.groupCode)}</code>
      <span class="org-sort-label">순서 ${g.sortOrder}</span>
      ${!g.isActive ? '<span class="org-badge-inactive">비활성</span>' : ""}
      ${g._pending ? `<span class="org-pending-badge">${g._pending === "create" ? "추가" : g._pending === "update" ? "수정" : "삭제예정"}</span>` : ""}
      <div class="org-node-btns">
        ${g._pending !== "delete" ? `<button class="btn-xs btn-secondary" data-action="org-edit" data-code="${escHtml(g.groupCode)}">편집</button>` : ""}
        <button class="btn-xs ${g._pending === "delete" ? "btn-secondary" : "btn-danger"}" data-action="${deleteAction}" data-code="${escHtml(g.groupCode)}">${deleteLabel}</button>
      </div>
    </div>`;
  }).join("");
}

// ─── 탭 전환 + 이벤트 위임 ─────────────────────────────────────────────────
document.getElementById("viewOrgManagement").addEventListener("click", (e) => {
  // 탭 전환
  const tab = e.target.closest(".org-tab");
  if (tab) {
    document.querySelectorAll(".org-tab").forEach(t => t.classList.remove("active"));
    document.querySelectorAll(".org-tab-content").forEach(c => c.classList.add("hidden"));
    tab.classList.add("active");
    const tabId = { structure:"orgTabStructure", joblevel:"orgTabJobLevel", jobposition:"orgTabJobPosition", jobtitle:"orgTabJobTitle" }[tab.dataset.tab];
    if (tabId) document.getElementById(tabId)?.classList.remove("hidden");
    updateOrgInsightMetrics();
    return;
  }

  // 트리 노드 접기/펼치기
  const toggleBtn = e.target.closest("[data-org-toggle]");
  if (toggleBtn) {
    const code = toggleBtn.dataset.orgToggle;
    if (orgExpandedSet.has(code)) orgExpandedSet.delete(code);
    else orgExpandedSet.add(code);
    renderOrgManagement();
    return;
  }

  // 버튼 액션 위임
  const btn = e.target.closest("[data-action]");
  if (!btn) return;
  const action = btn.dataset.action;

  if (action === "org-add-root")  openOrgGroupModal(null, btn.dataset.type || "COMPANY", null);
  if (action === "org-add-flat")  openOrgGroupModal(null, btn.dataset.type, null);
  if (action === "org-add-child") {
    const ptype = btn.dataset.ptype;
    openOrgGroupModal(null, ptype === "COMPANY" ? "DIVISION" : "TEAM", btn.dataset.parent);
  }
  if (action === "org-edit")    openOrgGroupModal(btn.dataset.code, null, null);
  if (action === "org-delete")  markOrgGroupPendingDelete(btn.dataset.code);
  if (action === "org-restore") { orgPendingChanges.delete(btn.dataset.code); renderOrgManagement(); }
});

document.getElementById("btnRefreshOrg").addEventListener("click", () => void loadOrgManagementPage());

document.getElementById("btnOrgReset").addEventListener("click", () => {
  orgPendingChanges.clear();
  renderOrgManagement();
});

document.getElementById("btnOrgSave").addEventListener("click", async () => {
  if (orgPendingChanges.size === 0) { await uiAlert("저장할 변경사항이 없습니다."); return; }
  if (!(await uiConfirm(`변경사항 ${orgPendingChanges.size}건을 저장하시겠습니까?`))) return;
  await saveOrgChanges();
});

// ─── pending 삭제 마킹 (재귀 - 자식 포함) ─────────────────────────────────
function markOrgGroupPendingDelete(groupCode) {
  const eff = buildEffectiveOrgList();
  const kids = eff.filter(g => g.memberOfGroupCode === groupCode);
  for (const kid of kids) markOrgGroupPendingDelete(kid.groupCode);
  const existing = orgPendingChanges.get(groupCode);
  if (existing?.op === "create") {
    orgPendingChanges.delete(groupCode);   // 아직 서버에 없는 항목은 바로 제거
  } else {
    const g = eff.find(x => x.groupCode === groupCode);
    if (g) orgPendingChanges.set(groupCode, { op: "delete", data: { ...g } });
  }
  renderOrgManagement();
}

// ─── 일괄 저장 ────────────────────────────────────────────────────────────
function getOrgDepth(data) {
  const typeDepth = { COMPANY: 0, DIVISION: 1, TEAM: 2, JOB_LEVEL: 0, JOB_POSITION: 0, JOB_TITLE: 0 };
  return typeDepth[data.groupType] ?? 0;
}

async function saveOrgChanges() {
  const creates = [...orgPendingChanges.entries()]
    .filter(([, c]) => c.op === "create")
    .sort(([, a], [, b]) => getOrgDepth(a.data) - getOrgDepth(b.data));
  const updates = [...orgPendingChanges.entries()].filter(([, c]) => c.op === "update");
  const deletes = [...orgPendingChanges.entries()]
    .filter(([, c]) => c.op === "delete")
    .sort(([, a], [, b]) => getOrgDepth(b.data) - getOrgDepth(a.data)); // 깊은 순서

  const errors = [];
  for (const [, change] of creates) {
    const res = await apiFetch("/api/admin/org-groups", { method:"POST", headers:{"Content-Type":"application/json"}, body: JSON.stringify(change.data) });
    if (!res.ok) { const j = await res.json().catch(()=>({})); errors.push(`[생성 ${change.data.groupCode}] ${j.error?.message || res.status}`); }
  }
  for (const [code, change] of updates) {
    const res = await apiFetch(`/api/admin/org-groups/${encodeURIComponent(code)}`, { method:"PUT", headers:{"Content-Type":"application/json"}, body: JSON.stringify(change.data) });
    if (!res.ok) { const j = await res.json().catch(()=>({})); errors.push(`[수정 ${code}] ${j.error?.message || res.status}`); }
  }
  for (const [code] of deletes) {
    const res = await apiFetch(`/api/admin/org-groups/${encodeURIComponent(code)}`, { method:"DELETE" });
    if (!res.ok) { const j = await res.json().catch(()=>({})); errors.push(`[삭제 ${code}] ${j.error?.message || res.status}`); }
  }
  if (errors.length) await uiAlert("일부 저장 실패:\n" + errors.join("\n"));
  await loadOrgManagementPage();
}

// ─── 조직 그룹 모달 ─────────────────────────────────────────────────────
function openOrgGroupModal(editCode, defaultType, defaultParent) {
  orgEditingCode = editCode || null;
  const isEdit   = !!orgEditingCode;
  // 편집 시 pending 데이터 우선, 없으면 서버 데이터
  const existing = isEdit ? (buildEffectiveOrgList().find(g => g.groupCode === orgEditingCode)) : null;

  document.getElementById("orgGroupEditTitle").textContent = isEdit ? "조직 그룹 편집" : "조직 그룹 추가";
  document.getElementById("orgGroupEditError").textContent = "";
  document.getElementById("orgGroupEditError").classList.add("hidden");
  document.getElementById("btnOrgGroupEditConfirm").dataset.editCode = editCode || "";

  const typeEl = document.getElementById("ogType");
  typeEl.value    = existing ? existing.groupType : (defaultType || "COMPANY");
  typeEl.disabled = isEdit;

  const codeEl    = document.getElementById("ogCode");
  codeEl.value    = existing ? existing.groupCode : "";
  codeEl.readOnly = false; // 편집 시에도 코드 수정 가능
  codeEl.dataset.originalCode = existing ? existing.groupCode : "";
  // 변경 경고 초기화
  const codeWarn = document.getElementById("ogCodeChangeWarn");
  if (codeWarn) codeWarn.classList.add("hidden");

  document.getElementById("ogDisplayName").value = existing ? existing.displayName : "";
  document.getElementById("ogSortOrder").value   = existing ? existing.sortOrder   : 0;
  document.getElementById("ogIsActive").checked  = existing ? existing.isActive    : true;

  updateOrgParentDropdown(typeEl.value, existing?.memberOfGroupCode || defaultParent || "");
  updateOrgPathPreview();
  openModal("modalOrgGroupEdit");
}

function updateOrgParentDropdown(groupType, selectedParent) {
  const row = document.getElementById("ogParentRow");
  const sel = document.getElementById("ogParent");
  const needsParent = ["DIVISION","TEAM"].includes(groupType);
  row.classList.toggle("hidden", !needsParent);
  if (!needsParent) { sel.value = ""; return; }
  const parentType = groupType === "DIVISION" ? "COMPANY" : "DIVISION";
  const eff = buildEffectiveOrgList();
  const options = eff.filter(g => g.groupType === parentType && g._pending !== "delete");
  sel.innerHTML = `<option value="">(없음)</option>` +
    options.map(g => `<option value="${escHtml(g.groupCode)}" ${g.groupCode === selectedParent ? "selected" : ""}>${escHtml(g.displayName)}</option>`).join("");
}

/** 조직 코드 기준으로 display name 경로를 조립 (미리보기 전용, / 구분자) */
function buildDisplayPath(code, eff) {
  if (!code) return "";
  const org = eff.find(g => g.groupCode === code);
  if (!org) return code;
  const parentPart = org.memberOfGroupCode ? buildDisplayPath(org.memberOfGroupCode, eff) : "";
  return parentPart ? parentPart + " / " + org.displayName : org.displayName;
}

function updateOrgPathPreview() {
  const displayName = document.getElementById("ogDisplayName").value.trim();
  const parentCode  = document.getElementById("ogParent").value;
  const eff  = buildEffectiveOrgList();
  let path = displayName || "(표시명 입력 후 확인)";
  if (parentCode) {
    const parentDisplayPath = buildDisplayPath(parentCode, eff);
    if (parentDisplayPath) path = parentDisplayPath + " / " + path;
  }
  document.getElementById("ogPathPreview").textContent = path;
}

document.getElementById("ogType").addEventListener("change", (e) => {
  updateOrgParentDropdown(e.target.value, "");
  updateOrgPathPreview();
});
document.getElementById("ogParent").addEventListener("change", updateOrgPathPreview);
document.getElementById("ogDisplayName").addEventListener("input", updateOrgPathPreview);
document.getElementById("ogCode").addEventListener("input", (e) => {
  const original = e.target.dataset.originalCode || "";
  const warn = document.getElementById("ogCodeChangeWarn");
  if (warn) warn.classList.toggle("hidden", !original || e.target.value.trim() === original);
});

document.getElementById("btnOrgGroupEditConfirm").addEventListener("click", () => {
  const isEdit      = !!document.getElementById("btnOrgGroupEditConfirm").dataset.editCode;
  const groupCode   = document.getElementById("ogCode").value.trim();
  const groupType   = document.getElementById("ogType").value;
  const displayName = document.getElementById("ogDisplayName").value.trim();
  const parentCode  = document.getElementById("ogParent").value || null;
  const sortOrder   = parseInt(document.getElementById("ogSortOrder").value) || 0;
  const isActive    = document.getElementById("ogIsActive").checked;

  const errEl = document.getElementById("orgGroupEditError");
  if (!groupCode)   { errEl.textContent = "그룹 코드를 입력하세요."; errEl.classList.remove("hidden"); return; }
  if (!displayName) { errEl.textContent = "표시명을 입력하세요.";    errEl.classList.remove("hidden"); return; }

  const eff = buildEffectiveOrgList();

  // 코드 중복 검사: 자기 자신 제외 후 검사
  const codeConflict = eff.some(g => g.groupCode === groupCode && g._pending !== "delete" && g.groupCode !== orgEditingCode);
  if (codeConflict) {
    errEl.textContent = "이미 존재하는 그룹 코드입니다.";
    errEl.classList.remove("hidden");
    return;
  }

  // groupPath는 백엔드에서 코드 기반으로 재계산됨. pending 로컬 display 용으로만 displayName 경로 계산.
  const displayParentPath = parentCode ? buildDisplayPath(parentCode, eff) : "";
  const localDisplayPath  = displayParentPath ? displayParentPath + " / " + displayName : displayName;

  const data = { groupType, groupCode, displayName, memberOfGroupCode: parentCode, sortOrder, isActive, groupPath: localDisplayPath };

  if (isEdit) {
    const prev = orgPendingChanges.get(orgEditingCode);
    const op   = prev?.op === "create" ? "create" : "update";
    // 코드가 바뀐 경우: 원래 키로 pending 유지 (저장 시 URL = 원래 코드, body.groupCode = 새 코드)
    orgPendingChanges.set(orgEditingCode, { op, data });
    // 표시명이 바뀌면 자식의 표시 경로도 재계산
    updatePendingChildPaths(orgEditingCode, localDisplayPath);
  } else {
    orgExpandedSet.add(groupCode); // 새 노드는 펼친 상태로
    orgPendingChanges.set(groupCode, { op: "create", data });
  }

  closeModal("modalOrgGroupEdit");
  renderOrgManagement();
});

/** pending 상태의 자식 그룹 display 경로 갱신 (미리보기 전용, 백엔드는 재계산) */
function updatePendingChildPaths(parentCode, parentDisplayPath) {
  const eff = buildEffectiveOrgList();
  const kids = eff.filter(g => g.memberOfGroupCode === parentCode);
  for (const kid of kids) {
    const newDisplayPath = parentDisplayPath + " / " + kid.displayName;
    const prev = orgPendingChanges.get(kid.groupCode);
    if (prev) {
      prev.data = { ...prev.data, groupPath: newDisplayPath };
    } else {
      orgPendingChanges.set(kid.groupCode, { op: "update", data: { ...kid, groupPath: newDisplayPath } });
    }
    updatePendingChildPaths(kid.groupCode, newDisplayPath);
  }
}

/* ==========================================================================
 * 조직도 모달
 * ========================================================================== */
let orgChartData = null;

document.getElementById("btnOrgChart").addEventListener("click", () => {
  openModal("modalOrgChart");
  loadOrgChart();
});

document.getElementById("btnOrgChartRefresh").addEventListener("click", () => {
  orgChartData = null;
  loadOrgChart();
});

async function loadOrgChart() {
  const scroll = document.getElementById("orgChartTreeScroll");
  const grid   = document.getElementById("orgChartMemberGrid");
  const teamNameEl  = document.getElementById("orgChartTeamName");
  const teamCountEl = document.getElementById("orgChartTeamCount");
  if (scroll) scroll.innerHTML = '<p class="orgchart-hint">불러오는 중…</p>';
  if (grid)   grid.innerHTML   = '<p class="orgchart-hint">좌측에서 팀을 선택하면 구성원이 표시됩니다.</p>';
  if (teamNameEl)  teamNameEl.textContent  = "팀을 선택하세요";
  if (teamCountEl) teamCountEl.textContent = "";

  try {
    const res  = await apiFetch("/api/user-directory/organization");
    const json = await res.json().catch(() => ({}));
    if (!res.ok) throw new Error(json.error?.message || "조직도 조회 실패");
    orgChartData = json.data;
    renderOrgChartTree(orgChartData);
  } catch (e) {
    if (scroll) scroll.innerHTML = `<p class="orgchart-error">${escHtml(e.message || "오류가 발생했습니다.")}</p>`;
  }
}

function renderOrgChartTree(data) {
  const scroll = document.getElementById("orgChartTreeScroll");
  if (!scroll) return;

  const companies = data?.companies ?? [];
  if (!companies.length) {
    scroll.innerHTML = '<p class="orgchart-hint">등록된 조직이 없습니다.</p>';
    return;
  }

  const parts = [];
  for (const co of companies) {
    const coDirectCnt = co.directMembers?.length ?? 0;
    const coTotalCnt  = coDirectCnt
      + (co.divisions ?? []).reduce((s, d) =>
          s + (d.directMembers?.length ?? 0) + (d.teams ?? []).reduce((ts, t) => ts + (t.users?.length ?? 0), 0), 0);

    parts.push(`<div class="orgchart-company-block">`);
    parts.push(`<button type="button" class="orgchart-company-label orgchart-node-btn"
      data-node-type="company" data-co="${escHtml(co.name)}">
      🏢 ${escHtml(co.name)}
    </button>`);

    for (const div of (co.divisions ?? [])) {
      const divDirectCnt = div.directMembers?.length ?? 0;
      const divTotalCnt  = divDirectCnt + (div.teams ?? []).reduce((s, t) => s + (t.users?.length ?? 0), 0);
      parts.push(`<div class="orgchart-div-block" data-expanded="true">`);
      parts.push(`<div class="orgchart-div-header">
        <button type="button" class="orgchart-div-toggle-btn"
          data-node-type="div-toggle" data-co="${escHtml(co.name)}" data-div="${escHtml(div.name)}">▶</button>
        <button type="button" class="orgchart-div-name-btn orgchart-node-btn"
          data-node-type="division" data-co="${escHtml(co.name)}" data-div="${escHtml(div.name)}">
          📂 ${escHtml(div.name)}
          <span class="orgchart-node-count">${divTotalCnt}</span>
        </button>
      </div>`);
      parts.push(`<div class="orgchart-div-teams">`);
      for (const team of (div.teams ?? [])) {
        const cnt = team.users?.length ?? 0;
        parts.push(`<button type="button"
          class="orgchart-team-btn orgchart-node-btn"
          data-node-type="team"
          data-co="${escHtml(co.name)}"
          data-div="${escHtml(div.name)}"
          data-team="${escHtml(team.name)}">
          👥 ${escHtml(team.name)}
          <span class="orgchart-node-count">${cnt}</span>
        </button>`);
      }
      parts.push(`</div></div>`);
    }
    parts.push(`</div>`);
  }
  scroll.innerHTML = parts.join("");
}

function renderOrgChartMembers(team) {
  const grid        = document.getElementById("orgChartMemberGrid");
  const teamNameEl  = document.getElementById("orgChartTeamName");
  const teamCountEl = document.getElementById("orgChartTeamCount");
  if (!grid) return;

  if (teamNameEl)  teamNameEl.textContent  = team.name;
  if (teamCountEl) teamCountEl.textContent = `${team.users?.length ?? 0}명`;

  const users = sortOrgDirectoryMembers(team.users ?? []);
  if (!users.length) {
    grid.innerHTML = '<p class="orgchart-hint">팀 구성원이 없습니다.</p>';
    return;
  }

  grid.innerHTML = users.map(u => {
    const initial = (u.name ?? "?").charAt(0);
    const badges  = [u.jobLevel, u.jobTitle]
      .filter(Boolean)
      .map(b => `<span class="orgchart-badge">${escHtml(b)}</span>`)
      .join("");
    return `<div class="orgchart-member-card" data-emp="${escHtml(u.employeeNo ?? "")}" title="프로필 보기">
      <div class="orgchart-member-avatar">${escHtml(initial)}</div>
      <div class="orgchart-member-info">
        <div class="orgchart-member-name">${escHtml(u.name ?? "-")}</div>
        <div class="orgchart-member-emp">${escHtml(u.employeeNo ?? "")}</div>
      </div>
      ${badges ? `<div class="orgchart-member-badges">${badges}</div>` : ""}
    </div>`;
  }).join("");
}

document.getElementById("orgChartMemberGrid").addEventListener("click", (e) => {
  const card = e.target.closest(".orgchart-member-card[data-emp]");
  if (!card) return;
  const emp = card.dataset.emp;
  if (emp) openUserProfile(emp);
});

document.getElementById("orgChartTreeScroll").addEventListener("click", (e) => {
  if (!orgChartData) return;

  // ── 토글 버튼: 본부 접기/펼치기만 처리 ──────────────────────────────
  const toggleBtn = e.target.closest(".orgchart-div-toggle-btn");
  if (toggleBtn) {
    const block = toggleBtn.closest(".orgchart-div-block");
    if (block) {
      block.dataset.expanded = block.dataset.expanded === "true" ? "false" : "true";
    }
    return;
  }

  // ── 노드 버튼: 멤버 표시 ─────────────────────────────────────────────
  const btn = e.target.closest(".orgchart-node-btn");
  if (!btn) return;

  const nodeType = btn.dataset.nodeType;
  const coName   = btn.dataset.co;
  const divName  = btn.dataset.div;
  const teamName = btn.dataset.team;

  let nodeLabel   = "";
  let nodeMembers = null;

  if (nodeType === "team") {
    for (const co of (orgChartData.companies ?? [])) {
      if (co.name !== coName) continue;
      for (const div of (co.divisions ?? [])) {
        if (div.name !== divName) continue;
        const team = (div.teams ?? []).find(t => t.name === teamName);
        if (team) { nodeLabel = team.name; nodeMembers = team.users ?? []; }
        break;
      }
      if (nodeMembers) break;
    }
  } else if (nodeType === "division") {
    for (const co of (orgChartData.companies ?? [])) {
      if (co.name !== coName) continue;
      const div = (co.divisions ?? []).find(d => d.name === divName);
      if (div) {
        nodeLabel   = div.name;
        nodeMembers = div.directMembers ?? [];
      }
      break;
    }
  } else if (nodeType === "company") {
    const co = (orgChartData.companies ?? []).find(c => c.name === coName);
    if (co) { nodeLabel = co.name; nodeMembers = co.directMembers ?? []; }
  }

  if (nodeMembers === null) return;

  document.querySelectorAll(".orgchart-node-btn.is-selected")
    .forEach(el => el.classList.remove("is-selected"));
  btn.classList.add("is-selected");

  renderOrgChartMembers({ name: nodeLabel, users: nodeMembers });
});
