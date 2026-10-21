import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import vm from 'node:vm'

const loginSource = fs.readFileSync(new URL('../src/views/Login.vue', import.meta.url), 'utf8')
const script = loginSource.match(/<script setup>([\s\S]*?)<\/script>/)[1]
  .replace(/^import .* from .*$/gm, '')

function loginPage(saved, response = { code: 1, data: { token: 'test-token', userId: 1 } }) {
  const storage = new Map(saved === undefined ? [] : [['rememberedCredentials', saved]])
  const logs = []
  const sandbox = {
    ref: value => ({ value }),
    onMounted: callback => callback(),
    useRouter: () => ({ push() {} }),
    useI18n: () => ({ locale: { value: 'en-US' } }),
    clearUserInfoCache() {},
    ElMessage: { error() {}, success() {} },
    localStorage: {
      getItem: key => storage.get(key) ?? null,
      setItem: (key, value) => storage.set(key, value),
      removeItem: key => storage.delete(key)
    },
    console: { log: (...args) => logs.push(args), error: (...args) => logs.push(args) },
    request: { post: async () => {
      if (response instanceof Error) throw response
      return response
    } }
  }
  vm.runInNewContext(script + '\nglobalThis.page = { login, loginForm, rememberMe, handleRememberMeChange }', sandbox)
  return { ...sandbox.page, storage, logs }
}

test('loading legacy remembered credentials removes the saved password without prefilling it', () => {
  const page = loginPage(JSON.stringify({ userName: 'alice', userPassword: 'old-secret' }))
  assert.equal(page.loginForm.value.userName, 'alice')
  assert.equal(page.loginForm.value.userPassword, '')
  assert.deepEqual(JSON.parse(page.storage.get('rememberedCredentials')), { userName: 'alice' })
  assert.equal(page.rememberMe.value, true)
})

test('successful remember-username login never persists the plaintext password', async () => {
  const page = loginPage()
  page.loginForm.value = { userName: 'alice', userPassword: 'private-password' }
  page.rememberMe.value = true
  assert.equal(await page.login(), true)
  assert.deepEqual(JSON.parse(page.storage.get('rememberedCredentials')), { userName: 'alice' })
  assert.equal(page.storage.get('token'), 'test-token')
  assert(!JSON.stringify([...page.storage.values()]).includes('private-password'))
  assert.equal(page.logs.length, 0)
})

test('invalid saved credentials are removed and unchecking remember clears them', () => {
  const invalid = loginPage('not-json')
  assert(!invalid.storage.has('rememberedCredentials'))
  const page = loginPage(JSON.stringify({ userName: 'alice' }))
  page.handleRememberMeChange(false)
  assert(!page.storage.has('rememberedCredentials'))
})

test('failed login does not log the request error containing credentials', async () => {
  const page = loginPage(undefined, new Error('Axios config: secret-password'))
  assert.equal(await page.login(), false)
  assert.equal(page.logs.length, 0)
  assert(!page.storage.has('token'))
})

test('registration does not log errors that may contain request credentials', () => {
  const source = fs.readFileSync(new URL('../src/views/Register.vue', import.meta.url), 'utf8')
  assert(!/console\.(?:log|error)\(/.test(source))
})
