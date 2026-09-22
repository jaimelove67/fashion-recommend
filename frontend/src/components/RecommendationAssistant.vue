<script setup>
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import {
  ArrowRight,
  Bookmark,
  Check,
  CloudSun,
  LoaderCircle,
  MessageCircle,
  Send,
  Shirt,
  Sparkles,
  X
} from '@lucide/vue'

const props = defineProps({
  app: { type: Object, required: true },
  selectedLook: { type: Object, default: null },
  actualWeather: { type: Object, default: null },
  profileTags: { type: Array, default: () => [] }
})

const assistantOpen = ref(false)
const assistantDraft = ref('')
const assistantInput = ref(null)
const assistantScroll = ref(null)
const brokenImages = ref(new Set())
const assistantMessages = ref([
  {
    id: 'welcome',
    role: 'assistant',
    type: 'text',
    text: '告诉我今天要去哪里，或想呈现什么感觉。我会结合你的衣橱和天气，给你一套可以直接穿的搭配。'
  }
])

const assistantPrompts = [
  { label: '明天要通勤', prompt: '明天要通勤，想穿得精神一点' },
  { label: '周末出行', prompt: '周末要出门，想舒服又有层次' },
  { label: '想正式一点', prompt: '把这套调整得更正式一点' },
  { label: '这件怎么搭', prompt: '帮我把常穿的这件搭得更好看' }
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
const currentWeather = computed(() => props.actualWeather || state.value.weather || null)
const currentCity = computed(() => currentWeather.value?.city || state.value.recommendationForm?.city || '城市未设置')
const weatherSummary = computed(() => {
  if (!currentWeather.value) return '天气待获取'
  const temperature = Number(currentWeather.value.temperatureC)
  const condition = props.app.weatherConditionLabel(currentWeather.value.weatherCode)
  return `${Number.isFinite(temperature) ? temperature.toFixed(0) : '--'}° · ${condition}`
})

const contextItems = computed(() => [
  { label: '地点', value: currentCity.value },
  { label: '天气', value: weatherSummary.value },
  { label: '衣橱', value: wardrobeStats.value.ready ? `${wardrobeStats.value.ready} 件可用` : '还没有可用衣物' },
  { label: '风格', value: props.profileTags[0] || '还在了解你' }
])

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
  const previous = String(state.value.recommendationForm?.styleHint || '').trim()
  return [...new Set([previous, text.trim()].filter(Boolean))].join('；').slice(0, 120)
}

function openAssistant(plan = null, prompt = '') {
  if (plan?.occasion) state.value.recommendationForm.occasion = plan.occasion
  if (plan?.palette) state.value.recommendationForm.styleHint = plan.palette
  if (plan?.city && plan.city !== '当前城市') state.value.recommendationForm.city = plan.city
  assistantDraft.value = prompt
  assistantOpen.value = true
}

function closeAssistant() {
  assistantOpen.value = false
}

async function submitAssistantPrompt(promptOverride = '') {
  if (state.value.generating) return
  const text = String(promptOverride || assistantDraft.value).trim()
  if (!text) return

  appendMessage({ role: 'user', type: 'text', text })
  assistantDraft.value = ''

  const form = state.value.recommendationForm || {}
  const city = extractCity(text) || String(form.city || '').trim() || currentWeather.value?.city || ''
  const occasion = inferOccasion(text) || String(form.occasion || '').trim() || '日常出行'

  if (!city) {
    appendMessage({
      role: 'assistant',
      type: 'text',
      text: '我还不知道你所在的城市。告诉我城市名，我就能把当天的天气一起考虑进去。'
    })
    return
  }

  const previousCity = String(form.city || '').trim()
  form.occasion = occasion
  form.city = city
  form.styleHint = composeStyleHint(text)
  if (previousCity && previousCity !== city) props.app.clearWeather()

  const recommendation = await props.app.generateRecommendation()
  if (recommendation) {
    appendMessage({
      role: 'assistant',
      type: 'result',
      text: '我从你的衣橱里挑了一套，可以先看这套：',
      recommendation
    })
  } else {
    appendMessage({
      role: 'assistant',
      type: 'text',
      text: state.value.error || '这次没有生成成功。你可以换一个场合，或者先补充衣橱里的衣物信息。'
    })
  }
}

async function saveAssistantRecommendation(message) {
  if (!message?.recommendation || message.recommendation.saved) return
  const saved = await props.app.saveRecommendation(message.recommendation)
  if (!saved) return
  assistantMessages.value = assistantMessages.value.map((item) => (
    item.recommendation?.id === saved.id ? { ...item, recommendation: saved } : item
  ))
  appendMessage({ role: 'assistant', type: 'text', text: '已收藏。下次可以从历史搭配里继续调整。' })
}

function followUpPrompt(action) {
  return {
    change: '换一套不同配色，但保持适合今天的场合',
    formal: '再正式一点，保留舒适感',
    lighter: '少穿一层，优先轻薄和方便活动'
  }[action] || '换一套不同感觉的搭配'
}

function handleResultAction(action, message) {
  if (action === 'save') return saveAssistantRecommendation(message)
  return submitAssistantPrompt(followUpPrompt(action))
}

