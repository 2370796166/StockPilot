import { request } from '@/shared/utils/request'
import type { InventoryBalance, InventoryLedger } from '@/modules/inventory/types'
export interface ReconciliationResult {
  warehouseId: number
  locationId: number
  skuId: number
  checkedAt: string
  inventory: {
    balance: InventoryBalance | null
    latest: InventoryLedger | null
    summary: {
      ledgerCount: number
      brokenLinks: number
      actualChange: string
      availableChange: string
      frozenChange: string
    }
  }
  frozenSourceQuantity: string
  checkedTransfers: number
  issues: Array<{ code: string; businessNo: string | null; description: string }>
}
export const reconcile = (params: { warehouseId: number; locationId: number; skuId: number }) =>
  request<ReconciliationResult>({ method: 'GET', url: '/inventory/reconciliation', params })
