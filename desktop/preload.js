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
  /** 부팅/로그인 시 자동 실행(설치본 전용). */
  getOpenAtLogin: () => ipcRenderer.invoke("ech-get-open-at-login"),
  setOpenAtLogin: (enabled) => ipcRenderer.invoke("ech-set-open-at-login", enabled),
  /**
   * 임시 경로에 저장 후 OS 기본 앱으로 연다(데스크톱 전용).
   * @param {{ filename: string, buffer: ArrayBuffer }} payload
   * @returns {Promise<{ ok: boolean, error?: string }>}
   */
  openTempFileWithDefaultApp: (payload) => ipcRenderer.invoke("ech-open-temp-file-default-app", payload),
  /**
   * 저장 대화상자로 경로 선택 → 디스크 저장 후 OS 기본 앱으로 연다(「저장 후 열기」).
   * @param {{ filename: string, buffer: ArrayBuffer }} payload
   * @returns {Promise<{ ok: boolean, canceled?: boolean, error?: string }>}
   */
  saveFileAndOpenWithDefaultApp: (payload) => ipcRenderer.invoke("ech-save-file-and-open-default-app", payload),
});

