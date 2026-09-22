<template>
  <div class="ards-monitor">
    <!-- 顶部筛选 -->
    <div class="filter-bar">
      <span class="page-title">ARDS 监测</span>
      <div class="filters">
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          value-format="YYYY-MM-DD"
          :clearable="false"
          style="width: 260px"
        />
        <el-select v-model="gradeFilter" placeholder="分级筛选" style="width: 120px" @change="loadData">
          <el-option label="全部" value="" />
          <el-option label="轻度" value="轻度" />
          <el-option label="中度" value="中度" />
          <el-option label="重度" value="重度" />
          <el-option label="未知" value="未知" />
        </el-select>
        <el-button type="primary" @click="loadData">
          <el-icon><Search /></el-icon> 查询
        </el-button>
        <span v-if="departName" class="depart-tag">科室：{{ departName }}</span>
      </div>
    </div>

    <!-- 无权限提示 -->
    <el-empty v-if="!departCode" description="缺少科室权限参数，请通过外链访问" />

    <template v-else>
      <!-- 统计卡片 -->
      <div class="stat-cards">
        <div class="stat-card">
          <div class="stat-label">ARDS 患者</div>
          <div class="stat-value">{{ summary.total }}</div>
          <div class="stat-sub">当前在科</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">分级分布</div>
          <div class="stat-value-grade">
            <span class="grade-mild">{{ summary.mild }}</span>
            <span class="divider">/</span>
            <span class="grade-moderate">{{ summary.moderate }}</span>
            <span class="divider">/</span>
            <span class="grade-severe">{{ summary.severe }}</span>
          </div>
          <div class="stat-sub">轻 / 中 / 重</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">肺保护性通气依从率</div>
          <div class="stat-value">{{ summary.vt_compliance_rate }}%</div>
          <div class="stat-sub">潮气量≤6ml/kg（{{ summary.vent_data_count }}人有数据）</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">平均氧合指数</div>
          <div class="stat-value">{{ summary.avg_oxygenation_index || '—' }}</div>
          <div class="stat-sub">最新血气</div>
        </div>
      </div>

      <!-- 图表区 -->
      <div class="chart-row">
        <div class="chart-box">
          <div class="chart-title">ARDS 分级分布</div>
          <div ref="gradeChartRef" class="chart-container"></div>
        </div>
        <div class="chart-box">
          <div class="chart-title">肺保护性通气依从率</div>
          <div ref="complianceChartRef" class="chart-container"></div>
        </div>
      </div>

      <!-- 患者列表 -->
      <div class="table-box">
        <div class="table-title">患者列表（点击行展开详情）</div>
        <el-table
          v-loading="loading"
          :data="patients"
          row-key="patient_id"
          :expand-row-keys="expandRowKeys"
          class="ards-table"
          @row-click="handleRowClick"
        >
          <el-table-column type="expand">
            <template #default="{ row }">
              <div v-loading="row.detailLoading" class="detail-panel">
                <div class="detail-section">
                  <div class="detail-title">呼吸机参数趋势（在科期间）</div>
                  <div class="detail-tabs">
                    <el-radio-group v-model="row.ventMetric" size="small" @change="renderVentChart(row)">
                      <el-radio-button label="vt_ml">潮气量(ml)</el-radio-button>
                      <el-radio-button label="peep">PEEP</el-radio-button>
                      <el-radio-button label="fio2">FiO2(%)</el-radio-button>
                      <el-radio-button label="rr">呼吸频率</el-radio-button>
                      <el-radio-button label="mv">分钟通气量</el-radio-button>
                    </el-radio-group>
                  </div>
                  <div :ref="el => setVentChartRef(row.patient_id, el)" class="detail-chart"></div>
                </div>
                <div class="detail-section">
                  <div class="detail-title">氧合指数趋势（血气时间点）</div>
                  <div :ref="el => setOiChartRef(row.patient_id, el)" class="detail-chart"></div>
                </div>
                <div class="detail-section">
                  <div class="detail-title">肺保护性通气达标情况</div>
                  <el-table :data="buildComplianceRows(row)" size="small" border>
                    <el-table-column prop="metric" label="指标" width="160" />
                    <el-table-column prop="current" label="当前值" width="140" />
                    <el-table-column prop="target" label="目标值" width="140" />
                    <el-table-column prop="status" label="状态" width="120">
                      <template #default="{ row: r }">
                        <el-tag :type="r.status === '达标' ? 'success' : r.status === '无数据' ? 'info' : 'danger'" size="small">
                          {{ r.status }}
                        </el-tag>
                      </template>
                    </el-table-column>
                  </el-table>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="bed_code" label="床号" width="70" />
          <el-table-column prop="patient_name" label="姓名" width="80" />
          <el-table-column prop="in_hospital_no" label="住院号" width="140" show-overflow-tooltip />
          <el-table-column label="入科时间" width="140">
            <template #default="{ row }">{{ fmtTime(row.in_depart_time) }}</template>
          </el-table-column>
          <el-table-column label="出科时间" width="140">
            <template #default="{ row }">{{ fmtTime(row.out_depart_time) }}</template>
          </el-table-column>
          <el-table-column prop="ards_grade" label="分级" width="80">
            <template #default="{ row }">
              <el-tag :type="gradeTagType(row.ards_grade)" size="small">{{ row.ards_grade }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="oxygenation_index" label="氧合指数" width="90" />
          <el-table-column prop="fio2" label="FiO2" width="70" />
          <el-table-column prop="peep" label="PEEP" width="70" />
          <el-table-column prop="vt_ml" label="潮气量(ml)" width="90" />
          <el-table-column label="潮气量/kg" width="100">
            <template #default="{ row }">
              <span v-if="row.vt_per_kg != null">{{ row.vt_per_kg }}<el-tag v-if="row.ideal_weight_estimated" type="warning" size="small" style="margin-left:2px;">估</el-tag></span>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column prop="vent_days" label="通气天数" width="80" />
        </el-table>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick } from 'vue'
