<script setup>
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { ArrowRight, ArrowUpRight, Camera, CheckCircle2, ChevronRight, Download, Heart, Info, LoaderCircle, MousePointer2, Shirt } from '@lucide/vue'
import { requestTryOnExtension } from '../utils/extensionBridge.js'
import CrossSiteTryOnSetupDialog from './CrossSiteTryOnSetupDialog.vue'
const props = defineProps({ username: { type: String, required: true } })
const busy = ref(false)
const connected = ref(false)
const error = ref('')
const installationOpen = ref(false)
const initialStep = ref(0)
const connectButton = ref(null)
let active = true
let requestVersion = 0
function openInstallation(step = 0) { initialStep.value = step; installationOpen.value = true }
defineExpose({ openInstallation })
function closeInstallation() {
  installationOpen.value = false
  void nextTick(() => connectButton.value?.focus())
}
watch(() => props.username, () => {
  requestVersion += 1; busy.value = false; connected.value = false; error.value = ''; installationOpen.value = false
})
onBeforeUnmount(() => { active = false; requestVersion += 1 })
async function connect() {
  if (busy.value) return
  busy.value = true; connected.value = false; error.value = ''
  const username = props.username
  const version = ++requestVersion
  try {
    await requestTryOnExtension('CONNECT_AND_OPEN', { username })
    if (active && requestVersion === version && props.username === username) connected.value = true
  } catch (cause) {
    if (active && requestVersion === version && props.username === username) {
      connected.value = false; error.value = cause.message
      if (!installationOpen.value) openInstallation(cause.code === 'EXTENSION_UNAVAILABLE' ? 0 : 3)
    }
  }
  finally { if (active && requestVersion === version) busy.value = false }
}
</script>
<template>
  <section class="cross-site-launcher" aria-labelledby="cross-site-title">
    <div class="launcher-main">
      <div class="launcher-copy">
        <p class="launcher-eyebrow"><span class="eyebrow-icon"><Shirt :size="16" /></span>试穿探索<span class="eyebrow-divider" />购物网页内浮窗</p>
        <h2 id="cross-site-title"><span>把喜欢的款式，</span><span>穿在自己身上看看</span></h2>
        <p class="launcher-description">在购物网页拖入衣服图片，实时看看上身效果。喜欢的单品，随时保存回这里。</p>
        <div class="launcher-actions">
          <button ref="connectButton" type="button" class="launcher-start" data-zhiji-cross-site :disabled="busy" @click="connect"><LoaderCircle v-if="busy" :size="18" class="spinning" /><Camera v-else :size="18" /><span>{{ busy ? '正在连接…' : '开启跨站试穿' }}</span><ArrowUpRight :size="16" /></button>
          <a class="launcher-favorites" href="#favorites"><Heart :size="17" />我的喜欢穿搭<ArrowRight :size="15" /></a>
        </div>
      </div>

      <div class="launcher-illustration" aria-hidden="true">
        <div class="preview-browser">
          <div class="preview-toolbar"><span class="window-dots"><i /><i /><i /></span><span>购物网页</span><span class="preview-demo-label">示意</span></div>
          <div class="preview-content">
            <div class="preview-garment"><img src="/assets/trend-pieces/white-shirt.png" alt="" loading="lazy" /><span>喜欢的款式</span></div>
            <div class="preview-floating"><div class="preview-floating-header"><Camera :size="12" /><span>试穿浮窗</span><span class="preview-window-controls">− ×</span></div><div class="preview-camera"><Shirt :size="29" /><span>拖入衣服图片</span></div><div class="preview-save"><Heart :size="11" />保存喜欢的单品</div></div>
            <div class="preview-drag"><MousePointer2 :size="16" /><span>拖入试穿</span></div>
          </div>
        </div>
        <span class="illustration-caption">选中一件，试穿一下</span>
      </div>
    </div>

    <div class="launcher-footer">
      <ol class="launcher-flow" aria-label="试穿使用流程"><li><MousePointer2 :size="14" />拖入商品图</li><li class="flow-divider" aria-hidden="true"><ChevronRight :size="13" /></li><li><Shirt :size="14" />查看上身效果</li><li class="flow-divider" aria-hidden="true"><ChevronRight :size="13" /></li><li><Heart :size="14" />保存喜欢</li></ol>
      <div class="launcher-tools"><a href="/downloads/zhiji-try-on-extension.zip" download @click="openInstallation(1)"><Download :size="14" />下载配套扩展</a><span class="tools-divider" /><button type="button" class="installation-help" @click="openInstallation()">首次使用怎么安装？<ArrowUpRight :size="13" /></button></div>
    </div>
    <p v-if="!connected && !error" class="launcher-note installation-hint"><Info :size="13" />首次使用会引导你完成扩展安装与连接。</p>
    <p v-if="connected" class="launcher-note connection-note" role="status"><CheckCircle2 :size="15" />已开启。请保留项目标签页；首次在购物网站使用时，点击扩展图标，在本站启用浮窗。</p>
    <p v-if="error" class="launcher-note connection-error" role="alert"><Info :size="15" />{{ error }}</p>
  </section>
  <CrossSiteTryOnSetupDialog v-if="installationOpen" :initial-step="initialStep" :username="username" :busy="busy" :connected="connected" :error="error" @close="closeInstallation" @connect="connect" />
