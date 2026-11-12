<template>
  <el-card>
    <template #header>权属转移（链上校验 + 存证）</template>
    <el-form :model="form" label-width="110px" style="max-width:560px">
      <el-form-item label="选择商品">
        <!-- 已转移的商品不再出现在候选里 -->
        <GoodsSelect v-model="form.productId" exclude-transferred />
      </el-form-item>
      <el-form-item label="买家账号">
        <el-input v-model="form.buyer" placeholder="买家地址/账号" />
      </el-form-item>
      <el-form-item>
        <el-alert type="info" :closable="false" show-icon title="说明"
          description="提交后由智能合约校验当前权属（require owner == 发起人）并转移，交易摘要同步上链存证。若出让方与链上权属人不一致，合约会直接拒绝。" />
      </el-form-item>
      <el-button type="primary" :loading="loading" @click="onSubmit">发起转移</el-button>
    </el-form>
  </el-card>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { transferTrade } from '../../api/goods'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import GoodsSelect from '../../components/GoodsSelect.vue'

const form = reactive({ productId: null, buyer: '' })
const loading = ref(false)

async function onSubmit() {
  if (!form.productId) { ElMessage.warning('请选择商品'); return }
  if (!form.buyer) { ElMessage.warning('请填写买家账号'); return }
  loading.value = true
  try {
    await transferTrade({ productId: form.productId, buyer: form.buyer })
    ElMessage.success('权属转移成功，已上链存证')
    form.buyer = ''
    form.productId = null
  } catch (e) {
    // 拦截器已提示（含合约拒绝原因）
  } finally {
    loading.value = false
  }
}
</script>
