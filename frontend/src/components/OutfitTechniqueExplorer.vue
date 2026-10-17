<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ArrowRight, Check, ChevronDown, Lightbulb, Shirt } from '@lucide/vue'
import { outfitTechniqueCategories, outfitTechniques } from '../data/outfitTechniques.js'
import { formatTechniquePublication, formatTechniqueSourcePublication, mergeOutfitTechniques, normalizeOutfitTechniqueFeed,
  safeTechniqueSourceUrl } from '../utils/outfitTechniqueFeed.js'

const emit = defineEmits(['open-wardrobe'])
const category = ref('全部')
const selectedId = ref(outfitTechniques[0]?.id || '')
const showCheck = ref(false)
const weeklyFeed = ref({ items: [], latestPublishedAt: null, status: null })
let interacted = false
let active = true
let feedRequest

onMounted(async () => {
  feedRequest = new AbortController()
  try {
    const response = await fetch('/api/v1/outfit-techniques', { signal: feedRequest.signal, credentials: 'same-origin' })
    if (!response.ok) return
    const feed = normalizeOutfitTechniqueFeed(await response.json())
    if (!active) return
    weeklyFeed.value = feed
    if (!interacted && feed.items.length) selectedId.value = sourcedTechniques.value[0]?.id || ''
  } catch {
    // Keep the curated lessons available when the weekly service cannot be read.
  }
})
onBeforeUnmount(() => {
  active = false
  feedRequest?.abort()
})

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
const visibleTechniques = computed(() => sourcedTechniques.value.filter(technique =>
  category.value === '全部' || technique.category === category.value))
const selectedTechnique = computed(() => visibleTechniques.value.find(technique => technique.id === selectedId.value)
  || visibleTechniques.value[0] || null)

watch(() => selectedTechnique.value?.id, () => { showCheck.value = false })

function setCategory(value) {
  interacted = true
  category.value = value
  selectedId.value = visibleTechniques.value[0]?.id || ''
}

function selectTechnique(id) {
  interacted = true
  selectedId.value = id
}
</script>

