<template>
  <div class="login-container">
    <el-card class="login-card">
      <template #header>
        <div class="card-header">
          <el-text type="primary">
            {{ $t('login.title') }}
          </el-text>
        </div>
      </template>
      <div class="language-switch">
        <el-select 
          v-model="currentLocale" 
          @change="changeLanguage"
          :aria-label="$t('header.language')"
        >
          <el-option 
            v-for="locale in locales" 
            :key="locale.value" 
            :label="locale.label" 
            :value="locale.value"
          />
        </el-select>
      </div>
      <el-form class="login-form" label-position="top" @submit.prevent="handleLogin">
        <el-form-item :label="$t('login.username')" for="login-username">
          <el-input id="login-username" name="username" autocomplete="username" v-model="loginForm.userName" :placeholder="$t('login.username')" clearable class="login-input" />
        </el-form-item>
        <el-form-item :label="$t('login.password')" for="login-password">
          <el-input id="login-password" name="password" autocomplete="current-password" v-model="loginForm.userPassword" type="password" :placeholder="$t('login.password')" show-password
            class="login-input" />
        </el-form-item>
        <el-form-item>
          <el-checkbox v-model="rememberMe" @change="handleRememberMeChange">{{ $t('login.rememberMe') }}</el-checkbox>
        </el-form-item>
        <el-form-item class="button-form-item">
          <el-button type="primary" class="login-button" native-type="submit" :loading="submitting">
            {{ $t('login.loginButton') }}
          </el-button>
        </el-form-item>
      </el-form>
      <div class="register-link">
        <el-text>{{ $t('login.noAccount') }}</el-text>
        <el-button type="primary" link @click="goToRegister">
          {{ $t('login.registerNow') }}
        </el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import request from '@/utils/request'
import { ElMessage } from 'element-plus'
import { clearUserInfoCache } from '@/utils/user'
import { useI18n } from 'vue-i18n'

const { locale } = useI18n()

const loginForm = ref({
  userName: '',
  userPassword: ''
})

const rememberMe = ref(false)

const router = useRouter()

// 语言切换相关
const currentLocale = ref(localStorage.getItem('locale') || 'zh-CN')
const locales = ref([
  { label: '中文', value: 'zh-CN' },
  { label: 'English', value: 'en-US' }
])

const changeLanguage = (newLocale) => {
  locale.value = newLocale
  localStorage.setItem('locale', newLocale)
}

// 页面加载时检查是否有记住的登录信息
onMounted(() => {
  const savedCredentials = localStorage.getItem('rememberedCredentials')
  if (savedCredentials) {
    try {
      const credentials = JSON.parse(savedCredentials)
      loginForm.value.userName = credentials.userName || ''
      localStorage.setItem('rememberedCredentials', JSON.stringify({ userName: loginForm.value.userName }))
      rememberMe.value = true
    } catch (e) {
      localStorage.removeItem('rememberedCredentials')
    }
  }
})

// 处理"记住我"选项变化
const handleRememberMeChange = (checked) => {
  if (!checked) {
    // 如果用户取消勾选"记住我"，则清除已保存的凭据
    localStorage.removeItem('rememberedCredentials')
  }
}

