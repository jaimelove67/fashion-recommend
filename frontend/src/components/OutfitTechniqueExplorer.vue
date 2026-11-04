<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ArrowRight, Bookmark, Check, Plus, Share2, Shirt, ThumbsUp, X } from '@lucide/vue'
import { outfitTechniqueCategories, outfitTechniques } from '../data/outfitTechniques.js'
import { formatTechniquePublication, formatTechniqueSourcePublication, mergeOutfitTechniques, normalizeOutfitTechniqueFeed,
  normalizeTechniqueProgress, safeTechniqueSourceUrl, techniqueProgressVersion } from '../utils/outfitTechniqueFeed.js'

const PROGRESS_STORAGE_KEY = 'fashion.techniques.progress.v1'
const PREFERENCES_STORAGE_KEY = 'fashion.techniques.preferences.v1'
const emit = defineEmits(['open-wardrobe'])
const category = ref('全部')
const sharedLessonId = new URL(window.location.href).searchParams.get('technique') || ''
const selectedId = ref(sharedLessonId || outfitTechniques[0]?.id || '')
const showCheck = ref(false)
const selectedPlan = ref('')
const completedLessons = ref(new Map())
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
  try { completedLessons.value = new Map(normalizeTechniqueProgress(window.localStorage.getItem(PROGRESS_STORAGE_KEY))) } catch { /* Optional browser-only progress. */ }
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
const currentVersion = computed(() => selectedTechnique.value ? techniqueProgressVersion(selectedTechnique.value) : '')
const completedCount = computed(() => visibleTechniques.value.filter(isCompleted).length)
const currentCompleted = computed(() => Boolean(selectedTechnique.value && isCompleted(selectedTechnique.value)))
const nextTechnique = computed(() => visibleTechniques.value[(selectedIndex.value + 1) % visibleTechniques.value.length])
const isLastLesson = computed(() => selectedIndex.value === visibleTechniques.value.length - 1)
const comparisonVisual = computed(() => selectedTechnique.value?.comparisonVisual)
// These static illustrations describe this curated example only. API visuals keep their own attribution.
const editorialImages = computed(() => {
  const lesson = selectedTechnique.value
  const original = outfitTechniques[0]
  if (comparisonVisual.value || lesson?.id !== original.id || lesson.example.before !== original.example.before || lesson.example.after !== original.example.after) return []
  return [
    { url: '/assets/techniques/color-before-wide.jpg', alt: 'AI 示意：蓝色上衣搭配高饱和红色肩包', label: '较抢眼', colors: ['#165c9c', '#cc353b'], colorLabel: '蓝色与高饱和红色', caption: '对比配色', detail: '高饱和' },
    { url: '/assets/techniques/color-after-wide.jpg', alt: 'AI 示意：同一件蓝色上衣搭配低饱和豆沙棕色肩包', label: '更柔和', colors: ['#165c9c', '#ad827b'], colorLabel: '蓝色与低饱和豆沙棕色', caption: '降低饱和', detail: '更协调' }
  ]
})
const currentSaved = computed(() => savedLessons.value.has(selectedTechnique.value?.id))
const currentHelpful = computed(() => helpfulLessons.value.has(selectedTechnique.value?.id))

watch(() => `${selectedTechnique.value?.id}:${currentVersion.value}`, () => {
  showCheck.value = false
  selectedPlan.value = ''
  unavailableImages.value = new Set()
  shareUrl.value = ''
  shareCopied.value = false
})

