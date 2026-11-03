<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ChevronDown, Search, SlidersHorizontal } from '@lucide/vue'
import { trendPlatformName } from '../utils/trendSources.js'

const props = defineProps({ app: { type: Object, required: true } })
const open = ref(false)
const state = computed(() => props.app.state)
const draft = reactive({ period: 'day', platform: '', topic: '' })
const platforms = computed(() => [...new Set(['douyin', 'weibo', 'xiaohongshu',
  ...(state.value.trendMeta?.sources || []).map(source => source.id)])]
  .filter(value => value && value !== 'configured-demo'))
const active = computed(() => state.value.trendPeriod === 'week' || state.value.trendPlatform || state.value.trendTopic)
const summary = computed(() => [state.value.trendPeriod === 'week' ? '近 7 天' : '近 24 小时',
  state.value.trendPlatform ? trendPlatformName(state.value.trendPlatform) : '全部来源',
  state.value.trendTopic ? `关键词：${state.value.trendTopic}` : ''].filter(Boolean).join(' · '))

watch(() => [state.value.trendPeriod, state.value.trendPlatform, state.value.trendTopic], () => {
  draft.period = state.value.trendPeriod || 'day'
  draft.platform = state.value.trendPlatform || ''
  draft.topic = state.value.trendTopic || ''
}, { immediate: true })

async function apply(reset = false) {
  if (state.value.trendsLoading) return
  state.value.trendPeriod = reset ? 'day' : draft.period
  state.value.trendPlatform = reset ? '' : draft.platform
  state.value.trendTopic = reset ? '' : draft.topic.trim()
  await props.app.loadTrends()
}
</script>

<template>
  <section class="trend-filter-panel" aria-label="穿搭高级筛选">
    <div class="filter-heading">
      <button type="button" class="filter-toggle" :aria-expanded="open" aria-controls="trend-filter-form" @click="open = !open">
        <SlidersHorizontal :size="16" aria-hidden="true" />高级筛选<ChevronDown :size="15" :class="{ rotated: open }" aria-hidden="true" />
      </button>
      <span v-if="open || active" class="filter-summary">筛选条件：{{ summary }}</span>
    </div>
    <p v-if="state.trendError" class="filter-error" role="alert">{{ state.trendError }}<template v-if="state.trends.length">本次筛选未完成，仍显示上次成功加载的内容。</template>请重新应用筛选，或恢复每日精选。</p>
    <form v-if="open" id="trend-filter-form" @submit.prevent="apply()">
      <fieldset :disabled="state.trendsLoading">
        <legend class="sr-only">鉴赏时间范围</legend>
        <div class="filter-field"><label for="trend-filter-period">时间范围</label><select id="trend-filter-period" v-model="draft.period"><option value="day">近 24 小时</option><option value="week">近 7 天</option></select></div>
        <div class="filter-field"><label for="trend-filter-platform">内容来源</label><select id="trend-filter-platform" v-model="draft.platform"><option value="">全部来源</option><option v-for="platform in platforms" :key="platform" :value="platform">{{ trendPlatformName(platform) }}</option></select></div>
        <div class="filter-field filter-topic"><label for="trend-filter-topic">标题或标签关键词</label><input id="trend-filter-topic" v-model="draft.topic" type="search" maxlength="120" placeholder="例如：白衬衫、通勤" /></div>
        <div class="filter-actions"><button type="button" @click="apply(true)">恢复每日精选</button><button type="submit" class="filter-submit"><Search :size="15" aria-hidden="true" />{{ state.trendsLoading ? '筛选中…' : '应用筛选' }}</button></div>
      </fieldset>
    </form>
  </section>
</template>

<style scoped>
.trend-filter-panel { margin:0 0 24px; padding:0 0 16px; border-bottom:1px solid var(--line); }
.filter-heading { display:flex; align-items:center; flex-wrap:wrap; gap:10px 20px; }
.filter-toggle { display:inline-flex; align-items:center; gap:8px; min-height:40px; border:1px solid var(--line); border-radius:7px; padding:8px 12px; background:var(--surface); color:var(--ink); font:inherit; font-size:12px; cursor:pointer; }
.rotated { transform:rotate(180deg); }
.filter-summary { color:var(--muted); font-size:12px; overflow-wrap:anywhere; }
form { margin-top:16px; }
fieldset { display:grid; grid-template-columns:140px 150px minmax(180px,1fr) auto; gap:14px; align-items:end; border:0; margin:0; padding:0; min-width:0; }
.filter-field { display:grid; gap:7px; min-width:0; color:var(--muted); font-size:12px; }
input,select { width:100%; min-width:0; min-height:42px; border:1px solid var(--line); border-radius:6px; padding:9px 11px; background:var(--surface); color:var(--ink); font:inherit; }
.filter-actions { display:flex; gap:8px; }
.filter-actions button { display:inline-flex; align-items:center; justify-content:center; gap:6px; min-height:42px; border:1px solid var(--line); border-radius:6px; padding:9px 12px; background:var(--surface); color:var(--ink); font:inherit; font-size:12px; cursor:pointer; }
.filter-actions .filter-submit { border-color:var(--accent); background:var(--accent); color:var(--surface); }
.filter-error { margin:12px 0 0; color:var(--danger, #a33b32); font-size:12px; line-height:1.7; }
button:focus-visible,input:focus-visible,select:focus-visible { outline:2px solid var(--accent); outline-offset:3px; }
button:disabled,fieldset:disabled { opacity:.65; }
@media(max-width:1100px) { fieldset { grid-template-columns:1fr 1fr; } .filter-actions { align-self:end; } }
@media(max-width:600px) { fieldset { grid-template-columns:1fr; } .filter-actions button { flex:1; } }
</style>
