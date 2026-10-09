<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, watch } from 'vue'
import { ElAlert, ElButton, ElCard, ElIcon, ElInput } from 'element-plus'
import 'element-plus/es/components/alert/style/css'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/card/style/css'
import 'element-plus/es/components/icon/style/css'
import 'element-plus/es/components/input/style/css'
import { useAuthStore } from '@/modules/auth/store'
import {
  createAgentSession,
  clearAgentSession,
  submitAgentTask,
  getAgentTask,
  cancelAgentTask,
  resumeAgentTask,
  getAgentSession,
  retryAgentTask,
} from './api'
import { loadSession, saveSession, forgetSession } from '@/modules/ai/session'
import type { AgentTask, Candidate } from './types'
import AiEvidence from './AiEvidence.vue'
import { ChatDotRound, Box, Tickets, TrendCharts } from '@element-plus/icons-vue'

const examples = [
  { title: '查库存列表', text: '白云仓有哪些商品，可用库存低于20的有哪些？', icon: Box },
  { title: '查未完成单据', text: '有哪些销售单还没有完成？', icon: Tickets },
  { title: '看库存变化', text: '螺栓在一号仓本周的库存变化是什么？', icon: TrendCharts },
]

const auth = useAuthStore()
const question = ref(''),
  sessionId = ref(''),
  loading = ref(false),
  error = ref('')
const turns = ref<AgentTask[]>([]),
  conditions = ref<Record<string, string>>({}),
  missing = ref<Record<string, string>>({})
const refinement = ref('')
let submission: { question: string; requestId: string; retryOf?: string; version?: number } | undefined
const uncertainSubmission = ref(false)
const restorePending = ref(false)
let alive = true,
  generation = 0,
  timer: ReturnType<typeof setTimeout> | undefined,
  pending: AbortController | undefined
