import { apiClient } from './http'

export type WorkflowResourceType =
  | 'PURCHASE_REQUEST'
  | 'PURCHASE_ORDER'
  | 'SALES_QUOTE'
  | 'SALES_ORDER'
  | 'INVENTORY_ADJUSTMENT'
  | 'PROJECT'
  | 'GENERAL'

export type WorkflowTaskStatus = 'OPEN' | 'APPROVED' | 'REJECTED' | 'TRANSFERRED' | 'COMPLETED' | 'CANCELLED'
export type WorkflowAction = 'APPROVE' | 'REJECT' | 'TRANSFER' | 'COMPLETE' | 'CANCEL'

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

export interface WorkflowTask {
  id: string
  resourceType: WorkflowResourceType
  resourceId: string
  title: string
  description?: string
  amount: number
  requesterUserId: string
  assigneeUserId?: string
  assigneeRole?: string
  status: WorkflowTaskStatus
  dueAt?: string
  completedAt?: string
}

export interface WorkflowNotification {
  id: string
  type: string
  title: string
  message: string
  read: boolean
  createdAt: string
}

export interface WorkflowComment {
  id: string
  resourceType: string
  resourceId: string
  authorUserId: string
  body: string
  createdAt: string
}

export interface WorkflowActivity {
  id: string
  actionCode: string
  summary: string
  actorUserId: string
  createdAt: string
  details: Record<string, unknown>
}

export interface TaskRequest {
  resourceType: WorkflowResourceType
  resourceId: string
  title: string
  description?: string
  amount: number
  assigneeUserId?: string
  dueAt?: string
}

export async function listWorkflowTasks(page = 0, size = 20) {
  const response = await apiClient.get<ApiEnvelope<PageResponse<WorkflowTask>>>('/v1/workflow/tasks', { params: { page, size } })
  return response.data.data
}

export async function createWorkflowTask(payload: TaskRequest) {
  const response = await apiClient.post<ApiEnvelope<WorkflowTask>>('/v1/workflow/tasks', payload)
  return response.data.data
}

export async function actOnWorkflowTask(id: string, action: WorkflowAction, comment?: string, transferToUserId?: string) {
  const response = await apiClient.post<ApiEnvelope<{ task: WorkflowTask; action: WorkflowAction; notificationCreated: boolean }>>(
    `/v1/workflow/tasks/${id}/actions`,
    { action, comment, transferToUserId },
  )
  return response.data.data
}

export async function listWorkflowNotifications(page = 0, size = 20) {
  const response = await apiClient.get<ApiEnvelope<PageResponse<WorkflowNotification>>>('/v1/workflow/notifications', { params: { page, size } })
  return response.data.data
}

export async function getWorkflowUnreadCount() {
  const response = await apiClient.get<ApiEnvelope<{ count: number }>>('/v1/workflow/notifications/unread-count')
  return response.data.data.count
}

export async function markWorkflowNotificationRead(id: string) {
  const response = await apiClient.post<ApiEnvelope<WorkflowNotification>>(`/v1/workflow/notifications/${id}/read`)
  return response.data.data
}

export async function listResourceComments(resourceType: string, resourceId: string) {
  const response = await apiClient.get<ApiEnvelope<PageResponse<WorkflowComment>>>(`/v1/workflow/resources/${resourceType}/${resourceId}/comments`, { params: { page: 0, size: 50 } })
  return response.data.data
}

export async function addResourceComment(resourceType: string, resourceId: string, body: string) {
  const response = await apiClient.post<ApiEnvelope<WorkflowComment>>(`/v1/workflow/resources/${resourceType}/${resourceId}/comments`, { body })
  return response.data.data
}

export async function listResourceActivities(resourceType: string, resourceId: string) {
  const response = await apiClient.get<ApiEnvelope<PageResponse<WorkflowActivity>>>(`/v1/workflow/resources/${resourceType}/${resourceId}/activities`, { params: { page: 0, size: 50 } })
  return response.data.data
}
