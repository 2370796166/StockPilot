import type { DataStatus } from './api'

export interface MasterDataRecord {
  id: number
  code: string
  name: string
  status: DataStatus
  remark: string | null
  createdAt: string
  updatedAt: string
  version: number
}

export interface LocationRecord extends MasterDataRecord {
  warehouseId: number
  warehouseCode: string
}

export interface SkuRecord extends MasterDataRecord {
  categoryId: number | null
  categoryName: string | null
  unit: string
}

export type MasterDataResource = 'warehouses' | 'categories' | 'suppliers' | 'skus'

export interface MasterDataForm {
  code?: string
  name: string
  remark: string
  version?: number
}
