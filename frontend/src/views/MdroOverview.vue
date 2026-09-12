<template>
  <div class="mdro-overview">
    <!-- 顶部筛选栏 -->
    <div class="filter-bar">
      <div class="filter-left">
        <el-select v-model="selectedDepartCode" placeholder="选择科室" style="width: 180px" @change="loadData">
          <el-option
            v-for="dept in departments"
            :key="dept.org_code"
            :label="dept.depart_name"
            :value="dept.org_code"
          />
        </el-select>
        <el-date-picker
          v-model="dateRange"
          type="monthrange"
          range-separator="至"
          start-placeholder="开始月份"
          end-placeholder="结束月份"
          value-format="YYYY-MM"
          :clearable="false"
          @change="handleDateChange"
        />
        <el-button type="primary" @click="loadData" :loading="loading">
          <el-icon><Refresh /></el-icon> 刷新
        </el-button>
      </div>
      <div class="filter-right">
        <el-button @click="goPatients">
          <el-icon><User /></el-icon> 患者明细
        </el-button>
        <el-button @click="goConfig">
          <el-icon><Setting /></el-icon> 细菌配置
        </el-button>
      </div>
    </div>

    <!-- 核心指标卡片 -->
    <div class="metric-cards">
      <div class="metric-card">
        <div class="metric-label">在科患者数</div>
        <div class="metric-value">
          {{ overview.totalPatients ?? '--' }}
          <span class="metric-unit">人</span>
        </div>
        <div class="metric-sub">统计周期内在科</div>
      </div>

      <div class="metric-card info">
        <div class="metric-label">细菌培养送检</div>
        <div class="metric-value">
          {{ overview.culturePatients ?? '--' }}
          <span class="metric-unit">人</span>
        </div>
        <div class="metric-sub">送检 {{ overview.totalCultureCount ?? 0 }} 次</div>
      </div>

      <div class="metric-card success">
        <div class="metric-label">培养阳性患者</div>
        <div class="metric-value">
          {{ overview.positivePatients ?? '--' }}
          <span class="metric-unit">人</span>
        </div>
        <div class="metric-sub">阳性 {{ overview.positiveCultureCount ?? 0 }} 次</div>
      </div>

      <div class="metric-card warning">
        <div class="metric-label">培养阳性率</div>
        <div class="metric-value">
          {{ overview.positiveRate ?? '--' }}
          <span class="metric-unit">%</span>
        </div>
        <div class="metric-sub">阳性/送检患者</div>
      </div>

      <div class="metric-card danger">
        <div class="metric-label">高风险细菌检出</div>
        <div class="metric-value">
          {{ overview.highRiskPatients ?? '--' }}
          <span class="metric-unit">人</span>
        </div>
        <div class="metric-sub">检出率 {{ overview.highRiskRate ?? 0 }}%</div>
      </div>
    </div>

    <!-- 细菌分类统计 -->
    <div class="metric-cards small">
      <div class="metric-card gram-positive">
        <div class="metric-label">革兰阳性菌</div>
        <div class="metric-value small">{{ overview.gramPositiveCount ?? 0 }}<span class="metric-unit">次</span></div>
      </div>
      <div class="metric-card gram-negative">
        <div class="metric-label">革兰阴性菌</div>
        <div class="metric-value small">{{ overview.gramNegativeCount ?? 0 }}<span class="metric-unit">次</span></div>
      </div>
      <div class="metric-card fungi">
        <div class="metric-label">真菌</div>
        <div class="metric-value small">{{ overview.fungiCount ?? 0 }}<span class="metric-unit">次</span></div>
      </div>
      <div class="metric-card other">
        <div class="metric-label">其他</div>
        <div class="metric-value small">{{ overview.otherCount ?? 0 }}<span class="metric-unit">次</span></div>
      </div>
    </div>

    <!-- 月度趋势图 -->
    <div class="chart-card">
      <div class="block-title">
        <span class="dot"></span>
        <span>月度趋势（阳性率 & 检出次数）</span>
      </div>
      <div ref="trendChartRef" class="chart-container"></div>
    </div>

    <!-- 菌株排名 + 分类占比 -->
    <div class="row-cards">
      <div class="chart-card flex-2">
        <div class="block-title">
          <span class="dot"></span>
          <span>检出菌株排名 TOP20</span>
        </div>
        <div ref="rankChartRef" class="chart-container tall"></div>
      </div>
      <div class="chart-card flex-1">
        <div class="block-title">
          <span class="dot"></span>
          <span>细菌分类占比</span>
        </div>
        <div ref="classChartRef" class="chart-container"></div>
      </div>
    </div>

    <!-- 标本类型分布 -->
    <div class="chart-card">
      <div class="block-title">
        <span class="dot"></span>
        <span>标本类型分布 TOP10</span>
      </div>
      <div ref="specimenChartRef" class="chart-container"></div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, nextTick, computed } from 'vue'
