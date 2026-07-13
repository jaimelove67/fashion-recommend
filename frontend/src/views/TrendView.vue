<script setup>
import { computed, ref, watch } from 'vue'
import { ArrowRight, ArrowUpRight, Flame, Layers3, LoaderCircle, RefreshCw, Sparkles, Tag, TrendingUp } from '@lucide/vue'

const props = defineProps({
  app: { type: Object, required: true }
})

const fallbackLooks = ['/assets/look-urban.jpg', '/assets/look-tailoring.jpg', '/assets/look-color.jpg']
const platformNames = { douyin: '抖音', xiaohongshu: '小红书', weibo: '微博' }
const activeTag = ref('全部')
const state = computed(() => props.app.state || {})
const trends = computed(() => state.value.trends || [])
const trendMeta = computed(() => state.value.trendMeta || {})
const trendStats = computed(() => props.app.trendStats || {})
const profile = computed(() => state.value.profile)

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
  const ordered = [...trends.value].sort((a, b) => (Number(b.heatScore) || 0) - (Number(a.heatScore) || 0))
  const index = ordered.findIndex((item) => item.id === selectedTrend.value.id)
  return index >= 0 ? index + 1 : null
})

const heroImage = computed(() => selectedTrend.value?.imageUrl || fallbackLooks[0])
const visibleCards = computed(() => filteredTrends.value.slice(0, 3))
const metricRail = computed(() => [
  {
    label: '可用样本',
    value: trendStats.value.count ?? 0,
    unit: '条',
    note: trendMeta.value.demoMode ? '开发样本集' : '后端返回快照',
    icon: Layers3
  },
  {
    label: '平均热度',
    value: trendStats.value.count ? trendStats.value.averageHeat : '—',
    unit: trendStats.value.count ? '分' : '',
    note: '仅基于当前返回项',
    icon: TrendingUp
  },
  {
    label: '高频标签',
    value: trendStats.value.topTag || '暂无',
    unit: '',
    note: '由当前样本标签计数',
    icon: Tag
  }
])

const selectedFacts = computed(() => {
  const item = selectedTrend.value
  if (!item) return []
  return [
    {
      label: '热度与位次',
      value: item.heatScore ?? '—',
      note: selectedRank.value ? `当前样本第 ${selectedRank.value} 位` : '暂无位次'
    },
    {
      label: '内容标签',
      value: (item.topicTags || []).length,
      note: (item.topicTags || []).join('、') || '暂无标签'
    },
    {
      label: '采集状态',
      value: item.stale ? '已标记过期' : '已采集',
      note: formatDateTime(item.fetchedAt)
    }
  ]
})

watch(tags, (nextTags) => {
  if (!nextTags.includes(activeTag.value)) activeTag.value = '全部'
})

function formatPlatform(value) {
  return platformNames[value] || value || '来源未标记'
}

function formatDateTime(value) {
  if (!value) return '尚未更新'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '尚未更新'
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
  state.value.recommendationForm.styleHint = suggestion
  props.app.selectView('recommend')
}
</script>

