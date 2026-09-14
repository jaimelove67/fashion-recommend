<script setup>
import { computed, ref, watch } from 'vue'
import {
  ArrowRight,
  Bookmark,
  CalendarDays,
  Check,
  ChevronLeft,
  ChevronRight,
  CloudSun,
  Compass,
  Flame,
  LoaderCircle,
  MessageCircle,
  Shirt,
  Sparkles,
  Star,
  SunMedium,
  Thermometer,
  Umbrella,
  Wind
} from '@lucide/vue'
import Stack from '../components/Stack.vue'
import RecommendationAssistant from '../components/RecommendationAssistant.vue'

const props = defineProps({
  app: { type: Object, required: true }
})

const brokenImages = ref(new Set())
const dailyOffset = ref(0)
const selectedLookId = ref(null)
const assistantRef = ref(null)
const today = new Date()
const weekdayNames = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']

const fallbackImages = [
  '/assets/look-tailoring.jpg',
  '/assets/look-color.jpg',
  '/assets/look-urban.jpg'
]

const planTemplates = [
  {
    dayName: '周一',
    occasion: '通勤 · 清爽利落',
    weatherLabel: '晴朗',
    temperature: '24°',
    low: '17°',
    score: 93,
    palette: '暖白 / 石墨灰',
    note: '柔和的中性色适合工作日，轮廓利落，穿着也不拘束。',
    icon: SunMedium
  },
  {
    dayName: '周二',
    occasion: '客户会面 · 稳妥得体',
    weatherLabel: '多云',
    temperature: '22°',
    low: '16°',
    score: 91,
    palette: '雾蓝 / 米白',
    note: '蓝灰色压住明度，利落的版型看起来正式，但不会太用力。',
    icon: CloudSun
  },
  {
    dayName: '周三',
    occasion: '城市漫步 · 轻松分层',
    weatherLabel: '晴朗',
    temperature: '25°',
    low: '18°',
    score: 90,
    palette: '黑色 / 冷灰白',
    note: '用常穿的一件做主角，颜色保持简单，走一天也轻便。',
    icon: SunMedium
  },
  {
    dayName: '周四',
    occasion: '通勤 · 稳妥正式',
    weatherLabel: '小雨',
    temperature: '20°',
    low: '15°',
    score: 90,
    palette: '雾蓝 / 炭灰',
    note: '带一件轻薄外层应对温差，鞋子选防滑、走路不累的款式。',
    icon: Umbrella
  },
  {
    dayName: '周五',
    occasion: '晚间约会 · 舒展得体',
    weatherLabel: '多云',
    temperature: '23°',
    low: '17°',
    score: 92,
    palette: '炭灰 / 暖白',
    note: '深色打底更收比例，再用柔软材质放松整体感觉。',
    icon: CloudSun
  },
  {
    dayName: '周六',
    occasion: '周末出行 · 轻装',
    weatherLabel: '晴朗',
    temperature: '26°',
    low: '19°',
    score: 88,
    palette: '橄榄绿 / 白色',
    note: '少穿一层，把方便活动的衣物留给户外行程。',
    icon: SunMedium
  },
  {
    dayName: '周日',
    occasion: '休息日 · 随意出门',
    weatherLabel: '晴间云',
    temperature: '24°',
    low: '18°',
    score: 89,
    palette: '米白 / 橄榄绿',
    note: '用熟悉的颜色配一双舒服的鞋，出门不必花太多时间。',
    icon: Wind
  }
]

