const http = require("http");
const { Server } = require("socket.io");
const { healthCheckDb, saveMessage, getPoolStats } = require("./db");

const PORT = process.env.SOCKET_PORT || 3001;
/** @type {Map<number, { userId: number, status: string, updatedAt: string }>} */
const presenceByUserId = new Map();
/** @type {Map<string, number>} socket.id -> userId (presence 등록된 소켓만) */
const socketIdToUserId = new Map();
/** @type {Map<number, Set<string>>} userId -> socket.id 집합 (동일 사용자 다중 탭) */
const userIdToSocketIds = new Map();

const MAX_MESSAGE_BODY_LENGTH = Number(process.env.MAX_MESSAGE_BODY_LENGTH || 4000);
/** ACK 없이 재시도 간격 목적으로 사용할 최소 메시지 간격 (ms) */
const MIN_SEND_INTERVAL_MS = Number(process.env.MIN_SEND_INTERVAL_MS || 100);
/** 소켓당 메시지 전송 속도 제한 — 1초 내 최대 전송 수 */
const MAX_MSGS_PER_SECOND = Number(process.env.MAX_MSGS_PER_SECOND || 10);

/** 소켓별 rate limit 추적: socket.id -> { count, resetAt } */
const rateLimitMap = new Map();

function linkSocketToUser(socketId, userId) {
  if (!userIdToSocketIds.has(userId)) {
    userIdToSocketIds.set(userId, new Set());
  }
  userIdToSocketIds.get(userId).add(socketId);
}

function unlinkSocketFromUser(socketId, userId) {
  const set = userIdToSocketIds.get(userId);
  if (!set) return;
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

/**
 * 소켓별 메시지 rate limit 체크.
 * @returns {boolean} true면 허용, false면 차단
 */
function checkRateLimit(socketId) {
  const now = Date.now();
  let entry = rateLimitMap.get(socketId);
  if (!entry || now >= entry.resetAt) {
    entry = { count: 0, resetAt: now + 1000 };
  }
  entry.count += 1;
  rateLimitMap.set(socketId, entry);
  return entry.count <= MAX_MSGS_PER_SECOND;
}

const server = http.createServer((req, res) => {
  if (req.url === "/health" && req.method === "GET") {
    healthCheckDb()
      .then(() => {
        const stats = getPoolStats();
        res.writeHead(200, { "Content-Type": "application/json" });
        res.end(
          JSON.stringify({
            status: "ok",
            service: "ech-realtime",
            db: "ok",
            pool: stats,
            connections: socketIdToUserId.size,
          })
        );
      })
      .catch((err) => {
        res.writeHead(500, { "Content-Type": "application/json" });
        res.end(
          JSON.stringify({
            status: "error",
            service: "ech-realtime",
            db: "error",
            message: err.message,
          })
        );
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
  /** 연결당 버퍼 상한 — 비정상 대용량 페이로드 완충 */
  maxHttpBufferSize: 1e6,
  /**
   * 클라이언트 재연결 설정.
   * 서버 재시작 시 클라이언트가 자동으로 재연결을 시도한다.
   * pingTimeout/pingInterval을 조정해 응답 없는 소켓을 빠르게 감지한다.
   */
  pingTimeout: 20000,
  pingInterval: 10000,
  connectTimeout: 10000,
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

  socket.on("disconnect", (reason) => {
    const userId = socketIdToUserId.get(socket.id);
    if (userId === undefined) return;
    socketIdToUserId.delete(socket.id);
    unlinkSocketFromUser(socket.id, userId);
    rateLimitMap.delete(socket.id);
    console.log(`[socket] 연결 해제: socketId=${socket.id} userId=${userId} reason=${reason}`);
  });

  socket.on("channel:join", (channelId) => {
    socket.join(String(channelId));
  });

  /**
   * 메시지 전송 이벤트.
   *
   * 클라이언트는 ACK 콜백을 넘겨 전송 성공/실패 여부를 확인할 수 있다:
   *   socket.emit("message:send", payload, (ack) => {
   *     if (ack.ok) { // 저장 성공 }
   *     else { // 실패, ack.code, ack.message 참고 }
   *   });
   *
   * ACK 없이 호출해도 기존과 동일하게 동작한다 (하위 호환).
   */
  socket.on("message:send", async ({ channelId, senderId, text }, ack) => {
    const parsedChannelId = Number(channelId);
    const parsedSenderId = Number(senderId);
    const body = String(text || "").trim();

    /** ACK 콜백 안전 래퍼 — ACK가 없어도 에러 없이 동작 */
    const reply = (payload) => {
      if (typeof ack === "function") ack(payload);
    };

    // 입력 검증
    if (!Number.isInteger(parsedChannelId) || !Number.isInteger(parsedSenderId) || !body) {
      const errPayload = { ok: false, code: "INVALID_PAYLOAD", message: "channelId, senderId, text를 확인해주세요." };
      socket.emit("message:error", errPayload);
      reply(errPayload);
      return;
    }

    if (body.length > MAX_MESSAGE_BODY_LENGTH) {
      const errPayload = {
        ok: false,
        code: "MESSAGE_TOO_LARGE",
        message: `메시지는 ${MAX_MESSAGE_BODY_LENGTH}자 이하여야 합니다.`,
      };
      socket.emit("message:error", errPayload);
      reply(errPayload);
      return;
    }

    // Rate limit 검사
    if (!checkRateLimit(socket.id)) {
      const errPayload = { ok: false, code: "RATE_LIMITED", message: "메시지 전송 속도가 너무 빠릅니다. 잠시 후 재시도하세요." };
      socket.emit("message:error", errPayload);
      reply(errPayload);
      return;
    }

    try {
      const saved = await saveMessage({
        channelId: parsedChannelId,
        senderId: parsedSenderId,
        body,
      });

      // pg 라이브러리는 bigint를 문자열로 반환하므로 Number()로 명시 변환
      const broadcastPayload = {
        messageId: Number(saved.id),
        channelId: Number(saved.channel_id),
        senderId: Number(saved.sender_id),
        senderName: saved.sender_name || null,
        text: saved.body,
        createdAt: saved.created_at,
      };
      io.to(String(parsedChannelId)).emit("message:new", broadcastPayload);
      reply({ ok: true, messageId: saved.id, createdAt: saved.created_at });
    } catch (error) {
      const isNotMember = error.code === "NOT_CHANNEL_MEMBER";
      const errPayload = {
        ok: false,
        code: isNotMember ? "NOT_CHANNEL_MEMBER" : "DB_SAVE_FAILED",
        message: isNotMember
          ? error.message
          : "메시지 저장 중 오류가 발생했습니다. 잠시 후 재시도해주세요.",
      };
      socket.emit("message:error", errPayload);
      reply(errPayload);
      console.error("[socket] message:send 실패:", error.code || error.message);
    }
  });
});

server.listen(PORT, () => {
  console.log(`ECH realtime server running on :${PORT}`);
});

// ── 프로세스 종료 시 Pool 정리 ───────────────────────────
async function gracefulShutdown(signal) {
  console.log(`[shutdown] ${signal} 수신. 정리 중...`);
  io.close(() => console.log("[shutdown] Socket.io 닫힘"));
  server.close(() => console.log("[shutdown] HTTP 서버 닫힘"));
  const { pool } = require("./db");
  if (pool && typeof pool.end === "function") {
    await pool.end();
    console.log("[shutdown] DB Pool 종료");
  }
  process.exit(0);
}

process.on("SIGTERM", () => gracefulShutdown("SIGTERM"));
process.on("SIGINT", () => gracefulShutdown("SIGINT"));
