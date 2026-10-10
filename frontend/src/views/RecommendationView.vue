<script setup>
import OutfitAdjustment from '../components/OutfitAdjustment.vue'
import RecommendationFeedback from '../components/RecommendationFeedback.vue'
import { computed, ref, watch } from 'vue'
import {
  ArrowRight,
  Bookmark,
  Check,
  CloudSun,
  Flame,
  LoaderCircle,
  MessageCircle,
  Shirt,
  Sparkles,
  Star,
  SunMedium,
  Umbrella,
  Wind
} from '@lucide/vue'
import { formatRecommendationReason, formatRecommendationTitle } from '../utils/recommendationReason'
import { outfitModelReferenceKey } from '../utils/outfitModel'

const props = defineProps({
  app: { type: Object, required: true }
})

const emit = defineEmits(['open-assistant'])

const brokenImages = ref(new Set())
const visualResults = ref({})
const visualBusyKeys = ref(new Set())
const rationaleSection = ref(null)
const today = new Date()
const weekdayNames = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']

const dailyLookMode = {
  title: '穿搭方案',
  subtitle: '衣橱预览',
  palette: '待搭配',
  note: '生成穿搭推荐后，可查看单品组合与搭配依据。',
}

const state = computed(() => props.app.state || {})
const wardrobe = computed(() => state.value.wardrobe || [])
const history = computed(() => state.value.history || [])
const profile = computed(() => state.value.profile || null)
const actualWeather = computed(() => state.value.weather || null)
const supportedModelGenders = new Set(['MALE', 'FEMALE'])
const modelGender = computed(() => {
  const value = String(profile.value?.gender || '').trim().toUpperCase()
  return supportedModelGenders.has(value) ? value : ''
})
const modelGenderLabel = computed(() => ({ MALE: '男', FEMALE: '女' }[modelGender.value] || '待补充'))
const modelDisplayLabel = computed(() => profile.value?.usePersonalPhotoForOutfit ? '本人形象' : modelGenderLabel.value + '模特')

function isWardrobeItemUsable(item) {
  return Boolean(item?.id) && item.recognitionStatus !== 'NEEDS_MANUAL_REVIEW'
}

const availableWardrobe = computed(() => wardrobe.value.filter(isWardrobeItemUsable))
const wardrobeById = computed(() => new Map(availableWardrobe.value.map((item) => [String(item.id), item])))

function wardrobeItemsOnly(items) {
  const picked = new Set()
  return (Array.isArray(items) ? items : []).map((item) => wardrobeById.value.get(String(item?.id))).filter((item) => {
    if (!item || picked.has(String(item.id))) return false
    picked.add(String(item.id))
    return true
  })
}

const currentRecommendationItems = computed(() => wardrobeItemsOnly(state.value.currentRecommendation?.items))

const recommendationPool = computed(() => {
  const candidates = []
  if (state.value.currentRecommendation) candidates.push(state.value.currentRecommendation)
  candidates.push(...history.value)
  const unique = new Map()
  for (const item of candidates) {
    if (item?.id && !unique.has(item.id)) unique.set(item.id, item)
  }
  return [...unique.values()]
})

const generatedRecommendation = computed(() => {
  const source = recommendationPool.value[0]
  return source?.id && hasCombinableOutfit(wardrobeItemsOnly(source.items)) ? source : null
})
// Keep a restored outfit's explanation, weather and occasion from the same saved record.
const outfitWeather = computed(() => generatedRecommendation.value?.weather || actualWeather.value)

function temperatureLabel(value) {
  return typeof value === 'number' && Number.isFinite(value) ? `${value.toFixed(0)}°` : '--°'
}

function itemKey(item) {
  return item?.id || item?.name || 'unknown'
}

function wardrobeCategoryLabel(category) {
  const value = String(category || '')
  if (/连体|连衣裙/.test(value)) return '连体装'
  if (value.includes('外套')) return '外套'
  if (value.includes('上装')) return '上装'
  if (value.includes('下装')) return '下装'
  if (value.includes('鞋')) return '鞋履'
  if (value.includes('配饰')) return '配饰'
  return '衣物'
}

function categoryMatches(item, category) {
  return canonicalWardrobeCategory(item?.category) === category
}

function canonicalWardrobeCategory(category) {
  const value = String(category || '').trim()
  if (/连体|连衣裙/.test(value)) return '连体装'
  for (const canonical of ['外套', '上装', '下装', '鞋履', '配饰']) {
    if (value.includes(canonical) || (canonical === '鞋履' && value.includes('鞋'))) return canonical
  }
  return value
}

function hasCombinableOutfit(items) {
  return items.length >= 2
    && (items.some(item => /连体|连衣裙/.test(item.category))
      || (items.some(item => categoryMatches(item, '上装')) && items.some(item => categoryMatches(item, '下装'))))
}

function itemsForLook(dayIndex) {
  const categoryOrder = availableWardrobe.value.some(item => /连体|连衣裙/.test(item.category))
    ? ['连体装', '鞋履', '外套', '配饰'] : ['上装', '下装', '鞋履', '外套']
  const picked = []
  const pickedKeys = new Set()
  for (const category of categoryOrder) {
    const candidates = availableWardrobe.value.filter((item) => categoryMatches(item, category) && !pickedKeys.has(itemKey(item)))
    const candidate = candidates.length
      ? candidates[((dayIndex % candidates.length) + candidates.length) % candidates.length]
      : null
    if (candidate) {
      picked.push(candidate)
      pickedKeys.add(itemKey(candidate))
    }
  }
  return hasCombinableOutfit(picked) ? picked.slice(0, 4) : []
}

