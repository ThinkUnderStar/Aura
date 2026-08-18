<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { notificationApi } from '@/api'
import { useNotificationStore } from '@/stores/notification'
import { toast } from '@/stores/toast'
import type { Notification } from '@/types'
import { NOTIFICATION_TYPE } from '@/constants/enums'
import { formatTime } from '@/utils/format'
import AppIcon from '@/components/ui/AppIcon.vue'
import AppBadge from '@/components/ui/AppBadge.vue'
import AppEmpty from '@/components/ui/AppEmpty.vue'
import AppSpinner from '@/components/ui/AppSpinner.vue'
import AppPagination from '@/components/ui/AppPagination.vue'
import AppConfirm from '@/components/ui/AppConfirm.vue'
import AppModal from '@/components/ui/AppModal.vue'

const notifStore = useNotificationStore()

const items = ref<Notification[]>([])
const total = ref(0)
const page = ref(1)
const size = 20
const filter = ref<'all' | 'unread' | 'read'>('all')
const loading = ref(false)
const deleting = ref<Notification | null>(null)
const clearingRead = ref(false)
const viewing = ref<Notification | null>(null)
const hasUnread = computed(() => notifStore.unread > 0)
const hasRead = computed(() => notifStore.read > 0)

/** 把一条通知的内容拆成「你发出的」与「管理员回复」两部分（依据后端固定文案拆分） */
function splitContent(n: Notification): { sent: string; reply: string } {
  if (n.type === 'feedback_reply') {
    const marker = '已收到管理员回复：'
    const i = n.content.indexOf(marker)
    if (i > -1) return { sent: n.content.slice(0, i), reply: n.content.slice(i + marker.length) }
  }
  if (n.type === 'report_result') {
    const marker = '已被管理员'
    const i = n.content.indexOf(marker)
    if (i > -1) return { sent: n.content.slice(0, i), reply: n.content.slice(i + marker.length) }
  }
  // 无法拆分的场景（如“被举报人通知”），整体按「你发出的」展示
  return { sent: n.content, reply: '' }
}

const viewingParts = computed(() => (viewing.value ? splitContent(viewing.value) : { sent: '', reply: '' }))

