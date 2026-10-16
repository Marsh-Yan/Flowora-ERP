<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import { createFinancePayment, listFinancePayments, listFinanceBankAccounts, postFinancePayment, type FinancePayment, type FinanceBankAccount, type PaymentInput } from '@/api/finance-v2'
const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; changed: [] }>()
const auth = useAuthStore(), { t } = useI18n()
const rows = ref<FinancePayment[]>([]), banks = ref<FinanceBankAccount[]>([])
const loading = ref(false), busy = ref(false), failed = ref(false), uncertain = ref(false)
const form = reactive({ source: '', bank: '', amount: 0, reference: '', confirmed: false, draft: '', postConfirmed: false })
let generation = 0, key = '', fingerprint = ''
const today = () => { const d=new Date(); return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}` }
const canView = computed(() => auth.hasPermission('finance:view'))
const canCreate = computed(() => canView.value && auth.hasPermission('finance:create'))
const canPost = computed(() => canView.value && auth.hasPermission('finance:post'))
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
const payload = computed<PaymentInput | null>(() => selected.value ? {paymentType:selected.value.paymentType === 'RECEIPT' ? 'CUSTOMER_REFUND' : 'SUPPLIER_REFUND', originalPaymentId:selected.value.id, partyId:selected.value.partyId, bankAccountId:form.bank || undefined,businessDate:today(),accountingDate:today(),exchangeRateDate:selected.value.exchangeRateDate!,currencyCode:selected.value.currencyCode,exchangeRate:selected.value.exchangeRate!,amount:form.amount,reference:form.reference.trim()} : null)
const ready = computed(() => props.modelValue && !loading.value && !failed.value && canView.value)
const valid = computed(() => ready.value && canCreate.value && !!selected.value && !!payload.value && Number.isFinite(form.amount) && form.amount > 0 && Number(form.amount.toFixed(4)) === form.amount && form.reference.trim().length <= 160 && (!form.bank || matchingBanks.value.some(r => r.id === form.bank)) && (form.amount <= remaining(selected.value!) || (uncertain.value && JSON.stringify(payload.value) === fingerprint)) && form.confirmed)
const draft = computed(() => refunds.value.find(r => r.id === form.draft && r.status === 'DRAFT' && !!r.originalPaymentId))
watch(() => form.source, () => { form.amount=0; form.bank=''; form.confirmed=false })
watch(() => [form.amount,form.bank,form.reference], () => { form.confirmed=false })
watch(() => form.draft, () => { form.postConfirmed=false })
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
watch(() => [props.modelValue,auth.user?.organizationId,canView.value,canCreate.value,canPost.value], () => { Object.assign(form,{source:'',bank:'',amount:0,reference:'',confirmed:false,draft:'',postConfirmed:false});key='';fingerprint='';uncertain.value=false;void load() }, {immediate:true})
onBeforeUnmount(() => {generation++})
function close(){if(!busy.value)emit('update:modelValue',false)}
async function save(){
  if(busy.value || !valid.value || !payload.value)return
  const body=payload.value,next=JSON.stringify(body),token=generation
  if(next !== fingerprint){fingerprint=next;key=globalThis.crypto.randomUUID()}
  busy.value=true;uncertain.value=false
  try {await createFinancePayment(body,key);if(token === generation){form.amount=0;form.confirmed=false}}
  catch{if(token === generation)uncertain.value=true}
  finally{if(token === generation){await load();emit('changed')}busy.value=false}
}
async function post(){
  if(busy.value || !ready.value || !canPost.value || !draft.value || !form.postConfirmed)return
  const row=draft.value,token=generation;busy.value=true;uncertain.value=false
  try{await postFinancePayment(row);if(token === generation){form.draft='';form.postConfirmed=false}}
  catch{if(token === generation)uncertain.value=true}
  finally{if(token === generation){await load();emit('changed')}busy.value=false}
}
</script>
<template>
  <el-dialog :model-value="modelValue" :title="t('finance.refund.title')" width="min(740px, calc(100vw - 32px))" destroy-on-close :show-close="!busy" :close-on-click-modal="!busy" :close-on-press-escape="!busy" @update:model-value="close">
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
      <el-checkbox v-model="form.confirmed">{{ t('finance.refund.confirmDraft') }}</el-checkbox>
      <el-button :disabled="busy || !valid" :loading="busy" @click="save">{{ t('finance.refund.save') }}</el-button>
    </el-form>
    <div v-if="ready">
      <p>{{ t('finance.refund.history') }}</p>
      <table class="refund-history"><thead><tr><th>{{ t('finance.number') }}</th><th>{{ t('finance.refund.source') }}</th><th>{{ t('finance.amount') }}</th><th>{{ t('finance.statusLabel') }}</th></tr></thead><tbody><tr v-for="row in refunds" :key="row.id"><td>{{ row.number }}</td><td>{{ rows.find(r => r.id === row.originalPaymentId)?.number ?? row.originalPaymentId ?? '—' }}</td><td>{{ row.amount }} {{ row.currencyCode }}</td><td>{{ row.status }}</td></tr></tbody></table>
      <el-form v-if="canPost" :key="busy ? 'post-pending' : 'post-ready'" :disabled="busy" label-position="top">
        <el-form-item :label="t('finance.refund.draft')"><el-select v-model="form.draft" class="full-width"><el-option v-for="row in refunds.filter(r => r.status === 'DRAFT' && !!r.originalPaymentId)" :key="row.id" :value="row.id" :label="`${row.number} · ${row.amount} ${row.currencyCode}`" /></el-select></el-form-item>
        <el-checkbox v-model="form.postConfirmed">{{ t('finance.refund.confirmPost') }}</el-checkbox><el-button :disabled="busy || !draft || !form.postConfirmed" :loading="busy" @click="post">{{ t('finance.refund.post') }}</el-button>
      </el-form>
    </div>
    <template #footer><el-button :disabled="busy" @click="close">{{ t('finance.bankEntry.close') }}</el-button></template>
  </el-dialog>
</template>
<style scoped>.refund-history{width:100%;font-size:12px;border-collapse:collapse;margin:12px 0}.refund-history th,.refund-history td{text-align:left;padding:8px;border-bottom:1px solid var(--el-border-color);overflow-wrap:anywhere}.full-width{width:100%}</style>
