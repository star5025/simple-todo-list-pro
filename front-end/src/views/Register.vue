<template>
  <div class="register-container">
    <el-card class="register-card">
      <template #header>
        <div class="card-header">
          <el-text type="primary">
            {{ $t('register.title') }}
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
      <el-form class="register-form" label-position="top" @submit.prevent="handleRegister">
        <el-form-item :label="$t('register.username')" for="register-username">
          <el-input
            id="register-username" name="username" autocomplete="username"
            v-model="registerForm.userName"
            :placeholder="$t('register.username')"
            clearable
            class="register-input"
          />
        </el-form-item>
        <el-form-item :label="$t('register.password')" for="register-password">
          <el-input
            id="register-password" name="password" autocomplete="new-password"
            v-model="registerForm.userPassword"
            type="password"
            :placeholder="$t('register.password')"
            show-password
            class="register-input"
          />
        </el-form-item>
        <el-form-item :label="$t('register.confirmPassword')" for="register-confirm-password">
          <el-input
            id="register-confirm-password" name="confirm-password" autocomplete="new-password"
            v-model="registerForm.confirmPassword"
            type="password"
            :placeholder="$t('register.confirmPassword')"
            show-password
            class="register-input"
          />
        </el-form-item>
        <el-form-item class="button-form-item">
          <el-button 
            type="primary" 
            class="register-button"
            native-type="submit"
            :loading="submitting"
          >
            {{ $t('register.registerButton') }}
          </el-button>
        </el-form-item>
      </el-form>
      <div class="login-link">
        <el-text>{{ $t('register.hasAccount') }}</el-text>
        <el-button 
          type="primary" 
          link
          @click="goToLogin"
        >
          {{ $t('register.loginNow') }}
        </el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import request from '@/utils/request'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'

const { locale } = useI18n()

const registerForm = ref({
  userName: '',
  userPassword: '',
  confirmPassword: ''
})

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

const submitting = ref(false)

const handleRegister = async () => {
  if (submitting.value) return
  // 表单验证
  if (!registerForm.value.userName || !registerForm.value.userPassword) {
    ElMessage.error('用户名和密码不能为空')
    return
  }
  
  if (registerForm.value.userPassword !== registerForm.value.confirmPassword) {
    ElMessage.error('两次输入的密码不一致')
    return
  }
  
  submitting.value = true
  try {
    // 只发送用户名和密码到后端，不发送确认密码
    const requestData = {
      userName: registerForm.value.userName,
      userPassword: registerForm.value.userPassword
    }
    
    const res = await request.post('/user/register', requestData)
    // 注意：现在业务逻辑错误不会进入catch块，而是正常返回
    if (res.code === 1) {
      ElMessage.success('注册成功！')
      router.push('/login')
    } else {
      // 只显示后端返回的错误信息，如果没有则显示默认信息
      if (res.msg) {
        ElMessage.error(res.msg)
      } else {
        ElMessage.error('注册失败')
      }
    }
  } catch (error) {
    // request displays the error; do not log Axios config containing the password.
  } finally {
    submitting.value = false
  }
}

const goToLogin = () => {
  router.push('/login')
}
</script>

<style scoped>
.register-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: safe center;
  flex: 1;
  min-height: 0;
  padding: 24px 16px;
  box-sizing: border-box;
  background: var(--app-bg, #f7f8fa);
  color: var(--el-text-color-primary, #243047);
}

.register-card {
  width: 100%;
  max-width: 420px;
  min-width: 0;
  border: 1px solid var(--app-border, #e7ebf0);
  border-radius: 12px;
  background: var(--app-surface, #fff);
  box-shadow: none;
}

.register-card :deep(.el-card__header),
.register-card :deep(.el-card__body) {
  padding: 24px;
}

.card-header {
  text-align: left;
  font-size: 20px;
  font-weight: 600;
}

.card-header :deep(.el-text) {
  color: var(--el-text-color-primary);
  font-size: inherit;
  font-weight: inherit;
  letter-spacing: -.3px;
}

.language-switch :deep(.el-select__wrapper) {
  color: var(--el-text-color-regular);
  background: var(--app-bg);
}

.register-input :deep(.el-input__wrapper.is-focus) {
  outline: 2px solid var(--el-color-primary-light-8);
  outline-offset: 1px;
}

.register-button { font-weight: 600; }
.login-link :deep(.el-button.is-link) { font-weight: 600; }
.login-link :deep(.el-button.is-link:focus-visible) {
  outline: 2px solid var(--el-color-primary);
  outline-offset: 2px;
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

.register-input,
.register-button {
  width: 100%;
}

.register-card :deep(.el-input__wrapper),
.register-card :deep(.el-select__wrapper),
.register-card :deep(.el-button) {
  min-height: 44px;
  box-sizing: border-box;
}

.register-card :deep(.el-form-item__label) {
  color: var(--el-text-color-regular);
  font-weight: 500;
  line-height: 1.5;
}

.login-link {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px 8px;
  color: var(--app-muted, #697586);
}

.login-link :deep(.el-text) {
  color: inherit;
}

@media (max-width: 600px) {
  .register-card :deep(.el-card__header),
  .register-card :deep(.el-card__body) {
    padding: 16px;
  }
}

/* 矮窗口：收紧间距，尽量完整展示，避免出现竖向滚动条 */
@media (max-height: 720px) {
  .register-container {
    justify-content: flex-start;
    padding-top: 10px;
    padding-bottom: 10px;
  }

  .register-card :deep(.el-card__header) {
    padding: 10px 16px;
  }

  .register-card :deep(.el-card__body) {
    padding: 10px 16px 12px;
  }

  .language-switch {
    margin-bottom: 6px;
  }

  .register-card :deep(.el-form-item) {
    margin-bottom: 10px;
  }

  .register-card :deep(.el-input__wrapper),
  .register-card :deep(.el-select__wrapper),
  .register-card :deep(.el-button) {
    min-height: 40px;
  }

  .login-link {
    margin-top: 6px;
  }
}
</style>