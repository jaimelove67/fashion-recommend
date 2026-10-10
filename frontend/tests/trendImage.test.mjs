import assert from 'node:assert/strict'
import test from 'node:test'
import { trendImageCandidates, trendDisplayImageUrl } from '../src/utils/trendImage.js'

test('prefers Sina large images and retains the thumbnail as a fallback', () => {
  assert.deepEqual(
    trendImageCandidates('https://wx3.sinaimg.cn/thumbnail/photo.jpg?source=feed'),
    [
      'https://wx3.sinaimg.cn/large/photo.jpg?source=feed',
      'https://wx3.sinaimg.cn/thumbnail/photo.jpg?source=feed'
    ]
  )
})

test('leaves non-Sina image URLs unchanged', () => {
  assert.deepEqual(trendImageCandidates('https://images.example.com/photo.jpg'), [
    'https://images.example.com/photo.jpg'
  ])
})

test('leaves other Sina image variants unchanged', () => {
  assert.deepEqual(trendImageCandidates('https://wx3.sinaimg.cn/large/photo.jpg'), [
    'https://wx3.sinaimg.cn/large/photo.jpg'
  ])
})

test('keeps same-origin demo asset paths as image candidates', () => {
  assert.deepEqual(trendImageCandidates('/assets/look-urban.jpg'), [
    '/assets/look-urban.jpg'
  ])
})

test('routes only fixed public Sina image paths through the app', () => {
  assert.equal(trendDisplayImageUrl('https://wx3.sinaimg.cn/cmw960/abc123.jpg'), '/trend-images/wx3/cmw960/abc123.jpg')
  for (const url of ['https://wx3.sinaimg.cn.evil.test/large/abc.jpg', 'https://localhost/large/abc.jpg',
    'http://wx3.sinaimg.cn/large/abc.jpg', 'https://user:pass@wx3.sinaimg.cn/large/abc.jpg',
    'https://wx3.sinaimg.cn/large/abc.jpg?target=http://localhost', '/assets/trend-pieces/knit.png']) {
    assert.equal(trendDisplayImageUrl(url), url)
  }
})
