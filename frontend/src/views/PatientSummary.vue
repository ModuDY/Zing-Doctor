<template>
  <div class="summary-page qb-theme">
    <!-- 顶部：返回 + 患者身份 -->
    <section class="summary-header">
      <div class="header-left">
        <el-button link @click="goBack" class="back-btn">
          <el-icon><arrow-left /></el-icon>
          返回工作台
        </el-button>
      </div>
      <div class="header-patient" v-if="patient">
        <h1 class="patient-name">{{ patient.name || '—' }}</h1>
        <div class="patient-meta">
          <span class="meta-item bed-pill">{{ patient.bedNo || '未分配床位' }}</span>
          <span class="meta-item">住院号 {{ patient.patientNo || '—' }}</span>
          <span class="meta-item" v-if="patient.wardName">{{ patient.wardName }}</span>
          <span class="meta-item stay-badge">入科第 {{ patient.icuDays || '—' }} 天</span>
        </div>
      </div>
      <div class="header-right">
        <el-button :icon="Refresh" :loading="loading" @click="loadSummary" size="small">刷新</el-button>
      </div>
    </section>

    <div v-loading="loading" class="summary-body">
      <!-- 加载失败 -->
      <el-empty v-if="!loading && loadError" :description="loadError" class="load-error">
        <el-button @click="goBack">返回工作台</el-button>
      </el-empty>

      <template v-if="patient">
        <!-- 第一行：基本信息 / 生命支持 / 评分 -->
        <div class="summary-row top-row">
          <!-- 基本信息 -->
          <div class="summary-card info-card">
            <div class="card-kicker">BASIC INFO</div>
            <h3 class="card-title">基本信息</h3>
            <div class="info-grid">
              <div class="info-item">
                <span class="info-label">年龄</span>
                <span class="info-value">{{ patient.age || '—' }}</span>
              </div>
              <div class="info-item">
                <span class="info-label">性别</span>
                <span class="info-value">{{ patient.gender || '—' }}</span>
              </div>
              <div class="info-item">
                <span class="info-label">科室编码</span>
                <span class="info-value">{{ patient.departCode || '—' }}</span>
              </div>
              <div class="info-item">
                <span class="info-label">入科时间</span>
                <span class="info-value">{{ formatTime(patient.inDepartmentTime) }}</span>
              </div>
            </div>
          </div>

          <!-- 生命支持 -->
          <div class="summary-card support-card">
            <div class="card-kicker">LIFE SUPPORT</div>
            <h3 class="card-title">当前生命支持</h3>
            <div class="support-tags">
              <div :class="['support-item', { active: patient.ventilated }]">
                <div class="support-icon">{{ patient.ventilated ? '✓' : '—' }}</div>
                <div class="support-label">机械通气</div>
              </div>
              <div :class="['support-item', { active: patient.onVasopressor }]">
                <div class="support-icon">{{ patient.onVasopressor ? '✓' : '—' }}</div>
                <div class="support-label">血管活性药</div>
              </div>
              <div :class="['support-item', { active: patient.onCrrt }]">
                <div class="support-icon">{{ patient.onCrrt ? '✓' : '—' }}</div>
                <div class="support-label">CRRT</div>
              </div>
            </div>
            <div class="support-hint" v-if="!patient.ventilated && !patient.onVasopressor && !patient.onCrrt">
              暂无生命支持记录
            </div>
          </div>

          <!-- 评分 -->
          <div class="summary-card score-card">
            <div class="card-kicker">SCORES</div>
            <h3 class="card-title">最近评分</h3>
            <div class="score-items">
              <div class="score-item">
                <div class="score-label">SOFA</div>
                <div :class="['score-value', { 'score-na': patient.lastSofaScore == null }]">
                  {{ patient.lastSofaScore != null ? patient.lastSofaScore : '未评' }}
                </div>
                <div class="score-range">0 – 24</div>
              </div>
              <div class="score-divider"></div>
              <div class="score-item">
                <div class="score-label">APACHE II</div>
                <div :class="['score-value', { 'score-na': patient.lastApacheScore == null }]">
                  {{ patient.lastApacheScore != null ? patient.lastApacheScore : '未评' }}
                </div>
                <div class="score-range">0 – 71</div>
              </div>
            </div>
          </div>
        </div>

        <!-- 感染摘要 -->
        <div class="summary-card infection-card">
          <div class="card-header">
            <div>
              <div class="card-kicker">INFECTION</div>
              <h3 class="card-title">感染摘要</h3>
            </div>
            <el-tag v-if="patient.infectionDataStatus === 'UNKNOWN'" type="warning" size="small" effect="plain">
              感染数据暂不可用
            </el-tag>
            <el-tag v-else-if="patient.suspectedInfection" type="danger" size="small">疑似感染</el-tag>
            <el-tag v-else type="info" size="small" effect="plain">未发现疑似感染</el-tag>
          </div>

          <template v-if="patient.infectionDataStatus !== 'UNKNOWN'">
            <div class="infection-metrics">
              <div class="metric">
                <span class="metric-label">感染类型</span>
                <span class="metric-value">{{ patient.infectionType || '感染部位待明确' }}</span>
              </div>
              <div class="metric">
                <span class="metric-label">PCT</span>
                <span :class="['metric-value', { warn: patient.pct != null && patient.pct >= 0.5 }]">
                  {{ patient.pct != null ? patient.pct + ' ng/mL' : '—' }}
                </span>
              </div>
              <div class="metric">
                <span class="metric-label">WBC</span>
                <span class="metric-value">{{ patient.wbc != null ? patient.wbc + ' ×10⁹/L' : '—' }}</span>
              </div>
              <div class="metric">
                <span class="metric-label">体温</span>
                <span :class="['metric-value', { warn: patient.temperature != null && patient.temperature >= 38.3 }]">
                  {{ patient.temperature != null ? patient.temperature + ' ℃' : '—' }}
                </span>
              </div>
              <div class="metric">
                <span class="metric-label">当前抗菌药</span>
                <span class="metric-value abx-value">
                  {{ patient.currentAbx && patient.currentAbx.length ? patient.currentAbx.join('、') : '无' }}
                </span>
              </div>
              <div class="metric" v-if="patient.abxStartTime">
                <span class="metric-label">用药开始</span>
                <span class="metric-value">{{ formatTime(patient.abxStartTime) }}</span>
              </div>
            </div>

            <div class="infection-flags" v-if="patient.suspectedInfection">
              <el-tag v-if="patient.septicShock" type="danger" size="small" effect="dark">脓毒性休克</el-tag>
              <el-tag v-if="patient.mrsaRisk" type="warning" size="small">MRSA 风险</el-tag>
              <el-tag v-if="patient.mdrRisk" type="warning" size="small">MDR 风险</el-tag>
              <el-tag v-if="patient.fungalRisk" type="info" size="small">真菌风险</el-tag>
              <el-tag :type="riskTagType(patient.infectionRiskLevel)" size="small" effect="plain">
                风险等级：{{ patient.infectionRiskLevel || '未评估' }}
              </el-tag>
              <el-button link type="primary" size="small" v-if="patient.infectionEvidence" @click="showEvidence = true">
                查看判定依据
              </el-button>
            </div>
          </template>
        </div>

        <!-- 今日待办 -->
        <div class="summary-card todo-card">
          <div class="card-header">
            <div>
              <div class="card-kicker">TODAY</div>
              <h3 class="card-title">今日待办</h3>
            </div>
            <el-badge :value="patient.todoCount || 0" :hidden="!patient.todoCount" class="todo-badge" />
          </div>
          <div v-if="patient.todoCount && patient.todoCount > 0" class="todo-list">
            <div
              v-for="todo in todoItems"
              :key="todo.code"
              class="todo-item"
              @click="handleTodo(todo)"
            >
              <div class="todo-dot" :class="todo.type"></div>
              <div class="todo-content">
                <div class="todo-text">{{ todo.label }}</div>
                <div class="todo-sub" v-if="todo.sub">{{ todo.sub }}</div>
              </div>
              <el-icon class="todo-arrow"><arrow-right /></el-icon>
            </div>
          </div>
          <div v-else class="todo-empty">今日无待办事项</div>
        </div>

        <!-- 快捷操作 -->
        <div class="summary-card actions-card">
          <div class="card-kicker">QUICK ACTIONS</div>
          <h3 class="card-title">快捷操作</h3>
          <div class="action-grid">
            <el-button type="primary" @click="jump('/page/abx-decision')" class="action-btn primary-action">
              抗感染决策
            </el-button>
            <el-button @click="jump('/page/abx-pkpd')" class="action-btn">PK/PD 剂量</el-button>
            <el-button @click="jump('/page/sofa-score')" class="action-btn">SOFA 评分</el-button>
            <el-button @click="jump('/page/apache2-score')" class="action-btn">APACHE II</el-button>
            <el-button @click="jump('/page/sepsis-bundle')" class="action-btn">脓毒症集束化</el-button>
            <el-button @click="jump('/page/ards-monitor')" class="action-btn">ARDS 监测</el-button>
            <el-button @click="jump('/page/ards-prone-list')" class="action-btn">俯卧位记录</el-button>
          </div>
        </div>
      </template>
    </div>

    <!-- 判定依据弹窗 -->
    <el-dialog v-model="showEvidence" title="感染判定依据" width="480px" class="evidence-dialog">
      <div class="evidence-content">{{ patient && patient.infectionEvidence }}</div>
    </el-dialog>
  </div>
