import type { PageResult } from '@/shared/types/api'
import type { TransferDetail, TransferLine, TransferStatus, TransferSummary } from '@/modules/transfer/types'
import { request } from '@/shared/utils/request'
function writableLines(lines: TransferLine[]) {
  return lines.map(({ sourceLocationId, targetLocationId, skuId, quantity }) => ({
    sourceLocationId,
    targetLocationId,
    skuId,
    quantity,
  }))
}
export const pageTransfers = (params: {
  page: number
  size: number
  transferNo?: string
  sourceWarehouseId?: number
  targetWarehouseId?: number
  status?: TransferStatus
}) => request<PageResult<TransferSummary>>({ method: 'GET', url: '/transfers', params })
export const getTransfer = (id: number) => request<TransferDetail>({ method: 'GET', url: `/transfers/${id}` })
export const createTransfer = (data: {
  transferNo: string
  sourceWarehouseId: number
  targetWarehouseId: number
  remark: string
  lines: TransferLine[]
}) =>
  request<TransferDetail>({ method: 'POST', url: '/transfers', data: { ...data, lines: writableLines(data.lines) } })
export const updateTransfer = (
  id: number,
  data: {
    version: number
    sourceWarehouseId: number
    targetWarehouseId: number
    remark: string
    lines: TransferLine[]
  },
) =>
  request<TransferDetail>({
    method: 'PUT',
    url: `/transfers/${id}`,
    data: { ...data, lines: writableLines(data.lines) },
  })
export const transitionTransfer = (id: number, action: string, version?: number) =>
  request<TransferDetail>({
    method: 'POST',
    url: `/transfers/${id}/${action}`,
    data: version === undefined ? undefined : { version },
  })
