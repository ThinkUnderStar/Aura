// 与后端实体/响应对应的前端类型

/** 统一响应包装 */
export interface Result<T = unknown> {
  code: number
  msg: string
  data: T
}

/** 分页结构（MyBatis-Plus） */
export interface Page<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages?: number
}

export interface UserVO {
  id: number
  username: string
  phone: string | null
  email: string | null
  avatar: string | null
  role: number
  status: number
  banStartTime: string | null
  banEndTime: string | null
  banReason: string | null
  banBy: number | null
  token: string | null
}

export interface Agent {
  id: number
  userId: number
  name: string
  status: number
  createTime: string
  updateTime: string
  version: number
}

export interface KnowledgeBase {
  id: number
  ownerId: number
  isTeam: number
  name: string
  description: string | null
  docCount: number
  status: number
  createTime: string
  updateTime: string
  version: number
}

export interface Document {
  id: number
  kbId: number
  fileName: string
  fileSize: number
  fileType: string
  filePath: string
  status: number
  uploadBy: number
  createTime: string
  updateTime: string
  version: number
}

export interface Workspace {
  id: number
  name: string
  description: string | null
  logo: string | null
  inviteCode: string
  ownerId: number
  kbId: number | null
  status: number
  createTime: string
  updateTime: string
  version: number
}

export interface WorkspaceVO extends Workspace {
  role?: number
  memberStatus?: number
}

export interface WorkspaceMemberVO {
  userId: number
  username: string
  avatar: string | null
  role: number
  joinedAt: string
}

export interface MessageVO {
  id: number
  agentId: number
  role: 'user' | 'assistant' | 'tool_confirm'
  content: string
  createTime: string
  action: string | null
  editedContent: string | null
}

export interface Notification {
  id: number
  userId: number
  title: string
  content: string
  type: 'report_result' | 'feedback_reply'
  relatedId: number | null
  isRead: number
  status: number
  createTime: string
  updateTime: string
}

export interface Feedback {
  id: number
  userId: number
  type: 'bug' | 'suggestion' | 'experience' | 'other'
  title: string
  content: string
  contact: string | null
  status: number
  handlerId: number | null
  reply: string | null
  replyTime: string | null
  createTime: string
}

export interface Report {
  id: number
  reporterId: number
  targetType: 'user' | 'workspace' | 'document'
  targetId: number
  reason: string
  description: string
  status: number
  handlerId: number | null
  handleResult: string | null
  handleTime: string | null
  createTime: string
}

export interface OperationLog {
  id: number
  workspaceId: number
  operatorId: number
  module: string
  action: string
  summary: string
  createTime: string
}

export interface BindingKbInfo {
  personalKbIds?: number[]
  teamWorkspaceIds?: number[]
}
