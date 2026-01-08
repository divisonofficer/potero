import { sveltekit } from "@sveltejs/kit/vite";
import tailwindcss from "@tailwindcss/vite";
import { defineConfig } from "vite";

export default defineConfig({
  plugins: [tailwindcss(), sveltekit()],
  build: {
    target: "esnext",
  },
  server: {
    host: "0.0.0.0", // Bind to all interfaces for WSL access from Windows
    port: 5173,
    strictPort: true,
    proxy: {
      // Proxy API requests to Ktor backend
      "/api": {
        target: "http://127.0.0.1:8080",
        changeOrigin: true,
        secure: false,
        ws: true,
        configure: (proxy) => {
          // Disable timeout for large file uploads
          proxy.on("proxyReq", (proxyReq, req) => {
            // Remove timeout for uploads
            if (req.url?.includes("/upload")) {
              proxyReq.setTimeout(0);
            }
            console.log("Proxying:", req.method, req.url);
          });
          proxy.on("error", (err, _req, res) => {
            console.log("Proxy error:", err.message);
            if (res && "writeHead" in res) {
              res.writeHead(502, { "Content-Type": "application/json" });
              res.end(
                JSON.stringify({
                  success: false,
                  error: { code: "PROXY_ERROR", message: err.message },
                })
              );
            }
          });
        },
      },
    },
  },
  // Relative paths for Electron file:// protocol
  base: "./",
});
