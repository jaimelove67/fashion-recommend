import { computed, reactive } from 'vue'

const VALID_VIEWS = new Set(['home', 'trend', 'recommend', 'wardrobe', 'history', 'profile'])
const WRITE_METHODS = new Set(['POST', 'PUT', 'PATCH', 'DELETE'])

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

function createGarmentForm() {
  return { name: '', category: '', color: '', style: '', imageUrl: '' }
}

function createRecommendationForm() {
  return { occasion: '通勤', city: '', styleHint: '' }
}

function createProfileForm() {
  return { displayName: '', stylePreferences: '', colorPreferences: '', occasions: '' }
}

async function readApiBody(response) {
  return response.json().catch(() => null)
}

function responseError(response, body, fallback = '接口请求失败') {
  const error = new Error(body?.message || `${fallback} (${response.status})`)
  error.status = response.status
  return error
}

export function useFashionApp() {
  const categories = ['上装', '下装', '鞋履', '外套', '配饰']
  let csrf = null
  let sessionVersion = 0
  const state = reactive({
    authPhase: 'checking',
    authUser: null,
    authSubmitting: false,
    authError: '',
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
    allowAiRecognition: false,
    globalQuery: '',
    searchOpen: false,
    notificationsOpen: false,
    garmentForm: createGarmentForm(),
    recommendationForm: createRecommendationForm(),
    profileForm: createProfileForm()
  })

  function resetPrivateState() {
    sessionVersion += 1
    state.wardrobe = []
    state.history = []
    state.currentRecommendation = null
    state.profile = null
    state.weather = null
    state.wardrobeLoading = false
    state.historyLoading = false
    state.profileLoading = false
    state.weatherLoading = false
    state.generating = false
    state.saving = false
    state.adding = false
    state.profileSaving = false
    state.feedbackSavingId = null
    state.deletingId = null
    state.editingId = null
    state.selectedImage = null
    state.allowAiRecognition = false
    state.globalQuery = ''
    state.searchOpen = false
    state.notificationsOpen = false
    state.garmentForm = createGarmentForm()
    state.recommendationForm = createRecommendationForm()
    state.profileForm = createProfileForm()
    state.error = ''
  }

  function isCurrentSession(version) {
    return state.authPhase === 'authenticated' && version === sessionVersion
  }

  function expireSession() {
    if (state.authPhase !== 'guest') resetPrivateState()
    csrf = null
    state.authUser = null
    state.authPhase = 'guest'
    state.authError = '登录状态已失效，请重新登录。'
  }

  async function refreshCsrf() {
    const response = await fetch('/api/v1/auth/csrf', {
      credentials: 'same-origin',
      headers: { Accept: 'application/json' }
    })
    const body = await readApiBody(response)
    if (!response.ok || !body || body.code !== 0 || !body.data?.headerName || !body.data?.token) {
      throw responseError(response, body, '无法建立安全会话')
    }
    csrf = body.data
    return csrf
  }

  async function ensureCsrf() {
    return csrf || refreshCsrf()
  }

  async function request(path, options = {}, policy = {}) {
    const method = String(options.method || 'GET').toUpperCase()
    const headers = new Headers(options.headers || {})
    headers.set('Accept', 'application/json')
    if (options.body instanceof URLSearchParams && !headers.has('Content-Type')) {
      headers.set('Content-Type', 'application/x-www-form-urlencoded;charset=UTF-8')
    } else if (options.body && !(options.body instanceof FormData) && !headers.has('Content-Type')) {
      headers.set('Content-Type', 'application/json')
    }
    if (WRITE_METHODS.has(method)) {
      const token = await ensureCsrf()
      headers.set(token.headerName, token.token)
    }

    const response = await fetch(path, {
      ...options,
      method,
      credentials: 'same-origin',
      headers
    })
    const body = await readApiBody(response)
    if (response.status === 403 && WRITE_METHODS.has(method) && policy.retryCsrf !== false) {
      csrf = null
      await refreshCsrf()
      return request(path, options, { ...policy, retryCsrf: false })
    }
    if (response.status === 401) {
      if (!policy.allowUnauthorized) expireSession()
      throw responseError(response, body, '请先登录')
    }
    if (!response.ok || !body || body.code !== 0) {
      throw responseError(response, body)
    }
    return body.data
  }

  async function requestMultipart(path, formData) {
    return request(path, {
      method: 'POST',
      body: formData
    })
  }

  function showError(cause) {
    if (cause?.status === 401) return
    state.error = cause instanceof Error ? cause.message : '接口暂时不可用，请稍后重试。'
  }

  function clearError() {
    state.error = ''
  }

  async function loadPrivateData() {
    return Promise.allSettled([loadWardrobe(), loadHistory(), loadProfile()])
  }

  async function completeLogin(username, password) {
    await request('/api/v1/auth/login', {
      method: 'POST',
      body: new URLSearchParams({ username, password })
    }, { allowUnauthorized: true })
    csrf = null
    await refreshCsrf()
    const user = await request('/api/v1/auth/me', {}, { allowUnauthorized: true })
    resetPrivateState()
    state.authUser = user
    state.authPhase = 'authenticated'
    state.authError = ''
    await loadPrivateData()
  }

  async function login(credentials) {
    if (state.authSubmitting) return false
    state.authSubmitting = true
    state.authError = ''
    try {
      await completeLogin(credentials.username, credentials.password)
      return true
    } catch (cause) {
      resetPrivateState()
      state.authUser = null
      state.authPhase = 'guest'
      state.authError = cause instanceof Error ? cause.message : '登录失败，请稍后重试。'
      return false
    } finally {
      state.authSubmitting = false
    }
  }

  async function register(credentials) {
    if (state.authSubmitting) return false
    state.authSubmitting = true
    state.authError = ''
    try {
      await request('/api/v1/auth/register', {
        method: 'POST',
        body: JSON.stringify({ username: credentials.username, password: credentials.password })
      }, { allowUnauthorized: true })
      await completeLogin(credentials.username, credentials.password)
      return true
    } catch (cause) {
      resetPrivateState()
      state.authUser = null
      state.authPhase = 'guest'
      state.authError = cause instanceof Error ? cause.message : '注册失败，请稍后重试。'
      return false
    } finally {
      state.authSubmitting = false
    }
  }

  async function logout() {
    if (state.authSubmitting) return false
    state.authSubmitting = true
    clearError()
    try {
      await request('/api/v1/auth/logout', { method: 'POST' })
      csrf = null
      resetPrivateState()
      state.authUser = null
      state.authPhase = 'guest'
      state.authError = ''
      return true
    } catch (cause) {
      showError(cause)
      return state.authPhase === 'guest'
    } finally {
      state.authSubmitting = false
    }
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
    if (state.authPhase !== 'authenticated' || state.wardrobeLoading) return null
    const version = sessionVersion
    state.wardrobeLoading = true
    try {
      const wardrobe = await request('/api/v1/me/wardrobe')
      if (isCurrentSession(version)) state.wardrobe = wardrobe
      return wardrobe
    } catch (cause) {
      showError(cause)
      return null
    } finally {
      if (isCurrentSession(version)) state.wardrobeLoading = false
    }
  }

  async function loadHistory() {
    if (state.authPhase !== 'authenticated' || state.historyLoading) return null
    const version = sessionVersion
    state.historyLoading = true
    try {
      const history = await request('/api/v1/me/recommendations')
      if (isCurrentSession(version)) state.history = history
      return history
    } catch (cause) {
      showError(cause)
      return null
    } finally {
      if (isCurrentSession(version)) state.historyLoading = false
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
    if (state.authPhase !== 'authenticated' || state.profileLoading) return null
    const version = sessionVersion
    state.profileLoading = true
    try {
      const profile = await request('/api/v1/me/style-profile')
      if (isCurrentSession(version)) {
        state.profile = profile
        hydrateProfileForm(profile)
      }
      return profile
    } catch (cause) {
      showError(cause)
      return null
    } finally {
      if (isCurrentSession(version)) state.profileLoading = false
    }
  }

  async function saveProfile() {
    if (state.authPhase !== 'authenticated') return null
    const version = sessionVersion
    state.profileSaving = true
    clearError()
    try {
      const profile = await request('/api/v1/me/style-profile/refresh', {
        method: 'POST',
        body: JSON.stringify({
          displayName: state.profileForm.displayName,
          stylePreferences: listFromCsv(state.profileForm.stylePreferences),
          colorPreferences: listFromCsv(state.profileForm.colorPreferences),
          occasions: listFromCsv(state.profileForm.occasions)
        })
      })
      if (isCurrentSession(version)) {
        state.profile = profile
        hydrateProfileForm(profile)
      }
      return profile
    } catch (cause) {
      showError(cause)
      return null
    } finally {
      if (isCurrentSession(version)) state.profileSaving = false
    }
  }

  async function loadWeather() {
    if (state.authPhase !== 'authenticated' || !state.recommendationForm.city) return null
    const version = sessionVersion
    state.weatherLoading = true
    clearError()
    try {
      const weather = await request(`/api/v1/weather/current?city=${encodeURIComponent(state.recommendationForm.city)}`)
      if (isCurrentSession(version)) state.weather = weather
      return weather
    } catch (cause) {
      if (isCurrentSession(version)) state.weather = null
      showError(cause)
      return null
    } finally {
      if (isCurrentSession(version)) state.weatherLoading = false
    }
  }

  function resetGarmentForm() {
    state.garmentForm = createGarmentForm()
    state.editingId = null
    state.selectedImage = null
    state.allowAiRecognition = false
  }

  function selectImage(event) {
    state.selectedImage = event?.target?.files?.[0] || null
    state.allowAiRecognition = false
  }

  function editGarment(item) {
    state.editingId = item.id
    state.selectedImage = null
    state.allowAiRecognition = false
    state.garmentForm = {
      name: item.name === '待补充衣物' ? '' : item.name,
      category: categories.includes(item.category) ? item.category : '',
      color: item.color === '待识别' ? '' : item.color,
      style: item.style || '',
      imageUrl: item.imageUrl || ''
    }
  }

  async function addGarment() {
    if (state.authPhase !== 'authenticated') return null
    const version = sessionVersion
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
        if (isCurrentSession(version)) {
          state.wardrobe = state.wardrobe.map((candidate) => candidate.id === item.id ? item : candidate)
        }
      } else if (state.selectedImage) {
        const formData = new FormData()
        formData.append('image', state.selectedImage)
        formData.append('allowAiRecognition', String(state.allowAiRecognition))
        for (const [key, value] of Object.entries(state.garmentForm)) {
          if (key !== 'imageUrl' && value) formData.append(key, value)
        }
        item = await requestMultipart('/api/v1/me/wardrobe/upload', formData)
        if (isCurrentSession(version)) state.wardrobe = [item, ...state.wardrobe]
      } else {
        item = await request('/api/v1/me/wardrobe', {
          method: 'POST',
          body: JSON.stringify(state.garmentForm)
        })
        if (isCurrentSession(version)) state.wardrobe = [item, ...state.wardrobe]
      }
      if (isCurrentSession(version)) resetGarmentForm()
      return item
    } catch (cause) {
      showError(cause)
      return null
    } finally {
      if (isCurrentSession(version)) state.adding = false
    }
  }

  async function deleteGarment(itemId) {
    if (state.authPhase !== 'authenticated') return false
    const version = sessionVersion
    state.deletingId = itemId
    clearError()
    try {
      await request(`/api/v1/me/wardrobe/${itemId}`, { method: 'DELETE' })
      if (isCurrentSession(version)) state.wardrobe = state.wardrobe.filter((item) => item.id !== itemId)
      return true
    } catch (cause) {
      showError(cause)
      return false
    } finally {
      if (isCurrentSession(version)) state.deletingId = null
    }
  }

  async function generateRecommendation() {
    if (state.authPhase !== 'authenticated') return null
    const version = sessionVersion
    state.generating = true
    clearError()
    try {
      const recommendation = await request('/api/v1/recommendations', {
        method: 'POST',
        body: JSON.stringify({ ...state.recommendationForm })
      })
      if (isCurrentSession(version)) {
        state.currentRecommendation = recommendation
        state.weather = recommendation.weather || null
        await loadHistory()
      }
      return recommendation
    } catch (cause) {
      showError(cause)
      return null
    } finally {
      if (isCurrentSession(version)) state.generating = false
    }
  }

  async function rateRecommendation(recommendation, rating) {
    if (state.authPhase !== 'authenticated' || !recommendation) return null
    const version = sessionVersion
    state.feedbackSavingId = recommendation.id
    clearError()
    try {
      const updated = await request(`/api/v1/me/recommendations/${recommendation.id}/feedback`, {
        method: 'POST',
        body: JSON.stringify({ rating, feedbackType: 'rating' })
      })
      if (isCurrentSession(version)) {
        if (state.currentRecommendation?.id === updated.id) state.currentRecommendation = updated
        state.history = state.history.map((item) => item.id === updated.id ? updated : item)
      }
      return updated
    } catch (cause) {
      showError(cause)
      return null
    } finally {
      if (isCurrentSession(version)) state.feedbackSavingId = null
    }
  }

  async function saveRecommendation(recommendation = state.currentRecommendation) {
    if (state.authPhase !== 'authenticated' || !recommendation || recommendation.saved) return recommendation
    const version = sessionVersion
    state.saving = true
    clearError()
    try {
      const saved = await request(`/api/v1/me/recommendations/${recommendation.id}/save`, { method: 'POST' })
      if (isCurrentSession(version)) {
        if (state.currentRecommendation?.id === saved.id) state.currentRecommendation = saved
        const exists = state.history.some((item) => item.id === saved.id)
        state.history = exists
          ? state.history.map((item) => item.id === saved.id ? saved : item)
          : [saved, ...state.history]
      }
      return saved
    } catch (cause) {
      showError(cause)
      return null
    } finally {
      if (isCurrentSession(version)) state.saving = false
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
    const nextView = VALID_VIEWS.has(view) ? view : 'home'
    if (view !== nextView) window.history.replaceState(null, '', `#${nextView}`)
    selectView(nextView, { updateHistory: false, instant: true })
  }

  async function initialize() {
    const requestedView = window.location.hash.slice(1)
    state.activeView = VALID_VIEWS.has(requestedView) ? requestedView : 'home'
    if (!VALID_VIEWS.has(requestedView)) window.history.replaceState(null, '', '#home')
    window.addEventListener('hashchange', syncViewFromLocation)
    state.authPhase = 'checking'
    state.authError = ''
    const trendsPromise = loadTrends()
    try {
      await refreshCsrf()
      const user = await request('/api/v1/auth/me', {}, { allowUnauthorized: true })
      resetPrivateState()
      state.authUser = user
      state.authPhase = 'authenticated'
      state.authError = ''
      await loadPrivateData()
    } catch (cause) {
      resetPrivateState()
      state.authUser = null
      state.authPhase = 'guest'
      state.authError = cause?.status === 401
        ? ''
        : (cause instanceof Error ? cause.message : '暂时无法检查登录状态。')
    }
    await trendsPromise
  }

  function dispose() {
    window.removeEventListener('hashchange', syncViewFromLocation)
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
    login,
    register,
    logout,
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
