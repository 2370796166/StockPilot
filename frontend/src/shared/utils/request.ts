import axios, { AxiosError, type AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import type { ApiResponse } from '@/shared/types/api'
import { clearSession, getAccessToken } from './session'

const client = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || '/api',
  timeout: 15_000,
})

// 所有 API 请求统一附带 Bearer Token，业务页面无需重复处理认证请求头。
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

// 统一转换 HTTP 认证、授权、校验和服务端异常；401 会清理失效会话并保留原访问地址。
client.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ApiResponse<unknown>>) => {
    if (axios.isCancel(error)) return Promise.reject(error)
    const status = error.response?.status
    if (status === 401) {
      if (error.config?.url === '/auth/login') {
        ElMessage.error(error.response?.data?.message || '用户名或密码错误')
      } else {
        // 旧会话的迟到401不能清除用户刚刚建立的新会话。
        const currentToken = getAccessToken()
        if (currentToken && error.config?.headers?.Authorization !== `Bearer ${currentToken}`) {
          return Promise.reject(error)
        }
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

// 解包后端统一响应结构；即使 HTTP 成功，业务 code 非 SUCCESS 仍按失败处理。
export async function request<T>(config: AxiosRequestConfig): Promise<T> {
  const response = await client.request<ApiResponse<T>>(config)
  if (response.data.code !== 'SUCCESS') {
    ElMessage.error(response.data.message || '业务处理失败')
    throw new Error(response.data.code)
  }
  return response.data.data
}
