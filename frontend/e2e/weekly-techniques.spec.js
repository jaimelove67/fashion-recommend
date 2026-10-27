import { expect, test } from '@playwright/test'
import { outfitTechniques } from '../src/data/outfitTechniques.js'

function weeklyLesson(overrides = {}) {
  const base = structuredClone(outfitTechniques[0])
  return { ...base, id: 'weekly-layering', title: '本周练习：先留空间，再整理领口', category: '叠穿',
    publishedAt: '2026-10-16T02:00:00Z', generationMode: 'weekly-ai',
    sources: base.sources.map((source, index) => ({ ...source, url: `https://example.test/weekly-${index}`,
      publishedAt: index ? null : '2018-02-25T18:40:10Z', author: index ? source.author : null,
      scope: '假冒人工核验声明' })), ...overrides }
}

async function openTechniques(page, { status = 200, data, gate, imageStatus = 200 } = {}) {
  const errors = []
  const writes = []
  page.on('pageerror', error => errors.push(error.message))
  await page.route('**/api/**', async route => {
    const request = route.request()
    const path = new URL(request.url()).pathname
    if (request.method() !== 'GET') writes.push(path)
    if (path.startsWith('/api/v1/outfit-techniques/comparison-images/')) {
      return route.fulfill({ status: imageStatus, contentType: imageStatus === 200 ? 'image/svg+xml' : 'application/json',
        body: imageStatus === 200 ? '<svg xmlns="http://www.w3.org/2000/svg" width="800" height="450"><rect width="800" height="450" fill="#e4e9dd"/></svg>' : '{"code":404,"message":"Image not found"}' })
    }
    let responseData = {}
    if (path === '/api/v1/auth/csrf') responseData = { token: 'weekly-test', headerName: 'X-XSRF-TOKEN' }
    else if (path === '/api/v1/auth/me') responseData = { username: 'weekly-test', authorities: [] }
    else if (path === '/api/v1/trends') responseData = { items: [], styles: [], sources: [] }
    else if (path === '/api/v1/me/wardrobe') responseData = []
    else if (path === '/api/v1/me/recommendations') responseData = { content: [], totalElements: 0, hasNext: false }
    else if (path === '/api/v1/me/style-profile') responseData = null
    else if (path === '/api/v1/outfit-techniques') {
      if (gate) await gate
      return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify({ code: status === 200 ? 0 : status,
        data: data ?? { items: [weeklyLesson()], cadence: 'weekly', status: 'ready', latestPublishedAt: '2026-10-16T02:00:00Z' } }) })
    }
    return route.fulfill({ contentType: 'application/json', body: JSON.stringify({ code: 0, data: responseData }) })
  })
  await page.goto('/#trend')
  const explorer = page.getByRole('region', { name: '穿搭技巧', exact: true })
  await expect(explorer).toBeVisible()
  return { explorer, errors, writes }
}

test('weekly lessons appear newest first, remove duplicated curated sources and disclose AI extraction', async ({ page }) => {
  const older = { ...structuredClone(outfitTechniques[0]), id: 'weekly-color',
    generationMode: 'weekly-ai', publishedAt: '2026-10-09T02:00:00Z' }
  const { explorer, errors } = await openTechniques(page, { data: { items: [older, weeklyLesson()], status: 'ready' } })
  const choices = explorer.locator('.technique-choice')
  const detail = explorer.locator('.technique-detail')
  await expect(choices).toHaveCount(outfitTechniques.length + 1)
  await expect(choices.first()).toContainText(weeklyLesson().title)
  await expect(detail.getByRole('heading', { level: 3 })).toHaveText(weeklyLesson().title)
  await expect(explorer.locator('.technique-weekly-status')).toContainText('最近更新 2026-10-16')
  await expect(detail.locator('.technique-ai-disclosure')).toContainText('未经人工逐条核读')
  await expect(detail.locator('.technique-example-note')).toContainText('AI 推演，非原帖对照实验')
  await expect(detail.locator('.technique-source-list')).toContainText('原文未署名')
  await expect(detail.locator('.technique-source-list')).toContainText('正文提取日期：2026-10-16')
  await expect(detail.locator('.technique-source-meta').first()).toContainText('2018-02-26')
  await expect(detail.locator('.technique-source-meta').last()).toContainText('原帖未标注日期')
  await expect(detail.locator('.technique-source-meta').first()).not.toContainText('T18:40:10Z')
  await expect(detail.locator('.technique-source-list')).toContainText('已提取正文，AI归纳；未核验原图或视频')
  await expect(detail).not.toContainText('假冒人工核验声明')
  if (process.env.CAPTURE_WEEKLY_TECHNIQUES) {
    await explorer.screenshot({ path: '../output/weekly-techniques/desktop.png', animations: 'disabled' })
  }
  await explorer.getByRole('navigation', { name: '按技巧主题筛选' }).getByRole('button', { name: '叠穿', exact: true }).click()
  await expect(choices).toHaveCount(2)
  await detail.getByRole('button', { name: '展开自查提示', exact: true }).click()
  await expect(detail.locator('.technique-checklist')).toBeVisible()
  await choices.last().click()
  await expect(detail.locator('.technique-checklist')).toBeHidden()
  await expect(detail.locator('.technique-ai-disclosure')).toHaveCount(0)
  expect(errors).toEqual([])
})

