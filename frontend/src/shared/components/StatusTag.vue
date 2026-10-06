<script setup lang="ts">
import { ElTag } from 'element-plus'
import 'element-plus/es/components/tag/style/css'
import type { DataStatus } from '@/shared/types/api'
defineProps<{ status: DataStatus }>()
</script>
<template>
  <el-tag
    :type="status === 'ENABLED' ? 'success' : 'info'"
    effect="light"
    >{{ status === 'ENABLED' ? '启用' : '停用' }}</el-tag
  >
</template>
