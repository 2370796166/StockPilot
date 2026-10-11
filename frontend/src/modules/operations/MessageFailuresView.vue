<script setup lang="ts">
import {
  ElButton,
  ElCard,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElSelect,
  ElOption,
  ElTable,
  ElTableColumn,
  ElTableColumn as TableColumn,
  ElMessage,
  ElMessageBox,
  vLoading,
} from 'element-plus'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/card/style/css'
import 'element-plus/es/components/dialog/style/css'
import 'element-plus/es/components/form/style/css'
import 'element-plus/es/components/form-item/style/css'
import 'element-plus/es/components/input/style/css'
import 'element-plus/es/components/select/style/css'
import 'element-plus/es/components/option/style/css'
import 'element-plus/es/components/table/style/css'
import 'element-plus/es/components/table-column/style/css'
import 'element-plus/es/components/loading/style/css'
import { onMounted, reactive, ref } from 'vue'
import { getFailure, handleFailure, pageFailures } from './api'
import type { MessageFailure, MessageDetail } from './api'
import PermissionGate from '@/shared/components/PermissionGate.vue'
import ServerPagination from '@/shared/components/ServerPagination.vue'
import { useLatestRequest } from '@/shared/composables/useLatestRequest'
const records = ref<MessageFailure[]>([]),
  detail = ref<MessageDetail | null>(null),
  total = ref(0),
  loading = ref(false),
  dialog = ref(false),
  acting = ref<number>()
const query = reactive({ page: 1, size: 20, status: 'PENDING', businessNo: '' })
const listRequest = useLatestRequest(),
  detailRequest = useLatestRequest()
const labels: Record<string, string> = { PENDING: '待处理', RETRY_REQUESTED: '等待发布重试', RESOLVED: '已处理' }
async function load() {
  const sequence = listRequest.next()
  loading.value = true
  try {
    const result = await pageFailures({
      ...query,
      status: query.status || undefined,
      businessNo: query.businessNo.trim() || undefined,
    })
    if (!listRequest.isCurrent(sequence)) return
    records.value = result.records
    total.value = result.total
  } catch {
    /* Errors are displayed by the request interceptor. */
  } finally {
    if (listRequest.isCurrent(sequence)) loading.value = false
  }
}
function search() {
  query.page = 1
  void load()
}
async function inspect(row: MessageFailure) {
  const sequence = detailRequest.next()
  detail.value = null
  try {
    const value = await getFailure(row.id)
    if (!detailRequest.isCurrent(sequence)) return
    detail.value = value
    dialog.value = true
  } catch {
    /* Errors are displayed by the request interceptor. */
  }
}
async function act(row: MessageFailure, action: 'retry' | 'resolve') {
  if (acting.value !== undefined) return
  acting.value = row.id
  try {
    const { value } = await ElMessageBox.prompt(
      action === 'retry'
        ? '修复故障后重试原事件，保留消息身份与幂等记录。请填写处理说明。'
        : '此操作记录人工处理结论，不会重新发布消息。请填写处理说明。',
      action === 'retry' ? '重试消息' : '标记已处理',
      {
        type: 'warning',
        inputValidator: (v: string) => (!!v?.trim() && v.trim().length <= 500) || '请填写最多500字的说明',
      },
    )
    const result = await handleFailure(row.id, action, row.occurrenceCount, value.trim())
    ElMessage.success(result.failure.status === 'RETRY_REQUESTED' ? '已提交发布重试，请稍后刷新结果' : '处理结果已保存')
    await load()
  } catch {
    /* Dialog cancellation and request errors leave the record unchanged. */
  } finally {
    acting.value = undefined
  }
}
onMounted(load)
const FailureColumn = TableColumn<MessageFailure>
</script>
<template>
  <div class="page-stack">
    <div class="page-heading">
      <div>
        <p class="eyebrow">运行保障</p>
        <h1>消息异常</h1>
        <p>查看失败原因和处理轨迹，修复故障后重试原事件</p>
      </div>
    </div>
    <el-card
      shadow="never"
      class="filter-card"
      ><el-form
        inline
        @submit.prevent="search"
        ><el-form-item label="状态"
          ><el-select
            v-model="query.status"
            clearable
            style="width: 160px"
            ><el-option
              v-for="(label, key) in labels"
              :key="key"
              :label="label"
              :value="key" /></el-select></el-form-item
        ><el-form-item label="业务单号"
          ><el-input
            v-model="query.businessNo"
            maxlength="64"
            clearable /></el-form-item
        ><el-form-item
          ><el-button
            type="primary"
            native-type="submit"
            >查询</el-button
          ></el-form-item
        ></el-form
      ></el-card
    >
    <el-card
      shadow="never"
      class="table-card"
      ><el-table
        v-loading="loading"
        :data="records"
      >
        <el-table-column
          label="业务单号"
          prop="businessNo"
          min-width="170"
        /><el-table-column
          label="失败阶段"
          width="95"
          ><template #default="s">{{ s.row.failureStage === 'PUBLISH' ? '发布' : '消费' }}</template></el-table-column
        ><el-table-column
          label="状态"
          min-width="140"
          ><template #default="s">{{ labels[s.row.status] }}</template></el-table-column
        ><el-table-column
          label="失败原因"
          prop="failureReason"
          min-width="260"
          show-overflow-tooltip
        /><el-table-column
          label="最后失败时间"
          prop="lastFailedAt"
          min-width="170"
        /><FailureColumn
          label="操作"
          min-width="235"
          ><template #default="s"
            ><el-button
              link
              type="primary"
              @click="inspect(s.row)"
              >详情</el-button
            ><PermissionGate authority="MESSAGE_FAILURE_WRITE"
              ><template v-if="s.row.status === 'PENDING'"
                ><el-button
                  link
                  :disabled="acting !== undefined"
                  @click="act(s.row, 'retry')"
                  >重试</el-button
                ><el-button
                  link
                  :disabled="acting !== undefined"
                  @click="act(s.row, 'resolve')"
                  >标记已处理</el-button
                ></template
              ></PermissionGate
            ></template
          ></FailureColumn
        > </el-table
      ><ServerPagination
        v-model:page="query.page"
        v-model:size="query.size"
        :total="total"
        @change="load"
    /></el-card>
    <el-dialog
      v-model="dialog"
      title="失败消息与处理轨迹"
      width="900px"
      @closed="detailRequest.invalidate()"
      ><template v-if="detail"
        ><p>消息 ID：{{ detail.failure.messageId }}</p>
        <p>失败原因：{{ detail.failure.failureReason }}</p>
        <p>处理说明：{{ detail.failure.resolutionNote || '暂无处理说明' }}</p>
        <p class="eyebrow">最近100条轨迹，最新记录在前</p>
        <el-table :data="detail.traces"
          ><el-table-column
            label="阶段"
            prop="stage"
            min-width="150" /><el-table-column
            label="尝试次数"
            prop="attemptNo"
            width="95" /><el-table-column
            label="说明"
            prop="detail"
            min-width="240" /><el-table-column
            label="时间"
            prop="occurredAt"
            min-width="170" /></el-table></template
    ></el-dialog>
  </div>
</template>
