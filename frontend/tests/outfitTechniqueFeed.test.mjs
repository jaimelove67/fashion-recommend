import assert from 'node:assert/strict'
import test from 'node:test'
import { outfitTechniques } from '../src/data/outfitTechniques.js'
import { formatTechniquePublication, formatTechniqueSourcePublication, mergeOutfitTechniques, normalizeOutfitTechniqueFeed,
  normalizeTechniqueComparisonVisual, normalizeTechniqueProgress, safeTechniqueSourceUrl,
  techniqueProgressVersion } from '../src/utils/outfitTechniqueFeed.js'

function lesson(overrides = {}) {
  return { ...structuredClone(outfitTechniques[0]), id: 'weekly-first', title: '一周一练：领口层次',
    publishedAt: '2026-10-16T02:00:00Z', generationMode: 'weekly-ai',
    sources: [
      { id: 'weekly-a', title: 'Source A', url: 'https://example.test/lesson-a', author: null,
        platform: '作者博客', readAt: '2026-10-16', publishedAt: null, scope: '已人工逐条核验' },
      { id: 'weekly-b', title: 'Source B', url: 'https://example.test/lesson-b', author: 'Author B',
        platform: '作者博客', readAt: '2026-10-16', publishedAt: '2020-01-01' }
    ], sourceNotes: [{ sourceId: 'weekly-a', point: 'First grounded note' },
      { sourceId: 'weekly-b', point: 'Second grounded note' }], ...overrides }
}

function feed(items, extras = {}) {
  return { code: 0, data: { items, cadence: 'weekly', status: 'ready', ...extras } }
}

test('normalizes readable weekly lessons with honest AI reading scope and absent authors', () => {
  const result = normalizeOutfitTechniqueFeed(feed([lesson()], { latestPublishedAt: '2027-01-01T00:00:00Z' }))
  assert.equal(result.items.length, 1)
  assert.equal(result.items[0].sources[0].author, '原文未署名')
  assert.equal(result.items[0].sources[0].publishedAt, null)
  assert.equal(result.items[0].sources[0].scope, '已提取正文，AI归纳；未核验原图或视频')
  assert.equal(result.latestPublishedAt, '2026-10-16T02:00:00Z')
  assert.equal(result.status, 'ready')
})

test('ignores failed envelopes and malformed items while preserving valid rows', () => {
  for (const payload of [null, {}, [], { code: 500, data: { items: [lesson()] } }, { code: 0, data: { items: {} } }]) {
    assert.deepEqual(normalizeOutfitTechniqueFeed(payload).items, [])
  }
  const cases = [null, {}, lesson({ steps: null }), lesson({ example: { before: 'only before' } }),
    lesson({ summary: '' }), lesson({ generationMode: 'manual' }), lesson({ publishedAt: 'not-a-date' }),
    lesson({ sourceNotes: [{ sourceId: 'unknown', point: 'No source' }] }),
    lesson({ sources: [null] }), lesson({ sources: [lesson().sources[0], lesson().sources[0]] })]
  assert.equal(normalizeOutfitTechniqueFeed(feed([...cases, lesson()])).items.length, 1)
  assert.equal(normalizeOutfitTechniqueFeed(feed(cases)).latestPublishedAt, null)
})

test('only accepts safe absolute source URLs and rejects rows with an unsafe source', () => {
  for (const url of ['javascript:alert(1)', 'data:text/html,hi', '/relative', 'https://user:secret@example.test/', null]) {
    assert.equal(safeTechniqueSourceUrl(url), null)
    const entry = lesson()
    entry.sources[0].url = url
    assert.equal(normalizeOutfitTechniqueFeed(feed([entry])).items.length, 0)
  }
  assert.equal(safeTechniqueSourceUrl('https://example.test/article'), 'https://example.test/article')
})

