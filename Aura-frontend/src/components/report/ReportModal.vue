<script setup lang="ts">
import { ref, watch } from 'vue'
import { reportApi } from '@/api'
import { toast } from '@/stores/toast'
import { REPORT_REASON } from '@/constants/enums'
import AppIcon from '@/components/ui/AppIcon.vue'
import AppModal from '@/components/ui/AppModal.vue'

const props = withDefaults(
  defineProps<{
    open: boolean
    targetType: 'user' | 'workspace' | 'document'
    targetId: number
    targetName?: string
  }>(),
  { targetName: '' },
)
const emit = defineEmits<{ (e: 'close'): void }>()

const reason = ref('')
const description = ref('')
const submitting = ref(false)

// 每次打开时重置表单
watch(
  () => props.open,
  (open) => {
    if (open) {
      reason.value = ''
      description.value = ''
    }
  },
)

async function submit() {
  if (!reason.value) return toast.error('请选择举报原因')
  if (!description.value.trim()) return toast.error('请填写举报描述')
  submitting.value = true
  try {
    await reportApi.submit({
      targetType: props.targetType,
      targetId: props.targetId,
      reason: reason.value,
      description: description.value.trim(),
    })
    toast.success('举报已提交，管理员将尽快处理')
    emit('close')
  } catch {
    /* 拦截器已提示 */
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <AppModal :open="open" :title="`举报${targetName ? `「${targetName}」` : ''}`" width="max-w-lg" @close="emit('close')">
    <div class="space-y-4">
      <div>
        <label class="label">举报原因</label>
        <div class="flex flex-wrap gap-2">
          <button
            v-for="(label, key) in REPORT_REASON"
            :key="key"
            class="rounded-sm px-3 py-1.5 text-sm transition-colors"
            :class="reason === key ? 'bg-ink-solid text-white' : 'text-muted hover:bg-surface-muted hover:text-ink'"
            @click="reason = key"
          >
            {{ label }}
          </button>
        </div>
      </div>
      <div>
        <label class="label">举报描述</label>
        <textarea v-model="description" class="input resize-none" rows="4" placeholder="请详细描述举报原因，便于管理员审核" />
      </div>
    </div>
    <div class="mt-5 flex items-center justify-between gap-2">
      <span class="text-xs text-faint">处理结果将通过「通知」告知你</span>
      <div class="flex gap-2">
        <button class="btn-secondary" @click="emit('close')">取消</button>
        <button class="btn-primary" :disabled="submitting" @click="submit">
          <AppIcon v-if="submitting" name="refresh" :size="14" class="animate-spin" />
          提交举报
        </button>
      </div>
    </div>
  </AppModal>
</template>
