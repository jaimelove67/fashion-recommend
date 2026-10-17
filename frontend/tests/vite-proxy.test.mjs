import test from 'node:test'
import assert from 'node:assert/strict'
import { createServer as createHttpServer, request as requestHttp } from 'node:http'
import { fileURLToPath } from 'node:url'
import { createServer, loadConfigFromFile } from 'vite'
import { trendImageProxyPlugin } from '../dev/trendImageProxy.mjs'

test('the development proxy accepts a configured backend without changing same-origin security', async t => {
  const previousTarget = process.env.DEV_API_TARGET
  t.after(() => {
    if (previousTarget === undefined) delete process.env.DEV_API_TARGET
    else process.env.DEV_API_TARGET = previousTarget
  })
  const root = fileURLToPath(new URL('../', import.meta.url))
  for (const [target, expected] of [
    [' http://127.0.0.1:8088 ', 'http://127.0.0.1:8088'],
    ['', 'http://localhost:8080']
  ]) {
    process.env.DEV_API_TARGET = target
    const loaded = await loadConfigFromFile(
      { command: 'serve', mode: 'test' },
      fileURLToPath(new URL('../vite.config.js', import.meta.url)), root, 'silent'
    )
    for (const path of ['/api', '/actuator']) {
      assert.equal(loaded.config.server.proxy[path].target, expected)
      assert.equal(loaded.config.server.proxy[path].changeOrigin, false)
    }
  }
})

test('the development proxy preserves same-origin API requests and their security headers', async (t) => {
  const backend = createHttpServer(async (request, response) => {
    let body = ''
    for await (const chunk of request) body += chunk
    const origin = request.headers.origin
    const sameOrigin = !origin || origin === `http://${request.headers.host}`
    response.writeHead(sameOrigin ? (request.method === 'POST' ? 400 : 200) : 403, {
      'Content-Type': 'application/json'
    })
    response.end(JSON.stringify({
      host: request.headers.host,
      origin,
      cookie: request.headers.cookie,
      csrf: request.headers['x-xsrf-token'],
      body
    }))
  })
  await new Promise(resolve => backend.listen(0, '127.0.0.1', resolve))
  t.after(() => {
    backend.closeAllConnections()
    return new Promise(resolve => backend.close(resolve))
  })
  const backendUrl = `http://127.0.0.1:${backend.address().port}`
  const root = fileURLToPath(new URL('../', import.meta.url))
  const loaded = await loadConfigFromFile(
    { command: 'serve', mode: 'test' },
    fileURLToPath(new URL('../vite.config.js', import.meta.url)), root, 'silent'
  )
  // Substitute only the backend address, keeping each real proxy rule's options and shape.
  const proxy = Object.fromEntries(Object.entries(loaded.config.server.proxy).map(([path, options]) => [
    path, typeof options === 'string' ? backendUrl : { ...options, target: backendUrl }
  ]))
  const vite = await createServer({
    ...loaded.config, configFile: false, root, logLevel: 'silent',
    server: { ...loaded.config.server, host: '127.0.0.1', port: 0, hmr: false, watch: null, proxy },
    optimizeDeps: { noDiscovery: true, entries: [] }
  })
  t.after(() => vite.close())
  await vite.listen()
  const base = `http://127.0.0.1:${vite.httpServer.address().port}`
  const cookie = 'XSRF-TOKEN=proxy-test; JSESSIONID=proxy-test'
  const body = JSON.stringify({ username: '!', password: 'short' })
  const registration = await fetch(`${base}/api/v1/auth/register`, {
    method: 'POST',
    headers: { Origin: base, Cookie: cookie, 'X-XSRF-TOKEN': 'proxy-test', 'Content-Type': 'application/json' },
    body
  })
  assert.equal(registration.status, 400, 'same-origin registration must reach validation instead of CORS 403')
  assert.deepEqual(await registration.json(), {
    host: new URL(base).host, origin: base, cookie, csrf: 'proxy-test', body
  })
  const health = await fetch(`${base}/actuator/health`, { headers: { Origin: base } })
  assert.equal(health.status, 200)
  assert.equal((await health.json()).host, new URL(base).host)

  const crossOrigin = await fetch(`${base}/api/v1/auth/register`, {
    method: 'POST', headers: { Origin: 'https://untrusted.example', 'Content-Type': 'application/json' }, body
  })
  assert.equal(crossOrigin.status, 403, 'untrusted origins must still reach the backend unchanged')
  assert.equal((await crossOrigin.json()).origin, 'https://untrusted.example')
})