const dailyLookModes = [
  {
    title: '清爽通勤',
    subtitle: '轻正式 · 低饱和',
    palette: '暖白 / 石墨灰',
    note: '轻薄外层应对全天温差，先把轮廓穿稳。',
    fitLabel: '首选'
  },
  {
    title: '柔和层次',
    subtitle: '松弛感 · 有分寸',
    palette: '雾蓝 / 米白',
    note: '柔软材质靠近脸部，上下比例保持利落。',
    fitLabel: '轻正式'
  },
  {
    title: '城市轻叠穿',
    subtitle: '利落感 · 轻叠穿',
    palette: '炭灰 / 冷白',
    note: '用常穿衣物做重点，活动时不显臃肿。',
    fitLabel: '有层次'
  },
  {
    title: '深色小对比',
    subtitle: '深色基底 · 小亮点',
    palette: '墨黑 / 橄榄绿',
    note: '主色占大部分面积，鞋履或配饰只加一处亮色。',
    fitLabel: '低负担'
  },
  {
    title: '轻松周末',
    subtitle: '舒适感 · 易行动',
    palette: '米白 / 棕灰',
    note: '少一层、更好活动，最后一套留给舒适感。',
    fitLabel: '松弛版'
  }
]

const state = computed(() => props.app.state || {})
const wardrobe = computed(() => state.value.wardrobe || [])
const history = computed(() => state.value.history || [])
const trendPreview = computed(() => state.value.trends?.[0] || null)
const profile = computed(() => state.value.profile || null)
const actualWeather = computed(() => state.value.weather || null)

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

function itemKey(item) {
  return item?.id || item?.name || 'unknown'
}

function categoryMatches(item, category) {
  const value = item?.category || ''
  return value.includes(category) || (category === '鞋履' && value.includes('鞋'))
}

function itemsForLook(variantIndex, dayIndex, source) {
  if (source?.items?.length) return source.items.slice(0, 4)
  if (!wardrobe.value.length) return []

  const categoryOrder = variantIndex % 2
    ? ['外套', '上装', '下装', '鞋履', '配饰']
    : ['上装', '下装', '外套', '鞋履', '配饰']
  const picked = []
  const pickedKeys = new Set()
  for (const category of categoryOrder) {
    const candidates = wardrobe.value.filter((item) => categoryMatches(item, category) && !pickedKeys.has(itemKey(item)))
    const candidate = candidates.length
      ? candidates[(variantIndex + dayIndex) % candidates.length]
      : null
    if (candidate) {
      picked.push(candidate)
      pickedKeys.add(itemKey(candidate))
    }
  }
  for (let offset = 0; offset < wardrobe.value.length; offset += 1) {
    if (picked.length >= 4) break
    const item = wardrobe.value[(variantIndex + dayIndex + offset) % wardrobe.value.length]
    if (!pickedKeys.has(itemKey(item))) {
      picked.push(item)
      pickedKeys.add(itemKey(item))
    }
  }
  return picked.slice(0, 4)
}

