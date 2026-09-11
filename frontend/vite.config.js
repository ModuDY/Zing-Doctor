import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 前端开发服务器：/api 与 /entry 代理到后端 8081
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    host: '0.0.0.0',
    proxy: {
      '/api': { target: 'http://localhost:8081', changeOrigin: true },
      '/entry': { target: 'http://localhost:8081', changeOrigin: true }
    }
  },
  build: {
    outDir: 'dist',
    sourcemap: false
  }
})