const labels: Record<string, string> = {
  sku: '商品名称或编码',
  warehouse: '仓库名称或编码',
  location: '库位名称或编码',
  otherWarehouse: '另一个仓库名称或编码',
  startDate: '开始日期 YYYY-MM-DD',
  endDate: '结束日期 YYYY-MM-DD',
  number: '业务单号',
  documentType: '单据类型',
  ledgerNo: '流水号',
}
function resetLocal() {
  generation++
  pending?.abort()
  clearTimeout(timer)
  sessionId.value = ''
  turns.value = []
  conditions.value = {}
  missing.value = {}
  submission = undefined
  uncertainSubmission.value = false
  restorePending.value = false
  loading.value = false
  error.value = ''
}
watch(() => auth.user?.userId, resetLocal)
async function restore() {
  const userId = auth.user?.userId
  if (!userId) return
  const id = loadSession(userId)
  if (!id) return
  loading.value = true
  const version = ++generation
  const controller = new AbortController()
  pending = controller
  try {
    const session = await getAgentSession(id, controller.signal)
    const history = await Promise.all(session.history.map((turn) => getAgentTask(turn.taskId, controller.signal)))
    if (!alive || generation !== version) return
    sessionId.value = id
    turns.value = history
    conditions.value = session.context
    restorePending.value = false
    error.value = ''
    loading.value = false
    const task = current()
    if (task?.status === 'RUNNING') {
      loading.value = true
      apply(task, version)
    }
  } catch (failure) {
    if (alive && generation === version && !controller.signal.aborted) {
      loading.value = false
      const status = (failure as { response?: { status?: number } }).response?.status
      if (status === 404 || status === 410) {
        forgetSession(userId)
        sessionId.value = ''
        restorePending.value = false
        error.value = '会话已失效，可重新查询。'
      } else {
        sessionId.value = id
        restorePending.value = true
        error.value = '会话恢复暂时失败，请重新连接；已有服务端任务仍会保留至有效期结束。'
      }
    }
  }
}
onMounted(() => void restore())
function current() {
  return turns.value[turns.value.length - 1]
}
function apply(task: AgentTask, version: number) {
  if (!alive || version !== generation) return
  const index = turns.value.findIndex((t) => t.id === task.id)
  if (index >= 0 && turns.value[index]!.version > task.version) return
  if (index < 0) turns.value.push(task)
  else turns.value[index] = task
  if (turns.value.length > 10) turns.value.shift()
  conditions.value = task.conditions
  if (task.status === 'RUNNING') timer = setTimeout(() => void poll(task.id, version), 600)
  else {
    loading.value = false
    missing.value = {}
  }
}
async function poll(id: string, version: number) {
  const controller = new AbortController()
  pending = controller
  try {
    const task = await getAgentTask(id, controller.signal)
    apply(task, version)
  } catch {
    if (alive && version === generation && !controller.signal.aborted) {
      error.value = '连接中断，已查证据保留。可重新连接当前查询。'
      loading.value = false
    }
  }
}
async function send() {
  if (loading.value || !question.value.trim() || (!submission?.retryOf && question.value.length > 1000)) return
  if (restorePending.value) {
    await restore()
    return
  }
  const prior = current()
  if (prior && ['NEEDS_SELECTION', 'NEEDS_CLARIFICATION'].includes(prior.status)) {
    await resume(undefined, {}, question.value.trim())
    return
  }
  loading.value = true
  error.value = ''
  const version = ++generation
  const controller = new AbortController()
  pending = controller
  try {
    const id = sessionId.value || (await createAgentSession(controller.signal)).id
    if (!alive || version !== generation || controller.signal.aborted) {
      void clearAgentSession(id).catch(() => {})
      return
    }
    sessionId.value = id
    if (auth.user?.userId) saveSession(auth.user.userId, id)
    const text = question.value.trim()
    if (submission && submission.question !== text) {
      error.value = '上次提交尚未确认，请重试原提交或取消后再发送新问题。'
      loading.value = false
      return
    }
    submission ??= { question: text, requestId: crypto.randomUUID() }
    const task = submission.retryOf
      ? await retryAgentTask(submission.retryOf, submission.version!, submission.requestId, controller.signal)
      : await submitAgentTask(id, submission.question, submission.requestId, controller.signal)
    if (!alive || version !== generation || controller.signal.aborted) {
      void cancelAgentTask(task.id).catch(() => {})
      return
    }
    apply(task, version)
    submission = undefined
    uncertainSubmission.value = false
    if (version === generation) question.value = ''
  } catch {
    if (alive && version === generation && !controller.signal.aborted) {
      error.value = '查询未能提交，请检查连接；会话失效时可清空后重试。'
      uncertainSubmission.value = !!submission
      loading.value = false
    }
  }
}
async function resume(candidate?: Candidate, refinements: Record<string, string> = {}, message?: string) {
  const task = current()
  if (!task || loading.value) return
  const fields = message
    ? {}
    : Object.fromEntries(
        Object.entries(missing.value)
          .filter(([, value]) => value.trim())
          .map(([key, value]) => [key, value.trim()]),
      )
  if (!message && !candidate && task.missingFields.some((field) => !fields[field])) return
  const choices = candidate ? { [`${candidate.kind}:${candidate.keyword}`]: candidate.id } : {}
  loading.value = true
  error.value = ''
  const version = ++generation
  const controller = new AbortController()
  pending = controller
  try {
    apply(
      await resumeAgentTask(task.id, task.version, choices, fields, controller.signal, refinements, message),
      version,
    )
    if (message && alive && version === generation) question.value = ''
  } catch {
    if (alive && version === generation && !controller.signal.aborted) {
      error.value = '补充条件未成功，已有证据保留。请重试或清空过期会话。'
      loading.value = false
    }
  }
}
async function cancel() {
  const task = current()
  const version = ++generation
  pending?.abort()
  clearTimeout(timer)
  loading.value = true
  try {
    if (task && ['RUNNING', 'NEEDS_SELECTION', 'NEEDS_CLARIFICATION'].includes(task.status))
      apply(await cancelAgentTask(task.id), version)
    else {
      if (sessionId.value) await clearAgentSession(sessionId.value)
      if (auth.user?.userId) forgetSession(auth.user.userId)
      resetLocal()
    }
  } catch {
    if (alive && version === generation) {
      loading.value = false
      error.value = '取消请求未确认，服务端仍受查询时限约束。'
    }
  }
}
async function clear() {
  const id = sessionId.value
  const task = current()
  const version = ++generation
  loading.value = true
  pending?.abort()
  clearTimeout(timer)
  try {
    if (id) await clearAgentSession(id)
    else if (task) await cancelAgentTask(task.id)
    if (!alive || version !== generation) return
    if (auth.user?.userId) forgetSession(auth.user.userId)
    resetLocal()
  } catch {
    if (alive && version === generation) {
      error.value = '清空未确认，请重试。'
      loading.value = false
    }
  }
}
async function reconnect() {
  const task = current()
  if (!task || loading.value) return
  loading.value = true
  error.value = ''
  await poll(task.id, ++generation)
}
async function retry() {
  const task = current()
  if (!task || loading.value) return
  question.value = task.question
  submission = { question: task.question, requestId: crypto.randomUUID(), retryOf: task.id, version: task.version }
  await send()
}
async function retrySubmission() {
  if (!submission || loading.value) return
  question.value = submission.question
  await send()
}
async function refineCandidate() {
  const task = current()
  const candidate = task?.results.flatMap((result) => result.candidates)[0]
  if (!candidate || !refinement.value.trim()) return
  await resume(undefined, { [`${candidate.kind}:${candidate.keyword}`]: refinement.value.trim() })
}
onBeforeUnmount(() => {
  alive = false
  generation++
  pending?.abort()
  clearTimeout(timer)
  const task = current()
  if (task?.status === 'RUNNING') void cancelAgentTask(task.id).catch(() => {})
})
</script>

