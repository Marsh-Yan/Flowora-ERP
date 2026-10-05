import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { defineComponent, h } from 'vue'
import * as trade from '@/api/trade'
import { createI18n } from 'vue-i18n'
import ElementPlus, { ElMessage, ElMessageBox } from 'element-plus'
import { beforeEach, expect, it, vi } from 'vitest'
import ProcurementView from '@/views/ProcurementView.vue'
import SalesView from '@/views/SalesView.vue'
import { useAuthStore, type AuthUser } from '@/stores/auth'
import en from '@/i18n/locales/en-US'
import * as procurement from '@/api/procurement'
import * as sales from '@/api/sales'
import * as master from '@/api/master-data'

vi.mock('@/api/procurement', () => ({ listPurchaseOrders: vi.fn(), listPurchaseRequests: vi.fn(), createPurchaseRequest: vi.fn() }))
vi.mock('@/api/sales', () => ({ listSalesOrders: vi.fn(), listSalesQuotes: vi.fn(), listReceivables: vi.fn(), createSalesQuote: vi.fn(), approveSalesQuote: vi.fn(), createDelivery: vi.fn(), createPayment: vi.fn() }))
vi.mock('@/api/trade', () => ({ createPurchaseOrderV2: vi.fn(), createSalesOrderV2: vi.fn(), getTradeOrderV2: vi.fn(), changeTradeOrderStateV2: vi.fn() }))
vi.mock('@/api/master-data', () => ({ listMasterData: vi.fn(), getOrganizationSettings: vi.fn() }))
const page = <T>(content: T[]) => ({ content, page: 0, size: 50, totalElements: content.length, totalPages: 1 })
beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(master.getOrganizationSettings).mockResolvedValue({baseCurrencyCode:'CNY'} as Awaited<ReturnType<typeof master.getOrganizationSettings>>)
  vi.mocked(procurement.listPurchaseOrders).mockResolvedValue(page([{ id: 'po', number: 'PO-owned', status: 'DRAFT', supplierId: 'supplier', itemId: 'item', warehouseId: 'warehouse' } as procurement.PurchaseOrder]))
  vi.mocked(procurement.listPurchaseRequests).mockResolvedValue(page([]))
  vi.mocked(sales.listSalesQuotes).mockResolvedValue(page([{ id: 'quote', number: 'QT', status: 'SUBMITTED' } as sales.SalesQuote]))
  vi.mocked(sales.listSalesOrders).mockResolvedValue(page([
    { id: 'so', number: 'SO-owned', status: 'CONFIRMED', remainingQuantity: 1, customerId: 'customer', warehouseId: 'warehouse' } as sales.SalesOrder,
    { id: 'draft', number: 'SO-draft', status: 'DRAFT', remainingQuantity: 1 } as sales.SalesOrder,
  ]))
  vi.mocked(sales.listReceivables).mockResolvedValue(page([{ id: 'ar', number: 'AR', outstandingAmount: 12 } as sales.Receivable]))
  vi.mocked(master.listMasterData).mockResolvedValue(page([]))
})
function global(permissions: string[], dataScope: AuthUser['dataScope'] = 'ALL') {
  const pinia = createPinia(); const auth = useAuthStore(pinia)
  auth.user = { id: 'user', username: 'user', displayName: 'User', organizationId: 'org', organizationName: 'Org', departmentId: null, roles: ['CUSTOM'], permissions, dataScope, mustChangePassword: false }
  return { plugins: [pinia, ElementPlus, createI18n({ legacy: false, locale: 'en-US', messages: { 'en-US': en } })], stubs: { teleport: true, ElSelect: true } }
}
const labels = (wrapper: ReturnType<typeof mount>) => wrapper.findAll('button').map(b => b.text())

