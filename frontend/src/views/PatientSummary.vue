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
        <!-- 第一行：患者概览 + 评分 -->
        <div class="summary-row top-row">
          <!-- 患者概览：基本信息 + 生命支持 -->
          <div class="summary-card overview-card">
            <div class="card-kicker">PATIENT OVERVIEW</div>
            <h3 class="card-title">患者概览</h3>
            <div class="overview-body">
              <!-- 基本信息：行式紧凑布局 -->
              <div class="overview-info">
                <div class="info-line">
                  <span class="info-line-label">年龄性别</span>
                  <span class="info-line-value">{{ patient.age || '—' }}岁 · {{ patient.gender || '—' }}</span>
                </div>
                <div class="info-line">
                  <span class="info-line-label">科室</span>
                  <span class="info-line-value">{{ patient.departName || patient.wardName || patient.departCode || '—' }}</span>
                </div>
                <div class="info-line">
                  <span class="info-line-label">住院号</span>
                  <span class="info-line-value">{{ patient.patientNo || '—' }}</span>
                </div>
                <div class="info-line">
                  <span class="info-line-label">入科时间</span>
                  <span class="info-line-value">{{ formatTime(patient.inDepartmentTime) }}</span>
                </div>
              </div>
              <!-- 生命支持：紧凑标签 -->
              <div class="overview-support">
                <div class="support-label">生命支持</div>
                <div class="support-chips">
                  <span :class="['support-chip', { on: patient.ventilated }]">
                    {{ patient.ventilated ? '● 机械通气' : '○ 机械通气' }}
                  </span>
                  <span :class="['support-chip', { on: patient.onVasopressor }]">
                    {{ patient.onVasopressor ? '● 血管活性药' : '○ 血管活性药' }}
                  </span>
                  <span :class="['support-chip', { on: patient.onCrrt }]">
                    {{ patient.onCrrt ? '● CRRT' : '○ CRRT' }}
                  </span>
                  <span :class="['support-chip', { on: patient.onEcmo }]">
                    {{ patient.onEcmo ? '● ECMO' : '○ ECMO' }}
                  </span>
                </div>
                <div class="support-none" v-if="!patient.ventilated && !patient.onVasopressor && !patient.onCrrt && !patient.onEcmo">
                  暂无生命支持
                </div>
              </div>
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
                <div class="score-grade" :class="sofaGradeClass(patient.lastSofaScore)">
                  {{ sofaGradeText(patient.lastSofaScore) }}
                </div>
              </div>
              <div class="score-divider"></div>
              <div class="score-item">
                <div class="score-label">APACHE II</div>
                <div :class="['score-value', { 'score-na': patient.lastApacheScore == null }]">
                  {{ patient.lastApacheScore != null ? patient.lastApacheScore : '未评' }}
                </div>
                <div class="score-grade" :class="apacheGradeClass(patient.lastApacheScore)">
                  {{ apacheGradeText(patient.lastApacheScore) }}
                </div>
                <div class="score-mortality" v-if="patient.lastApacheMortality != null">
                  预计死亡率 {{ patient.lastApacheMortality }}%
                </div>
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
              <div class="metric temperature-metric">
                <div class="temperature-head">
                  <span class="metric-label">体温</span>
                  <span :class="['metric-value', { warn: patient.temperature != null && patient.temperature >= 38.3 }]">
                    {{ patient.temperature != null ? patient.temperature + ' ℃' : '—' }}
                  </span>
                </div>
                <template v-if="temperatureChart">
                  <svg class="temperature-chart" viewBox="0 0 220 62" role="img" aria-label="近24小时体温趋势">
                    <line x1="4" y1="54" x2="216" y2="54" class="temperature-axis" />
                    <polyline :points="temperatureChart.polyline" class="temperature-line" />
                    <g v-for="point in temperatureChart.points" :key="point.key">
                      <title>{{ point.time }} {{ point.value }} ℃</title>
                      <circle :cx="point.x" :cy="point.y" :r="point.isExtreme ? 3.5 : 2" :class="['temperature-dot', { min: point.isMin, max: point.isMax }]" />
                    </g>
                    <text v-if="temperatureChart.min" :x="temperatureChart.min.x" :y="temperatureChart.min.labelY" text-anchor="middle" class="temperature-label min-label">低</text>
                    <text v-if="temperatureChart.max" :x="temperatureChart.max.x" :y="temperatureChart.max.labelY" text-anchor="middle" class="temperature-label max-label">高</text>
                  </svg>
                  <div class="temperature-extremes">
                    <span class="temperature-extreme min-extreme">低 {{ temperatureChart.min.value }} ℃ <small>{{ temperatureChart.min.time }}</small></span>
                    <span class="temperature-extreme max-extreme">高 {{ temperatureChart.max.value }} ℃ <small>{{ temperatureChart.max.time }}</small></span>
                  </div>
                </template>
                <span v-else-if="temperatureTrendUnknown" class="temperature-status">24 小时数据暂不可用</span>
                <span v-else class="temperature-status">24 小时无体温记录</span>
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

        <!-- 二期第一批：24h 检验 + 培养药敏 左右分栏 -->
        <div class="summary-row mid-row">
          <!-- 24h 检验摘要 -->
          <div class="summary-card labs-card">
            <div class="card-header">
              <div>
                <div class="card-kicker">LABS 24H</div>
                <h3 class="card-title">24 小时检验</h3>
              </div>
              <div class="labs-summary" v-if="patient.labs24h && patient.labs24h.dataStatus === 'FOUND'">
                <span class="labs-summary-item abnormal"><b>{{ patient.labs24h.abnormalCount }}</b> 异常</span>
                <span class="labs-summary-divider"></span>
                <span class="labs-summary-item normal"><b>{{ patient.labs24h.normalCount }}</b> 正常</span>
              </div>
            </div>

            <template v-if="patient.labs24h">
              <div v-if="patient.labs24h.dataStatus === 'EMPTY'" class="block-empty">
                窗口内无检验记录
              </div>
              <div v-else-if="patient.labs24h.dataStatus === 'UNKNOWN'" class="block-unknown">
                数据暂不可用
              </div>
              <div v-else-if="!patient.labs24h.abnormalItems || patient.labs24h.abnormalItems.length === 0" class="block-empty">
                窗口内无异常检验
              </div>
              <div v-else class="labs-table">
                <div class="labs-table-head">
                  <span>检验项目</span><span>结果</span><span>参考范围</span><span>时间</span>
                </div>
                <div
                  v-for="(item, idx) in patient.labs24h.abnormalItems"
                  :key="idx"
                  class="lab-item"
                >
                  <div class="lab-name">{{ item.itemName }}</div>
                  <div class="lab-result">
                    <span class="lab-value">{{ item.result }}</span>
                    <span class="lab-unit" v-if="item.unit">{{ item.unit }}</span>
                    <span class="lab-trend" v-if="item.trend === 'UP'">升</span>
                    <span class="lab-trend down" v-else-if="item.trend === 'DOWN'">降</span>
                  </div>
                  <div class="lab-ref">{{ item.refRange || '—' }}</div>
                  <div class="lab-time">{{ shortLabTime(item.checkTime) }}</div>
                </div>
              </div>
            </template>
          </div>

          <!-- 培养与药敏摘要 -->
          <div :class="['summary-card', 'culture-card', { 'card-mini': cultureIsEmpty }]">
            <div class="card-header">
              <div>
                <div class="card-kicker">CULTURE</div>
                <h3 class="card-title">培养与药敏</h3>
              </div>
              <template v-if="patient.culture">
                <el-tag
                  :type="cultureTagType(patient.culture.dataStatus)"
                  size="small"
                  effect="plain"
                >
                  {{ cultureStatusText(patient.culture.dataStatus) }}
                </el-tag>
              </template>
            </div>

            <div v-if="cultureIsEmpty" class="mini-hint">
              {{ patient.culture && patient.culture.dataStatus === 'UNKNOWN' ? '数据暂不可用' : '近期无培养送检' }}
            </div>

            <template v-else-if="patient.culture">
              <div class="culture-meta">
                <div class="meta-row">
                  <span class="meta-label">标本</span>
                  <span class="meta-val">{{ patient.culture.latestSpecimen || '—' }}</span>
                </div>
                <div class="meta-row">
                  <span class="meta-label">采样时间</span>
                  <span class="meta-val">{{ patient.culture.sampleTime || '—' }}</span>
                </div>
                <div class="meta-row" v-if="patient.culture.reportTime">
                  <span class="meta-label">报告时间</span>
                  <span class="meta-val">{{ patient.culture.reportTime }}</span>
                </div>
              </div>
              <div v-if="patient.culture.dataStatus === 'PENDING'" class="block-pending">
                已送检，等待报告
              </div>
              <template v-else>
                <div class="culture-organisms" v-if="patient.culture.organisms && patient.culture.organisms.length">
                  <div class="org-label">检出菌</div>
                  <div class="org-list">
                    <el-tag
                      v-for="(org, i) in patient.culture.organisms"
                      :key="i"
                      type="danger"
                      size="small"
                      effect="plain"
                    >{{ org }}</el-tag>
                  </div>
                </div>
                <div v-else class="block-empty">未检出致病菌</div>
                <div class="culture-risk" v-if="patient.culture.drugResistanceRisk">
                  <el-tag :type="cultureRiskType(patient.culture.drugResistanceRisk)" size="small">
                    {{ patient.culture.drugResistanceRisk }}
                  </el-tag>
                </div>
                <div class="culture-ast" v-if="patient.culture.astSummary">
                  <div class="ast-label">药敏摘要</div>
                  <div class="ast-text">{{ patient.culture.astSummary }}</div>
                </div>
              </template>
            </template>
          </div>
        </div>

        <!-- 脓毒症集束化状态 -->
        <div :class="['summary-card', 'sepsis-card', { 'card-mini': sepsisIsEmpty }]" v-if="patient.sepsisBundle">
          <div class="card-header">
            <div>
              <div class="card-kicker">SEPSIS BUNDLE</div>
              <h3 class="card-title">脓毒症集束化</h3>
            </div>
            <el-tag
              :type="sepsisTagType(patient.sepsisBundle.dataStatus)"
              size="small"
              effect="plain"
            >
              {{ sepsisStatusText(patient.sepsisBundle.dataStatus) }}
            </el-tag>
          </div>

          <div v-if="sepsisIsEmpty" class="mini-hint">
            {{ patient.sepsisBundle.dataStatus === 'UNKNOWN' ? '数据暂不可用' : '暂无脓毒症集束化评估记录' }}
          </div>

          <template v-else>
            <div class="sepsis-progress">
              <div class="bundle-col">
                <div class="bundle-label">1 小时</div>
                <div class="bundle-bar-wrap">
                  <div class="bundle-bar" :style="{ width: bundlePercent(patient.sepsisBundle.h1Completed, patient.sepsisBundle.h1Total) }"></div>
                </div>
                <div class="bundle-count">{{ patient.sepsisBundle.h1Completed }}/{{ patient.sepsisBundle.h1Total }}</div>
              </div>
              <div class="bundle-col">
                <div class="bundle-label">3 小时</div>
                <div class="bundle-bar-wrap">
                  <div class="bundle-bar" :style="{ width: bundlePercent(patient.sepsisBundle.h3Completed, patient.sepsisBundle.h3Total) }"></div>
                </div>
                <div class="bundle-count">{{ patient.sepsisBundle.h3Completed }}/{{ patient.sepsisBundle.h3Total }}</div>
              </div>
              <div class="bundle-col">
                <div class="bundle-label">6 小时</div>
                <div class="bundle-bar-wrap">
                  <div class="bundle-bar" :style="{ width: bundlePercent(patient.sepsisBundle.h6Completed, patient.sepsisBundle.h6Total) }"></div>
                </div>
                <div class="bundle-count">{{ patient.sepsisBundle.h6Completed }}/{{ patient.sepsisBundle.h6Total }}</div>
              </div>
            </div>
            <div class="sepsis-pending" v-if="patient.sepsisBundle.pendingItems && patient.sepsisBundle.pendingItems.length">
              <div class="pending-label">未完成项</div>
              <div class="pending-list">
                <span v-for="(p, i) in patient.sepsisBundle.pendingItems" :key="i" class="pending-tag">{{ p }}</span>
              </div>
            </div>
            <div class="sepsis-record-time" v-if="patient.sepsisBundle.recordTime">
              最近评估：{{ patient.sepsisBundle.recordTime }}
            </div>
          </template>
        </div>

        <!-- 底部：今日待办 + 快捷操作 左右分栏 -->
        <div class="summary-row bottom-row">
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
    cultureIsEmpty() {
      return !this.patient || !this.patient.culture ||
        this.patient.culture.dataStatus === 'UNKNOWN' ||
        this.patient.culture.dataStatus === 'NOT_SENT'
    },
    sepsisIsEmpty() {
      return !this.patient || !this.patient.sepsisBundle ||
        this.patient.sepsisBundle.dataStatus === 'NOT_APPLICABLE' ||
        this.patient.sepsisBundle.dataStatus === 'UNKNOWN'
    },
    temperatureTrendUnknown() {
      return !this.patient || !this.patient.temperatureTrend ||
        this.patient.temperatureTrend.dataStatus === 'UNKNOWN'
    },
    temperatureChart() {
      const trend = this.patient && this.patient.temperatureTrend
      if (!trend || trend.dataStatus !== 'FOUND' || !trend.points || !trend.points.length) return null
      const values = trend.points.map(point => Number(point.value)).filter(Number.isFinite)
      if (!values.length) return null
      const minValue = Math.min(...values)
      const maxValue = Math.max(...values)
      const range = Math.max(maxValue - minValue, 0.4)
      const low = minValue - range * 0.15
      const high = maxValue + range * 0.15
      const left = 6
      const width = 208
      const top = 7
      const height = 43
      const points = trend.points.map((point, index) => {
        const value = Number(point.value)
        const x = trend.points.length === 1 ? 110 : left + width * index / (trend.points.length - 1)
        const y = top + (high - value) / (high - low) * height
        const isMin = value === minValue
        const isMax = value === maxValue
        return {
          key: `${point.time}-${index}`,
          x: Number(x.toFixed(2)),
          y: Number(y.toFixed(2)),
          value: Number(value.toFixed(1)),
          time: this.shortTemperatureTime(point.time),
          isMin,
          isMax,
          isExtreme: isMin || isMax,
          labelY: isMin ? Math.max(y - 8, 8) : Math.min(y + 14, 60)
        }
      })
      const min = points.find(point => point.isMin)
      const max = points.find(point => point.isMax)
      return { points, polyline: points.map(point => `${point.x},${point.y}`).join(' '), min, max }
    },
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
    shortTemperatureTime(t) {
      if (!t) return '--:--'
      const match = String(t).match(/(\d{2}):(\d{2})(?::\d{2})?$/)
      return match ? `${match[1]}:${match[2]}` : String(t).slice(-5)
    },
    shortLabTime(t) {
      if (!t) return '—'
      const value = String(t)
      const match = value.match(/(\d{2})-(\d{2})\s+(\d{2}:\d{2})/)
      return match ? `${match[2]}-${match[3]}` : value.replace('T', ' ').slice(-11)
    },
    riskTagType(level) {
      if (level === '高风险') return 'danger'
      if (level === '中风险') return 'warning'
      return 'info'
    },
    cultureTagType(status) {
      if (status === 'POSITIVE') return 'danger'
      if (status === 'PENDING') return 'warning'
      if (status === 'NEGATIVE') return 'success'
      return 'info'
    },
    cultureStatusText(status) {
      const map = { NOT_SENT: '未送检', PENDING: '待报告', NEGATIVE: '未检出', POSITIVE: '已检出', UNKNOWN: '不可用' }
      return map[status] || status
    },
    cultureRiskType(risk) {
      if (risk === 'MDR') return 'danger'
      if (risk === 'MRSA') return 'warning'
      return 'info'
    },
    sepsisTagType(status) {
      if (status === 'COMPLETED') return 'success'
      if (status === 'IN_PROGRESS') return 'warning'
      if (status === 'OVERDUE') return 'danger'
      return 'info'
    },
    sepsisStatusText(status) {
      const map = { NOT_APPLICABLE: '无记录', IN_PROGRESS: '进行中', COMPLETED: '已完成', OVERDUE: '已超时', UNKNOWN: '不可用' }
      return map[status] || status
    },
    bundlePercent(completed, total) {
      if (!total || total <= 0) return '0%'
      return Math.min(100, Math.round((completed / total) * 100)) + '%'
    },
    sofaGradeText(score) {
      if (score == null) return '—'
      if (score <= 6) return '轻度'
      if (score <= 9) return '中度'
      if (score <= 12) return '重度'
      return '极重度'
    },
    sofaGradeClass(score) {
      if (score == null) return ''
      if (score <= 6) return 'grade-low'
      if (score <= 9) return 'grade-mid'
      if (score <= 12) return 'grade-high'
      return 'grade-critical'
    },
    apacheGradeText(score) {
      if (score == null) return '—'
      if (score <= 4) return '低风险'
      if (score <= 9) return '较低风险'
      if (score <= 14) return '中风险'
      if (score <= 19) return '较高风险'
      if (score <= 24) return '高风险'
      return '极高风险'
    },
    apacheGradeClass(score) {
      if (score == null) return ''
      if (score <= 9) return 'grade-low'
      if (score <= 14) return 'grade-mid'
      if (score <= 19) return 'grade-high'
      return 'grade-critical'
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

/* 第一行两列：患者概览 + 评分 */
.top-row {
  display: grid;
  grid-template-columns: 1.5fr 1fr;
  gap: 16px;
  margin-bottom: 0;
}
.top-row .summary-card { margin-bottom: 0; }

/* 底部左右分栏：待办 + 快捷操作 */
.bottom-row {
  display: grid;
  grid-template-columns: 1.1fr 1fr;
  gap: 16px;
  margin-top: 16px;
}
.bottom-row .summary-card { margin-bottom: 0; }

/* 患者概览 */
.overview-body {
  display: flex;
  gap: 24px;
  align-items: flex-start;
}
.overview-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.info-line {
  display: flex;
  align-items: baseline;
  gap: 10px;
  font-size: 13px;
}
.info-line-label {
  color: #a8a29e;
  font-size: 12px;
  min-width: 56px;
  flex-shrink: 0;
}
.info-line-value {
  color: #292524;
  font-weight: 600;
}
.overview-support {
  flex: 1;
  min-width: 0;
}
.overview-support .support-label {
  font-size: 11px;
  color: #a8a29e;
  margin-bottom: 8px;
  letter-spacing: 1px;
  text-transform: uppercase;
}
.support-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.support-chip {
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  background: #f5f5f4;
  color: #a8a29e;
  border: 1px solid #e7e5e4;
}
.support-chip.on {
  background: #fff7ed;
  color: #c2410c;
  border-color: #fdba74;
  font-weight: 600;
}
.support-none {
  font-size: 12px;
  color: #a8a29e;
  margin-top: 6px;
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
  margin-bottom: 6px;
}
.score-value.score-na {
  font-size: 18px;
  font-weight: 500;
  color: #a8a29e;
}
.score-grade {
  font-size: 11px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 999px;
  display: inline-block;
}
.score-grade.grade-low { background: #dcfce7; color: #166534; }
.score-grade.grade-mid { background: #fef3c7; color: #92400e; }
.score-grade.grade-high { background: #ffedd5; color: #c2410c; }
.score-grade.grade-critical { background: #fee2e2; color: #991b1b; }
.score-mortality {
  font-size: 11px;
  color: #a8a29e;
  margin-top: 4px;
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
.temperature-metric { min-width: 0; }
.temperature-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
}
.temperature-chart {
  display: block;
  width: 100%;
  max-width: 220px;
  height: 62px;
  margin: 4px 0 0;
  overflow: visible;
}
.temperature-axis { stroke: #e7e5e4; stroke-width: 1; }
.temperature-line {
  fill: none;
  stroke: #ea580c;
  stroke-width: 2;
  stroke-linecap: round;
  stroke-linejoin: round;
}
.temperature-dot { fill: #a8a29e; stroke: #fff; stroke-width: 1.5; }
.temperature-dot.min { fill: #16a34a; }
.temperature-dot.max { fill: #dc2626; }
.temperature-label { font-size: 9px; font-weight: 700; }
.min-label { fill: #15803d; }
.max-label { fill: #b91c1c; }
.temperature-extremes {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  margin-top: -2px;
  font-size: 10px;
  line-height: 1.3;
}
.temperature-extreme { white-space: nowrap; }
.temperature-extreme small { color: #a8a29e; font-size: 10px; }
.min-extreme { color: #15803d; }
.max-extreme { color: #b91c1c; }
.temperature-status { font-size: 11px; color: #a8a29e; margin-top: 10px; }
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
  padding: 10px;
  color: #a8a29e;
  font-size: 13px;
}

/* 快捷操作 */
.action-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
}
.action-btn {
  width: 100%;
  height: 40px;
  font-size: 13px;
}
.primary-action {
  font-weight: 600;
  background-color: #ea580c !important;
  border-color: #ea580c !important;
  color: #fff !important;
}
.primary-action:hover {
  background-color: #c2410c !important;
  border-color: #c2410c !important;
  color: #fff !important;
}
.primary-action:focus {
  background-color: #ea580c !important;
  border-color: #ea580c !important;
  color: #fff !important;
}

/* 依据弹窗 */
.evidence-content {
  font-size: 13px;
  color: #44403c;
  line-height: 1.7;
  white-space: pre-wrap;
}

/* 中间行：24h检验 + 培养药敏 */
.mid-row {
  display: grid;
  grid-template-columns: 1.3fr 1fr;
  gap: 16px;
  margin-top: 16px;
}
.mid-row .summary-card { margin-bottom: 0; }

/* 通用空状态 */
.block-empty {
  padding: 20px 0;
  text-align: center;
  color: #a8a29e;
  font-size: 13px;
}
.block-unknown {
  padding: 20px 0;
  text-align: center;
  color: #d97706;
  font-size: 13px;
}
.block-pending {
  padding: 12px 14px;
  background: #fffbeb;
  border: 1px solid #fde68a;
  border-radius: 8px;
  color: #b45309;
  font-size: 13px;
  text-align: center;
  margin-top: 10px;
}

/* 空状态折叠卡片 */
.card-mini {
  padding: 12px 20px;
}
.card-mini .card-header {
  margin-bottom: 0;
}
.card-mini .card-title {
  margin-bottom: 0;
  font-size: 14px;
}
.card-mini .card-kicker {
  margin-bottom: 2px;
}
.mini-hint {
  font-size: 12px;
  color: #a8a29e;
  margin-top: 6px;
  padding-left: 2px;
}

/* 24h 检验 */
.labs-summary {
  display: flex;
  gap: 10px;
  align-items: center;
}
.labs-summary-item {
  font-size: 13px;
  font-weight: 700;
  color: #dc2626;
}
.labs-summary-item.normal {
  font-size: 12px;
  color: #a8a29e;
}
.labs-table {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 280px;
  overflow-y: auto;
}
.labs-table-head,
.lab-item {
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) minmax(92px, .85fr) minmax(80px, .8fr) 62px;
  align-items: center;
  column-gap: 10px;
}
.labs-table-head {
  min-height: 28px;
  padding: 0 10px;
  color: #a8a29e;
  font-size: 10px;
}
.lab-item {
  position: relative;
  min-height: 48px;
  padding: 7px 10px 7px 13px;
  border-bottom: 1px solid #f5f5f4;
  background: #fff;
}
.lab-item::before {
  content: '';
  position: absolute;
  left: 0;
  top: 10px;
  bottom: 10px;
  width: 3px;
  border-radius: 0 2px 2px 0;
  background: #dc2626;
}
.lab-name {
  font-size: 13px;
  font-weight: 600;
  color: #1c1917;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.lab-result {
  display: flex;
  align-items: center;
  gap: 5px;
}
.lab-value {
  font-size: 16px;
  font-weight: 700;
  color: #dc2626;
}
.lab-unit {
  font-size: 11px;
  color: #78716c;
}
.lab-trend {
  padding: 1px 4px;
  border-radius: 3px;
  background: #fee2e2;
  font-size: 10px;
  color: #dc2626;
  font-weight: 700;
}
.lab-trend.down { color: #2563eb; background: #dbeafe; }
.lab-ref {
  font-size: 11px;
  color: #78716c;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.lab-time {
  font-size: 11px;
  color: #a8a29e;
  text-align: right;
  white-space: nowrap;
}

/* 培养药敏 */
.culture-meta {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 12px;
}
.meta-row {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
}
.meta-label { color: #a8a29e; }
.meta-val { color: #292524; font-weight: 500; }
.culture-organisms { margin-bottom: 10px; }
.org-label {
  font-size: 11px;
  color: #a8a29e;
  margin-bottom: 6px;
}
.org-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.culture-risk { margin-bottom: 10px; }
.culture-ast {
  padding-top: 10px;
  border-top: 1px solid #f5f5f4;
}
.ast-label {
  font-size: 11px;
  color: #a8a29e;
  margin-bottom: 4px;
}
.ast-text {
  font-size: 12px;
  color: #57534e;
  line-height: 1.5;
}

/* 脓毒症集束化 */
.sepsis-card { margin-top: 16px; }
.sepsis-progress {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
  margin-bottom: 14px;
}
.bundle-col { text-align: center; }
.bundle-label {
  font-size: 12px;
  color: #78716c;
  margin-bottom: 6px;
}
.bundle-bar-wrap {
  height: 8px;
  background: #f5f5f4;
  border-radius: 4px;
  overflow: hidden;
  margin-bottom: 4px;
}
.bundle-bar {
  height: 100%;
  background: linear-gradient(90deg, #fb923c, #ea580c);
  border-radius: 4px;
  transition: width .3s;
}
.bundle-count {
  font-size: 14px;
  font-weight: 700;
  color: #1c1917;
}
.sepsis-pending { margin-bottom: 10px; }
.pending-label {
  font-size: 11px;
  color: #a8a29e;
  margin-bottom: 6px;
}
.pending-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.pending-tag {
  padding: 3px 10px;
  background: #fff7ed;
  border: 1px solid #fed7aa;
  border-radius: 999px;
  font-size: 12px;
  color: #c2410c;
}
.sepsis-record-time {
  font-size: 11px;
  color: #a8a29e;
  text-align: right;
}

/* 响应式 */
@media (max-width: 1100px) {
  .top-row { grid-template-columns: 1fr; }
  .mid-row { grid-template-columns: 1fr; }
  .bottom-row { grid-template-columns: 1fr; }
  .infection-metrics { grid-template-columns: repeat(2, 1fr); }
  .sepsis-progress { grid-template-columns: 1fr; gap: 12px; }
}
@media (max-width: 700px) {
  .summary-page { padding: 14px; }
  .infection-metrics { grid-template-columns: 1fr; }
  .action-grid { grid-template-columns: 1fr; }
  .patient-name { font-size: 20px; }
}
</style>
