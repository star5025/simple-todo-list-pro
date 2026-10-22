import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'

import ElementPlus, { ElMessage } from 'element-plus'
import 'element-plus/dist/index.css'
import './styles/base.css'

// 提示消息单条显示：新消息顶替旧消息，避免多条堆叠遮挡界面。
for (const type of ['success', 'warning', 'info', 'error']) {
  const original = ElMessage[type]
  ElMessage[type] = (options, ...rest) => {
    ElMessage.closeAll()
    return original(options, ...rest)
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