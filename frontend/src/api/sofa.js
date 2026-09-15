import request from './request'

/** 单患者 SOFA 评估（按 patientId） */
export function fetchSofaAssessment(patientId, startTime, endTime) {
  return request.get(`/sofa/assessment/${patientId}`, { params: { startTime, endTime } })
}

/** 单患者 SOFA 评估（按 ICU 外链住院号） */
export function fetchSofaAssessmentByNo(inHospitalNo, startTime, endTime) {
  return request.get('/sofa/assessment/by-no', { params: { inHospitalNo, startTime, endTime } })
}

/** 保存评分记录（失败提示由页面给出，带“保存失败”上下文，故 silentError） */
export function saveSofaRecord(record, startTime, endTime) {
  return request.post('/sofa/record', record, { params: { startTime, endTime }, silentError: true })
}

/** 患者历史评分 */
export function fetchSofaRecords(inHospitalNo) {
  return request.get('/sofa/records', { params: { inHospitalNo } })
}

/** 逻辑删除评分记录（失败提示由页面给出，带“删除失败”上下文，故 silentError） */
export function deleteSofaRecord(id) {
  return request.post('/sofa/record/delete', null, { params: { id }, silentError: true })
}

/** 科室总览 */
export function fetchSofaOverview(departCode, startTime, endTime) {
  return request.get('/sofa/overview', { params: { departCode, startTime, endTime } })
}

/** 单指标趋势（resp/coag/liver/cardio/neuro/renal/total） */
export function fetchSofaTrend(patientId, metricKey, startTime, endTime) {
  return request.get(`/sofa/metric-trend/${patientId}`, { params: { metricKey, startTime, endTime } })
}

/** 重症系统已评估的 GCS 记录（GCS 弹窗：自动同步最新 / 手动选择用） */
export function fetchSofaGcsRecords(patientId) {
  return request.get(`/sofa/patient/${patientId}/gcs-records`)
}

/** 取某条记录的文书 PDF（Base64 + 文件名） */
export function fetchSofaRecordPdf(id) {
  return request.get(`/sofa/record/${id}/pdf`)
}

/** 补传文书 PDF（与主体保存解耦） */
export function attachSofaRecordPdf(id, pdfData, pdfName) {
  return request.post(`/sofa/record/${id}/pdf`, { pdfData, pdfName })
}

/** 手动触发自动初评（幂等） */
export function autoGenerateSofa(departCode, overHours) {
  return request.post('/sofa/auto-generate', null, { params: { departCode, overHours } })
}

/** 文书归档推送：调院方归档接口，成功后该记录标记「已归档」 */
export function pushSofaArchive(id) {
  return request.post('/archive/push', null, { params: { biz: 'SOFA', id } })
}

/** 撤销归档标记：只改本地状态，不调院方接口 */
export function unmarkSofaArchive(id) {
  return request.post('/archive/unmark', null, { params: { biz: 'SOFA', id } })
}

/** 配置列表（含停用项；configType 为空返回全部） */
export function fetchSofaConfig(configType) {
  return request.get(configType ? `/sofa/config/${configType}` : '/sofa/config')
}

/** 配置保存（id 为空新增，否则更新） */
export function saveSofaConfig(body) {
  return request.post('/sofa/config/save', body)
}

/** 配置删除（逻辑删除） */
export function deleteSofaConfig(id) {
  return request.post('/sofa/config/delete', null, { params: { id } })
}

/** 配置启用/停用 */
export function toggleSofaConfig(id, status) {
  return request.post('/sofa/config/toggle', null, { params: { id, status } })
}
