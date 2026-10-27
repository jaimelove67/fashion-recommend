const AI_READING_SCOPE = '已提取正文，AI归纳；未核验原图或视频'
const FEED_STATUSES = new Set(['ready', 'collecting', 'waiting-for-quality', 'disabled'])

function text(value, maxLength = 4000) {
  return typeof value === 'string' && value.trim() && value.length <= maxLength ? value.trim() : null
}

function instant(value) {
  return typeof value === 'string' && /^\d{4}-\d{2}-\d{2}T/.test(value) && Number.isFinite(Date.parse(value)) ? value : null
}

export function safeTechniqueSourceUrl(value) {
  if (typeof value !== 'string' || value.length > 4000) return null
  try {
    const url = new URL(value)
    return ['https:', 'http:'].includes(url.protocol) && !url.username && !url.password ? url.href : null
  } catch {
    return null
  }
}

function safeTechniqueImageUrl(value) {
  if (typeof value === 'string' && /^\/api\/v1\/outfit-techniques\/comparison-images\/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value)) return value
  const url = safeTechniqueSourceUrl(value)
  if (!url || new URL(url).protocol !== 'https:') return null
  return url
}

export function normalizeTechniqueComparisonVisual(value) {
  if (!value || !['source', 'generated'].includes(value.kind) || !Array.isArray(value.images)
      || value.images.length < 1 || value.images.length > 2) return null
  const seen = new Set()
  const images = value.images.flatMap(image => {
    const url = safeTechniqueImageUrl(image?.url)
    const alt = text(image?.alt, 500)
    if (!url || !alt || seen.has(url) || (value.kind === 'source' && !url.startsWith('https:'))
        || (value.kind === 'generated' && !url.startsWith('/api/v1/outfit-techniques/comparison-images/'))) return []
    seen.add(url)
    return [{ url, alt }]
  })
  if (images.length !== value.images.length) return null
  const candidateSource = safeTechniqueSourceUrl(value.sourceUrl)
  const sourceUrl = candidateSource && new URL(candidateSource).protocol === 'https:' ? candidateSource : null
  if (value.kind === 'source' && !sourceUrl) return null
  return { kind: value.kind, images, ...(sourceUrl ? { sourceUrl } : {}),
    ...(text(value.note, 1000) ? { note: text(value.note, 1000) } : {}) }
}

// The browser stores only a public lesson ID and this content-version digest.
export function techniqueProgressVersion(technique) {
  const content = JSON.stringify([technique.title, technique.summary, technique.sourceNotes,
    technique.synthesisNote, technique.steps, technique.reason, technique.example,
    technique.suitable, technique.limit, technique.check, technique.sources, technique.comparisonVisual])
  let left = 2166136261
  let right = 3339675911
  for (let index = 0; index < content.length; index++) {
    left = Math.imul(left ^ content.charCodeAt(index), 16777619)
    right = Math.imul(right ^ content.charCodeAt(index), 2246822519)
  }
  return `v1-${(left >>> 0).toString(16).padStart(8, '0')}${(right >>> 0).toString(16).padStart(8, '0')}`
}

export function normalizeTechniqueProgress(value) {
  try {
    const entries = JSON.parse(value)
    if (!Array.isArray(entries)) return []
    return entries.filter(entry => Array.isArray(entry) && entry.length === 2
      && text(entry[0], 200) && typeof entry[1] === 'string' && /^v1-[a-f0-9]{16}$/.test(entry[1]))
      .map(([id, version]) => [id.trim(), version])
  } catch { return [] }
}

function normalizeSource(value, generationMode) {
  if (!value || typeof value !== 'object') return null
  const id = text(value.id, 200)
  const title = text(value.title, 500)
  const url = safeTechniqueSourceUrl(value.url)
  const platform = text(value.platform, 200)
  const readAt = typeof value.readAt === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value.readAt)
    && Number.isFinite(Date.parse(value.readAt)) ? value.readAt : null
  if (!id || !title || !url || !platform || !readAt) return null
  return { id, title, url, platform, readAt, author: text(value.author, 200) || '原文未署名',
    publishedAt: text(value.publishedAt, 100), scope: generationMode === 'weekly-ai' ? AI_READING_SCOPE
      : text(value.scope, 1000) || '已核读文字正文；未独立查看原图或观看视频。历史方法参考。' }
}

