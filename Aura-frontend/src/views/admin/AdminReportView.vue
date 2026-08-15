<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { adminApi } from '@/api'
import { toast } from '@/stores/toast'
import type { Report } from '@/types'
import { REPORT_REASON, REPORT_STATUS, REPORT_TARGET } from '@/constants/enums'
import { formatTime } from '@/utils/format'
import AppIcon from '@/components/ui/AppIcon.vue'
import AppBadge from '@/components/ui/AppBadge.vue'
import AppModal from '@/components/ui/AppModal.vue'
import AppEmpty from '@/components/ui/AppEmpty.vue'
import AppSpinner from '@/components/ui/AppSpinner.vue'
import AppPagination from '@/components/ui/AppPagination.vue'

const items = ref<Report[]>([])
const total = ref(0)
const page = ref(1)
const size = 20
const statusFilter = ref<number | undefined>(undefined)
const loading = ref(false)

const handling = ref<Report | null>(null)
const handleStatus = ref<number>(1)
const handleResult = ref('')
const handlingBusy = ref(false)

const STATUS_TONE: Record<number, 'yellow' | 'green' | 'red'> = {
  0: 'yellow',
  1: 'green',
  2: 'red',
}

async function load() {
  loading.value = true
  try {
    const { data } = await adminApi.reports(page.value, size, statusFilter.value, undefined)
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

async function submitHandle() {
  if (!handling.value) return
  handlingBusy.value = true
  try {
    await adminApi.handleReport({
      reportId: handling.value.id,
      status: handleStatus.value,
      handleResult: handleResult.value.trim(),
    })
    toast.success('已处理')
    handling.value = null
    handleResult.value = ''
    await load()
  } catch {
    /* 拦截器已提示 */
  } finally {
    handlingBusy.value = false
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
    <h1 class="text-lg font-medium text-ink">举报管理</h1>
    <p class="mt-1 text-sm text-faint">处理用户提交的举报。</p>

    <div class="mt-4 flex flex-wrap gap-1 border-b border-line">
      <button
        class="-mb-px border-b-2 px-3 py-2 text-sm transition-colors"
        :class="statusFilter === undefined ? 'border-ink font-medium text-ink' : 'border-transparent text-muted hover:text-ink'"
        @click="setStatusFilter(undefined)"
      >
        全部
      </button>
      <button
        v-for="(label, key) in REPORT_STATUS"
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
        <AppEmpty icon="flag" title="暂无举报" />
      </div>
      <ul v-else class="divide-y divide-line">
        <li v-for="r in items" :key="r.id" class="px-5 py-4">
          <div class="flex items-center gap-2">
            <AppBadge tone="gray">{{ REPORT_TARGET[r.targetType] || r.targetType }}</AppBadge>
            <AppBadge tone="blue">{{ REPORT_REASON[r.reason] || r.reason }}</AppBadge>
            <AppBadge :tone="STATUS_TONE[r.status] ?? 'gray'">{{ REPORT_STATUS[r.status] ?? '未知' }}</AppBadge>
            <span class="text-xs text-faint">举报人 {{ r.reporterId }}</span>
            <span class="ml-auto text-xs text-faint">{{ formatTime(r.createTime, false) }}</span>
          </div>
          <p class="mt-2 text-sm text-muted">目标 ID：{{ r.targetId }}</p>
          <p class="mt-1 whitespace-pre-line text-sm leading-6 text-ink">{{ r.description }}</p>
          <p v-if="r.handleResult" class="mt-2 rounded-sm bg-surface-muted px-3 py-2 text-sm text-ink">
            处理结果：{{ r.handleResult }}
          </p>
          <div v-if="r.status === 0" class="mt-3 flex justify-end">
            <button
              class="btn-secondary !px-3 !py-1.5 text-xs"
              @click="handling = r; handleStatus = 1; handleResult = ''"
            >
              <AppIcon name="check" :size="13" />
              处理
            </button>
          </div>
        </li>
      </ul>
      <div v-if="total > 0" class="border-t border-line px-4">
        <AppPagination :page="page" :total="total" :size="size" @change="onPage" />
      </div>
    </div>

    <AppModal :open="!!handling" title="处理举报" @close="handling = null">
      <div class="space-y-4">
        <div>
          <label class="label">处理结果</label>
          <div class="flex gap-2">
            <button
              v-for="(label, key) in REPORT_STATUS"
              :key="key"
              class="rounded-sm px-3 py-1.5 text-sm transition-colors"
              :class="handleStatus === Number(key) ? 'bg-ink text-white' : 'text-muted hover:bg-surface-muted hover:text-ink'"
              @click="handleStatus = Number(key)"
            >
              {{ label }}
            </button>
          </div>
        </div>
        <div>
          <label class="label">处理说明</label>
          <textarea v-model="handleResult" class="input resize-none" rows="3" placeholder="简要说明处理结果" />
        </div>
      </div>
      <div class="mt-5 flex justify-end gap-2">
        <button class="btn-secondary" @click="handling = null">取消</button>
        <button class="btn-primary" :disabled="handlingBusy" @click="submitHandle">提交</button>
      </div>
    </AppModal>
  </div>
</template>
