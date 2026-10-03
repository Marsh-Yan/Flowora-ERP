import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { defineComponent, h } from 'vue'
import * as trade from '@/api/trade'
import { createI18n } from 'vue-i18n'
import ElementPlus, { ElMessage } from 'element-plus'
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
vi.mock('@/api/trade', () => ({ createPurchaseOrderV2: vi.fn(), createSalesOrderV2: vi.fn() }))
vi.mock('@/api/master-data', () => ({ listMasterData: vi.fn() }))
const page = <T>(content: T[]) => ({ content, page: 0, size: 50, totalElements: content.length, totalPages: 1 })
beforeEach(() => {
  vi.resetAllMocks()
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
async function sourceEditor(entry: typeof sourceCases[number]) {
  const source = { id: 'source-header', lineId: 'source-line', number: 'SRC-1', status: 'APPROVED', supplierId: 'supplier',
    customerId: 'customer', warehouseId: 'warehouse', itemId: 'item', quantity: 3, estimatedUnitCost: 10,
    unitPrice: 10, discountRate: 0, taxRate: 0, currencyCode: 'CNY', requesterUserId: 'user',
    validUntil: '2026-10-31', totalAmount: 30, note: '' }
  vi.mocked(procurement.listPurchaseRequests).mockResolvedValue(page([source as procurement.PurchaseRequest]))
  vi.mocked(sales.listSalesQuotes).mockResolvedValue(page([source as sales.SalesQuote]))
  const options = global([`${entry.module}:view`, `${entry.module}:create`, 'master:view'])
  const wrapper = mount(entry.view, { global: { ...options, stubs: { ...options.stubs, ElSelect: NativeSelect, ElOption: NativeOption } } })
  await flushPromises()
  await wrapper.find('[id$="-orders"]').trigger('click'); await flushPromises()
  const button = wrapper.findAll('button').find(b => b.text() === en[entry.module].create)!
  await button.trigger('click'); await flushPromises()
  const field = wrapper.findAll('.el-form-item').find(f => f.text().includes(entry.sourceLabel))!
  await field.find('select').setValue('source-header'); await flushPromises()
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
