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

/**
 * 已出科（历史）患者列表，按出科时间倒序。
 *
 * 工作台只列在科患者，出科后就从列表消失 —— 医生想复查某个病人的监测、文书、
 * 查房记录时没有入口，这个接口补这个缺口。
 *
 * @param {string} [departCode] 科室边界（org_code）；不传按后端权限口径（管理员=全院）
 * @param {string} [keyword] 姓名或住院号关键字
 * @param {number} [limit=300] 条数上限，后端再封顶 500
 * @returns {Promise<Array>} WorkbenchPatient 基础信息（含 outDepartmentTime / inDepart）
 */
export function fetchDischargedPatients(departCode, keyword, limit = 300) {
  const params = { limit }
  if (departCode) params.departCode = departCode
  if (keyword) params.keyword = keyword
  return request.get('/workbench/patients/history', { params, silentError: true })
}

/**
 * 科室驾驶舱今日快照 + 近 N 天流转（只读）。
 *
 * 科室边界由后端按账号授权收敛（不信任前端传的 departCode），这里只把当前选中科室带上。
 *
 * @param {string} [departCode] 科室编码（org_code）
 * @param {number} [days=30] 流转趋势天数，1~90
 * @returns {Promise<object>} CockpitSnapshot：各区块独立带 dataStatus
 *   （OK / EMPTY / PENDING_SOURCE / UNKNOWN），单块失败不影响其它块
 */
export function fetchCockpitSummary(departCode, days = 30) {
  const params = { days }
  if (departCode) params.departCode = departCode
  return request.get('/department-cockpit/summary', { params, silentError: true })
}

/**
 * 单患者镇痛镇静监测（只读）：一次批量返回 NRS / CPOT / RASS / CAM-ICU。
 *
 * 二选一：
 * @param {string} patientId 患者ID
 * @param {object} [options]
 * @param {number} [options.hours=24] 快捷回溯小时数（后端限制 1~720=30天）；与 start/end 互斥
 * @param {string} [options.start] 自定义窗口开始（含），ISO yyyy-MM-ddTHH:mm:ss
 * @param {string} [options.end]   自定义窗口结束（不含），ISO；与 start 同时提供，最长30天
 * 数据来自重症系统正式评分文书 doc 三表，不与 observe 镜像合并。
 */
export function fetchSedationMonitor(patientId, options = {}) {
  const params = {}
  if (options.start && options.end) {
    params.start = options.start
    params.end = options.end
  } else {
    params.hours = options.hours || 24
  }
  return request.get(`/workbench/patients/${patientId}/sedation`, {
    params,
    silentError: true
  })
}

/**
 * 单患者急性脑损伤 / 神经功能监测（只读）：一次批量返回
 * 急性脑损伤诊断识别 + GCS（E/V/M，插管记 T）+ 神志 + 双侧瞳孔 + ICP。
 *
 * @param {string} patientId 患者ID
 * @param {object} [options]
 * @param {number} [options.hours=24] 快捷回溯小时数（1~720=30天）；与 start/end 互斥
 * @param {string} [options.start] 自定义窗口开始（含），ISO yyyy-MM-ddTHH:mm:ss
 * @param {string} [options.end]   自定义窗口结束（不含），ISO；最长30天
 * GCS 来自正式评分文书，神志/瞳孔/ICP 来自重症系统监测评估记录。
 */
export function fetchNeuroMonitor(patientId, options = {}) {
  const params = {}
  if (options.start && options.end) {
    params.start = options.start
    params.end = options.end
  } else {
    params.hours = options.hours || 24
  }
  return request.get(`/workbench/patients/${patientId}/neuro`, {
    params,
    silentError: true
  })
}
