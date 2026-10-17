import assert from 'node:assert/strict'
import { test } from 'node:test'
import { useFashionApp } from '../src/composables/useFashionApp.js'
import { createTrendAnalysisExamples, isTrendAnalysisExample, trendOutfitAnalysis } from '../src/data/trendOutfitAnalyses.js'
import { trendGalleryImageUrl } from '../src/utils/trendGallery.js'

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

test('attaches curated analysis only to the exact verified source post', () => {
  assert.equal(trendOutfitAnalysis({ sourceUrl: 'https://www.sina.cn/news/detail/5347432265156146.html?from=share' }).points.length, 3)
  assert.equal(trendOutfitAnalysis({ sourceUrl: 'https://m.weibo.cn/status/5310383998634818' }).images.length, 2)
  for (const sourceUrl of [
    'https://example.com/news/detail/5347432265156146.html',
    'https://www.sina.cn/news/detail/999999999.html',
    'https://name:password@www.sina.cn/news/detail/5347432265156146.html',
    'https://weibo.com/5347432265156146',
    'invalid'
  ]) assert.equal(trendOutfitAnalysis({ sourceUrl, evidence: { author: 'SavisLook' } }), null)
})

test('preserves daily quota and per-outfit slots without leaking them into a later non-daily feed', async (t) => {
  const originalFetch = globalThis.fetch
  const item = { id: 'niche-look', platform: 'weibo', title: '哥特风穿搭',
    imageUrl: 'https://images.test/niche.jpg', topicTags: ['哥特风'], evidence: { mediaType: 'image' } }
  const slot = { id: item.id, category: 'niche', label: '小众穿搭欣赏', reason: '原帖明确标注哥特风' }
  let dailySelection = { target: 5, menswear: 0, womenswear: 0, niche: 1,
    missing: { menswear: 2, womenswear: 2, niche: 0 }, assignments: [slot] }
  globalThis.fetch = async () => new Response(JSON.stringify({ code: 0, data: { items: [item], dailySelection } }),
    { headers: { 'content-type': 'application/json' } })
  t.after(() => { globalThis.fetch = originalFetch })
  const app = useFashionApp()
  await app.loadTrends()
  assert.deepEqual(app.state.trendMeta.dailySelection, dailySelection)
  assert.deepEqual(app.state.trends[0].dailySlot, slot)
  assert.equal(item.dailySlot, undefined)
  dailySelection = null
  await app.loadTrends()
  assert.equal(app.state.trendMeta.dailySelection, null)
  assert.equal(app.state.trends[0].dailySlot, undefined)
})

test('historical analysis examples preserve dates and do not submit nonexistent feed ids to recommendations', async (t) => {
  const originalWindow = globalThis.window
  const originalDocument = globalThis.document
  globalThis.window = { location: { hash: '#trend' }, history: { pushState() {} }, scrollTo() {} }
  globalThis.document = { querySelector() { return null } }
  t.after(() => {
    if (originalWindow === undefined) delete globalThis.window
    else globalThis.window = originalWindow
    if (originalDocument === undefined) delete globalThis.document
    else globalThis.document = originalDocument
  })
  const examples = createTrendAnalysisExamples('http://localhost:5173')
  assert.equal(examples.length, 2)
  assert.equal(examples[0].publishedAt, '2026-09-26T16:17:00+08:00')
  assert.equal(examples[1].publishedAt, '2026-06-16T10:41:00+08:00')
  for (const example of examples) {
    assert.equal(isTrendAnalysisExample(example), true)
    assert.match(trendGalleryImageUrl(example), /^http:\/\/localhost:5173\/assets\/trend-analysis\//)
  }
  const app = useFashionApp()
  app.state.recommendationForm.trendId = 'previous-real-trend'
  await app.useTrend(examples[0])
  assert.equal(app.state.recommendationForm.trendId, undefined)
  assert.equal(app.state.selectedTrendReference.sourceUrl, examples[0].sourceUrl)
  assert.ok(app.state.recommendationForm.styleHint.includes('白色上衣与白鞋呼应'))
  assert.ok(app.state.recommendationForm.styleHint.length <= 120)
})
