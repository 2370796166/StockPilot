<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { pageLocations } from '../api/masterData'
import type { LocationRecord } from '../types/masterData'
const props = withDefaults(defineProps<{ modelValue?: number; warehouseId?: number; placeholder?: string; disabled?: boolean }>(), { modelValue: undefined, warehouseId: undefined, placeholder: '请选择库位', disabled: false })
const emit = defineEmits<{ 'update:modelValue': [value: number | undefined] }>()
const options = ref<LocationRecord[]>([]), loading = ref(false)
async function search(name = '') { if (!props.warehouseId) { options.value = []; return } loading.value = true; try { const result = await pageLocations({ page: 1, size: 20, warehouseId: props.warehouseId, name: name || undefined, status: 'ENABLED' }); options.value = result.records } finally { loading.value = false } }
watch(() => props.warehouseId, () => { emit('update:modelValue', undefined); void search() })
onMounted(search)
</script>
<template><el-select :model-value="modelValue" clearable filterable remote :remote-method="search" :loading="loading" :disabled="disabled || !warehouseId" :placeholder="placeholder" style="width:100%" @update:model-value="emit('update:modelValue', $event)"><el-option v-for="item in options" :key="item.id" :label="`${item.code} · ${item.name}`" :value="item.id" /></el-select></template>