watch(assistantOpen, async (isOpen) => {
  if (isOpen) {
    document.body.classList.add('assistant-is-open')
    await nextTick()
    assistantInput.value?.focus()
  } else {
    document.body.classList.remove('assistant-is-open')
  }
})

watch(assistantMessages, async () => {
  await nextTick()
  const element = assistantScroll.value
  if (element) element.scrollTo({ top: element.scrollHeight, behavior: 'smooth' })
}, { deep: true })

onBeforeUnmount(() => document.body.classList.remove('assistant-is-open'))

defineExpose({ open: openAssistant })
</script>

<template>
  <div class="assistant-component">
    <article class="assistant-entry-panel">
      <header class="panel-header assistant-entry-header">
        <div>
          <div class="section-number">03 / AI STYLIST</div>
          <h2>让知己帮你搭</h2>
          <p>不用想好所有条件，把今天的场合和感觉告诉我。</p>
        </div>
        <span class="assistant-entry-state"><span aria-hidden="true"></span>随时可以问</span>
      </header>

      <div class="assistant-entry-body">
        <div class="assistant-context-line" aria-label="助手将参考的当前信息">
          <span v-for="item in contextItems" :key="item.label" class="assistant-context-chip">
            <small>{{ item.label }}</small>{{ item.value }}
          </span>
        </div>
        <button class="assistant-open-button" type="button" aria-haspopup="dialog" @click="openAssistant()">
          <span class="assistant-open-icon"><MessageCircle :size="18" aria-hidden="true" /></span>
          <span class="assistant-open-copy">
            <strong>例如：明天要见客户，想正式一点但不要太严肃</strong>
            <small>和知己聊一句，剩下的交给衣橱和天气</small>
          </span>
          <ArrowRight :size="17" aria-hidden="true" />
        </button>
        <div class="assistant-entry-prompts" aria-label="快捷提问">
          <span>可以直接问</span>
          <button v-for="item in assistantPrompts.slice(0, 3)" :key="item.label" type="button" @click="openAssistant(null, item.prompt)">
            {{ item.label }}
          </button>
        </div>
      </div>
    </article>
    <div v-if="assistantOpen" class="assistant-layer" @click.self="closeAssistant">
      <aside class="assistant-drawer" role="dialog" aria-modal="true" aria-labelledby="assistant-title" @keydown.esc.stop="closeAssistant">
        <header class="assistant-drawer-header">
          <div class="assistant-drawer-heading">
            <div class="assistant-drawer-mark"><Sparkles :size="16" aria-hidden="true" />知己助手</div>
            <h2 id="assistant-title">今天想怎么穿？</h2>
            <p>说你的目的地、场合或想要的感觉，我会从衣橱里找答案。</p>
          </div>
          <button class="assistant-close" type="button" aria-label="关闭知己助手" title="关闭" @click="closeAssistant"><X :size="18" /></button>
        </header>

        <div class="assistant-drawer-context">
          <span>正在参考</span>
          <div>
            <b>{{ currentCity }}</b>
            <b>{{ weatherSummary }}</b>
            <b>{{ wardrobeStats.ready ? `${wardrobeStats.ready} 件可用衣物` : '衣橱待补充' }}</b>
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
                <h3>{{ message.recommendation.summary }}</h3>
                <ul v-if="recommendationItems(message.recommendation).length" class="assistant-result-items" aria-label="推荐搭配中的衣物">
                  <li v-for="(item, itemIndex) in recommendationItems(message.recommendation)" :key="`${message.id}-${item.id || itemIndex}`">
                    <img v-if="imageSource(item, `assistant-${message.id}`, itemIndex)" :src="imageSource(item, `assistant-${message.id}`, itemIndex)" :alt="item.name || item.category || '搭配衣物'" @error="imageFailed(`assistant-${message.id}`, item, itemIndex)" />
                    <span v-else class="assistant-image-missing"><Shirt :size="14" aria-hidden="true" /><small>暂无图片</small></span>
                    <div><span>{{ item.category || '衣物' }} · {{ item.color || '待识别' }}</span><strong>{{ item.name || '衣橱衣物' }}</strong></div>
                  </li>
                </ul>
                <div v-else class="assistant-result-empty"><Shirt :size="16" />衣橱里还没有足够的可用衣物。</div>
                <p class="assistant-result-reason"><span>这样搭的原因</span>{{ message.recommendation.reason }}</p>
                <div class="assistant-result-actions">
                  <button type="button" @click="handleResultAction('change', message)">换一套</button>
                  <button type="button" @click="handleResultAction('formal', message)">更正式</button>
                  <button type="button" @click="handleResultAction('lighter', message)">少一层</button>
                  <button class="assistant-result-save" type="button" :disabled="message.recommendation.saved || props.app.state.saving" @click="handleResultAction('save', message)">
                    <Check v-if="message.recommendation.saved" :size="14" /><Bookmark v-else :size="14" />{{ message.recommendation.saved ? '已收藏' : '收藏这套' }}
                  </button>
                </div>
              </div>
            </div>
          </div>
          <div v-if="props.app.state.generating" class="assistant-generating" role="status">
            <LoaderCircle class="spinning" :size="16" />正在从衣橱里挑选搭配…
          </div>
        </div>

        <div class="assistant-intents">
          <span>试试这些说法</span>
          <div>
            <button v-for="item in assistantPrompts" :key="item.label" type="button" :disabled="props.app.state.generating" @click="submitAssistantPrompt(item.prompt)">{{ item.label }}</button>
          </div>
        </div>

        <form class="assistant-composer" @submit.prevent="submitAssistantPrompt()">
          <label class="sr-only" for="assistant-draft">告诉知己你的穿搭需求</label>
          <textarea id="assistant-draft" ref="assistantInput" v-model="assistantDraft" maxlength="160" rows="2" :disabled="props.app.state.generating" placeholder="例如：周五晚餐，想舒服一点但看起来精神" />
          <div class="assistant-composer-footer">
            <span><CloudSun :size="14" aria-hidden="true" />{{ props.app.state.generating ? '正在整理你的衣橱' : '会参考天气、场合和个人风格' }}</span>
            <button class="assistant-send" type="submit" :disabled="!assistantDraft.trim() || props.app.state.generating" aria-label="发送给知己">
              <LoaderCircle v-if="props.app.state.generating" class="spinning" :size="17" />
              <Send v-else :size="17" aria-hidden="true" />
            </button>
          </div>
        </form>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.assistant-component {
  min-width: 0;
}

