/**
 * 操作人（create_by / update_by）展示的统一处理。
 *
 * <p>审计字段由服务端解析：外链通道取第三方随外链带来的 realname / username，
 * 直连通道取登录账号查库的真实姓名。两侧都识别不出人时后端写占位值 {@code unknown}
 * （见 OperatorContext.UNKNOWN），现场 ICU 外链模板还出现过拼错的 {@code unkonw}。
 *
 * <p>这类值<b>不是人名</b>：直接亮出来会让人以为身份已经接通、只是这个人叫这个名字，
 * 从而掩盖「外链模板没配 realname / 服务端没配 default-operator」这个真正的问题。
 * 因此凡是要把操作人显示给人看的地方，一律先过这里。
 *
 * <p>注意：本模块只管<b>显示</b>。审计字段该写什么仍由服务端决定，前端不参与伪造。
 */

/** 占位值：unknown 及其常见拼错、各类空值写法、未替换的模板变量 */
const PLACEHOLDER = /^(unknown|unkonw|unkown|unknow|null|undefined|none|nil|n\/a|na|匿名|未知|无|-|--|\.)$/i

/** 该值是否只是占位而非真实人名（含空、未替换的模板变量 ${realname}） */
export function isPlaceholderOperator(v) {
  const s = String(v == null ? '' : v).trim()
  if (s === '' || s.indexOf('${') >= 0) return true
  return PLACEHOLDER.test(s)
}

/**
 * 展示用操作人名：占位值返回空串，由调用方决定显示「—」还是「未知（…）」。
 */
export function operatorLabel(v) {
  return isPlaceholderOperator(v) ? '' : String(v).trim()
}

/** 操作人拿不到时的排查提示（title / 说明文案用，别的地方不用猜） */
export const OPERATOR_MISSING_HINT =
  '服务端未解析到操作人：ICU 外链模板未带 realname/username，且未配置服务端默认操作人（external-link.default-operator）'
