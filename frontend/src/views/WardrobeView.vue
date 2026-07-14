<script setup>
import { computed, nextTick, onBeforeUnmount, onDeactivated, ref, watch } from 'vue'
import {
  CircleAlert,
  CircleCheck,
  Grid2X2,
  ImageOff,
  List,
  LoaderCircle,
  PackageOpen,
  Pencil,
  Plus,
  RefreshCw,
  Search,
  Shirt,
  SlidersHorizontal,
  Tags,
  Trash2,
  Upload,
  X
} from '@lucide/vue'

const props = defineProps({
  app: {
    type: Object,
    required: true
  }
})
const app = props.app

const query = ref('')
const categoryFilter = ref('all')
const colorFilter = ref('all')
const styleFilter = ref('all')
const statusFilter = ref('all')
const sortBy = ref('recent')
const viewMode = ref('grid')
const modalOpen = ref(false)
const uploadIntent = ref(false)
const modalPanel = ref(null)
const firstField = ref(null)
const fileField = ref(null)
const modalTrigger = ref(null)
const imageErrors = ref(new Set())
let previousBodyOverflow = ''

const state = computed(() => app.state)
const wardrobe = computed(() => state.value.wardrobe)
const loading = computed(() => state.value.wardrobeLoading)
const adding = computed(() => state.value.adding)
const deletingId = computed(() => state.value.deletingId)
const editingId = computed(() => state.value.editingId)
const selectedImage = computed(() => state.value.selectedImage)
const garmentForm = computed(() => state.value.garmentForm)
const wardrobeStats = computed(() => app.wardrobeStats)
const categories = computed(() => app.categories)

function categoryCount(category) {
  return wardrobeStats.value.byCategory[category]
}

function statValue(key) {
  return wardrobeStats.value[key]
}

const statCards = computed(() => [
  { label: '全部单品', value: statValue('total'), icon: Shirt },
  { label: '本周新增', value: statValue('weeklyAdded'), icon: Plus },
  { label: '可参与推荐', value: statValue('ready'), icon: CircleCheck },
  { label: '待完善', value: statValue('review'), icon: CircleAlert }
])

const categoryCards = computed(() => categories.value.map((category) => {
  const items = wardrobe.value.filter((item) => item.category === category)
  return {
    category,
    count: categoryCount(category),
    preview: items.find((item) => item.imageUrl) || items[0] || null
  }
}))

const colorOptions = computed(() => [...new Set(
  wardrobe.value.map((item) => String(item.color || '').trim()).filter(Boolean)
)].sort((a, b) => a.localeCompare(b, 'zh-CN')))

const styleOptions = computed(() => [...new Set(
  wardrobe.value.map((item) => String(item.style || '').trim()).filter(Boolean)
)].sort((a, b) => a.localeCompare(b, 'zh-CN')))

function isReviewItem(item) {
  return item?.recognitionStatus === 'NEEDS_MANUAL_REVIEW'
}

function statusLabel(item) {
  if (isReviewItem(item)) return '待完善'
  if (item?.recognitionStatus === 'MANUAL_CORRECTED') return '人工确认'
  return '可推荐'
}

const filteredItems = computed(() => {
  const normalizedQuery = query.value.trim().toLocaleLowerCase('zh-CN')
  const indexed = wardrobe.value.map((item, index) => ({ item, index }))
  const result = indexed.filter(({ item }) => {
    if (categoryFilter.value !== 'all' && item.category !== categoryFilter.value) return false
    if (colorFilter.value !== 'all' && item.color !== colorFilter.value) return false
    if (styleFilter.value !== 'all' && item.style !== styleFilter.value) return false
    if (statusFilter.value === 'ready' && isReviewItem(item)) return false
    if (statusFilter.value === 'review' && !isReviewItem(item)) return false
    if (!normalizedQuery) return true
    return [item.name, item.category, item.color, item.style]
      .filter(Boolean)
      .some((value) => String(value).toLocaleLowerCase('zh-CN').includes(normalizedQuery))
  })

  function itemTime(item) {
    const parsed = Date.parse(item.updatedAt || item.createdAt || item.addedAt || '')
    return Number.isFinite(parsed) ? parsed : null
  }

  return result.sort((left, right) => {
    if (sortBy.value === 'name') return String(left.item.name || '').localeCompare(String(right.item.name || ''), 'zh-CN')
    if (sortBy.value === 'category') return String(left.item.category || '').localeCompare(String(right.item.category || ''), 'zh-CN')
    const leftTime = itemTime(left.item)
    const rightTime = itemTime(right.item)
    if (leftTime !== null && rightTime !== null && leftTime !== rightTime) {
      return sortBy.value === 'oldest' ? leftTime - rightTime : rightTime - leftTime
    }
    return sortBy.value === 'oldest' ? right.index - left.index : left.index - right.index
  }).map(({ item }) => item)
})

const hasActiveFilters = computed(() => (
  Boolean(query.value.trim()) ||
  categoryFilter.value !== 'all' ||
  colorFilter.value !== 'all' ||
  styleFilter.value !== 'all' ||
  statusFilter.value !== 'all'
))

