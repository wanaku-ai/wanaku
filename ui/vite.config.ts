import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

const outDir = './dist';

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  build: {
    outDir,
    sourcemap: true,
    emptyOutDir: true,
  },
  base: './',
  css: {
    preprocessorOptions: {
      scss: {
        api: 'modern-compiler',
      },
    },
  },
})
