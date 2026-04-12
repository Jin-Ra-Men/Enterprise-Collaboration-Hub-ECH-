/**
 * Removes desktop/dist before electron-builder repacks.
 * Fails if app.asar is locked — close CSTalk.exe / Electron using this folder first.
 */
const fs = require("fs");
const path = require("path");

const dist = path.join(__dirname, "..", "dist");
if (!fs.existsSync(dist)) {
  process.exit(0);
}
try {
  fs.rmSync(dist, { recursive: true, force: true });
  console.log("[CSTalk] removed desktop/dist");
} catch (e) {
  console.error("[CSTalk] desktop/dist 를 지울 수 없습니다. (파일이 다른 프로세스에 잠김)");
  console.error("  → 실행 중인 CSTalk.exe / electron.exe / 이 폴더를 연 탐색기 창을 종료한 뒤 다시 빌드하세요.");
  console.error(String(e && e.message ? e.message : e));
  process.exit(1);
}