<template>
  <section class="technique-explorer" aria-labelledby="outfit-techniques-title">
    <header class="technique-heading">
      <p class="technique-eyebrow"><Lightbulb :size="15" aria-hidden="true" />从原帖中提炼可实践的方法</p>
      <h2 id="outfit-techniques-title">穿搭技巧</h2>
      <p>阅读相关穿搭帖，整理作者讲到的做法，再归纳适用条件和搭配思路。每条技巧都可回到原帖核对。</p>
      <p class="technique-weekly-status">每周约更新 1 条 · 质量优先，不凑数量<span v-if="weeklyFeed.latestPublishedAt"> · 最近更新 {{ formatTechniquePublication(weeklyFeed.latestPublishedAt) }}</span></p>
      <p v-if="weeklyFeed.status === 'disabled'" class="technique-weekly-note">自动整理暂未启用，可先阅读已整理的技巧。</p>
      <p v-else-if="['collecting', 'waiting-for-quality'].includes(weeklyFeed.status)" class="technique-weekly-note">新内容正在整理，来源与质量达标后更新。</p>
    </header>

    <nav v-if="sourcedTechniques.length" class="technique-categories" aria-label="按技巧主题筛选">
      <button v-for="value in availableCategories" :key="value" type="button"
        :aria-pressed="category === value" @click="setCategory(value)">{{ value }}</button>
    </nav>

    <div v-if="sourcedTechniques.length" class="technique-layout">
      <div class="technique-choices" :class="{ 'single-technique': visibleTechniques.length === 1 }" role="group" aria-label="选择穿搭技巧">
        <button v-for="technique in visibleTechniques" :key="technique.id" type="button" class="technique-choice"
          :aria-pressed="selectedTechnique?.id === technique.id" aria-controls="technique-detail"
          @click="selectTechnique(technique.id)">
          <span class="technique-choice-meta"><span class="technique-choice-category">{{ technique.category }}</span><span class="technique-source-count">{{ technique.sources.length }} 篇原帖</span></span>
          <strong>{{ technique.title }}</strong>
          <span class="technique-choice-summary">{{ technique.summary }}</span>
          <span class="technique-choice-action">{{ selectedTechnique?.id === technique.id ? '正在阅读' : '学习这招' }}<ArrowRight :size="15" aria-hidden="true" /></span>
        </button>
      </div>

      <article v-if="selectedTechnique" id="technique-detail" class="technique-detail">
        <header class="technique-detail-heading">
          <p class="technique-eyebrow">{{ selectedTechnique.category }} · 一次练习一种方法</p>
          <h3 aria-live="polite">{{ selectedTechnique.title }}</h3>
          <p>{{ selectedTechnique.summary }}</p>
          <p v-if="selectedTechnique.generationMode === 'weekly-ai'" class="technique-ai-disclosure">每周精选 · {{ formatTechniquePublication(selectedTechnique.publishedAt) }} 发布<br>AI 根据提取的文字正文归纳，未经人工逐条核读；原图和视频未核验，请结合原帖判断。</p>
        </header>

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

        <div class="technique-explanation">
          <section aria-labelledby="technique-how-title">
            <h4 id="technique-how-title">归纳的方法</h4>
            <p class="technique-section-note">结合原帖整理的练习步骤。</p>
            <ol class="technique-steps">
              <li v-for="(step, index) in selectedTechnique.steps" :key="step"><span aria-hidden="true">0{{ index + 1 }}</span><p>{{ step }}</p></li>
            </ol>
          </section>
          <section class="technique-reason" aria-labelledby="technique-reason-title">
            <h4 id="technique-reason-title">为什么这样搭</h4>
            <p>{{ selectedTechnique.reason }}</p>
          </section>
        </div>

        <section class="technique-example" aria-labelledby="technique-example-title">
          <h4 id="technique-example-title">对照着试</h4>
          <p class="technique-example-note">{{ selectedTechnique.generationMode === 'weekly-ai' ? '以下为 AI 推演，非原帖对照实验。' : '以下为编辑推演，非原帖对照实验。' }}依据原帖方法整理成练习示例，可用衣橱里的相近衣物尝试。</p>
          <div class="technique-comparison">
            <div><span>调整前</span><p>{{ selectedTechnique.example.before }}</p></div>
            <div><span>调整后</span><p>{{ selectedTechnique.example.after }}</p></div>
          </div>
        </section>

        <div class="technique-conditions">
          <section aria-labelledby="technique-suitable-title"><h4 id="technique-suitable-title">适合什么时候</h4><p>{{ selectedTechnique.suitable }}</p></section>
          <section aria-labelledby="technique-limit-title"><h4 id="technique-limit-title">注意取舍</h4><p>{{ selectedTechnique.limit }}</p></section>
        </div>

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

        <footer class="technique-practice">
          <div class="technique-practice-actions">
            <button type="button" class="technique-check-toggle" :aria-expanded="showCheck" aria-controls="technique-checklist"
              @click="showCheck = !showCheck"><Check :size="16" aria-hidden="true" />{{ showCheck ? '收起自查提示' : '展开自查提示' }}<ChevronDown :size="15" :class="{ expanded: showCheck }" aria-hidden="true" /></button>
            <button type="button" class="technique-wardrobe" @click="emit('open-wardrobe')"><Shirt :size="17" aria-hidden="true" />去我的衣橱练习<ArrowRight :size="16" aria-hidden="true" /></button>
          </div>
          <div v-if="showCheck" id="technique-checklist" class="technique-checklist"><h4>穿好后检查</h4><p>{{ selectedTechnique.check }}</p></div>
        </footer>
      </article>
    </div>
    <div v-else class="technique-empty" role="status"><h3>原帖整理中</h3><p>目前还没有整理完成的原帖技巧。有可核对的来源后，会在这里呈现作者要点与归纳方法。</p></div>
    <p v-if="sourcedTechniques.length" class="technique-scope">原帖转述与综合归纳分别呈现；适用条件、取舍与练习示例为整理后的理解，可结合原帖和自己的衣物判断。</p>
  </section>
</template>

