import { expect, test } from '@playwright/test'

const svg = '<svg xmlns="http://www.w3.org/2000/svg" width="300" height="500"><rect width="300" height="500" fill="#d8dfd4"/></svg>'
function candidate(fingerprint = 'a'.repeat(64), extra = {}) {
  return { fingerprint, status: 'PENDING_HUMAN', hidden: false, item: {
    id: 'review/test', platform: 'weibo', title: '男装穿搭复核样例', summary: '原帖文字，需结合全部配图判断。',
    topicTags: ['男装穿搭', '日常'], publishedAt: new Date().toISOString(), sourceUrl: 'https://source.test/post/1',
    imageUrl: 'https://review-images.test/cover.svg',
    evidence: { author: '测试作者', mediaType: 'image', images: ['https://review-images.test/cover.svg', 'https://review-images.test/second.svg'], fullBodyImageUrl: 'https://review-images.test/full.svg' },
    ...extra
  } }
}

async function openReviews(page, options = {}) {
  let current = candidate()
  let postCount = 0
  let queueCount = 0
  const submitted = []
  await page.route('https://**/*', route => route.abort())
  await page.route('https://review-images.test/**', async route => {
    if (options.image) return options.image(route)
    return route.fulfill({ contentType: 'image/svg+xml', body: svg })
  })
  await page.route('**/api/**', async route => {
    const path = new URL(route.request().url()).pathname
    let data = {}
    if (path.endsWith('/auth/me')) data = { username: 'review-admin', authorities: ['ROLE_ADMIN'] }
    else if (path.endsWith('/auth/csrf')) data = { token: 'review-csrf', headerName: 'X-XSRF-TOKEN' }
    else if (path.endsWith('/review-queue')) { queueCount += 1; data = current ? [current] : [] }
    else if (path.endsWith('/content-review')) {
      postCount += 1
      submitted.push({ body: route.request().postDataJSON(), csrf: route.request().headers()['x-xsrf-token'] })
      if (options.conflict && postCount === 1) {
        current = candidate('b'.repeat(64), { summary: '原帖已更新，请重新检查。' })
        return route.fulfill({ status: 409, contentType: 'application/json', body: JSON.stringify({ code: 409, message: '内容已变化' }) })
      }
      data = { ...current, status: submitted.at(-1).body.approved ? 'APPROVED' : 'REJECTED' }
      current = null
    } else if (path.endsWith('/trends')) data = { items: [], styles: [], sources: [], demoMode: false }
    else if (path.endsWith('/wardrobe') || path.endsWith('/ai-models')) data = []
    else if (path.endsWith('/style-profile')) data = null
    else if (path.endsWith('/recommendations') || path.endsWith('/users') || path.endsWith('/feedback') || path.endsWith('/audit-logs')) data = { content: [], totalElements: 0, hasNext: false }
    return route.fulfill({ contentType: 'application/json', body: JSON.stringify({ code: 0, data }) })
  })
  await page.goto('/#admin')
  await page.getByRole('button', { name: /穿搭图片复核/ }).click()
  await expect(page.getByRole('heading', { name: '穿搭图片复核', exact: true })).toBeVisible()
  await expect(page.locator('.trend-review-card')).toHaveCount(1)
  return { submitted, queueCount: () => queueCount }
}

test('approval requires the cover and every image, a note and explicit confirmation, then sends the exact fingerprint and CSRF', async ({ page }) => {
  let releaseFullImage
  const held = new Promise(resolve => { releaseFullImage = resolve })
  const { submitted } = await openReviews(page, { image: async route => {
    if (route.request().url().endsWith('/full.svg')) await held
    return route.fulfill({ contentType: 'image/svg+xml', body: svg })
  } })
  const approve = page.getByRole('button', { name: '通过复核', exact: true })
  const confirmation = page.getByRole('checkbox')
  await expect(page.locator('.trend-review-images img')).toHaveCount(3)
  await expect(page.getByText('测试作者', { exact: false })).toBeVisible()
  await expect(page.getByRole('link', { name: '查看原帖', exact: true })).toHaveAttribute('href', 'https://source.test/post/1')
  await page.getByLabel('复核说明', { exact: true }).fill('全部图片均为完整服装搭配展示，文字与穿搭一致。')
  await expect(confirmation).toBeDisabled()
  await expect(approve).toBeDisabled()
  releaseFullImage()
  await expect(confirmation).toBeEnabled()
  await expect(approve).toBeDisabled()
  await confirmation.check()
  await page.screenshot({ path: '../output/integration-ui/admin-review-desktop.png', fullPage: true, animations: 'disabled' })
  await approve.click()
  await expect(page.getByText('当前没有待复核的穿搭内容。')).toBeVisible()
  expect(submitted).toEqual([{ body: { approved: true, fingerprint: 'a'.repeat(64), note: '全部图片均为完整服装搭配展示，文字与穿搭一致。' }, csrf: 'review-csrf' }])
})

test('a changed post refreshes the queue and requires fresh image confirmation and a new note without resubmitting approval', async ({ page }) => {
  const { submitted, queueCount } = await openReviews(page, { conflict: true })
  await expect(page.getByRole('checkbox')).toBeEnabled()
  await page.getByLabel('复核说明', { exact: true }).fill('第一版已检查。')
  await page.getByRole('checkbox').check()
  await page.getByRole('button', { name: '通过复核', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('内容已变化')
  await expect(page.getByText('原帖已更新，请重新检查。')).toBeVisible()
  await expect(page.getByLabel('复核说明', { exact: true })).toHaveValue('')
  await expect(page.getByRole('checkbox')).not.toBeChecked()
  await expect(page.getByRole('button', { name: '通过复核', exact: true })).toBeDisabled()
  expect(submitted).toHaveLength(1)
  expect(queueCount()).toBe(2)
  await expect(page.getByRole('checkbox')).toBeEnabled()
  await page.getByLabel('复核说明', { exact: true }).fill('重新检查全部新图片与文字。')
  await page.getByRole('checkbox').check()
  await page.getByRole('button', { name: '通过复核', exact: true }).click()
  await expect(page.locator('.trend-review-card')).toHaveCount(0)
  expect(submitted.map(request => request.body.fingerprint)).toEqual(['a'.repeat(64), 'b'.repeat(64)])
})

test('an unavailable image blocks approval while an explained rejection is still possible on mobile', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  const { submitted } = await openReviews(page, { image: route => route.abort() })
  await expect(page.getByText('图片不可用，无法通过复核', { exact: false }).first()).toBeVisible()
  await page.getByLabel('复核说明', { exact: true }).fill('原图无法查看，拒绝收录。')
  await expect(page.getByRole('checkbox')).toBeDisabled()
  await expect(page.getByRole('button', { name: '通过复核', exact: true })).toBeDisabled()
  expect(await page.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth)).toBe(false)
  await page.screenshot({ path: '../output/integration-ui/admin-review-mobile.png', fullPage: true, animations: 'disabled' })
  await page.getByRole('button', { name: '拒绝收录', exact: true }).click()
  await expect(page.locator('.trend-review-card')).toHaveCount(0)
  expect(submitted[0].body).toEqual({ approved: false, fingerprint: 'a'.repeat(64), note: '原图无法查看，拒绝收录。' })
  expect(await page.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth)).toBe(false)
})
