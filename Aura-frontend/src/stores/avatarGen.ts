import { defineStore } from 'pinia'
import { ref } from 'vue'

/**
 * AI 生成头像弹窗状态（仅本次会话内有效）。
 *
 * 弹窗开关、提示词、生成结果、生成中标记都放这里：生成请求是慢操作，
 * 用户中途切到别的模块时 ProfileView 会被卸载，本地 ref 随组件销毁，
 * 结果回来也无处落。上提到 store（应用级单例，不随组件销毁）后：
 * 请求继续在后台跑，结果/进度落到 store，切回来弹窗状态原样保留，
 * 刷新页面才重置。
 */
export const useAvatarGenStore = defineStore('avatarGen', () => {
  const showGenerate = ref(false)
  const prompt = ref('')
  const generated = ref<string | null>(null)
  const generating = ref(false)

  function open() {
    showGenerate.value = true
  }

  function close() {
    showGenerate.value = false
  }

  function reset() {
    showGenerate.value = false
    prompt.value = ''
    generated.value = null
    generating.value = false
  }

  return { showGenerate, prompt, generated, generating, open, close, reset }
})
