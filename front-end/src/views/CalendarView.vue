<template>
  <div class="calendar-container">
    <div class="calendar-toolbar">
      <div class="view-switch" role="group" :aria-label="t('calendar.view')">
        <button type="button" class="view-button" :class="{ active: view === 'month' }" @click="view = 'month'">{{ t('calendar.month') }}</button>
        <button type="button" class="view-button" :class="{ active: view === 'week' }" @click="view = 'week'">{{ t('calendar.week') }}</button>
      </div>
      <h2 class="calendar-title">{{ title }}</h2>
      <div class="nav-actions">
        <button type="button" class="nav-button" :aria-label="t('calendar.today')" @click="goToday">{{ t('calendar.today') }}</button>
        <button type="button" class="nav-button icon" :aria-label="t('calendar.previous')" @click="go(-1)"><el-icon><ArrowLeft /></el-icon></button>
        <button type="button" class="nav-button icon" :aria-label="t('calendar.next')" @click="go(1)"><el-icon><ArrowRight /></el-icon></button>
      </div>
    </div>

    <div class="calendar-body" :aria-busy="tasksLoading">
      <p v-if="tasksLoading" class="calendar-status" role="status">{{ t('calendar.loading', locale === 'zh-CN' ? '正在加载待办事项…' : 'Loading tasks…') }}</p>
      <div v-else-if="tasksError" class="calendar-status" role="alert">
        <span>{{ t('calendar.loadFailed', locale === 'zh-CN' ? '待办事项加载失败' : 'Failed to load tasks') }}</span>
        <button type="button" class="nav-button" @click="scheduleFetch">{{ t('calendar.retry', locale === 'zh-CN' ? '重试' : 'Retry') }}</button>
      </div>
      <!-- 月份视图 -->
      <div v-if="view === 'month'" class="month-view" role="grid" :aria-label="title">
        <div class="weekday-row">
          <div v-for="label in weekdayLabels" :key="label" class="weekday-cell">{{ label }}</div>
        </div>
        <div class="month-grid">
          <button
            v-for="day in monthDays"
            :key="day.key"
            type="button"
            class="day-cell"
            :class="{ 'outside': !day.inMonth, 'today': day.isToday, 'selected': selectedKey === day.key }"
            :aria-pressed="selectedKey === day.key"
            :aria-label="`${day.label}${day.count ? ' · ' + day.count : ''}`"
            @click="select(day)"
          >
            <span class="day-number">{{ day.number }}</span>
            <span v-if="day.count" class="day-badge" aria-hidden="true">{{ day.count }}</span>
            <span v-else class="day-placeholder" aria-hidden="true"></span>
          </button>
        </div>
      </div>

      <!-- 星期视图 -->
      <div v-else class="week-view" role="grid" :aria-label="title">
        <div
          v-for="day in weekDays"
          :key="day.key"
          class="week-day"
          :class="{ 'today': day.isToday, 'selected': selectedKey === day.key }"
          role="button"
          tabindex="0"
          :aria-pressed="selectedKey === day.key"
          :aria-label="day.label"
          @click="select(day)"
          @keydown.enter.prevent="select(day)"
          @keydown.space.prevent="select(day)"
        >
          <div class="week-day-header">
            <span class="week-day-name">{{ day.weekday }}</span>
            <span class="week-day-number" :class="{ 'is-today': day.isToday }">{{ day.number }}</span>
          </div>
          <div class="week-day-body" role="region" :aria-label="day.label">
            <ul v-if="tasksByKey[day.key]?.length" class="week-task-list">
              <li v-for="task in tasksByKey[day.key].slice(0, 5)" :key="task.taskId" class="week-task-item" :class="{ done: task.status }">
                {{ task.taskName }}
              </li>
            </ul>
            <span v-else class="day-placeholder"></span>
          </div>
        </div>
      </div>
    </div>

    <!-- 选中日期面板 -->
    <section v-if="selectedKey" class="day-panel" :aria-label="selectedLabel">
      <h3 class="day-panel-title">{{ selectedLabel }}</h3>
      <p v-if="tasksLoading" class="day-panel-empty" role="status">{{ t('calendar.loading', locale === 'zh-CN' ? '正在加载待办事项…' : 'Loading tasks…') }}</p>
      <p v-else-if="tasksError" class="day-panel-empty">{{ t('calendar.loadFailed', locale === 'zh-CN' ? '待办事项加载失败' : 'Failed to load tasks') }}</p>
      <ul v-else-if="selectedTasks.length" class="panel-task-list">
        <li v-for="task in selectedTasks" :key="task.taskId">
          <button type="button" class="panel-task-item" :class="{ done: task.status }" @click="openTask(task.taskId)">
            <span class="panel-task-name">{{ task.taskName }}</span>
            <span class="panel-task-time">{{ formatStart(task.startTime) }}</span>
          </button>
        </li>
      </ul>
      <p v-else class="day-panel-empty">{{ t('calendar.noTodosYet') }}</p>
    </section>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft, ArrowRight } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import request from '@/utils/request'

