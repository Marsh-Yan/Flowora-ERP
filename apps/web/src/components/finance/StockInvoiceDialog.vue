<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import { getOrganizationSettings } from '@/api/master-data'
import { createFinanceInvoice, listStockInvoiceSources, type StockInvoiceSource, type InvoiceInput } from '@/api/finance-v2'
const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; changed: [] }>()
const auth = useAuthStore(), { t, locale } = useI18n()
const rows = ref<StockInvoiceSource[]>([]), currency = ref(''), sourceId = ref(''), quantity = ref(1), rate = ref(0), rateDate = ref('')
const loading = ref(false), busy = ref(false), failed = ref(false), uncertain = ref(false)
let generation = 0, requestKey = '', fingerprint = ''
const allowed = computed(() => auth.hasPermission('finance:view') && auth.hasPermission('finance:invoice'))
const ready = computed(() => props.modelValue && allowed.value && !loading.value && !failed.value && !!currency.value)
const candidates = computed(() => rows.value.filter(row => /^[A-Z]{3}$/.test(row.currencyCode) && Number.isFinite(row.remainingQuantity) && row.remainingQuantity > 0 && Number.isFinite(row.unitPrice) && row.unitPrice > 0 && row.discountRate >= 0 && row.discountRate <= 100 && row.taxRate >= 0 && row.taxRate <= 100))
const source = computed(() => candidates.value.find(row => row.sourceLineId === sourceId.value))
const positiveScale = (n: number, digits: number) => Number.isFinite(n) && n > 0 && Number(n.toFixed(digits)) === n
const rateDateValid = computed(() => { const d = new Date(`${rateDate.value}T12:00:00`); return /^\d{4}-\d{2}-\d{2}$/.test(rateDate.value) && !Number.isNaN(d.getTime()) && `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}` === rateDate.value && rateDate.value <= date() })
const selectedRate = computed(() => source.value?.currencyCode === currency.value ? 1 : rate.value)
const valid = computed(() => ready.value && !!source.value && positiveScale(quantity.value,4) && quantity.value <= source.value.remainingQuantity && positiveScale(selectedRate.value,8) && (source.value.currencyCode === currency.value || rateDateValid.value))
const date = () => { const d = new Date(); return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}` }
function money(value: number) { return value.toLocaleString(locale.value, { maximumFractionDigits: 4 }) }
function close() { if (!busy.value) emit('update:modelValue',false) }
watch(sourceId, () => { quantity.value = source.value?.remainingQuantity ?? 1; rate.value = 0; rateDate.value = date(); requestKey = ''; fingerprint = '' })
async function load() {
  const token = ++generation
  if (!props.modelValue || !allowed.value) { rows.value = []; currency.value = ''; loading.value = false; return }
  loading.value = true; failed.value = false
  try {
    const [sources,settings] = await Promise.all([listStockInvoiceSources(),getOrganizationSettings()])
    if (token !== generation) return
    rows.value = sources; currency.value = settings.baseCurrencyCode
    if (!source.value) sourceId.value = ''
  } catch { if (token === generation) { failed.value = true; rows.value = []; currency.value = '' } }
  finally { if (token === generation) loading.value = false }
}
watch(() => [props.modelValue,auth.user?.organizationId,allowed.value], () => { sourceId.value = ''; quantity.value = 1; rate.value = 0; rateDate.value = date(); requestKey = ''; fingerprint = ''; uncertain.value = false; void load() }, { immediate: true })
onBeforeUnmount(() => { generation++ })
async function save() {
  if (busy.value || !valid.value || !source.value) return
  const row = source.value, today = date(), due = new Date(`${today}T12:00:00`); due.setDate(due.getDate()+30)
  const dueDate = `${due.getFullYear()}-${String(due.getMonth()+1).padStart(2,'0')}-${String(due.getDate()).padStart(2,'0')}`
  const net = Math.round(quantity.value * row.unitPrice * (1-row.discountRate/100) * 10000) / 10000
  const payload: InvoiceInput = { documentType: row.documentType, partyId: row.partyId, businessDate: today, accountingDate: today, exchangeRateDate: row.currencyCode === currency.value ? today : rateDate.value, dueDate, currencyCode: row.currencyCode, exchangeRate: selectedRate.value, lines: [{ itemId: row.itemId, description: row.description, quantity: quantity.value, unitPrice: row.unitPrice, discountRate: row.discountRate, taxRate: row.taxRate, sources: [{ sourceType: row.sourceType, sourceId: row.sourceId, sourceLineId: row.sourceLineId, quantity: quantity.value, amount: net }] }] }
  const next = JSON.stringify(payload)
  if (next !== fingerprint) { requestKey = globalThis.crypto.randomUUID(); fingerprint = next }
  busy.value = true; uncertain.value = false
  const token = generation
  try { await createFinanceInvoice(payload,requestKey); if (token === generation) { ElMessage.success(t('finance.stockInvoice.saved')); emit('update:modelValue',false) } }
  catch { if (token === generation) uncertain.value = true }
  finally { if (token === generation) { await load(); emit('changed') } busy.value = false }
}
</script>
<template>
  <el-dialog :model-value="modelValue" :title="t('finance.stockInvoice.title')" width="min(640px, calc(100vw - 32px))" destroy-on-close :show-close="!busy" :close-on-click-modal="!busy" :close-on-press-escape="!busy" @update:model-value="close">
    <el-alert v-if="failed" :title="t('finance.stockInvoice.loadFailed')" type="error" :closable="false" /><el-alert v-if="uncertain" :title="t('finance.stockInvoice.failed')" type="error" :closable="false" />
    <el-button :disabled="loading || busy" @click="load">{{ t('finance.m4.retry') }}</el-button>
    <el-form v-if="ready" :key="busy ? 'pending' : 'ready'" :disabled="busy" label-position="top">
      <p>{{ t('finance.stockInvoice.terms') }}</p><p>{{ t('finance.m4.baseCurrency',{currency}) }}</p>
      <el-form-item :label="t('finance.stockInvoice.source')"><el-select v-model="sourceId" class="full-width" filterable><el-option v-for="row in candidates" :key="row.sourceLineId" :value="row.sourceLineId" :label="`${row.sourceNumber} · ${row.orderNumber} · ${row.partyName} · ${row.description} · ${row.currencyCode} · ${t('finance.stockInvoice.remaining')}: ${money(row.remainingQuantity)}`" /></el-select></el-form-item>
      <template v-if="source"><p>{{ t('finance.stockInvoice.remaining') }}: {{ money(source.remainingQuantity) }} · {{ t('finance.m4.unitPrice') }}: {{ money(source.unitPrice) }} {{ source.currencyCode }} · {{ t('finance.m4.taxRate') }}: {{ money(source.taxRate) }}%</p><el-form-item :label="t('finance.stockInvoice.quantity')"><el-input-number v-model="quantity" :min="0.0001" :max="source.remainingQuantity" :precision="4" class="full-width" /></el-form-item></template>
      <template v-if="source && source.currencyCode !== currency"><p>{{ t('finance.foreign.direction',{currency:source.currencyCode,base:currency}) }}</p><p>{{ t('finance.stockInvoice.fxTerms') }}</p><el-form-item :label="t('finance.foreign.rate')"><el-input-number v-model="rate" :min="0" :precision="8" :controls="false" class="full-width" /></el-form-item><el-form-item :label="t('finance.foreign.rateDate')"><el-date-picker v-model="rateDate" type="date" value-format="YYYY-MM-DD" :clearable="false" class="full-width" /></el-form-item></template>
      <p v-if="!candidates.length">{{ t('finance.stockInvoice.empty') }}</p>
    </el-form>
    <template #footer><el-button :disabled="busy" @click="close">{{ t('finance.bankEntry.close') }}</el-button><el-button type="primary" :loading="busy" :disabled="busy || !valid" @click="save">{{ t('projects.save') }}</el-button></template>
  </el-dialog>
</template>
