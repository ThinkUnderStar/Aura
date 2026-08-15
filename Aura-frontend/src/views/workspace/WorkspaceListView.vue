<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { memberApi, wsApi } from '@/api'
import { toast } from '@/stores/toast'
import type { WorkspaceVO } from '@/types'
import { MEMBER_ROLE, WS_STATUS } from '@/constants/enums'
import { formatTime, initialOf } from '@/utils/format'
import AppIcon from '@/components/ui/AppIcon.vue'
import AppBadge from '@/components/ui/AppBadge.vue'
import AppAvatar from '@/components/ui/AppAvatar.vue'
import AppModal from '@/components/ui/AppModal.vue'
import AppEmpty from '@/components/ui/AppEmpty.vue'
import AppSpinner from '@/components/ui/AppSpinner.vue'

const router = useRouter()

const workspaces = ref<WorkspaceVO[]>([])
const loading = ref(false)

const showCreate = ref(false)
const createForm = ref({ name: '', description: '' })
const creating = ref(false)

const showJoin = ref(false)
const inviteCode = ref('')
const joining = ref(false)

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

const STATUS: Record<number, { tone: 'green' | 'red' | 'gray'; label: string }> = {
  [WS_STATUS.NORMAL]: { tone: 'green', label: '正常' },
  [WS_STATUS.BANNED]: { tone: 'red', label: '已封禁' },
  [WS_STATUS.DISSOLVED]: { tone: 'gray', label: '已解散' },
}

async function load() {
  loading.value = true
  try {
    const { data } = await wsApi.list(1, 100)
    workspaces.value = data.code === 200 ? data.data.records : []
  } catch {
    workspaces.value = []
  } finally {
    loading.value = false
  }
}

async function create() {
  const name = createForm.value.name.trim()
  if (!name) return toast.error('请输入团队名称')
  creating.value = true
  try {
    await wsApi.create({ name, description: createForm.value.description.trim() || undefined })
    showCreate.value = false
    createForm.value = { name: '', description: '' }
    toast.success('已创建')
    await load()
  } catch {
    /* 拦截器已提示 */
  } finally {
    creating.value = false
  }
}

async function join() {
  const code = inviteCode.value.trim()
  if (!code) return toast.error('请输入邀请码')
  joining.value = true
  try {
    await memberApi.join(code)
    showJoin.value = false
    inviteCode.value = ''
    toast.success('已加入团队')
    await load()
  } catch {
    /* 拦截器已提示 */
  } finally {
    joining.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="mx-auto max-w-5xl px-4 py-8 md:px-8">
    <div class="mb-6 flex flex-wrap items-center justify-between gap-3">
      <div>
        <h1 class="text-lg font-medium text-ink">团队</h1>
        <p class="mt-1 text-sm text-faint">协作共享知识库，或加入他人的团队。</p>
      </div>
      <div class="flex items-center gap-2">
        <button class="btn-secondary" @click="showJoin = true">
          <AppIcon name="users" :size="15" />
          加入团队
        </button>
        <button class="btn-primary" @click="showCreate = true">
          <AppIcon name="plus" :size="15" />
          创建团队
        </button>
      </div>
    </div>

    <div v-if="loading" class="flex justify-center py-16">
      <AppSpinner label="加载中…" />
    </div>

    <AppEmpty
      v-else-if="!workspaces.length"
      icon="users"
      title="还没有团队"
      description="创建团队与成员共享知识库，或通过邀请码加入已有团队。"
    >
      <div class="flex gap-2">
        <button class="btn-secondary" @click="showJoin = true">加入团队</button>
        <button class="btn-primary" @click="showCreate = true">创建团队</button>
      </div>
    </AppEmpty>

    <div v-else class="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
      <button
        v-for="ws in workspaces"
        :key="ws.id"
        class="card group p-5 text-left transition-all hover:shadow-lift"
        @click="router.push(`/workspaces/${ws.id}`)"
      >
        <div class="flex items-start justify-between">
          <AppAvatar :src="ws.logo" :name="ws.name" :size="40" />
          <div class="flex items-center gap-1.5">
            <AppBadge v-if="ws.role != null" :tone="ROLE_TONE[ws.role] ?? 'gray'">{{ ROLE_LABEL[ws.role] ?? '成员' }}</AppBadge>
            <AppBadge :tone="STATUS[ws.status]?.tone ?? 'gray'">{{ STATUS[ws.status]?.label ?? '未知' }}</AppBadge>
          </div>
        </div>
        <h3 class="mt-4 text-sm font-medium text-ink">{{ ws.name }}</h3>
        <p class="mt-1 line-clamp-2 min-h-[2.5rem] text-xs leading-5 text-faint">
          {{ ws.description || '暂无描述' }}
        </p>
        <p class="mt-3 text-xs text-faint">创建于 {{ formatTime(ws.createTime, false) }}</p>
      </button>
    </div>

    <!-- 创建 -->
    <AppModal :open="showCreate" title="创建团队" @close="showCreate = false">
      <div class="space-y-4">
        <div>
          <label class="label">名称</label>
          <input v-model="createForm.name" class="input" placeholder="例如：产品研发组" />
        </div>
        <div>
          <label class="label">描述（可选）</label>
          <textarea v-model="createForm.description" class="input resize-none" rows="3" />
        </div>
      </div>
      <div class="mt-5 flex justify-end gap-2">
        <button class="btn-secondary" @click="showCreate = false">取消</button>
        <button class="btn-primary" :disabled="creating" @click="create">
          <AppIcon v-if="creating" name="refresh" :size="14" class="animate-spin" />
          创建
        </button>
      </div>
    </AppModal>

    <!-- 加入 -->
    <AppModal :open="showJoin" title="加入团队" @close="showJoin = false">
      <label class="label">邀请码</label>
      <input v-model="inviteCode" class="input" placeholder="输入团队邀请码" @keydown.enter="join" />
      <div class="mt-5 flex justify-end gap-2">
        <button class="btn-secondary" @click="showJoin = false">取消</button>
        <button class="btn-primary" :disabled="joining" @click="join">加入</button>
      </div>
    </AppModal>
  </div>
</template>
