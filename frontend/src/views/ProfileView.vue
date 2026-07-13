<script setup>
import { computed, onBeforeUnmount, onDeactivated, ref, watch } from 'vue'
import {
  Archive,
  ArrowUpRight,
  Clock3,
  LoaderCircle,
  Palette,
  RefreshCw,
  Shirt,
  Sparkles,
  TrendingUp,
  UserRound,
  X
} from '@lucide/vue'

const props = defineProps({
  app: { type: Object, required: true }
})

const editOpen = ref(false)
const brokenImages = ref(new Set())
let previousBodyOverflow = ''

const scoreBand = computed(() => {
  const score = props.app.profileScore
  if (score === 0) return 'empty'
  if (score <= 25) return 'low'
  if (score <= 50) return 'mid'
  if (score <= 75) return 'high'
  return 'full'
})
const colorSuggestions = computed(() => props.app.state.profile?.colorSuggestions || [])
const styleReferences = computed(() => props.app.state.trends.slice(0, 4))
const averageRating = computed(() => props.app.recommendationStats.averageRating)

function formatDate(value) {
  if (!value) return '时间未记录'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '时间未记录'
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  }).format(date)
}

function formatRating(value) {
  return Number.isFinite(value) ? value.toFixed(1) : '--'
}

function imageFailed(key) {
  brokenImages.value.add(key)
}

async function submitProfile() {
  const saved = await props.app.saveProfile()
  if (saved) editOpen.value = false
}

watch(editOpen, (open) => {
  if (typeof document === 'undefined') return
  if (open) {
    previousBodyOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
  } else {
    document.body.style.overflow = previousBodyOverflow
  }
})

onBeforeUnmount(() => {
  if (typeof document !== 'undefined') document.body.style.overflow = previousBodyOverflow
})

onDeactivated(() => {
  editOpen.value = false
  if (typeof document !== 'undefined') document.body.style.overflow = previousBodyOverflow
})
</script>