test('partial source overlap keeps distinct weekly and curated lessons while full source sets deduplicate', async ({ page }) => {
  const sharedSource = structuredClone(outfitTechniques[0].sources[0])
  const newest = weeklyLesson()
  newest.sources[0] = sharedSource
  const partial = weeklyLesson({ id: 'weekly-partial-overlap', title: '本周另一练：先比较色块，再选择领口',
    publishedAt: '2026-10-09T02:00:00Z' })
  partial.sources = [sharedSource, { ...partial.sources[1], url: 'https://example.test/different-topic' }]
  const duplicate = { ...structuredClone(newest), id: 'weekly-same-sources', title: '换标题重复来源的内容',
    publishedAt: '2026-10-02T02:00:00Z', sources: [...newest.sources].reverse()
      .map(source => ({ ...source, url: `${source.url}?utm_source=shared#section` })) }
  const { explorer, errors } = await openTechniques(page, { data: { items: [partial, duplicate, newest], status: 'ready' } })
  const choices = explorer.locator('.technique-choice')
  await expect(choices).toHaveCount(outfitTechniques.length + 2)
  await expect(choices.first()).toContainText(newest.title)
  await expect(choices.nth(1)).toContainText(partial.title)
  await expect(choices.filter({ hasText: outfitTechniques[0].title })).toHaveCount(1)
  await expect(choices.filter({ hasText: duplicate.title })).toHaveCount(0)
  await choices.nth(1).click()
  await expect(explorer.locator('.technique-detail h3')).toHaveText(partial.title)
  expect(errors).toEqual([])
})

for (const failure of ['unavailable', 'malformed', 'unsafe-source']) {
  test(`curated lessons remain readable when the weekly response is ${failure}`, async ({ page }) => {
    const unsafe = weeklyLesson()
    unsafe.sources[0].url = 'javascript:alert(1)'
    const { explorer, errors } = await openTechniques(page, failure === 'unavailable' ? { status: 503 }
      : { data: failure === 'malformed' ? { items: null } : { items: [unsafe], status: 'ready' } })
    await expect(explorer.locator('.technique-choice')).toHaveCount(outfitTechniques.length)
    await expect(explorer.locator('.technique-detail h3')).toHaveText(outfitTechniques[0].title)
    await expect(explorer.locator('.technique-weekly-status')).not.toContainText('最近更新')
    await expect(explorer.locator('[role="alert"]')).toHaveCount(0)
    expect(errors).toEqual([])
  })
}

test('a late weekly response preserves the lesson the reader selected', async ({ page }) => {
  let release
  const gate = new Promise(resolve => { release = resolve })
  const { explorer, errors } = await openTechniques(page, { gate })
  try {
    await explorer.locator('.technique-choice').last().click()
    await expect(explorer.locator('.technique-detail h3')).toHaveText(outfitTechniques.at(-1).title)
    await explorer.getByRole('button', { name: '展开自查提示', exact: true }).click()
  } finally {
    release()
  }
  await expect(explorer.locator('.technique-choice')).toHaveCount(outfitTechniques.length + 1)
  await expect(explorer.locator('.technique-detail h3')).toHaveText(outfitTechniques.at(-1).title)
  await expect(explorer.locator('.technique-checklist')).toBeVisible()
  expect(errors).toEqual([])
})

