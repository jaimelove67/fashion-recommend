import { expect, test } from '@playwright/test'

const svg = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 300 400"><rect width="300" height="400" fill="#b8ccb8"/><path d="M60 350 115 80h70l55 270" fill="#315b49"/></svg>'
const trends = Array.from({ length: 10 }, (_, index) => ({
  id: `gallery-${index + 1}`,
  platform: 'editorial',
  title: `画廊回归样例 ${index + 1}`,
  topicTags: [`回归标签 ${index + 1}`],
  imageUrl: `https://images.test/gallery-${index + 1}.svg`,
  summary: `来源摘要 ${index + 1}`,
  publishedAt: '2026-09-21T09:00:00+08:00',
  evidence: { mediaType: 'post', images: [] }
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
  const clickedCard = gallery.getByRole('button', { name: '查看第 2 条：画廊回归样例 2' })
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
  await expect(gallery.locator('.gallery-card[aria-current="true"]')).toHaveAttribute('aria-label', '查看第 2 条：画廊回归样例 2')
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

test('shows five mostly visible display-size gallery images on desktop', async ({ page }) => {
  const gallery = page.getByRole('region', { name: '趋势穿搭画廊' })
  const cards = gallery.locator('.gallery-card')
  await expect(cards).toHaveCount(5)
  const centerImage = gallery.locator('.gallery-card[data-gallery-index="0"] .gallery-card-image img')
  await expect(centerImage).toBeVisible()
  const centerImageBounds = await centerImage.boundingBox()
  expect(centerImageBounds.width).toBeGreaterThanOrEqual(260)

  const galleryBounds = await gallery.boundingBox()
  const cardBounds = await cards.evaluateAll((elements) => elements.map((element) => {
    const bounds = element.getBoundingClientRect()
    return { x: bounds.x, width: bounds.width }
  }))
  for (const bounds of cardBounds) {
    const visibleWidth = Math.max(
      0,
      Math.min(bounds.x + bounds.width, galleryBounds.x + galleryBounds.width)
        - Math.max(bounds.x, galleryBounds.x)
    )
    expect(visibleWidth / bounds.width).toBeGreaterThanOrEqual(0.8)
  }
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