function itemKey(item) {
  return `${item?.id ?? item?.name ?? ''}:${item?.imageUrl ?? ''}`
}

function showImage(item) {
  return Boolean(item?.imageUrl) && !imageErrors.value.has(itemKey(item))
}

function markImageError(item) {
  const next = new Set(imageErrors.value)
  next.add(itemKey(item))
  imageErrors.value = next
}

function clearFilters() {
  query.value = ''
  categoryFilter.value = 'all'
  colorFilter.value = 'all'
  styleFilter.value = 'all'
  statusFilter.value = 'all'
}

function selectCategory(category) {
  categoryFilter.value = category
}

async function openAddModal(preferUpload = false) {
  if (typeof document !== 'undefined') modalTrigger.value = document.activeElement
  app.resetGarmentForm()
  uploadIntent.value = preferUpload
  modalOpen.value = true
  await nextTick()
  if (preferUpload && fileField.value) fileField.value.focus()
  else firstField.value?.focus()
}

async function openEditModal(item) {
  if (typeof document !== 'undefined') modalTrigger.value = document.activeElement
  app.editGarment(item)
  uploadIntent.value = false
  modalOpen.value = true
  await nextTick()
  firstField.value?.focus()
}

function closeModal() {
  if (adding.value) return
  modalOpen.value = false
  uploadIntent.value = false
  app.resetGarmentForm()
  nextTick(() => modalTrigger.value?.focus?.())
}

async function submitGarment() {
  if (adding.value) return
  const wasEditing = Boolean(editingId.value)
  const countBefore = wardrobe.value.length
  const savedItem = await app.addGarment()
  const added = wardrobe.value.length > countBefore
  const editFinished = wasEditing && !editingId.value
  if (savedItem || added || editFinished) {
    modalOpen.value = false
    uploadIntent.value = false
    nextTick(() => modalTrigger.value?.focus?.())
  }
}

function handleDialogKeydown(event) {
  if (event.key === 'Escape') {
    event.preventDefault()
    closeModal()
    return
  }
  if (event.key !== 'Tab' || !modalPanel.value) return
  const focusable = [...modalPanel.value.querySelectorAll(
    'button:not([disabled]), input:not([disabled]), select:not([disabled]), [tabindex]:not([tabindex="-1"])'
  )]
  if (!focusable.length) return
  const first = focusable[0]
  const last = focusable[focusable.length - 1]
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault()
    last.focus()
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault()
    first.focus()
  }
}

watch(modalOpen, (open) => {
  if (typeof document === 'undefined') return
  if (open) {
    previousBodyOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
  } else {
    document.body.style.overflow = previousBodyOverflow
  }
})

watch(wardrobe, (items) => {
  if (categoryFilter.value !== 'all' && !categories.value.includes(categoryFilter.value)) categoryFilter.value = 'all'
  if (colorFilter.value !== 'all' && !items.some((item) => item.color === colorFilter.value)) colorFilter.value = 'all'
  if (styleFilter.value !== 'all' && !items.some((item) => item.style === styleFilter.value)) styleFilter.value = 'all'
})

onBeforeUnmount(() => {
  if (typeof document !== 'undefined') document.body.style.overflow = previousBodyOverflow
})

onDeactivated(() => {
  modalOpen.value = false
  uploadIntent.value = false
  app.resetGarmentForm()
  if (typeof document !== 'undefined') document.body.style.overflow = previousBodyOverflow
})
</script>

