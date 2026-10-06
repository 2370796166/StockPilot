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
import { changeMasterDataStatus, createMasterData, pageMasterData, updateMasterData } from '@/modules/master-data/api'
import type { DataStatus } from '@/shared/types/api'
import type { MasterDataRecord, MasterDataResource } from '@/modules/master-data/types'
import PermissionGate from './PermissionGate.vue'
import ServerPagination from './ServerPagination.vue'
import StatusTag from './StatusTag.vue'
import { useLiveSearch } from '@/shared/composables/useLiveSearch'

// 仓库、分类和供应商等标准基础资料共用本页，资源类型决定调用的后端端点。
const props = defineProps<{ title: string; noun: string; resource: MasterDataResource }>()
const loading = ref(false)
const records = ref<MasterDataRecord[]>([])
const total = ref(0)
const query = reactive<{ page: number; size: number; code: string; name: string; status?: DataStatus }>({
  page: 1,
  size: 20,
  code: '',
  name: '',
})
const dialogVisible = ref(false)
const editing = ref<MasterDataRecord | null>(null)
const formRef = ref<FormInstance>()
const submitLoading = ref(false)
const statusChangingId = ref<number>()
const form = reactive({ code: '', name: '', remark: '' })
const rules: FormRules = {
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
    const result = await pageMasterData(props.resource, {
      page: query.page,
      size: query.size,
      code: query.code || undefined,
      name: query.name || undefined,
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
  query.code = ''
  query.name = ''
  query.status = undefined
  cancelLiveSearch()
  query.page = 1
  void load()
}
function openCreate() {
  editing.value = null
  Object.assign(form, { code: '', name: '', remark: '' })
  dialogVisible.value = true
}
function openEdit(row: MasterDataRecord) {
  editing.value = row
  Object.assign(form, { code: row.code, name: row.name, remark: row.remark || '' })
  dialogVisible.value = true
}
// 创建时提交稳定编码；编辑时编码不可修改并携带当前版本，防止覆盖其他管理员的更新。
async function submit() {
  if (submitLoading.value || !(await formRef.value?.validate().catch(() => false))) return
  submitLoading.value = true
  try {
    if (editing.value)
      await updateMasterData(props.resource, editing.value.id, {
        name: form.name.trim(),
        remark: form.remark.trim(),
        version: editing.value.version,
      })
    else
      await createMasterData(props.resource, {
        code: form.code.trim(),
        name: form.name.trim(),
        remark: form.remark.trim(),
      })
    ElMessage.success(`${props.noun}${editing.value ? '修改' : '创建'}成功`)
    dialogVisible.value = false
    await load()
  } finally {
    submitLoading.value = false
  }
}
// 基础资料只允许启停而不提供删除；状态变更前二次确认并阻止重复提交。
async function toggleStatus(row: MasterDataRecord) {
  if (statusChangingId.value) return
  const next: DataStatus = row.status === 'ENABLED' ? 'DISABLED' : 'ENABLED'
  try {
    await ElMessageBox.confirm(
      `确认${next === 'ENABLED' ? '启用' : '停用'}${props.noun}“${row.name}”吗？`,
      '状态变更确认',
      { type: 'warning', confirmButtonText: '确认', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  statusChangingId.value = row.id
  try {
    await changeMasterDataStatus(props.resource, row.id, next, row.version)
    ElMessage.success('状态已更新')
    await load()
  } finally {
    statusChangingId.value = undefined
  }
}
const cancelLiveSearch = useLiveSearch([() => query.code, () => query.name], search)
onMounted(load)
const ElTableColumn = TableColumn<MasterDataRecord>
</script>
<template>
  <div class="page-stack">
    <div class="page-heading">
      <div>
        <p class="eyebrow">基础资料</p>
        <h1>{{ title }}</h1>
        <p>数据由 StockPilot 后端接口实时提供</p>
      </div>
      <PermissionGate authority="MASTER_DATA_WRITE"
        ><el-button
          type="primary"
          @click="openCreate"
          >新增{{ noun }}</el-button
        ></PermissionGate
      >
    </div>
    <el-card
      shadow="never"
      class="filter-card"
      ><el-form
        inline
        @submit.prevent="search"
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
            style="width: 120px"
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
    >
      <el-table
        v-loading="loading"
        :data="records"
        row-key="id"
        ><el-table-column
          prop="code"
          label="编码"
          min-width="150"
        /><el-table-column
          prop="name"
          label="名称"
          min-width="180"
        /><el-table-column
          label="状态"
          width="100"
          ><template #default="scope"><StatusTag :status="scope.row.status" /></template></el-table-column
        ><el-table-column
          prop="remark"
          label="备注"
          min-width="220"
          show-overflow-tooltip
          ><template #default="scope">{{ scope.row.remark || '—' }}</template></el-table-column
        ><el-table-column
          label="操作"
          width="180"
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
      >
      <ServerPagination
        v-model:page="query.page"
        v-model:size="query.size"
        :total="total"
        @change="load"
      />
    </el-card>
    <el-dialog
      v-model="dialogVisible"
      :title="`${editing ? '编辑' : '新增'}${noun}`"
      width="520px"
      destroy-on-close
      @closed="formRef?.clearValidate()"
      ><el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        ><el-form-item
          label="编码"
          prop="code"
          ><el-input
            v-model="form.code"
            :disabled="Boolean(editing)"
            maxlength="32" /></el-form-item
        ><el-form-item
          label="名称"
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
