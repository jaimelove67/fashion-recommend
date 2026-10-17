import { ANYWEAR_ORIGIN, anywearFrameUrl } from './liveTryOn.js'
import { imageAddress, imageFile } from './images.js'
import { installTryOnHost, readTryOnHostImage, stopTryOnHost } from './tryOnHost.js'

const PREFIX = 'tryon-window:'
const requests = new Map(), locks = new Map()
let onWindowClosed = null
export function setTryOnWindowClosedHandler(handler) { onWindowClosed = handler }
const keyFor = sender => PREFIX + sender.tab.id + ':' + sender.frameId
const sessions = async () => Object.entries(await chrome.storage.session.get(null)).filter(([key]) => key.startsWith(PREFIX))
const notify = (session, data) => chrome.runtime.sendMessage({ type: 'TRY_ON_WINDOW_EVENT', tryonId: session.tryonId, ...data }).catch(() => {})

async function serial(key, operation) {
  const task = (locks.get(key) || Promise.resolve()).catch(() => {}).then(operation)
  locks.set(key, task)
  try { return await task } finally { if (locks.get(key) === task) locks.delete(key) }
}
async function store(key, session) {
  await serial(key, async () => {
    if (requests.get(key) !== session.tryonId) throw new Error('本次试穿已取消。')
    await chrome.storage.session.set({ [key]: session })
  })
}

function loaded(tabId) {
  return new Promise((resolve, reject) => {
    const finish = error => {
      clearTimeout(timer); chrome.tabs.onUpdated.removeListener(updated); chrome.tabs.onRemoved.removeListener(removed)
      if (error) reject(error); else resolve()
    }
    const updated = (id, change) => { if (id === tabId && change.status === 'complete') finish() }
    const removed = id => { if (id === tabId) finish(new Error('试穿窗口已关闭，请重新开始。')) }
    const timer = setTimeout(() => finish(new Error('试衣服务加载超时，请检查网络后重试。')), 20000)
    chrome.tabs.onUpdated.addListener(updated); chrome.tabs.onRemoved.addListener(removed)
    void chrome.tabs.get(tabId).then(tab => { if (tab.status === 'complete') finish() }, () => removed(tabId))
  })
}

async function controlSession(sender) {
  const token = new URL(sender.url || 'about:blank').searchParams.get('controls')
  if (!token) return null
  const entry = (await sessions()).find(([, session]) => session.controlToken === token)
  if (!entry) throw new Error('试穿窗口已失效，请重新开启。')
  return entry
}

async function updateSession(key, session, changes) {
  return serial(key, async () => {
    const current = (await chrome.storage.session.get(key))[key]
    if (!current || current.tryonId !== session.tryonId) return false
    await chrome.storage.session.set({ [key]: { ...current, ...changes } })
    return true
  })
}

export async function hasIntegratedWindow() { return (await sessions()).some(([, session]) => session.adopted) }
export async function focusIntegratedWindow() {
  const entry = (await sessions()).find(([, session]) => session.adopted)
  if (entry) await chrome.windows.update(entry[1].windowId, { focused: true })
}

export async function integratedState(sender) {
  const entry = await controlSession(sender)
  if (!entry) return null
  const [, session] = entry
  const image = (await chrome.scripting.executeScript({ target: { tabId: session.providerTabId }, func: readTryOnHostImage }))[0]?.result || ''
  return { item: session.item, image, tryonId: session.tryonId, stage: session.stage, hasRendered: session.hasRendered }
}

export async function adoptIntegratedWindow(sender) {
  const entry = await controlSession(sender)
  if (!entry) throw new Error('试穿操作面板无效。')
  await updateSession(entry[0], entry[1], { adopted: true })
}

