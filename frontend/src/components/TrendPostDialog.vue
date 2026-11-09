<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { ArrowUpRight, X } from '@lucide/vue'
import { trendGalleryImageUrl } from '../utils/trendGallery.js'
import { trendDisplayImageUrl, trendImageCandidates } from '../utils/trendImage.js'
import { externalTrendSourceUrl, trendPlatformName } from '../utils/trendSources.js'

const props = defineProps({ trend: { type: Object, required: true } })
const emit = defineEmits(['close', 'use-trend'])
const dialog = ref(null)
const broken = ref(new Set())
const urls = computed(() => [...new Set([trendGalleryImageUrl(props.trend), ...(props.trend.evidence?.images || []), props.trend.imageUrl].filter(Boolean))])
const selected = ref(0)
const image = computed(() => trendImageCandidates(urls.value[selected.value]).map(trendDisplayImageUrl).find(url => !broken.value.has(url)))
const source = computed(() => externalTrendSourceUrl(props.trend))
let previousFocus, previousOverflow
onMounted(() => {
  previousFocus = document.activeElement
  previousOverflow = document.documentElement.style.overflow
  document.documentElement.style.overflow = 'hidden'
  dialog.value.showModal()
})
onBeforeUnmount(() => {
  document.documentElement.style.overflow = previousOverflow
  void nextTick(() => previousFocus?.isConnected && previousFocus.focus({ preventScroll: true }))
})
</script>

<template>
  <Teleport to="body">
    <dialog ref="dialog" class="post-dialog" aria-labelledby="post-title" @cancel.prevent="emit('close')" @click.self="emit('close')">
      <button class="post-close" type="button" aria-label="关闭穿搭介绍" autofocus @click="emit('close')"><X :size="22" /></button>
      <div class="post-layout">
        <div class="post-media">
          <img v-if="image" :src="image" :alt="`穿搭图片 ${selected + 1}`" referrerpolicy="no-referrer" @error="broken = new Set([...broken, image])">
          <p v-else>图片暂时无法显示</p>
          <div v-if="urls.length > 1" class="post-pages" role="group" aria-label="切换图片">
            <button v-for="(_, index) in urls" :key="index" type="button" :aria-pressed="selected === index" :aria-label="`查看第 ${index + 1} 张图片`" @click="selected = index">{{ index + 1 }}</button>
          </div>
        </div>
        <div class="post-copy">
          <p class="post-author">{{ trend.evidence?.author || trendPlatformName(trend.platform) }}<template v-if="trend.evidence?.author"> · {{ trendPlatformName(trend.platform) }}</template></p>
          <h2 id="post-title">穿搭介绍</h2>
          <p class="post-description">{{ trend.summary || trend.title || '查看原帖了解这套穿搭。' }}</p>
          <p v-if="trend.topicTags?.length" class="post-tags">{{ trend.topicTags.join(' · ') }}</p>
          <div class="post-actions"><a v-if="source" :href="source" target="_blank" rel="noopener noreferrer">查看原帖 <ArrowUpRight :size="16" /></a><button type="button" @click="emit('use-trend', trend)">用我的衣橱搭一套</button></div>
        </div>
      </div>
    </dialog>
  </Teleport>
</template>

<style scoped>
.post-dialog { width:min(960px,calc(100% - 32px)); max-height:min(850px,calc(100dvh - 32px)); padding:0; border:0; border-radius:16px; background:#f7f5ef; color:#203b31; box-shadow:0 20px 70px #12271d55; overflow:auto; }
.post-dialog::backdrop { background:#10231bbb; }
.post-close { position:absolute; z-index:2; right:16px; top:16px; display:grid; place-items:center; width:42px; height:42px; border:0; border-radius:50%; background:#f7f5ef; color:#203b31; cursor:pointer; }
.post-layout { display:grid; grid-template-columns:minmax(0,1.1fr) minmax(0,1fr); min-height:480px; }
.post-media { background:#e6e5dc; min-height:480px; display:flex; align-items:center; justify-content:center; position:relative; }
.post-media img { width:100%; height:100%; max-height:80vh; object-fit:contain; }
.post-pages { position:absolute; bottom:16px; display:flex; gap:6px; flex-wrap:wrap; justify-content:center; }
.post-pages button { width:32px; height:32px; border:0; border-radius:50%; background:#f7f5ef; color:#203b31; cursor:pointer; }
.post-pages button[aria-pressed=true] { background:#244d3d; color:white; }
.post-copy { padding:64px 36px 36px; display:flex; flex-direction:column; align-items:flex-start; }
.post-author,.post-tags { color:#586c60; font-size:13px; line-height:1.6; }
.post-copy h2 { font-size:27px; margin:12px 0 20px; }
.post-description { white-space:pre-line; overflow-wrap:anywhere; line-height:1.9; font-size:15px; margin:0; }
.post-actions { margin-top:auto; padding-top:28px; display:flex; flex-wrap:wrap; gap:12px; align-items:center; }
.post-actions a,.post-actions button { display:inline-flex; align-items:center; gap:6px; border:1px solid #285340; border-radius:8px; padding:12px 16px; font:inherit; text-decoration:none; cursor:pointer; }
.post-actions a { color:#285340; }.post-actions button { background:#285340; color:white; }
@media(max-width:700px) { .post-layout { grid-template-columns:1fr; } .post-media { min-height:280px; } .post-media img { max-height:55dvh; } .post-copy { padding:24px; } }
</style>
