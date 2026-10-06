import { request } from '@/shared/utils/request'
import type { AiAnswer, Selection } from './types'
export function askAssistant(question: string, selections: Selection[] = [], signal?: AbortSignal) {
  // Covers the server's maximum question budget; cancellation never becomes a system error.
  return request<AiAnswer>({
    url: '/ai/questions',
    method: 'POST',
    data: { question, selections },
    timeout: 310_000,
    signal,
  })
}
