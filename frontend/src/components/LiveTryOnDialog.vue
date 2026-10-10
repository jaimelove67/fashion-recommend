<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Camera, CircleAlert, Heart, LoaderCircle, RefreshCw, X } from '@lucide/vue'
import {
  ANYWEAR_ORIGIN, ANYWEAR_PRIVACY_URL, anywearFrameUrl,
  canTryOnGarment, isAnywearMessage, prepareTryOnImage
} from '../utils/liveTryOn.js'

const props = defineProps({
  items: { type: Array, required: true },
  initialItemId: { type: [Number, String], default: null },
  saveFavorite: { type: Function, default: null }
})
const emit = defineEmits(['close'])
const dialog = ref(null)
const frame = ref(null)
const selectedId = ref(props.initialItemId)
const items = computed(() => props.items.filter(canTryOnGarment))
const selected = computed(() => items.value.find(item => String(item.id) === String(selectedId.value)) || items.value[0])
const consentGiven = ref(false)
const stage = ref('idle')
const hasRendered = ref(false)
const error = ref('')
const favoriteState = ref('idle')
const favoriteError = ref('')
const frameSrc = ref('')
const frameGeneration = ref(0)
const secureContext = window.isSecureContext && Boolean(navigator.mediaDevices?.getUserMedia)
const statusText = computed(() => ({
  idle: '选择衣物，开启实时试穿',
  preparing: '正在准备衣物图片…',
  loading: '正在加载试衣服务…',
  connecting: '正在连接，请在试衣窗口中允许使用摄像头…',
  queued: '服务正在排队，请查看试衣窗口中的提示',
  streaming: '实时试穿中',
  ended: '本次试穿已结束',
  error: '暂时无法试穿'
}[stage.value]))
let requestVersion = 0
let favoriteVersion = 0
let imageRequest = null
let watchdog = null
let preparedImage = ''
let tryonId = ''
let previousFocus = null

function clearWatchdog() {
  clearTimeout(watchdog)
  watchdog = null
}

function stopSession({ preserveImage = false } = {}) {
  requestVersion += 1
  imageRequest?.abort()
  imageRequest = null
  clearWatchdog()
  frameSrc.value = ''
  if (!preserveImage) { preparedImage = ''; hasRendered.value = false }
  tryonId = ''
  error.value = ''
  stage.value = 'idle'
}

function fail(message) {
  stopSession()
  error.value = message
  stage.value = 'error'
}

function waitForService(timeout) {
  clearWatchdog()
  watchdog = setTimeout(() => fail('试衣服务未及时响应，请检查网络或摄像头权限后重试。'), timeout)
}

async function startSession() {
  if (!selected.value || !secureContext || favoriteState.value === 'saving') return
  stopSession()
  consentGiven.value = true
  const version = requestVersion
  const imageUrl = selected.value.imageUrl
  const controller = new AbortController()
  imageRequest = controller
  const imageTimeout = setTimeout(() => controller.abort(), 15000)
  stage.value = 'preparing'
  try {
    const image = await prepareTryOnImage(imageUrl, { origin: window.location.origin, signal: controller.signal })
    if (version !== requestVersion) return
    preparedImage = image
    tryonId = `tryon_${crypto.randomUUID()}`
    stage.value = 'loading'
    frameGeneration.value += 1
    frameSrc.value = anywearFrameUrl(window.location.hostname)
    waitForService(20000)
  } catch (cause) {
    if (version !== requestVersion) return
    fail(cause.name === 'AbortError' ? '衣物图片读取超时，请重试。' : cause.message || '试衣启动失败，请稍后重试。')
  } finally {
    clearTimeout(imageTimeout)
    if (imageRequest === controller) imageRequest = null
  }
}

function sendImage() {
  if (!frameSrc.value || !preparedImage || !frame.value?.contentWindow) return
  stage.value = 'connecting'
  waitForService(45000)
  const provider = frame.value.contentWindow
  provider.postMessage({ type: 'DECART_FULLSCREEN_MODE' }, ANYWEAR_ORIGIN)
  provider.postMessage({
    type: 'DECART_AUTO_START', productImageUrl: preparedImage, tryonId,
    imageSource: selected.value?.imageSource || 'trend_piece_selection', suggestedFacingMode: 'user',
    pageUrl: `${window.location.origin}/`, garmentCategoryRaw: selected.value?.category
  }, ANYWEAR_ORIGIN)
}

