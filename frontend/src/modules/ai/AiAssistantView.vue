<script setup lang="ts">
import { ElAlert, ElButton, ElCard, ElEmpty, ElInput } from 'element-plus'
import 'element-plus/es/components/alert/style/css'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/card/style/css'
import 'element-plus/es/components/empty/style/css'
import 'element-plus/es/components/input/style/css'
import { ref, onBeforeUnmount } from 'vue'
import { askAssistant } from '@/modules/ai/api'
import type { AiAnswer, Candidate, Selection } from './types'
import AiEvidence from './AiEvidence.vue'
const question = ref(''),
  loading = ref(false),
  response = ref<AiAnswer | null>(null),
  error = ref(''),
  submittedQuestion = ref('')
const selections = ref<Selection[]>([])
let alive = true
let pending: AbortController | undefined
function cancel() {
  pending?.abort()
}
onBeforeUnmount(() => {
  alive = false
  cancel()
})
const examples = [
  'A 商品在一号仓还有多少实际、可用和冻结库存？',
  'A 商品在不同仓库的库存分别是多少？',
  '查询销售出库单 SOxxx 的状态和明细。',
  '查询 A 商品在一号仓的库存流水，并解释这些变化。',
  '查询流水号对应的业务单据。',
  'A 商品在一号仓本周库存为什么变化？请按业务动作汇总。',
  'A 商品在一号仓当前冻结分别来自哪些销售单和调拨单？',
]
const statuses: Record<string, string> = {
  DISABLED: '尚未启用',
  CONFIGURATION_ERROR: '模型配置缺失或无效',
  NO_DATA: '没有匹配数据',
  FORBIDDEN: '权限不足',
  QUERY_FAILED: '业务查询失败',
  MODEL_TIMEOUT: '模型超时',
  QUESTION_TIMEOUT: '问答时间超限',
  REQUEST_CANCELLED: '已取消',
  MODEL_ERROR: '模型调用失败',
  INVALID_MODEL_RESPONSE: '模型响应无效',
  TOOL_LIMIT_EXCEEDED: '工具调用次数超限',
  NEEDS_SELECTION: '请选择匹配项',
  NEEDS_CLARIFICATION: '请补充查询条件',
  INVALID_ARGUMENTS: '参数无效',
  INVALID_TOOL: '工具被拒绝',
}
async function send(candidate?: Candidate) {
  if (loading.value || !question.value.trim() || question.value.length > 1000) return
  if (candidate && submittedQuestion.value !== question.value.trim()) return
  if (candidate && !response.value?.continuationToken) {
    error.value = '所选查询已失效，请重新发送问题。'
    return
  }
  if (!candidate) selections.value = []
  else
    selections.value = [
      ...selections.value.filter((s) => s.kind !== candidate.kind || s.keyword !== candidate.keyword),
      { kind: candidate.kind, keyword: candidate.keyword, id: candidate.id },
    ]
  submittedQuestion.value = question.value.trim()
  const continuationToken = candidate ? (response.value?.continuationToken ?? undefined) : undefined
  loading.value = true
  error.value = ''
  if (!candidate) response.value = null
  const controller = new AbortController()
  pending = controller
  try {
    const result = await askAssistant(submittedQuestion.value, selections.value, controller.signal, continuationToken)
    if (alive && !controller.signal.aborted) response.value = result
  } catch (cause) {
    if (alive && !controller.signal.aborted) {
      const status = (cause as { response?: { status: number } }).response?.status
      error.value =
        status === 403 ? '当前账号无权访问，请联系管理员。' : '请求失败，未得到有效回答。请检查网络或稍后重试。'
    }
  } finally {
    if (alive) loading.value = false
    if (pending === controller) pending = undefined
  }
}
</script>
<template>
  <div class="assistant-page">
    <div class="page-header">
      <div>
        <h1>AI 仓储助手</h1>
        <p>查询库存、业务单据与库存变化。数量和来源以结构化业务查询结果为准。</p>
      </div>
    </div>
    <el-card shadow="never">
      <label for="assistant-question">你想查询什么？</label
      ><el-input
        id="assistant-question"
        v-model="question"
        type="textarea"
        :rows="4"
        :maxlength="1000"
        show-word-limit
        :disabled="loading"
        placeholder="请输入商品、仓库名称，或准确业务单号 / 流水号"
        @keydown.ctrl.enter.prevent="send()"
      />
      <div class="send-row">
        <span>仅查询与解释 · Ctrl + Enter 发送</span
        ><el-button
          type="primary"
          :loading="loading"
          :disabled="loading || !question.trim()"
          @click="send()"
          >发送问题</el-button
        >
        <el-button
          v-if="loading"
          @click="cancel"
          >取消等待</el-button
        >
      </div>
      <div class="example-questions">
        <span>示例（请替换为真实名称或单号）</span
        ><el-button
          v-for="example in examples"
          :key="example"
          text
          :disabled="loading"
          @click="question = example"
          >{{ example }}</el-button
        >
      </div>
    </el-card>
    <el-alert
      v-if="loading"
      title="正在识别问题并查询业务数据，请勿重复发送"
      type="info"
      :closable="false"
      show-icon
    /><el-alert
      v-if="error"
      :title="error"
      type="error"
      :closable="false"
      show-icon
    />
    <template v-if="response">
      <el-alert
        v-if="response.status !== 'OK'"
        :title="statuses[response.status] ?? '查询未完成'"
        type="warning"
        :closable="false"
        show-icon
      />
      <el-card shadow="never"
        ><h2>回答</h2>
        <p class="answer-copy">{{ response.answer }}</p>
        <small>回答时间：{{ response.queriedAt }}。各查询时间可能不同。</small></el-card
      >
      <AiEvidence
        v-for="(result, index) in response.results"
        :key="index"
        :evidence="result"
        @select="send"
      />
    </template>
    <el-empty
      v-else-if="!loading && !error"
      description="输入问题后，回答与可核对来源会显示在这里"
    />
  </div>
</template>
<style scoped>
.assistant-page {
  max-width: 1200px;
  margin: auto;
}
.assistant-page > :deep(.el-card),
.assistant-page > :deep(.el-alert) {
  margin-bottom: 16px;
}
.send-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-top: 16px;
}
.send-row span,
small {
  color: var(--el-text-color-secondary);
}
.example-questions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  margin-top: 16px;
}
.example-questions :deep(.el-button) {
  margin: 0;
  height: auto;
  white-space: normal;
  text-align: left;
}
.answer-copy {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  line-height: 1.8;
}
label {
  display: block;
  margin-bottom: 12px;
}
</style>
