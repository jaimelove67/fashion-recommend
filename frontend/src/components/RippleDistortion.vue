<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'

const props = defineProps({
  brushSize: { type: Number, default: 160 },
  strength: { type: Number, default: 0.2 },
  spread: { type: Number, default: 5 },
  fade: { type: Number, default: 3 },
  spacing: { type: Number, default: 18 },
  trigger: { type: String, default: 'both' },
  clickStrength: { type: Number, default: 2 },
  enabled: { type: Boolean, default: true }
})

const rootRef = ref(null)
const canvasRef = ref(null)

let context = null
let animationFrame = 0
let resizeObserver = null
let reducedMotionQuery = null
let reducedMotion = false
let width = 0
let height = 0
let lastPointer = null
let lastFrameTime = 0
const ripples = []

function isInside(x, y) {
  return x >= 0 && y >= 0 && x <= width && y <= height
}

function pointerPosition(event) {
  const root = rootRef.value
  if (!root) return null
  const rect = root.getBoundingClientRect()
  const x = event.clientX - rect.left
  const y = event.clientY - rect.top
  return isInside(x, y) ? { x, y } : null
}

function spawnRipple(position, multiplier = 1) {
  if (!position || !props.enabled || reducedMotion) return
  ripples.push({
    x: position.x,
    y: position.y,
    startedAt: performance.now(),
    strength: Math.min(1.5, Math.max(0.1, props.strength * multiplier)),
    rotation: Math.random() * Math.PI
  })
  if (ripples.length > 24) ripples.shift()
}

function handlePointerMove(event) {
  if (!props.enabled || reducedMotion || !['hover', 'both'].includes(props.trigger)) return
  const position = pointerPosition(event)
  if (!position) {
    lastPointer = null
    return
  }
  if (!lastPointer || Math.hypot(position.x - lastPointer.x, position.y - lastPointer.y) >= props.spacing) {
    spawnRipple(position)
    lastPointer = position
  }
}

function handlePointerDown(event) {
  if (!props.enabled || reducedMotion || !['click', 'both'].includes(props.trigger)) return
  spawnRipple(pointerPosition(event), props.clickStrength)
}

function resizeCanvas() {
  const root = rootRef.value
  const canvas = canvasRef.value
  if (!root || !canvas) return
  const rect = root.getBoundingClientRect()
  const pixelRatio = Math.min(window.devicePixelRatio || 1, 1.5)
  width = Math.max(1, rect.width)
  height = Math.max(1, rect.height)
  canvas.width = Math.round(width * pixelRatio)
  canvas.height = Math.round(height * pixelRatio)
  canvas.style.width = `${width}px`
  canvas.style.height = `${height}px`
  context = canvas.getContext('2d')
  context?.setTransform(pixelRatio, 0, 0, pixelRatio, 0, 0)
}

function drawAmbient(time) {
  if (!context) return
  const phase = time * 0.00018
  const ambientX = width * (0.5 + Math.sin(phase) * 0.12)
  const ambientY = height * (0.5 + Math.cos(phase * 1.15) * 0.1)
  const wash = context.createRadialGradient(ambientX, ambientY, 0, ambientX, ambientY, Math.max(width, height) * 0.62)
  wash.addColorStop(0, 'rgba(39, 85, 72, 0.11)')
  wash.addColorStop(0.42, 'rgba(177, 149, 98, 0.055)')
  wash.addColorStop(1, 'rgba(252, 250, 245, 0)')
  context.fillStyle = wash
  context.fillRect(0, 0, width, height)

  context.save()
  context.translate(width * 0.5, height * 0.52)
  context.rotate(Math.sin(phase * 0.8) * 0.08)
  context.scale(1, 0.55)
  context.strokeStyle = 'rgba(39, 85, 72, 0.11)'
  context.lineWidth = 1
  for (let index = 0; index < 4; index += 1) {
    const radius = Math.min(width, height) * (0.12 + index * 0.12) + Math.sin(phase * 2 + index) * 4
    context.beginPath()
    context.arc(0, 0, radius, 0, Math.PI * 2)
    context.stroke()
  }
  context.restore()
}