.assistant-entry-panel {
  position: relative;
  min-width: 0;
  border-top: 1px solid var(--rec-ink);
  padding-top: 24px;
}

.assistant-entry-header {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  align-items: flex-start;
}

.assistant-entry-header .section-number {
  margin-bottom: 12px;
  color: var(--rec-accent);
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: .08em;
  text-transform: uppercase;
}

.assistant-entry-header h2 {
  margin-bottom: 8px;
  color: var(--rec-dark);
  font-family: var(--font-display);
  font-size: 24px;
  font-weight: 650;
  letter-spacing: -.045em;
  line-height: 1.1;
}

.assistant-entry-header p {
  max-width: 430px;
  margin: 0;
  color: var(--rec-muted);
  font-size: 11px;
  line-height: 1.6;
}

.assistant-entry-state {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding-top: 4px;
  color: var(--rec-muted);
  font-size: 10px;
  white-space: nowrap;
}

.assistant-entry-state > span {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--rec-accent);
  box-shadow: 0 0 0 4px var(--rec-accent-soft);
}

.assistant-entry-body {
  margin-top: 25px;
}

.assistant-context-line {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.assistant-context-chip {
  display: inline-flex;
  align-items: baseline;
  gap: 6px;
  border: 1px solid var(--rec-line);
  border-radius: 999px;
  padding: 6px 9px;
  color: var(--rec-ink);
  background: var(--rec-paper);
  font-size: 11px;
}

.assistant-context-chip small {
  color: var(--rec-muted);
  font-size: 9px;
}

.assistant-open-button {
  display: flex;
  width: 100%;
  min-height: 72px;
  align-items: center;
  gap: 12px;
  margin-top: 15px;
  border: 1px solid var(--rec-line-dark);
  padding: 13px 15px;
  color: var(--rec-ink);
  background: var(--rec-paper);
  text-align: left;
}

.assistant-open-button:hover {
  border-color: var(--rec-accent);
  background: var(--rec-accent-soft);
}

.assistant-open-icon {
  display: grid;
  width: 34px;
  height: 34px;
  flex: 0 0 auto;
  place-items: center;
  border-radius: 50%;
  color: var(--rec-paper-strong);
  background: var(--rec-dark);
}

.assistant-open-copy {
  display: grid;
  min-width: 0;
  flex: 1;
  gap: 4px;
}

.assistant-open-copy strong {
  overflow: hidden;
  color: var(--rec-dark);
  font-size: 12px;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.assistant-open-copy small {
  color: var(--rec-muted);
  font-size: 10px;
}

.assistant-open-button > svg {
  flex: 0 0 auto;
  color: var(--rec-accent);
}

.assistant-entry-prompts {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-top: 13px;
  color: var(--rec-muted);
  font-size: 10px;
}

.assistant-entry-prompts button,
.assistant-intents button,
.assistant-result-actions button {
  border: 1px solid var(--rec-line);
  padding: 5px 8px;
  color: var(--rec-ink);
  background: transparent;
  font-size: 10px;
}

.assistant-entry-prompts button:hover,
.assistant-intents button:hover,
.assistant-result-actions button:hover {
  border-color: var(--rec-accent);
  color: var(--rec-accent);
}

.assistant-layer {
  position: fixed;
  z-index: 100;
  inset: 0;
  display: flex;
  justify-content: flex-end;
  background: rgba(32, 43, 36, .28);
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
  .assistant-entry-header {
    gap: 12px;
  }

  .assistant-entry-state {
    padding-top: 2px;
  }

  .assistant-open-copy strong {
    overflow: visible;
    white-space: normal;
  }

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
