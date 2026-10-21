import { apiClient } from './http'
import type { FinanceInvoice } from './finance-v2'

interface ApiEnvelope<T> { data: T; requestId: string }

export interface ProjectBillingBasis {
  id: string; basisType: string; description: string; availableAmount: number; billedAmount: number
  remainingAmount: number; currencyCode: string; status: string
}

export interface ProjectProfit {
  projectId: string; currencyCode: string; contractAmount: number; availableBilling: number; billedAmount: number
  baseCurrencyCode: string; managementLaborCosts: Array<{ currencyCode: string; amount: number }>
  postedRevenue: number; postedCost: number; managementLaborCost: number | null; grossProfit: number
  budgetRevenueVariance: number | null; budgetCostVariance: number | null; pendingApprovals: number; unsettledInvoices: number; closeEligible: boolean
}

export async function getProjectProfitability(projectId: string) {
  const response = await apiClient.get<ApiEnvelope<ProjectProfit>>(`/v2/projects/${projectId}/profitability`)
  return response.data.data
}

export async function listProjectBillingBasisV2(projectId: string) {
  const response = await apiClient.get<ApiEnvelope<ProjectBillingBasis[]>>(`/v2/projects/${projectId}/billing-basis`)
  return response.data.data
}

export async function configureProjectBilling(projectId: string, payload: { billingMode: string; contractAmount: number }) {
  const response = await apiClient.post<ApiEnvelope<{ billingMode: string; contractAmount: number }>>(`/v2/projects/${projectId}/billing-configuration`, payload)
  return response.data.data
}

export interface ProjectInvoiceInput {
  businessDate: string; accountingDate: string; dueDate: string; exchangeRateDate: string
  currencyCode: string; exchangeRate: number
  lines: Array<{ billingBasisId: string; amount: number; taxRate: number }>
}

export async function generateProjectInvoice(projectId: string, payload: ProjectInvoiceInput, requestKey: string) {
  const response = await apiClient.post<ApiEnvelope<FinanceInvoice>>(`/v2/projects/${projectId}/invoices`, payload, { headers: { 'Idempotency-Key': requestKey } })
  return response.data.data
}
