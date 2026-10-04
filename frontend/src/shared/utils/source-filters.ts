// Only known bounded filter values are accepted from assistant source links.
export function sourceFilters(search = typeof window === 'undefined' ? '' : window.location.search) {
  if (!search) return { skuId: undefined, warehouseId: undefined, locationId: undefined, businessNo: '' }
  const query = new URLSearchParams(search)
  const id = (key: string) => {
    const raw = query.get(key)
    const value = raw && /^[1-9]\d*$/.test(raw) ? Number(raw) : undefined
    return value && Number.isSafeInteger(value) ? value : undefined
  }
  const raw = query.get('businessNo') ?? ''
  const date = (key: string) => {
    const value = query.get(key) ?? ''
    if (!/^\d{4}-\d{2}-\d{2}$/.test(value) || Number(value.slice(0, 4)) < 1000 || Number(value.slice(0, 4)) > 9998)
      return undefined
    const parsed = new Date(`${value}T00:00:00Z`)
    return !Number.isNaN(parsed.getTime()) && parsed.toISOString().slice(0, 10) === value ? value : undefined
  }
  const start = date('startDate'),
    end = date('endDate')
  const validRange = start && end && start <= end && (Date.parse(end) - Date.parse(start)) / 86400000 < 92
  return {
    skuId: id('skuId'),
    warehouseId: id('warehouseId'),
    locationId: id('locationId'),
    businessNo: /^[A-Za-z0-9_-]{2,64}$/.test(raw) ? raw : '',
    startDate: validRange ? start : undefined,
    endDate: validRange ? end : undefined,
  }
}
