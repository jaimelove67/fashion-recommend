import { computed, reactive, nextTick } from 'vue'

const VALID_VIEWS = new Set(['home', 'trend', 'recommend', 'wardrobe', 'history', 'profile', 'admin'])
const WRITE_METHODS = new Set(['POST', 'PUT', 'PATCH', 'DELETE'])
const ADMIN_PAGE_SIZE = 20

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
const WEATHER_CITY_STORAGE_KEY = 'fashion.weather.city'
const WEATHER_LOCATION_STORAGE_KEY = 'fashion.weather.location'

function listFromCsv(value = '') {
  return value.split(/[，,、]/).map((item) => item.trim()).filter(Boolean)
}

function readLocalStorage(key) {
  if (typeof window === 'undefined') return ''
  try {
    return window.localStorage.getItem(key) || ''
  } catch {
    return ''
  }
}

function readStoredWeatherCity() {
  return readLocalStorage(WEATHER_CITY_STORAGE_KEY)
}

function readStoredWeatherLocation() {
  const value = readLocalStorage(WEATHER_LOCATION_STORAGE_KEY)
  if (!value) return null
  try {
    const parsed = JSON.parse(value)
    if (Number.isFinite(parsed.latitude) && Number.isFinite(parsed.longitude)) return parsed
  } catch {
    // Ignore malformed browser-only state and ask for location again.
  }
  return null
}

function writeLocalStorage(key, value) {
  if (typeof window === 'undefined') return
  try {
    if (value === null) window.localStorage.removeItem(key)
    else window.localStorage.setItem(key, value)
  } catch {
    // Weather preferences are optional and must not block the main application.
  }
}

function startOfLocalDay(date) {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate())
}

function createGarmentForm() {
  return { name: '', category: '', color: '', style: '', imageUrl: '' }
}

function createRecommendationForm() {
  return { occasion: '通勤', city: readStoredWeatherCity(), styleHint: '' }
}

function createProfileForm() {
  return { displayName: '', stylePreferences: '', colorPreferences: '', occasions: '' }
}

async function readApiBody(response) {
  return response.json().catch(() => null)
}

function responseError(response, body, fallback = '请求失败，请稍后再试。') {
  const error = new Error(body?.message || `${fallback} (${response.status})`)
  error.status = response.status
  return error
}

