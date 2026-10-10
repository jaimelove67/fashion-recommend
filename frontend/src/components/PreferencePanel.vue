<script setup>
import { computed, nextTick, onBeforeUnmount, onDeactivated, reactive, ref, watch } from 'vue'
import { ArrowRight, Check, Compass, LoaderCircle, Palette, Pencil, X } from '@lucide/vue'
import StyleReference from './StyleReference.vue'
import { FEELING_OPTIONS, PREFERENCE_FIELDS, STYLE_REFERENCES, preferenceList, suggestPreferences } from '../data/stylePreferences.js'

const props = defineProps({ app: { type: Object, required: true } })
const dialog = ref(null)
const opened = ref(false)
const step = ref('direct')
const error = ref('')
const source = ref('')
const feelings = ref([])
const choices = reactive({})
const custom = reactive({})
const draft = reactive({ displayName: '', stylePreferences: [], colorPreferences: [], occasions: [], avoidPreferences: [] })
const profile = computed(() => props.app.state.profile || {})
const busy = computed(() => props.app.state.profileSaving || props.app.state.profileAnalyzing || props.app.state.profilePhotoUploading)
const hasPreferences = computed(() => PREFERENCE_FIELDS.some(field => profile.value[field.key]?.length))
const status = computed(() => !hasPreferences.value ? '尚未建立 · 可跳过' : profile.value.preferencesConfirmed ? '已确认' : '已记录 · 待确认')
const owner = () => props.app.state.authUser?.username
let bodyOverflow = ''

function hydrate() {
  draft.displayName = profile.value.displayName || owner() || '你'
  for (const field of PREFERENCE_FIELDS) {
    draft[field.key] = [...(profile.value[field.key] || [])]
    custom[field.key] = ''
  }
  for (const key of Object.keys(choices)) delete choices[key]
  feelings.value = []
  source.value = ''
  error.value = ''
}

async function open(mode, inspiration = null) {
  if (busy.value || !owner() || !props.app.state.profile) return
  const account = owner()
  hydrate()
  step.value = mode
  if (inspiration) {
    draft.stylePreferences = preferenceList([...draft.stylePreferences, ...inspiration.stylePreferences])
    draft.colorPreferences = preferenceList([...draft.colorPreferences, ...inspiration.colorPreferences])
    source.value = `参考搭配“${inspiration.title}”的风格和颜色整理，可调整后确认。`
  }
  await nextTick()
  if (account !== owner() || !dialog.value) return
  if (!dialog.value.open) dialog.value.showModal()
  bodyOverflow = document.body.style.overflow
  document.body.style.overflow = 'hidden'
  opened.value = true
  await nextTick()
  dialog.value.querySelector('h3')?.focus()
}

function close(force = false) {
  if (busy.value && !force) return
  dialog.value?.close()
  onClose()
}

function onClose() {
  if (opened.value) document.body.style.overflow = bodyOverflow
  opened.value = false
  error.value = ''
}

function toggle(key, value) {
  if (busy.value) return
  const list = key === 'feelings' ? feelings.value : draft[key]
  const index = list.indexOf(value)
  if (index >= 0) list.splice(index, 1)
  else list.push(value)
}

function options(field) { return preferenceList([...field.options, ...draft[field.key]]) }

function clearChoices() {
  for (const field of PREFERENCE_FIELDS) {
    draft[field.key] = []
    custom[field.key] = ''
  }
  feelings.value = []
  for (const key of Object.keys(choices)) delete choices[key]
  source.value = ''
}

function review() {
  const explicitColors = preferenceList([...draft.colorPreferences, ...preferenceList(custom.colorPreferences)])
  const suggestion = suggestPreferences(choices, feelings.value, explicitColors)
  draft.stylePreferences = suggestion.styles
  draft.colorPreferences = suggestion.colors
  source.value = suggestion.references.length
    ? `根据你选择的“${suggestion.references.join('、')}”整理。风格与配色都是候选方向，可在下方调整后确认。`
    : feelings.value.length ? '根据你选择的感觉整理，可调整后确认。' : '暂未确定风格，可以只保存已明确的颜色、场合或避开条件。'
  step.value = 'review'
  error.value = ''
}

