import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const routes: RouteRecordRaw[] = [
  { path: '/login', name: 'login', component: () => import('@/views/auth/LoginView.vue'), meta: { public: true } },
  { path: '/register', name: 'register', component: () => import('@/views/auth/RegisterView.vue'), meta: { public: true } },

  {
    path: '/',
    component: () => import('@/components/layout/AppShell.vue'),
    redirect: '/chat',
    children: [
      { path: 'chat', name: 'chat', component: () => import('@/views/chat/ChatView.vue') },
      { path: 'chat/:agentId', name: 'chat-detail', component: () => import('@/views/chat/ChatView.vue') },
      { path: 'agents', name: 'agents', component: () => import('@/views/agent/AgentListView.vue') },
      { path: 'kb', name: 'kb', component: () => import('@/views/kb/KnowledgeBaseListView.vue') },
      { path: 'kb/:kbId', name: 'kb-detail', component: () => import('@/views/kb/KnowledgeBaseDetailView.vue') },
      { path: 'workspaces', name: 'workspaces', component: () => import('@/views/workspace/WorkspaceListView.vue') },
      {
        path: 'workspaces/:workspaceId',
        name: 'workspace-detail',
        component: () => import('@/views/workspace/WorkspaceDetailView.vue'),
      },
      { path: 'notifications', name: 'notifications', component: () => import('@/views/notification/NotificationView.vue') },
      { path: 'feedback', name: 'feedback', component: () => import('@/views/feedback/FeedbackView.vue') },
      { path: 'profile', name: 'profile', component: () => import('@/views/profile/ProfileView.vue') },
    ],
  },

  {
    path: '/admin',
    component: () => import('@/views/admin/AdminLayout.vue'),
    meta: { admin: true },
    children: [
      { path: '', redirect: '/admin/workspaces' },
      { path: 'workspaces', name: 'admin-workspaces', component: () => import('@/views/admin/AdminWorkspaceView.vue') },
      { path: 'admins', name: 'admin-admins', component: () => import('@/views/admin/AdminAccountView.vue') },
      { path: 'feedbacks', name: 'admin-feedbacks', component: () => import('@/views/admin/AdminFeedbackView.vue') },
      { path: 'reports', name: 'admin-reports', component: () => import('@/views/admin/AdminReportView.vue') },
    ],
  },

  { path: '/:pathMatch(.*)*', redirect: '/chat' },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to) => {
  const auth = useAuthStore()

  if (to.meta.public) {
    if (auth.isLoggedIn && (to.name === 'login' || to.name === 'register')) return { path: '/chat' }
    return true
  }

  if (!auth.isLoggedIn) return { path: '/login', query: { redirect: to.fullPath } }
  if (to.meta.admin && !auth.isAdmin) return { path: '/chat' }
  return true
})

export default router
