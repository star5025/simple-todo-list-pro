<template>
  <div class="todo-detail-container">
    <transition name="el-fade-in-linear" mode="out-in">
      <el-card class="todo-detail-card" v-if="!loading && todo">
        <template #header>
          <div class="card-header">
            <el-button @click="goBack" type="primary" link>
              <el-icon><ArrowLeft /></el-icon>
              {{ t('todoDetail.back') }}
            </el-button>
            <div class="header-title">{{ isEditing ? t('todoDetail.editTodo') : t('todoDetail.todoDetails') }}</div>
            <div class="header-actions">
              <el-button 
                v-if="!isEditing" 
                @click="startEdit" 
                type="primary" 
                link
              >
                {{ t('todoDetail.edit') }}
              </el-button>
              <el-button 
                v-else 
                @click="saveEdit" 
                type="primary" 
                link
              >
                {{ t('todoDetail.save') }}
              </el-button>
            </div>
          </div>
        </template>

        <div class="todo-detail-content">
          <div class="todo-basic-info">
            <div class="todo-status">
              <el-tag :type="todo.status ? 'success' : 'info'">
                {{ todo.status ? t('todoDetail.completed') : t('todoDetail.incomplete') }}
              </el-tag>
            </div>
            
            <h2 v-if="!isEditing" class="todo-title">{{ todo.taskName }}</h2>
            <el-input 
              v-else
              v-model="editForm.taskName" 
              class="todo-title-input"
              :placeholder="t('todoDetail.enterTaskName')"
            />
            
            <div v-if="todo.description || isEditing" class="todo-description">
              <h3>{{ t('todoDetail.description') }}</h3>
              <p v-if="!isEditing">{{ todo.description }}</p>
              <el-input 
                v-else
                v-model="editForm.description" 
                type="textarea" 
                :rows="3"
                :placeholder="t('todoDetail.enterDescription')"
              />
            </div>
          </div>
          
          <el-divider />
          
          <div class="todo-time-info">
            <div class="time-item" v-if="todo.createdTime">
              <div class="time-label">{{ t('todoDetail.createdTime') }}</div>
              <div class="time-value">{{ formatTime(todo.createdTime) }}</div>
            </div>
            
            <div class="time-item">
              <div class="time-label">{{ t('todoDetail.startTime') }}</div>
              <div v-if="!isEditing" class="time-value">{{ todo.startTime ? formatTime(todo.startTime) : t('todoDetail.notSet') }}</div>
              <el-date-picker 
                v-else
                v-model="editForm.startTime"
                :aria-label="t('todoDetail.startTime')"
                type="datetime"
                :placeholder="t('todoDetail.selectStartTime')"
                format="YYYY-MM-DD HH:mm"
                value-format="YYYY-MM-DD HH:mm"
                popper-class="todo-date-popper"
              />
            </div>
            
            <div class="time-item">
              <div class="time-label">{{ t('todoDetail.dueTime') }}</div>
              <div v-if="!isEditing" class="time-value">{{ todo.dueTime ? formatTime(todo.dueTime) : t('todoDetail.notSet') }}</div>
              <div v-if="!isEditing && todo.dueTime" class="time-remaining">{{ getCountdownText(todo) }}</div>
              <el-date-picker
                v-if="isEditing"
                v-model="editForm.dueTime"
                :aria-label="t('todoDetail.dueTime')"
                type="datetime"
                :placeholder="t('todoDetail.selectDueTime')"
                format="YYYY-MM-DD HH:mm"
                value-format="YYYY-MM-DD HH:mm"
                popper-class="todo-date-popper"
              />
            </div>
            
            <div class="time-item">
              <div class="time-label">{{ t('todoDetail.remindTime') }}</div>
              <div v-if="!isEditing" class="time-value">{{ todo.remindTime ? formatTime(todo.remindTime) : t('todoDetail.notSet') }}</div>
              <el-date-picker 
                v-else
                v-model="editForm.remindTime"
                :aria-label="t('todoDetail.remindTime')"
                type="datetime"
                :placeholder="t('todoDetail.selectRemindTime')"
                format="YYYY-MM-DD HH:mm"
                value-format="YYYY-MM-DD HH:mm"
                popper-class="todo-date-popper"
              />
            </div>
          </div>

          <el-divider />
          <section class="repeat-settings" :aria-label="t('todoDetail.repeatRule')">
            <label for="repeat-rule">{{ t('todoDetail.repeatRule') }}</label>
            <div class="repeat-controls">
              <el-select
                id="repeat-rule"
                v-model="repeatRule"
                :aria-label="t('todoDetail.repeatRule')"
                :disabled="scheduleLoading || scheduleSaving || !scheduleLoaded"
                :loading="scheduleLoading"
              >
                <el-option v-for="rule in repeatRules" :key="rule" :value="rule" :label="t(`todoDetail.repeat${rule}`)" />
              </el-select>
              <el-button
                type="primary"
                :loading="scheduleSaving"
                :disabled="!scheduleLoaded || scheduleLoading || repeatRule === savedRepeatRule"
                @click="saveSchedule"
              >{{ t('todoDetail.saveRepeatRule') }}</el-button>
              <el-button
                :disabled="!scheduleLoaded || scheduleLoading || scheduleSaving || repeatRule === savedRepeatRule"
                @click="repeatRule = savedRepeatRule"
              >{{ t('todoDetail.cancelRepeatRule') }}</el-button>
              <el-button v-if="scheduleError" :loading="scheduleLoading" @click="fetchSchedule">
                {{ t('todoDetail.retrySchedule') }}
              </el-button>
            </div>
            <p v-if="scheduleError" class="schedule-error" role="alert">{{ scheduleError }}</p>
            <details class="repeat-guide">
              <summary>{{ t('todoDetail.repeatRule') }}</summary>
              <p class="repeat-help">{{ t('todoDetail.repeatHelp') }}</p>
              <p class="repeat-help">{{ t('todoDetail.reminderHelp') }}</p>
            </details>
          </section>
        </div>
      </el-card>
      
      <el-card class="todo-detail-card" v-else-if="loading">
        <template #header>
          <div class="card-header">
            <el-button @click="goBack" type="primary" link>
              <el-icon><ArrowLeft /></el-icon>
              {{ t('todoDetail.back') }}
            </el-button>
            <div class="header-title">{{ t('todoDetail.loading') }}</div>
          </div>
        </template>
        
        <div class="loading-container">
          <el-icon class="is-loading" :size="30" aria-hidden="true"><Loading /></el-icon>
        </div>
      </el-card>
      
      <el-card class="todo-detail-card" v-else>
        <template #header>
          <div class="card-header">
            <el-button @click="goBack" type="primary" link>
              <el-icon><ArrowLeft /></el-icon>
              {{ t('todoDetail.back') }}
            </el-button>
            <div class="header-title">{{ t('todoDetail.todoDetails') }}</div>
          </div>
        </template>
        
        <div class="empty-container">
          <el-empty :description="t('todoDetail.notFound')" />
        </div>
      </el-card>
    </transition>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import { ArrowLeft, Loading } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()

