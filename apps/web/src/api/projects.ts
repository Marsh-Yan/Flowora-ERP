import { apiClient } from './http'

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

interface ApiEnvelope<T> {
  data: T
  requestId: string
}

export type ProjectStatus = 'PLANNED' | 'ACTIVE' | 'AT_RISK' | 'COMPLETED' | 'ARCHIVED'
export type TaskStatus = 'TODO' | 'IN_PROGRESS' | 'BLOCKED' | 'DONE'
export type TaskPriority = 'LOW' | 'MEDIUM' | 'HIGH'

export interface Project {
  id: string
  number: string
  name: string
  description?: string
  customerId?: string
  salesOrderId?: string
  managerUserId: string
  targetDate: string
  budgetRevenue: number
  budgetCost: number
  currencyCode: string
  status: ProjectStatus
  progressPercent: number
  actualCost: number
  actualHours: number
  billableAmount: number
  completedTaskCount: number
  totalTaskCount: number
}

export interface Milestone {
  id: string
  projectId: string
  name: string
  sequenceNo: number
  targetDate?: string
  status: 'PLANNED' | 'IN_PROGRESS' | 'COMPLETED'
}

export interface ProjectTask {
  id: string
  projectId: string
  milestoneId?: string
  title: string
  description?: string
  assigneeUserId: string
  priority: TaskPriority
  dueDate?: string
  status: TaskStatus
  estimatedHours: number
  actualHours: number
}

export interface Timesheet {
  id: string
  projectId: string
  taskId?: string
  userId: string
  workDate: string
  hours: number
  costAmount: number
  billableAmount: number
  billable: boolean
  currencyCode: string
  note?: string
}

export interface ProjectExpense {
  id: string
  projectId: string
  taskId?: string
  userId: string
  expenseDate: string
  category: string
  amount: number
  billableAmount: number
  billable: boolean
  currencyCode: string
  description?: string
}

export interface ProjectBudget {
  id: string
  projectId: string
  category: string
  amount: number
  currencyCode: string
  note?: string
}

export interface BillingBasisRow {
  type: 'TIMESHEET' | 'EXPENSE'
  id: string
  date: string
  taskId?: string
  description?: string
  quantity: number
  amount: number
  currencyCode: string
  sourceUserId: string
}

export async function listProjects(query = '', status?: ProjectStatus, page = 0, size = 50) {
  const response = await apiClient.get<ApiEnvelope<PageResponse<Project>>>('/v1/projects', { params: { query, status, page, size } })
  return response.data.data
}

export async function createProject(payload: { name: string; description?: string; customerId?: string; salesOrderId?: string; managerUserId?: string; targetDate: string; budgetRevenue?: number; budgetCost?: number; currencyCode?: string }) {
  const response = await apiClient.post<ApiEnvelope<Project>>('/v1/projects', payload)
  return response.data.data
}

export async function changeProjectStatus(id: string, status: ProjectStatus) {
  const response = await apiClient.post<ApiEnvelope<Project>>(`/v1/projects/${id}/status`, { status })
  return response.data.data
}

export async function getProjectSummary(id: string) {
  const response = await apiClient.get<ApiEnvelope<Project>>(`/v1/projects/${id}/summary`)
  return response.data.data
}

export async function listMilestones(projectId: string) {
  const response = await apiClient.get<ApiEnvelope<Milestone[]>>(`/v1/projects/${projectId}/milestones`)
  return response.data.data
}

export async function createMilestone(projectId: string, payload: { name: string; sequenceNo?: number; targetDate?: string }) {
  const response = await apiClient.post<ApiEnvelope<Milestone>>(`/v1/projects/${projectId}/milestones`, payload)
  return response.data.data
}

export async function listProjectTasks(projectId: string, page = 0, size = 50) {
  const response = await apiClient.get<ApiEnvelope<PageResponse<ProjectTask>>>(`/v1/projects/${projectId}/tasks`, { params: { page, size } })
  return response.data.data
}

export async function createProjectTask(projectId: string, payload: { title: string; description?: string; milestoneId?: string; assigneeUserId?: string; priority?: TaskPriority; dueDate?: string; estimatedHours?: number }) {
  const response = await apiClient.post<ApiEnvelope<ProjectTask>>(`/v1/projects/${projectId}/tasks`, payload)
  return response.data.data
}

export async function changeTaskStatus(taskId: string, status: TaskStatus) {
  const response = await apiClient.post<ApiEnvelope<ProjectTask>>(`/v1/projects/tasks/${taskId}/status`, { status })
  return response.data.data
}

export async function listTimesheets(projectId: string, page = 0, size = 50) {
  const response = await apiClient.get<ApiEnvelope<PageResponse<Timesheet>>>(`/v1/projects/${projectId}/timesheets`, { params: { page, size } })
  return response.data.data
}

export async function createTimesheet(projectId: string, payload: { taskId?: string; workDate: string; hours: number; costRate: number; billingRate: number; billable: boolean; currencyCode?: string; note?: string }) {
  const response = await apiClient.post<ApiEnvelope<Timesheet>>(`/v1/projects/${projectId}/timesheets`, payload)
  return response.data.data
}

export async function listProjectExpenses(projectId: string, page = 0, size = 50) {
  const response = await apiClient.get<ApiEnvelope<PageResponse<ProjectExpense>>>(`/v1/projects/${projectId}/expenses`, { params: { page, size } })
  return response.data.data
}

export async function createProjectExpense(projectId: string, payload: { taskId?: string; expenseDate: string; category: string; amount: number; billable: boolean; currencyCode?: string; description?: string }) {
  const response = await apiClient.post<ApiEnvelope<ProjectExpense>>(`/v1/projects/${projectId}/expenses`, payload)
  return response.data.data
}

export async function listProjectBudgets(projectId: string) {
  const response = await apiClient.get<ApiEnvelope<ProjectBudget[]>>(`/v1/projects/${projectId}/budgets`)
  return response.data.data
}

export async function createProjectBudget(projectId: string, payload: { category: string; amount: number; currencyCode?: string; note?: string }) {
  const response = await apiClient.post<ApiEnvelope<ProjectBudget>>(`/v1/projects/${projectId}/budgets`, payload)
  return response.data.data
}

export async function listBillingBasis(projectId: string) {
  const response = await apiClient.get<ApiEnvelope<BillingBasisRow[]>>(`/v1/projects/${projectId}/billing-basis`)
  return response.data.data
}
