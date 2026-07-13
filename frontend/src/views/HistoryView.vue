<script setup>
import { computed, ref } from 'vue'
import {
  Bookmark,
  ChevronDown,
  Clock3,
  CloudSun,
  History,
  LoaderCircle,
  RefreshCw,
  Search,
  Shirt,
  Sparkles,
  Star,
  X
} from '@lucide/vue'

const props = defineProps({
  app: { type: Object, required: true }
})

const query = ref('')
const activeFilter = ref('all')
const expandedId = ref(null)
const brokenImages = ref(new Set())

const filters = [
  { id: 'all', label: '全部' },
  { id: 'saved', label: '已收藏' },
  { id: 'rated', label: '已评分' }
]

const ratedCount = computed(() => props.app.state.history.filter((item) => Number.isFinite(item.feedback?.rating)).length)
const maxTrendCount = computed(() => Math.max(1, ...props.app.historyTrend.map((item) => item.count)))
const filteredHistory = computed(() => {
  const normalized = query.value.trim().toLocaleLowerCase('zh-CN')
  return props.app.state.history.filter((item) => {
    if (activeFilter.value === 'saved' && !item.saved) return false
    if (activeFilter.value === 'rated' && !Number.isFinite(item.feedback?.rating)) return false
    if (!normalized) return true
    const itemText = (item.items || []).map((garment) => `${garment.name} ${garment.category} ${garment.color} ${garment.style || ''}`).join(' ')
    return `${item.summary} ${item.reason} ${item.occasion} ${item.city} ${itemText}`.toLocaleLowerCase('zh-CN').includes(normalized)
  })
})

function formatRating(value) {
  return Number.isFinite(value) ? value.toFixed(1) : '--'
}

function formatDate(value) {
  if (!value) return '时间未记录'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '时间未记录'
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  }).format(date)
}

function imageFailed(key) {
  brokenImages.value.add(key)
}

function clearFilters() {
  query.value = ''
  activeFilter.value = 'all'
}
</script>

