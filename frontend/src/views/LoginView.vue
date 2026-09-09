<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { CircleAlert, Eye, EyeOff, LoaderCircle, LockKeyhole, LogIn, UserRoundPlus } from '@lucide/vue'

const AUTH_SWITCH_DURATION = 240

const props = defineProps({
  app: {
    type: Object,
    required: true
  }
})

const mode = ref('login')
const showPassword = ref(false)
const validationError = ref('')
const form = reactive({ username: '', password: '', confirmPassword: '' })
const submitting = computed(() => props.app.state.authSubmitting)
const errorMessage = computed(() => validationError.value || props.app.state.authError)
const usernameInput = ref(null)
let focusTimer = 0
let switchToken = 0

function clearFocusTimer() {
  if (!focusTimer) return
  window.clearTimeout(focusTimer)
  focusTimer = 0
}

function focusFirstEditable() {
  const input = usernameInput.value
  if (!input || input.disabled) return
  input.focus({ preventScroll: true })
}

function scheduleFocus() {
  clearFocusTimer()
  const token = ++switchToken
  nextTick(() => {
    focusTimer = window.setTimeout(() => {
      focusTimer = 0
      if (token !== switchToken || submitting.value) return
      const activeElement = document.activeElement
      if (activeElement?.matches?.('input, textarea, select')) return
      focusFirstEditable()
    }, AUTH_SWITCH_DURATION)
  })
}

function hideConfirmDuringExit(element) {
  element.setAttribute('aria-hidden', 'true')
  const input = element.querySelector('input[name="confirmPassword"]')
  if (input) input.disabled = true
}

function selectMode(nextMode) {
  if (submitting.value || nextMode === mode.value) return
  mode.value = nextMode
  showPassword.value = false
  validationError.value = ''
  props.app.state.authError = ''
  form.password = ''
  form.confirmPassword = ''
  scheduleFocus()
}

async function submit() {
  validationError.value = ''
  const username = form.username.trim()
  const passwordBytes = new TextEncoder().encode(form.password).length
  if (passwordBytes > 72) {
    validationError.value = '密码的 UTF-8 编码不能超过 72 字节。'
    return
  }
  if (mode.value === 'register' && form.password !== form.confirmPassword) {
    validationError.value = '两次输入的密码不一致。'
    return
  }
  const credentials = { username, password: form.password }
  if (mode.value === 'register') await props.app.register(credentials)
  else await props.app.login(credentials)
}

onMounted(() => {
  nextTick(focusFirstEditable)
})

onBeforeUnmount(() => {
  clearFocusTimer()
  switchToken += 1
})
</script>