function pad(value) { return String(value).padStart(2, '0') }
function categoryCount(value) { return value === '全部' ? sourcedTechniques.value.length : sourcedTechniques.value.filter(item => item.category === value).length }
function isCompleted(technique) { return completedLessons.value.get(technique.id) === techniqueProgressVersion(technique) }
function setCategory(value) {
  interacted = true
  category.value = value
  selectedId.value = visibleTechniques.value[0]?.id || ''
  announcement.value = `已切换到${value === '全部' ? '全部技巧' : value}，共 ${visibleTechniques.value.length} 节`
}
async function selectTechnique(id) {
  interacted = true
  selectedId.value = id
  announcement.value = `已打开第 ${selectedIndex.value + 1} 节`
  await nextTick()
  lessonTitle.value?.focus({ preventScroll: true })
  lessonArticle.value?.scrollIntoView({ block: 'start', behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth' })
}
function toggleCompleted() {
  if (!selectedTechnique.value) return
  const next = new Map(completedLessons.value)
  if (currentCompleted.value) next.delete(selectedTechnique.value.id)
  else next.set(selectedTechnique.value.id, currentVersion.value)
  completedLessons.value = next
  try { window.localStorage.setItem(PROGRESS_STORAGE_KEY, JSON.stringify([...next])) } catch { /* Reading never depends on storage permission. */ }
  announcement.value = currentCompleted.value ? '已标记本节完成，阅读进度仅保存在此浏览器' : '已取消完成标记'
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
  const message = kind === 'saved' ? (currentSaved.value ? '已收藏本节' : '已取消收藏') : (currentHelpful.value ? '已标记有帮助' : '已取消有帮助标记')
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
    announcement.value = '本节链接已复制'
  } catch {
    if (selectedTechnique.value?.id !== lessonId) return
    shareUrl.value = url.href
    announcement.value = '请复制下方本节链接'
  }
}
</script>

<template>
  <section class="technique-explorer" aria-labelledby="outfit-techniques-title">
    <header class="intro technique-heading">
      <img class="technique-hero-image" src="/assets/techniques/lesson-hero-light.jpg" alt="" width="2172" height="724" decoding="async" />
      <div class="technique-heading-copy">
        <p class="eyebrow">{{ category === '全部' ? '穿搭方法课' : `${category}方法课` }} / 共 {{ visibleTechniques.length }} 节</p>
        <h2 id="outfit-techniques-title" class="page-title">穿搭技巧</h2>
        <p class="lede">阅读真实原帖，把穿搭问题拆成可以判断的步骤。先看搭配关系，再用已有衣物试一试。</p>
      </div>
    </header>
    <nav v-if="sourcedTechniques.length" class="cats technique-categories" aria-label="按技巧主题筛选">
      <button v-for="value in availableCategories" :key="value" class="cat" type="button" :aria-label="value"
        :aria-pressed="category === value" @click="setCategory(value)">{{ value }}<em aria-hidden="true">{{ pad(categoryCount(value)) }}</em></button>
    </nav>
    <div v-if="sourcedTechniques.length" class="spread technique-layout">
      <aside class="rail" aria-label="本课目录">
        <div class="rail-head"><span>本课目录</span><span>{{ pad(visibleTechniques.length) }} 节</span></div>
        <ol class="step-list technique-choices" role="group" aria-label="选择穿搭技巧">
          <li v-for="(technique, index) in visibleTechniques" :key="technique.id">
            <button type="button" class="step-btn technique-choice" :class="{ 'is-active': selectedTechnique?.id === technique.id, 'is-done': isCompleted(technique) }"
              :aria-pressed="selectedTechnique?.id === technique.id" :aria-current="selectedTechnique?.id === technique.id ? 'step' : undefined"
              :aria-label="`第 ${index + 1} 节，${technique.title}${isCompleted(technique) ? '，已完成' : ''}`" aria-controls="technique-detail" @click="selectTechnique(technique.id)">
              <span class="step-num" aria-hidden="true">{{ pad(index + 1) }}</span>
              <span class="step-copy"><strong class="step-name">{{ technique.title }}</strong><span class="step-time"><span class="technique-source-count">{{ technique.sources.length }} 篇原帖</span> · {{ technique.steps.length }} 步练习</span></span>
              <span class="step-state" aria-hidden="true"><Check v-if="isCompleted(technique)" :size="10" /></span>
            </button>
          </li>
        </ol>
        <div class="rail-progress">
          <p :class="{ 'is-complete': completedCount === visibleTechniques.length }">{{ completedCount === visibleTechniques.length ? `已完成全部 ${visibleTechniques.length} 节` : `已完成 ${completedCount} / ${visibleTechniques.length}` }}</p>
          <div class="progress-line" role="progressbar" aria-label="本课阅读进度" :aria-valuenow="completedCount" :aria-valuemin="0" :aria-valuemax="visibleTechniques.length"><span :style="{ width: `${completedCount / visibleTechniques.length * 100}%` }"></span></div>
          <small>阅读进度仅保存在此浏览器</small>
        </div>
      </aside>
      <article v-if="selectedTechnique" id="technique-detail" ref="lessonArticle" class="lesson technique-detail">
        <header class="technique-detail-heading">
          <div class="lesson-progress" aria-hidden="true"><span v-for="technique in visibleTechniques" :key="technique.id" :class="{ 'is-done': isCompleted(technique), 'is-current': selectedTechnique.id === technique.id }"></span></div>
          <div class="lesson-topline">
            <p class="lesson-meta"><span>第 {{ pad(selectedIndex + 1) }} 节</span><span class="meta-divider" aria-hidden="true"></span><span>{{ selectedTechnique.category }} · {{ selectedTechnique.steps.length }} 步练习</span></p>
            <div class="lesson-tools" role="group" aria-label="本节操作">
              <button type="button" :aria-pressed="currentSaved" :title="currentSaved ? '取消收藏（仅此浏览器）' : '收藏本节（仅此浏览器）'" @click="togglePreference('saved')"><Bookmark :size="15" aria-hidden="true" /><span>{{ currentSaved ? '已收藏' : '收藏' }}</span></button>
              <button type="button" :aria-pressed="currentHelpful" title="标记有帮助（仅此浏览器）" @click="togglePreference('helpful')"><ThumbsUp :size="15" aria-hidden="true" /><span>{{ currentHelpful ? '已赞' : '有帮助' }}</span></button>
              <button type="button" @click="copyLessonLink"><Share2 :size="15" aria-hidden="true" /><span>{{ shareCopied ? '已复制' : '分享' }}</span></button>
            </div>
          </div>
          <p v-if="preferenceNotice" class="lesson-save-notice">{{ preferenceNotice }}</p>
          <label v-if="shareUrl" class="lesson-share-link">复制本节链接<input :value="shareUrl" readonly @focus="$event.target.select()" /></label>
          <h3 ref="lessonTitle" class="lesson-title" tabindex="-1" aria-live="polite">{{ selectedTechnique.title }}</h3>
          <p class="lesson-lede">{{ selectedTechnique.summary }}</p>
          <p v-if="selectedTechnique.generationMode === 'weekly-ai'" class="technique-ai-disclosure">每周精选 · {{ formatTechniquePublication(selectedTechnique.publishedAt) }} 发布<br>AI 根据原帖文字归纳。</p>
        </header>
        <section class="section technique-scene" :class="{ 'has-visual': editorialImages.length }" aria-labelledby="technique-scene-title">
          <div class="technique-scene-copy">
            <h4 id="technique-scene-title" class="section-title"><span class="sec-num" aria-hidden="true">01</span>先看场景</h4>
            <div class="scene"><span class="scene-check" aria-hidden="true"><Check :size="11" /></span><div><span class="scene-label">你可能正在这样穿</span><p>{{ selectedTechnique.example.before }}</p></div></div>
          </div>
          <figure v-if="editorialImages.length" class="technique-scene-visual">
            <div class="scene-image-pair">
              <template v-for="(image, index) in editorialImages" :key="image.url">
                <ArrowRight v-if="index" class="scene-arrow" :size="20" aria-hidden="true" />
                <div class="scene-image" :data-side="index === 0 ? 'a' : 'b'">
                  <img v-if="!unavailableImages.has(image.url)" :src="image.url" :alt="image.alt" width="1000" height="500" loading="lazy" decoding="async" @error="markImageUnavailable(image.url)" />
                  <p v-else class="technique-image-unavailable">示意图暂不可用，请参考文字。</p>
                  <span v-if="!unavailableImages.has(image.url)" class="scene-image-label"><X v-if="index === 0" :size="12" aria-hidden="true" /><Check v-else :size="12" aria-hidden="true" />{{ image.label }}</span>
                </div>
              </template>
            </div>
            <figcaption>AI 配色示意 · 非原帖实拍</figcaption>
          </figure>
          <div class="technique-reason"><h5 class="technique-subtitle">为什么这样搭</h5><p>{{ selectedTechnique.reason }}</p></div>
        </section>
        <section class="section technique-example" aria-labelledby="technique-example-title">
          <div class="technique-section-heading">
            <h4 id="technique-example-title" class="section-title"><span class="sec-num" aria-hidden="true">02</span>对照着试</h4>
            <p class="technique-example-note">{{ selectedTechnique.generationMode === 'weekly-ai' ? '以下为 AI 推演，非原帖对照实验。' : '以下为编辑推演，非原帖对照实验。' }}一次比较一种变化，可用衣橱里的相近衣物尝试。</p>
          </div>
          <figure v-if="comparisonVisual" class="technique-comparison-visual" :data-kind="comparisonVisual.kind">
            <figcaption><strong>{{ comparisonVisual.kind === 'source' ? '原帖对照图' : 'AI 对照示意图' }}</strong><span>{{ comparisonVisual.kind === 'source' ? '保留原帖图片，请结合原文理解。' : '用于理解 A / B 方案，不是原帖实拍或效果保证。' }}</span></figcaption>
            <div class="technique-visual-images" :class="{ 'is-pair': comparisonVisual.images.length === 2 }"><div v-for="image in comparisonVisual.images" :key="image.url" class="technique-visual-image">
              <img v-if="!unavailableImages.has(image.url)" :src="image.url" :alt="image.alt" loading="lazy" decoding="async" referrerpolicy="no-referrer" @error="markImageUnavailable(image.url)" />
              <p v-else class="technique-image-unavailable" role="status">对照图暂不可用，可继续阅读下方 A / B 文字方案。</p>
            </div></div>
            <p v-if="comparisonVisual.note" class="technique-visual-note">{{ comparisonVisual.note }}</p>
            <a v-if="comparisonVisual.sourceUrl" :href="comparisonVisual.sourceUrl" target="_blank" rel="noopener noreferrer">{{ comparisonVisual.kind === 'source' ? '查看图片原帖' : '查看方法来源' }}<ArrowRight :size="13" aria-hidden="true" /></a>
          </figure>
          <div class="compare technique-comparison" role="group" aria-label="A / B 练习方案">
            <section v-for="plan in ['a', 'b']" :key="plan" class="compare-side" :data-side="plan" :class="{ 'is-selected': selectedPlan === plan }">
              <div class="compare-head"><span class="compare-letter" aria-hidden="true">{{ plan.toUpperCase() }}</span><div class="compare-title"><h5>{{ plan === 'a' ? '先看当前穿法' : '再试一种调整' }}</h5><p><span>{{ plan === 'a' ? '调整前' : '调整后' }}</span> · {{ plan === 'a' ? '保留整套搭配，观察想调整的地方。' : '其余保持不变，先比较一处变化。' }}</p></div></div>
              <div v-if="editorialImages.length" class="compare-outfit">
                <img v-if="!unavailableImages.has(editorialImages[plan === 'a' ? 0 : 1].url)" :src="editorialImages[plan === 'a' ? 0 : 1].url" :alt="editorialImages[plan === 'a' ? 0 : 1].alt" width="1000" height="500" loading="lazy" decoding="async" @error="markImageUnavailable(editorialImages[plan === 'a' ? 0 : 1].url)" />
                <p v-else class="technique-image-unavailable">示意图暂不可用，请参考文字。</p>
                <div class="compare-colors"><div class="compare-swatches" role="img" :aria-label="editorialImages[plan === 'a' ? 0 : 1].colorLabel"><span v-for="color in editorialImages[plan === 'a' ? 0 : 1].colors" :key="color" :style="{ backgroundColor: color }"></span></div><p>{{ editorialImages[plan === 'a' ? 0 : 1].caption }}<br><span>（{{ editorialImages[plan === 'a' ? 0 : 1].detail }}）</span></p></div>
              </div>
              <dl class="compare-list"><div><dt>做法</dt><dd>{{ plan === 'a' ? selectedTechnique.example.before : selectedTechnique.example.after }}</dd></div></dl>
              <button type="button" class="technique-plan-toggle" :aria-pressed="selectedPlan === plan" @click="selectedPlan = selectedPlan === plan ? '' : plan">{{ selectedPlan === plan ? '已选' : '本次练习' }} {{ plan.toUpperCase() }}<Check v-if="selectedPlan === plan" :size="13" aria-hidden="true" /></button>
            </section>
          </div><p class="compare-note">先明确自己的目标，再决定保留哪一种变化。</p>
        </section>
        <section class="section" aria-labelledby="technique-how-title"><h4 id="technique-how-title" class="section-title"><span class="sec-num" aria-hidden="true">03</span><span>归纳的方法</span></h4><ol class="how-list technique-steps"><li v-for="(step, index) in selectedTechnique.steps" :key="step"><span class="how-num" aria-hidden="true">{{ pad(index + 1) }}</span><p>{{ step }}</p></li></ol></section>
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


        <div class="extra technique-practice">
          <button type="button" class="technique-check-toggle" :aria-expanded="showCheck" aria-controls="technique-checklist" @click="showCheck = !showCheck"><span class="extra-title">{{ showCheck ? '收起自查提示' : '展开自查提示' }}</span><small class="extra-hint" aria-hidden="true">带着一个问题去试衣</small><Plus :size="18" :class="{ expanded: showCheck }" aria-hidden="true" /></button>
          <div v-if="showCheck" id="technique-checklist" class="extra-body technique-checklist"><div class="extra-block"><h4>穿好后检查</h4><p v-if="selectedPlan" class="technique-plan-status">本次练习：{{ selectedPlan.toUpperCase() }} 方案</p><p>{{ selectedTechnique.check }}</p></div><button type="button" class="technique-wardrobe" @click="emit('open-wardrobe')"><Shirt :size="16" aria-hidden="true" />去我的衣橱练习<ArrowRight :size="16" aria-hidden="true" /></button></div>
        </div>
        <footer class="lesson-actions">
          <button type="button" class="done-toggle" :class="{ 'is-done': currentCompleted }" :aria-pressed="currentCompleted" @click="toggleCompleted"><span class="done-box" aria-hidden="true"><Check v-if="currentCompleted" :size="12" /></span>{{ currentCompleted ? '已完成本节' : '标记本节完成' }}</button>
          <button v-if="nextTechnique" type="button" class="next-btn" @click="selectTechnique(nextTechnique.id)"><span class="next-kicker">{{ isLastLesson ? '本课结束' : '下一节' }}</span><strong class="next-title">{{ isLastLesson ? '回到第一节' : nextTechnique.title }}</strong></button>
        </footer>
        <button v-if="!showCheck" type="button" class="technique-wardrobe technique-wardrobe-link" @click="emit('open-wardrobe')"><Shirt :size="15" aria-hidden="true" />去我的衣橱练习<ArrowRight :size="15" aria-hidden="true" /></button>
      </article>
    </div>
    <div v-else class="technique-empty" role="status"><h3>原帖整理中</h3><p>目前还没有整理完成的原帖技巧。有可核对的来源后，会在这里呈现作者要点与归纳方法。</p></div>
    <div class="technique-feed-meta">
      <p class="technique-weekly-status">每周约更新 1 条 · 质量优先，不凑数量<span v-if="weeklyFeed.latestPublishedAt"> · 最近更新 {{ formatTechniquePublication(weeklyFeed.latestPublishedAt) }}</span></p>
      <p v-if="weeklyFeed.status === 'disabled'" class="technique-weekly-note">自动整理暂未启用，可先阅读已整理的技巧。</p>
      <p v-else-if="['collecting', 'waiting-for-quality'].includes(weeklyFeed.status)" class="technique-weekly-note">新内容正在整理，来源与质量达标后更新。</p>
    </div>
    <p v-if="sourcedTechniques.length" class="technique-scope">原帖转述与综合归纳分别呈现；适用条件、取舍与练习示例为整理后的理解，可结合原帖和自己的衣物判断。</p>
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
.step-state { display: grid; place-items: center; width: 13px; height: 13px; margin-top: 1px; border: 1.5px solid #cdcbbf; border-radius: 50%; color: var(--paper); }
.step-btn.is-active:not(.is-done) .step-state { border: 3px solid #3e8555; }
.step-btn.is-done .step-state { border-color: var(--pine); background: var(--pine); }
.step-state svg { display: block; }
.rail-progress { margin: 8px 0 0; padding: 12px 9px 0; border-top: 1px solid var(--line); }
.rail-progress p { margin: 0 0 6px; color: #41483f; font-size: 11px; font-weight: 600; }
.progress-line { height: 4px; overflow: hidden; border-radius: 2px; background: #e4e0d7; }
.progress-line span { display: block; height: 100%; background: var(--pine); transition: width .25s ease; }
.rail-progress small { display: block; margin-top: 8px; color: var(--muted); font-size: 10px; line-height: 1.7; }
.lesson { min-width: 0; scroll-margin-top: 94px; }
.lesson-progress { display: flex; gap: 0; width: calc(100% - 220px); min-width: 90px; margin-bottom: 0; }
.lesson-progress span { flex: 1; height: 2px; background: #e8e4da; }
.lesson-progress span.is-current { background: var(--sage); }
.lesson-progress span.is-done { background: var(--pine); }
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
.technique-scene { margin-top: 12px; }
.technique-scene.has-visual { display: grid; grid-template-columns: 1.18fr 1fr; gap: 0 16px; align-items: center; }
.technique-scene-copy { min-width: 0; }
.scene { display: flex; align-items: flex-start; gap: 10px; padding: 12px 14px; border-radius: 6px; background: var(--pine-soft); }
.scene-check { display: grid; place-items: center; flex: none; width: 15px; height: 15px; margin-top: 2px; border-radius: 50%; color: #fff; background: #7c9d6c; }
.scene-label { display: block; color: #3c4839; font-size: 11px; line-height: 1.7; font-weight: 600; }
.scene p { margin: 3px 0 0; color: #253326; font-size: 13px; line-height: 1.7; font-weight: 600; }
.technique-scene-visual { position: relative; min-width: 0; margin: 0; }
.scene-image-pair { display: grid; grid-template-columns: minmax(0,1fr) 22px minmax(0,1fr); align-items: center; gap: 8px; }
.scene-image { position: relative; min-width: 0; overflow: hidden; border-radius: 6px; }
.scene-image img { display: block; width: 100%; height: auto; aspect-ratio: 1.06; object-fit: cover; object-position: center 34%; }
.scene-arrow { color: #334d3f; }
.scene-image-label { position: absolute; right: 0; bottom: 0; display: inline-flex; align-items: center; gap: 3px; padding: 3px 7px; border: 1px solid #e9b5a9; border-radius: 5px 0 5px 0; background: #fff0e8; color: #ab493b; font-size: 9px; line-height: 1.3; font-weight: 600; }
.scene-image[data-side="b"] .scene-image-label { color: #3b6f46; border-color: #c4d4b8; background: #eef4e9; }
.technique-scene-visual figcaption { position: absolute; right: 0; bottom: -15px; color: var(--muted); font-size: 9px; line-height: 1.5; text-align: right; }
.technique-reason { grid-column: 1 / -1; margin-top: 2px; }
.technique-subtitle { margin: 0; font-size: 15px; line-height: 1.2; }
.technique-reason p { margin: 0; color: var(--ink-soft); font-size: 12px; line-height: 1.4; }
.technique-example { margin-top: 3px; padding-top: 3px; border-top: 1px solid var(--line); }
.technique-section-heading { display: flex; align-items: baseline; gap: 20px; margin-bottom: 7px; }
.technique-section-heading .section-title { flex: none; margin: 0; }
.technique-example-note { margin: 0; color: #62695c; font-size: 10px; line-height: 1.7; }
.compare { display: grid; grid-template-columns: minmax(0,1fr) minmax(0,1fr); gap: 14px; }
.compare-side { --side: var(--pine); min-width: 0; padding: 11px 17px 10px; border: 1px solid #dfe3d3; border-radius: 7px; background: #f0f1e7; }
.compare-side[data-side="b"] { --side: #96713e; border-color: #e6ddce; background: #f5f0e7; }
.compare-side.is-selected { border-color: var(--side); }
.compare-head { display: flex; align-items: flex-start; gap: 13px; margin-bottom: 2px; }
.compare-letter { flex: none; color: var(--side); font: 700 36px/.95 var(--serif); }
.compare-title { min-width: 0; }
.compare-title h5 { margin: 0 0 3px; color: var(--ink); font-size: 13px; line-height: 1.5; }
.compare-title p { margin: 0; color: #646a5a; font-size: 9px; line-height: 1.6; }
.compare-outfit { display: grid; grid-template-columns: minmax(0,1.9fr) minmax(70px,1fr); align-items: center; gap: 12px; margin: 0 0 7px; }
.compare-outfit > img { display: block; width: 100%; height: auto; aspect-ratio: 2.12 / 1; border-radius: 5px; object-fit: cover; object-position: center 37%; }
.compare-colors { min-width: 0; text-align: center; }
.compare-swatches { display: flex; justify-content: center; gap: 8px; }
.compare-swatches span { display: block; flex: none; width: 23px; height: 23px; border-radius: 50%; }
.compare-colors p { margin: 7px 0 0; color: #4f5848; font-size: 10px; line-height: 1.5; }
.compare-colors p span { color: #62695c; font-size: 9px; }
.compare-list { margin: 0; }
.compare-list > div { display: grid; grid-template-columns: 28px minmax(0,1fr); gap: 8px; }
.compare-list dt { padding-top: 1px; color: #62695c; font-size: 10px; line-height: 1.8; }
.compare-list dd { margin: 0; color: var(--ink-soft); font-size: 11px; line-height: 1.8; }
.technique-plan-toggle { display: inline-flex; align-items: center; gap: 6px; min-height: 28px; margin-top: 7px; padding: 4px 0; border: 0; border-bottom: 1px solid #b7bda9; color: var(--side); background: none; font-size: 11px; line-height: 1.5; }
.technique-plan-toggle[aria-pressed="true"] { font-weight: 700; }
.compare-note { margin: 9px 0 0; color: var(--muted); font-size: 11px; line-height: 1.7; text-align: center; }
.technique-comparison-visual { margin: 12px 0 16px; }
.technique-comparison-visual figcaption { display: flex; flex-wrap: wrap; align-items: baseline; gap: 6px 12px; margin: 0 0 10px; color: var(--muted); font-size: 11px; line-height: 1.7; }
.technique-comparison-visual figcaption strong { color: var(--pine); font-weight: 600; }
.technique-visual-images { display: grid; gap: 12px; }
.technique-visual-images.is-pair { grid-template-columns: minmax(0,1fr) minmax(0,1fr); }
.technique-visual-image { min-width: 0; }
.technique-visual-image img { display: block; width: 100%; height: auto; max-height: 600px; border-radius: 5px; object-fit: contain; background: var(--paper-2); }
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
.extra { margin-top: 28px; border-top: 1px solid var(--line); border-bottom: 1px solid var(--line); }
.technique-check-toggle { display: flex; align-items: center; gap: 12px; width: 100%; padding: 16px 0; border: 0; background: none; text-align: left; }
.extra-title { color: var(--ink); font-size: 13px; font-weight: 600; }
.extra-hint { margin-left: auto; color: var(--muted); font-size: 11px; }
.technique-check-toggle > svg { flex: none; margin: 0 4px 0 auto; color: var(--muted); transition: transform .2s ease; }
.technique-check-toggle > svg.expanded { transform: rotate(45deg); }
.extra-body { display: grid; gap: 16px; padding-bottom: 18px; }
.extra-block h4 { margin: 0 0 6px; font: 700 17px/1.5 var(--serif); }
.extra-block p { margin: 0; color: var(--ink-soft); font-size: 13px; line-height: 1.85; }
.technique-checklist p.technique-plan-status { margin-bottom: 6px; color: var(--pine); font-size: 11px; }
.technique-wardrobe { display: inline-flex; align-items: center; gap: 8px; width: fit-content; padding: 7px 0; border: 0; color: var(--pine); background: none; font-size: 12px; }
.lesson-actions { display: flex; align-items: center; justify-content: space-between; gap: 24px; margin-top: 28px; padding-top: 20px; border-top: 1px solid var(--line-strong); }
.done-toggle { display: flex; align-items: center; gap: 9px; padding: 8px 0; border: 0; background: none; color: var(--ink-soft); font-size: 12px; }
.done-box { display: grid; place-items: center; flex: none; width: 17px; height: 17px; border: 1px solid #a9ad99; border-radius: 3px; color: var(--paper); }
.done-toggle.is-done .done-box { background: var(--pine); border-color: var(--pine); }
.next-btn { display: flex; flex-direction: column; align-items: flex-start; gap: 4px; max-width: 280px; padding: 11px 18px; border: 0; border-radius: 5px; background: var(--pine-deep); color: var(--paper); text-align: left; }
.next-btn:hover { background: #193c28; }
.next-kicker { font-size: 10px; color: #d3dfcb; }
.next-title { font-size: 12px; line-height: 1.6; font-weight: 500; overflow-wrap: anywhere; }
.technique-wardrobe-link { margin-top: 16px; }
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
  .scene { padding-block: 16px; }
  .scene p { font-size: 14px; }
  .compare-title h5 { font-size: 14px; }
  .compare-list dd { font-size: 12px; }
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
  .rail-progress { display: flex; align-items: center; gap: 12px; margin-top: 8px; padding: 0; border-top: 0; }
  .rail-progress p { flex: none; margin: 0; }
  .rail-progress .progress-line { width: 90px; }
  .rail-progress small { margin: 0 0 0 auto; }
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
  .rail-progress { flex-wrap: wrap; gap: 5px 10px; margin-top: 9px; }
  .rail-progress small { flex-basis: 100%; margin: 0; font-size: 10px; }
  .lesson { scroll-margin-top: 144px; }
  .lesson-progress { width: 100%; margin-bottom: 9px; }
  .lesson-topline { align-items: flex-start; flex-direction: column; gap: 9px; }
  .lesson-meta { font-size: 10px; }
  .lesson-tools { gap: 7px; margin: 0; }
  .lesson-tools button { min-height: 36px; padding: 8px 11px; font-size: 11px; }
  .lesson-title { margin-top: 10px; font-size: 25px; line-height: 1.45; }
  .lesson-lede { margin-top: 8px; font-size: 13px; line-height: 1.85; }
  .technique-scene.has-visual { grid-template-columns: minmax(0,1fr); gap: 14px; }
  .technique-scene { margin-top: 22px; }
  .section-title { gap: 8px; font-size: 16px; }
  .scene { padding: 13px 14px; }
  .scene p { font-size: 13px; }
  .scene-label { font-size: 11px; }
  .scene-image-pair { grid-template-columns: minmax(0,1fr) 24px minmax(0,1fr); gap: 10px; }
  .scene-image img { aspect-ratio: 1.08; }
  .scene-image-label { padding: 4px 7px; font-size: 10px; }
  .technique-scene-visual figcaption { position: static; margin-top: 5px; }
  .technique-reason { margin-top: 0; }
  .technique-reason p { font-size: 13px; line-height: 1.9; }
  .technique-subtitle { font-size: 15px; }
  .technique-example { margin-top: 20px; padding-top: 12px; }
  .technique-section-heading { align-items: flex-start; flex-direction: column; gap: 6px; margin-bottom: 12px; }
  .technique-example-note { font-size: 11px; }
  .compare { grid-template-columns: minmax(0,1fr); gap: 12px; }
  .compare-side { padding: 15px 17px; }
  .compare-letter { font-size: 40px; }
  .compare-title h5 { font-size: 15px; }
  .compare-title p { font-size: 10px; }
  .compare-outfit { grid-template-columns: minmax(0,2fr) minmax(76px,1fr); }
  .compare-list dt, .compare-list dd { font-size: 12px; }
  .compare-colors p { font-size: 11px; }
  .technique-plan-toggle { min-height: 36px; font-size: 12px; }
  .how-list p, .mistake p, .technique-suitable p, .technique-source-points p, .technique-synthesis p { font-size: 13px; line-height: 1.9; }
  .technique-source-list a { font-size: 13px; }
  .extra-hint { display: none; }
  .lesson-actions { flex-direction: column; align-items: stretch; gap: 14px; }
  .next-btn { width: 100%; max-width: none; }
  .next-title { font-size: 13px; }
  .done-toggle { min-height: 44px; }
}
@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after { scroll-behavior: auto !important; animation: none !important; transition: none !important; }
}
</style>
