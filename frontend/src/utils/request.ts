import axios, { AxiosError, type AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import type { ApiResponse } from '../types/api'
import { clearSession, getAccessToken } from './session'

const client = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || '/api',
  timeout: 15_000,
})

client.interceptors.request.use((config) => {
  const token = getAccessToken()
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

function redirectToLogin(): void {
  clearSession()
  if (window.location.pathname === '/login') return
  const redirect = `${window.location.pathname}${window.location.search}`
  window.location.replace(`/login?redirect=${encodeURIComponent(redirect)}`)
}

client.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ApiResponse<unknown>>) => {
    const status = error.response?.status
    if (status === 401) {
      if (error.config?.url === '/auth/login') {
        ElMessage.error(error.response?.data?.message || '用户名或密码错误')
      } else {
        redirectToLogin()
        ElMessage.error('登录状态已失效，请重新登录')
      }
    } else if (status === 403) {
      ElMessage.error('当前账号无权执行此操作')
    } else if (status && status < 500) {
      ElMessage.error(error.response?.data?.message || '请求未能完成，请检查输入')
    } else {
      ElMessage.error('系统暂时不可用，请稍后重试')
    }
    return Promise.reject(error)
  },
)

export async function request<T>(config: AxiosRequestConfig): Promise<T> {
  const response = await client.request<ApiResponse<T>>(config)
  if (response.data.code !== 'SUCCESS') {
    ElMessage.error(response.data.message || '业务处理失败')
    throw new Error(response.data.code)
  }
  return response.data.data
}
