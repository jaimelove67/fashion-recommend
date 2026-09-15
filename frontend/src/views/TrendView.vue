<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import TrendDiscovery from '../components/TrendDiscovery.vue'
import { ArrowRight, ArrowUpRight, Flame, Layers3, LoaderCircle, RefreshCw, Sparkles, Tag, TrendingUp } from '@lucide/vue'

const props = defineProps({
  app: { type: Object, required: true }
})

const fallbackLooks = ['', '', '']
const platformNames = { douyin: '抖音', xiaohongshu: '小红书', weibo: '微博', editorial: '时尚编辑精选' }
const activeTag = ref('全部')
const state = computed(() => props.app.state || {})
const trends = computed(() => state.value.trends || [])
const trendMeta = computed(() => state.value.trendMeta || {})
const trendStats = computed(() => props.app.trendStats || {})
const profile = computed(() => state.value.profile)
const scoreLabel = computed(() => trendMeta.value.scoreLabel || '热度')

const tags = computed(() => [
  '全部',
  ...new Set(trends.value.flatMap((item) => item.topicTags || []).filter(Boolean))
])

const filteredTrends = computed(() => {
  if (activeTag.value === '全部') return trends.value
  return trends.value.filter((item) => (item.topicTags || []).includes(activeTag.value))
})

const selectedTrend = computed(() => {
  const selected = trends.value.find((item) => item.id === state.value.selectedTrendId)
  if (selected && (activeTag.value === '全部' || (selected.topicTags || []).includes(activeTag.value))) return selected
  return filteredTrends.value[0] || props.app.topTrend || null
})

const selectedRank = computed(() => {
  if (!selectedTrend.value) return null
  const ordered = trends.value
  const index = ordered.findIndex((item) => item.id === selectedTrend.value.id)
  return index >= 0 ? index + 1 : null
})

const heroImage = computed(() => selectedTrend.value?.imageUrl || fallbackLooks[0])
const topTenTrends = computed(() => filteredTrends.value.slice(0, 10))
const galleryIndex = ref(0)
const galleryShift = ref(126)
const galleryStage = ref(null)
const galleryTrend = computed(() => topTenTrends.value[galleryIndex.value] || topTenTrends.value[0] || null)
const galleryReducedMotion = ref(false)
const galleryPaused = ref(false)
let galleryTimer = null
let galleryMounted = false
let galleryMediaQuery = null
const visibleCards = computed(() => filteredTrends.value)
  const metricRail = computed(() => [
  {
    label: '当前条目',
    value: trendStats.value.count ?? 0,
    unit: '条',
    note: trendMeta.value.demoMode ? '使用开发样本' : '服务端返回数据',
    icon: Layers3
  },
  {
    label: '内容来源',
    value: new Set(trends.value.map(item => item.platform)).size,
    unit: '个',
    note: '各来源独立排序',
    icon: TrendingUp
  },
  {
    label: '出现最多的标签',
    value: trendStats.value.topTag || '未设置',
    unit: '',
    note: '统计当前标签出现次数',
    icon: Tag
  }
])

const selectedFacts = computed(() => {
  const item = selectedTrend.value
  if (!item) return []
  return [
    {
      label: item.evidence?.scoreLabel || scoreLabel.value,
      value: item.platform === 'editorial' ? '编辑精选' : item.heatScore ?? '—',
      note: '仅表示当前采集范围，不代表全网排名'
    },
    {
      label: '主题标签',
      value: (item.topicTags || []).length,
      note: (item.topicTags || []).join('、') || '未设置标签'
    },
    {
      label: '数据状态',
      value: item.stale ? '已过期' : '已收录',
      note: formatDateTime(item.fetchedAt)
    }
  ]
})

watch(tags, (nextTags) => {
  if (!nextTags.includes(activeTag.value)) activeTag.value = '全部'
})

watch(topTenTrends, (items) => {
  const selectedIndex = items.findIndex((item) => item.id === state.value.selectedTrendId)
  galleryIndex.value = selectedIndex >= 0 ? selectedIndex : 0
  if (galleryMounted) {
    updateGalleryShift()
    startGalleryAutoplay()
  }
}, { immediate: true })

watch(() => state.value.selectedTrendId, (selectedId) => {
  const selectedIndex = topTenTrends.value.findIndex((item) => item.id === selectedId)
  if (selectedIndex >= 0 && selectedIndex !== galleryIndex.value) galleryIndex.value = selectedIndex
})

