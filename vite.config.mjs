import { defineConfig } from 'vite';
export default defineConfig({ base: './', server: { host: '127.0.0.1' }, build: { outDir: 'dist/renderer', emptyOutDir: true, chunkSizeWarningLimit: 700 }, worker: { format: 'es' } });
