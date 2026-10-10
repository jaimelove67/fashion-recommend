import assert from 'node:assert/strict'
import { test } from 'node:test'
import { useFashionApp } from '../src/composables/useFashionApp.js'

const originalFetch = globalThis.fetch

function apiResponse(status, data, message = 'ok') {
  return new Response(JSON.stringify({
    code: status >= 400 ? status : 0,
    message,
    data
  }), {
    status,
    headers: { 'content-type': 'application/json' }
  })
}

function deferred() {
  let resolve
  const promise = new Promise((complete) => { resolve = complete })
  return { promise, resolve }
}

function createApiMock({ onCsrf, onWardrobeGet, onWardrobePost, onFavoriteGet, onFavoritePost } = {}) {
  let identity = null
  let csrfCalls = 0
  const calls = []

  const fetch = async (input, options = {}) => {
    const url = new URL(String(input), 'http://localhost')
    const method = String(options.method || 'GET').toUpperCase()
    const call = {
      path: url.pathname,
      method,
      actor: identity,
      body: options.body,
      csrfHeader: new Headers(options.headers).get('X-XSRF-TOKEN')
    }
    calls.push(call)

    if (call.path === '/api/v1/auth/csrf' && method === 'GET') {
      csrfCalls += 1
      if (onCsrf) return onCsrf({ ...call, number: csrfCalls })
      return apiResponse(200, {
        token: `csrf-${identity || 'anon'}`,
        headerName: 'X-XSRF-TOKEN'
      })
    }
    if (call.path === '/api/v1/auth/login' && method === 'POST') {
      identity = new URLSearchParams(options.body).get('username')
      return apiResponse(200, { username: identity, authorities: [] })
    }
    if (call.path === '/api/v1/auth/me' && method === 'GET') {
      if (!identity) return apiResponse(401, null, 'login required')
      return apiResponse(200, { username: identity, authorities: [] })
    }
    if (call.path === '/api/v1/auth/logout' && method === 'POST') {
      identity = null
      return apiResponse(200, null)
    }
    if (call.path === '/api/v1/me/wardrobe' && method === 'GET') {
      return onWardrobeGet ? onWardrobeGet(call) : apiResponse(200, [])
    }
    if (call.path === '/api/v1/me/wardrobe' && method === 'POST') {
      call.body = JSON.parse(options.body)
      return onWardrobePost
        ? onWardrobePost(call)
        : apiResponse(201, { id: 901, ...call.body })
    }
    if (call.path === '/api/v1/me/recommendations' && method === 'GET') {
      return apiResponse(200, { content: [], totalElements: 0, page: 0, hasNext: false })
    }
    if (call.path === '/api/v1/me/style-profile' && method === 'GET') {
      return apiResponse(200, null)
    }
    if (call.path === '/api/v1/me/try-on-favorites' && method === 'GET') {
      return onFavoriteGet ? onFavoriteGet(call) : apiResponse(200, [])
    }
    if (call.path === '/api/v1/me/try-on-favorites' && method === 'POST') {
      return onFavoritePost ? onFavoritePost(call) : apiResponse(201, { id: 1, name: '试穿衬衫', category: '上装' })
    }
    throw new Error(`Unexpected mock request: ${method} ${call.path}`)
  }

  return {
    calls,
    fetch,
    setIdentity(value) { identity = value },
    get identity() { return identity }
  }
}

function setGarmentForm(app, name) {
  app.state.garmentForm = {
    name,
    category: '外套',
    color: '雾蓝',
    style: '通勤',
    imageUrl: ''
  }
}

test('a favorite list completing after an account change never restores the previous account items', async t => {
  const response = deferred(), started = deferred()
  const api = createApiMock({ onFavoriteGet(call) {
    if (call.actor === 'alice') { started.resolve(); return response.promise }
    return apiResponse(200, [{ id: 2, name: 'Bob favorite' }])
  } })
  t.mock.method(globalThis, 'fetch', api.fetch)
  const app = useFashionApp()
  await login(app, 'alice')
  const pending = app.loadTryOnFavorites()
  await started.promise
  await app.logout(); await login(app, 'bob')
  await app.loadTryOnFavorites()
  response.resolve(apiResponse(200, [{ id: 1, name: 'Alice favorite' }]))
  await pending
  assert.deepEqual(app.state.tryOnFavorites.map(item => item.name), ['Bob favorite'])
  assert.equal(app.state.authUser.username, 'bob')
})

test('an older favorite list cannot overwrite a favorite saved while the list is loading', async t => {
  const response = deferred(), started = deferred()
  const api = createApiMock({ onFavoriteGet() { started.resolve(); return response.promise } })
  t.mock.method(globalThis, 'fetch', api.fetch)
  const app = useFashionApp()
  await login(app, 'alice')
  const pending = app.loadTryOnFavorites()
  await started.promise
  await app.saveTryOnFavorite({ name: '试穿衬衫', category: '上装', sourceKind: 'SHOPPING' }, 'data:image/png;base64,aW1hZ2U=')
  response.resolve(apiResponse(200, []))
  await pending
  assert.equal(app.state.tryOnFavorites[0].name, '试穿衬衫')
  assert.equal(app.state.tryOnFavoritesLoading, false)
  const write = api.calls.find(call => call.path.endsWith('/try-on-favorites') && call.method === 'POST')
  assert.equal(write.body.get('expectedUser'), 'alice')
  assert.equal(write.csrfHeader, 'csrf-alice')
})

async function login(app, username) {
  assert.equal(await app.login({ username, password: 'mock-password' }), true)
  assert.equal(app.state.authUser.username, username)
}