test('mobile weekly lessons fit the page and wardrobe practice only navigates', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  const { explorer, errors, writes } = await openTechniques(page)
  await expect(explorer.locator('.technique-ai-disclosure')).toBeVisible()
  const detail = explorer.locator('.technique-detail')
  await detail.scrollIntoViewIfNeeded()
  const bounds = await detail.boundingBox()
  expect(bounds.x).toBeGreaterThanOrEqual(0)
  expect(bounds.x + bounds.width).toBeLessThanOrEqual(390)
  expect(await detail.evaluate(node => node.scrollWidth <= node.clientWidth + 1)).toBe(true)
  if (process.env.CAPTURE_WEEKLY_TECHNIQUES) {
    await explorer.screenshot({ path: '../output/weekly-techniques/mobile-390.png', animations: 'disabled' })
  }
  await explorer.getByRole('button', { name: '去我的衣橱练习', exact: true }).click()
  await expect(page).toHaveURL(/#wardrobe$/)
  expect(writes).toEqual([])
  expect(errors).toEqual([])
})

test('reading actions select a practice plan, persist only completion, and navigate to the next lesson', async ({ page }) => {
  const { explorer, errors, writes } = await openTechniques(page, { data: { items: [], status: 'disabled' } })
  await explorer.getByRole('button', { name: '本次练习 B', exact: true }).click()
  await expect(explorer.getByRole('button', { name: '已选 B', exact: true })).toHaveAttribute('aria-pressed', 'true')
  await explorer.getByRole('button', { name: '展开自查提示', exact: true }).click()
  await expect(explorer.locator('.technique-checklist')).toContainText('本次练习：B 方案')
  await explorer.getByRole('button', { name: '标记本节完成', exact: true }).click()
  await expect(explorer.getByRole('button', { name: '已完成本节', exact: true })).toHaveAttribute('aria-pressed', 'true')
  await expect(explorer.locator('.technique-checklist')).toBeVisible()
  await expect(explorer.getByRole('progressbar', { name: '本课阅读进度' })).toHaveAttribute('aria-valuenow', '1')
  const progress = await page.evaluate(() => JSON.parse(localStorage.getItem('fashion.techniques.progress.v1')))
  expect(progress).toHaveLength(1)
  expect(progress[0][0]).toBe(outfitTechniques[0].id)
  expect(progress[0][1]).toMatch(/^v1-[a-f0-9]{16}$/)
  await page.reload()
  await expect(explorer.getByRole('button', { name: '已完成本节', exact: true })).toBeVisible()
  await expect(explorer.getByRole('button', { name: '本次练习 B', exact: true })).toHaveAttribute('aria-pressed', 'false')
  await explorer.locator('.next-btn').click()
  await expect(explorer.locator('.lesson-title')).toHaveText(outfitTechniques[1].title)
  await expect(explorer.locator('.lesson-title')).toBeFocused()
  await expect(explorer.locator('.technique-checklist')).toBeHidden()
  expect(writes).toEqual([])
  expect(errors).toEqual([])
})

test('a new content version makes its previous completion mark obsolete', async ({ page }) => {
  const data = { items: [weeklyLesson()], status: 'ready' }
  const { explorer, errors } = await openTechniques(page, { data })
  await expect(explorer.locator('.lesson-title')).toHaveText(data.items[0].title)
  await explorer.getByRole('button', { name: '标记本节完成', exact: true }).click()
  data.items[0].check = '更新后的自检问题，请重新阅读后再标记。'
  await page.reload()
  await expect(explorer.locator('.lesson-title')).toHaveText(data.items[0].title)
  await expect(explorer.getByRole('button', { name: '标记本节完成', exact: true })).toHaveAttribute('aria-pressed', 'false')
  await expect(explorer.getByRole('progressbar', { name: '本课阅读进度' })).toHaveAttribute('aria-valuenow', '0')
  expect(errors).toEqual([])
})

