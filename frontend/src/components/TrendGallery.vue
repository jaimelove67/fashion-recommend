<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ImageOff } from '@lucide/vue'

const AUTOPLAY_INTERVAL_MS = 3000

const props = defineProps({
  trends: { type: Array, default: () => [] },
  selectedId: { type: [String, Number], default: null }
})

const emit = defineEmits(['select'])
const gallerySection = ref(null)
const galleryStage = ref(null)
const galleryShift = ref(126)
const galleryIndex = ref(0)
const galleryReady = ref(false)
const galleryVisibleRange = ref(2)
const unavailableImageUrls = ref(new Set())
let resizeObserver = null
let autoplayTimer = null
let motionPreference = null
let galleryMounted = false
let galleryKeyboardFocused = false
let galleryReducedMotion = false

const selectedIndex = computed(() => {
  const index = props.trends.findIndex((item) => item.id === props.selectedId)
  return index >= 0 ? index : 0
})
const visibleCards = computed(() => {
  const total = props.trends.length
  if (!total) return []
  const range = Math.min(galleryVisibleRange.value, Math.floor((total - 1) / 2))
  if (total <= range * 2 + 1) return props.trends.map((item, index) => ({ item, index }))

  return Array.from({ length: range * 2 + 1 }, (_, index) => index - range).map((offset) => {
    const index = (galleryIndex.value + offset + total) % total
    return { item: props.trends[index], index }
  })
})

function imageUrl(item) {
  return item?.imageUrl || item?.evidence?.images?.find(Boolean) || ''
}

function hasImage(item) {
  const url = imageUrl(item)
  return Boolean(url) && !unavailableImageUrls.value.has(url)
}

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
    ? Math.min(260, stageWidth * 0.68)
    : Math.min(308, Math.max(236, stageWidth * 0.235))
  galleryShift.value = mobileLayout
    ? Math.round(cardWidth + 20)
    : Math.round(cardWidth * 0.8)
  const visibleRange = window.innerWidth <= 980 ? 1 : 2
  galleryVisibleRange.value = visibleRange

  if (props.trends.length) {
    let selectedOffset = selectedIndex.value - galleryIndex.value
    const half = props.trends.length / 2
    if (selectedOffset > half) selectedOffset -= props.trends.length
    if (selectedOffset < -half) selectedOffset += props.trends.length
    if (Math.abs(selectedOffset) > visibleRange) galleryIndex.value = selectedIndex.value
  }
}

function cardOffset(index) {
  const total = props.trends.length
  if (!total) return 0
  let offset = index - galleryIndex.value
  const half = total / 2
  if (offset > half) offset -= total
  if (offset < -half) offset += total
  return offset
}

function cardStyle(index) {
  const offset = cardOffset(index)
  const distance = Math.abs(offset)
  return {
    '--gallery-x': `${offset * galleryShift.value}px`,
    '--gallery-y': `${distance * 7}px`,
    '--gallery-rotation': `${offset * -7}deg`,
    '--gallery-scale': Math.max(0.82, 1 - distance * 0.06),
    zIndex: 20 - distance
  }
}

