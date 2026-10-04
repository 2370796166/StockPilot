import test from 'node:test'
import assert from 'node:assert/strict'
import { deferred, flush, mount } from './component-harness.mjs'

const cases = [
  ['modules/master-data/views/LocationView.vue', '@/modules/master-data/location-api', 'pageLocations'],
  ['modules/master-data/views/SkuView.vue', '@/modules/master-data/sku-api', 'pageSkus'],
  ['modules/documents/views/OrderDocumentView.vue', '@/modules/documents/api', 'pageDocuments', { kind: 'purchase' }],
  ['modules/transfer/TransferView.vue', '@/modules/transfer/api', 'pageTransfers'],
  ['modules/inventory-count/InventoryCountView.vue', '@/modules/inventory-count/api', 'pageCounts'],
  ['modules/inventory/views/BalanceView.vue', '@/modules/inventory/api', 'pageBalances'],
  ['modules/inventory/views/LedgerView.vue', '@/modules/inventory/api', 'pageLedgers'],
  ['modules/security/views/UserView.vue', '@/modules/security/api', 'pageUsers'],
  ['modules/security/views/RoleView.vue', '@/modules/security/api', 'listRoles', {}, 'roles'],
]
for (const [file, api, method, props = {}, field = 'records'] of cases) {
  test(`${file}: last response wins and old completion cannot clear active loading`, async () => {
    const first = deferred(),
      second = deferred(),
      third = deferred()
    const pending = [first, second, third]
    const c = mount(file, props, {
      [api]: { [method]: () => pending.shift().promise },
      '@/modules/auth/store': { useAuthStore: () => ({ can: () => true }) },
      '@/modules/transfer/useTransferActions': { useTransferActions: () => ({}) },
      '@/modules/inventory-count/useInventoryCountActions': { useInventoryCountActions: () => ({}) },
    })
    const page = (id) => (field === 'roles' ? [{ id }] : { records: [{ id }], total: id })
    const latest = c.state.load()
    first.resolve(page(1))
    await flush()
    assert.equal(c.state.loading, true)
    second.resolve(page(2))
    await latest
    assert.equal(c.state[field][0].id, 2)
    const final = c.state.load()
    third.reject(new Error('simulated API error already shown by interceptor'))
    await final
    assert.equal(c.state.loading, false)
    c.unmount()
  })
}

test('EntityRef cannot display a previous row label after its ID changes', async () => {
  const first = deferred(),
    second = deferred()
  const c = mount(
    'shared/components/EntityRef.vue',
    { resource: 'skus', id: 1 },
    {
      '@/modules/master-data/reference-options': {
        resolveReferenceLabel: (_, id) => (id === 1 ? first.promise : second.promise),
      },
    },
  )
  c.props.id = 2
  await flush()
  second.resolve('SKU-2')
  await flush()
  first.resolve('SKU-1')
  await flush()
  assert.equal(c.state.label, 'SKU-2')
  c.unmount()
})
