<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { verifiedFullBodyImageUrl } from '../utils/trendGallery.js'
import { trendImageCandidates, trendDisplayImageUrl } from '../utils/trendImage.js'
const AUTOPLAY_INTERVAL_MS = 2000
const GALLERY_POOL_SIZE = 10
const fallbackTrendImages = [
  { src: '/assets/look-urban.jpg', alt: '城市日常穿搭参考' },
  { src: '/assets/look-tailoring.jpg', alt: '利落通勤穿搭参考' },
  { src: '/assets/look-color.jpg', alt: '简洁配色穿搭参考' }
]

const props = defineProps({
  trends: { type: Array, default: () => [] },
  selectedId: { type: [String, Number], default: null }
})

const emit = defineEmits(['select'])
const gallerySection = ref(null)
const galleryStage = ref(null)
const galleryCardWidth = ref(360)
const galleryShift = ref(400)
const galleryIndex = ref(0)
const galleryReady = ref(false)
const galleryVisibleCount = ref(5)
const unavailableImageUrls = ref(new Set())
let resizeObserver = null
let autoplayTimer = null
let motionPreference = null
let galleryMounted = false
let galleryKeyboardFocused = false
let galleryReducedMotion = false

const galleryImages = computed(() => {
  const seen = new Set()
  return props.trends.flatMap((item) => {
    const verifiedUrl = verifiedFullBodyImageUrl(item)
    if (!verifiedUrl) return []
    const url = trendImageCandidates(verifiedUrl).map(trendDisplayImageUrl)
      .find((candidate) => !unavailableImageUrls.value.has(candidate))
    if (!url || seen.has(url)) return []
    seen.add(url)
    return [{ item, url, key: String(item.id) }]
  }).slice(0, GALLERY_POOL_SIZE)
})

const selectedIndex = computed(() => {
  const index = galleryImages.value.findIndex(({ item }) => item.id === props.selectedId)
  return index >= 0 ? index : 0
})
const visibleCards = computed(() => {
  const total = galleryImages.value.length
  if (!total) return []
  const count = Math.min(galleryVisibleCount.value, total)
  const leadingCount = Math.floor((count - 1) / 2)

  return Array.from({ length: count }, (_, index) => index - leadingCount).map((offset) => {
    const index = (galleryIndex.value + offset + total) % total
    return { ...galleryImages.value[index], index, offset }
  })
})

function markImageUnavailable(event) {
  const url = event.currentTarget.getAttribute('src') || event.currentTarget.currentSrc
  if (!url || unavailableImageUrls.value.has(url)) return
  unavailableImageUrls.value = new Set([...unavailableImageUrls.value, url])
}

function updateGalleryShift() {
  const stageWidth = galleryStage.value?.getBoundingClientRect().width
    || gallerySection.value?.getBoundingClientRect().width
    || window.innerWidth
  const mobileLayout = window.innerWidth <= 760
  const cardWidth = mobileLayout
    ? Math.min(300, stageWidth * 0.74)
    : Math.min(360, Math.max(300, stageWidth * 0.26))
  const cardGap = mobileLayout
    ? 32
    : Math.min(48, Math.max(32, stageWidth * 0.028))
  galleryCardWidth.value = Math.round(cardWidth)
  galleryShift.value = Math.round(cardWidth + cardGap)
  galleryVisibleCount.value = window.innerWidth <= 1100 ? 3 : 5

  if (galleryImages.value.length && !visibleCards.value.some(({ index }) => index === selectedIndex.value)) {
    galleryIndex.value = selectedIndex.value
  }
}

function cardStyle(offset) {
  const distance = Math.abs(offset)
  const positionOffset = distance <= 1
    ? offset
    : Math.sign(offset) * (1 + (distance - 1) * 0.8)
  const scale = Math.max(0.82, 1 - distance * 0.09)
  const edgeShift = Math.sign(offset || 1) * 76
  return {
    '--gallery-x': `${positionOffset * galleryShift.value}px`,
    '--gallery-y': `${distance * 8}px`,
    '--gallery-rotation': `${offset * -7}deg`,
    '--gallery-scale': scale,
    '--gallery-transition-scale': Math.max(0.76, scale - 0.06),
    '--gallery-edge-shift': `${edgeShift}px`,
    '--gallery-opacity': distance > 1 ? 0.68 : 1,
    zIndex: 20 - distance
  }
}

