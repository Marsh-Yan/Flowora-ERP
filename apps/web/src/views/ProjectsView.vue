<script setup lang="ts">
/* global Blob, URL, document, window */
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Download, Plus, Printer, Refresh } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import { listMasterData, type MasterDataRecord } from '@/api/master-data'
import { listSalesOrders, type SalesOrder } from '@/api/sales'
import {
  changeProjectStatus,
  changeTaskStatus,
  createMilestone,
  createProject,
  createProjectBudget,
  createProjectExpense,
  createProjectTask,
  createTimesheet,
  getProjectSummary,
  listBillingBasis,
  listMilestones,
  listProjectBudgets,
  listProjectExpenses,
  listProjects,
  listProjectTasks,
  listTimesheets,
  type BillingBasisRow,
  type Milestone,
  type Project,
  type ProjectBudget,
  type ProjectExpense,
  type ProjectStatus,
  type ProjectTask,
  type TaskPriority,
  type TaskStatus,
  type Timesheet,
} from '@/api/projects'

const { t, locale } = useI18n()
const loading = ref(false)
const activeTab = ref('milestones')
const projects = ref<Project[]>([])
const customers = ref<MasterDataRecord[]>([])
const salesOrders = ref<SalesOrder[]>([])
const selectedProject = ref<Project | null>(null)
const milestones = ref<Milestone[]>([])
const tasks = ref<ProjectTask[]>([])
const timesheets = ref<Timesheet[]>([])
const expenses = ref<ProjectExpense[]>([])
const budgets = ref<ProjectBudget[]>([])
const billingBasis = ref<BillingBasisRow[]>([])
const projectVisible = ref(false)
const milestoneVisible = ref(false)
const taskVisible = ref(false)
const timesheetVisible = ref(false)
const expenseVisible = ref(false)
const budgetVisible = ref(false)

const projectForm = reactive({ name: '', description: '', customerId: '', salesOrderId: '', targetDate: today(), budgetRevenue: 0, budgetCost: 0, currencyCode: 'USD' })
const milestoneForm = reactive({ name: '', sequenceNo: undefined as number | undefined, targetDate: '' })
const taskForm = reactive({ title: '', description: '', milestoneId: '', assigneeUserId: '', priority: 'MEDIUM' as TaskPriority, dueDate: '', estimatedHours: 0 })
const timesheetForm = reactive({ taskId: '', workDate: today(), hours: 1, costRate: 0, billingRate: 0, billable: true, currencyCode: 'USD', note: '' })
const expenseForm = reactive({ taskId: '', expenseDate: today(), category: 'TRAVEL', amount: 0, billable: true, currencyCode: 'USD', description: '' })
const budgetForm = reactive({ category: 'LABOR', amount: 0, currencyCode: 'USD', note: '' })

const statusOptions: ProjectStatus[] = ['PLANNED', 'ACTIVE', 'AT_RISK', 'COMPLETED', 'ARCHIVED']
const taskStatusOptions: TaskStatus[] = ['TODO', 'IN_PROGRESS', 'BLOCKED', 'DONE']

function today() {
  return new Date().toISOString().slice(0, 10)
}

function formatAmount(value: number | undefined, currency = '') {
  if (!currency) return Number(value ?? 0).toLocaleString(locale.value, { minimumFractionDigits: 2, maximumFractionDigits: 2 })
  return new Intl.NumberFormat(locale.value, { style: 'currency', currency, maximumFractionDigits: 2 }).format(Number(value ?? 0))
}

function downloadCsv(filename: string, headers: string[], rows: Array<Array<string | number | undefined>>) {
  const escape = (value: string | number | undefined) => `"${String(value ?? '').replaceAll('"', '""')}"`
  const csv = `\uFEFF${[headers, ...rows].map((row) => row.map(escape).join(',')).join('\n')}`
  const link = document.createElement('a')
  link.href = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }))
  link.download = filename
  link.click()
  URL.revokeObjectURL(link.href)
}

