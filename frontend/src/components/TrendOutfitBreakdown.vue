<script setup>
import { computed } from 'vue'
import { ArrowRight, ArrowUpRight, BookOpen, Shirt } from '@lucide/vue'
import { trendOutfitAnalysis, isTrendAnalysisExample } from '../data/trendOutfitAnalyses.js'
import { externalTrendSourceUrl, trendPlatformName } from '../utils/trendSources.js'

const props = defineProps({
  trend: { type: Object, required: true },
  wardrobe: { type: Array, default: () => [] },
  wardrobeLoading: { type: Boolean, default: false }
})
const emit = defineEmits(['open-analysis', 'use-trend'])
const analysis = computed(() => trendOutfitAnalysis(props.trend))
const sourceUrl = computed(() => externalTrendSourceUrl(props.trend))
const sourceDate = computed(() => {
  if (!props.trend.publishedAt) return ''
  const date = new Date(props.trend.publishedAt)
  return Number.isNaN(date.getTime()) ? '' : date.toLocaleDateString('zh-CN', { timeZone: 'Asia/Shanghai' })
})
const confirmedCount = computed(() => props.wardrobe.filter(item => item?.id && item.recognitionStatus !== 'NEEDS_MANUAL_REVIEW').length)
</script>

<template>
  <section class="trend-breakdown" aria-labelledby="trend-breakdown-title">
    <div class="brief-heading">
      <div>
        <p class="brief-eyebrow">{{ isTrendAnalysisExample(trend) ? '历史分析示例' : '当前选中的穿搭' }}</p>
        <h2 id="trend-breakdown-title">{{ trend.title }}</h2>
        <p class="brief-meta">{{ trend.evidence?.author || trendPlatformName(trend.platform) }}<template v-if="trend.evidence?.author"> · {{ trendPlatformName(trend.platform) }}</template><template v-if="sourceDate"> · {{ sourceDate }}</template></p>
      </div>
      <a v-if="sourceUrl" class="source-link" :href="sourceUrl" target="_blank" rel="noopener noreferrer">查看原帖<ArrowUpRight :size="15" aria-hidden="true" /></a>
    </div>

    <p v-if="trend.dailySlot" class="brief-daily-slot"><strong>{{ trend.dailySlot.label }}</strong><span v-if="trend.dailySlot.category === 'niche'">{{ trend.dailySlot.reason }}</span></p>

    <p v-if="analysis" class="analysis-scope">{{ analysis.scope }}</p>
    <ol v-if="analysis" class="brief-points" aria-label="这套穿搭的三个要点">
      <li v-for="(point, index) in analysis.points" :key="point.id">
        <span class="brief-number">0{{ index + 1 }}</span>
        <div><h3>{{ point.title }}</h3><p>{{ point.brief }}</p></div>
      </li>
    </ol>
    <div v-else class="brief-source">
      <p v-if="trend.summary">{{ trend.summary }}</p>
      <p v-else>来源未提供摘要，可打开详情查看已收录的图片与原帖。</p>
      <div v-if="trend.topicTags?.length" class="brief-tags"><span v-for="tag in trend.topicTags" :key="tag">{{ tag }}</span></div>
      <span>暂无深入解读，搭配细节以原帖为准。</span>
    </div>

    <footer class="brief-actions">
      <p v-if="wardrobeLoading">正在读取你的衣橱…</p>
      <p v-else-if="confirmedCount">可结合衣橱中 {{ confirmedCount }} 件已确认衣物尝试搭配。</p>
      <p v-else>可以先看懂搭法，再从自己的衣橱开始尝试。</p>
      <div class="brief-buttons">
        <button type="button" class="brief-secondary" @click="emit('open-analysis', trend)"><BookOpen :size="17" aria-hidden="true" />深入分析<ArrowRight :size="15" aria-hidden="true" /></button>
        <button type="button" class="brief-primary" @click="emit('use-trend', trend)"><Shirt :size="17" aria-hidden="true" />用我的衣橱搭一套</button>
      </div>
    </footer>
  </section>
</template>

<style scoped>
.trend-breakdown { border:1px solid var(--line); border-radius:16px; padding:var(--trend-panel-padding, 24px); background:var(--surface-soft); color:var(--ink); }
.brief-heading { display:flex; align-items:flex-start; justify-content:space-between; gap:24px; }
.brief-eyebrow { margin:0 0 9px; color:var(--accent); font-size:12px; font-weight:700; }
.brief-heading h2 { margin:0; font-family:var(--font-display); font-size:clamp(23px,2.3vw,30px); font-weight:600; line-height:1.4; overflow-wrap:anywhere; }
.brief-meta { margin:10px 0 0; color:var(--muted); font-size:12px; line-height:1.7; }
.source-link { display:inline-flex; align-items:center; gap:5px; flex-shrink:0; min-height:40px; color:var(--accent); text-decoration:none; font-size:12px; }
.analysis-scope { margin:17px 0 0; color:var(--muted); font-size:12px; }
.brief-daily-slot { display:flex; flex-wrap:wrap; align-items:baseline; gap:7px 14px; margin:18px 0 0; color:var(--accent); font-size:12px; line-height:1.8; }
.brief-daily-slot span { color:var(--muted); }
.brief-points { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:24px; padding:0; margin:17px 0 23px; list-style:none; }
.brief-points li { display:flex; align-items:flex-start; gap:11px; min-width:0; border-top:1px solid var(--line); padding-top:15px; }
.brief-number { margin-top:2px; color:var(--accent); font-size:12px; font-weight:700; }
.brief-points h3 { margin:0 0 7px; font-size:14px; line-height:1.65; }
.brief-points p { margin:0; color:var(--muted); font-size:13px; line-height:1.85; }
.brief-source { margin:20px 0; font-size:14px; line-height:1.85; }
.brief-source>p { max-width:980px; margin:0 0 12px; white-space:pre-line; overflow-wrap:anywhere; }
.brief-source>span { display:block; color:var(--muted); font-size:12px; margin-top:12px; }
.brief-tags { display:flex; flex-wrap:wrap; gap:8px 15px; color:var(--accent); font-size:12px; }
.brief-actions { display:flex; justify-content:space-between; align-items:center; gap:20px; border-top:1px solid var(--line); padding-top:18px; }
.brief-actions>p { margin:0; color:var(--muted); font-size:12px; line-height:1.7; }
.brief-buttons { display:flex; gap:10px; flex-shrink:0; }
.brief-buttons button { display:inline-flex; align-items:center; justify-content:center; gap:8px; min-height:46px; border:1px solid var(--accent); border-radius:8px; padding:10px 16px; font:inherit; font-size:13px; cursor:pointer; }
.brief-secondary { background:var(--surface); color:var(--accent); }
.brief-primary { background:var(--accent); color:var(--surface); }
button:focus-visible,a:focus-visible { outline:2px solid var(--accent-strong); outline-offset:4px; }
@media(max-width:900px) { .brief-points { grid-template-columns:1fr; gap:14px; } .brief-points p { max-width:680px; } .brief-actions { align-items:flex-start; flex-direction:column; gap:14px; } }
@media(max-width:760px) { .trend-breakdown { padding:var(--trend-panel-padding, 20px); border-radius:12px; } }
@media(max-width:600px) { .brief-heading { gap:10px; flex-wrap:wrap; } .brief-heading h2 { font-size:23px; } .brief-buttons { flex-wrap:wrap; width:100%; } .brief-buttons button { flex:1 1 160px; } .brief-actions { align-items:stretch; } .source-link { min-height:32px; } }
</style>
