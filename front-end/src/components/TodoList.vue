<template>
  <div class="todo-list-container">
    <el-card class="todo-list-card">
      <template #header>
        <div class="card-header">
          <div class="header-left">
            <h1 class="list-title">{{ t('todoList.title') }} <span class="task-count">({{ total }})</span></h1>
          </div>
          <div class="header-right">
            <el-switch
              v-model="compactMode"
              :active-text="t('todoList.compact')"
              inactive-text=""
              :aria-label="t('todoList.compact')"
            />
            <el-button
              v-if="!batchMode"
              plain
              :icon="Delete" 
              @click="toggleBatchMode"
            >
              {{ t('todoList.batchDelete') }}
            </el-button>
            <el-button 
              v-else
              plain
              :icon="DeleteFilled" 
              @click="toggleBatchMode"
            >
              {{ t('todoList.exit') }}
            </el-button>
          </div>
        </div>
      </template>
      
      <div class="filter-bar">
        <div class="filter-group">
          <span class="filter-label">{{ t('sidebar.sortBy') }}</span>
          <div class="filter-chips">
            <button
              v-for="field in sortFields"
              :key="field"
              type="button"
              class="filter-chip"
              :class="{ active: timeFilter.field === field }"
              :aria-pressed="timeFilter.field === field"
              @click="setTimeFilter(field)"
            >
              {{ t(`sidebar.${field}`) }}
              <el-icon v-if="timeFilter.field === field" class="chip-arrow" aria-hidden="true">
                <ArrowUp v-if="timeFilter.order === 'asc'" />
                <ArrowDown v-else />
              </el-icon>
            </button>
          </div>
        </div>
        <div class="filter-group">
          <span class="filter-label">{{ t('sidebar.status') }}</span>
          <div class="filter-chips">
            <button
              v-for="option in statusOptions"
              :key="String(option.value)"
              type="button"
              class="filter-chip"
              :class="{ active: statusFilter === option.value }"
              :aria-pressed="statusFilter === option.value"
              @click="setStatusFilter(option.value)"
            >
              {{ t(`sidebar.${option.key}`) }}
            </button>
          </div>
        </div>
      </div>

      <div v-if="loading" class="loading-container">
        <el-skeleton :rows="5" animated />
      </div>
      
      <div v-else-if="todos.length === 0" class="empty-container">
        <el-empty :description="t('todoList.noTodos')" />
      </div>
      
      <div v-else>
        <!-- 按日期分组显示待办事项 -->
        <template v-if="showDateGroups && !compactMode">
          <div 
            v-for="(group, date) in groupedTodos" 
            :key="date" 
            class="date-group"
          >
            <div class="date-header">
              <el-text size="large" class="date-title">{{ formatDateHeader(date) }}</el-text>
            </div>
            
            <div 
              v-for="todo in group" 
              :key="todo.taskId" 
              class="todo-item"
              :class="{ 'completed': todo.status, 'selected': batchMode && selectedTodoIds.includes(todo.taskId), 'batch-mode': batchMode }"
              :role="batchMode ? 'checkbox' : 'link'"
              :aria-checked="batchMode ? selectedTodoIds.includes(todo.taskId) : undefined"
              :aria-label="todo.taskName"
              tabindex="0"
              @keydown="handleTodoKeydown($event, todo)"
              @click="handleTodoClick(todo)"
            >
              <div class="todo-info">
                <el-checkbox 
                  v-if="!batchMode"
                  :model-value="todo.status"
                  :aria-label="todo.taskName"
                  @change="(val) => updateTodoStatus(todo, val)"
                  @click.stop
                  class="todo-checkbox"
                  :class="{ 'just-completed': justCompletedId === todo.taskId }"
                />
                <span v-else class="selection-indicator" aria-hidden="true">
                  <el-icon v-if="selectedTodoIds.includes(todo.taskId)"><Check /></el-icon>
                </span>
                <span 
                  class="todo-name" 
                  :class="{ 'completed-text': todo.status }"
                  >{{ todo.taskName }}</span>
              </div>
              
              <div class="todo-meta">
                <button
                  v-if="!batchMode"
                  type="button"
                  class="icon-button favourite-icon"
                  :class="{ 'favourited': todo.favourite, 'pop': poppingTaskId === todo.taskId }"
                  :aria-label="`${t('addTodo.favourite')}: ${todo.taskName}`"
                  :aria-pressed="!!todo.favourite"
                  @click.stop="toggleFavourite(todo)"
                >
                  <el-icon aria-hidden="true"><Star v-if="!todo.favourite" /><StarFilled v-else /></el-icon>
                </button>
                <el-tag
                  v-if="!compactMode"
                  :type="getCountdownTagType(todo)"
                  size="small"
                  class="countdown-tag"
                >
                  {{ getCountdownText(todo) }}
                </el-tag>
                <button
                  v-if="!batchMode"
                  type="button"
                  class="icon-button delete-icon"
                  :aria-label="`${t('todoList.delete')}: ${todo.taskName}`"
                  @click.stop="confirmDelete(todo)"
                >
                  <el-icon aria-hidden="true"><Delete /></el-icon>
                </button>
              </div>
            </div>
          </div>
        </template>
        
        <!-- 不按日期分组显示待办事项 -->
        <template v-else>
          <div 
            v-for="todo in todos" 
            :key="todo.taskId" 
            class="todo-item"
            :class="{ 'completed': todo.status, 'selected': batchMode && selectedTodoIds.includes(todo.taskId), 'batch-mode': batchMode }"
            :role="batchMode ? 'checkbox' : 'link'"
            :aria-checked="batchMode ? selectedTodoIds.includes(todo.taskId) : undefined"
            :aria-label="todo.taskName"
            tabindex="0"
            @keydown="handleTodoKeydown($event, todo)"
            @click="handleTodoClick(todo)"
          >
            <div class="todo-info">
              <el-checkbox 
                v-if="!batchMode"
                :model-value="todo.status"
                :aria-label="todo.taskName"
                @change="(val) => updateTodoStatus(todo, val)"
                @click.stop
                class="todo-checkbox"
              />
              <span v-else class="selection-indicator" aria-hidden="true">
                <el-icon v-if="selectedTodoIds.includes(todo.taskId)"><Check /></el-icon>
              </span>
              <span 
                class="todo-name" 
                :class="{ 'completed-text': todo.status }"
                >{{ todo.taskName }}</span>
            </div>
            
            <div class="todo-meta">
              <button
                v-if="!batchMode"
                type="button"
                class="icon-button favourite-icon"
                :class="{ 'favourited': todo.favourite, 'pop': poppingTaskId === todo.taskId }"
                :aria-label="`${t('addTodo.favourite')}: ${todo.taskName}`"
                :aria-pressed="!!todo.favourite"
                @click.stop="toggleFavourite(todo)"
              >
                <el-icon aria-hidden="true"><Star v-if="!todo.favourite" /><StarFilled v-else /></el-icon>
              </button>
              <el-tag
                v-if="!compactMode"
                :type="getCountdownTagType(todo)"
                size="small"
                class="countdown-tag"
              >
                {{ getCountdownText(todo) }}
              </el-tag>
              <button
                v-if="!batchMode"
                type="button"
                class="icon-button delete-icon"
                :aria-label="`${t('todoList.delete')}: ${todo.taskName}`"
                @click.stop="confirmDelete(todo)"
              >
                <el-icon aria-hidden="true"><Delete /></el-icon>
              </button>
            </div>
          </div>
        </template>
      </div>
      
      <!-- 批量删除操作栏 -->
      <div v-if="batchMode && selectedTodoIds.length > 0" class="batch-action-bar">
        <div class="batch-info" role="status">
          {{ t('todoList.selectedItems', [selectedTodoIds.length]) }}
        </div>
        <div class="batch-actions">
          <el-button plain @click="confirmBatchDelete">{{ t('todoList.delete') }}</el-button>
          <el-button @click="cancelBatchMode">{{ t('todoList.cancel') }}</el-button>
        </div>
      </div>
      
      <div v-if="total > 0" class="pagination-container">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50]"
          :total="total"
          :layout="isMobile ? 'prev, pager, next' : 'total, sizes, prev, pager, next, jumper'"
          :pager-count="isMobile ? 5 : 7"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
        <div v-if="isMobile" class="mobile-page-size">
          <label for="todo-page-size">{{ t('todoList.pageSize') }}</label>
          <el-select
            id="todo-page-size"
            :model-value="pageSize"
            :aria-label="t('todoList.pageSize')"
            @change="handleSizeChange"
          >
            <el-option v-for="size in [10, 20, 50]" :key="size" :label="String(size)" :value="size" />
          </el-select>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, watch, computed, onActivated, onDeactivated, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import { ArrowDown, ArrowUp, Check, Delete, DeleteFilled, Star, StarFilled } from '@element-plus/icons-vue'