<template>
  <main class="auth-view">
    <section class="auth-visual" aria-label="知己穿搭视觉">
      <div class="auth-visual-media">
        <img src="/assets/look-tailoring.jpg" alt="简洁剪裁的城市穿搭" fetchpriority="high" />
      </div>
      <div class="auth-visual-copy">
        <p>WEAVESELF / 知己</p>
        <h1>穿得像自己。</h1>
        <span>你的衣橱、偏好与推荐记录，只在登录后呈现。</span>
      </div>
    </section>

    <section class="auth-panel" aria-label="账户认证">
      <div class="auth-brand"><strong>知己</strong><span>WEAVESELF</span></div>

      <div class="auth-tabs" role="tablist" aria-label="账户操作">
        <button
          type="button"
          role="tab"
          aria-controls="auth-form"
          :aria-selected="mode === 'login'"
          :class="{ active: mode === 'login' }"
          :disabled="submitting"
          @click="selectMode('login')"
        >
          <span class="auth-tab-title">登录</span>
          <small aria-hidden="true">继续已有风格</small>
        </button>
        <button
          type="button"
          role="tab"
          aria-controls="auth-form"
          :aria-selected="mode === 'register'"
          :class="{ active: mode === 'register' }"
          :disabled="submitting"
          @click="selectMode('register')"
        >
          <span class="auth-tab-title">注册</span>
          <small aria-hidden="true">开始新的风格记录</small>
        </button>
      </div>

      <div v-if="errorMessage" class="auth-error" role="alert">
        <CircleAlert :size="17" aria-hidden="true" />
        <span>{{ errorMessage }}</span>
      </div>

      <form id="auth-form" class="auth-form" @submit.prevent="submit">
        <label>
          <span>用户名</span>
          <div class="auth-input">
            <UserRoundPlus :size="18" aria-hidden="true" />
            <input
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
              placeholder="例如 lin_xia"
            />
          </div>
          <small :class="{ 'is-hidden': mode !== 'register' }">3-32 位小写字母、数字、下划线或连字符</small>
        </label>

        <label>
          <span>密码</span>
          <div class="auth-input">
            <LockKeyhole :size="18" aria-hidden="true" />
            <input
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
              :aria-label="showPassword ? '隐藏密码' : '显示密码'"
              :title="showPassword ? '隐藏密码' : '显示密码'"
              @click="showPassword = !showPassword"
            >
              <EyeOff v-if="showPassword" :size="18" aria-hidden="true" />
              <Eye v-else :size="18" aria-hidden="true" />
            </button>
          </div>
        </label>

        <div class="auth-confirm-slot" :class="{ expanded: mode === 'register' }" :aria-hidden="mode !== 'register' ? 'true' : undefined">
          <Transition name="auth-confirm" @before-leave="hideConfirmDuringExit">
            <label v-if="mode === 'register'" key="confirm-password" :aria-hidden="mode !== 'register' ? 'true' : undefined">
              <span>确认密码</span>
              <div class="auth-input">
                <LockKeyhole :size="18" aria-hidden="true" />
                <input
                  v-model="form.confirmPassword"
                  name="confirmPassword"
                  :type="showPassword ? 'text' : 'password'"
                  autocomplete="new-password"
                  minlength="8"
                  maxlength="72"
                  :disabled="mode !== 'register'"
                  required
                  placeholder="再次输入密码"
                />
              </div>
            </label>
            <p v-else key="continue-style" class="auth-mode-note" aria-hidden="true">登录后继续已有风格记录</p>
          </Transition>
        </div>

        <button class="auth-submit" type="submit" :disabled="submitting">
          <LoaderCircle v-if="submitting" class="spinning" :size="18" aria-hidden="true" />
          <LogIn v-else-if="mode === 'login'" :size="18" aria-hidden="true" />
          <UserRoundPlus v-else :size="18" aria-hidden="true" />
          {{ submitting ? '请稍候' : mode === 'login' ? '登录' : '创建账户' }}
        </button>
      </form>
    </section>
  </main>
</template>

<style scoped>
.auth-view {
  display: grid;
  min-height: 100vh;
  grid-template-columns: minmax(360px, 1.08fr) minmax(430px, .92fr);
  background: var(--bg);
}

.auth-visual {
  position: relative;
  min-height: 100vh;
  overflow: hidden;
  background: #242522;
}

.auth-visual-media {
  position: absolute;
  inset: 0;
}

.auth-visual::after {
  position: absolute;
  inset: 0;
  z-index: 1;
  background: rgba(20, 21, 19, .38);
  content: '';
}

.auth-visual-media img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.auth-visual-copy {
  position: absolute;
  right: 48px;
  bottom: 46px;
  left: 48px;
  z-index: 1;
  color: #fff;
}

.auth-visual-copy p {
  margin: 0 0 9px;
  color: var(--accent);
  font-size: 11px;
  font-weight: 800;
}

.auth-visual-copy h1 {
  margin: 0 0 13px;
  font: 700 46px/1.08 var(--serif);
}

.auth-visual-copy span {
  display: block;
  max-width: 470px;
  color: rgba(255, 255, 255, .84);
  font-size: 14px;
  line-height: 1.7;
}

.auth-panel {
  display: flex;
  width: min(100%, 520px);
  justify-self: center;
  flex-direction: column;
  justify-content: center;
  padding: 56px 48px;
}

.auth-brand {
  display: flex;
  align-items: baseline;
  gap: 9px;
  margin-bottom: 54px;
}

.auth-brand strong {
  font: 700 25px/1 var(--serif);
}

.auth-brand span {
  color: var(--muted);
  font-size: 9px;
  font-weight: 800;
}

