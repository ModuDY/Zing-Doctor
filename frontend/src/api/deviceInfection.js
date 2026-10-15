import request from './request'

/**
 * 导管相关感染确诊录入（VAP / CRBSI / CAUTI）。
 * 确诊记录驱动质控导管域指标 quality_24 / quality_26 / quality_425
 * 与规则层三条「例/千导管日」发病率。
 */
export function fetchDeviceInfections(patientId, infectionType) {
  return request.get('/device-infection/records', {
    params: { patientId, infectionType },
    silentError: true
  })
}

export function saveDeviceInfection(data) {
  return request.post('/device-infection/save', data)
}

export function deleteDeviceInfection(id, patientId) {
  return request.delete(`/device-infection/records/${id}`, { params: { patientId } })
}
