import { expect, test } from '@playwright/test'
import { outfitTechniques } from '../src/data/outfitTechniques.js'

// This suite uses the running Vite proxy, Spring service and an isolated PostgreSQL database.
// It intentionally does not intercept or replace API responses. Before each complete run, import
// five fresh pending integration fixtures and provision the isolated administrator outside the UI.
const enabled = process.env.RUN_LOCAL_INTEGRATION === 'true'
const fixtureIds = [
  'weibo:integration-1', 'douyin:integration-2', 'xiaohongshu:integration-3',
  'weibo:integration-4', 'xiaohongshu:integration-5'
]
const fixtureCategories = ['男装穿搭', '男装穿搭', '女装穿搭', '女装穿搭', '哥特风穿搭']
const fixtureImages = [
  '/assets/home-look-commute.webp', '/assets/home-look-casual.webp',
  '/assets/trend-analysis/sanmu-vest-a.jpg', '/assets/trend-analysis/sanmu-vest-b.jpg',
  '/assets/home-look-details.webp'
]
const fixtureSummary = '仅用于本地前后端联调的人工构造数据。图片为已有项目素材，与分类标签没有事实关联，不代表平台原帖或当天采集结果。'
const localHosts = new Set(['localhost', '127.0.0.1', '[::1]'])
const password = 'integration-user-2026'
const localPng = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=',
  'base64'
)

async function csrfHeaders(api) {
  const response = await api.get('/api/v1/auth/csrf')
  expect(response.status()).toBe(200)
  const body = await response.json()
  expect(body).toMatchObject({ code: 0, data: { headerName: expect.any(String), token: expect.any(String) } })
  return { [body.data.headerName]: body.data.token }
}

async function openLogin(page) {
  await page.goto('/#home')
  await page.getByRole('button', { name: '登录 / 注册', exact: true }).click()
  await page.getByRole('button', { name: '直接登录', exact: true }).click()
  await expect(page.getByRole('heading', { name: '登录知己', exact: true })).toBeVisible()
}

async function registerThroughUi(page, username) {
  await openLogin(page)
  await page.getByRole('tab', { name: '注册', exact: true }).click()
  await page.locator('input[name="username"]').fill(username)
  await page.locator('input[name="password"]').fill(password)
  await page.locator('input[name="confirmPassword"]').fill(password)
  const registration = postResponse(page, '/api/v1/auth/register')
  await page.getByRole('button', { name: '注册账户', exact: true }).click()
  expect((await registration).status()).toBe(201)
  await expect(page.getByRole('button', { name: '退出登录', exact: true })).toBeVisible()
}

function postResponse(page, pathname) {
  return page.waitForResponse(response => decodeURIComponent(new URL(response.url()).pathname) === pathname
    && response.request().method() === 'POST')
}

