import assert from 'node:assert/strict'
import test from 'node:test'
import { useFashionApp } from '../src/composables/useFashionApp.js'
globalThis.window = { removeEventListener() {} }

const response = data => new Response(JSON.stringify({ code: 0, data }), { headers: { 'Content-Type': 'application/json' } })
const page = (id, total = 1) => ({ content: [{ id }], totalElements: 1, page: 0, hasNext: false,
  statistics: { total, saved: 7, rated: 3, averageRating: 4, coveredItems: 8 } })

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
