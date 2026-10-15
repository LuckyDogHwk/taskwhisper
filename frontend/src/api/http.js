import axios from 'axios'
import { ElMessage } from 'element-plus'

/**
 * Axios 实例（baseURL /api，对应后端 @RequestMapping("/api/...")）。
 * 后端统一响应 {code, message, data}（见 common/Result.java），成功 code=0。
 * NL 接口后端最长 30s（DeepSeek 超时），故客户端超时放宽到 60s。
 */
const http = axios.create({
  baseURL: '/api',
  timeout: 60000,
})

/** 请求拦截器：从 localStorage 取 token，挂 Authorization: Bearer */
http.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

/** 401 统一处理：清登录态并跳登录页（路由为 hash 模式） */
function handleUnauthorized() {
  localStorage.removeItem('token')
  localStorage.removeItem('user')
  if (!window.location.hash.startsWith('#/login')) {
    window.location.replace('/#/login')
  }
}

/**
 * 响应拦截器：
 * - HTTP 2xx：解包 Result，code===0 → 直接返回 data；code===401 → 清态跳登录；
 *   其余 code → ElMessage.error(message) 并 reject。
 * - HTTP 非 2xx：后端业务异常会把 HTTP 状态码设成 code（见 GlobalExceptionHandler），
 *   响应体仍是 Result 结构，取 message 提示；401 同样清态跳登录。
 */
http.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res && typeof res === 'object' && 'code' in res) {
      if (res.code === 0) {
        return res.data
      }
      if (res.code === 401) {
        handleUnauthorized()
      } else {
        ElMessage.error(res.message || '请求失败')
      }
      return Promise.reject(new Error(res.message || '请求失败'))
    }
    return res
  },
  (error) => {
    const status = error.response?.status
    const message = error.response?.data?.message
    if (status === 401) {
      handleUnauthorized()
    } else if (message) {
      ElMessage.error(message)
    } else {
      ElMessage.error('网络异常，请确认后端服务已启动')
    }
    return Promise.reject(error)
  }
)

export default http
