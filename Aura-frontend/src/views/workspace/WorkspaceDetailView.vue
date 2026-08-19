<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { kbApi, logApi, memberApi, wsApi } from '@/api'
import { useAuthStore } from '@/stores/auth'
import { toast } from '@/stores/toast'
import type { KnowledgeBase, OperationLog, WorkspaceMemberVO, WorkspaceVO } from '@/types'
import { MEMBER_ROLE, WS_STATUS } from '@/constants/enums'
import { formatTime } from '@/utils/format'
import AppIcon from '@/components/ui/AppIcon.vue'
import AppBadge from '@/components/ui/AppBadge.vue'
import AppAvatar from '@/components/ui/AppAvatar.vue'
import AppModal from '@/components/ui/AppModal.vue'
import AppConfirm from '@/components/ui/AppConfirm.vue'
import AppEmpty from '@/components/ui/AppEmpty.vue'
import AppSpinner from '@/components/ui/AppSpinner.vue'
import AppPagination from '@/components/ui/AppPagination.vue'
import ReportModal from '@/components/report/ReportModal.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const wsId = computed(() => Number(route.params.workspaceId))

const workspace = ref<WorkspaceVO | null>(null)
const teamKb = ref<KnowledgeBase | null>(null)
const members = ref<WorkspaceMemberVO[]>([])
const memberTotal = ref(0)
const logs = ref<OperationLog[]>([])
const logTotal = ref(0)
const tab = ref<'overview' | 'members' | 'logs'>('overview')
const loading = ref(false)
const page = ref(1)

const showEdit = ref(false)
const editForm = ref({ name: '', description: '' })
const saving = ref(false)
const logoInput = ref<HTMLInputElement | null>(null)
const showReport = ref(false)

const confirm = ref<null | { title: string; message: string; danger?: boolean; action: () => Promise<void> }>(null)
const confirmBusy = ref(false)

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

const myRole = computed(() => workspace.value?.role ?? MEMBER_ROLE.MEMBER)
const isOwner = computed(() => myRole.value === MEMBER_ROLE.OWNER)
const isAdmin = computed(() => myRole.value === MEMBER_ROLE.ADMIN)
const isNormal = computed(() => workspace.value?.status === WS_STATUS.NORMAL)

