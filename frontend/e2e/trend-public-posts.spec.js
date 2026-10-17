import { expect, test } from '@playwright/test'

const svg = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 300 533"><rect width="300" height="533" fill="#b8ccb8"/><path d="M112 86h76l24 212h-124z" fill="#315b49"/></svg>'

for (const { count, width } of [{ count: 1, width: 1280 }, { count: 4, width: 1280 }, { count: 4, width: 390 }]) {
test(`restores the 3D gallery for ${count} image posts without full-body markers at ${width}px`, async ({ page }) => {
  await page.setViewportSize({ width, height: 900 })
  const items = [{ id: 'ordinary-knit-post', platform: 'weibo', title: '日常针织衫穿搭参考',
    summary: '针织衫日常搭配', publishedAt: '2026-10-03T05:13:15+08:00', topicTags: ['针织'],
    sourceUrl: 'https://weibo.com/2/detail/5349801751216296', imageUrl: 'https://images.test/knit.jpg',
    evidence: { mediaType: 'image', images: ['https://images.test/knit.jpg'], fullBodyImageUrl: null } }]
  while (items.length < count) {
    const imageUrl = `https://images.test/knit-${items.length}.jpg`
    items.push({ ...items[0], id: `knit-${items.length}`, title: `日常针织穿搭 ${items.length}`,
      imageUrl, evidence: { mediaType: 'image', images: [imageUrl], fullBodyImageUrl: null } })
  }
  const feed = { data: { items, styles: [], demoMode: false, sources: [] } }
  await page.route('**/api/**', async route => {
    const path = new URL(route.request().url()).pathname
    let data = {}
    if (path === '/api/v1/auth/me') data = { username: 'trend-regression', authorities: [] }
    else if (path === '/api/v1/auth/csrf') data = { token: 'qa', headerName: 'X-XSRF-TOKEN' }
    else if (path === '/api/v1/trends') data = { ...feed.data, items }
    else if (path === '/api/v1/me/wardrobe') data = []
    else if (path === '/api/v1/me/recommendations') data = { content: [], totalElements: 0 }
    else if (path === '/api/v1/me/style-profile') data = null
    await route.fulfill({ contentType: 'application/json', body: JSON.stringify({ code: 0, data }) })
  })
  await page.route('https://images.test/**', route => route.fulfill({ contentType: 'image/svg+xml', body: svg }))
  await page.goto('/#trend')
  const gallery = page.getByRole('region', { name: '穿搭鉴赏画廊' })
  const cards = gallery.locator('.gallery-card')
  await expect(gallery).toBeVisible()
  await expect(gallery).toHaveAttribute('aria-roledescription', '3D 画廊')
  await expect(cards).toHaveCount(Math.min(count, width <= 1100 ? 3 : 5))
  await expect(cards.first()).toHaveAttribute('aria-setsize', String(count))
  await expect.poll(() => cards.locator('img').evaluateAll(images => images.every(image => image.naturalWidth > 0))).toBe(true)
  await expect(page.locator('.discovery-look')).toHaveCount(0)
  await expect(page.locator('#trend-breakdown-title')).toHaveText(items[0].title)
  await expect(page.locator('.trend-breakdown .source-link')).toHaveAttribute('href', items[0].sourceUrl)
  if (count > 1) {
    const secondCard = gallery.getByRole('button', { name: `查看第 2 张图片：${items[1].title}` })
    await secondCard.click()
    await expect(secondCard).toHaveAttribute('data-gallery-center', 'true')
    await expect(page.locator('#trend-breakdown-title')).toHaveText(items[1].title)
  }
  await expect(page.locator('.trend-empty-state')).toHaveCount(0)
})

}