function exportProjects() {
  downloadCsv('flowora-projects.csv', ['Number', 'Name', 'Status', 'Target date', 'Progress', 'Actual cost', 'Actual hours', 'Billable amount'], projects.value.map((item) => [item.number, item.name, item.status, item.targetDate, item.progressPercent, item.actualCost, item.actualHours, item.billableAmount]))
}

function printProject() {
  window.print()
}

function statusType(status: string) {
  if (['ACTIVE', 'COMPLETED', 'DONE'].includes(status)) return 'success'
  if (['AT_RISK', 'BLOCKED', 'IN_PROGRESS'].includes(status)) return 'warning'
  if (['ARCHIVED'].includes(status)) return 'info'
  return 'info'
}

function label(key: string, value: string) {
  return t(`${key}.${value}`, value)
}

function customerName(id?: string) {
  return customers.value.find((item) => item.id === id)?.name ?? id ?? '-'
}

function orderName(id?: string) {
  return salesOrders.value.find((item) => item.id === id)?.number ?? id ?? '-'
}

async function load() {
  loading.value = true
  try {
    const [projectPage, customerPage, orderPage] = await Promise.all([
      listProjects(),
      listMasterData<MasterDataRecord>('customers', '', 0, 100),
      listSalesOrders('', 0, 100),
    ])
    projects.value = projectPage.content
    customers.value = customerPage.content
    salesOrders.value = orderPage.content
    if (selectedProject.value) {
      const fresh = projects.value.find((item) => item.id === selectedProject.value?.id)
      if (fresh) await selectProject(fresh)
    }
  } catch {
    ElMessage.error(t('projects.loadFailed'))
  } finally {
    loading.value = false
  }
}

async function selectProject(project: Project) {
  selectedProject.value = project
  try {
    const [summary, milestoneRows, taskPage, timesheetPage, expensePage, budgetRows, billingRows] = await Promise.all([
      getProjectSummary(project.id),
      listMilestones(project.id),
      listProjectTasks(project.id),
      listTimesheets(project.id),
      listProjectExpenses(project.id),
      listProjectBudgets(project.id),
      listBillingBasis(project.id),
    ])
    selectedProject.value = summary
    milestones.value = milestoneRows
    tasks.value = taskPage.content
    timesheets.value = timesheetPage.content
    expenses.value = expensePage.content
    budgets.value = budgetRows
    billingBasis.value = billingRows
    timesheetForm.currencyCode = summary.currencyCode
    expenseForm.currencyCode = summary.currencyCode
    budgetForm.currencyCode = summary.currencyCode
  } catch {
    ElMessage.error(t('projects.loadFailed'))
  }
}

function openProject() {
  Object.assign(projectForm, { name: '', description: '', customerId: '', salesOrderId: '', targetDate: today(), budgetRevenue: 0, budgetCost: 0, currencyCode: 'USD' })
  projectVisible.value = true
}

async function submitProject() {
  try {
    const created = await createProject({ ...projectForm, customerId: projectForm.customerId || undefined, salesOrderId: projectForm.salesOrderId || undefined })
    projectVisible.value = false
    ElMessage.success(t('projects.created'))
    await load()
    await selectProject(created)
  } catch {
    ElMessage.error(t('projects.saveFailed'))
  }
}

function requireProject() {
  if (!selectedProject.value) ElMessage.warning(t('projects.selectProject'))
  return selectedProject.value
}

async function updateStatus(status: ProjectStatus) {
  const project = requireProject()
  if (!project) return
  try {
    selectedProject.value = await changeProjectStatus(project.id, status)
    await load()
    ElMessage.success(t('projects.statusChanged'))
  } catch {
    ElMessage.error(t('projects.actionFailed'))
  }
}

async function submitMilestone() {
  const project = requireProject()
  if (!project) return
  try {
    await createMilestone(project.id, { ...milestoneForm, sequenceNo: milestoneForm.sequenceNo || undefined, targetDate: milestoneForm.targetDate || undefined })
    milestoneVisible.value = false
    Object.assign(milestoneForm, { name: '', sequenceNo: undefined, targetDate: '' })
    await selectProject(project)
    ElMessage.success(t('projects.saved'))
  } catch { ElMessage.error(t('projects.saveFailed')) }
}

