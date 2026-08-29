import { apiClient } from '@/api/http'

interface ApiResponse<T> {
  data: T
  requestId: string
}

export interface Organization {
  id: string
  parentId: string | null
  name: string
  status: 'ACTIVE' | 'ARCHIVED'
  baseCurrencyCode: string
  timezone: string
}

export interface Department {
  id: string
  parentId: string | null
  code: string
  name: string
  managerMembershipId: string | null
  active: boolean
  version: number
}

export interface Role {
  id: string
  code: string
  name: string
  description: string | null
  dataScope: 'ALL' | 'DEPARTMENT' | 'SELF' | 'ASSIGNED'
  systemRole: boolean
  active: boolean
  permissions: string[]
}

export interface Permission {
  code: string
  resource: string
  action: string
  description: string
  sensitive: boolean
}

export interface PlatformUser {
  id: string
  username: string
  displayName: string
  status: 'INVITED' | 'ACTIVE' | 'LOCKED' | 'DISABLED'
  membershipId: string
  organizationId: string
  departmentId: string | null
  membershipStatus: string
  defaultOrganization: boolean
  roleIds: string[]
}

export interface ActiveSession {
  id: string
  createdAt: string
  lastAccessedAt: string
  maxInactiveSeconds: number
  expired: boolean
  current: boolean
}

async function get<T>(path: string) {
  const response = await apiClient.get<ApiResponse<T>>(path)
  return response.data.data
}

async function post<T>(path: string, body: unknown = {}) {
  const response = await apiClient.post<ApiResponse<T>>(path, body)
  return response.data.data
}

export const platformApi = {
  organizations: () => get<Organization[]>('/v2/organizations'),
  departments: () => get<Department[]>('/v2/departments'),
  roles: () => get<Role[]>('/v2/roles'),
  permissions: () => get<Permission[]>('/v2/permissions'),
  users: () => get<PlatformUser[]>('/v2/users'),
  sessions: () => get<ActiveSession[]>('/v2/session/active'),
  revokeSession: (sessionId: string, reason: string) =>
    post<{ revoked: boolean }>(`/v2/session/active/${sessionId}/revoke`, { reason }),
  changePassword: (currentPassword: string, newPassword: string) =>
    post<{ reauthenticationRequired: boolean }>('/v2/session/change-password', {
      currentPassword,
      newPassword,
    }),
  startMfaEnrollment: () =>
    post<{ secret: string; otpauthUri: string }>('/v2/session/mfa/enroll'),
  confirmMfaEnrollment: (code: string) =>
    post<{ codes: string[] }>('/v2/session/mfa/confirm', { code }),
  disableMfa: (code: string) => post<{ enabled: boolean }>('/v2/session/mfa/disable', { code }),
}
