import { apiClient } from './http'

export type MasterDataResource =
  | 'customers'
  | 'suppliers'
  | 'items'
  | 'warehouses'
  | 'currencies'
  | 'exchange-rates'
  | 'tax-rates'
  | 'accounts'

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface ApiEnvelope<T> {
  data: T
  requestId: string
}

export interface MasterDataRecord {
  id: string
  code: string
  name?: string
  active: boolean
  [key: string]: unknown
}

export interface OrganizationSettings {
  id: string
  name: string
  baseCurrencyCode: string
  timezone: string
  approvalThreshold: number
  defaultTaxRate: number
  active: boolean
}

export interface ImportResult {
  imported: number
  rejected: number
  errors: Array<{ row: number; message: string }>
}

export async function listMasterData<T extends MasterDataRecord>(resource: MasterDataResource, query: string, page: number, size = 10) {
  const response = await apiClient.get<ApiEnvelope<PageResponse<T>>>(`/v1/masters/${resource}`, {
    params: { query, page, size },
  })
  return response.data.data
}

export async function saveMasterData<T extends MasterDataRecord>(resource: MasterDataResource, payload: Partial<T>, id?: string) {
  const response = id
    ? await apiClient.put<ApiEnvelope<T>>(`/v1/masters/${resource}/${id}`, payload)
    : await apiClient.post<ApiEnvelope<T>>(`/v1/masters/${resource}`, payload)
  return response.data.data
}

export async function deactivateMasterData(resource: MasterDataResource, id: string) {
  await apiClient.delete(`/v1/masters/${resource}/${id}`)
}

export async function importMasterData(resource: MasterDataResource, file: File) {
  const form = new FormData()
  form.append('file', file)
  const response = await apiClient.post<ApiEnvelope<ImportResult>>(`/v1/masters/${resource}/import`, form)
  return response.data.data
}

export async function getOrganizationSettings() {
  const response = await apiClient.get<ApiEnvelope<OrganizationSettings>>('/v1/organizations/settings')
  return response.data.data
}

export async function saveOrganizationSettings(payload: Omit<OrganizationSettings, 'id' | 'active'>) {
  const response = await apiClient.put<ApiEnvelope<OrganizationSettings>>('/v1/organizations/settings', payload)
  return response.data.data
}