test('places newest lessons first and deduplicates ids, normalized titles and full source sets', () => {
  const older = lesson({ id: 'older', title: 'Older lesson', publishedAt: '2026-10-08T02:00:00Z' })
  const newest = lesson({ id: 'newest', title: 'Newest lesson' })
  const sourceDuplicate = lesson({ id: 'third', title: 'Different words', publishedAt: '2026-10-01T02:00:00Z',
    sources: lesson().sources.map(source => ({ ...source, url: `${source.url}?utm_source=share#part` })) })
  const normalized = normalizeOutfitTechniqueFeed(feed([older, sourceDuplicate, newest])).items
  const merged = mergeOutfitTechniques(normalized, [
    { ...outfitTechniques[0], id: 'newest' },
    { ...outfitTechniques[1], title: ' Ｎｅｗｅｓｔ：lesson！ ' },
    { ...outfitTechniques[2], sources: [
      { url: 'https://example.test/lesson-b/#other' }, { url: 'https://example.test/lesson-a?fbclid=share' }
    ] },
    ...outfitTechniques.slice(3)
  ])
  assert.deepEqual(merged.map(item => item.id), ['newest', ...outfitTechniques.slice(3).map(item => item.id)])
})

test('retains distinct weekly and curated topics that share only one source', () => {
  const newest = lesson({ id: 'newest', title: 'Newest topic' })
  const partial = lesson({ id: 'partial', title: 'Another topic', publishedAt: '2026-10-09T02:00:00Z',
    sources: lesson().sources.map((source, index) => index ? { ...source, url: 'https://example.test/lesson-c' } : source) })
  const curated = { ...outfitTechniques[2], sources: [
    { url: 'https://example.test/lesson-a' }, { url: 'https://example.test/lesson-d' }
  ] }
  const merged = mergeOutfitTechniques(normalizeOutfitTechniqueFeed(feed([partial, newest])).items, [curated])
  assert.deepEqual(merged.map(item => item.id), ['newest', 'partial', curated.id])
})

test('keeps curated lessons sharing sources when there are no new weekly lessons', () => {
  assert.deepEqual(mergeOutfitTechniques([], outfitTechniques), outfitTechniques)
})

test('formats publication dates in Shanghai time and handles invalid values', () => {
  assert.equal(formatTechniquePublication('2026-10-16T18:00:00Z'), '2026-10-17')
  assert.equal(formatTechniquePublication('invalid'), '')
})

test('formats source instants in Shanghai, preserves valid dates and labels unknown dates', () => {
  assert.equal(formatTechniqueSourcePublication('2018-02-25T06:40:10Z'), '2018-02-25')
  assert.equal(formatTechniqueSourcePublication('2018-02-25T18:40:10Z'), '2018-02-26')
  assert.equal(formatTechniqueSourcePublication('2018-02-25T23:40:10-05:00'), '2018-02-26')
  assert.equal(formatTechniqueSourcePublication('2024-02-29'), '2024-02-29')
  for (const value of [null, undefined, '', 'unknown', '2026-02-30', '2026-02-30T12:00:00Z', '2026-10-16T12:00:00', 123]) {
    assert.equal(formatTechniqueSourcePublication(value), '原帖未标注日期')
  }
})

test('accepts sourced curated API lessons without converting reading scope or publication into AI claims', () => {
  const curated = { ...structuredClone(outfitTechniques[0]), generationMode: 'curated', publishedAt: null,
    comparisonVisual: { kind: 'generated', images: [{ url: '/api/v1/outfit-techniques/comparison-images/2b0be073-1234-4567-89ab-e12cd34f56ab', alt: '同一件上衣的 A / B 配色练习' }] } }
  const result = normalizeOutfitTechniqueFeed(feed([curated]))
  assert.equal(result.items.length, 1)
  assert.equal(result.items[0].generationMode, 'curated')
  assert.equal(result.items[0].publishedAt, null)
  assert.equal(result.items[0].sources[0].scope, curated.sources[0].scope)
  assert.equal(result.latestPublishedAt, null)
  assert.equal(mergeOutfitTechniques(result.items, outfitTechniques).length, outfitTechniques.length)
  assert.equal(mergeOutfitTechniques(result.items, outfitTechniques)[0].comparisonVisual.kind, 'generated')
})

