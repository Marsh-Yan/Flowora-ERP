/* eslint-disable vue/one-component-per-file -- Focused financial action stubs. */
import { mount, flushPromises } from '@vue/test-utils'
import { defineComponent } from 'vue'
import { createPinia } from 'pinia'
import { createI18n } from 'vue-i18n'
import { beforeEach, expect, it, vi } from 'vitest'
import { useAuthStore } from '@/stores/auth'
import Dialog from '@/components/finance/PaymentCorrectionDialog.vue'
import en from '@/i18n/locales/en-US'
import * as api from '@/api/finance-v2'
vi.mock('@/api/finance-v2',()=>({listFinancePayments:vi.fn(),listFinanceBankAccounts:vi.fn(),createFinancePayment:vi.fn(),postFinancePayment:vi.fn(),correctFinanceRefund:vi.fn(),recoverFinanceRefundSource:vi.fn(),correctFinancePayment:vi.fn()}))
const slot=defineComponent({template:'<div><slot /></div>'})
const button=defineComponent({props:{disabled:Boolean,loading:Boolean},template:'<button :disabled="disabled || loading"><slot /></button>'})
const dialog=defineComponent({props:{modelValue:Boolean},template:'<section v-if="modelValue"><slot /><slot name="footer" /></section>'})
const alert=defineComponent({props:{title:{type:String,default:''}},template:'<p>{{title}}</p>'})
const select=defineComponent({props:{modelValue:{type:String,default:''}},emits:['update:modelValue'],template:`<select :value="modelValue" @change="$emit('update:modelValue',$event.target.value)"><option value=""></option><slot /></select>`})
const option=defineComponent({props:{value:{type:String,default:''},label:{type:String,default:''}},template:'<option :value="value">{{label}}</option>'})
const number=defineComponent({props:{modelValue:{type:Number,default:0}},emits:['update:modelValue'],template:`<input type="number" :value="modelValue" @input="$emit('update:modelValue',Number($event.target.value))" />`})
const text=defineComponent({props:{modelValue:{type:String,default:''}},emits:['update:modelValue'],template:`<input type="text" :value="modelValue" @input="$emit('update:modelValue',$event.target.value)" />`})
const date=defineComponent({props:{modelValue:{type:String,default:''}},emits:['update:modelValue'],template:`<input type="date" :value="modelValue" @input="$emit('update:modelValue',$event.target.value)" />`})
const checkbox=defineComponent({props:{modelValue:Boolean},emits:['update:modelValue'],template:`<label><input type="checkbox" :checked="modelValue" @change="$emit('update:modelValue',$event.target.checked)" /><slot /></label>`})
const source=(patch:Partial<api.FinancePayment>={}):api.FinancePayment=>({id:'actual-receipt',number:'RC-1',paymentType:'RECEIPT',partyType:'CUSTOMER',partyId:'actual-customer',status:'POSTED',allocationStatus:'UNALLOCATED',accountingDate:'2026-10-01',currencyCode:'USD',baseCurrencyCode:'CNY',exchangeRate:7.2,exchangeRateDate:'2026-10-01',amount:100,allocatedAmount:20,version:1,allocations:[],...patch})
const refund=(patch:Partial<api.FinancePayment>={}):api.FinancePayment=>source({id:'actual-refund',number:'CR-1',paymentType:'CUSTOMER_REFUND',status:'DRAFT',amount:30,allocatedAmount:0,originalPaymentId:'actual-receipt',...patch})
function render(permissions=['finance:view','finance:create','finance:post'],scope:'ALL'|'SELF'='ALL') {const pinia=createPinia();useAuthStore(pinia).user={id:'u',username:'u',displayName:'u',organizationId:'o',organizationName:'o',departmentId:null,dataScope:scope,permissions,roles:[],mustChangePassword:false};return mount(Dialog,{props:{modelValue:true},global:{plugins:[pinia,createI18n({legacy:false,locale:'en-US',messages:{'en-US':en}})],stubs:{ElDialog:dialog,ElAlert:alert,ElButton:button,ElForm:slot,ElFormItem:slot,ElSelect:select,ElOption:option,ElInputNumber:number,ElInput:text,ElDatePicker:date,ElCheckbox:checkbox,ElTable:defineComponent({props:{data:{type:Array,default:()=>[]}},template:'<pre>{{JSON.stringify(data)}}</pre>'}),ElTableColumn:true}}})}

