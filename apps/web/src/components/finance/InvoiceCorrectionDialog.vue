<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import { correctFinanceInvoice, listFinanceInvoices, type FinanceInvoice } from '@/api/finance-v2'
const props=defineProps<{modelValue:boolean}>()
const emit=defineEmits<{'update:modelValue':[value:boolean];changed:[]}>()
const auth=useAuthStore(),{t}=useI18n()
const rows=ref<FinanceInvoice[]>([]),loading=ref(false),busy=ref(false),failed=ref(false),uncertain=ref(false)
const form=reactive({id:'',date:'',reason:'',confirmed:false})
let generation=0,key='',fingerprint=''
const today=()=>{const d=new Date();return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`}
const canView=computed(()=>auth.hasPermission('finance:view'))
const canCreate=computed(()=>canView.value && auth.hasPermission('finance:invoice'))
const canPost=computed(()=>canView.value && auth.hasPermission('finance:post'))
const originals=computed(()=>rows.value)
const candidates=computed(()=>originals.value.filter(r=>(r.status==='DRAFT' && canCreate.value)||(r.status==='POSTED' && ['SALES_INVOICE','SUPPLIER_INVOICE'].includes(r.documentType) && canPost.value)))
const selected=computed(()=>candidates.value.find(r=>r.id===form.id))
const occupied=computed(()=>!!selected.value && (selected.value.allocatedAmount!==0 || selected.value.creditedAmount!==0 || rows.value.some(r=>['DRAFT','POSTED'].includes(r.status) && r.originalInvoiceId===selected.value!.id)))
const validDate=computed(()=>/^\d{4}-\d{2}-\d{2}$/.test(form.date) && Number.isFinite(Date.parse(form.date)) && new Date(form.date).toISOString().slice(0,10)===form.date)
const valid=computed(()=>props.modelValue && canView.value && !loading.value && !failed.value && !!selected.value && !occupied.value && validDate.value && form.date>=selected.value.accountingDate && !!form.reason.trim() && form.reason.trim().length<=500 && form.confirmed)
watch(()=>JSON.stringify([selected.value,occupied.value,form.date,form.reason]),()=>{form.confirmed=false})
async function load(){
  const token=++generation
  if(!props.modelValue || !canView.value){rows.value=[];loading.value=false;return}
  loading.value=true;failed.value=false
  try{const value=await listFinanceInvoices();if(token===generation)rows.value=value}
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
  try{await correctFinanceInvoice(row.id,action,body,key);if(token===generation){form.id='';form.reason='';form.confirmed=false}}
  catch{if(token===generation)uncertain.value=true}
  finally{if(token===generation){const refresh=generation+1;await load();if(generation===refresh)emit('changed')}busy.value=false}
}
</script>
<template>
  <el-dialog :model-value="modelValue" :title="t('finance.invoiceCorrection.title')" width="min(900px, calc(100vw - 32px))" destroy-on-close :show-close="!busy" :close-on-click-modal="!busy" :close-on-press-escape="!busy" @update:model-value="close">
    <p>{{ t('finance.invoiceCorrection.terms') }}</p>
    <el-alert v-if="failed" :title="t('finance.invoiceCorrection.failed')" type="error" :closable="false" />
    <el-alert v-if="uncertain" :title="t('finance.refund.uncertain')" type="warning" :closable="false" />
    <el-button :disabled="busy || loading" @click="load">{{ t('finance.m4.retry') }}</el-button>
    <el-form v-if="canCreate || canPost" label-position="top" :disabled="busy || loading || failed">
      <el-form-item :label="t('finance.invoiceCorrection.select')"><el-select v-model="form.id" class="full-width" filterable><el-option v-for="row in candidates" :key="row.id" :value="row.id" :label="`${row.number} · ${row.documentType} · ${row.status} · ${row.totalAmount} ${row.currencyCode}`" /></el-select></el-form-item>
      <p v-if="selected">{{ selected.number }} · {{ selected.partyId }} · {{ selected.totalAmount }} {{ selected.currencyCode }} · {{ selected.accountingDate }} · {{ selected.status }}</p>
      <el-alert v-if="occupied" :title="t('finance.invoiceCorrection.occupied')" type="warning" :closable="false" />
      <el-form-item :label="t('finance.invoiceCorrection.date')"><el-date-picker v-model="form.date" type="date" value-format="YYYY-MM-DD" /></el-form-item>
      <el-form-item :label="t('finance.refund.correctionReason')"><el-input v-model="form.reason" type="textarea" maxlength="500" /></el-form-item>
      <p v-if="selected">{{ t(`finance.invoiceCorrection.${selected.status==='DRAFT'?'cancelAction':'reverseAction'}`) }}</p>
      <el-checkbox v-model="form.confirmed">{{ t('finance.invoiceCorrection.confirm') }}</el-checkbox>
      <el-button type="danger" :loading="busy" :disabled="busy || !valid" @click="correct">{{ t('finance.invoiceCorrection.submit') }}</el-button>
    </el-form>
    <h3>{{ t('finance.invoiceCorrection.history') }}</h3>
    <el-table :data="originals" size="small">
      <el-table-column prop="number" :label="t('finance.number')" min-width="160" />
      <el-table-column prop="status" :label="t('finance.statusLabel')" width="110" />
      <el-table-column :label="t('finance.amount')" width="150"><template #default="{row}">{{ row.totalAmount }} {{ row.currencyCode }}</template></el-table-column>
      <el-table-column :label="t('finance.refund.correctionHistory')" min-width="260"><template #default="{row}"><span v-if="row.correction">{{ row.correction.action }} · {{ row.correction.reason }} · {{ row.correction.actorUserId }} · {{ row.correction.accountingDate }} · {{ row.correction.createdAt }} · {{ row.correction.journalEntryId || '—' }}</span><span v-else>—</span></template></el-table-column>
    </el-table>
    <template #footer><el-button :disabled="busy" @click="close">{{ t('finance.allocation.close') }}</el-button></template>
  </el-dialog>
</template>
