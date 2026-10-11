import { request } from '@/shared/utils/request'
import type { PageResult } from '@/shared/types/api'
export interface MessageFailure {
  id: number
  messageId: string
  eventName: string
  businessNo: string
  failureStage: 'PUBLISH' | 'CONSUME'
  status: 'PENDING' | 'RETRY_REQUESTED' | 'RESOLVED'
  failureReason: string
  occurrenceCount: number
  lastFailedAt: string
  resolutionNote: string | null
  resolvedBy: number | null
}
export interface MessageDetail {
  failure: MessageFailure
  traces: Array<{ stage: string; consumerName: string; attemptNo: number; detail: string; occurredAt: string }>
}
export interface OperationsStatus {
  observedAt: string
  messagingEnabled: boolean
  cacheEnabled: boolean
  activeConnections: number
  idleConnections: number
  waitingConnections: number
  observedRequests: number
  totalRequestSeconds: number
  heapUsedBytes: number
  backlog: {
    pendingMessages: number
    failedMessages: number
    pendingFailures: number
    oldestPendingAgeSeconds: number | null
  }
}
export const pageFailures = (params: { page: number; size: number; status?: string; businessNo?: string }) =>
  request<PageResult<MessageFailure>>({ method: 'GET', url: '/messages/failures', params })
export const getFailure = (id: number) => request<MessageDetail>({ method: 'GET', url: `/messages/failures/${id}` })
export const handleFailure = (id: number, action: 'retry' | 'resolve', occurrenceCount: number, note: string) =>
  request<MessageDetail>({ method: 'POST', url: `/messages/failures/${id}/${action}`, data: { occurrenceCount, note } })
export const getOperationsStatus = () => request<OperationsStatus>({ method: 'GET', url: '/operations/status' })
