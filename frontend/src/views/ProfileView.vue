<script setup>
import { computed, nextTick, onBeforeUnmount, onDeactivated, reactive, ref, watch } from 'vue'
import {
  ArrowRight, BellRing, Camera, Check, ChevronRight, CircleHelp, Info, LoaderCircle,
  Palette, Pencil, RefreshCw, Ruler, ScanFace, Shirt, Sparkles, UserRound, Weight, X
} from '@lucide/vue'
import PreferencePanel from '../components/PreferencePanel.vue'

const props = defineProps({ app: { type: Object, required: true } })
const DEFAULT_PORTRAIT = '/assets/profile-portrait.png'
const editOpen = ref(false)
const setupStep = ref(1)
const setupError = ref('')
const analysisError = ref('')
const photoInput = ref(null)
const editingField = ref('')
const editingValue = ref('')
const selectedPhoto = ref(null)
const allowAiAnalysis = ref(false)
const usePersonalModel = ref(false)
const dismissedAnalysisNotice = ref('')
let previewUrl = ''
let previousBodyOverflow = ''
const draft = reactive({ height: '', weight: '', photoUrl: '', photoName: '' })
const profile = computed(() => props.app.state.profile || {})
const profileForm = computed(() => props.app.state.profileForm || {})
const busy = computed(() => props.app.state.profileSaving || props.app.state.profilePhotoUploading || props.app.state.profileAnalyzing)
const owner = () => props.app.state.authUser?.username
const basicsReady = computed(() => Boolean(profile.value.heightCm && profile.value.weightKg))
const photoReady = computed(() => Boolean(selectedPhoto.value || profile.value.photoUrl))
const analysisReady = computed(() => Boolean(profile.value.analysis && !profile.value.stale))
const analysisNoticeKey = computed(() => analysisReady.value
  ? owner() + ':' + profile.value.analysisSource + ':' + profile.value.analysisUpdatedAt : '')
const analysisNoticeVisible = computed(() => Boolean(analysisNoticeKey.value && dismissedAnalysisNotice.value !== analysisNoticeKey.value))
const analysisPhase = computed(() => props.app.state.profileAnalyzing || props.app.state.profilePhotoUploading
  ? 'analyzing' : analysisReady.value ? 'complete' : 'idle')
const analysisStatusLabel = computed(() => analysisPhase.value === 'analyzing' ? '分析中'
  : profile.value.stale ? '资料已变更 · 待重新确认'
    : analysisReady.value ? profile.value.analysisSource === 'MANUAL' ? '已手动确认' : '分析完成' : '待完善')
const analysisStatusClass = computed(() => analysisPhase.value === 'analyzing' ? 'analyzing' : analysisReady.value ? 'complete' : 'pending')
const analysisSourceLabel = computed(() => profile.value.stale ? '资料已变更，原分析暂不参与推荐；请重新分析或手动确认。'
  : profile.value.analysisSource === 'MODEL' ? 'AI 视觉分析 · ' + (profile.value.analysisModelName || '已配置模型')
    : profile.value.analysisSource === 'MANUAL' ? '手动填写或修正 · 后续推荐将参考这些资料' : '暂无分析结果，可选择 AI 分析或手动填写。')
const profileGender = computed(() => {
  const value = String(profile.value.gender || '').trim().toUpperCase()
  return value === 'MALE' || value === 'FEMALE' ? value : ''
})
const profileGenderLabel = computed(() => ({ MALE: '男', FEMALE: '女' }[profileGender.value] || '待补充'))
const displayName = computed(() => profile.value.displayName || owner() || '你')
const styleTags = computed(() => unique([...(profile.value.styleTags || []), ...(profile.value.stylePreferences || [])]).slice(0, 4))
const styleKeywords = computed(() => styleTags.value)
const recommendedPalette = computed(() => unique(profile.value.colorSuggestions || []).slice(0, 6)
  .map((name, index) => ({ name, color: colorFor(name, index) })))
const summary = computed(() => profile.value.stale ? '基础资料已更新，请重新分析或手动确认形象特征，以供后续推荐参考。'
  : profile.value.reasonSummary || '保存基础资料后，可上传照片进行 AI 分析，或手动填写形象特征与穿搭建议。')
const analysisRows = computed(() => [
  { key: 'faceShape', label: '脸型', icon: ScanFace },
  { key: 'facialLine', label: '面部线条', icon: UserRound },
  { key: 'visualContrast', label: '视觉对比', icon: Palette },
  { key: 'hairFeatures', label: '发型特征', icon: Sparkles },
  { key: 'bodyProportions', label: '身体比例', icon: Ruler },
  { key: 'fitSuggestions', label: '剪裁建议', icon: Shirt, list: true },
  { key: 'colorSuggestions', label: '颜色建议', icon: Palette, list: true }
].map((row) => {
  const value = profile.value.analysis?.[row.key]
  return { ...row, value: row.list ? (value || []).join('、') : value || '',
    detail: row.list ? '多个关键词请用顿号或逗号分隔，保存后将用于推荐参考。' : '请填写已确认的特征；无法确认时可留空。' }
}))
const flowSteps = computed(() => [
  { key: 'basics', label: '基础资料', note: basicsReady.value ? '身高、体重已保存 · 模特性别：' + profileGenderLabel.value : '基础资料待保存', status: basicsReady.value ? 'done' : 'current' },
  { key: 'photo', label: '上传照片', note: profile.value.photoUrl ? '个人照片已保存' : 'AI 分析需上传照片；手动填写可跳过', status: profile.value.photoUrl ? 'done' : 'current' },
  { key: 'analysis', label: '综合分析', note: analysisSourceLabel.value, status: analysisReady.value ? 'done' : 'current' }
])
const outfitFormulas = [
  { label: '日常搭配示例', note: '日常休闲 · 舒适自然', image: '/assets/look-everyday-flatlay.png' },
  { label: '通勤搭配示例', note: '简洁稳重 · 适合工作日', image: '/assets/look-commute-flatlay.png' }
]

function unique(values) { return [...new Set(values.filter((value) => typeof value === 'string' && value.trim()).map((value) => value.trim()))] }

function colorFor(name, index = 0) {
  const map = {
    '深绿': '#276e63', '橄榄绿': '#70765d', '藏青': '#2e435e', '海军蓝': '#2e435e',
    '雾蓝': '#9db8c8', '浅蓝': '#a9c2d3', '深蓝': '#243d62', '燕麦色': '#cdbca4', '卡其': '#b8a485',
    '米白': '#eee8dc', '暖白': '#f2eee6', '浅灰': '#c7cbc9', '石墨灰': '#626968',
    '炭灰': '#424746', '黑色': '#1c201f', '棕色': '#8d755f'
  }
  return map[name] || props.app.colorFor?.(name) || ['#276e63', '#2e435e', '#cdbca4', '#eee8dc', '#c7cbc9', '#424746'][index % 6]
}

function formatDate(value) {
  if (!value) return '暂无日期'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '暂无日期' : new Intl.DateTimeFormat('zh-CN', { month: '2-digit', day: '2-digit' }).format(date)
}

function releasePreview() {
  if (previewUrl) URL.revokeObjectURL(previewUrl)
  previewUrl = ''
}

