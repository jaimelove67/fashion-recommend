<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import {
  Activity,
  ArrowLeft,
  ArrowUpRight,
  Bot,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  CircleAlert,
  CircleOff,
  ClipboardCheck,
  Database,
  FileClock,
  LoaderCircle,
  MessageSquareText,
  RefreshCw,
  Search,
  ShieldCheck,
  UsersRound
} from '@lucide/vue'

const props = defineProps({
  app: { type: Object, required: true }
})

const sections = [
  { id: 'overview', label: '运营总览', description: '平台数据', icon: Activity },
  { id: 'users', label: '账号与权限', description: '状态治理', icon: UsersRound },
  { id: 'feedback', label: '反馈审核', description: '处理用户声音', icon: MessageSquareText },
  { id: 'moderation', label: '趋势审核', description: 'AI 初审 / 人工终审', icon: ClipboardCheck },
  { id: 'audit', label: '操作日志', description: '追踪管理动作', icon: FileClock }
]

const feedbackStatuses = [
  { value: 'PENDING', label: '待处理' },
  { value: 'REVIEWED', label: '已查看' },
  { value: 'RESOLVED', label: '已解决' },
  { value: 'ALL', label: '全部状态' }
]

const moderationStatuses = [
  { value: 'PENDING_HUMAN', label: '待人工终审' },
  { value: 'AI_FAILED', label: 'AI 初审失败' },
  { value: 'PENDING_AI', label: '等待 AI 初审' },
  { value: 'APPROVED', label: '已通过' },
  { value: 'REJECTED', label: '已驳回' },
  { value: 'ALL', label: '全部状态' }
]

const activeSection = ref('overview')
const overview = computed(() => props.app.state.adminOverview)
const currentUsername = computed(() => props.app.state.authUser?.username || '')
const users = computed(() => props.app.state.adminUsers)
const feedback = computed(() => props.app.state.adminFeedback)
const trendContents = computed(() => props.app.state.adminTrendContents)
const auditLogs = computed(() => props.app.state.adminAuditLogs)
const moderationNotes = ref({})
const overviewChartsRef = ref(null)
const overviewChartsVisible = ref(false)
const overviewChartsKey = ref(0)
let overviewChartsObserver = null
let overviewChartsScrollHandler = null
const anyAdminLoading = computed(() => props.app.state.adminOverviewLoading
  || props.app.state.adminUsersLoading
  || props.app.state.adminFeedbackLoading
  || props.app.state.adminTrendLoading
  || props.app.state.adminAuditLoading)

const monoInk = '#1C1C1A'
const monoMid = '#6A6963'
const monoMuted = '#8F8E88'
const monoFaint = '#C6C5BF'
const monoGrid = '#DEDDD6'

function countValue(value) {
  const number = Number(value)
  return Number.isFinite(number) ? Math.max(0, Math.round(number)) : 0
}

function polarPoint(cx, cy, radius, degrees) {
  const radians = degrees * Math.PI / 180
  return {
    x: Number((cx + radius * Math.cos(radians)).toFixed(2)),
    y: Number((cy + radius * Math.sin(radians)).toFixed(2))
  }
}

const accountComposition = computed(() => {
  const total = countValue(overview.value?.totalUsers)
  const enabled = Math.min(countValue(overview.value?.enabledUsers), total)
  const disabled = Math.max(total - enabled, 0)
  if (!total) {
    return [
      { label: '可用账号', value: 0, percent: 0, color: monoInk },
      { label: '停用账号', value: 0, percent: 0, color: monoMid }
    ]
  }
  const enabledPercent = Math.round(enabled / total * 100)
  return [
    { label: '可用账号', value: enabled, percent: enabledPercent, color: monoInk },
    { label: '停用账号', value: disabled, percent: 100 - enabledPercent, color: monoMid }
  ]
})

const accountEnabledPercent = computed(() => accountComposition.value[0]?.percent || 0)
const accountTicks = computed(() => {
  let cursor = 0
  return accountComposition.value.flatMap((segment, segmentIndex) => {
    const ticks = Array.from({ length: segment.percent }, (_, offset) => {
      const index = cursor + offset
      const angle = index * 3.6 - 90
      const inner = polarPoint(200, 108, 62, angle)
      const outer = polarPoint(200, 108, 76, angle)
      return {
        key: `${segment.label}-${offset}`,
        index,
        label: segment.label,
        value: segment.value,
        offset: offset + 1,
        x1: inner.x,
        y1: inner.y,
        x2: outer.x,
        y2: outer.y,
        color: segment.color,
        opacity: segmentIndex === 0 ? 1 : 0.74
      }
    })
    cursor += segment.percent
    return ticks
  })
})

const accountHeadline = computed(() => {
  const total = countValue(overview.value?.totalUsers)
  if (!total) return '还没有账号数据'
  if (accountEnabledPercent.value >= 90) return '可用账号保持稳定'
  if (accountEnabledPercent.value >= 60) return '大多数账号仍可使用'
  return '停用账号需要关注'
})

const runtimeBars = computed(() => {
  const total = countValue(overview.value?.totalRecommendations)
  const llm = countValue(overview.value?.llmRecommendations)
  const fallback = countValue(overview.value?.fallbackRecommendations)
  return [
    { label: '实际 LLM', value: llm, color: monoInk },
    { label: '规则降级', value: fallback, color: monoMid },
    { label: '未标记', value: Math.max(total - llm - fallback, 0), color: monoMuted }
  ]
})

const runtimeHeadline = computed(() => {
  const total = countValue(overview.value?.totalRecommendations)
  if (!total) return '还没有生成记录'
  const llm = countValue(overview.value?.llmRecommendations)
  const fallback = countValue(overview.value?.fallbackRecommendations)
  if (fallback > llm) return '规则降级仍是主要来源'
  if (llm > fallback) return '模型结果占据主要来源'
  return '模型与规则结果接近'
})

const runtimeBarTicks = computed(() => {
  const max = Math.max(1, ...runtimeBars.value.map((bar) => bar.value))
  const base = 192
  const step = 142 / max
  return runtimeBars.value.map((bar, barIndex) => {
    const x = 82 + barIndex * 118
    return {
      ...bar,
      x,
      topY: bar.value ? base - (bar.value - 1) * step : base,
      ticks: Array.from({ length: bar.value }, (_, index) => ({
        index,
        x1: x - 14 - ((index * 17 + barIndex * 7) % 3),
        x2: x + 14 + ((index * 11 + barIndex * 5) % 3),
        y: base - index * step,
        opacity: 0.64 + ((index * 19 + barIndex * 13) % 28) / 100
      }))
    }
  })
})

function disconnectOverviewChartsObserver() {
  if (overviewChartsObserver) {
    overviewChartsObserver.disconnect()
    overviewChartsObserver = null
  }
  if (overviewChartsScrollHandler) {
    window.removeEventListener('scroll', overviewChartsScrollHandler)
    overviewChartsScrollHandler = null
  }
}

function observeOverviewCharts() {
  disconnectOverviewChartsObserver()
  const target = overviewChartsRef.value
  if (!target) return
  overviewChartsVisible.value = false
  if (!('IntersectionObserver' in window)) {
    overviewChartsVisible.value = true
    return
  }
  overviewChartsObserver = new IntersectionObserver(([entry]) => {
    if (entry.isIntersecting) {
      overviewChartsVisible.value = true
      disconnectOverviewChartsObserver()
    }
  }, { threshold: 0.3 })
  overviewChartsObserver.observe(target)
  overviewChartsScrollHandler = () => {
    const bounds = target.getBoundingClientRect()
    if (bounds.bottom > 0 && bounds.top < window.innerHeight * 0.92) {
      overviewChartsVisible.value = true
      disconnectOverviewChartsObserver()
    }
  }
  window.addEventListener('scroll', overviewChartsScrollHandler, { passive: true })
  window.requestAnimationFrame(overviewChartsScrollHandler)
}

function replayOverviewCharts() {
  overviewChartsVisible.value = false
  overviewChartsKey.value += 1
  window.requestAnimationFrame(() => {
    overviewChartsVisible.value = true
  })
}

function scheduleOverviewChartReveal() {
  nextTick(() => {
    observeOverviewCharts()
    window.requestAnimationFrame(() => {
      if (overviewChartsRef.value) overviewChartsVisible.value = true
    })
  })
}

function selectSection(section) {
  activeSection.value = section
  if (section === 'overview') props.app.loadAdminOverview()
  if (section === 'users') props.app.loadAdminUsers({ page: 0 })
  if (section === 'feedback') props.app.loadAdminFeedback({ page: 0 })
  if (section === 'moderation') props.app.loadAdminTrendContents({ page: 0 })
  if (section === 'audit') props.app.loadAdminAuditLogs({ page: 0 })
}

