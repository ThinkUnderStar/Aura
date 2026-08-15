<script setup lang="ts">
import { useRoute } from 'vue-router'
import { TAB_NAV } from '@/constants/nav'
import AppIcon from '@/components/ui/AppIcon.vue'

const route = useRoute()

function isActive(path: string) {
  if (path === '/chat') return route.path.startsWith('/chat')
  if (path === '/profile') return route.path.startsWith('/profile')
  return route.path.startsWith(path)
}
</script>

<template>
  <nav
    class="fixed inset-x-0 bottom-0 z-40 flex border-t border-line bg-surface pb-[env(safe-area-inset-bottom)]"
  >
    <router-link
      v-for="item in TAB_NAV"
      :key="item.path"
      :to="item.path"
      class="flex flex-1 flex-col items-center gap-0.5 py-2.5 text-[11px] transition-colors"
      :class="isActive(item.path) ? 'text-ink' : 'text-faint'"
    >
      <AppIcon :name="item.icon" :size="20" />
      <span>{{ item.name }}</span>
    </router-link>
  </nav>
</template>
