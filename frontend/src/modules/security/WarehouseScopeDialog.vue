<script setup lang="ts">
import { ElButton, ElCheckbox, ElDialog, ElForm, ElFormItem, ElMessage, vLoading } from 'element-plus'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/checkbox/style/css'
import 'element-plus/es/components/dialog/style/css'
import 'element-plus/es/components/form/style/css'
import 'element-plus/es/components/form-item/style/css'
import 'element-plus/es/components/loading/style/css'
import { computed, ref, watch } from 'vue'
import RemoteMasterDataSelect from '@/shared/components/RemoteMasterDataSelect.vue'
import EntityRef from '@/shared/components/EntityRef.vue'
import { useLatestRequest } from '@/shared/composables/useLatestRequest'
import { getWarehouseScope, setWarehouseScope } from './api'
import type { WarehouseScope } from './api'
const props = defineProps<{ modelValue: boolean; userId?: number }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()
const visible = computed({
  get: () => props.modelValue,
  set: (value) => {
    if (!saving.value) emit('update:modelValue', value)
  },
})
const scope = ref<WarehouseScope | null>(null),
  selected = ref<number>(),
  loading = ref(false),
  saving = ref(false)
const latest = useLatestRequest()
watch(
  () => [props.modelValue, props.userId],
  async () => {
    const sequence = latest.next()
    scope.value = null
    selected.value = undefined
    if (!props.modelValue || !props.userId) return
    loading.value = true
    try {
      const value = await getWarehouseScope(props.userId)
      if (latest.isCurrent(sequence)) scope.value = value
    } catch {
      /* Errors are displayed by the request interceptor. */
    } finally {
      if (latest.isCurrent(sequence)) loading.value = false
    }
  },
  { immediate: true },
)
function add() {
  if (!scope.value || !selected.value || scope.value.warehouseIds.includes(selected.value)) return
  if (scope.value.warehouseIds.length >= 200) {
    ElMessage.warning('最多指定200个仓库')
    return
  }
  scope.value.warehouseIds.push(selected.value)
  selected.value = undefined
}
async function save() {
  if (saving.value || !scope.value || !props.userId) return
  saving.value = true
  try {
    await setWarehouseScope(props.userId, {
      ...scope.value,
      warehouseIds: scope.value.allWarehouses ? [] : [...scope.value.warehouseIds],
    })
    ElMessage.success('仓库数据权限已更新，下次请求立即生效')
    emit('update:modelValue', false)
  } catch {
    /* Errors are displayed by the request interceptor. */
  } finally {
    saving.value = false
  }
}
</script>
<template>
  <el-dialog
    v-model="visible"
    title="仓库数据权限"
    width="560px"
    :close-on-click-modal="false"
    :show-close="!saving"
    ><div v-loading="loading">
      <el-form
        v-if="scope"
        label-position="top"
        ><el-form-item><el-checkbox v-model="scope.allWarehouses">允许访问全部仓库</el-checkbox></el-form-item
        ><template v-if="!scope.allWarehouses"
          ><el-form-item label="添加可访问仓库"
            ><RemoteMasterDataSelect
              v-model="selected"
              resource="warehouses"
            /><el-button @click="add">添加</el-button></el-form-item
          >
          <p
            v-for="id in scope.warehouseIds"
            :key="id"
          >
            <EntityRef
              resource="warehouses"
              :id="id"
            /><el-button
              link
              type="danger"
              @click="scope.warehouseIds = scope.warehouseIds.filter((item) => item !== id)"
              >移除</el-button
            >
          </p>
          <p>未指定仓库时，该账号不能访问仓库业务数据。</p></template
        ></el-form
      >
      <p v-else-if="!loading">数据权限未能获取，请关闭后重新打开。</p>
    </div>
    <template #footer
      ><el-button
        :disabled="saving"
        @click="visible = false"
        >取消</el-button
      ><el-button
        type="primary"
        :disabled="!scope || loading"
        :loading="saving"
        @click="save"
        >保存数据权限</el-button
      ></template
    ></el-dialog
  >
</template>