import { Search } from '@element-plus/icons-vue'
import * as echarts from '../utils/echarts'
import request from '../api/request'

const loading = ref(false)
const patients = ref([])
const summary = ref({})
const gradeFilter = ref('')
const expandRowKeys = ref([])
const departCode = ref('')
const departName = ref('')

// 时间范围默认当月
const now = new Date()
const firstDay = new Date(now.getFullYear(), now.getMonth(), 1)
const lastDay = new Date(now.getFullYear(), now.getMonth() + 1, 0)
const dateRange = ref([
  `${firstDay.getFullYear()}-${String(firstDay.getMonth() + 1).padStart(2, '0')}-${String(firstDay.getDate()).padStart(2, '0')}`,
  `${lastDay.getFullYear()}-${String(lastDay.getMonth() + 1).padStart(2, '0')}-${String(lastDay.getDate()).padStart(2, '0')}`
])

// 图表引用
const gradeChartRef = ref(null)
const complianceChartRef = ref(null)
let gradeChart = null
let complianceChart = null
const ventChartMap = {}
const oiChartMap = {}

function setVentChartRef(pid, el) {
  if (el) ventChartMap[pid] = el
}
function setOiChartRef(pid, el) {
  if (el) oiChartMap[pid] = el
}

// 外链参数
onMounted(() => {
  const params = new URLSearchParams(window.location.search)
  departCode.value = params.get('departCode') || ''
  if (departCode.value) {
    loadDepartments()
    loadData()
  }
})

async function loadDepartments() {
  try {
    const data = await request.get('/handover/departments')
    if (data && Array.isArray(data)) {
      const dept = data.find(d => d.org_code === departCode.value)
      if (dept && dept.depart_name) departName.value = dept.depart_name
    }
  } catch (e) {
    console.error('加载科室列表失败', e)
  }
}

async function loadData() {
  if (!departCode.value) return
  loading.value = true
  try {
    const data = await request.get('/ards/overview', {
      params: {
        departCode: departCode.value,
        startTime: dateRange.value[0] + ' 00:00:00',
        endTime: dateRange.value[1] + ' 23:59:59',
        grade: gradeFilter.value
      }
    })
    summary.value = data.summary || {}
    patients.value = (data.patients || []).map(p => ({
      ...p,
      detailLoading: false,
      ventMetric: 'vt_ml',
      detailLoaded: false
    }))
    nextTick(() => {
      renderGradeChart()
      renderComplianceChart()
    })
  } catch (e) {
    console.error('加载ARDS数据失败', e)
    console.warn('加载失败: ', e.message || e)
  } finally {
    loading.value = false
  }
}