async function imageProxyFixture(t, respond) {
  const seen = []
  const upstream = createHttpServer((request, response) => {
    seen.push({ path: request.url, method: request.method, headers: request.headers })
    respond(request, response)
  })
  await new Promise(resolve => upstream.listen(0, '127.0.0.1', resolve))
  t.after(() => {
    upstream.closeAllConnections()
    return new Promise(resolve => upstream.close(resolve))
  })
  const upstreamBase = `http://127.0.0.1:${upstream.address().port}`
  const targets = []
  const transport = (target, options, callback) => {
    targets.push({ url: target.href, tlsVerified: options.rejectUnauthorized })
    return requestHttp(new URL(target.pathname, upstreamBase), options, callback)
  }
  const root = fileURLToPath(new URL('../', import.meta.url))
  const loaded = await loadConfigFromFile(
    { command: 'serve', mode: 'test' }, fileURLToPath(new URL('../vite.config.js', import.meta.url)), root, 'silent'
  )
  assert.ok(loaded.config.plugins.some(plugin => plugin?.name === 'fixed-trend-image-proxy'), 'the real Vite configuration must install the fixed image proxy')
  const plugins = loaded.config.plugins.map(plugin => plugin?.name === 'fixed-trend-image-proxy' ? trendImageProxyPlugin(transport) : plugin)
  const vite = await createServer({
    ...loaded.config, plugins, configFile: false, root, logLevel: 'silent',
    server: { ...loaded.config.server, host: '127.0.0.1', port: 0, hmr: false, watch: null },
    optimizeDeps: { noDiscovery: true, entries: [] }
  })
  t.after(() => vite.close())
  await vite.listen()
  return { base: `http://127.0.0.1:${vite.httpServer.address().port}`, targets, seen }
}

function rawGet(base, path) {
  const url = new URL(base)
  return new Promise((resolve, reject) => {
    const request = requestHttp({ hostname: url.hostname, port: url.port, path }, response => {
      response.resume()
      response.on('end', () => resolve(response.statusCode))
    })
    request.on('error', reject)
    request.end()
  })
}

test('the Vite image proxy reaches only fixed CDN paths, returns image bytes and strips browser credentials and upstream cookies', async t => {
  const png = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jxYQAAAAASUVORK5CYII=', 'base64')
  const { base, targets, seen } = await imageProxyFixture(t, (request, response) => {
    response.writeHead(200, {
      'Content-Type': 'image/png', 'Content-Length': png.length, 'Cache-Control': 'public, max-age=60',
      'Set-Cookie': 'CDN-TRACKING=private', 'X-Upstream-Private': 'hidden', 'Access-Control-Allow-Credentials': 'true'
    })
    response.end(png)
  })
  const image = await fetch(`${base}/trend-images/wx2/large/AbC123.png`, { headers: {
    Cookie: 'JSESSIONID=private', Authorization: 'Bearer private', Referer: `${base}/#profile`, Origin: base,
    'X-XSRF-TOKEN': 'private', 'X-Api-Key': 'private', 'X-Forwarded-For': '192.0.2.1',
    'X-Original-URL': '/api/v1/me/style-profile', 'Sec-Fetch-Dest': 'image', 'Accept-Language': 'private-language'
  } })
  assert.equal(image.status, 200)
  assert.deepEqual(Buffer.from(await image.arrayBuffer()), png)
  assert.equal(image.headers.get('content-type'), 'image/png')
  assert.equal(image.headers.get('cache-control'), 'public, max-age=60')
  assert.equal(image.headers.get('x-content-type-options'), 'nosniff')
  for (const name of ['set-cookie', 'x-upstream-private', 'access-control-allow-credentials']) assert.equal(image.headers.get(name), null)
  assert.deepEqual(targets, [{ url: 'https://wx2.sinaimg.cn/large/AbC123.png', tlsVerified: true }])
  assert.equal(seen[0].path, '/large/AbC123.png')
  assert.deepEqual(Object.keys(seen[0].headers).sort(), ['accept', 'connection', 'host', 'user-agent'])
  assert.equal(seen[0].headers.host, 'wx2.sinaimg.cn')
  assert.equal(seen[0].headers['user-agent'], 'fashion-trend-images/1.0')
  assert.equal(seen[0].headers.accept, 'image/*')
  for (const [index, size] of ['cmw960', 'large', 'mw690', 'thumbnail', 'orj360', 'thumb150'].entries()) {
    const host = `wx${index % 4 + 1}`
    const extension = ['jpg', 'jpeg', 'png', 'webp'][index % 4]
    const head = await fetch(`${base}/trend-images/${host}/${size}/A1.${extension}`, { method: 'HEAD' })
    assert.equal(head.status, 200)
    assert.equal((await head.arrayBuffer()).byteLength, 0)
    assert.equal(targets.at(-1).url, `https://${host}.sinaimg.cn/${size}/A1.${extension}`)
    assert.equal(seen.at(-1).method, 'HEAD')
  }
})

