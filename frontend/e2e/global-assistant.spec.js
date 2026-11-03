import { expect, test } from '@playwright/test'
import { mkdir } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'

const reviewDir = fileURLToPath(new URL('../../output/global-assistant/', import.meta.url))
const wardrobe = [
  { id: 1, name: '米白衬衫', category: '上装', color: '米白', style: '通勤', imageUrl: '/assets/wardrobe/wardrobe-01-warm-oxford-shirt.png' },
  { id: 2, name: '深蓝长裤', category: '下装', color: '深蓝', style: '通勤' }
]
const weather = { city: '长沙', temperatureC: 22, apparentTemperatureC: 22, precipitationMm: 0,
  weatherCode: 0, windSpeedKmh: 8, source: 'wttr.in', observedAt: '2026-10-03T02:00:00Z' }
const recommendation = { id: 7, city: '长沙', occasion: '通勤', temperatureC: 22, weather,
  summary: '米白与深蓝的简洁通勤搭配', reason: '米白与深蓝的配色简洁，适合日常通勤。',
  items: wardrobe, engine: 'llm', saved: false }

async function openApp(page, view = 'recommend', options = {}) {
  const errors = []
  const requests = []
  const consultRequests = []
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
    else if (pathname === '/api/v1/me/wardrobe') data = options.wardrobe ?? wardrobe
    else if (pathname === '/api/v1/me/style-profile') data = { gender: null, styleTags: ['通勤'] }
    else if (pathname.startsWith('/api/v1/weather/')) {
      data = options.noWeather ? null : weather
      if (options.noWeather) status = 503
    }
    else if (pathname === '/api/v1/assistant/consult') {
      consultRequests.push(request.postDataJSON())
      if (options.consultationGate) await options.consultationGate
      status = options.consultationStatus || 200
      if (status === 401) authenticated = false
      message = status === 401 ? '登录已过期，请重新登录。' : status === 200 ? 'ok' : '穿搭问答服务暂时不可用，请稍后重试'
      data = status === 200 ? {
        answer: '米白衬衫可以搭深蓝长裤。浅色上装与深色下装形成清晰层次。\n想轻松一点，可以尝试米白与卡其的组合。',
        examples: options.wardrobe ?? wardrobe,
        visuals: [{ title: '米白与深蓝', pieces: [
          { label: '上装', color: '米白', hex: '#EEE7D9' },
          { label: '下装', color: '深蓝', hex: '#263A58' }
        ], explanation: '上浅下深突出层次。色块仅用于说明配色关系。' }]
      } : null
    }
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
  return { errors, requests, consultRequests }
}

function assistantDialog(page) {
  return page.getByRole('dialog', { name: '知己 AI 助手', exact: true })
}

