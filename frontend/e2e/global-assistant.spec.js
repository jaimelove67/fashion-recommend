import { expect, test } from '@playwright/test'
import { mkdir } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'

const reviewDir = fileURLToPath(new URL('../../output/global-assistant/', import.meta.url))
const wardrobe = [
  { id: 1, name: '米白衬衫', category: '上装', color: '米白', style: '通勤' },
  { id: 2, name: '深蓝长裤', category: '下装', color: '深蓝', style: '通勤' }
]
const weather = { city: '长沙', temperatureC: 22, apparentTemperatureC: 22, precipitationMm: 0,
  weatherCode: 0, windSpeedKmh: 8, source: 'wttr.in', observedAt: '2026-10-03T02:00:00Z' }
const recommendation = { id: 7, city: '长沙', occasion: '通勤', temperatureC: 22, weather,
  summary: '米白与深蓝的简洁通勤搭配', reason: '米白与深蓝的配色简洁，适合日常通勤。',
  items: wardrobe, engine: 'llm', saved: false }

async function openApp(page, view = 'home', options = {}) {
  const errors = []
  const requests = []
  let generated = null
  let authenticated = true
  page.on('pageerror', (error) => errors.push(error.message))
  await page.addInitScript(() => localStorage.setItem('fashion.weather.city', '长沙'))
  await page.route('**/api/**', async (route) => {
    const request = route.request()
    const pathname = new URL(request.url()).pathname
    let data = null
    let status = 200
    let message = 'ok'
    if (pathname === '/api/v1/auth/csrf') data = { token: 'assistant-fixture', headerName: 'X-XSRF-TOKEN' }
    else if (pathname === '/api/v1/auth/me') {
      if (authenticated) data = { username: 'assistant-fixture', authorities: ['ROLE_USER'] }
      else status = 401
    } else if (pathname === '/api/v1/auth/logout') authenticated = false
    else if (pathname === '/api/v1/trends') data = { items: [], styles: [], sources: [] }
    else if (pathname === '/api/v1/me/wardrobe') data = wardrobe
    else if (pathname === '/api/v1/me/style-profile') data = { gender: null, styleTags: ['通勤'] }
    else if (pathname.startsWith('/api/v1/weather/')) data = weather
    else if (pathname === '/api/v1/me/recommendations') data = {
      content: generated ? [generated] : [], totalElements: generated ? 1 : 0, page: 0, hasNext: false
    }
    else if (pathname === '/api/v1/recommendations' && request.method() === 'POST') {
      requests.push(request.postDataJSON())
      if (options.generationGate) await options.generationGate
      status = options.generationStatus || 200
      if (status === 200) data = generated = { ...recommendation }
      else {
        message = status === 401 ? '登录已过期，请重新登录。' : '当前衣橱缺少可组合的不同类别衣物'
        if (status === 401) authenticated = false
      }
    } else if (pathname === '/api/v1/me/recommendations/7/save') {
      data = generated = { ...recommendation, saved: true }
    }
    await route.fulfill({ status, contentType: 'application/json', body: JSON.stringify({ code: status === 200 ? 0 : status, message, data }) })
  })
  await page.goto(`/#${view}`)
  await expect(page.getByRole('button', { name: '打开知己 AI 助手', exact: true })).toBeVisible()
  return { errors, requests }
}

function assistantDialog(page) {
  return page.getByRole('dialog', { name: '知己 AI 助手', exact: true })
}

test('the navigation icon opens the assistant from every user page without changing the page', async ({ page }) => {
  const { errors } = await openApp(page)
  for (const view of ['home', 'trend', 'recommend', 'wardrobe', 'history', 'profile']) {
    await page.evaluate((nextView) => { window.location.hash = nextView }, view)
    await expect(page).toHaveURL(new RegExp(`#${view}$`))
    if (view === 'profile') await expect(page.locator('.profile-button')).toHaveClass(/active/)
    else await expect(page.locator(`.pill-nav-desktop button[aria-current="page"]`)).toHaveAttribute('aria-label', {
      home: '首页', trend: '穿搭趋势', recommend: '穿搭推荐', wardrobe: '我的衣橱', history: '搭配记录'
    }[view])
    const opener = page.getByRole('button', { name: '打开知己 AI 助手', exact: true })
    await opener.click()
    await expect(assistantDialog(page)).toBeVisible()
    await expect(page.locator('.pill-nav-logo')).toHaveAttribute('aria-expanded', 'true')
    await expect(page).toHaveURL(new RegExp(`#${view}$`))
    await expect(assistantDialog(page).getByRole('textbox', { name: '穿搭需求' })).toBeFocused()
    await page.keyboard.press('Escape')
    await expect(assistantDialog(page)).toHaveCount(0)
    await expect(opener).toBeFocused()
  }
  expect(errors).toEqual([])
})

