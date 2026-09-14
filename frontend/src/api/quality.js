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
 * 页面请传 async: true —— 批算要重建全部事实层，常以分钟计，同步等会撞上
 * 60s 超时（用户看到假「失败」而后端还在跑）；异步只返回 runId，随后轮询 fetchQualityRun。
 * withPatients 默认 false：患者明细在下钻时按需生成更快。
 * silentError：失败提示由页面给出（带「重算失败」上下文），避免两条弹窗。
 */
export function recalcQuality(params) {
  return request.post('/quality/recalc', null, { params, silentError: true, timeout: 120000 })
}

/** 查询批次进度（配合 recalcQuality({ async: true }) 轮询） */
export function fetchQualityRun(runId) {
  return request.get('/quality/run', { params: { runId }, silentError: true, timeout: 20000 })
}

/**
 * 单指标重算：只算这一条，秒级返回。
 *
 * 解决「只改了一条指标的口径，却要等 127 条全跑完」。返回里的 result 可直接就地更新该行。
 */
export function recalcQualityMetric(code, params) {
  return request.post('/quality/recalc/metric', null, {
    params: { code, ...params },
    silentError: true,
    timeout: 180000
  })
}

/** 人工录入指标值（MANUAL 类指标）；silentError 同 recalcQuality */
export function saveQualityManual(params) {
  return request.post('/quality/manual', null, { params, silentError: true })
}

/**
 * 重读配置并重新同步指标字典（改完 YAML 热生效，不必重启）；silentError 同上。
 *
 * 后端是「reload 重读配置 + 落库」两步：仅落库不会让配置文件里的修改生效。
 * 前提是配置位于 jar 外部目录（zing.quality.config-dir），否则重读的仍是打包时的旧文件。
 */
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

// ---------------------------------------------------------------------------
// 可视化配置（QualityConfigController）
//
// 与上面读接口分开的原因：这一组会写配置表、改变计算口径，风险等级完全不同。
// 所有写接口都带 silentError —— 「校验没过」「真源是 YAML」「保存成功」需要三种
// 截然不同的引导文案，交给页面按业务上下文给出，比统一弹一句「操作未成功」有用得多。
// 试跑类接口 timeout 放宽：它要真跑一遍达梦，慢的时候不止 60s。
// ---------------------------------------------------------------------------

/**
 * 给配置写请求附加写接口令牌。
 *
 * 写保护由服务端判定（IP 白名单 / 令牌），二者取其一即可：
 *   - 走 IP 白名单时完全不需要令牌，本函数原样返回；
 *   - 走令牌时用构建变量 VITE_QUALITY_CONFIG_WRITE_TOKEN 注入，
 *     这样令牌不进代码库、也不必让使用者手工填。
 * 注意令牌是构建期常量，会出现在前端产物里，因此只适合内网过渡期使用；
 * 真正长期方案是接入 SSO 后改为角色判定（见 QualityConfigGuard）。
 */
function withWriteToken(config = {}) {
  const token = import.meta.env.VITE_QUALITY_CONFIG_WRITE_TOKEN
  if (!token) {
    return config
  }
  return { ...config, headers: { ...(config.headers || {}), 'X-Quality-Config-Token': token } }
}

/**
 * 配置真源与可写状态：页面打开时先调，据此决定可编辑还是只读预览。
 *
 * 返回里除了 configSource/writable，还有 writeAllowed 与 writeHint：
 * 让页面「一进来」就能说明白能不能改、为什么不能改，而不是等点保存才收到 403。
 */
export function fetchQualityConfigStatus() {
  return request.get('/quality/config/status')
}

/** 指标列表（不含表达式正文） */
export function fetchConfigMetrics(params) {
  return request.get('/quality/config/metrics', { params })
}

/** 指标详情：可直接编辑的口径对象 */
export function fetchConfigMetric(code) {
  return request.get('/quality/config/metric', { params: { code } })
}

/** 只校验不保存（trial=true 时后端会真跑一遍，返回试算值与编译 SQL） */
export function validateConfigMetric(metric, trial = true) {
  return request.post('/quality/config/metric/validate', metric, {
    params: { trial },
    silentError: true,
    timeout: 180000
  })
}

/**
 * 保存指标：校验 → 落库 → 留快照 → 热生效。
 *
 * 不再传 operator：操作人由服务端从外链参数/网关头解析，
 * 前端传的值一律被忽略 —— 否则审计字段等于由被审计者自己填写。
 */
export function saveConfigMetric(metric, trial = true) {
  return request.post('/quality/config/metric', metric, withWriteToken({
    params: { trial },
    silentError: true,
    timeout: 180000
  }))
}

/** 停用指标（不删除，历史结果保留） */
export function disableConfigMetric(code) {
  return request.post('/quality/config/metric/disable', null, withWriteToken({
    params: { code },
    silentError: true
  }))
}

/** 导出全部生效中的指标口径（含表达式正文，可直接回灌导入） */
export function exportQualityMetricsConfig() {
  return request.get('/quality/config/metrics/export', { timeout: 120000 })
}

/**
 * 批量导入指标口径（部分成功：逐条返回结论）。
 *
 * trial 固定 false —— 逐条真跑会拖死页面，批量场景依赖 L1-L3 静态校验，
 * 试跑留给单条编辑。
 */
export function importQualityMetricsConfig(metrics, mode = 'skip') {
  return request.post('/quality/config/metrics/import', { metrics, mode, trial: false },
    withWriteToken({ silentError: true, timeout: 300000 }))
}

export function fetchConfigFacts() {
  return request.get('/quality/config/facts')
}

export function fetchConfigFact(fact) {
  return request.get('/quality/config/fact', { params: { fact } })
}

export function validateConfigFact(fact, trial = true) {
  return request.post('/quality/config/fact/validate', fact, {
    params: { trial },
    silentError: true,
    timeout: 180000
  })
}

export function saveConfigFact(fact, trial = true) {
  return request.post('/quality/config/fact', fact, withWriteToken({
    params: { trial },
    silentError: true,
    timeout: 180000
  }))
}

/** 事实层可引用列（简单模式的字段下拉数据源；取不到不阻断编辑，退化为手工输入） */
export function fetchFactFields(fact) {
  return request.get('/quality/config/fields', { params: { fact }, silentError: true })
}

/** 影响面：哪些指标引用了该事实层（改事实层前必看） */
export function fetchFactImpact(fact) {
  return request.get('/quality/config/impact', { params: { fact } })
}

/** 当前生效的事实层 SQL */
export function fetchConfigFactSql(fact) {
  return request.get('/quality/config/fact/sql', { params: { fact }, silentError: true })
}

/** 变更历史（每次保存留一份完整快照） */
export function fetchQualityConfigHistory(params) {
  return request.get('/quality/config/history', { params })
}

/** 回滚到指定历史版本（操作人同样由服务端解析） */
export function rollbackQualityConfig(historyId) {
  return request.post('/quality/config/rollback', null, withWriteToken({
    params: { historyId },
    silentError: true
  }))
}

/** 手动重载配置 + 同步字典（保存时已自动执行，此处用于排查与恢复） */
export function reloadQualityConfig() {
  return request.post('/quality/config/reload', null, withWriteToken({ silentError: true }))
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