import { useMediaQuery } from '@/composables/useMediaQuery'
import { useI18n } from 'vue-i18n'
import { toTaskDateOnly, parseTaskDate } from '@/utils/taskDates'

const { t, locale } = useI18n()
const router = useRouter()
const route = useRoute()
const isMobile = useMediaQuery('(max-width: 1100px)')

// 数据相关
const todos = ref([])
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const showDateGroups = ref(true) // 控制是否显示日期分组
const compactMode = ref(false) // 简洁显示：平铺列表，仅保留完成/删除/收藏操作
const batchMode = ref(false) // 控制是否处于批量删除模式
const selectedTodoIds = ref([]) // 用于批量删除的选中项

// 筛选状态：由待办列表面板自己维护
const sortFields = ['dueTime', 'createdTime', 'startTime']
const statusOptions = [
  { key: 'all', value: null },
  { key: 'pending', value: false },
  { key: 'completed', value: true }
]
const timeFilter = ref({ field: 'dueTime', order: 'asc' })
const statusFilter = ref(false)

const queryParams = computed(() => {
  const params = {}
  if (statusFilter.value !== null) params.status = statusFilter.value
  if (timeFilter.value.field && timeFilter.value.order) {
    params.orderBy = timeFilter.value.field + (timeFilter.value.order === 'desc' ? 'Desc' : '')
  }
  return params
})