<template>
  <div class="agent-page">
    <div class="page-header page-heading">
      <div>
        <p class="eyebrow">查询与追溯</p>
        <h1>AI 仓储助手</h1>
        <p>查询库存、冻结来源与业务单据，支持在当前会话中连续追问。</p>
      </div>
      <el-button
        :disabled="!sessionId"
        @click="clear"
        >清空会话</el-button
      >
    </div>
    <el-card
      v-if="Object.keys(conditions).length"
      shadow="never"
      ><strong>已确认条件</strong>
      <div class="scope">
        <span
          v-for="(value, key) in conditions"
          :key="key"
          >{{ labels[key] ?? key }}：{{ value }}</span
        >
      </div></el-card
    >
    <el-alert
      v-if="error"
      :title="error"
      type="error"
      :closable="false"
    />
    <el-button
      v-if="restorePending"
      :disabled="loading"
      @click="restore"
      >重新连接会话</el-button
    >
    <section
      v-if="!turns.length"
      class="agent-start"
    >
      <span class="agent-start-icon"
        ><el-icon><ChatDotRound /></el-icon
      ></span>
      <h2>从一个仓储问题开始</h2>
      <p>说出商品和仓库，助手会查询数据并提供可追溯的结果。</p>
      <div class="example-grid">
        <button
          v-for="example in examples"
          :key="example.title"
          class="example-button"
          :disabled="loading"
          @click="question = example.text"
        >
          <el-icon><component :is="example.icon" /></el-icon><strong>{{ example.title }}</strong
          ><span>{{ example.text }}</span>
        </button>
      </div>
      <small>点击示例填入问题，发送前请替换为实际商品和仓库。</small>
    </section>
    <article
      v-for="(task, turn) in turns"
      :key="task.id"
      class="turn"
    >
      <el-card shadow="never"
        ><h2>{{ task.question }}</h2>
        <p
          v-if="task.status === 'RUNNING'"
          role="status"
        >
          {{ task.progress }}
        </p>
        <p
          v-else
          class="status"
        >
          {{ task.progress }}
        </p>
        <details v-if="task.steps?.length">
          <summary>查询步骤</summary>
          <ul>
            <li
              v-for="(step, index) in task.steps"
              :key="index"
            >
              {{ step.label }} · {{ step.status === 'OK' ? '已完成' : step.status === 'RUNNING' ? '执行中' : '未完成' }}
            </li>
          </ul>
        </details>
        <p
          v-for="(conclusion, index) in task.answer.conclusions"
          :key="index"
          class="answer"
        >
          {{ conclusion.text }}
          <a
            v-for="id in conclusion.evidenceIds ?? [conclusion.evidenceId]"
            :key="id"
            :href="`#${task.id}-${id}`"
            >[{{ id }}]</a
          >
        </p>
        <p
          v-for="text in task.answer.uncertainties"
          :key="text"
          class="uncertain"
        >
          {{ text }}
        </p>
        <p
          v-for="text in task.answer.suggestions"
          :key="text"
        >
          {{ text }}
        </p>
        <small>各证据有独立查询时间和结果范围；会话内旧结果仅供回看。</small>
      </el-card>
      <details v-if="task.previousResults?.length">
        <summary>选择或补充条件前的证据（仅供回看，当前结论使用重新查询的结果）</summary>
        <AiEvidence
          v-for="(result, index) in task.previousResults"
          :key="index"
          :evidence="result"
          readonly
        />
      </details>
      <div
        v-for="(result, index) in task.results"
        :id="`${task.id}-${result.evidenceId ?? `E${index + 1}`}`"
        :key="index"
      >
        <small>证据 {{ result.evidenceId ?? `E${index + 1}` }} · {{ result.queriedAt }}</small
        ><AiEvidence
          :evidence="result"
          :readonly="turn !== turns.length - 1 || task.status !== 'NEEDS_SELECTION' || loading"
          @select="(candidate) => turn === turns.length - 1 && !loading && resume(candidate)"
        />
      </div>
      <el-card
        v-if="turn === turns.length - 1 && task.status === 'NEEDS_SELECTION'"
        shadow="never"
      >
        <label
          >缩小候选范围（名称或准确编码）<el-input
            v-model="refinement"
            :maxlength="100"
            :disabled="loading"
        /></label>
        <el-button
          :disabled="loading || !refinement.trim()"
          @click="refineCandidate"
          >继续原任务</el-button
        >
      </el-card>
      <el-card
        v-if="turn === turns.length - 1 && task.status === 'NEEDS_CLARIFICATION'"
        shadow="never"
        ><h3>补充缺失条件</h3>
        <p>可填写下面的条件，也可以直接发送“就在白云仓”或“改查天河仓”等补充。</p>
        <label
          v-for="field in task.missingFields"
          :key="field"
          >{{ labels[field] ?? field
          }}<el-input
            v-model="missing[field]"
            :maxlength="100"
            :disabled="loading" /></label
        ><el-button
          :disabled="loading || task.missingFields.some((field) => !missing[field]?.trim())"
          @click="resume()"
          >继续原查询</el-button
        ></el-card
      >
    </article>
    <el-card
      shadow="never"
      class="agent-composer"
      ><label
        class="composer-label"
        for="agent-question"
        >你的问题</label
      ><el-input
        id="agent-question"
        v-model="question"
        type="textarea"
        :rows="3"
        :maxlength="1000"
        :disabled="loading"
        placeholder="输入问题或继续追问；更换条件时请明确说明"
        @keydown.ctrl.enter.prevent="send"
      />
      <div class="actions">
        <el-button
          type="primary"
          :loading="loading"
          :disabled="loading || !question.trim()"
          @click="send"
          >发送</el-button
        ><el-button
          v-if="
            loading || (current() && ['RUNNING', 'NEEDS_SELECTION', 'NEEDS_CLARIFICATION'].includes(current()!.status))
          "
          @click="cancel"
          >取消查询</el-button
        ><el-button
          v-if="uncertainSubmission"
          :disabled="loading"
          @click="retrySubmission"
          >重试原提交</el-button
        ><el-button
          v-if="error && current()"
          :disabled="loading"
          @click="reconnect"
          >重新连接</el-button
        ><el-button
          v-if="current() && ['FAILED', 'PARTIAL', 'CANCELLED'].includes(current()!.status)"
          :disabled="loading"
          @click="retry"
          >重新查询</el-button
        >
      </div></el-card
    >
    <p class="agent-footnote">
      仅查询，不修改库存或单据。当前数量每次重新读取；历史结果以各自查询时间为准。支持 Ctrl + Enter 发送。
    </p>
  </div>
