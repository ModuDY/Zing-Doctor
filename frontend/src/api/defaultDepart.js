import request from './request'

/**
 * 已登录用户从左侧菜单进入列表/总览页时，URL 没带 departCode，
 * 自动按当前账号的科室范围取默认科室。
 *
 * 规则：
 *  - 外链场景（URL 已带 departCode）不要调用本函数，直接用 URL 的值；
 *  - 普通账号只有 1 个科室：自动选那个；
 *  - 管理员或多科室账号：默认取 departs[0]（第一个授权科室），
 *    后端按科室过滤；如需切换，后续在页面上加科室下拉即可；
 *  - 无科室权限：返回空字符串，页面保持"无权限"提示。
 *
 * 复用工作台的 /api/workbench/scope 接口，保证科室边界判断与工作台一致。
 */
export async function resolveDefaultDepartCode() {
  try {
    const scope = await request.get('/workbench/scope')
    if (!scope) return ''
    if (Array.isArray(scope.departs) && scope.departs.length > 0) {
      return scope.departs[0].org_code || ''
    }
    return ''
  } catch (e) {
    console.warn('获取默认科室失败', e)
    return ''
  }
}
