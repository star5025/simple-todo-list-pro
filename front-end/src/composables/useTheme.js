import { ref } from 'vue'

// 18:00 - 06:00 视为夜间
const isNightTime = (date = new Date()) => {
  const hour = date.getHours()
  return hour >= 18 || hour < 6
}

const STORAGE_KEY = 'themeMode'
const readMode = () => {
  const saved = localStorage.getItem(STORAGE_KEY)
  return saved === 'light' || saved === 'dark' ? saved : 'auto'
}

export const isDark = ref(false)
// auto：按时间自动；light / dark：用户手动选择，优先于自动
export const themeMode = ref(readMode())

const resolveDark = mode => (mode === 'auto' ? isNightTime() : mode === 'dark')

export function applyTheme(dark) {
  isDark.value = dark
  document.documentElement.classList.toggle('dark', dark)
}

export function setThemeMode(mode) {
  themeMode.value = mode
  localStorage.setItem(STORAGE_KEY, mode)
  applyTheme(resolveDark(mode))
}

// 按钮点击：在浅色与深色之间切换（手动模式）
export function toggleTheme() {
  setThemeMode(isDark.value ? 'light' : 'dark')
}

let timer = null

export function startAutoTheme() {
  applyTheme(resolveDark(themeMode.value))
  if (!timer) {
    timer = window.setInterval(() => {
      if (themeMode.value === 'auto') applyTheme(isNightTime())
    }, 60 * 1000)
  }
}

export function stopAutoTheme() {
  if (timer) window.clearInterval(timer)
  timer = null
}