<template>
  <section class="history-view">
    <header class="page-hero">
      <div>
        <p class="eyebrow"><History :size="15" aria-hidden="true" />推荐历史</p>
        <h1>每一次生成，都有据可查。</h1>
        <p>搜索真实推荐记录，回看当时使用的衣物、天气快照、收藏状态和评分。</p>
      </div>
      <button class="refresh-button" type="button" :disabled="app.state.historyLoading" @click="app.loadHistory">
        <LoaderCircle v-if="app.state.historyLoading" class="spinning" :size="17" />
        <RefreshCw v-else :size="17" aria-hidden="true" />
        刷新记录
      </button>
    </header>

    <section class="stats-band" aria-label="历史记录统计">
      <article>
        <span>全部方案</span>
        <strong>{{ app.recommendationStats.total }}</strong>
        <small>服务端返回记录</small>
      </article>
      <article>
        <span>已收藏</span>
        <strong>{{ app.savedHistory.length }}</strong>
        <small>已确认保存</small>
      </article>
      <article>
        <span>已评分</span>
        <strong>{{ ratedCount }}</strong>
        <small>提交过反馈的方案</small>
      </article>
      <article>
        <span>平均评分</span>
        <strong>{{ formatRating(app.recommendationStats.averageRating) }}<em v-if="app.recommendationStats.averageRating"> / 5</em></strong>
        <small>{{ app.recommendationStats.averageRating ? '仅统计有效评分' : '尚无评分数据' }}</small>
      </article>
    </section>

    <section class="trend-section" aria-labelledby="history-trend-title">
      <header class="section-heading">
        <div>
          <p class="section-kicker">最近 7 天</p>
          <h2 id="history-trend-title">推荐与收藏趋势</h2>
        </div>
        <div class="chart-legend" aria-label="图例"><span><i></i>生成</span><span><i></i>收藏</span></div>
      </header>

      <div class="history-chart" role="img" aria-label="最近七天推荐生成与收藏数量柱状图">
        <div v-for="day in app.historyTrend" :key="day.key" class="chart-column">
          <div class="bar-area">
            <span
              class="bar total-bar"
              :class="{ empty: day.count === 0 }"
              :style="{ height: `${day.count ? Math.max(12, day.count / maxTrendCount * 100) : 3}%` }"
              :title="`${day.label} 生成 ${day.count} 条`"
            ></span>
            <span
              class="bar saved-bar"
              :class="{ empty: day.saved === 0 }"
              :style="{ height: `${day.saved ? Math.max(12, day.saved / maxTrendCount * 100) : 3}%` }"
              :title="`${day.label} 收藏 ${day.saved} 条`"
            ></span>
          </div>
          <strong>{{ day.count }}<small v-if="day.saved"> / {{ day.saved }}</small></strong>
          <span>{{ day.label }}</span>
        </div>
      </div>
    </section>

    <section class="records-section" aria-labelledby="records-title">
      <header class="records-header">
        <div>
          <p class="section-kicker">全部记录</p>
          <h2 id="records-title">方案档案</h2>
        </div>
        <span>当前显示 {{ filteredHistory.length }} / {{ app.state.history.length }} 条</span>
      </header>

      <div class="record-tools">
        <div class="filter-group" role="group" aria-label="筛选推荐历史">
          <button
            v-for="filter in filters"
            :key="filter.id"
            type="button"
            :class="{ active: activeFilter === filter.id }"
            :aria-pressed="activeFilter === filter.id"
            @click="activeFilter = filter.id"
          >
            {{ filter.label }}
          </button>
        </div>
        <label class="search-field">
          <Search :size="17" aria-hidden="true" />
          <input v-model="query" type="search" placeholder="搜索场合、城市、方案或衣物" aria-label="搜索推荐历史" />
          <button v-if="query" type="button" aria-label="清空搜索" @click="query = ''"><X :size="15" /></button>
        </label>
      </div>

      <div v-if="app.state.historyLoading" class="state-panel" aria-live="polite">
        <LoaderCircle class="spinning" :size="27" />
        <strong>正在读取推荐历史</strong>
        <span>服务端数据返回后会保留当前筛选条件。</span>
      </div>

      <div v-else-if="filteredHistory.length" class="history-list">
        <article v-for="item in filteredHistory" :key="item.id" class="history-card">
          <header class="card-header">
            <div class="record-meta">
              <span>#{{ item.id }}</span>
              <span><Clock3 :size="13" />{{ formatDate(item.generatedAt) }}</span>
              <span v-if="item.city"><CloudSun :size="13" />{{ item.city }}<template v-if="item.temperatureC !== null && item.temperatureC !== undefined"> · {{ item.temperatureC }}°C</template></span>
            </div>
            <span class="saved-label" :class="{ saved: item.saved }"><Bookmark :size="14" :fill="item.saved ? 'currentColor' : 'none'" />{{ item.saved ? '已收藏' : '未收藏' }}</span>
          </header>

          <div class="card-title">
            <div>
              <p>{{ item.occasion || '未标注场合' }}</p>
              <h3>{{ item.summary }}</h3>
            </div>
            <button type="button" :aria-expanded="expandedId === item.id" @click="expandedId = expandedId === item.id ? null : item.id">
              {{ expandedId === item.id ? '收起详情' : '查看详情' }}
              <ChevronDown :size="15" :class="{ rotated: expandedId === item.id }" />
            </button>
          </div>

          <ul class="garment-strip" :aria-label="`${item.summary}的衣物`">
            <li v-for="garment in item.items || []" :key="`${item.id}-${garment.id}`">
              <div class="garment-media">
                <img
                  v-if="garment.imageUrl && !brokenImages.has(`${item.id}-${garment.id}`)"
                  :src="garment.imageUrl"
                  :alt="garment.name"
                  @error="imageFailed(`${item.id}-${garment.id}`)"
                />
                <span v-else :style="{ backgroundColor: app.colorFor(garment.color) }"><Shirt :size="19" /></span>
              </div>
              <div><strong>{{ garment.name }}</strong><small>{{ garment.category }} · {{ garment.color }}</small></div>
            </li>
          </ul>

          <div v-if="expandedId === item.id" class="record-details">
            <div><span>推荐理由</span><p>{{ item.reason }}</p></div>
            <div><span>生成来源</span><p>{{ item.engine }}</p></div>
            <div v-if="item.weather">
              <span>天气快照</span>
              <p>{{ item.weather.temperatureC }}°C · 体感 {{ item.weather.apparentTemperatureC }}°C · 降水 {{ item.weather.precipitationMm }} mm · 风速 {{ item.weather.windSpeedKmh }} km/h · {{ item.weather.source }}</p>
            </div>
          </div>

          <footer class="card-footer">
            <div class="rating-control">
              <span>{{ item.feedback?.rating ? `已评 ${item.feedback.rating} 星` : '为这套方案评分' }}</span>
              <div>
                <button
                  v-for="rating in 5"
                  :key="rating"
                  type="button"
                  :class="{ rated: item.feedback?.rating >= rating }"
                  :disabled="app.state.feedbackSavingId === item.id"
                  :aria-label="`为方案 ${item.id} 评 ${rating} 星`"
                  @click="app.rateRecommendation(item, rating)"
                >
                  <Star :size="18" :fill="item.feedback?.rating >= rating ? 'currentColor' : 'none'" />
                </button>
              </div>
            </div>
            <button
              class="save-button"
              type="button"
              :class="{ saved: item.saved }"
              :disabled="app.state.saving || item.saved"
              @click="app.saveRecommendation(item)"
            >
              <LoaderCircle v-if="app.state.saving && !item.saved" class="spinning" :size="16" />
              <Bookmark v-else :size="16" :fill="item.saved ? 'currentColor' : 'none'" />
              {{ item.saved ? '已收藏' : '收藏方案' }}
            </button>
          </footer>
        </article>
      </div>

      <div v-else class="state-panel empty-state">
        <Sparkles :size="29" aria-hidden="true" />
        <strong>{{ app.state.history.length ? '没有符合条件的记录' : '还没有推荐历史' }}</strong>
        <span>{{ app.state.history.length ? '试着清空搜索或切换筛选条件。' : '完成一次真实推荐后，记录会自动出现在这里。' }}</span>
        <button v-if="app.state.history.length" type="button" @click="clearFilters">清空筛选</button>
        <button v-else type="button" @click="app.selectView('recommend')">去生成第一套方案</button>
      </div>
    </section>
  </section>
