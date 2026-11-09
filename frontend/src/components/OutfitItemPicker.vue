<script setup>
import { computed, ref } from 'vue'
const props = defineProps({ item: { type: Object, required: true }, wardrobe: { type: Array, required: true }, selected: { type: Array, required: true } })
const emit = defineEmits(['select', 'close'])
const error = ref('')
const loading = ref(false)
const candidates = computed(() => props.wardrobe.filter(item => item.category === props.item.category && !props.selected.some(selected => selected.id === item.id)))
async function upload(event) {
  const file = event.target.files?.[0]
  if (!file) return
  error.value = ''
  if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type) || file.size > 2 * 1024 * 1024) {
    error.value = '请选择不超过2MB的 JPG、PNG 或 WebP 图片。'
    event.target.value = ''
    return
  }
  loading.value = true
  try {
    const data = await new Promise((resolve, reject) => {
      const reader = new FileReader()
      reader.onload = () => resolve(reader.result)
      reader.onerror = reject
      reader.readAsDataURL(file)
    })
    const image = new Image()
    image.src = data
    await image.decode()
    if (image.width < 240 || image.height < 240 || image.width > 8000 || image.height > 8000) throw new Error()
    emit('select', { id: `upload-${crypto.randomUUID()}`, name: file.name.slice(0, 120), category: props.item.category, imageUrl: data, imageData: data, color: '', style: '', uploaded: true })
  } catch {
    error.value = '图片无法读取，请使用宽高为240至8000像素的有效图片。'
  } finally { loading.value = false }
}
</script>

<template>
  <section class="item-picker" aria-labelledby="item-picker-title" :aria-busy="loading">
    <header><h3 id="item-picker-title">更换{{ item.category }}单品</h3><button type="button" :disabled="loading" @click="emit('close')">取消更换</button></header>
    <p>从衣橱选择，或上传用于本次搭配的图片。</p>
    <label class="upload-label">上传单品图片<input type="file" accept="image/jpeg,image/png,image/webp" :disabled="loading" @change="upload" /></label>
    <small>JPG、PNG、WebP，最大2MB，宽高240至8000像素。上传图片仅保留在本次编辑中。</small>
    <p v-if="loading" role="status">正在读取图片…</p>
    <p v-if="error" role="alert">{{ error }}</p>
    <div class="picker-items">
      <button v-for="candidate in candidates" :key="candidate.id" type="button" :disabled="loading" @click="emit('select', candidate)">
        <img v-if="candidate.imageUrl" :src="candidate.imageUrl" alt="" /><span>{{ candidate.name }}</span>
      </button>
    </div>
    <p v-if="!candidates.length">衣橱中暂无其他同类单品，可上传图片更换。</p>
  </section>
</template>

<style scoped>
.item-picker { padding: 24px; margin: 16px 0; border: 1px solid var(--line); border-radius: 12px; background: var(--surface); color: var(--ink); }
header { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
h3, p { margin: 0 0 16px; }
button { min-height: 44px; padding: 10px; border: 1px solid var(--line-strong); border-radius: 8px; background: var(--surface); color: inherit; cursor: pointer; font: inherit; }
button:focus-visible, input:focus-visible { outline: 2px solid var(--accent); outline-offset: 3px; }
.upload-label { display: grid; gap: 10px; margin-bottom: 12px; font-weight: 600; }
input { max-width: 100%; }
small { display: block; line-height: 1.6; }
.picker-items { display: grid; grid-template-columns: repeat(auto-fill, minmax(120px, 1fr)); gap: 12px; margin-top: 20px; }
.picker-items button { display: grid; gap: 10px; overflow-wrap: anywhere; }
img { width: 100%; height: 120px; object-fit: contain; }
[role="alert"] { color: var(--coral); }
button:disabled { opacity: .5; cursor: wait; }
</style>
