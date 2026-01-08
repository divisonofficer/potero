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
     * Check if running in Electron
     */
    isElectron: true
});
