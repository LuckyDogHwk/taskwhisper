import { createRouter, createWebHashHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

/**
 * 路由。用 hash 模式：npm run build 产物拷进后端 static/ 单进程演示时，
 * 刷新 /#/tasks 无需后端做 SPA forward 也能正确命中页面（设计文档 §7 运行模式 2）。
 */
const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/', redirect: '/tasks' },
    { path: '/login', name: 'login', component: () => import('@/views/LoginView.vue') },
    { path: '/tasks', name: 'tasks', component: () => import('@/views/TasksView.vue') },
  ],
})

/** 守卫：无 token → /login；已登录访问 /login → /tasks */
router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.path !== '/login' && !auth.token) {
    return '/login'
  }
  if (to.path === '/login' && auth.token) {
    return '/tasks'
  }
  return true
})

export default router
