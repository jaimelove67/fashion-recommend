import { expect, test } from '@playwright/test'
import { outfitTechniques } from '../src/data/outfitTechniques.js'
import { getOutfitTechniqueCase } from '../src/data/outfitTechniqueCases.js'

const sourcedTechniques = outfitTechniques.filter(technique => technique.sources?.length)

async function openTechniques(page, { feedFails = false } = {}) {
  const writes = []
  await page.route('**/api/**', async route => {
    const request = route.request()
    const pathname = new URL(request.url()).pathname
    if (!['GET', 'HEAD'].includes(request.method())) writes.push(pathname)
    if (pathname === '/api/v1/trends' && feedFails) {
      return route.fulfill({ status: 503, contentType: 'application/json', body: JSON.stringify({ code: 503, message: '来源暂时不可用' }) })
    }
    let data = {}
    if (pathname === '/api/v1/auth/csrf') data = { token: 'technique-qa', headerName: 'X-XSRF-TOKEN' }
    else if (pathname === '/api/v1/auth/me') data = { username: 'technique-qa', authorities: [] }
    else if (pathname === '/api/v1/trends') data = { items: [], styles: [], demoMode: false, sources: [] }
    else if (pathname === '/api/v1/me/wardrobe') data = []
    else if (pathname === '/api/v1/me/recommendations') data = { content: [], totalElements: 0, page: 0, hasNext: false }
    else if (pathname === '/api/v1/me/style-profile') data = null
    await route.fulfill({ contentType: 'application/json', body: JSON.stringify({ code: 0, data }) })
  })
  const trendResponse = page.waitForResponse(response => new URL(response.url()).pathname === '/api/v1/trends')
  if (new URL(page.url()).hash === '#trend') await page.reload()
  else await page.goto('/#trend')
  expect((await trendResponse).status()).toBe(feedFails ? 503 : 200)
  const explorer = page.getByRole('region', { name: '穿搭技巧', exact: true })
  await expect(explorer).toBeVisible()
  return { explorer, writes }
}

test('sourced techniques describe the problem first and show the matching solution once', async ({ page }) => {
  const { explorer } = await openTechniques(page)
  const filters = explorer.getByRole('navigation', { name: '按技巧主题筛选' })
  const choices = explorer.locator('.technique-choice')
  const detail = explorer.locator('article.technique-detail')
  expect(sourcedTechniques.length).toBeGreaterThan(1)
  await expect(choices).toHaveCount(sourcedTechniques.length)
  const category = sourcedTechniques[1].category
  await filters.getByRole('button', { name: category, exact: true }).click()
  await expect(choices).toHaveCount(sourcedTechniques.filter(technique => technique.category === category).length)
  await expect(choices.first()).toHaveAttribute('aria-pressed', 'true')
  for (const text of ['原帖要点与参考来源', '搭配要点', '为什么这样搭', '调整前', '调整后', '适合什么时候', '注意取舍']) {
    await expect(detail.getByText(text, { exact: true })).toBeVisible()
  }
  await expect(detail.locator('.technique-example-note')).toHaveText('AI 搭配示意')
  await filters.getByRole('button', { name: '全部', exact: true }).click()
  await expect(choices).toHaveCount(sourcedTechniques.length)
  for (const [index, technique] of sourcedTechniques.entries()) {
    const study = getOutfitTechniqueCase(technique)
    await choices.nth(index).click()
    await expect(detail.locator('.scene-question')).toHaveText(study.question)
    await expect(detail.locator('.scene-problem')).toHaveText(study.problem)
    await expect(detail.locator('.technique-solution')).toContainText(study.solution)
    await expect(detail.locator('.technique-scene img')).toHaveCount(0)
    const images = detail.locator('.technique-example img')
    await detail.locator('.technique-example').scrollIntoViewIfNeeded()
    await expect(images).toHaveCount(2)
    await expect.poll(() => images.evaluateAll(nodes => nodes.every(node => node.complete && node.naturalWidth > 0))).toBe(true)
    await expect(images.first()).toHaveAttribute('alt', study.images[0].alt)
    await expect(images.last()).toHaveAttribute('alt', study.images[1].alt)
    const order = await detail.evaluate(node => node.querySelector('.technique-scene').compareDocumentPosition(node.querySelector('.technique-example')))
    expect(order & 4).toBeTruthy()
    await expect(detail.locator('.technique-practice, .done-toggle, .technique-plan-toggle')).toHaveCount(0)
  }
  await expect(explorer.getByRole('progressbar')).toHaveCount(0)
  await expect(explorer).not.toContainText('步练习')
})

