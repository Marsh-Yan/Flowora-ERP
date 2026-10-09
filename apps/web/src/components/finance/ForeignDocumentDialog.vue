<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import { getOrganizationSettings, listMasterData, type MasterDataRecord, type MasterDataResource } from '@/api/master-data'
import { createFinanceInvoice, createFinancePayment, listFinanceBankAccounts, type FinanceBankAccount, type InvoiceInput, type PaymentInput } from '@/api/finance-v2'

const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; changed: [] }>()
const auth = useAuthStore(), { t } = useI18n()
type Kind = 'SALES_INVOICE' | 'SUPPLIER_INVOICE' | 'RECEIPT' | 'PAYMENT'
const today = () => { const d = new Date(); return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}` }
const form = reactive({ kind: 'SALES_INVOICE' as Kind, partyId: '', currency: '', rate: 0, rateDate: today(), description: '', quantity: 1, unitPrice: 0, taxRate: 0, amount: 0, bankAccountId: '', reference: '' })
const base = ref(''), currencies = ref<MasterDataRecord[]>([]), customers = ref<MasterDataRecord[]>([]), suppliers = ref<MasterDataRecord[]>([]), banks = ref<FinanceBankAccount[]>([])
const loading = ref(false), busy = ref(false), failed = ref(false), uncertain = ref(false)
let generation = 0, requestKey = '', fingerprint = ''
const invoice = computed(() => form.kind.endsWith('INVOICE'))
const allowed = computed(() => auth.hasPermission('finance:view') && auth.hasPermission('master:view') && (auth.hasPermission('finance:invoice') || auth.hasPermission('finance:create')))
const kinds = computed<Kind[]>(() => [...(auth.hasPermission('finance:invoice') ? ['SALES_INVOICE', 'SUPPLIER_INVOICE'] as Kind[] : []), ...(auth.hasPermission('finance:create') ? ['RECEIPT', 'PAYMENT'] as Kind[] : [])])
const parties = computed(() => ['SALES_INVOICE', 'RECEIPT'].includes(form.kind) ? customers.value : suppliers.value)
const foreign = computed(() => currencies.value.filter(row => row.active && /^[A-Z]{3}$/.test(row.code) && row.code !== base.value))
const matchingBanks = computed(() => banks.value.filter(row => row.currencyCode === form.currency))
const scale = (n: number, precision: number) => Number.isFinite(n) && n > 0 && Number(n.toFixed(precision)) === n
const rateDateValid = computed(() => { if (!/^\d{4}-\d{2}-\d{2}$/.test(form.rateDate)) return false; const d = new Date(`${form.rateDate}T12:00:00`); return !Number.isNaN(d.getTime()) && `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}` === form.rateDate && form.rateDate <= today() })
const valid = computed(() => props.modelValue && allowed.value && !loading.value && !failed.value && !!base.value && kinds.value.includes(form.kind) && foreign.value.some(row => row.code === form.currency) && parties.value.some(row => row.id === form.partyId && row.active) && scale(form.rate,8) && rateDateValid.value && (invoice.value ? !!form.description.trim() && form.description.length <= 240 && scale(form.quantity,4) && scale(form.unitPrice,4) && Number.isFinite(form.taxRate) && form.taxRate >= 0 && form.taxRate <= 100 : scale(form.amount,4) && form.reference.length <= 160 && (!form.bankAccountId || matchingBanks.value.some(row => row.id === form.bankAccountId))))
watch(() => form.kind, () => { form.partyId = ''; form.bankAccountId = ''; form.rate = 0 })
watch(() => form.currency, () => { form.bankAccountId = ''; form.rate = 0 })
async function all(resource: MasterDataResource) {
  const rows: MasterDataRecord[] = []
  for (let page = 0; page < 100; page++) { const result = await listMasterData<MasterDataRecord>(resource,'',page,100); rows.push(...result.content); if (page + 1 >= result.totalPages) return rows.filter(row => row.active) }
  throw new Error('Master data pagination incomplete')
}
function close() { if (!busy.value) emit('update:modelValue',false) }
async function load() {
  const token = ++generation
  if (!props.modelValue || !allowed.value) { base.value = ''; currencies.value = []; customers.value = []; suppliers.value = []; banks.value = []; loading.value = false; return }
  loading.value = true; failed.value = false
  try {
    const [settings, currencyRows, customerRows, supplierRows, bankRows] = await Promise.all([getOrganizationSettings(), all('currencies'), all('customers'), all('suppliers'), listFinanceBankAccounts()])
    if (token !== generation) return
    base.value = settings.baseCurrencyCode; currencies.value = currencyRows; customers.value = customerRows; suppliers.value = supplierRows; banks.value = bankRows
    if (!foreign.value.some(row => row.code === form.currency)) form.currency = ''
    if (!parties.value.some(row => row.id === form.partyId)) form.partyId = ''
    if (form.bankAccountId && !matchingBanks.value.some(row => row.id === form.bankAccountId)) form.bankAccountId = ''
  } catch { if (token === generation) { failed.value = true; base.value = ''; currencies.value = []; banks.value = [] } }
  finally { if (token === generation) loading.value = false }
}
watch(() => [props.modelValue,auth.user?.organizationId,allowed.value], () => { Object.assign(form,{ kind:kinds.value[0] ?? 'SALES_INVOICE',partyId:'',currency:'',rate:0,rateDate:today(),description:'',quantity:1,unitPrice:0,taxRate:0,amount:0,bankAccountId:'',reference:'' }); requestKey = ''; fingerprint = ''; uncertain.value = false; void load() }, { immediate:true })
onBeforeUnmount(() => { generation++ })
async function save() {
  if (busy.value || !valid.value) return
  const date = today(), due = new Date(`${date}T12:00:00`); due.setDate(due.getDate()+30)
  const dueDate = `${due.getFullYear()}-${String(due.getMonth()+1).padStart(2,'0')}-${String(due.getDate()).padStart(2,'0')}`
  const shared = { partyId:form.partyId,businessDate:date,accountingDate:date,exchangeRateDate:form.rateDate,currencyCode:form.currency,exchangeRate:form.rate }
  const payload: InvoiceInput | PaymentInput = invoice.value ? { ...shared,documentType:form.kind as 'SALES_INVOICE'|'SUPPLIER_INVOICE',dueDate,lines:[{description:form.description.trim(),quantity:form.quantity,unitPrice:form.unitPrice,discountRate:0,taxRate:form.taxRate,sources:[]}] } : { ...shared,paymentType:form.kind as 'RECEIPT'|'PAYMENT',bankAccountId:form.bankAccountId || undefined,amount:form.amount,reference:form.reference }
  const next = JSON.stringify(payload)
  if (next !== fingerprint) { requestKey = globalThis.crypto.randomUUID(); fingerprint = next }
  const isInvoice = invoice.value, token = generation
  busy.value = true; uncertain.value = false
  try { if (isInvoice) await createFinanceInvoice(payload as InvoiceInput,requestKey); else await createFinancePayment(payload as PaymentInput,requestKey); if (token === generation) { ElMessage.success(t('finance.foreign.saved')); emit('update:modelValue',false) } }
  catch { if (token === generation) uncertain.value = true }
  finally { if (token === generation) { await load(); emit('changed') } busy.value = false }
}
</script>
<template>
  <el-dialog :model-value="modelValue" :title="t('finance.foreign.title')" width="min(660px, calc(100vw - 32px))" destroy-on-close :show-close="!busy" :close-on-click-modal="!busy" :close-on-press-escape="!busy" @update:model-value="close">
    <p>{{ t('finance.foreign.terms') }}</p>
    <el-alert v-if="failed" :title="t('finance.foreign.loadFailed')" type="error" :closable="false" /><el-alert v-if="uncertain" :title="t('finance.foreign.failed')" type="error" :closable="false" />
    <el-button :disabled="loading || busy" @click="load">{{ t('finance.m4.retry') }}</el-button>
    <el-form v-if="!loading && !failed && allowed && base" :key="busy ? 'pending' : 'ready'" :disabled="busy" label-position="top">
      <el-form-item :label="t('finance.m4.type')"><el-select v-model="form.kind" class="full-width"><el-option v-for="kind in kinds" :key="kind" :value="kind" :label="t(`finance.foreign.${kind}`)" /></el-select></el-form-item>
      <el-form-item :label="t('finance.m4.party')"><el-select v-model="form.partyId" class="full-width" filterable><el-option v-for="row in parties" :key="row.id" :value="row.id" :label="`${row.code} · ${row.name}`" /></el-select></el-form-item>
      <el-form-item :label="t('finance.currency')"><el-select v-model="form.currency" class="full-width"><el-option v-for="row in foreign" :key="row.id" :value="row.code" :label="`${row.code} · ${row.name}`" /></el-select></el-form-item>
      <p>{{ t('finance.foreign.direction',{currency:form.currency || '—',base}) }}</p>
      <el-form-item :label="t('finance.foreign.rate')"><el-input-number v-model="form.rate" :min="0" :precision="8" :controls="false" class="full-width" /></el-form-item>
      <el-form-item :label="t('finance.foreign.rateDate')"><el-date-picker v-model="form.rateDate" type="date" value-format="YYYY-MM-DD" :clearable="false" class="full-width" /></el-form-item>
      <template v-if="invoice"><el-form-item :label="t('finance.m4.description')"><el-input v-model="form.description" :maxlength="240" /></el-form-item><el-form-item :label="t('finance.m4.quantity')"><el-input-number v-model="form.quantity" :min="0.0001" :precision="4" class="full-width" /></el-form-item><el-form-item :label="t('finance.m4.unitPrice')"><el-input-number v-model="form.unitPrice" :min="0" :precision="4" class="full-width" /></el-form-item><el-form-item :label="t('finance.m4.taxRate')"><el-input-number v-model="form.taxRate" :min="0" :max="100" :precision="4" class="full-width" /></el-form-item></template>
      <template v-else><el-form-item :label="t('finance.amount')"><el-input-number v-model="form.amount" :min="0" :precision="4" class="full-width" /></el-form-item><el-form-item :label="t('finance.bankEntry.account')"><el-select v-model="form.bankAccountId" class="full-width" clearable :placeholder="t('finance.foreign.cash')"><el-option v-for="row in matchingBanks" :key="row.id" :value="row.id" :label="`${row.code} · ${row.name} · ${row.currencyCode}`" /></el-select></el-form-item><el-form-item :label="t('finance.m4.reference')"><el-input v-model="form.reference" :maxlength="160" /></el-form-item></template>
      <p v-if="!foreign.length">{{ t('finance.foreign.empty') }}</p>
    </el-form>
    <template #footer><el-button :disabled="busy" @click="close">{{ t('finance.bankEntry.close') }}</el-button><el-button type="primary" :loading="busy" :disabled="busy || !valid" @click="save">{{ t('projects.save') }}</el-button></template>
  </el-dialog>
</template>
