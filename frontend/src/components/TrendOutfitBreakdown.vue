<script setup>
import { computed, ref } from 'vue'
import { ArrowUpRight, LoaderCircle } from '@lucide/vue'
import { trendImageCandidates, trendDisplayImageUrl } from '../utils/trendImage.js'
import { externalTrendSourceUrl } from '../utils/trendSources.js'

const props = defineProps({
  trend: { type: Object, required: true },
  wardrobe: { type: Array, default: () => [] },
  wardrobeLoading: { type: Boolean, default: false }
})

const emit = defineEmits(['use-trend', 'open-wardrobe'])
const unavailableSourceImages = ref(new Set())

const platformNames = {
  douyin: '抖音',
  weibo: '微博', xiaohongshu: '小红书',
  editorial: '时尚编辑精选',
  'configured-demo': '穿搭参考',
  'configured-feed': '已连接数据源',
  'web-scrape': '公开网页'
}
const topicTags = computed(() => (props.trend?.topicTags || []).filter(Boolean))
const summary = computed(() => String(props.trend?.summary || '').trim())
const sourceImageCandidates = computed(() => trendImageCandidates(
  props.trend?.imageUrl || props.trend?.evidence?.images?.find(Boolean) || ''
).map(trendDisplayImageUrl))
const sourceImageUrl = computed(() => sourceImageCandidates.value
  .find((url) => !unavailableSourceImages.value.has(url)) || '')
