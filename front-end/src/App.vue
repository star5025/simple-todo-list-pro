<script setup>
import { RouterView, useRoute } from 'vue-router'
import Footer from './components/Footer.vue'
import { onErrorCaptured, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'

const route = useRoute()
const hasError = ref(false)

// 登录/注册之间使用方向明确的滑动过渡，其他页面保持轻量淡入。
const isAuthPath = path => path.startsWith('/login') || path.startsWith('/register')
const pageTransition = ref('el-fade-in')
watch(() => route.fullPath, (to, from) => {
  if (from && isAuthPath(from) && isAuthPath(to) && to !== from) {
    pageTransition.value = to.startsWith('/register') ? 'auth-slide-left' : 'auth-slide-right'
  } else {
    pageTransition.value = 'el-fade-in'
  }
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
  display: flex;
  flex-direction: column;
}

.main-container {
  flex: 1;
  display: flex;
  min-width: 0;
}

.content-main {
  flex: 1;
  min-width: 0;
  padding: 0;
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

/* 登录/注册页面切换：前进向右滑入，返回向左滑入，配合淡入让页面变化明显可感知 */
.auth-slide-left-enter-active,
.auth-slide-right-enter-active {
  transition: opacity 0.2s ease, transform 0.26s ease;
}

.auth-slide-left-leave-active,
.auth-slide-right-leave-active {
  transition: opacity 0.16s ease, transform 0.2s ease;
}

.auth-slide-left-enter-from {
  opacity: 0;
  transform: translateX(40px);
}

.auth-slide-left-leave-to {
  opacity: 0;
  transform: translateX(-40px);
}

.auth-slide-right-enter-from {
  opacity: 0;
  transform: translateX(-40px);
}

.auth-slide-right-leave-to {
  opacity: 0;
  transform: translateX(40px);
}
</style>