it('scoped procurement readers load only scoped orders without shared or master APIs', async () => {
  for (const scope of ['SELF', 'DEPARTMENT', 'ASSIGNED'] as const) {
    const w = mount(ProcurementView, { global: global(['procurement:view'], scope) }); await flushPromises()
    expect(w.find('#pane-orders').text()).toContain('PO-owned')
    expect(w.find('[id$="-requests"]').exists()).toBe(false)
    expect(labels(w)).not.toContain(en.procurement.create); w.unmount()
  }
  expect(procurement.listPurchaseRequests).not.toHaveBeenCalled(); expect(master.listMasterData).not.toHaveBeenCalled()
})
it('scoped sales readers skip quotes and shared money and retain order identifiers', async () => {
  for (const scope of ['SELF', 'DEPARTMENT', 'ASSIGNED'] as const) {
    const w = mount(SalesView, { global: global(['sales:view'], scope) }); await flushPromises()
    expect(w.find('#pane-orders').text()).toContain('SO-owned')
    expect(w.findAll('.inventory-summary-grid strong')).toHaveLength(1)
    expect(w.find('[id$="-quotes"]').exists()).toBe(false); expect(w.find('[id$="-receivables"]').exists()).toBe(false)
    expect(labels(w)).not.toContain(en.sales.create); expect(labels(w)).not.toContain(en.sales.deliver); w.unmount()
  }
  expect(sales.listSalesQuotes).not.toHaveBeenCalled(); expect(sales.listReceivables).not.toHaveBeenCalled(); expect(master.listMasterData).not.toHaveBeenCalled()
})
it('all-scope readers have no quote decisions, deliveries or payments', async () => {
  const w = mount(SalesView, { global: global(['sales:view']) }); await flushPromises()
  expect(w.text()).toContain('12.00')
  for (const label of [en.sales.create, en.sales.approve, en.sales.deliver, en.sales.receivePayment]) expect(labels(w)).not.toContain(label)
  expect(master.listMasterData).not.toHaveBeenCalled(); w.unmount()
})
it('custom current capabilities show decisions and postings with draft deliveries hidden', async () => {
  const w = mount(SalesView, { global: global(['sales:view', 'workflow:approve', 'inventory:post', 'finance:post']) }); await flushPromises()
  expect(labels(w)).toContain(en.sales.approve); expect(labels(w)).toContain(en.sales.receivePayment)
  expect(labels(w).filter(label => label === en.sales.deliver)).toHaveLength(1)
  expect(labels(w)).not.toContain(en.sales.create); w.unmount()
})
it('scoped draft creation remains available with create and master permissions', async () => {
  for (const [view, module] of [[ProcurementView, 'procurement'], [SalesView, 'sales']] as const) {
    const w = mount(view, { global: global([`${module}:view`, `${module}:create`, 'master:view'], 'SELF') }); await flushPromises()
    const button = w.findAll('button').find(b => b.text() === en[module].create)!
    expect(button).toBeDefined(); await button.trigger('click'); await flushPromises()
    expect(w.text()).not.toContain(module === 'sales' ? en.sales.sourceQuote : en.procurement.sourceRequest)
    w.unmount()
  }
})
it('failed shared reads and optional master pickers preserve orders and show unknown money', async () => {
  vi.mocked(master.listMasterData).mockRejectedValue(new Error('unavailable'))
  vi.mocked(procurement.listPurchaseRequests).mockRejectedValue(new Error('unavailable'))
  const p = mount(ProcurementView, { global: global(['procurement:view', 'master:view']) }); await flushPromises()
  expect(p.find('#pane-orders').text()).toContain('PO-owned'); expect(p.find('#pane-requests [role="alert"]').exists()).toBe(true)
  expect(p.find('#pane-requests .el-empty').exists()).toBe(false); p.unmount()
  vi.mocked(sales.listReceivables).mockRejectedValue(new Error('unavailable'))
  const s = mount(SalesView, { global: global(['sales:view', 'master:view']) }); await flushPromises()
  expect(s.find('#pane-orders').text()).toContain('SO-owned')
  expect(s.findAll('.inventory-summary-grid strong')[1]?.text()).toBe('—')
  expect(s.find('#pane-receivables [role="alert"]').exists()).toBe(true); expect(s.find('#pane-receivables .el-empty').exists()).toBe(false); s.unmount()
})

const sourceCases = [
  { view: ProcurementView, module: 'procurement', sourceLabel: en.procurement.sourceRequest, type: 'PURCHASE_REQUEST', create: trade.createPurchaseOrderV2 },
  { view: SalesView, module: 'sales', sourceLabel: en.sales.sourceQuote, type: 'SALES_QUOTE', create: trade.createSalesOrderV2 },
] as const

