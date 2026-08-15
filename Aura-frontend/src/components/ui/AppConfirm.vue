<script setup lang="ts">
import AppModal from './AppModal.vue'
import AppIcon from './AppIcon.vue'

withDefaults(
  defineProps<{
    open: boolean
    title?: string
    message?: string
    confirmText?: string
    danger?: boolean
    loading?: boolean
  }>(),
  { title: '确认操作', confirmText: '确认', danger: false, loading: false },
)
const emit = defineEmits<{ (e: 'confirm'): void; (e: 'cancel'): void }>()
</script>

<template>
  <AppModal :open="open" :title="title" @close="emit('cancel')">
    <p v-if="message" class="whitespace-pre-line text-sm leading-6 text-muted">{{ message }}</p>
    <div class="mt-5 flex justify-end gap-2">
      <button class="btn-secondary" :disabled="loading" @click="emit('cancel')">取消</button>
      <button
        :class="danger ? 'btn-danger' : 'btn-primary'"
        :disabled="loading"
        @click="emit('confirm')"
      >
        <AppIcon v-if="loading" name="refresh" :size="14" class="animate-spin" />
        {{ confirmText }}
      </button>
    </div>
  </AppModal>
</template>
