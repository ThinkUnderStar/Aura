<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { docApi, kbApi } from '@/api'
import { http } from '@/api/http'
import { toast } from '@/stores/toast'
import type { Document, KnowledgeBase } from '@/types'
import { DOC_STATUS } from '@/constants/enums'
import { formatSize, formatTime } from '@/utils/format'
import AppIcon from '@/components/ui/AppIcon.vue'
import AppBadge from '@/components/ui/AppBadge.vue'
import AppModal from '@/components/ui/AppModal.vue'
import AppConfirm from '@/components/ui/AppConfirm.vue'
import AppEmpty from '@/components/ui/AppEmpty.vue'
import AppSpinner from '@/components/ui/AppSpinner.vue'
import AppPagination from '@/components/ui/AppPagination.vue'

const route = useRoute()
const router = useRouter()
const kbId = computed(() => Number(route.params.kbId))

const kb = ref<KnowledgeBase | null>(null)
const docs = ref<Document[]>([])
const total = ref(0)
const page = ref(1)
const size = 20
const loading = ref(false)
const uploading = ref(false)

const showEdit = ref(false)
const editForm = ref({ name: '', description: '' })
const saving = ref(false)

const deleting = ref<Document | null>(null)
const deletingBusy = ref(false)

const fileInput = ref<HTMLInputElement | null>(null)

const statusBadge = (s: number) =>
  s === DOC_STATUS.DONE ? { tone: 'green' as const, label: '已索引' }
    : s === DOC_STATUS.FAILED ? { tone: 'red' as const, label: '失败' }
    : { tone: 'yellow' as const, label: '索引中' }

async function loadKb() {
  try {
    const { data } = await kbApi.list(1, 100)
    kb.value = data.code === 200 ? (data.data.records.find((k) => k.id === kbId.value) ?? null) : null
    if (kb.value) {
      editForm.value = { name: kb.value.name, description: kb.value.description ?? '' }
    }
  } catch {
    kb.value = null
  }
}