function updateGalleryShift() {
  if (typeof window === 'undefined') return
  if (window.innerWidth <= 540) {
    const stageWidth = galleryStage.value?.getBoundingClientRect().width || window.innerWidth
    galleryShift.value = Math.round(Math.min(212, stageWidth * 0.58) + 16)
    return
  }
  if (window.innerWidth <= 760) {
    const stageWidth = galleryStage.value?.getBoundingClientRect().width || window.innerWidth
    galleryShift.value = Math.round(Math.min(212, stageWidth * 0.58) + 16)
    return
  }
  if (window.innerWidth <= 1100) {
    const stageWidth = galleryStage.value?.getBoundingClientRect().width || window.innerWidth
    const cardWidth = Math.min(238, Math.max(132, stageWidth * 0.155))
    galleryShift.value = Math.round(cardWidth + 24)
    return
  }
  const stageWidth = galleryStage.value?.getBoundingClientRect().width || window.innerWidth
  const cardWidth = Math.min(238, Math.max(132, stageWidth * 0.155))
  galleryShift.value = Math.round(cardWidth + 24)
}

onMounted(() => {
  galleryMounted = true
  updateGalleryShift()
  window.addEventListener('resize', updateGalleryShift)
  galleryMediaQuery = window.matchMedia('(prefers-reduced-motion: reduce)')
  galleryReducedMotion.value = galleryMediaQuery.matches
  galleryMediaQuery.addEventListener?.('change', handleGalleryMotionPreference)
  document.addEventListener('visibilitychange', handleGalleryVisibility)
  startGalleryAutoplay()
})

onBeforeUnmount(() => {
  galleryMounted = false
  stopGalleryAutoplay()
  if (typeof window !== 'undefined') window.removeEventListener('resize', updateGalleryShift)
  if (typeof document !== 'undefined') document.removeEventListener('visibilitychange', handleGalleryVisibility)
  galleryMediaQuery?.removeEventListener?.('change', handleGalleryMotionPreference)
})

function formatPlatform(value) {
  return platformNames[value] || value || '未标注来源'
}

function formatDateTime(value) {
  if (!value) return '暂无更新时间'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '暂无更新时间'
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false
  })
}

function chooseTag(tag) {
  activeTag.value = tag
  const firstMatch = tag === '全部'
    ? trends.value[0]
    : trends.value.find((item) => (item.topicTags || []).includes(tag))
  const current = trends.value.find((item) => item.id === state.value.selectedTrendId)
  const currentMatches = current && (tag === '全部' || (current.topicTags || []).includes(tag))
  if (!currentMatches && firstMatch) state.value.selectedTrendId = firstMatch.id
}

function selectTrend(item) {
  if (item?.id) state.value.selectedTrendId = item.id
}

function fallbackFor(index = 0) {
  return fallbackLooks[index % fallbackLooks.length]
}

function selectGalleryItem(index, syncTrend = true) {
  const item = topTenTrends.value[index]
  if (!item) return
  galleryIndex.value = index
  if (syncTrend) selectTrend(item)
}

function moveGallery(step, syncTrend = true) {
  const total = topTenTrends.value.length
  if (total < 2) return
  const nextIndex = (galleryIndex.value + step + total) % total
  selectGalleryItem(nextIndex, syncTrend)
}

function startGalleryAutoplay() {
  stopGalleryAutoplay()
  if (!galleryMounted || galleryPaused.value || galleryReducedMotion.value || document.hidden || topTenTrends.value.length < 2) return
  galleryTimer = window.setInterval(() => moveGallery(1, false), 1000)
}

function stopGalleryAutoplay() {
  if (galleryTimer) {
    window.clearInterval(galleryTimer)
    galleryTimer = null
  }
}

function pauseGallery() {
  galleryPaused.value = true
  stopGalleryAutoplay()
}

function resumeGallery() {
  if (galleryStage.value?.contains(document.activeElement)) return
  galleryPaused.value = false
  startGalleryAutoplay()
}

function galleryCardFromTarget(target) {
  return target?.closest?.('.gallery-card')
}

function handleGalleryMouseOver(event) {
  if (!galleryCardFromTarget(event.target) || galleryCardFromTarget(event.relatedTarget)) return
  pauseGallery()
}

function handleGalleryMouseOut(event) {
  if (!galleryCardFromTarget(event.target) || galleryCardFromTarget(event.relatedTarget)) return
  resumeGallery()
}

function handleGalleryFocusout(event) {
  if (!event.currentTarget.contains(event.relatedTarget)) resumeGallery()
}

function handleGalleryMotionPreference(event) {
  galleryReducedMotion.value = event.matches
  if (event.matches) stopGalleryAutoplay()
  else startGalleryAutoplay()
}

function handleGalleryVisibility() {
  if (document.hidden) stopGalleryAutoplay()
  else startGalleryAutoplay()
}

function galleryCardStyle(index) {
  const total = topTenTrends.value.length
  if (!total) return {}
  let offset = index - galleryIndex.value
  const half = total / 2
  if (offset > half) offset -= total
  if (offset < -half) offset += total
  const distance = Math.abs(offset)
  return {
    '--gallery-x': `${offset * galleryShift.value}px`,
    '--gallery-y': `${distance * 7}px`,
    '--gallery-depth': '0px',
    '--gallery-rotation': `${offset * -7}deg`,
    '--gallery-scale': Math.max(0.82, 1 - distance * 0.06),
    opacity: distance <= 3 ? 1 : 0,
    pointerEvents: distance <= 3 ? 'auto' : 'none',
    zIndex: 20 - distance
  }
}