const applyFilters = () => {
  currentPage.value = 1
  selectedTodoIds.value = []
  scrollToListTop()
  if (listActive) fetchTodos()
}

// 点击同一字段：升序 → 降序 → 取消排序
const setTimeFilter = field => {
  if (timeFilter.value.field !== field) {
    timeFilter.value = { field, order: 'asc' }
  } else if (timeFilter.value.order === 'asc') {
    timeFilter.value = { field, order: 'desc' }
  } else {
    timeFilter.value = { field: null, order: null }
  }
  applyFilters()
}

// 再次点击当前状态则取消该筛选（回到全部）
const setStatusFilter = status => {
  statusFilter.value = status !== null && statusFilter.value === status ? null : status
  applyFilters()
}

// 按指定时间字段分组的待办事项
const groupedTodos = computed(() => {
  const groups = {}
  
  // 确定用于分组的时间字段
  let groupByField = 'createdTime' // 默认按创建时间分组
  
  // 根据当前排序字段决定分组字段
  if (timeFilter.value.field) {
    groupByField = timeFilter.value.field
  }
  
  todos.value.forEach(todo => {
    // 获取用于分组的时间字段值
    let timeValue = null
    
    // 根据分组字段获取对应的时间值
    switch (groupByField) {
      case 'dueTime':
        timeValue = todo.dueTime
        break
      case 'startTime':
        timeValue = todo.startTime
        break
      case 'createdTime':
      default:
        // 检查不同的字段名（后端可能使用不同的字段名）
        if (todo.createTime) {
          timeValue = todo.createTime
        } else if (todo.createdTime) {
          timeValue = todo.createdTime
        }
        break
    }
    
    // 获取日期部分（不包含时间）
    let timeStr = null
    if (timeValue) {
      try {
        timeStr = toTaskDateOnly(timeValue)
      } catch (e) {
        console.error('日期解析错误:', e)
      }
    }
    
    if (timeStr) {
      if (!groups[timeStr]) {
        groups[timeStr] = []
      }
      
      groups[timeStr].push(todo)
    } else {
      // 如果没有有效的时间值，归入"未指定"组
      const unspecifiedGroup = t('todoList.unspecifiedDate')
      if (!groups[unspecifiedGroup]) {
        groups[unspecifiedGroup] = []
      }
      groups[unspecifiedGroup].push(todo)
    }
  })
  
  return groups
})

