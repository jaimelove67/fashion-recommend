import { expect, test } from '@playwright/test'
import { mkdir } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'

const reviewDir = process.env.E2E_SCREENSHOT_DIR || fileURLToPath(new URL('../../output/recommendation-rationale-update/', import.meta.url))
const reason = '米白与深蓝的配色简洁，适合日常通勤。衬衫和长裤的风格一致，整体更利落。'
const legacyReason = '用户为172cm女性，人工确认方脸，米白衬衫（ID:1）精准响应；其挺括质地适合通勤。深蓝长裤（ID:2）为直筒版型，与172cm身高匹配，深蓝与米白形成简洁的配色，适合通勤。无反馈评分缺失，组合逻辑闭环，完全覆盖需求。'

const wardrobe = [
  { id: 1, name: '米白衬衫', category: '上装', color: '米白', style: '通勤' },
  { id: 2, name: '深蓝长裤', category: '下装', color: '深蓝', style: '通勤' }
]
const weather = { city: '长沙', temperatureC: 15, apparentTemperatureC: 14, precipitationMm: 0.2,
  weatherCode: 53, windSpeedKmh: 12, source: 'wttr.in', observedAt: '2026-10-03T02:55:41Z' }
const recommendation = { id: 7, city: '长沙', occasion: '通勤', temperatureC: 15,
  summary: '通勤推荐', reason, weather, items: wardrobe, engine: 'llm', saved: false }

async function openRecommendation(page, { saved = true, weatherAvailable = saved, savedReason = reason } = {}) {
  const errors = []
  page.on('pageerror', (error) => errors.push(error.message))
  // A different startup city must not replace the weather saved with this outfit.
  await page.addInitScript(() => localStorage.setItem('fashion.weather.city', '上海'))
  await page.route('**/api/**', async (route) => {
    const pathname = new URL(route.request().url()).pathname
    let data = null
    if (pathname === '/api/v1/auth/csrf') data = { token: 'context-fixture', headerName: 'X-XSRF-TOKEN' }
    else if (pathname === '/api/v1/auth/me') data = { username: 'context-fixture', authorities: ['ROLE_USER'] }
    else if (pathname === '/api/v1/trends') data = { items: [], styles: [], sources: [] }
    else if (pathname === '/api/v1/me/wardrobe') data = wardrobe
    else if (pathname === '/api/v1/me/style-profile') data = { gender: null, styleTags: ['通勤'] }
    else if (pathname === '/api/v1/me/recommendations') data = {
      content: saved ? [{ ...recommendation, reason: savedReason }] : [],
      totalElements: saved ? 1 : 0, page: 0, hasNext: false
    }
    else if (pathname === '/api/v1/recommendations' && route.request().method() === 'POST') data = { ...recommendation, id: 8 }
    if (pathname.startsWith('/api/v1/weather/')) {
      if (!weatherAvailable) return route.fulfill({ status: 503, contentType: 'application/json',
        body: JSON.stringify({ code: 503, message: '天气服务暂时不可用', data: null }) })
      data = { ...weather, city: '上海', temperatureC: 31, apparentTemperatureC: 32, weatherCode: 0 }
    }
    return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 0, message: 'ok', data }) })
  })
  await page.goto('/#recommend')
  await expect(page.getByRole('heading', { name: saved ? '搭配方案已生成。' : '衣橱已就绪，创建穿搭方案。' })).toBeVisible()
  return errors
}

