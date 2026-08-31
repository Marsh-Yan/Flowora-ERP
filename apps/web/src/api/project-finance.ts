import { apiClient } from './http'
import type { FinanceInvoice } from './finance-v2'

interface ApiEnvelope<T> { data: T; requestId: string }

export interface ProjectBillingBasis {
  id: string; basisType: string; description: string; availableAmount: number; billedAmount: number
  remainingAmount: number; currencyCode: string; status: string
}

export interface ProjectProfit {
  projectId: string; currencyCode: string; contractAmount: number; availableBilling: number; billedAmount: number
  postedRevenue: number; postedCost: number; managementLaborCost: number; grossProfit: number
  budgetRevenueVariance: number; budgetCostVariance: number; pendingApprovals: number; unsettledInvoices: number; closeEligible: boolean
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

export async function generateProjectInvoice(projectId: string, basis: ProjectBillingBasis, date: string) {
  const payload = {
    businessDate: date, accountingDate: date, dueDate: date, exchangeRateDate: date,
    currencyCode: basis.currencyCode, exchangeRate: 1,
    lines: [{ billingBasisId: basis.id, amount: basis.remainingAmount, taxRate: 0 }],
  }
  const response = await apiClient.post<ApiEnvelope<FinanceInvoice>>(`/v2/projects/${projectId}/invoices`, payload, { headers: { 'Idempotency-Key': crypto.randomUUID() } })
  return response.data.data
}
