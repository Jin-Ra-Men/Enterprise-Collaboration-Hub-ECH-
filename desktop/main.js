const fs = require("fs");
const os = require("os");
const path = require("path");
const { app, BrowserWindow, ipcMain, Menu, Tray, nativeImage, Notification } = require("electron");
const { autoUpdater } = require("electron-updater");

const gotTheLock = app.requestSingleInstanceLock();
if (!gotTheLock) {
  app.quit();
} else {
const DEFAULT_SERVER_URL = "http://ech.co.kr:8080";

/** 창 제목: 패키지 버전은 `app.getVersion()`(package.json / 빌드 산출물). */
function getWindowTitle() {
  return `ECH — Enterprise Collaboration Hub — v${app.getVersion()}`;
}

/** exe 옆 ech-server.json (선택): serverUrl, updateBaseUrl(자동업데이트 전용 베이스 URL) */
function readEchServerJson() {
  try {
    const cfgPath = path.join(path.dirname(process.execPath), "ech-server.json");
    if (fs.existsSync(cfgPath)) {
      const raw = JSON.parse(fs.readFileSync(cfgPath, "utf8"));
      return raw && typeof raw === "object" ? raw : {};
    }
  } catch {
    /* ignore */
  }
  return {};
}

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

function resolveAppIconPath() {
  const iconPath = path.join(__dirname, "assets", "icon.png");
  return fs.existsSync(iconPath) ? iconPath : null;
}

function createTray() {
  const iconPath = resolveAppIconPath();

  let icon;
  try {
    icon = iconPath ? nativeImage.createFromPath(iconPath) : nativeImage.createEmpty();
  } catch {
    icon = nativeImage.createEmpty();
  }

  tray = new Tray(icon);
  tray.setToolTip(`ECH v${app.getVersion()}`);

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
  const iconPath = resolveAppIconPath();
  mainWindow = new BrowserWindow({
    width: 1400,
    height: 900,
    show: true,
    ...(iconPath ? { icon: iconPath } : {}),
    webPreferences: {
      contextIsolation: true,
      nodeIntegration: false,
      preload: path.join(__dirname, "preload.js"),
    },
  });

  const cfg = readEchServerJson();
  const serverUrl = (cfg.serverUrl && String(cfg.serverUrl).trim())
    ? String(cfg.serverUrl).trim().replace(/\/$/, "")
    : DEFAULT_SERVER_URL;

  if (app.isPackaged) {
    // 운영: 서버에서 직접 index.html 로드 (origin = serverUrl → API/Socket URL 자동 결정)
    mainWindow.loadURL(serverUrl + "/index.html");
  } else {
    // 개발: 로컬 파일 로드
    const indexInDev = path.join(__dirname, "..", "frontend", "index.html");
    mainWindow.loadFile(indexInDev);
  }

  mainWindow.webContents.on("did-finish-load", () => {
    try {
      mainWindow.setTitle(getWindowTitle());
    } catch {
      /* ignore */
    }
  });

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
    const cfg = readEchServerJson();
    let genericBase = null;
    if (cfg.updateBaseUrl && String(cfg.updateBaseUrl).trim()) {
      genericBase = String(cfg.updateBaseUrl).trim().replace(/\/?$/, "/");
    } else if (cfg.serverUrl && String(cfg.serverUrl).trim()) {
      genericBase = `${String(cfg.serverUrl).trim().replace(/\/$/, "")}/desktop-updates/`;
    }
    if (genericBase) {
      autoUpdater.setFeedURL({ provider: "generic", url: genericBase });
    }
    // ech-server.json 없음 → 빌드 시점 GitHub publish 설정(electron-updater 기본)

    autoUpdater.autoDownload = true;
    autoUpdater.autoInstallOnAppQuit = true;
    autoUpdater.on("error", (err) => {
      console.warn("[ECH] autoUpdater error:", err?.message || err);
    });
    autoUpdater.on("update-downloaded", (info) => {
      const version = info?.version != null ? String(info.version) : "";
      const notifyRenderer = () => {
        try {
          if (!mainWindow || mainWindow.isDestroyed()) return;
          mainWindow.webContents.send("ech-update-downloaded", { version });
        } catch {
          /* ignore */
        }
      };
      showMainWindow();
      notifyRenderer();
      if (mainWindow && !mainWindow.isDestroyed() && mainWindow.webContents.isLoading()) {
        mainWindow.webContents.once("did-finish-load", notifyRenderer);
      }
    });
    void autoUpdater.checkForUpdates();
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

ipcMain.handle("ech-install-update", () => {
  try {
    app.isQuitting = true;
    autoUpdater.quitAndInstall(false, true);
  } catch (e) {
    console.warn("[ECH] quitAndInstall failed:", e?.message || e);
  }
  return true;
});

app.on("second-instance", () => {
  showMainWindow();
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

} // gotTheLock

