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
        <!--
          打印预览：清单要求「所见即所得的打印 + 可调整打印参数」。
          预览窗口里就是最终打印件（同一份 DOM），导出 PDF 与打印都走它，
          避免「屏幕上排好的版、打印出来变样」。
        -->
        <el-button :disabled="!rows.length" @click="openPrintPreview">
          <el-icon><Printer /></el-icon> 打印预览
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
              :label="m.name"
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

    <!--
      打印预览（所见即所得）：预览里的 DOM 就是打印件本身 ——
      「导出 PDF」对它截图、「打印」把它的 HTML 原样写进打印窗口，
      因此屏幕上排成什么样，纸上/PDF 里就是什么样。
    -->
    <el-dialog
      v-model="printVisible"
      title="打印预览 · 质控指标月度监汇表"
      width="96%"
      top="3vh"
      class="qb-overlay"
      :close-on-click-modal="false"
    >
      <div class="print-params">
        <span class="pp-label">纸张方向</span>
        <el-radio-group v-model="printOrientation" size="small">
          <el-radio-button value="landscape">A4 横向</el-radio-button>
          <el-radio-button value="portrait">A4 纵向</el-radio-button>
        </el-radio-group>
        <span class="pp-label">每页行数</span>
        <el-input-number
          v-model="printRowsPerPage"
          :min="10"
          :max="60"
          :step="2"
          size="small"
          controls-position="right"
          style="width: 104px"
        />
        <el-checkbox v-model="printAtoms" size="small">含分子 / 分母子行</el-checkbox>
        <el-checkbox v-model="printChart" size="small">附年度趋势图</el-checkbox>
        <span class="pp-hint">共 {{ printPages.length }} 页 · 每页 {{ printRowsPerPage }} 行</span>
      </div>

      <div class="print-scroll">
        <div
          ref="printDocRef"
          class="print-doc"
          :class="printOrientation === 'portrait' ? 'is-portrait' : 'is-landscape'"
        >
          <div v-for="(page, pi) in printPages" :key="pi" class="doc-page">
            <div class="doc-head">
              <div class="doc-title">质控指标月度监汇表</div>
              <div class="doc-sub">
                {{ year }} 年度 · {{ departLabel }} · 生成时间 {{ generatedAt }}
              </div>
            </div>
            <img
              v-if="printChart && pi === 0 && chartImage"
              class="doc-chart"
              :src="chartImage"
              alt="年度趋势"
            />
            <table class="doc-table">
              <thead>
                <tr>
                  <th class="c-name">指标名称</th>
                  <th class="c-type">类型</th>
                  <th class="c-unit">单位</th>
                  <th v-for="h in header" :key="h" class="c-num">{{ h }}</th>
                  <th class="c-sum">全年合计</th>
                  <template v-if="isLandscape">
                    <th class="c-q">Q1</th>
                    <th class="c-q">Q2</th>
                    <th class="c-q">Q3</th>
                    <th class="c-q">Q4</th>
                    <th class="c-q">月均</th>
                    <th class="c-q">最高月</th>
                    <th class="c-q">最低月</th>
                  </template>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in page" :key="row.docKey" :class="{ 'is-atom': row.isChild }">
                  <td class="c-name">
                    <span v-if="row.isChild" class="indent">└ </span>{{ row.name }}
                  </td>
                  <td class="c-type">{{ row.isChild ? row.roleLabel || '原子项' : '指标' }}</td>
                  <td class="c-unit">{{ row.unit || '' }}</td>
                  <td v-for="(h, i) in header" :key="h" class="c-num">
                    {{ num(row.months ? row.months[i] : null, row.precision) }}
                  </td>
                  <td class="c-sum">{{ num(row.yearTotal, row.precision) }}</td>
                  <template v-if="isLandscape">
                    <td class="c-q">{{ num(row.q1, row.precision) }}</td>
                    <td class="c-q">{{ num(row.q2, row.precision) }}</td>
                    <td class="c-q">{{ num(row.q3, row.precision) }}</td>
                    <td class="c-q">{{ num(row.q4, row.precision) }}</td>
                    <td class="c-q">{{ num(row.yearAvg, row.precision) }}</td>
                    <td class="c-q">{{ row.maxMonth ? row.maxMonth + '月' : '—' }}</td>
                    <td class="c-q">{{ row.minMonth ? row.minMonth + '月' : '—' }}</td>
                  </template>
                </tr>
              </tbody>
            </table>
            <div class="doc-foot">
              <span>数据来源：质控指标中台（{{ departLabel }} · {{ year }}）</span>
              <span>第 {{ pi + 1 }} / {{ printPages.length }} 页</span>
            </div>
          </div>

          <el-empty v-if="!printPages.length" description="无可打印的指标数据" />
        </div>
      </div>

      <template #footer>
        <span class="pp-foot-hint">季度、月均与极值月的完整数值可在「导出 Excel」中查看</span>
        <el-button @click="printVisible = false">关闭</el-button>
        <el-button :loading="pdfExporting" :disabled="!printPages.length" @click="downloadPdf">
          <el-icon><Download /></el-icon> 导出 PDF
        </el-button>
        <el-button
          type="primary"
          :disabled="!printPages.length"
          @click="doPrint"
        >
          <el-icon><Printer /></el-icon> 打印
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Cpu, Download, Printer, Grid, CircleCheck, Collection, View } from '@element-plus/icons-vue'
import { jsPDF } from 'jspdf'
import html2canvas from 'html2canvas'
import { externalParam } from '../utils/external'
import { currentDepart } from '../utils/departContext'
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
const extDepartCode = externalParam('departCode')
const departCode = ref(extDepartCode || currentDepart.departCode)
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

