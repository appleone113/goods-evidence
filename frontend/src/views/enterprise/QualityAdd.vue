<template>
  <el-card>
    <template #header>质检录入（存证上链）</template>
    <el-form :model="form" label-width="110px" style="max-width:560px">
      <el-form-item label="选择商品">
        <GoodsSelect v-model="form.productId" />
      </el-form-item>
      <el-form-item label="质检机构">
        <el-input v-model="form.org" placeholder="如 江西省质检中心" />
      </el-form-item>
      <el-form-item label="质检结果">
        <el-radio-group v-model="form.result">
          <el-radio value="合格">合格</el-radio>
          <el-radio value="不合格">不合格</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="质检报告">
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
import { addQuality } from '../../api/goods'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import GoodsSelect from '../../components/GoodsSelect.vue'

// 字段名与后端 QualityDTO 保持一致：productId / org / result / report
const form = reactive({ productId: null, org: '江西省质检中心', result: '合格' })
const file = ref(null)
const loading = ref(false)

function onFile(u) { file.value = u.raw }

async function onSubmit() {
  if (!form.productId) { ElMessage.warning('请选择商品'); return }
  if (!file.value) { ElMessage.warning('请选择质检报告文件'); return }
  const fd = new FormData()
  fd.append('productId', form.productId)
  fd.append('org', form.org)
  fd.append('result', form.result)
  fd.append('report', file.value)
  loading.value = true
  try {
    await addQuality(fd)
    ElMessage.success('质检已存证上链')
    file.value = null
  } catch (e) {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}
</script>
