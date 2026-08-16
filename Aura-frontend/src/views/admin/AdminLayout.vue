<script setup lang="ts">
import { useRouter } from 'vue-router'
import AppIcon from '@/components/ui/AppIcon.vue'

const router = useRouter()

const tabs = [
  { name: '团队', path: '/admin/workspaces', icon: 'users' },
  { name: '管理员', path: '/admin/admins', icon: 'shield' },
  { name: '反馈', path: '/admin/feedbacks', icon: 'mail' },
  { name: '举报', path: '/admin/reports', icon: 'flag' },
]
</script>

<template>
  <div class="flex h-screen flex-col">
    <header class="flex h-16 shrink-0 items-center justify-between border-b border-line bg-surface px-4 md:px-8">
      <div class="flex items-center gap-3">
        <div class="flex h-7 w-7 items-center justify-center rounded-sm bg-ink text-white">
          <span class="font-serif text-sm leading-none">A</span>
        </div>
        <div class="flex items-center gap-2">
          <span class="font-serif text-base tracking-tight text-ink">Aura</span>
          <span class="text-faint">/</span>
          <span class="text-sm text-muted">管理后台</span>
        </div>
      </div>
      <button class="flex items-center gap-1.5 text-sm text-muted transition-colors hover:text-ink" @click="router.push('/chat')">
        <AppIcon name="arrow-left" :size="15" />
        返回工作台
      </button>
    </header>

    <nav class="flex shrink-0 gap-1 border-b border-line bg-surface px-4 md:px-8">
      <router-link
        v-for="t in tabs"
        :key="t.path"
        :to="t.path"
        class="-mb-px flex items-center gap-1.5 border-b-2 px-4 py-3 text-sm transition-colors"
        :class="$route.path.startsWith(t.path) ? 'border-ink font-medium text-ink' : 'border-transparent text-muted hover:text-ink'"
      >
        <AppIcon :name="t.icon" :size="15" />
        {{ t.name }}
      </router-link>
    </nav>

    <main class="flex-1 overflow-y-auto">
      <router-view />
    </main>
  </div>
</template>