function normalizeWeeklyTechnique(value) {
  if (!value || typeof value !== 'object' || !['weekly-ai', 'curated'].includes(value.generationMode)) return null
  const publishedAt = instant(value.publishedAt)
  if ((value.generationMode === 'weekly-ai' && !publishedAt) || !Array.isArray(value.sources) || value.sources.length > 10
      || !Array.isArray(value.sourceNotes) || value.sourceNotes.length > 20
      || !Array.isArray(value.steps) || value.steps.length < 2 || value.steps.length > 10) return null
  const normalized = { publishedAt, generationMode: value.generationMode }
  for (const field of ['id', 'category', 'title', 'summary', 'synthesisNote', 'reason', 'suitable', 'limit', 'check']) {
    normalized[field] = text(value[field], ['id', 'category'].includes(field) ? 200 : 4000)
    if (!normalized[field]) return null
  }
  normalized.sources = value.sources.map(source => normalizeSource(source, value.generationMode))
  if (!normalized.sources.length || normalized.sources.some(source => !source)
      || new Set(normalized.sources.map(source => source.id)).size !== normalized.sources.length
      || new Set(normalized.sources.map(source => source.url)).size !== normalized.sources.length) return null
  normalized.sourceNotes = value.sourceNotes.map(note => {
    const sourceId = text(note?.sourceId, 200)
    const point = text(note?.point)
    return sourceId && point && normalized.sources.some(source => source.id === sourceId) ? { sourceId, point } : null
  })
  if (!normalized.sourceNotes.length || normalized.sourceNotes.some(note => !note)) return null
  normalized.steps = value.steps.map(step => text(step))
  if (normalized.steps.some(step => !step)) return null
  const before = text(value.example?.before)
  const after = text(value.example?.after)
  if (!before || !after) return null
  normalized.example = { before, after }
  normalized.comparisonVisual = normalizeTechniqueComparisonVisual(value.comparisonVisual)
  return normalized
}

export function normalizeOutfitTechniqueFeed(payload) {
  if (payload?.code !== 0 || !payload.data || !Array.isArray(payload.data.items)) {
    return { items: [], latestPublishedAt: null, status: null }
  }
  const items = payload.data.items.slice(0, 60).map(normalizeWeeklyTechnique).filter(Boolean)
    .sort((left, right) => (Date.parse(right.publishedAt) || 0) - (Date.parse(left.publishedAt) || 0))
  return {
    items,
    // Only advertise dates backed by a valid lesson that can actually be read.
    latestPublishedAt: items.find(item => item.generationMode === 'weekly-ai')?.publishedAt || null,
    status: FEED_STATUSES.has(payload.data.status) ? payload.data.status : null
  }
}

function titleKey(value) {
  return value.normalize('NFKC').toLowerCase().replace(/[\p{P}\p{Z}\s]+/gu, '')
}

function sourceKey(value) {
  const url = new URL(value)
  url.hash = ''
  for (const key of [...url.searchParams.keys()]) {
    if (/^utm_/i.test(key) || ['fbclid', 'gclid', 'mc_cid', 'mc_eid', '_ga'].includes(key.toLowerCase())) {
      url.searchParams.delete(key)
    }
  }
  return `${url.host}${url.pathname.replace(/\/+$/, '')}${url.search}`
}

function sourceSetKey(sources) {
  const urls = (sources || []).flatMap(source => {
    const safe = safeTechniqueSourceUrl(source.url)
    return safe ? [sourceKey(safe)] : []
  })
  return urls.length ? [...new Set(urls)].sort().join('\n') : null
}

export function mergeOutfitTechniques(weeklyItems, curatedItems) {
  const ids = new Set()
  const titles = new Set()
  const sourceSets = new Set()
  const merged = []
  for (const item of [...weeklyItems, ...curatedItems]) {
    const sources = sourceSetKey(item.sources)
    if (ids.has(item.id) || titles.has(titleKey(item.title)) || (sources && sourceSets.has(sources))) continue
    merged.push(item)
    ids.add(item.id)
    titles.add(titleKey(item.title))
    if (sources) sourceSets.add(sources)
  }
  return merged
}

export function formatTechniquePublication(value) {
  return instant(value) ? new Intl.DateTimeFormat('zh-CN', { timeZone: 'Asia/Shanghai',
    year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date(value)).replaceAll('/', '-') : ''
}

export function formatTechniqueSourcePublication(value) {
  const date = typeof value === 'string' ? /^\d{4}-\d{2}-\d{2}/.exec(value)?.[0] : null
  const parsed = date ? new Date(`${date}T00:00:00Z`) : null
  if (!parsed || !Number.isFinite(parsed.getTime()) || parsed.toISOString().slice(0, 10) !== date) {
    return '原帖未标注日期'
  }
  if (value === date) return date
  if (/(?:Z|[+-]\d{2}:\d{2})$/.test(value)) {
    const formatted = formatTechniquePublication(value)
    if (formatted) return formatted
  }
  return '原帖未标注日期'
}
