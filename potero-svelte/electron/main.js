import { app, BrowserWindow, ipcMain, Menu, dialog, shell } from 'electron';
import { fileURLToPath } from 'url';
import { dirname, join } from 'path';
import { spawn } from 'child_process';
import { existsSync } from 'fs';
import http from 'http';

// Remove default menu bar
Menu.setApplicationMenu(null);

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

let mainWindow;
let backendProcess = null;

const BACKEND_PORT = 18080;
const BACKEND_HOST = 'localhost';

// ============ Resource Path Helpers ============

/**
 * Get Java executable path
 * - Production: bundled JRE
 * - Development: system Java
 */
function getJavaPath() {
    if (app.isPackaged) {
        // Production: use bundled JRE
        const jrePath = join(process.resourcesPath, 'jre', 'bin', 'java.exe');
        if (existsSync(jrePath)) {
            return jrePath;
        }
        throw new Error('Bundled JRE not found at: ' + jrePath);
    } else {
        // Development: use system Java
        return 'java';
    }
}

/**
 * Get JAR file path
 * - Production: extraResources folder
 * - Development: KMP build output
 */
function getJarPath() {
    if (app.isPackaged) {
        return join(process.resourcesPath, 'backend', 'potero-server.jar');
    } else {
        // Development: reference KMP build output directly
        return join(__dirname, '..', '..', 'potero-kmp', 'server', 'build', 'libs', 'potero-server.jar');
    }
}

/**
 * Get frontend path for the backend to serve
 * - Production: extraResources/frontend
 * - Development: potero-svelte/build
 */
function getFrontendPath() {
    if (app.isPackaged) {
        return join(process.resourcesPath, 'frontend');
    } else {
        return join(__dirname, '..', 'build');
    }
}

// ============ Backend Process Management ============

/**
 * Start the backend server
 */
async function startBackend() {
    const javaPath = getJavaPath();
    const jarPath = getJarPath();
    const frontendPath = getFrontendPath();

    console.log('[Backend] Java path:', javaPath);
    console.log('[Backend] JAR path:', jarPath);
    console.log('[Backend] Frontend path:', frontendPath);

    if (!existsSync(jarPath)) {
        throw new Error(`JAR file not found: ${jarPath}\nRun 'npm run build:backend' first.`);
    }

    // Set working directory to where the frontend is located
    const cwd = app.isPackaged ? process.resourcesPath : dirname(jarPath);

    return new Promise((resolve, reject) => {
        backendProcess = spawn(javaPath, [
            '-jar',
            jarPath
        ], {
            cwd: cwd,
            stdio: ['pipe', 'pipe', 'pipe'],
            // Hide console window on Windows
            windowsHide: true
        });

        backendProcess.stdout.on('data', (data) => {
            console.log(`[Backend] ${data.toString().trim()}`);
        });

        backendProcess.stderr.on('data', (data) => {
            console.error(`[Backend Error] ${data.toString().trim()}`);
        });

        backendProcess.on('error', (err) => {
            console.error('[Backend] Failed to start:', err);
            reject(err);
        });

        backendProcess.on('exit', (code, signal) => {
            console.log(`[Backend] Exited with code ${code}, signal ${signal}`);
            backendProcess = null;
        });

        // Wait for server to be ready (health check)
        waitForBackend(30000) // 30 second timeout
            .then(() => resolve())
            .catch((err) => reject(err));
    });
}

/**
 * Wait for backend server to be ready (polling health endpoint)
 */
function waitForBackend(timeout) {
    const startTime = Date.now();
    const checkInterval = 500; // 500ms interval

    return new Promise((resolve, reject) => {
        const check = () => {
            if (Date.now() - startTime > timeout) {
                reject(new Error('Backend startup timeout after ' + timeout + 'ms'));
                return;
            }

            const req = http.request({
                hostname: BACKEND_HOST,
                port: BACKEND_PORT,
                path: '/health',
                method: 'GET',
                timeout: 1000
            }, (res) => {
                if (res.statusCode === 200) {
                    console.log('[Backend] Server is ready');
                    resolve();
                } else {
                    setTimeout(check, checkInterval);
                }
            });

            req.on('error', () => {
                // Connection failed, retry
                setTimeout(check, checkInterval);
            });

            req.on('timeout', () => {
                req.destroy();
                setTimeout(check, checkInterval);
            });

            req.end();
        };

        check();
    });
}

/**
 * Stop the backend process
 */
