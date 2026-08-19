import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

// Aura 后端：Spring Boot，端口 8001，context-path = /aura
// 开发环境通过代理把 /aura 转发到后端，避免 CORS，同时覆盖 API 与静态资源(/aura/uploads/**)
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
      // Aura logo 图片目录（网页图标 / 智能体头像共用，直接以模块方式引用）
      '@logo': fileURLToPath(new URL('./pictures/logo', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/aura': {
        target: 'http://localhost:8001',
        changeOrigin: true,
      },
    },
  },
})