.auth-tabs {
  display: grid;
  position: relative;
  width: 100%;
  grid-template-columns: 1fr 1fr;
  border-bottom: 1px solid var(--line);
  margin-bottom: 34px;
}

.auth-tabs button {
  position: relative;
  display: flex;
  min-height: 54px;
  flex-direction: column;
  align-items: flex-start;
  justify-content: center;
  gap: 2px;
  border: 0;
  padding: 0 4px 8px;
  color: var(--muted);
  background: transparent;
  font-size: 13px;
  font-weight: 700;
  text-align: left;
  transition: color 180ms ease;
}

.auth-tabs button::after {
  position: absolute;
  right: 0;
  bottom: -1px;
  left: 0;
  height: 2px;
  transform: scaleX(0);
  transform-origin: left center;
  border-radius: 0 2px 2px 0;
  background: var(--accent-strong);
  content: '';
}

.auth-tabs button::before {
  position: absolute;
  bottom: -3px;
  left: 0;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--accent);
  content: '';
  opacity: 0;
}

.auth-tabs button.active::after {
  animation: auth-stitch-line 240ms cubic-bezier(.22, .8, .32, 1) both;
}

.auth-tabs button.active::before {
  animation: auth-stitch-dot 240ms cubic-bezier(.22, .8, .32, 1) both;
}

.auth-tabs button.active {
  color: var(--ink);
}

.auth-tab-title {
  line-height: 1.2;
}

.auth-tabs button small {
  color: var(--muted);
  font-size: 10px;
  font-weight: 500;
  line-height: 1.2;
  transition: color 180ms ease;
}

.auth-tabs button.active small {
  color: var(--accent-strong);
}

.auth-tabs button:hover:not(:disabled) {
  color: var(--ink);
}

@keyframes auth-stitch-line {
  from { transform: scaleX(0); }
  to { transform: scaleX(1); }
}

@keyframes auth-stitch-dot {
  0% {
    left: 0;
    opacity: 0;
    transform: scale(.55);
  }
  18% {
    opacity: 1;
    transform: scale(1);
  }
  100% {
    left: calc(100% - 6px);
    opacity: 1;
    transform: scale(.82);
  }
}

.auth-error {
  display: flex;
  align-items: flex-start;
  gap: 9px;
  border-left: 3px solid var(--coral);
  margin-bottom: 20px;
  padding: 11px 13px;
  color: #822f25;
  background: #fff0ed;
  font-size: 12px;
  line-height: 1.5;
}

.auth-error svg {
  flex: 0 0 auto;
  margin-top: 1px;
}

.auth-form {
  display: grid;
  gap: 19px;
}

.auth-form label {
  display: grid;
  gap: 8px;
  color: var(--ink);
  font-size: 12px;
  font-weight: 700;
}

.auth-form label > small {
  min-height: 13px;
  color: var(--muted);
  font-size: 10px;
  font-weight: 400;
  line-height: 1.3;
  transition: opacity 180ms ease;
}

.auth-form label > small.is-hidden {
  visibility: hidden;
  opacity: 0;
}

.auth-input {
  display: grid;
  min-height: 47px;
  align-items: center;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 10px;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  padding: 0 13px;
  background: var(--surface);
  transition: border-color 180ms ease, box-shadow 180ms ease, background-color 180ms ease;
}

.auth-input:focus-within {
  border-color: var(--accent-strong);
  box-shadow: 0 0 0 3px rgba(169, 133, 86, .2), 0 5px 18px rgba(76, 57, 35, .06);
  background: #fffdf9;
}

.auth-input > svg {
  color: var(--muted);
}

.auth-input input {
  min-width: 0;
  height: 45px;
  border: 0;
  outline: 0;
  color: var(--ink);
  background: transparent;
  font: inherit;
  font-weight: 500;
}

.auth-input button {
  display: grid;
  width: 32px;
  height: 32px;
  place-items: center;
  border: 0;
  color: var(--muted);
  background: transparent;
}

.auth-confirm-slot {
  display: flex;
  position: relative;
  height: 75px;
  flex-direction: column;
  justify-content: flex-start;
  overflow: hidden;
}

