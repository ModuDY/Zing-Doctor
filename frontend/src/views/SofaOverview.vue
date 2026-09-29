<template>
  <div class="sofa-overview">
    <!-- 顶部筛选 -->
    <div class="filter-bar">
      <span class="page-title">SOFA 评分总览</span>
      <div class="filters">
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          value-format="YYYY-MM-DD"
          :clearable="false"
          style="width: 250px"
        />
        <el-button size="default" @click="quickRange(7)">近7天</el-button>
        <el-button size="default" @click="quickRange(30)">近30天</el-button>
        <el-button type="primary" :loading="loading" @click="loadData">
          <el-icon><Search /></el-icon> 查询
        </el-button>
        <span v-if="departName" class="depart-tag">科室：{{ departName }}</span>
      </div>
    </div>

    <el-empty v-if="departCodeInvalid" :description="departCodeInvalidText" />

    <template v-else>
      <!-- 统计卡片：口径是「患者」不是「记录」 -->
      <div class="stat-cards">
        <div class="stat-card">
          <div class="stat-label">评分患者数</div>
          <div class="stat-value">{{ summary.patientCount || 0 }}</div>
          <div class="stat-sub">共 {{ summary.recordCount || 0 }} 条评分记录</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">平均总分</div>
          <div class="stat-value">{{ fmt1(summary.avgScore) }}</div>
          <div class="stat-sub">按患者最新评分（0~24）</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">高危患者数</div>
          <div class="stat-value" :class="{ 'is-danger': (summary.highRiskCount || 0) > 0 }">
            {{ summary.highRiskCount || 0 }}
          </div>
          <div class="stat-sub">最新总分 ≥ 10 分</div>
        </div>
        <div class="stat-card stat-card-warning">
          <div class="stat-label">ΔSOFA 恶化预警</div>
          <div class="stat-value">{{ summary.worsenedCount || 0 }}</div>
          <div class="stat-sub">较上次升高 ≥ 2 分</div>
        </div>
      </div>

      <!-- 趋势 + 分布 -->
      <div class="chart-row">
        <div class="chart-box">
          <div class="chart-title">总分趋势（按天）</div>
          <div v-show="trend.length" ref="trendChartRef" class="chart-container"></div>
          <div v-if="!trend.length && !loading" class="chart-empty">当前时间范围内暂无评分数据</div>
        </div>
        <div class="chart-box">
          <div class="chart-title">最新总分分布（按患者）</div>
          <div v-show="distTotal > 0" ref="distChartRef" class="chart-container"></div>
          <div v-if="!distTotal && !loading" class="chart-empty">当前时间范围内暂无评分数据</div>
        </div>
      </div>

      <!-- ΔSOFA 恶化患者 -->
      <div v-if="worsenedList.length" class="table-box">
        <div class="table-title is-warn">
          ΔSOFA 恶化患者（较上次升高 ≥ 2，提示器官功能恶化）
          <span class="count">{{ worsenedList.length }}</span>
        </div>
        <el-table :data="worsenedList" v-loading="loading" border size="small" @row-click="goToScorePage">
          <el-table-column prop="bedNo" label="床号" width="80" align="center">
            <template #default="{ row }">{{ row.bedNo || '—' }}</template>
          </el-table-column>
          <el-table-column prop="patientName" label="姓名" width="90" />
          <el-table-column prop="inHospitalNo" label="住院号" width="130" />
          <el-table-column prop="totalScore" label="最新总分" width="90" align="center">
            <template #default="{ row }"><span class="score-danger">{{ row.totalScore }}</span></template>
          </el-table-column>
          <el-table-column label="ΔSOFA" width="80" align="center">
            <template #default="{ row }"><span class="score-danger">+{{ row.deltaSofa }}</span></template>
          </el-table-column>
          <el-table-column label="评分时间" min-width="150">
            <template #default="{ row }">{{ fmtTime(row.scoreTime) }}</template>
          </el-table-column>
        </el-table>
      </div>

      <!-- 评分记录 -->
      <div class="table-box">
        <div class="table-title">
          评分记录 <span class="count">{{ records.length }}</span>
          <span class="title-hint">点击行查看该患者评分</span>
        </div>
        <el-table :data="pagedRecords" v-loading="loading" border size="small" @row-click="goToScorePage">
          <el-table-column label="评分时间" width="150">
            <template #default="{ row }">{{ fmtTime(row.scoreTime) }}</template>
          </el-table-column>
          <el-table-column prop="bedNo" label="床号" width="80" align="center">
            <template #default="{ row }">{{ row.bedNo || '—' }}</template>
          </el-table-column>
          <el-table-column prop="patientName" label="姓名" width="90" />
          <el-table-column prop="inHospitalNo" label="住院号" width="130" />
          <el-table-column prop="totalScore" label="总分" width="70" align="center">
            <template #default="{ row }">
              <span :class="scoreClass(row.totalScore)">{{ row.totalScore }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="respScore" label="呼吸" width="60" align="center" />
          <el-table-column prop="coagScore" label="凝血" width="60" align="center" />
          <el-table-column prop="liverScore" label="肝" width="50" align="center" />
          <el-table-column prop="cardioScore" label="循环" width="60" align="center" />
          <el-table-column prop="neuroScore" label="神经" width="60" align="center" />
          <el-table-column prop="renalScore" label="肾" width="50" align="center" />
          <el-table-column label="Δ" width="70" align="center">
            <template #default="{ row }">
              <span :class="deltaClass(row.deltaSofa)">{{ deltaText(row.deltaSofa) }}</span>
            </template>
          </el-table-column>
        </el-table>
        <div v-if="records.length > pageSize" class="pager">
          <el-pagination
            v-model:current-page="page"
            :page-size="pageSize"
            :total="records.length"
            layout="prev, pager, next, total"
            small
          />
        </div>
        <el-empty v-if="!loading && !records.length" description="暂无评分记录" :image-size="80" />
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Search } from '@element-plus/icons-vue'
import { fetchSofaOverview } from '../api/sofa'
import { appendExternalContext } from '../utils/external'
import { currentDepart } from '../utils/departContext'
import * as echarts from '../utils/echarts'