<template>
  <section class="profile-view">
    <div v-if="app.state.profileLoading && !app.state.profile" class="state-panel" aria-live="polite">
      <LoaderCircle class="spinning" :size="28" />
      <strong>正在读取个人风格档案</strong>
      <span>档案返回后会按真实字段计算完整度。</span>
    </div>

    <template v-else-if="app.state.profile">
      <section class="profile-hero" aria-labelledby="profile-title">
        <div class="hero-copy">
          <p class="eyebrow"><UserRound :size="15" aria-hidden="true" />个人风格档案</p>
          <h1 id="profile-title">{{ app.state.profile.displayName }}的个人形象</h1>
          <p>{{ app.state.profile.reasonSummary }}</p>
          <div class="hero-actions">
            <button class="primary-action" type="button" @click="editOpen = true"><RefreshCw :size="16" />更新档案</button>
            <button class="secondary-action" type="button" :disabled="app.state.profileLoading" @click="app.loadProfile">
              <LoaderCircle v-if="app.state.profileLoading" class="spinning" :size="16" />
              <RefreshCw v-else :size="16" />
              重新读取
            </button>
          </div>
        </div>

        <div class="profile-identity">
          <div class="identity-group">
            <span>偏好风格</span>
            <div class="tag-row">
              <strong v-for="tag in app.state.profile.stylePreferences" :key="`preference-${tag}`">{{ tag }}</strong>
              <em v-if="!app.state.profile.stylePreferences.length">暂未填写</em>
            </div>
          </div>
          <div class="identity-group">
            <span>常用场合</span>
            <div class="tag-row">
              <strong v-for="occasion in app.state.profile.occasions" :key="`occasion-${occasion}`">{{ occasion }}</strong>
              <em v-if="!app.state.profile.occasions.length">暂未填写</em>
            </div>
          </div>
          <div class="identity-group">
            <span>档案状态</span>
            <p>{{ app.state.profile.stale ? '档案已过期，建议更新' : '档案已同步' }}</p>
            <small>{{ formatDate(app.state.profile.generatedAt) }}</small>
          </div>
        </div>

        <aside class="score-panel" aria-label="档案完整度">
          <div class="score-ring" :class="scoreBand">
            <span>{{ app.profileScore }}</span>
            <small>/ 100</small>
          </div>
          <h2>档案完整度</h2>
          <p>仅表示偏好、颜色、场合与档案状态字段的填写情况，不是 AI 风格评分。</p>
        </aside>
      </section>

      <section class="profile-analysis" aria-label="档案分析">
        <div class="dimension-panel">
          <header class="section-heading">
            <div>
              <p class="section-kicker">完整度维度</p>
              <h2>哪些信息已经足够明确</h2>
            </div>
            <UserRound :size="22" aria-hidden="true" />
          </header>

          <div class="dimension-list">
            <div v-for="dimension in app.profileDimensions" :key="dimension.label">
              <span>{{ dimension.label }}<strong>{{ dimension.value }}%</strong></span>
              <progress :value="dimension.value" max="100" :aria-label="`${dimension.label}完整度 ${dimension.value}%`">{{ dimension.value }}%</progress>
            </div>
          </div>

          <div class="profile-meta">
            <div><span>档案来源</span><strong>{{ app.state.profile.modelName }}</strong></div>
            <div><span>生成时间</span><strong>{{ formatDate(app.state.profile.generatedAt) }}</strong></div>
          </div>
        </div>

        <div class="color-panel">
          <header class="section-heading">
            <div>
              <p class="section-kicker">颜色建议</p>
              <h2>后端档案给出的适配色</h2>
            </div>
            <Palette :size="22" aria-hidden="true" />
          </header>

          <div v-if="colorSuggestions.length" class="swatch-list">
            <div v-for="color in colorSuggestions" :key="color">
              <span class="swatch" :style="{ backgroundColor: app.colorFor(color) }" :title="color"></span>
              <strong>{{ color }}</strong>
            </div>
          </div>
          <p v-else class="inline-empty">档案尚未返回颜色建议。</p>

          <div class="preference-copy">
            <span>已填写颜色偏好</span>
            <p>{{ app.state.profile.colorPreferences.length ? app.state.profile.colorPreferences.join('、') : '暂未填写' }}</p>
          </div>
        </div>

        <div class="suggestion-panel">
          <header class="section-heading">
            <div>
              <p class="section-kicker">风格建议</p>
              <h2>下一套可以从这里开始</h2>
            </div>
            <Sparkles :size="22" aria-hidden="true" />
          </header>

          <div class="suggestion-block">
            <span>档案标签</span>
            <div class="tag-row">
              <strong v-for="tag in app.state.profile.styleTags" :key="`style-${tag}`">{{ tag }}</strong>
              <em v-if="!app.state.profile.styleTags.length">暂无标签</em>
            </div>
          </div>
          <div class="suggestion-block">
            <span>可以尝试</span>
            <div class="tag-row alternate">
              <strong v-for="tag in app.state.profile.tryStyleTags" :key="`try-${tag}`">{{ tag }}</strong>
              <em v-if="!app.state.profile.tryStyleTags.length">暂无建议</em>
            </div>
          </div>
          <div class="item-suggestions">
            <span>单品方向</span>
            <p>{{ app.state.profile.itemSuggestions.length ? app.state.profile.itemSuggestions.join(' · ') : '暂无单品建议' }}</p>
          </div>
        </div>
      </section>

      <section class="data-section" aria-labelledby="profile-data-title">
        <header class="section-heading wide-heading">
          <div>
            <p class="section-kicker">真实使用数据</p>
            <h2 id="profile-data-title">衣橱与推荐反馈</h2>
          </div>
          <span>所有数值均来自当前服务端状态</span>
        </header>

        <div class="data-band">
          <article class="data-group">
            <header><Shirt :size="19" /><div><span>我的衣橱</span><strong>{{ app.wardrobeStats.total }} 件</strong></div></header>
            <dl>
              <div><dt>信息可用</dt><dd>{{ app.wardrobeStats.ready }}</dd></div>
              <div><dt>待完善</dt><dd>{{ app.wardrobeStats.review }}</dd></div>
              <div><dt>近 7 天新增</dt><dd>{{ app.wardrobeStats.weeklyAdded }}</dd></div>
            </dl>
            <button type="button" @click="app.selectView('wardrobe')">查看衣橱 <ArrowUpRight :size="15" /></button>
          </article>

          <article class="data-group">
            <header><Archive :size="19" /><div><span>推荐记录</span><strong>{{ app.recommendationStats.total }} 条</strong></div></header>
            <dl>
              <div><dt>已收藏</dt><dd>{{ app.recommendationStats.saved }}</dd></div>
              <div><dt>平均评分</dt><dd>{{ formatRating(averageRating) }}</dd></div>
              <div><dt>覆盖衣物</dt><dd>{{ app.recommendationStats.coveredItems }}</dd></div>
            </dl>
            <button type="button" @click="app.selectView('history')">查看历史 <ArrowUpRight :size="15" /></button>
          </article>
        </div>
      </section>

      <section class="reference-section" aria-labelledby="style-reference-title">
        <header class="section-heading wide-heading">
          <div>
            <p class="section-kicker">实时风格参照</p>
            <h2 id="style-reference-title">来自当前趋势数据的视觉线索</h2>
          </div>
          <button class="text-action" type="button" @click="app.selectView('trend')">查看全部风潮 <TrendingUp :size="16" /></button>
        </header>

        <div v-if="styleReferences.length" class="reference-grid">
          <article v-for="trend in styleReferences" :key="trend.id">
            <div class="reference-media">
              <img
                v-if="trend.imageUrl && !brokenImages.has(trend.id)"
                :src="trend.imageUrl"
                :alt="trend.title"
                @error="imageFailed(trend.id)"
              />
              <span v-else><TrendingUp :size="28" /></span>
            </div>
            <div class="reference-copy">
              <small>{{ trend.platform }} · 热度 {{ trend.heatScore }}</small>
              <strong>{{ trend.title }}</strong>
              <p>{{ (trend.topicTags || []).slice(0, 3).join(' · ') || '暂无标签' }}</p>
            </div>
            <a :href="trend.sourceUrl" target="_blank" rel="noreferrer" :aria-label="`查看${trend.title}来源`"><ArrowUpRight :size="17" /></a>
          </article>
        </div>
        <div v-else class="reference-empty"><TrendingUp :size="25" /><span>暂无可用趋势数据，档案本身仍可正常编辑。</span></div>
      </section>

      <section class="timeline-section" aria-labelledby="profile-timeline-title">
        <header class="section-heading wide-heading">
          <div>
            <p class="section-kicker">档案记录</p>
            <h2 id="profile-timeline-title">当前档案的可验证节点</h2>
          </div>
          <Clock3 :size="21" aria-hidden="true" />
        </header>
        <div class="timeline-row">
          <article><span></span><strong>偏好已保存</strong><p>{{ app.state.profile.stylePreferences.length }} 个风格方向 · {{ app.state.profile.colorPreferences.length }} 个颜色方向</p></article>
          <article><span></span><strong>场合已记录</strong><p>{{ app.state.profile.occasions.length }} 个常用场合</p></article>
          <article><span></span><strong>{{ app.state.profile.stale ? '等待更新' : '档案已同步' }}</strong><p>{{ formatDate(app.state.profile.generatedAt) }}</p></article>
        </div>
      </section>
    </template>

    <div v-else class="state-panel empty-state">
      <UserRound :size="30" />
      <strong>还没有可显示的风格档案</strong>
      <span>先重新读取服务端档案；若仍为空，可以填写基础偏好生成档案。</span>
      <div><button type="button" @click="app.loadProfile">重新读取</button><button type="button" @click="editOpen = true">填写档案</button></div>
    </div>

    <div v-if="editOpen" class="modal-backdrop" @click.self="editOpen = false" @keydown.esc="editOpen = false">
      <section class="profile-modal" role="dialog" aria-modal="true" aria-labelledby="profile-form-title">
        <header>
          <div><p class="section-kicker">更新档案</p><h2 id="profile-form-title">把偏好写得具体一点</h2></div>
          <button type="button" aria-label="关闭档案编辑" @click="editOpen = false"><X :size="19" /></button>
        </header>

        <form @submit.prevent="submitProfile">
          <label>
            <span>称呼</span>
            <input v-model.trim="app.state.profileForm.displayName" required maxlength="80" placeholder="例如：小林" />
          </label>
          <label>
            <span>风格偏好</span>
            <input v-model.trim="app.state.profileForm.stylePreferences" maxlength="240" placeholder="用逗号分隔，例如：极简、通勤" />
          </label>
          <label>
            <span>颜色偏好</span>
            <input v-model.trim="app.state.profileForm.colorPreferences" maxlength="240" placeholder="用逗号分隔，例如：暖白、雾蓝" />
          </label>
          <label>
            <span>常用场合</span>
            <input v-model.trim="app.state.profileForm.occasions" maxlength="240" placeholder="用逗号分隔，例如：通勤、约会" />
          </label>
          <p>保存后由现有后端接口刷新档案；未填写的列表会按后端规则处理。</p>
          <div class="modal-actions">
            <button type="button" @click="editOpen = false">取消</button>
            <button class="primary-action" type="submit" :disabled="app.state.profileSaving">
              <LoaderCircle v-if="app.state.profileSaving" class="spinning" :size="17" />
              <Sparkles v-else :size="17" />
              {{ app.state.profileSaving ? '正在保存' : '保存并刷新档案' }}
            </button>
          </div>
        </form>
      </section>
    </div>
  </section>
