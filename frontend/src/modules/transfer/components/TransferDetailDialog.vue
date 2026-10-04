<script setup lang="ts">
import { computed } from 'vue'
import type { TransferDetail } from '@/modules/transfer/types'
import BusinessStatusTag from '@/shared/components/BusinessStatusTag.vue'
import EntityRef from '@/shared/components/EntityRef.vue'

const props = defineProps<{ modelValue: boolean; detail: TransferDetail | null }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()
const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
})
</script>

<template>
  <el-dialog
    v-model="visible"
    title="调拨详情"
    width="900px"
  >
    <template v-if="detail">
      <el-descriptions
        :column="3"
        border
      >
        <el-descriptions-item label="单号">{{ detail.transferNo }}</el-descriptions-item>
        <el-descriptions-item label="状态"><BusinessStatusTag :status="detail.status" /></el-descriptions-item>
        <el-descriptions-item label="备注">{{ detail.remark || '—' }}</el-descriptions-item>
      </el-descriptions>
      <el-table
        :data="detail.lines"
        border
        style="margin-top: 16px"
      >
        <el-table-column
          prop="lineNo"
          label="#"
          width="55"
        />
        <el-table-column label="源库位">
          <template #default="scope"
            ><EntityRef
              resource="locations"
              :id="scope.row.sourceLocationId"
          /></template>
        </el-table-column>
        <el-table-column label="目标库位">
          <template #default="scope"
            ><EntityRef
              resource="locations"
              :id="scope.row.targetLocationId"
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
          prop="quantity"
          label="数量"
        />
      </el-table>
    </template>
  </el-dialog>
</template>
