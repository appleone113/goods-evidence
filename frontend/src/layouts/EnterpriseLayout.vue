<template>
  <el-container style="height:100vh">
    <el-aside width="200px" style="background:#001529">
      <div class="logo">企业工作台</div>
      <el-menu background-color="#001529" text-color="#fff" active-text-color="#409EFF" router>
        <el-menu-item index="/enterprise/register">商品登记·一物一码</el-menu-item>
        <el-menu-item index="/enterprise/list">商品列表</el-menu-item>
        <el-menu-item index="/enterprise/quality">质检录入</el-menu-item>
        <el-menu-item index="/enterprise/repair">维修录入</el-menu-item>
        <el-menu-item index="/enterprise/trade">权属转移</el-menu-item>
        <el-menu-item index="/enterprise/trace">商品履历</el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header style="display:flex;justify-content:space-between;align-items:center;background:#fff;border-bottom:1px solid #eee">
        <span>商品交易存证系统 · 企业端</span>
        <el-button text @click="logout">退出</el-button>
      </el-header>
      <el-main><router-view /></el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { useRouter } from 'vue-router'
const router = useRouter()
function logout() {
  localStorage.clear()
  router.push('/login')
}
</script>

<style scoped>
.logo { color:#fff; text-align:center; padding:16px 0; font-weight:bold; }
</style>