function refreshWorkspace() {
  props.app.loadAdminOverview()
  props.app.loadAdminUsers({ page: props.app.state.adminUsersPage })
  props.app.loadAdminFeedback({ page: props.app.state.adminFeedbackPage })
  props.app.loadAdminTrendContents({ page: props.app.state.adminTrendPage })
  props.app.loadAdminAuditLogs({ page: props.app.state.adminAuditPage })
}

function searchUsers() {
  props.app.loadAdminUsers({ page: 0 })
}

function applyFeedbackFilters() {
  props.app.loadAdminFeedback({ page: 0 })
}

function applyModerationFilters() {
  props.app.loadAdminTrendContents({ page: 0 })
}

function applyAuditFilters() {
  props.app.loadAdminAuditLogs({ page: 0 })
}

function previousUsersPage() {
  if (props.app.state.adminUsersPage > 0) {
    props.app.loadAdminUsers({ page: props.app.state.adminUsersPage - 1 })
  }
}

function nextUsersPage() {
  if (props.app.state.adminUsersHasNext) {
    props.app.loadAdminUsers({ page: props.app.state.adminUsersPage + 1 })
  }
}

function previousFeedbackPage() {
  if (props.app.state.adminFeedbackPage > 0) {
    props.app.loadAdminFeedback({ page: props.app.state.adminFeedbackPage - 1 })
  }
}

function nextFeedbackPage() {
  if (props.app.state.adminFeedbackHasNext) {
    props.app.loadAdminFeedback({ page: props.app.state.adminFeedbackPage + 1 })
  }
}

function previousModerationPage() {
  if (props.app.state.adminTrendPage > 0) {
    props.app.loadAdminTrendContents({ page: props.app.state.adminTrendPage - 1 })
  }
}

function nextModerationPage() {
  if (props.app.state.adminTrendHasNext) {
    props.app.loadAdminTrendContents({ page: props.app.state.adminTrendPage + 1 })
  }
}

function previousAuditPage() {
  if (props.app.state.adminAuditPage > 0) {
    props.app.loadAdminAuditLogs({ page: props.app.state.adminAuditPage - 1 })
  }
}

function nextAuditPage() {
  if (props.app.state.adminAuditHasNext) {
    props.app.loadAdminAuditLogs({ page: props.app.state.adminAuditPage + 1 })
  }
}

function roleLabel(authority) {
  if (authority === 'ROLE_ADMIN') return '管理员'
  if (authority === 'ROLE_USER') return '普通用户'
  return authority.replace(/^ROLE_/, '')
}

function feedbackStatusLabel(status) {
  return feedbackStatuses.find((item) => item.value === status)?.label || status || '未知'
}

function moderationStatusLabel(status) {
  return moderationStatuses.find((item) => item.value === status)?.label || status || '未知'
}

function moderationDecisionLabel(decision) {
  const labels = { PASS: '建议通过', REVIEW: '建议复核', REJECT: '建议驳回', ERROR: 'AI 失败' }
  return labels[decision] || '未生成建议'
}

function moderationRiskLabel(level) {
  const labels = { LOW: '低风险', MEDIUM: '中风险', HIGH: '高风险', UNKNOWN: '未知风险' }
  return labels[level] || '未评估'
}

function moderationStatusClass(status) {
  return (status || 'unknown').toLowerCase().replace('_', '-')
}

function moderationActionBusy(id, action) {
  return props.app.state.adminTrendAction === `${action}:${id}`
}

async function reviewTrend(item, status) {
  const note = moderationNotes.value[item.id] || ''
  const updated = await props.app.finalizeAdminTrendReview(item.id, status, note)
  if (updated) delete moderationNotes.value[item.id]
}

async function retryTrend(item) {
  const updated = await props.app.retryAdminTrendAi(item.id)
  if (updated) delete moderationNotes.value[item.id]
}

function toggleTrendVisibility(item) {
  props.app.updateAdminTrendVisibility(item.id, !item.hidden)
}

function auditActionLabel(action) {
  if (action === 'USER_STATUS_UPDATE') return '账号状态调整'
  if (action === 'FEEDBACK_STATUS_UPDATE') return '反馈状态处理'
  return action || '未知操作'
}

function engineLabel(engine) {
  return props.app.engineLabel(engine)
}