const todo = ref(null)
// 初始即为加载中：进入详情先显示骨架，取到数据才渲染，避免闪现"未找到"
const loading = ref(true)
const isEditing = ref(false)
const repeatRules = ['NONE', 'DAILY', 'WEEKLY', 'MONTHLY']
const repeatRule = ref('NONE')
const savedRepeatRule = ref('NONE')
const scheduleLoaded = ref(false)
const scheduleLoading = ref(false)
const scheduleSaving = ref(false)
const scheduleError = ref('')
const taskId = route.params.id
let disposed = false

const fetchSchedule = async () => {
  if (!taskId || disposed || scheduleLoading.value || scheduleSaving.value) return
  scheduleLoading.value = true
  scheduleLoaded.value = false
  scheduleError.value = ''
  try {
    const response = await request.get(`/task/${taskId}/schedule`)
    if (disposed) return
    if (response.code === 1) {
      repeatRule.value = response.data.repeatRule
      savedRepeatRule.value = response.data.repeatRule
      scheduleLoaded.value = true
    } else {
      scheduleError.value = response.msg || t('todoDetail.scheduleFetchFailed')
      ElMessage.error(scheduleError.value)
    }
  } catch (error) {
    if (disposed) return
    scheduleError.value = t('todoDetail.scheduleFetchFailed')
    ElMessage.error(scheduleError.value)
  } finally {
    if (!disposed) scheduleLoading.value = false
  }
}

