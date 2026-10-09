<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import { getOrganizationSettings, listMasterData, type MasterDataRecord } from '@/api/master-data'
import { listCurrencyRevaluations, createCurrencyRevaluation, reverseCurrencyRevaluation, type CurrencyRevaluation } from '@/api/finance-v2'
const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; changed: [] }>()
const auth = useAuthStore(), { t, locale } = useI18n()
const today = () => { const d = new Date(); return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}` }
const history = ref<CurrencyRevaluation[]>([]), currencies = ref<MasterDataRecord[]>([]), base = ref('')
const currency = ref(''), rate = ref(0), confirmed = ref(false), accountingDate = ref(today())
const selectedId = ref(''), reason = ref(''), reverseDate = ref(today()), reverseConfirmed = ref(false)
const loading = ref(false), busy = ref(false), failed = ref(false), uncertain = ref(false)
let generation = 0, requestKey = '', fingerprint = '', retryMode = ''
const allowed = computed(() => auth.hasPermission('finance:view'))
const canCreate = computed(() => allowed.value && auth.hasPermission('finance:budget') && auth.hasPermission('master:view'))
const canReverse = computed(() => allowed.value && auth.hasPermission('finance:post'))
const foreign = computed(() => currencies.value.filter(row => row.active && /^[A-Z]{3}$/.test(row.code) && row.code !== base.value))
const ready = computed(() => props.modelValue && allowed.value && !loading.value && !failed.value)
const original = computed(() => history.value.find(row => row.id === selectedId.value && row.status === 'POSTED' && !!row.journalEntryId))
const payload = computed(() => ({ accountingDate: accountingDate.value, currencyCode: currency.value, rate: rate.value }))
const active = computed(() => history.value.some(row => row.currencyCode === currency.value && row.status === 'POSTED'))
const createValid = computed(() => ready.value && canCreate.value && !!base.value && foreign.value.some(row => row.code === currency.value) && Number.isFinite(rate.value) && rate.value > 0 && Number(rate.value.toFixed(8)) === rate.value && accountingDate.value === today() && confirmed.value && (!active.value || (uncertain.value && retryMode === 'create' && fingerprint === JSON.stringify(payload.value))))
const dateValid = (value: string) => { const d = new Date(`${value}T12:00:00`); return /^\d{4}-\d{2}-\d{2}$/.test(value) && !Number.isNaN(d.getTime()) && `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}` === value && value <= today() }
const reverseValid = computed(() => ready.value && canReverse.value && !!original.value && dateValid(reverseDate.value) && reverseDate.value >= original.value.accountingDate && !!reason.value.trim() && reason.value.trim().length <= 500 && reverseConfirmed.value)
const money = (n: number) => n.toLocaleString(locale.value, { maximumFractionDigits: 4 })
function close() { if (!busy.value) emit('update:modelValue',false) }
watch(currency, () => { rate.value = 0; confirmed.value = false })
watch(rate, () => { confirmed.value = false })
watch(selectedId, () => { reason.value = ''; reverseDate.value = today(); reverseConfirmed.value = false })
watch([reason,reverseDate], () => { reverseConfirmed.value = false })
async function load() {
  const token = ++generation
  if (!props.modelValue || !allowed.value) { history.value = []; currencies.value = []; base.value = ''; loading.value = false; return }
  loading.value = true; failed.value = false
  try {
    const [values,setup] = await Promise.all([listCurrencyRevaluations(),(async () => {
      if (!canCreate.value) return { settings: null, rows: [] as MasterDataRecord[] }
      const settings = await getOrganizationSettings(), rows: MasterDataRecord[] = []
      for (let page=0;page<100;page++) {
        const value = await listMasterData<MasterDataRecord>('currencies','',page,100)
        rows.push(...value.content)
        if (page+1 >= value.totalPages) return { settings, rows }
      }
      throw new Error('Incomplete currencies')
    })()])
    if (token !== generation) return
    history.value = values; base.value = setup.settings?.baseCurrencyCode ?? ''; currencies.value = setup.rows
    if (!foreign.value.some(row => row.code === currency.value)) currency.value = ''
    if (!original.value) selectedId.value = ''
  } catch { if (token === generation) { failed.value = true; history.value = []; currencies.value = []; base.value = '' } }
  finally { if (token === generation) loading.value = false }
}
watch(() => [props.modelValue,auth.user?.organizationId,allowed.value,canCreate.value,canReverse.value], () => { currency.value = ''; rate.value = 0; confirmed.value = false; accountingDate.value = today(); selectedId.value = ''; reason.value = ''; reverseConfirmed.value = false; requestKey = ''; fingerprint = ''; retryMode = ''; uncertain.value = false; void load() }, { immediate: true })
onBeforeUnmount(() => { generation++ })
async function submit(mode: 'create' | 'reverse') {
  if (busy.value || (mode === 'create' ? !createValid.value : !reverseValid.value)) return
  const data = mode === 'create' ? payload.value : { id: original.value!.id, accountingDate: reverseDate.value, reason: reason.value.trim() }
  const next = JSON.stringify(data)
  if (retryMode !== mode || fingerprint !== next) { requestKey = globalThis.crypto.randomUUID(); fingerprint = next; retryMode = mode }
  const token = generation; busy.value = true; uncertain.value = false
  try {
    if (mode === 'create') await createCurrencyRevaluation(payload.value,requestKey)
    else await reverseCurrencyRevaluation(original.value!.id,reverseDate.value,reason.value.trim(),requestKey)
    if (token === generation) { ElMessage.success(t(`finance.revaluation.${mode === 'create' ? 'saved' : 'reversed'}`)); confirmed.value = false; reverseConfirmed.value = false }
  } catch { if (token === generation) uncertain.value = true }
  finally { if (token === generation) { await load(); emit('changed') } busy.value = false }
}
</script>
<template>
  <el-dialog :model-value="modelValue" :title="t('finance.revaluation.title')" width="min(800px, calc(100vw - 32px))" destroy-on-close :show-close="!busy" :close-on-click-modal="!busy" :close-on-press-escape="!busy" @update:model-value="close">
    <p>{{ t('finance.revaluation.terms') }}</p>
    <el-alert v-if="failed" :title="t('finance.revaluation.failed')" type="error" :closable="false" />
    <el-alert v-if="uncertain" :title="t('finance.revaluation.uncertain')" type="warning" :closable="false" />
    <el-button :disabled="busy || loading" @click="load">{{ t('finance.revaluation.reload') }}</el-button>
    <el-form v-if="ready && canCreate" :key="busy ? 'pending' : 'ready'" :disabled="busy" label-position="top">
      <p>{{ t('finance.revaluation.date') }}: {{ accountingDate }} · {{ base }}</p>
      <el-form-item :label="t('finance.revaluation.currency')"><el-select v-model="currency" class="full-width"><el-option v-for="row in foreign" :key="row.id" :value="row.code" :label="row.code" /></el-select></el-form-item>
      <p v-if="currency">{{ t('finance.foreign.direction',{currency,base}) }}</p>
      <el-form-item :label="t('finance.revaluation.rate')"><el-input-number v-model="rate" :min="0" :precision="8" :controls="false" class="full-width" /></el-form-item>
      <p v-if="active">{{ t('finance.revaluation.blocked') }}</p>
      <el-checkbox v-model="confirmed" :disabled="busy">{{ t('finance.revaluation.confirm') }}</el-checkbox>
      <el-button :disabled="busy || !createValid" :loading="busy" @click="submit('create')">{{ t('finance.revaluation.post') }}</el-button>
    </el-form>
    <h3>{{ t('finance.revaluation.history') }}</h3>
    <div v-if="ready" class="revaluation-history"><table><thead><tr><th>{{ t('finance.revaluation.original') }}</th><th>{{ t('finance.revaluation.rate') }}</th><th>{{ t('finance.revaluation.gain') }}</th><th>{{ t('finance.revaluation.loss') }}</th></tr></thead><tbody><tr v-for="row in history" :key="row.id"><td>{{ row.number }} · {{ row.accountingDate }} · {{ row.currencyCode }} · {{ t(`finance.revaluation.${row.status}`) }}</td><td>{{ row.rate }}</td><td>{{ money(row.totalGain) }}</td><td>{{ money(row.totalLoss) }}</td></tr></tbody></table><p v-if="!history.length">{{ t('finance.revaluation.empty') }}</p></div>
    <el-form v-if="ready && canReverse" :key="busy ? 'pending' : 'ready'" :disabled="busy" label-position="top">
      <el-form-item :label="t('finance.revaluation.original')"><el-select v-model="selectedId" class="full-width"><el-option v-for="row in history.filter(row => row.status === 'POSTED' && !!row.journalEntryId)" :key="row.id" :value="row.id" :label="`${row.number} · ${row.currencyCode} · ${row.accountingDate}`" /></el-select></el-form-item>
      <el-form-item :label="t('finance.revaluation.reverseDate')"><el-date-picker v-model="reverseDate" type="date" value-format="YYYY-MM-DD" :clearable="false" class="full-width" /></el-form-item>
      <el-form-item :label="t('finance.revaluation.reason')"><el-input v-model="reason" :maxlength="500" /></el-form-item>
      <el-checkbox v-model="reverseConfirmed" :disabled="busy">{{ t('finance.revaluation.confirmReverse') }}</el-checkbox>
      <el-button :disabled="busy || !reverseValid" :loading="busy" @click="submit('reverse')">{{ t('finance.revaluation.reverse') }}</el-button>
    </el-form>
    <template #footer><el-button :disabled="busy" @click="close">{{ t('finance.bankEntry.close') }}</el-button></template>
  </el-dialog>
</template>
<style scoped>.revaluation-history{overflow-x:auto;margin-block:16px}table{width:100%;border-collapse:collapse}th,td{text-align:left;padding:8px;border-bottom:1px solid var(--el-border-color)}th{white-space:nowrap}.full-width{width:100%}</style>
