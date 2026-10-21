<template>
  <el-container class="home-container">
    <Header :mobile="isMobile" :navigation-open="navigationOpen" @open-navigation="navigationOpen = true" />

    <el-drawer v-if="isMobile" v-model="navigationOpen" direction="ltr" size="min(320px, 88vw)" :title="t('header.navigation')">
      <div id="mobile-navigation"><Sidebar @navigate="navigationOpen = false" /></div>
    </el-drawer>
    <el-container class="main-container">
      <Sidebar v-if="!isMobile" />
      
      <el-main class="main-content">
        <!-- 主页内容区域 -->
        <div class="home-content">
          <router-view v-slot="{ Component }">
            <transition name="el-fade-in" mode="out-in">
              <component :is="Component" :key="$route.fullPath" v-if="Component" />
            </transition>
          </router-view>
        </div>
      </el-main>
    </el-container>
    
  </el-container>
</template>

<script setup>
import { provide, ref, watch, onMounted, onBeforeUnmount } from 'vue'
import { onBeforeRouteLeave, useRouter, useRoute } from 'vue-router'
import { useMediaQuery } from '@/composables/useMediaQuery'
import { useI18n } from 'vue-i18n'
import { createReminderPoller } from '@/utils/reminders'
import { ElNotification } from 'element-plus'
import Header from '@/components/Header.vue'
import Sidebar from '@/components/Sidebar.vue'
import request from '@/utils/request'

const router = useRouter()
const route = useRoute()
const isMobile = useMediaQuery('(max-width: 760px)')
const navigationOpen = ref(false)
watch(() => route.fullPath, () => { navigationOpen.value = false })
watch(isMobile, () => { navigationOpen.value = false })
const { t } = useI18n()

// 创建筛选条件响应式引用，设置默认筛选条件
const filterParams = ref({
  status: false,    // 默认只显示未完成的待办事项
  orderBy: 'dueTime' // 默认按截止时间排序
})

// 创建更新筛选条件的方法
const updateFilter = (params) => {
  filterParams.value = {...filterParams.value, ...params}
}

// 提供筛选条件和更新方法给子组件
provide('filterParams', filterParams)
provide('updateFilter', updateFilter)

const sessionToken = localStorage.getItem('token')
let remindersActive = true
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
  },
  notify: (reminder) => {
    const notification = ElNotification({
      title: t('reminders.title'),
      message: t('reminders.message', { taskName: reminder.taskName }),
      type: 'warning',
      duration: 0,
      onClick: () => {
        if (remindersActive && localStorage.getItem('token') === sessionToken) {
          router.push(`/home/todo/${reminder.taskId}`)
        }
      }
    })
    return () => notification.close()
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
.home-container { flex-direction: column; flex: 1; min-width: 0; }
.main-container { width: 100%; max-width: 1440px; margin: 0 auto; flex: 1; min-width: 0; align-items: stretch; }
.main-content { padding: 32px; min-width: 0; overflow: visible; }
.home-content { width: 100%; max-width: 1040px; margin: 0 auto; min-width: 0; }
.el-fade-in-enter-active, .el-fade-in-leave-active { transition: opacity .12s ease; }
@media (max-width: 1000px) { .main-content { padding: 24px; } }
@media (max-width: 760px) { .main-content { padding: 16px 12px 24px; } }
</style>
