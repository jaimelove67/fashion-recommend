import { expect, test } from '@playwright/test'

async function mockApp(page, { restoredSession = false, admin = false, unavailable = false, rejectLogin = false, rejectRegistration = false, captchaUnavailable = false, sessionGate = null } = {}) {
  let authenticated = restoredSession
  const requests = []
  const errors = []
  page.on('pageerror', error => errors.push(error.message))
  await page.route('**/api/**', async route => {
    const request = route.request()
    const path = new URL(request.url()).pathname
    requests.push({ path, method: request.method(), authenticated })
    let data = null
    let status = 200
    if (unavailable) status = 503
    else if (path === '/api/v1/auth/csrf') data = { token: 'guest-test', headerName: 'X-XSRF-TOKEN' }
    else if (path === '/api/v1/auth/captcha') {
      if (captchaUnavailable) status = 503
      else data = { image: 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=', expiresInSeconds: 180 }
    }
    else if (path === '/api/v1/auth/me') {
      if (sessionGate) await sessionGate
      if (authenticated) data = { username: 'guest-test', authorities: [admin ? 'ROLE_ADMIN' : 'ROLE_USER'] }
      else status = 401
    } else if (path === '/api/v1/auth/login') {
      if (rejectLogin) status = 401
      else authenticated = true
    }
    else if (path === '/api/v1/auth/register') status = rejectRegistration ? 400 : 201
    else if (path === '/api/v1/auth/logout') authenticated = false
    else if (path === '/api/v1/trends') data = { items: [], styles: [], sources: [] }
    else if (path === '/api/v1/me/wardrobe') data = [{ id: 1, name: '测试白衬衫', category: '上装', color: '白色' }]
    else if (path === '/api/v1/me/recommendations') data = { content: [], totalElements: 0, page: 0, hasNext: false }
    else if (path === '/api/v1/admin/overview') data = {}
    else if (path === '/api/v1/admin/ai-models') data = []
    else if (path.startsWith('/api/v1/admin/')) data = { content: [], totalElements: 0, page: 0, hasNext: false }
    else if (path !== '/api/v1/me/style-profile') status = 404
    await route.fulfill({ status, contentType: 'application/json', body: JSON.stringify({
      code: status < 400 ? 0 : status, data, message: status === 503 ? '服务暂时不可用' : status === 400 ? '验证码错误，请输入新图片中的字符。' : '请先登录'
    }) })
  })
  return { requests, errors }
}

async function showLoginForm(page) {
  const heading = page.getByRole('heading', { name: '登录知己', exact: true })
  const shortcut = page.getByRole('button', { name: '直接登录', exact: true })
  await expect(heading.or(shortcut).first()).toBeVisible()
  if (await shortcut.isVisible()) await shortcut.click()
  await expect(heading).toBeVisible()
}

async function openLogin(page) {
  await page.getByRole('button', { name: '登录 / 注册', exact: true }).click()
  await showLoginForm(page)
}

async function signIn(page) {
  await showLoginForm(page)
  await page.getByRole('textbox', { name: '账号', exact: true }).fill('guest-test')
  await page.locator('input[name="password"]').fill('guest-password-2026')
  await page.getByRole('button', { name: '登录知己', exact: true }).click()
  await expect(page.getByRole('button', { name: '退出登录', exact: true })).toBeVisible()
}

test('plays the double-fish intro before revealing the login form', async ({ page }) => {
  const api = await mockApp(page)
  await page.emulateMedia({ reducedMotion: 'no-preference' })
  await page.goto('/')
  await page.getByRole('button', { name: '登录 / 注册', exact: true }).click()
  await expect(page.locator('.fish-auth')).toHaveAttribute('data-phase', 'intro')
  await expect(page.getByRole('heading', { name: '登录知己', exact: true })).toBeHidden()
  await expect(page.getByRole('button', { name: '返回首页', exact: true })).toBeVisible()
  await page.getByRole('button', { name: '点击图标，进入知己', exact: true }).click()
  await expect(page.locator('.fish-auth')).toHaveAttribute('data-phase', 'opening')
  await expect(page.locator('.fish-auth')).toHaveAttribute('data-phase', 'login')
  await expect(page.getByRole('textbox', { name: '账号', exact: true })).toBeFocused()
  await page.getByRole('button', { name: '返回欢迎页', exact: true }).click()
  await expect(page.locator('.fish-auth')).toHaveAttribute('data-phase', 'intro')
  await page.getByRole('button', { name: '返回首页', exact: true }).click()
  await expect(page.locator('.guest-look')).toHaveCount(3)
  expect(api.requests.filter(request => request.method !== 'GET')).toEqual([])
  expect(api.errors).toEqual([])
})

test('late fish images do not hide a form opened through the login shortcut', async ({ page }) => {
  const api = await mockApp(page)
  let releaseImages
  const imageGate = new Promise(resolve => { releaseImages = resolve })
  await page.route('**/prototypes/zhiji-fish/assets/fish-atlas.png', async route => {
    await imageGate
    await route.continue()
  })
  try {
    await page.goto('/', { waitUntil: 'domcontentloaded' })
    await page.getByRole('button', { name: '登录 / 注册', exact: true }).click()
    await expect(page.locator('.fish-auth')).toHaveAttribute('data-phase', 'loading')
    await page.getByRole('button', { name: '直接登录', exact: true }).click()
    const username = page.getByRole('textbox', { name: '账号', exact: true })
    await username.fill('still-typing')
    const loaded = page.waitForResponse('**/prototypes/zhiji-fish/assets/fish-atlas.png')
    releaseImages()
    await loaded
    await expect.poll(() => page.locator('.fish-auth-canvas').evaluate(canvas => canvas.width)).toBeGreaterThan(300)
    await expect(page.locator('.fish-auth')).toHaveAttribute('data-phase', 'login')
    await expect(username).toBeVisible()
    await expect(username).toHaveValue('still-typing')
    expect(api.errors).toEqual([])
  } finally {
    releaseImages()
  }
})

test('a late image failure does not move focus away from the password field', async ({ page }) => {
  const api = await mockApp(page)
  let releaseImages
  const imageGate = new Promise(resolve => { releaseImages = resolve })
  await page.route('**/prototypes/zhiji-fish/assets/fish-atlas.png', async route => {
    await imageGate
    await route.abort()
  })
  try {
    await page.goto('/', { waitUntil: 'domcontentloaded' })
    await page.getByRole('button', { name: '登录 / 注册', exact: true }).click()
    await page.getByRole('button', { name: '直接登录', exact: true }).click()
    const password = page.locator('input[name="password"]')
    await expect(page.getByRole('textbox', { name: '账号', exact: true })).toBeFocused()
    await password.fill('still-private-password')
    releaseImages()
    await expect(page.getByText('开场动画加载失败，可直接使用登录表单。', { exact: true })).toBeVisible()
    await expect(password).toBeFocused()
    await expect(page.getByRole('textbox', { name: '账号', exact: true })).toHaveValue('')
    expect(api.errors).toEqual([])
  } finally {
    releaseImages()
  }
})

test('reduced motion opens the form without the swimming transition', async ({ page }) => {
  const api = await mockApp(page)
  await page.emulateMedia({ reducedMotion: 'reduce' })
  await page.goto('/')
  await page.getByRole('button', { name: '登录 / 注册', exact: true }).click()
  await expect(page.locator('.fish-auth')).toHaveAttribute('data-phase', 'intro')
  await page.getByRole('button', { name: '点击图标，进入知己', exact: true }).click()
  await expect(page.locator('.fish-auth')).toHaveAttribute('data-phase', 'login')
  await expect(page.getByRole('textbox', { name: '账号', exact: true })).toBeFocused()
  expect(api.errors).toEqual([])
})

test('missing fish images leave a usable login form and a way back home', async ({ page }) => {
  const api = await mockApp(page)
  await page.route('**/prototypes/zhiji-fish/assets/fish-atlas.png', route => route.abort())
  await page.goto('/')
  await page.getByRole('button', { name: '登录 / 注册', exact: true }).click()
  await expect(page.getByText('开场动画加载失败，可直接使用登录表单。', { exact: true })).toBeVisible()
  await expect(page.getByRole('textbox', { name: '账号', exact: true })).toBeEditable()
  await page.getByRole('button', { name: '返回首页', exact: true }).click()
  await expect(page.locator('.guest-look')).toHaveCount(3)
  expect(api.errors).toEqual([])
})

test('shows outfit examples and features to guests without loading private data', async ({ page }) => {
  const api = await mockApp(page)
  await page.goto('/')
  await expect(page.getByRole('heading', { name: '先找一点穿搭灵感', exact: true })).toBeVisible()
  await expect(page.locator('.guest-look')).toHaveCount(3)
  await expect(page.locator('.home-guide-card')).toHaveCount(4)
  await expect(page.getByText('搭配示例 · 非个性化推荐', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '登录 / 注册', exact: true })).toBeEnabled()
  await expect(page.locator('.stat-rail')).toHaveCount(0)
  await expect(page.locator('.fish-auth')).toHaveCount(0)
  await expect(page.getByRole('button', { name: '退出登录', exact: true })).toHaveCount(0)
  const images = await page.locator('.collage-frame img, .guest-look img').evaluateAll(images =>
    images.every(image => image.complete && image.naturalWidth > 0))
  expect(images).toBe(true)
  expect(api.requests.filter(request => request.path.startsWith('/api/v1/me/'))).toEqual([])
  expect(api.errors).toEqual([])
})

