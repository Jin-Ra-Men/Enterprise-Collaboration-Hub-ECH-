const socket = io("http://localhost:3001");
const messagesEl = document.getElementById("messages");
const form = document.getElementById("messageForm");
const channelIdEl = document.getElementById("channelId");
const senderIdEl = document.getElementById("senderId");
const messageInputEl = document.getElementById("messageInput");

/** 데모 UI DOM 노드 무한 증가 방지 (메모리·렌더 비용 상한) */
const MAX_VISIBLE_MESSAGES = 200;

let joinedChannel = null;

function trimMessageList() {
  while (messagesEl.children.length > MAX_VISIBLE_MESSAGES) {
    messagesEl.removeChild(messagesEl.firstChild);
  }
}

function appendMessage(message) {
  const item = document.createElement("p");
  item.textContent = `[${message.channelId}] user#${message.senderId}: ${message.text}`;
  messagesEl.appendChild(item);
  trimMessageList();
}

function ensureChannelJoined(channelId) {
  if (joinedChannel === channelId) return;
  socket.emit("channel:join", channelId);
  joinedChannel = channelId;
}

form.addEventListener("submit", (event) => {
  event.preventDefault();
  const channelId = Number(channelIdEl.value.trim());
  const senderId = Number(senderIdEl.value.trim());
  const text = messageInputEl.value.trim();
  if (!Number.isInteger(channelId) || !Number.isInteger(senderId) || !text) return;

  ensureChannelJoined(channelId);
  socket.emit("message:send", { channelId, senderId, text });
  messageInputEl.value = "";
});

socket.on("connect", () => {
  const initialChannelId = Number(channelIdEl.value.trim() || "1");
  if (Number.isInteger(initialChannelId)) {
    ensureChannelJoined(initialChannelId);
  }
});

socket.on("message:new", appendMessage);
socket.on("message:error", (error) => {
  const item = document.createElement("p");
  item.textContent = `[error] ${error.message}`;
  item.style.color = "crimson";
  messagesEl.appendChild(item);
  trimMessageList();
});
