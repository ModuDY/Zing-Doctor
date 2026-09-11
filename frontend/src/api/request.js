import axios from 'axios'
import { ElMessage } from 'element-plus'
import { getExternalHeaders } from '../utils/external'

const request = axios.create({
  baseURL: '/api',
  timeout: 60000
})

// 请求注入外链鉴权头
request.interceptors.request.use((config) => {
  Object.assign(config.headers, getExternalHeaders())
  return config
})

// 统一解包与错误提示
request.interceptors.response.use(
  (resp) => {
    const res = resp.data
    if (res.code !== 0) {
      ElMessage.error(res.message || '请求失败')
      return Promise.reject(new Error(res.message || '请求失败'))
    }
    return res.data
  },
  (err) => {
    const msg = err.response && err.response.data && err.response.data.message
    ElMessage.error(msg || err.message || '网络错误')
    return Promise.reject(err)
  }
)

export default request