export function useFashionApp() {
  const categories = ['上装', '下装', '鞋履', '外套', '配饰']
  let csrf = null
  let sessionVersion = 0
  let trendRequestVersion = 0
  const state = reactive({
    authPhase: 'checking',
    authUser: null,
    authSubmitting: false,
    authError: '',
    activeView: 'home',
    trends: [],
    trendPeriod: 'week',
    trendPlatform: '',
    trendStyles: [],
    trendError: '',
    selectedTrendReference: null,
    trendMeta: { primarySource: '', fetchedAt: null, demoMode: false, scoreLabel: '热度' },
    selectedTrendId: null,
    wardrobe: [],
    history: [],
    historyTotal: 0,
    historyPage: 0,
    historyHasNext: false,
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
    adminOverview: null,
    adminOverviewLoading: false,
    adminUsers: [],
    adminUsersTotal: 0,
    adminUsersPage: 0,
    adminUsersHasNext: false,
    adminUsersLoading: false,
    adminUserAction: null,
    adminQuery: '',
    adminFeedback: [],
    adminFeedbackTotal: 0,
    adminFeedbackPage: 0,
    adminFeedbackHasNext: false,
    adminFeedbackLoading: false,
    adminFeedbackAction: null,
    adminFeedbackStatus: 'PENDING',
    adminFeedbackQuery: '',
    adminTrendContents: [],
    adminTrendTotal: 0,
    adminTrendPage: 0,
    adminTrendHasNext: false,
    adminTrendLoading: false,
    adminTrendAction: null,
    adminTrendStatus: 'PENDING_HUMAN',
    adminTrendQuery: '',
    adminTrendRun: null,
    adminAuditLogs: [],
    adminAuditTotal: 0,
    adminAuditPage: 0,
    adminAuditHasNext: false,
    adminAuditLoading: false,
    adminAuditAction: '',
    adminAuditOutcome: '',
    globalQuery: '',
    searchOpen: false,
    notificationsOpen: false,
    garmentForm: createGarmentForm(),
    recommendationForm: createRecommendationForm(),
    profileForm: createProfileForm()
  })

  function resetPrivateState() {
    state.selectedTrendReference = null
    sessionVersion += 1
    state.wardrobe = []
    state.history = []
    state.historyTotal = 0
    state.historyPage = 0
    state.historyHasNext = false
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
    state.adminOverview = null
    state.adminOverviewLoading = false
    state.adminUsers = []
    state.adminUsersTotal = 0
    state.adminUsersPage = 0
    state.adminUsersHasNext = false
    state.adminUsersLoading = false
    state.adminUserAction = null
    state.adminQuery = ''
    state.adminFeedback = []
    state.adminFeedbackTotal = 0
    state.adminFeedbackPage = 0
    state.adminFeedbackHasNext = false
    state.adminFeedbackLoading = false
    state.adminFeedbackAction = null
    state.adminFeedbackStatus = 'PENDING'
    state.adminFeedbackQuery = ''
    state.adminTrendContents = []
    state.adminTrendTotal = 0
    state.adminTrendPage = 0
    state.adminTrendHasNext = false
    state.adminTrendLoading = false
    state.adminTrendAction = null
    state.adminTrendStatus = 'PENDING_HUMAN'
    state.adminTrendQuery = ''
    state.adminTrendRun = null
    state.adminAuditLogs = []
    state.adminAuditTotal = 0
    state.adminAuditPage = 0
    state.adminAuditHasNext = false
    state.adminAuditLoading = false
    state.adminAuditAction = ''
    state.adminAuditOutcome = ''
    state.globalQuery = ''
    state.searchOpen = false
    state.notificationsOpen = false
    state.garmentForm = createGarmentForm()
    state.recommendationForm = createRecommendationForm()
    state.profileForm = createProfileForm()
    state.error = ''
  }

  const isAdmin = computed(() => state.authUser?.authorities?.includes('ROLE_ADMIN') === true)

  function isCurrentSession(version) {
    return state.authPhase === 'authenticated' && version === sessionVersion
  }

  function expireSession() {
    if (state.authPhase !== 'guest') resetPrivateState()
    csrf = null
    state.authUser = null
    state.authPhase = 'guest'
    state.authError = '登录已过期，请重新登录。'
  }

  async function refreshCsrf() {
    const response = await fetch('/api/v1/auth/csrf', {
      credentials: 'same-origin',
      headers: { Accept: 'application/json' }
    })
    const body = await readApiBody(response)
    if (!response.ok || !body || body.code !== 0 || !body.data?.headerName || !body.data?.token) {
      throw responseError(response, body, '无法建立登录会话')
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
      throw responseError(response, body, '请先登录账户')
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
    state.error = cause instanceof Error ? cause.message : '服务暂时不可用，请稍后再试。'
  }

  function clearError() {
    state.error = ''
  }

  function clearWeather() {
    state.weather = null
    writeLocalStorage(WEATHER_CITY_STORAGE_KEY, null)
    writeLocalStorage(WEATHER_LOCATION_STORAGE_KEY, null)
  }

  async function loadPrivateData() {
    const requests = [loadWardrobe(), loadHistory(), loadProfile()]
    if (isAdmin.value) requests.push(
      loadAdminOverview(),
      loadAdminUsers(),
      loadAdminFeedback(),
      loadAdminTrendContents(),
      loadAdminAuditLogs())
    return Promise.allSettled(requests)
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
    if (state.activeView === 'admin' && !isAdmin.value) {
      state.activeView = 'home'
      window.history.replaceState(null, '', '#home')
    } else if (isAdmin.value && state.activeView === 'home') {
      state.activeView = 'admin'
      window.history.replaceState(null, '', '#admin')
    }
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
      state.authError = cause instanceof Error ? cause.message : '登录失败，请稍后再试。'
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
      state.authError = cause instanceof Error ? cause.message : '注册失败，请稍后再试。'
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
    const version = ++trendRequestVersion
    state.trendsLoading = true
    state.trendError = ''
    try {
      const params = new URLSearchParams({ period: state.trendPeriod, platform: state.trendPlatform })
      const feed = await request(`/api/v1/trends?${params}`)
      if (version !== trendRequestVersion) return
      state.trends = feed.items || []
      state.trendStyles = feed.styles || []
      state.trendMeta = {
        primarySource: feed.primarySource || '',
        fetchedAt: feed.fetchedAt || null,
        demoMode: Boolean(feed.demoMode),
        scoreLabel: feed.scoreLabel || '来源内评分',
        sources: feed.sources || [],
        notice: feed.notice || ''
      }
      if (!state.selectedTrendId || !state.trends.some((item) => item.id === state.selectedTrendId)) {
        state.selectedTrendId = state.trends[0]?.id || null
      }
    } catch (cause) {
      if (version === trendRequestVersion) state.trendError = '趋势暂时无法加载，请稍后重试。'
    } finally {
      if (version === trendRequestVersion) state.trendsLoading = false
    }
  }

  async function useTrend(item) {
    state.selectedTrendReference = item
    state.recommendationForm.trendId = item.id
    state.recommendationForm.styleHint = (item.topicTags || []).join('、').slice(0, 120)
    await selectView('recommend')
    await nextTick()
    document.querySelector('.trend-reference-banner')?.focus()
  }

  function clearTrendReference() {
    state.selectedTrendReference = null
    delete state.recommendationForm.trendId
  }

  async function refreshTrendSources() {
    if (!isAdmin.value || state.trendsLoading) return
    state.trendsLoading = true
    try {
      await request('/api/v1/admin/trends/refresh', { method: 'POST' })
    } catch (cause) { showError(cause) }
    finally { await loadTrends() }
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

  async function loadHistory(options = {}) {
    if (state.authPhase !== 'authenticated' || state.historyLoading) return null
    const append = options.append === true
    const page = append ? state.historyPage + 1 : 0
    const version = sessionVersion
    state.historyLoading = true
    try {
      const historyPage = await request(`/api/v1/me/recommendations?page=${page}&size=20`)
      if (isCurrentSession(version)) {
        state.history = append ? [...state.history, ...historyPage.content] : historyPage.content
        state.historyTotal = historyPage.totalElements
        state.historyPage = historyPage.page
        state.historyHasNext = historyPage.hasNext
      }
      return historyPage
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

  async function loadAdminOverview() {
    if (!isAdmin.value || state.adminOverviewLoading) return null
    const version = sessionVersion
    state.adminOverviewLoading = true
    try {
      const overview = await request('/api/v1/admin/overview')
      if (isCurrentSession(version)) state.adminOverview = overview
      return overview
    } catch (cause) {
      showError(cause)
      return null
    } finally {
      if (isCurrentSession(version)) state.adminOverviewLoading = false
    }
  }

  async function loadAdminUsers(options = {}) {
    if (!isAdmin.value || state.adminUsersLoading) return null
    const append = options.append === true
    const page = Number.isInteger(options.page)
      ? options.page
      : (append ? state.adminUsersPage + 1 : 0)
    const version = sessionVersion
    state.adminUsersLoading = true
    try {
      const params = new URLSearchParams({ page: String(page), size: String(ADMIN_PAGE_SIZE) })
      const query = state.adminQuery.trim()
      if (query) params.set('query', query)
      const result = await request(`/api/v1/admin/users?${params.toString()}`)
      if (isCurrentSession(version)) {
        state.adminUsers = result.items || []
        state.adminUsersTotal = Number(result.totalElements || 0)
        state.adminUsersPage = Number(result.page || 0)
        state.adminUsersHasNext = Boolean(result.hasNext)
      }
      return result
    } catch (cause) {
      showError(cause)
      return null
    } finally {
      if (isCurrentSession(version)) state.adminUsersLoading = false
    }
  }

  async function updateAdminUserStatus(username, enabled) {
    if (!isAdmin.value || !username || state.adminUserAction) return null
    const version = sessionVersion
    state.adminUserAction = username
    clearError()
    try {
      const updated = await request(`/api/v1/admin/users/${encodeURIComponent(username)}/status`, {
        method: 'PUT',
        body: JSON.stringify({ enabled })
      })
      if (isCurrentSession(version)) {
        const index = state.adminUsers.findIndex((item) => item.username === username)
        if (index >= 0) state.adminUsers.splice(index, 1, updated)
        await loadAdminOverview()
        await loadAdminAuditLogs({ page: 0 })
      }
      return updated
    } catch (cause) {
      showError(cause)
      return null
    } finally {
      if (isCurrentSession(version)) state.adminUserAction = null
    }
  }

  async function loadAdminFeedback(options = {}) {
    if (!isAdmin.value || state.adminFeedbackLoading) return null
    const page = Number.isInteger(options.page) ? options.page : 0
    const version = sessionVersion
    state.adminFeedbackLoading = true
    try {
      const params = new URLSearchParams({ page: String(page), size: String(ADMIN_PAGE_SIZE) })
      if (state.adminFeedbackStatus && state.adminFeedbackStatus !== 'ALL') {
        params.set('status', state.adminFeedbackStatus)
      }
      const query = state.adminFeedbackQuery.trim()
      if (query) params.set('query', query)
      const result = await request(`/api/v1/admin/feedback?${params.toString()}`)
      if (isCurrentSession(version)) {
        state.adminFeedback = result.items || []
        state.adminFeedbackTotal = Number(result.totalElements || 0)
        state.adminFeedbackPage = Number(result.page || 0)
        state.adminFeedbackHasNext = Boolean(result.hasNext)
      }
      return result
    } catch (cause) {
      showError(cause)
      return null
    } finally {
      if (isCurrentSession(version)) state.adminFeedbackLoading = false
    }
  }

  async function updateAdminFeedbackStatus(recommendationId, status) {
    if (!isAdmin.value || !recommendationId || state.adminFeedbackAction) return null
    const version = sessionVersion
    state.adminFeedbackAction = `${recommendationId}:${status}`
    clearError()
    try {
      const updated = await request(`/api/v1/admin/feedback/${recommendationId}/status`, {
        method: 'PUT',
        body: JSON.stringify({ status })
      })
      if (isCurrentSession(version)) {
        const index = state.adminFeedback.findIndex((item) => item.recommendationId === recommendationId)
        if (index >= 0) state.adminFeedback.splice(index, 1, updated)
        await loadAdminOverview()
        await loadAdminAuditLogs({ page: 0 })
        if (state.adminFeedbackStatus !== 'ALL' && state.adminFeedbackStatus !== updated.moderationStatus) {
          await loadAdminFeedback({ page: state.adminFeedbackPage })
        }
      }
      return updated
    } catch (cause) {
      showError(cause)
      return null
    } finally {
      if (isCurrentSession(version)) state.adminFeedbackAction = null
    }
  }

  async function loadAdminTrendContents(options = {}) {
    if (!isAdmin.value || state.adminTrendLoading) return null
    const page = Number.isInteger(options.page) ? options.page : 0
    const version = sessionVersion
    state.adminTrendLoading = true
    try {
      const params = new URLSearchParams({ page: String(page), size: String(ADMIN_PAGE_SIZE) })
      if (state.adminTrendStatus && state.adminTrendStatus !== 'ALL') {
        params.set('status', state.adminTrendStatus)
      }
      const query = state.adminTrendQuery.trim()
      if (query) params.set('query', query)
      const result = await request(`/api/v1/admin/trends/contents?${params.toString()}`)
      if (isCurrentSession(version)) {
        state.adminTrendContents = result.items || []
        state.adminTrendTotal = Number(result.totalElements || 0)
        state.adminTrendPage = Number(result.page || 0)
        state.adminTrendHasNext = Boolean(result.hasNext)
      }
      return result
    } catch (cause) {
      showError(cause)
      return null
    } finally {
      if (isCurrentSession(version)) state.adminTrendLoading = false
    }
  }

  async function runAdminTrendAiReview(limit = 10) {
    if (!isAdmin.value || state.adminTrendAction) return null
    const version = sessionVersion
    state.adminTrendAction = 'ai-review'
    clearError()
    try {
      const run = await request(`/api/v1/admin/trends/ai-review?limit=${encodeURIComponent(limit)}`, {
        method: 'POST'
      })
      if (isCurrentSession(version)) {
        state.adminTrendRun = run
        await loadAdminTrendContents({ page: state.adminTrendPage })
        await loadAdminAuditLogs({ page: 0 })
      }
      return run
    } catch (cause) {
      showError(cause)
      return null
    } finally {
      if (isCurrentSession(version)) state.adminTrendAction = null
    }
  }

  async function retryAdminTrendAi(id) {
    if (!isAdmin.value || !id || state.adminTrendAction) return null
    const version = sessionVersion
    state.adminTrendAction = `retry:${id}`
    clearError()
    try {
      const updated = await request(`/api/v1/admin/trends/contents/${encodeURIComponent(id)}/retry-ai`, {
        method: 'POST'
      })
      if (isCurrentSession(version)) {
        await loadAdminTrendContents({ page: state.adminTrendPage })
        await loadAdminAuditLogs({ page: 0 })
      }
      return updated
    } catch (cause) {
      showError(cause)
      return null
    } finally {
      if (isCurrentSession(version)) state.adminTrendAction = null
    }
  }

  async function finalizeAdminTrendReview(id, status, note = '') {
    if (!isAdmin.value || !id || !status || state.adminTrendAction) return null
    const version = sessionVersion
    state.adminTrendAction = `review:${id}`
    clearError()
    try {
      const updated = await request(`/api/v1/admin/trends/contents/${encodeURIComponent(id)}/review`, {
        method: 'PUT',
        body: JSON.stringify({ status, note })
      })
      if (isCurrentSession(version)) {
        await loadAdminTrendContents({ page: state.adminTrendPage })
        await loadAdminAuditLogs({ page: 0 })
        await loadTrends()
      }
      return updated
    } catch (cause) {
      showError(cause)
      return null
    } finally {
      if (isCurrentSession(version)) state.adminTrendAction = null
    }
  }

  async function updateAdminTrendVisibility(id, hidden) {
    if (!isAdmin.value || !id || state.adminTrendAction) return null
    const version = sessionVersion
    state.adminTrendAction = `visibility:${id}`
    clearError()
    try {
      const updated = await request(`/api/v1/admin/trends/${encodeURIComponent(id)}/visibility`, {
        method: 'PUT',
        body: JSON.stringify({ hidden })
      })
      if (isCurrentSession(version)) {
        const index = state.adminTrendContents.findIndex((item) => item.id === id)
        if (index >= 0) state.adminTrendContents.splice(index, 1, updated)
        await loadAdminAuditLogs({ page: 0 })
        await loadTrends()
      }
      return updated
    } catch (cause) {
      showError(cause)
      return null
    } finally {
      if (isCurrentSession(version)) state.adminTrendAction = null
    }
  }

  async function loadAdminAuditLogs(options = {}) {
    if (!isAdmin.value || state.adminAuditLoading) return null
    const page = Number.isInteger(options.page) ? options.page : 0
    const version = sessionVersion
    state.adminAuditLoading = true
    try {
      const params = new URLSearchParams({ page: String(page), size: String(ADMIN_PAGE_SIZE) })
      if (state.adminAuditAction) params.set('action', state.adminAuditAction)
      if (state.adminAuditOutcome) params.set('outcome', state.adminAuditOutcome)
      const result = await request(`/api/v1/admin/audit-logs?${params.toString()}`)
      if (isCurrentSession(version)) {
        state.adminAuditLogs = result.items || []
        state.adminAuditTotal = Number(result.totalElements || 0)
        state.adminAuditPage = Number(result.page || 0)
        state.adminAuditHasNext = Boolean(result.hasNext)
      }
      return result
    } catch (cause) {
      showError(cause)
      return null
    } finally {
      if (isCurrentSession(version)) state.adminAuditLoading = false
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
      if (isCurrentSession(version)) {
        state.weather = weather
        writeLocalStorage(WEATHER_CITY_STORAGE_KEY, state.recommendationForm.city.trim())
        writeLocalStorage(WEATHER_LOCATION_STORAGE_KEY, null)
      }
      return weather
    } catch (cause) {
      if (isCurrentSession(version)) state.weather = null
      showError(cause)
      return null
    } finally {
      if (isCurrentSession(version)) state.weatherLoading = false
    }
  }

  function browserLocation() {
    return new Promise((resolve, reject) => {
      if (typeof navigator === 'undefined' || !navigator.geolocation) {
        reject(new Error('此浏览器不支持定位。'))
        return
      }
      navigator.geolocation.getCurrentPosition(
        ({ coords }) => resolve({ latitude: coords.latitude, longitude: coords.longitude }),
        reject,
        { enableHighAccuracy: false, maximumAge: 10 * 60 * 1000, timeout: 6000 }
      )
    })
  }

  async function loadLocalWeather(options = {}) {
    if (state.authPhase !== 'authenticated' || state.weatherLoading) return null
    const version = sessionVersion
    state.weatherLoading = true
    if (!options.silent) clearError()
    try {
      const coordinates = options.coordinates || readStoredWeatherLocation() || await browserLocation()
      const query = new URLSearchParams({
        latitude: coordinates.latitude.toFixed(4),
        longitude: coordinates.longitude.toFixed(4)
      })
      const weather = await request(`/api/v1/weather/current?${query.toString()}`)
      if (isCurrentSession(version)) {
        state.weather = weather
        writeLocalStorage(WEATHER_CITY_STORAGE_KEY, null)
        writeLocalStorage(WEATHER_LOCATION_STORAGE_KEY, JSON.stringify(coordinates))
      }
      return weather
    } catch (cause) {
      if (isCurrentSession(version) && !options.silent) {
        showError(new Error('无法获取当前位置天气，请输入城市后再试。'))
      }
      return null
    } finally {
      if (isCurrentSession(version)) state.weatherLoading = false
    }
  }

  async function loadStartupWeather() {
    if (readStoredWeatherCity()) return loadWeather()
    return loadLocalWeather({ silent: true })
  }

  async function refreshWeather() {
    if (state.weather?.city === '当前位置' || !state.recommendationForm.city) {
      return loadLocalWeather()
    }
    return loadWeather()
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
      total: state.historyTotal,
      loaded: state.history.length,
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
    const topTag = [...tagCounts.entries()].sort((a, b) => b[1] - a[1])[0]?.[0] || '未设置'
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
      { label: '颜色偏好', value: richness(profile?.colorPreferences, 20) },
      { label: '常用场合', value: richness(profile?.occasions, 20) },
      { label: '更新状态', value: profile ? (profile.stale ? 60 : 100) : 0 }
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

  // Weather is usually live (wttr.in / open-meteo). The configured-demo snapshot is static offline
  // presentation data and must never be shown as real-time weather.
  function weatherSourceLabel(source = '') {
    if (source === 'configured-demo') return '演示天气 · 非实时'
    if (source === 'wttr.in') return '实时天气 · wttr.in'
    if (source === 'open-meteo') return '实时天气 · Open-Meteo'
    return source || '来源未知'
  }

  function weatherConditionLabel(code) {
    const value = Number(code)
    if (value === 0) return '晴'
    if ([1, 2, 3].includes(value)) return '多云'
    if ([45, 48].includes(value)) return '雾'
    if ([51, 53, 55, 56, 57].includes(value)) return '毛毛雨'
    if ([61, 63, 65, 66, 67, 80, 81, 82].includes(value)) return '降雨'
    if ([71, 73, 75, 77, 85, 86].includes(value)) return '降雪'
    if (value >= 95) return '雷雨'
    return '天气'
  }

  function engineLabel(engine) {
    if (engine === 'llm') return '大模型'
    if (engine === 'development-rule-v1') return '规则引擎（备用）'
    return engine || '来源未知'
  }

  function fallbackReasonLabel(reason) {
    const labels = {
      'llm-disabled': '已关闭模型调用',
      'missing-api-key': '未配置模型密钥',
      'request-failed': '模型请求失败',
      'response-invalid': '模型返回内容无法解析',
      'result-invalid': '模型结果未通过校验',
      'duplicate-item-ids': '返回了重复衣物',
      'foreign-item-ids': '返回了衣橱之外的衣物',
      'same-category': '返回的衣物类别不完整'
    }
    return labels[reason] || reason || ''
  }

  async function ensureViewData(view) {
    if (view === 'trend' && !state.trends.length) await loadTrends()
    if (view === 'wardrobe' && !state.wardrobe.length) await loadWardrobe()
    if (view === 'history') await loadHistory()
    if (view === 'profile' && !state.profile) await loadProfile()
    if (view === 'admin' && isAdmin.value) {
      if (!state.adminOverview) await loadAdminOverview()
      if (!state.adminUsers.length && !state.adminUsersTotal) await loadAdminUsers()
      if (!state.adminFeedback.length && !state.adminFeedbackTotal) await loadAdminFeedback()
      if (!state.adminTrendContents.length && !state.adminTrendTotal) await loadAdminTrendContents()
      if (!state.adminAuditLogs.length && !state.adminAuditTotal) await loadAdminAuditLogs()
    }
  }

  async function selectView(view, options = {}) {
    const requestedView = VALID_VIEWS.has(view) ? view : 'home'
    const nextView = requestedView === 'admin' && state.authPhase === 'authenticated' && !isAdmin.value
      ? 'home'
      : requestedView
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
    const requestedView = VALID_VIEWS.has(view) ? view : 'home'
    const nextView = requestedView === 'admin' && state.authPhase === 'authenticated' && !isAdmin.value
      ? 'home'
      : requestedView === 'home' && state.authPhase === 'authenticated' && isAdmin.value
        ? 'admin'
        : requestedView
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
      if (state.activeView === 'admin' && !isAdmin.value) {
        state.activeView = 'home'
        window.history.replaceState(null, '', '#home')
      } else if (isAdmin.value && state.activeView === 'home') {
        state.activeView = 'admin'
        window.history.replaceState(null, '', '#admin')
      }
      await loadPrivateData()
      await loadStartupWeather()
    } catch (cause) {
      resetPrivateState()
      state.authUser = null
      state.authPhase = 'guest'
      state.authError = cause?.status === 401
        ? ''
        : (cause instanceof Error ? cause.message : '暂时无法确认登录状态。')
    }
    await trendsPromise
  }

  function dispose() {
    window.removeEventListener('hashchange', syncViewFromLocation)
  }

  return {
    state,
    isAdmin,
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
    weatherSourceLabel,
    weatherConditionLabel,
    engineLabel,
    fallbackReasonLabel,
    initialize,
    dispose,
    login,
    register,
    logout,
    clearError,
    clearWeather,
    loadTrends,
    useTrend,
    clearTrendReference,
    refreshTrendSources,
    loadWardrobe,
    loadHistory,
    loadProfile,
    loadAdminOverview,
    loadAdminUsers,
    updateAdminUserStatus,
    loadAdminFeedback,
    updateAdminFeedbackStatus,
    loadAdminTrendContents,
    runAdminTrendAiReview,
    retryAdminTrendAi,
    finalizeAdminTrendReview,
    updateAdminTrendVisibility,
    loadAdminAuditLogs,
    saveProfile,
    loadWeather,
    loadLocalWeather,
    refreshWeather,
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
