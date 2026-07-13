<script setup>
import { Palette, RefreshCw, Sparkles } from '@lucide/vue'

defineProps({
  profile: { type: Object, required: true },
  loading: { type: Boolean, default: false }
})

defineEmits(['refresh'])
</script>

<template>
  <section class="style-panel">
    <header class="panel-heading">
      <div>
        <p class="section-label">我的风格</p>
        <h2>从你的衣橱出发</h2>
      </div>
      <button class="icon-button" type="button" title="更新个人风格档案" :disabled="loading" @click="$emit('refresh')">
        <RefreshCw :size="18" :class="{ spinning: loading }" />
      </button>
    </header>

    <div class="style-tags">
      <span v-for="tag in profile.styleTags" :key="tag">{{ tag }}</span>
    </div>
    <p class="profile-reason">{{ profile.reasonSummary }}</p>

    <div class="recommend-block">
      <Sparkles :size="18" />
      <div><strong>下一套尝试</strong><p>{{ profile.itemSuggestions.join(' · ') }}</p></div>
    </div>

    <div class="palette-row">
      <div class="palette-heading"><Palette :size="16" /><span>适配颜色</span></div>
      <div class="swatches">
        <span v-for="color in profile.colorSuggestions" :key="color" :title="color" :class="`swatch ${color}`"></span>
      </div>
    </div>

    <footer><span>可以尝试</span><strong>{{ profile.tryStyleTags.join('、') }}</strong></footer>
  </section>
</template>