function addDays(date, amount) {
  const result = new Date(date.getFullYear(), date.getMonth(), date.getDate())
  result.setDate(result.getDate() + amount)
  return result
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

const dailyDate = computed(() => addDays(today, dailyOffset.value))
const dailyDateKey = computed(() => dateSlug(dailyDate.value))
const dailyDayIndex = computed(() => {
  const todayIndex = today.getDay() === 0 ? 6 : today.getDay() - 1
  return (todayIndex + dailyOffset.value + planTemplates.length) % planTemplates.length
})
const dailyTemplate = computed(() => planTemplates[dailyDayIndex.value])
const dailyDateLabel = computed(() => dateLabel(dailyDate.value))
const dailyDayName = computed(() => weekdayNames[dailyDate.value.getDay()])
const dailyDateHeading = computed(() => `${dailyDate.value.getMonth() + 1}月${dailyDate.value.getDate()}日 · ${dailyDayName.value}`)
const dailyWeather = computed(() => {
  if (dailyOffset.value === 0 && actualWeather.value) {
    return {
      label: props.app.weatherConditionLabel(actualWeather.value.weatherCode),
      temperature: `${Number(actualWeather.value.temperatureC).toFixed(0)}°`,
      low: `${Number(actualWeather.value.apparentTemperatureC).toFixed(0)}°`
    }
  }
  return {
    label: dailyTemplate.value.weatherLabel,
    temperature: dailyTemplate.value.temperature,
    low: dailyTemplate.value.low
  }
})

const weatherBriefCity = computed(() => {
  const city = state.value.recommendationForm?.city || actualWeather.value?.city || '你的常用城市'
  return dailyOffset.value === 0 ? city : `${city} · 计划参考`
})

const weatherBriefDetail = computed(() => {
  if (dailyOffset.value === 0 && actualWeather.value) {
    return `体感 ${Number(actualWeather.value.apparentTemperatureC).toFixed(0)}° · 风速 ${Number(actualWeather.value.windSpeedKmh).toFixed(0)} km/h`
  }
  return dailyOffset.value === 0 ? '填好城市后，这里会显示天气' : '非当天日期使用搭配计划参考，不代表实时预报'
})

const dailyLooks = computed(() => dailyLookModes.map((mode, lookIndex) => {
  const source = recommendationPool.value[lookIndex] || null
  const items = itemsForLook(lookIndex, dailyDayIndex.value, source)
  const heroItem = items.find((item) => item?.imageUrl) || items[0] || null
  const palette = uniqueColors(items).join(' / ') || mode.palette
  const occasion = dailyTemplate.value.occasion.split('·')[0].trim()
  return {
    id: `ootd-${dailyDateKey.value}-${lookIndex + 1}`,
    img: imageSource(heroItem, `ootd-${dailyDateKey.value}`, lookIndex),
    lookIndex,
    lookLabel: `LOOK ${String(lookIndex + 1).padStart(2, '0')}`,
    title: `${occasion} · ${mode.title}`,
    subtitle: source?.summary || mode.subtitle,
    occasion,
    palette,
    note: source?.reason || mode.note,
    items,
    heroItem,
    source,
    fitLabel: mode.fitLabel,
    weatherLabel: dailyWeather.value.label,
    temperature: dailyWeather.value.temperature,
    low: dailyWeather.value.low,
    city: source?.city || actualWeather.value?.city || state.value.recommendationForm.city || '当前城市',
    dayName: dailyDayName.value,
    dateLabel: dailyDateLabel.value,
    isToday: dailyOffset.value === 0,
    isGenerated: Boolean(source?.id),
    isInspiration: !source?.id
  }
}))

const stackLooks = computed(() => [
  ...dailyLooks.value.filter((look) => look.isInspiration),
  ...dailyLooks.value.filter((look) => !look.isInspiration)
])
const selectedLook = computed(() => (
  dailyLooks.value.find((look) => look.id === selectedLookId.value)
  || dailyLooks.value.find((look) => !look.isInspiration)
  || dailyLooks.value[0]
  || null
))
const dailyLookCount = computed(() => dailyLooks.value.length)
const realLookCount = computed(() => dailyLooks.value.filter((look) => !look.isInspiration).length)
const hasRealLooks = computed(() => realLookCount.value > 0)
const coveragePercent = computed(() => {
  const total = props.app.wardrobeStats.total
  if (!total) return null
  return Math.min(100, Math.round((props.app.recommendationStats.coveredItems / total) * 100))
})

const profileTags = computed(() => {
  const tags = profile.value?.styleTags || []
  return tags.length ? tags.slice(0, 3) : ['低饱和', '利落轮廓', '日常通勤']
})

const heroCondition = computed(() => dailyWeather.value.label || '晴朗')
const heroTemperature = computed(() => dailyWeather.value.temperature || '--')

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
  return fallbackImages[index % fallbackImages.length]
}

function imageFailed(prefix, item, index = 0) {
  brokenImages.value = new Set([...brokenImages.value, imageKey(prefix, item, index)])
}

function handleLookChange(payload) {
  if (payload?.card?.id) selectedLookId.value = payload.card.id
}

function shiftDay(step) {
  dailyOffset.value += step
}

function jumpToToday() {
  dailyOffset.value = 0
}

function openAssistant(plan = null, prompt = '') {
  assistantRef.value?.open(plan, prompt)
}

