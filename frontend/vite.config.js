import { fileURLToPath, URL } from 'node:url';
import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

// Dev: the SPA runs on :5173 and proxies /api → the Spring backend on :8080, so the
// browser only ever talks to one origin and there is no CORS concern (README §3.6).
// Build: `vite build` emits into ../src/main/resources/static so Spring can serve the
// console single-origin from :8080 (web Task 4 history-fallback resolver).
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: '../src/main/resources/static',
    emptyOutDir: true,
  },
  test: {
    environment: 'jsdom',
    globals: true,
  },
});
