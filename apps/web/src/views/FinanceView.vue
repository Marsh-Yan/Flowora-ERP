<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { CreditCard, Plus, Refresh } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import {
  closeAccountingPeriod,
  createManualJournal,
  createSupplierPayment,
  getBalanceSheet,
  getIncomeStatement,
  getPayableAging,
  getReceivableAging,
  getTrialBalance,
  listAccountingPeriods,
  listJournalEntries,
  listPayables,
  type AccountingPeriod,
  type AgingRow,
  type FinancialStatementResponse,
  type JournalEntry,
  type Payable,
  type PaymentMethod,
  type TrialBalanceResponse,
} from '@/api/finance'

const { t } = useI18n()
const activeTab = ref<'journals' | 'periods' | 'trialBalance' | 'incomeStatement' | 'balanceSheet' | 'payables' | 'aging'>('journals')
const loading = ref(false)
const manualVisible = ref(false)
const paymentVisible = ref(false)
const journals = ref<JournalEntry[]>([])
const periods = ref<AccountingPeriod[]>([])
const payables = ref<Payable[]>([])
const trialBalance = ref<TrialBalanceResponse | null>(null)
const incomeStatement = ref<FinancialStatementResponse | null>(null)
const balanceSheet = ref<FinancialStatementResponse | null>(null)
const receivableAging = ref<AgingRow[]>([])
const payableAging = ref<AgingRow[]>([])
const selectedPayable = ref<Payable | null>(null)
const startDate = ref(firstDayOfMonth())
const endDate = ref(today())
const manualForm = reactive({ entryDate: today(), memo: '', currencyCode: 'USD', debitAccountCode: '1000', creditAccountCode: '1100', amount: 0 })
const paymentForm = reactive({ amount: 0, method: 'BANK' as PaymentMethod, paymentDate: today(), reference: '' })

const outstandingTotal = computed(() => payables.value.reduce((sum, item) => sum + Number(item.outstandingAmount), 0))
const postedTotal = computed(() => journals.value.filter((item) => item.status === 'POSTED').reduce((sum, item) => sum + Number(item.totalDebit), 0))
const profitTotal = computed(() => Number(incomeStatement.value?.total ?? 0))

function today() {
  return new Date().toISOString().slice(0, 10)
}

function firstDayOfMonth() {
  const date = new Date()
  return new Date(date.getFullYear(), date.getMonth(), 1).toISOString().slice(0, 10)
}

