import request from './request'

// 登录：返回 { token, role, userId, enterpriseId }
export function login(data) {
  return request.post('/auth/login', data)
}

// 注册：后端用 @RequestParam 接收，故走 query 参数
export function register(data) {
  return request.post('/auth/register', null, { params: data })
}
