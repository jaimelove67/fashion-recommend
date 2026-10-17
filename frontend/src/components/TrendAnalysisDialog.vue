<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { ArrowUpRight, Check, Shirt, X } from '@lucide/vue'
import { trendOutfitAnalysis, isTrendAnalysisExample } from '../data/trendOutfitAnalyses.js'
import { trendGalleryImageUrl } from '../utils/trendGallery.js'
import { trendImageCandidates, trendDisplayImageUrl } from '../utils/trendImage.js'
import { externalTrendSourceUrl, trendPlatformName } from '../utils/trendSources.js'

const props = defineProps({
  trend: { type: Object, required: true },
  wardrobe: { type: Array, default: () => [] },
  wardrobeLoading: { type: Boolean, default: false }
})
const emit = defineEmits(['close', 'use-trend', 'open-wardrobe'])
const dialog = ref(null)
const pointContent = ref(null)
const analysis = computed(() => trendOutfitAnalysis(props.trend))
const activeImageId = ref(analysis.value?.images[0]?.id || 'source')
const activePointId = ref(analysis.value?.points[0]?.id || '')
const activeChangeId = ref(analysis.value?.changes[0]?.id || '')
const unavailableImages = ref(new Set())
const sourceUrl = computed(() => externalTrendSourceUrl(props.trend))
const dateLabel = computed(() => {
  const date = new Date(props.trend.publishedAt)
  return props.trend.publishedAt && !Number.isNaN(date.getTime())
    ? date.toLocaleDateString('zh-CN', { timeZone: 'Asia/Shanghai' }) : '发布时间未提供'
})
const images = computed(() => {
  if (analysis.value) return analysis.value.images.filter(image => !unavailableImages.value.has(image.url))
  const source = trendGalleryImageUrl(props.trend) || props.trend.imageUrl || props.trend.evidence?.images?.find(Boolean)
  return trendImageCandidates(source || '').map(trendDisplayImageUrl)
    .filter(url => !unavailableImages.value.has(url))
    .slice(0, 1).map(url => ({ id: 'source', url, label: '原帖配图', width: 3, height: 4, pins: [] }))
})
const activeImage = computed(() => images.value.find(image => image.id === activeImageId.value) || images.value[0])
const activePoint = computed(() => analysis.value?.points.find(point => point.id === activePointId.value))
const activeChange = computed(() => analysis.value?.changes.find(change => change.id === activeChangeId.value))
const wardrobeMatches = computed(() => {
  const tags = (props.trend.topicTags || []).map(tag => String(tag).trim().toLocaleLowerCase('zh-CN')).filter(Boolean)
  return props.wardrobe.filter(item => item?.id && item.recognitionStatus !== 'NEEDS_MANUAL_REVIEW')
    .map(item => {
      const text = [item.name, item.category, item.color, item.style].filter(Boolean).join(' ').toLocaleLowerCase('zh-CN')
      return { item, tags: tags.filter(tag => text.includes(tag)) }
    }).filter(match => match.tags.length)
    .sort((a, b) => b.tags.length - a.tags.length).slice(0, 4)
})

let originFocus = null
let originScroll = null
let originHash = ''
let previousOverflow = ''
let restorePosition = true

function markImageUnavailable(event) {
  unavailableImages.value = new Set([...unavailableImages.value, event.currentTarget.getAttribute('src')])
}

async function selectPoint(id, fromPhoto = false) {
  activePointId.value = id
  if (fromPhoto && window.matchMedia('(max-width:760px)').matches) {
    await nextTick()
    pointContent.value?.scrollIntoView({ block: 'start', behavior: 'instant' })
  }
}

function navigate(event) {
  restorePosition = false
  emit(event, props.trend)
}

function closeOnBackdrop(event) {
  if (event.target !== dialog.value) return
  const bounds = dialog.value.getBoundingClientRect()
  if (event.clientX < bounds.left || event.clientX > bounds.right || event.clientY < bounds.top || event.clientY > bounds.bottom) emit('close')
}

function keepFocusInDialog(event) {
  if (event.key !== 'Tab' || event.altKey || event.ctrlKey || event.metaKey) return
  const controls = [...dialog.value.querySelectorAll('a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]')]
    .filter(element => element.tabIndex >= 0 && element.getClientRects().length)
  const first = controls[0]
  const last = controls.at(-1)
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault()
    last?.focus()
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault()
    first?.focus()
  }
}

