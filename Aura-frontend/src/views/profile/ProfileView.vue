<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { authApi, userApi } from '@/api'
import { useAuthStore } from '@/stores/auth'
import { toast } from '@/stores/toast'
import { assetUrl } from '@/utils/asset'
import { desensitizeEmail } from '@/utils/format'
import AppIcon from '@/components/ui/AppIcon.vue'
import AppAvatar from '@/components/ui/AppAvatar.vue'
import AppBadge from '@/components/ui/AppBadge.vue'
import AppModal from '@/components/ui/AppModal.vue'
import AppConfirm from '@/components/ui/AppConfirm.vue'

const router = useRouter()
const auth = useAuthStore()

const form = ref({ username: '', email: '' })
const emailCode = ref('')
const savingUsername = ref(false)
const sendingCode = ref(false)
const savingEmail = ref(false)
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

const editing = ref(false)

function startEdit() {
  syncForm()
  emailCode.value = ''
  editing.value = true
}

function cancelEdit() {
  editing.value = false
  emailCode.value = ''
}

async function saveUsername() {
  const username = form.value.username.trim()
  if (!username) return toast.error('用户名不能为空')
  savingUsername.value = true
  try {
    await userApi.updateUsername(username)
    toast.success('用户名已保存')
    auth.updateUser({ username })
  } catch {
    /* 拦截器已提示 */
  } finally {
    savingUsername.value = false
  }
}

async function sendEmailCode() {
  const email = form.value.email.trim()
  if (!email) return toast.error('请先填写邮箱')
  sendingCode.value = true
  try {
    await authApi.sendCode(email, 'reset')
    toast.success('验证码已发送')
  } catch {
    /* 拦截器已提示 */
  } finally {
    sendingCode.value = false
  }
}

async function saveEmail() {
  const email = form.value.email.trim()
  const code = emailCode.value.trim()
  if (!email) return toast.error('请先填写邮箱')
  if (!code) return toast.error('请输入邮箱验证码')
  savingEmail.value = true
  try {
    await userApi.updateEmail(email, code)
    toast.success('邮箱已绑定')
    // 后端不回传数据，本地按后端脱敏规则存储，避免明文覆盖
    auth.updateUser({ email: desensitizeEmail(email) })
    emailCode.value = ''
  } catch {
    /* 拦截器已提示 */
  } finally {
    savingEmail.value = false
  }
}

async function onAvatar(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  uploadingAvatar.value = true
  try {
    const { data } = await userApi.uploadAvatar(file)
    if (data.code === 200 && data.data) auth.updateUser({ avatar: data.data })
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
    // 后端期望的是纯文件名（不含 /temp_images/ 前缀），且 isSaved=1 表示保存为头像
    const imageName = generated.value.replace(/^\/temp_images\//, '')
    const { data } = await userApi.saveGeneratedAvatar(imageName, 1)
    if (data.code === 200 && data.data) auth.updateUser({ avatar: data.data })
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
      <div class="mb-4 flex items-center justify-between">
        <h3 class="text-sm font-medium text-ink">基本信息</h3>
        <button v-if="!editing" class="btn-secondary !px-3 !py-1.5 text-xs" @click="startEdit">
          <AppIcon name="edit" :size="13" />
          编辑资料
        </button>
        <button v-else class="btn-secondary !px-3 !py-1.5 text-xs" @click="cancelEdit">完成</button>
      </div>

      <!-- 只读展示 -->
      <div v-if="!editing" class="space-y-3">
        <div class="flex items-center justify-between">
          <span class="text-sm text-faint">用户名</span>
          <span class="text-sm text-ink">{{ auth.user?.username || '—' }}</span>
        </div>
        <div class="flex items-center justify-between">
          <span class="text-sm text-faint">邮箱</span>
          <span class="text-sm" :class="auth.user?.email ? 'text-ink' : 'text-faint'">
            {{ auth.user?.email || '未绑定' }}
          </span>
        </div>
        <div class="flex items-center justify-between">
          <span class="text-sm text-faint">手机号</span>
          <span class="text-sm" :class="auth.user?.phone ? 'text-ink' : 'text-faint'">
            {{ auth.user?.phone || '未填写' }}
          </span>
        </div>
      </div>

      <!-- 编辑模式 -->
      <div v-else class="space-y-4">
        <div>
          <label class="label">用户名</label>
          <div class="flex gap-2">
            <input v-model="form.username" class="input flex-1" />
            <button class="btn-secondary shrink-0" :disabled="savingUsername" @click="saveUsername">
              <AppIcon v-if="savingUsername" name="refresh" :size="14" class="animate-spin" />
              保存
            </button>
          </div>
        </div>
        <div>
          <label class="label">邮箱</label>
          <div class="flex gap-2">
            <input v-model="form.email" class="input flex-1" placeholder="绑定邮箱" />
            <button class="btn-secondary shrink-0" :disabled="sendingCode" @click="sendEmailCode">
              {{ sendingCode ? '发送中…' : '发送验证码' }}
            </button>
          </div>
          <div class="mt-2 flex gap-2">
            <input v-model="emailCode" class="input flex-1" placeholder="邮箱验证码" />
            <button class="btn-primary shrink-0" :disabled="savingEmail" @click="saveEmail">
              <AppIcon v-if="savingEmail" name="refresh" :size="14" class="animate-spin" />
              确认绑定
            </button>
          </div>
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
