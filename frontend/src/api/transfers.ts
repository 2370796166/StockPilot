import type { PageResult } from '../types/api'
import type { TransferDetail, TransferLine, TransferStatus, TransferSummary } from '../types/business'
import { request } from '../utils/request'
export const pageTransfers = (params: { page: number; size: number; transferNo?: string; sourceWarehouseId?: number; targetWarehouseId?: number; status?: TransferStatus }) => request<PageResult<TransferSummary>>({ method: 'GET', url: '/transfers', params })
export const getTransfer = (id: number) => request<TransferDetail>({ method: 'GET', url: `/transfers/${id}` })
export const createTransfer = (data: { transferNo: string; sourceWarehouseId: number; targetWarehouseId: number; remark: string; lines: TransferLine[] }) => request<TransferDetail>({ method: 'POST', url: '/transfers', data })
export const updateTransfer = (id: number, data: { version: number; sourceWarehouseId: number; targetWarehouseId: number; remark: string; lines: TransferLine[] }) => request<TransferDetail>({ method: 'PUT', url: `/transfers/${id}`, data })
export const transitionTransfer = (id: number, action: string, version?: number) => request<TransferDetail>({ method: 'POST', url: `/transfers/${id}/${action}`, data: version === undefined ? undefined : { version } })
