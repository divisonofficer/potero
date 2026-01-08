import { sveltekit } from '@sveltejs/kit/vite';
import tailwindcss from '@tailwindcss/vite';
import { defineConfig } from 'vite';

export default defineConfig({
	plugins: [tailwindcss(), sveltekit()],
	build: {
		target: 'esnext'
	},
	server: {
		port: 5173,
		strictPort: true,
		proxy: {
			// Proxy API requests to Ktor backend
			'/api': {
				target: 'http://127.0.0.1:8080',
				changeOrigin: true
			}
		}
	},
	// Relative paths for Electron file:// protocol
	base: './'
});
