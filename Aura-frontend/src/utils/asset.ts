import { API_BASE } from '@/api/config'

/**
 * 后端静态资源（头像/Logo/文档图片等）统一前缀。
 * 后端在 context-path /aura 下挂载 /uploads/**，DB 中存的是相对路径如 /uploads/avatars/x.png。
 */
export function assetUrl(path?: string | null): string {
  if (!path) return ''
  if (/^https?:\/\//.test(path) || path.startsWith('data:')) return path
  if (path.startsWith(API_BASE)) return path
  return `${API_BASE}${path}`
}
