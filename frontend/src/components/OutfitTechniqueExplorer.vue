<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ArrowRight, Bookmark, Share2, Shirt, ThumbsUp } from '@lucide/vue'
import { outfitTechniqueCategories, outfitTechniques } from '../data/outfitTechniques.js'
import { getOutfitTechniqueCase } from '../data/outfitTechniqueCases.js'
import { formatTechniquePublication, formatTechniqueSourcePublication, mergeOutfitTechniques,
  normalizeOutfitTechniqueFeed, safeTechniqueSourceUrl } from '../utils/outfitTechniqueFeed.js'

const PREFERENCES_STORAGE_KEY = 'fashion.techniques.preferences.v1'
const emit = defineEmits(['open-wardrobe'])
const category = ref('全部')
const sharedLessonId = new URL(window.location.href).searchParams.get('technique') || ''
const selectedId = ref(sharedLessonId || outfitTechniques[0]?.id || '')
const savedLessons = ref(new Set())
const helpfulLessons = ref(new Set())
const preferenceNotice = ref('')
const shareUrl = ref('')
const shareCopied = ref(false)
const unavailableImages = ref(new Set())
const announcement = ref('')
const lessonTitle = ref(null)
const lessonArticle = ref(null)
const weeklyFeed = ref({ items: [], latestPublishedAt: null, status: null })
let interacted = false
let active = true
let feedRequest

onMounted(async () => {
  try {
    const preferences = JSON.parse(window.localStorage.getItem(PREFERENCES_STORAGE_KEY))
    for (const [key, target] of [['saved', savedLessons], ['helpful', helpfulLessons]]) {
      if (Array.isArray(preferences?.[key])) target.value = new Set(preferences[key].filter(id => typeof id === 'string' && id.length <= 200).slice(0, 200))
    }
  } catch { /* Reading works even when browser storage is unavailable. */ }
  if (sharedLessonId) lessonArticle.value?.scrollIntoView({ block: 'start' })
  feedRequest = new AbortController()
  try {
    const response = await fetch('/api/v1/outfit-techniques', { signal: feedRequest.signal, credentials: 'same-origin' })
    if (!response.ok) return
    const feed = normalizeOutfitTechniqueFeed(await response.json())
    if (!active) return
    weeklyFeed.value = feed
    if (!interacted && feed.items.length) {
      selectedId.value = sourcedTechniques.value.some(item => item.id === sharedLessonId) ? sharedLessonId : sourcedTechniques.value[0]?.id || ''
    }
  } catch {
    // Sourced lessons stay readable when the weekly service is unavailable.
  }
})
onBeforeUnmount(() => { active = false; feedRequest?.abort() })

const sourcedTechniques = computed(() => mergeOutfitTechniques(weeklyFeed.value.items, outfitTechniques).flatMap(technique => {
  const seenUrls = new Set()
  const sources = (Array.isArray(technique.sources) ? technique.sources : []).flatMap(source => {
    const url = safeTechniqueSourceUrl(source.url)
    if (!url || seenUrls.has(url)) return []
    seenUrls.add(url)
    return [{ ...source, url }]
  })
  if (!sources.length) return []
  const sourceNotes = (Array.isArray(technique.sourceNotes) ? technique.sourceNotes : []).flatMap(note => {
    const source = sources.find(item => item.id === note.sourceId)
    return source && typeof note.point === 'string' && note.point.trim() ? [{ ...note, source }] : []
  })
  return [{ ...technique, sources, sourceNotes }]
}))
const availableCategories = computed(() => {
  const categories = new Set(sourcedTechniques.value.map(technique => technique.category))
  const ordered = outfitTechniqueCategories.filter(value => value !== '全部' && categories.has(value))
  return ['全部', ...new Set([...ordered, ...categories])]
})
const visibleTechniques = computed(() => sourcedTechniques.value.filter(technique => category.value === '全部' || technique.category === category.value))
const selectedTechnique = computed(() => visibleTechniques.value.find(technique => technique.id === selectedId.value) || visibleTechniques.value[0] || null)
const selectedSources = computed(() => {
  const technique = selectedTechnique.value
  return technique ? technique.sources.map(source => ({
    source,
    notes: technique.sourceNotes.filter(note => note.sourceId === source.id)
  })) : []
})
const selectedIndex = computed(() => visibleTechniques.value.findIndex(technique => technique.id === selectedTechnique.value?.id))
const nextTechnique = computed(() => visibleTechniques.value[(selectedIndex.value + 1) % visibleTechniques.value.length])
const caseStudy = computed(() => getOutfitTechniqueCase(selectedTechnique.value))
const comparisonVisual = computed(() => selectedTechnique.value?.comparisonVisual
  || (caseStudy.value ? { kind: 'generated', images: caseStudy.value.images } : null))
const comparisonImages = computed(() => comparisonVisual.value?.images.length === 2 ? comparisonVisual.value.images : [])
const combinedImage = computed(() => comparisonVisual.value?.images.length === 1 ? comparisonVisual.value.images[0] : null)
const currentSaved = computed(() => savedLessons.value.has(selectedTechnique.value?.id))
const currentHelpful = computed(() => helpfulLessons.value.has(selectedTechnique.value?.id))

