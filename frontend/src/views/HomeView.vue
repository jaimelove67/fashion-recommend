<script setup>
import { computed } from 'vue'
import { ArrowRight, ArrowUpRight, Bookmark, Shirt, Sparkles, TrendingUp } from '@lucide/vue'

const props = defineProps({
  app: { type: Object, required: true }
})

const fallbackLooks = ['/assets/look-urban.jpg', '/assets/look-tailoring.jpg', '/assets/look-color.jpg']
const state = computed(() => props.app.state || {})
const trends = computed(() => state.value.trends || [])
const profile = computed(() => state.value.profile)
const trendMeta = computed(() => state.value.trendMeta || {})
const trendStats = computed(() => props.app.trendStats || {})
const scoreLabel = computed(() => trendMeta.value.scoreLabel || '热度')
const wardrobeStats = computed(() => props.app.wardrobeStats || {})
const recommendationStats = computed(() => props.app.recommendationStats || {})
const heroTrend = computed(() => props.app.topTrend || trends.value[0] || null)
const trendPreviews = computed(() => trends.value.slice(0, 3))

const heroFrames = computed(() => {
  const ordered = []
  if (heroTrend.value) ordered.push(heroTrend.value)
  for (const item of trends.value) {
    if (!ordered.some((candidate) => candidate.id === item.id)) ordered.push(item)
  }
  return fallbackLooks.map((fallback, index) => ({
    item: ordered[index] || null,
    src: ordered[index]?.imageUrl || fallback,
    fallback
  }))
})

