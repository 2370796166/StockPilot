<script setup lang="ts">
import {
  ElButton,
  ElCard,
  ElCol,
  ElDescriptions,
  ElDescriptionsItem,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElOption,
  ElRow,
  ElSelect,
  ElTable,
  ElTableColumn as TableColumn,
  vLoading,
} from 'element-plus'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/card/style/css'
import 'element-plus/es/components/col/style/css'
import 'element-plus/es/components/descriptions/style/css'
import 'element-plus/es/components/descriptions-item/style/css'
import 'element-plus/es/components/dialog/style/css'
import 'element-plus/es/components/form/style/css'
import 'element-plus/es/components/form-item/style/css'
import 'element-plus/es/components/input/style/css'
import 'element-plus/es/components/option/style/css'
import 'element-plus/es/components/row/style/css'
import 'element-plus/es/components/select/style/css'
import 'element-plus/es/components/table/style/css'
import 'element-plus/es/components/table-column/style/css'
import 'element-plus/es/components/loading/style/css'
import { isQuantity } from '@/shared/utils/quantity'
import { sourceFilters } from '@/shared/utils/source-filters'
import { useLatestRequest } from '@/shared/composables/useLatestRequest'
import { computed, onMounted, reactive, ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createDocument, getDocument, pageDocuments, transitionDocument, updateDocument } from '@/modules/documents/api'
import BusinessStatusTag from '@/shared/components/BusinessStatusTag.vue'
import DocumentLinesEditor from '@/shared/components/DocumentLinesEditor.vue'
import EntityRef from '@/shared/components/EntityRef.vue'
import PermissionGate from '@/shared/components/PermissionGate.vue'
import RemoteMasterDataSelect from '@/shared/components/RemoteMasterDataSelect.vue'
import ServerPagination from '@/shared/components/ServerPagination.vue'
import type {
  DocumentDetail,
  DocumentKind,
  DocumentLine,
  DocumentStatus,
  DocumentSummary,
} from '@/modules/documents/types'
import { useAuthStore } from '@/modules/auth/store'
import { useLiveSearch } from '@/shared/composables/useLiveSearch'

// 采购入库和销售出库共享单据页面骨架，但权限集合、状态动作和业务文案按单据类型分别计算。
const props = defineProps<{ kind: DocumentKind }>(),
  auth = useAuthStore()
const isPurchase = computed(() => props.kind === 'purchase'),
  title = computed(() => (isPurchase.value ? '采购入库单' : '销售出库单')),
  noLabel = computed(() => (isPurchase.value ? '入库单号' : '出库单号'))
const permissions = computed(() =>
  isPurchase.value
    ? {
        read: 'PURCHASE_RECEIPT_READ',
        write: 'PURCHASE_RECEIPT_WRITE',
        approve: 'PURCHASE_RECEIPT_APPROVE',
        complete: 'PURCHASE_RECEIPT_COMPLETE',
      }
    : {
        read: 'SALES_OUTBOUND_READ',
        write: 'SALES_OUTBOUND_WRITE',
        approve: 'SALES_OUTBOUND_APPROVE',
        complete: 'SALES_OUTBOUND_COMPLETE',
      },
)
const loading = ref(false),
  records = ref<DocumentSummary[]>([]),
  total = ref(0),
  dialogVisible = ref(false),
  detailVisible = ref(false),
  submitting = ref(false),
  actionId = ref<number>()
const editing = ref<DocumentDetail | null>(null),
  detail = ref<DocumentDetail | null>(null),
  formRef = ref<FormInstance>()
