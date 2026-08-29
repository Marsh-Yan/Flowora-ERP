import { defineStore } from 'pinia'
import { ref } from 'vue'
import { apiClient } from '@/api/http'

export interface AuthUser {
  id: string
  username: string
  displayName: string
  organizationId: string
  organizationName: string
  departmentId: string | null
  dataScope: 'ALL' | 'DEPARTMENT' | 'SELF' | 'ASSIGNED'
  permissions: string[]
  mustChangePassword: boolean
  roles: string[]
}

export interface OrganizationOption {
  id: string
  name: string
  defaultOrganization: boolean
  departmentId: string | null
}

interface ApiResponse<T> {
  data: T
  requestId: string
}

export const useAuthStore = defineStore('auth', () => {
  const user = ref<AuthUser | null>(null)
  const initialized = ref(false)
  const organizations = ref<OrganizationOption[]>([])
  const loading = ref(false)

  async function ensureCsrf() {
    await apiClient.get<ApiResponse<{ token: string }>>('/v2/session/csrf')
  }

  async function ensureSession() {
    if (initialized.value) {
      return user.value !== null
    }

    try {
      const response = await apiClient.get<ApiResponse<AuthUser>>('/v2/session/me')
      user.value = response.data.data
    } catch {
      user.value = null
    } finally {
      initialized.value = true
    }

    return user.value !== null
  }

  async function login(username: string, password: string, mfaCode?: string) {
    loading.value = true
    try {
      await ensureCsrf()
      const response = await apiClient.post<ApiResponse<AuthUser>>('/v2/session/login', { username, password, mfaCode })
      user.value = response.data.data
      initialized.value = true
    } finally {
      loading.value = false
    }
  }

  async function logout() {
    try {
      await ensureCsrf()
      await apiClient.post('/v2/session/logout')
    } finally {
      user.value = null
      initialized.value = true
      organizations.value = []
    }
  }


  function hasPermission(permission?: string) {
    return !permission || user.value?.permissions.includes(permission) === true
  }

  async function loadOrganizations() {
    const response = await apiClient.get<ApiResponse<OrganizationOption[]>>('/v2/session/organizations')
    organizations.value = response.data.data
    return organizations.value
  }

  async function switchOrganization(organizationId: string) {
    await ensureCsrf()
    const response = await apiClient.post<ApiResponse<AuthUser>>('/v2/session/switch-organization', {
      organizationId,
    })
    user.value = response.data.data
    return user.value
  }
  return { user, organizations, initialized, loading, ensureCsrf, ensureSession, login, logout, hasPermission, loadOrganizations, switchOrganization }
})