function receiveMessage(event) {
  if (!isAnywearMessage(event, frame.value?.contentWindow)) return
  const data = event.data
  if (['DECART_CAMERA_FLIP', 'DECART_RESTART_SAME_GARMENT'].includes(data.type)) {
    stage.value = 'loading'
    frameGeneration.value += 1
    waitForService(20000)
    return
  }
  if (data.type !== 'DECART_TRACK_EVENT') return
  if (data.properties?.tryon_id && data.properties.tryon_id !== tryonId) return
  if (data.event === 'queue_entered') {
    clearWatchdog()
    stage.value = 'queued'
  } else if (data.event === 'queue_completed') {
    stage.value = 'connecting'
    waitForService(45000)
  } else if (data.event === 'first_frame_rendered') {
    if (data.properties?.tryon_id !== tryonId) return
    clearWatchdog()
    hasRendered.value = true
    stage.value = 'streaming'
  } else if (data.event === 'session_ended') {
    stopSession({ preserveImage: true })
    stage.value = 'ended'
  } else if (data.event === 'first_frame_timeout') {
    fail('试穿画面未能生成，请检查摄像头权限，或稍后重试。')
  }
}

async function likeSelected() {
  if (!props.saveFavorite || !selected.value || !preparedImage || !hasRendered.value || favoriteState.value !== 'idle') return
  const version = favoriteVersion
  favoriteState.value = 'saving'; favoriteError.value = ''
  try {
    await props.saveFavorite(selected.value, preparedImage)
    if (version === favoriteVersion) favoriteState.value = 'saved'
  } catch (cause) {
    if (version === favoriteVersion) { favoriteState.value = 'idle'; favoriteError.value = cause.message }
  }
}

watch([() => selected.value?.id, () => selected.value?.imageUrl], () => {
  favoriteVersion += 1
  stopSession(); favoriteState.value = 'idle'; favoriteError.value = ''
})

onMounted(() => {
  previousFocus = document.activeElement
  window.addEventListener('message', receiveMessage)
  dialog.value.showModal()
})
onBeforeUnmount(() => {
  favoriteVersion += 1
  stopSession()
  window.removeEventListener('message', receiveMessage)
  dialog.value?.close()
  if (previousFocus?.isConnected) previousFocus.focus()
})
</script>

<template>
  <dialog ref="dialog" class="try-on-dialog" aria-labelledby="try-on-title" @cancel.prevent="emit('close')">
    <header class="try-on-header">
      <div><p>趋势单品 · 实时试穿</p><h2 id="try-on-title">实时试衣</h2></div>
      <button type="button" class="try-on-close" autofocus aria-label="关闭实时试衣" @click="emit('close')"><X :size="20" /></button>
    </header>
    <div class="try-on-layout">
      <aside class="try-on-selection" aria-label="选择试穿衣物">
        <label>试穿单品
          <select v-model="selectedId" :disabled="!items.length">
            <option v-for="item in items" :key="item.id" :value="item.id">{{ item.name }} · {{ item.category }}</option>
          </select>
        </label>
        <img v-if="selected" class="try-on-garment" :src="selected.imageUrl" :alt="selected.name" />
        <p v-if="selected?.imageNote" class="try-on-note">{{ selected.imageNote }}。效果仅代表所选图片的款式与配色。</p>
        <p v-if="!items.length">当前范围暂无可试穿单品。请选择有可用图片的上装、裤装或外套。</p>
        <div class="try-on-consent">
          <strong>使用摄像头查看上身效果</strong>
          <p>开启后，选中的衣物图片和摄像头实时画面将发送至 Anywear / Decart 生成试穿效果。</p>
          <a :href="ANYWEAR_PRIVACY_URL" target="_blank" rel="noopener noreferrer">查看服务方隐私政策</a>
        </div>
        <p v-if="!secureContext" class="try-on-error" role="alert">当前浏览器无法使用摄像头。请通过 HTTPS 或本机 localhost 打开项目，并使用支持摄像头的浏览器。</p>
        <button
          type="button" class="try-on-start" :disabled="!selected || !secureContext || favoriteState === 'saving' || ['preparing', 'loading', 'connecting', 'queued'].includes(stage)"
          @click="startSession"
        >
          <LoaderCircle v-if="['preparing', 'loading', 'connecting', 'queued'].includes(stage)" :size="17" class="spinning" />
          <RefreshCw v-else-if="consentGiven" :size="17" /><Camera v-else :size="17" />
          {{ !consentGiven ? '同意并开始试衣' : frameSrc ? '重新试穿此衣物' : '开始试穿此衣物' }}
        </button>
        <p class="try-on-note">试穿效果仅供款式与配色参考，无法确定尺码或真实合身程度。服务配额与排队情况以试衣窗口提示为准。</p>
        <button v-if="saveFavorite" type="button" class="try-on-like" :disabled="!hasRendered || favoriteState !== 'idle'" @click="likeSelected"><Heart :size="17" />{{ favoriteState === 'saved' ? '已保存到喜欢穿搭' : favoriteState === 'saving' ? '正在保存…' : '喜欢，保存到项目' }}</button>
        <p v-if="favoriteError" class="try-on-error" role="alert">{{ favoriteError }}</p>
      </aside>
      <section class="try-on-preview" aria-label="实时试衣画面">
        <p class="try-on-status" role="status" aria-live="polite">{{ statusText }}</p>
        <iframe
          v-if="frameSrc" :key="frameGeneration" ref="frame" :src="frameSrc" title="Anywear 实时试衣窗口"
          :allow="`camera ${ANYWEAR_ORIGIN}; fullscreen ${ANYWEAR_ORIGIN}`"
          sandbox="allow-scripts allow-same-origin" referrerpolicy="no-referrer" @load="sendImage"
        ></iframe>
        <div v-else class="try-on-placeholder">
          <CircleAlert v-if="error" :size="36" /><Camera v-else :size="40" />
          <strong>{{ error ? '试衣暂时不可用' : '在这里看到穿上衣物的自己' }}</strong>
          <p v-if="error" class="try-on-error" role="alert">{{ error }}</p>
          <p v-else>选择一件衣物并开始试穿，在服务窗口中允许使用摄像头。</p>
        </div>
      </section>
    </div>
  </dialog>
