export const reminderKey = ({ taskId, revision }) => `${taskId}:${revision}`

// All deduplication is page-local. The server owns durable delivery state.
export function createReminderPoller({
  fetchDue,
  acknowledge,
  notify,
  isActive = () => true,
  onError = () => {},
  interval = 15000,
  setTimer = setTimeout,
  clearTimer = clearTimeout
}) {
  const shown = new Set()
  const pending = new Map()
  const notifications = new Map()
  let timer = null
  let started = false
  let stopped = false
  let inFlight = false

  const closeNotification = (key) => {
    notifications.get(key)?.()
    notifications.delete(key)
  }

  const stop = () => {
    stopped = true
    if (timer !== null) clearTimer(timer)
    timer = null
    for (const key of notifications.keys()) closeNotification(key)
    pending.clear()
  }

  const active = () => {
    if (!stopped && !isActive()) stop()
    return !stopped
  }

  const poll = async () => {
    if (!active() || inFlight) return
    if (timer !== null) clearTimer(timer)
    timer = null
    inFlight = true
    try {
      const reminders = await fetchDue()
      if (!active()) return

      for (const reminder of reminders) {
        const key = reminderKey(reminder)
        if (shown.has(key)) continue
        try {
          // Only queue an acknowledgement after the notification was displayed.
          const close = notify(reminder)
          shown.add(key)
          pending.set(key, reminder)
          if (typeof close === 'function') notifications.set(key, close)
        } catch (error) {
          onError(error)
        }
      }

      for (const [key, reminder] of pending) {
        if (!active()) return
        try {
          await acknowledge(reminder)
          if (!active()) return
          pending.delete(key)
        } catch (error) {
          if (!active()) return
          if (error.response?.status === 409 || error.response?.status === 404) {
            // A changed/completed/deleted task invalidates this revision.
            pending.delete(key)
            closeNotification(key)
          } else {
            onError(error)
          }
        }
      }
    } catch (error) {
      if (active()) onError(error)
    } finally {
      inFlight = false
      // Schedule after completion, so slow requests can never overlap.
      if (started && active()) {
        timer = setTimer(() => {
          timer = null
          void poll()
        }, interval)
      }
    }
  }

  const start = () => {
    if (started || !active()) return
    started = true
    return poll()
  }

  return { start, poll, stop }
}