<template>
  <section class="wardrobe-view" aria-labelledby="wardrobe-title">
    <header class="page-header">
      <div>
        <p class="eyebrow">数字化衣橱</p>
        <h1 id="wardrobe-title">我的衣橱</h1>
        <p class="page-intro">管理你的所有单品，让推荐从真实衣物出发。</p>
      </div>
      <div class="header-actions">
        <button type="button" class="button secondary" @click="openAddModal(true)">
          <Upload :size="17" aria-hidden="true" />
          上传单品
        </button>
        <button type="button" class="button primary" @click="openAddModal(false)">
          <Plus :size="17" aria-hidden="true" />
          添加单品
        </button>
      </div>
    </header>

    <div class="wardrobe-layout">
      <aside class="filter-rail" aria-label="衣橱分类与状态筛选">
        <div class="rail-heading">
          <div>
            <strong>我的衣橱</strong>
            <span>{{ statValue('total') }} 件单品</span>
          </div>
          <button
            type="button"
            class="icon-button"
            :disabled="loading"
            aria-label="刷新衣橱"
            title="刷新衣橱"
            @click="app.loadWardrobe()"
          >
            <LoaderCircle v-if="loading" class="spinning" :size="17" aria-hidden="true" />
            <RefreshCw v-else :size="17" aria-hidden="true" />
          </button>
        </div>

        <div class="rail-group">
          <p>单品分类</p>
          <div class="rail-options">
            <button
              type="button"
              :class="{ active: categoryFilter === 'all' }"
              :aria-pressed="categoryFilter === 'all'"
              @click="selectCategory('all')"
            >
              <span><PackageOpen :size="16" aria-hidden="true" />全部单品</span>
              <em>{{ statValue('total') }}</em>
            </button>
            <button
              v-for="category in categories"
              :key="category"
              type="button"
              :class="{ active: categoryFilter === category }"
              :aria-pressed="categoryFilter === category"
              @click="selectCategory(category)"
            >
              <span><Shirt :size="16" aria-hidden="true" />{{ category }}</span>
              <em>{{ categoryCount(category) }}</em>
            </button>
          </div>
        </div>

        <div class="rail-group compact-group">
          <p>资料状态</p>
          <div class="rail-options">
            <button
              type="button"
              :class="{ active: statusFilter === 'ready' }"
              :aria-pressed="statusFilter === 'ready'"
              @click="statusFilter = statusFilter === 'ready' ? 'all' : 'ready'"
            >
              <span><CircleCheck :size="16" aria-hidden="true" />可参与推荐</span>
              <em>{{ statValue('ready') }}</em>
            </button>
            <button
              type="button"
              :class="{ active: statusFilter === 'review' }"
              :aria-pressed="statusFilter === 'review'"
              @click="statusFilter = statusFilter === 'review' ? 'all' : 'review'"
            >
              <span><CircleAlert :size="16" aria-hidden="true" />待完善</span>
              <em>{{ statValue('review') }}</em>
            </button>
          </div>
        </div>

        <button v-if="hasActiveFilters" type="button" class="clear-rail" @click="clearFilters">
          <X :size="15" aria-hidden="true" />清除全部筛选
        </button>
      </aside>

      <div class="wardrobe-content">
        <section class="stat-strip" aria-label="衣橱统计">
          <article v-for="stat in statCards" :key="stat.label" class="stat-item">
            <div class="stat-icon"><component :is="stat.icon" :size="18" aria-hidden="true" /></div>
            <div><strong>{{ stat.value }}</strong><span>{{ stat.label }}</span></div>
          </article>
        </section>

        <section class="category-overview" aria-labelledby="category-overview-title">
          <div class="section-heading">
            <div>
              <p class="section-kicker">分类概览</p>
              <h2 id="category-overview-title">快速进入一个分类</h2>
            </div>
            <span>{{ categories.length }} 个分类</span>
          </div>
          <div v-if="categoryCards.length" class="category-grid">
            <button
              v-for="card in categoryCards"
              :key="card.category"
              type="button"
              class="category-card"
              :class="{ active: categoryFilter === card.category }"
              :aria-pressed="categoryFilter === card.category"
              @click="selectCategory(card.category)"
            >
              <span class="category-copy"><strong>{{ card.category }}</strong><em>{{ card.count }} 件</em></span>
              <img
                v-if="card.preview && showImage(card.preview)"
                :src="card.preview.imageUrl"
                :alt="card.preview.name || card.category"
                @error="markImageError(card.preview)"
              />
              <span v-else class="category-fallback"><Shirt :size="27" aria-hidden="true" /></span>
            </button>
          </div>
          <div v-else class="overview-empty">添加单品后，这里会按真实分类生成概览。</div>
        </section>

        <section id="wardrobe-collection" class="collection" aria-labelledby="collection-title">
          <div class="section-heading collection-heading">
            <div>
              <p class="section-kicker">单品集合</p>
              <h2 id="collection-title">{{ filteredItems.length }} 件符合条件</h2>
            </div>
            <button
              type="button"
              class="refresh-button"
              :disabled="loading"
              @click="app.loadWardrobe()"
            >
              <LoaderCircle v-if="loading" class="spinning" :size="15" aria-hidden="true" />
              <RefreshCw v-else :size="15" aria-hidden="true" />
              刷新
            </button>
          </div>

          <div class="toolbar" role="search">
            <label class="select-control">
              <span class="sr-only">筛选类别</span>
              <select v-model="categoryFilter">
                <option value="all">全部类别</option>
                <option v-for="category in categories" :key="category" :value="category">{{ category }}</option>
              </select>
            </label>
            <label class="select-control">
              <span class="sr-only">筛选颜色</span>
              <select v-model="colorFilter">
                <option value="all">全部颜色</option>
                <option v-for="color in colorOptions" :key="color" :value="color">{{ color }}</option>
              </select>
            </label>
            <label class="select-control">
              <span class="sr-only">筛选风格</span>
              <select v-model="styleFilter">
                <option value="all">全部风格</option>
                <option v-for="style in styleOptions" :key="style" :value="style">{{ style }}</option>
              </select>
            </label>
            <label class="select-control sort-control">
              <span class="sr-only">排序方式</span>
              <SlidersHorizontal :size="15" aria-hidden="true" />
              <select v-model="sortBy">
                <option value="recent">最近添加</option>
                <option value="oldest">最早添加</option>
                <option value="name">按名称</option>
                <option value="category">按类别</option>
              </select>
            </label>
            <label class="search-control">
              <Search :size="17" aria-hidden="true" />
              <span class="sr-only">搜索我的衣橱</span>
              <input v-model="query" type="search" placeholder="搜索名称、颜色或风格" />
              <button v-if="query" type="button" aria-label="清空搜索" @click="query = ''">
                <X :size="15" aria-hidden="true" />
              </button>
            </label>
            <div class="view-toggle" aria-label="展示方式">
              <button
                type="button"
                :class="{ active: viewMode === 'grid' }"
                :aria-pressed="viewMode === 'grid'"
                aria-label="网格展示"
                title="网格展示"
                @click="viewMode = 'grid'"
              ><Grid2X2 :size="17" aria-hidden="true" /></button>
              <button
                type="button"
                :class="{ active: viewMode === 'list' }"
                :aria-pressed="viewMode === 'list'"
                aria-label="列表展示"
                title="列表展示"
                @click="viewMode = 'list'"
              ><List :size="18" aria-hidden="true" /></button>
            </div>
          </div>

          <div v-if="loading" class="state-panel" role="status" aria-live="polite">
            <LoaderCircle class="spinning" :size="25" aria-hidden="true" />
            <strong>正在整理衣橱</strong>
            <span>单品数据返回后会显示在这里。</span>
          </div>

          <div v-else-if="wardrobe.length === 0" class="state-panel empty-state">
            <span class="state-icon"><PackageOpen :size="28" aria-hidden="true" /></span>
            <strong>衣橱还是空的</strong>
            <span>先录入一件真实单品，再开始建立你的穿搭资料。</span>
            <button type="button" class="button primary" @click="openAddModal(false)">
              <Plus :size="16" aria-hidden="true" />添加第一件单品
            </button>
          </div>

          <div v-else-if="filteredItems.length === 0" class="state-panel empty-state">
            <span class="state-icon"><Search :size="27" aria-hidden="true" /></span>
            <strong>没有符合条件的单品</strong>
            <span>换一个关键词或清除筛选条件。</span>
            <button type="button" class="button secondary" @click="clearFilters">清除筛选</button>
          </div>

          <div v-else class="garment-grid" :class="{ 'list-view': viewMode === 'list' }">
            <article v-for="item in filteredItems" :key="item.id || itemKey(item)" class="garment-card">
              <div class="garment-media">
                <img
                  v-if="showImage(item)"
                  :src="item.imageUrl"
                  :alt="item.name || '衣物图片'"
                  loading="lazy"
                  @error="markImageError(item)"
                />
                <div v-else class="image-fallback">
                  <ImageOff :size="25" aria-hidden="true" />
                  <span>暂无图片</span>
                </div>
                <span class="status-badge" :class="{ review: isReviewItem(item) }">
                  {{ statusLabel(item) }}
                </span>
                <div class="card-actions">
                  <button
                    type="button"
                    class="icon-button"
                    :aria-label="'编辑' + (item.name || '这件单品')"
                    title="编辑单品"
                    @click="openEditModal(item)"
                  ><Pencil :size="15" aria-hidden="true" /></button>
                  <button
                    type="button"
                    class="icon-button danger"
                    :disabled="deletingId === item.id"
                    :aria-label="'删除' + (item.name || '这件单品')"
                    title="删除单品"
                    @click="app.deleteGarment(item.id)"
                  >
                    <LoaderCircle v-if="deletingId === item.id" class="spinning" :size="15" aria-hidden="true" />
                    <Trash2 v-else :size="15" aria-hidden="true" />
                  </button>
                </div>
              </div>
              <div class="garment-copy">
                <div>
                  <h3>{{ item.name || '待补充衣物' }}</h3>
                  <p>{{ item.category || '未分类' }}<template v-if="item.color"> · {{ item.color }}</template></p>
                </div>
                <span v-if="item.style" class="style-tag"><Tags :size="13" aria-hidden="true" />{{ item.style }}</span>
                <small v-if="isReviewItem(item)">{{ item.recognitionMessage || '补全信息后即可用于推荐' }}</small>
              </div>
            </article>
          </div>
        </section>
      </div>
    </div>

    <div
      v-if="modalOpen"
      class="modal-backdrop"
      role="presentation"
      @click.self="closeModal"
    >
      <section
        ref="modalPanel"
        class="garment-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="garment-modal-title"
        @keydown="handleDialogKeydown"
      >
        <header class="modal-header">
          <div>
            <p class="section-kicker">{{ editingId ? '修正资料' : uploadIntent ? '上传识别' : '手动录入' }}</p>
            <h2 id="garment-modal-title">{{ editingId ? '编辑这件单品' : '添加一件单品' }}</h2>
            <p>{{ editingId ? '修改名称、类别、颜色或风格。' : '可上传实物图自动识别，也可直接填写信息。' }}</p>
          </div>
          <button
            type="button"
            class="icon-button modal-close"
            :disabled="adding"
            aria-label="关闭添加单品窗口"
            title="关闭"
            @click="closeModal"
          ><X :size="19" aria-hidden="true" /></button>
        </header>

        <form class="garment-form" @submit.prevent="submitGarment">
          <label>
            <span>单品名称</span>
            <input
              ref="firstField"
              v-model.trim="garmentForm.name"
              :required="!selectedImage"
              maxlength="120"
              placeholder="例如：米白衬衫"
              autocomplete="off"
            />
          </label>

          <div class="form-grid">
            <label>
              <span>类别</span>
              <select v-model="garmentForm.category">
                <option v-for="category in categories" :key="category" :value="category">{{ category }}</option>
              </select>
            </label>
            <label>
              <span>颜色</span>
              <input
                v-model.trim="garmentForm.color"
                :required="!selectedImage"
                maxlength="40"
                placeholder="例如：暖白"
                autocomplete="off"
              />
            </label>
          </div>

          <label>
            <span>风格 <em>可选</em></span>
            <input
              v-model.trim="garmentForm.style"
              maxlength="120"
              placeholder="例如：极简通勤"
              autocomplete="off"
            />
          </label>

          <label v-if="!editingId" class="upload-field" :class="{ emphasized: uploadIntent }">
            <span><Upload :size="16" aria-hidden="true" />衣物图片</span>
            <input
              ref="fileField"
              type="file"
              accept="image/jpeg,image/png,image/webp"
              @change="app.selectImage($event)"
            />
            <small>{{ selectedImage ? selectedImage.name : '支持 JPG、PNG、WebP，上传后沿用现有图片识别流程。' }}</small>
          </label>

          <label v-if="!editingId && !selectedImage">
            <span>图片地址 <em>可选</em></span>
            <input
              v-model.trim="garmentForm.imageUrl"
              type="url"
              maxlength="500"
              placeholder="https://..."
              autocomplete="url"
            />
          </label>

          <div v-if="editingId && garmentForm.imageUrl" class="current-image">
            <span>当前图片</span>
            <img :src="garmentForm.imageUrl" alt="当前衣物图片" />
          </div>

          <footer class="modal-actions">
            <button type="button" class="button secondary" :disabled="adding" @click="closeModal">取消</button>
            <button type="submit" class="button primary" :disabled="adding">
              <LoaderCircle v-if="adding" class="spinning" :size="17" aria-hidden="true" />
              <CircleCheck v-else :size="17" aria-hidden="true" />
              {{ adding ? '保存中' : editingId ? '保存修改' : '加入衣橱' }}
            </button>
          </footer>
        </form>
      </section>
    </div>
  </section>
