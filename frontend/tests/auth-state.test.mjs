import test from 'node:test'
import assert from 'node:assert/strict'
import { moduleLoader } from './module-harness.mjs'
import { deferred } from './component-harness.mjs'

function setup(api) {
  let token = 'first'
  let clears = 0
  const load = moduleLoader({
    pinia: { defineStore: (_, factory) => factory },
    '@/modules/auth/api': api,
    '@/shared/utils/session': {
      getAccessToken: () => token,
      clearSession: () => {
        token = null
      },
      saveSession: (value) => {
        token = value
      },
    },
    '@/modules/master-data/reference-cache': {
      clearReferenceCache: () => {
        clears++
      },
    },
  })
  return { store: load('@/modules/auth/store').useAuthStore(), token: () => token, clears: () => clears }
}

test('concurrent user refreshes share a request, later refresh sees changed permissions', async () => {
  const pending = deferred()
  let calls = 0
  const { store } = setup({
    getCurrentUser: () => (++calls === 1 ? pending.promise : Promise.resolve({ authorities: [] })),
  })
  const first = store.loadCurrentUser(),
    second = store.loadCurrentUser()
  assert.equal(calls, 1)
  pending.resolve({ authorities: ['SECURITY_USER_READ'] })
  assert.equal(await first, true)
  assert.equal(await second, true)
  assert.equal(store.can('SECURITY_USER_READ'), true)
  await store.loadCurrentUser()
  assert.equal(calls, 2)
  assert.equal(store.can('SECURITY_USER_READ'), false)
})

test('logout invalidates pending user response and clears reference data', async () => {
  const pending = deferred()
  const { store, token, clears } = setup({ getCurrentUser: () => pending.promise })
  const request = store.loadCurrentUser()
  store.logout()
  pending.resolve({ authorities: ['ADMIN'] })
  assert.equal(await request, false)
  assert.equal(store.user.value, null)
  assert.equal(token(), null)
  assert.equal(clears(), 1)
})

test('failed current-user load after login removes the half-created session', async () => {
  const { store, token } = setup({
    login: async () => ({ accessToken: 'new', expiresAt: 'future' }),
    getCurrentUser: async () => {
      throw new Error('network')
    },
  })
  await assert.rejects(store.login({}), /CURRENT_USER_LOAD_FAILED/)
  assert.equal(token(), null)
  assert.equal(store.user.value, null)
  assert.equal(store.loading.value, false)
})

test('pending login cannot restore a token after logout', async () => {
  const pending = deferred()
  const { store, token } = setup({ login: () => pending.promise })
  const request = store.login({})
  store.logout()
  pending.resolve({ accessToken: 'late', expiresAt: 'future' })
  await assert.rejects(request, /LOGIN_CANCELLED/)
  assert.equal(token(), null)
})

test('protected navigation rechecks permission even when already authenticated', async () => {
  let guard,
    calls = 0
  const auth = {
    authenticated: true,
    loadCurrentUser: async () => {
      calls++
      return true
    },
    can: () => false,
  }
  const load = moduleLoader(
    {
      'vue-router': {
        createWebHistory() {},
        createRouter: () => ({
          beforeEach: (fn) => {
            guard = fn
          },
        }),
      },
      '@/modules/auth/store': { useAuthStore: () => auth },
      '@/shared/utils/session': { getAccessToken: () => 'token' },
    },
    { document: {} },
  )
  load('@/router/index')
  const result = await guard({
    name: 'security-users',
    fullPath: '/security/users',
    meta: { authority: 'SECURITY_USER_READ' },
    matched: [{ meta: { requiresAuth: true } }],
  })
  assert.equal(calls, 1)
  assert.equal(result.name, 'forbidden')
})

test('malformed local expiry is rejected and removed', () => {
  const values = new Map([
    ['stockpilot.accessToken', 'token'],
    ['stockpilot.expiresAt', 'invalid'],
  ])
  const load = moduleLoader(
    {},
    { localStorage: { getItem: (key) => values.get(key), removeItem: (key) => values.delete(key) } },
  )
  assert.equal(load('@/shared/utils/session').getAccessToken(), null)
  assert.equal(values.size, 0)
})

for (const [requestToken, shouldClear] of [
  ['old-token', false],
  ['current-token', true],
]) {
  test(`401 for ${requestToken} only clears its own session`, async () => {
    let onError,
      clears = 0,
      redirects = 0,
      messages = 0
    const client = {
      interceptors: {
        request: { use() {} },
        response: {
          use: (_, handler) => {
            onError = handler
          },
        },
      },
    }
    const load = moduleLoader(
      {
        axios: { default: { create: () => client } },
        'element-plus': {
          ElMessage: {
            error: () => {
              messages++
            },
          },
        },
        './session': {
          getAccessToken: () => 'current-token',
          clearSession: () => {
            clears++
          },
        },
      },
      {
        window: {
          location: {
            pathname: '/dashboard',
            search: '',
            replace: () => {
              redirects++
            },
          },
        },
      },
    )
    load('@/shared/utils/request')
    const error = {
      response: { status: 401 },
      config: { url: '/auth/me', headers: { Authorization: `Bearer ${requestToken}` } },
    }
    await assert.rejects(onError(error))
    assert.equal(clears, shouldClear ? 1 : 0)
    assert.equal(redirects, shouldClear ? 1 : 0)
    assert.equal(messages, shouldClear ? 1 : 0)
  })
}
