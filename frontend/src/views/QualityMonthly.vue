<template>
  <div class="quality-monthly">
    <!-- 筛选栏 -->
    <div class="filter-bar">
      <div class="filter-left">
        <el-date-picker
          v-model="year"
          type="year"
          placeholder="选择年份"
          value-format="YYYY"
          :clearable="false"
          style="width: 130px"
          @change="loadMonthly"
        />
        <el-select
          v-model="departCode"
          placeholder="全院"
          style="width: 170px"
          clearable
          @change="loadMonthly"
        >
          <el-option
            v-for="dept in departments"
            :key="dept.org_code"
            :label="dept.depart_name"
            :value="dept.org_code"
          />
        </el-select>
        <el-select v-model="domainFilter" placeholder="全部域" style="width: 170px" clearable>
          <el-option v-for="d in domainOptions" :key="d" :label="d" :value="d" />
        </el-select>
        <el-checkbox v-model="onlyWithData">只看有数据的指标</el-checkbox>
        <el-button type="primary" :loading="loading" @click="loadMonthly">
          <el-icon><Refresh /></el-icon> 刷新
        </el-button>
      </div>

      <div class="filter-right">
        <el-button :loading="rebuilding" @click="doRebuild">
          <el-icon><Cpu /></el-icon> 重建汇总
        </el-button>
        <el-button type="success" :loading="exporting" :disabled="!rows.length" @click="doExport">
          <el-icon><Download /></el-icon> 导出 Excel
        </el-button>
      </div>
    </div>

    <!-- 摘要卡 -->
    <div class="metric-cards">
      <div class="metric-card info">
        <div class="metric-label">汇总指标数</div>
        <div class="metric-value">{{ rows.length }}</div>
        <div class="metric-sub">{{ year }} 年 · {{ departLabel }}</div>
      </div>
      <div class="metric-card ok">
        <div class="metric-label">本年度有数据</div>
        <div class="metric-value">{{ withDataCount }}</div>
        <div class="metric-sub">至少 1 个月有值</div>
      </div>
      <div class="metric-card">
        <div class="metric-label">覆盖域</div>
        <div class="metric-value">{{ domainOptions.length }}</div>
        <div class="metric-sub">按指标所属域统计</div>
      </div>
      <div class="metric-card muted">
        <div class="metric-label">当前展示</div>
        <div class="metric-value">{{ displayRows.length }}</div>
        <div class="metric-sub">{{ filterHint }}</div>
      </div>
    </div>

    <el-alert
      v-if="!loading && rows.length === 0"
      type="warning"
      show-icon
      :closable="false"
      title="该年度暂无汇总数据"
      description="月度汇总宽表由结果表折叠而来，需先按月出数（看板页「触发计算」），再点「重建汇总」生成。"
      style="margin-bottom: 14px"
    />

    <!-- 年度趋势 -->
    <div v-if="rows.length" class="chart-card">
      <div class="chart-head">
        <div class="block-title">
          <span class="dot"></span>
          <span>年度趋势（单指标 1-12 月）</span>
        </div>
        <el-select
          v-model="chartCode"
          filterable
          placeholder="选择指标"
          style="width: 360px"
          @change="renderChart"
        >
          <el-option-group v-for="g in chartGroups" :key="g.domain" :label="g.domain">
            <el-option
              v-for="m in g.metrics"
              :key="m.code"
              :label="`${m.code} ${m.name}`"
              :value="m.code"
            />
          </el-option-group>
        </el-select>
      </div>
      <div ref="chartRef" class="chart-container"></div>
    </div>

    <!-- 月度宽表 -->
    <div class="table-card">
      <div class="block-title">
        <span class="dot"></span>
        <span>月度汇总宽表（1-12 月横排）</span>
        <span class="title-note">
          率类按「分子分母先汇总再重算」为加权值，数类直接求和；高亮为年内最高/最低月
        </span>
      </div>
      <el-table
        v-loading="loading"
        :data="displayRows"
        border
        size="small"
        max-height="620"
        :row-class-name="rowClass"
      >
        <el-table-column prop="code" label="指标编号" width="118" fixed="left" />
        <el-table-column prop="name" label="指标名称" min-width="230" fixed="left" show-overflow-tooltip />
        <el-table-column prop="domain" label="所属域" width="130" />
        <el-table-column prop="unit" label="单位" width="66" align="center" />
        <el-table-column
          v-for="(h, i) in header"
          :key="h"
          :label="h"
          width="88"
          align="right"
        >
          <template #default="{ row }">
            <span :class="monthClass(row, i)">{{ num(row.months ? row.months[i] : null) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="Q1" width="90" align="right">
          <template #default="{ row }">{{ num(row.q1) }}</template>
        </el-table-column>
        <el-table-column label="Q2" width="90" align="right">
          <template #default="{ row }">{{ num(row.q2) }}</template>
        </el-table-column>
        <el-table-column label="Q3" width="90" align="right">
          <template #default="{ row }">{{ num(row.q3) }}</template>
        </el-table-column>
        <el-table-column label="Q4" width="90" align="right">
          <template #default="{ row }">{{ num(row.q4) }}</template>
        </el-table-column>
        <el-table-column label="全年合计" width="110" align="right" fixed="right">
          <template #default="{ row }">
            <span class="strong">{{ num(row.yearTotal) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="月均" width="100" align="right" fixed="right">
          <template #default="{ row }">
            <span class="strong">{{ num(row.yearAvg) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="最高月" width="86" align="center" fixed="right">
          <template #default="{ row }">{{ row.maxMonth ? `${row.maxMonth}月` : '—' }}</template>
        </el-table-column>
        <el-table-column label="最低月" width="86" align="center" fixed="right">
          <template #default="{ row }">{{ row.minMonth ? `${row.minMonth}月` : '—' }}</template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Cpu, Download } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import {
  fetchQualityMonthly,
  rebuildQualityMonthly,
  exportQualityXlsx,
  fetchQualityDepartments
} from '../api/quality'

const DEFAULT_HEADER = [
  '1月', '2月', '3月', '4月', '5月', '6月',
  '7月', '8月', '9月', '10月', '11月', '12月'
]

const year = ref(String(new Date().getFullYear()))
const departCode = ref('')
const departments = ref([])
const domainFilter = ref('')
const onlyWithData = ref(false)

const loading = ref(false)
const rebuilding = ref(false)
const exporting = ref(false)

const headers = ref(DEFAULT_HEADER)
const rowsData = ref([])
const chartCode = ref('')
const chartRef = ref(null)
let chart = null

const header = computed(() => headers.value || DEFAULT_HEADER)
const rows = computed(() => rowsData.value || [])

const departLabel = computed(() => {
  if (!departCode.value) return '全院'
  const d = departments.value.find((x) => x.org_code === departCode.value)
  return d ? d.depart_name : departCode.value
})

const domainOptions = computed(() =>
  [...new Set(rows.value.map((r) => r.domain).filter(Boolean))]
)

const withDataCount = computed(
  () =>
    rows.value.filter((r) =>
      (r.months || []).some((v) => v !== null && v !== undefined && v !== '')
    ).length
)

const displayRows = computed(() => {
  let list = rows.value
  if (domainFilter.value) {
    list = list.filter((r) => r.domain === domainFilter.value)
  }
  if (onlyWithData.value) {
    list = list.filter((r) =>
      (r.months || []).some((v) => v !== null && v !== undefined && v !== '')
    )
  }
  return list
})

const filterHint = computed(() => {
  const parts = []
  if (domainFilter.value) parts.push(domainFilter.value)
  if (onlyWithData.value) parts.push('仅有数据')
  return parts.length ? parts.join(' · ') : '全部指标'
})

/** 趋势图下拉：按域分组的指标清单 */
const chartGroups = computed(() => {
  const map = new Map()
  for (const r of rows.value) {
    const key = r.domain || '其他'
    if (!map.has(key)) map.set(key, [])
    map.get(key).push({ code: r.code, name: r.name })
  }
  return [...map.entries()].map(([domain, metrics]) => ({ domain, metrics }))
})

// ---------------- 数据加载 ----------------
async function loadMonthly() {
  loading.value = true
  try {
    const res = await fetchQualityMonthly(Number(year.value), departCode.value || '')
    headers.value = (res && res.header) || DEFAULT_HEADER
    rowsData.value = (res && res.rows) || []
    // 选中指标被过滤掉或首次加载时，回落到第一条
    if (!rowsData.value.some((r) => r.code === chartCode.value)) {
      chartCode.value = rowsData.value.length ? rowsData.value[0].code : ''
    }
    await nextTick()
    renderChart()
  } catch (e) {
    console.warn('月度汇总加载失败:', e.message || e)
  } finally {
    loading.value = false
  }
}

async function loadDepartments() {
  try {
    const res = await fetchQualityDepartments()
    departments.value = Array.isArray(res) ? res : []
  } catch (e) {
    console.warn('科室列表加载失败:', e.message || e)
  }
}

// ---------------- 重建 / 导出 ----------------
async function doRebuild() {
  try {
    await ElMessageBox.confirm(
      `将按 ${year.value} 年的月度结果重建汇总宽表（幂等：先删后建），是否继续？`,
      '重建月度汇总',
      { type: 'warning', confirmButtonText: '开始重建', cancelButtonText: '取消' }
    )
  } catch (e) {
    return
  }
  rebuilding.value = true
  try {
    const res = await rebuildQualityMonthly(Number(year.value), departCode.value || '')
    const n = res && res.metrics !== undefined ? res.metrics : 0
    ElMessage.success(`重建完成：${n} 条指标汇总`)
    await loadMonthly()
  } catch (e) {
    ElMessage.error('重建失败：' + (e.message || e))
  } finally {
    rebuilding.value = false
  }
}

async function doExport() {
  exporting.value = true
  try {
    const resp = await exportQualityXlsx(Number(year.value), departCode.value || '')
    const blob = new Blob([resp.data], {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
    })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `质控月报_${year.value}_${departLabel.value}.xlsx`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    setTimeout(() => URL.revokeObjectURL(url), 60000)
    ElMessage.success('导出成功')
  } catch (e) {
    ElMessage.error('导出失败：' + (e.message || e))
  } finally {
    exporting.value = false
  }
}

// ---------------- 图表 ----------------
function renderChart() {
  if (!chartRef.value) return
  // 图表容器在「无数据」时会被 v-if 移除，恢复数据后是新的 DOM 节点；
  // 此时旧实例仍持有已卸载的节点，必须销毁重建，否则新容器永远是空白。
  if (chart && chart.getDom() !== chartRef.value) {
    chart.dispose()
    chart = null
  }
  if (!chart) chart = echarts.init(chartRef.value)
  const row = rows.value.find((r) => r.code === chartCode.value)
  if (!row) {
    chart.clear()
    return
  }
  const data = (row.months || []).map((v) =>
    v === null || v === undefined || v === '' ? null : Number(v)
  )
  chart.setOption(
    {
      tooltip: { trigger: 'axis' },
      grid: { left: 60, right: 30, top: 36, bottom: 34 },
      xAxis: {
        type: 'category',
        data: header.value,
        axisLabel: { color: '#666' }
      },
      yAxis: {
        type: 'value',
        name: row.unit || '',
        axisLabel: { color: '#666' },
        splitLine: { lineStyle: { color: '#eee' } }
      },
      series: [
        {
          name: row.name,
          type: 'line',
          smooth: true,
          connectNulls: true,
          data,
          itemStyle: { color: '#409eff' },
          areaStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: 'rgba(64,158,255,0.30)' },
              { offset: 1, color: 'rgba(64,158,255,0.05)' }
            ])
          },
          label: { show: true, fontSize: 10, color: '#666' }
        }
      ]
    },
    true
  )
}

function handleResize() {
  chart?.resize()
}

// ---------------- 展示工具 ----------------
/** 数值展示：整数不带小数，否则保留 2 位并去掉尾随 0 */
function num(v) {
  if (v === null || v === undefined || v === '') return '—'
  const n = Number(v)
  if (Number.isNaN(n)) return String(v)
  if (Number.isInteger(n)) return String(n)
  return n.toFixed(2).replace(/0+$/, '').replace(/\.$/, '')
}

/** 月度单元格高亮：年内最高月/最低月 */
function monthClass(row, index) {
  const m = index + 1
  if (row.maxMonth === m && row.minMonth === m) return ''
  if (row.maxMonth === m) return 'month-max'
  if (row.minMonth === m) return 'month-min'
  return ''
}

function rowClass({ row }) {
  return (row.months || []).some((v) => v !== null && v !== undefined && v !== '')
    ? ''
    : 'row-empty'
}

onMounted(async () => {
  await loadDepartments()
  await loadMonthly()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  if (chart) {
    chart.dispose()
    chart = null
  }
})
</script>

<style scoped>
.quality-monthly {
  padding: 16px;
  background: #f5f7fa;
  min-height: 100vh;
}

.filter-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 16px;
  padding: 12px 16px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
}

