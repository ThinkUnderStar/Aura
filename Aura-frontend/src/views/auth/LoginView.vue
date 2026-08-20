<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useNotificationStore } from '@/stores/notification'
import { useThemeStore } from '@/stores/theme'
import { authApi } from '@/api'
import { toast } from '@/stores/toast'
import { validateLoginAccount } from '@/utils/validate'
import { resolveAuraLogo } from '@/utils/auraLogo'
import AppIcon from '@/components/ui/AppIcon.vue'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const notif = useNotificationStore()
const theme = useThemeStore()
// 品牌 logo 随主题切换（暗色→白主体，亮色→黑主体）
const brandLogo = computed(() => resolveAuraLogo(theme.isDark))

const way = ref<1 | 2>(1) // 1-密码 2-验证码
const form = reactive({ username: '', password: '', code: '' })
const showPwd = ref(false)
const remember = ref(true)
const loading = ref(false)
const sending = ref(false)
const countdown = ref(0)

// 实时格式校验：账号须为手机号或邮箱（与 AuthServiceImpl 规则一致）
const errors = reactive<Record<string, string>>({ username: '', password: '', code: '' })

function validateField(field: 'username' | 'password' | 'code') {
  switch (field) {
    case 'username':
      errors.username = validateLoginAccount(form.username) ?? ''
      break
    case 'password':
      errors.password = way.value === 1 ? (form.password ? '' : '请输入密码') : ''
      break
    case 'code':
      errors.code = way.value === 2 ? (form.code ? '' : '请输入验证码') : ''
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

function switchWay(w: 1 | 2) {
  way.value = w
  validateField('password')
  validateField('code')
}

async function sendCode() {
  validateField('username')
  if (errors.username) return toast.error(errors.username)
  sending.value = true
  try {
    await authApi.sendCode(form.username, 'login')
    toast.success('验证码已发送')
    startCountdown()
  } catch {
    /* 拦截器已提示 */
  } finally {
    sending.value = false
  }
}

async function submit() {
  ;(['username', 'password', 'code'] as const).forEach(validateField)
  const firstError = Object.values(errors).find(Boolean)
  if (firstError) return toast.error(firstError)
  loading.value = true
  try {
    await auth.login({
      username: form.username,
      password: form.password,
      code: form.code,
      loginWay: way.value,
      isRemember: remember.value,
    })
    toast.success('登录成功')
    await notif.refreshUnread()
    router.push((route.query.redirect as string) || '/chat')
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
      <!-- 品牌 -->
      <div class="mb-8 text-center">
        <div class="mx-auto mb-3 flex h-10 w-10 items-center justify-center overflow-hidden rounded-md bg-surface-muted p-1.5">
          <img :src="brandLogo" alt="Aura" class="h-full w-full object-contain" />
        </div>
        <h1 class="font-serif text-xl tracking-tight text-ink">Aura</h1>
        <p class="mt-1 text-sm text-faint">登录智能体工作台</p>
      </div>

      <div class="card p-6">
        <!-- 登录方式切换 -->
        <div class="mb-5 grid grid-cols-2 gap-1 rounded-sm bg-surface-muted p-1">
          <button
            class="rounded-sm py-1.5 text-sm transition-colors"
            :class="way === 1 ? 'bg-surface font-medium text-ink shadow-lift' : 'text-muted hover:text-ink'"
            @click="switchWay(1)"
          >
            密码登录
          </button>
          <button
            class="rounded-sm py-1.5 text-sm transition-colors"
            :class="way === 2 ? 'bg-surface font-medium text-ink shadow-lift' : 'text-muted hover:text-ink'"
            @click="switchWay(2)"
          >
            验证码登录
          </button>
        </div>

        <form class="space-y-4" @submit.prevent="submit">
          <div>
            <label class="label">{{ way === 1 ? '账号' : '手机号' }}</label>
            <input
              v-model="form.username"
              class="input"
              :class="{ 'input-error': errors.username }"
              :placeholder="way === 1 ? '手机号或邮箱' : '请输入手机号'"
              autocomplete="username"
              @input="validateField('username')"
            />
            <p v-if="errors.username" class="field-error">{{ errors.username }}</p>
          </div>

          <div v-if="way === 1">
            <label class="label">密码</label>
            <div class="relative">
              <input
                v-model="form.password"
                class="input pr-10"
                :class="{ 'input-error': errors.password }"
                :type="showPwd ? 'text' : 'password'"
                placeholder="请输入密码"
                autocomplete="current-password"
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

          <div v-else>
            <label class="label">验证码</label>
            <div class="flex gap-2">
              <input
                v-model="form.code"
                class="input flex-1"
                :class="{ 'input-error': errors.code }"
                placeholder="6 位验证码"
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

          <label class="flex items-center gap-2 text-sm text-muted">
            <input v-model="remember" type="checkbox" class="accent-black" />
            保持登录
          </label>

          <button type="submit" class="btn-primary w-full !py-2.5" :disabled="loading">
            <AppIcon v-if="loading" name="refresh" :size="14" class="animate-spin" />
            {{ loading ? '登录中…' : '登录' }}
          </button>
        </form>
      </div>

      <p class="mt-6 text-center text-sm text-faint">
        还没有账号？
        <router-link to="/register" class="text-ink underline-offset-2 hover:underline">注册</router-link>
      </p>
    </div>
  </div>
</template>
