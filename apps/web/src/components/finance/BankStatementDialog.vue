<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import { importBankStatement, listFinanceBankAccounts, type FinanceBankAccount } from '@/api/finance-v2'
const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; changed: [] }>()
const auth = useAuthStore()
const { t } = useI18n()
const banks = ref<FinanceBankAccount[]>([])
const loading = ref(false)
const busy = ref(false)
const failed = ref(false)
const uncertain = ref(false)
const form = reactive({ bankAccountId: '', date: '', amount: 0, reference: '' })
let generation = 0
const allowed = computed(() => auth.hasPermission('finance:view') && auth.hasPermission('finance:bank'))
const bank = computed(() => banks.value.find(row => row.id === form.bankAccountId))
const validDate = computed(() => /^\d{4}-\d{2}-\d{2}$/.test(form.date) && Number.isFinite(Date.parse(form.date)) && new Date(form.date).toISOString().slice(0, 10) === form.date)
const valid = computed(() => props.modelValue && allowed.value && !loading.value && !failed.value && !!bank.value && validDate.value && Number.isFinite(form.amount) && form.amount !== 0 && Math.abs(form.amount) < 1e15 && !!form.reference.trim() && form.reference.trim().length <= 160)
function close() { if (!busy.value) emit('update:modelValue', false) }
async function load() {
  const token = ++generation
  if (!props.modelValue || !allowed.value) { banks.value = []; loading.value = false; return }
  loading.value = true; failed.value = false
  try { const values = await listFinanceBankAccounts(); if (token === generation) banks.value = values }
  catch { if (token === generation) { banks.value = []; failed.value = true } }
  finally { if (token === generation) loading.value = false }
}
watch(() => [props.modelValue, auth.user?.organizationId, allowed.value], () => {
  const date = new Date()
  Object.assign(form, { bankAccountId: '', date: `${date.getFullYear()}-${String(date.getMonth()+1).padStart(2,'0')}-${String(date.getDate()).padStart(2,'0')}`, amount: 0, reference: '' })
  uncertain.value = false; void load()
}, { immediate: true })
onBeforeUnmount(() => { generation++ })
async function save() {
  if (busy.value || !valid.value || !bank.value) return
  busy.value = true; uncertain.value = false
  try {
    await importBankStatement({ bankAccountId: bank.value.id, lines: [{ transactionDate: form.date, amount: form.amount, currencyCode: bank.value.currencyCode, externalReference: form.reference.trim() }] })
    ElMessage.success(t('finance.bankEntry.saved')); emit('update:modelValue', false)
  } catch { uncertain.value = true }
  finally { emit('changed'); await load(); busy.value = false }
}
</script>
<template>
  <el-dialog :model-value="modelValue" :title="t('finance.bankEntry.title')" width="min(520px, calc(100vw - 32px))" destroy-on-close :show-close="!busy" :close-on-click-modal="!busy" :close-on-press-escape="!busy" @update:model-value="close">
    <el-alert v-if="failed" :title="t('finance.bankEntry.loadFailed')" type="error" :closable="false" />
    <el-alert v-if="uncertain" :title="t('finance.bankEntry.failed')" type="error" :closable="false" />
    <el-button :disabled="busy || loading" @click="load">{{ t('finance.m4.retry') }}</el-button>
    <p>{{ t('finance.bankEntry.direction') }}</p>
    <el-form v-if="allowed && !loading && !failed" :key="busy ? 'pending' : 'ready'" :disabled="busy" label-position="top">
      <el-form-item :label="t('finance.bankEntry.account')"><el-select v-model="form.bankAccountId" class="full-width"><el-option v-for="row in banks" :key="row.id" :value="row.id" :label="`${row.code} · ${row.name} · ${row.currencyCode}`" /></el-select></el-form-item>
      <p v-if="bank">{{ bank.currencyCode }}</p>
      <el-form-item :label="t('finance.bankEntry.date')"><el-input v-model="form.date" type="date" /></el-form-item>
      <el-form-item :label="t('finance.bankEntry.amount')"><el-input-number v-model="form.amount" :precision="4" class="full-width" /></el-form-item>
      <el-form-item :label="t('finance.bankEntry.reference')"><el-input v-model="form.reference" :maxlength="160" /></el-form-item>
    </el-form>
    <template #footer><el-button :disabled="busy" @click="close">{{ t('finance.bankEntry.close') }}</el-button><el-button type="primary" :disabled="busy || !valid" :loading="busy" @click="save">{{ t('finance.bankEntry.save') }}</el-button></template>
  </el-dialog>
</template>
