<script setup lang="ts">
import { ref } from 'vue'
import { adminApi } from '@/api'
import type { UserVO } from '@/types'
import { USER_ROLE } from '@/constants/enums'
import { formatTime } from '@/utils/format'
import { toast } from '@/stores/toast'
import AppIcon from '@/components/ui/AppIcon.vue'
import AppAvatar from '@/components/ui/AppAvatar.vue'
import AppBadge from '@/components/ui/AppBadge.vue'
import AppEmpty from '@/components/ui/AppEmpty.vue'
import AppModal from '@/components/ui/AppModal.vue'
import AppConfirm from '@/components/ui/AppConfirm.vue'

const searchId = ref('')
const searching = ref(false)
const user = ref<UserVO | null>(null)
const notFound = ref(false)

// 封禁（type=1）
const showBan = ref(false)
const banReason = ref('')
const banTime = ref('')
const banning = ref(false)

// 延长封禁（type=2）
const showExtend = ref(false)
const extendReason = ref('')
const extendTime = ref('')
const extending = ref(false)

// 解封
const showUnban = ref(false)
const unbanning = ref(false)

async function search() {
  const id = Number(searchId.value)
  if (!Number.isInteger(id) || id <= 0) return toast.error('请输入有效的用户 ID')
  searching.value = true
  notFound.value = false
  user.value = null
  try {
    const { data } = await adminApi.getUser(id)
    user.value = data.code === 200 ? data.data : null
    notFound.value = data.code !== 200
  } catch {
    user.value = null
    notFound.value = true
  } finally {
    searching.value = false
  }
}

function openBan() {
  banReason.value = ''
  banTime.value = ''
  showBan.value = true
}

async function submitBan() {
  if (!user.value) return
  const reason = banReason.value.trim()
  if (!reason) return toast.error('请填写封禁原因')
  banning.value = true
  try {
    const { data } = await adminApi.banUser({
      targetUserId: user.value.id,
      type: 1,
      banReason: reason,
      // 天数留空表示永久封禁
      banTime: banTime.value ? Number(banTime.value) : null,
    })
    if (data.code === 200) {
      toast.success('已封禁')
      showBan.value = false
      await search()
    }
  } catch {
    /* 拦截器已提示 */
  } finally {
    banning.value = false
  }
}

function openExtend() {
  extendReason.value = ''
  extendTime.value = ''
  showExtend.value = true
}

async function submitExtend() {
  if (!user.value) return
  const days = Number(extendTime.value)
  if (!Number.isInteger(days) || days <= 0) return toast.error('延长天数至少为 1 天')
  extending.value = true
  try {
    const { data } = await adminApi.banUser({
      targetUserId: user.value.id,
      type: 2,
      banReason: extendReason.value.trim(),
      banTime: days,
    })
    if (data.code === 200) {
      toast.success('已延长封禁')
      showExtend.value = false
      await search()
    }
  } catch {
    /* 拦截器已提示 */
  } finally {
    extending.value = false
  }
}

function openUnban() {
  showUnban.value = true
}

async function confirmUnban() {
  if (!user.value) return
  unbanning.value = true
  try {
    const { data } = await adminApi.unbanUser(user.value.id)
    if (data.code === 200) {
      toast.success('已解封')
      showUnban.value = false
      await search()
    }
  } catch {
    /* 拦截器已提示 */
  } finally {
    unbanning.value = false
  }
}
</script>

