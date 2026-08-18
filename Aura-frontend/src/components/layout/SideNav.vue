<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { MAIN_NAV } from '@/constants/nav'
import AppIcon from '@/components/ui/AppIcon.vue'
import AppAvatar from '@/components/ui/AppAvatar.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

function isActive(path: string) {
  if (path === '/chat') return route.path.startsWith('/chat')
  return route.path.startsWith(path)
}
</script>

<template>
  <aside class="w-60 shrink-0 flex-col border-r border-line bg-surface">
    <!-- 品牌 -->
    <div class="flex h-16 items-center gap-2.5 border-b border-line px-5">
      <div class="flex h-7 w-7 items-center justify-center rounded-sm bg-ink-solid text-white">
        <span class="font-serif text-sm leading-none">A</span>
      </div>
      <span class="font-serif text-lg tracking-tight text-ink">Aura</span>
    </div>

    <!-- 导航 -->
    <nav class="flex-1 space-y-0.5 overflow-y-auto px-3 py-4">
      <router-link
        v-for="item in MAIN_NAV"
        :key="item.path"
        :to="item.path"
        class="flex items-center gap-3 rounded-sm px-3 py-2 text-sm transition-colors"
        :class="isActive(item.path) ? 'bg-surface-muted font-medium text-ink' : 'text-muted hover:bg-surface-muted hover:text-ink'"
      >
        <AppIcon :name="item.icon" :size="17" />
        <span>{{ item.name }}</span>
      </router-link>

      <router-link
        v-if="auth.isAdmin"
        to="/admin"
        class="mt-2 flex items-center gap-3 rounded-sm px-3 py-2 text-sm transition-colors"
        :class="route.path.startsWith('/admin') ? 'bg-surface-muted font-medium text-ink' : 'text-muted hover:bg-surface-muted hover:text-ink'"
      >
        <AppIcon name="shield" :size="17" />
        <span>管理后台</span>
      </router-link>
    </nav>

    <!-- 用户 -->
    <router-link
      to="/profile"
      class="flex items-center gap-3 border-t border-line px-5 py-4 transition-colors hover:bg-surface-muted"
    >
      <AppAvatar :src="auth.user?.avatar" :name="auth.user?.username" :size="32" />
      <div class="min-w-0 flex-1">
        <p class="truncate text-sm font-medium text-ink">{{ auth.user?.username }}</p>
        <p class="text-xs text-faint">{{ auth.isAdmin ? '管理员' : '用户' }}</p>
      </div>
      <AppIcon name="chevron-right" :size="14" class="text-faint" />
    </router-link>
  </aside>
</template>