.filter-left,
.filter-right {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
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
  padding: 18px 20px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
  border-left: 4px solid #909399;
}

.metric-card.ok {
  border-left-color: #67c23a;
}
.metric-card.info {
  border-left-color: #409eff;
}
.metric-card.muted {
  border-left-color: #c0c4cc;
}

.metric-label {
  font-size: 14px;
  color: #606266;
  margin-bottom: 8px;
}

.metric-value {
  font-size: 30px;
  font-weight: 700;
  color: #303133;
  line-height: 1.2;
}

.metric-sub {
  font-size: 12px;
  color: #909399;
  margin-top: 6px;
}

.chart-card,
.table-card {
  background: #fff;
  border-radius: 8px;
  padding: 16px 20px;
  margin-bottom: 16px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
}

.chart-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}

.chart-container {
  width: 100%;
  height: 320px;
}

.block-title {
  display: flex;
  align-items: center;
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 12px;
}

.dot {
  width: 4px;
  height: 16px;
  background: linear-gradient(180deg, #409eff, #66b1ff);
  border-radius: 2px;
  margin-right: 8px;
}

.title-note {
  font-size: 12px;
  font-weight: 400;
  color: #909399;
  margin-left: 10px;
}

.strong {
  font-weight: 600;
  color: #303133;
}

:deep(.month-max) {
  color: #f56c6c;
  font-weight: 700;
}

:deep(.month-min) {
  color: #67c23a;
  font-weight: 700;
}

:deep(.row-empty) {
  color: #c0c4cc;
}

@media (max-width: 1200px) {
  .metric-cards {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
