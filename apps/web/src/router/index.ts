import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import AppLayout from '@/layouts/AppLayout.vue'
import LoginView from '@/views/LoginView.vue'
import { useAuthStore } from '@/stores/auth'

const placeholderRoutes: RouteRecordRaw[] = [
  ['sales', 'nav.sales'],
  ['procurement', 'nav.procurement'],
  ['inventory', 'nav.inventory'],
  ['finance', 'nav.finance'],
  ['workflow', 'nav.workflow'],
  ['analytics', 'nav.analytics'],
  ['settings', 'nav.settings'],
].map(([path, titleKey]) => ({
  path,
  name: path,
  component: () => import('@/views/ModulePlaceholderView.vue'),
  meta: { titleKey },
}))

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: LoginView,
      meta: { public: true },
    },
    {
      path: '/',
      component: AppLayout,
      children: [
        { path: '', redirect: '/dashboard' },
        {
          path: 'dashboard',
          name: 'dashboard',
          component: () => import('@/views/DashboardView.vue'),
          meta: { titleKey: 'nav.dashboard' },
        },
        ...placeholderRoutes,
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

  return true
})

export default router
