<template>
  <div class="main-layout">
    <!-- 侧边栏 -->
    <aside class="sidebar">
      <div class="sidebar-logo">
        <img v-if="sidebarLogoVisible" class="logo-icon" :src="sidebarLogoUrl" alt="医院logo">
        <div class="logo-text">
          <div class="logo-title">医生决策系统</div>
        </div>
      </div>

      <div v-if="loggedIn && !isExternalLink" class="sidebar-dept">
        <span class="dept-label">科室</span>
        <el-select v-model="selectedDepart" size="small" placeholder="选择科室" style="width:100%" @change="onDepartChange">
          <el-option v-for="d in departs" :key="d.org_code" :label="d.depart_name" :value="d.org_code" />
        </el-select>
      </div>

      <nav class="sidebar-nav">
        <router-link to="/page/patient-workbench" class="nav-item" active-class="nav-active">
          <MedicalIcon name="patient" />
          <span class="nav-label">患者工作台</span>
        </router-link>
        <router-link to="/page/handover-board" class="nav-item" active-class="nav-active">
          <MedicalIcon name="handover" />
          <span class="nav-label">医生交班览表</span>
        </router-link>
        <router-link to="/page/discharge-stats" class="nav-item" active-class="nav-active">
          <MedicalIcon name="bed" />
          <span class="nav-label">患者出科统计</span>
        </router-link>
        <router-link to="/page/abx-decision" class="nav-item" active-class="nav-active">
          <MedicalIcon name="pill" />
          <span class="nav-label">抗感染决策</span>
        </router-link>
        <router-link to="/page/abx-pkpd" class="nav-item" active-class="nav-active">
          <MedicalIcon name="trend" />
          <span class="nav-label">PK/PD 剂量优化</span>
        </router-link>
        <router-link to="/page/abx-ddd" class="nav-item" active-class="nav-active">
          <MedicalIcon name="bottle" />
          <span class="nav-label">使用强度分析</span>
        </router-link>
        <router-link to="/page/abx-mdro" class="nav-item" active-class="nav-active">
          <MedicalIcon name="dish" />
          <span class="nav-label">细菌培养监测</span>
        </router-link>

        <div class="nav-divider">重症评分</div>
        <router-link to="/page/sofa-overview" class="nav-item" active-class="nav-active">
          <MedicalIcon name="trend" />
          <span class="nav-label">SOFA 评分总览</span>
        </router-link>
        <router-link to="/page/sofa-score" class="nav-item" active-class="nav-active">
          <MedicalIcon name="score" />
          <span class="nav-label">SOFA 评分</span>
        </router-link>
        <router-link to="/page/apache2-score" class="nav-item" active-class="nav-active">
          <MedicalIcon name="gauge" />
          <span class="nav-label">APACHE II 评分</span>
        </router-link>
        <router-link to="/page/apache2-overview" class="nav-item" active-class="nav-active">
          <MedicalIcon name="board" />
          <span class="nav-label">APACHE II 总览</span>
        </router-link>
        <router-link to="/page/sepsis-bundle" class="nav-item" active-class="nav-active">
          <MedicalIcon name="heart" />
          <span class="nav-label">脓毒症集束化治疗</span>
        </router-link>
        <router-link to="/page/ards-monitor" class="nav-item" active-class="nav-active">
          <MedicalIcon name="lungs" />
          <span class="nav-label">ARDS 监测</span>
        </router-link>
        <router-link to="/page/ards-prone-list" class="nav-item" active-class="nav-active">
          <MedicalIcon name="prone" />
          <span class="nav-label">ARDS 俯卧位记录</span>
        </router-link>

        <div class="nav-divider">质控中台</div>
        <router-link to="/page/quality-board" class="nav-item" active-class="nav-active">
          <MedicalIcon name="board" />
          <span class="nav-label">质控指标看板</span>
        </router-link>
        <router-link to="/page/quality-monthly" class="nav-item" active-class="nav-active">
          <MedicalIcon name="calendar" />
          <span class="nav-label">质控月度汇总</span>
        </router-link>
        <router-link to="/page/quality-config" class="nav-item" active-class="nav-active">
          <MedicalIcon name="tools" />
          <span class="nav-label">质控指标配置</span>
        </router-link>

        <div class="nav-divider">配置管理</div>
        <router-link to="/page/abx-ddd-config" class="nav-item" active-class="nav-active">
          <MedicalIcon name="bottle" />
          <span class="nav-label">DDD 值配置</span>
        </router-link>
        <router-link to="/page/abx-mdro-config" class="nav-item" active-class="nav-active">
          <MedicalIcon name="taxonomy" />
          <span class="nav-label">细菌分类配置</span>
        </router-link>
        <router-link to="/page/abx-word-config" class="nav-item" active-class="nav-active">
          <MedicalIcon name="memo" />
          <span class="nav-label">抗菌词库配置</span>
        </router-link>
        <router-link to="/page/sofa-config" class="nav-item" active-class="nav-active">
          <MedicalIcon name="settings" />
          <span class="nav-label">SOFA 配置</span>
        </router-link>
        <router-link to="/page/ards-prone-config" class="nav-item" active-class="nav-active">
          <MedicalIcon name="mapping" />
          <span class="nav-label">俯卧位映射配置</span>
        </router-link>

        <div class="nav-divider">系统设置</div>
        <router-link to="/page/param-config" class="nav-item" active-class="nav-active">
          <MedicalIcon name="settings" />
          <span class="nav-label">参数设置</span>
        </router-link>
        <router-link to="/page/system-check" class="nav-item" active-class="nav-active">
          <MedicalIcon name="check" />
          <span class="nav-label">交付自检</span>
        </router-link>
      </nav>

      <div class="sidebar-footer">
        <!-- 当前患者：选中后切到任何单患者页面都还是这个人；不想被它影响就清除。
             必须让它看得见 —— 否则「这个页面为什么只有这一个病人」无从解释。 -->
        <div v-if="patient.patientId" class="footer-patient">
          <div class="patient-head">
            <span class="patient-title">当前患者</span>
            <button class="patient-clear" title="清除当前患者" @click="handleClearPatient">清除</button>
          </div>
          <div class="patient-name">{{ patientLabel }}</div>
          <div class="patient-sub">{{ patient.departName || '未分配科室' }} · {{ patient.inHospitalNo }}</div>
        </div>
        <div class="footer-user">
          <div class="user-avatar">{{ userInitial }}</div>
          <div class="user-info">
            <div class="user-name">{{ userName }}</div>
            <div class="user-role">{{ userRoleText }}</div>
          </div>
          <!-- 仅直连登录时出现；外链访问由第三方系统控制会话，不显示 -->
          <button v-if="loggedIn" class="logout-btn" title="退出登录" @click="handleLogout">退出</button>
        </div>
      </div>
    </aside>

    <!-- 主内容区 -->
    <main class="main-content">
      <slot />
    </main>
  </div>
