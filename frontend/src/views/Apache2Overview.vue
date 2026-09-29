<template>
  <div class="apache2-overview qb-theme">
    <section class="overview-header">
      <div class="header-left">
        <h1 class="page-title">APACHE II 评分总览</h1>
        <span v-if="departName" class="depart-pill">{{ departName }}</span>
      </div>
      <div class="header-right">
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          value-format="YYYY-MM-DD"
          :clearable="false"
          class="date-picker"
        />
        <el-button type="primary" @click="loadData" class="query-btn">
          <el-icon><Search /></el-icon> 查询
        </el-button>
        <el-button type="warning" :loading="autoGenerating" @click="manualAutoGenerate">
          自动补全在科患者评分
        </el-button>
      </div>
    </section>

    <el-empty v-if="departCodeInvalid" :description="departCodeInvalidText" class="empty-state"></el-empty>

    <div v-else>
      <div class="stat-row">
        <div class="stat-card">
          <div class="card-kicker">RECORDS</div>
          <div class="stat-label">评分记录数</div>
          <div class="stat-value">{{ summary.totalCount }}</div>
          <div class="stat-sub">当前时间范围</div>
        </div>
        <div class="stat-card">
          <div class="card-kicker">AVERAGE</div>
          <div class="stat-label">平均总分</div>
          <div class="stat-value">{{ fmt2(summary.avgScore) }}</div>
          <div class="stat-sub">APACHE II 总分</div>
        </div>
        <div class="stat-card">
          <div class="card-kicker">MORTALITY</div>
          <div class="stat-label">平均死亡率</div>
          <div class="stat-value">{{ fmt2(summary.avgMortality) }}<span class="stat-unit">%</span></div>
          <div class="stat-sub">预测院内死亡率</div>
        </div>
        <div class="stat-card stat-danger">
          <div class="card-kicker">HIGH RISK</div>
          <div class="stat-label">高危患者数</div>
          <div class="stat-value">{{ summary.highRiskCount }}</div>
          <div class="stat-sub">总分 ≥ 20 分</div>
        </div>
      </div>

      <div class="chart-card">
        <div class="card-header">
          <div class="card-kicker">DISTRIBUTION</div>
          <div class="card-title">APACHE II 评分分布</div>
        </div>
        <div v-show="summary.totalCount > 0" ref="distributionChartRef" class="chart-container"></div>
        <div v-if="!summary.totalCount" class="chart-empty">当前时间范围内暂无评分数据</div>
      </div>

      <div class="table-card">
        <div class="card-header">
          <div>
            <div class="card-kicker">PATIENTS</div>
            <div class="card-title">患者评分列表</div>
          </div>
          <span class="table-count">共 {{ records.length }} 条记录</span>
        </div>
        <el-table
          ref="tableRef"
          v-loading="loading"
          :data="records"
          style="width: 100%"
          row-key="id"
          @row-click="handleRowClick"
          class="apache-table"
        >
          <el-table-column type="expand">
            <template #default="{ row }">
              <div class="detail-panel">
                <div class="detail-section">
                  <div class="detail-section-title">评分汇总</div>
                  <div class="detail-scores">
                    <div class="detail-score-item">
                      <span class="ds-label">A 年龄分</span>
                      <span class="ds-val">{{ row.ageScore }}</span>
                    </div>
                    <div class="detail-score-item">
                      <span class="ds-label">B 慢性健康分</span>
                      <span class="ds-val">{{ row.chronicScore }}</span>
                    </div>
                    <div class="detail-score-item">
                      <span class="ds-label">C GCS 分</span>
                      <span class="ds-val">{{ row.gcsScore }}</span>
                    </div>
                    <div class="detail-score-item">
                      <span class="ds-label">D 急性生理分</span>
                      <span class="ds-val">{{ row.physiologyScore }}</span>
                    </div>
                    <div class="detail-score-item ds-total">
                      <span class="ds-label">总分</span>
                      <span class="ds-val">{{ row.totalScore }}</span>
                    </div>
                    <div class="detail-score-item ds-mortality">
                      <span class="ds-label">预测死亡率</span>
                      <span class="ds-val">{{ fmt2(row.mortalityRate) }}%</span>
                    </div>
                  </div>
                </div>
                <div class="detail-section">
                  <div class="detail-section-title">评分信息</div>
                  <div class="detail-info-grid">
                    <div class="di-item"><span class="di-label">评分来源</span><span class="di-value">{{ scoreTypeText(row.scoreType) }}</span></div>
                    <div class="di-item"><span class="di-label">评分时间</span><span class="di-value">{{ row.scoreTime }}</span></div>
                    <div class="di-item"><span class="di-label">疾病分类</span><span class="di-value">{{ diagnosisTypeText(row.diagnosisType) }}</span></div>
                    <div class="di-item"><span class="di-label">创建人</span><span class="di-value">{{ operatorLabel(row.createBy) || '—' }}</span></div>
                  </div>
                </div>
                <div class="detail-actions">
                  <el-button size="small" type="primary" @click.stop="goToScorePage(row)">
                    查看 / 编辑评分
                  </el-button>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="patientName" label="患者姓名" min-width="90" />
          <el-table-column prop="inHospitalNo" label="住院号" min-width="130" />
          <el-table-column prop="scoreType" label="评分来源" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="row.scoreType === 'custom' ? 'warning' : 'info'" size="small" effect="plain">
                {{ scoreTypeText(row.scoreType) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="ageScore" label="A年龄" width="70" align="center" />
          <el-table-column prop="chronicScore" label="B慢性" width="70" align="center" />
          <el-table-column prop="gcsScore" label="C GCS" width="70" align="center" />
          <el-table-column prop="physiologyScore" label="D生理" width="70" align="center" />
          <el-table-column prop="totalScore" label="总分" width="80" align="center">
            <template #default="{ row }">
              <span :class="['total-score', getTotalScoreClass(row.totalScore)]">{{ row.totalScore }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="mortalityRate" label="死亡率" width="90" align="center">
            <template #default="{ row }">
              <span :class="['mortality-val', { 'mortality-high': row.mortalityRate >= 40 }]">
                {{ fmt2(row.mortalityRate) }}%
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="scoreTime" label="评分时间" min-width="160" />
        </el-table>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import request from '../api/request'
import { appendExternalContext } from '../utils/external'
import { operatorLabel } from '../utils/operator'
import * as echarts from '../utils/echarts'
import { currentDepart } from '../utils/departContext'

const route = useRoute()
const router = useRouter()
const RAW_PLACEHOLDER = /\$\{[^}]*\}/
const pickQuery = (v) => {
  const s = String(v == null ? '' : v).trim()
  return s && !RAW_PLACEHOLDER.test(s) ? s : ''
}
const externalDepartCode = pickQuery(route.query.departCode)
const departCode = ref(externalDepartCode || currentDepart.departCode)
const departName = ref(pickQuery(route.query.departName))
const departCodeInvalid = computed(() => !departCode.value)
const departCodeInvalidText = computed(() => {
  if (RAW_PLACEHOLDER.test(String(route.query.departCode || ''))) {
    return '外链科室参数未被 ICU 系统替换（仍为 ${departCode}），请在 ICU 外链配置中确认已传入科室编码'
  }
  return '缺少科室权限参数，请通过外链访问'
})

const dateRange = ref([])
const records = ref([])
const loading = ref(false)
const tableRef = ref(null)
const summary = reactive({
  totalCount: 0,
  avgScore: 0,
  avgMortality: 0,
  highRiskCount: 0,
  scoreDistribution: {}
})

const distributionChartRef = ref(null)
let distributionChart = null

function fmtDate(d) {
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

onMounted(() => {
  const today = new Date()
  dateRange.value = [fmtDate(new Date(today.getFullYear(), today.getMonth(), 1)), fmtDate(today)]
  window.addEventListener('resize', handleResize)
  if (!departCodeInvalid.value) {
    loadData()
  }
})

watch(() => currentDepart.departCode, (code) => {
  if (!externalDepartCode && code) {
    departCode.value = code
    if (!departCodeInvalid.value) loadData()
  }
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  if (distributionChart) {
    distributionChart.dispose()
    distributionChart = null
  }
})

async function loadData() {
  if (!dateRange.value || dateRange.value.length < 2) {
    ElMessage.warning('请选择时间范围')
    return
  }
  loading.value = true
  try {
    const data = (await request.get('/apache2/overview', {
      params: {
        departCode: departCode.value,
        startTime: dateRange.value[0] + ' 00:00:00',
        endTime: dateRange.value[1] + ' 23:59:59'
      }
    })) || {}
    summary.totalCount = data.totalCount || 0
    summary.avgScore = data.avgScore || 0
    summary.avgMortality = data.avgMortality || 0
    summary.highRiskCount = data.highRiskCount || 0
    summary.scoreDistribution = data.scoreDistribution || {}
    records.value = data.records || []
    await nextTick()
    renderDistributionChart()
  } catch (e) {
    console.warn('APACHE II 总览加载失败: ', e && e.message)
  } finally {
    loading.value = false
  }
}

const autoGenerating = ref(false)
async function manualAutoGenerate() {
  try {
    await ElMessageBox.confirm(
      '将为当前科室所有「在科且入科超过 24 小时、尚无评分记录」的患者生成一份 APACHE II 自动评分；已有记录的患者会自动跳过，可安全重复执行。是否继续？',
      '自动生成在科患者评分',
      { confirmButtonText: '开始生成', cancelButtonText: '取消', type: 'warning' }
    )
  } catch (action) {
    return
  }
  autoGenerating.value = true
  try {
    const res = (await request.post('/apache2/auto-generate', null, {
      params: { departCode: departCode.value || '', overHours: 24 }
    })) || {}
    ElMessage.success(`扫描 ${res.scanned || 0} 人，新增 ${res.created || 0} 份，跳过 ${res.skipped || 0} 人，失败 ${res.failed || 0} 人`)
    loadData()
  } catch (e) {
    console.warn('自动生成失败: ', e && e.message)
  } finally {
    autoGenerating.value = false
  }
}

function renderDistributionChart() {
  if (!distributionChartRef.value) return
  if (!distributionChart) {
    distributionChart = echarts.init(distributionChartRef.value)
  }
  const dist = summary.scoreDistribution || {}
  const categories = Object.keys(dist)
  const values = Object.values(dist).map((v) => Number(v) || 0)
  if (!categories.length) {
    distributionChart.clear()
    return
  }
  distributionChart.setOption({
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      backgroundColor: '#1c1917',
      borderColor: '#1c1917',
      textStyle: { color: '#fff', fontSize: 12 }
    },
    grid: { left: 50, right: 24, top: 24, bottom: 32 },
    xAxis: {
      type: 'category',
      data: categories,
      axisLabel: { color: '#78716c', fontSize: 12 },
      axisLine: { lineStyle: { color: '#e7e5e4' } },
      axisTick: { show: false }
    },
    yAxis: {
      type: 'value',
      name: '人数',
      nameTextStyle: { color: '#a8a29e', fontSize: 11 },
      axisLabel: { color: '#78716c', fontSize: 12 },
      splitLine: { lineStyle: { color: '#f5f5f4' } }
    },
    series: [{
      type: 'bar',
      data: values,
      barWidth: '45%',
      itemStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: '#f97316' },
          { offset: 1, color: '#fdba74' }
        ]),
        borderRadius: [6, 6, 0, 0]
      },
      label: { show: true, position: 'top', color: '#57534e', fontSize: 12, fontWeight: 600 }
    }]
  }, true)
  distributionChart.resize()
}

