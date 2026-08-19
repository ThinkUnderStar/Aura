<script setup lang="ts">
defineOptions({ name: 'ChatView' })
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { useRoute, useRouter } from 'vue-router'
import { agentApi, chatApi } from '@/api'
import type { InterruptPayload, StreamHandlers } from '@/api/sse'
import { useChatStreamStore } from '@/stores/chatStream'
import { useChatUIStore } from '@/stores/chatUI'
import { useThemeStore } from '@/stores/theme'
import { toast } from '@/stores/toast'
import { resolveAuraLogo } from '@/utils/auraLogo'
import type { Agent, MessageVO } from '@/types'
import MessageBubble from '@/components/chat/MessageBubble.vue'
import ChatInput from '@/components/chat/ChatInput.vue'
import ToolConfirmPanel from '@/components/chat/ToolConfirmPanel.vue'
import AppIcon from '@/components/ui/AppIcon.vue'
import AppModal from '@/components/ui/AppModal.vue'
import AppEmpty from '@/components/ui/AppEmpty.vue'
import AppSpinner from '@/components/ui/AppSpinner.vue'
import Markdown from '@/components/ui/Markdown.vue'
import AppConfirm from '@/components/ui/AppConfirm.vue'

const route = useRoute()
const router = useRouter()
const theme = useThemeStore()
// Aura 智能体头像：按当前主题切换（暗色→白主体，亮色→黑主体）
const auraAvatar = computed(() => resolveAuraLogo(theme.isDark))

const agents = ref<Agent[]>([])
const messages = ref<MessageVO[]>([])
const currentAgentId = ref<number | null>(null)

// 流式状态上提到 chatStream store：切模块（含 /admin）不再卸载即断流
const chatStream = useChatStreamStore()
const { streamingAgents, streamingByAgent, interruptByAgent } = storeToRefs(chatStream)

const loadingAgents = ref(false)
const loadingMessages = ref(false)

// 联网开关 / 输入草稿按智能体独立（会话内有效），不共用一个状态
const chatUI = useChatUIStore()
const { webSearchByAgent, drafts } = storeToRefs(chatUI)
const currentWebSearch = computed(() => currentAgentId.value != null && !!webSearchByAgent.value[currentAgentId.value])
const draft = computed({
  get: () => (currentAgentId.value != null ? drafts.value[currentAgentId.value] ?? '' : ''),
  set: (text: string) => {
    if (currentAgentId.value != null) drafts.value[currentAgentId.value] = text
  },
})

function toggleWebSearch() {
  if (currentAgentId.value == null) return
  chatUI.toggleWebSearch(currentAgentId.value)
}

const scrollRef = ref<HTMLElement | null>(null)
const showCreate = ref(false)
const newAgentName = ref('')
const creating = ref(false)
const showClear = ref(false)
const clearing = ref(false)
const showBacktrackConfirm = ref(false)
const backtrackConfirmMessage = ref('')
const pendingBacktrack = ref<{ messageId: number; content: string } | null>(null)

const currentAgent = computed(() => agents.value.find((a) => a.id === currentAgentId.value))
const currentStreaming = computed(() => currentAgentId.value != null && !!streamingAgents.value[currentAgentId.value])
const currentStreamingText = computed(() => (currentAgentId.value != null ? streamingByAgent.value[currentAgentId.value] ?? '' : ''))
const currentInterrupt = computed(() => (currentAgentId.value != null ? interruptByAgent.value[currentAgentId.value] ?? null : null))
const hasMessages = computed(() => messages.value.length > 0 || currentStreaming.value || !!currentStreamingText.value || !!currentInterrupt.value)

async function scrollToBottom() {
  await nextTick()
  const el = scrollRef.value
  if (el) el.scrollTop = el.scrollHeight
}

// ---------- 智能体 ----------
async function loadAgents() {
  loadingAgents.value = true
  try {
    const { data } = await agentApi.list(1, 100)
    agents.value = data.code === 200 ? data.data.records : []
  } catch {
    agents.value = []
  } finally {
    loadingAgents.value = false
  }
}

async function createAgent() {
  const name = newAgentName.value.trim()
  if (!name) return toast.error('请输入智能体名称')
  creating.value = true
  try {
    const { data } = await agentApi.create(name)
    const agent = data.data
    showCreate.value = false
    newAgentName.value = ''
    await loadAgents()
    if (agent?.id) router.push(`/chat/${agent.id}`)
  } catch {
    /* 拦截器已提示 */
  } finally {
    creating.value = false
  }
}

