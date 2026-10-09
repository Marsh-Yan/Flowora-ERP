<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { getOrganizationSettings, listMasterData, type MasterDataRecord } from '@/api/master-data'
import {
  createFinanceInvoice, createFinancePayment, getFinanceDashboard, listBankStatementLines,
  listFinanceInvoices, listFinancePayments, postFinanceInvoice, postFinancePayment, listFinanceBankAccounts,
  type FinanceBankAccount, type BankStatementLine, type FinanceDashboard, type FinanceInvoice, type FinancePayment,
} from '@/api/finance-v2'

import FinanceAllocationDialog from './FinanceAllocationDialog.vue'
import BankStatementDialog from './BankStatementDialog.vue'
import BankReconciliationDialog from './BankReconciliationDialog.vue'
import StockInvoiceDialog from './StockInvoiceDialog.vue'
import CreditNoteDialog from './CreditNoteDialog.vue'
import CurrencyRevaluationDialog from './CurrencyRevaluationDialog.vue'
import ForeignDocumentDialog from './ForeignDocumentDialog.vue'
import MatchExceptionDialog from './MatchExceptionDialog.vue'
import { useAuthStore } from '@/stores/auth'

const emit = defineEmits<{ posted: [] }>()
const auth = useAuthStore()
const { t, locale } = useI18n()
const loading = ref(false)
const saving = ref(false)
const baseCurrency = ref('')
const postingId = ref('')
const dashboard = ref<FinanceDashboard | null>(null)
const invoices = ref<FinanceInvoice[]>([])
const payments = ref<FinancePayment[]>([])
const statements = ref<BankStatementLine[]>([])
const banks = ref<FinanceBankAccount[]>([])
const statementVisible = ref(false)
const reconciliationVisible = ref(false)
const paymentBanks = computed(() => banks.value.filter(row => row.currencyCode === baseCurrency.value))
const customers = ref<MasterDataRecord[]>([])
const suppliers = ref<MasterDataRecord[]>([])
const invoiceVisible = ref(false)
const stockInvoiceVisible = ref(false)
const creditVisible = ref(false), revaluationVisible = ref(false)
const foreignVisible = ref(false)
const matchVisible = ref(false), matchInvoiceId = ref('')
function openMatch(row: FinanceInvoice) {
  if (loading.value || postingId.value || !auth.hasPermission('finance:view')) return
  matchInvoiceId.value = row.id; matchVisible.value = true
}
const paymentVisible = ref(false)
const allocationVisible = ref(false)
const allocationPaymentId = ref('')
function openAllocation(row: FinancePayment) {
  if (loading.value || postingId.value || !auth.hasPermission('finance:view') || !auth.hasPermission('finance:allocate') || row.status !== 'POSTED' || !['RECEIPT', 'PAYMENT'].includes(row.paymentType)) return
  allocationPaymentId.value = row.id
  allocationVisible.value = true
}
function allocationChanged() { emit('posted'); void load() }
const localDate = (value: Date) => `${value.getFullYear()}-${String(value.getMonth() + 1).padStart(2, '0')}-${String(value.getDate()).padStart(2, '0')}`
const today = () => localDate(new Date())
const plusDays = (days: number) => { const value = new Date(); value.setDate(value.getDate() + days); return localDate(value) }
const dateRange = ref<[string, string]>([`${today().slice(0, 7)}-01`, today()])
type Section = 'dashboard' | 'invoices' | 'payments' | 'statements' | 'parties' | 'settings' | 'banks'
const errors = reactive<Record<Section, boolean>>({ dashboard: false, invoices: false, payments: false, statements: false, parties: false, settings: false, banks: false })
let loadVersion = 0
const invoiceForm = reactive({ documentType: 'SALES_INVOICE' as 'SALES_INVOICE' | 'SUPPLIER_INVOICE', partyId: '', description: '', quantity: 1, unitPrice: 0, taxRate: 0 })
const paymentForm = reactive({ paymentType: 'RECEIPT' as 'RECEIPT' | 'PAYMENT', partyId: '', bankAccountId: '', amount: 0, reference: '' })