export async function selectIntegratedItem(sender, item) {
  const entry = await controlSession(sender)
  if (!entry) throw new Error('请在试穿窗口中选择图片。')
  await serial(entry[0], async () => {
    const current = (await chrome.storage.session.get(entry[0]))[entry[0]]
    if (!current || current.controlToken !== entry[1].controlToken) throw new Error('试穿窗口已失效，请重新开启。')
    await chrome.storage.session.set({ [entry[0]]: { ...current, item } })
  })
  await chrome.scripting.executeScript({ target: { tabId: entry[1].providerTabId }, func: stopTryOnHost, args: [true] })
}

export async function integratedItem(sender) {
  return (await controlSession(sender))?.[1].item || null
}

async function remove(key, session, reason = '') {
  if (requests.get(key) === session.tryonId) requests.delete(key)
  await serial(key, async () => {
    if ((await chrome.storage.session.get(key))[key]?.tryonId === session.tryonId) await chrome.storage.session.remove(key)
  })
  if (session.windowId) await chrome.windows.remove(session.windowId).catch(() => {})
  if (reason) await notify(session, { event: reason })
  if (session.adopted) await onWindowClosed?.()
}

export async function closeTryOnWindow(sender, tryonId) {
  const controls = await controlSession(sender)
  if (controls) {
    const [key, session] = controls
    if (tryonId && session.tryonId !== tryonId) return
    if (requests.get(key) === session.tryonId) requests.delete(key)
    if (await updateSession(key, session, { tryonId: '', stage: 'idle', hasRendered: false })) {
      await chrome.scripting.executeScript({ target: { tabId: session.providerTabId }, func: stopTryOnHost, args: [false, session.tryonId] }).catch(() => {})
    }
    return
  }
  const key = keyFor(sender)
  if (!tryonId || requests.get(key) === tryonId) requests.delete(key)
  const session = (await chrome.storage.session.get(key))[key]
  if (session && !session.adopted && (!tryonId || session.tryonId === tryonId)) await remove(key, session)
}

export async function closeAllTryOnWindows() {
  requests.clear()
  for (const [key, session] of await sessions()) await remove(key, session, 'closed')
}

export async function openTryOnWindow(message, sender) {
  const controls = await controlSession(sender)
  if (!controls && (!sender.tab || !Number.isInteger(sender.frameId))) throw new Error('请在试穿浮窗中开始。')
  if (!/^tryon_[a-zA-Z0-9-]{1,80}$/.test(message.tryonId || '')) throw new Error('试穿请求无效，请重新开始。')
  imageFile(message.image)
  const item = { name: String(message.item?.name || '购物单品').slice(0, 100), category: String(message.category || '上装').slice(0, 20),
    imageUrl: message.item?.imageUrl ? imageAddress(message.item.imageUrl) : '',
    sourceUrl: message.item?.sourceUrl ? imageAddress(message.item.sourceUrl) : '', sourceKind: 'SHOPPING' }
  if (!controls) await closeAllTryOnWindows()
  const key = controls?.[0] || keyFor(sender)
  requests.set(key, message.tryonId)
  const session = { ...(controls?.[1] || {}), tryonId: message.tryonId, ownerTabId: controls?.[1].ownerTabId || sender.tab.id,
    windowId: controls?.[1].windowId || null, providerTabId: controls?.[1].providerTabId || null,
    hostsite: controls?.[1].hostsite || new URL(sender.tab.url).hostname,
    controlToken: controls?.[1].controlToken || crypto.randomUUID(), item, stage: 'connecting', hasRendered: false }
  let popup
  try {
    if (!await chrome.permissions.contains({ origins: [ANYWEAR_ORIGIN + '/*'] })) throw new Error('请允许访问 Anywear 试衣服务后重试。')
    await store(key, session)
    // No product image, account identifier or project credential goes in the URL.
    const providerUrl = anywearFrameUrl(session.hostsite)
    if (!controls) {
      popup = await chrome.windows.create({ type: 'popup', width: 900, height: 760, url: 'about:blank', focused: true })
      session.windowId = popup.id; session.providerTabId = popup.tabs?.[0]?.id
    }
    if (!session.providerTabId) throw new Error('无法打开试穿窗口，请重试。')
    if ((await chrome.storage.session.get(key))[key]?.tryonId !== session.tryonId) throw new Error('本次试穿已取消。')
    await store(key, session)
    if (!controls) { await chrome.tabs.update(session.providerTabId, { url: providerUrl }); await loaded(session.providerTabId) }
    if ((await chrome.storage.session.get(key))[key]?.tryonId !== session.tryonId) throw new Error('本次试穿已取消。')
    const tab = await chrome.tabs.get(session.providerTabId)
    const url = new URL(tab.url)
    if (url.origin !== ANYWEAR_ORIGIN || url.pathname !== '/widget/latest/widget.html') throw new Error('试衣服务页面不可用，请稍后重试。')
    session.ready = true
    await store(key, session)
    await chrome.scripting.executeScript({ target: { tabId: session.providerTabId }, func: installTryOnHost,
      args: [{ productImageUrl: message.image, tryonId: session.tryonId, imageSource: 'zhiji_shopping_selection',
        suggestedFacingMode: 'user', garmentCategoryRaw: item.category }, ANYWEAR_ORIGIN, providerUrl,
        chrome.runtime.getURL('floating.html') + '?controls=' + session.controlToken] })
    return { opened: true }
  } catch (error) {
    if (controls) {
      if (await updateSession(key, session, { tryonId: '', stage: 'idle', hasRendered: false })) {
        await chrome.scripting.executeScript({ target: { tabId: session.providerTabId }, func: stopTryOnHost, args: [false, session.tryonId] }).catch(() => {})
      }
    } else await remove(key, session)
    throw error
  }
}