// 格式化日期标题
const formatDateHeader = (dateStr) => {
  // 处理"未指定"组
  if (dateStr === t('todoList.unspecifiedDate')) {
    return t('todoList.unspecifiedDate')
  }
  
  const date = parseTaskDate(dateStr)
  const today = new Date()
  const tomorrow = new Date(today)
  tomorrow.setDate(tomorrow.getDate() + 1)
  
  // 格式化日期为 YYYY-MM-DD
  const formattedDate = toTaskDateOnly(date)
  const todayStr = toTaskDateOnly(today)
  const tomorrowStr = toTaskDateOnly(tomorrow)
  
  if (formattedDate === todayStr) {
    return t('todoList.today')
  } else if (formattedDate === tomorrowStr) {
    return t('todoList.tomorrow')
  } else {
    // 根据当前语言显示不同格式
    const currentLocale = locale.value;
    if (currentLocale === 'zh-CN') {
      // 中文格式: "1月30日"
      const month = date.getMonth() + 1
      const day = date.getDate()
      return t('todoList.monthsLater', [month, day])
    } else {
      // 英文格式: "Jan 30"
      const monthNames = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun',
                         'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']
      const month = monthNames[date.getMonth()]
      const day = date.getDate()
      return `${month} ${day}`
    }
  }
}

// 处理日期分组切换
const handleDateGroupToggle = (value) => {
  console.log('日期分组切换:', value)
  // 这里可以添加其他逻辑，如果需要的话
}

// 处理键盘事件
const handleKeydown = (event) => {
  // 只在批量删除模式下处理键盘事件
  if (!batchMode.value) return
  
  // ESC键退出批量删除模式
  if (event.key === 'Escape') {
    cancelBatchMode()
  }
  // Enter键触发确认删除对话框
  else if (event.key === 'Enter' && event.target === document.body && selectedTodoIds.value.length > 0) {
    event.preventDefault() // 防止表单默认提交行为
    event.stopPropagation() // 阻止事件冒泡
    
    // 检查是否已经有确认对话框打开
    const hasMessageBox = document.querySelector('.el-message-box')
    if (!hasMessageBox) {
      confirmBatchDelete()
    }
  }
}

// 处理批量模式切换
const toggleBatchMode = () => {
  batchMode.value = !batchMode.value
  if (!batchMode.value) {
    // 退出批量模式时清空选中项
    selectedTodoIds.value = []
    // 移除键盘事件监听
    document.removeEventListener('keydown', handleKeydown)
  } else {
    // 进入批量模式时添加键盘事件监听
    document.addEventListener('keydown', handleKeydown)
  }
}

// 处理待办事项点击事件
const handleTodoClick = (todo) => {
  // 如果处于批量模式，则进入选择/取消选择逻辑
  if (batchMode.value) {
    handleTodoSelect(!selectedTodoIds.value.includes(todo.taskId), todo.taskId)
  } else {
    // 否则进入详情页面
    goToDetail(todo.taskId)
  }
}

const handleTodoKeydown = (event, todo) => {
  // 子控件保留自己的键盘行为，行操作不触发全局批量删除快捷键。
  if (event.target !== event.currentTarget) return
  if (event.key === 'Enter' || event.key === ' ') {
    event.preventDefault()
    event.stopPropagation()
    handleTodoClick(todo)
  }
}

// 处理待办事项选择事件
const handleTodoSelect = (selected, taskId) => {
  if (selected) {
    // 添加到选中列表
    if (!selectedTodoIds.value.includes(taskId)) {
      selectedTodoIds.value.push(taskId)
    }
  } else {
    // 从选中列表中移除
    const index = selectedTodoIds.value.indexOf(taskId)
    if (index > -1) {
      selectedTodoIds.value.splice(index, 1)
    }
  }
}

// 取消批量模式
const cancelBatchMode = () => {
  batchMode.value = false
  selectedTodoIds.value = []
  // 移除键盘事件监听
  document.removeEventListener('keydown', handleKeydown)
}

// 分页由列表自身管理；筛选只提供查询条件，避免旧页码覆盖当前页。
let requestSequence = 0
let listActive = false
const firstListLoad = ref(true)

onActivated(() => {
  listActive = true
  if (batchMode.value) document.addEventListener('keydown', handleKeydown)
  fetchTodos()
})
onDeactivated(() => {
  listActive = false
  requestSequence++
  loading.value = false
  document.removeEventListener('keydown', handleKeydown)
})

const fetchTodos = async () => {
  const sequence = ++requestSequence
  if (firstListLoad.value) loading.value = true
  const { page: ignoredPage, pageSize: ignoredSize, ...filters } = queryParams.value
  try {
    const response = await request.get('/task/pageQuery', {
      ...filters, page: currentPage.value, pageSize: pageSize.value
    })
    if (sequence !== requestSequence || !listActive) return
    if (response.code === 1) {
      todos.value = response.data.records.map(todo => ({ ...todo, favourite: !!todo.favourite }))
      total.value = response.data.total
      if (currentPage.value > 1 && !todos.value.length && total.value > 0) {
        currentPage.value = Math.ceil(total.value / pageSize.value)
        return fetchTodos()
      }
      firstListLoad.value = false
    } else {
      ElMessage.error(response.msg || t('todoList.fetchFailed'))
    }
  } catch {
    if (sequence === requestSequence && listActive) ElMessage.error(t('todoList.fetchFailed'))
  } finally {
    if (sequence === requestSequence) loading.value = false
  }
}