const hasParty = (type: string, id: string) => parties(type).some(row => row.id === id && row.active)
const ready = computed(() => !loading.value && !errors.parties && !errors.settings && !!baseCurrency.value && auth.hasPermission('master:view'))
const invoiceReady = computed(() => ready.value && auth.hasPermission('finance:invoice') && hasParty(invoiceForm.documentType, invoiceForm.partyId) && !!invoiceForm.description.trim() && Number.isFinite(invoiceForm.quantity) && invoiceForm.quantity > 0 && Number.isFinite(invoiceForm.unitPrice) && invoiceForm.unitPrice > 0 && Number.isFinite(invoiceForm.taxRate) && invoiceForm.taxRate >= 0 && invoiceForm.taxRate <= 100)
const paymentReady = computed(() => ready.value && auth.hasPermission('finance:create') && hasParty(paymentForm.paymentType, paymentForm.partyId) && Number.isFinite(paymentForm.amount) && paymentForm.amount > 0 && paymentForm.reference.length <= 160 && (!paymentForm.bankAccountId || (!errors.banks && paymentBanks.value.some(row => row.id === paymentForm.bankAccountId))))
watch(() => baseCurrency.value, () => { paymentForm.bankAccountId = '' })
watch(() => invoiceForm.documentType, () => { invoiceForm.partyId = '' })
watch(() => paymentForm.paymentType, () => { paymentForm.partyId = '' })
function openInvoice() {
  if (saving.value || loading.value) return
  Object.assign(invoiceForm, { documentType: 'SALES_INVOICE', partyId: '', description: '', quantity: 1, unitPrice: 0, taxRate: 0 })
  invoiceVisible.value = true
}
function openPayment() {
  if (saving.value || loading.value) return
  Object.assign(paymentForm, { paymentType: 'RECEIPT', partyId: '', bankAccountId: '', amount: 0, reference: '' })
  paymentVisible.value = true
}

function bankName(id: string) { const bank = banks.value.find(row => row.id === id); return bank ? `${bank.code} · ${bank.name}` : id }
function money(value?: number) { return Number(value ?? 0).toLocaleString(locale.value, { minimumFractionDigits: 2, maximumFractionDigits: 2 }) }
function statusType(status: string) { return ['POSTED', 'PAID', 'ALLOCATED', 'MATCHED'].includes(status) ? 'success' : status.includes('EXCEPTION') ? 'danger' : status === 'DRAFT' ? 'warning' : 'info' }
function parties(type: string) { return type === 'SALES_INVOICE' || type === 'RECEIPT' ? customers.value : suppliers.value }

async function load() {
  if (!dateRange.value || dateRange.value.length !== 2) return
  const version = ++loadVersion
  const [from, to] = dateRange.value
  loading.value = true
  baseCurrency.value = ''
  dashboard.value = null; invoices.value = []; payments.value = []; statements.value = []
  const inRange = (date: string) => date >= from && date <= to
  async function section(key: Section, work: () => Promise<void>) {
    errors[key] = false
    try { await work() } catch { if (version === loadVersion) errors[key] = true }
  }
  await Promise.all([
    section('settings', async () => { const value = await getOrganizationSettings(); if (version === loadVersion) baseCurrency.value = value.baseCurrencyCode }),
    section('dashboard', async () => { const value = await getFinanceDashboard(from, to); if (version === loadVersion) dashboard.value = value }),
    section('invoices', async () => { const value = await listFinanceInvoices(); if (version === loadVersion) invoices.value = value.filter(row => inRange(row.accountingDate)) }),
    section('payments', async () => { const value = await listFinancePayments(); if (version === loadVersion) payments.value = value.filter(row => inRange(row.accountingDate)) }),
    section('banks', async () => { const value = await listFinanceBankAccounts(); if (version === loadVersion) banks.value = value }),
    section('statements', async () => { const value = await listBankStatementLines(); if (version === loadVersion) statements.value = value.filter(row => inRange(row.transactionDate)) }),
    ...(auth.hasPermission('master:view') ? [section('parties', async () => {
      const [customerPage, supplierPage] = await Promise.all([listMasterData<MasterDataRecord>('customers', '', 0, 100), listMasterData<MasterDataRecord>('suppliers', '', 0, 100)])
      if (version === loadVersion) { customers.value = customerPage.content.filter(row => row.active); suppliers.value = supplierPage.content.filter(row => row.active) }
    })] : []),
  ])
  if (version === loadVersion) loading.value = false
}