</template>

<style scoped>
.profile-view {
  width: min(1180px, 100%);
  margin: 0 auto;
  padding: 58px 0 78px;
  color: var(--ink);
}

.profile-hero {
  display: grid;
  grid-template-columns: 1.15fr .85fr 280px;
  gap: 34px;
  align-items: stretch;
  min-height: 380px;
  border-bottom: 1px solid var(--line);
  padding: 12px 0 52px;
}

.hero-copy { align-self: center; }
.eyebrow,
.section-kicker {
  display: flex;
  align-items: center;
  gap: 7px;
  margin: 0 0 12px;
  color: var(--accent);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0;
}

.hero-copy h1 {
  margin: 0;
  font-family: Georgia, "Songti SC", serif;
  font-size: 47px;
  font-weight: 500;
  line-height: 1.1;
  letter-spacing: 0;
}

.hero-copy > p:last-of-type { max-width: 520px; margin: 20px 0 0; color: var(--muted); font-size: 13px; line-height: 1.8; }
.hero-actions { display: flex; flex-wrap: wrap; gap: 10px; margin-top: 26px; }
.primary-action,
.secondary-action,
.empty-state button {
  display: inline-flex;
  min-height: 42px;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border: 1px solid var(--ink);
  border-radius: 5px;
  padding: 9px 15px;
  color: var(--surface);
  background: var(--ink);
  font-size: 12px;
  font-weight: 700;
}

