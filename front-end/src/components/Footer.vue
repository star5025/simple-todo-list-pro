<template>
  <el-footer class="footer">
    <span class="version">v{{ version }}</span>
    <a class="author" href="https://github.com/star5025" target="_blank" rel="noopener noreferrer" aria-label="star5025 GitHub">
      <el-avatar :size="22" src="https://github.com/star5025.png" alt="star5025">S</el-avatar>
      <span>star5025</span>
    </a>
    <a class="repository" href="https://github.com/star5025/simple-todo-list-pro" target="_blank" rel="noopener noreferrer">GitHub ↗</a>
  </el-footer>
</template>

<script setup>
import { version } from '../../package.json'
</script>

<style scoped>
.footer { display: flex; align-items: center; justify-content: space-between; gap: 16px; height: auto; min-height: 56px; padding: 12px 32px max(12px, env(safe-area-inset-bottom)); border-top: 1px solid var(--app-border); background: var(--app-surface); margin-top: auto; font-size: 12px; color: var(--app-muted); }
a { color: var(--app-muted); text-decoration: none; display: inline-flex; align-items: center; gap: 7px; min-height: 32px; }
a:hover { color: var(--el-color-primary); }
@media (max-width: 760px) { .footer { padding-left: 16px; padding-right: 16px; } a { min-height: 44px; } }
</style>
