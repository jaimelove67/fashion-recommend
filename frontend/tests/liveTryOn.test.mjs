import assert from 'node:assert/strict'
import test from 'node:test'
import { ANYWEAR_ORIGIN, anywearFrameUrl, canTryOnGarment, isAnywearMessage, prepareTryOnImage, trendTryOnItem } from '../src/utils/liveTryOn.js'
import { imageAddress, imageFile, readImage, sitePermission } from '../src/utils/tryOnImages.js'
import { favoriteUploadForm, favoriteTryOnItem } from '../src/utils/tryOnFavorites.js'

test('try-on accepts confirmed photographed garments and excludes shoes, accessories and review items', () => {
  const item = { id: 1, category: '上装', imageUrl: '/api/v1/me/wardrobe/1/image', recognitionStatus: 'MANUAL' }
  assert.equal(canTryOnGarment(item), true)
  for (const override of [{ imageUrl: '' }, { category: '鞋履' }, { category: '配饰' }, { recognitionStatus: 'NEEDS_MANUAL_REVIEW' }]) {
    assert.equal(canTryOnGarment({ ...item, ...override }), false)
  }
})

test('provider navigation is fixed and does not place private input in the iframe URL', () => {
  const url = new URL(anywearFrameUrl('localhost'))
  assert.equal(url.origin, ANYWEAR_ORIGIN)
  assert.equal(url.pathname, '/widget/latest/widget.html')
  assert.equal(url.searchParams.get('norestore'), '1')
  assert.equal(url.searchParams.get('hostsite'), 'localhost')
  assert.equal(url.searchParams.has('productImageUrl'), false)
})

test('provider messages require both the expected origin and the current iframe window', () => {
  const frame = {}
  const event = { origin: ANYWEAR_ORIGIN, source: frame, data: { type: 'DECART_TRACK_EVENT' } }
  assert.equal(isAnywearMessage(event, frame), true)
  assert.equal(isAnywearMessage({ ...event, origin: 'https://example.com' }, frame), false)
  assert.equal(isAnywearMessage({ ...event, source: {} }, frame), false)
  assert.equal(isAnywearMessage({ ...event, data: null }, frame), false)
  assert.equal(isAnywearMessage(event, null), false)
})

test('external HTTPS images are passed without fetching or attaching this app session', async t => {
  t.mock.method(globalThis, 'fetch', () => { throw new Error('Unexpected credentialed fetch') })
  const image = await prepareTryOnImage('https://images.example.com/shirt.png', { origin: 'http://localhost:5173' })
  assert.equal(image, 'https://images.example.com/shirt.png')
})

test('rejects executable, insecure external and embedded-credential image addresses', async () => {
  for (const image of ['javascript:alert(1)', 'data:text/html,test', 'http://external.example.com/image.png', 'https://name:password@example.com/image.png']) {
    await assert.rejects(prepareTryOnImage(image, { origin: 'http://localhost:5173' }))
  }
})

test('private image reads require a successful image response instead of sending a login or API document', async t => {
  const calls = []
  t.mock.method(globalThis, 'fetch', async (...args) => {
    calls.push(args)
    return new Response('<html>Login</html>', { headers: { 'content-type': 'text/html' } })
  })
  await assert.rejects(prepareTryOnImage('/api/v1/me/wardrobe/1/image', { origin: 'http://localhost:5173' }), /JPG/)
  assert.equal(calls[0][1].credentials, 'same-origin')
  assert.equal(calls[0][1].redirect, 'error')
})

test('empty private images and expired sessions fail before FileReader conversion', async t => {
  t.mock.method(globalThis, 'fetch', async () => new Response('', { headers: { 'content-type': 'image/png' } }))
  await assert.rejects(prepareTryOnImage('/image', { origin: 'http://localhost:5173' }), /0 字节/)
  globalThis.fetch = async () => new Response(null, { status: 401 })
  await assert.rejects(prepareTryOnImage('/image', { origin: 'http://localhost:5173' }), /登录状态/)
})

test('trend images retain their illustration label and source, with pants mapped to the supported category', () => {
  const item = trendTryOnItem({ id: 'jeans', name: '原牛', category: '裤装', image: '/assets/jeans.png', mentions: [{}, { sourceUrl: 'https://example.com/post' }] })
  assert.equal(item.category, '下装')
  assert.equal(canTryOnGarment(item), true)
  assert.equal(item.sourceKind, 'TREND_ILLUSTRATION')
  assert.equal(item.sourceUrl, 'https://example.com/post')
  assert.match(favoriteTryOnItem(item).imageNote, /非原帖商品图/)
  assert.equal(canTryOnGarment(trendTryOnItem({ id: 'empty', category: '上装' })), false)
})

test('extension permissions name only the exact selected image host', () => {
  assert.equal(sitePermission('https://cdn.shop.example.com/image.png?size=large'), 'https://cdn.shop.example.com/*')
  assert.equal(sitePermission('http://127.0.0.1:5173/image.png'), 'http://127.0.0.1/*')
  for (const address of ['http://shop.example.com/image', 'https://user:secret@shop.example.com/image', 'file:///image.png', 'javascript:alert(1)']) {
    assert.throws(() => imageAddress(address))
  }
})

test('favorite form contains original image bytes and the expected account without session credentials', async () => {
  const data = 'data:image/png;base64,aW1hZ2U='
  const form = favoriteUploadForm({ name: '购物衬衫', category: '上装', sourceKind: 'SHOPPING', sourceUrl: 'https://shop.example.com/product' }, data, 'alice')
  assert.equal(await form.get('image').text(), 'image')
  assert.equal(form.get('image').type, 'image/png')
  assert.equal(form.get('sourceKind'), 'SHOPPING')
  assert.equal(form.get('expectedUser'), 'alice')
  assert.equal(form.get('sourceUrl'), 'https://shop.example.com/product')
  for (const image of ['data:text/html;base64,aW1hZ2U=', 'https://example.com/image.png', 'data:image/png;base64,', 'data:image/png;base64,!!!!']) {
    assert.throws(() => imageFile(image))
  }
})

test('shopping image fetch omits credentials and refuses redirects or non-image responses', async t => {
  const calls = []
  t.mock.method(globalThis, 'fetch', async (...args) => { calls.push(args); return new Response('image', { headers: { 'content-type': 'image/png' } }) })
  assert.equal(await readImage('https://shop.example.com/image.png'), 'data:image/png;base64,aW1hZ2U=')
  assert.equal(calls[0][1].credentials, 'omit')
  assert.equal(calls[0][1].redirect, 'error')
  globalThis.fetch = async () => new Response('login', { headers: { 'content-type': 'text/html' } })
  await assert.rejects(readImage('https://shop.example.com/image.png'), /JPG/)
  globalThis.fetch = async () => new Response('', { headers: { 'content-type': 'image/png' } })
  await assert.rejects(readImage('https://shop.example.com/image.png'), /为空/)
})

test('shopping image streams stop at the size limit even when content-length is absent', async t => {
  let canceled = false
  const stream = new ReadableStream({
    start(controller) { controller.enqueue(new Uint8Array(10 * 1024 * 1024 + 1)) },
    cancel() { canceled = true }
  })
  t.mock.method(globalThis, 'fetch', async () => new Response(stream, { headers: { 'content-type': 'image/png' } }))
  await assert.rejects(readImage('https://shop.example.com/image.png'), /10 MB/)
  assert.equal(canceled, true)
})