watch(dailyLooks, (looks) => {
  const selected = looks.find((look) => look.id === selectedLookId.value)
  if (!selected || (selected.isInspiration && looks.some((look) => !look.isInspiration))) {
    selectedLookId.value = looks.find((look) => !look.isInspiration)?.id || looks[0]?.id || null
  }
}, { immediate: true })
</script>

<template>
  <section class="recommendation-view">
    <div class="recommendation-canvas">
      <header class="recommendation-hero">
        <div class="hero-overline">
          <span><Sparkles :size="14" aria-hidden="true" />DAILY EDIT / 今日搭配</span>
          <span class="hero-overline-date">{{ dailyDateHeading }}</span>
        </div>

        <div class="hero-grid">
          <div class="hero-copy">
            <div class="hero-index"><span>01</span><span>今天穿哪一套？</span></div>
            <h1 v-if="hasRealLooks">今天有 {{ realLookCount }} 套衣橱搭配可选。</h1>
            <h1 v-else>今天还没想好穿什么？</h1>
            <p v-if="hasRealLooks" class="hero-lead">天气、场合和衣橱已经放在一起。先看看今天的搭配，不确定时也可以直接问知己。</p>
            <p v-else class="hero-lead">告诉知己去哪里、做什么，或想要什么感觉。我会结合天气和衣橱，帮你搭出一套可以直接穿的组合。</p>
            <div class="hero-actions">
              <button class="hero-primary" type="button" @click="openAssistant()">
                <MessageCircle :size="17" aria-hidden="true" />问问知己<ArrowRight :size="17" aria-hidden="true" />
              </button>
              <button class="hero-secondary" type="button" @click="jumpToToday">
                <CalendarDays :size="16" aria-hidden="true" />回到今天
              </button>
            </div>
            <div class="hero-footnote">
              <span v-for="tag in profileTags" :key="tag">#{{ tag }}</span>
              <span class="hero-footnote-muted">{{ profile?.displayName ? `${profile.displayName}的今日搭配` : '按你的衣橱生成' }}</span>
            </div>
          </div>

          <aside class="weather-brief" aria-label="今天的天气和穿衣提示">
            <div class="weather-brief-top">
              <div>
                <span class="brief-label">{{ dailyOffset === 0 ? 'TODAY / 今日' : 'DAY / 当日参考' }}</span>
                <strong>{{ heroCondition }}</strong>
              </div>
              <component :is="dailyTemplate?.icon || CloudSun" :size="24" aria-hidden="true" />
            </div>
            <div class="weather-reading">
              <strong>{{ heroTemperature }}</strong>
              <div>
                <span>{{ weatherBriefCity }}</span>
                <small>{{ weatherBriefDetail }}</small>
              </div>
            </div>
            <div class="weather-note">
              <Thermometer :size="15" aria-hidden="true" />
              <span>{{ selectedLook?.note || dailyTemplate.note }}</span>
            </div>
            <button class="weather-link" type="button" @click="openAssistant()">问问知己修改条件 <ArrowRight :size="14" /></button>
          </aside>
        </div>
      </header>

      <section class="overview-strip" aria-label="今日搭配概览">
        <div class="overview-item">
          <span>{{ hasRealLooks ? '衣橱搭配' : '灵感方向' }}</span>
          <strong>{{ hasRealLooks ? realLookCount : dailyLookCount }} <small>{{ hasRealLooks ? '套' : '个' }}</small></strong>
          <em>{{ hasRealLooks ? '来自你的衣橱' : '生成后才会落到衣橱' }}</em>
        </div>
        <div class="overview-item">
          <span>可用衣物</span>
          <strong>{{ props.app.wardrobeStats.ready }} <small>件</small></strong>
          <em>{{ props.app.wardrobeStats.review ? `${props.app.wardrobeStats.review} 件衣物待补充` : (props.app.wardrobeStats.ready ? '衣物信息齐全' : '至少添加两类衣物') }}</em>
        </div>
        <div class="overview-item overview-item-accent">
          <span>风格线索</span>
          <strong class="overview-style-value">{{ profileTags[0] || '待建立' }}</strong>
          <em>{{ profileTags.slice(1).join(' · ') || '完善档案后更精准' }}</em>
        </div>
        <div class="overview-source">
          <span>推荐依据</span>
          <strong>天气 × 衣橱 × 场合</strong>
          <p>问问知己时，会把这些信息一起考虑。</p>
        </div>
      </section>

      <section class="ootd-section" aria-labelledby="ootd-title">
        <header class="section-header ootd-header">
          <div>
            <div class="section-number">02 / DAILY OOTD</div>
            <h2 id="ootd-title">今天的搭配</h2>
            <p>{{ dailyDateHeading }} · {{ dailyWeather.label }} {{ dailyWeather.temperature }} · {{ hasRealLooks ? `${realLookCount} 套来自衣橱` : `${dailyLookCount} 个灵感方向` }}</p>
          </div>
          <div class="ootd-controls">
            <button class="round-control" type="button" aria-label="前一天" title="前一天" @click="shiftDay(-1)"><ChevronLeft :size="17" /></button>
            <button class="ootd-today" type="button" @click="jumpToToday">{{ dailyOffset === 0 ? '今天' : '回到今天' }}</button>
            <button class="round-control" type="button" aria-label="后一天" title="后一天" @click="shiftDay(1)"><ChevronRight :size="17" /></button>
          </div>
        </header>

        <div class="ootd-workspace">
          <div class="ootd-stack-panel">
            <div class="ootd-stack-stage">
              <Stack
                :key="dailyDateKey"
                :cards-data="stackLooks"
                :random-rotation="true"
                :sensitivity="150"
                :send-to-back-on-click="true"
                :mobile-click-only="true"
                @change="handleLookChange"
              >
                <template #default="{ card }">
                  <article class="ootd-stack-card">
                    <img
                      :src="card.img"
                      :alt="`${card.dayName} ${card.title}`"
                      draggable="false"
                      @error="imageFailed(`ootd-${dailyDateKey}`, card.heroItem, card.lookIndex)"
                    />
                  </article>
                </template>
              </Stack>
            </div>
          </div>

          <article v-if="selectedLook" class="ootd-detail" role="tabpanel" :aria-label="`${selectedLook.dayName} ${selectedLook.title}`">
            <div class="ootd-detail-topline"><span>{{ selectedLook.dayName }} / {{ selectedLook.dateLabel }}</span><strong>{{ selectedLook.weatherLabel }} {{ selectedLook.temperature }} · {{ selectedLook.low }}</strong></div>
            <div class="ootd-detail-kicker"><span>{{ selectedLook.lookLabel }} / SELECTED LOOK</span><span class="ootd-source-state" :class="{ inspiration: selectedLook.isInspiration }">{{ selectedLook.isInspiration ? '灵感方向' : '来自衣橱' }}</span><span class="ootd-fit-label">{{ selectedLook.fitLabel }}</span></div>
            <h3>{{ selectedLook.title }}</h3>
            <p class="ootd-detail-lead">{{ selectedLook.note }}</p>
            <div class="ootd-detail-palette">
              <span>COLOR EDIT</span>
              <strong>{{ selectedLook.palette }}</strong>
            </div>
            <div class="ootd-items-heading">
              <span>LOOK CONTENT</span>
              <strong>{{ selectedLook.items.length ? `${selectedLook.items.length} 件衣物` : '衣橱中还没有可用衣物' }}</strong>
            </div>
            <ul v-if="selectedLook.items.length" class="ootd-item-list" aria-label="这套搭配包含的衣物">
              <li v-for="(item, itemIndex) in selectedLook.items" :key="`${itemKey(item)}-${itemIndex}`">
                <div class="ootd-item-image">
                  <img
                    :src="imageSource(item, `ootd-detail-${dailyDateKey}`, itemIndex)"
                    :alt="item.name || item.category || '搭配衣物'"
                    @error="imageFailed(`ootd-detail-${dailyDateKey}`, item, itemIndex)"
                  />
                </div>
                <div class="ootd-item-copy"><span>{{ String(itemIndex + 1).padStart(2, '0') }} / {{ item.category || '衣物' }}</span><strong>{{ item.name || item.style || '衣橱衣物' }}</strong><small>{{ item.style || item.color || '已加入这套搭配' }}</small></div>
              </li>
            </ul>
            <div v-else class="ootd-empty"><Shirt :size="20" aria-hidden="true" /><span>添加至少两类衣物后，这里会换成你衣橱里的搭配。</span></div>
            <div class="ootd-editor-note"><Compass :size="18" aria-hidden="true" /><div><span>EDITOR'S NOTE</span><p>{{ selectedLook.note }}</p></div></div>
            <div class="ootd-detail-actions">
              <button class="detail-secondary" type="button" @click="openAssistant(selectedLook)">调整条件</button>
              <button class="detail-primary" type="button" @click="openAssistant(selectedLook, '参考这套搭配，再给我一套相近但不同的组合')"><Sparkles :size="15" />生成类似搭配</button>
            </div>
          </article>
        </div>
      </section>

      <section class="workbench-grid assistant-workbench" aria-label="AI 穿搭助手与衣橱状态">
        <RecommendationAssistant
          ref="assistantRef"
          :app="props.app"
          :selected-look="selectedLook"
          :actual-weather="actualWeather"
          :profile-tags="profileTags"
        />

        <aside class="closet-panel">
          <header class="panel-header compact">
            <div>
              <div class="section-number">04 / CLOSET SIGNAL</div>
              <h2>衣橱使用情况</h2>
            </div>
            <Shirt :size="19" aria-hidden="true" />
          </header>
          <div class="closet-score">
            <strong>{{ coveragePercent === null ? '--' : `${coveragePercent}%` }}</strong>
            <span>已有衣物参与推荐</span>
          </div>
          <div class="closet-progress" role="progressbar" :aria-valuenow="coveragePercent || 0" aria-valuemin="0" aria-valuemax="100" aria-label="衣橱参与推荐的比例"><span :style="{ transform: `scaleX(${(coveragePercent || 0) / 100})` }"></span></div>
          <div class="closet-facts">
            <div><span>衣橱衣物数</span><strong>{{ props.app.wardrobeStats.total }}</strong></div>
            <div><span>历史搭配</span><strong>{{ props.app.recommendationStats.total }}</strong></div>
          </div>
          <div class="closet-suggestion"><Check :size="15" aria-hidden="true" /><span>{{ props.app.wardrobeStats.review ? '先补充待确认衣物的信息，再生成搭配。' : (props.app.wardrobeStats.ready ? '衣物信息已齐，可以生成下一套搭配。' : '先添加至少两类衣物，再生成第一套搭配。') }}</span></div>
        </aside>
      </section>

      <section class="generated-section" aria-labelledby="generated-title">
        <header class="section-header">
          <div>
            <div class="section-number">05 / GENERATED LOOK</div>
            <h2 id="generated-title">刚生成的搭配</h2>
          </div>
          <span v-if="state.currentRecommendation" class="generated-time">{{ formatDate(state.currentRecommendation.generatedAt) }}</span>
        </header>

        <div v-if="state.generating" class="generated-state" aria-live="polite">
          <LoaderCircle class="spinning" :size="22" />
          <strong>正在从衣橱里挑选搭配</strong>
          <span>天气、场合和风格偏好会一起参考。</span>
        </div>

        <article v-else-if="state.currentRecommendation" class="generated-card">
          <div class="generated-card-topline">
            <div><span class="record-id">搭配 #{{ state.currentRecommendation.id }}</span><span>{{ state.currentRecommendation.occasion }}</span><span>{{ state.currentRecommendation.city }}</span></div>
            <span class="saved-state" :class="{ saved: state.currentRecommendation.saved }"><Bookmark :size="15" :fill="state.currentRecommendation.saved ? 'currentColor' : 'none'" />{{ state.currentRecommendation.saved ? '已收藏' : '未收藏' }}</span>
          </div>
          <div class="engine-line"><span><Sparkles :size="13" />{{ props.app.engineLabel(state.currentRecommendation.engine) }}</span><small v-if="state.currentRecommendation.generationAudit?.modelName">{{ state.currentRecommendation.generationAudit.modelName }}</small><small v-if="state.currentRecommendation.generationAudit?.fallbackReason">{{ props.app.fallbackReasonLabel(state.currentRecommendation.generationAudit.fallbackReason) }}</small></div>
          <h3>{{ state.currentRecommendation.summary }}</h3>
          <div class="generated-content-grid">
            <ul class="generated-items" aria-label="搭配中的衣物">
              <li v-for="(item, itemIndex) in state.currentRecommendation.items || []" :key="`${state.currentRecommendation.id}-${item.id || itemIndex}`">
                <div class="generated-item-image"><img :src="imageSource(item, 'result', itemIndex)" :alt="item.name" @error="imageFailed('result', item, itemIndex)" /></div>
                <div><span>{{ item.category }} · {{ item.color }}</span><strong>{{ item.name }}</strong><small>{{ item.style || '衣橱衣物' }}</small></div>
              </li>
            </ul>
            <div class="generated-reason"><span>这样搭的原因</span><p>{{ state.currentRecommendation.reason }}</p><div v-if="state.currentRecommendation.weather" class="generated-weather"><CloudSun :size="15" />{{ state.currentRecommendation.weather.temperatureC }}° · 体感 {{ state.currentRecommendation.weather.apparentTemperatureC }}° · {{ props.app.weatherConditionLabel(state.currentRecommendation.weather.weatherCode) }}</div></div>
          </div>
          <footer class="generated-actions">
            <div class="rating-control"><span>这套搭配对你有用吗？</span><div aria-label="搭配评分"><button v-for="rating in 5" :key="rating" type="button" :class="{ rated: state.currentRecommendation.feedback?.rating >= rating }" :disabled="state.feedbackSavingId === state.currentRecommendation.id" :aria-label="`${rating} 星评分`" @click="props.app.rateRecommendation(state.currentRecommendation, rating)"><Star :size="18" :fill="state.currentRecommendation.feedback?.rating >= rating ? 'currentColor' : 'none'" /></button></div></div>
            <button class="save-button" type="button" :class="{ saved: state.currentRecommendation.saved }" :disabled="state.saving || state.currentRecommendation.saved" @click="props.app.saveRecommendation()"><LoaderCircle v-if="state.saving" class="spinning" :size="16" /><Bookmark v-else :size="16" :fill="state.currentRecommendation.saved ? 'currentColor' : 'none'" />{{ state.currentRecommendation.saved ? '已保存' : '收藏这套搭配' }}</button>
          </footer>
        </article>

          <div v-else class="generated-empty">
          <div><Sparkles :size="19" aria-hidden="true" /><strong>还没有生成搭配</strong></div>
          <span>选择上面的日期，或问问知己生成一套搭配。</span>
          <button type="button" @click="openAssistant()"><MessageCircle :size="14" />问问知己 <ArrowRight :size="14" /></button>
        </div>
      </section>

      <section class="signal-grid" aria-label="搭配参考">
        <article class="signal-panel trend-signal">
          <header class="panel-header compact">
              <div><div class="section-number">06 / TREND SIGNAL</div><h2>本周趋势参考</h2></div>
            <Flame :size="19" aria-hidden="true" />
          </header>
          <div v-if="trendPreview" class="trend-signal-content">
            <img :src="trendPreview.imageUrl || fallbackImages[1]" :alt="trendPreview.title" />
            <div><span>{{ trendPreview.platform || '趋势样本' }} · 热度 {{ trendPreview.heatScore ?? '—' }}</span><strong>{{ trendPreview.title }}</strong><p>{{ (trendPreview.topicTags || []).slice(0, 3).join(' · ') || '先从趋势里挑一个方向' }}</p><button type="button" @click="props.app.selectView('trend')">查看全部趋势 <ArrowRight :size="14" /></button></div>
          </div>
          <div v-else class="signal-empty"><Flame :size="18" />加载趋势数据后，这里会显示一条参考。</div>
        </article>
        <article class="signal-panel note-signal">
          <header class="panel-header compact"><div><div class="section-number">07 / NOTES</div><h2>搭配小提示</h2></div><Wind :size="19" aria-hidden="true" /></header>
          <ul>
            <li><span>01</span><p><strong>让常穿衣物多出现几次</strong>同一件衣物也能搭出不同组合。</p></li>
            <li><span>02</span><p><strong>先把主色定下来</strong>颜色稳定后，再决定要不要加配饰。</p></li>
            <li><span>03</span><p><strong>给天气留一点余量</strong>温差大时，带一件轻薄外层更方便。</p></li>
          </ul>
        </article>
      </section>
    </div>
  </section>