test('guest content is visible while the session check is still pending', async ({ page }) => {
  let releaseSession
  const sessionGate = new Promise(resolve => { releaseSession = resolve })
  await mockApp(page, { sessionGate })
  await page.goto('/')
  try {
    await expect(page.getByRole('heading', { name: '先找一点穿搭灵感', exact: true })).toBeVisible({ timeout: 3000 })
    await expect(page.locator('.guest-login-button')).toBeDisabled()
    await page.getByRole('button', { name: '先看穿搭示例', exact: true }).click()
    await expect(page.locator('#guest-inspiration')).toBeInViewport()
  } finally {
    releaseSession()
  }
  await expect(page.getByRole('button', { name: '登录 / 注册', exact: true })).toBeEnabled()
})

test('guest examples remain available when the backend is unavailable', async ({ page }) => {
  const api = await mockApp(page, { unavailable: true })
  await page.goto('/')
  await expect(page.locator('.guest-look')).toHaveCount(3)
  await expect(page.getByRole('button', { name: '登录 / 注册', exact: true })).toBeEnabled()
  await expect(page.getByText('暂无穿搭内容', { exact: true })).toHaveCount(0)
  await openLogin(page)
  await expect(page.getByRole('heading', { name: '登录知己', exact: true })).toBeVisible()
  await expect(page.getByRole('alert')).toContainText('服务暂时不可用')
  expect(api.errors).toEqual([])
})