async function submitTask() {
  const project = requireProject()
  if (!project) return
  try {
    await createProjectTask(project.id, { ...taskForm, milestoneId: taskForm.milestoneId || undefined, assigneeUserId: taskForm.assigneeUserId || undefined, dueDate: taskForm.dueDate || undefined })
    taskVisible.value = false
    Object.assign(taskForm, { title: '', description: '', milestoneId: '', assigneeUserId: '', priority: 'MEDIUM', dueDate: '', estimatedHours: 0 })
    await selectProject(project)
    ElMessage.success(t('projects.saved'))
  } catch { ElMessage.error(t('projects.saveFailed')) }
}

async function submitTimesheet() {
  const project = requireProject()
  if (!project) return
  try {
    await createTimesheet(project.id, { ...timesheetForm, taskId: timesheetForm.taskId || undefined })
    timesheetVisible.value = false
    await selectProject(project)
    ElMessage.success(t('projects.saved'))
  } catch { ElMessage.error(t('projects.saveFailed')) }
}

async function submitExpense() {
  const project = requireProject()
  if (!project) return
  try {
    await createProjectExpense(project.id, { ...expenseForm, taskId: expenseForm.taskId || undefined })
    expenseVisible.value = false
    await selectProject(project)
    ElMessage.success(t('projects.saved'))
  } catch { ElMessage.error(t('projects.saveFailed')) }
}

async function submitBudget() {
  const project = requireProject()
  if (!project) return
  try {
    await createProjectBudget(project.id, budgetForm)
    budgetVisible.value = false
    await selectProject(project)
    ElMessage.success(t('projects.saved'))
  } catch { ElMessage.error(t('projects.saveFailed')) }
}

async function updateTaskStatus(task: ProjectTask, status: TaskStatus) {
  try {
    await changeTaskStatus(task.id, status)
    if (selectedProject.value) await selectProject(selectedProject.value)
  } catch { ElMessage.error(t('projects.actionFailed')) }
}

onMounted(load)
</script>

