<script setup lang="ts">
import Toaster from '@/components/ui/Toaster.vue'
import { useThemeStore } from '@/stores/theme'

// 全局初始化主题（应用首次挂载即生效，含系统主题监听；切换按钮在 TopBar 用户菜单）
useThemeStore()
</script>

<template>
  <router-view />
  <Toaster />
</template>
