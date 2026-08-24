import type { DataStatus, PageQuery, PageResult } from '../types/api'
import type { LocationRecord, MasterDataForm, MasterDataRecord, MasterDataResource, SkuRecord } from '../types/masterData'
import { request } from '../utils/request'

export const pageMasterData = (resource: MasterDataResource, params: PageQuery) =>
  request<PageResult<MasterDataRecord>>({ method: 'GET', url: `/master-data/${resource}`, params })

export const getMasterData = (resource: MasterDataResource, id: number) =>
  request<MasterDataRecord>({ method: 'GET', url: `/master-data/${resource}/${id}` })

export const getLocation = (id: number) => request<LocationRecord>({ method: 'GET', url: `/master-data/locations/${id}` })
export const getSku = (id: number) => request<SkuRecord>({ method: 'GET', url: `/master-data/skus/${id}` })

export const createMasterData = (resource: MasterDataResource, data: MasterDataForm) =>
  request<MasterDataRecord>({ method: 'POST', url: `/master-data/${resource}`, data })

export const updateMasterData = (resource: MasterDataResource, id: number, data: MasterDataForm) =>
  request<MasterDataRecord>({ method: 'PUT', url: `/master-data/${resource}/${id}`, data })

export const changeMasterDataStatus = (resource: MasterDataResource, id: number, status: DataStatus, version: number) =>
  request<MasterDataRecord>({ method: 'PATCH', url: `/master-data/${resource}/${id}/status`, data: { status, version } })

export interface LocationQuery extends PageQuery { warehouseId?: number }
export interface LocationCreate { warehouseId: number; code: string; name: string; remark: string }
export interface LocationUpdate { warehouseId: number; name: string; remark: string; version: number }

export const pageLocations = (params: LocationQuery) =>
  request<PageResult<LocationRecord>>({ method: 'GET', url: '/master-data/locations', params })

export const createLocation = (data: LocationCreate) =>
  request<LocationRecord>({ method: 'POST', url: '/master-data/locations', data })

export const updateLocation = (id: number, data: LocationUpdate) =>
  request<LocationRecord>({ method: 'PUT', url: `/master-data/locations/${id}`, data })

export const changeLocationStatus = (id: number, status: DataStatus, version: number) =>
  request<LocationRecord>({ method: 'PATCH', url: `/master-data/locations/${id}/status`, data: { status, version } })

export interface SkuQuery extends PageQuery { categoryId?: number }
export interface SkuCreate { code: string; name: string; categoryId?: number; unit: string; remark: string }
export interface SkuUpdate { name: string; categoryId?: number; unit: string; remark: string; version: number }

export const pageSkus = (params: SkuQuery) =>
  request<PageResult<SkuRecord>>({ method: 'GET', url: '/master-data/skus', params })

export const createSku = (data: SkuCreate) =>
  request<SkuRecord>({ method: 'POST', url: '/master-data/skus', data })

export const updateSku = (id: number, data: SkuUpdate) =>
  request<SkuRecord>({ method: 'PUT', url: `/master-data/skus/${id}`, data })

export const changeSkuStatus = (id: number, status: DataStatus, version: number) =>
  request<SkuRecord>({ method: 'PATCH', url: `/master-data/skus/${id}/status`, data: { status, version } })
