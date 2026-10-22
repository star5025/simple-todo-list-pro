<template>
  <el-aside class="sidebar" :aria-label="t('header.navigation')">
    <div class="sidebar-container">
      <router-link to="/home/list" class="list-link" @click="$emit('navigate')">
        <el-icon><List /></el-icon>{{ t('todoList.title') }}
      </router-link>
      <router-link to="/home/dashboard" class="list-link" @click="$emit('navigate')">
        <el-icon><DataAnalysis /></el-icon>{{ t('dashboard.title') }}
      </router-link>
      <!-- 新增待办按钮 -->
      <div class="add-todo-section">
        <button 
          class="custom-button custom-button--primary custom-button--large"
          @click="handleAddTodo"
        >
          <el-icon><Plus /></el-icon>
          {{ t('sidebar.addTodo') }}
        </button>
      </div>
      
      <!-- 个人信息按钮 -->
      <div class="user-info-section">
        <button 
          class="custom-button custom-button--primary custom-button--large"
          @click="handleUserInfo"
        >
          <el-icon><User /></el-icon>
          {{ t('sidebar.userInfo') }}
        </button>
      </div>
      
      <!-- 收藏待办卡片 -->
      <el-card class="favourite-card" shadow="never">
        <template #header>
          <div class="favourite-header">
            <el-icon><Star /></el-icon>
            <span>{{ t('sidebar.favourites') }}</span>
          </div>
        </template>
        
        <div class="favourite-content">
          <div v-if="loadingFavourites" class="loading-container">
            <el-skeleton :rows="3" animated />
          </div>
          
          <div v-else-if="favouriteTodos.length === 0" class="empty-container">
            <el-text size="small" type="info">{{ t('sidebar.noFavourites') }}</el-text>
          </div>
          
          <div v-else class="favourite-list">
            <div 
              v-for="todo in favouriteTodos" 
              :key="todo.taskId"
              class="favourite-item"
              @click="goToTodoDetail(todo.taskId)"
            >
              <div class="favourite-item-name">{{ todo.taskName }}</div>
              <div class="favourite-item-due" v-if="todo.dueTime">
                {{ formatDueTime(todo.dueTime) }}
              </div>
            </div>
          </div>
        </div>
      </el-card>
      
      <!-- 筛选区域 -->
      <el-card class="filter-card" shadow="never">
        <template #header>
          <div class="filter-header">
            <el-icon><Filter /></el-icon>
            <span>{{ t('sidebar.filters') }}</span>
          </div>
        </template>
        
        <div class="filter-content">
          <!-- 时间排序筛选 -->
          <div class="filter-group">
            <el-text class="filter-group-label" size="small">{{ t('sidebar.sortBy') }}</el-text>
            <div class="filter-buttons">
              <button 
                :class="[
                  'custom-button', 
                  'custom-button--small',
                  timeFilter.field === 'dueTime' ? 'custom-button--primary' : 'custom-button--default'
                ]"
                :aria-pressed="timeFilter.field === 'dueTime'"
                @click="setTimeFilter('dueTime')"
              >
                {{ t('sidebar.dueTime') }}
                <el-icon v-if="timeFilter.field === 'dueTime'">
                  <ArrowUp v-if="timeFilter.order === 'asc' && timeFilter.field === 'dueTime'" />
                  <ArrowDown v-else-if="timeFilter.order === 'desc' && timeFilter.field === 'dueTime'" />
                </el-icon>
              </button>
              
              <button 
                :class="[
                  'custom-button', 
                  'custom-button--small',
                  timeFilter.field === 'createdTime' ? 'custom-button--primary' : 'custom-button--default'
                ]"
                :aria-pressed="timeFilter.field === 'createdTime'"
                @click="setTimeFilter('createdTime')"
              >
                {{ t('sidebar.createdTime') }}
                <el-icon v-if="timeFilter.field === 'createdTime'">
                  <ArrowUp v-if="timeFilter.order === 'asc' && timeFilter.field === 'createdTime'" />
                  <ArrowDown v-else-if="timeFilter.order === 'desc' && timeFilter.field === 'createdTime'" />
                </el-icon>
              </button>
              
              <button 
                :class="[
                  'custom-button', 
                  'custom-button--small',
                  timeFilter.field === 'startTime' ? 'custom-button--primary' : 'custom-button--default'
                ]"
                :aria-pressed="timeFilter.field === 'startTime'"
                @click="setTimeFilter('startTime')"
              >
                {{ t('sidebar.startTime') }}
                <el-icon v-if="timeFilter.field === 'startTime'">
                  <ArrowUp v-if="timeFilter.order === 'asc' && timeFilter.field === 'startTime'" />
                  <ArrowDown v-else-if="timeFilter.order === 'desc' && timeFilter.field === 'startTime'" />
                </el-icon>
              </button>
            </div>
          </div>
          
          <!-- 完成状态筛选 -->
          <div class="filter-group">
            <el-text class="filter-group-label" size="small">{{ t('sidebar.status') }}</el-text>
            <div class="filter-buttons">
              <button 
                :class="[
                  'custom-button', 
                  'custom-button--small',
                  statusFilter === null ? 'custom-button--primary' : 'custom-button--default'
                ]"
                :aria-pressed="statusFilter === null"
                @click="setStatusFilter(null)"
              >
                {{ t('sidebar.all') }}
              </button>
              
              <button 
                :class="[
                  'custom-button', 
                  'custom-button--small',
                  statusFilter === false ? 'custom-button--primary' : 'custom-button--default'
                ]"
                :aria-pressed="statusFilter === false"
                @click="setStatusFilter(false)"
              >
                {{ t('sidebar.pending') }}
              </button>
              
              <button 
                :class="[
                  'custom-button', 
                  'custom-button--small',
                  statusFilter === true ? 'custom-button--primary' : 'custom-button--default'
                ]"
                :aria-pressed="statusFilter === true"
                @click="setStatusFilter(true)"
              >
                {{ t('sidebar.completed') }}
              </button>
            </div>
          </div>
          
        </div>
      </el-card>
    </div>
  </el-aside>