const hasSourceImage = computed(() => Boolean(sourceImageUrl.value))
const sourceIsBoardOnly = computed(() => props.trend?.evidence?.mediaType === 'board')
const summaryText = computed(() => {
  if (summary.value) return summary.value
  if (sourceIsBoardOnly.value) return '当前来源仅提供榜单话题，未提供穿搭图片与正文。'
  return '来源未提供摘要，当前展示已收录的标题与主题标签。'
})
const sourceImageNote = computed(() => {
  if (sourceImageCandidates.value.length) return '原文配图暂不可用，可查看已收录的文字信息。'
  if (sourceIsBoardOnly.value) return '来源只提供榜单话题，不包含穿搭配图。'
  return '来源没有提供可用配图。'
})
const platformName = computed(() => platformNames[props.trend?.platform] || '内容来源')
const sourceUrl = computed(() => externalTrendSourceUrl(props.trend))
const sourceAuthor = computed(() => String(props.trend?.evidence?.author || '').trim())
const publishedAt = computed(() => formatDateTime(props.trend?.publishedAt))
const usableWardrobe = computed(() =>
  props.wardrobe.filter((item) => item?.id && item.recognitionStatus !== 'NEEDS_MANUAL_REVIEW')
)
const wardrobeMatches = computed(() => {
  const tags = topicTags.value.map((tag) => String(tag).trim()).filter(Boolean)
  if (!tags.length) return []

  return usableWardrobe.value
    .map((item) => {
      const searchable = [item.name, item.category, item.style]
        .filter(Boolean)
        .join(' ')
        .toLocaleLowerCase('zh-CN')
      const matchingTags = tags.filter((tag) => searchable.includes(tag.toLocaleLowerCase('zh-CN')))
      return { item, matchingTags }
    })
    .filter((match) => match.matchingTags.length)
    .sort((left, right) => right.matchingTags.length - left.matchingTags.length)
    .slice(0, 2)
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

function markSourceImageUnavailable(event) {
  const image = event.currentTarget
  const url = image.getAttribute('src') || image.currentSrc || image.src
  if (!url || unavailableSourceImages.value.has(url)) return
  unavailableSourceImages.value = new Set([...unavailableSourceImages.value, url])
}

function itemDescription(item) {
  return [item.category, item.color, item.style].filter(Boolean).join(' · ') || '衣物信息待补充'
}
</script>

<template>
  <section class="trend-breakdown" aria-labelledby="trend-breakdown-title">
    <div class="trend-breakdown-grid">
      <figure v-if="hasSourceImage" class="trend-source-figure">
        <img
          :src="sourceImageUrl"
          :alt="'来源穿搭灵感：' + trend.title"
          referrerpolicy="no-referrer"
          @error="markSourceImageUnavailable"
        />
      </figure>
      <article v-else class="trend-source-copy">
        <p class="eyebrow">来源</p>
        <h2>{{ sourceIsBoardOnly ? '榜单话题' : (sourceImageUrl ? '原文配图暂不可用' : '来源文字') }}</h2>
        <p>{{ sourceImageNote }}</p>
        <span>标题、主题标签与摘要会在右侧按来源原样呈现。</span>
      </article>

      <div class="trend-breakdown-copy">
        <p class="eyebrow">穿搭内容解析</p>
        <h2 id="trend-breakdown-title">{{ trend.title }}</h2>
        <p class="breakdown-intro">依据来源提供的标题、标签与摘要整理。</p>

        <div class="breakdown-points">
          <article class="breakdown-point">
            <span>主题标签</span>
            <div v-if="topicTags.length" class="topic-tags">
              <span v-for="tag in topicTags" :key="tag">{{ tag }}</span>
            </div>
            <p v-else>来源未提供主题标签。</p>
          </article>

          <article class="breakdown-point">
            <span>原文摘要</span>
            <p>{{ summaryText }}</p>
          </article>

          <article class="breakdown-point source-point">
            <span>来源</span>
            <p>
              {{ platformName }}
              <template v-if="sourceAuthor"> · {{ sourceAuthor }}</template>
              <template v-if="publishedAt"> · {{ publishedAt }}</template>
            </p>
          </article>
        </div>

        <p class="evidence-note">具体搭配细节请参阅原文。</p>
        <a
          v-if="sourceUrl"
          class="source-link"
          :href="sourceUrl"
          target="_blank"
          rel="noopener noreferrer"
        >查看原文<ArrowUpRight :size="16" aria-hidden="true" /></a>
      </div>
    </div>

    <section class="wardrobe-substitutes" aria-labelledby="wardrobe-substitutes-title">
      <header class="wardrobe-heading">
        <div>
          <p class="eyebrow">我的衣橱</p>
          <h3 id="wardrobe-substitutes-title">衣橱匹配单品</h3>
          <span>展示已确认且与内容标签匹配的衣物。</span>
        </div>
        <button type="button" class="wardrobe-link" @click="emit('open-wardrobe')">查看我的衣橱<ArrowUpRight :size="15" aria-hidden="true" /></button>
      </header>

      <div v-if="wardrobeLoading" class="wardrobe-state" role="status" aria-live="polite">
        <LoaderCircle class="spinning" :size="19" aria-hidden="true" />
        正在加载衣橱单品…
      </div>
      <div v-else-if="wardrobeMatches.length" class="wardrobe-match-list">
        <article v-for="match in wardrobeMatches" :key="match.item.id" class="wardrobe-match">
          <div class="wardrobe-match-copy">
            <strong>{{ match.item.name || '未命名衣物' }}</strong>
            <span>{{ itemDescription(match.item) }}</span>
            <small>匹配标签：{{ match.matchingTags.join('、') }}</small>
          </div>
        </article>
      </div>
      <div v-else class="wardrobe-state wardrobe-empty">
        <p v-if="!wardrobe.length">暂无已确认的衣橱单品，请添加并完善衣物信息。</p>
        <p v-else-if="!usableWardrobe.length">衣物信息待确认，完善后可参与穿搭推荐。</p>
        <p v-else>暂无与当前标签直接匹配的衣物。生成推荐时仍将从已确认的衣橱单品中选择。</p>
        <button type="button" class="wardrobe-link" @click="emit('open-wardrobe')">查看我的衣橱<ArrowUpRight :size="15" aria-hidden="true" /></button>
      </div>
    </section>

    <footer class="trend-breakdown-action">
      <p>参考当前风格，使用个人衣橱单品生成搭配。</p>
      <button type="button" @click="emit('use-trend', trend)">参考风格生成搭配<ArrowUpRight :size="17" aria-hidden="true" /></button>
    </footer>
  </section>
</template>

<style scoped>
.trend-breakdown {
  color: var(--ink);
}

.trend-breakdown-grid {
  display: grid;
  grid-template-columns: minmax(0, .92fr) minmax(0, 1.08fr);
  gap: 42px;
  align-items: stretch;
  padding: 30px;
  border-radius: 18px;
  background: var(--surface-soft);
}

.trend-source-figure,
.trend-source-copy {
  min-width: 0;
  min-height: 520px;
  margin: 0;
  overflow: hidden;
  border-radius: 12px;
  background: var(--surface);
}

.trend-source-figure {
  position: relative;
  aspect-ratio: 4 / 5;
}

.trend-source-figure img {
  width: 100%;
  height: 100%;
  min-height: 0;
  object-fit: cover;
  object-position: center 35%;
}

.trend-source-copy {
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: clamp(26px, 4vw, 54px);
}

.eyebrow {
  margin: 0 0 11px;
  color: var(--muted);
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0;
}

.trend-source-copy h2,
.trend-breakdown-copy h2 {
  margin: 0;
  color: var(--ink);
  font-family: var(--font-display);
  font-size: clamp(30px, 3.2vw, 44px);
  font-weight: 600;
  letter-spacing: -.025em;
  line-height: 1.18;
}

.trend-source-copy > p:not(.eyebrow) {
  margin: 20px 0 0;
  color: var(--muted);
  font-size: 15px;
  line-height: 1.9;
}

.trend-source-copy > span {
  margin-top: 28px;
  border-top: 1px solid var(--line);
  padding-top: 14px;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.7;
}

.trend-breakdown-copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  justify-content: center;
  padding: 12px 0;
}

.breakdown-intro {
  margin: 12px 0 0;
  color: var(--muted);
  font-size: 14px;
  line-height: 1.7;
}

.breakdown-points {
  display: grid;
  gap: 0;
  margin-top: 24px;
}

.breakdown-point {
  display: grid;
  grid-template-columns: 100px minmax(0, 1fr);
  gap: 14px;
  align-items: start;
  border-top: 1px solid var(--line);
  padding: 17px 0;
}

.breakdown-point > span {
  color: var(--muted);
  font-size: 12px;
  font-weight: 700;
}

.breakdown-point p {
  margin: 0;
  color: var(--ink);
  font-size: 13px;
  line-height: 1.75;
}

.topic-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
}

