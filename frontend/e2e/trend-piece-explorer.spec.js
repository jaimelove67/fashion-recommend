import { expect, test } from '@playwright/test'

const trends = [
  {
    id: 'weibo:raw-denim',
    platform: 'weibo',
    title: '黑色原牛搭白衬衫',
    summary: '日常穿搭分享',
    topicTags: ['丹宁'],
    sourceUrl: 'https://example.com/raw-denim',
    publishedAt: '2026-09-21T09:00:00+08:00',
    evidence: { mediaType: 'post', images: [] }
  },
  {
    id: 'douyin:raw-denim',
    platform: 'douyin',
    title: '黑原牛和乐福鞋',
    summary: '第二条单品线索',
    topicTags: ['复古'],
    sourceUrl: 'https://example.com/second-look',
    publishedAt: '2026-09-21T10:00:00+08:00',
    evidence: { mediaType: 'post', images: [] }
  },
  {
    id: 'weibo:generic-denim',
    platform: 'weibo',
    title: '丹宁风格穿搭',
    summary: '只谈风格，没有具体单品',
    topicTags: ['丹宁'],
    sourceUrl: 'https://example.com/denim-style',
    publishedAt: '2026-09-21T11:00:00+08:00',
    evidence: { mediaType: 'post', images: [] }
  },
  {
    id: 'weibo:sweater',
    platform: 'weibo',
    title: '把童装改造成成人穿的熊图案毛衣',
    summary: '针织单品改造分享',
    topicTags: ['针织', '秋季穿搭'],
    publishedAt: '2026-09-23T09:00:00+08:00',
    stale: false,
    evidence: { mediaType: 'post', images: [] }
  },
  {
    id: 'douyin:tights',
    platform: 'douyin',
    title: '光腿神器穿搭测评：连脚款还是踩脚款',
    summary: '对比两种打底袜款式',
    topicTags: ['打底袜', '穿搭测评'],
    publishedAt: '2026-09-23T10:00:00+08:00',
    stale: false,
    evidence: { mediaType: 'video', images: [] }
  }
]

test('derives items from social trends and shows item-only images with seasonal pairings', async ({ page }) => {
  await page.route('**/api/**', async (route) => {
    const pathname = new URL(route.request().url()).pathname
    let data = {}
    if (pathname === '/api/v1/auth/csrf') data = { token: 'qa', headerName: 'X-XSRF-TOKEN' }
    else if (pathname === '/api/v1/auth/me') data = { username: 'piece-qa', authorities: [] }
    else if (pathname === '/api/v1/trends') {
      const platform = new URL(route.request().url()).searchParams.get('platform')
      data = { items: trends.filter((item) => !platform || item.platform === platform), styles: [], fetchedAt: '2026-09-21T11:00:00+08:00', demoMode: false, sources: [] }
    } else if (pathname === '/api/v1/me/wardrobe') data = []
    else if (pathname === '/api/v1/me/recommendations') data = { content: [], totalElements: 0, page: 0, hasNext: false }
    else if (pathname === '/api/v1/me/style-profile') data = null

    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 0, message: 'ok', data }) })
  })

  await page.goto('/#trend')
  const explorer = page.getByRole('region', { name: '趋势单品解析' })
  await expect(explorer).toBeVisible()
  await expect(explorer.getByRole('button', { name: /黑色原牛.*查看搭配/ })).toBeVisible()
  await expect(explorer.getByRole('button', { name: /白衬衫.*查看搭配/ })).toBeVisible()
  await expect(explorer.getByRole('button', { name: /毛衣.*查看搭配/ })).toBeVisible()
  await expect(explorer.getByRole('button', { name: /光腿神器.*查看搭配/ })).toBeVisible()
  await expect(explorer.getByRole('img', { name: '黑色原牛单品图' })).toBeVisible()
  await expect.poll(() => explorer.getByRole('img', { name: '黑色原牛单品图' }).evaluate((image) => image.naturalWidth)).toBeGreaterThan(0)
  const photoFrame = await explorer.locator('.identity-photo').boundingBox()
  const detailImage = await explorer.getByRole('img', { name: '黑色原牛单品图' }).boundingBox()
  expect(detailImage.x).toBeGreaterThanOrEqual(photoFrame.x - 1)
  expect(detailImage.x + detailImage.width).toBeLessThanOrEqual(photoFrame.x + photoFrame.width + 1)
  expect(detailImage.y).toBeGreaterThanOrEqual(photoFrame.y - 1)
  expect(detailImage.y + detailImage.height).toBeLessThanOrEqual(photoFrame.y + photoFrame.height + 1)
  await expect(explorer.getByRole('img', { name: /穿搭参考图/ })).toHaveCount(0)
  await expect(explorer.getByText('只谈风格，没有具体单品')).toHaveCount(0)
  await expect(explorer.getByText('相关来源')).toHaveCount(0)

  await explorer.getByRole('button', { name: /白衬衫.*查看搭配/ }).click()
  await expect(explorer.locator('#piece-detail')).toContainText('白衬衫')
  await expect(explorer.locator('#piece-detail')).toContainText('直筒牛仔裤')
  await expect(explorer.getByRole('img', { name: '白衬衫单品图' })).toBeVisible()

  await explorer.getByRole('button', { name: /乐福鞋.*查看搭配/ }).click()
  await expect(explorer.getByRole('img', { name: /穿搭参考图/ })).toHaveCount(0)

  await explorer.getByRole('button', { name: '裤装', exact: true }).click()
  await expect(explorer.getByRole('button', { name: /白衬衫.*查看搭配/ })).toHaveCount(0)
  await expect(explorer.locator('#piece-detail')).toContainText('长袖衬衫')
  await expect(explorer.locator('#piece-detail')).toContainText('腰围略松时')
  await expect(explorer.locator('#piece-detail')).toContainText('新黑色原牛可能发生摩擦掉色')
  await expect(explorer.getByRole('img', { name: '黑色原牛单品图' })).toBeVisible()

  await page.getByLabel('内容来源').selectOption('douyin')
  await expect(page.locator('.trend-count')).toContainText('2 条')
  await expect(explorer.getByRole('button', { name: /毛衣.*查看搭配/ })).toHaveCount(0)
  await expect(explorer.getByRole('button', { name: /黑色原牛.*查看搭配/ })).toBeVisible()
  await expect(explorer.locator('#piece-detail')).toContainText('黑色原牛')
})
