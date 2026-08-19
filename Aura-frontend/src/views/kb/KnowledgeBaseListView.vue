<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { kbApi } from '@/api'
import { toast } from '@/stores/toast'
import type { KnowledgeBase } from '@/types'
import { formatTime, initialOf } from '@/utils/format'
import AppIcon from '@/components/ui/AppIcon.vue'
import AppBadge from '@/components/ui/AppBadge.vue'
import AppModal from '@/components/ui/AppModal.vue'
import AppConfirm from '@/components/ui/AppConfirm.vue'
import AppEmpty from '@/components/ui/AppEmpty.vue'
import AppSpinner from '@/components/ui/AppSpinner.vue'

const router = useRouter()

// 全部（status=1）与回收站（status=0）分开维护：后端 /kb/get 两种状态都会返回，
// 按状态拆开，回收站里的知识库可恢复（/kb/restore）或彻底删除（/kb/delete/force）
const view = ref<'active' | 'recycle'>('active')
const activeKbs = ref<KnowledgeBase[]>([])
const deletedKbs = ref<KnowledgeBase[]>([])
const loading = ref(false)
const keyword = ref('')
const searching = ref(false)

const showCreate = ref(false)
const createForm = ref({ name: '', description: '' })
const creating = ref(false)

const renaming = ref<KnowledgeBase | null>(null)
const renameForm = ref({ name: '', description: '' })
const renamingBusy = ref(false)

const deleting = ref<KnowledgeBase | null>(null)
const deletingBusy = ref(false)

async function load() {
  loading.value = true
  try {
    const { data } = await kbApi.list(1, 100)
    applyRecords(data.code === 200 ? data.data.records : [])
  } catch {
    activeKbs.value = []
    deletedKbs.value = []
  } finally {
    loading.value = false
  }
}

async function search() {
  searching.value = true
  try {
    const kw = keyword.value.trim()
    const { data } = kw ? await kbApi.search(kw, 1, 100) : await kbApi.list(1, 100)
    applyRecords(data.code === 200 ? data.data.records : [])
  } catch {
    activeKbs.value = []
    deletedKbs.value = []
  } finally {
    searching.value = false
  }
}

// 拆分正常 / 已删除。注意 /kb/search 只查 status=1，所以搜索后切换到回收站会重新 load 全量列表
function applyRecords(records: KnowledgeBase[]) {
  activeKbs.value = records.filter((k) => k.status === 1)
  deletedKbs.value = records.filter((k) => k.status !== 1)
}

async function switchView(v: 'active' | 'recycle') {
  if (view.value === v) return
  view.value = v
  keyword.value = ''
  await load()
}

// 后端保留期：逻辑删除后 30 天内可恢复，到期由定时任务（@Scheduled 每分钟）
// 按 updateTime 自动物理清理（见 SysKnowledgeBaseServiceImpl.cleanExpiredKnowledgeBases）
const RETENTION_DAYS = 30
const DAY_MS = 24 * 60 * 60 * 1000

function daysLeft(kb: KnowledgeBase): number {
  const updated = new Date(kb.updateTime).getTime()
  if (Number.isNaN(updated)) return RETENTION_DAYS
  return Math.max(0, Math.ceil((updated + RETENTION_DAYS * DAY_MS - Date.now()) / DAY_MS))
}

