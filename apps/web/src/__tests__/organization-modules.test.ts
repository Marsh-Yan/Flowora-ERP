import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { createI18n } from 'vue-i18n'
import ElementPlus from 'element-plus'
import { beforeEach, expect, it, vi } from 'vitest'
import InventoryView from '@/views/InventoryView.vue'
import FinanceClosurePanel from '@/components/finance/FinanceClosurePanel.vue'
import { useAuthStore, type AuthUser } from '@/stores/auth'
import en from '@/i18n/locales/en-US'
import * as stock from '@/api/inventory'
import * as master from '@/api/master-data'
import * as procurement from '@/api/procurement'

vi.mock('@/api/inventory', () => ({ listStockBalances: vi.fn(), listStockLedger: vi.fn(), createStockAdjustment: vi.fn(), receivePurchaseOrder: vi.fn(), transferStock: vi.fn() }))
vi.mock('@/api/trade', () => ({ listAvailability: vi.fn().mockResolvedValue([]), traceInventory: vi.fn() }))
vi.mock('@/api/master-data', () => ({ listMasterData: vi.fn() }))
vi.mock('@/api/procurement', () => ({ listPurchaseOrders: vi.fn() }))
vi.mock('@/api/finance-v2', () => ({
  getFinanceDashboard: vi.fn().mockResolvedValue({}), listFinanceInvoices: vi.fn().mockResolvedValue([]), listFinancePayments: vi.fn().mockResolvedValue([]), listBankStatementLines: vi.fn().mockResolvedValue([]),
  createFinanceInvoice: vi.fn(), createFinancePayment: vi.fn(), postFinanceInvoice: vi.fn(), postFinancePayment: vi.fn(),
}))
beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(stock.listStockBalances).mockResolvedValue({ content: [{ id: 'balance', warehouseId: 'warehouse', itemId: 'item', quantity: 12, averageCost: 10, inventoryValue: 120 }], page: 0, size: 50, totalElements: 1, totalPages: 1 })
  vi.mocked(stock.listStockLedger).mockResolvedValue({ content: [], page: 0, size: 50, totalElements: 0, totalPages: 0 })
  vi.mocked(master.listMasterData).mockResolvedValue({ content: [], page: 0, size: 50, totalElements: 0, totalPages: 0 })
  vi.mocked(procurement.listPurchaseOrders).mockResolvedValue({ content: [], page: 0, size: 50, totalElements: 0, totalPages: 0 })
})
function context(permissions: string[], scope: AuthUser['dataScope'] = 'ALL') {
  const pinia = createPinia(); const auth = useAuthStore(pinia)
  auth.user = { id: 'tester', username: 'tester', displayName: 'Tester', organizationId: 'A', organizationName: 'A', departmentId: null, dataScope: scope, permissions, roles: ['CUSTOM'], mustChangePassword: false }
  return { auth, global: { plugins: [pinia, ElementPlus, createI18n({ legacy: false, locale: 'en-US', messages: { 'en-US': en } })] } }
}
it('loads inventory readers without unauthorized optional APIs or write and trace controls', async () => {
  const wrapper = mount(InventoryView, { global: context(['inventory:view']).global }); await flushPromises()
  expect(wrapper.text()).toContain('120.00')
  expect(wrapper.text()).toContain('warehouse')
  expect(master.listMasterData).not.toHaveBeenCalled(); expect(procurement.listPurchaseOrders).not.toHaveBeenCalled()
  const labels = wrapper.findAll('button').map(button => button.text())
  for (const label of [en.inventory.receive, en.inventory.adjustment, en.inventory.transfer]) expect(labels).not.toContain(label)
  expect(wrapper.find('[id$="-trace"]').exists()).toBe(false)
  wrapper.unmount()
})
it('keeps core inventory when an authorized optional picker fails', async () => {
  vi.mocked(procurement.listPurchaseOrders).mockRejectedValue(new Error('unavailable'))
  const wrapper = mount(InventoryView, { global: context(['inventory:view','inventory:post','master:view','procurement:view']).global }); await flushPromises()
  expect(wrapper.text()).toContain('120.00'); expect(procurement.listPurchaseOrders).toHaveBeenCalledOnce()
  wrapper.unmount()
})
it('shows unavailable inventory totals as dashes when the core request fails', async () => {
  vi.mocked(stock.listStockBalances).mockRejectedValue(new Error('unavailable'))
  const wrapper = mount(InventoryView, { global: context(['inventory:view']).global }); await flushPromises()
  expect(wrapper.find('.inventory-summary-grid').text().match(/—/g)).toHaveLength(3)
  expect(wrapper.find('.inventory-summary-grid').text()).not.toContain('0.00')
  wrapper.unmount()
})
it('finance readers neither request master pickers nor offer invoice or payment creation', async () => {
  const wrapper = mount(FinanceClosurePanel, { global: context(['finance:view']).global }); await flushPromises()
  expect(master.listMasterData).not.toHaveBeenCalled()
  const labels = wrapper.findAll('button').map(button => button.text())
  expect(labels).not.toContain(en.finance.m4.newInvoice); expect(labels).not.toContain(en.finance.m4.newPayment)
  wrapper.unmount()
})
it('restricts organization modules in navigation policy while preserving scoped sales access', () => {
  for (const scope of ['SELF','DEPARTMENT','ASSIGNED'] as const) {
    const { auth } = context(['inventory:view','finance:view','sales:view'], scope)
    expect(auth.hasPermission('inventory:view')).toBe(false); expect(auth.hasPermission('finance:view')).toBe(false)
    expect(auth.hasPermission('sales:view')).toBe(true)
  }
})
