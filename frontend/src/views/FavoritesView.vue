<script setup>
import { computed, ref } from 'vue'
import { ArrowUpRight, Camera, Heart, ImageOff, LoaderCircle, RefreshCw } from '@lucide/vue'
import { canTryOnGarment } from '../utils/liveTryOn.js'
const props = defineProps({ app: { type: Object, required: true } })
const emit = defineEmits(['open-try-on'])
const query = ref('')
const broken = ref(new Set())
const items = computed(() => props.app.state.tryOnFavorites.filter(item =>
  `${item.name} ${item.category}`.includes(query.value.trim())))
function sourceLink(value) {
  try { const url = new URL(value); return ['http:', 'https:'].includes(url.protocol) && !url.username && !url.password ? url.href : null }
  catch { return null }
}
function failed(id) { broken.value = new Set([...broken.value, id]) }
function open(item) { emit('open-try-on', [item, ...items.value.filter(value => value.id !== item.id)]) }
</script>
<template>
  <section class="favorites-page">
    <header><div><p class="eyebrow"><Heart :size="15" />喜欢穿搭</p><h1>喜欢穿搭</h1><p>收下试穿后心动的单品，随时再看看上身效果，或回到原商品页。</p></div><button type="button" class="refresh" :disabled="app.state.tryOnFavoritesLoading" @click="app.loadTryOnFavorites()"><RefreshCw :size="16" />刷新收藏</button></header>
    <div class="favorites-tools"><span>已收藏 {{ app.state.tryOnFavorites.length }} 件试穿单品</span><input v-model="query" type="search" maxlength="100" aria-label="搜索喜欢的单品" placeholder="搜索名称或类别"></div>
    <div v-if="app.state.tryOnFavoritesLoading" class="favorites-state" role="status"><LoaderCircle :size="24" />正在加载喜欢的单品…</div>
    <div v-else-if="app.state.tryOnFavoritesError" class="favorites-state" role="alert"><strong>收藏加载失败</strong><p>{{ app.state.tryOnFavoritesError }}</p><button type="button" @click="app.loadTryOnFavorites()">重新加载</button></div>
    <div v-else-if="!items.length" class="favorites-state"><Heart :size="32" /><strong>{{ query ? '没有找到匹配的单品' : '还没有喜欢的试穿单品' }}</strong><p>去趋势页开启试穿浮窗，看到喜欢的款式就把它保存回来。</p><a href="#trend">去开启试穿 <ArrowUpRight :size="15" /></a></div>
    <div v-else class="favorites-grid">
      <article v-for="item in items" :key="item.id" class="favorite-card">
        <div class="favorite-image"><img v-if="!broken.has(item.id)" :src="item.imageUrl" :alt="item.name" loading="lazy" @error="failed(item.id)"><ImageOff v-else :size="32" /></div>
        <div class="favorite-copy"><p>{{ item.category }} · {{ item.sourceKind === 'TREND_ILLUSTRATION' ? '趋势示意款' : '购物单品' }}</p><h2>{{ item.name }}</h2><div class="favorite-actions"><button type="button" :disabled="!canTryOnGarment(item) || broken.has(item.id)" @click="open(item)"><Camera :size="15" />再次试穿</button><a v-if="sourceLink(item.sourceUrl)" :href="sourceLink(item.sourceUrl)" target="_blank" rel="noopener noreferrer">{{ item.sourceKind === 'TREND_ILLUSTRATION' ? '查看趋势来源' : '查看原商品' }}<ArrowUpRight :size="15" /></a></div></div>
      </article>
    </div>
  </section>
</template>
<style scoped>
.favorites-page{width:min(calc(100% - 48px),1440px);margin:0 auto;padding:34px 0 100px;color:var(--ink)}header{display:flex;align-items:center;justify-content:space-between;gap:24px}header h1{margin:0;font-family:var(--font-display);font-size:clamp(38px,5vw,62px);font-weight:600}.eyebrow{display:flex;align-items:center;gap:8px;margin:0 0 12px;color:var(--muted);font-size:12px}header>div>p:last-child{margin:16px 0;color:var(--muted);font-size:14px;line-height:1.8}button,a{display:inline-flex;align-items:center;gap:8px;font:inherit;font-size:13px}button{min-height:40px;border:1px solid var(--line);border-radius:var(--radius);padding:9px 14px;color:var(--ink);background:var(--surface);cursor:pointer}button:disabled{opacity:.5}a{color:var(--accent);text-decoration:none}.favorites-tools{display:flex;align-items:center;justify-content:space-between;gap:20px;padding:24px 0;border-top:1px solid var(--line);color:var(--muted);font-size:13px}.favorites-tools input{width:260px;max-width:100%;border:1px solid var(--line);border-radius:var(--radius);padding:10px 12px;background:var(--surface);color:var(--ink);font:inherit}.favorites-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:24px}.favorite-card{overflow:hidden;border:1px solid var(--line);border-radius:var(--radius);background:var(--surface)}.favorite-image{display:grid;height:250px;place-items:center;background:var(--surface-soft);color:var(--muted)}.favorite-image img{width:100%;height:100%;object-fit:contain}.favorite-copy{padding:20px}.favorite-copy>p{margin:0 0 8px;font-size:12px;color:var(--muted)}.favorite-copy h2{margin:0 0 18px;font-size:19px;font-weight:600;overflow-wrap:anywhere}.favorite-actions{display:flex;flex-wrap:wrap;gap:12px}.favorites-state{display:flex;min-height:330px;flex-direction:column;align-items:center;justify-content:center;gap:16px;text-align:center;color:var(--muted)}.favorites-state strong{color:var(--ink);font-size:20px}.favorites-state p{margin:0;line-height:1.8}@media(max-width:1000px){.favorites-grid{grid-template-columns:repeat(2,minmax(0,1fr))}}@media(max-width:600px){.favorites-page{width:calc(100% - 32px)}header{align-items:start;flex-direction:column;gap:10px}.favorites-tools{align-items:start;flex-direction:column;gap:14px}.favorites-grid{grid-template-columns:1fr}.favorite-image{height:280px}}
</style>
