// API 基础路径：开发走 Vite 代理，生产改为后端域名或同源
export const API_BASE = import.meta.env.VITE_API_BASE || '/aura'

// Sa-Token 存储 key（后端 token-name = satoken）
export const TOKEN_KEY = 'aura_token'
export const USER_KEY = 'aura_user'

export function getToken(): string {
  return localStorage.getItem(TOKEN_KEY) || ''
}

export function setToken(token: string) {
  localStorage.setItem(TOKEN_KEY, token)
}

export function clearToken() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}
