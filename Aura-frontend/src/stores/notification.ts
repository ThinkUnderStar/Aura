import { defineStore } from 'pinia'
import { ref } from 'vue'
import { notificationApi } from '@/api'
import { useAuthStore } from './auth'

export const useNotificationStore = defineStore('notification', () => {
  const unread = ref(0)

  async function refreshUnread() {
    const auth = useAuthStore()
    if (!auth.isLoggedIn) {
      unread.value = 0
      return
    }
    try {
      const { data } = await notificationApi.unreadCount()
      unread.value = data.code === 200 ? data.data : 0
    } catch {
      /* 静默失败 */
    }
  }

  return { unread, refreshUnread }
})