<template>
  <div class="mx-auto max-w-2xl px-4 py-8 md:px-8">
    <h1 class="text-lg font-medium text-ink">用户管理</h1>
    <p class="mt-1 text-sm text-faint">输入用户 ID 查询账号信息，可执行封禁、延长封禁与解封操作。</p>

    <!-- 搜索 -->
    <div class="card mt-6 flex gap-2 p-4">
      <input
        v-model="searchId"
        class="input flex-1"
        type="number"
        placeholder="输入用户 ID，例如 1001"
        @keydown.enter="search"
      />
      <button class="btn-primary shrink-0" :disabled="searching" @click="search">
        <AppIcon v-if="searching" name="refresh" :size="14" class="animate-spin" />
        查询
      </button>
    </div>

    <!-- 结果 -->
    <div v-if="searching" class="mt-4 flex justify-center py-10 text-faint">
      <AppIcon name="refresh" :size="20" class="animate-spin" />
    </div>

    <AppEmpty
      v-else-if="notFound"
      class="mt-4"
      icon="user"
      title="未找到该用户"
      description="请确认用户 ID 是否正确，或该账号是否已注销。"
    />

    <div v-else-if="user" class="card mt-4 p-5">
      <div class="flex items-center gap-4">
        <AppAvatar :src="user.avatar" :name="user.username" :size="52" />
        <div class="min-w-0 flex-1">
          <div class="flex flex-wrap items-center gap-2">
            <p class="text-base font-medium text-ink">{{ user.username }}</p>
            <AppBadge :tone="user.role === USER_ROLE.ADMIN ? 'yellow' : 'gray'">
              {{ user.role === USER_ROLE.ADMIN ? '管理员' : '普通用户' }}
            </AppBadge>
            <AppBadge :tone="user.status === 0 ? 'red' : 'green'">
              {{ user.status === 0 ? '已封禁' : '正常' }}
            </AppBadge>
          </div>
          <p class="mt-0.5 text-xs text-faint">ID {{ user.id }}</p>
        </div>
      </div>

      <dl class="mt-4 space-y-2 border-t border-line pt-4 text-sm">
        <div class="flex justify-between gap-4">
          <dt class="text-faint">手机号</dt>
          <dd class="text-ink">{{ user.phone || '未填写' }}</dd>
        </div>
        <div class="flex justify-between gap-4">
          <dt class="text-faint">邮箱</dt>
          <dd class="text-ink">{{ user.email || '未绑定' }}</dd>
        </div>
        <template v-if="user.status === 0">
          <div class="flex justify-between gap-4">
            <dt class="text-faint">封禁原因</dt>
            <dd class="text-ink">{{ user.banReason || '—' }}</dd>
          </div>
          <div class="flex justify-between gap-4">
            <dt class="text-faint">封禁开始</dt>
            <dd class="text-ink">{{ formatTime(user.banStartTime) }}</dd>
          </div>
          <div class="flex justify-between gap-4">
            <dt class="text-faint">封禁结束</dt>
            <dd class="text-ink">{{ user.banEndTime ? formatTime(user.banEndTime) : '永久' }}</dd>
          </div>
          <div class="flex justify-between gap-4">
            <dt class="text-faint">执行管理员 ID</dt>
            <dd class="text-ink">{{ user.banBy ?? '—' }}</dd>
          </div>
        </template>
      </dl>

      <div class="mt-5 flex justify-end gap-2">
        <template v-if="user.status === 0">
          <button class="btn-secondary" @click="openExtend">延长封禁</button>
          <button class="btn-secondary" @click="openUnban">解封</button>
        </template>
        <button v-else-if="user.role !== USER_ROLE.ADMIN" class="btn-danger" @click="openBan">
          <AppIcon name="shield" :size="15" />
          封禁
        </button>
        <p v-else class="text-xs text-faint">管理员账号不可封禁</p>
      </div>
    </div>

    <!-- 封禁弹窗 -->
    <AppModal :open="showBan" title="封禁用户" @close="showBan = false">
      <label class="label">封禁原因</label>
      <input v-model="banReason" class="input" placeholder="请填写封禁原因" />
      <label class="label mt-4">封禁天数</label>
      <input v-model="banTime" class="input" type="number" placeholder="留空表示永久封禁" />
      <div class="mt-5 flex justify-end gap-2">
        <button class="btn-secondary" :disabled="banning" @click="showBan = false">取消</button>
        <button class="btn-danger" :disabled="banning" @click="submitBan">
          <AppIcon v-if="banning" name="refresh" :size="14" class="animate-spin" />
          确认封禁
        </button>
      </div>
    </AppModal>

    <!-- 延长封禁弹窗 -->
    <AppModal :open="showExtend" title="延长封禁" @close="showExtend = false">
      <label class="label">延长天数</label>
      <input v-model="extendTime" class="input" type="number" placeholder="至少 1 天" />
      <label class="label mt-4">原因（可选）</label>
      <input v-model="extendReason" class="input" placeholder="覆盖原封禁原因（可留空）" />
      <div class="mt-5 flex justify-end gap-2">
        <button class="btn-secondary" :disabled="extending" @click="showExtend = false">取消</button>
        <button class="btn-primary" :disabled="extending" @click="submitExtend">
          <AppIcon v-if="extending" name="refresh" :size="14" class="animate-spin" />
          确认延长
        </button>
      </div>
    </AppModal>

    <!-- 解封确认 -->
    <AppConfirm
      :open="showUnban"
      title="解封用户"
      :message="`确定解封「${user?.username}」吗？解封后该用户可正常登录。`"
      confirm-text="解封"
      :loading="unbanning"
      @confirm="confirmUnban"
      @cancel="showUnban = false"
    />
  </div>
</template>
