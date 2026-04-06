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
  /** 자동 업데이트: 새 버전 다운로드 완료 시 메인에서 수신 */
  onUpdateDownloaded: (handler) => {
    if (typeof handler !== "function") return;
    try {
      ipcRenderer.on("ech-update-downloaded", (_, payload) => handler(payload || {}));
    } catch {
      /* ignore */
    }
  },
  /** 확인 시 설치 후 재시작 */
  installUpdateAndRestart: () => {
    try {
      return ipcRenderer.invoke("ech-install-update");
    } catch {
      return Promise.resolve(false);
    }
  },
});

