import test from 'node:test'
import assert from 'node:assert/strict'
import { deferred, flush, mount } from './component-harness.mjs'

const source = '@/modules/master-data/reference-options'
const locationFile = 'shared/components/RemoteLocationSelect.vue'
const item = { id: 99, warehouseId: 1, code: 'L99', name: 'Selected', status: 'ENABLED' }

test('clearing a warehouse invalidates its pending location response and loading state', async () => {
  const old = deferred()
  const c = mount(
    locationFile,
    { warehouseId: 1 },
    { [source]: { searchLocationOptions: () => old.promise }, '@/modules/master-data/location-api': {} },
  )
  c.props.warehouseId = undefined
  await flush()
  assert.equal(c.state.loading, false)
  old.resolve({ records: [item] })
  await flush()
  assert.equal(c.state.options.length, 0)
  c.unmount()
})

test('new search intent invalidates an old response before the debounce fires', async (t) => {
  t.mock.timers.enable({ apis: ['setTimeout'] })
  const old = deferred()
  const c = mount(
    locationFile,
    { warehouseId: 1 },
    { [source]: { searchLocationOptions: () => old.promise }, '@/modules/master-data/location-api': {} },
  )
  c.state.search('new')
  old.resolve({ records: [item] })
  await flush()
  assert.equal(c.state.options.length, 0)
  c.unmount()
})

test('selected location outside the first page is hydrated without losing its ID', async () => {
  const c = mount(
    locationFile,
    { warehouseId: 1, modelValue: 99 },
    {
      [source]: {
        searchLocationOptions: async () => ({ records: [] }),
        resolveReferenceLabel: async () => 'L99 · Selected',
      },
      '@/modules/master-data/location-api': { getLocation: async () => item },
    },
  )
  await flush()
  assert.equal(
    c.state.options.some((x) => x.id === 99),
    true,
  )
  c.unmount()
})

test('selected master data outside the first page is hydrated', async () => {
  const c = mount(
    'shared/components/RemoteMasterDataSelect.vue',
    { resource: 'warehouses', modelValue: 99 },
    {
      [source]: {
        searchMasterDataOptions: async () => ({ records: [] }),
        resolveReferenceLabel: async () => 'W99 · Selected',
      },
      '@/modules/master-data/api': { getMasterData: async () => item },
    },
  )
  await flush()
  assert.equal(
    c.state.options.some((x) => x.id === 99),
    true,
  )
  c.unmount()
})

test('newer list search results cannot be overwritten by a slower old response', async () => {
  const old = deferred(),
    latest = deferred()
  let calls = 0
  const c = mount(
    'shared/components/StandardMasterDataPage.vue',
    { resource: 'warehouses', title: '仓库', noun: '仓库' },
    {
      '@/modules/master-data/api': { pageMasterData: () => (++calls === 1 ? old.promise : latest.promise) },
    },
  )
  const pending = c.state.load()
  latest.resolve({ records: [{ id: 2 }], total: 1 })
  await pending
  old.resolve({ records: [{ id: 1 }], total: 7 })
  await flush()
  assert.equal(c.state.records[0].id, 2)
  assert.equal(c.state.total, 1)
  c.unmount()
})
