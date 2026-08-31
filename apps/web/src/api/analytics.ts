import { apiClient } from './http'

interface ApiEnvelope<T> { data: T; requestId: string }

export interface WorkspaceCard {
  code: string
  value: number
  currencyCode?: string
  route: string
  requiredPermission?: string
  severity: string
}
export interface WorkspaceSnapshot {
  organizationId: string
  roles: string[]
  refreshedAt: string
  cards: WorkspaceCard[]
  risks: string[]
}
export interface TrendPoint {
  period: string
  sales: number
  purchases: number
  revenue: number
  expense: number
  grossProfit: number
}
export interface AnalyticsSnapshot {
  from: string
  to: string
  currencyCode: string
  trends: TrendPoint[]
  refreshedAt: string
}
export interface OrganizationSummary {
  organizationId: string
  organizationName: string
  sourceCurrencyCode: string
  reportCurrencyCode: string
  exchangeRateDate?: string
  exchangeRateMissing: boolean
  sales: number
  receivables: number
  payables: number
  cash: number
  includesEliminations: boolean
}
export interface SavedView {
  id: string
  resourceType: string
  name: string
  definition: Record<string, unknown>
  shared: boolean
  ownerUserId: string
  updatedAt: string
  version: number
}
export interface ExportJob {
  id: string
  resourceType: string
  format: string
  status: string
  rowCount: number
  resultFilename?: string
  errorCode?: string
  expiresAt?: string
  createdAt: string
  completedAt?: string
}
export interface DiagnosticSnapshot {
  productVersion: string
  deliveryStage: string
  migrationVersion: string
  dependencies: Record<string, { status: string; details: Record<string, unknown> }>
  taskBacklog: Record<string, number>
  generatedAt: string
}

export async function getWorkspace() {
  return (await apiClient.get<ApiEnvelope<WorkspaceSnapshot>>('/v2/analytics/workspace')).data.data
}
export async function getAnalytics(from: string, to: string) {
  return (await apiClient.get<ApiEnvelope<AnalyticsSnapshot>>('/v2/analytics/snapshot', { params: { from, to } })).data.data
}
export async function getOrganizationAnalytics(reportCurrency: string) {
  return (await apiClient.get<ApiEnvelope<OrganizationSummary[]>>('/v2/analytics/organizations', { params: { reportCurrency } })).data.data
}
export async function getSavedViews(resourceType = 'ANALYTICS') {
  return (await apiClient.get<ApiEnvelope<SavedView[]>>('/v2/analytics/views', { params: { resourceType } })).data.data
}
export async function saveView(name: string, definition: Record<string, unknown>, shared = false) {
  return (await apiClient.post<ApiEnvelope<SavedView>>('/v2/analytics/views', { resourceType: 'ANALYTICS', name, definition, shared })).data.data
}
export async function removeView(id: string) { await apiClient.delete(`/v2/analytics/views/${id}`) }
export async function createExport(resourceType: string, filters: Record<string, unknown>) {
  return (await apiClient.post<ApiEnvelope<ExportJob>>('/v2/exports', { resourceType, filters, locale: 'zh-CN' })).data.data
}
export async function getExports() { return (await apiClient.get<ApiEnvelope<ExportJob[]>>('/v2/exports')).data.data }
export async function downloadExport(id: string, filename: string) {
  const response = await apiClient.get<Blob>(`/v2/exports/${id}/download`, { responseType: 'blob' })
  const url = URL.createObjectURL(response.data)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename
  anchor.click()
  URL.revokeObjectURL(url)
}
export async function getDiagnostics() {
  return (await apiClient.get<ApiEnvelope<DiagnosticSnapshot>>('/v2/admin/diagnostics')).data.data
}
