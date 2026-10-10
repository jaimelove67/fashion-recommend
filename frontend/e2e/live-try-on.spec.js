import { expect, test } from '@playwright/test'

const PROVIDER = 'https://anywear.decart.ai'
const PHOTO = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a0S8AAAAASUVORK5CYII=', 'base64')
const trends = [{ id: 'weibo:try-on', platform: 'weibo', title: '黑色原牛搭白衬衫，乐福鞋与光腿神器',
  summary: '试穿入口测试', sourceUrl: 'https://example.com/trend-look', stale: false,
  publishedAt: '2026-10-10T09:00:00+08:00', evidence: { mediaType: 'post', images: [] } }]

async function setup(page, { view = 'trend', mode = 'normal', failImage = false, delayImage = false } = {}) {
  const providerRequests = []
  const imageReads = []
  const errors = [], favoriteWrites = []
  let delayedRoute = null
  page.on('pageerror', error => errors.push(error.message))
  await page.addInitScript(() => localStorage.setItem('fashion.weather.city', '长沙'))
  await page.context().addCookies([{ name: 'JSESSIONID', value: 'private-test-session', url: test.info().project.use.baseURL, httpOnly: true }])
  await page.route(PROVIDER + '/**', async route => {
    providerRequests.push(route.request().url())
    // Exercises the real postMessage contract. This fixture never opens a camera,
    // contacts inference servers or claims to produce a virtual try-on video.
    await route.fulfill({ contentType: 'text/html', body: `<!doctype html><html><body>
      <p>Anywear protocol fixture · no generated video</p><script>
      window.messages = [];
      window.addEventListener('message', event => {
        window.messages.push(event.data);
        if (event.data.type === 'DECART_AUTO_START' && '${mode}' !== 'silent') {
          parent.postMessage({ type: 'DECART_TRACK_EVENT', event: '${mode === 'queue' ? 'queue_entered' : 'first_frame_rendered'}', properties: { tryon_id: event.data.tryonId } }, event.origin);
        }
      });
      </script></body></html>` })
  })
  await page.route('**/assets/trend-pieces/**', async route => {
    const request = route.request()
    if (request.resourceType() === 'fetch') {
      imageReads.push({ path: new URL(request.url()).pathname, cookie: request.headers().cookie })
      if (delayImage) { delayedRoute = route; return }
      if (failImage) return route.fulfill({ status: 404, body: 'Image unavailable' })
    }
    return route.fulfill({ contentType: 'image/png', body: PHOTO })
  })
  await page.route('**/api/**', async route => {
    const request = route.request()
    const path = new URL(request.url()).pathname
    if (path.endsWith('/image')) {
      if (request.resourceType() === 'fetch') {
        imageReads.push({ path, cookie: request.headers().cookie })
        if (delayImage) { delayedRoute = route; return }
        if (failImage) return route.fulfill({ status: 401, body: 'Session expired' })
      }
      return route.fulfill({ contentType: 'image/png', body: PHOTO })
    }
    let data = null
    if (path.endsWith('/auth/me')) data = { username: 'try-on-test', authorities: ['ROLE_USER'] }
    else if (path.endsWith('/auth/csrf')) data = { token: 'test', headerName: 'X-XSRF-TOKEN' }
    else if (path.endsWith('/wardrobe')) data = []
    else if (path.endsWith('/style-profile')) data = { gender: null, styleTags: [], analysis: null }
    else if (path.endsWith('/trends')) data = { items: trends, sources: [], styles: [] }
    else if (path.endsWith('/try-on-favorites') && request.method() === 'POST') {
      favoriteWrites.push({ body: request.postDataBuffer().toString(), headers: request.headers() })
      data = { id: 1, name: '白衬衫', category: '上装', imageUrl: '/api/v1/me/try-on-favorites/1/image',
        sourceKind: 'TREND_ILLUSTRATION', sourceUrl: 'https://example.com/trend-look' }
    }
    else if (path.endsWith('/try-on-favorites')) data = []
    else if (path.includes('/weather/')) data = { city: '长沙', temperatureC: 25, apparentTemperatureC: 25, weatherCode: 0, source: 'open-meteo' }
    else if (path.endsWith('/recommendations')) data = { content: [], page: 0, totalElements: 0, hasNext: false, statistics: { total: 0 } }
    await route.fulfill({ contentType: 'application/json', body: JSON.stringify({ code: 0, data }) })
  })
  await page.goto('/#' + view)
  await expect(page.locator('.app-shell')).toBeVisible()
  return { providerRequests, imageReads, errors, favoriteWrites, releaseImage: async () => delayedRoute?.fulfill({ contentType: 'image/png', body: PHOTO }).catch(() => {}) }
}

