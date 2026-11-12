import request from './request'

// 商品登记（模块二：一物一码）
export function registerProduct(data) {
  return request.post('/product/register', data)
}

// 上传商品实物图：返回 /api/file/{对象名}，登记时作为 imageUrl 提交
export function uploadProductImage(formData) {
  return request.post('/product/upload-image', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

// 商品列表
export function listProducts() {
  return request.get('/product/list')
}

// 质检录入（模块五）
export function addQuality(formData) {
  return request.post('/quality/add', formData, { headers: { 'Content-Type': 'multipart/form-data' } })
}

// 维修录入（模块五）
export function addRepair(formData) {
  return request.post('/repair/add', formData, { headers: { 'Content-Type': 'multipart/form-data' } })
}

// 权属转移（模块四）
// 后端 controller 用 @RequestParam 接收，不能发 JSON body，
// 否则报 Required request parameter 'productId' is not present
export function transferTrade(data) {
  return request.post('/trade/transfer', null, { params: data })
}

// 商品全生命周期履历（模块三）
export function getTrace(productId) {
  return request.get('/trace/' + productId)
}
