const fs = require("fs");
const os = require("os");
const path = require("path");
const { app, BrowserWindow, ipcMain, Menu, Tray, nativeImage, Notification, shell, dialog } = require("electron");
const { autoUpdater } = require("electron-updater");

const gotTheLock = app.requestSingleInstanceLock();
if (!gotTheLock) {
  app.quit();
} else {
if (process.platform === "win32") {
  try {
    app.setAppUserModelId("com.cstalk.desktop");
  } catch {
    /* ignore */
  }
}
const DEFAULT_SERVER_URL = "http://cstalk.co.kr:8080";

/** 창 제목: 패키지 버전은 `app.getVersion()`(package.json / 빌드 산출물). */
function getWindowTitle() {
  return `CSTalk — v${app.getVersion()}`;
}

/**
 * 선택 설정: serverUrl, updateBaseUrl(자동업데이트 전용 베이스 URL)
 * 1) CSTalk.exe와 같은 폴더의 cstalk-server.json (구 ech-server.json 호환)
 * 2) %ProgramData%\CSTalk\cstalk-server.json (구 %ProgramData%\ECH\ech-server.json 호환)
 */
function readCstalkServerJson() {
  const candidates = [
    path.join(path.dirname(process.execPath), "cstalk-server.json"),
    path.join(process.env.PROGRAMDATA || "C:\\ProgramData", "CSTalk", "cstalk-server.json"),
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

function buildTrayMenu() {
  let openAtLoginChecked = false;
  try {
    if (app.isPackaged) {
      openAtLoginChecked = app.getLoginItemSettings().openAtLogin === true;
    }
  } catch {
    /* ignore */
  }
  const platform = process.platform;
  const startupLabel =
    platform === "darwin"
      ? "로그인 시 자동 실행"
      : platform === "win32"
        ? "Windows 시작 시 실행"
        : "시작 시 자동 실행";

  return Menu.buildFromTemplate([
    {
      label: "열기",
      click: () => showMainWindow(),
    },
    { type: "separator" },
    {
      label: startupLabel,
      type: "checkbox",
      checked: openAtLoginChecked,
      enabled: app.isPackaged,
      click: (menuItem) => {
        if (!app.isPackaged) return;
        try {
          app.setLoginItemSettings({
            openAtLogin: menuItem.checked,
            path: process.execPath,
          });
        } catch (e) {
          console.warn("[CSTalk] setLoginItemSettings failed:", e?.message || e);
        }
      },
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
}

function createTray() {
  const iconPath = resolveAppIconPath();
  const icon = createTrayNativeImage(iconPath);

  tray = new Tray(icon);
  tray.setToolTip(`CSTalk v${app.getVersion()}`);

  tray.setContextMenu(buildTrayMenu());
  tray.on("right-click", () => {
    tray.setContextMenu(buildTrayMenu());
  });

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

  const cfg = readCstalkServerJson();
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
    const cfg = readCstalkServerJson();
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
      console.warn("[CSTalk] autoUpdater error:", err?.message || err);
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
    console.warn("[CSTalk] autoUpdater init failed:", e?.message || e);
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
    console.warn("[CSTalk] quitAndInstall failed:", e?.message || e);
  }
  return true;
});

/** 설치본(NSIS)에서만 의미 있음. 개발 모드(`electron .`)에서는 비활성. */
ipcMain.handle("ech-get-open-at-login", () => {
  try {
    if (!app.isPackaged) return { openAtLogin: false, supported: false };
    return { openAtLogin: app.getLoginItemSettings().openAtLogin === true, supported: true };
  } catch {
    return { openAtLogin: false, supported: app.isPackaged };
  }
});

ipcMain.handle("ech-set-open-at-login", (_, enabled) => {
  if (!app.isPackaged) return false;
  try {
    app.setLoginItemSettings({
      openAtLogin: enabled === true,
      path: process.execPath,
    });
    return true;
  } catch (e) {
    console.warn("[CSTalk] ech-set-open-at-login failed:", e?.message || e);
    return false;
  }
});

/**
 * Renderer에서 내려받은 바이너리를 %TEMP%\\ech-open\\ 아래에 저장한 뒤 OS 기본 앱으로 연다(Windows: 메모장·Word 등 파일 연결).
 * `shell.openPath`는 성공 시 빈 문자열, 실패 시 오류 문자열을 반환한다.
 */
function safeOpenBasename(name) {
  const base = path.basename(String(name || "download"));
  const cleaned = base.replace(/[<>:"/\\|?*\x00-\x1f]/g, "_").trim();
  return cleaned || "download";
}

ipcMain.handle("ech-open-temp-file-default-app", async (_, payload) => {
  try {
    const filename = payload && typeof payload.filename === "string" ? payload.filename : "download";
    const buf = payload?.buffer;
    if (!buf || !(buf instanceof ArrayBuffer)) {
      return { ok: false, error: "invalid buffer" };
    }
    const base = safeOpenBasename(filename);
    const dir = path.join(os.tmpdir(), "cstalk-open");
    await fs.promises.mkdir(dir, { recursive: true });
    const unique = `${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
    const fp = path.join(dir, `${unique}-${base}`);
    await fs.promises.writeFile(fp, Buffer.from(buf));
    const err = await shell.openPath(fp);
    if (err) {
      return { ok: false, error: err };
    }
    return { ok: true };
  } catch (e) {
    console.warn("[CSTalk] ech-open-temp-file-default-app failed:", e?.message || e);
    return { ok: false, error: String(e?.message || e) };
  }
});

/**
 * 「저장 후 열기」: 사용자가 저장 위치를 고른 뒤 디스크에 쓰고 OS 기본 앱으로 연다(임시 열기 없음).
 */
ipcMain.handle("ech-save-file-and-open-default-app", async (_, payload) => {
  try {
    const filename = payload && typeof payload.filename === "string" ? payload.filename : "download";
    const buf = payload?.buffer;
    if (!buf || !(buf instanceof ArrayBuffer)) {
      return { ok: false, error: "invalid buffer" };
    }
    const win = mainWindow;
    if (!win || win.isDestroyed()) {
      return { ok: false, error: "no window" };
    }
    const base = safeOpenBasename(filename);
    const { canceled, filePath } = await dialog.showSaveDialog(win, {
      title: "파일 저장",
      defaultPath: base,
      buttonLabel: "저장",
    });
    if (canceled) {
      return { ok: false, canceled: true };
    }
    if (!filePath) {
      return { ok: false, error: "no path" };
    }
    await fs.promises.writeFile(filePath, Buffer.from(buf));
    const err = await shell.openPath(filePath);
    if (err) {
      return { ok: false, error: err };
    }
    return { ok: true };
  } catch (e) {
    console.warn("[CSTalk] ech-save-file-and-open-default-app failed:", e?.message || e);
    return { ok: false, error: String(e?.message || e) };
  }
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

