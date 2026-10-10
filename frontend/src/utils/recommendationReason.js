const MAX_REASON_LENGTH = 180
const INTERNAL_OR_PROFILE_DUMP = /评分|反馈|闭环|精准响应|完全覆盖|全部来自衣橱|衣橱仅|用户为|用户是|人工确认|身高|体重|脸型|方脸|圆脸|面部|头发|直发|腿部线条|\d+(?:\.\d+)?\s*(?:cm|厘米|kg|公斤|℃|°C)/i
const GARMENT_ATTRIBUTES = ['挺括', '纯棉', '棉麻', '真丝', '丝质', '羊毛', '羊绒', '真皮', '直筒', '阔腿', '修身', '宽松', '高腰', '透气', '防水', '防滑']

export function formatRecommendationTitle(recommendation) {
  const summary = String(recommendation?.summary || '').trim()
  if (summary && summary.length <= 40 && !INTERNAL_OR_PROFILE_DUMP.test(summary) && !/[\r\n]/.test(summary)) return summary
  const items = Array.isArray(recommendation?.items) ? recommendation.items : []
  const colors = [...new Set(items.map((item) => String(item?.color || '').trim()).filter((color) => color && color.length <= 12))].slice(0, 2)
  const occasion = String(recommendation?.occasion || '').trim()
  const title = [colors.join('与'), occasion ? `${occasion}搭配` : '衣橱搭配'].filter(Boolean).join(' · ')
  return title.length <= 40 ? title : '衣橱搭配方案'
}

// Keep saved reasons readable without changing the original recommendation record.
// The text model receives names/styles, so unspecified garment properties are not confirmed facts.
export function formatRecommendationReason(recommendation) {
  if (!recommendation) return ''
  const items = Array.isArray(recommendation.items) ? recommendation.items : []
  const knownDescription = items.map((item) => `${item?.name || ''} ${item?.style || ''}`).join(' ')
  const raw = String(recommendation.reason || '')
    .replace(/[（(]\s*(?:衣物\s*)?(?:ID|编号)\s*[:：#]?\s*\d+\s*[）)]/gi, '')
    .replace(/\bID\s*[:：#]?\s*\d+\b/gi, '')
  const sentences = raw.split(/[。！？!?；;\r\n]+/).map((text) => text.trim()).filter(Boolean)
  const anchors = items.flatMap((item) => [item?.name, item?.color]).filter(Boolean)
  const staleNotice = raw.includes('旧分析暂未用于')
    ? '形象资料已变更，这次推荐参考当前基础资料和偏好' : ''
  const selected = staleNotice ? [staleNotice] : []
  let length = staleNotice ? staleNotice.length + 1 : 0
  let recentItems = []
  for (const sentence of sentences) {
    if (sentence.includes('旧分析暂未用于')) continue
    let changed = false
    const clauses = sentence.split(/[，,]/).map((text) => text.trim()).filter(Boolean)
    const supported = clauses.filter((clause) => {
      const mentioned = items.filter((item) => item?.name && clause.includes(item.name))
      if (mentioned.length) recentItems = mentioned
      const description = recentItems.length
        ? recentItems.map((item) => `${item.name} ${item.style || ''}`).join(' ') : knownDescription
      const rejected = INTERNAL_OR_PROFILE_DUMP.test(clause)
        || GARMENT_ATTRIBUTES.some((attribute) => clause.includes(attribute) && !description.includes(attribute))
      if (rejected) changed = true
      return !rejected
    })
    // After removing a mixed legacy clause, start at a named item/color to keep a clear subject.
    const start = changed ? supported.findIndex((clause) => anchors.some((anchor) => clause.includes(anchor))) : 0
    if (start < 0 || !supported.length) continue
    const readable = supported.slice(start).join('，')
    if (selected.includes(readable) || length + readable.length + 1 > MAX_REASON_LENGTH) continue
    selected.push(readable)
    length += readable.length + 1
    if (selected.length === 3) break
  }
  if (selected.length) return selected.join('。') + '。'

  const styles = items.map((item) => String(item?.style || '').trim())
  const commonStyle = styles.length > 1 && styles[0] && styles.every((style) => style === styles[0])
  return commonStyle ? `所选单品的${styles[0].length <= 40 ? styles[0] : ''}风格保持一致，组合更协调。` : '暂无简短搭配说明。'
}
