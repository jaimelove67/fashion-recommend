import { expect, test } from '@playwright/test'

const imageUrl = 'https://wx3.sinaimg.cn/thumbnail/qalook.jpg'
const largeImageUrl = '/trend-images/wx3/large/qalook.jpg'
const svg = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 300 533"><rect width="300" height="533" fill="#dfe7df"/><circle cx="150" cy="55" r="24" fill="#decab6"/><path d="M112 86h76l24 212h-124z" fill="#315b49"/><path d="M117 298h28l-8 215h-28zm38 0h28l8 215h-28z" fill="#3a4146"/></svg>'

test('keeps a verified trend image visible when the thumbnail URL fails', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 900 })
  await page.route('**/trend-images/wx3/thumbnail/**', (route) => route.abort())
  await page.route('**/trend-images/wx3/large/**', (route) => route.fulfill({
    status: 200,
    contentType: 'image/svg+xml',
    body: svg
  }))
  await page.route('**/api/**', async (route) => {
    const pathname = new URL(route.request().url()).pathname
    let data = {}
    if (pathname === '/api/v1/auth/csrf') data = { token: 'qa', headerName: 'X-XSRF-TOKEN' }
    else if (pathname === '/api/v1/auth/me') data = { username: 'gallery-image-qa', authorities: [] }
    else if (pathname === '/api/v1/trends') {
      data = {
        items: [{
          id: 'sina-thumbnail-fallback',
          platform: 'weibo',
          title: '新浪图片回退样例',
          topicTags: ['日常穿搭'],
          imageUrl,
          evidence: { mediaType: 'post', images: [imageUrl], fullBodyImageUrl: imageUrl }
        }],
        styles: [],
        fetchedAt: '2026-09-28T09:00:00+08:00',
        demoMode: true,
        sources: []
      }
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

  const gallery = page.getByRole('region', { name: '趋势穿搭画廊' })
  await expect(gallery.locator('.gallery-card')).toHaveCount(1)
  await expect(gallery.locator('.gallery-card-image img')).toHaveAttribute('src', largeImageUrl)
  await expect.poll(() => gallery.locator('.gallery-card-image img').evaluate((image) => image.naturalWidth)).toBeGreaterThan(0)
})
