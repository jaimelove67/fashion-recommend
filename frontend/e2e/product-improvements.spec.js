import { expect, test } from '@playwright/test'

const clothes = [
  { id: 1, name: '白衬衫', category: '上装', color: '白色' },
  { id: 2, name: '蓝长裤', category: '下装', color: '蓝色' },
  { id: 3, name: '黑长裤', category: '下装', color: '黑色' }
]
const weather = { city: '长沙', temperatureC: 25, apparentTemperatureC: 25, weatherCode: 0, source: 'open-meteo' }
const original = { id: 1, occasion: '通勤', city: '长沙', summary: '简洁通勤', reason: '配色协调，适合通勤。',
  items: clothes.slice(0, 2), weather, temperatureC: 25, generatedAt: '2026-10-01T03:00:00Z', engine: 'llm' }

async function setup(page, view = 'recommend', fail = false) {
  let latest = original
  const calls = []
  const errors = []
  page.on('pageerror', error => errors.push(error.message))
  await page.addInitScript(() => localStorage.setItem('fashion.weather.city', '长沙'))
  await page.route('**/api/**', async route => {
    const req = route.request()
    const url = new URL(req.url())
    let data = null
    if (url.pathname.endsWith('/auth/me')) data = { username: 'test', authorities: ['ROLE_USER'] }
    else if (url.pathname.endsWith('/auth/csrf')) data = { token: 'test', headerName: 'X-XSRF-TOKEN' }
    else if (url.pathname.endsWith('/wardrobe')) data = clothes
    else if (url.pathname.endsWith('/style-profile')) data = { gender: null, analysis: {}, styleTags: [] }
    else if (url.pathname.includes('/weather/')) {
      if (fail === true) return route.fulfill({ status: 503, contentType: 'application/json', body: JSON.stringify({ code: 503, message: '天气服务暂时不可用' }) })
      data = { ...weather, city: url.searchParams.has('latitude') ? '当前位置' : '长沙' }
    }
    else if (url.pathname.endsWith('/trends')) data = { items: [], sources: [], styles: [] }
    else if (url.pathname === '/api/v1/me/recommendations') {
      calls.push(url.search)
      const filtered = url.searchParams.get('filter') === 'saved' || url.searchParams.has('query')
      data = { content: filtered ? [{ ...original, id: 99, saved: true, summary: '旧收藏' }] : [latest],
        page: 0, totalElements: filtered ? 1 : 45, hasNext: false,
        statistics: { total: 45, saved: 7, rated: 3, averageRating: 4.5, coveredItems: 3 } }
    } else if (url.pathname === '/api/v1/recommendations') {
      const body = req.postDataJSON()
      calls.push(body)
      if (fail === 'replacement') return route.fulfill({ status: 422, contentType: 'application/json', body: JSON.stringify({ code: 422, message: '当前条件下无法组成完整搭配，请解除保留或排除条件' }) })
      if (fail && body.manualTemperatureC == null && body.latitude == null) return route.fulfill({ status: 503, contentType: 'application/json', body: JSON.stringify({ code: 503, message: '天气服务暂时不可用' }) })
      data = latest = { ...original, id: 2, generatedAt: '2026-10-07T03:00:00Z', items: [clothes[0], clothes[2]],
        ...(body.manualTemperatureC != null ? { temperatureC: body.manualTemperatureC,
          weather: { city: '长沙', source: 'user-provided', temperatureC: body.manualTemperatureC } } : {}) }
    }
    await route.fulfill({ contentType: 'application/json', body: JSON.stringify({ code: 0, data }) })
  })
  await page.goto('/#' + view)
  await expect(page.locator('.app-shell')).toBeVisible()
  return { calls, errors }
}

test('saved date is honest and replacing trousers locks the top', async ({ page }) => {
  const { calls, errors } = await setup(page)
  await expect(page.getByRole('button', { name: '前一天', exact: true })).toHaveCount(0)
  await expect(page.getByRole('button', { name: '后一天', exact: true })).toHaveCount(0)
  await expect(page.locator('.ootd-header')).toContainText('生成于 10月1日')
  await page.getByRole('button', { name: '替换蓝长裤', exact: true }).click()
  await expect(page.getByRole('button', { name: '替换黑长裤', exact: true })).toBeVisible()
  const body = calls.find(value => typeof value === 'object')
  expect(body.lockedItemIds).toEqual([1])
  expect(body.excludedItemIds).toEqual([2])
  expect(body.occasion).toBe('通勤')
  expect(errors).toEqual([])
  await page.screenshot({ path: '../output/product-improvements/recommendation-desktop.png', fullPage: true })
})

