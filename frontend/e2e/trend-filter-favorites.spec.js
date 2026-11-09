import { expect, test } from '@playwright/test'
import { readFileSync } from 'node:fs'

const picture = 'https://images.example.test/whole-look.png'
const sourceUrl = 'https://weibo.com/1234567/fashion-reference'
const trend = {
  id: 'wardrobe-reference', platform: 'weibo', title: '白衬衫与阔腿裤的通勤搭配',
  summary: '用白衬衫搭配阔腿裤，配上白色运动鞋。', topicTags: ['通勤'],
  imageUrl: picture, sourceUrl, stale: false,
  publishedAt: '2026-11-03T01:00:00Z', fetchedAt: '2026-11-03T02:00:00Z',
  evidence: { mediaType: 'image', images: [picture], fullBodyImageUrl: picture }
}
const pixel = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aAt8AAAAASUVORK5CYII=', 'base64')
const lookImage = readFileSync(new URL('../public/assets/look-urban.jpg', import.meta.url))

async function setup(page, { generic = false, failFirstSave = false, platform = 'weibo', admin = false } = {}) {
  const queries = []
  const mutations = []
  const favorites = []
  const errors = []
  let saveAttempts = 0
  let failNextTrend = false
  const currentTrend = { ...trend, platform,
    sourceUrl: platform === 'xiaohongshu' ? 'https://www.xiaohongshu.com/explore/fashion-reference' : sourceUrl }
  page.on('pageerror', error => errors.push(error.message))
  await page.addInitScript(() => localStorage.setItem('fashion.weather.city', '长沙'))
  await page.route(picture + '**', route => route.fulfill({ contentType: 'image/jpeg', body: lookImage }))
  await page.route('**/api/**', async route => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname
    let data = null
    if (request.method() !== 'GET') mutations.push({ path, body: request.postDataBuffer()?.toString('utf8') || '' })
    if (path === '/api/v1/auth/csrf') data = { token: 'trend-test', headerName: 'X-XSRF-TOKEN' }
    else if (path === '/api/v1/auth/me') data = { username: 'trend-test', authorities: admin ? ['ROLE_USER', 'ROLE_ADMIN'] : ['ROLE_USER'] }
    else if (path === '/api/v1/me/wardrobe') data = []
    else if (path === '/api/v1/me/recommendations') data = { content: [], totalElements: 0, page: 0, hasNext: false }
    else if (path === '/api/v1/me/style-profile') data = { gender: null, stylePreferences: [] }
    else if (path === '/api/v1/weather/current') data = { city: '长沙', temperatureC: 22, source: 'open-meteo' }
    else if (path === '/api/v1/outfit-techniques') data = { items: [], status: 'disabled' }
    else if (path === '/api/v1/trends') {
      queries.push(Object.fromEntries(url.searchParams))
      if (failNextTrend) {
        failNextTrend = false
        return route.fulfill({ status: 503, contentType: 'application/json', body: JSON.stringify({ code: 503, message: '趋势暂时无法加载' }) })
      }
      const item = generic ? { ...currentTrend, title: '通勤风格灵感', summary: '简洁自然的日常风格。' } : currentTrend
      data = { items: [item], sources: [{ id: platform, state: 'ready' }], styles: [],
        period: url.searchParams.get('period'),
        dailySelection: url.searchParams.get('period') === 'day'
          ? { menswear: 1, womenswear: 0, niche: 0, target: 5, missing: {}, assignments: [] } : null }
    } else if (path === '/api/v1/me/try-on-favorites' && request.method() === 'POST') {
      saveAttempts += 1
      if (failFirstSave && saveAttempts === 1) return route.fulfill({ status: 503, contentType: 'application/json',
        body: JSON.stringify({ code: 503, message: '收藏服务暂不可用，请重试。' }) })
      data = { id: 72, name: '白衬衫', category: '上装', sourceKind: 'TREND_ILLUSTRATION', sourceUrl: currentTrend.sourceUrl,
        imageUrl: '/api/v1/me/try-on-favorites/72/image' }
      favorites.push(data)
    } else if (path === '/api/v1/me/try-on-favorites') data = favorites
    else if (path === '/api/v1/me/try-on-favorites/72/image') return route.fulfill({ contentType: 'image/png', body: pixel })
    await route.fulfill({ contentType: 'application/json', body: JSON.stringify({ code: 0, data }) })
  })
  await page.goto('/#trend')
  await expect(page.getByRole('heading', { name: '穿搭鉴赏与风格参考' })).toBeVisible()
  return { queries, mutations, errors, failNextTrend: () => { failNextTrend = true } }
}