<style scoped>
.technique-explorer {
  --technique-inset: var(--trend-panel-padding, 24px);
  margin: var(--trend-section-gap, 40px) 0 0;
  border-top: 1px solid var(--line);
  padding-top: 32px;
  color: var(--ink);
  scroll-margin-top: 100px;
}
.technique-eyebrow { display:flex; align-items:center; gap:8px; margin:0 0 12px; color:var(--accent); font-size:12px; font-weight:600; line-height:1.8; }
.technique-heading h2 { margin:0; font-family:var(--font-display); font-size:var(--trend-section-title-size, clamp(28px,2.5vw,34px)); font-weight:600; line-height:1.3; }
.technique-heading>p:not(.technique-eyebrow) { max-width:70ch; margin:12px 0 0; color:var(--muted); font-size:14px; line-height:1.8; }
.technique-heading>.technique-weekly-status,.technique-heading>.technique-weekly-note { font-size:12px; }
.technique-categories { display:flex; flex-wrap:wrap; gap:8px; margin:24px 0; }
.technique-categories button { min-height:44px; border:1px solid var(--line); border-radius:8px; padding:10px 20px; color:var(--muted); background:var(--surface); font:inherit; font-size:13px; cursor:pointer; }
.technique-categories button[aria-pressed="true"] { border-color:var(--accent); color:var(--surface); background:var(--accent); }
.technique-layout { display:grid; grid-template-columns:300px minmax(0,1fr); align-items:start; gap:24px; }
.technique-choices { display:grid; min-width:0; gap:12px; }
.technique-choice { display:flex; width:100%; min-width:0; flex-direction:column; align-items:stretch; gap:10px; border:1px solid var(--line); border-radius:16px; padding:var(--technique-inset); color:var(--ink); background:var(--surface); text-align:left; font:inherit; cursor:pointer; }
.technique-choice[aria-pressed="true"] { border-color:var(--accent); background:var(--accent-soft); }
.technique-choice-meta { display:flex; align-items:baseline; justify-content:space-between; flex-wrap:wrap; gap:8px; min-height:22px; line-height:22px; }
.technique-choice-category { color:var(--accent); font-size:12px; font-weight:600; }
.technique-source-count { color:var(--muted); font-size:12px; }
.technique-choice strong { font-family:var(--font-display); font-size:20px; font-weight:600; line-height:1.5; overflow-wrap:anywhere; }
.technique-choice-summary { color:var(--muted); font-size:12px; line-height:1.8; }
.technique-choice-action { display:flex; align-items:center; gap:8px; margin-top:2px; color:var(--accent); font-size:12px; line-height:1.8; }
.technique-detail { min-width:0; overflow:hidden; border:1px solid var(--line); border-radius:16px; background:var(--surface); }
.technique-detail-heading { padding:var(--technique-inset); background:var(--surface-soft); }
.technique-detail-heading>.technique-eyebrow { min-height:22px; line-height:22px; }
.technique-detail-heading h3 { margin:0; font-family:var(--font-display); font-size:clamp(24px,2.1vw,30px); font-weight:600; line-height:1.5; overflow-wrap:anywhere; }
.technique-detail-heading>p:not(.technique-eyebrow) { margin:12px 0 0; color:var(--muted); font-size:14px; line-height:1.8; }
.technique-detail-heading>.technique-ai-disclosure { border-top:1px solid var(--line); padding-top:12px; font-size:12px; }
.technique-detail h4 { margin:0; font-size:14px; font-weight:700; line-height:1.8; }
.technique-detail p { overflow-wrap:anywhere; }
.technique-detail a { color:var(--accent); overflow-wrap:anywhere; text-underline-offset:3px; }
.technique-detail a svg { display:inline; flex-shrink:0; margin-left:5px; vertical-align:middle; }
.technique-post-notes,.technique-synthesis,.technique-sources { border-top:1px solid var(--line); padding:var(--technique-inset); }
.technique-section-note { margin:8px 0 0; color:var(--muted); font-size:12px; line-height:1.8; }
.technique-note-list,.technique-source-list { display:grid; gap:20px; margin:20px 0 0; padding:0; list-style:none; }
.technique-note-list li { border-left:2px solid var(--line); padding-left:16px; }
.technique-note-list p,.technique-synthesis>p { margin:0; font-size:14px; line-height:1.8; }
.technique-note-list a { display:inline-block; margin-top:8px; font-size:12px; line-height:1.8; }
.technique-synthesis { background:var(--surface-soft); }
.technique-synthesis>p { margin-top:12px; color:var(--muted); }
.technique-source-list li { min-width:0; }
.technique-source-list a { font-size:14px; font-weight:600; line-height:1.8; }
.technique-source-meta,.technique-source-read,.technique-source-scope { margin:6px 0 0; color:var(--muted); font-size:12px; line-height:1.8; }
.technique-explanation { display:grid; grid-template-columns:minmax(0,1.15fr) minmax(0,.85fr); gap:24px; padding:var(--technique-inset); }
.technique-steps { display:grid; gap:16px; margin:20px 0 0; padding:0; list-style:none; }
.technique-steps li { display:grid; grid-template-columns:20px minmax(0,1fr); align-items:baseline; gap:12px; }
.technique-steps li>span { color:var(--accent); font-size:12px; font-weight:700; }
.technique-steps p,.technique-reason p,.technique-conditions p,.technique-checklist p { margin:0; font-size:14px; line-height:1.8; }
.technique-reason { border-left:1px solid var(--line); padding-left:24px; }
.technique-reason p { margin-top:16px; color:var(--muted); }
.technique-example { border-top:1px solid var(--line); padding:var(--technique-inset); }
.technique-example-note { margin:8px 0 20px; color:var(--muted); font-size:12px; line-height:1.8; }
.technique-comparison { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:16px; }
.technique-comparison>div { border:1px solid var(--line); border-radius:8px; padding:20px; background:var(--surface-soft); }
.technique-comparison>div:last-child { border-color:var(--accent); background:var(--accent-soft); }
.technique-comparison span { color:var(--accent); font-size:12px; font-weight:700; line-height:1.8; }
.technique-comparison p { margin:12px 0 0; font-size:14px; line-height:1.8; }
.technique-conditions { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:24px; border-top:1px solid var(--line); padding:var(--technique-inset); }
.technique-conditions p { margin-top:12px; color:var(--muted); }
.technique-practice { border-top:1px solid var(--line); padding:var(--technique-inset); }
.technique-practice-actions { display:flex; align-items:center; justify-content:space-between; gap:12px; flex-wrap:wrap; }
.technique-practice button { display:inline-flex; align-items:center; justify-content:center; gap:8px; min-height:44px; border:1px solid var(--accent); border-radius:8px; padding:10px 16px; font:inherit; font-size:13px; cursor:pointer; }
.technique-check-toggle { color:var(--accent); background:var(--surface); }
.technique-check-toggle .expanded { transform:rotate(180deg); }
.technique-wardrobe { color:var(--surface); background:var(--accent); }
.technique-checklist { margin-top:20px; border-left:3px solid var(--accent); padding:8px 0 8px 16px; }
.technique-checklist p { margin-top:8px; color:var(--muted); }
.technique-scope { margin:20px 0 0; color:var(--muted); font-size:12px; line-height:1.8; }
.technique-empty { margin-top:24px; border:1px solid var(--line); border-radius:16px; padding:var(--technique-inset); background:var(--surface); }
.technique-empty h3 { margin:0; font-family:var(--font-display); font-size:24px; line-height:1.5; }
.technique-empty p { margin:12px 0 0; color:var(--muted); font-size:14px; line-height:1.8; }
button:focus-visible,a:focus-visible { outline:2px solid var(--accent-strong); outline-offset:4px; }
@media(max-width:1100px) {
  .technique-layout { grid-template-columns:260px minmax(0,1fr); gap:20px; }
  .technique-explanation { grid-template-columns:1fr; }
  .technique-reason { border-left:0; border-top:1px solid var(--line); padding:20px 0 0; }
}
@media(max-width:960px) {
  .technique-layout { grid-template-columns:minmax(0,1fr); gap:20px; }
  .technique-choices { display:flex; gap:12px; overflow-x:auto; padding:0 0 8px; scroll-snap-type:x proximity; }
  .technique-choice { flex:0 0 280px; scroll-snap-align:start; }
  .technique-choices.single-technique .technique-choice { flex-basis:100%; }
  .technique-choice:focus-visible { outline-offset:-5px; }
}
@media(max-width:760px) {
  .technique-explorer { --technique-inset:var(--trend-panel-padding,20px); padding-top:28px; scroll-margin-top:142px; }
  .technique-categories { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); margin:20px 0; }
  .technique-categories button { padding-inline:12px; }
  .technique-layout { gap:16px; }
  .technique-choice { flex-basis:min(82%,288px); border-radius:12px; }
  .technique-choice strong { font-size:20px; }
  .technique-detail { border-radius:12px; }
  .technique-detail-heading h3 { font-size:24px; }
  .technique-comparison,.technique-conditions { grid-template-columns:minmax(0,1fr); gap:20px; }
  .technique-practice-actions { align-items:stretch; flex-direction:column; }
  .technique-comparison>div { padding:16px; }
}
@media(prefers-reduced-motion:reduce) { * { scroll-behavior:auto; } }
</style>
