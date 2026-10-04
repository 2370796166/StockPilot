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
