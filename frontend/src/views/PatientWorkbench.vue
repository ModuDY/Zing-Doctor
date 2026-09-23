<template>
  <div class="workbench-page">
    <header class="page-heading">
      <div>
        <div class="eyebrow">ICU · CLINICAL WORKSPACE</div>
        <h1>患者工作台</h1>
        <p>汇总当前在科患者，便于快速进入现有临床工具。</p>
      </div>
      <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
    </header>

    <el-alert class="notice" type="info" :closable="false" show-icon>
      首版仅展示在科患者基础信息，不自动判定高危或临床待办；姓名和住院号已脱敏。
    </el-alert>

    <section class="summary-row">
      <div class="summary-card"><span class="summary-icon">👥</span><div><small>当前在科患者</small><strong>{{ filteredPatients.length }}<em> / {{ patients.length }} 人</em></strong></div></div>
      <div class="summary-card"><span class="summary-icon">🛏️</span><div><small>有床位信息</small><strong>{{ occupiedBeds }}<em> 张床</em></strong></div></div>
      <div class="summary-card update-card"><span class="summary-icon">⟳</span><div><small>数据更新时间</small><strong class="time-value">{{ updatedAt || '尚未加载' }}</strong></div></div>
    </section>

    <el-card class="patient-card" shadow="never">
      <div class="toolbar">
        <el-input v-model="keyword" clearable placeholder="搜索姓名、住院号、床位或科室" :prefix-icon="Search" />
        <el-select v-model="department" clearable placeholder="全部科室"><el-option v-for="item in departments" :key="item" :label="item" :value="item" /></el-select>
        <el-select v-model="sortBy" placeholder="排序方式"><el-option label="入科时间：新到旧" value="newest" /><el-option label="入科时间：旧到新" value="oldest" /><el-option label="床位" value="bed" /></el-select>
      </div>

      <el-table v-loading="loading" :data="filteredPatients" stripe row-key="patientId" empty-text="暂无在科患者数据" @row-click="openPatient">
        <el-table-column label="患者" min-width="190">
          <template #default="{ row }"><div class="patient-name">{{ row.name || '未知' }}</div><div class="patient-no">住院号 {{ row.patientNo || '—' }}</div></template>
        </el-table-column>
        <el-table-column label="性别 / 年龄" width="130"><template #default="{ row }">{{ row.gender || '—' }}<span class="muted"> · </span>{{ row.age == null ? '—' : `${row.age} 岁` }}</template></el-table-column>
        <el-table-column prop="department" label="科室" min-width="140"><template #default="{ row }">{{ row.department || '未分配' }}</template></el-table-column>
        <el-table-column prop="bedNo" label="床位" width="105"><template #default="{ row }"><span class="bed-pill">{{ row.bedNo || '待分配' }}</span></template></el-table-column>
        <el-table-column label="入科时间" min-width="170"><template #default="{ row }">{{ formatTime(row.inDepartmentTime) }}</template></el-table-column>
        <el-table-column label="ICU 天数" width="110"><template #default="{ row }">{{ row.icuDays == null ? '—' : `${row.icuDays} 天` }}</template></el-table-column>
        <el-table-column label="快捷入口" width="185" fixed="right"><template #default="{ row }"><el-button link type="primary" @click.stop="goDecision(row)">抗感染决策</el-button><el-button link type="primary" @click.stop="goSofa(row)">SOFA</el-button></template></el-table-column>
      </el-table>
      <div v-if="!loading && patients.length === 0" class="empty-foot">请检查 ICU 数据源配置或稍后刷新。</div>
    </el-card>
  </div>
</template>

