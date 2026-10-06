<script setup lang="ts">
import { ElAlert, ElDialog, ElTable, ElTableColumn } from 'element-plus'
import 'element-plus/es/components/alert/style/css'
import 'element-plus/es/components/dialog/style/css'
import 'element-plus/es/components/table/style/css'
import 'element-plus/es/components/table-column/style/css'
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
    <el-alert
      v-if="detail?.status === 'CANCELLED'"
      type="info"
      :closable="false"
      :title="`取消人：${detail.cancelledByName}；时间：${detail.cancelledAt}；原因：${detail.cancelReason}`"
    />
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
