<script setup>
import { computed, ref } from 'vue'
import { AlertTriangle, ArrowUpRight, Camera, Lightbulb } from '@lucide/vue'
import { collectTrendPieces, pieceCategories, resolveSeason } from '../data/trendPieces.js'
import { canTryOnGarment, trendTryOnItem } from '../utils/liveTryOn.js'

const props = defineProps({
  trends: { type: Array, default: () => [] },
  weather: { type: Object, default: null },
  loading: { type: Boolean, default: false },
  error: { type: String, default: '' }
})
const emit = defineEmits(['open-try-on'])

const category = ref('全部')
const selectedId = ref('')
const unavailableImages = ref(new Set())
const season = computed(() => resolveSeason(props.weather))
const mentionedPieces = computed(() => collectTrendPieces(props.trends, season.value))
const displayPieces = computed(() => props.loading && !props.trends.length ? [] : mentionedPieces.value)
const availableCategories = computed(() => pieceCategories.filter((value) =>
  value === '全部' || displayPieces.value.some((piece) => piece.category === value)
))
const effectiveCategory = computed(() => availableCategories.value.includes(category.value) ? category.value : '全部')
const visiblePieces = computed(() => displayPieces.value.filter((piece) =>
  effectiveCategory.value === '全部' || piece.category === effectiveCategory.value
))
const selectedPiece = computed(() =>
  visiblePieces.value.find((piece) => piece.id === selectedId.value) || visiblePieces.value[0] || null
)
const selectedTryOnItem = computed(() => trendTryOnItem(selectedPiece.value))
const canTryOnSelected = computed(() => canTryOnGarment(selectedTryOnItem.value)
  && !unavailableImages.value.has(selectedPiece.value?.id))

function openTryOn() {
  if (!canTryOnSelected.value) return
  const remaining = visiblePieces.value
    .filter(piece => piece.id !== selectedPiece.value.id && !unavailableImages.value.has(piece.id))
    .map(trendTryOnItem)
    .filter(canTryOnGarment)
  emit('open-try-on', [selectedTryOnItem.value, ...remaining])
}

function markImageUnavailable(id) {
  unavailableImages.value = new Set([...unavailableImages.value, id])
}

function setCategory(value) {
  category.value = value
  selectedId.value = ''
}
</script>

