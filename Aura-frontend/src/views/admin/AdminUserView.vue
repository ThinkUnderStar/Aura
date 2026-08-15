<script setup lang="ts">
import { ref } from 'vue'
import { adminApi } from '@/api'
import { toast } from '@/stores/toast'
import AppIcon from '@/components/ui/AppIcon.vue'

const banForm = ref({ targetUserId: '', banReason: '', banDays: 30 })
const banning = ref(false)
const unbanId = ref('')
const unbanning = ref(false)

async function ban() {
  const id = Number(banForm.value.targetUserId)
  if (!id) return toast.error('请输入要封禁的用户 ID')
  banning.value = true
  try {
    // type：临时/永久封禁语义需与后端确认，这里以 banDays>0 表示临时封禁
    await adminApi.banUser({
      targetUserId: id,
      type: banForm.value.banDays > 0 ? 1 : 2,
      banReason: banForm.value.banReason.trim() || undefined,
      banTime: banForm.value.banDays > 0 ? banForm.value.banDays : undefined,
    })
    toast.success('已提交封禁')
    banForm.value = { targetUserId: '', banReason: '', banDays: 30 }
  } catch {
    /* 拦截器已提示 */
  } finally {
    banning.value = false
  }
}

async function unban() {
  const id = Number(unbanId.value)
  if (!id) return toast.error('请输入要解封的用户 ID')
  unbanning.value = true
  try {
    await adminApi.unbanUser(id)
    toast.success('已解封')
    unbanId.value = ''
  } catch {
    /* 拦截器已提示 */
  } finally {
    unbanning.value = false
  }
}
</script>

<template>
  <div class="mx-auto max-w-3xl px-4 py-8 md:px-8">
    <h1 class="text-lg font-medium text-ink">用户管理</h1>
    <p class="mt-1 text-sm text-faint">封禁 / 解封用户账号。</p>

    <div class="mt-6 mb-4 flex items-start gap-3 rounded-lg border border-yellow-bg bg-yellow-bg/40 px-4 py-3">
      <AppIcon name="info" :size="16" class="mt-0.5 shrink-0 text-yellow-text" />
      <p class="text-sm leading-6 text-muted">
        后端目前未提供「用户分页列表」接口，暂无法展示完整用户列表。下方提供按用户 ID 的封禁 / 解封操作，待后端补充列表接口后即可接入列表展示。
      </p>
    </div>

    <div class="grid grid-cols-1 gap-4 md:grid-cols-2">
      <div class="card p-5">
        <h3 class="mb-4 text-sm font-medium text-ink">封禁用户</h3>
        <div class="space-y-4">
          <div>
            <label class="label">用户 ID</label>
            <input v-model="banForm.targetUserId" class="input" type="number" placeholder="输入用户 ID" />
          </div>
          <div>
            <label class="label">封禁原因</label>
            <input v-model="banForm.banReason" class="input" placeholder="可选" />
          </div>
          <div>
            <label class="label">封禁天数（0 为永久）</label>
            <input v-model.number="banForm.banDays" class="input" type="number" min="0" />
          </div>
          <button class="btn-danger w-full" :disabled="banning" @click="ban">
            <AppIcon v-if="banning" name="refresh" :size="14" class="animate-spin" />
            封禁
          </button>
        </div>
      </div>

      <div class="card p-5">
        <h3 class="mb-4 text-sm font-medium text-ink">解封用户</h3>
        <div class="space-y-4">
          <div>
            <label class="label">用户 ID</label>
            <input v-model="unbanId" class="input" type="number" placeholder="输入用户 ID" />
          </div>
          <button class="btn-secondary w-full" :disabled="unbanning" @click="unban">
            <AppIcon v-if="unbanning" name="refresh" :size="14" class="animate-spin" />
            解封
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
