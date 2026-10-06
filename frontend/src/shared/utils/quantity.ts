// DECIMAL(19,4) values must never pass through JavaScript Number.
export function isQuantity(value: unknown, allowZero = false): value is string {
  return (
    typeof value === 'string' &&
    /^(0|[1-9]\d{0,14})(\.\d{1,4})?$/.test(value) &&
    (allowZero || !/^0(?:\.0{1,4})?$/.test(value))
  )
}

export function compareQuantities(left: string, right: string): number {
  if (!isQuantity(left, true) || !isQuantity(right, true)) throw new Error('Invalid quantity')
  const units = (value: string) => {
    const [integer, fraction = ''] = value.split('.')
    return BigInt(integer) * 10000n + BigInt(fraction.padEnd(4, '0'))
  }
  const a = units(left),
    b = units(right)
  return a < b ? -1 : a > b ? 1 : 0
}