.topic-tags span {
  border-bottom: 1px solid var(--line-strong);
  padding: 0 0 4px;
  color: var(--ink);
  font-size: 13px;
}

.evidence-note {
  margin: 2px 0 0;
  color: var(--muted);
  font-size: 11px;
  line-height: 1.65;
}

.source-link,
.wardrobe-link {
  display: inline-flex;
  min-height: 40px;
  align-items: center;
  gap: 7px;
  border: 0;
  padding: 7px 0;
  color: var(--accent);
  background: transparent;
  font-size: 12px;
  font-weight: 700;
  text-decoration: none;
}

.source-link {
  align-self: flex-start;
  margin-top: 10px;
  border-bottom: 1px solid var(--accent);
}

.source-link:hover,
.source-link:focus-visible,
.wardrobe-link:hover,
.wardrobe-link:focus-visible {
  color: var(--accent-strong);
  outline: 0;
}

.wardrobe-substitutes {
  margin-top: 28px;
  border-top: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
  padding: 27px 0 30px;
}

.wardrobe-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 22px;
}

.wardrobe-heading .eyebrow {
  margin-bottom: 7px;
}

.wardrobe-heading h3 {
  margin: 0;
  font-family: var(--font-display);
  font-size: 28px;
  font-weight: 500;
  line-height: 1.2;
}

