import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json'
  }
})

// 请求拦截
api.interceptors.request.use(config => config, error => Promise.reject(error))

// 响应拦截
api.interceptors.response.use(
  response => response.data,
  error => {
    const msg = error.response?.data?.message || error.message || '请求失败'
    console.error('API Error:', msg)
    return Promise.reject(new Error(msg))
  }
)

export default api

/** 上传文件 */
export function uploadFile(file) {
  const formData = new FormData()
  formData.append('file', file)
  return api.post('/upload/file', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

/** AI 视觉识别图片上传 */
export function uploadFileWithAI(file, aiConfig) {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('model', aiConfig.model || 'gpt-4o-mini')
  formData.append('token', aiConfig.token)
  formData.append('baseUrl', aiConfig.baseUrl || 'https://api.openai.com/v1')
  return api.post('/upload/file-ai', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120000  // AI 视觉识别可能需要较长时间
  })
}

/** 获取所有数据源 */
export function getSources() {
  return api.get('/upload/sources')
}

/** 获取指定数据源的数据 */
export function getData(dataId) {
  return api.get(`/upload/data/${dataId}`)
}

/** 删除数据源 */
export function deleteData(dataId) {
  return api.delete(`/upload/data/${dataId}`)
}

/** 执行对账 */
export function runReconciliation(params) {
  return api.post('/reconciliation/run', params)
}

/** AI 智能对账 */
export function runAIReconciliation(params) {
  return api.post('/ai/reconcile', params)
}

/** 测试 AI 连接（后端代理，避免 CORS） */
export function testAIConnection(baseUrl, token, model) {
  return api.post('/ai/test', { baseUrl, token, model })
}
