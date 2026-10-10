<script setup>
import { computed, ref } from 'vue'
import { trendImageCandidates, trendDisplayImageUrl } from '../utils/trendImage.js'
import { ArrowUpRight, RefreshCw, Shirt, ImageOff } from '@lucide/vue'
import {
  externalTrendSourceUrl,
  isConfiguredDemoTrend,
  shouldShowStaleTrend,
  trendPlatformName,
  trendSourceCounts,
  trendSourceExclusionReason,
  trendSourceMessage
} from '../utils/trendSources.js'
const props = defineProps({ app: { type: Object, required: true }, controlsOnly: Boolean, postsOnly: Boolean, limit: { type: Number, default: 3 } })
const emit = defineEmits(['filter-style'])
const state = computed(() => props.app.state)
const demoMode = computed(() => Boolean(state.value.trendMeta?.demoMode))
const tag = ref('')
const broken = ref(new Set())
const names = { douyin: '抖音', weibo: '微博', xiaohongshu: '小红书', editorial: '时尚编辑', 'configured-demo': '穿搭参考', 'configured-feed': '已连接数据源', 'web-scrape': '公开网页' }
const creatorTierNames = { mainstream: '主流博主', niche: '小众博主', unclassified: '粉丝数量未提供' }
const states = { ready: '已连接', unavailable: '采集失败', unconfigured: '未配置', pending: '等待采集' }
const matches = item => (item.topicTags || []).filter(t => (state.value.profile?.stylePreferences || []).some(p => p.includes(t) || t.includes(p)))
const items = computed(() => [...state.value.trends.filter(i => !tag.value || i.topicTags?.includes(tag.value))].sort((a, b) => matches(b).length - matches(a).length).slice(0, props.limit || undefined))
const date = v => v ? new Date(v).toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }) : '尚未更新'
function selectTag(value) { tag.value = tag.value === value ? '' : value; emit('filter-style', tag.value || '全部') }
function formatFollowers(value) { return `${Number(value).toLocaleString('zh-CN')} 位粉丝` }
function formatFollowerEvidence(evidence) {
  if (evidence?.authorFollowersLabel) return `粉丝约 ${evidence.authorFollowersLabel}`
  return evidence?.authorFollowers != null ? formatFollowers(evidence.authorFollowers) : ''
}
function isOutfitReviewVideo(item) {
  return item.evidence?.mediaType === 'video'
    && /(测评|评测|评价|点评|试穿|横评|对比|上身|review|try[\s-]?on)/i.test(`${item.title || ''} ${item.summary || ''} ${(item.topicTags || []).join(' ')}`)
}
function imageFor(item) {
  return trendImageCandidates(item.imageUrl).map(trendDisplayImageUrl).find(url => !broken.value.has(url)) || ''
}
function imageUnavailableLabel(item) {
  if (item.imageUrl) return externalTrendSourceUrl(item) ? '图片加载失败，可查看原文' : '图片加载失败'
  if (item.evidence?.mediaType === 'board') return '当前来源仅提供榜单话题，未提供配图'
  return isConfiguredDemoTrend(item) ? '穿搭参考未提供配图' : '来源未提供配图，请查看来源内容'
}
async function filter(key, value) {
  state.value[key] = value
  state.value.trends = []
  state.value.trendStyles = []
  state.value.trendMeta.fetchedAt = null
  tag.value = ''
  emit('filter-style', '全部')
  await props.app.loadTrends()
}
</script>
<template>
  <section class="trend-discovery" :class="{ 'controls-only': controlsOnly }" aria-label="热门穿搭与流行风格">
    <header v-if="!controlsOnly && !postsOnly" class="discovery-heading">
      <div><h2>{{ controlsOnly ? '趋势筛选' : '穿搭内容精选' }}</h2><p v-if="!controlsOnly">浏览已收录内容，按时间、来源与风格筛选。</p></div>
      <button type="button" class="text-action" :disabled="state.trendsLoading" @click="app.loadTrends()"><RefreshCw :size="16" />重新加载</button>
    </header>
    <div v-if="!postsOnly" class="discovery-filters">
      <div class="period-switch" role="group" aria-label="趋势时间范围">
        <button type="button" aria-pressed="true" @click="filter('trendPeriod', 'day')">近 24 小时</button>
      </div>
      <label>内容来源<select :value="state.trendPlatform" :disabled="demoMode" @change="filter('trendPlatform', $event.target.value)"><option value="">全部来源</option><option value="douyin">抖音</option><option value="weibo">微博</option><option value="xiaohongshu">小红书</option></select></label>
      <span class="updated">每日 12:00 更新（北京时间）<template v-if="!demoMode && state.trendMeta.fetchedAt"> · 最近采集 {{ date(state.trendMeta.fetchedAt) }}</template><template v-else-if="demoMode"> · 穿搭参考</template></span>
      <button v-if="controlsOnly" type="button" class="text-action control-refresh" :disabled="state.trendsLoading" @click="app.loadTrends()"><RefreshCw :size="16" />重新加载</button>
    </div>
    <div v-if="!controlsOnly && state.trendStyles?.length" class="style-strip" aria-label="当前内容中的流行风格">
      <button v-for="style in state.trendStyles" :key="style.name" type="button" :aria-pressed="tag === style.name" @click="selectTag(style.name)"><strong>{{ style.name }}</strong><span>{{ style.contentCount }} 篇提及</span></button>
    </div>
    <p v-if="!controlsOnly && state.trendError" class="discovery-message" role="alert">{{ state.trendError }}<button type="button" @click="app.loadTrends()">重试</button></p>
    <p v-else-if="!controlsOnly && state.trendsLoading" class="discovery-message" role="status">正在加载穿搭趋势…</p>
    <div v-else-if="!controlsOnly && !state.trends.length" class="discovery-empty" role="status"><h3>当前筛选范围暂无穿搭内容</h3><p>可更换来源或稍后重新加载，并在下方查看数据源状态。</p></div>
    <div v-if="!controlsOnly && !state.trendsLoading && !state.trendError" class="discovery-looks">
      <article v-for="item in items" :key="item.id" class="discovery-look">
        <component
          :is="externalTrendSourceUrl(item) ? 'a' : 'div'"
          class="look-picture"
          :href="externalTrendSourceUrl(item) || undefined"
          :target="externalTrendSourceUrl(item) ? '_blank' : undefined"
          :rel="externalTrendSourceUrl(item) ? 'noopener noreferrer' : undefined"
          :aria-label="externalTrendSourceUrl(item) ? `在来源平台查看：${item.title}` : undefined"
        >
          <img v-if="imageFor(item)" :src="imageFor(item)" :alt="item.title" loading="lazy" referrerpolicy="no-referrer" @error="broken = new Set([...broken, imageFor(item)])" />
          <span v-else class="image-unavailable"><ImageOff :size="26" />{{ imageUnavailableLabel(item) }}</span><span class="source-mark">{{ names[item.platform] || item.platform }}</span>
        </component>
        <div class="look-details">
          <p class="look-meta">{{ item.evidence?.author || names[item.platform] || item.platform }} · {{ date(item.publishedAt) }}</p><h3>{{ item.title }}</h3><p class="look-tags">{{ (item.topicTags || []).join(' / ') }}</p>
          <p v-if="item.evidence?.creatorTier || item.evidence?.authorFollowers != null || item.evidence?.authorFollowersLabel || isOutfitReviewVideo(item)" class="look-badges"><span v-if="item.evidence?.creatorTier">{{ creatorTierNames[item.evidence.creatorTier] || item.evidence.creatorTier }}</span><span v-if="formatFollowerEvidence(item.evidence)">{{ formatFollowerEvidence(item.evidence) }}</span><span v-if="isOutfitReviewVideo(item)">穿搭测评视频</span></p>
          <p v-if="matches(item).length" class="match-reason">与你偏好的 {{ matches(item).join('、') }} 相关</p>
          <p v-if="shouldShowStaleTrend(item)" class="look-meta">上次收录于 {{ date(item.fetchedAt) }}，等待更新</p>
          <p class="look-meta">{{ item.evidence?.scoreLabel || '来源内评分' }}<template v-if="item.platform !== 'editorial'"> {{ item.heatScore }}</template></p>
          <div class="look-actions"><button type="button" @click="app.useTrend(item)"><Shirt :size="16" />参考风格生成搭配</button><a v-if="externalTrendSourceUrl(item)" :href="externalTrendSourceUrl(item)" target="_blank" rel="noopener noreferrer" aria-label="在来源平台查看"><ArrowUpRight :size="20" /></a></div>
        </div>
      </article>
    </div>
    <button v-if="!controlsOnly && !postsOnly && state.trends.length" class="text-action more-trends" type="button" @click="app.selectView('trend')">查看全部 {{ state.trends.length }} 条内容 <ArrowUpRight :size="16" /></button>
    <details v-if="!controlsOnly && !postsOnly" class="source-details"><summary>来源与统计说明</summary><p>{{ state.trendMeta.notice }}</p>
      <ul><li v-for="source in state.trendMeta.sources || []" :key="source.id"><strong>{{ trendPlatformName(source.id) }}</strong><span>{{ trendSourceMessage(source) }}</span><span v-if="trendSourceCounts(source)">{{ trendSourceCounts(source) }}</span><span v-if="trendSourceExclusionReason(source)">原因：{{ trendSourceExclusionReason(source) }}</span><time v-if="source.lastSuccessAt">最近成功更新 {{ date(source.lastSuccessAt) }}</time></li></ul>
      <button v-if="app.isAdmin" class="text-action" type="button" :disabled="state.trendsLoading" @click="app.refreshTrendSources()">刷新已连接数据源</button>
    </details>
  </section>
