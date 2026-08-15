<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useNotificationStore } from '@/stores/notification'
import AppIcon from '@/components/ui/AppIcon.vue'
import AppAvatar from '@/components/ui/AppAvatar.vue'

const router = useRouter()
const auth = useAuthStore()
const notif = useNotificationStore()
const menuOpen = ref(false)

async function logout() {
  menuOpen.value = false
  await auth.logout()
  router.push('/login')
}
</script>

<template>
  <header class="flex h-16 shrink-0 items-center justify-between border-b border-line bg-surface px-4 md:px-8">
    <!-- 移动端显示品牌（桌面由侧栏承担） -->
    <div class="flex items-center gap-2.5 md:hidden">
      <div class="flex h-6 w-6 items-center justify-center rounded-sm bg-ink text-white">
        <span class="font-serif text-xs leading-none">A</span>
      </div>
      <span class="font-serif text-base tracking-tight text-ink">Aura</span>
    </div>
    <div class="hidden text-sm text-faint md:block">智能体工作台</div>

    <div class="flex items-center gap-1.5">
      <!-- 通知 -->
      <button
        class="relative rounded-sm p-2 text-muted transition-colors hover:bg-surface-muted hover:text-ink"
        @click="router.push('/notifications')"
      >
        <AppIcon name="bell" :size="18" />
        <span
          v-if="notif.unread > 0"
          class="absolute right-1 top-1 flex h-4 min-w-4 items-center justify-center rounded-full bg-red-text px-1 text-[10px] font-medium leading-none text-white"
        >
          {{ notif.unread > 99 ? '99+' : notif.unread }}
        </span>
      </button>

      <!-- 用户菜单 -->
      <div class="relative">
        <button class="rounded-full" @click="menuOpen = !menuOpen">
          <AppAvatar :src="auth.user?.avatar" :name="auth.user?.username" :size="32" />
        </button>

        <div
          v-if="menuOpen"
          class="fixed inset-0 z-40"
          @click="menuOpen = false"
        ></div>
        <Transition name="menu">
          <div
            v-if="menuOpen"
            class="absolute right-0 top-full z-50 mt-2 w-48 overflow-hidden rounded-lg border border-line bg-surface py-1 shadow-lift"
          >
            <div class="border-b border-line px-4 py-2.5">
              <p class="truncate text-sm font-medium text-ink">{{ auth.user?.username }}</p>
              <p class="text-xs text-faint">{{ auth.user?.email || auth.user?.phone || '—' }}</p>
            </div>
            <button
              class="flex w-full items-center gap-2.5 px-4 py-2 text-sm text-ink transition-colors hover:bg-surface-muted"
              @click="router.push('/profile'); menuOpen = false"
            >
              <AppIcon name="user" :size="16" class="text-muted" />
              个人中心
            </button>
            <button
              v-if="auth.isAdmin"
              class="flex w-full items-center gap-2.5 px-4 py-2 text-sm text-ink transition-colors hover:bg-surface-muted"
              @click="router.push('/admin'); menuOpen = false"
            >
              <AppIcon name="shield" :size="16" class="text-muted" />
              管理后台
            </button>
            <button
              class="flex w-full items-center gap-2.5 px-4 py-2 text-sm text-red-text transition-colors hover:bg-red-bg"
              @click="logout"
            >
              <AppIcon name="log-out" :size="16" />
              退出登录
            </button>
          </div>
        </Transition>
      </div>
    </div>
  </header>
</template>

<style scoped>
.menu-enter-active,
.menu-leave-active {
  transition: opacity 0.15s ease, transform 0.15s ease;
}
.menu-enter-from,
.menu-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}
</style>
