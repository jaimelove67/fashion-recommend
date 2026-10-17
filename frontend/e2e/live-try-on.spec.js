import { expect, test } from '@playwright/test'
import { createServer, request as proxyRequest } from 'node:http'

const PROVIDER = 'https://anywear.decart.ai'
const PHOTO = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a0S8AAAAASUVORK5CYII=', 'base64')
const favorite = { id: 1, name: '白衬衫', category: '上装', imageUrl: '/api/v1/me/try-on-favorites/1/image',
  sourceKind: 'TREND_ILLUSTRATION', sourceUrl: 'https://example.com/trend-look' }
const favoriteSelection = [favorite,
  { ...favorite, id: 2, name: '直筒牛仔裤', category: '下装', imageUrl: '/api/v1/me/try-on-favorites/2/image' },
  { ...favorite, id: 3, name: '乐福鞋', category: '鞋履', imageUrl: '/api/v1/me/try-on-favorites/3/image' }]

async function setup(page, { view = 'favorites', initialFavorites = favoriteSelection, baseURL = test.info().project.use.baseURL } = {}) {
  const providerRequests = [], imageReads = [], errors = [], favoriteWrites = [], favoriteReads = []
  let authenticated = true
  page.on('pageerror', error => errors.push(error.message))
  await page.addInitScript(() => localStorage.setItem('fashion.weather.city', '长沙'))
  await page.context().addCookies([{ name: 'JSESSIONID', value: 'private-test-session', url: baseURL, httpOnly: true }])
  await page.route(PROVIDER + '/**', async route => {
    providerRequests.push(route.request().url())
    // Keep the service's embedding restriction. This fixture never opens a camera
    // or calls inference; the project must guide users to the extension instead.
    await route.fulfill({ contentType: 'text/html', headers: { 'Content-Security-Policy': "frame-ancestors 'self'" },
      body: '<!doctype html><p>Inert service fixture; no camera or inference</p>' })
  })
  await page.route('**/api/**', async route => {
    const request = route.request(), path = new URL(request.url()).pathname
    if (path.endsWith('/image')) {
      imageReads.push({ path, cookie: request.headers().cookie })
      return route.fulfill({ contentType: 'image/png', body: PHOTO })
    }
    let data = null
    if (path.endsWith('/auth/me')) {
      if (!authenticated) return route.fulfill({ status: 401, contentType: 'application/json', body: JSON.stringify({ code: 401 }) })
      data = { username: 'try-on-test', authorities: ['ROLE_USER'] }
    } else if (path.endsWith('/auth/logout')) authenticated = false
    else if (path.endsWith('/auth/csrf')) data = { token: 'test', headerName: 'X-XSRF-TOKEN' }
    else if (path.endsWith('/wardrobe')) data = []
    else if (path.endsWith('/style-profile')) data = { gender: null, styleTags: [], analysis: null }
    else if (path.endsWith('/trends')) data = { items: [], sources: [], styles: [] }
    else if (path.endsWith('/try-on-favorites') && request.method() === 'POST') favoriteWrites.push(request.postData())
    else if (path.endsWith('/try-on-favorites')) { favoriteReads.push(path); data = initialFavorites }
    else if (path.includes('/weather/')) data = { city: '长沙', temperatureC: 25, apparentTemperatureC: 25, weatherCode: 0, source: 'open-meteo' }
    else if (path.endsWith('/recommendations')) data = { content: [], page: 0, totalElements: 0, hasNext: false, statistics: { total: 0 } }
    await route.fulfill({ contentType: 'application/json', body: JSON.stringify({ code: 0, data }) })
  })
  await page.goto(new URL('/#' + view, baseURL).href)
  await expect(page.locator('.app-shell')).toBeVisible()
  return { providerRequests, imageReads, errors, favoriteWrites, favoriteReads }
}

async function openDialog(page) {
  const card = page.locator('.favorite-card').filter({ has: page.getByRole('heading', { name: favorite.name, exact: true }) })
  await card.getByRole('button', { name: '再次试穿', exact: true }).click()
  await expect(page.getByRole('dialog', { name: '实时试衣', exact: true })).toBeVisible()
}

