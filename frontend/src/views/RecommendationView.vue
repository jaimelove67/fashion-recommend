<script setup>
import { computed, ref } from 'vue'
import {
  ArrowUpRight,
  Bookmark,
  CloudSun,
  ClipboardList,
  Flame,
  LoaderCircle,
  MapPin,
  RefreshCw,
  Shirt,
  Sparkles,
  Star,
  TrendingUp
} from '@lucide/vue'

const props = defineProps({
  app: { type: Object, required: true }
})

const formOpen = ref(!props.app.state.currentRecommendation)
const brokenImages = ref(new Set())

const trendPreview = computed(() => props.app.state.trends.slice(0, 3))
const coveragePercent = computed(() => {
  const total = props.app.wardrobeStats.total
  if (!total) return null
  return Math.min(100, Math.round((props.app.recommendationStats.coveredItems / total) * 100))
})

function formatRating(value) {
  return Number.isFinite(value) ? value.toFixed(1) : '--'
}

function formatDate(value) {
  if (!value) return '时间未记录'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '时间未记录'
  return new Intl.DateTimeFormat('zh-CN', {
    month: 'numeric',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  }).format(date)
}

function imageFailed(key) {
  brokenImages.value.add(key)
}
</script>

<template>
  <section class="recommendation-view">
    <header class="page-hero">
      <div class="hero-copy">
        <p class="eyebrow"><Sparkles :size="15" aria-hidden="true" />智能穿搭推荐</p>
        <h1>从你的衣橱，生成今天的答案。</h1>
        <p>场合、城市与个人偏好会交给后端主链路，页面只呈现真实返回的衣物和推荐依据。</p>
      </div>
      <button
        class="primary-action"
        type="button"
        :aria-expanded="formOpen"
        aria-controls="recommendation-form"
        @click="formOpen = !formOpen"
      >
        <Sparkles :size="17" aria-hidden="true" />
        {{ formOpen ? '收起生成条件' : '生成新搭配' }}
      </button>
    </header>

    <section class="stats-band" aria-label="推荐统计">
      <article>
        <span>历史方案</span>
        <strong>{{ app.recommendationStats.total }}</strong>
        <small>后端已返回的推荐记录</small>
      </article>
      <article>
        <span>已收藏</span>
        <strong>{{ app.recommendationStats.saved }}</strong>
        <small>已确认保存的方案</small>
      </article>
      <article>
        <span>平均反馈</span>
        <strong>{{ formatRating(app.recommendationStats.averageRating) }}<em v-if="app.recommendationStats.averageRating"> / 5</em></strong>
        <small>{{ app.recommendationStats.averageRating ? '基于已提交评分' : '尚无评分数据' }}</small>
      </article>
      <article>
        <span>覆盖衣物</span>
        <strong>{{ app.recommendationStats.coveredItems }}</strong>
        <small>历史方案中出现过的单品</small>
      </article>
    </section>

    <section id="recommendation-form" v-show="formOpen" class="generator-panel" aria-labelledby="generator-title">
      <header class="panel-heading">
        <div>
          <p class="section-kicker">生成条件</p>
          <h2 id="generator-title">这次要去哪里？</h2>
        </div>
        <button class="quiet-button" type="button" :disabled="app.state.weatherLoading || !app.state.recommendationForm.city" @click="app.loadWeather">
          <LoaderCircle v-if="app.state.weatherLoading" class="spinning" :size="16" />
          <RefreshCw v-else :size="16" aria-hidden="true" />
          更新天气
        </button>
      </header>

      <form class="recommendation-form" @submit.prevent="app.generateRecommendation">
        <div class="form-fields">
          <label>
            <span>场合</span>
            <input v-model.trim="app.state.recommendationForm.occasion" required maxlength="80" placeholder="例如：通勤、约会、周末出行" />
          </label>
          <label>
            <span>城市</span>
            <input v-model.trim="app.state.recommendationForm.city" required maxlength="80" placeholder="例如：长沙" />
          </label>
          <label class="wide-field">
            <span>风格提示 <small>可选</small></span>
            <input v-model.trim="app.state.recommendationForm.styleHint" maxlength="120" placeholder="例如：低饱和、利落、适合步行" />
          </label>
        </div>

        <div class="form-footer">
          <div v-if="app.state.weather" class="weather-preview">
            <CloudSun :size="20" aria-hidden="true" />
            <p>
              <strong>{{ app.state.weather.city }}</strong>
              <span>{{ app.state.weather.temperatureC }}°C · 体感 {{ app.state.weather.apparentTemperatureC }}°C · 降水 {{ app.state.weather.precipitationMm }} mm</span>
            </p>
            <small>{{ app.state.weather.source }}</small>
          </div>
          <p v-else class="weather-empty"><MapPin :size="17" aria-hidden="true" />填写城市后可先读取实时天气</p>

          <button class="generate-button" type="submit" :disabled="app.state.generating">
            <LoaderCircle v-if="app.state.generating" class="spinning" :size="18" />
            <Sparkles v-else :size="18" aria-hidden="true" />
            {{ app.state.generating ? '正在生成方案' : '生成穿搭方案' }}
          </button>
        </div>
      </form>
    </section>

    <section class="result-section" aria-labelledby="result-title">
      <header class="section-heading">
        <div>
          <p class="section-kicker">当前方案</p>
          <h2 id="result-title">从数据到可执行穿搭</h2>
        </div>
        <span v-if="app.state.currentRecommendation" class="result-time">{{ formatDate(app.state.currentRecommendation.generatedAt) }}</span>
      </header>

      <div v-if="app.state.generating" class="loading-state" aria-live="polite">
        <LoaderCircle class="spinning" :size="26" />
        <strong>后端正在组合衣橱单品</strong>
        <span>完成后会在这里显示真实推荐结果。</span>
      </div>

      <article v-else-if="app.state.currentRecommendation" class="recommendation-result">
        <div class="result-topline">
          <div>
            <span class="record-id">方案 #{{ app.state.currentRecommendation.id }}</span>
            <span>{{ app.state.currentRecommendation.occasion }}</span>
            <span>{{ app.state.currentRecommendation.city }}</span>
          </div>
          <span class="saved-state" :class="{ saved: app.state.currentRecommendation.saved }">
            <Bookmark :size="15" :fill="app.state.currentRecommendation.saved ? 'currentColor' : 'none'" />
            {{ app.state.currentRecommendation.saved ? '已收藏' : '未收藏' }}
          </span>
        </div>

        <h3>{{ app.state.currentRecommendation.summary }}</h3>

        <ul class="outfit-list" aria-label="推荐衣物">
          <li v-for="item in app.state.currentRecommendation.items || []" :key="`${app.state.currentRecommendation.id}-${item.id}`">
            <div class="garment-media">
              <img
                v-if="item.imageUrl && !brokenImages.has(`result-${item.id}`)"
                :src="item.imageUrl"
                :alt="item.name"
                @error="imageFailed(`result-${item.id}`)"
              />
              <span v-else class="image-fallback" :style="{ backgroundColor: app.colorFor(item.color) }"><Shirt :size="23" /></span>
            </div>
            <div>
              <strong>{{ item.name }}</strong>
              <p>{{ item.category }} · {{ item.color }}<template v-if="item.style"> · {{ item.style }}</template></p>
            </div>
          </li>
        </ul>

        <div class="result-details">
          <div>
            <span>推荐理由</span>
            <p>{{ app.state.currentRecommendation.reason }}</p>
          </div>
          <div>
            <span>生成来源</span>
            <p>{{ app.state.currentRecommendation.engine }}</p>
          </div>
          <div v-if="app.state.currentRecommendation.weather">
            <span>天气快照</span>
            <p>
              {{ app.state.currentRecommendation.weather.temperatureC }}°C · 体感 {{ app.state.currentRecommendation.weather.apparentTemperatureC }}°C ·
              风速 {{ app.state.currentRecommendation.weather.windSpeedKmh }} km/h · {{ app.state.currentRecommendation.weather.source }}
            </p>
          </div>
        </div>

        <footer class="result-actions">
          <div class="rating-control">
            <span>这套搭配是否有用？</span>
            <div aria-label="方案评分">
              <button
                v-for="rating in 5"
                :key="rating"
                type="button"
                :class="{ rated: app.state.currentRecommendation.feedback?.rating >= rating }"
                :disabled="app.state.feedbackSavingId === app.state.currentRecommendation.id"
                :aria-label="`${rating} 星反馈`"
                @click="app.rateRecommendation(app.state.currentRecommendation, rating)"
              >
                <Star :size="19" :fill="app.state.currentRecommendation.feedback?.rating >= rating ? 'currentColor' : 'none'" />
              </button>
            </div>
          </div>
          <button
            class="save-button"
            type="button"
            :class="{ saved: app.state.currentRecommendation.saved }"
            :disabled="app.state.saving || app.state.currentRecommendation.saved"
            @click="app.saveRecommendation()"
          >
            <LoaderCircle v-if="app.state.saving" class="spinning" :size="17" />
            <Bookmark v-else :size="17" :fill="app.state.currentRecommendation.saved ? 'currentColor' : 'none'" />
            {{ app.state.currentRecommendation.saved ? '已保存到历史' : '收藏这套方案' }}
          </button>
        </footer>
      </article>

      <div v-else class="empty-state">
        <ClipboardList :size="30" aria-hidden="true" />
        <strong>还没有当前方案</strong>
        <span>补充生成条件后，推荐结果会连同来源、天气快照和真实衣物一起出现。</span>
        <button type="button" @click="formOpen = true">填写生成条件</button>
      </div>
    </section>

    <section class="insight-grid">
      <div class="coverage-panel">
        <header class="section-heading compact-heading">
          <div>
            <p class="section-kicker">衣橱覆盖</p>
            <h2>推荐是否真正用到了已有衣物</h2>
          </div>
          <Shirt :size="22" aria-hidden="true" />
        </header>

        <div class="coverage-value">
          <strong>{{ coveragePercent === null ? '--' : `${coveragePercent}%` }}</strong>
          <span>{{ app.recommendationStats.coveredItems }} / {{ app.wardrobeStats.total }} 件衣物已在历史方案中出现</span>
        </div>
        <progress v-if="coveragePercent !== null" :value="coveragePercent" max="100" aria-label="衣橱推荐覆盖率">{{ coveragePercent }}%</progress>
        <p v-else class="coverage-empty">衣橱暂无可计算基数，添加衣物并生成方案后再统计覆盖率。</p>

        <dl class="coverage-facts">
          <div><dt>衣橱总数</dt><dd>{{ app.wardrobeStats.total }}</dd></div>
          <div><dt>可直接使用</dt><dd>{{ app.wardrobeStats.ready }}</dd></div>
          <div><dt>待完善信息</dt><dd>{{ app.wardrobeStats.review }}</dd></div>
        </dl>
      </div>

      <div class="trend-panel">
        <header class="section-heading compact-heading">
          <div>
            <p class="section-kicker">实时风潮参照</p>
            <h2>生成前可以看看当下趋势</h2>
          </div>
          <button class="quiet-button" type="button" @click="app.selectView('trend')">查看风潮 <TrendingUp :size="16" /></button>
        </header>

        <div v-if="trendPreview.length" class="trend-list">
          <article v-for="trend in trendPreview" :key="trend.id">
            <div class="trend-media">
              <img
                v-if="trend.imageUrl && !brokenImages.has(`trend-${trend.id}`)"
                :src="trend.imageUrl"
                :alt="trend.title"
                @error="imageFailed(`trend-${trend.id}`)"
              />
              <span v-else class="image-fallback trend-fallback"><TrendingUp :size="23" /></span>
            </div>
            <div class="trend-copy">
              <span>{{ trend.platform }} · <Flame :size="13" />{{ trend.heatScore }}</span>
              <strong>{{ trend.title }}</strong>
              <p>{{ (trend.topicTags || []).slice(0, 3).join(' · ') || '暂无标签' }}</p>
            </div>
            <a :href="trend.sourceUrl" target="_blank" rel="noreferrer" :aria-label="`查看${trend.title}来源`"><ArrowUpRight :size="17" /></a>
          </article>
        </div>
        <div v-else class="trend-empty"><TrendingUp :size="24" /><span>暂无可用趋势数据</span></div>
      </div>
    </section>
  </section>
