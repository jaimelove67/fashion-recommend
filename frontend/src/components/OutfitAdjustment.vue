<script setup>
import { computed, ref, watch } from 'vue'

const props = defineProps({ app: { type: Object, required: true }, recommendation: { type: Object, required: true } })
const emit = defineEmits(['generated'])
const locked = ref([])
const error = ref('')
const items = computed(() => props.recommendation.items || [])
watch(() => props.recommendation.id, () => { locked.value = []; error.value = '' })

function toggle(id) {
  locked.value = locked.value.includes(id) ? locked.value.filter(value => value !== id) : [...locked.value, id]
}

async function regenerate(replaced = null) {
  if (props.app.state.generating) return
  error.value = ''
  const source = props.recommendation
  const result = await props.app.generateRecommendation({
    occasion: source.occasion,
    city: source.city,
    trendId: null,
    styleHint: (replaced ? `保留其他单品，仅替换${replaced.name}` : '在保留指定单品的基础上调整搭配').slice(0, 120),
    lockedItemIds: replaced ? items.value.filter(item => item.id !== replaced.id).map(item => item.id) : [...locked.value],
    excludedItemIds: replaced ? [replaced.id] : [],
    manualTemperatureC: source.weather?.source === 'user-provided' ? source.temperatureC : null
  })
  if (result) emit('generated', result)
  else error.value = props.app.state.error || '未能生成新方案，原方案已保留。'
}
</script>

<template>
  <section class="outfit-adjustment" aria-label="调整当前搭配">
    <strong>调整当前搭配</strong>
    <p>换一件会保留其余单品；也可先选择要保留的衣物，再重新搭配。</p>
    <ul>
      <li v-for="item in items" :key="item.id">
        <span>{{ item.name }}</span>
        <button type="button" :aria-label="`保留${item.name}`" :aria-pressed="locked.includes(item.id)" :disabled="app.state.generating" @click="toggle(item.id)">{{ locked.includes(item.id) ? '已保留' : '保留' }}</button>
        <button type="button" :aria-label="`替换${item.name}`" :disabled="app.state.generating || locked.includes(item.id)" @click="regenerate(item)">换一件</button>
      </li>
    </ul>
    <button type="button" :disabled="app.state.generating || locked.length === items.length" @click="regenerate()">{{ app.state.generating ? '正在调整…' : '按保留条件重新搭配' }}</button>
    <p v-if="error" role="alert">{{ error }}</p>
  </section>
</template>

<style scoped>
.outfit-adjustment { margin: 16px 0; padding: 16px; border: 1px solid var(--line, #d8d5cc); border-radius: 12px; font-size: 13px; }
.outfit-adjustment p { margin: 8px 0; line-height: 1.6; }
ul { padding: 0; list-style: none; }
li { display: flex; align-items: center; gap: 8px; margin: 8px 0; }
li span { flex: 1; min-width: 0; overflow-wrap: anywhere; }
button { flex-shrink: 0; border: 1px solid #aaa99a; border-radius: 6px; padding: 7px 10px; color: inherit; background: transparent; cursor: pointer; }
button[aria-pressed="true"] { background: #e4e9dd; }
button:disabled { opacity: .5; cursor: default; }
button:focus-visible { outline: 2px solid #566b43; outline-offset: 2px; }
[role="alert"] { color: #963b2a; }
</style>
