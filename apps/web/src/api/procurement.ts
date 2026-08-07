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

export type ProcurementStatus = 'DRAFT' | 'SUBMITTED' | 'APPROVED' | 'PARTIALLY_RECEIVED' | 'RECEIVED' | 'REJECTED' | 'CANCELLED'

export interface PurchaseRequest {
  id: string
  number: string
  status: ProcurementStatus
  supplierId: string
  warehouseId: string
  requesterUserId: string
  itemId: string
  quantity: number
  estimatedUnitCost: number
  note?: string
}

export interface PurchaseOrder {
  id: string
  number: string
  status: ProcurementStatus
  purchaseRequestId?: string
  supplierId: string
  warehouseId: string
  buyerUserId: string
  lineId?: string
  itemId: string
  orderedQuantity: number
  receivedQuantity: number
  remainingQuantity: number
  unitPrice: number
  taxRate: number
  orderDate: string
  expectedDate?: string
  note?: string
}

export interface PurchaseRequestInput {
  supplierId: string
  warehouseId: string
  itemId: string
  quantity: number
  estimatedUnitCost: number
  note?: string
}

export interface PurchaseOrderInput {
  purchaseRequestId?: string
  supplierId: string
  warehouseId: string
  itemId: string
  quantity: number
  unitPrice: number
  taxRate: number
  expectedDate?: string
  note?: string
}

export async function listPurchaseRequests(query = '', page = 0, size = 20) {
  const response = await apiClient.get<ApiEnvelope<PageResponse<PurchaseRequest>>>('/v1/procurement/requests', { params: { query, page, size } })
  return response.data.data
}

export async function createPurchaseRequest(payload: PurchaseRequestInput) {
  const response = await apiClient.post<ApiEnvelope<PurchaseRequest>>('/v1/procurement/requests', payload)
  return response.data.data
}

export async function listPurchaseOrders(query = '', page = 0, size = 20) {
  const response = await apiClient.get<ApiEnvelope<PageResponse<PurchaseOrder>>>('/v1/procurement/orders', { params: { query, page, size } })
  return response.data.data
}

export async function createPurchaseOrder(payload: PurchaseOrderInput) {
  const response = await apiClient.post<ApiEnvelope<PurchaseOrder>>('/v1/procurement/orders', payload)
  return response.data.data
}

export async function cancelPurchaseOrder(id: string) {
  await apiClient.delete(`/v1/procurement/orders/${id}`)
}
