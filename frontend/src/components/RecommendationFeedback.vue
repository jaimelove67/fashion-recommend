<script setup>
import { computed, reactive, ref, useId, watch } from 'vue'
import { Check, Compass, LoaderCircle, MessageSquare, Star } from '@lucide/vue'
import { FEEDBACK_REASONS } from '../data/stylePreferences.js'

const props = defineProps({ app: { type: Object, required: true }, recommendation: { type: Object, required: true } })
const editing = ref(false)
const error = ref('')
const draft = reactive({ rating: 0, feedbackType: 'rating', comment: '' })
const commentId = useId()
const busy = computed(() => props.app.state.feedbackSavingId === props.recommendation.id)
const feedback = computed(() => props.recommendation.feedback)
const shownRating = computed(() => editing.value ? draft.rating : feedback.value?.rating || 0)
const reasonLabel = computed(() => FEEDBACK_REASONS.find(reason => reason.value === feedback.value?.feedbackType)?.label)
const contextFeedback = computed(() => ['too_hot', 'too_cold', 'occasion_mismatch'].includes(draft.feedbackType))
const canExplore = computed(() => props.recommendation.items?.some(item => item.style || item.color))
const owner = () => props.app.state.authUser?.username

function hydrate(value = feedback.value) {
  draft.rating = value?.rating || 0
  draft.feedbackType = value?.feedbackType || 'rating'
  draft.comment = value?.comment || ''
}

function edit() {
  hydrate()
  editing.value = true
  error.value = ''
}

async function rate(rating) {
  if (busy.value) return
  if (editing.value) { draft.rating = rating; return }
  const account = owner()
  const saved = await props.app.rateRecommendation(props.recommendation, rating)
  if (account === owner()) error.value = saved ? '' : props.app.state.error || '评分保存失败，请重试。'
}

async function save() {
  if (busy.value) return
  if (!draft.rating) { error.value = '请先选择 1–5 星评分。'; return }
  const account = owner()
  const saved = await props.app.rateRecommendation(props.recommendation, draft.rating, {
    feedbackType: draft.feedbackType, comment: draft.comment.trim()
  })
  if (account !== owner()) return
  if (!saved) { error.value = props.app.state.error || '评价保存失败，请重试。'; return }
  editing.value = false
  hydrate(saved.feedback)
  error.value = ''
}

watch(() => props.recommendation.feedback, () => { if (!editing.value) hydrate() }, { immediate: true })
watch(() => props.recommendation.id, () => { editing.value = false; error.value = ''; hydrate() })
watch(() => props.app.state.authUser?.username, () => { editing.value = false; error.value = ''; hydrate(null) })
</script>

