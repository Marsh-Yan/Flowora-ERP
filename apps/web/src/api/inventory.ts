import { apiClient } from './http'

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

interface ApiEnvelope<T> {
  data: T
  requestId: string
}

export type MovementType = 'RECEIPT' | 'SHIPMENT' | 'TRANSFER_IN' | 'TRANSFER_OUT' | 'COUNT' | 'ADJUSTMENT' | 'OPENING' | 'SALES_RETURN' | 'PURCHASE_RETURN'
export type AdjustmentStatus = 'DRAFT' | 'PENDING_APPROVAL' | 'POSTED' | 'REJECTED' | 'CANCELLED'

export interface StockBalance {
  id: string
  warehouseId: string
  itemId: string
  quantity: number
  averageCost: number
  inventoryValue: number
}

export interface StockSummary {
  inventoryValue: number
  balanceCount: number
  ledgerCount: number
}

export async function getStockSummary() {
  const response = await apiClient.get<ApiEnvelope<StockSummary>>('/v2/compat/inventory/summary')
  return response.data.data
}

export interface StockLedgerEntry {
  id: string
  warehouseId: string
  itemId: string
  movementType: MovementType
  documentType: string
  documentId: string
  quantityDelta: number
  unitCost: number
  valueDelta: number
  balanceQuantity: number
  balanceValue: number
  actorUserId: string
  createdAt: string
}

export interface StockAdjustment {
  id: string
  number: string
  warehouseId: string
  itemId: string
  quantityDelta: number
  unitCost: number
  reason: string
  status: AdjustmentStatus
  workflowTaskId?: string
  postedAt?: string
}

export async function listStockBalances(warehouseId = '', page = 0, size = 50) {
  const response = await apiClient.get<ApiEnvelope<PageResponse<StockBalance>>>('/v2/compat/inventory/balances', { params: { warehouseId, page, size } })
  return response.data.data
}

export async function listStockLedger(warehouseId = '', itemId = '', page = 0, size = 50) {
  const response = await apiClient.get<ApiEnvelope<PageResponse<StockLedgerEntry>>>('/v2/compat/inventory/ledger', { params: { warehouseId, itemId, page, size } })
  return response.data.data
}

export async function receivePurchaseOrder(payload: { purchaseOrderId: string; purchaseOrderLineId: string; warehouseId: string; quantity: number; unitCost: number }) {
  const response = await apiClient.post<ApiEnvelope<unknown>>('/v2/compat/inventory/receipts', payload)
  return response.data.data
}

export async function transferStock(payload: { sourceWarehouseId: string; targetWarehouseId: string; itemId: string; quantity: number; unitCost: number }) {
  const response = await apiClient.post<ApiEnvelope<unknown>>('/v2/compat/inventory/transfers', payload)
  return response.data.data
}

export async function createStockAdjustment(payload: { warehouseId: string; itemId: string; quantityDelta: number; unitCost: number; reason: string }) {
  const response = await apiClient.post<ApiEnvelope<StockAdjustment>>('/v2/compat/inventory/adjustments', payload)
  return response.data.data
}
