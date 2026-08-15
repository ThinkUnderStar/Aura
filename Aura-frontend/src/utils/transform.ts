// 后端 application.yaml 配了全局 Jackson `property-naming-strategy: SNAKE_CASE`，
// 因此所有「多词字段」在 JSON 里都是 snake_case（如 login_way、repeat_password、user_id）。
// 前端内部统一用 camelCase，故在 HTTP 边界做双向转换：
//   - 请求体：camelCase -> snake_case
//   - 响应体：snake_case -> camelCase
//
// 唯一例外：LoginDto.isRemember 后端显式标注了 @JsonProperty("isRemember")，
// 覆盖了全局命名策略，必须保持原样发送，不做转换。

/** 请求体中需保持原样（不转 snake_case）的字段 */
const REQUEST_KEEP_AS_IS = new Set(['isRemember'])

function isPlainObject(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
}

function snakeKey(key: string): string {
  if (REQUEST_KEEP_AS_IS.has(key)) return key
  return key.replace(/[A-Z]/g, (ch) => '_' + ch.toLowerCase())
}

function camelKey(key: string): string {
  return key.replace(/_([a-z0-9])/g, (_, ch: string) => ch.toUpperCase())
}

/** 递归把对象键 camelCase -> snake_case（FormData / Blob 原样返回） */
export function toSnakeCase(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(toSnakeCase)
  if (value instanceof FormData || value instanceof Blob) return value
  if (isPlainObject(value)) {
    const out: Record<string, unknown> = {}
    for (const [key, val] of Object.entries(value)) {
      out[snakeKey(key)] = toSnakeCase(val)
    }
    return out
  }
  return value
}

/** 递归把对象键 snake_case -> camelCase */
export function toCamelCase(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(toCamelCase)
  if (value instanceof Blob) return value
  if (isPlainObject(value)) {
    const out: Record<string, unknown> = {}
    for (const [key, val] of Object.entries(value)) {
      out[camelKey(key)] = toCamelCase(val)
    }
    return out
  }
  return value
}
