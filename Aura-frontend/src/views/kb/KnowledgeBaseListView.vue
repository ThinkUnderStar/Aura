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

const kbs = ref<KnowledgeBase[]>([])
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
    kbs.value = data.code === 200 ? data.data.records : []
  } catch {
    kbs.value = []
  } finally {
    loading.value = false
  }
}

async function search() {
  searching.value = true
  try {
    const kw = keyword.value.trim()
    const { data } = kw ? await kbApi.search(kw, 1, 100) : await kbApi.list(1, 100)
    kbs.value = data.code === 200 ? data.data.records : []
  } catch {
    kbs.value = []
  } finally {
    searching.value = false
  }
}

async function create() {
  const name = createForm.value.name.trim()
  if (!name) return toast.error('请输入知识库名称')
  creating.value = true
  try {
    await kbApi.create({
      name,
      description: createForm.value.description.trim() || undefined,
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
  if (!name) return toast.error('名称不能为空')
  renamingBusy.value = true
  try {
    await kbApi.updateMy({
      id: renaming.value.id,
      name,
      description: renameForm.value.description.trim() || undefined,
    })
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
        <div class="relative">
          <AppIcon name="search" :size="15" class="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-faint" />
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

    <div v-if="loading || searching" class="flex justify-center py-16">
      <AppSpinner label="加载中…" />
    </div>

    <AppEmpty
      v-else-if="!kbs.length"
      icon="book"
      title="还没有知识库"
      description="创建知识库并上传文档，智能体即可检索到相关内容。"
    >
      <button class="btn-primary" @click="showCreate = true">
        <AppIcon name="plus" :size="15" />
        新建知识库
      </button>
    </AppEmpty>

    <div v-else class="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
      <button
        v-for="kb in kbs"
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

    <!-- 新建 -->
    <AppModal :open="showCreate" title="新建知识库" @close="showCreate = false">
      <div class="space-y-4">
        <div>
          <label class="label">名称</label>
          <input v-model="createForm.name" class="input" placeholder="例如：产品文档" />
        </div>
        <div>
          <label class="label">描述（可选）</label>
          <textarea v-model="createForm.description" class="input resize-none" rows="3" placeholder="简要说明知识库用途" />
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

    <!-- 删除 -->
    <AppConfirm
      :open="!!deleting"
      title="删除知识库"
      :message="`确定删除「${deleting?.name}」吗？删除后智能体将无法检索其中的内容。`"
      confirm-text="删除"
      :danger="true"
      :loading="deletingBusy"
      @confirm="remove"
      @cancel="deleting = null"
    />
  </div>
</template>
