<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import { changeSkuStatus, createSku, pageSkus, updateSku } from '@/modules/master-data/api'
import PermissionGate from '@/shared/components/PermissionGate.vue'
import RemoteMasterDataSelect from '@/shared/components/RemoteMasterDataSelect.vue'
import ServerPagination from '@/shared/components/ServerPagination.vue'
import StatusTag from '@/shared/components/StatusTag.vue'
import type { DataStatus } from '@/shared/types/api'
import type { SkuRecord } from '@/modules/master-data/types'
import { useLiveSearch } from '@/shared/composables/useLiveSearch'

const loading = ref(false),
  dialogVisible = ref(false),
  submitLoading = ref(false)
const statusChangingId = ref<number>(),
  editing = ref<SkuRecord | null>(null),
  formRef = ref<FormInstance>()
const records = ref<SkuRecord[]>([]),
  total = ref(0)
const query = reactive<{
  page: number
  size: number
  categoryId?: number
  code: string
  name: string
  status?: DataStatus
}>({ page: 1, size: 20, code: '', name: '' })
const form = reactive<{ categoryId?: number; code: string; name: string; unit: string; remark: string }>({
  categoryId: undefined,
  code: '',
  name: '',
  unit: '',
  remark: '',
})
const rules: FormRules = {
  code: [
    { required: true, message: '请输入商品编码（SKU）', trigger: 'blur' },
    {
      pattern: /^[A-Za-z][A-Za-z0-9_-]{1,31}$/,
      message: '以字母开头，长度 2-32，仅限字母、数字、下划线或连字符',
      trigger: 'blur',
    },
  ],
  name: [
    { required: true, message: '请输入商品名称', trigger: 'blur' },
    { max: 100, message: '名称不能超过 100 个字符', trigger: 'blur' },
  ],
  unit: [
    { required: true, message: '请输入计量单位', trigger: 'blur' },
    { max: 20, message: '计量单位不能超过 20 个字符', trigger: 'blur' },
  ],
  remark: [{ max: 255, message: '备注不能超过 255 个字符', trigger: 'blur' }],
}
async function load() {
  loading.value = true
  try {
    const result = await pageSkus({ ...query, code: query.code || undefined, name: query.name || undefined })
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
  Object.assign(query, { page: 1, categoryId: undefined, code: '', name: '', status: undefined })
  cancelLiveSearch()
  void load()
}
function openCreate() {
  editing.value = null
  Object.assign(form, { categoryId: undefined, code: '', name: '', unit: '', remark: '' })
  dialogVisible.value = true
}
function openEdit(row: SkuRecord) {
  editing.value = row
  Object.assign(form, {
    categoryId: row.categoryId || undefined,
    code: row.code,
    name: row.name,
    unit: row.unit,
    remark: row.remark || '',
  })
  dialogVisible.value = true
}
async function submit() {
  if (submitLoading.value || !(await formRef.value?.validate().catch(() => false))) return
  submitLoading.value = true
  try {
    if (editing.value)
      await updateSku(editing.value.id, {
        categoryId: form.categoryId,
        name: form.name.trim(),
        unit: form.unit.trim(),
        remark: form.remark.trim(),
        version: editing.value.version,
      })
    else
      await createSku({
        categoryId: form.categoryId,
        code: form.code.trim(),
        name: form.name.trim(),
        unit: form.unit.trim(),
        remark: form.remark.trim(),
      })
    ElMessage.success(`商品${editing.value ? '修改' : '创建'}成功`)
    dialogVisible.value = false
    await load()
  } finally {
    submitLoading.value = false
  }
}
async function toggleStatus(row: SkuRecord) {
  if (statusChangingId.value) return
  const next: DataStatus = row.status === 'ENABLED' ? 'DISABLED' : 'ENABLED'
  try {
    await ElMessageBox.confirm(`确认${next === 'ENABLED' ? '启用' : '停用'}商品“${row.name}”吗？`, '状态变更确认', {
      type: 'warning',
    })
  } catch {
    return
  }
  statusChangingId.value = row.id
  try {
    await changeSkuStatus(row.id, next, row.version)
    ElMessage.success('状态已更新')
    await load()
  } finally {
    statusChangingId.value = undefined
  }
}
const cancelLiveSearch = useLiveSearch([() => query.code, () => query.name], search)
onMounted(load)
</script>
<template>
  <div class="page-stack">
    <div class="page-heading">
      <div>
        <p class="eyebrow">基础资料</p>
        <h1>商品管理（SKU）</h1>
        <p>维护商品档案、分类归属和计量单位</p>
      </div>
      <PermissionGate authority="MASTER_DATA_WRITE"
        ><el-button
          type="primary"
          @click="openCreate"
          >新增商品</el-button
        ></PermissionGate
      >
    </div>
    <el-card
      shadow="never"
      class="filter-card"
      ><el-form
        inline
        @submit.prevent="search"
        ><el-form-item label="商品分类"
          ><RemoteMasterDataSelect
            v-model="query.categoryId"
            resource="categories"
            placeholder="选择分类" /></el-form-item
        ><el-form-item label="商品编码"
          ><el-input
            v-model="query.code"
            clearable
            placeholder="输入商品编码" /></el-form-item
        ><el-form-item label="商品名称"
          ><el-input
            v-model="query.name"
            clearable
            placeholder="输入商品名称" /></el-form-item
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
          prop="code"
          label="商品编码（SKU）"
          min-width="140"
        /><el-table-column
          prop="name"
          label="商品名称"
          min-width="180"
        /><el-table-column
          prop="categoryName"
          label="商品分类"
          min-width="140"
          ><template #default="scope">{{ scope.row.categoryName || '未分类' }}</template></el-table-column
        ><el-table-column
          prop="unit"
          label="单位"
          width="90"
        /><el-table-column
          label="状态"
          width="90"
          ><template #default="scope"><StatusTag :status="scope.row.status" /></template></el-table-column
        ><el-table-column
          prop="remark"
          label="备注"
          min-width="160"
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
      :title="`${editing ? '编辑' : '新增'}商品`"
      width="540px"
      destroy-on-close
      @closed="formRef?.clearValidate()"
      ><el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        ><el-form-item
          label="商品编码（SKU）"
          prop="code"
          ><el-input
            v-model="form.code"
            :disabled="Boolean(editing)"
            maxlength="32" /></el-form-item
        ><el-form-item
          label="商品名称"
          prop="name"
          ><el-input
            v-model="form.name"
            maxlength="100"
            show-word-limit /></el-form-item
        ><el-row :gutter="16"
          ><el-col :span="14"
            ><el-form-item
              label="商品分类"
              prop="categoryId"
              ><RemoteMasterDataSelect
                v-model="form.categoryId"
                resource="categories"
                placeholder="可不选择分类" /></el-form-item></el-col
          ><el-col :span="10"
            ><el-form-item
              label="计量单位"
              prop="unit"
              ><el-input
                v-model="form.unit"
                maxlength="20"
                placeholder="如：件、箱、kg" /></el-form-item></el-col></el-row
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
