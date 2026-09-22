<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { gsap } from 'gsap'

const props = defineProps({
  logo: { type: String, required: true },
  logoAlt: { type: String, default: 'Logo' },
  items: { type: Array, default: () => [] },
  activeHref: { type: String, default: undefined },
  className: { type: String, default: '' },
  ease: { type: String, default: 'power3.out' },
  baseColor: { type: String, default: '#000000' },
  pillColor: { type: String, default: '#ffffff' },
  hoveredPillTextColor: { type: String, default: '#ffffff' },
  pillTextColor: { type: String, default: undefined },
  onMobileMenuClick: { type: Function, default: undefined },
  onItemClick: { type: Function, default: undefined },
  initialLoadAnimation: { type: Boolean, default: true }
})

const resolvedPillTextColor = computed(() => props.pillTextColor ?? props.baseColor)
const isMobileMenuOpen = ref(false)

const circleRefs = ref([])
const timelineRefs = ref([])
const activeTweenRefs = ref([])
const logoImgRef = ref(null)
const logoTweenRef = ref(null)
const hamburgerRef = ref(null)
const mobileMenuRef = ref(null)
const navItemsRef = ref(null)
const logoRef = ref(null)

watch(
  () => props.items,
  (items) => {
    circleRefs.value = new Array(items.length).fill(null)
    timelineRefs.value = new Array(items.length).fill(null)
    activeTweenRefs.value = new Array(items.length).fill(null)
  },
  { immediate: true, deep: true }
)

const cssVars = computed(() => ({
  '--base': props.baseColor,
  '--pill-bg': props.pillColor,
  '--hover-text': props.hoveredPillTextColor,
  '--pill-text': resolvedPillTextColor.value,
  '--nav-h': '46px',
  '--logo-size': '46px',
  '--pill-pad-x': '17px',
  '--pill-gap': '4px'
}))

const isExternalLink = (href) => Boolean(href) && (
  href.startsWith('http://') ||
  href.startsWith('https://') ||
  href.startsWith('//') ||
  href.startsWith('mailto:') ||
  href.startsWith('tel:')
)

const shouldUseButton = (item) => Boolean(props.onItemClick) && !isExternalLink(item?.href)

const setCircleRef = (el, index) => {
  if (circleRefs.value.length > index) circleRefs.value[index] = el
}

const layout = () => {
  circleRefs.value.forEach((circle, index) => {
    if (!circle?.parentElement) return

    const pill = circle.parentElement
    const rect = pill.getBoundingClientRect()
    const { width: w, height: h } = rect
    const radius = ((w * w) / 4 + h * h) / (2 * h)
    const diameter = Math.ceil(2 * radius) + 2
    const delta = Math.ceil(radius - Math.sqrt(Math.max(0, radius * radius - (w * w) / 4))) + 1
    const originY = diameter - delta

    circle.style.width = `${diameter}px`
    circle.style.height = `${diameter}px`
    circle.style.bottom = `-${delta}px`

    gsap.set(circle, {
      xPercent: -50,
      scale: 0,
      transformOrigin: `50% ${originY}px`
    })

    const label = pill.querySelector('.pill-label')
    const hoveredLabel = pill.querySelector('.pill-label-hover')

    if (label) gsap.set(label, { y: 0 })
    if (hoveredLabel) gsap.set(hoveredLabel, { y: h + 12, opacity: 0 })

    timelineRefs.value[index]?.kill()
    const timeline = gsap.timeline({ paused: true })

    timeline.to(circle, { scale: 1.2, xPercent: -50, duration: 0.7, ease: props.ease, overwrite: 'auto' }, 0)

    if (label) {
      timeline.to(label, { y: -(h + 8), duration: 0.7, ease: props.ease, overwrite: 'auto' }, 0)
    }

    if (hoveredLabel) {
      timeline.to(hoveredLabel, { y: 0, opacity: 1, duration: 0.7, ease: props.ease, overwrite: 'auto' }, 0)
    }

    timelineRefs.value[index] = timeline
  })
}