// ============================================================================
// 打印 / PDF（清单：所见即所得打印 + 可调打印参数；导出 Excel、PDF）
//
// 为什么预览里自己排一份 DOM，而不是直接打印 el-table：
//   1) 表格设了 max-height，屏上只渲染可见行，直接打印会丢掉后面几十条指标；
//   2) 固定列（fixed="left"）打印时会与滚动区重叠，出现重影列。
// 所以打印件由这里重新分页渲染：页眉（标题 + 年度科室 + 生成时间）、可选趋势图、
// 每页重复表头、页脚带页码 —— 纸质报表本来也该是这个样子。
// ============================================================================
const printVisible = ref(false)
const printOrientation = ref('landscape')
const printRowsPerPage = ref(26)
const printAtoms = ref(true)
const printChart = ref(true)
const pdfExporting = ref(false)
const printDocRef = ref(null)
const generatedAt = ref('')
const chartImage = ref('')

const isLandscape = computed(() => printOrientation.value === 'landscape')

/**
 * 打印件的数据行：指标行 +（可选）它的分子 / 分母子行。
 *
 * 与 Excel 导出「月度汇总」sheet 同一口径（子行用 └ 缩进），
 * 否则会出现「纸上和导出文件对不上」这类要花半天核对的问题。
 */
const printRows = computed(() => {
  const list = []
  for (const r of displayRows.value) {
    list.push({ ...r, docKey: String(r.rowKey || r.code), isChild: false })
    if (!printAtoms.value) continue
    for (const c of r.children || []) {
      list.push({
        ...c,
        docKey: String(r.rowKey || r.code) + '::' + String(c.code || c.name),
        isChild: true
      })
    }
  }
  return list
})

/** 按「每页行数」切页。分页必须落在行边界上，否则一行会被纸张裁成两半 */
const printPages = computed(() => {
  const size = Math.max(1, Number(printRowsPerPage.value) || 26)
  const all = printRows.value
  const pages = []
  for (let i = 0; i < all.length; i += size) pages.push(all.slice(i, i + size))
  return pages
})

