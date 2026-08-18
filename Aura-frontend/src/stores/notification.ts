import { defineStore } from 'pinia'
import { ref } from 'vue'
import { notificationApi } from '@/api'
import { useAuthStore } from './auth'

export const useNotificationStore = defineStore('notification', () => {
  const unread = ref(0)
  const read = ref(0)

  async function refreshUnread() {
    const auth = useAuthStore()
    if (!auth.isLoggedIn) {
      unread.value = 0
      read.value = 0
      return
    }
    try {
      const [u, r] = await Promise.all([notificationApi.unreadCount(), notificationApi.readCount()])
      unread.value = u.data.code === 200 ? u.data.data : 0
      read.value = r.data.code === 200 ? r.data.data : 0
    } catch {
      /* 静默失败 */
    }
  }

  return { unread, read, refreshUnread }
})
