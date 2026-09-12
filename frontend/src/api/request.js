import axios from 'axios'
import { ElMessage, ElLoading } from 'element-plus'
import { getExternalHeaders } from '../utils/external'

const request = axios.create({
  baseURL: '/api',
  timeout: 60000
})

// ---------------- 慢请求全局加载反馈 ----------------
// 后端取数依赖外网达梦库，慢时可达 10s+。请求超过阈值仍未返回才显示遮罩，
// 避免快请求造成闪烁；并发请求用计数器统一管理，全部结束才关闭。
const SLOW_THRESHOLD = 1200
let pendingCount = 0
let slowTimer = null
let loadingInstance = null

function showLoading() {
  if (loadingInstance) return
  loadingInstance = ElLoading.service({
    lock: true,
    text: '数据加载中…',
    background: 'rgba(255, 255, 255, 0.45)'
  })
}

function hideLoading() {
  if (slowTimer) {
    clearTimeout(slowTimer)
    slowTimer = null
  }
  if (loadingInstance) {
    loadingInstance.close()
    loadingInstance = null
  }
}

function enterRequest() {
  pendingCount += 1
  if (pendingCount === 1) {
    slowTimer = setTimeout(showLoading, SLOW_THRESHOLD)
  }
}

function leaveRequest() {
  pendingCount = Math.max(0, pendingCount - 1)
  if (pendingCount === 0) hideLoading()
}

// ---------------- 错误文案友好化 ----------------
// 后端异常信息可能包含 MyBatis 堆栈、Mapper 类名、JDBC 驱动名等内部细节，
// 不应直接展示给医生用户，这里做归类映射；原始信息仍打印到控制台便于排查。
const MESSAGE_RULES = [
  [/外链|签名|expire|pageCode|ICU token|pageCode/i, '访问链接已失效或签名校验失败，请从 ICU 系统重新进入'],
  [/JDBC|CannotGetJdbcConnection|网络通信|PersistenceException|数据库连接|Read timed out/i, '数据服务暂时不可用，请稍后重试'],
  [/参数校验失败|MethodArgumentNotValid|HttpMessageNotReadable/, '提交的数据有误，请检查后重试']
]

/** 是否为“可直接展示给用户的短业务提示”（如“缺少住院号，无法保存”） */
function isPlainHint(msg) {
  return msg.length <= 30 && !/###|Exception|nested|\.java|Caused by/.test(msg)
}

function friendlyMessage(msg) {
  const raw = String(msg || '').trim()
  if (!raw) return '请求失败，请稍后重试'
  if (isPlainHint(raw)) return raw
  for (const [pattern, text] of MESSAGE_RULES) {
    if (pattern.test(raw)) return text
  }
  return '操作失败，请稍后重试'
}

// 请求注入外链鉴权头
request.interceptors.request.use((config) => {
  Object.assign(config.headers, getExternalHeaders())
  enterRequest()
  return config
}, (err) => {
  leaveRequest()
  return Promise.reject(err)
})

// 统一解包与错误提示
request.interceptors.response.use(
  (resp) => {
    leaveRequest()
    const res = resp.data
    if (res.code !== 0) {
      console.error('[api]', resp.config.url, res.code, res.message)
      const friendly = friendlyMessage(res.message)
      ElMessage.error(friendly)
      // 抛出的 message 用友好文案：各页面 catch 里的 "保存失败：" + e.message
      // 不会再把后端原始堆栈展示给用户
      return Promise.reject(new Error(friendly))
    }
    return res.data
  },
  (err) => {
    leaveRequest()
    const data = err.response && err.response.data
    const raw = (data && (data.message || data.msg)) || err.message || '网络错误'
    console.error('[api]', err.config && err.config.url, err.response && err.response.status, raw)
    const friendly = friendlyMessage(raw)
    ElMessage.error(friendly)
    if (err && typeof err === 'object') {
      try { err.message = friendly } catch (e) { /* 只读属性时忽略 */ }
    }
    return Promise.reject(err)
  }
)

export default request
