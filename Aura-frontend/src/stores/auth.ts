import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { clearToken, setToken, getToken, USER_KEY } from '@/api/config'
import { authApi } from '@/api'
import type { UserVO } from '@/types'

function readUser(): UserVO | null {
  try {
    const raw = localStorage.getItem(USER_KEY)
    return raw ? (JSON.parse(raw) as UserVO) : null
  } catch {
    return null
  }
}

export const useAuthStore = defineStore('auth', () => {
  const user = ref<UserVO | null>(readUser())
  const token = ref<string>(getToken())

  const isLoggedIn = computed(() => !!token.value)
  const isAdmin = computed(() => user.value?.role === 2)

  function setSession(tokenVal: string, userVal: UserVO) {
    token.value = tokenVal
    user.value = userVal
    setToken(tokenVal)
    localStorage.setItem(USER_KEY, JSON.stringify(userVal))
  }

  async function login(payload: {
    username: string
    password?: string
    code?: string
    loginWay: number
    isRemember: boolean
  }) {
    const { data } = await authApi.login(payload)
    if (data.code !== 200) throw new Error(data.msg)
    const vo = data.data
    if (!vo.token) throw new Error(data.msg || '登录失败')
    setSession(vo.token, vo)
    return vo
  }

  async function logout() {
    try {
      await authApi.logout()
    } catch {
      /* 忽略登出接口异常，本地照常清理 */
    }
    token.value = ''
    user.value = null
    clearToken()
  }

  function reset() {
    token.value = ''
    user.value = null
    clearToken()
  }

  return { user, token, isLoggedIn, isAdmin, login, logout, reset }
})
