const { Pool } = require("pg");

const pool = new Pool({
  host: process.env.DB_HOST || "localhost",
  port: Number(process.env.DB_PORT || 5432),
  database: process.env.DB_NAME || "ech",
  user: process.env.DB_USER || "ech_user",
  password: process.env.DB_PASSWORD || "ech_password",
  max: Number(process.env.DB_POOL_MAX || 10),
  min: Number(process.env.DB_POOL_MIN || 1),
  idleTimeoutMillis: Number(process.env.DB_POOL_IDLE_MS || 30_000),
  connectionTimeoutMillis: Number(process.env.DB_POOL_CONNECT_TIMEOUT_MS || 10_000),
  // 커넥션 최대 수명 (30분). 장기 세션 누수 방지.
  maxUses: 7500,
});

// Pool 레벨 에러 핸들링 (클라이언트 체크아웃 없이 발생하는 idle 에러)
pool.on("error", (err) => {
  console.error("[DB Pool] idle client 오류 발생, 무시 가능:", err.message);
});

pool.on("connect", () => {
  console.log("[DB Pool] 새 커넥션 획득");
});

pool.on("remove", () => {
  console.log("[DB Pool] 커넥션 제거");
});

/**
 * 메시지 저장. 재시도 로직 포함 (최대 3회, 지수 백오프).
 */
async function saveMessage({ channelId, senderEmployeeNo, body }, retries = 3) {
  const emp = String(senderEmployeeNo || "").trim();
  if (!emp) {
    const err = new Error("발신자 사원번호가 필요합니다.");
    err.code = "INVALID_SENDER";
    throw err;
  }
  const senderLookup = await pool.query(
    `SELECT employee_no, name FROM users WHERE employee_no = $1`,
    [emp]
  );
  if (senderLookup.rowCount === 0) {
    const err = new Error("존재하지 않는 사용자입니다.");
    err.code = "SENDER_NOT_FOUND";
    throw err;
  }
  const senderName = senderLookup.rows[0].name || null;

  const memberCheck = await pool.query(
    `SELECT 1 FROM channel_members WHERE channel_id = $1 AND user_id = $2`,
    [channelId, emp]
  );
  if (memberCheck.rowCount === 0) {
    const err = new Error("채널 멤버가 아닌 사용자는 메시지를 보낼 수 없습니다.");
    err.code = "NOT_CHANNEL_MEMBER";
    throw err;
  }

  const query = `
    INSERT INTO messages (channel_id, sender_id, body, message_type, is_deleted, is_edited, created_at, updated_at)
    VALUES ($1, $2, $3, 'TEXT', false, false, NOW(), NOW())
    RETURNING id, channel_id, sender_id, body, created_at
  `;
  const values = [channelId, emp, body];

  for (let attempt = 1; attempt <= retries; attempt++) {
    try {
      const result = await pool.query(query, values);
      const row = result.rows[0];
      // 발신자 이름 조회 (프론트엔드 표시용)
      row.sender_name = senderName;
      return row;
    } catch (err) {
      const isRetryable = isRetryableError(err);
      if (!isRetryable || attempt === retries) {
        console.error(
          `[DB] saveMessage 실패 (시도 ${attempt}/${retries}):`,
          err.code,
          err.message
        );
        throw err;
      }
      const delay = Math.pow(2, attempt - 1) * 200; // 200ms, 400ms, 800ms
      console.warn(
        `[DB] saveMessage 재시도 ${attempt}/${retries} (${delay}ms 후):`,
        err.code
      );
      await sleep(delay);
    }
  }
}

/**
 * DB 상태 확인 (헬스체크용).
 */
async function healthCheckDb() {
  await pool.query("SELECT 1");
}

/**
 * Pool 통계 정보 반환 (모니터링용).
 */
function getPoolStats() {
  return {
    total: pool.totalCount,
    idle: pool.idleCount,
    waiting: pool.waitingCount,
  };
}

/**
 * 재시도 가능한 DB 에러인지 판단.
 * PSQL 에러 코드 참고: https://www.postgresql.org/docs/current/errcodes-appendix.html
 */
function isRetryableError(err) {
  const retryableCodes = new Set([
    "ECONNREFUSED",  // 연결 거부 (서버 재시작 중)
    "ECONNRESET",    // 연결 초기화
    "ETIMEDOUT",     // 타임아웃
    "57P01",         // admin_shutdown
    "57P02",         // crash_shutdown
    "57P03",         // cannot_connect_now
    "08006",         // connection_failure
    "08001",         // sqlclient_unable_to_establish_sqlconnection
    "08004",         // rejected_connection
    "40001",         // serialization_failure (트랜잭션 충돌)
    "40P01",         // deadlock_detected
  ]);
  return retryableCodes.has(err.code);
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

const MAX_MENTION_TOKENS = 20;

function extractMentionEmployeeNosFromBody(body) {
  const s = String(body || "");
  const re = /@\{([^}]+)\}/g;
  const out = new Set();
  let m;
  while ((m = re.exec(s)) !== null && out.size < MAX_MENTION_TOKENS) {
    const inner = m[1];
    const pipe = inner.indexOf("|");
    const emp = (pipe >= 0 ? inner.slice(0, pipe) : inner).trim();
    if (emp) out.add(emp);
  }
  return [...out];
}

async function filterEmployeeNosInChannel(channelId, employeeNos) {
  if (!employeeNos || employeeNos.length === 0) return [];
  const r = await pool.query(
    `SELECT user_id FROM channel_members WHERE channel_id = $1 AND user_id = ANY($2::text[])`,
    [channelId, employeeNos]
  );
  return r.rows.map((row) => row.user_id);
}

async function getChannelMentionMeta(channelId) {
  const r = await pool.query(
    `SELECT name, channel_type, description FROM channels WHERE id = $1`,
    [channelId]
  );
  if (r.rowCount === 0) return null;
  const row = r.rows[0];
  const ct = String(row.channel_type || "").toUpperCase();
  const isDm = ct === "DM";
  const display = isDm && row.description ? String(row.description) : String(row.name || "");
  return {
    channelName: display || row.name || "채널",
    channelType: ct || "PUBLIC",
  };
}

function mentionPreviewForToast(body, maxLen = 120) {
  const s = String(body || "")
    .replace(/@\{([^}|]+)\|([^}]+)\}/g, "@$2")
    .replace(/@\{([^}]+)\}/g, "@$1");
  return s.length <= maxLen ? s : s.slice(0, maxLen) + "…";
}

module.exports = {
  saveMessage,
  healthCheckDb,
  getPoolStats,
  extractMentionEmployeeNosFromBody,
  filterEmployeeNosInChannel,
  getChannelMentionMeta,
  mentionPreviewForToast,
};
