/**
 * ECH 통합 부하 테스트 (k6)
 *
 * 실행 방법:
 *   k6 run --env BASE_URL=http://localhost:8080 tools/k6/load-test.js
 *
 * k6 설치: https://grafana.com/docs/k6/latest/get-started/installation/
 */
import http from "k6/http";
import { check, sleep } from "k6";
import { Counter, Rate, Trend } from "k6/metrics";

// ── 커스텀 메트릭 ──────────────────────────────────────────
const loginErrors = new Counter("login_errors");
const searchErrors = new Counter("search_errors");
const loginDuration = new Trend("login_duration_ms");
const searchDuration = new Trend("search_duration_ms");
const errorRate = new Rate("error_rate");

// ── 부하 시나리오 ──────────────────────────────────────────
export const options = {
  scenarios: {
    /** 로그인 스파이크 테스트: 급격히 올라오는 동시 로그인 시뮬레이션 */
    login_spike: {
      executor: "ramping-vus",
      startVUs: 0,
      stages: [
        { duration: "10s", target: 20 },  // 10초 동안 VU 20명으로 증가
        { duration: "30s", target: 50 },  // 30초 동안 VU 50명 유지
        { duration: "10s", target: 0 },   // 10초 동안 0명으로 감소
      ],
      exec: "loginFlow",
    },
    /** 검색 부하 테스트: 지속적 검색 요청 */
    search_load: {
      executor: "constant-vus",
      vus: 10,
      duration: "30s",
      exec: "searchFlow",
      startTime: "15s",  // 로그인 안정화 후 시작
    },
  },
  thresholds: {
    http_req_duration: ["p(95)<2000"],    // 95%ile 응답 2초 이내
    http_req_failed: ["rate<0.05"],       // 에러율 5% 미만
    login_duration_ms: ["p(95)<1500"],    // 로그인 95%ile 1.5초 이내
    search_duration_ms: ["p(95)<1000"],   // 검색 95%ile 1초 이내
    error_rate: ["rate<0.05"],
  },
};

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";

// 테스트 계정 풀 (DataInitializer에서 생성되는 계정)
const TEST_ACCOUNTS = [
  { loginId: "admin@ech.internal", password: "Ech@1234!" },
  { loginId: "kim.chulsu@ech.internal", password: "Ech@1234!" },
  { loginId: "lee.younghee@ech.internal", password: "Ech@1234!" },
  { loginId: "park.minho@ech.internal", password: "Ech@1234!" },
  { loginId: "choi.jisoo@ech.internal", password: "Ech@1234!" },
];

function pickAccount() {
  return TEST_ACCOUNTS[Math.floor(Math.random() * TEST_ACCOUNTS.length)];
}

/**
 * 로그인 플로우: 로그인 → /me 호출
 */
export function loginFlow() {
  const account = pickAccount();
  const loginStart = Date.now();

  const loginRes = http.post(
    `${BASE_URL}/api/auth/login`,
    JSON.stringify({ loginId: account.loginId, password: account.password }),
    { headers: { "Content-Type": "application/json" }, tags: { name: "login" } }
  );

  loginDuration.add(Date.now() - loginStart);

  const loginOk = check(loginRes, {
    "로그인 200 OK": (r) => r.status === 200,
    "토큰 존재": (r) => {
      try {
        return JSON.parse(r.body).data?.token != null;
      } catch {
        return false;
      }
    },
  });

  if (!loginOk) {
    loginErrors.add(1);
    errorRate.add(1);
    sleep(1);
    return;
  }

  errorRate.add(0);
  const token = JSON.parse(loginRes.body).data.token;

  // /me 호출
  const meRes = http.get(`${BASE_URL}/api/auth/me`, {
    headers: { Authorization: `Bearer ${token}` },
    tags: { name: "me" },
  });

  check(meRes, {
    "/me 200 OK": (r) => r.status === 200,
  });

  sleep(1);
}

/**
 * 검색 플로우: 로그인 후 다양한 키워드로 검색
 */
export function searchFlow() {
  const account = pickAccount();

  // 로그인
  const loginRes = http.post(
    `${BASE_URL}/api/auth/login`,
    JSON.stringify({ loginId: account.loginId, password: account.password }),
    { headers: { "Content-Type": "application/json" }, tags: { name: "search_login" } }
  );

  if (loginRes.status !== 200) {
    errorRate.add(1);
    sleep(1);
    return;
  }

  const token = JSON.parse(loginRes.body).data.token;
  const keywords = ["회의", "보고서", "업무", "개발", "test", "hello"];
  const keyword = keywords[Math.floor(Math.random() * keywords.length)];

  const searchStart = Date.now();
  const searchRes = http.get(
    `${BASE_URL}/api/search?q=${encodeURIComponent(keyword)}&limit=20`,
    {
      headers: { Authorization: `Bearer ${token}` },
      tags: { name: "search" },
    }
  );

  searchDuration.add(Date.now() - searchStart);

  const searchOk = check(searchRes, {
    "검색 200 OK": (r) => r.status === 200,
    "결과 구조 정상": (r) => {
      try {
        return JSON.parse(r.body).data?.totalCount != null;
      } catch {
        return false;
      }
    },
  });

  if (!searchOk) {
    searchErrors.add(1);
    errorRate.add(1);
  } else {
    errorRate.add(0);
  }

  sleep(0.5);
}
