import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import vm from 'node:vm'
import { ref, watch, computed, nextTick } from 'vue'

function deferred() {
  let resolve
  const promise = new Promise(done => { resolve = done })
  return { promise, resolve }
}

function listHarness(get) {
  const source = fs.readFileSync(new URL('../src/components/TodoList.vue', import.meta.url), 'utf8')
    .match(/<script setup>([\s\S]*?)<\/script>/)[1].replace(/^import .* from .*$/gm, '')
  const filters = ref({ status: false, orderBy: 'dueTime', page: 1, pageSize: 10 })
  const activated = [], deactivated = [], unmounted = [], watches = [], patches = [], scrolls = []
  const sandbox = {
    ref, computed, nextTick,
    watch: (...args) => { const stop = watch(...args); watches.push(stop); return stop },
    inject: () => filters,
    useI18n: () => ({ t: key => key, locale: ref('en-US') }),
    useRoute: () => ({ name: 'TodoList', fullPath: '/home/list' }),
    useRouter: () => ({ push() {} }),
    useMediaQuery: () => ref(false),
    onActivated: cb => activated.push(cb), onDeactivated: cb => deactivated.push(cb),
    onUnmounted: cb => unmounted.push(cb), defineExpose() {},
    request: { get, patch: async (url, body) => { patches.push(body); return { code: 1 } } },
    ElMessage: { success() {}, error() {}, warning() {} },
    ElMessageBox: { confirm: async () => {} },
    window: { setTimeout: () => 1, dispatchEvent() {} },
    document: { addEventListener() {}, removeEventListener() {}, querySelector: () => ({ scrollTo: ({ top }) => scrolls.push(top) }) },
    CustomEvent: class {}, console: { log() {}, error() {} }
  }
  vm.runInNewContext(source + '\nglobalThis.api = { fetchTodos, todos, total, currentPage, pageSize, toggleFavourite, handleCurrentChange, handleSizeChange }', sandbox)
  return { ...sandbox.api, filters, patches, scrolls,
    activate: () => activated.forEach(cb => cb()),
    deactivate: () => deactivated.forEach(cb => cb()),
    close: () => { deactivated.forEach(cb => cb()); unmounted.forEach(cb => cb()); watches.forEach(stop => stop()) } }
}
const result = name => ({ code: 1, data: { total: 30, records: [{ taskId: 1, taskName: name, favourite: false }] } })

test('list discards stale filter responses and fetches once on activation', async () => {
  const calls = []
  const page = listHarness((url, params) => { const response = deferred(); calls.push({ params, response }); return response.promise })
  try {
    page.activate()
    assert.equal(calls.length, 1)
    page.filters.value = { status: true, orderBy: 'dueTime' }
    await nextTick()
    assert.equal(calls.length, 2)
    calls[1].response.resolve(result('new filter'))
    await nextTick(); await nextTick()
    calls[0].response.resolve(result('old filter'))
    await nextTick(); await nextTick()
    assert.equal(page.todos.value[0].taskName, 'new filter')
  } finally { page.close() }
})

test('return to cached list preserves its page rather than stale filter page', async () => {
  const calls = []
  const page = listHarness(async (url, params) => { calls.push(params); return result('task') })
  try {
    page.activate(); await nextTick()
    page.currentPage.value = 2
    await page.fetchTodos()
    page.deactivate(); page.activate(); await nextTick()
    assert.equal(calls.at(-1).page, 2)
    assert.equal(calls.at(-1).pageSize, 10)
  } finally { page.close() }
})

test('deactivated list cannot accept a late response', async () => {
  const response = deferred()
  const page = listHarness(() => response.promise)
  try {
    page.activate(); page.deactivate()
    response.resolve(result('late'))
    await nextTick(); await nextTick()
    assert.equal(page.todos.value.length, 0)
  } finally { page.close() }
})

test('favourite patches only its field and ignores repeated in-flight clicks', async () => {
  const page = listHarness(async () => result('task'))
  try {
    page.activate(); await nextTick()
    const todo = { taskId: 1, status: false, favourite: false, remindTime: null }
    await Promise.all([page.toggleFavourite(todo), page.toggleFavourite(todo)])
    assert.equal(page.patches.length, 1)
    assert.deepEqual(JSON.parse(JSON.stringify(page.patches[0])), { favourite: true })
    assert.equal(todo.favourite, true)
  } finally { page.close() }
})

test('changing page or page size scrolls the content area back to the top', async () => {
  const page = listHarness(async () => result('task'))
  try {
    page.activate(); await nextTick()
    page.handleCurrentChange(2); await nextTick()
    page.handleSizeChange(20); await nextTick()
    assert.deepEqual(page.scrolls, [0, 0])
    assert.equal(page.currentPage.value, 1)
  } finally { page.close() }
})

test('home marks acknowledged reminders and removes invalidated ones via the close contract', async () => {
  const source = fs.readFileSync(new URL('../src/views/Home.vue', import.meta.url), 'utf8')
    .match(/<script setup>([\s\S]*?)<\/script>/)[1].replace(/^import .* from .*$/gm, '')
  let callbacks
  const stops = []
  const sandbox = {
    ref, provide() {}, watch: (...args) => { stops.push(watch(...args)) },
    useRouter: () => ({ push() {} }), useRoute: () => ({ fullPath: '/home/list' }),
    useMediaQuery: () => ref(false),
    useI18n: () => ({ t: key => key, locale: ref('en-US') }),
    localStorage: { getItem: () => 'fixture' },
    createReminderPoller: options => { callbacks = options; return { start() {}, stop() {} } },
    request: { post: async () => ({ code: 1 }) },
    onMounted() {}, onBeforeUnmount() {}, onBeforeRouteLeave() {}
  }
  try {
    vm.runInNewContext(source + '\nglobalThis.items = reminders', sandbox)
    const reminder = { taskId: 7, revision: 2, taskName: 'task' }
    const close = callbacks.notify(reminder)
    assert.equal(sandbox.items.value[0].acked, false)
    await callbacks.acknowledge(reminder)
    assert.equal(sandbox.items.value[0].acked, true)
    close()
    assert.equal(sandbox.items.value.length, 0)
  } finally { stops.forEach(stop => stop()) }
})
