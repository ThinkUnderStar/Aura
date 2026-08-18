import { defineStore } from 'pinia'
import { ref, watch } from 'vue'
import { streamChat, type InterruptPayload, type StreamHandlers } from '@/api/sse'
import { useAuthStore } from '@/stores/auth'

/**
 * 全局流式对话状态。
 *
 * 之前流式连接（AbortController + 各 agent 的流式文本/中断状态）都挂在 ChatView 组件内：
 * 切 agent 只是复用同一组件实例，所以不断流；但切到别的功能模块（知识库、个人中心，
 * 乃至独立的 /admin layout）会卸载 ChatView，onBeforeUnmount 里的 abort() 把流打断。
 *
 * 这里把流状态和连接上提到 store（应用级单例，不随组件销毁），组件卸载后流继续在后台跑，
 * 切回来时由 loadMessages 或 finalize 钩子把完整消息补进列表。登出时才统一中止。
 */
export const useChatStreamStore = defineStore('chatStream', () => {
  const streamingAgents = ref<Record<number, boolean>>({})
  const streamingByAgent = ref<Record<number, string>>({})
  const interruptByAgent = ref<Record<number, InterruptPayload>>({})
  const controllers = new Map<number, AbortController>()

  // 当前存活组件注册的收尾钩子：流结束时先把完整文本落库到视图的消息列表
  let finalizeHook: ((agentId: number) => void) | null = null

  function setFinalizeHook(fn: ((agentId: number) => void) | null) {
    finalizeHook = fn
  }

  function appendText(agentId: number, t: string) {
    streamingByAgent.value[agentId] = (streamingByAgent.value[agentId] ?? '') + t
  }

  function setInterrupt(agentId: number, p: InterruptPayload) {
    interruptByAgent.value[agentId] = p
  }

  /** 收尾：先让当前视图把完整文本落库，再清理流状态与连接 */
  function finalize(agentId: number) {
    finalizeHook?.(agentId)
    delete streamingByAgent.value[agentId]
    delete interruptByAgent.value[agentId]
    delete streamingAgents.value[agentId]
    controllers.delete(agentId)
  }

  async function runStream(
    agentId: number,
    path: string,
    body: Record<string, unknown>,
    handlers: StreamHandlers,
    method: 'POST' | 'PUT' = 'POST',
  ) {
    const controller = new AbortController()
    controllers.set(agentId, controller)
    streamingAgents.value[agentId] = true
    await streamChat(path, body, handlers, controller.signal, method)
  }

  /** 取消并清空某 agent 的流（回溯重生成前使用） */
  function resetAgent(agentId: number) {
    controllers.get(agentId)?.abort()
    delete streamingByAgent.value[agentId]
    delete interruptByAgent.value[agentId]
    delete streamingAgents.value[agentId]
    controllers.delete(agentId)
  }

  /** 登出等场景统一中止全部流式连接并清空状态 */
  function abortAll() {
    for (const c of controllers.values()) c.abort()
    controllers.clear()
    streamingAgents.value = {}
    streamingByAgent.value = {}
    interruptByAgent.value = {}
  }

  // 登出后不允许后台流继续，统一中止
  const auth = useAuthStore()
  watch(
    () => auth.isLoggedIn,
    (loggedIn) => {
      if (!loggedIn) abortAll()
    },
  )

  return {
    streamingAgents,
    streamingByAgent,
    interruptByAgent,
    runStream,
    appendText,
    setInterrupt,
    finalize,
    resetAgent,
    abortAll,
    setFinalizeHook,
  }
})
