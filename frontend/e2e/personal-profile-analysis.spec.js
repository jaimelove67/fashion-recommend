import { test, expect } from '@playwright/test'

const PHOTO = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jP1sAAAAASUVORK5CYII=', 'base64')
const ANALYSIS = {
  faceShape: '椭圆偏长', facialLine: '清晰', visualContrast: '中等', hairFeatures: '短发',
  bodyProportions: '', fitSuggestions: ['圆领', '直筒'], styleTags: ['简洁通勤'],
  tryStyleTags: [], colorSuggestions: ['米白', '深蓝'], itemSuggestions: ['圆领上衣'], reasonSummary: '结合已确认特征参考领口与配色。'
}

function captureRuntimeErrors(page) {
  const errors = []
  page.on('pageerror', (error) => errors.push(error.message))
  page.on('console', (message) => {
    if (message.text().includes('[Vue warn]')) errors.push(message.text())
  })
  return errors
}

async function fixture(page, { failAnalysis = false, failModelPreference = false } = {}) {
  let stored = {
    displayName: '链路测试', gender: 'FEMALE', heightCm: null, weightKg: null, photoUrl: null,
    stylePreferences: ['通勤'], colorPreferences: ['米白'], occasions: ['通勤'],
    styleTags: ['通勤'], tryStyleTags: [], colorSuggestions: ['米白'], itemSuggestions: [],
    reasonSummary: '已保存偏好，可以分析或手动填写。', analysis: null, analysisSource: null, stale: false,
    usePersonalPhotoForOutfit: false
  }
  const calls = []
  await page.route('**/api/**', async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname
    const method = request.method()
    calls.push({ path, method, body: method === 'POST' && !path.endsWith('/photo') ? request.postDataJSON() : null })
    const send = (data, status = 200, message = 'ok') => route.fulfill({
      status, contentType: 'application/json', body: JSON.stringify({ code: status < 400 ? 0 : status, data, message })
    })
    if (path.endsWith('/auth/csrf')) return send({ token: 'fixture-csrf', headerName: 'X-XSRF-TOKEN' })
    if (path.endsWith('/auth/me')) return send({ username: 'profile-e2e', authorities: ['ROLE_USER'] })
    if (path === '/api/v1/trends') return send({ items: [], styles: [], sources: [], demoMode: false })
    if (path.startsWith('/api/v1/weather/')) return send({
      city: '长沙', temperatureC: 24, apparentTemperatureC: 24, weatherCode: 1, source: 'fixture', observedAt: new Date().toISOString()
    })
    if (path === '/api/v1/me/wardrobe') return send([])
    if (path === '/api/v1/me/recommendations') return send({ content: [], totalElements: 0, page: 0, hasNext: false })
    if (path === '/api/v1/me/style-profile') return send(stored)
    if (path.endsWith('/style-profile/refresh')) {
      stored = { ...stored, ...request.postDataJSON(), generatedAt: new Date().toISOString() }
      return send(stored)
    }
    if (path.endsWith('/style-profile/photo') && method === 'POST') {
      expect(request.headers()['content-type']).toContain('multipart/form-data')
      stored = { ...stored, photoUrl: '/api/v1/me/style-profile/photo?v=' + Date.now(), usePersonalPhotoForOutfit: false }
      return send(stored)
    }
    if (path.endsWith('/style-profile/photo') && method === 'GET') {
      return route.fulfill({ status: 200, contentType: 'image/png', body: PHOTO })
    }
    if (path.endsWith('/style-profile/analyze')) {
      expect(request.postDataJSON()).toEqual({ allowAiAnalysis: true })
      if (failAnalysis) return send(null, 503, '视觉模型未启用，请手动填写')
      stored = { ...stored, analysis: structuredClone(ANALYSIS), analysisSource: 'MODEL', analysisModelName: 'fixture-vision',
        analysisUpdatedAt: new Date().toISOString(), styleTags: ANALYSIS.styleTags, colorSuggestions: ANALYSIS.colorSuggestions,
        itemSuggestions: ANALYSIS.itemSuggestions, reasonSummary: ANALYSIS.reasonSummary, stale: false }
      return send(stored)
    }
    if (path.endsWith('/style-profile/outfit-model')) {
      if (failModelPreference) return send(null, 503, '人物设置暂时无法保存，请重试')
      stored = { ...stored, usePersonalPhotoForOutfit: request.postDataJSON().usePersonalPhotoForOutfit }
      return send(stored)
    }
    if (path.endsWith('/style-profile/analysis')) {
      const analysis = request.postDataJSON()
      stored = { ...stored, analysis, analysisSource: 'MANUAL', analysisModelName: null,
        analysisUpdatedAt: new Date().toISOString(), stale: false,
        styleTags: analysis.styleTags?.length ? analysis.styleTags : stored.stylePreferences,
        colorSuggestions: analysis.colorSuggestions?.length ? analysis.colorSuggestions : stored.colorPreferences,
        itemSuggestions: analysis.itemSuggestions || [], reasonSummary: analysis.reasonSummary }
      return send(stored)
    }
    return send(null, 404, 'Unexpected fixture API: ' + path)
  })
  return { calls, current: () => stored }
}

