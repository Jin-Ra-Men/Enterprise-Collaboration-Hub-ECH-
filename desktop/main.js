const fs = require("fs");
const os = require("os");
const path = require("path");
const { app, BrowserWindow, ipcMain, Menu, Tray, nativeImage, Notification } = require("electron");
const { autoUpdater } = require("electron-updater");

/** @type {BrowserWindow | null} */
let mainWindow = null;
/** @type {Tray | null} */
let tray = null;

function showMainWindow() {
  if (!mainWindow) return;
  if (mainWindow.isMinimized()) mainWindow.restore();
  mainWindow.show();
  mainWindow.focus();
}

function createTray() {
  const iconInPackage = path.join(__dirname, "assets", "tray-icon.png");
  const iconInDev     = path.join(__dirname, "assets", "tray-icon.png");
  const iconPath      = fs.existsSync(iconInPackage) ? iconInPackage : iconInDev;

  let icon;
  try {
    icon = nativeImage.createFromPath(iconPath);
  } catch {
    icon = nativeImage.createEmpty();
  }

  tray = new Tray(icon);
  tray.setToolTip("ECH");

  const contextMenu = Menu.buildFromTemplate([
    {
      label: "열기",
      click: () => showMainWindow(),
    },
    { type: "separator" },
    {
      label: "종료",
      click: () => {
        app.isQuitting = true;
        app.quit();
      },
    },
  ]);

  tray.setContextMenu(contextMenu);

  // 트레이 아이콘 클릭(좌클릭) → 창 표시
  tray.on("click", () => showMainWindow());
}

function createMainWindow() {
  mainWindow = new BrowserWindow({
    width: 1400,
    height: 900,
    show: true,
    webPreferences: {
      contextIsolation: true,
      nodeIntegration: false,
      preload: path.join(__dirname, "preload.js"),
    },
  });

  // 서버 URL 결정: ech-server.json > 환경변수 > 기본값(ech.co.kr:8080)
  // 배포 시 exe 옆에 ech-server.json {"serverUrl":"http://192.168.x.x:8080"} 으로 재정의 가능
  let serverUrl = "http://ech.co.kr:8080";
  try {
    const cfgPath = path.join(path.dirname(process.execPath), "ech-server.json");
    if (fs.existsSync(cfgPath)) {
      const cfg = JSON.parse(fs.readFileSync(cfgPath, "utf8"));
      if (cfg && cfg.serverUrl) serverUrl = cfg.serverUrl.replace(/\/$/, "");
    }
  } catch { /* ignore */ }

  if (app.isPackaged) {
    // 운영: 서버에서 직접 index.html 로드 (origin = serverUrl → API/Socket URL 자동 결정)
    mainWindow.loadURL(serverUrl + "/index.html");
  } else {
    // 개발: 로컬 파일 로드
    const indexInDev = path.join(__dirname, "..", "frontend", "index.html");
    mainWindow.loadFile(indexInDev);
  }

  // X 버튼 → 트레이로 숨기기 (완전 종료 아님)
  mainWindow.on("close", (e) => {
    if (!app.isQuitting) {
      e.preventDefault();
      mainWindow.hide();
    }
  });
}

function setupAutoUpdater() {
  if (!app.isPackaged) return;
  try {
    autoUpdater.autoDownload = true;
    autoUpdater.autoInstallOnAppQuit = true;
    autoUpdater.on("error", (err) => {
      console.warn("[ECH] autoUpdater error:", err?.message || err);
    });
    autoUpdater.on("update-downloaded", () => {
      try {
        new Notification({
          title: "ECH 업데이트",
          body: "새 버전이 내려받아졌습니다. 앱을 종료하면 업데이트가 적용됩니다.",
        }).show();
      } catch {
        /* ignore */
      }
    });
    void autoUpdater.checkForUpdatesAndNotify();
    setInterval(() => {
      void autoUpdater.checkForUpdates();
    }, 6 * 60 * 60 * 1000);
  } catch (e) {
    console.warn("[ECH] autoUpdater init failed:", e?.message || e);
  }
}

/**
 * AD 자동 로그인용: 현재 Windows 로그인 계정의 sAMAccountName을 반환한다.
 * 도메인 접두사(DOMAIN\username)가 있으면 제거하고 소문자로 반환한다.
 */
ipcMain.handle("get-windows-username", () => {
  try {
    let username = os.userInfo().username || "";
    if (username.includes("\\")) {
      username = username.split("\\").pop();
    }
    return username.trim().toLowerCase();
  } catch {
    return null;
  }
});

ipcMain.on("os-notification-show", (event, payload) => {
  try {
    const tag = payload?.tag != null ? String(payload.tag) : "";
    const title = payload?.title != null ? String(payload.title) : "알림";
    const body = payload?.body != null ? String(payload.body) : "";

    const n = new Notification({ title, body });
    n.on("click", () => {
      const targetWin = mainWindow ?? event?.sender?.browserWindow?.();
      try {
        targetWin?.show?.();
        targetWin?.focus?.();
      } catch {
        // ignore
      }
      try {
        event?.sender?.send?.("os-notification-click", { tag });
      } catch {
        // ignore
      }
    });

    n.show();
  } catch {
    // ignore
  }
});

app.whenReady().then(() => {
  // Remove default Electron in-window menu bar (File / Edit / View / …) on Windows/Linux.
  if (process.platform !== "darwin") {
    Menu.setApplicationMenu(null);
  }
  createMainWindow();
  createTray();
  setupAutoUpdater();
});

// 트레이 모드: 모든 창이 닫혀도 앱은 트레이에서 계속 실행
app.on("window-all-closed", () => {
  // macOS 외에서는 트레이가 있으면 유지, 없으면 종료
  if (process.platform !== "darwin" && !tray) app.quit();
});

app.on("activate", () => {
  if (mainWindow == null) createMainWindow();
});

