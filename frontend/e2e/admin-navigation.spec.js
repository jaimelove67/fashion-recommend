import { expect, test } from '@playwright/test'

for (const width of [1280, 390]) {
  test(`administrator logs in to management and can return to the user side at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 850 })
    await page.emulateMedia({ reducedMotion: 'reduce' })
    let signedIn = false
    await page.route('**/api/**', route => {
      const path = new URL(route.request().url()).pathname
      let data = {}
      if (path.endsWith('/auth/login')) { signedIn = true; data = { username: 'admin-test', authorities: ['ROLE_ADMIN'] } }
      else if (path.endsWith('/auth/me') && !signedIn) return route.fulfill({ status: 401, contentType: 'application/json', body: JSON.stringify({ code: 401, message: '请先登录' }) })
      else if (path.endsWith('/auth/me')) data = { username: 'admin-test', authorities: ['ROLE_ADMIN'] }
      else if (path.endsWith('/auth/csrf')) data = { token: 'test', headerName: 'X-XSRF-TOKEN' }
      else if (path.endsWith('/trends')) data = { items: [], sources: [], styles: [] }
      else if (path.endsWith('/wardrobe') || path.endsWith('/ai-models')) data = []
      else if (path.endsWith('/style-profile')) data = null
      else if (path.endsWith('/users') || path.endsWith('/feedback') || path.endsWith('/audit-logs')) data = { items: [], totalElements: 0, hasNext: false }
      else if (path.endsWith('/recommendations')) data = { content: [], totalElements: 0, hasNext: false }
      return route.fulfill({ contentType: 'application/json', body: JSON.stringify({ code: 0, data }) })
    })
    await page.goto('/#recommend')
    await page.getByRole('button', { name: '登录 / 注册', exact: true }).click()
    await page.getByRole('button', { name: '直接登录', exact: true }).click()
    await page.locator('#fish-username').fill('admin-test')
    await page.locator('#fish-password').fill('test-password')
    await page.getByRole('button', { name: '登录知己', exact: true }).click()
    await expect(page).toHaveURL(/#admin$/)
    await expect(page.getByRole('heading', { name: '运营总览', exact: true })).toBeVisible()
    await expect(page.locator('.pill-nav-list')).not.toContainText('穿搭推荐')
    await page.getByRole('button', { name: '返回用户端' }).click()
    await expect(page).toHaveURL(/#recommend$/)
    if (width > 760) await expect(page.locator('.pill-nav-list')).toContainText('穿搭推荐')
    await page.reload()
    await expect(page).toHaveURL(/#recommend$/)
    await page.evaluate(() => { location.hash = '#admin' })
    await expect(page.getByRole('heading', { name: '运营总览', exact: true })).toBeVisible()
  })
}

test('ordinary users keep their requested page and cannot enter the admin page', async ({ page }) => {
  await page.route('**/api/**', route => {
    const path = new URL(route.request().url()).pathname
    let data = {}
    if (path.endsWith('/auth/me')) data = { username: 'ordinary-user', authorities: ['ROLE_USER'] }
    else if (path.endsWith('/auth/csrf')) data = { token: 'test', headerName: 'X-XSRF-TOKEN' }
    else if (path.endsWith('/trends')) data = { items: [], sources: [], styles: [] }
    else if (path.endsWith('/wardrobe')) data = []
    else if (path.endsWith('/style-profile')) data = null
    else if (path.endsWith('/recommendations')) data = { content: [], totalElements: 0, hasNext: false }
    return route.fulfill({ contentType: 'application/json', body: JSON.stringify({ code: 0, data }) })
  })
  await page.goto('/#wardrobe')
  await expect(page.locator('.pill-nav-list')).toContainText('我的衣橱')
  await expect(page).toHaveURL(/#wardrobe$/)
  await page.evaluate(() => { location.hash = '#admin' })
  await expect(page).toHaveURL(/#recommend$/)
  await expect(page.locator('.pill-nav-list')).not.toContainText('管理后台')
})
