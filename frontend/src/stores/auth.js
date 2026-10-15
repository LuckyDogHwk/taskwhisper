import { defineStore } from 'pinia'
import { login as apiLogin, register as apiRegister } from '@/api/auth'

/**
 * 登录态存储：token / user 持久化到 localStorage，刷新页面不丢。
 * 注册接口同样返回 {token, userId, username}（AuthResponse），故注册即登录。
 */
export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    user: JSON.parse(localStorage.getItem('user') || 'null'),
  }),
  getters: {
    isLoggedIn: (state) => Boolean(state.token),
    username: (state) => state.user?.username || '',
  },
  actions: {
    /** 登录成功后写入内存 + localStorage */
    login(username, password) {
      return apiLogin(username, password).then((data) => this.setSession(data))
    },
    /** 注册成功即登录（后端 register 也签发 token） */
    register(username, password) {
      return apiRegister(username, password).then((data) => this.setSession(data))
    },
    /** 写入会话数据（Pinia actions 之间可直接调用） */
    setSession(data) {
      this.token = data.token
      this.user = { userId: data.userId, username: data.username }
      localStorage.setItem('token', data.token)
      localStorage.setItem('user', JSON.stringify(this.user))
    },
    /** 登出：清内存与 localStorage */
    logout() {
      this.token = ''
      this.user = null
      localStorage.removeItem('token')
      localStorage.removeItem('user')
    },
  },
})