</template>

<script>
import { h } from 'vue'
import { ElMessageBox } from 'element-plus'
import { logout as logoutApi } from '../api/auth'
import { getUser, isLoggedIn, clearSession } from '../utils/auth'
import { currentPatient, clearCurrentPatient, currentPatientLabel } from '../utils/patientContext'
import { currentDepart, setCurrentDepart } from '../utils/departContext'
import request from '../api/request'

const MEDICAL_ICON_PATHS = {
  patient: '<circle cx="12" cy="8" r="3"/><path d="M5 21c.5-4 2.8-6 7-6s6.5 2 7 6M19 4v6M16 7h6"/>',
  handover: '<rect x="5" y="3" width="14" height="18" rx="2"/><path d="M8 8h8M8 12h5M8 16h3M15 15l2 2 3-4"/>',
  bed: '<path d="M3 18v-5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2v5M3 18h18M7 11V8a1 1 0 0 1 1-1h2a1 1 0 0 1 1 1v3"/>',
  pill: '<path d="M7 17 17 7a4 4 0 0 1 5.7 5.7l-10 10A4 4 0 0 1 7 17Z"/><path d="m10 14 5 5"/>',
  bottle: '<path d="M8 3h8M9 3v4h6V3M7 7h10v14H7zM10 12h4M12 10v4"/>',
  dish: '<path d="M4 9h16l-1 8a3 3 0 0 1-3 3H8a3 3 0 0 1-3-3zM4 9c2 2 4-2 6 0s4 2 6 0 3 2 4 0"/><circle cx="9" cy="14" r="1"/><circle cx="14" cy="16" r="1"/>',
  trend: '<path d="M4 19V5M4 19h17M7 15l3-4 3 2 5-7"/><circle cx="7" cy="15" r="1"/><circle cx="18" cy="6" r="1"/>',
  score: '<rect x="5" y="3" width="14" height="18" rx="2"/><path d="M9 12l2 2 4-4"/>',
  gauge: '<path d="M4 17a8 8 0 1 1 16 0M12 17l4-6M6 20h12"/><circle cx="12" cy="17" r="1.5"/>',
  heart: '<path fill="currentColor" stroke="none" d="M12 20S4 15.2 4 9.5A4.5 4.5 0 0 1 12 7a4.5 4.5 0 0 1 8 2.5C20 15.2 12 20 12 20Z"/><path d="M3 12h3l2-4 3 8 2-4h3l2-3 2 3h2"/>',
  lungs: '<path d="M12 5v14M12 9c-2-3-4-4-5-3-2 1-4 5-4 9 0 3 2 5 5 5 2 0 4-2 4-5M12 9c2-3 4-4 5-3 2 1 4 5 4 9 0 3-2 5-5 5-2 0-4-2-4-5"/>',
  prone: '<circle cx="7" cy="9" r="2"/><path d="M9 10l6 2 4-2M10 13l-3 4M15 12l3 4M4 5a8 8 0 0 1 13-1M18 4l-1-3M18 4l3-1"/>',
  board: '<path d="M4 20V10M10 20V4M16 20v-7M22 20H2"/>',
  calendar: '<rect x="4" y="5" width="16" height="15" rx="2"/><path d="M8 3v4M16 3v4M4 9h16M8 13h2M13 13h2M8 17h2"/>',
  tools: '<path d="M14 6a4 4 0 0 0-5 5L4 16a2 2 0 1 0 3 3l5-5a4 4 0 0 0 5-5l-3 3-2-2zM17 16l4 4"/>',
  taxonomy: '<circle cx="7" cy="7" r="2"/><circle cx="17" cy="7" r="2"/><circle cx="12" cy="17" r="2"/><path d="M9 7h6M8 9l3 6M16 9l-3 6"/>',
  memo: '<rect x="5" y="3" width="14" height="18" rx="2"/><path d="M8 8h8M8 12h5M8 16h8M15 12v4M13 14h4"/>',
  mapping: '<circle cx="6" cy="7" r="2"/><circle cx="18" cy="17" r="2"/><path d="M8 7h4a4 4 0 0 1 4 4v4M16 17h-4a4 4 0 0 1-4-4V9"/>',
  settings: '<circle cx="12" cy="12" r="3"/><path d="M19 12a7 7 0 0 0-.1-1.2l2-1.5-2-3.4-2.3 1a7 7 0 0 0-2-1.2L14.3 3h-4.6l-.3 2.7a7 7 0 0 0-2 1.2l-2.3-1-2 3.4 2 1.5A7 7 0 0 0 5 12c0 .4 0 .8.1 1.2l-2 1.5 2 3.4 2.3-1a7 7 0 0 0 2 1.2l.3 2.7h4.6l.3-2.7a7 7 0 0 0 2-1.2l2.3 1 2-3.4-2-1.5c.1-.4.1-.8.1-1.2Z"/>',
  check: '<path d="M12 3l7 3v5c0 4.5-3 8-7 10-4-2-7-5.5-7-10V6zM8 12l2.5 2.5L16 9"/>'
}