async function load() {
  loading.value = true
  try {
    const isRead = filter.value === 'all' ? undefined : filter.value === 'read' ? 1 : 0
    const { data } = await notificationApi.list(page.value, size, undefined, isRead)
    items.value = data.code === 200 ? data.data.records : []
    total.value = data.code === 200 ? data.data.total : 0
  } catch {
    items.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function setFilter(f: 'all' | 'unread' | 'read') {
  filter.value = f
  page.value = 1
  load()
}

async function markRead(n: Notification) {
  if (n.isRead === 1) return
  try {
    await notificationApi.read(n.id)
    n.isRead = 1
    notifStore.refreshUnread()
  } catch {
    /* 拦截器已提示 */
  }
}

/** 点击通知：标记已读，并弹出完整消息 */
function openDetail(n: Notification) {
  markRead(n)
  viewing.value = n
}

async function markAll() {
  if (!hasUnread.value) return
  try {
    await notificationApi.readAll()
    toast.success('已全部标记为已读')
    notifStore.refreshUnread()
    await load()
  } catch {
    /* 拦截器已提示 */
  }
}

async function remove() {
  if (!deleting.value) return
  try {
    await notificationApi.remove(deleting.value.id)
    deleting.value = null
    await load()
    notifStore.refreshUnread()
  } catch {
    /* 拦截器已提示 */
  }
}

async function clearRead() {
  try {
    await notificationApi.removeRead()
    clearingRead.value = false
    toast.success('已清除所有已读通知')
    notifStore.refreshUnread()
    await load()
  } catch {
    /* 拦截器已提示 */
  }
}

function onPage(p: number) {
  page.value = p
  load()
}

onMounted(() => {
  load()
  notifStore.refreshUnread()
})
</script>

<template>
  <div class="mx-auto max-w-3xl px-4 py-8 md:px-8">
    <div class="mb-6 flex items-center justify-between">
      <div>
        <h1 class="text-lg font-medium text-ink">通知</h1>
        <p class="mt-1 text-sm text-faint">系统消息与反馈回复。</p>
      </div>
      <div class="flex gap-2">
        <button
          class="btn-secondary"
          :disabled="!hasUnread"
          :title="hasUnread ? '将所有通知标记为已读' : '暂无未读通知'"
          @click="markAll"
        >
          <AppIcon name="check" :size="15" />
          全部已读
        </button>
        <button
          class="btn-secondary"
          :disabled="!hasRead"
          :title="hasRead ? '清除所有已读通知' : '暂无已读通知'"
          @click="clearingRead = true"
        >
          <AppIcon name="trash" :size="15" />
          清除已读
        </button>
      </div>
    </div>

    <div class="mb-4 flex gap-1 border-b border-line">
      <button
        v-for="f in (['all', 'unread', 'read'] as const)"
        :key="f"
        class="-mb-px border-b-2 px-4 py-2 text-sm transition-colors"
        :class="filter === f ? 'border-ink font-medium text-ink' : 'border-transparent text-muted hover:text-ink'"
        @click="setFilter(f)"
      >
        {{ f === 'all' ? '全部' : f === 'unread' ? '未读' : '已读' }}
      </button>
    </div>

    <div class="card overflow-hidden">
      <div v-if="loading" class="flex justify-center py-16">
        <AppSpinner label="加载中…" />
      </div>
      <div v-else-if="!items.length" class="py-10">
        <AppEmpty icon="bell" title="暂无通知" />
      </div>
      <ul v-else class="divide-y divide-line">
        <li
          v-for="n in items"
          :key="n.id"
          class="flex cursor-pointer items-start gap-3 px-5 py-4 transition-colors hover:bg-surface-muted"
          :class="{ 'bg-surface-muted/60': n.isRead === 0 }"
          @click="openDetail(n)"
        >
          <span
            class="mt-1.5 h-2 w-2 shrink-0 rounded-full"
            :class="n.isRead === 0 ? 'bg-red-text' : 'bg-line-strong'"
          />
          <div class="min-w-0 flex-1">
            <div class="flex items-center gap-2">
              <AppBadge :tone="n.type === 'feedback_reply' ? 'blue' : 'yellow'">
                {{ NOTIFICATION_TYPE[n.type] || '系统' }}
              </AppBadge>
              <p class="truncate text-sm font-medium text-ink">{{ n.title }}</p>
            </div>
            <p class="mt-1 line-clamp-2 min-h-[3rem] whitespace-pre-line text-sm leading-6 text-muted">{{ n.content }}</p>
            <p class="mt-1.5 text-xs text-faint">{{ formatTime(n.createTime, false) }}</p>
          </div>
          <button
            class="shrink-0 rounded-sm p-1.5 text-faint hover:bg-red-bg hover:text-red-text"
            title="删除"
            @click.stop="deleting = n"
          >
            <AppIcon name="trash" :size="15" />
          </button>
        </li>
      </ul>
      <div v-if="total > 0" class="border-t border-line px-4">
        <AppPagination :page="page" :total="total" :size="size" @change="onPage" />
      </div>
    </div>

    <AppConfirm
      :open="!!deleting"
      title="删除通知"
      message="确定删除这条通知吗？"
      confirm-text="删除"
      :danger="true"
      @confirm="remove"
      @cancel="deleting = null"
    />

    <AppConfirm
      :open="clearingRead"
      title="清除已读通知"
      message="确定清除所有已读通知吗？此操作不可恢复。"
      confirm-text="清除"
      :danger="true"
      @confirm="clearRead"
      @cancel="clearingRead = false"
    />

    <!-- 通知详情：点击通知后标记已读并展示完整消息 -->
    <AppModal :open="!!viewing" :title="viewing?.title || '通知'" width="max-w-lg" @close="viewing = null">
      <div v-if="viewing" class="max-h-[60vh] space-y-3 overflow-y-auto pr-1">
        <div class="flex items-center gap-2">
          <AppBadge :tone="viewing.type === 'feedback_reply' ? 'blue' : 'yellow'">
            {{ NOTIFICATION_TYPE[viewing.type] || '系统' }}
          </AppBadge>
          <span class="text-xs text-faint">{{ formatTime(viewing.createTime, false) }}</span>
        </div>
        <template v-if="viewingParts.reply">
          <div class="rounded-lg bg-surface-muted/70 p-3">
            <p class="text-xs font-medium text-faint">你发出的</p>
            <p class="mt-1 break-words whitespace-pre-line text-sm leading-6 text-ink">{{ viewingParts.sent }}</p>
          </div>
          <div class="rounded-lg bg-surface-muted/70 p-3">
            <p class="text-xs font-medium text-faint">管理员回复</p>
            <p class="mt-1 break-words whitespace-pre-line text-sm leading-6 text-ink">{{ viewingParts.reply }}</p>
          </div>
        </template>
        <p v-else class="break-words whitespace-pre-line text-sm leading-6 text-muted">{{ viewing.content }}</p>
      </div>
    </AppModal>
  </div>
</template>