<template>
  <section class="piece-explorer" aria-labelledby="piece-explorer-title">
    <header class="explorer-heading">
      <div>
        <p class="eyebrow">单品</p>
        <h2 id="piece-explorer-title">趋势单品解析</h2>
        <p class="heading-intro">查看趋势提及的单品、组合建议与日常护理说明。单品图片为本地示意图，非原帖配图。</p>
      </div>
      <span class="season-context">{{ season.description }}</span>
    </header>

    <div v-if="loading && !trends.length" class="piece-loading" role="status">
      <span>正在加载单品信息…</span>
      <div class="loading-line loading-line-short" aria-hidden="true"></div>
      <div class="loading-line" aria-hidden="true"></div>
      <div class="loading-line loading-line-medium" aria-hidden="true"></div>
    </div>
    <template v-else>
      <nav class="category-nav" aria-label="按单品类型筛选">
        <button
          v-for="value in availableCategories"
          :key="value"
          type="button"
          :aria-pressed="effectiveCategory === value"
          @click="setCategory(value)"
        >{{ value }}</button>
      </nav>

      <p v-if="!mentionedPieces.length" class="piece-empty" role="status">
        {{ error ? '单品列表暂时无法更新。' : '暂无符合条件的单品。' }}
      </p>

      <p v-if="visiblePieces.length > 2" class="rail-hint">左右滑动查看更多单品 →</p>
      <div class="piece-rail" role="group" aria-label="选择要查看的单品">
        <button
          v-for="piece in visiblePieces"
          :key="piece.id"
          type="button"
          class="piece-choice"
          :class="{ selected: selectedPiece?.id === piece.id }"
          :aria-pressed="selectedPiece?.id === piece.id"
          aria-controls="piece-detail"
          @click="selectedId = piece.id"
        >
          <span class="choice-image">
            <img v-if="piece.image && !unavailableImages.has(piece.id)" :src="piece.image" alt="" loading="lazy" decoding="async" @error="markImageUnavailable(piece.id)" />
            <span v-else class="image-fallback">暂无单品图</span>
          </span>
          <span class="choice-copy">
            <span class="choice-type">本地示意图 · </span><span class="choice-type">{{ piece.category }} · 趋势提及 {{ piece.mentions.length }} 条</span>
            <strong>{{ piece.name }}</strong>
            <span v-if="piece.seasonStatus === '不建议穿'" class="choice-season-warning">{{ season.label }}不建议</span>
            <span class="choice-footer">查看搭配 <ArrowUpRight :size="15" aria-hidden="true" /></span>
          </span>
        </button>
      </div>

      <Transition name="piece-swap" mode="out-in">
        <article v-if="selectedPiece" id="piece-detail" :key="selectedPiece.id" class="piece-detail">
          <div class="feature-grid">
            <header class="piece-identity">
              <div class="identity-topline">
                <span>单品解析 / {{ selectedPiece.category }}</span>
                <span>{{ selectedPiece.seasonStatus }}</span>
              </div>
              <figure class="identity-photo">
                <img
                  v-if="selectedPiece.image && !unavailableImages.has(selectedPiece.id)"
                  :src="selectedPiece.image"
                  :alt="`${selectedPiece.name}单品图`"
                  decoding="async"
                  @error="markImageUnavailable(selectedPiece.id)"
                />
                <span v-else class="image-fallback">暂无单品图</span>
              </figure>
              <div class="identity-copy">
                <p>本地单品示意图 · 非原帖配图</p>
                <h3 aria-live="polite">{{ selectedPiece.name }}</h3>
                <p>{{ selectedPiece.description }}</p>
              </div>
              <div class="piece-try-on">
                <button type="button" :disabled="!canTryOnSelected" @click="openTryOn">
                  <Camera :size="17" aria-hidden="true" />实时试穿
                </button>
                <p v-if="canTryOnSelected">用摄像头查看这款示意图的上身效果，参考款式与配色。</p>
                <p v-else>{{ !selectedPiece.image || unavailableImages.has(selectedPiece.id)
                  ? '暂无可用单品图，暂时无法试穿。' : '当前实时试穿暂不支持此单品类型。' }}</p>
              </div>
            </header>

            <section class="pairing-section" :aria-labelledby="`pairing-${selectedPiece.id}`">
              <div class="pairing-heading">
                <div>
                  <p class="section-kicker">搭配建议</p>
                  <h4 :id="`pairing-${selectedPiece.id}`">单品组合建议</h4>
                </div>
              </div>
              <div v-if="selectedPiece.pairings.length" class="pairing-list">
                <div v-for="pairing in selectedPiece.pairings" :key="pairing.type" class="pairing-row">
                  <span class="pairing-type">{{ pairing.type }}</span>
                  <div>
                    <strong>{{ pairing.items }}</strong>
                    <p>{{ pairing.reason }}</p>
                    <div v-if="pairing.tones?.length" class="pairing-tones" aria-label="建议配色">
                      <span v-for="tone in pairing.tones" :key="tone.name" class="tone-label">
                        <i class="tone-swatch" :class="`tone-${tone.key}`" aria-hidden="true"></i>{{ tone.name }}
                      </span>
                    </div>
                  </div>
                </div>
              </div>
              <p v-if="selectedPiece.seasonNote" class="season-note" :class="{ 'season-note-warning': selectedPiece.seasonStatus === '不建议穿' }">
                {{ selectedPiece.seasonNote }}
              </p>
            </section>
          </div>

          <div class="practical-grid">
            <section class="care-section" :aria-labelledby="`care-${selectedPiece.id}`">
              <div class="practical-heading">
                <span class="section-kicker">日常护理</span>
                <h4 :id="`care-${selectedPiece.id}`">清洁与收纳</h4>
              </div>
              <ul><li v-for="line in selectedPiece.care" :key="line">{{ line }}</li></ul>
              <p class="care-label">具体护理方式请遵循实物洗标及品牌说明。</p>
            </section>
            <div class="practical-notes">
              <section class="warning-note" :aria-labelledby="`warning-${selectedPiece.id}`">
                <h4 :id="`warning-${selectedPiece.id}`"><AlertTriangle :size="17" aria-hidden="true" />穿着提醒</h4>
                <p>{{ selectedPiece.warning }}</p>
              </section>
              <section class="tip-note" :aria-labelledby="`tip-${selectedPiece.id}`">
                <div class="tip-heading">
                  <h4 :id="`tip-${selectedPiece.id}`"><Lightbulb :size="17" aria-hidden="true" />穿搭技巧</h4>
                </div>
                <p>{{ selectedPiece.tip }}</p>
              </section>
            </div>
          </div>
        </article>
      </Transition>
    </template>
  </section>
