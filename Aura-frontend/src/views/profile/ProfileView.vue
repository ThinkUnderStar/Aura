<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { authApi, userApi } from '@/api'
import { useAuthStore } from '@/stores/auth'
import { toast } from '@/stores/toast'
import { assetUrl } from '@/utils/asset'
import AppIcon from '@/components/ui/AppIcon.vue'
import AppAvatar from '@/components/ui/AppAvatar.vue'
import AppBadge from '@/components/ui/AppBadge.vue'
import AppModal from '@/components/ui/AppModal.vue'
import AppConfirm from '@/components/ui/AppConfirm.vue'

const router = useRouter()
const auth = useAuthStore()

const form = ref({ username: '', email: '' })
const saving = ref(false)
const avatarInput = ref<HTMLInputElement | null>(null)
const uploadingAvatar = ref(false)

const showGenerate = ref(false)
const prompt = ref('')
const generating = ref(false)
const generated = ref<string | null>(null)
const savingGenerated = ref(false)

const showDelete = ref(false)
const deleting = ref(false)

const isAdmin = computed(() => auth.isAdmin)

function syncForm() {
  form.value = { username: auth.user?.username ?? '', email: auth.user?.email ?? '' }
}
syncForm()

async function saveProfile() {
  if (!form.value.username.trim()) return toast.error('用户名不能为空')
  saving.value = true
  try {
    await userApi.update({ username: form.value.username.trim(), email: form.value.email.trim() || undefined })
    toast.success('已保存')
    if (auth.user) {
      auth.user.username = form.value.username.trim()
      auth.user.email = form.value.email.trim() || null
    }
  } catch {
    /* 拦截器已提示 */
  } finally {
    saving.value = false
  }
}

async function onAvatar(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  uploadingAvatar.value = true
  try {
    const { data } = await userApi.uploadAvatar(file)
    if (data.code === 200 && data.data && auth.user) auth.user.avatar = data.data
    toast.success('头像已更新')
  } catch {
    /* 拦截器已提示 */
  } finally {
    uploadingAvatar.value = false
    input.value = ''
  }
}

async function generate() {
  if (!prompt.value.trim()) return toast.error('请输入头像描述')
  generating.value = true
  try {
    const { data } = await userApi.generateAvatar(prompt.value.trim())
    generated.value = data.code === 200 ? data.data : null
  } catch {
    /* 拦截器已提示 */
  } finally {
    generating.value = false
  }
}

async function saveGenerated() {
  if (!generated.value) return
  savingGenerated.value = true
  try {
    const { data } = await userApi.saveGeneratedAvatar(generated.value)
    if (data.code === 200 && data.data && auth.user) auth.user.avatar = data.data
    toast.success('头像已保存')
    showGenerate.value = false
    generated.value = null
    prompt.value = ''
  } catch {
    /* 拦截器已提示 */
  } finally {
    savingGenerated.value = false
  }
}

async function logout() {
  await auth.logout()
  router.push('/login')
}

async function deleteAccount() {
  deleting.value = true
  try {
    await authApi.deleteAccount()
    toast.success('账号已注销')
    auth.reset()
    router.push('/login')
  } catch {
    /* 拦截器已提示 */
  } finally {
    deleting.value = false
  }
}
</script>

<template>
  <div class="mx-auto max-w-2xl px-4 py-8 md:px-8">
    <h1 class="mb-6 text-lg font-medium text-ink">个人中心</h1>

    <!-- 头像 -->
    <div class="card mb-4 p-5">
      <div class="flex items-center gap-4">
        <AppAvatar :src="auth.user?.avatar" :name="auth.user?.username" :size="64" />
        <div>
          <div class="flex items-center gap-2">
            <p class="text-base font-medium text-ink">{{ auth.user?.username }}</p>
            <AppBadge v-if="isAdmin" tone="yellow">管理员</AppBadge>
          </div>
          <p class="mt-0.5 text-sm text-faint">{{ auth.user?.phone || auth.user?.email || '未填写联系方式' }}</p>
          <div class="mt-2 flex gap-2">
            <button class="btn-secondary !px-3 !py-1.5 text-xs" :disabled="uploadingAvatar" @click="avatarInput?.click()">
              <AppIcon :name="uploadingAvatar ? 'refresh' : 'upload'" :size="14" :class="uploadingAvatar ? 'animate-spin' : ''" />
              上传头像
            </button>
            <button class="btn-secondary !px-3 !py-1.5 text-xs" @click="showGenerate = true">
              <AppIcon name="sparkle" :size="14" />
              AI 生成头像
            </button>
          </div>
          <input ref="avatarInput" type="file" accept="image/*" class="hidden" @change="onAvatar" />
        </div>
      </div>
    </div>

    <!-- 基本信息 -->
    <div class="card mb-4 p-5">
      <h3 class="mb-4 text-sm font-medium text-ink">基本信息</h3>
      <div class="space-y-4">
        <div>
          <label class="label">用户名</label>
          <input v-model="form.username" class="input" />
        </div>
        <div>
          <label class="label">邮箱</label>
          <input v-model="form.email" class="input" placeholder="可选" />
        </div>
        <div class="flex justify-end">
          <button class="btn-primary" :disabled="saving" @click="saveProfile">
            <AppIcon v-if="saving" name="refresh" :size="14" class="animate-spin" />
            保存修改
          </button>
        </div>
      </div>
    </div>

    <!-- 账号操作 -->
    <div class="card p-5">
      <h3 class="mb-4 text-sm font-medium text-ink">账号操作</h3>
      <div class="flex flex-wrap gap-2">
        <button class="btn-secondary" @click="logout">
          <AppIcon name="log-out" :size="15" />
          退出登录
        </button>
        <button class="btn-danger" @click="showDelete = true">
          <AppIcon name="trash" :size="15" />
          注销账号
        </button>
      </div>
    </div>

    <!-- AI 生成头像 -->
    <AppModal :open="showGenerate" title="AI 生成头像" @close="showGenerate = false">
      <label class="label">描述</label>
      <input v-model="prompt" class="input" placeholder="例如：简约几何风格的抽象头像" @keydown.enter="generate" />
      <div v-if="generating" class="mt-4 flex justify-center py-6">
        <AppIcon name="refresh" :size="20" class="animate-spin text-faint" />
      </div>
      <div v-else-if="generated" class="mt-4 flex flex-col items-center gap-3">
        <img :src="assetUrl(generated)" alt="生成头像预览" class="h-28 w-28 rounded-lg border border-line object-cover" />
        <p class="text-xs text-faint">预览满意后可保存为头像</p>
      </div>
      <div class="mt-5 flex justify-end gap-2">
        <button class="btn-secondary" @click="showGenerate = false">取消</button>
        <button v-if="!generated" class="btn-primary" :disabled="generating" @click="generate">
          生成
        </button>
        <button v-else class="btn-primary" :disabled="savingGenerated" @click="saveGenerated">
          <AppIcon v-if="savingGenerated" name="refresh" :size="14" class="animate-spin" />
          保存为头像
        </button>
      </div>
    </AppModal>

    <!-- 注销确认 -->
    <AppConfirm
      :open="showDelete"
      title="注销账号"
      message="注销后账号及全部数据将被永久删除，且不可恢复。确定继续吗？"
      confirm-text="注销"
      :danger="true"
      :loading="deleting"
      @confirm="deleteAccount"
      @cancel="showDelete = false"
    />
  </div>
</template>