test('normalizes one combined A/B illustration and a pair of attributable source images', () => {
  const illustration = { kind: 'generated', images: [{ url: '/api/v1/outfit-techniques/comparison-images/2b0be073-1234-4567-89ab-e12cd34f56ab', alt: ' A / B 两种搭法 ' }], note: ' 同一人物与固定光线下的示意 ' }
  assert.deepEqual(normalizeTechniqueComparisonVisual(illustration), { ...illustration,
    images: [{ ...illustration.images[0], alt: 'A / B 两种搭法' }], note: '同一人物与固定光线下的示意' })
  const source = { kind: 'source', images: [{ url: 'https://images.example.test/left.jpg', alt: '原文第一种搭法' },
    { url: 'https://images.example.test/right.jpg', alt: '原文第二种搭法' }], sourceUrl: 'https://example.test/article' }
  assert.deepEqual(normalizeTechniqueComparisonVisual(source), source)
})

test('rejects unsafe or ambiguous comparison assets without discarding the readable lesson', () => {
  const goodImage = { url: 'https://images.example.test/comparison.jpg', alt: '原文对照图' }
  const badVisuals = [null, {}, { kind: 'photo', images: [goodImage] },
    { kind: 'generated', images: [goodImage] },
    { kind: 'source', sourceUrl: 'https://example.test/article', images: [{ url: '/api/v1/outfit-techniques/comparison-images/2b0be073-1234-4567-89ab-e12cd34f56ab', alt: '不能把生成图标作原图' }] },
    { kind: 'source', images: [goodImage] }, { kind: 'source', images: [goodImage], sourceUrl: 'javascript:alert(1)' },
    { kind: 'generated', images: [] }, { kind: 'generated', images: [goodImage, goodImage] },
    { kind: 'generated', images: [goodImage, { ...goodImage, url: 'https://images.example.test/second.jpg' }, { ...goodImage, url: 'https://images.example.test/third.jpg' }] },
    ...['javascript:alert(1)', 'data:image/svg+xml,unsafe', 'http://images.example.test/a.jpg', '//images.example.test/a.jpg',
      'https://user:secret@images.example.test/a.jpg', '/api/v1/me/photo', '/api/v1/outfit-techniques/foo/comparison-image',
      '/api/v1/outfit-techniques/comparison-images/not-a-uuid', '/api/v1/outfit-techniques/comparison-images/2b0be073-1234-4567-89ab-e12cd34f56ab?token=secret']
      .map(url => ({ kind: 'generated', images: [{ url, alt: '示意' }] })),
    { kind: 'generated', images: [{ ...goodImage, alt: '' }] }]
  for (const comparisonVisual of badVisuals) {
    assert.equal(normalizeTechniqueComparisonVisual(comparisonVisual), null)
    const normalized = normalizeOutfitTechniqueFeed(feed([lesson({ comparisonVisual })]))
    assert.equal(normalized.items.length, 1)
    assert.equal(normalized.items[0].comparisonVisual, null)
  }
})

test('browser reading progress stores only public IDs and changes version when lesson content changes', () => {
  const first = lesson()
  const version = techniqueProgressVersion(first)
  assert.match(version, /^v1-[a-f0-9]{16}$/)
  assert.equal(techniqueProgressVersion(structuredClone(first)), version)
  assert.notEqual(techniqueProgressVersion({ ...first, check: '更新后的自检问题' }), version)
  assert.notEqual(techniqueProgressVersion({ ...first, example: { ...first.example, after: '新练习' } }), version)
  const stored = JSON.stringify([[first.id, version]])
  assert.deepEqual(normalizeTechniqueProgress(stored), [[first.id, version]])
  for (const value of [null, '', 'malformed', '{}', JSON.stringify([{ username: 'private', lesson: first }]),
    JSON.stringify([['id', 'not-a-version'], [null, version], ['id', version, 'extra']])]) {
    assert.deepEqual(normalizeTechniqueProgress(value), [])
  }
})