const route = useRoute()
const router = useRouter()

// ICU 外链模板未被替换的占位符（如 ${departCode}）会原样带进 query，必须按“无效参数”处理，
// 否则会以一个不存在的科室去查询，表面“成功”但永远返回空数据。
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
const loading = ref(false)
const records = ref([])
const worsenedList = ref([])
const trend = ref([])
const summary = reactive({
  patientCount: 0,
  recordCount: 0,
  avgScore: 0,
  highRiskCount: 0,
  worsenedCount: 0,
  scoreDistribution: {}
})

const trendChartRef = ref(null)
const distChartRef = ref(null)
let trendChart = null
let distChart = null

const page = ref(1)
const pageSize = 20
const pagedRecords = computed(() => {
  const start = (page.value - 1) * pageSize
  return records.value.slice(start, start + pageSize)
})
const distTotal = computed(() => {
  const d = summary.scoreDistribution || {}
  return Object.values(d).reduce((a, b) => a + (Number(b) || 0), 0)
})

/** 本地时区日期 yyyy-MM-dd：不能用 toISOString()（UTC 会整体前移一天，导致当天记录被排除） */
function fmtDate(d) {
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

function quickRange(days) {
  const today = new Date()
  dateRange.value = [fmtDate(new Date(today.getTime() - (days - 1) * 86400000)), fmtDate(today)]
  loadData()
}

onMounted(() => {
  const today = new Date()
  dateRange.value = [fmtDate(new Date(today.getTime() - 6 * 86400000)), fmtDate(today)]
  window.addEventListener('resize', handleResize)
  if (!departCodeInvalid.value) loadData()
})

// 非外链场景下，侧边栏切换科室时自动重新加载
watch(() => currentDepart.departCode, (code) => {
  if (!externalDepartCode && code) {
    departCode.value = code
    if (!departCodeInvalid.value) loadData()
  }
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  if (trendChart) { trendChart.dispose(); trendChart = null }
  if (distChart) { distChart.dispose(); distChart = null }
})

async function loadData() {
  if (!dateRange.value || dateRange.value.length < 2) {
    return
  }
  loading.value = true
  try {
    // request 响应拦截器已把后端 Result 信封解包，这里就是 Result.data 本身
    const data = (await fetchSofaOverview(
      departCode.value,
      dateRange.value[0] + ' 00:00:00',
      dateRange.value[1] + ' 23:59:59'
    )) || {}
    summary.patientCount = data.patientCount || 0
    summary.recordCount = data.recordCount || data.totalCount || 0
    summary.avgScore = data.avgScore || 0
    summary.highRiskCount = data.highRiskCount || 0
    summary.worsenedCount = data.worsenedCount || 0
    summary.scoreDistribution = data.scoreDistribution || {}
    trend.value = data.trend || []
    worsenedList.value = data.worsenedList || []
    records.value = data.records || []
    page.value = 1
    await nextTick()
    renderTrendChart()
    renderDistChart()
  } catch (e) {
    // 全局拦截器已统一提示，这里只留诊断日志
    console.warn('SOFA 总览加载失败: ', e && e.message)
  } finally {
    loading.value = false
  }
}

/**
 * 趋势：按天画平均总分与当日最高。
 * SOFA 的临床价值在动态变化，静态分布看不出走向，所以这条线是总览的主视图。
 */
function renderTrendChart() {
  if (!trendChartRef.value) return
  if (!trendChart) trendChart = echarts.init(trendChartRef.value)
  const list = trend.value || []
  if (!list.length) {
    trendChart.clear()
    return
  }
  trendChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['平均总分', '当日最高'], top: 0, textStyle: { color: '#57534e', fontSize: 12 } },
    grid: { left: 45, right: 16, top: 34, bottom: 26 },
    xAxis: {
      type: 'category',
      data: list.map((t) => t.date),
      axisLabel: { color: '#78716c', fontSize: 11 },
      axisLine: { lineStyle: { color: '#e7e5e4' } }
    },
    yAxis: {
      type: 'value',
      name: '分',
      max: 24,
      nameTextStyle: { color: '#78716c' },
      axisLabel: { color: '#78716c', fontSize: 11 },
      splitLine: { lineStyle: { color: '#f5f5f4' } }
    },
    series: [
      {
        name: '平均总分',
        type: 'line',
        smooth: true,
        data: list.map((t) => t.avgScore),
        itemStyle: { color: '#ea580c' },
        areaStyle: { color: 'rgba(234, 88, 12, 0.10)' }
      },
      {
        name: '当日最高',
        type: 'line',
        smooth: true,
        data: list.map((t) => t.maxScore),
        itemStyle: { color: '#dc2626' },
        lineStyle: { type: 'dashed', width: 1.5 }
      }
    ]
  }, true)
  trendChart.resize()
}

