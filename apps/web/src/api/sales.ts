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

export type QuoteStatus = 'DRAFT' | 'SUBMITTED' | 'APPROVED' | 'REJECTED' | 'CONVERTED' | 'EXPIRED' | 'CANCELLED'
export type OrderStatus = 'DRAFT' | 'CONFIRMED' | 'PARTIALLY_FULFILLED' | 'FULFILLED' | 'PARTIALLY_SETTLED' | 'SETTLED' | 'CANCELLED'
export type ReceivableStatus = 'OPEN' | 'PARTIALLY_SETTLED' | 'SETTLED' | 'CANCELLED'

export interface SalesQuote {
  id: string
  number: string
  status: QuoteStatus
  workflowTaskId?: string
  customerId: string
  itemId: string
  quantity: number
  unitPrice: number
  discountRate: number
  taxRate: number
  currencyCode: string
  validUntil: string
  totalAmount: number
  note?: string
  approvedAt?: string
}

export interface SalesOrder {
  id: string
  number: string
  status: OrderStatus
  quoteId?: string
  customerId: string
  warehouseId: string
  lineId: string
  itemId: string
  orderedQuantity: number
  fulfilledQuantity: number
  remainingQuantity: number
  unitPrice: number
  discountRate: number
  taxRate: number
  currencyCode: string
  orderDate: string
  dueDate?: string
  totalAmount: number
  receivableAmount: number
  paidAmount: number
  outstandingAmount: number
  note?: string
}

export interface Delivery {
  id: string
  number: string
  salesOrderId: string
  warehouseId: string
  itemId: string
  quantity: number
  unitCost: number
  status: 'POSTED' | 'CANCELLED'
  postedAt: string
}

export interface Receivable {
  id: string
  number: string
  salesOrderId: string
  customerId: string
  sourceType: string
  currencyCode: string
  totalAmount: number
  paidAmount: number
  outstandingAmount: number
  status: ReceivableStatus
  dueDate: string
}

export interface Payment {
  id: string
  number: string
  receivableId: string
  customerId: string
  amount: number
  currencyCode: string
  method: 'BANK' | 'CASH' | 'OTHER'
  paymentDate: string
  reference?: string
}

export async function listSalesQuotes(query = '', page = 0, size = 50) {
  const response = await apiClient.get<ApiEnvelope<PageResponse<SalesQuote>>>('/v1/sales/quotes', { params: { query, page, size } })
  return response.data.data
}

export async function createSalesQuote(payload: { customerId: string; itemId: string; quantity: number; unitPrice: number; discountRate: number; taxRate: number; currencyCode: string; validUntil: string; note?: string }) {
  const response = await apiClient.post<ApiEnvelope<SalesQuote>>('/v1/sales/quotes', payload)
  return response.data.data
}

export async function approveSalesQuote(id: string) {
  const response = await apiClient.post<ApiEnvelope<SalesQuote>>(`/v1/sales/quotes/${id}/approve`)
  return response.data.data
}

export async function listSalesOrders(query = '', page = 0, size = 50) {
  const response = await apiClient.get<ApiEnvelope<PageResponse<SalesOrder>>>('/v1/sales/orders', { params: { query, page, size } })
  return response.data.data
}

export async function createSalesOrder(payload: { quoteId?: string; customerId: string; warehouseId: string; itemId: string; quantity: number; unitPrice: number; discountRate: number; taxRate: number; currencyCode: string; dueDate?: string; note?: string }) {
  const response = await apiClient.post<ApiEnvelope<SalesOrder>>('/v1/sales/orders', payload)
  return response.data.data
}

export async function createDelivery(payload: { salesOrderId: string; salesOrderLineId: string; warehouseId: string; quantity: number }) {
  const response = await apiClient.post<ApiEnvelope<Delivery>>('/v1/sales/deliveries', payload)
  return response.data.data
}

export async function listReceivables(query = '', page = 0, size = 50) {
  const response = await apiClient.get<ApiEnvelope<PageResponse<Receivable>>>('/v1/sales/receivables', { params: { query, page, size } })
  return response.data.data
}

export async function createPayment(payload: { receivableId: string; amount: number; method: Payment['method']; paymentDate: string; reference?: string }) {
  const response = await apiClient.post<ApiEnvelope<Payment>>('/v1/sales/payments', payload)
  return response.data.data
}