watch(selectedTechnique, () => {
  unavailableImages.value = new Set()
  shareUrl.value = ''
  shareCopied.value = false
})

function pad(value) { return String(value).padStart(2, '0') }
function categoryCount(value) { return value === '全部' ? sourcedTechniques.value.length : sourcedTechniques.value.filter(item => item.category === value).length }
function setCategory(value) {
  interacted = true
  category.value = value
  selectedId.value = visibleTechniques.value[0]?.id || ''
  announcement.value = `已切换到${value === '全部' ? '全部技巧' : value}，共 ${visibleTechniques.value.length} 篇`
}
async function selectTechnique(id) {
  interacted = true
  selectedId.value = id
  announcement.value = `已打开${selectedTechnique.value?.title || '穿搭技巧'}`
  await nextTick()
  lessonTitle.value?.focus({ preventScroll: true })
  lessonArticle.value?.scrollIntoView({ block: 'start', behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth' })
}
function markImageUnavailable(url) { unavailableImages.value = new Set([...unavailableImages.value, url]) }
function togglePreference(kind) {
  if (!selectedTechnique.value) return
  interacted = true
  const target = kind === 'saved' ? savedLessons : helpfulLessons
  const next = new Set(target.value)
  if (next.has(selectedTechnique.value.id)) next.delete(selectedTechnique.value.id)
  else next.add(selectedTechnique.value.id)
  target.value = next
  try {
    window.localStorage.setItem(PREFERENCES_STORAGE_KEY, JSON.stringify({ saved: [...savedLessons.value], helpful: [...helpfulLessons.value] }))
    preferenceNotice.value = ''
  } catch {
    preferenceNotice.value = '浏览器未允许保存，收藏和反馈仅在本次阅读期间有效。'
  }
  const message = kind === 'saved' ? (currentSaved.value ? '已收藏这篇技巧' : '已取消收藏') : (currentHelpful.value ? '已标记有帮助' : '已取消有帮助标记')
  announcement.value = preferenceNotice.value ? `${message}。${preferenceNotice.value}` : `${message}，仅保存在此浏览器`
}
async function copyLessonLink() {
  if (!selectedTechnique.value) return
  interacted = true
  const lessonId = selectedTechnique.value.id
  const url = new URL(window.location.href)
  url.search = ''
  url.searchParams.set('technique', lessonId)
  url.hash = 'trend'
  shareCopied.value = false
  shareUrl.value = ''
  try {
    await navigator.clipboard.writeText(url.href)
    if (selectedTechnique.value?.id !== lessonId) return
    shareCopied.value = true
    shareUrl.value = ''
    announcement.value = '技巧链接已复制'
  } catch {
    if (selectedTechnique.value?.id !== lessonId) return
    shareUrl.value = url.href
    announcement.value = '请复制下方技巧链接'
  }
}
</script>

<template>
  <section class="technique-explorer" aria-labelledby="outfit-techniques-title">
    <header class="intro technique-heading">
      <img class="technique-hero-image" src="/assets/techniques/lesson-hero-light.jpg" alt="" width="2172" height="724" decoding="async" />
      <div class="technique-heading-copy">
        <p class="eyebrow">{{ category === '全部' ? '日常穿搭指南' : `${category}搭配指南` }} / {{ visibleTechniques.length }} 篇技巧</p>
        <h2 id="outfit-techniques-title" class="page-title">穿搭技巧</h2>
        <p class="lede">从日常穿搭中遇到的问题出发，看懂调整思路和前后变化，找到适合自己的穿法。</p>
      </div>
    </header>
    <nav v-if="sourcedTechniques.length" class="cats technique-categories" aria-label="按技巧主题筛选">
      <button v-for="value in availableCategories" :key="value" class="cat" type="button" :aria-label="value"
        :aria-pressed="category === value" @click="setCategory(value)">{{ value }}<em aria-hidden="true">{{ pad(categoryCount(value)) }}</em></button>
    </nav>
    <div v-if="sourcedTechniques.length" class="spread technique-layout">
      <aside class="rail" aria-label="技巧目录">
        <div class="rail-head"><span>技巧目录</span><span>{{ pad(visibleTechniques.length) }} 篇</span></div>
        <ol class="step-list technique-choices" role="group" aria-label="选择穿搭技巧">
          <li v-for="(technique, index) in visibleTechniques" :key="technique.id">
            <button type="button" class="step-btn technique-choice" :class="{ 'is-active': selectedTechnique?.id === technique.id }"
              :aria-pressed="selectedTechnique?.id === technique.id" :aria-current="selectedTechnique?.id === technique.id ? 'true' : undefined"
              :aria-label="technique.title" aria-controls="technique-detail" @click="selectTechnique(technique.id)">
              <span class="step-num" aria-hidden="true">{{ pad(index + 1) }}</span>
              <span class="step-copy"><strong class="step-name">{{ technique.title }}</strong><span class="step-time">{{ technique.category }} · <span class="technique-source-count">{{ technique.sources.length }} 篇原帖</span></span></span>
              <ArrowRight v-if="selectedTechnique?.id === technique.id" class="step-current" :size="13" aria-hidden="true" />
            </button>
          </li>
        </ol>
      </aside>
      <article v-if="selectedTechnique" id="technique-detail" ref="lessonArticle" class="lesson technique-detail">
        <header class="technique-detail-heading">
          <div class="lesson-topline">
            <p class="lesson-meta"><span>{{ selectedTechnique.category }}</span><span class="meta-divider" aria-hidden="true"></span><span>穿搭案例解析</span></p>
            <div class="lesson-tools" role="group" aria-label="技巧操作">
              <button type="button" :aria-pressed="currentSaved" :title="currentSaved ? '取消收藏（仅此浏览器）' : '收藏这篇技巧（仅此浏览器）'" @click="togglePreference('saved')"><Bookmark :size="15" aria-hidden="true" /><span>{{ currentSaved ? '已收藏' : '收藏' }}</span></button>
              <button type="button" :aria-pressed="currentHelpful" title="标记有帮助（仅此浏览器）" @click="togglePreference('helpful')"><ThumbsUp :size="15" aria-hidden="true" /><span>{{ currentHelpful ? '已赞' : '有帮助' }}</span></button>
              <button type="button" @click="copyLessonLink"><Share2 :size="15" aria-hidden="true" /><span>{{ shareCopied ? '已复制' : '分享' }}</span></button>
            </div>
          </div>
          <p v-if="preferenceNotice" class="lesson-save-notice">{{ preferenceNotice }}</p>
          <label v-if="shareUrl" class="lesson-share-link">复制技巧链接<input :value="shareUrl" readonly @focus="$event.target.select()" /></label>
          <h3 ref="lessonTitle" class="lesson-title" tabindex="-1" aria-live="polite">{{ selectedTechnique.title }}</h3>
          <p class="lesson-lede">{{ caseStudy?.summary || selectedTechnique.summary }}</p>
          <p v-if="selectedTechnique.generationMode === 'weekly-ai'" class="technique-ai-disclosure">每周精选 · {{ formatTechniquePublication(selectedTechnique.publishedAt) }} 发布<br>AI 根据原帖文字归纳。</p>
        </header>
        <section class="section technique-scene" aria-labelledby="technique-scene-title">
          <h4 id="technique-scene-title" class="section-title"><span class="sec-num" aria-hidden="true">01</span>你可能遇到的穿搭问题</h4>
          <div class="scene">
            <p class="scene-label">{{ caseStudy?.context || selectedTechnique.suitable }}</p>
            <h5 v-if="caseStudy" class="scene-question">{{ caseStudy.question }}</h5>
            <p class="scene-problem">{{ caseStudy?.problem || selectedTechnique.example.before }}</p>
            <p v-if="caseStudy" class="scene-goal"><span>想要的效果</span>{{ caseStudy.goal }}</p>
          </div>
        </section>
        <section class="section technique-example" aria-labelledby="technique-example-title">
          <div class="technique-section-heading">
            <h4 id="technique-example-title" class="section-title"><span class="sec-num" aria-hidden="true">02</span>解决方案与对比</h4>
          </div>
          <div class="technique-solution">
            <h5>{{ caseStudy?.solutionTitle || '从这一处调整开始' }}</h5>
            <p>{{ caseStudy?.solution || selectedTechnique.example.after }}</p>
          </div>
          <figure class="technique-comparison-visual" :data-kind="comparisonVisual?.kind || 'text'">
            <figcaption v-if="comparisonVisual" class="technique-example-note">{{ comparisonVisual.kind === 'source' ? '原帖对照图' : 'AI 搭配示意' }}</figcaption>
            <div v-if="combinedImage" class="technique-combined-image">
              <img v-if="!unavailableImages.has(combinedImage.url)" :src="combinedImage.url" :alt="combinedImage.alt" loading="lazy" decoding="async" referrerpolicy="no-referrer" @error="markImageUnavailable(combinedImage.url)" />
              <p v-else class="technique-image-unavailable" role="status">对照图暂不可用，可继续阅读下面的调整说明。</p>
            </div>
            <div class="compare technique-comparison" role="group" aria-label="调整前后对比">
              <section v-for="(side, index) in ['before', 'after']" :key="side" class="compare-side" :data-side="side">
                <div class="compare-head"><span class="compare-label">{{ index === 0 ? '调整前' : '调整后' }}</span><h5>{{ caseStudy ? (index === 0 ? caseStudy.beforeTitle : caseStudy.afterTitle) : (index === 0 ? '当前穿法' : '调整思路') }}</h5></div>
                <div v-if="comparisonImages[index]" class="compare-photo" :class="{ 'is-spread': comparisonImages[index].panel, 'is-source': comparisonVisual.kind === 'source' }">
                  <img v-if="!unavailableImages.has(comparisonImages[index].url)" :src="comparisonImages[index].url" :alt="comparisonImages[index].alt" :data-panel="comparisonImages[index].panel" loading="lazy" decoding="async" referrerpolicy="no-referrer" @error="markImageUnavailable(comparisonImages[index].url)" />
                  <p v-else class="technique-image-unavailable" role="status">对照图暂不可用，可继续阅读下面的调整说明。</p>
                </div>
                <p class="compare-description">{{ caseStudy ? caseStudy[side] : selectedTechnique.example[side] }}</p>
              </section>
            </div>
            <p v-if="comparisonVisual?.note" class="technique-visual-note">{{ comparisonVisual.note }}</p>
            <a v-if="comparisonVisual?.sourceUrl" :href="comparisonVisual.sourceUrl" target="_blank" rel="noopener noreferrer">{{ comparisonVisual.kind === 'source' ? '查看图片原帖' : '查看方法来源' }}<ArrowRight :size="13" aria-hidden="true" /></a>
          </figure>
          <section class="technique-reason" aria-labelledby="technique-reason-title">
            <h5 id="technique-reason-title" class="technique-subtitle">为什么这样搭</h5>
            <div v-if="caseStudy?.palette" class="technique-palette" aria-label="本例配色关系">
              <div v-for="palette in caseStudy.palette" :key="palette.label" class="technique-palette-row">
                <span class="palette-side">{{ palette.label }}</span>
                <span v-for="color in palette.colors" :key="color.label" class="palette-color"><i :style="{ backgroundColor: color.color }" aria-hidden="true"></i>{{ color.label }}</span>
              </div>
            </div>
            <div v-if="caseStudy?.reasoning" class="technique-reason-points">
              <div v-for="point in caseStudy.reasoning" :key="point.title" class="technique-reason-point"><h6>{{ point.title }}</h6><p>{{ point.body }}</p></div>
            </div>
            <p v-else>{{ selectedTechnique.reason }}</p>
          </section>
        </section>
        <section class="section" aria-labelledby="technique-how-title"><h4 id="technique-how-title" class="section-title"><span class="sec-num" aria-hidden="true">03</span><span>搭配要点</span></h4><ol class="how-list technique-steps"><li v-for="(step, index) in selectedTechnique.steps" :key="step"><span class="how-num" aria-hidden="true">{{ pad(index + 1) }}</span><p>{{ step }}</p></li></ol></section>
        <aside class="mistake" aria-labelledby="technique-limit-title"><h4 id="technique-limit-title" class="mistake-label">注意取舍</h4><p>{{ selectedTechnique.limit }}</p></aside>
        <section class="technique-suitable" aria-labelledby="technique-suitable-title"><h4 id="technique-suitable-title">适合什么时候</h4><p>{{ selectedTechnique.suitable }}</p></section>
        <section class="technique-sources" aria-labelledby="technique-sources-title">
          <h4 id="technique-sources-title">原帖要点与参考来源</h4>
          <p class="technique-section-note">以下为原文要点的转述，点击标题可查看原帖。</p>
          <ul class="technique-source-list">
            <li v-for="{ source, notes } in selectedSources" :key="source.id || source.url">
              <a :href="source.url" target="_blank" rel="noopener noreferrer">{{ source.title }}<ArrowRight :size="14" aria-hidden="true" /></a>
              <p class="technique-source-meta">{{ source.author }} · {{ source.platform }} · {{ formatTechniqueSourcePublication(source.publishedAt) }}</p>
              <div v-if="notes.length" class="technique-source-points">
                <p v-for="(note, index) in notes" :key="`${note.sourceId}-${index}`">{{ note.point }}</p>
              </div>
              <p v-else class="technique-source-empty">这篇原帖的要点仍在整理。</p>
            </li>
          </ul>
        </section>

        <section v-if="selectedTechnique.synthesisNote" class="technique-synthesis" aria-labelledby="technique-synthesis-title">
          <h4 id="technique-synthesis-title">{{ selectedTechnique.sources.length > 1 ? '跨帖归纳' : '本帖归纳' }}</h4>
          <p>{{ selectedTechnique.synthesisNote }}</p>
        </section>


        <footer class="lesson-actions">
          <button type="button" class="technique-wardrobe" @click="emit('open-wardrobe')"><Shirt :size="15" aria-hidden="true" />查看我的衣橱<ArrowRight :size="15" aria-hidden="true" /></button>
          <button v-if="nextTechnique && visibleTechniques.length > 1" type="button" class="next-btn" @click="selectTechnique(nextTechnique.id)"><span class="next-kicker">继续阅读<ArrowRight :size="13" aria-hidden="true" /></span><strong class="next-title">{{ nextTechnique.title }}</strong></button>
        </footer>
      </article>
    </div>
    <div v-else class="technique-empty" role="status"><h3>原帖整理中</h3><p>目前还没有整理完成的原帖技巧。有可核对的来源后，会在这里呈现作者要点与归纳方法。</p></div>
    <div class="technique-feed-meta">
      <p class="technique-weekly-status">每周约更新 1 条 · 质量优先，不凑数量<span v-if="weeklyFeed.latestPublishedAt"> · 最近更新 {{ formatTechniquePublication(weeklyFeed.latestPublishedAt) }}</span></p>
      <p v-if="weeklyFeed.status === 'disabled'" class="technique-weekly-note">自动整理暂未启用，可先阅读已整理的技巧。</p>
      <p v-else-if="['collecting', 'waiting-for-quality'].includes(weeklyFeed.status)" class="technique-weekly-note">新内容正在整理，来源与质量达标后更新。</p>
    </div>
    <p v-if="sourcedTechniques.length" class="technique-scope">案例讲解与原帖要点一同呈现，可结合自己的衣物、穿着感受与当天场合选择。</p>
    <p class="sr-only" role="status" aria-live="polite">{{ announcement }}</p>
  </section>
</template>

<style scoped>
.technique-explorer {
  --paper: #f8f5ef;
  --paper-2: #eee9df;
  --ink: #182b24;
  --ink-soft: #41483f;
  --muted: #6c6c61;
  --line: #e3ddd2;
  --line-strong: #cbc3b3;
  --pine: #39704b;
  --pine-deep: #244e36;
  --pine-soft: #e9ecdf;
  --sage: #94b091;
  --clay: #985943;
  --clay-soft: #f1e7de;
  --serif: "Noto Serif SC", "Source Han Serif SC", "Songti SC", "STSong", "SimSun", serif;
  --sans: "PingFang SC", "Microsoft YaHei", "Noto Sans SC", sans-serif;
  margin: var(--trend-section-gap, 40px) 0 0;
  padding: 0 28px 32px;
  color: var(--ink);
  background: var(--paper);
  font-family: var(--sans);
  border-top: 1px solid var(--line);
  scroll-margin-top: 88px;
}
.technique-explorer, .technique-explorer * { box-sizing: border-box; }
.technique-explorer ::selection { color: var(--pine-deep); background: #dce5d3; }
button, input { font: inherit; }
button { cursor: pointer; }
a { color: inherit; text-underline-offset: 3px; }
button:focus-visible, a:focus-visible, input:focus-visible { outline: 2px solid var(--pine); outline-offset: 4px; }
.sr-only { position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px; overflow: hidden; clip: rect(0,0,0,0); white-space: nowrap; border: 0; }
.technique-heading { position: relative; isolation: isolate; min-height: clamp(124px, 12.8vw, 164px); margin: 0 -28px; overflow: hidden; }
.technique-hero-image { position: absolute; z-index: -1; inset: 0; display: block; width: 100%; height: 100%; object-fit: cover; object-position: center 48%; }
.technique-heading-copy { padding: 10px 48px 8px; }
.eyebrow { margin: 0 0 6px; font-size: 11px; line-height: 1.5; font-weight: 600; letter-spacing: .1em; color: #575d52; }
.page-title { margin: 0; color: #102b23; font: 900 clamp(40px, 4.8vw, 56px)/1.08 var(--serif); letter-spacing: .025em; }
.lede { max-width: 650px; margin: 9px 0 0; color: #343f36; font-size: 12px; line-height: 1.75; }
.cats { display: flex; gap: 28px; align-items: stretch; min-height: 34px; margin: 0 -28px; padding: 0 48px; overflow-x: auto; border-bottom: 1px solid var(--line); scrollbar-width: thin; scrollbar-color: var(--line-strong) transparent; }
.cat { position: relative; display: flex; gap: 5px; align-items: baseline; flex: none; padding: 8px 0; border: 0; background: none; color: #585e53; font-size: 12px; line-height: 1.5; white-space: nowrap; }
.cat em { color: #707165; font-size: 9px; font-style: normal; font-variant-numeric: tabular-nums; }
.cat:hover, .cat[aria-pressed="true"] { color: var(--ink); }
.cat[aria-pressed="true"] { font-weight: 700; }
.cat[aria-pressed="true"]::after { position: absolute; right: 0; bottom: 0; left: 0; height: 2px; background: var(--pine-deep); content: ""; }
.spread { display: grid; grid-template-columns: 205px minmax(0,1fr); gap: 44px; align-items: start; padding: 24px 0 40px; }
.spread > * { min-width: 0; }
.rail { position: sticky; top: 94px; padding-right: 18px; border-right: 1px solid var(--line); }
.rail-head { display: flex; align-items: baseline; justify-content: space-between; gap: 12px; padding: 0 8px 7px; color: #41483f; font-size: 12px; font-weight: 600; }
.rail-head > :last-child { font-size: 11px; font-variant-numeric: tabular-nums; }
.step-list { display: block; margin: 0; padding: 0; list-style: none; }
.step-list li + li { border-top: 1px solid var(--line); }
.step-btn { display: grid; grid-template-columns: 18px minmax(0,1fr) 13px; gap: 6px; align-items: start; width: 100%; height: 100%; padding: 6.5px 8px 6.5px 12px; border: 0; border-radius: 5px; color: var(--ink); background: transparent; text-align: left; transition: background .18s ease; }
.step-btn:hover { background: #efeee5; }
.step-btn.is-active { background: var(--pine-soft); box-shadow: inset 2px 0 0 var(--sage); }
.step-num { padding-top: 1px; font-size: 11px; line-height: 1.6; font-weight: 600; font-variant-numeric: tabular-nums; }
.step-copy { min-width: 0; }
.step-name { display: block; font-size: 11.5px; line-height: 1.5; font-weight: 600; overflow-wrap: anywhere; }
.step-time { display: block; margin-top: 3px; color: #62695b; font-size: 9px; line-height: 1.6; white-space: nowrap; }
.step-current { margin-top: 3px; color: var(--pine); }
.lesson { min-width: 0; scroll-margin-top: 94px; }
.lesson-topline { display: flex; align-items: center; justify-content: space-between; gap: 12px; min-height: 30px; }
.lesson-meta { display: flex; align-items: center; gap: 8px; margin: 0; color: #515b4c; font-size: 10px; line-height: 1.6; font-weight: 600; letter-spacing: .03em; }
.meta-divider { display: block; width: 10px; height: 1px; background: var(--line-strong); }
.lesson-tools { display: flex; align-items: center; gap: 6px; margin-top: -6px; }
.lesson-tools button { display: inline-flex; align-items: center; justify-content: center; gap: 5px; min-height: 29px; padding: 6px 9px; border: 0; border-radius: 18px; color: #444c42; background: #fdfbf7; font-size: 10px; line-height: 1; white-space: nowrap; }
.lesson-tools button:hover, .lesson-tools button[aria-pressed="true"] { color: var(--pine-deep); background: #e5ebdf; }
.lesson-tools button[aria-pressed="true"] .lucide-bookmark { fill: #c8d9bd; }
.lesson-save-notice { margin: 6px 0; color: #85533e; font-size: 11px; line-height: 1.7; }
.lesson-share-link { display: grid; gap: 6px; margin: 8px 0; color: var(--pine-deep); font-size: 11px; }
.lesson-share-link input { min-width: 0; width: 100%; padding: 8px; border: 1px solid var(--line-strong); border-radius: 4px; background: #fffdf7; font-size: 12px; }
.lesson-title { margin: 1px 0 4px; color: #142820; font: 900 clamp(24px, 2.7vw, 32px)/1.42 var(--serif); letter-spacing: .005em; overflow-wrap: anywhere; }
.lesson-title:focus { outline: none; }
.lesson-lede { margin: 0; color: var(--ink-soft); font-size: 12px; line-height: 1.8; }
.technique-detail p, .technique-detail a, .technique-detail dd { overflow-wrap: anywhere; }
.technique-ai-disclosure { margin: 12px 0 0; padding-top: 10px; border-top: 1px solid var(--line); color: var(--muted); font-size: 11px; line-height: 1.8; }
.section { margin-top: 26px; }
.section-title { display: flex; align-items: center; gap: 10px; margin: 0 0 10px; color: var(--ink); font-size: 16px; line-height: 1.5; font-weight: 700; }
.sec-num { display: grid; place-items: center; flex: none; width: 19px; height: 19px; border-radius: 50%; color: var(--pine); background: #e8e8d5; font: 600 11px/1 var(--serif); }
.technique-scene { margin-top: 24px; }
.scene { padding: 19px 22px; border-left: 3px solid var(--sage); border-radius: 0 6px 6px 0; background: var(--pine-soft); }
.scene .scene-label { margin: 0 0 7px; color: #596750; font-size: 11px; line-height: 1.7; }
.scene-question { margin: 0 0 8px; color: var(--ink); font: 700 20px/1.6 var(--serif); }
.scene .scene-problem { margin: 0; color: #394934; font-size: 13px; line-height: 1.9; }
.scene .scene-goal { display: flex; flex-wrap: wrap; gap: 4px 12px; margin: 14px 0 0; padding-top: 11px; border-top: 1px solid #d3dcc9; color: var(--pine-deep); font-size: 12px; line-height: 1.8; }
.scene-goal span { flex: none; font-weight: 600; }
.technique-example { margin-top: 28px; }
.technique-section-heading { margin-bottom: 12px; }
.technique-solution h5 { margin: 0 0 6px; color: var(--pine-deep); font-size: 17px; line-height: 1.7; }
.technique-solution p { margin: 0; color: var(--ink-soft); font-size: 13px; line-height: 1.9; }
.technique-comparison-visual { margin: 18px 0 20px; }
.technique-example-note { margin: 0 0 9px; color: var(--muted); font-size: 11px; line-height: 1.7; }
.compare { display: grid; grid-template-columns: minmax(0,1fr) minmax(0,1fr); gap: 16px; }
.compare-side { min-width: 0; padding: 14px; border: 1px solid var(--line); border-radius: 7px; background: #f3eee6; }
.compare-side[data-side="after"] { border-color: #d2ddc9; background: #edf0e6; }
.compare-head { margin-bottom: 12px; }
.compare-label { display: inline-block; margin-bottom: 5px; color: #80634d; font-size: 11px; line-height: 1.6; }
.compare-side[data-side="after"] .compare-label { color: var(--pine); }
.compare-head h5 { margin: 0; color: var(--ink); font-size: 14px; line-height: 1.6; }
.compare-photo { position: relative; aspect-ratio: 3 / 4; overflow: hidden; border-radius: 4px; background: var(--paper-2); }
.compare-photo > img { display: block; width: 100%; height: 100%; object-fit: cover; object-position: center; }
.compare-photo.is-spread > img { position: absolute; top: 0; left: 0; width: 200%; max-width: none; }
.compare-photo.is-spread > img[data-panel="right"] { left: -100%; }
.compare-photo.is-source { aspect-ratio: auto; }
.compare-photo.is-source > img { height: auto; max-height: 560px; object-fit: contain; }
.compare-description { margin: 11px 0 0; color: var(--ink-soft); font-size: 12px; line-height: 1.85; }
.technique-combined-image { margin-bottom: 16px; }
.technique-combined-image img { display: block; width: 100%; height: auto; max-height: 640px; object-fit: contain; border-radius: 5px; background: var(--paper-2); }
.technique-reason { padding-left: 15px; border-left: 2px solid #c3ceb8; }
.technique-subtitle { margin: 0 0 5px; font-size: 14px; line-height: 1.7; }
.technique-reason p { margin: 0; color: var(--ink-soft); font-size: 13px; line-height: 1.85; }
.technique-reason-points { display: grid; gap: 17px; margin-top: 14px; }
.technique-reason-point h6 { margin: 0 0 5px; color: var(--ink); font-size: 13px; line-height: 1.8; font-weight: 600; }
.technique-palette { display: grid; gap: 10px; margin: 14px 0 18px; }
.technique-palette-row { display: flex; flex-wrap: wrap; align-items: center; gap: 8px 16px; }
.palette-side { color: var(--muted); font-size: 11px; }
.palette-color { display: inline-flex; align-items: center; gap: 6px; color: var(--ink-soft); font-size: 11px; line-height: 1.8; }
.palette-color i { width: 15px; height: 15px; flex: none; border: 1px solid #00000012; border-radius: 50%; }
.technique-visual-note, .technique-image-unavailable { margin: 8px 0; color: var(--muted); font-size: 11px; line-height: 1.8; }
.technique-image-unavailable { padding: 12px; border: 1px solid var(--line); }
.technique-comparison-visual > a { color: var(--pine); font-size: 11px; }
.technique-detail a svg { display: inline; margin-left: 5px; vertical-align: middle; }
.how-list { margin: 0; padding: 0; border-top: 1px solid var(--line); list-style: none; }
.how-list li { display: grid; grid-template-columns: 28px minmax(0,1fr); gap: 12px; padding: 13px 0; border-bottom: 1px solid var(--line); }
.how-num { padding-top: 2px; color: var(--pine); font: 500 19px/1.5 var(--serif); }
.how-list p { margin: 0; color: var(--ink-soft); font-size: 13px; line-height: 1.85; }
.mistake { margin: 26px 0; padding: 15px 18px; border-radius: 5px; background: var(--clay-soft); }
.mistake-label { margin: 0 0 5px; color: #854c38; font-size: 12px; }
.mistake p { margin: 0; color: #57493c; font-size: 12px; line-height: 1.85; }
.technique-suitable { padding-bottom: 20px; border-bottom: 1px solid var(--line); }
.technique-suitable h4, .technique-sources h4 { margin: 0 0 8px; font: 700 18px/1.6 var(--serif); }
.technique-suitable p, .technique-synthesis p { margin: 0; color: var(--ink-soft); font-size: 13px; line-height: 1.85; }
.technique-section-note { margin: 0; color: var(--muted); font-size: 11px; line-height: 1.8; }
.technique-source-list { display: grid; gap: 20px; margin: 16px 0 0; padding: 0; list-style: none; }
.technique-source-list > li { padding-left: 14px; border-left: 1px solid var(--line-strong); }
.technique-source-points { display: grid; gap: 8px; margin-top: 10px; }
.technique-source-points p { margin: 0; color: var(--ink-soft); font-size: 13px; line-height: 1.85; }
.technique-source-empty { margin: 10px 0 0; color: var(--muted); font-size: 12px; line-height: 1.8; }
.technique-synthesis { margin-top: 24px; padding: 15px 18px; border-radius: 5px; background: var(--pine-soft); }
.technique-synthesis h4 { margin: 0 0 5px; color: var(--pine-deep); font-size: 12px; }
.technique-sources { margin-top: 28px; }
.technique-source-list a { display: flex; align-items: baseline; gap: 5px; width: fit-content; max-width: 100%; color: var(--pine); font-size: 13px; line-height: 1.7; font-weight: 600; }
.technique-source-list a svg { flex: none; margin-left: 0; }
.technique-source-meta { margin: 5px 0 0; color: var(--muted); font-size: 11px; line-height: 1.8; }
.technique-wardrobe { display: inline-flex; align-items: center; gap: 8px; width: fit-content; padding: 7px 0; border: 0; color: var(--pine); background: none; font-size: 12px; }
.lesson-actions { display: flex; align-items: center; justify-content: space-between; gap: 24px; margin-top: 28px; padding-top: 20px; border-top: 1px solid var(--line-strong); }
.next-btn { display: flex; flex-direction: column; align-items: flex-start; gap: 4px; max-width: 280px; padding: 11px 18px; border: 0; border-radius: 5px; background: var(--pine-deep); color: var(--paper); text-align: left; }
.next-btn:hover { background: #193c28; }
.next-kicker { display: flex; align-items: center; gap: 8px; font-size: 10px; color: #d3dfcb; }
.next-title { font-size: 12px; line-height: 1.6; font-weight: 500; overflow-wrap: anywhere; }
.technique-feed-meta { margin-bottom: 18px; }
.technique-weekly-status, .technique-weekly-note { margin: 10px 0 0; color: var(--muted); font-size: 11px; line-height: 1.8; }
.technique-scope { margin: 0; padding-top: 18px; border-top: 1px solid var(--line); color: var(--muted); font-size: 11px; line-height: 1.8; }
.technique-empty { padding: 28px 0; color: var(--ink-soft); line-height: 1.8; }
@media (min-width: 1280px) {
  .spread { grid-template-columns: 224px minmax(0,1fr); gap: 46px; }
  .step-name { font-size: 13px; }
  .step-btn { padding-block: 10px; }
  .step-time { font-size: 10px; }
  .lesson-lede, .technique-reason p { font-size: 13px; }
  .section-title { font-size: 17px; }
}
@media (max-width: 900px) {
  .technique-explorer { padding-inline: 22px; }
  .technique-heading, .cats { margin-inline: -22px; }
  .technique-heading-copy { padding-inline: 32px; }
  .cats { padding-inline: 32px; }
  .spread { grid-template-columns: minmax(0,1fr); gap: 24px; }
  .rail { position: static; padding: 0; border-right: 0; }
  .rail-head { padding-inline: 0; }
  .step-list { display: flex; overflow-x: auto; padding-bottom: 5px; scrollbar-width: thin; scrollbar-color: var(--line-strong) transparent; }
  .step-list li { flex: 0 0 205px; }
  .step-list li + li { border-top: 0; border-left: 1px solid var(--line); }
  .lesson-title { font-size: 27px; }
}
@media (max-width: 680px) {
  .technique-explorer { padding: 0 16px 24px; scroll-margin-top: 142px; }
  .technique-heading { min-height: 176px; margin-inline: -16px; }
  .technique-heading-copy { padding: 19px 20px 17px; }
  .technique-hero-image { object-position: 70% center; opacity: .25; }
  .page-title { font-size: 42px; }
  .eyebrow { font-size: 10px; }
  .lede { max-width: 30em; margin-top: 12px; font-size: 12px; line-height: 1.85; }
  .cats { gap: 25px; margin-inline: -16px; padding-inline: 20px; }
  .cat { min-height: 44px; padding-block: 12px 10px; font-size: 12px; }
  .spread { gap: 20px; padding-top: 18px; }
  .step-list li { flex-basis: 210px; }
  .step-btn { min-height: 78px; padding-block: 11px; }
  .step-name { font-size: 12px; }
  .lesson { scroll-margin-top: 144px; }
  .lesson-topline { align-items: flex-start; flex-direction: column; gap: 9px; }
  .lesson-meta { font-size: 10px; }
  .lesson-tools { gap: 7px; margin: 0; }
  .lesson-tools button { min-height: 36px; padding: 8px 11px; font-size: 11px; }
  .lesson-title { margin-top: 10px; font-size: 25px; line-height: 1.45; }
  .lesson-lede { margin-top: 8px; font-size: 13px; line-height: 1.85; }
  .technique-scene { margin-top: 22px; }
  .section-title { gap: 8px; font-size: 16px; }
  .scene { padding: 13px 14px; }
  .scene-question { font-size: 18px; }
  .technique-reason p { font-size: 13px; line-height: 1.9; }
  .technique-subtitle { font-size: 15px; }
  .technique-example { margin-top: 24px; }
  .technique-example-note { font-size: 11px; }
  .compare { grid-template-columns: minmax(0,1fr); gap: 12px; }
  .compare-side { padding: 15px 17px; }
  .compare-description { font-size: 13px; }
  .how-list p, .mistake p, .technique-suitable p, .technique-source-points p, .technique-synthesis p { font-size: 13px; line-height: 1.9; }
  .technique-source-list a { font-size: 13px; }
  .lesson-actions { flex-direction: column; align-items: stretch; gap: 14px; }
  .next-btn { width: 100%; max-width: none; }
  .next-title { font-size: 13px; }
}
@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after { scroll-behavior: auto !important; animation: none !important; transition: none !important; }
}
</style>
