/**
 * 科室编码相关的小工具。
 *
 * <p>系统里科室有两套取值：
 * <ul>
 *   <li><b>org_code</b> —— sys_depart.org_code（如 20070131），外链 / 质控 / DDD 都在用。</li>
 *   <li><b>ward_name</b> —— 病区名，patient_info 里的展示性字段。</li>
 * </ul>
 * 查询与聚合一律以 org_code 为准：病区名与它不是同一套编码，拿病区名去过滤
 * 既查不出东西，也没法和其它模块对齐（待办就只能按全院聚合）。
 */

/**
 * 把外链带来的科室参数归一成 org_code。
 *
 * <p>外链方传的可能是科室名称（如「ICU-4U」）而不是编码（如 20070131）。传名称时
 * 查询不会命中任何行，表现是「列表为空」而不是报错；更麻烦的是名称在下拉框里
 * 会原样显示，看起来像是选对了科室 —— 极难排查。
 *
 * <p>判据：先按编码匹配；匹配不到、且按名称唯一命中时换成该科室的编码。
 * 空值（= 全院口径）与无法判定时原样返回 —— 不静默替使用者猜科室。
 *
 * @param {string} code 待归一的值
 * @param {Array<{org_code: string, depart_name: string}>} departments 科室字典
 * @returns {string} 归一后的值
 */
export function normalizeDepartParam(code, departments) {
  const c = String(code == null ? '' : code).trim()
  if (!c) return ''
  const list = Array.isArray(departments) ? departments : []
  if (list.some((d) => String(d.org_code) === c)) return c
  const byName = list.filter((d) => String(d.depart_name) === c)
  return byName.length === 1 ? String(byName[0].org_code) : c
}

/**
 * org_code → 科室名称。
 *
 * <p>查不到时原样返回编码：宁可让使用者看到一个陌生编码，也不要显示空白或
 * 「未分配」—— 后者会让人误以为数据缺失，实际是科室没匹配上。
 */
export function deptNameOf(code, departments) {
  const c = String(code == null ? '' : code).trim()
  if (!c) return ''
  const list = Array.isArray(departments) ? departments : []
  const hit = list.find((d) => String(d.org_code) === c)
  return hit ? String(hit.depart_name || c) : c
}
