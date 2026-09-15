<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { CircleAlert, Eye, EyeOff, LoaderCircle, LogIn, UserRoundPlus } from '@lucide/vue'

const props = defineProps({
  app: {
    type: Object,
    required: true
  }
})

const form = reactive({
  username: '',
  password: '',
  confirmPassword: ''
})
const mode = ref('login')
const showPassword = ref(false)
const validationError = ref('')
const phase = ref('loading')
const statusText = ref('正在加载双鱼…')
const introVisible = ref(true)
const loginVisible = ref(false)
const assetsError = ref('')
const motionReduced = ref(false)
const canvasRef = ref(null)
const startButton = ref(null)
const usernameInput = ref(null)

const submitting = computed(() => props.app.state.authSubmitting)
const errorMessage = computed(() => validationError.value || props.app.state.authError)

let context = null
let atlasImage = null
let logoImage = null
let motionMedia = null
let animationFrame = 0
let focusTimer = 0
let width = 0
let height = 0
let lastFrame = 0
let swimClock = 0
let stepSeconds = 0
let started = 0
let restStarted = 0
let centerCache = null

const pathTables = new Map()
const motion = [0, 1].map((index) => ({
  wave: index * 1.8,
  energy: 0.35,
  bend: 0,
  angle: null,
  bob: 0,
  exitBob: 0
}))

function clearFocusTimer() {
  if (!focusTimer) return
  window.clearTimeout(focusTimer)
  focusTimer = 0
}

function clearAuthError() {
  validationError.value = ''
  props.app.state.authError = ''
}

function focusFirstEditable() {
  if (!loginVisible.value || submitting.value) return
  const input = usernameInput.value
  if (!input || input.disabled) return
  input.focus({ preventScroll: true })
}

function scheduleFocus() {
  clearFocusTimer()
  nextTick(() => {
    focusTimer = window.setTimeout(() => {
      focusTimer = 0
      if (!submitting.value && loginVisible.value) focusFirstEditable()
    }, 240)
  })
}

function isReducedMotion() {
  return motionReduced.value
}

function setPhase(nextPhase, nextStatus) {
  if (nextPhase === 'login' && phase.value !== 'submitting') restStarted = swimClock
  phase.value = nextPhase
  started = swimClock
  statusText.value = nextStatus
  wake()
}

function wake() {
  if (!animationFrame) animationFrame = window.requestAnimationFrame(frame)
}

function clamp(value) {
  return Math.max(0, Math.min(1, value))
}

function smooth(value) {
  return value * value * (3 - 2 * value)
}

function glide(value) {
  return value * value * value * (10 + value * (-15 + 6 * value))
}

function animationDuration() {
  return isReducedMotion() ? 80 : 3000
}

function initialCenter() {
  if (!centerCache) {
    const rect = startButton.value?.getBoundingClientRect()
    centerCache = rect
      ? {
          x: rect.x + rect.width / 2,
          y: rect.y + rect.height / 2,
          size: Math.min(rect.width - 20, rect.height)
        }
      : {
          x: width / 2,
          y: height * 0.48,
          size: Math.min(width * 0.5, 320)
        }
  }
  return centerCache
}

function geometry() {
  const mobile = width < 650
  const fishSize = mobile ? Math.min(width * 0.4, 180) : Math.min(width * 0.29, 390)
  return {
    mobile,
    size: fishSize,
    spread: mobile ? width * 0.31 : Math.min(width * 0.32, 440),
    y: height * 0.48
  }
}