const runInitialAnimation = () => {
  if (!props.initialLoadAnimation) return

  if (logoRef.value) {
    gsap.set(logoRef.value, { scale: 0, transformOrigin: 'center' })
    gsap.to(logoRef.value, { scale: 1, duration: 0.55, ease: props.ease })
  }

  if (navItemsRef.value) {
    gsap.set(navItemsRef.value, { width: 0, overflow: 'hidden' })
    gsap.to(navItemsRef.value, { width: 'auto', duration: 0.55, ease: props.ease })
  }
}

const onResize = () => layout()

onMounted(async () => {
  await nextTick()
  layout()
  window.addEventListener('resize', onResize)

  if (document.fonts?.ready) document.fonts.ready.then(layout).catch(() => {})

  if (mobileMenuRef.value) gsap.set(mobileMenuRef.value, { visibility: 'hidden', opacity: 0, y: 10 })
  runInitialAnimation()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  timelineRefs.value.forEach((timeline) => timeline?.kill())
  activeTweenRefs.value.forEach((tween) => tween?.kill())
  logoTweenRef.value?.kill()
  gsap.killTweensOf([logoRef.value, navItemsRef.value, mobileMenuRef.value])
})

watch(
  () => [props.items, props.ease, props.initialLoadAnimation],
  async () => {
    await nextTick()
    layout()
    runInitialAnimation()
  },
  { deep: true }
)

const handleEnter = (index) => {
  const timeline = timelineRefs.value[index]
  if (!timeline) return
  activeTweenRefs.value[index]?.kill()
  activeTweenRefs.value[index] = timeline.tweenTo(timeline.duration(), {
    duration: 0.28,
    ease: props.ease,
    overwrite: 'auto'
  })
}

const handleLeave = (index) => {
  const timeline = timelineRefs.value[index]
  if (!timeline) return
  activeTweenRefs.value[index]?.kill()
  activeTweenRefs.value[index] = timeline.tweenTo(0, {
    duration: 0.22,
    ease: props.ease,
    overwrite: 'auto'
  })
}

const handleLogoEnter = () => {
  if (!logoImgRef.value) return
  logoTweenRef.value?.kill()
  gsap.set(logoImgRef.value, { rotate: 0 })
  logoTweenRef.value = gsap.to(logoImgRef.value, {
    rotate: 360,
    duration: 0.5,
    ease: props.ease,
    overwrite: 'auto'
  })
}

const setMobileMenuOpen = (open) => {
  isMobileMenuOpen.value = open

  const lines = hamburgerRef.value?.querySelectorAll('.hamburger-line')
  if (lines?.length) {
    gsap.to(lines[0], { rotation: open ? 45 : 0, y: open ? 3 : 0, duration: 0.28, ease: props.ease })
    gsap.to(lines[1], { rotation: open ? -45 : 0, y: open ? -3 : 0, duration: 0.28, ease: props.ease })
  }

  if (!mobileMenuRef.value) return
  if (open) {
    gsap.set(mobileMenuRef.value, { visibility: 'visible' })
    gsap.fromTo(
      mobileMenuRef.value,
      { opacity: 0, y: 10 },
      { opacity: 1, y: 0, duration: 0.28, ease: props.ease }
    )
  } else {
    gsap.to(mobileMenuRef.value, {
      opacity: 0,
      y: 10,
      duration: 0.2,
      ease: props.ease,
      onComplete: () => gsap.set(mobileMenuRef.value, { visibility: 'hidden' })
    })
  }
}

const handleItemClick = (item, event) => {
  if (shouldUseButton(item)) event.preventDefault()
  props.onItemClick?.(item)
  if (isMobileMenuOpen.value) setMobileMenuOpen(false)
}

const toggleMobileMenu = () => {
  setMobileMenuOpen(!isMobileMenuOpen.value)
  props.onMobileMenuClick?.()
}
</script>

