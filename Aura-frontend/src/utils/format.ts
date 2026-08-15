// 通用格式化工具

/** 日期格式化（无依赖，避免引入 dayjs） */
export function formatTime(value?: string | null, withYear = true): string {
  if (!value) return '—'
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return '—'
  const pad = (n: number) => String(n).padStart(2, '0')
  const hm = `${pad(d.getHours())}:${pad(d.getMinutes())}`
  const md = `${d.getMonth() + 1}月${d.getDate()}日`
  if (withYear) return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${hm}`
  const now = new Date()
  const sameYear = now.getFullYear() === d.getFullYear()
  return sameYear ? `${md} ${hm}` : `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${hm}`
}

/** 文件大小 */
export function formatSize(bytes?: number | null): string {
  if (bytes == null) return '—'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

/** 名称首字（用于无头像实体的确定性占位） */
export function initialOf(name: string): string {
  return (name || '?').trim().charAt(0).toUpperCase()
}
