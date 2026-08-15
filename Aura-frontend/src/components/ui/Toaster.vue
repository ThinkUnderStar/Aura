<script setup lang="ts">
import { useToast } from '@/stores/toast'
import AppIcon from './AppIcon.vue'

const { items, dismiss } = useToast()

const TONE = {
  success: 'text-green-text',
  error: 'text-red-text',
  info: 'text-blue-text',
} as const
const ICON = { success: 'check', error: 'alert', info: 'info' } as const
</script>

<template>
  <Teleport to="body">
    <div class="pointer-events-none fixed inset-x-0 top-4 z-[60] flex flex-col items-center gap-2 px-4">
      <TransitionGroup name="toast">
        <div
          v-for="t in items"
          :key="t.id"
          class="pointer-events-auto flex w-full max-w-sm items-center gap-2.5 rounded-lg border border-line bg-surface px-4 py-3 text-sm shadow-lift"
        >
          <AppIcon :name="ICON[t.type]" :size="16" :class="TONE[t.type]" />
          <span class="flex-1 text-ink">{{ t.message }}</span>
          <button class="text-faint hover:text-ink" @click="dismiss(t.id)">
            <AppIcon name="x" :size="14" />
          </button>
        </div>
      </TransitionGroup>
    </div>
  </Teleport>
</template>

<style scoped>
.toast-enter-active,
.toast-leave-active {
  transition: all 0.25s ease;
}
.toast-enter-from {
  opacity: 0;
  transform: translateY(-8px);
}
.toast-leave-to {
  opacity: 0;
}
</style>
