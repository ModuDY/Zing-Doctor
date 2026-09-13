import { createRouter, createWebHistory } from 'vue-router'
import { captureExternalContext } from '../utils/external'

/**
 * 路由规范：
 *  - 所有可外链页面统一挂在 /page/{pageCode} 下，与 zing_page_config.frontend_path 一一对应；
 *  - 新增可外链页面 = 在 zing_page_config 注册一行 + 在此登记一条路由；
 *  - 外链访问统一走后端 /entry/{pageCode}，校验签名后 302 到本路由（并透传 pageCode/expire/sign）。
 */
const routes = [
  { path: '/', redirect: '/page/abx-patient-list' },
  {
    path: '/page/abx-patient-list',
    name: 'patientList',
    component: () => import('../views/PatientList.vue'),
    meta: { title: '疑似感染患者列表' }
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

router.beforeEach((to) => {
  // 外链打开时捕获签名上下文（pageCode/expire/sign），供 /api 调用鉴权
  captureExternalContext()
  document.title = to.meta.title ? `${to.meta.title} · 医生系统` : '医生系统'
})

export default router
