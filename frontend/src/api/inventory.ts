import type { PageResult } from '../types/api'
import type { InventoryBalance, InventoryBusinessType, InventoryLedger } from '../types/inventory'
import { request } from '../utils/request'
export const pageBalances = (params: { page: number; size: number; warehouseId?: number; locationId?: number; skuId?: number }) => request<PageResult<InventoryBalance>>({ method: 'GET', url: '/inventory/balances', params })
export const pageLedgers = (params: { page: number; size: number; warehouseId?: number; locationId?: number; skuId?: number; businessType?: InventoryBusinessType; businessNo?: string }) => request<PageResult<InventoryLedger>>({ method: 'GET', url: '/inventory/ledgers', params })
