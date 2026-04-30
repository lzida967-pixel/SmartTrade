import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import tailwindcss from '@tailwindcss/vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    tailwindcss(),
  ],
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        secure: false,
        // SSE 长连接需要关掉默认的代理超时；并明确不缓冲
        ws: true,
        proxyTimeout: 0,
        timeout: 0,
        configure: (proxy) => {
          proxy.on('proxyRes', (proxyRes) => {
            // 防止上游/中间件加 Content-Length 导致客户端等收完才解析
            delete proxyRes.headers['content-length']
            proxyRes.headers['cache-control'] = 'no-cache, no-transform'
            proxyRes.headers['x-accel-buffering'] = 'no'
          })
        }
      }
    }
  }
})
