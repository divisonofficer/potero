/**
 * TypeScript definitions for Electron IPC API
 */

export interface ElectronAPI {
    /**
     * Trigger SSO login flow with BrowserWindow
     * Opens GenAI login in a modal window, monitors URL changes,
     * and automatically extracts the access token from the callback URL.
     *
     * @returns Promise with login result containing access token
     */
    loginSSO: () => Promise<{
        success: true;
        accessToken: string;
        expiresIn: number | null;
    }>;

    /**
     * Check if running in Electron environment
     */
    isElectron: boolean;
}

declare global {
    interface Window {
        electronAPI?: ElectronAPI;
    }
}

export {};