async function enterPhotoStep(page) {
  await page.goto('/#profile')
  await expect(page.getByRole('heading', { name: '形象分析结果' })).toBeVisible()
  await page.getByRole('button', { name: '编辑形象资料', exact: true }).click()
  await page.getByRole('spinbutton', { name: '身高', exact: true }).fill('172')
  await page.getByRole('spinbutton', { name: '体重', exact: true }).fill('60')
  await page.getByRole('combobox', { name: '模特性别', exact: true }).selectOption('FEMALE')
  await page.getByRole('button', { name: '下一步：上传照片' }).click()
  await page.getByLabel('个人照片', { exact: true }).setInputFiles({ name: 'portrait.png', mimeType: 'image/png', buffer: PHOTO })
}

test('personal photo analysis and corrected fields persist across page reloads', async ({ page }) => {
  const errors = captureRuntimeErrors(page)
  const api = await fixture(page)
  await page.addInitScript(() => localStorage.setItem('fashion.profile.setup.v1', JSON.stringify({
    height: '199', weight: '99', photoUrl: '/assets/profile-portrait.png', analysisComplete: true
  })))
  await enterPhotoStep(page)
  await expect(page.getByRole('radio', { name: /使用默认模特/ })).toBeChecked()
  expect(api.current().heightCm).toBe(172)
  expect(api.current().weightKg).toBe(60)
  await page.getByRole('button', { name: '开始 AI 分析', exact: true }).click()
  await expect(page.getByText('请授权本次 AI 分析，或选择手动填写。')).toBeVisible()
  expect(api.calls.filter((call) => call.path.endsWith('/analyze'))).toHaveLength(0)
  await page.getByRole('checkbox', { name: /同意将本次个人照片/ }).check()
  await page.getByRole('button', { name: '开始 AI 分析', exact: true }).click()
  await expect(page.getByText('AI 视觉分析 · fixture-vision').first()).toBeVisible()
  expect(api.current().usePersonalPhotoForOutfit).toBe(false)
  const face = page.locator('.analysis-row').filter({ hasText: '脸型' })
  await expect(face.locator('strong')).toHaveText('椭圆偏长')
  await face.getByRole('button', { name: '编辑', exact: true }).click()
  await page.getByRole('textbox', { name: '编辑脸型', exact: true }).fill('方脸')
  await face.getByRole('button', { name: '保存', exact: true }).click()
  await expect(face.locator('strong')).toHaveText('方脸')
  expect(api.current().analysis.faceShape).toBe('方脸')
  expect(api.current().analysisSource).toBe('MANUAL')
  expect(api.current().analysis.fitSuggestions).toEqual([])
  const fit = page.locator('.analysis-row').filter({ hasText: '剪裁建议' })
  await fit.getByRole('button', { name: '编辑', exact: true }).click()
  await page.getByRole('textbox', { name: '编辑剪裁建议', exact: true }).fill('V领、直筒')
  await fit.getByRole('button', { name: '保存', exact: true }).click()
  await expect(fit.locator('strong')).toHaveText('V领、直筒')
  expect(api.current().analysis.fitSuggestions).toEqual(['V领', '直筒'])
  await page.reload()
  await expect(page.locator('.analysis-row').filter({ hasText: '脸型' }).locator('strong')).toHaveText('方脸')
  await expect(page.getByText('手动填写或修正 · 后续推荐将参考这些资料').first()).toBeVisible()
  await page.getByRole('button', { name: '关闭分析提示' }).click()
  await expect(page.locator('.timeline-notice')).toHaveClass(/hidden/)
  await page.getByRole('button', { name: '编辑形象资料', exact: true }).click()
  await page.getByRole('spinbutton', { name: '身高', exact: true }).fill('199')
  await page.getByRole('button', { name: '关闭资料编辑' }).click()
  await expect(page.locator('.fact-height strong')).toHaveText('172 cm')
  expect(api.current().heightCm).toBe(172)
  expect(errors).toEqual([])
  await page.screenshot({ path: test.info().outputPath('personal-profile.png'), fullPage: true })
})