function openPrintPreview() {
  if (!rows.value.length) return
  generatedAt.value = new Date().toLocaleString('zh-CN', { hour12: false })
  chartImage.value = currentChartImage()
  printVisible.value = true
}

/** 取屏幕上趋势图的位图：纸面带图，避免「图上有的、纸上没有」 */
function currentChartImage() {
  try {
    if (!chart) return ''
    return chart.getDataURL({ type: 'png', pixelRatio: 2, backgroundColor: '#ffffff' })
  } catch (e) {
    console.warn('趋势图导出失败:', e.message || e)
    return ''
  }
}

function docFileName(ext) {
  return `质控指标月度监汇表_${year.value}_${departLabel.value}.${ext}`
}

/** 逐页截图 → 每页一张 A4（横向 297×210 / 纵向 210×297），行不跨页截断 */
async function downloadPdf() {
  const pages = printDocRef.value ? printDocRef.value.querySelectorAll('.doc-page') : []
  if (!pages.length) return
  pdfExporting.value = true
  try {
    const pdf = new jsPDF('l', 'mm', 'a4')
    const landscape = isLandscape.value
    const pageW = landscape ? 297 : 210
    const pageH = landscape ? 210 : 297
    const margin = 6
    for (let i = 0; i < pages.length; i++) {
      const canvas = await html2canvas(pages[i], {
        scale: 2,
        useCORS: true,
        backgroundColor: '#ffffff',
        logging: false
      })
      const maxW = pageW - margin * 2
      const maxH = pageH - margin * 2
      let imgW = maxW
      let imgH = (canvas.height * imgW) / canvas.width
      if (imgH > maxH) {
        imgH = maxH
        imgW = (canvas.width * imgH) / canvas.height
      }
      if (i > 0) pdf.addPage()
      pdf.addImage(
        canvas.toDataURL('image/jpeg', 0.9),
        'JPEG',
        (pageW - imgW) / 2,
        margin,
        imgW,
        imgH
      )
    }
    pdf.save(docFileName('pdf'))
    ElMessage.success(`已导出 PDF（${pages.length} 页）`)
  } catch (e) {
    ElMessage.error('导出 PDF 失败：' + (e.message || e))
  } finally {
    pdfExporting.value = false
  }
}

/**
 * 浏览器打印：把预览件的 HTML 写进新窗口，并带上当前页面的样式表。
 *
 * 带样式表而不是另手写一份 CSS：手写的那份必然与屏幕样式漂移，
 * 「所见即所得」就成了一句空话。scoped 样式的属性选择器在克隆节点上照样命中。
 */
function doPrint() {
  const el = printDocRef.value
  if (!el) return
  const w = window.open('', '_blank')
  if (!w) {
    ElMessage.warning('浏览器拦截了打印窗口，请允许本站弹窗后重试')
    return
  }
  const size = isLandscape.value ? 'A4 landscape' : 'A4 portrait'
  const doc = w.document
  doc.write(
    '<!DOCTYPE html><html><head><meta charset="utf-8"><title>' +
      docFileName('pdf') +
      '</title></head><body></body></html>'
  )
  doc.close()
  // 整份样式表克隆过去（含 scoped），保证纸面与屏幕一致
  document.querySelectorAll('style, link[rel="stylesheet"]').forEach((node) => {
    doc.head.appendChild(node.cloneNode(true))
  })
  const extra = doc.createElement('style')
  extra.textContent = `
    @page { size: ${size}; margin: 8mm; }
    body { margin: 0; background: #fff; }
    .print-scroll { max-height: none !important; overflow: visible !important; padding: 0 !important; background: #fff !important; }
    .doc-page { border: none !important; margin: 0 !important; page-break-after: always; break-after: page; }
    .doc-page:last-child { page-break-after: auto; break-after: auto; }
    .doc-chart { max-height: 62mm; }
  `
  doc.head.appendChild(extra)
  const host = doc.createElement('div')
  host.className = 'print-doc ' + (isLandscape.value ? 'is-landscape' : 'is-portrait')
  host.innerHTML = el.innerHTML
  doc.body.appendChild(host)
  w.focus()
  setTimeout(() => w.print(), 400)
}
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

