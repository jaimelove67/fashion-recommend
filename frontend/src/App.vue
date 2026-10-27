<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import {
  Bell,
  CircleAlert,
  CloudSun,
  LocateFixed,
  LoaderCircle,
  LogOut,
  RefreshCw,
  Search,
  UserRound,
  X
} from '@lucide/vue'
import { useFashionApp } from './composables/useFashionApp'
import PillNav from './components/PillNav.vue'
import RecommendationAssistant from './components/RecommendationAssistant.vue'
import LiveTryOnDialog from './components/LiveTryOnDialog.vue'
import { canTryOnGarment } from './utils/liveTryOn.js'
import { favoriteTryOnItem } from './utils/tryOnFavorites.js'
import { disconnectTryOnExtension } from './utils/extensionBridge.js'
import HomeView from './views/HomeView.vue'
import TrendView from './views/TrendView.vue'
import WardrobeView from './views/WardrobeView.vue'
import RecommendationView from './views/RecommendationView.vue'
import HistoryView from './views/HistoryView.vue'
import ProfileView from './views/ProfileView.vue'
import LoginView from './views/LoginView.vue'
import AdminView from './views/AdminView.vue'

const fashion = reactive(useFashionApp())
const guestLoginOpen = ref(false)
const searchInput = ref(null)
const weatherOpen = ref(false)
const assistantOpen = ref(false)
const assistantRef = ref(null)
const tryOnOpen = ref(false)
const currentViewRef = ref(null)
const tryOnItemIds = ref([])
const tryOnItems = computed(() => tryOnItemIds.value
  .map(id => fashion.state.tryOnFavorites.map(favoriteTryOnItem)
    .find(item => item.id === id))
  .filter(canTryOnGarment))

const baseNavigation = [
  { id: 'recommend', label: '穿搭推荐', href: '#recommend' },
  { id: 'wardrobe', label: '我的衣橱', href: '#wardrobe' },
  { id: 'trend', label: '穿搭鉴赏', href: '#trend' },
  { id: 'history', label: '搭配记录', href: '#history', keywords: '穿搭历史' }
]

const isAdminWorkspace = computed(() => fashion.isAdmin && fashion.state.activeView === 'admin')
const navigation = computed(() => {
  if (isAdminWorkspace.value) return [{ id: 'admin', label: '管理后台', href: '#admin' }]
  return fashion.state.authUser?.authorities?.includes('ROLE_ADMIN')
    ? [...baseNavigation, { id: 'admin', label: '管理后台', href: '#admin' }]
    : baseNavigation
})

const viewComponents = {
  trend: TrendView,
  recommend: RecommendationView,
  wardrobe: WardrobeView,
  history: HistoryView,
  favorites: HistoryView,
  profile: ProfileView,
  admin: AdminView
}

const currentView = computed(() => viewComponents[fashion.state.activeView] || RecommendationView)
const normalizedQuery = computed(() => fashion.state.globalQuery.trim().toLocaleLowerCase('zh-CN'))
const searchResults = computed(() => {
  const query = normalizedQuery.value
  if (!query) return []
  const searchableViews = isAdminWorkspace.value ? navigation.value : [
    ...navigation.value,
    { id: 'favorites', label: '喜欢的穿搭', keywords: '喜欢穿搭' }
  ]
  const views = searchableViews
    .filter((item) => `${item.label} ${item.keywords || ''}`.includes(query))
    .map((item) => ({ id: `view-${item.id}`, type: 'view', view: item.id, title: item.label,
      meta: item.id === 'favorites' ? '搭配记录' : '页面' }))
  const trends = fashion.state.trends
    .filter((item) => `${item.title} ${(item.topicTags || []).join(' ')}`.toLocaleLowerCase('zh-CN').includes(query))
    .map((item) => ({ id: `trend-${item.id}`, type: 'trend', view: 'trend', trendId: item.id, title: item.title, meta: '穿搭鉴赏' }))
  const garments = fashion.state.wardrobe
    .filter((item) => `${item.name} ${item.category} ${item.color} ${item.style || ''}`.toLocaleLowerCase('zh-CN').includes(query))
    .map((item) => ({ id: `garment-${item.id}`, type: 'garment', view: 'wardrobe', title: item.name, meta: `${item.category} · ${item.color}` }))
  return [...views, ...trends, ...garments].slice(0, 8)
})

