<script setup lang="ts">
import AppIcon from './AppIcon.vue'

withDefaults(defineProps<{ icon?: string; title?: string; description?: string }>(), {
  icon: 'info',
  title: '暂无内容',
})
</script>

<template>
  <div class="flex flex-col items-center justify-center py-16 text-center">
    <div class="mb-3 flex h-11 w-11 items-center justify-center rounded-lg border border-line bg-surface-muted text-faint">
      <AppIcon :name="icon" :size="20" />
    </div>
    <p class="text-sm font-medium text-ink">{{ title }}</p>
    <p v-if="description" class="mt-1 max-w-xs text-xs leading-5 text-faint">{{ description }}</p>
    <div v-if="$slots.default" class="mt-4">
      <slot />
    </div>
  </div>
</template>
