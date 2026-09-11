import { expect, test } from '@playwright/test'

const password = 'e2e-password-2026'
const transparentPng = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=',
  'base64'
)
let accountSequence = 0

function uniqueUsername(label, testInfo) {
  accountSequence += 1
  const suffix = `${Date.now().toString(36)}${accountSequence.toString(36)}${testInfo.workerIndex}`
  return `e2e_${label}_${suffix}`.slice(0, 32)
}

async function expectCurrentAuthCopy(page) {
  await expect(page.getByRole('heading', { name: '先把你的衣橱记下来。', exact: true })).toBeVisible()
  await expect(page.getByRole('tab', { name: '登录', exact: true })).toBeVisible()
  await expect(page.getByRole('tab', { name: '注册', exact: true })).toBeVisible()
  await expect(page.getByText('欢迎回来', { exact: true })).toHaveCount(0)
  await expect(page.getByText('建立你的账户', { exact: true })).toHaveCount(0)
  await expect(page.getByRole('heading', { name: '登录知己', exact: true })).toHaveCount(0)
  await expect(page.getByRole('heading', { name: '注册知己', exact: true })).toHaveCount(0)
}

async function registerThroughUi(page, username, onLoginReady) {
  await page.goto('/')
  await expectCurrentAuthCopy(page)
  if (onLoginReady) await onLoginReady()
  await page.getByRole('tab', { name: '注册', exact: true }).click()
  await expectCurrentAuthCopy(page)
  await page.locator('input[name="username"]').fill(username)
  await page.locator('input[name="password"]').fill(password)
  await page.locator('input[name="confirmPassword"]').fill(password)
  await page.getByRole('button', { name: '注册账户', exact: true }).click()
  await expect(page.getByRole('button', { name: '退出登录' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '今天穿什么，从衣橱开始。' })).toBeVisible()
}

test('uses a warm 4:6 image-to-login composition', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto('/')
  await expectCurrentAuthCopy(page)

  const layout = await page.evaluate(() => {
    const view = document.querySelector('.auth-view')
    const visual = document.querySelector('.auth-visual')
    const panel = document.querySelector('.auth-panel')
    const submit = document.querySelector('.auth-submit')
    return {
      viewWidth: view.getBoundingClientRect().width,
      visualWidth: visual.getBoundingClientRect().width,
      panelWidth: panel.getBoundingClientRect().width,
      submitBackground: getComputedStyle(submit).backgroundColor
    }
  })

  expect(layout.visualWidth / layout.viewWidth).toBeCloseTo(0.4, 1)
  expect(layout.panelWidth / layout.viewWidth).toBeCloseTo(0.6, 1)
  expect(layout.submitBackground).toBe('rgb(42, 33, 31)')
  await expect(page.locator('.auth-visual img')).toHaveAttribute('src', '/assets/look-urban.jpg')
})

async function csrfHeaders(api) {
  const response = await api.get('/api/v1/auth/csrf')
  expect(response.status()).toBe(200)
  const body = await response.json()
  expect(body).toMatchObject({
    code: 0,
    data: {
      headerName: expect.any(String),
      token: expect.any(String)
    }
  })
  return { [body.data.headerName]: body.data.token }
}

async function createSupportingItem(api, headers, item) {
  const response = await api.post('/api/v1/me/wardrobe', { headers, data: item })
  expect(response.ok()).toBeTruthy()
  const body = await response.json()
  expect(body.code).toBe(0)
  return body.data
}

async function cleanupWardrobe(api, headers, itemIds) {
  if (!headers || !itemIds.length) return
  await Promise.allSettled(itemIds.map((itemId) => api.delete(
    `/api/v1/me/wardrobe/${itemId}`,
    { headers }
  )))
}