</template>

<style scoped>
.wardrobe-view {
  width: min(calc(100% - 48px), var(--page-width, 1200px));
  margin: 0 auto;
  padding-bottom: 80px;
  color: var(--ink);
}

.page-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 32px;
  padding: 46px 0 30px;
}

.eyebrow,
.section-kicker {
  margin: 0 0 7px;
  color: var(--accent);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0;
}

.page-header h1 {
  max-width: 760px;
  margin: 0;
  font-family: Georgia, "Songti SC", serif;
  font-size: 32px;
  font-weight: 500;
  line-height: 1.08;
  letter-spacing: 0;
}

.page-intro {
  margin: 13px 0 0;
  color: var(--muted);
  font-size: 14px;
  line-height: 1.6;
}

.header-actions,
.modal-actions {
  display: flex;
  flex: 0 0 auto;
  gap: 10px;
}

.button,
.icon-button,
.refresh-button,
.view-toggle button,
.search-control button,
.rail-options button,
.clear-rail,
.category-card {
  font: inherit;
  cursor: pointer;
}

.button {
  display: inline-flex;
  min-height: 42px;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  padding: 9px 16px;
  font-size: 13px;
  font-weight: 700;
}

.button.primary {
  border-color: var(--ink);
  color: var(--surface);
  background: var(--ink);
}