function swimPose(index, progress, geometryValue, center) {
  const sign = index === 0 ? 1 : -1
  const start = {
    x: width / 2 + sign * 30,
    y: center.y - sign * 43
  }
  const end = {
    x: width / 2 + sign * geometryValue.spread,
    y: geometryValue.y + sign * (geometryValue.mobile ? height * 0.36 : 30)
  }
  const bend = Math.min(height * 0.24, geometryValue.mobile ? 170 : 190)
  const firstControl = {
    x: start.x + sign * geometryValue.spread * 0.65,
    y: start.y + (index === 0 ? 10 : 65)
  }
  const secondControl = {
    x: end.x - sign * 12,
    y: Math.max(
      start.y + (index === 0 ? 25 : 80),
      end.y - sign * bend
    )
  }
  const remaining = 1 - progress
  const x =
    remaining * remaining * remaining * start.x +
    3 * remaining * remaining * progress * firstControl.x +
    3 * remaining * progress * progress * secondControl.x +
    progress * progress * progress * end.x
  const y =
    remaining * remaining * remaining * start.y +
    3 * remaining * remaining * progress * firstControl.y +
    3 * remaining * progress * progress * secondControl.y +
    progress * progress * progress * end.y
  const dx =
    3 * remaining * remaining * (firstControl.x - start.x) +
    6 * remaining * progress * (secondControl.x - firstControl.x) +
    3 * progress * progress * (end.x - secondControl.x)
  const dy =
    3 * remaining * remaining * (firstControl.y - start.y) +
    6 * remaining * progress * (secondControl.y - firstControl.y) +
    3 * progress * progress * (end.y - secondControl.y)

  return { x, y, angle: Math.atan2(dy, dx) }
}

function swimAtDistance(fishIndex, progress, geometryValue, center) {
  let table = pathTables.get(fishIndex)
  if (!table) {
    table = [{ t: 0, d: 0 }]
    let previous = swimPose(fishIndex, 0, geometryValue, center)
    let distance = 0
    for (let sampleIndex = 1; sampleIndex <= 160; sampleIndex += 1) {
      const pose = swimPose(fishIndex, sampleIndex / 160, geometryValue, center)
      distance += Math.hypot(pose.x - previous.x, pose.y - previous.y)
      table.push({ t: sampleIndex / 160, d: distance })
      previous = pose
    }
    pathTables.set(fishIndex, table)
  }

  const target = clamp(progress) * table[160].d
  let low = 0
  let high = 160
  while (high - low > 1) {
    const middle = (low + high) >> 1
    if (table[middle].d < target) low = middle
    else high = middle
  }
  const first = table[low]
  const second = table[high]
  const ratio = second.d === first.d ? 0 : (target - first.d) / (second.d - first.d)
  return swimPose(fishIndex, first.t + (second.t - first.t) * ratio, geometryValue, center)
}

function drawLogo(x, y, size, alpha = 1) {
  if (!context || !logoImage) return
  const sourceWidth = logoImage.naturalWidth || logoImage.width
  const sourceHeight = logoImage.naturalHeight || logoImage.height
  const renderedHeight = size * (sourceHeight / sourceWidth)
  context.save()
  context.globalAlpha = alpha
  context.drawImage(
    logoImage,
    0,
    0,
    sourceWidth,
    sourceHeight,
    x - size / 2,
    y - renderedHeight / 2,
    size,
    renderedHeight
  )
  context.restore()
}

function drawFish(fishIndex, x, y, size, angle, alpha, energy) {
  if (!context || !atlasImage || alpha <= 0) return
  const currentMotion = motion[fishIndex]
  const delta = stepSeconds
  const blend = 1 - Math.exp(-delta * 6)
  currentMotion.energy += (energy - currentMotion.energy) * blend
  currentMotion.wave += delta * (1.5 + currentMotion.energy * 4.2)
  const turn =
    currentMotion.angle === null || delta === 0
      ? 0
      : Math.atan2(Math.sin(angle - currentMotion.angle), Math.cos(angle - currentMotion.angle)) / delta
  const bendTarget = Math.max(-1, Math.min(1, turn * 0.28))
  currentMotion.bend += (bendTarget - currentMotion.bend) * (1 - Math.exp(-delta * 8))
  currentMotion.angle = angle

  context.save()
  context.translate(x, y)
  context.rotate(angle)
  context.globalAlpha = alpha
  const sourceWidth = atlasImage.naturalWidth || atlasImage.width
  const sourceHeight = atlasImage.naturalHeight || atlasImage.height
  const fishWidth = sourceWidth / 2
  const scale = size / fishWidth
  const destinationHeight = sourceHeight * scale
  const strips = 110
  const slice = fishWidth / strips

  for (let stripIndex = 0; stripIndex < strips; stripIndex += 1) {
    const progress = stripIndex / strips
    const tail = Math.pow(1 - progress, 2.3)
    const wave = isReducedMotion() ? 0 : Math.sin(currentMotion.wave + progress * 5.8) * size * 0.027 * tail * currentMotion.energy
    const bend = isReducedMotion() ? 0 : -currentMotion.bend * size * 0.13 * tail
    context.drawImage(
      atlasImage,
      fishIndex * fishWidth + stripIndex * slice,
      0,
      slice + 0.3,
      sourceHeight,
      -size / 2 + stripIndex * slice * scale,
      -destinationHeight / 2 + wave + bend,
      slice * scale + 0.65,
      destinationHeight
    )
  }
  context.restore()
}

