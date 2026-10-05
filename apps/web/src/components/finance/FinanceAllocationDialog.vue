<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import { allocateFinancePayment, listFinanceInvoices, listFinancePayments, reverseFinanceAllocation, type FinanceAllocation, type FinanceInvoice, type FinancePayment } from '@/api/finance-v2'

const props = defineProps<{ modelValue: boolean; paymentId: string }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; changed: [] }>()
const auth = useAuthStore()
const { t, locale } = useI18n()
const loading = ref(false)
const busy = ref(false)
const failed = ref(false)
const actionFailed = ref(false)
const payment = ref<FinancePayment | null>(null)
const invoices = ref<FinanceInvoice[]>([])
const form = reactive({ invoiceId: '', amount: 0 })
const reversal = ref<FinanceAllocation | null>(null)
const reason = ref('')
const reversalKey = ref('')
let loadVersion = 0
const canManage = computed(() => auth.hasPermission('finance:view') && auth.hasPermission('finance:allocate'))
const remaining = (total: number, allocated: number, credited = 0) => Math.max(0, Math.round((total - allocated - credited) * 10000) / 10000)
const paymentRemaining = computed(() => payment.value ? remaining(payment.value.amount, payment.value.allocatedAmount) : 0)
const candidates = computed(() => invoices.value.filter(row => payment.value && row.status === 'POSTED' && row.partyType === payment.value.partyType && row.partyId === payment.value.partyId && row.currencyCode === payment.value.currencyCode && row.documentType === (payment.value.paymentType === 'RECEIPT' ? 'SALES_INVOICE' : 'SUPPLIER_INVOICE') && remaining(row.totalAmount, row.allocatedAmount, row.creditedAmount) > 0))
const selected = computed(() => candidates.value.find(row => row.id === form.invoiceId))
const maximum = computed(() => selected.value ? Math.min(paymentRemaining.value, remaining(selected.value.totalAmount, selected.value.allocatedAmount, selected.value.creditedAmount)) : 0)
const ready = computed(() => canManage.value && !loading.value && !failed.value && payment.value?.status === 'POSTED' && ['RECEIPT', 'PAYMENT'].includes(payment.value.paymentType))
const valid = computed(() => ready.value && !!selected.value && Number.isFinite(form.amount) && form.amount > 0 && form.amount <= maximum.value)
const canReverse = computed(() => ready.value && !!reversal.value && payment.value?.allocations.some(row => row.id === reversal.value?.id && row.status === 'ACTIVE') && reason.value.trim().length > 0 && reason.value.trim().length <= 500)
function money(value: number) { return value.toLocaleString(locale.value, { minimumFractionDigits: 2, maximumFractionDigits: 4 }) }
function invoiceNumber(id: string) { return invoices.value.find(row => row.id === id)?.number ?? id }
function selectInvoice() { form.amount = maximum.value }
function close() { if (!busy.value) emit('update:modelValue', false) }

async function load() {
  const version = ++loadVersion
  if (!props.modelValue || !canManage.value || !props.paymentId) { payment.value = null; invoices.value = []; loading.value = false; return }
  const organizationId = auth.user?.organizationId
  loading.value = true; failed.value = false
  try {
    const [paymentRows, invoiceRows] = await Promise.all([listFinancePayments(), listFinanceInvoices()])
    if (version !== loadVersion || organizationId !== auth.user?.organizationId) return
    payment.value = paymentRows.find(row => row.id === props.paymentId) ?? null
    invoices.value = invoiceRows
    if (!payment.value) failed.value = true
    if (!candidates.value.some(row => row.id === form.invoiceId)) { form.invoiceId = ''; form.amount = 0 }
    else form.amount = Math.min(form.amount, maximum.value)
  } catch { if (version === loadVersion) { failed.value = true; payment.value = null; invoices.value = [] } }
  finally { if (version === loadVersion) loading.value = false }
}
watch(() => [props.modelValue, props.paymentId, auth.user?.organizationId, canManage.value], () => {
  form.invoiceId = ''; form.amount = 0; reversal.value = null; reason.value = ''; actionFailed.value = false
  void load()
}, { immediate: true })
onBeforeUnmount(() => { loadVersion++ })

