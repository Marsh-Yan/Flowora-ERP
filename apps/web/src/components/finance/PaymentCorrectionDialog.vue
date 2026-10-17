<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import { correctFinancePayment, listFinancePayments, type FinancePayment } from '@/api/finance-v2'
const props=defineProps<{modelValue:boolean}>()
const emit=defineEmits<{'update:modelValue':[value:boolean];changed:[]}>()
const auth=useAuthStore(),{t}=useI18n()
const rows=ref<FinancePayment[]>([]),loading=ref(false),busy=ref(false),failed=ref(false),uncertain=ref(false)
const form=reactive({id:'',date:'',reason:'',confirmed:false})
let generation=0,key='',fingerprint=''
const today=()=>{const d=new Date();return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`}
const canView=computed(()=>auth.hasPermission('finance:view'))
const canCreate=computed(()=>canView.value && auth.hasPermission('finance:create'))
const canPost=computed(()=>canView.value && auth.hasPermission('finance:post'))
const originals=computed(()=>rows.value.filter(r=>['RECEIPT','PAYMENT'].includes(r.paymentType)))
const candidates=computed(()=>originals.value.filter(r=>(r.status==='DRAFT' && canCreate.value)||(r.status==='POSTED' && canPost.value)))
const selected=computed(()=>candidates.value.find(r=>r.id===form.id))
const occupied=computed(()=>!!selected.value && (selected.value.allocatedAmount!==0 || selected.value.allocations.some(a=>a.status==='ACTIVE') || rows.value.some(r=>['CUSTOMER_REFUND','SUPPLIER_REFUND'].includes(r.paymentType) && ['DRAFT','POSTED'].includes(r.status) && (r.originalPaymentId===selected.value!.id || (!r.originalPaymentId && r.partyType===selected.value!.partyType && r.partyId===selected.value!.partyId && r.currencyCode===selected.value!.currencyCode)))))
const validDate=computed(()=>/^\d{4}-\d{2}-\d{2}$/.test(form.date) && Number.isFinite(Date.parse(form.date)) && new Date(form.date).toISOString().slice(0,10)===form.date)
const valid=computed(()=>props.modelValue && canView.value && !loading.value && !failed.value && !!selected.value && !occupied.value && validDate.value && form.date>=selected.value.accountingDate && !!form.reason.trim() && form.reason.trim().length<=500 && form.confirmed)
watch(()=>JSON.stringify([selected.value,occupied.value,form.date,form.reason]),()=>{form.confirmed=false})
async function load(){
  const token=++generation
  if(!props.modelValue || !canView.value){rows.value=[];loading.value=false;return}
  loading.value=true;failed.value=false
  try{const value=await listFinancePayments();if(token===generation)rows.value=value}
  catch{if(token===generation){failed.value=true;rows.value=[]}}
  finally{if(token===generation)loading.value=false}
}
watch(()=>[props.modelValue,auth.user?.organizationId,canView.value,canCreate.value,canPost.value],()=>{Object.assign(form,{id:'',date:today(),reason:'',confirmed:false});key='';fingerprint='';uncertain.value=false;load()},{immediate:true})
onBeforeUnmount(()=>{generation++})
function close(){if(!busy.value)emit('update:modelValue',false)}
async function correct(){
  if(busy.value || !valid.value || !selected.value)return
  const row=selected.value,action=row.status==='DRAFT'?'cancel':'reverse',body={version:row.version,accountingDate:form.date,reason:form.reason.trim()},token=generation
  const next=JSON.stringify({id:row.id,action,...body})
  if(next!==fingerprint){fingerprint=next;key=globalThis.crypto.randomUUID()}
  busy.value=true;uncertain.value=false
  try{await correctFinancePayment(row.id,action,body,key);if(token===generation){form.id='';form.reason='';form.confirmed=false}}
  catch{if(token===generation)uncertain.value=true}
  finally{if(token===generation){const refresh=generation+1;await load();if(generation===refresh)emit('changed')}busy.value=false}
}
</script>
<template>
  <el-dialog :model-value="modelValue" :title="t('finance.paymentCorrection.title')" width="min(900px, calc(100vw - 32px))" destroy-on-close :show-close="!busy" :close-on-click-modal="!busy" :close-on-press-escape="!busy" @update:model-value="close">
    <p>{{ t('finance.paymentCorrection.terms') }}</p>
    <el-alert v-if="failed" :title="t('finance.paymentCorrection.failed')" type="error" :closable="false" />
    <el-alert v-if="uncertain" :title="t('finance.refund.uncertain')" type="warning" :closable="false" />
    <el-button :disabled="busy || loading" @click="load">{{ t('finance.m4.retry') }}</el-button>
    <el-form v-if="canCreate || canPost" label-position="top" :disabled="busy || loading || failed">
      <el-form-item :label="t('finance.paymentCorrection.select')"><el-select v-model="form.id" class="full-width" filterable><el-option v-for="row in candidates" :key="row.id" :value="row.id" :label="`${row.number} · ${row.paymentType} · ${row.status} · ${row.amount} ${row.currencyCode}`" /></el-select></el-form-item>
      <p v-if="selected">{{ selected.number }} · {{ selected.partyId }} · {{ selected.amount }} {{ selected.currencyCode }} · {{ selected.exchangeRate }} {{ selected.baseCurrencyCode }} · {{ selected.accountingDate }} · {{ selected.status }}</p>
      <el-alert v-if="occupied" :title="t('finance.paymentCorrection.occupied')" type="warning" :closable="false" />
      <el-form-item :label="t('finance.paymentCorrection.date')"><el-date-picker v-model="form.date" type="date" value-format="YYYY-MM-DD" /></el-form-item>
      <el-form-item :label="t('finance.refund.correctionReason')"><el-input v-model="form.reason" type="textarea" maxlength="500" /></el-form-item>
      <p v-if="selected">{{ t(`finance.paymentCorrection.${selected.status==='DRAFT'?'cancelAction':'reverseAction'}`) }}</p>
      <el-checkbox v-model="form.confirmed">{{ t('finance.paymentCorrection.confirm') }}</el-checkbox>
      <el-button type="danger" :loading="busy" :disabled="busy || !valid" @click="correct">{{ t('finance.paymentCorrection.submit') }}</el-button>
    </el-form>
    <h3>{{ t('finance.paymentCorrection.history') }}</h3>
    <el-table :data="originals" size="small">
      <el-table-column prop="number" :label="t('finance.number')" min-width="160" />
      <el-table-column prop="status" :label="t('finance.statusLabel')" width="110" />
      <el-table-column :label="t('finance.amount')" width="150"><template #default="{row}">{{ row.amount }} {{ row.currencyCode }}</template></el-table-column>
      <el-table-column :label="t('finance.refund.correctionHistory')" min-width="260"><template #default="{row}"><span v-if="row.paymentCorrection">{{ row.paymentCorrection.action }} · {{ row.paymentCorrection.reason }} · {{ row.paymentCorrection.actorUserId }} · {{ row.paymentCorrection.accountingDate }} · {{ row.paymentCorrection.createdAt }} · {{ row.paymentCorrection.journalEntryId || '—' }}</span><span v-else>—</span></template></el-table-column>
    </el-table>
    <template #footer><el-button :disabled="busy" @click="close">{{ t('finance.allocation.close') }}</el-button></template>
  </el-dialog>
</template>
