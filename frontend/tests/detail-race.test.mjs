import test from 'node:test'
import assert from 'node:assert/strict'
import { deferred, mount } from './component-harness.mjs'

const cases = [
  [
    'modules/documents/views/OrderDocumentView.vue',
    '@/modules/documents/api',
    'pageDocuments',
    'getDocument',
    { kind: 'purchase' },
    'openDetail',
    'detail',
  ],
  [
    'modules/documents/views/OrderDocumentView.vue',
    '@/modules/documents/api',
    'pageDocuments',
    'getDocument',
    { kind: 'sales' },
    'openEdit',
    'editing',
  ],
  ['modules/transfer/TransferView.vue', '@/modules/transfer/api', 'pageTransfers', 'getTransfer', {}, 'show', 'detail'],
  [
    'modules/transfer/TransferView.vue',
    '@/modules/transfer/api',
    'pageTransfers',
    'getTransfer',
    {},
    'edit',
    'editing',
  ],
  [
    'modules/inventory-count/InventoryCountView.vue',
    '@/modules/inventory-count/api',
    'pageCounts',
    'getCount',
    {},
    'show',
    'detail',
  ],
  [
    'modules/inventory-count/InventoryCountView.vue',
    '@/modules/inventory-count/api',
    'pageCounts',
    'getCount',
    {},
    'recordResults',
    'detail',
  ],
]

for (const [file, api, page, get, props, open, field] of cases) {
  test(`${file} ${open}: a slower previous row cannot replace the selected document`, async () => {
    const old = deferred(),
      latest = deferred()
    const pending = [old, latest]
    const c = mount(file, props, {
      [api]: { [page]: async () => ({ records: [], total: 0 }), [get]: () => pending.shift().promise },
      '@/modules/auth/store': { useAuthStore: () => ({ can: () => true }) },
      '@/modules/transfer/useTransferActions': { useTransferActions: () => ({}) },
      '@/modules/inventory-count/useInventoryCountActions': { useInventoryCountActions: () => ({}) },
    })
    try {
      const first = c.state[open]({ id: 1 }),
        second = c.state[open]({ id: 2 })
      latest.resolve({ id: 2, no: 'LATEST', warehouseId: 2, lines: [] })
      await second
      old.resolve({ id: 1, no: 'OLD', warehouseId: 1, lines: [] })
      await first
      assert.equal(c.state[field].id, 2)
      if (open === 'openEdit') assert.equal(c.state.form.warehouseId, 2)
    } finally {
      c.unmount()
    }
  })
}
