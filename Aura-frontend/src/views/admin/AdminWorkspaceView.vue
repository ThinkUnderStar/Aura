<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { adminApi } from '@/api'
import { toast } from '@/stores/toast'
import type { Workspace } from '@/types'
import { WS_STATUS } from '@/constants/enums'
import { formatTime } from '@/utils/format'
import AppIcon from '@/components/ui/AppIcon.vue'
import AppBadge from '@/components/ui/AppBadge.vue'
import AppEmpty from '@/components/ui/AppEmpty.vue'
import AppSpinner from '@/components/ui/AppSpinner.vue'
import AppPagination from '@/components/ui/AppPagination.vue'
import AppConfirm from '@/components/ui/AppConfirm.vue'

const items = ref<Workspace[]>([])
const total = ref(0)
const page = ref(1)
const size = 20
const loading = ref(false)
const router = useRouter()
const target = ref<Workspace | null>(null)
const busy = ref(false)
const keyword = ref('')

const STATUS: Record<number, { tone: 'green' | 'red' | 'gray'; label: string }> = {
  [WS_STATUS.NORMAL]: { tone: 'green', label: '正常' },
  [WS_STATUS.BANNED]: { tone: 'red', label: '已封禁' },
  [WS_STATUS.DISSOLVED]: { tone: 'gray', label: '已解散' },
}

async function load() {
  loading.value = true
  try {
    const kw = keyword.value.trim()
    const { data } = kw
      ? await adminApi.searchWorkspaces(kw, page.value, size)
      : await adminApi.workspaces(page.value, size)
    items.value = data.code === 200 ? data.data.records : []
    total.value = data.code === 200 ? data.data.total : 0
  } catch {
    items.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  load()
}

function clearSearch() {
  keyword.value = ''
  page.value = 1
  load()
}

async function confirmAction() {
  if (!target.value) return
  busy.value = true
  try {
    if (target.value.status === WS_STATUS.BANNED) {
      await adminApi.unbanWorkspace(target.value.id, target.value.kbId ?? 0)
      toast.success('已解封')
    } else {
      await adminApi.banWorkspace(target.value.id)
      toast.success('已封禁')
    }
    target.value = null
    await load()
  } catch {
    /* 拦截器已提示 */
  } finally {
    busy.value = false
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
    <h1 class="text-lg font-medium text-ink">团队管理</h1>
    <p class="mt-1 text-sm text-faint">查看全部团队并执行封禁 / 解封。</p>

    <div class="mt-5 flex items-center gap-2">
      <div class="relative max-w-sm flex-1">
        <button
          type="button"
          class="absolute left-1 top-1/2 -translate-y-1/2 rounded-sm p-1 text-faint transition-colors hover:text-ink"
          title="按团队名称搜索"
          @click="search"
        >
          <AppIcon name="search" :size="15" />
        </button>
        <input v-model="keyword" class="input pl-9" placeholder="按团队名称搜索" @keydown.enter="search" />
      </div>
      <button class="btn-secondary" @click="search">搜索</button>
      <button v-if="keyword" class="btn-secondary" @click="clearSearch">清空</button>
    </div>

    <div class="card mt-6 overflow-hidden">
      <div v-if="loading" class="flex justify-center py-16">
        <AppSpinner label="加载中…" />
      </div>
      <div v-else-if="!items.length" class="py-10">
        <AppEmpty icon="users" title="暂无团队" />
      </div>
      <ul v-else class="divide-y divide-line">
        <li
          v-for="ws in items"
          :key="ws.id"
          class="flex cursor-pointer items-center gap-3 px-5 py-3 transition-colors hover:bg-surface-muted"
          @click="router.push(`/admin/workspaces/${ws.id}`)"
        >
          <div class="min-w-0 flex-1">
            <div class="flex items-center gap-2">
              <p class="truncate text-sm font-medium text-ink">{{ ws.name }}</p>
              <AppBadge :tone="STATUS[ws.status]?.tone ?? 'gray'">{{ STATUS[ws.status]?.label ?? '未知' }}</AppBadge>
            </div>
            <p class="mt-0.5 text-xs text-faint">ID {{ ws.id }} · 创建者 {{ ws.ownerId }} · 创建于 {{ formatTime(ws.createTime, false) }}</p>
          </div>
          <button
            :class="ws.status === WS_STATUS.BANNED ? 'btn-secondary' : 'btn-danger'"
            @click.stop="target = ws"
          >
            {{ ws.status === WS_STATUS.BANNED ? '解封' : '封禁' }}
          </button>
        </li>
      </ul>
      <div v-if="total > 0" class="border-t border-line px-4">
        <AppPagination :page="page" :total="total" :size="size" @change="onPage" />
      </div>
    </div>

    <AppConfirm
      :open="!!target"
      :title="target?.status === WS_STATUS.BANNED ? '解封团队' : '封禁团队'"
      :message="`确定${target?.status === WS_STATUS.BANNED ? '解封' : '封禁'}「${target?.name}」吗？`"
      :confirm-text="target?.status === WS_STATUS.BANNED ? '解封' : '封禁'"
      :danger="target?.status !== WS_STATUS.BANNED"
      :loading="busy"
      @confirm="confirmAction"
      @cancel="target = null"
    />
  </div>
</template>
