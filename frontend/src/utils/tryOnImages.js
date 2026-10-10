export const MAX_IMAGE_BYTES = 10 * 1024 * 1024
export const IMAGE_TYPES = new Set(['image/jpeg', 'image/png', 'image/webp'])

export function imageAddress(value) {
  const url = new URL(value)
  const local = ['localhost', '127.0.0.1'].includes(url.hostname)
  if (url.username || url.password || (url.protocol !== 'https:' && !(local && url.protocol === 'http:'))) {
    throw new Error('请选择 HTTPS 商品图片；本机测试图片可以使用 localhost 地址。')
  }
  return url.href
}

export function sitePermission(value) {
  const url = new URL(imageAddress(value))
  return `${url.protocol}//${url.hostname}/*`
}

export function imageFile(dataUrl) {
  const match = /^data:(image\/(?:png|jpeg|webp));base64,([A-Za-z0-9+/]+={0,2})$/.exec(dataUrl || '')
  if (!match || match[2].length > Math.ceil(MAX_IMAGE_BYTES / 3) * 4) throw new Error('请选择不超过 10 MB 的 JPG、PNG 或 WebP 图片。')
  const binary = atob(match[2])
  if (!binary.length || binary.length > MAX_IMAGE_BYTES) throw new Error('单品图片大小不受支持。')
  return new Blob([Uint8Array.from(binary, char => char.charCodeAt(0))], { type: match[1] })
}

export async function readImage(value) {
  const url = imageAddress(value)
  const response = await fetch(url, { credentials: 'omit', redirect: 'error', signal: AbortSignal.timeout(15000) })
  if (!response.ok) throw new Error('商品图片无法读取。请下载清晰单品图后，在浮窗中选择图片。')
  const type = response.headers.get('content-type')?.split(';')[0].trim().toLowerCase()
  if (!IMAGE_TYPES.has(type)) throw new Error('商品图片需为 JPG、PNG 或 WebP。请下载后转换格式再选择。')
  if (Number(response.headers.get('content-length')) > MAX_IMAGE_BYTES) throw new Error('商品图片不能超过 10 MB。')
  const reader = response.body.getReader()
  const chunks = []
  let size = 0
  while (true) {
    const { value: chunk, done } = await reader.read()
    if (done) break
    size += chunk.length
    if (size > MAX_IMAGE_BYTES) { await reader.cancel(); throw new Error('商品图片不能超过 10 MB。') }
    chunks.push(chunk)
  }
  if (!size) throw new Error('商品图片为空，请换一张图片。')
  let binary = ''
  for (const chunk of chunks) {
    for (let index = 0; index < chunk.length; index += 8192) binary += String.fromCharCode(...chunk.subarray(index, index + 8192))
  }
  return `data:${type};base64,${btoa(binary)}`
}
