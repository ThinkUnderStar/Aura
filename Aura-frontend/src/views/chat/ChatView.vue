<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { agentApi, chatApi } from '@/api'
import { streamChat, type InterruptPayload, type StreamHandlers } from '@/api/sse'
import { toast } from '@/stores/toast'
import type { Agent, MessageVO } from '@/types'
import MessageBubble from '@/components/chat/MessageBubble.vue'
import ChatInput from '@/components/chat/ChatInput.vue'
import ToolConfirmDialog from '@/components/chat/ToolConfirmDialog.vue'
import AppIcon from '@/components/ui/AppIcon.vue'
import AppModal from '@/components/ui/AppModal.vue'
import AppEmpty from '@/components/ui/AppEmpty.vue'
import AppSpinner from '@/components/ui/AppSpinner.vue'
import Markdown from '@/components/ui/Markdown.vue'
import AppConfirm from '@/components/ui/AppConfirm.vue'

const route = useRoute()
const router = useRouter()

const agents = ref<Agent[]>([])
const messages = ref<MessageVO[]>([])
const currentAgentId = ref<number | null>(null)

const loadingAgents = ref(false)
const loadingMessages = ref(false)
const streaming = ref(false)
const streamingText = ref('')
const pendingInterrupt = ref<InterruptPayload | null>(null)
const awaitingInterrupt = ref(false)
const webSearch = ref(false)

const scrollRef = ref<HTMLElement | null>(null)
const showCreate = ref(false)
const newAgentName = ref('')
const creating = ref(false)
const showClear = ref(false)
const clearing = ref(false)

let abortController: AbortController | null = null

const currentAgent = computed(() => agents.value.find((a) => a.id === currentAgentId.value))
const hasMessages = computed(() => messages.value.length > 0 || streaming.value || !!streamingText.value)

async function scrollToBottom() {
  await nextTick()
  scrollRef.value?.scrollTo({ top: scrollRef.value.scrollHeight, behavior: 'smooth' })
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
    // 后端按时间正序返回，这里保持展示顺序
    const records = data.code === 200 ? data.data.records : []
    messages.value = [...records].reverse()
    await scrollToBottom()
  } catch {
    messages.value = []
  } finally {
    loadingMessages.value = false
  }
}

function selectAgent(agent: Agent) {
  if (agent.id === currentAgentId.value) return
  router.push(`/chat/${agent.id}`)
}

function finalize() {
  if (streamingText.value.trim()) {
    messages.value.push({
      id: -Date.now(),
      agentId: currentAgentId.value ?? 0,
      role: 'assistant',
      content: streamingText.value,
      createTime: new Date().toISOString(),
      action: null,
      editedContent: null,
    })
  }
  streamingText.value = ''
  streaming.value = false
  awaitingInterrupt.value = false
}

function stopStreaming() {
  abortController?.abort()
  streaming.value = false
  awaitingInterrupt.value = false
  pendingInterrupt.value = null
  finalize()
}

function makeHandlers(): StreamHandlers {
  return {
    onText: (t) => {
      streamingText.value += t
      scrollToBottom()
    },
    onInterrupt: (p) => {
      awaitingInterrupt.value = true
      pendingInterrupt.value = p
      streaming.value = false
    },
    onDone: () => {
      if (!awaitingInterrupt.value) finalize()
    },
    onError: (m) => {
      toast.error(m)
      streaming.value = false
      if (streamingText.value) finalize()
      else awaitingInterrupt.value = false
    },
  }
}

async function runStream(path: string, body: Record<string, unknown>) {
  abortController = new AbortController()
  streaming.value = true
  await streamChat(path, body, makeHandlers(), abortController.signal)
}

