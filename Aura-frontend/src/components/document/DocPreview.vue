<script setup lang="ts">
import { computed, ref, watch, onBeforeUnmount } from 'vue'
import { http } from '@/api/http'
import type { Document } from '@/types'
import AppIcon from '@/components/ui/AppIcon.vue'
import AppModal from '@/components/ui/AppModal.vue'
import AppSpinner from '@/components/ui/AppSpinner.vue'
import AppEmpty from '@/components/ui/AppEmpty.vue'
import Markdown from '@/components/ui/Markdown.vue'

const props = defineProps<{ open: boolean; doc: Document | null }>()
const emit = defineEmits<{ (e: 'close'): void }>()

const loading = ref(false)
const error = ref(false)
const text = ref('')
const pdfUrl = ref('')

const isTxt = computed(() => props.doc?.fileType === 'txt')
const isMd = computed(() => props.doc?.fileType === 'md')
const isPdf = computed(() => props.doc?.fileType === 'pdf')

function revokePdf() {
  if (pdfUrl.value) {
    URL.revokeObjectURL(pdfUrl.value)
    pdfUrl.value = ''
  }
}

async function load() {
  if (!props.doc || !props.open) return
  loading.value = true
  error.value = false
  text.value = ''
  revokePdf()
  try {
    const res = await http.get('/document/content', {
      params: { documentId: props.doc.id, disposition: 'inline' },
      responseType: 'blob',
    })
    const blob = res.data as Blob
    if (isTxt.value || isMd.value) {
      // 按 UTF-8 解码文本，避免后端未带 charset 导致乱码
      text.value = await blob.text()
    } else if (isPdf.value) {
      const pdfBlob = new Blob([blob], { type: 'application/pdf' })
      pdfUrl.value = URL.createObjectURL(pdfBlob)
    }
    // docx 等格式不支持内联预览，仅显示下载提示
  } catch {
    error.value = true
    /* 拦截器已提示 */
  } finally {
    loading.value = false
  }
}

async function download() {
  if (!props.doc) return
  try {
    const res = await http.get('/document/content', {
      params: { documentId: props.doc.id, disposition: 'attachment' },
      responseType: 'blob',
    })
    const url = URL.createObjectURL(res.data as Blob)
    const a = document.createElement('a')
    a.href = url
    a.download = props.doc.fileName
    a.click()
    setTimeout(() => URL.revokeObjectURL(url), 60_000)
  } catch {
    /* 拦截器已提示 */
  }
}

watch(
  [() => props.open, () => props.doc],
  () => {
    if (props.open && props.doc) load()
    else revokePdf()
  },
  { immediate: true },
)

onBeforeUnmount(revokePdf)
</script>

<template>
  <AppModal :open="open" :title="doc?.fileName ?? '文档预览'" width="max-w-4xl" @close="emit('close')">
    <div class="min-h-[40vh] max-h-[70vh] overflow-auto">
      <div v-if="loading" class="flex justify-center py-16">
        <AppSpinner label="加载中…" />
      </div>
      <AppEmpty v-else-if="error" icon="file" title="预览加载失败" description="请稍后重试，或直接下载文档查看。" />
      <pre v-else-if="isTxt" class="whitespace-pre-wrap break-words text-sm leading-6 text-ink">{{ text }}</pre>
      <div v-else-if="isMd">
        <Markdown :content="text" />
      </div>
      <iframe v-else-if="isPdf && pdfUrl" :src="pdfUrl" class="h-[70vh] w-full" />
      <div v-else class="flex flex-col items-center justify-center py-16 text-center">
        <AppIcon name="file-text" :size="32" class="text-faint" />
        <p class="mt-3 text-sm text-muted">该格式暂不支持在线预览，请下载后查看。</p>
        <button class="btn-primary mt-4" @click="download">
          <AppIcon name="download" :size="15" />
          下载文档
        </button>
      </div>
    </div>
  </AppModal>
</template>