function drawRipples(progress) {
  if (!context || isReducedMotion()) return
  const value = clamp(progress)
  context.save()
  context.strokeStyle = '#b8c9b5'
  context.lineWidth = 0.7
  for (let index = 0; index < 3; index += 1) {
    const ripple = clamp((value - index * 0.09) / 0.8)
    context.globalAlpha = (1 - ripple) * 0.28
    context.beginPath()
    context.ellipse(
      width / 2,
      height * 0.46,
      90 + ripple * width * 0.47,
      45 + ripple * height * 0.3,
      -0.12,
      0,
      Math.PI * 2
    )
    context.stroke()
  }
  context.restore()
}

function draw(now) {
  if (!context) return
  stepSeconds = lastFrame
    ? Math.min(Math.max(0, now - lastFrame), 50) / 1000
    : 0
  lastFrame = now
  swimClock += stepSeconds * 1000
  context.clearRect(0, 0, width, height)

  const currentPhase = phase.value
  if (currentPhase === 'loading' || !atlasImage || !logoImage) return

  const geometryValue = geometry()
  const center = initialCenter()

  if (currentPhase === 'intro') {
    drawLogo(center.x, center.y, center.size)
    return
  }

  if (currentPhase === 'opening') {
    const progress = clamp((swimClock - started) / animationDuration())
    drawRipples(progress)
    if (progress < 0.32) drawLogo(center.x, center.y, center.size, 1 - smooth(clamp(progress / 0.32)))

    for (let index = 0; index < 2; index += 1) {
      const travel = clamp((progress - index * 0.035) / 0.965)
      const pose = swimAtDistance(index, glide(travel), geometryValue, center)
      const effort = 0.34 + 1.05 * Math.sin(Math.PI * travel)
      drawFish(
        index,
        pose.x,
        pose.y,
        geometryValue.size * (0.68 + 0.32 * glide(travel)),
        pose.angle,
        glide(clamp((progress - 0.025) / 0.23)),
        effort
      )
    }

    if (progress > 0.72) revealLogin()
    if (progress >= 1) setPhase('login', '等待登录')
    return
  }

  if (currentPhase === 'login' || currentPhase === 'submitting') {
    for (let index = 0; index < 2; index += 1) {
      const pose = swimPose(index, 1, geometryValue, center)
      const age = swimClock - restStarted
      const bob = isReducedMotion() ? 0 : Math.sin(age * 0.00085 + index * 1.8) * 3 * glide(clamp(age / 900))
      motion[index].bob = bob
      drawFish(index, pose.x, pose.y + bob, geometryValue.size, pose.angle, 1, 0.34)
    }
  }
}

function frame(now) {
  animationFrame = 0
  draw(now)
  const currentPhase = phase.value
  if (currentPhase !== 'home' && currentPhase !== 'intro' && currentPhase !== 'loading' && !(isReducedMotion() && currentPhase === 'login')) {
    wake()
  }
}

function resizeCanvas() {
  const element = canvasRef.value
  if (!element || !context) return
  centerCache = null
  pathTables.clear()
  width = window.innerWidth
  height = window.innerHeight
  const devicePixelRatio = Math.min(window.devicePixelRatio || 1, 2)
  element.width = width * devicePixelRatio
  element.height = height * devicePixelRatio
  element.style.width = width + 'px'
  element.style.height = height + 'px'
  context.setTransform(devicePixelRatio, 0, 0, devicePixelRatio, 0, 0)
  wake()
}

function revealLogin() {
  if (loginVisible.value) return
  loginVisible.value = true
  nextTick(focusFirstEditable)
}

function resetScene() {
  if (!atlasImage || !logoImage) return
  centerCache = null
  pathTables.clear()
  lastFrame = 0
  swimClock = 0
  motion.forEach((currentMotion, index) => {
    Object.assign(currentMotion, {
      wave: index * 1.8,
      energy: 0.35,
      bend: 0,
      angle: null,
      bob: 0,
      exitBob: 0
    })
  })
  introVisible.value = true
  loginVisible.value = false
  clearAuthError()
  setPhase('intro', '点击双鱼开始')
  nextTick(() => startButton.value?.focus({ preventScroll: true }))
}