async function login() {
  // 登录前清除旧的用户信息缓存
  clearUserInfoCache()
  
  // 清除localStorage中的旧用户信息
  localStorage.removeItem('userId')
  localStorage.removeItem('userName')
  
  try {
    // 确保发送的数据格式正确
    const loginData = {
      userName: loginForm.value.userName,
      userPassword: loginForm.value.userPassword
    };
    
    const res = await request.post('/user/login', loginData)
    
    // 注意：现在业务逻辑错误不会进入catch块，而是正常返回
    if (res.code === 1) {
      // 如果用户选择了"记住我"，则保存凭据
      if (rememberMe.value) {
        const credentials = {
          userName: loginForm.value.userName
        }
        localStorage.setItem('rememberedCredentials', JSON.stringify(credentials))
      } else {
        // 如果没有选择"记住我"，确保清除已保存的凭据
        localStorage.removeItem('rememberedCredentials')
      }
      
      // 存储token
      localStorage.setItem('token', res.data?.token || res.token)
      // 存储用户名
      localStorage.setItem('userName', loginForm.value.userName)
      // 存储用户ID（如果后端返回了）
      if (res.data?.userId !== undefined && res.data?.userId !== null) {
        localStorage.setItem('userId', res.data.userId)
      }
      return true // 登录成功返回true
    } else if (res.code === 0) {
      // 只显示后端返回的错误信息，如果没有则显示默认信息
      if (res.msg) {
        ElMessage.error(res.msg)
      } else {
        ElMessage.error('登录失败：用户名或密码错误')
      }
      return false // 登录失败返回false
    } else {
      // 只显示后端返回的错误信息，如果没有则显示默认信息
      if (res.msg) {
        ElMessage.error(res.msg)
      } else {
        ElMessage.error('未知错误')
      }
      return false
    }
  } catch (e) {
    // request already reports HTTP/authentication errors without logging credentials.
    return false // 异常时也返回false
  }
}

const loginResult = ref(null)
const submitting = ref(false)

const handleLogin = async () => {
  if (submitting.value) return
  submitting.value = true
  try {
    loginResult.value = await login()
    if (loginResult.value) {
      ElMessage.success('登录成功！')
      router.push('/home')
    } else {
      loginForm.value.userPassword = ''
    }
  } finally {
    submitting.value = false
  }
}


const goToRegister = () => {
  router.push('/register')
}
</script>

<style scoped>
.login-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: safe center;
  flex: 1;
  min-height: 0;
  padding: 24px 16px;
  box-sizing: border-box;
  background: var(--app-bg, #f7f8fa);
  color: #243047;
}

.login-card {
  width: 100%;
  max-width: 420px;
  min-width: 0;
  border: 1px solid var(--app-border, #e7ebf0);
  border-radius: 12px;
  background: var(--app-surface, #fff);
  box-shadow: none;
}

.login-card :deep(.el-card__header),
.login-card :deep(.el-card__body) {
  padding: 24px;
}

.card-header {
  text-align: left;
  font-size: 20px;
  font-weight: 600;
}

.card-header :deep(.el-text) {
  color: #243047;
  font-size: inherit;
}

.language-switch {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 20px;
}

.language-switch .el-select {
  width: 150px;
  max-width: 100%;
}

.login-input,
.login-button {
  width: 100%;
}

.login-card :deep(.el-input__wrapper),
.login-card :deep(.el-select__wrapper),
.login-card :deep(.el-button),
.login-card :deep(.el-checkbox) {
  min-height: 44px;
  box-sizing: border-box;
}

.login-card :deep(.el-form-item__label) {
  color: #243047;
  line-height: 1.5;
}

.register-link {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px 8px;
  color: var(--app-muted, #697586);
}

.register-link :deep(.el-text) {
  color: inherit;
}

@media (max-width: 600px) {
  .login-card :deep(.el-card__header),
  .login-card :deep(.el-card__body) {
    padding: 16px;
  }
}

/* 矮窗口：收紧间距，尽量完整展示，避免出现竖向滚动条 */
@media (max-height: 700px) {
  .login-container {
    justify-content: flex-start;
    padding-top: 12px;
    padding-bottom: 12px;
  }

  .login-card :deep(.el-card__header) {
    padding: 12px 20px;
  }

  .login-card :deep(.el-card__body) {
    padding: 12px 20px 16px;
  }

  .language-switch {
    margin-bottom: 8px;
  }

  .login-card :deep(.el-form-item) {
    margin-bottom: 12px;
  }

  .login-card :deep(.el-checkbox) {
    min-height: 32px;
  }

  .register-link {
    margin-top: 8px;
  }
}
</style>