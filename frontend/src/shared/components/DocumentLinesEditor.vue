<script setup lang="ts">
import { ElButton, ElTable, ElTableColumn } from 'element-plus'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/table/style/css'
import 'element-plus/es/components/table-column/style/css'
import type { DocumentLine } from '@/modules/documents/types'
import RemoteLocationSelect from './RemoteLocationSelect.vue'
import RemoteMasterDataSelect from './RemoteMasterDataSelect.vue'
import QuantityInput from './QuantityInput.vue'
const props = defineProps<{ modelValue: DocumentLine[]; warehouseId?: number; disabled?: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: DocumentLine[]] }>()
function add() {
  emit('update:modelValue', [...props.modelValue, { locationId: 0, skuId: 0, quantity: '1' }])
}
function remove(index: number) {
  emit(
    'update:modelValue',
    props.modelValue.filter((_, i) => i !== index),
  )
}
function update(index: number, key: 'locationId' | 'skuId', value: number | undefined) {
  const copy = props.modelValue.map((line) => ({ ...line }))
  copy[index][key] = value || 0
  emit('update:modelValue', copy)
}
function updateQuantity(index: number, value: string) {
  emit(
    'update:modelValue',
    props.modelValue.map((line, i) => (i === index ? { ...line, quantity: value } : { ...line })),
  )
}
</script>
<template>
  <div class="line-editor">
    <div class="line-editor-head">
      <strong>单据明细</strong
      ><el-button
        v-if="!disabled"
        type="primary"
        link
        @click="add"
        >添加明细</el-button
      >
    </div>
    <el-table
      :data="modelValue"
      border
      ><el-table-column
        type="index"
        label="#"
        width="52"
      /><el-table-column
        label="库位"
        min-width="190"
        ><template #default="scope"
          ><RemoteLocationSelect
            :model-value="scope.row.locationId || undefined"
            :warehouse-id="warehouseId"
            :disabled="disabled"
            @update:model-value="update(scope.$index, 'locationId', $event)" /></template></el-table-column
      ><el-table-column
        label="SKU"
        min-width="190"
        ><template #default="scope"
          ><RemoteMasterDataSelect
            :model-value="scope.row.skuId || undefined"
            resource="skus"
            placeholder="请选择 SKU"
            :disabled="disabled"
            @update:model-value="update(scope.$index, 'skuId', $event)" /></template></el-table-column
      ><el-table-column
        label="数量"
        width="160"
        ><template #default="scope"
          ><QuantityInput
            :model-value="scope.row.quantity"
            :disabled="disabled"
            style="width: 100%"
            @update:model-value="updateQuantity(scope.$index, $event)" /></template></el-table-column
      ><el-table-column
        v-if="!disabled"
        label="操作"
        width="70"
        ><template #default="scope"
          ><el-button
            link
            type="danger"
            @click="remove(scope.$index)"
            >删除</el-button
          ></template
        ></el-table-column
      ></el-table
    >
  </div>
</template>
