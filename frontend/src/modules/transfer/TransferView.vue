<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createTransfer, getTransfer, pageTransfers, transitionTransfer, updateTransfer } from '@/modules/transfer/api'
import BusinessStatusTag from '@/shared/components/BusinessStatusTag.vue'
import EntityRef from '@/shared/components/EntityRef.vue'
import PermissionGate from '@/shared/components/PermissionGate.vue'
import RemoteLocationSelect from '@/shared/components/RemoteLocationSelect.vue'
import RemoteMasterDataSelect from '@/shared/components/RemoteMasterDataSelect.vue'
import ServerPagination from '@/shared/components/ServerPagination.vue'
import { useLiveSearch } from '@/shared/composables/useLiveSearch'
import { useAuthStore } from '@/modules/auth/store'
import type { TransferDetail, TransferLine, TransferStatus, TransferSummary } from '@/modules/documents/types'
const auth = useAuthStore(),
  loading = ref(false),
  records = ref<TransferSummary[]>([]),
  total = ref(0),
  dialog = ref(false),
  detailDialog = ref(false),
  saving = ref(false),
  acting = ref<number>(),
  editing = ref<TransferDetail | null>(null),
  detail = ref<TransferDetail | null>(null)
const query = reactive<{
  page: number
  size: number
  transferNo: string
  sourceWarehouseId?: number
  targetWarehouseId?: number
  status?: TransferStatus
}>({ page: 1, size: 20, transferNo: '' })
const form = reactive<{
  transferNo: string
  sourceWarehouseId?: number
  targetWarehouseId?: number
  remark: string
  lines: TransferLine[]
}>({ transferNo: '', remark: '', lines: [] })
const statuses: TransferStatus[] = [
  'DRAFT',
  'SUBMITTED',
  'APPROVED',
  'OUTBOUND_COMPLETED',
  'IN_TRANSIT',
  'COMPLETED',
  'CANCELLED',
]
async function load() {
  loading.value = true
  try {
    const r = await pageTransfers({ ...query, transferNo: query.transferNo || undefined })
    records.value = r.records
    total.value = r.total
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
    transferNo: '',
    sourceWarehouseId: undefined,
    targetWarehouseId: undefined,
    status: undefined,
  })
  cancelLiveSearch()
  void load()
}
function addLine() {
  form.lines.push({ sourceLocationId: 0, targetLocationId: 0, skuId: 0, quantity: 1 })
}
function removeLine(i: number) {
  form.lines.splice(i, 1)
}
function create() {
  editing.value = null
  Object.assign(form, {
    transferNo: '',
    sourceWarehouseId: undefined,
    targetWarehouseId: undefined,
    remark: '',
    lines: [],
  })
  addLine()
  dialog.value = true
}
async function edit(row: TransferSummary) {
  const d = await getTransfer(row.id)
  editing.value = d
  Object.assign(form, {
    transferNo: d.transferNo,
    sourceWarehouseId: d.sourceWarehouseId,
    targetWarehouseId: d.targetWarehouseId,
    remark: d.remark || '',
    lines: d.lines.map((x) => ({ ...x })),
  })
  dialog.value = true
}
async function show(row: TransferSummary) {
  detail.value = await getTransfer(row.id)
  detailDialog.value = true
}
async function save() {
  if (
    saving.value ||
    !form.transferNo.match(/^[A-Za-z0-9_-]{2,64}$/) ||
    !form.sourceWarehouseId ||
    !form.targetWarehouseId ||
    form.sourceWarehouseId === form.targetWarehouseId ||
    !form.lines.length ||
    form.lines.some((x) => !x.sourceLocationId || !x.targetLocationId || !x.skuId || x.quantity <= 0)
  ) {
    ElMessage.warning('请完整填写单号、不同的源/目标仓库及有效明细')
    return
  }
  saving.value = true
  try {
    if (editing.value)
      await updateTransfer(editing.value.id, {
        version: editing.value.version,
        sourceWarehouseId: form.sourceWarehouseId,
        targetWarehouseId: form.targetWarehouseId,
        remark: form.remark,
        lines: form.lines,
      })
    else
      await createTransfer({
        transferNo: form.transferNo,
        sourceWarehouseId: form.sourceWarehouseId,
        targetWarehouseId: form.targetWarehouseId,
        remark: form.remark,
        lines: form.lines,
      })
    ElMessage.success('调拨单已保存')
    dialog.value = false
    await load()
  } finally {
    saving.value = false
  }
}
function actions(r: TransferSummary) {
  const a = [] as Array<{ key: string; label: string; perm: string; version: boolean }>
  if (r.status === 'DRAFT') a.push({ key: 'submit', label: '提交并冻结', perm: 'TRANSFER_WRITE', version: true })
  if (r.status === 'SUBMITTED') a.push({ key: 'approve', label: '审核', perm: 'TRANSFER_APPROVE', version: true })
  if (r.status === 'APPROVED') a.push({ key: 'dispatch', label: '确认调出', perm: 'TRANSFER_OUTBOUND', version: false })
  if (r.status === 'OUTBOUND_COMPLETED')
    a.push({ key: 'start-transit', label: '开始运输', perm: 'TRANSFER_OUTBOUND', version: true })
  if (r.status === 'IN_TRANSIT') a.push({ key: 'receive', label: '确认收货', perm: 'TRANSFER_INBOUND', version: false })
  if (['SUBMITTED', 'APPROVED'].includes(r.status))
    a.push({ key: 'cancel', label: '取消并释放', perm: 'TRANSFER_WRITE', version: false })
  return a.filter((x) => auth.can(x.perm))
}
async function execute(r: TransferSummary, a: ReturnType<typeof actions>[number]) {
  try {
    await ElMessageBox.confirm(`确认执行“${a.label}”吗？`, '关键操作确认', { type: 'warning' })
  } catch {
    return
  }
  acting.value = r.id
  try {
    await transitionTransfer(r.id, a.key, a.version ? r.version : undefined)
    ElMessage.success(`${a.label}成功`)
    await load()
  } finally {
    acting.value = undefined
  }
}
const cancelLiveSearch = useLiveSearch([() => query.transferNo], search)
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
      <PermissionGate authority="TRANSFER_WRITE"
        ><el-button
          type="primary"
          @click="create"
          >新建调拨单</el-button
        ></PermissionGate
      >
    </div>
    <el-card
      shadow="never"
      class="filter-card"
      ><el-form
        inline
        @submit.prevent="search"
        ><el-form-item label="调拨单号"><el-input v-model="query.transferNo" /></el-form-item
        ><el-form-item label="源仓"
          ><RemoteMasterDataSelect
            v-model="query.sourceWarehouseId"
            resource="warehouses" /></el-form-item
        ><el-form-item label="目标仓"
          ><RemoteMasterDataSelect
            v-model="query.targetWarehouseId"
            resource="warehouses" /></el-form-item
        ><el-form-item label="状态"
          ><el-select
            v-model="query.status"
            clearable
            style="width: 140px"
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
          prop="transferNo"
          label="调拨单号"
          min-width="160"
        /><el-table-column
          label="源仓"
          min-width="150"
          ><template #default="s"
            ><EntityRef
              resource="warehouses"
              :id="s.row.sourceWarehouseId" /></template></el-table-column
        ><el-table-column
          label="目标仓"
          min-width="150"
          ><template #default="s"
            ><EntityRef
              resource="warehouses"
              :id="s.row.targetWarehouseId" /></template></el-table-column
        ><el-table-column
          label="状态"
          width="110"
          ><template #default="s"><BusinessStatusTag :status="s.row.status" /></template></el-table-column
        ><el-table-column
          prop="createdAt"
          label="创建时间"
          min-width="165"
        /><el-table-column
          label="操作"
          min-width="300"
          ><template #default="s"
            ><el-button
              link
              type="primary"
              @click="show(s.row)"
              >详情</el-button
            ><el-button
              v-if="s.row.status === 'DRAFT' && auth.can('TRANSFER_WRITE')"
              link
              type="primary"
              @click="edit(s.row)"
              >编辑</el-button
            ><el-button
              v-for="a in actions(s.row)"
              :key="a.key"
              link
              :type="a.key === 'cancel' ? 'danger' : 'primary'"
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
      v-model="dialog"
      :title="`${editing ? '编辑' : '新建'}调拨单`"
      width="980px"
      ><el-form label-position="top"
        ><el-row :gutter="12"
          ><el-col :span="6"
            ><el-form-item label="调拨单号"
              ><el-input
                v-model="form.transferNo"
                :disabled="!!editing"
                maxlength="64" /></el-form-item></el-col
          ><el-col :span="6"
            ><el-form-item label="源仓"
              ><RemoteMasterDataSelect
                v-model="form.sourceWarehouseId"
                resource="warehouses" /></el-form-item></el-col
          ><el-col :span="6"
            ><el-form-item label="目标仓"
              ><RemoteMasterDataSelect
                v-model="form.targetWarehouseId"
                resource="warehouses" /></el-form-item></el-col
          ><el-col :span="6"
            ><el-form-item label="备注"
              ><el-input
                v-model="form.remark"
                maxlength="255" /></el-form-item></el-col
        ></el-row>
        <div class="line-editor-head">
          <strong>调拨明细</strong
          ><el-button
            link
            type="primary"
            @click="addLine"
            >添加明细</el-button
          >
        </div>
        <el-table
          :data="form.lines"
          border
          ><el-table-column
            type="index"
            width="50"
          /><el-table-column label="源库位"
            ><template #default="s"
              ><RemoteLocationSelect
                v-model="s.row.sourceLocationId"
                :warehouse-id="form.sourceWarehouseId" /></template></el-table-column
          ><el-table-column label="目标库位"
            ><template #default="s"
              ><RemoteLocationSelect
                v-model="s.row.targetLocationId"
                :warehouse-id="form.targetWarehouseId" /></template></el-table-column
          ><el-table-column label="SKU"
            ><template #default="s"
              ><RemoteMasterDataSelect
                v-model="s.row.skuId"
                resource="skus" /></template></el-table-column
          ><el-table-column
            label="数量"
            width="150"
            ><template #default="s"
              ><el-input-number
                v-model="s.row.quantity"
                :min="0.0001"
                :precision="4"
                style="width: 100%" /></template></el-table-column
          ><el-table-column width="70"
            ><template #default="s"
              ><el-button
                link
                type="danger"
                @click="removeLine(s.$index)"
                >删除</el-button
              ></template
            ></el-table-column
          ></el-table
        ></el-form
      ><template #footer
        ><el-button @click="dialog = false">取消</el-button
        ><el-button
          type="primary"
          :loading="saving"
          @click="save"
          >保存</el-button
        ></template
      ></el-dialog
    >
    <el-dialog
      v-model="detailDialog"
      title="调拨详情"
      width="900px"
      ><template v-if="detail"
        ><el-descriptions
          :column="3"
          border
          ><el-descriptions-item label="单号">{{ detail.transferNo }}</el-descriptions-item
          ><el-descriptions-item label="状态"><BusinessStatusTag :status="detail.status" /></el-descriptions-item
          ><el-descriptions-item label="备注">{{ detail.remark || '—' }}</el-descriptions-item></el-descriptions
        ><el-table
          :data="detail.lines"
          border
          style="margin-top: 16px"
          ><el-table-column
            prop="lineNo"
            label="#"
            width="55" /><el-table-column label="源库位"
            ><template #default="s"
              ><EntityRef
                resource="locations"
                :id="s.row.sourceLocationId" /></template></el-table-column
          ><el-table-column label="目标库位"
            ><template #default="s"
              ><EntityRef
                resource="locations"
                :id="s.row.targetLocationId" /></template></el-table-column
          ><el-table-column label="SKU"
            ><template #default="s"
              ><EntityRef
                resource="skus"
                :id="s.row.skuId" /></template></el-table-column
          ><el-table-column
            prop="quantity"
            label="数量" /></el-table></template
    ></el-dialog>
  </div>
</template>
