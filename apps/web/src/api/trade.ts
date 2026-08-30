import { apiClient } from './http'

interface ApiEnvelope<T> {
  data: T
  requestId: string
}

export interface TradeLineInput {
  itemId: string
  quantity: number
  unitPrice: number
  discountRate: number
  taxRate: number
  sourceDocumentType?: string
  sourceDocumentId?: string
  sourceLineId?: string
}

export interface TradeDocumentLine {
  id: string
  itemId: string
  orderedQuantity: number
  fulfilledQuantity: number
  reservedQuantity: number
  returnedQuantity: number
  unitPrice: number
  discountRate: number
  taxRate: number
  netAmount: number
  taxAmount: number
  grossAmount: number
}

export interface TradeDocument {
  id: string
  number: string
  documentType: 'SALES_ORDER' | 'PURCHASE_ORDER'
  partnerId: string
  warehouseId: string
  status: string
  currencyCode: string
  documentDate: string
  dueDate?: string
  totalAmount: number
  note?: string
  version: number
  createdAt: string
  lines: TradeDocumentLine[]
}

export interface Availability {
  warehouseId: string
  locationId?: string
  itemId: string
  lotId?: string
  serialId?: string
  onHand: number
  reserved: number
  available: number
  averageCost: number
  frozen: boolean
}

export interface MovementLine {
  id: string
  sequence: number
  itemId: string
  fromWarehouseId?: string
  fromLocationId?: string
  toWarehouseId?: string
  toLocationId?: string
  lotId?: string
  serialId?: string
  quantity: number
  unitCost: number
  valueAmount: number
  sourceLineType?: string
  sourceLineId?: string
}

export interface Movement {
  id: string
  number: string
  movementType: string
  sourceType: string
  sourceId: string
  reversalOfId?: string
  requestId: string
  postedAt: string
  lines: MovementLine[]
}

export interface TraceResult {
  itemId: string
  lotId?: string
  serialId?: string
  movements: Movement[]
}

export interface FinancialSourceEvent {
  id: string
  eventType: string
  sourceType: string
  sourceId: string
  currency: string
  quantity: number
  amount: number
  status: string
  occurredAt: string
}

function requestKey(prefix: string) {
  return `${prefix}-${crypto.randomUUID()}`
}

export async function createSalesOrderV2(payload: { customerId: string; warehouseId: string; currencyCode: string; dueDate?: string; note?: string; lines: TradeLineInput[] }) {
  const response = await apiClient.post<ApiEnvelope<TradeDocument>>('/v2/sales/orders', payload, { headers: { 'Idempotency-Key': requestKey('sales-order') } })
  return response.data.data
}

export async function createPurchaseOrderV2(payload: { supplierId: string; warehouseId: string; currencyCode: string; expectedDate?: string; note?: string; lines: TradeLineInput[] }) {
  const response = await apiClient.post<ApiEnvelope<TradeDocument>>('/v2/procurement/orders', payload, { headers: { 'Idempotency-Key': requestKey('purchase-order') } })
  return response.data.data
}

export async function listAvailability(warehouseId = '', itemId = '') {
  const response = await apiClient.get<ApiEnvelope<Availability[]>>('/v2/inventory/availability', { params: { warehouseId, itemId } })
  return response.data.data
}

export async function traceInventory(itemId: string, lotId = '', serialId = '') {
  const response = await apiClient.get<ApiEnvelope<TraceResult>>('/v2/inventory/trace', { params: { itemId, lotId, serialId } })
  return response.data.data
}

export async function listFinancialSourceEvents(status = 'PENDING_FINANCE') {
  const response = await apiClient.get<ApiEnvelope<FinancialSourceEvent[]>>('/v2/trade/financial-source-events', { params: { status } })
  return response.data.data
}