// Two native controls intentionally live beside the component tests.
// eslint-disable-next-line vue/one-component-per-file
const NativeSelect = defineComponent({
  props: { modelValue: { type: String, default: '' } }, emits: ['update:modelValue', 'change'],
  setup(props, { emit, slots }) {
    return () => h('select', { value: props.modelValue, onChange: (event: Event) => {
      const value = (event.target as HTMLSelectElement).value
      emit('update:modelValue', value); emit('change', value)
    } }, [h('option', { value: '' }, 'None'), slots.default?.()])
  },
})
// eslint-disable-next-line vue/one-component-per-file
const NativeOption = defineComponent({
  props: { value: { type: String, default: '' }, label: { type: String, default: '' } },
  setup(props) { return () => h('option', { value: props.value }, props.label) },
})
async function sourceEditor(entry: typeof sourceCases[number], sourceEligible = true) {
  const source = { id: 'source-header', lineId: 'source-line', number: 'SRC-1', status: 'APPROVED', supplierId: 'supplier',
    customerId: 'customer', warehouseId: 'warehouse', itemId: 'item', quantity: 3, estimatedUnitCost: 10,
    unitPrice: 10, discountRate: 0, taxRate: 0, currencyCode: 'CNY', requesterUserId: 'user',
    validUntil: '2026-10-31', sourceEligible, totalAmount: 30, note: '' }
  vi.mocked(procurement.listPurchaseRequests).mockResolvedValue(page([source as procurement.PurchaseRequest]))
  vi.mocked(sales.listSalesQuotes).mockResolvedValue(page([source as sales.SalesQuote]))
  const options = global([`${entry.module}:view`, `${entry.module}:create`, 'master:view'])
  const wrapper = mount(entry.view, { global: { ...options, stubs: { ...options.stubs, ElSelect: NativeSelect, ElOption: NativeOption } } })
  await flushPromises()
  await wrapper.find('[id$="-orders"]').trigger('click'); await flushPromises()
  const button = wrapper.findAll('button').find(b => b.text() === en[entry.module].create)!
  await button.trigger('click'); await flushPromises()
  const field = wrapper.findAll('.el-form-item').find(f => f.text().includes(entry.sourceLabel))!
  if (sourceEligible) { await field.find('select').setValue('source-header'); await flushPromises() }
  return { wrapper, field, button }
}
async function saveEditor(wrapper: ReturnType<typeof mount>) {
  await wrapper.findAll('button').find(b => b.text() === en.masterData.save)!.trigger('click')
  await flushPromises()
}

it.each(sourceCases)('$module sends the selected real source line and leaves added lines independent', async entry => {
  const { wrapper } = await sourceEditor(entry)
  await wrapper.findAll('button').find(b => b.text().includes('Add line'))!.trigger('click')
  await saveEditor(wrapper)
  const payload = vi.mocked(entry.create).mock.calls[0]![0]
  expect(payload.lines[0]).toMatchObject({ sourceDocumentType: entry.type, sourceDocumentId: 'source-header', sourceLineId: 'source-line' })
  expect(payload.lines[1]).not.toHaveProperty('sourceLineId')
  wrapper.unmount()
})
it.each(sourceCases)('$module clearing the source removes the line association', async entry => {
  const { wrapper, field } = await sourceEditor(entry)
  await field.find('select').setValue('')
  await saveEditor(wrapper)
  expect(vi.mocked(entry.create).mock.calls[0]![0].lines[0]).not.toHaveProperty('sourceLineId')
  wrapper.unmount()
})
it.each(sourceCases)('$module deleting the source line keeps the remaining line independent', async entry => {
  const { wrapper } = await sourceEditor(entry)
  await wrapper.findAll('button').find(b => b.text().includes('Add line'))!.trigger('click')
  await wrapper.findAll('button').find(b => b.text() === '−')!.trigger('click')
  await saveEditor(wrapper)
  const lines = vi.mocked(entry.create).mock.calls[0]![0].lines
  expect(lines).toHaveLength(1); expect(lines[0]).not.toHaveProperty('sourceLineId')
  wrapper.unmount()
})
it.each(sourceCases)('$module reopening refreshes the editor source selection', async entry => {
  const { wrapper, button } = await sourceEditor(entry)
  await wrapper.findAll('button').find(b => b.text() === en.masterData.cancel)!.trigger('click')
  await button.trigger('click'); await flushPromises()
  await saveEditor(wrapper)
  const line = vi.mocked(entry.create).mock.calls[0]![0].lines[0]
  if (entry.module === 'sales') expect(line).not.toHaveProperty('sourceLineId')
  else expect(line).toMatchObject({ sourceLineId: 'source-line', sourceDocumentId: 'source-header' })
  wrapper.unmount()
})


