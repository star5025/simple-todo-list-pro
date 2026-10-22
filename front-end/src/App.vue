<script setup>
import { RouterView, useRoute } from 'vue-router'
import Footer from './components/Footer.vue'
import { onErrorCaptured, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'

const route = useRoute()
const hasError = ref(false)

// 登录/注册之间使用克制的淡入过渡，其他页面保持轻量淡入。
const isAuthPath = path => path.startsWith('/login') || path.startsWith('/register')
const pageTransition = ref('el-fade-in')
watch(() => route.fullPath, (to, from) => {
  pageTransition.value = from && isAuthPath(from) && isAuthPath(to) && to !== from ? 'auth-fade' : 'el-fade-in'
})

onErrorCaptured(() => {
  hasError.value = true
  console.error('页面出现未处理错误')
  ElMessage.error('页面出现错误，请刷新页面重试')
  return false
})

// 定义刷新页面的方法
const reloadPage = () => {
  window.location.reload()
}
</script>

<template>
  <el-container class="app-container" direction="vertical">
    <el-container class="main-container" v-if="!hasError">
      <el-main class="content-main">
        <RouterView v-slot="{ Component, route: currentRoute }">
          <transition :name="pageTransition" mode="out-in">
            <component :is="Component" :key="currentRoute.fullPath" v-if="Component" />
          </transition>
        </RouterView>
      </el-main>
    </el-container>
    <el-container v-else class="error-container">
      <el-main class="error-main">
        <el-result
          icon="error"
          title="页面出现错误"
          subTitle="请刷新页面重试"
        >
          <template #extra>
            <el-button type="primary" @click="reloadPage">刷新页面</el-button>
          </template>
        </el-result>
      </el-main>
    </el-container>
    <Footer />
  </el-container>
</template>

<style scoped>
.app-container {
  min-height: 100vh;
  min-height: 100svh;
  height: 100vh;
  height: 100svh;
  display: flex;
  flex-direction: column;
}

.main-container {
  flex: 1;
  display: flex;
  min-width: 0;
  min-height: 0;
}

.content-main {
  flex: 1;
  min-width: 0;
  min-height: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  overflow-y: auto;
}

.error-container {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.error-main {
  width: 100%;
  display: flex;
  justify-content: center;
}

/* 登录/注册页面切换：纯淡入，不产生任何位移，避免切换瞬间出现滚动条 */
.auth-fade-enter-active,
.auth-fade-leave-active {
  transition: opacity 0.22s ease;
}

.auth-fade-enter-from,
.auth-fade-leave-to {
  opacity: 0;
}
</style>