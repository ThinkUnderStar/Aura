<script setup lang="ts">
import { onBeforeUnmount, onMounted } from 'vue'
import SideNav from './SideNav.vue'
import TopBar from './TopBar.vue'
import BottomTabBar from './BottomTabBar.vue'
import { useNotificationStore } from '@/stores/notification'

const notif = useNotificationStore()

// 通知角标不能只靠进通知页刷新：登录后定时轮询未读数，切回标签页时立即刷新
const POLL_MS = 30_000
let timer: number | undefined

function refreshUnread() {
  notif.refreshUnread()
}

function onVisibility() {
  if (document.visibilityState === 'visible') refreshUnread()
}

onMounted(() => {
  refreshUnread() // 进入应用立即拉一次
  timer = window.setInterval(refreshUnread, POLL_MS)
  document.addEventListener('visibilitychange', onVisibility)
})

onBeforeUnmount(() => {
  if (timer !== undefined) window.clearInterval(timer)
  document.removeEventListener('visibilitychange', onVisibility)
})
</script>

<template>
  <div class="flex h-screen overflow-hidden">
    <SideNav class="hidden md:flex" />
    <div class="flex min-w-0 flex-1 flex-col">
      <TopBar />
      <main class="flex-1 overflow-y-auto pb-20 md:pb-0">
        <KeepAlive include="ChatView">
          <router-view />
        </KeepAlive>
      </main>
    </div>
    <BottomTabBar class="md:hidden" />
  </div>
</template>
