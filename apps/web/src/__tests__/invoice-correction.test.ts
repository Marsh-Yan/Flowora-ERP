/* eslint-disable vue/one-component-per-file -- Focused financial action stubs. */
import { mount, flushPromises } from '@vue/test-utils'
import { defineComponent } from 'vue'
import { createPinia } from 'pinia'
import { createI18n } from 'vue-i18n'
import { beforeEach, expect, it, vi } from 'vitest'
import { useAuthStore } from '@/stores/auth'
import Dialog from '@/components/finance/InvoiceCorrectionDialog.vue'
import en from '@/i18n/locales/en-US'
import * as api from '@/api/finance-v2'
vi.mock('@/api/finance-v2',()=>({listFinanceInvoices:vi.fn(),listFinanceBankAccounts:vi.fn(),createFinanceInvoice:vi.fn(),postFinanceInvoice:vi.fn(),correctFinanceRefund:vi.fn(),recoverFinanceRefundSource:vi.fn(),correctFinanceInvoice:vi.fn()}))
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
const source=(patch:Partial<api.FinanceInvoice>={}):api.FinanceInvoice=>({id:'actual-receipt',number:'SI-1',documentType:'SALES_INVOICE',partyType:'CUSTOMER',partyId:'actual-customer',status:'POSTED',settlementStatus:'UNPAID',matchStatus:'NOT_REQUIRED',dueDate:'2026-10-31',accountingDate:'2026-10-01',currencyCode:'USD',totalAmount:100,allocatedAmount:20,creditedAmount:0,version:1,...patch})
const refund=(patch:Partial<api.FinanceInvoice>={}):api.FinanceInvoice=>source({id:'actual-credit',number:'CC-1',documentType:'CUSTOMER_CREDIT',status:'DRAFT',totalAmount:30,allocatedAmount:0,originalInvoiceId:'actual-receipt',...patch})
function render(permissions=['finance:view','finance:invoice','finance:post'],scope:'ALL'|'SELF'='ALL') {const pinia=createPinia();useAuthStore(pinia).user={id:'u',username:'u',displayName:'u',organizationId:'o',organizationName:'o',departmentId:null,dataScope:scope,permissions,roles:[],mustChangePassword:false};return mount(Dialog,{props:{modelValue:true},global:{plugins:[pinia,createI18n({legacy:false,locale:'en-US',messages:{'en-US':en}})],stubs:{ElDialog:dialog,ElAlert:alert,ElButton:button,ElForm:slot,ElFormItem:slot,ElSelect:select,ElOption:option,ElInputNumber:number,ElInput:text,ElDatePicker:date,ElCheckbox:checkbox,ElTable:defineComponent({props:{data:{type:Array,default:()=>[]}},template:'<pre>{{JSON.stringify(data)}}</pre>'}),ElTableColumn:true}}})}

