<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { searchLocationOptions } from '@/modules/master-data/api'
import type { LocationRecord } from '@/modules/master-data/types'
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
  if (!props.warehouseId) {
    options.value = []
    return
  }
  const sequence = ++requestSequence
  loading.value = true
  try {
    const result = await searchLocationOptions(props.warehouseId, keyword)
    if (sequence === requestSequence) options.value = result.records
  } finally {
    if (sequence === requestSequence) loading.value = false
  }
}
function search(keyword = '') {
  if (timer) clearTimeout(timer)
  timer = setTimeout(() => {
    void load(keyword)
  }, 250)
}
watch(
  () => props.warehouseId,
  () => {
    emit('update:modelValue', undefined)
    options.value = []
    void load()
  },
)
onMounted(() => {
  void load()
})
onBeforeUnmount(() => {
  if (timer) clearTimeout(timer)
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