export async function forwardProviderEvent(message, sender) {
  if (sender.id !== chrome.runtime.id || sender.frameId !== 0 || !sender.tab
    || new URL(sender.url || 'about:blank').origin !== ANYWEAR_ORIGIN) throw new Error('试穿消息来源无效。')
  const entry = (await sessions()).find(([, session]) => session.providerTabId === sender.tab.id && session.tryonId === message.tryonId)
  if (!entry) return
  const [key, session] = entry, data = message.data
  if (!data || typeof data !== 'object') return
  if (data.type === 'DECART_TRACK_EVENT' && data.event === 'first_frame_rendered' && data.properties?.tryon_id !== session.tryonId) return
  const stages = { first_frame_rendered: 'streaming', queue_entered: 'queued', queue_completed: 'connecting', session_ended: 'ended' }
  if (data.type === 'DECART_TRACK_EVENT' && stages[data.event]) {
    const changes = { stage: stages[data.event] }
    if (data.event === 'first_frame_rendered' && data.properties?.tryon_id === session.tryonId) changes.hasRendered = true
    if (data.event === 'session_ended') changes.tryonId = ''
    await updateSession(key, session, changes)
  }
  await notify(session, { data })
  if (data.type === 'DECART_TRACK_EVENT' && data.event === 'session_ended') {
    await chrome.scripting.executeScript({ target: { tabId: session.providerTabId }, func: stopTryOnHost, args: [false, session.tryonId] }).catch(() => {})
  }
}

chrome.tabs.onRemoved.addListener(async tabId => {
  const entries = await sessions()
  for (const key of requests.keys()) if (key.startsWith(PREFIX + tabId + ':') && !entries.find(([entryKey]) => entryKey === key)?.[1].adopted) requests.delete(key)
  for (const [key, session] of entries) {
    if ((!session.adopted && session.ownerTabId === tabId) || session.providerTabId === tabId) await remove(key, session, 'closed')
  }
})
chrome.tabs.onUpdated.addListener(async (tabId, change) => {
  if (!change.url && change.status !== 'loading') return
  const entries = await sessions()
  for (const key of requests.keys()) if (key.startsWith(PREFIX + tabId + ':') && !entries.find(([entryKey]) => entryKey === key)?.[1].adopted) requests.delete(key)
  for (const [key, session] of entries) {
    if ((!session.adopted && session.ownerTabId === tabId) || (session.providerTabId === tabId && session.ready)) {
      await remove(key, session, 'closed')
    }
  }
})
