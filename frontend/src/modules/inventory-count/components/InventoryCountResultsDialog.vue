<script setup lang="ts">
import { ElAlert, ElButton, ElDialog, ElInput, ElTable, ElTableColumn } from 'element-plus'
import 'element-plus/es/components/alert/style/css'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/dialog/style/css'
import 'element-plus/es/components/input/style/css'
import 'element-plus/es/components/table/style/css'
import 'element-plus/es/components/table-column/style/css'
import QuantityInput from '@/shared/components/QuantityInput.vue'
import { isQuantity, compareQuantities } from '@/shared/utils/quantity'
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { recordCountResults } from '@/modules/inventory-count/api'
import type { CountDetail, CountLine } from '@/modules/inventory-count/types'
import EntityRef from '@/shared/components/EntityRef.vue'

const props = defineProps<{ modelValue: boolean; detail: CountDetail | null }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; saved: [detail: CountDetail] }>()
const saving = ref(false)
const lines = ref<CountLine[]>([])
const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
})

watch(
  () => props.modelValue,
  (open) => {
    if (open) lines.value = props.detail?.lines.map((line) => ({ ...line })) ?? []
  },
)

async function save() {
  const detail = props.detail
  if (
    !detail ||
    saving.value ||
    lines.value.some((line) => !isQuantity(line.countedQuantity, true) || !line.reason?.trim())
  ) {
    ElMessage.warning('每条明细都必须填写有效实盘数量（最多四位小数）和原因')
    return
  }
  saving.value = true
  try {
    const saved = await recordCountResults(
      detail.id,
      detail.version,
      lines.value.map((line) => ({
        lineId: line.id,
        countedQuantity: line.countedQuantity!,
        reason: line.reason!.trim(),
      })),
    )
    if (lines.value.some((line) => compareQuantities(line.countedQuantity!, line.snapshotFrozenQuantity) < 0))
      ElMessage.warning('实盘低于冻结量，结果已保存；请核查，或取消盘点后处理冻结来源')
    else ElMessage.success('实盘结果已保存')
    visible.value = false
    emit('saved', saved)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <el-dialog
    v-model="visible"
    title="录入实盘结果"
    width="900px"
  >
    <el-alert
      title="所有明细均需录入，差异由后端计算"
      type="warning"
      :closable="false"
    />
    <el-table
      :data="lines"
      border
      style="margin-top: 12px"
    >
      <el-table-column
        prop="lineNo"
        label="#"
        width="50"
      />
      <el-table-column
        label="维度"
        min-width="200"
      >
        <template #default="scope">
          <EntityRef
            resource="locations"
            :id="scope.row.locationId"
          />
          /
          <EntityRef
            resource="skus"
            :id="scope.row.skuId"
          />
        </template>
      </el-table-column>
      <el-table-column
        prop="snapshotActualQuantity"
        label="账面量"
      />
      <el-table-column label="实盘量">
        <template #default="scope">
          <QuantityInput v-model="scope.row.countedQuantity" />
        </template>
      </el-table-column>
      <el-table-column label="原因">
        <template #default="scope"
          ><el-input
            v-model="scope.row.reason"
            maxlength="255"
        /></template>
      </el-table-column>
    </el-table>
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
