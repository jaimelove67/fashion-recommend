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

for (const operation of ['add', 'upload', 'edit', 'delete']) {
  for (const responseOrder of ['old-read-first', 'committed-read-first', 'write-first']) {
    test(`wardrobe ${operation} and an overlapping read preserve every item when ${responseOrder}`, async t => {
      const readResponse = deferred(), writeResponse = deferred(), writeStarted = deferred()
      const original = [{ id: 1, name: 'Original coat', category: '外套', color: '雾蓝' }, { id: 2, name: 'Existing shoes' }]
      const changed = { id: operation === 'edit' ? 1 : 3, name: 'Updated garment', category: '外套', color: '雾蓝' }
      const expected = operation === 'delete' ? original.slice(1)
        : operation === 'edit' ? [changed, original[1]] : [...original, changed]
      t.mock.method(globalThis, 'fetch', async (input, options = {}) => {
        if (String(input).endsWith('/csrf')) return apiResponse(200, { token: 'fixture', headerName: 'X-XSRF-TOKEN' })
        if (options.method === 'GET') return readResponse.promise
        writeStarted.resolve()
        return writeResponse.promise
      })
      const app = useFashionApp()
      app.state.authPhase = 'authenticated'
      app.state.authUser = { username: 'alice', authorities: [] }
      // The initial GET has not populated the local list yet.
      const reading = app.loadWardrobe()
      if (operation === 'edit') app.editGarment(original[0])
      else setGarmentForm(app, changed.name)
      if (operation === 'upload') app.state.selectedImage = new Blob(['fixture'], { type: 'image/png' })
      const writing = operation === 'delete' ? app.deleteGarment(1) : app.addGarment()
      await writeStarted.promise

      if (responseOrder !== 'write-first') {
        // A fast GET can see either the old snapshot or an already committed write.
        readResponse.resolve(apiResponse(200, responseOrder === 'committed-read-first' ? expected : original))
        await reading
        writeResponse.resolve(apiResponse(200, operation === 'delete' ? null : changed))
        await writing
      } else {
        writeResponse.resolve(apiResponse(200, operation === 'delete' ? null : changed))
        await writing
        readResponse.resolve(apiResponse(200, original))
        await reading
      }

      const sorted = items => [...items].sort((left, right) => left.id - right.id)
      assert.deepEqual(sorted(app.state.wardrobe), sorted(expected))
      assert.equal(app.state.wardrobeLoading, false)
      assert.equal(app.state.adding, false)
      assert.equal(app.state.deletingId, null)
    })
  }
}

test('a failed wardrobe write leaves the overlapping server snapshot intact', async t => {
  const readResponse = deferred()
  t.mock.method(globalThis, 'fetch', async (input, options = {}) => {
    if (String(input).endsWith('/csrf')) return apiResponse(200, { token: 'fixture', headerName: 'X-XSRF-TOKEN' })
    return options.method === 'GET' ? readResponse.promise : apiResponse(500, null, 'Write failed')
  })
  const app = useFashionApp()
  app.state.authPhase = 'authenticated'
  app.state.authUser = { username: 'alice', authorities: [] }
  const reading = app.loadWardrobe()
  assert.equal(await app.deleteGarment(1), false)
  const original = [{ id: 1, name: 'Still present' }]
  readResponse.resolve(apiResponse(200, original))
  await reading
  assert.deepEqual(app.state.wardrobe, original)
  assert.equal(app.state.wardrobeLoading, false)
})

test('an old wardrobe snapshot and its successful writes never carry into the next account', async t => {
  const oldRead = deferred(), oldStarted = deferred()
  let aliceReads = 0
  const api = createApiMock({ onWardrobeGet(call) {
    if (call.actor === 'alice' && ++aliceReads === 2) {
      oldStarted.resolve()
      return oldRead.promise
    }
    return apiResponse(200, [{ id: 1, name: `${call.actor} garment` }])
  } })
  t.mock.method(globalThis, 'fetch', api.fetch)
  const app = useFashionApp()
  await login(app, 'alice')
  const reading = app.loadWardrobe()
  await oldStarted.promise
  setGarmentForm(app, 'Alice new garment')
  await app.addGarment()
  await app.logout()
  await login(app, 'bob')
  oldRead.resolve(apiResponse(200, [{ id: 1, name: 'alice garment' }]))
  await reading
  assert.deepEqual(app.state.wardrobe, [{ id: 1, name: 'bob garment' }])
  assert.equal(app.state.wardrobeLoading, false)
  assert.equal(app.state.error, '')
})

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

