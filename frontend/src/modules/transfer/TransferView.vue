<script setup lang="ts">
import { sourceFilters } from '@/shared/utils/source-filters'
import { useLatestRequest } from '@/shared/composables/useLatestRequest'
import { onMounted, reactive, ref } from 'vue'
import { getTransfer, pageTransfers } from '@/modules/transfer/api'
import TransferDetailDialog from '@/modules/transfer/components/TransferDetailDialog.vue'
import TransferFormDialog from '@/modules/transfer/components/TransferFormDialog.vue'
import type { TransferDetail, TransferStatus, TransferSummary } from '@/modules/transfer/types'
import { useTransferActions } from '@/modules/transfer/useTransferActions'
import { useAuthStore } from '@/modules/auth/store'
import BusinessStatusTag from '@/shared/components/BusinessStatusTag.vue'
import EntityRef from '@/shared/components/EntityRef.vue'
import PermissionGate from '@/shared/components/PermissionGate.vue'
import RemoteMasterDataSelect from '@/shared/components/RemoteMasterDataSelect.vue'
import ServerPagination from '@/shared/components/ServerPagination.vue'
import { useLiveSearch } from '@/shared/composables/useLiveSearch'

const auth = useAuthStore()
const loading = ref(false)
const records = ref<TransferSummary[]>([])
const total = ref(0)
const formDialog = ref(false)
const detailDialog = ref(false)
const editing = ref<TransferDetail | null>(null)
const detail = ref<TransferDetail | null>(null)
const query = reactive<{
  page: number
  size: number
  transferNo: string
  sourceWarehouseId?: number
  targetWarehouseId?: number
  status?: TransferStatus
}>({ page: 1, size: 20, transferNo: sourceFilters().businessNo })
const statuses: TransferStatus[] = [
  'DRAFT',
  'SUBMITTED',
  'APPROVED',
  'OUTBOUND_COMPLETED',
  'IN_TRANSIT',
  'COMPLETED',
  'CANCELLED',
]

const listRequest = useLatestRequest()
async function load() {
  const sequence = listRequest.next()
  loading.value = true
  try {
    const result = await pageTransfers({ ...query, transferNo: query.transferNo || undefined })
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

const cancelLiveSearch = useLiveSearch([() => query.transferNo], search)
const { acting, actions, execute } = useTransferActions(load)

function reset() {
  Object.assign(query, {
    page: 1,
    transferNo: '',
    sourceWarehouseId: undefined,
    targetWarehouseId: undefined,
    status: undefined,
  })
  cancelLiveSearch()
  void load()
}

const documentRequest = useLatestRequest()
function create() {
  documentRequest.invalidate()
  editing.value = null
  formDialog.value = true
}

async function edit(row: TransferSummary) {
  const sequence = documentRequest.next()
  try {
    const item = await getTransfer(row.id)
    if (!documentRequest.isCurrent(sequence)) return
    editing.value = item
    formDialog.value = true
  } catch {
    // Request errors are displayed by the shared interceptor.
  }
}

async function show(row: TransferSummary) {
  const sequence = documentRequest.next()
  try {
    const item = await getTransfer(row.id)
    if (!documentRequest.isCurrent(sequence)) return
    detail.value = item
    detailDialog.value = true
  } catch {
    // Request errors are displayed by the shared interceptor.
  }
}

onMounted(load)
</script>

<template>
  <div class="page-stack">
    <div class="page-heading">
      <div>
        <p class="eyebrow">业务单据</p>
        <h1>库存调拨</h1>
        <p>源仓冻结、调出、在途与目标收货</p>
      </div>
      <PermissionGate authority="TRANSFER_WRITE">
        <el-button
          type="primary"
          @click="create"
          >新建调拨单</el-button
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
        <el-form-item label="调拨单号"><el-input v-model="query.transferNo" /></el-form-item>
        <el-form-item label="源仓">
          <RemoteMasterDataSelect
            v-model="query.sourceWarehouseId"
            resource="warehouses"
          />
        </el-form-item>
        <el-form-item label="目标仓">
          <RemoteMasterDataSelect
            v-model="query.targetWarehouseId"
            resource="warehouses"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select
            v-model="query.status"
            clearable
            style="width: 140px"
          >
            <el-option
              v-for="status in statuses"
              :key="status"
              :label="status"
              :value="status"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            native-type="submit"
            >查询</el-button
          ><el-button @click="reset">重置</el-button>
        </el-form-item>
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
          prop="transferNo"
          label="调拨单号"
          min-width="160"
        />
        <el-table-column
          label="源仓"
          min-width="150"
        >
          <template #default="scope">
            <EntityRef
              resource="warehouses"
              :id="scope.row.sourceWarehouseId"
            />
          </template>
        </el-table-column>
        <el-table-column
          label="目标仓"
          min-width="150"
        >
          <template #default="scope">
            <EntityRef
              resource="warehouses"
              :id="scope.row.targetWarehouseId"
            />
          </template>
        </el-table-column>
        <el-table-column
          label="状态"
          width="110"
        >
          <template #default="scope"><BusinessStatusTag :status="scope.row.status" /></template>
        </el-table-column>
        <el-table-column
          prop="createdAt"
          label="创建时间"
          min-width="165"
        />
        <el-table-column
          label="操作"
          min-width="300"
        >
          <template #default="scope">
            <el-button
              link
              type="primary"
              @click="show(scope.row)"
              >详情</el-button
            >
            <el-button
              v-if="scope.row.status === 'DRAFT' && auth.can('TRANSFER_WRITE')"
              link
              type="primary"
              @click="edit(scope.row)"
              >编辑</el-button
            >
            <el-button
              v-for="action in actions(scope.row)"
              :key="action.key"
              link
              :type="action.key === 'cancel' ? 'danger' : 'primary'"
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
    <TransferFormDialog
      v-model="formDialog"
      :editing="editing"
      @saved="load"
    />
    <TransferDetailDialog
      v-model="detailDialog"
      :detail="detail"
    />
  </div>
</template>
