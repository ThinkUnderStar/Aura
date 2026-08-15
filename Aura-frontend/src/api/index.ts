import { http } from './http'
import type {
  Agent,
  BindingKbInfo,
  Document,
  Feedback,
  KnowledgeBase,
  MessageVO,
  Notification,
  OperationLog,
  Page,
  Report,
  Result,
  UserVO,
  Workspace,
  WorkspaceMemberVO,
  WorkspaceVO,
} from '@/types'

// ============ 认证 ============
export const authApi = {
  login: (data: { username: string; password?: string; code?: string; loginWay: number; isRemember: boolean }) =>
    http.post<Result<UserVO>>('/auth/login', data),
  sendCode: (username: string, way: 'login' | 'register' | 'reset') =>
    http.post<Result<void>>('/auth/code', null, { params: { username, way } }),
  register: (data: { username: string; password: string; repeatPassword: string; phone: string; code: string }) =>
    http.post<Result<void>>('/auth/register/user', data),
  logout: () => http.delete<Result<void>>('/auth/logout'),
  deleteAccount: () => http.delete<Result<void>>('/auth/delete'),
}

// ============ 用户 ============
export const userApi = {
  update: (data: { username?: string; email?: string }) => http.put<Result<void>>('/user/update', data),
  uploadAvatar: (file: File) => {
    const fd = new FormData()
    fd.append('file', file)
    return http.put<Result<string>>('/user/avatar', fd)
  },
  generateAvatar: (prompt: string) => http.post<Result<string>>('/user/avatar/generate', { prompt }),
  saveGeneratedAvatar: (fileName: string) => http.post<Result<string>>('/user/avatar/generate/save', { fileName }),
}

// ============ 智能体 ============
export const agentApi = {
  create: (name: string) => http.post<Result<Agent>>('/agent/create', null, { params: { name } }),
  list: (page = 1, size = 20) => http.get<Result<Page<Agent>>>('/agent/get', { params: { page, size } }),
  search: (keyWord: string, page = 1, size = 20) =>
    http.get<Result<Page<Agent>>>('/agent/search', { params: { keyWord, page, size } }),
  update: (id: number, name: string) =>
    http.put<Result<Agent>>('/agent/update', null, { params: { id, name } }),
  bindKbs: (agentId: number, kbIds: number[]) =>
    http.put<Result<void>>(`/agent/${agentId}/kbs`, { kbIds }),
  bindingKbs: (agentId: number) => http.get<Result<BindingKbInfo>>(`/agent/${agentId}/binding-kbs`),
  remove: (id: number) => http.delete<Result<void>>('/agent/delete', { params: { id } }),
}

// ============ 对话 ============
export const chatApi = {
  messages: (agentId: number, page = 1, size = 20) =>
    http.get<Result<Page<MessageVO>>>(`/chat/get/${agentId}`, { params: { page, size } }),
  clear: (agentId: number) => http.delete<Result<void>>(`/chat/clear/${agentId}`),
}

// ============ 知识库 ============
export const kbApi = {
  create: (data: { name: string; description?: string; isTeam?: number }) =>
    http.post<Result<KnowledgeBase>>('/kb/create', data),
  list: (page = 1, pageSize = 20) =>
    http.get<Result<Page<KnowledgeBase>>>('/kb/get', { params: { page, pageSize } }),
  team: (workspaceId: number) => http.get<Result<KnowledgeBase>>(`/kb/get/${workspaceId}`),
  updateMy: (data: { id: number; name?: string; description?: string }) =>
    http.put<Result<KnowledgeBase>>('/kb/update/my', data),
  updateTeam: (data: { id: number; type: 'name' | 'description'; value: string }) =>
    http.put<Result<KnowledgeBase>>('/kb/update/team', data),
  logicDelete: (id: number) => http.delete<Result<KnowledgeBase>>('/kb/delete/logic', { params: { id } }),
  forceDelete: (id: number) => http.delete<Result<void>>('/kb/delete/force', { params: { id } }),
  restore: (id: number) => http.put<Result<KnowledgeBase>>('/kb/restore', null, { params: { id } }),
  search: (keyword: string, page = 1, pageSize = 20) =>
    http.get<Result<Page<KnowledgeBase>>>('/kb/search', { params: { keyword, page, pageSize } }),
}

// ============ 文档 ============
export const docApi = {
  upload: (file: File, kbId: number) => {
    const fd = new FormData()
    fd.append('file', file)
    return http.post<Result<Document>>('/document/upload', fd, { params: { kbId } })
  },
  list: (kbId: number, page = 1, size = 20) =>
    http.get<Result<Page<Document>>>('/document/get', { params: { kbId, page, size } }),
  remove: (documentId: number) => http.delete<Result<void>>('/document/delete', { params: { documentId } }),
  contentUrl: (documentId: number, disposition = 'inline') =>
    `/document/content?documentId=${documentId}&disposition=${disposition}`,
}

