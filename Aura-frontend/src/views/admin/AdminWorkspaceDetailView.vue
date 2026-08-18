<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { adminApi, kbApi, logApi, memberApi } from '@/api'
import type { KnowledgeBase, OperationLog, Workspace, WorkspaceMemberVO } from '@/types'
import { MEMBER_ROLE, WS_STATUS } from '@/constants/enums'
import { formatTime } from '@/utils/format'
import AppIcon from '@/components/ui/AppIcon.vue'
import AppBadge from '@/components/ui/AppBadge.vue'
import AppAvatar from '@/components/ui/AppAvatar.vue'
import AppEmpty from '@/components/ui/AppEmpty.vue'
import AppSpinner from '@/components/ui/AppSpinner.vue'
import AppPagination from '@/components/ui/AppPagination.vue'

const route = useRoute()
const router = useRouter()
const wsId = computed(() => Number(route.params.workspaceId))
const from = computed(() => route.query.from)

const workspace = ref<Workspace | null>(null)
const notFound = ref(false)
const teamKb = ref<KnowledgeBase | null>(null)
const members = ref<WorkspaceMemberVO[]>([])
const memberTotal = ref(0)
const logs = ref<OperationLog[]>([])
const logTotal = ref(0)
const tab = ref<'overview' | 'members' | 'logs'>('overview')
const loading = ref(false)
const page = ref(1)

const STATUS: Record<number, { tone: 'green' | 'red' | 'gray'; label: string }> = {
  [WS_STATUS.NORMAL]: { tone: 'green', label: '正常' },
  [WS_STATUS.BANNED]: { tone: 'red', label: '已封禁' },
  [WS_STATUS.DISSOLVED]: { tone: 'gray', label: '已解散' },
}

const ROLE_LABEL: Record<number, string> = {
  [MEMBER_ROLE.OWNER]: '创建者',
  [MEMBER_ROLE.ADMIN]: '管理员',
  [MEMBER_ROLE.MEMBER]: '成员',
}
const ROLE_TONE: Record<number, 'yellow' | 'blue' | 'gray'> = {
  [MEMBER_ROLE.OWNER]: 'yellow',
  [MEMBER_ROLE.ADMIN]: 'blue',
  [MEMBER_ROLE.MEMBER]: 'gray',
}

async function loadWorkspace() {
  try {
    const { data } = await adminApi.workspaces(1, 100)
    if (data.code === 200) {
      const found = data.data.records.find((w) => w.id === wsId.value) ?? null
      workspace.value = found
      notFound.value = found === null
    } else {
      workspace.value = null
      notFound.value = true
    }
  } catch {
    workspace.value = null
    notFound.value = false
  }
}

async function loadTeamKb() {
  try {
    const { data } = await kbApi.team(wsId.value)
    teamKb.value = data.code === 200 ? data.data : null
  } catch {
    teamKb.value = null
  }
}

async function loadMembers() {
  try {
    const { data } = await memberApi.list(wsId.value, 1, 100)
    members.value = data.code === 200 ? data.data.records : []
    memberTotal.value = data.code === 200 ? data.data.total : 0
  } catch {
    members.value = []
  }
}

async function loadLogs() {
  loading.value = true
  try {
    const { data } = await logApi.list(wsId.value, page.value, 20)
    logs.value = data.code === 200 ? data.data.records : []
    logTotal.value = data.code === 200 ? data.data.total : 0
  } catch {
    logs.value = []
    logTotal.value = 0
  } finally {
    loading.value = false
  }
}

function switchTab(t: 'overview' | 'members' | 'logs') {
  tab.value = t
  if (t === 'overview') {
    loadWorkspace()
    loadTeamKb()
  } else if (t === 'members') {
    loadMembers()
  } else if (t === 'logs') {
    loadLogs()
  }
}

function onLogPage(p: number) {
  page.value = p
  loadLogs()
}

onMounted(() => {
  loadWorkspace()
  loadTeamKb()
  loadMembers()
})
</script>

