<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { agentApi, kbApi } from '@/api'
import { toast } from '@/stores/toast'
import type { Agent, KnowledgeBase } from '@/types'
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
const personalKbs = ref<KnowledgeBase[]>([])
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
  bindingLoading.value = true
  try {
    const [kbs, bound] = await Promise.all([
      kbApi.list(1, 100),
      agentApi.bindingKbs(agent.id),
    ])
    personalKbs.value = kbs.data.code === 200 ? kbs.data.data.records.filter((k) => k.isTeam === 0) : []
    boundIds.value = bound.data.code === 200 ? bound.data.data.personalKbIds ?? [] : []
  } catch {
    personalKbs.value = []
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
      <p class="text-sm text-faint">为「{{ binding?.name }}」选择可用的个人知识库。</p>
      <div v-if="bindingLoading" class="flex justify-center py-8">
        <AppSpinner />
      </div>
      <div v-else-if="!personalKbs.length" class="py-8 text-center text-sm text-faint">
        暂无可关联的个人知识库，请先在「知识库」中创建。
      </div>
      <div v-else class="mt-4 max-h-72 space-y-1 overflow-y-auto">
        <label
          v-for="kb in personalKbs"
          :key="kb.id"
          class="flex cursor-pointer items-center gap-3 rounded-sm px-3 py-2.5 transition-colors hover:bg-surface-muted"
        >
          <input
            type="checkbox"
            class="accent-black"
            :checked="boundIds.includes(kb.id)"
            @change="toggleKb(kb.id)"
          />
          <div class="min-w-0 flex-1">
            <p class="truncate text-sm text-ink">{{ kb.name }}</p>
            <p class="text-xs text-faint">{{ kb.docCount }} 篇文档</p>
          </div>
        </label>
      </div>
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
