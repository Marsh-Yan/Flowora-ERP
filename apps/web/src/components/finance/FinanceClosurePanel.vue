<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { listMasterData, type MasterDataRecord } from '@/api/master-data'
import {
  createFinanceInvoice, createFinancePayment, getFinanceDashboard, listBankStatementLines,
  listFinanceInvoices, listFinancePayments, postFinanceInvoice, postFinancePayment,
  type BankStatementLine, type FinanceDashboard, type FinanceInvoice, type FinancePayment,
} from '@/api/finance-v2'

const { t, locale } = useI18n()
const loading = ref(false)
const dashboard = ref<FinanceDashboard | null>(null)
const invoices = ref<FinanceInvoice[]>([])
const payments = ref<FinancePayment[]>([])
const statements = ref<BankStatementLine[]>([])
const customers = ref<MasterDataRecord[]>([])
const suppliers = ref<MasterDataRecord[]>([])
const invoiceVisible = ref(false)
const paymentVisible = ref(false)
const today = () => new Date().toISOString().slice(0, 10)
const plusDays = (days: number) => { const value = new Date(); value.setDate(value.getDate() + days); return value.toISOString().slice(0, 10) }
const invoiceForm = reactive({ documentType: 'SALES_INVOICE' as 'SALES_INVOICE' | 'SUPPLIER_INVOICE', partyId: '', description: '', quantity: 1, unitPrice: 0, taxRate: 0 })
const paymentForm = reactive({ paymentType: 'RECEIPT' as 'RECEIPT' | 'PAYMENT', partyId: '', amount: 0, reference: '' })

function money(value?: number) { return Number(value ?? 0).toLocaleString(locale.value, { minimumFractionDigits: 2, maximumFractionDigits: 2 }) }
function statusType(status: string) { return ['POSTED', 'PAID', 'ALLOCATED', 'MATCHED'].includes(status) ? 'success' : status.includes('EXCEPTION') ? 'danger' : status === 'DRAFT' ? 'warning' : 'info' }
function parties(type: string) { return type === 'SALES_INVOICE' || type === 'RECEIPT' ? customers.value : suppliers.value }

async function load() {
  loading.value = true
  try {
    const [summary, invoiceRows, paymentRows, statementRows, customerPage, supplierPage] = await Promise.all([
      getFinanceDashboard(), listFinanceInvoices(), listFinancePayments(), listBankStatementLines(),
      listMasterData<MasterDataRecord>('customers', '', 0, 100), listMasterData<MasterDataRecord>('suppliers', '', 0, 100),
    ])
    dashboard.value = summary; invoices.value = invoiceRows; payments.value = paymentRows; statements.value = statementRows
    customers.value = customerPage.content; suppliers.value = supplierPage.content
  } catch { ElMessage.error(t('finance.m4.loadFailed')) } finally { loading.value = false }
}

async function submitInvoice() {
  try {
    const date = today()
    await createFinanceInvoice({
      documentType: invoiceForm.documentType, partyId: invoiceForm.partyId, businessDate: date, accountingDate: date,
      dueDate: plusDays(30), exchangeRateDate: date, currencyCode: 'USD', exchangeRate: 1,
      lines: [{ description: invoiceForm.description, quantity: invoiceForm.quantity, unitPrice: invoiceForm.unitPrice, discountRate: 0, taxRate: invoiceForm.taxRate, sources: [] }],
    })
    invoiceVisible.value = false; ElMessage.success(t('finance.m4.invoiceCreated')); await load()
  } catch { ElMessage.error(t('finance.saveFailed')) }
}

async function submitPayment() {
  try {
    const date = today()
    await createFinancePayment({ paymentType: paymentForm.paymentType, partyId: paymentForm.partyId, businessDate: date, accountingDate: date, exchangeRateDate: date, currencyCode: 'USD', exchangeRate: 1, amount: paymentForm.amount, reference: paymentForm.reference })
    paymentVisible.value = false; ElMessage.success(t('finance.m4.paymentCreated')); await load()
  } catch { ElMessage.error(t('finance.saveFailed')) }
}

async function postInvoice(row: FinanceInvoice) { try { await postFinanceInvoice(row); await load(); ElMessage.success(t('finance.m4.posted')) } catch { ElMessage.error(t('finance.saveFailed')) } }
async function postPayment(row: FinancePayment) { try { await postFinancePayment(row); await load(); ElMessage.success(t('finance.m4.posted')) } catch { ElMessage.error(t('finance.saveFailed')) } }
onMounted(load)
</script>