function selectTrend(entry) {
  const item = entry?.item
  if (item?.id == null) return
  galleryIndex.value = entry.index
  emit('select', item)
  startGalleryAutoplay()
}

function focusCenterCard() {
  nextTick(() => {
    galleryStage.value
      ?.querySelector(`.gallery-card[data-gallery-index="${galleryIndex.value}"]`)
      ?.focus({ preventScroll: true })
  })
}

function stopGalleryAutoplay() {
  if (autoplayTimer === null) return
  window.clearInterval(autoplayTimer)
  autoplayTimer = null
}

function startGalleryAutoplay() {
  stopGalleryAutoplay()
  if (
    !galleryMounted
    || !galleryReady.value
    || galleryKeyboardFocused
    || galleryReducedMotion
    || document.hidden
    || galleryImages.value.length < 2
  ) return

  autoplayTimer = window.setInterval(() => {
    const total = galleryImages.value.length
    if (total < 2) {
      stopGalleryAutoplay()
      return
    }
    galleryIndex.value = (galleryIndex.value + 1) % total
  }, AUTOPLAY_INTERVAL_MS)
}

function handleGalleryFocusin(event) {
  if (!event.target?.matches?.(':focus-visible')) return
  galleryKeyboardFocused = true
  stopGalleryAutoplay()
}

function handleGalleryFocusout(event) {
  if (event.currentTarget.contains(event.relatedTarget)) return
  galleryKeyboardFocused = false
  startGalleryAutoplay()
}

function handleGalleryMotionPreference(event) {
  galleryReducedMotion = event.matches
  startGalleryAutoplay()
}

function handleGalleryVisibility() {
  if (document.hidden) stopGalleryAutoplay()
  else startGalleryAutoplay()
}

function handleKeydown(event) {
  if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return
  const total = galleryImages.value.length
  if (total < 2) return
  event.preventDefault()

  let nextIndex = selectedIndex.value
  if (event.key === 'ArrowLeft') nextIndex = (selectedIndex.value - 1 + total) % total
  if (event.key === 'ArrowRight') nextIndex = (selectedIndex.value + 1) % total
  if (event.key === 'Home') nextIndex = 0
  if (event.key === 'End') nextIndex = total - 1
  selectTrend({ ...galleryImages.value[nextIndex], index: nextIndex })
  focusCenterCard()
}

onMounted(() => {
  galleryMounted = true
  updateGalleryShift()
  window.addEventListener('resize', updateGalleryShift)
  if (typeof ResizeObserver !== 'undefined' && gallerySection.value) {
    resizeObserver = new ResizeObserver(updateGalleryShift)
    resizeObserver.observe(gallerySection.value)
  }
  motionPreference = window.matchMedia('(prefers-reduced-motion: reduce)')
  galleryReducedMotion = motionPreference.matches
  motionPreference.addEventListener?.('change', handleGalleryMotionPreference)
  document.addEventListener('visibilitychange', handleGalleryVisibility)
  startGalleryAutoplay()
})

watch(() => JSON.stringify(galleryImages.value.map(({ key, url }) => [key, url])), async () => {
  galleryReady.value = false
  await nextTick()
  galleryIndex.value = selectedIndex.value
  updateGalleryShift()
  galleryReady.value = true
  startGalleryAutoplay()
}, { immediate: true })

onBeforeUnmount(() => {
  galleryMounted = false
  stopGalleryAutoplay()
  window.removeEventListener('resize', updateGalleryShift)
  resizeObserver?.disconnect()
  motionPreference?.removeEventListener?.('change', handleGalleryMotionPreference)
  document.removeEventListener('visibilitychange', handleGalleryVisibility)
})
</script>

