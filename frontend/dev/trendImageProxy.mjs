import { request as requestHttps } from 'node:https'

const IMAGE_PATH = /^\/trend-images\/(wx[1-4])\/(cmw960|large|mw690|thumbnail|orj360|thumb150)\/([A-Za-z0-9]+\.(?:jpg|jpeg|png|webp))$/
const RESPONSE_HEADERS = ['content-type', 'content-length', 'cache-control', 'etag', 'last-modified', 'expires']
const IMAGE_TYPES = new Set(['image/jpeg', 'image/jpg', 'image/png', 'image/webp'])

// The injectable transport is only a local test seam; request paths can never choose an origin.
export function createTrendImageMiddleware(requestUpstream = requestHttps) {
  return function trendImageMiddleware(request, response, next) {
    if (!request.url?.startsWith('/trend-images')) return next()
    const fail = status => {
      if (response.destroyed || response.writableEnded) return
      if (response.headersSent) return response.destroy()
      response.writeHead(status, { 'Content-Type': 'text/plain; charset=utf-8', 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff' })
      response.end('Trend image unavailable')
    }
    const match = IMAGE_PATH.exec(request.url)
    if (!match) return fail(404)
    if (!['GET', 'HEAD'].includes(request.method)) {
      response.setHeader('Allow', 'GET, HEAD')
      return fail(405)
    }
    const target = new URL(`https://${match[1]}.sinaimg.cn/${match[2]}/${match[3]}`)
    let upstream
    try {
      upstream = requestUpstream(target, {
        method: request.method,
        rejectUnauthorized: true,
        // Build a fresh allowlist: never copy cookies, credentials, referrers or user headers.
        headers: { Host: target.hostname, 'User-Agent': 'fashion-trend-images/1.0', Accept: 'image/*' }
      }, incoming => {
        const status = incoming.statusCode || 502
        // Do not expose CDN redirects as a route to another upstream or browser destination.
        const contentType = String(incoming.headers['content-type'] || '').split(';')[0].trim().toLowerCase()
        if (status !== 200 || !IMAGE_TYPES.has(contentType)) {
          incoming.resume()
          return fail(status >= 400 && status <= 599 ? status : 502)
        }
        const headers = { 'X-Content-Type-Options': 'nosniff' }
        for (const name of RESPONSE_HEADERS) {
          if (incoming.headers[name] !== undefined) headers[name] = incoming.headers[name]
        }
        response.writeHead(200, headers)
        incoming.on('error', () => fail(502))
        incoming.pipe(response)
      })
      upstream.on('error', () => fail(502))
      upstream.setTimeout(15_000, () => upstream.destroy(new Error('Trend image timed out')))
      request.once('aborted', () => upstream.destroy())
      response.once('close', () => { if (!response.writableEnded) upstream.destroy() })
      upstream.end()
    } catch {
      upstream?.destroy()
      fail(502)
    }
  }
}

export function trendImageProxyPlugin(requestUpstream) {
  return {
    name: 'fixed-trend-image-proxy',
    configureServer(server) {
      server.middlewares.use(createTrendImageMiddleware(requestUpstream))
    }
  }
}