const statRail = computed(() => [
  {
    label: '我的衣橱',
    value: wardrobeStats.value.total ?? 0,
    unit: '件',
    note: `近 7 天新增 ${wardrobeStats.value.weeklyAdded ?? 0} 件`,
    icon: Shirt
  },
  {
    label: '搭配记录',
    value: recommendationStats.value.total ?? 0,
    unit: '条',
    note: `已收藏 ${recommendationStats.value.saved ?? 0} 条`,
    icon: Bookmark
  },
  {
    label: '趋势条目',
    value: trendStats.value.count ?? 0,
    unit: '条',
    note: trendStats.value.count ? `平均${scoreLabel.value} ${trendStats.value.averageHeat}` : '暂无趋势数据',
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
    hour12: false
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
</script>

<template>
  <div class="home-page">
    <section class="editorial-hero" aria-labelledby="home-title">
      <div class="hero-copy">
        <div v-if="trendMeta.demoMode" class="sample-note" role="status">
          趋势数据使用开发样本
        </div>
        <h1 id="home-title">今天穿什么，从衣橱开始。</h1>
        <p class="hero-lead">
          记下你的衣物，再填场合和城市。系统会结合天气与风格偏好，从衣橱里挑出几套搭配，并说明选择理由。
        </p>
        <div class="hero-actions">
          <button type="button" class="primary-action" @click="app.selectView('recommend')">
            <Sparkles :size="18" />生成今日搭配<ArrowRight :size="18" />
          </button>
          <button type="button" class="secondary-action" @click="app.selectView('trend')">
            看看趋势<TrendingUp :size="18" />
          </button>
        </div>
        <div class="hero-context">
          <span>{{ profile?.displayName ? `${profile.displayName}的风格档案` : '我的风格档案' }}</span>
          <span v-if="heroTrend">当前趋势：{{ heroTrend.title }}</span>
          <span v-else>趋势数据还没准备好</span>
        </div>
      </div>

      <figure class="hero-collage" aria-label="趋势参考图">
        <div
          v-for="(frame, index) in heroFrames"
          :key="frame.item?.id || frame.src"
          class="collage-frame"
          :class="`frame-${index + 1}`"
        >
          <img
            :src="frame.src"
            :alt="frame.item?.title || '穿搭参考图'"
            @error="handleImageError($event, frame.fallback)"
          />
        </div>
        <figcaption>
          <span>ZIJI / LOOK NOTES</span>
          <strong>{{ heroTrend?.title || '先从今天这一套开始' }}</strong>
        </figcaption>
      </figure>
    </section>

    <section class="stat-rail" aria-label="衣橱与推荐概览">
      <article v-for="stat in statRail" :key="stat.label" class="stat-item">
        <component :is="stat.icon" :size="19" aria-hidden="true" />
        <div>
          <span>{{ stat.label }}</span>
          <p><strong>{{ stat.value }}</strong><small>{{ stat.unit }}</small></p>
          <em>{{ stat.note }}</em>
        </div>
      </article>
      <p class="rail-source">
        <span>{{ trendMeta.demoMode ? '开发样本' : '服务端数据' }}</span>
        最后更新 {{ formatUpdateTime(trendMeta.fetchedAt) }}
      </p>
    </section>

    <section class="trend-section" aria-labelledby="home-trend-title">
      <header class="section-heading">
        <div>
          <p>趋势速览</p>
          <h2 id="home-trend-title">先看看最近有哪些趋势</h2>
        </div>
        <button type="button" class="section-link" @click="app.selectView('trend')">
          查看全部趋势<ArrowRight :size="17" />
        </button>
      </header>

      <div v-if="state.trendsLoading" class="section-state" aria-live="polite">正在加载趋势数据…</div>
      <div v-else-if="!trendPreviews.length" class="section-state">
        <strong>暂无趋势数据</strong>
        <button type="button" @click="app.loadTrends()">重新加载</button>
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
              @error="handleImageError($event, fallbackLooks[index])"
            />
            <span>{{ String(index + 1).padStart(2, '0') }}</span>
          </span>
          <span class="preview-copy">
            <span class="preview-meta">
              <span>{{ item.platform || '未标注来源' }}</span>
              <span>{{ scoreLabel }} {{ item.heatScore ?? '—' }}</span>
            </span>
            <strong>{{ item.title }}</strong>
            <span class="preview-tags">{{ (item.topicTags || []).join(' / ') || '未设置标签' }}</span>
            <span class="preview-open">查看详情<ArrowUpRight :size="16" /></span>
          </span>
        </button>
      </div>
    </section>
  </div>
</template>

<style scoped>
.home-page {
  width: min(calc(100% - 48px), 1280px);
  margin: 0 auto;
  color: var(--ink);
}

.editorial-hero {
  display: grid;
  min-height: 610px;
  grid-template-columns: minmax(300px, 0.78fr) minmax(500px, 1.22fr);
  gap: clamp(28px, 5vw, 76px);
  align-items: center;
  padding: 54px 0 42px;
}

.hero-copy {
  position: relative;
  z-index: 2;
}

.sample-note {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  width: fit-content;
  margin-bottom: 22px;
  border: 1px solid color-mix(in srgb, var(--accent) 22%, var(--line));
  border-radius: 999px;
  padding: 7px 11px;
  color: var(--accent-strong);
  background: var(--accent-soft);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0;
}

.sample-note::before {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--coral);
  content: '';
}

.hero-copy h1 {
  max-width: 570px;
  margin: 0;
  font-family: var(--font-display);
  font-size: clamp(48px, 5vw, 68px);
  font-weight: 600;
  line-height: 1.04;
  letter-spacing: -.04em;
  text-wrap: balance;
}

.hero-lead {
  max-width: 520px;
  margin: 26px 0 0;
  color: var(--muted);
  font-size: 15px;
  line-height: 1.8;
}

.hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 32px;
}

.hero-actions button,
.section-link,
.section-state button {
  display: inline-flex;
  min-height: 44px;
  align-items: center;
  justify-content: center;
  gap: 9px;
  border-radius: var(--radius);
  padding: 10px 16px;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0;
}

.primary-action {
  border: 1px solid var(--accent-strong);
  color: var(--surface);
  background: var(--accent-strong);
  box-shadow: 0 10px 22px rgba(27, 85, 77, .16);
}

.secondary-action {
  border: 1px solid var(--line);
  color: var(--ink);
  background: var(--surface);
}

.hero-actions button:hover,
.hero-actions button:focus-visible,
.section-link:hover,
.section-link:focus-visible {
  border-color: var(--accent);
  color: var(--accent-strong);
  background: var(--accent-soft);
  outline: 0;
}

.hero-actions .primary-action:hover,
.hero-actions .primary-action:focus-visible {
  color: var(--surface);
  background: var(--accent);
  box-shadow: 0 13px 28px rgba(27, 85, 77, .22);
}

.hero-context {
  display: flex;
  max-width: 540px;
  flex-wrap: wrap;
  gap: 8px 18px;
  margin-top: 34px;
  border-top: 1px solid var(--line);
  padding-top: 14px;
  color: var(--muted);
  font-size: 11px;
  line-height: 1.5;
}

.hero-context span:first-child {
  color: var(--ink);
  font-weight: 700;
}

.hero-collage {
  position: relative;
  min-height: 540px;
  margin: 0;
  isolation: isolate;
}

.hero-collage::before {
  position: absolute;
  top: 12%;
  right: 1%;
  width: 78%;
  height: 74%;
  border-radius: 26px;
  background: #e7ece6;
  content: '';
  transform: rotate(-2deg);
  z-index: -1;
}

.collage-frame {
  position: absolute;
  overflow: hidden;
  border: 7px solid rgba(255, 253, 249, .84);
  border-radius: 16px;
  background: var(--surface);
  box-shadow: var(--shadow-soft);
}

.collage-frame img {
  width: 100%;
  height: 100%;
  display: block;
  object-fit: cover;
}

.frame-1 {
  top: 4%;
  right: 4%;
  width: 56%;
  height: 73%;
  transform: rotate(2deg);
}

.frame-1 img {
  object-position: center 30%;
}

.frame-2 {
  bottom: 7%;
  left: 0;
  z-index: 2;
  width: 43%;
  height: 48%;
  transform: rotate(-4deg);
}

.frame-3 {
  right: 10%;
  bottom: 0;
  z-index: 3;
  width: 31%;
  height: 33%;
  transform: rotate(1deg);
}

.hero-collage figcaption {
  position: absolute;
  top: 6%;
  left: 7%;
  display: grid;
  max-width: 180px;
  gap: 6px;
  color: var(--muted);
}

.hero-collage figcaption span {
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0;
}

.hero-collage figcaption strong {
  font-family: var(--font-display);
  font-size: 18px;
  font-weight: 500;
  line-height: 1.25;
}

.stat-rail {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr)) minmax(190px, 0.7fr);
  border-top: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
  background: color-mix(in srgb, var(--surface) 72%, transparent);
}

