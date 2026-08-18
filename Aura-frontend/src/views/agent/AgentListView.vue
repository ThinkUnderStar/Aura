<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { agentApi, kbApi, wsApi } from '@/api'
import { toast } from '@/stores/toast'
import type { Agent, KnowledgeBase } from '@/types'

/** 可绑定知识库：团队知识库额外携带其所属团队 id，用于还原已绑定状态 */
type BindableKb = KnowledgeBase & { workspaceId?: number }
import { formatTime, initialOf } from '@/utils/format'
import AppIcon from '@/components/ui/AppIcon.vue'
import AppModal from '@/components/ui/AppModal.vue'
import AppConfirm from '@/components/ui/AppConfirm.vue'
import AppEmpty from '@/components/ui/AppEmpty.vue'
import AppSpinner from '@/components/ui/AppSpinner.vue'

const router = useRouter()

const agents = ref<Agent[]>([])
const loading = ref(false)

const showCreate = ref(false)
const newName = ref('')
const creating = ref(false)

const renaming = ref<Agent | null>(null)
const renameValue = ref('')
const renamingBusy = ref(false)

const deleting = ref<Agent | null>(null)
const deletingBusy = ref(false)

const binding = ref<Agent | null>(null)
const allKbs = ref<BindableKb[]>([])
const boundIds = ref<number[]>([])
const bindingBusy = ref(false)
const bindingLoading = ref(false)

async function load() {
  loading.value = true
  try {
    const { data } = await agentApi.list(1, 100)
    agents.value = data.code === 200 ? data.data.records : []
  } catch {
    agents.value = []
  } finally {
    loading.value = false
  }
}

async function create() {
  const name = newName.value.trim()
  if (!name) return toast.error('请输入智能体名称')
  creating.value = true
  try {
    await agentApi.create(name)
    showCreate.value = false
    newName.value = ''
    toast.success('已创建')
    await load()
  } catch {
    /* 拦截器已提示 */
  } finally {
    creating.value = false
  }
}

function openRename(agent: Agent) {
  renaming.value = agent
  renameValue.value = agent.name
}

async function saveRename() {
  if (!renaming.value) return
  const name = renameValue.value.trim()
  if (!name) return toast.error('名称不能为空')
  renamingBusy.value = true
  try {
    await agentApi.update(renaming.value.id, name)
    toast.success('已重命名')
    renaming.value = null
    await load()
  } catch {
    /* 拦截器已提示 */
  } finally {
    renamingBusy.value = false
  }
}

async function remove() {
  if (!deleting.value) return
  deletingBusy.value = true
  try {
    await agentApi.remove(deleting.value.id)
    toast.success('已删除')
    deleting.value = null
    await load()
  } catch {
    /* 拦截器已提示 */
  } finally {
    deletingBusy.value = false
  }
}

async function openBind(agent: Agent) {
  binding.value = agent
  boundIds.value = []
  allKbs.value = []
  bindingLoading.value = true
  try {
    const [kbs, bound, workspaces] = await Promise.all([
      kbApi.list(1, 100),
      agentApi.bindingKbs(agent.id),
      wsApi.list(1, 100),
    ])

    const list: BindableKb[] = []
    // 个人知识库
    const personal = kbs.data.code === 200
      ? kbs.data.data.records.filter((k) => k.isTeam === 0 && k.status === 1)
      : []
    list.push(...personal)

    // 团队知识库：遍历我所在的正常团队，逐个查询其绑定的知识库
    // 后端接口会自动校验「团队正常 + 我是活跃成员 + 知识库正常」，无权/不可用则跳过
    const team: BindableKb[] = []
    if (workspaces.data.code === 200) {
      for (const ws of workspaces.data.data.records) {
        if (ws.status !== 1 || ws.memberStatus !== 1) continue
        try {
          const t = await kbApi.team(ws.id)
          if (t.data.code === 200) team.push({ ...t.data.data, workspaceId: ws.id })
        } catch {
          /* 无权限或团队/知识库不可用，忽略该团队 */
        }
      }
    }
    list.push(...team)

    allKbs.value = list

    // 已绑定：个人 kbIds + 团队 workspaceIds 反查对应的知识库 id
    const boundPersonalIds = bound.data.code === 200 ? (bound.data.data.kbIds ?? []) : []
    const boundWorkspaceIds = new Set<number>(
      bound.data.code === 200 ? (bound.data.data.workspaceIds ?? []) : [],
    )
    const boundTeamIds = team
      .filter((k) => k.workspaceId != null && boundWorkspaceIds.has(k.workspaceId))
      .map((k) => k.id)
    boundIds.value = [...boundPersonalIds, ...boundTeamIds]
  } catch {
    allKbs.value = []
  } finally {
    bindingLoading.value = false
  }
}

