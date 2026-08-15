// 后端字段枚举（与 Java 实体一一对应，前端唯一来源）

/** 用户角色 */
export const USER_ROLE = {
  USER: 1,
  ADMIN: 2,
} as const

/** 团队状态：1-正常 0-已解散 2-被封禁 */
export const WS_STATUS = {
  DISSOLVED: 0,
  NORMAL: 1,
  BANNED: 2,
} as const

/** 团队成员角色：0-创建者 1-管理员 2-普通成员 */
export const MEMBER_ROLE = {
  OWNER: 0,
  ADMIN: 1,
  MEMBER: 2,
} as const

/** 文档索引状态：0-索引中 1-已索引 2-失败 */
export const DOC_STATUS = {
  INDEXING: 0,
  DONE: 1,
  FAILED: 2,
} as const

/** 反馈类型 */
export const FEEDBACK_TYPE: Record<string, string> = {
  bug: '功能异常',
  suggestion: '功能建议',
  experience: '使用体验',
  other: '其他',
}

/** 反馈处理状态 */
export const FEEDBACK_STATUS: Record<number, string> = {
  0: '待处理',
  1: '处理中',
  2: '已完成',
  3: '已关闭',
}

/** 举报目标类型 */
export const REPORT_TARGET: Record<string, string> = {
  user: '用户',
  workspace: '团队',
  document: '文档',
}

/** 举报原因 */
export const REPORT_REASON: Record<string, string> = {
  spam: '垃圾信息',
  harassment: '骚扰',
  inappropriate: '不当内容',
  violation: '违规行为',
  other: '其他',
}

/** 举报处理状态：0-待处理 1-已处理 2-已驳回 */
export const REPORT_STATUS: Record<number, string> = {
  0: '待处理',
  1: '已处理',
  2: '已驳回',
}

/** 通知类型 */
export const NOTIFICATION_TYPE = {
  report_result: '举报结果',
  feedback_reply: '反馈回复',
} as const

/** 状态角标映射：语义 -> {背景, 文字} 的柔色 */
export const BADGE_TONE = {
  green: { bg: 'bg-green-bg', text: 'text-green-text' },
  blue: { bg: 'bg-blue-bg', text: 'text-blue-text' },
  yellow: { bg: 'bg-yellow-bg', text: 'text-yellow-text' },
  red: { bg: 'bg-red-bg', text: 'text-red-text' },
  gray: { bg: 'bg-surface-muted', text: 'text-muted' },
} as const

export type BadgeTone = keyof typeof BADGE_TONE
