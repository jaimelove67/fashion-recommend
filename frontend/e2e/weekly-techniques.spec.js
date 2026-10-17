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

async function openTechniques(page, { status = 200, data, gate } = {}) {
  const errors = []
  const writes = []
  page.on('pageerror', error => errors.push(error.message))
  await page.route('**/api/**', async route => {
    const request = route.request()
    const path = new URL(request.url()).pathname
    if (request.method() !== 'GET') writes.push(path)
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