.secondary-action,
.empty-state button:first-child { color: var(--ink); background: var(--surface); }
.profile-identity {
  display: grid;
  align-content: center;
  border-left: 1px solid var(--line);
  padding-left: 34px;
}

.identity-group { padding: 19px 0; border-bottom: 1px solid var(--line); }
.identity-group:last-child { border-bottom: 0; }
.identity-group > span,
.suggestion-block > span,
.item-suggestions > span,
.preference-copy > span { display: block; margin-bottom: 9px; color: var(--muted); font-size: 10px; }
.identity-group p { margin: 0; font-size: 13px; font-weight: 700; }
.identity-group small { display: block; margin-top: 5px; color: var(--muted); font-size: 10px; }
.tag-row { display: flex; flex-wrap: wrap; gap: 6px; }
.tag-row strong {
  border: 1px solid var(--line);
  border-radius: 3px;
  padding: 6px 8px;
  background: var(--surface);
  font-size: 10px;
  font-weight: 650;
}

.tag-row.alternate strong { color: var(--accent); background: var(--accent-soft); }
.tag-row em { color: var(--muted); font-size: 11px; font-style: normal; }
.score-panel {
  display: grid;
  place-items: center;
  place-content: center;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  padding: 25px;
  background: var(--surface);
  text-align: center;
}

