<template>
  <el-card>
    <template #header>商品列表（一物一码）</template>
    <el-table :data="list" v-loading="loading" style="width:100%">
      <el-table-column label="图片" width="80">
        <template #default="{ row }">
          <el-image v-if="row.imageUrl" :src="row.imageUrl"
                    style="width:44px;height:44px;border-radius:4px" fit="cover"
                    :preview-src-list="[row.imageUrl]" preview-teleported />
          <span v-else style="color:#c0c4cc">未上传</span>
        </template>
      </el-table-column>
      <el-table-column prop="productId" label="ID" min-width="200" />
      <el-table-column prop="name" label="名称" />
      <el-table-column prop="code" label="一物一码" />
      <el-table-column prop="status" label="状态" width="110">
        <template #default="{ row }">
          <el-tag :type="statusTag(row.status)">{{ statusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="180">
        <template #default="{ row }">
          <el-button size="small" type="primary" @click="viewDetail(row)">履历</el-button>
          <el-popover placement="left" :width="220" trigger="click">
            <template #reference>
              <el-button size="small">二维码</el-button>
            </template>
            <div style="text-align:center">
              <el-image v-if="row.qrUrl" :src="row.qrUrl" style="width:180px;height:180px" fit="contain" />
              <div v-else style="color:#909399">二维码未生成</div>
              <div style="font-size:12px;color:#909399;margin-top:6px">{{ row.code || '-' }}</div>
            </div>
          </el-popover>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { listProducts } from '../../api/goods'

const list = ref([])
const loading = ref(false)
const router = useRouter()

const STATUS_MAP = {
  0: ['登记', 'info'],
  1: ['在售', 'warning'],
  2: ['交易中', 'primary'],
  3: ['已转移', 'success']
}

function statusText(s) { return (STATUS_MAP[s] || ['未知', 'info'])[0] }
function statusTag(s) { return (STATUS_MAP[s] || ['未知', 'info'])[1] }

async function load() {
  loading.value = true
  try {
    const res = await listProducts()
    list.value = res.data || res || []
  } catch (e) {
    list.value = []
  }
  loading.value = false
}

// 跳转到全生命周期履历页
function viewDetail(row) {
  router.push({ path: '/enterprise/trace', query: { productId: row.productId } })
}

onMounted(load)
</script>