const { t, locale } = useI18n()
const router = useRouter()
const view = ref('month')
const cursor = ref(new Date())
const selectedKey = ref(null)
const tasks = ref([])
const tasksLoading = ref(false)
const tasksError = ref(false)
let requestSequence = 0
let disposed = false

const startOfDay = date => new Date(date.getFullYear(), date.getMonth(), date.getDate())
const keyOf = date => `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
const addDays = (date, days) => new Date(date.getFullYear(), date.getMonth(), date.getDate() + days)
const addMonths = (date, months) => new Date(date.getFullYear(), date.getMonth() + months, 1)
const today = ref(startOfDay(new Date()))
const todayKey = computed(() => keyOf(today.value))

const weekdayLabels = computed(() => locale.value === 'zh-CN'
  ? ['一', '二', '三', '四', '五', '六', '日']
  : ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'])

const title = computed(() => {
  const date = cursor.value
  const formatter = new Intl.DateTimeFormat(locale.value === 'zh-CN' ? 'zh-CN' : 'en-US', { year: 'numeric', month: 'long' })
  if (view.value === 'month') return formatter.format(date)
  const monday = weekStart(date)
  const sunday = addDays(monday, 6)
  const dayFmt = new Intl.DateTimeFormat(locale.value === 'zh-CN' ? 'zh-CN' : 'en-US', { month: 'short', day: 'numeric' })
  if (monday.getMonth() === sunday.getMonth()) return `${formatter.format(monday)} · ${dayFmt.format(sunday)}`
  return `${dayFmt.format(monday)} – ${dayFmt.format(sunday)}, ${sunday.getFullYear()}`
})

const weekStart = date => {
  const d = startOfDay(date)
  return addDays(d, -((d.getDay() + 6) % 7))
}

const rangeDays = computed(() => view.value === 'month'
  ? (() => { const first = new Date(cursor.value.getFullYear(), cursor.value.getMonth(), 1); return Array.from({ length: 42 }, (_, i) => addDays(weekStart(first), i)) })()
  : Array.from({ length: 7 }, (_, i) => addDays(weekStart(cursor.value), i)))

const monthDays = computed(() => {
  const first = new Date(cursor.value.getFullYear(), cursor.value.getMonth(), 1)
  return rangeDays.value.map(date => ({
    key: keyOf(date),
    number: date.getDate(),
    inMonth: date.getMonth() === first.getMonth(),
    isToday: keyOf(date) === todayKey.value,
    count: tasksByKey.value[keyOf(date)]?.length || 0,
    label: new Intl.DateTimeFormat(locale.value === 'zh-CN' ? 'zh-CN' : 'en-US', { month: 'long', day: 'numeric', year: 'numeric' }).format(date)
  }))
})

const weekDays = computed(() => rangeDays.value.map(date => ({
  key: keyOf(date),
  number: date.getDate(),
  weekday: new Intl.DateTimeFormat(locale.value === 'zh-CN' ? 'zh-CN' : 'en-US', { weekday: 'short' }).format(date),
  isToday: keyOf(date) === todayKey.value,
  label: new Intl.DateTimeFormat(locale.value === 'zh-CN' ? 'zh-CN' : 'en-US', { month: 'long', day: 'numeric', year: 'numeric' }).format(date)
})))

// 任务按开始时间的本地日期分组
const tasksByKey = computed(() => {
  const groups = {}
  for (const task of tasks.value) {
    if (!task.startTime) continue
    const date = new Date(task.startTime)
    const key = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
    if (!groups[key]) groups[key] = []
    groups[key].push(task)
  }
  return groups
})

const selectedLabel = computed(() => {
  const date = rangeDays.value.find(day => keyOf(day) === selectedKey.value)
  return date ? new Intl.DateTimeFormat(locale.value === 'zh-CN' ? 'zh-CN' : 'en-US', { month: 'long', day: 'numeric', year: 'numeric' }).format(date) : ''
})
const selectedTasks = computed(() => selectedKey.value ? (tasksByKey.value[selectedKey.value] || []) : [])

const formatStart = time => time ? new Date(time).toLocaleString(locale.value === 'zh-CN' ? 'zh-CN' : 'en-US', { hour: '2-digit', minute: '2-digit' }) : ''

const fetchRange = async (seq, days) => {
  if (disposed || seq !== requestSequence) return
  const from = days[0].toISOString()
  const to = addDays(days[days.length - 1], 1).toISOString()
  try {
    const response = await request.get('/task/calendar', { from, to })
    if (disposed || seq !== requestSequence) return
    if (response.code === 1 && Array.isArray(response.data)) {
      tasks.value = response.data
    } else {
      tasksError.value = true
    }
  } catch {
    if (!disposed && seq === requestSequence) tasksError.value = true
  } finally {
    if (!disposed && seq === requestSequence) tasksLoading.value = false
  }
}

let fetchTimer = null
const scheduleFetch = () => {
  if (disposed) return
  const seq = ++requestSequence
  const days = rangeDays.value
  window.clearTimeout(fetchTimer)
  tasks.value = []
  tasksLoading.value = true
  tasksError.value = false
  if (selectedKey.value && !days.some(day => keyOf(day) === selectedKey.value)) {
    selectedKey.value = null
  }
  fetchTimer = window.setTimeout(() => {
    fetchTimer = null
    fetchRange(seq, days)
  }, 120)
}

const go = direction => {
  cursor.value = view.value === 'month' ? addMonths(cursor.value, direction) : addDays(cursor.value, direction * 7)
}

const goToday = () => {
  today.value = startOfDay(new Date())
  cursor.value = today.value
  select({ key: todayKey.value })
}

const select = day => {
  selectedKey.value = day.key
}

const openTask = taskId => router.push(`/home/todo/${taskId}`)

watch(rangeDays, scheduleFetch, { immediate: true, flush: 'sync' })
onBeforeUnmount(() => {
  disposed = true
  ++requestSequence
  window.clearTimeout(fetchTimer)
})
</script>

<style scoped>
.calendar-container { width: 100%; min-width: 0; color: #243047; }
.calendar-toolbar { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; margin-bottom: 16px; }
.view-switch { display: inline-flex; border: 1px solid var(--app-border, #e7ebf0); border-radius: 10px; overflow: hidden; background: var(--app-surface, #fff); }
.view-button { min-height: 40px; padding: 0 16px; border: 0; background: transparent; color: var(--app-muted, #697586); font-size: 14px; cursor: pointer; }
.view-button.active { background: var(--el-color-primary); color: #fff; font-weight: 600; }
.view-button, .nav-button, .day-cell { transition: background-color .12s ease, border-color .12s ease; }
.day-number, .day-badge, .week-day-number { font-variant-numeric: tabular-nums; }
.calendar-title { margin: 0 auto 0 0; font-size: 18px; font-weight: 600; }
.nav-actions { display: flex; gap: 6px; }
.nav-button { min-height: 40px; padding: 0 12px; border: 1px solid var(--app-border, #e7ebf0); border-radius: 8px; background: var(--app-surface, #fff); color: var(--app-muted, #697586); font-size: 13px; cursor: pointer; display: inline-flex; align-items: center; justify-content: center; }
.nav-button.icon { width: 40px; padding: 0; }
.nav-button:hover { color: var(--el-color-primary); border-color: var(--el-color-primary); }
.calendar-body { background: var(--app-surface, #fff); border: 1px solid var(--app-border, #e7ebf0); border-radius: 12px; padding: 16px; }
.calendar-status { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; margin: 0 0 12px; color: var(--app-muted, #697586); font-size: 14px; }
.weekday-row { display: grid; grid-template-columns: repeat(7, minmax(0, 1fr)); gap: 4px; margin-bottom: 8px; }
.weekday-cell { text-align: center; font-size: 12px; color: var(--app-muted, #697586); padding: 6px 0; }
.month-grid { display: grid; grid-template-columns: repeat(7, minmax(0, 1fr)); gap: 4px; }
.day-cell { min-height: 76px; padding: 8px 6px; border: 1px solid transparent; border-radius: 10px; background: transparent; cursor: pointer; display: flex; flex-direction: column; align-items: flex-start; gap: 6px; text-align: left; }
.day-cell:hover { background: var(--app-bg, #f7f8fa); }
.day-cell.outside { color: var(--app-muted, #697586); opacity: 0.55; }
.day-cell.today .day-number { color: var(--el-color-primary); font-weight: 700; }
.day-cell.selected { border-color: var(--el-color-primary); background: var(--el-color-primary-light-9); }
.day-number { font-size: 14px; min-width: 26px; height: 26px; display: inline-flex; align-items: center; justify-content: center; border-radius: 999px; }
.day-cell.today .day-number { border: 1px solid var(--el-color-primary); }
.day-badge { align-self: flex-start; min-width: 20px; height: 20px; padding: 0 6px; border-radius: 999px; background: var(--el-color-primary); color: #fff; font-size: 12px; display: inline-flex; align-items: center; justify-content: center; }
.day-placeholder { min-height: 4px; }
.week-view { display: grid; grid-template-columns: repeat(7, minmax(0, 1fr)); gap: 6px; }
.week-day { border: 1px solid var(--app-border, #e7ebf0); border-radius: 10px; min-width: 0; cursor: pointer; }
.week-day:hover { background: var(--app-bg, #f7f8fa); }
.week-day:focus-visible { outline: 2px solid var(--el-color-primary); outline-offset: 2px; }
.week-day.today { border-color: var(--el-color-primary); }
.week-day.selected { background: var(--el-color-primary-light-9); }
.week-day-header { width: 100%; border: 0; background: transparent; padding: 10px 8px; cursor: pointer; display: flex; flex-direction: column; align-items: center; gap: 4px; color: inherit; }
.week-day-name { font-size: 12px; color: var(--app-muted, #697586); }
.week-day-number { font-size: 15px; font-weight: 600; min-width: 30px; height: 30px; display: inline-flex; align-items: center; justify-content: center; border-radius: 999px; }
.week-day-number.is-today { background: var(--el-color-primary); color: #fff; }
.week-day-body { min-height: 260px; padding: 8px; }
.week-task-list { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 4px; }
.week-task-item { font-size: 12px; line-height: 1.4; padding: 4px 6px; border-radius: 6px; background: var(--app-bg, #f7f8fa); color: #243047; overflow-wrap: anywhere; }
.week-task-item.done { color: var(--app-muted, #697586); text-decoration: line-through; }
.day-panel { margin-top: 16px; background: var(--app-surface, #fff); border: 1px solid var(--app-border, #e7ebf0); border-radius: 12px; padding: 20px; }
.day-panel-title { margin: 0 0 10px; font-size: 16px; font-weight: 600; }
.day-panel-empty { margin: 0; color: var(--app-muted, #697586); font-size: 14px; }
.panel-task-list { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 6px; }
.panel-task-item { width: 100%; display: flex; justify-content: space-between; align-items: center; gap: 12px; padding: 10px 12px; border: 1px solid var(--app-border, #e7ebf0); border-radius: 8px; background: transparent; cursor: pointer; color: #243047; font-size: 14px; text-align: left; }
.panel-task-item:hover { border-color: var(--el-color-primary); }
.panel-task-item.done .panel-task-name { color: var(--app-muted, #697586); text-decoration: line-through; }
.panel-task-name { min-width: 0; overflow-wrap: anywhere; }
.panel-task-time { flex: none; color: var(--app-muted, #697586); font-size: 12px; }
@media (max-width: 760px) {
  .calendar-body { padding: 10px; }
  .day-cell { min-height: 48px; padding: 6px 2px; align-items: center; border-radius: 8px; }
  .day-badge { min-width: 18px; height: 18px; padding: 0 4px; font-size: 11px; }
  .month-grid { gap: 2px; }
  .week-view { display: flex; flex-direction: column; gap: 8px; }
  .week-day { display: flex; align-items: stretch; }
  .week-day-header { width: 76px; flex: none; border-right: 1px solid var(--app-border, #e7ebf0); }
  .week-day-body { min-height: 0; flex: 1; }
}
</style>
