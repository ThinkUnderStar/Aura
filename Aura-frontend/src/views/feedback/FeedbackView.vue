<script setup lang="ts">
import { ref } from 'vue'
import { feedbackApi } from '@/api'
import { toast } from '@/stores/toast'
import { FEEDBACK_TYPE } from '@/constants/enums'
import AppIcon from '@/components/ui/AppIcon.vue'

// 反馈页只负责提交；管理员回复后通过「通知」告知，不再单独维护反馈历史列表
const form = ref({ type: 'bug', title: '', content: '', contact: '' })
const submitting = ref(false)

async function submit() {
  if (!form.value.title.trim()) return toast.error('请输入标题')
  if (!form.value.content.trim()) return toast.error('请输入内容')
  submitting.value = true
  try {
    await feedbackApi.submit({
      type: form.value.type,
      title: form.value.title.trim(),
      content: form.value.content.trim(),
      contact: form.value.contact.trim() || undefined,
    })
    form.value = { type: 'bug', title: '', content: '', contact: '' }
    toast.success('反馈已提交，回复将通过「通知」告知你')
  } catch {
    /* 拦截器已提示 */
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="mx-auto max-w-lg px-4 py-8 md:px-8">
    <div class="mb-6">
      <h1 class="text-lg font-medium text-ink">反馈</h1>
      <p class="mt-1 text-sm text-faint">提交问题与建议，管理员回复后会通过「通知」告知你。</p>
    </div>

    <div class="card space-y-4 p-6">
      <div>
        <label class="label">类型</label>
        <div class="flex flex-wrap gap-2">
          <button
            v-for="(label, key) in FEEDBACK_TYPE"
            :key="key"
            class="rounded-sm px-3 py-1.5 text-sm transition-colors"
            :class="form.type === key ? 'bg-ink-solid text-white' : 'text-muted hover:bg-surface-muted hover:text-ink'"
            @click="form.type = key as string"
          >
            {{ label }}
          </button>
        </div>
      </div>
      <div>
        <label class="label">标题</label>
        <input v-model="form.title" class="input" placeholder="简要描述问题或建议" />
      </div>
      <div>
        <label class="label">内容</label>
        <textarea v-model="form.content" class="input resize-none" rows="5" placeholder="详细描述你的问题或建议" />
      </div>
      <div>
        <label class="label">联系方式（可选）</label>
        <input v-model="form.contact" class="input" placeholder="邮箱或手机号，便于我们联系" />
      </div>
      <div class="flex justify-end pt-1">
        <button class="btn-primary" :disabled="submitting" @click="submit">
          <AppIcon v-if="submitting" name="refresh" :size="14" class="animate-spin" />
          提交反馈
        </button>
      </div>
    </div>
  </div>
</template>
