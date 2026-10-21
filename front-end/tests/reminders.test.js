import test from 'node:test'
import assert from 'node:assert/strict'
import { createReminderPoller, reminderKey } from '../src/utils/reminders.js'

const reminder = (revision = 1, taskId = 7) => ({
  taskId, taskName: 'Due task', remindTime: '2026-10-20T10:00:00', revision
})

function deferred() {
  let resolve, reject
  const promise = new Promise((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}

function harness(overrides = {}) {
  const timers = new Map()
  const events = []
  const errors = []
  let nextTimer = 0
  const poller = createReminderPoller({
    fetchDue: async () => [reminder()],
    notify: (item) => {
      events.push(`show:${reminderKey(item)}`)
      return () => events.push(`close:${reminderKey(item)}`)
    },
    acknowledge: async (item) => { events.push(`ack:${reminderKey(item)}`) },
    onError: (error) => errors.push(error),
    setTimer: (callback, delay) => {
      const id = ++nextTimer
      timers.set(id, { callback, delay })
      return id
    },
    clearTimer: (id) => timers.delete(id),
    ...overrides
  })
  return { poller, timers, events, errors }
}

test('identity includes both task and revision, not reminder time alone', () => {
  assert.equal(reminderKey(reminder()), '7:1')
  assert.notEqual(reminderKey(reminder()), reminderKey(reminder(2)))
  assert.notEqual(reminderKey(reminder()), reminderKey(reminder(1, 8)))
})

test('starts immediately, displays before ack, and schedules a 15-second retry', async () => {
  const { poller, timers, events } = harness()
  await poller.start()
  assert.deepEqual(events, ['show:7:1', 'ack:7:1'])
  assert.equal(timers.size, 1)
  assert.equal([...timers.values()][0].delay, 15000)
  poller.stop()
  assert.equal(timers.size, 0)
  assert.deepEqual(events, ['show:7:1', 'ack:7:1', 'close:7:1'])
})

test('repeated start and poll cannot overlap a pending due request', async () => {
  const response = deferred()
  let requests = 0
  const { poller, timers } = harness({ fetchDue: () => { requests++; return response.promise } })
  const first = poller.start()
  await poller.start()
  await poller.poll()
  assert.equal(requests, 1)
  assert.equal(timers.size, 0)
  response.resolve([])
  await first
  assert.equal(timers.size, 1)
  await poller.poll()
  assert.equal(requests, 2)
  assert.equal(timers.size, 1, 'manual polling replaces, rather than duplicates, the timer')
  poller.stop()
})

test('unmount before due response prevents notifications, acks and late timers', async () => {
  const response = deferred()
  const { poller, timers, events, errors } = harness({ fetchDue: () => response.promise })
  const first = poller.start()
  poller.stop()
  response.resolve([reminder()])
  await first
  await poller.start()
  await poller.poll()
  assert.deepEqual(events, [])
  assert.deepEqual(errors, [])
  assert.equal(timers.size, 0)
})

test('unmount during ack closes notifications and does not ack subsequent items', async () => {
  const ack = deferred()
  const enteredAck = deferred()
  let acknowledgements = 0
  const { poller, timers, events, errors } = harness({
    fetchDue: async () => [reminder(), reminder(1, 8)],
    acknowledge: () => { acknowledgements++; enteredAck.resolve(); return ack.promise }
  })
  const first = poller.start()
  await enteredAck.promise
  await poller.poll()
  assert.equal(acknowledgements, 1)
  poller.stop()
  ack.reject(new Error('late failure'))
  await first
  assert.equal(acknowledgements, 1)
  assert.deepEqual(events, ['show:7:1', 'show:8:1', 'close:7:1', 'close:8:1'])
  assert.deepEqual(errors, [])
  assert.equal(timers.size, 0)
})

test('failed ack retries even if due list is empty, without redisplaying in this page', async () => {
  let acknowledgements = 0
  let fetches = 0
  const { poller, events, errors } = harness({
    fetchDue: async () => ++fetches === 2 ? [] : [reminder()],
    acknowledge: async () => {
      if (++acknowledgements === 1) throw new Error('network lost')
    }
  })
  await poller.start()
  assert.equal(acknowledgements, 1)
  assert.equal(errors.length, 1)
  await poller.poll()
  assert.equal(acknowledgements, 2)
  await poller.poll()
  assert.equal(acknowledgements, 2, 'successful ack is not queued again')
  assert.deepEqual(events, ['show:7:1'])
  poller.stop()
})

for (const status of [404, 409]) {
  test(`HTTP ${status} invalidates pending revision, closes its notification and stops retrying`, async () => {
    let acknowledgements = 0
    const { poller, events, errors } = harness({
      acknowledge: async () => {
        acknowledgements++
        throw { response: { status } }
      }
    })
    await poller.start()
    await poller.poll()
    assert.equal(acknowledgements, 1)
    assert.deepEqual(events, ['show:7:1', 'close:7:1'])
    assert.deepEqual(errors, [])
    poller.stop()
    assert.equal(events.length, 2, 'stale notification is not closed twice')
  })
}

test('a revised task can notify again; duplicate rows and successful deliveries cannot', async () => {
  let revision = 1
  const { poller, events } = harness({ fetchDue: async () => [reminder(revision), reminder(revision)] })
  await poller.start()
  await poller.poll()
  revision = 2
  await poller.poll()
  assert.deepEqual(events, ['show:7:1', 'ack:7:1', 'show:7:2', 'ack:7:2'])
  poller.stop()
})

test('fetch failures are retried, without acknowledging unseen reminders', async () => {
  let fetches = 0
  const { poller, timers, events, errors } = harness({
    fetchDue: async () => {
      if (++fetches === 1) throw new Error('offline')
      return [reminder()]
    }
  })
  await poller.start()
  assert.equal(errors.length, 1)
  assert.equal(timers.size, 1)
  assert.deepEqual(events, [])
  await poller.poll()
  assert.deepEqual(events, ['show:7:1', 'ack:7:1'])
  poller.stop()
})

test('notification failure is never acknowledged and can retry display later', async () => {
  let displays = 0
  let acknowledgements = 0
  const { poller, errors } = harness({
    notify: () => { if (++displays === 1) throw new Error('display unavailable') },
    acknowledge: async () => { acknowledgements++ }
  })
  await poller.start()
  assert.equal(acknowledgements, 0)
  await poller.poll()
  assert.equal(displays, 2)
  assert.equal(acknowledgements, 1)
  assert.equal(errors.length, 1)
  poller.stop()
})

test('logout while due request is pending discards its response', async () => {
  let loggedIn = true
  const response = deferred()
  const { poller, events, timers } = harness({
    isActive: () => loggedIn,
    fetchDue: () => response.promise
  })
  const first = poller.start()
  loggedIn = false
  response.resolve([reminder()])
  await first
  assert.deepEqual(events, [])
  assert.equal(timers.size, 0)
})

test('a stopped timer callback cannot restart requests', async () => {
  let requests = 0
  const { poller, timers } = harness({ fetchDue: async () => { requests++; return [] } })
  await poller.start()
  const { callback } = [...timers.values()][0]
  poller.stop()
  callback()
  await Promise.resolve()
  assert.equal(requests, 1)
  assert.equal(timers.size, 0)
})

test('new page instance may redeliver an unacknowledged reminder (no persistent deduplication)', async () => {
  const options = { acknowledge: async () => { throw new Error('offline') } }
  const first = harness(options)
  await first.poller.start()
  first.poller.stop()
  const second = harness(options)
  await second.poller.start()
  assert.deepEqual(second.events, ['show:7:1'])
  second.poller.stop()
})
