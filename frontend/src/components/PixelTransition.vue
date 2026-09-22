<template>
  <div
    ref="containerRef"
    :class="['pixel-transition', className, { 'pixel-transition-active': isActive }]"
    :style="style"
    :tabindex="interactive ? 0 : undefined"
    @mouseenter="!isTouchDevice ? handleEnter() : undefined"
    @mouseleave="!isTouchDevice ? handleLeave() : undefined"
    @click="isTouchDevice && interactive ? handleClick() : undefined"
    @focus="!isTouchDevice ? handleEnter() : undefined"
    @blur="!isTouchDevice ? handleLeave() : undefined"
  >
    <div v-if="aspectRatio !== 'auto'" class="pixel-transition-spacer" :style="{ paddingTop: aspectRatio }" aria-hidden="true" />

    <div class="pixel-transition-content" :aria-hidden="isActive">
      <slot name="firstContent">
        <slot name="first" />
      </slot>
    </div>

    <div
      ref="activeRef"
      class="pixel-transition-content pixel-transition-active-content"
      :style="{ display: 'none' }"
      :aria-hidden="!isActive"
    >
      <slot name="secondContent">
        <slot name="second" />
      </slot>
    </div>

    <div ref="pixelGridRef" class="pixel-transition-grid" aria-hidden="true" />
  </div>
</template>

<script setup>
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { gsap } from 'gsap'

const props = defineProps({
  gridSize: { type: Number, default: 7 },
  pixelColor: { type: String, default: 'currentColor' },
  animationStepDuration: { type: Number, default: 0.3 },
  once: { type: Boolean, default: false },
  className: { type: String, default: '' },
  style: { type: [Object, String, Array], default: () => ({}) },
  aspectRatio: { type: String, default: '100%' },
  interactive: { type: Boolean, default: true }
})

const containerRef = ref(null)
const pixelGridRef = ref(null)
const activeRef = ref(null)
const isActive = ref(false)

const isTouchDevice = typeof window !== 'undefined' && (
  'ontouchstart' in window
  || navigator.maxTouchPoints > 0
  || window.matchMedia?.('(pointer: coarse)').matches
)

function buildGrid() {
  const pixelGridElement = pixelGridRef.value
  if (!pixelGridElement) return

  pixelGridElement.innerHTML = ''
  const safeGridSize = Math.max(1, Math.round(props.gridSize))
  const size = 100 / safeGridSize

  for (let row = 0; row < safeGridSize; row += 1) {
    for (let column = 0; column < safeGridSize; column += 1) {
      const pixel = document.createElement('span')
      pixel.className = 'pixel-transition-pixel'
      pixel.style.backgroundColor = props.pixelColor
      pixel.style.width = `${size}%`
      pixel.style.height = `${size}%`
      pixel.style.left = `${column * size}%`
      pixel.style.top = `${row * size}%`
      pixelGridElement.appendChild(pixel)
    }
  }
}

function animatePixels(activate) {
  isActive.value = activate

  const pixelGridElement = pixelGridRef.value
  const activeElement = activeRef.value
  if (!activeElement) return

  activeElement.style.display = activate ? 'block' : 'none'
  activeElement.style.pointerEvents = activate ? 'none' : ''
  if (!pixelGridElement) return

  const pixels = pixelGridElement.querySelectorAll('.pixel-transition-pixel')
  if (!pixels.length) return

  gsap.killTweensOf(pixels)

  // Switch the content behind a full pixel cover, then reveal it in one short pass.
  // This keeps the interaction responsive even when the pointer moves across cards quickly.
  gsap.set(pixels, { display: 'block' })

  const animationDuration = Math.max(0.08, Number(props.animationStepDuration) || 0.18)
  const staggerDuration = Math.max(0.001, animationDuration / pixels.length)

  gsap.to(pixels, {
    display: 'none',
    duration: 0,
    stagger: { each: staggerDuration, from: 'random' }
  })
}

function handleEnter() {
  if (!isActive.value) animatePixels(true)
}

function handleLeave() {
  if (isActive.value && !props.once) animatePixels(false)
}

function handleClick() {
  if (!isActive.value) animatePixels(true)
  else if (!props.once) animatePixels(false)
}

onMounted(buildGrid)

onUnmounted(() => {
  const pixels = pixelGridRef.value?.querySelectorAll('.pixel-transition-pixel')
  if (pixels?.length) gsap.killTweensOf(pixels)
})

watch(() => [props.gridSize, props.pixelColor], buildGrid)
</script>

<style scoped>
.pixel-transition {
  position: relative;
  overflow: hidden;
  isolation: isolate;
}

.pixel-transition-spacer {
  width: 100%;
}

.pixel-transition-content,
.pixel-transition-grid {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}

.pixel-transition-content {
  z-index: 1;
}

.pixel-transition-active-content {
  z-index: 2;
}

.pixel-transition-grid {
  z-index: 3;
  pointer-events: none;
}

.pixel-transition-pixel {
  position: absolute;
  display: none;
}
</style>