test('post summaries retain attributable source notes and links with an empty or unavailable trend feed', async ({ page }) => {
  const empty = await openTechniques(page)
  await expect(empty.explorer.locator('.technique-choice')).toHaveCount(sourcedTechniques.length)
  await expect(empty.explorer.locator('article.technique-detail')).toContainText('原帖要点与参考来源')

  await page.unroute('**/api/**')
  const { explorer } = await openTechniques(page, { feedFails: true })
  await expect(explorer.locator('.technique-choice')).toHaveCount(sourcedTechniques.length)
  for (const technique of sourcedTechniques) {
    const choice = explorer.locator('.technique-choice').filter({ has: page.getByText(technique.title, { exact: true }) })
    await expect(choice.locator('.technique-source-count')).toHaveText(`${technique.sources.length} 篇原帖`)
    await choice.click()
    const detail = explorer.locator('article.technique-detail')
    await expect(detail.getByRole('heading', { name: technique.sources.length > 1 ? '跨帖归纳' : '本帖归纳', exact: true })).toBeVisible()
    await expect(detail.locator('.technique-synthesis')).toContainText(technique.synthesisNote)
    const sources = detail.locator('.technique-source-list li')
    await expect(sources).toHaveCount(technique.sources.length)
    for (const [index, source] of technique.sources.entries()) {
      const url = new URL(source.url)
      expect(['http:', 'https:']).toContain(url.protocol)
      expect(url.username || url.password).toBe('')
      const sourceRow = sources.nth(index)
      const link = sourceRow.getByRole('link', { name: source.title, exact: true })
      await expect(link).toHaveAttribute('href', url.href)
      await expect(link).toHaveAttribute('target', '_blank')
      await expect(link).toHaveAttribute('rel', 'noopener noreferrer')
      await expect(sourceRow.getByRole('link')).toHaveCount(1)
      await expect(sourceRow).toContainText(source.author)
      await expect(sourceRow).toContainText(source.platform)
      await expect(sourceRow).not.toContainText('阅读日期')
      await expect(sourceRow).not.toContainText('阅读范围')
      await expect(sourceRow.locator('.technique-source-points p')).toHaveText(
        technique.sourceNotes.filter(note => note.sourceId === source.id).map(note => note.point)
      )
    }
    expect(technique.sourceNotes.length).toBeGreaterThan(0)
  }
  await expect(explorer.getByRole('button', { name: '查看我的衣橱', exact: true })).toBeEnabled()
})

test('mobile techniques stay within the screen and opening the wardrobe only navigates', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  const { explorer, writes } = await openTechniques(page)
  await explorer.getByRole('navigation', { name: '按技巧主题筛选' }).getByRole('button', { name: sourcedTechniques.at(-1).category, exact: true }).click()
  const detail = explorer.locator('article.technique-detail')
  await detail.scrollIntoViewIfNeeded()
  for (const element of [explorer, detail]) {
    const bounds = await element.boundingBox()
    expect(bounds.x).toBeGreaterThanOrEqual(0)
    expect(bounds.x + bounds.width).toBeLessThanOrEqual(390)
  }
  expect(await detail.evaluate(node => node.scrollWidth <= node.clientWidth + 1)).toBe(true)
  await explorer.getByRole('button', { name: '查看我的衣橱', exact: true }).click()
  await expect(page).toHaveURL(/#wardrobe$/)
  await expect(page.getByRole('heading', { name: '我的衣橱', exact: true, level: 1 })).toBeVisible()
  expect(writes).toEqual([])
})