test('loads the daily feed without advanced filter controls', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 900 })
  const { queries, errors } = await setup(page)
  await expect(page.getByRole('button', { name: '高级筛选', exact: true })).toHaveCount(0)
  await expect(page.getByLabel('时间范围', { exact: true })).toHaveCount(0)
  await expect(page.getByLabel('内容来源', { exact: true })).toHaveCount(0)
  await expect(page.getByLabel('标题或标签关键词', { exact: true })).toHaveCount(0)
  await expect(page.locator('.trend-daily-composition')).toBeVisible()
  await expect(page.locator('.trend-breakdown')).toContainText(trend.title)
  expect(queries[0]).toMatchObject({ period: 'day', platform: '' })
  expect(queries[0].topic || '').toBe('')
  expect(errors).toEqual([])
})

test('saves an explicitly labelled local garment illustration without adding wardrobe items or using the whole outfit', async ({ page }) => {
  const { mutations, errors } = await setup(page)
  const pieces = page.getByRole('region', { name: '收藏款式参考' })
  await expect(pieces).toContainText('非原帖商品图')
  await expect(pieces.getByAltText('白衬衫款式示意图')).toHaveAttribute('src', '/assets/trend-pieces/white-shirt.png')
  await expect(pieces.getByRole('button', { name: '收藏白色运动鞋示意图' })).toHaveCount(0)
  await pieces.getByRole('button', { name: '收藏白衬衫示意图', exact: true }).click()
  await expect(pieces.getByRole('button', { name: '已收藏白衬衫示意图', exact: true })).toBeDisabled()
  const upload = mutations.find(request => request.path === '/api/v1/me/try-on-favorites')
  expect(upload.body).toContain('name="sourceKind"\r\n\r\nTREND_ILLUSTRATION')
  expect(upload.body).toContain('name="name"\r\n\r\n白衬衫')
  expect(upload.body).toContain('name="category"\r\n\r\n上装')
  expect(upload.body).toContain(sourceUrl)
  expect(upload.body).not.toContain(picture)
  expect(mutations.some(request => request.path.includes('/wardrobe'))).toBe(false)
  await pieces.getByRole('button', { name: '查看喜欢的穿搭', exact: true }).click()
  await expect(page.getByRole('heading', { name: '白衬衫', exact: true })).toBeVisible()
  await expect(page.getByText('穿搭示意款', { exact: false })).toBeVisible()
  expect(errors).toEqual([])
})

test('failed illustration save remains retryable and never claims success', async ({ page }) => {
  const { mutations } = await setup(page, { failFirstSave: true })
  const pieces = page.getByRole('region', { name: '收藏款式参考' })
  const save = pieces.getByRole('button', { name: '收藏白衬衫示意图', exact: true })
  await save.click()
  await expect(pieces.getByRole('alert')).toContainText('收藏服务暂不可用')
  await expect(save).toBeEnabled()
  await expect(pieces.getByRole('button', { name: '已收藏白衬衫示意图', exact: true })).toHaveCount(0)
  await save.click()
  await expect(pieces.getByRole('status')).toContainText('白衬衫示意图已保存')
  expect(mutations.filter(request => request.path === '/api/v1/me/try-on-favorites')).toHaveLength(2)
})

test('generic style text does not invent garment illustrations from the outfit photo', async ({ page }) => {
  await setup(page, { generic: true })
  await expect(page.locator('.trend-breakdown')).toContainText('通勤风格灵感')
  await expect(page.getByRole('region', { name: '收藏款式参考' })).toHaveCount(0)
})

test('Xiaohongshu garment mentions use the same illustration collection and keep their original source', async ({ page }) => {
  const { mutations } = await setup(page, { platform: 'xiaohongshu' })
  const pieces = page.getByRole('region', { name: '收藏款式参考' })
  await expect(pieces.getByAltText('白衬衫款式示意图')).toHaveAttribute('src', '/assets/trend-pieces/white-shirt.png')
  await pieces.getByRole('button', { name: '收藏白衬衫示意图', exact: true }).click()
  await expect(pieces.getByRole('status')).toContainText('白衬衫示意图已保存')
  const upload = mutations.find(request => request.path === '/api/v1/me/try-on-favorites')
  expect(upload.body).toContain('name="sourceKind"\r\n\r\nTREND_ILLUSTRATION')
  expect(upload.body).toContain('https://www.xiaohongshu.com/explore/fashion-reference')
})

test('a failed refresh identifies retained content and allows retry', async ({ page }) => {
  const { failNextTrend } = await setup(page, { admin: true })
  await expect(page.locator('.trend-count')).toContainText('每日精选')
  failNextTrend()
  await page.locator('.trend-source-details summary').click()
  await page.getByRole('button', { name: '刷新已连接数据源', exact: true }).click()
  const alert = page.locator('.trend-refresh-error')
  await expect(alert).toContainText('当前显示上次成功加载的内容')
  await expect(page.locator('.trend-count')).toContainText('每日精选')
  await alert.getByRole('button', { name: '重新加载', exact: true }).click()
  await expect(alert).toHaveCount(0)
  await expect(page.locator('.trend-breakdown')).toContainText(trend.title)
})