function openLogin(skipAnimation = false) {
  if (submitting.value || phase.value !== 'intro') return
  clearAuthError()
  introVisible.value = false
  if (skipAnimation || isReducedMotion()) {
    revealLogin()
    setPhase('login', '等待登录')
  } else {
    setPhase('opening', '正在展开登录')
  }
}

function selectMode(nextMode) {
  if (submitting.value || nextMode === mode.value) return
  mode.value = nextMode
  showPassword.value = false
  clearAuthError()
  form.password = ''
  form.confirmPassword = ''
  scheduleFocus()
}

async function submit() {
  if (submitting.value) return
  validationError.value = ''
  const username = form.username.trim()
  const passwordBytes = new TextEncoder().encode(form.password).length

  if (!/^[a-z0-9][a-z0-9_-]{2,31}$/.test(username)) {
    validationError.value = '账号需为 3–32 位小写字母、数字、下划线或连字符。'
    return
  }
  if (passwordBytes < 8) {
    validationError.value = '密码至少需要 8 个字符。'
    return
  }
  if (passwordBytes > 72) {
    validationError.value = '密码长度不能超过 72 个字节。'
    return
  }
  if (mode.value === 'register' && form.password !== form.confirmPassword) {
    validationError.value = '两次输入的密码不一样。'
    return
  }

  form.username = username
  clearAuthError()
  setPhase('submitting', mode.value === 'login' ? '正在登录知己' : '正在创建账户')

  try {
    const success = mode.value === 'register'
      ? await props.app.register({ username, password: form.password })
      : await props.app.login({ username, password: form.password })

    if (success) return
    loginVisible.value = true
    setPhase(
      'login',
      mode.value === 'login' ? '登录失败，可重试' : '注册失败，可重试'
    )
  } catch (cause) {
    validationError.value = cause instanceof Error ? cause.message : '服务暂时不可用，请稍后再试。'
    loginVisible.value = true
    setPhase('login', '请求失败，可重试')
  }
}

function loadImage(source) {
  return new Promise((resolve, reject) => {
    const image = new Image()
    image.onload = () => resolve(image)
    image.onerror = reject
    image.src = source
  })
}

function handleMotionChange(event) {
  motionReduced.value = event.matches
  wake()
}

onMounted(() => {
  context = canvasRef.value?.getContext('2d')
  motionMedia = window.matchMedia('(prefers-reduced-motion: reduce)')
  motionReduced.value = motionMedia.matches
  window.addEventListener('resize', resizeCanvas)
  if (motionMedia.addEventListener) motionMedia.addEventListener('change', handleMotionChange)
  else motionMedia.addListener(handleMotionChange)

  if (!context) {
    assetsError.value = '当前浏览器不支持双鱼动画，但仍可继续登录。'
    introVisible.value = false
    loginVisible.value = true
    setPhase('login', '等待登录')
    return
  }

  Promise.all([
    loadImage('/prototypes/zhiji-fish/assets/fish-atlas.png'),
    loadImage('/prototypes/zhiji-fish/assets/original-logo.png')
  ])
    .then(([atlas, logo]) => {
      atlasImage = atlas
      logoImage = logo
      resizeCanvas()
      resetScene()
    })
    .catch(() => {
      assetsError.value = '双鱼图片加载失败，但仍可继续登录。'
      introVisible.value = false
      loginVisible.value = true
      setPhase('login', '等待登录')
      nextTick(focusFirstEditable)
    })
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeCanvas)
  if (motionMedia?.removeEventListener) motionMedia.removeEventListener('change', handleMotionChange)
  else motionMedia?.removeListener(handleMotionChange)
  if (animationFrame) window.cancelAnimationFrame(animationFrame)
  clearFocusTimer()
})
</script>

