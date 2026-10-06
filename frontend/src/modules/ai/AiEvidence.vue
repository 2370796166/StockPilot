<script setup lang="ts">
import { ElButton, ElCard, ElTable, ElTableColumn } from 'element-plus'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/card/style/css'
import 'element-plus/es/components/table/style/css'
import 'element-plus/es/components/table-column/style/css'
import type { Candidate, Evidence, QueryPage } from './types'
import { computed } from 'vue'
import { useAuthStore } from '@/modules/auth/store'
const props = defineProps<{ evidence: Evidence }>()
const emit = defineEmits<{ select: [candidate: Evidence['candidates'][number]] }>()
const auth = useAuthStore()
const skuContext = computed(() => props.evidence.data.sku as Candidate | undefined)
const skuUnit = computed(() => props.evidence.data.references?.[`sku:${skuContext.value?.id}`]?.unit)
const labels: Record<string, string> = {
  warehouseId: '仓库',
  locationId: '库位',
  skuId: '商品',
  sourceWarehouseId: '源仓库',
  targetWarehouseId: '目标仓库',
  sourceLocationId: '源库位',
  targetLocationId: '目标库位',
  actualQuantity: '实际库存',
  availableQuantity: '可用库存',
  frozenQuantity: '冻结库存',
  beforeActualQuantity: '实际前值',
  changeActualQuantity: '实际变化',
  afterActualQuantity: '实际后值',
  beforeAvailableQuantity: '可用前值',
  changeAvailableQuantity: '可用变化',
  afterAvailableQuantity: '可用后值',
  beforeFrozenQuantity: '冻结前值',
  changeFrozenQuantity: '冻结变化',
  afterFrozenQuantity: '冻结后值',
  ledgerNo: '流水号',
  businessType: '业务动作',
  businessNo: '业务单号',
  occurredAt: '发生时间',
  meaning: '业务解释',
  lineNo: '行号',
  quantity: '数量',
  countBookQuantity: '账面量',
  countedQuantity: '实盘量',
  differenceQuantity: '差异量',
  snapshotActualQuantity: '快照实际',
  snapshotAvailableQuantity: '快照可用',
  snapshotFrozenQuantity: '快照冻结',
  updatedAt: '更新时间',
  ledgerCount: '流水条数',
  documentType: '单据类型',
  status: '当前单据状态',
  reservedAt: '冻结时间',
  salesQuantity: '有效销售冻结量',
  transferQuantity: '有效调拨冻结量',
  sourceQuantity: '有效单据占用总量',
}
const statusLabels: Record<string, string> = {
  DRAFT: '草稿',
  SUBMITTED: '已提交',
  RESERVED: '已冻结',
  APPROVED: '已审核',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
  COUNTING: '盘点中',
  ADJUSTED: '已调整',
  OUTBOUND_COMPLETED: '已调出',
  IN_TRANSIT: '在途',
}
const headings: Record<string, string> = {
  query_balances: '库存查询',
  query_ledgers: '库存流水',
  summarize_movements: '时间段库存变化',
  query_frozen_sources: '当前有效冻结来源',
  get_document: '业务单据',
  trace_ledger: '流水来源',
  find_sku: '商品查询',
  find_warehouse: '仓库查询',
  find_location: '库位查询',
}
const tables = computed(() => {
  const data = props.evidence.data
  const pages: Array<{ title: string; page?: QueryPage; rows: Record<string, unknown>[] }> = []
  for (const [key, title] of [
    ['warehouses', '仓库汇总（覆盖匹配库位）'],
    ['locations', '库位明细'],
    ['ledgers', '流水明细'],
    ['salesSources', '有效销售冻结明细'],
    ['transferSources', '有效调拨冻结明细'],
  ] as const) {
    const page = data[key]
    if (page) pages.push({ title, page, rows: page.records })
  }
  if (data.lines) pages.push({ title: `单据明细：显示 ${data.lineReturned} / ${data.lineTotal} 行`, rows: data.lines })
  if (data.summary) pages.push({ title: '区间差量汇总（覆盖全部匹配流水）', rows: [data.summary] })
  if (data.movements) pages.push({ title: '按业务动作汇总', rows: data.movements })
  if (data.frozenTotals) pages.push({ title: '当前冻结总量核对（独立于分页）', rows: [data.frozenTotals] })
  return pages
})
function columns(rows: Record<string, unknown>[]) {
  return Object.keys(rows[0] ?? {}).filter((key) => labels[key])
}
function display(row: Record<string, unknown>, key: string) {
  const value = row[key]
  if (key === 'documentType') return value === 'SALES' ? '销售出库' : value === 'TRANSFER' ? '仓库调拨' : value
  if (key === 'status') return statusLabels[String(value)] ?? value
  if (key === 'businessType') return row.meaning ?? value
  const kind =
    key === 'skuId'
      ? 'sku'
      : key.toLowerCase().includes('warehouseid')
        ? 'warehouse'
        : key.toLowerCase().includes('locationid')
          ? 'location'
          : ''
  const reference = props.evidence.data.references?.[`${kind}:${value}`]
  return reference
    ? `${reference.name}（${reference.code}）${reference.unit ? ' · ' + reference.unit : ''}`
    : (value ?? '—')
}
function allowedSource(path: string) {
  return /^\/(inventory\/(balances|ledgers)|documents\/(sales-outbound|purchase-receipts|transfers|inventory-counts))(\?|$)/.test(
    path,
  )
}
</script>
<template>
  <el-card
    class="evidence-card"
    shadow="never"
  >
    <strong>{{ headings[evidence.tool] ?? '查询结果' }}</strong>
    <p>{{ evidence.message }}</p>
    <p class="query-time">查询时间：{{ evidence.queriedAt }}</p>
    <div v-if="skuContext">商品：{{ skuContext.name }}（{{ skuContext.code }}）</div>
    <p v-if="evidence.data.warehouse">仓库：{{ evidence.data.warehouse.name }}（{{ evidence.data.warehouse.code }}）</p>
    <p v-if="evidence.data.location">库位：{{ evidence.data.location.name }}（{{ evidence.data.location.code }}）</p>
    <p v-if="skuUnit">数量单位：{{ skuUnit }}</p>
    <p v-if="evidence.data.period">
      区间：{{ evidence.data.period.startDate }} 至 {{ evidence.data.period.endDate }}（含起止日，{{
        evidence.data.period.timezone
      }}）
    </p>
    <p v-if="evidence.data.totalCheck">
      总量核对：{{
        evidence.data.totalCheck === 'TOTAL_MATCH'
          ? '范围内总量一致'
          : evidence.data.totalCheck === 'TOTAL_MISMATCH'
            ? '存在差异，请核查'
            : '余额缺失，无法核对'
      }}
    </p>
    <p v-if="evidence.data.status">
      单据状态：{{ statusLabels[String(evidence.data.status)] ?? evidence.data.status }}
    </p>
    <p v-if="evidence.data.ledgerNo">流水：{{ evidence.data.ledgerNo }} · {{ evidence.data.meaning }}</p>
    <p v-if="evidence.data.outboundNo || evidence.data.receiptNo || evidence.data.transferNo || evidence.data.countNo">
      业务单号：{{
        evidence.data.outboundNo || evidence.data.receiptNo || evidence.data.transferNo || evidence.data.countNo
      }}
    </p>
    <div
      v-if="evidence.candidates.length"
      class="candidate-list"
    >
      <el-button
        v-for="candidate in evidence.candidates"
        :key="`${candidate.kind}:${candidate.id}`"
        @click="emit('select', candidate)"
        >{{ candidate.name }}（{{ candidate.code }}）</el-button
      >
    </div>
    <section
      v-for="table in tables"
      :key="table.title"
      class="result-table"
    >
      <h3>{{ table.title }}</h3>
      <p v-if="table.page">
        第 {{ table.page.page }} 页，每页最多 {{ table.page.size }} 条；本页 {{ table.page.returned }} 条，条件匹配
        {{ table.page.total }} 条。
      </p>
      <el-table
        :data="table.rows"
        border
        max-height="420"
        ><el-table-column
          v-for="key in columns(table.rows)"
          :key="key"
          :label="labels[key]"
          min-width="150"
          ><template #default="{ row }">{{ display(row, key) }}</template></el-table-column
        ></el-table
      >
    </section>
    <nav
      class="source-links"
      aria-label="查询来源"
    >
      <template
        v-for="source in evidence.sources"
        :key="source.path"
        ><RouterLink
          v-if="auth.can(source.authority) && allowedSource(source.path)"
          :to="source.path"
          >{{ source.label }}</RouterLink
        ><span v-else>{{ source.label }}（无页面读取权限）</span></template
      >
    </nav>
  </el-card>
</template>
<style scoped>
.evidence-card {
  margin-top: 16px;
}
.query-time {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.source-links,
.candidate-list {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 16px;
}
.candidate-list :deep(.el-button) {
  margin-left: 0;
}
.result-table {
  margin-top: 20px;
}
</style>