for (const width of [1200, 820, 390]) test(`sourced course layout matches the responsive reading pattern at ${width}px`, async ({ page }) => {
  await page.setViewportSize({ width, height: 900 })
  const { explorer, errors } = await openTechniques(page, { data: { items: [], status: 'disabled' } })
  await expect(explorer.locator('.technique-choice')).toHaveCount(outfitTechniques.length)
  const rail = explorer.locator('.rail')
  const detail = explorer.locator('.technique-detail')
  const geometry = await explorer.evaluate(node => ({ scroll: node.scrollWidth, client: node.clientWidth,
    rail: getComputedStyle(node.querySelector('.rail')).position,
    direction: getComputedStyle(node.querySelector('.step-list')).display }))
  expect(geometry.scroll).toBeLessThanOrEqual(geometry.client + 1)
  expect(geometry.rail).toBe(width > 1020 ? 'sticky' : 'static')
  expect(geometry.direction).toBe(width > 1020 ? 'block' : 'flex')
  const railBounds = await rail.boundingBox()
  const detailBounds = await detail.boundingBox()
  if (width > 1020) expect(railBounds.x + railBounds.width).toBeLessThan(detailBounds.x)
  else expect(railBounds.y + railBounds.height).toBeLessThanOrEqual(detailBounds.y)
  expect(await explorer.locator('.technique-comparison').evaluate(node => getComputedStyle(node).gridTemplateColumns.split(' ').length)).toBe(width <= 680 ? 1 : 2)
  await explorer.locator('.technique-choice').last().click()
  await expect(detail.locator('.lesson-title')).toHaveText(outfitTechniques.at(-1).title)
  await detail.locator('.next-btn').click()
  await expect(detail.locator('.lesson-title')).toHaveText(outfitTechniques[0].title)
  expect(errors).toEqual([])
})

for (const imageStatus of [200, 404]) test(`a combined generated A/B visual ${imageStatus === 200 ? 'loads with an honest label' : 'falls back to the readable text plans'}`, async ({ page }) => {
  const url = '/api/v1/outfit-techniques/comparison-images/2b0be073-1234-4567-89ab-e12cd34f56ab'
  const technique = weeklyLesson({ comparisonVisual: { kind: 'generated', images: [{ url, alt: '同一套衣服的 A / B 方法示意' }], note: '仅用于理解方法。' } })
  const { explorer, errors } = await openTechniques(page, { imageStatus, data: { items: [technique], status: 'ready' } })
  const visual = explorer.locator('.technique-comparison-visual')
  await visual.scrollIntoViewIfNeeded()
  await expect(visual).toContainText('AI 对照示意图')
  await expect(visual).toContainText('不是原帖实拍或效果保证')
  if (imageStatus === 200) {
    const image = visual.getByRole('img', { name: '同一套衣服的 A / B 方法示意' })
    await expect(image).toHaveAttribute('src', url)
    await expect.poll(() => image.evaluate(node => node.complete && node.naturalWidth > 0)).toBe(true)
  } else {
    await expect(visual.getByRole('status')).toContainText('可继续阅读下方 A / B 文字方案')
    await expect(explorer.locator('.technique-comparison')).toContainText(technique.example.before)
    await expect(explorer.locator('.technique-comparison')).toContainText(technique.example.after)
  }
  expect(errors).toEqual([])
})

test('curated lessons receive comparison visuals from the API without duplicated lessons or AI-text disclosure', async ({ page }) => {
  const sourceImage = 'https://comparison-images.test/before.jpg'
  const secondImage = 'https://comparison-images.test/after.jpg'
  await page.route('https://comparison-images.test/**', route => route.fulfill({ contentType: 'image/svg+xml',
    body: '<svg xmlns="http://www.w3.org/2000/svg" width="300" height="500"><rect width="300" height="500" fill="#e4e9dd"/></svg>' }))
  const curated = { ...structuredClone(outfitTechniques[0]), generationMode: 'curated', publishedAt: null,
    comparisonVisual: { kind: 'source', images: [{ url: sourceImage, alt: '原帖中的第一版' }, { url: secondImage, alt: '原帖中的第二版' }], sourceUrl: outfitTechniques[0].sources[0].url } }
  const { explorer, errors } = await openTechniques(page, { data: { items: [curated], status: 'disabled' } })
  await expect(explorer.locator('.technique-choice')).toHaveCount(outfitTechniques.length)
  const visual = explorer.locator('.technique-comparison-visual')
  await visual.scrollIntoViewIfNeeded()
  await expect(visual).toContainText('原帖对照图')
  await expect(visual.locator('img')).toHaveCount(2)
  await expect.poll(() => visual.locator('img').evaluateAll(images => images.every(image => image.complete && image.naturalWidth > 0))).toBe(true)
  await expect(visual.getByRole('link', { name: '查看图片原帖', exact: true })).toHaveAttribute('href', curated.comparisonVisual.sourceUrl)
  await expect(explorer.locator('.technique-ai-disclosure')).toHaveCount(0)
  await expect(explorer.locator('.technique-weekly-status')).not.toContainText('最近更新')
  expect(errors).toEqual([])
})