test('local weather stays in sync with navigation while the outfit keeps its saved context', async ({ page }) => {
  const errors = await openRecommendation(page)
  for (let i = 0; i < 2; i++) {
    await expect(page.locator('.weather-reading > strong')).toHaveText('31°')
    await expect(page.locator('.weather-reading > div > span')).toHaveText('上海')
    await expect(page.locator('.weather-nav-value')).toHaveText('31°')
    await expect(page.locator('.weather-brief .brief-label')).toHaveText('当地天气')
    await expect(page.locator('.weather-brief-top strong')).toHaveText('晴')
    await expect(page.locator('.ootd-board-heading > strong')).toHaveText('毛毛雨 15° · 通勤')
    await expect(page.locator('.weather-brief')).not.toContainText(/体感|风速|剪裁|完整推荐理由/)
    await expect(page.locator('.ootd-rationale dl')).not.toContainText(/待生成|通勤推荐|完整推荐理由/)
    if (i === 0) await page.reload()
  }
  await page.setViewportSize({ width: 2034, height: 927 })
  await mkdir(reviewDir, { recursive: true })
  await page.locator('.recommendation-hero').screenshot({ path: reviewDir + '/03-after-weather.png' })
  await page.locator('.ootd-rationale').screenshot({ path: reviewDir + '/04-after-rationale.png' })
  await expect(page.locator('.ootd-reason p')).toBeVisible()
  await expect(page.locator('.ootd-reason p')).toHaveText(reason)
  await expect(page.getByRole('button', { name: '调整条件', exact: true })).toHaveCount(0)
  await expect(page.getByRole('button', { name: '生成类似搭配', exact: true })).toHaveCount(0)
  await expect(page.getByText(reason, { exact: true })).toHaveCount(1)
  await page.setViewportSize({ width: 390, height: 844 })
  await expect(page.locator('.ootd-reason p')).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await page.locator('.ootd-rationale').screenshot({ path: reviewDir + '/05-after-mobile.png' })
  expect(errors).toEqual([])
})

test('unavailable weather stays unknown in the current outfit preview', async ({ page }) => {
  const errors = await openRecommendation(page, { saved: false })
  await expect(page.locator('.weather-reading > strong')).toHaveText('--°')
  await expect(page.locator('.weather-brief-top strong')).toHaveText('天气待获取')
  await expect(page.locator('.ootd-board-heading > strong')).toContainText('天气待获取 --°')
  expect(errors).toEqual([])
})

test('unavailable local weather does not reuse the saved outfit temperature', async ({ page }) => {
  const errors = await openRecommendation(page, { weatherAvailable: false })
  await expect(page.locator('.weather-reading > strong')).toHaveText('--°')
  await expect(page.locator('.weather-brief-top strong')).toHaveText('天气待获取')
  await expect(page.locator('.weather-nav-value')).toHaveCount(0)
  await expect(page.locator('.ootd-board-heading > strong')).toHaveText('毛毛雨 15° · 通勤')
  expect(errors).toEqual([])
})

test('a new recommendation links to one concise visible explanation', async ({ page }) => {
  const errors = await openRecommendation(page)
  await page.getByRole('button', { name: '打开知己 AI 助手', exact: true }).click()
  const assistant = page.getByRole('dialog', { name: '知己 AI 助手' })
  await assistant.locator('#assistant-draft').fill('长沙通勤')
  await assistant.getByRole('button', { name: '生成穿搭推荐' }).click()
  await expect(assistant.locator('.assistant-result-card')).toBeVisible()
  await assistant.getByRole('button', { name: '关闭知己助手' }).click()
  await expect(page.getByText(reason, { exact: true })).toHaveCount(1)
  await expect(page.locator('.ootd-reason p')).toBeVisible()
  await page.locator('.generated-reason .rationale-link').focus()
  await page.keyboard.press('Enter')
  await expect(page.locator('.ootd-reason')).toBeFocused()
  await expect(page.locator('.ootd-reason p')).toHaveText(reason)
  expect(errors).toEqual([])
})

test('restored legacy explanations omit internal metadata and unconfirmed properties', async ({ page }) => {
  const errors = await openRecommendation(page, { savedReason: legacyReason })
  const explanation = page.locator('.ootd-reason p')
  await expect(explanation).toHaveText('深蓝与米白形成简洁的配色，适合通勤。')
  await expect(explanation).not.toContainText(/ID|172|评分|闭环|挺括|直筒|完全覆盖/)
  await expect(page.locator('.ootd-board-actions')).toHaveCount(0)
  await mkdir(reviewDir, { recursive: true })
  await page.locator('.ootd-rationale').screenshot({ path: reviewDir + '/06-legacy-reason.png' })
  expect(errors).toEqual([])
})
