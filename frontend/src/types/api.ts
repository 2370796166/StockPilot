export interface ApiResponse<T> {
  code: string
  message: string
  data: T
  timestamp: string
}

export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
}

export type DataStatus = 'ENABLED' | 'DISABLED'

export interface PageQuery {
  page: number
  size: number
  code?: string
  name?: string
  status?: DataStatus
}