async function saveBind() {
  if (!binding.value) return
  bindingBusy.value = true
  try {
    await agentApi.bindKbs(binding.value.id, boundIds.value)
    toast.success('已保存关联')
    binding.value = null
  } catch {
    /* 拦截器已提示 */
  } finally {
    bindingBusy.value = false
  }
}

function toggleKb(id: number) {
  const i = boundIds.value.indexOf(id)
  if (i >= 0) boundIds.value.splice(i, 1)
  else boundIds.value.push(id)
}

const boundKbs = computed(() => allKbs.value.filter((k) => boundIds.value.includes(k.id)))
const availableKbs = computed(() => allKbs.value.filter((k) => !boundIds.value.includes(k.id)))

onMounted(load)
</script>

<template>
  <div class="mx-auto max-w-5xl px-4 py-8 md:px-8">
    <div class="mb-6 flex items-center justify-between">
      <div>
        <h1 class="text-lg font-medium text-ink">智能体</h1>
        <p class="mt-1 text-sm text-faint">每个智能体拥有独立的对话与知识库关联。</p>
      </div>
      <button class="btn-primary" @click="showCreate = true">
        <AppIcon name="plus" :size="15" />
        新建智能体
      </button>
    </div>

    <div v-if="loading" class="flex justify-center py-16">
      <AppSpinner label="加载中…" />
    </div>

    <AppEmpty
      v-else-if="!agents.length"
      icon="bot"
      title="还没有智能体"
      description="创建第一个智能体，开始对话。"
    >
      <button class="btn-primary" @click="showCreate = true">
        <AppIcon name="plus" :size="15" />
        新建智能体
      </button>
    </AppEmpty>

    <div v-else class="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
      <button
        v-for="agent in agents"
        :key="agent.id"
        class="card group p-5 text-left transition-all hover:shadow-lift"
        @click="router.push(`/chat/${agent.id}`)"
      >
        <div class="flex items-start justify-between">
          <div class="flex h-10 w-10 items-center justify-center rounded-sm bg-surface-muted text-lg font-medium text-ink">
            {{ initialOf(agent.name) }}
          </div>
          <div class="flex gap-0.5 opacity-0 transition-opacity group-hover:opacity-100">
            <button
              class="rounded-sm p-1.5 text-faint hover:bg-surface-muted hover:text-ink"
              title="关联知识库"
              @click.stop="openBind(agent)"
            >
              <AppIcon name="book" :size="15" />
            </button>
            <button
              class="rounded-sm p-1.5 text-faint hover:bg-surface-muted hover:text-ink"
              title="重命名"
              @click.stop="openRename(agent)"
            >
              <AppIcon name="edit" :size="15" />
            </button>
            <button
              class="rounded-sm p-1.5 text-faint hover:bg-red-bg hover:text-red-text"
              title="删除"
              @click.stop="deleting = agent"
            >
              <AppIcon name="trash" :size="15" />
            </button>
          </div>
        </div>
        <h3 class="mt-4 text-sm font-medium text-ink">{{ agent.name }}</h3>
        <p class="mt-1 text-xs text-faint">创建于 {{ formatTime(agent.createTime, false) }}</p>
      </button>
    </div>

    <!-- 新建 -->
    <AppModal :open="showCreate" title="新建智能体" @close="showCreate = false">
      <label class="label">名称</label>
      <input v-model="newName" class="input" placeholder="例如：写作助手" @keydown.enter="create" />
      <div class="mt-5 flex justify-end gap-2">
        <button class="btn-secondary" @click="showCreate = false">取消</button>
        <button class="btn-primary" :disabled="creating" @click="create">
          <AppIcon v-if="creating" name="refresh" :size="14" class="animate-spin" />
          创建
        </button>
      </div>
    </AppModal>

    <!-- 重命名 -->
    <AppModal :open="!!renaming" title="重命名智能体" @close="renaming = null">
      <label class="label">名称</label>
      <input v-model="renameValue" class="input" @keydown.enter="saveRename" />
      <div class="mt-5 flex justify-end gap-2">
        <button class="btn-secondary" @click="renaming = null">取消</button>
        <button class="btn-primary" :disabled="renamingBusy" @click="saveRename">保存</button>
      </div>
    </AppModal>

    <!-- 关联知识库 -->
    <AppModal :open="!!binding" title="关联知识库" width="max-w-lg" @close="binding = null">
      <p class="text-sm text-faint">为「{{ binding?.name }}」勾选可用的个人或团队知识库。</p>
      <div v-if="bindingLoading" class="flex justify-center py-8">
        <AppSpinner />
      </div>
      <template v-else>
        <div class="mt-4 max-h-[50vh] overflow-y-auto pr-1">
          <!-- 已绑定 -->
          <div>
            <div class="mb-1.5 flex items-center gap-2 px-1">
              <span class="text-xs font-medium text-ink">已绑定</span>
              <span class="rounded-full bg-surface-muted px-2 py-0.5 text-xs text-faint">{{ boundKbs.length }}</span>
            </div>
            <div v-if="!boundKbs.length" class="rounded-sm border border-dashed border-line px-3 py-4 text-center text-xs text-faint">
              尚未绑定任何知识库
            </div>
            <div v-else class="space-y-1">
              <div
                v-for="kb in boundKbs"
                :key="kb.id"
                class="flex items-center gap-3 rounded-sm bg-surface-muted px-3 py-2.5"
              >
                <span class="flex h-4 w-4 shrink-0 items-center justify-center rounded-sm bg-ink-solid text-white">
                  <AppIcon name="check" :size="11" />
                </span>
                <div class="min-w-0 flex-1">
                  <p class="truncate text-sm text-ink">
                    {{ kb.name }}
                    <span
                      v-if="kb.workspaceId != null"
                      class="ml-1.5 rounded-sm bg-surface-muted px-1.5 py-0.5 text-[10px] text-faint"
                    >团队</span>
                  </p>
                  <p class="text-xs text-faint">{{ kb.docCount }} 篇文档</p>
                </div>
                <button
                  class="rounded-sm p-1.5 text-faint transition-colors hover:bg-surface hover:text-red-text"
                  title="取消绑定"
                  @click="toggleKb(kb.id)"
                >
                  <AppIcon name="x" :size="14" />
                </button>
              </div>
            </div>
          </div>

          <!-- 可绑定 -->
          <div class="mt-5">
            <div class="mb-1.5 flex items-center gap-2 px-1">
              <span class="text-xs font-medium text-ink">可绑定</span>
              <span class="rounded-full bg-surface-muted px-2 py-0.5 text-xs text-faint">{{ availableKbs.length }}</span>
            </div>
            <div v-if="!availableKbs.length" class="rounded-sm border border-dashed border-line px-3 py-4 text-center text-xs text-faint">
              暂无可绑定的知识库，请先在「知识库」中创建。
            </div>
            <div v-else class="space-y-1">
              <div
                v-for="kb in availableKbs"
                :key="kb.id"
                class="flex items-center gap-3 rounded-sm px-3 py-2.5 transition-colors hover:bg-surface-muted"
              >
                <span class="flex h-4 w-4 shrink-0 items-center justify-center rounded-sm border border-line-strong"></span>
                <div class="min-w-0 flex-1">
                  <p class="truncate text-sm text-ink">
                    {{ kb.name }}
                    <span
                      v-if="kb.workspaceId != null"
                      class="ml-1.5 rounded-sm bg-surface-muted px-1.5 py-0.5 text-[10px] text-faint"
                    >团队</span>
                  </p>
                  <p class="text-xs text-faint">{{ kb.docCount }} 篇文档</p>
                </div>
                <button
                  class="rounded-sm p-1.5 text-faint transition-colors hover:bg-surface hover:text-ink"
                  title="绑定"
                  @click="toggleKb(kb.id)"
                >
                  <AppIcon name="plus" :size="14" />
                </button>
              </div>
            </div>
          </div>
        </div>
      </template>
      <div class="mt-5 flex justify-end gap-2">
        <button class="btn-secondary" @click="binding = null">取消</button>
        <button class="btn-primary" :disabled="bindingBusy" @click="saveBind">保存</button>
      </div>
    </AppModal>

    <!-- 删除 -->
    <AppConfirm
      :open="!!deleting"
      title="删除智能体"
      :message="`确定删除「${deleting?.name}」吗？其对话记录将一并删除，且不可恢复。`"
      confirm-text="删除"
      :danger="true"
      :loading="deletingBusy"
      @confirm="remove"
      @cancel="deleting = null"
    />
  </div>
</template>
