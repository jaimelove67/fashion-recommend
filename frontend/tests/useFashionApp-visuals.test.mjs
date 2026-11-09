import assert from 'node:assert/strict'
import test from 'node:test'
import { useFashionApp } from '../src/composables/useFashionApp.js'

globalThis.window = { removeEventListener() {} }
const response = data => new Response(JSON.stringify({ code: 0, data }), { headers: { 'Content-Type': 'application/json' } })
const outfit = { id: 42, items: [{ id: 1, color: 'blue' }, { id: 2 }] }
const success = { status: 'SUCCEEDED', imageUrl: '/api/v1/me/recommendations/42/visual/images/asset', modelGender: 'MALE', modelSource: 'DEFAULT' }
function session() {
  const app = useFashionApp()
  app.state.authPhase = 'authenticated'
  app.state.profile = { gender: 'MALE' }
  return app
}

test('visual requests merge concurrent reads and forward explicit regeneration', async () => {
  const app = session()
  const paths = []
  globalThis.fetch = async url => {
    if (String(url).endsWith('/auth/csrf')) return response({ token: 'test', headerName: 'X-XSRF-TOKEN' })
    paths.push(String(url))
    return response(success)
  }
  await Promise.all([app.generateRecommendationVisual(outfit), app.generateRecommendationVisual(outfit)])
  assert.equal(paths.length, 1)
  await app.generateRecommendationVisual(outfit)
  assert.equal(paths.length, 1)
  await app.generateRecommendationVisual(outfit, { force: true })
  assert.equal(paths.at(-1), '/api/v1/me/recommendations/42/visual?force=true')
  await app.generateRecommendationVisual({ ...outfit, items: [{ id: 1, color: 'red' }, { id: 2 }] })
  assert.equal(paths.length, 3)
  app.dispose()
})

test('a refreshed app reads the saved visual again and failed requests remain retryable', async () => {
  let calls = 0
  globalThis.fetch = async url => {
    if (String(url).endsWith('/auth/csrf')) return response({ token: 'test', headerName: 'X-XSRF-TOKEN' })
    calls++
    return response(calls === 2 ? { status: 'FAILED', imageUrl: null } : success)
  }
  const first = session()
  assert.equal((await first.generateRecommendationVisual(outfit)).imageUrl, success.imageUrl)
  first.dispose()
  const refreshed = session()
  assert.equal((await refreshed.generateRecommendationVisual(outfit)).status, 'FAILED')
  assert.equal((await refreshed.generateRecommendationVisual(outfit)).imageUrl, success.imageUrl)
  assert.equal(calls, 3)
  refreshed.dispose()
})

test('a concurrent backend generation is polled through GET without another generation POST', async () => {
  const app = session()
  const methods = []
  const originalTimeout = globalThis.setTimeout
  globalThis.setTimeout = callback => originalTimeout(callback, 0)
  globalThis.fetch = async (url, options) => {
    if (String(url).endsWith('/auth/csrf')) return response({ token: 'test', headerName: 'X-XSRF-TOKEN' })
    methods.push(options.method)
    return response(methods.length === 1 ? { status: 'PENDING' } : success)
  }
  try {
    assert.equal((await app.generateRecommendationVisual(outfit)).status, 'SUCCEEDED')
    assert.deepEqual(methods, ['POST', 'GET'])
  } finally {
    globalThis.setTimeout = originalTimeout
    app.dispose()
  }
})

test('all concurrent callers discard an image completed after logout', async () => {
  const app = session()
  let complete
  globalThis.fetch = async url => {
    if (String(url).endsWith('/auth/csrf')) return response({ token: 'test', headerName: 'X-XSRF-TOKEN' })
    if (String(url).endsWith('/visual')) return new Promise(resolve => { complete = resolve })
    return response(null)
  }
  const first = app.generateRecommendationVisual(outfit)
  const second = app.generateRecommendationVisual(outfit)
  await new Promise(setImmediate)
  await app.logout()
  complete(response(success))
  assert.deepEqual(await Promise.all([first, second]), [null, null])
  app.dispose()
})


test('read-only restoration uses GET and custom generation submits owned ids and image data', async () => {
  const app = session()
  const calls = []
  globalThis.fetch = async (url, options) => {
    if (String(url).endsWith('/auth/csrf')) return response({ token: 'test', headerName: 'X-XSRF-TOKEN' })
    calls.push({ url: String(url), options })
    return response(success)
  }
  await app.generateRecommendationVisual(outfit, { readOnly: true })
  assert.equal(calls[0].options.method, 'GET')
  const custom = { ...outfit, items: [{ ...outfit.items[0], uploaded: true, name: 'custom', category: '上装', imageData: 'data:image/png;base64,AAAA' }, outfit.items[1]] }
  await app.generateRecommendationVisual(custom, { force: true, customItems: true })
  assert.deepEqual(JSON.parse(calls[1].options.body).items, [
    { name: 'custom', category: '上装', imageData: 'data:image/png;base64,AAAA' }, { wardrobeId: 2 }
  ])
  app.dispose()
})
