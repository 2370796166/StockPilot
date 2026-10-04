<script setup lang="ts">
import { computed } from 'vue'
import type { CountDetail } from '@/modules/inventory-count/types'
import EntityRef from '@/shared/components/EntityRef.vue'

const props = defineProps<{ modelValue: boolean; detail: CountDetail | null }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()
const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
})
</script>

<template>
  <el-dialog
    v-model="visible"
    title="盘点详情"
    width="900px"
  >
    <el-table
      v-if="detail"
      :data="detail.lines"
      border
    >
      <el-table-column
        prop="lineNo"
        label="#"
        width="50"
      />
      <el-table-column label="库位">
        <template #default="scope"
          ><EntityRef
            resource="locations"
            :id="scope.row.locationId"
        /></template>
      </el-table-column>
      <el-table-column label="SKU">
        <template #default="scope"
          ><EntityRef
            resource="skus"
            :id="scope.row.skuId"
        /></template>
      </el-table-column>
      <el-table-column
        prop="snapshotActualQuantity"
        label="账面实际"
      />
      <el-table-column
        prop="snapshotFrozenQuantity"
        label="冻结"
      />
      <el-table-column
        prop="countedQuantity"
        label="实盘"
      />
      <el-table-column
        prop="differenceQuantity"
        label="差异"
      />
      <el-table-column
        prop="reason"
        label="原因"
      />
    </el-table>
  </el-dialog>
</template>