.stat-item {
  display: grid;
  min-height: 136px;
  grid-template-columns: 28px minmax(0, 1fr);
  gap: 13px;
  align-content: center;
  padding: 24px;
  border-right: 1px solid var(--line);
}

.stat-item > svg {
  margin-top: 3px;
  color: var(--accent);
}

.stat-item span,
.stat-item em,
.rail-source {
  color: var(--muted);
  font-size: 11px;
  font-style: normal;
  line-height: 1.5;
}

.stat-item p {
  display: flex;
  align-items: baseline;
  gap: 5px;
  margin: 5px 0 3px;
}

.stat-item strong {
  font-family: var(--font-mono);
  font-size: 32px;
  font-weight: 500;
}

.stat-item small {
  font-size: 12px;
}

.rail-source {
  display: grid;
  align-content: center;
  gap: 6px;
  margin: 0;
  padding: 24px;
}

.rail-source span {
  width: fit-content;
  color: var(--ink);
  font-weight: 700;
}

.trend-section {
  padding: 84px 0 92px;
}

.section-heading {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 28px;
  margin-bottom: 30px;
}

.section-heading p {
  margin: 0 0 8px;
  color: var(--muted);
  font-size: 12px;
  font-weight: 700;
}

.section-heading h2 {
  max-width: 650px;
  margin: 0;
  font-family: var(--font-display);
  font-size: 32px;
  font-weight: 700;
  line-height: 1.18;
  letter-spacing: 0;
}

.section-link {
  min-height: 40px;
  flex: 0 0 auto;
  border: 1px solid var(--line);
  color: var(--ink);
  background: var(--surface);
}

.trend-grid {
  display: grid;
  grid-template-columns: 1.2fr .9fr .9fr;
  gap: 16px;
}

.trend-preview {
  min-width: 0;
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: 14px;
  padding: 0;
  color: var(--ink);
  background: var(--surface);
  text-align: left;
  transition: transform 180ms ease, border-color 180ms ease, box-shadow 180ms ease;
}

.trend-preview:first-child .preview-image {
  aspect-ratio: 1.08 / 1;
}

.trend-preview:hover,
.trend-preview:focus-visible {
  border-color: var(--accent);
  box-shadow: var(--shadow-soft);
  outline: 0;
  transform: translateY(-3px);
}

.preview-image {
  position: relative;
  display: block;
  aspect-ratio: 4 / 3;
  overflow: hidden;
  background: var(--accent-soft);
}

.preview-image img {
  width: 100%;
  height: 100%;
  display: block;
  object-fit: cover;
  transition: transform 320ms ease;
}

.trend-preview:hover .preview-image img {
  transform: scale(1.025);
}

