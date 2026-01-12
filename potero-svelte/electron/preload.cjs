const { contextBridge, ipcRenderer } = require('electron');

/**
 * Expose safe IPC methods to the renderer process
 */
contextBridge.exposeInMainWorld('electronAPI', {
    /**
     * Trigger SSO login flow with BrowserWindow
     * Returns: { success: true, accessToken: string, expiresIn: number | null }
     */
    loginSSO: () => ipcRenderer.invoke('sso-login'),

    /**
     * Get app version
     * Returns: string (e.g., "0.1.0")
     */
    getAppVersion: () => ipcRenderer.invoke('app-version'),

    /**
     * Get backend server status
     * Returns: { running: boolean, port: number }
     */
    getBackendStatus: () => ipcRenderer.invoke('backend-status'),

    /**
     * Window control functions
     */
    windowMinimize: () => ipcRenderer.invoke('window-minimize'),
    windowMaximize: () => ipcRenderer.invoke('window-maximize'),
    windowClose: () => ipcRenderer.invoke('window-close'),

    /**
     * Check if running in Electron
     */
    isElectron: true
});
