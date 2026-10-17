import { test as base, expect, chromium } from '@playwright/test'
import { mkdirSync, readdirSync, readFileSync, writeFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const ROOT = fileURLToPath(new URL('../../', import.meta.url))
const APP = process.env.E2E_BASE_URL || 'http://127.0.0.1:5173'
const SHOP = 'https://shop.try-on.test'
const PHOTO_URL = SHOP + '/images/white-shirt.png'
const PROVIDER = 'https://anywear.decart.ai'
const PHOTO = readFileSync(new URL('../public/assets/trend-pieces/white-shirt.png', import.meta.url))
const test = base.extend({
  providerGranted: [true, { option: true }],
  context: async ({ providerGranted }, use) => {
    const directory = path.join(ROOT, 'output/extension-check-' + crypto.randomUUID())
    const extension = path.join(directory, 'extension')
    mkdirSync(extension, { recursive: true })
    for (const file of readdirSync(path.join(ROOT, 'output/zhiji-try-on-extension'))) {
      writeFileSync(path.join(extension, file), readFileSync(path.join(ROOT, 'output/zhiji-try-on-extension', file)))
    }
    const manifest = JSON.parse(readFileSync(path.join(extension, 'manifest.json'), 'utf8'))
    expect(manifest.host_permissions).toBeUndefined()
    expect(manifest.content_scripts).toBeUndefined()
    // Only the isolated test profile gets the fixture and provider host grants. Native
    // permission-dialog approval remains a manual check; production stays opt-in.
    manifest.host_permissions = ['http://127.0.0.1/*', 'http://localhost/*', SHOP + '/*', ...(providerGranted ? [PROVIDER + '/*'] : [])]
    writeFileSync(path.join(extension, 'manifest.json'), JSON.stringify(manifest))
    const context = await chromium.launchPersistentContext(path.join(directory, 'profile'), {
      channel: 'chromium', headless: true, baseURL: APP,
      executablePath: process.env.E2E_EXTENSION_BROWSER_EXECUTABLE || undefined,
      args: [`--disable-extensions-except=${extension}`, `--load-extension=${extension}`]
    })
    try { await use(context) } finally { await context.close() }
  }
})

async function setup(context, { providerMode = 'streaming' } = {}) {
  let username = 'try-on-owner'
  const favorites = [], writes = [], errors = [], providerRequests = []
  context.on('page', page => page.on('pageerror', error => errors.push(error.message)))
  await context.route(PROVIDER + '/**', async route => {
    providerRequests.push(route.request().url())
    await route.fulfill({ contentType: 'text/html', headers: { 'Content-Security-Policy': "frame-ancestors 'self'" }, body: `<!doctype html><html><body><p>Anywear protocol fixture · no generated video</p><script>
      window.messages=[];window.addEventListener('message',event=>{window.messages.push(event.data);if(event.data.type==='DECART_AUTO_START' && '${providerMode}' !== 'silent')parent.postMessage({type:'DECART_TRACK_EVENT',event:'${providerMode === 'queue' ? 'queue_entered' : 'first_frame_rendered'}',properties:{tryon_id:event.data.tryonId}},event.origin)});
    </script></body></html>` })
  })
  await context.route(APP + '/api/**', async route => {
    const request = route.request(), pathname = new URL(request.url()).pathname
    let data = null
    if (pathname.endsWith('/auth/me')) data = { username, authorities: [] }
    else if (pathname.endsWith('/auth/csrf')) data = { token: 'test-csrf', headerName: 'X-XSRF-TOKEN' }
    else if (pathname.endsWith('/try-on-favorites') && request.method() === 'POST') {
      writes.push({ body: request.postDataBuffer(), headers: request.headers() })
      const favorite = { id: 1, name: '购物白衬衫', category: '上装', imageUrl: '/api/v1/me/try-on-favorites/1/image', sourceUrl: SHOP + '/product/white-shirt', sourceKind: 'SHOPPING' }
      if (!favorites.length) favorites.push(favorite)
      data = favorite
    } else if (pathname.endsWith('/try-on-favorites')) data = favorites
    else if (pathname.endsWith('/image')) return route.fulfill({ contentType: 'image/png', body: PHOTO })
    else if (pathname.endsWith('/wardrobe')) data = []
    else if (pathname.endsWith('/recommendations')) data = { content: [], page: 0, totalElements: 0, hasNext: false }
    else if (pathname.endsWith('/trends')) data = { items: [], styles: [], sources: [] }
    else if (pathname.includes('/weather/')) data = { city: '长沙', temperatureC: 25, source: 'test' }
    await route.fulfill({ contentType: 'application/json', body: JSON.stringify({ code: 0, data }) })
  })
  await context.route(SHOP + '/**', route => new URL(route.request().url()).pathname.endsWith('.png')
    ? route.fulfill({ contentType: 'image/png', body: PHOTO })
    : route.fulfill({ contentType: 'text/html; charset=utf-8', body: `<!doctype html><html><head><meta charset="UTF-8"><title>测试购物网站</title></head><body style="margin:50px;font:16px system-ui"><h1>测试购物网站</h1><a href="/product/white-shirt"><img src="${PHOTO_URL}" alt="购物白衬衫" width="220" height="280" draggable="true"></a><p>仅用于验证拖图和收藏，不连接真实商城。</p></body></html>` }))
  const worker = context.serviceWorkers()[0] || await context.waitForEvent('serviceworker')
  const extensionId = worker.url().split('/')[2]
  await context.addCookies([{ name: 'JSESSIONID', value: 'isolated-private-session', url: APP, httpOnly: true }])
  const project = await context.newPage()
  await project.goto(APP + '/#trend')
  await expect(project.getByRole('button', { name: '开启跨站试穿', exact: true })).toBeVisible()
  async function enable(page) {
    const url = page.url()
    const tabId = await worker.evaluate(async url => (await chrome.tabs.query({})).find(tab => tab.url === url).id, url)
    // Exercise actual Chrome scripting with a fixture-host grant, rather than
    // faking runtime messaging or injecting UI into the application document.
    await worker.evaluate(async tabId => {
      const tab = await chrome.tabs.get(tabId)
      const url = new URL(tab.url)
      await chrome.scripting.registerContentScripts([{ id: 'fixture_' + tabId,
        matches: [`${url.protocol}//${url.hostname}/*`], js: ['content.js'], runAt: 'document_idle' }])
      await chrome.scripting.executeScript({ target: { tabId }, files: ['content.js'] })
    }, tabId)
    return tabId
  }
  const projectTab = await enable(project)
  const popup = await context.newPage(); await popup.goto(`chrome-extension://${extensionId}/popup.html`)
  // Native popup permissions are pregranted in this isolated profile. Bind via
  // the real popup origin so shopping content scripts cannot change the target.
  const bound = await popup.evaluate(tabId => chrome.runtime.sendMessage({ type: 'BIND_PROJECT', tabId }), projectTab)
  expect(bound.ok).toBe(true)
  await popup.close()
  await expect(project.locator('#zhiji-try-on-host')).toHaveCount(0)
  await project.getByRole('button', { name: '开启跨站试穿', exact: true }).click()
  await expect(project.getByRole('status').filter({ hasText: '已开启。' })).toBeVisible()
  await expect.poll(() => project.frames().some(frame => frame.url().includes(extensionId + '/floating.html'))).toBe(true)
  const projectFloat = project.frames().find(frame => frame.url().includes(extensionId + '/floating.html'))
  await expect(projectFloat.getByRole('alert')).toBeHidden()
  const shop = await context.newPage(); await shop.goto(SHOP + '/collection'); await enable(shop)
  await expect.poll(() => shop.frames().some(frame => frame.url().includes(extensionId + '/floating.html'))).toBe(true)
  const floating = shop.frames().find(frame => frame.url().includes(extensionId + '/floating.html'))
  async function drag() {
    await shop.bringToFront()
    await expect.poll(() => shop.getByRole('img', { name: '购物白衬衫', exact: true }).evaluate(image => image.naturalWidth)).toBeGreaterThan(0)
    const source = await shop.getByRole('img', { name: '购物白衬衫', exact: true }).boundingBox()
    const target = await shop.locator('#zhiji-try-on-host').boundingBox()
    await shop.mouse.move(source.x + source.width / 2, source.y + source.height / 2)
    await shop.mouse.down()
    await shop.mouse.move(source.x + source.width / 2 + 12, source.y + source.height / 2 + 12, { steps: 4 })
    await shop.mouse.move(target.x + 150, target.y + 85, { steps: 20 })
    await shop.mouse.up()
    await expect(floating.getByLabel('单品名称')).toHaveValue('购物白衬衫')
  }
  async function start() {
    await floating.getByRole('button', { name: '同意并开始试穿', exact: true }).click()
    await expect.poll(() => controls()).toBeTruthy()
    await expect(controls().getByRole('status')).toHaveText('实时试穿中')
  }
  const provider = () => context.pages().find(page => !page.isClosed() && page.url().startsWith(PROVIDER))
  const controls = () => context.pages().filter(page => !page.isClosed()).flatMap(page => page.frames()).find(frame => !frame.isDetached() && frame.url().includes(extensionId + '/floating.html?controls='))
  const providerFrame = () => context.pages().filter(page => !page.isClosed()).flatMap(page => page.frames()).find(frame => !frame.isDetached() && frame.parentFrame() && frame.url().startsWith(PROVIDER))
  return { project, shop, get floating() { return controls() || shop.frames().find(frame => !frame.isDetached() && frame.url().includes(extensionId + '/floating.html')) },
    worker, extensionId, drag, start, provider, providerFrame, favorites, writes, providerRequests, errors,
    changeAccount: value => { username = value } }
}

test('real extension carries a dragged product into try-on and saves it back to the connected account', async ({ context }) => {
  const api = await setup(context)
  expect(api.providerRequests).toEqual([])
  await api.drag()
  expect(api.providerRequests).toEqual([])
  await api.start()
  const vendor = api.provider()
  expect(vendor).toBeTruthy()
  expect(vendor.mainFrame().parentFrame()).toBeNull()
  await expect(api.floating.locator('#preview iframe')).toHaveCount(0)
  expect(new URL(vendor.url()).searchParams.get('hostsite')).toBe('shop.try-on.test')
  expect(vendor.url()).not.toContain('data:')
  const message = await api.providerFrame().evaluate(() => window.messages.find(item => item.type === 'DECART_AUTO_START'))
  expect(message.productImageUrl).toBe('data:image/png;base64,' + PHOTO.toString('base64'))
  expect(JSON.stringify(message)).not.toContain('isolated-private-session')
  expect(JSON.stringify(message)).not.toContain('try-on-owner')
  const stored = await api.worker.evaluate(() => chrome.storage.session.get(null))
  expect(JSON.stringify(stored)).not.toContain(PHOTO.toString('base64'))
  await vendor.screenshot({ path: '../output/anywear-integration/integrated-window.png' })
  await api.floating.getByRole('button', { name: /喜欢，保存到项目/ }).click()
  await expect(api.floating.getByRole('button', { name: /已保存到喜欢穿搭/ })).toBeVisible()
  expect(api.writes).toHaveLength(1)
  expect(api.writes[0].headers['x-xsrf-token']).toBe('test-csrf')
  expect(api.writes[0].headers.cookie).toContain('JSESSIONID=isolated-private-session')
  expect(api.writes[0].body.includes(PHOTO)).toBe(true)
  expect(api.writes[0].body.toString()).toContain('try-on-owner')
  await api.shop.screenshot({ path: '../output/anywear-integration/cross-site-floating.png' })
  await api.project.goto(APP + '/#favorites')
  await expect(api.project.locator('.favorite-card')).toContainText('购物白衬衫')
  await expect(api.project.getByRole('link', { name: '查看原商品' })).toHaveAttribute('href', SHOP + '/product/white-shirt')
  await api.project.reload()
  await expect(api.project.locator('.favorite-card')).toContainText('购物白衬衫')
  await api.project.screenshot({ path: '../output/anywear-integration/favorites.png' })
  expect(api.errors).toEqual([])
})

test('changing the project account refuses the favorite instead of saving it under the new user', async ({ context }) => {
  const api = await setup(context); await api.drag(); await api.start()
  api.changeAccount('another-account')
  await api.floating.getByRole('button', { name: /喜欢，保存到项目/ }).click()
  await expect(api.floating.getByRole('alert')).toContainText('项目账号已变更')
  expect(api.writes).toHaveLength(0)
})

test('closing the project stops connected shopping-page frames', async ({ context }) => {
  const api = await setup(context); await api.drag(); await api.start()
  const provider = api.provider()
  await api.project.close()
  await expect(api.shop.locator('#zhiji-try-on-host')).toHaveCount(0)
  await expect.poll(() => provider.isClosed()).toBe(true)
})

test('an ended cross-site try-on stops its frame and still permits liking the tried product', async ({ context }) => {
  const api = await setup(context); await api.drag(); await api.start()
  const provider = api.provider()
  await api.providerFrame().evaluate(() => {
    const start = window.messages.find(message => message.type === 'DECART_AUTO_START')
    parent.postMessage({ type: 'DECART_TRACK_EVENT', event: 'session_ended', properties: { tryon_id: start.tryonId } }, '*')
  })
  await expect(api.floating.locator('#preview iframe')).toHaveCount(0)
  await expect(provider.locator('iframe[title="Anywear 实时试衣窗口"]')).toHaveCount(0)
  expect(provider.isClosed()).toBe(false)
  await expect(api.floating.getByRole('status')).toHaveText('本次试穿已结束')
  await api.floating.getByRole('button', { name: /喜欢，保存到项目/ }).click()
  await expect(api.floating.getByRole('button', { name: /已保存到喜欢穿搭/ })).toBeVisible()
  expect(api.writes).toHaveLength(1)
})

test('an unapproved site gets no injected float and cannot request arbitrary image reads', async ({ context }) => {
  const api = await setup(context)
  await context.route('https://unapproved.try-on.test/**', route => route.fulfill({ contentType: 'text/html', body: '<h1>Unapproved site</h1>' }))
  const other = await context.newPage(); await other.goto('https://unapproved.try-on.test/')
  await expect(other.locator('#zhiji-try-on-host')).toHaveCount(0)
  const result = await api.floating.evaluate(() => chrome.runtime.sendMessage({ type: 'READ_IMAGE', url: 'https://unapproved.try-on.test/private.png' }))
  expect(result.ok).toBe(false)
  expect(api.providerRequests).toEqual([])
})

test('shopping content cannot replace the bound project or redirect favorites to another site', async ({ context }) => {
  const api = await setup(context)
  await context.route(SHOP + '/api/v1/auth/me', route => route.fulfill({ contentType: 'application/json',
    body: JSON.stringify({ code: 0, data: { username: 'fake-project-user' } }) }))
  await api.shop.evaluate(() => {
    const button = document.createElement('button'); button.dataset.zhijiCrossSite = ''; button.id = 'fake-connect'; button.textContent = 'Fake project connect'; document.body.prepend(button)
    window.connectResponses = []
    window.addEventListener('message', event => { if (event.data?.channel === 'ZHIJI_TRY_ON_EXTENSION') window.connectResponses.push(event.data) })
  })
  await api.shop.getByRole('button', { name: 'Fake project connect' }).click()
  await api.shop.evaluate(() => window.postMessage({ channel: 'ZHIJI_TRY_ON_WEB', type: 'CONNECT_AND_OPEN',
    username: 'fake-project-user', requestId: 'forged-connection' }, location.origin))
  await expect.poll(() => api.shop.evaluate(() => window.connectResponses.find(message => message.requestId === 'forged-connection')?.ok)).toBe(false)
  const state = await api.worker.evaluate(() => chrome.storage.session.get('connection'))
  expect(state.connection.origin).toBe(APP)
  expect(state.connection.username).toBe('try-on-owner')
})

test('closing an integrated window restores the launcher, and stopping inference keeps its controls', async ({ context }) => {
  const api = await setup(context); await api.drag(); await api.start()
  const first = api.provider()
  await first.close()
  await expect(api.shop.locator('#zhiji-try-on-host')).toBeVisible()
  await expect.poll(() => Boolean(api.floating)).toBe(true)
  await api.floating.getByRole('button', { name: '同意并开始试穿', exact: true }).click()
  await expect.poll(() => api.provider()).toBeTruthy()
  await expect.poll(() => api.floating?.url().includes('controls=')).toBe(true)
  await expect(api.floating.getByRole('status')).toHaveText('实时试穿中')
  const second = api.provider()
  // Same parent message used by the floating panel's native minimize control.
  await api.floating.evaluate(() => window.dispatchEvent(new MessageEvent('message', { source: parent, data: { type: 'ZHIJI_SUSPEND' } })))
  await expect(second.locator('iframe[title="Anywear 实时试衣窗口"]')).toHaveCount(0)
  expect(second.isClosed()).toBe(false)
  await expect(api.floating.getByRole('status')).toContainText('已停止试穿')
  expect(api.errors).toEqual([])
})

test('unmatched and forged provider messages cannot enable liking before a first frame', async ({ context }) => {
  const api = await setup(context, { providerMode: 'silent' }); await api.drag()
  await api.floating.getByRole('button', { name: '同意并开始试穿', exact: true }).click()
  await expect.poll(() => api.provider()).toBeTruthy()
  await expect(api.provider().locator('iframe[title="知己试穿操作面板"]')).toBeVisible()
  await expect.poll(() => api.floating?.url().includes('controls=')).toBe(true)
  await expect(api.floating.getByRole('status')).toContainText('正在连接')
  const provider = api.provider()
  const rejected = await api.floating.evaluate(() => chrome.runtime.sendMessage({ type: 'PROVIDER_EVENT',
    tryonId: 'tryon_forged', data: { type: 'DECART_TRACK_EVENT', event: 'first_frame_rendered' } }))
  expect(rejected.ok).toBe(false)
  await api.providerFrame().evaluate(() => parent.postMessage({ type: 'DECART_TRACK_EVENT', event: 'first_frame_rendered', properties: { tryon_id: 'tryon_wrong' } }, '*'))
  await expect(api.floating.getByRole('button', { name: /喜欢，保存到项目/ })).toBeDisabled()
  await provider.clock.install()
  await api.floating.getByLabel('单品类别').selectOption('下装')
  await api.floating.getByRole('button', { name: '重新试穿此单品', exact: true }).click()
  await expect(api.floating.getByRole('status')).toContainText('正在连接')
  await provider.clock.fastForward(46000)
  await expect(api.floating.getByRole('alert')).toContainText('试衣服务未及时响应')
  await expect(provider.locator('iframe[title="Anywear 实时试衣窗口"]')).toHaveCount(0)
  expect(provider.isClosed()).toBe(false)
})

test.describe('provider permission', () => {
 test.use({ providerGranted: false })
 test('a missing provider host permission refuses to open a window without reading the product', async ({ context }) => {
  const api = await setup(context); await api.drag()
  // Automatic restarts check existing grants; only a real user click may ask.
  await api.floating.getByLabel('选择图片').setInputFiles({ name: 'shirt.png', mimeType: 'image/png', buffer: PHOTO })
  const response = await api.floating.evaluate(() => chrome.runtime.sendMessage({ type: 'OPEN_TRY_ON',
    tryonId: 'tryon_permission', image: document.getElementById('photo').src, category: '上装' }))
  expect(response.ok).toBe(false)
  expect(response.error).toContain('允许访问 Anywear')
  expect(api.providerRequests).toEqual([])
  expect(api.provider()).toBeUndefined()
 })
})

test('queued sessions stay queued after the open request returns and can be stopped', async ({ context }) => {
  const api = await setup(context, { providerMode: 'queue' }); await api.drag()
  await api.floating.getByRole('button', { name: '同意并开始试穿', exact: true }).click()
  await expect.poll(() => api.provider()).toBeTruthy()
  await expect(api.provider().locator('iframe[title="知己试穿操作面板"]')).toBeVisible()
  await expect.poll(() => api.floating?.url().includes('controls=')).toBe(true)
  await expect(api.floating.getByRole('status')).toHaveText('服务正在排队，请查看窗口提示')
  const provider = api.provider()
  await provider.clock.install()
  await provider.clock.fastForward(60000)
  await expect(api.floating.getByRole('status')).toHaveText('服务正在排队，请查看窗口提示')
  await api.floating.evaluate(() => window.dispatchEvent(new MessageEvent('message', { source: parent, data: { type: 'ZHIJI_SUSPEND' } })))
  await expect(provider.locator('iframe[title="Anywear 实时试衣窗口"]')).toHaveCount(0)
  expect(provider.isClosed()).toBe(false)
})

test('replacing a product reuses the integrated window and ignores an old session close', async ({ context }) => {
  const api = await setup(context); await api.drag(); await api.start()
  const previous = api.provider()
  const previousId = await api.providerFrame().evaluate(() => window.messages.find(message => message.type === 'DECART_AUTO_START').tryonId)
  await api.floating.getByLabel('选择图片').setInputFiles({ name: 'new-shirt.png', mimeType: 'image/png', buffer: PHOTO })
  await expect.poll(async () => {
    try { return await api.providerFrame()?.evaluate(() => window.messages?.find(message => message.type === 'DECART_AUTO_START')?.tryonId) }
    catch { return previousId }
  }).not.toBe(previousId)
  await expect(api.floating.getByRole('status')).toHaveText('实时试穿中')
  const current = api.provider()
  expect(current).toBe(previous)
  const currentId = await api.providerFrame().evaluate(() => window.messages.find(message => message.type === 'DECART_AUTO_START').tryonId)
  expect(currentId).not.toBe(previousId)
  // An old in-flight close must not tear down the newer session.
  await api.floating.evaluate(tryonId => chrome.runtime.sendMessage({ type: 'CLOSE_TRY_ON', tryonId }), previousId)
  const sessions = await api.worker.evaluate(() => chrome.storage.session.get(null))
  const active = Object.entries(sessions).filter(([key]) => key.startsWith('tryon-window:')).map(([, session]) => session)
  expect(active).toHaveLength(1)
  expect(active[0].tryonId).toBe(currentId)
  expect(current.isClosed()).toBe(false)
})

test('an enabled shopping site retains its floating panel across navigation and new tabs', async ({ context }) => {
  const api = await setup(context)
  await api.shop.goto(SHOP + '/product/another-shirt')
  await expect(api.shop.locator('#zhiji-try-on-host')).toBeVisible()
  const second = await context.newPage()
  await second.goto(SHOP + '/collection/new')
  await expect(second.locator('#zhiji-try-on-host')).toBeVisible()
  await api.project.bringToFront()
  await api.shop.bringToFront()
  await expect(api.shop.locator('#zhiji-try-on-host')).toBeVisible()
  expect(api.errors).toEqual([])
})

test('one integrated window contains the garment, live preview and favorite controls across page navigation', async ({ context }) => {
  const api = await setup(context); await api.drag(); await api.start()
  const provider = api.provider()
  await expect(provider.locator('iframe[title="知己试穿操作面板"]')).toBeVisible()
  await expect(api.shop.locator('#zhiji-try-on-host')).toHaveCount(0)
  await api.shop.goto(SHOP + '/product/another-shirt')
  expect(provider.isClosed()).toBe(false)
  await expect(provider.frameLocator('iframe[title="知己试穿操作面板"]').getByLabel('单品名称')).toHaveValue('购物白衬衫')
  await provider.frameLocator('iframe[title="知己试穿操作面板"]').getByRole('button', { name: /喜欢，保存到项目/ }).click()
  await expect(provider.frameLocator('iframe[title="知己试穿操作面板"]').getByRole('button', { name: /已保存到喜欢穿搭/ })).toBeVisible()
  expect(api.writes).toHaveLength(1)
})

test('the integrated window can stay on top with its preview and controls together', async ({ context }) => {
  const api = await setup(context); await api.drag(); await api.start()
  const provider = api.provider()
  await provider.getByRole('button', { name: '置顶显示', exact: true }).click()
  await expect.poll(() => provider.evaluate(() => Boolean(window.documentPictureInPicture?.window))).toBe(true)
  await expect.poll(() => provider.evaluate(() => documentPictureInPicture.window.document.querySelectorAll('iframe').length)).toBe(2)
  await expect(api.floating.getByLabel('单品名称')).toHaveValue('购物白衬衫')
  await api.shop.bringToFront()
  await api.shop.goto(SHOP + '/another-product')
  expect(provider.isClosed()).toBe(false)
  await expect.poll(() => provider.evaluate(() => Boolean(documentPictureInPicture.window))).toBe(true)
  await expect(api.floating.getByRole('status')).toHaveText('实时试穿中')
  await api.floating.getByRole('button', { name: /喜欢，保存到项目/ }).click()
  await expect(api.floating.getByRole('button', { name: /已保存到喜欢穿搭/ })).toBeVisible()
  expect(api.writes).toHaveLength(1)
  await provider.evaluate(() => documentPictureInPicture.window.close())
  await expect(provider.getByRole('button', { name: '置顶显示', exact: true })).toBeVisible()
  await expect(provider.locator('iframe[title="知己试穿操作面板"]')).toBeVisible()
  expect(api.errors).toEqual([])
})

test('a trusted drop selects a new garment inside the integrated window and saves its source', async ({ context }) => {
  const api = await setup(context); await api.drag(); await api.start()
  const provider = api.provider()
  const bounds = await provider.locator('iframe[title="知己试穿操作面板"]').boundingBox()
  const session = await context.newCDPSession(provider)
  const data = { items: [{ mimeType: 'text/uri-list', data: SHOP + '/product/second' },
    { mimeType: 'text/html', data: `<a href="${SHOP}/product/second"><img src="${PHOTO_URL}" alt="第二件衬衫"></a>` }], dragOperationsMask: 1 }
  await provider.bringToFront()
  await session.send('Input.dispatchDragEvent', { type: 'dragEnter', x: bounds.x + 100, y: bounds.y + 40, data })
  await session.send('Input.dispatchDragEvent', { type: 'drop', x: bounds.x + 100, y: bounds.y + 40, data })
  await expect(api.floating.getByLabel('单品名称')).toHaveValue('第二件衬衫')
  await expect(provider.locator('iframe[title="Anywear 实时试衣窗口"]')).toHaveCount(0)
  await api.floating.getByRole('button', { name: '重新试穿此单品', exact: true }).click()
  await expect(api.floating.getByRole('status')).toHaveText('实时试穿中')
  await api.floating.getByRole('button', { name: /喜欢，保存到项目/ }).click()
  await expect(api.floating.getByRole('button', { name: /已保存到喜欢穿搭/ })).toBeVisible()
  expect(api.writes[0].body.toString()).toContain('第二件衬衫')
  expect(api.writes[0].body.toString()).toContain(SHOP + '/product/second')
  expect(api.errors).toEqual([])
})

test('closing the source shopping tab keeps the integrated window available for favorites', async ({ context }) => {
  const api = await setup(context); await api.drag(); await api.start()
  const provider = api.provider()
  await api.shop.close()
  expect(provider.isClosed()).toBe(false)
  await expect(api.floating.getByRole('status')).toHaveText('实时试穿中')
  await api.floating.getByRole('button', { name: /喜欢，保存到项目/ }).click()
  await expect(api.floating.getByRole('button', { name: /已保存到喜欢穿搭/ })).toBeVisible()
  expect(api.writes).toHaveLength(1)
})
