import { expect, test } from '@playwright/test'

const categories = ['menswear', 'menswear', 'womenswear', 'womenswear', 'niche']
const labels = { menswear: '男装穿搭', womenswear: '女装穿搭', niche: '小众穿搭欣赏' }
const svg = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 300 500"><rect width="300" height="500" fill="#d8dfd4"/><rect x="90" y="80" width="120" height="160" fill="#3b6555"/><path d="M90 240h120v210h-45V290h-30v160H90z" fill="#58606a"/></svg>'

async function openDaily(page, categoriesToUse, width) {
  await page.setViewportSize({ width, height: 900 })
  await page.clock.install()
  const items = categoriesToUse.map((category, index) => ({
    id: `daily-${index}`, platform: ['weibo', 'xiaohongshu', 'douyin'][index % 3], title: `${labels[category]}样例 ${index + 1}`,
    imageUrl: `https://daily-images.test/${index}.svg`, topicTags: [labels[category]],
    publishedAt: new Date().toISOString(), sourceUrl: `https://example.com/post/${index}`,
    evidence: { mediaType: 'image', author: '测试来源', creatorTier: category === 'niche' ? 'mainstream' : 'niche' }
  }))
  const counts = Object.fromEntries(Object.keys(labels).map(category => [category, categoriesToUse.filter(value => value === category).length]))
  const dailySelection = { target: 5, ...counts,
    missing: { menswear: 2 - counts.menswear, womenswear: 2 - counts.womenswear, niche: 1 - counts.niche },
    assignments: items.map((item, index) => ({ id: item.id, category: categoriesToUse[index], label: labels[categoriesToUse[index]], reason: categoriesToUse[index] === 'niche' ? '原帖明确标注哥特风，单独占用欣赏名额' : '原帖明确服装分类' })) }
  await page.route('https://daily-images.test/**', route => route.fulfill({ contentType: 'image/svg+xml', body: svg }))
  await page.route('**/api/**', route => {
    const path = new URL(route.request().url()).pathname
    let data = {}
    if (path.endsWith('/auth/me')) data = { username: 'daily-qa', authorities: [] }
    else if (path.endsWith('/auth/csrf')) data = { token: 'daily-qa', headerName: 'X-XSRF-TOKEN' }
    else if (path.endsWith('/trends')) data = { items, dailySelection, styles: [], sources: [], demoMode: false }
    else if (path.endsWith('/me/wardrobe')) data = []
    else if (path.endsWith('/me/style-profile')) data = null
    else if (path.endsWith('/me/recommendations')) data = { content: [], totalElements: 0, hasNext: false }
    return route.fulfill({ contentType: 'application/json', body: JSON.stringify({ code: 0, data }) })
  })
  await page.goto('/#trend')
}

for (const width of [1280, 390]) test(`shows the five outfit roles and independent niche reason at ${width}px`, async ({ page }) => {
  await openDaily(page, categories, width)
  await expect(page.locator('.trend-count')).toContainText('每日精选 5 / 5 套')
  const composition = page.getByLabel('每日精选收录情况')
  await expect(composition).toContainText('男装 2 / 2')
  await expect(composition).toContainText('女装 2 / 2')
  await expect(composition).toContainText('小众风格欣赏 1 / 1')
  await expect(composition).not.toContainText('待补充')
  await expect(page.getByLabel('内容来源')).toHaveCount(0)
  await page.getByRole('button', { name: '查看第 5 张图片：小众穿搭欣赏样例 5' }).click()
  await expect(page.locator('.brief-daily-slot')).toContainText('小众穿搭欣赏')
  await expect(page.locator('.brief-daily-slot')).toContainText('哥特风')
  await page.getByRole('button', { name: '深入分析', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '深入分析', exact: true })
  await expect(dialog.locator('.analysis-header')).toContainText('小众穿搭欣赏')
  await expect(dialog).toContainText('原帖明确标注哥特风')
  await dialog.getByRole('button', { name: '返回画廊', exact: true }).click()
  await page.getByRole('button', { name: '分析示例', exact: true }).click()
  await expect(page.getByLabel('每日精选收录情况')).toHaveCount(0)
  await page.getByRole('button', { name: '近期内容', exact: true }).click()
  await expect(page.locator('.trend-count')).toContainText('每日精选 5 / 5 套')
})

test('shows missing categories rather than declaring an incomplete daily group full', async ({ page }) => {
  await openDaily(page, ['menswear', 'menswear', 'womenswear'], 1280)
  await expect(page.locator('.trend-count')).toContainText('每日精选 3 / 5 套')
  const composition = page.getByLabel('每日精选收录情况')
  await expect(composition).toContainText('女装1套、小众欣赏1套')
  await expect(composition).toContainText('小众风格欣赏 0 / 1')
  await expect(page.locator('.gallery-card')).toHaveCount(3)
})
