<script setup>
import OutfitAdjustment from './OutfitAdjustment.vue'
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import {
  Bookmark,
  Check,
  CloudSun,
  LoaderCircle,
  Send,
  Shirt,
  Sparkles,
  X
} from '@lucide/vue'
import { formatRecommendationReason, formatRecommendationTitle } from '../utils/recommendationReason'

const props = defineProps({
  app: { type: Object, required: true },
  open: { type: Boolean, default: false }
})

const emit = defineEmits(['update:open'])
const assistantOpen = computed({
  get: () => props.open,
  set: (value) => emit('update:open', value)
})
const assistantDraft = ref('')
const manualWeather = ref(false)
const manualTemperature = ref('')
const weatherRecoveryNeeded = ref(false)
const recoveryStep = ref('location')
const recoveryCity = ref('')
const recoveryMessage = ref('')
const cityWeatherFailed = ref(false)
const recoveredCity = ref('')
const recoveredDraft = ref('')
const recoveryVisible = computed(() => !currentWeather.value || weatherRecoveryNeeded.value)
watch(assistantDraft, value => {
  if (value.trim() && value.trim() !== recoveredDraft.value) recoveredCity.value = ''
})

function rememberRecoveredCity() {
  recoveredCity.value = state.value.recommendationForm.city
  recoveredDraft.value = assistantDraft.value.trim()
}

function useRecoveredWeather() {
  weatherRecoveryNeeded.value = false
  manualWeather.value = false
  manualTemperature.value = ''
  cityWeatherFailed.value = false
  recoveryMessage.value = ''
  recoveryStep.value = 'location'
}

async function locateWeather() {
  recoveryMessage.value = ''
  manualWeather.value = false
  cityWeatherFailed.value = false
  const weather = await props.app.loadLocalWeather({ fresh: true })
  if (!isComponentActive || state.value.authPhase !== 'authenticated') return
  if (weather) {
    useRecoveredWeather()
    rememberRecoveredCity()
  }
  else {
    recoveryMessage.value = state.value.error || '请允许浏览器定位，或选择城市获取天气。'
    recoveryStep.value = 'city'
  }
}

function chooseWeatherCity() {
  recoveryStep.value = 'city'
  recoveryCity.value = state.value.recommendationForm.city === '当前位置' ? '' : state.value.recommendationForm.city || ''
}

async function fetchCityWeather() {
  if (!recoveryCity.value.trim() || state.value.weatherLoading) return
  state.value.recommendationForm.city = recoveryCity.value.trim()
  rememberRecoveredCity()
  props.app.clearWeather()
  manualWeather.value = false
  const weather = await props.app.loadWeather()
  if (!isComponentActive || state.value.authPhase !== 'authenticated') return
  if (weather) {
    useRecoveredWeather()
    rememberRecoveredCity()
  }
  else {
    recoveryMessage.value = '城市天气仍不可用。可稍后重试，或临时手动填写温度继续。'
    cityWeatherFailed.value = true
  }
}
const assistantInput = ref(null)
const assistantScroll = ref(null)
const assistantDrawer = ref(null)
const brokenImages = ref(new Set())
let isComponentActive = true
let returnFocus = null
let backgroundShell = null
let backgroundWasInert = false
const assistantMessages = ref([
  {
    id: 'welcome',
    role: 'assistant',
    type: 'text',
    text: '请说明所在城市、出行场合与风格需求。推荐将结合当前天气、衣橱单品及已保存的个人资料，生成搭配方案。'
  }
])

const assistantPrompts = [
  { label: '日常通勤', prompt: '日常通勤，希望搭配简洁、得体' },
  { label: '周末出行', prompt: '周末出行，希望穿着舒适并具有层次感' },
  { label: '正式场合', prompt: '正式场合，希望搭配正式、简洁' },
  { label: '协调配色', prompt: '请使用衣橱单品搭配，保持配色协调' }
]

