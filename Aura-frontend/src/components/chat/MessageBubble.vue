<script setup lang="ts">
import { computed } from 'vue'
import type { MessageVO } from '@/types'
import { useAuthStore } from '@/stores/auth'
import { toast } from '@/stores/toast'
import { formatTime } from '@/utils/format'
import Markdown from '@/components/ui/Markdown.vue'
import AppAvatar from '@/components/ui/AppAvatar.vue'
import AppIcon from '@/components/ui/AppIcon.vue'

const props = defineProps<{ message: MessageVO }>()
const auth = useAuthStore()

const isUser = computed(() => props.message.role === 'user')
const isTool = computed(() => props.message.role === 'tool_confirm')

function copy() {
  navigator.clipboard?.writeText(props.message.content).then(
    () => toast.success('已复制'),
    () => toast.error('复制失败'),
  )
}
</script>

<template>
  <!-- 用户消息：右对齐，墨黑气泡 -->
  <div v-if="isUser" class="flex justify-end gap-3">
    <div class="flex max-w-[80%] flex-col items-end">
      <div class="rounded-lg rounded-tr-sm bg-ink px-4 py-2.5 text-sm leading-6 text-white">
        <span class="whitespace-pre-wrap break-words">{{ message.content }}</span>
      </div>
      <span class="mt-1 text-xs text-faint">{{ formatTime(message.createTime, false) }}</span>
    </div>
    <AppAvatar :src="auth.user?.avatar" :name="auth.user?.username" :size="32" />
  </div>

  <!-- 工具调用确认：系统风格 -->
  <div v-else-if="isTool" class="flex gap-3">
    <div class="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-surface-muted text-muted">
      <AppIcon name="sparkle" :size="15" />
    </div>
    <div class="min-w-0 max-w-[80%]">
      <p class="text-xs text-faint">工具调用</p>
      <div
        class="mt-1 whitespace-pre-wrap break-words rounded-lg border border-line bg-surface px-4 py-2.5 font-mono text-xs leading-5 text-muted"
      >
        {{ message.content }}
      </div>
    </div>
  </div>

  <!-- 助手消息：左对齐，Markdown 渲染 -->
  <div v-else class="group flex gap-3">
    <div class="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-ink text-white">
      <span class="font-serif text-xs leading-none">A</span>
    </div>
    <div class="min-w-0 max-w-[80%]">
      <p class="text-xs text-faint">Aura</p>
      <div class="mt-1 rounded-lg rounded-tl-sm border border-line bg-surface px-4 py-3">
        <Markdown :content="message.content" />
      </div>
      <div class="mt-1 flex items-center gap-3 text-xs text-faint opacity-0 transition-opacity group-hover:opacity-100">
        <button class="flex items-center gap-1 transition-colors hover:text-ink" @click="copy">
          <AppIcon name="copy" :size="12" />复制
        </button>
        <span>{{ formatTime(message.createTime, false) }}</span>
      </div>
    </div>
  </div>
</template>
