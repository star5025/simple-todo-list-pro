<template>
  <el-container class="home-container">
    <Header :mobile="isMobile" :navigation-open="navigationOpen" @open-navigation="navigationOpen = true" />

    <el-drawer v-if="isMobile" v-model="navigationOpen" direction="ltr" size="min(320px, 88vw)" :title="t('header.navigation')">
      <div id="mobile-navigation"><Sidebar @navigate="navigationOpen = false" /></div>
    </el-drawer>
    <el-container class="main-container">
      <Sidebar v-if="!isMobile" />
      
      <el-main class="main-content" :class="{ 'locale-fade': localeSwitching }">
        <!-- 主页内容区域 -->
        <div class="home-content">
          <router-view v-slot="{ Component, route: current }">
            <transition :name="innerTransition" mode="out-in">
              <keep-alive include="TodoList">
                <component :is="Component" :key="current.fullPath" v-if="Component" />
              </keep-alive>
            </transition>
          </router-view>
        </div>
      </el-main>
    </el-container>

    <ReminderPanel
      v-if="reminders.length"
      :reminders="reminders"
      @open="openReminder"
      @clear="reminders = []"
    />
    
  </el-container>
</template>

<script setup>
import { provide, ref, watch, onMounted, onBeforeUnmount } from 'vue'
import { onBeforeRouteLeave, useRouter, useRoute } from 'vue-router'
import { useMediaQuery } from '@/composables/useMediaQuery'
import { useI18n } from 'vue-i18n'
import { createReminderPoller } from '@/utils/reminders'
import Header from '@/components/Header.vue'
import Sidebar from '@/components/Sidebar.vue'
import ReminderPanel from '@/components/ReminderPanel.vue'
import request from '@/utils/request'

const router = useRouter()
const route = useRoute()
const isMobile = useMediaQuery('(max-width: 760px)')
const navigationOpen = ref(false)
// 侧栏三个主面板之间切换用缩放浮现；日历与其当日页之间用淡入。
const panelPaths = ['/home/list', '/home/dashboard', '/home/calendar']
const isDayPath = path => path === '/home/calendar' || path.startsWith('/home/day/')
const pathOnly = path => (path || '').split('?')[0]
const innerTransition = ref('')
watch(() => route.fullPath, (to, from) => {
  const next = pathOnly(to), prev = pathOnly(from)
  innerTransition.value = from && to !== from
    ? (panelPaths.includes(prev) && panelPaths.includes(next) ? 'panel-grow'
      : isDayPath(prev) && isDayPath(next) ? 'day-fade' : '')
    : ''
  navigationOpen.value = false
})
watch(isMobile, () => { navigationOpen.value = false })
const { t, locale } = useI18n()

// 语言切换时仅内容区做淡入上浮过渡，Header 与 Footer 不参与
const localeSwitching = ref(false)
let localeTimer = null
watch(locale, () => {
  localeSwitching.value = true
  window.clearTimeout(localeTimer)
  localeTimer = window.setTimeout(() => { localeSwitching.value = false }, 280)
})

const sessionToken = localStorage.getItem('token')
let remindersActive = true
const reminders = ref([])

const openReminder = reminder => {
  reminders.value = reminders.value.filter(item => item.key !== reminder.key)
  if (remindersActive && localStorage.getItem('token') === sessionToken) {
    router.push(`/home/todo/${reminder.taskId}`)
  }
}

const reminderPoller = createReminderPoller({
  isActive: () => remindersActive && !!sessionToken && localStorage.getItem('token') === sessionToken,
  fetchDue: async () => {
    const response = await request.get('/reminders/due')
    if (response.code !== 1) throw new Error(response.msg || t('reminders.fetchFailed'))
    return response.data
  },
  acknowledge: async ({ taskId, revision }) => {
    const response = await request.post(`/reminders/${taskId}/ack`, { revision })
    if (response.code !== 1) throw new Error(response.msg || t('reminders.ackFailed'))
    const item = reminders.value.find(entry => entry.key === `${taskId}:${revision}`)
    if (item) item.acked = true
  },
  notify: (reminder) => {
    // 聚合到可翻阅的提醒面板，而不是逐个弹出通知。
    const key = `${reminder.taskId}:${reminder.revision}`
    if (!reminders.value.some(item => item.key === key)) {
      reminders.value.push({ key, taskId: reminder.taskId, taskName: reminder.taskName, acked: false })
    }
    // close 用于版本失效和退出，不是确认成功回调。
    return () => {
      reminders.value = reminders.value.filter(entry => entry.key !== key)
    }
  },
  onError: () => console.error(t('reminders.retryFailed'))
})

const stopReminders = () => {
  remindersActive = false
  reminderPoller.stop()
}

// Handle drawer Escape before page-level shortcuts can discard an unfinished form.
const handleNavigationKeydown = event => {
  if (navigationOpen.value && event.key === 'Escape') {
    event.preventDefault()
    event.stopImmediatePropagation()
    navigationOpen.value = false
  }
}
onMounted(() => {
  reminderPoller.start()
  document.addEventListener('keydown', handleNavigationKeydown, true)
})
onBeforeRouteLeave(stopReminders)
onBeforeUnmount(() => {
  stopReminders()
  document.removeEventListener('keydown', handleNavigationKeydown, true)
})
</script>

<style scoped>
.home-container { flex-direction: column; flex: 1; min-width: 0; height: 100%; min-height: 0; }
.main-container { width: 100%; max-width: 1440px; margin: 0 auto 16px; flex: 1; min-width: 0; min-height: 0; align-items: stretch; }
.main-content { padding: 32px; min-width: 0; min-height: 0; overflow-y: auto; }
.home-content { width: 100%; max-width: 1040px; margin: 0 auto; min-width: 0; }
.el-fade-in-enter-active, .el-fade-in-leave-active { transition: opacity .12s ease; }
/* 语言切换内容区淡入上浮过渡 */
.locale-fade { animation: locale-fade 0.26s ease; }
@keyframes locale-fade { 0% { opacity: 0; transform: translateY(6px); } 100% { opacity: 1; transform: none; } }
/* 日历与当日页面之间：纯淡入 + 极轻微缩放，不产生位移与额外滚动空间 */
.day-fade-enter-active, .day-fade-leave-active { transition: opacity .18s ease, transform .18s ease; }
.day-fade-enter-from, .day-fade-leave-to { opacity: 0; transform: scale(.995); }
/* 待办 / 看板 / 日历 三个面板切换：内容轻微放大浮现 */
.panel-grow-enter-active { transition: opacity .22s ease, transform .22s cubic-bezier(.22, .61, .36, 1); }
.panel-grow-leave-active { transition: opacity .12s ease; }
.panel-grow-enter-from { opacity: 0; transform: scale(.975); }
.panel-grow-leave-to { opacity: 0; }
@media (max-width: 1000px) { .main-content { padding: 24px; } }
@media (max-width: 760px) { .main-content { padding: 16px 12px 24px; } }
</style>
