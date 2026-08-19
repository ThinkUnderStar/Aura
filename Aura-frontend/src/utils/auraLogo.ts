import auraBlack from '@logo/aura-black.png'
import auraWhite from '@logo/aura-white.png'

/**
 * Aura 品牌 logo 两张图（文件名即标注，主体色不同）：
 * - aura-white：白色主体，用于暗色主题 / 系统暗色
 * - aura-black：黑色主体，用于亮色主题 / 系统亮色
 *
 * 统一从这里取 URL，避免各处分别 import 拼路径。
 */
export const AURA_LOGO = {
  dark: auraWhite,
  light: auraBlack,
} as const

/** 按“是否为暗色”解析对应 logo URL：暗色用白主体，亮色用黑主体。 */
export function resolveAuraLogo(dark: boolean): string {
  return dark ? AURA_LOGO.dark : AURA_LOGO.light
}
