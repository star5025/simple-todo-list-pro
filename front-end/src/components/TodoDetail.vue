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
                type="datetime"
                :placeholder="t('todoDetail.selectStartTime')"
                format="YYYY-MM-DD HH:mm"
                value-format="YYYY-MM-DD HH:mm"
                style="margin-left: 20px; flex: 1;"
              />
            </div>
            
            <div class="time-item">
              <div class="time-label">{{ t('todoDetail.dueTime') }}</div>
              <div v-if="!isEditing" class="time-value">{{ todo.dueTime ? formatTime(todo.dueTime) : t('todoDetail.notSet') }}</div>
              <div v-if="!isEditing && todo.dueTime" class="time-remaining">{{ getCountdownText(todo) }}</div>
              <el-date-picker 
                v-else
                v-model="editForm.dueTime"
                type="datetime"
                :placeholder="t('todoDetail.selectDueTime')"
                format="YYYY-MM-DD HH:mm"
                value-format="YYYY-MM-DD HH:mm"
                style="margin-left: 20px; flex: 1;"
              />
            </div>
            
            <div class="time-item">
              <div class="time-label">{{ t('todoDetail.remindTime') }}</div>
              <div v-if="!isEditing" class="time-value">{{ todo.remindTime ? formatTime(todo.remindTime) : t('todoDetail.notSet') }}</div>
              <el-date-picker 
                v-else
                v-model="editForm.remindTime"
                type="datetime"
                :placeholder="t('todoDetail.selectRemindTime')"
                format="YYYY-MM-DD HH:mm"
                value-format="YYYY-MM-DD HH:mm"
                style="margin-left: 20px; flex: 1;"
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
            <p class="repeat-help">{{ t('todoDetail.repeatHelp') }}</p>
            <p class="repeat-help">{{ t('todoDetail.reminderHelp') }}</p>
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
          <el-skeleton animated>
            <template #template>
              <el-skeleton-item variant="h3" style="width: 40%" />
              <div style="margin-top: 20px">
                <el-skeleton-item variant="text" style="margin-bottom: 16px" />
                <el-skeleton-item variant="text" style="margin-bottom: 16px" />
                <el-skeleton-item variant="text" style="margin-bottom: 16px" />
                <el-skeleton-item variant="text" style="margin-bottom: 16px" />
              </div>
            </template>
          </el-skeleton>
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
import { ArrowLeft } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()

const todo = ref(null)
const loading = ref(false)
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
/* 缩短过渡动画时间 */
.el-fade-in-linear-enter-active {
  transition: opacity 0.2s linear !important;
}

.el-fade-in-linear-leave-active {
  transition: opacity 0.15s linear !important;
}

.todo-detail-container {
  width: 100%;
}

.todo-detail-card {
  border-radius: 8px;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-title {
  font-size: 18px;
  font-weight: bold;
  text-align: center;
  flex: 1;
}

.todo-detail-content {
  padding: 20px 0;
}

.todo-basic-info {
  padding: 0 20px;
}

.todo-status {
  margin-bottom: 20px;
}

.todo-title {
  font-size: 24px;
  font-weight: bold;
  margin-bottom: 20px;
  color: #303133;
}

.todo-title-input {
  margin-bottom: 20px;
}

.todo-description h3 {
  margin: 20px 0 10px 0;
  font-size: 18px;
  color: #606266;
}

.todo-description p {
  color: #606266;
  line-height: 1.6;
  white-space: pre-wrap;
}

.todo-time-info {
  padding: 0 20px;
}

.time-item {
  display: flex;
  align-items: center;
  margin-bottom: 15px;
}

.time-label {
  width: 80px;
  font-weight: bold;
  color: #606266;
}

.time-value {
  margin-left: 20px;
  color: #303133;
  flex: 1;
}

.time-remaining {
  margin-left: 10px;
  color: #909399;
  font-size: 14px;
}

.loading-container,
.empty-container {
  padding: 20px 0;
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 300px;
}

.empty-container {
  width: 100%;
}

.repeat-settings {
  padding: 0 20px;
}

.repeat-settings label {
  display: block;
  margin-bottom: 12px;
  font-weight: bold;
  color: #606266;
}

.repeat-controls {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
}

.repeat-controls .el-select {
  width: 180px;
}

.repeat-controls .el-button + .el-button {
  margin-left: 0;
}

.repeat-help {
  color: #606266;
  font-size: 14px;
  line-height: 1.6;
}

.schedule-error {
  color: #f56c6c;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 20px;
}
</style>