<script setup lang="ts">
import AppIcon from './AppIcon.vue'
withDefaults(defineProps<{ size?: number; label?: string }>(), { size: 16 })
</script>

<template>
  <span class="inline-flex items-center gap-2 text-sm text-faint">
    <AppIcon name="refresh" :size="size" class="animate-spin" />
    <span v-if="label">{{ label }}</span>
  </span>
</template>