async function save() {
  if (busy.value) return
  const values = { displayName: draft.displayName.trim() }
  for (const field of PREFERENCE_FIELDS) {
    values[field.key] = preferenceList([...draft[field.key], ...preferenceList(custom[field.key])])
    if (values[field.key].length > 10 || values[field.key].some(value => value.length > 40)) {
      error.value = `${field.label}最多 10 项，每项不超过 40 字，请精简后保存。`
      return
    }
  }
  const account = owner()
  const result = await props.app.savePreferences(values)
  if (account !== owner()) return
  if (!result) {
    error.value = props.app.state.error || '偏好保存失败，请重试。'
    return
  }
  close(true)
}

function skip() {
  if (busy.value) return
  close(true)
  props.app.selectView('recommend')
}

watch(() => props.app.state.preferenceInspiration, async (inspiration) => {
  if (!inspiration) return
  await open('review', inspiration)
  props.app.state.preferenceInspiration = null
}, { immediate: true })
watch(() => props.app.state.authUser?.username, () => { close(true); hydrate() })
onDeactivated(() => close(true))
onBeforeUnmount(() => { if (opened.value) document.body.style.overflow = bodyOverflow })
</script>

<template>
  <section class="preference-panel" aria-labelledby="preference-panel-title">
    <header>
      <div><p class="preference-kicker"><Palette :size="15" />由你决定</p><h2 id="preference-panel-title">我的穿搭偏好</h2></div>
      <span class="preference-status">{{ status }}</span>
    </header>
    <p class="preference-description">可以直接表达喜欢的方向，也可以先看看搭配参考。每一项都允许暂不确定。</p>
    <dl class="preference-summary">
      <div v-for="field in PREFERENCE_FIELDS" :key="field.key"><dt>{{ field.label }}</dt><dd>{{ profile[field.key]?.join('、') || '暂未确定' }}</dd></div>
    </dl>
    <p v-if="hasPreferences && !profile.preferencesConfirmed" class="preference-legacy">已有记录已保留，请核对是否符合自己的喜好；确认后将作为明确偏好使用。</p>
    <div class="preference-actions">
      <button type="button" class="preference-primary" :disabled="busy || !app.state.profile" @click="open('direct')"><Pencil :size="15" />我有喜欢的方向</button>
      <button type="button" class="preference-secondary" :disabled="busy || !app.state.profile" @click="open('explore')"><Compass :size="16" />帮我探索</button>
      <button type="button" class="preference-link" @click="app.selectView('recommend')">暂不确定，先看推荐 <ArrowRight :size="14" /></button>
    </div>
    <p class="preference-footnote">照片分析与衣橱构成提供搭配参考。这里保存的是你确认的喜好，可随时调整。</p>
  </section>

  <dialog ref="dialog" class="preference-dialog" aria-labelledby="preference-dialog-title" @close="onClose" @cancel="busy && $event.preventDefault()" @click.self="close()">
    <div class="preference-dialog-inner">
      <header class="preference-dialog-header">
        <div><p class="preference-kicker">一步步找到喜欢的方向</p><h3 id="preference-dialog-title" tabindex="-1">{{ step === 'explore' ? '探索穿搭偏好' : step === 'review' ? '确认候选方向' : '编辑穿搭偏好' }}</h3></div>
        <button type="button" class="preference-close" aria-label="关闭偏好编辑" :disabled="busy" @click="close()"><X :size="18" /></button>
      </header>
      <div class="preference-mode-switch" role="group" aria-label="偏好设置方式">
        <button type="button" :aria-pressed="step === 'direct'" :disabled="busy" @click="step = 'direct'">直接选择</button>
        <button type="button" :aria-pressed="step === 'explore'" :disabled="busy" @click="step = 'explore'">从参考搭配探索</button>
      </div>
      <p v-if="step === 'review'" class="preference-candidate" role="status">{{ source }}<span>以下选择尚未保存，确认后才成为你的偏好。</span></p>
      <form @submit.prevent="step === 'explore' ? review() : save()">
        <fieldset v-if="step === 'explore'" class="preference-field" :disabled="busy">
          <legend>希望呈现的感觉</legend><p>用直观感受开始，不确定也可以跳过。</p>
          <div class="preference-chips"><button v-for="feeling in FEELING_OPTIONS" :key="feeling.label" type="button" :aria-pressed="feelings.includes(feeling.label)" @click="toggle('feelings', feeling.label)">{{ feeling.label }}</button></div>
        </fieldset>
        <label v-else class="preference-name">显示名称<input v-model="draft.displayName" maxlength="80" placeholder="希望如何称呼你" /></label>
        <template v-for="field in PREFERENCE_FIELDS" :key="field.key">
          <fieldset v-if="step !== 'explore' || field.key !== 'stylePreferences'" class="preference-field" :disabled="busy">
            <legend>{{ field.label }}</legend><p>{{ field.hint }}</p>
            <div class="preference-chips"><button v-for="option in options(field)" :key="option" type="button" :aria-pressed="draft[field.key].includes(option)" @click="toggle(field.key, option)">{{ option }}</button></div>
            <label class="preference-custom">其他{{ field.label }}<input v-model="custom[field.key]" :aria-label="`其他${field.label}`" maxlength="410" placeholder="可选，多个关键词用顿号分隔" /></label>
          </fieldset>
        </template>
        <section v-if="step === 'explore'" class="preference-references" aria-labelledby="preference-reference-title">
          <h4 id="preference-reference-title">哪种搭配让你更想尝试？</h4><p>这是风格参考示意，不代表你已拥有这些衣物。可以选多个，也可以全部跳过。</p>
          <div class="preference-reference-grid">
            <article v-for="reference in STYLE_REFERENCES" :key="reference.id" class="preference-reference-card" :class="{ selected: ['like', 'try'].includes(choices[reference.id]) }">
              <StyleReference :reference="reference" /><h5>{{ reference.title }}</h5><p>{{ reference.description }}</p>
              <div role="group" :aria-label="`评价${reference.title}`"><button v-for="choice in [{ value: 'like', label: '喜欢' }, { value: 'try', label: '愿意尝试' }, { value: 'no', label: '暂不喜欢' }]" :key="choice.value" type="button" :aria-pressed="choices[reference.id] === choice.value" @click="choices[reference.id] = choices[reference.id] === choice.value ? null : choice.value">{{ choice.label }}</button></div>
            </article>
          </div>
        </section>
        <p v-if="error" class="preference-error" role="alert">{{ error }}</p>
        <footer class="preference-dialog-footer">
          <button type="button" class="preference-link" :disabled="busy" @click="clearChoices">全部暂不确定</button>
          <button type="button" class="preference-secondary" :disabled="busy" @click="skip">跳过，先看推荐</button>
          <button class="preference-primary" type="submit" :disabled="busy"><LoaderCircle v-if="busy" class="spinning" :size="15" /><Check v-else :size="15" />{{ busy ? '保存中…' : step === 'explore' ? '查看候选方向' : '确认保存我的偏好' }}</button>
        </footer>
      </form>
    </div>
  </dialog>
