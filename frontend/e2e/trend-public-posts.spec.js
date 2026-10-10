import { expect, test } from '@playwright/test'

for (const count of [1, 4]) {
test(`shows all ${count} recent image posts without verified full-body gallery images`, async ({ page }) => {
  const items = [{ id: 'ordinary-knit-post', platform: 'weibo', title: '日常针织衫穿搭参考',
    summary: '针织衫日常搭配', publishedAt: '2026-10-03T05:13:15+08:00', topicTags: ['针织'],
    sourceUrl: 'https://weibo.com/2/detail/5349801751216296', imageUrl: 'https://images.test/knit.jpg',
    evidence: { mediaType: 'image', images: ['https://images.test/knit.jpg'], fullBodyImageUrl: null } }]
  while (items.length < count) items.push({ ...items[0], id: `knit-${items.length}`, title: `日常针织穿搭 ${items.length}` })
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
  await page.route('https://**/*', route => route.abort())
  await page.goto('/#trend')
  await expect(page.locator('.discovery-look')).toHaveCount(count)
  await expect(page.locator('.discovery-look').first()).toContainText(items[0].title)
  await expect(page.locator('.discovery-look').first()).toContainText('图片加载失败，可查看原文')
  await expect(page.locator('.discovery-look a').first()).toHaveAttribute('href', items[0].sourceUrl)
  await expect(page.locator('.trend-gallery')).toHaveCount(0)
  await expect(page.locator('.trend-empty-state')).toHaveCount(0)
})

}
