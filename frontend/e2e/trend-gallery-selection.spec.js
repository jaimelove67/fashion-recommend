import { expect, test } from '@playwright/test'

const svg = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 300 533"><rect width="300" height="533" fill="#b8ccb8"/><circle cx="150" cy="55" r="24" fill="#decab6"/><path d="M112 86h76l24 212h-124z" fill="#315b49"/><path d="M117 298h28l-8 215h-28zm38 0h28l8 215h-28z" fill="#3a4146"/></svg>'
const trends = Array.from({ length: 10 }, (_, index) => ({
  id: `gallery-${index + 1}`,
  platform: 'weibo',
  title: `画廊回归样例 ${index + 1}`,
  topicTags: [`回归标签 ${index + 1}`],
  imageUrl: `https://images.test/gallery-${index + 1}.svg`,
  summary: `来源摘要 ${index + 1}`,
  publishedAt: '2026-09-21T09:00:00+08:00',
  evidence: { mediaType: 'post', images: [], fullBodyImageUrl: `https://images.test/gallery-${index + 1}.svg` }
}))

async function openTrendGallery(page) {
  await page.setViewportSize({ width: 1280, height: 900 })
  await page.route('https://images.test/**', (route) => route.fulfill({
    status: 200,
    contentType: 'image/svg+xml',
    body: svg
  }))
  await page.route('**/api/**', async (route) => {
    const pathname = new URL(route.request().url()).pathname
    let data = {}
    if (pathname === '/api/v1/auth/csrf') data = { token: 'qa', headerName: 'X-XSRF-TOKEN' }
    else if (pathname === '/api/v1/auth/me') data = { username: 'gallery-qa', authorities: [] }
    else if (pathname === '/api/v1/trends') {
      data = { items: trends, styles: [], fetchedAt: '2026-09-21T09:00:00+08:00', demoMode: true, sources: [], notice: 'local regression fixture' }
    } else if (pathname === '/api/v1/me/wardrobe') data = []
    else if (pathname === '/api/v1/me/recommendations') data = { content: [], totalElements: 0, page: 0, hasNext: false }
    else if (pathname === '/api/v1/me/style-profile') data = null

    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ code: 0, message: 'ok', data })
    })
  })

  await page.goto('/#trend')
}

test.beforeEach(async ({ page }) => {
  await openTrendGallery(page)
})

test('pressing a gallery photo keeps it on track, then rotates it into the center', async ({ page }) => {
  const gallery = page.getByRole('region', { name: '趋势穿搭画廊' })
  const clickedCard = gallery.getByRole('button', { name: '查看第 2 张图片：画廊回归样例 2' })
  const centerCard = gallery.locator('.gallery-card[data-gallery-center="true"]')
  await expect(clickedCard).toBeVisible()
  const centerSlot = await centerCard.boundingBox()
  const originalSlot = await clickedCard.boundingBox()
  expect(Math.abs(originalSlot.x - centerSlot.x)).toBeGreaterThan(100)

  await page.mouse.move(
    originalSlot.x + originalSlot.width / 2,
    originalSlot.y + originalSlot.height / 2
  )
  await page.mouse.down()
  await page.waitForTimeout(180)
  const pressedSlot = await clickedCard.boundingBox()
  expect(pressedSlot.x).toBeCloseTo(originalSlot.x, 0)
  expect(pressedSlot.y).toBeCloseTo(originalSlot.y, 0)
  expect(pressedSlot.width).toBeCloseTo(originalSlot.width, 0)
  expect(pressedSlot.height).toBeCloseTo(originalSlot.height, 0)
  await page.mouse.up()

  await expect(page.locator('#trend-breakdown-title')).toHaveText('画廊回归样例 2')
  await expect(page.getByText('来源摘要 2', { exact: true })).toBeVisible()
  await expect(gallery.locator('.gallery-card[aria-current="true"]')).toHaveAttribute('aria-label', '查看第 2 张图片：画廊回归样例 2')
  await expect(clickedCard).toHaveAttribute('data-gallery-center', 'true', { timeout: 500 })
  await expect(page.getByRole('dialog')).toHaveCount(0)

  await expect.poll(async () => clickedCard.evaluate((element) =>
    element.getAnimations().some((animation) => animation.playState === 'running')
  ), { timeout: 3000 }).toBe(false)
  const selectedSlot = await gallery.locator('.gallery-card[aria-current="true"]').boundingBox()
  const selectedImage = await gallery.locator('.gallery-card[aria-current="true"] .gallery-card-image img').boundingBox()
  const galleryBounds = await gallery.boundingBox()

  expect(selectedSlot.x).toBeCloseTo(centerSlot.x, 0)
  expect(selectedSlot.y).toBeCloseTo(centerSlot.y, 0)
  expect(selectedSlot.width).toBeCloseTo(centerSlot.width, 0)
  expect(selectedSlot.height).toBeCloseTo(centerSlot.height, 0)
  expect(selectedImage.x).toBeGreaterThanOrEqual(galleryBounds.x)
  expect(selectedImage.x + selectedImage.width).toBeLessThanOrEqual(galleryBounds.x + galleryBounds.width)
})

