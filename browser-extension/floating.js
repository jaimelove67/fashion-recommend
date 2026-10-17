import { ANYWEAR_ORIGIN } from './liveTryOn.js'
import { sitePermission } from './images.js'

const $ = id => document.getElementById(id)
let selected = null, image = '', consent = false, active = false, tryonId = '', version = 0, timer = null, saving = false, streaming = false, queued = false, tried = false, saved = false
const integrated = new URLSearchParams(location.search).has('controls')
const send = async message => {
  const result = await chrome.runtime.sendMessage(message)
  if (!result?.ok) throw new Error(result?.error || '连接失败，请在项目中重新连接。')
  return result.data
}
function stop() {
  version++; clearTimeout(timer); timer = null
  if (tryonId) void send({ type: 'CLOSE_TRY_ON', tryonId }).catch(() => {})
  active = false; tryonId = ''; streaming = queued = false; $('like').disabled = true; $('preview').textContent = ''
}
function syncButtons() { $('like').disabled = !tried || saving || saved; $('start').disabled = saving || !selected || (active && !streaming) }
function fail(message) { stop(); $('error').textContent = message; $('error').hidden = false; $('status').textContent = '暂时无法试穿'; $('start').disabled = !selected }
function wait(timeout) { clearTimeout(timer); timer = setTimeout(() => fail('试衣服务未及时响应。请检查独立试穿窗口、网络和摄像头权限后重试。'), timeout) }
function showSelection(item, data = '') {
  tried = false; saved = false; image = data; selected = item; $('photo').src = data || ''; $('photo').hidden = !data
  $('name').value = item.name || '购物单品'; $('garment').hidden = false; $('start').disabled = false
  $('status').textContent = data ? '图片已准备好' : '已选中商品，点击开始以读取图片并试穿'
  $('error').hidden = true; $('like').textContent = '♡ 喜欢，保存到项目'
}
async function selectItem(item) {
  stop(); showSelection(item)
  const current = version
  if (consent && await chrome.permissions.contains({ origins: [sitePermission(item.imageUrl)] }) && current === version) void start()
}
async function syncConnection() {
  const data = await send({ type: 'GET_STATE' })
  if (!data.connected) { fail('请先登录项目，在趋势页开启跨站试穿。'); return }
  if (integrated && data.integrated) {
    const session = data.integrated
    showSelection(session.item, session.image)
    $('category').value = session.item.category || '上装'
    tryonId = session.tryonId || ''; consent = true; active = Boolean(tryonId)
    streaming = session.stage === 'streaming'; queued = session.stage === 'queued'; tried = session.hasRendered
    $('consent').hidden = true; $('start').textContent = '重新试穿此单品'
    $('status').textContent = streaming ? '实时试穿中' : queued ? '服务正在排队，请查看窗口提示' : session.stage === 'ended' ? '本次试穿已结束' : active ? '正在连接，请在左侧试穿画面中允许使用摄像头…' : '图片已准备好'
    if (active && !streaming && !queued) wait(45000)
    syncButtons(); await send({ type: 'CONTROLS_READY' }); return
  }
  if (data.item && (!selected || data.item.imageUrl !== selected.imageUrl)) void selectItem(data.item)
  else if (!selected) { $('error').hidden = true; $('status').textContent = '先拖入一张清晰的衣服图片' }
}
async function start(requestPermission = false) {
  if (!selected || saving) return
  stop(); tried = false; const current = version; $('start').disabled = true; $('error').hidden = true
  try {
    const origins = [ANYWEAR_ORIGIN + '/*']
    if (!image) origins.push(sitePermission(selected.imageUrl))
    // Request all required hosts in the original click gesture, before fetching.
    const allowed = requestPermission ? await chrome.permissions.request({ origins }) : await chrome.permissions.contains({ origins })
    if (current !== version) return
    if (!allowed) throw new Error('试穿需要你授权读取商品图片及访问 Anywear 试衣服务。')
    if (!image) {
      $('status').textContent = '正在读取商品图片…'
      const result = await send({ type: 'READ_IMAGE', url: selected.imageUrl })
      if (current !== version) return
      image = result.image; $('photo').src = image; $('photo').hidden = false
    }
    if (current !== version) return
    consent = true; $('consent').hidden = true; $('start').textContent = '重新试穿此单品'
    tryonId = 'tryon_' + crypto.randomUUID()
    const activeId = tryonId
    active = true
    $('preview').textContent = integrated ? '' : '正在打开选图、试穿和收藏合一的窗口…'
    $('status').textContent = '正在打开独立试穿窗口…'; wait(25000)
    await send({ type: 'OPEN_TRY_ON', tryonId: activeId, image, category: $('category').value,
      item: { ...selected, name: $('name').value.trim() || selected.name } })
    if (current !== version) { void send({ type: 'CLOSE_TRY_ON', tryonId: activeId }).catch(() => {}); return }
    if (!streaming && !queued) { $('status').textContent = integrated ? '正在连接，请在左侧试穿画面中允许使用摄像头…' : '正在连接，请在独立试穿窗口中允许使用摄像头…'; wait(45000) }
  } catch (error) { if (current === version) fail(error.message) }
}
window.addEventListener('message', event => {
  // Parent messages only suspend the camera or report a drop error. They cannot
  // read images/account data, start inference or submit a favorite.
  if (event.source === parent && event.data?.type === 'ZHIJI_SUSPEND') {
    stop(); $('status').textContent = '已停止试穿，点击重新试穿可继续'; syncButtons(); return
  }
  if (event.source === parent && event.data?.type === 'ZHIJI_DROP_ERROR') { fail(String(event.data.error).slice(0, 200)); return }
  if (event.source === parent && event.data?.type === 'ZHIJI_SELECTION_CHANGED') {
    void send({ type: 'GET_STATE' }).then(data => { if (data.item) void selectItem(data.item) }).catch(error => fail(error.message)); return
  }
  if (event.source === parent && event.data?.type === 'ZHIJI_CONNECTION_CHANGED') {
    void syncConnection().catch(error => fail(error.message)); return
  }
})
chrome.runtime.onMessage.addListener((message, sender) => {
  if (sender.id !== chrome.runtime.id || message.type !== 'TRY_ON_WINDOW_EVENT' || !tryonId || message.tryonId !== tryonId) return
  if (message.event === 'closed') { stop(); $('status').textContent = '试穿窗口已关闭'; syncButtons(); return }
  const data = message.data
  if (!data || typeof data !== 'object') return
  if (['DECART_CAMERA_FLIP', 'DECART_RESTART_SAME_GARMENT'].includes(data.type)) { void start(); return }
  if (data.type !== 'DECART_TRACK_EVENT' || (data.properties?.tryon_id && data.properties.tryon_id !== tryonId)) return
  if (data.event === 'first_frame_rendered' && data.properties?.tryon_id === tryonId) {
    clearTimeout(timer); tried = true; streaming = true; $('status').textContent = '实时试穿中'; syncButtons()
  } else if (data.event === 'queue_entered') { queued = true; clearTimeout(timer); $('status').textContent = '服务正在排队，请查看窗口提示' }
  else if (data.event === 'queue_completed') { queued = false; $('status').textContent = '正在连接试衣服务…'; wait(45000) }
  else if (data.event === 'first_frame_timeout') fail('试穿画面未能生成，请检查摄像头权限后重试。')
  else if (data.event === 'session_ended') { stop(); $('status').textContent = '本次试穿已结束'; syncButtons() }
})
$('file').onchange = event => {
  const file = event.target.files[0]; if (!file) return
  stop(); const current = version
  if (!['image/png', 'image/jpeg', 'image/webp'].includes(file.type) || !file.size || file.size > 10485760) { fail('请选择不超过 10 MB 的 JPG、PNG 或 WebP 图片。'); return }
  const reader = new FileReader()
  reader.onload = () => {
    if (current !== version) return
    showSelection(selected || { name: file.name, sourceUrl: '', sourceKind: 'SHOPPING' }, reader.result)
    if (consent) void start()
  }
  reader.onerror = () => { if (current === version) fail('图片读取失败，请重新选择。') }
  reader.readAsDataURL(file)
}
$('category').onchange = () => { stop(); tried = false; $('start').disabled = !selected; $('status').textContent = '类别已更改，请重新试穿' }
$('start').onclick = () => void start(true)
$('like').onclick = async () => {
  if (saving || $('like').disabled || !image) return
  const current = version, item = { ...selected, imageData: image, name: $('name').value.trim(), category: $('category').value }
  if (!item.name) { $('error').textContent = '请填写单品名称。'; $('error').hidden = false; return }
  saving = true; syncButtons(); $('like').textContent = '正在保存…'; $('error').hidden = true
  try {
    const result = await send({ type: 'SAVE_FAVORITE', item })
    if (!result?.ok) throw new Error(result?.error || '收藏失败，请重试。')
    if (current === version) { saved = true; $('like').textContent = '♥ 已保存到喜欢穿搭' }
  } catch (error) {
    if (current === version) { $('error').textContent = error.message; $('error').hidden = false; $('like').disabled = false; $('like').textContent = '♡ 重试收藏' }
  } finally {
    saving = false
    // A new product can be dropped while the previous favorite is being saved.
    // Restore controls for the current frame without marking that product saved.
    syncButtons()
  }
}
$('favorites').onclick = () => void send({ type: 'OPEN_PROJECT', favorites: true }).catch(error => fail(error.message))
window.addEventListener('pagehide', () => { clearTimeout(timer); if (!integrated) stop() })
if (integrated) {
  document.querySelector('.drop-zone strong').textContent = '把其他网页中的衣服图片拖到这里'
  window.addEventListener('dragover', event => event.preventDefault())
  window.addEventListener('drop', event => {
    if (!event.isTrusted || saving) return
    if (event.dataTransfer.files.length) {
      event.preventDefault(); $('file').files = event.dataTransfer.files; $('file').dispatchEvent(new Event('change')); return
    }
    const html = new DOMParser().parseFromString(event.dataTransfer.getData('text/html'), 'text/html')
    const url = html.querySelector('img')?.getAttribute('src') || event.dataTransfer.getData('text/uri-list').split(/\r?\n/).find(line => line && !line.startsWith('#'))
    const source = html.querySelector('a')?.getAttribute('href') || ''
    if (!url) return
    event.preventDefault(); stop(); const current = version; $('start').disabled = true
    void send({ type: 'SELECT_INTEGRATED_GARMENT', item: { imageUrl: url,
      name: html.querySelector('img')?.getAttribute('alt') || '购物单品', sourceUrl: /^https?:\/\//.test(source) ? source : '' } })
      .then(result => { if (current === version) showSelection(result.item) })
      .catch(error => { if (current === version) fail(error.message) })
  })
}
void syncConnection().catch(error => fail(error.message))
