<template>
  <el-container style="height:100vh">
    <el-aside width="200px" style="background:#001529">
      <div class="logo">监管中心</div>
      <el-menu background-color="#001529" text-color="#fff" active-text-color="#409EFF" router>
        <el-menu-item index="/regulator/dashboard">监管看板</el-menu-item>
        <el-menu-item index="/regulator/logs">操作日志</el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header style="display:flex;justify-content:space-between;align-items:center;background:#fff;border-bottom:1px solid #eee">
        <span>商品交易存证系统 · 监管端</span>
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
