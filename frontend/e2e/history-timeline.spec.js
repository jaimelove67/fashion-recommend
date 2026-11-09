import { expect, test } from '@playwright/test'

for (const width of [1280, 320]) {
  test(`history timeline shows daily saved and unsaved totals at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 })
    await page.clock.setFixedTime(new Date('2026-11-06T12:00:00'))
    const records = [
      ...Array.from({ length: 12 }, (_, index) => ({
        id: index + 1, generatedAt: '2026-11-05T12:00:00', saved: index < 7, items: []
      })),
      { id: 13, generatedAt: '2026-11-06T12:00:00', saved: true, items: [] },
      { id: 14, generatedAt: '2026-11-06T12:00:00', saved: false, items: [] }
    ]
    await page.route('**/api/**', route => {
      const path = new URL(route.request().url()).pathname
      let data = {}
      if (path.endsWith('/auth/me')) data = { username: 'timeline-user', authorities: ['ROLE_USER'] }
      else if (path.endsWith('/auth/csrf')) data = { token: 'test', headerName: 'X-XSRF-TOKEN' }
      else if (path.endsWith('/wardrobe')) data = []
      else if (path.endsWith('/style-profile')) data = null
      else if (path.endsWith('/recommendations')) data = { content: records, totalElements: records.length, page: 0, hasNext: false }
      return route.fulfill({ contentType: 'application/json', body: JSON.stringify({ code: 0, data }) })
    })
    await page.goto('/#history')
    const timeline = page.locator('.history-timeline')
    await expect(timeline.locator('li')).toHaveCount(7)
    await expect(timeline.locator('li').nth(0)).toHaveAttribute('aria-label', /共 0 套，已收藏 0 套，未收藏 0 套/)
    await expect(timeline.locator('li').nth(5)).toHaveAttribute('aria-label', /共 12 套，已收藏 7 套，未收藏 5 套/)
    await expect(timeline.locator('li').nth(6)).toHaveAttribute('aria-label', /共 2 套，已收藏 1 套，未收藏 1 套/)
    await expect(timeline.locator('li').nth(5).locator('.timeline-dot')).toHaveCount(2)
    const bounds = await timeline.evaluate(element => ({ scrollWidth: element.scrollWidth, clientWidth: element.clientWidth }))
    expect(bounds.scrollWidth).toBeLessThanOrEqual(bounds.clientWidth)
    await timeline.screenshot({ path: test.info().outputPath(`timeline-${width}.png`) })
  })
}
