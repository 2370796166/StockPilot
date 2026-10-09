import test from 'node:test'
import assert from 'node:assert/strict'
import { mount, deferred, flush } from './component-harness.mjs'

test('natural text supplements a paused task through input instead of creating a new task', async () => {
  const inputs = []
  const component = view({
    submitAgentTask: async () => task('NEEDS_CLARIFICATION', { missingFields: ['warehouse'] }),
    resumeAgentTask: async (...args) => {
      inputs.push(args)
      return task()
    },
  })
  component.state.question = '查询螺栓库存'
  await component.state.send()
  component.state.question = '就在白云仓'
  await component.state.send()
  assert.equal(inputs.length, 1)
  assert.equal(inputs[0][0], 't1')
  assert.equal(inputs[0][6], '就在白云仓')
  assert.deepEqual(JSON.parse(JSON.stringify(inputs[0][3])), {})
  assert.equal(component.state.turns.length, 1)
  assert.equal(component.state.question, '')
  component.unmount()
})

const task = (status = 'COMPLETED', extras = {}) => ({
  id: 't1',
  sessionId: 's1',
  version: 1,
  status,
  progress: '查询完成',
  question: '商品库存',
  conditions: { sku: 'BOLT' },
  results: [],
  answer: { conclusions: [], uncertainties: [], suggestions: [] },
  missingFields: [],
  ...extras,
})
function view(overrides = {}, extraMocks = {}) {
  const calls = []
  const api = {
    createAgentSession: async () => ({ id: 's1' }),
    submitAgentTask: async (...args) => {
      calls.push(args)
      return task()
    },
    getAgentTask: async () => task(),
    cancelAgentTask: async () => task('CANCELLED'),
    clearAgentSession: async () => {},
    resumeAgentTask: async () => task(),
    retryAgentTask: async () => task('COMPLETED', { id: 't2' }),
    getAgentSession: async () => ({ id: 's1', history: [], context: {} }),
    ...overrides,
  }
  const component = mount(
    'modules/ai/AiAgentView.vue',
    {},
    { './api': api, '@/modules/auth/store': { useAuthStore: () => ({ user: { userId: 1 } }) }, ...extraMocks },
  )
  return { ...component, api, calls }
}
test('agent blocks duplicate submissions and keeps successive turns in one session', async () => {
  const pending = deferred(),
    component = view({ submitAgentTask: async () => pending.promise })
  component.state.question = '查询商品库存'
  const first = component.state.send()
  await flush()
  await component.state.send()
  assert.equal(component.state.loading, true)
  pending.resolve(task())
  await first
  assert.equal(component.state.turns.length, 1)
  assert.equal(component.state.sessionId, 's1')
  component.api.submitAgentTask = async () => task('COMPLETED', { id: 't2' })
  component.state.question = '为什么可用量少'
  await component.state.send()
  assert.equal(component.state.turns.length, 2)
  assert.equal(component.state.conditions.sku, 'BOLT')
  component.unmount()
})
test('candidate and missing input resume the original task with its version', async () => {
  const resumes = [],
    component = view({
      submitAgentTask: async () => task('NEEDS_SELECTION'),
      resumeAgentTask: async (...args) => {
        resumes.push(args)
        return task('NEEDS_CLARIFICATION', { version: 2, missingFields: ['warehouse'] })
      },
    })
  component.state.question = '商品库存'
  await component.state.send()
  await component.state.resume({ kind: 'sku', keyword: '商品', id: 7 })
  assert.deepEqual(JSON.parse(JSON.stringify(resumes[0].slice(0, 3))), ['t1', 1, { 'sku:商品': 7 }])
  assert.equal(component.state.turns.length, 1)
  component.state.missing.warehouse = '一号仓'
  await component.state.resume()
  assert.equal(resumes[1][1], 2)
  assert.equal(resumes[1][3].warehouse, '一号仓')
  component.unmount()
})
test('cancel during submission prevents late task from restoring the view and cancels server task', async () => {
  const pending = deferred(),
    cancelled = [],
    component = view({
      submitAgentTask: async () => pending.promise,
      cancelAgentTask: async (id) => {
        cancelled.push(id)
        return task('CANCELLED')
      },
    })
  component.state.question = '库存'
  const send = component.state.send()
  await flush()
  await component.state.cancel()
  pending.resolve(task('RUNNING'))
  await send
  await flush()
  assert.equal(component.state.turns.length, 0)
  assert.equal(component.state.loading, false)
  assert.deepEqual(cancelled, ['t1'])
  component.unmount()
})
test('clear rejects late polling results and retains evidence when a reconnect fails', async () => {
  const pending = deferred(),
    component = view({
      submitAgentTask: async () =>
        task('PARTIAL', { results: [{ tool: 'query_balances', data: { quantity: '900719925474099.1234' } }] }),
      getAgentTask: async () => pending.promise,
    })
  component.state.question = '库存'
  await component.state.send()
  const poll = component.state.reconnect()
  await flush()
  await component.state.clear()
  pending.resolve(task('RUNNING'))
  await poll
  assert.equal(component.state.turns.length, 0)
  assert.equal(component.state.sessionId, '')
  component.unmount()
  const failed = view({
    submitAgentTask: async () => task('PARTIAL', { results: [{ data: { quantity: '900719925474099.1234' } }] }),
    getAgentTask: async () => {
      throw new Error('network')
    },
  })
  failed.state.question = '库存'
  await failed.state.send()
  await failed.state.reconnect()
  assert.equal(failed.state.turns[0].results[0].data.quantity, '900719925474099.1234')
  assert.ok(failed.state.error)
  failed.unmount()
})

