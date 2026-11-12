<template>
  <el-select v-model="inner" filterable clearable placeholder="请选择商品（可按名称/编码搜索）" style="width:100%">
    <el-option v-for="p in options" :key="p.productId" :label="label(p)" :value="p.productId" />
    <template #empty>
      <div style="padding:8px;color:#909399;font-size:12px">暂无商品，请先在「商品登记」页创建</div>
    </template>
  </el-select>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue'
import { listProducts } from '../api/goods'

const props = defineProps({
  modelValue: { type: [Number, String], default: null },
  // 是否只显示未转移的商品（用于权属转移场景）
  excludeTransferred: { type: Boolean, default: false }
})
const emit = defineEmits(['update:modelValue'])

const options = ref([])
const inner = ref(props.modelValue)

watch(() => props.modelValue, (v) => { inner.value = v })
watch(inner, (v) => emit('update:modelValue', v))

function label(p) {
  return `${p.name}（${p.code || '无码'}）`
}

async function load() {
  try {
    const res = await listProducts()
    const all = res.data || res || []
    options.value = props.excludeTransferred ? all.filter(p => p.status !== 3) : all
  } catch (e) {
    options.value = []
  }
}

onMounted(load)
defineExpose({ reload: load })
</script>