for (const width of [1440, 390]) {
  test(`selects wardrobe constraints before the first generation at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: width === 390 ? 844 : 980 })
    const { errors, requests } = await openApp(page, 'recommend', { wardrobe: [...wardrobe,
      { id: 3, name: '灰色外套', category: '外套', color: '灰色' },
      { id: 4, name: '待确认衣物', category: '上装', color: '', recognitionStatus: 'NEEDS_MANUAL_REVIEW' }] })
    await page.getByRole('button', { name: '打开知己 AI 助手', exact: true }).click()
    const dialog = assistantDialog(page)
    await dialog.getByRole('button', { name: '衣橱搭配', exact: true }).click()
    await dialog.locator('.garment-picker summary').click()
    await expect(dialog.getByLabel('待确认衣物的搭配选择')).toHaveCount(0)
    await dialog.getByLabel('米白衬衫的搭配选择').selectOption('locked')
    await dialog.getByLabel('灰色外套的搭配选择').selectOption('excluded')
    await dialog.getByRole('textbox', { name: '穿搭需求' }).fill('请用我的衣橱生成一套通勤搭配')
    await expect(dialog.locator('.garment-picker summary')).toContainText('指定 1/4 · 排除 1/100')
    await dialog.getByRole('searchbox', { name: '搜索衣物' }).fill('衬衫')
    await dialog.getByRole('searchbox', { name: '搜索衣物' }).press('Enter')
    await expect(dialog.getByRole('button', { name: '生成穿搭推荐', exact: true })).toBeEnabled()
    expect(requests).toHaveLength(0)
    await dialog.getByRole('searchbox', { name: '搜索衣物' }).fill('')
    expect(await dialog.evaluate(el => el.scrollWidth <= el.clientWidth)).toBe(true)
    await mkdir(reviewDir, { recursive: true })
    await page.screenshot({ path: `${reviewDir}/garment-picker-${width}.png`, animations: 'disabled' })
    await dialog.getByRole('button', { name: '生成穿搭推荐', exact: true }).click()
    await expect(dialog.locator('.assistant-result-card')).toBeVisible()
    expect(requests[0].lockedItemIds).toEqual([1])
    expect(requests[0].excludedItemIds).toEqual([3])
    expect(errors).toEqual([])
  })
}

test('expanded garment picker and weather recovery remain scrollable on mobile', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 640 })
  await openApp(page, 'recommend', { noWeather: true, wardrobe: Array.from({ length: 12 }, (_, index) => ({
    id: index + 1, name: `衣物${index + 1}`, category: '上装', color: '白色' })) })
  await page.getByRole('button', { name: '打开知己 AI 助手', exact: true }).click()
  const dialog = assistantDialog(page)
  await dialog.getByRole('button', { name: '衣橱搭配', exact: true }).click()
  await dialog.locator('.garment-picker summary').click()
  const send = dialog.getByRole('button', { name: '生成穿搭推荐', exact: true })
  await send.scrollIntoViewIfNeeded()
  const bounds = await send.boundingBox()
  expect(bounds.y + bounds.height).toBeLessThanOrEqual(640)
  expect(await dialog.evaluate(el => el.scrollWidth <= el.clientWidth)).toBe(true)
})

for (const width of [1440, 390]) {
  test(`consultation explains colors without wardrobe or location at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: width === 390 ? 844 : 980 })
    const { errors, requests, consultRequests } = await openApp(page, 'wardrobe', { wardrobe: [], noWeather: true })
    await page.getByRole('button', { name: '打开知己 AI 助手', exact: true }).click()
    const dialog = assistantDialog(page)
    await expect(dialog.getByRole('button', { name: '穿搭问答', exact: true })).toHaveAttribute('aria-pressed', 'true')
    await expect(dialog.getByRole('button', { name: '开启定位获取天气' })).toHaveCount(0)
    await dialog.getByRole('textbox', { name: '穿搭需求' }).fill('米白衬衫怎么配色？')
    await dialog.getByRole('button', { name: '发送穿搭问题' }).click()
    await expect(dialog.locator('.assistant-explanation')).toContainText('米白与深蓝')
    await expect(dialog.locator('.assistant-color-swatch')).toHaveCount(2)
    await expect(dialog.getByRole('list', { name: '问答引用的衣橱单品' })).toHaveCount(0)
    await expect(dialog.locator('.assistant-visual-note')).toContainText('不代表你已拥有')
    await expect(dialog.locator('.assistant-result-card')).toHaveCount(0)
    expect(requests).toEqual([])
    expect(consultRequests[0]).toEqual({ question: '米白衬衫怎么配色？', history: [] })
    const last = dialog.locator('.assistant-color-example').last()
    await last.scrollIntoViewIfNeeded()
    expect(await dialog.evaluate(el => el.scrollWidth <= el.clientWidth)).toBe(true)
    expect(await last.evaluate(el => el.scrollWidth <= el.clientWidth)).toBe(true)
    await mkdir(reviewDir, { recursive: true })
    await page.screenshot({ path: `${reviewDir}/consultation-${width}.png`, animations: 'disabled' })
    await dialog.getByRole('textbox', { name: '穿搭需求' }).fill('那鞋子呢？')
    await dialog.getByRole('button', { name: '发送穿搭问题' }).click()
    await expect.poll(() => consultRequests.length).toBe(2)
    expect(consultRequests[1].history.map(turn => turn.role)).toEqual(['user', 'assistant'])
    expect(consultRequests[1].history[0].text).toBe('米白衬衫怎么配色？')
    expect(errors).toEqual([])
  })
}

