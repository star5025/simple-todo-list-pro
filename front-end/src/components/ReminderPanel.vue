<template>
  <aside class="reminder-panel" :aria-label="t('reminders.stackTitle')" aria-live="polite">
    <div class="panel-header">
      <span class="panel-title">
        <el-icon><Bell /></el-icon>{{ t('reminders.stackTitle') }}
        <span class="panel-count">{{ reminders.length }}</span>
      </span>
      <button class="clear-button" type="button" @click="$emit('clear')">{{ t('reminders.clearAll') }}</button>
    </div>
    <ol class="reminder-list">
      <li v-for="reminder in visibleReminders" :key="reminder.key">
        <button
          class="reminder-item"
          type="button"
          :class="{ acked: reminder.acked }"
          @click="$emit('open', reminder)"
        >
          <el-icon class="item-icon" aria-hidden="true"><BellFilled v-if="!reminder.acked" /><CircleCheck v-else /></el-icon>
          <span class="item-name">{{ reminder.taskName }}</span>
        </button>
      </li>
    </ol>
    <button
      v-if="reminders.length > visibleLimit"
      class="toggle-button"
      type="button"
      @click="showAll = !showAll"
    >
      {{ showAll ? t('reminders.showLess') : t('reminders.showMore', [reminders.length - visibleLimit]) }}
    </button>
  </aside>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { Bell, BellFilled, CircleCheck } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'

const props = defineProps({ reminders: { type: Array, required: true } })
defineEmits(['open', 'clear'])
const { t } = useI18n()
const visibleLimit = 8
const showAll = ref(false)
watch(() => props.reminders.length, length => { if (length <= visibleLimit) showAll.value = false })
const visibleReminders = computed(() => showAll.value ? props.reminders : props.reminders.slice(0, visibleLimit))
</script>

<style scoped>
.reminder-panel {
  position: fixed;
  top: 16px;
  right: 16px;
  z-index: 2100;
  width: min(360px, calc(100vw - 24px));
  max-height: min(60vh, 480px);
  display: flex;
  flex-direction: column;
  background: var(--app-surface, #fff);
  border: 1px solid var(--app-border, #e7ebf0);
  border-radius: 12px;
  box-shadow: 0 6px 24px rgba(16, 24, 40, 0.12);
  overflow: hidden;
}
.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 12px 14px;
  border-bottom: 1px solid var(--app-border, #e7ebf0);
  background: var(--app-bg, #f7f8fa);
}
.panel-title { display: inline-flex; align-items: center; gap: 7px; font-size: 14px; font-weight: 600; color: var(--el-text-color-primary, #243047); }
.panel-title .el-icon { color: var(--el-color-primary); }
.panel-count { min-width: 20px; height: 20px; padding: 0 6px; border-radius: 999px; background: var(--el-color-primary); color: #fff; font-size: 12px; display: inline-flex; align-items: center; justify-content: center; }
.clear-button { border: 0; background: transparent; color: var(--app-muted, #697586); font-size: 13px; cursor: pointer; min-height: 36px; padding: 0 8px; border-radius: 8px; }
.clear-button:hover { color: var(--el-color-primary); background: rgba(64, 158, 255, 0.08); }
.reminder-list { list-style: none; margin: 0; padding: 6px; overflow-y: auto; overscroll-behavior: contain; }
.toggle-button { width: 100%; min-height: 40px; border: 0; border-top: 1px solid var(--app-border, #e7ebf0); background: transparent; color: var(--app-muted, #697586); font-size: 13px; cursor: pointer; }
.toggle-button:hover { color: var(--el-color-primary); }
@media (max-width: 760px) {
  .reminder-panel { top: auto; bottom: 12px; right: 12px; left: 12px; width: auto; max-height: 50vh; }
}
.reminder-item { display: flex; align-items: center; gap: 10px; width: 100%; min-height: 44px; padding: 8px 10px; border: 0; background: transparent; border-radius: 8px; cursor: pointer; color: var(--el-text-color-primary, #243047); font-size: 14px; text-align: left; }
.reminder-item:hover { background: var(--app-bg, #f7f8fa); }
.reminder-item.acked .item-name { color: var(--app-muted, #697586); }
.item-icon { color: var(--el-color-primary); flex: none; font-size: 16px; }
.reminder-item.acked .item-icon { color: #67c23a; }
.item-name { min-width: 0; flex: 1; overflow-wrap: anywhere; }
</style>