test('adds a garment, generates an outfit, saves it, and persists feedback', async ({ page, context }, testInfo) => {
  const username = uniqueUsername('flow', testInfo)
  const createdItemIds = []
  let writeHeaders = null

  try {
    await registerThroughUi(page, username)
    await page.getByRole('button', { name: '查看天气' }).click()
    await expect(page.locator('.weather-panel')).toBeVisible()
    await page.keyboard.press('Escape')
    await expect(page.locator('.weather-panel')).toBeHidden()
    writeHeaders = await csrfHeaders(context.request)

    const supportingItems = [
      { name: 'E2E 石墨灰直筒裤', category: '下装', color: '石墨灰', style: '通勤' },
      { name: 'E2E 白色低帮鞋', category: '鞋履', color: '白色', style: '城市休闲' }
    ]
    for (const item of supportingItems) {
      const created = await createSupportingItem(context.request, writeHeaders, item)
      createdItemIds.push(created.id)
    }

    await page.getByRole('button', { name: '衣橱', exact: true }).click()
    await expect(page.getByRole('heading', { name: '我的衣橱' })).toBeVisible()
    await expect(page.getByRole('heading', { name: supportingItems[0].name })).toBeVisible()
    await page.getByRole('button', { name: '添加衣物', exact: true }).click()

    const dialog = page.getByRole('dialog', { name: '添加一件衣物' })
    await expect(dialog).toBeVisible()
    await dialog.getByLabel('衣物名称').fill('E2E 雾蓝牛津纺衬衫')
    await dialog.getByLabel('类别').selectOption('上装')
    await dialog.getByLabel('颜色').fill('雾蓝')
    await dialog.getByLabel(/^风格/).fill('极简通勤')

    const createResponsePromise = page.waitForResponse((response) => {
      const url = new URL(response.url())
      return url.pathname === '/api/v1/me/wardrobe' && response.request().method() === 'POST'
    })
    await dialog.getByRole('button', { name: '添加到衣橱' }).click()
    const createResponse = await createResponsePromise
    expect(createResponse.ok()).toBeTruthy()
    const createBody = await createResponse.json()
    expect(createBody.code).toBe(0)
    createdItemIds.push(createBody.data.id)
    await expect(page.getByRole('heading', { name: 'E2E 雾蓝牛津纺衬衫' })).toBeVisible()

    await page.getByRole('button', { name: '推荐', exact: true }).click()
    await expect(page.getByRole('heading', { name: '今天有 5 套搭配可选。' })).toBeVisible()
    await expect(page.getByRole('heading', { name: '今天的搭配', exact: true })).toBeVisible()
    await expect(page.locator('.ootd-stack-card')).toHaveCount(5)
    await page.getByLabel('场合', { exact: true }).fill('E2E 答辩通勤')
    await page.getByLabel('城市', { exact: true }).fill(process.env.E2E_CITY || '杭州')
    await page.getByLabel(/^风格提示/).fill('正式、简洁、适合室内汇报')

    const recommendationResponsePromise = page.waitForResponse((response) => {
      const url = new URL(response.url())
      return url.pathname === '/api/v1/recommendations' && response.request().method() === 'POST'
    })
    await page.getByRole('button', { name: '生成搭配' }).click()
    const recommendationResponse = await recommendationResponsePromise
    expect(recommendationResponse.ok()).toBeTruthy()
    const recommendationBody = await recommendationResponse.json()
    expect(recommendationBody.code).toBe(0)
    expect(recommendationBody.data.items.length).toBeGreaterThanOrEqual(2)
    const recommendationId = recommendationBody.data.id

    // The E2E Compose override disables the recommendation LLM so this suite never makes a paid
    // model call. Assert generationAudit shows rule fallback (llm-disabled) rather than provider metadata.
    expect(recommendationBody.data.engine).toBe('development-rule-v1')
    expect(recommendationBody.data.generationAudit).toBeTruthy()
    expect(recommendationBody.data.generationAudit.fallbackReason).toBe('llm-disabled')
    expect(recommendationBody.data.generationAudit.modelName).toBeNull()
    expect(recommendationBody.data.generationAudit.promptVersion).toBeNull()
    expect(recommendationBody.data.generationAudit.providerCallId).toBeNull()

    await expect(page.getByText(`搭配 #${recommendationId}`, { exact: true })).toBeVisible()
    await expect(page.getByText(recommendationBody.data.summary, { exact: true })).toBeVisible()

    const saveResponsePromise = page.waitForResponse((response) => {
      const url = new URL(response.url())
      return url.pathname === `/api/v1/me/recommendations/${recommendationId}/save`
        && response.request().method() === 'POST'
    })
    await page.getByRole('button', { name: '收藏这套搭配' }).click()
    const saveResponse = await saveResponsePromise
    expect(saveResponse.ok()).toBeTruthy()
    const saveBody = await saveResponse.json()
    expect(saveBody.code).toBe(0)
    expect(saveBody.data.saved).toBe(true)
    await expect(page.getByRole('button', { name: '已保存' })).toBeDisabled()

    const feedbackResponsePromise = page.waitForResponse((response) => {
      const url = new URL(response.url())
      return url.pathname === `/api/v1/me/recommendations/${recommendationId}/feedback`
        && response.request().method() === 'POST'
    })
    await page.getByRole('button', { name: '5 星评分' }).click()
    const feedbackResponse = await feedbackResponsePromise
    expect(feedbackResponse.ok()).toBeTruthy()
    const feedbackBody = await feedbackResponse.json()
    expect(feedbackBody).toMatchObject({ code: 0, data: { id: recommendationId, feedback: { rating: 5 } } })

    await page.getByRole('button', { name: '历史', exact: true }).click()
    await expect(page.getByRole('heading', { name: '你生成过的每一套搭配' })).toBeVisible()
    await expect(page.getByText('已评 5 星', { exact: true })).toBeVisible()

    const historyResponse = await context.request.get('/api/v1/me/recommendations')
    expect(historyResponse.ok()).toBeTruthy()
    const historyBody = await historyResponse.json()
    const savedRecord = historyBody.data.content.find((item) => item.id === recommendationId)
    expect(savedRecord).toMatchObject({ id: recommendationId, saved: true, feedback: { rating: 5 } })

    await testInfo.attach('e2e-result.json', {
      body: Buffer.from(JSON.stringify({
        username,
        createdGarmentId: createBody.data.id,
        recommendationId,
        engine: recommendationBody.data.engine,
        saved: saveBody.data.saved,
        rating: feedbackBody.data.feedback.rating
      }, null, 2)),
      contentType: 'application/json'
    })
  } finally {
    await cleanupWardrobe(context.request, writeHeaders, createdItemIds)
  }
})

