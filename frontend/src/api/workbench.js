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
 * 驾驶舱补充质控块：近 7 天转出后 48h 重返率 + 近 12 个月 sepsis bundle 完成率趋势。
 *
 * <p>与 summary 分开请求：一块要按任意窗口现算、一块要读 12 个月结果，耗时与今日快照
 * 不是一个量级。分开可以让首屏先出、补充块后到，单块失败也不影响整页。
 *
 * @param {string} [departCode] 科室编码（org_code）
 * @returns {Promise<object>} CockpitQualityExtra：
 *   readmitNumerator / readmitDenominator / readmitRate / windowStart / windowEnd /
 *   readmitNote / bundleTrend[{ month, numerator, denominator, rate }] / bundleNote
 */
export function fetchCockpitQualityExtra(departCode) {
  const params = {}
  if (departCode) params.departCode = departCode
  return request.get('/department-cockpit/quality-extra', { params, silentError: true })
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
 * 窗口内阿片类镇痛药执行医嘱（只读）：芬太尼/瑞芬/舒芬、吗啡、地佐辛等。
 * 与 fetchSedationMonitor 同窗口参数，供监测页「镇痛药使用与评分关联」对照。
 */
export function fetchAnalgesia(patientId, options = {}) {
  const params = {}
  if (options.start && options.end) {
    params.start = options.start
    params.end = options.end
  } else {
    params.hours = options.hours || 24
  }
  return request.get(`/workbench/patients/${patientId}/analgesia`, {
    params,
    silentError: true
  })
}

/**
 * 单患者营养支持分析（只读）：按医嘱实际给入量（逐小时明细）× 配方密度，
 * 估算肠内/肠外能量(kcal)与蛋白质(g)。
 *
 * @param {string} patientId 患者ID
 * @param {object} [options]
 * @param {number} [options.hours=24] 快捷回溯小时数（1~720）；与 start/end 互斥
 * @param {string} [options.start] 自定义起 yyyy-MM-ddTHH:mm:ss
 * @param {string} [options.end]   自定义止 yyyy-MM-ddTHH:mm:ss
 */
export function fetchNutritionMonitor(patientId, options = {}) {
  const params = {}
  if (options.start && options.end) {
    params.start = options.start
    params.end = options.end
  } else {
    params.hours = options.hours || 24
  }
  return request.get(`/workbench/patients/${patientId}/nutrition`, {
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

/**
 * 生命体征趋势（docs/25 §1）：HR/RR/SpO₂/血压/体温，多指标趋势 + 原始记录。
 * @param {string} patientId
 * @param {number} [hours=24] 1~168
 */
export function fetchVitals(patientId, hours = 24) {
  return request.get(`/vitals/patients/${patientId}`, { params: { hours }, silentError: true })
}

/**
 * 检验趋势（肝功能 liver / 凝血 coag / 肾功能与电解质 renal，docs/25 §4/§5）。
 * 按项目名称匹配（生产库存在同一项目多编码），返回分组序列 + 明细。
 * renal 面板会排除名称相近但非同一项目的检验（降钙素原、脑钠肽、尿素/肌酐比值、尿液标本）。
 * @param {string} patientId
 * @param {'liver'|'coag'|'renal'} type
 * @param {number} [hours=168] 1~720
 */
export function fetchLabTrend(patientId, type = 'liver', hours = 168) {
  return request.get(`/lab-trend/patients/${patientId}`, { params: { type, hours }, silentError: true })
}

/**
 * 体液平衡（docs/25 §2）：出量/尿量/医嘱入量/其他入量四来源分列。
 * 刻意不含净平衡合计数（来源去重未经证明）。
 * @param {string} patientId
 * @param {number} [hours=24] 1~72
 */
export function fetchFluidBalance(patientId, hours = 24) {
  return request.get(`/fluid-balance/patients/${patientId}`, { params: { hours }, silentError: true })
}