<template>
  <div class="mx-auto max-w-4xl px-4 py-8 md:px-8">
    <button class="mb-4 flex items-center gap-1 text-sm text-muted hover:text-ink" @click="router.push(from === 'report' ? '/admin/reports' : '/admin/workspaces')">
      <AppIcon name="arrow-left" :size="15" />
      {{ from === 'report' ? '返回举报' : '返回团队管理' }}
    </button>

    <AppEmpty v-if="notFound" icon="users" title="该团队不存在或已被删除" description="可能已被解散、封禁或彻底清除。" />
    <AppSpinner v-else-if="!workspace" class="justify-center py-16" label="加载中…" />

    <template v-else>
      <!-- 头部 -->
      <div class="mb-6 flex flex-wrap items-center justify-between gap-3">
        <div class="flex items-center gap-4">
          <AppAvatar :src="workspace.logo" :name="workspace.name" :size="52" />
          <div>
            <div class="flex items-center gap-2">
              <h1 class="text-lg font-medium text-ink">{{ workspace.name }}</h1>
              <AppBadge :tone="STATUS[workspace.status]?.tone ?? 'gray'">{{ STATUS[workspace.status]?.label ?? '未知' }}</AppBadge>
            </div>
            <p class="mt-0.5 text-sm text-faint">{{ workspace.description || '暂无描述' }}</p>
          </div>
        </div>
      </div>

      <!-- Tab 切换 -->
      <div class="mb-4 flex gap-1 border-b border-line">
        <button
          v-for="t in (['overview', 'members', 'logs'] as const)"
          :key="t"
          class="-mb-px border-b-2 px-4 py-2.5 text-sm transition-colors"
          :class="tab === t ? 'border-ink font-medium text-ink' : 'border-transparent text-muted hover:text-ink'"
          @click="switchTab(t)"
        >
          {{ t === 'overview' ? '概览' : t === 'members' ? '成员' : '日志' }}
        </button>
      </div>

      <!-- 概览 -->
      <div v-if="tab === 'overview'" class="space-y-4">
        <div class="card p-5">
          <h3 class="mb-3 text-sm font-medium text-ink">团队信息</h3>
          <dl class="space-y-2 text-sm">
            <div class="flex justify-between gap-4">
              <dt class="text-faint">团队 ID</dt>
              <dd class="text-ink">{{ workspace.id }}</dd>
            </div>
            <div class="flex justify-between gap-4">
              <dt class="text-faint">创建者 ID</dt>
              <dd class="text-ink">{{ workspace.ownerId }}</dd>
            </div>
            <div class="flex justify-between gap-4">
              <dt class="text-faint">关联知识库 ID</dt>
              <dd class="text-ink">{{ workspace.kbId ?? '—' }}</dd>
            </div>
            <div class="flex justify-between gap-4">
              <dt class="text-faint">邀请码</dt>
              <dd class="text-ink">{{ workspace.inviteCode || '—' }}</dd>
            </div>
            <div class="flex justify-between gap-4">
              <dt class="text-faint">创建时间</dt>
              <dd class="text-ink">{{ formatTime(workspace.createTime, false) }}</dd>
            </div>
            <div class="flex justify-between gap-4">
              <dt class="text-faint">更新时间</dt>
              <dd class="text-ink">{{ formatTime(workspace.updateTime, false) }}</dd>
            </div>
          </dl>
        </div>

        <div class="card p-5">
          <h3 class="mb-3 text-sm font-medium text-ink">团队知识库</h3>
          <button
            v-if="teamKb"
            class="flex w-full items-center gap-3 rounded-sm px-2 py-2 text-left transition-colors hover:bg-surface-muted"
            @click="router.push({ path: `/admin/kb/${teamKb.id}`, query: { workspaceId: wsId, ...(from === 'report' ? { from: 'report' } : {}) } })"
          >
            <AppIcon name="book" :size="17" class="text-muted" />
            <div class="flex-1">
              <p class="text-sm text-ink">{{ teamKb.name }}</p>
              <p class="mt-0.5 line-clamp-2 text-xs leading-5 text-faint">{{ teamKb.description || '暂无描述' }}</p>
              <p class="mt-1 text-xs text-faint">{{ teamKb.docCount }} 篇文档</p>
            </div>
            <AppIcon name="chevron-right" :size="14" class="text-faint" />
          </button>
          <p v-else class="text-sm text-faint">暂无团队知识库。</p>
        </div>
      </div>

      <!-- 成员 -->
      <div v-else-if="tab === 'members'" class="card overflow-hidden">
        <div v-if="!members.length" class="py-10">
          <AppEmpty icon="users" title="暂无成员" />
        </div>
        <ul v-else class="divide-y divide-line">
          <li v-for="m in members" :key="m.userId" class="flex items-center gap-3 px-5 py-3">
            <AppAvatar :src="m.avatar" :name="m.username" :size="36" />
            <div class="min-w-0 flex-1">
              <p class="truncate text-sm font-medium text-ink">{{ m.username }}</p>
              <p class="text-xs text-faint">加入于 {{ formatTime(m.joinedAt, false) }}</p>
            </div>
            <AppBadge :tone="ROLE_TONE[m.role] ?? 'gray'">{{ ROLE_LABEL[m.role] ?? '成员' }}</AppBadge>
          </li>
        </ul>
      </div>

      <!-- 日志 -->
      <div v-else class="card overflow-hidden">
        <div v-if="loading" class="flex justify-center py-16">
          <AppSpinner label="加载日志…" />
        </div>
        <div v-else-if="!logs.length" class="py-10">
          <AppEmpty icon="clock" title="暂无日志" />
        </div>
        <ul v-else class="divide-y divide-line">
          <li v-for="log in logs" :key="log.id" class="flex items-start gap-3 px-5 py-3">
            <div class="mt-0.5 flex h-7 w-7 shrink-0 items-center justify-center rounded-sm bg-surface-muted text-faint">
              <AppIcon name="clock" :size="14" />
            </div>
            <div class="min-w-0 flex-1">
              <p class="text-sm text-ink">{{ log.requestSummary || `${log.module} · ${log.operation}` }}</p>
              <p class="mt-0.5 text-xs text-faint">{{ log.module }} · {{ formatTime(log.createTime, false) }}</p>
            </div>
          </li>
        </ul>
        <div v-if="logTotal > 0" class="border-t border-line px-4">
          <AppPagination :page="page" :total="logTotal" :size="20" @change="onLogPage" />
        </div>
      </div>
    </template>
  </div>
</template>
