<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { Check, Heart, LoaderCircle } from '@lucide/vue'
import { collectTrendPieces, resolveSeason } from '../data/trendPieces.js'
import { canTryOnGarment, trendTryOnItem } from '../utils/liveTryOn.js'
import { externalTrendSourceUrl } from '../utils/trendSources.js'

const props = defineProps({ app: { type: Object, required: true }, trend: { type: Object, required: true } })
const pieces = computed(() => collectTrendPieces([props.trend], resolveSeason(props.app.state.weather))
  .map(piece => ({ ...trendTryOnItem(piece), sourceUrl: externalTrendSourceUrl(props.trend) || '' }))
  .filter(canTryOnGarment))
const savingId = ref(null)
const savedIds = ref(new Set())
const unavailableIds = ref(new Set())
const error = ref('')
const notice = ref('')
let version = 0
let active = true
watch(() => [props.trend.id, props.app.state.authUser?.username], () => {
  version += 1
  savingId.value = null
  savedIds.value = new Set()
  unavailableIds.value = new Set()
  error.value = ''
  notice.value = ''
})
onBeforeUnmount(() => { active = false })

function isSaved(piece) {
  return savedIds.value.has(piece.id) || (props.app.state.tryOnFavorites || []).some(item =>
    item.sourceKind === 'TREND_ILLUSTRATION' && item.name === piece.name && item.category === piece.category
    && (item.sourceUrl || '') === piece.sourceUrl)
}

async function save(piece) {
  if (savingId.value || isSaved(piece) || unavailableIds.value.has(piece.id)) return
  const currentVersion = version
  savingId.value = piece.id
  error.value = ''
  notice.value = ''
  try {
    const saved = await props.app.saveTryOnFavorite(piece)
    if (!active || currentVersion !== version) return
    if (!saved) throw new Error('收藏未完成，请稍后重试。')
    savedIds.value = new Set([...savedIds.value, piece.id])
    notice.value = `${piece.name}示意图已保存到“喜欢的穿搭”。`
  } catch (cause) {
    if (active && currentVersion === version) error.value = cause?.message || '收藏未完成，请稍后重试。'
  } finally {
    if (active && currentVersion === version) savingId.value = null
  }
}
</script>

<template>
  <section v-if="pieces.length" class="trend-piece-favorites" aria-labelledby="trend-piece-favorites-title">
    <header><div><h2 id="trend-piece-favorites-title">收藏款式参考</h2><p>款式示意 · 非原帖商品图</p></div></header>
    <div class="piece-grid">
      <article v-for="piece in pieces" :key="piece.id" class="piece-card">
        <img v-if="!unavailableIds.has(piece.id)" :src="piece.imageUrl" :alt="`${piece.name}款式示意图`" loading="lazy" @error="unavailableIds = new Set([...unavailableIds, piece.id])" />
        <span v-else class="piece-image-missing">示意图暂不可用</span>
        <div><strong>{{ piece.name }}</strong><small>{{ piece.category }} · 款式示意</small><button type="button" :disabled="Boolean(savingId) || isSaved(piece) || unavailableIds.has(piece.id)" :aria-label="`${isSaved(piece) ? '已收藏' : '收藏'}${piece.name}示意图`" @click="save(piece)"><LoaderCircle v-if="savingId === piece.id" class="spinning" :size="15" aria-hidden="true" /><Check v-else-if="isSaved(piece)" :size="15" aria-hidden="true" /><Heart v-else :size="15" aria-hidden="true" />{{ savingId === piece.id ? '收藏中…' : isSaved(piece) ? '已收藏' : '收藏示意图' }}</button></div>
      </article>
    </div>
    <p v-if="error" class="piece-error" role="alert">{{ error }}</p>
    <div v-if="notice" class="piece-notice" role="status"><span>{{ notice }}</span><button type="button" @click="app.selectView('favorites')">查看喜欢的穿搭</button></div>
  </section>
</template>

<style scoped>
.trend-piece-favorites { margin-top:24px; border:1px solid var(--line); border-radius:12px; padding:var(--trend-panel-padding,24px); background:var(--surface); }
header h2 { margin:0; color:var(--ink); font-family:var(--font-display); font-size:21px; font-weight:600; }
header p { max-width:750px; margin:10px 0 18px; color:var(--muted); font-size:12px; line-height:1.8; }
.piece-grid { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:14px; }
.piece-card { display:flex; gap:14px; min-width:0; border:1px solid var(--line); border-radius:8px; padding:12px; }
.piece-card img,.piece-image-missing { width:90px; height:112px; flex:0 0 90px; object-fit:contain; border-radius:5px; background:var(--surface-soft); }
.piece-image-missing { display:grid; place-content:center; padding:5px; color:var(--muted); font-size:11px; text-align:center; }
.piece-card>div { display:flex; min-width:0; flex-direction:column; align-items:flex-start; justify-content:center; gap:7px; }
.piece-card strong { font-size:14px; overflow-wrap:anywhere; }
.piece-card small { color:var(--muted); font-size:11px; }
.piece-card button,.piece-notice button { display:inline-flex; align-items:center; justify-content:center; gap:5px; min-height:38px; border:1px solid var(--line); border-radius:6px; padding:7px 9px; background:var(--surface); color:var(--accent); font:inherit; font-size:12px; cursor:pointer; }
.piece-card button:disabled { cursor:default; opacity:.65; }
.piece-notice { display:flex; flex-wrap:wrap; align-items:center; gap:12px; margin:16px 0 0; color:var(--accent); font-size:12px; }
.piece-error { margin:14px 0 0; color:var(--danger, #a33b32); font-size:12px; line-height:1.7; }
button:focus-visible { outline:2px solid var(--accent); outline-offset:3px; }
.spinning { animation:piece-spin 1s linear infinite; }
@keyframes piece-spin { to { transform:rotate(360deg); } }
@media(max-width:1100px) { .piece-grid { grid-template-columns:repeat(2,minmax(0,1fr)); } }
@media(max-width:700px) { .piece-grid { grid-template-columns:1fr; } }
@media(prefers-reduced-motion:reduce) { .spinning { animation:none; } }
</style>
