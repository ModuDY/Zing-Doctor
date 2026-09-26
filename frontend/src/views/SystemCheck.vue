<template>
  <div class="check-page qb-theme">
    <header class="check-hero">
      <div>
        <div class="eyebrow">DELIVERY · ACCEPTANCE CHECK</div>
        <h1>交付自检</h1>
        <p>用于现场确认当前运行包、数据库升级和 ICU 数据源状态，不替代业务功能验收。</p>
      </div>
      <el-button :loading="loading" @click="load"><el-icon><Refresh /></el-icon>重新检查</el-button>
    </header>

    <el-alert v-if="loadError" type="error" :closable="false" show-icon :title="loadError" />

    <section class="check-grid">
      <div class="check-card check-card-main">
        <div class="check-card-head"><span>服务健康度</span><el-tag :type="healthTagType">{{ healthLabel }}</el-tag></div>
        <div class="health-summary">{{ healthSummary }}</div>
        <div class="check-list">
          <div class="check-row"><span>医生库</span><strong :class="stateClass(health.doctorDatabase)">{{ health.doctorDatabase || '未返回' }}</strong></div>
          <div class="check-row"><span>复评表升级</span><strong :class="stateClass(health.reassessmentSchema)">{{ health.reassessmentSchema || '未返回' }}</strong></div>
          <div class="check-row"><span>ICU 数据源</span><strong :class="stateClass(health.icuDatabase)">{{ health.icuDatabase || '未返回' }}</strong></div>
          <div class="check-row"><span>检查时间</span><strong>{{ health.time || '—' }}</strong></div>
        </div>
      </div>

      <div class="check-card">
        <div class="check-card-head"><span>当前交付包</span><el-tag type="info">只读</el-tag></div>
        <div class="check-list">
          <div class="check-row"><span>版本</span><strong>{{ build.version || 'unknown' }}</strong></div>
          <div class="check-row"><span>提交号</span><strong class="mono">{{ build.gitCommit || 'unknown' }}</strong></div>
          <div class="check-row"><span>构建时间</span><strong>{{ build.buildTime || 'unknown' }}</strong></div>
          <div class="check-row"><span>运行 profile</span><strong>{{ build.activeProfile || '—' }}</strong></div>
          <div class="check-row"><span>ICU provider</span><strong>{{ build.icuDataProvider || health.icuDataProvider || '—' }}</strong></div>
          <div class="check-row"><span>服务时间</span><strong>{{ build.serverTime || '—' }}</strong></div>
        </div>
      </div>
    </section>

    <el-alert class="check-note" type="info" :closable="false" show-icon>
      验收建议：先确认服务健康度为 UP，再进入患者工作台选择一个真实患者，完成一次评分保存、抗感染决策保存和 48～72 小时复评留痕；最后确认工作台待办会随操作消失。
    </el-alert>
  </div>
</template>

<script>
import { Refresh } from '@element-plus/icons-vue'
import { fetchBuildInfo, fetchHealth } from '../api/system'

export default {
  name: 'SystemCheck',
  components: { Refresh },
  data() {
    return {
      loading: false,
      loadError: '',
      build: {},
      health: {}
    }
  },
  computed: {
    healthLabel() {
      if (this.health.status === 'UP') return '可交付'
      if (this.health.status === 'DOWN') return '未就绪'
      return '未知'
    },
    healthTagType() {
      return this.health.status === 'UP' ? 'success' : (this.health.status === 'DOWN' ? 'danger' : 'info')
    },
    healthSummary() {
      if (this.health.status === 'UP') return '基础服务、数据库和复评表均已通过检查。'
      if (this.health.status === 'DOWN') return '至少一项基础检查未通过，请先处理后再进行现场验收。'
      return '正在读取服务状态。'
    }
  },
  created() {
    this.load()
  },
  methods: {
    stateClass(value) {
      if (value === 'UP' || value === 'READY' || value === 'SKIPPED_MOCK') return 'state-ok'
      if (value === 'DOWN' || value === 'MISSING_OR_UNAVAILABLE') return 'state-error'
      return 'state-muted'
    },
    async load() {
      this.loading = true
      this.loadError = ''
      const [buildResult, healthResult] = await Promise.allSettled([fetchBuildInfo(), fetchHealth()])
      if (buildResult.status === 'fulfilled') this.build = buildResult.value || {}
      if (healthResult.status === 'fulfilled') this.health = healthResult.value?.data || healthResult.value || {}
      if (buildResult.status === 'rejected' && healthResult.status === 'rejected') {
        this.loadError = '交付自检接口不可用，请确认当前页面连接的是后端服务，而不是旧的前端静态包。'
      }
      this.loading = false
    }
  }
}
</script>

<style scoped>
.check-page { min-height: 100vh; padding: 28px 32px 44px; background: #fafaf9; color: #292524; }
.check-hero { display: flex; align-items: flex-start; justify-content: space-between; gap: 24px; margin-bottom: 24px; }
.eyebrow { color: #a8a29e; font-size: 11px; letter-spacing: 1.6px; }
h1 { margin: 8px 0 8px; font-size: 28px; }
.check-hero p { margin: 0; color: #78716c; font-size: 13px; }
.check-grid { display: grid; grid-template-columns: 1.05fr .95fr; gap: 16px; margin-top: 16px; }
.check-card { padding: 20px; border: 1px solid #e7e5e4; border-radius: 12px; background: #fff; box-shadow: 0 1px 4px rgba(28,25,23,.04); }
.check-card-head { display: flex; align-items: center; justify-content: space-between; padding-bottom: 14px; border-bottom: 1px solid #f5f5f4; font-weight: 700; }
.health-summary { padding: 18px 0 10px; color: #57534e; font-size: 14px; }
.check-list { display: flex; flex-direction: column; gap: 0; }
.check-row { display: flex; justify-content: space-between; gap: 20px; padding: 11px 0; border-bottom: 1px solid #f5f5f4; color: #78716c; font-size: 13px; }
.check-row:last-child { border-bottom: 0; }
.check-row strong { color: #44403c; font-weight: 600; text-align: right; }
.state-ok { color: #15803d !important; }.state-error { color: #dc2626 !important; }.state-muted { color: #a8a29e !important; }
.mono { font-family: Consolas, monospace; font-size: 12px; }
.check-note { margin-top: 16px; }
@media (max-width: 760px) { .check-page { padding: 20px 14px 34px; } .check-hero { flex-direction: column; } .check-grid { grid-template-columns: 1fr; } }
</style>

