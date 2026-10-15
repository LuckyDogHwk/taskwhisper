import http from './http'

/**
 * AI 一句话建任务（核心卖点）。POST /api/tasks/nl
 * 未配置 DeepSeek Key → 后端 503；LLM 输出校验失败 → 502；
 * 两种情况的后端 message 均由 http 拦截器统一弹出。
 * @param {string} text 自然语言描述，如「明天下午三点提醒我开会，高优先级」
 * @returns {Promise<object>} TaskResponse
 */
export function createFromNl(text) {
  return http.post('/tasks/nl', { text })
}
