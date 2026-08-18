<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { adminApi } from '@/api'
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
const size = 20
const statusFilter = ref<number | undefined>(undefined)
const loading = ref(false)

const replying = ref<Feedback | null>(null)
const reply = ref('')
const replyingBusy = ref(false)

const STATUS_TONE: Record<number, 'yellow' | 'blue' | 'green' | 'gray'> = {
  0: 'yellow',
  1: 'blue',
  2: 'green',
  3: 'gray',
}
const STATUS_OPTIONS = [0, 1, 2, 3]

async function load() {
  loading.value = true
  try {
    const { data } = await adminApi.feedbacks(page.value, size, statusFilter.value, undefined)
    items.value = data.code === 200 ? data.data.records : []
    total.value = data.code === 200 ? data.data.total : 0
  } catch {
    items.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function setStatusFilter(s: number | undefined) {
  statusFilter.value = s
  page.value = 1
  load()
}

async function submitReply() {
  if (!replying.value) return
  if (!reply.value.trim()) return toast.error('请输入回复内容')
  replyingBusy.value = true
  try {
    await adminApi.replyFeedback({ feedbackId: replying.value.id, reply: reply.value.trim() })
    toast.success('已回复')
    replying.value = null
    reply.value = ''
    await load()
  } catch {
    /* 拦截器已提示 */
  } finally {
    replyingBusy.value = false
  }
}

async function setStatus(f: Feedback, status: number) {
  try {
    await adminApi.updateFeedbackStatus(f.id, status)
    toast.success('状态已更新')
    await load()
  } catch {
    /* 拦截器已提示 */
  }
}

function onPage(p: number) {
  page.value = p
  load()
}

onMounted(load)
</script>

<template>
  <div class="mx-auto max-w-4xl px-4 py-8 md:px-8">
    <h1 class="text-lg font-medium text-ink">反馈管理</h1>
    <p class="mt-1 text-sm text-faint">处理用户反馈并回复。</p>

    <div class="mt-4 flex flex-wrap gap-1 border-b border-line">
      <button
        class="-mb-px border-b-2 px-3 py-2 text-sm transition-colors"
        :class="statusFilter === undefined ? 'border-ink font-medium text-ink' : 'border-transparent text-muted hover:text-ink'"
        @click="setStatusFilter(undefined)"
      >
        全部
      </button>
      <button
        v-for="(label, key) in FEEDBACK_STATUS"
        :key="key"
        class="-mb-px border-b-2 px-3 py-2 text-sm transition-colors"
        :class="statusFilter === Number(key) ? 'border-ink font-medium text-ink' : 'border-transparent text-muted hover:text-ink'"
        @click="setStatusFilter(Number(key))"
      >
        {{ label }}
      </button>
    </div>

    <div class="card mt-4 overflow-hidden">
      <div v-if="loading" class="flex justify-center py-16">
        <AppSpinner label="加载中…" />
      </div>
      <div v-else-if="!items.length" class="py-10">
        <AppEmpty icon="mail" title="暂无反馈" />
      </div>
      <ul v-else class="divide-y divide-line">
        <li v-for="f in items" :key="f.id" class="px-5 py-4">
          <div class="flex items-center gap-2">
            <AppBadge tone="gray">{{ FEEDBACK_TYPE[f.type] || f.type }}</AppBadge>
            <AppBadge :tone="STATUS_TONE[f.status] ?? 'gray'">{{ FEEDBACK_STATUS[f.status] ?? '未知' }}</AppBadge>
            <span class="text-xs text-faint">用户 {{ f.userId }}</span>
            <span class="ml-auto text-xs text-faint">{{ formatTime(f.createTime, false) }}</span>
          </div>
          <p class="mt-2 break-words text-sm font-medium text-ink">{{ f.title }}</p>
          <p class="mt-1 whitespace-pre-line break-words text-sm leading-6 text-muted">{{ f.content }}</p>
          <p v-if="f.reply" class="mt-2 break-words rounded-sm bg-surface-muted px-3 py-2 text-sm text-ink">回复：{{ f.reply }}</p>
          <div class="mt-3 flex flex-wrap items-center gap-2">
            <select
              class="rounded-sm border border-line bg-surface px-2 py-1.5 text-xs text-ink outline-none"
              :value="f.status"
              @change="setStatus(f, Number(($event.target as HTMLSelectElement).value))"
            >
              <!-- 0=待处理 是提交后的初始态，后端不允许改回，仅作展示 -->
              <option v-for="s in STATUS_OPTIONS" :key="s" :value="s" :disabled="s === 0">{{ FEEDBACK_STATUS[s] }}</option>
            </select>
            <button class="btn-secondary !px-3 !py-1.5 text-xs" @click="replying = f; reply = f.reply ?? ''">
              <AppIcon name="mail" :size="13" />
              {{ f.reply ? '修改回复' : '回复' }}
            </button>
          </div>
        </li>
      </ul>
      <div v-if="total > 0" class="border-t border-line px-4">
        <AppPagination :page="page" :total="total" :size="size" @change="onPage" />
      </div>
    </div>

    <AppModal :open="!!replying" title="回复反馈" @close="replying = null">
      <label class="label">回复内容</label>
      <textarea v-model="reply" class="input resize-none" rows="4" placeholder="输入回复内容" />
      <div class="mt-5 flex justify-end gap-2">
        <button class="btn-secondary" @click="replying = null">取消</button>
        <button class="btn-primary" :disabled="replyingBusy" @click="submitReply">发送</button>
      </div>
    </AppModal>
  </div>
</template>
