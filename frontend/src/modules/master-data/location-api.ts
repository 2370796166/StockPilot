import type { DataStatus, PageQuery, PageResult } from '@/shared/types/api'
import type { LocationRecord } from '@/modules/master-data/types'
import { clearReferenceCache } from '@/modules/master-data/reference-cache'
import { request } from '@/shared/utils/request'

export interface LocationQuery extends PageQuery {
  warehouseId?: number
}

export interface LocationCreate {
  warehouseId: number
  code: string
  name: string
  remark: string
}

export interface LocationUpdate {
  warehouseId: number
  name: string
  remark: string
  version: number
}

export const getLocation = (id: number) =>
  request<LocationRecord>({ method: 'GET', url: `/master-data/locations/${id}` })

export const pageLocations = (params: LocationQuery) =>
  request<PageResult<LocationRecord>>({ method: 'GET', url: '/master-data/locations', params })

export const createLocation = (data: LocationCreate) =>
  request<LocationRecord>({ method: 'POST', url: '/master-data/locations', data }).then((result) => {
    clearReferenceCache()
    return result
  })

export const updateLocation = (id: number, data: LocationUpdate) =>
  request<LocationRecord>({ method: 'PUT', url: `/master-data/locations/${id}`, data }).then((result) => {
    clearReferenceCache()
    return result
  })

export const changeLocationStatus = (id: number, status: DataStatus, version: number) =>
  request<LocationRecord>({
    method: 'PATCH',
    url: `/master-data/locations/${id}/status`,
    data: { status, version },
  }).then((result) => {
    clearReferenceCache()
    return result
  })
