import { expect, test } from '@playwright/test'

for (const width of [1280, 390]) test(`stored references display without history or date labels at ${width}px`, async ({ page }) => {
  await page.setViewportSize({ width, height: 900 })
  const items = [1, 2].map(index => ({ id: `weibo:stored-${index}`, platform: 'weibo', title: `已收录的通勤穿搭 ${index}`,
    publishedAt: '2026-10-18T02:55:00Z', fetchedAt: '2026-10-18T05:38:32Z', stale: true,
    topicTags: ['通勤'], sourceUrl: `https://weibo.com/123456/abc${index}`, imageUrl: `https://images.example/old-${index}.jpg`,
    evidence: { author: '参考作者', mediaType: 'image', images: [`https://images.example/old-${index}.jpg`] } }))
  await page.route('https://images.example/**', route => route.fulfill({ contentType: 'image/svg+xml',
    body: '<svg xmlns="http://www.w3.org/2000/svg" width="300" height="400"><rect width="300" height="400" fill="#d6d3c9"/></svg>' }))
  await page.route('**/api/**', route => {
    const endpoint = new URL(route.request().url()).pathname
    let data = {}
    if (endpoint === '/api/v1/auth/csrf') data = { token: 'history-test', headerName: 'X-XSRF-TOKEN' }
    else if (endpoint === '/api/v1/auth/me') data = { username: 'history-test', authorities: [] }
    else if (endpoint === '/api/v1/trends') data = { items, sources: [], styles: [], historicalFallback: true, demoMode: false, dailySelection: null }
    else if (endpoint === '/api/v1/outfit-techniques') data = { items: [], status: 'disabled' }
    else if (endpoint === '/api/v1/me/wardrobe') data = []
    else if (endpoint === '/api/v1/me/recommendations') data = { content: [], totalElements: 0, hasNext: false }
    else if (endpoint === '/api/v1/me/style-profile') data = null
    return route.fulfill({ contentType: 'application/json', body: JSON.stringify({ code: 0, data }) })
  })
  await page.goto('/#trend')
  await expect(page.locator('.trend-count')).toHaveText('穿搭参考 2 套')
  await expect(page.locator('.trend-gallery')).toBeVisible()
  await expect(page.locator('.trend-empty-state')).toHaveCount(0)
  await expect(page.locator('.trend-daily-composition')).toHaveCount(0)
  await expect(page.locator('.trend-page')).not.toContainText('历史参考')
  await expect(page.locator('.trend-breakdown')).toHaveCount(0)
  await page.locator('.gallery-card').first().click()
  await expect(page.getByRole('dialog')).toBeVisible()
  await expect(page.locator('.post-author')).toContainText('参考作者')
  await expect(page.getByRole('dialog')).not.toContainText('2026')
  await expect(page.getByRole('link', { name: '查看原帖', exact: true }).last()).toHaveAttribute('href', /https:\/\/weibo.com\/123456\/abc[12]/)
  await page.keyboard.press('Escape')
  await expect(page.getByRole('dialog')).toHaveCount(0)
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth)).toBe(true)
})
