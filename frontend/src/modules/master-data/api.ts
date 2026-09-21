import type { DataStatus, PageQuery, PageResult } from '@/shared/types/api'
import type {
  LocationRecord,
  MasterDataForm,
  MasterDataRecord,
  MasterDataResource,
  SkuRecord,
} from '@/modules/master-data/types'
import { request } from '@/shared/utils/request'

const OPTION_CACHE_TTL_MS = 30_000
const optionCache = new Map<
  string,
  { expiresAt: number; value: Promise<PageResult<MasterDataRecord | LocationRecord>> }
>()
const labelCache = new Map<string, string>()

function rememberLabels(resource: MasterDataResource | 'locations', records: Array<MasterDataRecord | LocationRecord>) {
  records.forEach((item) => labelCache.set(`${resource}:${item.id}`, `${item.code} · ${item.name}`))
}

function clearReferenceCache() {
  optionCache.clear()
  labelCache.clear()
}

async function cachedOptions<T extends MasterDataRecord | LocationRecord>(
  key: string,
  loader: () => Promise<PageResult<T>>,
) {
  const current = optionCache.get(key)
  if (current && current.expiresAt > Date.now()) return current.value as Promise<PageResult<T>>
  const value = loader().catch((error) => {
    optionCache.delete(key)
    throw error
  })
  optionCache.set(key, { expiresAt: Date.now() + OPTION_CACHE_TTL_MS, value })
  return value
}

export const pageMasterData = (resource: MasterDataResource, params: PageQuery) =>
  request<PageResult<MasterDataRecord>>({ method: 'GET', url: `/master-data/${resource}`, params })

export const getMasterData = (resource: MasterDataResource, id: number) =>
  request<MasterDataRecord>({ method: 'GET', url: `/master-data/${resource}/${id}` })

export const getLocation = (id: number) =>
  request<LocationRecord>({ method: 'GET', url: `/master-data/locations/${id}` })
export const getSku = (id: number) => request<SkuRecord>({ method: 'GET', url: `/master-data/skus/${id}` })

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

export const pageLocations = (params: LocationQuery) =>
  request<PageResult<LocationRecord>>({ method: 'GET', url: '/master-data/locations', params })

export async function searchMasterDataOptions(resource: MasterDataResource, keyword = '') {
  const normalized = keyword.trim()
  const result = await cachedOptions(`${resource}:enabled:${normalized}`, () =>
    pageMasterData(resource, { page: 1, size: 50, keyword: normalized || undefined, status: 'ENABLED' }),
  )
  rememberLabels(resource, result.records)
  return result
}

export async function searchLocationOptions(warehouseId: number, keyword = '') {
  const normalized = keyword.trim()
  const result = await cachedOptions(`locations:${warehouseId}:enabled:${normalized}`, () =>
    pageLocations({ page: 1, size: 50, warehouseId, keyword: normalized || undefined, status: 'ENABLED' }),
  )
  rememberLabels('locations', result.records)
  return result
}

export async function resolveReferenceLabel(resource: MasterDataResource | 'locations', id: number) {
  const key = `${resource}:${id}`
  const cached = labelCache.get(key)
  if (cached) return cached
  const result = await cachedOptions(`${resource}:all`, () =>
    resource === 'locations' ? pageLocations({ page: 1, size: 100 }) : pageMasterData(resource, { page: 1, size: 100 }),
  )
  rememberLabels(resource, result.records)
  const fromPage = labelCache.get(key)
  if (fromPage) return fromPage
  const item =
    resource === 'locations'
      ? await getLocation(id)
      : resource === 'skus'
        ? await getSku(id)
        : await getMasterData(resource, id)
  const label = `${item.code} · ${item.name}`
  labelCache.set(key, label)
  return label
}

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