const MedicalIcon = {
  name: 'MedicalIcon',
  props: { name: { type: String, required: true } },
  render() {
    return h('span', { class: 'nav-icon', 'aria-hidden': 'true' }, [
      h('svg', {
        viewBox: '0 0 24 24',
        fill: 'none',
        stroke: 'currentColor',
        'stroke-width': '1.7',
        'stroke-linecap': 'round',
        'stroke-linejoin': 'round',
        innerHTML: MEDICAL_ICON_PATHS[this.name] || MEDICAL_ICON_PATHS.memo
      })
    ])
  }
}

export default {
  name: 'MainLayout',
  components: { MedicalIcon },
  data() {
    return {
      departs: [],
      selectedDepart: currentDepart.departCode || '',
      sidebarLogoUrl: '/logo.png',
      sidebarLogoVisible: true
    }
  },
  computed: {
    /** 全局患者上下文（响应式单例）：工作台选中一人后，切菜单也不会丢 */
    patient() {
      return currentPatient
    },
    patientLabel() {
      return currentPatientLabel()
    },
    loggedIn() {
      return isLoggedIn()
    },
    /** 外链访问（URL 带 extToken 或 departCode）：科室由第三方系统指定，不显示全局切换下拉 */
    isExternalLink() {
      const q = new URLSearchParams(window.location.search)
      return !!(q.get('extToken') || q.get('departCode'))
    },
    userName() {
      // 优先展示第三方/外链传入的姓名；没有时退回登录账号，兼顾两种访问方式
      const u = getUser()
      const name = sessionStorage.getItem('doctor_realname')
      if (name) return name
      const params = new URLSearchParams(window.location.search)
      return params.get('realname') || (u && (u.realName || u.username)) || '医生'
    },
    userRoleText() {
      return this.loggedIn ? '已登录' : 'ICU 医生'
    },
    userInitial() {
      const n = this.userName
      return n ? n.charAt(0) : '医'
    }
  },
  async mounted() {
    // 侧边栏品牌配置（logo显示开关 + logo图片）：所有访问方式都需要，先于科室加载
    this.loadSidebarConfig()
    // 已登录用户加载授权科室列表；外链访问不加载（URL 已指定科室）
    if (!this.loggedIn || this.isExternalLink) return
    try {
      const scope = await request.get('/workbench/scope')
      if (scope && Array.isArray(scope.departs)) {
        this.departs = scope.departs
        // 只有一个科室时无歧义，直接用；有多个时不替用户猜 ——
        // 自动选中第一个会让医生以为「看到的就是全部」，实际只是其中一个科室，
        // 而且这种误解不会报错、看着也像对的。留空交由各页面提示用户来选。
        if (!this.selectedDepart && this.departs.length === 1) {
          this.selectedDepart = this.departs[0].org_code
          this.onDepartChange(this.selectedDepart)
        }
      }
    } catch (e) {
      console.warn('加载科室列表失败', e)
    }
  },
  methods: {
    /**
     * 侧边栏品牌配置：
     *  - SIDEBAR_SHOW_LOGO：是否显示医院logo（switch，默认开）
     *  - DOC_HOSPITAL_LOGO：logo图片（image，默认 /logo.png），与文书抬头共用
     * 任一参数未配置或读取失败，保持内置默认，绝不打断布局渲染。
     */
    async loadSidebarConfig() {
      try {
        const [showRes, logoRes] = await Promise.all([
          request.get('/sys-param/get', { params: { key: 'SIDEBAR_SHOW_LOGO' }, silentError: true }),
          request.get('/sys-param/get', { params: { key: 'DOC_HOSPITAL_LOGO' }, silentError: true })
        ])
        if (showRes !== undefined && showRes !== null && showRes !== '') {
          this.sidebarLogoVisible = String(showRes) === '1'
        }
        if (logoRes) {
          this.sidebarLogoUrl = String(logoRes)
        }
      } catch (e) {
        // 保持默认
      }
    },
    onDepartChange(code) {
      const dep = this.departs.find(d => d.org_code === code)
      const prev = currentDepart.departCode
      setCurrentDepart(dep || { org_code: code, depart_name: code })
      // 科室真的变了才丢掉当前患者：患者只属于一个科室，带着旧科室的患者再进
      // SOFA / APACHE 那些页面，看到的是错的人 —— 而且不报错，看着还像对的。
      // 判断「真的变了」是必须的：本函数初始化时也会被调用一次，
      // 那时 currentDepart 尚无值，若一律清除，刷新一次页面就会把从工作台
      // 带过来的患者清掉。与工作台里的科室下拉保持同一套行为。
      if (prev && prev !== code) {
        clearCurrentPatient()
      }
    },
    handleClearPatient() {
      clearCurrentPatient()
    },
    async handleLogout() {
      try {
        await ElMessageBox.confirm('确定要退出登录吗？', '退出登录', {
          confirmButtonText: '退出',
          cancelButtonText: '取消',
          type: 'warning'
        })
      } catch (e) {
        return
      }
      // 通知后端失败也要清本地令牌，否则点了退出却还停在登录态
      try {
        await logoutApi()
      } catch (e) {
        console.error('退出登录接口调用失败（已忽略）', e)
      }
      clearSession()
      window.location.href = '/login'
    }
  }
}
</script>

