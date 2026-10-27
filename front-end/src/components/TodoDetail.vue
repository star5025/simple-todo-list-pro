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
              <button
                v-if="!isEditing"
                type="button"
                class="icon-action favourite-icon"
                :class="{ favourited: todo.favourite }"
                :aria-label="`${t('addTodo.favourite')}: ${todo.taskName}`"
                :aria-pressed="!!todo.favourite"
                :disabled="actionBusy"
                @click="toggleFavourite"
              >
                <el-icon aria-hidden="true"><StarFilled v-if="todo.favourite" /><Star v-else /></el-icon>
              </button>
              <button
                v-if="!isEditing"
                type="button"
                class="icon-action delete-icon"
                :aria-label="`${t('todoList.delete')}: ${todo.taskName}`"
                :disabled="actionBusy"
                @click="confirmDeleteTodo"
              >
                <el-icon aria-hidden="true"><Delete /></el-icon>
              </button>
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
            
            <div v-if="!isEditing" class="title-row">
              <button
                type="button"
                class="complete-box"
                :class="{ done: todo.status }"
                :aria-label="todo.status ? t('todoList.completed') : t('todoList.markAsComplete')"
                :aria-pressed="!!todo.status"
                :disabled="actionBusy || todo.status"
                @click="completeTodo"
              >
                <span class="box" aria-hidden="true">
                  <svg v-if="todo.status" class="check" viewBox="0 0 24 24">
                    <polyline points="5 13 10 18 19 7" />
                  </svg>
                </span>
              </button>
              <h2 class="todo-title">{{ todo.taskName }}</h2>
            </div>
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
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import { ArrowLeft, Delete, Loading, Star, StarFilled } from '@element-plus/icons-vue'
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
const actionBusy = ref(false)
let disposed = false

const notifyFavouriteChanged = () => {
  window.dispatchEvent(new CustomEvent('favouriteChanged'))
}

// 详情页直接收藏/取消收藏：只提交 favourite 字段，避免旧快照覆盖其他字段
const toggleFavourite = async () => {
  if (actionBusy.value || !todo.value) return
  actionBusy.value = true
  const next = !todo.value.favourite
  try {
    const response = await request.patch(`/task/${taskId}`, { favourite: next })
    if (disposed) return
    if (response.code === 1) {
      todo.value.favourite = next
      notifyFavouriteChanged()
      ElMessage.success(t(next ? 'todoList.addedToFavorites' : 'todoList.removedFromFavorites'))
    } else {
      ElMessage.error(response.msg || t('todoList.favoriteOperationFailed'))
    }
  } catch (error) {
    if (!disposed) ElMessage.error(t('todoList.favoriteOperationFailed'))
  } finally {
    if (!disposed) actionBusy.value = false
  }
}

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

// 标记完成：与列表一致需要二次确认，完成后刷新详情以反映服务端状态
const completeTodo = async () => {
  if (actionBusy.value || !todo.value) return
  if (todo.value.status) {
    ElMessage.warning(t('todoList.cannotUncomplete'))
    return
  }
  try {
    await ElMessageBox.confirm(
      confirmText('todoList.completeConfirmNamed', 'todoList.completeConfirmShort', [todo.value.taskName]),
      '',
      messageBoxOptions()
    )
  } catch {
    return
  }
  actionBusy.value = true
  try {
    const response = await request.patch(`/task/${taskId}`, { status: true })
    if (disposed) return
    if (response.code === 1) {
      todo.value.status = true
      notifyFavouriteChanged()
      // 直接更新本地状态，不重新拉取详情：避免整页加载动画，只保留顶部提示与勾选动画
      ElMessage.success(t('todoList.taskCompleted'))
    } else {
      ElMessage.error(response.msg || t('todoList.statusUpdateFailed'))
    }
  } catch (error) {
    if (!disposed) ElMessage.error(t('todoList.statusUpdateFailed'))
  } finally {
    if (!disposed) actionBusy.value = false
  }
}

