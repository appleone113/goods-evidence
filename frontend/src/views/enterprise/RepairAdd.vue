<template>
  <el-card>
    <template #header>维修录入（存证上链）</template>
    <el-form :model="form" label-width="110px" style="max-width:560px">
      <el-form-item label="选择商品">
        <GoodsSelect v-model="form.productId" />
      </el-form-item>
      <el-form-item label="维修方">
        <el-input v-model="form.repairer" placeholder="如 赣西数码维修服务部" />
      </el-form-item>
      <el-form-item label="维修内容"><el-input v-model="form.content" type="textarea" /></el-form-item>
      <el-form-item label="维修凭证">
        <el-upload :auto-upload="false" :on-change="onFile" :limit="1">
          <el-button>选择文件</el-button>
        </el-upload>
      </el-form-item>
      <el-button type="primary" :loading="loading" @click="onSubmit">提交并上链</el-button>
    </el-form>
  </el-card>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { addRepair } from '../../api/goods'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import GoodsSelect from '../../components/GoodsSelect.vue'

// 字段名与后端 RepairDTO 保持一致：productId / repairer / content / voucher
const form = reactive({ productId: null, repairer: '赣西数码维修服务部', content: '' })
const file = ref(null)
const loading = ref(false)

function onFile(u) { file.value = u.raw }

async function onSubmit() {
  if (!form.productId) { ElMessage.warning('请选择商品'); return }
  if (!form.content) { ElMessage.warning('请填写维修内容'); return }
  if (!file.value) { ElMessage.warning('请选择维修凭证文件'); return }
  const fd = new FormData()
  fd.append('productId', form.productId)
  fd.append('repairer', form.repairer)
  fd.append('content', form.content)
  fd.append('voucher', file.value)
  loading.value = true
  try {
    await addRepair(fd)
    ElMessage.success('维修记录已存证上链')
    file.value = null
  } catch (e) {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}
</script>
