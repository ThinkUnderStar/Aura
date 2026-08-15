<script setup lang="ts">
import { computed } from 'vue'
import AppIcon from './AppIcon.vue'

const props = withDefaults(defineProps<{ page: number; total: number; size?: number }>(), { size: 20 })
const emit = defineEmits<{ (e: 'change', page: number): void }>()

const pages = computed(() => Math.max(1, Math.ceil(props.total / props.size)))

function go(p: number) {
  if (p < 1 || p > pages.value || p === props.page) return
  emit('change', p)
}
</script>

<template>
  <div v-if="total > 0" class="flex items-center justify-between px-1 py-3 text-xs text-faint">
    <span>共 {{ total }} 条</span>
    <div class="flex items-center gap-1">
      <button class="btn-ghost p-1.5" :disabled="page <= 1" @click="go(page - 1)">
        <AppIcon name="chevron-left" :size="14" />
      </button>
      <span class="font-mono text-ink">{{ page }} / {{ pages }}</span>
      <button class="btn-ghost p-1.5" :disabled="page >= pages" @click="go(page + 1)">
        <AppIcon name="chevron-right" :size="14" />
      </button>
    </div>
  </div>
</template>