</template>
<style scoped>
.agent-page {
  max-width: 1200px;
  margin: auto;
  display: flex;
  flex-direction: column;
  gap: 22px;
  min-width: 0;
}
.turn {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}
.agent-start {
  padding: 38px 28px 28px;
  background: #fff;
  border: 1px solid var(--sp-border);
  border-radius: var(--sp-radius);
}
.agent-start-icon {
  display: grid;
  place-items: center;
  width: 48px;
  height: 48px;
  border-radius: 12px;
  background: var(--sp-mint);
  color: var(--sp-green);
  font-size: 26px;
}
.agent-start h2 {
  font-size: 22px;
  margin: 22px 0 10px;
  font-weight: 600;
}
.agent-start > p {
  color: var(--sp-muted);
  font-size: 13px;
  line-height: 1.8;
}
.example-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin: 26px 0 18px;
}
.example-button {
  padding: 18px;
  border: 1px solid var(--sp-border);
  border-radius: 8px;
  background: #f8faf9;
  cursor: pointer;
  text-align: left;
  display: grid;
  grid-template-columns: 20px 1fr;
  gap: 10px;
  color: inherit;
  transition: border-color 0.15s;
}
.example-button:hover:not(:disabled) {
  border-color: var(--sp-green);
  background: var(--sp-mint);
}
.example-button:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
.example-button .el-icon {
  color: var(--sp-green);
  font-size: 18px;
}
.example-button strong {
  font-size: 13px;
  font-weight: 600;
}
.example-button span {
  grid-column: 1 / -1;
  font-size: 12px;
  color: var(--sp-muted);
  line-height: 1.7;
}
.scope,
.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 14px;
}
.scope span {
  padding: 6px 10px;
  border-radius: 4px;
  background: var(--sp-mint);
  font-size: 12px;
  color: var(--sp-green-dark);
  overflow-wrap: anywhere;
}
.actions .el-button {
  margin-left: 0;
}
.answer {
  white-space: pre-wrap;
  line-height: 1.8;
  overflow-wrap: anywhere;
  font-size: 14px;
}
.answer a {
  margin-left: 5px;
  font-size: 12px;
  text-decoration: none;
}
.status,
small {
  color: var(--sp-muted);
  font-size: 12px;
  line-height: 1.8;
}
.uncertain {
  color: var(--el-color-warning-dark-2);
}
label {
  display: block;
  margin: 12px 0;
  font-size: 13px;
}
label :deep(.el-input) {
  margin-top: 8px;
}
h2 {
  font-size: 18px;
  line-height: 1.6;
  overflow-wrap: anywhere;
}
.turn details {
  padding: 12px 0;
  color: var(--sp-muted);
  font-size: 12px;
}
.turn summary {
  cursor: pointer;
  color: var(--sp-green);
  line-height: 1.8;
}
.turn li {
  margin: 8px 0;
}
.turn [id] {
  scroll-margin-top: 90px;
}
.agent-composer {
  border-color: #abcabd;
}
.composer-label {
  font-size: 12px;
  font-weight: 600;
  margin: 0 0 12px;
}
.agent-composer :deep(.el-textarea__inner) {
  line-height: 1.8;
  padding: 12px 14px;
  resize: vertical;
}
.agent-footnote {
  color: var(--sp-muted);
  font-size: 11px;
  line-height: 1.8;
  margin: -8px 0 0;
}
@media (max-width: 760px) {
  .example-grid {
    grid-template-columns: 1fr;
  }
  .agent-start {
    padding: 24px 20px;
  }
  .agent-start h2 {
    font-size: 20px;
  }
}
</style>
