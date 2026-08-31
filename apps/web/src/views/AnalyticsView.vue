<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import {
  createExport, downloadExport, getAnalytics, getDiagnostics, getExports, getOrganizationAnalytics,
  getSavedViews, removeView, saveView, type AnalyticsSnapshot, type DiagnosticSnapshot,
  type ExportJob, type OrganizationSummary, type SavedView,
} from '@/api/analytics'

const auth = useAuthStore()
const now = new Date()
const start = new Date(now.getFullYear(), now.getMonth() - 5, 1)
const dateRange = ref<[string, string]>([start.toISOString().slice(0, 10), now.toISOString().slice(0, 10)])
const reportCurrency = ref('USD')
const loading = ref(false)
const snapshot = ref<AnalyticsSnapshot>()
const organizations = ref<OrganizationSummary[]>([])
const views = ref<SavedView[]>([])
const exports = ref<ExportJob[]>([])
const diagnostics = ref<DiagnosticSnapshot>()
const viewName = ref('')
const canCrossOrg = computed(() => auth.hasPermission('analytics:cross-org'))
const canExport = computed(() => auth.hasPermission('analytics:export'))
const canDiagnose = computed(() => auth.hasPermission('admin:diagnostics'))

async function refresh() {
  loading.value = true
  try {
    snapshot.value = await getAnalytics(dateRange.value[0], dateRange.value[1])
    views.value = await getSavedViews()
    if (canCrossOrg.value) organizations.value = await getOrganizationAnalytics(reportCurrency.value)
    if (canExport.value) exports.value = await getExports()
    if (canDiagnose.value) diagnostics.value = await getDiagnostics()
  } catch { ElMessage.error('分析数据加载失败') } finally { loading.value = false }
}
async function persistView() {
  if (!viewName.value.trim()) return
  await saveView(viewName.value.trim(), { dateRange: dateRange.value, reportCurrency: reportCurrency.value })
  viewName.value = ''
  views.value = await getSavedViews()
  ElMessage.success('视图已保存')
}
async function applyView(view: SavedView) {
  const definition = view.definition
  if (Array.isArray(definition.dateRange) && definition.dateRange.length === 2) dateRange.value = definition.dateRange as [string, string]
  if (typeof definition.reportCurrency === 'string') reportCurrency.value = definition.reportCurrency
  await refresh()
}
async function deleteView(view: SavedView) {
  await ElMessageBox.confirm(`删除视图“${view.name}”？`, '确认操作')
  await removeView(view.id)
  views.value = await getSavedViews()
}
async function requestExport(resourceType: string) {
  await createExport(resourceType, { from: dateRange.value[0], to: dateRange.value[1] })
  exports.value = await getExports()
  ElMessage.success('导出任务已提交')
}
function money(value: number, currency = snapshot.value?.currencyCode || 'USD') {
  return new Intl.NumberFormat(undefined, { style: 'currency', currency }).format(value)
}
onMounted(refresh)
</script>