<template>
  <el-card v-loading="loading" shadow="never" class="m4-panel">
    <div class="section-heading"><div><span class="eyebrow">M4 · Finance close</span><h2>{{ t('finance.m4.title') }}</h2><p>{{ t('finance.m4.subtitle') }}</p></div><div><el-button plain @click="invoiceVisible = true">{{ t('finance.m4.newInvoice') }}</el-button><el-button type="primary" @click="paymentVisible = true">{{ t('finance.m4.newPayment') }}</el-button></div></div>
    <div class="m4-metrics">
      <div><span>{{ t('finance.m4.receivables') }}</span><strong>{{ money(dashboard?.receivables) }}</strong></div>
      <div><span>{{ t('finance.m4.payables') }}</span><strong>{{ money(dashboard?.payables) }}</strong></div>
      <div><span>{{ t('finance.m4.netIncome') }}</span><strong>{{ money(dashboard?.netIncome) }}</strong></div>
      <div><span>{{ t('finance.m4.unmatched') }}</span><strong>{{ dashboard?.unmatchedBankLines ?? 0 }}</strong></div>
    </div>
    <el-tabs>
      <el-tab-pane :label="t('finance.m4.invoices')"><el-table :data="invoices" size="small"><el-table-column prop="number" :label="t('finance.number')" /><el-table-column prop="documentType" :label="t('finance.m4.type')" /><el-table-column prop="accountingDate" :label="t('finance.entryDate')" /><el-table-column :label="t('finance.amount')"><template #default="{ row }">{{ money(row.totalAmount) }} {{ row.currencyCode }}</template></el-table-column><el-table-column :label="t('finance.statusLabel')"><template #default="{ row }"><el-tag :type="statusType(row.status)">{{ row.status }}</el-tag></template></el-table-column><el-table-column :label="t('finance.actions')"><template #default="{ row }"><el-button v-if="row.status === 'DRAFT'" link type="primary" @click="postInvoice(row)">{{ t('finance.m4.post') }}</el-button></template></el-table-column></el-table></el-tab-pane>
      <el-tab-pane :label="t('finance.m4.payments')"><el-table :data="payments" size="small"><el-table-column prop="number" :label="t('finance.number')" /><el-table-column prop="paymentType" :label="t('finance.m4.type')" /><el-table-column prop="accountingDate" :label="t('finance.entryDate')" /><el-table-column :label="t('finance.amount')"><template #default="{ row }">{{ money(row.amount) }} {{ row.currencyCode }}</template></el-table-column><el-table-column :label="t('finance.statusLabel')"><template #default="{ row }"><el-tag :type="statusType(row.status)">{{ row.status }}</el-tag></template></el-table-column><el-table-column :label="t('finance.actions')"><template #default="{ row }"><el-button v-if="row.status === 'DRAFT'" link type="primary" @click="postPayment(row)">{{ t('finance.m4.post') }}</el-button></template></el-table-column></el-table></el-tab-pane>
      <el-tab-pane :label="t('finance.m4.bank')"><el-table :data="statements" size="small"><el-table-column prop="transactionDate" :label="t('finance.entryDate')" /><el-table-column prop="externalReference" :label="t('finance.m4.reference')" /><el-table-column prop="counterparty" :label="t('finance.m4.counterparty')" /><el-table-column prop="amount" :label="t('finance.amount')" /><el-table-column prop="reconciliationStatus" :label="t('finance.statusLabel')" /></el-table></el-tab-pane>
    </el-tabs>
  </el-card>

  <el-dialog v-model="invoiceVisible" :title="t('finance.m4.newInvoice')" width="560px"><el-form label-position="top"><div class="form-grid"><el-form-item :label="t('finance.m4.type')"><el-select v-model="invoiceForm.documentType"><el-option label="Sales invoice" value="SALES_INVOICE" /><el-option label="Supplier invoice" value="SUPPLIER_INVOICE" /></el-select></el-form-item><el-form-item :label="t('finance.m4.party')"><el-select v-model="invoiceForm.partyId" filterable><el-option v-for="item in parties(invoiceForm.documentType)" :key="item.id" :label="`${item.code} · ${item.name}`" :value="item.id" /></el-select></el-form-item><el-form-item :label="t('finance.description')"><el-input v-model="invoiceForm.description" /></el-form-item><el-form-item :label="t('finance.m4.quantity')"><el-input-number v-model="invoiceForm.quantity" :min="0.01" /></el-form-item><el-form-item :label="t('finance.m4.unitPrice')"><el-input-number v-model="invoiceForm.unitPrice" :min="0" :precision="2" /></el-form-item><el-form-item :label="t('finance.m4.taxRate')"><el-input-number v-model="invoiceForm.taxRate" :min="0" :precision="2" /></el-form-item></div></el-form><template #footer><el-button @click="invoiceVisible = false">{{ t('projects.cancel') }}</el-button><el-button type="primary" @click="submitInvoice">{{ t('projects.save') }}</el-button></template></el-dialog>
  <el-dialog v-model="paymentVisible" :title="t('finance.m4.newPayment')" width="520px"><el-form label-position="top"><div class="form-grid"><el-form-item :label="t('finance.m4.type')"><el-select v-model="paymentForm.paymentType"><el-option label="Receipt" value="RECEIPT" /><el-option label="Payment" value="PAYMENT" /></el-select></el-form-item><el-form-item :label="t('finance.m4.party')"><el-select v-model="paymentForm.partyId" filterable><el-option v-for="item in parties(paymentForm.paymentType)" :key="item.id" :label="`${item.code} · ${item.name}`" :value="item.id" /></el-select></el-form-item><el-form-item :label="t('finance.amount')"><el-input-number v-model="paymentForm.amount" :min="0.01" :precision="2" /></el-form-item><el-form-item :label="t('finance.m4.reference')"><el-input v-model="paymentForm.reference" /></el-form-item></div></el-form><template #footer><el-button @click="paymentVisible = false">{{ t('projects.cancel') }}</el-button><el-button type="primary" @click="submitPayment">{{ t('projects.save') }}</el-button></template></el-dialog>
</template>

<style scoped>
.m4-panel { margin-bottom: 18px; }.m4-metrics { display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:10px;margin:16px 0 }.m4-metrics div{padding:14px;border-radius:12px;background:#f6f8fb}.m4-metrics span{display:block;color:var(--flowora-muted);font-size:12px}.m4-metrics strong{display:block;margin-top:6px;font-size:20px}.form-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:0 14px}.form-grid :deep(.el-select),.form-grid :deep(.el-input-number){width:100%}@media(max-width:760px){.m4-metrics,.form-grid{grid-template-columns:1fr 1fr}}
</style>
