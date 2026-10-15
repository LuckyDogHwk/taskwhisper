<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createTask, deleteTask, listTasks, updateTask } from '@/api/tasks'
import { createCategory, listCategories } from '@/api/categories'
import { getStats } from '@/api/stats'
import { useAuthStore } from '@/stores/auth'
import { PRIORITY_OPTIONS, STATUS_OPTIONS, formatDueTime, priorityTagType } from '@/utils/task'
import NlPanel from '@/components/NlPanel.vue'

const router = useRouter()
const auth = useAuthStore()

// —— 列表 / 过滤 / 分页 ——
const tasks = ref([])
const total = ref(0)
const tableLoading = ref(false)
const query = reactive({ page: 1, size: 10, status: '', categoryId: null, priority: '' })

// —— 分类 / 统计 ——
const categories = ref([])
const stats = reactive({ statusCounts: {}, categoryCounts: [] })

async function loadTasks() {
  tableLoading.value = true
  try {
    const data = await listTasks({
      status: query.status || undefined,
      categoryId: query.categoryId || undefined,
      priority: query.priority || undefined,
      page: query.page,
      size: query.size,
    })
    tasks.value = data.records
    total.value = data.total
  } catch {
    // 错误信息已由 http 拦截器统一弹出
  } finally {
    tableLoading.value = false
  }
}

async function loadCategories() {
  try {
    categories.value = await listCategories()
  } catch {
    // 分类加载失败不阻塞主流程
  }
}

async function loadStats() {
  try {
    const data = await getStats()
    stats.statusCounts = data.statusCounts || {}
    stats.categoryCounts = data.categoryCounts || []
  } catch {
    // 统计失败不阻塞主流程
  }
}

function refreshAll() {
  loadTasks()
  loadStats()
}

function onFilterChange() {
  query.page = 1
  loadTasks()
}

function onSizeChange() {
  query.page = 1
  loadTasks()
}

// —— 行内操作 ——
/** 表格内联切换状态：先调接口成功再改本地行数据，失败保持服务端原值 */
async function onStatusChange(row, value) {
  try {
    await updateTask(row.id, { status: value })
    row.status = value
    ElMessage.success('状态已更新')
    loadStats()
  } catch {
    // 错误提示已由 http 拦截器统一弹出，row 未被修改
  }
}

async function onDelete(row) {
  try {
    await deleteTask(row.id)
    ElMessage.success('已删除')
    if (tasks.value.length === 1 && query.page > 1) {
      query.page -= 1
    }
    refreshAll()
  } catch {
    // 错误提示已由 http 拦截器统一弹出
  }
}

// —— 新建 / 编辑弹窗（同一个 Dialog 复用） ——
const dialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref()
const emptyForm = () => ({
  id: null,
  title: '',
  description: '',
  categoryId: null,
  priority: '中',
  status: '待办',
  dueTime: null,
})
const form = reactive(emptyForm())

const formRules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
}

function openCreate() {
  Object.assign(form, emptyForm())
  dialogVisible.value = true
}

function openEdit(row) {
  Object.assign(form, {
    id: row.id,
    title: row.title,
    description: row.description || '',
    categoryId: row.categoryId ?? null,
    priority: row.priority,
    status: row.status,
    dueTime: row.dueTime ? String(row.dueTime).slice(0, 19) : null,
  })
  dialogVisible.value = true
}

async function onSubmit() {
  const ok = await formRef.value.validate().catch(() => false)
  if (!ok) return
  submitting.value = true
  const body = {
    title: form.title.trim(),
    description: form.description.trim() || null,
    categoryId: form.categoryId || null,
    priority: form.priority,
    status: form.status,
    dueTime: form.dueTime || null,
  }
  try {
    if (form.id) {
      await updateTask(form.id, body)
      ElMessage.success('已保存')
    } else {
      await createTask(body)
      ElMessage.success('任务已创建')
    }
    dialogVisible.value = false
    refreshAll()
  } catch {
    // 错误提示已由 http 拦截器统一弹出
  } finally {
    submitting.value = false
  }
}

/** 弹窗内快捷新建分类（后端同名幂等） */
async function onAddCategory() {
  let result
  try {
    result = await ElMessageBox.prompt('输入新分类名称', '新建分类', {
      inputValidator: (v) => (v && v.trim() ? true : '名称不能为空'),
    })
  } catch {
    return // 用户取消
  }
  try {
    const created = await createCategory(result.value.trim())
    await loadCategories()
    form.categoryId = created.id
    ElMessage.success(`分类「${created.name}」已创建`)
  } catch {
    // 错误提示已由 http 拦截器统一弹出
  }
}

function onLogout() {
  auth.logout()
  router.push('/login')
}

onMounted(() => {
  loadTasks()
  loadCategories()
  loadStats()
})
</script>

