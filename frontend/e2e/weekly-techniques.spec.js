import { expect, test } from '@playwright/test'
import { outfitTechniques } from '../src/data/outfitTechniques.js'

function weeklyLesson(overrides = {}) {
  const base = structuredClone(outfitTechniques[0])
  return { ...base, id: 'weekly-layering', title: '本周技巧：先留空间，再整理领口', category: '叠穿',
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
  await expect(detail.locator('.technique-ai-disclosure')).toContainText('AI 根据原帖文字归纳')
  await expect(detail.locator('.scene-problem')).toHaveText(weeklyLesson().example.before)
  await expect(detail.locator('.technique-solution')).toContainText(weeklyLesson().example.after)
  await expect(detail.locator('.technique-source-list')).toContainText('原文未署名')
  await expect(detail.locator('.technique-source-list')).not.toContainText('正文提取日期')
  await expect(detail.locator('.technique-source-meta').first()).toContainText('2018-02-26')
  await expect(detail.locator('.technique-source-meta').last()).toContainText('原帖未标注日期')
  await expect(detail.locator('.technique-source-meta').first()).not.toContainText('T18:40:10Z')
  await expect(detail.locator('.technique-source-list')).not.toContainText('阅读范围')
  await expect(detail).not.toContainText('假冒人工核验声明')
  if (process.env.CAPTURE_WEEKLY_TECHNIQUES) {
    await explorer.screenshot({ path: '../output/weekly-techniques/desktop.png', animations: 'disabled' })
  }
  await explorer.getByRole('navigation', { name: '按技巧主题筛选' }).getByRole('button', { name: '叠穿', exact: true }).click()
  await expect(choices).toHaveCount(2)
  await choices.last().click()
  await expect(detail.locator('.lesson-title')).toHaveText(outfitTechniques[3].title)
  await expect(detail.locator('.technique-ai-disclosure')).toHaveCount(0)
  expect(errors).toEqual([])
})

test('source notes are grouped with the correct post when another post has no notes', async ({ page }) => {
  const lesson = weeklyLesson()
  lesson.sourceNotes = [
    { sourceId: lesson.sources[0].id, point: '第一篇原帖的第一条要点。' },
    { sourceId: lesson.sources[0].id, point: '第一篇原帖的第二条要点。' }
  ]
  const { explorer, errors } = await openTechniques(page, { data: { items: [lesson], status: 'ready' } })
  const detail = explorer.locator('.technique-detail')
  const rows = detail.locator('.technique-source-list > li')
  await expect(detail.getByRole('heading', { name: '原帖要点与参考来源', exact: true })).toHaveCount(1)
  await expect(rows).toHaveCount(2)
  await expect(rows.first().locator('.technique-source-points p')).toHaveText(lesson.sourceNotes.map(note => note.point))
  await expect(rows.first().getByRole('link')).toHaveCount(1)
  await expect(rows.first().getByRole('link')).toHaveAttribute('href', lesson.sources[0].url)
  await expect(rows.last().locator('.technique-source-points p')).toHaveCount(0)
  await expect(rows.last()).toContainText('这篇原帖的要点仍在整理。')
  await expect(rows.last().getByRole('link')).toHaveCount(1)
  await expect(rows.last().getByRole('link')).toHaveAttribute('href', lesson.sources[1].url)
  await expect(rows.last()).not.toContainText('阅读范围')
  await expect(rows.last()).not.toContainText('正文提取日期')
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
    await explorer.getByRole('button', { name: '收藏', exact: true }).click()
  } finally {
    release()
  }
  await expect(explorer.locator('.technique-choice')).toHaveCount(outfitTechniques.length + 1)
  await expect(explorer.locator('.technique-detail h3')).toHaveText(outfitTechniques.at(-1).title)
  await expect(explorer.getByRole('button', { name: '已收藏', exact: true })).toHaveAttribute('aria-pressed', 'true')
  expect(errors).toEqual([])
})