<template>
  <section class="feedback-panel" :aria-label="`搭配 ${recommendation.id} 的评价`">
    <div class="rating-control">
      <span>{{ shownRating ? `${editing ? '待提交' : '已评'} ${shownRating} 星` : '评价搭配方案' }}</span>
      <div aria-label="搭配评分"><button v-for="rating in 5" :key="rating" type="button" :class="{ rated: shownRating >= rating }" :disabled="busy" :aria-label="`${rating} 星评分`" @click="rate(rating)"><Star :size="18" :fill="shownRating >= rating ? 'currentColor' : 'none'" /></button></div>
      <button v-if="!editing" class="feedback-text-action" type="button" :disabled="busy" @click="edit"><MessageSquare :size="14" />{{ feedback?.comment || reasonLabel ? '编辑具体感受' : '补充具体感受' }}</button>
    </div>
    <div v-if="!editing && (reasonLabel || feedback?.comment)" class="feedback-saved"><strong v-if="reasonLabel">{{ reasonLabel }}</strong><p v-if="feedback?.comment">{{ feedback.comment }}</p></div>
    <form v-if="editing" class="feedback-form" @submit.prevent="save">
      <fieldset :disabled="busy"><legend>这次穿搭的具体感受（可选）</legend><div class="feedback-reasons"><button v-for="reason in FEEDBACK_REASONS" :key="reason.value" type="button" :aria-pressed="draft.feedbackType === reason.value" @click="draft.feedbackType = draft.feedbackType === reason.value ? 'rating' : reason.value">{{ reason.label }}</button></div></fieldset>
      <p v-if="contextFeedback" class="feedback-hint">这类反馈用于说明天气或场合适配，不据此判断你是否喜欢这个风格。</p>
      <label :for="commentId">简短意见（可选）</label><textarea :id="commentId" v-model="draft.comment" :disabled="busy" maxlength="500" rows="3" placeholder="例如：喜欢配色，但今天外出时感觉偏热。" /><span class="feedback-count">{{ draft.comment.length }}/500</span>
      <div class="feedback-form-actions"><button type="button" :disabled="busy" @click="editing = false; error = ''">取消</button><button type="submit" :disabled="busy"><LoaderCircle v-if="busy" class="spinning" :size="14" /><Check v-else :size="14" />{{ busy ? '保存中…' : '保存评价' }}</button></div>
    </form>
    <p v-if="error" class="feedback-error" role="alert">{{ error }}</p>
    <button v-if="canExplore" class="feedback-explore" type="button" :disabled="busy" @click="app.exploreRecommendationPreferences(recommendation)"><Compass :size="14" />从这套搭配探索偏好</button>
  </section>
</template>

<style scoped>
.feedback-panel { flex: 1; min-width: 0; width: 100%; }
.rating-control { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }.rating-control > span { color: var(--muted); font-size: 12px; }.rating-control > div { display: flex; gap: 4px; }.rating-control > div button { display: grid; place-items: center; width: 30px; height: 32px; padding: 0; border: 0; background: transparent; color: var(--muted); }.rating-control > div button.rated { color: var(--gold); }
.feedback-text-action, .feedback-explore { display: inline-flex; align-items: center; gap: 6px; background: transparent; border: 0; color: var(--accent); font-size: 11px; padding: 7px 0; }.feedback-explore { margin-top: 5px; }
.feedback-saved { font-size: 12px; margin-top: 9px; }.feedback-saved strong { font-weight: 500; color: var(--accent); }.feedback-saved p { color: var(--muted); margin: 4px 0; white-space: pre-wrap; overflow-wrap: anywhere; }
.feedback-form { margin-top: 14px; padding: 15px; border: 1px solid var(--line); border-radius: 10px; background: var(--surface-soft); }
.feedback-form fieldset { border: 0; margin: 0 0 12px; padding: 0; min-width: 0; }.feedback-form legend, .feedback-form > label { font-size: 12px; font-weight: 600; }.feedback-reasons { display: flex; flex-wrap: wrap; gap: 7px; margin-top: 9px; }.feedback-reasons button { padding: 6px 10px; background: var(--surface); border: 1px solid var(--line); border-radius: 16px; font-size: 11px; }.feedback-reasons button[aria-pressed=true] { color: var(--accent); border-color: var(--accent); background: var(--accent-soft); }
.feedback-form textarea { display: block; margin-top: 7px; resize: vertical; width: 100%; min-height: 80px; background: var(--surface); border: 1px solid var(--line); border-radius: 7px; padding: 9px; font-size: 12px; }.feedback-count { display: block; text-align: right; margin-top: 4px; font-size: 10px; color: var(--muted); }.feedback-hint { color: var(--muted); font-size: 11px; line-height: 1.7; }
.feedback-form-actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 9px; }.feedback-form-actions button { display: flex; align-items: center; gap: 6px; padding: 7px 12px; background: var(--surface); border: 1px solid var(--line); border-radius: 7px; font-size: 12px; }.feedback-form-actions button[type=submit] { background: var(--accent); color: white; border-color: var(--accent); }.feedback-error { color: var(--danger); font-size: 12px; }
@media (max-width: 600px) { .feedback-panel { flex-basis: 100%; }.feedback-text-action { margin-right: auto; }.feedback-form { padding: 12px; } }
</style>
