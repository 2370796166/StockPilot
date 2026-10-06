<script setup lang="ts">
import { ElInput } from 'element-plus'
import 'element-plus/es/components/input/style/css'
defineProps<{ modelValue?: string | null; disabled?: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()
</script>

<template>
  <el-input
    :model-value="modelValue ?? ''"
    :disabled="disabled"
    inputmode="decimal"
    maxlength="20"
    placeholder="最多四位小数"
    @update:model-value="emit('update:modelValue', $event)"
  />
</template>
