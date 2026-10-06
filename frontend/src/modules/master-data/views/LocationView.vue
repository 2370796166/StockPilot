<script setup lang="ts">
import {
  ElButton,
  ElCard,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn as TableColumn,
  vLoading,
} from 'element-plus'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/card/style/css'
import 'element-plus/es/components/dialog/style/css'
import 'element-plus/es/components/form/style/css'
import 'element-plus/es/components/form-item/style/css'
import 'element-plus/es/components/input/style/css'
import 'element-plus/es/components/option/style/css'
import 'element-plus/es/components/select/style/css'
import 'element-plus/es/components/table/style/css'
import 'element-plus/es/components/table-column/style/css'
import 'element-plus/es/components/loading/style/css'
import { useLatestRequest } from '@/shared/composables/useLatestRequest'
import { onMounted, reactive, ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import { changeLocationStatus, createLocation, pageLocations, updateLocation } from '@/modules/master-data/location-api'
import PermissionGate from '@/shared/components/PermissionGate.vue'
import RemoteMasterDataSelect from '@/shared/components/RemoteMasterDataSelect.vue'
import ServerPagination from '@/shared/components/ServerPagination.vue'
import StatusTag from '@/shared/components/StatusTag.vue'
import type { DataStatus } from '@/shared/types/api'
import type { LocationRecord } from '@/modules/master-data/types'
import { useLiveSearch } from '@/shared/composables/useLiveSearch'

const loading = ref(false),
  dialogVisible = ref(false),
  submitLoading = ref(false)
const statusChangingId = ref<number>(),
  editing = ref<LocationRecord | null>(null),
  formRef = ref<FormInstance>()
const records = ref<LocationRecord[]>([]),
  total = ref(0)
const query = reactive<{
  page: number
  size: number
  warehouseId?: number
  code: string
  name: string
  status?: DataStatus
}>({ page: 1, size: 20, code: '', name: '' })
const form = reactive<{ warehouseId?: number; code: string; name: string; remark: string }>({
  warehouseId: undefined,
  code: '',
  name: '',
  remark: '',
})
const rules: FormRules = {
  warehouseId: [{ required: true, message: '请选择所属仓库', trigger: 'change' }],
  code: [
    { required: true, message: '请输入编码', trigger: 'blur' },
    {
      pattern: /^[A-Za-z][A-Za-z0-9_-]{1,31}$/,
      message: '以字母开头，长度 2-32，仅限字母、数字、下划线或连字符',
      trigger: 'blur',
    },
  ],
  name: [
    { required: true, message: '请输入名称', trigger: 'blur' },
    { max: 100, message: '名称不能超过 100 个字符', trigger: 'blur' },
  ],
  remark: [{ max: 255, message: '备注不能超过 255 个字符', trigger: 'blur' }],
}
const listRequest = useLatestRequest()
async function load() {
  const sequence = listRequest.next()
  loading.value = true
  try {
    const result = await pageLocations({ ...query, code: query.code || undefined, name: query.name || undefined })
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
  Object.assign(query, { page: 1, warehouseId: undefined, code: '', name: '', status: undefined })
  cancelLiveSearch()
  void load()
}
function openCreate() {
  editing.value = null
  Object.assign(form, { warehouseId: undefined, code: '', name: '', remark: '' })
  dialogVisible.value = true
}
function openEdit(row: LocationRecord) {
  editing.value = row
  Object.assign(form, { warehouseId: row.warehouseId, code: row.code, name: row.name, remark: row.remark || '' })
  dialogVisible.value = true
}
async function submit() {
  if (submitLoading.value || !(await formRef.value?.validate().catch(() => false)) || !form.warehouseId) return
  submitLoading.value = true
  try {
    if (editing.value)
      await updateLocation(editing.value.id, {
        warehouseId: form.warehouseId,
        name: form.name.trim(),
        remark: form.remark.trim(),
        version: editing.value.version,
      })
    else
      await createLocation({
        warehouseId: form.warehouseId,
        code: form.code.trim(),
        name: form.name.trim(),
        remark: form.remark.trim(),
      })
    ElMessage.success(`库位${editing.value ? '修改' : '创建'}成功`)
    dialogVisible.value = false
    await load()
  } finally {
    submitLoading.value = false
  }
}
async function toggleStatus(row: LocationRecord) {
  if (statusChangingId.value) return
  const next: DataStatus = row.status === 'ENABLED' ? 'DISABLED' : 'ENABLED'
  try {
    await ElMessageBox.confirm(`确认${next === 'ENABLED' ? '启用' : '停用'}库位“${row.name}”吗？`, '状态变更确认', {
      type: 'warning',
    })
  } catch {
    return
  }
  statusChangingId.value = row.id
  try {
    await changeLocationStatus(row.id, next, row.version)
    ElMessage.success('状态已更新')
    await load()
  } finally {
    statusChangingId.value = undefined
  }
}
const cancelLiveSearch = useLiveSearch([() => query.code, () => query.name], search)
onMounted(load)
const ElTableColumn = TableColumn<LocationRecord>
</script>
<template>
  <div class="page-stack">
    <div class="page-heading">
      <div>
        <p class="eyebrow">基础资料</p>
        <h1>库位管理</h1>
        <p>库位始终归属于一个真实仓库</p>
      </div>
      <PermissionGate authority="MASTER_DATA_WRITE"
        ><el-button
          type="primary"
          @click="openCreate"
          >新增库位</el-button
        ></PermissionGate
      >
    </div>
    <el-card
      shadow="never"
      class="filter-card"
      ><el-form
        inline
        @submit.prevent="search"
        ><el-form-item label="仓库"
          ><RemoteMasterDataSelect
            v-model="query.warehouseId"
            resource="warehouses"
            placeholder="选择仓库" /></el-form-item
        ><el-form-item label="编码"
          ><el-input
            v-model="query.code"
            clearable
            placeholder="输入编码" /></el-form-item
        ><el-form-item label="名称"
          ><el-input
            v-model="query.name"
            clearable
            placeholder="输入名称" /></el-form-item
        ><el-form-item label="状态"
          ><el-select
            v-model="query.status"
            clearable
            placeholder="全部"
            style="width: 110px"
            ><el-option
              label="启用"
              value="ENABLED" /><el-option
              label="停用"
              value="DISABLED" /></el-select></el-form-item
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
          prop="warehouseCode"
          label="仓库编码"
          min-width="130"
        /><el-table-column
          prop="code"
          label="库位编码"
          min-width="140"
        /><el-table-column
          prop="name"
          label="库位名称"
          min-width="160"
        /><el-table-column
          label="状态"
          width="90"
          ><template #default="scope"><StatusTag :status="scope.row.status" /></template></el-table-column
        ><el-table-column
          prop="remark"
          label="备注"
          min-width="180"
          show-overflow-tooltip
          ><template #default="scope">{{ scope.row.remark || '—' }}</template></el-table-column
        ><el-table-column
          label="操作"
          width="170"
          fixed="right"
          ><template #default="scope"
            ><PermissionGate authority="MASTER_DATA_WRITE"
              ><el-button
                link
                type="primary"
                @click="openEdit(scope.row)"
                >编辑</el-button
              ><el-button
                link
                :type="scope.row.status === 'ENABLED' ? 'danger' : 'success'"
                :loading="statusChangingId === scope.row.id"
                @click="toggleStatus(scope.row)"
                >{{ scope.row.status === 'ENABLED' ? '停用' : '启用' }}</el-button
              ></PermissionGate
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
      :title="`${editing ? '编辑' : '新增'}库位`"
      width="520px"
      destroy-on-close
      @closed="formRef?.clearValidate()"
      ><el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        ><el-form-item
          label="所属仓库"
          prop="warehouseId"
          ><RemoteMasterDataSelect
            v-model="form.warehouseId"
            resource="warehouses"
            placeholder="请选择所属仓库" /></el-form-item
        ><el-form-item
          label="库位编码"
          prop="code"
          ><el-input
            v-model="form.code"
            :disabled="Boolean(editing)"
            maxlength="32" /></el-form-item
        ><el-form-item
          label="库位名称"
          prop="name"
          ><el-input
            v-model="form.name"
            maxlength="100"
            show-word-limit /></el-form-item
        ><el-form-item
          label="备注"
          prop="remark"
          ><el-input
            v-model="form.remark"
            type="textarea"
            :rows="3"
            maxlength="255"
            show-word-limit /></el-form-item></el-form
      ><template #footer
        ><el-button
          :disabled="submitLoading"
          @click="dialogVisible = false"
          >取消</el-button
        ><el-button
          type="primary"
          :loading="submitLoading"
          @click="submit"
          >保存</el-button
        ></template
      ></el-dialog
    >
  </div>
</template>
