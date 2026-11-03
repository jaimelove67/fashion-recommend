import assert from 'node:assert/strict'
import test from 'node:test'
import { useFashionApp } from '../src/composables/useFashionApp.js'
import { isWardrobeGenerationRequest } from '../src/utils/assistantIntent.js'
globalThis.window = { removeEventListener() {} }

const response = data => new Response(JSON.stringify({ code: 0, data }), { headers: { 'Content-Type': 'application/json' } })
const page = (id, total = 1) => ({ content: [{ id }], totalElements: 1, page: 0, hasNext: false,
  statistics: { total, saved: 7, rated: 3, averageRating: 4, coveredItems: 8 } })

test('only explicit wardrobe generation requests leave consultation', () => {
  for (const question of ['你好，你能做什么？', '蓝色衬衫怎么配色？', '长沙通勤，希望简洁得体',
    '如何用我的衣橱生成搭配？', '不要用我的衣橱生成搭配，只讲配色', '我的衣橱有蓝衬衫，应该怎么搭？',
    '用我的衣橱搭配时，蓝色和绿色合适吗？', '参考我的衣橱，推荐几种适合我的配色？',
    '用我的衣橱选衣服时有哪些技巧？', '请根据我的衣橱推荐搭配']) {
    assert.equal(isWardrobeGenerationRequest(question), false, question)
  }
  for (const request of ['请用我的衣橱生成一套日常通勤搭配', '请使用衣橱单品搭配一套，保持配色协调',
    '从我上传的衣服中选一套', '请根据我的衣橱推荐一套搭配', '用我的衣橱生成搭配']) {
    assert.equal(isWardrobeGenerationRequest(request), true, request)
  }
})

test('consultation works without weather or garments and sends recent conversation without generating', async () => {
  const app = useFashionApp()
  app.state.authPhase = 'authenticated'
  app.state.weatherLoading = true
  const paths = []
  let body
  globalThis.fetch = async (url, options) => {
    paths.push(String(url))
    if (String(url).endsWith('/auth/csrf')) return response({ token: 'test', headerName: 'X-XSRF-TOKEN' })
    body = JSON.parse(options.body)
    assert.equal(options.headers.get('X-XSRF-TOKEN'), 'test')
    return response({ answer: '蓝配米白。', examples: [], visuals: [] })
  }
  const result = await app.consultAssistant('蓝色怎么配？', Array.from({ length: 12 }, (_, i) => ({ role: 'user', text: String(i) })))
  assert.equal(result.answer, '蓝配米白。')
  assert.equal(body.history.length, 10)
  assert.equal(body.history[0].text, '2')
  assert.equal(paths.at(-1), '/api/v1/assistant/consult')
  assert.equal(app.state.currentRecommendation, null)
  assert.equal(app.state.weather, null)
  assert.equal(app.state.consulting, false)
  app.dispose()
})

test('consultation ignores a late response after logout', async () => {
  const app = useFashionApp()
  app.state.authPhase = 'authenticated'
  let finish
  globalThis.fetch = async url => {
    if (String(url).endsWith('/auth/csrf')) return response({ token: 'test', headerName: 'X-XSRF-TOKEN' })
    if (String(url).endsWith('/assistant/consult')) return new Promise(resolve => { finish = resolve })
    return response(null)
  }
  const pending = app.consultAssistant('蓝色怎么配？')
  await new Promise(setImmediate)
  await app.logout()
  finish(response({ answer: '旧账号的回答', examples: [], visuals: [] }))
  assert.equal(await pending, null)
  assert.equal(app.state.consulting, false)
  app.dispose()
})

test('location coordinates reach recommendations but never override an explicitly selected city', async () => {
  const app = useFashionApp()
  app.state.authPhase = 'authenticated'
  const bodies = []
  globalThis.fetch = async (url, options) => {
    if (String(url).includes('/weather/')) return response({ city: '当前位置', temperatureC: 24, source: 'open-meteo' })
    if (String(url).endsWith('/auth/csrf')) return response({ token: 'test', headerName: 'X-XSRF-TOKEN' })
    if (String(url).includes('/me/recommendations')) return response(page(9))
    bodies.push(JSON.parse(options.body))
    return response({ id: 9, weather: { city: bodies.at(-1).city, temperatureC: 24, source: 'open-meteo' } })
  }
  await app.loadLocalWeather({ coordinates: { latitude: 28.2, longitude: 112.9 } })
  assert.equal(app.state.recommendationForm.city, '当前位置')
  await app.generateRecommendation()
  assert.equal(bodies[0].latitude, 28.2)
  assert.equal(bodies[0].longitude, 112.9)
  await app.generateRecommendation({ city: '北京' })
  assert.equal(bodies[1].city, '北京')
  assert.equal(bodies[1].latitude, undefined)
  assert.equal(bodies[1].longitude, undefined)
  app.dispose()
})

test('server history filtering uses all-account statistics and ignores an older search response', async () => {
  const app = useFashionApp()
  app.state.authPhase = 'authenticated'
  let finishOld
  const paths = []
  globalThis.fetch = async url => {
    paths.push(String(url))
    if (String(url).includes('query=old')) return new Promise(resolve => { finishOld = resolve })
    return response(page(2, 45))
  }
  const old = app.loadHistory({ query: 'old' })
  await new Promise(setImmediate)
  await app.loadHistory({ query: 'new', filter: 'saved' })
  finishOld(response(page(1)))
  await old
  assert.equal(app.state.history[0].id, 2)
  assert.equal(app.recommendationStats.value.total, 45)
  assert.equal(app.recommendationStats.value.saved, 7)
  assert.equal(app.recommendationStats.value.averageRating, 4)
  assert.ok(paths.some(path => path.includes('filter=saved') && path.includes('query=new')))
  app.dispose()
})

test('manual temperature and item constraints are sent without replacing the live weather', async () => {
  const app = useFashionApp()
  app.state.authPhase = 'authenticated'
  app.state.wardrobe = [{ id: 1 }, { id: 2 }]
  app.state.weather = { city: '长沙', temperatureC: 30, source: 'open-meteo' }
  let body
  globalThis.fetch = async (url, options) => {
    if (String(url).endsWith('/auth/csrf')) return response({ token: 'test', headerName: 'X-XSRF-TOKEN' })
    if (String(url).includes('/me/recommendations')) return response(page(9))
    body = JSON.parse(options.body)
    return response({ id: 9, weather: { source: 'user-provided', temperatureC: 22 } })
  }
  await app.generateRecommendation({ manualTemperatureC: 22, lockedItemIds: [1], excludedItemIds: [2] })
  assert.equal(body.manualTemperatureC, 22)
  assert.deepEqual(body.lockedItemIds, [1])
  assert.deepEqual(body.excludedItemIds, [2])
  assert.equal(app.state.weather.temperatureC, 30)
  assert.equal(app.state.currentRecommendation.weather.source, 'user-provided')
  app.dispose()
})