function formatDate(value) {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '—'
  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

function averageRating(value) {
  return Number(value) > 0 ? Number(value).toFixed(1) : '暂无'
}

function feedbackActionBusy(recommendationId, status) {
  return props.app.state.adminFeedbackAction === `${recommendationId}:${status}`
}

watch(activeSection, (section) => {
  if (section === 'overview') {
    scheduleOverviewChartReveal()
  } else {
    disconnectOverviewChartsObserver()
  }
})

watch(overview, (value) => {
  if (value && activeSection.value === 'overview') {
    scheduleOverviewChartReveal()
  }
})

onMounted(() => {
  if (!props.app.isAdmin) return
  if (!overview.value) props.app.loadAdminOverview()
  if (!users.value.length && !props.app.state.adminUsersTotal) props.app.loadAdminUsers()
  if (!feedback.value.length && !props.app.state.adminFeedbackTotal) props.app.loadAdminFeedback()
  if (!trendContents.value.length && !props.app.state.adminTrendTotal) props.app.loadAdminTrendContents()
  if (!auditLogs.value.length && !props.app.state.adminAuditTotal) props.app.loadAdminAuditLogs()
  scheduleOverviewChartReveal()
})

onBeforeUnmount(disconnectOverviewChartsObserver)
</script>

<template>
  <section v-if="!app.isAdmin" class="admin-forbidden" role="alert">
    <CircleAlert :size="20" aria-hidden="true" />
    <div>
      <strong>没有管理权限</strong>
      <span>当前账号只能访问个人衣橱和推荐数据。</span>
    </div>
  </section>

  <section v-else class="admin-workspace" aria-labelledby="admin-title">
    <header class="admin-workspace-header">
      <div class="admin-title-block">
        <button class="admin-back-button" type="button" @click="app.selectView('home')">
          <ArrowLeft :size="15" aria-hidden="true" />返回用户端
        </button>
        <p class="admin-kicker"><ShieldCheck :size="14" aria-hidden="true" />WEAVESELF / ADMIN OPERATIONS</p>
        <h1 id="admin-title">管理工作台</h1>
        <p class="admin-title-copy">从平台运行、账号治理和用户反馈出发，维护一个可信的穿搭推荐服务。</p>
      </div>
      <div class="admin-identity-block">
        <span>当前管理员</span>
        <strong>{{ currentUsername }}</strong>
        <button class="admin-refresh" type="button" :disabled="anyAdminLoading" @click="refreshWorkspace">
          <LoaderCircle v-if="anyAdminLoading" class="spinning" :size="15" aria-hidden="true" />
          <RefreshCw v-else :size="15" aria-hidden="true" />
          刷新数据
        </button>
      </div>
    </header>

    <div class="admin-layout">
      <aside class="admin-sidebar" aria-label="管理工作台导航">
        <div class="admin-sidebar-label">运营中心</div>
        <nav class="admin-side-nav">
          <button
            v-for="section in sections"
            :key="section.id"
            type="button"
            :class="{ active: activeSection === section.id }"
            :aria-current="activeSection === section.id ? 'page' : undefined"
            @click="selectSection(section.id)"
          >
            <component :is="section.icon" :size="17" aria-hidden="true" />
            <span><strong>{{ section.label }}</strong><small>{{ section.description }}</small></span>
          </button>
        </nav>
        <div class="admin-privacy-note">
          <Database :size="16" aria-hidden="true" />
          <p>管理数据已脱敏。用户私有衣橱、图片和推荐正文不会出现在这里。</p>
        </div>
      </aside>

      <div class="admin-content">
        <section v-if="activeSection === 'overview'" class="admin-section" aria-labelledby="overview-title">
          <header class="admin-section-heading">
            <div><span>PLATFORM PULSE</span><h2 id="overview-title">运营总览</h2></div>
            <p>聚合当前数据库中的真实业务数据</p>
          </header>

          <div v-if="overview" class="overview-grid">
            <article class="overview-lead">
              <div class="overview-lead-top"><span>可继续提供服务的账号</span><CheckCircle2 :size="18" aria-hidden="true" /></div>
              <strong class="overview-lead-number">{{ overview.enabledUsers }}</strong>
              <p>当前共有 {{ overview.totalUsers }} 个账号，{{ overview.disabledUsers }} 个账号处于停用状态。</p>
              <div class="overview-lead-stats">
                <div><small>平均反馈评分</small><strong>{{ averageRating(overview.averageRating) }}<i v-if="overview.averageRating > 0"> / 5</i></strong></div>
                <div><small>已收藏推荐</small><strong>{{ overview.savedRecommendations }}</strong></div>
              </div>
            </article>

            <section class="admin-signal-panel" aria-labelledby="signal-title">
              <div class="admin-panel-title"><div><span>DATA SIGNALS</span><h3 id="signal-title">需要留意的信号</h3></div><Activity :size="17" aria-hidden="true" /></div>
              <div class="admin-signal-list">
                <button type="button" @click="selectSection('feedback')"><span><MessageSquareText :size="16" aria-hidden="true" />待处理反馈</span><strong>{{ overview.pendingFeedback }}</strong></button>
                <button type="button" @click="selectSection('users')"><span><CircleOff :size="16" aria-hidden="true" />停用账号</span><strong>{{ overview.disabledUsers }}</strong></button>
                <div><span><CircleAlert :size="16" aria-hidden="true" />待人工识别衣物</span><strong>{{ overview.manualReviewItems }}</strong></div>
                <div><span><Database :size="16" aria-hidden="true" />图片清理队列</span><strong>{{ overview.pendingImageCleanupTasks }}</strong></div>
              </div>
            </section>
          </div>

          <div v-else class="admin-loading" role="status" aria-live="polite"><LoaderCircle class="spinning" :size="18" aria-hidden="true" />正在读取平台数据…</div>

          <div v-if="overview" ref="overviewChartsRef" class="overview-lower-grid">
            <section class="admin-operation-panel mono-chart-card" aria-labelledby="recommendation-health-title">
              <div class="admin-panel-title"><div><span>RECOMMENDATION RUNTIME</span><h3 id="recommendation-health-title">{{ runtimeHeadline }}</h3></div><Bot :size="17" aria-hidden="true" /></div>
              <p class="mono-chart-sub">已落库生成记录 · 一档代表一条记录</p>
              <div class="mono-chart-stage" role="button" tabindex="0" aria-label="点击重播推荐运行图表" @click="replayOverviewCharts" @keydown.enter.prevent="replayOverviewCharts">
                <svg :key="overviewChartsKey" class="mono-chart-svg" :class="{ 'is-visible': overviewChartsVisible }" viewBox="0 0 400 230" role="img" aria-labelledby="runtime-chart-title runtime-chart-desc">
                  <title id="runtime-chart-title">推荐运行结果构成</title>
                  <desc id="runtime-chart-desc">每一档代表一条已落库的推荐生成记录，按实际 LLM、规则降级和未标记分类。</desc>
                  <line class="mono-chart-element" x1="28" y1="196" x2="372" y2="196" :stroke="monoGrid" stroke-width="0.8" style="--chart-delay: 0ms; --chart-opacity: .9" />
                  <g v-for="bar in runtimeBarTicks" :key="bar.label">
                    <line v-for="tick in bar.ticks" :key="`${bar.label}-${tick.index}`" class="mono-chart-element" :x1="tick.x1" :y1="tick.y" :x2="tick.x2" :y2="tick.y" :stroke="bar.color" stroke-width="1.2" :style="{ '--chart-delay': `${tick.index * 12}ms`, '--chart-opacity': tick.opacity }"><title>{{ bar.label }} · 第 {{ tick.index + 1 }} 条记录</title></line>
                    <text class="mono-chart-element mono-chart-value" :x="bar.x" :y="bar.topY - 10" :fill="bar.color" text-anchor="middle" :style="{ '--chart-delay': `${350 + bar.x}ms`, '--chart-opacity': 1 }">{{ bar.value }}</text>
                    <text class="mono-chart-element mono-chart-axis" :x="bar.x" y="214" :fill="monoMuted" text-anchor="middle" :style="{ '--chart-delay': `${650 + bar.x}ms`, '--chart-opacity': 1 }">{{ bar.label }}</text>
                  </g>
                  <text class="mono-chart-element mono-chart-footnote" x="200" y="229" :fill="monoFaint" text-anchor="middle" style="--chart-delay: 1050ms; --chart-opacity: 1">ONE TICK = ONE RECORD · DATABASE</text>
                </svg>
              </div>
              <p class="admin-caption">总量 {{ overview.totalRecommendations }} 条；模型调用与规则降级分开统计，未标记记录不会被误称为模型成功。</p>
              <p class="mono-chart-source">F1 RUNG BARS · RECOMMENDATION ENGINE · DATABASE</p>
            </section>

            <section class="admin-operation-panel boundary-panel mono-chart-card" aria-labelledby="account-composition-title">
              <div class="admin-panel-title"><div><span>ACCOUNT COMPOSITION</span><h3 id="account-composition-title">{{ accountHeadline }}</h3></div><UsersRound :size="17" aria-hidden="true" /></div>
              <p class="mono-chart-sub">当前账号状态 · 一格代表 1%</p>
              <div class="mono-chart-stage" role="button" tabindex="0" aria-label="点击重播账号状态图表" @click="replayOverviewCharts" @keydown.enter.prevent="replayOverviewCharts">
                <svg :key="overviewChartsKey" class="mono-chart-svg" :class="{ 'is-visible': overviewChartsVisible }" viewBox="0 0 400 230" role="img" aria-labelledby="account-chart-title account-chart-desc">
                  <title id="account-chart-title">账号状态构成</title>
                  <desc id="account-chart-desc">用一百个环形刻度展示可用账号和停用账号的百分比。</desc>
                  <g v-if="accountTicks.length">
                    <line v-for="tick in accountTicks" :key="tick.key" class="mono-chart-element" :x1="tick.x1" :y1="tick.y1" :x2="tick.x2" :y2="tick.y2" :stroke="tick.color" stroke-width="1.4" :style="{ '--chart-delay': `${tick.index * 12}ms`, '--chart-opacity': tick.opacity }"><title>{{ tick.label }} · 第 {{ tick.offset }} 格</title></line>
                  </g>
                  <circle v-else class="mono-chart-element" cx="200" cy="108" r="69" fill="none" :stroke="monoGrid" stroke-width="1" stroke-dasharray="2 5" style="--chart-delay: 100ms; --chart-opacity: 1" />
                  <text class="mono-chart-element mono-chart-center-value" x="200" y="106" :fill="monoInk" text-anchor="middle" style="--chart-delay: 900ms; --chart-opacity: 1">{{ overview.totalUsers ? `${accountEnabledPercent}%` : '—' }}</text>
                  <text class="mono-chart-element mono-chart-center-label" x="200" y="124" :fill="monoMuted" text-anchor="middle" style="--chart-delay: 980ms; --chart-opacity: 1">可用账号</text>
                  <text class="mono-chart-element mono-chart-footnote" x="200" y="214" :fill="monoFaint" text-anchor="middle" style="--chart-delay: 1080ms; --chart-opacity: 1">{{ overview.totalUsers ? `TOTAL · ${overview.totalUsers} ACCOUNTS` : 'NO ACCOUNT DATA' }}</text>
                </svg>
              </div>
              <div class="mono-chart-legend" aria-label="账号状态图例"><span v-for="segment in accountComposition" :key="segment.label"><i :style="{ backgroundColor: segment.color }"></i>{{ segment.label }} {{ segment.value }}（{{ segment.percent }}%）</span></div>
              <div class="boundary-note"><ShieldCheck :size="15" aria-hidden="true" /><span>衣物识别待人工确认：{{ overview.manualReviewItems }} 件</span></div>
              <p class="admin-caption">后台继续只管理用户私有衣橱和推荐记录；公共趋势内容另见趋势审核。</p>
              <p class="mono-chart-source">F4 TICK DONUT · ACCOUNT STATUS · DATABASE</p>
            </section>

            <section class="admin-operation-panel recent-panel" aria-labelledby="recent-actions-title">
              <div class="admin-panel-title"><div><span>RECENT ACTIONS</span><h3 id="recent-actions-title">最近管理动作</h3></div><button type="button" class="text-button" @click="selectSection('audit')">查看全部</button></div>
              <div v-if="auditLogs.length" class="recent-action-list">
                <div v-for="log in auditLogs.slice(0, 4)" :key="log.id"><span class="recent-action-mark"></span><div><strong>{{ auditActionLabel(log.action) }}</strong><small>{{ log.targetId || '—' }} · {{ formatDate(log.createdAt) }}</small></div><em>{{ log.outcome === 'SUCCESS' ? '成功' : log.outcome }}</em></div>
              </div>
              <p v-else class="admin-empty-inline">还没有管理操作记录。</p>
            </section>
          </div>
        </section>

        <section v-else-if="activeSection === 'users'" class="admin-section" aria-labelledby="users-title">
          <header class="admin-section-heading">
            <div><span>ACCOUNT GOVERNANCE</span><h2 id="users-title">账号与权限</h2></div>
            <p>停用只影响后续登录，不触碰个人业务数据</p>
          </header>
          <section class="admin-table-panel">
            <form class="admin-toolbar" role="search" @submit.prevent="searchUsers">
              <label class="admin-search-field"><Search :size="16" aria-hidden="true" /><span class="sr-only">按用户名搜索</span><input v-model="app.state.adminQuery" type="search" maxlength="32" placeholder="按用户名搜索" /></label>
              <button class="toolbar-button toolbar-primary" type="submit" :disabled="app.state.adminUsersLoading">搜索账号</button>
              <span class="toolbar-total">共 {{ app.state.adminUsersTotal }} 个账号</span>
            </form>
            <div v-if="app.state.adminUsersLoading && !users.length" class="admin-loading" role="status" aria-live="polite"><LoaderCircle class="spinning" :size="18" />正在加载账号…</div>
            <div v-else-if="!users.length" class="admin-empty"><UsersRound :size="21" aria-hidden="true" />没有符合条件的账号</div>
            <div v-else class="admin-table-wrap">
              <table class="admin-table">
                <caption class="sr-only">账号权限列表</caption>
                <thead><tr><th scope="col">账号</th><th scope="col">角色</th><th scope="col">状态</th><th scope="col">操作</th></tr></thead>
                <tbody>
                  <tr v-for="user in users" :key="user.username">
                    <th scope="row"><span class="admin-user-name">{{ user.username }}</span><small v-if="user.username === currentUsername">当前登录账号</small></th>
                    <td><div class="admin-role-list"><span v-for="authority in user.authorities" :key="authority" class="admin-role">{{ roleLabel(authority) }}</span></div></td>
                    <td><span class="admin-status" :class="{ enabled: user.enabled }"><i></i>{{ user.enabled ? '启用' : '停用' }}</span></td>
                    <td><button class="status-action" type="button" :disabled="user.username === currentUsername || app.state.adminUserAction === user.username" :aria-label="`${user.enabled ? '停用' : '启用'}账号 ${user.username}`" @click="app.updateAdminUserStatus(user.username, !user.enabled)"><LoaderCircle v-if="app.state.adminUserAction === user.username" class="spinning" :size="13" aria-hidden="true" />{{ user.username === currentUsername ? '当前账号' : (user.enabled ? '停用账号' : '重新启用') }}</button></td>
                  </tr>
                </tbody>
              </table>
            </div>
            <footer class="admin-pagination"><span>第 {{ app.state.adminUsersPage + 1 }} 页</span><div><button type="button" :disabled="app.state.adminUsersPage === 0 || app.state.adminUsersLoading" @click="previousUsersPage"><ChevronLeft :size="14" />上一页</button><button type="button" :disabled="!app.state.adminUsersHasNext || app.state.adminUsersLoading" @click="nextUsersPage">下一页<ChevronRight :size="14" /></button></div></footer>
          </section>
        </section>

        <section v-else-if="activeSection === 'feedback'" class="admin-section" aria-labelledby="feedback-title">
          <header class="admin-section-heading">
            <div><span>USER VOICE</span><h2 id="feedback-title">反馈审核</h2></div>
            <p>保留原始评分，只维护处理状态和处理人</p>
          </header>
          <section class="admin-table-panel">
            <form class="admin-toolbar feedback-toolbar" role="search" @submit.prevent="applyFeedbackFilters">
              <label class="admin-select-field"><span>状态</span><select v-model="app.state.adminFeedbackStatus" @change="applyFeedbackFilters"><option v-for="item in feedbackStatuses" :key="item.value" :value="item.value">{{ item.label }}</option></select></label>
              <label class="admin-search-field feedback-search"><Search :size="16" aria-hidden="true" /><span class="sr-only">搜索反馈</span><input v-model="app.state.adminFeedbackQuery" type="search" maxlength="64" placeholder="搜索账号或反馈内容" /></label>
              <button class="toolbar-button toolbar-primary" type="submit" :disabled="app.state.adminFeedbackLoading">筛选</button>
              <span class="toolbar-total">共 {{ app.state.adminFeedbackTotal }} 条</span>
            </form>
            <p class="table-privacy-note"><ShieldCheck :size="14" aria-hidden="true" />仅显示反馈元数据，不展示推荐正文、衣物图片或私有衣橱。</p>
            <div v-if="app.state.adminFeedbackLoading && !feedback.length" class="admin-loading" role="status" aria-live="polite"><LoaderCircle class="spinning" :size="18" />正在加载反馈…</div>
            <div v-else-if="!feedback.length" class="admin-empty"><MessageSquareText :size="21" aria-hidden="true" />当前没有符合条件的反馈</div>
            <div v-else class="admin-table-wrap">
              <table class="admin-table feedback-table">
                <caption class="sr-only">用户反馈审核列表</caption>
                <thead><tr><th scope="col">反馈</th><th scope="col">推荐上下文</th><th scope="col">状态</th><th scope="col">更新时间</th><th scope="col">处理</th></tr></thead>
                <tbody>
                  <tr v-for="item in feedback" :key="item.recommendationId">
                    <th scope="row"><div class="feedback-user"><strong>{{ item.username }}</strong><span><b v-for="star in 5" :key="star" :class="{ filled: star <= item.rating }">★</b></span></div><p>{{ item.comment || '未留下文字说明' }}</p><small>{{ item.feedbackType || '未分类反馈' }}</small></th>
                    <td><div class="feedback-context"><strong>{{ item.occasion }} · {{ item.city }}</strong><span>{{ engineLabel(item.engine) }}</span><small v-if="item.fallbackReason">{{ app.fallbackReasonLabel(item.fallbackReason) }}</small></div></td>
                    <td><span class="review-status" :class="item.moderationStatus.toLowerCase()"><i></i>{{ feedbackStatusLabel(item.moderationStatus) }}</span></td>
                    <td class="muted-cell">{{ formatDate(item.updatedAt) }}</td>
                    <td><div class="feedback-actions"><button v-if="item.moderationStatus === 'PENDING'" type="button" :disabled="app.state.adminFeedbackAction" @click="app.updateAdminFeedbackStatus(item.recommendationId, 'REVIEWED')"><LoaderCircle v-if="feedbackActionBusy(item.recommendationId, 'REVIEWED')" class="spinning" :size="13" />标为已查看</button><button v-if="item.moderationStatus !== 'RESOLVED'" type="button" class="resolve-action" :disabled="app.state.adminFeedbackAction" @click="app.updateAdminFeedbackStatus(item.recommendationId, 'RESOLVED')"><LoaderCircle v-if="feedbackActionBusy(item.recommendationId, 'RESOLVED')" class="spinning" :size="13" />标为已解决</button><button v-if="item.moderationStatus !== 'PENDING'" type="button" :disabled="app.state.adminFeedbackAction" @click="app.updateAdminFeedbackStatus(item.recommendationId, 'PENDING')"><LoaderCircle v-if="feedbackActionBusy(item.recommendationId, 'PENDING')" class="spinning" :size="13" />退回待处理</button></div></td>
                  </tr>
                </tbody>
              </table>
            </div>
            <footer class="admin-pagination"><span>第 {{ app.state.adminFeedbackPage + 1 }} 页</span><div><button type="button" :disabled="app.state.adminFeedbackPage === 0 || app.state.adminFeedbackLoading" @click="previousFeedbackPage"><ChevronLeft :size="14" />上一页</button><button type="button" :disabled="!app.state.adminFeedbackHasNext || app.state.adminFeedbackLoading" @click="nextFeedbackPage">下一页<ChevronRight :size="14" /></button></div></footer>
          </section>
        </section>

        <section v-else-if="activeSection === 'moderation'" class="admin-section" aria-labelledby="moderation-title">
          <header class="admin-section-heading">
            <div><span>CONTENT GOVERNANCE</span><h2 id="moderation-title">趋势内容审核</h2></div>
            <p>AI 只提供初审建议，管理员负责最终发布决定</p>
          </header>
          <section class="admin-table-panel">
            <form class="admin-toolbar moderation-toolbar" role="search" @submit.prevent="applyModerationFilters">
              <label class="admin-select-field"><span>状态</span><select v-model="app.state.adminTrendStatus" @change="applyModerationFilters"><option v-for="item in moderationStatuses" :key="item.value" :value="item.value">{{ item.label }}</option></select></label>
              <label class="admin-search-field moderation-search"><Search :size="16" aria-hidden="true" /><span class="sr-only">搜索趋势内容</span><input v-model="app.state.adminTrendQuery" type="search" maxlength="64" placeholder="搜索标题、平台或内容 ID" /></label>
              <button class="toolbar-button" type="submit" :disabled="app.state.adminTrendLoading">筛选</button>
              <button class="toolbar-button toolbar-primary" type="button" :disabled="app.state.adminTrendAction || !app.state.adminTrendContents.length && app.state.adminTrendStatus === 'PENDING_AI'" @click="app.runAdminTrendAiReview()">
                <LoaderCircle v-if="app.state.adminTrendAction === 'ai-review'" class="spinning" :size="13" aria-hidden="true" />
                <Bot v-else :size="13" aria-hidden="true" />
                运行 AI 初审
              </button>
              <span class="toolbar-total">共 {{ app.state.adminTrendTotal }} 条</span>
            </form>
            <div v-if="app.state.adminTrendRun" class="moderation-run" role="status" aria-live="polite">
              <Bot :size="15" aria-hidden="true" />
              <span v-if="app.state.adminTrendRun.aiEnabled">本次请求 {{ app.state.adminTrendRun.requested }} 条，完成 {{ app.state.adminTrendRun.reviewed }} 条，失败 {{ app.state.adminTrendRun.failed }} 条。完成后请在“待人工终审”中作出最终决定。</span>
              <span v-else>AI 初审当前未启用，内容仍保持隔离，不会自动发布。</span>
            </div>
            <p class="table-privacy-note"><ShieldCheck :size="14" aria-hidden="true" />仅向模型发送标题、摘要、标签、来源等趋势元数据，不发送用户私有衣橱。</p>
            <div v-if="app.state.adminTrendLoading && !trendContents.length" class="admin-loading" role="status" aria-live="polite"><LoaderCircle class="spinning" :size="18" />正在读取趋势审核队列…</div>
            <div v-else-if="!trendContents.length" class="admin-empty"><ClipboardCheck :size="21" aria-hidden="true" /><span v-if="app.state.adminTrendStatus === 'PENDING_HUMAN'">当前没有待人工终审内容；可切换状态或先运行 AI 初审。</span><span v-else>当前没有符合条件的趋势内容</span></div>
            <div v-else class="admin-table-wrap">
              <table class="admin-table moderation-table">
                <caption class="sr-only">趋势内容 AI 初审与人工终审列表</caption>
                <thead><tr><th scope="col">趋势内容</th><th scope="col">AI 初审建议</th><th scope="col">审核状态</th><th scope="col">人工终审</th></tr></thead>
                <tbody>
                  <tr v-for="item in trendContents" :key="item.id">
                    <th scope="row">
                      <div class="moderation-content">
                        <strong>{{ item.title || '未命名内容' }}</strong>
                        <span>{{ item.platform }} · 来源热度 {{ item.heatScore }}</span>
                        <a v-if="item.sourceUrl" :href="item.sourceUrl" target="_blank" rel="noreferrer">查看原始来源 <ArrowUpRight :size="12" aria-hidden="true" /></a>
                        <p v-if="item.summary">{{ item.summary }}</p>
                        <small>{{ item.id }} · 抓取于 {{ formatDate(item.fetchedAt) }}</small>
                      </div>
                    </th>
                    <td>
                      <div v-if="item.aiDecision" class="moderation-ai-summary">
                        <div><span class="moderation-decision">{{ moderationDecisionLabel(item.aiDecision) }}</span><span class="moderation-risk">{{ moderationRiskLabel(item.aiRiskLevel) }}</span></div>
                        <p>{{ item.aiReason || '模型未提供说明' }}</p>
                        <small>{{ item.aiModel || '未记录模型' }} · {{ formatDate(item.aiReviewedAt) }}</small>
                      </div>
                      <span v-else class="moderation-pending-note">等待 AI 初审</span>
                    </td>
                    <td>
                      <div class="moderation-status-cell">
                        <span class="review-status" :class="moderationStatusClass(item.moderationStatus)"><i></i>{{ moderationStatusLabel(item.moderationStatus) }}</span>
                        <small v-if="item.reviewedBy">人工：{{ item.reviewedBy }} · {{ formatDate(item.reviewedAt) }}</small>
                        <small v-if="item.reviewNote">说明：{{ item.reviewNote }}</small>
                      </div>
                    </td>
                    <td>
                      <div v-if="item.moderationStatus === 'PENDING_HUMAN' || item.moderationStatus === 'AI_FAILED'" class="moderation-review-form">
                        <label class="moderation-note-field"><span class="sr-only">{{ item.id }} 的人工终审说明</span><textarea v-model="moderationNotes[item.id]" maxlength="500" :placeholder="item.moderationStatus === 'AI_FAILED' ? 'AI 失败时必须填写人工依据' : '可选：填写终审说明'"></textarea></label>
                        <div class="moderation-actions">
                          <button class="moderation-approve" type="button" :disabled="app.state.adminTrendAction" @click="reviewTrend(item, 'APPROVED')"><LoaderCircle v-if="moderationActionBusy(item.id, 'review')" class="spinning" :size="13" aria-hidden="true" />通过并发布</button>
                          <button class="moderation-reject" type="button" :disabled="app.state.adminTrendAction" @click="reviewTrend(item, 'REJECTED')">驳回</button>
                          <button v-if="item.moderationStatus === 'AI_FAILED'" type="button" :disabled="app.state.adminTrendAction" @click="retryTrend(item)"><LoaderCircle v-if="moderationActionBusy(item.id, 'retry')" class="spinning" :size="13" aria-hidden="true" />重试 AI</button>
                        </div>
                        <small v-if="item.moderationStatus === 'AI_FAILED'" class="moderation-override-note">AI 失败不会自动发布；填写说明后可人工接管。</small>
                      </div>
                      <div v-else-if="item.moderationStatus === 'APPROVED'" class="moderation-published-cell">
                        <strong>{{ item.hidden ? '已通过，当前下架' : '已通过，正在展示' }}</strong>
                        <button class="status-action" type="button" :disabled="app.state.adminTrendAction" @click="toggleTrendVisibility(item)"><LoaderCircle v-if="moderationActionBusy(item.id, 'visibility')" class="spinning" :size="13" aria-hidden="true" />{{ item.hidden ? '恢复展示' : '下架' }}</button>
                      </div>
                      <div v-else-if="item.moderationStatus === 'REJECTED'" class="moderation-published-cell"><span>已驳回，不会展示给普通用户</span></div>
                      <div v-else class="moderation-published-cell"><span>等待 AI 处理后进入人工队列</span></div>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            <footer class="admin-pagination"><span>第 {{ app.state.adminTrendPage + 1 }} 页</span><div><button type="button" :disabled="app.state.adminTrendPage === 0 || app.state.adminTrendLoading" @click="previousModerationPage"><ChevronLeft :size="14" />上一页</button><button type="button" :disabled="!app.state.adminTrendHasNext || app.state.adminTrendLoading" @click="nextModerationPage">下一页<ChevronRight :size="14" /></button></div></footer>
          </section>
        </section>

        <section v-else class="admin-section" aria-labelledby="audit-title">
          <header class="admin-section-heading">
            <div><span>TRACEABILITY</span><h2 id="audit-title">操作日志</h2></div>
            <p>记录账号治理和反馈处理，不保存敏感凭据</p>
          </header>
          <section class="admin-table-panel">
            <form class="admin-toolbar" @submit.prevent="applyAuditFilters">
              <label class="admin-select-field"><span>操作</span><select v-model="app.state.adminAuditAction"><option value="">全部操作</option><option value="USER_STATUS_UPDATE">账号状态调整</option><option value="FEEDBACK_STATUS_UPDATE">反馈状态处理</option></select></label>
              <label class="admin-select-field"><span>结果</span><select v-model="app.state.adminAuditOutcome"><option value="">全部结果</option><option value="SUCCESS">成功</option></select></label>
              <button class="toolbar-button toolbar-primary" type="submit" :disabled="app.state.adminAuditLoading">筛选日志</button>
              <span class="toolbar-total">共 {{ app.state.adminAuditTotal }} 条</span>
            </form>
            <div v-if="app.state.adminAuditLoading && !auditLogs.length" class="admin-loading" role="status" aria-live="polite"><LoaderCircle class="spinning" :size="18" />正在读取操作日志…</div>
            <div v-else-if="!auditLogs.length" class="admin-empty"><FileClock :size="21" aria-hidden="true" />还没有符合条件的操作日志</div>
            <div v-else class="admin-table-wrap">
              <table class="admin-table audit-table">
                <caption class="sr-only">管理员操作审计日志</caption>
                <thead><tr><th scope="col">时间</th><th scope="col">操作者</th><th scope="col">动作</th><th scope="col">对象</th><th scope="col">结果</th><th scope="col">说明</th></tr></thead>
                <tbody><tr v-for="log in auditLogs" :key="log.id"><td class="muted-cell">{{ formatDate(log.createdAt) }}</td><td><strong>{{ log.actorUsername }}</strong></td><td><span class="audit-action">{{ auditActionLabel(log.action) }}</span></td><td><span class="audit-target">{{ log.targetType }} · {{ log.targetId || '—' }}</span></td><td><span class="audit-outcome">{{ log.outcome === 'SUCCESS' ? '成功' : log.outcome }}</span></td><td class="audit-details">{{ log.details || '—' }}</td></tr></tbody>
              </table>
            </div>
            <footer class="admin-pagination"><span>第 {{ app.state.adminAuditPage + 1 }} 页</span><div><button type="button" :disabled="app.state.adminAuditPage === 0 || app.state.adminAuditLoading" @click="previousAuditPage"><ChevronLeft :size="14" />上一页</button><button type="button" :disabled="!app.state.adminAuditHasNext || app.state.adminAuditLoading" @click="nextAuditPage">下一页<ChevronRight :size="14" /></button></div></footer>
          </section>
        </section>
      </div>
    </div>
  </section>
</template>

<style scoped>
.admin-workspace { width: min(calc(100% - 64px), 1240px); margin: 0 auto; padding: 34px 0 84px; }
.admin-workspace-header { display: flex; align-items: flex-end; justify-content: space-between; gap: 30px; border-bottom: 1px solid var(--line); padding-bottom: 28px; }
.admin-title-block { min-width: 0; }
.admin-back-button { display: inline-flex; align-items: center; gap: 6px; margin-bottom: 25px; border: 0; padding: 0; color: var(--muted); background: transparent; font-size: 11px; }
.admin-back-button:hover { color: var(--accent-strong); }
.admin-kicker { display: flex; align-items: center; gap: 7px; margin: 0 0 10px; color: var(--accent-strong); font-size: 9px; font-weight: 800; letter-spacing: .15em; }
.admin-title-block h1 { margin: 0; color: var(--ink); font-family: var(--font-display); font-size: clamp(34px, 4.5vw, 58px); font-weight: 400; letter-spacing: -.06em; line-height: 1; }
.admin-title-copy { max-width: 560px; margin: 14px 0 0; color: var(--muted); font-size: 12px; line-height: 1.7; }
.admin-identity-block { display: grid; min-width: 170px; gap: 4px; justify-items: end; padding-bottom: 2px; }
.admin-identity-block > span { color: var(--muted); font-size: 10px; }
.admin-identity-block > strong { color: var(--ink); font-size: 13px; }
.admin-refresh { display: inline-flex; align-items: center; gap: 8px; min-height: 36px; margin-top: 12px; border: 1px solid var(--line-strong); border-radius: 999px; padding: 0 14px; color: var(--ink); background: transparent; font-size: 10px; font-weight: 800; }
.admin-refresh:hover:not(:disabled) { border-color: var(--accent); color: var(--accent-strong); background: var(--accent-soft); }
.admin-layout { display: grid; grid-template-columns: 220px minmax(0, 1fr); align-items: start; gap: 34px; padding-top: 28px; }
.admin-sidebar { position: sticky; top: 104px; min-width: 0; }
.admin-sidebar-label { margin: 0 0 11px 12px; color: var(--muted); font-size: 9px; font-weight: 800; letter-spacing: .14em; }
.admin-side-nav { display: grid; gap: 5px; }
.admin-side-nav button { display: grid; grid-template-columns: 20px minmax(0, 1fr); align-items: center; gap: 11px; width: 100%; border: 1px solid transparent; border-radius: 11px; padding: 12px; color: var(--muted); background: transparent; text-align: left; }
.admin-side-nav button:hover { color: var(--accent-strong); background: rgba(255,255,255,.52); }
.admin-side-nav button.active { border-color: var(--line); color: var(--accent-strong); background: var(--surface); box-shadow: 0 9px 25px rgba(37,59,52,.06); }
.admin-side-nav button > span { display: grid; gap: 2px; min-width: 0; }
.admin-side-nav strong { font-size: 11px; font-weight: 800; }
.admin-side-nav small { color: var(--muted); font-size: 9px; }
.admin-privacy-note { display: flex; align-items: flex-start; gap: 8px; margin: 28px 12px 0; border-top: 1px solid var(--line); padding-top: 17px; color: var(--muted); }
.admin-privacy-note svg { flex: 0 0 auto; color: var(--accent-strong); }
.admin-privacy-note p { margin: 0; font-size: 10px; line-height: 1.65; }
.admin-content { min-width: 0; }
.admin-section-heading { display: flex; align-items: flex-end; justify-content: space-between; gap: 20px; margin-bottom: 19px; }
.admin-section-heading > div > span, .admin-panel-title > div > span { color: var(--accent-strong); font-size: 9px; font-weight: 800; letter-spacing: .14em; }
.admin-section-heading h2 { margin: 6px 0 0; color: var(--ink); font-family: var(--font-display); font-size: 30px; font-weight: 400; letter-spacing: -.045em; }
.admin-section-heading > p { max-width: 260px; margin: 0; color: var(--muted); font-size: 10px; line-height: 1.6; text-align: right; }
.overview-grid { display: grid; grid-template-columns: minmax(0, 1.08fr) minmax(290px, .92fr); gap: 13px; }
.overview-lead { min-height: 285px; border-radius: 16px; padding: 25px; color: var(--surface); background: var(--accent-strong); box-shadow: 0 22px 42px rgba(11,48,40,.15); }
.overview-lead-top { display: flex; align-items: center; justify-content: space-between; color: rgba(252,250,245,.72); font-size: 10px; }
.overview-lead-top svg { color: #b9d5a9; }
.overview-lead-number { display: block; margin-top: 31px; font-family: var(--font-display); font-size: clamp(64px, 8vw, 92px); font-weight: 400; letter-spacing: -.08em; line-height: .8; }
.overview-lead p { max-width: 330px; margin: 18px 0 28px; color: rgba(252,250,245,.72); font-size: 11px; line-height: 1.7; }
.overview-lead-stats { display: grid; grid-template-columns: 1fr 1fr; gap: 18px; border-top: 1px solid rgba(252,250,245,.22); padding-top: 14px; }
.overview-lead-stats div { display: grid; gap: 4px; }
.overview-lead-stats small { color: rgba(252,250,245,.62); font-size: 9px; }
.overview-lead-stats strong { font-family: var(--font-display); font-size: 24px; font-weight: 400; }
.overview-lead-stats i { color: rgba(252,250,245,.64); font-family: var(--font-sans); font-size: 9px; font-style: normal; }
.admin-signal-panel, .admin-operation-panel, .admin-table-panel { border: 1px solid var(--line); border-radius: 15px; background: rgba(255,255,255,.42); }
.admin-signal-panel { padding: 21px 20px 13px; }
.admin-panel-title { display: flex; align-items: flex-start; justify-content: space-between; gap: 14px; }
.admin-panel-title > svg { color: var(--accent-strong); }
.admin-panel-title h3 { margin: 6px 0 0; color: var(--ink); font-family: var(--font-display); font-size: 22px; font-weight: 400; letter-spacing: -.04em; }
.admin-signal-list { display: grid; margin-top: 19px; }
.admin-signal-list > * { display: flex; align-items: center; justify-content: space-between; gap: 10px; border-top: 1px solid var(--line); padding: 13px 0; color: var(--muted); font-size: 11px; }
.admin-signal-list button { width: 100%; background: transparent; text-align: left; }
.admin-signal-list button:hover { color: var(--accent-strong); }
.admin-signal-list span { display: inline-flex; align-items: center; gap: 8px; }
.admin-signal-list span svg { color: var(--accent-strong); }
.admin-signal-list strong { color: var(--ink); font-family: var(--font-display); font-size: 21px; font-weight: 400; }
.overview-lower-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 13px; margin-top: 13px; }
.mono-chart-card { min-height: 342px; border-radius: 24px; }
.mono-chart-sub { margin: 18px 0 0; color: var(--muted); font-size: 9px; line-height: 1.5; }
.mono-chart-stage { margin-top: 5px; border-top: 1px solid var(--line); padding-top: 5px; cursor: pointer; outline: 0; }
.mono-chart-stage:focus-visible { border-radius: 8px; box-shadow: 0 0 0 3px rgba(55,126,111,.12); }
.mono-chart-svg { display: block; width: 100%; height: 211px; overflow: visible; }
.mono-chart-element { opacity: 0; }
.mono-chart-svg.is-visible .mono-chart-element { animation: mono-chart-fade .9s ease var(--chart-delay, 0ms) both; }
@keyframes mono-chart-fade { from { opacity: 0; } to { opacity: var(--chart-opacity, 1); } }
.mono-chart-value, .mono-chart-center-value { font-family: var(--font-display); font-size: 12px; font-weight: 800; }
.mono-chart-center-value { font-size: 27px; }
.mono-chart-center-label, .mono-chart-axis { font-family: var(--font-sans); font-size: 9px; font-weight: 700; }
.mono-chart-footnote { font-family: var(--font-sans); font-size: 7px; font-weight: 600; letter-spacing: .12em; }
.mono-chart-legend { display: flex; flex-wrap: wrap; gap: 7px 14px; margin-top: -2px; color: var(--muted); font-size: 9px; }
.mono-chart-legend span { display: inline-flex; align-items: center; gap: 5px; }
.mono-chart-legend i { display: block; width: 7px; height: 7px; border-radius: 50%; }
.mono-chart-source { margin: 10px 0 0; color: #C6C5BF; font-size: 8px; font-weight: 600; letter-spacing: .08em; }
.mono-chart-card .boundary-note { margin-top: 11px; }
@media (prefers-reduced-motion: reduce) {
  .mono-chart-svg .mono-chart-element { opacity: var(--chart-opacity, 1); animation: none; }
}
.admin-operation-panel { min-height: 205px; padding: 21px 20px; }
.runtime-total { display: flex; align-items: baseline; gap: 8px; margin: 23px 0 17px; }
.runtime-total strong { color: var(--ink); font-family: var(--font-display); font-size: 40px; font-weight: 400; letter-spacing: -.06em; }
.runtime-total span { color: var(--muted); font-size: 10px; }
.runtime-breakdown { display: grid; gap: 10px; }
.runtime-breakdown div { display: grid; grid-template-columns: 8px minmax(0, 1fr) auto; align-items: center; gap: 8px; color: var(--muted); font-size: 10px; }
.runtime-breakdown strong { color: var(--ink); font-size: 11px; }
.runtime-dot { width: 7px; height: 7px; border-radius: 50%; background: var(--line-strong); }
.runtime-dot-llm { background: var(--accent-strong); }
.runtime-dot-fallback { background: var(--coral); }
.admin-caption { margin: 17px 0 0; color: var(--muted); font-size: 9px; line-height: 1.55; }
.boundary-panel { background: var(--surface-soft); }
.boundary-copy { margin: 23px 0 18px; color: var(--muted); font-size: 11px; line-height: 1.75; }
.boundary-note { display: inline-flex; align-items: center; gap: 7px; border-radius: 999px; padding: 7px 10px; color: var(--accent-strong); background: rgba(255,255,255,.58); font-size: 9px; font-weight: 700; }
.recent-panel { grid-column: 1 / -1; min-height: 0; }
.text-button { border: 0; padding: 0; color: var(--accent-strong); background: transparent; font-size: 10px; font-weight: 800; }
.text-button:hover { color: var(--coral); }
.recent-action-list { display: grid; grid-template-columns: repeat(4, 1fr); gap: 0; margin-top: 19px; }
.recent-action-list > div { display: flex; align-items: flex-start; gap: 9px; min-width: 0; border-right: 1px solid var(--line); padding: 3px 17px; }
.recent-action-list > div:first-child { padding-left: 0; }
.recent-action-list > div:last-child { border-right: 0; }
.recent-action-mark { width: 7px; height: 7px; flex: 0 0 auto; margin-top: 5px; border-radius: 50%; background: var(--accent); }
.recent-action-list div > div { display: grid; gap: 3px; min-width: 0; }
.recent-action-list strong { overflow: hidden; color: var(--ink); font-size: 10px; text-overflow: ellipsis; white-space: nowrap; }
.recent-action-list small { overflow: hidden; color: var(--muted); font-size: 9px; text-overflow: ellipsis; white-space: nowrap; }
.recent-action-list em { margin-left: auto; color: var(--accent-strong); font-size: 9px; font-style: normal; }
.admin-table-panel { overflow: hidden; padding: 0 20px 12px; }
.admin-toolbar { display: flex; align-items: center; gap: 9px; border-bottom: 1px solid var(--line); padding: 14px 0 12px; }
.admin-search-field { display: flex; min-width: 170px; flex: 1; align-items: center; gap: 8px; border-bottom: 1px solid var(--line-strong); padding: 0 2px 8px; color: var(--muted); }
.admin-search-field:focus-within { border-color: var(--accent); color: var(--accent-strong); }
.admin-search-field input { width: 100%; min-width: 0; border: 0; outline: 0; color: var(--ink); background: transparent; font-size: 11px; }
.toolbar-button { min-height: 31px; border: 1px solid var(--line-strong); border-radius: 999px; padding: 0 12px; font-size: 10px; font-weight: 800; white-space: nowrap; }
.toolbar-primary { border-color: var(--accent-strong); color: var(--surface); background: var(--accent-strong); }
.toolbar-primary:hover:not(:disabled) { background: var(--accent); }
.toolbar-total { margin-left: auto; color: var(--muted); font-size: 10px; white-space: nowrap; }
.admin-select-field { display: inline-flex; align-items: center; gap: 7px; color: var(--muted); font-size: 10px; white-space: nowrap; }
.admin-select-field select { min-height: 31px; border: 1px solid var(--line); border-radius: 8px; padding: 0 25px 0 9px; color: var(--ink); background: var(--surface); font-size: 10px; }
.feedback-toolbar .feedback-search { min-width: 210px; }
.table-privacy-note { display: flex; align-items: center; gap: 6px; margin: 13px 0 1px; color: var(--muted); font-size: 9px; }
.table-privacy-note svg { color: var(--accent-strong); }
.admin-table-wrap { overflow-x: auto; }
.admin-table { width: 100%; border-collapse: collapse; font-size: 10px; }
.admin-table th, .admin-table td { border-bottom: 1px solid var(--line); padding: 15px 9px; text-align: left; vertical-align: middle; }
.admin-table thead th { color: var(--muted); font-size: 9px; font-weight: 800; letter-spacing: .08em; white-space: nowrap; }
.admin-table tbody th { min-width: 185px; font-weight: 700; }
.admin-user-name { display: block; color: var(--ink); font-size: 11px; }
.admin-table tbody th small { display: block; margin-top: 4px; color: var(--accent-strong); font-size: 9px; font-weight: 500; }
.admin-role-list { display: flex; flex-wrap: wrap; gap: 5px; }
.admin-role { border-radius: 999px; padding: 5px 8px; color: var(--accent-strong); background: var(--accent-soft); font-size: 9px; font-weight: 700; }
.admin-status { display: inline-flex; align-items: center; gap: 6px; color: var(--coral); font-size: 10px; font-weight: 700; }
.admin-status.enabled { color: var(--accent-strong); }
.admin-status i, .review-status i { display: block; width: 6px; height: 6px; border-radius: 50%; background: currentColor; }
.status-action, .feedback-actions button { display: inline-flex; align-items: center; gap: 5px; min-height: 28px; border: 1px solid var(--line-strong); border-radius: 999px; padding: 0 10px; color: var(--ink); background: transparent; font-size: 9px; font-weight: 800; white-space: nowrap; }
.status-action:hover:not(:disabled), .feedback-actions button:hover:not(:disabled) { border-color: var(--accent); color: var(--accent-strong); background: var(--accent-soft); }
.feedback-table tbody th { min-width: 230px; }
.feedback-user { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.feedback-user strong { color: var(--ink); font-size: 11px; }
.feedback-user span { display: inline-flex; gap: 1px; color: var(--line-strong); font-size: 10px; letter-spacing: 0; }
.feedback-user b.filled { color: var(--gold); }
.feedback-table tbody th p { max-width: 250px; margin: 7px 0 4px; color: var(--muted); font-size: 10px; font-weight: 400; line-height: 1.55; }
.feedback-table tbody th small { color: var(--muted); font-size: 9px; font-weight: 400; }
.feedback-context { display: grid; gap: 4px; min-width: 130px; }
.feedback-context strong { color: var(--ink); font-size: 10px; }
.feedback-context span, .feedback-context small { color: var(--muted); font-size: 9px; font-weight: 400; }
.feedback-context small { color: var(--coral); }
.review-status { display: inline-flex; align-items: center; gap: 6px; border-radius: 999px; padding: 6px 8px; color: var(--coral); background: rgba(183,100,80,.1); font-size: 9px; font-weight: 800; white-space: nowrap; }
.review-status.reviewed { color: #876b2f; background: rgba(198,160,97,.16); }
.review-status.resolved { color: var(--accent-strong); background: var(--accent-soft); }
.muted-cell { color: var(--muted); font-size: 9px; white-space: nowrap; }
.feedback-actions { display: flex; flex-wrap: wrap; gap: 5px; max-width: 160px; }
.feedback-actions .resolve-action { border-color: var(--accent-strong); color: var(--accent-strong); }
.feedback-actions .resolve-action:hover:not(:disabled) { color: var(--surface); background: var(--accent-strong); }
.moderation-toolbar .moderation-search { min-width: 220px; }
.moderation-run { display: flex; align-items: flex-start; gap: 8px; margin: 14px 0 1px; border-bottom: 1px solid var(--line); padding: 0 0 13px; color: var(--accent-strong); font-size: 10px; line-height: 1.6; }
.moderation-run svg { flex: 0 0 auto; margin-top: 1px; }
.moderation-table { min-width: 1080px; }
.moderation-table tbody th { min-width: 260px; vertical-align: top; }
.moderation-content, .moderation-ai-summary, .moderation-status-cell, .moderation-published-cell { display: grid; gap: 5px; min-width: 0; }
.moderation-content strong { color: var(--ink); font-size: 12px; line-height: 1.45; }
.moderation-content > span, .moderation-content small, .moderation-ai-summary small, .moderation-status-cell small { color: var(--muted); font-size: 9px; font-weight: 400; line-height: 1.5; }
.moderation-content a { display: inline-flex; align-items: center; gap: 3px; width: fit-content; color: var(--accent-strong); font-size: 9px; font-weight: 800; text-decoration: underline; text-underline-offset: 3px; }
.moderation-content a:hover { color: var(--coral); }
.moderation-content p, .moderation-ai-summary p { max-width: 300px; margin: 2px 0; color: var(--muted); font-size: 10px; font-weight: 400; line-height: 1.6; }
.moderation-ai-summary { min-width: 190px; }
.moderation-ai-summary > div { display: flex; align-items: center; flex-wrap: wrap; gap: 5px; }
.moderation-decision { color: var(--ink); font-size: 10px; font-weight: 800; }
.moderation-risk { border-radius: 999px; padding: 4px 7px; color: var(--accent-strong); background: var(--accent-soft); font-size: 9px; font-weight: 800; }
.moderation-pending-note { color: var(--muted); font-size: 10px; }
.review-status.pending-human { color: #876b2f; background: rgba(198,160,97,.16); }
.review-status.pending-ai { color: var(--muted); background: var(--surface-soft); }
.review-status.ai-failed { color: var(--coral); background: rgba(183,100,80,.1); }
.review-status.approved { color: var(--accent-strong); background: var(--accent-soft); }
.review-status.rejected { color: var(--muted); background: var(--surface-soft); }
.moderation-review-form { display: grid; gap: 8px; min-width: 250px; }
.moderation-note-field textarea { display: block; width: 100%; min-height: 54px; resize: vertical; border: 1px solid var(--line); border-radius: 8px; padding: 8px 9px; outline: 0; color: var(--ink); background: var(--surface); font: inherit; font-size: 10px; line-height: 1.5; }
.moderation-note-field textarea:focus { border-color: var(--accent); box-shadow: 0 0 0 3px rgba(55,126,111,.12); }
.moderation-actions { display: flex; flex-wrap: wrap; gap: 5px; }
.moderation-actions button { display: inline-flex; align-items: center; gap: 5px; min-height: 28px; border: 1px solid var(--line-strong); border-radius: 999px; padding: 0 9px; color: var(--ink); background: transparent; font-size: 9px; font-weight: 800; white-space: nowrap; }
.moderation-actions button:hover:not(:disabled) { border-color: var(--accent); color: var(--accent-strong); background: var(--accent-soft); }
.moderation-actions .moderation-approve { border-color: var(--accent-strong); color: var(--surface); background: var(--accent-strong); }
.moderation-actions .moderation-approve:hover:not(:disabled) { color: var(--surface); background: var(--accent); }
.moderation-actions .moderation-reject { border-color: rgba(183,100,80,.45); color: var(--coral); }
.moderation-override-note { color: var(--coral); font-size: 9px; line-height: 1.5; }
.moderation-published-cell { align-items: start; color: var(--muted); font-size: 10px; line-height: 1.5; }
.moderation-published-cell strong { color: var(--accent-strong); font-size: 10px; }
.moderation-published-cell .status-action { width: fit-content; }
.audit-table { min-width: 690px; }
.audit-action { color: var(--ink); font-weight: 700; }
.audit-target { color: var(--muted); font-size: 9px; }
.audit-outcome { color: var(--accent-strong); font-size: 9px; font-weight: 800; }
.audit-details { max-width: 250px; color: var(--muted); font-size: 9px; line-height: 1.5; }
.admin-pagination { display: flex; align-items: center; justify-content: space-between; gap: 14px; padding-top: 13px; color: var(--muted); font-size: 10px; }
.admin-pagination > div { display: flex; gap: 6px; }
.admin-pagination button { display: inline-flex; align-items: center; gap: 4px; min-height: 27px; border: 1px solid var(--line); border-radius: 999px; padding: 0 9px; color: var(--ink); background: transparent; font-size: 9px; }
.admin-pagination button:hover:not(:disabled) { border-color: var(--accent); color: var(--accent-strong); }
.admin-loading, .admin-empty { display: flex; min-height: 185px; align-items: center; justify-content: center; gap: 8px; color: var(--muted); font-size: 10px; }
.admin-empty-inline { margin: 21px 0 0; color: var(--muted); font-size: 10px; }
.admin-forbidden { display: flex; width: min(calc(100% - 64px), 620px); align-items: center; gap: 12px; margin: 90px auto; border: 1px solid rgba(183,100,80,.35); border-radius: 11px; padding: 17px; color: var(--coral); background: rgba(183,100,80,.08); }
.admin-forbidden div { display: grid; gap: 5px; }
.admin-forbidden strong { color: var(--ink); font-size: 14px; }
.admin-forbidden span { color: var(--muted); font-size: 11px; }

@media (max-width: 900px) {
  .admin-layout { grid-template-columns: 1fr; gap: 23px; }
  .admin-sidebar { position: static; }
  .admin-privacy-note { display: none; }
  .admin-side-nav { grid-template-columns: repeat(5, 1fr); }
  .admin-side-nav button { grid-template-columns: 1fr; justify-items: center; gap: 6px; padding: 10px 7px; text-align: center; }
  .admin-side-nav button > span { justify-items: center; }
  .admin-side-nav small { display: none; }
}

@media (max-width: 680px) {
  .admin-workspace { width: min(calc(100% - 32px), 620px); padding: 24px 0 54px; }
  .admin-workspace-header { display: grid; align-items: start; gap: 20px; }
  .admin-back-button { margin-bottom: 20px; }
  .admin-identity-block { justify-items: start; }
  .admin-layout { padding-top: 21px; }
  .admin-sidebar-label { margin-left: 2px; }
  .admin-side-nav { gap: 4px; }
  .admin-side-nav button { border-radius: 9px; padding: 9px 3px; }
  .admin-side-nav strong { font-size: 9px; }
  .overview-grid, .overview-lower-grid { grid-template-columns: 1fr; }
  .overview-lead { min-height: 250px; padding: 21px; }
  .overview-lead-number { margin-top: 27px; font-size: 70px; }
  .admin-section-heading { align-items: start; flex-direction: column; gap: 8px; }
  .admin-section-heading > p { max-width: none; text-align: left; }
  .admin-table-panel { padding: 0 13px 10px; }
  .admin-toolbar { align-items: stretch; flex-wrap: wrap; }
  .admin-search-field { order: 1; min-width: 100%; }
  .admin-select-field { order: 0; }
  .toolbar-button { order: 0; }
  .toolbar-total { order: 2; margin-left: 0; }
  .feedback-toolbar .feedback-search { min-width: 100%; }
  .moderation-toolbar .moderation-search { min-width: 100%; }
  .feedback-table { min-width: 810px; }
  .admin-table { min-width: 570px; }
  .recent-action-list { grid-template-columns: 1fr 1fr; gap: 16px 0; }
  .recent-action-list > div:nth-child(2) { border-right: 0; }
  .recent-action-list > div:nth-child(3), .recent-action-list > div:nth-child(4) { border-top: 1px solid var(--line); padding-top: 16px; }
  .recent-action-list > div:nth-child(3) { padding-left: 0; }
  .admin-forbidden { width: min(calc(100% - 32px), 620px); margin: 50px auto; }
}
</style>
