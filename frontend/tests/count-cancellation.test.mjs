import test from 'node:test'
import assert from 'node:assert/strict'
import { moduleLoader } from './module-harness.mjs'
import { deferred } from './component-harness.mjs'

test('count cancellation requires a reason and issues one versioned request despite repeated clicks', async () => {
  const prompt = deferred(),
    calls = []
  let validator,
    reloads = 0
  const load = moduleLoader({
    '@/modules/auth/store': { useAuthStore: () => ({ can: () => true }) },
    'element-plus': {
      ElMessage: { success() {} },
      ElMessageBox: {
        prompt: (message, title, options) => {
          validator = options.inputValidator
          return prompt.promise
        },
      },
    },
    '@/modules/inventory-count/api': { cancelCount: async (...args) => calls.push(args) },
  })
  const actions = load('@/modules/inventory-count/useInventoryCountActions').useInventoryCountActions(async () => {
    reloads++
  })
  const count = { id: 1, version: 3, status: 'APPROVED' }
  const action = actions.actions(count).find((item) => item.key === 'cancel')
  const pending = actions.execute(count, action)
  await actions.execute(count, action)
  assert.equal(calls.length, 0)
  assert.equal(validator(' '), '原因不能为空且最多255字')
  assert.equal(validator('x'.repeat(256)), '原因不能为空且最多255字')
  assert.equal(validator('真实短缺'), true)
  prompt.resolve({ value: '  真实短缺  ' })
  await pending
  assert.deepEqual(calls[0], [1, 3, '真实短缺'])
  assert.equal(calls.length, 1)
  assert.equal(reloads, 1)
  assert.equal(actions.acting.value, undefined)
  for (const status of ['ADJUSTED', 'CANCELLED']) assert.equal(actions.actions({ ...count, status }).length, 0)
})

test('audit-only permission does not expose cancellation', () => {
  const load = moduleLoader({
    '@/modules/auth/store': { useAuthStore: () => ({ can: (authority) => authority === 'INVENTORY_COUNT_APPROVE' }) },
    '@/modules/inventory-count/api': {},
  })
  const actions = load('@/modules/inventory-count/useInventoryCountActions').useInventoryCountActions(async () => {})
  assert.equal(
    actions.actions({ status: 'APPROVED' }).some((item) => item.key === 'cancel'),
    false,
  )
})