.wardrobe-heading > div > span {
  display: block;
  margin-top: 8px;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.6;
}

.wardrobe-match-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
  margin-top: 20px;
}

.wardrobe-match {
  display: grid;
  min-width: 0;
  grid-template-columns: minmax(0, 1fr);
  padding: 12px;
  background: var(--surface);
}

.wardrobe-match-copy {
  display: grid;
  min-width: 0;
  gap: 6px;
}

.wardrobe-match-copy strong {
  overflow: hidden;
  color: var(--ink);
  font-family: var(--font-display);
  font-size: 17px;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.wardrobe-match-copy > span {
  overflow: hidden;
  color: var(--muted);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.wardrobe-match-copy small {
  color: var(--accent);
  font-size: 10px;
  line-height: 1.5;
}

.wardrobe-state {
  display: flex;
  min-height: 0;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  margin-top: 18px;
  color: var(--muted);
  font-size: 13px;
  line-height: 1.65;
}

.wardrobe-state p {
  flex: 1 1 360px;
  margin: 0;
}

.wardrobe-empty .wardrobe-link {
  flex: 0 0 auto;
}

.trend-breakdown-action {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 22px;
  padding-top: 22px;
}

.trend-breakdown-action p {
  margin: 0;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.65;
}

.trend-breakdown-action button {
  display: inline-flex;
  min-height: 52px;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  gap: 10px;
  border: 1px solid var(--accent);
  border-radius: 8px;
  padding: 12px 20px;
  color: var(--surface);
  background: var(--accent);
  font-size: 13px;
  font-weight: 700;
}

.trend-breakdown-action button:hover,
.trend-breakdown-action button:focus-visible {
  border-color: var(--accent-strong);
  background: var(--accent-strong);
  outline: 0;
}

.spinning {
  animation: wardrobe-spin 1s linear infinite;
}

@keyframes wardrobe-spin {
  to { transform: rotate(360deg); }
}

@media (max-width: 980px) {
  .trend-breakdown-grid {
    grid-template-columns: minmax(0, .9fr) minmax(0, 1.1fr);
    gap: 26px;
    padding: 24px;
  }

  .trend-source-figure,
  .trend-source-copy {
    min-height: 460px;
  }

  .trend-source-figure {
    aspect-ratio: auto;
  }

  .trend-source-figure img {
    min-height: 0;
  }

  .breakdown-point {
    grid-template-columns: 80px minmax(0, 1fr);
    gap: 10px;
  }
}

@media (max-width: 720px) {
  .trend-breakdown-grid {
    grid-template-columns: 1fr;
    gap: 24px;
    padding: 16px;
    border-radius: 12px;
  }

  .trend-source-figure,
  .trend-source-copy {
    min-height: 0;
  }

  .trend-source-figure {
    aspect-ratio: 4 / 5;
  }

  .trend-source-figure img {
    height: auto;
    min-height: 0;
    aspect-ratio: 4 / 5;
  }

  .trend-source-copy {
    min-height: 280px;
  }

  .trend-breakdown-copy {
    padding: 8px 4px 12px;
  }

  .trend-breakdown-copy h2,
  .trend-source-copy h2 {
    font-size: 32px;
  }

  .wardrobe-heading {
    align-items: flex-start;
    flex-direction: column;
    gap: 5px;
  }

  .wardrobe-match-list {
    grid-template-columns: 1fr;
  }

  .trend-breakdown-action {
    align-items: stretch;
    flex-direction: column;
  }

  .trend-breakdown-action button {
    width: 100%;
  }
}

@media (max-width: 420px) {
  .breakdown-point {
    grid-template-columns: 74px minmax(0, 1fr);
    gap: 8px;
  }

  .wardrobe-match {
    padding: 10px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .spinning {
    animation: none;
  }
}
</style>
