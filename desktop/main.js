const fs = require("fs");
const path = require("path");
const { app, BrowserWindow, ipcMain, Menu, Notification } = require("electron");

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
});

app.on("window-all-closed", () => {
  if (process.platform !== "darwin") app.quit();
});

app.on("activate", () => {
  if (mainWindow == null) createMainWindow();
});