</template>

<style scoped>
.history-view {
  width: min(1180px, 100%);
  margin: 0 auto;
  padding-bottom: 76px;
  color: var(--ink);
}

.page-hero {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 40px;
  padding: 72px 0 42px;
}

.page-hero > div { max-width: 720px; }
.eyebrow,
.section-kicker {
  display: flex;
  align-items: center;
  gap: 7px;
  margin: 0 0 12px;
  color: var(--accent);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0;
}

.page-hero h1 {
  margin: 0;
  font-family: Georgia, "Songti SC", serif;
  font-size: 48px;
  font-weight: 500;
  line-height: 1.08;
  letter-spacing: 0;
}

.page-hero > div > p:last-child { margin: 19px 0 0; color: var(--muted); font-size: 14px; line-height: 1.8; }
.refresh-button,
.save-button,
.empty-state > button {
  display: inline-flex;
  min-height: 42px;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border: 1px solid var(--ink);
  border-radius: 5px;
  padding: 9px 15px;
  color: var(--surface);
  background: var(--ink);
  font-size: 12px;
  font-weight: 700;
}

.stats-band {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  border: 1px solid var(--line);
  border-radius: var(--radius);
  background: var(--surface);
}

.stats-band article { min-width: 0; padding: 24px 27px; border-right: 1px solid var(--line); }
.stats-band article:last-child { border-right: 0; }
.stats-band span,
.stats-band small { display: block; color: var(--muted); font-size: 11px; }
.stats-band strong { display: block; margin: 8px 0 7px; font-family: Georgia, serif; font-size: 31px; font-weight: 500; line-height: 1; }
.stats-band em { color: var(--muted); font: 400 13px/1 sans-serif; }

.trend-section { padding: 64px 0 0; }
.section-heading,
.records-header {
  display: flex;
  align-items: start;
  justify-content: space-between;
  gap: 24px;
}