<template>
  <div class="operations-page projects-page">
    <div class="operations-heading section-heading">
      <div>
        <span class="eyebrow">{{ t('projects.eyebrow') }}</span>
        <h1>{{ t('projects.title') }}</h1>
        <p>{{ t('projects.subtitle') }}</p>
      </div>
      <div class="operations-actions">
        <el-button round plain :loading="loading" @click="load"><el-icon><Refresh /></el-icon>{{ t('projects.refresh') }}</el-button>
        <el-button round plain @click="exportProjects"><el-icon><Download /></el-icon>{{ t('projects.exportCsv') }}</el-button>
        <el-button round plain :disabled="!selectedProject" @click="printProject"><el-icon><Printer /></el-icon>{{ t('projects.print') }}</el-button>
        <el-button type="primary" round @click="openProject"><el-icon><Plus /></el-icon>{{ t('projects.create') }}</el-button>
      </div>
    </div>

    <div class="inventory-summary-grid">
      <el-card shadow="never"><span class="eyebrow">{{ t('projects.projectCount') }}</span><strong>{{ projects.length }}</strong><small>{{ t('projects.projectCountHint') }}</small></el-card>
      <el-card shadow="never"><span class="eyebrow">{{ t('projects.activeProjects') }}</span><strong>{{ projects.filter((item) => item.status === 'ACTIVE').length }}</strong><small>{{ t('projects.activeProjectsHint') }}</small></el-card>
      <el-card shadow="never"><span class="eyebrow">{{ t('projects.billableTotal') }}</span><strong>{{ formatAmount(projects.reduce((sum, item) => sum + Number(item.billableAmount), 0)) }}</strong><small>{{ t('projects.billableTotalHint') }}</small></el-card>
    </div>

    <div class="projects-layout">
      <el-card shadow="never" class="operations-card project-list-card">
        <div class="section-heading"><div><h2>{{ t('projects.projects') }}</h2><p>{{ t('projects.projectListHint') }}</p></div></div>
        <el-table v-loading="loading" :data="projects" row-key="id" highlight-current-row empty-text="" @row-click="selectProject">
          <el-table-column :label="t('projects.name')" min-width="190"><template #default="{ row }"><strong>{{ row.name }}</strong><small class="project-number">{{ row.number }}</small></template></el-table-column>
          <el-table-column prop="targetDate" :label="t('projects.targetDate')" width="118" />
          <el-table-column :label="t('projects.statusLabel')" width="125"><template #default="{ row }"><el-tag :type="statusType(row.status)" size="small">{{ label('projects.status', row.status) }}</el-tag></template></el-table-column>
          <el-table-column :label="t('projects.progress')" width="125"><template #default="{ row }"><el-progress :percentage="Number(row.progressPercent)" :stroke-width="8" /></template></el-table-column>
        </el-table>
        <el-empty v-if="!projects.length && !loading" :description="t('projects.empty')" />
      </el-card>

      <el-card shadow="never" class="operations-card project-detail-card">
        <template v-if="selectedProject">
          <div class="project-detail-heading">
            <div><span class="eyebrow">{{ selectedProject.number }}</span><h2>{{ selectedProject.name }}</h2><p>{{ customerName(selectedProject.customerId) }} · {{ orderName(selectedProject.salesOrderId) }}</p></div>
            <el-select :model-value="selectedProject.status" class="status-select" @update:model-value="updateStatus"><el-option v-for="status in statusOptions" :key="status" :label="label('projects.status', status)" :value="status" /></el-select>
          </div>
          <div class="project-metrics"><div><span>{{ t('projects.progress') }}</span><strong>{{ selectedProject.progressPercent }}%</strong></div><div><span>{{ t('projects.actualCost') }}</span><strong>{{ formatAmount(selectedProject.actualCost, selectedProject.currencyCode) }}</strong></div><div><span>{{ t('projects.actualHours') }}</span><strong>{{ selectedProject.actualHours }}</strong></div><div><span>{{ t('projects.billableAmount') }}</span><strong>{{ formatAmount(selectedProject.billableAmount, selectedProject.currencyCode) }}</strong></div></div>
          <el-tabs v-model="activeTab">
            <el-tab-pane :label="t('projects.milestones')" name="milestones"><div class="tab-toolbar"><el-button type="primary" plain size="small" @click="milestoneVisible = true"><el-icon><Plus /></el-icon>{{ t('projects.addMilestone') }}</el-button></div><el-table :data="milestones" size="small"><el-table-column prop="sequenceNo" label="#" width="55" /><el-table-column prop="name" :label="t('projects.name')" /><el-table-column prop="targetDate" :label="t('projects.targetDate')" width="125" /><el-table-column :label="t('projects.statusLabel')" width="135"><template #default="{ row }"><el-tag size="small" :type="statusType(row.status)">{{ label('projects.milestoneStatus', row.status) }}</el-tag></template></el-table-column></el-table></el-tab-pane>
            <el-tab-pane :label="t('projects.tasks')" name="tasks"><div class="tab-toolbar"><el-button type="primary" plain size="small" @click="taskVisible = true"><el-icon><Plus /></el-icon>{{ t('projects.addTask') }}</el-button></div><el-table :data="tasks" size="small"><el-table-column prop="title" :label="t('projects.task')" min-width="180" /><el-table-column prop="assigneeUserId" :label="t('projects.assignee')" width="130" /><el-table-column prop="estimatedHours" :label="t('projects.estimate')" width="95" /><el-table-column :label="t('projects.statusLabel')" width="140"><template #default="{ row }"><el-select size="small" :model-value="row.status" @update:model-value="updateTaskStatus(row, $event)"><el-option v-for="status in taskStatusOptions" :key="status" :label="label('projects.taskStatus', status)" :value="status" /></el-select></template></el-table-column></el-table></el-tab-pane>
            <el-tab-pane :label="t('projects.timesheets')" name="timesheets"><div class="tab-toolbar"><el-button type="primary" plain size="small" @click="timesheetVisible = true"><el-icon><Plus /></el-icon>{{ t('projects.addTimesheet') }}</el-button></div><el-table :data="timesheets" size="small"><el-table-column prop="workDate" :label="t('projects.date')" width="115" /><el-table-column prop="userId" :label="t('projects.user')" width="125" /><el-table-column prop="hours" :label="t('projects.hours')" width="85" /><el-table-column prop="costAmount" :label="t('projects.cost')" width="115" /><el-table-column prop="billableAmount" :label="t('projects.billable')" width="115" /><el-table-column :label="t('projects.billableFlag')" width="100"><template #default="{ row }"><el-tag size="small" :type="row.billable ? 'success' : 'info'">{{ row.billable ? t('projects.yes') : t('projects.no') }}</el-tag></template></el-table-column></el-table></el-tab-pane>
            <el-tab-pane :label="t('projects.expenses')" name="expenses"><div class="tab-toolbar"><el-button type="primary" plain size="small" @click="expenseVisible = true"><el-icon><Plus /></el-icon>{{ t('projects.addExpense') }}</el-button></div><el-table :data="expenses" size="small"><el-table-column prop="expenseDate" :label="t('projects.date')" width="115" /><el-table-column prop="category" :label="t('projects.category')" width="125" /><el-table-column prop="amount" :label="t('projects.amount')" width="120" /><el-table-column prop="billableAmount" :label="t('projects.billable')" width="120" /><el-table-column prop="description" :label="t('projects.description')" min-width="160" /></el-table></el-tab-pane>
            <el-tab-pane :label="t('projects.budgets')" name="budgets"><div class="tab-toolbar"><el-button type="primary" plain size="small" @click="budgetVisible = true"><el-icon><Plus /></el-icon>{{ t('projects.addBudget') }}</el-button></div><el-table :data="budgets" size="small"><el-table-column prop="category" :label="t('projects.category')" /><el-table-column prop="amount" :label="t('projects.amount')" width="140" /><el-table-column prop="currencyCode" :label="t('projects.currency')" width="100" /><el-table-column prop="note" :label="t('projects.note')" /></el-table></el-tab-pane>
            <el-tab-pane :label="t('projects.billingBasis')" name="billingBasis"><el-table :data="billingBasis" size="small"><el-table-column :label="t('projects.type')" width="120"><template #default="{ row }">{{ label('projects.basisType', row.type) }}</template></el-table-column><el-table-column prop="date" :label="t('projects.date')" width="115" /><el-table-column prop="description" :label="t('projects.description')" min-width="180" /><el-table-column prop="quantity" :label="t('projects.quantity')" width="95" /><el-table-column prop="amount" :label="t('projects.amount')" width="125" /><el-table-column prop="currencyCode" :label="t('projects.currency')" width="90" /></el-table></el-tab-pane>
          </el-tabs>
        </template>
        <el-empty v-else :description="t('projects.selectProject')" />
      </el-card>
    </div>

    <el-dialog v-model="projectVisible" :title="t('projects.createProject')" width="620px"><el-form label-position="top"><div class="operations-form-grid"><el-form-item :label="t('projects.name')"><el-input v-model="projectForm.name" /></el-form-item><el-form-item :label="t('projects.targetDate')"><el-date-picker v-model="projectForm.targetDate" type="date" value-format="YYYY-MM-DD" class="full-width" /></el-form-item><el-form-item :label="t('projects.customer')"><el-select v-model="projectForm.customerId" clearable filterable class="full-width"><el-option v-for="customer in customers" :key="customer.id" :label="`${customer.code} · ${customer.name}`" :value="customer.id" /></el-select></el-form-item><el-form-item :label="t('projects.salesOrder')"><el-select v-model="projectForm.salesOrderId" clearable filterable class="full-width"><el-option v-for="order in salesOrders" :key="order.id" :label="`${order.number} · ${formatAmount(order.totalAmount, order.currencyCode)}`" :value="order.id" /></el-select></el-form-item><el-form-item :label="t('projects.budgetRevenue')"><el-input-number v-model="projectForm.budgetRevenue" :min="0" :precision="2" class="full-width" /></el-form-item><el-form-item :label="t('projects.budgetCost')"><el-input-number v-model="projectForm.budgetCost" :min="0" :precision="2" class="full-width" /></el-form-item></div><el-form-item :label="t('projects.description')"><el-input v-model="projectForm.description" type="textarea" :rows="3" /></el-form-item></el-form><template #footer><el-button @click="projectVisible = false">{{ t('projects.cancel') }}</el-button><el-button type="primary" @click="submitProject">{{ t('projects.save') }}</el-button></template></el-dialog>
    <el-dialog v-model="milestoneVisible" :title="t('projects.addMilestone')" width="480px"><el-form label-position="top"><el-form-item :label="t('projects.name')"><el-input v-model="milestoneForm.name" /></el-form-item><div class="operations-form-grid"><el-form-item :label="t('projects.sequenceNo')"><el-input-number v-model="milestoneForm.sequenceNo" :min="1" class="full-width" /></el-form-item><el-form-item :label="t('projects.targetDate')"><el-date-picker v-model="milestoneForm.targetDate" type="date" value-format="YYYY-MM-DD" class="full-width" /></el-form-item></div></el-form><template #footer><el-button @click="milestoneVisible = false">{{ t('projects.cancel') }}</el-button><el-button type="primary" @click="submitMilestone">{{ t('projects.save') }}</el-button></template></el-dialog>
    <el-dialog v-model="taskVisible" :title="t('projects.addTask')" width="560px"><el-form label-position="top"><el-form-item :label="t('projects.task')"><el-input v-model="taskForm.title" /></el-form-item><div class="operations-form-grid"><el-form-item :label="t('projects.milestone')"><el-select v-model="taskForm.milestoneId" clearable class="full-width"><el-option v-for="item in milestones" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item><el-form-item :label="t('projects.priority')"><el-select v-model="taskForm.priority" class="full-width"><el-option v-for="item in ['LOW', 'MEDIUM', 'HIGH']" :key="item" :label="label('projects.priorityValue', item)" :value="item" /></el-select></el-form-item><el-form-item :label="t('projects.assignee')"><el-input v-model="taskForm.assigneeUserId" /></el-form-item><el-form-item :label="t('projects.dueDate')"><el-date-picker v-model="taskForm.dueDate" type="date" value-format="YYYY-MM-DD" class="full-width" /></el-form-item><el-form-item :label="t('projects.estimate')"><el-input-number v-model="taskForm.estimatedHours" :min="0" :precision="2" class="full-width" /></el-form-item></div><el-form-item :label="t('projects.description')"><el-input v-model="taskForm.description" type="textarea" :rows="3" /></el-form-item></el-form><template #footer><el-button @click="taskVisible = false">{{ t('projects.cancel') }}</el-button><el-button type="primary" @click="submitTask">{{ t('projects.save') }}</el-button></template></el-dialog>
    <el-dialog v-model="timesheetVisible" :title="t('projects.addTimesheet')" width="560px"><el-form label-position="top"><div class="operations-form-grid"><el-form-item :label="t('projects.task')"><el-select v-model="timesheetForm.taskId" clearable class="full-width"><el-option v-for="item in tasks" :key="item.id" :label="item.title" :value="item.id" /></el-select></el-form-item><el-form-item :label="t('projects.date')"><el-date-picker v-model="timesheetForm.workDate" type="date" value-format="YYYY-MM-DD" class="full-width" /></el-form-item><el-form-item :label="t('projects.hours')"><el-input-number v-model="timesheetForm.hours" :min="0.01" :precision="2" class="full-width" /></el-form-item><el-form-item :label="t('projects.costRate')"><el-input-number v-model="timesheetForm.costRate" :min="0" :precision="2" class="full-width" /></el-form-item><el-form-item :label="t('projects.billingRate')"><el-input-number v-model="timesheetForm.billingRate" :min="0" :precision="2" class="full-width" /></el-form-item></div><el-form-item :label="t('projects.note')"><el-input v-model="timesheetForm.note" /></el-form-item><el-checkbox v-model="timesheetForm.billable">{{ t('projects.billableFlag') }}</el-checkbox></el-form><template #footer><el-button @click="timesheetVisible = false">{{ t('projects.cancel') }}</el-button><el-button type="primary" @click="submitTimesheet">{{ t('projects.save') }}</el-button></template></el-dialog>
    <el-dialog v-model="expenseVisible" :title="t('projects.addExpense')" width="520px"><el-form label-position="top"><div class="operations-form-grid"><el-form-item :label="t('projects.task')"><el-select v-model="expenseForm.taskId" clearable class="full-width"><el-option v-for="item in tasks" :key="item.id" :label="item.title" :value="item.id" /></el-select></el-form-item><el-form-item :label="t('projects.date')"><el-date-picker v-model="expenseForm.expenseDate" type="date" value-format="YYYY-MM-DD" class="full-width" /></el-form-item><el-form-item :label="t('projects.category')"><el-input v-model="expenseForm.category" /></el-form-item><el-form-item :label="t('projects.amount')"><el-input-number v-model="expenseForm.amount" :min="0.01" :precision="2" class="full-width" /></el-form-item></div><el-form-item :label="t('projects.description')"><el-input v-model="expenseForm.description" /></el-form-item><el-checkbox v-model="expenseForm.billable">{{ t('projects.billableFlag') }}</el-checkbox></el-form><template #footer><el-button @click="expenseVisible = false">{{ t('projects.cancel') }}</el-button><el-button type="primary" @click="submitExpense">{{ t('projects.save') }}</el-button></template></el-dialog>
    <el-dialog v-model="budgetVisible" :title="t('projects.addBudget')" width="480px"><el-form label-position="top"><div class="operations-form-grid"><el-form-item :label="t('projects.category')"><el-input v-model="budgetForm.category" /></el-form-item><el-form-item :label="t('projects.amount')"><el-input-number v-model="budgetForm.amount" :min="0" :precision="2" class="full-width" /></el-form-item></div><el-form-item :label="t('projects.note')"><el-input v-model="budgetForm.note" /></el-form-item></el-form><template #footer><el-button @click="budgetVisible = false">{{ t('projects.cancel') }}</el-button><el-button type="primary" @click="submitBudget">{{ t('projects.save') }}</el-button></template></el-dialog>
  </div>