</template>

<style scoped>
.recommendation-view {
  width: min(1180px, 100%);
  margin: 0 auto;
  color: var(--ink);
}

.page-hero {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 40px;
  padding: 72px 0 42px;
}

.hero-copy { max-width: 720px; }
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
  max-width: 680px;
  margin: 0;
  font-family: Georgia, "Songti SC", serif;
  font-size: 48px;
  font-weight: 500;
  line-height: 1.08;
  letter-spacing: 0;
}

.hero-copy > p:last-child {
  max-width: 650px;
  margin: 19px 0 0;
  color: var(--muted);
  font-size: 14px;
  line-height: 1.8;
}

.primary-action,
.generate-button,
.save-button,
.empty-state button {
  display: inline-flex;
  min-height: 44px;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border: 1px solid var(--ink);
  border-radius: 5px;
  padding: 10px 17px;
  color: var(--surface);
  background: var(--ink);
  font-size: 13px;
  font-weight: 700;
}

.stats-band {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  border: 1px solid var(--line);
  border-radius: var(--radius);
  background: var(--surface);
}

.stats-band article {
  min-width: 0;
  padding: 24px 27px;
  border-right: 1px solid var(--line);
}

.stats-band article:last-child { border-right: 0; }
.stats-band span,
.stats-band small {
  display: block;
  color: var(--muted);
  font-size: 11px;
}

