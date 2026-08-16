import { API_BASE } from '@/api/config'

/**
 * 后端静态资源（头像/Logo/文档图片等）统一前缀。
 * 后端在 context-path /aura 下挂载 /uploads/**，而 DB 存的是省略了 /uploads 的短路径，
 * 如 /avatars/x.png、/workspace_logos/x.png、/temp_images/x.png。
 * 这里统一补成 /uploads/... 再拼上 API_BASE。
 */
const INTERNAL_UPLOAD_PREFIXES = ['/avatars/', '/workspace_logos/', '/temp_images/']

export function assetUrl(path?: string | null): string {
  if (!path) return ''
  if (/^https?:\/\//.test(path) || path.startsWith('data:')) return path
  if (path.startsWith(API_BASE)) return path
  if (path.startsWith('/uploads/')) return `${API_BASE}${path}`
  if (INTERNAL_UPLOAD_PREFIXES.some((p) => path.startsWith(p))) {
    return `${API_BASE}/uploads${path}`
  }
  return `${API_BASE}${path}`
}
