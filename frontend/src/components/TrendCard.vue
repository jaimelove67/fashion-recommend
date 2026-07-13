<script setup>
import { ArrowUpRight, Flame } from '@lucide/vue'

defineProps({
  item: { type: Object, required: true },
  active: { type: Boolean, default: false }
})

defineEmits(['select'])
</script>

<template>
  <article class="trend-card" :class="{ active }" @click="$emit('select', item)">
    <img class="trend-image" :src="item.imageUrl" :alt="item.title" />
    <div class="trend-copy">
      <div class="trend-meta"><span>{{ item.platform }}</span><span>更新于 {{ new Date(item.fetchedAt).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }) }}</span></div>
      <h3>{{ item.title }}</h3>
      <p>{{ item.topicTags.join(' / ') }}</p>
    </div>
    <div class="trend-score"><Flame :size="16" />{{ item.heatScore }}</div>
    <a class="source-link" :href="item.sourceUrl" target="_blank" rel="noreferrer" @click.stop><ArrowUpRight :size="17" /></a>
  </article>
</template>