function handleGalleryKeydown(event) {
  if (event.key === 'ArrowLeft') {
    event.preventDefault()
    moveGallery(-1)
  }
  if (event.key === 'ArrowRight') {
    event.preventDefault()
    moveGallery(1)
  }
  if (event.key === 'Home') {
    event.preventDefault()
    selectGalleryItem(0)
  }
  if (event.key === 'End') {
    event.preventDefault()
    selectGalleryItem(topTenTrends.value.length - 1)
  }
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

function useSuggestion(suggestion) {
  if (!state.value.recommendationForm) state.value.recommendationForm = {}
  props.app.clearTrendReference()
  state.value.recommendationForm.styleHint = suggestion
  props.app.selectView('recommend')
}
</script>

<template>
  <div class="trend-page">
    <TrendDiscovery :app="app" controls-only @filter-style="activeTag = $event" />
    <section v-if="trends.length && !state.trendsLoading" class="trend-hero" aria-labelledby="trend-title">
      <div class="hero-intro">
        <div v-if="trendMeta.demoMode" class="demo-banner" role="status">
          <strong>开发样本数据</strong>
          <span>当前内容用于演示，接入趋势源后会替换</span>
        </div>
        <h1 id="trend-title">趋势观察</h1>
        <p>查看公开穿搭内容、来源和发布时间，再决定哪些风格值得试试。</p>
        <div class="hero-update">
          <span>更新时间 {{ formatDateTime(trendMeta.fetchedAt) }}</span>
          <button
            type="button"
            class="refresh-button"
            :disabled="state.trendsLoading"
            aria-label="刷新趋势数据"
            title="刷新趋势数据"
            @click="app.loadTrends()"
          >
            <LoaderCircle v-if="state.trendsLoading" class="spinning" :size="18" />
            <RefreshCw v-else :size="18" />
          </button>
        </div>
      </div>

      <figure class="hero-media">
        <img
          :src="heroImage"
          :alt="selectedTrend?.title || '趋势参考图'"
          @error="handleImageError($event, fallbackLooks[0])"
        />
        <figcaption>
          <span>{{ selectedTrend ? formatPlatform(selectedTrend.platform) : '趋势条目' }}</span>
          <strong>{{ selectedTrend?.title || '等待趋势数据' }}</strong>
        </figcaption>
      </figure>
    </section>

    <section class="metric-rail" aria-label="趋势数据概览">
      <article v-for="metric in metricRail" :key="metric.label">
        <component :is="metric.icon" :size="19" aria-hidden="true" />
        <div>
          <span>{{ metric.label }}</span>
          <p><strong>{{ metric.value }}</strong><small v-if="metric.unit">{{ metric.unit }}</small></p>
          <em>{{ metric.note }}</em>
        </div>
      </article>
    </section>

    <section class="trend-gallery-section" aria-labelledby="trend-gallery-title">
      <header class="gallery-heading">
        <div>
          <h2 id="trend-gallery-title">趋势穿搭精选</h2>
          <span>{{ trendMeta.demoMode ? '开发样本 · 接入趋势源后更新' : '各平台独立排序后交替展示 · 非全网榜单' }}</span>
        </div>
      </header>

      <div
        v-if="topTenTrends.length"
        ref="galleryStage"
        class="gallery-stage"
        role="region"
        aria-label="趋势穿搭轮播"
        aria-roledescription="趋势穿搭轮播"
        aria-keyshortcuts="ArrowLeft ArrowRight Home End"
        tabindex="0"
        @keydown="handleGalleryKeydown"
        @mouseover="handleGalleryMouseOver"
        @mouseout="handleGalleryMouseOut"
        @focusin="pauseGallery"
        @focusout="handleGalleryFocusout"
      >
        <button
          v-for="(item, index) in topTenTrends"
          :key="item.id"
          type="button"
          class="gallery-card"
          :class="{ active: galleryTrend?.id === item.id }"
          :style="galleryCardStyle(index)"
          :aria-label="`查看第 ${index + 1} 套：${item.title}`"
          :aria-current="galleryTrend?.id === item.id ? 'true' : undefined"
          @click="selectGalleryItem(index)"
        >
          <span class="gallery-card-image">
            <img
              :src="item.imageUrl || fallbackFor(index)"
              :alt="item.title"
              @error="handleImageError($event, fallbackFor(index + 1))"
            />
          </span>
        </button>
      </div>
      <div v-else class="trend-state gallery-empty">
        <strong>等待趋势数据</strong>
        <span>趋势源准备好后，这里会展示热度最高的 10 条内容。</span>
      </div>
    </section>

    <section class="trend-browser" aria-labelledby="trend-browser-title">
      <header class="browser-heading">
        <div>
          <p>按标签浏览</p>
          <h2 id="trend-browser-title">按标签查看趋势</h2>
        </div>
        <span>{{ filteredTrends.length }} 条结果</span>
      </header>

      <div class="tag-filters" role="toolbar" aria-label="按标签筛选趋势">
        <button
          v-for="tagName in tags"
          :key="tagName"
          type="button"
          :class="{ active: activeTag === tagName }"
          :aria-pressed="activeTag === tagName"
          @click="chooseTag(tagName)"
        >
          {{ tagName }}
        </button>
      </div>

      <div v-if="state.trendsLoading && !selectedTrend" class="trend-state" aria-live="polite">
        <LoaderCircle class="spinning" :size="22" />正在加载趋势数据…
      </div>
      <div v-else-if="!selectedTrend" class="trend-state">
        <strong>暂时没有趋势数据</strong>
        <button type="button" @click="app.loadTrends()">重新加载</button>
      </div>
      <template v-else>
        <article class="selected-feature">
          <div class="feature-media">
            <img
              :src="selectedTrend.imageUrl || fallbackLooks[0]"
              :alt="selectedTrend.title"
              @error="handleImageError($event, fallbackLooks[0])"
            />
            <span><Flame :size="16" />{{ selectedTrend.evidence?.scoreLabel || scoreLabel }} {{ selectedTrend.platform === 'editorial' ? '' : selectedTrend.heatScore }}</span>
          </div>
          <div class="feature-copy">
            <div class="feature-number">NO. {{ String(selectedRank || 1).padStart(2, '0') }}</div>
            <p class="feature-source">{{ formatPlatform(selectedTrend.platform) }} · 发布于 {{ formatDateTime(selectedTrend.publishedAt) }}</p>
            <h2>{{ selectedTrend.title }}</h2>
            <p class="feature-summary">{{ selectedTrend.summary || `当前条目只有标题、主题标签和${scoreLabel}。${scoreLabel}反映数据源的热度信号，不等于你的个人偏好。` }}</p>
            <div class="feature-tags" aria-label="主题标签">
              <span v-for="tagName in selectedTrend.topicTags || []" :key="tagName">{{ tagName }}</span>
              <span v-if="!(selectedTrend.topicTags || []).length">未设置标签</span>
            </div>
            <button type="button" class="source-action" @click="app.useTrend(selectedTrend)">用我的衣橱搭一套 <ArrowRight :size="17" /></button>
            <div v-if="selectedTrend.evidence" class="evidence-counts">
              <span v-if="selectedTrend.evidence.likes != null">点赞 {{ selectedTrend.evidence.likes }}</span>
              <span v-if="selectedTrend.evidence.favorites != null">收藏 {{ selectedTrend.evidence.favorites }}</span>
              <span v-if="selectedTrend.evidence.comments != null">评论 {{ selectedTrend.evidence.comments }}</span>
              <span v-if="selectedTrend.evidence.interactionGrowth != null">本期互动增加 {{ selectedTrend.evidence.interactionGrowth }}</span>
              <span v-if="selectedTrend.stale">等待更新，当前为上次收录内容</span>
            </div>
            <div v-if="selectedTrend.evidence?.images?.length > 1" class="source-gallery" aria-label="原文图集">
              <a v-for="url in selectedTrend.evidence.images" :key="url" :href="selectedTrend.sourceUrl" target="_blank" rel="noopener noreferrer"><img :src="url" :alt="selectedTrend.title" loading="lazy" referrerpolicy="no-referrer" @error="handleImageError($event, '')" /></a>
            </div>
            <a
              v-if="selectedTrend.sourceUrl"
              class="source-action"
              :href="selectedTrend.sourceUrl"
              target="_blank"
              rel="noreferrer"
            >
              查看来源<ArrowUpRight :size="17" />
            </a>
          </div>
        </article>

        <div class="fact-grid" aria-label="当前趋势信息">
          <article v-for="fact in selectedFacts" :key="fact.label" class="fact-card">
            <span>{{ fact.label }}</span>
            <strong>{{ fact.value }}</strong>
            <p>{{ fact.note }}</p>
          </article>
        </div>
      </template>
    </section>

    <section v-if="visibleCards.length" class="trend-list" aria-labelledby="trend-list-title">
      <header class="list-heading">
        <div>
          <p>当前条目</p>
          <h2 id="trend-list-title">精选趋势</h2>
        </div>
      </header>
      <div class="trend-card-grid">
        <article
          v-for="(item, index) in visibleCards"
          :key="item.id"
          class="trend-card"
          :class="{ selected: selectedTrend?.id === item.id }"
        >
          <button
            type="button"
            class="card-select"
            :aria-current="selectedTrend?.id === item.id ? 'true' : undefined"
            @click="selectTrend(item)"
          >
            <span class="card-image">
              <img
                :src="item.imageUrl || fallbackLooks[index]"
                :alt="item.title"
                @error="handleImageError($event, fallbackLooks[index])"
              />
              <span>NO. {{ String(index + 1).padStart(2, '0') }}</span>
            </span>
            <span class="card-copy">
              <span class="card-meta"><span>{{ formatPlatform(item.platform) }}</span><span>{{ item.evidence?.scoreLabel || scoreLabel }} {{ item.platform === 'editorial' ? '' : item.heatScore }}</span></span>
              <strong>{{ item.title }}</strong>
              <span class="card-tags">{{ (item.topicTags || []).join(' / ') || '未设置标签' }}</span>
            </span>
          </button>
          <a v-if="item.sourceUrl" :href="item.sourceUrl" target="_blank" rel="noreferrer">
            查看来源<ArrowUpRight :size="15" />
          </a>
        </article>
      </div>
    </section>

    <section class="profile-suggestions" aria-labelledby="suggestion-title">
      <header>
        <div>
          <p>个人档案</p>
          <h2 id="suggestion-title">下一件衣物，可以试试什么？</h2>
        </div>
        <Sparkles :size="22" aria-hidden="true" />
      </header>

      <div v-if="state.profileLoading" class="suggestion-state" aria-live="polite">正在加载风格档案…</div>
      <div v-else-if="profile?.itemSuggestions?.length" class="suggestion-list">
        <button
          v-for="(suggestion, index) in profile.itemSuggestions"
          :key="suggestion"
          type="button"
          @click="useSuggestion(suggestion)"
        >
          <span>{{ String(index + 1).padStart(2, '0') }}</span>
          <strong>{{ suggestion }}</strong>
          <em>带入下一次推荐</em>
          <ArrowRight :size="17" />
        </button>
      </div>
      <div v-else class="suggestion-state">
        <span>{{ profile ? '档案里还没有衣物建议' : '填写风格档案后，这里会按你的偏好给出衣物建议' }}</span>
        <button type="button" @click="app.selectView('profile')">打开风格档案</button>
      </div>
    </section>
  </div>
</template>

<style scoped>
.evidence-counts { display:flex; flex-wrap:wrap; gap:12px; color:#596259; font-size:13px; margin:16px 0; }
.source-gallery { display:flex; overflow:auto; gap:10px; margin:16px 0; }
.source-gallery img { width:100px; height:130px; object-fit:cover; }
.trend-page {
  width: min(calc(100% - 48px), 1240px);
  margin: 0 auto;
  padding-bottom: 104px;
  color: var(--ink);
}

.trend-hero {
  display: grid;
  min-height: 500px;
  grid-template-columns: minmax(320px, 0.82fr) minmax(460px, 1.18fr);
  gap: 68px;
  align-items: center;
  padding: 48px 0 38px;
}

.hero-intro h1 {
  margin: 0;
  font-family: var(--font-display);
  font-size: 54px;
  font-weight: 700;
  line-height: 1.05;
  letter-spacing: -.06em;
}

.hero-intro > p {
  max-width: 520px;
  margin: 24px 0 0;
  color: var(--muted);
  font-size: 14px;
  line-height: 1.85;
}

.demo-banner {
  display: grid;
  width: fit-content;
  gap: 3px;
  margin-bottom: 28px;
  border-left: 3px solid var(--accent);
  padding: 7px 12px;
  background: var(--accent-soft);
}

.demo-banner strong {
  font-size: 12px;
}

.demo-banner span {
  color: var(--muted);
  font-size: 11px;
}

.hero-update {
  display: flex;
  max-width: 520px;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  margin-top: 30px;
  border-top: 1px solid var(--line);
  padding-top: 14px;
  color: var(--muted);
  font-size: 11px;
}

.refresh-button {
  display: grid;
  width: 40px;
  height: 40px;
  flex: 0 0 auto;
  place-items: center;
  border: 1px solid var(--line);
  border-radius: 50%;
  color: var(--ink);
  background: var(--surface);
}

.refresh-button:hover,
.refresh-button:focus-visible {
  border-color: var(--accent);
  outline: 0;
}

.hero-media {
  position: relative;
  min-width: 0;
  margin: 0;
}

.hero-media > img {
  width: 100%;
  aspect-ratio: 16 / 10;
  display: block;
  border-radius: 16px;
  object-fit: cover;
  object-position: center 35%;
  box-shadow: 0 20px 50px rgba(25, 70, 60, .1);
}

.hero-media figcaption {
  position: relative;
  width: min(78%, 460px);
  display: grid;
  gap: 5px;
  margin: -36px 0 0 auto;
  border-left: 3px solid var(--accent);
  border-radius: 14px 0 0 14px;
  padding: 15px 18px;
  background: var(--surface);
  box-shadow: 0 12px 30px rgba(25, 70, 60, .1);
}

.hero-media figcaption span {
  color: var(--muted);
  font-size: 10px;
  font-weight: 700;
}

.hero-media figcaption strong {
  font-family: var(--font-display);
  font-size: 19px;
  font-weight: 500;
  line-height: 1.3;
}

.metric-rail {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  border-top: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
}

.metric-rail article {
  display: grid;
  min-height: 142px;
  grid-template-columns: 30px minmax(0, 1fr);
  gap: 14px;
  align-content: center;
  padding: 26px 30px;
  border-right: 1px solid var(--line);
}

.metric-rail article:last-child {
  border-right: 0;
}

.metric-rail svg {
  margin-top: 4px;
  color: var(--accent);
}

.metric-rail span,
.metric-rail em {
  color: var(--muted);
  font-size: 11px;
  font-style: normal;
  line-height: 1.5;
}

.metric-rail p {
  display: flex;
  min-width: 0;
  align-items: baseline;
  gap: 6px;
  margin: 5px 0 3px;
}

.metric-rail strong {
  overflow: hidden;
  font-family: var(--font-display);
  font-size: 31px;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.metric-rail small {
  flex: 0 0 auto;
  font-size: 12px;
}

.trend-gallery-section {
  padding-top: 84px;
}

.gallery-heading {
  display: block;
}

.gallery-heading p {
  margin: 0 0 9px;
  color: var(--muted);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.16em;
}

.gallery-heading h2 {
  margin: 0;
  font-family: var(--font-display);
  font-size: 34px;
  font-weight: 700;
  line-height: 1.15;
}

.gallery-heading > div > span {
  display: block;
  margin-top: 12px;
  color: var(--muted);
  font-size: 11px;
}

.gallery-stage {
  position: relative;
  height: clamp(300px, 28vw, 500px);
  margin-top: 30px;
  overflow: hidden;
  color: var(--ink);
  perspective: 1800px;
  isolation: isolate;
}

.gallery-stage:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 4px;
}

.gallery-card {
  position: absolute;
  top: 51%;
  left: 50%;
  width: clamp(132px, 15.5%, 238px);
  aspect-ratio: 3 / 4;
  display: block;
  overflow: visible;
  border: 0;
  border-radius: 2px;
  padding: 0;
  color: inherit;
  background: transparent;
  cursor: pointer;
  filter: saturate(0.92);
  transform: translate3d(calc(-50% + var(--gallery-x)), calc(-50% + var(--gallery-y)), var(--gallery-depth)) rotateY(var(--gallery-rotation)) scale(var(--gallery-scale));
  transform-origin: center 70%;
  transform-style: preserve-3d;
  transition: transform 720ms cubic-bezier(0.22, 0.75, 0.2, 1), opacity 320ms ease, filter 260ms ease;
}

.gallery-card:hover,
.gallery-card:focus-visible,
.gallery-card.active {
  filter: saturate(1.04);
  outline: 0;
}

.gallery-card.active {
  filter: saturate(1.08) brightness(1.04);
}

.gallery-card-image {
  position: absolute;
  inset: 0;
  display: block;
  overflow: hidden;
  border: 1px solid rgba(82, 92, 91, 0.16);
  border-radius: inherit;
  background: #dfe5e7;
  box-shadow: 0 16px 28px rgba(41, 55, 63, 0.13);
}

.gallery-card.active .gallery-card-image {
  border-color: rgba(54, 64, 62, 0.32);
  box-shadow: 0 20px 34px rgba(41, 55, 63, 0.18);
}

.gallery-card-image img {
  width: 100%;
  height: 100%;
  display: block;
  object-fit: cover;
  transition: transform 480ms ease;
}

.gallery-card:hover .gallery-card-image img,
.gallery-card:focus-visible .gallery-card-image img {
  transform: scale(1.045);
}

.gallery-empty {
  margin-top: 30px;
}

.gallery-empty span {
  color: var(--muted);
}

.trend-browser {
  padding-top: 88px;
}

.browser-heading,
.list-heading,
.profile-suggestions > header {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 28px;
}

.browser-heading p,
.list-heading p,
.profile-suggestions header p {
  margin: 0 0 8px;
  color: var(--muted);
  font-size: 12px;
  font-weight: 700;
}

.browser-heading h2,
.list-heading h2,
.profile-suggestions header h2 {
  margin: 0;
  font-family: var(--font-display);
  font-size: 34px;
  font-weight: 500;
  line-height: 1.2;
  letter-spacing: 0;
}

.browser-heading > span {
  flex: 0 0 auto;
  color: var(--muted);
  font-size: 12px;
}

.tag-filters {
  display: flex;
  gap: 4px;
  margin-top: 30px;
  overflow-x: auto;
  border-bottom: 1px solid var(--line);
  scrollbar-width: thin;
}

.tag-filters button {
  min-height: 44px;
  flex: 0 0 auto;
  border: 0;
  border-bottom: 2px solid transparent;
  padding: 9px 15px;
  color: var(--muted);
  background: transparent;
  font-size: 13px;
  letter-spacing: 0;
}

.tag-filters button.active {
  border-bottom-color: var(--ink);
  color: var(--ink);
  font-weight: 700;
}

.tag-filters button:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: -2px;
}

.selected-feature {
  display: grid;
  grid-template-columns: minmax(0, 1.08fr) minmax(330px, 0.92fr);
  gap: 56px;
  align-items: center;
  margin-top: 38px;
  border-bottom: 1px solid var(--line);
  padding-bottom: 46px;
}

.feature-media {
  position: relative;
  overflow: hidden;
  border-radius: var(--radius);
  background: var(--accent-soft);
}

.feature-media img {
  width: 100%;
  aspect-ratio: 4 / 3;
  display: block;
  object-fit: cover;
}

.feature-media > span {
  position: absolute;
  top: 16px;
  right: 16px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border-radius: var(--radius);
  padding: 8px 10px;
  color: var(--ink);
  background: var(--surface);
  font-size: 11px;
  font-weight: 700;
}

.feature-number {
  color: var(--accent);
  font-family: var(--font-mono);
  font-size: 15px;
  font-weight: 700;
}

.feature-source {
  margin: 16px 0 0;
  color: var(--muted);
  font-size: 11px;
  line-height: 1.5;
}

.feature-copy h2 {
  margin: 14px 0 0;
  font-family: var(--font-display);
  font-size: 36px;
  font-weight: 700;
  line-height: 1.13;
  letter-spacing: 0;
}

.feature-summary {
  margin: 21px 0 0;
  color: var(--muted);
  font-size: 13px;
  line-height: 1.85;
}

.feature-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
  margin-top: 22px;
}

