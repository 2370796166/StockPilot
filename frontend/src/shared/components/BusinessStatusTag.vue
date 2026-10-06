<script setup lang="ts">
import { ElTag } from 'element-plus'
import 'element-plus/es/components/tag/style/css'
const props = defineProps<{ status: string }>()
const labels: Record<string, string> = {
  DRAFT: '草稿',
  COUNTING: '盘点中',
  SUBMITTED: '已提交',
  RESERVED: '已冻结',
  APPROVED: '已审核',
  OUTBOUND_COMPLETED: '已调出',
  IN_TRANSIT: '运输中',
  COMPLETED: '已完成',
  ADJUSTED: '已调整',
  CANCELLED: '已取消',
  OPEN: '待处理',
  RESOLVED: '已解除',
}
const types: Record<string, 'info' | 'warning' | 'primary' | 'success' | 'danger'> = {
  DRAFT: 'info',
  COUNTING: 'warning',
  SUBMITTED: 'primary',
  RESERVED: 'warning',
  APPROVED: 'primary',
  OUTBOUND_COMPLETED: 'warning',
  IN_TRANSIT: 'warning',
  COMPLETED: 'success',
  ADJUSTED: 'success',
  CANCELLED: 'info',
}
</script>
<template>
  <el-tag
    :type="types[props.status] || 'info'"
    effect="light"
    >{{ labels[props.status] || props.status }}</el-tag
  >
</template>
