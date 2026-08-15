<script setup lang="ts">
import { computed } from 'vue'
import type { InterruptPayload } from '@/api/sse'
import AppModal from '@/components/ui/AppModal.vue'
import AppIcon from '@/components/ui/AppIcon.vue'

const props = defineProps<{ open: boolean; payload: InterruptPayload | null }>()
const emit = defineEmits<{ (e: 'choose', option: string): void; (e: 'cancel'): void }>()

// 常见选项 -> 友好文案；其余原样展示
const LABEL: Record<string, string> = {
  approve: '确认执行',
  reject: '拒绝',
  edit: '修改后执行',
  continue: '继续',
  cancel: '取消',
}

const options = computed(() => (props.payload?.options?.length ? props.payload.options : ['approve', 'reject']))
function label(o: string) {
  return LABEL[o] || o
}
</script>

<template>
  <AppModal :open="open" title="工具调用确认" @close="emit('cancel')">
    <p class="text-sm leading-6 text-ink">{{ payload?.question || '是否继续执行该操作？' }}</p>
    <div class="mt-5 flex flex-wrap gap-2">
      <button
        v-for="o in options"
        :key="o"
        class="btn-secondary"
        :class="{ '!bg-ink !text-white hover:!bg-neutral-800': o === 'approve' || o === 'continue' }"
        @click="emit('choose', o)"
      >
        <AppIcon :name="o === 'reject' ? 'x' : 'check'" :size="14" />
        {{ label(o) }}
      </button>
      <button class="btn-ghost" @click="emit('cancel')">
        <AppIcon name="x" :size="14" />
        忽略
      </button>
    </div>
  </AppModal>
</template>