.score-ring {
  display: flex;
  width: 152px;
  height: 152px;
  align-items: baseline;
  justify-content: center;
  border: 11px solid var(--line);
  border-radius: 50%;
  padding-top: 48px;
}

.score-ring.low { border-top-color: var(--accent); }
.score-ring.mid { border-top-color: var(--accent); border-right-color: var(--accent); }
.score-ring.high { border-top-color: var(--accent); border-right-color: var(--accent); border-bottom-color: var(--accent); }
.score-ring.full { border-color: var(--accent); }
.score-ring span { font-family: Georgia, serif; font-size: 39px; line-height: 1; }
.score-ring small { margin-left: 4px; color: var(--muted); font-size: 10px; }
.score-panel h2 { margin: 19px 0 8px; font-family: Georgia, "Songti SC", serif; font-size: 20px; font-weight: 500; }
.score-panel p { margin: 0; color: var(--muted); font-size: 10px; line-height: 1.65; }

.profile-analysis {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  border-bottom: 1px solid var(--line);
  padding: 54px 0;
}

.dimension-panel,
.color-panel,
.suggestion-panel { min-width: 0; padding: 0 28px; }
.dimension-panel { padding-left: 0; }
.suggestion-panel { padding-right: 0; }
.color-panel,
.suggestion-panel { border-left: 1px solid var(--line); }
.section-heading {
  display: flex;
  align-items: start;
  justify-content: space-between;
  gap: 18px;
}

.section-heading h2 {
  margin: 0;
  font-family: Georgia, "Songti SC", serif;
  font-size: 23px;
  font-weight: 500;
  line-height: 1.25;
  letter-spacing: 0;
}

.section-heading > svg { flex: 0 0 auto; color: var(--accent); }
.dimension-list { display: grid; gap: 17px; margin-top: 27px; }
.dimension-list > div { display: grid; gap: 7px; }
.dimension-list span { display: flex; justify-content: space-between; gap: 12px; color: var(--muted); font-size: 10px; }
.dimension-list strong { color: var(--ink); }
.dimension-list progress { width: 100%; height: 5px; border: 0; background: var(--line); }
.dimension-list progress::-webkit-progress-bar { background: var(--line); }
.dimension-list progress::-webkit-progress-value { background: var(--accent); }
.dimension-list progress::-moz-progress-bar { background: var(--accent); }
.profile-meta { display: grid; gap: 12px; margin-top: 25px; border-top: 1px solid var(--line); padding-top: 17px; }
.profile-meta div { display: flex; align-items: start; justify-content: space-between; gap: 15px; }
.profile-meta span { color: var(--muted); font-size: 9px; }
.profile-meta strong { max-width: 70%; overflow-wrap: anywhere; text-align: right; font-size: 9px; font-weight: 600; }
.swatch-list { display: grid; grid-template-columns: repeat(2, 1fr); gap: 17px 13px; margin-top: 29px; }
.swatch-list > div { display: grid; grid-template-columns: 35px minmax(0, 1fr); gap: 9px; align-items: center; }
.swatch { width: 35px; height: 35px; border: 1px solid rgba(0, 0, 0, .1); border-radius: 50%; }
.swatch-list strong { font-size: 11px; }
.preference-copy,
.item-suggestions { margin-top: 25px; border-top: 1px solid var(--line); padding-top: 17px; }
.preference-copy p,
.item-suggestions p { margin: 0; color: var(--muted); font-size: 11px; line-height: 1.65; }
.inline-empty { margin: 30px 0 0; color: var(--muted); font-size: 11px; }
.suggestion-block { margin-top: 25px; }

.data-section,
.reference-section,
.timeline-section { padding-top: 60px; }
.wide-heading { align-items: end; }
.wide-heading > span { color: var(--muted); font-size: 10px; }
.text-action,
.data-group > button {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: 0;
  padding: 4px 0;
  color: var(--ink);
  background: transparent;
  font-size: 11px;
  font-weight: 650;
}

