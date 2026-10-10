export const ANYWEAR_ORIGIN = 'https://anywear.decart.ai'
export const ANYWEAR_PRIVACY_URL = 'https://docs.platform.decart.ai/resources/privacy-policy'
const MAX_IMAGE_BYTES = 10 * 1024 * 1024
const IMAGE_TYPES = new Set(['image/jpeg', 'image/png', 'image/webp'])
const GARMENT_CATEGORIES = new Set(['上装', '下装', '外套', '连体装', '连衣裙'])

export function trendTryOnItem(piece) {
  return {
    id: piece?.id,
    name: piece?.name,
    category: piece?.category === '裤装' ? '下装' : piece?.category,
    imageUrl: piece?.image,
    imageSource: 'trend_piece_illustration',
    sourceKind: 'TREND_ILLUSTRATION',
    sourceUrl: piece?.mentions?.find(item => item.sourceUrl)?.sourceUrl || '',
    imageNote: '趋势单品示意图 · 非原帖商品图'
  }
}

export function canTryOnGarment(item) {
  return Boolean(item?.id && item.imageUrl)
    && item.recognitionStatus !== 'NEEDS_MANUAL_REVIEW'
    && GARMENT_CATEGORIES.has(item.category)
}

export function anywearFrameUrl(hostname) {
  const url = new URL('/widget/latest/widget.html', ANYWEAR_ORIGIN)
  url.searchParams.set('norestore', '1')
  url.searchParams.set('autostart', '1')
  url.searchParams.set('hostsite', hostname)
  return url.href
}

// Read same-origin image bytes locally. Never send this app's session cookie or
// authenticated image URL to the isolated provider frame.
export async function prepareTryOnImage(imageUrl, { origin, signal }) {
  let url
  try { url = new URL(imageUrl, origin) }
  catch { throw new Error('这张单品图片的地址不可用，请选择其他单品。') }
  if (url.username || url.password || !['http:', 'https:'].includes(url.protocol)) {
    throw new Error('这张单品图片的地址不受支持，请选择其他单品。')
  }
  if (url.origin !== origin) {
    if (url.protocol !== 'https:') throw new Error('外部单品图片需要使用 HTTPS 链接，请选择其他单品。')
    return url.href
  }

  const response = await fetch(url.href, { credentials: 'same-origin', redirect: 'error', signal })
  if (!response.ok) throw new Error('无法读取衣物图片，请确认登录状态及图片是否仍然可用。')
  const type = response.headers.get('content-type')?.split(';')[0].trim().toLowerCase()
  if (!IMAGE_TYPES.has(type)) throw new Error('试衣需要 JPG、PNG 或 WebP 格式的衣物图片。')
  const blob = await response.blob()
  if (!blob.size || blob.size > MAX_IMAGE_BYTES) throw new Error('单品图片需要大于 0 字节且不超过 10 MB，请选择其他单品。')
  signal?.throwIfAborted()

  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    const abort = () => reader.abort()
    const cleanup = () => signal?.removeEventListener('abort', abort)
    reader.onload = () => { cleanup(); resolve(reader.result) }
    reader.onerror = () => { cleanup(); reject(new Error('单品图片读取失败，请选择其他单品后重试。')) }
    reader.onabort = () => { cleanup(); reject(new DOMException('Aborted', 'AbortError')) }
    signal?.addEventListener('abort', abort, { once: true })
    reader.readAsDataURL(blob)
  })
}

export function isAnywearMessage(event, frameWindow) {
  return Boolean(frameWindow) && event.source === frameWindow
    && event.origin === ANYWEAR_ORIGIN && event.data !== null && typeof event.data === 'object'
}