async function openDialog(page) {
  const explorer = page.getByRole('region', { name: '趋势单品解析' })
  await explorer.getByRole('button', { name: /白衬衫.*查看搭配/ }).click()
  await explorer.getByRole('button', { name: '实时试穿', exact: true }).click()
  await expect(page.getByRole('dialog', { name: '实时试衣', exact: true })).toBeVisible()
}

test('trend entry requires consent, sends image bytes and removes the frame when closed', async ({ page }) => {
  const api = await setup(page)
  await openDialog(page)
  expect(api.providerRequests).toEqual([])
  expect(api.imageReads).toEqual([])
  await expect(page.getByRole('combobox', { name: '试穿单品' }).locator('option')).toHaveCount(2)
  await expect(page.getByRole('dialog')).toContainText('非原帖商品图')
  await page.getByRole('button', { name: '同意并开始试衣', exact: true }).click()
  await expect(page.getByRole('status').filter({ hasText: '实时试穿中' })).toBeVisible()
  const iframe = page.frameLocator('iframe[title="Anywear 实时试衣窗口"]')
  const message = await iframe.locator('body').evaluate(() => window.messages.find(item => item.type === 'DECART_AUTO_START'))
  expect(message.productImageUrl).toBe('data:image/png;base64,' + PHOTO.toString('base64'))
  expect(JSON.stringify(message)).not.toContain('private-test-session')
  expect(JSON.stringify(message)).not.toContain('try-on-test')
  expect(JSON.stringify(message)).not.toContain('/api/v1/me/')
  expect(api.imageReads[0].cookie).toContain('JSESSIONID=private-test-session')
  await expect(page.locator('iframe')).toHaveAttribute('sandbox', 'allow-scripts allow-same-origin')
  await page.getByRole('button', { name: '关闭实时试衣', exact: true }).click()
  await expect(page.locator('iframe')).toHaveCount(0)
  await openDialog(page)
  await expect(page.getByRole('button', { name: '同意并开始试衣', exact: true })).toBeVisible()
  await page.getByRole('button', { name: '关闭实时试衣', exact: true }).click()
  expect(api.errors).toEqual([])
})

test('trend entry only includes current supported items and selection changes stop the previous frame', async ({ page }) => {
  const api = await setup(page)
  await openDialog(page)
  await page.getByRole('button', { name: '同意并开始试衣', exact: true }).click()
  await expect(page.getByRole('status').filter({ hasText: '实时试穿中' })).toBeVisible()
  await page.getByRole('combobox', { name: '试穿单品' }).selectOption('black-raw-denim')
  await expect(page.locator('iframe')).toHaveCount(0)
  await page.getByRole('button', { name: '开始试穿此衣物', exact: true }).click()
  await expect(page.getByRole('status').filter({ hasText: '实时试穿中' })).toBeVisible()
  expect(api.imageReads.map(item => item.path)).toEqual(['/assets/trend-pieces/white-shirt.png', '/assets/trend-pieces/black-raw-denim.png'])
  expect(api.errors).toEqual([])
})

test('failed image reads prevent loading the provider and allow retry', async ({ page }) => {
  const api = await setup(page, { failImage: true })
  await openDialog(page)
  await page.getByRole('button', { name: '同意并开始试衣', exact: true }).click()
  await expect(page.getByRole('alert').filter({ hasText: '无法读取衣物图片' })).toBeVisible()
  expect(api.providerRequests).toEqual([])
  await expect(page.getByRole('button', { name: '开始试穿此衣物', exact: true })).toBeEnabled()
})

test('a rendered trend try-on can be liked with an explicit illustration source and account check', async ({ page }) => {
  const api = await setup(page)
  await openDialog(page)
  const like = page.getByRole('button', { name: '喜欢，保存到项目', exact: true })
  await expect(like).toBeDisabled()
  await page.getByRole('button', { name: '同意并开始试衣', exact: true }).click()
  await expect(like).toBeEnabled()
  await like.click()
  await expect(page.getByRole('button', { name: '已保存到喜欢穿搭' })).toBeDisabled()
  expect(api.favoriteWrites).toHaveLength(1)
  expect(api.favoriteWrites[0].body).toContain('TREND_ILLUSTRATION')
  expect(api.favoriteWrites[0].body).toContain('https://example.com/trend-look')
  expect(api.favoriteWrites[0].body).toContain('try-on-test')
  expect(api.favoriteWrites[0].headers['x-xsrf-token']).toBe('test')
})

