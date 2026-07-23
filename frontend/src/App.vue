<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import {
  Bell,
  CircleAlert,
  Clock3,
  Home,
  LoaderCircle,
  LogOut,
  Search,
  Shirt,
  Sparkles,
  TrendingUp,
  UserRound,
  X
} from '@lucide/vue'
import { useFashionApp } from './composables/useFashionApp'
import HomeView from './views/HomeView.vue'
import TrendView from './views/TrendView.vue'
import WardrobeView from './views/WardrobeView.vue'
import RecommendationView from './views/RecommendationView.vue'
import HistoryView from './views/HistoryView.vue'
import ProfileView from './views/ProfileView.vue'
import LoginView from './views/LoginView.vue'

const fashion = reactive(useFashionApp())
const searchInput = ref(null)

const navigation = [
  { id: 'home', label: '首页', icon: Home },
  { id: 'trend', label: '风潮', icon: TrendingUp },
  { id: 'recommend', label: '推荐', icon: Sparkles },
  { id: 'wardrobe', label: '衣橱', icon: Shirt },
  { id: 'history', label: '历史', icon: Clock3 }
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
    .map((item) => ({ id: `trend-${item.id}`, type: 'trend', view: 'trend', trendId: item.id, title: item.title, meta: '风潮' }))
  const garments = fashion.state.wardrobe
    .filter((item) => `${item.name} ${item.category} ${item.color} ${item.style || ''}`.toLocaleLowerCase('zh-CN').includes(query))
    .map((item) => ({ id: `garment-${item.id}`, type: 'garment', view: 'wardrobe', title: item.name, meta: `${item.category} · ${item.color}` }))
  return [...views, ...trends, ...garments].slice(0, 8)
})

const notifications = computed(() => {
  const items = []
  if (fashion.wardrobeStats.review) {
    items.push({ id: 'review', title: `${fashion.wardrobeStats.review} 件衣物待完善`, view: 'wardrobe' })
  }
  if (fashion.state.trendMeta.demoMode) {
    items.push({ id: 'trend-demo', title: '当前趋势为开发期样本数据', view: 'trend' })
  }
  if (fashion.state.error) {
    items.push({ id: 'error', title: fashion.state.error, view: fashion.state.activeView })
  }
  if (!items.length) items.push({ id: 'ready', title: '衣橱与个人档案已同步', view: 'home' })
  return items
})

async function toggleSearch() {
  fashion.state.searchOpen = !fashion.state.searchOpen
  fashion.state.notificationsOpen = false
  if (fashion.state.searchOpen) {
    await nextTick()
    searchInput.value?.focus()
  }
}

function toggleNotifications() {
  fashion.state.notificationsOpen = !fashion.state.notificationsOpen
  fashion.state.searchOpen = false
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
}

onMounted(() => fashion.initialize())
onBeforeUnmount(() => fashion.dispose())
</script>

<template>
  <main v-if="fashion.state.authPhase === 'checking'" class="session-gate" role="status" aria-live="polite">
    <div class="session-gate-brand"><strong>知己</strong><span>WEAVESELF</span></div>
    <LoaderCircle class="spinning" :size="23" aria-hidden="true" />
    <span>正在检查登录状态</span>
  </main>

  <LoginView v-else-if="fashion.state.authPhase === 'guest'" :app="fashion" />

  <div v-else class="app-shell" @keydown.esc="closePanels">
    <header class="app-header">
      <button class="brand" type="button" aria-label="返回知己首页" @click="fashion.selectView('home')">
        <strong>知己</strong><span>WEAVESELF</span>
      </button>

      <nav class="primary-nav" aria-label="主导航">
        <button
          v-for="item in navigation"
          :key="item.id"
          type="button"
          :class="{ active: fashion.state.activeView === item.id }"
          :aria-current="fashion.state.activeView === item.id ? 'page' : undefined"
          @click="fashion.selectView(item.id)"
        >
          <component :is="item.icon" :size="16" aria-hidden="true" />
          <span>{{ item.label }}</span>
        </button>
      </nav>

      <div class="header-actions">
        <button class="utility-button" type="button" aria-label="搜索页面、趋势或衣物" :aria-expanded="fashion.state.searchOpen" @click="toggleSearch">
          <X v-if="fashion.state.searchOpen" :size="19" />
          <Search v-else :size="19" />
        </button>
        <button class="utility-button notification-button" type="button" aria-label="查看通知" :aria-expanded="fashion.state.notificationsOpen" @click="toggleNotifications">
          <Bell :size="19" />
          <span v-if="notifications.length" class="notification-dot"></span>
        </button>
        <button class="profile-button" type="button" :class="{ active: fashion.state.activeView === 'profile' }" aria-label="打开个人风格档案" @click="fashion.selectView('profile')">
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

    <section v-if="fashion.state.searchOpen" class="header-panel search-panel" aria-label="全局搜索">
      <div class="search-field">
        <Search :size="18" aria-hidden="true" />
        <input ref="searchInput" v-model="fashion.state.globalQuery" type="search" placeholder="搜索页面、风潮或衣物" aria-label="搜索页面、风潮或衣物" />
      </div>
      <div v-if="normalizedQuery" class="search-results">
        <button v-for="result in searchResults" :key="result.id" type="button" @click="openSearchResult(result)">
          <span>{{ result.title }}</span><small>{{ result.meta }}</small>
        </button>
        <p v-if="!searchResults.length">没有找到匹配内容</p>
      </div>
      <p v-else class="panel-hint">输入名称、类别、颜色或趋势关键词</p>
    </section>

    <section v-if="fashion.state.notificationsOpen" class="header-panel notification-panel" aria-label="通知列表">
      <p class="panel-title">状态通知</p>
      <button v-for="item in notifications" :key="item.id" type="button" @click="fashion.selectView(item.view)">
        <span class="notice-mark"></span><span>{{ item.title }}</span>
      </button>
    </section>

    <div v-if="fashion.state.error" class="error-banner" role="alert">
      <CircleAlert :size="18" aria-hidden="true" />
      <span>{{ fashion.state.error }}</span>
      <button type="button" aria-label="关闭错误提示" @click="fashion.clearError"><X :size="17" /></button>
    </div>

    <main class="app-main">
      <KeepAlive>
        <component :is="currentView" :app="fashion" />
      </KeepAlive>
    </main>
  </div>
</template>
