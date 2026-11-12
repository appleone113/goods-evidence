<template>
  <el-card>
    <template #header>
      <div style="display:flex;justify-content:space-between;align-items:center">
        <span>操作日志（审计留痕）</span>
        <div>
          <el-select v-model="limit" size="small" style="width:120px;margin-right:8px" @change="load">
            <el-option :value="20" label="最近20条" />
            <el-option :value="50" label="最近50条" />
            <el-option :value="100" label="最近100条" />
          </el-select>
          <el-input v-model="keyword" size="small" placeholder="搜索操作人/操作" clearable style="width:180px;margin-right:8px" />
          <el-button size="small" type="primary" @click="load">刷新</el-button>
        </div>
      </div>
    </template>

    <el-table :data="filtered" v-loading="loading" style="width:100%" size="small" stripe>
      <el-table-column prop="logId" label="日志ID" width="110" />
      <el-table-column prop="operator" label="操作人" width="160" />
      <el-table-column prop="action" label="操作类型" width="160" />
      <el-table-column prop="detail" label="详情" show-overflow-tooltip />
      <el-table-column label="操作时间" width="180">
        <template #default="{ row }">{{ fmtTime(row.time) }}</template>
      </el-table-column>
    </el-table>

    <el-empty v-if="!loading && !filtered.length" description="暂无操作日志" />
  </el-card>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { getLogs } from '../../api/dashboard'

const list = ref([])
const loading = ref(false)
const limit = ref(20)
const keyword = ref('')

const filtered = computed(() => {
  const k = keyword.value.trim().toLowerCase()
  if (!k) return list.value
  return list.value.filter(r =>
    (r.operator || '').toLowerCase().includes(k) ||
    (r.action || '').toLowerCase().includes(k) ||
    (r.detail || '').toLowerCase().includes(k)
  )
})

function fmtTime(t) {
  if (!t) return '-'
  return String(t).replace('T', ' ').slice(0, 19)
}

async function load() {
  loading.value = true
  try {
    const res = await getLogs(limit.value)
    list.value = (res.data || res || [])
  } catch (e) {
    list.value = []
  }
  loading.value = false
}

onMounted(load)
</script>
