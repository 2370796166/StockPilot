import type { CurrentUser, LoginRequest, TokenResponse } from '@/modules/auth/types'
import { request } from '@/shared/utils/request'

export const login = (data: LoginRequest) => request<TokenResponse>({ method: 'POST', url: '/auth/login', data })

export const getCurrentUser = () => request<CurrentUser>({ method: 'GET', url: '/auth/me' })
