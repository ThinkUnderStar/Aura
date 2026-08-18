import { defineStore } from 'pinia'
import { ref } from 'vue'

export type ThemeMode = 'system' | 'light' | 'dark'

const STORAGE_KEY = 'aura_theme'
const DARK_MQ = '(prefers-color-scheme: dark)'

// 与 index.html 里的防闪白脚本共用同一套判定：无效值一律回退到 system。
// localStorage 在部分隐私模式/沙箱下会抛 SecurityError，统一 try/catch 兜底（与 auth store 一致）。
function readStored(): ThemeMode {
  try {
    const v = localStorage.getItem(STORAGE_KEY)
    return v === 'light' || v === 'dark' || v === 'system' ? v : 'system'
  } catch {
    return 'system'
  }
}

function writeStored(m: ThemeMode) {
  try {
    localStorage.setItem(STORAGE_KEY, m)
  } catch {
    /* 存储不可用就只切当前页面，不持久化 */
  }
}

export const useThemeStore = defineStore('theme', () => {
  const mode = ref<ThemeMode>(readStored())

  function apply() {
    const prefersDark = window.matchMedia(DARK_MQ).matches
    const dark = mode.value === 'dark' || (mode.value === 'system' && prefersDark)
    document.documentElement.classList.toggle('dark', dark)
    // 同步浏览器 UI（地址栏等）的主题色，与 index.html 的 meta 一致
    document
      .querySelector('meta[name="theme-color"]')
      ?.setAttribute('content', dark ? '#1A1917' : '#F7F6F3')
  }

  function setMode(m: ThemeMode) {
    mode.value = m
    writeStored(m)
    apply()
  }

  // 跟随系统模式下，系统主题变化实时生效。
  // 注意：MediaQueryList.addEventListener 在 Chrome<83 / Safari<14 及部分套壳内核上不存在，
  // 直接调用会抛 TypeError 导致整页白屏；这里做特性检测并回退到旧版 addListener。
  const mql = window.matchMedia(DARK_MQ)
  const onSystemChange = () => {
    if (mode.value === 'system') apply()
  }
  if (typeof mql.addEventListener === 'function') {
    mql.addEventListener('change', onSystemChange)
  } else if (typeof mql.addListener === 'function') {
    mql.addListener(onSystemChange)
  }

  apply()

  return { mode, setMode }
})
