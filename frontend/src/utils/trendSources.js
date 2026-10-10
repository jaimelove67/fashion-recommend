export const TREND_PLATFORM_NAMES = {
  douyin: '抖音',
  weibo: '微博', xiaohongshu: '小红书',
  editorial: '时尚编辑精选',
  'configured-demo': '穿搭参考',
  'configured-feed': '已连接数据源',
  'web-scrape': '公开网页'
}

const TREND_SOURCE_STATES = {
  ready: '已连接',
  stale: '已过期',
  unavailable: '采集失败',
  unconfigured: '未配置',
  pending: '等待采集'
}

const EXCLUSION_REASON_NAMES = {
  'outside-window': '超出时间范围',
  'no-eligible-items': '暂无可展示内容',
  'no-valid-image': '暂无可用图片',
  'all-items-expired': '采集内容已过期'
}

function countValue(value) {
  if (value === null || value === undefined || value === '') return null
  const number = Number(value)
  return Number.isFinite(number) ? number : null
}

export function trendPlatformName(value) {
  return TREND_PLATFORM_NAMES[value] || value || '内容来源'
}

export function isConfiguredDemoTrend(item) {
  return item?.platform === 'configured-demo'
}

export function shouldShowStaleTrend(item) {
  return Boolean(item?.stale) && !isConfiguredDemoTrend(item)
}

export function externalTrendSourceUrl(item) {
  if (isConfiguredDemoTrend(item)) return ''
  const value = String(item?.sourceUrl || '').trim()
  return /^https?:\/\//i.test(value) ? value : ''
}

export function trendSourceStateName(value) {
  return TREND_SOURCE_STATES[String(value || '').toLowerCase()] || '状态未知'
}

export function trendSourceCounts(source) {
  const collected = countValue(source?.itemCount ?? source?.collectedCount)
  const eligible = countValue(source?.eligibleCount)
  const labels = []
  if (collected !== null) labels.push(`采集 ${collected} 条`)
  if (eligible !== null) labels.push(`可展示 ${eligible} 条`)
  return labels.join(' / ')
}

export function trendSourceExclusionReason(source) {
  const reason = String(source?.exclusionReason || '').trim()
  if (!reason) return ''
  return EXCLUSION_REASON_NAMES[reason] || reason
}

export function trendSourceMessage(source) {
  const message = String(source?.message || '').trim()
  const state = trendSourceStateName(source?.state)
  return message ? `${state} · ${message}` : state
}