.data-band {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  margin-top: 24px;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  background: var(--surface);
}

.data-group { min-width: 0; padding: 27px; }
.data-group + .data-group { border-left: 1px solid var(--line); }
.data-group header { display: flex; align-items: center; gap: 12px; }
.data-group header > svg { color: var(--accent); }
.data-group header div { display: grid; gap: 3px; }
.data-group header span { color: var(--muted); font-size: 10px; }
.data-group header strong { font-family: Georgia, serif; font-size: 22px; font-weight: 500; }
.data-group dl { display: grid; grid-template-columns: repeat(3, 1fr); margin: 22px 0; border-top: 1px solid var(--line); border-bottom: 1px solid var(--line); }
.data-group dl div { padding: 14px 12px 14px 0; }
.data-group dl div + div { border-left: 1px solid var(--line); padding-left: 12px; }
.data-group dt { color: var(--muted); font-size: 9px; }
.data-group dd { margin: 6px 0 0; font-family: Georgia, serif; font-size: 20px; }

.reference-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-top: 24px;
}

.reference-grid article {
  position: relative;
  min-width: 0;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  overflow: hidden;
  background: var(--surface);
}

.reference-media { height: 170px; background: var(--accent-soft); }
.reference-media img { width: 100%; height: 100%; object-fit: cover; }
.reference-media > span { display: grid; width: 100%; height: 100%; place-items: center; color: var(--accent); }
.reference-copy { padding: 15px; }
.reference-copy small { color: var(--accent); font-size: 9px; }
.reference-copy strong { display: block; overflow: hidden; margin-top: 7px; font-family: Georgia, "Songti SC", serif; font-size: 17px; font-weight: 500; text-overflow: ellipsis; white-space: nowrap; }
.reference-copy p { overflow: hidden; margin: 7px 0 0; color: var(--muted); font-size: 9px; text-overflow: ellipsis; white-space: nowrap; }
.reference-grid a {
  position: absolute;
  top: 10px;
  right: 10px;
  display: grid;
  width: 32px;
  height: 32px;
  place-items: center;
  border-radius: 50%;
  color: var(--ink);
  background: var(--surface);
}

.reference-empty { display: flex; min-height: 180px; align-items: center; justify-content: center; gap: 9px; margin-top: 24px; border-top: 1px solid var(--line); border-bottom: 1px solid var(--line); color: var(--muted); font-size: 11px; }
.timeline-section { padding-bottom: 10px; }
.timeline-row { display: grid; grid-template-columns: repeat(3, 1fr); margin-top: 26px; border-top: 1px solid var(--line); }
.timeline-row article { position: relative; min-width: 0; padding: 25px 24px 0 0; }
.timeline-row article + article { padding-left: 24px; }
.timeline-row article > span { position: absolute; top: -5px; left: 0; width: 10px; height: 10px; border: 2px solid var(--bg); border-radius: 50%; background: var(--accent); }
.timeline-row article + article > span { left: 24px; }
.timeline-row strong { font-family: Georgia, "Songti SC", serif; font-size: 16px; font-weight: 500; }
.timeline-row p { margin: 7px 0 0; color: var(--muted); font-size: 10px; line-height: 1.6; }

.state-panel {
  display: grid;
  min-height: 480px;
  place-items: center;
  place-content: center;
  gap: 11px;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  color: var(--muted);
  background: var(--surface);
  text-align: center;
}

.state-panel > svg { color: var(--accent); }
.state-panel strong { color: var(--ink); font-family: Georgia, "Songti SC", serif; font-size: 22px; font-weight: 500; }
.state-panel > span { max-width: 430px; font-size: 11px; line-height: 1.7; }
.empty-state > div { display: flex; gap: 9px; margin-top: 8px; }
.modal-backdrop {
  position: fixed;
  z-index: 30;
  inset: 0;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgba(16, 17, 16, .55);
}

.profile-modal {
  width: min(560px, 100%);
  max-height: calc(100vh - 48px);
  overflow-y: auto;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  padding: 26px;
  background: var(--surface);
  box-shadow: 0 24px 60px rgba(0, 0, 0, .18);
}

