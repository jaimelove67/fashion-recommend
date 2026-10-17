import { expect, test } from '@playwright/test'

const svg = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 300 500"><rect width="300" height="500" fill="#e9e6dd"/><rect x="90" y="70" width="120" height="180" fill="#315b49"/><rect x="90" y="250" width="50" height="210" fill="#525f68"/><rect x="160" y="250" width="50" height="210" fill="#525f68"/></svg>'
const trends = Array.from({ length: 6 }, (_, index) => ({
  id: `analysis-feed-${index}`, platform: 'weibo', title: `近期穿搭 ${index + 1}`,
  imageUrl: `https://images.test/analysis-${index}.svg`, topicTags: ['日常'],
  summary: `这条原帖的摘要 ${index + 1}`, publishedAt: '2026-10-15T09:00:00+08:00',
  sourceUrl: `https://weibo.com/qa/post-${index}`, evidence: { author: '测试来源', mediaType: 'image' }
}))

async function openTrends(page, width = 1280) {
  await page.setViewportSize({ width, height: 900 })
  await page.clock.install()
  await page.route('https://images.test/**', route => route.fulfill({ contentType: 'image/svg+xml', body: svg }))
  await page.route('**/api/**', async route => {
    const pathname = new URL(route.request().url()).pathname
    let data = {}
    if (pathname.endsWith('/auth/csrf')) data = { token: 'analysis-qa', headerName: 'X-XSRF-TOKEN' }
    else if (pathname.endsWith('/auth/me')) data = { username: 'analysis-qa', authorities: [] }
    else if (pathname.endsWith('/trends')) data = { items: trends, styles: [], sources: [], demoMode: false }
    else if (pathname.endsWith('/me/wardrobe')) data = [
      { id: 'white-tee', name: '我的白色上衣', category: '上衣', color: '白色', recognitionStatus: 'CONFIRMED' },
      { id: 'review-tee', name: '未确认的白色衣服', color: '白色', recognitionStatus: 'NEEDS_MANUAL_REVIEW' }
    ]
    else if (pathname.endsWith('/me/recommendations')) data = { content: [], totalElements: 0, hasNext: false }
    else if (pathname.endsWith('/me/style-profile')) data = null
    await route.fulfill({ contentType: 'application/json', body: JSON.stringify({ code: 0, data }) })
  })
  await page.goto('/#trend')
  await expect(page.locator('#trend-breakdown-title')).toHaveText(trends[0].title)
}

async function openExample(page) {
  await page.getByRole('button', { name: '分析示例', exact: true }).click()
  await expect(page.locator('.trend-example-notice')).toContainText('不计入近期鉴赏')
  await expect(page.locator('#trend-gallery-title')).toHaveText('穿搭分析示例')
  await expect(page.locator('.brief-points > li')).toHaveCount(3)
  await page.getByRole('button', { name: '深入分析', exact: true }).click()
  return page.getByRole('dialog', { name: '深入分析', exact: true })
}

test('gallery selection updates a brief; deep reading freezes the track and restores focus and position on close', async ({ page }) => {
  await openTrends(page)
  await page.getByRole('button', { name: '查看第 2 张图片：近期穿搭 2' }).click()
  await expect(page.locator('#trend-breakdown-title')).toHaveText('近期穿搭 2')
  await expect(page.getByRole('dialog')).toHaveCount(0)
  const open = page.getByRole('button', { name: '深入分析', exact: true })
  await open.scrollIntoViewIfNeeded()
  const scrollBefore = await page.evaluate(() => window.scrollY)
  await open.click()
  const dialog = page.getByRole('dialog', { name: '深入分析', exact: true })
  await expect(dialog).toBeVisible()
  await expect(dialog).toContainText('这条原帖的摘要 2')
  await expect(dialog).toContainText('暂无这条穿搭的深入解读')
  await expect(dialog.locator('.analysis-pin')).toHaveCount(0)
  // Vue keeps outgoing cards mounted until their selection transition finishes.
  const track = page.locator('.gallery-card:not(.gallery-card-leave-active)')
  const trackBefore = await track.evaluateAll(cards => cards.map(card => card.dataset.galleryIndex).join(','))
  await page.clock.fastForward(6500)
  expect(await track.evaluateAll(cards => cards.map(card => card.dataset.galleryIndex).join(','))).toBe(trackBefore)
  await expect(page.getByRole('button', { name: '关闭深入分析', exact: true })).toBeFocused()
  await page.keyboard.press('Shift+Tab')
  await expect(dialog.getByRole('button', { name: '用我的衣橱搭一套', exact: true })).toBeFocused()
  await page.keyboard.press('Tab')
  await expect(dialog.getByRole('button', { name: '关闭深入分析', exact: true })).toBeFocused()
  await page.keyboard.press('Escape')
  await expect(dialog).toHaveCount(0)
  await expect(open).toBeFocused()
  expect(await page.evaluate(() => document.documentElement.style.overflow)).toBe('')
  expect(await page.evaluate(() => window.scrollY)).toBeCloseTo(scrollBefore, 0)
  await expect(page.locator('#trend-breakdown-title')).toHaveText('近期穿搭 2')
  await page.clock.fastForward(2200)
  expect(await track.evaluateAll(cards => cards.map(card => card.dataset.galleryIndex).join(','))).not.toBe(trackBefore)
})