async function loadDocs() {
  loading.value = true
  try {
    const { data } = await docApi.list(kbId.value, page.value, size)
    docs.value = data.code === 200 ? data.data.records : []
    total.value = data.code === 200 ? data.data.total : 0
  } catch {
    docs.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

async function saveEdit() {
  const name = editForm.value.name.trim()
  if (!name) return toast.error('名称不能为空')
  saving.value = true
  try {
    if (kb.value?.isTeam === 1) {
      // 团队知识库：仅名称可改（后端按 type 逐项更新）
      await kbApi.updateTeam({ id: kbId.value, type: 'name', value: name })
    } else {
      await kbApi.updateMy({ id: kbId.value, name, description: editForm.value.description.trim() || undefined })
    }
    toast.success('已保存')
    showEdit.value = false
    await loadKb()
  } catch {
    /* 拦截器已提示 */
  } finally {
    saving.value = false
  }
}

async function onFiles(e: Event) {
  const input = e.target as HTMLInputElement
  const files = input.files
  if (!files?.length) return
  uploading.value = true
  try {
    for (const file of Array.from(files)) {
      await docApi.upload(file, kbId.value)
    }
    toast.success('上传完成，正在索引')
    await loadDocs()
  } catch {
    /* 拦截器已提示 */
  } finally {
    uploading.value = false
    input.value = ''
  }
}

async function remove() {
  if (!deleting.value) return
  deletingBusy.value = true
  try {
    await docApi.remove(deleting.value.id)
    toast.success('已删除')
    deleting.value = null
    await loadDocs()
  } catch {
    /* 拦截器已提示 */
  } finally {
    deletingBusy.value = false
  }
}

// 通过带鉴权头的 blob 请求预览/下载（后端以 satoken 头鉴权，直链无法带 token）
async function fetchContent(doc: Document, disposition: 'inline' | 'attachment') {
  try {
    const res = await http.get('/document/content', {
      params: { documentId: doc.id, disposition },
      responseType: 'blob',
    })
    const blob = res.data as Blob
    const url = URL.createObjectURL(blob)
    if (disposition === 'inline') {
      window.open(url, '_blank')
    } else {
      const a = document.createElement('a')
      a.href = url
      a.download = doc.fileName
      a.click()
    }
    setTimeout(() => URL.revokeObjectURL(url), 60_000)
  } catch {
    /* 拦截器已提示 */
  }
}

function onPage(p: number) {
  page.value = p
  loadDocs()
}

onMounted(() => {
  loadKb()
  loadDocs()
})
</script>

<template>
  <div class="mx-auto max-w-5xl px-4 py-8 md:px-8">
    <!-- 头部 -->
    <div class="mb-6">
      <button class="mb-4 flex items-center gap-1 text-sm text-muted hover:text-ink" @click="router.back()">
        <AppIcon name="arrow-left" :size="15" />
        返回
      </button>
      <div class="flex flex-wrap items-start justify-between gap-3">
        <div class="flex items-center gap-3">
          <div class="flex h-11 w-11 items-center justify-center rounded-sm bg-surface-muted text-lg font-medium text-ink">
            {{ (kb?.name || '?').charAt(0).toUpperCase() }}
          </div>
          <div>
            <div class="flex items-center gap-2">
              <h1 class="text-lg font-medium text-ink">{{ kb?.name }}</h1>
              <AppBadge v-if="kb?.isTeam === 1" tone="blue">团队</AppBadge>
            </div>
            <p class="mt-0.5 text-sm text-faint">{{ kb?.description || '暂无描述' }} · {{ kb?.docCount ?? docs.length }} 篇文档</p>
          </div>
        </div>
        <div class="flex items-center gap-2">
          <button class="btn-secondary" @click="showEdit = true">
            <AppIcon name="edit" :size="15" />
            编辑
          </button>
          <button class="btn-primary" :disabled="uploading" @click="fileInput?.click()">
            <AppIcon :name="uploading ? 'refresh' : 'upload'" :size="15" :class="uploading ? 'animate-spin' : ''" />
            {{ uploading ? '上传中…' : '上传文档' }}
          </button>
          <input ref="fileInput" type="file" multiple class="hidden" @change="onFiles" />
        </div>
      </div>
    </div>

    <!-- 文档列表 -->
    <div class="card overflow-hidden">
      <div v-if="loading" class="flex justify-center py-16">
        <AppSpinner label="加载文档…" />
      </div>

      <AppEmpty
        v-else-if="!docs.length"
        icon="file"
        title="暂无文档"
        description="上传 PDF、Word、Markdown 等文档，系统将自动索引。"
      />

      <div v-else>
        <!-- 桌面表格 -->
        <table class="hidden w-full text-left text-sm md:table">
          <thead class="border-b border-line text-xs text-faint">
            <tr>
              <th class="px-5 py-3 font-medium">文件名</th>
              <th class="px-5 py-3 font-medium">大小</th>
              <th class="px-5 py-3 font-medium">状态</th>
              <th class="px-5 py-3 font-medium">上传时间</th>
              <th class="px-5 py-3 text-right font-medium">操作</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-line">
            <tr v-for="doc in docs" :key="doc.id" class="group transition-colors hover:bg-surface-muted">
              <td class="max-w-xs px-5 py-3">
                <div class="flex items-center gap-2.5">
                  <AppIcon name="file-text" :size="16" class="shrink-0 text-faint" />
                  <span class="truncate font-medium text-ink">{{ doc.fileName }}</span>
                </div>
              </td>
              <td class="px-5 py-3 text-muted">{{ formatSize(doc.fileSize) }}</td>
              <td class="px-5 py-3">
                <AppBadge :tone="statusBadge(doc.status).tone">{{ statusBadge(doc.status).label }}</AppBadge>
              </td>
              <td class="px-5 py-3 text-muted">{{ formatTime(doc.createTime, false) }}</td>
              <td class="px-5 py-3">
                <div class="flex justify-end gap-0.5 opacity-0 transition-opacity group-hover:opacity-100">
                  <button class="rounded-sm p-1.5 text-faint hover:bg-surface-muted hover:text-ink" title="预览" @click="fetchContent(doc, 'inline')">
                    <AppIcon name="eye" :size="15" />
                  </button>
                  <button class="rounded-sm p-1.5 text-faint hover:bg-surface-muted hover:text-ink" title="下载" @click="fetchContent(doc, 'attachment')">
                    <AppIcon name="download" :size="15" />
                  </button>
                  <button class="rounded-sm p-1.5 text-faint hover:bg-red-bg hover:text-red-text" title="删除" @click="deleting = doc">
                    <AppIcon name="trash" :size="15" />
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>

        <!-- 移动列表 -->
        <ul class="divide-y divide-line md:hidden">
          <li v-for="doc in docs" :key="doc.id" class="flex items-center gap-3 px-4 py-3">
            <AppIcon name="file-text" :size="18" class="shrink-0 text-faint" />
            <div class="min-w-0 flex-1">
              <p class="truncate text-sm font-medium text-ink">{{ doc.fileName }}</p>
              <p class="mt-0.5 text-xs text-faint">{{ formatSize(doc.fileSize) }} · {{ formatTime(doc.createTime, false) }}</p>
            </div>
            <AppBadge :tone="statusBadge(doc.status).tone">{{ statusBadge(doc.status).label }}</AppBadge>
            <button class="rounded-sm p-1.5 text-faint hover:bg-red-bg hover:text-red-text" @click="deleting = doc">
              <AppIcon name="trash" :size="15" />
            </button>
          </li>
        </ul>

        <div class="border-t border-line px-4">
          <AppPagination :page="page" :total="total" :size="size" @change="onPage" />
        </div>
      </div>
    </div>

    <!-- 编辑 -->
    <AppModal :open="showEdit" title="编辑知识库" @close="showEdit = false">
      <div class="space-y-4">
        <div>
          <label class="label">名称</label>
          <input v-model="editForm.name" class="input" />
        </div>
        <div v-if="kb?.isTeam !== 1">
          <label class="label">描述</label>
          <textarea v-model="editForm.description" class="input resize-none" rows="3" />
        </div>
      </div>
      <div class="mt-5 flex justify-end gap-2">
        <button class="btn-secondary" @click="showEdit = false">取消</button>
        <button class="btn-primary" :disabled="saving" @click="saveEdit">保存</button>
      </div>
    </AppModal>

    <!-- 删除 -->
    <AppConfirm
      :open="!!deleting"
      title="删除文档"
      :message="`确定删除「${deleting?.fileName}」吗？该文档将从知识库中移除。`"
      confirm-text="删除"
      :danger="true"
      :loading="deletingBusy"
      @confirm="remove"
      @cancel="deleting = null"
    />
  </div>
</template>
