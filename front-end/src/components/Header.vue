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
      <button class="profile-button" type="button" :aria-label="t('sidebar.userInfo')" @click="goToUserInfo">
        <el-avatar :size="30">{{ displayedUserName.charAt(0).toUpperCase() }}</el-avatar>
        <span class="username">{{ displayedUserName }}</span>
      </button>
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
      <el-button class="logout-button" @click="handleLogout">{{ t('header.logout') }}</el-button>
    </div>
  </el-header>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowDown, Menu } from '@element-plus/icons-vue'
import { clearUserInfoCache } from '@/utils/user'
import { useI18n } from 'vue-i18n'

defineProps({ mobile: Boolean, navigationOpen: Boolean })
defineEmits(['open-navigation'])
const { t, locale } = useI18n()
const router = useRouter()
const displayedUserName = ref(localStorage.getItem('userName') || '')
const currentLanguage = ref(localStorage.getItem('locale') || 'zh-CN')
const handleUsernameUpdate = event => { displayedUserName.value = event.detail }
onMounted(() => window.addEventListener('usernameUpdated', handleUsernameUpdate))
onUnmounted(() => window.removeEventListener('usernameUpdated', handleUsernameUpdate))
const goToUserInfo = () => router.push('/home/userinfo')
const switchLanguage = lang => {
  locale.value = lang
  currentLanguage.value = lang
  localStorage.setItem('locale', lang)
}
const handleLogout = () => {
  localStorage.removeItem('token')
  localStorage.removeItem('userName')
  localStorage.removeItem('userId')
  clearUserInfoCache()
  router.push('/login')
}
</script>

<style scoped>
.header { height: 72px; padding: 0 32px; border-bottom: 1px solid var(--app-border); background: var(--app-surface); }
.header-container { height: 100%; max-width: none; margin: 0; display: flex; align-items: center; gap: 12px; min-width: 0; }
.brand { margin-right: auto; color: var(--el-text-color-primary); text-decoration: none; font-size: 18px; font-weight: 650; letter-spacing: -.5px; white-space: nowrap; }
.brand span { color: var(--app-muted); font-size: 11px; font-weight: 500; letter-spacing: .4px; margin-left: 4px; }
.profile-button, .menu-button { border: 0; background: transparent; display: inline-flex; align-items: center; justify-content: center; gap: 10px; cursor: pointer; color: inherit; border-radius: 8px; min-height: 44px; padding: 4px 8px; }
.profile-button, .menu-button { transition: background-color .15s ease, color .15s ease; }
.username { max-width: 140px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 13px; font-weight: 500; color: var(--el-text-color-regular); }
.profile-button .el-avatar { background: var(--app-bg); color: var(--el-color-primary); font-weight: 600; border: 1px solid var(--app-border); }
.menu-button { width: 44px; flex: none; font-size: 21px; }
.language-button, .logout-button { border-color: transparent; background: transparent; color: var(--app-muted); }
@media (hover: hover) {
  .profile-button:hover, .menu-button:hover { background: var(--app-bg); }
  .language-button:hover, .logout-button:hover { background: var(--app-bg); border-color: var(--app-border); color: var(--el-text-color-primary); }
}
.profile-button:active, .menu-button:active { background: var(--el-color-primary-light-9); }
.profile-button:focus-visible, .menu-button:focus-visible,
.language-button:focus-visible, .logout-button:focus-visible { outline: 2px solid var(--el-color-primary); outline-offset: 2px; }
@media (max-width: 760px) {
  .header { height: auto; min-height: 64px; padding: 8px 12px; }
  .header-container { gap: 4px; flex-wrap: wrap; }
  .brand { font-size: 16px; }
  .profile-button { padding: 4px; }
  .username { display: none; }
  .language-button { padding: 8px; }
  .logout-button { padding: 8px; font-size: 12px; }
}
@media (max-width: 380px) {
  .brand span { display: none; }
  .brand { font-size: 14px; }
}
</style>
