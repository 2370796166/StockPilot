<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { pageLedgers } from '@/modules/inventory/api'
import EntityRef from '@/shared/components/EntityRef.vue'
import RemoteLocationSelect from '@/shared/components/RemoteLocationSelect.vue'
import RemoteMasterDataSelect from '@/shared/components/RemoteMasterDataSelect.vue'
import ServerPagination from '@/shared/components/ServerPagination.vue'
import type { InventoryBusinessType, InventoryLedger } from '@/modules/inventory/types'
import { useLiveSearch } from '@/shared/composables/useLiveSearch'
const loading = ref(false),
  records = ref<InventoryLedger[]>([]),
  total = ref(0),
  detail = ref<InventoryLedger | null>(null),
  detailVisible = ref(false)
const query = reactive<{
  page: number
  size: number
  warehouseId?: number
  locationId?: number
  skuId?: number
  businessType?: InventoryBusinessType
  businessNo: string
}>({ page: 1, size: 20, businessNo: '' })
const types: InventoryBusinessType[] = [
  'INITIALIZE',
  'PURCHASE_RECEIPT',
  'OUTBOUND_FREEZE',
  'OUTBOUND_RELEASE',
  'OUTBOUND_SHIP',
  'TRANSFER_FREEZE',
  'TRANSFER_RELEASE',
  'TRANSFER_OUT',
  'TRANSFER_IN',
  'INVENTORY_COUNT',
  'INVENTORY_GAIN',
  'INVENTORY_LOSS',
]
const labels: Record<InventoryBusinessType, string> = {
  INITIALIZE: '库存初始化',
  PURCHASE_RECEIPT: '采购入库',
  OUTBOUND_FREEZE: '销售冻结',
  OUTBOUND_RELEASE: '销售释放',
  OUTBOUND_SHIP: '销售出库',
  TRANSFER_FREEZE: '调拨冻结',
  TRANSFER_RELEASE: '调拨释放',
  TRANSFER_OUT: '调拨出库',
  TRANSFER_IN: '调拨入库',
  INVENTORY_COUNT: '库存盘点',
  INVENTORY_GAIN: '盘盈',
  INVENTORY_LOSS: '盘亏',
}
async function load() {
  loading.value = true
  try {
    const result = await pageLedgers({ ...query, businessNo: query.businessNo || undefined })
    records.value = result.records
    total.value = result.total
  } finally {
    loading.value = false
  }
}
function search() {
  query.page = 1
  void load()
}
function reset() {
  Object.assign(query, {
    page: 1,
    warehouseId: undefined,
    locationId: undefined,
    skuId: undefined,
    businessType: undefined,
    businessNo: '',
  })
  cancelLiveSearch()
  void load()
}
function show(row: InventoryLedger) {
  detail.value = row
  detailVisible.value = true
}
const cancelLiveSearch = useLiveSearch([() => query.businessNo], search)
onMounted(load)
</script>
<template>
  <div class="page-stack">
    <div class="page-heading">
      <div>
        <p class="eyebrow">库存中心</p>
        <h1>库存流水</h1>
        <p>追加式库存审计事实，不提供修改或删除操作</p>
      </div>
    </div>
    <el-card
      shadow="never"
      class="filter-card"
      ><el-form
        inline
        @submit.prevent="search"
        ><el-form-item label="业务单号"
          ><el-input
            v-model="query.businessNo"
            clearable /></el-form-item
        ><el-form-item label="业务类型"
          ><el-select
            v-model="query.businessType"
            clearable
            style="width: 140px"
            ><el-option
              v-for="type in types"
              :key="type"
              :label="labels[type]"
              :value="type" /></el-select></el-form-item
        ><el-form-item label="仓库"
          ><RemoteMasterDataSelect
            v-model="query.warehouseId"
            resource="warehouses" /></el-form-item
        ><el-form-item label="库位"
          ><RemoteLocationSelect
            v-model="query.locationId"
            :warehouse-id="query.warehouseId" /></el-form-item
        ><el-form-item label="SKU"
          ><RemoteMasterDataSelect
            v-model="query.skuId"
            resource="skus" /></el-form-item
        ><el-form-item
          ><el-button
            type="primary"
            native-type="submit"
            >查询</el-button
          ><el-button @click="reset">重置</el-button></el-form-item
        ></el-form
      ></el-card
    ><el-card
      shadow="never"
      class="table-card"
      ><el-table
        v-loading="loading"
        :data="records"
        ><el-table-column
          prop="ledgerNo"
          label="流水号"
          min-width="190"
        /><el-table-column
          label="业务类型"
          min-width="120"
          ><template #default="scope">{{
            labels[scope.row.businessType as InventoryBusinessType]
          }}</template></el-table-column
        ><el-table-column
          prop="businessNo"
          label="业务单号"
          min-width="150"
        /><el-table-column
          label="SKU"
          min-width="170"
          ><template #default="scope"
            ><EntityRef
              resource="skus"
              :id="scope.row.skuId" /></template></el-table-column
        ><el-table-column
          prop="changeActualQuantity"
          label="实际变化"
          align="right"
        /><el-table-column
          prop="changeAvailableQuantity"
          label="可用变化"
          align="right"
        /><el-table-column
          prop="changeFrozenQuantity"
          label="冻结变化"
          align="right"
        /><el-table-column
          prop="operatorName"
          label="操作人"
          width="110"
        /><el-table-column
          prop="occurredAt"
          label="发生时间"
          min-width="165"
        /><el-table-column
          label="操作"
          width="70"
          ><template #default="scope"
            ><el-button
              link
              type="primary"
              @click="show(scope.row)"
              >详情</el-button
            ></template
          ></el-table-column
        ></el-table
      ><ServerPagination
        v-model:page="query.page"
        v-model:size="query.size"
        :total="total"
        @change="load" /></el-card
    ><el-dialog
      v-model="detailVisible"
      title="库存流水详情"
      width="760px"
      ><template v-if="detail"
        ><el-descriptions
          :column="2"
          border
          ><el-descriptions-item label="流水号">{{ detail.ledgerNo }}</el-descriptions-item
          ><el-descriptions-item label="业务单号">{{ detail.businessNo }}</el-descriptions-item
          ><el-descriptions-item label="仓库"
            ><EntityRef
              resource="warehouses"
              :id="detail.warehouseId" /></el-descriptions-item
          ><el-descriptions-item label="库位"
            ><EntityRef
              resource="locations"
              :id="detail.locationId" /></el-descriptions-item
          ><el-descriptions-item label="SKU"
            ><EntityRef
              resource="skus"
              :id="detail.skuId" /></el-descriptions-item
          ><el-descriptions-item label="操作人">{{ detail.operatorName }}</el-descriptions-item
          ><el-descriptions-item label="实际量"
            >{{ detail.beforeActualQuantity }} → {{ detail.afterActualQuantity }}（{{
              detail.changeActualQuantity
            }}）</el-descriptions-item
          ><el-descriptions-item label="可用量"
            >{{ detail.beforeAvailableQuantity }} → {{ detail.afterAvailableQuantity }}（{{
              detail.changeAvailableQuantity
            }}）</el-descriptions-item
          ><el-descriptions-item label="冻结量"
            >{{ detail.beforeFrozenQuantity }} → {{ detail.afterFrozenQuantity }}（{{
              detail.changeFrozenQuantity
            }}）</el-descriptions-item
          ><el-descriptions-item label="版本"
            >{{ detail.balanceVersionBefore }} → {{ detail.balanceVersionAfter }}</el-descriptions-item
          ><el-descriptions-item
            v-if="detail.countedQuantity !== null"
            label="盘点结果"
            :span="2"
            >账面 {{ detail.countBookQuantity }} / 实盘 {{ detail.countedQuantity }} / 差异
            {{ detail.differenceQuantity }} / 原因 {{ detail.adjustmentReason }}</el-descriptions-item
          ></el-descriptions
        ></template
      ></el-dialog
    >
  </div>
</template>
