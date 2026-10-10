import assert from 'node:assert/strict'
import { test } from 'node:test'
import { useFashionApp } from '../src/composables/useFashionApp.js'

test('requests only the last 24 hours even when an old weekly selection remains', async (t) => {
  const originalFetch = globalThis.fetch
  const requestedPeriods = []
  const realItem = {
    id: 'real-cover',
    platform: 'weibo',
    title: '真实来源但未核对图片',
    topicTags: ['日常穿搭'],
    imageUrl: 'https://images.test/real-cover.jpg',
    evidence: { mediaType: 'post', images: ['https://images.test/real-cover.jpg'] }
  }
  const demoItem = {
    id: 'demo-look',
    platform: 'configured-demo',
    title: '演示补充图片',
    topicTags: ['通勤'],
    imageUrl: '/assets/look-urban.jpg'
  }

  globalThis.fetch = async (input) => {
    const url = new URL(String(input), 'http://localhost')
    assert.equal(url.pathname, '/api/v1/trends')
    const period = url.searchParams.get('period')
    requestedPeriods.push(period)
    const data = period === 'day'
      ? { items: [realItem], styles: [], sources: [], demoMode: false, fetchedAt: '2026-09-29T09:00:00Z' }
      : { items: [demoItem], styles: [], sources: [], demoMode: true, fetchedAt: '2026-09-29T09:00:00Z' }
    return new Response(JSON.stringify({ code: 0, message: 'ok', data }), {
      status: 200,
      headers: { 'content-type': 'application/json' }
    })
  }
  t.after(() => { globalThis.fetch = originalFetch })

  const app = useFashionApp()
  assert.equal(app.state.trendPeriod, 'day')
  app.state.trendPeriod = 'week'
  await app.loadTrends()

  assert.deepEqual(requestedPeriods, ['day'])
  assert.deepEqual(app.state.trends.map((item) => item.id), ['real-cover'])
  assert.deepEqual(app.state.galleryTrends, [])
  assert.equal(app.state.trendMeta.demoMode, false)
  assert.equal(app.state.selectedTrendId, 'real-cover')
})

test('uses a configured demo trend only as local reference and preserves real trend references', async (t) => {
  const originalWindow = globalThis.window
  const originalDocument = globalThis.document
  globalThis.window = {
    location: { hash: '#trend' },
    history: { pushState() {} },
    scrollTo() {}
  }
  globalThis.document = { querySelector() { return null } }
  t.after(() => {
    if (originalWindow === undefined) delete globalThis.window
    else globalThis.window = originalWindow
    if (originalDocument === undefined) delete globalThis.document
    else globalThis.document = originalDocument
  })

  const app = useFashionApp()
  app.state.recommendationForm.trendId = 'old-real-trend'

  await app.useTrend({
    id: 'demo-look',
    platform: 'configured-demo',
    topicTags: ['通勤', '低饱和']
  })
  assert.equal(app.state.selectedTrendReference.platform, 'configured-demo')
  assert.equal(app.state.recommendationForm.trendId, undefined)
  assert.equal(app.state.recommendationForm.styleHint, '通勤、低饱和')

  await app.useTrend({
    id: 'real-trend',
    platform: 'weibo',
    topicTags: ['日常穿搭']
  })
  assert.equal(app.state.recommendationForm.trendId, 'real-trend')
  assert.equal(app.state.recommendationForm.styleHint, '日常穿搭')
})
