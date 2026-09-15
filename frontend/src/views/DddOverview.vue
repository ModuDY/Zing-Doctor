<template>
  <div class="ddd-overview abx-theme">
    <!-- 顶部筛选栏 -->
    <div class="filter-bar">
      <div class="filter-left">
        <el-select v-model="selectedDepartCode" placeholder="选择科室" style="width: 180px" popper-class="abx-popper" @change="loadData">
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
          popper-class="abx-popper"
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
          <el-icon><Setting /></el-icon> DDD配置
        </el-button>
      </div>
    </div>

    <!-- 核心指标卡片 -->
    <div class="metric-cards">
      <div class="metric-card" :class="{达标: overview.usageRate达标}">
        <div class="metric-label">抗菌药物使用率</div>
        <div class="metric-value">
          {{ overview.usageRate ?? '--' }}
          <span class="metric-unit">%</span>
        </div>
        <div class="metric-target">目标 ≤ {{ overview.usageRateTarget }}%</div>
        <div class="metric-sub">
          {{ overview.usedPatientCount ?? 0 }} / {{ overview.totalPatientCount ?? 0 }} 人
        </div>
      </div>

      <div class="metric-card" :class="{达标: overview.useDensity达标}">
        <div class="metric-label">抗菌药物使用强度</div>
        <div class="metric-value">
          {{ overview.useDensity ?? '--' }}
          <span class="metric-unit">DDDs/100床日</span>
        </div>
        <div class="metric-target">目标 ≤ {{ overview.useDensityTarget }}</div>
        <div class="metric-sub">总 DDDs: {{ overview.totalDdds ?? 0 }}</div>
      </div>

      <div class="metric-card special">
        <div class="metric-label">特殊使用级强度</div>
        <div class="metric-value">
          {{ overview.specialUseDensity ?? '--' }}
          <span class="metric-unit">DDDs/100床日</span>
        </div>
        <div class="metric-target">占比 {{ overview.specialRatio ?? 0 }}%</div>
        <div class="metric-sub">特殊级 DDDs: {{ overview.specialDdds ?? 0 }}</div>
      </div>

      <div class="metric-card info">
        <div class="metric-label">统计概览</div>
        <div class="metric-value small">
          {{ overview.drugKindCount ?? 0 }}
          <span class="metric-unit">种药物</span>
        </div>
        <div class="metric-target">总床日: {{ overview.totalBedDays ?? 0 }}</div>
        <div class="metric-sub">统计周期: {{ dateRangeText }}</div>
      </div>
    </div>

    <!-- 月度趋势图 -->
    <div class="chart-card">
      <div class="block-title">
        <span class="dot"></span>
        <span>月度趋势（使用率 & 使用强度）</span>
      </div>
      <div ref="trendChartRef" class="chart-container"></div>
    </div>

    <!-- 药品排名 + 分类占比 -->
    <div class="row-cards">
      <div class="chart-card flex-2">
        <div class="block-title">
          <span class="dot"></span>
          <span>药品消耗排名 TOP20（按 DDDs）</span>
        </div>
        <div ref="rankChartRef" class="chart-container tall"></div>
      </div>

      <div class="chart-card flex-1">
        <div class="block-title">
          <span class="dot"></span>
          <span>药品分类占比</span>
        </div>
        <div ref="classChartRef" class="chart-container"></div>

        <div class="block-title" style="margin-top: 16px;">
          <span class="dot"></span>
          <span>管理级别占比</span>
        </div>
        <div ref="levelChartRef" class="chart-container"></div>
      </div>
    </div>

    <!-- 药品排名表格 -->
    <div class="chart-card">
      <div class="block-title">
        <span class="dot"></span>
        <span>药品消耗明细</span>
      </div>
      <el-table :data="drugRanks" stripe size="small" max-height="400">
        <el-table-column prop="rank" label="排名" width="60" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.rank <= 3" :type="row.rank === 1 ? 'danger' : row.rank === 2 ? 'warning' : 'success'" size="small">
              {{ row.rank }}
            </el-tag>
            <span v-else>{{ row.rank }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="drugName" label="药品名称" min-width="160" />
        <el-table-column prop="drugClass" label="分类" width="120" />
        <el-table-column prop="manageLevel" label="管理级别" width="100">
          <template #default="{ row }">
            <el-tag :type="row.manageLevel === '特殊' ? 'danger' : row.manageLevel === '限制' ? 'warning' : 'success'" size="small">
              {{ row.manageLevel }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="dddValue" label="DDD值" width="90" align="right" />
        <el-table-column prop="totalDose" label="总消耗量(g)" width="120" align="right" />
        <el-table-column prop="totalDdds" label="总DDDs" width="110" align="right">
          <template #default="{ row }">
            <span class="highlight">{{ row.totalDdds }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="ratio" label="占比(%)" width="90" align="right" />
        <el-table-column prop="patientCount" label="使用患者数" width="100" align="center" />
        <el-table-column prop="adviceCount" label="医嘱条数" width="90" align="center" />
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, nextTick, computed } from 'vue'
import { useRouter } from 'vue-router'
import { Refresh, User, Setting } from '@element-plus/icons-vue'
import request from '../api/request'
import * as echarts from 'echarts'
import '../styles/abx-theme.css'

const router = useRouter()
const loading = ref(false)
const overview = reactive({})
const drugRanks = ref([])
const dateRange = ref([])
const departments = ref([])
const selectedDepartCode = ref('20070131')

const trendChartRef = ref(null)
const rankChartRef = ref(null)
const classChartRef = ref(null)
const levelChartRef = ref(null)

let trendChart = null
let rankChart = null
let classChart = null
let levelChart = null

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
    const [overviewRes, rankRes] = await Promise.all([
      request.get('/antibiotic/ddd/overview', { params }),
      request.get('/antibiotic/ddd/drug-rank', { params })
    ])
    Object.assign(overview, overviewRes || {})
    drugRanks.value = rankRes || []
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
    const res = await request.get('/antibiotic/ddd/departments')
    departments.value = res || []
  } catch (e) {
    console.error('加载科室列表失败', e)
  }
}

