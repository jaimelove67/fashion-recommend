<script setup>
import { computed, ref } from 'vue'
import { recommendationSelectionError } from '../utils/recommendationSelection.js'

const props = defineProps({ app: { type: Object, required: true } })
const query = ref('')
const state = computed(() => props.app.state)
const form = computed(() => state.value.recommendationForm)
const locked = computed(() => form.value.lockedItemIds || [])
const excluded = computed(() => form.value.excludedItemIds || [])
const readyItems = computed(() => state.value.wardrobe.filter(item => item.recognitionStatus !== 'NEEDS_MANUAL_REVIEW'))
const items = computed(() => readyItems.value.filter(item => `${item.name} ${item.category} ${item.color}`.includes(query.value.trim())))
const error = computed(() => recommendationSelectionError(form.value, state.value.wardrobe))
const busy = computed(() => state.value.generating || state.value.wardrobeLoading)
function selection(id) { return locked.value.includes(id) ? 'locked' : excluded.value.includes(id) ? 'excluded' : '' }
function select(id, value) {
  if (busy.value) return
  if (value === 'locked' && !locked.value.includes(id) && locked.value.length >= 4) return
  if (value === 'excluded' && !excluded.value.includes(id) && excluded.value.length >= 100) return
  form.value.lockedItemIds = locked.value.filter(value => value !== id)
  form.value.excludedItemIds = excluded.value.filter(value => value !== id)
  if (value === 'locked') form.value.lockedItemIds.push(id)
  if (value === 'excluded') form.value.excludedItemIds.push(id)
}
function clear() {
  form.value.lockedItemIds = []
  form.value.excludedItemIds = []
}
</script>

<template>
  <details class="garment-picker">
    <summary>选择本次衣物 <span>指定 {{ locked.length }}/4 · 排除 {{ excluded.length }}/100</span></summary>
    <div class="garment-picker-content">
      <div class="garment-picker-tools">
        <label>搜索衣物<input v-model="query" type="search" placeholder="名称、类别或颜色" maxlength="120" @keydown.enter.prevent /></label>
        <button type="button" :disabled="busy || (!locked.length && !excluded.length)" @click="clear">清空选择</button>
      </div>
      <p v-if="error" class="garment-picker-error" role="alert">{{ error }} 可先清空选择。</p>
      <p v-if="state.wardrobeLoading" role="status">正在读取衣橱…</p>
      <p v-else-if="!readyItems.length">暂无已确认衣物，请先到我的衣橱添加或完善单品。</p>
      <p v-else-if="!items.length">没有匹配的衣物，请更换关键词。</p>
      <ul v-else>
        <li v-for="item in items" :key="item.id">
          <div><strong>{{ item.name }}</strong><small>{{ item.category }} · {{ item.color }}</small></div>
          <select :value="selection(item.id)" :aria-label="item.name + '的搭配选择'" :disabled="busy" @change="select(item.id, $event.target.value)">
            <option value="">自动搭配</option>
            <option value="locked" :disabled="locked.length >= 4 && !locked.includes(item.id)">必须使用</option>
            <option value="excluded" :disabled="excluded.length >= 100 && !excluded.includes(item.id)">本次排除</option>
          </select>
        </li>
      </ul>
    </div>
  </details>
</template>

<style scoped>
.garment-picker { margin: 10px 0; border-block: 1px solid var(--line); color: var(--ink); font-size: 12px; }
summary { padding: 12px 0; cursor: pointer; font-weight: 650; }
summary span { margin-left: 8px; color: var(--muted); font-weight: 400; }
.garment-picker-content { padding-bottom: 12px; }
p { margin: 0 0 12px; line-height: 1.6; color: var(--muted); }
.garment-picker-tools { display: flex; align-items: end; gap: 10px; margin-bottom: 12px; }
label { display: grid; gap: 6px; min-width: 0; flex: 1; }
input, select, button { min-width: 0; min-height: 40px; padding: 8px; border: 1px solid var(--line-strong); border-radius: 6px; background: var(--surface); color: var(--ink); font: inherit; }
input { width: 100%; }
button { cursor: pointer; flex-shrink: 0; }
ul { list-style: none; padding: 0; margin: 0; max-height: 220px; overflow: auto; scrollbar-color: var(--line-strong) var(--surface); }
li { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 8px 0; border-bottom: 1px solid var(--line); }
li > div { display: grid; gap: 4px; min-width: 0; overflow-wrap: anywhere; }
small { color: var(--muted); }
select { flex-shrink: 0; max-width: 48%; }
.garment-picker-error { color: var(--danger); }
:disabled { opacity: .55; cursor: not-allowed; }
button:hover:not(:disabled) { background: var(--accent-soft); }
:is(summary, input, select, button):focus-visible { outline: 2px solid var(--accent); outline-offset: 3px; }
@media (max-width: 700px) {
  .garment-picker-content { max-height: 24dvh; overflow-y: auto; }
  ul { max-height: none; overflow: visible; }
  input, select { font-size: 16px; }
}
</style>