it.each(sourceCases)('$module keeps the form open and explains remaining source quantity', async entry => {
  const { wrapper } = await sourceEditor(entry)
  vi.mocked(entry.create).mockRejectedValueOnce({ isAxiosError: true, response: { data: { code: 'SOURCE_QUANTITY_EXCEEDED' } } })
  const message = vi.spyOn(ElMessage, 'error')
  await saveEditor(wrapper)
  expect(message).toHaveBeenCalledWith(en.errors.sourceQuantityExceeded)
  expect(wrapper.findAll('button').some(b => b.text() === en.masterData.save)).toBe(true)
  message.mockRestore(); wrapper.unmount()
})

it('sales omits an approved quote that the server marks expired', async () => {
  const entry = sourceCases.find(entry => entry.module === 'sales')!
  const { wrapper, field } = await sourceEditor(entry, false)
  expect(field.findAll('option').some(option => option.text() === 'SRC-1')).toBe(false)
  wrapper.unmount()
})

it('sales explains expiry that occurs after the source list was loaded', async () => {
  const entry = sourceCases.find(entry => entry.module === 'sales')!
  const { wrapper } = await sourceEditor(entry)
  vi.mocked(entry.create).mockRejectedValueOnce({ isAxiosError: true, response: { data: { code: 'SOURCE_QUOTE_EXPIRED' } } })
  const message = vi.spyOn(ElMessage, 'error')
  await saveEditor(wrapper)
  expect(message).toHaveBeenCalledWith(en.errors.sourceQuoteExpired)
  expect(wrapper.findAll('button').some(button => button.text() === en.masterData.save)).toBe(true)
  message.mockRestore(); wrapper.unmount()
})

it.each(sourceCases)('$module explains reuse of a request number with changed content', async entry => {
  const { wrapper } = await sourceEditor(entry)
  vi.mocked(entry.create).mockRejectedValueOnce({ isAxiosError: true, response: { data: { code: 'IDEMPOTENCY_REQUEST_MISMATCH' } } })
  const message = vi.spyOn(ElMessage, 'error')
  await saveEditor(wrapper)
  expect(message).toHaveBeenCalledWith(en.errors.idempotencyRequestMismatch)
  expect(wrapper.findAll('button').some(button => button.text() === en.masterData.save)).toBe(true)
  message.mockRestore(); wrapper.unmount()
})

async function orderActionsEditor(entry: typeof sourceCases[number], permissions = [`${entry.module}:view`, `${entry.module}:submit`]) {
  const order = { buyerUserId: 'buyer', lineId: 'line', unitPrice: 10, discountRate: 0, taxRate: 0, currencyCode: 'CNY', orderDate: '2026-10-03', totalAmount: 30, receivableAmount: 0, paidAmount: 0, outstandingAmount: 0, id: 'order', number: 'ORDER-1', status: 'DRAFT', supplierId: 'supplier', customerId: 'customer',
    itemId: 'item', warehouseId: 'warehouse', orderedQuantity: 3, receivedQuantity: 0, fulfilledQuantity: 0, remainingQuantity: 3 }
  vi.mocked(procurement.listPurchaseOrders).mockResolvedValue(page([order as procurement.PurchaseOrder]))
  vi.mocked(sales.listSalesOrders).mockResolvedValue(page([order as sales.SalesOrder]))
  const wrapper = mount(entry.view, { global: global(permissions, 'SELF') })
  await flushPromises()
  await wrapper.find('[id$="-orders"]').trigger('click'); await flushPromises()
  return wrapper
}
function actionButton(wrapper: ReturnType<typeof mount>, text: string) {
  return wrapper.findAll('button').find(button => button.text() === text)!
}

