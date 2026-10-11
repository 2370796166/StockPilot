export type DocumentKind = 'purchase' | 'sales'
export type DocumentStatus = 'DRAFT' | 'SUBMITTED' | 'RESERVED' | 'APPROVED' | 'COMPLETED' | 'CANCELLED'
export interface DocumentLine {
  id?: number
  lineNo?: number
  locationId: number
  skuId: number
  quantity: string
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
  supplierId?: number | null
  supplierName?: string | null
  actions?: Array<{
    action: string
    fromStatus: string
    toStatus: string
    reason: string
    operatorName: string
    occurredAt: string
  }>
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
