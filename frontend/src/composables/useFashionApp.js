import { computed, reactive } from 'vue'

const USER_ID = 'demo-user'
const VALID_VIEWS = new Set(['home', 'trend', 'recommend', 'wardrobe', 'history', 'profile'])

const COLOR_MAP = {
  '低饱和': '#9da6a1',
  '冷灰白': '#e7e8e5',
  '暖白': '#eee9df',
  '米白': '#e6dccb',
  '雾蓝': '#8fa5b1',
  '石墨灰': '#4f5553',
  '橄榄绿': '#74785c',
  '黑色': '#171817',
  '白色': '#f4f3ef',
  '蓝色': '#68859b',
  '棕色': '#8f745e',
  '卡其': '#b09a79'
}

const FALLBACK_COLORS = ['#9da6a1', '#b29a7a', '#77878d', '#746f67', '#879276', '#8f7d86']

function listFromCsv(value = '') {
  return value.split(/[，,、]/).map((item) => item.trim()).filter(Boolean)
}

function startOfLocalDay(date) {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate())
}

export function useFashionApp() {
  const categories = ['上装', '下装', '鞋履', '外套', '配饰']
  const state = reactive({
    activeView: 'home',
    trends: [],
    trendMeta: { primarySource: '', fetchedAt: null, demoMode: false },
    selectedTrendId: null,
    wardrobe: [],
    history: [],
    currentRecommendation: null,
    profile: null,
    weather: null,
    error: '',
    wardrobeLoading: false,
    historyLoading: false,
    trendsLoading: false,
    profileLoading: false,
    weatherLoading: false,
    generating: false,
    saving: false,
    adding: false,
    profileSaving: false,
    feedbackSavingId: null,
    deletingId: null,
    editingId: null,
    selectedImage: null,
    globalQuery: '',
    searchOpen: false,
    notificationsOpen: false,
    garmentForm: { name: '', category: '上装', color: '', style: '', imageUrl: '' },
    recommendationForm: { occasion: '通勤', city: '', styleHint: '' },
    profileForm: { displayName: '', stylePreferences: '', colorPreferences: '', occasions: '' }
  })

  async function request(path, options = {}) {
    const response = await fetch(path, {
      ...options,
      headers: { 'Content-Type': 'application/json', 'X-User-Id': USER_ID, ...(options.headers || {}) }
    })
    const body = await response.json().catch(() => null)
    if (!response.ok || !body || body.code !== 0) {
      throw new Error(body?.message || `接口请求失败 (${response.status})`)
    }
    return body.data
  }

  async function requestMultipart(path, formData) {
    const response = await fetch(path, {
      method: 'POST',
      headers: { 'X-User-Id': USER_ID },
      body: formData
    })
    const body = await response.json().catch(() => null)
    if (!response.ok || !body || body.code !== 0) {
      throw new Error(body?.message || `图片上传失败 (${response.status})`)
    }
    return body.data
  }

  function showError(cause) {
    state.error = cause instanceof Error ? cause.message : '接口暂时不可用，请稍后重试。'
  }

  function clearError() {
    state.error = ''
  }

  async function loadTrends() {
    state.trendsLoading = true
    try {
      const feed = await request('/api/v1/trends')
      state.trends = feed.items || []
      state.trendMeta = {
        primarySource: feed.primarySource || '',
        fetchedAt: feed.fetchedAt || null,
        demoMode: Boolean(feed.demoMode)
      }
      if (!state.selectedTrendId || !state.trends.some((item) => item.id === state.selectedTrendId)) {
        state.selectedTrendId = state.trends[0]?.id || null
      }
    } catch (cause) {
      showError(cause)
    } finally {
      state.trendsLoading = false
    }
  }

  async function loadWardrobe() {
    state.wardrobeLoading = true
    try {
      state.wardrobe = await request('/api/v1/me/wardrobe')
    } catch (cause) {
      showError(cause)
    } finally {
      state.wardrobeLoading = false
    }
  }

  async function loadHistory() {
    state.historyLoading = true
    try {
      state.history = await request('/api/v1/me/recommendations')
    } catch (cause) {
      showError(cause)
    } finally {
      state.historyLoading = false
    }
  }

  function hydrateProfileForm(profile) {
    state.profileForm = {
      displayName: profile?.displayName || '',
      stylePreferences: (profile?.stylePreferences || []).join('、'),
      colorPreferences: (profile?.colorPreferences || []).join('、'),
      occasions: (profile?.occasions || []).join('、')
    }
  }

  async function loadProfile() {
    state.profileLoading = true
    try {
      state.profile = await request('/api/v1/me/style-profile')
      hydrateProfileForm(state.profile)
    } catch (cause) {
      showError(cause)
    } finally {
      state.profileLoading = false
    }
  }

  async function saveProfile() {
    state.profileSaving = true
    clearError()
    try {
      state.profile = await request('/api/v1/me/style-profile/refresh', {
        method: 'POST',
        body: JSON.stringify({
          displayName: state.profileForm.displayName,
          stylePreferences: listFromCsv(state.profileForm.stylePreferences),
          colorPreferences: listFromCsv(state.profileForm.colorPreferences),
          occasions: listFromCsv(state.profileForm.occasions)
        })
      })
      hydrateProfileForm(state.profile)
      return state.profile
    } catch (cause) {
      showError(cause)
      return null
    } finally {
      state.profileSaving = false
    }
  }

  async function loadWeather() {
    if (!state.recommendationForm.city) return null
    state.weatherLoading = true
    clearError()
    try {
      state.weather = await request(`/api/v1/weather/current?city=${encodeURIComponent(state.recommendationForm.city)}`)
      return state.weather
    } catch (cause) {
      state.weather = null
      showError(cause)
      return null
    } finally {
      state.weatherLoading = false
    }
  }

  function resetGarmentForm() {
    state.garmentForm = { name: '', category: '上装', color: '', style: '', imageUrl: '' }
    state.editingId = null
    state.selectedImage = null
  }

  function selectImage(event) {
    state.selectedImage = event?.target?.files?.[0] || null
  }

  function editGarment(item) {
    state.editingId = item.id
    state.selectedImage = null
    state.garmentForm = {
      name: item.name === '待补充衣物' ? '' : item.name,
      category: categories.includes(item.category) ? item.category : '上装',
      color: item.color === '待识别' ? '' : item.color,
      style: item.style || '',
      imageUrl: item.imageUrl || ''
    }
  }

  async function addGarment() {
    state.adding = true
    clearError()
    try {
      let item
      if (state.editingId) {
        item = await request(`/api/v1/me/wardrobe/${state.editingId}`, {
          method: 'PUT',
          body: JSON.stringify({
            name: state.garmentForm.name,
            category: state.garmentForm.category,
            color: state.garmentForm.color,
            style: state.garmentForm.style
          })
        })
        state.wardrobe = state.wardrobe.map((candidate) => candidate.id === item.id ? item : candidate)
      } else if (state.selectedImage) {
        const formData = new FormData()
        formData.append('image', state.selectedImage)
        for (const [key, value] of Object.entries(state.garmentForm)) {
          if (key !== 'imageUrl' && value) formData.append(key, value)
        }
        item = await requestMultipart('/api/v1/me/wardrobe/upload', formData)
        state.wardrobe = [item, ...state.wardrobe]
      } else {
        item = await request('/api/v1/me/wardrobe', {
          method: 'POST',
          body: JSON.stringify(state.garmentForm)
        })
        state.wardrobe = [item, ...state.wardrobe]
      }
      resetGarmentForm()
      return item
    } catch (cause) {
      showError(cause)
      return null
    } finally {
      state.adding = false
    }
  }

  async function deleteGarment(itemId) {
    state.deletingId = itemId
    clearError()
    try {
      await request(`/api/v1/me/wardrobe/${itemId}`, { method: 'DELETE' })
      state.wardrobe = state.wardrobe.filter((item) => item.id !== itemId)
      return true
    } catch (cause) {
      showError(cause)
      return false
    } finally {
      state.deletingId = null
    }
  }

  async function generateRecommendation() {
    state.generating = true
    clearError()
    try {
      state.currentRecommendation = await request('/api/v1/recommendations', {
        method: 'POST',
        body: JSON.stringify({ ...state.recommendationForm })
      })
      state.weather = state.currentRecommendation.weather || null
      await loadHistory()
      return state.currentRecommendation
    } catch (cause) {
      showError(cause)
      return null
    } finally {
      state.generating = false
    }
  }

  async function rateRecommendation(recommendation, rating) {
    if (!recommendation) return null
    state.feedbackSavingId = recommendation.id
    clearError()
    try {
      const updated = await request(`/api/v1/me/recommendations/${recommendation.id}/feedback`, {
        method: 'POST',
        body: JSON.stringify({ rating, feedbackType: 'rating' })
      })
      if (state.currentRecommendation?.id === updated.id) state.currentRecommendation = updated
      state.history = state.history.map((item) => item.id === updated.id ? updated : item)
      return updated
    } catch (cause) {
      showError(cause)
      return null
    } finally {
      state.feedbackSavingId = null
    }
  }

  async function saveRecommendation(recommendation = state.currentRecommendation) {
    if (!recommendation || recommendation.saved) return recommendation
    state.saving = true
    clearError()
    try {
      const saved = await request(`/api/v1/me/recommendations/${recommendation.id}/save`, { method: 'POST' })
      if (state.currentRecommendation?.id === saved.id) state.currentRecommendation = saved
      const exists = state.history.some((item) => item.id === saved.id)
      state.history = exists
        ? state.history.map((item) => item.id === saved.id ? saved : item)
        : [saved, ...state.history]
      return saved
    } catch (cause) {
      showError(cause)
      return null
    } finally {
      state.saving = false
    }
  }

  const wardrobeStats = computed(() => {
    const now = Date.now()
    const byCategory = Object.fromEntries(categories.map((category) => [category, 0]))
    let weeklyAdded = 0
    let review = 0
    for (const item of state.wardrobe) {
      if (Object.hasOwn(byCategory, item.category)) byCategory[item.category] += 1
      if (item.createdAt && now - new Date(item.createdAt).getTime() <= 7 * 86400000) weeklyAdded += 1
      if (item.recognitionStatus === 'NEEDS_MANUAL_REVIEW') review += 1
    }
    return {
      total: state.wardrobe.length,
      weeklyAdded,
      ready: state.wardrobe.length - review,
      review,
      byCategory
    }
  })

  const savedHistory = computed(() => state.history.filter((item) => item.saved))

  const recommendationStats = computed(() => {
    const ratings = state.history.map((item) => item.feedback?.rating).filter(Number.isFinite)
    const covered = new Set()
    for (const recommendation of state.history) {
      for (const item of recommendation.items || []) covered.add(item.id || item.name)
    }
    return {
      total: state.history.length,
      saved: savedHistory.value.length,
      averageRating: ratings.length ? ratings.reduce((sum, value) => sum + value, 0) / ratings.length : null,
      coveredItems: covered.size
    }
  })

  const trendStats = computed(() => {
    const scores = state.trends.map((item) => Number(item.heatScore) || 0)
    const tagCounts = new Map()
    for (const item of state.trends) {
      for (const tag of item.topicTags || []) tagCounts.set(tag, (tagCounts.get(tag) || 0) + 1)
    }
    const topTag = [...tagCounts.entries()].sort((a, b) => b[1] - a[1])[0]?.[0] || '暂无'
    return {
      count: state.trends.length,
      averageHeat: scores.length ? Math.round(scores.reduce((sum, value) => sum + value, 0) / scores.length) : 0,
      topTag
    }
  })

  const topTrend = computed(() => state.trends.find((item) => item.id === state.selectedTrendId) || state.trends[0] || null)

  const profileScore = computed(() => {
    if (!state.profile) return 0
    const fields = [
      state.profile.stylePreferences,
      state.profile.colorPreferences,
      state.profile.occasions,
      state.profile.styleTags
    ]
    return fields.reduce((score, values) => score + (values?.length ? 25 : 0), 0)
  })

  const profileDimensions = computed(() => {
    const profile = state.profile
    const richness = (values, step) => values?.length ? Math.min(100, 40 + values.length * step) : 0
    return [
      { label: '风格偏好', value: richness(profile?.stylePreferences, 24) },
      { label: '颜色方向', value: richness(profile?.colorPreferences, 20) },
      { label: '场合覆盖', value: richness(profile?.occasions, 20) },
      { label: '档案状态', value: profile ? (profile.stale ? 60 : 100) : 0 }
    ]
  })

  const historyTrend = computed(() => {
    const today = startOfLocalDay(new Date())
    return Array.from({ length: 7 }, (_, index) => {
      const day = new Date(today)
      day.setDate(today.getDate() - (6 - index))
      const nextDay = new Date(day)
      nextDay.setDate(day.getDate() + 1)
      const matches = state.history.filter((item) => {
        const generated = new Date(item.generatedAt)
        return generated >= day && generated < nextDay
      })
      return {
        key: day.toISOString().slice(0, 10),
        label: `${day.getMonth() + 1}/${day.getDate()}`,
        count: matches.length,
        saved: matches.filter((item) => item.saved).length
      }
    })
  })

  function colorFor(name = '') {
    if (COLOR_MAP[name]) return COLOR_MAP[name]
    for (const [keyword, color] of Object.entries(COLOR_MAP)) {
      if (name.includes(keyword)) return color
    }
    let hash = 0
    for (const char of name) hash = (hash * 31 + char.charCodeAt(0)) >>> 0
    return FALLBACK_COLORS[hash % FALLBACK_COLORS.length]
  }

  async function ensureViewData(view) {
    if (view === 'trend' && !state.trends.length) await loadTrends()
    if (view === 'wardrobe' && !state.wardrobe.length) await loadWardrobe()
    if (view === 'history') await loadHistory()
    if (view === 'profile' && !state.profile) await loadProfile()
  }

  async function selectView(view, options = {}) {
    const nextView = VALID_VIEWS.has(view) ? view : 'home'
    state.activeView = nextView
    state.searchOpen = false
    state.notificationsOpen = false
    if (options.updateHistory !== false && window.location.hash !== `#${nextView}`) {
      window.history.pushState(null, '', `#${nextView}`)
    }
    window.scrollTo({ top: 0, behavior: options.instant ? 'auto' : 'smooth' })
    await ensureViewData(nextView)
  }

  function syncViewFromLocation() {
    const view = window.location.hash.slice(1)
    selectView(VALID_VIEWS.has(view) ? view : 'home', { updateHistory: false, instant: true })
  }

  async function initialize() {
    const requestedView = window.location.hash.slice(1)
    state.activeView = VALID_VIEWS.has(requestedView) ? requestedView : 'home'
    if (!VALID_VIEWS.has(requestedView)) window.history.replaceState(null, '', '#home')
    window.addEventListener('popstate', syncViewFromLocation)
    await Promise.allSettled([loadTrends(), loadWardrobe(), loadHistory(), loadProfile()])
  }

  function dispose() {
    window.removeEventListener('popstate', syncViewFromLocation)
  }

  return {
    state,
    categories,
    wardrobeStats,
    savedHistory,
    recommendationStats,
    trendStats,
    topTrend,
    profileScore,
    profileDimensions,
    historyTrend,
    colorFor,
    initialize,
    dispose,
    clearError,
    loadTrends,
    loadWardrobe,
    loadHistory,
    loadProfile,
    saveProfile,
    loadWeather,
    resetGarmentForm,
    selectImage,
    editGarment,
    addGarment,
    deleteGarment,
    generateRecommendation,
    rateRecommendation,
    saveRecommendation,
    selectView
  }
}