async function submitInvoice() {
  if (saving.value || !invoiceReady.value) return
  saving.value = true
  try {
    const date = today()
    await createFinanceInvoice({
      documentType: invoiceForm.documentType, partyId: invoiceForm.partyId, businessDate: date, accountingDate: date,
      dueDate: plusDays(30), exchangeRateDate: date, currencyCode: baseCurrency.value, exchangeRate: 1,
      lines: [{ description: invoiceForm.description, quantity: invoiceForm.quantity, unitPrice: invoiceForm.unitPrice, discountRate: 0, taxRate: invoiceForm.taxRate, sources: [] }],
    })
    invoiceVisible.value = false; ElMessage.success(t('finance.m4.invoiceCreated')); await load()
  } catch { ElMessage.error(t('finance.saveFailed')) } finally { saving.value = false }
}

async function submitPayment() {
  if (saving.value || !paymentReady.value) return
  saving.value = true
  try {
    const date = today()
    await createFinancePayment({ paymentType: paymentForm.paymentType, partyId: paymentForm.partyId, bankAccountId: paymentForm.bankAccountId || undefined, businessDate: date, accountingDate: date, exchangeRateDate: date, currencyCode: baseCurrency.value, exchangeRate: 1, amount: paymentForm.amount, reference: paymentForm.reference })
    paymentVisible.value = false; ElMessage.success(t('finance.m4.paymentCreated')); await load()
  } catch { ElMessage.error(t('finance.saveFailed')) } finally { saving.value = false }
}

async function postInvoice(row: FinanceInvoice) {
  if (postingId.value || loading.value || !auth.hasPermission('finance:post') || row.status !== 'DRAFT' || row.matchStatus === 'EXCEPTION') return
  postingId.value = row.id
  try { await postFinanceInvoice(row); emit('posted'); await load(); ElMessage.success(t('finance.m4.posted')) } catch { ElMessage.error(t('finance.saveFailed')); await load() } finally { postingId.value = '' }
}
async function postPayment(row: FinancePayment) {
  if (postingId.value || loading.value || !auth.hasPermission('finance:post') || row.status !== 'DRAFT') return
  postingId.value = row.id
  try { await postFinancePayment(row); emit('posted'); await load(); ElMessage.success(t('finance.m4.posted')) } catch { ElMessage.error(t('finance.saveFailed')); await load() } finally { postingId.value = '' }
}
onBeforeUnmount(() => { loadVersion++ })
onMounted(load)
</script>

