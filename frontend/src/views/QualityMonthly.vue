<template>
  <div class="quality-monthly qb-theme">
    <!-- 筛选栏 -->
    <div class="filter-bar qb-card">
      <div class="filter-left">
        <el-date-picker
          v-model="year"
          type="year"
          placeholder="选择年份"
          value-format="YYYY"
          :clearable="false"
          style="width: 130px"
          popper-class="qb-popper"
          @change="loadMonthly"
        />
        <el-select
          v-model="departCode"
          placeholder="全院"
          style="width: 170px"
          clearable
          popper-class="qb-popper"
          @change="loadMonthly"
        >
          <el-option
            v-for="dept in departments"
            :key="dept.org_code"
            :label="dept.depart_name"
            :value="dept.org_code"
          />
        </el-select>
        <el-checkbox v-model="onlyWithData">只看有数据的指标</el-checkbox>
        <el-checkbox v-model="showAtoms" @change="loadMonthly">显示原子项</el-checkbox>
        <el-button type="primary" :loading="loading" @click="loadMonthly">
          <el-icon><Refresh /></el-icon> 刷新
        </el-button>
      </div>

      <div class="filter-right">
        <el-button :loading="rebuilding" @click="doRebuild">
          <el-icon><Cpu /></el-icon> 重建汇总
        </el-button>
        <el-button type="primary" :loading="exporting" :disabled="!rows.length" @click="doExport">
          <el-icon><Download /></el-icon> 导出 Excel
        </el-button>
      </div>
    </div>

    <!-- 摘要卡 -->
    <div class="metric-cards">
      <div class="metric-card info">
        <div class="metric-head">
          <span class="metric-label">汇总指标数</span>
          <span class="metric-ico"><el-icon><Grid /></el-icon></span>
        </div>
        <div class="metric-value">{{ metricRows.length }}</div>
        <div class="metric-sub">{{ year }} 年 · {{ departLabel }}</div>
      </div>
      <div class="metric-card ok">
        <div class="metric-head">
          <span class="metric-label">本年度有数据</span>
          <span class="metric-ico"><el-icon><CircleCheck /></el-icon></span>
        </div>
        <div class="metric-value">{{ withDataCount }}</div>
        <div class="metric-sub">至少 1 个月有值</div>
      </div>
      <div class="metric-card">
        <div class="metric-head">
          <span class="metric-label">覆盖域</span>
          <span class="metric-ico"><el-icon><Collection /></el-icon></span>
        </div>
        <div class="metric-value">{{ domainOptions.length }}</div>
        <div class="metric-sub">按指标所属域统计</div>
      </div>
      <div class="metric-card muted">
        <div class="metric-head">
          <span class="metric-label">当前展示</span>
          <span class="metric-ico"><el-icon><View /></el-icon></span>
        </div>
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
    <div v-if="rows.length" class="chart-card qb-card">
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
          popper-class="qb-popper"
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
    <div class="table-card qb-card">
      <div class="block-title">
        <span class="dot"></span>
        <span>月度汇总宽表（1-12 月横排）</span>
        <span class="title-note">
          行 = 质控指标（分子 ÷ 分母 × 系数），点行展开可见分子项 / 分母项；
          季度与全年按「分子分母先汇总再相除」的加权口径，高亮为年内最高/最低月
        </span>
      </div>
      <!--
        树形表格：主行是**指标**，展开后是构成它的分子项与分母项（原子项）。
        默认折叠 —— 一屏几十条指标已经够看，要追究某条的构成再点开那一条。
        row-key 用后端给的 rowKey（子行带规则前缀）：同一个原子项常被多条指标共用
        （quality_403 被 13 条当分母），key 若只用原子项 code，点开一条会连带展开十几条。
      -->
      <el-table
        ref="tableRef"
        v-loading="loading"
        :data="displayRows"
        row-key="rowKey"
        :tree-props="{ children: 'children' }"
        border
        size="small"
        max-height="620"
        :row-class-name="rowClass"
        @row-click="toggleRow"
      >
        <!--
          不设「指标编号」列：ruleId 是 ICU 侧数字 id 或 LOCAL_xxx，又长又占宽，
          对看报表的人不构成信息（要追溯是哪条规则，导出的 Excel 里有编号列）。
          name 因此加宽，并且成为树形展开箭头的落点 —— 箭头固定出现在第一列。
        -->
        <el-table-column prop="name" label="指标名称" min-width="300" fixed="left" show-overflow-tooltip />
        <el-table-column label="类型" width="72" align="center">
          <template #default="{ row }">
            <span :class="['type-tag', row.rowType === 'METRIC' ? 'is-metric' : 'is-atom']">
              {{ row.rowType === 'METRIC' ? '指标' : row.roleLabel || '原子项' }}
            </span>
          </template>
        </el-table-column>
        <!--
          不设「所属域」列：域是给人**筛**的（顶部下拉 + 趋势图分组标题），不是给人读的 ——
          一行指标属于哪个域，看名字就猜得到，单占一列只是噪声。
        -->
        <el-table-column prop="unit" label="单位" width="70" align="center" />
        <el-table-column
          v-for="(h, i) in header"
          :key="h"
          :label="h"
          width="88"
          align="right"
        >
          <template #default="{ row }">
            <span :class="monthClass(row, i)">
              {{ num(row.months ? row.months[i] : null, row.precision) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="Q1" width="90" align="right">
          <template #default="{ row }">{{ num(row.q1, row.precision) }}</template>
        </el-table-column>
        <el-table-column label="Q2" width="90" align="right">
          <template #default="{ row }">{{ num(row.q2, row.precision) }}</template>
        </el-table-column>
        <el-table-column label="Q3" width="90" align="right">
          <template #default="{ row }">{{ num(row.q3, row.precision) }}</template>
        </el-table-column>
        <el-table-column label="Q4" width="90" align="right">
          <template #default="{ row }">{{ num(row.q4, row.precision) }}</template>
        </el-table-column>
        <el-table-column label="全年合计" width="110" align="right" fixed="right">
          <template #default="{ row }">
            <span class="strong">{{ num(row.yearTotal, row.precision) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="月均" width="100" align="right" fixed="right">
          <template #default="{ row }">
            <span class="strong">{{ num(row.yearAvg, row.precision) }}</span>
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
import { Refresh, Cpu, Download, Grid, CircleCheck, Collection, View } from '@element-plus/icons-vue'
import { externalParam } from '../utils/external'
import '../styles/quality-theme.css'
import * as echarts from '../utils/echarts'
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
// 外链带 departCode 时默认按该科室统计（与看板一致），不再默认跑全院；可手动清空回到全院
const departCode = ref(externalParam('departCode'))
const departments = ref([])
const onlyWithData = ref(false)
// 宽表的行是指标（分子 ÷ 分母 × 系数）。打开它才追加「没有任何指标在引用」的原子项行 ——
// 那是排查用的（某个原子项算出来了却没人用），日常看指标时不必背着这些噪声
const showAtoms = ref(false)

const loading = ref(false)
const rebuilding = ref(false)
const exporting = ref(false)

const headers = ref(DEFAULT_HEADER)
const rowsData = ref([])
const chartCode = ref('')
const chartRef = ref(null)
const tableRef = ref(null)
let chart = null

const header = computed(() => headers.value || DEFAULT_HEADER)
const rows = computed(() => rowsData.value || [])

/** 指标行（宽表主行）：趋势图与摘要卡都只认它，原子项不计入「指标数」 */
const metricRows = computed(() => rows.value.filter((r) => r.rowType === 'METRIC'))

const departLabel = computed(() => {
  if (!departCode.value) return '全院'
  const d = departments.value.find((x) => x.org_code === departCode.value)
  return d ? d.depart_name : departCode.value
})

/**
 * 指标覆盖的域（只供摘要卡的「覆盖域」计数）。
 *
 * <p>页面上既没有域列、也没有域筛选：这个页面是「按指标逐条看全年走势」，
 * 域筛不出更有价值的东西，留着下拉反而让人以为筛一下能看到更相关的行。
 * 只取指标行 —— 指标的域是 qualityTypeCode，与原子项的 domain 不是一套。
 */
const domainOptions = computed(() =>
  [...new Set(metricRows.value.map((r) => r.domain).filter(Boolean))]
)

/**
 * 某行是否出过数。
 *
 * <p>指标还要看它的分子/分母：率算不出来（分母为 0）**不等于**没出数 ——
 * 「这个月没有分母人群」和「压根没跑批」是两回事，都判成没数据会把前者误筛掉。
 */
function hasData(row) {
  if ((row.months || []).some((v) => v !== null && v !== undefined && v !== '')) return true
  return (row.children || []).some((k) =>
    (k.months || []).some((v) => v !== null && v !== undefined && v !== '')
  )
}

const withDataCount = computed(() => metricRows.value.filter(hasData).length)

/** 点整行即展开/收起它的分子、分母：比去点那个小三角容易命中得多 */
function toggleRow(row) {
  if (!row || !row.children || !row.children.length) return
  tableRef.value?.toggleRowExpansion(row)
}

const displayRows = computed(() =>
  onlyWithData.value ? rows.value.filter(hasData) : rows.value
)

const filterHint = computed(() => {
  const parts = []
  if (onlyWithData.value) parts.push('仅有数据')
  if (showAtoms.value) parts.push('含原子项')
  return parts.length ? parts.join(' · ') : '全部指标'
})

/** 趋势图下拉：按域分组的**指标**清单（原子项只在宽表里作为展开行，不混进趋势图） */
const chartGroups = computed(() => {
  const map = new Map()
  for (const r of metricRows.value) {
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
    const res = await fetchQualityMonthly(
      Number(year.value),
      departCode.value || '',
      showAtoms.value
    )
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
      { type: 'warning', confirmButtonText: '开始重建', cancelButtonText: '取消', customClass: 'qb-overlay' }
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
        axisLabel: { color: '#78716c' },
        axisLine: { lineStyle: { color: '#e7e5e4' } }
      },
      yAxis: {
        type: 'value',
        name: row.unit || '',
        nameTextStyle: { color: '#78716c' },
        axisLabel: { color: '#78716c' },
        splitLine: { lineStyle: { color: '#f5f5f4' } }
      },
      series: [
        {
          name: row.name,
          type: 'line',
          smooth: true,
          connectNulls: true,
          data,
          symbolSize: 7,
          lineStyle: { width: 3, color: '#f97316' },
          itemStyle: { color: '#f97316', borderColor: '#fff', borderWidth: 2 },
          areaStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: 'rgba(249,115,22,0.18)' },
              { offset: 1, color: 'rgba(249,115,22,0.01)' }
            ])
          },
          label: { show: true, fontSize: 10, color: '#78716c' }
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
/**
 * 数值展示：整数不带小数，否则按精度保留并去掉尾随 0。
 *
 * @param precision 指标自己配的小数位（percentPrecision）。不传按 2 ——
 *        写死 2 位会把 3 位小数的指标（如 ‰ 类发病率）截断，
 *        而截断过的率比显示空更危险：从数字上看不出它被改过。
 */
function num(v, precision) {
  if (v === null || v === undefined || v === '') return '—'
  const n = Number(v)
  if (Number.isNaN(n)) return String(v)
  const p = Number.isInteger(precision) ? precision : 2
  if (p <= 0) return String(Math.round(n))
  return n.toFixed(p).replace(/0+$/, '').replace(/\.$/, '')
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
  const empty = !hasData(row)
  // 子行加淡底：整行缩进之外再给一层视觉层级，长表格里不容易看串行
  if (row.rowType === 'ATOM') return empty ? 'row-atom row-empty' : 'row-atom'
  return empty ? 'row-empty' : ''
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
/* ===================================================================
   质控月度汇总 · 暖橙主题（设计稿 quality-monthly.html 适配）
   =================================================================== */
.quality-monthly {
  padding: 24px;
  background: #fafaf9;
  min-height: 100vh;
}

.qb-card {
  background: #ffffff;
  border: 1px solid #e7e5e4;
  border-radius: 12px;
  box-shadow: 0 1px 2px rgba(28, 25, 23, 0.04);
}

.filter-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 20px;
  padding: 14px 18px;
}

.filter-left,
.filter-right {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
}

/* ---------------- 摘要卡（设计稿 stat-card 风格） ---------------- */
.metric-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 20px;
}

.metric-card {
  background: linear-gradient(180deg, #ffffff 0%, #fafaf9 100%);
  border: 1px solid #e7e5e4;
  border-radius: 12px;
  padding: 16px 18px 14px;
  transition: box-shadow 0.2s, transform 0.2s, border-color 0.2s;
}
.metric-card:hover {
  border-color: #d6d3d1;
  box-shadow: 0 4px 14px rgba(28, 25, 23, 0.06);
  transform: translateY(-1px);
}

.metric-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.metric-label {
  font-size: 13px;
  font-weight: 500;
  color: #78716c;
}

.metric-ico {
  width: 34px;
  height: 34px;
  border-radius: 8px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 17px;
  color: #78716c;
  background: #f5f5f4;
}
.metric-card.info .metric-ico {
  color: #0891b2;
  background: #cffafe;
}
.metric-card.ok .metric-ico {
  color: #16a34a;
  background: #dcfce7;
}
.metric-card:not(.info):not(.ok):not(.muted) .metric-ico {
  color: #d97706;
  background: #fef3c7;
}

.metric-value {
  font-size: 28px;
  font-weight: 700;
  color: #1c1917;
  line-height: 1.25;
  margin-top: 8px;
  font-variant-numeric: tabular-nums;
}

.metric-sub {
  font-size: 12px;
  color: #a8a29e;
  margin-top: 8px;
}

.chart-card,
.table-card {
  padding: 18px 20px;
  margin-bottom: 20px;
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
  margin-top: 12px;
}

.block-title {
  display: flex;
  align-items: center;
  font-size: 15px;
  font-weight: 600;
  color: #44403c;
  margin-bottom: 12px;
}

.dot {
  width: 4px;
  height: 16px;
  background: linear-gradient(180deg, #f97316, #fb923c);
  border-radius: 2px;
  margin-right: 8px;
  flex-shrink: 0;
}

.title-note {
  font-size: 12px;
  font-weight: 400;
  color: #a8a29e;
  margin-left: 10px;
}

.strong {
  font-weight: 700;
  color: #292524;
}

/* 年内最高月=暖红、最低月=绿（设计稿 error / success 色值） */
:deep(.month-max) {
  color: #dc2626;
  font-weight: 700;
}

:deep(.month-min) {
  color: #16a34a;
  font-weight: 700;
}

:deep(.row-empty) {
  color: #a8a29e;
}

/* 分子 / 分母子行：淡底，与主行拉开层次，长表格里不容易看串 */
:deep(.row-atom) {
  background: #fafaf9;
}

/* 类型标签：指标=蓝、分子/分母=中性灰，一眼分出层级 */
.type-tag {
  display: inline-block;
  padding: 0 6px;
  border-radius: 3px;
  font-size: 12px;
  line-height: 18px;
}

.type-tag.is-metric {
  color: #1d4ed8;
  background: #eff6ff;
}

.type-tag.is-atom {
  color: #57534e;
  background: #f5f5f4;
}

@media (max-width: 1200px) {
  .metric-cards {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 640px) {
  .quality-monthly {
    padding: 12px;
  }
  .metric-cards {
    grid-template-columns: 1fr;
  }
}
</style>