// 弹窗文案：优先带用户名，未取到用户名时退回简短文案；标题留空
const confirmText = (namedKey, plainKey, args) => {
  const name = (localStorage.getItem('userName') || '').trim()
  return name ? t(namedKey, [name, ...args]) : t(plainKey, args)
}
const messageBoxOptions = extra => ({
  confirmButtonText: t('todoList.confirmAction'),
  cancelButtonText: t('todoList.cancel'),
  title: '',
  ...extra
})

// 更新待办状态
const updateTodoStatus = async (todo, newStatus) => {
  const key = todo.taskId
  if (pendingTaskActions.has(key)) return
  pendingTaskActions.add(key)
  try {
    // 如果待办事项已经完成，则不允许再标记为未完成
    if (todo.status === true) {
      // 已完成的待办事项不能再改为未完成
      ElMessage.warning(t('todoList.cannotUncomplete'))
      return
    }

    // 待办事项从未完成标记为完成的情况
    await ElMessageBox.confirm(
      confirmText('todoList.completeConfirmNamed', 'todoList.completeConfirmShort', [todo.taskName]),
      '',
      messageBoxOptions()
    );
    
    // 用户确认后执行状态更新，只发送状态字段
    const response = await request.patch(`/task/${todo.taskId}`, {
      status: newStatus
    })
    
    if (response.code === 1) {
      // 更新本地状态
      todo.status = newStatus
      if (newStatus) playCompletedFeedback(todo.taskId)
      window.dispatchEvent(new CustomEvent('favouriteChanged'))
      ElMessage.success(t('todoList.taskCompleted'))
    } else {
      // 恢复状态
      ElMessage.error(response.msg || t('todoList.statusUpdateFailed'))
    }
  } catch (error) {
    // 用户取消操作或请求失败，恢复状态
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(t('todoList.statusUpdateFailed'))
    }
  } finally {
    releaseTaskAction(key)
  }
}

// 计算倒计时文本
const getCountdownText = (todo) => {
  // 如果待办事项已完成，直接显示"已完成"
  if (todo.status) {
    return t('todoList.completed')
  }
  
  if (!todo.dueTime) return t('todoList.noDueTime')
  
  const now = new Date()
  const dueDate = new Date(todo.dueTime)
  const today = Date.UTC(now.getFullYear(), now.getMonth(), now.getDate())
  const dueDay = Date.UTC(dueDate.getFullYear(), dueDate.getMonth(), dueDate.getDate())
  const days = Math.round((dueDay - today) / 86400000)
  if (dueDate < now) return t('todoList.daysOverdue', [Math.max(1, -days)])
  if (days === 0) return t('todoList.dueToday')
  if (days === 1) return t('todoList.dueTomorrow')
  return t('todoList.daysUntilDue', [days])
}

// 获取倒计时标签类型
const getCountdownTagType = (todo) => {
  // 如果待办事项已完成，显示绿色标签
  if (todo.status) {
    return 'success'
  }
  
  if (!todo.dueTime) return 'info' // 返回'info'类型而不是空字符串
  
  const now = new Date()
  const dueDate = new Date(todo.dueTime)
  const diffTime = dueDate - now
  
  if (diffTime < 0) {
    return 'danger' // 已过期，红色
  } else if (diffTime < 1000 * 60 * 60 * 24) {
    return 'warning' // 一天内到期，橙色
  } else {
    return 'success' // 正常，绿色
  }
}

// 进入新的分页时回到内容区顶部；使用瞬时滚动，避免平滑动画。
const scrollToListTop = () => {
  const container = document.querySelector('.content-main')
  if (container) container.scrollTo({ top: 0, behavior: 'auto' })
}

// 分页相关方法
const handleSizeChange = val => {
  pageSize.value = val
  currentPage.value = 1
  selectedTodoIds.value = []
  scrollToListTop()
  fetchTodos()
}

const handleCurrentChange = val => {
  currentPage.value = val
  selectedTodoIds.value = []
  scrollToListTop()
  fetchTodos()
}