<template>
  <section ref="gallerySection" class="trend-gallery" aria-labelledby="trend-gallery-title">
    <header class="gallery-heading">
      <div>
        <h2 id="trend-gallery-title">趋势穿搭精选</h2>
        <p v-if="galleryImages.length">{{ galleryImages.length }} 张可用趋势图片</p>
        <p v-else-if="galleryReady && props.trends.length">趋势图片暂不可用</p>
        <p v-else>图片待加载</p>
      </div>
    </header>

    <div
      v-if="galleryImages.length && galleryReady"
      ref="galleryStage"
      class="gallery-stage"
      :style="{ '--gallery-card-width': `${galleryCardWidth}px` }"
      role="region"
      aria-label="趋势穿搭画廊"
      aria-roledescription="3D 画廊"
      aria-keyshortcuts="ArrowLeft ArrowRight Home End"
      @keydown="handleKeydown"
      @focusin="handleGalleryFocusin"
      @focusout="handleGalleryFocusout"
    >
      <TransitionGroup name="gallery-card">
        <button
          v-for="{ item, url, key, index, offset } in visibleCards"
          :key="key"
          type="button"
          class="gallery-card"
          :class="{
            active: selectedId === item.id,
            focal: offset === 0,
            edge: Math.abs(offset) > 1
          }"
          :style="cardStyle(offset)"
          :data-gallery-index="index"
          :data-gallery-center="offset === 0 ? 'true' : undefined"
          :data-gallery-edge="Math.abs(offset) > 1 ? 'true' : undefined"
          :aria-label="`查看第 ${index + 1} 张图片：${item.title}`"
          :aria-current="selectedId === item.id ? 'true' : undefined"
          :aria-pressed="selectedId === item.id"
          :aria-posinset="index + 1"
          :aria-setsize="galleryImages.length"
          :tabindex="0"
          @click="selectTrend({ item, index })"
        >
          <span class="gallery-card-image">
            <img
              :src="url"
              :alt="item.title || '趋势穿搭图片'"
              loading="lazy"
              referrerpolicy="no-referrer"
              @error="markImageUnavailable"
            />
          </span>
        </button>
      </TransitionGroup>
    </div>
    <div v-else-if="galleryReady && props.trends.length && !galleryImages.length" class="gallery-empty gallery-empty-reference" role="status">
      <div class="gallery-empty-copy">
        <strong>趋势图片暂时无法显示</strong>
        <span>以下为本地穿搭参考图片，非实时趋势内容。</span>
      </div>
      <div class="gallery-empty-images" aria-label="本地穿搭参考">
        <figure v-for="image in fallbackTrendImages" :key="image.src">
          <img :src="image.src" :alt="image.alt" />
          <figcaption>本地参考</figcaption>
        </figure>
      </div>
    </div>
    <div v-else-if="galleryReady && !galleryImages.length" class="gallery-empty" role="status">暂无可展示的趋势图片</div>
    <div v-else class="gallery-measuring" role="status">正在加载趋势画廊…</div>
  </section>
</template>

<style scoped>
.trend-gallery {
  padding: 14px 0 48px;
}

.gallery-heading {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 24px;
}

.gallery-heading h2 {
  margin: 0;
  font-family: var(--font-display);
  font-size: clamp(26px, 3.1vw, 36px);
  font-weight: 500;
  line-height: 1.2;
}

.gallery-heading p {
  margin: 10px 0 0;
  color: var(--muted);
  font-size: 13px;
  line-height: 1.6;
}

