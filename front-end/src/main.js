import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'

import ElementPlus, { ElMessage } from 'element-plus'
import 'element-plus/dist/index.css'
import './styles/base.css'

// 提示消息合并：相同内容的提示在短时间窗口内合并为一条并显示次数，
// 配合操作防抖避免快速点击时提示不断弹出闪动。
for (const type of ['success', 'warning', 'info', 'error']) {
  const original = ElMessage[type]
  ElMessage[type] = (options, ...rest) => {
    const payload = typeof options === 'string'
      ? { message: options, grouping: true }
      : { ...(options || {}), grouping: true }
    return original(payload, ...rest)
  }
}

// 导入并初始化键盘事件处理器
import './utils/keyboardHandler'

// 导入国际化配置
import i18n from './i18n'

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(ElementPlus)
app.use(i18n)

app.mount('#app')