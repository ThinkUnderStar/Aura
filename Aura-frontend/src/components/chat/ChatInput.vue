<script setup lang="ts">
import { ref } from 'vue'
import AppIcon from '@/components/ui/AppIcon.vue'

const props = withDefaults(defineProps<{ disabled?: boolean; placeholder?: string }>(), {
  disabled: false,
  placeholder: '输入消息，Enter 发送，Shift+Enter 换行',
})
const emit = defineEmits<{ (e: 'send', text: string): void }>()

const text = ref('')

function submit() {
  const t = text.value.trim()
  if (!t || props.disabled) return
  emit('send', t)
  text.value = ''
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) {
    e.preventDefault()
    submit()
  }
}
</script>

<template>
  <div class="flex items-end gap-2 rounded-lg border border-line bg-surface px-3 py-2 transition-colors focus-within:border-line-strong">
    <textarea
      v-model="text"
      rows="1"
      class="max-h-40 min-h-[24px] flex-1 resize-none bg-transparent text-sm leading-6 text-ink outline-none placeholder:text-faint"
      :placeholder="placeholder"
      :disabled="disabled"
      @keydown="onKeydown"
    />
    <button
      class="btn-primary shrink-0 !px-3 !py-2"
      :disabled="disabled || !text.trim()"
      title="发送"
      @click="submit"
    >
      <AppIcon name="send" :size="16" />
    </button>
  </div>
</template>
