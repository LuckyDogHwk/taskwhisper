/**
 * 任务域常量与展示辅助。
 * 枚举值全中文，与后端实体约定一致（设计文档 §4）：
 * priority ∈ {高, 中, 低}（默认 中）；status ∈ {待办, 进行中, 已完成}（默认 待办）。
 */

export const STATUS_OPTIONS = ['待办', '进行中', '已完成']

export const PRIORITY_OPTIONS = ['高', '中', '低']

const PRIORITY_TAG_TYPE = {
  高: 'danger',
  中: 'warning',
  低: 'info',
}

/** 优先级 → el-tag 颜色 */
export function priorityTagType(priority) {
  return PRIORITY_TAG_TYPE[priority] || 'info'
}

/** 后端 LocalDateTime（ISO 如 2026-10-15T14:00:00）→ 「2026-10-15 14:00」 */
export function formatDueTime(value) {
  if (!value) return '—'
  return String(value).replace('T', ' ').slice(0, 16)
}
