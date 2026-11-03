import { expect, test } from '@playwright/test'

const techniqueRoot = '/api/v1/admin/outfit-techniques'
const operationalStatus = {
  status: 'ready', enabled: true,
  lastAttemptAt: '2026-11-02T02:00:00Z', lastSuccessAt: '2026-11-02T02:01:00Z',
  nextDueAt: '2026-11-09T02:00:00Z', lastErrorCode: null, attemptsInWindow: 1, weeklyAttemptLimit: 3
}

async function openAdmin(page, options = {}) {
  const writes = []
  const requests = []
  const errors = []
  const status = { ...operationalStatus, ...options.status }
  const budget = { month: '2026-11', used: 2, limit: 10, ...options.budget }
  page.on('pageerror', error => errors.push(error.message))
  await page.route('https://**/*', route => route.abort())
  await page.route('**/api/**', async route => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname
    requests.push(url)
    if (request.method() !== 'GET') writes.push({ path, csrf: request.headers()['x-xsrf-token'] })
    let data = {}
    if (path === '/api/v1/auth/me') data = { username: 'technique-admin', authorities: ['ROLE_ADMIN'] }
    else if (path === '/api/v1/auth/csrf') data = { token: 'technique-csrf', headerName: 'X-XSRF-TOKEN' }
    else if (path === `${techniqueRoot}/status`) {
      if (options.statusFailure) return route.fulfill({ status: 503, contentType: 'application/json', body: JSON.stringify({ code: 503, message: '暂时不可用' }) })
      data = status
    } else if (path === `${techniqueRoot}/comparison-visuals/status`) data = budget
    else if (path === `${techniqueRoot}/refresh`) data = { attempted: false, outcome: 'not-due', state: status }
    else if (path === `${techniqueRoot}/comparison-visuals`) data = { accepted: true, budget }
    else if (path === '/api/v1/admin/overview') data = {
      totalUsers: 8, enabledUsers: 7, disabledUsers: 1, totalWardrobeItems: 42,
      totalRecommendations: 6, totalFeedback: 5, averageRating: 4.2, savedRecommendations: 3,
      llmRecommendations: 4, fallbackRecommendations: 2, pendingFeedback: 1, manualReviewItems: 0, pendingImageCleanupTasks: 0
    }
    else if (path === '/api/v1/trends') data = { items: [], styles: [], sources: [], demoMode: false }
    else if (path.endsWith('/wardrobe') || path.endsWith('/ai-models') || path.endsWith('/try-on-favorites')) data = []
    else if (path.endsWith('/style-profile')) data = null
    else if (path.endsWith('/audit-logs')) data = {
      items: [{ id: 1, actorUsername: 'technique-admin', action: url.searchParams.get('action') || 'TECHNIQUE_COMPARISON',
        outcome: url.searchParams.get('outcome') || 'QUEUED', targetType: 'outfit_technique', targetId: 'all', details: '补充对照图', createdAt: '2026-11-03T02:00:00Z' }],
      totalElements: 1, page: 0, hasNext: false
    }
    else if (path.endsWith('/recommendations') || path.endsWith('/users') || path.endsWith('/feedback')) data = { items: [], content: [], totalElements: 0, hasNext: false }
    return route.fulfill({ contentType: 'application/json', body: JSON.stringify({ code: 0, data }) })
  })
  await page.goto('/#admin')
  await expect(page.getByRole('heading', { name: '运营总览', exact: true })).toBeVisible()
  return { writes, requests, errors }
}

async function selectTechniques(page) {
  await page.getByRole('button', { name: /穿搭技巧管理/ }).click()
  const panel = page.getByRole('region', { name: '穿搭技巧管理', exact: true })
  await expect(panel).toBeVisible()
  return panel
}

test('entering and refreshing technique management only reads status and budget, with real overview totals', async ({ page }) => {
  const { writes, requests, errors } = await openAdmin(page)
  await expect(page.locator('.overview-lead-stats').getByText('衣橱单品总数', { exact: true })).toBeVisible()
  await expect(page.locator('.overview-lead-stats')).toContainText('42')
  await expect(page.locator('.overview-lead-stats').getByText('用户反馈总数', { exact: true })).toBeVisible()
  const panel = await selectTechniques(page)
  await expect(panel.getByText('1 / 3 次', { exact: true })).toBeVisible()
  await expect(panel.getByText('2026-11 月度额度', { exact: true })).toBeVisible()
  await expect(panel.getByRole('button', { name: '整理技巧（调用模型）', exact: true })).toBeEnabled()
  await panel.getByRole('button', { name: '刷新状态', exact: true }).click()
  await expect.poll(() => requests.filter(url => url.pathname === `${techniqueRoot}/status`).length).toBe(2)
  await expect(panel.getByRole('button', { name: '刷新状态', exact: true })).toBeEnabled()
  expect(requests.filter(url => url.pathname === `${techniqueRoot}/comparison-visuals/status`)).toHaveLength(2)
  expect(writes).toEqual([])
  expect(errors).toEqual([])
  if (process.env.CAPTURE_ADMIN_TECHNIQUES) await page.screenshot({ path: '../output/feature-gaps/admin-techniques-desktop.png', fullPage: true, animations: 'disabled' })
})

