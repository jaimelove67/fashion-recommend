import { fileURLToPath } from 'node:url'
import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import { trendImageProxyPlugin } from './dev/trendImageProxy.mjs'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, fileURLToPath(new URL('.', import.meta.url)), 'DEV_')
  const backendTarget = env.DEV_API_TARGET?.trim() || 'http://localhost:8080'
  return {
    plugins: [vue(), trendImageProxyPlugin()],
    server: {
      port: 5173,
      proxy: {
        // Preserve the browser Host so same-origin writes pass the backend CORS check.
        '/api': { target: backendTarget, changeOrigin: false },
        '/actuator': { target: backendTarget, changeOrigin: false }
      }
    }
  }
})
