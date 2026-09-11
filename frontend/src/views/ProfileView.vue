<script setup>
import { computed, onBeforeUnmount, onDeactivated, onMounted, reactive, ref, watch } from 'vue'
import {
  ArrowRight,
  BellRing,
  Camera,
  Check,
  ChevronRight,
  CircleHelp,
  Info,
  LoaderCircle,
  Palette,
  Pencil,
  RefreshCw,
  Ruler,
  ScanFace,
  Shirt,
  Sparkles,
  UserRound,
  Weight,
  X
} from '@lucide/vue'

const props = defineProps({
  app: { type: Object, required: true }
})

const DEFAULT_PORTRAIT = '/assets/profile-portrait.png'
const SETUP_STORAGE_KEY = 'fashion.profile.setup.v1'

const editOpen = ref(false)
const setupStep = ref(1)
const analysisPhase = ref('idle')
const setupError = ref('')
const photoInput = ref(null)
const analysisNoticeVisible = ref(false)
const editingField = ref('')
const editingValue = ref('')
const photoReady = ref(false)
const basicsReady = ref(false)
const analysisReady = ref(false)
const basicsEdited = reactive({ height: false, weight: false })
let previousBodyOverflow = ''
let analysisTimer = null

const draft = reactive({
  height: '175',
  weight: '65',
  photoUrl: DEFAULT_PORTRAIT,
  photoName: '示例照片'
})

const analysisValues = reactive({
  faceShape: '椭圆偏长',
  facialLine: '清晰、利落',
  visualContrast: '中等',
  hairFeatures: '短发、整洁'
})

const profile = computed(() => props.app.state.profile || {})
const displayName = computed(() => profile.value.displayName || props.app.state.authUser?.username || '你')
const styleTags = computed(() => unique([
  ...(profile.value.styleTags || []),
  ...(profile.value.stylePreferences || []),
  '干净简约'
]).slice(0, 4))
const styleKeywords = computed(() => unique([
  ...(profile.value.styleTags || []),
  ...(profile.value.stylePreferences || []),
  '自然',
  '利落',
  '舒适'
]).slice(0, 5))
const recommendedPalette = computed(() => {
  const names = unique([
    ...(profile.value.colorSuggestions || []),
    '深绿',
    '藏青',
    '燕麦色',
    '浅灰',
    '米白',
    '炭灰'
  ]).slice(0, 6)
  return names.map((name, index) => ({ name, color: colorFor(name, index) }))
})
const summary = computed(() => {
  const tags = styleTags.value.slice(0, 2).join('、') || '清爽、简洁'
  return `你目前更常选${tags}。下一次搭配可以继续用简洁配色和利落线条。`
})
const analysisStatusLabel = computed(() => analysisPhase.value === 'analyzing' ? '分析中' : analysisReady.value ? '分析完成' : '待完成')
const analysisStatusClass = computed(() => analysisPhase.value === 'analyzing' ? 'analyzing' : analysisReady.value ? 'complete' : 'pending')
const analysisRows = computed(() => [
  {
    key: 'faceShape',
    label: '脸型',
    value: analysisValues.faceShape,
    detail: '轮廓偏长，衣领和领口不必堆叠得太满。',
    icon: ScanFace
  },
  {
    key: 'facialLine',
    label: '面部线条',
    value: analysisValues.facialLine,
    detail: '线条清楚，衣物可以利落一些，材质不必太硬。',
    icon: UserRound
  },
  {
    key: 'visualContrast',
    label: '视觉对比',
    value: analysisValues.visualContrast,
    detail: '低饱和配色更稳，深色留一处就够。',
    icon: Palette
  },
  {
    key: 'hairFeatures',
    label: '发型特征',
    value: analysisValues.hairFeatures,
    detail: '发型看起来整洁，搭配上可以保留一点轻松质感。',
    icon: Sparkles
  }
])
const flowSteps = computed(() => [
  { key: 'basics', label: '基础资料', note: basicsReady.value ? `身高 ${draft.height || '--'} cm · 体重 ${draft.weight || '--'} kg` : '等待填写身高与体重', status: basicsReady.value ? 'done' : 'current' },
  { key: 'photo', label: '上传照片', note: photoReady.value ? '个人照片已上传' : '等待上传个人照片', status: photoReady.value ? 'done' : 'current' },
  { key: 'analysis', label: '综合分析', note: analysisPhase.value === 'analyzing' ? '正在结合照片和基础信息' : analysisReady.value ? '照片和基础信息已整理' : '等待照片与基础资料', status: analysisPhase.value === 'analyzing' || !analysisReady.value ? 'current' : 'done' }
])
const outfitFormulas = [
  { label: '日常穿搭', note: '舒适自然 · 方便出门', image: '/assets/look-everyday-flatlay.png' },
  { label: '通勤穿搭', note: '简洁稳重 · 适合工作日', image: '/assets/look-commute-flatlay.png' }
]

