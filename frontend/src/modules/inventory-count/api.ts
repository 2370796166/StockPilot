import type { PageResult } from '@/shared/types/api'
import type { CountDetail, CountStatus, CountSummary } from '@/modules/documents/types'
import { request } from '@/shared/utils/request'
export const pageCounts = (params: {
  page: number
  size: number
  countNo?: string
  warehouseId?: number
  status?: CountStatus
}) => request<PageResult<CountSummary>>({ method: 'GET', url: '/inventory-counts', params })
export const getCount = (id: number) => request<CountDetail>({ method: 'GET', url: `/inventory-counts/${id}` })
export const createCount = (data: {
  countNo: string
  warehouseId: number
  remark: string
  dimensions: Array<{ locationId: number; skuId: number }>
}) => request<CountDetail>({ method: 'POST', url: '/inventory-counts', data })
export const transitionCount = (id: number, action: string, version?: number) =>
  request<CountDetail>({
    method: 'POST',
    url: `/inventory-counts/${id}/${action}`,
    data: version === undefined ? undefined : { version },
  })
export const recordCountResults = (
  id: number,
  version: number,
  results: Array<{ lineId: number; countedQuantity: number; reason: string }>,
) => request<CountDetail>({ method: 'PUT', url: `/inventory-counts/${id}/results`, data: { version, results } })
