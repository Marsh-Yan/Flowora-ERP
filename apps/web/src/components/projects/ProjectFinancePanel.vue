<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import {
  configureProjectBilling, generateProjectInvoice, getProjectProfitability, listProjectBillingBasisV2,
  type ProjectBillingBasis, type ProjectProfit,
} from '@/api/project-finance'

const props = defineProps<{ projectId: string }>()
const { t, locale } = useI18n()
const loading = ref(false)
const profit = ref<ProjectProfit | null>(null)
const basis = ref<ProjectBillingBasis[]>([])
const mode = ref('TIME_MATERIAL')
const contractAmount = ref(0)
function money(value?: number, currency?: string) { return new Intl.NumberFormat(locale.value, { style: 'currency', currency: currency || 'USD', maximumFractionDigits: 2 }).format(Number(value ?? 0)) }
async function load() { loading.value = true; try { [profit.value, basis.value] = await Promise.all([getProjectProfitability(props.projectId), listProjectBillingBasisV2(props.projectId)]); contractAmount.value = Number(profit.value.contractAmount) } catch { profit.value = null; basis.value = [] } finally { loading.value = false } }
async function saveConfiguration() { try { await configureProjectBilling(props.projectId, { billingMode: mode.value, contractAmount: contractAmount.value }); ElMessage.success(t('projects.m4.saved')); await load() } catch { ElMessage.error(t('projects.saveFailed')) } }
async function invoice(row: ProjectBillingBasis) { try { await generateProjectInvoice(props.projectId, row, new Date().toISOString().slice(0, 10)); ElMessage.success(t('projects.m4.invoiceCreated')); await load() } catch { ElMessage.error(t('projects.saveFailed')) } }
watch(() => props.projectId, load)
onMounted(load)
</script>

<template>
  <el-card v-loading="loading" shadow="never" class="project-finance">
    <div class="section-heading"><div><span class="eyebrow">M4 · Project finance</span><h3>{{ t('projects.m4.title') }}</h3><p>{{ t('projects.m4.subtitle') }}</p></div><div class="billing-config"><el-select v-model="mode"><el-option label="Fixed price" value="FIXED_PRICE" /><el-option label="Milestone" value="MILESTONE" /><el-option label="Time & material" value="TIME_MATERIAL" /></el-select><el-input-number v-model="contractAmount" :min="0" :precision="2" /><el-button type="primary" @click="saveConfiguration">{{ t('projects.m4.saveBilling') }}</el-button></div></div>
    <div class="profit-grid">
      <div><span>{{ t('projects.m4.contract') }}</span><strong>{{ money(profit?.contractAmount, profit?.currencyCode) }}</strong></div>
      <div><span>{{ t('projects.m4.available') }}</span><strong>{{ money(profit?.availableBilling, profit?.currencyCode) }}</strong></div>
      <div><span>{{ t('projects.m4.revenue') }}</span><strong>{{ money(profit?.postedRevenue, profit?.currencyCode) }}</strong></div>
      <div><span>{{ t('projects.m4.cost') }}</span><strong>{{ money(profit?.postedCost, profit?.currencyCode) }}</strong></div>
      <div><span>{{ t('projects.m4.grossProfit') }}</span><strong>{{ money(profit?.grossProfit, profit?.currencyCode) }}</strong></div>
      <div><span>{{ t('projects.m4.closeEligible') }}</span><strong>{{ profit?.closeEligible ? t('projects.yes') : t('projects.no') }}</strong></div>
    </div>
    <el-table :data="basis" size="small"><el-table-column prop="basisType" :label="t('projects.type')" width="130" /><el-table-column prop="description" :label="t('projects.description')" min-width="200" /><el-table-column :label="t('projects.m4.available')" width="140"><template #default="{ row }">{{ money(row.remainingAmount, row.currencyCode) }}</template></el-table-column><el-table-column prop="status" :label="t('projects.statusLabel')" width="110" /><el-table-column :label="t('finance.actions')" width="140"><template #default="{ row }"><el-button v-if="row.remainingAmount > 0" link type="primary" @click="invoice(row)">{{ t('projects.m4.createInvoice') }}</el-button></template></el-table-column></el-table>
  </el-card>
</template>

<style scoped>
.project-finance{margin:0 0 18px}.billing-config{display:flex;gap:8px;align-items:center}.billing-config .el-select{width:150px}.profit-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:10px;margin:14px 0}.profit-grid div{padding:12px;border-radius:10px;background:#f6f8fb}.profit-grid span{display:block;color:var(--flowora-muted);font-size:11px}.profit-grid strong{display:block;margin-top:4px}@media(max-width:900px){.billing-config{flex-wrap:wrap}.profit-grid{grid-template-columns:repeat(2,minmax(0,1fr))}}
</style>
