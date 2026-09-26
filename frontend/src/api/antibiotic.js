import request from './request'

/**
 * 疑似感染列表「待决策」判定规则（参数设置页可切换口径）。
 * 页面据此改统计卡与筛选项的文案，避免配了 ADMIT_24H_NEVER 还写着「今日待决策」。
 */
export function fetchPendingRule() {
  return request.get('/sys-param/get', { params: { key: 'ABX_PENDING_DECISION_RULE' } })
}

/**
 * 疑似感染患者列表。
 * departCode 是科室边界：服务端会按当前账号的科室授权校验，
 * 普通账号不传或传越权科室会直接报错（不退回全院）。
 */
export function fetchPatients(departCode) {
  return request.get('/antibiotic/patients', { params: { departCode } })
}

/** 单患者决策页数据（评估 + 推荐方案） */
export function fetchAssessment(patientId) {
  return request.get(`/antibiotic/patients/${patientId}/assessment`)
}

/** 单患者决策页数据（按 ICU 外链住院号定位患者） */
export function fetchAssessmentByNo(inHospitalNo) {
  return request.get('/antibiotic/patients/by-no/assessment', { params: { inHospitalNo } })
}

/** 保存医生决策 */
export function saveDecision(data) {
  return request.post('/antibiotic/decision-record', null, { params: data })
}

/** 更新医生决策记录（编辑历史决策） */
export function updateDecision(data) {
  return request.post('/antibiotic/decision-record/update', null, { params: data })
}

/** 删除医生决策记录 */
export function deleteDecision(id) {
  return request.post('/antibiotic/decision-record/delete', null, { params: { id } })
}

/** 患者决策历史 */
export function fetchRecords(patientId) {
  return request.get(`/antibiotic/patients/${patientId}/records`)
}

/** 职工字典搜索（医生下拉框，支持拼音首字母/工号/姓名） */
export function searchStaff(keyword) {
  return request.get('/antibiotic/staff/search', { params: { keyword } })
}

/** 第二维度：PK/PD 抗菌药物剂量优化（按 patientId） */
export function fetchPkpd(patientId) {
  return request.get(`/antibiotic/patients/${patientId}/pkpd`)
}

/** 第二维度：PK/PD 抗菌药物剂量优化（按 ICU 外链住院号） */
export function fetchPkpdByNo(inHospitalNo) {
  return request.get('/antibiotic/patients/by-no/pkpd', { params: { inHospitalNo } })
}