.profile-modal > header { display: flex; align-items: start; justify-content: space-between; gap: 20px; }
.profile-modal h2 { margin: 0; font-family: Georgia, "Songti SC", serif; font-size: 27px; font-weight: 500; }
.profile-modal > header > button { display: grid; width: 36px; height: 36px; place-items: center; border: 1px solid var(--line); border-radius: 50%; color: var(--ink); background: var(--surface); }
.profile-modal form { display: grid; gap: 16px; margin-top: 25px; }
.profile-modal label { display: grid; gap: 7px; }
.profile-modal label span { color: var(--muted); font-size: 11px; font-weight: 650; }
.profile-modal input { width: 100%; min-height: 44px; border: 1px solid var(--line); border-radius: 4px; outline: 0; padding: 10px 12px; color: var(--ink); background: var(--bg); font-size: 13px; }
.profile-modal input:focus { border-color: var(--accent); box-shadow: 0 0 0 3px var(--accent-soft); }
.profile-modal form > p { margin: 0; color: var(--muted); font-size: 10px; line-height: 1.6; }
.modal-actions { display: flex; justify-content: flex-end; gap: 9px; border-top: 1px solid var(--line); padding-top: 19px; }
.modal-actions > button { min-height: 40px; border: 1px solid var(--line); border-radius: 5px; padding: 8px 14px; color: var(--ink); background: var(--surface); font-size: 11px; }
.modal-actions .primary-action { border-color: var(--ink); color: var(--surface); background: var(--ink); }
.spinning { animation: spin 1s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

@media (max-width: 1000px) {
  .profile-hero { grid-template-columns: 1.2fr .8fr; }
  .score-panel { grid-column: 1 / -1; min-height: 240px; }
  .profile-analysis { grid-template-columns: repeat(2, 1fr); }
  .suggestion-panel { grid-column: 1 / -1; border-top: 1px solid var(--line); border-left: 0; padding: 32px 0 0; }
  .reference-grid { grid-template-columns: repeat(2, 1fr); }
}

@media (max-width: 720px) {
  .profile-view { padding-top: 39px; }
  .profile-hero { grid-template-columns: 1fr; gap: 28px; }
  .hero-copy h1 { font-size: 36px; }
  .hero-actions { display: grid; grid-template-columns: 1fr 1fr; }
  .profile-identity { border-top: 1px solid var(--line); border-left: 0; padding: 10px 0 0; }
  .score-panel { grid-column: auto; }
  .profile-analysis { grid-template-columns: 1fr; }
  .dimension-panel,
  .color-panel,
  .suggestion-panel { border-left: 0; padding: 0; }
  .color-panel,
  .suggestion-panel { border-top: 1px solid var(--line); margin-top: 32px; padding-top: 32px; }
  .data-band { grid-template-columns: 1fr; }
  .data-group + .data-group { border-top: 1px solid var(--line); border-left: 0; }
  .reference-grid { grid-template-columns: 1fr; }
  .reference-media { height: 230px; }
  .timeline-row { grid-template-columns: 1fr; border-top: 0; border-left: 1px solid var(--line); }
  .timeline-row article,
  .timeline-row article + article { padding: 0 0 27px 25px; }
  .timeline-row article > span,
  .timeline-row article + article > span { top: 2px; left: -5px; }
  .wide-heading { align-items: start; }
  .wide-heading > span { display: none; }
}

@media (max-width: 480px) {
  .hero-actions { grid-template-columns: 1fr; }
  .data-group dl { grid-template-columns: 1fr; }
  .data-group dl div { display: flex; align-items: center; justify-content: space-between; padding: 10px 0; }
  .data-group dl div + div { border-top: 1px solid var(--line); border-left: 0; padding-left: 0; }
  .data-group dd { margin: 0; }
  .empty-state > div { align-items: stretch; flex-direction: column; }
  .modal-actions { align-items: stretch; flex-direction: column-reverse; }
  .modal-actions > button { width: 100%; }
}
</style>
