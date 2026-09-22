<script setup>
import { computed, onMounted } from 'vue'
import TrendDiscovery from '../components/TrendDiscovery.vue'
import TrendGallery from '../components/TrendGallery.vue'
import TrendOutfitBreakdown from '../components/TrendOutfitBreakdown.vue'
import { LoaderCircle } from '@lucide/vue'

const props = defineProps({
  app: { type: Object, required: true }
})

const app = props.app
const state = computed(() => app.state || {})
const trends = computed(() => Array.isArray(state.value.trends) ? state.value.trends : [])
const trendMeta = computed(() => state.value.trendMeta || {})
const selectedTrend = computed(() =>
  trends.value.find((item) => item.id === state.value.selectedTrendId) || trends.value[0] || null
)

const platformNames = {
  douyin: '抖音',
  weibo: '微博',
  editorial: '时尚编辑精选',
  'configured-feed': '配置来源',
  'web-scrape': '公开网页'
}
const sourceStateNames = {
  ready: '已连接',
  unavailable: '采集失败',
  unconfigured: '未接通',
  pending: '等待采集'
}

function formatPlatform(value) {
  return platformNames[value] || '授权来源'
}

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
        <p class="eyebrow">穿搭灵感</p>
        <h1>拆开看，一种趋势怎么穿</h1>
        <p>先看来源里真实出现的穿搭线索，再用衣橱里的衣物试搭。</p>
      </div>
      <span v-if="trends.length" class="trend-count">当前范围收录 {{ trends.length }} 条</span>
    </header>

    <TrendGallery
      v-if="trends.length"
      :trends="trends"
      :selected-id="selectedTrend?.id"
      @select="selectTrend"
    />

    <div v-if="!selectedTrend && state.trendsLoading" class="trend-page-state" role="status" aria-live="polite">
      <LoaderCircle class="spinning" :size="22" aria-hidden="true" />
      正在读取趋势内容…
    </div>
    <div v-else-if="!selectedTrend && state.trendError" class="trend-page-state" role="alert">
      <strong>趋势暂时无法加载</strong>
      <span>{{ state.trendError }}</span>
      <button type="button" @click="app.loadTrends()">重新加载</button>
    </div>
    <div v-else-if="!selectedTrend" class="trend-page-state">
      <strong>这个范围还没有可用的穿搭内容</strong>
      <span>试试近 7 天或其他来源。</span>
      <button type="button" @click="app.loadTrends()">重新加载</button>
    </div>
    <TrendOutfitBreakdown
      v-else
      :trend="selectedTrend"
      :wardrobe="state.wardrobe || []"
      :wardrobe-loading="state.wardrobeLoading"
      @use-trend="app.useTrend"
      @open-wardrobe="app.selectView('wardrobe')"
    />

    <details class="trend-source-details">
      <summary>来源与采集说明</summary>
      <p>{{ trendMeta.notice || '趋势内容来自已连接的数据源。各平台数据口径独立展示。' }}</p>
      <ul>
        <li v-for="source in trendMeta.sources || []" :key="source.id">
          <strong>{{ platformNames[source.id] || source.id }}</strong>
          <span>{{ sourceStateNames[source.state] || '状态未知' }} · {{ source.message }}</span>
          <span v-if="source.itemCount">收录 {{ source.itemCount }} 条</span>
          <time v-if="source.lastSuccessAt">最后成功 {{ formatDateTime(source.lastSuccessAt) }}</time>
        </li>
      </ul>
      <button
        v-if="app.isAdmin"
        type="button"
        class="source-refresh"
        :disabled="state.trendsLoading"
        @click="app.refreshTrendSources()"
      >更新已连接来源</button>
    </details>
  </div>
</template>

<style scoped>
.trend-page {
  width: min(calc(100% - 48px), 1240px);
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
  font-weight: 700;
  letter-spacing: .08em;
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
    width: min(calc(100% - 32px), 1240px);
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