.stats-band strong {
  display: block;
  margin: 8px 0 7px;
  font-family: Georgia, serif;
  font-size: 31px;
  font-weight: 500;
  line-height: 1;
}

.stats-band em { color: var(--muted); font: 400 13px/1 sans-serif; }

.generator-panel {
  margin-top: 34px;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  padding: 28px;
  background: var(--surface);
}

.panel-heading,
.section-heading {
  display: flex;
  align-items: start;
  justify-content: space-between;
  gap: 24px;
}

.panel-heading h2,
.section-heading h2 {
  margin: 0;
  font-family: Georgia, "Songti SC", serif;
  font-size: 26px;
  font-weight: 500;
  line-height: 1.25;
  letter-spacing: 0;
}

.quiet-button {
  display: inline-flex;
  min-height: 38px;
  align-items: center;
  gap: 7px;
  border: 1px solid var(--line);
  border-radius: 5px;
  padding: 8px 11px;
  color: var(--ink);
  background: var(--surface);
  font-size: 12px;
  font-weight: 650;
}

.recommendation-form { margin-top: 26px; }
.form-fields {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
}

.wide-field { grid-column: 1 / -1; }
.recommendation-form label { display: grid; gap: 8px; }
.recommendation-form label > span { color: var(--muted); font-size: 12px; font-weight: 700; }
.recommendation-form label small { font-weight: 400; }
.recommendation-form input {
  width: 100%;
  min-height: 46px;
  border: 1px solid var(--line);
  border-radius: 4px;
  outline: none;
  padding: 11px 13px;
  color: var(--ink);
  background: var(--bg);
  font-size: 14px;
}

