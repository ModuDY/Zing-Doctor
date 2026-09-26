import { createRouter, createWebHistory } from 'vue-router'
import { captureExternalContext, hasExternalContext } from '../utils/external'
import { isLoggedIn } from '../utils/auth'
import { currentPatient, hasCurrentPatient } from '../utils/patientContext'

/**
 * 路由规范：
 *  - 所有可外链页面统一挂在 /page/{pageCode} 下，与 sys_page_config.frontend_path 一一对应；
 *  - 新增可外链页面 = 在 sys_page_config 注册一行 + 在此登记一条路由；
 *  - 外链访问统一走后端 /entry/{pageCode}，校验签名后 302 到本路由（并透传 pageCode/expire/sign）。
 */
const routes = [
  { path: '/', redirect: '/page/patient-workbench' },
  {
    path: '/login',
    name: 'login',
    component: () => import('../views/Login.vue'),
    meta: { title: '登录' }
  },
  {
    path: '/page/patient-workbench',
    name: 'patientWorkbench',
    component: () => import('../views/PatientWorkbench.vue'),
    meta: { title: '患者工作台' }
  },
  {
    path: '/page/abx-patient-list',
    name: 'patientList',
    // 已并入患者工作台的「感染风险」视图，不再有独立页面。
    // 老书签、菜单旧链接、ICU 外链（/entry/abx-patient-list 会 302 到这里）一律重定向过去；
    // query 必须原样透传 —— 外链的 pageCode/extToken/expire/sign/departCode 全在 query 里，
    // 丢掉任何一个外链就断了，且症状是"打不开"而不是"打开后报错"，很难排查。
    redirect: (to) => ({ path: '/page/patient-workbench', query: { ...to.query, view: 'infection' } }),
    meta: { title: '疑似感染患者列表（已并入患者工作台）' }
  },
  {
    path: '/page/abx-decision',
    name: 'decision',
    component: () => import('../views/DecisionDetail.vue'),
    meta: { title: '经验性抗感染治疗决策' }
  },
  {
    path: '/page/abx-pkpd',
    name: 'pkpd',
    component: () => import('../views/PkpdDetail.vue'),
    meta: { title: 'PK/PD 抗菌药物剂量优化' }
  },
  {
    path: '/page/abx-ddd',
    name: 'dddOverview',
    component: () => import('../views/DddOverview.vue'),
    meta: { title: '抗菌药物使用强度分析' }
  },
  {
    path: '/page/abx-ddd-patients',
    name: 'dddPatients',
    component: () => import('../views/DddPatients.vue'),
    meta: { title: '抗菌药物使用患者明细' }
  },
  {
    path: '/page/abx-ddd-config',
    name: 'dddConfig',
    component: () => import('../views/DddConfig.vue'),
    meta: { title: 'DDD值配置管理' }
  },
  {
    path: '/page/abx-mdro',
    name: 'mdroOverview',
    component: () => import('../views/MdroOverview.vue'),
    meta: { title: '细菌培养检出监测' }
  },
  {
    path: '/page/abx-mdro-patients',
    name: 'mdroPatients',
    component: () => import('../views/MdroPatients.vue'),
    meta: { title: '细菌培养患者明细' }
  },
  {
    path: '/page/abx-mdro-config',
    name: 'mdroConfig',
    component: () => import('../views/MdroConfig.vue'),
    meta: { title: '细菌分类配置管理' }
  },
  {
    path: '/page/sepsis-bundle',
    name: 'sepsisBundle',
    component: () => import('../views/SepsisBundle.vue'),
    meta: { title: '脓毒症休克集束化治疗' }
  },
  {
    path: '/page/abx-word-config',
    name: 'abxWordConfig',
    component: () => import('../views/AbxWordConfig.vue'),
    meta: { title: '抗菌药物识别词库配置' }
  },
  {
    path: '/page/handover-board',
    name: 'handoverBoard',
    component: () => import('../views/HandoverBoard.vue'),
    meta: { title: '医生交班览表' }
  },
  {
    path: '/page/discharge-stats',
    name: 'dischargeStats',
    component: () => import('../views/DischargeStats.vue'),
    meta: { title: '患者出科统计' }
  },
  {
    path: '/page/ards-monitor',
    name: 'ardsMonitor',
    component: () => import('../views/ARDSMonitor.vue'),
    meta: { title: 'ARDS监测' }
  },
  {
    path: '/page/apache2-overview',
    name: 'apache2Overview',
    component: () => import('../views/Apache2Overview.vue'),
    meta: { title: 'APACHE II评分总览' }
  },
  {
    path: '/page/apache2-score',
    name: 'apache2Score',
    component: () => import('../views/Apache2Score.vue'),
    meta: { title: 'APACHE II评分评估' }
  },
  {
    path: '/page/sofa-score',
    name: 'sofaScore',
    component: () => import('../views/SofaScore.vue'),
    meta: { title: 'SOFA 评分' }
  },
  {
    path: '/page/sofa-overview',
    name: 'sofaOverview',
    component: () => import('../views/SofaOverview.vue'),
    meta: { title: 'SOFA 评分总览' }
  },
  {
    path: '/page/sofa-config',
    name: 'sofaConfig',
    component: () => import('../views/SofaConfig.vue'),
    meta: { title: 'SOFA 配置管理' }
  },
  {
    path: '/page/ards-prone-list',
    name: 'ardsProneList',
    component: () => import('../views/ArdsProneList.vue'),
    meta: { title: 'ARDS 俯卧位通气记录' }
  },
  {
    path: '/page/ards-prone-record',
    name: 'ardsProneRecord',
    component: () => import('../views/ArdsProneRecord.vue'),
    meta: { title: 'ARDS 俯卧位通气记录填写' }
  },
  {
    path: '/page/ards-prone-config',
    name: 'ardsProneConfig',
    component: () => import('../views/ArdsProneConfig.vue'),
    meta: { title: 'ARDS 俯卧位数据映射配置' }
  },
  {
    path: '/page/quality-board',
    name: 'qualityBoard',
    component: () => import('../views/QualityBoard.vue'),
    meta: { title: '质控指标看板' }
  },
  {
    path: '/page/quality-monthly',
    name: 'qualityMonthly',
    component: () => import('../views/QualityMonthly.vue'),
    meta: { title: '质控月度汇总' }
  },
  {
    path: '/page/quality-config',
    name: 'qualityConfig',
    component: () => import('../views/QualityConfig.vue'),
    meta: { title: '质控指标配置' }
  },
  {
    path: '/page/param-config',
    name: 'paramConfig',
    component: () => import('../views/ParamConfig.vue'),
    meta: { title: '参数设置' }
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'notFound',
    component: () => import('../views/NotFound.vue'),
    meta: { title: '页面不存在' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

/**
 * 会把「当前患者」带过去的页面：一次只看一个患者的那几个。
 *
 * <p>列表页（ARDS 俯卧位列表、SOFA / APACHE 概览、疑似感染患者列表、质控看板）刻意
 * 不在其内 —— 给它们注入住院号会把列表过滤成只剩一个人，那不是使用者想要的；
 * 想在列表里看某个人，用页面自己的搜索框即可。
 *
 * <p>patientId 与 inHospitalNo 必须同时带：药企链路（决策 / PK/PD / SOFA）读
 * patientId，APACHE II 与脓毒症只读 inHospitalNo（住院号），少带一个另一半页面照样空着。
 */
const PATIENT_PAGES = new Set([
  '/page/abx-decision',
  '/page/abx-pkpd',
  '/page/sofa-score',
  '/page/apache2-score',
  '/page/sepsis-bundle',
  // 俯卧位「列表」跟随：它会拿住院号去填搜索框，等于只看该患者的记录 ——
  // 使用者要的正是这个。但必须连入科时间一起给：新建记录时后端靠
  // (住院号 + 入科时间) 定位到哪一次入科，只有住院号会退化成「最近一次入科」，
  // 同一住院号多次入科（转科 / 再入院）时会挂错人。
  '/page/ards-prone-list'
  // 俯卧位「填写页」刻意不在内：它只认 record id，没有 id 会直接提示
  // 「请从列表页进入」—— 患者归属在列表页点新建、后端建记录那一刻就固化了，
  // 给它注入任何患者字段都不会被读取。
])

/**
 * 访问控制：两条合法通道，任一满足即放行。
 *   1) 第三方系统外链进入（URL 带 extToken 或 expire+sign）—— 免登录，逻辑保持原有；
 *   2) 已在本系统登录（本地存有 JWT）。
 * 两者都不满足才跳转登录页，并带上来源地址便于登录后原路返回。
 */
router.beforeEach((to) => {
  // 外链打开时捕获签名上下文（pageCode/expire/sign），供 /api 调用鉴权
  captureExternalContext()
  document.title = to.meta.title ? `${to.meta.title} · 医生决策系统` : '医生决策系统'

  if (to.name === 'login') {
    // 已登录时不该再看登录页
    return isLoggedIn() ? { path: '/' } : true
  }

  // 已选中患者时把它带进单患者页面。侧边栏菜单是纯 <router-link>，切换时不带任何
  // 参数，不补的话这些页面读不到患者 —— 这就是「切到别的菜单患者就丢了」的原因。
  // 页面自己带了患者参数时不覆盖（例如从其它列表点进来另一个人）。
  if (hasCurrentPatient() && PATIENT_PAGES.has(to.path)
      && !to.query.patientId && !to.query.inHospitalNo) {
    return {
      path: to.path,
      query: {
        ...to.query,
        patientId: currentPatient.patientId,
        inHospitalNo: currentPatient.inHospitalNo,
        // ARDS 俯卧位用它定位入科次（缺了会退化到「最近一次入科」，可能挂错人）
        // 后端会自行把传入文本解析到分钟级，带不带秒都能对上
        inDepartTime: currentPatient.inDepartTime,
        departCode: currentPatient.departCode,
        // APACHE II 单独读 patientName 做页面标题；工作台里的姓名已是脱敏值（如「张*」）
        ...(currentPatient.name ? { patientName: currentPatient.name } : {})
      },
      replace: true
    }
  }

  if (hasExternalContext() || isLoggedIn()) {
    return true
  }
  return { path: '/login', query: { redirect: to.fullPath } }
})

export default router