test('blocked inline try-on offers extension setup without loading the provider', async ({ page }) => {
  const api = await setup(page)
  await openDialog(page)
  await expect(page.getByText('请使用配套扩展试穿', { exact: true })).toBeVisible()
  await expect(page.getByRole('dialog')).toContainText('桌面版 Chrome / Edge')
  await expect(page.getByRole('dialog')).toContainText('非原帖商品图')
  await expect(page.locator('iframe')).toHaveCount(0)
  await expect(page.getByRole('button', { name: '同意并开始试衣', exact: true })).toHaveCount(0)
  await expect(page.getByRole('link', { name: '下载所选单品图片', exact: true })).toHaveAttribute('download', '')
  await page.getByRole('button', { name: '查看扩展安装引导', exact: true }).click()
  await expect(page.getByRole('dialog', { name: '实时试衣', exact: true })).toHaveCount(0)
  await expect(page.getByRole('dialog', { name: '几步准备，开启试穿浮窗' })).toBeVisible()
  expect(api.providerRequests).toEqual([])
  expect(api.favoriteWrites).toEqual([])
  expect(api.errors).toEqual([])
})

test('selection previews only supported items and updates the selected image download', async ({ page }) => {
  const api = await setup(page)
  await openDialog(page)
  const select = page.getByRole('combobox', { name: '试穿单品' })
  await expect(select.locator('option')).toHaveCount(2)
  await expect(select.locator('option')).toHaveText(['白衬衫 · 上装', '直筒牛仔裤 · 下装'])
  const download = page.getByRole('link', { name: '下载所选单品图片', exact: true })
  const original = await download.getAttribute('href')
  await select.selectOption({ index: 1 })
  await expect(download).not.toHaveAttribute('href', original)
  await expect(page.locator('.try-on-garment')).toHaveAttribute('src', await download.getAttribute('href'))
  await page.getByRole('button', { name: '关闭实时试衣', exact: true }).click()
  await openDialog(page)
  await expect(download).toHaveAttribute('href', original)
  expect(api.providerRequests).toEqual([])
})

test('unavailable preview images cannot be downloaded and changing the selection allows recovery', async ({ page }) => {
  const api = await setup(page)
  await openDialog(page)
  await page.locator('.try-on-garment').dispatchEvent('error')
  await expect(page.getByRole('alert')).toContainText('这张单品图片暂时无法读取')
  await expect(page.getByRole('link', { name: '下载所选单品图片', exact: true })).toHaveCount(0)
  await page.getByRole('combobox', { name: '试穿单品' }).selectOption({ index: 1 })
  await expect(page.getByRole('link', { name: '下载所选单品图片', exact: true })).toBeVisible()
  expect(api.providerRequests).toEqual([])
})

test('a favorite image downloads through its own account without sending it to the provider', async ({ page }) => {
  const imageCookies = [], appURL = test.info().project.use.baseURL
  // Chrome's native download can reissue the URL outside page.route. Serve the
  // private image over real same-origin HTTP so success includes its bytes and cookie.
  const server = createServer((request, response) => {
    if (request.url === favorite.imageUrl) {
      imageCookies.push(request.headers.cookie || '')
      const authorized = imageCookies.at(-1).includes('JSESSIONID=private-test-session')
      response.writeHead(authorized ? 200 : 401, {
        'Content-Type': 'image/png', 'Cache-Control': 'private, no-store'
      })
      response.end(authorized ? PHOTO : '')
      return
    }
    const upstream = proxyRequest(new URL(request.url, appURL), {
      method: request.method, headers: { ...request.headers, host: new URL(appURL).host }
    }, forwarded => { response.writeHead(forwarded.statusCode, forwarded.headers); forwarded.pipe(response) })
    upstream.on('error', () => { response.writeHead(502); response.end() })
    request.pipe(upstream)
  })
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve))
  try {
    const baseURL = `http://127.0.0.1:${server.address().port}`
    const api = await setup(page, { view: 'favorites', initialFavorites: [favorite], baseURL })
    await page.getByRole('button', { name: '再次试穿', exact: true }).click()
    const downloading = page.waitForEvent('download')
    await page.getByRole('link', { name: '下载所选单品图片', exact: true }).click()
    const download = await downloading
    expect(await download.failure()).toBeNull()
    expect(imageCookies.at(-1)).toContain('JSESSIONID=private-test-session')
    expect(api.providerRequests).toEqual([])
    expect(api.favoriteWrites).toEqual([])
  } finally {
    server.closeAllConnections()
    await new Promise(resolve => server.close(resolve))
  }
})

