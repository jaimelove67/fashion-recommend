<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ArrowRight, Check, CheckCircle2, ChevronLeft, Copy, Download, FolderOpen, LoaderCircle, Puzzle, X } from '@lucide/vue'

const props = defineProps({
  initialStep: { type: Number, default: 0 },
  username: { type: String, required: true },
  busy: { type: Boolean, default: false },
  connected: { type: Boolean, default: false },
  error: { type: String, default: '' }
})
const emit = defineEmits(['close', 'connect'])
const steps = ['下载', '解压', '安装', '连接', '使用']
const dialog = ref(null)
const step = ref(Math.max(0, Math.min(3, props.initialStep)))
const browser = ref(/Edg\//.test(navigator.userAgent) ? 'edge' : 'chrome')
const addressInput = ref(null)
const copyState = ref('')
const extensionAddress = computed(() => `${browser.value}://extensions`)
const nextLabel = computed(() => ['已下载，下一步', '已解压，下一步', '已加载扩展，下一步'][step.value])

watch(browser, () => { copyState.value = '' })
watch(() => props.connected, connected => { if (connected) step.value = 4 })

async function copyAddress() {
  try {
    await navigator.clipboard.writeText(extensionAddress.value)
    copyState.value = 'copied'
  } catch {
    addressInput.value?.focus()
    addressInput.value?.select()
    copyState.value = 'manual'
  }
}

function closeOnBackdrop(event) {
  if (event.target !== dialog.value) return
  const rect = dialog.value.getBoundingClientRect()
  if (event.clientX < rect.left || event.clientX > rect.right || event.clientY < rect.top || event.clientY > rect.bottom) emit('close')
}

onMounted(() => dialog.value.showModal())
onBeforeUnmount(() => dialog.value?.close())
</script>

<template>
  <dialog ref="dialog" class="try-on-setup-dialog" aria-labelledby="try-on-setup-title" @cancel.prevent="emit('close')" @click="closeOnBackdrop">
    <header class="setup-header">
      <div class="setup-heading"><span class="setup-heading-icon" aria-hidden="true"><Puzzle :size="20" /></span><div><p class="setup-eyebrow">跨站试穿 · 首次设置</p><h2 id="try-on-setup-title">几步准备，开启试穿浮窗</h2></div></div>
      <button type="button" class="setup-close" aria-label="关闭安装引导" autofocus @click="emit('close')"><X :size="20" /></button>
    </header>

    <ol class="setup-progress" aria-label="安装进度">
      <li v-for="(label, index) in steps" :key="label" :class="{ current: step === index, completed: step > index }" :aria-current="step === index ? 'step' : undefined">
        <span class="step-number"><Check v-if="step > index" :size="14" /><template v-else>{{ index + 1 }}</template></span><span>{{ label }}</span>
      </li>
    </ol>

    <div class="setup-content" aria-live="polite">
      <section v-if="step === 0" aria-labelledby="setup-download-title">
        <p class="step-caption">第 1 步 / 共 5 步</p><h3 id="setup-download-title">下载扩展压缩包</h3>
        <p>配套扩展会在购物网页中显示试穿浮窗，并将喜欢的单品保存回你的项目账号。</p>
        <div class="setup-download-card"><span class="download-file-icon" aria-hidden="true"><FolderOpen :size="25" /></span><div class="download-file-details"><strong>知己 · 跨站试穿</strong><span>ZIP 压缩包 · Chrome / Edge</span></div><a class="setup-primary download-action" href="/downloads/zhiji-try-on-extension.zip" download><Download :size="16" />下载扩展 ZIP</a></div>
        <p class="setup-note">等待浏览器下载完成，再点击“已下载，下一步”。如果已经下载过，可以直接继续。</p>
        <button type="button" class="setup-text-button" @click="step = 3">我已安装扩展，直接连接项目<ArrowRight :size="15" /></button>
      </section>

      <section v-else-if="step === 1" aria-labelledby="setup-extract-title">
        <p class="step-caption">第 2 步 / 共 5 步</p><h3 id="setup-extract-title">解压下载的文件</h3>
        <p>打开电脑的下载文件夹，找到 <code>zhiji-try-on-extension.zip</code>，右键选择“全部解压”。</p>
        <div class="setup-file-flow" aria-label="将压缩包解压为扩展文件夹">
          <div><Download :size="26" /><strong>ZIP 压缩包</strong><span>浏览器下载的文件</span></div>
          <ArrowRight :size="20" />
          <div><FolderOpen :size="26" /><strong>解压后的文件夹</strong><span>里面有 manifest.json</span></div>
        </div>
        <p class="setup-note">解压完成后，打开文件夹确认能看到 <code>manifest.json</code>。保留这个文件夹，下一步会从这里加载扩展。</p>
      </section>

      <section v-else-if="step === 2" aria-labelledby="setup-install-title">
        <p class="step-caption">第 3 步 / 共 5 步</p><h3 id="setup-install-title">在浏览器中安装扩展</h3>
        <fieldset class="browser-choice"><legend>选择你使用的浏览器</legend><label :class="{ selected: browser === 'chrome' }"><input v-model="browser" type="radio" value="chrome" />Chrome</label><label :class="{ selected: browser === 'edge' }"><input v-model="browser" type="radio" value="edge" />Edge</label></fieldset>
        <div class="extension-address"><input ref="addressInput" :value="extensionAddress" readonly aria-label="扩展管理页地址" /><button type="button" class="setup-secondary" @click="copyAddress"><Check v-if="copyState === 'copied'" :size="15" /><Copy v-else :size="15" />{{ copyState === 'copied' ? '已复制' : '复制地址' }}</button></div>
        <p v-if="copyState === 'manual'" class="setup-note" role="status">请手动复制已选中的地址，粘贴到浏览器地址栏打开。</p>
        <ol class="setup-instructions"><li>复制上方地址，粘贴到浏览器地址栏，打开扩展管理页。</li><li>打开“开发者模式”，点击“{{ browser === 'edge' ? '加载解压缩的扩展' : '加载已解压的扩展程序' }}”。</li><li>选择刚才解压后包含 <code>manifest.json</code> 的文件夹。</li></ol>
        <p class="setup-note">看到“知己 · 跨站试穿”的扩展卡片后，回到本项目继续。此扩展适用于桌面版 Chrome / Edge。</p>
      </section>

      <section v-else-if="step === 3" aria-labelledby="setup-connect-title">
        <p class="step-caption">第 4 步 / 共 5 步</p><h3 id="setup-connect-title">连接当前项目网站</h3>
        <p>保持本项目页面打开，在浏览器右上角的扩展菜单（拼图图标）中找到“知己 · 跨站试穿”。</p>
        <div class="setup-connection-card"><Puzzle :size="26" /><div><strong>知己 · 跨站试穿</strong><span>点击“连接此项目网站”，批准本站权限</span></div></div>
        <p>完成后，点击下方“连接并开启试穿”，检查连接并开启浮窗。</p>
        <p v-if="error" class="setup-error" role="alert">{{ error }}</p>
      </section>

      <section v-else aria-labelledby="setup-ready-title">
        <div class="setup-success-icon"><CheckCircle2 :size="34" /></div>
        <p class="step-caption">第 5 步 / 共 5 步</p><h3 id="setup-ready-title">已连接，可以去购物网站试穿了</h3>
        <p>当前连接账号：<strong>{{ username }}</strong>。请保留本项目标签页。</p>
        <ol class="setup-instructions"><li>打开 HTTPS 购物网站，点击浏览器工具栏中的“知己 · 跨站试穿”。</li><li>首次在该网站使用时，选择“在本站启用试穿浮窗”并授权。</li><li>拖入商品图片并同意开始。在合一窗口中允许摄像头、查看效果并收藏。</li><li>点击窗口顶部“置顶显示”，浏览其他网页时可继续向该窗口拖入商品图片。</li></ol>
        <p class="setup-note">保存的单品可在项目的“喜欢穿搭”中查看。</p>
      </section>
    </div>

    <footer class="setup-footer">
      <button v-if="step > 0 && step < 4" type="button" class="setup-secondary" :disabled="busy" @click="step -= 1"><ChevronLeft :size="16" />上一步</button>
      <span v-else class="footer-note">{{ step === 4 ? '准备完成，祝你找到喜欢的款式' : '下载与解压完成后，由你确认进入下一步' }}</span>
      <button v-if="step < 3" type="button" class="setup-primary" @click="step += 1">{{ nextLabel }}<ArrowRight :size="16" /></button>
      <button v-else-if="step === 3" type="button" class="setup-primary" data-zhiji-cross-site :disabled="busy" @click="emit('connect')"><LoaderCircle v-if="busy" :size="16" class="spinning" />{{ busy ? '正在连接…' : '连接并开启试穿' }}</button>
      <button v-else type="button" class="setup-primary" @click="emit('close')">完成<Check :size="16" /></button>
    </footer>
  </dialog>
</template>

<style scoped>
.try-on-setup-dialog{width:min(760px,calc(100vw - 32px));max-height:calc(100dvh - 32px);margin:auto;padding:0;border:1px solid #d3ded0;border-radius:20px;background:var(--surface);color:var(--ink);overflow:auto;box-shadow:0 28px 100px rgba(22,44,30,.2)}
.try-on-setup-dialog::backdrop{background:rgba(24,35,28,.46);backdrop-filter:blur(3px)}
.setup-header{display:flex;align-items:start;justify-content:space-between;gap:20px;padding:28px 32px 24px;border-bottom:1px solid #e3e8df;background:linear-gradient(115deg,var(--surface),#f0f4ec)}
.setup-heading{display:flex;align-items:center;gap:14px;min-width:0}.setup-heading-icon{display:grid;place-items:center;flex-shrink:0;width:44px;height:44px;border:1px solid #d6e2d0;border-radius:13px;background:#e4eddf;color:var(--accent)}
.setup-eyebrow{margin:0 0 8px;color:var(--accent);font-size:12px;font-weight:600}.setup-header h2{margin:0;font-family:var(--font-display);font-size:27px;font-weight:600;line-height:1.4}
.setup-close{display:grid;place-items:center;flex-shrink:0;width:34px;height:34px;border:1px solid var(--line);border-radius:10px;background:var(--surface);color:var(--muted);cursor:pointer}.setup-close:hover{border-color:var(--line-strong);background:var(--accent-soft)}
.setup-progress{display:flex;justify-content:space-between;gap:0;margin:0;padding:22px 30px 19px;border-bottom:1px solid #e5e9df;list-style:none;background:#f5f6ef}
.setup-progress li{position:relative;isolation:isolate;display:flex;flex:1;flex-direction:column;align-items:center;gap:8px;color:var(--muted);font-size:11px}.setup-progress li:not(:last-child)::after{position:absolute;top:14px;left:calc(50% + 22px);width:calc(100% - 44px);height:1px;background:#d7dfd2;content:"";z-index:-1}.setup-progress .completed::after{background:#9cb698}
.step-number{display:grid;place-items:center;width:29px;height:29px;border:1px solid #d6ddd0;border-radius:50%;background:var(--surface);font-size:11px;font-weight:600}
.setup-progress .current{color:var(--accent-strong);font-weight:600}.current .step-number,.completed .step-number{border-color:var(--accent-strong);background:var(--accent-strong);color:var(--surface)}
.setup-content{padding:30px 32px;min-height:285px}.step-caption{margin:0 0 10px!important;color:var(--accent)!important;font-size:11px!important;font-weight:600;letter-spacing:.025em}.setup-content h3{margin:0 0 14px;font-size:22px;line-height:1.5;font-weight:600}
.setup-content p{margin:12px 0;color:var(--muted);font-size:14px;line-height:1.9}.setup-content strong{color:var(--ink)}.setup-content code{font-family:monospace;font-size:.9em;overflow-wrap:anywhere}.setup-content .setup-note{font-size:12px;line-height:1.8}
.setup-primary,.setup-secondary{display:inline-flex;align-items:center;justify-content:center;gap:8px;min-height:44px;border-radius:9px;padding:10px 16px;font:inherit;font-size:13px;font-weight:500;text-decoration:none;cursor:pointer}.setup-primary{border:1px solid var(--accent-strong);background:var(--accent-strong);color:var(--surface);box-shadow:0 3px 8px rgba(18,61,49,.08)}.setup-primary:hover:not(:disabled){background:#1c5140}.setup-secondary{border:1px solid var(--line);background:var(--surface);color:var(--ink)}.setup-secondary:hover:not(:disabled){background:var(--accent-soft);border-color:var(--line-strong)}
.setup-download-card{display:flex;align-items:center;gap:14px;margin:22px 0 16px;padding:18px;border:1px solid #dbe3d3;border-radius:12px;background:#f1f5ea}.download-file-icon{display:grid;place-items:center;flex-shrink:0;width:46px;height:46px;border:1px solid #d9e4ce;border-radius:11px;background:#e4edda;color:var(--accent)}.download-file-details{display:grid;flex:1;min-width:0;gap:6px}.download-file-details strong{font-size:13px}.download-file-details span{color:var(--muted);font-size:11px}.setup-download-card .download-action{flex-shrink:0;margin:0}
.setup-primary:disabled,.setup-secondary:disabled{opacity:.6;cursor:wait}.download-action{margin:8px 0}.setup-text-button{display:flex;align-items:center;gap:7px;margin-top:22px;padding:0;border:0;background:transparent;color:var(--accent);font:inherit;font-size:13px;cursor:pointer}
.setup-file-flow{display:flex;align-items:center;justify-content:center;gap:24px;margin:22px 0;padding:22px;border:1px solid #e1e6d9;border-radius:12px;background:#f5f6ef}.setup-file-flow>div{display:grid;justify-items:center;gap:10px;text-align:center;font-size:13px}.setup-file-flow svg{color:var(--accent)}.setup-file-flow span{color:var(--muted);font-size:12px}
.browser-choice{display:flex;gap:10px;margin:18px 0 14px;padding:0;border:0;font-size:13px}.browser-choice legend{margin-bottom:10px;color:var(--muted);font-size:12px}.browser-choice label{display:flex;align-items:center;gap:8px;min-height:40px;padding:9px 18px;border:1px solid var(--line);border-radius:8px;background:var(--surface);cursor:pointer}.browser-choice label.selected{border-color:#a5bba0;background:#eaf1e2;color:var(--accent)}.browser-choice input{margin:0;accent-color:var(--accent-strong)}
.extension-address{display:flex;align-items:center;gap:10px}.extension-address input{min-width:0;flex:1;width:100%;padding:12px;border:1px solid var(--line);border-radius:8px;background:var(--surface-alt);color:var(--ink);font:13px monospace}.extension-address button{flex-shrink:0}
.setup-instructions{margin:20px 0 0;padding-left:22px;color:var(--muted);font-size:13px;line-height:1.9}.setup-instructions li+li{margin-top:8px}
.setup-connection-card{display:flex;align-items:center;gap:14px;margin:20px 0;padding:20px;border:1px solid #d8e2cf;border-radius:12px;background:#eff4e9;color:var(--accent)}.setup-connection-card div{display:grid;gap:8px}.setup-connection-card strong{font-size:14px}.setup-connection-card span{color:var(--muted);font-size:12px}
.setup-content .setup-error{margin-top:18px;padding:12px 14px;border-left:3px solid var(--danger);background:var(--surface-alt);color:var(--danger);font-size:12px}.setup-success-icon{color:var(--accent);margin-bottom:14px}
.setup-footer{position:sticky;bottom:0;display:flex;align-items:center;justify-content:space-between;gap:16px;padding:20px 32px;border-top:1px solid #e1e6db;background:var(--surface);box-shadow:0 -5px 15px #fcfaf54d}.footer-note{color:var(--muted);font-size:12px;line-height:1.6}.spinning{animation:spin 1s linear infinite}@keyframes spin{to{transform:rotate(360deg)}}
@media(max-width:600px){.setup-header{padding:20px}.setup-header h2{font-size:23px}.setup-progress{gap:6px;padding:18px 14px}.setup-progress li{flex-direction:column;gap:7px;font-size:11px}.setup-content{padding:22px 20px;min-height:0}.setup-content h3{font-size:20px}.setup-file-flow{gap:15px;padding:18px 10px}.setup-footer{padding:18px 20px;gap:12px}.footer-note{max-width:130px;font-size:11px}.setup-primary,.setup-secondary{font-size:12px;padding:10px 12px}}
@media(prefers-reduced-motion:reduce){.spinning{animation:none}}
@media(max-width:600px){.setup-heading{gap:10px}.setup-heading-icon{width:34px;height:34px;border-radius:10px}.setup-heading-icon svg{width:17px;height:17px}.setup-header h2{font-size:21px}.setup-progress{gap:0}.setup-progress li:not(:last-child)::after{left:calc(50% + 18px);width:calc(100% - 36px)}.setup-download-card{flex-wrap:wrap;padding:16px;gap:12px}.setup-download-card .download-action{width:100%;margin-top:2px}.download-file-icon{width:39px;height:39px}.setup-text-button{font-size:12px}.setup-header{padding-right:16px}.setup-eyebrow{font-size:11px}}
.setup-primary,.setup-secondary{white-space:nowrap}@media(max-width:380px){.extension-address{flex-wrap:wrap}.extension-address input{flex-basis:100%}.extension-address button{width:100%}.setup-footer{gap:10px;padding:16px}}
</style>