// 行点击展开
function handleRowClick(row) {
  const idx = expandRowKeys.value.indexOf(row.patient_id)
  if (idx > -1) {
    expandRowKeys.value.splice(idx, 1)
  } else {
    expandRowKeys.value.push(row.patient_id)
    if (!row.detailLoaded) {
      loadPatientDetail(row)
    }
  }
}

async function loadPatientDetail(row) {
  row.detailLoading = true
  try {
    // 呼吸机趋势查询范围：入科时间 ~ 出科时间（在科期间）
    const trendStart = fmtTimeFull(row.in_depart_time) || '2000-01-01 00:00:00'
    const trendEnd = fmtTimeFull(row.out_depart_time) || fmtTimeFull(new Date())
    const [ventData, oiData] = await Promise.all([
      request.get(`/ards/patient/${row.patient_id}/ventilator-trend`, { params: { startTime: trendStart, endTime: trendEnd } }),
      request.get(`/ards/patient/${row.patient_id}/oxygenation-history`)
    ])
    row.ventData = ventData || []
    row.oiData = oiData || []
    row.detailLoaded = true
    nextTick(() => {
      renderVentChart(row)
      renderOiChart(row)
    })
  } catch (e) {
    console.error('加载患者详情失败', e)
  } finally {
    row.detailLoading = false
  }
}

// 分级饼图
function renderGradeChart() {
  if (!gradeChartRef.value) return
  if (!gradeChart) gradeChart = echarts.init(gradeChartRef.value)
  const s = summary.value
  gradeChart.setOption({
    tooltip: { trigger: 'item' },
    legend: { bottom: 0, itemWidth: 12, itemHeight: 12 },
    series: [{
      type: 'pie',
      radius: ['40%', '65%'],
      center: ['50%', '45%'],
      label: { formatter: '{b}: {c} ({d}%)', fontSize: 11 },
      data: [
        { value: s.mild || 0, name: '轻度', itemStyle: { color: '#67C23A' } },
        { value: s.moderate || 0, name: '中度', itemStyle: { color: '#E6A23C' } },
        { value: s.severe || 0, name: '重度', itemStyle: { color: '#F56C6C' } },
        { value: s.unknown_grade || 0, name: '未知', itemStyle: { color: '#909399' } }
      ]
    }]
  })
}

// 依从率柱状图
function renderComplianceChart() {
  if (!complianceChartRef.value) return
  if (!complianceChart) complianceChart = echarts.init(complianceChartRef.value)
  const s = summary.value
  complianceChart.setOption({
    tooltip: { trigger: 'axis', formatter: '{b}: {c}%' },
    grid: { left: 50, right: 20, top: 20, bottom: 40 },
    xAxis: {
      type: 'category',
      data: ['潮气量≤6ml/kg', 'PEEP≥5', 'FiO2≤60%', '呼吸频率≤35'],
      axisLabel: { fontSize: 11, interval: 0 }
    },
    yAxis: { type: 'value', max: 100, axisLabel: { formatter: '{value}%' } },
    series: [{
      type: 'bar',
      data: [
        { value: s.vt_compliance_rate || 0, itemStyle: { color: '#409EFF' } },
        { value: s.peep_compliance_rate || 0, itemStyle: { color: '#67C23A' } },
        { value: s.fio2_compliance_rate || 0, itemStyle: { color: '#E6A23C' } },
        { value: s.rr_compliance_rate || 0, itemStyle: { color: '#909399' } }
      ],
      barWidth: '40%',
      label: { show: true, position: 'top', formatter: '{c}%', fontSize: 11 }
    }]
  })
}

