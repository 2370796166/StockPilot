import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { getCurrentUser, login as loginRequest } from '@/modules/auth/api'
import type { CurrentUser, LoginRequest } from '@/modules/auth/types'
import { clearSession, getAccessToken, saveSession } from '@/shared/utils/session'

export const useAuthStore = defineStore('auth', () => {
  const user = ref<CurrentUser | null>(null)
  const loading = ref(false)
  const initialized = ref(false)
  const authenticated = computed(() => Boolean(getAccessToken() && user.value))

  // 权限判断基于后端当前用户接口返回的实时权限集合，用于菜单、按钮和路由守卫。
  function can(authority: string): boolean {
    return user.value?.authorities.includes(authority) ?? false
  }

  // 使用已有 Token 向后端重新确认用户状态和授权；Token 失效或用户停用时清空内存用户。
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

  // 登录成功后先保存 Access Token，再加载当前用户；两步都成功才建立完整前端登录态。
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

  // 退出登录只清理本地无状态令牌和用户信息，后端不维护可供注销的服务端会话。
  function logout(): void {
    clearSession()
    user.value = null
    initialized.value = true
  }

  return { user, loading, initialized, authenticated, can, loadCurrentUser, login, logout }
})