test('lost submission response retries the same request ID and passes cancellation signals', async () => {
  const submissions = [],
    component = view({
      submitAgentTask: async (...args) => {
        submissions.push(args)
        if (submissions.length === 1) throw new Error('network')
        return task()
      },
    })
  component.state.question = '商品库存'
  await component.state.send()
  assert.equal(component.state.uncertainSubmission, true)
  await component.state.retrySubmission()
  assert.equal(submissions.length, 2)
  assert.equal(submissions[0][2], submissions[1][2])
  assert.ok(submissions[0][3] instanceof AbortSignal)
  assert.equal(component.state.turns.length, 1)
  component.unmount()
})

test('explicit requery asks the server to restore the confirmed original task', async () => {
  const retries = [],
    component = view({
      submitAgentTask: async () => task('PARTIAL', { version: 4 }),
      retryAgentTask: async (...args) => {
        retries.push(args)
        return task('COMPLETED', { id: 't2' })
      },
    })
  component.state.question = '查询商品库存'
  await component.state.send()
  await component.state.retry()
  assert.equal(retries[0][0], 't1')
  assert.equal(retries[0][1], 4)
  assert.equal(component.state.turns.length, 2)
  component.unmount()
})

test('leaving the page cancels running work and keeps the session for source-page return', async () => {
  const cleared = [],
    cancelled = [],
    component = view({
      submitAgentTask: async () => task('RUNNING'),
      clearAgentSession: async (id) => cleared.push(id),
      cancelAgentTask: async (id) => {
        cancelled.push(id)
        return task('CANCELLED')
      },
    })
  component.state.question = '库存'
  await component.state.send()
  component.unmount()
  await flush()
  assert.deepEqual(cleared, [])
  assert.deepEqual(cancelled, ['t1'])
})

test('restoration network failure keeps the session and prevents a new task until reconnection', async () => {
  const forgotten = [],
    attempts = []
  const component = view(
    {
      getAgentSession: async (id) => {
        attempts.push(id)
        if (attempts.length === 1) throw new Error('network')
        return { id, history: [{ taskId: 't1' }], context: { sku: 'BOLT' } }
      },
    },
    {
      '@/modules/ai/session': {
        loadSession: () => 's1',
        saveSession() {},
        forgetSession: (id) => forgotten.push(id),
      },
    },
  )
  await flush()
  assert.equal(component.state.restorePending, true)
  assert.equal(component.state.sessionId, 's1')
  component.state.question = '新问题'
  await component.state.send()
  assert.equal(component.calls.length, 0)
  assert.equal(component.state.restorePending, false)
  assert.equal(component.state.turns[0].id, 't1')
  assert.deepEqual(forgotten, [])
  component.unmount()
})

test('an expired server session is forgotten without restoring cached facts', async () => {
  const forgotten = []
  const component = view(
    {
      getAgentSession: async () => {
        throw { response: { status: 404 } }
      },
    },
    {
      '@/modules/ai/session': {
        loadSession: () => 'expired',
        saveSession() {},
        forgetSession: (id) => forgotten.push(id),
      },
    },
  )
  await flush()
  assert.deepEqual(forgotten, [1])
  assert.equal(component.state.sessionId, '')
  assert.equal(component.state.turns.length, 0)
  component.unmount()
})
