// Date-picker values represent local wall time, including date-only midnight.
export function parseTaskDate(value) {
  if (value instanceof Date) return new Date(value.getTime())
  if (/^\d{4}-\d{2}-\d{2}$/.test(value)) {
    const [year, month, day] = value.split('-').map(Number)
    return new Date(year, month - 1, day)
  }
  return new Date(value.replace(' ', 'T'))
}

export function toTaskDateOnly(value) {
  const date = parseTaskDate(value)
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}