.button.secondary {
  color: var(--ink);
  background: var(--surface);
}

.button:hover:not(:disabled),
.button:focus-visible,
.refresh-button:hover:not(:disabled),
.refresh-button:focus-visible {
  border-color: var(--accent);
}

button:disabled {
  cursor: wait;
  opacity: .58;
}

button:focus-visible,
input:focus-visible,
select:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}

.wardrobe-layout {
  display: grid;
  grid-template-columns: 196px minmax(0, 1fr);
  min-width: 0;
  gap: 30px;
  align-items: start;
}

.filter-rail {
  position: sticky;
  top: 24px;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  padding: 18px 12px 14px;
  background: var(--surface);
}

.rail-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 0 6px 16px;
  border-bottom: 1px solid var(--line);
}

.rail-heading strong,
.rail-heading span {
  display: block;
}

.rail-heading strong {
  font-family: Georgia, "Songti SC", serif;
  font-size: 18px;
  font-weight: 500;
}

.rail-heading span {
  margin-top: 4px;
  color: var(--muted);
  font-size: 11px;
}

.icon-button {
  display: inline-grid;
  width: 34px;
  height: 34px;
  flex: 0 0 auto;
  place-items: center;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  color: var(--muted);
  background: var(--surface);
}

.icon-button:hover:not(:disabled),
.icon-button:focus-visible {
  border-color: var(--accent);
  color: var(--ink);
}

.icon-button.danger:hover:not(:disabled),
.icon-button.danger:focus-visible {
  color: var(--accent);
}

.rail-group {
  padding: 17px 0;
  border-bottom: 1px solid var(--line);
}

