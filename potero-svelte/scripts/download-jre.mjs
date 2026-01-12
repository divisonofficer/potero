/**
 * JRE Download Script for Windows x64
 * Downloads Adoptium JRE 17 for bundling with Electron app
 *
 * Usage: node scripts/download-jre.mjs
 */

import { execSync } from 'child_process';
import { existsSync, mkdirSync, rmSync, renameSync, readdirSync } from 'fs';
import { join, dirname } from 'path';
import { fileURLToPath } from 'url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const JRE_DIR = join(__dirname, '..', 'jre-bundle');
const TARGET_DIR = join(JRE_DIR, 'jre-win-x64');

// Adoptium JRE 17 LTS for Windows x64
const JRE_VERSION = '17.0.13+11';
const JRE_FILENAME = `OpenJDK17U-jre_x64_windows_hotspot_${JRE_VERSION.replace('+', '_')}.zip`;
const JRE_URL = `https://github.com/adoptium/temurin17-binaries/releases/download/jdk-${encodeURIComponent(JRE_VERSION)}/${JRE_FILENAME}`;

async function downloadJRE() {
    console.log('=== JRE Download Script ===\n');

    // Check if JRE already exists
    if (existsSync(TARGET_DIR)) {
        const javaExe = join(TARGET_DIR, 'bin', 'java.exe');
        if (existsSync(javaExe)) {
            console.log(`JRE already exists at: ${TARGET_DIR}`);
            console.log('Skipping download. Delete the folder to re-download.\n');
            return;
        }
    }

    // Create JRE directory
    mkdirSync(JRE_DIR, { recursive: true });
    const zipPath = join(JRE_DIR, 'jre.zip');

    console.log(`Downloading JRE ${JRE_VERSION}...`);
    console.log(`URL: ${JRE_URL}\n`);

    try {
        // Download using curl (available on Windows 10+, Linux, macOS)
        execSync(`curl -L "${JRE_URL}" -o "${zipPath}"`, {
            stdio: 'inherit',
            timeout: 300000 // 5 minutes timeout
        });
    } catch (error) {
        console.error('Failed to download JRE:', error.message);
        console.log('\nTrying alternative download with PowerShell...');

        try {
            // Fallback to PowerShell for Windows
            execSync(`powershell -Command "Invoke-WebRequest -Uri '${JRE_URL}' -OutFile '${zipPath}'"`, {
                stdio: 'inherit',
                timeout: 300000
            });
        } catch (psError) {
            console.error('PowerShell download also failed:', psError.message);
            process.exit(1);
        }
    }

    console.log('\nExtracting JRE...');

    try {
        // Try using unzip (Linux/macOS) or PowerShell (Windows)
        if (process.platform === 'win32') {
            execSync(`powershell -Command "Expand-Archive -Path '${zipPath}' -DestinationPath '${JRE_DIR}' -Force"`, {
                stdio: 'inherit'
            });
        } else {
            execSync(`unzip -o "${zipPath}" -d "${JRE_DIR}"`, {
                stdio: 'inherit'
            });
        }
    } catch (error) {
        console.error('Failed to extract JRE:', error.message);
        process.exit(1);
    }

    // Find and rename extracted folder
    const extractedFolders = readdirSync(JRE_DIR).filter(name =>
        name.startsWith('jdk-') && name.includes('-jre')
    );

    if (extractedFolders.length > 0) {
        const extractedPath = join(JRE_DIR, extractedFolders[0]);

        // Remove existing target if exists
        if (existsSync(TARGET_DIR)) {
            rmSync(TARGET_DIR, { recursive: true });
        }

        renameSync(extractedPath, TARGET_DIR);
        console.log(`\nJRE extracted to: ${TARGET_DIR}`);
    } else {
        console.error('Could not find extracted JRE folder');
        process.exit(1);
    }

    // Cleanup zip file
    if (existsSync(zipPath)) {
        rmSync(zipPath);
        console.log('Cleaned up temporary files.');
    }

    // Verify installation
    const javaExe = join(TARGET_DIR, 'bin', 'java.exe');
    if (existsSync(javaExe)) {
        console.log('\n=== JRE Download Complete ===');
        console.log(`Java executable: ${javaExe}`);
    } else {
        console.error('\nWarning: java.exe not found. JRE may not be correctly extracted.');
    }
}

downloadJRE().catch(error => {
    console.error('Unexpected error:', error);
    process.exit(1);
});
