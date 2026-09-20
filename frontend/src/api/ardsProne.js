import request from './request'

/**
 * ARDS 俯卧位通气治疗记录 API。
 *
 * 注意：归档不在此处单开接口，统一走 /archive/push?biz=ARDS_PRONE，
 * 与 APACHE II（APACHE2）、SOFA（SOFA）评分归档同一个接口、同一套传参，仅文档类型编码不同。
 */

/** 记录列表：按住院号或科室（都为空时查全部） */
export function fetchArdsProneList(inHospitalNo, departCode) {
  return request.get('/ards-prone/list', { params: { inHospitalNo, departCode } })
}

/** 填写页视图：主记录 + 时点 + 单元格矩阵 */
export function fetchArdsProneRecord(id) {
  return request.get('/ards-prone/get', { params: { id } })
}

/** 参数字典（37 项）：填写页、打印文书、回传文书共用同一份 */
export function fetchArdsProneDict() {
  return request.get('/ards-prone/dict')
}

/** APACHE II 是否显示（参数控制，默认显示、全院统一） */
export function fetchArdsProneApache2Show() {
  return request.get('/ards-prone/apache2-show')
}

/** 归档回传是否启用（参数控制，默认关闭；关闭后列表/填写页隐藏归档按钮与状态列） */
export function fetchArdsProneArchiveEnabled() {
  return request.get('/ards-prone/archive-enabled')
}

/**
 * 新建一次俯卧位疗程记录。
 * ICU 外链模式可带 inHospitalSerialNo / inDepartTime，用于同一住院号多次入科时精确定位患者。
 */
export function createArdsProneRecord(inHospitalNo, patientId, startTime, inHospitalSerialNo, inDepartTime) {
  return request.post('/ards-prone/create', null, {
    params: { inHospitalNo, patientId, startTime, inHospitalSerialNo, inDepartTime },
    silentError: true
  })
}

/** 患者解析：外链参数（住院流水号 + 入科时间）→ patient_info.id 与诊断/入院日期 */
export function lookupArdsPronePatient(inHospitalNo, inHospitalSerialNo, inDepartTime) {
  return request.get('/ards-prone/patient/lookup', {
    params: { inHospitalNo, inHospitalSerialNo, inDepartTime },
    silentError: true
  })
}

/** 保存记录头（患者信息、并发症、终止指征、签名等） */
export function saveArdsProneRecord(record) {
  return request.post('/ards-prone/record/save', record, { silentError: true })
}

/** 批量保存单元格值（覆盖自动值须带原因，全程留痕） */
export function saveArdsProneCells(recordId, items) {
  return request.post('/ards-prone/cells/save', items, { params: { recordId }, silentError: true })
}

/** 自动采集某个时点（只填空值，窗口内无数据置空转手工） */
export function collectArdsProneTp(recordId, tpIndex, forceRefresh) {
  return request.post('/ards-prone/collect', null, { params: { recordId, tpIndex, forceRefresh: forceRefresh ? true : undefined }, silentError: true })
}

/** 更正留痕 */
export function fetchArdsProneLogs(recordId) {
  return request.get('/ards-prone/logs', { params: { recordId } })
}

/** 补传文书 PDF（与记录主体保存解耦） */
export function attachArdsPronePdf(id, pdfData, pdfName) {
  return request.post(`/ards-prone/record/${id}/pdf`, { pdfData, pdfName }, { silentError: true })
}

/** 逻辑删除记录 */
export function deleteArdsProneRecord(id) {
  return request.post('/ards-prone/record/delete', null, { params: { id }, silentError: true })
}

/** 列表页结果指标摘要（批量）：P/F 首末值与趋势、最低 ΔP、已填项数 */
export function fetchArdsProneSummary(ids) {
  return request.post('/ards-prone/summary', ids)
}

/** 打印前校验：缺项清单 */
export function checkArdsPronePrint(id) {
  return request.get('/ards-prone/check-print', { params: { id } })
}

// ------------------------------------------------------------ 时点配置

export function addArdsProneTp(recordId, label, offsetMinutes) {
  return request.post('/ards-prone/tp/add', null, { params: { recordId, label, offsetMinutes }, silentError: true })
}

export function updateArdsProneTp(tpId, label, offsetMinutes) {
  return request.post('/ards-prone/tp/update', null, { params: { tpId, label, offsetMinutes }, silentError: true })
}

export function deleteArdsProneTp(tpId) {
  return request.post('/ards-prone/tp/delete', null, { params: { tpId }, silentError: true })
}

export function resetArdsProneTp(recordId) {
  return request.post('/ards-prone/tp/reset', null, { params: { recordId }, silentError: true })
}

export function fetchArdsProneTpl(departCode) {
  return request.get('/ards-prone/tpl', { params: { departCode } })
}

export function saveArdsProneTpl(departCode, items) {
  return request.post('/ards-prone/tpl/save', items, { params: { departCode }, silentError: true })
}

// ------------------------------------------------------------ 归档回传

/** 归档推送：调院方归档接口（与 APACHE II、SOFA 同一接口同一传参），成功后记录标记「已归档」 */
export function pushArdsProneArchive(id) {
  return request.post('/archive/push', null, { params: { biz: 'ARDS_PRONE', id }, silentError: true })
}

/** 撤销归档标记：只改本地状态，不调院方接口 */
export function unmarkArdsProneArchive(id) {
  return request.post('/archive/unmark', null, { params: { biz: 'ARDS_PRONE', id }, silentError: true })
}

// ------------------------------------------------------------ 采集映射配置

/** 映射配置列表（含停用项）：configType / configKey 可空 */
export function fetchArdsProneMap(configType, configKey) {
  return request.get('/ards-prone/map', { params: { configType, configKey } })
}

/** 保存映射配置（id 为空新增） */
export function saveArdsProneMap(body) {
  return request.post('/ards-prone/map/save', body, { silentError: true })
}

export function deleteArdsProneMap(id) {
  return request.post('/ards-prone/map/delete', null, { params: { id }, silentError: true })
}

export function toggleArdsProneMap(id, status) {
  return request.post('/ards-prone/map/toggle', null, { params: { id, status }, silentError: true })
}

/** 一键用内置关键字生成配置（幂等，返回新增条数） */
export function seedArdsProneMap() {
  return request.post('/ards-prone/map/seed', null, { silentError: true })
}

/** 数据元候选：type=observe 监护字典 / lis 近 7 天检验项目 */
export function fetchArdsProneMapCandidates(type, keyword, limit) {
  return request.get('/ards-prone/map/candidates', { params: { type, keyword, limit } })
}

/** 试采（dry-run，不落库）：返回每项命中值与来源 */
export function previewArdsProneCollect(recordId, tpIndex) {
  return request.post('/ards-prone/map/preview', null, { params: { recordId, tpIndex }, silentError: true })
}