</template>

<script>
import { fetchPatientSummary } from '../api/workbench'
import { setCurrentPatient, clearCurrentPatient } from '../utils/patientContext'
import { ArrowLeft, ArrowRight, Refresh } from '@element-plus/icons-vue'

export default {
  name: 'PatientSummary',
  components: { ArrowLeft, ArrowRight, Refresh },
  data() {
    return {
      patient: null,
      loading: false,
      loadError: '',
      showEvidence: false
    }
  },
  computed: {
    todoItems() {
      if (!this.patient || !this.patient.todos) return []
      const map = {
        SOFA_NOT_TODAY: { code: 'SOFA_NOT_TODAY', label: '今日未评 SOFA', sub: '点击前往评分', type: 'orange', path: '/page/sofa-score' },
        APACHE_NOT_TODAY: { code: 'APACHE_NOT_TODAY', label: '今日未评 APACHE II', sub: '点击前往评分', type: 'orange', path: '/page/apache2-score' },
        ABX_REASSESSMENT_PENDING: {
          code: 'ABX_REASSESSMENT_PENDING',
          label: '抗感染 48~72h 复评待处理',
          sub: this.patient.reassessmentDueTime ? '截止 ' + this.formatTime(this.patient.reassessmentDueTime) : '',
          type: 'red',
          path: '/page/abx-decision'
        }
      }
      return this.patient.todos.map(c => map[c]).filter(Boolean)
    }
  },
  mounted() {
    this.loadSummary()
  },
  methods: {
    async loadSummary() {
      const patientId = this.$route.query.patientId
      if (!patientId) {
        this.loadError = '缺少 patientId 参数'
        return
      }
      this.loading = true
      this.loadError = ''
      try {
        // request.js 响应拦截器已解包：成功时直接返回 data（WorkbenchPatient），
        // 失败时抛 Error（message 为友好文案）。因此这里不判断 res.code。
        const data = await fetchPatientSummary(patientId)
        if (data) {
          this.patient = data
          // 写入全局患者上下文，侧边栏切换页面时患者不丢
          setCurrentPatient(data)
        } else {
          this.loadError = '患者不存在或已出科'
        }
      } catch (e) {
        this.loadError = e.message || '网络异常'
      } finally {
        this.loading = false
      }
    },
    jump(path) {
      if (!this.patient) return
      setCurrentPatient(this.patient)
      this.$router.push({
        path,
        query: {
          ...this.$route.query,
          patientId: this.patient.patientId,
          inHospitalNo: this.patient.inHospitalNo || '',
          inDepartTime: this.patient.inDepartmentTime || '',
          departCode: this.patient.departCode || '',
          ...(this.patient.name ? { patientName: this.patient.name } : {})
        }
      })
    },
    handleTodo(todo) {
      if (todo.path) this.jump(todo.path)
    },
    goBack() {
      this.$router.push('/page/patient-workbench')
    },
    formatTime(t) {
      if (!t) return '—'
      try {
        const d = new Date(t)
        if (isNaN(d.getTime())) return t
        const pad = n => String(n).padStart(2, '0')
        return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
      } catch {
        return t
      }
    },
    riskTagType(level) {
      if (level === '高风险') return 'danger'
      if (level === '中风险') return 'warning'
      return 'info'
    }
  }
}
</script>

