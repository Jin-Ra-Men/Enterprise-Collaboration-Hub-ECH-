const fs = require("fs");
const os = require("os");
const path = require("path");
const { app, BrowserWindow, ipcMain, Menu, Notification } = require("electron");
const { autoUpdater } = require("electron-updater");

/** @type {BrowserWindow | null} */
let mainWindow = null;

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

  const indexInPackage = path.join(__dirname, "frontend", "index.html");
  const indexInDev = path.join(__dirname, "..", "frontend", "index.html");
  const indexPath = fs.existsSync(indexInPackage) ? indexInPackage : indexInDev;
  mainWindow.loadFile(indexPath);
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
  setupAutoUpdater();
});

app.on("window-all-closed", () => {
  if (process.platform !== "darwin") app.quit();
});

app.on("activate", () => {
  if (mainWindow == null) createMainWindow();
});

