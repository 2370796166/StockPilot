import test from 'node:test'
import assert from 'node:assert/strict'
import { moduleLoader } from './module-harness.mjs'

for (const kind of ['purchase', 'sales', 'transfer']) {
  test(`${kind} draft edits send only writable line fields and preserve decimal strings`, async () => {
    const allowed =
      kind === 'transfer'
        ? ['sourceLocationId', 'targetLocationId', 'skuId', 'quantity']
        : ['locationId', 'skuId', 'quantity']
    const line =
      kind === 'transfer'
        ? { id: 71, lineNo: 1, sourceLocationId: 2, targetLocationId: 3, skuId: 4, quantity: '999999999999999.4321' }
        : { id: 71, lineNo: 1, locationId: 2, skuId: 4, quantity: '999999999999999.4321' }
    const requests = []
    const load = moduleLoader({
      '@/shared/utils/request': {
        request: async (config) => {
          const body = JSON.parse(JSON.stringify(config.data))
          // Model the strict request contract: a detail VO is not a valid write DTO.
          assert.deepEqual(Object.keys(body.lines[0]).sort(), [...allowed].sort())
          assert.equal(body.lines[0].quantity, '999999999999999.4321')
          requests.push({ ...config, data: body })
          return { id: 9, receiptNo: 'PO_9', outboundNo: 'SO_9', transferNo: 'TR_9' }
        },
      },
    })
    if (kind === 'transfer') {
      const api = load('@/modules/transfer/api')
      const data = { sourceWarehouseId: 1, targetWarehouseId: 2, remark: '', lines: [line] }
      await api.updateTransfer(9, { version: 3, ...data })
      await api.createTransfer({ transferNo: 'TR_9', ...data })
    } else {
      const api = load('@/modules/documents/api')
      const data = { warehouseId: 1, remark: '', lines: [line] }
      await api.updateDocument(kind, 9, { version: 3, ...data })
      await api.createDocument(kind, { no: kind === 'purchase' ? 'PO_9' : 'SO_9', ...data })
    }
    assert.equal(requests[0].method, 'PUT')
    assert.equal(requests[0].data.version, 3)
    assert.equal(requests[1].method, 'POST')
    assert.equal(line.id, 71, 'serialization must preserve the displayed detail record')
  })
}
