<template>
  <div class="login-wrap">
    <el-card class="reg-card">
      <h2 style="text-align:center;margin-bottom:16px">账号注册</h2>
      <el-form :model="form" label-width="80px">
        <el-form-item label="账号">
          <el-input v-model="form.username" placeholder="登录用账号" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" show-password />
        </el-form-item>
        <el-form-item label="确认密码">
          <el-input v-model="form.confirm" type="password" show-password />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="form.role" style="width:100%">
            <el-option label="企业端（登记/质检/维修/权属转移）" value="ENTERPRISE" />
            <el-option label="用户端（扫码验真/溯源）" value="USER" />
            <el-option label="监管端（审计看板）" value="ADMIN" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.role === 'ENTERPRISE'" label="企业ID">
          <el-input v-model.number="form.enterpriseId" placeholder="关联 enterprise 表，如 1" />
        </el-form-item>
        <el-button type="primary" style="width:100%" :loading="loading" @click="onRegister">注册</el-button>
      </el-form>
      <div style="text-align:center;margin-top:12px">
        <el-link type="primary" @click="$router.push('/login')">已有账号？去登录</el-link>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { register } from '../api/auth'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'

const router = useRouter()
const loading = ref(false)
const form = reactive({
  username: '',
  password: '',
  confirm: '',
  role: 'ENTERPRISE',
  enterpriseId: 1
})

async function onRegister() {
  if (!form.username || !form.password) { ElMessage.warning('请填写账号和密码'); return }
  if (form.password !== form.confirm) { ElMessage.warning('两次输入的密码不一致'); return }
  loading.value = true
  try {
    await register({
      username: form.username,
      password: form.password,
      role: form.role,
      enterpriseId: form.role === 'ENTERPRISE' ? form.enterpriseId : null
    })
    ElMessage.success('注册成功，请登录')
    router.push('/login')
  } catch (e) {
    // 拦截器已提示错误
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-wrap { height:100vh; display:flex; align-items:center; justify-content:center; background:#f0f2f5; }
.reg-card { width:420px; }
</style>