<template>
  <section class="analytics-view" aria-labelledby="analytics-title">
    <header class="page-heading">
      <div><span class="eyebrow">ANALYTICS</span><h1 id="analytics-title">经营分析中心</h1><p>趋势、跨组织经营汇总、受控导出和系统运行状态。</p></div>
      <el-button :loading="loading" @click="refresh">刷新</el-button>
    </header>
    <el-card shadow="never">
      <div class="filters">
        <el-date-picker v-model="dateRange" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开始日期" end-placeholder="结束日期" />
        <el-select v-model="reportCurrency" aria-label="报告币种" style="width:120px"><el-option v-for="code in ['USD','CNY','EUR']" :key="code" :value="code" /></el-select>
        <el-button type="primary" @click="refresh">应用</el-button>
        <el-input v-model="viewName" maxlength="120" placeholder="保存当前视图为……" style="max-width:240px" />
        <el-button @click="persistView">保存视图</el-button>
      </div>
      <div v-if="views.length" class="saved-views"><span>已保存视图</span><el-tag v-for="view in views" :key="view.id" closable @click="applyView(view)" @close.stop="deleteView(view)">{{ view.name }}</el-tag></div>
    </el-card>
    <el-card v-loading="loading" shadow="never">
      <template #header><strong>月度经营趋势</strong></template>
      <el-table :data="snapshot?.trends || []" empty-text="当前日期范围暂无数据">
        <el-table-column prop="period" label="期间" min-width="110" />
        <el-table-column label="销售"><template #default="{ row }">{{ money(row.sales) }}</template></el-table-column>
        <el-table-column label="采购"><template #default="{ row }">{{ money(row.purchases) }}</template></el-table-column>
        <el-table-column label="收入"><template #default="{ row }">{{ money(row.revenue) }}</template></el-table-column>
        <el-table-column label="费用"><template #default="{ row }">{{ money(row.expense) }}</template></el-table-column>
        <el-table-column label="毛利"><template #default="{ row }">{{ money(row.grossProfit) }}</template></el-table-column>
      </el-table>
    </el-card>
    <el-card v-if="canCrossOrg" shadow="never">
      <template #header><div class="card-title"><strong>跨组织汇总（{{ reportCurrency }}）</strong><el-tag type="warning">不含组织间抵销</el-tag></div></template>
      <el-table :data="organizations" empty-text="暂无可访问组织">
        <el-table-column prop="organizationName" label="组织" min-width="180" />
        <el-table-column label="销售"><template #default="{ row }">{{ money(row.sales, row.reportCurrencyCode) }}</template></el-table-column>
        <el-table-column label="应收"><template #default="{ row }">{{ money(row.receivables, row.reportCurrencyCode) }}</template></el-table-column>
        <el-table-column label="应付"><template #default="{ row }">{{ money(row.payables, row.reportCurrencyCode) }}</template></el-table-column>
        <el-table-column label="现金"><template #default="{ row }">{{ money(row.cash, row.reportCurrencyCode) }}</template></el-table-column>
        <el-table-column label="汇率"><template #default="{ row }"><el-tag :type="row.exchangeRateMissing ? 'danger' : 'success'">{{ row.exchangeRateMissing ? '缺失' : row.exchangeRateDate }}</el-tag></template></el-table-column>
      </el-table>
    </el-card>
    <el-card v-if="canExport" shadow="never">
      <template #header><div class="card-title"><strong>受控数据导出</strong><div><el-button v-for="resource in ['SALES','PURCHASES','INVENTORY','FINANCE','PROJECTS']" :key="resource" size="small" @click="requestExport(resource)">{{ resource }}</el-button></div></div></template>
      <el-table :data="exports" empty-text="暂无导出任务">
        <el-table-column prop="resourceType" label="数据域" /><el-table-column prop="status" label="状态" /><el-table-column prop="rowCount" label="行数" /><el-table-column prop="createdAt" label="创建时间" min-width="190" />
        <el-table-column label="操作"><template #default="{ row }"><el-button v-if="row.status === 'COMPLETED'" link type="primary" @click="downloadExport(row.id, row.resultFilename || `${row.resourceType}.csv`)">下载</el-button></template></el-table-column>
      </el-table>
    </el-card>
    <el-card v-if="canDiagnose && diagnostics" shadow="never">
      <template #header><strong>管理员诊断</strong></template>
      <div class="diagnostic-grid"><div><span>产品版本</span><strong>{{ diagnostics.productVersion }}</strong></div><div><span>交付阶段</span><strong>{{ diagnostics.deliveryStage }}</strong></div><div><span>数据库版本</span><strong>{{ diagnostics.migrationVersion }}</strong></div><div v-for="(item, name) in diagnostics.dependencies" :key="name"><span>{{ name }}</span><strong>{{ item.status }}</strong></div></div>
    </el-card>
  </section>
</template>

<style scoped>
.analytics-view{display:grid;gap:20px}.page-heading,.card-title,.filters,.saved-views{display:flex;align-items:center;gap:12px;flex-wrap:wrap}.page-heading,.card-title{justify-content:space-between}.page-heading h1{margin:5px 0}.page-heading p{margin:0;color:var(--el-text-color-secondary)}.eyebrow{font-size:12px;letter-spacing:.16em;color:var(--el-color-primary)}.saved-views{margin-top:14px}.saved-views .el-tag{cursor:pointer}.diagnostic-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(150px,1fr));gap:12px}.diagnostic-grid div{display:grid;gap:6px;padding:14px;border:1px solid var(--el-border-color-lighter);border-radius:10px}.diagnostic-grid span{font-size:12px;color:var(--el-text-color-secondary)}@media(max-width:640px){.page-heading{align-items:start}.filters>*{width:100%!important}}
</style>