.gallery-stage {
  position: relative;
  height: clamp(500px, 40vw, 600px);
  margin-top: 24px;
  overflow: hidden;
  isolation: isolate;
  perspective: 1800px;
  -webkit-mask-image: linear-gradient(90deg, transparent 0, #000 4%, #000 96%, transparent 100%);
  mask-image: linear-gradient(90deg, transparent 0, #000 4%, #000 96%, transparent 100%);
}

.gallery-card {
  position: absolute;
  top: 51%;
  left: 50%;
  display: block;
  width: var(--gallery-card-width, 360px);
  aspect-ratio: 3 / 4;
  overflow: visible;
  border: 0;
  border-radius: 2px;
  padding: 0;
  color: inherit;
  background: transparent;
  cursor: pointer;
  filter: saturate(.92);
  opacity: var(--gallery-opacity, 1);
  transform: translate3d(calc(-50% + var(--gallery-x)), calc(-50% + var(--gallery-y)), 0) rotateY(var(--gallery-rotation)) scale(var(--gallery-scale));
  transform-origin: center 70%;
  transform-style: preserve-3d;
  transition: transform 720ms cubic-bezier(.22, .75, .2, 1), opacity 420ms ease, filter 260ms ease;
}

.gallery-card-enter-active,
.gallery-card-leave-active {
  pointer-events: none;
  transition: transform 620ms cubic-bezier(.22, .75, .2, 1), opacity 420ms ease, filter 420ms ease;
}

.gallery-card-leave-active {
  position: absolute;
}

.gallery-card-enter-from,
.gallery-card-leave-to {
  opacity: 0;
  filter: saturate(.76) blur(1px);
  transform: translate3d(calc(-50% + var(--gallery-x) + var(--gallery-edge-shift)), calc(-50% + var(--gallery-y)), 0) rotateY(var(--gallery-rotation)) scale(var(--gallery-transition-scale));
}

.gallery-card:active:not(:disabled) {
  transform: translate3d(calc(-50% + var(--gallery-x)), calc(-50% + var(--gallery-y)), 0) rotateY(var(--gallery-rotation)) scale(var(--gallery-scale));
}

.gallery-card:hover,
.gallery-card:focus-visible,
.gallery-card.active {
  filter: saturate(1.04);
  outline: 0;
}

.gallery-card.active {
  filter: saturate(1.08) brightness(1.04);
}

.gallery-card.focal {
  filter: saturate(1.08) brightness(1.035);
}

.gallery-card:focus-visible .gallery-card-image {
  outline: 2px solid var(--accent);
  outline-offset: 3px;
}

.gallery-card-image {
  position: absolute;
  inset: 0;
  display: block;
  overflow: hidden;
  border: 1px solid rgba(82, 92, 91, .16);
  border-radius: inherit;
  background: var(--surface-soft, #dfe5e7);
  box-shadow: 0 16px 28px rgba(41, 55, 63, .13);
}

.gallery-card.active .gallery-card-image {
  border-color: rgba(54, 64, 62, .38);
  box-shadow: 0 20px 34px rgba(41, 55, 63, .2);
}

.gallery-card.focal .gallery-card-image {
  border-color: rgba(23, 79, 66, .46);
  box-shadow: 0 26px 44px rgba(41, 55, 63, .23);
}

.gallery-card-image img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.gallery-empty {
  display: grid;
  min-height: 260px;
  place-items: center;
  margin-top: 18px;
  border-radius: 14px;
  color: var(--muted);
  background: var(--surface-soft);
  font-size: 14px;
}

.gallery-empty-reference {
  align-content: center;
  gap: 16px;
  padding: 24px;
}

.gallery-empty-copy {
  display: grid;
  gap: 6px;
  text-align: center;
}

.gallery-empty-copy strong {
  color: var(--ink);
  font-family: var(--font-display);
  font-size: 18px;
  font-weight: 600;
}

.gallery-empty-copy span {
  color: var(--muted);
  font-size: 12px;
}

.gallery-empty-images {
  display: grid;
  width: min(100%, 760px);
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.gallery-empty-images figure {
  overflow: hidden;
  margin: 0;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: var(--surface);
  text-align: left;
}

.gallery-empty-images img {
  display: block;
  width: 100%;
  aspect-ratio: 4 / 3;
  object-fit: cover;
}

.gallery-empty-images figcaption {
  padding: 7px 10px;
  color: var(--muted);
  font-size: 10px;
}

.gallery-measuring {
  display: grid;
  height: clamp(500px, 40vw, 600px);
  place-items: center;
  color: var(--muted);
  font-size: 12px;
}

@media (max-width: 980px) {
  .gallery-stage {
    height: 520px;
  }

  .gallery-measuring {
    height: 520px;
  }
}

@media (max-width: 760px) {
  .trend-gallery {
    padding: 8px 0 32px;
  }

  .gallery-heading h2 {
    font-size: 31px;
  }

  .gallery-stage {
    height: 500px;
    margin-top: 16px;
  }
}

@media (max-width: 540px) {
  .gallery-heading h2 {
    font-size: 28px;
  }

  .gallery-stage {
    height: 460px;
  }
}

@media (max-width: 760px) {
  .gallery-empty-images {
    grid-template-columns: 1fr;
    max-width: 300px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .gallery-card,
  .gallery-card-image img {
    transition: none;
  }
}
</style>