function stopBackend() {
    if (backendProcess) {
        console.log('[Backend] Stopping backend process...');

        // On Windows, use taskkill to kill process tree
        if (process.platform === 'win32') {
            try {
                spawn('taskkill', ['/pid', backendProcess.pid.toString(), '/f', '/t'], {
                    windowsHide: true
                });
            } catch (e) {
                console.error('[Backend] Failed to kill process:', e);
            }
        } else {
            backendProcess.kill('SIGTERM');
        }

        backendProcess = null;
    }
}

// ============ Window Management ============

function createWindow() {
    mainWindow = new BrowserWindow({
        width: 1400,
        height: 900,
        show: false, // Hide until backend is ready
        frame: false, // Remove default title bar
        titleBarStyle: 'hidden', // For macOS compatibility
        webPreferences: {
            nodeIntegration: false,
            contextIsolation: true,
            preload: join(__dirname, 'preload.cjs')
        }
    });

    // Load the Svelte app from the backend server
    // The KMP backend now serves both API and static frontend files
    if (process.env.NODE_ENV === 'development' && !process.env.START_BACKEND) {
        // Development with Vite dev server
        mainWindow.loadURL('http://localhost:5173');
        mainWindow.webContents.openDevTools();
    } else {
        // Production or development with backend
        // Load from KMP backend which serves the frontend
        mainWindow.loadURL(`http://${BACKEND_HOST}:${BACKEND_PORT}`);
    }

    // Enable DevTools with F12 or Ctrl+Shift+I
    mainWindow.webContents.on('before-input-event', (event, input) => {
        if (input.key === 'F12' ||
            (input.control && input.shift && input.key.toLowerCase() === 'i')) {
            mainWindow.webContents.toggleDevTools();
        }
    });

    mainWindow.once('ready-to-show', () => {
        mainWindow.show();
    });

    mainWindow.on('closed', () => {
        mainWindow = null;
    });
}

// ============ SSO Login Handler (existing) ============

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

// ============ Additional IPC Handlers ============

ipcMain.handle('app-version', () => {
    return app.getVersion();
});

ipcMain.handle('backend-status', () => {
    return {
        running: backendProcess !== null,
        port: BACKEND_PORT
    };
});

// Window control handlers
ipcMain.handle('window-minimize', () => {
    if (mainWindow) mainWindow.minimize();
});

ipcMain.handle('window-maximize', () => {
    if (mainWindow) {
        if (mainWindow.isMaximized()) {
            mainWindow.unmaximize();
        } else {
            mainWindow.maximize();
        }
    }
});

ipcMain.handle('window-close', () => {
    if (mainWindow) mainWindow.close();
});

// ============ Directory Selection Handler ============

/**
 * Open a folder selection dialog (Windows-focused)
 * Returns the selected folder path or null if cancelled
 */
ipcMain.handle('select-directory', async () => {
    const result = await dialog.showOpenDialog(mainWindow, {
        properties: ['openDirectory', 'createDirectory'],
        title: 'Select Storage Directory'
    });
    return result.canceled ? null : result.filePaths[0];
});

/**
 * Open external URL in default browser
 */
ipcMain.handle('open-external', async (event, url) => {
    await shell.openExternal(url);
});

// ============ App Lifecycle ============

app.whenReady().then(async () => {
    try {
        // Start backend in production mode
        // In development, backend is expected to run separately unless START_BACKEND=true
        if (app.isPackaged || process.env.START_BACKEND === 'true') {
            console.log('[App] Starting backend server...');
            await startBackend();
            console.log('[App] Backend server started successfully');
        } else {
            console.log('[App] Development mode: backend should be started separately');
        }

        createWindow();
    } catch (err) {
        console.error('[App] Failed to start:', err);

        // Show error dialog to user
        const { dialog } = await import('electron');
        dialog.showErrorBox('Startup Error',
            `Failed to start the application:\n\n${err.message}\n\nPlease check if another instance is already running.`
        );

        app.quit();
    }
});

app.on('window-all-closed', () => {
    stopBackend();
    if (process.platform !== 'darwin') {
        app.quit();
    }
});

app.on('before-quit', () => {
    stopBackend();
});

app.on('activate', () => {
    if (mainWindow === null) {
        createWindow();
    }
});

// Cleanup on unexpected exit
process.on('exit', () => {
    stopBackend();
});

process.on('SIGINT', () => {
    stopBackend();
    process.exit();
});

process.on('SIGTERM', () => {
    stopBackend();
    process.exit();
});
