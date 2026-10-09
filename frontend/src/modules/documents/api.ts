import type { PageResult } from '@/shared/types/api'
import type {
  DocumentDetail,
  DocumentKind,
  DocumentLine,
  DocumentStatus,
  DocumentSummary,
} from '@/modules/documents/types'
import { request } from '@/shared/utils/request'

const base = (kind: DocumentKind) => (kind === 'purchase' ? '/inbound/purchase-receipts' : '/outbound/sales-orders')
const noKey = (kind: DocumentKind) => (kind === 'purchase' ? 'receiptNo' : 'outboundNo')
function summary(kind: DocumentKind, raw: Record<string, unknown>): DocumentSummary {
  return { ...(raw as unknown as DocumentSummary), no: String(raw[noKey(kind)]) }
}
function detail(kind: DocumentKind, raw: Record<string, unknown>): DocumentDetail {
  return { ...(raw as unknown as DocumentDetail), no: String(raw[noKey(kind)]) }
}
function writableLines(lines: DocumentLine[]) {
  return lines.map(({ locationId, skuId, quantity }) => ({ locationId, skuId, quantity }))
}

export async function pageDocuments(
  kind: DocumentKind,
  params: { page: number; size: number; no?: string; warehouseId?: number; status?: DocumentStatus },
) {
  const query = {
    ...params,
    [noKey(kind)]: params.no,
    no: undefined,
    status: kind === 'sales' ? params.status : undefined,
  }
  const result = await request<PageResult<Record<string, unknown>>>({ method: 'GET', url: base(kind), params: query })
  return { ...result, records: result.records.map((item) => summary(kind, item)) }
}
export async function getDocument(kind: DocumentKind, id: number) {
  return detail(kind, await request<Record<string, unknown>>({ method: 'GET', url: `${base(kind)}/${id}` }))
}
export async function createDocument(
  kind: DocumentKind,
  data: { no: string; warehouseId: number; remark: string; lines: DocumentLine[] },
) {
  const body = { ...data, [noKey(kind)]: data.no, no: undefined, lines: writableLines(data.lines) }
  return detail(kind, await request<Record<string, unknown>>({ method: 'POST', url: base(kind), data: body }))
}
export async function updateDocument(
  kind: DocumentKind,
  id: number,
  data: { version: number; warehouseId: number; remark: string; lines: DocumentLine[] },
) {
  return detail(
    kind,
    await request<Record<string, unknown>>({
      method: 'PUT',
      url: `${base(kind)}/${id}`,
      data: { ...data, lines: writableLines(data.lines) },
    }),
  )
}
export async function transitionDocument(kind: DocumentKind, id: number, action: string, version?: number) {
  return detail(
    kind,
    await request<Record<string, unknown>>({
      method: 'POST',
      url: `${base(kind)}/${id}/${action}`,
      data: version === undefined ? undefined : { version },
    }),
  )
}