<script>
import { Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { fetchInpatients } from '../api/workbench'

export default {
  name: 'PatientWorkbench',
  setup() { return { Refresh, Search } },
  data() { return { loading: false, patients: [], keyword: '', department: '', sortBy: 'newest', updatedAt: '' } },
  computed: {
    departments() { return [...new Set(this.patients.map(p => p.department).filter(Boolean))].sort() },
    filteredPatients() {
      const q = this.keyword.trim().toLowerCase()
      return this.patients.filter(p => (!this.department || p.department === this.department) && (!q || [p.name, p.patientNo, p.bedNo, p.department].some(v => String(v || '').toLowerCase().includes(q))))
        .slice().sort((a, b) => this.sortBy === 'oldest' ? this.time(a) - this.time(b) : this.sortBy === 'bed' ? String(a.bedNo || '').localeCompare(String(b.bedNo || ''), 'zh-CN', { numeric: true }) : this.time(b) - this.time(a))
    },
    occupiedBeds() { return this.patients.filter(p => p.bedNo).length }
  },
  created() { this.load() },
  methods: {
    async load() {
      this.loading = true
      try { this.patients = await fetchInpatients(); this.updatedAt = new Date().toLocaleString('zh-CN', { hour12: false }) }
      catch (e) { ElMessage.error(`患者列表加载失败：${e.message}`) }
      finally { this.loading = false }
    },
    time(row) { const t = row.inDepartmentTime ? new Date(row.inDepartmentTime).getTime() : 0; return Number.isNaN(t) ? 0 : t },
    formatTime(value) { if (!value) return '—'; return String(value).replace('T', ' ').slice(0, 16) },
    openPatient(row) { this.goDecision(row) },
    goDecision(row) { this.$router.push({ path: '/page/abx-decision', query: { patientId: row.patientId } }) },
    goSofa(row) { this.$router.push({ path: '/page/sofa-score', query: { patientId: row.patientId } }) }
  }
}
</script>

<style scoped>
.workbench-page { min-height: 100vh; max-width: 1440px; margin: 0 auto; padding: 30px 34px 48px; color: #292524; background: #fafaf9; }
.page-heading { display:flex; align-items:center; justify-content:space-between; margin-bottom:22px; }
.eyebrow { color:#0f766e; font-size:11px; font-weight:700; letter-spacing:1.5px; }
h1 { margin:7px 0 5px; font-size:27px; letter-spacing:-.5px; }
.page-heading p { margin:0; color:#78716c; font-size:14px; }
.notice { margin-bottom:18px; border:1px solid #ccfbf1; background:#f0fdfa; }
.summary-row { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:14px; margin-bottom:18px; }
.summary-card { display:flex; align-items:center; gap:14px; min-height:94px; padding:18px 20px; border:1px solid #e7e5e4; border-radius:12px; background:#fff; }
.summary-icon { display:grid; place-items:center; width:44px; height:44px; border-radius:12px; background:#f5f5f4; font-size:21px; }
.summary-card small { display:block; margin-bottom:7px; color:#78716c; font-size:12px; }
.summary-card strong { color:#1c1917; font-size:25px; }
.summary-card em { color:#a8a29e; font-size:12px; font-style:normal; font-weight:400; }
.time-value { font-size:17px!important; }
.patient-card { border:1px solid #e7e5e4; border-radius:12px; }
.toolbar { display:flex; gap:10px; margin-bottom:18px; }
.toolbar :deep(.el-input) { max-width:390px; }
.toolbar :deep(.el-select) { width:170px; }
.patient-name { color:#292524; font-weight:600; }
.patient-no { margin-top:4px; color:#a8a29e; font-size:12px; }
.muted { color:#a8a29e; }
.bed-pill { display:inline-block; padding:4px 9px; border-radius:6px; background:#f5f5f4; color:#57534e; font-size:12px; }
.empty-foot { padding:16px; text-align:center; color:#a8a29e; font-size:12px; }
@media(max-width:800px) { .workbench-page { padding:20px 14px; } .summary-row { grid-template-columns:1fr; gap:9px; } .toolbar { flex-wrap:wrap; } .toolbar :deep(.el-input), .toolbar :deep(.el-select) { width:100%; max-width:none; } }
</style>