function handleResize() {
  if (distributionChart) {
    distributionChart.resize()
  }
}

function handleRowClick(row, column) {
  if (column && column.type === 'expand') return
  if (tableRef.value) {
    tableRef.value.toggleRowExpansion(row)
  }
}

function fmt2(v) {
  const n = Number(v)
  return isNaN(n) ? '0.00' : n.toFixed(2)
}

function scoreTypeText(type) {
  const map = {
    auto: '自动评分',
    daily: '自动评分',
    custom: '手工评分',
    admission: '入科时',
    '24h': '24小时',
    '48h': '48小时'
  }
  return map[type] || type || '—'
}

function diagnosisTypeText(type) {
  const map = { nonoperative: '非手术类', operative: '手术类', none: '以上都不是' }
  return map[type] || type || '—'
}

function getTotalScoreClass(score) {
  if (score >= 30) return 'score-danger'
  if (score >= 20) return 'score-warning'
  if (score >= 10) return 'score-info'
  return 'score-normal'
}

function goToScorePage(row) {
  const url = `/page/apache2-score?inHospitalNo=${encodeURIComponent(row.inHospitalNo || '')}` +
    `&patientName=${encodeURIComponent(row.patientName || '')}` +
    `&departCode=${encodeURIComponent(departCode.value)}` +
    `&recordId=${encodeURIComponent(row.id)}`
  router.push(appendExternalContext(url))
}
</script>