test('a delayed 403 write is not replayed under the next account', async (t) => {
  const firstPostStarted = deferred()
  const firstPostResponse = deferred()
  const api = createApiMock({
    onWardrobePost(call) {
      if (call.actor === 'alice') {
        firstPostStarted.resolve(call)
        return firstPostResponse.promise
      }
      return apiResponse(201, { id: 902, ...call.body })
    }
  })
  globalThis.fetch = api.fetch
  t.after(() => { globalThis.fetch = originalFetch })

  const app = useFashionApp()
  await login(app, 'alice')
  setGarmentForm(app, 'Alice draft coat')
  const pendingWrite = app.addGarment()
  await firstPostStarted.promise

  await app.logout()
  await login(app, 'bob')
  firstPostResponse.resolve(apiResponse(403, null, 'stale CSRF'))
  await pendingWrite

  const writes = api.calls.filter((call) => call.path === '/api/v1/me/wardrobe' && call.method === 'POST')
  assert.equal(app.state.authUser.username, 'bob')
  assert.equal(writes.length, 1)
  assert.equal(writes[0].actor, 'alice')
  assert.equal(writes[0].body.name, 'Alice draft coat')
  assert.equal(app.state.error, '')
})

test('a same-session 403 still refreshes CSRF and retries once', async (t) => {
  const api = createApiMock({
    onCsrf(call) {
      return apiResponse(200, {
        token: `csrf-${call.actor || 'anon'}-${call.number}`,
        headerName: 'X-XSRF-TOKEN'
      })
    },
    onWardrobePost(call) {
      const attempts = api.calls.filter((item) => item.path === '/api/v1/me/wardrobe' && item.method === 'POST').length
      if (attempts === 1) return apiResponse(403, null, 'refresh token')
      return apiResponse(201, { id: 903, ...call.body })
    }
  })
  globalThis.fetch = api.fetch
  t.after(() => { globalThis.fetch = originalFetch })

  const app = useFashionApp()
  await login(app, 'alice')
  setGarmentForm(app, 'Alice coat')
  const result = await app.addGarment()

  const attempts = api.calls.filter((call) => call.path === '/api/v1/me/wardrobe' && call.method === 'POST')
  assert.equal(result.name, 'Alice coat')
  assert.equal(attempts.length, 2)
  assert.deepEqual(attempts.map((call) => call.actor), ['alice', 'alice'])
  assert.deepEqual(attempts.map((call) => call.csrfHeader), ['csrf-alice-2', 'csrf-alice-3'])
  assert.equal(app.state.wardrobe[0].name, 'Alice coat')
})

test('a late 401 from the previous account does not expire the current account', async (t) => {
  const oldGetStarted = deferred()
  const oldGetResponse = deferred()
  let aliceWardrobeGets = 0
  const api = createApiMock({
    onWardrobeGet(call) {
      if (call.actor === 'alice' && ++aliceWardrobeGets === 2) {
        oldGetStarted.resolve(call)
        return oldGetResponse.promise
      }
      return apiResponse(200, [])
    }
  })
  globalThis.fetch = api.fetch
  t.after(() => { globalThis.fetch = originalFetch })

  const app = useFashionApp()
  await login(app, 'alice')
  const pendingLoad = app.loadWardrobe()
  await oldGetStarted.promise

  await app.logout()
  await login(app, 'bob')
  oldGetResponse.resolve(apiResponse(401, null, 'old session expired'))
  await pendingLoad

  assert.equal(app.state.authPhase, 'authenticated')
  assert.equal(app.state.authUser.username, 'bob')
  assert.equal(app.state.error, '')
})

test('a CSRF refresh started in the old session cannot dispatch its waiting write or replace the new token', async (t) => {
  const oldCsrfStarted = deferred()
  const oldCsrfResponse = deferred()
  const api = createApiMock({
    onCsrf(call) {
      if (call.number === 1) {
        oldCsrfStarted.resolve(call)
        return oldCsrfResponse.promise
      }
      return apiResponse(200, {
        token: `csrf-${call.actor || 'anon'}-${call.number}`,
        headerName: 'X-XSRF-TOKEN'
      })
    }
  })
  globalThis.fetch = api.fetch
  t.after(() => { globalThis.fetch = originalFetch })

  const app = useFashionApp()
  api.setIdentity('alice')
  app.state.authUser = { username: 'alice', authorities: [] }
  app.state.authPhase = 'authenticated'
  setGarmentForm(app, 'Alice pending coat')
  const pendingWrite = app.addGarment()
  await oldCsrfStarted.promise

  await app.logout()
  await login(app, 'bob')
  oldCsrfResponse.resolve(apiResponse(200, {
    token: 'csrf-alice-delayed',
    headerName: 'X-XSRF-TOKEN'
  }))
  await pendingWrite

  const oldWrites = api.calls.filter((call) => call.path === '/api/v1/me/wardrobe' && call.method === 'POST')
  assert.equal(app.state.authUser.username, 'bob')
  assert.equal(oldWrites.length, 0)
  assert.equal(app.state.error, '')

  setGarmentForm(app, 'Bob coat')
  await app.addGarment()
  const bobWrites = api.calls.filter((call) => call.path === '/api/v1/me/wardrobe' && call.method === 'POST')
  assert.equal(bobWrites.length, 1)
  assert.equal(bobWrites[0].actor, 'bob')
  assert.equal(bobWrites[0].csrfHeader, 'csrf-bob-4')
  assert.equal(bobWrites[0].body.name, 'Bob coat')
})
