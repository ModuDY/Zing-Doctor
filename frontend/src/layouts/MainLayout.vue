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
.main-layout {
  display: flex;
  min-height: 100vh;
  background: #f0f2f5;
}

/* 侧边栏 */
.sidebar {
  width: 220px;
  background: linear-gradient(180deg, #1a3a5c 0%, #0f2540 100%);
  color: #fff;
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
  border-bottom: 1px solid rgba(255,255,255,0.1);
}

.logo-icon {
  width: 40px;
  height: 40px;
  background: linear-gradient(135deg, #4a9eff, #2d6fbf);
  border-radius: 10px;
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
  color: #fff;
  line-height: 1.3;
}

.logo-sub {
  font-size: 11px;
  color: rgba(255,255,255,0.5);
  margin-top: 2px;
}

.sidebar-nav {
  flex: 1;
  padding: 12px 10px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 11px 14px;
  border-radius: 8px;
  color: rgba(255,255,255,0.7);
  text-decoration: none;
  font-size: 14px;
  margin-bottom: 4px;
  transition: all 0.2s;
}

.nav-item:hover {
  background: rgba(255,255,255,0.08);
  color: #fff;
}

.nav-active {
  background: linear-gradient(90deg, rgba(74,158,255,0.25), rgba(74,158,255,0.08));
  color: #fff;
  font-weight: 500;
  border-left: 3px solid #4a9eff;
  padding-left: 11px;
}

.nav-icon {
  font-size: 16px;
  width: 20px;
  text-align: center;
}

.sidebar-footer {
  padding: 14px 16px;
  border-top: 1px solid rgba(255,255,255,0.1);
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
  background: linear-gradient(135deg, #4a9eff, #2d6fbf);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 600;
  color: #fff;
}

.user-name {
  font-size: 13px;
  color: #fff;
  font-weight: 500;
}

.user-role {
  font-size: 11px;
  color: rgba(255,255,255,0.5);
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