test('liked items retain history search and refresh and can open setup from a cached appreciation view', async ({ page }) => {
  const api = await setup(page, { view: 'trend', initialFavorites: [favorite] })
  const navigation = page.locator('.app-pill-nav')
  await navigation.getByRole('button', { name: '搭配记录', exact: true }).click()
  const sections = page.getByRole('group', { name: '切换穿搭历史内容' })
  await sections.getByRole('button', { name: '喜欢的穿搭', exact: true }).click()
  await expect(page.getByRole('heading', { name: '搭配记录', level: 1 })).toBeVisible()
  await expect(navigation.getByRole('button', { name: '搭配记录', exact: true })).toHaveAttribute('aria-current', 'page')
  await expect(page.locator('.stats-band')).toBeHidden()
  const card = page.locator('.favorite-card')
  await expect(card.getByRole('link', { name: '查看穿搭来源' })).toHaveAttribute('href', favorite.sourceUrl)
  const search = page.getByRole('searchbox', { name: '搜索喜欢的单品' })
  await search.fill('白衬衫')
  await sections.getByRole('button', { name: '穿搭历史', exact: true }).click()
  await sections.getByRole('button', { name: '喜欢的穿搭', exact: true }).click()
  await expect(search).toHaveValue('白衬衫')
  await search.fill('不存在的单品')
  await expect(page.getByText('没有找到匹配的单品', { exact: true })).toBeVisible()
  await search.fill('')
  const reads = api.favoriteReads.length
  await page.getByRole('button', { name: '刷新收藏', exact: true }).click()
  await expect.poll(() => api.favoriteReads.length).toBe(reads + 1)
  await card.getByRole('button', { name: '再次试穿', exact: true }).click()
  await expect(page.getByRole('dialog')).toContainText('请使用配套扩展试穿')
  await page.getByRole('button', { name: '查看扩展安装引导', exact: true }).click()
  await expect(page).toHaveURL(/#trend$/)
  await expect(page.getByRole('dialog', { name: '几步准备，开启试穿浮窗' })).toBeVisible()
  expect(api.providerRequests).toEqual([])
  expect(api.errors).toEqual([])
})

test('legacy favorites links open the history section on mobile and survive reload', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  const api = await setup(page, { view: 'favorites', initialFavorites: [{ ...favorite,
    name: '购物白衬衫', sourceKind: 'SHOPPING', sourceUrl: 'https://example.com/product/white-shirt' }] })
  await expect(page.getByRole('heading', { name: '搭配记录', level: 1 })).toBeVisible()
  await expect(page.getByRole('button', { name: '喜欢的穿搭', exact: true })).toHaveAttribute('aria-pressed', 'true')
  await expect(page.getByRole('link', { name: '查看原商品' })).toHaveAttribute('href', 'https://example.com/product/white-shirt')
  await page.reload()
  await expect(page.locator('.favorite-card')).toContainText('购物白衬衫')
  const bounds = await page.locator('.favorites-page').boundingBox()
  expect(bounds.x).toBeGreaterThanOrEqual(16)
  expect(bounds.x + bounds.width).toBeLessThanOrEqual(374)
  expect(api.errors).toEqual([])
})

test('navigation and logout dismiss the preview and extension setup', async ({ page }) => {
  const api = await setup(page)
  await openDialog(page)
  await page.evaluate(() => { window.location.hash = '#home' })
  await expect(page).toHaveURL(/#recommend$/)
  await expect(page.getByRole('dialog')).toHaveCount(0)
  await page.goto('/#favorites')
  await openDialog(page)
  await page.getByRole('button', { name: '查看扩展安装引导', exact: true }).click()
  // A logout can complete after a modal opens; it must remove the cached guide.
  await page.evaluate(() => document.querySelector('[aria-label="退出登录"]').click())
  await expect(page.getByRole('dialog')).toHaveCount(0)
  await expect(page.locator('.guest-header')).toBeVisible()
  expect(api.providerRequests).toEqual([])
  expect(api.errors).toEqual([])
})

test('mobile favorite try-on guidance stays inside the viewport and only offers supported garments', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await setup(page)
  const unsupported = page.locator('.favorite-card').filter({ has: page.getByRole('heading', { name: '乐福鞋', exact: true }) })
  await expect(unsupported.getByRole('button', { name: '再次试穿', exact: true })).toBeDisabled()
  await openDialog(page)
  const dialog = page.getByRole('dialog', { name: '实时试衣', exact: true })
  await expect(dialog.getByRole('combobox', { name: '试穿单品' }).locator('option')).toHaveText(['白衬衫 · 上装', '直筒牛仔裤 · 下装'])
  const bounds = await dialog.boundingBox()
  expect(bounds.x).toBeGreaterThanOrEqual(0)
  expect(bounds.x + bounds.width).toBeLessThanOrEqual(390)
  await expect(page.getByRole('button', { name: '查看扩展安装引导', exact: true })).toBeVisible()
  await page.screenshot({ path: '../output/review/fix-mobile-try-on-guidance.png', fullPage: true })
})
