import { expect, test, request } from '@playwright/test'
import { formatRecommendationTitle } from '../src/utils/recommendationReason.js'
import { execFileSync } from 'node:child_process'
import { readFile, writeFile, mkdir } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

// Opt-in: this suite uses real PostgreSQL, MinIO, weather and paid model APIs.
const live = process.env.E2E_LIVE_PROFILE === 'true'
const project = process.env.E2E_LIVE_PROJECT || 'fashion-profile-live-local'
const runId = (process.env.E2E_LIVE_RUN_ID || Date.now().toString(36)).replace(/[^a-z0-9]/g, '').slice(0, 12)
const root = fileURLToPath(new URL('../../', import.meta.url))
const reportDir = path.join(root, 'output/live-profile/results', runId)
const portrait = path.join(root, 'output/live-profile/fixtures/fictional-avatar.png')
const password = 'live-profile-test-2026'
const adminName = 'live_admin_' + runId
let admin
let modelDefaults
let evidence

async function csrf(api) {
  const response = await api.get('/api/v1/auth/csrf')
  expect(response.status()).toBe(200)
  const body = await response.json()
  return { [body.data.headerName]: body.data.token }
}

async function registerApi(api, username) {
  const response = await api.post('/api/v1/auth/register', {
    headers: await csrf(api), data: { username, password }
  })
  expect([201, 409]).toContain(response.status())
}

async function loginApi(api, username) {
  const response = await api.post('/api/v1/auth/login', {
    headers: await csrf(api), form: { username, password }
  })
  expect(response.status()).toBe(200)
}

function database(sql) {
  const docker = process.env.E2E_DOCKER_PATH || 'docker'
  const container = project + '-postgres-1'
  const owner = execFileSync(docker, ['inspect', '--format', '{{index .Config.Labels "com.docker.compose.project"}}', container], { encoding: 'utf8' }).trim()
  if (owner !== project || !project.startsWith('fashion-profile-live-')) throw new Error('Refusing to access a database outside the dedicated live test project')
  return execFileSync(docker, ['exec', '-i', container, 'sh', '-c', 'exec psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -At'], {
    input: sql, encoding: 'utf8'
  }).trim()
}

function verifyModelPrototypes() {
  const docker = process.env.E2E_DOCKER_PATH || 'docker'
  const container = project + '-backend-1'
  const owner = execFileSync(docker, ['inspect', '--format', '{{index .Config.Labels "com.docker.compose.project"}}', container], { encoding: 'utf8' }).trim()
  if (owner !== project || !project.startsWith('fashion-profile-live-')) throw new Error('Refusing to inspect a backend outside the dedicated live test project')
  for (const gender of ['MALE', 'FEMALE']) {
    const reference = execFileSync(docker, ['exec', container, 'printenv', 'BAILIAN_IMAGE_PROTOTYPE_' + gender], { encoding: 'utf8' }).trim()
    expect(reference, 'Outfit generation must use the photographic model, not the cartoon profile fixture').toBe(
      'classpath:reference_photo/model-' + gender.toLowerCase() + '.png'
    )
  }
}

async function override(capability, enabled) {
  const config = modelDefaults.find((item) => item.capability === capability)
  const response = await admin.put('/api/v1/admin/ai-models/' + capability, {
    headers: await csrf(admin),
    data: { provider: config.provider, model: config.model, enabled, apiKey: '', clearApiKey: false }
  })
  expect(response.status()).toBe(200)
}

async function resetModels() {
  if (!admin) return
  for (const capability of ['WARDROBE_RECOGNITION', 'OUTFIT_RECOMMENDATION', 'DAILY_IMAGE_GENERATION']) {
    const response = await admin.delete('/api/v1/admin/ai-models/' + capability, { headers: await csrf(admin) })
    expect(response.status()).toBe(200)
  }
}

async function registerUi(page, username) {
  await page.goto('/')
  await page.getByRole('button', { name: '直接登录', exact: true }).click()
  await page.getByRole('tab', { name: '注册', exact: true }).click()
  await page.locator('input[name="username"]').fill(username)
  await page.locator('input[name="password"]').fill(password)
  await page.locator('input[name="confirmPassword"]').fill(password)
  await page.getByRole('button', { name: '注册账户', exact: true }).click()
  await expect(page.getByRole('button', { name: '退出登录' })).toBeVisible()
  evidence.username = username
}

