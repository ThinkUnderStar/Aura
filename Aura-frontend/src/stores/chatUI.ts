import { defineStore } from 'pinia'
import { ref } from 'vue'

/**
 * 每个智能体独立的聊天界面状态（仅本次会话内有效）。
 *
 * 联网开关、输入框草稿都按 agentId 隔离：切换智能体各自保留，
 * 切到别的模块再回来也不丢（组件卸载不清空），刷新页面才重置。
 */
export const useChatUIStore = defineStore('chatUI', () => {
  const webSearchByAgent = ref<Record<number, boolean>>({})
  const drafts = ref<Record<number, string>>({})

  function toggleWebSearch(agentId: number) {
    webSearchByAgent.value[agentId] = !webSearchByAgent.value[agentId]
  }

  function setDraft(agentId: number, text: string) {
    drafts.value[agentId] = text
  }

  return { webSearchByAgent, drafts, toggleWebSearch, setDraft }
})