test('consultation uses an owned single item and explicit wardrobe text selects generation', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 980 })
  const { requests, consultRequests } = await openApp(page, 'wardrobe', { wardrobe: [wardrobe[0]] })
  await page.getByRole('button', { name: '打开知己 AI 助手', exact: true }).click()
  const dialog = assistantDialog(page)
  await dialog.getByRole('textbox', { name: '穿搭需求' }).fill('米白衬衫怎么搭？')
  await dialog.getByRole('button', { name: '发送穿搭问题' }).click()
  await expect(dialog.getByRole('list', { name: '问答引用的衣橱单品' })).toContainText('米白衬衫')
  await expect(dialog.getByRole('list', { name: '问答引用的衣橱单品' }).getByRole('listitem')).toHaveCount(1)
  const examplePhoto = dialog.getByRole('list', { name: '问答引用的衣橱单品' }).getByRole('img', { name: '米白衬衫' })
  await expect(examplePhoto).toBeVisible()
  await expect.poll(() => examplePhoto.evaluate(img => img.naturalWidth)).toBeGreaterThan(0)
  await mkdir(reviewDir, { recursive: true })
  await page.screenshot({ path: `${reviewDir}/consultation-owned.png`, animations: 'disabled' })
  await dialog.getByRole('textbox', { name: '穿搭需求' }).fill('请用我的衣橱生成一套日常通勤搭配')
  await dialog.getByRole('button', { name: '生成穿搭推荐', exact: true }).click()
  await expect(dialog.locator('.assistant-result-card')).toBeVisible()
  expect(requests).toHaveLength(1)
  expect(consultRequests).toHaveLength(1)
  await expect(dialog.getByRole('button', { name: '穿搭问答', exact: true })).toHaveAttribute('aria-pressed', 'true')
})

test('failed consultation keeps the question for retry without a fabricated answer', async ({ page }) => {
  const { requests } = await openApp(page, 'wardrobe', { consultationStatus: 502 })
  await page.getByRole('button', { name: '打开知己 AI 助手', exact: true }).click()
  const dialog = assistantDialog(page)
  await dialog.getByRole('textbox', { name: '穿搭需求' }).fill('你好，你能做什么？')
  await dialog.getByRole('button', { name: '发送穿搭问题' }).click()
  await expect(dialog.locator('.assistant-message-assistant').last()).toContainText('穿搭问答服务暂时不可用')
  await expect(dialog.getByRole('textbox', { name: '穿搭需求' })).toHaveValue('你好，你能做什么？')
  await expect(dialog.locator('.assistant-explanation')).toHaveCount(0)
  expect(requests).toEqual([])
})

test('wardrobe-related advice stays in consultation and the chosen consultation mode overrides inference', async ({ page }) => {
  const { requests, consultRequests } = await openApp(page, 'wardrobe', { wardrobe: [], noWeather: true })
  await page.getByRole('button', { name: '打开知己 AI 助手', exact: true }).click()
  const dialog = assistantDialog(page)
  const draft = dialog.getByRole('textbox', { name: '穿搭需求' })
  const questions = ['用我的衣橱搭配时，蓝色和绿色合适吗？', '参考我的衣橱，推荐几种适合我的配色？',
    '用我的衣橱选衣服时有哪些技巧？']
  for (const [index, question] of questions.entries()) {
    await draft.fill(question)
    await expect(dialog.getByRole('button', { name: '开启定位获取天气' })).toHaveCount(0)
    await dialog.getByRole('button', { name: '发送穿搭问题' }).click()
    await expect(dialog.locator('.assistant-explanation')).toHaveCount(index + 1)
  }
  // A deliberate mode choice has priority even when the text looks like a generation request.
  await draft.fill('请用我的衣橱生成一套通勤搭配')
  await expect(dialog.getByRole('button', { name: '生成穿搭推荐', exact: true })).toBeVisible()
  await dialog.getByRole('button', { name: '穿搭问答', exact: true }).click()
  await expect(dialog.getByRole('button', { name: '开启定位获取天气' })).toHaveCount(0)
  await dialog.getByRole('button', { name: '发送穿搭问题' }).click()
  await expect(dialog.locator('.assistant-explanation')).toHaveCount(4)
  expect(consultRequests.map(request => request.question)).toEqual([...questions, '请用我的衣橱生成一套通勤搭配'])
  expect(requests).toEqual([])
})