</template>

<style scoped>
.preference-panel { padding: 24px 28px; border: 1px solid var(--line); border-radius: 14px; background: var(--surface); }
.preference-panel > header, .preference-dialog-header { display: flex; justify-content: space-between; align-items: center; gap: 16px; }
.preference-kicker { display: flex; gap: 7px; align-items: center; color: var(--accent); font-size: 11px; font-weight: 700; margin: 0 0 5px; }
h2, h3 { margin: 0; font-size: 22px; } h3 { font-size: 21px; }
.preference-status { color: var(--accent); font-size: 12px; padding: 5px 10px; background: var(--accent-soft); border-radius: 20px; flex-shrink: 0; }
.preference-description, .preference-footnote, .preference-legacy { color: var(--muted); line-height: 1.7; }
.preference-summary { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 16px; margin: 20px 0; }
.preference-summary dt { color: var(--muted); font-size: 11px; margin-bottom: 6px; }
.preference-summary dd { margin: 0; font-size: 13px; font-weight: 600; overflow-wrap: anywhere; }
.preference-actions, .preference-dialog-footer { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; }
.preference-primary, .preference-secondary, .preference-link { display: inline-flex; gap: 7px; justify-content: center; align-items: center; min-height: 40px; border-radius: 8px; padding: 9px 14px; font-size: 12px; font-weight: 600; }
.preference-primary { background: var(--accent); color: white; border: 1px solid var(--accent); }
.preference-secondary { background: var(--surface); color: var(--accent); border: 1px solid var(--line-strong); }
.preference-link { border: 0; background: transparent; color: var(--muted); padding: 8px 3px; }
.preference-footnote { font-size: 11px; margin-bottom: 0; }.preference-legacy { font-size: 12px; }
.preference-dialog { width: min(850px, calc(100vw - 32px)); max-height: calc(100dvh - 40px); padding: 0; border: 1px solid var(--line); border-radius: 16px; color: var(--ink); background: var(--surface); box-shadow: var(--shadow-pop); }
.preference-dialog::backdrop { background: rgba(22, 39, 32, .48); }
.preference-dialog-inner { padding: 26px; }
.preference-close { display: grid; place-items: center; width: 34px; height: 34px; flex-shrink: 0; background: transparent; border: 1px solid var(--line); border-radius: 50%; }
.preference-mode-switch { display: flex; gap: 8px; margin: 22px 0; }
.preference-mode-switch button { border: 1px solid var(--line); border-radius: 8px; padding: 9px 12px; background: transparent; font-size: 12px; }
.preference-mode-switch button[aria-pressed=true] { color: var(--accent); background: var(--accent-soft); border-color: var(--accent); }
.preference-field { border: 0; padding: 0; margin: 22px 0; min-width: 0; }
.preference-field legend, .preference-name { font-size: 13px; font-weight: 700; }.preference-field > p { color: var(--muted); font-size: 12px; margin: 5px 0 10px; }
.preference-chips { display: flex; gap: 8px; flex-wrap: wrap; }
.preference-chips button { border: 1px solid var(--line); background: transparent; border-radius: 20px; padding: 7px 12px; font-size: 12px; overflow-wrap: anywhere; max-width: 100%; }
.preference-chips button[aria-pressed=true] { border-color: var(--accent); background: var(--accent-soft); color: var(--accent); }
.preference-name, .preference-custom { display: grid; gap: 7px; }.preference-custom { color: var(--muted); font-size: 11px; margin-top: 10px; }
.preference-name input, .preference-custom input { width: 100%; background: white; border: 1px solid var(--line); border-radius: 7px; padding: 9px 11px; font-size: 13px; font-weight: 400; }
.preference-candidate { padding: 14px; background: var(--accent-soft); color: var(--accent-strong); border-radius: 9px; font-size: 13px; line-height: 1.7; }.preference-candidate span { display: block; font-size: 11px; margin-top: 4px; }
.preference-references h4 { margin: 0; font-size: 16px; }.preference-references > p { color: var(--muted); font-size: 12px; line-height: 1.7; }
.preference-reference-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; }
.preference-reference-card { min-width: 0; padding: 10px; border: 1px solid var(--line); border-radius: 12px; background: #fffdf8; }.preference-reference-card.selected { border-color: var(--accent); }
.preference-reference-card h5 { font-size: 14px; margin: 12px 3px 4px; }.preference-reference-card > p { margin: 0 3px 12px; font-size: 12px; line-height: 1.6; color: var(--muted); }
.preference-reference-card > div { display: flex; gap: 5px; flex-wrap: wrap; }.preference-reference-card button { flex: 1; min-height: 36px; white-space: nowrap; font-size: 11px; padding: 6px; border: 1px solid var(--line); border-radius: 6px; background: transparent; }.preference-reference-card button[aria-pressed=true] { color: var(--accent); border-color: var(--accent); background: var(--accent-soft); }
.preference-dialog-footer { justify-content: flex-end; border-top: 1px solid var(--line); padding-top: 18px; margin-top: 24px; }.preference-dialog-footer > .preference-link { margin-right: auto; }
.preference-error { color: var(--danger); font-size: 13px; }
@media (max-width: 600px) { .preference-panel { padding: 20px; }.preference-summary { grid-template-columns: repeat(2, minmax(0, 1fr)); }.preference-panel > header { align-items: flex-start; }.preference-status { font-size: 10px; }h2 { font-size: 20px; }.preference-dialog { width: calc(100vw - 20px); max-height: calc(100dvh - 20px); }.preference-dialog-inner { padding: 18px; }.preference-reference-grid { grid-template-columns: 1fr; }.preference-dialog-footer > .preference-primary { width: 100%; }.preference-actions { align-items: stretch; }.preference-actions > button { flex: 1 1 auto; } }
</style>
