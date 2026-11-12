import request from './request'

// 扫码验真（模块七，公开接口）
export function scanVerify(code) {
  return request.get('/verify/scan', { params: { code } })
}
