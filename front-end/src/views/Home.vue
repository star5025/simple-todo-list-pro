<template>
  <el-container class="home-container">
    <Header />
    
    <el-container class="main-container">
      <Sidebar />
      
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
    
    <!-- 房屋图案按钮，用于跳转到待办项列表 -->
    <div class="home-button" @click="goToTodoList">
      <svg 
        xmlns="http://www.w3.org/2000/svg" 
        width="24" 
        height="24" 
        viewBox="0 0 24 24" 
        fill="none" 
        stroke="currentColor" 
        stroke-width="2" 
        stroke-linecap="round" 
        stroke-linejoin="round"
        class="home-icon"
      >
        <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"></path>
        <polyline points="9 22 9 12 15 12 15 22"></polyline>
      </svg>
    </div>
  </el-container>
</template>

<script setup>
import { provide, ref, onMounted, onBeforeUnmount } from 'vue'
import { onBeforeRouteLeave, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { createReminderPoller } from '@/utils/reminders'
import { ElNotification } from 'element-plus'
import Header from '@/components/Header.vue'
import Sidebar from '@/components/Sidebar.vue'
import request from '@/utils/request'

const router = useRouter()
const { t } = useI18n()

// 创建筛选条件响应式引用，设置默认筛选条件
const filterParams = ref({
  status: false,    // 默认只显示未完成的待办事项
  orderBy: 'dueTime' // 默认按截止时间排序
})

// 获取TodoList组件的引用
const todoListRef = ref(null)

// 创建更新筛选条件的方法
const updateFilter = (params) => {
  filterParams.value = {...filterParams.value, ...params}
}

// 提供筛选条件和更新方法给子组件
provide('filterParams', filterParams)
provide('updateFilter', updateFilter)

// 跳转到待办项列表
const goToTodoList = () => {
  router.push('/home/list')
}

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

onMounted(() => reminderPoller.start())
onBeforeRouteLeave(stopReminders)
onBeforeUnmount(stopReminders)
</script>

<style scoped>
/* 缩短过渡动画时间 */
.el-fade-in-enter-active {
  transition: opacity 0.2s ease-in-out !important;
}

.el-fade-in-leave-active {
  transition: opacity 0.15s ease-in-out !important;
}

.home-container {
  height: 100%;
  display: flex;
  flex-direction: column;
  position: relative;
}

.main-container {
  flex: 1;
  display: flex;
  overflow: hidden;
}

.main-content {
  flex: 1;
  padding: 20px;
  overflow-y: auto;
  background-color: transparent; /* 取消白色背景 */
  display: flex;
  flex-direction: column;
}

.home-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  height: 100%;
}

.home-content h1 {
  color: #409EFF;
  margin-bottom: 16px;
}

.home-content p {
  color: #606266;
  font-size: 16px;
}

.home-button {
  position: fixed;
  bottom: 80px; /* 略高于footer */
  right: 30px;
  width: 50px;
  height: 50px;
  background-color: rgba(64, 158, 255, 0.1); /* 低饱和度蓝色背景 */
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
  transition: all 0.3s ease;
  z-index: 1000;
}

.home-button:hover {
  background-color: rgba(64, 158, 255, 0.2); /* 悬停时 slightly increase 饱和度 */
  transform: scale(1.1);
}

.home-icon {
  color: #409EFF;
  width: 24px;
  height: 24px;
}
</style>