type W=ReturnType<typeof render>
const click=async(w:W,label:string)=>{await w.findAll('button').find(b=>b.text()===label)!.trigger('click');await flushPromises()}
const fill=async(w:W)=>{await w.find('select').setValue('actual-receipt');await w.find('input[type=textarea]').setValue('Verified mistaken original');await w.find('input[type=checkbox]').setValue(true)}
beforeEach(()=>{vi.resetAllMocks();vi.mocked(api.listFinancePayments).mockResolvedValue([source({allocatedAmount:0})]);vi.mocked(api.correctFinancePayment).mockResolvedValue(source({status:'REVERSED'}))})
it('requires explicit original selection and reconfirmation after reason/date/version changes',async()=>{
 const nextDay=new Date();nextDay.setDate(nextDay.getDate()+1);const changedDate=`${nextDay.getFullYear()}-${String(nextDay.getMonth()+1).padStart(2,'0')}-${String(nextDay.getDate()).padStart(2,'0')}`
 const w=render();await flushPromises();expect(w.find('select').element.value).toBe('');await click(w,'Confirm payment correction');expect(api.correctFinancePayment).not.toHaveBeenCalled();await fill(w);await w.find('input[type=textarea]').setValue('Changed evidence');await click(w,'Confirm payment correction');expect(api.correctFinancePayment).not.toHaveBeenCalled();await w.find('input[type=checkbox]').setValue(true);await w.find('input[type=date]').setValue(changedDate);await click(w,'Confirm payment correction');expect(api.correctFinancePayment).not.toHaveBeenCalled();await w.find('input[type=checkbox]').setValue(true);vi.mocked(api.listFinancePayments).mockResolvedValue([source({allocatedAmount:0,version:2})]);await click(w,'Reload');await click(w,'Confirm payment correction');expect(api.correctFinancePayment).not.toHaveBeenCalled();await w.find('input[type=checkbox]').setValue(true);await click(w,'Confirm payment correction');expect(api.correctFinancePayment).toHaveBeenCalledWith('actual-receipt','reverse',{version:2,accountingDate:changedDate,reason:'Changed evidence'},expect.any(String))
})
it('uses draft cancellation with create permission and posting reversal with post permission',async()=>{
 vi.mocked(api.listFinancePayments).mockResolvedValue([source({status:'DRAFT',allocatedAmount:0})]);const w=render(['finance:view','finance:create']);await flushPromises();await fill(w);await click(w,'Confirm payment correction');expect(api.correctFinancePayment).toHaveBeenCalledWith('actual-receipt','cancel',expect.objectContaining({version:1}),expect.any(String));w.unmount();vi.mocked(api.listFinancePayments).mockResolvedValue([source({allocatedAmount:0})]);const x=render(['finance:view','finance:create']);await flushPromises();expect(x.findAll('option').map(o=>o.attributes('value'))).not.toContain('actual-receipt')
})
it('blocks current active allocation, linked refund and unresolved same-party legacy refund',async()=>{
 for(const rows of [[source()],[source({allocatedAmount:0,allocations:[{id:'a',paymentId:'actual-receipt',invoiceId:'i',amount:1,baseAmount:1,realizedExchangeDifference:0,allocatedAt:'2026-10-01',status:'ACTIVE'}]})],[source({allocatedAmount:0}),refund()],[source({allocatedAmount:0}),refund({originalPaymentId:undefined})]]) {vi.mocked(api.listFinancePayments).mockResolvedValue(rows);const w=render();await flushPromises();await fill(w);await click(w,'Confirm payment correction');expect(api.correctFinancePayment).not.toHaveBeenCalled();expect(w.text()).toContain('Resolve allocations');w.unmount()}
})
it('preserves correction history for viewers and rejects limited organization scope',async()=>{
 vi.mocked(api.listFinancePayments).mockResolvedValue([source({status:'REVERSED',paymentCorrection:{action:'REVERSE',reason:'Persisted original correction',actorUserId:'operator',accountingDate:'2026-10-17',createdAt:'2026-10-17',journalEntryId:'actual-opposite-journal'}})]);const w=render(['finance:view']);await flushPromises();expect(w.find('select').exists()).toBe(false);expect(w.text()).toContain('Persisted original correction');expect(w.text()).toContain('actual-opposite-journal');w.unmount();vi.clearAllMocks();render(['finance:view','finance:post'],'SELF');await flushPromises();expect(api.listFinancePayments).not.toHaveBeenCalled()
})
it('reuses the same key on explicit uncertain retry and ignores late completion after close',async()=>{
 vi.mocked(api.correctFinancePayment).mockRejectedValueOnce(new Error('lost result'));const w=render();await flushPromises();await fill(w);await click(w,'Confirm payment correction');await click(w,'Confirm payment correction');expect(api.correctFinancePayment).toHaveBeenCalledTimes(2);expect(vi.mocked(api.correctFinancePayment).mock.calls[0]![3]).toBe(vi.mocked(api.correctFinancePayment).mock.calls[1]![3]);await fill(w);let resolve!:(r:api.FinancePayment)=>void;vi.mocked(api.correctFinancePayment).mockImplementationOnce(()=>new Promise(r=>{resolve=r}));const before=w.emitted('changed')?.length ?? 0;await click(w,'Confirm payment correction');await w.setProps({modelValue:false});resolve(source());await flushPromises();expect(w.emitted('changed')?.length ?? 0).toBe(before)
})
