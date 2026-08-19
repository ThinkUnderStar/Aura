import { AURA_LOGO } from './auraLogo'

/**
 * 网页图标跟随系统颜色（prefers-color-scheme）实时切换：
 * 系统暗色用白主体 logo，系统亮色用黑主体 logo。
 * 与 App 内主题偏好（stores/theme.ts）无关——用户强制亮色/暗色也不影响网页图标。
 */
const DARK_MQ = '(prefers-color-scheme: dark)'

let link: HTMLLinkElement | null = null

function apply() {
  const dark = window.matchMedia(DARK_MQ).matches
  const href = dark ? AURA_LOGO.dark : AURA_LOGO.light
  if (!link) link = document.head.querySelector<HTMLLinkElement>('link[rel="icon"]')
  if (!link) {
    link = document.createElement('link')
    link.rel = 'icon'
    link.type = 'image/png'
    document.head.appendChild(link)
  }
  if (link.href !== href) link.href = href
}

export function initFavicon() {
  apply()
  // 特性检测后注册监听：MediaQueryList.addEventListener 在部分旧内核上不存在
  const mql = window.matchMedia(DARK_MQ)
  if (typeof mql.addEventListener === 'function') {
    mql.addEventListener('change', apply)
  } else if (typeof mql.addListener === 'function') {
    mql.addListener(apply)
  }
}
