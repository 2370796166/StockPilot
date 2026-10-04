import test from 'node:test'
import assert from 'node:assert/strict'
import { deferred, flush, mount } from './component-harness.mjs'

for (const [file, api, page, transition] of [
  ['modules/transfer/TransferView.vue', '@/modules/transfer/api', 'pageTransfers', 'transitionTransfer'],
  ['modules/inventory-count/InventoryCountView.vue', '@/modules/inventory-count/api', 'pageCounts', 'transitionCount'],
]) {
  test(`${file}: one confirmation and one request despite repeated clicks`, async () => {
    const confirmation = deferred(),
      request = deferred()
    let prompts = 0,
      calls = 0
    const c = mount(
      file,
      {},
      {
        [api]: {
          [page]: async () => ({ records: [], total: 0 }),
          [transition]: () => {
            calls++
            return request.promise
          },
        },
        '@/modules/auth/store': { useAuthStore: () => ({ can: () => true }) },
        'element-plus': {
          ElMessage: { success() {} },
          ElMessageBox: {
            confirm: () => {
              prompts++
              return confirmation.promise
            },
          },
        },
      },
    )
    await flush()
    const row = { id: 1, version: 0, status: 'DRAFT' }
    const action = { key: 'submit', label: '提交', requiresVersion: true }
    const first = c.state.execute(row, action)
    const duplicate = c.state.execute(row, action)
    assert.equal(prompts, 1)
    confirmation.resolve()
    await flush()
    assert.equal(calls, 1)
    request.resolve()
    await Promise.all([first, duplicate])
    assert.equal(c.state.acting, undefined)
    c.unmount()
  })
  test(`${file}: cancellation and HTTP failure release the action guard`, async () => {
    let cancel = true,
      calls = 0
    const c = mount(
      file,
      {},
      {
        [api]: {
          [page]: async () => ({ records: [], total: 0 }),
          [transition]: async () => {
            calls++
            throw new Error('HTTP failure')
          },
        },
        '@/modules/auth/store': { useAuthStore: () => ({ can: () => true }) },
        'element-plus': {
          ElMessage: { success() {} },
          ElMessageBox: {
            confirm: async () => {
              if (cancel) throw 'cancel'
            },
          },
        },
      },
    )
    const row = { id: 1, version: 0, status: 'DRAFT' },
      action = { key: 'submit', label: '提交', requiresVersion: true }
    await c.state.execute(row, action)
    assert.equal(calls, 0)
    assert.equal(c.state.acting, undefined)
    cancel = false
    await c.state.execute(row, action)
    assert.equal(calls, 1)
    assert.equal(c.state.acting, undefined)
    c.unmount()
  })
}