function unique(values) {
  return [...new Set(values.filter((value) => typeof value === 'string' && value.trim()).map((value) => value.trim()))]
}

function colorFor(name, index = 0) {
  const map = {
    '深绿': '#276e63',
    '橄榄绿': '#70765d',
    '藏青': '#2e435e',
    '海军蓝': '#2e435e',
    '雾蓝': '#9db8c8',
    '浅蓝': '#a9c2d3',
    '燕麦色': '#cdbca4',
    '卡其': '#b8a485',
    '米白': '#eee8dc',
    '暖白': '#f2eee6',
    '浅灰': '#c7cbc9',
    '石墨灰': '#626968',
    '炭灰': '#424746',
    '黑色': '#1c201f',
    '棕色': '#8d755f'
  }
  return map[name] || props.app.colorFor?.(name) || ['#276e63', '#2e435e', '#cdbca4', '#eee8dc', '#c7cbc9', '#424746'][index % 6]
}

function formatDate(value) {
  if (!value) return '暂无日期'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '暂无日期'
  return new Intl.DateTimeFormat('zh-CN', { month: '2-digit', day: '2-digit' }).format(date)
}

function hydrateFromProfile(value) {
  if (!value) return
  if (value.heightCm) draft.height = String(value.heightCm)
  if (value.weightKg) draft.weight = String(value.weightKg)
  if (value.heightCm && value.weightKg) basicsReady.value = true
  if (value.photoUrl) {
    draft.photoUrl = value.photoUrl
    photoReady.value = true
  }
  if (value.analysis?.faceShape) analysisValues.faceShape = value.analysis.faceShape
  if (value.analysis?.facialLine) analysisValues.facialLine = value.analysis.facialLine
  if (value.analysis?.visualContrast) analysisValues.visualContrast = value.analysis.visualContrast
  if (value.analysis?.hairFeatures) analysisValues.hairFeatures = value.analysis.hairFeatures
  if (value.analysis && value.heightCm && value.weightKg && value.photoUrl) {
    analysisReady.value = true
    analysisNoticeVisible.value = true
    analysisPhase.value = 'complete'
  }
}

function hydrateLocalSetup() {
  if (typeof window === 'undefined') return
  try {
    const saved = JSON.parse(window.localStorage.getItem(SETUP_STORAGE_KEY) || 'null')
    if (saved?.height) draft.height = String(saved.height)
    if (saved?.weight) draft.weight = String(saved.weight)
    if (saved?.height && saved?.weight) basicsReady.value = true
    if (saved?.photoUrl) draft.photoUrl = saved.photoUrl
    if (saved?.photoName) draft.photoName = saved.photoName
    if (saved?.photoUrl) photoReady.value = true
    if (saved?.analysisComplete || (saved?.height && saved?.weight && saved?.photoUrl)) {
      analysisReady.value = true
      analysisNoticeVisible.value = true
      analysisPhase.value = 'complete'
    }
  } catch {
    // Local demo state is optional; the page keeps its default visual state.
  }
}

function persistLocalSetup() {
  if (typeof window === 'undefined') return
  try {
    window.localStorage.setItem(SETUP_STORAGE_KEY, JSON.stringify({
      height: draft.height,
      weight: draft.weight,
      photoUrl: draft.photoUrl.startsWith('data:') ? draft.photoUrl : '',
      photoName: draft.photoName,
      analysisComplete: true
    }))
  } catch {
    // A large uploaded photo should never block the page interaction.
  }
}

function validateBasics() {
  const height = Number(draft.height)
  const weight = Number(draft.weight)
  if (!Number.isFinite(height) || height < 80 || height > 250) {
    setupError.value = '身高请输入 80–250 cm。'
    return false
  }
  if (!Number.isFinite(weight) || weight < 20 || weight > 300) {
    setupError.value = '体重请输入 20–300 kg。'
    return false
  }
  setupError.value = ''
  return true
}