.recommendation-form input:focus { border-color: var(--accent); box-shadow: 0 0 0 3px var(--accent-soft); }
.form-footer {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 18px;
  align-items: center;
  margin-top: 20px;
}

.weather-preview,
.weather-empty {
  display: flex;
  min-height: 52px;
  align-items: center;
  gap: 11px;
  margin: 0;
  border-top: 1px solid var(--line);
  padding-top: 14px;
  color: var(--muted);
}

.weather-preview > svg,
.weather-empty > svg { flex: 0 0 auto; color: var(--accent); }
.weather-preview p { display: grid; gap: 3px; margin: 0; }
.weather-preview strong { color: var(--ink); font-size: 13px; }
.weather-preview span { font-size: 12px; }
.weather-preview small { margin-left: auto; color: var(--muted); font-size: 10px; }
.weather-empty { font-size: 12px; }
.generate-button { min-width: 180px; }

.result-section { padding: 64px 0 0; }
.result-time { color: var(--muted); font-size: 12px; }
.recommendation-result {
  margin-top: 24px;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  padding: 30px;
  background: var(--surface);
}

.result-topline {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
}

.result-topline > div { display: flex; flex-wrap: wrap; gap: 8px 15px; color: var(--muted); font-size: 11px; }
.record-id { color: var(--accent); font-weight: 700; }
.saved-state {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--muted);
  font-size: 11px;
}

