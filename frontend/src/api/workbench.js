import request from './request'

/** ICU 在科患者工作台（仅基本信息） */
export function fetchInpatients() {
  return request.get('/workbench/patients')
}