</template>
<style scoped>
.cross-site-launcher{position:relative;margin:0 0 var(--trend-section-gap,40px);border:1px solid var(--line);border-radius:16px;background:var(--surface);color:var(--ink);box-shadow:0 8px 30px rgba(48,69,50,.035);overflow:hidden}
.launcher-main{display:grid;grid-template-columns:minmax(0,1fr) 330px;align-items:center;gap:48px;padding:30px var(--trend-panel-padding,24px) 26px;background:linear-gradient(110deg,var(--surface) 48%,#f0f4ed 100%)}
.launcher-copy{min-width:0}.launcher-eyebrow{display:flex;align-items:center;gap:9px;margin:0 0 16px;color:var(--accent);font-size:12px;font-weight:500}.eyebrow-icon{display:grid;place-items:center;width:30px;height:30px;border:1px solid #dbe6db;border-radius:9px;background:var(--accent-soft)}.eyebrow-divider{width:1px;height:12px;margin:0 2px;background:var(--line-strong)}
.cross-site-launcher h2{margin:0;font-family:var(--font-display);font-size:var(--trend-section-title-size,clamp(28px,2.5vw,34px));font-weight:600;line-height:1.45;letter-spacing:-.035em;text-wrap:balance}.launcher-description{max-width:630px;margin:14px 0 0;color:var(--muted);font-size:14px;line-height:1.9;text-wrap:pretty}
.launcher-actions{display:flex;align-items:center;flex-wrap:wrap;gap:14px 24px;margin-top:24px}.launcher-start{display:inline-flex;align-items:center;justify-content:center;gap:10px;min-height:48px;padding:13px 20px;border:1px solid var(--accent-strong);border-radius:10px;background:var(--accent-strong);color:var(--surface);font:inherit;font-size:13px;font-weight:500;box-shadow:0 5px 12px rgba(18,61,49,.12);cursor:pointer}.launcher-start:hover:not(:disabled){background:#1c5140;box-shadow:0 7px 16px rgba(18,61,49,.17);transform:translateY(-1px)}.launcher-start:disabled{opacity:.65;cursor:wait}.launcher-start>svg:last-child{margin-left:7px}
.launcher-favorites{display:inline-flex;align-items:center;justify-content:center;gap:8px;min-height:44px;color:var(--accent);font-size:13px;text-decoration:none}.launcher-favorites>svg:last-child{transition:transform 160ms ease}.launcher-favorites:hover>svg:last-child{transform:translateX(3px)}
.launcher-illustration{position:relative;min-width:0;padding:2px 0 0}.preview-browser{height:194px;border:1px solid #cad6cb;border-radius:13px;background:#edf2ea;box-shadow:0 9px 25px rgba(44,65,49,.06);overflow:hidden}.preview-toolbar{display:flex;align-items:center;gap:11px;height:31px;padding:0 13px;border-bottom:1px solid #d7e0d5;background:#fafbf6;color:#728070;font-size:10px}.window-dots{display:flex;gap:4px}.window-dots i{width:5px;height:5px;border-radius:50%;background:#c5d1c1}.preview-demo-label{margin-left:auto;color:#879383;font-size:9px}.preview-content{position:relative;height:162px}.preview-garment{position:absolute;left:20px;top:15px;width:119px;height:131px;padding:8px 10px 6px;border:1px solid #e2e5d9;border-radius:8px;background:#fffef9;box-shadow:0 4px 10px #30453108;transform:rotate(-7deg)}.preview-garment img{width:100%;height:97px;object-fit:contain;mix-blend-mode:multiply}.preview-garment>span{display:block;margin-top:2px;color:#788271;font-size:9px;text-align:center}
.preview-floating{position:absolute;right:14px;top:15px;width:155px;border:1px solid #b8c9b8;border-radius:8px;background:#fcfaf5;box-shadow:0 7px 14px rgba(42,71,49,.12);overflow:hidden}.preview-floating-header{display:flex;align-items:center;gap:5px;height:25px;padding:0 8px;background:var(--accent-strong);color:#f6f9f0;font-size:9px}.preview-window-controls{margin-left:auto;font-size:12px;letter-spacing:3px;opacity:.8}.preview-camera{display:grid;justify-items:center;gap:8px;padding:14px 8px 11px;color:#688064;background:linear-gradient(150deg,#f3f5eb,#e8eee2);font-size:9px}.preview-camera svg{stroke-width:1.3;opacity:.8}.preview-save{display:flex;align-items:center;justify-content:center;gap:5px;padding:7px 0;color:var(--accent);font-size:8px}.preview-drag{position:absolute;top:101px;left:105px;display:flex;align-items:center;gap:5px;padding:6px 9px;border:1px solid #d1dccb;border-radius:6px;background:#fffef8;color:var(--accent);font-size:9px;box-shadow:0 3px 8px #30453110;transform:rotate(3deg)}.illustration-caption{display:block;margin-top:10px;color:#7c8975;font-size:10px;text-align:center;letter-spacing:.04em}
.launcher-footer{display:flex;align-items:center;justify-content:space-between;flex-wrap:wrap;gap:14px 24px;padding:17px var(--trend-panel-padding,24px) 8px;border-top:1px solid #e4e8df}.launcher-flow{display:flex;align-items:center;flex-wrap:wrap;gap:13px;margin:0;padding:0;list-style:none;color:var(--muted);font-size:11px}.launcher-flow li{display:inline-flex;align-items:center;gap:6px}.launcher-flow li>svg{color:var(--accent)}.launcher-flow .flow-divider>svg{color:#adb9aa}
.launcher-tools{display:flex;align-items:center;flex-wrap:wrap;gap:13px}.launcher-tools a,.installation-help{display:inline-flex;align-items:center;gap:6px;min-height:32px;padding:0;color:var(--accent);font:inherit;font-size:11px;text-decoration:none;cursor:pointer}.installation-help{border:0;background:transparent}.launcher-tools a:hover,.installation-help:hover{text-decoration:underline;text-underline-offset:4px}.tools-divider{width:1px;height:12px;background:var(--line)}
.launcher-note{display:flex;align-items:flex-start;gap:7px;margin:0;padding:0 var(--trend-panel-padding,24px) 17px;color:var(--muted);font-size:11px;line-height:1.8}.launcher-note>svg{flex-shrink:0;margin-top:3px}.connection-note{color:var(--accent)}.connection-error{margin:0 var(--trend-panel-padding,24px) 16px;padding:10px 14px;border:1px solid #ead8ce;border-radius:8px;background:#fcf4ed;color:var(--danger)}
.launcher-start:focus-visible,.launcher-favorites:focus-visible,.launcher-tools a:focus-visible,.installation-help:focus-visible{outline:2px solid var(--accent);outline-offset:4px}.spinning{animation:spin 1s linear infinite}@keyframes spin{to{transform:rotate(360deg)}}
@media(max-width:1100px){.launcher-main{grid-template-columns:minmax(0,1fr) 280px;gap:28px;padding-block:28px}.preview-garment{left:13px;width:105px}.preview-floating{right:10px;width:142px}.preview-drag{left:82px}}
@media(max-width:800px){.launcher-main{grid-template-columns:1fr;padding-block:25px;gap:0}.launcher-illustration{display:none}.launcher-eyebrow{margin-bottom:12px}.launcher-description{margin-top:12px;font-size:13px}.launcher-actions{margin-top:20px;gap:12px 20px}.launcher-footer{padding-block:14px 7px;gap:9px}.launcher-note:not(.connection-error){padding-block:0 16px}.launcher-tools a,.installation-help{min-height:38px;font-size:12px}.launcher-flow{gap:10px;font-size:11px}}
@media(max-width:760px){.cross-site-launcher{border-radius:12px}}
@media(max-width:420px){.launcher-main{padding-block:22px}.launcher-eyebrow{font-size:10px;gap:7px}.cross-site-launcher h2{line-height:1.5}.launcher-actions{align-items:stretch;gap:8px}.launcher-start{width:100%;min-height:46px}.launcher-favorites{justify-content:flex-start;min-height:36px}.launcher-flow{gap:7px;font-size:10px}.launcher-flow li{gap:4px}.launcher-tools{gap:10px}.launcher-tools a,.installation-help{font-size:11px}}
@media(prefers-reduced-motion:reduce){.spinning{animation:none}.launcher-start,.launcher-favorites>svg:last-child{transition:none}.launcher-start:hover:not(:disabled),.launcher-favorites:hover>svg:last-child{transform:none}}
.cross-site-launcher h2 span{display:inline-block}@media(max-width:800px){.cross-site-launcher h2 span{display:block}}
</style>
