import { expect, test } from '@playwright/test'

async function setup(page, { extension = null, clipboardFails = false } = {}) {
  const errors = []
  page.on('pageerror', error => errors.push(error.message))
  await page.addInitScript(({ extension, clipboardFails }) => {
    localStorage.setItem('fashion.weather.city', '长沙')
    if (clipboardFails) Object.defineProperty(navigator, 'clipboard', {
      value: { writeText: async () => { throw new Error('Clipboard unavailable in fixture') } }, configurable: true
    })
    if (!extension) return
    // Only the project/extension message boundary is simulated. The existing
    // cross-site-try-on suite exercises the real loaded extension separately.
    window.fixtureExtensionReady = extension === 'connected'
    let authorizedUntil = 0
    document.addEventListener('click', event => {
      if (event.isTrusted && event.target.closest('[data-zhiji-cross-site]')) authorizedUntil = Date.now() + 15000
    }, true)
    window.addEventListener('message', event => {
      if (event.source !== window || event.data?.channel !== 'ZHIJI_TRY_ON_WEB' || event.data.type !== 'CONNECT_AND_OPEN') return
      const ok = window.fixtureExtensionReady && Date.now() < authorizedUntil
      window.postMessage({ channel: 'ZHIJI_TRY_ON_EXTENSION', requestId: event.data.requestId, ok,
        data: ok ? { username: event.data.username } : undefined,
        error: ok ? undefined : '请在项目网页打开扩展，点击“连接此项目网站”，再开启试穿。'
      }, location.origin)
    })
  }, { extension, clipboardFails })
  await page.route('**/api/**', async route => {
    const path = new URL(route.request().url()).pathname
    let data = null
    if (path.endsWith('/auth/me')) data = { username: 'setup-owner', authorities: ['ROLE_USER'] }
    else if (path.endsWith('/auth/csrf')) data = { token: 'fixture-csrf', headerName: 'X-XSRF-TOKEN' }
    else if (path.endsWith('/wardrobe') || path.endsWith('/try-on-favorites')) data = []
    else if (path.endsWith('/style-profile')) data = { gender: null, styleTags: [], analysis: null }
    else if (path.endsWith('/trends')) data = { items: [], sources: [], styles: [] }
    else if (path.endsWith('/recommendations')) data = { content: [], page: 0, totalElements: 0, hasNext: false, statistics: { total: 0 } }
    else if (path.includes('/weather/')) data = { city: '长沙', temperatureC: 25, source: 'test' }
    await route.fulfill({ contentType: 'application/json', body: JSON.stringify({ code: 0, data }) })
  })
  await page.goto('/#trend')
  await expect(page.getByRole('button', { name: '开启跨站试穿', exact: true })).toBeVisible()
  return errors
}

function guide(page) { return page.getByRole('dialog', { name: '几步准备，开启试穿浮窗' }) }

test('an unavailable extension opens a download-first guide and failed connection cannot finish setup', async ({ page }) => {
  const errors = await setup(page)
  await page.getByRole('button', { name: '开启跨站试穿', exact: true }).click()
  const dialog = guide(page)
  await expect(dialog).toBeVisible()
  await expect(dialog.getByRole('heading', { name: '下载扩展压缩包' })).toBeVisible()
  const download = page.waitForEvent('download')
  await dialog.getByRole('link', { name: '下载扩展 ZIP', exact: true }).click()
  expect((await download).suggestedFilename()).toBe('zhiji-try-on-extension.zip')
  await expect(dialog.getByRole('heading', { name: '下载扩展压缩包' })).toBeVisible()
  await dialog.getByRole('button', { name: '已下载，下一步' }).click()
  await expect(dialog.getByRole('heading', { name: '解压下载的文件' })).toBeVisible()
  await dialog.getByRole('button', { name: '已解压，下一步' }).click()
  await dialog.getByRole('radio', { name: 'Edge', exact: true }).check()
  await expect(dialog.getByRole('textbox', { name: '扩展管理页地址' })).toHaveValue('edge://extensions')
  await expect(dialog).toContainText('加载解压缩的扩展')
  await dialog.getByRole('button', { name: '已加载扩展，下一步' }).click()
  await dialog.getByRole('button', { name: '连接并开启试穿' }).click()
  await expect(dialog.getByRole('alert')).toContainText('未检测到扩展连接')
  await expect(dialog.getByRole('heading', { name: '连接当前项目网站' })).toBeVisible()
  await expect(dialog.getByRole('button', { name: '完成', exact: true })).toHaveCount(0)
  await page.keyboard.press('Escape')
  await expect(dialog).toHaveCount(0)
  await expect(page.getByRole('button', { name: '开启跨站试穿', exact: true })).toBeFocused()
  expect(errors).toEqual([])
})

