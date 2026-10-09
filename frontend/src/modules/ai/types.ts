export interface Candidate {
  kind: string
  keyword: string
  id: number
  code: string
  name: string
}
export interface Source {
  label: string
  path: string
  authority: string
}
export interface QueryPage {
  page: number
  size: number
  total: number
  returned: number
  records: Record<string, unknown>[]
}
export interface Evidence {
  evidenceId?: string
  tool: string
  status: string
  message: string
  queriedAt: string
  candidates: Candidate[]
  sources: Source[]
  data: Record<string, unknown> & {
    warehouse?: Candidate
    location?: Candidate
    sku?: Candidate
    warehouses?: QueryPage
    locations?: QueryPage
    ledgers?: QueryPage
    salesSources?: QueryPage
    transferSources?: QueryPage
    movements?: Record<string, unknown>[]
    summary?: Record<string, unknown>
    frozenTotals?: Record<string, unknown>
    period?: { startDate: string; endDate: string; timezone: string }
    lines?: Record<string, unknown>[]
    lineTotal?: number
    lineReturned?: number
    references?: Record<string, { id: number; code: string; name: string; unit?: string }>
  }
}
export interface AiAnswer {
  continuationToken?: string | null
  status: string
  answer: string
  results: Evidence[]
  queriedAt: string
}
export type Selection = Pick<Candidate, 'kind' | 'keyword' | 'id'>
export interface AgentFact {
  evidenceId: string
  field: string
  value: string
}
export interface AgentConclusion {
  evidenceIds?: string[]
  rule: string
  text: string
  facts: AgentFact[]
  evidenceId: string
  scope: Record<string, string>
  queriedAt: string
  coverage: string
}
export interface AgentAnswer {
  conclusions: AgentConclusion[]
  uncertainties: string[]
  suggestions: string[]
}
export interface AgentTask {
  steps?: { label: string; status: string; evidenceId?: string; at: string }[]
  conditionSources?: Record<string, string>
  id: string
  sessionId: string
  version: number
  status: string
  reason?: string
  progress: string
  question: string
  results: Evidence[]
  answer: AgentAnswer
  previousResults?: Evidence[]
  conditions: Record<string, string>
  missingFields: string[]
  updatedAt: string
  modelCalls: number
  toolCalls: number
}
export interface AgentSession {
  history: { taskId: string; question: string; status: string; at: string }[]
  id: string
  context: Record<string, string>
  expiresAt: string
}