const notifications = computed(() => {
  const items = []
  if (fashion.state.profile && (fashion.state.profile.stale || !fashion.state.profile.analysis)) {
    const profile = fashion.state.profile
    const title = profile.stale ? '形象资料已变更，请重新确认分析结果'
      : profile.analysis ? '个人形象分析结果已保存' : '个人形象分析待完善'
    items.push({ id: 'profile-analysis', title, view: 'profile' })
  }
  if (fashion.wardrobeStats.review) {
    items.push({ id: 'review', title: `${fashion.wardrobeStats.review} 件衣物需要补充信息`, view: 'wardrobe' })
  }
  if (fashion.state.trendMeta.demoMode) {
    items.push({ id: 'trend-demo', title: '演示数据 · 非实时', view: 'trend' })
  }
  if (fashion.state.error) {
    items.push({ id: 'error', title: fashion.state.error, view: fashion.state.activeView })
  }

  return items
})

async function toggleSearch() {
  fashion.state.searchOpen = !fashion.state.searchOpen
  fashion.state.notificationsOpen = false
  weatherOpen.value = false
  if (fashion.state.searchOpen) {
    await nextTick()
    searchInput.value?.focus()
  }
}

function toggleNotifications() {
  fashion.state.notificationsOpen = !fashion.state.notificationsOpen
  fashion.state.searchOpen = false
  weatherOpen.value = false
}

function toggleWeather() {
  weatherOpen.value = !weatherOpen.value
  fashion.state.searchOpen = false
  fashion.state.notificationsOpen = false
}

function handleNavigationClick(item) {
  if (item?.id && viewComponents[item.id]) fashion.selectView(item.id)
}

function openGuestLogin(view) {
  if (fashion.state.authPhase !== 'guest') return
  if (view) void fashion.selectView(view, { instant: true })
  else window.scrollTo({ top: 0, behavior: 'auto' })
  guestLoginOpen.value = true
}

function returnToGuestHome() {
  guestLoginOpen.value = false
  void fashion.selectView('home', { instant: true })
}

function browseGuestSection(id) {
  document.getElementById(id)?.scrollIntoView({
    behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth',
    block: 'start'
  })
}

function openSearchResult(result) {
  if (result.trendId) fashion.state.selectedTrendId = result.trendId
  fashion.state.globalQuery = ''
  fashion.state.searchOpen = false
  fashion.selectView(result.view)
}

function closePanels() {
  fashion.state.searchOpen = false
  fashion.state.notificationsOpen = false
  weatherOpen.value = false
}

function openAssistant(plan = null, prompt = '') {
  closePanels()
  assistantRef.value?.open(plan, prompt)
}

function toggleAssistant() {
  if (assistantOpen.value) assistantOpen.value = false
  else openAssistant()
}

function openTryOn(selection) {
  if (fashion.state.activeView !== 'favorites' || fashion.state.authPhase !== 'authenticated') return
  tryOnItemIds.value = [...new Set((Array.isArray(selection) ? selection : [selection]).map(item => item?.id))]
  if (!tryOnItems.value.length) return
  closePanels()
  assistantOpen.value = false
  tryOnOpen.value = true
}

function closeTryOn() {
  tryOnOpen.value = false
  tryOnItemIds.value = []
}

async function openTryOnSetup() {
  closeTryOn()
  void fashion.selectView('trend')
  await nextTick()
  if (fashion.state.authPhase === 'authenticated' && fashion.state.activeView === 'trend') {
    currentViewRef.value?.openTryOnSetup?.()
  }
}

watch(() => [fashion.state.authPhase, fashion.state.authUser?.username, fashion.state.activeView,
  fashion.state.trendPlatform, fashion.state.trendPeriod, fashion.state.trends], closeTryOn)

watch(() => [fashion.state.authPhase, fashion.state.authUser?.username], (_value, previous) => {
  assistantOpen.value = false
  if (fashion.state.authPhase === 'authenticated') guestLoginOpen.value = false
  if (previous?.[0] === 'authenticated') {
    disconnectTryOnExtension()
    guestLoginOpen.value = fashion.state.authPhase === 'guest' && Boolean(fashion.state.authError)
  }
})

