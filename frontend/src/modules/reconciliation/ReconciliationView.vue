<script setup lang="ts">
import {
  ElButton,
  ElCard,
  ElForm,
  ElFormItem,
  ElDescriptions,
  ElDescriptionsItem,
  ElTable,
  ElTableColumn,
  ElMessage,
  vLoading,
} from 'element-plus'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/card/style/css'
import 'element-plus/es/components/form/style/css'
import 'element-plus/es/components/form-item/style/css'
import 'element-plus/es/components/descriptions/style/css'
import 'element-plus/es/components/descriptions-item/style/css'
import 'element-plus/es/components/table/style/css'
import 'element-plus/es/components/table-column/style/css'
import 'element-plus/es/components/loading/style/css'
import { reactive, ref, watch } from 'vue'
import RemoteMasterDataSelect from '@/shared/components/RemoteMasterDataSelect.vue'
import RemoteLocationSelect from '@/shared/components/RemoteLocationSelect.vue'
import { reconcile } from './api'
import type { ReconciliationResult } from './api'
import { useLatestRequest } from '@/shared/composables/useLatestRequest'
const query = reactive<{ warehouseId?: number; locationId?: number; skuId?: number }>({})
const result = ref<ReconciliationResult | null>(null),
  loading = ref(false)
const latest = useLatestRequest()
watch(
  () => [query.warehouseId, query.locationId, query.skuId],
  () => {
    latest.invalidate()
    result.value = null
    loading.value = false
  },
)
async function check() {
  if (!query.warehouseId || !query.locationId || !query.skuId) {
    ElMessage.warning('请选择仓库、库位和商品')
    return
  }
  const sequence = latest.next()
  loading.value = true
  result.value = null
  try {
    const value = await reconcile({ warehouseId: query.warehouseId, locationId: query.locationId, skuId: query.skuId })
    if (latest.isCurrent(sequence)) result.value = value
  } catch {
    /* A failed check is not a successful reconciliation. */
  } finally {
    if (latest.isCurrent(sequence)) loading.value = false
  }
}
</script>
<template>
  <div class="page-stack">
    <div class="page-heading">
      <div>
        <p class="eyebrow">库存核验</p>
        <h1>库存对账</h1>
        <p>核对指定库位与商品的余额、完整流水、冻结来源和调拨事实</p>
      </div>
    </div>
    <el-card
      shadow="never"
      class="filter-card"
      ><el-form
        inline
        @submit.prevent="check"
        ><el-form-item label="仓库"
          ><RemoteMasterDataSelect
            v-model="query.warehouseId"
            resource="warehouses" /></el-form-item
        ><el-form-item label="库位"
          ><RemoteLocationSelect
            v-model="query.locationId"
            :warehouse-id="query.warehouseId" /></el-form-item
        ><el-form-item label="商品"
          ><RemoteMasterDataSelect
            v-model="query.skuId"
            resource="skus" /></el-form-item
        ><el-form-item
          ><el-button
            type="primary"
            native-type="submit"
            :loading="loading"
            >开始对账</el-button
          ></el-form-item
        ></el-form
      ></el-card
    >
    <el-card
      v-loading="loading"
      shadow="never"
      ><template v-if="result"
        ><h2>{{ result.issues.length ? `发现 ${result.issues.length} 类差异` : '本次检查未发现差异' }}</h2>
        <p class="eyebrow">检查时间：{{ result.checkedAt }}。结果只覆盖所选维度；对账不会修改库存。</p>
        <el-descriptions
          :column="2"
          border
          ><el-descriptions-item label="完整流水条数">{{ result.inventory.summary.ledgerCount }}</el-descriptions-item
          ><el-descriptions-item label="流水链断点">{{ result.inventory.summary.brokenLinks }}</el-descriptions-item
          ><el-descriptions-item label="当前实际量">{{
            result.inventory.balance?.actualQuantity ?? '无余额记录'
          }}</el-descriptions-item
          ><el-descriptions-item label="流水实际量累计">{{
            result.inventory.summary.actualChange
          }}</el-descriptions-item
          ><el-descriptions-item label="当前冻结量">{{
            result.inventory.balance?.frozenQuantity ?? '无余额记录'
          }}</el-descriptions-item
          ><el-descriptions-item label="有效单据占用量">{{ result.frozenSourceQuantity }}</el-descriptions-item
          ><el-descriptions-item label="核对调拨条数">{{ result.checkedTransfers }}</el-descriptions-item
          ><el-descriptions-item label="当前余额版本">{{
            result.inventory.balance?.version ?? '无余额记录'
          }}</el-descriptions-item></el-descriptions
        ><el-table
          v-if="result.issues.length"
          :data="result.issues"
          style="margin-top: 20px"
          ><el-table-column
            label="检查项"
            prop="code"
            min-width="160" /><el-table-column
            label="关联单号"
            prop="businessNo"
            min-width="170" /><el-table-column
            label="差异说明"
            prop="description"
            min-width="280" /></el-table
      ></template>
      <p v-else-if="!loading">选择库存维度后开始检查。发现差异时，请追溯原始业务单据与流水。</p></el-card
    >
  </div>
</template>
