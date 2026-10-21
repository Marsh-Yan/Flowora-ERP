<script setup lang="ts">
/* global crypto */
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import { generateProjectInvoice, type ProjectBillingBasis, type ProjectInvoiceInput } from '@/api/project-finance'
const props=defineProps<{projectId:string;basis:ProjectBillingBasis | null;baseCurrencyCode:string}>()
const emit=defineEmits<{close:[];changed:[]}>()
const {t}=useI18n(),auth=useAuthStore(),busy=ref(false),uncertain=ref(false)
const today=()=>{const d=new Date();return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`}
const form=reactive({amount:0,rate:0,date:today(),rateDate:today(),tax:0,confirmed:false})
let generation=0,requestKey='',fingerprint=''
const sameCurrency=computed(()=>props.basis?.currencyCode===props.baseCurrencyCode)
const decimal=(n:number,p:number)=>Number.isFinite(n)&&n>0&&Number(n.toFixed(p))===n
const validDate=(s:string)=>{if(!/^\d{4}-\d{2}-\d{2}$/.test(s))return false;const d=new Date(`${s}T12:00:00`);return !Number.isNaN(d.getTime())&&`${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`===s}
const valid=computed(()=>!!props.basis && /^[A-Z]{3}$/.test(props.baseCurrencyCode) && /^[A-Z]{3}$/.test(props.basis.currencyCode) && auth.hasPermission('project:billing') && auth.user?.dataScope==='ALL' && decimal(form.amount,4) && form.amount<=props.basis.remainingAmount && decimal(form.rate,8) && (!sameCurrency.value || form.rate===1) && validDate(form.date) && validDate(form.rateDate) && form.rateDate<=form.date && form.rateDate<=today() && Number.isFinite(form.tax) && form.tax>=0 && form.tax<=100 && Number(form.tax.toFixed(4))===form.tax && form.confirmed)
function reset(){generation++;busy.value=false;uncertain.value=false;requestKey='';fingerprint='';Object.assign(form,{amount:props.basis?.remainingAmount??0,rate:sameCurrency.value?1:0,date:today(),rateDate:today(),tax:0,confirmed:false})}
watch(()=>[props.projectId,props.basis,props.baseCurrencyCode,auth.user?.organizationId],reset,{immediate:true})
watch(()=>[form.amount,form.rate,form.date,form.rateDate,form.tax],()=>{form.confirmed=false})
onBeforeUnmount(()=>{generation++})
function close(){if(!busy.value)emit('close')}
async function save(){
 if(busy.value || !valid.value || !props.basis)return
 const body:ProjectInvoiceInput={businessDate:form.date,accountingDate:form.date,dueDate:form.date,exchangeRateDate:form.rateDate,currencyCode:props.basis.currencyCode,exchangeRate:form.rate,lines:[{billingBasisId:props.basis.id,amount:form.amount,taxRate:form.tax}]}
 const next=JSON.stringify([props.projectId,body]);if(next!==fingerprint){requestKey=crypto.randomUUID();fingerprint=next}
 const token=generation,id=props.projectId;busy.value=true
 try{await generateProjectInvoice(id,body,requestKey);if(token===generation){ElMessage.success(t('projects.m4.invoiceCreated'));emit('changed');emit('close')}}
 catch{if(token===generation)uncertain.value=true}
 finally{if(token===generation)busy.value=false}
}
</script>
<template>
 <el-dialog :model-value="!!basis" :title="t('projects.m4.createInvoice')" width="min(640px, calc(100vw - 32px))" destroy-on-close :show-close="!busy" :close-on-click-modal="!busy" :close-on-press-escape="!busy" @update:model-value="close">
  <p>{{basis?.description}} · {{basis?.currencyCode}} → {{baseCurrencyCode || '—'}}</p>
  <p>{{t('projects.m4.invoiceTerms')}}</p>
  <el-alert v-if="uncertain" :title="t('projects.m4.invoiceUncertain')" type="error" :closable="false" />
  <el-form label-position="top" :disabled="busy">
   <el-form-item :label="t('projects.amount')"><el-input-number v-model="form.amount" :min="0" :max="basis?.remainingAmount" :precision="4" :controls="false" /></el-form-item>
   <el-form-item :label="t('finance.foreign.rate')"><el-input-number v-model="form.rate" :disabled="sameCurrency" :min="0" :precision="8" :controls="false" /></el-form-item>
   <p>{{t('finance.foreign.direction',{currency:basis?.currencyCode || '—',base:baseCurrencyCode || '—'})}}</p>
   <el-form-item :label="t('projects.m4.invoiceDate')"><el-date-picker v-model="form.date" type="date" value-format="YYYY-MM-DD" :clearable="false" /></el-form-item>
   <el-form-item :label="t('finance.foreign.rateDate')"><el-date-picker v-model="form.rateDate" type="date" value-format="YYYY-MM-DD" :clearable="false" /></el-form-item>
   <el-form-item :label="t('finance.m4.taxRate')"><el-input-number v-model="form.tax" :min="0" :max="100" :precision="4" :controls="false" /></el-form-item>
   <p v-if="form.rate>0">{{t('projects.m4.invoiceBasePreview',{amount:(form.amount*(1+form.tax/100)*form.rate).toFixed(4),base:baseCurrencyCode})}}</p>
   <el-checkbox v-model="form.confirmed">{{t('projects.m4.invoiceConfirm')}}</el-checkbox>
  </el-form>
  <template #footer><el-button :disabled="busy" @click="close">{{t('finance.bankEntry.close')}}</el-button><el-button type="primary" :loading="busy" :disabled="busy || !valid" @click="save">{{t('projects.save')}}</el-button></template>
 </el-dialog>
</template>
