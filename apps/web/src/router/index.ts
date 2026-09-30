import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { public: true },
    },
    {
      path: '/',
      component: () => import('@/layouts/AppLayout.vue'),
      children: [
        { path: '', redirect: '/dashboard' },
        {
          path: 'dashboard',
          name: 'dashboard',
          component: () => import('@/views/RoleWorkspaceView.vue'),
          meta: { titleKey: 'nav.dashboard' },
        },
        {
          path: 'workflow',
          name: 'workflow',
          component: () => import('@/views/WorkflowView.vue'),
          meta: { titleKey: 'nav.workflow', permission: 'workflow:view' },
        },
        {
          path: 'sales',
          name: 'sales',
          component: () => import('@/views/SalesView.vue'),
          meta: { titleKey: 'nav.sales', permission: 'sales:view' },
        },
        {
          path: 'procurement',
          name: 'procurement',
          component: () => import('@/views/ProcurementView.vue'),
          meta: { titleKey: 'nav.procurement', permission: 'procurement:view' },
        },
        {
          path: 'inventory',
          name: 'inventory',
          component: () => import('@/views/InventoryView.vue'),
          meta: { titleKey: 'nav.inventory', permission: 'inventory:view' },
        },
        {
          path: 'finance',
          name: 'finance',
          component: () => import('@/views/FinanceView.vue'),
          meta: { titleKey: 'nav.finance', permission: 'finance:view' },
        },
        {
          path: 'projects',
          name: 'projects',
          component: () => import('@/views/ProjectsView.vue'),
          meta: { titleKey: 'nav.projects', permission: 'project:view' },
        },
        {
          path: 'analytics',
          name: 'analytics',
          component: () => import('@/views/AnalyticsView.vue'),
          meta: { titleKey: 'nav.analytics', permission: 'analytics:view' },
        },
        {
          path: 'platform',
          name: 'platform-settings',
          component: () => import('@/views/PlatformSettingsView.vue'),
          meta: { titleKey: 'nav.platform', permission: 'organization:view' },
        },
        {
          path: 'account/security',
          name: 'account-security',
          component: () => import('@/views/AccountSecurityView.vue'),
          meta: { titleKey: 'nav.accountSecurity' },
        },
        {
          path: 'settings',
          name: 'settings',
          component: () => import('@/views/MasterDataView.vue'),
          meta: { titleKey: 'nav.settings' },
        },
      ],
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: () => import('@/views/NotFoundView.vue'),
    },
  ],
  scrollBehavior: () => ({ top: 0 }),
})

router.beforeEach(async (to) => {
  if (to.meta.public) return true

  const authStore = useAuthStore()
  const authenticated = await authStore.ensureSession()
  if (!authenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }

  if (authStore.user?.mustChangePassword && to.path !== '/account/security') {
    return { path: '/account/security' }
  }

  const permission = to.meta.permission as string | undefined
  if (!authStore.hasPermission(permission)) {
    return { name: 'dashboard', query: { denied: permission } }
  }


  return true
})

export default router
