import test from 'node:test'
import assert from 'node:assert/strict'
import { deferred, flush, mount } from './component-harness.mjs'
import { moduleLoader } from './module-harness.mjs'

test('safety stock saves maximum decimal threshold as text and blocks repeat writes', async () => {
  const pending = deferred(),
    calls = []
  const c = mount(
    'modules/safety-stock/SafetyStockView.vue',
    {},
    {
      './api': {
        pageRules: async () => ({ records: [], total: 0 }),
        pageAlerts: async () => ({ records: [], total: 0 }),
        createRule: (data) => {
          calls.push(data)
          return pending.promise
        },
      },
    },
  )
  c.state.open()
  Object.assign(c.state.form, { warehouseId: 1, locationId: 2, skuId: 3, thresholdQuantity: '999999999999999.9999' })
  const first = c.state.save()
  await c.state.save()
  assert.equal(calls.length, 1)
  assert.equal(calls[0].thresholdQuantity, '999999999999999.9999')
  pending.resolve({})
  await first
  assert.equal(c.state.saving, false)
  assert.equal(c.state.dialog, false)
  c.unmount()
})

test('reconciliation rejects stale responses after the selected dimension changes', async () => {
  const pending = deferred()
  const c = mount(
    'modules/reconciliation/ReconciliationView.vue',
    {},
    { './api': { reconcile: () => pending.promise } },
  )
  Object.assign(c.state.query, { warehouseId: 1, locationId: 2, skuId: 3 })
  await flush()
  const running = c.state.check()
  c.state.query.skuId = 4
  await flush()
  pending.resolve({ issues: [], skuId: 3 })
  await running
  assert.equal(c.state.result, null)
  assert.equal(c.state.loading, false)
  c.unmount()
})

test('a failed monitoring request clears the old snapshot instead of showing zero backlog', async () => {
  let calls = 0
  const c = mount(
    'modules/operations/OperationsStatusView.vue',
    {},
    {
      './api': {
        getOperationsStatus: async () => {
          if (++calls === 1) return { backlog: { pendingFailures: 2 } }
          throw new Error('offline')
        },
      },
    },
  )
  await flush()
  assert.equal(c.state.status.backlog.pendingFailures, 2)
  await c.state.load()
  assert.equal(c.state.status, null)
  assert.equal(c.state.loading, false)
  c.unmount()
})

test('message retries send the original occurrence and require an operator note only once', async () => {
  const prompt = deferred(),
    calls = []
  const c = mount(
    'modules/operations/MessageFailuresView.vue',
    {},
    {
      './api': {
        pageFailures: async () => ({ records: [], total: 0 }),
        handleFailure: async (...args) => {
          calls.push(args)
          return { failure: { status: 'RETRY_REQUESTED' } }
        },
      },
      'element-plus': { ElMessage: { success() {} }, ElMessageBox: { prompt: () => prompt.promise } },
    },
  )
  const row = { id: 8, occurrenceCount: 3 },
    first = c.state.act(row, 'retry')
  await c.state.act(row, 'retry')
  assert.equal(calls.length, 0)
  prompt.resolve({ value: '  已修复连接  ' })
  await first
  assert.deepEqual(calls[0], [8, 'retry', 3, '已修复连接'])
  assert.equal(calls.length, 1)
  assert.equal(c.state.acting, undefined)
  c.unmount()
})

test('count return uses approval permission and preserves a versioned reason', async () => {
  const calls = []
  const load = moduleLoader({
    '@/modules/auth/store': { useAuthStore: () => ({ can: (authority) => authority === 'INVENTORY_COUNT_APPROVE' }) },
    'element-plus': { ElMessage: { success() {} }, ElMessageBox: { prompt: async () => ({ value: '  复核实盘  ' }) } },
    '@/modules/inventory-count/api': { rejectCount: async (...args) => calls.push(args) },
  })
  const actions = load('@/modules/inventory-count/useInventoryCountActions').useInventoryCountActions(async () => {})
  const row = { id: 3, version: 7, status: 'SUBMITTED' },
    action = actions.actions(row).find((entry) => entry.key === 'reject')
  assert.ok(action)
  await actions.execute(row, action)
  assert.deepEqual(calls[0], [3, 7, '复核实盘'])
  assert.equal(actions.actions({ ...row, status: 'ADJUSTED' }).length, 0)
})