function selectTrend(item, index = props.trends.findIndex((trend) => trend.id === item?.id)) {
  if (item?.id == null) return
  if (index >= 0) galleryIndex.value = index
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
    || props.trends.length < 2
  ) return

  autoplayTimer = window.setInterval(() => {
    const total = props.trends.length
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
  if (props.trends.length < 2) return
  event.preventDefault()

  let nextIndex = selectedIndex.value
  if (event.key === 'ArrowLeft') nextIndex = (selectedIndex.value - 1 + props.trends.length) % props.trends.length
  if (event.key === 'ArrowRight') nextIndex = (selectedIndex.value + 1) % props.trends.length
  if (event.key === 'Home') nextIndex = 0
  if (event.key === 'End') nextIndex = props.trends.length - 1
  selectTrend(props.trends[nextIndex], nextIndex)
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

watch(() => JSON.stringify(props.trends.map((item) => item.id)), async () => {
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
        <h2 id="trend-gallery-title">风潮穿搭精选</h2>
        <p>画廊自动轮播，点击图片可将其转到中心并查看对应穿搭灵感</p>
      </div>
    </header>

    <div
      v-if="trends.length && galleryReady"
      ref="galleryStage"
      class="gallery-stage"
      role="region"
      aria-label="趋势穿搭画廊"
      aria-roledescription="3D 画廊"
      aria-keyshortcuts="ArrowLeft ArrowRight Home End"
      @keydown="handleKeydown"
      @focusin="handleGalleryFocusin"
      @focusout="handleGalleryFocusout"
    >
      <button
        v-for="{ item, index } in visibleCards"
        :key="item.id"
        type="button"
        class="gallery-card"
        :class="{ active: selectedId === item.id }"
        :style="cardStyle(index)"
        :data-gallery-index="index"
        :data-gallery-center="cardOffset(index) === 0 ? 'true' : undefined"
        :aria-label="`查看第 ${index + 1} 条：${item.title}`"
        :aria-current="selectedId === item.id ? 'true' : undefined"
        :aria-pressed="selectedId === item.id"
        :aria-posinset="index + 1"
        :aria-setsize="trends.length"
        :tabindex="Math.abs(cardOffset(index)) <= 3 ? 0 : -1"
        @click="selectTrend(item, index)"
      >
        <span v-if="hasImage(item)" class="gallery-card-image">
          <img
            :src="imageUrl(item)"
            :alt="item.title || '趋势穿搭图片'"
            loading="lazy"
            referrerpolicy="no-referrer"
            @error="markImageUnavailable"
          />
        </span>
        <span v-else class="gallery-card-no-image">
          <ImageOff :size="22" aria-hidden="true" />
          <small>{{ item.title || '来源未提供配图' }}</small>
        </span>
      </button>
    </div>
    <div v-else-if="!trends.length" class="gallery-empty" role="status">趋势内容加载后，会显示在这里。</div>
    <div v-else class="gallery-measuring" role="status">正在整理画廊…</div>
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
  height: clamp(430px, 42vw, 560px);
  margin-top: 18px;
  overflow: hidden;
  isolation: isolate;
  perspective: 1800px;
}

.gallery-card {
  position: absolute;
  top: 51%;
  left: 50%;
  display: block;
  width: clamp(236px, 23.5%, 308px);
  aspect-ratio: 3 / 4;
  overflow: visible;
  border: 0;
  border-radius: 2px;
  padding: 0;
  color: inherit;
  background: transparent;
  cursor: pointer;
  filter: saturate(.92);
  transform: translate3d(calc(-50% + var(--gallery-x)), calc(-50% + var(--gallery-y)), 0) rotateY(var(--gallery-rotation)) scale(var(--gallery-scale));
  transform-origin: center 70%;
  transform-style: preserve-3d;
  transition: transform 720ms cubic-bezier(.22, .75, .2, 1), filter 260ms ease;
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

.gallery-card:focus-visible .gallery-card-image,
.gallery-card:focus-visible .gallery-card-no-image {
  outline: 2px solid var(--accent);
  outline-offset: 3px;
}

.gallery-card-image,
.gallery-card-no-image {
  position: absolute;
  inset: 0;
  display: block;
  overflow: hidden;
  border: 1px solid rgba(82, 92, 91, .16);
  border-radius: inherit;
  background: var(--surface-soft, #dfe5e7);
  box-shadow: 0 16px 28px rgba(41, 55, 63, .13);
}

.gallery-card.active .gallery-card-image,
.gallery-card.active .gallery-card-no-image {
  border-color: rgba(54, 64, 62, .38);
  box-shadow: 0 20px 34px rgba(41, 55, 63, .2);
}

.gallery-card-image img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.gallery-card-no-image {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 16px;
  color: var(--muted);
  text-align: center;
}

.gallery-card-no-image small {
  display: -webkit-box;
  overflow: hidden;
  font-size: 11px;
  line-height: 1.5;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
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

.gallery-measuring {
  display: grid;
  height: clamp(430px, 42vw, 560px);
  place-items: center;
  color: var(--muted);
  font-size: 12px;
}

@media (max-width: 980px) {
  .gallery-stage {
    height: 440px;
  }

  .gallery-measuring {
    height: 440px;
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
    height: 460px;
    margin-top: 16px;
  }

  .gallery-card {
    width: min(68%, 260px);
  }
}

@media (max-width: 540px) {
  .gallery-heading h2 {
    font-size: 28px;
  }

  .gallery-stage {
    height: 420px;
  }

  .gallery-card {
    width: 68%;
  }
}

@media (prefers-reduced-motion: reduce) {
  .gallery-card,
  .gallery-card-image img {
    transition: none;
  }
}
</style>
