import { apiClient } from './http'

interface ApiEnvelope<T> { data: T; requestId: string }

export interface FinanceDashboard {
  receivables: number; payables: number; cash: number; revenue: number; expense: number
  netIncome: number; trialDebit: number; trialCredit: number; unmatchedBankLines: number; matchExceptions: number
}

export interface FinanceInvoice {
  id: string; number: string; documentType: string; partyType: string; partyId: string; projectId?: string
  status: string; settlementStatus: string; currencyCode: string; totalAmount: number; allocatedAmount: number; creditedAmount: number
  accountingDate: string; dueDate: string; matchStatus: string; version: number
}

export interface FinanceInvoiceDetail extends FinanceInvoice {
  matchExceptionApprovedBy?: string; matchExceptionReason?: string
  exchangeRate?: number; originalInvoiceId?: string; projectId?: string
  lines: Array<{ id: string; lineNo: number; description: string; quantity: number; unitPrice: number; discountRate?: number; taxRate: number; creditedQuantity?: number; itemId?: string; accountCode?: string; reversalAccountCode?: string; projectId?: string; matchQuantityVariance: number; matchPriceVarianceRate: number; matchTaxVariance: number; sources: Array<{ sourceType: string; sourceId: string; sourceLineId?: string; quantity: number; amount: number }> }>
}
export async function getFinanceInvoice(id: string) {
  const response = await apiClient.get<ApiEnvelope<FinanceInvoiceDetail>>(`/v2/finance/invoices/${id}`)
  return response.data.data
}
export async function approveFinanceMatchException(id: string, reason: string) {
  const response = await apiClient.post<ApiEnvelope<FinanceInvoiceDetail>>(`/v2/finance/invoices/${id}/match-exception/approve`, { reason })
  return response.data.data
}

export interface FinancePayment {
  id: string; number: string; paymentType: string; partyType: string; partyId: string; bankAccountId?: string; status: string
  allocationStatus: string; accountingDate: string; currencyCode: string; amount: number; allocatedAmount: number; version: number; allocations: FinanceAllocation[]
}

export interface BankStatementLine {
  id: string; bankAccountId: string; transactionDate: string; amount: number; currencyCode: string; externalReference: string
  counterparty?: string; description?: string; reconciliationStatus: string
}

export interface InvoiceInput {
  documentType: 'SALES_INVOICE' | 'SUPPLIER_INVOICE' | 'CUSTOMER_CREDIT' | 'SUPPLIER_CREDIT'; originalInvoiceId?: string; projectId?: string; partyId: string; businessDate: string; accountingDate: string
  dueDate: string; exchangeRateDate: string; currencyCode: string; exchangeRate: number
  lines: Array<{ itemId?: string; description: string; quantity: number; unitPrice: number; discountRate: number; taxRate: number; accountCode?: string; projectId?: string; sources?: Array<{ sourceType: string; sourceId: string; sourceLineId: string; quantity: number; amount: number }> }>
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

export async function createFinanceInvoice(payload: InvoiceInput, key: string = crypto.randomUUID()) {
  const response = await apiClient.post<ApiEnvelope<FinanceInvoice>>('/v2/finance/invoices', payload, { headers: { 'Idempotency-Key': key } })
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

export interface FinanceAllocation {
  id: string; paymentId: string; invoiceId: string; amount: number; baseAmount: number
  realizedExchangeDifference: number; status: 'ACTIVE' | 'REVERSED'; allocatedAt: string; reversedAt?: string
}

export async function allocateFinancePayment(paymentId: string, invoiceId: string, amount: number) {
  const response = await apiClient.post<ApiEnvelope<FinanceAllocation>>(`/v2/finance/payments/${paymentId}/allocations`, { invoiceId, amount })
  return response.data.data
}

export async function reverseFinanceAllocation(id: string, reason: string, key: string) {
  const response = await apiClient.post<ApiEnvelope<FinanceAllocation>>(`/v2/finance/allocations/${id}/reverse`, { reason }, { headers: { 'Idempotency-Key': key } })
  return response.data.data
}

export interface FinanceBankAccount { id: string; code: string; name: string; currencyCode: string }
export interface BankStatementInput { bankAccountId: string; lines: Array<{ transactionDate: string; amount: number; currencyCode: string; externalReference: string }> }
export async function listFinanceBankAccounts() {
  const response = await apiClient.get<ApiEnvelope<FinanceBankAccount[]>>('/v2/finance/bank/accounts')
  return response.data.data
}
export async function importBankStatement(payload: BankStatementInput) {
  const response = await apiClient.post<ApiEnvelope<BankStatementLine[]>>('/v2/finance/bank/statements/import', payload)
  return response.data.data
}

export interface BankReconciliation {
  id: string; number: string; bankAccountId: string; status: 'CONFIRMED' | 'REVERSED'
  totalStatementAmount: number; totalPaymentAmount: number; differenceAmount: number
  links: Array<{ statementLineId: string; paymentId: string; matchedAmount: number }>
}
export async function listBankReconciliations() {
  const response = await apiClient.get<ApiEnvelope<BankReconciliation[]>>('/v2/finance/bank/reconciliations')
  return response.data.data
}
export async function reconcileBankStatement(bankAccountId: string, statementLineId: string, paymentId: string, matchedAmount: number, key: string) {
  const response = await apiClient.post<ApiEnvelope<BankReconciliation>>('/v2/finance/bank/reconciliations', { bankAccountId, links: [{ statementLineId, paymentId, matchedAmount }] }, { headers: { 'Idempotency-Key': key } })
  return response.data.data
}
export async function reverseBankReconciliation(id: string, reason: string) {
  const response = await apiClient.post<ApiEnvelope<BankReconciliation>>(`/v2/finance/bank/reconciliations/${id}/reverse`, { reason })
  return response.data.data
}

export interface StockInvoiceSource {
  documentType: 'SALES_INVOICE' | 'SUPPLIER_INVOICE'; sourceType: string; sourceId: string; sourceLineId: string
  sourceNumber: string; orderNumber: string; partyId: string; partyName: string; itemId: string; description: string
  currencyCode: string; quantity: number; remainingQuantity: number; unitPrice: number; discountRate: number; taxRate: number
}
export async function listStockInvoiceSources() {
  const response = await apiClient.get<ApiEnvelope<StockInvoiceSource[]>>('/v2/finance/invoice-stock-sources')
  return response.data.data
}