async function saveBasicsAndSelectPhoto(page) {
  await page.goto('/#profile')
  await expect(page.getByRole('heading', { name: '形象分析结果', exact: true })).toBeVisible()
  await expect(page.locator('.fact-height strong')).toHaveText('-- cm')
  await page.getByRole('button', { name: '编辑形象资料', exact: true }).click()
  await page.getByRole('spinbutton', { name: '身高', exact: true }).fill('172')
  await page.getByRole('spinbutton', { name: '体重', exact: true }).fill('60')
  await page.getByRole('combobox', { name: '模特性别', exact: true }).selectOption('FEMALE')
  await page.getByRole('button', { name: '下一步：上传照片' }).click()
  await page.getByLabel('个人照片', { exact: true }).setInputFiles(portrait)
}

async function editField(page, label, value) {
  const row = page.locator('.analysis-row').filter({ has: page.locator('.analysis-row-main > span', { hasText: label }) })
  await row.getByRole('button', { name: '编辑', exact: true }).click()
  await page.getByRole('textbox', { name: '编辑' + label, exact: true }).fill(value)
  await row.getByRole('button', { name: '保存', exact: true }).click()
  await expect(row.locator('strong')).toHaveText(value)
}

async function post(api, route, data, timeout = 90000) {
  const response = await api.post(route, { headers: await csrf(api), data, timeout })
  const body = await response.json()
  expect(response.status(), body.message).toBe(200)
  return body.data
}

async function uploadGarments(api) {
  const fixtures = [
    ['米白翻领衬衫', '上装', '米白', 'wardrobe-01-warm-oxford-shirt.png'],
    ['深蓝牛仔裤', '下装', '深蓝', 'wardrobe-03-graphite-jeans.png'],
    ['黑色系带皮鞋', '鞋履', '黑色', 'wardrobe-07-black-loafers.png']
  ]
  const items = []
  for (const [name, category, color, file] of fixtures) {
    const response = await api.post('/api/v1/me/wardrobe/upload', {
      headers: await csrf(api), multipart: {
        image: { name: file, mimeType: 'image/png', buffer: await readFile(path.join(root, 'frontend/public/assets/wardrobe', file)) },
        name, category, color, style: '通勤', allowAiRecognition: 'false'
      }
    })
    expect(response.status()).toBe(200)
    items.push((await response.json()).data)
  }
  return items
}

