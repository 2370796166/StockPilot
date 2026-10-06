export type CountStatus = 'DRAFT' | 'COUNTING' | 'SUBMITTED' | 'APPROVED' | 'ADJUSTED' | 'CANCELLED'

export interface CountLine {
  id: number
  lineNo: number
  locationId: number
  skuId: number
  snapshotActualQuantity: string
  snapshotAvailableQuantity: string
  snapshotFrozenQuantity: string
  snapshotBalanceVersion: number
  countedQuantity: string | null
  differenceQuantity: string | null
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
  cancelledBy: number | null
  cancelledByName: string | null
  cancelledAt: string | null
  cancelReason: string | null
  lines: CountLine[]
}