// ---------- 消息 ----------
async function loadMessages(showSpinner = true) {
  if (!currentAgentId.value) return
  if (showSpinner) loadingMessages.value = true
  try {
    const { data } = await chatApi.messages(currentAgentId.value, 1, 200)
    // 后端按时间倒序返回（最新在前），反转成旧→新，让最新消息在底部
    const records = data.code === 200 ? data.data.records : []
    messages.value = [...records].reverse()
  } catch {
    messages.value = []
  } finally {
    loadingMessages.value = false
    // 等消息列表渲染完成后再滚到底部，否则 scrollHeight 还是 0，滚动无效
    await nextTick()
    scrollToBottom()
  }
}

function selectAgent(agent: Agent) {
  if (agent.id === currentAgentId.value) return
  router.push(`/chat/${agent.id}`)
}

// 组件存活的期间，流收尾时把完整文本落库到当前视图的消息列表。
// 组件卸载后（切到别的模块）store 里没有钩子，等切回来由 loadMessages 从后端拉取。
chatStream.setFinalizeHook((agentId) => {
  const text = streamingByAgent.value[agentId] ?? ''
  // 仅当收尾时正好在查看该智能体才落库
  if (text.trim() && agentId === currentAgentId.value) {
    // 快速切换时 loadMessages 可能已把后端写入的这条拉进列表，避免本地再 push 造成重复
    const alreadyStored = messages.value.some(
      (m) => m.agentId === agentId && m.role === 'assistant' && m.content === text,
    )
    if (!alreadyStored) {
      messages.value.push({
        id: -Date.now(),
        agentId,
        role: 'assistant',
        content: text,
        createTime: new Date().toISOString(),
        action: null,
        editedContent: null,
      })
    }
  }
  scrollToBottom()
})

function makeHandlers(agentId: number): StreamHandlers {
  return {
    onText: (t) => {
      chatStream.appendText(agentId, t)
      scrollToBottom()
    },
    onInterrupt: (p) => {
      chatStream.setInterrupt(agentId, p)
      scrollToBottom()
    },
    onDone: () => {
      if (!interruptByAgent.value[agentId]) chatStream.finalize(agentId)
    },
    onError: (m) => {
      toast.error(m)
      chatStream.finalize(agentId)
    },
  }
}

async function runStream(path: string, body: Record<string, unknown>, method: 'POST' | 'PUT' = 'POST') {
  const agentId = currentAgentId.value
  if (agentId == null) return
  await chatStream.runStream(agentId, path, body, makeHandlers(agentId), method)
}

async function send(text: string) {
  const agentId = currentAgentId.value
  if (agentId == null || currentStreaming.value) return
  delete interruptByAgent.value[agentId]
  messages.value.push({
    id: -Date.now(),
    agentId,
    role: 'user',
    content: text,
    createTime: new Date().toISOString(),
    action: null,
    editedContent: null,
  })
  await scrollToBottom()
  await runStream(`/chat/send/${agentId}`, {
    humanContent: text,
    enableWebSearch: currentWebSearch.value ? 1 : 0,
  })
}

// ---------- 中断续接 ----------
async function onChoose(option: string, edition?: string) {
  const agentId = currentAgentId.value
  if (agentId == null) return
  delete interruptByAgent.value[agentId]
  await runStream(`/chat/tool_allow/${agentId}`, {
    choice: option,
    edition: option === 'edit' ? edition : '',
    enableWebSearch: currentWebSearch.value ? 1 : 0,
  })
}

function onCancelInterrupt() {
  const agentId = currentAgentId.value
  if (agentId == null) return
  delete interruptByAgent.value[agentId]
  chatStream.finalize(agentId)
}

// ---------- 清空对话 ----------
async function clearConversation() {
  if (!currentAgentId.value) return
  clearing.value = true
  try {
    await chatApi.clear(currentAgentId.value)
    messages.value = []
    showClear.value = false
    toast.success('对话已清空')
  } catch {
    /* 拦截器已提示 */
  } finally {
    clearing.value = false
  }
}

