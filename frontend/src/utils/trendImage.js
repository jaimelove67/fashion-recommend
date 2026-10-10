export function trendImageCandidates(imageUrl) {
  if (!imageUrl) return []
  if (typeof imageUrl === 'string' && imageUrl.startsWith('/assets/')) return [imageUrl]

  try {
    const original = new URL(imageUrl)
    if (!/(^|\.)sinaimg\.cn$/i.test(original.hostname) || !/\/thumbnail\//i.test(original.pathname)) {
      return [imageUrl]
    }

    const large = new URL(original)
    large.pathname = large.pathname.replace(/\/thumbnail\//i, '/large/')
    return [...new Set([large.href, imageUrl])]
  } catch {
    return [imageUrl]
  }
}

// Serve public Sina images through the app; never accept an arbitrary proxy target.
export function trendDisplayImageUrl(imageUrl) {
  try {
    const url = new URL(imageUrl)
    if (url.protocol !== 'https:' || url.username || url.password || url.port || url.search || url.hash) return imageUrl
    const host = /^(wx[1-4])\.sinaimg\.cn$/i.exec(url.hostname)
    if (!host || !/^\/(?:cmw960|large|mw690|thumbnail)\/[A-Za-z0-9]+\.(?:jpg|jpeg|png|webp)$/.test(url.pathname)) return imageUrl
    return `/trend-images/${host[1].toLowerCase()}${url.pathname}`
  } catch { return imageUrl }
}
