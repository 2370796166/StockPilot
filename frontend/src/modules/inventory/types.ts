export interface InventoryBalance {
  id: number
  warehouseId: number
  locationId: number
  skuId: number
  actualQuantity: string
  availableQuantity: string
  frozenQuantity: string
  version: number
  createdAt: string
  updatedAt: string
}
export type InventoryBusinessType =
  | 'INITIALIZE'
  | 'PURCHASE_RECEIPT'
  | 'OUTBOUND_FREEZE'
  | 'OUTBOUND_RELEASE'
  | 'OUTBOUND_SHIP'
  | 'TRANSFER_FREEZE'
  | 'TRANSFER_RELEASE'
  | 'TRANSFER_OUT'
  | 'TRANSFER_IN'
  | 'INVENTORY_COUNT'
  | 'INVENTORY_GAIN'
  | 'INVENTORY_LOSS'
export interface InventoryLedger {
  id: number
  ledgerNo: string
  businessType: InventoryBusinessType
  businessNo: string
  warehouseId: number
  locationId: number
  skuId: number
  beforeActualQuantity: string
  changeActualQuantity: string
  afterActualQuantity: string
  beforeAvailableQuantity: string
  changeAvailableQuantity: string
  afterAvailableQuantity: string
  beforeFrozenQuantity: string
  changeFrozenQuantity: string
  afterFrozenQuantity: string
  balanceVersionBefore: number
  balanceVersionAfter: number
  countBookQuantity: string | null
  countedQuantity: string | null
  differenceQuantity: string | null
  adjustmentReason: string | null
  operatorId: number
  operatorName: string
  occurredAt: string
}
