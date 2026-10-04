<script setup lang="ts">
import { sourceFilters } from '@/shared/utils/source-filters'
import { useLatestRequest } from '@/shared/composables/useLatestRequest'
import { onMounted, reactive, ref } from 'vue'
import { getCount, pageCounts } from '@/modules/inventory-count/api'
import InventoryCountCreateDialog from '@/modules/inventory-count/components/InventoryCountCreateDialog.vue'
import InventoryCountDetailDialog from '@/modules/inventory-count/components/InventoryCountDetailDialog.vue'
import InventoryCountResultsDialog from '@/modules/inventory-count/components/InventoryCountResultsDialog.vue'
import type { CountDetail, CountStatus, CountSummary } from '@/modules/inventory-count/types'
import { useInventoryCountActions } from '@/modules/inventory-count/useInventoryCountActions'
import { useAuthStore } from '@/modules/auth/store'
import BusinessStatusTag from '@/shared/components/BusinessStatusTag.vue'
import EntityRef from '@/shared/components/EntityRef.vue'
import PermissionGate from '@/shared/components/PermissionGate.vue'
import RemoteMasterDataSelect from '@/shared/components/RemoteMasterDataSelect.vue'
import ServerPagination from '@/shared/components/ServerPagination.vue'
import { useLiveSearch } from '@/shared/composables/useLiveSearch'

const auth = useAuthStore()
const loading = ref(false)
const records = ref<CountSummary[]>([])
const total = ref(0)
const createDialog = ref(false)
const detailDialog = ref(false)
const resultDialog = ref(false)
const detail = ref<CountDetail | null>(null)
const query = reactive<{ page: number; size: number; countNo: string; warehouseId?: number; status?: CountStatus }>({
  page: 1,
  size: 20,
  countNo: sourceFilters().businessNo,
})
const statuses: CountStatus[] = ['DRAFT', 'COUNTING', 'SUBMITTED', 'APPROVED', 'ADJUSTED']

const listRequest = useLatestRequest()
async function load() {
  const sequence = listRequest.next()
  loading.value = true
  try {
    const result = await pageCounts({ ...query, countNo: query.countNo || undefined })
    if (!listRequest.isCurrent(sequence)) return
    records.value = result.records
    total.value = result.total
  } catch {
    // Request errors are displayed by the shared interceptor.
  } finally {
    if (listRequest.isCurrent(sequence)) loading.value = false
  }
}

function search() {
  query.page = 1
  void load()
}

const { acting, actions, execute } = useInventoryCountActions(load)
useLiveSearch([() => query.countNo], search)

const documentRequest = useLatestRequest()
function create() {
  documentRequest.invalidate()
  createDialog.value = true
}
async function openDocument(row: CountSummary, record: boolean) {
  const sequence = documentRequest.next()
  detailDialog.value = false
  resultDialog.value = false
  try {
    const item = await getCount(row.id)
    if (!documentRequest.isCurrent(sequence)) return
    detail.value = item
    if (record) resultDialog.value = true
    else detailDialog.value = true
  } catch {
    // Request errors are displayed by the shared interceptor.
  }
}
async function show(row: CountSummary) {
  await openDocument(row, false)
}
async function recordResults(row: CountSummary) {
  await openDocument(row, true)
}

async function handleSavedResults(saved: CountDetail) {
  detail.value = saved
  await load()
}

onMounted(load)
</script>

<template>
  <div class="page-stack">
    <div class="page-heading">
      <div>
        <p class="eyebrow">库存中心</p>
        <h1>库存盘点</h1>
        <p>静态维度锁、实盘录入、审核与差异调整</p>
      </div>
      <PermissionGate authority="INVENTORY_COUNT_WRITE">
        <el-button
          type="primary"
          @click="create"
          >新建盘点单</el-button
        >
      </PermissionGate>
    </div>
    <el-card
      shadow="never"
      class="filter-card"
    >
      <el-form
        inline
        @submit.prevent="search"
      >
        <el-form-item label="盘点单号"><el-input v-model="query.countNo" /></el-form-item>
        <el-form-item label="仓库">
          <RemoteMasterDataSelect
            v-model="query.warehouseId"
            resource="warehouses"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select
            v-model="query.status"
            clearable
          >
            <el-option
              v-for="status in statuses"
              :key="status"
              :label="status"
              :value="status"
            />
          </el-select>
        </el-form-item>
        <el-form-item
          ><el-button
            type="primary"
            native-type="submit"
            >查询</el-button
          ></el-form-item
        >
      </el-form>
    </el-card>
    <el-card
      shadow="never"
      class="table-card"
    >
      <el-table
        v-loading="loading"
        :data="records"
      >
        <el-table-column
          prop="countNo"
          label="盘点单号"
        />
        <el-table-column label="仓库">
          <template #default="scope"
            ><EntityRef
              resource="warehouses"
              :id="scope.row.warehouseId"
          /></template>
        </el-table-column>
        <el-table-column label="状态">
          <template #default="scope"><BusinessStatusTag :status="scope.row.status" /></template>
        </el-table-column>
        <el-table-column
          prop="createdAt"
          label="创建时间"
        />
        <el-table-column
          label="操作"
          min-width="280"
        >
          <template #default="scope">
            <el-button
              link
              type="primary"
              @click="show(scope.row)"
              >详情</el-button
            >
            <el-button
              v-if="scope.row.status === 'COUNTING' && auth.can('INVENTORY_COUNT_WRITE')"
              link
              type="primary"
              @click="recordResults(scope.row)"
              >录入实盘</el-button
            >
            <el-button
              v-for="action in actions(scope.row)"
              :key="action.key"
              link
              type="primary"
              :loading="acting === scope.row.id"
              @click="execute(scope.row, action)"
              >{{ action.label }}</el-button
            >
          </template>
        </el-table-column>
      </el-table>
      <ServerPagination
        v-model:page="query.page"
        v-model:size="query.size"
        :total="total"
        @change="load"
      />
    </el-card>
    <InventoryCountCreateDialog
      v-model="createDialog"
      @saved="load"
    />
    <InventoryCountDetailDialog
      v-model="detailDialog"
      :detail="detail"
    />
    <InventoryCountResultsDialog
      v-model="resultDialog"
      :detail="detail"
      @saved="handleSavedResults"
    />
  </div>
</template>
