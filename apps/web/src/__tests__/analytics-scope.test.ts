import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import { beforeEach, expect, it, vi } from 'vitest'
import AnalyticsView from '@/views/AnalyticsView.vue'
import { useAuthStore, type AuthUser } from '@/stores/auth'
import * as api from '@/api/analytics'

vi.mock('@/api/analytics', () => ({
  getAnalytics: vi.fn(), getSavedViews: vi.fn().mockResolvedValue([]),
  getOrganizationAnalytics: vi.fn().mockResolvedValue([]), getExports: vi.fn().mockResolvedValue([]),
  getDiagnostics: vi.fn(), createExport: vi.fn(), downloadExport: vi.fn(), removeView: vi.fn(), saveView: vi.fn(),
}))
beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(api.getAnalytics).mockResolvedValue({ from: '2026-10-01', to: '2026-10-01', currencyCode: 'USD', refreshedAt: '',
    trends: [{ period: '2026-10', sales: 71, purchases: null, revenue: null, expense: null, grossProfit: null }] })
})
async function render(scope: AuthUser['dataScope']) {
  const pinia = createPinia()
  const auth = useAuthStore(pinia)
  auth.user = { id: 'tester', username: 'tester', displayName: 'Tester', organizationId: 'A', organizationName: 'A', departmentId: null,
    dataScope: scope, permissions: ['analytics:view', 'analytics:cross-org', 'analytics:export', 'sales:view'], roles: ['CUSTOM'], mustChangePassword: false }
  const wrapper = mount(AnalyticsView, { global: { plugins: [pinia, ElementPlus] } })
  await flushPromises()
  return wrapper
}
it('displays unavailable metrics as dashes and exposes only the permitted scoped export', async () => {
  const wrapper = await render('SELF')
  expect(wrapper.text()).toContain('$71.00')
  expect(wrapper.text().match(/—/g)).toHaveLength(4)
  expect(wrapper.text()).not.toContain('$0.00')
  expect(api.getOrganizationAnalytics).not.toHaveBeenCalled()
  expect(wrapper.findAll('button').filter(button => ['SALES', 'PURCHASES', 'INVENTORY', 'FINANCE', 'PROJECTS'].includes(button.text())).map(button => button.text())).toEqual(['SALES'])
  wrapper.unmount()
})
it('does not offer unsupported assigned exports or request organization-wide aggregates', async () => {
  const wrapper = await render('ASSIGNED')
  expect(api.getOrganizationAnalytics).not.toHaveBeenCalled()
  expect(wrapper.findAll('button').some(button => button.text() === 'SALES')).toBe(false)
  wrapper.unmount()
})
