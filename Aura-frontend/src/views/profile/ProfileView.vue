<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { authApi, userApi } from '@/api'
import { useAuthStore } from '@/stores/auth'
import { useAvatarGenStore } from '@/stores/avatarGen'
import { toast } from '@/stores/toast'
import { validateEmail, validateImageFile, validateUsername } from '@/utils/validate'
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
// 实时格式校验：与后端 ValidateUtils 规则一致
const errors = reactive<Record<string, string>>({ username: '', email: '' })

function validateField(field: 'username' | 'email') {
  if (field === 'username') errors.username = validateUsername(form.value.username) ?? ''
  else errors.email = validateEmail(form.value.email) ?? ''
}

const savingUsername = ref(false)
const sendingCode = ref(false)
const savingEmail = ref(false)
const avatarInput = ref<HTMLInputElement | null>(null)
const uploadingAvatar = ref(false)

const gen = useAvatarGenStore()
const savingGenerated = ref(false)
const discarding = ref(false)

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
  errors.username = ''
  errors.email = ''
  editing.value = true
}

function cancelEdit() {
  editing.value = false
  emailCode.value = ''
  errors.username = ''
  errors.email = ''
}

async function saveUsername() {
  validateField('username')
  if (errors.username) return toast.error(errors.username)
  const username = form.value.username.trim()
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
  validateField('email')
  if (errors.email) return toast.error(errors.email)
  const email = form.value.email.trim()
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
  validateField('email')
  if (errors.email) return toast.error(errors.email)
  const email = form.value.email.trim()
  const code = emailCode.value.trim()
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
  const err = validateImageFile(file)
  if (err) {
    toast.error(err)
    input.value = ''
    return
  }
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
  if (!gen.prompt.trim()) return toast.error('请输入头像描述')
  gen.generating = true
  try {
    const { data } = await userApi.generateAvatar(gen.prompt.trim())
    gen.generated = data.code === 200 ? data.data : null
  } catch {
    /* 拦截器已提示 */
  } finally {
    gen.generating = false
  }
}

async function saveGenerated() {
  if (!gen.generated) return
  savingGenerated.value = true
  try {
    // 后端期望的是纯文件名（不含 /temp_images/ 前缀），且 isSaved=1 表示保存为头像
    const imageName = gen.generated.replace(/^\/temp_images\//, '')
    const { data } = await userApi.saveGeneratedAvatar(imageName, 1)
    if (data.code === 200 && data.data) auth.updateUser({ avatar: data.data })
    toast.success('头像已保存')
    gen.reset()
  } catch {
    /* 拦截器已提示 */
  } finally {
    savingGenerated.value = false
  }
}

/** 删除临时图片：复用 /avatar/generate/save 的 isSaved=0 分支，删除失败不阻断 */
async function deleteTempImage() {
  if (!gen.generated) return
  const imageName = gen.generated.replace(/^\/temp_images\//, '')
  try {
    await userApi.saveGeneratedAvatar(imageName, 0)
  } catch {
    /* 拦截器已提示；文件不存在/限流等场景由后端定时任务兜底清理 */
  }
}

/** 放弃当前生成结果：删除临时图片并回到输入状态 */
async function discardGenerated() {
  if (!gen.generated) return
  discarding.value = true
  try {
    await deleteTempImage()
  } finally {
    gen.generated = null
    discarding.value = false
  }
}

/** 重新生成：先删除旧临时图，再复用 /avatar/generate（后端每次随机种子出图） */
async function regenerate() {
  if (!gen.generated) return
  gen.generating = true
  try {
    await deleteTempImage()
  } finally {
    gen.generated = null
  }
  generate()
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
            <button class="btn-secondary !px-3 !py-1.5 text-xs" @click="gen.open()">
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
            <input
              v-model="form.username"
              class="input flex-1"
              :class="{ 'input-error': errors.username }"
              @input="validateField('username')"
            />
            <button class="btn-secondary shrink-0" :disabled="savingUsername" @click="saveUsername">
              <AppIcon v-if="savingUsername" name="refresh" :size="14" class="animate-spin" />
              保存
            </button>
          </div>
          <p v-if="errors.username" class="field-error">{{ errors.username }}</p>
        </div>
        <div>
          <label class="label">邮箱</label>
          <div class="flex gap-2">
            <input
              v-model="form.email"
              class="input flex-1"
              :class="{ 'input-error': errors.email }"
              placeholder="绑定邮箱"
              @input="validateField('email')"
            />
            <button class="btn-secondary shrink-0" :disabled="sendingCode" @click="sendEmailCode">
              {{ sendingCode ? '发送中…' : '发送验证码' }}
            </button>
          </div>
          <p v-if="errors.email" class="field-error">{{ errors.email }}</p>
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
    <AppModal :open="gen.showGenerate" title="AI 生成头像" @close="gen.close()">
      <label class="label">描述</label>
      <input v-model="gen.prompt" class="input" placeholder="例如：简约几何风格的抽象头像" @keydown.enter="generate" />
      <div v-if="gen.generating" class="mt-4 flex justify-center py-6">
        <AppIcon name="refresh" :size="20" class="animate-spin text-faint" />
      </div>
      <div v-else-if="gen.generated" class="mt-4 flex flex-col items-center gap-3">
        <img :src="assetUrl(gen.generated)" alt="生成头像预览" class="h-28 w-28 rounded-lg border border-line object-cover" />
        <p class="text-xs text-faint">预览满意后可保存为头像，不满意可重新生成</p>
      </div>
      <div class="mt-5 flex justify-end gap-2">
        <template v-if="!gen.generated">
          <button class="btn-secondary" @click="gen.close()">取消</button>
          <button class="btn-primary" :disabled="gen.generating" @click="generate">生成</button>
        </template>
        <template v-else>
          <button class="btn-secondary" :disabled="discarding" @click="discardGenerated">
            <AppIcon v-if="discarding" name="refresh" :size="14" class="animate-spin" />
            放弃
          </button>
          <button class="btn-secondary" @click="regenerate">重新生成</button>
          <button class="btn-primary" :disabled="savingGenerated" @click="saveGenerated">
            <AppIcon v-if="savingGenerated" name="refresh" :size="14" class="animate-spin" />
            保存为头像
          </button>
        </template>
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