const saveSchedule = async () => {
  if (disposed || !scheduleLoaded.value || scheduleLoading.value || scheduleSaving.value) return
  const rule = repeatRule.value
  if (rule === savedRepeatRule.value) return
  scheduleSaving.value = true
  scheduleError.value = ''
  try {
    const response = await request.put(`/task/${taskId}/schedule`, { repeatRule: rule })
    if (disposed) return
    if (response.code === 1) {
      savedRepeatRule.value = rule
      ElMessage.success(t('todoDetail.scheduleSaveSuccess'))
    } else {
      scheduleError.value = response.msg || t('todoDetail.scheduleSaveFailed')
      ElMessage.error(scheduleError.value)
    }
  } catch (error) {
    if (disposed) return
    scheduleError.value = t('todoDetail.scheduleSaveFailed')
    ElMessage.error(scheduleError.value)
  } finally {
    if (!disposed) scheduleSaving.value = false
  }
}

// 编辑表单
const editForm = reactive({
  taskName: '',
  description: '',
  status: false,
  startTime: '',
  dueTime: '',
  remindTime: ''
})

// 获取待办详情
const fetchTodoDetail = async () => {
  const taskId = route.params.id
  if (!taskId) {
    ElMessage.error(t('todoDetail.invalidParams'))
    return
  }
  
  loading.value = true
  try {
    const response = await request.get(`/task/${taskId}`)
    if (disposed) return
    if (response.code === 1) {
      todo.value = response.data
      // 初始化编辑表单
      initEditForm()
    } else {
      ElMessage.error(response.msg || t('todoDetail.fetchFailed'))
    }
  } catch (error) {
    if (!disposed) ElMessage.error(t('userInfo.operationFailed') || t('todoDetail.fetchFailed'))
  } finally {
    if (!disposed) loading.value = false
  }
}

// 初始化编辑表单
const initEditForm = () => {
  if (todo.value) {
    editForm.taskName = todo.value.taskName
    editForm.description = todo.value.description || ''
    editForm.status = todo.value.status
    editForm.startTime = todo.value.startTime || ''
    editForm.dueTime = todo.value.dueTime || ''
    editForm.remindTime = todo.value.remindTime || ''
  }
}

// 格式化时间
const formatTime = (time) => {
  if (!time) return ''
  return new Date(time).toLocaleString('zh-CN')
}

// 计算倒计时文本
const getCountdownText = (todo) => {
  if (!todo.dueTime) return ''
  
  const now = new Date()
  const dueDate = new Date(todo.dueTime)
  const diffTime = dueDate - now
  const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24))
  
  if (diffTime < 0) {
    return t('todoDetail.daysOverdue', [Math.abs(diffDays)])
  } else if (diffDays === 0) {
    return t('todoDetail.dueToday')
  } else if (diffDays === 1) {
    return t('todoDetail.dueTomorrow')
  } else {
    return t('todoDetail.daysUntilDue', [diffDays])
  }
}

// 返回上一页
const goBack = () => {
  router.push('/home/list')
}

// 开始编辑
const startEdit = () => {
  isEditing.value = true
  initEditForm()
}

// 保存编辑
const saveEdit = async () => {
  const taskId = route.params.id
  if (!taskId) {
    ElMessage.error(t('todoDetail.invalidParams'))
    return
  }
  
  // 时间校验逻辑
  if (editForm.startTime && editForm.dueTime && new Date(editForm.startTime) > new Date(editForm.dueTime)) {
    ElMessage.error(t('todoDetail.startTimeError'))
    return
  }
  
  if (editForm.remindTime && editForm.dueTime && new Date(editForm.remindTime) > new Date(editForm.dueTime)) {
    ElMessage.error(t('todoDetail.remindTimeError'))
    return
  }
  
  try {
    // 构造完整的请求数据对象，而不是只发送变化的字段
    const requestData = {
      taskId: taskId,
      taskName: editForm.taskName,
      description: editForm.description,
      status: editForm.status,
      startTime: editForm.startTime ? new Date(editForm.startTime).toISOString() : null,
      dueTime: editForm.dueTime ? new Date(editForm.dueTime).toISOString() : null,
      remindTime: editForm.remindTime ? new Date(editForm.remindTime).toISOString() : null
    };
    
    // 发送完整数据对象
    const response = await request.patch(`/task/${taskId}`, requestData)
    if (disposed) return
    
    if (response.code === 1) {
      window.dispatchEvent(new CustomEvent('favouriteChanged'))
      ElMessage.success(t('todoDetail.updateSuccess'))
      isEditing.value = false
      // 重新获取待办详情
      fetchTodoDetail()
    } else {
      ElMessage.error(response.msg || t('todoDetail.updateFailed'))
    }
  } catch (error) {
    if (!disposed) ElMessage.error(t('userInfo.operationFailed') || t('todoDetail.updateFailed'))
  }
}

