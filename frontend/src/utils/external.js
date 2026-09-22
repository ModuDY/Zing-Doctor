/**
 * 外链上下文管理。
 *
 * 外链 URL（经后端 /entry 校验后 302）会携带：
 *  - 签名模式：pageCode + expire + sign
 *  - ICU 明文模式：pageCode + extToken（静态密钥，ICU 系统外链模板写死）
 * 前端在路由跳转时捕获并暂存到 sessionStorage，调用 /api 时注入请求头
 * 通过后端 ExternalLinkInterceptor 校验。
 */

// 占位值判定：与后端 OperatorContext.PLACEHOLDER_NAMES、展示层 operatorLabel 同一把尺子
import { isPlaceholderOperator } from './operator'

const KEY_PAGE = 'extPageCode'
const KEY_EXPIRE = 'extExpire'
const KEY_SIGN = 'extSign'
const KEY_TOKEN = 'extToken'
const KEY_MODE = 'extMode'
/** 外链带来的医生身份（姓名优先，其次工号）：供后端写 create_by / update_by */
const KEY_OPERATOR = 'extOperator'

/** 从当前 URL 捕获外链上下文（有完整上下文才写入，避免覆盖已登录态） */
export function captureExternalContext() {
  const q = new URLSearchParams(window.location.search)

  // 身份必须最先捕获，且不以外链鉴权上下文完整为前提。
  //
  // 它先前被放在 `if (!pageCode) return` 之后：URL 缺 pageCode 时函数提前返回，
  // 身份根本没被存下来。而页面上的「评分医师」是直读 route.query.realname 的，
  // 于是出现了最迷惑的一种现象 —— 新建时医师栏明明填着名字，保存后 create_by 却是
  // unknown。身份（这次操作是谁做的）和鉴权（能否访问这个页面）是两件事，
  // 不该因为后者不完整而把前者一起丢掉。
  const operator = q.get('realname') || q.get('userName') || q.get('username') || q.get('userId') || ''
  if (operator) {
    // 「带了但不能用」的身份必须清掉，否则整个会话都被这个脏值污染：
    // 典型是 ICU 外链模板没做变量替换，原样传来 ${realname}；还有 unknown 及其拼错写法。
    // 它们都是非空串，能被后面「非空就发」的判断通过 → 每个接口都带上伪身份 →
    // 后端判为占位值 → create_by / update_by 一律记 unknown（页面显示「未知」）。
    // 实测就是这个：Referer 里出现过 &realname=${realname}，之后新建的记录全是 unknown。
    if (isPlaceholderOperator(operator)) {
      sessionStorage.removeItem(KEY_OPERATOR)
    } else {
      sessionStorage.setItem(KEY_OPERATOR, operator)
    }
  }
  // URL 完全没带身份参数时<b>不清空</b>已存的值：站内路由跳转（router.push）与
  // window.open 打开的新标签页都不会把 realname 拼在地址上，此前那条
  // 「没带就 removeItem」会在这类跳转后悄悄抹掉操作人。

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
  // 操作人身份：后端据此写 create_by / update_by，不由各页面自行传递（不可信）
  const operator = sessionStorage.getItem(KEY_OPERATOR)
  // 二次校验：只发「看起来像人名」的值。占位值宁可不发——后端本来也会判占位值，
  // 但脏值一旦进了 sessionStorage 就会被反复发出，不如在这里就近掐掉。
  if (operator && !isPlaceholderOperator(operator)) {
    // 中文名不能裸放进请求头：Servlet 按 ISO-8859-1 解码会变成 ç®¡ç†å 这类乱码
    // （现场 2026-09-20 实测，写进 create_by 的就是乱码），中间的反向代理还可能
    // 直接把非 ASCII 的头丢掉——后端判不出身份，create_by 就记成 unknown。
    // 统一 URL 编码成纯 ASCII 再传，服务端解码还原；纯 ASCII 姓名编码后原样不变。
    headers['X-External-Operator'] = encodeURIComponent(operator)
  }
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

/**
 * 外链通道当前是否拿到了可用的操作人身份。
 *
 * <p>拿不到 = 这次会话里所有审计字段（评分记录 / 俯卧位记录的 create_by、update_by，
 * 质控计算批次的 operator）都会被服务端记成 unknown —— 页面上一律显示「未知」。
 *
 * <p>这是配置问题而非系统故障：ICU 外链模板没把 realname / username 替换成真值
 * （实测传过来的是字面量 ${realname}）。把它暴露出来，是为了让页面能明确告知
 * 「链接没带身份」，而不是让用户对着一串「未知」以为是系统坏了。
 *
 * <p>直连登录不走这里：那条通道的姓名由服务端令牌反解得到，不受外链参数影响。
 */
export function hasExternalOperator() {
  const operator = sessionStorage.getItem(KEY_OPERATOR)
  return Boolean(operator) && !isPlaceholderOperator(operator)
}

/**
 * 读取外链传入的业务参数（departCode / patientId / inHospitalNo …）。
 *
 * 两种情况一律按「没传」处理，避免拿着脏值去查——那样表面请求成功，实际永远返回空：
 *  1) ICU 外链模板变量未被替换，原样传来 ${departCode}；
 *  2) 空白串。
 *
 * 只从当前 URL 读取：外链经 /entry 302 后的最终地址带着这些参数，刷新也仍在。
 * 与鉴权上下文（存 sessionStorage）不同，业务参数刻意不跨页留存，避免串科室。
 */
const RAW_PLACEHOLDER = /\$\{[^}]*\}/

export function externalParam(name) {
  const raw = new URLSearchParams(window.location.search).get(name)
  const s = String(raw == null ? '' : raw).trim()
  return s && !RAW_PLACEHOLDER.test(s) ? s : ''
}

/**
 * 把当前外链上下文拼到站内链接上。
 *
 * window.open 打开的新标签页并不保证继承当前标签页的 sessionStorage，
 * 因此站内跳转（如总览 → 评分页）必须把 pageCode/extToken（或 expire+sign）
 * 显式带在 URL 上，否则新标签页会失去外链鉴权、接口 401。
 */
export function appendExternalContext(url) {
  const pageCode = sessionStorage.getItem(KEY_PAGE)
  if (!pageCode) return url
  const token = sessionStorage.getItem(KEY_TOKEN)
  const expire = sessionStorage.getItem(KEY_EXPIRE)
  const sign = sessionStorage.getItem(KEY_SIGN)
  const parts = [`pageCode=${encodeURIComponent(pageCode)}`]
  // 带上医生身份：否则站内跳过去的页面丢失 realname，既显示不出名字也记不到操作人。
  // 用 realname 为键名，与目标页面既有的「评分医师」展示逻辑一致。
  const operator = sessionStorage.getItem(KEY_OPERATOR)
  // 别把占位身份往下传：新标签页会把它当真名再捕获一次，污染扩散到下一个页面
  if (operator && !isPlaceholderOperator(operator)) {
    parts.push(`realname=${encodeURIComponent(operator)}`)
  }
  if (token) {
    parts.push(`extToken=${encodeURIComponent(token)}`)
  } else if (expire && sign) {
    parts.push(`expire=${encodeURIComponent(expire)}`)
    parts.push(`sign=${encodeURIComponent(sign)}`)
  } else {
    return url
  }
  return url + (url.indexOf('?') >= 0 ? '&' : '?') + parts.join('&')
}
