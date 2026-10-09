import { request } from '@/shared/utils/request'
import type { AiAnswer, Selection, AgentSession, AgentTask } from './types'
export function askAssistant(
  question: string,
  selections: Selection[] = [],
  signal?: AbortSignal,
  continuationToken?: string,
) {
  // Covers the server's maximum question budget; cancellation never becomes a system error.
  return request<AiAnswer>({
    url: '/ai/questions',
    method: 'POST',
    data: { question, selections, continuationToken },
    timeout: 310_000,
    signal,
  })
}
export const createAgentSession = (signal?: AbortSignal) =>
  request<AgentSession>({ url: '/ai/sessions', method: 'POST', signal })
export const clearAgentSession = (id: string) =>
  request<void>({ url: `/ai/sessions/${encodeURIComponent(id)}`, method: 'DELETE' })
export const getAgentSession = (id: string, signal?: AbortSignal) =>
  request<AgentSession>({ url: `/ai/sessions/${encodeURIComponent(id)}`, signal })
export const retryAgentTask = (id: string, version: number, requestId: string, signal?: AbortSignal) =>
  request<AgentTask>({
    url: `/ai/tasks/${encodeURIComponent(id)}/retry`,
    method: 'POST',
    data: { version, requestId },
    signal,
  })
export const submitAgentTask = (id: string, question: string, requestId: string, signal?: AbortSignal) =>
  request<AgentTask>({
    url: `/ai/sessions/${encodeURIComponent(id)}/tasks`,
    method: 'POST',
    data: { question, requestId },
    signal,
  })
export const getAgentTask = (id: string, signal?: AbortSignal) =>
  request<AgentTask>({ url: `/ai/tasks/${encodeURIComponent(id)}`, signal })
export const cancelAgentTask = (id: string) =>
  request<AgentTask>({ url: `/ai/tasks/${encodeURIComponent(id)}/cancel`, method: 'POST' })
export const resumeAgentTask = (
  id: string,
  version: number,
  choices: Record<string, number>,
  conditions: Record<string, string>,
  signal?: AbortSignal,
  refinements: Record<string, string> = {},
  message?: string,
) =>
  request<AgentTask>({
    url: `/ai/tasks/${encodeURIComponent(id)}/input`,
    method: 'POST',
    data: { version, choices, conditions, refinements, message },
    signal,
  })