<template>
  <main class="fish-auth" :data-phase="phase">
    <div class="fish-auth-stage">
      <header class="fish-auth-header">
        <div class="fish-auth-brand">
          <strong>知己</strong>
          <small>ZHI JI</small>
        </div>
        <button
          v-if="!loginVisible"
          class="fish-auth-text-button"
          type="button"
          :disabled="phase === 'loading' || submitting"
          @click="openLogin(true)"
        >
          直接登录
        </button>
        <button
          v-else-if="!assetsError"
          class="fish-auth-text-button"
          type="button"
          :disabled="submitting"
          @click="resetScene"
        >
          返回开场
        </button>
      </header>

      <section
        class="fish-auth-intro"
        :class="{ 'is-hidden': !introVisible }"
        :aria-hidden="!introVisible ? 'true' : undefined"
        :inert="!introVisible"
      >
        <div class="fish-auth-intro-inner">
          <button
            ref="startButton"
            class="fish-auth-logo-trigger"
            type="button"
            aria-label="轻触双鱼，走近知己"
            :disabled="phase !== 'intro' || submitting"
            @click="openLogin()"
          ></button>
          <div class="fish-auth-wordmark">知己</div>
          <div class="fish-auth-roman">ZHI JI</div>
          <p class="fish-auth-invitation">轻触双鱼，走近知己</p>
          <p class="fish-auth-note">你的穿衣心事，和知己聊聊。</p>
        </div>
      </section>

      <section
        class="fish-auth-login"
        :class="{ 'is-visible': loginVisible }"
        :aria-hidden="!loginVisible ? 'true' : undefined"
        :inert="!loginVisible"
        aria-label="真实账户认证"
      >
        <div class="fish-auth-login-inner">
          <h1>{{ mode === 'login' ? '欢迎回来' : '创建你的风格档案' }}</h1>
          <p class="fish-auth-subtitle">
            {{ mode === 'login' ? '今天想怎么穿，从你的喜好说起。' : '建立你的风格档案，从喜欢的衣物开始。' }}
          </p>

          <div class="fish-auth-tabs" role="tablist" aria-label="账户操作">
            <button
              type="button"
              role="tab"
              aria-controls="fish-auth-form"
              :aria-selected="mode === 'login'"
              :class="{ active: mode === 'login' }"
              :disabled="submitting"
              @click="selectMode('login')"
            >
              <span>登录</span>
              <small aria-hidden="true">继续使用</small>
            </button>
            <button
              type="button"
              role="tab"
              aria-controls="fish-auth-form"
              :aria-selected="mode === 'register'"
              :class="{ active: mode === 'register' }"
              :disabled="submitting"
              @click="selectMode('register')"
            >
              <span>注册</span>
              <small aria-hidden="true">新建风格档案</small>
            </button>
          </div>

          <p v-if="assetsError" class="fish-auth-assets-error" role="status">{{ assetsError }}</p>

          <div v-if="errorMessage" class="fish-auth-error" role="alert">
            <CircleAlert :size="17" aria-hidden="true" />
            <span>{{ errorMessage }}</span>
          </div>

          <form id="fish-auth-form" class="fish-auth-form" @submit.prevent="submit">
            <label class="fish-auth-field" for="fish-username">
              <span>账号</span>
              <input
                id="fish-username"
                ref="usernameInput"
                v-model="form.username"
                name="username"
                autocomplete="username"
                autocapitalize="none"
                spellcheck="false"
                pattern="[a-z0-9][a-z0-9_\-]{2,31}"
                minlength="3"
                maxlength="32"
                required
                placeholder="请输入账号"
              />
              <small v-if="mode === 'register'">3–32 位小写字母、数字、下划线或连字符</small>
            </label>

            <label class="fish-auth-field" for="fish-password">
              <span>密码</span>
              <div class="fish-auth-password">
                <input
                  id="fish-password"
                  v-model="form.password"
                  name="password"
                  :type="showPassword ? 'text' : 'password'"
                  :autocomplete="mode === 'login' ? 'current-password' : 'new-password'"
                  minlength="8"
                  maxlength="72"
                  required
                  placeholder="至少 8 个字符"
                />
                <button
                  type="button"
                  class="fish-auth-eye"
                  :aria-label="showPassword ? '隐藏密码' : '显示密码'"
                  :title="showPassword ? '隐藏密码' : '显示密码'"
                  @click="showPassword = !showPassword"
                >
                  <EyeOff v-if="showPassword" :size="19" aria-hidden="true" />
                  <Eye v-else :size="19" aria-hidden="true" />
                </button>
              </div>
            </label>

            <label v-if="mode === 'register'" class="fish-auth-field" for="fish-confirm-password">
              <span>确认密码</span>
              <input
                id="fish-confirm-password"
                v-model="form.confirmPassword"
                name="confirmPassword"
                type="password"
                autocomplete="new-password"
                minlength="8"
                maxlength="72"
                required
                placeholder="再次输入密码"
              />
            </label>

            <button class="fish-auth-submit" type="submit" :disabled="submitting">
              <LoaderCircle v-if="submitting" class="spinning" :size="18" aria-hidden="true" />
              <LogIn v-else-if="mode === 'login'" :size="18" aria-hidden="true" />
              <UserRoundPlus v-else :size="18" aria-hidden="true" />
              {{ submitting ? '正在提交' : mode === 'login' ? '登录知己' : '注册账户' }}
            </button>
          </form>

          <p class="fish-auth-footnote">
            {{ mode === 'login' ? '登录后，你的衣橱和推荐记录会继续保留。' : '创建账户后会自动进入你的衣橱。' }}
          </p>
        </div>
      </section>

      <canvas ref="canvasRef" class="fish-auth-canvas" aria-hidden="true"></canvas>

      <footer class="fish-auth-footer" aria-live="polite">
        <span>双鱼登录 · 知己认证</span>
        <span>{{ statusText }}</span>
      </footer>

      <p v-if="phase === 'loading'" class="fish-auth-loading" role="status">正在加载双鱼…</p>
    </div>
  </main>
