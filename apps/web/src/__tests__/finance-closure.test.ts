/* eslint-disable vue/one-component-per-file -- Lightweight UI stubs belong to this component test. */
import { mount, flushPromises } from '@vue/test-utils'
import { defineComponent } from 'vue'
import { createPinia } from 'pinia'
import { useAuthStore } from '@/stores/auth'
import { createI18n } from 'vue-i18n'
import { beforeEach, expect, it, vi } from 'vitest'
import FinanceClosurePanel from '@/components/finance/FinanceClosurePanel.vue'
import en from '@/i18n/locales/en-US'
import * as api from '@/api/finance-v2'

vi.mock('@/api/finance-v2', () => ({
  getFinanceDashboard: vi.fn(), listFinanceInvoices: vi.fn(), listFinancePayments: vi.fn(), listBankStatementLines: vi.fn(),
  createFinanceInvoice: vi.fn(), createFinancePayment: vi.fn(), postFinanceInvoice: vi.fn(), postFinancePayment: vi.fn(),
}))
vi.mock('@/api/master-data', () => ({ listMasterData: vi.fn().mockResolvedValue({ content: [] }), getOrganizationSettings: vi.fn().mockResolvedValue({ baseCurrencyCode: 'USD' }) }))
const slot = defineComponent({ template: '<div><slot /></div>' })
const alert = defineComponent({ props: { title: { type: String, default: '' } }, template: '<div role="alert">{{ title }}</div>' })
const picker = defineComponent({ props: { modelValue: { type: Array, default: () => [] } }, emits: ['update:modelValue', 'change'], template: '<input />' })
function render() {
  const pinia = createPinia()
  const auth = useAuthStore(pinia)
  auth.user = { id: 'tester', username: 'tester', displayName: 'Tester', organizationId: 'A', organizationName: 'A', departmentId: null, dataScope: 'ALL', permissions: ['finance:view'], roles: ['CUSTOM'], mustChangePassword: false }
  return mount(FinanceClosurePanel, { global: {
    plugins: [pinia, createI18n({ legacy: false, locale: 'en-US', messages: { 'en-US': en } })],
    directives: { loading: {} },
    stubs: { ElCard: slot, ElTabs: slot, ElTabPane: slot, ElTable: defineComponent({ props: { data: { type: Array, default: () => [] } }, template: '<div class="rows">{{ JSON.stringify(data) }}</div>' }), ElTableColumn: true, ElButton: slot, ElAlert: alert, ElDatePicker: picker, ElDialog: true, ElTag: true, ElOption: true, ElSelect: true, ElFormItem: true, ElInput: true, ElInputNumber: true, ElForm: true },
  } })
}
beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(api.getFinanceDashboard).mockResolvedValue({ receivables: 100, payables: 0, cash: 0, revenue: 100, expense: 0, netIncome: 100, trialDebit: 100, trialCredit: 100, unmatchedBankLines: 0, matchExceptions: 0 })
  vi.mocked(api.listFinanceInvoices).mockResolvedValue([])
  vi.mocked(api.listFinancePayments).mockResolvedValue([])
  vi.mocked(api.listBankStatementLines).mockResolvedValue([])
})
it('passes an explicit default range and refreshes lists for the selected range', async () => {
  vi.mocked(api.listFinanceInvoices).mockResolvedValue([{ id: 'oct', number: 'Visible October invoice', accountingDate: '2026-10-01' }, { id: 'sep', number: 'September invoice', accountingDate: '2026-09-01' }] as api.FinanceInvoice[])
  const wrapper = render()
  await flushPromises()
  const args = vi.mocked(api.getFinanceDashboard).mock.calls[0]!
  expect(args).toHaveLength(2)
  expect(args.every(value => /^\d{4}-\d{2}-\d{2}$/.test(value))).toBe(true)
  const date = wrapper.findComponent(picker)
  date.vm.$emit('update:modelValue', ['2026-10-01', '2026-10-31'])
  date.vm.$emit('change')
  await flushPromises()
  expect(api.getFinanceDashboard).toHaveBeenLastCalledWith('2026-10-01', '2026-10-31')
  expect(wrapper.text()).toContain('Visible October invoice')
  expect(wrapper.text()).not.toContain('September invoice')
})
it('keeps a successful invoice list when metrics fail and displays no fake zero metrics', async () => {
  vi.mocked(api.getFinanceDashboard).mockRejectedValue(new Error('unavailable'))
  const today = new Date()
  const date = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-01`
  vi.mocked(api.listFinanceInvoices).mockResolvedValue([{ id: 'one', number: 'Still visible', accountingDate: date }] as api.FinanceInvoice[])
  const wrapper = render()
  await flushPromises()
  expect(wrapper.text()).toContain('Unable to load finance metrics')
  expect(wrapper.text()).toContain('Still visible')
  expect(wrapper.find('.m4-metrics').text()).toContain('—')
})
