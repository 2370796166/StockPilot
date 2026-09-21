export type DocumentKind = 'purchase' | 'sales'
export type DocumentStatus = 'DRAFT' | 'SUBMITTED' | 'RESERVED' | 'APPROVED' | 'COMPLETED' | 'CANCELLED'
export interface DocumentLine {
  id?: number
  lineNo?: number
  locationId: number
  skuId: number
  quantity: number
}
export interface DocumentSummary {
  id: number
  no: string
  warehouseId: number
  status: DocumentStatus
  remark: string | null
  createdByName: string
  version: number
  createdAt: string
  updatedAt: string
}
export interface DocumentDetail extends DocumentSummary {
  lines: DocumentLine[]
  submittedByName?: string
  submittedAt?: string
  reservedByName?: string
  reservedAt?: string
  approvedByName?: string
  approvedAt?: string
  completedByName?: string
  completedAt?: string
  cancelledByName?: string
  cancelledAt?: string
}

export type TransferStatus =
  | 'DRAFT'
  | 'SUBMITTED'
  | 'APPROVED'
  | 'OUTBOUND_COMPLETED'
  | 'IN_TRANSIT'
  | 'COMPLETED'
  | 'CANCELLED'
export interface TransferLine {
  id?: number
  lineNo?: number
  sourceLocationId: number
  targetLocationId: number
  skuId: number
  quantity: number
}
export interface TransferSummary {
  id: number
  transferNo: string
  sourceWarehouseId: number
  targetWarehouseId: number
  status: TransferStatus
  remark: string | null
  version: number
  createdAt: string
  updatedAt: string
}
export interface TransferDetail extends TransferSummary {
  lines: TransferLine[]
  transitRecords: Array<{
    transferLineId: number
    outboundQuantity: number
    inTransitQuantity: number
    receivedQuantity: number
    status: string
    version: number
  }>
}

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