test('guests can open login and registration and return to browsing', async ({ page }) => {
  const api = await mockApp(page)
  await page.goto('/')
  await openLogin(page)
  await expect(page.getByRole('heading', { name: '登录知己', exact: true })).toBeVisible()
  await page.getByRole('tab', { name: '注册', exact: true }).click()
  await expect(page.getByRole('heading', { name: '创建知己账户', exact: true })).toBeVisible()
  await expect(page.locator('input[name="confirmPassword"]')).toBeVisible()
  await page.getByRole('button', { name: '返回首页', exact: true }).click()
  await expect(page.locator('.guest-look')).toHaveCount(3)
  await expect(page.locator('.fish-auth')).toHaveCount(0)
  expect(api.requests.some(request => request.method !== 'GET')).toBe(false)
  expect(api.errors).toEqual([])
})

test('a protected feature opens login and continues to that feature after signing in', async ({ page }) => {
  const api = await mockApp(page)
  await page.goto('/')
  await expect(page.getByRole('button', { name: '登录 / 注册', exact: true })).toBeEnabled()
  await page.locator('.home-guide-card').filter({ hasText: '我的衣橱' }).click()
  await showLoginForm(page)
  await expect(page.getByRole('heading', { name: '登录知己', exact: true })).toBeVisible()
  expect(api.requests.filter(request => request.path.startsWith('/api/v1/me/'))).toEqual([])
  await signIn(page)
  await expect(page).toHaveURL(/#wardrobe$/)
  await expect(page.getByRole('heading', { name: '我的衣橱', exact: true })).toBeVisible()
  await expect(page.locator('.garment-card')).toContainText('测试白衬衫')
  await expect(page.locator('.guest-header')).toHaveCount(0)
  expect(api.errors).toEqual([])
})

test('registration from the guest home starts a personal space without copying examples into it', async ({ page }) => {
  const api = await mockApp(page)
  await page.goto('/')
  await openLogin(page)
  await page.getByRole('tab', { name: '注册', exact: true }).click()
  await page.locator('input[name="username"]').fill('guest-test')
  await page.locator('input[name="password"]').fill('guest-password-2026')
  await page.locator('input[name="confirmPassword"]').fill('guest-password-2026')
  await page.locator('input[name="captcha"]').fill('AbC23')
  await page.getByRole('button', { name: '注册账户', exact: true }).click()
  await expect(page.getByRole('button', { name: '退出登录', exact: true })).toBeVisible()
  await expect(page).toHaveURL(/#recommend$/)
  await expect(page.locator('.recommendation-view')).toBeVisible()
  await expect(page.locator('.guest-look')).toHaveCount(0)
  expect(api.requests.filter(request => request.method === 'POST').map(request => request.path))
    .toEqual(['/api/v1/auth/register', '/api/v1/auth/login'])
  expect(api.errors).toEqual([])
})

test('registration refreshes a rejected captcha and clears the previous input', async ({ page }) => {
  const api = await mockApp(page, { rejectRegistration: true })
  await page.goto('/')
  await openLogin(page)
  await page.getByRole('tab', { name: '注册', exact: true }).click()
  await page.locator('input[name="username"]').fill('captcha-test')
  await page.locator('input[name="password"]').fill('guest-password-2026')
  await page.locator('input[name="confirmPassword"]').fill('guest-password-2026')
  await page.locator('input[name="captcha"]').fill('WRONG')
  const sent = page.waitForRequest(request => new URL(request.url()).pathname === '/api/v1/auth/register')
  await page.getByRole('button', { name: '注册账户', exact: true }).click()
  expect((await sent).postDataJSON().captcha).toBe('WRONG')
  await expect(page.getByRole('alert')).toContainText('验证码错误')
  await expect(page.locator('input[name="captcha"]')).toHaveValue('')
  await expect(page.locator('input[name="captcha"]')).toBeEnabled()
  expect(api.requests.filter(request => request.path === '/api/v1/auth/captcha')).toHaveLength(2)
  expect(api.requests.filter(request => request.path === '/api/v1/auth/login')).toHaveLength(0)
  expect(api.errors).toEqual([])
})

test('captcha load failures prevent registration and allow retrying the image', async ({ page }) => {
  const api = await mockApp(page, { captchaUnavailable: true })
  await page.goto('/')
  await openLogin(page)
  await page.getByRole('tab', { name: '注册', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('服务暂时不可用')
  await expect(page.getByRole('button', { name: '注册账户', exact: true })).toBeDisabled()
  await page.getByRole('button', { name: '重新加载验证码', exact: true }).click()
  await expect.poll(() => api.requests.filter(request => request.path === '/api/v1/auth/captcha').length).toBe(2)
  await page.getByRole('tab', { name: '登录', exact: true }).click()
  await expect(page.locator('input[name="captcha"]')).toHaveCount(0)
  await expect(page.getByRole('button', { name: '登录知己', exact: true })).toBeEnabled()
})

test('a small-screen registration form can refresh the captcha without submitting credentials', async ({ page }) => {
  const api = await mockApp(page)
  await page.setViewportSize({ width: 320, height: 740 })
  await page.goto('/')
  await openLogin(page)
  await page.getByRole('tab', { name: '注册', exact: true }).click()
  await page.locator('input[name="captcha"]').fill('ABC23')
  await page.getByRole('button', { name: '刷新验证码', exact: true }).click()
  await expect(page.locator('input[name="captcha"]')).toHaveValue('')
  await expect(page.locator('input[name="captcha"]')).toBeEnabled()
  await expect(page.getByRole('img', { name: '注册验证码', exact: true })).toBeVisible()
  expect(api.requests.filter(request => request.path === '/api/v1/auth/captcha')).toHaveLength(2)
  expect(api.requests.filter(request => request.method === 'POST')).toHaveLength(0)
  const panelOverflows = await page.locator('.fish-auth-login').evaluate(panel => panel.scrollWidth > panel.clientWidth)
  expect(panelOverflows).toBe(false)
  expect((await page.getByRole('button', { name: '刷新验证码', exact: true }).boundingBox()).height).toBe(48)
  await page.getByRole('button', { name: '注册账户', exact: true }).scrollIntoViewIfNeeded()
  await expect(page.getByRole('button', { name: '注册账户', exact: true })).toBeInViewport()
  expect(api.errors).toEqual([])
})

test('a rejected login stays on the form and lets the visitor return to examples', async ({ page }) => {
  const api = await mockApp(page, { rejectLogin: true })
  await page.goto('/')
  await openLogin(page)
  await page.getByRole('textbox', { name: '账号', exact: true }).fill('guest-test')
  await page.locator('input[name="password"]').fill('guest-password-2026')
  await page.getByRole('button', { name: '登录知己', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('请先登录')
  await expect(page.getByRole('button', { name: '登录知己', exact: true })).toBeEnabled()
  await page.getByRole('button', { name: '返回首页', exact: true }).click()
  await expect(page.locator('.guest-look')).toHaveCount(3)
  expect(api.requests.filter(request => request.path.startsWith('/api/v1/me/'))).toEqual([])
  expect(api.errors).toEqual([])
})

test('restored sessions open recommendations and logout returns to the guest home', async ({ page }) => {
  const api = await mockApp(page, { restoredSession: true })
  await page.goto('/#home')
  await expect(page.getByRole('button', { name: '退出登录', exact: true })).toBeVisible()
  await expect(page).toHaveURL(/#recommend$/)
  await expect(page.locator('.recommendation-view')).toBeVisible()
  await expect(page.locator('.pill-nav-desktop').getByRole('button', { name: '首页', exact: true })).toHaveCount(0)
  await expect(page.locator('.guest-look')).toHaveCount(0)
  await page.getByRole('button', { name: '退出登录', exact: true }).click()
  await expect(page.locator('.guest-look')).toHaveCount(3)
  await expect(page.locator('.fish-auth')).toHaveCount(0)
  await expect(page.getByText('测试白衬衫', { exact: true })).toHaveCount(0)
  expect(api.errors).toEqual([])
})

test('signing in from the guest home opens recommendations and keeps the core navigation', async ({ page }) => {
  const api = await mockApp(page)
  await page.goto('/#home')
  await openLogin(page)
  await signIn(page)
  await expect(page).toHaveURL(/#recommend$/)
  const navigation = page.locator('.pill-nav-desktop')
  await expect(navigation.getByRole('button', { name: '首页', exact: true })).toHaveCount(0)
  await expect(navigation.getByRole('button')).toHaveCount(4)
  await expect(navigation.getByRole('button', { name: '穿搭推荐', exact: true })).toHaveAttribute('aria-current', 'page')
  await expect(page.locator('.recommendation-view')).toBeVisible()
  await page.reload()
  await expect(page).toHaveURL(/#recommend$/)
  await expect(page.locator('.recommendation-view')).toBeVisible()
  expect(api.errors).toEqual([])
})

test('signed-in home links and browser history resolve to recommendations', async ({ page }) => {
  const api = await mockApp(page, { restoredSession: true })
  await page.goto('/#wardrobe')
  await expect(page.getByRole('heading', { name: '我的衣橱', exact: true })).toBeVisible()
  await page.evaluate(() => { window.location.hash = '#home' })
  await expect(page).toHaveURL(/#recommend$/)
  await expect(page.locator('.recommendation-view')).toBeVisible()
  await page.getByRole('button', { name: '穿搭鉴赏', exact: true }).click()
  await expect(page).toHaveURL(/#trend$/)
  await page.goBack()
  await expect(page).toHaveURL(/#recommend$/)
  await expect(page.locator('.recommendation-view')).toBeVisible()
  expect(api.errors).toEqual([])
})

test('signed-in invalid routes and inaccessible admin links fall back to recommendations', async ({ page }) => {
  const api = await mockApp(page, { restoredSession: true })
  for (const route of ['', '#missing', '#admin']) {
    await page.goto(`/${route}`)
    await expect(page).toHaveURL(/#recommend$/)
    await expect(page.locator('.recommendation-view')).toBeVisible()
    await expect(page.locator('.pill-nav-desktop').getByRole('button', { name: '管理后台', exact: true })).toHaveCount(0)
  }
  expect(api.requests.filter(request => request.path.startsWith('/api/v1/admin/'))).toEqual([])
  expect(api.errors).toEqual([])
})

test('mobile navigation contains the core features without a home entry', async ({ page }) => {
  const api = await mockApp(page, { restoredSession: true })
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto('/#home')
  await expect(page).toHaveURL(/#recommend$/)
  await page.getByRole('button', { name: '打开页面菜单', exact: true }).click()
  const menu = page.locator('.pill-nav-mobile-menu')
  await expect(menu.getByRole('menuitem', { name: '首页', exact: true })).toHaveCount(0)
  await expect(menu.getByRole('menuitem')).toHaveCount(4)
  await menu.getByRole('menuitem', { name: '我的衣橱', exact: true }).click()
  await expect(page).toHaveURL(/#wardrobe$/)
  await expect(page.getByRole('heading', { name: '我的衣橱', exact: true })).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth)).toBe(false)
  expect(api.errors).toEqual([])
})

test('an admin can return from the admin workspace directly to user recommendations', async ({ page }) => {
  const api = await mockApp(page, { restoredSession: true, admin: true })
  await page.goto('/#home')
  await expect(page).toHaveURL(/#admin$/)
  await page.getByRole('button', { name: '返回用户端', exact: true }).click()
  await expect(page).toHaveURL(/#recommend$/)
  await expect(page.locator('.recommendation-view')).toBeVisible()
  await expect(page.locator('.pill-nav-desktop').getByRole('button', { name: '首页', exact: true })).toHaveCount(0)
  await expect(page.locator('.pill-nav-desktop').getByRole('button', { name: '管理后台', exact: true })).toBeVisible()
  expect(api.errors).toEqual([])
})

test('guest browsing works on a small screen and protected deep links stay private', async ({ page }) => {
  const api = await mockApp(page)
  await page.setViewportSize({ width: 320, height: 740 })
  await page.goto('/#wardrobe')
  await expect(page.locator('.guest-look')).toHaveCount(3)
  await expect(page.getByRole('heading', { name: '我的衣橱', exact: true })).toHaveCount(0)
  await page.getByRole('button', { name: '穿搭示例', exact: true }).click()
  await expect(page.locator('#guest-inspiration')).toBeInViewport()
  expect(await page.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth)).toBe(false)
  expect(api.requests.filter(request => request.path.startsWith('/api/v1/me/'))).toEqual([])
  expect(api.errors).toEqual([])
})