function hydrateFromProfile(value) {
  draft.height = value?.heightCm == null ? '' : String(value.heightCm)
  draft.weight = value?.weightKg == null ? '' : String(value.weightKg)
  if (!selectedPhoto.value) {
    draft.photoUrl = value?.photoUrl || ''
    draft.photoName = value?.photoUrl ? '已保存个人照片' : ''
  }
}

function validateBasics() {
  const height = Number(draft.height)
  const weight = Number(draft.weight)
  if (!draft.height || !Number.isFinite(height) || height < 80 || height > 250) {
    setupError.value = '身高请输入 80–250 cm。'
    return false
  }
  if (!draft.weight || !Number.isFinite(weight) || weight < 20 || weight > 300) {
    setupError.value = '体重请输入 20–300 kg。'
    return false
  }
  setupError.value = ''
  return true
}

function openSetup() {
  if (busy.value) return
  hydrateFromProfile(profile.value)
  setupError.value = ''
  allowAiAnalysis.value = false
  usePersonalModel.value = Boolean(profile.value.usePersonalPhotoForOutfit)
  setupStep.value = 1
  editOpen.value = true
}

function openModelSetup() {
  openSetup()
  if (basicsReady.value && profileGender.value) setupStep.value = 2
}

function closeSetup() {
  if (!busy.value) {
    editOpen.value = false
    setupError.value = ''
  }
}

async function continueToPhoto() {
  if (busy.value || !validateBasics()) return
  if (!profileForm.value.gender) {
    setupError.value = '请选择模特性别，该设置用于生成穿搭效果图。'
    return
  }
  const account = owner()
  const saved = await props.app.saveProfile({ heightCm: Number(draft.height), weightKg: Number(draft.weight) })
  if (account !== owner()) return
  if (!saved) {
    setupError.value = props.app.state.error || '基础资料保存失败，请重试。'
    return
  }
  setupStep.value = 2
}

function triggerPhotoUpload() { if (!busy.value) photoInput.value?.click() }

function handlePhotoChange(event) {
  const file = event.target.files?.[0]
  if (!file || busy.value) return
  if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) {
    setupError.value = '请选择 JPG、PNG 或 WEBP 照片。'
    return
  }
  if (file.size > 10 * 1024 * 1024) {
    setupError.value = '图片不能超过 10 MB。'
    return
  }
  releasePreview()
  selectedPhoto.value = file
  previewUrl = URL.createObjectURL(file)
  draft.photoUrl = previewUrl
  draft.photoName = file.name
  allowAiAnalysis.value = false
  usePersonalModel.value = false
  setupError.value = ''
}

async function saveSelectedPhoto() {
  if (!selectedPhoto.value) return profile.value
  const saved = await props.app.uploadProfilePhoto(selectedPhoto.value)
  if (!saved) return null
  selectedPhoto.value = null
  releasePreview()
  hydrateFromProfile(saved)
  return saved
}

async function saveModelPreference() {
  if (Boolean(profile.value.usePersonalPhotoForOutfit) === usePersonalModel.value) return profile.value
  const saved = await props.app.saveOutfitModelPreference(usePersonalModel.value)
  if (!saved) setupError.value = props.app.state.error || '人物形象设置保存失败，请重试。'
  return saved
}

async function saveModelSettings() {
  if (busy.value) return
  const account = owner()
  if (!await saveSelectedPhoto()) {
    if (account === owner()) setupError.value = props.app.state.error || '照片保存失败，请重试。'
    return
  }
  if (account !== owner()) return
  const saved = await saveModelPreference()
  if (account === owner() && saved) editOpen.value = false
}

async function startAnalysis() {
  if (busy.value || !validateBasics()) return
  if (!photoReady.value) {
    setupError.value = '请先选择个人照片，再开始分析。'
    return
  }
  if (!allowAiAnalysis.value) {
    setupError.value = '请授权本次 AI 分析，或选择手动填写。'
    return
  }
  const account = owner()
  setupStep.value = 3
  const uploaded = await saveSelectedPhoto()
  if (account !== owner()) return
  if (!uploaded) {
    setupStep.value = 2
    setupError.value = props.app.state.error || '照片保存失败，请重试。'
    return
  }
  if (!await saveModelPreference()) {
    if (account === owner()) setupStep.value = 2
    return
  }
  if (account !== owner()) return
  const result = await props.app.analyzeProfile(true)
  if (account !== owner()) return
  if (!result) {
    setupStep.value = 2
    setupError.value = props.app.state.error || '分析失败，请重试或手动填写。'
    return
  }
  editOpen.value = false
  analysisError.value = ''
}

async function finishManualSetup() {
  if (busy.value) return
  const account = owner()
  if (!await saveSelectedPhoto()) {
    setupError.value = props.app.state.error || '照片保存失败，请重试。'
    return
  }
  if (account !== owner()) return
  if (!await saveModelPreference() || account !== owner()) return
  editOpen.value = false
  beginEdit(analysisRows.value[0])
  await nextTick()
  document.querySelector('.analysis-inline-input')?.focus()
}

function beginEdit(row) {
  if (busy.value) return
  editingField.value = row.key
  editingValue.value = row.value
  analysisError.value = ''
}

async function saveEditedField() {
  if (!editingField.value || busy.value) return
  const row = analysisRows.value.find((item) => item.key === editingField.value)
  const value = editingValue.value.trim()
  const analysis = profile.value.stale ? {} : { ...(profile.value.analysis || {}) }
  const correctedObservation = !row.list && value !== row.value && profile.value.analysisSource === 'MODEL'
  if (correctedObservation) {
    for (const key of ['fitSuggestions', 'styleTags', 'tryStyleTags', 'colorSuggestions', 'itemSuggestions']) analysis[key] = []
  }
  analysis[row.key] = row.list ? value.split(/[,，、]/).map((item) => item.trim()).filter(Boolean) : value
  analysis.reasonSummary = correctedObservation
    ? '形象特征已更新。可重新分析或手动完善穿搭建议，后续推荐将参考最新确认的资料与偏好。'
    : '形象特征与穿搭建议已保存，后续推荐将参考这些资料。'
  const account = owner()
  const saved = await props.app.saveProfileAnalysis(analysis)
  if (account !== owner()) return
  if (!saved) {
    analysisError.value = props.app.state.error || '分析结果保存失败，请重试。'
    return
  }
  editingField.value = ''
  editingValue.value = ''
}

function goRecommendations() { props.app.selectView('recommend') }

watch(() => props.app.state.authUser?.username, () => {
  releasePreview()
  selectedPhoto.value = null
  editingField.value = ''
  editingValue.value = ''
  editOpen.value = false
  analysisError.value = ''
  setupError.value = ''
  allowAiAnalysis.value = false
  usePersonalModel.value = false
  hydrateFromProfile(null)
})
watch(() => props.app.state.profile, hydrateFromProfile, { immediate: true })
watch(editOpen, (open) => {
  if (typeof document === 'undefined') return
  if (open) {
    previousBodyOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
  } else document.body.style.overflow = previousBodyOverflow
})
onBeforeUnmount(() => {
  releasePreview()
  if (typeof document !== 'undefined') document.body.style.overflow = previousBodyOverflow
})
onDeactivated(() => {
  editOpen.value = false
  if (typeof document !== 'undefined') document.body.style.overflow = previousBodyOverflow
})
</script>