</template>

<style scoped>
.piece-explorer { margin-bottom:70px; border-top:1px solid var(--line); padding-top:43px; color:var(--ink); scroll-margin-top:96px; }
.explorer-heading { display:flex; max-width:none; align-items:flex-end; justify-content:space-between; gap:20px; }
.season-context { flex:0 0 auto; color:var(--muted); font-size:12px; white-space:nowrap; }
.eyebrow,.section-kicker { margin:0; color:var(--accent); font-size:12px; font-weight:600; letter-spacing:0; }
.eyebrow { margin-bottom:11px; }
.explorer-heading h2 { margin:0; font-family:var(--font-display); font-size:clamp(31px,3.3vw,44px); font-weight:600; letter-spacing:-.035em; line-height:1.2; }
.heading-intro { margin:11px 0 0; color:var(--muted); font-size:14px; line-height:1.7; text-wrap:pretty; }
.piece-loading { display:grid; gap:12px; padding:31px 0; color:var(--muted); }
.loading-line { height:22px; border-radius:3px; background:linear-gradient(90deg,var(--surface-soft),var(--surface),var(--surface-soft)); background-size:200% 100%; animation:loading-shimmer 1.6s linear infinite; }
.loading-line-short { width:30%; }
.loading-line-medium { width:66%; }
@keyframes loading-shimmer { to { background-position-x:-200%; } }
.category-nav { display:flex; gap:29px; margin-top:27px; overflow-x:auto; border-bottom:1px solid var(--line); scrollbar-width:thin; }
.category-nav button { min-height:46px; flex:0 0 auto; border:0; border-bottom:2px solid transparent; padding:0 2px 10px; color:var(--muted); background:transparent; font-size:13px; font-weight:700; }
.category-nav button:hover,.category-nav button:focus-visible { color:var(--accent); }
.category-nav button[aria-pressed="true"] { border-bottom-color:var(--accent); color:var(--accent); }
.piece-empty { margin:16px 0 0; border-left:3px solid var(--gold); padding:2px 0 2px 12px; color:var(--muted); font-size:12px; line-height:1.6; }
.rail-hint { display:none; }
.piece-rail { display:flex; width:fit-content; max-width:100%; overflow-x:auto; margin:20px 0 17px; border:1px solid var(--line); background:var(--surface); scroll-snap-type:x proximity; scrollbar-width:thin; }
.piece-choice { display:grid; grid-template-columns:82px minmax(0,1fr); min-width:258px; max-width:280px; min-height:108px; flex:0 0 258px; align-items:center; gap:13px; border:0; border-right:1px solid var(--line); border-top:3px solid transparent; padding:9px 13px; background:transparent; text-align:left; scroll-snap-align:start; }
.piece-choice:last-child { border-right:0; }
.piece-choice:hover { background:#f3f4ed; }
.piece-choice.selected { border-top-color:var(--accent); background:var(--accent-soft); }
.choice-image { display:block; width:82px; height:82px; overflow:hidden; background:#f5f2eb; }
.choice-image img { width:100%; height:100%; object-fit:cover; }
.choice-copy { display:flex; min-width:0; flex-direction:column; align-items:start; gap:5px; }
.choice-type { color:var(--muted); font-size:10px; font-weight:600; }
.piece-choice strong { font-family:var(--font-display); font-size:20px; font-weight:600; line-height:1.2; }
.choice-season-warning { color:var(--danger); font-size:10px; font-weight:700; }
.choice-footer { display:flex; align-items:center; gap:5px; color:var(--muted); font-size:11px; line-height:1.4; }
.piece-choice.selected .choice-footer,.piece-choice.selected .choice-type { color:var(--accent); }
.choice-footer svg { flex:0 0 auto; }
.image-fallback { display:grid; width:100%; height:100%; place-items:center; color:var(--muted); font-size:11px; }
.piece-detail { min-width:0; overflow:hidden; border:1px solid var(--line); background:var(--surface); }
.feature-grid { display:grid; grid-template-columns:minmax(280px,.78fr) minmax(0,1.22fr); }
.piece-identity { min-width:0; padding:26px 31px 30px; background:#e8ede5; }
.identity-topline { display:flex; justify-content:space-between; gap:12px; color:var(--accent); font-size:11px; font-weight:700; }
.identity-photo { position:relative; display:grid; height:clamp(210px,24vw,290px); overflow:hidden; margin:18px 0 20px; border:1px solid rgba(23,79,66,.1); background:#f5f2eb; }
.identity-photo img { position:absolute; inset:0; width:100%; height:100%; object-fit:contain; }
.season-note { margin:18px 0 0; border-left:2px solid var(--gold); padding:3px 0 3px 11px; color:var(--muted); font-size:12px; line-height:1.7; }
.season-note-warning { border-left-color:var(--danger); color:var(--danger); }
.identity-copy h3 { margin:0 0 10px; font-family:var(--font-display); font-size:clamp(36px,3.7vw,51px); font-weight:500; letter-spacing:-.055em; line-height:1.12; text-wrap:balance; }
.identity-copy p { max-width:36ch; margin:0; color:#43574e; font-size:14px; line-height:1.8; text-wrap:pretty; }
.piece-try-on { display:grid; gap:9px; margin-top:22px; }
.piece-try-on button { display:inline-flex; width:fit-content; min-height:44px; align-items:center; justify-content:center; gap:8px; border:0; border-radius:var(--radius); padding:11px 18px; color:var(--surface); background:var(--accent-strong); font:inherit; font-size:13px; font-weight:600; cursor:pointer; }
.piece-try-on button:disabled { opacity:.55; cursor:default; }
.piece-try-on button:focus-visible { outline:2px solid var(--accent); outline-offset:3px; }
.piece-try-on p { max-width:40ch; margin:0; color:#43574e; font-size:12px; line-height:1.7; }
.pairing-section { min-width:0; padding:29px 35px 30px; }
.pairing-heading { display:flex; align-items:end; justify-content:space-between; gap:14px; margin-bottom:12px; }
.pairing-heading h4,.practical-heading h4 { margin:5px 0 0; font-family:var(--font-display); font-size:26px; font-weight:600; letter-spacing:-.025em; line-height:1.3; }
.pairing-heading > span { color:var(--muted); font-size:11px; white-space:nowrap; }
.pairing-row { display:grid; grid-template-columns:74px minmax(0,1fr); gap:12px; border-top:1px solid var(--line); padding:19px 0; }
.pairing-row:last-child { padding-bottom:0; }
.pairing-type { width:fit-content; height:fit-content; border-left:2px solid var(--gold); padding:2px 0 2px 9px; color:var(--accent); font-size:12px; font-weight:800; white-space:nowrap; }
.pairing-row strong { display:block; max-width:49ch; font-size:15px; font-weight:700; line-height:1.6; text-wrap:pretty; }
.pairing-row p { max-width:62ch; margin:5px 0 0; color:var(--muted); font-size:12px; line-height:1.6; }
.pairing-tones { display:flex; flex-wrap:wrap; gap:8px 14px; margin-top:11px; }
.tone-label { display:inline-flex; align-items:center; gap:5px; color:var(--muted); font-size:11px; white-space:nowrap; }
.tone-swatch { display:inline-block; width:12px; height:12px; border:1px solid rgba(31,46,40,.2); border-radius:50%; }
.tone-white { background:#f6f5f0; }
.tone-gray { background:#c6c9c7; }
.tone-cream { background:#e9dfc9; }
.tone-blue { background:#b6cbda; }
.tone-black { background:#25282a; }
.tone-brown { background:#51372e; }
.practical-grid { display:grid; grid-template-columns:minmax(0,1.12fr) minmax(0,.88fr); border-top:1px solid var(--line); }
.care-section { padding:27px 31px 30px; background:#f4f5ee; }
.care-section ul { display:grid; gap:10px; margin:17px 0 0; padding-left:18px; }
.care-section li { max-width:65ch; font-size:13px; line-height:1.75; }
.care-section li::marker { color:var(--accent); }
.care-label { margin:17px 0 0; color:var(--muted); font-size:11px; }
.practical-notes { display:grid; grid-template-rows:auto 1fr; }
.warning-note,.tip-note { padding:23px 29px 24px; }
.warning-note { border-bottom:1px solid var(--line); background:#f8f2ed; }
.tip-note { background:#f0f3ec; }
.warning-note h4,.tip-note h4 { display:flex; align-items:center; gap:8px; margin:0; font-family:var(--font-display); font-size:19px; font-weight:600; }
.warning-note h4 svg { color:var(--danger); }
.tip-note h4 svg { color:var(--accent); }
.warning-note p,.tip-note p { max-width:55ch; margin:10px 0 0; font-size:12px; line-height:1.75; text-wrap:pretty; }
.piece-swap-enter-active,.piece-swap-leave-active { transition:opacity 160ms ease,transform 160ms ease; }
.piece-swap-enter-from { opacity:0; transform:translateY(6px); }
.piece-swap-leave-to { opacity:0; transform:translateY(-4px); }
@media (max-width:930px) {
  .feature-grid { grid-template-columns:minmax(255px,.85fr) minmax(0,1.15fr); }
  .identity-photo { height:230px; }
  .pairing-section { padding-inline:25px; }
}
@media (max-width:760px) {
  .piece-explorer { padding-top:32px; scroll-margin-top:142px; }
  .explorer-heading { display:block; }
  .season-context { display:inline-block; margin-top:13px; white-space:normal; }
  .category-nav { gap:24px; margin-top:19px; }
  .rail-hint { display:block; margin:12px 0 0; color:var(--muted); font-size:11px; text-align:right; }
  .piece-rail { margin-top:8px; }
  .piece-choice { grid-template-columns:72px minmax(0,1fr); min-width:223px; min-height:96px; flex-basis:223px; gap:10px; padding-inline:10px; }
  .choice-image { width:72px; height:72px; }
  .piece-choice strong { font-size:18px; }
  .feature-grid,.practical-grid { grid-template-columns:minmax(0,1fr); }
  .piece-identity { padding:22px 24px 25px; }
  .identity-photo { height:230px; margin:15px 0 18px; }
  .identity-copy h3 { font-size:38px; }
  .pairing-section { padding:24px 24px 23px; }
  .pairing-heading h4,.practical-heading h4 { font-size:23px; }
  .pairing-row { grid-template-columns:57px minmax(0,1fr); gap:10px; padding:17px 0; }
  .pairing-row strong { font-size:14px; }
  .care-section { padding:23px 24px 25px; }
  .warning-note,.tip-note { padding:21px 24px 22px; }
}
@media (prefers-reduced-motion:reduce) {
  .loading-line { animation:none; }
  .piece-swap-enter-active,.piece-swap-leave-active { transition:none; }
}
</style>
