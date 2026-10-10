<script setup>
import { ref, watch } from 'vue'
import { ArrowUpRight, Camera, Download, LoaderCircle } from '@lucide/vue'
import { requestTryOnExtension } from '../utils/extensionBridge.js'
const props = defineProps({ username: { type: String, required: true } })
const busy = ref(false)
const connected = ref(false)
const error = ref('')
watch(() => props.username, () => { connected.value = false; error.value = '' })
async function connect() {
  if (busy.value) return
  busy.value = true; error.value = ''
  const username = props.username
  try {
    await requestTryOnExtension('CONNECT_AND_OPEN', { username })
    if (props.username === username) connected.value = true
  } catch (cause) { if (props.username === username) error.value = cause.message }
  finally { busy.value = false }
}
</script>
<template>
  <section class="cross-site-launcher" aria-labelledby="cross-site-title">
    <div><p class="eyebrow">试穿探索</p><h2 id="cross-site-title">把喜欢的款式，穿在自己身上看看</h2>
      <p>开启试穿浮窗，去购物网站拖入衣服图片；喜欢后保存回来，继续探索搭配。</p>
    </div>
    <div class="launcher-actions">
      <button type="button" data-zhiji-cross-site :disabled="busy" @click="connect"><LoaderCircle v-if="busy" :size="17" class="spinning" /><Camera v-else :size="17" />{{ busy ? '正在连接…' : '开启跨站试穿' }}</button>
      <a href="/downloads/zhiji-try-on-extension.zip" download><Download :size="15" />下载配套扩展</a>
      <a href="#favorites"><ArrowUpRight :size="15" />我的喜欢穿搭</a>
    </div>
    <p v-if="connected" class="connection-note" role="status">已开启。请保留项目标签页；首次在购物网站使用时，点击扩展图标，在本站启用浮窗。</p>
    <p v-if="error" class="connection-error" role="alert">{{ error }}</p>
    <details><summary>首次使用怎么安装？</summary><ol><li>下载扩展压缩包并解压，打开 Chrome 的扩展管理页，或 Edge 的扩展页。</li><li>打开“开发者模式”，点击“加载已解压的扩展程序”，选择解压后的文件夹。</li><li>在项目网页点击工具栏中的“知己跨站试穿”，选择“连接此项目网站”并批准权限，再点击本页的“开启跨站试穿”。</li><li>购物网站按站点授权。拖入图片后，点击同意开始，并允许摄像头；试穿满意后点击“喜欢”。</li></ol></details>
  </section>
</template>
<style scoped>
.cross-site-launcher{display:grid;grid-template-columns:minmax(0,1fr) auto;gap:18px 32px;margin:0 0 36px;padding:26px 30px;border:1px solid var(--line);border-radius:var(--radius);background:var(--accent-soft);color:var(--ink)}.eyebrow{margin:0 0 8px;color:var(--accent);font-size:12px;font-weight:600}.cross-site-launcher h2{margin:0;font-family:var(--font-display);font-size:clamp(23px,2.6vw,32px);font-weight:600}.cross-site-launcher p{margin:10px 0 0;color:var(--muted);font-size:13px;line-height:1.8}.launcher-actions{display:flex;flex-direction:column;align-items:start;justify-content:center;gap:12px}.launcher-actions button{display:flex;min-height:44px;align-items:center;gap:8px;border:0;border-radius:var(--radius);padding:12px 20px;background:var(--accent-strong);color:var(--surface);font:inherit;font-size:13px;cursor:pointer}.launcher-actions button:disabled{opacity:.6}.launcher-actions a{display:flex;align-items:center;gap:8px;color:var(--accent);font-size:12px;text-decoration:none}.cross-site-launcher details,.connection-note,.connection-error{grid-column:1/-1}.cross-site-launcher details{color:var(--muted);font-size:12px;line-height:1.8}.cross-site-launcher summary{cursor:pointer;width:fit-content}.cross-site-launcher ol{margin:10px 0 0;padding-left:20px}.connection-error{color:var(--danger)!important}.spinning{animation:spin 1s linear infinite}@keyframes spin{to{transform:rotate(360deg)}}@media(max-width:760px){.cross-site-launcher{grid-template-columns:1fr;padding:20px;gap:16px}.launcher-actions{flex-direction:row;flex-wrap:wrap;align-items:center;gap:14px 20px}}
</style>
