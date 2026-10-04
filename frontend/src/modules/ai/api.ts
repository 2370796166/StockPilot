import { request } from '@/shared/utils/request'
import type { AiAnswer, Selection } from './types'
export function askAssistant(question: string, selections: Selection[] = []) {
  // Includes the maximum provider-call budget and short inventory-analysis snapshots.
  return request<AiAnswer>({ url: '/ai/questions', method: 'POST', data: { question, selections }, timeout: 650_000 })
}
