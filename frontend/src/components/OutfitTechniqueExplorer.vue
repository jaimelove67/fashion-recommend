<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ArrowRight, Check, Plus, Shirt } from '@lucide/vue'
import { outfitTechniqueCategories, outfitTechniques } from '../data/outfitTechniques.js'
import { formatTechniquePublication, formatTechniqueSourcePublication, mergeOutfitTechniques, normalizeOutfitTechniqueFeed,
  normalizeTechniqueProgress, safeTechniqueSourceUrl, techniqueProgressVersion } from '../utils/outfitTechniqueFeed.js'

const PROGRESS_STORAGE_KEY = 'fashion.techniques.progress.v1'
const emit = defineEmits(['open-wardrobe'])
const category = ref('全部')
const selectedId = ref(outfitTechniques[0]?.id || '')
const showCheck = ref(false)
const selectedPlan = ref('')
const completedLessons = ref(new Map())
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
  feedRequest = new AbortController()
  try {
    const response = await fetch('/api/v1/outfit-techniques', { signal: feedRequest.signal, credentials: 'same-origin' })
    if (!response.ok) return
    const feed = normalizeOutfitTechniqueFeed(await response.json())
    if (!active) return
    weeklyFeed.value = feed
    if (!interacted && feed.items.length) selectedId.value = sourcedTechniques.value[0]?.id || ''
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
const selectedIndex = computed(() => visibleTechniques.value.findIndex(technique => technique.id === selectedTechnique.value?.id))
const currentVersion = computed(() => selectedTechnique.value ? techniqueProgressVersion(selectedTechnique.value) : '')
const completedCount = computed(() => visibleTechniques.value.filter(isCompleted).length)
const currentCompleted = computed(() => Boolean(selectedTechnique.value && isCompleted(selectedTechnique.value)))
const nextTechnique = computed(() => visibleTechniques.value[(selectedIndex.value + 1) % visibleTechniques.value.length])
const isLastLesson = computed(() => selectedIndex.value === visibleTechniques.value.length - 1)
const comparisonVisual = computed(() => selectedTechnique.value?.comparisonVisual)

watch(() => `${selectedTechnique.value?.id}:${currentVersion.value}`, () => {
  showCheck.value = false
  selectedPlan.value = ''
  unavailableImages.value = new Set()
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
</script>

<template>
  <section class="technique-explorer" aria-labelledby="outfit-techniques-title">
    <header class="intro technique-heading">
      <p class="eyebrow">{{ category === '全部' ? '穿搭方法课' : `${category}方法课` }} / 共 {{ visibleTechniques.length }} 节</p>
      <h2 id="outfit-techniques-title" class="page-title">穿搭技巧</h2>
      <p class="lede">阅读真实原帖，把穿搭问题拆成可以判断的步骤。先看搭配关系，再用已有衣物试一试。</p>
      <nav v-if="sourcedTechniques.length" class="cats technique-categories" aria-label="按技巧主题筛选">
        <button v-for="value in availableCategories" :key="value" class="cat" type="button" :aria-label="value"
          :aria-pressed="category === value" @click="setCategory(value)">{{ value }}<em aria-hidden="true">{{ pad(categoryCount(value)) }}</em></button>
      </nav>
    </header>
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
          <div class="progress-line" role="progressbar" aria-label="本课阅读进度" :aria-valuenow="completedCount" :aria-valuemin="0" :aria-valuemax="visibleTechniques.length"><span :style="{ width: `${completedCount / visibleTechniques.length * 100}%` }"></span></div>
          <p :class="{ 'is-complete': completedCount === visibleTechniques.length }">{{ completedCount === visibleTechniques.length ? `已完成全部 ${visibleTechniques.length} 节` : `已完成 ${completedCount} / ${visibleTechniques.length}` }}</p>
          <small>阅读进度仅保存在此浏览器</small>
        </div>
      </aside>
      <article v-if="selectedTechnique" id="technique-detail" ref="lessonArticle" class="lesson technique-detail">
        <header class="technique-detail-heading">
          <div class="lesson-progress" aria-hidden="true"><span v-for="technique in visibleTechniques" :key="technique.id" :class="{ 'is-done': isCompleted(technique), 'is-current': selectedTechnique.id === technique.id }"></span></div>
          <p class="lesson-meta"><span>第 {{ pad(selectedIndex + 1) }} 节</span><span class="meta-divider" aria-hidden="true"></span><span>{{ selectedTechnique.category }} · {{ selectedTechnique.steps.length }} 步练习</span></p>
          <h3 ref="lessonTitle" class="lesson-title" tabindex="-1" aria-live="polite">{{ selectedTechnique.title }}</h3>
          <p class="lesson-lede">{{ selectedTechnique.summary }}</p>
          <p v-if="selectedTechnique.generationMode === 'weekly-ai'" class="technique-ai-disclosure">每周精选 · {{ formatTechniquePublication(selectedTechnique.publishedAt) }} 发布<br>AI 根据提取的文字正文归纳，未经人工逐条核读；原图和视频未核验，请结合原帖判断。</p>
        </header>
        <section class="section" aria-labelledby="technique-scene-title">
          <h4 id="technique-scene-title" class="section-title"><span class="sec-num" aria-hidden="true">01</span>先看场景</h4>
          <div class="scene"><span class="scene-label">你可能正在这样穿</span><p>{{ selectedTechnique.example.before }}</p></div>
          <h5 class="technique-subtitle">为什么这样搭</h5><p>{{ selectedTechnique.reason }}</p>
        </section>
        <section class="section technique-example" aria-labelledby="technique-example-title">
          <h4 id="technique-example-title" class="section-title"><span class="sec-num" aria-hidden="true">02</span>对照着试</h4>
          <p class="technique-example-note">{{ selectedTechnique.generationMode === 'weekly-ai' ? '以下为 AI 推演，非原帖对照实验。' : '以下为编辑推演，非原帖对照实验。' }}一次比较一种变化，可用衣橱里的相近衣物尝试。</p>
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
              <div class="compare-head"><span class="compare-letter" aria-hidden="true">{{ plan.toUpperCase() }}</span><div class="compare-title"><h5>{{ plan === 'a' ? '先看当前穿法' : '再试一种调整' }}</h5><p>{{ plan === 'a' ? '调整前' : '调整后' }}</p></div></div>
              <dl class="compare-list"><div><dt>做法</dt><dd>{{ plan === 'a' ? selectedTechnique.example.before : selectedTechnique.example.after }}</dd></div></dl>
              <button type="button" class="technique-plan-toggle" :aria-pressed="selectedPlan === plan" @click="selectedPlan = selectedPlan === plan ? '' : plan">{{ selectedPlan === plan ? '已选' : '本次练习' }} {{ plan.toUpperCase() }}<Check v-if="selectedPlan === plan" :size="13" aria-hidden="true" /></button>
            </section>
          </div><p class="compare-note">先明确自己的目标，再决定保留哪一种变化。</p>
        </section>
        <section class="section" aria-labelledby="technique-how-title"><h4 id="technique-how-title" class="section-title"><span class="sec-num" aria-hidden="true">03</span><span>归纳的方法</span></h4><ol class="how-list technique-steps"><li v-for="(step, index) in selectedTechnique.steps" :key="step"><span class="how-num" aria-hidden="true">{{ pad(index + 1) }}</span><p>{{ step }}</p></li></ol></section>
        <aside class="mistake" aria-labelledby="technique-limit-title"><h4 id="technique-limit-title" class="mistake-label">注意取舍</h4><p>{{ selectedTechnique.limit }}</p></aside>
        <section class="technique-suitable" aria-labelledby="technique-suitable-title"><h4 id="technique-suitable-title">适合什么时候</h4><p>{{ selectedTechnique.suitable }}</p></section>
        <section class="technique-post-notes" aria-labelledby="technique-post-notes-title">
          <h4 id="technique-post-notes-title">原帖要点（转述）</h4>
          <p class="technique-section-note">以下为原文转述，阅读范围见下方参考原帖。</p>
          <ul v-if="selectedTechnique.sourceNotes.length" class="technique-note-list">
            <li v-for="(note, index) in selectedTechnique.sourceNotes" :key="`${note.sourceId}-${index}`">
              <p>{{ note.point }}</p>
              <a :href="note.source.url" target="_blank" rel="noopener noreferrer">{{ note.source.author }} · 查看原帖<ArrowRight :size="13" aria-hidden="true" /></a>
            </li>
          </ul>
          <p v-else class="technique-section-note">原帖要点仍在整理，可先查看下方参考原帖。</p>
        </section>

        <section v-if="selectedTechnique.synthesisNote" class="technique-synthesis" aria-labelledby="technique-synthesis-title">
          <h4 id="technique-synthesis-title">{{ selectedTechnique.sources.length > 1 ? '跨帖归纳' : '本帖归纳' }}</h4>
          <p>{{ selectedTechnique.synthesisNote }}</p>
        </section>

        <section class="technique-sources" aria-labelledby="technique-sources-title">
          <h4 id="technique-sources-title">参考原帖</h4>
          <ul class="technique-source-list">
            <li v-for="source in selectedTechnique.sources" :key="source.id || source.url">
              <a :href="source.url" target="_blank" rel="noopener noreferrer">{{ source.title }}<ArrowRight :size="14" aria-hidden="true" /></a>
              <p class="technique-source-meta">{{ source.author }} · {{ source.platform }} · {{ formatTechniqueSourcePublication(source.publishedAt) }}</p>
              <p v-if="source.readAt" class="technique-source-read">{{ selectedTechnique.generationMode === 'weekly-ai' ? '正文提取日期' : '阅读日期' }}：{{ source.readAt }}</p>
              <p class="technique-source-scope">阅读范围：{{ source.scope || '以链接中可核对的正文为准' }}</p>
            </li>
          </ul>
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

  .technique-explorer{
    --paper:#F3EFE6;
    --paper-2:#ECE6D9;
    --ink:#1F241F;
    --ink-soft:#4F534A;
    --muted:#6C6B60;
    --line:#D7D0C1;
    --line-strong:#B9B1A1;
    --pine:#244638;
    --pine-deep:#183327;
    --pine-soft:#E4E9DD;
    --sage:#B7C2AC;
    --clay:#96503B;
    --clay-soft:#F3E4DB;
    --serif:"Songti SC","Source Han Serif SC","Noto Serif SC","STSong","SimSun",serif;
    --sans:"PingFang SC","Hiragino Sans GB","Microsoft YaHei","Noto Sans SC","Helvetica Neue",Arial,sans-serif;
  }

  .technique-explorer { margin:var(--trend-section-gap,40px) 0 0; padding:0 26px 40px; background:var(--paper); color:var(--ink); font-family:var(--sans); border-top:1px solid var(--line); scroll-margin-top:100px; }
  .technique-explorer,.technique-explorer * { box-sizing:border-box; }
  button { font:inherit; }
  a { color:inherit; }
  button:focus-visible,a:focus-visible { outline:2px solid var(--pine); outline-offset:3px; }
  .sr-only{
    position:absolute;width:1px;height:1px;padding:0;margin:-1px;
    overflow:hidden;clip:rect(0,0,0,0);white-space:nowrap;border:0;
  }

  /* ---------- page intro ---------- */
  .page{max-width:1180px;margin:0 auto;padding:0 40px}
  .intro{padding:76px 0 40px}
  .eyebrow{
    margin:0 0 18px;font-size:11.5px;letter-spacing:.14em;color:var(--muted);
  }
  .page-title{
    margin:0;font-family:var(--serif);font-weight:700;
    font-size:clamp(52px,7.2vw,84px);line-height:1.04;letter-spacing:.015em;
    color:var(--ink);
  }
  .lede{
    max-width:640px;margin:22px 0 0;font-size:16.5px;line-height:1.85;
    color:var(--ink-soft);
  }

  /* ---------- category nav ---------- */
  .cats{
    display:flex;align-items:flex-end;gap:34px;margin-top:36px;
    border-bottom:1px solid var(--line);overflow-x:auto;scrollbar-width:none;
  }
  .cats::-webkit-scrollbar{display:none}
  .cat{
    position:relative;display:flex;align-items:baseline;gap:6px;
    background:none;border:none;padding:0 0 14px;margin:0;
    font-size:14.5px;color:var(--muted);cursor:pointer;white-space:nowrap;
    transition:color .18s ease;
  }
  .cat em{
    font-style:normal;font-size:10.5px;letter-spacing:.04em;color:#837D70;
  }
  .cat:hover{color:var(--ink)}
  .cat[aria-pressed="true"]{color:var(--ink);font-weight:600}
  .cat[aria-pressed="true"] em{color:var(--pine)}
  .cat[aria-pressed="true"]::after{
    content:"";position:absolute;left:0;right:0;bottom:-1px;height:2px;background:var(--pine);
  }

  /* ---------- spread ---------- */
  .spread{
    display:grid;grid-template-columns:252px minmax(0,1fr);
    gap:clamp(44px,6vw,76px);align-items:start;
    padding:0 0 100px;
  }
  .spread > *{min-width:0}
  .lesson{max-width:740px;scroll-margin-top:100px}

  /* ---------- rail ---------- */
  .rail{
    position:sticky;top:100px;align-self:start;
    padding-right:28px;border-right:1px solid var(--line);
  }
  .rail-head{
    display:flex;align-items:baseline;justify-content:space-between;gap:12px;
    padding:0 0 14px;border-bottom:1px solid var(--ink);
    font-size:11.5px;letter-spacing:.12em;color:var(--muted);
  }
  .step-list{list-style:none;margin:0;padding:0;max-width:100%}
  .step-list li + li{border-top:1px solid var(--line)}
  .step-btn{
    width:100%;display:grid;grid-template-columns:24px minmax(0,1fr) 16px;
    gap:10px;align-items:start;padding:14px 10px 14px 12px;margin:0;
    background:none;border:none;text-align:left;color:var(--ink);cursor:pointer;
    transition:background .18s ease;
  }
  .step-btn:hover{background:rgba(36,70,56,.05)}
  .step-btn.is-active{
    background:var(--pine-soft);box-shadow:inset 2px 0 0 var(--pine);
  }
  .step-num{
    font-family:var(--serif);font-size:12px;line-height:1.5;color:var(--muted);padding-top:1px;
  }
  .step-btn.is-active .step-num{color:var(--pine)}
  .step-copy{display:block;min-width:0}
  .step-name{display:block;font-size:13.5px;line-height:1.55;font-weight:500}
  .step-btn.is-active .step-name{font-weight:600}
  .step-time{display:block;margin-top:4px;font-size:10.5px;letter-spacing:.04em;color:var(--muted)}
  .step-state{
    width:16px;height:16px;margin-top:2px;border:1px solid #B9B2A3;border-radius:50%;
    display:grid;place-items:center;font-size:9px;line-height:1;color:transparent;
    transition:background .18s ease,border-color .18s ease,color .18s ease;
  }
  .step-btn.is-active:not(.is-done) .step-state{border-color:var(--pine)}
  .step-btn.is-done .step-state{background:var(--pine);border-color:var(--pine);color:var(--paper)}
  .rail-progress{margin-top:26px}
  .progress-line{height:2px;background:var(--line);overflow:hidden}
  .progress-line span{
    display:block;height:100%;width:0;background:var(--pine);
    transition:width .32s cubic-bezier(.2,.7,.2,1);
  }
  .rail-progress p{margin:9px 0 0;font-size:11px;letter-spacing:.04em;color:var(--muted)}
  .rail-progress p.is-complete{color:var(--pine)}

  /* ---------- lesson article ---------- */
  .lesson-progress{display:flex;gap:5px;margin-bottom:22px}
  .lesson-progress span{flex:1;height:2px;background:var(--line)}
  .lesson-progress span.is-done{background:var(--pine)}
  .lesson-progress span.is-current{background:var(--sage)}
  .lesson-meta{
    display:flex;align-items:center;gap:10px;margin:0 0 14px;
    font-size:11.5px;letter-spacing:.06em;color:var(--muted);
  }
  .meta-divider{width:14px;height:1px;background:var(--line-strong);display:block}
  .lesson-title{
    margin:0 0 18px;font-family:var(--serif);font-weight:700;
    font-size:clamp(30px,3.2vw,40px);line-height:1.38;letter-spacing:.005em;color:var(--ink);
  }
  .lesson-lede{
    margin:0;max-width:38em;font-size:17px;line-height:1.85;color:var(--ink-soft);
  }
  .section{margin-top:52px}
  .section-title{
    display:flex;align-items:baseline;gap:12px;margin:0 0 20px;
    font-family:var(--serif);font-size:21px;line-height:1.4;font-weight:700;color:var(--ink);
  }
  .sec-num{
    flex:none;font-family:var(--serif);font-size:11.5px;letter-spacing:.06em;
    font-weight:400;color:var(--pine);
  }
  .section > p{margin:0;font-size:16px;line-height:1.9;color:#3C4038}

  .scene{
    display:grid;grid-template-columns:112px minmax(0,1fr);gap:20px;
    margin:26px 0 28px;padding:20px 0;
    border-top:1px solid var(--line);border-bottom:1px solid var(--line);
  }
  .scene-label{
    padding-top:5px;font-size:11px;line-height:1.65;letter-spacing:.06em;color:var(--muted);
  }
  .scene p{
    margin:0;font-family:var(--serif);font-size:18px;line-height:1.82;color:var(--ink);
  }

  /* ---------- comparison ---------- */
  .compare{
    display:grid;grid-template-columns:1fr 1fr;
    border-top:1px solid var(--line-strong);border-bottom:1px solid var(--line-strong);
  }
  .compare-side{position:relative;padding:24px 26px 26px}
  .compare-side + .compare-side{border-left:1px solid var(--line)}
  .compare-side::before{
    content:"";position:absolute;top:-1px;left:0;right:0;height:2px;background:var(--side);
  }
  .compare-side[data-side="a"]{--side:var(--pine)}
  .compare-side[data-side="b"]{--side:#7A5C43}
  .compare-head{display:flex;align-items:flex-start;gap:16px;margin-bottom:20px}
  .compare-letter{
    flex:none;font-family:var(--serif);font-size:52px;line-height:.86;
    font-weight:500;color:var(--side);
  }
  .compare-title h5{
    margin:2px 0 4px;font-family:var(--serif);font-size:21px;line-height:1.3;font-weight:700;
  }
  .compare-title p{margin:0;font-size:11.5px;letter-spacing:.08em;color:var(--muted)}
  .compare-list{margin:0;display:grid;gap:0}
  .compare-list > div{
    display:grid;grid-template-columns:44px minmax(0,1fr);gap:10px;
    padding:9px 0;border-top:1px solid rgba(185,177,161,.36);
  }
  .compare-list dt{padding-top:2px;font-size:10.5px;letter-spacing:.04em;color:var(--muted)}
  .compare-list dd{margin:0;font-size:14px;line-height:1.7;color:var(--ink-soft)}
  .compare-note{
    margin:12px 0 0;font-size:12.5px;line-height:1.7;text-align:center;color:var(--muted);
  }

  /* ---------- how-to list ---------- */
  .how-list{list-style:none;margin:0;padding:0;border-top:1px solid var(--line)}
  .how-list li{
    display:grid;grid-template-columns:54px minmax(0,1fr);gap:16px;
    padding:20px 0;border-bottom:1px solid var(--line);
  }
  .how-num{
    font-family:var(--serif);font-size:24px;line-height:1;color:var(--pine);padding-top:2px;
  }
  .how-list h4{margin:0 0 6px;font-size:16px;line-height:1.55;font-weight:600}
  .how-list p{margin:0;font-size:14.5px;line-height:1.78;color:var(--ink-soft)}

  /* ---------- mistake ---------- */
  .mistake{
    margin:48px 0;padding:22px 26px;background:var(--clay-soft);
    border-left:3px solid var(--clay);
  }
  .mistake-label{
    display:block;margin-bottom:8px;font-size:10.5px;letter-spacing:.16em;
    font-weight:600;color:var(--clay);
  }
  .mistake p{margin:0;font-size:15.5px;line-height:1.8;color:#403831}

  /* ---------- extra ---------- */
  .extra{border-top:1px solid var(--line);border-bottom:1px solid var(--line)}
  .extra summary{
    display:flex;align-items:center;gap:14px;padding:18px 0;
    cursor:pointer;list-style:none;
  }
  .extra summary::-webkit-details-marker{display:none}
  .extra-title{font-size:14.5px;font-weight:600;color:var(--ink)}
  .extra-hint{margin-left:auto;font-size:12px;color:var(--muted)}
  .extra-plus{
    flex:none;width:18px;text-align:center;font-size:20px;font-weight:300;
    line-height:1;color:var(--muted);transition:transform .2s ease;
  }
  .extra[open] .extra-plus{transform:rotate(45deg)}
  .extra-body{display:grid;gap:22px;padding:0 0 24px 0}
  .extra-block h4{
    margin:0 0 8px;font-family:var(--serif);font-size:17px;line-height:1.5;font-weight:700;
  }
  .extra-block p{margin:0;font-size:14.5px;line-height:1.82;color:var(--ink-soft)}

  /* ---------- actions ---------- */
  .lesson-actions{
    display:flex;align-items:center;justify-content:space-between;gap:24px;
    margin-top:40px;padding-top:26px;border-top:1px solid var(--ink);
  }
  .done-toggle{
    display:flex;align-items:center;gap:10px;padding:0;margin:0;
    background:none;border:none;cursor:pointer;font-size:13.5px;color:var(--ink-soft);
    transition:color .18s ease;
  }
  .done-toggle:hover{color:var(--ink)}
  .done-box{
    width:18px;height:18px;flex:none;border:1px solid #A9A294;
    display:grid;place-items:center;font-size:11px;line-height:1;color:var(--paper);
    transition:background .18s ease,border-color .18s ease;
  }
  .done-toggle.is-done{color:var(--ink)}
  .done-toggle.is-done .done-box{background:var(--pine);border-color:var(--pine)}
  .next-btn{
    display:flex;flex-direction:column;align-items:flex-start;gap:4px;
    min-width:230px;max-width:290px;padding:13px 22px;margin:0;
    background:var(--pine);color:#F7F3EA;border:none;cursor:pointer;text-align:left;
    transition:background .18s ease,transform .18s ease;
  }
  .next-btn:hover{background:var(--pine-deep);transform:translateY(-1px)}
  .next-kicker{font-size:10px;letter-spacing:.15em;opacity:.62}
  .next-title{font-size:14px;line-height:1.45;font-weight:500}

  /* ---------- brief lessons ---------- */
  .points{list-style:none;margin:0;padding:0;border-top:1px solid var(--line)}
  .points li{
    display:grid;grid-template-columns:44px minmax(0,1fr);gap:16px;
    padding:18px 0;border-bottom:1px solid var(--line);
  }
  .point-num{
    padding-top:3px;font-family:var(--serif);font-size:11.5px;letter-spacing:.04em;color:var(--pine);
  }
  .points h4{margin:0 0 5px;font-size:15.5px;line-height:1.55;font-weight:600}
  .points p{margin:0;font-size:14.5px;line-height:1.78;color:var(--ink-soft)}
  .brief-note{margin:38px 0 0;padding:18px 20px;background:var(--pine-soft)}
  .brief-note-label{
    display:block;margin-bottom:6px;font-size:10px;letter-spacing:.16em;
    font-weight:600;color:var(--pine);
  }
  .brief-note p{margin:0;font-size:14px;line-height:1.8;color:var(--ink-soft)}

  /* ---------- entrance ---------- */
  @media (prefers-reduced-motion:no-preference){
    .intro > *{animation:rise .55s cubic-bezier(.22,.7,.28,1) both}
    .intro > *:nth-child(2){animation-delay:.04s}
    .intro > *:nth-child(3){animation-delay:.08s}
    .intro > *:nth-child(4){animation-delay:.12s}
    .spread{animation:rise .65s .1s cubic-bezier(.22,.7,.28,1) both}
  }
  @keyframes rise{
    from{opacity:0;transform:translateY(10px)}
    to{opacity:1;transform:none}
  }

  /* ---------- tablet ---------- */
  @media (max-width:1020px){
    .technique-explorer{padding-inline:16px}
    .spread{grid-template-columns:1fr;gap:34px;padding-bottom:76px}
    .rail{
      position:static;padding:0;border-right:none;border-bottom:none;
    }
    .step-list{
      display:flex;overflow-x:auto;scrollbar-width:none;
      border-bottom:1px solid var(--line);
    }
    .step-list::-webkit-scrollbar{display:none}
    .step-list li{flex:0 0 224px}
    .step-list li + li{border-top:none;border-left:1px solid var(--line)}
    .step-btn{padding:14px 14px 16px}
    .rail-progress{
      display:flex;align-items:center;gap:14px;max-width:none;margin-top:14px;
    }
    .rail-progress .progress-line{width:120px;flex:none}
    .rail-progress p{margin:0}
    .lesson{max-width:none;scroll-margin-top:100px}
  }

  /* ---------- mobile ---------- */
  @media (max-width:680px){
    .technique-explorer{padding-inline:4px;scroll-margin-top:142px}
    .masthead-inner{padding:0 20px;height:58px}
    .top-nav{gap:16px}
    .top-nav a{font-size:11.5px}
    .page{padding:0 20px}
    .intro{padding:48px 0 26px}
    .page-title{font-size:clamp(48px,14vw,62px)}
    .lede{font-size:15.5px;line-height:1.8;margin-top:18px}
    .cats{gap:24px;margin-top:28px}
    .cat{font-size:13.5px}
    .spread{gap:28px;padding-bottom:64px}
    .step-list li{flex-basis:200px}
    .rail-progress{display:none}
    .lesson-title{font-size:28px;line-height:1.4}
    .lesson-lede{font-size:16px;line-height:1.8}
    .section{margin-top:42px}
    .section-title{font-size:19px;gap:9px;margin-bottom:16px}
    .section > p{font-size:15.5px;line-height:1.85}
    .scene{grid-template-columns:1fr;gap:7px;padding:18px 0;margin:22px 0 24px}
    .scene-label{padding-top:0}
    .scene p{font-size:16.5px;line-height:1.8}
    .compare{grid-template-columns:1fr}
    .compare-side{padding:22px 20px 24px}
    .compare-side + .compare-side{border-left:none;border-top:1px solid var(--line)}
    .compare-letter{font-size:44px}
    .compare-title h5{font-size:19px}
    .how-list li{grid-template-columns:42px minmax(0,1fr);gap:12px;padding:18px 0}
    .how-num{font-size:21px}
    .mistake{margin:38px 0;padding:18px 20px}
    .extra-hint{display:none}
    .lesson-actions{flex-direction:column;align-items:stretch;gap:14px;margin-top:34px}
    .next-btn{order:1;width:100%;max-width:none;min-width:0}
    .done-toggle{order:2;justify-content:center;padding:10px 0}
    .points li{grid-template-columns:36px minmax(0,1fr);gap:12px}
    .footer{padding:22px 20px 32px;flex-direction:column;gap:8px}
  }

  @media (prefers-reduced-motion:reduce){
    *,*::before,*::after{animation:none!important;transition:none!important}
    html{scroll-behavior:auto}
  }

.technique-weekly-status,.technique-weekly-note { margin:12px 0 0; color:var(--muted); font-size:11px; line-height:1.8; }
.technique-feed-meta { margin:0 0 24px; }
.technique-detail p,.technique-detail a,.technique-detail dd { overflow-wrap:anywhere; }
.step-name,.next-title { overflow-wrap:anywhere; }
.technique-detail h3:focus { outline:none; }
.step-btn { height:100%; }
.step-state svg { display:block; }
.rail-progress small { display:block; margin-top:8px; color:var(--muted); font-size:10px; line-height:1.6; }
.technique-ai-disclosure { margin:18px 0 0; padding-top:12px; border-top:1px solid var(--line); color:var(--muted); font-size:11px; line-height:1.8; }
.technique-subtitle { margin:0 0 8px; font-size:16px; line-height:1.5; }
.section>.technique-example-note { margin:0 0 20px; color:var(--muted); font-size:12px; line-height:1.7; }
.compare-side.is-selected { background:rgba(36,70,56,.035); }
.technique-plan-toggle { display:flex; align-items:center; gap:6px; margin:14px 0 0; padding:5px 0; border:0; border-bottom:1px solid var(--line-strong); background:none; color:var(--side); font-size:11px; cursor:pointer; }
.technique-comparison-visual { margin:0 0 24px; }
.technique-comparison-visual figcaption { display:flex; align-items:baseline; flex-wrap:wrap; gap:6px 12px; margin:0 0 12px; color:var(--muted); font-size:11px; line-height:1.7; }
.technique-comparison-visual figcaption strong { color:var(--pine); font-weight:600; }
.technique-visual-images { display:grid; gap:12px; }
.technique-visual-images.is-pair { grid-template-columns:minmax(0,1fr) minmax(0,1fr); }
.technique-visual-image { min-width:0; }
.technique-visual-image img { display:block; width:100%; height:auto; max-height:680px; object-fit:contain; background:var(--paper-2); }
.technique-comparison-visual>p,.technique-image-unavailable { margin:8px 0; color:var(--muted); font-size:12px; line-height:1.8; }
.technique-image-unavailable { padding:18px; border:1px solid var(--line); }
.technique-comparison-visual>a { font-size:11px; color:var(--pine); }
.technique-detail a svg { display:inline; margin-left:5px; vertical-align:middle; }
.how-list>li>p { margin:0; color:var(--ink-soft); font-size:15px; line-height:1.78; }
.technique-suitable { padding-bottom:26px; border-bottom:1px solid var(--line); }
.technique-suitable h4,.technique-sources h4,.technique-post-notes h4 { margin:0 0 10px; font-family:var(--serif); font-size:19px; line-height:1.5; }
.technique-suitable p,.technique-synthesis p { margin:0; color:var(--ink-soft); font-size:14.5px; line-height:1.82; }
.technique-post-notes { margin-top:52px; }
.technique-section-note { margin:0; color:var(--muted); font-size:12px; line-height:1.8; }
.technique-note-list,.technique-source-list { display:grid; gap:22px; margin:20px 0 0; padding:0; list-style:none; }
.technique-note-list li { padding-left:16px; border-left:2px solid var(--line); }
.technique-note-list p { margin:0; color:var(--ink-soft); font-size:14.5px; line-height:1.82; }
.technique-note-list a { display:inline-block; margin-top:8px; color:var(--pine); font-size:11px; line-height:1.8; }
.technique-synthesis { margin-top:26px; padding:18px 20px; background:var(--pine-soft); }
.technique-synthesis h4 { margin-bottom:6px; color:var(--pine); font-size:11px; letter-spacing:.12em; }
.technique-sources { margin-top:34px; padding-top:22px; border-top:1px solid var(--line); }
.technique-source-list a { color:var(--pine); font-size:13px; line-height:1.7; font-weight:500; }
.technique-source-meta,.technique-source-read,.technique-source-scope { margin:5px 0 0; color:var(--muted); font-size:11px; line-height:1.8; }
.technique-practice { margin-top:34px; }
.technique-check-toggle { display:flex; align-items:center; gap:14px; width:100%; padding:18px 0; border:0; background:none; text-align:left; cursor:pointer; }
.technique-check-toggle>svg { flex:none; margin-left:auto; margin-right:4px; color:var(--muted); transition:transform .2s ease; }
.technique-check-toggle>svg.expanded { transform:rotate(45deg); }
.technique-checklist p.technique-plan-status { margin:0 0 6px; color:var(--pine); font-size:12px; }
.technique-wardrobe { display:inline-flex; align-items:center; gap:8px; width:fit-content; padding:6px 0; border:0; color:var(--pine); background:none; font-size:12px; cursor:pointer; }
.technique-wardrobe-link { margin-top:22px; }
.technique-scope { margin:0; padding-top:24px; border-top:1px solid var(--line); color:var(--muted); font-size:11px; line-height:1.8; }
.technique-empty { padding:32px 0; color:var(--ink-soft); line-height:1.8; }
@media(max-width:1020px) { .rail-progress small{margin:0 0 0 auto} }
@media(max-width:680px) { .technique-post-notes{margin-top:42px} .technique-visual-images.is-pair{gap:8px} }

</style>