<template>
  <div class="tasks-page">
    <div class="top-bar">
      <h2>TaskWhisper · 一句话待办</h2>
      <div class="top-bar-right">
        <span class="username">{{ auth.username }}</span>
        <el-button link type="primary" @click="onLogout">退出登录</el-button>
      </div>
    </div>

    <NlPanel @created="refreshAll" />

    <!-- 统计卡片：三状态计数 + 分类分布 -->
    <div class="stats-row">
      <el-card v-for="s in STATUS_OPTIONS" :key="s" class="stat-card" shadow="never">
        <div class="stat-value">{{ stats.statusCounts[s] ?? 0 }}</div>
        <div class="stat-label">{{ s }}</div>
      </el-card>
      <el-card class="stat-card category-card" shadow="never">
        <div class="stat-label">分类分布</div>
        <div class="category-chips">
          <el-tag v-for="c in stats.categoryCounts" :key="c.categoryId ?? 'none'" size="small" effect="plain">
            {{ c.name }} × {{ c.count }}
          </el-tag>
          <span v-if="!stats.categoryCounts.length" class="muted">暂无任务</span>
        </div>
      </el-card>
    </div>

    <el-card shadow="never">
      <!-- 工具条：过滤 + 分页 -->
      <div class="toolbar">
        <el-select v-model="query.status" placeholder="状态" clearable class="filter-select" @change="onFilterChange">
          <el-option v-for="s in STATUS_OPTIONS" :key="s" :label="s" :value="s" />
        </el-select>
        <el-select v-model="query.categoryId" placeholder="分类" clearable class="filter-select" @change="onFilterChange">
          <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
        </el-select>
        <el-select v-model="query.priority" placeholder="优先级" clearable class="filter-select" @change="onFilterChange">
          <el-option v-for="p in PRIORITY_OPTIONS" :key="p" :label="p" :value="p" />
        </el-select>
        <el-button @click="refreshAll">刷新</el-button>
        <div class="toolbar-spacer" />
        <el-button type="primary" @click="openCreate">新建任务</el-button>
      </div>

      <el-table v-loading="tableLoading" :data="tasks" row-key="id" style="width: 100%">
        <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
        <el-table-column label="分类" width="110">
          <template #default="{ row }">{{ row.categoryName || '—' }}</template>
        </el-table-column>
        <el-table-column label="优先级" width="90">
          <template #default="{ row }">
            <el-tag :type="priorityTagType(row.priority)" size="small">{{ row.priority }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="130">
          <template #default="{ row }">
            <el-select
              :model-value="row.status"
              size="small"
              style="width: 110px"
              @change="(value) => onStatusChange(row, value)"
            >
              <el-option v-for="s in STATUS_OPTIONS" :key="s" :label="s" :value="s" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="截止时间" width="150">
          <template #default="{ row }">{{ formatDueTime(row.dueTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="140">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-popconfirm
              title="确定删除该任务？"
              confirm-button-text="删除"
              cancel-button-text="取消"
              @confirm="onDelete(row)"
            >
              <template #reference>
                <el-button link type="danger">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-row">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          background
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
          :page-sizes="[5, 10, 20, 50]"
          @current-change="loadTasks"
          @size-change="onSizeChange"
        />
      </div>
    </el-card>

    <!-- 新建 / 编辑任务弹窗 -->
    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑任务' : '新建任务'" width="520px">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="80px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" maxlength="100" show-word-limit placeholder="任务标题" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="任务描述（可选）"
          />
        </el-form-item>
        <el-form-item label="分类">
          <div class="category-picker">
            <el-select v-model="form.categoryId" placeholder="未分类" clearable class="category-select">
              <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
            </el-select>
            <el-button link type="primary" @click="onAddCategory">+ 新分类</el-button>
          </div>
        </el-form-item>
        <el-form-item label="优先级">
          <el-select v-model="form.priority" style="width: 100%">
            <el-option v-for="p in PRIORITY_OPTIONS" :key="p" :label="p" :value="p" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option v-for="s in STATUS_OPTIONS" :key="s" :label="s" :value="s" />
          </el-select>
        </el-form-item>
        <el-form-item label="截止时间">
          <el-date-picker
            v-model="form.dueTime"
            type="datetime"
            placeholder="选择截止时间（可选）"
            value-format="YYYY-MM-DDTHH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="onSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.tasks-page {
  max-width: 1100px;
  margin: 0 auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.top-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.top-bar h2 {
  margin: 0;
}

.top-bar-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.username {
  color: #606266;
}

.stats-row {
  display: flex;
  gap: 16px;
}

.stat-card {
  flex: 1;
}

.stat-card .stat-value {
  font-size: 28px;
  font-weight: 600;
}

.stat-card .stat-label {
  color: #909399;
  font-size: 13px;
  margin-top: 4px;
}

.category-card {
  flex: 2;
}

.category-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 4px;
}

.muted {
  color: #c0c4cc;
  font-size: 13px;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.filter-select {
  width: 130px;
}

.toolbar-spacer {
  flex: 1;
}

.pagination-row {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}

.category-picker {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
}

.category-select {
  flex: 1;
}
</style>