function openSetup() {
  setupError.value = ''
  setupStep.value = 1
  basicsEdited.height = false
  basicsEdited.weight = false
  photoReady.value = Boolean(draft.photoUrl && draft.photoUrl !== DEFAULT_PORTRAIT)
  editOpen.value = true
}

function closeSetup() {
  if (analysisPhase.value === 'analyzing') return
  editOpen.value = false
  setupError.value = ''
}

function continueToPhoto() {
  if (!validateBasics()) return
  if (!basicsEdited.height || !basicsEdited.weight) {
    setupError.value = '请填写身高和体重后再继续。'
    return
  }
  basicsReady.value = true
  setupStep.value = 2
}

function markBasicEdited(field) {
  basicsEdited[field] = true
}

function triggerPhotoUpload() {
  photoInput.value?.click()
}

function handlePhotoChange(event) {
  const file = event.target.files?.[0]
  if (!file) return
  if (!file.type.startsWith('image/')) {
    setupError.value = '请选择 JPG、PNG 或 WEBP 照片。'
    return
  }
  if (file.size > 10 * 1024 * 1024) {
    setupError.value = '图片不能超过 10 MB。'
    return
  }
  const reader = new FileReader()
  reader.onload = () => {
    draft.photoUrl = String(reader.result)
    draft.photoName = file.name
    photoReady.value = true
    setupError.value = ''
  }
  reader.readAsDataURL(file)
}

function startAnalysis() {
  if (!validateBasics()) {
    setupStep.value = 1
    return
  }
  if (!draft.photoUrl || !photoReady.value) {
    setupError.value = '请先选择个人照片，再开始分析。'
    return
  }
  setupStep.value = 3
  setupError.value = ''
  analysisPhase.value = 'analyzing'
  analysisTimer = window.setTimeout(() => {
    analysisPhase.value = 'complete'
    analysisReady.value = true
    analysisNoticeVisible.value = true
    persistLocalSetup()
    editOpen.value = false
  }, 1500)
}

function beginEdit(row) {
  editingField.value = row.key
  editingValue.value = row.value
}

function saveEditedField() {
  if (!editingField.value) return
  const value = editingValue.value.trim()
  if (value) analysisValues[editingField.value] = value
  editingField.value = ''
  editingValue.value = ''
  analysisNoticeVisible.value = true
}

function goRecommendations() {
  props.app.selectView('recommend')
}

watch(() => props.app.state.profile, hydrateFromProfile, { immediate: true })

watch(editOpen, (open) => {
  if (typeof document === 'undefined') return
  if (open) {
    previousBodyOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
  } else {
    document.body.style.overflow = previousBodyOverflow
  }
})

onMounted(hydrateLocalSetup)