function formatAmount(value: number | undefined) {
  return Number(value ?? 0).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function statusType(status: string) {
  if (['POSTED', 'OPEN'].includes(status)) return 'success'
  if (['PARTIALLY_SETTLED'].includes(status)) return 'warning'
  if (['CLOSED', 'SETTLED'].includes(status)) return 'info'
  if (['VOID', 'CANCELLED'].includes(status)) return 'danger'
  return 'info'
}

function statusLabel(status: string) {
  return t(`finance.status.${status}`, status)
}

function sourceLabel(source: string) {
  return t(`finance.source.${source}`, source)
}

function bucketLabel(bucket: string) {
  return t(`finance.buckets.${bucket}`, bucket)
}

async function load() {
  loading.value = true
  try {
    const [journalPage, periodPage, trial, income, balance, payablePage, receivableAgingRows, payableAgingRows] = await Promise.all([
      listJournalEntries(startDate.value, endDate.value),
      listAccountingPeriods(),
      getTrialBalance(startDate.value, endDate.value),
      getIncomeStatement(startDate.value, endDate.value),
      getBalanceSheet(startDate.value, endDate.value),
      listPayables(),
      getReceivableAging(endDate.value),
      getPayableAging(endDate.value),
    ])
    journals.value = journalPage.content
    periods.value = periodPage.content
    trialBalance.value = trial
    incomeStatement.value = income
    balanceSheet.value = balance
    payables.value = payablePage.content
    receivableAging.value = receivableAgingRows
    payableAging.value = payableAgingRows
  } catch {
    ElMessage.error(t('finance.loadFailed'))
  } finally {
    loading.value = false
  }
}

function openManualJournal() {
  manualForm.entryDate = today()
  manualForm.memo = ''
  manualForm.amount = 0
  manualVisible.value = true
}

async function submitManualJournal() {
  try {
    await createManualJournal({
      entryDate: manualForm.entryDate,
      memo: manualForm.memo,
      currencyCode: manualForm.currencyCode,
      lines: [
        { accountCode: manualForm.debitAccountCode, description: t('finance.debit'), debit: manualForm.amount, credit: 0 },
        { accountCode: manualForm.creditAccountCode, description: t('finance.credit'), debit: 0, credit: manualForm.amount },
      ],
    })
    manualVisible.value = false
    ElMessage.success(t('finance.journalCreated'))
    await load()
  } catch {
    ElMessage.error(t('finance.saveFailed'))
  }
}

function openPayment(payable: Payable) {
  selectedPayable.value = payable
  paymentForm.amount = Number(payable.outstandingAmount)
  paymentForm.paymentDate = today()
  paymentForm.reference = ''
  paymentVisible.value = true
}

async function submitPayment() {
  if (!selectedPayable.value) return
  try {
    await createSupplierPayment({ payableId: selectedPayable.value.id, ...paymentForm })
    paymentVisible.value = false
    ElMessage.success(t('finance.paymentCreated'))
    await load()
  } catch {
    ElMessage.error(t('finance.saveFailed'))
  }
}

async function closePeriod(period: AccountingPeriod) {
  try {
    await closeAccountingPeriod(period.id)
    ElMessage.success(t('finance.periodClosed'))
    await load()
  } catch {
    ElMessage.error(t('finance.saveFailed'))
  }
}

onMounted(load)
</script>

<template>
  <div class="operations-page finance-page">
    <div class="operations-heading section-heading">
      <div>
        <span class="eyebrow">{{ t('finance.eyebrow') }}</span>
        <h1>{{ t('finance.title') }}</h1>
        <p>{{ t('finance.subtitle') }}</p>
      </div>
      <div class="operations-actions">
        <el-date-picker v-model="startDate" type="date" value-format="YYYY-MM-DD" :placeholder="t('finance.from')" />
        <el-date-picker v-model="endDate" type="date" value-format="YYYY-MM-DD" :placeholder="t('finance.to')" />
        <el-button round plain :loading="loading" @click="load"><el-icon><Refresh /></el-icon>{{ t('finance.refresh') }}</el-button>
        <el-button type="primary" round @click="openManualJournal"><el-icon><Plus /></el-icon>{{ t('finance.manualJournal') }}</el-button>
      </div>
    </div>

    <div class="inventory-summary-grid">
      <el-card shadow="never"><span class="eyebrow">{{ t('finance.postedDebit') }}</span><strong>{{ formatAmount(postedTotal) }}</strong><small>{{ t('finance.postedDebitHint') }}</small></el-card>
      <el-card shadow="never"><span class="eyebrow">{{ t('finance.profit') }}</span><strong>{{ formatAmount(profitTotal) }}</strong><small>{{ t('finance.profitHint') }}</small></el-card>
      <el-card shadow="never"><span class="eyebrow">{{ t('finance.payableOutstanding') }}</span><strong>{{ formatAmount(outstandingTotal) }}</strong><small>{{ t('finance.payableHint') }}</small></el-card>
    </div>

    <el-card shadow="never" class="operations-card">
      <el-tabs v-model="activeTab">
        <el-tab-pane :label="t('finance.journals')" name="journals">
          <el-table v-loading="loading" :data="journals" empty-text="">
            <el-table-column type="expand"><template #default="{ row }"><el-table :data="row.lines" size="small" border><el-table-column prop="lineNo" label="#" width="55" /><el-table-column prop="accountCode" :label="t('finance.account')" width="120" /><el-table-column prop="description" :label="t('finance.description')" min-width="180" /><el-table-column prop="debit" :label="t('finance.debit')" width="130" /><el-table-column prop="credit" :label="t('finance.credit')" width="130" /></el-table></template></el-table-column>
            <el-table-column prop="number" :label="t('finance.number')" width="150" />
            <el-table-column prop="entryDate" :label="t('finance.entryDate')" width="120" />
            <el-table-column :label="t('finance.sourceLabel')" min-width="160"><template #default="{ row }">{{ sourceLabel(row.sourceType) }}</template></el-table-column>
            <el-table-column prop="memo" :label="t('finance.memo')" min-width="220" />
            <el-table-column prop="totalDebit" :label="t('finance.amount')" width="140"><template #default="{ row }">{{ formatAmount(row.totalDebit) }} {{ row.currencyCode }}</template></el-table-column>
            <el-table-column :label="t('finance.statusLabel')" width="110"><template #default="{ row }"><el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag></template></el-table-column>
          </el-table>
          <el-empty v-if="!journals.length && !loading" :description="t('finance.emptyJournals')" />
        </el-tab-pane>

        <el-tab-pane :label="t('finance.periods')" name="periods">
          <el-table v-loading="loading" :data="periods" empty-text="">
            <el-table-column :label="t('finance.period')" min-width="150"><template #default="{ row }">{{ row.year }}-{{ String(row.month).padStart(2, '0') }}</template></el-table-column>
            <el-table-column prop="startDate" :label="t('finance.from')" width="140" />
            <el-table-column prop="endDate" :label="t('finance.to')" width="140" />
            <el-table-column :label="t('finance.statusLabel')" width="130"><template #default="{ row }"><el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag></template></el-table-column>
            <el-table-column :label="t('finance.actions')" width="130"><template #default="{ row }"><el-button v-if="row.status === 'OPEN'" link type="primary" @click="closePeriod(row)">{{ t('finance.closePeriod') }}</el-button></template></el-table-column>
          </el-table>
          <el-empty v-if="!periods.length && !loading" :description="t('finance.emptyPeriods')" />
        </el-tab-pane>

        <el-tab-pane :label="t('finance.trialBalance')" name="trialBalance">
          <div class="finance-report-total"><span>{{ t('finance.totalDebit') }} / {{ t('finance.totalCredit') }}</span><strong>{{ formatAmount(trialBalance?.totalDebit) }} / {{ formatAmount(trialBalance?.totalCredit) }}</strong></div>
          <el-table v-loading="loading" :data="trialBalance?.rows ?? []"><el-table-column prop="accountCode" :label="t('finance.account')" width="120" /><el-table-column prop="accountName" :label="t('finance.accountName')" min-width="180" /><el-table-column prop="accountType" :label="t('finance.accountType')" width="130" /><el-table-column prop="debit" :label="t('finance.debit')" width="150" /><el-table-column prop="credit" :label="t('finance.credit')" width="150" /><el-table-column prop="balance" :label="t('finance.balance')" width="150" /></el-table>
        </el-tab-pane>

        <el-tab-pane :label="t('finance.incomeStatement')" name="incomeStatement">
          <div class="finance-report-total"><span>{{ t('finance.netProfit') }}</span><strong>{{ formatAmount(incomeStatement?.total) }}</strong></div>
          <el-table v-loading="loading" :data="incomeStatement?.rows ?? []"><el-table-column prop="accountCode" :label="t('finance.account')" width="120" /><el-table-column prop="accountName" :label="t('finance.accountName')" min-width="220" /><el-table-column prop="accountType" :label="t('finance.accountType')" width="130" /><el-table-column prop="amount" :label="t('finance.amount')" width="170" /></el-table>
        </el-tab-pane>

        <el-tab-pane :label="t('finance.balanceSheet')" name="balanceSheet">
          <div class="finance-report-total"><span>{{ t('finance.balanceTotal') }}</span><strong>{{ formatAmount(balanceSheet?.total) }}</strong></div>
          <el-table v-loading="loading" :data="balanceSheet?.rows ?? []"><el-table-column prop="accountCode" :label="t('finance.account')" width="120" /><el-table-column prop="accountName" :label="t('finance.accountName')" min-width="220" /><el-table-column prop="accountType" :label="t('finance.accountType')" width="130" /><el-table-column prop="amount" :label="t('finance.amount')" width="170" /></el-table>
        </el-tab-pane>

        <el-tab-pane :label="t('finance.payables')" name="payables">
          <el-table v-loading="loading" :data="payables" empty-text=""><el-table-column prop="number" :label="t('finance.number')" width="150" /><el-table-column prop="supplierId" :label="t('finance.supplier')" min-width="170" /><el-table-column prop="dueDate" :label="t('finance.dueDate')" width="130" /><el-table-column prop="totalAmount" :label="t('finance.amount')" width="140" /><el-table-column prop="outstandingAmount" :label="t('finance.outstanding')" width="150" /><el-table-column :label="t('finance.statusLabel')" width="150"><template #default="{ row }"><el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag></template></el-table-column><el-table-column :label="t('finance.actions')" width="140"><template #default="{ row }"><el-button v-if="row.outstandingAmount > 0" link type="primary" @click="openPayment(row)"><el-icon><CreditCard /></el-icon>{{ t('finance.pay') }}</el-button></template></el-table-column></el-table>
          <el-empty v-if="!payables.length && !loading" :description="t('finance.emptyPayables')" />
        </el-tab-pane>

        <el-tab-pane :label="t('finance.aging')" name="aging">
          <div class="finance-aging-grid"><el-card shadow="never"><div class="section-heading"><h2>{{ t('finance.receivableAging') }}</h2><span>{{ receivableAging.length }}</span></div><el-table :data="receivableAging" size="small"><el-table-column prop="number" :label="t('finance.number')" /><el-table-column prop="dueDate" :label="t('finance.dueDate')" /><el-table-column prop="outstandingAmount" :label="t('finance.outstanding')" /><el-table-column :label="t('finance.bucket')"><template #default="{ row }">{{ bucketLabel(row.bucket) }}</template></el-table-column></el-table></el-card><el-card shadow="never"><div class="section-heading"><h2>{{ t('finance.payableAging') }}</h2><span>{{ payableAging.length }}</span></div><el-table :data="payableAging" size="small"><el-table-column prop="number" :label="t('finance.number')" /><el-table-column prop="dueDate" :label="t('finance.dueDate')" /><el-table-column prop="outstandingAmount" :label="t('finance.outstanding')" /><el-table-column :label="t('finance.bucket')"><template #default="{ row }">{{ bucketLabel(row.bucket) }}</template></el-table-column></el-table></el-card></div>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <el-dialog v-model="manualVisible" :title="t('finance.manualJournal')" width="560px">
      <el-form label-position="top"><div class="operations-form-grid"><el-form-item :label="t('finance.entryDate')"><el-date-picker v-model="manualForm.entryDate" type="date" value-format="YYYY-MM-DD" class="full-width" /></el-form-item><el-form-item :label="t('finance.currency')"><el-input v-model="manualForm.currencyCode" /></el-form-item><el-form-item :label="t('finance.memo')"><el-input v-model="manualForm.memo" /></el-form-item><el-form-item :label="t('finance.amount')"><el-input-number v-model="manualForm.amount" :min="0.0001" :precision="4" class="full-width" /></el-form-item><el-form-item :label="t('finance.debitAccount')"><el-input v-model="manualForm.debitAccountCode" /></el-form-item><el-form-item :label="t('finance.creditAccount')"><el-input v-model="manualForm.creditAccountCode" /></el-form-item></div></el-form>
      <template #footer><el-button @click="manualVisible = false">{{ t('finance.cancel') }}</el-button><el-button type="primary" @click="submitManualJournal">{{ t('finance.post') }}</el-button></template>
    </el-dialog>

    <el-dialog v-model="paymentVisible" :title="t('finance.supplierPayment')" width="430px">
      <el-form label-position="top"><el-form-item :label="t('finance.number')"><el-input :model-value="selectedPayable?.number" disabled /></el-form-item><el-form-item :label="t('finance.amount')"><el-input-number v-model="paymentForm.amount" :min="0.0001" :max="selectedPayable?.outstandingAmount" :precision="4" class="full-width" /></el-form-item><el-form-item :label="t('finance.paymentMethod')"><el-select v-model="paymentForm.method" class="full-width"><el-option :label="t('finance.bank')" value="BANK" /><el-option :label="t('finance.cash')" value="CASH" /><el-option :label="t('finance.other')" value="OTHER" /></el-select></el-form-item><el-form-item :label="t('finance.paymentDate')"><el-date-picker v-model="paymentForm.paymentDate" type="date" value-format="YYYY-MM-DD" class="full-width" /></el-form-item><el-form-item :label="t('finance.reference')"><el-input v-model="paymentForm.reference" /></el-form-item></el-form>
      <template #footer><el-button @click="paymentVisible = false">{{ t('finance.cancel') }}</el-button><el-button type="primary" @click="submitPayment">{{ t('finance.pay') }}</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>
.finance-page .operations-actions { align-items: center; }
.finance-page .operations-actions .el-date-editor { width: 150px; }
.finance-report-total { display: flex; align-items: center; justify-content: space-between; margin: 4px 0 18px; padding: 12px 15px; border-radius: 10px; background: #f7f8fb; color: var(--flowora-muted); font-size: 11px; }
.finance-report-total strong { color: var(--flowora-ink); font-size: 18px; }
.finance-aging-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; }
.finance-aging-grid .el-card { border: 1px solid var(--flowora-border); border-radius: 12px; }
.finance-aging-grid .section-heading { margin-bottom: 10px; }
.finance-aging-grid .section-heading h2 { font-size: 14px; }
@media (max-width: 760px) {
  .finance-aging-grid { grid-template-columns: 1fr; }
  .finance-page .operations-actions .el-date-editor { width: 100%; }
}
</style>
