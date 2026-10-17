<script setup>
import { computed } from 'vue'
import { ArrowRight, ArrowUpRight, Bookmark, Clock3, CloudSun, LoaderCircle, Shirt, Sparkles, TrendingUp, UserRound } from '@lucide/vue'
import { trendPlatformName } from '../utils/trendSources.js'

const props = defineProps({
  app: { type: Object, required: true },
  guest: { type: Boolean, default: false }
})
const emit = defineEmits(['request-login', 'browse-examples'])

const fallbackLooks = ['/assets/look-urban.jpg', '/assets/look-tailoring.jpg', '/assets/look-color.jpg']
const heroFrames = [
  { src: '/assets/home-look-commute.webp', alt: '米色风衣搭配鼠尾草绿针织衫与奶油色长裤的日常通勤穿搭' },
  { src: '/assets/home-look-casual.webp', alt: '鼠尾草绿衬衫外套搭配白色上衣与深灰长裤的休闲穿搭' },
  { src: '/assets/home-look-details.webp', alt: '米白衬衫、绿色针织开衫与棕色皮具组成的日常衣橱搭配' }
]
const guestLooks = [
  { id: 'commute', title: '通勤，也可以松弛有度', occasion: '日常通勤', frame: heroFrames[0],
    tags: ['米色风衣', '绿色针织', '奶油色长裤'],
    description: '用柔和的同色系打底，一点绿色提亮，让工作日的搭配利落又轻松。' },
  { id: 'casual', title: '周末，轻松出门就好', occasion: '周末休闲', frame: heroFrames[1],
    tags: ['衬衫外套', '白色上衣', '深灰长裤'],
    description: '宽松外套搭配简洁内搭，用深浅对比穿出层次，适合散步与朋友小聚。' },
  { id: 'layering', title: '基础款，穿出温柔层次', occasion: '日常叠穿', frame: heroFrames[2],
    tags: ['米白衬衫', '针织开衫', '棕色配饰'],
    description: '衬衫与开衫叠穿，棕色配饰呼应柔和色调，让熟悉的单品多一种组合。' }
]
const state = computed(() => props.app.state || {})
const trends = computed(() => state.value.trends || [])
const profile = computed(() => state.value.profile)
const trendMeta = computed(() => state.value.trendMeta || {})
const trendStats = computed(() => props.app.trendStats || {})
const scoreLabel = computed(() => trendMeta.value.scoreLabel || '热度')
const wardrobeStats = computed(() => props.app.wardrobeStats || {})
const recommendationStats = computed(() => props.app.recommendationStats || {})
const trendPreviews = computed(() => trends.value.slice(0, 3))
const recentRecommendation = computed(() => state.value.history?.[0] || null)
const weatherSummary = computed(() => {
  const weather = state.value.weather
  if (!weather) return state.value.weatherLoading ? '正在获取天气…' : ''
  const temperature = weather.temperatureC == null ? NaN : Number(weather.temperatureC)
  const temperatureLabel = Number.isFinite(temperature) ? `${temperature.toFixed(0)}°` : ''
  const condition = typeof props.app.weatherConditionLabel === 'function'
    ? props.app.weatherConditionLabel(weather.weatherCode)
    : '今日天气'
  return [temperatureLabel, condition].filter(Boolean).join(' · ') || '今日天气'
})
const homeGuide = computed(() => props.guest ? [
  { id: 'recommend', label: '穿搭推荐', icon: Sparkles, action: '登录体验',
    note: '结合场合、天气与已有衣物，找到适合今天的穿搭。' },
  { id: 'wardrobe', label: '我的衣橱', icon: Shirt, action: '建立我的衣橱',
    note: '上传衣物照片，整理自己的单品，发现更多搭配可能。' },
  { id: 'history', label: '搭配记录', icon: Clock3, action: '记录我的搭配',
    note: '保存喜欢的方案，回看搭配与反馈，积累自己的风格。' },
  { id: 'profile', label: '个人形象档案', icon: UserRound, action: '完善我的档案',
    note: '补充形象资料与风格偏好，让穿搭建议更贴合你。' }
] : [
  {
    id: 'recommend',
    view: 'recommend',
    label: '穿搭推荐',
    note: wardrobeStats.value.ready ? `${wardrobeStats.value.ready} 件衣物已就绪` : '需上装与下装，或连体装与配套单品',
    icon: Sparkles,
    action: '查看推荐'
  },
  {
    id: 'wardrobe',
    view: 'wardrobe',
    label: '我的衣橱',
    note: wardrobeStats.value.total ? `${wardrobeStats.value.total} 件衣物` : '暂无衣物，请添加单品',
    icon: Shirt,
    action: '查看衣橱'
  },
  {
    id: 'recent',
    view: 'history',
    label: '最近搭配',
    note: recentRecommendation.value
      ? [recentRecommendation.value.occasion, recentRecommendation.value.city, `${recentRecommendation.value.items?.length || 0} 件单品`].filter(Boolean).join(' · ')
      : (recommendationStats.value.total ? `${recommendationStats.value.total} 条记录` : '暂无搭配记录'),
    icon: Clock3,
    action: '查看记录'
  },
  {
    id: 'profile',
    view: 'profile',
    label: '个人形象档案',
    note: profile.value ? '档案资料已保存' : '完善资料与个人偏好',
    icon: UserRound,
    action: '完善档案'
  }
])

