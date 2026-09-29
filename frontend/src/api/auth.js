import request from './request'

/**
 * 直连登录 API。
 *
 * /api/auth/login 由后端 WebConfig 放行（它本身不可能携带令牌），
 * 其余接口在请求拦截器里统一带上 Authorization 头。
 */

/** 账号密码登录，返回 { token, username, realName, expireAt } */
export function login(username, password) {
  return request.post('/auth/login', { username, password }, { silentError: true })
}

/** 退出登录（令牌无状态，服务端不做会话清理） */
export function logout() {
  return request.post('/auth/logout', { silentError: true })
}