function onExtensionMessage(event) {
  if (event.source === window && event.origin === location.origin
    && event.data?.channel === 'ZHIJI_TRY_ON_EXTENSION' && event.data.type === 'FAVORITE_SAVED'
    && fashion.state.authPhase === 'authenticated') void fashion.loadTryOnFavorites()
}
onMounted(() => window.addEventListener('message', onExtensionMessage))
onBeforeUnmount(() => window.removeEventListener('message', onExtensionMessage))
onMounted(() => fashion.initialize())
onBeforeUnmount(() => fashion.dispose())
</script>

<template>
  <LoginView
    v-if="fashion.state.authPhase === 'guest' && guestLoginOpen"
    :app="fashion"
    :direct-entry="Boolean(fashion.state.authError)"
    can-browse
    @browse="returnToGuestHome"
  />

  <div v-else-if="fashion.state.authPhase !== 'authenticated'" class="app-shell guest-shell">
    <header class="guest-header">
      <button class="guest-brand" type="button" aria-label="返回游客首页" @click="returnToGuestHome">
        <img src="/assets/zhiji-logo.png" alt="" width="40" height="40" />
        <span><strong>知己</strong><small>穿搭推荐</small></span>
      </button>
      <nav class="guest-navigation" aria-label="游客导航">
        <button type="button" @click="browseGuestSection('guest-inspiration')">穿搭示例</button>
        <button type="button" @click="browseGuestSection('home-features')">功能介绍</button>
      </nav>
      <button
        class="guest-login-button"
        type="button"
        :disabled="fashion.state.authPhase === 'checking'"
        @click="openGuestLogin()"
      >
        <LoaderCircle v-if="fashion.state.authPhase === 'checking'" class="spinning" :size="15" aria-hidden="true" />
        {{ fashion.state.authPhase === 'checking' ? '正在确认登录…' : '登录 / 注册' }}
      </button>
    </header>
    <main class="app-main">
      <HomeView
        :app="fashion"
        guest
        @request-login="openGuestLogin"
        @browse-examples="browseGuestSection('guest-inspiration')"
      />
    </main>
    <footer class="guest-footer">
      <span>知己 · 让日常穿搭更懂你</span>
      <span>登录后，开启你的个人穿搭空间</span>
    </footer>
  </div>

  <div v-else class="app-shell" :class="{ 'admin-workspace-shell': isAdminWorkspace }" @keydown.esc="closePanels">
    <header class="app-header">
      <PillNav
        class="app-pill-nav"
        logo="/assets/zhiji-logo.png"
        logo-alt="知己"
        :logo-expanded="assistantOpen"
        :items="navigation"
        :active-href="fashion.state.activeView === 'favorites' ? '#history' : `#${fashion.state.activeView}`"
        :on-item-click="handleNavigationClick"
        ease="power3.out"
        base-color="var(--nav-bg)"
        pill-color="var(--surface)"
        active-pill-color="var(--nav-active-bg)"
        hovered-pill-text-color="var(--accent-strong)"
        pill-text-color="var(--ink)"
        @logo-click="toggleAssistant"
      />

      <div class="header-actions">
        <button class="utility-button weather-button" :class="{ active: weatherOpen }" type="button" aria-label="查看天气" :aria-expanded="weatherOpen" title="天气" @click="toggleWeather">
          <CloudSun :size="19" />
          <span v-if="fashion.state.weather" class="weather-nav-value">{{ Number(fashion.state.weather.temperatureC).toFixed(0) }}°</span>
        </button>
        <button class="utility-button" type="button" aria-label="搜索页面、穿搭鉴赏或衣物" :aria-expanded="fashion.state.searchOpen" @click="toggleSearch">
          <X v-if="fashion.state.searchOpen" :size="19" />
          <Search v-else :size="19" />
        </button>
        <button class="utility-button notification-button" type="button" aria-label="查看通知" :aria-expanded="fashion.state.notificationsOpen" @click="toggleNotifications">
          <Bell :size="19" />
          <span v-if="notifications.length" class="notification-dot"></span>
        </button>
        <button class="profile-button" type="button" :class="{ active: fashion.state.activeView === 'profile' }" aria-label="查看个人形象档案" @click="fashion.selectView('profile')">
          <UserRound :size="18" />
          <span>{{ fashion.state.profile?.displayName || fashion.state.authUser?.username || '个人中心' }}</span>
        </button>
        <button
          class="utility-button"
          type="button"
          :disabled="fashion.state.authSubmitting"
          aria-label="退出登录"
          title="退出登录"
          @click="fashion.logout"
        >
          <LoaderCircle v-if="fashion.state.authSubmitting" class="spinning" :size="18" aria-hidden="true" />
          <LogOut v-else :size="18" aria-hidden="true" />
        </button>
      </div>
    </header>

    <section v-if="fashion.state.searchOpen" class="header-panel search-panel" aria-label="搜索">
      <div class="search-field">
        <Search :size="18" aria-hidden="true" />
        <input ref="searchInput" v-model="fashion.state.globalQuery" type="search" placeholder="搜索页面、穿搭鉴赏或衣物" aria-label="搜索页面、穿搭鉴赏或衣物" />
      </div>
      <div v-if="normalizedQuery" class="search-results">
        <button v-for="result in searchResults" :key="result.id" type="button" @click="openSearchResult(result)">
          <span>{{ result.title }}</span><small>{{ result.meta }}</small>
        </button>
        <p v-if="!searchResults.length">未找到匹配内容，请调整关键词。</p>
      </div>
      <p v-else class="panel-hint">支持按页面名称、衣物名称、类别、颜色或风格标签搜索。</p>
    </section>

    <section v-if="weatherOpen" class="header-panel weather-panel" aria-label="当前天气">
      <div class="weather-panel-heading">
        <div class="weather-panel-title">
          <CloudSun :size="20" aria-hidden="true" />
          <div>
            <span>当前天气</span>
            <strong>{{ fashion.state.weather?.city || '城市未设置' }}</strong>
          </div>
        </div>
        <small v-if="fashion.state.weather">{{ fashion.weatherSourceLabel(fashion.state.weather.source) }}</small>
      </div>

      <div v-if="fashion.state.weather" class="weather-panel-reading">
        <strong>{{ Number(fashion.state.weather.temperatureC).toFixed(1) }}°C</strong>
        <div>
          <span>{{ fashion.weatherConditionLabel(fashion.state.weather.weatherCode) }}</span>
          <p>体感 {{ Number(fashion.state.weather.apparentTemperatureC).toFixed(1) }}°C · 降水 {{ Number(fashion.state.weather.precipitationMm).toFixed(1) }} mm · 风速 {{ Number(fashion.state.weather.windSpeedKmh).toFixed(1) }} km/h</p>
        </div>
      </div>

      <div v-else-if="fashion.state.weatherLoading" class="weather-panel-empty" role="status" aria-live="polite">
        <LoaderCircle class="spinning" :size="17" />正在获取当前天气…
      </div>

      <div v-else class="weather-panel-form">
        <button class="weather-panel-locate" type="button" :disabled="fashion.state.weatherLoading" @click="fashion.loadLocalWeather({ fresh: true })">
          <LocateFixed :size="15" />开启定位获取天气
        </button>
        <label>
          <span class="sr-only">城市</span>
          <input
            v-model.trim="fashion.state.recommendationForm.city"
            type="text"
            maxlength="80"
            placeholder="输入城市名称"
            aria-label="城市"
            @input="fashion.clearWeather"
            @keyup.enter="fashion.loadWeather"
          />
        </label>
        <button type="button" :disabled="!fashion.state.recommendationForm.city || fashion.state.weatherLoading" @click="fashion.loadWeather">
          <RefreshCw :size="15" />获取城市天气
        </button>
      </div>

      <div v-if="fashion.state.weather" class="weather-panel-actions">
        <button type="button" @click="fashion.clearWeather">更换城市</button>
        <button type="button" :disabled="fashion.state.weatherLoading" @click="fashion.refreshWeather">
          <LoaderCircle v-if="fashion.state.weatherLoading" class="spinning" :size="15" />
          <RefreshCw v-else :size="15" />刷新天气
      </button>
      </div>
      <p v-if="!fashion.state.weather && !fashion.state.weatherLoading" class="panel-hint">优先开启定位获取当地天气；不方便定位时，也可以选择城市。</p>
    </section>

    <section v-if="fashion.state.notificationsOpen" class="header-panel notification-panel" aria-label="通知列表">
      <p class="panel-title">通知</p>
      <p v-if="!notifications.length">暂无待处理通知</p>
      <button v-for="item in notifications" :key="item.id" type="button" @click="fashion.selectView(item.view)">
        <span class="notice-mark"></span><span>{{ item.title }}</span>
      </button>
    </section>

    <div v-if="fashion.state.error" class="error-banner" role="alert">
      <CircleAlert :size="18" aria-hidden="true" />
      <span>{{ fashion.state.error }}</span>
      <button type="button" aria-label="关闭提示" @click="fashion.clearError"><X :size="17" /></button>
    </div>

    <main class="app-main">
      <KeepAlive>
        <component :is="currentView" ref="currentViewRef" :app="fashion" @open-assistant="openAssistant" @open-try-on="openTryOn" />
      </KeepAlive>
    </main>
    <RecommendationAssistant
      :key="fashion.state.authUser?.username"
      ref="assistantRef"
      v-model:open="assistantOpen"
      :app="fashion"
    />
    <LiveTryOnDialog
      v-if="tryOnOpen && fashion.state.authPhase === 'authenticated'"
      :key="fashion.state.authUser?.username"
      :items="tryOnItems"
      :initial-item-id="tryOnItems[0]?.id"
      @close="closeTryOn"
      @setup-extension="openTryOnSetup"
    />
  </div>
