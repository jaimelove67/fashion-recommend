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

function normalizeSource(value) {
  if (!value || typeof value !== 'object') return null
  const id = text(value.id, 200)
  const title = text(value.title, 500)
  const url = safeTechniqueSourceUrl(value.url)
  const platform = text(value.platform, 200)
  const readAt = typeof value.readAt === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value.readAt)
    && Number.isFinite(Date.parse(value.readAt)) ? value.readAt : null
  if (!id || !title || !url || !platform || !readAt) return null
  return { id, title, url, platform, readAt, author: text(value.author, 200) || '原文未署名',
    publishedAt: text(value.publishedAt, 100), scope: AI_READING_SCOPE }
}

function normalizeWeeklyTechnique(value) {
  if (!value || typeof value !== 'object' || value.generationMode !== 'weekly-ai') return null
  const publishedAt = instant(value.publishedAt)
  if (!publishedAt || !Array.isArray(value.sources) || value.sources.length > 10
      || !Array.isArray(value.sourceNotes) || value.sourceNotes.length > 20
      || !Array.isArray(value.steps) || value.steps.length < 2 || value.steps.length > 10) return null
  const normalized = { publishedAt, generationMode: 'weekly-ai' }
  for (const field of ['id', 'category', 'title', 'summary', 'synthesisNote', 'reason', 'suitable', 'limit', 'check']) {
    normalized[field] = text(value[field], ['id', 'category'].includes(field) ? 200 : 4000)
    if (!normalized[field]) return null
  }
  normalized.sources = value.sources.map(normalizeSource)
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
  return normalized
}

export function normalizeOutfitTechniqueFeed(payload) {
  if (payload?.code !== 0 || !payload.data || !Array.isArray(payload.data.items)) {
    return { items: [], latestPublishedAt: null, status: null }
  }
  const items = payload.data.items.slice(0, 60).map(normalizeWeeklyTechnique).filter(Boolean)
    .sort((left, right) => Date.parse(right.publishedAt) - Date.parse(left.publishedAt))
  return {
    items,
    // Only advertise dates backed by a valid lesson that can actually be read.
    latestPublishedAt: items[0]?.publishedAt || null,
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