test('session expiry during consultation closes the private conversation', async ({ page }) => {
  let release
  const consultationGate = new Promise(resolve => { release = resolve })
  await openApp(page, 'wardrobe', { consultationGate, consultationStatus: 401 })
  await page.getByRole('button', { name: '打开知己 AI 助手', exact: true }).click()
  const dialog = assistantDialog(page)
  await dialog.getByRole('textbox', { name: '穿搭需求' }).fill('白衬衫怎么搭？')
  await dialog.getByRole('button', { name: '发送穿搭问题' }).click()
  await expect(dialog.getByRole('status')).toContainText('正在整理穿搭建议')
  release()
  await expect(dialog).toHaveCount(0)
  await expect(page.getByRole('heading', { name: '登录知己', exact: true })).toBeVisible()
  await expect(page.locator('body')).not.toHaveClass(/assistant-is-open/)
})

test('the navigation icon opens the assistant from every user page without changing the page', async ({ page }) => {
  const { errors } = await openApp(page)
  for (const view of ['recommend', 'wardrobe', 'trend', 'history', 'profile']) {
    await page.evaluate((nextView) => { window.location.hash = nextView }, view)
    await expect(page).toHaveURL(new RegExp(`#${view}$`))
    if (view === 'profile') await expect(page.locator('.profile-button')).toHaveClass(/active/)
    else await expect(page.locator(`.pill-nav-desktop button[aria-current="page"]`)).toHaveAttribute('aria-label', {
      trend: '穿搭鉴赏', recommend: '穿搭推荐', wardrobe: '我的衣橱', history: '搭配记录'
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
  await dialog.getByRole('button', { name: '衣橱搭配', exact: true }).click()
  await dialog.getByRole('button', { name: '生成穿搭推荐', exact: true }).click()
  await expect(dialog.locator('.assistant-result-card')).toBeVisible()
  expect(requests).toEqual([{ occasion: '通勤', city: '长沙', styleHint: '长沙通勤，希望简洁得体', lockedItemIds: [], excludedItemIds: [] }])
  await expect(page).toHaveURL(/#wardrobe$/)
  await dialog.getByRole('button', { name: '收藏搭配', exact: true }).click()
  await expect(dialog.getByRole('button', { name: '已收藏', exact: true })).toBeDisabled()
  await dialog.getByRole('textbox', { name: '穿搭需求' }).fill('下一套想更轻松一点')
  await page.keyboard.press('Escape')
  await page.getByRole('button', { name: '穿搭鉴赏', exact: true }).click()
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
  await dialog.getByRole('button', { name: '衣橱搭配', exact: true }).click()
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
  expect(await dialog.evaluate((element) => getComputedStyle(element).backgroundColor)).toBe('rgb(252, 250, 245)')
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
  await dialog.getByRole('button', { name: '衣橱搭配', exact: true }).click()
  await dialog.getByRole('button', { name: '生成穿搭推荐', exact: true }).click()
  await expect(dialog.getByRole('status')).toContainText('正在生成搭配方案')
  releaseGeneration()
  await expect(dialog).toHaveCount(0)
  await expect(page.getByRole('heading', { name: '登录知己', exact: true })).toBeVisible()
  await expect(page.getByRole('alert')).toContainText('登录已过期')
  await expect(page.locator('body')).not.toHaveClass(/assistant-is-open/)
  await expect(page.locator('.app-shell')).toHaveCount(0)
  await expect(page.locator('.assistant-result-card')).toHaveCount(0)
  expect(errors).toEqual([])
})
