/**
 * ECH 메시지 전송 스트레스 테스트 (k6 - WebSocket은 별도이므로 REST API 기준)
 *
 * 실행 방법:
 *   k6 run --env BASE_URL=http://localhost:8080 --env CHANNEL_ID=1 tools/k6/message-stress-test.js
 */
import http from "k6/http";
import { check, sleep } from "k6";
import { Trend, Rate } from "k6/metrics";

const msgCreateDuration = new Trend("msg_create_duration_ms");
const msgListDuration = new Trend("msg_list_duration_ms");
const errorRate = new Rate("error_rate");

export const options = {
  scenarios: {
    message_stress: {
      executor: "ramping-vus",
      startVUs: 1,
      stages: [
        { duration: "10s", target: 20 },
        { duration: "1m", target: 20 },
        { duration: "10s", target: 0 },
      ],
    },
  },
  thresholds: {
    http_req_duration: ["p(99)<3000"],
    http_req_failed: ["rate<0.01"],
    error_rate: ["rate<0.01"],
  },
};

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
const CHANNEL_ID = __ENV.CHANNEL_ID || "1";

const ACCOUNTS = [
  { loginId: "kim.chulsu@ech.internal", password: "Ech@1234!" },
  { loginId: "lee.younghee@ech.internal", password: "Ech@1234!" },
  { loginId: "park.minho@ech.internal", password: "Ech@1234!" },
];

let cachedTokens = {};

function getToken(account) {
  if (cachedTokens[account.loginId]) return cachedTokens[account.loginId];
  const res = http.post(
    `${BASE_URL}/api/auth/login`,
    JSON.stringify(account),
    { headers: { "Content-Type": "application/json" } }
  );
  if (res.status === 200) {
    const token = JSON.parse(res.body).data.token;
    cachedTokens[account.loginId] = token;
    return token;
  }
  return null;
}

export default function () {
  const account = ACCOUNTS[Math.floor(Math.random() * ACCOUNTS.length)];
  const token = getToken(account);
  if (!token) { errorRate.add(1); sleep(1); return; }

  // 메시지 REST 목록 조회
  const listStart = Date.now();
  const listRes = http.get(
    `${BASE_URL}/api/channels/${CHANNEL_ID}/messages?limit=20`,
    { headers: { Authorization: `Bearer ${token}` }, tags: { name: "msg_list" } }
  );
  msgListDuration.add(Date.now() - listStart);

  const listOk = check(listRes, {
    "메시지 목록 200 OK": (r) => r.status === 200,
  });
  errorRate.add(listOk ? 0 : 1);

  sleep(0.3);
}