test('failed AI analysis keeps inputs and supports manual completion on a narrow screen', async ({ page }) => {
  const errors = captureRuntimeErrors(page)
  await page.setViewportSize({ width: 390, height: 844 })
  const api = await fixture(page, { failAnalysis: true })
  await enterPhotoStep(page)
  await page.getByRole('checkbox', { name: /同意将本次个人照片/ }).check()
  await page.getByRole('button', { name: '开始 AI 分析', exact: true }).click()
  await expect(page.getByText('视觉模型未启用，请手动填写').last()).toBeVisible()
  expect(api.current().photoUrl).toBeTruthy()
  expect(api.current().analysis).toBeNull()
  await page.getByRole('button', { name: '保存资料，手动填写', exact: true }).click()
  await page.getByRole('textbox', { name: '编辑脸型', exact: true }).fill('圆脸')
  await page.locator('.analysis-row').filter({ hasText: '脸型' }).getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.getByText('手动填写或修正 · 后续推荐将参考这些资料').first()).toBeVisible()
  expect(api.current().analysis.faceShape).toBe('圆脸')
  await page.reload()
  await expect(page.locator('.analysis-row').filter({ hasText: '脸型' }).locator('strong')).toHaveText('圆脸')
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy()
  expect(errors).toEqual([])
  await page.screenshot({ path: test.info().outputPath('personal-profile-mobile.png'), fullPage: true })
})

test('personal outfit identity is optional, persists and can be changed without rerunning analysis', async ({ page }) => {
  const errors = captureRuntimeErrors(page)
  const api = await fixture(page)
  await enterPhotoStep(page)
  await expect(page.getByText('是否使用上传的人物形象，生成每日穿搭效果图？')).toBeVisible()
  await page.getByRole('radio', { name: /使用我的照片/ }).check()
  await page.getByRole('checkbox', { name: /同意将本次个人照片/ }).check()
  await page.getByRole('button', { name: '开始 AI 分析', exact: true }).click()
  await expect(page.getByText('AI 视觉分析 · fixture-vision').first()).toBeVisible()
  expect(api.current().usePersonalPhotoForOutfit).toBe(true)
  await expect(page.locator('.outfit-model-summary strong')).toHaveText('我的照片')
  await page.reload()
  await expect(page.locator('.outfit-model-summary strong')).toHaveText('我的照片')
  await page.getByRole('button', { name: '设置人物形象', exact: true }).click()
  await expect(page.getByRole('radio', { name: /使用我的照片/ })).toBeChecked()
  await page.getByRole('radio', { name: /使用默认模特/ }).check()
  await page.getByRole('button', { name: '保存人物设置', exact: true }).click()
  await expect(page.locator('.outfit-model-summary strong')).toHaveText('默认模特')
  expect(api.calls.filter(call => call.path.endsWith('/analyze'))).toHaveLength(1)
  expect(api.current().analysis.faceShape).toBe('椭圆偏长')
  expect(errors).toEqual([])
})

test('changing the uploaded photo resets the identity choice and manual setup saves a fresh opt-in', async ({ page }) => {
  const api = await fixture(page)
  await page.setViewportSize({ width: 390, height: 844 })
  await enterPhotoStep(page)
  await page.getByRole('radio', { name: /使用我的照片/ }).check()
  await page.getByRole('button', { name: '保存人物设置', exact: true }).click()
  await expect(page.locator('.outfit-model-summary strong')).toHaveText('我的照片')
  await page.getByRole('button', { name: '设置人物形象', exact: true }).click()
  await page.getByLabel('个人照片', { exact: true }).setInputFiles({ name: 'new-portrait.png', mimeType: 'image/png', buffer: PHOTO })
  await expect(page.getByRole('radio', { name: /使用默认模特/ })).toBeChecked()
  await page.getByRole('radio', { name: /使用我的照片/ }).check()
  await page.getByRole('button', { name: '保存资料，手动填写', exact: true }).click()
  await expect(page.getByRole('textbox', { name: '编辑脸型' })).toBeVisible()
  expect(api.current().usePersonalPhotoForOutfit).toBe(true)
  expect(api.calls.filter(call => call.path.endsWith('/analyze'))).toHaveLength(0)
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
})

test('failed identity preference save does not dispatch an AI analysis request', async ({ page }) => {
  const api = await fixture(page, { failModelPreference: true })
  await enterPhotoStep(page)
  await page.getByRole('radio', { name: /使用我的照片/ }).check()
  await page.getByRole('checkbox', { name: /同意将本次个人照片/ }).check()
  await page.getByRole('button', { name: '开始 AI 分析', exact: true }).click()
  await expect(page.getByText('人物设置暂时无法保存，请重试').last()).toBeVisible()
  expect(api.calls.filter(call => call.path.endsWith('/analyze'))).toHaveLength(0)
  expect(api.current().usePersonalPhotoForOutfit).toBe(false)
})