function renderDistChart() {
  if (!distChartRef.value) return
  if (!distChart) distChart = echarts.init(distChartRef.value)
  const dist = summary.scoreDistribution || {}
  const categories = Object.keys(dist)
  const values = categories.map((k) => Number(dist[k]) || 0)
  if (!categories.length) {
    distChart.clear()
    return
  }
  distChart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 45, right: 16, top: 24, bottom: 26 },
    xAxis: {
      type: 'category',
      data: categories,
      axisLabel: { color: '#78716c', fontSize: 11 },
      axisLine: { lineStyle: { color: '#e7e5e4' } }
    },
    yAxis: {
      type: 'value',
      name: '患者数',
      nameTextStyle: { color: '#78716c' },
      axisLabel: { color: '#78716c', fontSize: 11 },
      splitLine: { lineStyle: { color: '#f5f5f4' } }
    },
    series: [{
      type: 'bar',
      data: values,
      barMaxWidth: 36,
      itemStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: '#fb923c' },
          { offset: 1, color: '#ea580c' }
        ]),
        borderRadius: [4, 4, 0, 0]
      },
      label: { show: true, position: 'top', color: '#57534e', fontSize: 12 }
    }]
  }, true)
  distChart.resize()
}

/** 窗口尺寸变化时自适应，避免图表被拉伸变形 */
function handleResize() {
  if (trendChart) trendChart.resize()
  if (distChart) distChart.resize()
}

function fmt1(v) {
  const n = Number(v)
  return isNaN(n) ? '0.0' : n.toFixed(1)
}

