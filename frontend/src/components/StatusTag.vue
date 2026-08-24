<script setup lang="ts">
import type { DataStatus } from '../types/api'
defineProps<{ status: DataStatus }>()
</script>
<template><el-tag :type="status === 'ENABLED' ? 'success' : 'info'" effect="light">{{ status === 'ENABLED' ? '启用' : '停用' }}</el-tag></template>
