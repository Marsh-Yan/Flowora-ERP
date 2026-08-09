<script setup lang="ts">
/* global window */
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { ArrowUp, Box, Check, Clock, Plus, Printer, Refresh, Right } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { getOrganizationSettings, type OrganizationSettings } from '@/api/master-data'
import { listStockBalances, type StockBalance } from '@/api/inventory'
import { listPurchaseOrders, type PurchaseOrder } from '@/api/procurement'
import { listProjects, type Project } from '@/api/projects'
import { listSalesOrders, type SalesOrder } from '@/api/sales'
import { listWorkflowTasks, type WorkflowTask } from '@/api/workflow'
import { useAuthStore } from '@/stores/auth'

const { t, locale } = useI18n()
const router = useRouter()
const authStore = useAuthStore()
const loading = ref(false)
const orders = ref<SalesOrder[]>([])
const purchaseOrders = ref<PurchaseOrder[]>([])
const balances = ref<StockBalance[]>([])
const workflowTasks = ref<WorkflowTask[]>([])
const projects = ref<Project[]>([])
const settings = ref<OrganizationSettings | null>(null)

const currency = computed(() => settings.value?.baseCurrencyCode ?? orders.value[0]?.currencyCode ?? 'USD')
const timezone = computed(() => settings.value?.timezone || Intl.DateTimeFormat().resolvedOptions().timeZone)
const openOrders = computed(() => orders.value.filter((item) => ['DRAFT', 'CONFIRMED', 'PARTIALLY_FULFILLED'].includes(item.status)))
const currentMonthRevenue = computed(() => {
  const month = new Date().toISOString().slice(0, 7)
  return orders.value.filter((item) => item.orderDate.startsWith(month)).reduce((sum, item) => sum + Number(item.totalAmount), 0)
})
const inventoryValue = computed(() => balances.value.reduce((sum, item) => sum + Number(item.inventoryValue), 0))
const openTasks = computed(() => workflowTasks.value.filter((item) => ['OPEN', 'TRANSFERRED'].includes(item.status)).length)

function percentage(value: number) {
  return Math.max(0, Math.min(100, Math.round(value)))
}

function ratio(numerator: number, denominator: number) {
  return denominator === 0 ? 0 : percentage((numerator / denominator) * 100)
}

function formatCurrency(value: number, code = currency.value) {
  return new Intl.NumberFormat(locale.value, { style: 'currency', currency: code, maximumFractionDigits: 2 }).format(Number(value || 0))
}

function formatDate(value: string) {
  try {
    return new Intl.DateTimeFormat(locale.value, { dateStyle: 'medium', timeZone: timezone.value }).format(new Date(value))
  } catch {
    return value
  }
}

const stats = computed(() => [
  { label: t('dashboard.revenue'), value: formatCurrency(currentMonthRevenue.value), trend: t('dashboard.revenueRealHint'), tone: 'mint', icon: '↗' },
  { label: t('dashboard.orders'), value: String(openOrders.value.length), trend: t('dashboard.ordersRealHint'), tone: 'violet', icon: '◫' },
  { label: t('dashboard.inventory'), value: formatCurrency(inventoryValue.value), trend: t('dashboard.inventoryRealHint'), tone: 'amber', icon: '◒' },
  { label: t('dashboard.tasks'), value: String(openTasks.value), trend: t('dashboard.tasksRealHint'), tone: 'blue', icon: '✓' },
])

const workflows = computed(() => {
  const ordered = purchaseOrders.value.reduce((sum, item) => sum + Number(item.orderedQuantity), 0)
  const received = purchaseOrders.value.reduce((sum, item) => sum + Number(item.receivedQuantity), 0)
  const salesOrdered = orders.value.reduce((sum, item) => sum + Number(item.orderedQuantity), 0)
  const salesFulfilled = orders.value.reduce((sum, item) => sum + Number(item.fulfilledQuantity), 0)
  const completedTasks = workflowTasks.value.filter((item) => ['APPROVED', 'COMPLETED'].includes(item.status)).length
  return [
    { label: t('dashboard.supplyChain'), value: ratio(received, ordered), status: ratio(received, ordered) > 70 ? t('dashboard.healthy') : t('dashboard.attention'), tone: ratio(received, ordered) > 70 ? 'success' : 'warning' },
    { label: t('dashboard.salesOperations'), value: ratio(salesFulfilled, salesOrdered), status: ratio(salesFulfilled, salesOrdered) > 70 ? t('dashboard.healthy') : t('dashboard.attention'), tone: ratio(salesFulfilled, salesOrdered) > 70 ? 'success' : 'warning' },
    { label: t('dashboard.collaboration'), value: ratio(completedTasks, workflowTasks.value.length), status: projects.value.some((item) => item.status === 'AT_RISK') ? t('dashboard.attention') : t('dashboard.healthy'), tone: projects.value.some((item) => item.status === 'AT_RISK') ? 'warning' : 'success' },
  ]
})