import { useRouter } from 'vue-router'
import { Refresh, User, Setting } from '@element-plus/icons-vue'
import request from '../api/request'
import * as echarts from 'echarts'

const router = useRouter()
const loading = ref(false)
const overview = reactive({})
const bacteriaRanks = ref([])
const dateRange = ref([])
const departments = ref([])
const selectedDepartCode = ref('20070131')

const trendChartRef = ref(null)
const rankChartRef = ref(null)
const classChartRef = ref(null)
const specimenChartRef = ref(null)

let trendChart = null
let rankChart = null
let classChart = null
let specimenChart = null

const dateRangeText = computed(() => {
  if (!dateRange.value || dateRange.value.length < 2) return '--'
  return `${dateRange.value[0]} ~ ${dateRange.value[1]}`
})

// 默认当月
function initDefaultDate() {
  const now = new Date()
  const current = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
  dateRange.value = [current, current]
}

function handleDateChange() {
  loadData()
}

function getTimeRange() {
  if (!dateRange.value || dateRange.value.length < 2) return null
  const [startMonth, endMonth] = dateRange.value
  const [sy, sm] = startMonth.split('-').map(Number)
  const [ey, em] = endMonth.split('-').map(Number)
  const startTime = `${sy}-${String(sm).padStart(2, '0')}-01 00:00:00`
  const endDate = new Date(ey, em, 1)
  const endTime = `${endDate.getFullYear()}-${String(endDate.getMonth() + 1).padStart(2, '0')}-01 00:00:00`
  return { startTime, endTime }
}

async function loadData() {
  const range = getTimeRange()
  if (!range) return
  loading.value = true
  try {
    const params = { ...range, departCode: selectedDepartCode.value || '' }
    const [overviewRes, rankRes, trendRes] = await Promise.all([
      request.get('/antibiotic/mdro/overview', { params }),
      request.get('/antibiotic/mdro/bacteria-rank', { params }),
      request.get('/antibiotic/mdro/monthly-trend', { params })
    ])
    Object.assign(overview, overviewRes || {})
    overview.monthlyTrend = trendRes || []
    bacteriaRanks.value = rankRes || []
    await nextTick()
    renderCharts()
  } catch (e) {
    console.warn('数据加载失败: ', e.message || e)
  } finally {
    loading.value = false
  }
}

async function loadDepartments() {
  try {
    const res = await request.get('/antibiotic/mdro/departments')
    departments.value = res || []
  } catch (e) {
    console.error('加载科室列表失败', e)
  }
}

function renderCharts() {
  renderTrendChart()
  renderRankChart()
  renderClassChart()
  renderSpecimenChart()
}

function renderTrendChart() {
  if (!trendChartRef.value) return
  if (!trendChart) trendChart = echarts.init(trendChartRef.value)
  const trend = overview.monthlyTrend || []
  trendChart.setOption({
    tooltip: { trigger: 'axis', confine: true },
    legend: { data: ['阳性率(%)', '革兰阳性菌', '革兰阴性菌', '真菌', '高风险细菌'], top: 0 },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', data: trend.map(t => t.month), axisLabel: { color: '#666' } },
    yAxis: [
      { type: 'value', name: '次数', axisLabel: { color: '#666' } },
      { type: 'value', name: '阳性率(%)', axisLabel: { color: '#666' }, max: 100 }
    ],
    series: [
      { name: '阳性率(%)', type: 'line', yAxisIndex: 1, data: trend.map(t => t.positiveRate), smooth: true, itemStyle: { color: '#e6a23c' }, lineStyle: { width: 3 } },
      { name: '革兰阳性菌', type: 'bar', data: trend.map(t => t.gramPositiveCount), itemStyle: { color: '#67c23a' } },
      { name: '革兰阴性菌', type: 'bar', data: trend.map(t => t.gramNegativeCount), itemStyle: { color: '#409eff' } },
      { name: '真菌', type: 'bar', data: trend.map(t => t.fungiCount), itemStyle: { color: '#e6a23c' } },
      { name: '高风险细菌', type: 'line', data: trend.map(t => t.highRiskCount), smooth: true, itemStyle: { color: '#f56c6c' }, lineStyle: { width: 2, type: 'dashed' } }
    ]
  })
}

function renderRankChart() {
  if (!rankChartRef.value) return
  if (!rankChart) rankChart = echarts.init(rankChartRef.value)
  const ranks = bacteriaRanks.value || []
  const names = ranks.map(r => r.bacteriaName).reverse()
  const values = ranks.map(r => r.detectCount).reverse()
  const colors = ranks.map(r => r.isHighRisk === 1 ? '#f56c6c' : '#409eff').reverse()
  rankChart.setOption({
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      confine: true,
      formatter: function(params) {
        const p = params[0]
        const idx = ranks.length - 1 - p.dataIndex
        const r = ranks[idx]
        return `${r.bacteriaName}<br/>检出次数: ${r.detectCount}<br/>检出患者: ${r.patientCount}人<br/>分类: ${r.bacteriaClassName || '其他'}${r.isHighRisk === 1 ? '<br/><span style=\'color:#f56c6c\'>高风险细菌</span>' : ''}`
      }
    },
    grid: { left: '3%', right: '8%', bottom: '3%', containLabel: true },
    xAxis: { type: 'value', axisLabel: { color: '#666' } },
    yAxis: { type: 'category', data: names, axisLabel: { color: '#333', fontSize: 11 } },
    series: [{
      type: 'bar',
      data: values.map((v, i) => ({ value: v, itemStyle: { color: colors[i] } })),
      label: { show: true, position: 'right', color: '#666', fontSize: 11 },
      barWidth: '60%'
    }]
  })
}