const statRail = computed(() => [
  {
    label: '我的衣橱',
    value: state.value.wardrobeLoading ? '—' : wardrobeStats.value.total ?? 0,
    unit: '件',
    note: `近 7 天新增 ${wardrobeStats.value.weeklyAdded ?? 0} 件`,
    icon: Shirt
  },
  {
    label: '搭配记录',
    value: state.value.historyLoading ? '—' : recommendationStats.value.total ?? 0,
    unit: '条',
    note: `已收藏 ${recommendationStats.value.saved ?? 0} 条`,
    icon: Bookmark
  },
  {
    label: '鉴赏收录',
    value: state.value.trendsLoading ? '—' : trendStats.value.count ?? 0,
    unit: '条',
    note: trendStats.value.count ? `${new Set(trends.value.map(item => item.platform)).size} 个来源 · 各来源独立排序` : '暂无穿搭内容',
    icon: TrendingUp
  }
])

function formatUpdateTime(value) {
  if (!value) return '暂无更新时间'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '暂无更新时间'
  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
    timeZone: 'Asia/Shanghai'
  })
}

function handleImageError(event, fallback) {
  const image = event.currentTarget
  if (image.dataset.fallbackApplied) {
    image.style.visibility = 'hidden'
    return
  }
  image.dataset.fallbackApplied = 'true'
  image.src = fallback
}

function goToTrend(item) {
  if (item?.id) state.value.selectedTrendId = item.id
  props.app.selectView('trend')
}

function navigate(view) {
  if (props.guest) emit('request-login', view)
  else props.app.selectView(view)
}

function browseTrends() {
  if (props.guest) emit('browse-examples')
  else props.app.selectView('trend')
}
</script>

