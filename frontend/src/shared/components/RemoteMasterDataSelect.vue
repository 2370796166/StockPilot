<script setup lang="ts">
import { ElOption, ElSelect } from 'element-plus'
import 'element-plus/es/components/option/style/css'
import 'element-plus/es/components/select/style/css'
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { searchMasterDataOptions } from '@/modules/master-data/reference-options'
import type { MasterDataRecord, MasterDataResource } from '@/modules/master-data/types'
import { getMasterData } from '@/modules/master-data/api'

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
    if (sequence !== requestSequence) return
    const records = [...result.records]
    if (props.modelValue && !records.some((item) => item.id === props.modelValue))
      records.unshift(await getMasterData(props.resource, props.modelValue))
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
  () => [props.resource, props.modelValue],
  () => {
    if (timer) clearTimeout(timer)
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
