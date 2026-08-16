<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { InterruptPayload } from '@/api/sse'
import AppIcon from '@/components/ui/AppIcon.vue'

const props = defineProps<{ payload: InterruptPayload | null }>()
const emit = defineEmits<{
  (e: 'choose', option: string, edition?: string): void
  (e: 'cancel'): void
}>()

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

// 编辑态：点击「修改后执行」后展开输入框
const editing = ref(false)
const edition = ref('')

// 从问题文本中提取原始内容用于预填（形如「是否同意将: X 添加进...」），提取失败则留空
function extractOriginal(question: string): string {
  const m = question.match(/将[:：]\s*([\s\S]*?)\s*添加进/)
  return m ? m[1].trim() : ''
}

// 出现新的中断时重置编辑态
watch(
  () => props.payload,
  (p) => {
    editing.value = false
    edition.value = extractOriginal(p?.question || '')
  },
)

function choose(o: string) {
  if (o === 'edit') {
    editing.value = true
    return
  }
  emit('choose', o)
}

function submitEdit() {
  const text = edition.value.trim()
  if (!text) return
  emit('choose', 'edit', text)
}

function cancelEdit() {
  editing.value = false
  edition.value = extractOriginal(props.payload?.question || '')
}
</script>

<template>
  <div class="flex gap-3">
    <div class="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-surface-muted text-muted">
      <AppIcon name="sparkle" :size="15" />
    </div>
    <div class="min-w-0 max-w-[80%]">
      <p class="text-xs text-faint">工具调用</p>
      <div class="mt-1 rounded-lg border border-line bg-surface px-4 py-2.5">
        <p class="whitespace-pre-wrap break-words text-sm leading-6 text-ink">
          {{ payload?.question || '是否继续执行该操作？' }}
        </p>

        <!-- 编辑态：显示输入框 -->
        <div v-if="editing" class="mt-3">
          <textarea
            v-model="edition"
            rows="3"
            class="input min-h-24 resize-y"
            placeholder="请输入修改后的内容"
          ></textarea>
          <div class="mt-2 flex justify-end gap-2">
            <button class="btn-secondary !px-3 !py-1.5 text-xs" @click="cancelEdit">返回</button>
            <button class="btn-primary !px-3 !py-1.5 text-xs" :disabled="!edition.trim()" @click="submitEdit">确认修改</button>
          </div>
        </div>

        <!-- 选项按钮 -->
        <div v-else class="mt-3 flex flex-wrap gap-2">
          <button
            v-for="o in options"
            :key="o"
            class="btn-secondary !px-3 !py-1.5 text-xs"
            :class="{ '!bg-ink !text-white hover:!bg-neutral-800': o === 'approve' || o === 'continue' }"
            @click="choose(o)"
          >
            <AppIcon :name="o === 'reject' ? 'x' : 'check'" :size="13" />
            {{ label(o) }}
          </button>
          <button class="btn-ghost !px-3 !py-1.5 text-xs" @click="emit('cancel')">
            <AppIcon name="x" :size="13" />
            忽略
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
