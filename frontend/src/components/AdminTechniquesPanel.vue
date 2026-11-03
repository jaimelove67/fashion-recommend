<script setup>
import { computed } from 'vue'
import { BookOpen, CircleAlert, ImagePlus, LoaderCircle, RefreshCw } from '@lucide/vue'

const props = defineProps({ app: { type: Object, required: true } })
const state = computed(() => props.app.state)
const status = computed(() => state.value.adminTechniqueStatus)
const budget = computed(() => state.value.adminTechniqueBudget)
const busy = computed(() => state.value.adminTechniquesLoading || Boolean(state.value.adminTechniqueAction))
const statusLabel = computed(() => ({
  ready: '可用', collecting: '正在整理', disabled: '暂未启用', 'waiting-for-quality': '等待合格内容'
}[status.value?.status] || '等待状态更新'))
const errorLabel = computed(() => ({
  'source-unavailable': '来源暂不可用',
  'insufficient-sources': '可核对的来源不足',
  'generation-unavailable': '生成服务暂不可用',
  'quality-rejected': '内容未达到发布要求',
  'internal-failure': '整理过程未完成',
  'lease-expired': '上次整理超时'
}[status.value?.lastErrorCode] || (status.value?.lastErrorCode ? '整理未完成，请检查来源与模型配置' : '无')))
const remaining = computed(() => budget.value ? Math.max(0, budget.value.limit - budget.value.used) : null)

function dateTime(value) {
  if (!value) return '暂无记录'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '暂无记录'
  return date.toLocaleString('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit'
  })
}
</script>

<template>
  <section class="technique-admin" aria-labelledby="technique-admin-title">
    <header class="technique-admin-heading">
      <h2 id="technique-admin-title">穿搭技巧管理</h2>
      <button class="technique-button" type="button" :disabled="busy" @click="app.loadAdminTechniques()">
        <LoaderCircle v-if="state.adminTechniquesLoading" :size="15" class="spinning" aria-hidden="true" />
        <RefreshCw v-else :size="15" aria-hidden="true" />刷新状态
      </button>
    </header>

    <p v-if="state.adminTechniqueError" class="technique-message technique-error" role="alert">
      <CircleAlert :size="17" aria-hidden="true" /><span>{{ state.adminTechniqueError }}</span>
    </p>
    <p v-if="state.adminTechniqueNotice" class="technique-message" role="status">{{ state.adminTechniqueNotice }}</p>
    <p v-if="state.adminTechniquesLoading && !status && !budget" class="technique-loading" role="status">
      <LoaderCircle :size="18" class="spinning" aria-hidden="true" />正在读取技巧状态与月度额度…
    </p>

    <section class="technique-operation" aria-labelledby="weekly-operation-title">
      <header class="technique-operation-heading">
        <h3 id="weekly-operation-title"><BookOpen :size="18" aria-hidden="true" />每周技巧整理</h3>
        <span class="technique-state" :class="{ 'is-ready': status?.status === 'ready' }">{{ statusLabel }}</span>
      </header>
      <dl v-if="status" class="technique-details">
        <div><dt>最近尝试</dt><dd>{{ dateTime(status.lastAttemptAt) }}</dd></div>
        <div><dt>最近成功</dt><dd>{{ dateTime(status.lastSuccessAt) }}</dd></div>
        <div><dt>下次计划时间</dt><dd>{{ dateTime(status.nextDueAt) }}</dd></div>
        <div><dt>近 7 天尝试次数</dt><dd>{{ status.attemptsInWindow }} / {{ status.weeklyAttemptLimit }} 次</dd></div>
        <div class="technique-detail-wide"><dt>最近问题</dt><dd>{{ errorLabel }}</dd></div>
      </dl>
      <p v-else-if="!state.adminTechniquesLoading" class="technique-empty">暂未读到整理状态，请刷新状态后重试。</p>
      <footer class="technique-operation-actions">
        <button class="technique-button technique-primary" type="button"
          :disabled="busy || !status?.enabled || status?.status === 'collecting'"
          aria-describedby="technique-cost-description" @click="app.refreshAdminTechniques()">
          <LoaderCircle v-if="state.adminTechniqueAction === 'refresh'" :size="15" class="spinning" aria-hidden="true" />
          {{ state.adminTechniqueAction === 'refresh' ? '正在整理…' : '整理技巧（调用模型）' }}
        </button>
        <span v-if="status && !status.enabled" class="technique-description">未启用，请检查模型配置与部署开关。</span>
        <span v-else-if="status?.status === 'collecting'" class="technique-description">整理中，稍后刷新状态。</span>
      </footer>
    </section>

    <section class="technique-operation" aria-labelledby="comparison-operation-title">
      <header class="technique-operation-heading">
        <h3 id="comparison-operation-title"><ImagePlus :size="18" aria-hidden="true" />技巧对照图</h3>
        <span v-if="budget" class="technique-month">{{ budget.month }} 月度额度</span>
      </header>
      <dl v-if="budget" class="technique-details technique-budget">
        <div><dt>已预留额度</dt><dd>{{ budget.used }} 次</dd></div>
        <div><dt>月度上限</dt><dd>{{ budget.limit }} 次</dd></div>
        <div><dt>剩余额度</dt><dd>{{ remaining }} 次</dd></div>
      </dl>
      <p v-else-if="!state.adminTechniquesLoading" class="technique-empty">暂未读到月度额度，请刷新状态后重试。</p>
      <p id="technique-cost-description" class="technique-description">整理和补图可能调用付费模型；生图失败也计入月度额度。</p>
      <p v-if="remaining === 0" class="technique-budget-note">本月生图额度已用完，仍可补充原文图片。</p>
      <footer class="technique-operation-actions">
        <button class="technique-button technique-primary" type="button" :disabled="busy || !budget"
          aria-describedby="technique-cost-description" @click="app.generateAdminTechniqueVisuals()">
          <LoaderCircle v-if="state.adminTechniqueAction === 'visuals'" :size="15" class="spinning" aria-hidden="true" />
          {{ state.adminTechniqueAction === 'visuals' ? '正在提交…' : '补全对照图（可能调用模型）' }}
        </button>
      </footer>
    </section>
  </section>
