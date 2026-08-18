<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useNotificationStore } from '@/stores/notification'
import { authApi } from '@/api'
import { toast } from '@/stores/toast'
import AppIcon from '@/components/ui/AppIcon.vue'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const notif = useNotificationStore()

const way = ref<1 | 2>(1) // 1-密码 2-验证码
const form = reactive({ username: '', password: '', code: '' })
const showPwd = ref(false)
const remember = ref(true)
const loading = ref(false)
const sending = ref(false)
const countdown = ref(0)

function startCountdown() {
  countdown.value = 60
  const t = setInterval(() => {
    countdown.value -= 1
    if (countdown.value <= 0) clearInterval(t)
  }, 1000)
}

async function sendCode() {
  if (!form.username) return toast.error('请先输入账号 / 手机号')
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
  if (!form.username) return toast.error('请输入账号')
  if (way.value === 1 && !form.password) return toast.error('请输入密码')
  if (way.value === 2 && !form.code) return toast.error('请输入验证码')
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
        <div class="mx-auto mb-3 flex h-10 w-10 items-center justify-center rounded-md bg-ink-solid text-white">
          <span class="font-serif text-lg leading-none">A</span>
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
            @click="way = 1"
          >
            密码登录
          </button>
          <button
            class="rounded-sm py-1.5 text-sm transition-colors"
            :class="way === 2 ? 'bg-surface font-medium text-ink shadow-lift' : 'text-muted hover:text-ink'"
            @click="way = 2"
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
              :placeholder="way === 1 ? '用户名 / 手机号' : '请输入手机号'"
              autocomplete="username"
            />
          </div>

          <div v-if="way === 1">
            <label class="label">密码</label>
            <div class="relative">
              <input
                v-model="form.password"
                class="input pr-10"
                :type="showPwd ? 'text' : 'password'"
                placeholder="请输入密码"
                autocomplete="current-password"
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

          <div v-else>
            <label class="label">验证码</label>
            <div class="flex gap-2">
              <input v-model="form.code" class="input flex-1" placeholder="6 位验证码" />
              <button
                type="button"
                class="btn-secondary shrink-0"
                :disabled="sending || countdown > 0"
                @click="sendCode"
              >
                {{ countdown > 0 ? `${countdown}s` : '获取验证码' }}
              </button>
            </div>
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
