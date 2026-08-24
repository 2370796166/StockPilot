import type { CurrentUser, LoginRequest, TokenResponse } from '../types/auth'
import { request } from '../utils/request'

export const login = (data: LoginRequest) =>
  request<TokenResponse>({ method: 'POST', url: '/auth/login', data })

export const getCurrentUser = () =>
  request<CurrentUser>({ method: 'GET', url: '/auth/me' })