function drawRipple(ripple, time) {
  if (!context) return
  const age = (time - ripple.startedAt) / 1000
  const life = Math.max(0.15, props.fade)
  const progress = age / life
  if (progress >= 1) return
  const eased = 1 - Math.pow(1 - progress, 2)
  const maxRadius = props.brushSize * (0.5 + props.spread * 0.34)
  const radius = props.brushSize * 0.16 + maxRadius * eased
  const fade = Math.pow(1 - progress, 1.4) * ripple.strength

  context.save()
  context.translate(ripple.x, ripple.y)
  context.rotate(ripple.rotation + Math.sin(age * 1.8) * 0.08)
  context.scale(1, 0.62)
  for (let ring = 0; ring < 4; ring += 1) {
    const ringProgress = (ring + 1) / 4
    const ringRadius = radius * (0.5 + ringProgress * 0.58)
    const ringFade = fade * (1 - ring * 0.17)
    context.beginPath()
    context.arc(0, 0, ringRadius, 0, Math.PI * 2)
    context.lineWidth = 1 + ringFade * 2.4
    context.strokeStyle = `rgba(39, 85, 72, ${Math.max(0, ringFade * 0.34)})`
    context.stroke()
  }

  const highlight = context.createRadialGradient(0, -radius * 0.18, 0, 0, 0, radius)
  highlight.addColorStop(0, `rgba(177, 149, 98, ${Math.max(0, fade * 0.12)})`)
  highlight.addColorStop(0.65, 'rgba(177, 149, 98, 0)')
  context.fillStyle = highlight
  context.beginPath()
  context.arc(0, 0, radius, 0, Math.PI * 2)
  context.fill()
  context.restore()
}

function render(time) {
  if (!context) return
  const delta = lastFrameTime ? Math.min(80, time - lastFrameTime) : 16
  lastFrameTime = time
  context.clearRect(0, 0, width, height)
  drawAmbient(time)
  for (const ripple of ripples) drawRipple(ripple, time)
  for (let index = ripples.length - 1; index >= 0; index -= 1) {
    if ((time - ripples[index].startedAt) / 1000 >= Math.max(0.15, props.fade)) ripples.splice(index, 1)
  }
  if (!reducedMotion && (ripples.length || delta > 0)) animationFrame = requestAnimationFrame(render)
}

function handleReducedMotionChange(event) {
  reducedMotion = event.matches
  lastPointer = null
  if (reducedMotion) ripples.splice(0)
  render(performance.now())
}

onMounted(() => {
  resizeCanvas()
  resizeObserver = new ResizeObserver(resizeCanvas)
  if (rootRef.value) resizeObserver.observe(rootRef.value)
  reducedMotionQuery = window.matchMedia('(prefers-reduced-motion: reduce)')
  reducedMotion = reducedMotionQuery.matches
  reducedMotionQuery.addEventListener?.('change', handleReducedMotionChange)
  window.addEventListener('pointermove', handlePointerMove, { passive: true })
  window.addEventListener('pointerdown', handlePointerDown, { passive: true })
  animationFrame = requestAnimationFrame(render)
})

onBeforeUnmount(() => {
  cancelAnimationFrame(animationFrame)
  resizeObserver?.disconnect()
  reducedMotionQuery?.removeEventListener?.('change', handleReducedMotionChange)
  window.removeEventListener('pointermove', handlePointerMove)
  window.removeEventListener('pointerdown', handlePointerDown)
})
</script>

<template>
  <div ref="rootRef" class="ripple-distortion" aria-hidden="true">
    <canvas ref="canvasRef" class="ripple-distortion-canvas" />
  </div>
</template>

<style scoped>
.ripple-distortion,
.ripple-distortion-canvas {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}

.ripple-distortion {
  overflow: hidden;
  pointer-events: none;
}

.ripple-distortion-canvas {
  display: block;
}
</style>
