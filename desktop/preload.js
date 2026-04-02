const { contextBridge, ipcRenderer } = require("electron");

contextBridge.exposeInMainWorld("electronAPI", {
  showOsNotification: (payload) => {
    try {
      ipcRenderer.send("os-notification-show", payload);
    } catch {
      // ignore
    }
  },
  onOsNotificationClick: (handler) => {
    try {
      if (typeof handler !== "function") return;
      ipcRenderer.on("os-notification-click", (_, data) => handler(data));
    } catch {
      // ignore
    }
  },
});