</template>

<style scoped>
.projects-layout { display: grid; grid-template-columns: minmax(340px, 0.82fr) minmax(0, 1.6fr); gap: 18px; }
.project-list-card, .project-detail-card { min-width: 0; }
.project-number { display: block; color: var(--flowora-muted); font-size: 11px; margin-top: 3px; }
.project-detail-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 18px; }
.project-detail-heading h2 { margin: 4px 0; font-size: 22px; }
.project-detail-heading p { margin: 0; color: var(--flowora-muted); }
.status-select { width: 145px; }
.project-metrics { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 10px; margin-bottom: 18px; }
.project-metrics div { padding: 12px; border-radius: 10px; background: #f7f8fb; }
.project-metrics span { display: block; color: var(--flowora-muted); font-size: 11px; }
.project-metrics strong { display: block; margin-top: 5px; color: var(--flowora-ink); font-size: 16px; }
.tab-toolbar { display: flex; justify-content: flex-end; min-height: 30px; margin-bottom: 8px; }
@media (max-width: 1120px) { .projects-layout { grid-template-columns: 1fr; } }
@media (max-width: 760px) { .project-metrics { grid-template-columns: repeat(2, minmax(0, 1fr)); } .project-detail-heading { flex-direction: column; } .status-select { width: 100%; } }
@media print { .projects-page .operations-heading, .projects-page .project-list-card, .projects-page .tab-toolbar, .app-sidebar, .app-header { display: none !important; } .projects-layout { display: block; } }
</style>
