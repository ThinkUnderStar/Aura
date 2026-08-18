<script setup lang="ts">
import AppIcon from '@/components/ui/AppIcon.vue'

const props = withDefaults(defineProps<{ disabled?: boolean; placeholder?: string }>(), {
  disabled: false,
  placeholder: '输入消息，Enter 发送，Shift+Enter 换行',
})
const emit = defineEmits<{ (e: 'send', text: string): void }>()

// 草稿由父级（ChatView）按智能体维护，这里只是读写当前智能体的那份
const model = defineModel<string>({ default: '' })

function submit() {
  const t = model.value.trim()
  if (!t || props.disabled) return
  emit('send', t)
  model.value = ''
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
      v-model="model"
      rows="1"
      class="max-h-40 min-h-[24px] flex-1 resize-none bg-transparent text-sm leading-6 text-ink outline-none placeholder:text-faint"
      :placeholder="placeholder"
      :disabled="disabled"
      @keydown="onKeydown"
    />
    <button
      class="btn-primary shrink-0 !px-3 !py-2"
      :disabled="disabled || !model.trim()"
      title="发送"
      @click="submit"
    >
      <AppIcon name="send" :size="16" />
    </button>
  </div>
</template>
