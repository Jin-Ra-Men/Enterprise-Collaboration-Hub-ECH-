/**
 * PM2 Ecosystem Config — ECH Realtime Server
 * 사용법: pm2 start pm2.ecosystem.config.cjs
 *        pm2 save && pm2-windows-service install (Windows 서비스 등록)
 *
 * 환경변수 값은 deploy/env.prod 참고하여 실제 운영 값으로 교체할 것.
 */
module.exports = {
  apps: [
    {
      name: "ech-realtime",
      script: "src/server.js",

      // WEB 서버에 실제로 배포한 경로로 수정
      cwd: "C:/ECH/realtime",

      instances: 1,
      autorestart: true,
      watch: false,
      max_memory_restart: "300M",

      // 로그 파일 경로
      out_file: "C:/ECH/logs/realtime-out.log",
      error_file: "C:/ECH/logs/realtime-error.log",
      merge_logs: true,
      log_date_format: "YYYY-MM-DD HH:mm:ss",

      env: {
        NODE_ENV: "production",

        // ── DB (env.prod 의 값과 동일하게 맞출 것) ──
        DB_HOST: "192.168.11.179",
        DB_PORT: "5432",
        DB_NAME: "ech",
        DB_USER: "ech_user",
        DB_PASSWORD: "CHANGE_ME_STRONG_PASSWORD",

        // ── 소켓 서버 ──
        SOCKET_PORT: "3001",
        SOCKET_HOST: "0.0.0.0",

        // ── 내부 인증 토큰 (백엔드 env.prod 의 REALTIME_INTERNAL_TOKEN 과 동일) ──
        REALTIME_INTERNAL_TOKEN: "CHANGE_ME_INTERNAL_SECRET",

        // ── 풀 설정 ──
        DB_POOL_MAX: "10",
        DB_POOL_IDLE_MS: "30000",
        DB_POOL_CONNECT_TIMEOUT_MS: "10000",

        MAX_MESSAGE_BODY_LENGTH: "4000",
        MAX_MSGS_PER_SECOND: "10",
      },
    },
  ],
};