const activities = computed(() => {
  const rows: Array<{ tone: string; icon: 'check' | 'box' | 'clock'; title: string; meta: string }> = []
  if (orders.value[0]) rows.push({ tone: 'violet', icon: 'clock', title: t('dashboard.salesCreatedDynamic', { number: orders.value[0].number }), meta: formatDate(orders.value[0].orderDate) })
  if (projects.value[0]) rows.push({ tone: 'blue', icon: 'box', title: t('dashboard.projectUpdatedDynamic', { name: projects.value[0].name }), meta: formatDate(projects.value[0].targetDate) })
  if (purchaseOrders.value[0]) rows.push({ tone: 'mint', icon: 'check', title: t('dashboard.purchaseUpdatedDynamic', { number: purchaseOrders.value[0].number }), meta: formatDate(purchaseOrders.value[0].orderDate) })
  return rows
})

async function load() {
  loading.value = true
  try {
    const [orderPage, purchasePage, balancePage, workflowPage, projectPage, organizationSettings] = await Promise.all([
      listSalesOrders('', 0, 100),
      listPurchaseOrders('', 0, 100),
      listStockBalances('', 0, 100),
      listWorkflowTasks(0, 100),
      listProjects('', undefined, 0, 100),
      getOrganizationSettings(),
    ])
    orders.value = orderPage.content
    purchaseOrders.value = purchasePage.content
    balances.value = balancePage.content
    workflowTasks.value = workflowPage.content
    projects.value = projectPage.content
    settings.value = organizationSettings
  } catch {
    ElMessage.error(t('dashboard.loadFailed'))
  } finally {
    loading.value = false
  }
}

function openSales() { router.push('/sales') }
function openWorkflow() { router.push('/workflow') }
function printDashboard() { window.print() }

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="dashboard-page">
    <section class="welcome-banner">
      <div>
        <span class="eyebrow">{{ t('dashboard.eyebrow') }}</span>
        <h1>{{ t('dashboard.title', { name: authStore.user?.displayName }) }}</h1>
        <p>{{ t('dashboard.subtitleReal') }}</p>
        <div class="welcome-actions">
          <el-button type="primary" round @click="openWorkflow">{{ t('dashboard.viewTasks') }}<el-icon class="button-icon"><Right /></el-icon></el-button>
          <el-button round plain @click="openSales"><el-icon class="button-icon"><Plus /></el-icon>{{ t('dashboard.createOrder') }}</el-button>
          <el-button round plain :loading="loading" @click="load"><el-icon class="button-icon"><Refresh /></el-icon>{{ t('dashboard.refresh') }}</el-button>
          <el-button round plain @click="printDashboard"><el-icon class="button-icon"><Printer /></el-icon>{{ t('dashboard.print') }}</el-button>
        </div>
      </div>
      <div class="banner-orbit orbit-one" />
      <div class="banner-orbit orbit-two" />
      <div class="banner-spark">✓</div>
    </section>

    <section class="stat-grid">
      <el-card v-for="stat in stats" :key="stat.label" class="stat-card" shadow="never">
        <div class="stat-card-header"><span>{{ stat.label }}</span><span class="stat-icon" :class="'tone-' + stat.tone">{{ stat.icon }}</span></div>
        <strong class="stat-value">{{ stat.value }}</strong>
        <div class="stat-trend"><el-icon><ArrowUp /></el-icon><span>{{ stat.trend }}</span></div>
      </el-card>
    </section>

    <section class="content-grid">
      <el-card class="insight-card" shadow="never">
        <div class="section-heading"><div><h2>{{ t('dashboard.workflowTitle') }}</h2><p>{{ t('dashboard.workflowSubtitleReal') }}</p></div><el-button text type="primary" @click="openWorkflow">{{ t('common.viewAll') }}</el-button></div>
        <div class="workflow-list">
          <div v-for="workflow in workflows" :key="workflow.label" class="workflow-row">
            <div class="workflow-label"><span class="workflow-dot" :class="'dot-' + workflow.tone" /><strong>{{ workflow.label }}</strong></div>
            <div class="workflow-progress"><el-progress :percentage="workflow.value" :show-text="false" :color="workflow.tone === 'warning' ? '#e4a11b' : '#35b890'" /></div>
            <span class="workflow-value">{{ workflow.value }}%</span>
            <el-tag size="small" :type="workflow.tone === 'warning' ? 'warning' : 'success'" effect="light">{{ workflow.status }}</el-tag>
          </div>
        </div>
      </el-card>

      <el-card class="activity-card" shadow="never">
        <div class="section-heading"><div><h2>{{ t('dashboard.activityTitle') }}</h2><p>{{ t('dashboard.activitySubtitleReal') }}</p></div><el-button text type="primary" @click="load">{{ t('dashboard.refresh') }}</el-button></div>
        <div v-if="activities.length" class="activity-list">
          <div v-for="activity in activities" :key="activity.title" class="activity-item">
            <div class="activity-icon" :class="`activity-icon-${activity.tone}`"><el-icon><Check v-if="activity.icon === 'check'" /><Box v-else-if="activity.icon === 'box'" /><Clock v-else /></el-icon></div>
            <div class="activity-copy"><strong>{{ activity.title }}</strong><span>{{ activity.meta }}</span></div>
          </div>
        </div>
        <el-empty v-else :description="t('dashboard.noActivity')" :image-size="70" />
      </el-card>
    </section>
  </div>
</template>

<style scoped>
.welcome-actions { flex-wrap: wrap; }
@media print {
  .welcome-actions, .app-sidebar, .app-header { display: none !important; }
  .dashboard-page { padding: 0; }
}
</style>