/* ===================================================================
   打印预览 / PDF（一份 DOM 同时供给屏幕预览、jsPDF 截图与浏览器打印）
   =================================================================== */
.print-params {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}

.pp-label {
  font-size: 13px;
  color: #78716c;
}

.pp-hint {
  font-size: 12px;
  color: #a8a29e;
  margin-left: auto;
}

.pp-foot-hint {
  float: left;
  font-size: 12px;
  color: #a8a29e;
  line-height: 32px;
}

.print-scroll {
  max-height: 68vh;
  overflow: auto;
  background: #f5f5f4;
  padding: 14px;
  border-radius: 8px;
}

.print-doc {
  width: 1122px;
  margin: 0 auto;
}

.print-doc.is-portrait {
  width: 794px;
}

/* 一页 = 一张 A4（96dpi：横向 1122×793、纵向 794×1123）。
   行高与「每页行数」匹配，行边界落在分页处，不会出现被裁成两半的行。 */
.doc-page {
  position: relative;
  width: 1122px;
  min-height: 793px;
  padding: 20px 22px 34px;
  box-sizing: border-box;
  background: #ffffff;
  border: 1px solid #e7e5e4;
  border-radius: 4px;
  margin-bottom: 16px;
  font-family: 'Microsoft YaHei', 'PingFang SC', sans-serif;
  color: #1c1917;
}

.is-portrait .doc-page {
  width: 794px;
  min-height: 1123px;
  padding: 24px 18px 34px;
}

.doc-head {
  border-bottom: 2px solid #f97316;
  padding-bottom: 7px;
  margin-bottom: 10px;
}

.doc-title {
  font-size: 19px;
  font-weight: 700;
  text-align: center;
  letter-spacing: 1px;
}

.doc-sub {
  font-size: 11px;
  color: #78716c;
  text-align: center;
  margin-top: 4px;
}

.doc-chart {
  display: block;
  width: 100%;
  max-height: 220px;
  object-fit: contain;
  margin: 0 0 10px;
}

.doc-table {
  width: 100%;
  border-collapse: collapse;
  table-layout: fixed;
  font-size: 11px;
}

.doc-table th,
.doc-table td {
  border: 1px solid #d6d3d1;
  padding: 3px 4px;
  line-height: 1.35;
  overflow: hidden;
  white-space: nowrap;
}

.doc-table th {
  background: #f5f5f4;
  font-weight: 600;
  text-align: center;
}

.doc-table tr.is-atom td {
  background: #fafaf9;
  color: #57534e;
}

.doc-table .indent {
  color: #a8a29e;
}

/* 列宽写死而非自适应：24 列的表在 A4 上必须精确分配，否则数值列会被挤到换行 */
.c-name {
  width: 230px;
  text-align: left;
}

.c-type {
  width: 40px;
  text-align: center;
}

.c-unit {
  width: 32px;
  text-align: center;
}

.c-num {
  width: 36px;
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.c-sum {
  width: 54px;
  text-align: right;
  font-weight: 700;
}

.c-q {
  width: 36px;
  text-align: right;
  font-variant-numeric: tabular-nums;
}

/* 纵向纸张放不下「类型 / 单位 / 季度」这些辅助列，只留指标名称 + 12 个月 + 全年合计 */
.is-portrait .c-type,
.is-portrait .c-unit {
  display: none;
}

.is-portrait .c-name {
  width: 240px;
}

.is-portrait .c-num {
  width: 42px;
}

.is-portrait .c-sum {
  width: 62px;
}

.doc-foot {
  position: absolute;
  left: 22px;
  right: 22px;
  bottom: 10px;
  display: flex;
  justify-content: space-between;
  font-size: 10px;
  color: #a8a29e;
}

.is-portrait .doc-foot {
  left: 18px;
  right: 18px;
}
</style>