<template>
  <div :class="['pill-nav-shell', className]" :style="cssVars">
    <nav class="pill-nav" aria-label="页面导航">
      <component
        :is="shouldUseButton(items?.[0]) ? 'button' : 'a'"
        :type="shouldUseButton(items?.[0]) ? 'button' : undefined"
        :href="!shouldUseButton(items?.[0]) ? items?.[0]?.href || '#' : undefined"
        class="pill-nav-logo"
        aria-label="返回首页"
        @click="handleItemClick(items?.[0], $event)"
        @mouseenter="handleLogoEnter"
      >
        <img ref="logoImgRef" :src="logo" :alt="logoAlt" />
      </component>

      <div ref="navItemsRef" class="pill-nav-desktop">
        <ul class="pill-nav-list" role="menubar">
          <li v-for="(item, index) in items" :key="item.href || `item-${index}`" role="none">
            <component
              :is="shouldUseButton(item) ? 'button' : 'a'"
              :type="shouldUseButton(item) ? 'button' : undefined"
              :href="!shouldUseButton(item) ? item.href || '#' : undefined"
              class="pill-nav-item"
              :class="{ 'is-active': activeHref === item.href }"
              :aria-label="item.ariaLabel || item.label"
              :aria-current="activeHref === item.href ? 'page' : undefined"
              @click="handleItemClick(item, $event)"
              @mouseenter="handleEnter(index)"
              @mouseleave="handleLeave(index)"
            >
              <span
                :ref="(el) => setCircleRef(el, index)"
                class="pill-nav-hover-circle"
                aria-hidden="true"
              ></span>
              <span class="pill-nav-label-stack">
                <span class="pill-label">{{ item.label }}</span>
                <span class="pill-label-hover" aria-hidden="true">{{ item.label }}</span>
              </span>
            </component>
          </li>
        </ul>
      </div>

      <button
        ref="hamburgerRef"
        class="pill-nav-hamburger"
        type="button"
        :aria-label="isMobileMenuOpen ? '关闭页面菜单' : '打开页面菜单'"
        :aria-expanded="isMobileMenuOpen"
        @click="toggleMobileMenu"
      >
        <span class="hamburger-line"></span>
        <span class="hamburger-line"></span>
      </button>
    </nav>

    <div ref="mobileMenuRef" class="pill-nav-mobile-menu">
      <ul role="menu">
        <li v-for="item in items" :key="item.href || `mobile-${item.label}`">
          <component
            :is="shouldUseButton(item) ? 'button' : 'a'"
            :type="shouldUseButton(item) ? 'button' : undefined"
            :href="!shouldUseButton(item) ? item.href || '#' : undefined"
            class="pill-nav-mobile-link"
            :class="{ 'is-active': activeHref === item.href }"
            role="menuitem"
            :aria-label="item.ariaLabel || item.label"
            :aria-current="activeHref === item.href ? 'page' : undefined"
            @click="handleItemClick(item, $event)"
          >
            {{ item.label }}
          </component>
        </li>
      </ul>
    </div>
  </div>
</template>

<style scoped>
.pill-nav-shell {
  position: relative;
  display: flex;
  width: max-content;
  max-width: 100%;
  align-items: center;
}

.pill-nav {
  display: inline-flex;
  width: max-content;
  max-width: 100%;
  align-items: center;
  gap: 7px;
}

.pill-nav-logo,
.pill-nav-hamburger {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  border: 1px solid rgba(255, 255, 255, .16);
  border-radius: 50%;
  padding: 0;
  color: var(--pill-bg);
  background: var(--base);
  box-shadow: 0 10px 24px rgba(30, 44, 39, .18), inset 0 1px 0 rgba(255, 255, 255, .14);
  overflow: hidden;
}

.pill-nav-logo {
  width: var(--logo-size);
  height: var(--logo-size);
  border-color: var(--line-strong);
  background: var(--surface);
}

.pill-nav-logo img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: center center;
}

.pill-nav-desktop {
  display: flex;
  width: max-content;
  max-width: 100%;
  height: var(--nav-h);
  align-items: center;
  border-radius: 999px;
  border: 1px solid rgba(255, 255, 255, .15);
  background: var(--base);
  box-shadow: 0 10px 24px rgba(30, 44, 39, .15), inset 0 1px 0 rgba(255, 255, 255, .14);
  overflow: hidden;
}

.pill-nav-list,
.pill-nav-mobile-menu ul {
  display: flex;
  align-items: stretch;
  gap: var(--pill-gap);
  margin: 0;
  padding: 3px;
  list-style: none;
}

.pill-nav-list {
  height: 100%;
}

