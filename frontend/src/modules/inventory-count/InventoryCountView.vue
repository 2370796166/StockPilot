<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createCount, getCount, pageCounts, recordCountResults, transitionCount } from '@/modules/inventory-count/api'
import BusinessStatusTag from '@/shared/components/BusinessStatusTag.vue'
import EntityRef from '@/shared/components/EntityRef.vue'
import PermissionGate from '@/shared/components/PermissionGate.vue'
import RemoteLocationSelect from '@/shared/components/RemoteLocationSelect.vue'
import RemoteMasterDataSelect from '@/shared/components/RemoteMasterDataSelect.vue'
import ServerPagination from '@/shared/components/ServerPagination.vue'
import { useLiveSearch } from '@/shared/composables/useLiveSearch'
import { useAuthStore } from '@/modules/auth/store'
import type { CountDetail, CountStatus, CountSummary } from '@/modules/documents/types'
const auth = useAuthStore(),
  loading = ref(false),
  records = ref<CountSummary[]>([]),
  total = ref(0),
  createDialog = ref(false),
  detailDialog = ref(false),
  resultDialog = ref(false),
  saving = ref(false),
  acting = ref<number>(),
  detail = ref<CountDetail | null>(null)
const query = reactive<{ page: number; size: number; countNo: string; warehouseId?: number; status?: CountStatus }>({
  page: 1,
  size: 20,
  countNo: '',
})
const form = reactive<{
  countNo: string
  warehouseId?: number
  remark: string
  dimensions: Array<{ locationId: number; skuId: number }>
}>({ countNo: '', remark: '', dimensions: [] })
const statuses: CountStatus[] = ['DRAFT', 'COUNTING', 'SUBMITTED', 'APPROVED', 'ADJUSTED']
async function load() {
  loading.value = true
  try {
    const r = await pageCounts({ ...query, countNo: query.countNo || undefined })
    records.value = r.records
    total.value = r.total
  } finally {
    loading.value = false
  }
}
function openCreate() {
  Object.assign(form, { countNo: '', warehouseId: undefined, remark: '', dimensions: [{ locationId: 0, skuId: 0 }] })
  createDialog.value = true
}
function add() {
  form.dimensions.push({ locationId: 0, skuId: 0 })
}
async function save() {
  if (
    saving.value ||
    !form.countNo.match(/^[A-Za-z0-9_-]{2,64}$/) ||
    !form.warehouseId ||
    !form.dimensions.length ||
    form.dimensions.some((x) => !x.locationId || !x.skuId)
  ) {
    ElMessage.warning('请完整填写盘点单号、仓库和盘点维度')
    return
  }
  saving.value = true
  try {
    await createCount({
      countNo: form.countNo,
      warehouseId: form.warehouseId,
      remark: form.remark,
      dimensions: form.dimensions,
    })
    ElMessage.success('盘点单已创建并锁定维度')
    createDialog.value = false
    await load()
  } finally {
    saving.value = false
  }
}
async function show(r: CountSummary) {
  detail.value = await getCount(r.id)
  detailDialog.value = true
}
async function results(r: CountSummary) {
  detail.value = await getCount(r.id)
  resultDialog.value = true
}
async function saveResults() {
  if (
    !detail.value ||
    saving.value ||
    detail.value.lines.some((x) => x.countedQuantity === null || !x.reason?.trim())
  ) {
    ElMessage.warning('每条明细都必须填写实盘数量和原因')
    return
  }
  saving.value = true
  try {
    detail.value = await recordCountResults(
      detail.value.id,
      detail.value.version,
      detail.value.lines.map((x) => ({ lineId: x.id, countedQuantity: Number(x.countedQuantity), reason: x.reason! })),
    )
    ElMessage.success('实盘结果已保存')
    resultDialog.value = false
    await load()
  } finally {
    saving.value = false
  }
}
function actions(r: CountSummary) {
  const a = [] as Array<{ key: string; label: string; perm: string; version: boolean }>
  if (r.status === 'DRAFT') a.push({ key: 'start', label: '开始盘点', perm: 'INVENTORY_COUNT_WRITE', version: true })
  if (r.status === 'COUNTING')
    a.push({ key: 'submit', label: '提交审核', perm: 'INVENTORY_COUNT_WRITE', version: true })
  if (r.status === 'SUBMITTED')
    a.push({ key: 'approve', label: '审核', perm: 'INVENTORY_COUNT_APPROVE', version: true })
  if (r.status === 'APPROVED')
    a.push({ key: 'adjust', label: '执行调整', perm: 'INVENTORY_COUNT_ADJUST', version: false })
  return a.filter((x) => auth.can(x.perm))
}
async function execute(r: CountSummary, a: ReturnType<typeof actions>[number]) {
  try {
    await ElMessageBox.confirm(`确认执行“${a.label}”吗？`, '关键操作确认', { type: 'warning' })
  } catch {
    return
  }
  acting.value = r.id
  try {
    await transitionCount(r.id, a.key, a.version ? r.version : undefined)
    ElMessage.success(`${a.label}成功`)
    await load()
  } finally {
    acting.value = undefined
  }
}
function search() {
  query.page = 1
  void load()
}
useLiveSearch([() => query.countNo], search)
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
      <PermissionGate authority="INVENTORY_COUNT_WRITE"
        ><el-button
          type="primary"
          @click="openCreate"
          >新建盘点单</el-button
        ></PermissionGate
      >
    </div>
    <el-card
      shadow="never"
      class="filter-card"
      ><el-form
        inline
        @submit.prevent="search"
        ><el-form-item label="盘点单号"><el-input v-model="query.countNo" /></el-form-item
        ><el-form-item label="仓库"
          ><RemoteMasterDataSelect
            v-model="query.warehouseId"
            resource="warehouses" /></el-form-item
        ><el-form-item label="状态"
          ><el-select
            v-model="query.status"
            clearable
            ><el-option
              v-for="s in statuses"
              :key="s"
              :label="s"
              :value="s" /></el-select></el-form-item
        ><el-form-item
          ><el-button
            type="primary"
            native-type="submit"
            >查询</el-button
          ></el-form-item
        ></el-form
      ></el-card
    ><el-card
      shadow="never"
      class="table-card"
      ><el-table
        v-loading="loading"
        :data="records"
        ><el-table-column
          prop="countNo"
          label="盘点单号"
        /><el-table-column label="仓库"
          ><template #default="s"
            ><EntityRef
              resource="warehouses"
              :id="s.row.warehouseId" /></template></el-table-column
        ><el-table-column label="状态"
          ><template #default="s"><BusinessStatusTag :status="s.row.status" /></template></el-table-column
        ><el-table-column
          prop="createdAt"
          label="创建时间"
        /><el-table-column
          label="操作"
          min-width="280"
          ><template #default="s"
            ><el-button
              link
              type="primary"
              @click="show(s.row)"
              >详情</el-button
            ><el-button
              v-if="s.row.status === 'COUNTING' && auth.can('INVENTORY_COUNT_WRITE')"
              link
              type="primary"
              @click="results(s.row)"
              >录入实盘</el-button
            ><el-button
              v-for="a in actions(s.row)"
              :key="a.key"
              link
              type="primary"
              :loading="acting === s.row.id"
              @click="execute(s.row, a)"
              >{{ a.label }}</el-button
            ></template
          ></el-table-column
        ></el-table
      ><ServerPagination
        v-model:page="query.page"
        v-model:size="query.size"
        :total="total"
        @change="load"
    /></el-card>
    <el-dialog
      v-model="createDialog"
      title="新建盘点单"
      width="800px"
      ><el-form label-position="top"
        ><el-row :gutter="12"
          ><el-col :span="8"
            ><el-form-item label="盘点单号"><el-input v-model="form.countNo" /></el-form-item></el-col
          ><el-col :span="8"
            ><el-form-item label="仓库"
              ><RemoteMasterDataSelect
                v-model="form.warehouseId"
                resource="warehouses" /></el-form-item></el-col
          ><el-col :span="8"
            ><el-form-item label="备注"><el-input v-model="form.remark" /></el-form-item></el-col
        ></el-row>
        <div class="line-editor-head">
          <strong>盘点维度</strong
          ><el-button
            link
            type="primary"
            @click="add"
            >添加</el-button
          >
        </div>
        <el-table
          :data="form.dimensions"
          border
          ><el-table-column
            type="index"
            width="50"
          /><el-table-column label="库位"
            ><template #default="s"
              ><RemoteLocationSelect
                v-model="s.row.locationId"
                :warehouse-id="form.warehouseId" /></template></el-table-column
          ><el-table-column label="SKU"
            ><template #default="s"
              ><RemoteMasterDataSelect
                v-model="s.row.skuId"
                resource="skus" /></template></el-table-column
          ><el-table-column width="70"
            ><template #default="s"
              ><el-button
                link
                type="danger"
                @click="form.dimensions.splice(s.$index, 1)"
                >删除</el-button
              ></template
            ></el-table-column
          ></el-table
        ></el-form
      ><template #footer
        ><el-button @click="createDialog = false">取消</el-button
        ><el-button
          type="primary"
          :loading="saving"
          @click="save"
          >创建</el-button
        ></template
      ></el-dialog
    >
    <el-dialog
      v-model="detailDialog"
      title="盘点详情"
      width="900px"
      ><el-table
        v-if="detail"
        :data="detail.lines"
        border
        ><el-table-column
          prop="lineNo"
          label="#"
          width="50" /><el-table-column label="库位"
          ><template #default="s"
            ><EntityRef
              resource="locations"
              :id="s.row.locationId" /></template></el-table-column
        ><el-table-column label="SKU"
          ><template #default="s"
            ><EntityRef
              resource="skus"
              :id="s.row.skuId" /></template></el-table-column
        ><el-table-column
          prop="snapshotActualQuantity"
          label="账面实际" /><el-table-column
          prop="snapshotFrozenQuantity"
          label="冻结" /><el-table-column
          prop="countedQuantity"
          label="实盘" /><el-table-column
          prop="differenceQuantity"
          label="差异" /><el-table-column
          prop="reason"
          label="原因" /></el-table></el-dialog
    ><el-dialog
      v-model="resultDialog"
      title="录入实盘结果"
      width="900px"
      ><el-alert
        title="所有明细均需录入，差异由后端计算"
        type="warning"
        :closable="false"
      /><el-table
        v-if="detail"
        :data="detail.lines"
        border
        style="margin-top: 12px"
        ><el-table-column
          prop="lineNo"
          label="#"
          width="50" /><el-table-column
          label="维度"
          min-width="200"
          ><template #default="s"
            ><EntityRef
              resource="locations"
              :id="s.row.locationId" />
            /
            <EntityRef
              resource="skus"
              :id="s.row.skuId" /></template></el-table-column
        ><el-table-column
          prop="snapshotActualQuantity"
          label="账面量" /><el-table-column label="实盘量"
          ><template #default="s"
            ><el-input-number
              v-model="s.row.countedQuantity"
              :min="0"
              :precision="4" /></template></el-table-column
        ><el-table-column label="原因"
          ><template #default="s"
            ><el-input
              v-model="s.row.reason"
              maxlength="255" /></template></el-table-column></el-table
      ><template #footer
        ><el-button @click="resultDialog = false">取消</el-button
        ><el-button
          type="primary"
          :loading="saving"
          @click="saveResults"
          >保存</el-button
        ></template
      ></el-dialog
    >
  </div>
</template>
