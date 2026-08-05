import { defineStore } from 'pinia'
import { ref } from 'vue'
import { apiClient } from '@/api/http'

export interface AuthUser {
  id: string
  username: string
  displayName: string
  organizationId: string
  organizationName: string
  roles: string[]
}

interface ApiResponse<T> {
  data: T
  requestId: string
}

export const useAuthStore = defineStore('auth', () => {
  const user = ref<AuthUser | null>(null)
  const initialized = ref(false)
  const loading = ref(false)

  async function ensureCsrf() {
    await apiClient.get<ApiResponse<{ token: string }>>('/v1/auth/csrf')
  }

  async function ensureSession() {
    if (initialized.value) {
      return user.value !== null
    }

    try {
      const response = await apiClient.get<ApiResponse<AuthUser>>('/v1/auth/me')
      user.value = response.data.data
    } catch {
      user.value = null
    } finally {
      initialized.value = true
    }

    return user.value !== null
  }

  async function login(username: string, password: string) {
    loading.value = true
    try {
      await ensureCsrf()
      const response = await apiClient.post<ApiResponse<AuthUser>>('/v1/auth/login', { username, password })
      user.value = response.data.data
      initialized.value = true
    } finally {
      loading.value = false
    }
  }

  async function logout() {
    try {
      await ensureCsrf()
      await apiClient.post('/v1/auth/logout')
    } finally {
      user.value = null
      initialized.value = true
    }
  }

  return { user, initialized, loading, ensureSession, login, logout }
})
