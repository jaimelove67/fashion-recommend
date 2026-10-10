import { sitePermission } from './images.js'
const send = async message => {
  const response = await chrome.runtime.sendMessage(message)
  if (!response?.ok) throw new Error(response?.error || '扩展连接失败')
  return response.data
}
const showError = error => { const field = document.getElementById('error'); field.textContent = error.message; field.hidden = false }
const action = (id, message) => document.getElementById(id).onclick = async () => {
  try { await send(message); window.close() } catch (error) { showError(error) }
}
async function enableSite(bindProject = false) {
  try {
    const [tab] = await chrome.tabs.query({ active: true, currentWindow: true })
    if (!tab?.url) throw new Error('请在项目或购物网页中打开扩展。')
    const pattern = sitePermission(tab.url)
    if (!await chrome.permissions.request({ origins: [pattern] })) throw new Error('未授权本站，试穿浮窗尚未启用。')
    // Register only this exact, explicitly approved site. No default content
    // scripts or wildcard host access are enabled on installation.
    const id = 'site_' + btoa(pattern).replace(/[^a-zA-Z0-9]/g, '_')
    const scripts = await chrome.scripting.getRegisteredContentScripts({ ids: [id] })
    if (!scripts.length) await chrome.scripting.registerContentScripts([{ id, matches: [pattern], js: ['content.js'], runAt: 'document_idle' }])
    await chrome.scripting.executeScript({ target: { tabId: tab.id }, files: ['content.js'] })
    if (bindProject) await send({ type: 'BIND_PROJECT', tabId: tab.id })
    else await send({ type: 'SHOW_CURRENT' })
    window.close()
  } catch (error) { showError(error) }
}
document.getElementById('enable').onclick = () => void enableSite()
document.getElementById('bind').onclick = () => void enableSite(true)
action('project', { type: 'OPEN_PROJECT' }); action('favorites', { type: 'OPEN_PROJECT', favorites: true }); action('stop', { type: 'STOP_ALL' })
void send({ type: 'GET_STATE' }).then(data => {
  document.getElementById('connection').textContent = data.connected ? `已连接项目账号：${data.username}` : '尚未连接项目'
}).catch(showError)