.rail-group > p {
  margin: 0 8px 8px;
  color: var(--muted);
  font-size: 10px;
  font-weight: 700;
}

.rail-options {
  display: grid;
  gap: 3px;
}

.rail-options button {
  display: flex;
  min-height: 38px;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  border: 0;
  border-radius: var(--radius);
  padding: 8px;
  color: var(--muted);
  background: transparent;
  font-size: 12px;
}

.rail-options button > span {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 8px;
}

.rail-options button em {
  color: inherit;
  font-size: 11px;
  font-style: normal;
}

.rail-options button:hover,
.rail-options button.active {
  color: var(--ink);
  background: var(--accent-soft);
}

.compact-group {
  border-bottom: 0;
}

.clear-rail {
  display: flex;
  width: 100%;
  align-items: center;
  justify-content: center;
  gap: 6px;
  border: 0;
  padding: 9px 4px 2px;
  color: var(--accent);
  background: transparent;
  font-size: 11px;
}

.wardrobe-content {
  min-width: 0;
}

.stat-strip {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  border: 1px solid var(--line);
  border-radius: var(--radius);
  background: var(--surface);
}

.stat-item {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 13px;
  padding: 22px 20px;
}

.stat-item + .stat-item {
  border-left: 1px solid var(--line);
}

.stat-icon {
  display: grid;
  width: 36px;
  height: 36px;
  flex: 0 0 auto;
  place-items: center;
  border-radius: var(--radius);
  color: var(--accent);
  background: var(--accent-soft);
}

.stat-item strong,
.stat-item span {
  display: block;
}

.stat-item strong {
  font-family: Georgia, "Songti SC", serif;
  font-size: 27px;
  font-weight: 500;
  line-height: 1;
}

.stat-item span {
  margin-top: 6px;
  overflow: hidden;
  color: var(--muted);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.category-overview,
.collection {
  margin-top: 30px;
}

.section-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 18px;
  margin-bottom: 15px;
}

.section-heading h2 {
  margin: 0;
  font-family: Georgia, "Songti SC", serif;
  font-size: 21px;
  font-weight: 500;
  line-height: 1.2;
  letter-spacing: 0;
}

.section-heading > span {
  color: var(--muted);
  font-size: 11px;
}

.category-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(126px, 1fr));
  gap: 9px;
}

.category-card {
  position: relative;
  display: grid;
  min-height: 132px;
  overflow: hidden;
  grid-template-columns: minmax(0, .8fr) minmax(56px, 1fr);
  align-items: stretch;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  padding: 0;
  color: var(--ink);
  background: var(--surface);
  text-align: left;
}

.category-card:hover,
.category-card.active {
  border-color: var(--accent);
}

.category-copy {
  position: relative;
  z-index: 1;
  display: flex;
  min-width: 0;
  flex-direction: column;
  justify-content: space-between;
  padding: 14px 0 14px 14px;
}

.category-copy strong,
.category-copy em {
  display: block;
}

.category-copy strong {
  font-family: Georgia, "Songti SC", serif;
  font-size: 17px;
  font-weight: 500;
}

.category-copy em {
  color: var(--muted);
  font-size: 11px;
  font-style: normal;
}

.category-card img,
.category-fallback {
  width: 100%;
  height: 100%;
  min-height: 130px;
}

.category-card img {
  padding: 8px;
  object-fit: contain;
  object-position: center;
}

.category-fallback {
  display: grid;
  place-items: center;
  color: var(--muted);
  background: var(--bg);
}

.overview-empty {
  border: 1px dashed var(--line);
  border-radius: var(--radius);
  padding: 24px;
  color: var(--muted);
  background: var(--surface);
  font-size: 13px;
}

.collection-heading {
  align-items: center;
}

.refresh-button {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  padding: 7px 10px;
  color: var(--muted);
  background: var(--surface);
  font-size: 11px;
}

.toolbar {
  display: grid;
  grid-template-columns: 116px 116px 116px 130px minmax(180px, 1fr) auto;
  gap: 8px;
  margin-bottom: 18px;
}

.select-control,
.search-control {
  position: relative;
  display: flex;
  min-width: 0;
  height: 40px;
  align-items: center;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  color: var(--muted);
  background: var(--surface);
}

.select-control select,
.search-control input {
  width: 100%;
  height: 100%;
  min-width: 0;
  border: 0;
  outline: 0;
  color: var(--ink);
  background: transparent;
  font: inherit;
  font-size: 12px;
}

.select-control select {
  padding: 0 9px;
}

.sort-control {
  padding-left: 10px;
}

.sort-control select {
  padding-left: 7px;
}

.search-control {
  gap: 8px;
  padding: 0 9px;
}

.search-control input::placeholder {
  color: var(--muted);
}

.search-control button {
  display: grid;
  width: 24px;
  height: 24px;
  flex: 0 0 auto;
  place-items: center;
  border: 0;
  border-radius: var(--radius);
  color: var(--muted);
  background: transparent;
}

.view-toggle {
  display: flex;
  height: 40px;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  padding: 3px;
  background: var(--surface);
}

