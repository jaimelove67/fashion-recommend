import { expect, test } from '@playwright/test'

const unreviewedTrend = {
  id: 'unreviewed-trend',
  platform: 'weibo',
  title: '没有核对图片的趋势样例',
  topicTags: ['日常穿搭'],
  imageUrl: 'https://images.test/unreviewed-cover.jpg',
  evidence: { mediaType: 'post', images: [] }
}
const sinaImageUrl = 'https://wx3.sinaimg.cn/thumbnail/qabothfail.jpg'
const sinaImageTrend = {
  id: 'sina-both-fail',
  platform: 'weibo',
  title: '新浪图片双候选失败样例',
  topicTags: ['日常穿搭'],
  imageUrl: sinaImageUrl,
  evidence: { mediaType: 'post', images: [sinaImageUrl], fullBodyImageUrl: sinaImageUrl }
}

async function stubTrendApi(page, items) {
  await page.setViewportSize({ width: 1280, height: 900 })
  await page.route('**/api/**', async (route) => {
    const pathname = new URL(route.request().url()).pathname
    let data = {}

    if (pathname === '/api/v1/auth/csrf') data = { token: 'qa-empty-trends', headerName: 'X-XSRF-TOKEN' }
    else if (pathname === '/api/v1/auth/me') data = { username: 'empty-trends-qa', authorities: [] }
    else if (pathname === '/api/v1/trends') {
      data = {
        items,
        styles: [],
        fetchedAt: '2026-09-29T09:00:00+08:00',
        demoMode: false,
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
}

test('shows an empty state without preset images when the trends API returns no items', async ({ page }) => {
  await stubTrendApi(page, [])

  await page.goto('/#trend')

  const emptyState = page.locator('.trend-empty-state')
  await expect(emptyState).toBeVisible()
  await expect(page.locator('.updated')).toContainText('每日 12:00 更新（北京时间）')
  await expect(page.locator('.updated')).toContainText('最近采集')
  const images = emptyState.locator('.trend-empty-images img')
  await expect(images).toHaveCount(0)
  await expect(emptyState).toContainText('当前筛选范围暂无穿搭内容')
})

test('shows source posts when trends have no verified gallery images', async ({ page }) => {
  await stubTrendApi(page, [unreviewedTrend])

  await page.goto('/#trend')

  const emptyState = page.locator('.trend-empty-state')
  await expect(emptyState).toHaveCount(0)
  await expect(page.locator('.discovery-look')).toContainText(unreviewedTrend.title)
  await expect(page.locator('.trend-gallery')).toHaveCount(0)
})

test('shows local references when all remote gallery image candidates fail', async ({ page }) => {
  await page.route('**/trend-images/wx3/large/**', (route) => route.abort())
  await page.route('**/trend-images/wx3/thumbnail/**', (route) => route.abort())
  await stubTrendApi(page, [sinaImageTrend])

  await page.goto('/#trend')

  const fallback = page.locator('.trend-gallery .gallery-empty-reference')
  await expect(fallback).toBeVisible()
  await expect(fallback).toContainText('非实时趋势内容')
  const images = fallback.locator('.gallery-empty-images img')
  await expect(images).toHaveCount(3)
  await expect.poll(() => images.evaluateAll((elements) => elements.filter((image) => image.complete && image.naturalWidth > 0).length)).toBe(3)
})
