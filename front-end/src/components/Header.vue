<template>
  <el-header class="header">
    <div class="header-container">
      <button
        v-if="mobile"
        class="menu-button"
        type="button"
        :aria-label="t('header.navigation')"
        :aria-expanded="navigationOpen"
        aria-controls="mobile-navigation"
        @click="$emit('open-navigation')"
      ><el-icon><Menu /></el-icon></button>
      <router-link class="brand" to="/home/list">Simple Todo List <span>Pro</span></router-link>
      <div class="greeting" :title="t('header.greetingLabel')">
        <span class="greeting-emoji" aria-hidden="true">{{ greeting.emoji }}</span>
        <span class="greeting-text">{{ greetingLine }}</span>
      </div>
      <div
        class="header-clock"
        role="button"
        tabindex="0"
        :aria-pressed="showSeconds"
        :aria-label="`${dateText} ${weekdayText} ${timeText}`"
        @click="showSeconds = !showSeconds"
        @keydown.enter.prevent="showSeconds = !showSeconds"
        @keydown.space.prevent="showSeconds = !showSeconds"
      >
        <div class="clock-date">{{ dateText }}<span class="clock-weekday">{{ weekdayText }}</span></div>
        <div class="clock-time">
          <template v-for="(char, index) in timeChars" :key="index">
            <span v-if="char === ':'" class="clock-colon">:</span>
            <span v-else class="digit-tile">
              <transition name="flip" mode="out-in">
                <span :key="char" class="digit-face">{{ char }}</span>
              </transition>
            </span>
          </template>
        </div>
      </div>
      <button class="profile-button" type="button" :aria-label="t('sidebar.userInfo')" @click="goToUserInfo">
        <el-avatar :size="30">{{ displayedUserName.charAt(0).toUpperCase() }}</el-avatar>
      </button>
      <el-button
        class="theme-button"
        :disabled="themeSwitching"
        :aria-label="isDark ? t('header.themeLight') : t('header.themeDark')"
        :title="isDark ? t('header.themeLight') : t('header.themeDark')"
        @click="toggleTheme"
      >
        <el-icon><Sunny v-if="isDark" /><Moon v-else /></el-icon>
      </el-button>
      <el-dropdown @command="switchLanguage">
        <el-button class="language-button" :aria-label="t('header.language')">
          {{ currentLanguage === 'zh-CN' ? '中文' : 'EN' }}
          <el-icon class="el-icon--right"><ArrowDown /></el-icon>
        </el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="zh-CN">中文</el-dropdown-item>
            <el-dropdown-item command="en-US">English</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </el-header>
</template>

<script setup>
import { computed, ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowDown, Menu, Moon, Sunny } from '@element-plus/icons-vue'
import { isDark, themeSwitching, toggleTheme } from '@/composables/useTheme'
import { useI18n } from 'vue-i18n'

defineProps({ mobile: Boolean, navigationOpen: Boolean })
defineEmits(['open-navigation'])
const { t, locale } = useI18n()
const router = useRouter()
const displayedUserName = ref(localStorage.getItem('userName') || '')
const currentLanguage = ref(localStorage.getItem('locale') || 'zh-CN')
const handleUsernameUpdate = event => { displayedUserName.value = event.detail }

// 问候语：按小时分段，每分钟刷新一次
const now = ref(new Date())
let clockTimer = null
const greetingKey = computed(() => {
  const hour = now.value.getHours()
  if (hour < 5) return 'lateNight'
  if (hour < 8) return 'morning'
  if (hour < 11) return 'forenoon'
  if (hour < 13) return 'noon'
  if (hour < 17) return 'afternoon'
  if (hour < 19) return 'evening'
  if (hour < 23) return 'night'
  return 'lateNight'
})
const greetingEmoji = { lateNight: '🌜', morning: '🌅', forenoon: '☀️', noon: '🌤️', afternoon: '🌞', evening: '🌇', night: '🌙' }
const greeting = computed(() => ({
  emoji: greetingEmoji[greetingKey.value],
  text: t(`header.greeting.${greetingKey.value}`)
}))
// 问候语后接用户名：中文用「，」英文用「, 」
const greetingLine = computed(() => {
  const name = displayedUserName.value
  if (!name) return greeting.value.text
  return `${greeting.value.text}${locale.value === 'zh-CN' ? '，' : ', '}${name}`
})

// 中间时钟：日期 + 时分；点击切换是否显示秒
const pad = value => String(value).padStart(2, '0')
const showSeconds = ref(false)
const timeText = computed(() => {
  const date = now.value
  const base = `${pad(date.getHours())}:${pad(date.getMinutes())}`
  return showSeconds.value ? `${base}:${pad(date.getSeconds())}` : base
})
// 拆分为逐位数字，只有变化的位数会翻页
const timeChars = computed(() => timeText.value.split(''))
const dateText = computed(() => new Intl.DateTimeFormat(locale.value === 'zh-CN' ? 'zh-CN' : 'en-US', { month: 'short', day: 'numeric' }).format(now.value))
const weekdayText = computed(() => new Intl.DateTimeFormat(locale.value === 'zh-CN' ? 'zh-CN' : 'en-US', { weekday: 'short' }).format(now.value))

