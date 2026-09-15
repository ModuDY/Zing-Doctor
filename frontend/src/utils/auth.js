/**
 * 直连登录本地会话。
 *
 * 区别于 utils/external.js（外链免登录上下文，存 sessionStorage、按标签页隔离）：
 * 登录态刻意放在 localStorage —— 医生习惯开着多个标签页，刷新或新开标签页都应保持登录，
 * 只有点「退出登录」或令牌过期才清除。
 */

const KEY_TOKEN = 'zing_doctor_token'
const KEY_USER = 'zing_doctor_user'

/** 当前令牌（未登录返回空串） */
export function getToken() {
  try {
    return localStorage.getItem(KEY_TOKEN) || ''
  } catch (e) {
    return ''
  }
}

export function setToken(token) {
  try {
    localStorage.setItem(KEY_TOKEN, token)
  } catch (e) { /* 隐私模式下 localStorage 不可写，忽略即可 */ }
}

/** 当前登录者 { username, realName }，未登录返回 null */
export function getUser() {
  try {
    const raw = localStorage.getItem(KEY_USER)
    return raw ? JSON.parse(raw) : null
  } catch (e) {
    return null
  }
}

export function setUser(user) {
  try {
    localStorage.setItem(KEY_USER, JSON.stringify(user || {}))
  } catch (e) { /* 同上 */ }
}

/** 是否已登录（仅看本地是否有令牌，真伪由后端判定） */
export function isLoggedIn() {
  return Boolean(getToken())
}

/** 登录请求之外的 API 都要带的鉴权头 */
export function getAuthHeaders() {
  const token = getToken()
  return token ? { Authorization: 'Bearer ' + token } : {}
}

/** 清除本地会话 */
export function clearSession() {
  try {
    localStorage.removeItem(KEY_TOKEN)
    localStorage.removeItem(KEY_USER)
  } catch (e) { /* 同上 */ }
}
