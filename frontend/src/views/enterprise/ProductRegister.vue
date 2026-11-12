<template>
  <el-card>
    <template #header>商品登记（一物一码）</template>
    <el-form :model="form" label-width="90px" style="max-width:560px">
      <el-form-item label="商品名称"><el-input v-model="form.name" placeholder="如 二手iPhone 15" /></el-form-item>
      <el-form-item label="分类"><el-input v-model="form.category" placeholder="如 数码/手机" /></el-form-item>
      <el-form-item label="描述"><el-input v-model="form.description" type="textarea" /></el-form-item>
      <el-form-item label="商品图片">
        <div style="display:flex;align-items:center;gap:12px;flex-wrap:wrap">
          <input type="file" accept="image/*" @change="onFile" />
          <el-image v-if="form.imageUrl" :src="form.imageUrl"
                    style="width:64px;height:64px;border-radius:6px;border:1px solid #dcdfe6" fit="cover" />
          <span v-if="uploading" style="color:#409EFF;font-size:12px">上传中…</span>
          <span v-else-if="!form.imageUrl" style="color:#909399;font-size:12px">可选，上传后随登记一起保存</span>
          <el-button v-if="form.imageUrl" size="small" text @click="form.imageUrl = ''">移除</el-button>
        </div>
      </el-form-item>
      <el-form-item label="登记企业ID">
        <el-input v-model.number="form.enterpriseId" placeholder="取自登录账号关联的企业" />
      </el-form-item>
      <el-button type="primary" :loading="loading" @click="onSubmit">提交登记</el-button>
    </el-form>

    <template v-if="result">
      <el-divider />
      <el-result icon="success" title="登记成功" :sub-title="`商品已写入链下库，并完成链上权属初始化`">
        <template #extra>
          <div style="display:flex;gap:24px;align-items:center;justify-content:center;flex-wrap:wrap">
            <el-image v-if="result.imageUrl" :src="result.imageUrl"
                      style="width:140px;height:140px;border-radius:8px" fit="cover" />
            <QrCode :src="result.qrUrl" :size="180" />
            <el-descriptions border :column="1" style="min-width:280px">
              <el-descriptions-item label="商品ID">{{ result.productId }}</el-descriptions-item>
              <el-descriptions-item label="一物一码">{{ result.code }}</el-descriptions-item>
              <el-descriptions-item label="当前权属人">{{ result.currentOwner }}</el-descriptions-item>
            </el-descriptions>
          </div>
          <div style="margin-top:12px">
            <el-button type="primary" text @click="$router.push({ path: '/enterprise/trace', query: { productId: result.productId } })">
              查看该商品履历
            </el-button>
          </div>
        </template>
      </el-result>
      <p style="color:#888;font-size:12px;text-align:center">提示：二维码已保存，可在「商品列表」查看，用户扫码即可验真与溯源。</p>
    </template>
  </el-card>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { registerProduct, uploadProductImage } from '../../api/goods'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import QrCode from '../../components/QrCode.vue'

const form = reactive({
  name: '',
  category: '',
  description: '',
  imageUrl: '',
  enterpriseId: Number(localStorage.getItem('enterpriseId') || 1)
})
const loading = ref(false)
const uploading = ref(false)
const result = ref(null)

onMounted(() => {
  // 登录时后端已回带 enterpriseId，这里做一次兜底
  const eid = localStorage.getItem('enterpriseId')
  if (eid) form.enterpriseId = Number(eid)
})

// 先上传图片拿到 /api/file/... 地址，再随登记表单一起提交
async function onFile(e) {
  const file = e.target.files?.[0]
  if (!file) return
  uploading.value = true
  try {
    const fd = new FormData()
    fd.append('file', file)
    const res = await uploadProductImage(fd)
    form.imageUrl = res.data || res
    ElMessage.success('图片已上传，提交登记后生效')
  } catch (err) {
    // 拦截器已统一提示
  } finally {
    uploading.value = false
  }
}

async function onSubmit() {
  if (!form.name) { ElMessage.warning('请填写商品名称'); return }
  loading.value = true
  try {
    const res = await registerProduct(form)
    result.value = res.data || res
    ElMessage.success('登记成功，一物一码已生成')
  } catch (e) {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}
</script>