.preview-image > span {
  position: absolute;
  top: 12px;
  left: 12px;
  min-width: 32px;
  border-radius: var(--radius);
  padding: 5px 7px;
  color: var(--ink);
  background: var(--surface);
  font-size: 11px;
  font-weight: 700;
  text-align: center;
}

.preview-copy {
  display: grid;
  gap: 10px;
  padding: 20px;
}

.preview-meta,
.preview-open {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  color: var(--muted);
  font-size: 11px;
}

.preview-copy > strong {
  min-height: 48px;
  font-family: var(--font-display);
  font-size: 20px;
  font-weight: 700;
  line-height: 1.25;
}

.preview-tags {
  min-height: 36px;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.5;
}

.preview-open {
  justify-content: flex-start;
  border-top: 1px solid var(--line);
  padding-top: 12px;
  color: var(--ink);
  font-weight: 700;
}

.section-state {
  display: grid;
  min-height: 240px;
  place-items: center;
  align-content: center;
  gap: 14px;
  border-top: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
  color: var(--muted);
  font-size: 13px;
}

.section-state strong {
  color: var(--ink);
  font-family: var(--font-display);
  font-size: 22px;
  font-weight: 500;
}

.section-state button {
  border: 1px solid var(--line);
  color: var(--ink);
  background: var(--surface);
}

@media (max-width: 1050px) {
  .editorial-hero {
    min-height: auto;
    grid-template-columns: minmax(280px, 0.85fr) minmax(400px, 1.15fr);
  }

  .hero-copy h1 {
    font-size: 56px;
  }

  .hero-collage {
    min-height: 480px;
  }

  .stat-rail {
    grid-template-columns: repeat(3, 1fr);
  }

  .rail-source {
    display: flex;
    grid-column: 1 / -1;
    justify-content: space-between;
    border-top: 1px solid var(--line);
  }
}

@media (max-width: 780px) {
  .editorial-hero {
    grid-template-columns: 1fr;
    gap: 12px;
    padding-top: 54px;
  }

  .hero-copy {
    padding-right: 20px;
  }

  .hero-copy h1 {
    max-width: 560px;
    font-size: 52px;
  }

  .hero-collage {
    min-height: 500px;
  }

  .stat-rail {
    grid-template-columns: 1fr;
  }

  .stat-item {
    min-height: 112px;
    border-right: 0;
    border-bottom: 1px solid var(--line);
  }

  .rail-source {
    grid-column: auto;
    border-top: 0;
  }

  .trend-grid {
    grid-template-columns: 1fr;
  }

  .trend-preview {
    display: grid;
    grid-template-columns: minmax(180px, 0.82fr) minmax(0, 1.18fr);
  }

  .preview-image {
    height: 100%;
    aspect-ratio: auto;
  }
}

@media (max-width: 560px) {
  .home-page {
    width: calc(100% - 32px);
  }

  .editorial-hero {
    padding-top: 38px;
  }

  .hero-copy {
    padding-right: 0;
  }

  .hero-copy h1 {
    font-size: 38px;
    line-height: 1.08;
  }

  .hero-lead {
    font-size: 14px;
  }

  .hero-actions {
    display: grid;
    grid-template-columns: 1fr;
  }

  .hero-actions button {
    width: 100%;
  }

  .hero-collage {
    min-height: 410px;
  }

  .frame-1 {
    top: 5%;
    right: 1%;
    width: 63%;
    height: 69%;
  }

  .frame-2 {
    width: 48%;
    height: 46%;
  }

  .frame-3 {
    right: 4%;
    width: 34%;
    height: 31%;
  }

  .hero-collage figcaption {
    top: 4%;
    left: 2%;
    max-width: 112px;
  }

  .hero-collage figcaption strong {
    font-size: 14px;
  }

  .rail-source {
    display: grid;
  }

  .trend-section {
    padding: 68px 0 72px;
  }

  .section-heading {
    display: grid;
    align-items: start;
  }

  .section-heading h2 {
    font-size: 30px;
  }

  .section-link {
    width: 100%;
  }

  .trend-preview {
    display: block;
  }

  .preview-image {
    aspect-ratio: 4 / 3;
  }

  .trend-preview:first-child .preview-image {
    aspect-ratio: 4 / 3;
  }
}

@media (prefers-reduced-motion: reduce) {
  .trend-preview,
  .preview-image img {
    transition: none;
  }
}
</style>
