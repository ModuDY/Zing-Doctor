<template>
  <div class="main-layout">
    <!-- 侧边栏 -->
    <aside class="sidebar">
      <div class="sidebar-logo">
        <div class="logo-icon">抗</div>
        <div class="logo-text">
          <div class="logo-title">医生决策系统</div>
          <div class="logo-sub">ICU 抗生素分析</div>
        </div>
      </div>

      <nav class="sidebar-nav">
        <router-link to="/page/abx-patient-list" class="nav-item" active-class="nav-active">
          <span class="nav-icon">📋</span>
          <span class="nav-label">疑似感染患者</span>
        </router-link>
        <router-link to="/page/abx-decision" class="nav-item" active-class="nav-active">
          <span class="nav-icon">💊</span>
          <span class="nav-label">抗感染决策</span>
        </router-link>
        <router-link to="/page/abx-pkpd" class="nav-item" active-class="nav-active">
          <span class="nav-icon">⚗️</span>
          <span class="nav-label">PK/PD 剂量优化</span>
        </router-link>
        <router-link to="/page/abx-ddd" class="nav-item" active-class="nav-active">
          <span class="nav-icon">📊</span>
          <span class="nav-label">使用强度分析</span>
        </router-link>
        <router-link to="/page/abx-ddd-config" class="nav-item" active-class="nav-active">
          <span class="nav-icon">⚙️</span>
          <span class="nav-label">DDD 配置管理</span>
        </router-link>
        <router-link to="/page/abx-mdro" class="nav-item" active-class="nav-active">
          <span class="nav-icon">🦠</span>
          <span class="nav-label">细菌培养监测</span>
        </router-link>
        <router-link to="/page/abx-mdro-config" class="nav-item" active-class="nav-active">
          <span class="nav-icon">🔬</span>
          <span class="nav-label">细菌配置管理</span>
        </router-link>
        <router-link to="/page/sepsis-bundle" class="nav-item" active-class="nav-active">
          <span class="nav-icon">💊</span>
          <span class="nav-label">脓毒症集束化治疗</span>
        </router-link>

        <div class="nav-divider">质控中台</div>
        <router-link to="/page/quality-board" class="nav-item" active-class="nav-active">
          <span class="nav-icon">📈</span>
          <span class="nav-label">质控指标看板</span>
        </router-link>
        <router-link to="/page/quality-monthly" class="nav-item" active-class="nav-active">
          <span class="nav-icon">🗓️</span>
          <span class="nav-label">质控月度汇总</span>
        </router-link>
      </nav>

      <div class="sidebar-footer">
        <div class="footer-user">
          <div class="user-avatar">{{ userInitial }}</div>
          <div class="user-info">
            <div class="user-name">{{ userName }}</div>
            <div class="user-role">ICU 医生</div>
          </div>
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
export default {
  name: 'MainLayout',
  computed: {
    userName() {
      // 从 sessionStorage / URL 参数取登录者姓名，外链时可能有 realname
      const name = sessionStorage.getItem('doctor_realname')
      if (name) return name
      const params = new URLSearchParams(window.location.search)
      return params.get('realname') || '医生'
    },
    userInitial() {
      const n = this.userName
      return n ? n.charAt(0) : '医'
    }
  }
}
</script>

<style scoped>
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
  background: linear-gradient(135deg, #57534e, #292524);
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  font-weight: 700;
  color: #fff;
}

.logo-title {
  font-size: 15px;
  font-weight: 600;
  color: #1c1917;
  line-height: 1.3;
}

.logo-sub {
  font-size: 11px;
  color: #a8a29e;
  margin-top: 2px;
}

.sidebar-nav {
  flex: 1;
  padding: 12px 10px;
  /* 菜单项超出可视高度时自身滚动，避免底部入口被裁掉 */
  overflow-y: auto;
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
  font-size: 16px;
  width: 20px;
  text-align: center;
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
