<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { searchMasterDataOptions } from '@/modules/master-data/api'
import type { MasterDataRecord, MasterDataResource } from '@/modules/master-data/types'

const props = withDefaults(
  defineProps<{ modelValue?: number; resource: MasterDataResource; placeholder?: string; disabled?: boolean }>(),
  { modelValue: undefined, placeholder: '请选择', disabled: false },
)
const emit = defineEmits<{ 'update:modelValue': [value: number | undefined] }>()
const options = ref<MasterDataRecord[]>([])
const loading = ref(false)
let timer: ReturnType<typeof setTimeout> | undefined
let requestSequence = 0

async function load(keyword = '') {
  const sequence = ++requestSequence
  loading.value = true
  try {
    const result = await searchMasterDataOptions(props.resource, keyword)
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
  () => props.resource,
  () => {
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
    :disabled="disabled"
    :placeholder="placeholder"
    style="width: 100%"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-option
      v-for="item in options"
      :key="item.id"
      :label="`${item.code} · ${item.name}`"
      :value="item.id"
    />
  </el-select>
</template>
