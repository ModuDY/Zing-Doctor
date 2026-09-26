import { reactive, watch } from 'vue'

/**
 * 当前患者上下文 —— 在患者工作台选中一人后，切到任何单患者页面都还是这个人。
 *
 * <p>为什么需要它：侧边栏菜单是纯 {@code <router-link to="/page/xxx">}，
 * 切换时不会带任何 query 参数，而各业务页面都从 {@code route.query} 读患者。
 * 于是在工作台选完人、从菜单点进 SOFA / APACHE II 之类的页面，
 * 患者就"丢了"—— 页面读不到 patientId，只能空着等用户自己填。
 *
 * <p>落点选择：全局 reactive + sessionStorage，不引入 Pinia。
 * 这个上下文只有一个消费者维度（当前患者），引入状态管理库是过度设计；
 * 而必须持久化到 sessionStorage —— 页面一刷新，内存里的 reactive 就没了，
 * 病人也就跟着丢了。会话级（关标签页即清）正合适：
 * 换个医生用同一台机器，不该继承上一个人的患者。
 *
 * <p>两个字段都要存：各页面读的不一样 ——
 * 药企链路（决策 / PKPD / SOFA）读 {@code patientId}，
 * APACHE II 与脓毒症只读 {@code inHospitalNo}（住院号）。
 * 少存一个，另一半页面照样是空的。
 */
const STORAGE_KEY = 'zing_current_patient'
const WORKBENCH_REFRESH_KEY = 'zing_workbench_refresh_at'

const EMPTY = {
  patientId: '',
  inHospitalNo: '',
  // 入科时间：ARDS 俯卧位靠它（配合住院号）定位到「哪一次入科」。
  // 页面与接口认的参数名是 inDepartTime，不是工作台里的 inDepartmentTime，
  // 存的时候就按前者命名 —— 名字不对会被静默忽略。
  inDepartTime: '',
  name: '',
  bedNo: '',
  departCode: '',
  departName: ''
}

export const currentPatient = reactive({ ...EMPTY })

// 刷新后恢复。解析失败就当没选过 —— 缓存损坏不该让整个页面打不开
try {
  const saved = JSON.parse(sessionStorage.getItem(STORAGE_KEY) || 'null')
  if (saved && saved.patientId) {
    Object.assign(currentPatient, saved)
  }
} catch (e) {
  sessionStorage.removeItem(STORAGE_KEY)
}

watch(currentPatient, (v) => {
  if (v && v.patientId) {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(v))
  } else {
    sessionStorage.removeItem(STORAGE_KEY)
  }
}, { deep: true })

/** 在工作台选中一个患者（覆盖式：同时只存在一个当前患者） */
export function setCurrentPatient(row) {
  Object.assign(currentPatient, {
    patientId: row?.patientId || '',
    inHospitalNo: row?.inHospitalNo || '',
    // 工作台字段叫 inDepartmentTime，ARDS 页面与后端认的是 inDepartTime
    inDepartTime: row?.inDepartmentTime || '',
    // 姓名在工作台里已经是脱敏值（如「张*」），这里不另做处理
    name: row?.name || '',
    bedNo: row?.bedNo || '',
    departCode: row?.departCode || '',
    departName: row?.departName || row?.wardName || ''
  })
}

/** 清除当前患者：切科室、换病人、或不想再被它影响时使用 */
export function clearCurrentPatient() {
  Object.assign(currentPatient, EMPTY)
}

/** 评分/决策等单患者页面保存成功后通知患者工作台重新取数。 */
export function markWorkbenchRefresh(reason = 'patient-updated') {
  const value = `${Date.now()}:${reason}`
  sessionStorage.setItem(WORKBENCH_REFRESH_KEY, value)
  window.dispatchEvent(new CustomEvent('zing:workbench-refresh', { detail: { value, reason } }))
}

export function workbenchRefreshToken() {
  return sessionStorage.getItem(WORKBENCH_REFRESH_KEY) || ''
}
export function hasCurrentPatient() {
  return !!currentPatient.patientId
}

/** 顶栏展示用：床位和姓名至少得有一个，否则显示住院号 */
export function currentPatientLabel() {
  const p = currentPatient
  if (!p.patientId) return ''
  const who = p.name || p.inHospitalNo || '未知'
  return p.bedNo ? `${p.bedNo}床 ${who}` : who
}
