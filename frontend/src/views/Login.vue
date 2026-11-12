<template>
  <div class="login-wrap">
    <el-card class="login-card">
      <h2 style="text-align:center;margin-bottom:16px">商品交易存证系统</h2>
      <el-form :model="form" label-width="70px">
        <el-form-item label="角色">
          <el-select v-model="form.role" placeholder="请选择角色">
            <el-option label="企业端（登记/质检/维修/权属转移）" value="ENTERPRISE" />
            <el-option label="用户端（扫码验真/溯源）" value="USER" />
            <el-option label="监管端（审计看板）" value="ADMIN" />
          </el-select>
        </el-form-item>
        <el-form-item label="账号">
          <el-input v-model="form.username" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" show-password />
        </el-form-item>
        <el-button type="primary" style="width:100%" @click="onLogin">登录</el-button>
        <div style="text-align:center;margin-top:12px">
          <el-link type="primary" @click="$router.push('/register')">没有账号？去注册</el-link>
        </div>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { reactive } from 'vue'
import { useRouter } from 'vue-router'
import { login } from '../api/auth'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'

const router = useRouter()
// 角色取值与后端 @RequireRole 一致：ENTERPRISE / USER / ADMIN
const form = reactive({ role: 'ENTERPRISE', username: '', password: '' })

// 角色 -> 登录后落地页
const HOME = { ENTERPRISE: '/enterprise', USER: '/user', ADMIN: '/regulator' }

async function onLogin() {
  try {
    const res = await login(form)
    const data = res.data || res
    localStorage.setItem('token', data.token)
    const role = String(data.role || form.role).toUpperCase()
    localStorage.setItem('role', role)
    localStorage.setItem('userId', data.userId)
    // 企业账号回带的企业ID，登记商品时作为登记方
    if (data.enterpriseId) localStorage.setItem('enterpriseId', data.enterpriseId)
    ElMessage.success('登录成功')
    router.push(HOME[role] || '/login')
  } catch (e) {
    // 拦截器已提示
  }
}
</script>

<style scoped>
.login-wrap { height:100vh; display:flex; align-items:center; justify-content:center; background:#f0f2f5; }
.login-card { width:380px; }
</style>