onMounted(() => {
  originFocus = document.activeElement
  originScroll = { top: window.scrollY, left: window.scrollX }
  originHash = window.location.hash
  previousOverflow = document.documentElement.style.overflow
  document.documentElement.style.overflow = 'hidden'
  dialog.value.showModal()
})

onBeforeUnmount(() => {
  dialog.value?.close()
  document.documentElement.style.overflow = previousOverflow
  if (restorePosition) void nextTick(() => {
    if (window.location.hash !== originHash) return
    originFocus?.isConnected && originFocus.focus({ preventScroll: true })
    if (originScroll) window.scrollTo({ ...originScroll, behavior: 'instant' })
  })
})
</script>

<template>
  <Teleport to="body">
    <dialog ref="dialog" class="trend-analysis-dialog" aria-labelledby="trend-analysis-title" @cancel.prevent="emit('close')" @click="closeOnBackdrop" @keydown="keepFocusInDialog">
      <header class="analysis-header">
        <div><p>{{ isTrendAnalysisExample(trend) ? '历史作品 · 分析示例' : trend.dailySlot?.label || '选中的穿搭' }}</p><h2 id="trend-analysis-title">深入分析</h2></div>
        <button type="button" class="analysis-close" aria-label="关闭深入分析" autofocus @click="emit('close')"><X :size="22" aria-hidden="true" /></button>
      </header>

      <div class="analysis-scroll">
        <div class="analysis-main">
          <aside class="analysis-visual" aria-label="原图与分析标记">
            <div v-if="activeImage" class="analysis-photo" :style="{ '--photo-ratio': activeImage.width / activeImage.height }">
              <img :src="activeImage.url" :alt="`${trend.evidence?.author || ''} ${activeImage.label}：${trend.title}`" referrerpolicy="no-referrer" @error="markImageUnavailable">
              <button v-for="(pin, index) in activeImage.pins" :key="pin.point" type="button" class="analysis-pin" :style="{ left: `${pin.x}%`, top: `${pin.y}%` }" :aria-label="`查看分析点 ${index + 1}：${analysis.points.find(point => point.id === pin.point)?.title}`" :aria-pressed="activePointId === pin.point" aria-controls="analysis-point-content" @click="selectPoint(pin.point, true)">{{ index + 1 }}</button>
            </div>
            <div v-else class="analysis-no-image"><strong>原帖配图暂不可用</strong><p>仍可阅读已整理的内容，或前往原帖查看图片。</p></div>
            <div v-if="images.length > 1" class="analysis-image-choices" role="group" aria-label="切换原帖图片"><button v-for="image in images" :key="image.id" type="button" :aria-pressed="activeImage?.id === image.id" @click="activeImageId = image.id">{{ image.label }}</button></div>
            <p class="analysis-caption">{{ activeImage?.label || analysis?.scope }}<template v-if="analysis && activeImage"> · 点击编号，对照具体原因</template></p>
            <p class="analysis-author">{{ trend.evidence?.author || trendPlatformName(trend.platform) }} · {{ dateLabel }}</p>
            <a v-if="sourceUrl" class="analysis-source-link" :href="sourceUrl" target="_blank" rel="noopener noreferrer">查看原帖<ArrowUpRight :size="15" aria-hidden="true" /></a>
          </aside>

          <div class="analysis-reading">
            <p class="analysis-kicker">{{ analysis ? '看懂这一套，再决定怎样借鉴' : '原帖内容' }}</p>
            <h3 class="analysis-look-title">{{ analysis?.title || trend.title }}</h3>
            <p v-if="trend.dailySlot?.category === 'niche'" class="analysis-small-note">{{ trend.dailySlot.reason }}</p>
            <template v-if="analysis">
              <p class="analysis-scope">{{ analysis.scope }} · 以下为基于原图的分析判断</p>
              <div class="analysis-point-choices" role="group" aria-label="选择分析要点">
                <button v-for="(point, index) in analysis.points" :key="point.id" type="button" :aria-pressed="activePointId === point.id" aria-controls="analysis-point-content" @click="selectPoint(point.id)"><span>0{{ index + 1 }}</span>{{ point.title }}</button>
              </div>
              <section v-if="activePoint" id="analysis-point-content" ref="pointContent" class="analysis-point-content" aria-live="polite" aria-atomic="true">
                <h4>{{ activePoint.title }}</h4>
                <dl><div><dt>原图里能看到</dt><dd>{{ activePoint.observation }}</dd></div><div><dt>为什么会这样</dt><dd>{{ activePoint.reason }}</dd></div><div class="analysis-tradeoff"><dt>取舍与条件</dt><dd>{{ activePoint.tradeoff }}</dd></div><div><dt>你可以这样试</dt><dd>{{ activePoint.test }}</dd></div></dl>
              </section>

              <section class="analysis-changes" aria-labelledby="analysis-changes-title">
                <p class="analysis-kicker">一次只改一处</p><h4 id="analysis-changes-title">换掉这一件，会发生什么？</h4>
                <p class="analysis-small-note">以下为替换推演，尚未经过真人对照试穿。</p>
                <div class="analysis-change-choices" role="group" aria-label="选择替换方案"><button v-for="change in analysis.changes" :key="change.id" type="button" :aria-pressed="activeChangeId === change.id" aria-controls="analysis-change-result" @click="activeChangeId = change.id">{{ change.label }}</button></div>
                <dl v-if="activeChange" id="analysis-change-result" class="analysis-change-result" aria-live="polite" aria-atomic="true"><div><dt>保留什么</dt><dd>{{ activeChange.kept }}</dd></div><div><dt>改变什么</dt><dd>{{ activeChange.changed }}</dd></div><div><dt>怎样验证</dt><dd>{{ activeChange.test }}</dd></div></dl>
              </section>
            </template>
            <section v-else class="analysis-pending">
              <p v-if="trend.summary" class="analysis-original-summary">{{ trend.summary }}</p>
              <p v-else>来源未提供摘要，可前往原帖查看具体搭配。</p>
              <div v-if="trend.topicTags?.length" class="analysis-tags"><span v-for="tag in trend.topicTags" :key="tag">{{ tag }}</span></div>
              <h4>暂无这条穿搭的深入解读</h4><p>当前展示来源提供的图片、标题与摘要。你可以先查看原帖，或结合风格标签从自己的衣橱尝试搭配。</p>
            </section>

            <section class="analysis-wardrobe" aria-labelledby="analysis-wardrobe-title">
              <p class="analysis-kicker">从自己的衣服开始</p><h4 id="analysis-wardrobe-title">用我的衣橱尝试</h4>
              <ol v-if="analysis" class="analysis-steps"><li v-for="step in analysis.steps" :key="step">{{ step }}</li></ol>
              <p v-if="wardrobeLoading" class="analysis-small-note" role="status">正在加载衣橱单品…</p>
              <div v-else-if="wardrobeMatches.length" class="analysis-wardrobe-matches"><p>与你选中内容标签相关的衣物，版型与组合仍需确认：</p><article v-for="match in wardrobeMatches" :key="match.item.id"><Check :size="16" aria-hidden="true" /><div><strong>{{ match.item.name || '未命名衣物' }}</strong><span>{{ [match.item.category, match.item.color, match.item.style].filter(Boolean).join(' · ') }}</span><small>相关标签：{{ match.tags.join('、') }}</small></div></article></div>
              <p v-else class="analysis-small-note">{{ wardrobe.length ? '暂未找到与这些标签直接相关的已确认衣物；生成搭配时仍会从你的衣橱选择。' : '衣橱里暂无已确认衣物，添加并确认后就可以尝试搭配。' }}</p>
              <button type="button" class="analysis-wardrobe-link" @click="navigate('open-wardrobe')">查看我的衣橱<ArrowUpRight :size="14" aria-hidden="true" /></button>
            </section>
            <p v-if="analysis" class="analysis-evidence-note">{{ analysis.note }}</p>
          </div>
        </div>
      </div>
      <footer class="analysis-footer"><button type="button" class="analysis-return" @click="emit('close')">返回画廊</button><button type="button" class="analysis-use" @click="navigate('use-trend')"><Shirt :size="17" aria-hidden="true" />用我的衣橱搭一套</button></footer>
    </dialog>
  </Teleport>