function dateSlug(date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

function dateLabel(date) {
  return `${date.getMonth() + 1}.${String(date.getDate()).padStart(2, '0')}`
}

function uniqueColors(items) {
  return [...new Set(items.map((item) => item?.color).filter(Boolean))].slice(0, 2)
}

const dailyDate = computed(() => {
  const savedDate = generatedRecommendation.value?.generatedAt
  return savedDate && Number.isFinite(new Date(savedDate).getTime()) ? new Date(savedDate) : today
})
const dailyDateKey = computed(() => dateSlug(dailyDate.value))
const dailyDateLabel = computed(() => dateLabel(dailyDate.value))
const dailyDayName = computed(() => weekdayNames[dailyDate.value.getDay()])
const dailyDateHeading = computed(() => `${dailyDate.value.getMonth() + 1}月${dailyDate.value.getDate()}日 · ${dailyDayName.value}`)
const dailyWeather = computed(() => {
  const weather = outfitWeather.value
  return {
    label: weather?.source === 'user-provided' ? '手动温度 · 非实时' : weather?.weatherCode != null ? props.app.weatherConditionLabel(weather.weatherCode) : '天气待获取',
    temperature: temperatureLabel(weather?.temperatureC),
    low: temperatureLabel(weather?.apparentTemperatureC)
  }
})

const weatherBriefCity = computed(() => {
  return actualWeather.value?.city || state.value.recommendationForm?.city || '城市待设置'
})

const weatherBriefIcon = computed(() => {
  const code = actualWeather.value?.weatherCode
  if (code === 0 || code === 1) return SunMedium
  if (code >= 51 && code <= 67 || code >= 80) return Umbrella
  return CloudSun
})

const dailyLooks = computed(() => {
  const source = generatedRecommendation.value
  const sourceItems = wardrobeItemsOnly(source?.items)
  const isGenerated = Boolean(source)
  const items = isGenerated ? sourceItems.slice(0, 4) : itemsForLook(0)
  const mode = dailyLookMode
  const palette = uniqueColors(items).join(' / ') || mode.palette
  const occasion = source?.occasion || state.value.recommendationForm?.occasion || '待选择场合'
  const visualKey = isGenerated ? visualKeyForSource(source, modelGender.value, sourceItems) : ''
  const visual = visualKey ? visualResults.value[visualKey] : null
  const generatedImage = visual?.imageUrl || ''
  return [{
    id: 'ootd-' + dailyDateKey.value,
    modelImage: generatedImage,
    modelImageKind: generatedImage ? 'generated' : 'missing',
    modelGender: modelGender.value,
    modelGenderLabel: modelGenderLabel.value,
    modelDisplayLabel: modelDisplayLabel.value,
    visualKey,
    visualStatus: visual?.status || 'idle',
    visualMessage: visual?.message || '',
    lookLabel: source ? '已生成搭配' : '衣橱预览',
    title: occasion + ' · ' + mode.title,
    subtitle: source ? formatRecommendationTitle(source) : mode.subtitle,
    occasion,
    palette,
    note: source?.reason || mode.note,
    items,
    source,
    weatherLabel: dailyWeather.value.label,
    temperature: dailyWeather.value.temperature,
    low: dailyWeather.value.low,
    city: source?.city || outfitWeather.value?.city || state.value.recommendationForm?.city || '当前城市',
    dayName: dailyDayName.value,
    dateLabel: dailyDateLabel.value,
    isToday: dailyDateKey.value === dateSlug(today),
    isGenerated,
    isWardrobePreview: !isGenerated && hasCombinableOutfit(items)
  }]
})

const selectedLook = computed(() => dailyLooks.value[0] || null)
const selectedLookReason = computed(() => formatRecommendationReason(selectedLook.value?.source))
const currentRecommendationReason = computed(() => formatRecommendationReason(state.value.currentRecommendation))
const wardrobeLookCount = computed(() => dailyLooks.value.filter((look) => look.items.length).length)
const generatedLookCount = computed(() => dailyLooks.value.filter((look) => look.isGenerated).length)
const hasWardrobeLooks = computed(() => wardrobeLookCount.value > 0)
const modelSelectionHint = computed(() => modelGender.value ? `${modelDisplayLabel.value} · AI 穿搭效果图` : '需设置模特性别')
const coveragePercent = computed(() => {
  const total = props.app.wardrobeStats.total
  if (!total) return null
  return Math.min(100, Math.round((props.app.recommendationStats.coveredItems / total) * 100))
})

const profileTags = computed(() => {
  const tags = profile.value?.styleTags || []
  return tags.length ? tags.slice(0, 3) : ['偏好待完善']
})

const selectedLookRationale = computed(() => {
  const look = selectedLook.value
  if (!look) {
    return [
      { label: '适用场合', value: '待选择' },
      { label: '搭配配色', value: '待搭配' },
      { label: '衣橱来源', value: '当前没有可用衣物' }
    ]
  }
  return [
    { label: '适用场合', value: look.source?.occasion || state.value.recommendationForm?.occasion || '待选择' },
    { label: '搭配配色', value: look.palette },
    { label: '衣橱来源', value: look.items.length ? `${look.items.length} 件已确认衣物` : '当前没有可用衣物' }
  ]
})

function visualKeyForSource(source, gender, items) {
  const itemIds = (items || []).map((item) => item?.id).filter(Boolean).join(',')
  return `${source?.id || ''}:${gender || ''}:${outfitModelReferenceKey(profile.value)}:${state.value.outfitModelRevision || 0}:${itemIds}`
}

function visualKeyForLook(look) {
  return look?.visualKey || visualKeyForSource(look?.source, look?.modelGender, look?.items)
}

const selectedVisual = computed(() => {
  const key = visualKeyForLook(selectedLook.value)
  return key ? visualResults.value[key] || null : null
})
const selectedVisualBusy = computed(() => visualBusyKeys.value.has(visualKeyForLook(selectedLook.value)))

async function requestLookVisual(look = selectedLook.value, force = false) {
  if (!look?.source?.id || !modelGender.value || typeof props.app.generateRecommendationVisual !== 'function') return null
  const key = visualKeyForLook(look)
  if (!key || (!force && visualResults.value[key] && visualResults.value[key].status !== 'PENDING')) return visualResults.value[key] || null
  if (visualBusyKeys.value.has(key)) return null
  visualResults.value = {
    ...visualResults.value,
    [key]: { status: 'PENDING', imageUrl: null, model: null, modelGender: modelGender.value, message: '正在生成穿搭效果图…' }
  }
  visualBusyKeys.value = new Set([...visualBusyKeys.value, key])
  try {
    const result = await props.app.generateRecommendationVisual(look.source, { force })
    if (result && key === visualKeyForLook(selectedLook.value)) {
      visualResults.value = { ...visualResults.value, [key]: result }
    } else {
      const next = { ...visualResults.value }
      delete next[key]
      visualResults.value = next
    }
    return result
  } finally {
    const next = new Set(visualBusyKeys.value)
    next.delete(key)
    visualBusyKeys.value = next
  }
}

const heroCondition = computed(() => actualWeather.value?.weatherCode != null
  ? props.app.weatherConditionLabel(actualWeather.value.weatherCode) : '天气待获取')
const heroTemperature = computed(() => temperatureLabel(actualWeather.value?.temperatureC))

function formatDate(value) {
  if (!value) return '暂无时间'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '暂无时间'
  return new Intl.DateTimeFormat('zh-CN', {
    month: 'numeric',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  }).format(date)
}

function imageKey(prefix, item, index = 0) {
  return `${prefix}-${itemKey(item)}-${index}`
}

function imageSource(item, prefix, index = 0) {
  const key = imageKey(prefix, item, index)
  if (item?.imageUrl && !brokenImages.value.has(key)) return item.imageUrl
  return ''
}

function modelImageSource(look) {
  if (!look?.modelImage) return ''
  const key = imageKey('model', { id: look.visualKey || look.modelGender || 'unset' })
  return brokenImages.value.has(key) ? '' : look.modelImage
}

function modelImageFailed(look) {
  brokenImages.value = new Set([...brokenImages.value, imageKey('model', { id: look?.visualKey || look?.modelGender || 'unset' })])
}

function imageFailed(prefix, item, index = 0) {
  brokenImages.value = new Set([...brokenImages.value, imageKey(prefix, item, index)])
}

function openAssistant(plan = null, prompt = '') {
  emit('open-assistant', plan, prompt)
}

function showDailyLook() {
  document.getElementById('ootd-title')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

function showRecommendationReason() {
  const section = rationaleSection.value
  if (!section) return
  section.scrollIntoView({ behavior: 'smooth', block: 'center' })
  section.focus({ preventScroll: true })
}

watch(() => selectedLook.value?.visualKey, () => {
  const look = selectedLook.value
  if (look?.isGenerated && modelGender.value) requestLookVisual(look)
}, { immediate: true })
</script>

<template>
  <section class="recommendation-view">
    <div class="recommendation-canvas">
      <header class="recommendation-hero">
        <div class="hero-overline">
          <span><Sparkles :size="14" aria-hidden="true" />今日搭配</span>
          <span class="hero-overline-date">{{ dailyDateHeading }}</span>
        </div>

        <div class="hero-grid">
          <div class="hero-copy">
            <div class="hero-index"><span>01</span><span>个性化穿搭方案</span></div>
            <h1 v-if="selectedLook?.isGenerated">搭配方案已生成。</h1>
            <h1 v-else-if="hasWardrobeLooks">衣橱已就绪，创建穿搭方案。</h1>
            <h1 v-else>完善衣橱，开始穿搭推荐。</h1>
            <p v-if="selectedLook?.isGenerated" class="hero-lead">查看搭配方案与单品组合，推荐综合参考天气、场合、个人偏好及当前衣橱。</p>
            <p v-else-if="hasWardrobeLooks" class="hero-lead">当前展示衣橱单品预览，可通过穿搭助手设置场合与风格需求，生成搭配方案。</p>
            <p v-else class="hero-lead">请添加并确认上装与下装，或连体装与配套单品，再通过穿搭助手生成搭配方案。</p>
            <div class="hero-actions">
              <button class="hero-primary" type="button" @click="showDailyLook">
                <Sparkles :size="17" aria-hidden="true" />查看搭配方案<ArrowRight :size="17" aria-hidden="true" />
              </button>
            </div>
            <div class="hero-footnote">
              <span v-for="tag in profileTags" :key="tag">#{{ tag }}</span>
              <span class="hero-footnote-model">效果图：{{ modelSelectionHint }}</span>
              <span class="hero-footnote-muted">{{ profile?.displayName ? `${profile.displayName}的今日搭配` : '基于个人衣橱' }}</span>
            </div>
          </div>

          <aside class="weather-brief" aria-label="当地天气概览">
            <div class="weather-brief-top">
              <div>
                <span class="brief-label">当地天气</span>
                <strong>{{ heroCondition }}</strong>
              </div>
              <component :is="weatherBriefIcon" :size="24" aria-hidden="true" />
            </div>
            <div class="weather-reading">
              <strong>{{ heroTemperature }}</strong>
              <div>
                <span>{{ weatherBriefCity }}</span>
              </div>
            </div>
          </aside>
        </div>
      </header>

      <div v-if="state.selectedTrendReference" class="trend-reference-banner" role="status" tabindex="-1">
        <div><strong>已选择穿搭参考</strong><p>{{ state.selectedTrendReference.title }}</p><small>推荐参考所选穿搭风格，单品仍从个人衣橱中选择。</small></div>
        <button type="button" @click="openAssistant(null, '请参考已选择的穿搭风格，使用我的衣橱单品生成搭配方案')">设置需求并生成</button>
        <button type="button" @click="props.app.clearTrendReference()">取消参考</button>
      </div>
      <section class="overview-strip" aria-label="今日搭配概览">
        <div class="overview-item">
          <span>{{ hasWardrobeLooks ? '搭配方案' : '衣橱待完善' }}</span>
          <strong>{{ hasWardrobeLooks ? wardrobeLookCount : 0 }} <small>套</small></strong>
          <em>{{ hasWardrobeLooks ? (generatedLookCount ? `${generatedLookCount} 套已生成方案` : '当前衣橱预览') : '先添加可用衣物' }}</em>
        </div>
        <div class="overview-item">
          <span>可用衣物</span>
          <strong>{{ props.app.wardrobeStats.ready }} <small>件</small></strong>
          <em>{{ props.app.wardrobeStats.review ? `${props.app.wardrobeStats.review} 件衣物待补充` : (props.app.wardrobeStats.ready ? '衣物信息齐全' : '至少添加两类衣物') }}</em>
        </div>
        <div class="overview-item overview-item-accent">
          <span>风格偏好</span>
          <strong class="overview-style-value">{{ profileTags[0] || '待建立' }}</strong>
          <em>{{ profileTags.slice(1).join(' · ') || '可在形象档案中完善偏好' }}</em>
        </div>
        <div class="overview-source">
          <span>推荐依据</span>
          <strong>天气 · 场合 · 衣橱</strong>
          <p>结合已保存的形象资料与个人偏好。</p>
        </div>
      </section>

      <section class="ootd-section" aria-labelledby="ootd-title">
        <header class="section-header ootd-header">
          <div>
            <div class="section-number">02 · 搭配</div>
            <h2 id="ootd-title">搭配方案</h2>
            <p>{{ generatedRecommendation ? '生成于 ' : '' }}{{ dailyDateHeading }} · {{ dailyWeather.label }} {{ dailyWeather.temperature }} · {{ hasWardrobeLooks ? `${wardrobeLookCount} 套方案 · 单品来自当前衣橱` : '需补充可用衣物' }}</p>
          </div>
          <button class="ootd-today" type="button" @click="app.selectView('history')">查看搭配记录</button>
        </header>

        <OutfitAdjustment v-if="generatedRecommendation" :app="app" :recommendation="generatedRecommendation" />
        <div v-if="selectedLook" class="ootd-board-wrap">
          <div class="ootd-board" :aria-label="`${selectedLook.title}的穿搭方案展示`">
            <div class="ootd-board-heading">
              <span>{{ selectedLook.lookLabel }} / {{ selectedLook.dateLabel }}</span>
              <strong>{{ selectedLook.weatherLabel }} {{ selectedLook.temperature }} · {{ selectedLook.occasion }}</strong>
            </div>

            <div class="ootd-board-model" :class="{ 'is-empty': !modelImageSource(selectedLook) }">
              <div v-if="modelImageSource(selectedLook)" class="ootd-board-model-visual">
                <img
                  :src="modelImageSource(selectedLook)"
                  :alt="`${selectedLook.modelDisplayLabel}穿搭效果图`"
                  draggable="false"
                  @error="modelImageFailed(selectedLook)"
                />
                <span v-if="selectedVisualBusy" class="ootd-board-model-loading"><LoaderCircle :size="15" class="spinning" />正在生成穿搭效果图…</span>
              </div>
              <div v-else-if="selectedLook.items.length" class="ootd-model-empty">
                <Shirt :size="30" aria-hidden="true" />
                <template v-if="!selectedLook.modelGender">
                  <strong>请设置模特性别</strong>
                  <span>设置模特性别后，可生成对应的穿搭效果图。</span>
                  <button class="ootd-visual-action" type="button" @click="props.app.selectView('profile')">选择模特性别</button>
                </template>
                <template v-else-if="!selectedLook.isGenerated">
                  <strong>请先生成搭配方案</strong>
                  <span>当前为衣橱预览。生成搭配方案后，可继续生成穿搭效果图。</span>
                  <button class="ootd-visual-action" type="button" @click="openAssistant(selectedLook)">生成搭配方案</button>
                </template>
                <template v-else>
                  <strong>{{ selectedVisualBusy ? '正在生成穿搭效果图…' : '暂无穿搭效果图' }}</strong>
                  <span>{{ selectedVisual?.message || '穿搭效果图生成后将在此展示，仅供搭配参考。' }}</span>
                </template>
              </div>
            </div>

            <article
              v-for="(item, itemIndex) in selectedLook.items"
              :key="`${itemKey(item)}-${itemIndex}`"
              class="ootd-board-item"
              :class="`board-item-${itemIndex + 1}`"
            >
              <div class="ootd-board-item-image">
                <img
                  v-if="imageSource(item, `ootd-board-${dailyDateKey}`, itemIndex)"
                  :src="imageSource(item, `ootd-board-${dailyDateKey}`, itemIndex)"
                  :alt="item.name || item.category || '衣橱单品'"
                  @error="imageFailed(`ootd-board-${dailyDateKey}`, item, itemIndex)"
                />
                <span v-else class="ootd-image-missing"><Shirt :size="19" aria-hidden="true" /><small>暂无图片</small></span>
              </div>
              <footer>
                <div>
                  <strong>{{ wardrobeCategoryLabel(item.category) }}</strong>
                  <small>{{ String(itemIndex + 1).padStart(2, '0') }} / {{ item.name || item.style || '衣橱单品' }}</small>
                </div>
                <span>衣橱已有</span>
              </footer>
            </article>

            <div v-if="!selectedLook.items.length" class="ootd-board-empty" role="status">
              <Shirt :size="24" aria-hidden="true" />
              <strong>暂无可组合的衣橱单品</strong>
              <span>请添加并确认上装与下装，或连体装与配套单品，以生成完整搭配。</span>
              <button class="ootd-empty-action" type="button" @click="props.app.selectView('wardrobe')">完善衣橱信息</button>
            </div>
          </div>

          <div class="ootd-board-caption">
            <div class="ootd-board-status" aria-live="polite">
              <span>
                {{ !selectedLook.items.length
                  ? '生成搭配需上装与下装，或连体装与配套单品'
                  : selectedLook.modelGenderLabel === '待补充'
                    ? '请在个人形象档案中设置模特性别'
                    : selectedLook.modelImageKind === 'generated'
                    ? `已生成${selectedLook.modelDisplayLabel}穿搭效果图`
                    : !selectedLook.isGenerated
                      ? '请先生成搭配方案，再生成穿搭效果图'
                      : selectedVisualBusy
                        ? '正在生成穿搭效果图…'
                        : (selectedVisual?.message || '穿搭效果图尚未生成') }}
              </span>
              <button
                v-if="selectedLook.isGenerated && selectedLook.modelGender"
                type="button"
                class="ootd-visual-action"
                :disabled="selectedVisualBusy"
                @click="requestLookVisual(selectedLook, true)"
              >
                <LoaderCircle v-if="selectedVisualBusy" :size="14" class="spinning" />
                <Sparkles v-else :size="14" />
                {{ selectedVisualBusy ? '生成中' : selectedLook.modelImageKind === 'generated' ? '重新生成效果图' : '生成穿搭效果图' }}
              </button>
            </div>
          </div>

          <section class="ootd-rationale" aria-labelledby="ootd-rationale-title">
            <div class="ootd-rationale-heading"><span>搭配依据</span><strong id="ootd-rationale-title">搭配信息</strong></div>
            <dl>
              <div v-for="fact in selectedLookRationale" :key="fact.label">
                <dt>{{ fact.label }}</dt>
                <dd>{{ fact.value }}</dd>
              </div>
            </dl>
            <div v-if="selectedLook.isGenerated && selectedLookReason" ref="rationaleSection" class="ootd-reason" tabindex="-1" aria-label="搭配理由">
              <p>{{ selectedLookReason }}</p>
            </div>
          </section>
        </div>
        <div v-else class="ootd-board-empty ootd-board-empty-page">
          <Shirt :size="24" aria-hidden="true" />
          <strong>暂无搭配方案</strong>
          <span>请添加衣橱单品，再生成穿搭推荐。</span>
        </div>
      </section>

      <section class="workbench-grid" aria-label="衣橱使用情况">
        <aside class="closet-panel">
          <header class="panel-header compact">
            <div>
              <div class="section-number">03 · 衣橱</div>
              <h2>衣橱使用情况</h2>
            </div>
            <Shirt :size="19" aria-hidden="true" />
          </header>
          <div class="closet-score">
            <strong>{{ coveragePercent === null ? '--' : `${coveragePercent}%` }}</strong>
            <span>衣物参与推荐的比例</span>
          </div>
          <div class="closet-progress" role="progressbar" :aria-valuenow="coveragePercent || 0" aria-valuemin="0" aria-valuemax="100" aria-label="衣橱参与推荐的比例"><span :style="{ transform: `scaleX(${(coveragePercent || 0) / 100})` }"></span></div>
          <div class="closet-facts">
            <div><span>衣橱单品总数</span><strong>{{ props.app.wardrobeStats.total }}</strong></div>
            <div><span>搭配记录</span><strong>{{ props.app.recommendationStats.total }}</strong></div>
          </div>
          <div class="closet-suggestion"><Check :size="15" aria-hidden="true" /><span>{{ props.app.wardrobeStats.review ? '部分衣物信息待确认，补充后即可参与推荐。' : (props.app.wardrobeStats.ready ? '已确认的衣物可用于穿搭推荐。' : '请添加并确认上装与下装，或连体装与配套单品，再生成搭配方案。') }}</span></div>
        </aside>
      </section>

      <section class="generated-section" aria-labelledby="generated-title">
        <header class="section-header">
          <div>
            <div class="section-number">04 · 记录</div>
            <h2 id="generated-title">本次生成结果</h2>
          </div>
          <span v-if="state.currentRecommendation" class="generated-time">{{ formatDate(state.currentRecommendation.generatedAt) }}</span>
        </header>

        <div v-if="state.generating" class="generated-state" aria-live="polite">
          <LoaderCircle class="spinning" :size="22" />
          <strong>正在生成搭配方案…</strong>
          <span>正在综合天气、场合、形象资料与个人偏好选择衣橱单品。</span>
        </div>

        <article v-else-if="state.currentRecommendation" class="generated-card">
          <div class="generated-card-topline">
            <div><span class="record-id">搭配 #{{ state.currentRecommendation.id }}</span><span>{{ state.currentRecommendation.occasion }}</span><span>{{ state.currentRecommendation.city }}</span></div>
            <span class="saved-state" :class="{ saved: state.currentRecommendation.saved }"><Bookmark :size="15" :fill="state.currentRecommendation.saved ? 'currentColor' : 'none'" />{{ state.currentRecommendation.saved ? '已收藏' : '未收藏' }}</span>
          </div>
          <div class="engine-line"><span><Sparkles :size="13" />{{ props.app.engineLabel(state.currentRecommendation.engine) }}</span><small v-if="state.currentRecommendation.generationAudit?.modelName">{{ state.currentRecommendation.generationAudit.modelName }}</small><small v-if="state.currentRecommendation.generationAudit?.fallbackReason">{{ props.app.fallbackReasonLabel(state.currentRecommendation.generationAudit.fallbackReason) }}</small></div>
          <h3>{{ formatRecommendationTitle(state.currentRecommendation) }}</h3>
          <div class="generated-content-grid">
            <ul v-if="currentRecommendationItems.length" class="generated-items" aria-label="搭配中的衣物">
              <li v-for="(item, itemIndex) in currentRecommendationItems" :key="`${state.currentRecommendation.id}-${item.id || itemIndex}`">
                <div class="generated-item-image">
                  <img v-if="imageSource(item, 'result', itemIndex)" :src="imageSource(item, 'result', itemIndex)" :alt="item.name" @error="imageFailed('result', item, itemIndex)" />
                  <span v-else class="generated-image-missing"><Shirt :size="15" aria-hidden="true" /><small>暂无图片</small></span>
                </div>
                <div><span>{{ item.category }} · {{ item.color }}</span><strong>{{ item.name }}</strong><small>{{ item.style || '衣橱衣物' }}</small></div>
              </li>
            </ul>
            <div v-else class="generated-items-empty"><Shirt :size="17" aria-hidden="true" /><span>该方案的单品已不在当前可用衣橱中。</span></div>
            <div class="generated-reason">
              <span>推荐说明</span>
              <button v-if="selectedLook.source?.id === state.currentRecommendation.id && selectedLookReason" class="rationale-link" type="button" @click="showRecommendationReason">查看搭配依据 <ArrowRight :size="14" /></button>
              <p v-else>{{ currentRecommendationReason }}</p>
            </div>
          </div>
          <footer class="generated-actions">
            <RecommendationFeedback :app="app" :recommendation="state.currentRecommendation" />
            <button class="save-button" type="button" :class="{ saved: state.currentRecommendation.saved }" :disabled="state.saving || state.currentRecommendation.saved" @click="props.app.saveRecommendation()"><LoaderCircle v-if="state.saving" class="spinning" :size="16" /><Bookmark v-else :size="16" :fill="state.currentRecommendation.saved ? 'currentColor' : 'none'" />{{ state.currentRecommendation.saved ? '已收藏' : '收藏搭配' }}</button>
          </footer>
        </article>

          <div v-else class="generated-empty">
          <div><Sparkles :size="19" aria-hidden="true" /><strong>暂无生成结果</strong></div>
          <span>通过穿搭助手设置需求，生成个性化搭配方案。</span>
          <button type="button" @click="openAssistant()"><MessageCircle :size="14" />打开穿搭助手 <ArrowRight :size="14" /></button>
        </div>
      </section>

      <section class="signal-grid" aria-label="搭配参考">
        <article class="signal-panel note-signal">
          <header class="panel-header compact"><div><div class="section-number">05 · 建议</div><h2>穿搭建议</h2></div><Wind :size="19" aria-hidden="true" /></header>
          <ul>
            <li><span>01</span><p><strong>合理复用衣橱单品</strong>通过调整配色与组合，拓展同一单品的搭配方式。</p></li>
            <li><span>02</span><p><strong>明确主要配色</strong>确定主要配色后，再以配饰补充层次。</p></li>
            <li><span>03</span><p><strong>考虑天气与温差</strong>温差较大时，可准备便于穿脱的轻薄外套。</p></li>
          </ul>
        </article>
      </section>
    </div>
  </section>
</template>

<style scoped>
.trend-reference-banner { display:flex; flex-wrap:wrap; align-items:center; gap:20px; padding:24px; margin:20px 0; background:#e6ece2; color:#263e32; }
.trend-reference-banner > div { flex:1 1 260px; }
.trend-reference-banner p { margin:8px 0; overflow-wrap:anywhere; }
.trend-reference-banner button { cursor:pointer; min-height:44px; padding:10px 14px; color:inherit; border:1px solid #345647; background:transparent; }
.recommendation-view {
  --rec-bg: #f4f1eb;
  --rec-paper: #fbfaf6;
  --rec-paper-strong: #fffefa;
  --rec-ink: #202923;
  --rec-muted: #7a8179;
  --rec-line: #dddcd3;
  --rec-line-dark: #c3c7bd;
  --rec-accent: #788a6b;
  --rec-accent-soft: #e6ece2;
  --rec-rust: #b96850;
  --rec-dark: #202b24;
  min-height: calc(100vh - 76px);
  color: var(--rec-ink);
  background: var(--rec-bg);
}

.recommendation-canvas {
  width: min(calc(100% - 64px), 1280px);
  margin: 0 auto;
  padding: 42px 0 96px;
}

.recommendation-view button,
.recommendation-view input {
  font: inherit;
}

.recommendation-view button {
  border: 0;
}

.recommendation-view button:focus-visible,
.recommendation-view input:focus-visible {
  outline: 2px solid var(--rec-accent);
  outline-offset: 3px;
}

.recommendation-view h1,
.recommendation-view h2,
.recommendation-view h3,
.recommendation-view p {
  margin-top: 0;
}

.recommendation-view h1,
.recommendation-view h2,
.recommendation-view h3 {
  font-family: var(--font-display);
  letter-spacing: -.045em;
}

.recommendation-hero {
  border-bottom: 1px solid var(--rec-line);
  padding-bottom: 42px;
}

.hero-overline,
.hero-overline > span,
.hero-actions,
.hero-footnote,
.hero-index,
.weather-brief-top,
.section-number,
.generated-card-topline,
.engine-line,
.generated-actions,
.rating-control,
.rating-control > div,
.save-button,
.signal-empty {
  display: flex;
  align-items: center;
}

.hero-overline {
  justify-content: space-between;
  gap: 24px;
  color: var(--rec-muted);
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0;
}

.hero-overline > span:first-child {
  gap: 8px;
  color: var(--rec-accent);
}

.hero-overline-date {
  letter-spacing: .04em;
  text-transform: none;
}

.hero-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 340px;
  gap: clamp(48px, 9vw, 136px);
  align-items: end;
  padding: 54px 0 18px;
}

.hero-copy {
  max-width: 760px;
}

.hero-index {
  gap: 12px;
  margin-bottom: 22px;
  color: var(--rec-muted);
  font-size: 11px;
  letter-spacing: .04em;
}

.hero-index span:first-child {
  color: var(--rec-rust);
  font-family: var(--font-mono);
  font-size: 12px;
  font-weight: 700;
}

.hero-copy h1 {
  max-width: 720px;
  margin-bottom: 22px;
  color: var(--rec-dark);
  font-size: clamp(46px, 6vw, 76px);
  font-weight: 650;
  line-height: 1.03;
}

.hero-lead {
  max-width: 500px;
  margin-bottom: 30px;
  color: var(--rec-muted);
  font-size: 14px;
  line-height: 1.85;
}

.hero-actions {
  flex-wrap: wrap;
  gap: 10px;
}

.hero-primary,
.save-button,
.generated-empty button {
  min-height: 42px;
  gap: 8px;
  padding: 10px 16px;
  font-size: 12px;
  font-weight: 700;
}

.hero-primary,
.save-button {
  display: inline-flex;
  justify-content: center;
  color: var(--rec-paper-strong);
  background: var(--rec-dark);
}

.hero-primary:hover,
.save-button:hover:not(:disabled) {
  background: var(--rec-accent);
  transform: translateY(-1px);
}

.generated-empty button {
  display: inline-flex;
  justify-content: center;
  border: 1px solid var(--rec-line-dark);
  color: var(--rec-ink);
  background: transparent;
}

.generated-empty button:hover {
  border-color: var(--rec-accent);
  color: var(--rec-accent);
}

.hero-footnote {
  flex-wrap: wrap;
  gap: 6px 12px;
  margin-top: 34px;
  color: var(--rec-accent);
  font-size: 11px;
}

.hero-footnote-muted {
  margin-left: 8px;
  color: var(--rec-muted);
}

.weather-brief {
  border-top: 1px solid var(--rec-ink);
  border-bottom: 1px solid var(--rec-line);
  padding: 18px 0 16px;
}

.weather-brief-top {
  justify-content: space-between;
  gap: 16px;
}

.weather-brief-top > div {
  display: grid;
  gap: 6px;
}

.brief-label,
.weather-reading span,
.overview-item span,
.overview-item em,
.overview-source span,
  .overview-source p,
.section-header p,
.generated-time,
.generated-card-topline > div,
.engine-line,
.generated-item-image + div span,
.generated-item-image + div small,
.generated-reason span,
.generated-reason p,
.signal-panel span,
.signal-panel p,
.note-signal p,
.closet-score span,
.closet-facts span,
.closet-suggestion {
  color: var(--rec-muted);
  font-size: 11px;
}

.weather-brief-top > svg {
  color: var(--rec-rust);
}

.weather-brief-top strong {
  color: var(--rec-dark);
  font-family: var(--font-display);
  font-size: 23px;
  font-weight: 650;
}

.weather-reading {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 14px;
  align-items: baseline;
  margin-top: 28px;
}

.weather-reading > strong {
  font-family: var(--font-mono);
  font-size: 38px;
  font-weight: 500;
  line-height: 1;
}

.weather-reading > div {
  display: grid;
  gap: 4px;
}

.overview-strip {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr)) minmax(230px, .95fr);
  border-bottom: 1px solid var(--rec-line);
}

.overview-item,
.overview-source {
  min-width: 0;
  padding: 23px 22px 25px 0;
}

.overview-item + .overview-item,
.overview-source {
  border-left: 1px solid var(--rec-line);
  padding-left: 22px;
}

.overview-item span,
.overview-item em,
.overview-source span,
.overview-source p {
  display: block;
}

.overview-item strong {
  display: block;
  margin: 9px 0 6px;
  color: var(--rec-dark);
  font-family: var(--font-mono);
  font-size: 29px;
  font-weight: 500;
  line-height: 1;
}

.overview-item strong small {
  font: 400 11px/1 var(--font-sans);
}

.overview-style-value {
  overflow: hidden;
  font-family: var(--font-display) !important;
  font-size: 18px !important;
  letter-spacing: -.035em;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.overview-item-accent strong {
  color: var(--rec-accent);
}

.overview-item em {
  font-style: normal;
}

.overview-source strong {
  display: block;
  margin: 9px 0 6px;
  font-family: var(--font-display);
  font-size: 16px;
  font-weight: 650;
}

.overview-source p {
  max-width: 210px;
  margin: 0;
  line-height: 1.55;
}

.ootd-section {
  padding-top: 62px;
  scroll-margin-top: 5rem;
}

.section-header,
.panel-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
}

.section-number {
  gap: 7px;
  margin-bottom: 12px;
  color: var(--rec-rust);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: .02em;
}

.section-header h2,
.panel-header h2 {
  margin-bottom: 8px;
  color: var(--rec-dark);
  font-size: 29px;
  font-weight: 650;
  line-height: 1.1;
}

.section-header p,
.panel-header p {
  margin: 0;
  line-height: 1.6;
}

.ootd-controls {
  display: flex;
  align-items: center;
  gap: 7px;
}

.ootd-today,
.round-control {
  color: var(--rec-muted);
  background: transparent;
  font-size: 11px;
  font-weight: 700;
}

.ootd-today {
  min-height: 34px;
  border-bottom: 1px solid var(--rec-ink);
  padding: 0 3px;
  color: var(--rec-ink);
}

.round-control {
  display: grid;
  width: 34px;
  height: 34px;
  place-items: center;
  border: 1px solid var(--rec-line);
  border-radius: 50%;
}

.round-control:hover,
.round-control:focus-visible {
  border-color: var(--rec-accent);
  color: var(--rec-accent);
}

.ootd-workspace {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 10px;
  margin-top: 30px;
}

.ootd-stack-panel {
  overflow: hidden;
  min-width: 0;
  min-height: 520px;
  border: 1px solid var(--rec-line);
  padding: 20px 22px 17px;
  background: #e8e3d9;
}

.ootd-detail-topline,
.ootd-detail-kicker,
.ootd-detail-palette,
.ootd-items-heading,
.ootd-detail-actions {
  display: flex;
  align-items: center;
}

.ootd-detail-kicker,
.ootd-items-heading,
.ootd-detail-palette,
.ootd-editor-note > div > span {
  font-family: var(--font-mono);
  font-size: 9px;
  letter-spacing: .06em;
  text-transform: uppercase;
}

.ootd-detail-kicker,
.ootd-items-heading,
.ootd-detail-palette,
.ootd-editor-note > div > span {
  color: var(--rec-accent);
}

.ootd-stack-stage {
  width: min(100%, 520px);
  height: 680px;
  margin: 0 auto;
}

.ootd-stack-card {
  position: relative;
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto;
  width: 100%;
  height: 100%;
  overflow: hidden;
  border: 1px solid rgba(255, 254, 250, .45);
  border-radius: 18px;
  background: #141816;
  box-shadow: 0 18px 38px rgba(32, 43, 36, .16);
}

.ootd-stack-card.is-empty {
  color: var(--rec-ink);
  background: var(--rec-paper-strong);
}

.ootd-stack-card-topline,
.ootd-stack-card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 16px 17px;
}