// 呼吸机参数趋势
function renderVentChart(row) {
  const el = ventChartMap[row.patient_id]
  if (!el || !row.ventData) return
  let chart = echarts.getInstanceByDom(el)
  if (!chart) chart = echarts.init(el)

  const metric = row.ventMetric
  const metricNames = {
    vt_ml: '潮气量(ml)', peep: 'PEEP(cmH2O)', fio2: 'FiO2(%)',
    rr: '呼吸频率(次/分)', mv: '分钟通气量(L/min)'
  }
  // 短码 -> 完整item_code映射
  const metricCodeMap = {
    vt_ml: 'oi_呼末潮气量',
    peep: 'oi_peep',
    fio2: 'oi_FiO2(设置值)',
    rr: 'oi_呼吸频率(设置值)',
    mv: 'oi_呼末分钟通气量'
  }
  const fullCode = metricCodeMap[metric] || metric
  const filtered = row.ventData.filter(d => d.item_code === fullCode)
  const times = filtered.map(d => fmtTimeShort(d.item_time))
  const values = filtered.map(d => parseFloat(d.item_value) || 0)

  // 目标线
  let markLine = null
  if (metric === 'vt_ml' && row.ideal_weight) {
    const target = 6 * row.ideal_weight
    markLine = { data: [{ yAxis: target, name: '6ml/kg目标', lineStyle: { color: '#F56C6C', type: 'dashed' } }], label: { formatter: `目标:${target.toFixed(0)}ml` } }
  } else if (metric === 'peep') {
    markLine = { data: [{ yAxis: 5, lineStyle: { color: '#67C23A', type: 'dashed' } }], label: { formatter: '目标≥5' } }
  } else if (metric === 'fio2') {
    markLine = { data: [{ yAxis: 60, lineStyle: { color: '#E6A23C', type: 'dashed' } }], label: { formatter: '目标≤60%' } }
  } else if (metric === 'rr') {
    markLine = { data: [{ yAxis: 35, lineStyle: { color: '#F56C6C', type: 'dashed' } }], label: { formatter: '目标≤35' } }
  }

  chart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 50, right: 20, top: 30, bottom: 40 },
    xAxis: { type: 'category', data: times, axisLabel: { fontSize: 10 } },
    yAxis: { type: 'value', name: metricNames[metric], nameTextStyle: { fontSize: 10 } },
    series: [{
      type: 'line',
      data: values,
      smooth: true,
      symbol: 'circle',
      symbolSize: 4,
      lineStyle: { width: 2, color: '#409EFF' },
      itemStyle: { color: '#409EFF' },
      markLine: markLine
    }]
  }, true)
}

// 氧合指数趋势
function renderOiChart(row) {
  const el = oiChartMap[row.patient_id]
  if (!el || !row.oiData) return
  let chart = echarts.getInstanceByDom(el)
  if (!chart) chart = echarts.init(el)

  const times = row.oiData.map(d => fmtTimeShort(d.check_time))
  const values = row.oiData.map(d => parseFloat(d.oxygenation_index) || 0)

  chart.setOption({
    tooltip: { trigger: 'axis', formatter: p => `${p[0].axisValue}<br/>氧合指数: ${p[0].value}` },
    grid: { left: 50, right: 20, top: 30, bottom: 40 },
    xAxis: { type: 'category', data: times, axisLabel: { fontSize: 10 } },
    yAxis: { type: 'value', name: '氧合指数', nameTextStyle: { fontSize: 10 } },
    series: [{
      type: 'line',
      data: values,
      smooth: true,
      symbol: 'circle',
      symbolSize: 6,
      lineStyle: { width: 2, color: '#F56C6C' },
      itemStyle: { color: '#F56C6C' },
      markArea: {
        itemStyle: { opacity: 0.08 },
        data: [
          [{ yAxis: 200, itemStyle: { color: '#67C23A' } }, { yAxis: 300 }],
          [{ yAxis: 100, itemStyle: { color: '#E6A23C' } }, { yAxis: 200 }],
          [{ yAxis: 0, itemStyle: { color: '#F56C6C' } }, { yAxis: 100 }]
        ]
      },
      markLine: {
        data: [
          { yAxis: 300, lineStyle: { color: '#67C23A', type: 'dashed' }, label: { formatter: '轻度上限300', fontSize: 9 } },
          { yAxis: 200, lineStyle: { color: '#E6A23C', type: 'dashed' }, label: { formatter: '中度上限200', fontSize: 9 } },
          { yAxis: 100, lineStyle: { color: '#F56C6C', type: 'dashed' }, label: { formatter: '重度上限100', fontSize: 9 } }
        ]
      }
    }]
  }, true)
}

