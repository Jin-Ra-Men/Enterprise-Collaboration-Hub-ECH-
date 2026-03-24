const { Pool } = require("pg");

const pool = new Pool({
  host: process.env.DB_HOST || "localhost",
  port: Number(process.env.DB_PORT || 5432),
  database: process.env.DB_NAME || "ech",
  user: process.env.DB_USER || "ech_user",
  password: process.env.DB_PASSWORD || "ech_password",
  max: Number(process.env.DB_POOL_MAX || 10),
  idleTimeoutMillis: Number(process.env.DB_POOL_IDLE_MS || 30_000),
  connectionTimeoutMillis: Number(process.env.DB_POOL_CONNECT_TIMEOUT_MS || 10_000),
});

async function saveMessage({ channelId, senderId, body }) {
  const query = `
    INSERT INTO messages (channel_id, sender_id, body)
    VALUES ($1, $2, $3)
    RETURNING id, channel_id, sender_id, body, created_at
  `;
  const values = [channelId, senderId, body];
  const result = await pool.query(query, values);
  return result.rows[0];
}

async function healthCheckDb() {
  await pool.query("SELECT 1");
}

module.exports = {
  saveMessage,
  healthCheckDb,
};
