<template>
  <div class="dashboard-container">
    <h1 class="dashboard-title">{{ t('dashboard.title') }}</h1>

    <div v-if="loading" class="loading-container">
      <el-skeleton :rows="5" animated />
    </div>

    <div v-else-if="error" class="error-container">
      <el-result icon="error" :title="t('dashboard.loadFailed')">
        <template #extra>
          <el-button type="primary" @click="fetchDashboard">{{ t('dashboard.retry') }}</el-button>
        </template>
      </el-result>
    </div>

    <template v-else>
      <section class="overview-grid" :aria-label="t('dashboard.overview')">
        <div class="stat-card">
          <span class="stat-label">{{ t('dashboard.total') }}</span>
          <span class="stat-value">{{ stats.total }}</span>
        </div>
        <div class="stat-card">
          <span class="stat-label">{{ t('dashboard.pending') }}</span>
          <span class="stat-value">{{ stats.pending }}</span>
        </div>
        <div class="stat-card">
          <span class="stat-label">{{ t('dashboard.completed') }}</span>
          <span class="stat-value">{{ stats.completed }}</span>
        </div>
        <div class="stat-card">
          <span class="stat-label">{{ t('dashboard.favourite') }}</span>
          <span class="stat-value">{{ stats.favourite }}</span>
        </div>
      </section>

      <section class="panel">
        <h2 class="panel-title">{{ t('dashboard.completionRate') }}</h2>
        <el-progress :percentage="stats.completionRate" :stroke-width="14" :format="formatRate" />
      </section>

      <section class="panel">
        <h2 class="panel-title">{{ t('dashboard.attention') }}</h2>
        <ul class="attention-list">
          <li class="attention-item overdue">
            <span class="attention-label">{{ t('dashboard.overdue') }}</span>
            <span class="attention-value">{{ stats.overduePending }}</span>
          </li>
          <li class="attention-item">
            <span class="attention-label">{{ t('dashboard.dueToday') }}</span>
            <span class="attention-value">{{ stats.dueToday }}</span>
          </li>
          <li class="attention-item">
            <span class="attention-label">{{ t('dashboard.dueNext7') }}</span>
            <span class="attention-value">{{ stats.dueNext7 }}</span>
          </li>
        </ul>
      </section>

      <section class="panel">
        <h2 class="panel-title">{{ t('dashboard.dueDistribution') }}</h2>
        <p v-if="!stats.dueByDay.length" class="muted">{{ t('dashboard.noDueSoon') }}</p>
        <ol v-else class="distribution-list" aria-hidden="false">
          <li v-for="day in stats.dueByDay" :key="day.date" class="distribution-row">
            <span class="distribution-date">{{ formatDay(day.date) }}</span>
            <div class="distribution-track">
              <div
                class="distribution-bar"
                :style="{ width: barWidth(day.count) }"
                role="img"
                :aria-label="`${formatDay(day.date)} ${day.count}`"
              ></div>
            </div>
            <span class="distribution-count">{{ day.count }}</span>
          </li>
        </ol>
      </section>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import request from '@/utils/request'

const { t, locale } = useI18n()
const stats = ref({ total: 0, pending: 0, completed: 0, favourite: 0, completionRate: 0, overduePending: 0, dueToday: 0, dueNext7: 0, dueByDay: [] })
const loading = ref(false)
const error = ref(false)

const formatRate = rate => `${Math.round(rate)}%`

const maxDue = computed(() => Math.max(1, ...stats.value.dueByDay.map(day => day.count)))
const barWidth = count => `${Math.round((count / maxDue.value) * 100)}%`

const formatDay = date => {
  const [year, month, day] = date.split('-')
  if (locale.value === 'zh-CN') return `${Number(month)}月${Number(day)}日`
  const parsed = new Date(`${date}T00:00:00`)
  return parsed.toLocaleDateString(locale.value === 'en-US' ? 'en-US' : 'zh-CN', { month: 'short', day: 'numeric' })
}

const fetchDashboard = async () => {
  loading.value = true
  error.value = false
  try {
    const response = await request.get('/task/dashboard')
    if (response.code === 1) {
      stats.value = response.data
    } else {
      error.value = true
    }
  } catch {
    error.value = true
  } finally {
    loading.value = false
  }
}

onMounted(fetchDashboard)
</script>

<style scoped>
.dashboard-container { width: 100%; min-width: 0; color: var(--el-text-color-primary, #243047); }
.dashboard-title { margin: 0 0 20px; font-size: 24px; font-weight: 600; }
.overview-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; }
.stat-card { background: var(--app-surface, #fff); border: 1px solid var(--app-border, #e7ebf0); border-radius: 12px; padding: 18px 16px; display: flex; flex-direction: column; gap: 6px; }
.stat-label { color: var(--app-muted, #697586); font-size: 13px; }
.stat-value { font-size: 26px; font-weight: 650; font-variant-numeric: tabular-nums; letter-spacing: -.4px; }
.stat-card:first-child .stat-value { color: var(--el-color-primary); }
.stat-card { transition: border-color .15s ease; }
@media (hover: hover) { .stat-card:hover { border-color: var(--el-color-primary-light-7); } }
.panel { background: var(--app-surface, #fff); border: 1px solid var(--app-border, #e7ebf0); border-radius: 12px; padding: 20px; margin-top: 16px; }
.panel-title { margin: 0 0 14px; font-size: 16px; font-weight: 600; color: var(--el-text-color-primary, #243047); }
.attention-list { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 8px; }
.attention-item { display: flex; justify-content: space-between; align-items: center; padding: 10px 12px; border-radius: 8px; background: var(--app-bg, #f7f8fa); color: var(--app-muted, #697586); font-size: 14px; }
.attention-item.overdue { background: #fdecec; color: #c04141; }
.attention-item.overdue .attention-value { color: #c04141; }
.attention-value { font-weight: 650; font-size: 18px; color: var(--el-text-color-primary, #243047); }
.distribution-list { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 10px; }
.distribution-row { display: grid; grid-template-columns: 74px minmax(0, 1fr) 28px; align-items: center; gap: 10px; }
.distribution-date { font-size: 13px; color: var(--app-muted, #697586); }
.distribution-track { height: 12px; background: var(--app-bg, #f7f8fa); border-radius: 999px; overflow: hidden; }
.distribution-bar { height: 100%; background: var(--el-color-primary); border-radius: 999px; transition: width 0.25s ease; }
.distribution-count { font-size: 13px; font-weight: 600; text-align: right; }
.muted { color: var(--app-muted, #697586); font-size: 14px; margin: 0; }
.loading-container, .error-container { padding: 20px 0; }
@media (max-width: 760px) {
  .overview-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .panel { padding: 16px; }
}
</style>
