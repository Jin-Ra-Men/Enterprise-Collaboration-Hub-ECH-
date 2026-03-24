const socket = io("http://localhost:3001");
const messagesEl = document.getElementById("messages");
const form = document.getElementById("messageForm");
const channelIdEl = document.getElementById("channelId");
const usernameEl = document.getElementById("username");
const messageInputEl = document.getElementById("messageInput");

let joinedChannel = null;

function appendMessage(message) {
  const item = document.createElement("p");
  item.textContent = `[${message.channelId}] ${message.user}: ${message.text}`;
  messagesEl.appendChild(item);
}

function ensureChannelJoined(channelId) {
  if (joinedChannel === channelId) return;
  socket.emit("channel:join", channelId);
  joinedChannel = channelId;
}

form.addEventListener("submit", (event) => {
  event.preventDefault();
  const channelId = channelIdEl.value.trim();
  const user = usernameEl.value.trim();
  const text = messageInputEl.value.trim();
  if (!channelId || !user || !text) return;

  ensureChannelJoined(channelId);
  socket.emit("message:send", { channelId, user, text });
  messageInputEl.value = "";
});

socket.on("connect", () => {
  ensureChannelJoined(channelIdEl.value.trim() || "general");
});

socket.on("message:new", appendMessage);
