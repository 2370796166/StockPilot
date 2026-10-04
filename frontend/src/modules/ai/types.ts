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
  status: string
  answer: string
  results: Evidence[]
  queriedAt: string
}
export type Selection = Pick<Candidate, 'kind' | 'keyword' | 'id'>