.saved-state.saved { color: var(--accent); }
.recommendation-result > h3 {
  max-width: 760px;
  margin: 20px 0 26px;
  font-family: Georgia, "Songti SC", serif;
  font-size: 32px;
  font-weight: 500;
  line-height: 1.25;
  letter-spacing: 0;
}

.outfit-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin: 0;
  padding: 0;
  border-top: 1px solid var(--line);
  list-style: none;
}

.outfit-list li {
  display: grid;
  grid-template-columns: 58px minmax(0, 1fr);
  gap: 13px;
  align-items: center;
  min-width: 0;
  padding: 15px 18px 15px 0;
  border-bottom: 1px solid var(--line);
}

.outfit-list li:nth-child(odd) { border-right: 1px solid var(--line); }
.outfit-list li:nth-child(even) { padding-left: 18px; }
.garment-media,
.trend-media {
  overflow: hidden;
  background: var(--bg);
}

.garment-media { width: 58px; height: 66px; border-radius: 4px; }
.garment-media img,
.trend-media img { width: 100%; height: 100%; object-fit: cover; }
.image-fallback {
  display: grid;
  width: 100%;
  height: 100%;
  place-items: center;
  color: var(--surface);
}

.outfit-list strong { display: block; overflow-wrap: anywhere; font-size: 13px; }
.outfit-list p { margin: 5px 0 0; color: var(--muted); font-size: 11px; line-height: 1.5; }
.result-details { display: grid; grid-template-columns: 1.6fr .65fr 1fr; border-bottom: 1px solid var(--line); }
.result-details > div { padding: 20px 20px 20px 0; }
.result-details > div + div { border-left: 1px solid var(--line); padding-left: 20px; }
.result-details span { color: var(--accent); font-size: 11px; font-weight: 700; }
.result-details p { margin: 7px 0 0; color: var(--muted); font-size: 12px; line-height: 1.65; }
.result-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 22px;
  padding-top: 20px;
}

.rating-control { display: flex; align-items: center; gap: 12px; color: var(--muted); font-size: 12px; }
.rating-control > div { display: flex; gap: 2px; }
.rating-control button {
  display: grid;
  width: 31px;
  height: 31px;
  place-items: center;
  border: 0;
  padding: 0;
  color: var(--line);
  background: transparent;
}

.rating-control button:hover,
.rating-control button:focus-visible,
.rating-control button.rated { color: var(--accent); }
.save-button.saved { border-color: var(--line); color: var(--accent); background: var(--accent-soft); }
.loading-state,
.empty-state {
  display: grid;
  min-height: 360px;
  place-items: center;
  place-content: center;
  gap: 11px;
  margin-top: 24px;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  color: var(--muted);
  background: var(--surface);
  text-align: center;
}

.loading-state > svg,
.empty-state > svg { color: var(--accent); }
.loading-state strong,
.empty-state strong { color: var(--ink); font-family: Georgia, "Songti SC", serif; font-size: 21px; font-weight: 500; }
.loading-state span,
.empty-state span { max-width: 440px; font-size: 12px; line-height: 1.7; }
.empty-state button { margin-top: 7px; }
.insight-grid {
  display: grid;
  grid-template-columns: .8fr 1.2fr;
  gap: 36px;
  padding: 64px 0 76px;
}