test('an installed but unbound extension starts at connection and advances only on a successful handshake', async ({ page }) => {
  const errors = await setup(page, { extension: 'unbound' })
  await page.getByRole('button', { name: '开启跨站试穿', exact: true }).click()
  const dialog = guide(page)
  await expect(dialog.getByRole('heading', { name: '连接当前项目网站' })).toBeVisible()
  await expect(dialog.getByRole('alert')).toContainText('连接此项目网站')
  await page.evaluate(() => { window.fixtureExtensionReady = true })
  await dialog.getByRole('button', { name: '连接并开启试穿' }).click()
  await expect(dialog.getByRole('heading', { name: '已连接，可以去购物网站试穿了' })).toBeVisible()
  await expect(dialog).toContainText('setup-owner')
  await dialog.getByRole('button', { name: '完成', exact: true }).click()
  await expect(dialog).toHaveCount(0)
  await expect(page.getByRole('status').filter({ hasText: '已开启。' })).toBeVisible()
  await page.getByRole('button', { name: '开启跨站试穿', exact: true }).click()
  await expect(dialog).toHaveCount(0)
  expect(errors).toEqual([])
})

test('mobile setup supports manual clipboard fallback, previous steps and already-installed users', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  const errors = await setup(page, { clipboardFails: true })
  await page.getByRole('button', { name: '首次使用怎么安装？' }).click()
  const dialog = guide(page)
  await dialog.getByRole('button', { name: '已下载，下一步' }).click()
  await dialog.getByRole('button', { name: '已解压，下一步' }).click()
  await expect(dialog.getByRole('textbox', { name: '扩展管理页地址' })).toHaveValue('chrome://extensions')
  await dialog.getByRole('button', { name: '复制地址' }).click()
  await expect(dialog.getByRole('status')).toContainText('请手动复制已选中的地址')
  await expect(dialog.getByRole('textbox', { name: '扩展管理页地址' })).toBeFocused()
  const width = await dialog.evaluate(element => ({ scroll: element.scrollWidth, client: element.clientWidth }))
  expect(width.scroll).toBeLessThanOrEqual(width.client)
  await dialog.screenshot({ path: '../output/anywear-integration/setup-mobile.png' })
  await dialog.getByRole('button', { name: '上一步' }).click()
  await dialog.getByRole('button', { name: '上一步' }).click()
  await dialog.getByRole('button', { name: '我已安装扩展，直接连接项目' }).click()
  await expect(dialog.getByRole('heading', { name: '连接当前项目网站' })).toBeVisible()
  await dialog.getByRole('button', { name: '关闭安装引导' }).click()
  await expect(dialog).toHaveCount(0)
  expect(errors).toEqual([])
})

test('a previous connection does not mark a new guide completed without a fresh check', async ({ page }) => {
  const errors = await setup(page, { extension: 'connected' })
  await page.getByRole('button', { name: '开启跨站试穿', exact: true }).click()
  await expect(page.getByRole('status').filter({ hasText: '已开启。' })).toBeVisible()
  await page.evaluate(() => { window.fixtureExtensionReady = false })
  await page.getByRole('button', { name: '首次使用怎么安装？' }).click()
  const dialog = guide(page)
  await expect(dialog.getByRole('heading', { name: '下载扩展压缩包' })).toBeVisible({ timeout: 3000 })
  await dialog.getByRole('button', { name: '我已安装扩展，直接连接项目' }).click()
  await dialog.getByRole('button', { name: '连接并开启试穿' }).click()
  await expect(dialog.getByRole('alert')).toContainText('连接此项目网站')
  await expect(dialog.getByRole('button', { name: '完成', exact: true })).toHaveCount(0)
  await page.evaluate(() => { window.fixtureExtensionReady = true })
  await dialog.getByRole('button', { name: '连接并开启试穿' }).click()
  await expect(dialog.getByRole('heading', { name: '已连接，可以去购物网站试穿了' })).toBeVisible()
  expect(errors).toEqual([])
})
