/**
 * Fix absolute paths in build files for Electron file:// protocol
 * Converts /_app/ to ./_app/ and /icon_ to ./icon_
 * Also fixes SvelteKit base path for file:// protocol
 */

import { readFileSync, writeFileSync, readdirSync, statSync } from 'fs';
import { join, dirname } from 'path';
import { fileURLToPath } from 'url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const buildPath = join(__dirname, '..', 'build');

console.log('Fixing paths in build files for Electron...');

// Fix index.html
const indexPath = join(buildPath, 'index.html');
let indexContent = readFileSync(indexPath, 'utf-8');

// Replace absolute paths with relative paths in index.html
indexContent = indexContent.replace(/href="\/_app\//g, 'href="./_app/');
indexContent = indexContent.replace(/href="\/icon_/g, 'href="./icon_');
indexContent = indexContent.replace(/import\("\/_app\//g, 'import("./_app/');
indexContent = indexContent.replace(/src="\/_app\//g, 'src="./_app/');

// Note: Using Electron's custom protocol (app://) instead of file://
// This allows SvelteKit routing to work correctly without path hacks
// Keep the base path as empty since app:// protocol provides proper URL paths

writeFileSync(indexPath, indexContent, 'utf-8');
console.log('  Fixed: index.html');

// Fix JS files in _app directory
function fixJsFiles(dir) {
    const files = readdirSync(dir);
    for (const file of files) {
        const filePath = join(dir, file);
        const stat = statSync(filePath);

        if (stat.isDirectory()) {
            fixJsFiles(filePath);
        } else if (file.endsWith('.js')) {
            let content = readFileSync(filePath, 'utf-8');
            const original = content;

            // Fix common absolute path patterns in JS
            content = content.replace(/"\/_app\//g, '"./_app/');
            content = content.replace(/'\/_app\//g, "'./_app/");
            content = content.replace(/`\/_app\//g, "`./_app/");
            content = content.replace(/"\/icon_/g, '"./icon_');

            // Fix template literal patterns like `${var}/_app/` when var could be empty
            // These patterns appear in SvelteKit's generated code for asset loading
            content = content.replace(/\+"\/_app\//g, '+"./_app/');
            content = content.replace(/\+"\/icon_/g, '+"./icon_');

            if (content !== original) {
                writeFileSync(filePath, content, 'utf-8');
                console.log(`  Fixed: ${file}`);
            }
        }
    }
}

const appDir = join(buildPath, '_app');
if (statSync(appDir).isDirectory()) {
    fixJsFiles(appDir);
}

console.log('Paths fixed successfully!');