test('a wardrobe-page recommendation and unfinished draft survive navigation and remain linked to the recommendation page', async ({ page }) => {
  const { errors, requests } = await openApp(page, 'wardrobe')
  await page.getByRole('button', { name: '打开知己 AI 助手', exact: true }).click()
  const dialog = assistantDialog(page)
  await dialog.getByRole('textbox', { name: '穿搭需求' }).fill('长沙通勤，希望简洁得体')
  await dialog.getByRole('button', { name: '生成穿搭推荐', exact: true }).click()
  await expect(dialog.locator('.assistant-result-card')).toBeVisible()
  expect(requests).toEqual([{ occasion: '通勤', city: '长沙', styleHint: '长沙通勤，希望简洁得体' }])
  await expect(page).toHaveURL(/#wardrobe$/)
  await dialog.getByRole('button', { name: '收藏搭配', exact: true }).click()
  await expect(dialog.getByRole('button', { name: '已收藏', exact: true })).toBeDisabled()
  await dialog.getByRole('textbox', { name: '穿搭需求' }).fill('下一套想更轻松一点')
  await page.keyboard.press('Escape')
  await page.getByRole('button', { name: '穿搭趋势', exact: true }).click()
  await page.getByRole('button', { name: '打开知己 AI 助手', exact: true }).click()
  await expect(dialog.locator('.assistant-message-user')).toHaveText('长沙通勤，希望简洁得体')
  await expect(dialog.getByRole('textbox', { name: '穿搭需求' })).toHaveValue('下一套想更轻松一点')
  await expect(dialog.locator('.assistant-result-card')).toHaveCount(1)
  await expect(dialog.getByRole('button', { name: '已收藏', exact: true })).toBeDisabled()
  await expect(dialog.getByRole('button', { name: '已收藏', exact: true })).toBeInViewport()
  await mkdir(reviewDir, { recursive: true })
  await page.screenshot({ path: reviewDir + '/desktop.png', animations: 'disabled' })
  await page.keyboard.press('Escape')
  await page.getByRole('button', { name: '穿搭推荐', exact: true }).click()
  await expect(page.locator('.generated-card')).toContainText('搭配 #7')
  await expect(page.locator('.generated-card')).toContainText('已收藏')
  await expect(page.locator('.assistant-entry-panel')).toHaveCount(0)
  expect(errors).toEqual([])
})

test('recommendation-page shortcuts open the same window and keyboard focus stays inside until dismissal', async ({ page }) => {
  const { errors } = await openApp(page, 'recommend')
  const shortcut = page.getByRole('button', { name: '打开穿搭助手', exact: true })
  await shortcut.click()
  const dialog = assistantDialog(page)
  await expect(dialog).toBeVisible()
  await expect(page.locator('.app-shell')).toHaveJSProperty('inert', true)
  await expect(page.locator('body')).toHaveClass(/assistant-is-open/)
  await dialog.getByRole('textbox', { name: '穿搭需求' }).fill('长沙通勤')
  await dialog.getByRole('button', { name: '关闭知己助手' }).focus()
  await page.keyboard.press('Shift+Tab')
  await expect(dialog.getByRole('button', { name: '生成穿搭推荐', exact: true })).toBeFocused()
  await page.keyboard.press('Tab')
  await expect(dialog.getByRole('button', { name: '关闭知己助手' })).toBeFocused()
  await page.locator('.assistant-layer').click({ position: { x: 10, y: 10 } })
  await expect(dialog).toHaveCount(0)
  await expect(shortcut).toBeFocused()
  await expect(page.locator('.app-shell')).toHaveJSProperty('inert', false)
  await expect(page.locator('body')).not.toHaveClass(/assistant-is-open/)
  await page.getByRole('button', { name: '打开知己 AI 助手', exact: true }).click()
  await expect(dialog).toHaveCount(1)
  await expect(dialog.getByRole('textbox', { name: '穿搭需求' })).toHaveValue('长沙通勤')
  expect(errors).toEqual([])
})

test('the mobile icon closes the page menu and opens a drawer inside the viewport', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  const { errors } = await openApp(page, 'wardrobe')
  await page.getByRole('button', { name: '打开页面菜单', exact: true }).click()
  await page.getByRole('button', { name: '打开知己 AI 助手', exact: true }).click()
  const dialog = assistantDialog(page)
  await expect(dialog).toBeVisible()
  await expect(page.locator('.pill-nav-hamburger')).toHaveAttribute('aria-expanded', 'false')
  await expect(dialog.getByRole('textbox', { name: '穿搭需求' })).toBeFocused()
  await expect(dialog).toHaveCSS('opacity', '1')
  const bounds = await dialog.boundingBox()
  expect(bounds.width).toBe(390)
  expect(bounds.y).toBeGreaterThanOrEqual(0)
  expect(bounds.y + bounds.height).toBeLessThanOrEqual(844)
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  expect(await dialog.evaluate((element) => getComputedStyle(element).backgroundColor)).toBe('rgb(255, 254, 250)')
  await mkdir(reviewDir, { recursive: true })
  await page.screenshot({ path: reviewDir + '/mobile.png', animations: 'disabled' })
  await page.keyboard.press('Escape')
  await expect(page.locator('body')).not.toHaveClass(/assistant-is-open/)
  expect(errors).toEqual([])
})

test('session expiry during generation dismisses the global window and releases the page', async ({ page }) => {
  let releaseGeneration
  const generationGate = new Promise((resolve) => { releaseGeneration = resolve })
  const { errors } = await openApp(page, 'wardrobe', { generationGate, generationStatus: 401 })
  await page.getByRole('button', { name: '打开知己 AI 助手', exact: true }).click()
  const dialog = assistantDialog(page)
  await dialog.getByRole('textbox', { name: '穿搭需求' }).fill('长沙通勤')
  await dialog.getByRole('button', { name: '生成穿搭推荐', exact: true }).click()
  await expect(dialog.getByRole('status')).toContainText('正在生成搭配方案')
  releaseGeneration()
  await expect(dialog).toHaveCount(0)
  await expect(page.getByRole('button', { name: '点击图标，进入知己', exact: true })).toBeVisible()
  await expect(page.locator('body')).not.toHaveClass(/assistant-is-open/)
  await expect(page.locator('.app-shell')).toHaveCount(0)
  await expect(page.locator('.assistant-result-card')).toHaveCount(0)
  expect(errors).toEqual([])
})
