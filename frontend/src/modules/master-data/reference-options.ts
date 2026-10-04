import { getMasterData, pageMasterData } from '@/modules/master-data/api'
import { getLocation, pageLocations } from '@/modules/master-data/location-api'
import {
  cachedReferenceOptions,
  cachedReferenceValue,
  referenceCacheVersion,
  getReferenceLabel,
  rememberReferenceLabels,
  setReferenceLabel,
} from '@/modules/master-data/reference-cache'
import { getSku } from '@/modules/master-data/sku-api'
import type { MasterDataResource } from '@/modules/master-data/types'

export async function searchMasterDataOptions(resource: MasterDataResource, keyword = '') {
  const version = referenceCacheVersion()
  const normalized = keyword.trim()
  const result = await cachedReferenceOptions(`${resource}:enabled:${normalized}`, () =>
    pageMasterData(resource, { page: 1, size: 50, keyword: normalized || undefined, status: 'ENABLED' }),
  )
  rememberReferenceLabels(resource, result.records, version)
  return result
}

export async function searchLocationOptions(warehouseId: number, keyword = '') {
  const version = referenceCacheVersion()
  const normalized = keyword.trim()
  const result = await cachedReferenceOptions(`locations:${warehouseId}:enabled:${normalized}`, () =>
    pageLocations({ page: 1, size: 50, warehouseId, keyword: normalized || undefined, status: 'ENABLED' }),
  )
  rememberReferenceLabels('locations', result.records, version)
  return result
}

export async function resolveReferenceLabel(resource: MasterDataResource | 'locations', id: number) {
  const version = referenceCacheVersion()
  const cached = getReferenceLabel(resource, id)
  if (cached) return cached
  const result = await cachedReferenceOptions(`${resource}:all`, () =>
    resource === 'locations' ? pageLocations({ page: 1, size: 100 }) : pageMasterData(resource, { page: 1, size: 100 }),
  )
  rememberReferenceLabels(resource, result.records, version)
  const fromPage = getReferenceLabel(resource, id)
  if (fromPage) return fromPage
  const item = await cachedReferenceValue(`${resource}:detail:${id}`, () =>
    resource === 'locations' ? getLocation(id) : resource === 'skus' ? getSku(id) : getMasterData(resource, id),
  )
  const label = `${item.code} · ${item.name}`
  setReferenceLabel(resource, id, label, version)
  return label
}
