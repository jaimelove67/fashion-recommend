<script setup>
import { computed, ref } from 'vue'
import { ArrowUpRight, ImageOff, LoaderCircle, Shirt } from '@lucide/vue'

const props = defineProps({
  trend: { type: Object, required: true },
  wardrobe: { type: Array, default: () => [] },
  wardrobeLoading: { type: Boolean, default: false }
})

const emit = defineEmits(['use-trend', 'open-wardrobe'])
const unavailableSourceImages = ref(new Set())
const unavailableWardrobeImages = ref(new Set())

const platformNames = {
  douyin: '抖音',
  weibo: '微博',
  editorial: '时尚编辑精选',
  'configured-feed': '配置来源',
  'web-scrape': '公开网页'
}
const topicTags = computed(() => (props.trend?.topicTags || []).filter(Boolean))
const summary = computed(() => String(props.trend?.summary || '').trim())
const sourceImageUrl = computed(() =>
  props.trend?.imageUrl || props.trend?.evidence?.images?.find(Boolean) || ''
)
const hasSourceImage = computed(() =>
  Boolean(sourceImageUrl.value) && !unavailableSourceImages.value.has(sourceImageUrl.value)
)
const sourceIsBoardOnly = computed(() => props.trend?.evidence?.mediaType === 'board')
const summaryText = computed(() => {
  if (summary.value) return summary.value
  if (sourceIsBoardOnly.value) return '这个来源只提供榜单话题，没有穿搭图片或正文。'
  return '来源未提供摘要；这里只展示已收录的标题与标签，不推断具体穿法。'
})
const sourceImageNote = computed(() => {
  if (sourceImageUrl.value) return '原文配图暂时无法显示，以下保留来源文字信息。'
  if (sourceIsBoardOnly.value) return '来源只提供榜单话题，不包含穿搭配图。'
  return '来源没有提供可用配图。'
})
const platformName = computed(() => platformNames[props.trend?.platform] || '授权来源')
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

function hasWardrobeImage(item) {
  return Boolean(item.imageUrl) && !unavailableWardrobeImages.value.has(String(item.id))
}

function markWardrobeImageUnavailable(item) {
  unavailableWardrobeImages.value = new Set([...unavailableWardrobeImages.value, String(item.id)])
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
        <figcaption>
          <span>{{ platformName }} · 来源穿搭灵感</span>
          <span>不属于你的衣橱</span>
        </figcaption>
      </figure>
      <article v-else class="trend-source-copy">
        <p class="eyebrow">{{ platformName }} · 来源信息</p>
        <h2>{{ sourceIsBoardOnly ? '榜单话题' : (sourceImageUrl ? '原文配图暂不可用' : '来源文字') }}</h2>
        <p>{{ sourceImageNote }}</p>
        <span>标题、主题标签与摘要会在右侧按来源原样呈现。</span>
      </article>

      <div class="trend-breakdown-copy">
        <p class="eyebrow">拆解这条灵感</p>
        <h2 id="trend-breakdown-title">{{ trend.title }}</h2>
        <p class="breakdown-intro">从来源内容中提取可核对的信息，作为穿搭参考。</p>

        <div class="breakdown-points">
          <article class="breakdown-point">
            <span>主题线索</span>
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

        <p class="evidence-note">具体穿法以原文为准；此处只呈现来源明确提供的信息。</p>
        <a
          v-if="trend.sourceUrl"
          class="source-link"
          :href="trend.sourceUrl"
          target="_blank"
          rel="noopener noreferrer"
        >查看原文<ArrowUpRight :size="16" aria-hidden="true" /></a>
      </div>
    </div>

    <section class="wardrobe-substitutes" aria-labelledby="wardrobe-substitutes-title">
      <header class="wardrobe-heading">
        <div>
          <p class="eyebrow">把灵感带回衣橱</p>
          <h3 id="wardrobe-substitutes-title">衣橱里的替代</h3>
          <span>显示衣物名称、类别或风格与趋势标签相符的已确认单品。</span>
        </div>
        <button type="button" class="wardrobe-link" @click="emit('open-wardrobe')">查看我的衣橱<ArrowUpRight :size="15" aria-hidden="true" /></button>
      </header>

      <div v-if="wardrobeLoading" class="wardrobe-state" role="status" aria-live="polite">
        <LoaderCircle class="spinning" :size="19" aria-hidden="true" />
        正在读取你的衣橱…
      </div>
      <div v-else-if="wardrobeMatches.length" class="wardrobe-match-list">
        <article v-for="match in wardrobeMatches" :key="match.item.id" class="wardrobe-match">
          <div v-if="hasWardrobeImage(match.item)" class="wardrobe-match-image">
            <img
              :src="match.item.imageUrl"
              :alt="match.item.name || '衣橱单品'"
              loading="lazy"
              @error="markWardrobeImageUnavailable(match.item)"
            />
          </div>
          <div v-else class="wardrobe-match-image wardrobe-match-no-image">
            <ImageOff :size="18" aria-hidden="true" />
            <span>暂无照片</span>
          </div>
          <div class="wardrobe-match-copy">
            <strong>{{ match.item.name || '未命名衣物' }}</strong>
            <span>{{ itemDescription(match.item) }}</span>
            <small>对应线索：{{ match.matchingTags.join('、') }}</small>
          </div>
        </article>
      </div>
      <div v-else class="wardrobe-state wardrobe-empty">
        <Shirt :size="21" aria-hidden="true" />
        <p v-if="!wardrobe.length">衣橱还没有已确认的单品。添加衣物后，可以用这条灵感试搭。</p>
        <p v-else-if="!usableWardrobe.length">衣橱里的衣物仍需确认，确认后才能参与搭配。</p>
        <p v-else>目前没有衣物信息与这条内容的标签直接相符；继续试搭时，推荐仍只会使用已确认的衣物。</p>
        <button type="button" class="wardrobe-link" @click="emit('open-wardrobe')">打开我的衣橱<ArrowUpRight :size="15" aria-hidden="true" /></button>
      </div>
    </section>

    <footer class="trend-breakdown-action">
      <p>参考内容提供灵感，搭配单品仍来自你的衣橱。</p>
      <button type="button" @click="emit('use-trend', trend)">按这个方向用我的衣橱试搭<ArrowUpRight :size="17" aria-hidden="true" /></button>
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

.trend-source-figure figcaption {
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 8px 16px;
  padding: 16px 18px;
  color: var(--surface);
  background: rgba(18, 36, 30, .82);
  font-size: 11px;
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
  font-size: 11px;
  font-weight: 700;
  letter-spacing: .08em;
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
  grid-template-columns: 92px minmax(0, 1fr);
  gap: 14px;
  align-items: center;
  padding: 12px;
  background: var(--surface);
}

.wardrobe-match-image {
  width: 92px;
  height: 108px;
  overflow: hidden;
  background: var(--surface-soft);
}

.wardrobe-match-image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.wardrobe-match-no-image {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 7px;
  color: var(--muted);
}

.wardrobe-match-no-image span {
  font-size: 10px;
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
  min-height: 104px;
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

.wardrobe-empty > svg {
  flex: 0 0 auto;
  color: var(--accent);
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
    grid-template-columns: 76px minmax(0, 1fr);
    gap: 10px;
    padding: 10px;
  }

  .wardrobe-match-image {
    width: 76px;
    height: 92px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .spinning {
    animation: none;
  }
}
</style>