const knownCities = [
  '北京', '上海', '广州', '深圳', '杭州', '南京', '长沙', '成都', '武汉', '西安',
  '重庆', '苏州', '厦门', '青岛', '天津', '济南', '郑州', '合肥', '福州', '昆明',
  '大连', '宁波', '无锡', '东莞', '佛山', '南昌', '哈尔滨', '长春', '沈阳', '贵阳',
  '南宁', '海口', '乌鲁木齐', '拉萨', '兰州', '太原', '石家庄', '呼和浩特', '银川',
  '西宁', '香港', '澳门'
]

const state = computed(() => props.app.state || {})
const wardrobe = computed(() => state.value.wardrobe || [])
const wardrobeStats = computed(() => props.app.wardrobeStats || {})
const currentWeather = computed(() => state.value.weather || null)
watch(currentWeather, weather => { if (weather && weather.source !== 'user-provided') useRecoveredWeather() })
const currentCity = computed(() => currentWeather.value?.city || state.value.recommendationForm?.city || '城市未设置')
const weatherSummary = computed(() => {
  if (!currentWeather.value) return '天气待获取'
  const temperature = Number(currentWeather.value.temperatureC)
  const condition = props.app.weatherConditionLabel(currentWeather.value.weatherCode)
  return `${Number.isFinite(temperature) ? temperature.toFixed(0) : '--'}° · ${condition}`
})

const availableWardrobe = computed(() => wardrobe.value.filter((item) => Boolean(item?.id) && item.recognitionStatus !== 'NEEDS_MANUAL_REVIEW'))
const wardrobeById = computed(() => new Map(availableWardrobe.value.map((item) => [String(item.id), item])))

function recommendationItems(recommendation) {
  const seen = new Set()
  return (recommendation?.items || []).map((item) => wardrobeById.value.get(String(item?.id))).filter((item) => {
    if (!item || seen.has(String(item.id))) return false
    seen.add(String(item.id))
    return true
  }).slice(0, 4)
}

function itemKey(item) {
  return item?.id || item?.name || 'unknown'
}

function imageKey(prefix, item, index = 0) {
  return `${prefix}-${itemKey(item)}-${index}`
}

function imageSource(item, prefix, index = 0) {
  const key = imageKey(prefix, item, index)
  if (item?.imageUrl && !brokenImages.value.has(key)) return item.imageUrl
  return ''
}

function imageFailed(prefix, item, index = 0) {
  brokenImages.value = new Set([...brokenImages.value, imageKey(prefix, item, index)])
}