onBeforeUnmount(() => {
  if (typeof document !== 'undefined') document.body.style.overflow = previousBodyOverflow
  if (analysisTimer) window.clearTimeout(analysisTimer)
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
      <strong>正在加载个人形象</strong>
      <span>加载完成后，这里会显示照片、基础资料和分析结果。</span>
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
          <p>填写身高、体重并上传照片后，这里会整理一份形象参考和穿衣建议。</p>
        </div>
        <button class="profile-primary profile-top-action" type="button" @click="goRecommendations">
          查看搭配推荐 <ArrowRight :size="17" />
        </button>
      </header>

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
          <h2 id="profile-hero-title">你的穿衣参考</h2>
          <p class="profile-intro-copy">{{ summary }}</p>
          <div class="profile-action-row">
            <button class="profile-primary" type="button" @click="openSetup">
              编辑形象资料 <Pencil :size="15" />
            </button>
            <button class="profile-secondary" type="button" :disabled="analysisPhase === 'analyzing'" @click="openSetup">
              <RefreshCw :size="15" />重新填写
            </button>
          </div>
          <div class="profile-privacy-note">
            <Info :size="16" aria-hidden="true" />
            <span>照片只用于生成穿衣参考，结果可以随时修改。</span>
          </div>
        </div>

        <div class="portrait-stage">
          <div class="fact-card fact-height">
            <span>身高</span>
            <strong>{{ draft.height || '--' }}<small> cm</small></strong>
            <em>{{ basicsReady ? '手动填写' : '示例数据' }}</em>
          </div>
          <div class="fact-card fact-weight">
            <span>体重</span>
            <strong>{{ draft.weight || '--' }}<small> kg</small></strong>
            <em>{{ basicsReady ? '手动填写' : '示例数据' }}</em>
          </div>
          <div class="portrait-frame">
            <img :src="draft.photoUrl || DEFAULT_PORTRAIT" :alt="`${displayName}的个人照片`" />
            <span class="portrait-status"><component :is="photoReady ? Check : Camera" :size="13" />{{ photoReady ? '照片已上传' : '示例照片 · 请更换' }}</span>
          </div>
          <div class="fact-card fact-photo">
            <Camera :size="17" />
            <span>照片信息</span>
            <strong>可修改</strong>
          </div>
          <div class="portrait-caption">
            <span>{{ draft.photoName || '当前照片' }}</span>
            <button type="button" @click="openSetup"><Camera :size="14" />更换照片</button>
          </div>
        </div>

        <aside class="analysis-card" aria-labelledby="analysis-card-title">
          <header class="analysis-card-header">
            <div>
              <p class="module-kicker">AI PERSONAL ANALYSIS</p>
               <h2 id="analysis-card-title">形象分析结果</h2>
            </div>
             <button class="quiet-action" type="button" @click="openSetup">编辑结果 <Pencil :size="13" /></button>
          </header>
           <p class="analysis-card-intro">结果参考了你填写的身高、体重和照片，只用于穿衣建议，不评价外貌。</p>

          <div class="analysis-row-list">
            <div v-for="row in analysisRows" :key="row.key" class="analysis-row">
              <span class="analysis-row-icon"><component :is="row.icon" :size="16" /></span>
              <div class="analysis-row-main">
                <span>{{ row.label }}</span>
                <template v-if="editingField === row.key">
                   <input v-model="editingValue" class="analysis-inline-input" :aria-label="`编辑${row.label}`" @keyup.enter="saveEditedField" @keyup.esc="editingField = ''" />
                  <small>{{ row.detail }}</small>
                </template>
                <template v-else>
                  <strong>{{ row.value }}</strong>
                  <small>{{ row.detail }}</small>
                </template>
              </div>
              <button v-if="editingField === row.key" class="row-edit-button" type="button" @click="saveEditedField">保存</button>
               <button v-else class="row-edit-button" type="button" @click="beginEdit(row)">编辑</button>
            </div>
          </div>

          <div class="palette-block">
             <div class="palette-heading"><span>可以先试的颜色</span><CircleHelp :size="14" aria-hidden="true" /></div>
            <div class="swatch-row">
              <span v-for="color in recommendedPalette" :key="color.name" class="swatch-item">
                <i class="swatch" :style="{ backgroundColor: color.color }"></i>
                <small>{{ color.name }}</small>
              </span>
            </div>
          </div>
          <div class="analysis-card-footer">
             <span>这份结果只作穿衣参考，随时可以修改。</span>
             <button type="button" @click="goRecommendations">查看搭配 <ArrowRight :size="14" /></button>
          </div>
        </aside>
      </section>

      <section class="profile-module-grid" aria-label="形象分析模块">
        <article class="profile-module">
          <header class="module-header">
            <div>
              <p class="module-kicker">VISUAL NOTES</p>
              <h2>照片里的线索</h2>
            </div>
            <CircleHelp :size="17" aria-hidden="true" />
          </header>
          <p class="module-subtitle">当前照片中可以看到的特征</p>
          <div class="keyword-list">
            <span v-for="keyword in styleKeywords" :key="keyword">{{ keyword }}</span>
          </div>
           <div class="qualitative-scale" aria-label="风格倾向参考">
            <div class="scale-line"><i></i><b></b><i></i><i></i></div>
            <div><span>简洁</span><strong>自然</strong><span>利落</span><span>精致</span></div>
          </div>
           <p class="module-footnote">保留自然感即可；颜色稳一点，线条清楚一些。</p>
        </article>

        <article class="profile-module">
          <header class="module-header">
            <div>
              <p class="module-kicker">FIT DIRECTION</p>
              <h2>穿衣建议</h2>
            </div>
            <Ruler :size="18" aria-hidden="true" />
          </header>
          <p class="module-subtitle">结合已填的身高、体重和照片</p>
          <div class="fit-facts">
            <span><Ruler :size="14" />{{ draft.height }} cm</span>
            <span><Weight :size="14" />{{ draft.weight }} kg</span>
            <button type="button" @click="openSetup">编辑 <Pencil :size="12" /></button>
          </div>
          <div class="guidance-list">
             <div><Shirt :size="17" /><span>版型</span><strong>合身为主，别贴得太紧</strong><ChevronRight :size="15" /></div>
             <div><Ruler :size="17" /><span>衣长</span><strong>上衣不必太长，比例更利落</strong><ChevronRight :size="15" /></div>
             <div><Sparkles :size="17" /><span>材质</span><strong>挺括和柔软材质搭着用</strong><ChevronRight :size="15" /></div>
          </div>
        </article>

        <article class="profile-module recommendation-module">
          <header class="module-header">
            <div>
              <p class="module-kicker">NEXT LOOK</p>
              <h2>从搭配开始</h2>
            </div>
            <button class="text-action" type="button" @click="goRecommendations">查看推荐 <ArrowRight :size="14" /></button>
          </header>
           <p class="module-subtitle">先从这两套日常搭配开始</p>
          <div class="outfit-grid">
            <article v-for="outfit in outfitFormulas" :key="outfit.label" class="outfit-card">
              <img :src="outfit.image" :alt="outfit.label" />
              <div><strong>{{ outfit.label }}</strong><span>{{ outfit.note }}</span></div>
            </article>
          </div>
           <button class="module-primary" type="button" @click="goRecommendations">去生成搭配 <ArrowRight :size="15" /></button>
        </article>
      </section>

      <section class="profile-timeline" aria-labelledby="profile-timeline-title">
        <header class="timeline-header">
          <div>
            <p class="module-kicker">PROFILE JOURNEY</p>
            <h2 id="profile-timeline-title">资料更新记录</h2>
          </div>
          <span>资料可以随时修改，改完后重新分析</span>
        </header>
        <div class="timeline-track">
          <article v-for="(step, index) in flowSteps" :key="`timeline-${step.key}`" class="timeline-item" :class="step.status">
            <span class="timeline-dot"><Check v-if="step.status === 'done'" :size="13" /><span v-else>{{ index + 1 }}</span></span>
            <div><strong>{{ step.label }}{{ step.status === 'done' ? (index === 2 ? '完成' : '已填写') : '待完成' }}</strong><small>{{ step.status === 'done' ? (index === 2 ? formatDate(profile.generatedAt) : '已保存') : '等待输入' }}</small></div>
          </article>
          <div class="timeline-notice" :class="{ hidden: !analysisNoticeVisible }">
            <BellRing :size="17" />
             <div><strong>分析结果已更新</strong><span>分析已参考照片、身高和体重。</span></div>
             <button type="button" aria-label="关闭分析提示" @click="analysisNoticeVisible = false"><X :size="15" /></button>
          </div>
        </div>
      </section>
    </template>

    <div v-if="editOpen" class="profile-modal-backdrop" @click.self="closeSetup">
      <section class="profile-dialog" role="dialog" aria-modal="true" aria-labelledby="profile-dialog-title">
        <header class="dialog-header">
          <div>
            <p class="module-kicker">UPDATE PROFILE</p>
             <h2 id="profile-dialog-title">编辑形象资料</h2>
          </div>
           <button type="button" aria-label="关闭资料编辑" :disabled="analysisPhase === 'analyzing'" @click="closeSetup"><X :size="18" /></button>
        </header>

         <ol class="dialog-steps" aria-label="编辑步骤">
          <li :class="{ active: setupStep === 1, complete: setupStep > 1 }"><span>{{ setupStep > 1 ? '✓' : '1' }}</span>基础资料</li>
          <li :class="{ active: setupStep === 2, complete: setupStep > 2 }"><span>{{ setupStep > 2 ? '✓' : '2' }}</span>上传照片</li>
           <li :class="{ active: setupStep === 3 }"><span>{{ analysisPhase === 'analyzing' ? '…' : '3' }}</span>综合分析</li>
        </ol>

        <form v-if="setupStep === 1" class="dialog-form" @submit.prevent="continueToPhoto">
           <div class="dialog-copy"><strong>先填写基础资料</strong><span>身高和体重会和照片一起用于生成穿衣建议。</span></div>
          <div class="measurement-grid">
            <label><span>身高 <em>必填</em></span><div><input v-model="draft.height" type="number" min="80" max="250" step="0.1" inputmode="decimal" required aria-label="身高" @input="markBasicEdited('height')" /><small>cm</small></div></label>
            <label><span>体重 <em>必填</em></span><div><input v-model="draft.weight" type="number" min="20" max="300" step="0.1" inputmode="decimal" required aria-label="体重" @input="markBasicEdited('weight')" /><small>kg</small></div></label>
          </div>
           <p class="dialog-hint"><Info :size="15" />这些数据只用于穿衣比例、版型和尺码参考，不用于健康评价。</p>
          <p v-if="setupError" class="dialog-error" role="alert"><Info :size="15" />{{ setupError }}</p>
          <div class="dialog-actions"><button class="profile-primary" type="submit">下一步：上传照片 <ArrowRight :size="15" /></button></div>
        </form>

        <form v-else-if="setupStep === 2" class="dialog-form" @submit.prevent="startAnalysis">
           <div class="dialog-copy"><strong>上传一张清楚的个人照片</strong><span>建议使用正面照片，光线均匀、少用滤镜，确保面部清楚。</span></div>
          <input ref="photoInput" class="sr-only" type="file" accept="image/png,image/jpeg,image/webp" aria-label="个人照片" @change="handlePhotoChange" />
          <button class="photo-upload-area" type="button" @click="triggerPhotoUpload">
             <img v-if="draft.photoUrl" :src="draft.photoUrl" alt="待分析照片" />
             <span v-else class="upload-empty"><Camera :size="26" /><strong>点击选择照片</strong><small>JPG / PNG / WEBP，最大 10 MB</small></span>
            <span class="upload-overlay"><Camera :size="16" />{{ draft.photoUrl ? '更换照片' : '选择照片' }}</span>
          </button>
           <div class="analysis-input-summary"><span>本次分析资料</span><strong>身高 {{ draft.height }} cm</strong><strong>体重 {{ draft.weight }} kg</strong><strong>{{ photoReady ? '照片已选择' : '请上传个人照片' }}</strong></div>
          <p v-if="setupError" class="dialog-error" role="alert"><Info :size="15" />{{ setupError }}</p>
           <div class="dialog-actions"><button class="dialog-back" type="button" @click="setupStep = 1">返回修改</button><button class="profile-primary" type="submit">开始分析 <Sparkles :size="15" /></button></div>
        </form>

        <div v-else class="dialog-analysis" role="status" aria-live="polite">
          <div class="analysis-spinner"><LoaderCircle class="spinning" :size="30" /></div>
           <strong>正在整理你的形象参考</strong>
           <p>正在结合照片、身高 {{ draft.height }} cm 和体重 {{ draft.weight }} kg 生成穿衣建议。</p>
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
.profile-eyebrow { display: flex; align-items: center; gap: 7px; margin: 0 0 15px; color: var(--accent-strong); font-size: 11px; font-weight: 800; letter-spacing: .07em; }
.profile-intro h2 { max-width: 300px; margin: 0; font-size: clamp(25px, 3vw, 36px); font-weight: 720; line-height: 1.12; letter-spacing: -.065em; }
.profile-intro-copy { max-width: 320px; margin: 18px 0 0; color: var(--muted); font-size: 13px; line-height: 1.78; }
.profile-action-row { display: flex; flex-wrap: wrap; gap: 9px; margin-top: 25px; }
.profile-privacy-note { display: flex; align-items: flex-start; gap: 7px; margin-top: 25px; color: var(--muted); font-size: 10px; line-height: 1.55; }
.profile-privacy-note svg { flex: 0 0 auto; color: var(--accent); }

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
.module-kicker { margin: 0 0 7px; color: var(--accent-strong); font-size: 10px; font-weight: 800; letter-spacing: .08em; }
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
  .fact-photo { display: grid; grid-template-columns: 1fr auto; align-items: center; }
  .fact-height span,
  .fact-weight span { grid-column: 1; }
  .fact-height strong,
  .fact-weight strong { grid-column: 2; grid-row: span 2; }
  .fact-height em,
  .fact-weight em { grid-column: 1; }
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