// ---------- 回溯（编辑消息重新生成） ----------
function onEditSubmit(message: MessageVO, newContent: string) {
  if (currentStreaming.value) return
  const idx = messages.value.findIndex((m) => m.id === message.id)
  const subsequent = idx >= 0 ? messages.value.length - 1 - idx : 0
  if (subsequent > 0) {
    pendingBacktrack.value = { messageId: message.id, content: newContent }
    backtrackConfirmMessage.value = `将删除这条消息之后的 ${subsequent} 条对话，并从这条消息重新生成，且不可恢复。确定继续吗？`
    showBacktrackConfirm.value = true
  } else {
    doBacktrack(message.id, newContent)
  }
}

function confirmBacktrack() {
  const p = pendingBacktrack.value
  showBacktrackConfirm.value = false
  pendingBacktrack.value = null
  if (p) doBacktrack(p.messageId, p.content)
}

async function doBacktrack(messageId: number, content: string) {
  const agentId = currentAgentId.value
  if (agentId == null) return
  const idx = messages.value.findIndex((m) => m.id === messageId)
  if (idx >= 0) {
    messages.value[idx].content = content
    messages.value.splice(idx + 1)
  }
  chatStream.resetAgent(agentId)
  await scrollToBottom()
  await runStream(
    `/chat/update/${messageId}`,
    {
      humanContent: content,
      enableWebSearch: currentWebSearch.value ? 1 : 0,
    },
    'PUT',
  )
}

// ---------- 路由联动 ----------
watch(
  () => route.params.agentId,
  (id) => {
    if (id) {
      currentAgentId.value = Number(id)
      // 流式状态按智能体隔离，切换不清空：若该智能体正在生成，切回来会继续显示并续写
      loadMessages()
    }
  },
)

onBeforeUnmount(() => {
  // 组件卸载不中断后台流（切模块/跨 layout 时流继续跑）；只摘掉本视图的收尾钩子。
  // 登出时由 chatStream store 统一 abort。
  chatStream.setFinalizeHook(null)
})

// ---------- 初始化 ----------
async function init() {
  await loadAgents()
  const routeId = route.params.agentId
  if (routeId) {
    currentAgentId.value = Number(routeId)
    await loadMessages()
  } else if (agents.value.length) {
    router.replace(`/chat/${agents.value[0].id}`)
  }
}
init()
</script>

