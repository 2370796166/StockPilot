import type { DataStatus, PageQuery, PageResult } from '@/shared/types/api'
import type { MasterDataForm, MasterDataRecord, MasterDataResource } from '@/modules/master-data/types'
import { clearReferenceCache } from '@/modules/master-data/reference-cache'
import { request } from '@/shared/utils/request'

export const pageMasterData = (resource: MasterDataResource, params: PageQuery) =>
  request<PageResult<MasterDataRecord>>({ method: 'GET', url: `/master-data/${resource}`, params })

export const getMasterData = (resource: MasterDataResource, id: number) =>
  request<MasterDataRecord>({ method: 'GET', url: `/master-data/${resource}/${id}` })

export const createMasterData = (resource: MasterDataResource, data: MasterDataForm) =>
  request<MasterDataRecord>({ method: 'POST', url: `/master-data/${resource}`, data }).then((result) => {
    clearReferenceCache()
    return result
  })

export const updateMasterData = (resource: MasterDataResource, id: number, data: MasterDataForm) =>
  request<MasterDataRecord>({ method: 'PUT', url: `/master-data/${resource}/${id}`, data }).then((result) => {
    clearReferenceCache()
    return result
  })

export const changeMasterDataStatus = (resource: MasterDataResource, id: number, status: DataStatus, version: number) =>
  request<MasterDataRecord>({
    method: 'PATCH',
    url: `/master-data/${resource}/${id}/status`,
    data: { status, version },
  }).then((result) => {
    clearReferenceCache()
    return result
  })
