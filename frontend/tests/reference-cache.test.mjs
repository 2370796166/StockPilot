import test from 'node:test'
import assert from 'node:assert/strict'
import { moduleLoader } from './module-harness.mjs'
import { deferred } from './component-harness.mjs'

const cacheId = '@/modules/master-data/reference-cache'
const page = (records = []) => ({ records, total: records.length, page: 1, size: 100 })

test('old failure cannot evict a replacement request after cache invalidation', async () => {
  const cache = moduleLoader()(cacheId)
  const old = deferred(),
    fresh = deferred()
  const first = cache.cachedReferenceOptions('same', () => old.promise)
  cache.clearReferenceCache()
  const second = cache.cachedReferenceOptions('same', () => fresh.promise)
  old.reject(new Error('old failure'))
  await assert.rejects(first)
  assert.equal(
    cache.cachedReferenceOptions('same', () => assert.fail('duplicate request')),
    second,
  )
  fresh.resolve(page())
  await second
})

test('labels expire and stale responses cannot restore cleared names', () => {
  let now = 0
  const cache = moduleLoader({}, { Date: { now: () => now } })(cacheId)
  const version = cache.referenceCacheVersion()
  cache.setReferenceLabel('skus', 1, 'old', version)
  now = 30_001
  assert.equal(cache.getReferenceLabel('skus', 1), undefined)
  cache.clearReferenceCache()
  cache.setReferenceLabel('skus', 1, 'new')
  cache.rememberReferenceLabels('skus', [{ id: 1, code: 'OLD', name: 'old' }], version)
  assert.equal(cache.getReferenceLabel('skus', 1), 'new')
})

test('request and label caches evict oldest entries when capacity is exceeded', async () => {
  const cache = moduleLoader()(cacheId)
  let firstCalls = 0
  const first = () => {
    firstCalls++
    return Promise.resolve(page())
  }
  await cache.cachedReferenceOptions('0', first)
  for (let i = 1; i <= 256; i++) await cache.cachedReferenceOptions(String(i), async () => page())
  await cache.cachedReferenceOptions('0', first)
  assert.equal(firstCalls, 2)
  for (let i = 0; i <= 1000; i++) cache.setReferenceLabel('skus', i, String(i))
  assert.equal(cache.getReferenceLabel('skus', 0), undefined)
  assert.equal(cache.getReferenceLabel('skus', 1000), '1000')
})

test('concurrent labels outside the first page share one list and one detail request', async () => {
  let pages = 0,
    details = 0
  const load = moduleLoader({
    '@/modules/master-data/api': {
      pageMasterData: async () => {
        pages++
        return page()
      },
    },
    '@/modules/master-data/location-api': {},
    '@/modules/master-data/sku-api': {
      getSku: async () => {
        details++
        return { id: 101, code: 'SKU101', name: '商品' }
      },
    },
  })
  const options = load('@/modules/master-data/reference-options')
  const labels = await Promise.all(Array.from({ length: 10 }, () => options.resolveReferenceLabel('skus', 101)))
  assert.ok(labels.every((label) => label === 'SKU101 · 商品'))
  assert.equal(pages, 1)
  assert.equal(details, 1)
})

test('a list started before invalidation cannot overwrite current cached names', async () => {
  const pending = deferred()
  const load = moduleLoader({
    '@/modules/master-data/api': { pageMasterData: () => pending.promise },
    '@/modules/master-data/location-api': {},
    '@/modules/master-data/sku-api': {},
  })
  const cache = load(cacheId)
  const request = load('@/modules/master-data/reference-options').searchMasterDataOptions('skus')
  cache.clearReferenceCache()
  cache.setReferenceLabel('skus', 1, 'new')
  pending.resolve(page([{ id: 1, code: 'OLD', name: 'old' }]))
  await request
  assert.equal(cache.getReferenceLabel('skus', 1), 'new')
})
