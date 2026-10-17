import assert from 'node:assert/strict'
import { test } from 'node:test'
import { useFashionApp } from '../src/composables/useFashionApp.js'
import { trendGalleryImageUrl, verifiedFullBodyImageUrl } from '../src/utils/trendGallery.js'

test('keeps publisher images and legacy unreviewed covers out of the gallery', async (t) => {
  const originalFetch = globalThis.fetch
  const items = [
    { id: 'magazine', platform: 'editorial', title: '杂志封面穿搭', imageUrl: 'https://images.test/magazine.jpg', evidence: { fullBodyImageUrl: 'https://images.test/magazine.jpg' } },
    { id: 'unreviewed', platform: 'weibo', title: '日常穿搭', imageUrl: 'https://images.test/closeup.jpg', evidence: { images: ['https://images.test/closeup.jpg'] } },
    { id: 'reviewed', platform: 'douyin', title: '秋季穿搭', imageUrl: 'https://images.test/cover.jpg', evidence: { images: ['https://images.test/cover.jpg', 'https://images.test/full-body.jpg'], fullBodyImageUrl: 'https://images.test/full-body.jpg' } }
  ]
  globalThis.fetch = async (input) => {
    assert.equal(new URL(String(input), 'http://localhost').pathname, '/api/v1/trends')
    return new Response(JSON.stringify({ code: 0, message: 'ok', data: { items, styles: [], sources: [] } }), {
      status: 200,
      headers: { 'content-type': 'application/json' }
    })
  }
  t.after(() => { globalThis.fetch = originalFetch })

  const app = useFashionApp()
  await app.loadTrends()

  assert.deepEqual(app.state.galleryTrends.map((item) => item.id), ['reviewed'])
  assert.equal(app.state.galleryTrends[0].evidence.fullBodyImageUrl, 'https://images.test/full-body.jpg')
})

test('allows only same-origin configured demo assets into the gallery', () => {
  assert.equal(verifiedFullBodyImageUrl({
    id: 'demo',
    platform: 'configured-demo',
    imageUrl: '/assets/look-urban.jpg'
  }), '/assets/look-urban.jpg')
  assert.equal(verifiedFullBodyImageUrl({
    id: 'demo-relative',
    platform: 'configured-demo',
    imageUrl: 'assets/look-urban.jpg'
  }), '')
  assert.equal(verifiedFullBodyImageUrl({
    id: 'demo-remote',
    platform: 'configured-demo',
    imageUrl: 'https://images.test/look-urban.jpg'
  }), '')
})

test('keeps image posts in the gallery without inventing full-body verification', async (t) => {
  const originalFetch = globalThis.fetch
  const imageUrl = 'https://images.test/daily-knit.jpg'
  const item = {
    id: 'daily-knit', platform: 'weibo', title: '日常针织穿搭', imageUrl,
    evidence: { mediaType: 'image', images: [imageUrl], fullBodyImageUrl: null }
  }
  globalThis.fetch = async () => Response.json({ code: 0, data: { items: [item], styles: [], sources: [] } })
  t.after(() => { globalThis.fetch = originalFetch })

  const app = useFashionApp()
  await app.loadTrends()

  assert.deepEqual(app.state.galleryTrends.map((trend) => trend.id), ['daily-knit'])
  assert.equal(app.state.galleryTrends[0].evidence.fullBodyImageUrl, null)
  assert.equal(verifiedFullBodyImageUrl(app.state.galleryTrends[0]), '')
  assert.equal(trendGalleryImageUrl(app.state.galleryTrends[0]), imageUrl)
  assert.equal(app.state.selectedTrendId, item.id)
})

test('uses reviewed full-body images first and limits cover fallback to image posts', () => {
  const item = {
    platform: 'weibo', title: '日常穿搭', imageUrl: 'https://images.test/cover.jpg',
    evidence: { mediaType: 'image', images: ['https://images.test/full-body.jpg'], fullBodyImageUrl: 'https://images.test/full-body.jpg' }
  }
  assert.equal(trendGalleryImageUrl(item), item.evidence.fullBodyImageUrl)
  const unverified = { ...item, evidence: { mediaType: 'image' } }
  assert.equal(trendGalleryImageUrl(unverified), item.imageUrl)
  assert.equal(trendGalleryImageUrl({ ...unverified, evidence: { mediaType: 'photo' } }), item.imageUrl)
  for (const mediaType of ['video', 'board', 'post', undefined]) {
    assert.equal(trendGalleryImageUrl({ ...unverified, evidence: { mediaType } }), '')
  }
  assert.equal(trendGalleryImageUrl({ ...unverified, platform: 'editorial' }), '')
  assert.equal(trendGalleryImageUrl({ ...unverified, platform: 'configured-demo' }), '')
  assert.equal(trendGalleryImageUrl({ ...unverified, title: '时装周秀场穿搭' }), '')
  for (const imageUrl of ['', null, 'javascript:alert(1)', 'file:///private.jpg', 'https://user:pass@images.test/look.jpg']) {
    assert.equal(trendGalleryImageUrl({ ...unverified, imageUrl }), '')
  }
})

test('accepts reviewed image posts from other platforms but never video covers or shows', () => {
  const item = { platform: 'xiaohongshu', title: '通勤穿搭', imageUrl: 'https://images.test/look.jpg', evidence: { mediaType: 'image', fullBodyImageUrl: 'https://images.test/look.jpg' } }
  assert.equal(verifiedFullBodyImageUrl(item), item.imageUrl)
  assert.equal(verifiedFullBodyImageUrl({ ...item, platform: 'other-platform' }), item.imageUrl)
  assert.equal(verifiedFullBodyImageUrl({ ...item, evidence: { ...item.evidence, mediaType: 'video' } }), '')
  assert.equal(verifiedFullBodyImageUrl({ ...item, title: '时装周秀场穿搭' }), '')
})