.feature-tags span {
  border: 1px solid var(--line);
  border-radius: var(--radius);
  padding: 7px 10px;
  color: var(--ink);
  background: var(--surface);
  font-size: 11px;
}

.source-action {
  display: inline-flex;
  min-height: 42px;
  align-items: center;
  gap: 8px;
  margin-top: 26px;
  border-bottom: 1px solid var(--ink);
  color: var(--ink);
  font-size: 12px;
  font-weight: 700;
  text-decoration: none;
}

.source-action:hover,
.source-action:focus-visible {
  color: var(--accent);
  outline: 0;
}

.fact-grid {
  display: grid;
  grid-template-columns: 1.15fr .92fr .92fr;
  gap: 14px;
  margin-top: 18px;
}

.fact-card {
  min-width: 0;
  border: 1px solid var(--line);
  border-radius: 14px;
  padding: 22px;
  background: var(--surface);
  box-shadow: 0 14px 34px rgba(25, 70, 60, .05);
}

.fact-card span {
  color: var(--muted);
  font-size: 11px;
  font-weight: 700;
}

.fact-card strong {
  display: block;
  overflow: hidden;
  margin-top: 14px;
  font-family: var(--font-display);
  font-size: 28px;
  font-weight: 500;
  line-height: 1.15;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.fact-card p {
  min-height: 36px;
  margin: 10px 0 0;
  overflow: hidden;
  color: var(--muted);
  font-size: 11px;
  line-height: 1.55;
}

.trend-list {
  padding-top: 92px;
}

.trend-card-grid {
  display: grid;
  grid-template-columns: 1.15fr .92fr .92fr;
  gap: 15px;
  margin-top: 30px;
}

.trend-card {
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: 14px;
  background: var(--surface);
  box-shadow: 0 14px 34px rgba(25, 70, 60, .05);
  transition: border-color 180ms ease, transform 180ms ease, box-shadow 180ms ease;
}

.trend-card:hover,
.trend-card:focus-within,
.trend-card.selected {
  border-color: var(--accent);
  box-shadow: 0 18px 40px rgba(25, 70, 60, .1);
  transform: translateY(-2px);
}

.trend-card:hover {
  transform: translateY(-3px);
}

.card-select {
  width: 100%;
  border: 0;
  padding: 0;
  color: var(--ink);
  background: transparent;
  text-align: left;
}

.card-select:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: -2px;
}

