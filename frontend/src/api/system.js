import request from './request'

/** 交付包版本、提交号和数据源模式。 */
export function fetchBuildInfo() {
  return request.get('/system/build-info')
}

/**
 * 健康接口故意允许 503：现场自检页需要把 DOWN 的明细展示出来，
 * 不能被 request 拦截器改成一条“请求失败”后丢掉上下文。
 */
export async function fetchHealth() {
  const response = await fetch('/api/health', { headers: { Accept: 'application/json' } })
  const body = await response.json()
  return { httpStatus: response.status, ...body }
}
