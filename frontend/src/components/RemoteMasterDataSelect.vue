<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { pageMasterData } from '../api/masterData'
import type { MasterDataRecord, MasterDataResource } from '../types/masterData'

const props = withDefaults(defineProps<{ modelValue?: number; resource: MasterDataResource; placeholder?: string; disabled?: boolean }>(), { modelValue: undefined, placeholder: '请选择', disabled: false })
const emit = defineEmits<{ 'update:modelValue': [value: number | undefined] }>()
const options = ref<MasterDataRecord[]>([])
const loading = ref(false)

async function search(name = '') {
  loading.value = true
  try {
    const result = await pageMasterData(props.resource, { page: 1, size: 20, name: name || undefined, status: 'ENABLED' })
    options.value = result.records
  } finally { loading.value = false }
}

watch(() => props.resource, () => search())
onMounted(() => search())
</script>
<template>
  <el-select :model-value="modelValue" clearable filterable remote :remote-method="search" :loading="loading" :disabled="disabled" :placeholder="placeholder" style="width: 100%" @update:model-value="emit('update:modelValue', $event)">
    <el-option v-for="item in options" :key="item.id" :label="`${item.code} · ${item.name}`" :value="item.id" />
  </el-select>
</template>