onMounted(() => {
  window.addEventListener('usernameUpdated', handleUsernameUpdate)
  clockTimer = window.setInterval(() => { now.value = new Date() }, 1000)
})
onUnmounted(() => {
  window.removeEventListener('usernameUpdated', handleUsernameUpdate)
  window.clearInterval(clockTimer)
})
const goToUserInfo = () => router.push('/home/userinfo')
const switchLanguage = lang => {
  locale.value = lang
  currentLanguage.value = lang
  localStorage.setItem('locale', lang)
}
</script>

<style scoped>
.header { height: 72px; padding: 0 32px; border-bottom: 1px solid var(--app-border); background: var(--app-surface); }
.header-container { height: 100%; max-width: none; margin: 0; display: flex; align-items: center; gap: 12px; min-width: 0; position: relative; }
.brand { margin-right: 12px; color: var(--el-text-color-primary); text-decoration: none; font-size: 18px; font-weight: 650; letter-spacing: -.5px; white-space: nowrap; }
.brand span { color: var(--app-muted); font-size: 11px; font-weight: 500; letter-spacing: .4px; margin-left: 4px; }
.greeting { display: inline-flex; align-items: center; gap: 6px; padding: 4px 2px; color: var(--el-text-color-regular); font-size: 13px; white-space: nowrap; }
.greeting-emoji { font-size: 15px; line-height: 1; }
/* 中间翻页时钟 */
.header-clock {
  position: absolute;
  left: 50%;
  top: 50%;
  transform: translate(-50%, -50%);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1px;
  line-height: 1.15;
  user-select: none;
  cursor: pointer;
  padding: 2px 6px;
  border-radius: 8px;
  transition: background-color .15s ease;
}
.header-clock:hover { background: var(--app-bg); }
.header-clock:focus-visible { outline: 2px solid var(--el-color-primary); outline-offset: 2px; }
.clock-date { display: inline-flex; align-items: center; gap: 6px; font-size: 11px; color: var(--app-muted); letter-spacing: .2px; }
.clock-weekday { opacity: .85; }
/* 计分板式翻页时钟：每位数字单独一块翻页牌 */
.clock-time { display: flex; align-items: center; gap: 3px; perspective: 320px; }
.digit-tile {
  position: relative;
  width: 20px;
  height: 26px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 5px;
  background: var(--app-bg);
  border: 1px solid var(--app-border);
  overflow: hidden;
  font-size: 15px;
  font-weight: 650;
  font-variant-numeric: tabular-nums;
  color: var(--el-text-color-primary);
}
/* 中间的翻页接缝 */
.digit-tile::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  top: 50%;
  height: 1px;
  background: var(--app-border);
  opacity: .75;
}
.digit-face { display: inline-block; line-height: 1; }
.clock-colon { font-size: 14px; font-weight: 700; color: var(--app-muted); padding: 0 1px; }
/* 翻页动画：沿水平轴翻转 */
.flip-enter-active { transition: transform .26s cubic-bezier(.3, .9, .3, 1), opacity .2s ease; transform-origin: center center; }
.flip-leave-active { transition: transform .18s ease, opacity .18s ease; transform-origin: center center; position: absolute; }
.flip-enter-from { transform: rotateX(-90deg); opacity: .2; }
.flip-leave-to { transform: rotateX(90deg); opacity: 0; }
.theme-button { border-color: transparent; background: transparent; color: var(--app-muted); padding: 8px; }
.theme-button:hover { color: var(--el-color-primary); background: var(--app-bg); }
.profile-button, .menu-button { margin-left: auto; border: 0; background: transparent; display: inline-flex; align-items: center; justify-content: center; gap: 10px; cursor: pointer; color: inherit; border-radius: 8px; min-height: 44px; padding: 4px 8px; }
.profile-button, .menu-button { transition: background-color .15s ease, color .15s ease; }
.username { max-width: 140px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 13px; font-weight: 500; color: var(--el-text-color-regular); }
.profile-button .el-avatar { background: var(--app-bg); color: var(--el-color-primary); font-weight: 600; border: 1px solid var(--app-border); }
.menu-button { width: 44px; flex: none; font-size: 21px; }
.language-button { border-color: transparent; background: transparent; color: var(--app-muted); }
@media (hover: hover) {
  .profile-button:hover, .menu-button:hover { background: var(--app-bg); }
  .language-button:hover { background: var(--app-bg); border-color: var(--app-border); color: var(--el-text-color-primary); }
}
.profile-button:active, .menu-button:active { background: var(--el-color-primary-light-9); }
.profile-button:focus-visible, .menu-button:focus-visible,
.language-button:focus-visible { outline: 2px solid var(--el-color-primary); outline-offset: 2px; }
@media (max-width: 760px) {
  .header { height: auto; min-height: 64px; padding: 8px 12px; }
  .greeting { padding: 4px 0; }
  .greeting-text { display: none; }
  .header-clock { display: none; }
  .header-container { gap: 4px; flex-wrap: wrap; }
  .brand { font-size: 16px; }
  .profile-button { padding: 4px; }
  .username { display: none; }
  .language-button { padding: 8px; }
}
@media (max-width: 380px) {
  .brand span { display: none; }
  .brand { font-size: 14px; }
}
</style>