<style scoped>
.apache2-overview {
  min-height: 100%;
  background: #f5f5f4;
  padding: 20px 24px 40px;
}
.overview-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
  flex-wrap: wrap;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}
.page-title {
  font-size: 22px;
  font-weight: 700;
  color: #1c1917;
  margin: 0;
  line-height: 1.2;
}
.depart-pill {
  display: inline-flex;
  align-items: center;
  padding: 3px 12px;
  border-radius: 999px;
  background: #fff7ed;
  color: #c2410c;
  font-weight: 600;
  font-size: 12px;
  border: 1px solid #fed7aa;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.date-picker { width: 260px; }
.query-btn { font-weight: 600; }
.empty-state { padding: 80px 0; }

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
  margin: 0;
}
.card-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 16px;
}

.stat-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 16px;
}
.stat-card {
  background: #fff;
  border: 1px solid #e7e5e4;
  border-radius: 10px;
  padding: 18px 20px;
  position: relative;
  overflow: hidden;
}
.stat-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: linear-gradient(90deg, #ea580c, #f97316);
}
.stat-card.stat-danger::before {
  background: linear-gradient(90deg, #dc2626, #ef4444);
}
.stat-label {
  font-size: 13px;
  color: #57534e;
  margin-bottom: 8px;
}
.stat-value {
  font-size: 34px;
  font-weight: 800;
  color: #1c1917;
  line-height: 1.1;
  margin-bottom: 6px;
}
.stat-card.stat-danger .stat-value { color: #dc2626; }
.stat-unit {
  font-size: 18px;
  font-weight: 600;
  margin-left: 2px;
}
.stat-sub {
  font-size: 12px;
  color: #a8a29e;
}

.chart-card {
  background: #fff;
  border: 1px solid #e7e5e4;
  border-radius: 10px;
  padding: 18px 20px;
  margin-bottom: 16px;
}
.chart-container {
  height: 280px;
  width: 100%;
}
.chart-empty {
  height: 280px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #a8a29e;
  font-size: 13px;
  background: #fafaf9;
  border-radius: 8px;
}

.table-card {
  background: #fff;
  border: 1px solid #e7e5e4;
  border-radius: 10px;
  padding: 18px 20px;
}
.table-count {
  font-size: 12px;
  color: #a8a29e;
  margin-top: 6px;
}

.apache-table {
  --el-table-border-color: #f5f5f4;
  --el-table-header-bg-color: #fafaf9;
  --el-table-header-text-color: #57534e;
  --el-table-row-hover-bg-color: #fff7ed;
}
.apache-table :deep(.el-table__header th) {
  font-weight: 600;
  font-size: 13px;
}
.apache-table :deep(.el-table__cell) { padding: 10px 0; }
.apache-table :deep(.el-table__row) { cursor: pointer; }
.total-score { font-weight: 700; font-size: 15px; }
.score-danger { color: #dc2626; }
.score-warning { color: #ea580c; }
.score-info { color: #2563eb; }
.score-normal { color: #16a34a; }
.mortality-val { font-weight: 600; color: #57534e; }
.mortality-high { color: #dc2626; }

.detail-panel {
  padding: 16px 20px;
  background: #fafaf9;
  border-radius: 8px;
  margin: 8px 0;
}
.detail-section { margin-bottom: 16px; }
.detail-section:last-child { margin-bottom: 0; }
.detail-section-title {
  font-size: 14px;
  font-weight: 700;
  color: #1c1917;
  margin-bottom: 12px;
  padding-left: 10px;
  border-left: 3px solid #ea580c;
}
.detail-scores {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 10px;
}
.detail-score-item {
  background: #fff;
  padding: 14px 10px;
  border-radius: 8px;
  text-align: center;
  border: 1px solid #e7e5e4;
}
.ds-label {
  display: block;
  font-size: 11px;
  color: #a8a29e;
  margin-bottom: 6px;
}
.ds-val {
  font-size: 22px;
  font-weight: 800;
  color: #1c1917;
}
.ds-total {
  background: #fff7ed;
  border-color: #fdba74;
}
.ds-total .ds-val { color: #ea580c; }
.ds-mortality {
  background: #fef2f2;
  border-color: #fecaca;
}
.ds-mortality .ds-val { color: #dc2626; }
.detail-info-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
}
.di-item {
  display: flex;
  gap: 8px;
  font-size: 13px;
}
.di-label { color: #a8a29e; flex-shrink: 0; }
.di-value { color: #44403c; font-weight: 500; }
.detail-actions {
  text-align: right;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid #e7e5e4;
}

@media (max-width: 1200px) {
  .stat-row { grid-template-columns: repeat(2, 1fr); }
  .detail-scores { grid-template-columns: repeat(3, 1fr); }
}
@media (max-width: 768px) {
  .apache2-overview { padding: 14px; }
  .stat-row { grid-template-columns: 1fr; }
  .detail-scores { grid-template-columns: repeat(2, 1fr); }
  .detail-info-grid { grid-template-columns: 1fr; }
  .page-title { font-size: 18px; }
  .date-picker { width: 100%; }
}
</style>
