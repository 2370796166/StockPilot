<script setup lang="ts">
import { ElButton, ElCol, ElDialog, ElForm, ElFormItem, ElInput, ElRow, ElTable, ElTableColumn } from 'element-plus'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/col/style/css'
import 'element-plus/es/components/dialog/style/css'
import 'element-plus/es/components/form/style/css'
import 'element-plus/es/components/form-item/style/css'
import 'element-plus/es/components/input/style/css'
import 'element-plus/es/components/row/style/css'
import 'element-plus/es/components/table/style/css'
import 'element-plus/es/components/table-column/style/css'
import QuantityInput from '@/shared/components/QuantityInput.vue'
import { isQuantity } from '@/shared/utils/quantity'
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { createTransfer, updateTransfer } from '@/modules/transfer/api'
import type { TransferDetail, TransferLine } from '@/modules/transfer/types'
import RemoteLocationSelect from '@/shared/components/RemoteLocationSelect.vue'
import RemoteMasterDataSelect from '@/shared/components/RemoteMasterDataSelect.vue'

const props = defineProps<{ modelValue: boolean; editing: TransferDetail | null }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; saved: [] }>()

const saving = ref(false)
const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
})
const form = reactive<{
  transferNo: string
  sourceWarehouseId?: number
  targetWarehouseId?: number
  remark: string
  lines: TransferLine[]
}>({ transferNo: '', remark: '', lines: [] })

function addLine() {
  form.lines.push({ sourceLocationId: 0, targetLocationId: 0, skuId: 0, quantity: '1' })
}

function resetForm() {
  const editing = props.editing
  Object.assign(form, {
    transferNo: editing?.transferNo ?? '',
    sourceWarehouseId: editing?.sourceWarehouseId,
    targetWarehouseId: editing?.targetWarehouseId,
    remark: editing?.remark ?? '',
    lines: editing ? editing.lines.map((line) => ({ ...line })) : [],
  })
  if (!form.lines.length) addLine()
}

watch(
  () => props.modelValue,
  (open) => {
    if (open) resetForm()
  },
)

async function save() {
  const sourceWarehouseId = form.sourceWarehouseId
  const targetWarehouseId = form.targetWarehouseId
  if (
    saving.value ||
    !form.transferNo.match(/^[A-Za-z0-9_-]{2,64}$/) ||
    !sourceWarehouseId ||
    !targetWarehouseId ||
    sourceWarehouseId === targetWarehouseId ||
    !form.lines.length ||
    form.lines.some(
      (line) => !line.sourceLocationId || !line.targetLocationId || !line.skuId || !isQuantity(line.quantity),
    )
  ) {
    ElMessage.warning('请完整填写单号、不同的源/目标仓库及有效明细')
    return
  }
  saving.value = true
  try {
    if (props.editing) {
      await updateTransfer(props.editing.id, {
        version: props.editing.version,
        sourceWarehouseId,
        targetWarehouseId,
        remark: form.remark,
        lines: form.lines,
      })
    } else {
      await createTransfer({
        transferNo: form.transferNo,
        sourceWarehouseId,
        targetWarehouseId,
        remark: form.remark,
        lines: form.lines,
      })
    }
    ElMessage.success('调拨单已保存')
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
    :title="`${editing ? '编辑' : '新建'}调拨单`"
    width="980px"
  >
    <el-form label-position="top">
      <el-row :gutter="12">
        <el-col :span="6">
          <el-form-item label="调拨单号">
            <el-input
              v-model="form.transferNo"
              :disabled="!!editing"
              maxlength="64"
            />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="源仓">
            <RemoteMasterDataSelect
              v-model="form.sourceWarehouseId"
              resource="warehouses"
            />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="目标仓">
            <RemoteMasterDataSelect
              v-model="form.targetWarehouseId"
              resource="warehouses"
            />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="备注"
            ><el-input
              v-model="form.remark"
              maxlength="255"
          /></el-form-item>
        </el-col>
      </el-row>
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
      >
        <el-table-column
          type="index"
          width="50"
        />
        <el-table-column label="源库位">
          <template #default="scope">
            <RemoteLocationSelect
              v-model="scope.row.sourceLocationId"
              :warehouse-id="form.sourceWarehouseId"
            />
          </template>
        </el-table-column>
        <el-table-column label="目标库位">
          <template #default="scope">
            <RemoteLocationSelect
              v-model="scope.row.targetLocationId"
              :warehouse-id="form.targetWarehouseId"
            />
          </template>
        </el-table-column>
        <el-table-column label="SKU">
          <template #default="scope">
            <RemoteMasterDataSelect
              v-model="scope.row.skuId"
              resource="skus"
            />
          </template>
        </el-table-column>
        <el-table-column
          label="数量"
          width="150"
        >
          <template #default="scope">
            <QuantityInput
              v-model="scope.row.quantity"
              style="width: 100%"
            />
          </template>
        </el-table-column>
        <el-table-column width="70">
          <template #default="scope">
            <el-button
              link
              type="danger"
              @click="form.lines.splice(scope.$index, 1)"
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
        >保存</el-button
      >
    </template>
  </el-dialog>
</template>