<template>
  <section class="profile-view">
    <div v-if="app.state.profileLoading && !app.state.profile" class="profile-loading state-panel" role="status" aria-live="polite">
      <LoaderCircle class="spinning" :size="28" />
      <strong>正在加载个人形象档案…</strong>
      <span>正在获取基础资料、个人照片与分析结果。</span>
    </div>

    <template v-else>
      <header class="profile-page-header">
        <div>
          <div class="profile-heading-line">
            <h1>个人形象档案</h1>
            <span class="analysis-status" :class="analysisStatusClass" role="status" aria-live="polite">
              <LoaderCircle v-if="analysisPhase === 'analyzing'" class="spinning" :size="14" />
              <Check v-else :size="14" />
              {{ analysisStatusLabel }}
            </span>
          </div>
      <p>管理基础资料与形象分析结果，为穿搭推荐提供已确认的形象特征及个人偏好。</p>
        </div>
        <button class="profile-primary profile-top-action" type="button" @click="goRecommendations">
          查看穿搭推荐 <ArrowRight :size="17" />
        </button>
      </header>

      <PreferencePanel :app="app" />

      <nav class="profile-progress" aria-label="形象分析进度">
        <template v-for="(step, index) in flowSteps" :key="step.key">
          <div class="progress-step" :class="step.status">
            <span class="progress-mark">
              <LoaderCircle v-if="step.key === 'analysis' && analysisPhase === 'analyzing'" class="spinning" :size="16" />
              <Check v-else-if="step.status === 'done'" :size="16" />
              <span v-else>{{ index + 1 }}</span>
            </span>
            <div>
              <strong>{{ step.label }}</strong>
              <span>{{ step.note }}</span>
            </div>
          </div>
          <ChevronRight v-if="index < flowSteps.length - 1" class="progress-arrow" :size="17" aria-hidden="true" />
        </template>
      </nav>

      <section class="profile-hero-board" aria-labelledby="profile-hero-title">
        <div class="profile-intro">
          <p class="profile-eyebrow"><Sparkles :size="15" />形象档案</p>
          <h2 id="profile-hero-title">个人穿搭参考</h2>
          <p class="profile-intro-copy">{{ summary }}</p>
          <div class="profile-action-row">
            <button class="profile-primary" type="button" @click="openSetup">
              编辑形象资料 <Pencil :size="15" />
            </button>
            <button class="profile-secondary" type="button" :disabled="busy" @click="openSetup">
              <RefreshCw :size="15" />更新基础资料
            </button>
          </div>
          <div class="profile-privacy-note">
            <Info :size="16" aria-hidden="true" />
            <span>个人照片保存在当前账户下。授权本次 AI 分析后，照片及相关资料将发送至 AI 分析服务。</span>
          </div>
          <div class="outfit-model-summary">
            <div><span>每日穿搭人物</span><strong>{{ profile.usePersonalPhotoForOutfit ? '我的照片' : '默认模特' }}</strong></div>
            <button class="text-action" type="button" :disabled="busy" @click="openModelSetup">设置人物形象 <Pencil :size="13" /></button>
          </div>
        </div>

        <div class="portrait-stage">
          <div class="fact-card fact-height">
            <span>身高</span>
            <strong>{{ profile.heightCm ?? '--' }}<small> cm</small></strong>
            <em>{{ basicsReady ? '已保存' : '待填写' }}</em>
          </div>
          <div class="fact-card fact-weight">
            <span>体重</span>
            <strong>{{ profile.weightKg ?? '--' }}<small> kg</small></strong>
            <em>{{ basicsReady ? '已保存' : '待填写' }}</em>
          </div>
          <div class="portrait-frame">
            <img :src="draft.photoUrl || DEFAULT_PORTRAIT" :alt="`${displayName}的个人照片`" />
            <span class="portrait-status"><component :is="profile.photoUrl && !selectedPhoto ? Check : Camera" :size="13" />{{ selectedPhoto ? '预览 · 尚未保存' : profile.photoUrl ? '照片已保存' : '示例照片 · 尚未上传' }}</span>
          </div>
           <div class="fact-card fact-photo">
             <Camera :size="17" />
             <span>照片信息</span>
             <strong>可修改</strong>
           </div>
           <div class="fact-card fact-gender">
             <span>模特性别</span>
             <strong>{{ profileGenderLabel }}</strong>
             <em>{{ profileGender ? '来自个人档案' : '请先填写' }}</em>
           </div>
          <div class="portrait-caption">
            <span>{{ draft.photoName || '当前照片' }}</span>
            <button type="button" @click="openSetup"><Camera :size="14" />更换照片</button>
          </div>
        </div>

        <aside class="analysis-card" aria-labelledby="analysis-card-title">
          <header class="analysis-card-header">
            <div>
              <p class="module-kicker">分析</p>
               <h2 id="analysis-card-title">形象分析结果</h2>
            </div>
             <button class="quiet-action" type="button" @click="openSetup">编辑结果 <Pencil :size="13" /></button>
          </header>
           <p class="analysis-card-intro">{{ analysisSourceLabel }}</p>
           <p v-if="analysisError" class="dialog-error" role="alert">{{ analysisError }}</p>

          <div class="analysis-row-list">
            <div v-for="row in analysisRows" :key="row.key" class="analysis-row">
              <span class="analysis-row-icon"><component :is="row.icon" :size="16" /></span>
              <div class="analysis-row-main">
                <span>{{ row.label }}</span>
                <template v-if="editingField === row.key">
                   <input v-model="editingValue" class="analysis-inline-input" :disabled="busy" :maxlength="row.list ? 800 : row.key === 'bodyProportions' ? 240 : 120" :aria-label="`编辑${row.label}`" @keyup.enter="saveEditedField" @keyup.esc="editingField = ''" />
                  <small>{{ row.detail }}</small>
                </template>
                <template v-else>
                  <strong>{{ row.value || '未填写' }}</strong>
                  <small>{{ row.detail }}</small>
                </template>
              </div>
              <button v-if="editingField === row.key" class="row-edit-button" type="button" :disabled="busy" @click="saveEditedField">{{ busy ? '保存中' : '保存' }}</button>
               <button v-else class="row-edit-button" type="button" :disabled="busy" @click="beginEdit(row)">编辑</button>
            </div>
          </div>

          <div class="palette-block">
             <div class="palette-heading"><span>建议配色</span><CircleHelp :size="14" aria-hidden="true" /></div>
            <div class="swatch-row">
              <span v-for="color in recommendedPalette" :key="color.name" class="swatch-item">
                <i class="swatch" :style="{ backgroundColor: color.color }"></i>
                <small>{{ color.name }}</small>
              </span>
            </div>
          </div>
          <div class="analysis-card-footer">
             <span>分析结果用于穿搭参考，可随时核对与修正。</span>
             <button type="button" @click="goRecommendations">查看穿搭推荐 <ArrowRight :size="14" /></button>
          </div>
        </aside>
      </section>

      <section class="profile-module-grid" aria-label="形象分析模块">
        <article class="profile-module">
          <header class="module-header">
            <div>
              <p class="module-kicker">照片</p>
          <h2>{{ analysisReady && profile.analysisSource === 'MODEL' ? '照片分析特征' : '个人风格标签' }}</h2>
            </div>
            <CircleHelp :size="17" aria-hidden="true" />
          </header>
          <p class="module-subtitle">已保存的风格参考</p>
          <div class="keyword-list">
            <span v-for="keyword in styleKeywords" :key="keyword">{{ keyword }}</span>
          </div>

           <p class="module-footnote">{{ analysisReady ? '根据已保存的分析结果与个人偏好整理，供后续搭配参考。' : '保存分析结果后，可查看相应的风格建议。' }}</p>
        </article>

        <article class="profile-module">
          <header class="module-header">
            <div>
              <p class="module-kicker">版型</p>
              <h2>穿搭建议</h2>
            </div>
            <Ruler :size="18" aria-hidden="true" />
          </header>
          <p class="module-subtitle">依据已保存的形象特征与穿搭建议</p>
          <div class="fit-facts">
            <span><Ruler :size="14" />{{ profile.heightCm ?? '--' }} cm</span>
            <span><Weight :size="14" />{{ profile.weightKg ?? '--' }} kg</span>
            <button type="button" @click="openSetup">编辑 <Pencil :size="12" /></button>
          </div>
          <div class="guidance-list">
             <div><Shirt :size="17" /><span>剪裁</span><strong>{{ analysisReady ? (profile.analysis.fitSuggestions || []).join('、') || '暂无剪裁建议' : '待分析或填写' }}</strong><ChevronRight :size="15" /></div>
             <div><Ruler :size="17" /><span>比例</span><strong>{{ analysisReady ? profile.analysis.bodyProportions || '尚未确认' : '待分析或填写' }}</strong><ChevronRight :size="15" /></div>
             <div><Sparkles :size="17" /><span>单品</span><strong>{{ analysisReady ? (profile.itemSuggestions || []).join('、') || '暂无单品建议' : '待分析或填写' }}</strong><ChevronRight :size="15" /></div>
          </div>
        </article>

        <article class="profile-module recommendation-module">
          <header class="module-header">
            <div>
              <p class="module-kicker">推荐</p>
              <h2>搭配方案参考</h2>
            </div>
            <button class="text-action" type="button" @click="goRecommendations">查看推荐 <ArrowRight :size="14" /></button>
          </header>
           <p class="module-subtitle">以下为日常与通勤搭配示例</p>
          <div class="outfit-grid">
            <article v-for="outfit in outfitFormulas" :key="outfit.label" class="outfit-card">
              <img :src="outfit.image" :alt="outfit.label" />
              <div><strong>{{ outfit.label }}</strong><span>{{ outfit.note }}</span></div>
            </article>
          </div>
           <button class="module-primary" type="button" @click="goRecommendations">创建穿搭方案 <ArrowRight :size="15" /></button>
        </article>
      </section>

      <section class="profile-timeline" aria-labelledby="profile-timeline-title">
        <header class="timeline-header">
          <div>
            <p class="module-kicker">进度</p>
            <h2 id="profile-timeline-title">档案完善进度</h2>
          </div>
          <span>修改基础资料后，请重新分析或手动确认。</span>
        </header>
        <div class="timeline-track">
          <article v-for="(step, index) in flowSteps" :key="`timeline-${step.key}`" class="timeline-item" :class="step.status">
            <span class="timeline-dot"><Check v-if="step.status === 'done'" :size="13" /><span v-else>{{ index + 1 }}</span></span>
            <div><strong>{{ step.label }}{{ step.status === 'done' ? ' · 已完成' : ' · 待完善' }}</strong><small>{{ step.status === 'done' ? (index === 2 ? formatDate(profile.analysisUpdatedAt) : '已保存') : '待填写' }}</small></div>
          </article>
          <div class="timeline-notice" :class="{ hidden: !analysisNoticeVisible }">
            <BellRing :size="17" />
             <div><strong>形象资料已更新</strong><span>{{ analysisSourceLabel }}</span></div>
             <button type="button" aria-label="关闭分析提示" @click="dismissedAnalysisNotice = analysisNoticeKey"><X :size="15" /></button>
          </div>
        </div>
      </section>
    </template>

    <div v-if="editOpen" class="profile-modal-backdrop" @click.self="closeSetup">
      <section class="profile-dialog" role="dialog" aria-modal="true" aria-labelledby="profile-dialog-title">
        <header class="dialog-header">
          <div>
            <p class="module-kicker">编辑</p>
             <h2 id="profile-dialog-title">编辑形象资料</h2>
          </div>
           <button type="button" aria-label="关闭资料编辑" :disabled="busy" @click="closeSetup"><X :size="18" /></button>
        </header>

         <ol class="dialog-steps" aria-label="编辑步骤">
          <li :class="{ active: setupStep === 1, complete: setupStep > 1 }"><span>{{ setupStep > 1 ? '✓' : '1' }}</span>基础资料</li>
          <li :class="{ active: setupStep === 2, complete: setupStep > 2 }"><span>{{ setupStep > 2 ? '✓' : '2' }}</span>上传照片</li>
           <li :class="{ active: setupStep === 3 }"><span>{{ analysisPhase === 'analyzing' ? '…' : '3' }}</span>综合分析</li>
        </ol>

        <form v-if="setupStep === 1" class="dialog-form" @submit.prevent="continueToPhoto">
            <div class="dialog-copy"><strong>完善基础资料</strong><span>模特性别用于效果图生成；身高与体重用于穿搭比例参考。</span></div>
           <div class="measurement-grid">
             <label><span>身高 <em>必填</em></span><div><input v-model="draft.height" type="number" min="80" max="250" step="0.1" inputmode="decimal" required aria-label="身高" /><small>cm</small></div></label>
             <label><span>体重 <em>必填</em></span><div><input v-model="draft.weight" type="number" min="20" max="300" step="0.1" inputmode="decimal" required aria-label="体重" /><small>kg</small></div></label>
           </div>
           <div class="gender-field">
             <label><span>模特性别 <em>必填</em></span><select v-model="profileForm.gender" required aria-label="模特性别"><option value="" disabled>请选择模特性别</option><option value="FEMALE">女</option><option value="MALE">男</option></select></label>
           </div>
           <p class="dialog-hint"><Info :size="15" />请填写准确资料，以供比例与版型建议参考。</p>
          <p v-if="setupError" class="dialog-error" role="alert"><Info :size="15" />{{ setupError }}</p>
          <div class="dialog-actions"><button class="profile-primary" type="submit" :disabled="busy">下一步：上传照片 <ArrowRight :size="15" /></button></div>
        </form>

        <form v-else-if="setupStep === 2" class="dialog-form" @submit.prevent="startAnalysis">
           <div class="dialog-copy"><strong>上传个人照片</strong><span>建议上传光线均匀、面部清晰的正面照片，并减少滤镜与遮挡。</span></div>
          <input ref="photoInput" class="sr-only" type="file" accept="image/png,image/jpeg,image/webp" aria-label="个人照片" @change="handlePhotoChange" />
          <button class="photo-upload-area" type="button" @click="triggerPhotoUpload">
             <img v-if="draft.photoUrl" :src="draft.photoUrl" alt="待分析照片" />
             <span v-else class="upload-empty"><Camera :size="26" /><strong>点击选择照片</strong><small>JPG / PNG / WEBP，最大 10 MB</small></span>
            <span class="upload-overlay"><Camera :size="16" />{{ draft.photoUrl ? '更换照片' : '选择照片' }}</span>
          </button>
           <div class="analysis-input-summary"><span>本次分析资料</span><strong>身高 {{ draft.height }} cm</strong><strong>体重 {{ draft.weight }} kg</strong><strong>{{ photoReady ? '照片已选择' : '请上传个人照片' }}</strong></div>
          <fieldset class="outfit-model-choice" :disabled="busy">
            <legend>是否使用上传的人物形象，生成每日穿搭效果图？</legend>
            <label><input v-model="usePersonalModel" type="radio" name="outfit-model-source" :value="false" /><span><strong>使用默认模特</strong><small>使用系统提供的模特展示每日穿搭。</small></span></label>
            <label><input v-model="usePersonalModel" type="radio" name="outfit-model-source" :value="true" :disabled="!photoReady" /><span><strong>使用我的照片</strong><small>以当前上传的照片作为人物参考，建议使用清晰、无遮挡的人物照片。</small></span></label>
            <p v-if="usePersonalModel">生成每日穿搭效果图时，这张照片将发送至 AI 生图服务。更换照片后需重新选择。</p>
            <p v-else>默认使用系统模特。你可以随时在个人形象档案中切换人物形象。</p>
            <button class="text-action" type="button" :disabled="busy" @click="saveModelSettings">保存人物设置 <Check :size="13" /></button>
          </fieldset>
          <p v-if="setupError" class="dialog-error" role="alert"><Info :size="15" />{{ setupError }}</p>
           <label class="dialog-hint"><input v-model="allowAiAnalysis" type="checkbox" :disabled="busy" />同意将本次个人照片、身高、体重及偏好发送至 AI 分析服务，用于生成形象分析与穿搭建议。</label>
            <div class="dialog-actions"><button class="dialog-back" type="button" :disabled="busy" @click="setupStep = 1">返回修改</button><button class="profile-secondary" type="button" :disabled="busy" @click="finishManualSetup">保存资料，手动填写</button><button class="profile-primary" type="submit" :disabled="busy">开始 AI 分析 <Sparkles :size="15" /></button></div>
        </form>

        <div v-else class="dialog-analysis" role="status" aria-live="polite">
          <div class="analysis-spinner"><LoaderCircle class="spinning" :size="30" /></div>
           <strong>{{ app.state.profilePhotoUploading ? '正在保存个人照片' : '正在分析形象特征…' }}</strong>
           <p>正在结合照片、身高 {{ draft.height }} cm 和体重 {{ draft.weight }} kg 生成穿搭建议。</p>
          <div class="analysis-progress-line"><i></i></div>
        </div>
      </section>
    </div>
  </section>
