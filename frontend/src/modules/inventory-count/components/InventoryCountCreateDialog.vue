<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { createCount } from '@/modules/inventory-count/api'
import RemoteLocationSelect from '@/shared/components/RemoteLocationSelect.vue'
import RemoteMasterDataSelect from '@/shared/components/RemoteMasterDataSelect.vue'

const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; saved: [] }>()
const saving = ref(false)
const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
})
const form = reactive<{
  countNo: string
  warehouseId?: number
  remark: string
  dimensions: Array<{ locationId: number; skuId: number }>
}>({ countNo: '', remark: '', dimensions: [] })

watch(
  () => props.modelValue,
  (open) => {
    if (open) {
      Object.assign(form, {
        countNo: '',
        warehouseId: undefined,
        remark: '',
        dimensions: [{ locationId: 0, skuId: 0 }],
      })
    }
  },
)

async function save() {
  const warehouseId = form.warehouseId
  if (
    saving.value ||
    !form.countNo.match(/^[A-Za-z0-9_-]{2,64}$/) ||
    !warehouseId ||
    !form.dimensions.length ||
    form.dimensions.some((dimension) => !dimension.locationId || !dimension.skuId)
  ) {
    ElMessage.warning('请完整填写盘点单号、仓库和盘点维度')
    return
  }
  saving.value = true
  try {
    await createCount({ countNo: form.countNo, warehouseId, remark: form.remark, dimensions: form.dimensions })
    ElMessage.success('盘点单已创建并锁定维度')
    visible.value = false
    emit('saved')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <el-dialog
    v-model="visible"
    title="新建盘点单"
    width="800px"
  >
    <el-form label-position="top">
      <el-row :gutter="12">
        <el-col :span="8"
          ><el-form-item label="盘点单号"><el-input v-model="form.countNo" /></el-form-item
        ></el-col>
        <el-col :span="8">
          <el-form-item label="仓库"
            ><RemoteMasterDataSelect
              v-model="form.warehouseId"
              resource="warehouses"
          /></el-form-item>
        </el-col>
        <el-col :span="8"
          ><el-form-item label="备注"><el-input v-model="form.remark" /></el-form-item
        ></el-col>
      </el-row>
      <div class="line-editor-head">
        <strong>盘点维度</strong>
        <el-button
          link
          type="primary"
          @click="form.dimensions.push({ locationId: 0, skuId: 0 })"
          >添加</el-button
        >
      </div>
      <el-table
        :data="form.dimensions"
        border
      >
        <el-table-column
          type="index"
          width="50"
        />
        <el-table-column label="库位">
          <template #default="scope">
            <RemoteLocationSelect
              v-model="scope.row.locationId"
              :warehouse-id="form.warehouseId"
            />
          </template>
        </el-table-column>
        <el-table-column label="SKU">
          <template #default="scope"
            ><RemoteMasterDataSelect
              v-model="scope.row.skuId"
              resource="skus"
          /></template>
        </el-table-column>
        <el-table-column width="70">
          <template #default="scope">
            <el-button
              link
              type="danger"
              @click="form.dimensions.splice(scope.$index, 1)"
              >删除</el-button
            >
          </template>
        </el-table-column>
      </el-table>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button
        type="primary"
        :loading="saving"
        @click="save"
        >创建</el-button
      >
    </template>
  </el-dialog>
</template>
