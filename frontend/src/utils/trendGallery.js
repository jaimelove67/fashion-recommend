const EXCLUDED_CONTENT = /(红毯|时装周|秀场|走秀|杂志|画报|时尚大片|封面拍摄|red[ -]?carpet|fashion[ -]?week|runway|catwalk|magazine|fashion editorial)/i

function isConfiguredDemoAsset(value) {
  if (typeof value !== 'string' || !value.startsWith('/assets/')) return false
  try {
    const url = new URL(value, 'http://configured-demo.local')
    return url.origin === 'http://configured-demo.local'
      && url.pathname.startsWith('/assets/')
      && !url.pathname.split('/').includes('..')
      && !url.pathname.includes('\\')
  } catch {
    return false
  }
}

export function verifiedFullBodyImageUrl(item) {
  if (item?.platform === 'configured-demo') {
    return [item?.evidence?.fullBodyImageUrl, item?.imageUrl]
      .find((url) => isConfiguredDemoAsset(url)) || ''
  }
  if (item?.platform === 'editorial') return ''
  if (item?.evidence?.mediaType && !['image', 'post', 'photo'].includes(item.evidence.mediaType)) return ''
  const url = item?.evidence?.fullBodyImageUrl
  if (typeof url !== 'string' || !/^https?:\/\//i.test(url)) return ''
  if (url !== item.imageUrl && !item?.evidence?.images?.includes(url)) return ''
  if (EXCLUDED_CONTENT.test(`${item.title || ''} ${item.summary || ''} ${(item.topicTags || []).join(' ')}`)) return ''
  return url
}
