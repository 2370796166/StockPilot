import type { PageResult } from '@/shared/types/api'
import type { InventoryBalance, InventoryBusinessType, InventoryLedger } from '@/modules/inventory/types'
import { request } from '@/shared/utils/request'
export const pageBalances = (params: {
  page: number
  size: number
  warehouseId?: number
  locationId?: number
  skuId?: number
}) => request<PageResult<InventoryBalance>>({ method: 'GET', url: '/inventory/balances', params })
export const pageLedgers = (params: {
  page: number
  size: number
  warehouseId?: number
  locationId?: number
  skuId?: number
  businessType?: InventoryBusinessType
  businessNo?: string
  startDate?: string
  endDate?: string
}) => request<PageResult<InventoryLedger>>({ method: 'GET', url: '/inventory/ledgers', params })
