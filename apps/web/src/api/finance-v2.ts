import { apiClient } from './http'

interface ApiEnvelope<T> { data: T; requestId: string }

export interface FinanceDashboard {
  receivables: number; payables: number; cash: number; revenue: number; expense: number
  netIncome: number; trialDebit: number; trialCredit: number; unmatchedBankLines: number; matchExceptions: number
}

export interface FinanceInvoice {
  id: string; number: string; documentType: string; partyType: string; partyId: string; projectId?: string
  status: string; settlementStatus: string; currencyCode: string; totalAmount: number; allocatedAmount: number
  accountingDate: string; dueDate: string; matchStatus: string; version: number
}

export interface FinancePayment {
  id: string; number: string; paymentType: string; partyType: string; partyId: string; status: string
  allocationStatus: string; accountingDate: string; currencyCode: string; amount: number; allocatedAmount: number; version: number
}

export interface BankStatementLine {
  id: string; transactionDate: string; amount: number; currencyCode: string; externalReference: string
  counterparty?: string; description?: string; reconciliationStatus: string
}

export interface InvoiceInput {
  documentType: 'SALES_INVOICE' | 'SUPPLIER_INVOICE'; partyId: string; businessDate: string; accountingDate: string
  dueDate: string; exchangeRateDate: string; currencyCode: string; exchangeRate: number
  lines: Array<{ itemId?: string; description: string; quantity: number; unitPrice: number; discountRate: number; taxRate: number; accountCode?: string; projectId?: string; sources?: never[] }>
}

export interface PaymentInput {
  paymentType: 'RECEIPT' | 'PAYMENT'; partyId: string; bankAccountId?: string; businessDate: string
  accountingDate: string; exchangeRateDate: string; currencyCode: string; exchangeRate: number; amount: number; reference?: string
}

export async function getFinanceDashboard(from: string, to: string) {
  const response = await apiClient.get<ApiEnvelope<FinanceDashboard>>('/v2/finance/dashboard', { params: { from, to } })
  return response.data.data
}

export async function listFinanceInvoices() {
  const response = await apiClient.get<ApiEnvelope<FinanceInvoice[]>>('/v2/finance/invoices')
  return response.data.data
}

export async function createFinanceInvoice(payload: InvoiceInput) {
  const response = await apiClient.post<ApiEnvelope<FinanceInvoice>>('/v2/finance/invoices', payload, { headers: { 'Idempotency-Key': crypto.randomUUID() } })
  return response.data.data
}

export async function postFinanceInvoice(invoice: FinanceInvoice) {
  const response = await apiClient.post<ApiEnvelope<FinanceInvoice>>(`/v2/finance/invoices/${invoice.id}/post`, undefined, { params: { version: invoice.version } })
  return response.data.data
}

export async function listFinancePayments() {
  const response = await apiClient.get<ApiEnvelope<FinancePayment[]>>('/v2/finance/payments')
  return response.data.data
}

export async function createFinancePayment(payload: PaymentInput) {
  const response = await apiClient.post<ApiEnvelope<FinancePayment>>('/v2/finance/payments', payload, { headers: { 'Idempotency-Key': crypto.randomUUID() } })
  return response.data.data
}

export async function postFinancePayment(payment: FinancePayment) {
  const response = await apiClient.post<ApiEnvelope<FinancePayment>>(`/v2/finance/payments/${payment.id}/post`, undefined, { params: { version: payment.version } })
  return response.data.data
}

export async function listBankStatementLines() {
  const response = await apiClient.get<ApiEnvelope<BankStatementLine[]>>('/v2/finance/bank/statements')
  return response.data.data
}
