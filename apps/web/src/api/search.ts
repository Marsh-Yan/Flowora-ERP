import { apiClient } from './http'

export interface SearchResult {
  type: 'CUSTOMER' | 'SALES_ORDER' | 'PROJECT'
  id: string
  title: string
  subtitle: string
  route: string
}

interface ApiEnvelope<T> {
  data: T
  requestId: string
}

export async function searchWorkspace(query: string) {
  const response = await apiClient.get<ApiEnvelope<{ results: SearchResult[] }>>('/v1/search', { params: { query } })
  return response.data.data.results
}