.section-heading h2,
.records-header h2 {
  margin: 0;
  font-family: Georgia, "Songti SC", serif;
  font-size: 27px;
  font-weight: 500;
  line-height: 1.2;
  letter-spacing: 0;
}

.chart-legend { display: flex; gap: 17px; color: var(--muted); font-size: 10px; }
.chart-legend span { display: flex; align-items: center; gap: 6px; }
.chart-legend i { width: 8px; height: 8px; background: var(--ink); }
.chart-legend span:last-child i { background: var(--accent); }
.history-chart {
  display: grid;
  grid-template-columns: repeat(7, minmax(52px, 1fr));
  gap: 13px;
  min-height: 230px;
  margin-top: 25px;
  border-top: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
  padding: 23px 12px 18px;
}

.chart-column { display: grid; grid-template-rows: 145px auto auto; gap: 5px; min-width: 0; text-align: center; }
.bar-area { display: flex; height: 145px; align-items: end; justify-content: center; gap: 5px; }
.bar { display: block; width: 13px; min-height: 4px; background: var(--ink); transition: height .2s ease; }
.saved-bar { background: var(--accent); }
.bar.empty { background: var(--line); }
.chart-column strong { font-family: Georgia, serif; font-size: 15px; font-weight: 500; }
.chart-column strong small { color: var(--accent); font: 500 9px/1 sans-serif; }
.chart-column > span { color: var(--muted); font-size: 10px; }

.records-section { padding-top: 64px; }
.records-header > span { color: var(--muted); font-size: 11px; }
.record-tools {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  margin: 25px 0 22px;
  border-top: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
  padding: 14px 0;
}

.filter-group { display: flex; gap: 4px; }
.filter-group button {
  min-height: 34px;
  border: 0;
  border-radius: 4px;
  padding: 7px 13px;
  color: var(--muted);
  background: transparent;
  font-size: 11px;
}

.filter-group button.active { color: var(--surface); background: var(--ink); }
.search-field {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  width: min(360px, 100%);
  min-height: 40px;
  border: 1px solid var(--line);
  border-radius: 4px;
  padding: 0 10px;
  color: var(--muted);
  background: var(--surface);
}

.search-field input { width: 100%; min-width: 0; border: 0; outline: 0; padding: 9px; color: var(--ink); background: transparent; font-size: 12px; }
.search-field button { display: grid; width: 28px; height: 28px; place-items: center; border: 0; padding: 0; color: var(--muted); background: transparent; }
.history-list { display: grid; gap: 16px; }
.history-card { border: 1px solid var(--line); border-radius: var(--radius); padding: 24px; background: var(--surface); }
.card-header,
.card-title,
.card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
}

.record-meta { display: flex; flex-wrap: wrap; align-items: center; gap: 9px 16px; color: var(--muted); font-size: 10px; }
.record-meta span { display: inline-flex; align-items: center; gap: 5px; }
.record-meta span:first-child { color: var(--accent); font-weight: 700; }
.saved-label { display: inline-flex; align-items: center; gap: 5px; color: var(--muted); font-size: 10px; }
.saved-label.saved { color: var(--accent); }
.card-title { align-items: end; margin-top: 15px; }
.card-title p { margin: 0 0 5px; color: var(--accent); font-size: 11px; }
.card-title h3 { max-width: 780px; margin: 0; font-family: Georgia, "Songti SC", serif; font-size: 25px; font-weight: 500; line-height: 1.3; letter-spacing: 0; }
.card-title > button {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 5px;
  border: 0;
  padding: 5px 0;
  color: var(--muted);
  background: transparent;
  font-size: 10px;
}

.card-title svg { transition: transform .18s ease; }
.card-title svg.rotated { transform: rotate(180deg); }
.garment-strip {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  margin: 21px 0 0;
  padding: 0;
  border-top: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
  list-style: none;
}

.garment-strip li {
  display: grid;
  grid-template-columns: 48px minmax(0, 1fr);
  gap: 10px;
  align-items: center;
  min-width: 0;
  padding: 13px 11px;
  border-right: 1px solid var(--line);
}

