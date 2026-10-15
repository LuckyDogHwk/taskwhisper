import http from './http'

/**
 * 注册。POST /api/auth/register
 * 后端注册成功同样签发 JWT（返回 AuthResponse），前端注册即登录。
 * @param {string} username
 * @param {string} password
 * @returns {Promise<{token: string, userId: number, username: string}>}
 */
export function register(username, password) {
  return http.post('/auth/register', { username, password })
}

/**
 * 登录。POST /api/auth/login
 * @param {string} username
 * @param {string} password
 * @returns {Promise<{token: string, userId: number, username: string}>}
 */
export function login(username, password) {
  return http.post('/auth/login', { username, password })
}