<style scoped>
/* 当前患者卡片：与外壳同一套 warm-stone 白卡，醒目但不抢主色。
   放在侧边栏底部而非顶部 —— 它是「当前处于谁身上」的状态提示，不是导航入口。 */
.footer-patient {
  margin-bottom: 10px;
  padding: 10px 12px;
  background: #fff;
  border: 1px solid #ece7e0;
  border-radius: 10px;
}
.patient-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 4px; }
.patient-title { font-size: 11px; color: #a8a29e; }
.patient-clear { border: none; background: transparent; color: #c2410c; font-size: 11px; cursor: pointer; padding: 0; }
.patient-name { font-size: 13px; font-weight: 600; color: #292524; }
.patient-sub { margin-top: 2px; font-size: 11px; color: #78716c; }

/* 侧边栏：设计稿 warm-stone 浅色版（白卡 + 暖灰底 + 1px 描边），去掉旧的深蓝渐变。
   刻意不绑主色：外壳同时承载抗菌药（青）与质控（橙）两套主题，
   激活态用中性暖灰强调，两边都不打架。入口数量与层级一律不动。 */
.main-layout {
  display: flex;
  min-height: 100vh;
  background: #fafaf9;
}

/* 侧边栏 */
.sidebar {
  width: 220px;
  background: #fafaf9;
  border-right: 1px solid #e7e5e4;
  color: #292524;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  position: fixed;
  top: 0;
  left: 0;
  bottom: 0;
  z-index: 100;
}

.sidebar-logo {
  padding: 20px 16px;
  display: flex;
  align-items: center;
  gap: 12px;
  border-bottom: 1px solid #f5f5f4;
}

.logo-icon {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  object-fit: contain;
  display: block;
  flex-shrink: 0;
}

.logo-title {
  font-size: 15px;
  font-weight: 600;
  color: #1c1917;
  line-height: 1.3;
}

.sidebar-dept {
  padding: 8px 12px;
  border-bottom: 1px solid #f0f0f0;
}
.dept-label {
  display: block;
  font-size: 12px;
  color: #909399;
  margin-bottom: 4px;
}
.sidebar-nav {
  flex: 1;
  padding: 12px 10px;
  /* 菜单项超出可视高度时自身滚动，避免底部入口被裁掉 */
  overflow-y: auto;
  /* 隐藏滚动条视觉，但保留滚动能力 */
  scrollbar-width: none;
  -ms-overflow-style: none;
}
.sidebar-nav::-webkit-scrollbar {
  width: 0;
  height: 0;
  display: none;
}

.nav-divider {
  padding: 14px 14px 6px;
  font-size: 11px;
  color: #a8a29e;
  letter-spacing: 1px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 11px 14px;
  border-radius: 8px;
  /* 透明描边占位：激活态补上 1px 边框时不会把文字挤动 */
  border: 1px solid transparent;
  color: #57534e;
  text-decoration: none;
  font-size: 14px;
  margin-bottom: 4px;
  transition: all 0.2s;
}

.nav-item:hover {
  background: #f5f5f4;
  color: #292524;
}

/* 卡片式激活项：白卡 + 暖灰描边 + 极轻阴影 + 左侧强调条 */
.nav-active {
  background: #ffffff;
  border: 1px solid #e7e5e4;
  border-left: 3px solid #292524;
  box-shadow: 0 1px 2px rgba(28, 25, 23, 0.04);
  color: #1c1917;
  font-weight: 600;
  padding-left: 12px;
}

.nav-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  color: #a8a29e;
  text-align: center;
  flex: 0 0 20px;
}

.nav-icon :deep(svg) {
  width: 19px;
  height: 19px;
  fill: none;
  stroke: currentColor;
  stroke-width: 1.7;
  stroke-linecap: round;
  stroke-linejoin: round;
}
/* 圆点类元素实心填充，避免空心圈 */
.nav-icon svg circle {
  fill: currentColor;
  stroke: none;
}

.nav-item:hover .nav-icon,
.nav-active .nav-icon {
  color: #c2410c;
}

.sidebar-footer {
  padding: 14px 16px;
  border-top: 1px solid #f5f5f4;
}

.footer-user {
  display: flex;
  align-items: center;
  gap: 10px;
}

/* 退出登录：仅直连登录会话出现，靠右排布 */
.logout-btn {
  margin-left: auto;
  padding: 4px 10px;
  font-size: 12px;
  color: #78716c;
  background: transparent;
  border: 1px solid #e7e5e4;
  border-radius: 6px;
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.15s;
}

.logout-btn:hover {
  color: #dc2626;
  background: #fef2f2;
  border-color: #fecaca;
}

.user-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: linear-gradient(135deg, #57534e, #292524);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 600;
  color: #fff;
}

.user-name {
  font-size: 13px;
  color: #1c1917;
  font-weight: 500;
}

.user-role {
  font-size: 11px;
  color: #a8a29e;
  margin-top: 1px;
}

/* 主内容区 */
.main-content {
  flex: 1;
  margin-left: 220px;
  min-height: 100vh;
  overflow-x: hidden;
}
</style>
