<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { createFromNl } from '@/api/nl'
import { formatDueTime, priorityTagType } from '@/utils/task'

/** 创建成功后通知父组件刷新列表与统计 */
const emit = defineEmits(['created'])

const text = ref('')
const loading = ref(false)
const resultVisible = ref(false)
const result = ref({})

async function onCreate() {
  const content = text.value.trim()
  if (!content) return
  loading.value = true
  try {
    result.value = await createFromNl(content)
    resultVisible.value = true
    text.value = ''
    ElMessage.success('任务已创建')
    emit('created')
  } catch {
    // 503（未配 Key）/ 502（解析失败）等错误信息已由 http 拦截器统一弹出
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <el-card class="nl-panel" shadow="never">
    <template #header>
      <div class="nl-header">
        <span class="nl-title">一句话建任务</span>
        <el-tag size="small" type="warning" effect="plain">AI · DeepSeek 解析</el-tag>
      </div>
    </template>

    <div class="nl-body">
      <el-input
        v-model="text"
        type="textarea"
        :rows="2"
        maxlength="500"
        show-word-limit
        placeholder="例如：明天下午三点提醒我开会，高优先级，属于工作"
      />
      <el-button type="primary" :loading="loading" :disabled="!text.trim()" @click="onCreate">
        解析并创建
      </el-button>
    </div>

    <!-- 解析结果展示：逐项列出后端返回的字段，任务已落库并出现在列表中 -->
    <el-dialog v-model="resultVisible" title="解析结果 · 任务已创建" width="480px">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="标题">{{ result.title }}</el-descriptions-item>
        <el-descriptions-item label="描述">{{ result.description || '（空）' }}</el-descriptions-item>
        <el-descriptions-item label="优先级">
          <el-tag :type="priorityTagType(result.priority)">{{ result.priority }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="状态">{{ result.status }}</el-descriptions-item>
        <el-descriptions-item label="截止时间">{{ formatDueTime(result.dueTime) }}</el-descriptions-item>
        <el-descriptions-item label="分类">{{ result.categoryName || '（未分类）' }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button type="primary" @click="resultVisible = false">好的</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<style scoped>
.nl-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.nl-title {
  font-weight: 600;
}

.nl-body {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}
</style>