async function create() {
  const name = createForm.value.name.trim()
  const description = createForm.value.description.trim()
  if (!name) return toast.error('请输入知识库名称')
  if (!description) return toast.error('请输入知识库描述')
  creating.value = true
  try {
    await kbApi.create({
      name,
      description,
      isTeam: 0,
    })
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

function openRename(kb: KnowledgeBase) {
  renaming.value = kb
  renameForm.value = { name: kb.name, description: kb.description ?? '' }
}

async function saveRename() {
  if (!renaming.value) return
  const name = renameForm.value.name.trim()
  const description = renameForm.value.description.trim()
  if (!name) return toast.error('名称不能为空')
  if (!description) return toast.error('请输入知识库描述')

  // 后端一次仅允许修改 name 或 description 之一（type 指定），按变更项分别提交
  const nameChanged = name !== renaming.value.name
  const descChanged = description !== (renaming.value.description ?? '')
  if (!nameChanged && !descChanged) {
    renaming.value = null
    return
  }

  renamingBusy.value = true
  try {
    if (nameChanged) {
      await kbApi.updateMy({ kbId: renaming.value.id, type: 'name', name })
    }
    if (descChanged) {
      await kbApi.updateMy({ kbId: renaming.value.id, type: 'description', description })
    }
    toast.success('已保存')
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
    await kbApi.logicDelete(deleting.value.id)
    toast.success('已删除')
    deleting.value = null
    await load()
  } catch {
    /* 拦截器已提示 */
  } finally {
    deletingBusy.value = false
  }
}

// ---------- 回收站：恢复 / 彻底删除 ----------
const restoring = ref<KnowledgeBase | null>(null)
const restoringBusy = ref(false)

async function doRestore() {
  if (!restoring.value) return
  restoringBusy.value = true
  try {
    await kbApi.restore(restoring.value.id)
    toast.success('已恢复')
    restoring.value = null
    await load()
  } catch {
    /* 拦截器已提示 */
  } finally {
    restoringBusy.value = false
  }
}

const forceDeleting = ref<KnowledgeBase | null>(null)
const forceDeletingBusy = ref(false)

async function doForceDelete() {
  if (!forceDeleting.value) return
  forceDeletingBusy.value = true
  try {
    await kbApi.forceDelete(forceDeleting.value.id)
    toast.success('已彻底删除')
    forceDeleting.value = null
    await load()
  } catch {
    /* 拦截器已提示 */
  } finally {
    forceDeletingBusy.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="mx-auto max-w-5xl px-4 py-8 md:px-8">
    <div class="mb-6 flex flex-wrap items-center justify-between gap-3">
      <div>
        <h1 class="text-lg font-medium text-ink">知识库</h1>
        <p class="mt-1 text-sm text-faint">上传文档构建语料，供智能体检索增强。</p>
      </div>
      <div class="flex items-center gap-2">
        <div v-if="view === 'active'" class="relative">
          <button
            type="button"
            class="absolute left-1 top-1/2 -translate-y-1/2 rounded-sm p-1 text-faint transition-colors hover:text-ink"
            title="搜索知识库"
            @click="search"
          >
            <AppIcon name="search" :size="15" />
          </button>
          <input
            v-model="keyword"
            class="input !pl-9"
            placeholder="搜索知识库"
            @keydown.enter="search"
          />
        </div>
        <button class="btn-primary" @click="showCreate = true">
          <AppIcon name="plus" :size="15" />
          新建
        </button>
      </div>
    </div>

    <!-- 全部 / 回收站 切换（删除走逻辑删除，回收站可恢复或彻底删除） -->
    <div class="mb-5 flex items-center gap-6 border-b border-line">
      <button
        class="-mb-px flex items-center gap-1.5 border-b-2 pb-2.5 text-sm transition-colors"
        :class="view === 'active' ? 'border-ink font-medium text-ink' : 'border-transparent text-muted hover:text-ink'"
        @click="switchView('active')"
      >
        全部
        <span v-if="activeKbs.length" class="rounded-full bg-surface-muted px-1.5 text-xs text-muted">{{ activeKbs.length }}</span>
      </button>
      <button
        class="-mb-px flex items-center gap-1.5 border-b-2 pb-2.5 text-sm transition-colors"
        :class="view === 'recycle' ? 'border-ink font-medium text-ink' : 'border-transparent text-muted hover:text-ink'"
        @click="switchView('recycle')"
      >
        回收站
        <span v-if="deletedKbs.length" class="rounded-full bg-red-bg px-1.5 text-xs text-red-text">{{ deletedKbs.length }}</span>
      </button>
    </div>

    <p v-if="view === 'recycle'" class="mb-3 text-xs text-faint">
      已删除的知识库保留 {{ RETENTION_DAYS }} 天，到期将自动永久清理，请及时恢复。
    </p>

    <div v-if="loading || searching" class="flex justify-center py-16">
      <AppSpinner label="加载中…" />
    </div>

    <!-- 全部：正常知识库卡片 -->
    <AppEmpty
      v-else-if="view === 'active' && !activeKbs.length"
      icon="book"
      title="还没有知识库"
      description="创建知识库并上传文档，智能体即可检索到相关内容。"
    >
      <button class="btn-primary" @click="showCreate = true">
        <AppIcon name="plus" :size="15" />
        新建知识库
      </button>
    </AppEmpty>

    <div v-else-if="view === 'active'" class="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
      <button
        v-for="kb in activeKbs"
        :key="kb.id"
        class="card group p-5 text-left transition-all hover:shadow-lift"
        @click="router.push(`/kb/${kb.id}`)"
      >
        <div class="flex items-start justify-between">
          <div class="flex h-10 w-10 items-center justify-center rounded-sm bg-surface-muted text-lg font-medium text-ink">
            {{ initialOf(kb.name) }}
          </div>
          <div v-if="kb.isTeam === 0" class="flex gap-0.5 opacity-0 transition-opacity group-hover:opacity-100">
            <button
              class="rounded-sm p-1.5 text-faint hover:bg-surface-muted hover:text-ink"
              title="编辑"
              @click.stop="openRename(kb)"
            >
              <AppIcon name="edit" :size="15" />
            </button>
            <button
              class="rounded-sm p-1.5 text-faint hover:bg-red-bg hover:text-red-text"
              title="删除"
              @click.stop="deleting = kb"
            >
              <AppIcon name="trash" :size="15" />
            </button>
          </div>
          <AppBadge v-else tone="blue">团队</AppBadge>
        </div>
        <h3 class="mt-4 text-sm font-medium text-ink">{{ kb.name }}</h3>
        <p class="mt-1 line-clamp-2 min-h-[2.5rem] text-xs leading-5 text-faint">
          {{ kb.description || '暂无描述' }}
        </p>
        <p class="mt-3 text-xs text-faint">{{ kb.docCount }} 篇文档 · {{ formatTime(kb.createTime, false) }}</p>
      </button>
    </div>

    <!-- 回收站：已删除知识库，可恢复或彻底删除 -->
    <AppEmpty
      v-else-if="view === 'recycle' && !deletedKbs.length"
      icon="trash"
      title="回收站是空的"
      description="删除的知识库会出现在这里，可随时恢复。"
    />
    <div v-else class="space-y-3">
      <div
        v-for="kb in deletedKbs"
        :key="kb.id"
        class="card flex items-center gap-4 p-4"
      >
        <div class="flex h-10 w-10 shrink-0 items-center justify-center rounded-sm bg-surface-muted text-lg font-medium text-faint">
          {{ initialOf(kb.name) }}
        </div>
        <div class="min-w-0 flex-1">
          <h3 class="truncate text-sm font-medium text-ink">{{ kb.name }}</h3>
          <p class="mt-0.5 truncate text-xs text-faint">{{ kb.description || '暂无描述' }}</p>
        </div>
        <span
          class="hidden shrink-0 text-xs sm:inline"
          :class="daysLeft(kb) <= 3 ? 'text-red-text' : 'text-faint'"
          title="到期后自动永久清理"
        >
          剩 {{ daysLeft(kb) }} 天自动清理
        </span>
        <span class="shrink-0 text-xs text-red-text">已删除</span>
        <button class="btn-secondary shrink-0" :disabled="restoringBusy" @click="restoring = kb">恢复</button>
        <button
          class="shrink-0 rounded-sm p-1.5 text-faint transition-colors hover:bg-red-bg hover:text-red-text"
          title="彻底删除（不可恢复）"
          @click="forceDeleting = kb"
        >
          <AppIcon name="trash" :size="16" />
        </button>
      </div>
    </div>

    <!-- 新建 -->
    <AppModal :open="showCreate" title="新建知识库" @close="showCreate = false">
      <div class="space-y-4">
        <div>
          <label class="label">名称</label>
          <input v-model="createForm.name" class="input" placeholder="例如：产品文档" />
        </div>
        <div>
          <label class="label">描述</label>
          <textarea v-model="createForm.description" class="input resize-none" rows="3" placeholder="简要说明知识库用途（必填）" />
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

    <!-- 编辑 -->
    <AppModal :open="!!renaming" title="编辑知识库" @close="renaming = null">
      <div class="space-y-4">
        <div>
          <label class="label">名称</label>
          <input v-model="renameForm.name" class="input" />
        </div>
        <div>
          <label class="label">描述</label>
          <textarea v-model="renameForm.description" class="input resize-none" rows="3" />
        </div>
      </div>
      <div class="mt-5 flex justify-end gap-2">
        <button class="btn-secondary" @click="renaming = null">取消</button>
        <button class="btn-primary" :disabled="renamingBusy" @click="saveRename">保存</button>
      </div>
    </AppModal>

    <!-- 删除（逻辑删除，进回收站） -->
    <AppConfirm
      :open="!!deleting"
      title="删除知识库"
      :message="`确定删除「${deleting?.name}」吗？删除后进入回收站，智能体将无法检索其中的内容，可随时恢复。`"
      confirm-text="删除"
      :danger="true"
      :loading="deletingBusy"
      @confirm="remove"
      @cancel="deleting = null"
    />

    <!-- 彻底删除（物理删除，不可恢复） -->
    <AppConfirm
      :open="!!forceDeleting"
      title="彻底删除"
      :message="`将永久删除「${forceDeleting?.name}」及其全部文档与向量数据，不可恢复。确定继续吗？`"
      confirm-text="彻底删除"
      :danger="true"
      :loading="forceDeletingBusy"
      @confirm="doForceDelete"
      @cancel="forceDeleting = null"
    />
  </div>
</template>