.auth-confirm-enter-active,
.auth-confirm-leave-active {
  position: absolute;
  inset: 0;
  width: 100%;
  max-height: 75px;
  overflow: hidden;
  transform-origin: top center;
  transition: max-height 240ms ease, opacity 240ms ease, transform 240ms cubic-bezier(.22, .8, .32, 1);
}

.auth-confirm-enter-from {
  max-height: 0;
  opacity: 0;
  transform: translateY(-10px) scaleY(.86);
}

.auth-confirm-leave-to {
  max-height: 0;
  opacity: 0;
  transform: translateY(-7px) scaleY(.9);
}

.auth-mode-note {
  margin: 12px 0 0;
  color: var(--muted);
  font-size: 11px;
  font-weight: 500;
  line-height: 1.4;
}

.auth-submit {
  display: inline-flex;
  width: 100%;
  min-height: 47px;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border: 1px solid var(--ink);
  border-radius: var(--radius);
  margin-top: 5px;
  color: #fff;
  background: var(--ink);
  font-size: 13px;
  font-weight: 800;
  transition: border-color 180ms ease, box-shadow 180ms ease, transform 120ms ease;
}

.auth-submit:hover:not(:disabled) {
  border-color: var(--accent);
  box-shadow: 0 0 0 1px var(--accent);
}

.auth-submit:active:not(:disabled) {
  transform: translateY(1px);
}

.auth-submit:disabled,
.auth-tabs button:disabled {
  cursor: wait;
  opacity: .62;
}

.spinning {
  animation: spin .9s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

@media (min-width: 681px) and (max-width: 820px) {
  .auth-view {
    grid-template-columns: minmax(0, .86fr) minmax(400px, 1.14fr);
  }

  .auth-panel {
    width: 100%;
    padding: 44px 32px;
  }

  .auth-visual-copy {
    right: 32px;
    bottom: 34px;
    left: 32px;
  }

  .auth-visual-copy h1 {
    font-size: 36px;
  }

  .auth-visual-copy span {
    font-size: 12px;
  }
}

@media (max-width: 680px) {
  .auth-view {
    grid-template-columns: 1fr;
    min-height: 100svh;
  }

  .auth-visual {
    height: clamp(180px, 28svh, 220px);
    min-height: 180px;
  }

  .auth-visual-copy {
    right: 24px;
    bottom: 21px;
    left: 24px;
  }

  .auth-visual-copy p {
    margin-bottom: 6px;
    font-size: 10px;
  }

  .auth-visual-copy h1 {
    margin-bottom: 8px;
    font-size: 32px;
  }

  .auth-visual-copy span {
    max-width: 320px;
    font-size: 12px;
    line-height: 1.5;
  }

  .auth-panel {
    width: 100%;
    min-height: auto;
    justify-self: stretch;
    justify-content: flex-start;
    padding: 30px 24px 48px;
  }
}

@media (max-width: 520px) {
  .auth-panel {
    padding: 26px 20px 42px;
  }

  .auth-brand {
    margin-bottom: 38px;
  }

  .auth-tabs {
    margin-bottom: 28px;
  }

  .auth-tabs button {
    min-height: 52px;
    padding-bottom: 7px;
  }

  .auth-tabs button small {
    font-size: 9px;
  }

  .auth-form {
    gap: 16px;
  }
}

@media (max-width: 680px) and (max-height: 700px) {
  .auth-visual {
    display: none;
  }

  .auth-panel {
    min-height: 100svh;
  }
}

@media (prefers-reduced-motion: reduce) {
  .auth-tabs button::after,
  .auth-tabs button::before {
    animation: none;
  }

  .auth-tabs button.active::after {
    transform: scaleX(1);
  }

  .auth-tabs button.active::before {
    display: none;
  }

  .auth-confirm-enter-active,
  .auth-confirm-leave-active,
  .auth-input,
  .auth-submit,
  .auth-form label > small,
  .auth-tabs button,
  .auth-tabs button small {
    transition-duration: .01ms !important;
    transition-delay: 0ms !important;
  }

  .auth-confirm-enter-from,
  .auth-confirm-leave-to {
    opacity: 1;
    max-height: none;
    transform: none;
  }

  .spinning {
    animation-duration: .01ms;
    animation-iteration-count: 1;
  }
}
</style>
