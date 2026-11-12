<template>
  <el-card>
    <template #header>
      <div style="display:flex;justify-content:space-between;align-items:center">
        <span>商品全生命周期履历</span>
        <!-- 商品ID 是 19 位雪花ID，不能用 .number（会触发 JS 精度丢失），保持字符串 -->
        <el-input v-model="inputId" placeholder="输入商品ID" size="small" style="width:200px">
          <template #append><el-button size="small" @click="onQuery">查询</el-button></template>
        </el-input>
      </div>
    </template>

    <!-- 商品概览 -->
    <el-descriptions v-if="vo" border :column="3" style="margin-bottom:16px">
      <el-descriptions-item label="商品ID">{{ vo.productId }}</el-descriptions-item>
      <el-descriptions-item label="商品名称">{{ vo.name }}</el-descriptions-item>
      <el-descriptions-item label="当前状态">
        <el-tag :type="statusTag(vo.status)">{{ statusText(vo.status) }}</el-tag>
      </el-descriptions-item>
    </el-descriptions>

    <el-empty v-if="!loading && !vo" description="暂无履历数据，请输入商品ID查询" />

    <!-- 履历时间线 -->
    <el-timeline v-if="vo && vo.events && vo.events.length">
      <el-timeline-item
        v-for="(e, i) in vo.events"
        :key="i"
        :timestamp="fmtTime(e.time)"
        :type="typeColor(e.type)"
        placement="top"
      >
        <el-card shadow="never" style="margin-bottom:4px">
          <template #header>
            <div style="display:flex;justify-content:space-between;align-items:center">
              <span style="font-weight:bold">{{ typeText(e.type) }}</span>
              <el-tag size="small" effect="plain">{{ e.type }}</el-tag>
            </div>
          </template>
          <div>{{ e.description }}</div>
          <div style="color:#909399;font-size:12px;margin-top:6px">操作方：{{ e.operator || '-' }}</div>
        </el-card>
      </el-timeline-item>
    </el-timeline>

    <el-empty v-else-if="vo" description="该商品暂无履历事件" />
  </el-card>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getTrace } from '../../api/goods'

const route = useRoute()
const router = useRouter()
const vo = ref(null)
const loading = ref(false)
const inputId = ref(null)

const STATUS_MAP = { 0: ['登记', 'info'], 1: ['在售', 'warning'], 2: ['交易中', 'primary'], 3: ['已转移', 'success'] }
const TYPE_MAP = {
  REGISTER: ['商品登记', 'primary'],
  TRADE: ['权属转移', 'success'],
  QUALITY: ['质检上报', 'warning'],
  REPAIR: ['维修维护', 'danger']
}

function statusText(s) { return (STATUS_MAP[s] || ['未知', 'info'])[0] }
function statusTag(s) { return (STATUS_MAP[s] || ['未知', 'info'])[1] }
function typeText(t) { return (TYPE_MAP[t] || [t, 'info'])[0] }
function typeColor(t) { return (TYPE_MAP[t] || [t, 'info'])[1] }

function fmtTime(t) {
  if (!t) return '-'
  return String(t).replace('T', ' ').slice(0, 19)
}

async function load(productId) {
  if (!productId) return
  loading.value = true
  try {
    const res = await getTrace(productId)
    vo.value = res.data || res
  } catch (e) {
    vo.value = null
  }
  loading.value = false
}

function onQuery() {
  if (!inputId.value) return
  router.push({ path: route.path, query: { productId: inputId.value } })
  load(inputId.value)
}

onMounted(() => {
  const id = route.query.productId || route.params.productId
  if (id) {
    inputId.value = String(id)   // 保持字符串，防止雪花ID 被 Number 截断
    load(id)
  }
})
</script>
