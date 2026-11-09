import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { setImmediate } from 'node:timers/promises'
import test from 'node:test'
import vm from 'node:vm'
import { imageAddress, sitePermission } from '../src/utils/tryOnImages.js'

const extensionSource = name => readFileSync(new URL(`../../browser-extension/${name}.js`, import.meta.url), 'utf8')
  .replace(/^import[\s\S]*?from '[^']+'\r?\n/gm, '')

async function popupFixture({ connected = false, permissionGranted = true } = {}) {
  const tab = { id: 8, url: 'https://cn.bing.com/images/search?q=clothes' }
  const fields = new Map()
  const messages = [], scripts = [], injections = []
  let listener, closed = false
  const connection = connected ? { enabled: true, username: 'test-owner', origin: 'https://project.test', tabId: 1 } : undefined
  const chrome = {
    runtime: {
      id: 'test-extension',
      getURL: name => `chrome-extension://test-extension/${name}`,
      onMessage: { addListener: callback => { listener = callback } },
      sendMessage: message => new Promise(resolve => listener(message, {
        id: 'test-extension', url: 'chrome-extension://test-extension/popup.html'
      }, resolve))
    },
    storage: {
      session: { get: async key => key === null ? {} : { connection } },
      local: { get: async () => ({ projectOrigin: 'https://project.test' }) }
    },
    tabs: {
      query: async () => [tab],
      get: async () => tab,
      sendMessage: async (tabId, message) => { messages.push({ tabId, ...message }) },
      onRemoved: { addListener() {} }
    },
    permissions: { request: async () => permissionGranted },
    scripting: {
      getRegisteredContentScripts: async () => scripts,
      registerContentScripts: async entries => { scripts.push(...entries) },
      executeScript: async details => { injections.push(details) }
    }
  }
  vm.runInNewContext(extensionSource('background'), {
    chrome, URL, imageAddress, sitePermission,
    setTryOnWindowClosedHandler() {}, hasIntegratedWindow: async () => false
  })
  const document = { getElementById: id => {
    if (!fields.has(id)) fields.set(id, { hidden: id === 'error', textContent: '' })
    return fields.get(id)
  } }
  document.getElementById('error')
  vm.runInNewContext(extensionSource('popup'), {
    chrome, document, sitePermission, btoa, window: { close: () => { closed = true } }
  })
  await setImmediate()
  return {
    fields, messages, scripts, injections, get closed() { return closed },
    async enable() {
      fields.get('enable').onclick()
      // Both popup completion paths are observable: it closes, or displays an error.
      for (let tick = 0; tick < 20 && !closed && fields.get('error').hidden; tick++) await setImmediate()
    }
  }
}

test('enabling a shopping site after the project session is lost explains reconnection instead of silently closing', async () => {
  const popup = await popupFixture()
  assert.equal(popup.fields.get('connection').textContent, '尚未连接项目')
  await popup.enable()
  assert.equal(popup.closed, false, 'The popup must stay open when no floating panel can be shown')
  assert.equal(popup.fields.get('error').hidden, false)
  assert.match(popup.fields.get('error').textContent, /项目.*开启跨站试穿/)
  assert.equal(popup.messages.some(message => message.type === 'SHOW_PANEL'), false)
})

test('enabling a connected shopping site injects the content script and displays its floating panel', async () => {
  const popup = await popupFixture({ connected: true })
  await popup.enable()
  assert.equal(popup.closed, true)
  assert.equal(popup.fields.get('error').hidden, true)
  assert.equal(popup.injections[0].target.tabId, 8)
  assert.equal(popup.scripts[0].matches[0], 'https://cn.bing.com/*')
  assert.ok(popup.messages.some(message => message.tabId === 8 && message.type === 'SHOW_PANEL'))
})

test('declining shopping site access keeps the popup open with a useful error', async () => {
  const popup = await popupFixture({ connected: true, permissionGranted: false })
  await popup.enable()
  assert.equal(popup.closed, false)
  assert.match(popup.fields.get('error').textContent, /未授权本站/)
  assert.equal(popup.injections.length, 0)
})
