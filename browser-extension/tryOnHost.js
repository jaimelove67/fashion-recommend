// Serialized into the provider tab's isolated extension world. Controls stay in
// an extension iframe; the service is its sibling under the service's own origin.
export function installTryOnHost(payload, origin, url, controlsUrl) {
  let host = globalThis.__zhijiTryOnHost
  if (!host) {
    const css = '*{box-sizing:border-box}html,body{margin:0;height:100%;font:13px system-ui;color:#233e34;background:#fafaf5}.zhiji-window{height:100vh;display:flex;flex-direction:column}.zhiji-header{display:flex;align-items:center;gap:12px;padding:10px 14px;background:#174f42;color:white}.zhiji-header strong{flex:1}.zhiji-header button{font:inherit;padding:7px 12px;border:1px solid #ffffff55;border-radius:6px;background:transparent;color:white;cursor:pointer}.zhiji-header button:disabled{opacity:.5;cursor:default}.zhiji-content{display:flex;flex:1;min-height:0}.zhiji-preview{flex:1;min-width:0;background:#eee;display:flex;align-items:center;justify-content:center}.zhiji-controls{width:300px;flex:none;border-left:1px solid #cad5cb}iframe{display:block;border:0;width:100%;height:100%}.zhiji-notice{margin:0;padding:8px 14px;background:#eef2e9;color:#536c5b;font-size:12px}@media(max-width:650px){.zhiji-controls{width:240px}.zhiji-header{gap:6px;padding:8px}}'
    const style = document.createElement('style'); style.textContent = css; document.head.append(style)
    const container = document.createElement('main'); container.className = 'zhiji-window'
    const header = document.createElement('header'); header.className = 'zhiji-header'
    const title = document.createElement('strong'); title.textContent = '知己 · 试穿与收藏'
    const pin = document.createElement('button'); pin.type = 'button'; pin.textContent = '置顶显示'
    const notice = document.createElement('p'); notice.className = 'zhiji-notice'; notice.setAttribute('role', 'status')
    notice.textContent = '选图、试穿和收藏都在此窗口完成。浏览其他网页时，可开启置顶显示。'
    const content = document.createElement('div'); content.className = 'zhiji-content'
    const preview = document.createElement('section'); preview.className = 'zhiji-preview'; preview.setAttribute('aria-label', '实时试穿画面')
    const controls = document.createElement('iframe'); controls.className = 'zhiji-controls'; controls.title = '知己试穿操作面板'
    controls.referrerPolicy = 'no-referrer'; controls.src = controlsUrl
    header.append(title, pin); content.append(preview, controls); container.append(header, notice, content)
    document.title = '知己 · 试穿与收藏'; document.body.replaceChildren(container)
    host = globalThis.__zhijiTryOnHost = { container, preview, controls, frame: null, payload: null, pip: null, listener: null }
    if (!window.documentPictureInPicture?.requestWindow) { pin.disabled = true; pin.textContent = '当前浏览器不支持置顶' }
    pin.onclick = async () => {
      if (host.pip && !host.pip.closed) { host.pip.close(); window.focus(); return }
      try {
        const pip = await window.documentPictureInPicture.requestWindow({ width: 900, height: 700 })
        host.pip = pip
        const pipStyle = pip.document.createElement('style'); pipStyle.textContent = css; pip.document.head.append(pipStyle)
        pip.document.body.append(container)
        if (host.listener) pip.addEventListener('message', host.listener)
        pin.textContent = '返回普通窗口'; notice.textContent = '已置顶。此窗口会保留在其他网页上方；请保留项目标签页和原试穿窗口。'
        pip.addEventListener('pagehide', () => {
          if (host.listener) pip.removeEventListener('message', host.listener)
          document.body.append(container); host.pip = null; pin.textContent = '置顶显示'
          notice.textContent = '已返回普通窗口。浏览其他网页时，可再次开启置顶显示。'
        }, { once: true })
      } catch {
        notice.textContent = '无法开启置顶显示。请在支持此功能的桌面 Chrome / Edge 中点击“置顶显示”重试。'
      }
    }
  }
  if (host.listener) { window.removeEventListener('message', host.listener); host.pip?.removeEventListener('message', host.listener) }
  host.frame?.remove(); host.payload = payload
  const frame = document.createElement('iframe'); host.frame = frame
  frame.title = 'Anywear 实时试衣窗口'; frame.allow = `camera ${origin}; fullscreen ${origin}`
  frame.sandbox = 'allow-scripts allow-same-origin'; frame.referrerPolicy = 'no-referrer'
  host.listener = event => {
    if (event.source !== frame.contentWindow || event.origin !== origin || !event.data || typeof event.data !== 'object') return
    const data = event.data
    if (data.type === 'DECART_TRACK_EVENT' && (!data.properties?.tryon_id || data.properties.tryon_id === payload.tryonId)) {
      void chrome.runtime.sendMessage({ type: 'PROVIDER_EVENT', tryonId: payload.tryonId,
        data: { type: data.type, event: data.event, properties: { tryon_id: data.properties?.tryon_id } } }).catch(() => {})
    } else if (['DECART_CAMERA_FLIP', 'DECART_RESTART_SAME_GARMENT'].includes(data.type)) {
      void chrome.runtime.sendMessage({ type: 'PROVIDER_EVENT', tryonId: payload.tryonId, data: { type: data.type } }).catch(() => {})
    }
  }
  window.addEventListener('message', host.listener); host.pip?.addEventListener('message', host.listener)
  frame.onload = () => {
    frame.contentWindow.postMessage({ type: 'DECART_FULLSCREEN_MODE' }, origin)
    frame.contentWindow.postMessage({ type: 'DECART_AUTO_START', ...payload }, origin)
  }
  host.preview.replaceChildren(frame); frame.src = url
}

export function stopTryOnHost(clearImage = false, expectedTryonId = '') {
  const host = globalThis.__zhijiTryOnHost
  if (!host) return
  if (expectedTryonId && host.payload?.tryonId !== expectedTryonId) return
  if (host.listener) { window.removeEventListener('message', host.listener); host.pip?.removeEventListener('message', host.listener); host.listener = null }
  host.frame?.remove(); host.frame = null; host.preview.textContent = '选择衣物并开始试穿'
  if (clearImage) host.payload = null
}

export function readTryOnHostImage() { return globalThis.__zhijiTryOnHost?.payload?.productImageUrl || '' }
