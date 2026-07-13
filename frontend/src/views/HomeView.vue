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
    note: `本周新增 ${wardrobeStats.value.weeklyAdded ?? 0} 件`,
    icon: Shirt
  },
  {
    label: '穿搭记录',
    value: recommendationStats.value.total ?? 0,
    unit: '次',
    note: `已收藏 ${recommendationStats.value.saved ?? 0} 条`,
    icon: Bookmark
  },
  {
    label: '风潮样本',
    value: trendStats.value.count ?? 0,
    unit: '条',
    note: trendStats.value.count ? `平均热度 ${trendStats.value.averageHeat}` : '尚无可用数据',
    icon: TrendingUp
  }
])

function formatUpdateTime(value) {
  if (!value) return '尚未更新'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '尚未更新'
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
          风潮数据为开发样本
        </div>
        <h1 id="home-title">知己，懂你的穿搭。</h1>
        <p class="hero-lead">
          用你已记录的衣橱、风格档案和公开趋势样本，生成能说清理由、也能留下反馈的每日搭配。
        </p>
        <div class="hero-actions">
          <button type="button" class="primary-action" @click="app.selectView('recommend')">
            <Sparkles :size="18" />开始搭配<ArrowRight :size="18" />
          </button>
          <button type="button" class="secondary-action" @click="app.selectView('trend')">
            查看风潮<TrendingUp :size="18" />
          </button>
        </div>
        <div class="hero-context">
          <span>{{ profile?.displayName ? `${profile.displayName}的知己` : '我的知己' }}</span>
          <span v-if="heroTrend">当前趋势：{{ heroTrend.title }}</span>
          <span v-else>风潮数据尚未载入</span>
        </div>
      </div>

      <figure class="hero-collage" aria-label="穿搭风潮视觉集">
        <div
          v-for="(frame, index) in heroFrames"
          :key="frame.item?.id || frame.src"
          class="collage-frame"
          :class="`frame-${index + 1}`"
        >
          <img
            :src="frame.src"
            :alt="frame.item?.title || '穿搭视觉参考'"
            @error="handleImageError($event, frame.fallback)"
          />
        </div>
        <figcaption>
          <span>ZIJI / LOOK NOTES</span>
          <strong>{{ heroTrend?.title || '从今天的衣橱开始' }}</strong>
        </figcaption>
      </figure>
    </section>

    <section class="stat-rail" aria-label="真实数据概览">
      <article v-for="stat in statRail" :key="stat.label" class="stat-item">
        <component :is="stat.icon" :size="19" aria-hidden="true" />
        <div>
          <span>{{ stat.label }}</span>
          <p><strong>{{ stat.value }}</strong><small>{{ stat.unit }}</small></p>
          <em>{{ stat.note }}</em>
        </div>
      </article>
      <p class="rail-source">
        <span>{{ trendMeta.demoMode ? '开发样本' : '后端数据' }}</span>
        更新于 {{ formatUpdateTime(trendMeta.fetchedAt) }}
      </p>
    </section>

    <section class="trend-section" aria-labelledby="home-trend-title">
      <header class="section-heading">
        <div>
          <p>风潮摘要</p>
          <h2 id="home-trend-title">从样本里找到可穿的线索</h2>
        </div>
        <button type="button" class="section-link" @click="app.selectView('trend')">
          全部风潮<ArrowRight :size="17" />
        </button>
      </header>

      <div v-if="state.trendsLoading" class="section-state" aria-live="polite">正在读取风潮数据…</div>
      <div v-else-if="!trendPreviews.length" class="section-state">
        <strong>暂无风潮样本</strong>
        <button type="button" @click="app.loadTrends()">重新读取</button>
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
              <span>{{ item.platform || '来源未标记' }}</span>
              <span>热度 {{ item.heatScore ?? '—' }}</span>
            </span>
            <strong>{{ item.title }}</strong>
            <span class="preview-tags">{{ (item.topicTags || []).join(' / ') || '暂无标签' }}</span>
            <span class="preview-open">查看数据<ArrowUpRight :size="16" /></span>
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
  min-height: 620px;
  grid-template-columns: minmax(300px, 0.78fr) minmax(500px, 1.22fr);
  gap: clamp(28px, 5vw, 76px);
  align-items: center;
  padding: 44px 0 36px;
}

.hero-copy {
  position: relative;
  z-index: 2;
}

.sample-note {
  width: fit-content;
  margin-bottom: 22px;
  border-left: 3px solid var(--accent);
  padding: 6px 10px;
  color: var(--muted);
  background: var(--accent-soft);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0;
}

.hero-copy h1 {
  max-width: 570px;
  margin: 0;
  font-family: Georgia, "Songti SC", serif;
  font-size: 68px;
  font-weight: 500;
  line-height: 1.02;
  letter-spacing: 0;
  text-wrap: balance;
}

.hero-lead {
  max-width: 520px;
  margin: 26px 0 0;
  color: var(--muted);
  font-size: 15px;
  line-height: 1.85;
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
  border: 1px solid var(--ink);
  color: var(--surface);
  background: var(--ink);
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
  outline: 0;
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
}

.collage-frame {
  position: absolute;
  overflow: hidden;
  border: 1px solid color-mix(in srgb, var(--line) 78%, transparent);
  border-radius: var(--radius);
  background: var(--surface);
  box-shadow: 0 18px 46px rgba(22, 32, 29, 0.1);
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
  font-family: Georgia, "Songti SC", serif;
  font-size: 18px;
  font-weight: 500;
  line-height: 1.25;
}

.stat-rail {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr)) minmax(190px, 0.7fr);
  border-top: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
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
  font-family: Georgia, serif;
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
  padding: 92px 0 100px;
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
  font-family: Georgia, "Songti SC", serif;
  font-size: 36px;
  font-weight: 500;
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
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.trend-preview {
  min-width: 0;
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  padding: 0;
  color: var(--ink);
  background: var(--surface);
  text-align: left;
  transition: transform 180ms ease, border-color 180ms ease;
}

.trend-preview:hover,
.trend-preview:focus-visible {
  border-color: var(--accent);
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
  font-family: Georgia, "Songti SC", serif;
  font-size: 21px;
  font-weight: 500;
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
  font-family: Georgia, "Songti SC", serif;
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
    font-size: 54px;
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
    font-size: 50px;
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
}

@media (prefers-reduced-motion: reduce) {
  .trend-preview,
  .preview-image img {
    transition: none;
  }
}
</style>
