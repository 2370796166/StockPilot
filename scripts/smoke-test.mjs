// Real HTTP acceptance through the frontend API modules. Use a dedicated disposable database.
import assert from 'node:assert/strict'
import { randomUUID } from 'node:crypto'
import { setTimeout as delay } from 'node:timers/promises'
import { moduleLoader } from '../frontend/tests/module-harness.mjs'

const base = new URL(process.env.STOCKPILOT_SMOKE_URL ?? 'http://127.0.0.1:18085/api')
assert.ok(['http:', 'https:'].includes(base.protocol), 'Smoke tests require HTTP or HTTPS')
assert.ok(['localhost', '127.0.0.1', '[::1]'].includes(base.hostname), 'Smoke tests require a loopback API')
assert.equal(base.pathname, '/api', 'STOCKPILOT_SMOKE_URL must end in /api')
assert.equal(base.username + base.password + base.search + base.hash, '', 'Use separate credentials')
assert.equal(
  process.env.STOCKPILOT_SMOKE_ALLOW_WRITES,
  'true',
  'Explicitly enable writes to the isolated test database',
)
const username = process.env.STOCKPILOT_SMOKE_USERNAME
const password = process.env.STOCKPILOT_SMOKE_PASSWORD
assert.ok(username && password, 'Set STOCKPILOT_SMOKE_USERNAME and STOCKPILOT_SMOKE_PASSWORD')
const prefix = 'SMOKE_' + randomUUID().replaceAll('-', '').slice(0, 12)
let token
let httpChecks = 0

async function api(method, path, data, expected = 200, auth = token) {
  const response = await fetch(base.href + path, {
    method,
    signal: AbortSignal.timeout(15000),
    headers: { 'Content-Type': 'application/json', ...(auth ? { Authorization: 'Bearer ' + auth } : {}) },
    body: data === undefined ? undefined : JSON.stringify(data),
  })
  const result = await response.json()
  assert.equal(response.status, expected, `${method} ${path}: ${result.code}`)
  if (expected === 200) assert.equal(result.code, 'SUCCESS')
  httpChecks++
  return result.data
}

async function waitForHealth() {
  const deadline = Date.now() + 90000
  while (Date.now() < deadline) {
    try {
      const response = await fetch(base.href + '/health', { signal: AbortSignal.timeout(2000) })
      const result = await response.json()
      if (response.ok && result.data?.status === 'UP' && result.data?.database === 'UP') return
    } catch {
      /* The packaged application may still be starting. */
    }
    await delay(1000)
  }
  throw new Error('Application and database did not become healthy within 90 seconds')
}

function scaled(value) {
  assert.equal(typeof value, 'string', 'Quantities must retain the decimal string contract')
  assert.match(value, /^-?\d+(\.\d{1,4})?$/)
  const negative = value.startsWith('-')
  const [whole, fraction = ''] = value.replace(/^-/, '').split('.')
  const magnitude = BigInt(whole) * 10000n + BigInt(fraction.padEnd(4, '0'))
  return negative ? -magnitude : magnitude
}

await waitForHealth()
await api('GET', '/inventory/balances', undefined, 401, null)
token = (await api('POST', '/auth/login', { username, password })).accessToken
const request = ({ method, url, data }) => api(method, url, data)
const load = moduleLoader({ '@/shared/utils/request': { request } })
const documents = load('@/modules/documents/api')
const transfers = load('@/modules/transfer/api')
const counts = load('@/modules/inventory-count/api')
const warehouse = await api('POST', '/master-data/warehouses', { code: prefix + '_A', name: 'HTTP验收源仓' })
const target = await api('POST', '/master-data/warehouses', { code: prefix + '_B', name: 'HTTP验收目标仓' })
const location = await api('POST', '/master-data/locations', {
  code: prefix + '_LA',
  name: '源仓库位',
  warehouseId: warehouse.id,
})
const targetLocation = await api('POST', '/master-data/locations', {
  code: prefix + '_LB',
  name: '目标仓库位',
  warehouseId: target.id,
})
const sku = await api('POST', '/master-data/skus', { code: prefix + '_SKU', name: '合成验收零件', unit: '件' })
const line = { locationId: location.id, skuId: sku.id, quantity: '1.0000' }
const maximum = '999999999999999.4321'

// Reading details introduces id/lineNo. The real frontend serializers must remove them on writes.
for (const kind of ['purchase', 'sales']) {
  let draft = await documents.createDocument(kind, {
    no: prefix + '_PRECISION_' + kind,
    warehouseId: warehouse.id,
    remark: '',
    lines: [line],
  })
  draft = await documents.getDocument(kind, draft.id)
  assert.ok(draft.lines[0].id)
  draft = await documents.updateDocument(kind, draft.id, {
    version: draft.version,
    warehouseId: warehouse.id,
    remark: '精度验收',
    lines: draft.lines.map((item) => ({ ...item, quantity: maximum })),
  })
  assert.equal(draft.lines[0].quantity, maximum)
}

async function balance(warehouseId, actual, available, frozen) {
  const page = await api('GET', `/inventory/balances?warehouseId=${warehouseId}&skuId=${sku.id}&size=100`)
  assert.equal(page.total, 1)
  const result = page.records[0]
  for (const [name, value] of Object.entries({
    actualQuantity: actual,
    availableQuantity: available,
    frozenQuantity: frozen,
  })) {
    assert.equal(scaled(result[name]), scaled(value), name)
  }
  assert.equal(scaled(result.actualQuantity), scaled(result.availableQuantity) + scaled(result.frozenQuantity))
  return result
}

