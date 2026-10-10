(() => {
  if (window.top !== window || window.__zhijiTryOnContentLoaded) return
  window.__zhijiTryOnContentLoaded = true
  let host = null, panel = null, frame = null, dragged = null, minimized = false, authorizedUntil = 0
  const extensionOrigin = chrome.runtime.getURL('').replace(/\/$/, '')
  const send = async message => {
    const result = await chrome.runtime.sendMessage(message)
    if (!result?.ok) throw new Error(result?.error || '扩展连接失败，请刷新页面。')
    return result.data
  }
  const api = async (path, options = {}) => {
    const response = await fetch(path, { ...options, credentials: 'same-origin', redirect: 'error', cache: 'no-store' })
    const body = await response.json().catch(() => null)
    if (!response.ok || body?.code !== 0) throw new Error(body?.message || '项目登录已过期，请重新登录并连接试穿。')
    return body.data
  }
  function hide() { host?.remove(); host = panel = frame = null; minimized = false }
  function show() {
    if (host) { frame?.contentWindow.postMessage({ type: 'ZHIJI_CONNECTION_CHANGED' }, extensionOrigin); return }
    host = document.createElement('div')
    host.id = 'zhiji-try-on-host'
    host.style.cssText = 'position:fixed!important;right:20px!important;bottom:20px!important;z-index:2147483647!important;display:block!important;'
    const root = host.attachShadow({ mode: 'closed' })
    const style = document.createElement('style')
    style.textContent = ':host{font:14px system-ui;color:#193b31}section{width:min(356px,calc(100vw - 24px));border:1px solid #cad5cb;border-radius:12px;background:#fafaf5;box-shadow:0 12px 48px #14282055;overflow:hidden}header{display:flex;align-items:center;gap:8px;height:42px;padding:0 10px;background:#174f42;color:#fff;cursor:move;touch-action:none}strong{flex:1;font-size:13px}button{width:30px;height:30px;border:0;border-radius:5px;background:#ffffff18;color:#fff;cursor:pointer}iframe{display:block;width:100%;height:min(610px,calc(100dvh - 100px));border:0}section.dragging{outline:3px solid #bd9b5c}section.dragging iframe{pointer-events:none}'
    panel = document.createElement('section'); panel.setAttribute('aria-label', '知己跨站试穿浮窗')
    const header = document.createElement('header')
    const title = document.createElement('strong'); title.textContent = '知己 · 跨站试穿'
    const minimize = document.createElement('button'); minimize.textContent = '−'; minimize.setAttribute('aria-label', '收起试穿浮窗')
    const close = document.createElement('button'); close.textContent = '×'; close.setAttribute('aria-label', '关闭跨站试穿')
    frame = document.createElement('iframe'); frame.src = chrome.runtime.getURL('floating.html'); frame.title = '知己跨站试穿浮窗'
    frame.allow = `camera ${extensionOrigin} https://anywear.decart.ai; fullscreen https://anywear.decart.ai`
    frame.referrerPolicy = 'no-referrer'
    header.append(title, minimize, close); panel.append(header, frame); root.append(style, panel)
    document.documentElement.append(host)
    minimize.onclick = () => {
      minimized = !minimized; frame.style.display = minimized ? 'none' : 'block'; minimize.textContent = minimized ? '+' : '−'
      minimize.setAttribute('aria-label', minimized ? '展开试穿浮窗' : '收起试穿浮窗')
      if (minimized) frame.contentWindow.postMessage({ type: 'ZHIJI_SUSPEND' }, extensionOrigin)
    }
    close.onclick = () => { hide(); void send({ type: 'STOP_ALL' }).catch(() => {}) }
    let move = null
    header.onpointerdown = event => {
      if (event.target.closest('button')) return
      const rect = host.getBoundingClientRect(); move = { x: event.clientX, y: event.clientY, left: rect.left, top: rect.top }
      header.setPointerCapture(event.pointerId)
    }
    header.onpointermove = event => {
      if (!move) return
      host.style.setProperty('right', 'auto', 'important'); host.style.setProperty('bottom', 'auto', 'important')
      host.style.setProperty('left', Math.max(0, Math.min(innerWidth - host.offsetWidth, move.left + event.clientX - move.x)) + 'px', 'important')
      host.style.setProperty('top', Math.max(0, Math.min(innerHeight - host.offsetHeight, move.top + event.clientY - move.y)) + 'px', 'important')
    }
    header.onpointerup = header.onpointercancel = () => { move = null }
    panel.ondragover = event => { if (dragged) { event.preventDefault(); event.dataTransfer.dropEffect = 'copy' } }
    panel.ondrop = event => {
      if (!event.isTrusted || !dragged) return
      event.preventDefault(); event.stopPropagation(); if (minimized) minimize.click()
      void send({ type: 'SELECT_GARMENT', item: dragged }).catch(error => {
        frame.contentWindow.postMessage({ type: 'ZHIJI_DROP_ERROR', error: error.message }, extensionOrigin)
      })
      panel.classList.remove('dragging')
    }
  }
  document.addEventListener('click', event => {
    if (event.isTrusted && event.target.closest('[data-zhiji-cross-site]')) authorizedUntil = Date.now() + 15000
  }, true)
  window.addEventListener('message', async event => {
    if (event.source !== window || event.origin !== location.origin || event.data?.channel !== 'ZHIJI_TRY_ON_WEB') return
    const message = event.data
    if (message.type === 'DISCONNECT') { void send({ type: 'DISCONNECT_PROJECT' }).catch(() => {}); return }
    if (!['PING', 'CONNECT_AND_OPEN'].includes(message.type)) return
    try {
      let data = { version: chrome.runtime.getManifest().version }
      if (message.type === 'CONNECT_AND_OPEN') {
        if (Date.now() > authorizedUntil) throw new Error('请点击项目中的“开启跨站试穿”按钮。')
        authorizedUntil = 0
        const account = await api('/api/v1/auth/me')
        if (account?.username !== message.username) throw new Error('项目账号已变更，请刷新后重试。')
        data = await send({ type: 'CONNECT', origin: location.origin, username: account.username }); show()
      }
      window.postMessage({ channel: 'ZHIJI_TRY_ON_EXTENSION', requestId: message.requestId, ok: true, data }, location.origin)
    } catch (error) {
      window.postMessage({ channel: 'ZHIJI_TRY_ON_EXTENSION', requestId: message.requestId, ok: false, error: error.message }, location.origin)
    }
  })
  document.addEventListener('dragstart', event => {
    if (!event.isTrusted || !panel) return
    const image = event.target.closest?.('img'); if (!image) return
    dragged = { imageUrl: image.currentSrc || image.src, sourceUrl: image.closest('a[href]')?.href || location.href,
      name: image.alt?.trim() || document.title || '购物单品' }
    panel.classList.add('dragging')
  }, true)
  document.addEventListener('dragend', () => { dragged = null; panel?.classList.remove('dragging') }, true)
  chrome.runtime.onMessage.addListener((message, sender, respond) => {
    if (sender.id !== chrome.runtime.id) return
    if (message.type === 'SHOW_PANEL') show()
    else if (message.type === 'HIDE_PANEL') hide()
    else if (message.type === 'GARMENT_SELECTED') frame?.contentWindow.postMessage({ type: 'ZHIJI_SELECTION_CHANGED' }, extensionOrigin)
    else if (message.type === 'SAVE_TO_PROJECT') {
      ;(async () => {
        if (location.origin !== message.origin) throw new Error('项目标签页已离开，请重新连接。')
        const account = await api('/api/v1/auth/me')
        if (account?.username !== message.expectedUser) throw new Error('项目账号已变更，请重新连接后收藏。')
        const match = /^data:(image\/(?:png|jpeg|webp));base64,([A-Za-z0-9+/]+={0,2})$/.exec(message.item?.imageData || '')
        if (!match || match[2].length > 13981016) throw new Error('单品图片无效或超过 10 MB。')
        const binary = atob(match[2]); const form = new FormData()
        form.append('image', new Blob([Uint8Array.from(binary, char => char.charCodeAt(0))], { type: match[1] }), 'garment.' + match[1].split('/')[1])
        for (const key of ['name', 'category', 'sourceUrl']) form.append(key, message.item[key] || '')
        form.append('sourceKind', 'SHOPPING'); form.append('expectedUser', message.expectedUser)
        const csrf = await api('/api/v1/auth/csrf')
        if (!csrf?.headerName || !csrf.token) throw new Error('项目登录已过期，请重新连接。')
        const result = await api('/api/v1/me/try-on-favorites', { method: 'POST', body: form, headers: { [csrf.headerName]: csrf.token } })
        window.postMessage({ channel: 'ZHIJI_TRY_ON_EXTENSION', type: 'FAVORITE_SAVED' }, location.origin)
        return result
      })().then(data => respond({ ok: true, data }), error => respond({ ok: false, error: error.message }))
      return true
    }
  })
  void send({ type: 'IS_ENABLED' }).then(data => { if (data.enabled) show() }).catch(() => {})
})()
