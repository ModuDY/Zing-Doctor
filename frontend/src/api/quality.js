import axios from 'axios'
import request from './request'
import { getExternalHeaders } from '../utils/external'

/**
 * 质控指标中台 API 客户端。
 *
 * 与后端 QualityController 一一对应：一套通用接口服务全部 127 条指标，
 * 指标增减只改 YAML 配置，接口与页面都不随指标变化。
 *
 * 周期参数约定（PeriodRange.of）：
 *   periodType  ∈ MONTH / QUARTER / YEAR / CUSTOM
 *   periodStart 支持 yyyy / yyyy-MM / yyyy-MM-dd / yyyy-MM-dd HH:mm:ss
 *   两者都不传时，后端默认取「上个月」。
 */

/**
 * 科室下拉数据。
 *
 * 质控模块自身没有科室字典接口（后端 departCode 传空即「全院 ALL」），
 * 这里复用 antibiotic/ddd 已暴露的同一份 ICU 科室字典（底层同为
 * IcuPatientMapper.selectAllDepartments），避免为下拉框单开一个接口。
 * silentError：科室下拉取不到不阻断看板，只是少了筛选维度。
 */
export function fetchQualityDepartments() {
  return request.get('/antibiotic/ddd/departments', { silentError: true })
}

/** 指标看板：127 条按域分组 + 本期值（空壳指标同样返回，值列为 null） */
export function fetchQualityOverview(params) {
  return request.get('/quality/overview', { params })
}

/** 单指标详情：口径 + 本期值 + 血缘摘要 + 患者明细 */
export function fetchQualityMetric(code, params) {
  return request.get('/quality/metric', { params: { code, ...params } })
}

/** 追溯第 3 层：算子链 + 编译后 SQL + 扫描行数（按批次号精确回溯） */
export function fetchQualityTrace(runId, code) {
  return request.get('/quality/metric/trace', { params: { runId, code } })
}

/** 追溯第 4 层：患者级命中明细（按需生成，点数字看到人） */
export function fetchQualityPatients(code, params) {
  return request.get('/quality/metric/patients', { params: { code, ...params } })
}

/** 覆盖率报告：可算 / 空壳 / 待接数据源的承诺清单 */
export function fetchQualityCoverage() {
  return request.get('/quality/coverage')
}

/** 事实层定义与编译后 SQL（改 YAML 前先在页面上确认口径） */
export function fetchQualityFacts(periodStart) {
  return request.get('/quality/facts', { params: { periodStart } })
}

/** 最近计算批次（血缘第 1 层） */
export function fetchQualityRuns() {
  return request.get('/quality/runs')
}

/**
 * 触发一次计算（幂等：同周期同科室重算覆盖）。
 *
 * withPatients 默认 false —— 患者明细在下钻时按需生成更快，避免月度批算顺带落库。
 * silentError：失败提示由页面给出（带「重算失败」上下文），避免两条弹窗。
 */
export function recalcQuality(params) {
  return request.post('/quality/recalc', null, { params, silentError: true })
}

/** 人工录入指标值（MANUAL 类指标）；silentError 同 recalcQuality */
export function saveQualityManual(params) {
  return request.post('/quality/manual', null, { params, silentError: true })
}

/** 重新同步指标字典（改完 YAML 热生效，不必重启）；silentError 同上 */
export function syncQualityIndex() {
  return request.post('/quality/sync-index', null, { silentError: true })
}

/** 月度汇总：1-12 月横排视图（含季度/全年合计与极值月） */
export function fetchQualityMonthly(year, departCode) {
  return request.get('/quality/monthly', { params: { year, departCode } })
}

/** 重建某年月度汇总宽表（幂等：先删后建）；silentError 同上 */
export function rebuildQualityMonthly(year, departCode) {
  return request.post('/quality/monthly/rebuild', null, {
    params: { year, departCode },
    silentError: true
  })
}

/**
 * 导出某年质控报表（服务端流式 xlsx，3 个 sheet）。
 *
 * 用裸 axios 而非统一 request：响应体是二进制流，会被统一拦截器
 * 当作 Result 包装解包而报错（与 Apache2Score 拉 PDF 同一处理方式），
 * 因此这里手动注入外链鉴权头并声明 responseType=blob。
 */
export function exportQualityXlsx(year, departCode) {
  return axios.get('/api/quality/export', {
    params: { year, departCode },
    headers: getExternalHeaders(),
    responseType: 'blob',
    timeout: 120000
  })
}