<template>
  <div class="home-page">
    <section class="editorial-hero" aria-labelledby="home-title">
      <div class="hero-copy">
        <h1 id="home-title">今天穿什么，<span>从你的衣橱开始。</span></h1>
        <p v-if="guest" class="guest-description">把已有衣服穿出新意。知己结合你的衣橱、场合与天气，帮你找到适合自己的搭配。</p>
        <div class="hero-actions">
          <button type="button" class="primary-action" :disabled="guest && state.authPhase === 'checking'" @click="navigate('recommend')">
            <Sparkles :size="17" aria-hidden="true" />{{ guest ? '开始我的穿搭' : '创建穿搭方案' }}<ArrowRight :size="17" aria-hidden="true" />
          </button>
          <button type="button" class="secondary-action" @click="browseTrends">
            {{ guest ? '先看穿搭示例' : '浏览穿搭鉴赏' }}<ArrowUpRight :size="17" aria-hidden="true" />
          </button>
        </div>
        <p v-if="guest" class="guest-hint">无需登录，先逛逛穿搭灵感。</p>
        <button v-else type="button" class="hero-weather" aria-label="查看今日天气与穿搭建议" @click="app.selectView('recommend')">
          <span class="weather-icon"><CloudSun :size="22" aria-hidden="true" /></span>
          <span class="weather-copy">
            <strong>{{ state.weather?.city || '今日天气' }}<span v-if="weatherSummary">{{ weatherSummary }}</span></strong>
          </span>
          <ArrowUpRight :size="16" aria-hidden="true" />
        </button>
      </div>

      <figure class="hero-collage" aria-label="穿搭灵感参考">
        <div class="collage-grid">
          <div
            v-for="(frame, index) in heroFrames"
            :key="frame.src"
            class="collage-frame"
            :class="`frame-${index + 1}`"
          >
            <img
              :src="frame.src"
              :alt="frame.alt"
              @error="handleImageError($event, fallbackLooks[index])"
            />
          </div>
        </div>
        <div class="collage-actions">
          <button type="button" :aria-label="guest ? '浏览穿搭示例' : '探索穿搭鉴赏'" @click="browseTrends">
            <ArrowUpRight :size="20" aria-hidden="true" />
          </button>
        </div>
      </figure>
    </section>

    <section v-if="!guest" class="stat-rail" aria-label="衣橱与推荐概览">
      <article v-for="stat in statRail" :key="stat.label" class="stat-item">
        <span class="stat-icon"><component :is="stat.icon" :size="19" aria-hidden="true" /></span>
        <div class="stat-copy">
          <span>{{ stat.label }}</span>
          <em>{{ stat.note }}</em>
        </div>
        <p><strong>{{ stat.value }}</strong><small>{{ stat.unit }}</small></p>
      </article>
    </section>

    <section id="home-features" class="home-guide" aria-labelledby="home-guide-title">
      <header class="section-heading home-guide-heading">
        <div>
          <p>{{ guest ? '认识知己' : '日常管理' }}</p>
          <h2 id="home-guide-title">{{ guest ? '让衣橱里的每一件，都有新可能' : '你的穿搭工作台' }}</h2>
        </div>
        <span>{{ guest ? '登录后开启你的个人穿搭空间' : '从整理衣橱，到找到自己的风格' }}</span>
      </header>
      <div class="home-guide-grid">
        <button v-for="item in homeGuide" :key="item.id" type="button" class="home-guide-card" :disabled="guest && state.authPhase === 'checking'" @click="navigate(item.view || item.id)">
          <span class="home-guide-top">
            <span class="home-guide-icon"><component :is="item.icon" :size="20" aria-hidden="true" /></span>
            <ArrowUpRight :size="17" aria-hidden="true" />
          </span>
          <span class="home-guide-copy">
            <strong>{{ item.label }}</strong>
            <small>{{ item.note }}</small>
          </span>
          <span class="home-guide-action">{{ item.action }}<ArrowRight :size="14" aria-hidden="true" /></span>
        </button>
      </div>
    </section>

    <section v-if="guest" id="guest-inspiration" class="trend-section" aria-labelledby="guest-inspiration-title">
      <header class="section-heading">
        <div>
          <p>灵感发现</p>
          <h2 id="guest-inspiration-title">先找一点穿搭灵感</h2>
        </div>
      </header>
      <p class="trend-source">搭配示例 · 非个性化推荐</p>
      <div class="trend-grid">
        <article v-for="(look, index) in guestLooks" :key="look.id" class="trend-preview guest-look">
          <div class="preview-image">
            <img :src="look.frame.src" :alt="look.frame.alt" loading="lazy" @error="handleImageError($event, fallbackLooks[index])" />
          </div>
          <div class="preview-copy">
            <span class="preview-meta">{{ look.occasion }}</span>
            <strong>{{ look.title }}</strong>
            <span class="preview-tags">{{ look.tags.join(' / ') }}</span>
            <p class="guest-look-description">{{ look.description }}</p>
          </div>
        </article>
      </div>
      <div class="guest-start">
        <div>
          <h3>灵感有了，试试自己的衣橱？</h3>
          <p>登录后添加衣物，让知己为你搭一套。</p>
        </div>
        <button type="button" class="section-link" :disabled="state.authPhase === 'checking'" @click="navigate('recommend')">
          开启我的穿搭<ArrowRight :size="16" aria-hidden="true" />
        </button>
      </div>
    </section>

    <section v-else class="trend-section" aria-labelledby="home-trend-title">
      <header class="section-heading">
        <div>
          <p>灵感发现</p>
          <h2 id="home-trend-title">近期穿搭鉴赏</h2>
        </div>
        <button type="button" class="section-link" @click="app.selectView('trend')">
          查看全部鉴赏<ArrowRight :size="16" aria-hidden="true" />
        </button>
      </header>
      <p class="trend-source" :class="{ 'demo-label': trendMeta.demoMode }">
        <span>{{ trendMeta.demoMode ? '演示数据 · 非实时' : '已收录穿搭 · 各来源独立排序' }}</span>
        <span>{{ trendMeta.fetchedAt ? `最近更新 ${formatUpdateTime(trendMeta.fetchedAt)}` : '暂无更新时间' }}</span>
      </p>

      <div v-if="state.trendsLoading" class="section-state" role="status" aria-live="polite">
        <LoaderCircle class="spinning" :size="24" aria-hidden="true" />
        <span>正在加载穿搭内容…</span>
      </div>
      <div v-else-if="!trendPreviews.length" class="section-state" :role="state.trendError ? 'alert' : 'status'">
        <TrendingUp :size="28" aria-hidden="true" />
        <div class="section-state-copy">
          <strong>{{ state.trendError ? '穿搭内容暂时无法加载' : '暂无穿搭内容' }}</strong>
          <p>{{ state.trendError || '先从自己的衣橱开始，也可以稍后回来寻找灵感。' }}</p>
        </div>
        <button type="button" @click="app.loadTrends()">重新加载<ArrowRight :size="15" aria-hidden="true" /></button>
      </div>
      <div v-else class="trend-grid">
        <button
          v-for="(item, index) in trendPreviews"
          :key="item.id"
          type="button"
          class="trend-preview"
          @click="goToTrend(item)"
        >
          <span class="preview-image">
            <img
              :src="item.imageUrl || fallbackLooks[index]"
              :alt="item.title"
              loading="lazy"
              @error="handleImageError($event, fallbackLooks[index])"
            />
            <span>{{ String(index + 1).padStart(2, '0') }}</span>
          </span>
          <span class="preview-copy">
            <span class="preview-meta">
              <span>{{ trendPlatformName(item.platform) }}</span>
              <span>{{ item.evidence?.scoreLabel || scoreLabel }} {{ item.platform === 'editorial' ? '' : item.heatScore ?? '—' }}</span>
            </span>
            <strong>{{ item.title }}</strong>
            <span class="preview-tags">{{ (item.topicTags || []).join(' / ') || '暂无主题标签' }}</span>
            <span class="preview-open">查看详情<ArrowUpRight :size="16" aria-hidden="true" /></span>
          </span>
        </button>
      </div>
    </section>
  </div>
