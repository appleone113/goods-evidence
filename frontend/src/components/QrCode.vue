<template>
  <div class="qr-box">
    <img v-if="resolved" :src="resolved" :width="size" :height="size" alt="二维码" />
    <span v-else>无码</span>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  // 支持三种：图片 URL（推荐）、纯 Base64、data URI
  src: { type: String, default: '' },
  size: { type: Number, default: 200 }
})

const resolved = computed(() => {
  const s = (props.src || '').trim()
  if (!s) return ''
  if (s.startsWith('http') || s.startsWith('/') || s.startsWith('data:')) return s
  // 形如 /api/file/xxx 之外的裸 Base64（ZXing 生成的是标准 PNG base64）
  return 'data:image/png;base64,' + s
})
</script>

<style scoped>
.qr-box { display:inline-block; border:1px solid #eee; padding:8px; border-radius:6px; }
</style>