</template>

<style scoped>
.profile-view {
  width: min(calc(100% - 72px), 1290px);
  margin: 0 auto;
  padding: 42px 0 72px;
  color: var(--ink);
}

.profile-loading {
  min-height: 420px;
  border: 1px solid var(--line);
  border-radius: 22px;
  background: rgba(251, 252, 250, .78);
}

.profile-page-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 30px;
  margin-bottom: 25px;
}

.profile-heading-line { display: flex; align-items: center; flex-wrap: wrap; gap: 13px; }
.profile-page-header h1 {
  margin: 0;
  font-size: clamp(34px, 4vw, 48px);
  font-weight: 720;
  line-height: 1.08;
  letter-spacing: -.065em;
}

.profile-page-header p { max-width: 620px; margin: 12px 0 0; color: var(--muted); font-size: 14px; line-height: 1.75; }
.analysis-status { display: inline-flex; align-items: center; gap: 6px; border: 1px solid #b7d8d0; border-radius: 999px; padding: 7px 11px; color: var(--accent-strong); background: var(--accent-soft); font-size: 11px; font-weight: 700; }
.analysis-status.analyzing { color: #986c36; border-color: #ead3ad; background: #fbf3e5; }
.analysis-status.pending { color: var(--muted); border-color: var(--line); background: var(--surface-soft); }

.profile-primary,
.profile-secondary,
.module-primary,
.dialog-back,
.quiet-action,
.text-action,
.row-edit-button,
.portrait-caption button,
.fit-facts button,
.analysis-card-footer button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  border: 1px solid transparent;
  font-size: 12px;
  font-weight: 700;
}

.profile-primary { min-height: 42px; border-color: var(--accent); border-radius: 7px; padding: 10px 16px; color: #fff; background: var(--accent); }
.profile-primary:hover:not(:disabled) { background: var(--accent-strong); transform: translateY(-1px); }
.profile-secondary { min-height: 42px; border-color: var(--line-strong); border-radius: 7px; padding: 10px 16px; color: var(--ink); background: var(--surface); }
.profile-secondary:hover:not(:disabled) { border-color: var(--accent); color: var(--accent-strong); background: var(--accent-soft); }
.profile-top-action { min-height: 47px; padding: 11px 18px; }

.profile-progress {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: clamp(16px, 4vw, 58px);
  border-top: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
  padding: 17px 20px;
  background: rgba(251, 252, 250, .65);
}

.progress-step { display: flex; min-width: 0; align-items: center; gap: 10px; }
.progress-mark { display: grid; width: 29px; height: 29px; flex: 0 0 auto; place-items: center; border-radius: 50%; color: #fff; background: var(--accent); font-size: 11px; font-weight: 750; }
.progress-step.current .progress-mark { color: var(--accent-strong); border: 1px solid var(--accent); background: var(--accent-soft); }
.progress-step > div { display: grid; gap: 2px; min-width: 0; }
.progress-step strong { font-size: 12px; white-space: nowrap; }
.progress-step span:not(.progress-mark) { color: var(--muted); font-size: 10px; white-space: nowrap; }
.progress-arrow { color: var(--line-strong); }

.profile-hero-board {
  display: grid;
  grid-template-columns: minmax(230px, .82fr) minmax(290px, 1fr) minmax(350px, 1.2fr);
  gap: 23px;
  align-items: center;
  padding: 30px 0 39px;
  border-bottom: 1px solid var(--line);
}

.profile-intro { align-self: center; padding: 12px 0; }
.profile-eyebrow { display: flex; align-items: center; gap: 7px; margin: 0 0 15px; color: var(--accent-strong); font-size: 12px; font-weight: 600; letter-spacing: 0; }
.profile-intro h2 { max-width: 300px; margin: 0; font-size: clamp(25px, 3vw, 36px); font-weight: 720; line-height: 1.12; letter-spacing: -.065em; }
.profile-intro-copy { max-width: 320px; margin: 18px 0 0; color: var(--muted); font-size: 13px; line-height: 1.78; }
.profile-action-row { display: flex; flex-wrap: wrap; gap: 9px; margin-top: 25px; }
.profile-privacy-note { display: flex; align-items: flex-start; gap: 7px; margin-top: 25px; color: var(--muted); font-size: 10px; line-height: 1.55; }
.profile-privacy-note svg { flex: 0 0 auto; color: var(--accent); }
.outfit-model-summary { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 12px; margin-top: 20px; padding: 15px 0; border-top: 1px solid var(--line); }
.outfit-model-summary > div { display: grid; gap: 4px; }
.outfit-model-summary span { color: var(--muted); font-size: 11px; }
.outfit-model-summary strong { font-size: 14px; }
.outfit-model-choice { display: grid; gap: 12px; min-width: 0; margin: 0; padding: 16px; border: 1px solid var(--line); border-radius: 12px; }
.outfit-model-choice legend { max-width: 100%; padding: 0 5px; font-size: 13px; font-weight: 600; line-height: 1.6; }
.outfit-model-choice label { display: flex; align-items: flex-start; gap: 9px; cursor: pointer; }
.outfit-model-choice input { margin-top: 3px; accent-color: var(--accent); flex-shrink: 0; }
.outfit-model-choice label span { display: grid; gap: 4px; }
.outfit-model-choice strong { font-size: 13px; }
.outfit-model-choice small, .outfit-model-choice p { color: var(--muted); font-size: 11px; line-height: 1.7; }
.outfit-model-choice p { margin: 0; }
.outfit-model-choice .text-action { justify-self: start; }

.portrait-stage { position: relative; display: grid; justify-items: center; min-height: 405px; padding: 9px 65px 0; }
.portrait-frame { position: relative; width: min(100%, 288px); aspect-ratio: .78; overflow: hidden; border-radius: 18px; background: var(--surface-soft); box-shadow: 0 18px 38px rgba(25, 70, 60, .08); }
.portrait-frame::after { position: absolute; inset: 0; border: 1px solid rgba(255, 255, 255, .45); border-radius: inherit; content: ''; pointer-events: none; }
.portrait-frame img { width: 100%; height: 100%; object-fit: cover; object-position: center 17%; }
.portrait-status { position: absolute; right: 12px; bottom: 12px; display: inline-flex; align-items: center; gap: 5px; border-radius: 999px; padding: 6px 9px; color: #fff; background: rgba(25, 93, 84, .88); font-size: 10px; font-weight: 700; }
.portrait-caption { display: flex; width: min(100%, 288px); align-items: center; justify-content: space-between; gap: 9px; margin-top: 10px; color: var(--muted); font-size: 10px; }
.portrait-caption span { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.portrait-caption button { padding: 3px 0; color: var(--accent-strong); background: transparent; }

.fact-card { position: absolute; z-index: 2; display: grid; gap: 4px; width: 111px; border: 1px solid rgba(217, 226, 221, .88); border-radius: 15px; padding: 13px 14px; background: rgba(251, 252, 250, .92); box-shadow: 0 13px 28px rgba(25, 70, 60, .06); }
.fact-card span { color: var(--muted); font-size: 10px; }
.fact-card strong { font-size: 18px; line-height: 1.1; letter-spacing: -.04em; }
.fact-card strong small { font-size: 10px; font-weight: 700; }
.fact-card em { color: var(--muted); font-size: 9px; font-style: normal; }
.fact-height { top: 26px; left: 0; }
.fact-weight { bottom: 62px; left: 0; }
.fact-photo { top: 114px; right: 0; align-items: flex-start; }
.fact-photo svg { color: var(--accent); }
.fact-photo strong { color: var(--accent-strong); font-size: 11px; }
.fact-gender { right: 0; bottom: 62px; }
.fact-gender strong { color: var(--accent-strong); }

.analysis-card,
.profile-module,
.profile-timeline {
  border: 1px solid var(--line);
  border-radius: 19px;
  background: rgba(251, 252, 250, .93);
  box-shadow: 0 16px 36px rgba(25, 70, 60, .045);
}

.analysis-card { align-self: stretch; min-width: 0; padding: 23px 23px 18px; }
.analysis-card-header,
.module-header,
.timeline-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 14px; }
.module-kicker { margin: 0 0 7px; color: var(--accent-strong); font-size: 11px; font-weight: 600; letter-spacing: .02em; }
.analysis-card h2,
.profile-module h2,
.profile-timeline h2 { margin: 0; font-size: 22px; font-weight: 690; line-height: 1.2; letter-spacing: -.04em; }
.quiet-action { padding: 4px 0; color: var(--accent-strong); background: transparent; }
.analysis-card-intro { margin: 14px 0 11px; color: var(--muted); font-size: 11px; line-height: 1.6; }
.analysis-row-list { border-top: 1px solid var(--line); }
.analysis-row { display: grid; grid-template-columns: 30px minmax(0, 1fr) auto; gap: 9px; align-items: center; min-height: 65px; border-bottom: 1px solid var(--line); }
.analysis-row-icon { display: grid; width: 27px; height: 27px; place-items: center; border-radius: 50%; color: var(--accent-strong); background: var(--surface-soft); }
.analysis-row-main { display: grid; gap: 3px; min-width: 0; }
.analysis-row-main > span { color: var(--muted); font-size: 10px; }
.analysis-row-main strong { font-size: 13px; }
.analysis-row-main small { overflow: hidden; color: var(--muted); font-size: 9px; line-height: 1.45; text-overflow: ellipsis; white-space: nowrap; }
.row-edit-button { padding: 4px 0 4px 8px; color: var(--accent-strong); background: transparent; white-space: nowrap; }
.analysis-inline-input { width: min(100%, 170px); border: 1px solid var(--line-strong); border-radius: 5px; padding: 4px 7px; outline: none; background: #fff; font-size: 12px; }
.analysis-inline-input:focus { border-color: var(--accent); box-shadow: 0 0 0 3px var(--accent-soft); }
.palette-block { padding-top: 16px; }
.palette-heading { display: flex; align-items: center; gap: 5px; color: var(--ink); font-size: 11px; font-weight: 700; }
.palette-heading svg { color: var(--muted); }
.swatch-row { display: flex; gap: 13px; margin-top: 11px; }
.swatch-item { display: grid; justify-items: center; gap: 5px; min-width: 30px; }
.swatch { display: block; width: 26px; height: 26px; border: 1px solid rgba(24, 37, 34, .1); border-radius: 50%; }
.swatch-item small { max-width: 42px; overflow: hidden; color: var(--muted); font-size: 8px; text-align: center; text-overflow: ellipsis; white-space: nowrap; }
.analysis-card-footer { display: flex; align-items: center; justify-content: space-between; gap: 10px; margin-top: 18px; border-top: 1px solid var(--line); padding-top: 13px; }
.analysis-card-footer span { color: var(--muted); font-size: 9px; line-height: 1.45; }
.analysis-card-footer button { flex: 0 0 auto; padding: 0; color: var(--accent-strong); background: transparent; }

.profile-module-grid { display: grid; grid-template-columns: 1fr 1.12fr 1.28fr; gap: 14px; padding: 27px 0; }
.profile-module { min-width: 0; min-height: 267px; padding: 22px 22px 19px; }
.module-header > svg { color: var(--accent-strong); }
.module-subtitle { margin: 8px 0 17px; color: var(--muted); font-size: 10px; line-height: 1.5; }
.keyword-list { display: flex; flex-wrap: wrap; gap: 8px; }
.keyword-list span { border-radius: 999px; padding: 8px 11px; color: var(--accent-strong); background: var(--accent-soft); font-size: 10px; font-weight: 700; }
.keyword-list span:nth-child(even) { color: var(--ink); background: #f1f3f0; }
.qualitative-scale { margin-top: 23px; }
.scale-line { position: relative; display: grid; grid-template-columns: repeat(4, 1fr); align-items: center; height: 12px; }
.scale-line::before { position: absolute; top: 5px; right: 0; left: 0; height: 2px; background: var(--line-strong); content: ''; }
.scale-line i,
.scale-line b { position: relative; display: block; width: 10px; height: 10px; justify-self: center; border: 1px solid var(--line-strong); border-radius: 50%; background: var(--surface); }
.scale-line b { width: 17px; height: 17px; border-color: var(--accent); background: var(--accent); box-shadow: 0 0 0 4px var(--accent-soft); }
.qualitative-scale > div:last-child { display: flex; justify-content: space-between; margin-top: 7px; color: var(--muted); font-size: 9px; }
.qualitative-scale strong { color: var(--accent-strong); }
.module-footnote { margin: 23px 0 0; color: var(--muted); font-size: 10px; line-height: 1.6; }

.fit-facts { display: flex; align-items: center; gap: 13px; border-bottom: 1px solid var(--line); padding: 0 0 13px; }
.fit-facts span { display: inline-flex; align-items: center; gap: 5px; color: var(--ink); font-size: 12px; font-weight: 700; }
.fit-facts span svg { color: var(--accent); }
.fit-facts button { margin-left: auto; padding: 0; color: var(--accent-strong); background: transparent; }
.guidance-list { display: grid; }
.guidance-list > div { display: grid; grid-template-columns: 22px 42px minmax(0, 1fr) 15px; gap: 7px; align-items: center; min-height: 46px; border-bottom: 1px solid var(--line); }
.guidance-list > div:last-child { border-bottom: 0; }
.guidance-list svg { color: var(--accent-strong); }
.guidance-list span { color: var(--muted); font-size: 10px; }
.guidance-list strong { overflow: hidden; font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.guidance-list > div > svg:last-child { color: var(--line-strong); }

.text-action { padding: 3px 0; color: var(--accent-strong); background: transparent; white-space: nowrap; }
.outfit-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 9px; }
.outfit-card { overflow: hidden; border: 1px solid var(--line); border-radius: 10px; background: #fff; }
.outfit-card img { width: 100%; aspect-ratio: 1.35; object-fit: cover; }
.outfit-card div { display: grid; gap: 3px; padding: 9px 10px 10px; }
.outfit-card strong { font-size: 11px; }
.outfit-card span { color: var(--muted); font-size: 9px; }
.module-primary { width: 100%; min-height: 38px; margin-top: 12px; border-radius: 6px; color: #fff; background: var(--accent); }
.module-primary:hover { background: var(--accent-strong); }

.profile-timeline { padding: 20px 23px 22px; }
.timeline-header { align-items: center; }
.timeline-header > span { color: var(--muted); font-size: 10px; }
.timeline-track { display: grid; grid-template-columns: repeat(3, 1fr) 1.25fr; gap: 0; align-items: center; margin-top: 20px; }
.timeline-item { position: relative; display: grid; grid-template-columns: 24px 1fr; gap: 9px; align-items: start; min-height: 50px; }
.timeline-item:not(:last-of-type)::after { position: absolute; top: 10px; right: 0; left: 31px; z-index: 0; height: 1px; background: var(--line-strong); content: ''; }
.timeline-dot { position: relative; z-index: 1; display: grid; width: 22px; height: 22px; place-items: center; border-radius: 50%; color: #fff; background: var(--accent); }
.timeline-item.current .timeline-dot { border: 1px solid var(--accent); color: var(--accent-strong); background: var(--accent-soft); font-size: 10px; font-weight: 700; }
.timeline-item strong { display: block; font-size: 11px; }
.timeline-item small { display: block; margin-top: 5px; color: var(--muted); font-size: 9px; }
.timeline-notice { display: flex; min-height: 50px; align-items: center; gap: 9px; border-radius: 11px; padding: 10px 12px; color: var(--accent-strong); background: var(--accent-soft); transition: opacity 180ms ease, transform 180ms ease; }
.timeline-notice.hidden { visibility: hidden; opacity: 0; transform: translateY(3px); }
.timeline-notice > svg { flex: 0 0 auto; }
.timeline-notice div { display: grid; gap: 2px; min-width: 0; }
.timeline-notice strong { font-size: 11px; }
.timeline-notice span { color: var(--muted); font-size: 9px; line-height: 1.4; }
.timeline-notice button { display: grid; width: 24px; height: 24px; flex: 0 0 auto; place-items: center; margin-left: auto; border: 0; color: var(--muted); background: transparent; }

.profile-modal-backdrop { position: fixed; inset: 0; z-index: 80; display: grid; place-items: center; padding: 22px; background: rgba(24, 37, 34, .34); backdrop-filter: blur(8px); }
.profile-dialog { width: min(100%, 540px); max-height: min(740px, calc(100dvh - 44px)); overflow: auto; border: 1px solid rgba(255, 255, 255, .6); border-radius: 20px; padding: 24px; background: var(--surface); box-shadow: 0 28px 80px rgba(24, 37, 34, .2); }
.dialog-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 20px; }
.dialog-header h2 { margin: 0; font-size: 25px; letter-spacing: -.05em; }
.dialog-header > button { display: grid; width: 34px; height: 34px; place-items: center; border: 1px solid var(--line); border-radius: 50%; color: var(--muted); background: transparent; }
.dialog-header > button:hover:not(:disabled) { color: var(--ink); background: var(--surface-soft); }
.dialog-steps { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; margin: 24px 0 28px; padding: 0; list-style: none; }
.dialog-steps li { display: flex; align-items: center; gap: 7px; color: var(--muted); font-size: 10px; font-weight: 700; }
.dialog-steps span { display: grid; width: 22px; height: 22px; place-items: center; border: 1px solid var(--line-strong); border-radius: 50%; font-size: 9px; }
.dialog-steps li.active { color: var(--accent-strong); }
.dialog-steps li.active span,
.dialog-steps li.complete span { border-color: var(--accent); color: #fff; background: var(--accent); }
.dialog-form { display: grid; gap: 18px; }
.dialog-copy { display: grid; gap: 7px; }
.dialog-copy strong { font-size: 16px; }
.dialog-copy span { color: var(--muted); font-size: 11px; line-height: 1.6; }
.measurement-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.measurement-grid label { display: grid; gap: 7px; }
.measurement-grid label > span { color: var(--muted); font-size: 10px; font-weight: 700; }
.measurement-grid em { color: var(--coral); font-style: normal; }
.measurement-grid label > div { display: flex; align-items: center; border: 1px solid var(--line); border-radius: 8px; background: #fff; }
.measurement-grid input { width: 100%; min-width: 0; border: 0; outline: 0; padding: 12px; background: transparent; font: inherit; font-size: 18px; font-weight: 700; }
.measurement-grid input:focus { box-shadow: inset 0 0 0 2px var(--accent); }
.measurement-grid small { padding-right: 12px; color: var(--muted); font-size: 10px; font-weight: 700; }
.gender-field { display: grid; gap: 7px; }
.gender-field label { display: grid; gap: 7px; }
.gender-field label > span { color: var(--muted); font-size: 10px; font-weight: 700; }
.gender-field em { color: var(--coral); font-style: normal; }
.gender-field select { width: 100%; border: 1px solid var(--line); border-radius: 8px; outline: 0; padding: 12px; color: var(--ink); background: #fff; font: inherit; }
.gender-field select:focus { border-color: var(--accent); box-shadow: 0 0 0 3px var(--accent-soft); }
.dialog-hint { display: flex; align-items: flex-start; gap: 7px; margin: 0; color: var(--muted); font-size: 10px; line-height: 1.6; }
.dialog-hint svg { flex: 0 0 auto; color: var(--accent); }
.dialog-error { display: flex; align-items: center; gap: 7px; margin: 0; color: var(--danger); font-size: 11px; }
.dialog-error svg { flex: 0 0 auto; }
.dialog-actions { display: flex; justify-content: flex-end; gap: 9px; margin-top: 3px; }
.dialog-back { min-height: 42px; border-color: var(--line); border-radius: 7px; padding: 10px 14px; color: var(--ink); background: transparent; }
.dialog-back:hover { background: var(--surface-soft); }
.photo-upload-area { position: relative; display: grid; width: 100%; aspect-ratio: 1.9; place-items: center; overflow: hidden; border: 1px dashed var(--line-strong); border-radius: 12px; padding: 0; color: var(--muted); background: var(--surface-soft); }
.photo-upload-area:hover { border-color: var(--accent); background: var(--accent-soft); }
.photo-upload-area img { width: 100%; height: 100%; object-fit: cover; object-position: center 20%; }
.upload-empty { display: grid; justify-items: center; gap: 7px; }
.upload-empty svg { color: var(--accent); }
.upload-empty strong { color: var(--ink); font-size: 13px; }
.upload-empty small { font-size: 10px; }
.upload-overlay { position: absolute; right: 12px; bottom: 12px; display: inline-flex; align-items: center; gap: 6px; border-radius: 999px; padding: 7px 10px; color: #fff; background: rgba(24, 37, 34, .75); font-size: 10px; font-weight: 700; }
.analysis-input-summary { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; border: 1px solid var(--line); border-radius: 9px; padding: 11px 12px; background: #fff; }
.analysis-input-summary span { width: 100%; color: var(--muted); font-size: 10px; }
.analysis-input-summary strong { border-radius: 999px; padding: 6px 9px; color: var(--accent-strong); background: var(--accent-soft); font-size: 10px; }
.dialog-analysis { display: grid; justify-items: center; gap: 10px; padding: 40px 20px 24px; text-align: center; }
.analysis-spinner { display: grid; width: 64px; height: 64px; place-items: center; border-radius: 50%; color: var(--accent); background: var(--accent-soft); }
.dialog-analysis strong { font-size: 17px; }
.dialog-analysis p { max-width: 340px; margin: 0; color: var(--muted); font-size: 11px; line-height: 1.7; }
.analysis-progress-line { width: min(100%, 260px); height: 4px; overflow: hidden; border-radius: 99px; background: var(--line); }
.analysis-progress-line i { display: block; width: 43%; height: 100%; border-radius: inherit; background: var(--accent); animation: analysis-slide 1.1s ease-in-out infinite alternate; }
@keyframes analysis-slide { from { transform: translateX(-10%); } to { transform: translateX(145%); } }

@media (max-width: 1120px) {
  .profile-hero-board { grid-template-columns: minmax(220px, .9fr) minmax(280px, 1fr); }
  .analysis-card { grid-column: 1 / -1; }
  .profile-module-grid { grid-template-columns: 1fr 1fr; }
  .recommendation-module { grid-column: 1 / -1; }
  .outfit-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}

@media (max-width: 760px) {
  .profile-view { width: min(calc(100% - 32px), 620px); padding: 28px 0 52px; }
  .profile-page-header { display: grid; align-items: start; gap: 18px; }
  .profile-page-header h1 { font-size: 34px; }
  .profile-top-action { justify-self: start; }
  .profile-progress { justify-content: flex-start; overflow-x: auto; gap: 17px; padding: 14px 3px; }
  .progress-step { flex: 0 0 auto; }
  .progress-arrow { flex: 0 0 auto; }
  .profile-hero-board { grid-template-columns: 1fr; gap: 25px; padding-top: 25px; }
  .profile-intro { padding: 0; }
  .profile-intro h2 { max-width: none; font-size: 30px; }
  .profile-intro-copy { max-width: none; }
  .portrait-stage { min-height: 0; padding: 0 0 4px; }
  .portrait-frame { width: min(100%, 330px); }
  .portrait-caption { width: min(100%, 330px); }
  .fact-card { position: static; width: min(100%, 330px); }
  .portrait-stage { display: flex; flex-direction: column; align-items: center; }
  .portrait-frame { order: 0; }
  .portrait-caption { order: 1; }
  .fact-card { order: 2; margin-top: 9px; }
  .fact-height,
  .fact-weight,
  .fact-photo,
  .fact-gender { display: grid; grid-template-columns: 1fr auto; align-items: center; }
  .fact-height span,
  .fact-weight span,
  .fact-gender span { grid-column: 1; }
  .fact-height strong,
  .fact-weight strong,
  .fact-gender strong { grid-column: 2; grid-row: span 2; }
  .fact-height em,
  .fact-weight em,
  .fact-gender em { grid-column: 1; }
  .fact-photo { grid-template-columns: auto 1fr; }
  .fact-photo svg { grid-row: span 2; }
  .analysis-card { grid-column: auto; padding: 20px 17px 16px; }
  .swatch-row { justify-content: space-between; gap: 6px; }
  .profile-module-grid { grid-template-columns: 1fr; gap: 12px; padding: 19px 0; }
  .recommendation-module { grid-column: auto; }
  .profile-module { min-height: auto; }
  .timeline-header { display: grid; gap: 8px; }
  .timeline-track { grid-template-columns: 1fr; gap: 12px; }
  .timeline-item { min-height: 45px; }
  .timeline-item:not(:last-of-type)::after { top: 22px; bottom: -12px; left: 10px; width: 1px; height: auto; }
  .timeline-notice { min-height: 0; }
  .profile-modal-backdrop { padding: 12px; }
  .profile-dialog { max-height: calc(100dvh - 24px); padding: 20px 17px; }
  .dialog-actions { flex-wrap: wrap; }
  .dialog-actions button { flex: 1 1 auto; }
}

@media (prefers-reduced-motion: reduce) {
  .analysis-progress-line i { animation: none; }
}
</style>