// ============ 团队 ============
export const wsApi = {
  list: (page = 1, size = 20) => http.get<Result<Page<WorkspaceVO>>>('/workspace/get', { params: { page, size } }),
  create: (data: { name: string; description?: string }) => http.post<Result<WorkspaceVO>>('/workspace/create', data),
  update: (data: { workspaceId: number; name?: string; description?: string }) =>
    http.post<Result<WorkspaceVO>>('/workspace/update', data),
  uploadLogo: (workspaceId: number, file: File) => {
    const fd = new FormData()
    fd.append('file', file)
    return http.put<Result<void>>('/workspace/logo', fd, { params: { workspaceId } })
  },
  remove: (workspaceId: number) => http.delete<Result<OperationLog[]>>('/workspace/delete', { params: { workspaceId } }),
  clean: (workspaceId: number) => http.delete<Result<void>>('/workspace/clean', { params: { workspaceId } }),
  resetInviteCode: (workspaceId: number) =>
    http.put<Result<string>>('/workspace/invite-code/reset', null, { params: { workspaceId } }),
}

// ============ 成员 ============
export const memberApi = {
  list: (workspaceId: number, page = 1, size = 20) =>
    http.get<Result<Page<WorkspaceMemberVO>>>(`/member/${workspaceId}/get`, { params: { page, size } }),
  join: (inviteCode: string) => http.post<Result<WorkspaceVO>>('/member/join', null, { params: { inviteCode } }),
  quit: (workspaceId: number) => http.delete<Result<void>>('/member/quit', { params: { workspaceId } }),
  remove: (workspaceId: number, userId: number) =>
    http.delete<Result<void>>('/member/remove', { params: { workspaceId, userId } }),
  setRole: (data: { workspaceId: number; targetUserId: number; type: 'set_admin' | 'remove_admin' }) =>
    http.put<Result<WorkspaceMemberVO>>('/member/set-role', data),
  transferOwner: (workspaceId: number, targetUserId: number) =>
    http.put<Result<WorkspaceMemberVO>>('/member/owner/transfer', null, { params: { workspaceId, targetUserId } }),
}

// ============ 通知 ============
export const notificationApi = {
  list: (page = 1, size = 20, type?: string, isRead?: number) =>
    http.get<Result<Page<Notification>>>('/notification/get', { params: { page, size, type, isRead } }),
  read: (notificationId: number) => http.put<Result<void>>('/notification/read', null, { params: { notificationId } }),
  readAll: () => http.put<Result<void>>('/notification/read-all'),
  remove: (notificationId: number) => http.delete<Result<void>>('/notification/delete', { params: { notificationId } }),
  unreadCount: () => http.get<Result<number>>('/notification/unread-count'),
}

// ============ 反馈 ============
export const feedbackApi = {
  submit: (data: { type: string; title: string; content: string; contact?: string }) =>
    http.post<Result<void>>('/feedback/submit', data),
  my: (page = 1, size = 20, status?: number) =>
    http.get<Result<Page<Feedback>>>('/feedback/my', { params: { page, size, status } }),
  detail: (feedbackId: number) => http.get<Result<Feedback>>('/feedback/detail', { params: { feedbackId } }),
}

// ============ 举报 ============
export const reportApi = {
  submit: (data: { targetType: string; targetId: number; reason: string; description: string }) =>
    http.post<Result<void>>('/report/submit', data),
  my: (page = 1, size = 20, status?: number) =>
    http.get<Result<Page<Report>>>('/report/my', { params: { page, size, status } }),
}

// ============ 团队操作日志 ============
export const logApi = {
  list: (workspaceId: number, page = 1, size = 20) =>
    http.get<Result<Page<OperationLog>>>(`/operation-log/${workspaceId}/get`, { params: { page, size } }),
}

// ============ 管理后台（role=2） ============
// 注意：后端暂无「用户分页列表」接口，用户管理页暂以占位呈现，待后端补充后接入。
export const adminApi = {
  banUser: (data: { targetUserId: number; type: number; banReason?: string; banTime?: number }) =>
    http.put<Result<void>>('/auth/ban', data),
  unbanUser: (targetUserId: number) => http.put<Result<void>>('/auth/unban', null, { params: { targetUserId } }),
  workspaces: (page = 1, size = 20) =>
    http.get<Result<Page<Workspace>>>('/workspace/get/all', { params: { page, size } }),
  banWorkspace: (workspaceId: number) =>
    http.delete<Result<OperationLog[]>>('/workspace/ban', { params: { workspaceId } }),
  unbanWorkspace: (workspaceId: number, kbId: number) =>
    http.put<Result<void>>('/workspace/unban', null, { params: { workspaceId, kbId } }),
  feedbacks: (page = 1, size = 20, status?: number, type?: string) =>
    http.get<Result<Page<Feedback>>>('/feedback/list', { params: { page, size, status, type } }),
  replyFeedback: (data: { feedbackId: number; reply: string }) => http.put<Result<void>>('/feedback/reply', data),
  updateFeedbackStatus: (feedbackId: number, status: number) =>
    http.put<Result<void>>('/feedback/status', null, { params: { feedbackId, status } }),
  reports: (page = 1, size = 20, status?: number, targetType?: string) =>
    http.get<Result<Page<Report>>>('/report/list', { params: { page, size, status, targetType } }),
  handleReport: (data: { reportId: number; status: number; handleResult: string }) =>
    http.put<Result<void>>('/report/handle', data),
}

// 注意：后端目前没有独立的「用户分页列表」接口（封禁只通过 /auth/ban 指定 id）。
// 管理后台用户列表需后端补充；此处 adminApi.users 为占位，待后端提供后启用。
