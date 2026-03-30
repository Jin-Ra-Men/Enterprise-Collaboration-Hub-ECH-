const http = require("http");
const { Server } = require("socket.io");
const { healthCheckDb, saveMessage, getPoolStats } = require("./db");

const PORT = process.env.SOCKET_PORT || 3001;
/** @type {Map<string, { employeeNo: string, status: string, updatedAt: string }>} */
const presenceByEmployeeNo = new Map();
/** @type {Map<string, string>} socket.id -> employeeNo (presence 등록된 소켓만) */
const socketIdToEmployeeNo = new Map();
/** @type {Map<string, Set<string>>} employeeNo -> socket.id 집합 (동일 사용자 다중 탭) */
const employeeNoToSocketIds = new Map();

const MAX_MESSAGE_BODY_LENGTH = Number(process.env.MAX_MESSAGE_BODY_LENGTH || 4000);
/** 소켓당 메시지 전송 속도 제한 — 1초 내 최대 전송 수 */
const MAX_MSGS_PER_SECOND = Number(process.env.MAX_MSGS_PER_SECOND || 10);

/** 소켓별 rate limit 추적: socket.id -> { count, resetAt } */
const rateLimitMap = new Map();

function linkSocketToEmployeeNo(socketId, employeeNo) {
  if (!employeeNoToSocketIds.has(employeeNo)) {
    employeeNoToSocketIds.set(employeeNo, new Set());
  }
  employeeNoToSocketIds.get(employeeNo).add(socketId);
}

function unlinkSocketFromEmployeeNo(socketId, employeeNo) {
  const set = employeeNoToSocketIds.get(employeeNo);
  if (!set) return;
  set.delete(socketId);
  if (set.size === 0) {
    employeeNoToSocketIds.delete(employeeNo);
    presenceByEmployeeNo.delete(employeeNo);
    io.emit("presence:update", {
      employeeNo,
      status: "OFFLINE",
      updatedAt: new Date().toISOString(),
    });
  }
}

function serializePresence() {
  return Array.from(presenceByEmployeeNo.entries()).map(([employeeNo, value]) => ({
    employeeNo,
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
            connections: socketIdToEmployeeNo.size,
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
  maxHttpBufferSize: 1e6,
  pingTimeout: 20000,
  pingInterval: 10000,
  connectTimeout: 10000,
});

io.on("connection", (socket) => {
  socket.on("presence:set", (payload) => {
    const employeeNo = String(payload?.employeeNo ?? payload?.userId ?? "").trim();
    const allowed = new Set(["ONLINE", "AWAY", "OFFLINE"]);
    const normalizedStatus = String(payload?.status || "").toUpperCase();

    if (!employeeNo || !allowed.has(normalizedStatus)) {
      socket.emit("presence:error", {
        code: "INVALID_PRESENCE_PAYLOAD",
        message: "employeeNo, status(ONLINE/AWAY/OFFLINE)를 확인해주세요.",
      });
      return;
    }

    const prevEmp = socketIdToEmployeeNo.get(socket.id);
    if (prevEmp !== undefined && prevEmp !== employeeNo) {
      socketIdToEmployeeNo.delete(socket.id);
      unlinkSocketFromEmployeeNo(socket.id, prevEmp);
    }
    if (prevEmp !== employeeNo) {
      socketIdToEmployeeNo.set(socket.id, employeeNo);
      linkSocketToEmployeeNo(socket.id, employeeNo);
    }

    const updatePayload = {
      employeeNo,
      status: normalizedStatus,
      updatedAt: new Date().toISOString(),
    };
    presenceByEmployeeNo.set(employeeNo, updatePayload);
    io.emit("presence:update", updatePayload);
  });

  socket.on("disconnect", (reason) => {
    const employeeNo = socketIdToEmployeeNo.get(socket.id);
    if (employeeNo === undefined) return;
    socketIdToEmployeeNo.delete(socket.id);
    unlinkSocketFromEmployeeNo(socket.id, employeeNo);
    rateLimitMap.delete(socket.id);
    console.log(`[socket] 연결 해제: socketId=${socket.id} employeeNo=${employeeNo} reason=${reason}`);
  });

  socket.on("channel:join", (channelId) => {
    socket.join(String(channelId));
  });

  /**
   * senderId: 발신자 사원번호(문자열). 레거시 숫자 userId는 지원하지 않는다.
   */
  socket.on("message:send", async ({ channelId, senderId, text }, ack) => {
    const parsedChannelId = Number(channelId);
    const senderEmployeeNo = String(senderId ?? "").trim();
    const body = String(text || "").trim();

    const reply = (payload) => {
      if (typeof ack === "function") ack(payload);
    };

    if (!Number.isInteger(parsedChannelId) || !senderEmployeeNo || !body) {
      const errPayload = {
        ok: false,
        code: "INVALID_PAYLOAD",
        message: "channelId, senderId(사원번호), text를 확인해주세요.",
      };
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

    if (!checkRateLimit(socket.id)) {
      const errPayload = {
        ok: false,
        code: "RATE_LIMITED",
        message: "메시지 전송 속도가 너무 빠릅니다. 잠시 후 재시도하세요.",
      };
      socket.emit("message:error", errPayload);
      reply(errPayload);
      return;
    }

    try {
      const saved = await saveMessage({
        channelId: parsedChannelId,
        senderEmployeeNo,
        body,
      });

      const broadcastPayload = {
        messageId: Number(saved.id),
        channelId: Number(saved.channel_id),
        senderId: String(saved.sender_id),
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