<template>
  <el-card v-loading="loading" shadow="never" class="m4-panel">
    <div class="section-heading"><div><span class="eyebrow">M4 · Finance close</span><h2>{{ t('finance.m4.title') }}</h2><p>{{ t('finance.m4.subtitle') }}</p></div><div><el-button v-if="auth.hasPermission('finance:invoice') && auth.hasPermission('master:view')" plain :disabled="loading || saving" @click="openInvoice">{{ t('finance.m4.newInvoice') }}</el-button><el-button v-if="auth.hasPermission('finance:create') && auth.hasPermission('master:view')" type="primary" :disabled="loading || saving" @click="openPayment">{{ t('finance.m4.newPayment') }}</el-button></div></div>
    <div class="finance-range"><el-date-picker v-model="dateRange" type="daterange" value-format="YYYY-MM-DD" :clearable="false" @change="load" /><el-button @click="load">{{ t('finance.m4.retry') }}</el-button><span>{{ t('finance.m4.rangeNote') }}</span></div>
    <el-alert v-if="errors.dashboard" :title="t('finance.m4.dashboardFailed')" type="error" :closable="false" show-icon />
    <el-alert v-if="errors.settings" :title="t('finance.m4.settingsFailed')" type="error" :closable="false" show-icon />
    <el-alert v-if="errors.parties" :title="t('finance.m4.partiesFailed')" type="error" :closable="false" show-icon />
    <div v-if="dashboard" class="m4-metrics">
      <div><span>{{ t('finance.m4.receivables') }}</span><strong>{{ money(dashboard?.receivables) }}</strong></div>
      <div><span>{{ t('finance.m4.payables') }}</span><strong>{{ money(dashboard?.payables) }}</strong></div>
      <div><span>{{ t('finance.m4.netIncome') }}</span><strong>{{ money(dashboard?.netIncome) }}</strong></div>
      <div><span>{{ t('finance.m4.unmatched') }}</span><strong>{{ dashboard?.unmatchedBankLines ?? 0 }}</strong></div>
    </div>
    <div v-else class="m4-metrics" aria-live="polite"><span>—</span><span>—</span><span>—</span><span>—</span></div>
    <el-alert v-for="section in (['invoices', 'payments', 'statements'] as const)" v-show="errors[section]" :key="section" :title="t(`finance.m4.${section}Failed`)" type="error" :closable="false" show-icon />
    <el-button v-if="auth.hasPermission('finance:view') && auth.hasPermission('finance:bank')" :disabled="loading || saving || !!postingId" @click="statementVisible = true">{{ t('finance.bankEntry.newStatement') }}</el-button>
    <el-button v-if="auth.hasPermission('finance:view') && auth.hasPermission('finance:bank')" :disabled="loading || saving || !!postingId" @click="reconciliationVisible = true">{{ t('finance.bankMatch.manage') }}</el-button>
    <el-button v-if="auth.hasPermission('finance:view') && auth.hasPermission('finance:invoice')" :disabled="loading || saving || !!postingId" @click="stockInvoiceVisible = true">{{ t('finance.stockInvoice.open') }}</el-button>
    <el-button v-if="auth.hasPermission('finance:view')" :disabled="loading || saving || !!postingId" @click="revaluationVisible = true">{{ t('finance.revaluation.open') }}</el-button>
    <el-button v-if="auth.hasPermission('finance:view') && auth.hasPermission('finance:invoice')" :disabled="loading || saving || !!postingId" @click="creditVisible = true">{{ t('finance.creditNote.open') }}</el-button>
    <el-button v-if="auth.hasPermission('finance:view') && auth.hasPermission('master:view') && (auth.hasPermission('finance:invoice') || auth.hasPermission('finance:create'))" :disabled="loading || saving || !!postingId" @click="foreignVisible = true">{{ t('finance.foreign.open') }}</el-button>
    <el-tabs>
      <el-tab-pane :label="t('finance.m4.invoices')"><el-table :data="invoices" size="small"><el-table-column prop="number" :label="t('finance.number')" /><el-table-column prop="documentType" :label="t('finance.m4.type')" /><el-table-column prop="accountingDate" :label="t('finance.entryDate')" /><el-table-column :label="t('finance.amount')"><template #default="{ row }">{{ money(row.totalAmount) }} {{ row.currencyCode }}</template></el-table-column><el-table-column :label="t('finance.statusLabel')"><template #default="{ row }"><el-tag :type="statusType(row.status)">{{ row.status }}</el-tag></template></el-table-column><el-table-column :label="t('finance.actions')"><template #default="{ row }"><el-button v-if="auth.hasPermission('finance:view') && row.documentType === 'SUPPLIER_INVOICE' && ['EXCEPTION', 'APPROVED_EXCEPTION'].includes(row.matchStatus)" link type="primary" :disabled="loading || !!postingId" @click="openMatch(row)">{{ t('finance.matchException.open') }}</el-button><el-button v-if="auth.hasPermission('finance:post') && row.status === 'DRAFT'" link type="primary" :disabled="!!postingId || loading || row.matchStatus === 'EXCEPTION'" :loading="postingId === row.id" @click="postInvoice(row)">{{ t('finance.m4.post') }}</el-button></template></el-table-column></el-table></el-tab-pane>
      <el-tab-pane :label="t('finance.m4.payments')"><el-table :data="payments" size="small"><el-table-column prop="number" :label="t('finance.number')" /><el-table-column prop="paymentType" :label="t('finance.m4.type')" /><el-table-column prop="accountingDate" :label="t('finance.entryDate')" /><el-table-column :label="t('finance.amount')"><template #default="{ row }">{{ money(row.amount) }} {{ row.currencyCode }}</template></el-table-column><el-table-column :label="t('finance.statusLabel')"><template #default="{ row }"><el-tag :type="statusType(row.status)">{{ row.status }}</el-tag></template></el-table-column><el-table-column :label="t('finance.actions')"><template #default="{ row }"><el-button v-if="auth.hasPermission('finance:post') && row.status === 'DRAFT'" link type="primary" :disabled="!!postingId || loading" :loading="postingId === row.id" @click="postPayment(row)">{{ t('finance.m4.post') }}</el-button><el-button v-if="auth.hasPermission('finance:view') && auth.hasPermission('finance:allocate') && row.status === 'POSTED' && ['RECEIPT', 'PAYMENT'].includes(row.paymentType)" link type="primary" :disabled="loading || !!postingId" @click="openAllocation(row)">{{ t('finance.allocation.manage') }}</el-button></template></el-table-column></el-table></el-tab-pane>
      <el-tab-pane :label="t('finance.m4.bank')"><el-table :data="statements" size="small"><el-table-column :label="t('finance.bankEntry.account')"><template #default="{ row }">{{ bankName(row.bankAccountId) }}</template></el-table-column><el-table-column prop="transactionDate" :label="t('finance.entryDate')" /><el-table-column prop="externalReference" :label="t('finance.m4.reference')" /><el-table-column prop="counterparty" :label="t('finance.m4.counterparty')" /><el-table-column :label="t('finance.amount')"><template #default="{ row }">{{ money(row.amount) }} {{ row.currencyCode }}</template></el-table-column><el-table-column prop="reconciliationStatus" :label="t('finance.statusLabel')" /></el-table></el-tab-pane>
    </el-tabs>
  </el-card>
  <ForeignDocumentDialog v-model="foreignVisible" @changed="allocationChanged" />
  <CurrencyRevaluationDialog v-model="revaluationVisible" @changed="allocationChanged" />
  <CreditNoteDialog v-model="creditVisible" @changed="allocationChanged" />
  <StockInvoiceDialog v-model="stockInvoiceVisible" @changed="allocationChanged" />
  <BankReconciliationDialog v-model="reconciliationVisible" @changed="allocationChanged" />
  <BankStatementDialog v-model="statementVisible" @changed="allocationChanged" />
  <MatchExceptionDialog v-model="matchVisible" :invoice-id="matchInvoiceId" @changed="allocationChanged" />
  <FinanceAllocationDialog v-model="allocationVisible" :payment-id="allocationPaymentId" @changed="allocationChanged" />

  <el-dialog v-model="invoiceVisible" :title="t('finance.m4.newInvoice')" width="min(560px, calc(100vw - 32px))" destroy-on-close :close-on-click-modal="!saving" :close-on-press-escape="!saving" :show-close="!saving"><p>{{ t('finance.m4.baseCurrency', { currency: baseCurrency || '—' }) }}</p><el-form :disabled="saving" label-position="top"><div class="form-grid"><el-form-item :label="t('finance.m4.type')"><el-select v-model="invoiceForm.documentType"><el-option label="Sales invoice" value="SALES_INVOICE" /><el-option label="Supplier invoice" value="SUPPLIER_INVOICE" /></el-select></el-form-item><el-form-item :label="t('finance.m4.party')"><el-select v-model="invoiceForm.partyId" filterable><el-option v-for="item in parties(invoiceForm.documentType)" :key="item.id" :label="`${item.code} · ${item.name}`" :value="item.id" /></el-select></el-form-item><el-form-item :label="t('finance.m4.description')"><el-input v-model="invoiceForm.description" /></el-form-item><el-form-item :label="t('finance.m4.quantity')"><el-input-number v-model="invoiceForm.quantity" :min="0.01" /></el-form-item><el-form-item :label="t('finance.m4.unitPrice')"><el-input-number v-model="invoiceForm.unitPrice" :min="0" :precision="2" /></el-form-item><el-form-item :label="t('finance.m4.taxRate')"><el-input-number v-model="invoiceForm.taxRate" :min="0" :max="100" :precision="2" /></el-form-item></div></el-form><template #footer><el-button :disabled="saving" @click="invoiceVisible = false">{{ t('projects.cancel') }}</el-button><el-button type="primary" :loading="saving" :disabled="saving || !invoiceReady" @click="submitInvoice">{{ t('projects.save') }}</el-button></template></el-dialog>
  <el-dialog v-model="paymentVisible" :title="t('finance.m4.newPayment')" width="min(520px, calc(100vw - 32px))" destroy-on-close :close-on-click-modal="!saving" :close-on-press-escape="!saving" :show-close="!saving"><p>{{ t('finance.m4.baseCurrency', { currency: baseCurrency || '—' }) }}</p><el-form :disabled="saving" label-position="top"><div class="form-grid"><el-form-item :label="t('finance.m4.type')"><el-select v-model="paymentForm.paymentType"><el-option label="Receipt" value="RECEIPT" /><el-option label="Payment" value="PAYMENT" /></el-select></el-form-item><el-form-item :label="t('finance.m4.party')"><el-select v-model="paymentForm.partyId" filterable><el-option v-for="item in parties(paymentForm.paymentType)" :key="item.id" :label="`${item.code} · ${item.name}`" :value="item.id" /></el-select></el-form-item><el-form-item :label="t('finance.bankEntry.account')"><el-select v-model="paymentForm.bankAccountId" clearable :disabled="errors.banks || loading" class="full-width" :placeholder="t('finance.bankEntry.cash')"><el-option v-for="row in paymentBanks" :key="row.id" :value="row.id" :label="`${row.code} · ${row.name} · ${row.currencyCode}`" /></el-select><p>{{ t('finance.bankEntry.cash') }}</p></el-form-item><el-alert v-if="errors.banks" :title="t('finance.bankEntry.loadFailed')" type="error" :closable="false" /><el-form-item :label="t('finance.amount')"><el-input-number v-model="paymentForm.amount" :min="0.01" :precision="2" /></el-form-item><el-form-item :label="t('finance.m4.reference')"><el-input v-model="paymentForm.reference" /></el-form-item></div></el-form><template #footer><el-button :disabled="saving" @click="paymentVisible = false">{{ t('projects.cancel') }}</el-button><el-button type="primary" :loading="saving" :disabled="saving || !paymentReady" @click="submitPayment">{{ t('projects.save') }}</el-button></template></el-dialog>
</template>

<style scoped>
.m4-panel { margin-bottom: 18px; }.m4-metrics { display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:10px;margin:16px 0 }.m4-metrics div{padding:14px;border-radius:12px;background:#f6f8fb}.m4-metrics span{display:block;color:var(--flowora-muted);font-size:12px}.m4-metrics strong{display:block;margin-top:6px;font-size:20px}.form-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:0 14px}.form-grid :deep(.el-select),.form-grid :deep(.el-input-number){width:100%}@media(max-width:760px){.m4-metrics,.form-grid{grid-template-columns:1fr 1fr}}
</style>
