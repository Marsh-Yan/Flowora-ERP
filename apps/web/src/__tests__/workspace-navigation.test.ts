import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { createI18n } from 'vue-i18n'
import { createMemoryHistory, createRouter } from 'vue-router'
import ElementPlus from 'element-plus'
import { beforeEach, expect, it, vi } from 'vitest'
import AppLayout from '@/layouts/AppLayout.vue'
import RoleWorkspaceView from '@/views/RoleWorkspaceView.vue'
import { useAuthStore, type AuthUser } from '@/stores/auth'
import { getWorkspace, type WorkspaceSnapshot } from '@/api/analytics'
import en from '@/i18n/locales/en-US'

vi.mock('@/api/analytics', () => ({ getWorkspace: vi.fn() }))
vi.mock('@/api/search', () => ({ searchWorkspace: vi.fn().mockResolvedValue([]) }))
function user(org: string): AuthUser { return { id: 'tester', username: 'tester', displayName: 'Tester', organizationId: org, organizationName: org, departmentId: null, dataScope: 'SELF', permissions: ['workflow:view'], roles: ['APPROVER'], mustChangePassword: false } }
function snapshot(org: string, count: number): WorkspaceSnapshot { return { organizationId: org, roles: ['APPROVER'], refreshedAt: '', cards: [{ code: 'MY_APPROVALS', value: count, route: '/workflow', requiredPermission: 'workflow:view', severity: 'INFO' }], risks: [] } }
beforeEach(() => vi.clearAllMocks())
async function render() {
  const pinia = createPinia()
  const auth = useAuthStore(pinia)
  auth.user = user('A')
  auth.organizations = [{ id: 'A', name: 'A', defaultOrganization: true, departmentId: null }, { id: 'B', name: 'B', defaultOrganization: false, departmentId: null }]
  vi.spyOn(auth, 'loadOrganizations').mockResolvedValue(auth.organizations)
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/', component: AppLayout, children: [{ path: 'dashboard', name: 'dashboard', component: RoleWorkspaceView }, { path: 'workflow', component: { template: '<div>Workflow page</div>' } }] }] })
  await router.push('/dashboard'); await router.isReady()
  const wrapper = mount({ template: '<router-view />' }, { global: { plugins: [pinia, router, ElementPlus, createI18n({ legacy: false, locale: 'en-US', messages: { 'en-US': en } })] }, attachTo: document.body })
  await flushPromises()
  return { wrapper, auth, router }
}
it('opens accessible permission-filtered navigation and closes it after navigation', async () => {
  vi.mocked(getWorkspace).mockResolvedValue(snapshot('A', 7))
  const { wrapper, router } = await render()
  const toggle = wrapper.find('button.mobile-menu')
  expect(toggle.attributes('aria-controls')).toBe('mobile-navigation')
  expect(toggle.attributes('aria-expanded')).toBe('false')
  await toggle.trigger('click'); await flushPromises()
  expect(toggle.attributes('aria-expanded')).toBe('true')
  const navigation = document.querySelector('#mobile-navigation')!
  expect(navigation.textContent).toContain('Workflow')
  expect(navigation.textContent).not.toContain('Finance')
  const workflow = navigation.querySelector<HTMLAnchorElement>('a[href="/workflow"]')!
  workflow.click(); await flushPromises()
  expect(router.currentRoute.value.path).toBe('/workflow')
  expect(toggle.attributes('aria-expanded')).toBe('false')
  wrapper.unmount()
})
it('replaces same-route workspace on organization switch and discards a late old response', async () => {
  let finishOld!: (value: WorkspaceSnapshot) => void
  vi.mocked(getWorkspace).mockResolvedValueOnce(snapshot('A', 7)).mockImplementationOnce(() => new Promise(resolve => { finishOld = resolve })).mockResolvedValueOnce(snapshot('B', 2))
  const { wrapper, auth, router } = await render()
  expect(wrapper.find('.metric-card strong').text()).toBe('7')
  await wrapper.find('.page-heading button').trigger('click'); await flushPromises()
  auth.user = user('B'); await flushPromises()
  expect(router.currentRoute.value.path).toBe('/dashboard')
  expect(wrapper.find('.metric-card strong').text()).toBe('2')
  finishOld(snapshot('A', 99)); await flushPromises()
  expect(wrapper.find('.metric-card strong').text()).toBe('2')
  expect(getWorkspace).toHaveBeenCalledTimes(3)
  wrapper.unmount()
})