function fmtTime(t) {
  if (!t) return '—'
  let s = String(t)
  if (s.includes('T')) s = s.replace('T', ' ')
  return s.length > 16 ? s.slice(0, 16) : s
}

/** 总分分级：≥10 高危、≥6 警戒，与评分页保持同一口径 */
function scoreClass(s) {
  if (s >= 10) return 'score-danger'
  if (s >= 6) return 'score-warning'
  return 'score-normal'
}

function deltaText(d) {
  if (d === null || d === undefined) return '—'
  return (d > 0 ? '+' : '') + d
}

function deltaClass(d) {
  if (d > 0) return 'score-danger'
  if (d < 0) return 'score-down'
  return ''
}

function goToScorePage(row) {
  const url = `/page/sofa-score?inHospitalNo=${encodeURIComponent(row.inHospitalNo || '')}` +
    `&patientName=${encodeURIComponent(row.patientName || '')}` +
    `&departCode=${encodeURIComponent(departCode.value)}` +
    `&recordId=${encodeURIComponent(row.id || '')}`
  router.push(appendExternalContext(url))
}
</script>

<style scoped>
.sofa-overview {
  padding: 16px;
  background: #fafaf9;
  min-height: 100vh;
}
.filter-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  background: #fff;
  padding: 14px 20px;
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(28, 25, 23, 0.05);
}
.page-title {
  font-size: 18px;
  font-weight: 600;
  color: #292524;
}
.filters {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.depart-tag {
  font-size: 13px;
  color: #57534e;
  background: #f5f5f4;
  padding: 4px 10px;
  border-radius: 12px;
}

.stat-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
  margin-bottom: 16px;
}
.stat-card {
  background: #fff;
  border-radius: 8px;
  padding: 14px 18px;
  box-shadow: 0 1px 4px rgba(28, 25, 23, 0.05);
}
.stat-card-warning {
  border-left: 4px solid #dc2626;
}
.stat-label {
  font-size: 12px;
  color: #78716c;
}
.stat-value {
  font-size: 30px;
  font-weight: 700;
  color: #292524;
  line-height: 1.2;
}
.stat-value.is-danger {
  color: #dc2626;
}
.stat-sub {
  font-size: 12px;
  color: #a8a29e;
  margin-top: 2px;
}

.chart-row {
  display: grid;
  grid-template-columns: 3fr 2fr;
  gap: 14px;
  margin-bottom: 16px;
}
.chart-box {
  background: #fff;
  border-radius: 8px;
  padding: 14px 16px;
  box-shadow: 0 1px 4px rgba(28, 25, 23, 0.05);
}
.chart-title {
  font-size: 14px;
  font-weight: 600;
  color: #292524;
  margin-bottom: 8px;
}
.chart-container {
  width: 100%;
  height: 260px;
}
.chart-empty {
  height: 260px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #a8a29e;
  font-size: 13px;
}

.table-box {
  background: #fff;
  border-radius: 8px;
  padding: 14px 16px;
  margin-bottom: 16px;
  box-shadow: 0 1px 4px rgba(28, 25, 23, 0.05);
}
.table-title {
  font-size: 14px;
  font-weight: 600;
  color: #292524;
  margin-bottom: 10px;
  display: flex;
  align-items: center;
  gap: 8px;
}
.table-title.is-warn {
  color: #dc2626;
}
.title-hint {
  font-size: 12px;
  font-weight: 400;
  color: #a8a29e;
}
.count {
  background: rgba(234, 88, 12, 0.10);
  color: #ea580c;
  padding: 1px 8px;
  border-radius: 10px;
  font-size: 12px;
  font-weight: 400;
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 10px;
}

.score-danger {
  color: #dc2626;
  font-weight: 600;
}
.score-warning {
  color: #d97706;
  font-weight: 600;
}
.score-normal {
  color: #16a34a;
}
.score-down {
  color: #16a34a;
}

.el-table {
  cursor: pointer;
}

@media (max-width: 1200px) {
  .stat-cards { grid-template-columns: repeat(2, 1fr); }
  .chart-row { grid-template-columns: 1fr; }
}
</style>
