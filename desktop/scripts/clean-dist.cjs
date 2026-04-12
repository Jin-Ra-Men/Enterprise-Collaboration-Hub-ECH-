/**
 * Removes desktop/dist before electron-builder repacks.
 * Retries a few times — Defender/Indexer sometimes releases the lock briefly.
 * Set CSTALK_SKIP_DIST_CLEAN=1 to skip (only if you cleared dist manually).
 */
const fs = require("fs");
const path = require("path");
const { execSync } = require("child_process");

const dist = path.join(__dirname, "..", "dist");

if (process.env.CSTALK_SKIP_DIST_CLEAN === "1") {
  console.warn("[CSTalk] CSTALK_SKIP_DIST_CLEAN=1 — skipping desktop/dist removal.");
  process.exit(0);
}

if (!fs.existsSync(dist)) {
  process.exit(0);
}

function sleepSyncMs(ms) {
  try {
    execSync(`powershell -NoProfile -Command "Start-Sleep -Milliseconds ${ms}"`, { stdio: "ignore", windowsHide: true });
  } catch {
    const end = Date.now() + ms;
    while (Date.now() < end) {
      /* sync fallback */
    }
  }
}

const maxAttempts = 6;
let lastErr;
for (let attempt = 1; attempt <= maxAttempts; attempt++) {
  try {
    fs.rmSync(dist, { recursive: true, force: true });
    console.log("[CSTalk] removed desktop/dist");
    process.exit(0);
  } catch (e) {
    lastErr = e;
    if (attempt < maxAttempts) {
      console.warn(`[CSTalk] desktop/dist 삭제 재시도 ${attempt}/${maxAttempts - 1}… (잠시 후)`);
      sleepSyncMs(1200);
    }
  }
}

console.error("[CSTalk] desktop/dist 를 지울 수 없습니다. (파일이 다른 프로세스에 잠김)");
console.error("");
console.error("  자주 있는 원인:");
console.error("  · Cursor / VS Code 가 이 저장소를 열어 두면 dist 안 파일을 잠글 수 있음 → IDE 완전 종료 후, 바깥 PowerShell에서만 빌드해 보세요.");
console.error("  · Windows Defender 실시간 검사 → 프로젝트 폴더를 일시 제외하거나, 잠시 끄고 재시도.");
console.error("  · 탐색기에서 desktop\\dist 를 연 상태, 또는 백그라운드 인덱싱.");
console.error("");
console.error("  잠금 프로세스 확인: 작업 관리자 → 성능 → 리소스 모니터 열기 → CPU → 연결된 핸들 → “app.asar” 검색");
console.error("");
console.error(String(lastErr && lastErr.message ? lastErr.message : lastErr));
process.exit(1);
