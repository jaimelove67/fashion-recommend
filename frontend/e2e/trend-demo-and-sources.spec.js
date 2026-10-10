import { expect, test } from '@playwright/test'

const demoItems = [
  {
    id: 'demo-urban',
    platform: 'configured-demo',
    title: '城市日常演示穿搭',
    topicTags: ['日常穿搭'],
    imageUrl: '/assets/look-urban.jpg',
    sourceUrl: '/',
    stale: true,
    evidence: { mediaType: 'post' }
  },
  {
    id: 'demo-tailoring',
    platform: 'configured-demo',
    title: '利落通勤演示穿搭',
    topicTags: ['通勤'],
    imageUrl: '/assets/look-tailoring.jpg',
    evidence: { mediaType: 'post' }
  }
]

test('shows only the daily range without demo banners and preserves reference images', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 900 })
  await page.route('**/api/**', async (route) => {
    const pathname = new URL(route.request().url()).pathname
    let data = {}
    if (pathname === '/api/v1/auth/csrf') data = { token: 'qa-demo', headerName: 'X-XSRF-TOKEN' }
    else if (pathname === '/api/v1/auth/me') data = { username: 'demo-qa', authorities: [] }
    else if (pathname === '/api/v1/trends') {
      expect(new URL(route.request().url()).searchParams.get('period')).toBe('day')
      data = {
        items: demoItems,
        styles: [],
        fetchedAt: '2026-09-29T09:00:00+08:00',
        demoMode: true,
        primarySource: 'configured-demo',
        sources: [
          {
            id: 'douyin',
            state: 'stale',
            message: '最近 7 天无新内容',
            itemCount: 3,
            eligibleCount: 0,
            exclusionReason: 'outside-window'
          },
          { id: 'configured-demo', state: 'ready', itemCount: 2, eligibleCount: 2 }
        ]
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

  await expect(page.locator('.trend-demo-banner')).toHaveCount(0)
  await expect(page.locator('.demo-notice')).toHaveCount(0)
  await expect(page.locator('.updated')).toHaveText('每日 12:00 更新（北京时间） · 穿搭参考')
  await expect(page.locator('.period-switch button')).toHaveCount(1)
  await expect(page.locator('.period-switch button')).toHaveText('近 24 小时')
  await expect(page.locator('.period-switch button')).toHaveAttribute('aria-pressed', 'true')
  await expect(page.getByRole('button', { name: '近 7 天', exact: true })).toHaveCount(0)
  await expect(page.locator('.discovery-filters select')).toBeDisabled()

  const gallery = page.getByRole('region', { name: '趋势穿搭画廊' })
  await expect(gallery.locator('.gallery-card')).toHaveCount(2)
  await expect(gallery.locator('.gallery-card-image img').first()).toHaveAttribute('src', '/assets/look-urban.jpg')
  await expect(page.locator('.trend-breakdown .source-link')).toHaveCount(0)
  await expect(page.getByText('等待更新', { exact: false })).toHaveCount(0)

  await page.locator('.trend-source-details summary').click()
  const sources = page.locator('.trend-source-details')
  await expect(sources).toContainText('已过期')
  await expect(sources).toContainText('采集 3 条 / 可展示 0 条')
  await expect(sources).toContainText('原因：超出时间范围')
  await expect(sources).toContainText('采集 2 条 / 可展示 2 条')
})
