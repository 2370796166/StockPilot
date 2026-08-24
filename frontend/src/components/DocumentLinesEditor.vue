<script setup lang="ts">
import type { DocumentLine } from '../types/business'
import RemoteLocationSelect from './RemoteLocationSelect.vue'
import RemoteMasterDataSelect from './RemoteMasterDataSelect.vue'
const props = defineProps<{ modelValue: DocumentLine[]; warehouseId?: number; disabled?: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: DocumentLine[]] }>()
function add() { emit('update:modelValue', [...props.modelValue, { locationId: 0, skuId: 0, quantity: 1 }]) }
function remove(index: number) { emit('update:modelValue', props.modelValue.filter((_, i) => i !== index)) }
function update(index: number, key: keyof DocumentLine, value: number | undefined) { const copy = props.modelValue.map((line) => ({ ...line })); copy[index][key] = value || 0; emit('update:modelValue', copy) }
</script>
<template>
  <div class="line-editor"><div class="line-editor-head"><strong>单据明细</strong><el-button v-if="!disabled" type="primary" link @click="add">添加明细</el-button></div>
    <el-table :data="modelValue" border><el-table-column type="index" label="#" width="52" /><el-table-column label="库位" min-width="190"><template #default="scope"><RemoteLocationSelect :model-value="scope.row.locationId || undefined" :warehouse-id="warehouseId" :disabled="disabled" @update:model-value="update(scope.$index, 'locationId', $event)" /></template></el-table-column><el-table-column label="SKU" min-width="190"><template #default="scope"><RemoteMasterDataSelect :model-value="scope.row.skuId || undefined" resource="skus" placeholder="请选择 SKU" :disabled="disabled" @update:model-value="update(scope.$index, 'skuId', $event)" /></template></el-table-column><el-table-column label="数量" width="160"><template #default="scope"><el-input-number :model-value="scope.row.quantity" :min="0.0001" :precision="4" :disabled="disabled" controls-position="right" style="width:100%" @update:model-value="update(scope.$index, 'quantity', $event)" /></template></el-table-column><el-table-column v-if="!disabled" label="操作" width="70"><template #default="scope"><el-button link type="danger" @click="remove(scope.$index)">删除</el-button></template></el-table-column></el-table>
  </div>
</template>