</template>

<style scoped>
.trend-analysis-dialog { position:fixed; inset:0; width:min(1240px,calc(100% - 56px)); height:calc(100dvh - 48px); max-width:none; max-height:none; margin:auto; padding:0; border:1px solid var(--line); border-radius:18px; background:var(--surface); color:var(--ink); display:grid; grid-template-rows:auto minmax(0,1fr) auto; box-shadow:0 24px 90px #1b332b33; }
.trend-analysis-dialog:not([open]) { display:none; }
.trend-analysis-dialog::backdrop { background:#172a246e; backdrop-filter:blur(5px); }
.analysis-header { display:flex; align-items:center; justify-content:space-between; gap:20px; padding:18px 26px; border-bottom:1px solid var(--line); }
.analysis-header p { margin:0 0 4px; color:var(--muted); font-size:11px; }
.analysis-header h2 { margin:0; font-family:var(--font-display); font-size:27px; font-weight:500; line-height:1.2; }
.analysis-close { display:grid; place-items:center; width:42px; height:42px; flex-shrink:0; border:1px solid var(--line); border-radius:50%; background:var(--surface); color:var(--ink); cursor:pointer; }
.analysis-scroll { min-height:0; overflow-y:auto; overscroll-behavior:contain; scrollbar-gutter:stable; }
.analysis-main { display:grid; grid-template-columns:minmax(0,.85fr) minmax(0,1.15fr); align-items:start; gap:34px; padding:26px; }
.analysis-visual { position:sticky; top:24px; min-width:0; text-align:center; }
.analysis-photo { position:relative; width:min(100%,calc((100dvh - 290px) * var(--photo-ratio))); margin:0 auto; }
.analysis-photo img { display:block; width:100%; height:auto; border-radius:9px; }
.analysis-pin { position:absolute; display:grid; place-items:center; transform:translate(-50%,-50%); width:32px; height:32px; padding:0; border:2px solid white; border-radius:50%; background:var(--accent); color:white; box-shadow:0 2px 8px #23392d4d; font:inherit; font-size:13px; font-weight:700; cursor:pointer; }
.analysis-pin[aria-pressed="true"] { background:#8a542b; outline:3px solid #ffffff80; }
.analysis-caption,.analysis-author { margin:11px 0 0; color:var(--muted); font-size:11px; line-height:1.7; }
.analysis-author { margin-top:6px; }
.analysis-source-link,.analysis-wardrobe-link { display:inline-flex; align-items:center; justify-content:center; gap:5px; min-height:36px; border:0; padding:8px 0; background:transparent; color:var(--accent); font:inherit; font-size:12px; text-decoration:none; cursor:pointer; }
.analysis-no-image { display:flex; flex-direction:column; justify-content:center; gap:12px; min-height:320px; padding:28px; background:var(--surface-soft); color:var(--muted); font-size:13px; }
.analysis-no-image p { margin:0; line-height:1.8; }
.analysis-reading { min-width:0; }
.analysis-kicker { margin:0 0 9px; color:var(--accent); font-size:12px; font-weight:700; }
.analysis-look-title { margin:0; font-family:var(--font-display); font-size:clamp(24px,2.4vw,33px); font-weight:500; line-height:1.45; overflow-wrap:anywhere; }
.analysis-scope,.analysis-small-note { margin:11px 0; color:var(--muted); font-size:12px; line-height:1.8; }
.analysis-point-choices { display:grid; gap:8px; margin:21px 0 18px; }
.analysis-point-choices button { display:flex; align-items:baseline; gap:12px; width:100%; min-height:43px; padding:10px 13px; border:1px solid var(--line); border-radius:6px; background:var(--surface); color:var(--ink); font:inherit; font-size:13px; text-align:left; cursor:pointer; }
.analysis-point-choices span { color:var(--accent); font-size:11px; }
.analysis-point-choices button[aria-pressed="true"] { border-color:var(--accent); background:var(--surface-soft); }
.analysis-point-content { padding:18px 20px; border-radius:8px; background:var(--surface-soft); scroll-margin-top:18px; }
.analysis-reading h4 { margin:0 0 12px; font-size:16px; line-height:1.65; }
.analysis-reading dl { display:grid; gap:14px; margin:0; }
.analysis-reading dt { margin-bottom:5px; color:var(--accent); font-size:12px; font-weight:700; }
.analysis-reading dd { margin:0; font-size:13px; line-height:1.9; overflow-wrap:anywhere; }
.analysis-tradeoff dd { color:#79552f; }
.analysis-changes,.analysis-wardrobe { margin-top:29px; padding-top:25px; border-top:1px solid var(--line); }
.analysis-change-choices,.analysis-image-choices { display:flex; flex-wrap:wrap; gap:8px; margin:14px 0 16px; }
.analysis-change-choices button,.analysis-image-choices button { min-height:38px; padding:8px 12px; border:1px solid var(--line); border-radius:6px; background:var(--surface); color:var(--ink); font:inherit; font-size:12px; line-height:1.6; cursor:pointer; }
.analysis-change-choices button[aria-pressed="true"],.analysis-image-choices button[aria-pressed="true"] { background:var(--accent); border-color:var(--accent); color:var(--surface); }
.analysis-image-choices { justify-content:center; margin-bottom:9px; }
.analysis-change-result { padding:17px 19px; border:1px solid var(--line); border-radius:7px; }
.analysis-steps { margin:12px 0 17px; padding-left:21px; font-size:13px; line-height:1.9; }
.analysis-steps li+li { margin-top:9px; }
.analysis-wardrobe-matches>p { margin:10px 0 12px; color:var(--muted); font-size:12px; line-height:1.7; }
.analysis-wardrobe-matches article { display:flex; align-items:flex-start; gap:9px; padding:11px 0; border-bottom:1px solid var(--line); }
.analysis-wardrobe-matches article>svg { color:var(--accent); flex-shrink:0; margin-top:4px; }
.analysis-wardrobe-matches article>div { display:grid; gap:4px; min-width:0; overflow-wrap:anywhere; }
.analysis-wardrobe-matches strong { font-size:13px; font-weight:500; }
.analysis-wardrobe-matches span,.analysis-wardrobe-matches small { color:var(--muted); font-size:11px; line-height:1.6; }
.analysis-evidence-note { margin:23px 0 0; padding-top:16px; border-top:1px solid var(--line); color:var(--muted); font-size:11px; line-height:1.8; }
.analysis-pending { margin-top:20px; font-size:13px; line-height:1.9; }
.analysis-pending h4 { margin-top:24px; }
.analysis-original-summary { white-space:pre-line; overflow-wrap:anywhere; }
.analysis-tags { display:flex; flex-wrap:wrap; gap:8px 14px; color:var(--accent); font-size:12px; }
.analysis-footer { display:flex; justify-content:space-between; align-items:center; gap:14px; padding:15px 26px; border-top:1px solid var(--line); background:var(--surface); }
.analysis-footer button { display:inline-flex; align-items:center; justify-content:center; gap:8px; min-height:44px; padding:10px 17px; border:1px solid var(--line); border-radius:7px; font:inherit; font-size:13px; cursor:pointer; }
.analysis-return { background:var(--surface); color:var(--ink); }
.analysis-footer .analysis-use { border-color:var(--accent); background:var(--accent); color:var(--surface); }
button:focus-visible,a:focus-visible { outline:2px solid var(--accent-strong); outline-offset:3px; }
@media(max-width:960px) { .analysis-main { gap:24px; padding:22px; grid-template-columns:minmax(0,.75fr) minmax(0,1.25fr); } }
@media(max-width:760px) {
  .trend-analysis-dialog { width:100%; height:100dvh; border:0; border-radius:0; }
  .analysis-header { padding:15px 18px; }
  .analysis-header h2 { font-size:24px; }
  .analysis-main { grid-template-columns:1fr; gap:24px; padding:20px 18px 28px; }
  .analysis-visual { position:static; }
  .analysis-photo { width:min(100%,360px); }
  .analysis-point-content { padding:17px 16px; }
  .analysis-look-title { font-size:26px; }
  .analysis-footer { padding:13px 16px max(13px,env(safe-area-inset-bottom)); gap:9px; }
  .analysis-footer button { padding:10px 12px; font-size:12px; }
}
</style>
