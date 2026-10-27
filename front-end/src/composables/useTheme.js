import { ref } from 'vue'

// 18:00 - 06:00 视为夜间，自动切到深色主题
const isNightTime = (date = new Date()) => {
  const hour = date.getHours()
  return hour >= 18 || hour < 6
}

export const isDark = ref(false)

export function applyTheme(dark) {
  isDark.value = dark
  document.documentElement.classList.toggle('dark', dark)
}

let timer = null

export function startAutoTheme() {
  const sync = () => applyTheme(isNightTime())
  sync()
  if (!timer) timer = window.setInterval(sync, 60 * 1000)
}

export function stopAutoTheme() {
  if (timer) window.clearInterval(timer)
  timer = null
}