<style scoped>
.summary-page {
  min-height: 100%;
  background: #f5f5f4;
  padding: 20px 24px 40px;
}

/* 顶部栏 */
.summary-header {
  display: flex;
  align-items: center;
  gap: 20px;
  margin-bottom: 18px;
}
.header-left { flex-shrink: 0; }
.back-btn {
  font-size: 14px;
  color: #57534e;
  padding: 6px 0;
}
.back-btn:hover { color: #ea580c; }
.header-patient { flex: 1; min-width: 0; }
.patient-name {
  font-size: 24px;
  font-weight: 700;
  color: #1c1917;
  margin: 0 0 6px 0;
  line-height: 1.2;
}
.patient-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.meta-item {
  font-size: 13px;
  color: #57534e;
}
.bed-pill {
  display: inline-flex;
  align-items: center;
  padding: 2px 10px;
  border-radius: 6px;
  background: #fff7ed;
  color: #c2410c;
  font-weight: 600;
  font-size: 12px;
}
.stay-badge {
  display: inline-flex;
  align-items: center;
  padding: 2px 10px;
  border-radius: 999px;
  background: #ea580c;
  color: #fff;
  font-weight: 600;
  font-size: 12px;
}
.header-right { flex-shrink: 0; }

/* 主体 */
.summary-body { min-height: 300px; }
.load-error { padding: 60px 0; }

/* 卡片通用 */
.summary-card {
  background: #fff;
  border: 1px solid #e7e5e4;
  border-radius: 10px;
  padding: 18px 20px;
  margin-bottom: 16px;
}
.card-kicker {
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 1.5px;
  color: #a8a29e;
  text-transform: uppercase;
  margin-bottom: 4px;
}
.card-title {
  font-size: 16px;
  font-weight: 700;
  color: #1c1917;
  margin: 0 0 14px 0;
}
.card-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 14px;
}
.card-header .card-title { margin-bottom: 0; }