</template>

<style scoped>
.home-page {
  width: min(calc(100% - 64px), var(--page-width));
  margin: 0 auto;
  color: var(--ink);
}

.editorial-hero {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1.04fr);
  gap: clamp(32px, 5.5vw, 80px);
  align-items: center;
  padding: 36px 0;
}

.hero-copy,
.hero-collage {
  min-width: 0;
}

.hero-copy h1 {
  margin: 0;
  font-family: var(--font-display);
  font-size: clamp(34px, 3.6vw, 52px);
  font-weight: 600;
  line-height: 1.28;
  letter-spacing: -.035em;
}

.hero-copy h1 > span {
  display: block;
  color: var(--ink);
}

.hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 28px;
}

.guest-description {
  max-width: 410px;
  margin: 20px 0 0;
  color: var(--muted);
  font-size: 14px;
  line-height: 1.9;
}

.guest-hint {
  margin: 16px 0 0;
  color: var(--muted);
  font-size: 11px;
}

#home-features,
#guest-inspiration {
  scroll-margin-top: 24px;
}

.guest-look-description {
  margin: 0;
  border-top: 1px solid var(--line);
  padding-top: 12px;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.8;
}

.guest-start {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  margin-top: 24px;
  border: 1px solid var(--line);
  border-radius: 14px;
  padding: 24px;
  background: var(--accent-soft);
}

.guest-start h3 {
  margin: 0;
  font-family: var(--font-display);
  font-size: 21px;
  font-weight: 600;
}

.guest-start p {
  margin: 8px 0 0;
  color: var(--muted);
  font-size: 12px;
}