function renderClassChart() {
  if (!classChartRef.value) return
  if (!classChart) classChart = echarts.init(classChartRef.value)
  const dist = overview.classDistribution || []
  classChart.setOption({
    tooltip: { trigger: 'item', confine: true, formatter: '{b}: {c}次 ({d}%)' },
    legend: { orient: 'vertical', right: 10, top: 'center' },
    series: [{
      type: 'pie',
      radius: ['40%', '70%'],
      center: ['40%', '50%'],
      avoidLabelOverlap: false,
      itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
      label: { show: false },
      data: dist.map(d => ({ name: d.name, value: d.value, itemStyle: { color: d.color } }))
    }]
  })
}

function renderSpecimenChart() {
  if (!specimenChartRef.value) return
  if (!specimenChart) specimenChart = echarts.init(specimenChartRef.value)
  const dist = overview.specimenDistribution || []
  specimenChart.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, confine: true },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', data: dist.map(d => d.name), axisLabel: { color: '#666', rotate: 30, fontSize: 11 } },
    yAxis: { type: 'value', axisLabel: { color: '#666' } },
    series: [{
      type: 'bar',
      data: dist.map(d => d.value),
      itemStyle: { color: '#909399', borderRadius: [4, 4, 0, 0] },
      label: { show: true, position: 'top', color: '#666', fontSize: 11 },
      barWidth: '50%'
    }]
  })
}

function goPatients() {
  router.push('/page/abx-mdro-patients')
}

function goConfig() {
  router.push('/page/abx-mdro-config')
}

onMounted(() => {
  initDefaultDate()
  loadDepartments()
  loadData()
  window.addEventListener('resize', () => {
    trendChart && trendChart.resize()
    rankChart && rankChart.resize()
    classChart && classChart.resize()
    specimenChart && specimenChart.resize()
  })
})
</script>

<style scoped>
.mdro-overview {
  padding: 16px;
  background: #f5f7fa;
  min-height: 100vh;
}

.filter-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  flex-wrap: wrap;
  gap: 12px;
}

.filter-left, .filter-right {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
}

.metric-cards {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 12px;
  margin-bottom: 12px;
}

.metric-cards.small {
  grid-template-columns: repeat(4, 1fr);
}

.metric-card {
  background: #fff;
  border-radius: 8px;
  padding: 16px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.06);
  border-left: 4px solid #409eff;
}

.metric-card.success { border-left-color: #67c23a; }
.metric-card.warning { border-left-color: #e6a23c; }
.metric-card.danger { border-left-color: #f56c6c; }
.metric-card.info { border-left-color: #909399; }
.metric-card.gram-positive { border-left-color: #67c23a; }
.metric-card.gram-negative { border-left-color: #409eff; }
.metric-card.fungi { border-left-color: #e6a23c; }
.metric-card.other { border-left-color: #909399; }

.metric-label {
  font-size: 13px;
  color: #909399;
  margin-bottom: 8px;
}

.metric-value {
  font-size: 28px;
  font-weight: 600;
  color: #303133;
  line-height: 1.2;
}

.metric-value.small {
  font-size: 22px;
}

.metric-unit {
  font-size: 13px;
  color: #909399;
  font-weight: 400;
  margin-left: 4px;
}

.metric-target {
  font-size: 12px;
  color: #67c23a;
  margin-top: 6px;
}

.metric-sub {
  font-size: 12px;
  color: #c0c4cc;
  margin-top: 4px;
}

.chart-card {
  background: #fff;
  border-radius: 8px;
  padding: 16px;
  margin-bottom: 12px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.06);
}

.row-cards {
  display: flex;
  gap: 12px;
  margin-bottom: 12px;
}

.flex-2 { flex: 2; }
.flex-1 { flex: 1; }

.block-title {
  display: flex;
  align-items: center;
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 12px;
}

.block-title .dot {
  width: 4px;
  height: 16px;
  background: #409eff;
  border-radius: 2px;
  margin-right: 8px;
}

.chart-container {
  width: 100%;
  height: 300px;
}

.chart-container.tall {
  height: 500px;
}

@media (max-width: 1200px) {
  .metric-cards {
    grid-template-columns: repeat(3, 1fr);
  }
  .metric-cards.small {
    grid-template-columns: repeat(2, 1fr);
  }
  .row-cards {
    flex-direction: column;
  }
}
</style>
