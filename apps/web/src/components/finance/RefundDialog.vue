<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import { recoverFinanceRefundSource, correctFinanceRefund, createFinancePayment, listFinancePayments, listFinanceBankAccounts, postFinancePayment, type FinancePayment, type FinanceBankAccount, type PaymentInput } from '@/api/finance-v2'
const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; changed: [] }>()
const auth = useAuthStore(), { t } = useI18n()
const rows = ref<FinancePayment[]>([]), banks = ref<FinanceBankAccount[]>([])
const loading = ref(false), busy = ref(false), failed = ref(false), uncertain = ref(false)
const form = reactive({ source: '', bank: '', amount: 0, reference: '', rate: 0, rateDate: '', confirmed: false, draft: '', postConfirmed: false, correction: '', reason: '', correctionConfirmed: false, recoveryRefund: '', recoveryOriginal: '', recoveryReason: '', recoveryConfirmed: false })
let generation = 0, key = '', fingerprint = '', correctionKey = '', correctionFingerprint = '', recoveryKey = '', recoveryFingerprint = ''
const today = () => { const d=new Date(); return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}` }
const canView = computed(() => auth.hasPermission('finance:view'))
const canCreate = computed(() => canView.value && auth.hasPermission('finance:create'))
const canPost = computed(() => canView.value && auth.hasPermission('finance:post'))
const canRecover = computed(() => canCreate.value && canPost.value)
const dateValid = (s?: string) => !!s && /^\d{4}-\d{2}-\d{2}$/.test(s) && !Number.isNaN(Date.parse(`${s}T12:00:00`)) && new Date(`${s}T12:00:00`).getDate() === Number(s.slice(-2))
const refunds = computed(() => rows.value.filter(r => ['CUSTOMER_REFUND','SUPPLIER_REFUND'].includes(r.paymentType)))
const sources = computed(() => rows.value.filter(r => r.status === 'POSTED' && ['RECEIPT','PAYMENT'].includes(r.paymentType) && r.partyType === (r.paymentType === 'RECEIPT' ? 'CUSTOMER' : 'SUPPLIER') && !!r.partyId && /^[A-Z]{3}$/.test(r.currencyCode) && /^[A-Z]{3}$/.test(r.baseCurrencyCode ?? '') && Number.isFinite(r.exchangeRate) && (r.exchangeRate ?? 0) > 0 && dateValid(r.exchangeRateDate) && dateValid(r.accountingDate) && r.accountingDate <= today()))
const selected = computed(() => sources.value.find(r => r.id === form.source))
function remaining(row: FinancePayment) {
  if (!Number.isFinite(row.amount) || !Number.isFinite(row.allocatedAmount)) return 0
  const related = refunds.value.filter(r => ['DRAFT','POSTED'].includes(r.status))
  if (related.some(r => !r.originalPaymentId && r.partyType === row.partyType && r.partyId === row.partyId && r.currencyCode === row.currencyCode)) return 0
  const occupied = related.filter(r => r.originalPaymentId === row.id)
  if (occupied.some(r => !Number.isFinite(r.amount) || r.amount < 0)) return 0
  return Math.max(0,(Math.round(row.amount*10000)-Math.round(row.allocatedAmount*10000)-occupied.reduce((n,r)=>n+Math.round(r.amount*10000),0))/10000)
}
const matchingBanks = computed(() => banks.value.filter(r => r.currencyCode === selected.value?.currencyCode))
const payload = computed<PaymentInput | null>(() => selected.value ? {paymentType:selected.value.paymentType === 'RECEIPT' ? 'CUSTOMER_REFUND' : 'SUPPLIER_REFUND', originalPaymentId:selected.value.id, partyId:selected.value.partyId, bankAccountId:form.bank || undefined,businessDate:today(),accountingDate:today(),exchangeRateDate:form.rateDate,currencyCode:selected.value.currencyCode,exchangeRate:form.rate,amount:form.amount,reference:form.reference.trim()} : null)
const ready = computed(() => props.modelValue && !loading.value && !failed.value && canView.value)
const rateValid = computed(() => !!selected.value && Number.isFinite(form.rate) && form.rate > 0 && Number(form.rate.toFixed(8)) === form.rate && (selected.value.currencyCode !== selected.value.baseCurrencyCode || form.rate === 1) && dateValid(form.rateDate) && form.rateDate >= selected.value.exchangeRateDate! && form.rateDate <= today())
const valid = computed(() => ready.value && canCreate.value && !!selected.value && !!payload.value && rateValid.value && Number.isFinite(form.amount) && form.amount > 0 && Number(form.amount.toFixed(4)) === form.amount && form.reference.trim().length <= 160 && (!form.bank || matchingBanks.value.some(r => r.id === form.bank)) && (form.amount <= remaining(selected.value!) || (uncertain.value && JSON.stringify(payload.value) === fingerprint)) && form.confirmed)
const correctionCandidates = computed(() => refunds.value.filter(r => (r.status === 'DRAFT' && canCreate.value) || (r.status === 'POSTED' && canPost.value && !!r.originalPaymentId)))
const correction = computed(() => correctionCandidates.value.find(r => r.id === form.correction))
watch(() => [form.correction,form.reason], () => {form.correctionConfirmed=false})
const draft = computed(() => refunds.value.find(r => r.id === form.draft && r.status === 'DRAFT' && !!r.originalPaymentId))
watch(() => form.source, () => { form.amount=0; form.bank=''; form.rate=selected.value?.exchangeRate ?? 0; form.rateDate=selected.value?.exchangeRateDate ?? ''; form.confirmed=false })
watch(() => [form.amount,form.bank,form.reference,form.rate,form.rateDate], () => { form.confirmed=false })
watch(() => form.draft, () => { form.postConfirmed=false })
const recoverableRefunds = computed(() => refunds.value.filter(r => r.status === 'POSTED' && !r.originalPaymentId && !r.refundExchangeJournalId && r.allocatedAmount === 0 && !r.allocations.some(a => a.status === 'ACTIVE')))
const recoveryRefund = computed(() => recoverableRefunds.value.find(r => r.id === form.recoveryRefund))
function recoveryRemaining(row: FinancePayment) {
  const linked=refunds.value.filter(r => r.originalPaymentId === row.id && ['DRAFT','POSTED'].includes(r.status))
  if(!Number.isFinite(row.amount) || !Number.isFinite(row.allocatedAmount) || linked.some(r => !Number.isFinite(r.amount) || r.amount < 0))return 0
  return (Math.round(row.amount*10000)-Math.round(row.allocatedAmount*10000)-linked.reduce((n,r)=>n+Math.round(r.amount*10000),0))/10000
}
const recoverySources = computed(() => sources.value.filter(r => {
  const refund=recoveryRefund.value
  return refund && r.paymentType === (refund.paymentType === 'CUSTOMER_REFUND' ? 'RECEIPT' : 'PAYMENT') && r.partyType === refund.partyType && r.partyId === refund.partyId && r.currencyCode === refund.currencyCode && r.baseCurrencyCode === refund.baseCurrencyCode && r.exchangeRate === refund.exchangeRate && dateValid(refund.exchangeRateDate) && r.exchangeRateDate! <= refund.exchangeRateDate! && refund.exchangeRateDate! <= refund.accountingDate && r.accountingDate <= refund.accountingDate && Number.isFinite(refund.amount) && refund.amount > 0 && recoveryRemaining(r) >= refund.amount
}))
const recoveryOriginal = computed(() => recoverySources.value.find(r => r.id === form.recoveryOriginal))
const recoveryValid = computed(() => ready.value && canRecover.value && !!recoveryRefund.value && !!recoveryOriginal.value && !!form.recoveryReason.trim() && form.recoveryReason.trim().length <= 500 && form.recoveryConfirmed)
watch(() => JSON.stringify([recoveryRefund.value,recoveryOriginal.value,recoveryOriginal.value ? recoveryRemaining(recoveryOriginal.value) : null]), () => {form.recoveryConfirmed=false})
watch(() => form.recoveryRefund, () => {form.recoveryOriginal='';form.recoveryReason='';form.recoveryConfirmed=false})
watch(() => [form.recoveryOriginal,form.recoveryReason], () => {form.recoveryConfirmed=false})
async function load() {
  const token=++generation
  if (!props.modelValue || !canView.value) { rows.value=[]; banks.value=[]; loading.value=false; return }
  loading.value=true; failed.value=false
  try {
    const [payments,accounts]=await Promise.all([listFinancePayments(),canCreate.value ? listFinanceBankAccounts() : Promise.resolve([])])
    if(token !== generation) return
    rows.value=payments; banks.value=accounts
    if (!sources.value.some(r => r.id === form.source)) form.source=''
    if (form.bank && !matchingBanks.value.some(r => r.id === form.bank)) form.bank=''
    if (!draft.value) form.draft=''
  } catch { if(token === generation) {failed.value=true;rows.value=[];banks.value=[]} }
  finally {if(token === generation)loading.value=false}
}
watch(() => [props.modelValue,auth.user?.organizationId,canView.value,canCreate.value,canPost.value], () => { Object.assign(form,{source:'',bank:'',amount:0,reference:'',rate:0,rateDate:'',confirmed:false,draft:'',postConfirmed:false,correction:'',reason:'',correctionConfirmed:false,recoveryRefund:'',recoveryOriginal:'',recoveryReason:'',recoveryConfirmed:false});recoveryKey='';recoveryFingerprint='';key='';fingerprint='';correctionKey='';correctionFingerprint='';uncertain.value=false;void load() }, {immediate:true})
onBeforeUnmount(() => {generation++})
function close(){if(!busy.value)emit('update:modelValue',false)}
async function save(){
  if(busy.value || !valid.value || !payload.value)return
  const body=payload.value,next=JSON.stringify(body),token=generation
  if(next !== fingerprint){fingerprint=next;key=globalThis.crypto.randomUUID()}
  busy.value=true;uncertain.value=false
  try {await createFinancePayment(body,key);if(token === generation){form.amount=0;form.confirmed=false}}
  catch{if(token === generation)uncertain.value=true}
  finally{if(token === generation){const refresh=generation+1;await load();if(generation === refresh)emit('changed')}busy.value=false}
}
async function post(){
  if(busy.value || !ready.value || !canPost.value || !draft.value || !form.postConfirmed)return
  const row=draft.value,token=generation;busy.value=true;uncertain.value=false
  try{await postFinancePayment(row);if(token === generation){form.draft='';form.postConfirmed=false}}
  catch{if(token === generation)uncertain.value=true}
  finally{if(token === generation){const refresh=generation+1;await load();if(generation === refresh)emit('changed')}busy.value=false}
}
async function correct(){
  if(busy.value || !ready.value || !correction.value || !form.correctionConfirmed || !form.reason.trim() || form.reason.trim().length > 500 || correction.value.accountingDate > today())return
  const row=correction.value, action=row.status === 'DRAFT' ? 'cancel' : 'reverse',body={version:row.version,accountingDate:today(),reason:form.reason.trim()},token=generation
  const next=JSON.stringify({id:row.id,action,...body})
  if(next !== correctionFingerprint){correctionFingerprint=next;correctionKey=globalThis.crypto.randomUUID()}
  busy.value=true;uncertain.value=false
  try{await correctFinanceRefund(row.id,action,body,correctionKey);if(token === generation){form.correction='';form.reason='';form.correctionConfirmed=false}}
  catch{if(token === generation)uncertain.value=true}
  finally{if(token === generation){const refresh=generation+1;await load();if(generation === refresh)emit('changed')}busy.value=false}
}
async function recoverSource(){
  if(busy.value || !recoveryValid.value || !recoveryRefund.value || !recoveryOriginal.value)return
  const row=recoveryRefund.value,original=recoveryOriginal.value,body={originalPaymentId:original.id,version:row.version,originalVersion:original.version,reason:form.recoveryReason.trim()},token=generation
  const next=JSON.stringify({id:row.id,...body})
  if(next !== recoveryFingerprint){recoveryFingerprint=next;recoveryKey=globalThis.crypto.randomUUID()}
  busy.value=true;uncertain.value=false
  try{await recoverFinanceRefundSource(row.id,body,recoveryKey);if(token === generation){form.recoveryRefund='';form.recoveryOriginal='';form.recoveryReason='';form.recoveryConfirmed=false}}
  catch{if(token === generation)uncertain.value=true}
  finally{if(token === generation){const refresh=generation+1;await load();if(generation === refresh)emit('changed')}busy.value=false}
}
</script>
<template>
  <el-dialog :model-value="modelValue" :title="t('finance.refund.title')" width="min(1040px, calc(100vw - 32px))" destroy-on-close :show-close="!busy" :close-on-click-modal="!busy" :close-on-press-escape="!busy" @update:model-value="close">
    <p>{{ t('finance.refund.terms') }}</p>
    <el-alert v-if="failed" :title="t('finance.refund.loadFailed')" type="error" :closable="false" />
    <el-alert v-if="uncertain" :title="t('finance.refund.uncertain')" type="warning" :closable="false" />
    <el-button :disabled="busy || loading" @click="load">{{ t('finance.m4.retry') }}</el-button>
    <el-form v-if="ready && canCreate" :key="busy ? 'pending' : 'ready'" :disabled="busy" label-position="top">
      <el-form-item :label="t('finance.refund.source')"><el-select v-model="form.source" class="full-width" filterable><el-option v-for="row in sources" :key="row.id" :value="row.id" :label="`${row.number} · ${row.partyId} · ${remaining(row)} ${row.currencyCode}`" /></el-select></el-form-item>
      <p v-if="selected">{{ t('finance.refund.basis',{amount:remaining(selected),currency:selected.currencyCode,rate:selected.exchangeRate,base:selected.baseCurrencyCode,date:selected.exchangeRateDate}) }}</p>
      <el-form-item :label="t('finance.amount')"><el-input-number v-model="form.amount" :min="0" :precision="4" :controls="false" class="full-width" /></el-form-item>
      <el-form-item :label="t('finance.bankEntry.account')"><el-select v-model="form.bank" clearable class="full-width" :placeholder="t('finance.foreign.cash')"><el-option v-for="row in matchingBanks" :key="row.id" :value="row.id" :label="`${row.code} · ${row.name} · ${row.currencyCode}`" /></el-select></el-form-item>
      <el-form-item :label="t('finance.m4.reference')"><el-input v-model="form.reference" :maxlength="160" /></el-form-item>
      <el-form-item v-if="selected" :label="t('finance.refund.settlementRate')"><el-input-number v-model="form.rate" :min="0" :precision="8" :controls="false" class="full-width" /></el-form-item>
      <el-form-item v-if="selected" :label="t('finance.refund.settlementRateDate')"><el-date-picker v-model="form.rateDate" type="date" value-format="YYYY-MM-DD" class="full-width" /></el-form-item>
      <p v-if="selected">{{ t('finance.refund.exchangeTerms') }}</p>
      <el-checkbox v-model="form.confirmed">{{ t('finance.refund.confirmDraft') }}</el-checkbox>
      <el-button :disabled="busy || !valid" :loading="busy" @click="save">{{ t('finance.refund.save') }}</el-button>
    </el-form>
    <div v-if="ready">
      <p>{{ t('finance.refund.history') }}</p>
      <div class="history-scroll"><table class="refund-history"><thead><tr><th>{{ t('finance.number') }}</th><th>{{ t('finance.refund.source') }}</th><th>{{ t('finance.amount') }}</th><th>{{ t('finance.statusLabel') }}</th><th>{{ t('finance.refund.exchangeHistory') }}</th><th>{{ t('finance.refund.correctionHistory') }}</th></tr></thead><tbody><tr v-for="row in refunds" :key="row.id"><td>{{ row.number }}</td><td>{{ rows.find(r => r.id === row.originalPaymentId)?.number ?? row.originalPaymentId ?? '—' }}</td><td>{{ row.amount }} {{ row.currencyCode }}</td><td>{{ row.status }}</td><td>{{ row.exchangeRate }} · {{ row.exchangeRateDate }}<br />{{ t('finance.refund.carrying') }} {{ row.refundSourceBaseAmount ?? '—' }} {{ row.baseCurrencyCode }}<br />{{ t('finance.refund.difference') }} {{ row.realizedExchangeDifference ?? '—' }} {{ row.baseCurrencyCode }}<br />{{ row.refundExchangeJournalId }}</td><td><p v-if="row.sourceRecovery">{{ t('finance.refund.recoveryHistory') }} · {{ row.sourceRecovery.reason }} · {{ row.sourceRecovery.actorUserId }} · {{ row.sourceRecovery.createdAt }}<br />{{ row.sourceRecovery.originalJournalEntryId }}<br />{{ row.sourceRecovery.refundJournalEntryId }}</p><template v-if="row.correction">{{ row.correction.action }} · {{ row.correction.reason }} · {{ row.correction.accountingDate }} · {{ row.correction.actorUserId }}<br />{{ t('finance.refund.paymentReversal') }} {{ row.correction.journalEntryId ?? '—' }}<br /><template v-if="row.correction.exchangeJournalEntryId">{{ t('finance.refund.exchangeReversal') }} {{ row.correction.exchangeJournalEntryId }}</template></template><template v-else-if="!row.sourceRecovery">—</template></td></tr></tbody></table></div>
      <el-form v-if="canPost" :key="busy ? 'post-pending' : 'post-ready'" :disabled="busy" label-position="top">
        <el-form-item :label="t('finance.refund.draft')"><el-select v-model="form.draft" class="full-width"><el-option v-for="row in refunds.filter(r => r.status === 'DRAFT' && !!r.originalPaymentId)" :key="row.id" :value="row.id" :label="`${row.number} · ${row.amount} ${row.currencyCode}`" /></el-select></el-form-item>
        <el-checkbox v-model="form.postConfirmed">{{ t('finance.refund.confirmPost') }}</el-checkbox><el-button :disabled="busy || !draft || !form.postConfirmed" :loading="busy" @click="post">{{ t('finance.refund.post') }}</el-button>
      </el-form>
      <el-form v-if="canCreate || canPost" :key="busy ? 'correction-pending' : 'correction-ready'" :disabled="busy" label-position="top">
        <p>{{ t('finance.refund.correctionTerms') }}</p>
        <el-form-item :label="t('finance.refund.correctionSelect')"><el-select v-model="form.correction" class="full-width"><el-option v-for="row in correctionCandidates" :key="row.id" :value="row.id" :label="`${row.number} · ${row.status} · ${row.amount} ${row.currencyCode}`" /></el-select></el-form-item>
        <p v-if="correction">{{ t(correction.status === 'DRAFT' ? 'finance.refund.cancelAction' : 'finance.refund.reverseAction') }} · {{ today() }}</p>
        <el-form-item :label="t('finance.refund.correctionReason')"><el-input v-model="form.reason" :maxlength="500" /></el-form-item>
        <el-checkbox v-model="form.correctionConfirmed">{{ t('finance.refund.correctionConfirm') }}</el-checkbox>
        <el-button :disabled="busy || !correction || !form.reason.trim() || form.reason.trim().length > 500 || !form.correctionConfirmed || correction.accountingDate > today()" :loading="busy" @click="correct">{{ t('finance.refund.correct') }}</el-button>
      </el-form>
      <el-form v-if="canRecover && recoverableRefunds.length" :key="busy ? 'recovery-pending' : 'recovery-ready'" :disabled="busy" label-position="top">
        <p>{{ t('finance.refund.recoveryTerms') }}</p>
        <el-form-item :label="t('finance.refund.recoveryRefund')"><el-select v-model="form.recoveryRefund" class="full-width"><el-option v-for="row in recoverableRefunds" :key="row.id" :value="row.id" :label="`${row.number} · ${row.amount} ${row.currencyCode}`" /></el-select></el-form-item>
        <el-form-item :label="t('finance.refund.recoveryOriginal')"><el-select v-model="form.recoveryOriginal" class="full-width"><el-option v-for="row in recoverySources" :key="row.id" :value="row.id" :label="`${row.number} · ${recoveryRemaining(row)} ${row.currencyCode} · ${row.exchangeRate}`" /></el-select></el-form-item>
        <el-form-item :label="t('finance.refund.recoveryReason')"><el-input v-model="form.recoveryReason" :maxlength="500" /></el-form-item>
        <el-checkbox v-model="form.recoveryConfirmed">{{ t('finance.refund.recoveryConfirm') }}</el-checkbox>
        <el-button :disabled="busy || !recoveryValid" :loading="busy" @click="recoverSource">{{ t('finance.refund.recover') }}</el-button>
      </el-form>
    </div>
    <template #footer><el-button :disabled="busy" @click="close">{{ t('finance.bankEntry.close') }}</el-button></template>
  </el-dialog>
</template>
<style scoped>.refund-history{width:100%;font-size:12px;border-collapse:collapse;margin:12px 0}.refund-history th,.refund-history td{text-align:left;padding:8px;border-bottom:1px solid var(--el-border-color);overflow-wrap:anywhere}.refund-history th:nth-child(-n+4),.refund-history td:nth-child(-n+4){white-space:nowrap}.refund-history td:nth-child(n+5){min-width:200px}.history-scroll{overflow-x:auto}.full-width{width:100%}</style>
