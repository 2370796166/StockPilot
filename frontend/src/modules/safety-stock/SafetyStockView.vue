<script setup lang="ts">
import {
  ElButton,
  ElCard,
  ElDialog,
  ElForm,
  ElFormItem,
  ElSelect,
  ElOption,
  ElTable,
  ElTableColumn,
  ElTableColumn as TableColumn,
  ElMessage,
  vLoading,
} from 'element-plus'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/card/style/css'
import 'element-plus/es/components/dialog/style/css'
import 'element-plus/es/components/form/style/css'
import 'element-plus/es/components/form-item/style/css'
import 'element-plus/es/components/select/style/css'
import 'element-plus/es/components/option/style/css'
import 'element-plus/es/components/table/style/css'
import 'element-plus/es/components/table-column/style/css'
import 'element-plus/es/components/loading/style/css'
import { onMounted, reactive, ref } from 'vue'
import { createRule, evaluateRule, pageAlerts, pageRules, updateRule } from './api'
import type { SafetyStockRule, LowStockAlert } from './api'
import EntityRef from '@/shared/components/EntityRef.vue'
import RemoteMasterDataSelect from '@/shared/components/RemoteMasterDataSelect.vue'
import RemoteLocationSelect from '@/shared/components/RemoteLocationSelect.vue'
import QuantityInput from '@/shared/components/QuantityInput.vue'
import PermissionGate from '@/shared/components/PermissionGate.vue'
import ServerPagination from '@/shared/components/ServerPagination.vue'
import { isQuantity } from '@/shared/utils/quantity'
import { useLatestRequest } from '@/shared/composables/useLatestRequest'
const rules = ref<SafetyStockRule[]>([]),
  alerts = ref<LowStockAlert[]>([])
const loading = ref(false),
  dialog = ref(false),
  saving = ref(false),
  acting = ref<number>()
const editing = ref<SafetyStockRule | null>(null),
  ruleTotal = ref(0),
  alertTotal = ref(0)
const filters = reactive<{ warehouseId?: number; ruleStatus?: string; alertStatus?: string }>({ alertStatus: 'OPEN' })
const ruleQuery = reactive({ page: 1, size: 20 }),
  alertQuery = reactive({ page: 1, size: 20 })