const confirmDeleteTodo = async () => {
  if (actionBusy.value || !todo.value) return
  try {
    await ElMessageBox.confirm(
      confirmText('todoList.deleteConfirmNamed', 'todoList.deleteConfirmShort', [todo.value.taskName]),
      '',
      messageBoxOptions({ confirmButtonClass: 'el-button--danger' })
    )
  } catch {
    return
  }
  actionBusy.value = true
  try {
    const response = await request.delete(`/task/${taskId}`)
    if (disposed) return
    if (response.code === 1) {
      notifyFavouriteChanged()
      ElMessage.success(t('todoList.deleteSuccess'))
      router.push('/home/list')
    } else {
      ElMessage.error(response.msg || t('todoList.deleteFailed'))
    }
  } catch (error) {
    if (!disposed) ElMessage.error(t('todoList.deleteFailed'))
  } finally {
    if (!disposed) actionBusy.value = false
  }
}

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

.header-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
}

.header-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

/* 与待办列表项一致的图标按钮：36px 方形、无边框、悬停主色 */
.icon-action {
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
  font-size: 16px;
  cursor: pointer;
  transition: background-color .12s ease, color .12s ease;
}

.icon-action:hover:not(:disabled) {
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.icon-action:disabled {
  cursor: default;
  opacity: .55;
}

/* 收藏星标固定为黄色，悬停保持黄色、不出现蓝色底 */
.favourite-icon.favourited,
.favourite-icon.favourited:hover:not(:disabled) {
  color: var(--app-star, #f0b429);
  background: transparent;
}

/* 与列表一致的方形勾选框：空白格 → 完成后填充主题蓝并显示白色对勾 */
.complete-box {
  width: 32px;
  height: 32px;
  padding: 0;
  border: 0;
  background: transparent;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: none;
  cursor: pointer;
  color: #fff;
}

.complete-box .box {
  position: relative;
  width: 18px;
  height: 18px;
  box-sizing: border-box;
  border: 1px solid var(--app-muted, #697586);
  border-radius: 3px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  transition: background-color .12s ease, border-color .12s ease;
}

/* 打勾描边动画：仅在完成瞬间播放一次 */
.complete-box .check {
  width: 12px;
  height: 12px;
  fill: none;
  stroke: #fff;
  stroke-width: 4;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-dasharray: 26;
  stroke-dashoffset: 26;
  animation: check-draw .4s .1s cubic-bezier(.4, 0, .2, 1) forwards;
}

.complete-box.done .box {
  border-color: var(--el-color-success);
  background: var(--el-color-success);
  animation: box-pop .45s cubic-bezier(.2, .9, .25, 1.25);
}

/* 完成后向外扩散的光环，强化完成感 */
.complete-box.done .box::after {
  content: '';
  position: absolute;
  inset: -2px;
  border: 2px solid rgba(103, 194, 58, .55);
  border-radius: 5px;
  pointer-events: none;
  animation: box-ring .5s ease-out forwards;
}

@keyframes check-draw {
  to { stroke-dashoffset: 0; }
}

@keyframes box-pop {
  0% { transform: scale(.8); box-shadow: 0 0 0 0 rgba(103, 194, 58, 0); }
  45% { transform: scale(1.35); box-shadow: 0 0 0 3px rgba(103, 194, 58, .14); }
  70% { transform: scale(.97); }
  100% { transform: scale(1); box-shadow: 0 0 0 0 rgba(103, 194, 58, 0); }
}

@keyframes box-ring {
  0% { opacity: .6; transform: scale(.92); }
  100% { opacity: 0; transform: scale(1.9); }
}

.complete-box:hover:not(:disabled) .box {
  border-color: var(--el-color-primary);
}

.complete-box:disabled {
  cursor: default;
}

.delete-icon:hover:not(:disabled) {
  color: var(--el-color-danger);
  background: var(--el-color-danger-light-9);
}

.todo-title {
  margin: 0;
  font-size: clamp(22px, 4vw, 28px);
  font-weight: 600;
  line-height: 1.4;
  overflow-wrap: anywhere;
  word-break: break-word;
}

/* 完成按钮位于标题左侧，与标题垂直居中对齐 */
.title-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 20px;
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