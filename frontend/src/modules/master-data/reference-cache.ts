import type { PageResult } from '@/shared/types/api'
import type { LocationRecord, MasterDataRecord, MasterDataResource } from '@/modules/master-data/types'

const OPTION_CACHE_TTL_MS = 30_000
const MAX_REQUEST_ENTRIES = 256
const MAX_LABEL_ENTRIES = 1000
const optionCache = new Map<string, { expiresAt: number; value: Promise<unknown> }>()
const labelCache = new Map<string, { expiresAt: number; value: string }>()
let generation = 0

export const referenceCacheVersion = () => generation

function prune<T extends { expiresAt: number }>(cache: Map<string, T>, limit: number) {
  const now = Date.now()
  for (const [key, entry] of cache) if (entry.expiresAt <= now) cache.delete(key)
  while (cache.size > limit) cache.delete(cache.keys().next().value!)
}

export function rememberReferenceLabels(
  resource: MasterDataResource | 'locations',
  records: Array<MasterDataRecord | LocationRecord>,
  expectedVersion = generation,
) {
  if (expectedVersion !== generation) return
  const expiresAt = Date.now() + OPTION_CACHE_TTL_MS
  records.forEach((item) =>
    labelCache.set(`${resource}:${item.id}`, { value: `${item.code} · ${item.name}`, expiresAt }),
  )
  prune(labelCache, MAX_LABEL_ENTRIES)
}

export function clearReferenceCache() {
  generation++
  optionCache.clear()
  labelCache.clear()
}

export function getReferenceLabel(resource: MasterDataResource | 'locations', id: number) {
  const key = `${resource}:${id}`
  const entry = labelCache.get(key)
  if (entry && entry.expiresAt > Date.now()) return entry.value
  labelCache.delete(key)
}

export function setReferenceLabel(
  resource: MasterDataResource | 'locations',
  id: number,
  label: string,
  expectedVersion = generation,
) {
  if (expectedVersion !== generation) return
  labelCache.set(`${resource}:${id}`, { value: label, expiresAt: Date.now() + OPTION_CACHE_TTL_MS })
  prune(labelCache, MAX_LABEL_ENTRIES)
}

export function cachedReferenceValue<T>(key: string, loader: () => Promise<T>): Promise<T> {
  const current = optionCache.get(key)
  if (current && current.expiresAt > Date.now()) return current.value as Promise<T>
  const value = Promise.resolve()
    .then(loader)
    .catch((error) => {
      if (optionCache.get(key)?.value === value) optionCache.delete(key)
      throw error
    })
  optionCache.set(key, { expiresAt: Date.now() + OPTION_CACHE_TTL_MS, value })
  prune(optionCache, MAX_REQUEST_ENTRIES)
  return value
}

export function cachedReferenceOptions<T extends MasterDataRecord | LocationRecord>(
  key: string,
  loader: () => Promise<PageResult<T>>,
) {
  return cachedReferenceValue(key, loader)
}
