import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import AppLayout from '@/layouts/AppLayout.vue'
import LoginView from '@/views/LoginView.vue'
import MasterDataView from '@/views/MasterDataView.vue'
import InventoryView from '@/views/InventoryView.vue'
import ProcurementView from '@/views/ProcurementView.vue'
import SalesView from '@/views/SalesView.vue'
import WorkflowView from '@/views/WorkflowView.vue'
import FinanceView from '@/views/FinanceView.vue'
import ProjectsView from '@/views/ProjectsView.vue'
import { useAuthStore } from '@/stores/auth'

const placeholderRoutes: RouteRecordRaw[] = [
  ['analytics', 'nav.analytics'],
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
        {
          path: 'workflow',
          name: 'workflow',
          component: WorkflowView,
          meta: { titleKey: 'nav.workflow' },
        },
        {
          path: 'sales',
          name: 'sales',
          component: SalesView,
          meta: { titleKey: 'nav.sales' },
        },
        {
          path: 'procurement',
          name: 'procurement',
          component: ProcurementView,
          meta: { titleKey: 'nav.procurement' },
        },
        {
          path: 'inventory',
          name: 'inventory',
          component: InventoryView,
          meta: { titleKey: 'nav.inventory' },
        },
        {
          path: 'finance',
          name: 'finance',
          component: FinanceView,
          meta: { titleKey: 'nav.finance' },
        },
        {
          path: 'projects',
          name: 'projects',
          component: ProjectsView,
          meta: { titleKey: 'nav.projects' },
        },
        ...placeholderRoutes,
        {
          path: 'settings',
          name: 'settings',
          component: MasterDataView,
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

  return true
})

export default router
