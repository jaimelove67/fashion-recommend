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
      reject(new Error('请安装扩展，在本项目网页的浏览器工具栏点击“知己跨站试穿”，选择“连接此项目网站”，再点击开启。'))
    }, timeoutMs)
    window.addEventListener('message', receive)
    window.postMessage({ channel: 'ZHIJI_TRY_ON_WEB', type, requestId, ...payload }, location.origin)
  })
}

export function disconnectTryOnExtension() {
  window.postMessage({ channel: 'ZHIJI_TRY_ON_WEB', type: 'DISCONNECT' }, location.origin)
}
