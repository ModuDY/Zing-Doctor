import { reactive, watch } from 'vue'

/**
 * 当前科室上下文 —— 全局科室切换下拉选中的科室。
 *
 * <p>为什么需要它：之前各列表页（出科统计 / APACHE II 总览 / ARDS 监测 /
 * 工作台 / 交班览表）都各自从 URL query 取 departCode，从左侧菜单直接进时
 * URL 没有这个参数，就只能显示"缺少科室权限参数"。多科室账号也没法切换。
 *
 * <p>统一规则：
 *  - 外链访问（URL 带 departCode）：以 URL 为准，不覆盖全局（第三方系统指定了科室）；
 *  - 已登录从菜单进入：读全局 context；context 为空时由 MainLayout 下拉自动选第一个；
 *  - 用户在侧边栏下拉切换科室：写全局 context，所有读 context 的页面切换后重新加载。
 *
 * <p>与 patientContext 一致：reactive + sessionStorage，刷新后保留选择。
 */
const STORAGE_KEY = 'zing_current_depart'

const EMPTY = {
  departCode: '',
  departName: ''
}

export const currentDepart = reactive({ ...EMPTY })

try {
  const saved = JSON.parse(sessionStorage.getItem(STORAGE_KEY) || 'null')
  if (saved && saved.departCode) {
    Object.assign(currentDepart, saved)
  }
} catch (e) {
  sessionStorage.removeItem(STORAGE_KEY)
}

watch(currentDepart, (v) => {
  if (v && v.departCode) {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(v))
  } else {
    sessionStorage.removeItem(STORAGE_KEY)
  }
}, { deep: true })

export function setCurrentDepart(dep) {
  if (!dep) {
    clearCurrentDepart()
    return
  }
  Object.assign(currentDepart, {
    departCode: dep.org_code || dep.departCode || '',
    departName: dep.depart_name || dep.departName || ''
  })
}

export function clearCurrentDepart() {
  Object.assign(currentDepart, EMPTY)
}

export function hasCurrentDepart() {
  !!currentDepart.departCode
}

/**
 * 页面解析科室优先级：URL query > 全局 context > 空。
 * 外链场景 URL 一定带 departCode；菜单进入走 context。
 */
export function resolvePageDepartCode(queryDepartCode) {
  const fromQuery = (queryDepartCode || '').toString().trim()
  if (fromQuery && !fromQuery.includes('${')) {
    return fromQuery
  }
  return currentDepart.departCode || ''
}