test('does not render manual arrow controls', async ({ page }) => {
  await expect(page.getByRole('button', { name: '上一条趋势' })).toHaveCount(0, { timeout: 1000 })
  await expect(page.getByRole('button', { name: '下一条趋势' })).toHaveCount(0, { timeout: 1000 })
})

test('shows five larger gallery images with symmetric edge previews on desktop', async ({ page }) => {
  const gallery = page.getByRole('region', { name: '趋势穿搭画廊' })
  const cards = gallery.locator('.gallery-card')
  const edgeCards = gallery.locator('.gallery-card[data-gallery-edge="true"]')
  await expect(cards).toHaveCount(5)
  await expect(edgeCards).toHaveCount(2)
  const centerImage = gallery.locator('.gallery-card[data-gallery-index="0"] .gallery-card-image img')
  await expect(centerImage).toHaveCSS('object-fit', 'contain')
  await expect(centerImage).toBeVisible()
  const centerImageBounds = await centerImage.boundingBox()
  expect(centerImageBounds.width).toBeGreaterThanOrEqual(300)

  const galleryBounds = await gallery.boundingBox()
  expect(centerImageBounds.height / galleryBounds.height).toBeGreaterThanOrEqual(0.7)
  const cardBounds = (await cards.evaluateAll((elements) => elements.map((element) => {
    const bounds = element.getBoundingClientRect()
    return { edge: element.getAttribute('data-gallery-edge') === 'true', x: bounds.x, width: bounds.width }
  }))).sort((a, b) => a.x - b.x)
  const edgeVisibleRatios = []
  for (const bounds of cardBounds) {
    const visibleWidth = Math.max(
      0,
      Math.min(bounds.x + bounds.width, galleryBounds.x + galleryBounds.width)
        - Math.max(bounds.x, galleryBounds.x)
    )
    const visibleRatio = visibleWidth / bounds.width
    expect(visibleRatio).toBeGreaterThanOrEqual(bounds.edge ? 0.35 : 0.85)
    if (bounds.edge) edgeVisibleRatios.push(visibleRatio)
  }
  expect(Math.abs(edgeVisibleRatios[0] - edgeVisibleRatios[1])).toBeLessThanOrEqual(0.02)
  for (let index = 1; index < cardBounds.length; index += 1) {
    const previous = cardBounds[index - 1]
    const current = cardBounds[index]
    if (!previous.edge && !current.edge) {
      expect(current.x - (previous.x + previous.width)).toBeGreaterThanOrEqual(20)
    }
  }
})

test('cycles through ten unique images while showing five cards at a time', async ({ page }) => {
  const gallery = page.getByRole('region', { name: '趋势穿搭画廊' })
  const cards = gallery.locator('.gallery-card')
  await expect(cards).toHaveCount(5)
  await expect(cards.first()).toHaveAttribute('aria-setsize', '10')

  await gallery.locator('.gallery-card[data-gallery-center="true"]').focus()
  await page.keyboard.press('End')
  const lastCard = gallery.locator('.gallery-card[data-gallery-index="9"][data-gallery-center="true"]')
  await expect(lastCard).toBeVisible()
  await expect(lastCard).toHaveAttribute('aria-label', '查看第 10 张图片：画廊回归样例 10')
  await expect(cards).toHaveCount(5)
})

test('uses only the daily image pool from the selected source filter', async ({ page }) => {
  const requestedPeriods = []
  await page.route('**/api/v1/trends**', async (route) => {
    const period = new URL(route.request().url()).searchParams.get('period')
    requestedPeriods.push(period)
    const items = period === 'day' ? trends : trends.slice(0, 3)
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 0,
        message: 'ok',
        data: { items, styles: [], fetchedAt: '2026-09-21T09:00:00+08:00', demoMode: false, sources: [] }
      })
    })
  })

  await page.reload()
  const gallery = page.getByRole('region', { name: '趋势穿搭画廊' })
  const cards = gallery.locator('.gallery-card')
  await expect(cards).toHaveCount(5)
  await expect(cards.first()).toHaveAttribute('aria-setsize', '10')
  expect(requestedPeriods).toEqual(['day'])
  await expect(page.getByText(/10 张可用趋势图片/)).toBeVisible()
})