async function send(text: string) {
  if (!currentAgentId.value || streaming.value) return
  messages.value.push({
    id: -Date.now(),
    agentId: currentAgentId.value,
    role: 'user',
    content: text,
    createTime: new Date().toISOString(),
    action: null,
    editedContent: null,
  })
  streamingText.value = ''
  awaitingInterrupt.value = false
  await scrollToBottom()
  await runStream(`/chat/send/${currentAgentId.value}`, {
    humanContent: text,
    enableWebSearch: webSearch.value ? 1 : 0,
  })
}

// ---------- 中断续接 ----------
async function onChoose(option: string) {
  const agentId = currentAgentId.value
  pendingInterrupt.value = null
  if (!agentId) return
  awaitingInterrupt.value = false
  await runStream(`/chat/tool_allow/${agentId}`, {
    choice: option,
    edition: option === 'edit' ? '' : undefined,
    enableWebSearch: webSearch.value ? 1 : 0,
  })
}

function onCancelInterrupt() {
  pendingInterrupt.value = null
  awaitingInterrupt.value = false
  if (streamingText.value) finalize()
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

// ---------- 路由联动 ----------
watch(
  () => route.params.agentId,
  (id) => {
    if (id) {
      currentAgentId.value = Number(id)
      streamingText.value = ''
      streaming.value = false
      pendingInterrupt.value = null
      awaitingInterrupt.value = false
      loadMessages()
    }
  },
)

onBeforeUnmount(() => abortController?.abort())

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
        class="shrink-0 rounded-sm px-3 py-1.5 text-sm transition-colors"
        :class="agent.id === currentAgentId ? 'bg-ink font-medium text-white' : 'text-muted hover:bg-surface-muted hover:text-ink'"
        @click="selectAgent(agent)"
      >
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
          :class="webSearch ? 'bg-blue-bg text-blue-text' : 'text-muted hover:bg-surface-muted hover:text-ink'"
          title="联网搜索"
          @click="webSearch = !webSearch"
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
        <div class="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-md bg-ink text-white">
          <span class="font-serif text-lg leading-none">A</span>
        </div>
        <h2 class="font-serif text-lg text-ink">与 {{ currentAgent?.name }} 对话</h2>
        <p class="mx-auto mt-2 max-w-sm text-sm leading-6 text-faint">
          直接输入问题即可开始。需要实时信息时，可打开右上角的「联网」开关。
        </p>
      </div>

      <!-- 消息列表 -->
      <div v-else class="mx-auto max-w-3xl space-y-6">
        <MessageBubble v-for="m in messages" :key="m.id" :message="m" />

        <!-- 流式输出气泡 -->
        <div v-if="streaming || streamingText" class="flex gap-3">
          <div class="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-ink text-white">
            <span class="font-serif text-xs leading-none">A</span>
          </div>
          <div class="min-w-0 max-w-[80%]">
            <p class="text-xs text-faint">Aura</p>
            <div class="mt-1 rounded-lg rounded-tl-sm border border-line bg-surface px-4 py-3">
              <Markdown v-if="streamingText" :content="streamingText" />
              <span v-else class="flex items-center gap-1 text-faint">
                <AppIcon name="refresh" :size="13" class="animate-spin" />
                思考中…
              </span>
              <span v-if="streaming" class="inline-block h-4 w-1.5 animate-pulse bg-ink align-middle" />
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 输入区 -->
    <div class="shrink-0 border-t border-line bg-surface px-4 py-3">
      <div class="mx-auto max-w-3xl">
        <ChatInput :disabled="!currentAgentId || streaming" @send="send" />
        <div class="mt-2 flex items-center justify-between">
          <button v-if="streaming" class="btn-danger-soft !px-3 !py-1 text-xs" @click="stopStreaming">
            停止生成
          </button>
          <span v-else />
          <span class="text-xs text-faint">Aura 可能出错，请核对重要信息</span>
        </div>
      </div>
    </div>

    <!-- 工具调用确认 -->
    <ToolConfirmDialog
      :open="!!pendingInterrupt"
      :payload="pendingInterrupt"
      @choose="onChoose"
      @cancel="onCancelInterrupt"
    />

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
  </div>
</template>