const form = reactive<{
  warehouseId?: number
  locationId?: number
  skuId?: number
  thresholdQuantity: string
  status: 'ENABLED' | 'DISABLED'
}>({ thresholdQuantity: '0', status: 'ENABLED' })
const listRequest = useLatestRequest()
async function load() {
  const sequence = listRequest.next()
  loading.value = true
  try {
    const [r, a] = await Promise.all([
      pageRules({ ...ruleQuery, warehouseId: filters.warehouseId, status: filters.ruleStatus }),
      pageAlerts({ ...alertQuery, warehouseId: filters.warehouseId, status: filters.alertStatus }),
    ])
    if (!listRequest.isCurrent(sequence)) return
    rules.value = r.records
    ruleTotal.value = r.total
    alerts.value = a.records
    alertTotal.value = a.total
  } catch {
    /* Errors are displayed by the request interceptor. */
  } finally {
    if (listRequest.isCurrent(sequence)) loading.value = false
  }
}
function search() {
  ruleQuery.page = 1
  alertQuery.page = 1
  void load()
}
function open(rule?: SafetyStockRule) {
  editing.value = rule || null
  Object.assign(form, {
    warehouseId: rule?.warehouseId,
    locationId: rule?.locationId,
    skuId: rule?.skuId,
    thresholdQuantity: rule?.thresholdQuantity || '0',
    status: rule?.status || 'ENABLED',
  })
  dialog.value = true
}
async function save() {
  if (saving.value) return
  if (!form.warehouseId || !form.locationId || !form.skuId || !isQuantity(form.thresholdQuantity, true)) {
    ElMessage.warning('请选择完整库存维度，阈值应为非负数量')
    return
  }
  saving.value = true
  try {
    if (editing.value)
      await updateRule(editing.value.id, {
        thresholdQuantity: form.thresholdQuantity,
        status: form.status,
        version: editing.value.version,
      })
    else
      await createRule({
        warehouseId: form.warehouseId,
        locationId: form.locationId,
        skuId: form.skuId,
        thresholdQuantity: form.thresholdQuantity,
      })
    dialog.value = false
    ElMessage.success('规则已保存并重新评估')
    await load()
  } catch {
    /* Errors are displayed by the request interceptor. */
  } finally {
    saving.value = false
  }
}
async function evaluate(rule: SafetyStockRule) {
  if (acting.value !== undefined) return
  acting.value = rule.id
  try {
    await evaluateRule(rule.id)
    ElMessage.success('预警已重新评估')
    await load()
  } catch {
    /* Errors are displayed by the request interceptor. */
  } finally {
    acting.value = undefined
  }
}
onMounted(load)
const RuleColumn = TableColumn<SafetyStockRule>
</script>
<template>
  <div class="page-stack">
    <div class="page-heading">
      <div>
        <p class="eyebrow">库存保障</p>
        <h1>安全库存</h1>
        <p>可用量低于阈值时预警，规则修改立即重新评估</p>
      </div>
      <PermissionGate authority="SAFETY_STOCK_WRITE"
        ><el-button
          type="primary"
          @click="open()"
          >新增规则</el-button
        ></PermissionGate
      >
    </div>
    <el-card
      shadow="never"
      class="filter-card"
      ><el-form
        inline
        @submit.prevent="search"
      >
        <el-form-item label="仓库"
          ><RemoteMasterDataSelect
            v-model="filters.warehouseId"
            resource="warehouses"
        /></el-form-item>
        <el-form-item label="规则状态"
          ><el-select
            v-model="filters.ruleStatus"
            clearable
            style="width: 130px"
            ><el-option
              label="启用"
              value="ENABLED" /><el-option
              label="停用"
              value="DISABLED" /></el-select
        ></el-form-item>
        <el-form-item label="预警状态"
          ><el-select
            v-model="filters.alertStatus"
            clearable
            style="width: 130px"
            ><el-option
              label="预警中"
              value="OPEN" /><el-option
              label="已解除"
              value="RESOLVED" /></el-select
        ></el-form-item>
        <el-form-item
          ><el-button
            type="primary"
            native-type="submit"
            >查询</el-button
          ></el-form-item
        >
      </el-form></el-card
    >
    <el-card
      shadow="never"
      class="table-card"
      ><h2>阈值规则</h2>
      <el-table
        v-loading="loading"
        :data="rules"
      >
        <el-table-column
          label="仓库"
          min-width="160"
          ><template #default="s"
            ><EntityRef
              resource="warehouses"
              :id="s.row.warehouseId" /></template
        ></el-table-column>
        <el-table-column
          label="库位"
          min-width="160"
          ><template #default="s"
            ><EntityRef
              resource="locations"
              :id="s.row.locationId" /></template
        ></el-table-column>
        <el-table-column
          label="商品"
          min-width="170"
          ><template #default="s"
            ><EntityRef
              resource="skus"
              :id="s.row.skuId" /></template
        ></el-table-column>
        <el-table-column
          label="安全库存阈值"
          prop="thresholdQuantity"
          align="right"
          min-width="180"
        />
        <el-table-column
          label="状态"
          width="90"
          ><template #default="s">{{ s.row.status === 'ENABLED' ? '启用' : '停用' }}</template></el-table-column
        >
        <RuleColumn
          label="操作"
          min-width="180"
          ><template #default="s"
            ><PermissionGate authority="SAFETY_STOCK_WRITE"
              ><el-button
                link
                type="primary"
                @click="open(s.row)"
                >修改</el-button
              ><el-button
                link
                :disabled="acting !== undefined"
                @click="evaluate(s.row)"
                >重新评估</el-button
              ></PermissionGate
            ></template
          ></RuleColumn
        > </el-table
      ><ServerPagination
        v-model:page="ruleQuery.page"
        v-model:size="ruleQuery.size"
        :total="ruleTotal"
        @change="load"
    /></el-card>
    <el-card
      shadow="never"
      class="table-card"
      ><h2>库存预警</h2>
      <p class="eyebrow">数量为上次评估时的可用量；库存变化事件处理后更新</p>
      <el-table
        v-loading="loading"
        :data="alerts"
      >
        <el-table-column
          label="商品"
          min-width="170"
          ><template #default="s"
            ><EntityRef
              resource="skus"
              :id="s.row.skuId" /></template
        ></el-table-column>
        <el-table-column
          label="仓库 / 库位"
          min-width="200"
          ><template #default="s"
            ><EntityRef
              resource="warehouses"
              :id="s.row.warehouseId" />
            /
            <EntityRef
              resource="locations"
              :id="s.row.locationId" /></template
        ></el-table-column>
        <el-table-column
          label="评估可用量"
          prop="availableQuantity"
          align="right"
          min-width="160"
        /><el-table-column
          label="阈值"
          prop="thresholdQuantity"
          align="right"
          min-width="160"
        />
        <el-table-column
          label="状态"
          width="95"
          ><template #default="s">{{ s.row.status === 'OPEN' ? '预警中' : '已解除' }}</template></el-table-column
        >
        <el-table-column
          label="来源单号 / 规则"
          prop="businessNo"
          min-width="170"
        /><el-table-column
          label="评估时间"
          prop="lastEvaluatedAt"
          min-width="170"
        /> </el-table
      ><ServerPagination
        v-model:page="alertQuery.page"
        v-model:size="alertQuery.size"
        :total="alertTotal"
        @change="load"
    /></el-card>
    <el-dialog
      v-model="dialog"
      :title="editing ? '修改安全库存规则' : '新增安全库存规则'"
      width="560px"
      :close-on-click-modal="false"
    >
      <el-form
        label-position="top"
        @submit.prevent="save"
        ><el-form-item label="仓库"
          ><RemoteMasterDataSelect
            v-model="form.warehouseId"
            resource="warehouses"
            :disabled="Boolean(editing)" /></el-form-item
        ><el-form-item label="库位"
          ><RemoteLocationSelect
            v-model="form.locationId"
            :warehouse-id="form.warehouseId"
            :disabled="Boolean(editing)" /></el-form-item
        ><el-form-item label="商品"
          ><RemoteMasterDataSelect
            v-model="form.skuId"
            resource="skus"
            :disabled="Boolean(editing)" /></el-form-item
        ><el-form-item label="安全库存阈值"><QuantityInput v-model="form.thresholdQuantity" /></el-form-item
        ><el-form-item
          v-if="editing"
          label="状态"
          ><el-select v-model="form.status"
            ><el-option
              label="启用"
              value="ENABLED" /><el-option
              label="停用"
              value="DISABLED" /></el-select></el-form-item
      ></el-form>
      <template #footer
        ><el-button
          :disabled="saving"
          @click="dialog = false"
          >取消</el-button
        ><el-button
          type="primary"
          :loading="saving"
          @click="save"
          >保存并评估</el-button
        ></template
      >
    </el-dialog>
  </div>
</template>