function renderCharts() {
  renderTrendChart()
  renderRankChart()
  renderClassChart()
  renderLevelChart()
}

function renderTrendChart() {
  if (!trendChartRef.value) return
  if (!trendChart) trendChart = echarts.init(trendChartRef.value)
  const trend = overview.monthlyTrend || []
  trendChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['使用率(%)', '使用强度(DDDs)'], top: 0 },
    grid: { left: 50, right: 50, top: 40, bottom: 30 },
    xAxis: { type: 'category', data: trend.map(t => t.month), axisLabel: { color: '#78716c' } },
    yAxis: [
      { type: 'value', name: '使用率(%)', axisLabel: { color: '#78716c' }, splitLine: { lineStyle: { color: '#f5f5f4' } } },
      { type: 'value', name: '使用强度', axisLabel: { color: '#78716c' }, splitLine: { show: false } }
    ],
    series: [
      {
        name: '使用率(%)', type: 'line', smooth: true,
        data: trend.map(t => t.usageRate),
        itemStyle: { color: '#14b8a6' },
        areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(20,184,166,0.18)' },
          { offset: 1, color: 'rgba(20,184,166,0.01)' }
        ]) },
        markLine: { silent: true, data: [{ yAxis: 60, name: '目标线', lineStyle: { color: '#16a34a', type: 'dashed' } }] }
      },
      {
        name: '使用强度(DDDs)', type: 'line', smooth: true, yAxisIndex: 1,
        data: trend.map(t => t.useDensity),
        itemStyle: { color: '#dc2626' },
        markLine: { silent: true, data: [{ yAxis: 40, name: '目标线', lineStyle: { color: '#d97706', type: 'dashed' } }] }
      }
    ]
  })
}

function renderRankChart() {
  if (!rankChartRef.value) return
  if (!rankChart) rankChart = echarts.init(rankChartRef.value)
  const top10 = (drugRanks.value || []).slice(0, 10).reverse()
  rankChart.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 120, right: 40, top: 20, bottom: 30 },
    xAxis: { type: 'value', name: 'DDDs', axisLabel: { color: '#78716c' } },
    yAxis: { type: 'category', data: top10.map(d => d.drugName), axisLabel: { color: '#78716c', fontSize: 12 } },
    series: [{
      type: 'bar', data: top10.map(d => ({
        value: d.totalDdds,
        itemStyle: {
          color: d.manageLevel === '特殊' ? '#dc2626' : d.manageLevel === '限制' ? '#d97706' : '#16a34a',
          borderRadius: [0, 4, 4, 0]
        }
      })),
      label: { show: true, position: 'right', color: '#78716c', fontSize: 11 }
    }]
  })
}

