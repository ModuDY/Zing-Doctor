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
