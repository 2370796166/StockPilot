import { request } from '@/shared/utils/request'
import type { PageResult } from '@/shared/types/api'
export interface SafetyStockRule {
  id: number
  warehouseId: number
  locationId: number
  skuId: number
  thresholdQuantity: string
  status: 'ENABLED' | 'DISABLED'
  version: number
}
export interface LowStockAlert {
  id: number
  ruleId: number
  warehouseId: number
  locationId: number
  skuId: number
  thresholdQuantity: string
  availableQuantity: string
  status: 'OPEN' | 'RESOLVED'
  businessNo: string
  sourceEventName: string
  firstTriggeredAt: string
  lastEvaluatedAt: string
  resolvedAt: string | null
}
export const pageRules = (params: { page: number; size: number; warehouseId?: number; status?: string }) =>
  request<PageResult<SafetyStockRule>>({ method: 'GET', url: '/safety-stock/rules', params })
export const pageAlerts = (params: { page: number; size: number; warehouseId?: number; status?: string }) =>
  request<PageResult<LowStockAlert>>({ method: 'GET', url: '/safety-stock/alerts', params })
export const createRule = (data: {
  warehouseId: number
  locationId: number
  skuId: number
  thresholdQuantity: string
}) => request<SafetyStockRule>({ method: 'POST', url: '/safety-stock/rules', data })
export const updateRule = (id: number, data: { thresholdQuantity: string; status: string; version: number }) =>
  request<SafetyStockRule>({ method: 'PUT', url: `/safety-stock/rules/${id}`, data })
export const evaluateRule = (id: number) => request<void>({ method: 'POST', url: `/safety-stock/rules/${id}/evaluate` })
