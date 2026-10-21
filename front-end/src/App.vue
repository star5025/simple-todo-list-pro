<script setup>
import { RouterView } from 'vue-router'
import Footer from './components/Footer.vue'
import { onErrorCaptured, ref } from 'vue'
import { ElMessage } from 'element-plus'

const hasError = ref(false)

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
        <RouterView />
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
</style>