import test from 'node:test'
import assert from 'node:assert/strict'
import { moduleLoader } from './module-harness.mjs'
import { mount, flush } from './component-harness.mjs'

const { isQuantity, compareQuantities } = moduleLoader()('@/shared/utils/quantity')
test('quantity validation preserves the full DECIMAL range and rejects floating point inputs', () => {
  for (const value of ['0.0001', '1', '999999999999999.9999']) assert.equal(isQuantity(value), true)
  for (const value of ['0', '0.0000']) {
    assert.equal(isQuantity(value), false)
    assert.equal(isQuantity(value, true), true)
  }
  for (const value of [1, NaN, null, '', '1e3', '0001', '-1', '1.00001', '1000000000000000', 'Infinity'])
    assert.equal(isQuantity(value, true), false)
  assert.equal(compareQuantities('99999999999999.9999', '100000000000000.0000'), -1)
  assert.equal(compareQuantities('1', '1.0000'), 0)
  assert.equal(compareQuantities('0.0001', '0'), 1)
})

test('document quantity editing keeps strings without changing the dimension IDs', () => {
  let submitted
  const lines = [{ locationId: 7, skuId: 8, quantity: '99999999999999.9999' }]
  const component = mount('shared/components/DocumentLinesEditor.vue', {
    modelValue: lines,
    'onUpdate:modelValue': (value) => {
      submitted = value
    },
  })
  component.state.updateQuantity(0, '999999999999999.1234')
  assert.equal(submitted[0].quantity, '999999999999999.1234')
  assert.equal(submitted[0].locationId, 7)
  assert.equal(submitted[0].skuId, 8)
  assert.equal(lines[0].quantity, '99999999999999.9999')
  component.unmount()
})

test('transfer edit and submit roundtrip the maximum decimal as text', async () => {
  let submitted
  const detail = {
    id: 1,
    version: 2,
    transferNo: 'T1',
    sourceWarehouseId: 1,
    targetWarehouseId: 2,
    lines: [{ sourceLocationId: 3, targetLocationId: 4, skuId: 5, quantity: '99999999999999.9999' }],
  }
  const component = mount(
    'modules/transfer/components/TransferFormDialog.vue',
    { modelValue: false, editing: detail },
    {
      '@/modules/transfer/api': {
        updateTransfer: async (id, data) => {
          submitted = { id, ...data }
        },
      },
    },
  )
  component.props.modelValue = true
  await flush()
  assert.equal(component.state.form.lines[0].quantity, detail.lines[0].quantity)
  await component.state.save()
  assert.equal(submitted.lines[0].quantity, '99999999999999.9999')
  component.unmount()
})

test('count results accept zero and preserve four decimal places on submission', async () => {
  let submitted
  const detail = {
    id: 1,
    version: 2,
    lines: [
      { id: 3, countedQuantity: '999999999999999.1234', snapshotFrozenQuantity: '0.0000', reason: '核对' },
      { id: 4, countedQuantity: '0.0000', snapshotFrozenQuantity: '0.0000', reason: '盘亏' },
    ],
  }
  const component = mount(
    'modules/inventory-count/components/InventoryCountResultsDialog.vue',
    { modelValue: false, detail },
    {
      '@/modules/inventory-count/api': {
        recordCountResults: async (id, version, results) => {
          submitted = results
          return detail
        },
      },
    },
  )
  component.props.modelValue = true
  await flush()
  await component.state.save()
  assert.equal(submitted[0].countedQuantity, '999999999999999.1234')
  assert.equal(submitted[1].countedQuantity, '0.0000')
  component.unmount()
})
