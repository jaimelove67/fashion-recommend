<script setup>
import { ref } from 'vue'
import { ArrowRight, ArrowUpRight, Sprout } from '@lucide/vue'
import RippleDistortion from '../components/RippleDistortion.vue'

const emit = defineEmits(['enter-zhiji'])
const comingSoonVisible = ref(false)

function enterZhiji() {
  comingSoonVisible.value = false
  emit('enter-zhiji')
}

function showComingSoon() {
  comingSoonVisible.value = true
}
</script>

<template>
  <main class="web-share-home" aria-labelledby="web-share-home-title">
    <section class="web-share-frame">
      <RippleDistortion
        class="web-share-ripple"
        :brush-size="170"
        :strength="0.26"
        :spread="4.5"
        :fade="3.2"
        :spacing="22"
        trigger="both"
        :click-strength="2.4"
      />
      <div class="web-share-content">
        <header class="web-share-heading">
          <h1 id="web-share-home-title" aria-label="jaime的web分享">
            <span class="web-share-name">jaime</span><span class="web-share-title">的 web 分享</span>
          </h1>
          <p>选择一个入口，开始探索。</p>
        </header>
        <div class="web-share-actions">
          <button type="button" class="web-share-option web-share-option-primary" aria-label="知己" @click="enterZhiji">
            <img class="web-share-logo" src="/assets/zhiji-logo.png" alt="" width="112" height="112" />
            <span class="web-share-entry-copy">
              <strong>知己</strong>
              <span class="web-share-entry-description">日常穿搭，从这里开始</span>
              <span class="web-share-entry-action">进入知己<ArrowUpRight :size="16" aria-hidden="true" /></span>
            </span>
          </button>
          <button
            type="button"
            class="web-share-option web-share-option-secondary"
            aria-label="敬请期待"
            :aria-describedby="comingSoonVisible ? 'web-share-coming-soon' : undefined"
            @click="showComingSoon"
          >
            <Sprout class="web-share-sprout" :size="28" :stroke-width="1.4" aria-hidden="true" />
            <span class="web-share-soon-copy">
              <strong>敬请期待</strong>
              <span>更多分享，慢慢到来</span>
            </span>
            <ArrowRight class="web-share-soon-arrow" :size="16" aria-hidden="true" />
          </button>
        </div>
        <p id="web-share-coming-soon" class="web-share-status" role="status" aria-live="polite">
          {{ comingSoonVisible ? '该入口正在准备中，敬请期待。' : '' }}
        </p>
      </div>
    </section>
  </main>
</template>

<style scoped>
.web-share-home {
  --share-ink: #344b3b;
  --share-muted: #63705e;
  --share-terracotta: #b6613d;
  --share-paper: #fffdf8;
  display: grid;
  overflow: clip;
  min-height: 100dvh;
  place-items: center;
  padding: clamp(40px, 7vh, 80px) 32px clamp(100px, 17vh, 180px);
  color: var(--share-ink);
  background: #fbf2e3 url('/assets/web-share-background.png') center / cover no-repeat;
}

.web-share-frame {
  position: relative;
  width: min(100%, 700px);
  isolation: isolate;
}

.web-share-ripple {
  inset: -48px;
  z-index: 0;
  width: calc(100% + 96px);
  height: calc(100% + 96px);
  opacity: .28;
}

.web-share-content {
  position: relative;
  z-index: 1;
  display: grid;
  justify-items: center;
  width: 100%;
  text-align: center;
}

.web-share-heading h1 {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  justify-content: center;
  gap: 14px;
  margin: 0;
  font-family: var(--font-display);
  font-size: clamp(32px, 3.2vw, 44px);
  font-weight: 400;
  line-height: 1.35;
}

.web-share-name {
  color: var(--share-terracotta);
  font-family: Georgia, 'Times New Roman', serif;
  font-size: clamp(48px, 4.8vw, 66px);
  font-style: italic;
  letter-spacing: -.04em;
}

.web-share-title {
  letter-spacing: .025em;
}

.web-share-heading p {
  margin: 12px 0 0;
  color: var(--share-muted);
  font-size: 14px;
  line-height: 1.8;
  letter-spacing: .06em;
}

.web-share-actions {
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) minmax(0, 1fr);
  align-items: center;
  gap: 24px;
  width: 100%;
  margin-top: 40px;
}

.web-share-option {
  min-width: 0;
  border: 0;
  border-radius: 16px;
  color: inherit;
  font: inherit;
  text-align: left;
  transition: transform 240ms cubic-bezier(.22, 1, .36, 1), box-shadow 240ms ease, background-color 200ms ease;
}