test('mobile weekly lessons fit the page and opening the wardrobe only navigates', async ({ page }) => {
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
  await explorer.getByRole('button', { name: '查看我的衣橱', exact: true }).click()
  await expect(page).toHaveURL(/#wardrobe$/)
  expect(writes).toEqual([])
  expect(errors).toEqual([])
})

test('reading has no task controls, ignores old progress and preserves favorites and navigation', async ({ page }) => {
  const legacy = JSON.stringify([[outfitTechniques[0].id, 'v1-1234567890abcdef']])
  await page.addInitScript(value => {
    if (!localStorage.getItem('fashion.techniques.progress.v1')) localStorage.setItem('fashion.techniques.progress.v1', value)
  }, legacy)
  const { explorer, errors, writes } = await openTechniques(page, { data: { items: [], status: 'disabled' } })
  await expect(explorer.getByRole('progressbar')).toHaveCount(0)
  await expect(explorer.locator('.technique-practice, .done-toggle, .technique-plan-toggle, .technique-checklist')).toHaveCount(0)
  await expect(explorer).not.toContainText('步练习')
  await explorer.getByRole('button', { name: '收藏', exact: true }).click()
  await page.reload()
  await expect(explorer.getByRole('button', { name: '已收藏', exact: true })).toHaveAttribute('aria-pressed', 'true')
  await explorer.locator('.next-btn').click()
  await expect(explorer.locator('.lesson-title')).toHaveText(outfitTechniques[1].title)
  await expect(explorer.locator('.lesson-title')).toBeFocused()
  expect(await page.evaluate(() => localStorage.getItem('fashion.techniques.progress.v1'))).toBe(legacy)
  expect(writes).toEqual([])
  expect(errors).toEqual([])
})

test('a rewritten curated API case uses its own problem and solution without unrelated fixed images', async ({ page }) => {
  const rewritten = { ...structuredClone(outfitTechniques[1]), generationMode: 'curated',
    example: { before: '换成新场景中的条纹上衣后，分界变得太复杂。', after: '保留裤子，先比较一件素色上衣。' } }
  const data = { items: [rewritten], status: 'ready' }
  const { explorer, errors } = await openTechniques(page, { data })
  await explorer.getByRole('button', { name: rewritten.title, exact: true }).click()
  await expect(explorer.locator('.scene-problem')).toHaveText(rewritten.example.before)
  await expect(explorer.locator('.technique-solution')).toContainText(rewritten.example.after)
  await expect(explorer.locator('.technique-example img')).toHaveCount(0)
  expect(errors).toEqual([])
})

test('a missing case illustration keeps the solution readable and changing techniques restores images', async ({ page }) => {
  await page.route('**/assets/techniques/proportion-comparison.png', route => route.fulfill({ status: 404, body: '' }))
  const { explorer, errors } = await openTechniques(page, { data: { items: [], status: 'disabled' } })
  await explorer.getByRole('button', { name: outfitTechniques[1].title, exact: true }).click()
  const solution = explorer.locator('.technique-example')
  await solution.scrollIntoViewIfNeeded()
  await expect(solution.getByRole('status')).toHaveCount(2)
  await expect(solution.locator('.technique-solution')).toContainText('先让上下近色')
  await expect(solution.locator('.compare-description')).toHaveCount(2)
  await explorer.getByRole('button', { name: outfitTechniques[0].title, exact: true }).click()
  await solution.scrollIntoViewIfNeeded()
  await expect(solution.getByRole('status')).toHaveCount(0)
  await expect.poll(() => solution.locator('img').evaluateAll(images => images.length === 2
    && images.every(image => image.complete && image.naturalWidth > 0))).toBe(true)
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
  await expect(visual).toContainText('AI 搭配示意')
  await expect(explorer.locator('.technique-scene img')).toHaveCount(0)
  if (imageStatus === 200) {
    const image = visual.getByRole('img', { name: '同一套衣服的 A / B 方法示意' })
    await expect(image).toHaveAttribute('src', url)
    await expect.poll(() => image.evaluate(node => node.complete && node.naturalWidth > 0)).toBe(true)
  } else {
    await expect(visual.getByRole('status')).toContainText('可继续阅读下面的调整说明')
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
