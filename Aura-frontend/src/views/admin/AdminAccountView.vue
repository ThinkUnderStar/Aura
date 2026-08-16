<script setup lang="ts">
import { reactive, ref } from 'vue'
import { adminApi, authApi } from '@/api'
import { toast } from '@/stores/toast'
import AppIcon from '@/components/ui/AppIcon.vue'

const form = reactive({
  username: '',
  password: '',
  repeatPassword: '',
  phone: '',
  phoneCode: '',
  email: '',
  emailCode: '',
})

const showPwd = ref(false)
const submitting = ref(false)
const phoneSending = ref(false)
const phoneCountdown = ref(0)
const emailSending = ref(false)
const emailCountdown = ref(0)

function startCountdown(kind: 'phone' | 'email') {
  const target = kind === 'phone' ? phoneCountdown : emailCountdown
  target.value = 60
  const t = setInterval(() => {
    target.value -= 1
    if (target.value <= 0) clearInterval(t)
  }, 1000)
}

async function sendPhoneCode() {
  if (!form.phone) return toast.error('请先输入手机号')
  phoneSending.value = true
  try {
    await authApi.sendCode(form.phone, 'register')
    toast.success('验证码已发送')
    startCountdown('phone')
  } catch {
    /* 拦截器已提示 */
  } finally {
    phoneSending.value = false
  }
}

async function sendEmailCode() {
  if (!form.email) return toast.error('请先输入邮箱')
  emailSending.value = true
  try {
    await authApi.sendCode(form.email, 'register')
    toast.success('验证码已发送')
    startCountdown('email')
  } catch {
    /* 拦截器已提示 */
  } finally {
    emailSending.value = false
  }
}

async function submit() {
  if (!form.username) return toast.error('请输入用户名')
  if (!form.password) return toast.error('请输入密码')
  if (form.password.length < 6) return toast.error('密码至少 6 位')
  if (form.password !== form.repeatPassword) return toast.error('两次输入的密码不一致')
  if (!form.phone) return toast.error('请输入手机号')
  if (!form.phoneCode) return toast.error('请输入手机验证码')
  if (!form.email) return toast.error('请输入邮箱')
  if (!form.emailCode) return toast.error('请输入邮箱验证码')

  submitting.value = true
  try {
    await adminApi.registerAdmin({ ...form })
    toast.success('管理员账号已创建')
    form.username = ''
    form.password = ''
    form.repeatPassword = ''
    form.phone = ''
    form.phoneCode = ''
    form.email = ''
    form.emailCode = ''
  } catch {
    /* 拦截器已提示 */
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="mx-auto max-w-xl px-4 py-8 md:px-8">
    <h1 class="text-lg font-medium text-ink">管理员账号</h1>
    <p class="mt-1 text-sm text-faint">创建新的管理员账号，新账号可直接登录并拥有系统管理权限。</p>

    <div class="card mt-6 p-6">
      <form class="space-y-4" @submit.prevent="submit">
        <div>
          <label class="label">用户名</label>
          <input v-model="form.username" class="input" placeholder="用于登录的用户名" autocomplete="off" />
        </div>

        <div class="grid grid-cols-1 gap-4 md:grid-cols-2">
          <div>
            <label class="label">密码</label>
            <div class="relative">
              <input
                v-model="form.password"
                class="input pr-10"
                :type="showPwd ? 'text' : 'password'"
                placeholder="至少 6 位"
                autocomplete="new-password"
              />
              <button
                type="button"
                class="absolute right-2.5 top-1/2 -translate-y-1/2 text-faint hover:text-ink"
                @click="showPwd = !showPwd"
              >
                <AppIcon :name="showPwd ? 'eye' : 'lock'" :size="16" />
              </button>
            </div>
          </div>
          <div>
            <label class="label">确认密码</label>
            <input
              v-model="form.repeatPassword"
              class="input"
              type="password"
              placeholder="再次输入密码"
              autocomplete="new-password"
            />
          </div>
        </div>

        <div>
          <label class="label">手机号</label>
          <div class="flex gap-2">
            <input v-model="form.phone" class="input flex-1" placeholder="请输入手机号" autocomplete="tel" />
            <button
              type="button"
              class="btn-secondary shrink-0"
              :disabled="phoneSending || phoneCountdown > 0"
              @click="sendPhoneCode"
            >
              {{ phoneCountdown > 0 ? `${phoneCountdown}s` : '获取验证码' }}
            </button>
          </div>
        </div>

        <div>
          <label class="label">手机验证码</label>
          <input v-model="form.phoneCode" class="input" placeholder="短信验证码" />
        </div>

        <div>
          <label class="label">邮箱</label>
          <div class="flex gap-2">
            <input v-model="form.email" class="input flex-1" placeholder="请输入邮箱" autocomplete="email" />
            <button
              type="button"
              class="btn-secondary shrink-0"
              :disabled="emailSending || emailCountdown > 0"
              @click="sendEmailCode"
            >
              {{ emailCountdown > 0 ? `${emailCountdown}s` : '获取验证码' }}
            </button>
          </div>
        </div>

        <div>
          <label class="label">邮箱验证码</label>
          <input v-model="form.emailCode" class="input" placeholder="邮箱验证码" />
        </div>

        <button type="submit" class="btn-primary w-full !py-2.5" :disabled="submitting">
          <AppIcon v-if="submitting" name="refresh" :size="14" class="animate-spin" />
          {{ submitting ? '创建中…' : '创建管理员' }}
        </button>
      </form>
    </div>
  </div>
</template>