</template>

<style scoped>
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
.weather-note,
.weather-link,
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
  font-size: 10px;
  font-weight: 700;
  letter-spacing: .13em;
  text-transform: uppercase;
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
.hero-secondary,
.detail-primary,
.detail-secondary,
.save-button,
.generated-empty button {
  min-height: 42px;
  gap: 8px;
  padding: 10px 16px;
  font-size: 12px;
  font-weight: 700;
}

.hero-primary,
.detail-primary,
.save-button {
  display: inline-flex;
  justify-content: center;
  color: var(--rec-paper-strong);
  background: var(--rec-dark);
}

.hero-primary:hover,
.detail-primary:hover,
.save-button:hover:not(:disabled) {
  background: var(--rec-accent);
  transform: translateY(-1px);
}

.hero-secondary,
.detail-secondary,
.generated-empty button {
  display: inline-flex;
  justify-content: center;
  border: 1px solid var(--rec-line-dark);
  color: var(--rec-ink);
  background: transparent;
}

.hero-secondary:hover,
.detail-secondary:hover,
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
.weather-reading small,
.weather-note,
.weather-link,
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
.generated-weather,
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

.weather-reading small {
  line-height: 1.45;
}

.weather-note {
  align-items: flex-start;
  gap: 8px;
  margin-top: 26px;
  line-height: 1.6;
}

