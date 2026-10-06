import test from 'node:test'
import assert from 'node:assert/strict'
import { mount, deferred, flush } from './component-harness.mjs'
import fs from 'node:fs'
import vm from 'node:vm'
import ts from 'typescript'

function view(askAssistant) {
  return mount('modules/ai/AiAssistantView.vue', {}, { '@/modules/ai/api': { askAssistant } })
}
const answer = (status = 'OK') => ({ status, answer: '可核对下方结果', results: [], queriedAt: 'now' })
test('assistant prevents duplicate sends and rejects blank or oversized input', async () => {
  const pending = deferred(),
    calls = []
  const component = view((question, selections) => {
    calls.push([question, selections])
    return pending.promise
  })
  await component.state.send()
  component.state.question = 'x'.repeat(1001)
  await component.state.send()
  assert.equal(calls.length, 0)
  component.state.question = '查询商品库存'
  const first = component.state.send()
  assert.equal(component.state.loading, true)
  await component.state.send()
  assert.equal(calls.length, 1)
  pending.resolve(answer())
  await first
  assert.equal(component.state.loading, false)
  assert.equal(component.state.response.status, 'OK')
  component.unmount()
})
test('candidate selection retains original question and accumulates choices', async () => {
  const calls = [],
    component = view(async (question, selections) => {
      calls.push(JSON.parse(JSON.stringify({ question, selections })))
      return answer('NEEDS_SELECTION')
    })
  component.state.question = 'A在一号仓的库存'
  await component.state.send()
  await component.state.send({ kind: 'sku', keyword: 'A', id: 1, name: 'A', code: 'A1' })
  await component.state.send({ kind: 'warehouse', keyword: '一号仓', id: 2, name: '一号仓', code: 'W1' })
  assert.equal(calls[2].question, 'A在一号仓的库存')
  assert.equal(calls[2].selections.length, 2)
  component.state.question = '新的问题'
  await component.state.send({ kind: 'sku', keyword: 'A', id: 3 })
  assert.equal(calls.length, 3)
  await component.state.send()
  assert.equal(calls[3].selections.length, 0)
  component.unmount()
})
test('disabled, configuration, permission, empty data and failures retain distinct states', async () => {
  for (const status of ['DISABLED', 'CONFIGURATION_ERROR', 'FORBIDDEN', 'NO_DATA', 'QUERY_FAILED', 'MODEL_TIMEOUT']) {
    const component = view(async () => answer(status))
    component.state.question = '查询库存'
    await component.state.send()
    assert.equal(component.state.response.status, status)
    assert.equal(component.state.error, '')
    assert.ok(component.state.statuses[status])
    component.unmount()
  }
})
test('HTTP errors and late responses cannot populate an unmounted assistant', async () => {
  const component = view(async () => {
    throw { response: { status: 403 } }
  })
  component.state.question = '查询'
  await component.state.send()
  assert.match(component.state.error, /无权访问/)
  assert.equal(component.state.loading, false)
  component.unmount()
  const pending = deferred(),
    late = view(() => pending.promise)
  late.state.question = '查询'
  const request = late.state.send()
  late.unmount()
  pending.resolve(answer())
  await request
  assert.equal(late.state.response, null)
})
test('evidence preserves decimal precision, pagination and limits source destinations', () => {
  const evidence = {
    tool: 'query_balances',
    candidates: [],
    sources: [],
    data: {
      warehouses: { page: 2, size: 20, total: 44, returned: 1, records: [{ actualQuantity: '999999999999999.1234' }] },
    },
  }
  const component = mount(
    'modules/ai/AiEvidence.vue',
    { evidence },
    { '@/modules/auth/store': { useAuthStore: () => ({ can: () => false }) } },
  )
  assert.equal(component.state.display(evidence.data.warehouses.records[0], 'actualQuantity'), '999999999999999.1234')
  assert.equal(component.state.tables[0].page.page, 2)
  assert.equal(component.state.tables[0].page.total, 44)
  assert.equal(component.state.allowedSource('https://malicious.example'), false)
  assert.equal(component.state.allowedSource('/security/users'), false)
  assert.equal(component.state.allowedSource('/documents/transfers?businessNo=TR1'), true)
  component.unmount()
})

