<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { feedbackApi } from '@/api'
import { toast } from '@/stores/toast'
import type { Feedback } from '@/types'
import { FEEDBACK_STATUS, FEEDBACK_TYPE } from '@/constants/enums'
import { formatTime } from '@/utils/format'
import AppIcon from '@/components/ui/AppIcon.vue'
import AppBadge from '@/components/ui/AppBadge.vue'
import AppModal from '@/components/ui/AppModal.vue'
import AppEmpty from '@/components/ui/AppEmpty.vue'
import AppSpinner from '@/components/ui/AppSpinner.vue'
import AppPagination from '@/components/ui/AppPagination.vue'

const items = ref<Feedback[]>([])
const total = ref(0)
const page = ref(1)
const size = 10
const loading = ref(false)

const showForm = ref(false)
const form = ref({ type: 'bug', title: '', content: '', contact: '' })
const submitting = ref(false)
const detail = ref<Feedback | null>(null)
const detailLoading = ref(false)

const STATUS_TONE: Record<number, 'yellow' | 'blue' | 'green' | 'gray'> = {
  0: 'yellow',
  1: 'blue',
  2: 'green',
  3: 'gray',
}

async function load() {
  loading.value = true
  try {
    const { data } = await feedbackApi.my(page.value, size)
    items.value = data.code === 200 ? data.data.records : []
    total.value = data.code === 200 ? data.data.total : 0
  } catch {
    items.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

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
    showForm.value = false
    form.value = { type: 'bug', title: '', content: '', contact: '' }
    toast.success('反馈已提交')
    await load()
  } catch {
    /* 拦截器已提示 */
  } finally {
    submitting.value = false
  }
}

async function openDetail(f: Feedback) {
  detail.value = f
  detailLoading.value = true
  try {
    const { data } = await feedbackApi.detail(f.id)
    if (data.code === 200) detail.value = data.data
  } catch {
    /* 拦截器已提示 */
  } finally {
    detailLoading.value = false
  }
}

function onPage(p: number) {
  page.value = p
  load()
}

onMounted(load)
</script>

<template>
  <div class="mx-auto max-w-3xl px-4 py-8 md:px-8">
    <div class="mb-6 flex items-center justify-between">
      <div>
        <h1 class="text-lg font-medium text-ink">反馈</h1>
        <p class="mt-1 text-sm text-faint">提交问题与建议，我们会尽快回复。</p>
      </div>
      <button class="btn-primary" @click="showForm = true">
        <AppIcon name="plus" :size="15" />
        提交反馈
      </button>
    </div>

    <div class="card overflow-hidden">
      <div v-if="loading" class="flex justify-center py-16">
        <AppSpinner label="加载中…" />
      </div>
      <div v-else-if="!items.length" class="py-10">
        <AppEmpty icon="mail" title="暂无反馈" description="提交第一条反馈，帮助我们改进。">
          <button class="btn-primary" @click="showForm = true">提交反馈</button>
        </AppEmpty>
      </div>
      <ul v-else class="divide-y divide-line">
        <li
          v-for="f in items"
          :key="f.id"
          class="cursor-pointer px-5 py-4 transition-colors hover:bg-surface-muted"
          @click="openDetail(f)"
        >
          <div class="flex items-center gap-2">
            <AppBadge tone="gray">{{ FEEDBACK_TYPE[f.type] || f.type }}</AppBadge>
            <AppBadge :tone="STATUS_TONE[f.status] ?? 'gray'">{{ FEEDBACK_STATUS[f.status] ?? '未知' }}</AppBadge>
            <span class="ml-auto text-xs text-faint">{{ formatTime(f.createTime, false) }}</span>
          </div>
          <p class="mt-2 text-sm font-medium text-ink">{{ f.title }}</p>
          <p class="mt-1 line-clamp-2 text-sm leading-6 text-muted">{{ f.content }}</p>
        </li>
      </ul>
      <div v-if="total > 0" class="border-t border-line px-4">
        <AppPagination :page="page" :total="total" :size="size" @change="onPage" />
      </div>
    </div>

    <!-- 提交表单 -->
    <AppModal :open="showForm" title="提交反馈" width="max-w-lg" @close="showForm = false">
      <div class="space-y-4">
        <div>
          <label class="label">类型</label>
          <div class="flex flex-wrap gap-2">
            <button
              v-for="(label, key) in FEEDBACK_TYPE"
              :key="key"
              class="rounded-sm px-3 py-1.5 text-sm transition-colors"
              :class="form.type === key ? 'bg-ink text-white' : 'text-muted hover:bg-surface-muted hover:text-ink'"
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
          <textarea v-model="form.content" class="input resize-none" rows="4" placeholder="详细描述你的问题或建议" />
        </div>
        <div>
          <label class="label">联系方式（可选）</label>
          <input v-model="form.contact" class="input" placeholder="邮箱或手机号，便于我们回复" />
        </div>
      </div>
      <div class="mt-5 flex justify-end gap-2">
        <button class="btn-secondary" @click="showForm = false">取消</button>
        <button class="btn-primary" :disabled="submitting" @click="submit">
          <AppIcon v-if="submitting" name="refresh" :size="14" class="animate-spin" />
          提交
        </button>
      </div>
    </AppModal>

    <!-- 详情 -->
    <AppModal :open="!!detail" title="反馈详情" width="max-w-lg" @close="detail = null">
      <div v-if="detailLoading" class="flex justify-center py-8">
        <AppSpinner />
      </div>
      <div v-else-if="detail" class="space-y-4">
        <div class="flex items-center gap-2">
          <AppBadge tone="gray">{{ FEEDBACK_TYPE[detail.type] || detail.type }}</AppBadge>
          <AppBadge :tone="STATUS_TONE[detail.status] ?? 'gray'">{{ FEEDBACK_STATUS[detail.status] ?? '未知' }}</AppBadge>
          <span class="ml-auto text-xs text-faint">{{ formatTime(detail.createTime) }}</span>
        </div>
        <div>
          <p class="text-sm font-medium text-ink">{{ detail.title }}</p>
          <p class="mt-2 whitespace-pre-line text-sm leading-6 text-muted">{{ detail.content }}</p>
        </div>
        <div v-if="detail.reply" class="rounded-lg border border-line bg-surface-muted p-4">
          <p class="mb-1 text-xs font-medium text-faint">官方回复</p>
          <p class="whitespace-pre-line text-sm leading-6 text-ink">{{ detail.reply }}</p>
          <p v-if="detail.replyTime" class="mt-2 text-xs text-faint">{{ formatTime(detail.replyTime) }}</p>
        </div>
      </div>
    </AppModal>
  </div>
</template>
