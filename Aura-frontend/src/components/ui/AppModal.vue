<script setup lang="ts">
import { watch, onBeforeUnmount } from 'vue'
import AppIcon from './AppIcon.vue'

const props = withDefaults(defineProps<{ open: boolean; title?: string; width?: string }>(), {
  width: 'max-w-md',
})
const emit = defineEmits<{ (e: 'close'): void }>()

function onKey(e: KeyboardEvent) {
  if (e.key === 'Escape') emit('close')
}
watch(
  () => props.open,
  (v) => {
    if (v) document.addEventListener('keydown', onKey)
    else document.removeEventListener('keydown', onKey)
  },
)
onBeforeUnmount(() => document.removeEventListener('keydown', onKey))
</script>

<template>
  <Teleport to="body">
    <Transition name="fade">
      <div
        v-if="open"
        class="fixed inset-0 z-50 flex items-end justify-center bg-black/20 p-0 sm:items-center sm:p-4"
        @click.self="emit('close')"
      >
        <div
          class="w-full rounded-t-lg border border-line bg-surface shadow-lift sm:rounded-lg"
          :class="width"
        >
          <div v-if="title" class="flex items-center justify-between border-b border-line px-5 py-4">
            <h3 class="text-sm font-medium text-ink">{{ title }}</h3>
            <button class="btn-ghost -mr-2 p-1.5" @click="emit('close')">
              <AppIcon name="x" :size="16" />
            </button>
          </div>
          <div class="p-5">
            <slot />
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