</template>

<style scoped>
.technique-admin { min-width: 0; color: var(--ink); }
.technique-admin-heading, .technique-operation-heading { display: flex; align-items: center; justify-content: space-between; gap: 16px; flex-wrap: wrap; }
.technique-admin-heading { margin-bottom: 24px; }
.technique-admin-heading h2 { margin: 0; font: 400 30px/1.25 var(--font-display); letter-spacing: -.03em; }
.technique-operation { padding: 24px 0; border-top: 1px solid var(--line); }
.technique-operation-heading h3 { display: flex; align-items: center; gap: 9px; margin: 0; font: 400 22px/1.4 var(--font-display); letter-spacing: -.02em; }
.technique-operation-heading svg { color: var(--accent-strong); flex-shrink: 0; }
.technique-state { color: var(--ink); font-size: 12px; }
.technique-state.is-ready { color: var(--accent-strong); }
.technique-month { font-size: 12px; }
.technique-details { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 22px 32px; margin: 24px 0; font-variant-numeric: tabular-nums; }
.technique-details div { display: grid; gap: 7px; min-width: 0; }
.technique-details dt { font-size: 12px; }
.technique-details dd { margin: 0; font-size: 15px; line-height: 1.6; overflow-wrap: anywhere; }
.technique-detail-wide { grid-column: 1 / -1; }
.technique-budget { grid-template-columns: repeat(3, minmax(0, 1fr)); }
.technique-budget dd { font-size: 22px; }
.technique-description, .technique-empty, .technique-budget-note { max-width: 70ch; margin: 0; color: var(--ink); font-size: 12px; line-height: 1.85; }
.technique-empty { margin: 22px 0; }
.technique-budget-note { margin-top: 12px; color: var(--accent-strong); }
.technique-operation-actions { display: flex; align-items: center; flex-wrap: wrap; gap: 14px; margin-top: 18px; }
.technique-operation-actions > span { flex: 1 1 240px; }
.technique-button { display: inline-flex; align-items: center; justify-content: center; gap: 7px; min-height: 38px; max-width: 100%; border: 1px solid var(--line-strong); border-radius: 999px; padding: 8px 15px; background: transparent; color: var(--ink); font: 600 12px/1.5 var(--font-sans); }
.technique-button:hover:not(:disabled) { border-color: var(--accent-strong); color: var(--accent-strong); background: var(--accent-soft); }
.technique-button:focus-visible { outline: 2px solid var(--accent-strong); outline-offset: 3px; }
.technique-primary { border-color: var(--accent-strong); color: var(--surface); background: var(--accent-strong); }
.technique-primary:hover:not(:disabled) { color: var(--surface); background: var(--ink); }
.technique-button:disabled { opacity: .55; cursor: not-allowed; }
.technique-message { display: flex; align-items: flex-start; gap: 8px; padding: 12px 0; margin: 0 0 16px; font-size: 13px; line-height: 1.7; }
.technique-message svg { flex-shrink: 0; margin-top: 3px; }
.technique-error { color: var(--danger); }
.technique-loading { display: flex; gap: 9px; align-items: center; padding: 18px 0; font-size: 13px; }
@media (max-width: 620px) {
  .technique-admin-heading { align-items: flex-start; }
  .technique-admin-heading h2 { font-size: 27px; }
  .technique-details { gap: 20px 16px; }
  .technique-details dd { font-size: 13px; }
  .technique-budget { gap: 14px 10px; }
  .technique-budget dd { font-size: 19px; }
  .technique-budget dt { font-size: 11px; }
}
</style>
