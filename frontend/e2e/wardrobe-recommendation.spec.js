import { expect, test } from '@playwright/test'

const apiBaseUrl = process.env.E2E_API_URL || 'http://localhost:8088'

function userHeaders(userId) {
  return { 'X-User-Id': userId }
}

async function createSupportingItem(request, userId, item) {
  const response = await request.post(apiBaseUrl + '/api/v1/me/wardrobe', {
    headers: userHeaders(userId),
    data: item
  })
  expect(response.ok()).toBeTruthy()
  const body = await response.json()
  expect(body.code).toBe(0)
  return body.data
}

test('adds a garment, generates an outfit, and saves it', async ({ page, request }, testInfo) => {
  const userId = 'e2e-smoke-' + Date.now() + '-' + testInfo.workerIndex
  const createdItemIds = []

  await page.route('**/api/**', async (route) => {
    await route.continue({
      headers: {
        ...route.request().headers(),
        'x-user-id': userId
      }
    })
  })

  const healthResponse = await request.get(apiBaseUrl + '/actuator/health')
  expect(healthResponse.ok(), 'backend health endpoint must be available').toBeTruthy()
  await expect(await healthResponse.json()).toMatchObject({ status: 'UP' })

  const supportingItems = [
    { name: 'E2E 石墨灰直筒裤', category: '下装', color: '石墨灰', style: '通勤' },
    { name: 'E2E 白色低帮鞋', category: '鞋履', color: '白色', style: '城市休闲' }
  ]

  try {
    for (const item of supportingItems) {
      const created = await createSupportingItem(request, userId, item)
      createdItemIds.push(created.id)
    }

    await page.goto('/')
    await expect(page.getByRole('heading', { name: '知己，懂你的穿搭。' })).toBeVisible()

    await page.getByRole('button', { name: '衣橱', exact: true }).click()
    await expect(page.getByRole('heading', { name: '我的衣橱' })).toBeVisible()
    await page.getByRole('button', { name: '添加单品', exact: true }).click()

    const dialog = page.getByRole('dialog', { name: '添加一件单品' })
    await expect(dialog).toBeVisible()
    await dialog.getByLabel('单品名称').fill('E2E 雾蓝牛津纺衬衫')
    await dialog.getByLabel('类别').selectOption('上装')
    await dialog.getByLabel('颜色').fill('雾蓝')
    await dialog.getByLabel(/^风格/).fill('极简通勤')

    const createResponsePromise = page.waitForResponse((response) => {
      const url = new URL(response.url())
      return url.pathname === '/api/v1/me/wardrobe' && response.request().method() === 'POST'
    })
    await dialog.getByRole('button', { name: '加入衣橱' }).click()
    const createResponse = await createResponsePromise
    expect(createResponse.ok()).toBeTruthy()
    const createBody = await createResponse.json()
    expect(createBody.code).toBe(0)
    createdItemIds.push(createBody.data.id)
    await expect(page.getByRole('heading', { name: 'E2E 雾蓝牛津纺衬衫' })).toBeVisible()

    await page.getByRole('button', { name: '推荐', exact: true }).click()
    await expect(page.getByRole('heading', { name: '从你的衣橱，生成今天的答案。' })).toBeVisible()
    await page.getByLabel('场合', { exact: true }).fill('E2E 答辩通勤')
    await page.getByLabel('城市', { exact: true }).fill(process.env.E2E_CITY || '杭州')
    await page.getByLabel(/^风格提示/).fill('正式、简洁、适合室内汇报')

    const recommendationResponsePromise = page.waitForResponse((response) => {
      const url = new URL(response.url())
      return url.pathname === '/api/v1/recommendations' && response.request().method() === 'POST'
    })
    await page.getByRole('button', { name: '生成穿搭方案' }).click()
    const recommendationResponse = await recommendationResponsePromise
    expect(recommendationResponse.ok()).toBeTruthy()
    const recommendationBody = await recommendationResponse.json()
    expect(recommendationBody.code).toBe(0)
    expect(recommendationBody.data.items.length).toBeGreaterThanOrEqual(2)
    const recommendationId = recommendationBody.data.id

    await expect(page.getByText('方案 #' + recommendationId, { exact: true })).toBeVisible()
    await expect(page.getByText(recommendationBody.data.summary, { exact: true })).toBeVisible()

    const saveResponsePromise = page.waitForResponse((response) => {
      const url = new URL(response.url())
      return url.pathname === '/api/v1/me/recommendations/' + recommendationId + '/save'
        && response.request().method() === 'POST'
    })
    await page.getByRole('button', { name: '收藏这套方案' }).click()
    const saveResponse = await saveResponsePromise
    expect(saveResponse.ok()).toBeTruthy()
    const saveBody = await saveResponse.json()
    expect(saveBody.code).toBe(0)
    expect(saveBody.data.saved).toBe(true)
    await expect(page.getByRole('button', { name: '已保存到历史' })).toBeDisabled()

    const historyResponse = await request.get(apiBaseUrl + '/api/v1/me/recommendations', {
      headers: userHeaders(userId)
    })
    expect(historyResponse.ok()).toBeTruthy()
    const historyBody = await historyResponse.json()
    const savedRecord = historyBody.data.find((item) => item.id === recommendationId)
    expect(savedRecord).toMatchObject({ id: recommendationId, saved: true })

    await testInfo.attach('e2e-result.json', {
      body: Buffer.from(JSON.stringify({
        userId,
        createdGarmentId: createBody.data.id,
        recommendationId,
        engine: recommendationBody.data.engine,
        saved: saveBody.data.saved
      }, null, 2)),
      contentType: 'application/json'
    })
  } finally {
    await Promise.allSettled(createdItemIds.map((itemId) => request.delete(
      apiBaseUrl + '/api/v1/me/wardrobe/' + itemId,
      { headers: userHeaders(userId) }
    )))
  }
})
