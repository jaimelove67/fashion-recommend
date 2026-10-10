<script setup>
import { computed, onMounted } from 'vue'
import TrendDiscovery from '../components/TrendDiscovery.vue'
import TrendGallery from '../components/TrendGallery.vue'
import TrendPieceExplorer from '../components/TrendPieceExplorer.vue'
import TrendOutfitBreakdown from '../components/TrendOutfitBreakdown.vue'
import CrossSiteTryOnLauncher from '../components/CrossSiteTryOnLauncher.vue'
import { LoaderCircle } from '@lucide/vue'
import {
  trendPlatformName,
  trendSourceCounts,
  trendSourceExclusionReason,
  trendSourceMessage
} from '../utils/trendSources.js'

const props = defineProps({
  app: { type: Object, required: true }
})
const emit = defineEmits(['open-try-on'])

const app = props.app
const state = computed(() => app.state || {})
const trends = computed(() => Array.isArray(state.value.trends) ? state.value.trends : [])
const galleryTrends = computed(() => Array.isArray(state.value.galleryTrends) ? state.value.galleryTrends : [])
const trendMeta = computed(() => state.value.trendMeta || {})
const selectedTrend = computed(() =>
  trends.value.find((item) => item.id === state.value.selectedTrendId)
    || galleryTrends.value.find((item) => item.id === state.value.selectedTrendId)
    || trends.value[0]
    || galleryTrends.value[0]
    || null
)
const isEmptyFeed = computed(() => {
  if (state.value.trendsLoading || state.value.trendError) return false
  return trends.value.length === 0 && galleryTrends.value.length === 0
})

function formatDateTime(value) {
  if (!value) return '发布时间未提供'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '发布时间未提供'
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false
  })
}

function selectTrend(item) {
  if (item?.id) state.value.selectedTrendId = item.id
}

onMounted(() => {
  if (state.value.authPhase === 'authenticated' && !state.value.wardrobe?.length && !state.value.wardrobeLoading) {
    void app.loadWardrobe()
  }
})
</script>

<template>
  <div class="trend-page">
    <TrendDiscovery :app="app" controls-only />

    <header class="trend-page-heading">
      <div>
        <p class="eyebrow">趋势</p>
        <h1>穿搭趋势与风格参考</h1>
        <p>浏览穿搭内容与单品建议，结合个人衣橱探索搭配方案。</p>
      </div>
      <span v-if="trends.length" class="trend-count">当前范围收录 {{ trends.length }} 条</span>
    </header>

    <CrossSiteTryOnLauncher v-if="state.authUser?.username" :username="state.authUser.username" />

    <TrendGallery
      v-if="galleryTrends.length"
      :trends="galleryTrends"
      :selected-id="selectedTrend?.id"
      @select="selectTrend"
    />

    <TrendDiscovery v-if="trends.length && !galleryTrends.length" :app="app" posts-only :limit="0" />

    <div v-if="!selectedTrend && state.trendsLoading" class="trend-page-state" role="status" aria-live="polite">
      <LoaderCircle class="spinning" :size="22" aria-hidden="true" />
      正在加载穿搭趋势…
    </div>
    <div v-else-if="!selectedTrend && state.trendError" class="trend-page-state" role="alert">
      <strong>穿搭趋势加载失败</strong>
      <span>{{ state.trendError }}</span>
      <button type="button" @click="app.loadTrends()">重新加载</button>
    </div>
    <div v-else-if="isEmptyFeed" class="trend-page-state trend-empty-state">
      <div class="trend-empty-copy">
        <strong>当前筛选范围暂无穿搭内容</strong>
        <span>数据源恢复并有符合筛选条件的内容后，将在此展示。</span>
      </div>
      <button type="button" @click="app.loadTrends()">重新加载</button>
    </div>
    <TrendOutfitBreakdown
      v-if="selectedTrend"
      :trend="selectedTrend"
      :wardrobe="state.wardrobe || []"
      :wardrobe-loading="state.wardrobeLoading"
      @use-trend="app.useTrend"
      @open-wardrobe="app.selectView('wardrobe')"
    />

    <TrendPieceExplorer
      :trends="trends"
      :weather="state.weather"
      :loading="state.trendsLoading"
      :error="state.trendError"
      @open-try-on="emit('open-try-on', $event)"
    />

    <details class="trend-source-details">
      <summary>数据来源与收录说明</summary>
      <p>{{ trendMeta.notice || '趋势内容来自已连接的数据源。各平台数据口径独立展示。' }}</p>
      <ul>
        <li v-for="source in trendMeta.sources || []" :key="source.id">
          <strong>{{ trendPlatformName(source.id) }}</strong>
          <span>{{ trendSourceMessage(source) }}</span>
          <span v-if="trendSourceCounts(source)">{{ trendSourceCounts(source) }}</span>
          <span v-if="trendSourceExclusionReason(source)">原因：{{ trendSourceExclusionReason(source) }}</span>
          <time v-if="source.lastSuccessAt">最近成功更新 {{ formatDateTime(source.lastSuccessAt) }}</time>
        </li>
      </ul>
      <button
        v-if="app.isAdmin"
        type="button"
        class="source-refresh"
        :disabled="state.trendsLoading"
        @click="app.refreshTrendSources()"
      >刷新已连接数据源</button>
    </details>
  </div>