test.describe('real local frontend/backend integration', () => {
  test.skip(!enabled, 'Requires RUN_LOCAL_INTEGRATION=true and an isolated live integration stack.')

  test.beforeAll(async ({ playwright }) => {
    expect(process.env.E2E_BASE_URL, 'Set the running local frontend URL explicitly.').toBeTruthy()
    const base = new URL(process.env.E2E_BASE_URL)
    expect(localHosts.has(base.hostname), 'This mutating suite may only target a local isolated environment.').toBe(true)
    expect(process.env.INTEGRATION_ADMIN_USERNAME).toBeTruthy()
    expect(process.env.INTEGRATION_ADMIN_PASSWORD).toBeTruthy()

    // A loopback URL can later point at a different database. Fail before creating accounts or
    // writing data unless this exact isolated fixture set is present, pending or already approved.
    const preflight = await playwright.request.newContext({ baseURL: base.origin })
    try {
      const login = await preflight.post('/api/v1/auth/login', {
        headers: await csrfHeaders(preflight),
        form: { username: process.env.INTEGRATION_ADMIN_USERNAME, password: process.env.INTEGRATION_ADMIN_PASSWORD }
      })
      expect(login.status(), 'The dedicated integration administrator must already exist.').toBe(200)
      const authentication = await login.json()
      expect(authentication).toMatchObject({ code: 0, data: {
        username: process.env.INTEGRATION_ADMIN_USERNAME, authorities: expect.arrayContaining(['ROLE_ADMIN'])
      } })
      const [feedResponse, queueResponse] = await Promise.all([
        preflight.get('/api/v1/trends?period=day'),
        preflight.get('/api/v1/admin/trends/review-queue?limit=50')
      ])
      expect(feedResponse.status()).toBe(200)
      expect(queueResponse.status()).toBe(200)
      const feed = await feedResponse.json()
      const queue = await queueResponse.json()
      expect(feed.code).toBe(0)
      expect(queue.code).toBe(0)
      expect(feed.data.demoMode).toBe(false)
      const items = [...feed.data.items, ...queue.data.map(candidate => candidate.item)]
      expect([...new Set(items.map(item => item.id))].sort(), 'Refusing writes: the local service does not expose exactly the isolated integration fixtures.').toEqual([...fixtureIds].sort())
      for (const item of items) {
        const index = fixtureIds.indexOf(item.id)
        expect(item.title).toBe(`联调样例 ${index + 1} · ${fixtureCategories[index]}`)
        expect(item.evidence?.author).toBe('本地联调样例')
        expect(item.evidence?.mediaType).toBe('image')
        expect(item.summary).toBe(fixtureSummary)
        const source = new URL(item.sourceUrl)
        const image = new URL(item.imageUrl)
        for (const url of [source, image]) {
          expect(localHosts.has(url.hostname)).toBe(true)
          expect(url.origin).toBe(base.origin)
          expect(url.username || url.password).toBe('')
        }
        expect(source.pathname).toBe('/')
        expect(source.search).toBe(`?integration-fixture=${index + 1}`)
        expect(source.hash).toBe('')
        expect(image.pathname).toBe(fixtureImages[index])
        expect(image.search || image.hash).toBe('')
        expect(item.evidence.images).toEqual([image.href])
      }
    } finally {
      await preflight.dispose()
    }
  })

  test.beforeEach(async ({ context, page }) => {
    // Links remain inspectable, but no test can accidentally load a third-party post or image.
    // Local requests, including every /api request, continue to the actual running services.
    await context.route('**/*', route => {
      const url = new URL(route.request().url())
      return localHosts.has(url.hostname) ? route.continue() : route.abort()
    })
    await page.setViewportSize({ width: 1440, height: 1000 })
  })

  test('guest access, CSRF rejection and server-side validation use real responses', async ({ page, context }) => {
    const errors = []
    page.on('pageerror', error => errors.push(error.message))
    await page.goto('/#home')
    await expect(page.getByRole('button', { name: '登录 / 注册', exact: true })).toBeVisible()

    for (const path of ['/api/v1/auth/me', '/api/v1/me/wardrobe', '/api/v1/admin/trends/review-queue']) {
      const response = await context.request.get(path)
      expect(response.status(), path).toBe(401)
      expect(await response.json()).toMatchObject({ code: 401, data: null })
    }

    const withoutCsrf = await context.request.post('/api/v1/auth/register', {
      data: { username: '!', password: 'short' }
    })
    expect(withoutCsrf.status()).toBe(403)
    expect(await withoutCsrf.json()).toMatchObject({ code: 403, data: null })

    const headers = await csrfHeaders(context.request)
    const validated = await context.request.post('/api/v1/auth/register', {
      headers, data: { username: '!', password: 'short' }
    })
    expect(validated.status()).toBe(400)
    expect(await validated.json()).toMatchObject({ code: 400, data: null })
    expect(errors).toEqual([])
  })

  test('administrator reviews every real local fixture image before the daily gallery exposes 2/2/1', async ({ page, context }, testInfo) => {
    test.setTimeout(150_000)
    expect(process.env.INTEGRATION_ADMIN_USERNAME).toBeTruthy()
    expect(process.env.INTEGRATION_ADMIN_PASSWORD).toBeTruthy()
    const errors = []
    page.on('pageerror', error => errors.push(error.message))
    await openLogin(page)
    await page.locator('input[name="username"]').fill(process.env.INTEGRATION_ADMIN_USERNAME)
    await page.locator('input[name="password"]').fill(process.env.INTEGRATION_ADMIN_PASSWORD)
    const login = postResponse(page, '/api/v1/auth/login')
    await page.getByRole('button', { name: '登录知己', exact: true }).click()
    expect((await login).status()).toBe(200)
    await expect(page).toHaveURL(/#admin$/)

    const queueResponse = await context.request.get('/api/v1/admin/trends/review-queue?limit=50')
    expect(queueResponse.status()).toBe(200)
    const queueBody = await queueResponse.json()
    expect(queueBody.code).toBe(0)
    const pending = queueBody.data.filter(candidate => fixtureIds.includes(candidate.item.id))
    expect(pending.map(candidate => candidate.item.id).sort(), 'Run with five pending isolated fixtures; reset only these fixtures before rerunning.').toEqual([...fixtureIds].sort())
    expect(pending.every(candidate => candidate.item.title.includes('联调样例'))).toBe(true)

    const beforeResponse = await context.request.get('/api/v1/trends?period=day')
    expect(beforeResponse.status()).toBe(200)
    const before = await beforeResponse.json()
    expect(before.data.items.filter(item => fixtureIds.includes(item.id))).toEqual([])

    await page.getByRole('button', { name: /穿搭图片复核/ }).click()
    await expect(page.getByRole('heading', { name: '穿搭图片复核', exact: true })).toBeVisible()
    for (const candidate of pending) {
      const card = page.locator('.trend-review-card').filter({
        has: page.getByRole('heading', { name: candidate.item.title, exact: true })
      })
      await expect(card).toHaveCount(1)
      const imageUrls = [...new Set([
        candidate.item.imageUrl, ...(candidate.item.evidence?.images || []),
        candidate.item.evidence?.fullBodyImageUrl
      ].filter(value => typeof value === 'string' && value.trim()))]
      expect(imageUrls.length).toBeGreaterThan(0)
      for (const url of imageUrls) expect(localHosts.has(new URL(url).hostname)).toBe(true)
      const images = card.locator('.trend-review-images img')
      await expect(images).toHaveCount(imageUrls.length)
      await card.scrollIntoViewIfNeeded()
      await expect.poll(() => images.evaluateAll(nodes => nodes.every(image => image.complete && image.naturalWidth > 0))).toBe(true)
      const approve = card.getByRole('button', { name: '通过复核', exact: true })
      const confirmation = card.getByRole('checkbox')
      await expect(approve).toBeDisabled()
      const note = '仅用于本地联调的人工构造样例；已逐一检查全部本地配图与说明。'
      await card.getByLabel('复核说明', { exact: true }).fill(note)
      await expect(confirmation).toBeEnabled()
      await expect(approve).toBeDisabled()
      await confirmation.check()
      await expect(approve).toBeEnabled()
      const review = postResponse(page, `/api/v1/admin/trends/${candidate.item.id}/content-review`)
      await approve.click()
      const response = await review
      expect(response.status()).toBe(200)
      expect(response.request().postDataJSON()).toEqual({ approved: true, fingerprint: candidate.fingerprint, note })
      expect(response.request().headers()['x-xsrf-token']).toBeTruthy()
      expect(await response.json()).toMatchObject({ code: 0, data: { status: 'APPROVED' } })
      await expect(card).toHaveCount(0)
    }

    const feedResponse = await context.request.get('/api/v1/trends?period=day')
    expect(feedResponse.status()).toBe(200)
    const feed = (await feedResponse.json()).data
    expect(feed.demoMode).toBe(false)
    expect(feed.items.map(item => item.id).sort()).toEqual([...fixtureIds].sort())
    expect(feed.dailySelection).toMatchObject({
      target: 5, menswear: 2, womenswear: 2, niche: 1,
      missing: { menswear: 0, womenswear: 0, niche: 0 }
    })
    expect(feed.dailySelection.assignments).toHaveLength(5)

    await page.getByRole('button', { name: '返回用户端', exact: true }).click()
    await page.getByRole('button', { name: '穿搭鉴赏', exact: true }).click()
    await expect(page).toHaveURL(/#trend$/)
    await expect(page.locator('.trend-count')).toContainText('每日精选 5 / 5 套')
    await expect(page.getByLabel('每日精选收录情况')).toContainText('男装 2 / 2')
    await expect(page.getByLabel('每日精选收录情况')).toContainText('女装 2 / 2')
    await expect(page.getByLabel('每日精选收录情况')).toContainText('小众风格欣赏 1 / 1')
    const gallery = page.getByRole('region', { name: '穿搭鉴赏画廊', exact: true })
    await expect(gallery).toBeVisible()
    await expect(gallery).toHaveAttribute('aria-roledescription', '3D 画廊')
    const selected = feed.items.find(item => feed.dailySelection.assignments.find(assignment => assignment.id === item.id)?.category === 'niche')
    await gallery.getByRole('button', { name: new RegExp(`^查看第 \\d+ 张图片：${selected.title}$`) }).click()
    await expect(page.locator('#trend-breakdown-title')).toHaveText(selected.title)
    await expect(page.locator('.brief-daily-slot')).toContainText('小众')
    const open = page.getByRole('button', { name: '深入分析', exact: true })
    await open.click()
    const dialog = page.getByRole('dialog', { name: '深入分析', exact: true })
    await expect(dialog).toBeVisible()
    await expect(dialog).toContainText(selected.summary)
    // Constructed integration posts have no curated analysis: they must not acquire invented insight.
    await expect(dialog).toContainText('暂无这条穿搭的深入解读')
    await dialog.getByRole('button', { name: '关闭深入分析', exact: true }).click()
    await expect(dialog).toHaveCount(0)
    await expect(open).toBeFocused()
    expect(await page.evaluate(() => document.documentElement.style.overflow)).toBe('')

    const techniques = page.getByRole('region', { name: '穿搭技巧', exact: true })
    await expect(techniques).toBeVisible()
    for (const technique of outfitTechniques.filter(item => item.sources?.length)) {
      await techniques.locator('.technique-choice').filter({ has: page.getByText(technique.title, { exact: true }) }).click()
      const detail = techniques.locator('article.technique-detail')
      await expect(detail.getByRole('heading', { name: '原帖要点（转述）', exact: true })).toBeVisible()
      await expect(detail.locator('.technique-synthesis')).toContainText(technique.synthesisNote)
      await expect(detail.locator('.technique-source-list li')).toHaveCount(technique.sources.length)
      for (const source of technique.sources) {
        await expect(detail.getByRole('link', { name: source.title, exact: true })).toHaveAttribute('href', new URL(source.url).href)
      }
    }
    await page.screenshot({ path: '../output/integration-live/appreciation-techniques.png', fullPage: true, animations: 'disabled' })
    await techniques.getByRole('button', { name: '去我的衣橱练习', exact: true }).click()
    await expect(page).toHaveURL(/#wardrobe$/)
    await expect(page.getByRole('heading', { name: '我的衣橱', exact: true, level: 1 })).toBeVisible()
    expect(errors).toEqual([])
    await testInfo.attach('daily-selection.json', {
      body: Buffer.from(JSON.stringify({ fixture: 'constructed-local-integration-only', dailySelection: feed.dailySelection }, null, 2)),
      contentType: 'application/json'
    })
  })

  test('a fresh ordinary account persists three manual garments, a rule recommendation, saved feedback and logout', async ({ page, context }, testInfo) => {
    test.setTimeout(150_000)
    const username = `integration_${Date.now().toString(36)}_${testInfo.workerIndex}`.slice(0, 32)
    await registerThroughUi(page, username)
    const current = await context.request.get('/api/v1/auth/me')
    expect(current.status()).toBe(200)
    expect((await current.json()).data.username).toBe(username)
    const noAdmin = await context.request.get('/api/v1/admin/trends/review-queue')
    expect(noAdmin.status()).toBe(403)
    expect(await noAdmin.json()).toMatchObject({ code: 403, data: null })

    await page.getByRole('button', { name: '我的衣橱', exact: true }).click()
    const garments = [
      { name: '联调 白色棉衬衫', category: '上装', color: '白色', style: '通勤' },
      { name: '联调 深灰直筒裤', category: '下装', color: '深灰', style: '通勤' },
      { name: '联调 黑色平底鞋', category: '鞋履', color: '黑色', style: '通勤' }
    ]
    const garmentIds = []
    for (const garment of garments) {
      await page.locator('.wardrobe-view > .page-header').getByRole('button', { name: '添加衣物', exact: true }).click()
      const dialog = page.getByRole('dialog', { name: '添加衣物', exact: true })
      await dialog.getByLabel('衣物名称').fill(garment.name)
      await dialog.getByLabel('类别').selectOption(garment.category)
      await dialog.getByLabel('颜色', { exact: true }).fill(garment.color)
      await dialog.getByLabel(/^风格/).fill(garment.style)
      const created = postResponse(page, '/api/v1/me/wardrobe')
      await dialog.getByRole('button', { name: '添加到衣橱', exact: true }).click()
      const response = await created
      expect(response.status()).toBe(200)
      const body = await response.json()
      expect(body.code).toBe(0)
      expect(body.data).toMatchObject(garment)
      garmentIds.push(body.data.id)
      await expect(page.getByRole('heading', { name: garment.name, exact: true })).toBeVisible()
    }
    await page.reload()
    for (const garment of garments) await expect(page.getByRole('heading', { name: garment.name, exact: true })).toBeVisible()

    await page.getByRole('button', { name: '穿搭推荐', exact: true }).click()
    await page.getByRole('button', { name: '打开知己 AI 助手', exact: true }).click()
    const assistant = page.getByRole('dialog')
    await assistant.getByRole('textbox', { name: '穿搭需求', exact: true }).fill('杭州本地联调，室内日常通勤，正式简洁。')
    const recommended = postResponse(page, '/api/v1/recommendations')
    await assistant.getByRole('button', { name: '生成穿搭推荐', exact: true }).click()
    const recommendationResponse = await recommended
    expect(recommendationResponse.status()).toBe(200)
    const recommendation = (await recommendationResponse.json()).data
    expect(recommendation.engine).toBe('development-rule-v1')
    expect(recommendation.generationAudit).toMatchObject({ fallbackReason: 'llm-disabled', modelName: null, providerCallId: null })
    expect(recommendation.items.length).toBeGreaterThanOrEqual(2)
    expect(recommendation.items.every(item => garmentIds.includes(item.id))).toBe(true)
    await assistant.getByRole('button', { name: '关闭知己助手', exact: true }).click()
    await expect(page.getByText(`搭配 #${recommendation.id}`, { exact: true })).toBeVisible()

    const saved = postResponse(page, `/api/v1/me/recommendations/${recommendation.id}/save`)
    await page.getByRole('button', { name: '收藏搭配', exact: true }).click()
    expect((await saved).status()).toBe(200)
    await expect(page.getByRole('button', { name: '已收藏', exact: true })).toBeDisabled()
    const feedback = postResponse(page, `/api/v1/me/recommendations/${recommendation.id}/feedback`)
    await page.getByRole('button', { name: '5 星评分', exact: true }).click()
    const feedbackResponse = await feedback
    expect(feedbackResponse.status()).toBe(200)
    expect(await feedbackResponse.json()).toMatchObject({ code: 0, data: { id: recommendation.id, feedback: { rating: 5 } } })

    await page.getByRole('button', { name: '搭配记录', exact: true }).click()
    await page.reload()
    await expect(page.getByRole('heading', { name: '搭配记录', exact: true, level: 1 })).toBeVisible()
    await expect(page.getByText('已评 5 星', { exact: true })).toBeVisible()
    const historyResponse = await context.request.get('/api/v1/me/recommendations')
    expect(historyResponse.status()).toBe(200)
    const history = (await historyResponse.json()).data.content.find(item => item.id === recommendation.id)
    expect(history).toMatchObject({ id: recommendation.id, saved: true, feedback: { rating: 5 } })
    const wardrobeResponse = await context.request.get('/api/v1/me/wardrobe')
    expect(wardrobeResponse.status()).toBe(200)
    expect((await wardrobeResponse.json()).data.map(item => item.id).sort()).toEqual([...garmentIds].sort())
    await page.screenshot({ path: '../output/integration-live/saved-history.png', fullPage: true, animations: 'disabled' })

    const logout = postResponse(page, '/api/v1/auth/logout')
    await page.getByRole('button', { name: '退出登录', exact: true }).click()
    expect((await logout).status()).toBe(200)
    for (const path of ['/api/v1/auth/me', '/api/v1/me/wardrobe', '/api/v1/me/recommendations']) {
      const response = await context.request.get(path)
      expect(response.status(), path).toBe(401)
    }
    await page.reload()
    const afterReload = await context.request.get('/api/v1/auth/me')
    expect(afterReload.status()).toBe(401)
    await testInfo.attach('ordinary-account-result.json', {
      body: Buffer.from(JSON.stringify({ username, garmentIds, recommendationId: recommendation.id,
        engine: recommendation.engine, fallbackReason: recommendation.generationAudit.fallbackReason,
        saved: history.saved, rating: history.feedback.rating }, null, 2)),
      contentType: 'application/json'
    })
  })

  test('a local PNG upload persists through private image storage and remains isolated from another account', async ({ page, context, playwright }, testInfo) => {
    test.setTimeout(150_000)
    const suffix = `${Date.now().toString(36)}_${testInfo.workerIndex}`
    const username = `integration_upload_${suffix}`.slice(0, 32)
    await registerThroughUi(page, username)
    await page.getByRole('button', { name: '我的衣橱', exact: true }).click()
    await page.locator('.wardrobe-view > .page-header').getByRole('button', { name: '上传衣物', exact: true }).click()
    const dialog = page.getByRole('dialog', { name: '添加衣物', exact: true })
    await dialog.getByLabel('衣物照片', { exact: true }).setInputFiles({
      name: 'integration-local-pixel.png', mimeType: 'image/png', buffer: localPng
    })
    await expect(dialog.getByRole('checkbox', { name: '授权 AI 识别衣物' })).not.toBeChecked()
    const garment = { name: '联调 PNG 存储样例', category: '上装', color: '白色', style: '存储联调' }
    await dialog.getByLabel('衣物名称').fill(garment.name)
    await dialog.getByLabel('类别').selectOption(garment.category)
    await dialog.getByLabel('颜色', { exact: true }).fill(garment.color)
    await dialog.getByLabel(/^风格/).fill(garment.style)
    const upload = postResponse(page, '/api/v1/me/wardrobe/upload')
    await dialog.getByRole('button', { name: '添加到衣橱', exact: true }).click()
    const response = await upload
    expect(response.status()).toBe(200)
    const body = await response.json()
    expect(body).toMatchObject({ code: 0, data: { ...garment, recognitionStatus: 'MANUAL_CORRECTED' } })
    const imageUrl = `/api/v1/me/wardrobe/${body.data.id}/image`
    expect(body.data.imageUrl).toBe(imageUrl)
    await expect(page.getByRole('heading', { name: garment.name, exact: true })).toBeVisible()

    async function expectStoredImage() {
      const image = await context.request.get(imageUrl)
      expect(image.status()).toBe(200)
      expect(image.headers()['content-type']).toMatch(/^image\/png(?:;|$)/)
      expect(image.headers()['x-content-type-options']).toBe('nosniff')
      expect(await image.body()).toEqual(localPng)
      const displayedImage = page.locator(`.wardrobe-view img[src="${imageUrl}"]`).first()
      await expect(displayedImage).toBeVisible()
      await expect.poll(() => displayedImage.evaluate(node => node.complete && node.naturalWidth > 0)).toBe(true)
    }
    await expectStoredImage()
    await page.reload()
    await expect(page.getByRole('heading', { name: garment.name, exact: true })).toBeVisible()
    await expectStoredImage()

    const stranger = await playwright.request.newContext({ baseURL: process.env.E2E_BASE_URL })
    try {
      const otherUsername = `integration_other_${suffix}`.slice(0, 32)
      const registration = await stranger.post('/api/v1/auth/register', {
        headers: await csrfHeaders(stranger), data: { username: otherUsername, password }
      })
      expect(registration.status()).toBe(201)
      const login = await stranger.post('/api/v1/auth/login', {
        headers: await csrfHeaders(stranger), form: { username: otherUsername, password }
      })
      expect(login.status()).toBe(200)
      const denied = await stranger.get(imageUrl)
      expect(denied.status()).toBe(404)
      expect(await denied.json()).toMatchObject({ code: 404, data: null })
    } finally {
      await stranger.dispose()
    }

    await page.screenshot({ path: '../output/integration-live/private-upload.png', fullPage: true, animations: 'disabled' })
    const logout = postResponse(page, '/api/v1/auth/logout')
    await page.getByRole('button', { name: '退出登录', exact: true }).click()
    expect((await logout).status()).toBe(200)
    const afterLogout = await context.request.get(imageUrl)
    expect(afterLogout.status()).toBe(401)
    await testInfo.attach('private-image-result.json', {
      body: Buffer.from(JSON.stringify({ username, itemId: body.data.id, imageUrl,
        expectedBytes: localPng.length, recognitionStatus: body.data.recognitionStatus,
        otherAccountStatus: 404, loggedOutStatus: 401 }, null, 2)),
      contentType: 'application/json'
    })
  })
})