test('cancelling assistant aborts its HTTP signal and clears loading without an error', async () => {
  let signal
  const component = view((question, selections, inputSignal) => {
    signal = inputSignal
    return new Promise((resolve, reject) => signal.addEventListener('abort', () => reject(new Error('cancelled'))))
  })
  component.state.question = '查询库存'
  const pending = component.state.send()
  assert.equal(component.state.loading, true)
  component.state.cancel()
  await pending
  assert.equal(signal.aborted, true)
  assert.equal(component.state.loading, false)
  assert.equal(component.state.error, '')
  assert.equal(component.state.response, null)
  component.unmount()
})

test('unmount aborts a pending assistant request', async () => {
  let signal
  const pending = deferred()
  const component = view((question, selections, inputSignal) => {
    signal = inputSignal
    return pending.promise
  })
  component.state.question = '查询库存'
  const send = component.state.send()
  component.unmount()
  assert.equal(signal.aborted, true)
  pending.resolve(answer())
  await send
  assert.equal(component.state.response, null)
})

test('period and frozen results keep complete totals separate from paginated source details', async () => {
  const evidence = {
    tool: 'query_frozen_sources',
    candidates: [],
    sources: [],
    data: {
      sku: { id: 1, name: 'A', code: 'SKU1' },
      references: { 'sku:1': { unit: '件' } },
      frozenTotals: {
        sourceQuantity: '999999999999999.1234',
        frozenQuantity: '999999999999999.1234',
        differenceQuantity: '0.0000',
      },
      salesSources: {
        page: 2,
        size: 1,
        total: 45,
        returned: 1,
        records: [{ documentType: 'SALES', status: 'APPROVED', quantity: '0.0001' }],
      },
      transferSources: { page: 2, size: 1, total: 10, returned: 0, records: [] },
      totalCheck: 'TOTAL_MATCH',
    },
  }
  const component = mount(
    'modules/ai/AiEvidence.vue',
    { evidence },
    { '@/modules/auth/store': { useAuthStore: () => ({ can: () => true }) } },
  )
  assert.equal(component.state.tables.length, 3)
  assert.equal(component.state.tables[0].page.total, 45)
  assert.equal(component.state.tables[2].rows[0].sourceQuantity, '999999999999999.1234')
  assert.equal(component.state.display(evidence.data.salesSources.records[0], 'status'), '已审核')
  assert.equal(component.state.display(evidence.data.salesSources.records[0], 'documentType'), '销售出库')
  assert.equal(component.state.skuUnit, '件')
  component.props.evidence = {
    ...evidence,
    tool: 'summarize_movements',
    data: {
      period: { startDate: '2026-10-01', endDate: '2026-10-03', timezone: 'Asia/Shanghai' },
      summary: { ledgerCount: 45, changeActualQuantity: '-999999999999999.1234' },
      movements: [{ businessType: 'TRANSFER_OUT', meaning: '调拨调出', changeActualQuantity: '-0.0001' }],
    },
  }
  await flush()
  assert.equal(component.state.tables.length, 2)
  assert.equal(component.state.tables[0].rows[0].ledgerCount, 45)
  assert.equal(component.state.display(component.state.tables[1].rows[0], 'businessType'), '调拨调出')
  component.unmount()
})

test('source date filters reject malformed, unpaired, reversed and overlong ranges', () => {
  const source = fs.readFileSync(new URL('../src/shared/utils/source-filters.ts', import.meta.url), 'utf8')
  const code = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText
  const module = { exports: {} }
  vm.runInNewContext(code, { module, exports: module.exports, URLSearchParams })
  const filters = module.exports.sourceFilters
  assert.equal(filters('?startDate=2026-10-01&endDate=2026-10-03&skuId=1').startDate, '2026-10-01')
  for (const query of [
    '?startDate=2026-02-30&endDate=2026-03-01',
    '?startDate=2026-10-01',
    '?startDate=2026-10-03&endDate=2026-10-01',
    '?startDate=2026-01-01&endDate=2026-04-03',
    '?startDate=0001-01-01&endDate=0001-01-02',
    '?startDate=not-a-date&endDate=2026-01-02',
  ]) {
    assert.equal(filters(query).startDate, undefined)
    assert.equal(filters(query).endDate, undefined)
  }
  assert.equal(filters('?startDate=2026-01-01&endDate=2026-04-02').endDate, '2026-04-02')
})
