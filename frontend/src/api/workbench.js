import request from './request'

/**
 * ICU 在科患者工作台（仅基本信息）
 *
 * departCode 必须是 sys_depart.org_code（如 20070131）—— 不认病区名（ward_name），
 * 两者不是同一套编码。不传表示不限科室（直连登录时的默认行为）；
 * 外链进入时应由 ICU 带在 URL 的 departCode 参数上。
 */
export function fetchInpatients(departCode) {
  const params = departCode ? { departCode } : {}
  // 工作台会把“列表未加载”以内嵌状态呈现，避免全局拦截器再弹一条无上下文的错误。
  return request.get('/workbench/patients', { params, silentError: true })
}

/**
 * 当前账号的科室可见范围：admin / matched / departs / message
 *
 * 前端据此决定三件事：默认进哪个科室、多科室时是否要让用户自选、
 * 以及未绑定科室时提示什么。列表接口会因为越权报错，所以范围要先于列表拿到。
 */
export function fetchDepartScope() {
  return request.get('/workbench/scope', { silentError: true })
}

/**
 * 单患者诊疗摘要：把工作台列表的 enrich 流水线收敛到一个患者上。
 * 返回 WorkbenchPatient 全字段（基本信息/生命支持/评分/感染/待办）。
 */
export function fetchPatientSummary(patientId) {
  return request.get(`/workbench/patients/${patientId}/summary`, { silentError: true })
}