test('failed weather can be replaced by explicit manual temperature without losing the draft', async ({ page }) => {
  await page.addInitScript(() => Object.defineProperty(navigator, 'geolocation', { value: { getCurrentPosition(ok, fail) { fail({ code: 1 }) } } }))
  const { calls, errors } = await setup(page, 'recommend', true)
  await page.setViewportSize({ width: 390, height: 844 })
  await page.getByRole('button', { name: '打开知己 AI 助手', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '知己 AI 助手' })
  await expect(dialog.getByRole('checkbox', { name: /手动填写温度/ })).toHaveCount(0)
  await dialog.getByRole('textbox', { name: '穿搭需求' }).fill('长沙通勤')
  await dialog.getByRole('button', { name: '生成穿搭推荐', exact: true }).click()
  await expect(dialog).toContainText('天气服务暂时不可用')
  await expect(dialog.getByRole('textbox', { name: '穿搭需求' })).toHaveValue('长沙通勤')
  await dialog.getByRole('button', { name: '开启定位获取天气', exact: true }).click()
  await expect(dialog).toContainText('定位权限未开启')
  await expect(dialog.getByRole('checkbox', { name: /手动填写温度/ })).toHaveCount(0)
  await dialog.getByRole('textbox', { name: '天气城市', exact: true }).fill('长沙')
  await dialog.getByRole('button', { name: '获取城市天气', exact: true }).click()
  await dialog.getByRole('checkbox', { name: /手动填写温度/ }).check()
  await dialog.getByRole('spinbutton', { name: '手动温度' }).fill('22')
  await dialog.getByRole('button', { name: '生成穿搭推荐', exact: true }).click()
  await expect(dialog.locator('.assistant-result-card')).toContainText('手动温度 22℃ · 非实时天气')
  expect(calls.filter(value => typeof value === 'object').at(-1).manualTemperatureC).toBe(22)
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  expect(errors).toEqual([])
  await dialog.locator('.assistant-result-card').getByText('手动温度 22℃ · 非实时天气').scrollIntoViewIfNeeded()
  await page.screenshot({ path: '../output/product-improvements/assistant-mobile.png' })
})

test('successful location is used by recommendation and never offers manual weather initially', async ({ page }) => {
  await page.addInitScript(() => Object.defineProperty(navigator, 'geolocation', { value: {
    getCurrentPosition(ok) { ok({ coords: { latitude: 28.2, longitude: 112.9 } }) }
  } }))
  const { calls } = await setup(page, 'recommend', true)
  await page.getByRole('button', { name: '打开知己 AI 助手', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '知己 AI 助手' })
  await expect(dialog.getByRole('checkbox', { name: /手动填写温度/ })).toHaveCount(0)
  await dialog.getByRole('textbox', { name: '穿搭需求' }).fill('长沙通勤')
  await dialog.getByRole('button', { name: '生成穿搭推荐', exact: true }).click()
  await expect(dialog).toContainText('天气服务暂时不可用')
  await page.route('**/api/v1/weather/current?*', route => route.fulfill({ contentType: 'application/json', body: JSON.stringify({ code: 0, data: { ...weather, city: '当前位置' } }) }))
  await dialog.getByRole('button', { name: '开启定位获取天气', exact: true }).click()
  await expect(dialog.locator('.assistant-drawer-context')).toContainText('当前位置')
  await expect(dialog.locator('.weather-recovery')).toHaveCount(0)
  await expect(dialog.getByRole('textbox', { name: '穿搭需求' })).toHaveValue('长沙通勤')
  await dialog.getByRole('button', { name: '生成穿搭推荐', exact: true }).click()
  await expect.poll(() => calls.filter(value => typeof value === 'object').length).toBe(2)
  const body = calls.filter(value => typeof value === 'object').at(-1)
  expect(body.city).toBe('当前位置')
  expect(body.latitude).toBe(28.2)
  expect(body.longitude).toBe(112.9)
  expect(body.manualTemperatureC).toBeUndefined()
  await expect(dialog.locator('.assistant-result-card')).toBeVisible()
})

test('history filters are server queries and statistics stay account-wide', async ({ page }) => {
  const { calls, errors } = await setup(page, 'history')
  await expect(page.locator('.stats-band')).toContainText('45')
  await expect(page.locator('.stats-band')).toContainText('7')
  await page.getByRole('button', { name: '已收藏', exact: true }).click()
  await expect(page.locator('.history-list')).toContainText('#99')
  expect(calls.some(value => typeof value === 'string' && value.includes('filter=saved'))).toBe(true)
  await page.getByRole('searchbox', { name: '搜索搭配记录' }).fill('旧收藏')
  await expect.poll(() => calls.some(value => typeof value === 'string' && value.includes('query='))).toBe(true)
  await expect(page.locator('.stats-band')).toContainText('45')
  expect(errors).toEqual([])
})

test('choosing a city successfully restores real weather without offering manual temperature', async ({ page }) => {
  await setup(page, 'recommend', true)
  await page.getByRole('button', { name: '打开知己 AI 助手', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '知己 AI 助手' })
  await dialog.getByRole('button', { name: '不使用定位，选择城市', exact: true }).click()
  await page.route('**/api/v1/weather/current?*', route => route.fulfill({ contentType: 'application/json', body: JSON.stringify({ code: 0, data: weather }) }))
  await dialog.getByRole('textbox', { name: '天气城市', exact: true }).fill('长沙')
  await dialog.getByRole('button', { name: '获取城市天气', exact: true }).click()
  await expect(dialog.locator('.weather-recovery')).toHaveCount(0)
  await expect(dialog.locator('.assistant-drawer-context')).toContainText('25°')
  await expect(dialog.getByRole('checkbox', { name: /手动填写温度/ })).toHaveCount(0)
})

test('a failed replacement keeps the original outfit and allows another attempt', async ({ page }) => {
  await setup(page, 'recommend', 'replacement')
  await page.getByRole('button', { name: '替换蓝长裤', exact: true }).click()
  await expect(page.locator('.outfit-adjustment [role="alert"]')).toContainText('无法组成完整搭配')
  await expect(page.getByRole('button', { name: '替换蓝长裤', exact: true })).toBeEnabled()
  await expect(page.locator('.ootd-board')).toContainText('蓝长裤')
  await page.getByRole('button', { name: '保留白衬衫', exact: true }).click()
  await expect(page.getByRole('button', { name: '保留白衬衫', exact: true })).toHaveAttribute('aria-pressed', 'true')
  await expect(page.getByRole('button', { name: '替换白衬衫', exact: true })).toBeDisabled()
})