let purchase = await documents.createDocument('purchase', {
  no: prefix + '_PURCHASE',
  warehouseId: warehouse.id,
  remark: '',
  lines: [{ ...line, quantity: '100.1234' }],
})
for (const action of ['submit', 'approve', 'complete']) {
  purchase = await documents.transitionDocument(
    'purchase',
    purchase.id,
    action,
    action === 'complete' ? undefined : purchase.version,
  )
}
await balance(warehouse.id, '100.1234', '100.1234', '0')
await api('POST', `/inbound/purchase-receipts/${purchase.id}/complete`, undefined, 409)
await balance(warehouse.id, '100.1234', '100.1234', '0')

let sale = await documents.createDocument('sales', {
  no: prefix + '_CANCEL',
  warehouseId: warehouse.id,
  remark: '',
  lines: [{ ...line, quantity: '0.1234' }],
})
sale = await documents.transitionDocument('sales', sale.id, 'reserve', sale.version)
await balance(warehouse.id, '100.1234', '100', '0.1234')
sale = await documents.transitionDocument('sales', sale.id, 'cancel')
assert.equal(sale.status, 'CANCELLED')
await balance(warehouse.id, '100.1234', '100.1234', '0')

let transfer = await transfers.createTransfer({
  transferNo: prefix + '_TRANSFER',
  sourceWarehouseId: warehouse.id,
  targetWarehouseId: target.id,
  remark: '',
  lines: [{ sourceLocationId: location.id, targetLocationId: targetLocation.id, skuId: sku.id, quantity: '1.0000' }],
})
transfer = await transfers.getTransfer(transfer.id)
assert.ok(transfer.lines[0].id)
const edit = (quantity) =>
  transfers.updateTransfer(transfer.id, {
    version: transfer.version,
    sourceWarehouseId: warehouse.id,
    targetWarehouseId: target.id,
    remark: '',
    lines: transfer.lines.map((item) => ({ ...item, quantity })),
  })
transfer = await edit(maximum)
assert.equal(transfer.lines[0].quantity, maximum)
transfer = await edit('2.4321')
for (const action of ['submit', 'approve', 'dispatch', 'start-transit', 'receive']) {
  transfer = await transfers.transitionTransfer(
    transfer.id,
    action,
    ['dispatch', 'receive'].includes(action) ? undefined : transfer.version,
  )
}
assert.equal(transfer.status, 'COMPLETED')
await api('POST', `/transfers/${transfer.id}/receive`, undefined, 409)
await balance(warehouse.id, '97.6913', '97.6913', '0')
await balance(target.id, '2.4321', '2.4321', '0')

let count = await counts.createCount({
  countNo: prefix + '_COUNT',
  warehouseId: warehouse.id,
  remark: '',
  dimensions: [{ locationId: location.id, skuId: sku.id }],
})
count = await counts.transitionCount(count.id, 'start', count.version)
count = await counts.recordCountResults(count.id, count.version, [
  { lineId: count.lines[0].id, countedQuantity: '98.0000', reason: '合成盘盈验收' },
])
for (const action of ['submit', 'approve', 'adjust']) {
  count = await counts.transitionCount(count.id, action, action === 'adjust' ? undefined : count.version)
}
assert.equal(count.status, 'ADJUSTED')
const finalSource = await balance(warehouse.id, '98', '98', '0')
const finalTarget = await balance(target.id, '2.4321', '2.4321', '0')

let ledgerChecks = 0
for (const finalBalance of [finalSource, finalTarget]) {
  const page = await api(
    'GET',
    `/inventory/ledgers?warehouseId=${finalBalance.warehouseId}&locationId=${finalBalance.locationId}&skuId=${sku.id}&size=100`,
  )
  assert.equal(page.total, page.records.length, 'The ledger audit must include every matching row')
  assert.ok(page.total > 0)
  const rows = [...page.records].sort((a, b) => a.balanceVersionAfter - b.balanceVersionAfter)
  let quantities = { Actual: 0n, Available: 0n, Frozen: 0n }
  let version = 0
  for (const row of rows) {
    assert.equal(row.balanceVersionBefore, version)
    for (const name of ['Actual', 'Available', 'Frozen']) {
      assert.equal(scaled(row[`before${name}Quantity`]), quantities[name])
      quantities[name] += scaled(row[`change${name}Quantity`])
      assert.equal(scaled(row[`after${name}Quantity`]), quantities[name])
    }
    version = row.balanceVersionAfter
    ledgerChecks++
  }
  assert.equal(version, finalBalance.version)
  for (const name of ['Actual', 'Available', 'Frozen']) {
    assert.equal(quantities[name], scaled(finalBalance[name.toLowerCase() + 'Quantity']))
  }
}
console.log(
  JSON.stringify(
    {
      status: 'PASSED',
      httpChecks,
      ledgerChecks,
      fixturePrefix: prefix,
      scenarios: [
        'strict draft edits and decimal precision',
        'purchase once',
        'sales cancellation',
        'transfer receipt once',
        'count adjustment',
        'complete ledger chain',
      ],
    },
    null,
    2,
  ),
)