/* 第一行三列 */
.top-row {
  display: grid;
  grid-template-columns: 1.2fr 1fr 1fr;
  gap: 16px;
  margin-bottom: 0;
}
.top-row .summary-card { margin-bottom: 0; }

/* 基本信息 */
.info-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px 16px;
}
.info-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.info-label {
  font-size: 11px;
  color: #a8a29e;
}
.info-value {
  font-size: 14px;
  color: #292524;
  font-weight: 500;
}

/* 生命支持 */
.support-tags {
  display: flex;
  gap: 12px;
}
.support-item {
  flex: 1;
  text-align: center;
  padding: 12px 8px;
  border-radius: 8px;
  background: #f5f5f4;
  border: 1px solid #e7e5e4;
  transition: all .2s;
}
.support-item.active {
  background: #fff7ed;
  border-color: #fdba74;
}
.support-icon {
  font-size: 20px;
  font-weight: 700;
  margin-bottom: 6px;
  color: #a8a29e;
}
.support-item.active .support-icon { color: #ea580c; }
.support-label {
  font-size: 12px;
  color: #57534e;
}
.support-item.active .support-label { color: #c2410c; font-weight: 600; }
.support-hint {
  margin-top: 10px;
  font-size: 12px;
  color: #a8a29e;
  text-align: center;
}

/* 评分 */
.score-items {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 20px;
}
.score-item { text-align: center; flex: 1; }
.score-label {
  font-size: 12px;
  color: #78716c;
  margin-bottom: 6px;
}
.score-value {
  font-size: 36px;
  font-weight: 800;
  color: #ea580c;
  line-height: 1;
  margin-bottom: 4px;
}
.score-value.score-na {
  font-size: 18px;
  font-weight: 500;
  color: #a8a29e;
}
.score-range {
  font-size: 10px;
  color: #a8a29e;
}
.score-divider {
  width: 1px;
  height: 48px;
  background: #e7e5e4;
}

/* 感染摘要 */
.infection-metrics {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px 20px;
  margin-bottom: 14px;
}
.metric {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.metric-label {
  font-size: 11px;
  color: #a8a29e;
}
.metric-value {
  font-size: 14px;
  color: #292524;
  font-weight: 600;
}
.metric-value.warn { color: #dc2626; }
.abx-value { font-weight: 500; }
.infection-flags {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  padding-top: 12px;
  border-top: 1px solid #f5f5f4;
}

/* 待办 */
.todo-badge { margin-right: 4px; }
.todo-list { display: flex; flex-direction: column; gap: 8px; }
.todo-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 14px;
  border-radius: 8px;
  background: #fafaf9;
  border: 1px solid #f5f5f4;
  cursor: pointer;
  transition: background .15s;
}
.todo-item:hover { background: #fff7ed; border-color: #fed7aa; }
.todo-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}
.todo-dot.orange { background: #ea580c; }
.todo-dot.red { background: #dc2626; }
.todo-content { flex: 1; min-width: 0; }
.todo-text {
  font-size: 14px;
  color: #292524;
  font-weight: 500;
}
.todo-sub {
  font-size: 12px;
  color: #a8a29e;
  margin-top: 2px;
}
.todo-arrow { color: #a8a29e; font-size: 14px; }
.todo-empty {
  text-align: center;
  padding: 20px;
  color: #a8a29e;
  font-size: 13px;
}

/* 快捷操作 */
.action-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
}
.action-btn {
  height: 40px;
  font-size: 13px;
}
.primary-action {
  font-weight: 600;
}

/* 依据弹窗 */
.evidence-content {
  font-size: 13px;
  color: #44403c;
  line-height: 1.7;
  white-space: pre-wrap;
}

/* 响应式 */
@media (max-width: 1100px) {
  .top-row { grid-template-columns: 1fr; }
  .infection-metrics { grid-template-columns: repeat(2, 1fr); }
  .action-grid { grid-template-columns: repeat(3, 1fr); }
}
@media (max-width: 700px) {
  .summary-page { padding: 14px; }
  .infection-metrics { grid-template-columns: 1fr; }
  .action-grid { grid-template-columns: repeat(2, 1fr); }
  .patient-name { font-size: 20px; }
}
</style>