test('a provider session end unmounts its frame and keeps the tried item available for liking', async ({ page }) => {
  const api = await setup(page)
  await openDialog(page)
  await page.getByRole('button', { name: '同意并开始试衣', exact: true }).click()
  await expect(page.getByRole('status').filter({ hasText: '实时试穿中' })).toBeVisible()
  const provider = page.frames().find(frame => frame.url().startsWith(PROVIDER))
  await provider.evaluate(() => {
    const start = window.messages.find(message => message.type === 'DECART_AUTO_START')
    parent.postMessage({ type: 'DECART_TRACK_EVENT', event: 'session_ended', properties: { tryon_id: start.tryonId } }, '*')
  })
  await expect(page.locator('iframe')).toHaveCount(0)
  await expect(page.getByRole('status').filter({ hasText: '本次试穿已结束' })).toBeVisible()
  await expect(page.getByRole('button', { name: '开始试穿此衣物', exact: true })).toBeEnabled()
  await page.getByRole('button', { name: '喜欢，保存到项目', exact: true }).click()
  await expect(page.getByRole('button', { name: '已保存到喜欢穿搭' })).toBeDisabled()
  expect(api.favoriteWrites).toHaveLength(1)
})

test('a cancelled image read cannot open a frame after the dialog closes', async ({ page }) => {
  const api = await setup(page, { delayImage: true })
  await openDialog(page)
  await page.getByRole('button', { name: '同意并开始试衣', exact: true }).click()
  await expect.poll(() => api.imageReads.length).toBe(1)
  await page.getByRole('button', { name: '关闭实时试衣', exact: true }).click()
  await api.releaseImage()
  await expect(page.locator('dialog')).toHaveCount(0)
  await expect(page.locator('iframe')).toHaveCount(0)
  expect(api.providerRequests).toEqual([])
})

test('a nonresponsive provider times out without leaving the camera frame mounted', async ({ page }) => {
  await page.clock.install()
  await setup(page, { mode: 'silent' })
  await openDialog(page)
  await page.getByRole('button', { name: '同意并开始试衣', exact: true }).click()
  await expect(page.getByRole('status').filter({ hasText: '正在连接' })).toBeVisible()
  await page.clock.fastForward(46000)
  await expect(page.getByRole('alert').filter({ hasText: '试衣服务未及时响应' })).toBeVisible()
  await expect(page.locator('iframe')).toHaveCount(0)
})

test('queued sessions can close, and forged provider events are ignored', async ({ page }) => {
  await setup(page, { mode: 'queue' })
  await openDialog(page)
  await page.getByRole('button', { name: '同意并开始试衣', exact: true }).click()
  await expect(page.getByRole('status').filter({ hasText: '服务正在排队' })).toBeVisible()
  await page.evaluate(() => window.dispatchEvent(new MessageEvent('message', {
    origin: 'https://anywear.decart.ai', source: window,
    data: { type: 'DECART_TRACK_EVENT', event: 'first_frame_rendered' }
  })))
  await expect(page.getByRole('status').filter({ hasText: '服务正在排队' })).toBeVisible()
  await page.getByRole('button', { name: '关闭实时试衣', exact: true }).click()
  await expect(page.locator('iframe')).toHaveCount(0)
})

test('navigation and logout dispose the provider frame', async ({ page }) => {
  await setup(page)
  await openDialog(page)
  await page.getByRole('button', { name: '同意并开始试衣', exact: true }).click()
  await expect(page.getByRole('status').filter({ hasText: '实时试穿中' })).toBeVisible()
  await page.evaluate(() => { window.location.hash = '#home' })
  await expect(page.locator('iframe')).toHaveCount(0)
  await page.goto('/#trend')
  await openDialog(page)
  await page.getByRole('button', { name: '同意并开始试衣', exact: true }).click()
  await expect(page.getByRole('status').filter({ hasText: '实时试穿中' })).toBeVisible()
  // Simulate a logout arriving while the modal is active, such as an auth action
  // initiated before it opened. The native modal makes the background inert.
  await page.evaluate(() => document.querySelector('[aria-label="退出登录"]').click())
  await expect(page.locator('iframe')).toHaveCount(0)
  await expect(page.locator('.app-shell')).toHaveCount(0)
})

test('mobile dialog stays within the viewport and unsupported trend pieces cannot start try-on', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await setup(page)
  const explorer = page.getByRole('region', { name: '趋势单品解析' })
  await explorer.getByRole('button', { name: /乐福鞋.*查看搭配/ }).click()
  await expect(explorer.getByRole('button', { name: '实时试穿', exact: true })).toBeDisabled()
  await explorer.getByRole('button', { name: /光腿神器.*查看搭配/ }).click()
  await expect(explorer.getByRole('button', { name: '实时试穿', exact: true })).toBeDisabled()
  await openDialog(page)
  const bounds = await page.getByRole('dialog').boundingBox()
  expect(bounds.x).toBeGreaterThanOrEqual(0)
  expect(bounds.x + bounds.width).toBeLessThanOrEqual(390)
  await page.screenshot({ path: '../output/anywear-integration/mobile-consent.png', fullPage: true })
})