test('manual actions use CSRF and preserve skipped versus queued results without claiming generated images', async ({ page }) => {
  const { writes, errors } = await openAdmin(page)
  const panel = await selectTechniques(page)
  await panel.getByRole('button', { name: '整理技巧（调用模型）', exact: true }).click()
  await expect(panel.getByRole('status')).toContainText('本次未执行')
  await expect(panel.getByRole('button', { name: '补全对照图（可能调用模型）', exact: true })).toBeEnabled()
  await panel.getByRole('button', { name: '补全对照图（可能调用模型）', exact: true }).click()
  await expect(panel.getByRole('status')).toContainText('尚不代表配图已生成')
  expect(writes).toEqual([
    { path: `${techniqueRoot}/refresh`, csrf: 'technique-csrf' },
    { path: `${techniqueRoot}/comparison-visuals`, csrf: 'technique-csrf' }
  ])
  expect(errors).toEqual([])
})

test('a failed status read can recover on mobile and exhausted image quota still allows source-image backfill', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  const options = { statusFailure: true, status: { status: 'disabled', enabled: false }, budget: { used: 10 } }
  const { writes, errors } = await openAdmin(page, options)
  const panel = await selectTechniques(page)
  await expect(panel.getByRole('alert')).toContainText('技巧运行状态读取失败')
  await expect(panel.getByRole('button', { name: '整理技巧（调用模型）', exact: true })).toBeDisabled()
  await expect(panel.getByText('本月生图额度已用完，仍可补充原文图片。', { exact: true })).toBeVisible()
  await expect(panel.getByRole('button', { name: '补全对照图（可能调用模型）', exact: true })).toBeEnabled()
  options.statusFailure = false
  await panel.getByRole('button', { name: '刷新状态', exact: true }).click()
  await expect(panel.getByRole('alert')).toHaveCount(0)
  await expect(panel.getByText('暂未启用', { exact: true })).toBeVisible()
  await expect(panel.getByRole('button', { name: '整理技巧（调用模型）', exact: true })).toBeDisabled()
  expect(writes).toEqual([])
  expect(await page.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth)).toBe(false)
  if (process.env.CAPTURE_ADMIN_TECHNIQUES) await page.screenshot({ path: '../output/feature-gaps/admin-techniques-mobile.png', fullPage: true, animations: 'disabled' })
  await panel.getByRole('button', { name: '补全对照图（可能调用模型）', exact: true }).click()
  await expect(panel.getByRole('status')).toContainText('补图任务已进入队列')
  expect(writes).toEqual([{ path: `${techniqueRoot}/comparison-visuals`, csrf: 'technique-csrf' }])
  expect(errors).toEqual([])
})

test('audit filters include trend and technique operations and their non-success outcomes', async ({ page }) => {
  const { requests, writes, errors } = await openAdmin(page)
  await page.getByRole('button', { name: /操作日志/ }).click()
  const audit = page.getByRole('region', { name: '操作日志', exact: true })
  const actionFilter = audit.getByRole('combobox', { name: '操作', exact: true })
  const outcomeFilter = audit.getByRole('combobox', { name: '结果', exact: true })
  for (const action of ['TREND_CONTENT_REVIEW', 'TREND_REFRESH', 'TECHNIQUE_REFRESH', 'TECHNIQUE_COMPARISON']) {
    await expect(actionFilter.locator(`option[value="${action}"]`)).toHaveCount(1)
  }
  for (const outcome of ['ATTEMPTED', 'SKIPPED', 'QUEUED']) {
    await expect(outcomeFilter.locator(`option[value="${outcome}"]`)).toHaveCount(1)
  }
  await actionFilter.selectOption('TECHNIQUE_COMPARISON')
  await outcomeFilter.selectOption('QUEUED')
  await expect(audit.getByRole('button', { name: '筛选日志', exact: true })).toBeEnabled()
  await audit.getByRole('button', { name: '筛选日志', exact: true }).click()
  await expect.poll(() => requests.filter(url => url.pathname.endsWith('/audit-logs') && url.searchParams.get('action') === 'TECHNIQUE_COMPARISON' && url.searchParams.get('outcome') === 'QUEUED').length).toBe(1)
  await expect(audit.locator('tbody')).toContainText('补全技巧对照图')
  await expect(audit.locator('tbody')).toContainText('已排队')
  expect(writes).toEqual([])
  expect(errors).toEqual([])
})
