<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Camera, Download, Info, X } from '@lucide/vue'
import { canTryOnGarment } from '../utils/liveTryOn.js'

const props = defineProps({
  items: { type: Array, required: true },
  initialItemId: { type: [Number, String], default: null }
})
const emit = defineEmits(['close', 'setup-extension'])
const dialog = ref(null)
const selectedId = ref(props.initialItemId)
const items = computed(() => props.items.filter(canTryOnGarment))
const selected = computed(() => items.value.find(item => String(item.id) === String(selectedId.value)) || items.value[0])
const imageFailed = ref(false)
let previousFocus = null

watch(() => selected.value?.imageUrl, () => { imageFailed.value = false })
onMounted(() => {
  previousFocus = document.activeElement
  dialog.value.showModal()
})
onBeforeUnmount(() => {
  dialog.value?.close()
  if (previousFocus?.isConnected) previousFocus.focus()
})
</script>

<template>
  <dialog ref="dialog" class="try-on-dialog" aria-labelledby="try-on-title" @cancel.prevent="emit('close')">
    <header class="try-on-header">
      <div><p>单品试穿</p><h2 id="try-on-title">实时试衣</h2></div>
      <button type="button" class="try-on-close" autofocus aria-label="关闭实时试衣" @click="emit('close')"><X :size="20" /></button>
    </header>
    <div class="try-on-content">
      <div class="try-on-notice" role="status">
        <Info :size="22" aria-hidden="true" />
        <div><strong>请使用配套扩展试穿</strong><p>试衣服务暂不支持在项目页面内直接播放。请使用桌面版 Chrome / Edge，安装并连接配套扩展，在独立试穿窗口中查看效果与收藏。</p></div>
      </div>
      <label class="try-on-selection">试穿单品
        <select v-model="selectedId" :disabled="!items.length">
          <option v-for="item in items" :key="item.id" :value="item.id">{{ item.name }} · {{ item.category }}</option>
        </select>
      </label>
      <img v-if="selected && !imageFailed" class="try-on-garment" :src="selected.imageUrl" :alt="selected.name" @error="imageFailed = true" />
      <p v-if="imageFailed" class="try-on-error" role="alert">这张单品图片暂时无法读取，请选择其他单品或刷新后重试。</p>
      <p v-if="selected?.imageNote" class="try-on-note">{{ selected.imageNote }}。效果仅代表所选图片的款式与配色。</p>
      <p v-if="!items.length" class="try-on-note">当前范围暂无可试穿单品。</p>
      <ol class="try-on-steps">
        <li>下载所选单品图片，并按引导安装、连接扩展。</li>
        <li>在扩展浮窗中选择这张图片，确认名称和类别，再同意开始试穿。</li>
        <li>允许摄像头，查看效果后在扩展窗口中保存喜欢的单品。</li>
      </ol>
      <div class="try-on-actions">
        <a v-if="selected && !imageFailed" class="try-on-download" :href="selected.imageUrl" download><Download :size="17" />下载所选单品图片</a>
        <button type="button" class="try-on-setup" @click="emit('setup-extension')"><Camera :size="17" />查看扩展安装引导</button>
      </div>
      <p class="try-on-note">此页面只预览单品，不会开启摄像头或向试衣服务发送图片。扩展会在你同意开始后使用 Anywear / Decart；试穿仅供款式与配色参考。</p>
    </div>
  </dialog>
</template>

<style scoped>
.try-on-dialog{width:min(620px,calc(100vw - 32px));max-height:calc(100dvh - 32px);margin:auto;padding:0;overflow:auto;border:1px solid var(--line);border-radius:12px;color:var(--ink);background:var(--surface)}
.try-on-dialog::backdrop{background:rgba(24,28,26,.55)}
.try-on-header{display:flex;align-items:center;justify-content:space-between;gap:20px;padding:20px 24px;border-bottom:1px solid var(--line)}
.try-on-header p{margin:0 0 6px;color:var(--muted);font-size:12px}.try-on-header h2{margin:0;font-size:24px}
.try-on-close{display:grid;place-items:center;width:36px;height:36px;border:1px solid var(--line);border-radius:8px;background:transparent;color:inherit;cursor:pointer}
.try-on-content{display:grid;gap:16px;padding:24px}.try-on-notice{display:flex;align-items:flex-start;gap:12px;padding:16px;border-radius:8px;background:var(--accent-soft);color:var(--accent)}
.try-on-notice>svg{flex-shrink:0}.try-on-notice strong{font-size:16px}.try-on-notice p,.try-on-note,.try-on-steps{margin:8px 0 0;color:var(--muted);font-size:13px;line-height:1.8}
.try-on-selection{display:grid;gap:8px;font-size:13px;font-weight:600}.try-on-selection select{min-width:0;width:100%;padding:10px;border:1px solid var(--line);border-radius:6px;background:var(--surface);color:inherit;font:inherit}
.try-on-garment{width:100%;height:180px;object-fit:contain;border-radius:6px;background:var(--surface-alt,#f4f2ed)}.try-on-steps{padding-left:20px}.try-on-steps li+li{margin-top:6px}
.try-on-actions{display:flex;flex-wrap:wrap;gap:12px}.try-on-download,.try-on-setup{display:inline-flex;align-items:center;justify-content:center;gap:8px;min-height:44px;padding:10px 14px;border:1px solid var(--line);border-radius:6px;background:var(--surface);color:var(--accent);font:inherit;font-size:13px;text-decoration:none;cursor:pointer}
.try-on-setup{border-color:var(--accent-strong);background:var(--accent-strong);color:var(--surface)}.try-on-error{margin:0;color:var(--danger);font-size:13px;line-height:1.8}
@media(max-width:700px){.try-on-header,.try-on-content{padding:16px}.try-on-garment{height:120px}.try-on-actions>a,.try-on-actions>button{width:100%}}
</style>