test('keeps old unreviewed covers and publisher images out of the gallery', async ({ page }) => {
  await page.route('**/api/v1/trends**', (route) => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({ code: 0, message: 'ok', data: {
      items: [
        { ...trends[0], evidence: { mediaType: 'post', images: [] } },
        { ...trends[1], platform: 'editorial' }
      ],
      styles: [], sources: []
    } })
  }))
  await page.reload()

  await expect(page.locator('.trend-gallery .gallery-card')).toHaveCount(0)
  await expect(page.locator('.trend-empty-state')).toHaveCount(0)
  await expect(page.locator('.discovery-look')).toHaveCount(2)
  await expect(page.locator('.discovery-look').first()).toContainText(trends[0].title)
})

test('keeps one focal image centered and larger than its side images', async ({ page }) => {
  const gallery = page.getByRole('region', { name: '趋势穿搭画廊' })
  const focalCard = gallery.locator('.gallery-card[data-gallery-center="true"]')
  const sideCard = gallery.locator('.gallery-card[data-gallery-index="1"]')
  await expect(focalCard).toBeVisible()
  await expect(sideCard).toBeVisible()

  const galleryBounds = await gallery.boundingBox()
  const focalBounds = await focalCard.boundingBox()
  const sideBounds = await sideCard.boundingBox()
  const galleryCenter = galleryBounds.x + galleryBounds.width / 2
  const focalCenter = focalBounds.x + focalBounds.width / 2

  expect(Math.abs(focalCenter - galleryCenter)).toBeLessThanOrEqual(2)
  expect(focalBounds.width / sideBounds.width).toBeGreaterThanOrEqual(1.06)
})

test('keeps an outgoing boundary image mounted while it fades away', async ({ page }) => {
  const gallery = page.getByRole('region', { name: '趋势穿搭画廊' })
  const outgoingCard = gallery.locator('.gallery-card[data-gallery-index="8"]')
  await expect(outgoingCard).toBeVisible()
  const outgoingHandle = await outgoingCard.elementHandle()

  await gallery.getByRole('button', { name: '查看第 2 张图片：画廊回归样例 2' }).click()

  const leavingState = await outgoingHandle.evaluate((element) => ({
    connected: element.isConnected,
    runningAnimations: element.getAnimations().filter((animation) => animation.playState === 'running').length
  }))
  expect(leavingState.connected).toBe(true)
  expect(leavingState.runningAnimations).toBeGreaterThan(0)
  await expect.poll(() => outgoingHandle.evaluate((element) => element.isConnected), { timeout: 1500 }).toBe(false)
})

test('autoplays while the pointer rests over the 3D track without changing the selected detail', async ({ page }) => {
  const gallery = page.getByRole('region', { name: '趋势穿搭画廊' })
  await expect(gallery.locator('.gallery-card')).not.toHaveCount(0)
  await gallery.locator('.gallery-card[data-gallery-center="true"]').hover()
  const visibleIndexes = await gallery.locator('.gallery-card').evaluateAll((cards) =>
    cards.map((card) => card.getAttribute('data-gallery-index')).join(',')
  )
  await expect.poll(async () => gallery.locator('.gallery-card').evaluateAll((cards) =>
    cards.map((card) => card.getAttribute('data-gallery-index')).join(',')
  ), { timeout: 4500 }).not.toBe(visibleIndexes)
  await expect(page.locator('#trend-breakdown-title')).toHaveText('画廊回归样例 1')
})

test('shows the selected outfit analysis directly after the gallery without duplicate post cards', async ({ page }) => {
  const gallery = page.getByRole('region', { name: '趋势穿搭画廊' })
  await expect(gallery).toBeVisible()
  await expect(page.locator('.discovery-look')).toHaveCount(0)
  expect(await gallery.evaluate(element => element.closest('.trend-gallery').nextElementSibling?.classList.contains('trend-breakdown'))).toBe(true)
  await gallery.getByRole('button', { name: '查看第 2 张图片：画廊回归样例 2' }).click()
  await expect(page.locator('#trend-breakdown-title')).toHaveText('画廊回归样例 2')
})
