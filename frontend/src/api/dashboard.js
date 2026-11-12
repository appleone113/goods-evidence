import request from './request'

// 监管看板概览（模块八）
export function getDashboard() {
  return request.get('/admin/dashboard')
}

// 操作日志（模块八）
export function getLogs(limit = 20) {
  return request.get('/admin/logs', { params: { limit } })
}

// 链上链下一致性巡检：重算哈希与链上存证比对，篡改/漏上链点名定位
export function runIntegrityCheck() {
  return request.get('/admin/integrity/check')
}

// 告警概览：验真失败记录 + 最近一次巡检结果
export function getAlerts() {
  return request.get('/admin/alerts')
}

// 近 N 天权属转移趋势（交易趋势图）
export function getTradeTrend(days = 14) {
  return request.get('/admin/trade-trend', { params: { days } })
}