async function allocate() {
  if (busy.value || !valid.value || !payment.value) return
  busy.value = true; actionFailed.value = false
  try {
    await allocateFinancePayment(payment.value.id, form.invoiceId, form.amount)
    form.invoiceId = ''; form.amount = 0
    ElMessage.success(t('finance.allocation.allocated'))
  } catch { actionFailed.value = true }
  finally { await load(); emit('changed'); busy.value = false }
}
function openReversal(row: FinanceAllocation) {
  if (busy.value || !ready.value || row.status !== 'ACTIVE') return
  reversal.value = row; reason.value = ''; reversalKey.value = globalThis.crypto.randomUUID()
}
async function reverse() {
  if (busy.value || !canReverse.value || !reversal.value) return
  busy.value = true; actionFailed.value = false
  try {
    await reverseFinanceAllocation(reversal.value.id, reason.value.trim(), reversalKey.value)
    reversal.value = null
    ElMessage.success(t('finance.allocation.reversedSuccess'))
  } catch { actionFailed.value = true }
  finally { await load(); emit('changed'); busy.value = false }
}
</script>

<template>
  <el-dialog :model-value="modelValue" :title="t('finance.allocation.title')" width="min(720px, calc(100vw - 32px))" destroy-on-close :close-on-click-modal="!busy" :close-on-press-escape="!busy" :show-close="!busy" @update:model-value="close">
    <el-alert v-if="failed" :title="t('finance.allocation.loadFailed')" type="error" :closable="false" />
    <el-alert v-if="actionFailed" :title="t('finance.allocation.failed')" type="error" :closable="false" />
    <el-button :disabled="busy || loading" @click="load">{{ t('finance.m4.retry') }}</el-button>
    <div v-if="payment" v-loading="loading">
      <p>{{ t('finance.allocation.payment') }}: {{ payment.number }}</p>
      <p>{{ t('finance.allocation.remaining') }}: {{ money(paymentRemaining) }} {{ payment.currencyCode }}</p>
      <el-form v-if="ready && !reversal" class="allocation-form" label-position="top" :disabled="busy">
        <p>{{ t('finance.allocation.allDates') }}</p>
        <el-form-item :label="t('finance.allocation.invoice')"><el-select v-model="form.invoiceId" class="full-width" @change="selectInvoice"><el-option v-for="row in candidates" :key="row.id" :value="row.id" :label="`${row.number} · ${money(remaining(row.totalAmount, row.allocatedAmount, row.creditedAmount))} ${row.currencyCode}`" /></el-select></el-form-item>
        <el-form-item :label="t('finance.allocation.amount')"><el-input-number v-model="form.amount" :min="0" :max="maximum" :precision="4" class="full-width" /></el-form-item>
        <el-button type="primary" :loading="busy" :disabled="busy || !valid" @click="allocate">{{ t('finance.allocation.apply') }}</el-button>
        <p v-if="!candidates.length || paymentRemaining === 0">{{ t('finance.allocation.empty') }}</p>
      </el-form>
      <el-form v-if="reversal" class="reversal-form" label-position="top" :disabled="busy">
        <p>{{ invoiceNumber(reversal.invoiceId) }} · {{ money(reversal.amount) }} {{ payment.currencyCode }}</p>
        <el-form-item :label="t('finance.allocation.reason')"><el-input v-model="reason" type="textarea" :maxlength="500" /></el-form-item>
        <el-button :disabled="busy" @click="reversal = null">{{ t('finance.allocation.cancelReverse') }}</el-button>
        <el-button type="danger" :loading="busy" :disabled="busy || !canReverse" @click="reverse">{{ t('finance.allocation.confirmReverse') }}</el-button>
      </el-form>
      <h3>{{ t('finance.allocation.history') }}</h3>
      <el-table :data="payment.allocations" size="small">
        <el-table-column :label="t('finance.allocation.invoice')" min-width="150"><template #default="{ row }">{{ invoiceNumber(row.invoiceId) }}</template></el-table-column>
        <el-table-column :label="t('finance.amount')" width="120"><template #default="{ row }">{{ money(row.amount) }}</template></el-table-column>
        <el-table-column :label="t('finance.statusLabel')" width="90"><template #default="{ row }">{{ t(`finance.allocation.${row.status === 'ACTIVE' ? 'active' : 'reversed'}`) }}</template></el-table-column>
        <el-table-column :label="t('finance.actions')" width="130" fixed="right"><template #default="{ row }"><el-button v-if="ready && row.status === 'ACTIVE'" link type="danger" :disabled="busy" @click="openReversal(row)">{{ t('finance.allocation.reverse') }}</el-button></template></el-table-column>
      </el-table>
    </div>
    <template #footer><el-button :disabled="busy" @click="close">{{ t('finance.allocation.close') }}</el-button></template>
  </el-dialog>
</template>
