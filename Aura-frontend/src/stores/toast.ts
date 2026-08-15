import { reactive } from 'vue'

export interface ToastItem {
  id: number
  type: 'success' | 'error' | 'info'
  message: string
}

const state = reactive<{ items: ToastItem[] }>({ items: [] })
let seed = 0

function push(type: ToastItem['type'], message: string) {
  const id = ++seed
  state.items.push({ id, type, message })
  setTimeout(() => dismiss(id), 3000)
}

function dismiss(id: number) {
  const i = state.items.findIndex((t) => t.id === id)
  if (i >= 0) state.items.splice(i, 1)
}

export function useToast() {
  return { items: state.items, dismiss }
}

export const toast = {
  success: (m: string) => push('success', m),
  error: (m: string) => push('error', m),
  info: (m: string) => push('info', m),
}
