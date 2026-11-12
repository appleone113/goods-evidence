import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 前端开发服务器：默认 5173，反向代理 /api 到后端 8080
// host: '0.0.0.0' 让同一 WiFi 下的手机/其他电脑也能访问（答辩局域网演示用）
// 注意：/api 走 Vite 代理，浏览器请求的是「前端所在地址」，因此二维码/接口地址
// 不需要写死后端 IP，手机访问 http://<本机IP>:5173 即可。
export default defineConfig({
  plugins: [vue()],
  server: {
    host: '0.0.0.0',
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
