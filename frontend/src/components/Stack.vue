<template>
  <div
    class="stack-root"
    :style="{ perspective: '600px' }"
    @mouseenter="pauseOnHover && (isPaused = true)"
    @mouseleave="pauseOnHover && (isPaused = false)"
  >
    <template v-for="(card, index) in stack" :key="card.id">
      <Motion
        v-if="!shouldDisableDrag"
        as="div"
        class="stack-motion-card"
        :style="{
          zIndex: index + 1,
          x: getCardState(card.id).x,
          y: getCardState(card.id).y,
          rotateX: getCardState(card.id).rotateX,
          rotateY: getCardState(card.id).rotateY
        }"
        drag
        :drag-constraints="{ top: 0, right: 0, bottom: 0, left: 0 }"
        :drag-elastic="0.6"
        :while-tap="{ cursor: 'grabbing' }"
        :on-drag-end="(_: PointerEvent, info) => handleDragEnd(_, info, card.id)"
      >
        <Motion
          as="div"
          class="stack-card-content"
          :animate="{
            rotateZ: getCardRotation(card, index),
            scale: 1 + index * 0.06 - stack.length * 0.06,
            transformOrigin: '90% 90%'
          }"
          :initial="false"
          :transition="{
            type: 'spring',
            stiffness: animationConfig.stiffness,
            damping: animationConfig.damping
          }"
          @click="shouldEnableClick && sendToBack(card.id)"
        >
          <slot :card="card" :index="index">
            <img :src="card.img" :alt="`card-${card.id}`" draggable="false" />
          </slot>
        </Motion>
      </Motion>

      <Motion v-else as="div" class="stack-motion-card stack-motion-card-static" :style="{ zIndex: index + 1, x: 0, y: 0 }">
        <Motion
          as="div"
          class="stack-card-content"
          :animate="{
            rotateZ: getCardRotation(card, index),
            scale: 1 + index * 0.06 - stack.length * 0.06,
            transformOrigin: '90% 90%'
          }"
          :initial="false"
          :transition="{
            type: 'spring',
            stiffness: animationConfig.stiffness,
            damping: animationConfig.damping
          }"
          @click="shouldEnableClick && sendToBack(card.id)"
        >
          <slot :card="card" :index="index">
            <img :src="card.img" :alt="`card-${card.id}`" draggable="false" />
          </slot>
        </Motion>
      </Motion>
    </template>
  </div>
</template>

<script setup>
import { Motion, useMotionValue, useTransform } from 'motion-v'
import { computed, onBeforeMount, onMounted, onUnmounted, ref, watch } from 'vue'

const props = defineProps({
  randomRotation: { type: Boolean, default: false },
  sensitivity: { type: Number, default: 200 },
  cardsData: { type: Array, default: () => [] },
  animationConfig: { type: Object, default: () => ({ stiffness: 260, damping: 20 }) },
  sendToBackOnClick: { type: Boolean, default: false },
  autoplay: { type: Boolean, default: false },
  autoplayDelay: { type: Number, default: 3000 },
  pauseOnHover: { type: Boolean, default: false },
  mobileClickOnly: { type: Boolean, default: false },
  mobileBreakpoint: { type: Number, default: 768 }
})

const emit = defineEmits(['change'])

const isMobile = ref(false)
const isPaused = ref(false)
const cardRandomRotations = ref({})
const stack = ref([])
const cardStates = new Map()
let autoplayInterval = null

const shouldDisableDrag = computed(() => props.mobileClickOnly && isMobile.value)
const shouldEnableClick = computed(() => props.sendToBackOnClick || shouldDisableDrag.value)

function checkMobile() {
  isMobile.value = window.innerWidth < props.mobileBreakpoint
}

function buildStack(data) {
  return data.map((card) => ({ ...card }))
}

function ensureRandomRotation(id) {
  if (!(id in cardRandomRotations.value)) {
    cardRandomRotations.value[id] = props.randomRotation ? Math.random() * 10 - 5 : 0
  }
}

function createCardState() {
  const x = useMotionValue(0)
  const y = useMotionValue(0)
  const rotateX = useTransform(y, [-100, 100], [60, -60])
  const rotateY = useTransform(x, [-100, 100], [-60, 60])
  return {
    x,
    y,
    rotateX,
    rotateY,
    reset() {
      x.set(0)
      y.set(0)
    }
  }
}

function getCardState(cardId) {
  let state = cardStates.get(cardId)
  if (!state) {
    state = createCardState()
    cardStates.set(cardId, state)
  }
  return state
}

function getCardRotation(card, index) {
  if (index === stack.value.length - 1) return 0
  return (stack.value.length - index - 1) * 4 + cardRandomRotations.value[card.id]
}

function notifyActive() {
  const index = stack.value.length - 1
  const card = stack.value[index]
  if (card) emit('change', { card, index, stack: stack.value })
}

function handleDragEnd(_, info, cardId) {
  const offset = info?.offset || { x: 0, y: 0 }
  if (Math.abs(offset.x) > props.sensitivity || Math.abs(offset.y) > props.sensitivity) {
    sendToBack(cardId)
  } else {
    getCardState(cardId).reset()
  }
}

function sendToBack(id) {
  const newStack = [...stack.value]
  const index = newStack.findIndex((card) => card.id === id)
  if (index < 0) return
  const [card] = newStack.splice(index, 1)
  getCardState(id).reset()
  newStack.unshift(card)
  ensureRandomRotation(card.id)
  stack.value = newStack
  notifyActive()
}

function resetStack(newCards) {
  stack.value = buildStack(newCards)
  stack.value.forEach((card) => {
    getCardState(card.id)
    ensureRandomRotation(card.id)
  })
  notifyActive()
}

onBeforeMount(() => {
  resetStack(props.cardsData)
  checkMobile()
  window.addEventListener('resize', checkMobile)
})

onMounted(notifyActive)

onUnmounted(() => {
  window.removeEventListener('resize', checkMobile)
  if (autoplayInterval) clearInterval(autoplayInterval)
})

watch(() => props.cardsData, (newCards) => resetStack(newCards), { deep: true })

watch(
  [() => props.autoplay, () => props.autoplayDelay, stack, isPaused],
  () => {
    if (autoplayInterval) {
      clearInterval(autoplayInterval)
      autoplayInterval = null
    }
    if (props.autoplay && stack.value.length > 1 && !isPaused.value) {
      autoplayInterval = setInterval(() => {
        const topCard = stack.value[stack.value.length - 1]
        if (topCard) sendToBack(topCard.id)
      }, props.autoplayDelay)
    }
  },
  { immediate: true }
)
</script>

<style scoped>
.stack-root {
  position: relative;
  width: 100%;
  height: 100%;
  touch-action: none;
}

.stack-motion-card {
  position: absolute;
  inset: 0;
  cursor: grab;
}

.stack-motion-card:active {
  cursor: grabbing;
}

.stack-motion-card-static {
  cursor: pointer;
}

.stack-card-content {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  overflow: hidden;
  border-radius: 18px;
}

.stack-card-content img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
  pointer-events: none;
  user-select: none;
}

@media (max-width: 767px) {
  .stack-card-content {
    transform-origin: 50% 50% !important;
  }
}
</style>
