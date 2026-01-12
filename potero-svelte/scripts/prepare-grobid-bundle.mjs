/**
 * Prepare GROBID bundle for Electron distribution
 *
 * This script copies only the necessary GROBID files for Windows deployment,
 * excluding source code and other platform binaries.
 *
 * Usage: node scripts/prepare-grobid-bundle.mjs
 */

import { existsSync, mkdirSync, cpSync, rmSync } from 'fs';
import { join } from 'path';
import { homedir } from 'os';

const GROBID_VERSION = '0.8.2';
const SOURCE_DIR = join(homedir(), '.potero', 'grobid', `grobid-${GROBID_VERSION}`);
const TARGET_DIR = join(process.cwd(), 'grobid-bundle');

// Files/folders to copy
const ITEMS_TO_COPY = [
    // The compiled server JAR
    { from: `grobid-service/build/libs/grobid-service-${GROBID_VERSION}-onejar.jar`, to: `grobid-service-${GROBID_VERSION}-onejar.jar` },

    // grobid-home contents (runtime dependencies)
    { from: 'grobid-home/config', to: 'grobid-home/config' },
    { from: 'grobid-home/language-detection', to: 'grobid-home/language-detection' },
    { from: 'grobid-home/lexicon', to: 'grobid-home/lexicon' },
    { from: 'grobid-home/lib', to: 'grobid-home/lib' },
    { from: 'grobid-home/models', to: 'grobid-home/models' },
    { from: 'grobid-home/pdf2xml', to: 'grobid-home/pdf2xml' },
    { from: 'grobid-home/schemas', to: 'grobid-home/schemas' },
    { from: 'grobid-home/sentence-segmentation', to: 'grobid-home/sentence-segmentation' },

    // pdfalto - Windows only (for Electron Windows build)
    { from: 'grobid-home/pdfalto/languages', to: 'grobid-home/pdfalto/languages' },
    { from: 'grobid-home/pdfalto/win-64', to: 'grobid-home/pdfalto/win-64' },
];

console.log('Preparing GROBID bundle for Electron...');
console.log(`Source: ${SOURCE_DIR}`);
console.log(`Target: ${TARGET_DIR}`);

// Check source exists
if (!existsSync(SOURCE_DIR)) {
    console.error(`\nError: GROBID source not found at ${SOURCE_DIR}`);
    console.error('Please run the application once to download and build GROBID first.');
    process.exit(1);
}

// Check onejar exists (indicates successful build)
const onejarPath = join(SOURCE_DIR, `grobid-service/build/libs/grobid-service-${GROBID_VERSION}-onejar.jar`);
if (!existsSync(onejarPath)) {
    console.error(`\nError: GROBID onejar not found at ${onejarPath}`);
    console.error('GROBID may not be built yet. Please run the application once to build GROBID.');
    process.exit(1);
}

// Clean target directory
if (existsSync(TARGET_DIR)) {
    console.log('\nCleaning existing bundle...');
    rmSync(TARGET_DIR, { recursive: true });
}

// Create target directory
mkdirSync(TARGET_DIR, { recursive: true });
mkdirSync(join(TARGET_DIR, 'grobid-home', 'pdfalto'), { recursive: true });

// Copy items
console.log('\nCopying files...');
for (const item of ITEMS_TO_COPY) {
    const sourcePath = join(SOURCE_DIR, item.from);
    const targetPath = join(TARGET_DIR, item.to);

    if (!existsSync(sourcePath)) {
        console.warn(`  Warning: ${item.from} not found, skipping...`);
        continue;
    }

    console.log(`  ${item.from} -> ${item.to}`);
    cpSync(sourcePath, targetPath, { recursive: true });
}

// Create empty tmp directory
mkdirSync(join(TARGET_DIR, 'grobid-home', 'tmp'), { recursive: true });

console.log('\n✓ GROBID bundle created at:', TARGET_DIR);
console.log('\nNext steps:');
console.log('1. The bundle will be included in electron-builder.yml extraResources');
console.log('2. Run: npm run electron:build:win');