test('rejects private APIs without authentication and preserves then clears the browser session', async ({ page, context }, testInfo) => {
  const unauthorized = await context.request.get('/api/v1/me/wardrobe')
  expect(unauthorized.status()).toBe(401)
  await expect(await unauthorized.json()).toMatchObject({ code: 401, data: null })

  const username = uniqueUsername('auth', testInfo)
  await registerThroughUi(page, username)
  await page.reload()

  await expect(page.getByRole('button', { name: '退出登录' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '今天穿什么，从衣橱开始。' })).toBeVisible()
  const authenticated = await context.request.get('/api/v1/auth/me')
  expect(authenticated.ok()).toBeTruthy()
  await expect(await authenticated.json()).toMatchObject({ code: 0, data: { username } })

  await page.getByRole('button', { name: '衣橱', exact: true }).click()
  await expect(page.getByRole('heading', { name: '我的衣橱' })).toBeVisible()
  await page.getByRole('button', { name: '退出登录' }).click()

  await expectCurrentAuthCopy(page)
  await expect(page.getByRole('heading', { name: '我的衣橱' })).toHaveCount(0)
  const afterLogout = await context.request.get('/api/v1/me/wardrobe')
  expect(afterLogout.status()).toBe(401)
  await expect(await afterLogout.json()).toMatchObject({ code: 401, data: null })
})

