const http = require("http");
const { Server } = require("socket.io");
const { healthCheckDb, saveMessage } = require("./db");

const PORT = process.env.SOCKET_PORT || 3001;
/** @type {Map<number, { userId: number, status: string, updatedAt: string }>} */
const presenceByUserId = new Map();
/** @type {Map<string, number>} socket.id -> userId (presence 등록된 소켓만) */
const socketIdToUserId = new Map();
/** @type {Map<number, Set<string>>} userId -> socket.id 집합 (동일 사용자 다중 탭) */
const userIdToSocketIds = new Map();

const MAX_MESSAGE_BODY_LENGTH = Number(process.env.MAX_MESSAGE_BODY_LENGTH || 4000);

function linkSocketToUser(socketId, userId) {
  if (!userIdToSocketIds.has(userId)) {
    userIdToSocketIds.set(userId, new Set());
  }
  userIdToSocketIds.get(userId).add(socketId);
}

function unlinkSocketFromUser(socketId, userId) {
  const set = userIdToSocketIds.get(userId);
  if (!set) {
    return;
  }
  set.delete(socketId);
  if (set.size === 0) {
    userIdToSocketIds.delete(userId);
    presenceByUserId.delete(userId);
    io.emit("presence:update", {
      userId,
      status: "OFFLINE",
      updatedAt: new Date().toISOString(),
    });
  }
}

function serializePresence() {
  return Array.from(presenceByUserId.entries()).map(([userId, value]) => ({
    userId: Number(userId),
    status: value.status,
    updatedAt: value.updatedAt,
  }));
}

const server = http.createServer((req, res) => {
  if (req.url === "/health" && req.method === "GET") {
    healthCheckDb()
      .then(() => {
        res.writeHead(200, { "Content-Type": "application/json" });
        res.end(JSON.stringify({ status: "ok", service: "ech-realtime", db: "ok" }));
      })
      .catch(() => {
        res.writeHead(500, { "Content-Type": "application/json" });
        res.end(JSON.stringify({ status: "error", service: "ech-realtime", db: "error" }));
      });
    return;
  }

  if (req.url === "/presence" && req.method === "GET") {
    res.writeHead(200, { "Content-Type": "application/json" });
    res.end(JSON.stringify({ status: "ok", data: serializePresence() }));
    return;
  }

  res.writeHead(404, { "Content-Type": "application/json" });
  res.end(JSON.stringify({ message: "Not Found" }));
});

const io = new Server(server, {
  cors: { origin: "*" },
  /** 연결당 버퍼 상한으로 비정상적으로 큰 페이로드 완충 완화 */
  maxHttpBufferSize: 1e6,
});

io.on("connection", (socket) => {
  socket.on("presence:set", ({ userId, status }) => {
    const parsedUserId = Number(userId);
    const allowed = new Set(["ONLINE", "AWAY", "OFFLINE"]);
    const normalizedStatus = String(status || "").toUpperCase();

    if (!Number.isInteger(parsedUserId) || !allowed.has(normalizedStatus)) {
      socket.emit("presence:error", {
        code: "INVALID_PRESENCE_PAYLOAD",
        message: "userId, status(ONLINE/AWAY/OFFLINE)를 확인해주세요.",
      });
      return;
    }

    const prevUserId = socketIdToUserId.get(socket.id);
    if (prevUserId !== undefined && prevUserId !== parsedUserId) {
      socketIdToUserId.delete(socket.id);
      unlinkSocketFromUser(socket.id, prevUserId);
    }
    if (prevUserId !== parsedUserId) {
      socketIdToUserId.set(socket.id, parsedUserId);
      linkSocketToUser(socket.id, parsedUserId);
    }

    const payload = {
      userId: parsedUserId,
      status: normalizedStatus,
      updatedAt: new Date().toISOString(),
    };
    presenceByUserId.set(parsedUserId, payload);
    io.emit("presence:update", payload);
  });

  socket.on("disconnect", () => {
    const userId = socketIdToUserId.get(socket.id);
    if (userId === undefined) {
      return;
    }
    socketIdToUserId.delete(socket.id);
    unlinkSocketFromUser(socket.id, userId);
  });

  socket.on("channel:join", (channelId) => {
    socket.join(String(channelId));
  });

  socket.on("message:send", async ({ channelId, senderId, text }) => {
    const parsedChannelId = Number(channelId);
    const parsedSenderId = Number(senderId);
    const body = String(text || "").trim();

    if (!Number.isInteger(parsedChannelId) || !Number.isInteger(parsedSenderId) || !body) {
      socket.emit("message:error", {
        code: "INVALID_PAYLOAD",
        message: "channelId, senderId, text를 확인해주세요.",
      });
      return;
    }

    if (body.length > MAX_MESSAGE_BODY_LENGTH) {
      socket.emit("message:error", {
        code: "MESSAGE_TOO_LARGE",
        message: `메시지는 ${MAX_MESSAGE_BODY_LENGTH}자 이하여야 합니다.`,
      });
      return;
    }

    try {
      const saved = await saveMessage({
        channelId: parsedChannelId,
        senderId: parsedSenderId,
        body,
      });

      io.to(String(parsedChannelId)).emit("message:new", {
        messageId: saved.id,
        channelId: saved.channel_id,
        senderId: saved.sender_id,
        text: saved.body,
        createdAt: saved.created_at,
      });
    } catch (error) {
      socket.emit("message:error", {
        code: "DB_SAVE_FAILED",
        message: "메시지 저장 중 오류가 발생했습니다.",
      });
    }
  });
});

server.listen(PORT, () => {
  console.log(`ECH realtime server running on :${PORT}`);
});