.garment-strip li:last-child { border-right: 0; }
.garment-media { width: 48px; height: 54px; overflow: hidden; border-radius: 3px; background: var(--bg); }
.garment-media img { width: 100%; height: 100%; object-fit: cover; }
.garment-media span { display: grid; width: 100%; height: 100%; place-items: center; color: var(--surface); }
.garment-strip strong { display: block; overflow: hidden; font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.garment-strip small { display: block; overflow: hidden; margin-top: 5px; color: var(--muted); font-size: 9px; text-overflow: ellipsis; white-space: nowrap; }
.record-details { display: grid; grid-template-columns: 1.5fr .7fr 1fr; border-bottom: 1px solid var(--line); }
.record-details > div { min-width: 0; padding: 18px 18px 18px 0; }
.record-details > div + div { border-left: 1px solid var(--line); padding-left: 18px; }
.record-details span { color: var(--accent); font-size: 10px; font-weight: 700; }
.record-details p { margin: 6px 0 0; color: var(--muted); font-size: 11px; line-height: 1.65; }
.card-footer { padding-top: 17px; }
.rating-control { display: flex; align-items: center; gap: 11px; color: var(--muted); font-size: 10px; }
.rating-control > div { display: flex; gap: 1px; }
.rating-control button { display: grid; width: 29px; height: 29px; place-items: center; border: 0; padding: 0; color: var(--line); background: transparent; }
.rating-control button:hover,
.rating-control button:focus-visible,
.rating-control button.rated { color: var(--accent); }
.save-button { min-height: 36px; padding: 7px 12px; }
.save-button.saved { border-color: var(--line); color: var(--accent); background: var(--accent-soft); }
.state-panel {
  display: grid;
  min-height: 330px;
  place-items: center;
  place-content: center;
  gap: 10px;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  color: var(--muted);
  background: var(--surface);
  text-align: center;
}

.state-panel > svg { color: var(--accent); }
.state-panel strong { color: var(--ink); font-family: Georgia, "Songti SC", serif; font-size: 21px; font-weight: 500; }
.state-panel span { max-width: 420px; font-size: 12px; line-height: 1.7; }
.empty-state > button { margin-top: 7px; }
.spinning { animation: spin 1s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

@media (max-width: 900px) {
  .page-hero { padding-top: 54px; }
  .page-hero h1 { font-size: 42px; }
  .stats-band { grid-template-columns: repeat(2, 1fr); }
  .stats-band article:nth-child(2) { border-right: 0; }
  .stats-band article:nth-child(-n + 2) { border-bottom: 1px solid var(--line); }
  .garment-strip { grid-template-columns: repeat(2, 1fr); }
  .garment-strip li:nth-child(2) { border-right: 0; }
  .garment-strip li:nth-child(-n + 2) { border-bottom: 1px solid var(--line); }
  .record-details { grid-template-columns: 1fr; }
  .record-details > div { padding: 15px 0; }
  .record-details > div + div { border-top: 1px solid var(--line); border-left: 0; padding-left: 0; }
}

@media (max-width: 680px) {
  .page-hero { flex-direction: column; align-items: stretch; gap: 25px; padding: 42px 0 30px; }
  .page-hero h1 { font-size: 36px; }
  .refresh-button { width: 100%; }
  .stats-band { grid-template-columns: 1fr; }
  .stats-band article { border-right: 0; border-bottom: 1px solid var(--line); padding: 20px; }
  .stats-band article:last-child { border-bottom: 0; }
  .history-chart { grid-template-columns: repeat(7, minmax(38px, 1fr)); gap: 5px; overflow-x: auto; padding-right: 0; padding-left: 0; }
  .bar { width: 9px; }
  .record-tools { align-items: stretch; flex-direction: column; }
  .filter-group { display: grid; grid-template-columns: repeat(3, 1fr); }
  .search-field { width: 100%; }
  .history-card { padding: 19px; }
  .card-title { align-items: start; flex-direction: column; }
  .card-title h3 { font-size: 22px; }
  .garment-strip { grid-template-columns: 1fr; }
  .garment-strip li,
  .garment-strip li:nth-child(2) { border-right: 0; border-bottom: 1px solid var(--line); }
  .garment-strip li:last-child { border-bottom: 0; }
  .card-footer,
  .rating-control { align-items: stretch; flex-direction: column; }
  .save-button { width: 100%; }
}
</style>
