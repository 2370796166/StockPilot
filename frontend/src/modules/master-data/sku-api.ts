import type { DataStatus, PageQuery, PageResult } from '@/shared/types/api'
import type { SkuRecord } from '@/modules/master-data/types'
import { clearReferenceCache } from '@/modules/master-data/reference-cache'
import { request } from '@/shared/utils/request'

export interface SkuQuery extends PageQuery {
  categoryId?: number
}

export interface SkuCreate {
  code: string
  name: string
  categoryId?: number
  unit: string
  remark: string
}

export interface SkuUpdate {
  name: string
  categoryId?: number
  unit: string
  remark: string
  version: number
}

export const getSku = (id: number) => request<SkuRecord>({ method: 'GET', url: `/master-data/skus/${id}` })

export const pageSkus = (params: SkuQuery) =>
  request<PageResult<SkuRecord>>({ method: 'GET', url: '/master-data/skus', params })

export const createSku = (data: SkuCreate) =>
  request<SkuRecord>({ method: 'POST', url: '/master-data/skus', data }).then((result) => {
    clearReferenceCache()
    return result
  })

export const updateSku = (id: number, data: SkuUpdate) =>
  request<SkuRecord>({ method: 'PUT', url: `/master-data/skus/${id}`, data }).then((result) => {
    clearReferenceCache()
    return result
  })

export const changeSkuStatus = (id: number, status: DataStatus, version: number) =>
  request<SkuRecord>({ method: 'PATCH', url: `/master-data/skus/${id}/status`, data: { status, version } }).then(
    (result) => {
      clearReferenceCache()
      return result
    },
  )