</template>

<style scoped>
.trend-page {
  width: min(calc(100% - 48px), 1440px);
  margin: 0 auto;
  padding-bottom: 104px;
  color: var(--ink);
}

.trend-page-heading {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 28px;
  margin: 34px 0;
}

.eyebrow {
  margin: 0 0 12px;
  color: var(--muted);
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0;
}

.trend-page-heading h1 {
  margin: 0;
  font-family: var(--font-display);
  font-size: clamp(38px, 5vw, 62px);
  font-weight: 600;
  letter-spacing: -.04em;
  line-height: 1.12;
}

.trend-page-heading > div > p:last-child {
  max-width: 690px;
  margin: 16px 0 0;
  color: var(--muted);
  font-size: 15px;
  line-height: 1.8;
}

.trend-count {
  flex: 0 0 auto;
  color: var(--muted);
  font-size: 12px;
}

.trend-page-state {
  display: flex;
  min-height: 280px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  border-top: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
  color: var(--muted);
  font-size: 14px;
  text-align: center;
}

.trend-page-state strong {
  color: var(--ink);
  font-family: var(--font-display);
  font-size: 24px;
  font-weight: 500;
}

.trend-empty-state {
  gap: 24px;
  padding: 40px 24px;
}

.trend-empty-copy {
  display: grid;
  gap: 8px;
}

.trend-page-state button,
.source-refresh {
  min-height: 40px;
  border: 1px solid var(--line-strong);
  border-radius: var(--radius);
  padding: 8px 14px;
  color: var(--ink);
  background: var(--surface);
}

.trend-page-state button:hover,
.trend-page-state button:focus-visible,
.source-refresh:hover,
.source-refresh:focus-visible {
  border-color: var(--accent);
  outline: 0;
}

.trend-source-details {
  margin-top: 42px;
  border-top: 1px solid var(--line);
  padding-top: 20px;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.7;
}

.trend-source-details summary {
  width: fit-content;
  cursor: pointer;
  color: var(--ink);
  font-weight: 700;
}

.trend-source-details ul {
  display: grid;
  gap: 10px;
  margin: 18px 0;
  padding: 0;
  list-style: none;
}

.trend-source-details li {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
}

.trend-source-details li strong {
  color: var(--ink);
}

.source-refresh {
  margin-top: 4px;
  font-size: 12px;
}

.spinning {
  animation: trend-spin 1s linear infinite;
}

@keyframes trend-spin {
  to { transform: rotate(360deg); }
}

@media (max-width: 760px) {
  .trend-page {
    width: min(calc(100% - 32px), 1440px);
  }

  .trend-page-heading {
    display: block;
    margin: 28px 0;
  }

  .trend-page-heading h1 {
    font-size: clamp(28px, 8vw, 36px);
    text-wrap: balance;
  }

  .trend-count {
    display: block;
    margin-top: 18px;
  }

}

@media (prefers-reduced-motion: reduce) {
  .spinning {
    animation: none;
  }
}
</style>
