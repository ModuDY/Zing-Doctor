import request from './request'

/**
 * 按工号取电子签名（评分文书「评分医师」右侧的签名图）。
 *
 * 工号取外链参数 username（ICU 登录人工号）。silentError：签名是文书锦上添花，
 * 取不到（未配签名 / ICU 库暂时不可用）不应弹错误打断医生开文书。
 */
export function fetchStaffSignature(workNo) {
  return request.get('/staff/signature', { params: { workNo }, silentError: true })
}

/**
 * 职工检索（文书签名 / 经管医师等人员字段用）。
 *
 * 支持拼音首字母 / 工号 / 姓名模糊匹配，最多 50 条；keyword 为空时按排序取前 50 条。
 * silentError：查不到或 ICU 库不可用时返回空列表即可，不应弹错误打断填写 ——
 * 这类人员字段始终保留手工输入能力（进修、外院会诊等不在职工库里的人也要能签）。
 */
export function searchStaff(keyword) {
  return request.get('/staff/search', { params: { keyword }, silentError: true })
}
