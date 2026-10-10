import { imageAddress, imageFile, readImage, sitePermission } from './images.js'

const localPage = sender => sender.id === chrome.runtime.id
  && sender.url?.split('?')[0] === chrome.runtime.getURL('popup.html')
const floatingPage = sender => sender.id === chrome.runtime.id
  && sender.url?.split('?')[0] === chrome.runtime.getURL('floating.html')
const contentPage = sender => sender.id === chrome.runtime.id && sender.tab
  && sender.frameId === 0 && /^https?:/.test(sender.url || '')

async function state() { return (await chrome.storage.session.get('connection')).connection }
async function broadcast(type) {
  const tabs = await chrome.tabs.query({})
  await Promise.allSettled(tabs.map(tab => chrome.tabs.sendMessage(tab.id, { type })))
}

async function handle(message, sender) {
  if (message.type === 'IS_ENABLED' && contentPage(sender)) return { enabled: Boolean((await state())?.enabled) }
  if (message.type === 'BIND_PROJECT' && localPage(sender)) {
    const tab = await chrome.tabs.get(message.tabId)
    const origin = new URL(imageAddress(tab.url)).origin
    if (!await chrome.permissions.contains({ origins: [sitePermission(tab.url)] })) throw new Error('请先批准项目网站的访问权限。')
    const previous = await state()
    if (previous && previous.origin !== origin) {
      await chrome.storage.session.clear()
      await broadcast('HIDE_PANEL')
    }
    await chrome.storage.local.set({ projectOrigin: origin })
    return { origin }
  }
  if (message.type === 'CONNECT' && contentPage(sender)) {
    const origin = new URL(sender.url).origin
    const { projectOrigin } = await chrome.storage.local.get('projectOrigin')
    if (origin !== projectOrigin) throw new Error('请在项目网页打开扩展，点击“连接此项目网站”，再开启试穿。')
    if (origin !== message.origin || !message.username || message.username.length > 100) throw new Error('项目连接无效。')
    const previous = await state()
    if (previous && (previous.username !== message.username || previous.origin !== origin || previous.tabId !== sender.tab.id)) {
      await chrome.storage.session.clear()
      await broadcast('HIDE_PANEL')
    }
    await chrome.storage.session.set({ connection: { tabId: sender.tab.id, origin, username: message.username, enabled: true } })
    await broadcast('SHOW_PANEL')
    return { username: message.username }
  }
  if (message.type === 'SELECT_GARMENT' && contentPage(sender)) {
    if (!(await state())?.enabled) throw new Error('请先在项目趋势页开启试穿。')
    const imageUrl = imageAddress(message.item?.imageUrl)
    const source = new URL(message.item.sourceUrl || sender.url)
    if (!['http:', 'https:'].includes(source.protocol) || source.username || source.password || source.href.length > 2048) throw new Error('商品来源链接不可用。')
    const item = { imageUrl, sourceUrl: source.href, name: String(message.item.name || '购物单品').slice(0, 100), sourceKind: 'SHOPPING' }
    await chrome.storage.session.set({ ['selection:' + sender.tab.id]: item })
    await chrome.tabs.sendMessage(sender.tab.id, { type: 'GARMENT_SELECTED', item })
    return { ok: true }
  }
  if (message.type === 'GET_STATE' && (floatingPage(sender) || localPage(sender))) {
    const connection = await state()
    const item = sender.tab ? (await chrome.storage.session.get('selection:' + sender.tab.id))['selection:' + sender.tab.id] : null
    return { connected: Boolean(connection?.enabled), username: connection?.username || '', item }
  }
  if (message.type === 'READ_IMAGE' && floatingPage(sender)) {
    const item = (await chrome.storage.session.get('selection:' + sender.tab?.id))['selection:' + sender.tab?.id]
    if (!(await state())?.enabled || !item || imageAddress(message.url) !== item.imageUrl) throw new Error('请重新拖入选中的商品图片。')
    if (!await chrome.permissions.contains({ origins: [sitePermission(item.imageUrl)] })) throw new Error('请点击开始试穿，授权读取这张商品图片。')
    return { image: await readImage(item.imageUrl) }
  }
  if (message.type === 'SAVE_FAVORITE' && floatingPage(sender)) {
    const connection = await state()
    if (!connection?.enabled) throw new Error('请在项目趋势页重新连接试穿。')
    imageFile(message.item?.imageData)
    const tab = await chrome.tabs.get(connection.tabId).catch(() => null)
    if (!tab?.url || new URL(tab.url).origin !== connection.origin) throw new Error('项目标签页已关闭或离开，请重新连接后收藏。')
    return chrome.tabs.sendMessage(connection.tabId, { type: 'SAVE_TO_PROJECT', expectedUser: connection.username,
      origin: connection.origin, item: message.item }, { frameId: 0 })
  }
  if (message.type === 'STOP_ALL' && (floatingPage(sender) || localPage(sender) || contentPage(sender))) {
    await chrome.storage.session.clear()
    await broadcast('HIDE_PANEL')
    return { ok: true }
  }
  if (message.type === 'DISCONNECT_PROJECT' && contentPage(sender)) {
    if ((await state())?.tabId === sender.tab.id) {
      await chrome.storage.session.clear()
      await broadcast('HIDE_PANEL')
    }
    return { ok: true }
  }
  if (message.type === 'OPEN_PROJECT' && (floatingPage(sender) || localPage(sender))) {
    const connection = await state()
    const { projectOrigin = 'http://localhost:8090' } = await chrome.storage.local.get('projectOrigin')
    const hash = message.favorites ? '#favorites' : '#trend'
    if (connection) await chrome.tabs.update(connection.tabId, { active: true, url: connection.origin + '/' + hash }).catch(() => chrome.tabs.create({ url: projectOrigin + '/' + hash }))
    else await chrome.tabs.create({ url: projectOrigin + '/' + hash })
    return { ok: true }
  }
  if (message.type === 'SHOW_CURRENT' && localPage(sender)) {
    if (!(await state())?.enabled) return { enabled: false }
    const [tab] = await chrome.tabs.query({ active: true, currentWindow: true })
    if (tab) await chrome.tabs.sendMessage(tab.id, { type: 'SHOW_PANEL' })
    return { ok: true }
  }
  throw new Error('此操作不受支持。')
}

chrome.runtime.onMessage.addListener((message, sender, respond) => {
  handle(message, sender).then(data => respond({ ok: true, data }), error => respond({ ok: false, error: error.message }))
  return true
})
chrome.tabs.onRemoved.addListener(async id => {
  await chrome.storage.session.remove('selection:' + id)
  if ((await state())?.tabId === id) {
    await chrome.storage.session.clear()
    await broadcast('HIDE_PANEL')
  }
})
