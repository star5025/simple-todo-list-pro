import test from 'node:test'
import assert from 'node:assert/strict'
import { parseTaskDate, toTaskDateOnly } from '../src/utils/taskDates.js'

const localParts = (date) => [
  date.getFullYear(), date.getMonth() + 1, date.getDate(),
  date.getHours(), date.getMinutes(), date.getSeconds(), date.getMilliseconds()
]

test('datetime to date keeps the local day near midnight in either timezone direction', () => {
  assert.equal(toTaskDateOnly(new Date(2026, 9, 20, 0, 15)), '2026-10-20')
  assert.equal(toTaskDateOnly(new Date(2026, 9, 20, 23, 45)), '2026-10-20')
  assert.equal(toTaskDateOnly('2026-10-20 00:15'), '2026-10-20')
  assert.equal(toTaskDateOnly('2026-10-20 23:45'), '2026-10-20')
})

test('date-only values are local midnight, including when converted repeatedly', () => {
  const date = parseTaskDate('2026-10-20')
  assert.deepEqual(localParts(date), [2026, 10, 20, 0, 0, 0, 0])
  assert.equal(toTaskDateOnly('2026-10-20'), '2026-10-20')
  assert.equal(toTaskDateOnly(toTaskDateOnly(date)), '2026-10-20')
})

test('datepicker space-separated datetime is normalized to a local ISO datetime', () => {
  const date = parseTaskDate('2026-10-20 09:30')
  assert.deepEqual(localParts(date), [2026, 10, 20, 9, 30, 0, 0])
  assert.equal(date.getTime(), parseTaskDate('2026-10-20T09:30').getTime())
})

test('Date defaults are cloned without changing the original instant', () => {
  const original = new Date(2026, 9, 20, 9, 30)
  const parsed = parseTaskDate(original)
  assert.notEqual(parsed, original)
  assert.equal(parsed.getTime(), original.getTime())
  parsed.setHours(23, 59, 59, 999)
  assert.equal(original.getHours(), 9)
})

test('date-only due time stays on the selected local day at end of day', () => {
  const due = parseTaskDate('2026-10-20')
  due.setHours(23, 59, 59, 999)
  assert.deepEqual(localParts(due), [2026, 10, 20, 23, 59, 59, 999])
  assert.ok(parseTaskDate('2026-10-20 23:59') < due)
})

test('local day boundaries survive a daylight-saving transition', () => {
  const start = parseTaskDate('2026-03-08')
  const due = parseTaskDate('2026-03-08')
  due.setHours(23, 59, 59, 999)
  assert.deepEqual(localParts(start), [2026, 3, 8, 0, 0, 0, 0])
  assert.deepEqual(localParts(due), [2026, 3, 8, 23, 59, 59, 999])
  if (process.env.TZ === 'America/Los_Angeles') {
    assert.equal(due.getTime() - start.getTime() + 1, 23 * 60 * 60 * 1000)
  }
})

test('submissions remain explicit UTC Z timestamps', () => {
  const expectedMidnight = {
    'Asia/Shanghai': '2026-10-19T16:00:00.000Z',
    'America/Los_Angeles': '2026-10-20T07:00:00.000Z',
    UTC: '2026-10-20T00:00:00.000Z'
  }
  const serialized = parseTaskDate('2026-10-20').toISOString()
  assert.ok(serialized.endsWith('Z'))
  if (expectedMidnight[process.env.TZ]) {
    assert.equal(serialized, expectedMidnight[process.env.TZ])
  }
  assert.equal(parseTaskDate('2026-10-20T09:30:00+08:00').toISOString(), '2026-10-20T01:30:00.000Z')
  assert.equal(parseTaskDate('2026-10-20T01:30:00Z').toISOString(), '2026-10-20T01:30:00.000Z')
})