test.describe('live personal profile integration', () => {
  test.skip(!live, 'Set E2E_LIVE_PROFILE=true only for an isolated real-provider integration run')
  test.setTimeout(300000)

  test.beforeAll(async ({ baseURL, browser }) => {
    // Fail before paid calls if profile-analysis fixtures have replaced the outfit models.
    verifyModelPrototypes()
    // This code-drawn avatar is only the fictional personal profile photo used for analysis.
    await mkdir(path.dirname(portrait), { recursive: true })
    const fixturePage = await browser.newPage({ viewport: { width: 768, height: 1024 } })
    await fixturePage.setContent(await readFile(path.join(root, 'frontend/e2e/fixtures/fictional-profile.svg'), 'utf8'))
    await fixturePage.screenshot({ path: portrait })
    await fixturePage.close()
    admin = await request.newContext({ baseURL })
    await registerApi(admin, adminName)
    database("INSERT INTO app_authorities(username, authority) VALUES ('" + adminName + "','ROLE_ADMIN') ON CONFLICT DO NOTHING;")
    await loginApi(admin, adminName)
    await resetModels()
    const response = await admin.get('/api/v1/admin/ai-models')
    expect(response.status()).toBe(200)
    modelDefaults = (await response.json()).data
    expect(database('SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1;')).toBe('10')
    await mkdir(reportDir, { recursive: true })
    // Fail before spending model quota if both live weather sources are unreachable.
    const weatherResponse = await admin.get('/api/v1/weather/current?city=' + encodeURIComponent('长沙'), { timeout: 90000 })
    const weatherBody = await weatherResponse.json()
    await writeFile(path.join(reportDir, 'weather-preflight.json'), JSON.stringify({
      status: weatherResponse.status(), message: weatherBody.message, weather: weatherBody.data
    }, null, 2))
    expect(weatherResponse.status(), weatherBody.message).toBe(200)
    expect(['wttr.in', 'open-meteo']).toContain(weatherBody.data.source)
  })

  test.beforeEach(async ({ page }) => {
    await resetModels()
    evidence = { startedAt: new Date().toISOString(), mockedApis: false, checks: [], runtimeErrors: [] }
    page.on('pageerror', (error) => evidence.runtimeErrors.push(error.message))
    page.on('console', (message) => {
      if (message.text().includes('[Vue warn]')) evidence.runtimeErrors.push(message.text().split('\n')[0])
    })
  })

  test.afterEach(async ({ page }, info) => {
    evidence.status = info.status
    evidence.finishedAt = new Date().toISOString()
    await writeFile(path.join(reportDir, info.title.startsWith('real AI') ? 'ai-chain.json' : 'manual-chain.json'), JSON.stringify(evidence, null, 2))
    await page.screenshot({ path: path.join(reportDir, info.title.startsWith('real AI') ? 'ai-chain.png' : 'manual-chain.png'), fullPage: true })
    await resetModels()
  })

  test.afterAll(async () => { await admin?.dispose() })

  test('real AI analysis, correction, recommendation, saving and feedback', async ({ page, context }) => {
    await registerUi(page, 'live_ai_' + runId)
    await saveBasicsAndSelectPhoto(page)
    await page.getByRole('button', { name: '开始 AI 分析', exact: true }).click()
    await expect(page.getByText('请授权本次 AI 分析，或选择手动填写。')).toBeVisible()
    evidence.checks.push('Explicit consent required before vision dispatch')
    await page.getByRole('checkbox', { name: /同意将本次个人照片/ }).check()
    const pendingAnalysis = page.waitForResponse((response) => response.url().includes('/style-profile/analyze'), { timeout: 75000 })
    await page.getByRole('button', { name: '开始 AI 分析', exact: true }).click()
    const analysisResponse = await pendingAnalysis
    const analysisBody = await analysisResponse.json()
    evidence.vision = { httpStatus: analysisResponse.status(), message: analysisBody.message, model: analysisBody.data?.analysisModelName }
    expect(analysisResponse.status(), analysisBody.message).toBe(200)
    const profile = analysisBody.data
    expect(profile.analysisSource).toBe('MODEL')
    expect(profile.analysisUpdatedAt).toBeTruthy()
    expect(profile.analysis).toBeTruthy()
    expect(profile.analysisModelName).toBeTruthy()
    expect(profile).not.toHaveProperty('photoObjectKey')
    evidence.vision.analysis = profile.analysis
    const savedPhoto = await context.request.get(profile.photoUrl)
    expect(savedPhoto.status()).toBe(200)
    expect(savedPhoto.headers()['cache-control']).toBe('no-store')
    expect(await savedPhoto.body()).toEqual(await readFile(portrait))
    evidence.checks.push('Real MinIO upload and authenticated photo download preserve identical bytes')
    await editField(page, '脸型', '人工确认的方脸')
    await editField(page, '剪裁建议', '翻领、直筒')
    await page.reload()
    await expect(page.getByText('人工确认的方脸', { exact: true })).toBeVisible()
    await expect(page.locator('.fact-height strong')).toHaveText('172 cm')
    evidence.checks.push('Manual correction and measurements survive browser reload')
    const garments = await uploadGarments(context.request)
    const withVisual = process.env.E2E_LIVE_VISUAL === 'true'
    if (!withVisual) await override('DAILY_IMAGE_GENERATION', false)
    await page.goto('/#recommend')
    await page.reload()
    await expect(page.getByRole('heading', { name: '衣橱已就绪，创建穿搭方案。' })).toBeVisible()
    await page.getByRole('button', { name: '打开知己 AI 助手', exact: true }).click()
    const assistant = page.getByRole('dialog', { name: '知己 AI 助手' })
    await assistant.locator('#assistant-draft').fill('长沙通勤。请在理由中写明参考个人档案中的身高数值、脸型和剪裁建议；未知信息留空。')
    const pendingRecommendation = page.waitForResponse((response) => new URL(response.url()).pathname === '/api/v1/recommendations'
      && response.request().method() === 'POST', { timeout: 90000 })
    const pendingVisual = withVisual ? page.waitForResponse((response) => /\/recommendations\/\d+\/visual$/.test(new URL(response.url()).pathname),
      { timeout: 180000 }) : null
    await assistant.getByRole('button', { name: '生成穿搭推荐', exact: true }).click()
    const recommendationResponse = await pendingRecommendation
    const recommendationBody = await recommendationResponse.json()
    expect(recommendationResponse.status(), recommendationBody.message).toBe(200)
    const recommendation = recommendationBody.data
    evidence.recommendationRequest = recommendationResponse.request().postDataJSON()
    expect(evidence.recommendationRequest.city).toBe('长沙')
    expect(evidence.recommendationRequest.occasion).toBe('通勤')
    expect(evidence.recommendationRequest.styleHint).not.toMatch(/172|方脸|翻领|直筒/)
    await expect(assistant.locator('.assistant-result-card')).toContainText(formatRecommendationTitle(recommendation))
    await assistant.getByRole('button', { name: '关闭知己助手' }).click()
    evidence.checks.push('The product assistant submits city and occasion through the real frontend; profile values come from server-side storage')
    evidence.recommendation = { id: recommendation.id, engine: recommendation.engine, audit: recommendation.generationAudit,
      weather: recommendation.weather, reason: recommendation.reason, itemIds: recommendation.items.map((item) => item.id) }
    expect(recommendation.engine).toBe('llm')
    expect(recommendation.generationAudit.promptVersion).toBe('recommendation-v5-professional-copy')
    expect(recommendation.generationAudit.providerCallId).toBeTruthy()
    expect(recommendation.generationAudit.totalTokens).toBeGreaterThan(0)
    expect(recommendation.reason.length).toBeLessThanOrEqual(180)
    expect(recommendation.reason).not.toMatch(/身高|体重|方脸|172|ID\s*[:：]/i)
    expect(recommendation.reason).toMatch(/翻领|直筒/)
    expect(recommendation.weather.source).not.toBe('configured-demo')
    expect(recommendation.items.every((item) => garments.some((garment) => garment.id === item.id))).toBe(true)
    evidence.checks.push('Real recommendation model uses corrected profile and live weather, selects only owned garments')
    if (withVisual) {
      const visualResponse = await pendingVisual
      const visual = (await visualResponse.json()).data
      evidence.visual = { httpStatus: visualResponse.status(), status: visual.status, model: visual.model,
        modelGender: visual.modelGender, itemCount: visual.itemCount, message: visual.message }
      expect(visualResponse.status()).toBe(200)
      expect(visual.status, visual.message).toBe('SUCCEEDED')
      expect(visual.modelGender).toBe('FEMALE')
      expect(visual.itemCount).toBeGreaterThanOrEqual(2)
      await expect(page.locator('.weather-reading > strong')).toHaveText(recommendation.weather.temperatureC.toFixed(0) + '°')
      await expect(page.locator('.weather-reading > div > span')).toHaveText(recommendation.city)
      await expect(page.locator('.ootd-board-heading > strong')).toContainText('· ' + recommendation.occasion)
      const image = page.locator('.ootd-board-model-visual img')
      await expect(image).toBeVisible()
      await expect.poll(() => image.evaluate((element) => element.complete && element.naturalWidth > 0), { timeout: 45000 }).toBe(true)
      await page.screenshot({ path: path.join(reportDir, 'real-outfit.png'), fullPage: true })
      evidence.checks.push('Real image generation uses the fictional prototype and owned garments, and renders in the product UI')
      evidence.checks.push('Displayed weather, city and occasion match the saved recommendation')
    }
    await page.goto('/#history')
    await expect(page.getByRole('button', { name: '收藏搭配', exact: true })).toBeVisible()
    await page.getByRole('button', { name: '收藏搭配', exact: true }).click()
    await expect(page.locator('.history-card .save-button')).toHaveText('已收藏')
    await page.getByRole('button', { name: '为搭配 ' + recommendation.id + ' 评 5 星', exact: true }).click()
    await expect(page.getByText('已评 5 星', { exact: true })).toBeVisible()
    await page.reload()
    const stored = (await (await context.request.get('/api/v1/recommendations/' + recommendation.id)).json()).data
    expect(stored.saved).toBe(true)
    expect(stored.feedback.rating).toBe(5)
    expect(database("SELECT analysis_source || '|' || height_cm || '|' || weight_kg FROM style_profiles WHERE user_id='" + evidence.username + "';")).toBe('MANUAL|172.0|60.0')
    evidence.checks.push('History saving, feedback and personal analysis persist in real PostgreSQL')
    expect(evidence.runtimeErrors).toEqual([])
  })

  test('disabled vision, manual completion, rule scoring, stale data and owner isolation', async ({ page, context, baseURL }) => {
    await override('WARDROBE_RECOGNITION', false)
    await override('OUTFIT_RECOMMENDATION', false)
    await page.setViewportSize({ width: 390, height: 844 })
    await registerUi(page, 'live_manual_' + runId)
    await saveBasicsAndSelectPhoto(page)
    await page.getByRole('checkbox', { name: /同意将本次个人照片/ }).check()
    const pendingAnalysis = page.waitForResponse((response) => response.url().includes('/style-profile/analyze'))
    await page.getByRole('button', { name: '开始 AI 分析', exact: true }).click()
    expect((await pendingAnalysis).status()).toBe(503)
    await expect(page.getByText(/当前可手动填写分析结果/).last()).toBeVisible()
    let profile = (await (await context.request.get('/api/v1/me/style-profile')).json()).data
    expect(profile.heightCm).toBe(172)
    expect(profile.photoUrl).toBeTruthy()
    expect(profile.analysis).toBeNull()
    evidence.checks.push('A disabled real vision service returns 503 and preserves measurements and uploaded photo')
    await page.getByRole('button', { name: '保存资料，手动填写', exact: true }).click()
    await page.getByRole('textbox', { name: '编辑脸型', exact: true }).fill('方脸')
    await page.locator('.analysis-row').filter({ hasText: '脸型' }).getByRole('button', { name: '保存', exact: true }).click()
    await editField(page, '剪裁建议', 'V领')
    await editField(page, '颜色建议', '米白')
    const blue = await post(context.request, '/api/v1/me/wardrobe', { name: '蓝色衬衫', category: '上装', color: '蓝色', style: '通勤' })
    const preferred = await post(context.request, '/api/v1/me/wardrobe', { name: '米白 V领上衣', category: '上装', color: '米白', style: '通勤' })
    await post(context.request, '/api/v1/me/wardrobe', { name: '黑色长裤', category: '下装', color: '黑色', style: '通勤' })
    const first = await post(context.request, '/api/v1/recommendations', { city: '长沙', occasion: '通勤' })
    expect(first.engine).toBe('development-rule-v1')
    expect(first.items.find((item) => item.category === '上装').id).toBe(preferred.id)
    evidence.checks.push('Rule fallback favors wardrobe metadata matching saved color and fit advice')
    profile = await post(context.request, '/api/v1/me/style-profile/refresh', {
      displayName: '联调测试', gender: 'FEMALE', heightCm: 180, weightKg: 60,
      stylePreferences: ['通勤'], colorPreferences: ['蓝色'], occasions: ['通勤']
    })
    expect(profile.stale).toBe(true)
    const stale = await post(context.request, '/api/v1/recommendations', { city: '长沙', occasion: '通勤' })
    expect(stale.items.find((item) => item.category === '上装').id).toBe(blue.id)
    expect(stale.reason).toContain('旧分析暂未用于')
    evidence.checks.push('Changed measurements and preferences invalidate old analysis and remove its effect on selection')
    const anonymous = await request.newContext({ baseURL })
    expect((await anonymous.get('/api/v1/me/style-profile/photo')).status()).toBe(401)
    await registerApi(anonymous, 'live_other_' + runId)
    await loginApi(anonymous, 'live_other_' + runId)
    expect((await anonymous.get('/api/v1/me/style-profile/photo?userId=' + evidence.username)).status()).toBe(404)
    expect((await anonymous.get('/api/v1/recommendations/' + first.id)).status()).toBe(404)
    expect((await context.request.post('/api/v1/me/style-profile/analysis', { data: { faceShape: '伪造请求' } })).status()).toBe(403)
    expect((await context.request.get('/api/v1/admin/ai-models')).status()).toBe(403)
    await anonymous.dispose()
    evidence.checks.push('Anonymous, other-owner, missing-CSRF and non-admin requests are rejected by the running server')
    await page.reload()
    await expect(page.getByText('资料已变更 · 待更新', { exact: true })).toBeVisible()
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
    expect(evidence.runtimeErrors).toEqual([])
    evidence.checks.push('Mobile UI survives reload without runtime warnings or horizontal overflow')
  })
})