.ootd-stack-card-topline {
  color: rgba(255, 254, 250, .68);
  font-family: var(--font-mono);
  font-size: 9px;
  letter-spacing: .05em;
}

.ootd-stack-card.is-empty .ootd-stack-card-topline {
  color: var(--rec-muted);
}

.ootd-stack-card-topline strong {
  color: var(--rec-paper-strong);
  font-family: var(--font-sans);
  font-size: 10px;
  letter-spacing: 0;
}

.ootd-stack-card.is-empty .ootd-stack-card-topline strong {
  color: var(--rec-rust);
}

.ootd-model-visual {
  display: flex;
  min-height: 0;
  align-items: center;
  justify-content: center;
  padding: 0 22px;
}

.ootd-model-visual img {
  display: block;
  width: 100%;
  height: 100%;
  min-width: 0;
  min-height: 0;
  object-fit: contain;
  object-position: center;
  pointer-events: none;
  user-select: none;
}

.ootd-model-empty {
  display: grid;
  align-content: center;
  justify-items: center;
  gap: 10px;
  padding: 30px;
  color: var(--rec-muted);
  text-align: center;
}

.ootd-model-empty svg {
  color: var(--rec-accent);
}

.ootd-model-empty strong {
  color: var(--rec-ink);
  font-family: var(--font-display);
  font-size: 18px;
}