</template>

<style scoped>
.guest-header,
.guest-footer {
  width: min(calc(100% - 64px), var(--page-width));
  margin: 0 auto;
}

.guest-header {
  display: grid;
  min-height: 76px;
  grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
  align-items: center;
  gap: 20px;
  border-bottom: 1px solid var(--line);
}

.guest-brand {
  display: flex;
  width: fit-content;
  align-items: center;
  gap: 9px;
  border: 0;
  padding: 0;
  color: var(--ink);
  background: transparent;
  text-align: left;
}

.guest-brand img {
  flex: 0 0 auto;
  object-fit: contain;
}

.guest-brand span {
  display: grid;
  gap: 2px;
}

.guest-brand strong {
  font: 600 23px/1.2 var(--font-display);
}

.guest-brand small {
  color: var(--muted);
  font-size: 10px;
}

.guest-navigation {
  display: flex;
  justify-content: center;
  gap: 8px;
}

.guest-navigation button {
  min-height: 44px;
  border: 0;
  border-radius: var(--radius);
  padding: 10px 14px;
  color: var(--muted);
  background: transparent;
  font-size: 12px;
  white-space: nowrap;
}

.guest-navigation button:hover {
  color: var(--accent-strong);
  background: var(--accent-soft);
}

.guest-login-button {
  display: inline-flex;
  min-height: 44px;
  align-items: center;
  justify-content: center;
  justify-self: end;
  gap: 8px;
  border: 1px solid var(--accent-strong);
  border-radius: var(--radius);
  padding: 10px 18px;
  color: var(--surface);
  background: var(--accent-strong);
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
}

.guest-login-button:hover:not(:disabled) {
  background: var(--accent);
}

.guest-footer {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 8px 20px;
  border-top: 1px solid var(--line);
  padding: 22px 0;
  color: var(--muted);
  font-size: 11px;
}

@media (max-width: 760px) {
  .guest-header,
  .guest-footer {
    width: calc(100% - 40px);
  }

  .guest-header {
    grid-template-columns: minmax(0, 1fr) auto;
    gap: 6px 12px;
    padding: 14px 0 6px;
  }

  .guest-navigation {
    grid-column: 1 / -1;
    grid-row: 2;
  }
}

@media (max-width: 480px) {
  .guest-header,
  .guest-footer {
    width: calc(100% - 32px);
  }
}
</style>