it.each(sourceCases)('$module confirms using the current scoped document version and refreshes', async entry => {
  const wrapper = await orderActionsEditor(entry)
  vi.mocked(trade.getTradeOrderV2).mockResolvedValue({ id: 'order', version: 7 } as trade.TradeDocument)
  await actionButton(wrapper, en.tradeActions.confirmOrder).trigger('click'); await flushPromises()
  expect(trade.getTradeOrderV2).toHaveBeenCalledWith(entry.module, 'order')
  expect(trade.changeTradeOrderStateV2).toHaveBeenCalledWith(entry.module, 'order', 'confirm', 7)
  expect(entry.module === 'sales' ? sales.listSalesOrders : procurement.listPurchaseOrders).toHaveBeenCalledTimes(2)
  wrapper.unmount()
})
it.each(sourceCases)('$module cancels only after the user confirms the named order', async entry => {
  const wrapper = await orderActionsEditor(entry)
  const prompt = vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue('confirm' as Awaited<ReturnType<typeof ElMessageBox.confirm>>)
  vi.mocked(trade.getTradeOrderV2).mockResolvedValue({ id: 'order', version: 8 } as trade.TradeDocument)
  await actionButton(wrapper, en.tradeActions.cancelOrder).trigger('click'); await flushPromises()
  expect(prompt.mock.calls[0]![0]).toContain('ORDER-1')
  expect(trade.changeTradeOrderStateV2).toHaveBeenCalledWith(entry.module, 'order', 'cancel', 8)
  prompt.mockRestore(); wrapper.unmount()
})
it.each(sourceCases)('$module dismissing cancellation performs no request', async entry => {
  const wrapper = await orderActionsEditor(entry)
  const prompt = vi.spyOn(ElMessageBox, 'confirm').mockRejectedValue('cancel')
  await actionButton(wrapper, en.tradeActions.cancelOrder).trigger('click'); await flushPromises()
  expect(trade.getTradeOrderV2).not.toHaveBeenCalled(); expect(trade.changeTradeOrderStateV2).not.toHaveBeenCalled()
  expect(actionButton(wrapper, en.tradeActions.confirmOrder).attributes('disabled')).toBeUndefined()
  prompt.mockRestore(); wrapper.unmount()
})
it.each(sourceCases)('$module requires both read and submit capabilities for order actions', async entry => {
  for (const permissions of [[`${entry.module}:view`], [`${entry.module}:submit`]]) {
    const wrapper = await orderActionsEditor(entry, permissions)
    expect(labels(wrapper)).not.toContain(en.tradeActions.confirmOrder)
    expect(labels(wrapper)).not.toContain(en.tradeActions.cancelOrder)
    wrapper.unmount()
  }
})
it.each(sourceCases)('$module guards repeated clicks while the version read is pending', async entry => {
  const wrapper = await orderActionsEditor(entry)
  let resolve!: (value: trade.TradeDocument) => void
  vi.mocked(trade.getTradeOrderV2).mockReturnValue(new Promise(done => { resolve = done }))
  const button = actionButton(wrapper, en.tradeActions.confirmOrder)
  await button.trigger('click'); await button.trigger('click')
  expect(trade.getTradeOrderV2).toHaveBeenCalledTimes(1)
  expect(button.attributes('disabled')).toBeDefined()
  resolve({ id: 'order', version: 4 } as trade.TradeDocument); await flushPromises()
  expect(trade.changeTradeOrderStateV2).toHaveBeenCalledTimes(1)
  wrapper.unmount()
})
it.each(sourceCases)('$module refreshes and explains a concurrent state conflict', async entry => {
  const wrapper = await orderActionsEditor(entry)
  vi.mocked(trade.getTradeOrderV2).mockResolvedValue({ id: 'order', version: 4 } as trade.TradeDocument)
  vi.mocked(trade.changeTradeOrderStateV2).mockRejectedValueOnce({ isAxiosError: true, response: { status: 409 } })
  const message = vi.spyOn(ElMessage, 'error')
  await actionButton(wrapper, en.tradeActions.confirmOrder).trigger('click'); await flushPromises()
  expect(message).toHaveBeenCalledWith(en.tradeActions.conflict)
  expect(entry.module === 'sales' ? sales.listSalesOrders : procurement.listPurchaseOrders).toHaveBeenCalledTimes(2)
  message.mockRestore(); wrapper.unmount()
})
it.each(sourceCases)('$module hides confirm/cancel for fulfilled or cancelled orders', async entry => {
  const wrapper = await orderActionsEditor(entry)
  const base = { buyerUserId: 'buyer', lineId: 'line', supplierId: 'supplier', customerId: 'customer', itemId: 'item', warehouseId: 'warehouse', orderedQuantity: 3, unitPrice: 10, discountRate: 0, taxRate: 0, currencyCode: 'CNY', orderDate: '2026-10-03', totalAmount: 30, receivableAmount: 0, paidAmount: 0, outstandingAmount: 0 }
  const orders = [
    { ...base, id: 'one', number: 'ONE', status: 'CONFIRMED', fulfilledQuantity: 1, receivedQuantity: 1, remainingQuantity: 2 },
    { ...base, id: 'two', number: 'TWO', status: 'CANCELLED', fulfilledQuantity: 0, receivedQuantity: 0, remainingQuantity: 0 },
  ]
  vi.mocked(procurement.listPurchaseOrders).mockResolvedValue(page(orders as procurement.PurchaseOrder[]))
  vi.mocked(sales.listSalesOrders).mockResolvedValue(page(orders as sales.SalesOrder[]))
  await actionButton(wrapper, en[entry.module].refresh).trigger('click'); await flushPromises()
  expect(labels(wrapper)).not.toContain(en.tradeActions.confirmOrder)
  expect(labels(wrapper)).not.toContain(en.tradeActions.cancelOrder)
  wrapper.unmount()
})