// 跳转到详情页
const goToDetail = (taskId) => {
  router.push(`/home/todo/${taskId}`)
}

// 确认删除
const confirmDelete = (todo) => {
  ElMessageBox.confirm(
    confirmText('todoList.deleteConfirmNamed', 'todoList.deleteConfirmShort', [todo.taskName]),
    '',
    messageBoxOptions({ confirmButtonClass: 'el-button--danger' })
  ).then(() => {
    deleteTodo(todo)
  }).catch(() => {
    // 用户取消删除
  })
}

// 确认批量删除
const confirmBatchDelete = () => {
  if (selectedTodoIds.value.length === 0) {
    ElMessage.warning(t('todoList.selectTodosFirst'))
    return
  }

  ElMessageBox.confirm(
    confirmText('todoList.batchDeleteConfirmNamed', 'todoList.batchDeleteConfirmShort', [selectedTodoIds.value.length]),
    '',
    messageBoxOptions({ confirmButtonClass: 'el-button--danger' })
  ).then(() => {
    batchDeleteTodos()
  }).catch(() => {
    // 用户取消删除
  })
}

// 删除待办项
const deleteTodo = async (todo) => {
  try {
    const response = await request.delete(`/task/${todo.taskId}`)
    
    if (response.code === 1) {
      ElMessage.success(t('todoList.deleteSuccess'))
      // 从列表中移除已删除的待办项
      const index = todos.value.findIndex(item => item.taskId === todo.taskId)
      if (index > -1) {
        todos.value.splice(index, 1)
      }
      // 更新总数
      total.value -= 1
      window.dispatchEvent(new CustomEvent('favouriteChanged'))
      await fetchTodos()
    } else {
      ElMessage.error(response.msg || t('todoList.deleteFailed'))
    }
  } catch (error) {
    ElMessage.error(t('todoList.deleteFailed'))
  }
}

// 批量删除待办项
const batchDeleteTodos = async () => {
  if (selectedTodoIds.value.length === 0) {
    ElMessage.warning(t('todoList.selectTodosFirst'))
    return
  }

  try {
    const response = await request.delete('/task/batch/delete', {}, {
      data: selectedTodoIds.value
    })

    if (response.code === 1) {
      ElMessage.success(t('todoList.batchDeleteSuccess'))
      // 清空选中项
      selectedTodoIds.value = []
      // 退出批量模式
      batchMode.value = false
      // 移除键盘事件监听
      document.removeEventListener('keydown', handleKeydown)
      // 重新获取数据
      fetchTodos()
    } else {
      ElMessage.error(response.msg || t('todoList.batchDeleteFailed'))
    }
  } catch (error) {
    ElMessage.error(t('todoList.batchDeleteFailed'))
  }
}

// 同一任务的交互在请求期间只允许一次，避免快速连点刷屏
const pendingTaskActions = new Set()
// 完成动效由响应式状态驱动，避免手动增删 class 被 Vue 重渲染覆盖
const justCompletedId = ref(null)
let completedFeedbackTimer = null
const playCompletedFeedback = taskId => {
  justCompletedId.value = null
  window.clearTimeout(completedFeedbackTimer)
  window.requestAnimationFrame(() => { justCompletedId.value = taskId })
  completedFeedbackTimer = window.setTimeout(() => { justCompletedId.value = null }, 700)
}
const releaseTaskAction = key => { window.setTimeout(() => pendingTaskActions.delete(key), 300) }
// 星标动画由响应式 class 控制，避免状态更新重写 class 打断动画
const poppingTaskId = ref(null)

// 切换收藏状态
const toggleFavourite = async (todo) => {
  const key = todo.taskId
  if (pendingTaskActions.has(key)) return
  pendingTaskActions.add(key)
  try {
    poppingTaskId.value = todo.taskId
    window.setTimeout(() => {
      if (poppingTaskId.value === todo.taskId) poppingTaskId.value = null
    }, 450)
    const newFavouriteStatus = !todo.favourite;
    
    const response = await request.patch(`/task/${todo.taskId}`, { favourite: newFavouriteStatus });

    if (response.code === 1) {
      // 更新本地状态
      todo.favourite = newFavouriteStatus;
      
      // 触发自定义事件，通知侧边栏更新收藏列表
      window.dispatchEvent(new CustomEvent('favouriteChanged', {
        detail: { taskId: todo.taskId, favourite: newFavouriteStatus }
      }));
      
      // 根据操作类型显示不同的提示信息
      if (newFavouriteStatus) {
        ElMessage.success(t('todoList.addedToFavorites'));
      } else {
        ElMessage.success(t('todoList.removedFromFavorites'));
      }
    } else {
      ElMessage.error(response.msg || t('todoList.favoriteOperationFailed'));
    }
  } catch (error) {
    ElMessage.error(t('todoList.favoriteOperationFailed') + ': ' + error.message);
  } finally {
    releaseTaskAction(key)
  }
};