.coverage-panel,
.trend-panel { min-width: 0; border-top: 1px solid var(--line); padding-top: 24px; }
.compact-heading h2 { font-size: 21px; }
.compact-heading > svg { color: var(--accent); }
.coverage-value { display: grid; gap: 5px; margin: 34px 0 15px; }
.coverage-value strong { font-family: Georgia, serif; font-size: 42px; font-weight: 500; }
.coverage-value span,
.coverage-empty { color: var(--muted); font-size: 11px; line-height: 1.6; }
.coverage-panel progress { width: 100%; height: 7px; border: 0; background: var(--line); }
.coverage-panel progress::-webkit-progress-bar { background: var(--line); }
.coverage-panel progress::-webkit-progress-value { background: var(--accent); }
.coverage-panel progress::-moz-progress-bar { background: var(--accent); }
.coverage-facts { display: grid; grid-template-columns: repeat(3, 1fr); margin: 28px 0 0; }
.coverage-facts div { padding-right: 14px; }
.coverage-facts div + div { border-left: 1px solid var(--line); padding-left: 14px; }
.coverage-facts dt { color: var(--muted); font-size: 10px; }
.coverage-facts dd { margin: 7px 0 0; font-family: Georgia, serif; font-size: 23px; }
.trend-list { display: grid; margin-top: 24px; border-top: 1px solid var(--line); }
.trend-list article {
  display: grid;
  grid-template-columns: 76px minmax(0, 1fr) 34px;
  gap: 14px;
  align-items: center;
  min-width: 0;
  padding: 13px 0;
  border-bottom: 1px solid var(--line);
}

.trend-media { width: 76px; height: 58px; border-radius: 4px; }
.trend-fallback { color: var(--accent); background: var(--accent-soft); }
.trend-copy { min-width: 0; }
.trend-copy > span { display: flex; align-items: center; gap: 4px; color: var(--accent); font-size: 10px; }
.trend-copy strong { display: block; overflow: hidden; margin-top: 5px; font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.trend-copy p { overflow: hidden; margin: 4px 0 0; color: var(--muted); font-size: 10px; text-overflow: ellipsis; white-space: nowrap; }
.trend-list a {
  display: grid;
  width: 32px;
  height: 32px;
  place-items: center;
  border: 1px solid var(--line);
  border-radius: 50%;
  color: var(--ink);
}

.trend-empty { display: flex; min-height: 170px; align-items: center; justify-content: center; gap: 9px; color: var(--muted); font-size: 12px; }
.spinning { animation: spin 1s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

@media (max-width: 900px) {
  .page-hero { align-items: start; padding-top: 54px; }
  .page-hero h1 { font-size: 42px; }
  .stats-band { grid-template-columns: repeat(2, 1fr); }
  .stats-band article:nth-child(2) { border-right: 0; }
  .stats-band article:nth-child(-n + 2) { border-bottom: 1px solid var(--line); }
  .result-details { grid-template-columns: 1fr; }
  .result-details > div { padding: 17px 0; }
  .result-details > div + div { border-top: 1px solid var(--line); border-left: 0; padding-left: 0; }
  .insight-grid { grid-template-columns: 1fr; gap: 44px; }
}

@media (max-width: 680px) {
  .page-hero { flex-direction: column; gap: 26px; padding: 42px 0 30px; }
  .page-hero h1 { font-size: 36px; }
  .primary-action { width: 100%; }
  .stats-band { grid-template-columns: 1fr; }
  .stats-band article { border-right: 0; border-bottom: 1px solid var(--line); padding: 20px; }
  .stats-band article:last-child { border-bottom: 0; }
  .generator-panel,
  .recommendation-result { padding: 20px; }
  .panel-heading,
  .section-heading { align-items: start; }
  .form-fields,
  .form-footer,
  .outfit-list { grid-template-columns: 1fr; }
  .wide-field { grid-column: auto; }
  .weather-preview { align-items: start; }
  .weather-preview small { display: none; }
  .generate-button { width: 100%; }
  .outfit-list li,
  .outfit-list li:nth-child(even) { border-right: 0; padding: 13px 0; }
  .recommendation-result > h3 { font-size: 27px; }
  .result-actions,
  .rating-control { align-items: stretch; flex-direction: column; }
  .save-button { width: 100%; }
  .coverage-facts { grid-template-columns: 1fr; }
  .coverage-facts div { display: flex; align-items: center; justify-content: space-between; padding: 10px 0; border-bottom: 1px solid var(--line); }
  .coverage-facts div + div { border-left: 0; padding-left: 0; }
  .coverage-facts dd { margin: 0; }
  .compact-heading .quiet-button { padding: 8px; font-size: 0; }
}
</style>
