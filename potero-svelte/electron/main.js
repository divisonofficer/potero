import { app, BrowserWindow, ipcMain } from 'electron';
import { fileURLToPath } from 'url';
import { dirname, join } from 'path';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

let mainWindow;

function createWindow() {
    mainWindow = new BrowserWindow({
        width: 1400,
        height: 900,
        webPreferences: {
            nodeIntegration: false,
            contextIsolation: true,
            preload: join(__dirname, 'preload.cjs')
        }
    });

    // Load the Svelte app
    if (process.env.NODE_ENV === 'development') {
        mainWindow.loadURL('http://localhost:5173');
        mainWindow.webContents.openDevTools();
    } else {
        mainWindow.loadFile(join(__dirname, '../build/index.html'));
    }

    mainWindow.on('closed', () => {
        mainWindow = null;
    });
}

/**
 * Handle SSO login with BrowserWindow
 * Opens GenAI login in a popup, monitors URL changes, extracts token from callback
 */
ipcMain.handle('sso-login', async () => {
    return new Promise((resolve, reject) => {
        const ssoWindow = new BrowserWindow({
            width: 800,
            height: 600,
            parent: mainWindow,
            modal: true,
            webPreferences: {
                nodeIntegration: false,
                contextIsolation: true
            }
        });

        const SSO_LOGIN_URL = 'https://genai.postech.ac.kr/auth/login';
        const CALLBACK_URL_PREFIX = 'https://genai.postech.ac.kr/auth/callback';

        ssoWindow.loadURL(SSO_LOGIN_URL);

        // Monitor URL changes
        const checkUrl = (currentUrl) => {
            console.log('[Electron SSO] URL changed:', currentUrl);

            if (currentUrl.startsWith(CALLBACK_URL_PREFIX)) {
                // Extract token from URL fragment
                const urlObj = new URL(currentUrl);
                const hash = urlObj.hash; // #access_token=...

                if (hash && hash.includes('access_token=')) {
                    const params = new URLSearchParams(hash.substring(1)); // Remove '#'
                    const accessToken = params.get('access_token');
                    const expiresIn = params.get('expires_in');

                    if (accessToken) {
                        console.log('[Electron SSO] Token extracted successfully');
                        ssoWindow.close();
                        resolve({
                            success: true,
                            accessToken,
                            expiresIn: expiresIn ? parseInt(expiresIn) : null
                        });
                    } else {
                        ssoWindow.close();
                        reject(new Error('Access token not found in callback URL'));
                    }
                }
            }
        };

        // Listen to URL changes
        ssoWindow.webContents.on('will-navigate', (event, url) => {
            checkUrl(url);
        });

        ssoWindow.webContents.on('did-navigate', (event, url) => {
            checkUrl(url);
        });

        ssoWindow.webContents.on('did-navigate-in-page', (event, url) => {
            checkUrl(url);
        });

        // Handle window close before login completes
        ssoWindow.on('closed', () => {
            reject(new Error('SSO login window closed by user'));
        });

        // Timeout after 5 minutes
        setTimeout(() => {
            if (!ssoWindow.isDestroyed()) {
                ssoWindow.close();
                reject(new Error('SSO login timeout'));
            }
        }, 5 * 60 * 1000);
    });
});

app.whenReady().then(createWindow);

app.on('window-all-closed', () => {
    if (process.platform !== 'darwin') {
        app.quit();
    }
});

app.on('activate', () => {
    if (mainWindow === null) {
        createWindow();
    }
});
