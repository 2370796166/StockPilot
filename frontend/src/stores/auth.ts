import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { getCurrentUser, login as loginRequest } from '../api/auth'
import type { CurrentUser, LoginRequest } from '../types/auth'
import { clearSession, getAccessToken, saveSession } from '../utils/session'

export const useAuthStore = defineStore('auth', () => {
  const user = ref<CurrentUser | null>(null)
  const loading = ref(false)
  const initialized = ref(false)
  const authenticated = computed(() => Boolean(getAccessToken() && user.value))

  function can(authority: string): boolean {
    return user.value?.authorities.includes(authority) ?? false
  }

  async function loadCurrentUser(): Promise<boolean> {
    if (!getAccessToken()) {
      user.value = null
      initialized.value = true
      return false
    }
    try {
      user.value = await getCurrentUser()
      return true
    } catch {
      user.value = null
      return false
    } finally {
      initialized.value = true
    }
  }

  async function login(credentials: LoginRequest): Promise<void> {
    loading.value = true
    try {
      const token = await loginRequest(credentials)
      saveSession(token.accessToken, token.expiresAt)
      if (!(await loadCurrentUser())) throw new Error('CURRENT_USER_LOAD_FAILED')
    } finally {
      loading.value = false
    }
  }

  function logout(): void {
    clearSession()
    user.value = null
    initialized.value = true
  }

  return { user, loading, initialized, authenticated, can, loadCurrentUser, login, logout }
})
