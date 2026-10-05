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

vi.mock('@/api/inventory', () => ({ getStockSummary: vi.fn(), listStockBalances: vi.fn(), listStockLedger: vi.fn(), createStockAdjustment: vi.fn(), receivePurchaseOrder: vi.fn(), transferStock: vi.fn() }))
vi.mock('@/api/trade', () => ({ listAvailability: vi.fn().mockResolvedValue([]), traceInventory: vi.fn() }))
vi.mock('@/api/master-data', () => ({ listMasterData: vi.fn() }))
vi.mock('@/api/procurement', () => ({ listPurchaseOrders: vi.fn() }))
vi.mock('@/api/finance-v2', () => ({
  getFinanceDashboard: vi.fn().mockResolvedValue({}), listFinanceInvoices: vi.fn().mockResolvedValue([]), listFinancePayments: vi.fn().mockResolvedValue([]), listBankStatementLines: vi.fn().mockResolvedValue([]),
  createFinanceInvoice: vi.fn(), createFinancePayment: vi.fn(), postFinanceInvoice: vi.fn(), postFinancePayment: vi.fn(), allocateFinancePayment: vi.fn(), reverseFinanceAllocation: vi.fn(),
}))
beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(stock.getStockSummary).mockResolvedValue({ inventoryValue: 120, balanceCount: 1, ledgerCount: 0 })
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
it('shows unavailable inventory totals as dashes without hiding successful tables', async () => {
  vi.mocked(stock.getStockSummary).mockRejectedValue(new Error('unavailable'))
  const wrapper = mount(InventoryView, { global: context(['inventory:view']).global }); await flushPromises()
  expect(wrapper.find('.inventory-summary-grid').text().match(/—/g)).toHaveLength(3)
  expect(wrapper.find('.inventory-summary-grid').text()).not.toContain('0.00')
  expect(wrapper.text()).toContain('warehouse')
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

function balance(id: string, value: number) { return { id, warehouseId: id, itemId: id, quantity: 1, averageCost: value, inventoryValue: value } }
function page<T>(rows: T[], index: number, total: number) { return { content: rows, page: index, size: 50, totalElements: total, totalPages: Math.ceil(total / 50) } }

it('uses full summary and supports independent balance and ledger pages', async () => {
  vi.mocked(stock.getStockSummary).mockResolvedValue({ inventoryValue: 550, balanceCount: 51, ledgerCount: 52 })
  vi.mocked(stock.listStockBalances).mockResolvedValueOnce(page([balance('first', 50)], 0, 51)).mockResolvedValueOnce(page([balance('last', 500)], 1, 51))
  vi.mocked(stock.listStockLedger).mockResolvedValueOnce(page([], 0, 52)).mockResolvedValueOnce(page([], 1, 52))
  const wrapper = mount(InventoryView, { global: context(['inventory:view']).global }); await flushPromises()
  expect(wrapper.find('.inventory-summary-grid').text()).toContain('550.00')
  await wrapper.find('[data-testid="balances-pagination"] .btn-next').trigger('click'); await flushPromises()
  expect(stock.listStockBalances).toHaveBeenLastCalledWith('', 1, 50)
  expect(wrapper.text()).toContain('last')
  expect(wrapper.find('.inventory-summary-grid').text()).toContain('550.00')
  expect(stock.getStockSummary).toHaveBeenCalledOnce()
  await wrapper.find('[id$="-ledger"]').trigger('click'); await flushPromises()
  await wrapper.find('[data-testid="ledger-pagination"] .btn-next').trigger('click'); await flushPromises()
  expect(stock.listStockLedger).toHaveBeenLastCalledWith('', '', 1, 50)
  expect(stock.listStockBalances).toHaveBeenCalledTimes(2)
  wrapper.unmount()
})

it('keeps a successful summary and offers retry after a page error without fake empty rows', async () => {
  vi.mocked(stock.listStockBalances).mockRejectedValueOnce(new Error('unavailable'))
  const wrapper = mount(InventoryView, { global: context(['inventory:view']).global }); await flushPromises()
  expect(wrapper.find('.inventory-summary-grid').text()).toContain('120.00')
  expect(wrapper.find('#pane-balances [role="alert"]').exists()).toBe(true)
  expect(wrapper.find('#pane-balances .el-empty').exists()).toBe(false)
  await wrapper.find('#pane-balances [role="alert"] button').trigger('click'); await flushPromises()
  expect(wrapper.find('#pane-balances [role="alert"]').exists()).toBe(false)
  expect(wrapper.text()).toContain('warehouse')
  wrapper.unmount()
})

it('refresh invalidates a delayed page response and resets both pagers', async () => {
  let resolveOld!: (result: Awaited<ReturnType<typeof stock.listStockBalances>>) => void
  vi.mocked(stock.listStockBalances).mockResolvedValueOnce(page([balance('initial', 10)], 0, 51))
    .mockImplementationOnce(() => new Promise(resolve => { resolveOld = resolve }))
    .mockResolvedValueOnce(page([balance('refreshed', 20)], 0, 51))
  const wrapper = mount(InventoryView, { global: context(['inventory:view']).global }); await flushPromises()
  await wrapper.find('[data-testid="balances-pagination"] .btn-next').trigger('click'); await flushPromises()
  await wrapper.find('.operations-heading button').trigger('click'); await flushPromises()
  resolveOld(page([balance('obsolete', 99)], 1, 51)); await flushPromises()
  expect(wrapper.text()).toContain('refreshed'); expect(wrapper.text()).not.toContain('obsolete')
  expect(wrapper.find('[data-testid="balances-pagination"] .number.is-active').text()).toBe('1')
  expect(stock.listStockBalances).toHaveBeenLastCalledWith('', 0, 50)
  wrapper.unmount()
})

it('clamps a page that has disappeared to the last available page', async () => {
  vi.mocked(stock.listStockBalances).mockResolvedValueOnce(page([balance('initial', 10)], 0, 51))
    .mockResolvedValueOnce(page([], 1, 1)).mockResolvedValueOnce(page([balance('remaining', 20)], 0, 1))
  const wrapper = mount(InventoryView, { global: context(['inventory:view']).global }); await flushPromises()
  await wrapper.find('[data-testid="balances-pagination"] .btn-next').trigger('click'); await flushPromises()
  expect(stock.listStockBalances).toHaveBeenLastCalledWith('', 0, 50)
  expect(wrapper.text()).toContain('remaining')
  expect(wrapper.find('[data-testid="balances-pagination"] .number.is-active').text()).toBe('1')
  wrapper.unmount()
})

it('distinguishes a successfully loaded empty inventory from unavailable summary', async () => {
  vi.mocked(stock.getStockSummary).mockResolvedValue({ inventoryValue: 0, balanceCount: 0, ledgerCount: 0 })
  vi.mocked(stock.listStockBalances).mockResolvedValue(page([], 0, 0))
  const wrapper = mount(InventoryView, { global: context(['inventory:view']).global }); await flushPromises()
  expect(wrapper.find('.inventory-summary-grid').text()).toContain('0.00')
  expect(wrapper.find('.inventory-summary-grid').text()).not.toContain('—')
  expect(wrapper.find('#pane-balances .el-empty').exists()).toBe(true)
  wrapper.unmount()
})

function receiptOrder(id = 'po', remaining = 4): procurement.PurchaseOrder {
  return { id, number: id, status: 'CONFIRMED', supplierId: 'supplier', warehouseId: `warehouse-${id}`, buyerUserId: 'buyer', lineId: `line-${id}`, itemId: 'item', orderedQuantity: 4, receivedQuantity: 4 - remaining, remainingQuantity: remaining, unitPrice: id === 'po' ? 8 : 9, taxRate: 0, orderDate: '2026-10-03' }
}
const receiptPermissions = ['inventory:view', 'inventory:post', 'master:view', 'procurement:view']
const buttonNamed = (wrapper: ReturnType<typeof mount>, text: string) => wrapper.findAll('button').find(button => button.text() === text)!
async function receiptEditor(orders = [receiptOrder()]) {
  vi.mocked(stock.receivePurchaseOrder).mockReset().mockResolvedValue(undefined)
  vi.mocked(procurement.listPurchaseOrders).mockResolvedValue({ content: orders, page: 0, size: 50, totalElements: orders.length, totalPages: 1 })
  const wrapper = mount(InventoryView, { global: { ...context(receiptPermissions).global, stubs: { teleport: true, ElSelect: true, ElOption: true } } }); await flushPromises()
  await buttonNamed(wrapper, en.inventory.receive).trigger('click'); await flushPromises()
  return wrapper
}
it('receipt opening selects a coherent default order without an extra change event', async () => {
  const wrapper = await receiptEditor()
  await buttonNamed(wrapper, en.masterData.save).trigger('click'); await flushPromises()
  expect(stock.receivePurchaseOrder).toHaveBeenCalledWith({ purchaseOrderId: 'po', purchaseOrderLineId: 'line-po', warehouseId: 'warehouse-po', quantity: 4, unitCost: 8 })
  wrapper.unmount()
})
it('receipt selection changes order, line, warehouse, remaining quantity and cost together', async () => {
  const wrapper = await receiptEditor([receiptOrder(), receiptOrder('second', 2)])
  const picker = wrapper.findComponent({ name: 'ElSelect' }); picker.vm.$emit('update:modelValue', 'second'); picker.vm.$emit('change', 'second'); await flushPromises()
  await buttonNamed(wrapper, en.masterData.save).trigger('click'); await flushPromises()
  expect(stock.receivePurchaseOrder).toHaveBeenCalledWith({ purchaseOrderId: 'second', purchaseOrderLineId: 'line-second', warehouseId: 'warehouse-second', quantity: 2, unitCost: 9 })
  wrapper.unmount()
})
it('receipt loading and empty eligible orders cannot open a writable receipt', async () => {
  const wrapper = await receiptEditor([])
  expect(buttonNamed(wrapper, en.inventory.receive).attributes('disabled')).toBeDefined()
  expect(wrapper.findAll('button').some(button => button.text() === en.masterData.save)).toBe(false)
  wrapper.unmount()
})
it('receipt excludes orders without a real line identifier', async () => {
  const order = receiptOrder(); delete order.lineId
  const wrapper = await receiptEditor([order])
  expect(buttonNamed(wrapper, en.inventory.receive).attributes('disabled')).toBeDefined()
  wrapper.unmount()
})
it('receipt does not retain a previously fulfilled order after refresh and reopening', async () => {
  const wrapper = await receiptEditor()
  await buttonNamed(wrapper, en.masterData.save).trigger('click'); await flushPromises()
  vi.mocked(procurement.listPurchaseOrders).mockResolvedValue({ content: [], page: 0, size: 50, totalElements: 0, totalPages: 0 })
  await buttonNamed(wrapper, en.inventory.refresh).trigger('click'); await flushPromises()
  expect(buttonNamed(wrapper, en.inventory.receive).attributes('disabled')).toBeDefined()
  expect(stock.receivePurchaseOrder).toHaveBeenCalledTimes(1)
  wrapper.unmount()
})
it('receipt guards repeated save clicks until posting completes', async () => {
  const wrapper = await receiptEditor()
  let done!: () => void
  vi.mocked(stock.receivePurchaseOrder).mockReturnValue(new Promise(resolve => { done = () => resolve(undefined) }))
  const save = buttonNamed(wrapper, en.masterData.save)
  await save.trigger('click'); await save.trigger('click')
  expect(stock.receivePurchaseOrder).toHaveBeenCalledTimes(1)
  expect(buttonNamed(wrapper, en.masterData.save).attributes('disabled')).toBeDefined()
  done(); await flushPromises(); wrapper.unmount()
})
it('receipt failed posting retains its selection and permits a deliberate retry', async () => {
  const wrapper = await receiptEditor()
  vi.mocked(stock.receivePurchaseOrder).mockRejectedValueOnce(new Error('posting failed'))
  const save = buttonNamed(wrapper, en.masterData.save); await save.trigger('click'); await flushPromises()
  expect(save.attributes('disabled')).toBeUndefined()
  await buttonNamed(wrapper, en.masterData.save).trigger('click'); await flushPromises()
  expect(stock.receivePurchaseOrder).toHaveBeenCalledTimes(2)
  expect(vi.mocked(stock.receivePurchaseOrder).mock.calls[1]![0].purchaseOrderId).toBe('po')
  wrapper.unmount()
})

it('receipt reopening uses the refreshed remaining quantity instead of the previous form', async () => {
  const wrapper = await receiptEditor()
  vi.mocked(procurement.listPurchaseOrders).mockResolvedValue({ content: [receiptOrder('po', 1)], page: 0, size: 50, totalElements: 1, totalPages: 1 })
  await buttonNamed(wrapper, en.masterData.save).trigger('click'); await flushPromises()
  await buttonNamed(wrapper, en.inventory.receive).trigger('click'); await flushPromises()
  await buttonNamed(wrapper, en.masterData.save).trigger('click'); await flushPromises()
  expect(vi.mocked(stock.receivePurchaseOrder).mock.calls[1]![0].quantity).toBe(1)
  wrapper.unmount()
})
