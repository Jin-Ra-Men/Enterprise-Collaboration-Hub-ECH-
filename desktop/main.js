const fs = require("fs");
const os = require("os");
const path = require("path");
const { app, BrowserWindow, ipcMain, Menu, Tray, nativeImage, Notification } = require("electron");
const { autoUpdater } = require("electron-updater");

const gotTheLock = app.requestSingleInstanceLock();
if (!gotTheLock) {
  app.quit();
} else {
if (process.platform === "win32") {
  try {
    app.setAppUserModelId("com.ech.desktop");
  } catch {
    /* ignore */
  }
}
const DEFAULT_SERVER_URL = "http://ech.co.kr:8080";

/** 창 제목: 패키지 버전은 `app.getVersion()`(package.json / 빌드 산출물). */
function getWindowTitle() {
  return `ECH — Enterprise Collaboration Hub — v${app.getVersion()}`;
}

/**
 * 선택 설정: serverUrl, updateBaseUrl(자동업데이트 전용 베이스 URL)
 * 1) ECH.exe와 같은 폴더의 ech-server.json
 * 2) Program Files 설치 시 사용자가 쓰기 어려울 수 있어 %ProgramData%\ECH\ech-server.json 도 지원
 */
function readEchServerJson() {
  const candidates = [
    path.join(path.dirname(process.execPath), "ech-server.json"),
    path.join(process.env.PROGRAMDATA || "C:\\ProgramData", "ECH", "ech-server.json"),
  ];
  for (const cfgPath of candidates) {
    try {
      if (fs.existsSync(cfgPath)) {
        const raw = JSON.parse(fs.readFileSync(cfgPath, "utf8"));
        if (raw && typeof raw === "object") return raw;
      }
    } catch {
      /* ignore */
    }
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

/**
 * Windows는 작업 표시줄/창 아이콘에 .ico가 가장 잘 맞고, PNG만 쓰면 Electron 기본 아이콘으로 남는 경우가 많다.
 */
function resolveAppIconPath() {
  const base = path.join(__dirname, "assets");
  if (process.platform === "win32") {
    const ico = path.join(base, "icon.ico");
    if (fs.existsSync(ico)) return ico;
  }
  const png = path.join(base, "icon.png");
  return fs.existsSync(png) ? png : null;
}

/** Windows 트레이는 작은 규격이 안정적(미적용 시 기본 Electron 아이콘으로 보일 수 있음). */
function createTrayNativeImage(iconPath) {
  if (!iconPath) return nativeImage.createEmpty();
  try {
    const image = nativeImage.createFromPath(iconPath);
    if (image.isEmpty()) return nativeImage.createEmpty();
    if (process.platform === "win32") {
      return image.resize({ width: 16, height: 16 });
    }
    return image;
  } catch {
    return nativeImage.createEmpty();
  }
}

function createTray() {
  const iconPath = resolveAppIconPath();
  const icon = createTrayNativeImage(iconPath);

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