<template>
  <div class="trend-page">
    <section class="trend-hero" aria-labelledby="trend-title">
      <div class="hero-intro">
        <div v-if="trendMeta.demoMode" class="demo-banner" role="status">
          <strong>开发样本</strong>
          <span>当前内容用于界面与数据链路联调</span>
        </div>
        <h1 id="trend-title">风潮观察</h1>
        <p>阅读后端已返回的公开趋势快照，看清标签、热度与时间，再决定它是否值得进入你的衣橱。</p>
        <div class="hero-update">
          <span>数据更新 {{ formatDateTime(trendMeta.fetchedAt) }}</span>
          <button
            type="button"
            class="refresh-button"
            :disabled="state.trendsLoading"
            aria-label="刷新风潮数据"
            title="刷新风潮数据"
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
          :alt="selectedTrend?.title || '风潮视觉参考'"
          @error="handleImageError($event, fallbackLooks[0])"
        />
        <figcaption>
          <span>{{ selectedTrend ? formatPlatform(selectedTrend.platform) : '风潮样本' }}</span>
          <strong>{{ selectedTrend?.title || '等待风潮数据' }}</strong>
        </figcaption>
      </figure>
    </section>

    <section class="metric-rail" aria-label="风潮数据概览">
      <article v-for="metric in metricRail" :key="metric.label">
        <component :is="metric.icon" :size="19" aria-hidden="true" />
        <div>
          <span>{{ metric.label }}</span>
          <p><strong>{{ metric.value }}</strong><small v-if="metric.unit">{{ metric.unit }}</small></p>
          <em>{{ metric.note }}</em>
        </div>
      </article>
    </section>

    <section class="trend-browser" aria-labelledby="trend-browser-title">
      <header class="browser-heading">
        <div>
          <p>趋势分类</p>
          <h2 id="trend-browser-title">选一个标签，聚焦当下风格</h2>
        </div>
        <span>{{ filteredTrends.length }} 条匹配</span>
      </header>

      <div class="tag-filters" role="toolbar" aria-label="按标签筛选风潮">
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
        <LoaderCircle class="spinning" :size="22" />正在读取风潮数据…
      </div>
      <div v-else-if="!selectedTrend" class="trend-state">
        <strong>当前没有可用趋势</strong>
        <button type="button" @click="app.loadTrends()">重新读取</button>
      </div>
      <template v-else>
        <article class="selected-feature">
          <div class="feature-media">
            <img
              :src="selectedTrend.imageUrl || fallbackLooks[0]"
              :alt="selectedTrend.title"
              @error="handleImageError($event, fallbackLooks[0])"
            />
            <span><Flame :size="16" />热度 {{ selectedTrend.heatScore ?? '—' }}</span>
          </div>
          <div class="feature-copy">
            <div class="feature-number">NO. {{ String(selectedRank || 1).padStart(2, '0') }}</div>
            <p class="feature-source">{{ formatPlatform(selectedTrend.platform) }} · 发布于 {{ formatDateTime(selectedTrend.publishedAt) }}</p>
            <h2>{{ selectedTrend.title }}</h2>
            <p class="feature-summary">这条趋势的可用信息来自标题、主题标签和后端热度字段。系统不会把热度直接当作个人偏好。</p>
            <div class="feature-tags" aria-label="趋势标签">
              <span v-for="tagName in selectedTrend.topicTags || []" :key="tagName">{{ tagName }}</span>
              <span v-if="!(selectedTrend.topicTags || []).length">暂无标签</span>
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

        <div class="fact-grid" aria-label="当前趋势数据卡">
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
          <p>当前样本</p>
          <h2 id="trend-list-title">三条趋势，三种穿法线索</h2>
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
              <span class="card-meta"><span>{{ formatPlatform(item.platform) }}</span><span>热度 {{ item.heatScore ?? '—' }}</span></span>
              <strong>{{ item.title }}</strong>
              <span class="card-tags">{{ (item.topicTags || []).join(' / ') || '暂无标签' }}</span>
            </span>
          </button>
          <a v-if="item.sourceUrl" :href="item.sourceUrl" target="_blank" rel="noreferrer">
            来源页<ArrowUpRight :size="15" />
          </a>
        </article>
      </div>
    </section>

    <section class="profile-suggestions" aria-labelledby="suggestion-title">
      <header>
        <div>
          <p>个人档案</p>
          <h2 id="suggestion-title">下一件，从你的风格空缺里找</h2>
        </div>
        <Sparkles :size="22" aria-hidden="true" />
      </header>

      <div v-if="state.profileLoading" class="suggestion-state" aria-live="polite">正在读取风格档案…</div>
      <div v-else-if="profile?.itemSuggestions?.length" class="suggestion-list">
        <button
          v-for="(suggestion, index) in profile.itemSuggestions"
          :key="suggestion"
          type="button"
          @click="useSuggestion(suggestion)"
        >
          <span>{{ String(index + 1).padStart(2, '0') }}</span>
          <strong>{{ suggestion }}</strong>
          <em>用于下次推荐</em>
          <ArrowRight :size="17" />
        </button>
      </div>
      <div v-else class="suggestion-state">
        <span>{{ profile ? '当前档案暂无单品建议' : '完善个人档案后，这里会显示单品建议' }}</span>
        <button type="button" @click="app.selectView('profile')">前往个人档案</button>
      </div>
    </section>
  </div>
</template>

<style scoped>
.trend-page {
  width: min(calc(100% - 48px), 1240px);
  margin: 0 auto;
  padding-bottom: 104px;
  color: var(--ink);
}

.trend-hero {
  display: grid;
  min-height: 520px;
  grid-template-columns: minmax(320px, 0.82fr) minmax(460px, 1.18fr);
  gap: 68px;
  align-items: center;
  padding: 48px 0 38px;
}

.hero-intro h1 {
  margin: 0;
  font-family: Georgia, "Songti SC", serif;
  font-size: 60px;
  font-weight: 500;
  line-height: 1.05;
  letter-spacing: 0;
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
  border-radius: var(--radius);
  object-fit: cover;
  object-position: center 35%;
  box-shadow: 0 20px 50px rgba(22, 32, 29, 0.1);
}

.hero-media figcaption {
  position: relative;
  width: min(78%, 460px);
  display: grid;
  gap: 5px;
  margin: -36px 0 0 auto;
  border-left: 3px solid var(--accent);
  border-radius: var(--radius) 0 0 var(--radius);
  padding: 15px 18px;
  background: var(--surface);
  box-shadow: 0 12px 30px rgba(22, 32, 29, 0.1);
}

.hero-media figcaption span {
  color: var(--muted);
  font-size: 10px;
  font-weight: 700;
}

.hero-media figcaption strong {
  font-family: Georgia, "Songti SC", serif;
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
  font-family: Georgia, "Songti SC", serif;
  font-size: 31px;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.metric-rail small {
  flex: 0 0 auto;
  font-size: 12px;
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
  font-family: Georgia, "Songti SC", serif;
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
  font-family: Georgia, serif;
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
  font-family: Georgia, "Songti SC", serif;
  font-size: 38px;
  font-weight: 500;
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
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
  margin-top: 18px;
}

.fact-card {
  min-width: 0;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  padding: 22px;
  background: var(--surface);
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
  font-family: Georgia, "Songti SC", serif;
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
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 15px;
  margin-top: 30px;
}

.trend-card {
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  background: var(--surface);
  transition: border-color 180ms ease, transform 180ms ease;
}

.trend-card:hover,
.trend-card:focus-within,
.trend-card.selected {
  border-color: var(--accent);
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
  font-family: Georgia, "Songti SC", serif;
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
  font-family: Georgia, serif;
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
  font-family: Georgia, "Songti SC", serif;
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
  .profile-suggestions > header {
    display: grid;
    align-items: start;
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
  .card-image img {
    transition: none;
  }
}
</style>