.pill-nav-list li {
  display: flex;
  height: 100%;
}

.pill-nav-item {
  position: relative;
  display: inline-flex;
  min-width: 62px;
  height: 100%;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
  border: 0;
  border-radius: 999px;
  padding: 0 16px;
  color: var(--pill-text);
  background: var(--pill-bg);
  font: inherit;
  font-size: 12px;
  font-weight: 750;
  line-height: 1;
  text-decoration: none;
  white-space: nowrap;
  cursor: pointer;
  overflow: hidden;
  transition: background-color 180ms ease, box-shadow 180ms ease, color 180ms ease;
}

.pill-nav-item:hover {
  box-shadow: inset 0 -2px 0 var(--gold);
}

.pill-nav-item.is-active {
  color: var(--hover-text);
  background: var(--base);
  box-shadow: inset 0 -2px 0 var(--gold);
}

.pill-nav-item:focus-visible {
  outline-color: var(--base);
  outline-offset: -3px;
}

.pill-nav-item.is-active:focus-visible,
.pill-nav-item:hover:focus-visible,
.pill-nav-hamburger:focus-visible {
  outline-color: var(--gold);
  outline-offset: -3px;
}

.pill-nav-mobile-link:focus-visible {
  color: var(--pill-text);
  background: var(--pill-bg);
  outline-color: var(--base);
  outline-offset: -3px;
}

.pill-nav-hover-circle {
  position: absolute;
  bottom: -10px;
  left: 50%;
  width: 0;
  height: 0;
  border-radius: 50%;
  background: var(--base);
  pointer-events: none;
  will-change: transform;
}

.pill-nav-label-stack {
  position: relative;
  z-index: 2;
  display: inline-block;
  line-height: 1;
}

.pill-label {
  position: relative;
  z-index: 2;
  display: inline-block;
  will-change: transform;
}

.pill-label-hover {
  position: absolute;
  top: 0;
  left: 0;
  z-index: 3;
  display: inline-block;
  color: var(--hover-text);
  opacity: 0;
  white-space: nowrap;
  will-change: transform, opacity;
}

.pill-nav-hamburger {
  display: none;
  width: var(--nav-h);
  height: var(--nav-h);
  flex-direction: column;
  gap: 5px;
}

.hamburger-line {
  display: block;
  width: 17px;
  height: 2px;
  border-radius: 999px;
  background: var(--pill-bg);
  transform-origin: center;
}

.pill-nav-mobile-menu {
  position: absolute;
  top: calc(100% + 11px);
  left: 0;
  z-index: 998;
  display: none;
  width: min(330px, calc(100vw - 32px));
  border: 1px solid rgba(255, 255, 255, .16);
  border-radius: 22px;
  background: var(--base);
  box-shadow: 0 20px 48px rgba(30, 44, 39, .2), inset 0 1px 0 rgba(255, 255, 255, .14);
  transform-origin: top center;
}

.pill-nav-mobile-menu ul {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 5px;
}

.pill-nav-mobile-link {
  display: block;
  width: 100%;
  border: 0;
  border-radius: 999px;
  padding: 12px 16px;
  color: var(--pill-bg);
  background: transparent;
  font: inherit;
  font-size: 13px;
  font-weight: 750;
  text-align: left;
  text-decoration: none;
  cursor: pointer;
  transition: background-color 180ms ease, color 180ms ease, transform 180ms ease;
}

.pill-nav-mobile-link:hover,
.pill-nav-mobile-link.is-active {
  color: var(--pill-text);
  background: var(--pill-bg);
}

.pill-nav-mobile-link.is-active {
  box-shadow: inset 0 -2px 0 var(--gold);
}

@media (max-width: 680px) {
  .pill-nav-shell {
    width: 100%;
  }

  .pill-nav {
    width: 100%;
    justify-content: space-between;
  }

  .pill-nav-desktop {
    display: none;
  }

  .pill-nav-hamburger,
  .pill-nav-mobile-menu {
    display: inline-flex;
  }

  .pill-nav-mobile-menu {
    display: block;
  }
}

@media (prefers-reduced-motion: reduce) {
  .pill-nav-item,
  .pill-nav-mobile-link {
    transition: none;
  }
}
</style>
