<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { searchLocationOptions } from '@/modules/master-data/reference-options'
import type { LocationRecord } from '@/modules/master-data/types'
import { getLocation } from '@/modules/master-data/location-api'
const props = withDefaults(
  defineProps<{ modelValue?: number; warehouseId?: number; placeholder?: string; disabled?: boolean }>(),
  { modelValue: undefined, warehouseId: undefined, placeholder: '请选择库位', disabled: false },
)
const emit = defineEmits<{ 'update:modelValue': [value: number | undefined] }>()
const options = ref<LocationRecord[]>([]),
  loading = ref(false)
let timer: ReturnType<typeof setTimeout> | undefined,
  requestSequence = 0
async function load(keyword = '') {
  const sequence = ++requestSequence
  if (!props.warehouseId) {
    options.value = []
    loading.value = false
    return
  }
  const warehouseId = props.warehouseId
  loading.value = true
  try {
    const result = await searchLocationOptions(warehouseId, keyword)
    if (sequence !== requestSequence) return
    const records = [...result.records]
    if (props.modelValue && !records.some((item) => item.id === props.modelValue)) {
      const selected = await getLocation(props.modelValue)
      if (selected.warehouseId === warehouseId) records.unshift(selected)
    }
    if (sequence === requestSequence) options.value = records
  } catch {
    // The shared request interceptor already displays the error.
    if (sequence === requestSequence) options.value = []
  } finally {
    if (sequence === requestSequence) loading.value = false
  }
}
function search(keyword = '') {
  if (timer) clearTimeout(timer)
  ++requestSequence
  options.value = []
  timer = setTimeout(() => {
    void load(keyword)
  }, 250)
}
watch(
  () => [props.warehouseId, props.modelValue],
  ([warehouseId, selected], [previousWarehouse, previousSelected]) => {
    if (timer) clearTimeout(timer)
    if (warehouseId !== previousWarehouse && selected === previousSelected) emit('update:modelValue', undefined)
    options.value = []
    void load()
  },
)
onMounted(() => {
  void load()
})
onBeforeUnmount(() => {
  if (timer) clearTimeout(timer)
  ++requestSequence
})
</script>
<template>
  <el-select
    :model-value="modelValue"
    clearable
    filterable
    remote
    :remote-method="search"
    :loading="loading"
    :disabled="disabled || !warehouseId"
    :placeholder="placeholder"
    style="width: 100%"
    @update:model-value="emit('update:modelValue', $event)"
    ><el-option
      v-for="item in options"
      :key="item.id"
      :label="`${item.code} · ${item.name}`"
      :value="item.id"
  /></el-select>
</template>
