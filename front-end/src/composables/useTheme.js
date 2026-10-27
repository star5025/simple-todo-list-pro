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
// 切换过程中禁用按钮，避免连点抖动
export const themeSwitching = ref(false)

const resolveDark = mode => (mode === 'auto' ? isNightTime() : mode === 'dark')

export function applyTheme(dark) {
  isDark.value = dark
  document.documentElement.classList.toggle('dark', dark)
}

let switchTimer = null

export function setThemeMode(mode) {
  themeMode.value = mode
  localStorage.setItem(STORAGE_KEY, mode)
  applyTheme(resolveDark(mode))
}

// 按钮点击：用遮罩淡出完成切换，600ms 内忽略重复点击
export function toggleTheme() {
  if (themeSwitching.value) return
  themeSwitching.value = true
  const root = document.documentElement

  // 用当前主题背景色覆盖整屏，切换颜色后再淡出，避免逐元素补间造成文字闪烁
  const veil = document.createElement('div')
  veil.className = 'theme-fade'
  veil.style.background = getComputedStyle(root).backgroundColor || (isDark.value ? '#11151b' : '#f7f8fa')
  document.body.appendChild(veil)
  root.classList.add('theme-switching')

  window.requestAnimationFrame(() => {
    applyTheme(!isDark.value)
    themeMode.value = isDark.value ? 'dark' : 'light'
    localStorage.setItem(STORAGE_KEY, themeMode.value)
    window.requestAnimationFrame(() => veil.classList.add('theme-fade-out'))
  })

  window.clearTimeout(switchTimer)
  switchTimer = window.setTimeout(() => {
    veil.remove()
    root.classList.remove('theme-switching')
    themeSwitching.value = false
  }, 480)
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
