import { createRouter, createWebHistory } from 'vue-router'
import Login from '../views/Login.vue'
import Register from '../views/Register.vue'
import EnterpriseLayout from '../layouts/EnterpriseLayout.vue'
import UserLayout from '../layouts/UserLayout.vue'
import RegulatorLayout from '../layouts/RegulatorLayout.vue'

// 简易 token 与角色读取（与后端 JwtUtil 约定的 role 字段对应）
function getRole() {
  return localStorage.getItem('role')
}
function getToken() {
  return localStorage.getItem('token')
}

const routes = [
  { path: '/login', name: 'Login', component: Login, meta: { public: true } },
  { path: '/register', name: 'Register', component: Register, meta: { public: true } },

  // 企业端：商品登记、一物一码、质检维修、权属转移
  {
    path: '/enterprise',
    component: EnterpriseLayout,
    redirect: '/enterprise/register',
    children: [
      { path: 'register', name: 'ProductRegister', component: () => import('../views/enterprise/ProductRegister.vue') },
      { path: 'list', name: 'ProductList', component: () => import('../views/enterprise/ProductList.vue') },
      { path: 'quality', name: 'QualityAdd', component: () => import('../views/enterprise/QualityAdd.vue') },
      { path: 'repair', name: 'RepairAdd', component: () => import('../views/enterprise/RepairAdd.vue') },
      { path: 'trade', name: 'TradeTransfer', component: () => import('../views/enterprise/TradeTransfer.vue') },
      // 企业端也可查看任意商品履历（与用户端复用同一组件）
      { path: 'trace', name: 'EnterpriseTrace', component: () => import('../views/user/Trace.vue') }
    ]
  },

  // 用户端（消费者）：扫码验真、溯源履历
  {
    path: '/user',
    component: UserLayout,
    redirect: '/user/scan',
    children: [
      { path: 'scan', name: 'ScanVerify', component: () => import('../views/user/ScanVerify.vue') },
      { path: 'trace', name: 'Trace', component: () => import('../views/user/Trace.vue') }
    ]
  },

  // 监管端：审计看板、操作日志
  {
    path: '/regulator',
    component: RegulatorLayout,
    redirect: '/regulator/dashboard',
    children: [
      { path: 'dashboard', name: 'Dashboard', component: () => import('../views/regulator/Dashboard.vue') },
      { path: 'logs', name: 'Logs', component: () => import('../views/regulator/Logs.vue') }
    ]
  },

  { path: '/', redirect: '/login' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 全局前置守卫：未登录跳登录；按角色约束可访问的端
router.beforeEach((to) => {
  if (to.meta.public) return true
  if (!getToken()) return '/login'
  const role = (localStorage.getItem('role') || '').toUpperCase()
  if (to.path.startsWith('/enterprise') && role !== 'ENTERPRISE') return '/login'
  if (to.path.startsWith('/user') && role !== 'USER') return '/login'
  if (to.path.startsWith('/regulator') && role !== 'ADMIN') return '/login'
  return true
})

export default router
