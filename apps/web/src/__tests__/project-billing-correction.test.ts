/* eslint-disable vue/one-component-per-file -- Focused project billing action stubs. */
import {mount,flushPromises} from '@vue/test-utils'
import {defineComponent} from 'vue'
import {createPinia} from 'pinia'
import {createI18n} from 'vue-i18n'
import {beforeEach,expect,it,vi} from 'vitest'
import Panel from '@/components/projects/ProjectFinancePanel.vue'
import {useAuthStore} from '@/stores/auth'
import en from '@/i18n/locales/en-US'
import * as api from '@/api/project-finance'
vi.mock('@/api/project-finance',()=>({configureProjectBilling:vi.fn(),generateProjectInvoice:vi.fn(),getProjectProfitability:vi.fn(),listProjectBillingBasisV2:vi.fn()}))
vi.mock('element-plus',()=>({ElMessage:{success:vi.fn(),error:vi.fn()}}))
const slot=defineComponent({template:'<div><slot /></div>'})
const button=defineComponent({props:{disabled:Boolean},template:'<button :disabled="disabled"><slot /></button>'})
const dialog=defineComponent({props:{modelValue:Boolean,projectId:{type:String,default:''}},emits:['changed'],template:`<section v-if="modelValue">{{projectId}}<button @click="$emit('changed')">Complete project correction</button></section>`})
const profit=(patch:Partial<api.ProjectProfit>={}):api.ProjectProfit=>({projectId:'project-a',currencyCode:'USD',baseCurrencyCode:'USD',managementLaborCosts:[],contractAmount:100,availableBilling:0,billedAmount:100,postedRevenue:0,postedCost:0,managementLaborCost:0,grossProfit:0,budgetRevenueVariance:0,budgetCostVariance:0,pendingApprovals:0,unsettledInvoices:0,closeEligible:false,...patch})
const basis=(patch:Partial<api.ProjectBillingBasis>={}):api.ProjectBillingBasis=>({id:'basis-a',basisType:'FIXED_PRICE',description:'Actual project basis',availableAmount:100,billedAmount:100,remainingAmount:0,currencyCode:'USD',status:'BILLED',...patch})
function render(permissions=['finance:view']){const pinia=createPinia();useAuthStore(pinia).user={id:'u',username:'u',displayName:'u',organizationId:'org',organizationName:'org',departmentId:null,dataScope:'ALL',permissions,roles:[],mustChangePassword:false};return mount(Panel,{props:{projectId:'project-a'},global:{plugins:[pinia,createI18n({legacy:false,locale:'en-US',messages:{'en-US':en}})],directives:{loading:()=>{}},stubs:{ProjectInvoiceDialog:true,InvoiceCorrectionDialog:dialog,ElCard:slot,ElButton:button,ElSelect:slot,ElOption:true,ElInputNumber:true,ElTable:defineComponent({props:{data:{type:Array,default:()=>[]}},template:'<pre>{{JSON.stringify(data)}}</pre>'}),ElTableColumn:true}}})}
beforeEach(()=>{vi.resetAllMocks();vi.mocked(api.getProjectProfitability).mockResolvedValue(profit());vi.mocked(api.listProjectBillingBasisV2).mockResolvedValue([basis()])})
it('opens corrections for this project and reloads released capacity and basis state after success',async()=>{
 const w=render();await flushPromises();await w.findAll('button').find(b=>b.text()==='Invoice corrections')!.trigger('click');expect(w.text()).toContain('project-a');vi.mocked(api.getProjectProfitability).mockResolvedValue(profit({availableBilling:100,billedAmount:0}));vi.mocked(api.listProjectBillingBasisV2).mockResolvedValue([basis({billedAmount:0,remainingAmount:100,status:'AVAILABLE'})]);await w.findAll('button').find(b=>b.text()==='Complete project correction')!.trigger('click');await flushPromises();expect(api.getProjectProfitability).toHaveBeenCalledTimes(2);expect(api.listProjectBillingBasisV2).toHaveBeenLastCalledWith('project-a');expect(w.text()).toContain('AVAILABLE');expect(w.text()).toContain('"billedAmount":0');expect(w.text()).toContain('"remainingAmount":100')
})
it('keeps the finance correction entry hidden without finance view permission',async()=>{
 const w=render(['project:view']);await flushPromises();expect(w.findAll('button').map(b=>b.text())).not.toContain('Invoice corrections')
})
it('ignores an earlier project profitability response after switching to another project',async()=>{
 let resolve!:(value:api.ProjectProfit)=>void;vi.mocked(api.getProjectProfitability).mockImplementationOnce(()=>new Promise(r=>{resolve=r})).mockResolvedValue(profit({projectId:'project-b',contractAmount:200,availableBilling:200}));const w=render();await w.setProps({projectId:'project-b'});await flushPromises();resolve(profit({contractAmount:999,availableBilling:999}));await flushPromises();expect(w.text()).toContain('$200.00');expect(w.text()).not.toContain('$999.00')
})
it('disables financial actions after project loading fails while allowing an explicit reload',async()=>{
 vi.mocked(api.getProjectProfitability).mockRejectedValue(new Error('Unavailable project evidence'));const w=render();await flushPromises();expect(w.findAll('button').find(b=>b.text()==='Invoice corrections')!.attributes('disabled')).toBeDefined();expect(w.findAll('button').find(b=>b.text()==='Save billing setup')!.attributes('disabled')).toBeDefined();expect(w.findAll('button').find(b=>b.text()==='Reload billing capacity')!.attributes('disabled')).toBeUndefined();expect(api.configureProjectBilling).not.toHaveBeenCalled()
})

it('labels base-currency profit separately from project-currency contract and quota',async()=>{
 vi.mocked(api.getProjectProfitability).mockResolvedValue(profit({currencyCode:'EUR',baseCurrencyCode:'USD',postedRevenue:130,postedCost:20,grossProfit:110,contractAmount:100,availableBilling:100}));const w=render();await flushPromises();expect(w.text()).toContain('Contract and billing quota: EUR');expect(w.text()).toContain('base currency USD');expect(w.text()).toContain('$130.00');expect(w.text()).not.toContain('€130.00');expect(w.text()).toContain('€100.00')
})