async function loadWorkspace() {
  try {
    const { data } = await wsApi.list(1, 100)
    workspace.value = data.code === 200 ? (data.data.records.find((w) => w.id === wsId.value) ?? null) : null
    if (workspace.value) editForm.value = { name: workspace.value.name, description: workspace.value.description ?? '' }
  } catch {
    workspace.value = null
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
  // 每次切标签都重新拉取对应数据，避免成员信息/团队信息更新后界面停留在旧数据
  if (t === 'overview') {
    loadWorkspace()
    loadTeamKb()
  } else if (t === 'members') {
    loadMembers()
  } else if (t === 'logs') {
    loadLogs()
  }
}

function ask(title: string, message: string, action: () => Promise<void>, danger = false) {
  confirm.value = { title, message, action, danger }
}

async function runConfirm() {
  if (!confirm.value) return
  confirmBusy.value = true
  try {
    await confirm.value.action()
  } catch {
    /* 拦截器已提示 */
  } finally {
    confirmBusy.value = false
    confirm.value = null
  }
}

function copyInvite() {
  const code = workspace.value?.inviteCode
  if (!code) return
  navigator.clipboard?.writeText(code).then(() => toast.success('邀请码已复制'))
}

async function resetInvite() {
  try {
    const { data } = await wsApi.resetInviteCode(wsId.value)
    const code = data.code === 200 ? data.data : undefined
    if (code && workspace.value) workspace.value.inviteCode = code
    toast.success('邀请码已重置')
  } catch {
    /* 拦截器已提示 */
  }
}

function openEdit() {
  // 打开弹窗时重置为当前值，保证“对比变更”以最新数据为准
  if (workspace.value) {
    editForm.value = { name: workspace.value.name, description: workspace.value.description ?? '' }
  }
  showEdit.value = true
}

async function saveEdit() {
  if (!workspace.value) return
  const name = editForm.value.name.trim()
  if (!name) return toast.error('名称不能为空')
  const description = editForm.value.description.trim()

  // 后端一次仅允许修改 name 或 description 之一（type 指定），按变更项分别提交
  const nameChanged = name !== workspace.value.name
  const descChanged = description !== (workspace.value.description ?? '')

  if (!nameChanged && !descChanged) {
    showEdit.value = false
    return
  }

  saving.value = true
  try {
    if (nameChanged) {
      await wsApi.update({ workspaceId: wsId.value, type: 'name', name })
    }
    if (descChanged) {
      await wsApi.update({ workspaceId: wsId.value, type: 'description', description })
    }
    toast.success('已保存')
    showEdit.value = false
    await loadWorkspace()
  } catch {
    /* 拦截器已提示 */
  } finally {
    saving.value = false
  }
}

async function onLogo(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  try {
    await wsApi.uploadLogo(wsId.value, file)
    toast.success('Logo 已更新')
    await loadWorkspace()
  } catch {
    /* 拦截器已提示 */
  } finally {
    input.value = ''
  }
}

async function removeMember(m: WorkspaceMemberVO) {
  await memberApi.remove(wsId.value, m.userId)
  toast.success('已移除成员')
  await loadMembers()
}

async function setAdmin(m: WorkspaceMemberVO, set: boolean) {
  // 后端 SetRoleDto 用整数 setRole（1=设管理员，2=取消管理员）+ memberId
  await memberApi.setRole({ workspaceId: wsId.value, memberId: m.userId, setRole: set ? 1 : 2 })
  toast.success(set ? '已设为管理员' : '已取消管理员')
  await loadMembers()
}

async function transferOwner(m: WorkspaceMemberVO) {
  await memberApi.transferOwner(wsId.value, m.userId)
  toast.success('已转让创建者')
  await loadMembers()
  await loadWorkspace()
}

async function quit() {
  await memberApi.quit(wsId.value)
  toast.success('已退出团队')
  router.push('/workspaces')
}

async function deleteWs() {
  await wsApi.remove(wsId.value)
  toast.success('团队已解散')
  router.push('/workspaces')
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
    <button class="mb-4 flex items-center gap-1 text-sm text-muted hover:text-ink" @click="router.push('/workspaces')">
      <AppIcon name="arrow-left" :size="15" />
      返回团队列表
    </button>

    <AppSpinner v-if="!workspace" class="justify-center py-16" label="加载中…" />

    <template v-else>
      <!-- 头部 -->
      <div class="mb-6 flex flex-wrap items-center justify-between gap-3">
        <div class="flex items-center gap-4">
          <AppAvatar :src="workspace.logo" :name="workspace.name" :size="52" />
          <div>
            <div class="flex items-center gap-2">
              <h1 class="text-lg font-medium text-ink">{{ workspace.name }}</h1>
              <AppBadge :tone="isNormal ? 'green' : 'red'">{{ isNormal ? '正常' : '已解散' }}</AppBadge>
            </div>
            <p class="mt-0.5 text-sm text-faint">{{ workspace.description || '暂无描述' }}</p>
          </div>
        </div>
        <div class="flex gap-2">
          <button class="btn-ghost" @click="showReport = true">
            <AppIcon name="flag" :size="15" />
            举报
          </button>
          <template v-if="isOwner || isAdmin">
            <button class="btn-secondary" @click="openEdit">
              <AppIcon name="edit" :size="15" />
              编辑
            </button>
            <button class="btn-secondary" @click="logoInput?.click()">
              <AppIcon name="upload" :size="15" />
              上传 Logo
            </button>
            <input ref="logoInput" type="file" accept="image/*" class="hidden" @change="onLogo" />
          </template>
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
          <h3 class="mb-3 text-sm font-medium text-ink">邀请成员</h3>
          <div class="flex flex-wrap items-center gap-2">
            <code class="rounded-sm border border-line bg-surface-muted px-3 py-1.5 font-mono text-sm text-ink">
              {{ workspace.inviteCode }}
            </code>
            <button class="btn-secondary !py-1.5" @click="copyInvite">
              <AppIcon name="copy" :size="14" />
              复制
            </button>
            <button v-if="isOwner || isAdmin" class="btn-ghost !py-1.5" @click="resetInvite">
              <AppIcon name="refresh" :size="14" />
              重置
            </button>
          </div>
        </div>

        <div class="card p-5">
          <h3 class="mb-3 text-sm font-medium text-ink">团队知识库</h3>
          <button
            v-if="teamKb"
            class="flex w-full items-center gap-3 rounded-sm px-2 py-2 text-left transition-colors hover:bg-surface-muted"
            @click="router.push(`/kb/${teamKb.id}`)"
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

        <div v-if="myRole !== MEMBER_ROLE.OWNER || isOwner" class="card p-5">
          <h3 class="mb-3 text-sm font-medium text-ink">危险操作</h3>
          <div class="flex flex-wrap gap-2">
            <button class="btn-danger-soft" @click="ask('退出团队', '退出后将无法访问该团队的知识库与对话。确定退出吗？', quit, true)">
              退出团队
            </button>
            <template v-if="isOwner">
              <button class="btn-danger" @click="ask('解散团队', '解散后团队将不可访问，此操作不可撤销。确定解散吗？', deleteWs, true)">
                解散团队
              </button>
            </template>
          </div>
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
              <div class="flex items-center gap-2">
                <p class="truncate text-sm font-medium text-ink">{{ m.username }}</p>
                <span v-if="m.userId === auth.user?.id" class="text-xs text-faint">（我）</span>
              </div>
              <p class="text-xs text-faint">加入于 {{ formatTime(m.joinedAt, false) }}</p>
            </div>
            <AppBadge :tone="ROLE_TONE[m.role] ?? 'gray'">{{ ROLE_LABEL[m.role] ?? '成员' }}</AppBadge>

            <div v-if="m.userId !== auth.user?.id && isOwner" class="flex gap-0.5">
              <button
                v-if="m.role === MEMBER_ROLE.MEMBER"
                class="rounded-sm p-1.5 text-faint hover:bg-surface-muted hover:text-ink"
                title="设为管理员"
                @click="setAdmin(m, true)"
              >
                <AppIcon name="shield" :size="15" />
              </button>
              <button
                v-if="m.role === MEMBER_ROLE.ADMIN"
                class="rounded-sm p-1.5 text-faint hover:bg-surface-muted hover:text-ink"
                title="取消管理员"
                @click="setAdmin(m, false)"
              >
                <AppIcon name="user" :size="15" />
              </button>
              <button
                class="rounded-sm p-1.5 text-faint hover:bg-surface-muted hover:text-ink"
                title="转让创建者"
                @click="ask('转让创建者', `确定将团队创建者转让给「${m.username}」吗？转让后你将降为普通成员。`, () => transferOwner(m), true)"
              >
                <AppIcon name="log-out" :size="15" />
              </button>
              <button
                class="rounded-sm p-1.5 text-faint hover:bg-red-bg hover:text-red-text"
                title="移除成员"
                @click="ask('移除成员', `确定将「${m.username}」移出团队吗？`, () => removeMember(m), true)"
              >
                <AppIcon name="trash" :size="15" />
              </button>
            </div>
            <div v-else-if="m.userId !== auth.user?.id && isAdmin && m.role === MEMBER_ROLE.MEMBER" class="flex gap-0.5">
              <button
                class="rounded-sm p-1.5 text-faint hover:bg-red-bg hover:text-red-text"
                title="移除成员"
                @click="ask('移除成员', `确定将「${m.username}」移出团队吗？`, () => removeMember(m), true)"
              >
                <AppIcon name="trash" :size="15" />
              </button>
            </div>
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

    <!-- 编辑 -->
    <AppModal :open="showEdit" title="编辑团队" @close="showEdit = false">
      <div class="space-y-4">
        <div>
          <label class="label">名称</label>
          <input v-model="editForm.name" class="input" />
        </div>
        <div>
          <label class="label">描述</label>
          <textarea v-model="editForm.description" class="input resize-none" rows="3" />
        </div>
      </div>
      <div class="mt-5 flex justify-end gap-2">
        <button class="btn-secondary" @click="showEdit = false">取消</button>
        <button class="btn-primary" :disabled="saving" @click="saveEdit">保存</button>
      </div>
    </AppModal>

    <!-- 通用确认 -->
    <AppConfirm
      :open="!!confirm"
      :title="confirm?.title ?? ''"
      :message="confirm?.message ?? ''"
      :danger="confirm?.danger ?? false"
      :loading="confirmBusy"
      @confirm="runConfirm"
      @cancel="confirm = null"
    />

    <!-- 举报团队 -->
    <ReportModal
      :open="showReport"
      target-type="workspace"
      :target-id="wsId"
      :target-name="workspace?.name ?? ''"
      @close="showReport = false"
    />
  </div>
</template>
