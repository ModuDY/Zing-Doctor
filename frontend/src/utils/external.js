/**
 * 外链上下文管理。
 *
 * 外链 URL（经后端 /entry 校验后 302）会携带：
 *  - 签名模式：pageCode + expire + sign
 *  - ICU 明文模式：pageCode + extToken（静态密钥，ICU 系统外链模板写死）
 * 前端在路由跳转时捕获并暂存到 sessionStorage，调用 /api 时注入请求头
 * 通过后端 ExternalLinkInterceptor 校验。
 */

const KEY_PAGE = 'extPageCode'
const KEY_EXPIRE = 'extExpire'
const KEY_SIGN = 'extSign'
const KEY_TOKEN = 'extToken'
const KEY_MODE = 'extMode'

/** 从当前 URL 捕获外链上下文（有完整上下文才写入，避免覆盖已登录态） */
export function captureExternalContext() {
  const q = new URLSearchParams(window.location.search)
  let pageCode = q.get('pageCode')
  const expire = q.get('expire')
  const sign = q.get('sign')
  const token = q.get('extToken')
  // 直链兜底：外部系统若直接配成 /page/{pageCode}?extToken=..（漏走 /entry 的 302 补参），
  // URL 上不会带 pageCode。注册的 frontend_path 恒为 /page/{pageCode}，故用路径末段推断，
  // 让直链也能建立外链鉴权上下文（后端仍会校验 X-External-Token，安全性不变）。
  if (!pageCode && (token || (expire && sign))) {
    const m = window.location.pathname.match(/\/page\/([^/?#]+)\/?$/)
    if (m) pageCode = decodeURIComponent(m[1])
  }
  if (!pageCode) return
  if (expire && sign) {
    sessionStorage.setItem(KEY_PAGE, pageCode)
    sessionStorage.setItem(KEY_EXPIRE, expire)
    sessionStorage.setItem(KEY_SIGN, sign)
    sessionStorage.removeItem(KEY_TOKEN)
    sessionStorage.setItem(KEY_MODE, '1')
  } else if (token) {
    sessionStorage.setItem(KEY_PAGE, pageCode)
    sessionStorage.setItem(KEY_TOKEN, token)
    sessionStorage.removeItem(KEY_EXPIRE)
    sessionStorage.removeItem(KEY_SIGN)
    sessionStorage.setItem(KEY_MODE, '1')
  }
}

/** 当前是否为外链访问模式（外链进入后整个会话都不显示侧边栏） */
export function isExternalMode() {
  return sessionStorage.getItem(KEY_MODE) === '1'
}

/** 获取外链鉴权请求头（签名模式或 ICU 明文模式） */
export function getExternalHeaders() {
  const headers = {}
  const pageCode = sessionStorage.getItem(KEY_PAGE)
  if (!pageCode) return headers
  const expire = sessionStorage.getItem(KEY_EXPIRE)
  const sign = sessionStorage.getItem(KEY_SIGN)
  const token = sessionStorage.getItem(KEY_TOKEN)
  headers['X-External-PageCode'] = pageCode
  if (expire && sign) {
    headers['X-External-Expire'] = expire
    headers['X-External-Sign'] = sign
  } else if (token) {
    headers['X-External-Token'] = token
  }
  return headers
}

/** 是否已具备外链鉴权上下文 */
export function hasExternalContext() {
  const pageCode = sessionStorage.getItem(KEY_PAGE)
  if (!pageCode) return false
  const expire = sessionStorage.getItem(KEY_EXPIRE)
  const sign = sessionStorage.getItem(KEY_SIGN)
  const token = sessionStorage.getItem(KEY_TOKEN)
  return Boolean((expire && sign) || token)
}