// 组件挂载时获取数据
onMounted(() => {
  fetchTodoDetail()
  fetchSchedule()
})
onBeforeUnmount(() => {
  disposed = true
})
</script>

<style scoped>
.todo-detail-container {
  width: 100%;
  min-width: 0;
  color: #243047;
}

.todo-detail-card {
  border: 1px solid var(--app-border, #e7ebf0);
  border-radius: 12px;
  background: var(--app-surface, #fff);
  box-shadow: none;
}

.card-header {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 16px;
}

.header-title {
  min-width: 0;
  flex: 1;
  font-size: 20px;
  font-weight: 600;
  overflow-wrap: anywhere;
}

.todo-status {
  margin-bottom: 16px;
}

.todo-title {
  margin: 0 0 20px;
  font-size: clamp(22px, 4vw, 28px);
  font-weight: 600;
  line-height: 1.4;
  overflow-wrap: anywhere;
  word-break: break-word;
}

.todo-title-input {
  margin-bottom: 20px;
}

.todo-description h3 {
  margin: 20px 0 8px;
  font-size: 16px;
  font-weight: 600;
}

.todo-description p,
.repeat-help {
  color: var(--app-muted, #697586);
  line-height: 1.7;
  overflow-wrap: anywhere;
  word-break: break-word;
}

.todo-description p {
  white-space: pre-wrap;
}

.time-item {
  display: grid;
  grid-template-columns: 112px minmax(0, 1fr);
  align-items: center;
  gap: 8px 16px;
  margin-bottom: 16px;
}

.time-label {
  color: var(--app-muted, #697586);
}

.time-value {
  min-width: 0;
  overflow-wrap: anywhere;
}

.time-remaining {
  grid-column: 2;
  color: var(--app-muted, #697586);
  font-size: 14px;
}

.time-item :deep(.el-date-editor) {
  width: 100%;
  min-width: 0;
}

.repeat-settings label {
  display: block;
  margin-bottom: 12px;
  font-weight: 600;
}

.repeat-controls {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
}

.repeat-controls .el-select {
  width: 180px;
  max-width: 100%;
}

.repeat-controls .el-button + .el-button {
  margin-left: 0;
}

.repeat-guide {
  margin-top: 16px;
}

.repeat-guide summary {
  display: list-item;
  min-height: 44px;
  line-height: 44px;
  cursor: pointer;
  color: var(--app-muted, #697586);
}

.repeat-help {
  margin: 8px 0;
  font-size: 14px;
}

.schedule-error {
  color: var(--el-color-danger);
  overflow-wrap: anywhere;
}

.todo-detail-card :deep(.el-button),
.todo-detail-card :deep(.el-input__wrapper),
.todo-detail-card :deep(.el-select__wrapper) {
  min-height: 44px;
  box-sizing: border-box;
}

.loading-container,
.empty-container {
  min-height: 240px;
}

.loading-container {
  display: flex;
  justify-content: center;
  align-items: center;
  color: var(--el-color-primary);
}

@media (max-width: 600px) {
  .time-item {
    grid-template-columns: minmax(0, 1fr);
    gap: 6px;
  }

  .time-remaining {
    grid-column: 1;
  }

  .header-title {
    flex-basis: 100%;
    order: 1;
  }

  .header-actions {
    margin-left: auto;
  }
}
</style>