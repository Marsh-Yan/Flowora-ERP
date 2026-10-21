/* eslint-disable vue/one-component-per-file -- Focused project invoice dialog stubs. */
import {mount,flushPromises} from '@vue/test-utils'
import {defineComponent} from 'vue'
import {createPinia} from 'pinia'
import {createI18n} from 'vue-i18n'
import {beforeEach,expect,it,vi} from 'vitest'
import Dialog from '@/components/projects/ProjectInvoiceDialog.vue'
import {useAuthStore} from '@/stores/auth'
import en from '@/i18n/locales/en-US'
import * as api from '@/api/project-finance'
vi.mock('@/api/project-finance',()=>({generateProjectInvoice:vi.fn()}))
vi.mock('element-plus',()=>({ElMessage:{success:vi.fn()}}))
const slot=defineComponent({template:'<div><slot /></div>'})
const dialog=defineComponent({props:{modelValue:Boolean},template:'<section v-if="modelValue"><slot /><slot name="footer" /></section>'})
const button=defineComponent({props:{disabled:Boolean,loading:Boolean},template:'<button :disabled="disabled || loading"><slot /></button>'})
const number=defineComponent({props:{modelValue:{type:Number,default:0},disabled:Boolean},emits:['update:modelValue'],template:`<input type="number" :disabled="disabled" :value="modelValue" @input="$emit('update:modelValue',Number($event.target.value))" />`})
const input=defineComponent({props:{modelValue:{type:String,default:''}},emits:['update:modelValue'],template:`<input type="text" :value="modelValue" @input="$emit('update:modelValue',$event.target.value)" />`})
const checkbox=defineComponent({props:{modelValue:Boolean},emits:['update:modelValue'],template:`<label><input type="checkbox" :checked="modelValue" @change="$emit('update:modelValue',$event.target.checked)" /><slot /></label>`})
const basis:api.ProjectBillingBasis={id:'actual-basis',basisType:'FIXED_PRICE',description:'Approved contract',availableAmount:100,billedAmount:0,remainingAmount:100,currencyCode:'EUR',status:'AVAILABLE'}
function render(currency='EUR',base='USD',permissions=['project:billing'],scope:'ALL'|'SELF'='ALL') {const pinia=createPinia();useAuthStore(pinia).user={id:'u',username:'u',displayName:'u',organizationId:'org',organizationName:'org',departmentId:null,dataScope:scope,permissions,roles:[],mustChangePassword:false};return mount(Dialog,{props:{projectId:'project-a',basis:{...basis,currencyCode:currency},baseCurrencyCode:base},global:{plugins:[pinia,createI18n({legacy:false,locale:'en-US',messages:{'en-US':en}})],stubs:{ElDialog:dialog,ElForm:slot,ElFormItem:slot,ElButton:button,ElInputNumber:number,ElDatePicker:input,ElCheckbox:checkbox,ElAlert:true}}})}
type W=ReturnType<typeof render>
const save=(w:W)=>w.findAll('button').find(b=>b.text()==='Save')!
async function confirm(w:W){await w.find('input[type=checkbox]').setValue(true)}
beforeEach(()=>{vi.resetAllMocks()})
it('does not silently save a foreign invoice at rate one and sends the explicit rate, dates and source',async()=>{
 const w=render();await flushPromises();expect((w.findAll('input[type=number]')[1]!.element as HTMLInputElement).value).toBe('0');expect(save(w).attributes('disabled')).toBeDefined();await w.findAll('input[type=number]')[1]!.setValue(7.2);await w.findAll('input[type=number]')[2]!.setValue(10);await confirm(w);expect(w.text()).toContain('792.0000 USD');await save(w).trigger('click');await flushPromises();expect(api.generateProjectInvoice).toHaveBeenCalledWith('project-a',expect.objectContaining({currencyCode:'EUR',exchangeRate:7.2,exchangeRateDate:expect.any(String),lines:[{billingBasisId:'actual-basis',amount:100,taxRate:10}]}),expect.any(String));expect(w.emitted('changed')).toHaveLength(1)
})
it('locks the same-currency rate to one and blocks missing base currency or permission',async()=>{
 const w=render('USD');await flushPromises();expect(w.findAll('input[type=number]')[1]!.attributes('disabled')).toBeDefined();await confirm(w);await save(w).trigger('click');await flushPromises();expect(vi.mocked(api.generateProjectInvoice).mock.calls[0]![1].exchangeRate).toBe(1);w.unmount();for(const [base,permissions,scope] of [['', ['project:billing'],'ALL'],['USD',[],'ALL'],['USD',['project:billing'],'SELF']] as const){const blocked=render('EUR',base,[...permissions],scope);await flushPromises();await blocked.findAll('input[type=number]')[1]!.setValue(1.3);await confirm(blocked);expect(save(blocked).attributes('disabled')).toBeDefined();blocked.unmount()}
})
it('rejects excess amount, rate precision and rate dates after the accounting date, and resets confirmation on edits',async()=>{
 const w=render();await flushPromises();await w.findAll('input[type=number]')[1]!.setValue(1.3);await confirm(w);expect(save(w).attributes('disabled')).toBeUndefined();await w.findAll('input[type=number]')[0]!.setValue(101);expect((w.find('input[type=checkbox]').element as HTMLInputElement).checked).toBe(false);await confirm(w);expect(save(w).attributes('disabled')).toBeDefined();await w.findAll('input[type=number]')[0]!.setValue(100);await w.findAll('input[type=number]')[1]!.setValue(1.123456789);await confirm(w);expect(save(w).attributes('disabled')).toBeDefined();await w.findAll('input[type=number]')[1]!.setValue(1.3);await w.findAll('input[type=date]')[0]!.setValue('2026-10-01');await w.findAll('input[type=date]')[1]!.setValue('2026-10-02');await confirm(w);expect(save(w).attributes('disabled')).toBeDefined()
})
it('retains the request key for an uncertain retry and blocks duplicate saves and pending close',async()=>{
 let reject!:(e:Error)=>void;vi.mocked(api.generateProjectInvoice).mockImplementationOnce(()=>new Promise((_r,j)=>{reject=j}));const w=render();await flushPromises();await w.findAll('input[type=number]')[1]!.setValue(1.3);await confirm(w);await save(w).trigger('click');await save(w).trigger('click');await w.findAll('button').find(b=>b.text()==='Close')!.trigger('click');expect(api.generateProjectInvoice).toHaveBeenCalledTimes(1);expect(w.emitted('close')).toBeUndefined();reject(new Error('Lost response'));await flushPromises();await save(w).trigger('click');await flushPromises();expect(vi.mocked(api.generateProjectInvoice).mock.calls[0]![2]).toBe(vi.mocked(api.generateProjectInvoice).mock.calls[1]![2])
})
it('changes the key only after changed details have been checked again',async()=>{
 vi.mocked(api.generateProjectInvoice).mockRejectedValueOnce(new Error('Lost response'));const w=render();await flushPromises();await w.findAll('input[type=number]')[1]!.setValue(1.3);await confirm(w);await save(w).trigger('click');await flushPromises();await w.findAll('input[type=number]')[1]!.setValue(1.5);expect(save(w).attributes('disabled')).toBeDefined();await confirm(w);await save(w).trigger('click');await flushPromises();expect(vi.mocked(api.generateProjectInvoice).mock.calls[0]![2]).not.toBe(vi.mocked(api.generateProjectInvoice).mock.calls[1]![2])
})
it('drops old project save results and clears rate/confirmation after project context changes',async()=>{
 let resolve!:(v:Awaited<ReturnType<typeof api.generateProjectInvoice>>)=>void;vi.mocked(api.generateProjectInvoice).mockImplementationOnce(()=>new Promise(r=>{resolve=r}));const w=render();await flushPromises();await w.findAll('input[type=number]')[1]!.setValue(1.3);await confirm(w);await save(w).trigger('click');await w.setProps({projectId:'project-b',basis:{...basis,id:'other-basis'}});resolve({id:'old'} as Awaited<ReturnType<typeof api.generateProjectInvoice>>);await flushPromises();expect(w.emitted('changed')).toBeUndefined();expect(w.emitted('close')).toBeUndefined();expect((w.findAll('input[type=number]')[1]!.element as HTMLInputElement).value).toBe('0');expect((w.find('input[type=checkbox]').element as HTMLInputElement).checked).toBe(false)
})