.weather-note svg {
  flex: 0 0 auto;
  color: var(--rec-accent);
}

.weather-link {
  gap: 5px;
  margin-top: 17px;
  padding: 0;
  color: var(--rec-ink);
  background: transparent;
  font-size: 11px;
  font-weight: 700;
}

.weather-link:hover {
  color: var(--rec-accent);
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
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: .08em;
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
  width: 100%;
  height: 100%;
  overflow: hidden;
  border: 1px solid rgba(255, 254, 250, .45);
  border-radius: 18px;
  background: var(--rec-accent-soft);
  box-shadow: 0 18px 38px rgba(32, 43, 36, .16);
}

.ootd-stack-card > img {
  display: block;
  width: 100%;
  height: 100%;
  min-width: 100%;
  min-height: 100%;
  object-fit: cover;
  object-position: center;
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
  grid-template-columns: 56px minmax(0, 1fr);
  align-items: center;
  gap: 10px;
  min-width: 0;
  border-bottom: 1px solid var(--rec-line);
  padding-bottom: 9px;
}

.ootd-item-image {
  width: 56px;
  height: 64px;
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

.workbench-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) minmax(280px, .65fr);
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

.generated-weather {
  display: flex;
  align-items: center;
  gap: 5px;
  margin-top: 16px;
}

.generated-weather svg {
  color: var(--rec-accent);
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

  .closet-panel {
    padding-top: 24px;
  }

  .closet-score {
    margin-top: 25px;
  }
}

@media (max-width: 620px) {
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

  .hero-primary,
  .hero-secondary {
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