const query = reactive<{ page: number; size: number; no: string; warehouseId?: number; status?: DocumentStatus }>({
  page: 1,
  size: 20,
  no: sourceFilters().businessNo,
})
const form = reactive<{ no: string; warehouseId?: number; remark: string; lines: DocumentLine[] }>({
  no: '',
  warehouseId: undefined,
  remark: '',
  lines: [],
})
const rules: FormRules = {
  no: [
    { required: true, message: `请输入${noLabel.value}`, trigger: 'blur' },
    { pattern: /^[A-Za-z0-9_-]{2,64}$/, message: '长度 2-64，仅限字母、数字、下划线或连字符', trigger: 'blur' },
  ],
  warehouseId: [{ required: true, message: '请选择仓库', trigger: 'change' }],
  remark: [{ max: 255, message: '备注不能超过 255 个字符', trigger: 'blur' }],
}
const salesStatuses: DocumentStatus[] = ['DRAFT', 'RESERVED', 'APPROVED', 'COMPLETED', 'CANCELLED']
const listRequest = useLatestRequest()
async function load() {
  const sequence = listRequest.next()
  loading.value = true
  try {
    const result = await pageDocuments(props.kind, {
      page: query.page,
      size: query.size,
      no: query.no || undefined,
      warehouseId: query.warehouseId,
      status: query.status,
    })
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
function reset() {
  Object.assign(query, { page: 1, no: '', warehouseId: undefined, status: undefined })
  cancelLiveSearch()
  void load()
}
const documentRequest = useLatestRequest()
function openCreate() {
  documentRequest.invalidate()
  editing.value = null
  Object.assign(form, {
    no: '',
    warehouseId: undefined,
    remark: '',
    lines: [{ locationId: 0, skuId: 0, quantity: '1' }],
  })
  dialogVisible.value = true
}
async function openEdit(row: DocumentSummary) {
  const sequence = documentRequest.next()
  try {
    const item = await getDocument(props.kind, row.id)
    if (!documentRequest.isCurrent(sequence)) return
    editing.value = item
    Object.assign(form, {
      no: item.no,
      warehouseId: item.warehouseId,
      remark: item.remark || '',
      lines: item.lines.map((line) => ({ ...line })),
    })
    dialogVisible.value = true
  } catch {
    // Request errors are displayed by the shared interceptor.
  }
}
async function openDetail(row: DocumentSummary) {
  const sequence = documentRequest.next()
  try {
    const item = await getDocument(props.kind, row.id)
    if (!documentRequest.isCurrent(sequence)) return
    detail.value = item
    detailVisible.value = true
  } catch {
    // Request errors are displayed by the shared interceptor.
  }
}
// 保存草稿前同时校验单头和所有明细；编辑请求携带版本，由后端乐观锁负责最终并发控制。
async function submit() {
  if (submitting.value || !(await formRef.value?.validate().catch(() => false)) || !form.warehouseId) return
  if (!form.lines.length || form.lines.some((line) => !line.locationId || !line.skuId || !isQuantity(line.quantity))) {
    ElMessage.warning('请完整填写至少一条有效明细')
    return
  }
  submitting.value = true
  try {
    if (editing.value)
      await updateDocument(props.kind, editing.value.id, {
        version: editing.value.version,
        warehouseId: form.warehouseId,
        remark: form.remark.trim(),
        lines: form.lines,
      })
    else
      await createDocument(props.kind, {
        no: form.no.trim(),
        warehouseId: form.warehouseId,
        remark: form.remark.trim(),
        lines: form.lines,
      })
    ElMessage.success('单据已保存')
    dialogVisible.value = false
    await load()
  } finally {
    submitting.value = false
  }
}
// 动作按钮同时受当前单据状态和用户权限约束；后端状态机与方法授权仍是最终保障。
function actions(row: DocumentSummary) {
  const list: Array<{ action: string; label: string; authority: string; version: boolean }> = []
  if (row.status === 'DRAFT')
    list.push({
      action: isPurchase.value ? 'submit' : 'reserve',
      label: isPurchase.value ? '提交' : '冻结库存',
      authority: permissions.value.write,
      version: true,
    })
  if ((isPurchase.value && row.status === 'SUBMITTED') || (!isPurchase.value && row.status === 'RESERVED'))
    list.push({ action: 'approve', label: '审核', authority: permissions.value.approve, version: true })
  if (row.status === 'APPROVED')
    list.push({
      action: 'complete',
      label: isPurchase.value ? '确认入库' : '确认出库',
      authority: permissions.value.complete,
      version: false,
    })
  if (!isPurchase.value && ['RESERVED', 'APPROVED'].includes(row.status))
    list.push({ action: 'cancel', label: '取消并释放', authority: permissions.value.write, version: false })
  return list.filter((item) => auth.can(item.authority))
}
// 库存相关状态动作执行前二次确认，并以 actionId 阻止用户重复点击同一关键操作。
async function execute(row: DocumentSummary, item: ReturnType<typeof actions>[number]) {
  if (actionId.value) return
  actionId.value = row.id
  try {
    await ElMessageBox.confirm(`确认执行“${item.label}”吗？该操作将按后端状态机处理库存。`, '关键操作确认', {
      type: 'warning',
    })
    await transitionDocument(props.kind, row.id, item.action, item.version ? row.version : undefined)
    ElMessage.success(`${item.label}成功`)
    await load()
  } catch {
    // Confirmation cancellation is expected; HTTP errors are shown by the request interceptor.
  } finally {
    actionId.value = undefined
  }
}
const cancelLiveSearch = useLiveSearch([() => query.no], search)
onMounted(load)
const ElTableColumn = TableColumn<DocumentSummary>
const LineTableColumn = TableColumn<DocumentLine>
</script>
<template>
  <div class="page-stack">
    <div class="page-heading">
      <div>
        <p class="eyebrow">业务单据</p>
        <h1>{{ title }}</h1>
        <p>可用操作严格依据后端状态与当前权限</p>
      </div>
      <PermissionGate :authority="permissions.write"
        ><el-button
          type="primary"
          @click="openCreate"
          >新建{{ title }}</el-button
        ></PermissionGate
      >
    </div>
    <el-card
      shadow="never"
      class="filter-card"
      ><el-form
        inline
        @submit.prevent="search"
        ><el-form-item :label="noLabel"
          ><el-input
            v-model="query.no"
            clearable /></el-form-item
        ><el-form-item label="仓库"
          ><RemoteMasterDataSelect
            v-model="query.warehouseId"
            resource="warehouses" /></el-form-item
        ><el-form-item
          v-if="!isPurchase"
          label="状态"
          ><el-select
            v-model="query.status"
            clearable
            style="width: 130px"
            ><el-option
              v-for="status in salesStatuses"
              :key="status"
              :label="status"
              :value="status" /></el-select></el-form-item
        ><el-form-item
          ><el-button
            type="primary"
            native-type="submit"
            >查询</el-button
          ><el-button @click="reset">重置</el-button></el-form-item
        ></el-form
      ></el-card
    >
    <el-card
      shadow="never"
      class="table-card"
      ><el-table
        v-loading="loading"
        :data="records"
        row-key="id"
        ><el-table-column
          prop="no"
          :label="noLabel"
          min-width="160"
        /><el-table-column
          label="仓库"
          min-width="170"
          ><template #default="scope"
            ><EntityRef
              resource="warehouses"
              :id="scope.row.warehouseId" /></template></el-table-column
        ><el-table-column
          label="状态"
          width="105"
          ><template #default="scope"><BusinessStatusTag :status="scope.row.status" /></template></el-table-column
        ><el-table-column
          prop="createdByName"
          label="制单人"
          width="120"
        /><el-table-column
          prop="createdAt"
          label="创建时间"
          min-width="165"
        /><el-table-column
          label="操作"
          min-width="260"
          fixed="right"
          ><template #default="scope"
            ><el-button
              link
              type="primary"
              @click="openDetail(scope.row)"
              >详情</el-button
            ><el-button
              v-if="scope.row.status === 'DRAFT' && auth.can(permissions.write)"
              link
              type="primary"
              @click="openEdit(scope.row)"
              >编辑</el-button
            ><el-button
              v-for="item in actions(scope.row)"
              :key="item.action"
              link
              :type="item.action === 'cancel' ? 'danger' : 'primary'"
              :loading="actionId === scope.row.id"
              @click="execute(scope.row, item)"
              >{{ item.label }}</el-button
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
      v-model="dialogVisible"
      :title="`${editing ? '编辑' : '新建'}${title}`"
      width="860px"
      destroy-on-close
      ><el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        ><el-row :gutter="16"
          ><el-col :span="8"
            ><el-form-item
              :label="noLabel"
              prop="no"
              ><el-input
                v-model="form.no"
                :disabled="Boolean(editing)"
                maxlength="64" /></el-form-item></el-col
          ><el-col :span="8"
            ><el-form-item
              label="仓库"
              prop="warehouseId"
              ><RemoteMasterDataSelect
                v-model="form.warehouseId"
                resource="warehouses" /></el-form-item></el-col
          ><el-col :span="8"
            ><el-form-item
              label="备注"
              prop="remark"
              ><el-input
                v-model="form.remark"
                maxlength="255" /></el-form-item></el-col></el-row
        ><DocumentLinesEditor
          v-model="form.lines"
          :warehouse-id="form.warehouseId" /></el-form
      ><template #footer
        ><el-button
          :disabled="submitting"
          @click="dialogVisible = false"
          >取消</el-button
        ><el-button
          type="primary"
          :loading="submitting"
          @click="submit"
          >保存</el-button
        ></template
      ></el-dialog
    >
    <el-dialog
      v-model="detailVisible"
      :title="`${title}详情`"
      width="820px"
      ><template v-if="detail"
        ><el-descriptions
          :column="3"
          border
          ><el-descriptions-item :label="noLabel">{{ detail.no }}</el-descriptions-item
          ><el-descriptions-item label="仓库"
            ><EntityRef
              resource="warehouses"
              :id="detail.warehouseId" /></el-descriptions-item
          ><el-descriptions-item label="状态"><BusinessStatusTag :status="detail.status" /></el-descriptions-item
          ><el-descriptions-item
            label="备注"
            :span="3"
            >{{ detail.remark || '—' }}</el-descriptions-item
          ></el-descriptions
        ><el-table
          :data="detail.lines"
          border
          style="margin-top: 16px"
          ><LineTableColumn
            prop="lineNo"
            label="行号"
            width="70" /><LineTableColumn label="库位"
            ><template #default="scope"
              ><EntityRef
                resource="locations"
                :id="scope.row.locationId" /></template></LineTableColumn
          ><LineTableColumn label="SKU"
            ><template #default="scope"
              ><EntityRef
                resource="skus"
                :id="scope.row.skuId" /></template></LineTableColumn
          ><LineTableColumn
            prop="quantity"
            label="数量" /></el-table></template
    ></el-dialog>
  </div>
</template>
