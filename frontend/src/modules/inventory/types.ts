export interface InventoryBalance {
  id: number
  warehouseId: number
  locationId: number
  skuId: number
  actualQuantity: number
  availableQuantity: number
  frozenQuantity: number
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
  beforeActualQuantity: number
  changeActualQuantity: number
  afterActualQuantity: number
  beforeAvailableQuantity: number
  changeAvailableQuantity: number
  afterAvailableQuantity: number
  beforeFrozenQuantity: number
  changeFrozenQuantity: number
  afterFrozenQuantity: number
  balanceVersionBefore: number
  balanceVersionAfter: number
  countBookQuantity: number | null
  countedQuantity: number | null
  differenceQuantity: number | null
  adjustmentReason: string | null
  operatorId: number
  operatorName: string
  occurredAt: string
}