function renderClassChart() {
  if (!classChartRef.value) return
  if (!classChart) classChart = echarts.init(classChartRef.value)
  const data = (overview.classRatios || []).map(c => ({ name: c.drugClass, value: c.ddds }))
  classChart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} DDDs ({d}%)' },
    legend: { type: 'scroll', bottom: 0, textStyle: { fontSize: 11 } },
    series: [{
      type: 'pie', radius: ['40%', '70%'], center: ['50%', '45%'],
      avoidLabelOverlap: true,
      itemStyle: { borderRadius: 4, borderColor: '#fff', borderWidth: 2 },
      label: { show: false },
      data
    }]
  })
}

function renderLevelChart() {
  if (!levelChartRef.value) return
  if (!levelChart) levelChart = echarts.init(levelChartRef.value)
  const data = (overview.levelRatios || []).map(l => ({ name: l.manageLevel, value: l.ddds }))
  const colors = { '非限制': '#16a34a', '限制': '#d97706', '特殊': '#dc2626' }
  levelChart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} DDDs ({d}%)' },
    legend: { bottom: 0, textStyle: { fontSize: 11 } },
    series: [{
      type: 'pie', radius: ['40%', '70%'], center: ['50%', '45%'],
      itemStyle: { borderRadius: 4, borderColor: '#fff', borderWidth: 2 },
      label: { show: true, formatter: '{b}\n{d}%', fontSize: 11 },
      data: data.map(d => ({ ...d, itemStyle: { color: colors[d.name] || '#78716c' } }))
    }]
  })
}

function goPatients() {
  router.push('/page/abx-ddd-patients')
}

function goConfig() {
  router.push('/page/abx-ddd-config')
}

onMounted(async () => {
  initDefaultDate()
  await loadDepartments()
  loadData()
  window.addEventListener('resize', () => {
    trendChart?.resize()
    rankChart?.resize()
    classChart?.resize()
    levelChart?.resize()
  })
})
</script>

<style scoped>
.ddd-overview {
  padding: 16px;
  background: #fafaf9;
  min-height: 100vh;
}

.filter-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  padding: 12px 16px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(28,25,23,0.04);
}

.filter-left, .filter-right {
  display: flex;
  gap: 12px;
  align-items: center;
}

.metric-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 16px;
}

.metric-card {
  background: #fff;
  border-radius: 8px;
  padding: 20px;
  box-shadow: 0 1px 4px rgba(28,25,23,0.04);
  border-left: 4px solid #78716c;
  transition: transform 0.2s;
}

.metric-card:hover {
  transform: translateY(-2px);
}

.metric-card.达标 {
  border-left-color: #16a34a;
}

.metric-card:not(.达标):not(.special):not(.info) {
  border-left-color: #dc2626;
}

.metric-card.special {
  border-left-color: #dc2626;
}

.metric-card.info {
  border-left-color: #0d9488;
}

.metric-label {
  font-size: 14px;
  color: #44403c;
  margin-bottom: 8px;
}

.metric-value {
  font-size: 32px;
  font-weight: 700;
  color: #292524;
  line-height: 1.2;
}

.metric-value.small {
  font-size: 24px;
}

.metric-unit {
  font-size: 13px;
  font-weight: 400;
  color: #78716c;
  margin-left: 4px;
}

.metric-target {
  font-size: 12px;
  color: #78716c;
  margin-top: 6px;
}

.metric-sub {
  font-size: 12px;
  color: #44403c;
  margin-top: 4px;
}

.chart-card {
  background: #fff;
  border-radius: 8px;
  padding: 16px 20px;
  margin-bottom: 16px;
  box-shadow: 0 1px 4px rgba(28,25,23,0.04);
}

.block-title {
  display: flex;
  align-items: center;
  font-size: 15px;
  font-weight: 600;
  color: #292524;
  margin-bottom: 12px;
}

.dot {
  width: 4px;
  height: 16px;
  background: linear-gradient(180deg, #0d9488, #2dd4bf);
  border-radius: 2px;
  margin-right: 8px;
}

.chart-container {
  width: 100%;
  height: 300px;
}

.chart-container.tall {
  height: 400px;
}

.row-cards {
  display: flex;
  gap: 16px;
  margin-bottom: 16px;
}

.flex-2 {
  flex: 2;
}

.flex-1 {
  flex: 1;
}

.highlight {
  color: #0d9488;
  font-weight: 600;
}

@media (max-width: 1200px) {
  .metric-cards {
    grid-template-columns: repeat(2, 1fr);
  }
  .row-cards {
    flex-direction: column;
  }
}
</style>