.card-image {
  position: relative;
  display: block;
  aspect-ratio: 4 / 3;
  overflow: hidden;
  background: var(--accent-soft);
}

.card-image img {
  width: 100%;
  height: 100%;
  display: block;
  object-fit: cover;
  transition: transform 300ms ease;
}

.trend-card:hover .card-image img {
  transform: scale(1.025);
}

.card-image > span {
  position: absolute;
  top: 12px;
  left: 12px;
  border-radius: var(--radius);
  padding: 5px 8px;
  color: var(--ink);
  background: var(--surface);
  font-size: 10px;
  font-weight: 700;
}

.card-copy {
  display: grid;
  gap: 11px;
  padding: 19px;
}

.card-meta {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  color: var(--muted);
  font-size: 10px;
}

.card-copy > strong {
  min-height: 50px;
  font-family: var(--font-display);
  font-size: 21px;
  font-weight: 500;
  line-height: 1.28;
}

.card-tags {
  min-height: 34px;
  color: var(--muted);
  font-size: 11px;
  line-height: 1.55;
}

.trend-card > a {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  border-top: 1px solid var(--line);
  padding: 12px 19px;
  color: var(--muted);
  font-size: 11px;
  text-decoration: none;
}

.trend-card > a:hover,
.trend-card > a:focus-visible {
  color: var(--ink);
  outline: 0;
}

