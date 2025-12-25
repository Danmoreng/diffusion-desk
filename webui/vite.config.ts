import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

// https://vite.dev/config/
export default defineConfig({
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  plugins: [vue()],
  base: '/app/',
  build: {
    outDir: '../public/app',
    emptyOutDir: true
  },
  server: {
    proxy: {
      '/v1': {
        target: 'http://localhost:1234',
        changeOrigin: true
      },
      '/outputs': {
        target: 'http://localhost:1234',
        changeOrigin: true
      }
    }
  },
})
