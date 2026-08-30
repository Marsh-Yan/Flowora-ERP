import { apiClient } from './http'

export type WorkflowResourceType =
  | 'PURCHASE_REQUEST'
  | 'PURCHASE_ORDER'
  | 'SALES_QUOTE'
  | 'SALES_ORDER'
  | 'INVENTORY_ADJUSTMENT'
  | 'PROJECT'
  | 'GENERAL'

export type WorkflowTaskStatus = 'OPEN' | 'APPROVED' | 'REJECTED' | 'RETURNED' | 'TRANSFERRED' | 'CANCELLED'
export type WorkflowAction = 'APPROVE' | 'REJECT' | 'RETURN' | 'TRANSFER'

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
  stepName: string
  workflowInstanceId: string
  stepInstanceId: string
  version: number
  amount?: number
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

export interface WorkflowTemplate {
  id: string
  code: string
  name: string
  resourceType: string
  priority: number
  status: 'DRAFT' | 'PUBLISHED' | 'RETIRED'
  currentVersionId?: string
  version: number
}

export interface WorkflowStepDraft {
  stepKey: string
  name: string
  sequence: number
  completionMode: 'SERIAL' | 'PARALLEL_ALL' | 'PARALLEL_ANY'
  approverType: 'USER' | 'ROLE' | 'DEPARTMENT_MANAGER' | 'DOCUMENT_OWNER' | 'REQUESTER_MANAGER'
  approverRef?: string
  dueHours?: number
}

export interface WorkflowVersion {
  id: string
  templateId: string
  versionNumber: number
  status: 'DRAFT' | 'PUBLISHED' | 'RETIRED'
  steps: WorkflowStepDraft[]
  publishedAt?: string
}

export interface WorkflowDecision {
  id: string
  action: string
  originalApproverUserId?: string
  actualActorUserId: string
  comment: string
  createdAt: string
}


export interface WorkflowAttachment {
  id: string
  resourceType: string
  resourceId: string
  originalFilename: string
  mediaType: string
  sizeBytes: number
  sha256Hex: string
  uploaderUserId: string
  status: string
  createdAt: string
  linkedAt?: string
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
  const response = await apiClient.get<ApiEnvelope<Array<Omit<WorkflowTask, 'title' | 'amount'>>>>('/v2/workflows/tasks', {
    params: { view: 'MINE', overdue: false },
  })
  const content = response.data.data.map((task) => ({ ...task, title: task.stepName ?? task.resourceType, amount: 0 }))
  return {
    content,
    page,
    size,
    totalElements: content.length,
    totalPages: content.length ? 1 : 0,
  }
}

export async function createWorkflowTask(payload: TaskRequest) {
  const response = await apiClient.post<ApiEnvelope<WorkflowTask>>('/v1/workflow/tasks', payload)
  return response.data.data
}

export async function actOnWorkflowTask(id: string, version: number, action: WorkflowAction, comment: string, transferToUserId?: string) {
  const response = await apiClient.post<ApiEnvelope<WorkflowTask>>(
    `/v2/workflows/tasks/${id}/actions`,
    { action, comment, transferToUserId },
    { headers: { 'If-Match': String(version) } },
  )
  return { task: { ...response.data.data, title: response.data.data.stepName ?? response.data.data.resourceType, amount: 0 }, action, notificationCreated: true }
}

export async function listWorkflowNotifications(page = 0, size = 20) {
  const response = await apiClient.get<ApiEnvelope<WorkflowNotification[]>>('/v2/collaboration/notifications')
  return { content: response.data.data, page, size, totalElements: response.data.data.length, totalPages: response.data.data.length ? 1 : 0 }
}

export async function getWorkflowUnreadCount() {
  const response = await apiClient.get<ApiEnvelope<{ count: number }>>('/v2/collaboration/notifications/unread-count')
  return response.data.data.count
}

export async function markWorkflowNotificationRead(id: string) {
  await apiClient.post('/v2/collaboration/notifications/read', { notificationIds: [id] })
  return { id, read: true } as WorkflowNotification
}

export async function listResourceComments(resourceType: string, resourceId: string) {
  const response = await apiClient.get<ApiEnvelope<WorkflowComment[]>>(`/v2/collaboration/resources/${resourceType}/${resourceId}/comments`)
  return { content: response.data.data, page: 0, size: 50, totalElements: response.data.data.length, totalPages: response.data.data.length ? 1 : 0 }
}

export async function addResourceComment(resourceType: string, resourceId: string, body: string, mentionedUserIds: string[] = []) {
  const response = await apiClient.post<ApiEnvelope<WorkflowComment>>(`/v2/collaboration/resources/${resourceType}/${resourceId}/comments`, { body, mentionedUserIds })
  return response.data.data
}

export async function listResourceActivities(resourceType: string, resourceId: string) {
  const response = await apiClient.get<ApiEnvelope<WorkflowActivity[]>>(`/v2/collaboration/resources/${resourceType}/${resourceId}/activities`)
  return { content: response.data.data, page: 0, size: 50, totalElements: response.data.data.length, totalPages: response.data.data.length ? 1 : 0 }
}

export async function listWorkflowTemplates() {
  const response = await apiClient.get<ApiEnvelope<WorkflowTemplate[]>>('/v2/workflows/templates')
  return response.data.data
}

export async function createWorkflowTemplate(payload: {
  code: string; name: string; resourceType: string; priority: number; reason: string
}) {
  const response = await apiClient.post<ApiEnvelope<WorkflowTemplate>>('/v2/workflows/templates', payload)
  return response.data.data
}

export async function createWorkflowVersion(templateId: string, steps: WorkflowStepDraft[]) {
  const response = await apiClient.post<ApiEnvelope<WorkflowVersion>>(`/v2/workflows/templates/${templateId}/versions`, {
    condition: { logic: 'ALL', conditions: [] },
    steps,
    allowSelfApproval: false,
  })
  return response.data.data
}

export async function publishWorkflowVersion(templateId: string, versionId: string, reason: string) {
  const response = await apiClient.post<ApiEnvelope<WorkflowVersion>>(
    `/v2/workflows/templates/${templateId}/versions/${versionId}/publish`,
    { reason },
  )
  return response.data.data
}

export async function listWorkflowDecisions(instanceId: string) {
  const response = await apiClient.get<ApiEnvelope<WorkflowDecision[]>>(`/v2/workflows/instances/${instanceId}/decisions`)
  return response.data.data
}

export async function listResourceAttachments(resourceType: string, resourceId: string) {
  const response = await apiClient.get<ApiEnvelope<WorkflowAttachment[]>>(
    `/v2/collaboration/resources/${resourceType}/${resourceId}/attachments`,
  )
  return response.data.data
}

export async function uploadResourceAttachment(resourceType: string, resourceId: string, file: File) {
  const form = new FormData()
  form.append('file', file)
  const response = await apiClient.post<ApiEnvelope<WorkflowAttachment>>(
    `/v2/collaboration/resources/${resourceType}/${resourceId}/attachments`,
    form,
  )
  return response.data.data
}

export async function downloadResourceAttachment(attachment: WorkflowAttachment) {
  const response = await apiClient.get<Blob>(
    `/v2/collaboration/attachments/${attachment.id}/content`,
    { responseType: 'blob' },
  )
  const url = URL.createObjectURL(response.data)
  const link = document.createElement('a')
  link.href = url
  link.download = attachment.originalFilename
  link.click()
  URL.revokeObjectURL(url)
}