.ootd-model-empty span {
  max-width: 220px;
  font-size: 11px;
  line-height: 1.6;
}

.ootd-stack-card-footer {
  align-items: flex-end;
  border-top: 1px solid rgba(255, 254, 250, .14);
}

.ootd-stack-card.is-empty .ootd-stack-card-footer {
  border-color: var(--rec-line);
}

.ootd-stack-card-footer strong {
  overflow: hidden;
  color: var(--rec-paper-strong);
  font-family: var(--font-display);
  font-size: 16px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ootd-stack-card.is-empty .ootd-stack-card-footer strong {
  color: var(--rec-ink);
}

.ootd-stack-card-footer span {
  flex: 0 0 auto;
  color: rgba(255, 254, 250, .68);
  font-size: 10px;
}

.ootd-stack-card.is-empty .ootd-stack-card-footer span {
  color: var(--rec-muted);
}

.ootd-detail {
  min-width: 0;
  border: 1px solid var(--rec-line);
  display: flex;
  flex-direction: column;
  min-height: 520px;
  padding: 30px 34px 25px;
  background: var(--rec-paper);
}

.ootd-detail-topline {
  justify-content: space-between;
  gap: 12px;
  color: var(--rec-muted);
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: .06em;
}

.ootd-detail-topline strong {
  color: var(--rec-rust);
  font-weight: 700;
}

.ootd-detail-kicker {
  justify-content: space-between;
  gap: 12px;
  margin-top: 48px;
  font-weight: 700;
}

.ootd-fit-label {
  border: 1px solid var(--rec-line-dark);
  border-radius: 999px;
  padding: 5px 8px;
  color: var(--rec-ink);
  font-family: var(--font-sans);
  font-size: 10px;
  letter-spacing: 0;
  text-transform: none;
}

.ootd-source-state {
  color: var(--rec-rust);
  font-family: var(--font-sans);
  font-size: 10px;
  letter-spacing: 0;
  text-transform: none;
  white-space: nowrap;
}

.ootd-source-state.inspiration {
  color: var(--rec-muted);
}

.ootd-source-state.preview {
  color: var(--rec-accent);
}

.ootd-detail h3 {
  max-width: 620px;
  margin: 18px 0 11px;
  color: var(--rec-dark);
  font-size: 35px;
  font-weight: 650;
  line-height: 1.1;
}

.ootd-detail-lead {
  max-width: 570px;
  margin: 0;
  color: var(--rec-muted);
  line-height: 1.65;
}

.ootd-model-note {
  display: grid;
  grid-template-columns: 70px minmax(0, 1fr);
  gap: 10px;
  margin-top: 18px;
  border-top: 1px solid var(--rec-line);
  border-bottom: 1px solid var(--rec-line);
  padding: 11px 0;
}

.ootd-model-note span {
  color: var(--rec-accent);
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 700;
}

.ootd-model-note strong {
  color: var(--rec-muted);
  font-size: 11px;
  font-weight: 500;
  line-height: 1.5;
}

.ootd-rationale {
  margin-top: 18px;
  border-bottom: 1px solid var(--rec-line);
}

.ootd-rationale-heading {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  padding-bottom: 9px;
}

.ootd-rationale-heading span {
  color: var(--rec-accent);
  font-family: var(--font-mono);
  font-size: 9px;
  font-weight: 700;
  letter-spacing: .06em;
}

.ootd-rationale-heading strong {
  color: var(--rec-ink);
  font-size: 12px;
}

.ootd-rationale dl {
  display: grid;
  margin: 0;
}

.ootd-rationale dl > div {
  display: grid;
  grid-template-columns: 70px minmax(0, 1fr);
  gap: 10px;
  border-top: 1px solid var(--rec-line);
  padding: 10px 0;
}

.ootd-rationale dt {
  color: var(--rec-ink);
  font-size: 11px;
  font-weight: 700;
}

.ootd-rationale dd {
  margin: 0;
  color: var(--rec-muted);
  font-size: 11px;
  line-height: 1.55;
}

.ootd-reason {
  border-top: 1px solid var(--rec-line);
  padding-top: 16px;
}

.ootd-reason:focus-visible,
.rationale-link:focus-visible {
  outline: 2px solid var(--rec-accent);
  outline-offset: 3px;
}

.ootd-reason p {
  margin: 0;
  color: var(--rec-muted);
  font-size: 12px;
  line-height: 1.8;
  overflow-wrap: anywhere;
}

.rationale-link {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 44px;
  margin-top: 8px;
  padding: 0;
  color: var(--rec-ink);
  background: transparent;
  font-size: 12px;
  cursor: pointer;
}

.ootd-detail-palette {
  justify-content: space-between;
  gap: 10px;
  margin-top: 25px;
  border-top: 1px solid var(--rec-line);
  border-bottom: 1px solid var(--rec-line);
  padding: 11px 0;
}

.ootd-detail-palette strong {
  overflow: hidden;
  color: var(--rec-dark);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ootd-items-heading {
  justify-content: space-between;
  gap: 10px;
  margin-top: 21px;
}

.ootd-items-heading strong {
  color: var(--rec-ink);
  font-family: var(--font-sans);
  font-size: 11px;
  letter-spacing: 0;
}

.ootd-item-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px 18px;
  margin: 12px 0 0;
  padding: 0;
  list-style: none;
}

.ootd-item-list li {
  display: grid;
  grid-template-columns: 72px minmax(0, 1fr);
  align-items: center;
  gap: 10px;
  min-width: 0;
  border: 1px solid var(--rec-line);
  border-radius: 10px;
  padding: 9px;
  background: var(--rec-paper-strong);
}

.ootd-item-image {
  width: 72px;
  height: 86px;
  overflow: hidden;
  border-radius: 6px;
  background: var(--rec-accent-soft);
}

.ootd-item-image img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.ootd-image-missing,
.generated-image-missing {
  display: grid;
  width: 100%;
  height: 100%;
  place-items: center;
  align-content: center;
  gap: 5px;
  color: var(--rec-muted);
  text-align: center;
}

.ootd-image-missing svg,
.generated-image-missing svg {
  color: var(--rec-accent);
}

.ootd-image-missing small,
.generated-image-missing small {
  font-size: 9px;
}

.ootd-item-copy {
  display: grid;
  min-width: 0;
  gap: 3px;
}

.ootd-item-copy span,
.ootd-item-copy strong,
.ootd-item-copy small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ootd-item-copy span {
  color: var(--rec-accent);
  font-family: var(--font-mono);
  font-size: 9px;
}

.ootd-item-copy strong {
  color: var(--rec-dark);
  font-size: 13px;
  font-weight: 650;
}

.ootd-item-copy small {
  color: var(--rec-muted);
  font-size: 10px;
}

.ootd-empty {
  display: flex;
  align-items: center;
  gap: 9px;
  margin-top: 14px;
  border: 1px dashed var(--rec-line-dark);
  padding: 12px;
  color: var(--rec-muted);
  font-size: 11px;
}

.ootd-empty svg {
  flex: 0 0 auto;
  color: var(--rec-accent);
}

.ootd-editor-note {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 10px;
  margin-top: 20px;
  border-left: 2px solid var(--rec-accent);
  padding-left: 12px;
}

.ootd-editor-note > svg {
  color: var(--rec-accent);
}

.ootd-editor-note > div {
  display: grid;
  gap: 5px;
}

.ootd-editor-note p {
  overflow: hidden;
  display: -webkit-box;
  margin: 0;
  color: var(--rec-muted);
  font-size: 11px;
  line-height: 1.5;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.ootd-detail-actions {
  gap: 9px;
  margin-top: auto;
  padding-top: 24px;
}

.ootd-board-wrap {
  margin-top: 30px;
}

.ootd-board {
  position: relative;
  display: grid;
  grid-template-columns: minmax(180px, .8fr) minmax(300px, 1.2fr) minmax(180px, .8fr);
  grid-template-rows: minmax(0, 1fr) minmax(0, 1fr);
  gap: 28px;
  min-height: 760px;
  overflow: hidden;
  border: 1px solid rgba(54, 61, 57, .08);
  border-radius: 28px;
  padding: 54px 46px 36px;
  background:
    radial-gradient(circle at 50% 42%, rgba(255, 255, 255, .98), rgba(255, 255, 255, 0) 37%),
    linear-gradient(145deg, #f8f8f6 0%, #f1f2ef 100%);
  box-shadow: 0 24px 60px rgba(47, 57, 51, .09);
}

.ootd-board-heading {
  position: absolute;
  top: 23px;
  right: 28px;
  left: 28px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  color: #8a918b;
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: .06em;
  text-transform: uppercase;
}

.ootd-board-heading strong {
  color: var(--rec-rust);
  font-family: var(--font-sans);
  font-size: 11px;
  letter-spacing: 0;
  text-transform: none;
}

.ootd-board-model {
  z-index: 1;
  grid-column: 2;
  grid-row: 1 / 3;
  display: flex;
  min-width: 0;
  min-height: 0;
  align-items: center;
  justify-content: center;
}

.ootd-board-model-visual {
  position: relative;
  display: flex;
  width: 100%;
  height: 100%;
  min-height: 620px;
  align-items: center;
  justify-content: center;
}

.ootd-board-model-visual img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: contain;
  object-position: center;
  pointer-events: none;
  user-select: none;
}

.ootd-board-model-loading {
  position: absolute;
  right: 50%;
  bottom: 14px;
  display: inline-flex;
  align-items: center;
  gap: 7px;
  transform: translateX(50%);
  border: 1px solid rgba(55, 67, 60, .1);
  border-radius: 999px;
  padding: 8px 12px;
  color: var(--rec-muted);
  background: rgba(255, 255, 255, .88);
  box-shadow: 0 8px 20px rgba(47, 57, 51, .08);
  font-size: 10px;
  white-space: nowrap;
}

.ootd-board-model.is-empty {
  border: 1px dashed rgba(55, 67, 60, .16);
  border-radius: 22px;
}

.ootd-board-item {
  z-index: 2;
  display: flex;
  min-width: 0;
  min-height: 0;
  flex-direction: column;
  border: 1px solid rgba(55, 67, 60, .08);
  border-radius: 22px;
  padding: 13px 13px 12px;
  background: rgba(255, 255, 253, .95);
  box-shadow: 0 14px 28px rgba(47, 57, 51, .08);
}

.ootd-board-item.board-item-1 { grid-column: 1; grid-row: 1; }
.ootd-board-item.board-item-2 { grid-column: 3; grid-row: 1; }
.ootd-board-item.board-item-3 { grid-column: 1; grid-row: 2; }
.ootd-board-item.board-item-4 { grid-column: 3; grid-row: 2; }

.ootd-board-item-image {
  display: grid;
  width: 100%;
  aspect-ratio: 1 / 1;
  min-height: 0;
  flex: 0 0 auto;
  place-items: center;
  overflow: hidden;
  border-radius: 15px;
  background: #fbfbf8;
}

.ootd-board-item-image img {
  display: block;
  width: 100%;
  height: 100%;
  min-height: 0;
  object-fit: contain;
}

.ootd-board-item footer {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 8px;
  padding: 13px 3px 0;
}

.ootd-board-item footer > div {
  display: grid;
  min-width: 0;
  gap: 4px;
}

.ootd-board-item footer strong {
  color: var(--rec-ink);
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: .06em;
}

.ootd-board-item footer small {
  overflow: hidden;
  color: var(--rec-muted);
  font-size: 10px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ootd-board-item footer > span {
  flex: 0 0 auto;
  border-radius: 999px;
  padding: 5px 8px;
  color: #7c827e;
  background: #eceeeb;
  font-size: 9px;
  white-space: nowrap;
}

.ootd-board-empty {
  position: absolute;
  z-index: 3;
  top: 50%;
  left: 50%;
  display: grid;
  max-width: min(360px, calc(100% - 32px));
  justify-items: center;
  gap: 9px;
  padding: 18px;
  transform: translate(-50%, -50%);
  color: var(--rec-muted);
  text-align: center;
}

.ootd-board-empty svg {
  color: var(--rec-accent);
}

.ootd-board-empty strong {
  color: var(--rec-ink);
  font-family: var(--font-display);
  font-size: 18px;
}

.ootd-board-empty span {
  max-width: 300px;
  font-size: 11px;
  line-height: 1.6;
}

.ootd-empty-action {
  min-height: 34px;
  margin-top: 4px;
  border: 1px solid var(--rec-line-dark);
  border-radius: 999px;
  padding: 0 14px;
  color: var(--rec-ink);
  background: var(--rec-paper-strong);
  font-size: 11px;
  font-weight: 700;
  cursor: pointer;
}

.ootd-empty-action:hover,
.ootd-empty-action:focus-visible {
  border-color: var(--rec-accent);
  color: var(--rec-accent);
}

.ootd-board-empty-page {
  position: relative;
  top: auto;
  left: auto;
  min-height: 420px;
  transform: none;
  border: 1px dashed var(--rec-line-dark);
  border-radius: 24px;
  padding: 50px 24px;
}

.ootd-board-caption {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 20px;
  margin-top: 15px;
}

.ootd-board-status {
  display: flex;
  width: 100%;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: var(--rec-muted);
  font-size: 11px;
  text-align: left;
}

.ootd-board-status > span {
  min-width: 0;
}

.ootd-visual-action {
  display: inline-flex;
  min-height: 32px;
  align-items: center;
  gap: 6px;
  border: 1px solid var(--rec-line-dark);
  border-radius: 999px;
  padding: 0 12px;
  color: var(--rec-ink);
  background: var(--rec-paper-strong);
  font-size: 10px;
  font-weight: 700;
  white-space: nowrap;
}

.ootd-visual-action:hover:not(:disabled),
.ootd-visual-action:focus-visible:not(:disabled) {
  border-color: var(--rec-accent);
  color: var(--rec-accent);
}

.ootd-visual-action:disabled {
  cursor: wait;
  opacity: .65;
}

.ootd-board-wrap > .ootd-rationale {
  margin-top: 30px;
  border: 1px solid var(--rec-line);
  border-radius: 18px;
  padding: 18px 22px;
  background: rgba(255, 255, 253, .5);
}

.ootd-board-wrap > .ootd-rationale dl > div {
  grid-template-columns: 92px minmax(0, 1fr);
}

.workbench-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 28px;
  padding-top: 64px;
}

.closet-panel,
.signal-panel {
  min-width: 0;
  border-top: 1px solid var(--rec-ink);
  padding-top: 22px;
}

.panel-header {
  align-items: flex-start;
}

.panel-header h2 {
  font-size: 24px;
}

.panel-header p {
  max-width: 430px;
}

.closet-panel > .panel-header > svg {
  color: var(--rec-accent);
}

.panel-header.compact h2 {
  margin-bottom: 0;
}

.closet-score {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-top: 35px;
}

.closet-score strong {
  color: var(--rec-dark);
  font-family: var(--font-mono);
  font-size: 40px;
  font-weight: 500;
  line-height: 1;
}

.closet-score span {
  max-width: 105px;
  line-height: 1.45;
}

.closet-progress {
  height: 7px;
  margin-top: 17px;
  background: var(--rec-line);
}

.closet-progress span {
  display: block;
  height: 100%;
  transform-origin: left center;
  background: var(--rec-accent);
  transition: transform 300ms ease;
}

.closet-facts {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
  margin-top: 24px;
}

.closet-facts div {
  display: grid;
  gap: 7px;
  border-top: 1px solid var(--rec-line);
  padding-top: 12px;
}

.closet-facts strong {
  font-family: var(--font-mono);
  font-size: 23px;
  font-weight: 500;
}

.closet-suggestion {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 8px;
  align-items: start;
  margin-top: 27px;
  border-left: 2px solid var(--rec-rust);
  padding-left: 11px;
  line-height: 1.6;
}

.closet-suggestion svg {
  color: var(--rec-rust);
}

.generated-section {
  padding-top: 70px;
}

.generated-time {
  align-self: center;
}

.generated-state,
.generated-empty {
  display: grid;
  min-height: 180px;
  place-content: center;
  justify-items: center;
  gap: 8px;
  margin-top: 28px;
  border: 1px dashed var(--rec-line-dark);
  color: var(--rec-muted);
  text-align: center;
}

.generated-state svg,
.generated-empty svg {
  color: var(--rec-accent);
}

.generated-state strong,
.generated-empty strong {
  color: var(--rec-ink);
  font-family: var(--font-display);
  font-size: 18px;
  font-weight: 650;
}

.generated-state span,
.generated-empty > span {
  font-size: 11px;
}

.generated-empty > div {
  display: flex;
  align-items: center;
  gap: 8px;
}

.generated-empty button {
  min-height: 34px;
  margin-top: 5px;
  padding: 7px 11px;
}

.generated-card {
  margin-top: 28px;
  border: 1px solid var(--rec-line);
  padding: 28px 30px 24px;
  background: var(--rec-paper);
}

.generated-card-topline {
  justify-content: space-between;
  gap: 16px;
}

.generated-card-topline > div {
  flex-wrap: wrap;
  gap: 8px 16px;
}

.record-id {
  color: var(--rec-accent);
  font-weight: 700;
}

.saved-state {
  gap: 6px;
  white-space: nowrap;
}

.saved-state.saved {
  color: var(--rec-accent);
}

.engine-line {
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 15px;
}

.engine-line > span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  border-radius: 999px;
  padding: 5px 9px;
  color: var(--rec-accent);
  background: var(--rec-accent-soft);
  font-size: 10px;
  font-weight: 700;
}

.engine-line small {
  color: var(--rec-muted);
  font-size: 10px;
}

.generated-card h3 {
  max-width: 760px;
  margin: 20px 0 25px;
  color: var(--rec-dark);
  font-size: 27px;
  font-weight: 650;
  line-height: 1.23;
}

.generated-content-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.25fr) minmax(240px, .75fr);
  gap: 28px;
  border-top: 1px solid var(--rec-line);
  padding-top: 22px;
}