</template>

<style scoped>
.fish-auth {
  --fish-ink: #214d3b;
  --fish-muted: #627467;
  --fish-gold: #ac8951;
  --fish-line: #dce5dc;
  --fish-paper: #fbfcf8;
  --fish-danger: #8b3c34;
  position: relative;
  min-height: 100dvh;
  overflow-x: hidden;
  color: var(--fish-ink);
  background: var(--fish-paper);
  font-family: "Microsoft YaHei", "PingFang SC", sans-serif;
}

.fish-auth-stage {
  position: relative;
  min-height: 100dvh;
  overflow: hidden;
  isolation: isolate;
}

.fish-auth-header {
  position: absolute;
  top: 30px;
  right: 42px;
  left: 42px;
  z-index: 8;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
}

.fish-auth-brand {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.fish-auth-brand strong {
  font: 600 25px/1 "STKaiti", "KaiTi", serif;
  letter-spacing: 0.16em;
}

.fish-auth-brand small {
  color: var(--fish-muted);
  font: 9px/1 Georgia, serif;
  letter-spacing: 0.22em;
}

.fish-auth-text-button,
.fish-auth-eye,
.fish-auth-back {
  border: 0;
  background: transparent;
}

.fish-auth-text-button {
  padding: 9px 0;
  border-bottom: 1px solid #d6ded5;
  color: var(--fish-muted);
  font-size: 12px;
}

.fish-auth-text-button:hover:not(:disabled) {
  color: var(--fish-ink);
  border-color: var(--fish-gold);
}

.fish-auth-canvas {
  position: fixed;
  inset: 0;
  z-index: 3;
  width: 100%;
  height: 100%;
  pointer-events: none;
}

.fish-auth-intro {
  position: absolute;
  inset: 0;
  z-index: 4;
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 1;
  visibility: visible;
  transition: opacity 350ms ease, transform 350ms ease, visibility 350ms ease;
}

.fish-auth-intro.is-hidden {
  opacity: 0;
  visibility: hidden;
  transform: translateY(-12px);
  pointer-events: none;
}

.fish-auth-intro-inner {
  display: flex;
  flex-direction: column;
  align-items: center;
  transform: translateY(38px);
}

.fish-auth-logo-trigger {
  width: 330px;
  height: 310px;
  border: 0;
  border-radius: 50%;
  background: transparent;
}

.fish-auth-logo-trigger:hover:not(:disabled) {
  background: radial-gradient(ellipse, #e9f0e520, transparent 65%);
}

.fish-auth-wordmark {
  margin: 6px 0 7px;
  padding-left: 12px;
  font: 58px/1 "STKaiti", "KaiTi", serif;
  letter-spacing: 0.2em;
}

.fish-auth-roman {
  padding-left: 9px;
  font: 11px/1 Georgia, serif;
  letter-spacing: 0.82em;
}

.fish-auth-invitation {
  display: flex;
  align-items: center;
  gap: 14px;
  margin: 29px 0 0;
  color: var(--fish-muted);
  font-size: 12px;
  letter-spacing: 0.18em;
}

.fish-auth-invitation::before,
.fish-auth-invitation::after {
  width: 24px;
  height: 1px;
  background: #cbb88e;
  content: "";
}

.fish-auth-note {
  margin: 15px 0 0;
  color: #788477;
  font-size: 11px;
  letter-spacing: 0.08em;
}

.fish-auth-login {
  position: absolute;
  top: 50%;
  left: 50%;
  z-index: 5;
  width: min(390px, calc(100% - 56px));
  max-height: calc(100dvh - 116px);
  overflow-y: auto;
  opacity: 0;
  visibility: hidden;
  pointer-events: none;
  transform: translate(-50%, calc(-50% + 18px));
  transition: opacity 700ms cubic-bezier(.22, 1, .36, 1), transform 700ms cubic-bezier(.22, 1, .36, 1), visibility 700ms;
  scrollbar-color: #c8d3c8 transparent;
}

.fish-auth-login.is-visible {
  opacity: 1;
  visibility: visible;
  pointer-events: auto;
  transform: translate(-50%, -50%);
}

.fish-auth-login-inner {
  padding: 22px 0 34px;
}

.fish-auth-login h1 {
  margin: 0 0 14px;
  text-align: center;
  font: 400 clamp(32px, 5vw, 42px)/1.15 "STKaiti", "KaiTi", serif;
  letter-spacing: 0.12em;
}

.fish-auth-subtitle {
  margin: 0 0 30px;
  color: var(--fish-muted);
  text-align: center;
  font-size: 12px;
  letter-spacing: 0.1em;
}

.fish-auth-tabs {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 18px;
  margin: 0 0 23px;
  border-bottom: 1px solid var(--fish-line);
}

.fish-auth-tabs button {
  position: relative;
  display: flex;
  min-height: 44px;
  flex-direction: column;
  align-items: flex-start;
  gap: 3px;
  border: 0;
  padding: 0 4px 9px;
  color: var(--fish-muted);
  background: transparent;
  text-align: left;
}

.fish-auth-tabs button::after {
  position: absolute;
  right: 0;
  bottom: -1px;
  left: 0;
  height: 2px;
  background: var(--fish-ink);
  content: "";
  opacity: 0;
  transform: scaleX(0.35);
  transform-origin: left center;
  transition: opacity 180ms ease, transform 180ms ease;
}

.fish-auth-tabs button.active {
  color: var(--fish-ink);
}

.fish-auth-tabs button.active::after {
  opacity: 1;
  transform: scaleX(1);
}

.fish-auth-tabs button small {
  color: var(--fish-muted);
  font-size: 10px;
}

.fish-auth-tabs button.active small {
  color: var(--fish-gold);
}

.fish-auth-assets-error,
.fish-auth-error {
  display: flex;
  align-items: flex-start;
  gap: 9px;
  margin: 0 0 16px;
  padding: 11px 13px;
  border: 1px solid color-mix(in srgb, var(--fish-gold) 45%, var(--fish-line));
  border-radius: 8px;
  font-size: 12px;
  line-height: 1.6;
}

.fish-auth-assets-error {
  color: var(--fish-muted);
  background: #f4f5ee;
}

.fish-auth-error {
  color: var(--fish-danger);
  background: #f7eee1;
}

.fish-auth-form {
  display: grid;
  gap: 17px;
}

.fish-auth-field {
  display: grid;
  gap: 9px;
  color: var(--fish-ink);
  font-size: 12px;
  font-weight: 600;
}

.fish-auth-field > small {
  margin-top: -3px;
  color: var(--fish-muted);
  font-size: 10px;
  font-weight: 400;
}

.fish-auth-field input {
  width: 100%;
  min-height: 48px;
  border: 1px solid var(--fish-line);
  border-radius: 5px;
  padding: 13px 15px;
  outline: 0;
  color: var(--fish-ink);
  background: #ffffffeb;
  caret-color: var(--fish-ink);
  transition: border-color 180ms ease, box-shadow 180ms ease, background-color 180ms ease;
}

.fish-auth-field input::placeholder {
  color: #788575;
}

.fish-auth-field input:focus {
  border-color: var(--fish-ink);
  background: #fff;
  box-shadow: 0 0 0 3px rgba(33, 77, 59, .11);
}

.fish-auth-password {
  position: relative;
}

.fish-auth-password input {
  padding-right: 52px;
}

.fish-auth-eye {
  position: absolute;
  top: 5px;
  right: 6px;
  bottom: 5px;
  padding: 7px;
  color: var(--fish-muted);
}

.fish-auth-eye:hover:not(:disabled) {
  color: var(--fish-ink);
}

.fish-auth-submit {
  display: inline-flex;
  min-height: 49px;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border: 1px solid var(--fish-ink);
  border-radius: 5px;
  margin-top: 2px;
  color: #fff;
  background: var(--fish-ink);
  font-size: 13px;
  font-weight: 700;
  box-shadow: 0 10px 24px rgba(33, 77, 59, .14);
}

.fish-auth-submit:hover:not(:disabled) {
  border-color: #316147;
  background: #316147;
  box-shadow: 0 13px 28px rgba(33, 77, 59, .22);
}

.fish-auth-footnote {
  margin: 18px 0 0;
  color: var(--fish-muted);
  text-align: center;
  font-size: 10px;
  line-height: 1.8;
}

.fish-auth-footer {
  position: absolute;
  right: 42px;
  bottom: 24px;
  left: 42px;
  z-index: 7;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  color: var(--fish-muted);
  font-size: 11px;
}

.fish-auth-footer span:first-child {
  letter-spacing: 0.08em;
}

.fish-auth-footer span:last-child {
  color: var(--fish-gold);
}

.fish-auth-loading {
  position: fixed;
  top: 45%;
  right: 0;
  left: 0;
  z-index: 9;
  margin: 0;
  padding: 25px;
  color: var(--fish-muted);
  background: var(--fish-paper);
  text-align: center;
}

.fish-auth button:focus-visible,
.fish-auth input:focus-visible {
  outline: 2px solid var(--fish-gold);
  outline-offset: 5px;
}

.fish-auth button:disabled {
  cursor: wait;
}

@media (max-width: 650px) {
  .fish-auth-header {
    top: 22px;
    right: 24px;
    left: 24px;
  }

  .fish-auth-brand strong {
    font-size: 22px;
  }

  .fish-auth-logo-trigger {
    width: 280px;
    height: 280px;
  }

  .fish-auth-intro-inner {
    transform: translateY(20px);
  }

  .fish-auth-wordmark {
    font-size: 49px;
  }

  .fish-auth-invitation {
    gap: 10px;
    margin-top: 24px;
    letter-spacing: 0.1em;
  }

  .fish-auth-note {
    font-size: 10px;
  }

  .fish-auth-login {
    width: min(350px, calc(100% - 64px));
    max-height: calc(100dvh - 100px);
  }

  .fish-auth-login-inner {
    padding-top: 15px;
  }

  .fish-auth-login h1 {
    font-size: 34px;
  }

  .fish-auth-subtitle {
    margin-bottom: 24px;
  }

  .fish-auth-footer {
    right: 20px;
    bottom: 14px;
    left: 20px;
    align-items: flex-end;
    font-size: 10px;
  }

  .fish-auth-footer span:last-child {
    text-align: right;
  }
}

@media (max-height: 670px) {
  .fish-auth-login {
    top: 48%;
  }

  .fish-auth-login-inner {
    padding-top: 8px;
  }

  .fish-auth-login h1 {
    margin-bottom: 8px;
    font-size: 29px;
  }

  .fish-auth-subtitle {
    margin-bottom: 18px;
  }

  .fish-auth-form {
    gap: 12px;
  }

  .fish-auth-footer {
    display: none;
  }

  .fish-auth-intro-inner {
    transform: translateY(0);
  }

  .fish-auth-logo-trigger {
    height: 230px;
  }

  .fish-auth-wordmark {
    font-size: 42px;
  }

  .fish-auth-invitation {
    margin-top: 18px;
  }

  .fish-auth-note {
    display: none;
  }
}

@media (prefers-reduced-motion: reduce) {
  .fish-auth *,
  .fish-auth *::before,
  .fish-auth *::after {
    transition-duration: 0.01ms !important;
    animation-duration: 0.01ms !important;
  }
}
</style>