test('verified examples show image-linked reasons, independent substitutions, both comparison photos and the wardrobe route', async ({ page }) => {
  await openTrends(page)
  const errors = []
  page.on('pageerror', error => errors.push(error.message))
  let dialog = await openExample(page)
  await expect(dialog.locator('.analysis-photo img')).toHaveAttribute('src', '/assets/trend-analysis/savis-autumn.jpg')
  await expect.poll(() => dialog.locator('.analysis-photo img').evaluate(image => image.complete && image.naturalWidth > 0)).toBe(true)
  await expect(dialog).toContainText('2026/9/26')
  await dialog.getByRole('button', { name: '查看分析点 2：系衣提供分界，也增加腰间体积' }).click()
  await expect(dialog.locator('#analysis-point-content')).toContainText('不能由此承诺显瘦或显高')
  await dialog.getByRole('button', { name: '白内搭换深色', exact: true }).click()
  await expect(dialog.locator('#analysis-change-result')).toContainText('胸前对比减弱')
  await dialog.getByRole('button', { name: '取下腰间系衣', exact: true }).click()
  await expect(dialog.locator('#analysis-change-result')).toContainText('少一条浅色横带')
  await expect(dialog.locator('.analysis-wardrobe-matches')).toContainText('我的白色上衣')
  await expect(dialog.locator('.analysis-wardrobe-matches')).not.toContainText('未确认的白色衣服')
  await dialog.getByRole('button', { name: '返回画廊', exact: true }).click()
  await page.locator('.gallery-card').filter({ has: page.getByAltText('同一件马甲，颜色分界怎样改变重点？') }).click()
  await expect(page.locator('#trend-breakdown-title')).toHaveText('同一件马甲，颜色分界怎样改变重点？')
  await page.getByRole('button', { name: '深入分析', exact: true }).click()
  dialog = page.getByRole('dialog', { name: '深入分析', exact: true })
  await dialog.getByRole('button', { name: 'B · 近似色裤与白色内搭', exact: true }).click()
  await expect(dialog.locator('.analysis-photo img')).toHaveAttribute('src', '/assets/trend-analysis/sanmu-vest-b.jpg')
  await expect.poll(() => dialog.locator('.analysis-photo img').evaluate(image => image.complete && image.naturalWidth > 0)).toBe(true)
  await dialog.getByRole('button', { name: '用我的衣橱搭一套', exact: true }).click()
  await expect(page).toHaveURL(/#recommend$/)
  await expect(page.locator('.trend-reference-banner')).toContainText('同一件马甲，颜色分界怎样改变重点？')
  await expect(page.getByRole('dialog')).toHaveCount(0)
  expect(await page.evaluate(() => document.documentElement.style.overflow)).toBe('')
  expect(errors).toEqual([])
})

test('opening the wardrobe from analysis releases the dialog and page scroll lock', async ({ page }) => {
  await openTrends(page)
  const dialog = await openExample(page)
  await dialog.getByRole('button', { name: '查看我的衣橱', exact: true }).click()
  await expect(page).toHaveURL(/#wardrobe$/)
  await expect(page.getByRole('dialog')).toHaveCount(0)
  expect(await page.evaluate(() => document.documentElement.style.overflow)).toBe('')
})

test('unavailable source images preserve the source link and do not create image analysis', async ({ page }) => {
  await openTrends(page)
  await page.route('https://images.test/**', route => route.abort())
  await page.reload()
  await page.getByRole('button', { name: '深入分析', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '深入分析', exact: true })
  await expect(dialog.locator('.analysis-no-image')).toContainText('原帖配图暂不可用')
  await expect(dialog.locator('.analysis-source-link')).toHaveAttribute('href', trends[0].sourceUrl)
  await expect(dialog).toContainText('暂无这条穿搭的深入解读')
  await expect(dialog.locator('.analysis-point-content')).toHaveCount(0)
  await dialog.getByRole('button', { name: '返回画廊', exact: true }).click()
  expect(await page.evaluate(() => document.documentElement.style.overflow)).toBe('')
})

for (const width of [390, 320]) test(`analysis is a full-screen readable detail with functional photo markers at ${width}px`, async ({ page }) => {
  await openTrends(page, width)
  const dialog = await openExample(page)
  const bounds = await dialog.boundingBox()
  expect(bounds.x).toBe(0)
  expect(bounds.y).toBe(0)
  expect(bounds.width).toBe(width)
  expect(bounds.height).toBe(900)
  await dialog.getByRole('button', { name: '查看分析点 2：系衣提供分界，也增加腰间体积' }).click()
  await expect(dialog.locator('#analysis-point-content h4')).toBeInViewport()
  expect(await dialog.evaluate(element => element.scrollWidth <= element.clientWidth)).toBe(true)
  expect(await dialog.locator('.analysis-scroll').evaluate(element => element.scrollWidth <= element.clientWidth)).toBe(true)
  await expect(dialog.getByRole('button', { name: '用我的衣橱搭一套', exact: true })).toBeInViewport()
  await dialog.getByRole('button', { name: '关闭深入分析', exact: true }).click()
  await expect(page.getByRole('button', { name: '深入分析', exact: true })).toBeFocused()
})
