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

export type JournalEntryStatus = 'POSTED' | 'REVERSED' | 'VOID'
export type PayableStatus = 'OPEN' | 'PARTIALLY_SETTLED' | 'SETTLED' | 'CANCELLED'
export type PaymentMethod = 'BANK' | 'CASH' | 'OTHER'

export interface JournalLine {
  lineNo: number
  accountCode: string
  description?: string
  debit: number
  credit: number
  currencyCode: string
}

export interface JournalEntry {
  id: string
  number: string
  periodId: string
  entryDate: string
  sourceType: string
  sourceId: string
  memo: string
  currencyCode: string
  totalDebit: number
  totalCredit: number
  status: JournalEntryStatus
  lines: JournalLine[]
}

export interface AccountingPeriod {
  id: string
  year: number
  month: number
  startDate: string
  endDate: string
  status: 'OPEN' | 'CLOSED'
}

export interface TrialBalanceRow {
  accountCode: string
  accountName: string
  accountType: string
  debit: number
  credit: number
  balance: number
}

export interface TrialBalanceResponse {
  from: string
  to: string
  rows: TrialBalanceRow[]
  totalDebit: number
  totalCredit: number
}

export interface StatementRow {
  accountCode: string
  accountName: string
  accountType: string
  amount: number
}

export interface FinancialStatementResponse {
  from: string
  to: string
  rows: StatementRow[]
  total: number
}

export interface AgingRow {
  documentType: string
  number: string
  partyId: string
  dueDate: string
  outstandingAmount: number
  daysOverdue: number
  bucket: string
}

export interface Payable {
  id: string
  number: string
  purchaseReceiptId?: string
  supplierId: string
  sourceType: string
  currencyCode: string
  totalAmount: number
  paidAmount: number
  outstandingAmount: number
  status: PayableStatus
  dueDate: string
}

export interface SupplierPayment {
  id: string
  number: string
  payableId: string
  supplierId: string
  amount: number
  currencyCode: string
  method: PaymentMethod
  paymentDate: string
  reference?: string
}

export interface ManualJournalInput {
  entryDate: string
  memo: string
  currencyCode: string
  lines: Array<{ accountCode: string; description?: string; debit: number; credit: number }>
}

export async function listJournalEntries(from?: string, to?: string, page = 0, size = 30) {
  const response = await apiClient.get<ApiEnvelope<PageResponse<JournalEntry>>>('/v2/compat/finance/journals', { params: { from, to, page, size } })
  return response.data.data
}

export async function createManualJournal(payload: ManualJournalInput) {
  const response = await apiClient.post<ApiEnvelope<JournalEntry>>('/v2/compat/finance/journals/manual', payload)
  return response.data.data
}

export async function listAccountingPeriods(page = 0, size = 24) {
  const response = await apiClient.get<ApiEnvelope<PageResponse<AccountingPeriod>>>('/v2/compat/finance/periods', { params: { page, size } })
  return response.data.data
}

export async function closeAccountingPeriod(id: string) {
  const response = await apiClient.post<ApiEnvelope<AccountingPeriod>>(`/v2/compat/finance/periods/${id}/close`)
  return response.data.data
}

export async function getTrialBalance(from?: string, to?: string) {
  const response = await apiClient.get<ApiEnvelope<TrialBalanceResponse>>('/v2/compat/finance/reports/trial-balance', { params: { from, to } })
  return response.data.data
}

export async function getIncomeStatement(from?: string, to?: string) {
  const response = await apiClient.get<ApiEnvelope<FinancialStatementResponse>>('/v2/compat/finance/reports/income-statement', { params: { from, to } })
  return response.data.data
}

export async function getBalanceSheet(from?: string, to?: string) {
  const response = await apiClient.get<ApiEnvelope<FinancialStatementResponse>>('/v2/compat/finance/reports/balance-sheet', { params: { from, to } })
  return response.data.data
}

export async function listPayables(query = '', page = 0, size = 30) {
  const response = await apiClient.get<ApiEnvelope<PageResponse<Payable>>>('/v2/compat/finance/payables', { params: { query, page, size } })
  return response.data.data
}

export async function listSupplierPayments(payableId: string, page = 0, size = 20) {
  const response = await apiClient.get<ApiEnvelope<PageResponse<SupplierPayment>>>(`/v2/compat/finance/payables/${payableId}/payments`, { params: { page, size } })
  return response.data.data
}

export async function createSupplierPayment(payload: { payableId: string; amount: number; method: PaymentMethod; paymentDate: string; reference?: string }) {
  const response = await apiClient.post<ApiEnvelope<SupplierPayment>>('/v2/compat/finance/supplier-payments', payload)
  return response.data.data
}

export async function getReceivableAging(asOf?: string) {
  const response = await apiClient.get<ApiEnvelope<AgingRow[]>>('/v2/compat/finance/reports/aging/receivables', { params: { asOf } })
  return response.data.data
}

export async function getPayableAging(asOf?: string) {
  const response = await apiClient.get<ApiEnvelope<AgingRow[]>>('/v2/compat/finance/reports/aging/payables', { params: { asOf } })
  return response.data.data
}