.hero-actions button,
.section-link,
.section-state button {
  display: inline-flex;
  min-height: 44px;
  align-items: center;
  justify-content: center;
  gap: 9px;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  padding: 10px 16px;
  color: var(--ink);
  background: var(--surface);
  font-size: 12px;
  font-weight: 600;
}

.hero-actions .primary-action {
  border-color: var(--accent-strong);
  color: var(--surface);
  background: var(--accent-strong);
}

.hero-actions .primary-action:hover {
  border-color: var(--accent);
  background: var(--accent);
}

.secondary-action:hover,
.section-link:hover,
.section-state button:hover {
  border-color: var(--accent);
  color: var(--accent);
  background: var(--accent-soft);
}

.hero-weather {
  display: flex;
  width: 100%;
  max-width: 410px;
  align-items: center;
  gap: 12px;
  margin-top: 28px;
  border: 0;
  border-top: 1px solid var(--line);
  padding: 18px 0 0;
  color: var(--muted);
  background: transparent;
  text-align: left;
}

.weather-icon {
  display: grid;
  flex: 0 0 auto;
  width: 38px;
  height: 38px;
  place-items: center;
  border-radius: 50%;
  color: var(--muted);
  background: var(--surface-soft);
}

.weather-copy {
  display: grid;
  min-width: 0;
  gap: 3px;
}

.weather-copy strong {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 10px;
  color: var(--ink);
  font-size: 12px;
  font-weight: 600;
  overflow-wrap: anywhere;
}

.weather-copy strong > span {
  color: var(--muted);
  font-weight: 400;
}

.hero-weather > svg {
  flex: 0 0 auto;
  margin-left: auto;
}

.hero-weather:hover > svg {
  color: var(--accent);
}

.hero-collage {
  margin: 0;
}

.collage-grid {
  display: grid;
  height: clamp(280px, 26vw, 374px);
  grid-template-columns: minmax(0, 1.3fr) minmax(0, 1fr);
  grid-template-rows: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.collage-frame {
  position: relative;
  min-width: 0;
  overflow: hidden;
  border-radius: 12px;
  background: var(--surface-soft);
}

.frame-1 {
  grid-row: 1 / -1;
}

.collage-frame img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: center 35%;
}

.collage-actions {
  display: flex;
  min-height: 60px;
  align-items: center;
  justify-content: flex-end;
  gap: 16px;
  padding-top: 12px;
}

.collage-actions button {
  display: grid;
  flex: 0 0 auto;
  width: 40px;
  height: 40px;
  place-items: center;
  border: 1px solid var(--line);
  border-radius: 50%;
  color: var(--ink);
  background: var(--surface);
}

.collage-actions button:hover {
  border-color: var(--accent);
  background: var(--accent-soft);
}

.stat-rail {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  border: 1px solid var(--line);
  border-radius: 14px;
  padding: 22px 0;
  background: var(--surface);
}

.stat-item {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 13px;
  border-right: 1px solid var(--line);
  padding: 0 24px;
}

.stat-item:last-child {
  border-right: 0;
}

.stat-icon {
  display: grid;
  flex: 0 0 auto;
  width: 38px;
  height: 38px;
  place-items: center;
  border-radius: 10px;
  color: var(--muted);
  background: var(--surface-soft);
}

.stat-copy {
  display: grid;
  min-width: 0;
  gap: 5px;
}

.stat-copy > span {
  color: var(--ink);
  font-size: 12px;
  font-weight: 600;
}

.stat-copy em {
  color: var(--muted);
  font-size: 10px;
  font-style: normal;
  line-height: 1.5;
}

.stat-item p {
  display: flex;
  flex: 0 0 auto;
  align-items: baseline;
  gap: 5px;
  margin: 0 0 0 auto;
}

.stat-item strong {
  font-family: var(--font-mono);
  font-size: 28px;
  font-weight: 500;
  letter-spacing: -.045em;
}

.stat-item small {
  color: var(--muted);
  font-size: 11px;
}

.home-guide {
  padding-top: 38px;
}

.section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 20px;
}

.section-heading p {
  margin: 0 0 5px;
  color: var(--muted);
  font-size: 11px;
}

.section-heading h2 {
  margin: 0;
  font-family: var(--font-display);
  font-size: 27px;
  font-weight: 600;
  line-height: 1.35;
}

