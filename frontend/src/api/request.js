import axios from 'axios'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import router from '../router'

// 统一请求实例：所有请求走 /api（由 vite 代理转发到后端 8080）
const request = axios.create({
  baseURL: '/api',
  timeout: 10000
})

// 请求拦截：自动附带 JWT
request.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers['Authorization'] = 'Bearer ' + token
  }
  return config
})

/**
 * 响应拦截：统一处理业务码与 401
 *
 * ⚠️ 关键：本项目后端的业务错误也返回 **HTTP 200**（错误体现在 Result.code 上）。
 * 若这里直接 `response.data` 放行，业务失败就被当成成功 —— 例如权属转移参数不匹配时，
 * 后端返回 `{code:500,...}`，页面却弹出「权属转移成功，已上链存证」，
 * 用户以为操作生效，库里其实什么都没发生。
 * 所以必须在这里识别业务码，非 200 一律走 reject，让调用方的 try/catch 能捕获。
 */
request.interceptors.response.use(
  (response) => {
    const data = response.data
    const isWrapped = data && typeof data === 'object' && data.code !== undefined
    // 非 Result 包装的响应（如二维码图片流）原样返回
    if (!isWrapped) {
      return data
    }
    if (data.code !== 200) {
      ElMessage.error(data.message || '操作失败')
      if (data.code === 401) {
        localStorage.clear()
        router.push('/login')
      }
      return Promise.reject(new Error(data.message || '操作失败'))
    }
    return data
  },
  (error) => {
    if (error.response) {
      if (error.response.status === 401) {
        ElMessage.error('登录已过期，请重新登录')
        localStorage.clear()
        router.push('/login')
      } else {
        ElMessage.error(error.response.data?.message || '请求失败')
      }
    } else {
      ElMessage.error('网络异常，无法连接后端')
    }
    return Promise.reject(error)
  }
)

export default request
