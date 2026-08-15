import axios, { type AxiosInstance } from 'axios'
import { API_BASE, getToken, clearToken } from './config'
import { toast } from '@/stores/toast'
import { toSnakeCase, toCamelCase } from '@/utils/transform'

export const http: AxiosInstance = axios.create({
  baseURL: API_BASE,
  timeout: 30_000,
})

// 请求拦截：统一携带 Sa-Token，并把 JSON 请求体的键 camelCase -> snake_case
http.interceptors.request.use((config) => {
  const token = getToken()
  if (token) config.headers['satoken'] = token
  if (config.data !== null && typeof config.data === 'object') {
    config.data = toSnakeCase(config.data)
  }
  return config
})

// 响应拦截：统一解包 Result，处理鉴权失效与错误提示
http.interceptors.response.use(
  (response) => {
    // 二进制流（文档下载/预览）直接返回
    if (response.config.responseType === 'blob') return response

    // 响应体键 snake_case -> camelCase，匹配前端类型
    response.data = toCamelCase(response.data)

    const body = response.data
    // 已解包或非标准 Result 结构，直接透传
    if (body == null || typeof body !== 'object' || !('code' in body)) return response

    if (body.code === 200) return response
    if (body.code === 401) {
      clearToken()
      toast.error(body.msg || '登录已失效，请重新登录')
      // 避免在登录页反复跳转
      if (!location.pathname.includes('/login')) {
        setTimeout(() => (location.href = '/login'), 800)
      }
      return Promise.reject(new Error(body.msg))
    }
    toast.error(body.msg || '请求失败')
    return Promise.reject(new Error(body.msg || '请求失败'))
  },
  (error) => {
    const status = error.response?.status
    const msg = error.response?.data?.msg || (status === 401 ? '未登录' : '网络异常，请稍后重试')
    if (status === 401) {
      clearToken()
      if (!location.pathname.includes('/login')) location.href = '/login'
    } else {
      toast.error(msg)
    }
    return Promise.reject(error)
  },
)

/** 从 Result 中解出 data（供调用方直接拿业务数据） */
export function unwrap<T>(promise: Promise<{ data: { data: T } }>): Promise<T> {
  return promise.then((r) => r.data.data)
}