test('uploads an image without AI consent and requires complete manual fields', async ({ page, context }, testInfo) => {
  const username = uniqueUsername('upload', testInfo)
  const createdItemIds = []
  let writeHeaders = null

  try {
    await registerThroughUi(page, username)
    writeHeaders = await csrfHeaders(context.request)
    await page.getByRole('button', { name: '衣橱', exact: true }).click()
    await expect(page.getByRole('heading', { name: '我的衣橱' })).toBeVisible()
    await page.getByRole('button', { name: '上传衣物', exact: true }).click()

    const dialog = page.getByRole('dialog', { name: '添加一件衣物' })
    const nameField = dialog.getByLabel('衣物名称')
    const categoryField = dialog.getByLabel('类别')
    const colorField = dialog.getByLabel('颜色')
    await dialog.getByLabel('衣物照片', { exact: true }).setInputFiles({
      name: 'manual-shirt.png',
      mimeType: 'image/png',
      buffer: transparentPng
    })

    const consent = dialog.getByRole('checkbox', { name: '使用 AI 识别照片' })
    await expect(consent).not.toBeChecked()
    await expect(nameField).toHaveJSProperty('required', true)
    await expect(categoryField).toHaveJSProperty('required', true)
    await expect(colorField).toHaveJSProperty('required', true)
    await dialog.getByRole('button', { name: '添加到衣橱' }).click()
    await expect(nameField).toBeFocused()
    await expect(dialog).toBeVisible()

    const itemName = 'E2E 人工确认衬衫'
    await nameField.fill(itemName)
    await categoryField.selectOption('上装')
    await colorField.fill('暖白')
    await dialog.getByLabel(/^风格/).fill('极简')

    const uploadResponsePromise = page.waitForResponse((response) => {
      const url = new URL(response.url())
      return url.pathname === '/api/v1/me/wardrobe/upload' && response.request().method() === 'POST'
    })
    await dialog.getByRole('button', { name: '添加到衣橱' }).click()
    const uploadResponse = await uploadResponsePromise
    expect(uploadResponse.ok()).toBeTruthy()
    const uploadBody = await uploadResponse.json()
    expect(uploadBody).toMatchObject({
      code: 0,
      data: { name: itemName, category: '上装', color: '暖白', recognitionStatus: 'MANUAL_CORRECTED' }
    })
    createdItemIds.push(uploadBody.data.id)
    await expect(page.getByRole('heading', { name: itemName })).toBeVisible()
    await expect(page.getByText('已确认', { exact: true })).toBeVisible()
  } finally {
    await cleanupWardrobe(context.request, writeHeaders, createdItemIds)
  }
})

test.describe('mobile viewport', () => {
  test.use({ viewport: { width: 390, height: 844 } })

  test('keeps login, navigation, and the upload dialog within the viewport', async ({ page }, testInfo) => {
    const username = uniqueUsername('mobile', testInfo)
    await registerThroughUi(page, username, async () => {
      expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390)
      await expectCurrentAuthCopy(page)
    })

    const headerActions = page.locator('.app-header .header-actions')
    const navigation = page.getByRole('navigation', { name: '页面导航' })
    await expect(navigation).toBeVisible()
    const actionsBox = await headerActions.boundingBox()
    const navigationBox = await navigation.boundingBox()
    expect(actionsBox).not.toBeNull()
    expect(navigationBox).not.toBeNull()
    expect(navigationBox.y).toBeGreaterThanOrEqual(actionsBox.y + actionsBox.height - 1)
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390)

    await page.getByRole('button', { name: '打开页面菜单' }).click()
    const mobileWardrobeLink = page.getByRole('menuitem', { name: '衣橱', exact: true })
    await expect(mobileWardrobeLink).toBeVisible()
    await mobileWardrobeLink.click()
    await expect(page.getByRole('heading', { name: '我的衣橱' })).toBeVisible()
    await page.getByRole('button', { name: '上传衣物', exact: true }).click()

    const dialog = page.getByRole('dialog', { name: '添加一件衣物' })
    await expect(dialog).toBeVisible()
    await expect(dialog.getByLabel('衣物照片', { exact: true })).toBeVisible()
    await dialog.getByRole('button', { name: '添加到衣橱' }).scrollIntoViewIfNeeded()
    await expect(dialog.getByRole('button', { name: '添加到衣橱' })).toBeVisible()
    const dialogBox = await dialog.boundingBox()
    expect(dialogBox).not.toBeNull()
    expect(dialogBox.x).toBeGreaterThanOrEqual(-1)
    expect(dialogBox.x + dialogBox.width).toBeLessThanOrEqual(391)
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390)
  })
})