</template>

<style scoped>
.try-on-dialog { width: min(1040px, calc(100vw - 32px)); max-height: calc(100dvh - 32px); margin: auto; padding: 0; border: 1px solid var(--line); border-radius: 12px; color: var(--ink); background: var(--surface); overflow: auto; }
.try-on-dialog::backdrop { background: rgba(24, 28, 26, .55); }
.try-on-header { display: flex; align-items: center; justify-content: space-between; gap: 20px; padding: 20px 24px; border-bottom: 1px solid var(--line); }
.try-on-header p { margin: 0 0 6px; color: var(--muted); font-size: 12px; }
.try-on-header h2 { margin: 0; font-size: 24px; }
.try-on-close { display: grid; place-items: center; width: 36px; height: 36px; border: 1px solid var(--line); border-radius: 8px; background: transparent; color: inherit; cursor: pointer; }
.try-on-layout { display: grid; grid-template-columns: 280px minmax(0, 1fr); }
.try-on-selection { display: grid; align-content: start; gap: 16px; padding: 24px; border-right: 1px solid var(--line); }
.try-on-selection label { display: grid; gap: 8px; font-size: 13px; font-weight: 600; }
.try-on-selection select { min-width: 0; width: 100%; padding: 10px; border: 1px solid var(--line); border-radius: 6px; background: var(--surface); color: inherit; font: inherit; }
.try-on-garment { width: 100%; height: 180px; object-fit: contain; border-radius: 6px; background: var(--surface-alt, #f4f2ed); }
.try-on-consent strong { font-size: 13px; }
.try-on-consent p, .try-on-note, .try-on-placeholder p { margin: 8px 0; color: var(--muted); font-size: 12px; line-height: 1.8; }
.try-on-consent a { color: var(--accent); font-size: 12px; }
.try-on-start { display: flex; align-items: center; justify-content: center; gap: 8px; min-height: 44px; padding: 10px; border: 0; border-radius: 6px; background: var(--accent-strong); color: var(--surface); font: inherit; font-size: 13px; cursor: pointer; }
.try-on-start:disabled { opacity: .55; cursor: default; }
.try-on-like { display:flex; align-items:center; justify-content:center; gap:8px; min-height:44px; border:1px solid var(--line); border-radius:6px; padding:10px; color:var(--accent); background:var(--surface); font:inherit; font-size:13px; cursor:pointer; }
.try-on-like:disabled { opacity:.55; cursor:default; }
.try-on-preview { display: flex; flex-direction: column; min-width: 0; min-height: 520px; }
.try-on-status { margin: 0; padding: 12px 18px; border-bottom: 1px solid var(--line); color: var(--muted); font-size: 12px; }
.try-on-preview iframe { flex: 1; width: 100%; min-height: 480px; border: 0; background: #fff; }
.try-on-placeholder { display: flex; flex: 1; flex-direction: column; align-items: center; justify-content: center; gap: 14px; padding: 32px; text-align: center; color: var(--muted); }
.try-on-placeholder strong { color: var(--ink); font-size: 17px; }
.try-on-error { color: #aa3e35 !important; font-size: 13px; line-height: 1.8; }
@media (max-width: 700px) {
  .try-on-header { padding: 16px; }
  .try-on-layout { grid-template-columns: 1fr; }
  .try-on-selection { padding: 16px; gap: 12px; border-right: 0; border-bottom: 1px solid var(--line); }
  .try-on-garment { height: 100px; }
  .try-on-preview { min-height: 420px; }
  .try-on-preview iframe { min-height: 400px; }
}
</style>