.view-toggle button {
  display: grid;
  width: 34px;
  place-items: center;
  border: 0;
  border-radius: var(--radius);
  color: var(--muted);
  background: transparent;
}

.view-toggle button.active {
  color: var(--ink);
  background: var(--accent-soft);
}

.garment-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
}

.garment-card {
  min-width: 0;
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  background: var(--surface);
}

.garment-media {
  position: relative;
  aspect-ratio: 4 / 4.5;
  overflow: hidden;
  background: var(--bg);
}

.garment-media img,
.image-fallback {
  width: 100%;
  height: 100%;
}

.garment-media img {
  display: block;
  padding: 12px;
  object-fit: contain;
  transition: transform .25s ease;
}

.garment-card:hover .garment-media img {
  transform: scale(1.025);
}

.image-fallback {
  display: grid;
  place-content: center;
  justify-items: center;
  gap: 7px;
  color: var(--muted);
}

.image-fallback span {
  font-size: 11px;
}

.status-badge {
  position: absolute;
  top: 9px;
  left: 9px;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  padding: 4px 7px;
  color: var(--ink);
  background: color-mix(in srgb, var(--surface) 90%, transparent);
  font-size: 10px;
}

.status-badge.review {
  color: var(--accent);
  background: var(--accent-soft);
}

.card-actions {
  position: absolute;
  top: 8px;
  right: 8px;
  display: flex;
  gap: 5px;
  opacity: 0;
  transform: translateY(-3px);
  transition: opacity .18s ease, transform .18s ease;
}

.garment-card:hover .card-actions,
.garment-card:focus-within .card-actions {
  opacity: 1;
  transform: translateY(0);
}

.card-actions .icon-button {
  width: 31px;
  height: 31px;
  background: color-mix(in srgb, var(--surface) 92%, transparent);
}

.garment-copy {
  display: grid;
  gap: 10px;
  padding: 13px;
}

