import assert from 'node:assert/strict'
import test from 'node:test'
import { useFashionApp } from '../src/composables/useFashionApp.js'

globalThis.window = { removeEventListener() {}, location: { origin: 'http://localhost' } }
const response = data => new Response(JSON.stringify({ code: 0, data }), { headers: { 'Content-Type': 'application/json' } })
const csrf = { token: 'test-csrf', headerName: 'X-XSRF-TOKEN' }
function admin() {
  const app = useFashionApp()
  app.state.authPhase = 'authenticated'
  app.state.authUser = { username: 'admin', authorities: ['ROLE_ADMIN'] }
  return app
}

test('technique reads never post; actions send CSRF and report skipped instead of success', async () => {
  const app = admin(), calls = []
  globalThis.fetch = async (path, options) => {
    calls.push({ path, options })
    if (path.endsWith('/csrf')) return response(csrf)
    if (path.endsWith('/refresh')) return response({ attempted: false, outcome: 'not-due', state: { status: 'ready' } })
    if (path.endsWith('/comparison-visuals/status')) return response({ month: '2026-11', used: 4, limit: 10 })
    if (path.includes('/audit-logs')) return response({ items: [] })
    return response({ status: 'ready', enabled: true })
  }
  await app.loadAdminTechniques()
  assert.equal(calls.length, 2)
  assert.ok(calls.every(call => call.options.method === 'GET'))
  await app.refreshAdminTechniques()
  const post = calls.find(call => call.options.method === 'POST')
  assert.equal(post.options.headers.get('X-XSRF-TOKEN'), csrf.token)
  assert.match(app.state.adminTechniqueNotice, /未执行/)
  assert.equal(app.state.adminTechniqueBudget.used, 4)
  assert.equal(app.state.adminTechniqueAction, '')
  app.dispose()
})

test('ordinary users cannot request admin capability APIs', async () => {
  const app = useFashionApp()
  app.state.authPhase = 'authenticated'
  app.state.authUser = { username: 'user', authorities: ['ROLE_USER'] }
  globalThis.fetch = () => { throw new Error('must not request') }
  assert.equal(await app.loadAdminTechniques(), null)
  assert.equal(await app.refreshAdminTechniques(), null)
  assert.equal(await app.generateAdminTechniqueVisuals(), null)
  app.dispose()
})

test('late technique status cannot repopulate a signed-out session', async () => {
  const app = admin(), resolvers = []
  globalThis.fetch = async path => {
    if (path.endsWith('/csrf')) return response(csrf)
    if (path.endsWith('/logout')) return response(null)
    return new Promise(resolve => resolvers.push(resolve))
  }
  const pending = app.loadAdminTechniques()
  await new Promise(setImmediate)
  await app.logout()
  resolvers.forEach(resolve => resolve(response({ status: 'ready', used: 5 })))
  await pending
  assert.equal(app.state.adminTechniqueStatus, null)
  assert.equal(app.state.adminTechniqueBudget, null)
  assert.equal(app.state.adminTechniquesLoading, false)
  app.dispose()
})

test('partial status failure clears stale quota and preserves the successful read', async () => {
  const app = admin()
  app.state.adminTechniqueBudget = { used: 1, limit: 10 }
  globalThis.fetch = async path => path.endsWith('/comparison-visuals/status')
    ? new Response(JSON.stringify({ code: 503, message: 'unavailable' }), { status: 503 })
    : response({ status: 'disabled', enabled: false })
  await app.loadAdminTechniques()
  assert.equal(app.state.adminTechniqueStatus.status, 'disabled')
  assert.equal(app.state.adminTechniqueBudget, null)
  assert.match(app.state.adminTechniqueError, /配图额度/)
  app.dispose()
})

test('trend filters reach the server and stale filtered responses do not win', async () => {
  const app = useFashionApp(), reads = []
  globalThis.fetch = (path) => new Promise(resolve => reads.push({ path, resolve }))
  app.state.trendPeriod = 'week'
  app.state.trendPlatform = 'weibo'
  app.state.trendTopic = ' 白衬衫 '
  const first = app.loadTrends()
  const url = new URL(reads[0].path, 'http://localhost')
  assert.equal(url.searchParams.get('period'), 'week')
  assert.equal(url.searchParams.get('platform'), 'weibo')
  assert.equal(url.searchParams.get('topic'), '白衬衫')
  app.state.trendTopic = '外套'
  const second = app.loadTrends()
  reads[1].resolve(response({ items: [{ id: 2 }], sources: [] }))
  await second
  reads[0].resolve(response({ items: [{ id: 1 }], sources: [] }))
  await first
  assert.equal(app.state.trends[0].id, 2)
  app.dispose()
})

test('preselected garments reach generation and invalid or conflicting choices never post', async () => {
  const app = useFashionApp(), bodies = []
  app.state.authPhase = 'authenticated'
  app.state.wardrobe = [{ id: 1 }, { id: 2 }, { id: 3, recognitionStatus: 'NEEDS_MANUAL_REVIEW' }]
  globalThis.fetch = async (path, options) => {
    if (path.endsWith('/csrf')) return response(csrf)
    if (path.includes('/me/recommendations')) return response({ content: [], totalElements: 0 })
    bodies.push(JSON.parse(options.body))
    return response({ id: 9 })
  }
  app.state.recommendationForm.lockedItemIds = [1]
  app.state.recommendationForm.excludedItemIds = [2]
  assert.equal((await app.generateRecommendation()).id, 9)
  assert.deepEqual(bodies[0].lockedItemIds, [1])
  assert.deepEqual(bodies[0].excludedItemIds, [2])
  assert.equal(await app.generateRecommendation({ excludedItemIds: [1] }), null)
  assert.match(app.state.error, /同时/)
  assert.equal(await app.generateRecommendation({ lockedItemIds: [3] }), null)
  assert.match(app.state.error, /需要确认/)
  assert.equal(await app.generateRecommendation({ lockedItemIds: [99] }), null)
  assert.equal(bodies.length, 1)
  app.dispose()
})

test('illustration favorite rejects remote or private image addresses before fetching', async () => {
  const app = admin()
  globalThis.fetch = () => { throw new Error('must not request') }
  for (const imageUrl of ['https://external.test/look.png', '/api/v1/me/style-profile/photo', '/assets/trend-pieces/../private.png']) {
    await assert.rejects(app.saveTryOnFavorite({ imageUrl, sourceKind: 'TREND_ILLUSTRATION' }), /本站提供/)
  }
  app.dispose()
})