// 更新单个待办事项的收藏状态（供外部调用）
const updateTodoFavouriteStatus = (taskId, favourite) => {
  const todo = todos.value.find(item => item.taskId === taskId);
  if (todo) {
    todo.favourite = favourite;
  }
};

// 暴露方法给父组件
defineExpose({
  fetchTodos
})

// 组件卸载时清理事件监听
onUnmounted(() => {
  document.removeEventListener('keydown', handleKeydown)
})

</script>

<style scoped>
.todo-list-container {
  width: 100%;
  min-width: 0;
}

.todo-list-card {
  background: #fff;
  box-shadow: none;
  border-color: var(--app-border, #e7ebf0);
}

.card-header,
.header-right,
.batch-action-bar,
.batch-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}

/* 筛选条：从侧边栏移入待办列表面板 */
.filter-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px 24px;
  padding: 0 0 16px;
  margin-bottom: 16px;
  border-bottom: 1px solid var(--app-border, #e7ebf0);
}

.filter-group {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.filter-label {
  color: var(--app-muted, #697586);
  font-size: 13px;
  white-space: nowrap;
}

.filter-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.filter-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 32px;
  padding: 0 12px;
  border: 1px solid var(--app-border, #e7ebf0);
  border-radius: 8px;
  background: transparent;
  color: var(--app-muted, #697586);
  font: inherit;
  font-size: 13px;
  cursor: pointer;
  transition: background-color .12s ease, border-color .12s ease, color .12s ease;
}

.filter-chip:hover {
  color: var(--el-color-primary);
  border-color: var(--el-color-primary-light-5);
}

.filter-chip.active {
  background: var(--el-color-primary-light-9);
  border-color: transparent;
  color: var(--el-color-primary);
  font-weight: 600;
}

.chip-arrow {
  font-size: 13px;
  pointer-events: none;
}

@media (max-width: 760px) {
  .filter-bar {
    gap: 10px;
    padding-bottom: 12px;
    margin-bottom: 12px;
  }

  .filter-group {
    flex-wrap: wrap;
    gap: 6px;
  }

  .filter-chip {
    min-height: 36px;
  }
}

.card-header,
.batch-action-bar {
  justify-content: space-between;
}

.header-left,
.header-right {
  min-width: 0;
}

.list-title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  line-height: 1.4;
  overflow-wrap: anywhere;
}

.task-count,
.date-title,
.batch-info {
  color: var(--app-muted, #697586);
}

.task-count {
  font-size: 14px;
  font-weight: 400;
}

.header-right :deep(.el-button),
.batch-actions :deep(.el-button) {
  margin-left: 0;
}

.date-group + .date-group {
  margin-top: 24px;
}

.date-header {
  padding: 10px 0;
  border-bottom: 1px solid var(--app-border, #e7ebf0);
}

.date-title {
  font-size: 14px;
  font-weight: 500;
}

.todo-item {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  min-width: 0;
  padding: 14px 0;
  border-bottom: 1px solid var(--app-border, #e7ebf0);
  cursor: pointer;
}

.todo-item:hover {
  background: #f8fafc;
}

.todo-item:focus-visible,
.icon-button:focus-visible {
  outline: 2px solid var(--el-color-primary);
  outline-offset: 2px;
}

.todo-item.selected {
  background: var(--el-color-primary-light-9);
}

.todo-info {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.todo-checkbox {
  margin-right: 0;
}

/* 列表勾选框的完成反馈：绿色弹跳 + 扩散光环，与详情页一致 */
.todo-checkbox :deep(.el-checkbox__inner) {
  transition: background-color .15s ease, border-color .15s ease;
}

.todo-checkbox.just-completed :deep(.el-checkbox__inner) {
  background: var(--el-color-success);
  border-color: var(--el-color-success);
  animation: list-box-pop .45s cubic-bezier(.2, .9, .25, 1.25);
}

/* 已完成状态长期保持绿色，不随完成动画结束回落到默认蓝色 */
.todo-checkbox.is-checked :deep(.el-checkbox__inner) {
  background-color: var(--el-color-success);
  border-color: var(--el-color-success);
}

.todo-checkbox.is-checked :deep(.el-checkbox__inner::after) {
  border-color: #fff;
}

.todo-checkbox.is-checked :deep(.el-checkbox__label) {
  color: var(--el-text-color-regular);
}

.todo-checkbox.just-completed :deep(.el-checkbox__inner::before) {
  content: '';
  position: absolute;
  inset: -2px;
  border: 2px solid rgba(103, 194, 58, .55);
  border-radius: 5px;
  animation: list-box-ring .5s ease-out forwards;
  pointer-events: none;
}

@keyframes list-box-pop {
  0% { transform: scale(.8); }
  45% { transform: scale(1.35); }
  70% { transform: scale(.97); }
  100% { transform: scale(1); }
}

@keyframes list-box-ring {
  0% { opacity: .6; transform: scale(.92); }
  100% { opacity: 0; transform: scale(1.9); }
}

.selection-indicator {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  box-sizing: border-box;
  border: 1px solid var(--app-muted, #697586);
  border-radius: 3px;
}

.selected .selection-indicator {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary);
  color: #fff;
}

.todo-name {
  min-width: 0;
  font-size: 15px;
  line-height: 1.6;
  overflow-wrap: anywhere;
  white-space: normal;
}

.completed-text {
  text-decoration: line-through;
  color: var(--app-muted, #697586);
}

.todo-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  min-width: 0;
}

.icon-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  width: 36px;
  height: 36px;
  padding: 0;
  border: 0;
  border-radius: 4px;
  background: transparent;
  color: var(--app-muted, #697586);
  font: inherit;
  cursor: pointer;
}

.icon-button:hover {
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}
/* 收藏星标固定为黄色，悬停不再变蓝 */
.favourite-icon.favourited,
.favourite-icon.favourited:hover {
  color: var(--app-star, #f0b429);
  background: transparent;
}
.delete-icon:hover { color: var(--el-color-danger); background: var(--el-color-danger-light-9); }
.task-count { font-variant-numeric: tabular-nums; }

.favourite-icon.pop {
  animation: star-pop 0.32s ease;
}

/* 只做缩放，不加发光/描边，避免出现类似边框的光晕 */
@keyframes star-pop {
  0% { transform: scale(1); }
  45% { transform: scale(1.28); }
  100% { transform: scale(1); }
}

.countdown-tag {
  height: auto;
  max-width: 100%;
  padding: 4px 8px;
  line-height: 1.4;
  white-space: normal;
  overflow-wrap: anywhere;
}

.pagination-container {
  display: flex;
  justify-content: center;
  flex-wrap: wrap;
  gap: 16px;
  padding-top: 24px;
}

.loading-container,
.empty-container {
  padding: 24px 0;
  min-height: 240px;
}

.batch-action-bar {
  margin-top: 16px;
  padding: 16px 0;
  border-top: 1px solid var(--app-border, #e7ebf0);
}

@media (max-width: 760px) {
  .header-right {
    width: 100%;
    justify-content: space-between;
  }

  .todo-item {
    grid-template-columns: minmax(0, 1fr);
    gap: 4px;
  }

  .todo-meta {
    padding-left: 30px;
  }

  .icon-button {
    width: 44px;
    height: 44px;
  }

  .todo-checkbox {
    min-height: 44px;
  }

}

@media (max-width: 1100px) {
  .pagination-container {
    flex-direction: column;
    align-items: stretch;
  }

  .pagination-container :deep(.el-pagination) {
    display: grid;
    grid-template-columns: 44px minmax(0, 1fr) 44px;
    gap: 4px;
    width: 100%;
    white-space: normal;
  }

  .pagination-container :deep(.el-pager) {
    display: flex;
    flex-wrap: wrap;
    justify-content: center;
    min-width: 0;
  }

  .pagination-container :deep(.el-pager li),
  .pagination-container :deep(.btn-prev),
  .pagination-container :deep(.btn-next) {
    min-width: 44px;
    height: 44px;
    margin: 0;
  }

  .mobile-page-size {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    justify-content: center;
    gap: 8px;
    color: var(--app-muted, #697586);
  }

  .mobile-page-size :deep(.el-select) {
    width: 88px;
  }

  .mobile-page-size :deep(.el-select__wrapper) {
    min-height: 44px;
  }
}
</style>