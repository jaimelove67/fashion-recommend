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

async function setup(page, { generic = false, failFirstSave = false, platform = 'weibo', weeklyCount = 1 } = {}) {
  const queries = []
  const mutations = []
  const favorites = []
  const errors = []
  let saveAttempts = 0
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
    else if (path === '/api/v1/auth/me') data = { username: 'trend-test', authorities: ['ROLE_USER'] }
    else if (path === '/api/v1/me/wardrobe') data = []
    else if (path === '/api/v1/me/recommendations') data = { content: [], totalElements: 0, page: 0, hasNext: false }
    else if (path === '/api/v1/me/style-profile') data = { gender: null, stylePreferences: [] }
    else if (path === '/api/v1/weather/current') data = { city: '长沙', temperatureC: 22, source: 'open-meteo' }
    else if (path === '/api/v1/outfit-techniques') data = { items: [], status: 'disabled' }
    else if (path === '/api/v1/trends') {
      queries.push(Object.fromEntries(url.searchParams))
      if (url.searchParams.get('topic') === '暂不可用') return route.fulfill({ status: 503, contentType: 'application/json',
        body: JSON.stringify({ code: 503, message: '趋势暂时无法加载' }) })
      const empty = url.searchParams.get('topic') === '无匹配内容'
      const item = generic ? { ...currentTrend, title: '通勤风格灵感', summary: '简洁自然的日常风格。' } : currentTrend
      const items = url.searchParams.get('period') === 'week' && weeklyCount > 1
        ? Array.from({ length: weeklyCount }, (_, index) => ({ ...item, id: `weekly-${index}`,
          title: `第${index + 1}套白衬衫参考`, imageUrl: `${picture}?look=${index}`,
          evidence: { mediaType: 'image', images: [`${picture}?look=${index}`], fullBodyImageUrl: `${picture}?look=${index}` } })) : [item]
      data = { items: empty ? [] : items, sources: [{ id: platform, state: 'ready' }], styles: [],
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
  return { queries, mutations, errors }
}

test('advanced filters retain daily defaults and submit week, platform and trimmed topic with empty-result recovery', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 900 })
  const { queries, errors } = await setup(page)
  await expect(page.getByLabel('内容来源', { exact: true })).toHaveCount(0)
  expect(queries[0].period).toBe('day')
  await page.getByRole('button', { name: '高级筛选', exact: true }).click()
  await page.getByLabel('时间范围', { exact: true }).selectOption('week')
  await page.getByLabel('内容来源', { exact: true }).selectOption('weibo')
  await page.getByLabel('标题或标签关键词', { exact: true }).fill('  无匹配内容  ')
  await page.getByRole('button', { name: '应用筛选', exact: true }).click()
  await expect(page.getByText('当前筛选范围暂无穿搭内容', { exact: true })).toBeVisible()
  expect(queries.at(-1)).toMatchObject({ period: 'week', platform: 'weibo', topic: '无匹配内容' })
  await expect(page.locator('.trend-daily-composition')).toHaveCount(0)
  await page.getByRole('button', { name: '清除筛选并恢复每日精选', exact: true }).click()
  await expect(page.locator('.trend-daily-composition')).toBeVisible()
  expect(queries.at(-1).period).toBe('day')
  expect(queries.at(-1).platform || '').toBe('')
  expect(queries.at(-1).topic || '').toBe('')
  await expect(page.getByLabel('时间范围', { exact: true })).toHaveValue('day')
  await expect(page.getByLabel('标题或标签关键词', { exact: true })).toHaveValue('')
  await page.screenshot({ path: '../output/feature-gaps/trend-desktop.png', fullPage: true })
  await page.setViewportSize({ width: 390, height: 844 })
  await expect(page.getByRole('button', { name: '应用筛选', exact: true })).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await page.screenshot({ path: '../output/feature-gaps/trend-mobile.png', fullPage: true })
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

test('failed weekly filters identify retained daily content instead of relabelling it as a weekly result', async ({ page }) => {
  await setup(page)
  await expect(page.locator('.trend-count')).toContainText('每日精选')
  await page.getByRole('button', { name: '高级筛选', exact: true }).click()
  await page.getByLabel('时间范围', { exact: true }).selectOption('week')
  await page.getByLabel('标题或标签关键词', { exact: true }).fill('暂不可用')
  await page.getByRole('button', { name: '应用筛选', exact: true }).click()
  await expect(page.getByRole('region', { name: '穿搭高级筛选' }).getByRole('alert')).toContainText('仍显示上次成功加载的内容')
  await expect(page.locator('.trend-count')).toContainText('每日精选')
  await page.getByRole('button', { name: '高级筛选', exact: true }).click()
  await expect(page.getByRole('region', { name: '穿搭高级筛选' }).getByRole('alert')).toBeVisible()
})

test('weekly content beyond the ten-image gallery remains reachable without repeating gallery entries', async ({ page }) => {
  await setup(page, { weeklyCount: 12 })
  await page.getByRole('button', { name: '高级筛选', exact: true }).click()
  await page.getByLabel('时间范围', { exact: true }).selectOption('week')
  await page.getByRole('button', { name: '应用筛选', exact: true }).click()
  const more = page.getByRole('region', { name: '更多近 7 天内容', exact: true })
  await expect(more.getByRole('listitem')).toHaveCount(2)
  await expect(more.getByRole('button', { name: '查看第1套白衬衫参考', exact: true })).toHaveCount(0)
  await more.getByRole('button', { name: '查看第12套白衬衫参考', exact: true }).click()
  await expect(page.getByRole('dialog', { name: '深入分析', exact: true }).locator('.analysis-look-title')).toHaveText('第12套白衬衫参考')
})
