<script setup lang="ts">
import { computed, ref } from 'vue'
import type { MessageVO } from '@/types'
import { useAuthStore } from '@/stores/auth'
import { useThemeStore } from '@/stores/theme'
import { toast } from '@/stores/toast'
import { formatTime } from '@/utils/format'
import { resolveAuraLogo } from '@/utils/auraLogo'
import Markdown from '@/components/ui/Markdown.vue'
import AppAvatar from '@/components/ui/AppAvatar.vue'
import AppIcon from '@/components/ui/AppIcon.vue'

const props = defineProps<{ message: MessageVO; disabled?: boolean }>()
const emit = defineEmits<{
  (e: 'edit-submit', message: MessageVO, newContent: string): void
}>()
const auth = useAuthStore()
const theme = useThemeStore()

// Aura 智能体头像：按当前主题切换（暗色→白主体，亮色→黑主体）
const auraAvatar = computed(() => resolveAuraLogo(theme.isDark))

const isUser = computed(() => props.message.role === 'user')
const isTool = computed(() => props.message.role === 'tool_confirm')

// 解析工具调用确认的 JSON 载荷，用于结构化展示而非渲染原始 JSON 字符串
const toolPayload = computed(() => {
  if (!isTool.value) return null
  try {
    const obj = JSON.parse(props.message.content)
    if (obj && typeof obj.question === 'string') return obj as { question: string; options?: string[] }
  } catch {
    /* 解析失败按纯文本兜底 */
  }
  return null
})

const toolQuestion = computed(() => toolPayload.value?.question || props.message.content)

const ACTION_LABEL: Record<string, string> = {
  approve: '已确认执行',
  reject: '已拒绝',
  edit: '已修改为',
  continue: '已继续',
  cancel: '已取消',
}
const actionLabel = computed(() => {
  const a = props.message.action
  return a ? ACTION_LABEL[a] || a : ''
})

// 编辑（回溯）：仅用户消息可编辑，重新生成后续对话
const editing = ref(false)
const draft = ref('')

function startEdit() {
  draft.value = props.message.content
  editing.value = true
}

function cancelEdit() {
  editing.value = false
  draft.value = ''
}

function submitEdit() {
  const text = draft.value.trim()
  if (!text) return
  editing.value = false
  emit('edit-submit', props.message, text)
}

function copy() {
  navigator.clipboard?.writeText(props.message.content).then(
    () => toast.success('已复制'),
    () => toast.error('复制失败'),
  )
}
</script>

<template>
  <!-- 用户消息：右对齐，墨黑气泡 -->
  <div v-if="isUser" class="group flex justify-end gap-3">
    <div class="flex max-w-[80%] flex-col items-end">
      <!-- 编辑态 -->
      <div v-if="editing" class="rounded-lg rounded-tr-sm bg-ink-solid px-3 py-2.5">
        <textarea
          v-model="draft"
          rows="3"
          class="block w-80 max-w-full resize-y rounded-sm border border-white/20 bg-transparent px-2 py-1 text-sm leading-6 text-white placeholder-white/40 focus:border-white/60 focus:outline-none"
          placeholder="请输入内容"
        ></textarea>
        <div class="mt-2 flex justify-end gap-2">
          <button
            class="rounded-sm px-2.5 py-1 text-xs text-white/70 transition-colors hover:bg-white/10 hover:text-white"
            @click="cancelEdit"
          >
            取消
          </button>
          <button
            class="rounded-sm bg-white px-2.5 py-1 text-xs font-medium text-ink-solid transition-colors hover:bg-white/90 disabled:opacity-50"
            :disabled="!draft.trim()"
            @click="submitEdit"
          >
            保存并重新生成
          </button>
        </div>
      </div>
      <!-- 普通态 -->
      <div v-else class="rounded-lg rounded-tr-sm bg-ink-solid px-4 py-2.5 text-sm leading-6 text-white">
        <span class="whitespace-pre-wrap break-words">{{ message.content }}</span>
      </div>
      <div class="mt-1 flex items-center gap-2 text-xs text-faint">
        <button
          v-if="message.id > 0 && !disabled"
          class="flex items-center gap-1 opacity-0 transition-opacity hover:text-ink group-hover:opacity-100"
          @click="startEdit"
        >
          <AppIcon name="edit" :size="12" />
          编辑
        </button>
        <span>{{ formatTime(message.createTime, false) }}</span>
      </div>
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
      <div class="mt-1 rounded-lg border border-line bg-surface px-4 py-2.5">
        <p class="whitespace-pre-wrap break-words text-sm leading-6 text-ink">{{ toolQuestion }}</p>
        <div
          v-if="message.action"
          class="mt-2 flex items-center gap-1.5 border-t border-line pt-2 text-xs text-muted"
        >
          <AppIcon :name="message.action === 'reject' ? 'x' : 'check'" :size="13" />
          <span>{{ actionLabel }}</span>
          <span v-if="message.action === 'edit' && message.editedContent" class="break-words text-ink">
            {{ message.editedContent }}
          </span>
        </div>
      </div>
    </div>
  </div>

  <!-- 助手消息：左对齐，Markdown 渲染 -->
  <div v-else class="group flex gap-3">
    <div class="h-10 w-10 shrink-0 overflow-hidden rounded-full bg-surface p-1.5">
      <img :src="auraAvatar" alt="Aura" class="h-full w-full object-contain" />
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