</template>

<script setup>
import { ref, inject, watch, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { 
  ArrowUp, 
  ArrowDown, 
  Plus,
  Filter,
  User,
  Star,
  List,
  DataAnalysis
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import request from '@/utils/request'

const { t } = useI18n()
const router = useRouter()
const emit = defineEmits(['navigate'])
const filterParams = inject('filterParams', ref({}))
const initialOrder = filterParams.value.orderBy || ''

// 注入更新筛选条件的方法
const updateFilter = inject('updateFilter', null)

// 时间筛选条件（单选）
const timeFilter = ref({
  field: initialOrder.replace(/Desc$/, '') || null,
  order: initialOrder ? (initialOrder.endsWith('Desc') ? 'desc' : 'asc') : null
})

// 状态筛选
const statusFilter = ref(filterParams.value.status ?? null) // null: 全部, false: 未完成, true: 已完成

// 收藏待办相关
const favouriteTodos = ref([])
const loadingFavourites = ref(false)

const handleAddTodo = () => {
  router.push('/home/add')
  emit('navigate')
}

const handleUserInfo = () => {
  router.push('/home/userinfo')
  emit('navigate')
}

// 设置时间筛选条件：点击同一字段在升序/降序间切换，不再取消排序
const setTimeFilter = (field) => {
  if (timeFilter.value.field === field) {
    timeFilter.value.order = timeFilter.value.order === 'asc' ? 'desc' : 'asc'
  } else {
    timeFilter.value.field = field
    timeFilter.value.order = 'asc'
  }
  
  // 立即应用筛选条件
  applyFilters()
}

// 设置状态筛选
const setStatusFilter = (status) => {
  statusFilter.value = status
  
  // 立即应用筛选条件
  applyFilters()
}

// 应用筛选条件
const applyFilters = () => {
  // 仅在非列表页时跳转，避免重复导航造成列表闪动
  if (router.currentRoute.value.path !== '/home/list') {
    router.push('/home/list')
    emit('navigate')
  }
  
  // 构造发送给后端的参数
  const params = {
    page: 1,
    pageSize: 10,
    status: statusFilter.value
  }
  
  // 根据时间字段和排序方向构造orderBy参数
  if (timeFilter.value.field && timeFilter.value.order) {
    const fieldMap = {
      'dueTime': 'dueTime',
      'createdTime': 'createdTime',
      'startTime': 'startTime'
    }
    
    const fieldName = fieldMap[timeFilter.value.field] || 'createdTime'
    const orderSuffix = timeFilter.value.order === 'desc' ? 'Desc' : ''
    params.orderBy = fieldName + orderSuffix
  }
  // 如果timeFilter.field或timeFilter.order为null，则不设置orderBy参数
  
  // 调用更新筛选条件的方法
  if (updateFilter) {
    updateFilter(params)
  } else {
    console.warn('updateFilter 方法未找到')
  }
  
}

// 获取收藏的待办事项
const firstFavouriteLoad = ref(true)
let favouriteRefreshTimer = null

const fetchFavouriteTodos = async (silent = false) => {
  const showSkeleton = !silent && firstFavouriteLoad.value
  if (showSkeleton) loadingFavourites.value = true
  try {
    // 获取用户收藏的待办事项，最多显示5个
    const response = await request.get('/task/pageQuery', {
      page: 1,
      pageSize: 5,
      favourite: true,
      orderBy: 'createdTimeDesc' // 按创建时间倒序排列，确保显示最新的收藏
    })
    
    if (response.code === 1) {
      // 确保favourite字段正确设置
      favouriteTodos.value = response.data.records.map(todo => ({
        ...todo,
        favourite: true // 明确设置为已收藏
      }))
    } else {
      ElMessage.error(response.msg || '获取收藏待办失败')
    }
  } catch (error) {
    console.error('获取收藏待办失败')
  } finally {
    if (showSkeleton) {
      loadingFavourites.value = false
      firstFavouriteLoad.value = false
    }
  }
}

// 格式化截止时间
const formatDueTime = (dueTime) => {
  if (!dueTime) return ''
  const date = new Date(dueTime)
  const today = new Date()
  
  // 如果是今天
  if (date.toDateString() === today.toDateString()) {
    return '今天 ' + date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  }
  
  // 如果是今年
  if (date.getFullYear() === today.getFullYear()) {
    return `${date.getMonth() + 1}月${date.getDate()}日`
  }
  
  // 其他情况显示完整日期
  return `${date.getFullYear()}年${date.getMonth() + 1}月${date.getDate()}日`
}

// 跳转到待办详情
const goToTodoDetail = (taskId) => {
  router.push(`/home/todo/${taskId}`)
  emit('navigate')
}

// 处理收藏状态变化：合并连续操作，静默刷新，避免骨架屏闪烁
const handleFavouriteChange = () => {
  window.clearTimeout(favouriteRefreshTimer)
  favouriteRefreshTimer = window.setTimeout(() => {
    fetchFavouriteTodos(true)
  }, 400)
}

// 组件挂载时应用一次筛选条件并获取收藏待办
onMounted(() => {
  // 移除了自动应用筛选条件的代码，改为只获取收藏待办
  fetchFavouriteTodos()
  
  // 监听收藏状态变化事件
  window.addEventListener('favouriteChanged', handleFavouriteChange)
})

// 组件卸载时移除事件监听
onUnmounted(() => {
  window.removeEventListener('favouriteChanged', handleFavouriteChange)
  window.clearTimeout(favouriteRefreshTimer)
})

// 暴露方法给父组件
defineExpose({
  fetchFavouriteTodos
})
</script>

<style scoped>
.sidebar { width: 248px; flex: 0 0 248px; padding: 28px 20px; background: var(--app-surface); border-right: 1px solid var(--app-border); overflow: visible; }
.sidebar-container { display: flex; flex-direction: column; gap: 16px; }
.list-link { display: flex; align-items: center; gap: 10px; color: var(--el-text-color-primary); font-size: 14px; font-weight: 600; text-decoration: none; border-radius: 8px; padding: 10px 12px; min-height: 44px; }
.list-link.router-link-active { background: var(--el-color-primary-light-9); color: var(--el-color-primary); }
.list-link:hover { background: var(--app-bg); }
.user-info-section .custom-button { background: white; color: var(--app-muted); border-color: var(--app-border); }
.favourite-card, .filter-card { border: 0; border-radius: 0; }
.sidebar :deep(.el-card__header) { padding: 16px 0 10px; border: 0; }
.sidebar :deep(.el-card__body) { padding: 0; }
.favourite-header, .filter-header { display: flex; align-items: center; gap: 8px; font-size: 12px; font-weight: 600; color: var(--app-muted); }
.favourite-header .el-icon, .filter-header .el-icon { font-size: 15px; }
.favourite-list { display: flex; flex-direction: column; gap: 4px; }
.favourite-item { padding: 10px; border-radius: 8px; cursor: pointer; }
.favourite-item:hover { background: var(--app-bg); }
.favourite-item-name { font-size: 13px; color: var(--el-text-color-primary); overflow-wrap: anywhere; }
.favourite-item-due { font-size: 12px; color: var(--app-muted); margin-top: 4px; }
.empty-container, .loading-container { padding: 12px 8px; }
.filter-content { display: flex; flex-direction: column; gap: 20px; }
.filter-group { display: flex; flex-direction: column; gap: 8px; }
.filter-group-label { color: var(--app-muted); font-size: 12px; }
.filter-buttons { display: flex; flex-direction: column; gap: 4px; }
.custom-button { border: 1px solid transparent; border-radius: 8px; cursor: pointer; display: flex; gap: 8px; align-items: center; justify-content: center; min-height: 40px; font-size: 13px; padding: 8px 12px; width: 100%; transition: background .12s ease; }
.custom-button--large { font-size: 14px; }
.custom-button--primary { background: var(--el-color-primary); border-color: var(--el-color-primary); color: white; }
.filter-buttons .custom-button { justify-content: space-between; }
.filter-buttons .custom-button--primary { background: var(--el-color-primary-light-9); border-color: transparent; color: var(--el-color-primary); }
.custom-button--default { background: transparent; color: var(--app-muted); }
.custom-button:hover { filter: brightness(.97); }

/* 排序箭头放大且不拦截点击，整个按钮都是可点区域 */
.filter-buttons .custom-button .el-icon {
  font-size: 16px;
  margin-left: 4px;
  pointer-events: none;
}
@media (max-width: 760px) { .sidebar { width: 100%; padding: 16px 20px 32px; border: 0; } .custom-button { min-height: 44px; } }
</style>
