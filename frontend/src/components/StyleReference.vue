<script setup>
defineProps({ reference: { type: Object, required: true } })
</script>

<template>
  <svg viewBox="0 0 280 200" role="img" :aria-label="`${reference.title}的搭配示意`" class="style-reference">
    <rect width="280" height="200" rx="12" fill="#f1eee6" />
    <ellipse cx="135" cy="181" rx="95" ry="7" fill="#d9d5ca" opacity=".45" />
    <g stroke="#59635c" stroke-width="1.4" stroke-linejoin="round">
      <path v-if="reference.shape === 'casual'" d="M56 27 72 20 102 20 120 29 137 63 119 73 109 57 111 120 64 120 66 58 55 73 38 63Z" :fill="reference.palette[0]" />
      <path v-else d="M65 26 76 20 99 20 111 26 132 49 117 64 107 54 110 120 64 120 68 54 55 64 39 49Z" :fill="reference.palette[0]" />
      <path d="M77 20Q87 35 99 20" fill="none" />
      <path v-if="reference.shape === 'casual'" d="M68 88H106V106H68Z" fill="none" opacity=".65" />
      <g v-if="reference.shape === 'smart' || reference.shape === 'expressive'">
        <path d="M64 27 78 24 82 119 59 121 62 58 50 77 35 66 48 37Z" :fill="reference.palette[1]" />
        <path d="M100 24 111 27 128 37 140 66 125 77 112 58 116 121 91 119Z" :fill="reference.palette[1]" />
        <path d="M78 24 68 51 80 59M100 24 110 51 96 59" fill="none" stroke="#d9dfd8" />
      </g>
      <path d="M161 36H213L218 152 194 157 186 73 179 157 155 152Z" :fill="reference.palette[1]" />
      <path d="M161 47H213M186 36V73" fill="none" opacity=".7" />
      <path d="M64 151 96 151 103 166Q104 175 94 177H57Q51 172 57 166Z" :fill="reference.palette[2]" />
      <path d="M112 151 141 151 151 168Q151 177 142 177H106Q99 172 106 165Z" :fill="reference.palette[2]" />
      <path d="M57 171H101M106 171H149" fill="none" stroke="#f8f5ed" stroke-width="3" />
    </g>
    <text x="16" y="184" fill="#6e756c" font-size="10">搭配示意 · 风格参考</text>
  </svg>
</template>

<style scoped>
.style-reference { display: block; width: 100%; height: auto; }
</style>
