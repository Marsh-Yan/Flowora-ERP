<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import { getFinanceInvoice, approveFinanceMatchException, type FinanceInvoiceDetail } from '@/api/finance-v2'
const props = defineProps<{ modelValue: boolean; invoiceId: string }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; changed: [] }>()
const auth = useAuthStore(), { t, locale } = useI18n()
const invoice = ref<FinanceInvoiceDetail | null>(null), reason = ref('')
const loading = ref(false), busy = ref(false), failed = ref(false), uncertain = ref(false)
let generation = 0
const canRead = computed(() => auth.hasPermission('finance:view'))
const canApprove = computed(() => auth.hasPermission('finance:match-exception'))
const ready = computed(() => props.modelValue && canRead.value && !loading.value && !failed.value && invoice.value?.id === props.invoiceId)
const valid = computed(() => ready.value && canApprove.value && invoice.value?.documentType === 'SUPPLIER_INVOICE' && invoice.value.status === 'DRAFT' && invoice.value.matchStatus === 'EXCEPTION' && !!reason.value.trim() && reason.value.trim().length <= 500)
function number(value: number) { return value.toLocaleString(locale.value, { maximumFractionDigits: 4 }) }
function close() { if (!busy.value) emit('update:modelValue', false) }
async function load() {
  const token = ++generation
  invoice.value = null
  if (!props.modelValue || !props.invoiceId || !canRead.value) { loading.value = false; return }
  loading.value = true; failed.value = false
  try {
    const row = await getFinanceInvoice(props.invoiceId)
    if (token !== generation) return
    if (row.id !== props.invoiceId || !Array.isArray(row.lines)) throw new Error('Invoice detail unavailable')
    invoice.value = row
  } catch { if (token === generation) failed.value = true }
  finally { if (token === generation) loading.value = false }
}
watch(() => [props.modelValue, props.invoiceId, auth.user?.organizationId, canRead.value], () => { reason.value = ''; uncertain.value = false; void load() }, { immediate: true })
onBeforeUnmount(() => { generation++ })
async function approve() {
  if (busy.value || !valid.value) return
  const id = props.invoiceId, submittedReason = reason.value.trim()
  busy.value = true; uncertain.value = false
  try { await approveFinanceMatchException(id, submittedReason); ElMessage.success(t('finance.matchException.saved')); reason.value = '' }
  catch { uncertain.value = true }
  finally { await load(); emit('changed'); busy.value = false }
}
</script>
<template>
  <el-dialog :model-value="modelValue" :title="t('finance.matchException.title')" width="min(760px, calc(100vw - 32px))" destroy-on-close :show-close="!busy" :close-on-click-modal="!busy" :close-on-press-escape="!busy" @update:model-value="close">
    <el-alert v-if="failed" :title="t('finance.matchException.loadFailed')" type="error" :closable="false" />
    <el-alert v-if="uncertain" :title="t('finance.matchException.failed')" type="warning" :closable="false" />
    <el-button :disabled="loading || busy" @click="load">{{ t('finance.m4.retry') }}</el-button>
    <section v-if="ready && invoice">
      <p>{{ invoice.number }} · {{ invoice.status }} · {{ invoice.matchStatus }} · {{ number(invoice.totalAmount) }} {{ invoice.currencyCode }}</p>
      <p>{{ t('finance.matchException.note') }}</p>
      <article v-for="line in invoice.lines" :key="line.id" class="match-line">
        <strong>{{ line.lineNo }} · {{ line.description }}</strong>
        <dl><dt>{{ t('finance.m4.quantity') }}</dt><dd>{{ number(line.quantity) }}</dd><dt>{{ t('finance.m4.unitPrice') }}</dt><dd>{{ number(line.unitPrice) }} {{ invoice.currencyCode }}</dd><dt>{{ t('finance.matchException.quantityVariance') }}</dt><dd>{{ number(line.matchQuantityVariance) }}</dd><dt>{{ t('finance.matchException.priceVariance') }}</dt><dd>{{ number(line.matchPriceVarianceRate) }}%</dd><dt>{{ t('finance.matchException.taxVariance') }}</dt><dd>{{ number(line.matchTaxVariance) }} {{ invoice.currencyCode }}</dd></dl>
        <p v-for="(source, index) in line.sources" :key="index" class="source-id">{{ t('finance.matchException.source') }}: {{ source.sourceType }} · {{ source.sourceId }} / {{ source.sourceLineId || '—' }}</p>
      </article>
      <template v-if="invoice.matchStatus === 'APPROVED_EXCEPTION'"><p class="source-id">{{ t('finance.matchException.approvedBy') }}: {{ invoice.matchExceptionApprovedBy || '—' }}</p><p class="source-id">{{ t('finance.matchException.approvedReason') }}: {{ invoice.matchExceptionReason || '—' }}</p></template>
      <el-form v-if="canApprove && invoice.status === 'DRAFT' && invoice.matchStatus === 'EXCEPTION'" :key="busy ? 'pending' : 'ready'" :disabled="busy" label-position="top"><el-form-item :label="t('finance.matchException.reason')"><el-input v-model="reason" type="textarea" :rows="3" maxlength="500" show-word-limit /></el-form-item></el-form>
      <p v-else-if="!canApprove">{{ t('finance.matchException.noApprove') }}</p>
    </section>
    <template #footer><el-button :disabled="busy" @click="close">{{ t('finance.bankEntry.close') }}</el-button><el-button v-if="canApprove && invoice?.matchStatus === 'EXCEPTION'" type="primary" :loading="busy" :disabled="busy || !valid" @click="approve">{{ t('finance.matchException.approve') }}</el-button></template>
  </el-dialog>
</template>
<style scoped>
.match-line { border: 1px solid var(--el-border-color); border-radius: 8px; padding: 12px; margin: 12px 0; }
dl { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 8px; } dt, dd { margin: 0; overflow-wrap: anywhere; } dd { text-align: right; } .source-id { overflow-wrap: anywhere; }
</style>
