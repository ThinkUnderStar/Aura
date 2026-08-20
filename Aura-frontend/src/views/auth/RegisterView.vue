<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useThemeStore } from '@/stores/theme'
import { authApi } from '@/api'
import { toast } from '@/stores/toast'
import { validatePhone, validateUsername, validatePassword } from '@/utils/validate'
import { resolveAuraLogo } from '@/utils/auraLogo'
import AppIcon from '@/components/ui/AppIcon.vue'

const router = useRouter()
const theme = useThemeStore()
// 品牌 logo 随主题切换（暗色→白主体，亮色→黑主体）
const brandLogo = computed(() => resolveAuraLogo(theme.isDark))

const form = reactive({ username: '', phone: '', password: '', repeatPassword: '', code: '' })
const showPwd = ref(false)
const loading = ref(false)
const sending = ref(false)
const countdown = ref(0)

// 实时格式校验：与后端 ValidateUtils / AuthServiceImpl 规则一致
const errors = reactive<Record<string, string>>({
  username: '',
  phone: '',
  password: '',
  repeatPassword: '',
  code: '',
})

function validateField(field: 'username' | 'phone' | 'password' | 'repeatPassword' | 'code') {
  switch (field) {
    case 'username':
      errors.username = validateUsername(form.username) ?? ''
      break
    case 'phone':
      errors.phone = validatePhone(form.phone) ?? ''
      break
    case 'password':
      errors.password = validatePassword(form.password) ?? ''
      // 密码变化时同步重新校验确认密码
      errors.repeatPassword = form.repeatPassword !== form.password ? '两次输入的密码不一致' : ''
      break
    case 'repeatPassword':
      errors.repeatPassword = form.repeatPassword !== form.password ? '两次输入的密码不一致' : ''
      break
    case 'code':
      errors.code = form.code ? '' : '请输入验证码'
      break
  }
}

function startCountdown() {
  countdown.value = 60
  const t = setInterval(() => {
    countdown.value -= 1
    if (countdown.value <= 0) clearInterval(t)
  }, 1000)
}

async function sendCode() {
  validateField('phone')
  if (errors.phone) return toast.error(errors.phone)
  sending.value = true
  try {
    await authApi.sendCode(form.phone, 'register')
    toast.success('验证码已发送')
    startCountdown()
  } catch {
    /* 拦截器已提示 */
  } finally {
    sending.value = false
  }
}

async function submit() {
  ;(['username', 'phone', 'password', 'repeatPassword', 'code'] as const).forEach(validateField)
  const firstError = Object.values(errors).find(Boolean)
  if (firstError) return toast.error(firstError)

  loading.value = true
  try {
    await authApi.register({ ...form })
    toast.success('注册成功，请登录')
    router.push('/login')
  } catch {
    /* 拦截器已提示 */
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="flex min-h-screen items-center justify-center px-4 py-12">
    <div class="w-full max-w-sm">
      <div class="mb-8 text-center">
        <div class="mx-auto mb-3 flex h-10 w-10 items-center justify-center overflow-hidden rounded-md bg-surface-muted p-1.5">
          <img :src="brandLogo" alt="Aura" class="h-full w-full object-contain" />
        </div>
        <h1 class="font-serif text-xl tracking-tight text-ink">创建账号</h1>
        <p class="mt-1 text-sm text-faint">加入 Aura 智能体工作台</p>
      </div>

      <div class="card p-6">
        <form class="space-y-4" @submit.prevent="submit">
          <div>
            <label class="label">用户名</label>
            <input
              v-model="form.username"
              class="input"
              :class="{ 'input-error': errors.username }"
              placeholder="4~16 位，字母开头，仅字母/数字/下划线"
              autocomplete="username"
              @input="validateField('username')"
            />
            <p v-if="errors.username" class="field-error">{{ errors.username }}</p>
          </div>

          <div>
            <label class="label">手机号</label>
            <input
              v-model="form.phone"
              class="input"
              :class="{ 'input-error': errors.phone }"
              placeholder="请输入手机号"
              autocomplete="tel"
              @input="validateField('phone')"
            />
            <p v-if="errors.phone" class="field-error">{{ errors.phone }}</p>
          </div>

          <div>
            <label class="label">密码</label>
            <div class="relative">
              <input
                v-model="form.password"
                class="input pr-10"
                :class="{ 'input-error': errors.password }"
                :type="showPwd ? 'text' : 'password'"
                placeholder="8~20 位，须含字母和数字"
                autocomplete="new-password"
                @input="validateField('password')"
              />
              <button
                type="button"
                class="absolute right-2.5 top-1/2 -translate-y-1/2 text-faint hover:text-ink"
                @click="showPwd = !showPwd"
              >
                <AppIcon :name="showPwd ? 'eye' : 'lock'" :size="16" />
              </button>
            </div>
            <p v-if="errors.password" class="field-error">{{ errors.password }}</p>
          </div>

          <div>
            <label class="label">确认密码</label>
            <input
              v-model="form.repeatPassword"
              class="input"
              :class="{ 'input-error': errors.repeatPassword }"
              type="password"
              placeholder="再次输入密码"
              autocomplete="new-password"
              @input="validateField('repeatPassword')"
            />
            <p v-if="errors.repeatPassword" class="field-error">{{ errors.repeatPassword }}</p>
          </div>

          <div>
            <label class="label">验证码</label>
            <div class="flex gap-2">
              <input
                v-model="form.code"
                class="input flex-1"
                :class="{ 'input-error': errors.code }"
                placeholder="短信验证码"
                @input="validateField('code')"
              />
              <button
                type="button"
                class="btn-secondary shrink-0"
                :disabled="sending || countdown > 0"
                @click="sendCode"
              >
                {{ countdown > 0 ? `${countdown}s` : '获取验证码' }}
              </button>
            </div>
            <p v-if="errors.code" class="field-error">{{ errors.code }}</p>
          </div>

          <button type="submit" class="btn-primary w-full !py-2.5" :disabled="loading">
            <AppIcon v-if="loading" name="refresh" :size="14" class="animate-spin" />
            {{ loading ? '注册中…' : '注册' }}
          </button>
        </form>
      </div>

      <p class="mt-6 text-center text-sm text-faint">
        已有账号？
        <router-link to="/login" class="text-ink underline-offset-2 hover:underline">去登录</router-link>
      </p>
    </div>
  </div>
</template>