.home-guide-heading > span {
  color: var(--muted);
  font-size: 11px;
  text-align: right;
}

.home-guide-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.home-guide-card {
  display: flex;
  min-width: 0;
  flex-direction: column;
  align-items: stretch;
  gap: 14px;
  border: 1px solid var(--line);
  border-radius: 14px;
  padding: 20px;
  color: var(--ink);
  background: var(--surface);
  text-align: left;
}

.home-guide-card:hover {
  border-color: var(--accent);
  box-shadow: 0 8px 24px rgba(82, 72, 55, .06);
  transform: translateY(-2px);
}

.home-guide-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: var(--muted);
}

.home-guide-icon {
  display: grid;
  width: 38px;
  height: 38px;
  place-items: center;
  border-radius: 10px;
  color: var(--muted);
  background: var(--surface-soft);
}

.home-guide-copy {
  display: grid;
  min-width: 0;
  gap: 5px;
}

.home-guide-copy strong {
  font-family: var(--font-display);
  font-size: 19px;
  font-weight: 600;
  line-height: 1.4;
}

.home-guide-copy small {
  color: var(--muted);
  font-size: 11px;
  line-height: 1.7;
  overflow-wrap: anywhere;
}

.home-guide-action {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: auto;
  color: var(--ink);
  font-size: 11px;
  font-weight: 600;
}

.trend-section {
  padding: 40px 0 64px;
}

.section-link {
  flex: 0 0 auto;
  gap: 8px;
  background: transparent;
}

.trend-source {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 6px 16px;
  margin: -4px 0 18px;
  color: var(--muted);
  font-size: 10px;
}

.demo-label {
  color: var(--coral);
}

.trend-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 18px;
}

.trend-preview {
  display: flex;
  min-width: 0;
  overflow: hidden;
  flex-direction: column;
  align-items: stretch;
  border: 1px solid var(--line);
  border-radius: 14px;
  padding: 0;
  color: var(--ink);
  background: var(--surface);
  text-align: left;
}

.trend-preview:hover {
  border-color: var(--accent);
  box-shadow: 0 8px 24px rgba(82, 72, 55, .06);
}

.preview-image {
  position: relative;
  display: block;
  flex: 0 0 auto;
  aspect-ratio: 4 / 3;
  overflow: hidden;
  background: var(--surface-soft);
}

.preview-image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: center 35%;
  transition: transform 320ms ease;
}

.trend-preview:hover .preview-image img {
  transform: scale(1.025);
}

.preview-image > span {
  position: absolute;
  top: 12px;
  left: 12px;
  display: grid;
  width: 30px;
  height: 30px;
  place-items: center;
  border-radius: 7px;
  color: var(--ink);
  background: var(--surface);
  font-family: var(--font-mono);
  font-size: 11px;
}

.preview-copy {
  display: grid;
  flex: 1;
  min-width: 0;
  grid-template-rows: auto auto 1fr auto;
  gap: 11px;
  padding: 20px;
}

.preview-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 4px 10px;
  color: var(--muted);
  font-size: 10px;
  overflow-wrap: anywhere;
}

.preview-copy > strong {
  display: -webkit-box;
  min-height: 2.8em;
  overflow: hidden;
  font-family: var(--font-display);
  font-size: 19px;
  font-weight: 600;
  line-height: 1.4;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow-wrap: anywhere;
}

.preview-tags {
  color: var(--muted);
  font-size: 11px;
  line-height: 1.7;
  overflow-wrap: anywhere;
}

.preview-open {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  border-top: 1px solid var(--line);
  padding-top: 12px;
  color: var(--ink);
  font-size: 11px;
  font-weight: 600;
}

.section-state {
  display: flex;
  min-height: 150px;
  align-items: center;
  justify-content: center;
  gap: 20px;
  border: 1px solid var(--line);
  border-radius: 14px;
  padding: 24px;
  color: var(--muted);
  background: var(--surface);
  font-size: 13px;
}

.section-state > svg {
  flex: 0 0 auto;
  color: var(--accent);
}

.section-state-copy strong {
  color: var(--ink);
  font-family: var(--font-display);
  font-size: 19px;
  font-weight: 600;
}