// 达标情况行
function buildComplianceRows(row) {
  const c = row.compliance || {}
  return [
    { metric: '潮气量/kg', current: row.vt_per_kg != null ? row.vt_per_kg + ' ml/kg' : '无数据', target: '≤6 ml/kg', status: c.vt_ok ? '达标' : row.vt_per_kg != null ? '未达标' : '无数据' },
    { metric: 'PEEP', current: row.peep != null ? row.peep + ' cmH2O' : '无数据', target: '≥5 cmH2O', status: c.peep_ok ? '达标' : row.peep != null ? '未达标' : '无数据' },
    { metric: 'FiO2', current: row.fio2 != null ? row.fio2 + '%' : '无数据', target: '≤60%', status: c.fio2_ok ? '达标' : row.fio2 != null ? '未达标' : '无数据' },
    { metric: '呼吸频率', current: row.rr != null ? row.rr + ' 次/分' : '无数据', target: '≤35 次/分', status: c.rr_ok ? '达标' : row.rr != null ? '未达标' : '无数据' },
    { metric: '平台压', current: '无数据', target: '≤30 cmH2O', status: '无数据' },
    { metric: '驱动压', current: '无数据', target: '≤15 cmH2O', status: '无数据' }
  ]
}

function gradeTagType(grade) {
  if (grade === '轻度') return 'success'
  if (grade === '中度') return 'warning'
  if (grade === '重度') return 'danger'
  return 'info'
}

function fmtTime(val) {
  if (!val) return '—'
  const s = String(val).replace('T', ' ')
  return s.length > 16 ? s.substring(0, 16) : s
}

function fmtTimeFull(val) {
  if (!val) return ''
  const s = String(val).replace('T', ' ')
  return s.length > 19 ? s.substring(0, 19) : s
}

function fmtTimeShort(val) {
  if (!val) return ''
  const s = String(val).replace('T', ' ')
  return s.length > 16 ? s.substring(5, 16) : s
}
</script>

<style scoped>
.ards-monitor {
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
.page-title {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}
.filters {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.depart-tag {
  color: #909399;
  font-size: 13px;
  margin-left: 8px;
}
.stat-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-bottom: 16px;
}
.stat-card {
  background: #fff;
  border-radius: 8px;
  padding: 16px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.06);
}
.stat-label {
  font-size: 13px;
  color: #909399;
  margin-bottom: 8px;
}
.stat-value {
  font-size: 28px;
  font-weight: 600;
  color: #303133;
}
.stat-value-grade {
  font-size: 24px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 4px;
}
.grade-mild { color: #67C23A; }
.grade-moderate { color: #E6A23C; }
.grade-severe { color: #F56C6C; }
.divider { color: #dcdfe6; font-size: 18px; }
.stat-sub {
  font-size: 12px;
  color: #c0c4cc;
  margin-top: 4px;
}
.chart-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin-bottom: 16px;
}
.chart-box {
  background: #fff;
  border-radius: 8px;
  padding: 12px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.06);
}
.chart-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 8px;
}
.chart-container {
  height: 220px;
}
.table-box {
  background: #fff;
  border-radius: 8px;
  padding: 12px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.06);
}
.table-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 8px;
}
.ards-table {
  cursor: pointer;
}
.detail-panel {
  padding: 12px 24px;
  background: #fafafa;
}
.detail-section {
  margin-bottom: 16px;
}
.detail-title {
  font-size: 13px;
  font-weight: 600;
  color: #606266;
  margin-bottom: 8px;
}
.detail-tabs {
  margin-bottom: 8px;
}
.detail-chart {
  height: 200px;
  background: #fff;
  border-radius: 4px;
}
@media (max-width: 1200px) {
  .stat-cards { grid-template-columns: repeat(2, 1fr); }
  .chart-row { grid-template-columns: 1fr; }
}
</style>