function nextMessageId(prefix = 'message') {
  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`
}

function appendMessage(message) {
  assistantMessages.value = [...assistantMessages.value, { id: nextMessageId(message.role), ...message }]
}

function extractCity(text) {
  return knownCities.find((city) => text.includes(city)) || ''
}

function inferOccasion(text) {
  const rules = [
    { pattern: /面试|客户|会议|上班|通勤/, value: '通勤' },
    { pattern: /约会|晚餐|见面/, value: '约会' },
    { pattern: /周末|旅行|出游|逛街|散步|户外/, value: '周末出行' },
    { pattern: /婚礼|宴会|正式场合/, value: '正式场合' }
  ]
  return rules.find((rule) => rule.pattern.test(text))?.value || ''
}

function composeStyleHint(text) {
  return text.trim().slice(0, 120)
}

function openAssistant(plan = null, prompt = '') {
  if (state.value.authPhase !== 'authenticated') return
  if (plan?.occasion) state.value.recommendationForm.occasion = plan.occasion
  if (plan?.palette) state.value.recommendationForm.styleHint = plan.palette
  if (plan?.city && plan.city !== '当前城市') state.value.recommendationForm.city = plan.city
  if (prompt) assistantDraft.value = prompt
  assistantOpen.value = true
}

function closeAssistant() {
  assistantOpen.value = false
}

async function submitAssistantPrompt(promptOverride = '') {
  if (state.value.generating || state.value.weatherLoading) return
  const text = String(promptOverride || assistantDraft.value).trim()
  if (!text) return

  if (manualWeather.value && (manualTemperature.value === '' || !Number.isFinite(Number(manualTemperature.value))
      || Number(manualTemperature.value) < -50 || Number(manualTemperature.value) > 60)) {
    appendMessage({ role: 'assistant', type: 'text', text: '请输入 -50 至 60℃ 的温度，或取消手动温度以获取实时天气。' })
    return
  }
  appendMessage({ role: 'user', type: 'text', text })
  assistantDraft.value = ''

  const form = state.value.recommendationForm || {}
  const city = (text === recoveredDraft.value ? recoveredCity.value : '') || extractCity(text) || String(form.city || '').trim() || currentWeather.value?.city || ''
  const occasion = inferOccasion(text) || String(form.occasion || '').trim() || '日常出行'

  if (!city) {
    assistantDraft.value = text
    weatherRecoveryNeeded.value = true
    recoveryStep.value = 'location'
    appendMessage({
      role: 'assistant',
      type: 'text',
      text: '请先开启定位获取当地天气，也可以选择城市。原需求已保留。'
    })
    return
  }

  const previousCity = String(form.city || '').trim()
  form.occasion = occasion
  form.city = city
  form.styleHint = composeStyleHint(text)
  if (previousCity && previousCity !== city) {
    props.app.clearWeather()
    manualWeather.value = false
    cityWeatherFailed.value = false
    recoveryStep.value = 'location'
  }

  const recommendation = await props.app.generateRecommendation(manualWeather.value ? { manualTemperatureC: Number(manualTemperature.value) } : {})
  if (!isComponentActive || state.value.authPhase !== 'authenticated') return
  if (recommendation) {
    if (!manualWeather.value) useRecoveredWeather()
    appendMessage({
      role: 'assistant',
      type: 'result',
      text: '搭配方案已生成，所选单品如下：',
      recommendation
    })
  } else {
    assistantDraft.value = text
    if (/天气/.test(state.value.error || '')) {
      weatherRecoveryNeeded.value = true
      if (!cityWeatherFailed.value) recoveryStep.value = 'location'
    }
    appendMessage({
      role: 'assistant',
      type: 'text',
      text: state.value.error || '搭配方案生成失败。请检查城市与衣橱信息后重试。'
    })
  }
}

async function saveAssistantRecommendation(message) {
  if (!message?.recommendation || message.recommendation.saved) return
  const saved = await props.app.saveRecommendation(message.recommendation)
  if (!saved || !isComponentActive || state.value.authPhase !== 'authenticated') return
  assistantMessages.value = assistantMessages.value.map((item) => (
    item.recommendation?.id === saved.id ? { ...item, recommendation: saved } : item
  ))
  appendMessage({ role: 'assistant', type: 'text', text: '搭配已收藏，可在搭配记录中查看。' })
}

function followUpPrompt(action) {
  return {
    change: '请使用不同配色生成搭配，保持当前场合要求',
    formal: '请提高搭配的正式程度，并兼顾穿着舒适度',
    lighter: '请减少穿着层次，优先选择轻薄、便于活动的单品'
  }[action] || '请根据当前需求重新生成搭配方案'
}

function handleResultAction(action, message) {
  if (action === 'save') return saveAssistantRecommendation(message)
  if (message?.recommendation) {
    state.value.recommendationForm.city = message.recommendation.city
    state.value.recommendationForm.occasion = message.recommendation.occasion
  }
  return submitAssistantPrompt(followUpPrompt(action))
}

function restoreBackground(restoreFocus = false) {
  document.body.classList.remove('assistant-is-open')
  if (backgroundShell) backgroundShell.inert = backgroundWasInert
  backgroundShell = null
  if (restoreFocus && returnFocus?.isConnected) returnFocus.focus({ preventScroll: true })
  returnFocus = null
}

function handleDialogKeydown(event) {
  if (event.key === 'Escape') {
    event.preventDefault()
    event.stopPropagation()
    closeAssistant()
    return
  }
  if (event.key !== 'Tab') return
  const controls = [...(assistantDrawer.value?.querySelectorAll('button:not(:disabled), textarea:not(:disabled), input:not(:disabled)') || [])]
    .filter((element) => element.getClientRects().length)
  const first = controls[0]
  const last = controls[controls.length - 1]
  if (!first) return event.preventDefault()
  if (!controls.includes(document.activeElement) || (event.shiftKey && document.activeElement === first)) {
    event.preventDefault()
    const target = event.shiftKey ? last : first
    target.focus()
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault()
    first.focus()
  }
}

watch(assistantOpen, async (isOpen) => {
  if (isOpen) {
    returnFocus = document.activeElement
    backgroundShell = document.querySelector('.app-shell')
    backgroundWasInert = backgroundShell?.inert || false
    if (backgroundShell) backgroundShell.inert = true
    document.body.classList.add('assistant-is-open')
    await nextTick()
    if (!isComponentActive || !assistantOpen.value) return
    if (assistantInput.value && !assistantInput.value.disabled) assistantInput.value.focus()
    else assistantDrawer.value?.querySelector('.assistant-close')?.focus()
    const conversation = assistantScroll.value
    conversation?.scrollTo({ top: conversation.scrollHeight, behavior: 'auto' })
  } else {
    restoreBackground(true)
  }
})

watch(assistantMessages, async () => {
  await nextTick()
  const element = assistantScroll.value
  if (element) element.scrollTo({ top: element.scrollHeight, behavior: 'smooth' })
}, { deep: true })

onBeforeUnmount(() => {
  isComponentActive = false
  restoreBackground()
})

defineExpose({ open: openAssistant })
</script>

<template>
  <Teleport to="body">
    <div v-if="assistantOpen" class="assistant-layer" @click.self="closeAssistant">
      <aside id="zhiji-assistant" ref="assistantDrawer" class="assistant-drawer" role="dialog" aria-modal="true" aria-labelledby="assistant-title" @keydown="handleDialogKeydown">
        <header class="assistant-drawer-header">
          <div class="assistant-drawer-heading">
            <div class="assistant-drawer-mark"><Sparkles :size="16" aria-hidden="true" />知己助手</div>
            <h2 id="assistant-title">知己 AI 助手</h2>
            <p>请说明城市、出行场合与风格偏好，推荐将从当前衣橱中选择单品。</p>
          </div>
          <button class="assistant-close" type="button" aria-label="关闭知己助手" title="关闭" @click="closeAssistant"><X :size="18" /></button>
        </header>

        <div class="assistant-drawer-context">
          <span>参考信息</span>
          <div>
            <b>{{ currentCity }}</b>
            <b>{{ weatherSummary }}</b>
            <b>{{ wardrobeStats.ready ? `${wardrobeStats.ready} 件可用衣物` : '衣橱待补充' }}</b>
            <b v-if="state.profile?.styleTags?.length">{{ state.profile.styleTags[0] }}</b>
          </div>
        </div>

        <div ref="assistantScroll" class="assistant-conversation" aria-live="polite">
          <div v-for="message in assistantMessages" :key="message.id" class="assistant-message" :class="`assistant-message-${message.role}`">
            <div v-if="message.role === 'assistant'" class="assistant-message-avatar" aria-hidden="true"><Sparkles :size="14" /></div>
            <div class="assistant-message-content">
              <p class="assistant-message-text">{{ message.text }}</p>
              <div v-if="message.type === 'result' && message.recommendation" class="assistant-result-card">
                <div class="assistant-result-meta">
                  <span><Sparkles :size="12" />{{ props.app.engineLabel(message.recommendation.engine) }}</span>
                  <small>{{ message.recommendation.occasion }} · {{ message.recommendation.city }}</small>
                </div>
                <h3>{{ formatRecommendationTitle(message.recommendation) }}</h3>
                <ul v-if="recommendationItems(message.recommendation).length" class="assistant-result-items" aria-label="推荐搭配中的衣物">
                  <li v-for="(item, itemIndex) in recommendationItems(message.recommendation)" :key="`${message.id}-${item.id || itemIndex}`">
                    <img v-if="imageSource(item, `assistant-${message.id}`, itemIndex)" :src="imageSource(item, `assistant-${message.id}`, itemIndex)" :alt="item.name || item.category || '搭配衣物'" @error="imageFailed(`assistant-${message.id}`, item, itemIndex)" />
                    <span v-else class="assistant-image-missing"><Shirt :size="14" aria-hidden="true" /><small>暂无图片</small></span>
                    <div><span>{{ item.category || '衣物' }} · {{ item.color || '待识别' }}</span><strong>{{ item.name || '衣橱衣物' }}</strong></div>
                  </li>
                </ul>
                <div v-else class="assistant-result-empty"><Shirt :size="16" />可用衣物不足，请补充并确认上装与下装，或连体装与配套单品。</div>
                <p v-if="message.recommendation.weather?.source === 'user-provided'">手动温度 {{ message.recommendation.temperatureC }}℃ · 非实时天气</p>
                <OutfitAdjustment :app="app" :recommendation="message.recommendation" @generated="recommendation => appendMessage({ role: 'assistant', type: 'result', text: '已按条件调整搭配。', recommendation })" />
                <p class="assistant-result-reason"><span>搭配依据</span>{{ formatRecommendationReason(message.recommendation) }}</p>
                <div class="assistant-result-actions">
                  <button type="button" @click="handleResultAction('change', message)">调整配色</button>
                  <button type="button" @click="handleResultAction('formal', message)">提高正式度</button>
                  <button type="button" @click="handleResultAction('lighter', message)">减少层次</button>
                  <button class="assistant-result-save" type="button" :disabled="message.recommendation.saved || props.app.state.saving" @click="handleResultAction('save', message)">
                    <Check v-if="message.recommendation.saved" :size="14" /><Bookmark v-else :size="14" />{{ message.recommendation.saved ? '已收藏' : '收藏搭配' }}
                  </button>
                </div>
              </div>
            </div>
          </div>
          <div v-if="props.app.state.generating" class="assistant-generating" role="status">
            <LoaderCircle class="spinning" :size="16" />正在生成搭配方案…
          </div>
        </div>

        <div class="assistant-intents">
          <span>常用需求</span>
          <div>
            <button v-for="item in assistantPrompts" :key="item.label" type="button" :disabled="props.app.state.generating" @click="submitAssistantPrompt(item.prompt)">{{ item.label }}</button>
          </div>
        </div>

        <form class="assistant-composer" @submit.prevent="submitAssistantPrompt()">
          <div v-if="recoveryVisible && !manualWeather" class="weather-recovery" aria-label="获取天气">
            <p>{{ state.weatherLoading ? '正在获取天气…' : '优先获取真实天气' }}</p>
            <button type="button" :disabled="state.weatherLoading || state.generating" @click="locateWeather">开启定位获取天气</button>
            <button v-if="recoveryStep === 'location'" type="button" :disabled="state.weatherLoading || state.generating" @click="chooseWeatherCity">不使用定位，选择城市</button>
            <template v-if="recoveryStep === 'city'">
              <label>城市<input v-model="recoveryCity" type="text" maxlength="80" aria-label="天气城市" placeholder="例如：长沙" :disabled="state.weatherLoading || state.generating" @input="cityWeatherFailed = false; manualWeather = false" @keydown.enter.prevent="fetchCityWeather" /></label>
              <button type="button" :disabled="!recoveryCity.trim() || state.weatherLoading || state.generating" @click="fetchCityWeather">获取城市天气</button>
            </template>
            <p v-if="recoveryMessage" role="status">{{ recoveryMessage }}</p>
          </div>
          <div v-if="recoveryVisible && cityWeatherFailed" class="manual-weather-control">
            <label><input v-model="manualWeather" type="checkbox" :disabled="state.generating" /> 临时手动填写温度（最后备用方式）</label>
            <label v-if="manualWeather">温度（℃）<input v-model="manualTemperature" type="number" min="-50" max="60" step="0.1" required :disabled="state.generating" aria-label="手动温度" /></label>
            <small v-if="manualWeather">仅使用你填写的温度，不推断晴雨和风速。</small>
          </div>
          <label class="sr-only" for="assistant-draft">穿搭需求</label>
          <textarea id="assistant-draft" ref="assistantInput" v-model="assistantDraft" maxlength="160" rows="2" :disabled="props.app.state.generating" placeholder="例如：长沙晚餐约会，希望简洁得体、穿着舒适" />
          <div class="assistant-composer-footer">
            <span><CloudSun :size="14" aria-hidden="true" />{{ props.app.state.generating ? '正在生成搭配方案…' : '参考天气、场合、衣橱与个人资料' }}</span>
            <button class="assistant-send" type="submit" :disabled="!assistantDraft.trim() || props.app.state.generating || props.app.state.weatherLoading" aria-label="生成穿搭推荐">
              <LoaderCircle v-if="props.app.state.generating" class="spinning" :size="17" />
              <Send v-else :size="17" aria-hidden="true" />
            </button>
          </div>
        </form>
      </aside>
    </div>
  </Teleport>
</template>

<style scoped>
.weather-recovery { display: flex; flex-wrap: wrap; gap: 8px; font-size: 12px; margin-bottom: 12px; }
.weather-recovery p { width: 100%; margin: 0; }
.weather-recovery button { padding: 6px 8px; border: 1px solid #c5c6bb; border-radius: 6px; background: transparent; color: inherit; }
.weather-recovery input { width: 120px; padding: 6px; margin-left: 8px; }
.manual-weather-control { display: grid; gap: 8px; margin-bottom: 12px; font-size: 12px; }
.manual-weather-control label { display: flex; align-items: center; gap: 8px; }
.manual-weather-control input[type="number"] { width: 100px; padding: 6px; }

.assistant-intents button,
.assistant-result-actions button {
  border: 1px solid var(--rec-line);
  padding: 5px 8px;
  color: var(--rec-ink);
  background: transparent;
  font-size: 10px;
}

.assistant-intents button:hover,
.assistant-result-actions button:hover {
  border-color: var(--rec-accent);
  color: var(--rec-accent);
}

.assistant-layer {
  --rec-bg: #f4f1eb;
  --rec-paper: #fbfaf6;
  --rec-paper-strong: #fffefa;
  --rec-ink: #202923;
  --rec-muted: #7a8179;
  --rec-line: #dddcd3;
  --rec-line-dark: #c3c7bd;
  --rec-accent: #788a6b;
  --rec-accent-soft: #e6ece2;
  --rec-dark: #202b24;
  position: fixed;
  z-index: 100;
  inset: 0;
  display: flex;
  justify-content: flex-end;
  background: rgba(32, 43, 36, .28);
}

.assistant-layer button {
  font-family: inherit;
}

.assistant-layer button:focus-visible {
  outline: 2px solid var(--rec-accent);
  outline-offset: 3px;
}

.assistant-drawer {
  display: grid;
  width: min(460px, 100vw);
  height: 100%;
  grid-template-rows: auto auto minmax(0, 1fr) auto auto;
  border-left: 1px solid var(--rec-line-dark);
  color: var(--rec-ink);
  background: var(--rec-paper-strong);
  box-shadow: -18px 0 46px rgba(32, 43, 36, .16);
  animation: assistant-drawer-in 260ms cubic-bezier(.22, .8, .32, 1) both;
}

.assistant-drawer-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  border-bottom: 1px solid var(--rec-line);
  padding: 26px 25px 21px;
}

.assistant-drawer-heading {
  min-width: 0;
}

.assistant-drawer-mark {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 10px;
  color: var(--rec-accent);
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: .06em;
}

.assistant-drawer-heading h2 {
  margin: 0;
  color: var(--rec-dark);
  font-family: var(--font-display);
  font-size: 30px;
  font-weight: 650;
  letter-spacing: -.045em;
  line-height: 1.08;
}

.assistant-drawer-heading p {
  max-width: 330px;
  margin: 10px 0 0;
  color: var(--rec-muted);
  font-size: 12px;
  line-height: 1.65;
}

.assistant-close {
  display: grid;
  width: 36px;
  height: 36px;
  flex: 0 0 auto;
  place-items: center;
  border: 1px solid var(--rec-line);
  border-radius: 50%;
  color: var(--rec-muted);
  background: transparent;
}

.assistant-close:hover {
  border-color: var(--rec-accent);
  color: var(--rec-accent);
}

.assistant-drawer-context {
  display: grid;
  gap: 7px;
  border-bottom: 1px solid var(--rec-line);
  padding: 13px 25px 14px;
  color: var(--rec-muted);
  font-size: 10px;
}

.assistant-drawer-context > div {
  display: flex;
  flex-wrap: wrap;
  gap: 7px 14px;
}

.assistant-drawer-context b {
  color: var(--rec-ink);
  font-size: 11px;
  font-weight: 700;
}

.assistant-conversation {
  min-height: 0;
  overflow: auto;
  padding: 22px 25px 17px;
  scrollbar-color: var(--rec-line-dark) var(--rec-paper-strong);
}

.assistant-message {
  display: flex;
  align-items: flex-start;
  gap: 9px;
  margin-bottom: 18px;
}

.assistant-message-user {
  justify-content: flex-end;
}

.assistant-message-avatar {
  display: grid;
  width: 27px;
  height: 27px;
  flex: 0 0 auto;
  place-items: center;
  border: 1px solid var(--rec-line-dark);
  border-radius: 50%;
  color: var(--rec-accent);
  background: var(--rec-accent-soft);
}

.assistant-message-content {
  min-width: 0;
  max-width: 88%;
}

.assistant-message-user .assistant-message-content {
  max-width: 82%;
}

.assistant-message-text {
  margin: 0;
  border: 1px solid var(--rec-line);
  border-radius: 3px 14px 14px 14px;
  padding: 10px 12px;
  color: var(--rec-ink);
  background: var(--rec-bg);
  font-size: 12px;
  line-height: 1.65;
}

.assistant-message-user .assistant-message-text {
  border-color: var(--rec-dark);
  color: var(--rec-paper-strong);
  background: var(--rec-dark);
  border-radius: 14px 3px 14px 14px;
}

.assistant-result-card {
  margin-top: 10px;
  border: 1px solid var(--rec-line-dark);
  padding: 14px;
  background: var(--rec-paper);
}

.assistant-result-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  color: var(--rec-muted);
  font-size: 9px;
}

.assistant-result-meta > span {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--rec-accent);
  font-weight: 700;
}

.assistant-result-meta small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.assistant-result-card h3 {
  margin: 13px 0 12px;
  color: var(--rec-dark);
  font-family: var(--font-display);
  font-size: 19px;
  font-weight: 650;
  letter-spacing: -.035em;
  line-height: 1.25;
}

.assistant-result-items {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 9px 12px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.assistant-result-items li {
  display: grid;
  grid-template-columns: 40px minmax(0, 1fr);
  gap: 8px;
  align-items: center;
  min-width: 0;
  border-bottom: 1px solid var(--rec-line);
  padding-bottom: 8px;
}

.assistant-result-items img {
  width: 40px;
  height: 46px;
  overflow: hidden;
  border-radius: 4px;
  object-fit: cover;
  background: var(--rec-bg);
}

.assistant-image-missing {
  display: grid;
  width: 40px;
  height: 46px;
  place-items: center;
  align-content: center;
  gap: 3px;
  border-radius: 4px;
  color: var(--rec-muted);
  background: var(--rec-bg);
  text-align: center;
}

.assistant-image-missing svg {
  color: var(--rec-accent);
}

.assistant-image-missing small {
  font-size: 8px;
}

.assistant-result-items li > div {
  display: grid;
  min-width: 0;
  gap: 2px;
}

.assistant-result-items span,
.assistant-result-items strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.assistant-result-items span {
  color: var(--rec-muted);
  font-size: 9px;
}

.assistant-result-items strong {
  color: var(--rec-ink);
  font-size: 11px;
  font-weight: 700;
}

.assistant-result-empty {
  display: flex;
  align-items: center;
  gap: 7px;
  border: 1px dashed var(--rec-line-dark);
  padding: 10px;
  color: var(--rec-muted);
  font-size: 10px;
}

.assistant-result-empty svg {
  color: var(--rec-accent);
}

.assistant-result-reason {
  margin: 12px 0 0;
  color: var(--rec-muted);
  font-size: 10px;
  line-height: 1.6;
}

.assistant-result-reason span {
  display: block;
  margin-bottom: 3px;
  color: var(--rec-accent);
  font-weight: 700;
}

.assistant-result-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 13px;
}

.assistant-result-actions button {
  min-height: 30px;
}

.assistant-result-actions .assistant-result-save {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  margin-left: auto;
  color: var(--rec-paper-strong);
  background: var(--rec-dark);
}

.assistant-result-actions .assistant-result-save:hover {
  color: var(--rec-paper-strong);
  background: var(--rec-accent);
}

.assistant-result-actions .assistant-result-save:disabled {
  color: var(--rec-accent);
  background: var(--rec-accent-soft);
}

.assistant-generating {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  color: var(--rec-accent);
  font-size: 11px;
}

.spinning {
  animation: assistant-spin 900ms linear infinite;
}

.assistant-intents {
  border-top: 1px solid var(--rec-line);
  padding: 12px 25px 11px;
  color: var(--rec-muted);
  font-size: 10px;
}

.assistant-intents > div {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 7px;
}

.assistant-composer {
  border-top: 1px solid var(--rec-line-dark);
  padding: 14px 25px 20px;
  background: var(--rec-paper);
}

.assistant-composer textarea {
  display: block;
  width: 100%;
  min-height: 70px;
  resize: vertical;
  border: 1px solid var(--rec-line);
  border-radius: 3px;
  outline: 0;
  padding: 11px 12px;
  color: var(--rec-ink);
  background: var(--rec-paper-strong);
  font: inherit;
  font-size: 12px;
  line-height: 1.6;
}

.assistant-composer textarea:focus {
  border-color: var(--rec-accent);
  box-shadow: 0 0 0 3px var(--rec-accent-soft);
}

.assistant-composer textarea::placeholder {
  color: #878e86;
}

.assistant-composer-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 9px;
}

.assistant-composer-footer > span {
  display: inline-flex;
  min-width: 0;
  align-items: center;
  gap: 5px;
  color: var(--rec-muted);
  font-size: 10px;
}

.assistant-composer-footer > span svg {
  flex: 0 0 auto;
  color: var(--rec-accent);
}

.assistant-send {
  display: grid;
  width: 38px;
  height: 38px;
  flex: 0 0 auto;
  place-items: center;
  border-radius: 50%;
  color: var(--rec-paper-strong);
  background: var(--rec-dark);
}

.assistant-send:hover:not(:disabled) {
  background: var(--rec-accent);
  transform: translateY(-1px);
}

.assistant-send:disabled {
  cursor: not-allowed;
  color: var(--rec-muted);
  background: var(--rec-line);
}

:global(body.assistant-is-open) {
  overflow: hidden;
}

@keyframes assistant-drawer-in {
  from { transform: translateX(24px); opacity: .4; }
  to { transform: translateX(0); opacity: 1; }
}

@keyframes assistant-drawer-up {
  from { transform: translateY(24px); opacity: .4; }
  to { transform: translateY(0); opacity: 1; }
}

@keyframes assistant-spin {
  to { transform: rotate(360deg); }
}

@media (max-width: 840px) {
  .assistant-layer {
    align-items: flex-end;
  }

  .assistant-drawer {
    width: 100%;
    height: min(88dvh, 760px);
    border-top: 1px solid var(--rec-line-dark);
    border-right: 0;
    border-bottom: 0;
    border-left: 0;
    border-radius: 18px 18px 0 0;
    animation-name: assistant-drawer-up;
  }

  .assistant-drawer-header {
    padding: 21px 20px 17px;
  }

  .assistant-drawer-context,
  .assistant-conversation,
  .assistant-intents,
  .assistant-composer {
    padding-right: 20px;
    padding-left: 20px;
  }

  .assistant-drawer-heading h2 {
    font-size: 27px;
  }
}

@media (max-width: 620px) {
  .assistant-result-items {
    grid-template-columns: 1fr;
  }

  .assistant-result-actions .assistant-result-save {
    margin-left: 0;
  }

  .assistant-message-content {
    max-width: calc(100% - 36px);
  }
}

@media (prefers-reduced-motion: reduce) {
  .assistant-drawer,
  .assistant-send {
    animation: none;
    transition: none;
  }
}
</style>