test('a network failure before login shows a readable message and never submits credentials', async t => {
  const requests = []
  t.mock.method(globalThis, 'fetch', async input => {
    requests.push(String(input))
    throw new TypeError('Failed to fetch')
  })
  const app = useFashionApp()
  assert.equal(await app.login({ username: 'alice', password: 'mock-password' }), false)
  assert.equal(app.state.authError, '无法连接服务器，请检查网络或服务是否启动后重试。')
  assert.equal(app.state.authPhase, 'guest')
  assert.equal(app.state.authUser, null)
  assert.equal(app.state.authSubmitting, false)
  assert.deepEqual(requests, ['/api/v1/auth/csrf'])
})

for (const failedStage of ['login', 'post-login-csrf', 'current-user']) {
  test(`a network failure during ${failedStage} returns to the guest state without replaying credentials`, async t => {
    const api = createApiMock()
    const requests = []
    let csrfRequests = 0
    t.mock.method(globalThis, 'fetch', async (input, options) => {
      const path = String(input)
      requests.push(path)
      if (path === '/api/v1/auth/csrf') csrfRequests += 1
      if ((failedStage === 'login' && path === '/api/v1/auth/login')
          || (failedStage === 'post-login-csrf' && path === '/api/v1/auth/csrf' && csrfRequests === 2)
          || (failedStage === 'current-user' && path === '/api/v1/auth/me')) {
        throw new TypeError('Failed to fetch')
      }
      return api.fetch(input, options)
    })
    const app = useFashionApp()
    app.state.authUser = { username: 'previous-reader', authorities: [] }
    app.state.authPhase = 'authenticated'
    app.state.wardrobe = [{ id: 11, name: 'Previous wardrobe fixture' }]
    app.state.history = [{ id: 22 }]
    app.state.profile = { displayName: 'Previous profile fixture' }
    app.state.tryOnFavorites = [{ id: 33 }]
    app.state.currentRecommendation = { id: 44 }
    app.state.recommendationForm.styleHint = 'Previous private draft fixture'

    assert.equal(await app.login({ username: 'alice', password: 'mock-password' }), false)
    assert.equal(app.state.authError, '无法连接服务器，请检查网络或服务是否启动后重试。')
    assert.equal(app.state.authPhase, 'guest')
    assert.equal(app.state.authUser, null)
    assert.equal(app.state.authSubmitting, false)
    assert.deepEqual(app.state.wardrobe, [])
    assert.deepEqual(app.state.history, [])
    assert.deepEqual(app.state.tryOnFavorites, [])
    assert.equal(app.state.profile, null)
    assert.equal(app.state.currentRecommendation, null)
    assert.equal(app.state.recommendationForm.styleHint, '')
    assert.equal(requests.filter(path => path === '/api/v1/auth/login').length, 1)
    assert.equal(requests.some(path => path.startsWith('/api/v1/me/')), false)
  })
}

test('API network errors retain their original cause and a distinct network marker', async t => {
  const cause = new TypeError('Failed to fetch')
  t.mock.method(globalThis, 'fetch', async () => { throw cause })
  const app = useFashionApp()
  await assert.rejects(app.loadRegistrationCaptcha(), error => {
    assert.equal(error.message, '无法连接服务器，请检查网络或服务是否启动后重试。')
    assert.equal(error.cause, cause)
    assert.equal(error.networkError, true)
    assert.equal(error.status, undefined)
    return true
  })
})

test('aborted requests and other non-network errors are preserved unchanged', async t => {
  const app = useFashionApp()
  for (const cause of [new DOMException('The operation was aborted.', 'AbortError'), new Error('Application fixture error')]) {
    const mockedFetch = t.mock.method(globalThis, 'fetch', async () => { throw cause })
    await assert.rejects(app.loadRegistrationCaptcha(), error => error === cause && error.networkError === undefined)
    mockedFetch.mock.restore()
  }
})

test('a rejected login retains the server authentication message rather than becoming a network error', async t => {
  const api = createApiMock()
  let loginAttempts = 0
  t.mock.method(globalThis, 'fetch', (input, options) => {
    if (String(input) === '/api/v1/auth/login') {
      loginAttempts += 1
      return Promise.resolve(apiResponse(401, null, '账号或密码错误。'))
    }
    return api.fetch(input, options)
  })
  const app = useFashionApp()
  assert.equal(await app.login({ username: 'alice', password: 'mock-password' }), false)
  assert.equal(app.state.authError, '账号或密码错误。')
  assert.equal(app.state.authPhase, 'guest')
  assert.equal(app.state.authSubmitting, false)
  assert.equal(loginAttempts, 1)
})

test('HTTP permission and service failures retain their status without a network marker', async t => {
  const app = useFashionApp()
  for (const status of [401, 403, 500]) {
    const mockedFetch = t.mock.method(globalThis, 'fetch', async () => apiResponse(status, null, `HTTP fixture ${status}`))
    await assert.rejects(app.loadRegistrationCaptcha(), error => {
      assert.equal(error.status, status)
      assert.equal(error.message, `HTTP fixture ${status}`)
      assert.equal(error.networkError, undefined)
      return true
    })
    mockedFetch.mock.restore()
  }
})

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
