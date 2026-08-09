import { apiClient } from './http'

export interface DemoDataStatus {
  enabled: boolean
  customers: number
  suppliers: number
  items: number
  warehouses: number
  purchaseOrders: number
  receipts: number
  salesOrders: number
  deliveries: number
  journalEntries: number
  projects: number
  lastResetAt: string | null
  resetCount: number
}

interface ApiResponse<T> {
  data: T
  requestId: string
}

export async function getDemoStatus() {
  const response = await apiClient.get<ApiResponse<DemoDataStatus>>('/v1/demo/status')
  return response.data.data
}

export async function resetDemoData() {
  const response = await apiClient.post<ApiResponse<DemoDataStatus>>('/v1/demo/reset')
  return response.data.data
}