.profile-suggestions {
  margin-top: 94px;
  border-top: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
  padding: 44px 0;
}

.profile-suggestions > header {
  align-items: center;
}

.profile-suggestions > header svg {
  flex: 0 0 auto;
  color: var(--accent);
}

.suggestion-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 32px;
  margin-top: 28px;
}

.suggestion-list button {
  display: grid;
  min-width: 0;
  min-height: 70px;
  grid-template-columns: 28px minmax(0, 1fr) auto 20px;
  gap: 12px;
  align-items: center;
  border: 0;
  border-top: 1px solid var(--line);
  padding: 13px 0;
  color: var(--ink);
  background: transparent;
  text-align: left;
}

.suggestion-list button:hover,
.suggestion-list button:focus-visible {
  color: var(--accent);
  outline: 0;
}

.suggestion-list button > span {
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 12px;
}

.suggestion-list button strong {
  overflow-wrap: anywhere;
  font-size: 13px;
}

.suggestion-list button em {
  color: var(--muted);
  font-size: 10px;
  font-style: normal;
}

.suggestion-state,
.trend-state {
  display: flex;
  min-height: 150px;
  align-items: center;
  justify-content: center;
  gap: 12px;
  color: var(--muted);
  font-size: 12px;
  text-align: center;
}

.suggestion-state button,
.trend-state button {
  min-height: 38px;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  padding: 8px 12px;
  color: var(--ink);
  background: var(--surface);
  font-size: 12px;
}