.section-state-copy p {
  margin: 6px 0 0;
  font-size: 12px;
}

.section-state button {
  flex: 0 0 auto;
}

@media (max-width: 1100px) {
  .editorial-hero {
    gap: 32px;
  }

  .hero-copy h1 {
    font-size: clamp(31px, 4vw, 44px);
  }

  .stat-item {
    gap: 10px;
    padding: 0 18px;
  }

  .stat-icon {
    display: none;
  }

  .home-guide-card {
    padding: 16px;
  }

  .home-guide-copy strong {
    font-size: 17px;
  }
}

@media (max-width: 760px) {
  .home-page {
    width: calc(100% - 40px);
  }

  .editorial-hero {
    grid-template-columns: minmax(0, 1fr);
    gap: 28px;
    padding: 28px 0;
  }

  .hero-copy h1 {
    font-size: clamp(32px, 6.5vw, 44px);
  }

  .hero-weather {
    max-width: none;
    margin-top: 20px;
    padding-top: 14px;
  }

  .collage-grid {
    height: 240px;
    grid-template-columns: minmax(0, 1.3fr) minmax(0, 1fr) minmax(0, 1fr);
    grid-template-rows: minmax(0, 1fr);
    gap: 10px;
  }

  .frame-1 {
    grid-row: auto;
  }

  .stat-rail {
    padding: 18px 0;
  }

  .stat-item {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
    padding: 0 16px;
  }

  .stat-copy {
    gap: 3px;
  }

  .stat-copy em {
    display: none;
  }

  .stat-item p {
    margin-left: 0;
  }

  .home-guide-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .home-guide-card {
    padding: 18px;
  }

  .trend-grid {
    grid-template-columns: minmax(0, 1fr);
    gap: 12px;
  }

  .trend-preview {
    display: grid;
    grid-template-columns: 150px minmax(0, 1fr);
  }

  .preview-image {
    height: 100%;
    min-height: 175px;
    aspect-ratio: auto;
  }

  .preview-copy {
    gap: 8px;
    padding: 16px;
  }

  .preview-copy > strong {
    min-height: 0;
  }

  .section-state {
    flex-wrap: wrap;
    gap: 14px;
    text-align: center;
  }
}

@media (max-width: 480px) {
  .home-page {
    width: calc(100% - 32px);
  }

  .editorial-hero {
    gap: 22px;
    padding: 24px 0;
  }

  .hero-copy h1 {
    font-size: clamp(30px, 8.2vw, 38px);
  }

  .hero-actions {
    gap: 8px;
    margin-top: 20px;
  }

  .hero-actions button {
    gap: 7px;
    padding: 10px 12px;
    font-size: 11px;
  }

  .collage-grid {
    height: 184px;
    gap: 8px;
  }

  .stat-item {
    padding: 0 12px;
  }

  .stat-copy > span {
    font-size: 11px;
  }

  .stat-item strong {
    font-size: 24px;
  }

  .home-guide {
    padding-top: 28px;
  }

  .section-heading {
    gap: 12px;
    margin-bottom: 16px;
  }

  .section-heading h2 {
    font-size: 23px;
  }

  .home-guide-heading > span {
    display: none;
  }

  .home-guide-grid {
    gap: 10px;
  }

  .home-guide-card {
    gap: 12px;
    padding: 14px;
  }

  .home-guide-copy strong {
    font-size: 17px;
  }

  .home-guide-copy small {
    font-size: 10px;
  }

  .trend-section {
    padding: 30px 0 44px;
  }

  .section-link {
    padding: 9px 10px;
    font-size: 10px;
  }

  .trend-preview {
    grid-template-columns: 104px minmax(0, 1fr);
  }

  .preview-image {
    min-height: 160px;
  }

  .preview-image > span {
    top: 8px;
    left: 8px;
    width: 25px;
    height: 25px;
    font-size: 10px;
  }

  .preview-copy {
    gap: 7px;
    padding: 12px;
  }

  .preview-copy > strong {
    font-size: 16px;
    line-height: 1.5;
  }

  .preview-meta {
    font-size: 9px;
  }

  .preview-tags,
  .preview-open {
    font-size: 10px;
  }

  .preview-open {
    padding-top: 8px;
  }
}
</style>