<template>
  <div class="flex h-full flex-col">
    <!-- 顶部：智能体选择 + 操作 -->
    <div class="flex shrink-0 items-center gap-2 overflow-x-auto border-b border-line bg-surface px-4 py-2.5">
      <button
        v-for="agent in agents"
        :key="agent.id"
        class="flex shrink-0 items-center gap-1.5 rounded-sm px-3 py-1.5 text-sm transition-colors"
        :class="agent.id === currentAgentId ? 'bg-ink-solid font-medium text-white' : 'text-muted hover:bg-surface-muted hover:text-ink'"
        @click="selectAgent(agent)"
      >
        <AppIcon
          v-if="streamingAgents[agent.id]"
          name="refresh"
          :size="13"
          class="animate-spin"
          :class="agent.id === currentAgentId ? 'text-white' : 'text-muted'"
        />
        {{ agent.name }}
      </button>

      <button
        class="flex shrink-0 items-center gap-1 rounded-sm px-2 py-1.5 text-sm text-muted transition-colors hover:bg-surface-muted hover:text-ink"
        @click="showCreate = true"
      >
        <AppIcon name="plus" :size="15" />
        新建
      </button>

      <div class="ml-auto flex shrink-0 items-center gap-1">
        <button
          class="flex items-center gap-1.5 rounded-sm px-2.5 py-1.5 text-xs transition-colors"
          :class="currentWebSearch ? 'bg-blue-bg text-blue-text' : 'text-muted hover:bg-surface-muted hover:text-ink'"
          title="联网搜索（按智能体独立，本次会话内有效）"
          @click="toggleWebSearch"
        >
          <AppIcon name="globe" :size="15" />
          联网
        </button>
        <button
          class="flex items-center gap-1.5 rounded-sm px-2.5 py-1.5 text-xs text-muted transition-colors hover:bg-surface-muted hover:text-ink"
          :disabled="!messages.length"
          @click="showClear = true"
        >
          <AppIcon name="trash" :size="15" />
          清空
        </button>
      </div>
    </div>

    <!-- 消息区 -->
    <div ref="scrollRef" class="flex-1 overflow-y-auto px-4 py-6">
      <div v-if="loadingMessages" class="flex justify-center py-16">
        <AppSpinner label="加载对话…" />
      </div>

      <div v-else-if="loadingAgents" class="flex justify-center py-16">
        <AppSpinner label="加载智能体…" />
      </div>

      <!-- 无智能体：引导创建 -->
      <div v-else-if="!agents.length" class="mx-auto max-w-md py-16">
        <AppEmpty icon="bot" title="还没有智能体" description="创建一个智能体，开始你的第一段对话。">
          <button class="btn-primary" @click="showCreate = true">
            <AppIcon name="plus" :size="15" />
            创建智能体
          </button>
        </AppEmpty>
      </div>

      <!-- 空对话 -->
      <div v-else-if="!hasMessages" class="mx-auto max-w-2xl py-20 text-center">
        <div class="mx-auto mb-4 flex h-12 w-12 items-center justify-center overflow-hidden rounded-md bg-surface-muted p-2">
          <img :src="auraAvatar" alt="Aura" class="h-full w-full object-contain" />
        </div>
        <h2 class="font-serif text-lg text-ink">与 {{ currentAgent?.name }} 对话</h2>
        <p class="mx-auto mt-2 max-w-sm text-sm leading-6 text-faint">
          直接输入问题即可开始。需要实时信息时，可打开右上角的「联网」开关。
        </p>
      </div>

      <!-- 消息列表 -->
      <div v-else class="mx-auto max-w-3xl space-y-6">
        <MessageBubble
          v-for="m in messages"
          :key="m.id"
          :message="m"
          :disabled="currentStreaming"
          @edit-submit="onEditSubmit"
        />

        <!-- 流式输出气泡（仅当前智能体生成时显示；切回来可继续看到并续写） -->
        <div v-if="currentStreaming || currentStreamingText" class="flex gap-3">
          <div class="h-10 w-10 shrink-0 overflow-hidden rounded-full bg-surface p-1.5">
            <img :src="auraAvatar" alt="Aura" class="h-full w-full object-contain" />
          </div>
          <div class="min-w-0 max-w-[80%]">
            <p class="text-xs text-faint">Aura</p>
            <div class="mt-1 rounded-lg rounded-tl-sm border border-line bg-surface px-4 py-3">
              <Markdown v-if="currentStreamingText" :content="currentStreamingText" />
              <span v-else class="flex items-center gap-1 text-faint">
                <AppIcon name="refresh" :size="13" class="animate-spin" />
                思考中…
              </span>
              <span v-if="currentStreaming" class="inline-block h-4 w-1.5 animate-pulse bg-ink-solid align-middle" />
            </div>
          </div>
        </div>

        <!-- 工具调用确认（内联，按智能体独立） -->
        <ToolConfirmPanel
          v-if="currentInterrupt"
          :payload="currentInterrupt"
          @choose="onChoose"
          @cancel="onCancelInterrupt"
        />
      </div>
    </div>

    <!-- 输入区 -->
    <div class="shrink-0 border-t border-line bg-surface px-4 py-3">
      <div class="mx-auto max-w-3xl">
        <ChatInput v-model="draft" :disabled="!currentAgentId || currentStreaming" @send="send" />
        <div class="mt-2 flex justify-end">
          <span class="text-xs text-faint">Aura 可能出错，请核对重要信息</span>
        </div>
      </div>
    </div>

    <!-- 新建智能体 -->
    <AppModal :open="showCreate" title="新建智能体" @close="showCreate = false">
      <label class="label">名称</label>
      <input v-model="newAgentName" class="input" placeholder="例如：写作助手" @keydown.enter="createAgent" />
      <div class="mt-5 flex justify-end gap-2">
        <button class="btn-secondary" @click="showCreate = false">取消</button>
        <button class="btn-primary" :disabled="creating" @click="createAgent">
          <AppIcon v-if="creating" name="refresh" :size="14" class="animate-spin" />
          创建
        </button>
      </div>
    </AppModal>

    <!-- 清空确认 -->
    <AppConfirm
      :open="showClear"
      title="清空对话"
      message="将删除与该智能体的全部历史消息，且不可恢复。确定继续吗？"
      confirm-text="清空"
      :danger="true"
      :loading="clearing"
      @confirm="clearConversation"
      @cancel="showClear = false"
    />

    <!-- 回溯确认 -->
    <AppConfirm
      :open="showBacktrackConfirm"
      title="重新生成后续对话"
      :message="backtrackConfirmMessage"
      confirm-text="继续"
      :danger="true"
      @confirm="confirmBacktrack"
      @cancel="showBacktrackConfirm = false"
    />
  </div>
</template>