it('sales outstanding fulfillment summary excludes cancelled orders with remaining quantity', async () => {
  vi.mocked(sales.listSalesOrders).mockResolvedValue(page([
    { id: 'cancelled', status: 'CANCELLED', remainingQuantity: 2 } as sales.SalesOrder,
    { id: 'draft', status: 'DRAFT', remainingQuantity: 1 } as sales.SalesOrder,
    { id: 'confirmed', status: 'CONFIRMED', remainingQuantity: 3 } as sales.SalesOrder,
    { id: 'fulfilled', status: 'FULFILLED', remainingQuantity: 0 } as sales.SalesOrder,
  ]))
  const wrapper = mount(SalesView, { global: global(['sales:view'], 'SELF') }); await flushPromises()
  expect(wrapper.find('.inventory-summary-grid strong').text()).toBe('2')
  wrapper.unmount()
})

it('defaults a manual sales order to organization currency and displays it', async()=>{
  const {wrapper,field}=await sourceEditor(sourceCases[1]);await field.find('select').setValue('');await flushPromises();
  expect(wrapper.text()).toContain('Document currency: CNY');await saveEditor(wrapper);
  expect(trade.createSalesOrderV2).toHaveBeenCalledWith(expect.objectContaining({currencyCode:'CNY'}));wrapper.unmount()
})
it('keeps a source quote currency then restores base currency when cleared or reopened', async()=>{
  const {wrapper,field,button}=await sourceEditor(sourceCases[1]);
  vi.mocked(sales.listSalesQuotes).mockResolvedValue(page([{id:'source-header',lineId:'source-line',number:'SRC-1',status:'APPROVED',customerId:'customer',itemId:'item',quantity:3,unitPrice:10,discountRate:0,taxRate:0,currencyCode:'EUR',sourceEligible:true} as sales.SalesQuote]));
  await wrapper.findAll('button').find(b=>b.text()===en.sales.refresh)!.trigger('click');await flushPromises();
  await field.find('select').setValue('');await field.find('select').setValue('source-header');await flushPromises();expect(wrapper.text()).toContain('Document currency: EUR');
  await wrapper.findAll('button').find(b=>b.text()===en.masterData.cancel)!.trigger('click');await flushPromises();await button.trigger('click');await flushPromises();
  expect(wrapper.text()).toContain('Document currency: CNY');wrapper.unmount()
})
it('blocks sales creation if currency settings cannot load', async()=>{
  vi.mocked(master.getOrganizationSettings).mockRejectedValue(new Error('offline'));
  const options=global(['sales:view','sales:create','master:view']);const wrapper=mount(SalesView,{global:options});await flushPromises();
  await wrapper.find('[id$="-orders"]').trigger('click');await flushPromises();
  const button=wrapper.findAll('button').find(b=>b.text()===en.sales.create)!;expect(button.attributes('disabled')).toBeDefined();await button.trigger('click');await flushPromises();
  expect(trade.createSalesOrderV2).not.toHaveBeenCalled();wrapper.unmount()
})
