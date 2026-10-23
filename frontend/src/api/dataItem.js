import request from './request'

/**
 * 数据项映射配置 API（/page/data-item-config）。
 *
 * 这是换院部署时唯一需要改的取数层：各院 ICU 的观察项编码、LIS 项目名都不一样，
 * 但页面要展示的指标是同一套。item_key 是程序内部键（hr / peep / alt），
 * match_value 才是本院实际的值。
 *
 * 所有取数页面共用这一套配置 —— 同一批编码被生命体征、交班、ARDS、SOFA、
 * APACHE II 等 13 个服务引用，按页面拆开必然配漏。
 */

/** 业务域元信息（Tab 名 + 是否已接入取数） */
export function fetchDataItemDomains() {
  return request.get('/config/data-item/domains', { silentError: true })
}

/** 配置列表，domain 为空 = 全部 */
export function fetchDataItems(domain) {
  return request.get('/config/data-item/list', { params: { domain }, silentError: true })
}

/** 新增 / 编辑（id 为空即新增） */
export function saveDataItem(row) {
  return request.post('/config/data-item/save', row, { silentError: true })
}

/** 启用 / 停用 */
export function setDataItemStatus(id, status) {
  return request.post('/config/data-item/status', null, {
    params: { id, status },
    silentError: true
  })
}

/** 删除配置行 */
export function deleteDataItem(id) {
  return request.post('/config/data-item/delete', null, { params: { id }, silentError: true })
}

/**
 * 候选值：从 ICU 源库拉真实存在的项目（按使用人数倒序），现场不用猜编码。
 * itemKey 只有 DICT（术语字典）用得上：体温查观察项字典，其余查 LIS 项目名。
 */
export function fetchDataItemCandidates(domain, itemKey, keyword) {
  return request.get('/config/data-item/candidates', {
    params: { domain, itemKey, keyword },
    silentError: true,
    timeout: 60000
  })
}

/** 试跑：按匹配值统计源库命中条数并返回最新样例 */
export function probeDataItem(domain, itemKey, value, days = 7) {
  return request.get('/config/data-item/probe', {
    params: { domain, itemKey, value, days },
    silentError: true,
    timeout: 60000
  })
}
