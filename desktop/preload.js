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
  /** AD 자동 로그인: 현재 Windows 계정의 sAMAccountName을 반환 */
  getWindowsUsername: () => {
    try {
      return ipcRenderer.invoke("get-windows-username");
    } catch {
      return Promise.resolve(null);
    }
  },
});

