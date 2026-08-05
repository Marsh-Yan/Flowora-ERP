import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

const placeholderRoutes: RouteRecordRaw[] = [
  ['sales', 'nav.sales'],
  ['procurement', 'nav.procurement'],
  ['inventory', 'nav.inventory'],
  ['finance', 'nav.finance'],
  ['workflow', 'nav.workflow'],
  ['analytics', 'nav.analytics'],
  ['settings', 'nav.settings'],
].map(([path, titleKey]) => ({
  path: '/' + path,
  name: path,
  component: () => import('@/views/ModulePlaceholderView.vue'),
  meta: { titleKey },
}))

export default createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/dashboard' },
    {
      path: '/dashboard',
      name: 'dashboard',
      component: () => import('@/views/DashboardView.vue'),
      meta: { titleKey: 'nav.dashboard' },
    },
    ...placeholderRoutes,
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: () => import('@/views/NotFoundView.vue'),
    },
  ],
  scrollBehavior: () => ({ top: 0 }),
})
