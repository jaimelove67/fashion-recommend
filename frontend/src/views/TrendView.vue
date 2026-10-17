<script setup>
import { computed, nextTick, onMounted, ref, shallowRef } from 'vue'
import TrendDiscovery from '../components/TrendDiscovery.vue'
import TrendGallery from '../components/TrendGallery.vue'
import OutfitTechniqueExplorer from '../components/OutfitTechniqueExplorer.vue'
import TrendOutfitBreakdown from '../components/TrendOutfitBreakdown.vue'
import TrendAnalysisDialog from '../components/TrendAnalysisDialog.vue'
import { createTrendAnalysisExamples } from '../data/trendOutfitAnalyses.js'
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
const tryOnLauncher = ref(null)
async function openTryOnSetup() {
  showAnalysisExamples.value = false
  await nextTick()
  tryOnLauncher.value?.openInstallation()
}
defineExpose({ openTryOnSetup })

const app = props.app
const state = computed(() => app.state || {})
const showAnalysisExamples = ref(false)
const analysisExamples = createTrendAnalysisExamples(window.location.origin)
const selectedExampleId = ref(analysisExamples[0].id)
const detailTrend = shallowRef(null)
const selectedId = computed(() => showAnalysisExamples.value ? selectedExampleId.value : state.value.selectedTrendId)
const trends = computed(() => showAnalysisExamples.value ? analysisExamples : (Array.isArray(state.value.trends) ? state.value.trends : []))
const galleryTrends = computed(() => showAnalysisExamples.value ? analysisExamples : (Array.isArray(state.value.galleryTrends) ? state.value.galleryTrends : []))
const trendMeta = computed(() => state.value.trendMeta || {})
const dailySelection = computed(() => !showAnalysisExamples.value && !trendMeta.value.demoMode ? trendMeta.value.dailySelection : null)
const dailyCount = computed(() => dailySelection.value ? dailySelection.value.menswear + dailySelection.value.womenswear + dailySelection.value.niche : 0)
const dailyMissing = computed(() => {
  const names = { menswear: '男装', womenswear: '女装', niche: '小众欣赏' }
  return Object.entries(dailySelection.value?.missing || {}).filter(([, count]) => count > 0)
    .map(([category, count]) => `${names[category] || category}${count}套`).join('、')
})
const selectedTrend = computed(() =>
  trends.value.find((item) => item.id === selectedId.value)
    || galleryTrends.value.find((item) => item.id === selectedId.value)
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
  if (item?.id == null) return
  if (showAnalysisExamples.value) selectedExampleId.value = item.id
  else state.value.selectedTrendId = item.id
}

function openAnalysis(item) {
  detailTrend.value = item
}

async function useTrend(item) {
  detailTrend.value = null
  await nextTick()
  await app.useTrend(item)
}

async function openWardrobe() {
  detailTrend.value = null
  await nextTick()
  await app.selectView('wardrobe')
}

onMounted(() => {
  if (state.value.authPhase === 'authenticated' && !state.value.wardrobe?.length && !state.value.wardrobeLoading) {
    void app.loadWardrobe()
  }
})
</script>

