// 主导航（桌面侧栏）与移动底栏的共享配置

export interface NavItem {
  name: string
  path: string
  icon: string
}

/** 桌面侧栏完整导航 */
export const MAIN_NAV: NavItem[] = [
  { name: '对话', path: '/chat', icon: 'home' },
  { name: '智能体', path: '/agents', icon: 'bot' },
  { name: '知识库', path: '/kb', icon: 'book' },
  { name: '团队', path: '/workspaces', icon: 'users' },
  { name: '通知', path: '/notifications', icon: 'bell' },
  { name: '反馈', path: '/feedback', icon: 'mail' },
]

/** 移动端底部标签（最多 5 项，其余入口收敛到个人中心） */
export const TAB_NAV: NavItem[] = [
  { name: '对话', path: '/chat', icon: 'home' },
  { name: '智能体', path: '/agents', icon: 'bot' },
  { name: '知识库', path: '/kb', icon: 'book' },
  { name: '团队', path: '/workspaces', icon: 'users' },
  { name: '我的', path: '/profile', icon: 'user' },
]