.garment-copy h3 {
  margin: 0;
  overflow: hidden;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.garment-copy p,
.garment-copy small {
  margin: 5px 0 0;
  color: var(--muted);
  font-size: 10px;
  line-height: 1.5;
}

.garment-copy small {
  margin: 0;
  color: var(--accent);
}

.style-tag {
  display: inline-flex;
  width: fit-content;
  max-width: 100%;
  align-items: center;
  gap: 5px;
  overflow: hidden;
  border-radius: var(--radius);
  padding: 5px 7px;
  color: var(--muted);
  background: var(--bg);
  font-size: 10px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.garment-grid.list-view {
  grid-template-columns: 1fr;
  gap: 8px;
}

.list-view .garment-card {
  display: grid;
  grid-template-columns: 116px minmax(0, 1fr);
}

.list-view .garment-media {
  aspect-ratio: 1 / 1;
}

.list-view .garment-copy {
  align-content: center;
  grid-template-columns: minmax(0, 1fr) auto;
  padding: 14px 18px;
}

.list-view .garment-copy small {
  grid-column: 1 / -1;
}

.state-panel {
  display: grid;
  min-height: 320px;
  place-content: center;
  justify-items: center;
  gap: 9px;
  border: 1px dashed var(--line);
  border-radius: var(--radius);
  color: var(--muted);
  background: var(--surface);
  text-align: center;
}

.state-panel strong {
  color: var(--ink);
  font-family: Georgia, "Songti SC", serif;
  font-size: 20px;
  font-weight: 500;
}

.state-panel > span:not(.state-icon) {
  max-width: 320px;
  font-size: 12px;
  line-height: 1.6;
}

.state-icon {
  display: grid;
  width: 52px;
  height: 52px;
  place-items: center;
  border-radius: var(--radius);
  color: var(--accent);
  background: var(--accent-soft);
}

.state-panel .button {
  margin-top: 9px;
}

.modal-backdrop {
  position: fixed;
  z-index: 50;
  inset: 0;
  display: grid;
  overflow-y: auto;
  place-items: center;
  padding: 24px;
  background: color-mix(in srgb, var(--ink) 52%, transparent);
}

.garment-modal {
  width: min(100%, 620px);
  max-height: calc(100vh - 48px);
  overflow-y: auto;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  background: var(--surface);
}

.modal-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  padding: 24px 24px 20px;
  border-bottom: 1px solid var(--line);
}

.modal-header h2 {
  margin: 0;
  font-family: Georgia, "Songti SC", serif;
  font-size: 27px;
  font-weight: 500;
  letter-spacing: 0;
}

.modal-header p:last-child {
  margin: 8px 0 0;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.55;
}

.modal-close {
  width: 38px;
  height: 38px;
}

.garment-form {
  display: grid;
  gap: 17px;
  padding: 24px;
}

.garment-form label {
  display: grid;
  gap: 7px;
  color: var(--ink);
  font-size: 12px;
  font-weight: 700;
}

.garment-form label > span {
  display: flex;
  align-items: center;
  gap: 6px;
}

.garment-form label em {
  color: var(--muted);
  font-size: 10px;
  font-style: normal;
  font-weight: 400;
}

.garment-form input,
.garment-form select {
  width: 100%;
  min-height: 43px;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  padding: 9px 11px;
  color: var(--ink);
  background: var(--surface);
  font: inherit;
  font-size: 13px;
  font-weight: 400;
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.upload-field {
  border: 1px dashed var(--line);
  border-radius: var(--radius);
  padding: 13px;
  background: var(--bg);
}

.upload-field.emphasized {
  border-color: var(--accent);
  background: var(--accent-soft);
}

.upload-field input[type="file"] {
  min-height: auto;
  padding: 8px;
  background: var(--surface);
}

.upload-field small {
  color: var(--muted);
  font-size: 10px;
  font-weight: 400;
  line-height: 1.5;
}

.current-image {
  display: grid;
  grid-template-columns: 1fr 64px;
  align-items: center;
  gap: 12px;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  padding: 10px 12px;
  color: var(--muted);
  font-size: 11px;
}

.current-image img {
  width: 64px;
  height: 64px;
  border-radius: var(--radius);
  object-fit: contain;
}

.modal-actions {
  justify-content: flex-end;
  padding-top: 4px;
}

.spinning {
  animation: spin .9s linear infinite;
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  clip-path: inset(50%);
  white-space: nowrap;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after {
    scroll-behavior: auto !important;
    transition-duration: .01ms !important;
    animation-duration: .01ms !important;
    animation-iteration-count: 1 !important;
  }
}

@media (max-width: 1180px) {
  .toolbar {
    grid-template-columns: repeat(4, minmax(105px, 1fr)) auto;
  }

  .search-control {
    grid-column: 1 / 5;
  }

  .view-toggle {
    grid-column: 5;
    grid-row: 1 / 3;
    align-self: end;
  }

  .garment-grid {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }
}

@media (max-width: 920px) {
  .page-header {
    padding-top: 34px;
  }

  .wardrobe-layout {
    grid-template-columns: minmax(0, 1fr);
  }

  .filter-rail {
    position: static;
    min-width: 0;
    max-width: 100%;
    overflow: hidden;
  }

  .rail-heading {
    padding-bottom: 13px;
  }

  .rail-group {
    padding: 13px 0 0;
    border: 0;
  }

  .rail-options {
    display: flex;
    width: 100%;
    max-width: 100%;
    overflow-x: auto;
    padding-bottom: 2px;
  }

  .rail-options button {
    min-width: max-content;
    border: 1px solid var(--line);
  }

  .clear-rail {
    width: fit-content;
    justify-content: flex-start;
  }

  .stat-item {
    padding: 18px 14px;
  }

  .category-grid {
    width: 100%;
    max-width: 100%;
    grid-template-columns: repeat(5, 148px);
    overflow-x: auto;
    padding-bottom: 5px;
  }

  .garment-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 700px) {
  .wardrobe-view {
    width: min(calc(100% - 32px), var(--page-width, 1200px));
    padding-bottom: 60px;
  }

  .page-header {
    align-items: flex-start;
    flex-direction: column;
    gap: 22px;
  }

  .header-actions {
    width: 100%;
  }

  .header-actions .button {
    flex: 1;
  }

  .stat-strip {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .stat-item:nth-child(odd) {
    border-left: 0;
  }

  .stat-item:nth-child(n + 3) {
    border-top: 1px solid var(--line);
  }

  .toolbar {
    grid-template-columns: 1fr 1fr auto;
  }

  .select-control,
  .sort-control {
    grid-column: auto;
  }

  .search-control {
    grid-column: 1 / 3;
    grid-row: 3;
  }

  .view-toggle {
    grid-column: 3;
    grid-row: 3;
  }

  .garment-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 430px) {
  .page-header h1 {
    font-size: 30px;
  }

  .header-actions {
    display: grid;
    grid-template-columns: 1fr 1fr;
  }

  .button {
    padding-inline: 10px;
  }

  .wardrobe-layout {
    gap: 22px;
  }

  .stat-item {
    gap: 9px;
    padding: 15px 11px;
  }

  .stat-icon {
    width: 32px;
    height: 32px;
  }

  .stat-item strong {
    font-size: 23px;
  }

  .toolbar {
    grid-template-columns: 1fr 1fr;
  }

  .search-control {
    grid-column: 1 / -1;
    grid-row: auto;
  }

  .view-toggle {
    grid-column: 1 / -1;
    grid-row: auto;
    width: fit-content;
    justify-self: end;
  }

  .garment-grid {
    grid-template-columns: 1fr;
  }

  .card-actions {
    opacity: 1;
    transform: none;
  }

  .list-view .garment-card {
    grid-template-columns: 94px minmax(0, 1fr);
  }

  .list-view .garment-copy {
    display: grid;
    grid-template-columns: 1fr;
    padding: 11px;
  }

  .form-grid {
    grid-template-columns: 1fr;
  }

  .modal-backdrop {
    align-items: end;
    padding: 0;
  }

  .garment-modal {
    width: 100%;
    max-height: 94vh;
    border-right: 0;
    border-bottom: 0;
    border-left: 0;
  }

  .modal-header,
  .garment-form {
    padding: 20px;
  }
}
</style>
