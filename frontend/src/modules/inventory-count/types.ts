export type CountStatus = 'DRAFT' | 'COUNTING' | 'SUBMITTED' | 'APPROVED' | 'ADJUSTED'

export interface CountLine {
  id: number
  lineNo: number
  locationId: number
  skuId: number
  snapshotActualQuantity: number
  snapshotAvailableQuantity: number
  snapshotFrozenQuantity: number
  snapshotBalanceVersion: number
  countedQuantity: number | null
  differenceQuantity: number | null
  reason: string | null
}

export interface CountSummary {
  id: number
  countNo: string
  warehouseId: number
  status: CountStatus
  remark: string | null
  version: number
  createdAt: string
  updatedAt: string
}

export interface CountDetail extends CountSummary {
  lines: CountLine[]
}
