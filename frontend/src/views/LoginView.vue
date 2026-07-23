<script setup>
import { computed, reactive, ref } from 'vue'
import { CircleAlert, Eye, EyeOff, LoaderCircle, LockKeyhole, LogIn, UserRoundPlus } from '@lucide/vue'

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

function selectMode(nextMode) {
  if (submitting.value) return
  mode.value = nextMode
  showPassword.value = false
  validationError.value = ''
  props.app.state.authError = ''
  form.password = ''
  form.confirmPassword = ''
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
</script>

<template>
  <main class="auth-view">
    <section class="auth-visual" aria-label="知己穿搭视觉">
      <img src="/assets/look-tailoring.jpg" alt="简洁剪裁的城市穿搭" />
      <div class="auth-visual-copy">
        <p>WEAVESELF / 知己</p>
        <h1>穿得像自己。</h1>
        <span>你的衣橱、偏好与推荐记录，只在登录后呈现。</span>
      </div>
    </section>

    <section class="auth-panel" :aria-labelledby="mode === 'login' ? 'login-title' : 'register-title'">
      <div class="auth-brand"><strong>知己</strong><span>WEAVESELF</span></div>

      <div class="auth-tabs" role="tablist" aria-label="账户操作">
        <button
          type="button"
          role="tab"
          :aria-selected="mode === 'login'"
          :class="{ active: mode === 'login' }"
          :disabled="submitting"
          @click="selectMode('login')"
        >登录</button>
        <button
          type="button"
          role="tab"
          :aria-selected="mode === 'register'"
          :class="{ active: mode === 'register' }"
          :disabled="submitting"
          @click="selectMode('register')"
        >注册</button>
      </div>

      <header class="auth-heading">
        <p>{{ mode === 'login' ? '欢迎回来' : '建立你的账户' }}</p>
        <h2 v-if="mode === 'login'" id="login-title">登录知己</h2>
        <h2 v-else id="register-title">注册知己</h2>
      </header>

      <div v-if="errorMessage" class="auth-error" role="alert">
        <CircleAlert :size="17" aria-hidden="true" />
        <span>{{ errorMessage }}</span>
      </div>

      <form class="auth-form" @submit.prevent="submit">
        <label>
          <span>用户名</span>
          <div class="auth-input">
            <UserRoundPlus :size="18" aria-hidden="true" />
            <input
              v-model="form.username"
              name="username"
              autocomplete="username"
              autocapitalize="none"
              spellcheck="false"
              pattern="[a-z0-9][a-z0-9_-]{2,31}"
              minlength="3"
              maxlength="32"
              required
              placeholder="例如 lin_xia"
            />
          </div>
          <small v-if="mode === 'register'">3-32 位小写字母、数字、下划线或连字符</small>
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

        <label v-if="mode === 'register'">
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
              required
              placeholder="再次输入密码"
            />
          </div>
        </label>

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

.auth-visual::after {
  position: absolute;
  inset: 0;
  background: rgba(20, 21, 19, .38);
  content: '';
}

.auth-visual img {
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

.auth-visual-copy p,
.auth-heading p {
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
  width: 100%;
  grid-template-columns: 1fr 1fr;
  border-bottom: 1px solid var(--line);
  margin-bottom: 34px;
}

.auth-tabs button {
  min-height: 43px;
  border: 0;
  border-bottom: 2px solid transparent;
  color: var(--muted);
  background: transparent;
  font-size: 13px;
  font-weight: 700;
}

.auth-tabs button.active {
  border-bottom-color: var(--accent);
  color: var(--ink);
}

.auth-heading {
  margin-bottom: 26px;
}

.auth-heading h2 {
  margin: 0;
  color: var(--ink);
  font: 700 34px/1.16 var(--serif);
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
  color: var(--muted);
  font-size: 10px;
  font-weight: 400;
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
}

.auth-input:focus-within {
  border-color: var(--accent);
  box-shadow: 0 0 0 3px var(--accent-soft);
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

@media (max-width: 820px) {
  .auth-view {
    grid-template-columns: 1fr;
  }

  .auth-visual {
    display: none;
  }

  .auth-panel {
    width: min(100%, 520px);
    min-height: 100vh;
  }
}

@media (max-width: 520px) {
  .auth-panel {
    justify-content: flex-start;
    padding: 30px 24px 48px;
  }

  .auth-brand {
    margin-bottom: 54px;
  }

  .auth-heading h2 {
    font-size: 30px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .spinning {
    animation-duration: .01ms;
    animation-iteration-count: 1;
  }
}
</style>
