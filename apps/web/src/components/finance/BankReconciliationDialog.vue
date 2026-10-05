<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import { listBankStatementLines, listFinancePayments, listFinanceBankAccounts, listBankReconciliations, reconcileBankStatement, reverseBankReconciliation, type BankStatementLine, type FinancePayment, type FinanceBankAccount, type BankReconciliation } from '@/api/finance-v2'
const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; changed: [] }>()
const auth = useAuthStore()
const { t, locale } = useI18n()
const statements = ref<BankStatementLine[]>([]), payments = ref<FinancePayment[]>([]), banks = ref<FinanceBankAccount[]>([]), records = ref<BankReconciliation[]>([])
const loading = ref(false), busy = ref(false), failed = ref(false), uncertain = ref(false)
const statementId = ref(''), paymentId = ref(''), requestKey = ref(''), reversalId = ref(''), reason = ref('')
let generation = 0
const allowed = computed(() => auth.hasPermission('finance:view') && auth.hasPermission('finance:bank'))
const ready = computed(() => props.modelValue && allowed.value && !loading.value && !failed.value)
const statementCandidates = computed(() => statements.value.filter(row => row.reconciliationStatus === 'UNMATCHED' && Number.isFinite(row.amount) && row.amount !== 0 && banks.value.some(bank => bank.id === row.bankAccountId && bank.currencyCode === row.currencyCode)))
const statement = computed(() => statementCandidates.value.find(row => row.id === statementId.value))
function remaining(row: FinancePayment) {
  const matched = records.value.filter(record => record.status === 'CONFIRMED').flatMap(record => record.links).filter(link => link.paymentId === row.id).reduce((sum, link) => sum + link.matchedAmount, 0)
  return Math.max(0, Math.round((row.amount - matched) * 10000) / 10000)
}
const paymentCandidates = computed(() => payments.value.filter(row => statement.value && row.status === 'POSTED' && row.bankAccountId === statement.value.bankAccountId && row.currencyCode === statement.value.currencyCode && (statement.value.amount > 0 ? ['RECEIPT','SUPPLIER_REFUND'] : ['PAYMENT','CUSTOMER_REFUND']).includes(row.paymentType) && remaining(row) >= Math.abs(statement.value.amount)))
const payment = computed(() => paymentCandidates.value.find(row => row.id === paymentId.value))
const valid = computed(() => ready.value && !!statement.value && !!payment.value)
const reversal = computed(() => records.value.find(row => row.id === reversalId.value && row.status === 'CONFIRMED'))
const validReverse = computed(() => ready.value && !!reversal.value && !!reason.value.trim() && reason.value.trim().length <= 500)
function money(value: number) { return value.toLocaleString(locale.value, { minimumFractionDigits: 2, maximumFractionDigits: 4 }) }
function bankName(id: string) { return banks.value.find(row => row.id === id)?.code ?? id }
function linkLabel(record: BankReconciliation) { return record.links.map(link => `${statements.value.find(row => row.id === link.statementLineId)?.externalReference ?? link.statementLineId} → ${payments.value.find(row => row.id === link.paymentId)?.number ?? link.paymentId} · ${money(link.matchedAmount)}`).join('; ') }
function close() { if (!busy.value) emit('update:modelValue', false) }
async function load() {
  const token = ++generation
  if (!props.modelValue || !allowed.value) { statements.value = []; payments.value = []; records.value = []; banks.value = []; loading.value = false; return }
  loading.value = true; failed.value = false
  try {
    const [lines, paymentRows, bankRows, history] = await Promise.all([listBankStatementLines(), listFinancePayments(), listFinanceBankAccounts(), listBankReconciliations()])
    if (token !== generation) return
    if (history.some(row => !Array.isArray(row.links))) throw new Error('Reconciliation links unavailable')
    statements.value = lines; payments.value = paymentRows; banks.value = bankRows; records.value = history
    if (!statementCandidates.value.some(row => row.id === statementId.value)) statementId.value = ''
    if (!paymentCandidates.value.some(row => row.id === paymentId.value)) paymentId.value = ''
    if (!reversal.value) reversalId.value = ''
  } catch { if (token === generation) { failed.value = true; statements.value = []; payments.value = []; records.value = []; banks.value = [] } }
  finally { if (token === generation) loading.value = false }
}
watch(statementId, () => { paymentId.value = ''; requestKey.value = '' })
watch(paymentId, () => { requestKey.value = '' })
watch(() => [props.modelValue, auth.user?.organizationId, allowed.value], () => { statementId.value = ''; paymentId.value = ''; requestKey.value = ''; reversalId.value = ''; reason.value = ''; uncertain.value = false; void load() }, { immediate: true })
onBeforeUnmount(() => { generation++ })
async function match() {
  if (busy.value || !valid.value || !statement.value || !payment.value) return
  busy.value = true; uncertain.value = false
  requestKey.value ||= globalThis.crypto.randomUUID()
  try { await reconcileBankStatement(statement.value.bankAccountId, statement.value.id, payment.value.id, Math.abs(statement.value.amount), requestKey.value); ElMessage.success(t('finance.bankMatch.saved')) }
  catch { uncertain.value = true }
  finally { await load(); emit('changed'); busy.value = false }
}
function openReversal(row: BankReconciliation) { if (!busy.value && ready.value && row.status === 'CONFIRMED') { reversalId.value = row.id; reason.value = '' } }
async function reverse() {
  if (busy.value || !validReverse.value || !reversal.value) return
  busy.value = true; uncertain.value = false
  try { await reverseBankReconciliation(reversal.value.id, reason.value.trim()); reversalId.value = ''; ElMessage.success(t('finance.bankMatch.reversedSuccess')) }
  catch { uncertain.value = true }
  finally { await load(); emit('changed'); busy.value = false }
}
</script>
<template>
  <el-dialog :model-value="modelValue" :title="t('finance.bankMatch.title')" width="min(800px, calc(100vw - 32px))" destroy-on-close :show-close="!busy" :close-on-click-modal="!busy" :close-on-press-escape="!busy" @update:model-value="close">
    <el-alert v-if="failed" :title="t('finance.bankMatch.loadFailed')" type="error" :closable="false" /><el-alert v-if="uncertain" :title="t('finance.bankMatch.failed')" type="error" :closable="false" />
    <el-button :disabled="loading || busy" @click="load">{{ t('finance.m4.retry') }}</el-button>
    <div v-if="ready">
      <el-form v-if="!reversal" :key="busy ? 'pending' : 'ready'" :disabled="busy" label-position="top">
        <p>{{ t('finance.bankMatch.note') }}</p>
        <el-form-item :label="t('finance.bankMatch.statement')"><el-select v-model="statementId" class="full-width"><el-option v-for="row in statementCandidates" :key="row.id" :value="row.id" :label="`${bankName(row.bankAccountId)} · ${row.externalReference} · ${money(row.amount)} ${row.currencyCode}`" /></el-select></el-form-item>
        <el-form-item :label="t('finance.bankMatch.payment')"><el-select v-model="paymentId" class="full-width"><el-option v-for="row in paymentCandidates" :key="row.id" :value="row.id" :label="`${row.number} · ${money(remaining(row))} ${row.currencyCode}`" /></el-select></el-form-item>
        <p v-if="payment">{{ t('finance.bankMatch.remaining') }}: {{ money(remaining(payment)) }} {{ payment.currencyCode }}</p>
        <el-button type="primary" :disabled="busy || !valid" :loading="busy" @click="match">{{ t('finance.bankMatch.apply') }}</el-button><p v-if="!statementCandidates.length">{{ t('finance.bankMatch.empty') }}</p>
      </el-form>
      <el-form v-else :key="busy ? 'pending-reverse' : 'ready-reverse'" :disabled="busy" label-position="top">
        <p>{{ reversal.number }} · {{ linkLabel(reversal) }}</p><el-form-item :label="t('finance.bankMatch.reason')"><el-input v-model="reason" type="textarea" :maxlength="500" /></el-form-item><el-button :disabled="busy" @click="reversalId = ''">{{ t('finance.bankMatch.keep') }}</el-button><el-button type="danger" :disabled="busy || !validReverse" :loading="busy" @click="reverse">{{ t('finance.bankMatch.confirmReverse') }}</el-button>
      </el-form>
      <h3>{{ t('finance.bankMatch.history') }}</h3>
      <el-table :data="records" size="small"><el-table-column :label="t('finance.number')" min-width="160"><template #default="{ row }">{{ row.number }} · {{ bankName(row.bankAccountId) }}</template></el-table-column><el-table-column :label="t('finance.bankMatch.history')" min-width="280"><template #default="{ row }">{{ linkLabel(row) }}</template></el-table-column><el-table-column :label="t('finance.statusLabel')" width="100"><template #default="{ row }">{{ t(`finance.bankMatch.${row.status === 'CONFIRMED' ? 'confirmed' : 'reversed'}`) }}</template></el-table-column><el-table-column :label="t('finance.actions')" width="120" fixed="right"><template #default="{ row }"><el-button v-if="row.status === 'CONFIRMED'" link type="danger" :disabled="busy" @click="openReversal(row)">{{ t('finance.bankMatch.reverse') }}</el-button></template></el-table-column></el-table>
    </div>
    <template #footer><el-button :disabled="busy" @click="close">{{ t('finance.bankEntry.close') }}</el-button></template>
  </el-dialog>
</template>