.web-share-option-primary {
  display: grid;
  grid-template-columns: 112px minmax(0, 1fr);
  align-items: center;
  gap: 20px;
  min-height: 200px;
  padding: 28px;
  background: var(--share-paper);
  box-shadow: 0 12px 36px rgb(91 74 43 / 10%);
}

.web-share-option-primary:hover {
  transform: translateY(-4px);
  background: #fffefb;
  box-shadow: 0 20px 44px rgb(91 74 43 / 15%);
}

.web-share-logo {
  width: 112px;
  height: 112px;
  object-fit: contain;
  mix-blend-mode: multiply;
}

.web-share-entry-copy,
.web-share-soon-copy {
  display: grid;
  min-width: 0;
}

.web-share-entry-copy strong {
  font-family: var(--font-display);
  font-size: 30px;
  font-weight: 600;
  line-height: 1.4;
  letter-spacing: .08em;
}

.web-share-entry-description {
  margin-top: 5px;
  color: var(--share-muted);
  font-size: 12px;
  line-height: 1.8;
}

.web-share-entry-action {
  display: flex;
  align-items: center;
  gap: 10px;
  width: fit-content;
  margin-top: 22px;
  color: #275548;
  font-size: 13px;
  font-weight: 500;
}

.web-share-entry-action svg {
  transition: transform 240ms cubic-bezier(.22, 1, .36, 1);
}

.web-share-option-primary:hover .web-share-entry-action svg {
  transform: translate(2px, -2px);
}

.web-share-option-secondary {
  display: flex;
  min-height: 164px;
  align-items: center;
  justify-content: center;
  gap: 16px;
  border: 1px dashed #a6ac96;
  padding: 24px;
  background: rgb(255 253 248 / 32%);
}

.web-share-option-secondary:hover {
  background: rgb(255 253 248 / 70%);
}

.web-share-sprout {
  flex: 0 0 auto;
  color: #6b7c58;
}

.web-share-soon-copy strong {
  font-family: var(--font-display);
  font-size: 22px;
  font-weight: 400;
  line-height: 1.6;
  letter-spacing: .04em;
}

.web-share-soon-copy > span {
  margin-top: 4px;
  color: var(--share-muted);
  font-size: 12px;
  line-height: 1.8;
}

.web-share-soon-arrow {
  display: none;
}

.web-share-option:focus-visible {
  outline: 2px solid var(--share-ink);
  outline-offset: 6px;
}

.web-share-status {
  min-height: 24px;
  margin: 24px 0 0;
  color: var(--share-muted);
  font-size: 13px;
  line-height: 24px;
}

@media (max-width: 640px) {
  .web-share-home {
    padding: 40px 24px 130px;
  }

  .web-share-heading h1 {
    gap: 9px;
    font-size: 28px;
  }

  .web-share-name {
    font-size: 48px;
  }

  .web-share-heading p {
    margin-top: 10px;
    font-size: 13px;
  }

  .web-share-actions {
    grid-template-columns: minmax(0, 1fr);
    gap: 14px;
    max-width: 400px;
    margin-top: 30px;
  }

  .web-share-option-primary {
    grid-template-columns: 100px minmax(0, 1fr);
    gap: 18px;
    min-height: 168px;
    padding: 24px;
  }

  .web-share-logo {
    width: 100px;
    height: 100px;
  }

  .web-share-entry-copy strong {
    font-size: 27px;
  }

  .web-share-entry-action {
    margin-top: 16px;
  }

  .web-share-option-secondary {
    min-height: 88px;
    justify-content: flex-start;
    gap: 16px;
    padding: 17px 24px;
  }

  .web-share-sprout {
    width: 24px;
    height: 24px;
  }

  .web-share-soon-copy strong {
    font-size: 19px;
  }

  .web-share-soon-copy > span {
    margin-top: 0;
  }

  .web-share-soon-arrow {
    display: block;
    flex: 0 0 auto;
    margin-left: auto;
    color: var(--share-muted);
  }
}

@media (max-width: 360px) {
  .web-share-home {
    padding-right: 20px;
    padding-left: 20px;
  }

  .web-share-heading h1 {
    font-size: 25px;
  }

  .web-share-name {
    font-size: 43px;
  }

  .web-share-option-primary {
    grid-template-columns: 86px minmax(0, 1fr);
    gap: 14px;
    padding: 22px;
  }

  .web-share-logo {
    width: 86px;
    height: 86px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .web-share-option,
  .web-share-entry-action svg {
    transition: none;
  }

  .web-share-option-primary:hover,
  .web-share-option-primary:hover .web-share-entry-action svg {
    transform: none;
  }
}
</style>
