import http from './http'

/**
 * 任务分页列表（status/categoryId/priority 过滤 + page/size 分页）。GET /api/tasks
 * @param {{status?: string, categoryId?: number, priority?: string, page?: number, size?: number}} [params]
 * @returns {Promise<{total: number, records: object[]}>}
 */
export function listTasks(params = {}) {
  return http.get('/tasks', { params })
}

/**
 * 新建任务。POST /api/tasks
 * @param {{title: string, description?: string, categoryId?: number, priority: string, status: string, dueTime?: string}} body
 * @returns {Promise<object>} TaskResponse
 */
export function createTask(body) {
  return http.post('/tasks', body)
}

/** 任务详情。GET /api/tasks/{id} */
export function getTask(id) {
  return http.get(`/tasks/${id}`)
}

/** 更新任务（含状态流转，部分字段可空=不改）。PUT /api/tasks/{id} */
export function updateTask(id, body) {
  return http.put(`/tasks/${id}`, body)
}

/** 删除任务。DELETE /api/tasks/{id} */
export function deleteTask(id) {
  return http.delete(`/tasks/${id}`)
}
