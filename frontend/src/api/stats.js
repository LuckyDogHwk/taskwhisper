import http from './http'

/**
 * 任务统计。GET /api/tasks/stats
 * @returns {Promise<{
 *   statusCounts: Object<string, number>,
 *   categoryCounts: Array<{categoryId: number|null, name: string, count: number}>
 * }>}
 * 说明：categoryCounts 含「未分类」桶（categoryId 为 null），由后端 LEFT JOIN 统计。
 */
export function getStats() {
  return http.get('/tasks/stats')
}