test('invalid Vite image proxy paths and methods fail locally without an upstream request or SPA fallback', async t => {
  const { base, targets, seen } = await imageProxyFixture(t, (request, response) => { response.end('must not be reached') })
  for (const path of [
    '/trend-images', '/trend-images/', '/trend-images/wx5/large/A1.jpg', '/trend-images/wx1.sinaimg.cn/large/A1.jpg',
    '/trend-images/https://example.com/A1.jpg', '/trend-images/wx1/original/A1.jpg', '/trend-images/wx1/large/A-1.jpg',
    '/trend-images/wx1/large/A1.svg', '/trend-images/wx1/large/A1.JPG', '/trend-images/wx1/large/A1.jpg?private=1',
    '/trend-images/wx1/large/A1.jpg#fragment', '/trend-images/wx1/large/%41.jpg', '/trend-images/wx1/large/../A1.jpg',
    '/trend-images/wx1/large/A1.jpg/extra', '/trend-images/wx1/large/A1.jpg%3fprivate=1', '/trend-images/wx1@evil.test/large/A1.jpg'
  ]) {
    const status = await rawGet(base, path)
    // Node rejects a literal fragment in an HTTP request target before Vite runs.
    assert.ok(status === 400 || status === 404, `${path}: expected a local rejection, got ${status}`)
  }
  const post = await fetch(`${base}/trend-images/wx1/large/A1.jpg`, { method: 'POST', body: 'private' })
  assert.equal(post.status, 405)
  assert.equal(post.headers.get('allow'), 'GET, HEAD')
  assert.deepEqual(targets, [])
  assert.deepEqual(seen, [])
})

test('the Vite image proxy does not forward redirects, active content or upstream failures', async t => {
  const { base, targets } = await imageProxyFixture(t, (request, response) => {
    if (request.url.endsWith('/Redirect.jpg')) {
      response.writeHead(302, { Location: 'https://arbitrary.example/private', 'Set-Cookie': 'private=1' })
      return response.end()
    }
    if (request.url.endsWith('/Broken.jpg')) return request.socket.destroy()
    response.writeHead(200, { 'Content-Type': 'image/svg+xml', 'Set-Cookie': 'private=1' })
    response.end('<svg xmlns="http://www.w3.org/2000/svg"><script>alert(1)</script></svg>')
  })
  for (const file of ['Redirect.jpg', 'Active.jpg', 'Broken.jpg']) {
    const image = await fetch(`${base}/trend-images/wx1/large/${file}`)
    assert.equal(image.status, 502)
    assert.equal(image.headers.get('location'), null)
    assert.equal(image.headers.get('set-cookie'), null)
    assert.equal(image.headers.get('x-content-type-options'), 'nosniff')
    assert.equal(await image.text(), 'Trend image unavailable')
  }
  assert.equal(targets.length, 3, 'redirects must never trigger another outgoing request')
})