</template>
<style scoped>
.trend-discovery {
  color:#202923;
  padding:36px 0;
  border-top:1px solid #dddcd3
}
.trend-discovery.controls-only {
  padding:14px 0 0;
  border-top:0
}
.controls-only .discovery-filters {
  gap:12px 18px
}
.controls-only .updated {
  margin-left:auto
}
.control-refresh {
  min-height:42px
}
.discovery-heading {
  display:flex;
  justify-content:space-between;
  align-items:start;
  gap:24px;
  margin-bottom:24px
}
.discovery-heading h2 {
  font-size:clamp(24px,3vw,36px);
  line-height:1.25;
  margin:0 0 10px
}
.discovery-heading p,.look-meta,.look-tags,.updated,.source-details {
  color:#596259;
  font-size:13px;
  line-height:1.65
}
.discovery-heading p {
  margin:0;
  font-size:15px
}
button {
  font:inherit;
  cursor:pointer
}
button:disabled {
  opacity:.55;
  cursor:wait
}
button:focus-visible,a:focus-visible,select:focus-visible,summary:focus-visible {
  outline:2px solid #345647;
  outline-offset:4px
}
.text-action {
  border:0;
  background:transparent;
  color:#345647;
  display:inline-flex;
  align-items:center;
  gap:8px;
  min-height:44px;
  white-space:nowrap
}
.discovery-filters {
  display:flex;
  flex-wrap:wrap;
  align-items:center;
  gap:16px 24px
}
.period-switch {
  display:inline-flex;
  border:1px solid #c3c7bd;
  border-radius:24px;
  padding:3px
}
.period-switch button {
  min-height:38px;
  border:0;
  border-radius:20px;
  padding:0 16px;
  background:transparent;
  color:#344137
}
.period-switch button[aria-pressed=true] {
  background:#263e32;
  color:#fff
}
.discovery-filters label {
  display:flex;
  align-items:center;
  gap:8px;
  font-size:13px
}
.discovery-filters select {
  font:inherit;
  min-height:42px;
  border:1px solid #c3c7bd;
  border-radius:6px;
  background:#fbfaf6;
  padding:8px;
  color:inherit
}
.updated {
  margin-left:auto
}
.style-strip {
  display:flex;
  flex-wrap:wrap;
  gap:8px 22px;
  margin:20px 0 26px
}
.style-strip button {
  display:flex;
  gap:9px;
  align-items:baseline;
  min-height:40px;
  border:0;
  border-bottom:1px solid transparent;
  background:none;
  color:#344137;
  padding:6px 0
}
.style-strip button[aria-pressed=true] {
  border-color:#345647
}
.style-strip span {
  font-size:12px;
  color:#596259
}
.discovery-looks {
  display:grid;
  grid-template-columns:repeat(3,minmax(0,1fr));
  gap:26px;
  margin-top:26px
}
.discovery-look {
  min-width:0
}
.look-picture {
  display:block;
  position:relative;
  aspect-ratio:3/4;
  overflow:hidden;
  background:#e4e7df;
  color:#345647
}
.look-picture img {
  width:100%;
  height:100%;
  object-fit:cover;
  transition:transform .25s ease
}
.look-picture:hover img {
  transform:scale(1.025)
}
.image-unavailable {
  display:flex;
  height:100%;
  justify-content:center;
  align-items:center;
  flex-direction:column;
  gap:12px;
  font-size:14px
}
.source-mark {
  position:absolute;
  left:12px;
  bottom:12px;
  background:#fbfaf6;
  padding:6px 10px;
  font-size:12px
}
.look-details h3 {
  margin:10px 0;
  font-size:18px;
  line-height:1.5;
  display:-webkit-box;
  -webkit-line-clamp:3;
  -webkit-box-orient:vertical;
  overflow:hidden;
  overflow-wrap:anywhere
}
.look-meta {
  margin:12px 0 6px
}
.look-tags {
  margin:6px 0
}
.look-badges {
  display:flex;
  flex-wrap:wrap;
  gap:6px;
  margin:10px 0;
  color:#345647;
  font-size:12px
}
.look-badges span {
  border:1px solid #c3c7bd;
  padding:3px 8px
}
.match-reason {
  color:#345647;
  font-size:13px
}
.look-actions {
  display:flex;
  gap:10px;
  margin-top:16px;
  align-items:center
}
.look-actions button {
  display:inline-flex;
  align-items:center;
  justify-content:center;
  gap:8px;
  min-height:44px;
  padding:10px 14px;
  border:1px solid #345647;
  background:transparent;
  color:#263e32
}
.look-actions button:hover {
  background:#e6ece2
}
.look-actions a {
  color:#345647;
  padding:10px
}
.more-trends {
  margin-top:24px
}
.discovery-empty,.discovery-message {
  padding:28px 0
}
.discovery-empty h3 {
  font-size:20px
}
.discovery-empty p {
  color:#596259
}
.source-details {
  margin-top:28px;
  padding-top:16px;
  border-top:1px solid #dddcd3
}
.source-details summary {
  cursor:pointer;
  padding:6px 0
}
.source-details ul {
  padding:0;
  list-style:none
}
.source-details li {
  display:flex;
  gap:14px;
  flex-wrap:wrap;
  margin:12px 0
}
@media(max-width:700px) {
  .discovery-heading {
    display:block
  }
  .discovery-heading>button {
    margin-top:8px
  }
  .discovery-looks {
    grid-template-columns:1fr;
    gap:30px
  }
  .look-picture {
    aspect-ratio:4/3
  }
  .updated {
    margin-left:0;
    width:100%
  }
  .discovery-filters {
    gap:12px
  }
}
@media(prefers-reduced-motion:reduce) {
  .look-picture img {
    transition:none
  }
}
</style>
