export function requestTryOnExtension(type, payload = {}, timeoutMs = 5000) {
  return new Promise((resolve, reject) => {
    const requestId = crypto.randomUUID()
    const cleanup = () => { clearTimeout(timer); window.removeEventListener('message', receive) }
    const receive = event => {
      if (event.source !== window || event.origin !== location.origin
        || event.data?.channel !== 'ZHIJI_TRY_ON_EXTENSION' || event.data.requestId !== requestId) return
      cleanup()
      if (event.data.ok) resolve(event.data.data)
      else reject(new Error(event.data.error || '扩展连接失败，请重试。'))
    }
    const timer = setTimeout(() => {
      cleanup()
      const error = new Error('未检测到扩展连接。请按引导安装并连接本站；如果已安装，打开浏览器工具栏中的“知己 · 跨站试穿”，选择“连接此项目网站”，再重试。')
      error.code = 'EXTENSION_UNAVAILABLE'
      reject(error)
    }, timeoutMs)
    window.addEventListener('message', receive)
    window.postMessage({ channel: 'ZHIJI_TRY_ON_WEB', type, requestId, ...payload }, location.origin)
  })
}

export function disconnectTryOnExtension() {
  window.postMessage({ channel: 'ZHIJI_TRY_ON_WEB', type: 'DISCONNECT' }, location.origin)
}