type W=ReturnType<typeof render>
const click=async(w:W,label:string)=>{await w.findAll('button').find(b=>b.text()===label)!.trigger('click');await flushPromises()}
const fill=async(w:W)=>{await w.find('select').setValue('actual-receipt');await w.find('input[type=textarea]').setValue('Verified mistaken original');await w.find('input[type=checkbox]').setValue(true)}
beforeEach(()=>{vi.resetAllMocks();vi.mocked(api.listFinanceInvoices).mockResolvedValue([source({allocatedAmount:0})]);vi.mocked(api.correctFinanceInvoice).mockResolvedValue(source({status:'REVERSED'}))})
it('requires explicit original selection and reconfirmation after reason/date/version changes',async()=>{
 const nextDay=new Date();nextDay.setDate(nextDay.getDate()+1);const changedDate=`${nextDay.getFullYear()}-${String(nextDay.getMonth()+1).padStart(2,'0')}-${String(nextDay.getDate()).padStart(2,'0')}`
 const w=render();await flushPromises();expect(w.find('select').element.value).toBe('');await click(w,'Confirm invoice correction');expect(api.correctFinanceInvoice).not.toHaveBeenCalled();await fill(w);await w.find('input[type=textarea]').setValue('Changed evidence');await click(w,'Confirm invoice correction');expect(api.correctFinanceInvoice).not.toHaveBeenCalled();await w.find('input[type=checkbox]').setValue(true);await w.find('input[type=date]').setValue(changedDate);await click(w,'Confirm invoice correction');expect(api.correctFinanceInvoice).not.toHaveBeenCalled();await w.find('input[type=checkbox]').setValue(true);vi.mocked(api.listFinanceInvoices).mockResolvedValue([source({allocatedAmount:0,version:2})]);await click(w,'Reload');await click(w,'Confirm invoice correction');expect(api.correctFinanceInvoice).not.toHaveBeenCalled();await w.find('input[type=checkbox]').setValue(true);await click(w,'Confirm invoice correction');expect(api.correctFinanceInvoice).toHaveBeenCalledWith('actual-receipt','reverse',{version:2,accountingDate:changedDate,reason:'Changed evidence'},expect.any(String))
})
it('uses draft cancellation with create permission and posting reversal with post permission',async()=>{
 vi.mocked(api.listFinanceInvoices).mockResolvedValue([source({status:'DRAFT',allocatedAmount:0})]);const w=render(['finance:view','finance:invoice']);await flushPromises();await fill(w);await click(w,'Confirm invoice correction');expect(api.correctFinanceInvoice).toHaveBeenCalledWith('actual-receipt','cancel',expect.objectContaining({version:1}),expect.any(String));w.unmount();vi.mocked(api.listFinanceInvoices).mockResolvedValue([source({allocatedAmount:0})]);const x=render(['finance:view','finance:invoice']);await flushPromises();expect(x.findAll('option').map(o=>o.attributes('value'))).not.toContain('actual-receipt')
})
it('blocks allocations, credited amounts and linked credit drafts or posted credits',async()=>{
 for(const rows of [[source()],[source({allocatedAmount:0,creditedAmount:1})],[source({allocatedAmount:0}),refund()],[source({allocatedAmount:0}),refund({status:'POSTED'})]]) {vi.mocked(api.listFinanceInvoices).mockResolvedValue(rows);const w=render();await flushPromises();await fill(w);await click(w,'Confirm invoice correction');expect(api.correctFinanceInvoice).not.toHaveBeenCalled();expect(w.text()).toContain('Resolve allocations');w.unmount()}
})
it('allows cancelling a credit draft but never offers posted credit reversal',async()=>{
 vi.mocked(api.listFinanceInvoices).mockResolvedValue([refund(),refund({id:'posted-credit',status:'POSTED'})]);const w=render();await flushPromises();expect(w.findAll('option').map(o=>o.attributes('value'))).not.toContain('posted-credit');await w.find('select').setValue('actual-credit');await w.find('input[type=textarea]').setValue('Unused credit');await w.find('input[type=checkbox]').setValue(true);await click(w,'Confirm invoice correction');expect(api.correctFinanceInvoice).toHaveBeenCalledWith('actual-credit','cancel',expect.any(Object),expect.any(String))
})
it('preserves correction history for viewers and rejects limited organization scope',async()=>{
 vi.mocked(api.listFinanceInvoices).mockResolvedValue([source({status:'REVERSED',correction:{action:'REVERSE',reason:'Persisted original correction',actorUserId:'operator',accountingDate:'2026-10-17',createdAt:'2026-10-17',journalEntryId:'actual-opposite-journal'}})]);const w=render(['finance:view']);await flushPromises();expect(w.find('select').exists()).toBe(false);expect(w.text()).toContain('Persisted original correction');expect(w.text()).toContain('actual-opposite-journal');w.unmount();vi.clearAllMocks();render(['finance:view','finance:post'],'SELF');await flushPromises();expect(api.listFinanceInvoices).not.toHaveBeenCalled()
})
it('reuses the same key on explicit uncertain retry and ignores late completion after close',async()=>{
 vi.mocked(api.correctFinanceInvoice).mockRejectedValueOnce(new Error('lost result'));const w=render();await flushPromises();await fill(w);await click(w,'Confirm invoice correction');await click(w,'Confirm invoice correction');expect(api.correctFinanceInvoice).toHaveBeenCalledTimes(2);expect(vi.mocked(api.correctFinanceInvoice).mock.calls[0]![3]).toBe(vi.mocked(api.correctFinanceInvoice).mock.calls[1]![3]);await fill(w);let resolve!:(r:api.FinanceInvoice)=>void;vi.mocked(api.correctFinanceInvoice).mockImplementationOnce(()=>new Promise(r=>{resolve=r}));const before=w.emitted('changed')?.length ?? 0;await click(w,'Confirm invoice correction');await w.setProps({modelValue:false});resolve(source());await flushPromises();expect(w.emitted('changed')?.length ?? 0).toBe(before)
})
