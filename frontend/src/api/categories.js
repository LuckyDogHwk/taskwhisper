import http from './http'

/** 当前用户分类列表。GET /api/categories → [{id, name}] */
export function listCategories() {
  return http.get('/categories')
}

/** 新建分类（同名幂等，返回已有分类）。POST /api/categories → {id, name} */
export function createCategory(name) {
  return http.post('/categories', { name })
}
