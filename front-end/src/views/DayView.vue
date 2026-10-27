<template>
  <div class="day-view">
    <div class="day-toolbar">
      <button type="button" class="back-button" @click="goBack">
        <el-icon><ArrowLeft /></el-icon>{{ t('dayView.back') }}
      </button>
      <h2 class="day-title">{{ title }}</h2>
    </div>

    <div v-if="loading" class="day-state" role="status">
      <el-icon class="is-loading"><Loading /></el-icon>{{ t('dayView.loading') }}
    </div>
    <div v-else-if="error" class="day-state" role="alert">
      <span>{{ t('dayView.loadFailed') }}</span>
      <button type="button" class="retry-button" @click="fetchDay">{{ t('dayView.retry') }}</button>
    </div>

    <template v-else>
      <section class="day-section" :aria-label="t('dayView.started')">
        <h3 class="section-title">{{ t('dayView.started') }} <span class="section-count">{{ started.length }}</span></h3>
        <ul v-if="started.length" class="task-list">
          <li v-for="task in started" :key="task.taskId">
            <button type="button" class="task-item" :class="{ done: task.status }" @click="openTask(task.taskId)">
              <span class="task-name">{{ task.taskName }}</span>
              <span class="task-time">{{ formatTime(task.startTime) }}</span>
            </button>
          </li>
        </ul>
        <p v-else class="empty-text">{{ t('dayView.empty') }}</p>
      </section>

      <section class="day-section" :aria-label="t('dayView.due')">
        <h3 class="section-title">{{ t('dayView.due') }} <span class="section-count">{{ due.length }}</span></h3>
        <ul v-if="due.length" class="task-list">
          <li v-for="task in due" :key="task.taskId">
            <button type="button" class="task-item" :class="{ done: task.status }" @click="openTask(task.taskId)">
              <span class="task-name">{{ task.taskName }}</span>
              <span class="task-time">{{ formatTime(task.dueTime) }}</span>
            </button>
          </li>
        </ul>
        <p v-else class="empty-text">{{ t('dayView.empty') }}</p>
      </section>
    </template>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Loading } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import request from '@/utils/request'

const { t, locale } = useI18n()
const route = useRoute()
const router = useRouter()
const started = ref([])
const due = ref([])
const loading = ref(true)
const error = ref(false)
let sequence = 0
let disposed = false

const dayKey = computed(() => String(route.params.date || ''))
const parsed = computed(() => {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(dayKey.value)
  if (!match) return null
  const date = new Date(Number(match[1]), Number(match[2]) - 1, Number(match[3]))
  return date.getFullYear() === Number(match[1]) && date.getMonth() === Number(match[2]) - 1
    && date.getDate() === Number(match[3]) ? date : null
})

const title = computed(() => parsed.value
  ? new Intl.DateTimeFormat(locale.value === 'zh-CN' ? 'zh-CN' : 'en-US', { year: 'numeric', month: 'long', day: 'numeric', weekday: 'long' }).format(parsed.value)
  : t('dayView.invalidDate'))

const formatTime = time => time
  ? new Date(time).toLocaleTimeString(locale.value === 'zh-CN' ? 'zh-CN' : 'en-US', { hour: '2-digit', minute: '2-digit' })
  : ''

const fetchDay = async () => {
  const date = parsed.value
  if (!date) { error.value = true; loading.value = false; return }
  const from = date.toISOString()
  const to = new Date(date.getFullYear(), date.getMonth(), date.getDate() + 1).toISOString()
  const current = ++sequence
  loading.value = true
  error.value = false
  try {
    const response = await request.get('/task/day', { from, to })
    if (disposed || current !== sequence) return
    if (response.code === 1) {
      started.value = response.data?.started || []
      due.value = response.data?.due || []
    } else {
      error.value = true
    }
  } catch {
    if (!disposed && current === sequence) error.value = true
  } finally {
    if (!disposed && current === sequence) loading.value = false
  }
}

const openTask = taskId => router.push(`/home/todo/${taskId}`)
const goBack = () => router.push('/home/calendar')
// Alt + ← 返回日历
const handleBackKeydown = event => {
  if (event.altKey && event.key === 'ArrowLeft') {
    event.preventDefault()
    goBack()
  }
}

watch(dayKey, fetchDay, { immediate: true })
onMounted(() => window.addEventListener('keydown', handleBackKeydown))
onBeforeUnmount(() => {
  disposed = true
  sequence++
  window.removeEventListener('keydown', handleBackKeydown)
})
</script>

<style scoped>
.day-view { width: 100%; min-width: 0; color: #243047; }
.day-toolbar { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; margin-bottom: 16px; }
.back-button, .retry-button { display: inline-flex; align-items: center; gap: 6px; min-height: 40px; padding: 0 12px; border: 1px solid var(--app-border, #e7ebf0); border-radius: 8px; background: var(--app-surface, #fff); color: var(--app-muted, #697586); font-size: 13px; cursor: pointer; }
.back-button:hover, .retry-button:hover { color: var(--el-color-primary); border-color: var(--el-color-primary); }
.day-title { margin: 0; font-size: 18px; font-weight: 600; }
.day-state { display: flex; align-items: center; gap: 10px; min-height: 120px; justify-content: center; color: var(--app-muted, #697586); font-size: 14px; }
.day-section { background: var(--app-surface, #fff); border: 1px solid var(--app-border, #e7ebf0); border-radius: 12px; padding: 16px; }
.day-section + .day-section { margin-top: 12px; }
.section-title { display: flex; align-items: center; gap: 8px; margin: 0 0 10px; font-size: 15px; font-weight: 600; }
.section-count { min-width: 20px; height: 20px; padding: 0 6px; border-radius: 999px; background: var(--el-color-primary-light-9); color: var(--el-color-primary); font-size: 12px; display: inline-flex; align-items: center; justify-content: center; }
.task-list { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 6px; }
.task-item { width: 100%; display: flex; justify-content: space-between; align-items: center; gap: 12px; min-height: 44px; padding: 10px 12px; border: 1px solid var(--app-border, #e7ebf0); border-radius: 8px; background: transparent; cursor: pointer; color: #243047; font-size: 14px; text-align: left; }
.task-item:hover { border-color: var(--el-color-primary); }
.task-item.done .task-name { color: var(--app-muted, #697586); text-decoration: line-through; }
.task-name { min-width: 0; overflow-wrap: anywhere; }
.task-time { flex: none; color: var(--app-muted, #697586); font-size: 12px; font-variant-numeric: tabular-nums; }
.empty-text { margin: 0; color: var(--app-muted, #697586); font-size: 14px; }
@media (max-width: 760px) {
  .day-view { padding: 0; }
  .day-section { padding: 12px; }
}
</style>