<template>
  <div class="trend-page">
    <header class="trend-page-heading">
      <div>
        <p class="eyebrow">鉴赏</p>
        <h1>穿搭鉴赏与风格参考</h1>
        <p>欣赏穿搭作品，学习搭配技巧，结合个人衣橱探索自己的穿法。</p>
      </div>
      <span v-if="trends.length || dailySelection" class="trend-count">{{ showAnalysisExamples ? `${trends.length} 套历史分析示例` : dailySelection ? `每日精选 ${dailyCount} / ${dailySelection.target} 套` : `当前范围收录 ${trends.length} 条` }}</span>
    </header>

    <div v-if="dailySelection" class="trend-daily-composition" aria-label="每日精选收录情况">
      <p><span>男装 {{ dailySelection.menswear }} / 2</span><span>女装 {{ dailySelection.womenswear }} / 2</span><span>小众风格欣赏 {{ dailySelection.niche }} / 1</span></p>
      <p v-if="dailyMissing">待补充：{{ dailyMissing }}。仅展示已通过复核的近期穿搭。</p>
    </div>

    <div class="trend-content-modes" role="group" aria-label="选择穿搭内容">
      <button type="button" :aria-pressed="!showAnalysisExamples" @click="showAnalysisExamples = false">近期内容</button>
      <button type="button" :aria-pressed="showAnalysisExamples" @click="showAnalysisExamples = true">分析示例</button>
    </div>
    <p v-if="showAnalysisExamples" class="trend-example-notice">以下为已核验的历史作品，用于体验完整穿搭分析；保留原始发布日期，不计入近期鉴赏。</p>

    <CrossSiteTryOnLauncher v-if="state.authUser?.username && !showAnalysisExamples" ref="tryOnLauncher" :username="state.authUser.username" />

    <TrendGallery
      v-if="galleryTrends.length"
      :trends="galleryTrends"
      :selected-id="selectedTrend?.id"
      :paused="Boolean(detailTrend)"
      :historical="showAnalysisExamples"
      @select="selectTrend"
    />

    <TrendDiscovery v-if="trends.length && !galleryTrends.length" :app="app" posts-only :limit="0" />

    <div v-if="!selectedTrend && state.trendsLoading" class="trend-page-state" role="status" aria-live="polite">
      <LoaderCircle class="spinning" :size="22" aria-hidden="true" />
      正在加载穿搭鉴赏…
    </div>
    <div v-else-if="!selectedTrend && state.trendError" class="trend-page-state" role="alert">
      <strong>穿搭鉴赏加载失败</strong>
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
      @open-analysis="openAnalysis"
      @use-trend="useTrend"
    />

    <TrendAnalysisDialog
      v-if="detailTrend"
      :key="detailTrend.id"
      :trend="detailTrend"
      :wardrobe="state.wardrobe || []"
      :wardrobe-loading="state.wardrobeLoading"
      @close="detailTrend = null"
      @use-trend="useTrend"
      @open-wardrobe="openWardrobe"
    />

    <OutfitTechniqueExplorer
      v-if="!showAnalysisExamples"
      @open-wardrobe="openWardrobe"
    />

    <details v-if="!showAnalysisExamples" class="trend-source-details">
      <summary>数据来源与收录说明</summary>
      <p>{{ trendMeta.notice || '穿搭内容来自已连接的数据源。各平台数据口径独立展示。' }}</p>
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
  --trend-panel-padding: 24px;
  --trend-section-title-size: clamp(28px, 2.5vw, 34px);
  --trend-section-gap: 40px;
  width: min(calc(100% - 48px), var(--page-width));
  margin: 0 auto;
  padding-bottom: 104px;
  color: var(--ink);
}

.trend-page-heading {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 24px;
  margin: 40px 0 28px;
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
  font-size: clamp(34px, 4vw, 52px);
  font-weight: 600;
  letter-spacing: -.04em;
  line-height: 1.25;
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

.trend-daily-composition { margin:0 0 24px; color:var(--muted); font-size:12px; line-height:1.8; }
.trend-daily-composition p { margin:5px 0; }
.trend-daily-composition p:first-child { display:flex; flex-wrap:wrap; gap:8px 24px; color:var(--accent); }

.trend-content-modes { display:flex; gap:7px; width:fit-content; margin:0 0 20px; padding:5px; border:1px solid var(--line); border-radius:9px; background:var(--surface-soft); }
.trend-content-modes button { min-height:42px; border:1px solid transparent; border-radius:6px; padding:10px 18px; background:transparent; color:var(--muted); font:inherit; font-size:13px; cursor:pointer; }
.trend-content-modes button[aria-pressed="true"] { border-color:var(--line); background:var(--surface); color:var(--accent); }
.trend-content-modes button:focus-visible { outline:2px solid var(--accent); outline-offset:3px; }
.trend-example-notice { margin:0 0 18px; padding:12px 15px; border-left:3px solid var(--accent); background:var(--surface-soft); color:var(--muted); font-size:12px; line-height:1.8; }

.trend-page-state {
  display: flex;
  min-height: 200px;
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
  margin-top: 32px;
  border-top: 1px solid var(--line);
  padding-top: 24px;
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
    --trend-panel-padding: 20px;
    --trend-section-title-size: 28px;
    --trend-section-gap: 32px;
    width: min(calc(100% - 32px), var(--page-width));
  }

  .trend-page-heading {
    display: block;
    margin: 28px 0 24px;
  }

  .trend-page-heading h1 {
    font-size: clamp(28px, 7.5vw, 34px);
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
