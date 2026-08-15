import { API_BASE, getToken } from './config'
import { toSnakeCase } from '@/utils/transform'

export interface InterruptPayload {
  question: string
  options: string[]
}

export interface StreamHandlers {
  onText: (text: string) => void
  onInterrupt: (payload: InterruptPayload) => void
  onDone: () => void
  onError: (message: string) => void
}

/**
 * 流式对话（POST + SSE）。
 *
 * 链路：Python 产出 `data: <text>` / `event: interrupt` + `data: <json>`，
 * Java 用 SseEmitter 原样 send，Spring 会再包一层 `data:`，因此前端收到的
 * data 内容可能是 `data: xxx` / `event: interrupt` / `data: {...}`。
 * 这里对 data 做「去一层 data:/event: 前缀」的容错，兼容有/无二次包装两种形态。
 *
 * 因为是 POST 返回的 SSE，不能用原生 EventSource（仅支持 GET），改用 fetch 流式解析。
 * 后端不显式发 done 事件，流关闭即视为结束。
 */
export async function streamChat(
  path: string,
  body: Record<string, unknown>,
  handlers: StreamHandlers,
  signal?: AbortSignal,
): Promise<void> {
  let response: Response
  try {
    response = await fetch(`${API_BASE}${path}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', satoken: getToken() },
      body: JSON.stringify(toSnakeCase(body)),
      signal,
    })
  } catch (e) {
    if ((e as Error)?.name === 'AbortError') return
    handlers.onError('网络连接失败')
    return
  }

  if (!response.ok || !response.body) {
    handlers.onError(`请求失败（${response.status}）`)
    return
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  const parser = createSSEParser(handlers)

  try {
    for (;;) {
      const { done, value } = await reader.read()
      if (done) break
      parser.feed(decoder.decode(value, { stream: true }))
    }
    parser.feed(decoder.decode())
    handlers.onDone()
  } catch (e) {
    if ((e as Error)?.name === 'AbortError') return
    handlers.onError('读取响应流失败')
  }
}

function createSSEParser(handlers: StreamHandlers) {
  let buffer = ''
  let pendingInterrupt = false

  function handleField(field: string, value: string) {
    if (field === 'event') {
      if (value === 'interrupt') pendingInterrupt = true
      else if (value === 'done') handlers.onDone()
      else if (value === 'error') handlers.onError('生成过程出错')
      return
    }
    if (field !== 'data') return

    // 二次包装容错：data 内可能再套一层 data:/event:
    let content = value
    if (content.startsWith('event:')) {
      const ev = content.slice(6).trim()
      if (ev === 'interrupt') pendingInterrupt = true
      else if (ev === 'done') handlers.onDone()
      else if (ev === 'error') handlers.onError('生成过程出错')
      return
    }
    if (content.startsWith('data:')) {
      content = content.slice(5).replace(/^ /, '')
    }

    if (pendingInterrupt) {
      pendingInterrupt = false
      try {
        handlers.onInterrupt(JSON.parse(content))
      } catch {
        handlers.onInterrupt({ question: content, options: ['approve', 'reject', 'edit'] })
      }
      return
    }

    // 无标记却直接出现 JSON 的中断载荷（兜底）
    if (content.trimStart().startsWith('{')) {
      try {
        const obj = JSON.parse(content)
        if (obj && 'question' in obj && Array.isArray(obj.options)) {
          handlers.onInterrupt(obj)
          return
        }
      } catch {
        /* ignore */
      }
    }

    handlers.onText(content)
  }

  function feed(chunk: string) {
    buffer += chunk
    let idx: number
    while ((idx = buffer.indexOf('\n')) >= 0) {
      const line = buffer.slice(0, idx).replace(/\r$/, '')
      buffer = buffer.slice(idx + 1)
      if (line === '') continue
      const colon = line.indexOf(':')
      if (colon <= 0) continue
      const field = line.slice(0, colon).trim()
      const value = line.slice(colon + 1).replace(/^ /, '')
      handleField(field, value)
    }
  }

  return { feed }
}
