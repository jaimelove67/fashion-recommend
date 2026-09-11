<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
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
import HomeView from './views/HomeView.vue'
import TrendView from './views/TrendView.vue'
import WardrobeView from './views/WardrobeView.vue'
import RecommendationView from './views/RecommendationView.vue'
import HistoryView from './views/HistoryView.vue'
import ProfileView from './views/ProfileView.vue'
import LoginView from './views/LoginView.vue'

const fashion = reactive(useFashionApp())
const searchInput = ref(null)
const weatherOpen = ref(false)

const navigation = [
  { id: 'home', label: '首页', href: '#home' },
  { id: 'trend', label: '趋势', href: '#trend' },
  { id: 'recommend', label: '推荐', href: '#recommend' },
  { id: 'wardrobe', label: '衣橱', href: '#wardrobe' },
  { id: 'history', label: '历史', href: '#history' }
]

const viewComponents = {
  home: HomeView,
  trend: TrendView,
  recommend: RecommendationView,
  wardrobe: WardrobeView,
  history: HistoryView,
  profile: ProfileView
}

const currentView = computed(() => viewComponents[fashion.state.activeView] || HomeView)
const normalizedQuery = computed(() => fashion.state.globalQuery.trim().toLocaleLowerCase('zh-CN'))
const searchResults = computed(() => {
  const query = normalizedQuery.value
  if (!query) return []
  const views = navigation
    .filter((item) => item.label.includes(query))
    .map((item) => ({ id: `view-${item.id}`, type: 'view', view: item.id, title: item.label, meta: '页面' }))
  const trends = fashion.state.trends
    .filter((item) => `${item.title} ${(item.topicTags || []).join(' ')}`.toLocaleLowerCase('zh-CN').includes(query))
    .map((item) => ({ id: `trend-${item.id}`, type: 'trend', view: 'trend', trendId: item.id, title: item.title, meta: '趋势' }))
  const garments = fashion.state.wardrobe
    .filter((item) => `${item.name} ${item.category} ${item.color} ${item.style || ''}`.toLocaleLowerCase('zh-CN').includes(query))
    .map((item) => ({ id: `garment-${item.id}`, type: 'garment', view: 'wardrobe', title: item.name, meta: `${item.category} · ${item.color}` }))
  return [...views, ...trends, ...garments].slice(0, 8)
})

const notifications = computed(() => {
  const items = []
  if (fashion.state.profile) {
    items.push({ id: 'profile-analysis', title: '形象档案已有分析结果', view: 'profile' })
  }
  if (fashion.wardrobeStats.review) {
    items.push({ id: 'review', title: `${fashion.wardrobeStats.review} 件衣物需要补充信息`, view: 'wardrobe' })
  }
  if (fashion.state.trendMeta.demoMode) {
    items.push({ id: 'trend-demo', title: '趋势数据使用开发样本', view: 'trend' })
  }
  if (fashion.state.error) {
    items.push({ id: 'error', title: fashion.state.error, view: fashion.state.activeView })
  }
  if (!items.length) items.push({ id: 'ready', title: '衣橱和个人档案已准备好', view: 'home' })
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

onMounted(() => fashion.initialize())
onBeforeUnmount(() => fashion.dispose())
</script>

<template>
  <main v-if="fashion.state.authPhase === 'checking'" class="session-gate" role="status" aria-live="polite">
    <div class="session-gate-brand"><strong>知己</strong><span>WEAVESELF</span></div>
    <LoaderCircle class="spinning" :size="23" aria-hidden="true" />
    <span>正在确认登录状态</span>
  </main>

  <LoginView v-else-if="fashion.state.authPhase === 'guest'" :app="fashion" />

  <div v-else class="app-shell" @keydown.esc="closePanels">
    <header class="app-header">
      <PillNav
        class="app-pill-nav"
        logo="/assets/zhiji-logo.png"
        logo-alt="知己"
        :items="navigation"
        :active-href="`#${fashion.state.activeView}`"
        :on-item-click="handleNavigationClick"
        ease="power3.out"
        base-color="#1e2c27"
        pill-color="#fffdf9"
        hovered-pill-text-color="#fffdf9"
        pill-text-color="#1e2c27"
      />

      <div class="header-actions">
        <button class="utility-button weather-button" :class="{ active: weatherOpen }" type="button" aria-label="查看天气" :aria-expanded="weatherOpen" title="天气" @click="toggleWeather">
          <CloudSun :size="19" />
          <span v-if="fashion.state.weather" class="weather-nav-value">{{ Number(fashion.state.weather.temperatureC).toFixed(0) }}°</span>
        </button>
        <button class="utility-button" type="button" aria-label="搜索页面、趋势或衣物" :aria-expanded="fashion.state.searchOpen" @click="toggleSearch">
          <X v-if="fashion.state.searchOpen" :size="19" />
          <Search v-else :size="19" />
        </button>
        <button class="utility-button notification-button" type="button" aria-label="查看通知" :aria-expanded="fashion.state.notificationsOpen" @click="toggleNotifications">
          <Bell :size="19" />
          <span v-if="notifications.length" class="notification-dot"></span>
        </button>
        <button class="profile-button" type="button" :class="{ active: fashion.state.activeView === 'profile' }" aria-label="打开风格档案" @click="fashion.selectView('profile')">
          <UserRound :size="18" />
          <span>{{ fashion.state.profile?.displayName || fashion.state.authUser?.username || '我' }}</span>
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
        <input ref="searchInput" v-model="fashion.state.globalQuery" type="search" placeholder="搜索页面、趋势或衣物" aria-label="搜索页面、趋势或衣物" />
      </div>
      <div v-if="normalizedQuery" class="search-results">
        <button v-for="result in searchResults" :key="result.id" type="button" @click="openSearchResult(result)">
          <span>{{ result.title }}</span><small>{{ result.meta }}</small>
        </button>
        <p v-if="!searchResults.length">没有找到相符的内容</p>
      </div>
      <p v-else class="panel-hint">可搜名称、类别、颜色或趋势标签</p>
    </section>

    <section v-if="weatherOpen" class="header-panel weather-panel" aria-label="当前天气">
      <div class="weather-panel-heading">
        <div class="weather-panel-title">
          <CloudSun :size="20" aria-hidden="true" />
          <div>
            <span>当前天气</span>
            <strong>{{ fashion.state.weather?.city || '当前位置' }}</strong>
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
          <RefreshCw :size="15" />查看城市天气
        </button>
        <button class="weather-panel-locate" type="button" :disabled="fashion.state.weatherLoading" @click="fashion.loadLocalWeather">
          <LocateFixed :size="15" />使用当前位置
        </button>
      </div>

      <div v-if="fashion.state.weather" class="weather-panel-actions">
        <button type="button" @click="fashion.clearWeather">更换城市</button>
        <button type="button" :disabled="fashion.state.weatherLoading" @click="fashion.refreshWeather">
          <LoaderCircle v-if="fashion.state.weatherLoading" class="spinning" :size="15" />
          <RefreshCw v-else :size="15" />刷新天气
      </button>
      </div>
      <p v-if="!fashion.state.weather && !fashion.state.weatherLoading" class="panel-hint">允许定位后会获取当前位置天气；也可以手动输入城市。</p>
    </section>

    <section v-if="fashion.state.notificationsOpen" class="header-panel notification-panel" aria-label="通知列表">
      <p class="panel-title">通知</p>
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
        <component :is="currentView" :app="fashion" />
      </KeepAlive>
    </main>
  </div>
</template>
