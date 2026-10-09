<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import { getOrganizationSettings } from '@/api/master-data'
import { createFinanceInvoice, getFinanceInvoice, listFinanceInvoices, type FinanceInvoice, type FinanceInvoiceDetail, type InvoiceInput } from '@/api/finance-v2'

const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; changed: [] }>()
const auth = useAuthStore(), { t, locale } = useI18n()
const rows = ref<FinanceInvoice[]>([]), currency = ref(''), originalId = ref(''), lineId = ref('')
const detail = ref<FinanceInvoiceDetail | null>(null), quantity = ref(1)
const loading = ref(false), reading = ref(false), busy = ref(false), failed = ref(false), uncertain = ref(false)
let generation = 0, detailGeneration = 0, requestKey = '', fingerprint = ''
const allowed = computed(() => auth.hasPermission('finance:view') && auth.hasPermission('finance:invoice'))
const openAmount = (row: FinanceInvoice) => row.totalAmount - row.allocatedAmount - row.creditedAmount
const eligible = (row: FinanceInvoice) => row.status === 'POSTED' && ['SALES_INVOICE', 'SUPPLIER_INVOICE'].includes(row.documentType) && /^[A-Z]{3}$/.test(row.currencyCode) && Number.isFinite(openAmount(row)) && openAmount(row) > 0
const candidates = computed(() => rows.value.filter(eligible))
const ready = computed(() => props.modelValue && allowed.value && !loading.value && !reading.value && !failed.value && !!currency.value)
const lines = computed(() => detail.value?.lines.filter(row => Number.isFinite(row.creditedQuantity) && row.quantity > (row.creditedQuantity ?? 0) && Number.isFinite(row.discountRate) && row.discountRate! >= 0 && row.discountRate! <= 100 && row.unitPrice > 0 && row.taxRate >= 0 && row.taxRate <= 100 && !!row.reversalAccountCode) ?? [])
const line = computed(() => lines.value.find(row => row.id === lineId.value))
const amount = computed(() => line.value ? Math.round(Math.round(quantity.value * line.value.unitPrice * (1 - line.value.discountRate! / 100) * 10000) * (1 + line.value.taxRate / 100)) / 10000 : 0)
const remaining = computed(() => line.value ? line.value.quantity - line.value.creditedQuantity! : 0)
const originalRateValid = computed(() => { const row = detail.value; if (!row || !Number.isFinite(row.exchangeRate) || row.exchangeRate! <= 0 || Number(row.exchangeRate!.toFixed(8)) !== row.exchangeRate) return false; if (row.currencyCode === currency.value) return row.exchangeRate === 1; const value = row.exchangeRateDate ?? '', d = new Date(`${value}T12:00:00`); return row.baseCurrencyCode === currency.value && /^\d{4}-\d{2}-\d{2}$/.test(value) && !Number.isNaN(d.getTime()) && `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}` === value && value <= date() })
const valid = computed(() => ready.value && detail.value && eligible(detail.value) && originalRateValid.value && line.value && Number.isFinite(quantity.value) && quantity.value > 0 && Number(quantity.value.toFixed(4)) === quantity.value && quantity.value <= remaining.value && amount.value > 0 && amount.value <= Math.round(openAmount(detail.value) * 10000) / 10000)
const money = (n: number) => n.toLocaleString(locale.value, { maximumFractionDigits: 4 })
const date = () => { const d = new Date(); return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}` }
function close() { if (!busy.value) emit('update:modelValue', false) }
watch(lineId, () => { quantity.value = 1 })
async function readOriginal() {
  const token = ++detailGeneration, id = originalId.value
  detail.value = null; lineId.value = ''; failed.value = false
  if (!props.modelValue || !allowed.value || !candidates.value.some(row => row.id === id)) { reading.value = false; return }
  reading.value = true
  try { const value = await getFinanceInvoice(id); if (token === detailGeneration) { if (value.id !== id || !eligible(value)) throw new Error('Original unavailable'); detail.value = value } }
  catch { if (token === detailGeneration) failed.value = true }
  finally { if (token === detailGeneration) reading.value = false }
}
watch(originalId, readOriginal)
async function load() {
  const token = ++generation
  ++detailGeneration; detail.value = null; lineId.value = ''; reading.value = false
  if (!props.modelValue || !allowed.value) { rows.value = []; currency.value = ''; loading.value = false; return }
  loading.value = true; failed.value = false
  try {
    const [invoices, settings] = await Promise.all([listFinanceInvoices(), getOrganizationSettings()])
    if (token !== generation) return
    rows.value = invoices; currency.value = settings.baseCurrencyCode
    if (!candidates.value.some(row => row.id === originalId.value)) originalId.value = ''
    await readOriginal()
  } catch { if (token === generation) { failed.value = true; rows.value = []; currency.value = '' } }
  finally { if (token === generation) loading.value = false }
}
watch(() => [props.modelValue, auth.user?.organizationId, allowed.value], () => { originalId.value = ''; requestKey = ''; fingerprint = ''; uncertain.value = false; void load() }, { immediate: true })
onBeforeUnmount(() => { generation++; detailGeneration++ })
async function save() {
  if (busy.value || !valid.value || !line.value || !detail.value) return
  const row = line.value, original = detail.value, today = date()
  const net = Math.round(quantity.value * row.unitPrice * (1-row.discountRate! / 100) * 10000) / 10000
  const payload: InvoiceInput = { documentType: original.documentType === 'SALES_INVOICE' ? 'CUSTOMER_CREDIT' : 'SUPPLIER_CREDIT', originalInvoiceId: original.id, partyId: original.partyId, projectId: original.projectId, businessDate: today, accountingDate: today, dueDate: today, exchangeRateDate: original.exchangeRateDate ?? today, currencyCode: original.currencyCode, exchangeRate: original.exchangeRate!, lines: [{ itemId: row.itemId, projectId: row.projectId, description: row.description, quantity: quantity.value, unitPrice: row.unitPrice, discountRate: row.discountRate!, taxRate: row.taxRate, accountCode: row.reversalAccountCode, sources: [{ sourceType: 'ORIGINAL_INVOICE_LINE', sourceId: original.id, sourceLineId: row.id, quantity: quantity.value, amount: net }] }] }
  const next = JSON.stringify(payload)
  if (next !== fingerprint) { requestKey = globalThis.crypto.randomUUID(); fingerprint = next }
  busy.value = true; uncertain.value = false
  const token = generation
  try { await createFinanceInvoice(payload, requestKey); if (token === generation) { ElMessage.success(t('finance.creditNote.saved')); emit('update:modelValue', false) } }
  catch { if (token === generation) uncertain.value = true }
  finally { if (token === generation) { await load(); emit('changed') } busy.value = false }
}
</script>
<template>
  <el-dialog :model-value="modelValue" :title="t('finance.creditNote.title')" width="min(640px, calc(100vw - 32px))" destroy-on-close :show-close="!busy" :close-on-click-modal="!busy" :close-on-press-escape="!busy" @update:model-value="close">
    <p>{{ t('finance.creditNote.terms') }}</p>
    <el-alert v-if="failed" :title="t('finance.creditNote.loadFailed')" type="error" :closable="false" />
    <el-alert v-if="uncertain" :title="t('finance.creditNote.failed')" type="error" :closable="false" />
    <el-button :disabled="loading || reading || busy" @click="load">{{ t('finance.m4.retry') }}</el-button>
    <el-form v-if="!loading && allowed && currency" :key="busy ? 'pending' : 'ready'" :disabled="busy || reading" label-position="top">
      <el-form-item :label="t('finance.creditNote.original')"><el-select v-model="originalId" class="full-width" filterable><el-option v-for="row in candidates" :key="row.id" :value="row.id" :label="`${row.number} · ${money(openAmount(row))} ${row.currencyCode}`" /></el-select></el-form-item>
      <p v-if="detail">{{ t('finance.creditNote.originalRate',{currency:detail.currencyCode,rate:detail.exchangeRate,base:currency,date:detail.exchangeRateDate ?? date()}) }}</p>
      <el-alert v-if="detail && !originalRateValid" :title="t('finance.creditNote.invalidRate')" type="error" :closable="false" />
      <el-form-item v-if="detail" :label="t('finance.creditNote.line')"><el-select v-model="lineId" class="full-width"><el-option v-for="row in lines" :key="row.id" :value="row.id" :label="`${row.lineNo} · ${row.description} · ${money(row.quantity-row.creditedQuantity!)}`" /></el-select></el-form-item>
      <template v-if="line"><p>{{ t('finance.creditNote.remaining') }}: {{ money(remaining) }}</p><el-form-item :label="t('finance.creditNote.quantity')"><el-input-number v-model="quantity" :min="0.0001" :max="remaining" :precision="4" class="full-width" /></el-form-item><p>{{ t('finance.creditNote.amount') }}: {{ money(amount) }} {{ detail?.currencyCode }}</p></template>
      <p v-if="!candidates.length">{{ t('finance.creditNote.empty') }}</p>
    </el-form>
    <template #footer><el-button :disabled="busy" @click="close">{{ t('finance.bankEntry.close') }}</el-button><el-button type="primary" :loading="busy" :disabled="busy || !valid" @click="save">{{ t('projects.save') }}</el-button></template>
  </el-dialog>
</template>