.trend-state {
  min-height: 360px;
  flex-direction: column;
  border-bottom: 1px solid var(--line);
}

.trend-state strong {
  color: var(--ink);
  font-family: var(--font-display);
  font-size: 22px;
  font-weight: 500;
}

.spinning {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

@media (max-width: 980px) {
  .trend-hero {
    grid-template-columns: minmax(280px, 0.85fr) minmax(380px, 1.15fr);
    gap: 38px;
  }

  .hero-intro h1 {
    font-size: 52px;
  }

  .selected-feature {
    grid-template-columns: minmax(0, 1fr) minmax(300px, 0.9fr);
    gap: 36px;
  }

  .feature-copy h2 {
    font-size: 33px;
  }

  .gallery-stage {
    height: 360px;
  }
}

@media (max-width: 760px) {
  .trend-hero {
    min-height: auto;
    grid-template-columns: 1fr;
    gap: 34px;
    padding-top: 52px;
  }

  .metric-rail,
  .fact-grid {
    grid-template-columns: 1fr;
  }

  .trend-gallery-section {
    padding-top: 64px;
  }

  .gallery-heading h2 {
    font-size: 31px;
  }

  .gallery-stage {
    height: 420px;
    margin-top: 24px;
  }

  .gallery-card {
    width: min(58%, 212px);
  }

  .metric-rail article {
    min-height: 116px;
    border-right: 0;
    border-bottom: 1px solid var(--line);
  }

  .metric-rail article:last-child {
    border-bottom: 0;
  }

  .selected-feature {
    grid-template-columns: 1fr;
    gap: 30px;
  }

  .trend-card-grid {
    grid-template-columns: 1fr;
  }

  .trend-card {
    display: block;
  }

  .card-select {
    display: grid;
    grid-template-columns: minmax(190px, 0.82fr) minmax(0, 1.18fr);
  }

  .card-image {
    height: 100%;
    aspect-ratio: auto;
  }

  .trend-card > a {
    justify-content: flex-end;
  }

  .suggestion-list {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 540px) {
  .trend-page {
    width: calc(100% - 32px);
  }

  .trend-hero {
    padding-top: 38px;
  }

  .hero-intro h1 {
    font-size: 42px;
  }

  .hero-media figcaption {
    width: 88%;
  }

  .browser-heading,
  .list-heading,
  .profile-suggestions > header,
  .gallery-heading {
    display: grid;
    align-items: start;
  }

  .gallery-heading h2 {
    font-size: 29px;
  }

  .gallery-stage {
    height: 380px;
  }

  .gallery-card {
    width: 58%;
  }

  .browser-heading h2,
  .list-heading h2,
  .profile-suggestions header h2 {
    font-size: 29px;
  }

  .feature-copy h2 {
    font-size: 30px;
  }

  .fact-card strong {
    white-space: normal;
  }

  .trend-card {
    display: block;
  }

  .card-select {
    display: block;
  }

  .card-image {
    aspect-ratio: 4 / 3;
  }

  .trend-card > a {
    grid-column: auto;
  }

  .suggestion-list button {
    grid-template-columns: 26px minmax(0, 1fr) 20px;
  }

  .suggestion-list button em {
    display: none;
  }

  .suggestion-state {
    flex-direction: column;
  }
}

@media (prefers-reduced-motion: reduce) {
  .trend-card,
  .card-image img,
  .gallery-card,
  .gallery-card-image img {
    transition: none;
  }
}
</style>
