import test from 'node:test'
import assert from 'node:assert/strict'
import { mount } from './component-harness.mjs'

function setup(login, query = {}) {
  const redirects = []
  const auth = { loading: false, login }
  const component = mount(
    'modules/auth/LoginView.vue',
    {},
    {
      '@/modules/auth/store': { useAuthStore: () => auth },
      'vue-router': {
        useRoute: () => ({ query }),
        useRouter: () => ({
          replace: async (path) => {
            redirects.push(path)
          },
        }),
      },
    },
  )
  component.state.formRef = { validate: async () => true }
  return { ...component, auth, redirects }
}

test('failed login stays on the form without an unhandled rejection and can retry', async () => {
  let attempts = 0
  const view = setup(
    async (credentials) => {
      assert.equal(credentials.username, 'operator')
      if (++attempts === 1) throw new Error('invalid credentials')
    },
    { redirect: '/inventory/balances' },
  )
  try {
    view.state.form.username = ' operator '
    view.state.form.password = 'wrong'
    await assert.doesNotReject(() => view.state.submit())
    assert.deepEqual(view.redirects, [])
    view.state.form.password = 'correct'
    await view.state.submit()
    assert.equal(attempts, 2)
    assert.deepEqual(view.redirects, ['/inventory/balances'])
  } finally {
    view.unmount()
  }
})

test('invalid fields and an in-progress login never submit credentials', async () => {
  let calls = 0
  const view = setup(async () => {
    calls++
  })
  try {
    view.state.formRef = {
      validate: async () => {
        throw new Error('required')
      },
    }
    await view.state.submit()
    view.state.formRef = { validate: async () => true }
    view.auth.loading = true
    await view.state.submit()
    assert.equal(calls, 0)
    assert.deepEqual(view.redirects, [])
  } finally {
    view.unmount()
  }
})