.generated-items {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 15px 20px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.generated-items li {
  display: grid;
  grid-template-columns: 52px minmax(0, 1fr);
  gap: 11px;
  align-items: center;
  min-width: 0;
}

.generated-item-image {
  width: 52px;
  height: 58px;
  overflow: hidden;
  border-radius: 5px;
  background: var(--rec-bg);
}

.generated-items-empty {
  display: flex;
  min-height: 74px;
  align-items: center;
  gap: 8px;
  border: 1px dashed var(--rec-line-dark);
  padding: 12px;
  color: var(--rec-muted);
  font-size: 11px;
}

.generated-items-empty svg {
  flex: 0 0 auto;
  color: var(--rec-accent);
}

.generated-item-image + div {
  display: grid;
  gap: 3px;
  min-width: 0;
}

.generated-item-image + div span,
.generated-item-image + div small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.generated-item-image + div span {
  font-size: 9px;
}

.generated-item-image + div strong {
  overflow: hidden;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.generated-item-image + div small {
  font-size: 10px;
}

.generated-reason {
  border-left: 1px solid var(--rec-line);
  padding-left: 22px;
}

.generated-reason > span {
  color: var(--rec-accent);
  font-weight: 700;
}

.generated-reason p {
  margin: 8px 0 0;
  line-height: 1.7;
}

.generated-actions {
  justify-content: space-between;
  gap: 18px;
  margin-top: 24px;
  border-top: 1px solid var(--rec-line);
  padding-top: 18px;
}

.rating-control {
  gap: 13px;
  color: var(--rec-muted);
  font-size: 11px;
}

.rating-control > div {
  gap: 1px;
}

.rating-control button {
  display: grid;
  width: 27px;
  height: 27px;
  place-items: center;
  color: var(--rec-line-dark);
  background: transparent;
}

.rating-control button:hover,
.rating-control button:focus-visible,
.rating-control button.rated {
  color: var(--rec-rust);
}

.save-button {
  min-width: 148px;
}

.save-button.saved {
  color: var(--rec-accent);
  background: var(--rec-accent-soft);
}

.signal-grid {
  display: grid;
  grid-template-columns: 1.1fr .9fr;
  gap: 28px;
  padding-top: 72px;
}

.signal-panel > .panel-header > svg {
  color: var(--rec-rust);
}

.trend-signal-content {
  display: grid;
  grid-template-columns: 150px minmax(0, 1fr);
  gap: 18px;
  align-items: stretch;
  margin-top: 24px;
}

.trend-signal-content > img {
  height: 150px;
  border-radius: 8px;
}

.trend-signal-content > div {
  display: flex;
  min-width: 0;
  flex-direction: column;
  align-items: flex-start;
}

.trend-signal-content span {
  color: var(--rec-accent);
}

.trend-signal-content strong {
  margin-top: 7px;
  color: var(--rec-ink);
  font-family: var(--font-display);
  font-size: 19px;
  font-weight: 650;
  line-height: 1.25;
}

.trend-signal-content p {
  margin: 7px 0 0;
  line-height: 1.55;
}

.trend-signal-content button {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-top: auto;
  padding: 0;
  color: var(--rec-ink);
  background: transparent;
  font-size: 11px;
  font-weight: 700;
}

.trend-signal-content button:hover {
  color: var(--rec-accent);
}

.signal-empty {
  justify-content: center;
  min-height: 150px;
  gap: 8px;
  color: var(--rec-muted);
  font-size: 11px;
}

.note-signal ul {
  display: grid;
  gap: 0;
  margin: 24px 0 0;
  padding: 0;
  list-style: none;
}

.note-signal li {
  display: grid;
  grid-template-columns: 32px minmax(0, 1fr);
  gap: 8px;
  border-bottom: 1px solid var(--rec-line);
  padding: 13px 0;
}

.note-signal li:first-child {
  border-top: 1px solid var(--rec-line);
}

.note-signal li > span {
  color: var(--rec-rust);
  font-family: var(--font-mono);
  font-size: 10px;
}

.note-signal p {
  margin: 0;
  line-height: 1.55;
}

.note-signal p strong {
  display: block;
  margin-bottom: 2px;
  color: var(--rec-ink);
  font-size: 12px;
}

.spinning {
  animation: spin 900ms linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

@media (max-width: 1120px) {
  .recommendation-canvas {
    width: min(calc(100% - 48px), 960px);
  }

  .hero-grid {
    grid-template-columns: minmax(0, 1fr) 290px;
    gap: 48px;
  }

  .overview-strip {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .overview-source {
    grid-column: 1 / -1;
    border-top: 1px solid var(--rec-line);
    border-left: 0;
    padding: 18px 0 20px;
  }

  .overview-source p {
    display: inline;
    margin-left: 10px;
  }

  .ootd-workspace {
    grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  }

  .ootd-stack-panel {
    padding-right: 18px;
    padding-left: 18px;
  }

  .ootd-stack-stage {
    width: min(100%, 460px);
    height: 620px;
  }
}

@media (max-width: 840px) {
  .hero-grid,
  .ootd-workspace,
  .workbench-grid,
  .signal-grid {
    grid-template-columns: 1fr;
  }

  .hero-grid {
    gap: 45px;
  }

  .weather-brief {
    max-width: 440px;
  }

  .ootd-stack-panel {
    min-height: 0;
  }

  .ootd-board {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    grid-template-rows: minmax(420px, auto) minmax(180px, auto) minmax(180px, auto);
    min-height: 0;
    padding: 54px 22px 22px;
  }

  .ootd-board-model {
    grid-column: 1 / -1;
    grid-row: 1;
  }

  .ootd-board-model-visual {
    min-height: 420px;
  }

  .ootd-board-item.board-item-1 { grid-column: 1; grid-row: 2; }
  .ootd-board-item.board-item-2 { grid-column: 2; grid-row: 2; }
  .ootd-board-item.board-item-3 { grid-column: 1; grid-row: 3; }
  .ootd-board-item.board-item-4 { grid-column: 2; grid-row: 3; }

  .ootd-board-caption {
    align-items: flex-start;
    flex-direction: column;
  }

  .ootd-board-status {
    justify-content: flex-start;
    text-align: left;
  }

  .closet-panel {
    padding-top: 24px;
  }

  .closet-score {
    margin-top: 25px;
  }
}

@media (max-width: 620px) {
  .ootd-section {
    scroll-margin-top: 8rem;
  }

  .recommendation-view {
    min-height: calc(100vh - 112px);
  }

  .recommendation-canvas {
    width: calc(100% - 32px);
    padding-top: 30px;
    padding-bottom: 64px;
  }

  .hero-overline {
    align-items: flex-start;
    flex-direction: column;
    gap: 8px;
  }

  .hero-grid {
    padding-top: 40px;
  }

  .hero-copy h1 {
    font-size: 42px;
  }

  .hero-actions {
    display: grid;
    grid-template-columns: 1fr;
  }

  .hero-primary {
    width: 100%;
  }

  .overview-strip {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .overview-item,
  .overview-source {
    padding: 18px 14px 19px 0;
  }

  .overview-item:nth-child(2n),
  .overview-source {
    border-left: 1px solid var(--rec-line);
    padding-left: 14px;
  }

  .overview-item:nth-child(3) {
    border-top: 1px solid var(--rec-line);
  }

  .overview-source {
    grid-column: 1 / -1;
    border-left: 0;
    padding-left: 0;
  }

  .overview-source p {
    display: block;
    margin: 6px 0 0;
  }

  .section-header,
  .panel-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .ootd-controls {
    align-self: flex-end;
  }

  .ootd-workspace {
    gap: 9px;
    margin-top: 24px;
  }

  .ootd-board-wrap {
    margin-top: 24px;
  }

  .ootd-board {
    display: flex;
    min-height: 0;
    flex-direction: column;
    gap: 12px;
    padding: 22px 14px 14px;
  }

  .ootd-board-heading {
    position: static;
    align-items: flex-start;
    flex-direction: column;
    gap: 6px;
    padding: 0 4px 4px;
  }

  .ootd-board-model,
  .ootd-board-model-visual {
    min-height: 430px;
  }

  .ootd-board-item {
    min-height: 240px;
  }

  .ootd-board-caption,
  .ootd-board-status {
    align-items: stretch;
    flex-direction: column;
  }

  .ootd-board-status {
    gap: 10px;
  }

  .ootd-visual-action {
    width: 100%;
    justify-content: center;
  }

  .ootd-board-wrap > .ootd-rationale {
    padding: 16px;
  }

  .ootd-board-wrap > .ootd-rationale dl > div {
    grid-template-columns: 70px minmax(0, 1fr);
  }

  .ootd-stack-panel {
    padding: 18px 16px 15px;
  }

  .ootd-stack-stage {
    width: min(100%, 324px);
    height: 500px;
  }

  .ootd-detail {
    min-height: 0;
    padding: 23px 20px 22px;
  }

  .ootd-detail-kicker {
    margin-top: 32px;
  }

  .ootd-detail h3 {
    margin-top: 24px;
    font-size: 29px;
  }

  .ootd-item-list,
  .generated-items,
  .generated-content-grid {
    grid-template-columns: 1fr;
  }

  .ootd-detail-actions {
    align-items: stretch;
    flex-direction: column;
  }

  .ootd-detail-actions button {
    width: 100%;
  }

  .generated-reason {
    border-top: 1px solid var(--rec-line);
    border-left: 0;
    padding: 16px 0 0;
  }

  .generated-card {
    padding: 22px 18px 19px;
  }

  .generated-actions,
  .rating-control {
    align-items: stretch;
    flex-direction: column;
  }

  .save-button {
    width: 100%;
  }

  .trend-signal-content {
    grid-template-columns: 112px minmax(0, 1fr);
  }

  .trend-signal-content > img {
    height: 112px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .ootd-stack-card,
  .closet-progress span {
    transition: none;
  }
}
</style>
