<template>
  <div class="page">
    <section class="head">
      <button class="back" @click="$router.back()">← 返回</button>
      <div class="head-main">
        <h2>{{ panelTitle }}</h2>
        <p class="sub">
          {{ patientName }} · {{ inHospitalNo }}
          <span class="dot">·</span>{{ rangeText }}
        </p>
      </div>
      <div class="head-right">
        <el-radio-group v-model="hours" size="small" @change="load">
          <el-radio-button :label="24">近 24 小时</el-radio-button>
          <el-radio-button :label="168">近 7 天</el-radio-button>
          <el-radio-button :label="720">近 30 天</el-radio-button>
        </el-radio-group>
        <el-button size="small" :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      </div>
    </section>

    <p class="hint">
      <el-icon><InfoFilled /></el-icon>
      {{ panelHint }}
    </p>

    <div v-loading="loading" class="groups">
      <div v-for="(g, gi) in groups" :key="gi" class="group-card">
        <div class="group-title">{{ g.name }}</div>
        <div class="group-grid">
          <div v-for="(s, si) in g.series" :key="si" class="chart-card">
            <div class="chart-head">
              <span class="chart-title">{{ s.name }}</span>
            </div>
            <div v-if="s.dataStatus === 'UNKNOWN'" class="state">查询失败</div>
            <div v-else-if="s.dataStatus === 'NO_RECORD'" class="state">窗口内无记录</div>
            <div v-else :ref="'chart-' + gi + '-' + si" class="chart"></div>
          </div>
        </div>
      </div>
    </div>

    <section class="table-block">
      <div class="block-head">
        <span class="block-title">检验明细</span>
        <span class="block-note">共 {{ recordCount || 0 }} 条（检验时间 / 项目 / 结果 / 参考范围）</span>
      </div>
      <el-table :data="records" size="small" max-height="380" empty-text="窗口内无记录">
        <el-table-column prop="time" label="检验时间" width="175" :formatter="(r, c, v) => fmtDateTime(v)" />
        <el-table-column prop="itemName" label="项目" min-width="150" show-overflow-tooltip />
        <el-table-column prop="value" label="结果" width="90" />
        <el-table-column prop="refText" label="参考范围" width="130">
          <template #default="{ row }">{{ refText(row) }}</template>
        </el-table-column>
        <el-table-column prop="alarmFlag" label="标记" width="70" />
      </el-table>
      <p class="foot-note">「谷草/谷丙」组合项（单字段双值）未纳入 —— 拆分规则未经临床确认。</p>
    </section>
  </div>
</template>

<script>
import { Refresh, InfoFilled } from '@element-plus/icons-vue'
import * as echarts from '../utils/echarts'
import { fetchLabTrend } from '../api/workbench'
import { fmtDateTime } from '../utils/datetime'

/**
 * 三个面板共用本组件，标题与提示集中在这里维护。
 *
 * 提示语都写清「不由趋势直接下结论」：检验趋势是给临床看数值走向的，
 * 页面自己给出诊断性措辞（肝衰竭 / AKI）会越权，且与工作台的规则判定重复。
 */
const PANELS = {
  liver: {
    title: '肝功能趋势',
    hint: '展示肝功能检验历史结果与参考范围；不由趋势直接诊断肝衰竭。按项目名称匹配（生产库存在同一项目多编码，如 ALT 两套）。'
  },
  coag: {
    title: '凝血功能趋势',
    hint: '展示凝血检验历史结果；不自动输出 DIC / 出血风险结论。D-二聚体仅 ICU 床边项目，覆盖有限。'
  },
  renal: {
    title: '肾功能与电解质趋势',
    hint: '展示肌酐 / 尿素氮与钾钠氯钙镁磷的历史结果与参考范围；不由趋势直接诊断 AKI。已排除同名异物（降钙素原、脑钠肽、肌钙蛋白、尿素/肌酐比值、尿液标本）。'
  }
}

export default {
  name: 'LabTrend',
  components: { Refresh, InfoFilled },
  data() {
    return {
      hours: 168,
      loading: false,
      patientName: '',
      inHospitalNo: '',
      rangeStart: '',
      rangeEnd: '',
      groups: [],
      records: [],
      recordCount: 0,
      charts: {}
    }
  },
  computed: {
    patientId() {
      return this.$route.query.patientId
    },
    /** 路由 → 面板类型：三个面板共用本组件，类型只由路由决定 */
    panel() {
      const name = this.$route.name || ''
      if (name === 'labCoag') return 'coag'
      if (name === 'labRenal') return 'renal'
      return 'liver'
    },
    panelTitle() {
      return PANELS[this.panel].title
    },
    panelHint() {
      return PANELS[this.panel].hint
    },
    rangeText() {
      if (!this.rangeStart) return ''
      const fmt = (t) => (t || '').replace('T', ' ').slice(5, 16)
      return fmt(this.rangeStart) + ' ~ ' + fmt(this.rangeEnd)
    }
  },
  watch: {
    // 肝功能 / 凝血 / 肾功能是三条路由（/page/lab-liver、/page/lab-coag、/page/lab-renal）
    // 指向同一个组件，Vue Router 会复用实例：切换菜单时 created 不再执行 → load() 不被调用 →
    // 页面一直停在上一类检验的结果上（只有依赖 $route 的标题与提示会变，数据不换，
    // 看着就像「点了菜单没反应」）。同一组件换 patientId 是同一类问题，故盯 fullPath。
    '$route.fullPath'() {
      this.disposeCharts()
      this.groups = []
      this.records = []
      this.recordCount = 0
      this.load()
    },
    groups() {
      this.$nextTick(this.renderCharts)
    }
  },
  created() {
    this.load()
  },
  beforeUnmount() {
    this.disposeCharts()
  },
  methods: {
    fmtDateTime(t) { return fmtDateTime(t) },
    async load() {
      if (!this.patientId) {
        this.$message.error('缺少 patientId 参数，请从患者工作台进入')
        return
      }
      this.loading = true
      try {
        const d = await fetchLabTrend(this.patientId, this.panel, this.hours)
        this.patientName = d.patientName || ''
        this.inHospitalNo = d.inHospitalNo || ''
        this.rangeStart = d.rangeStart
        this.rangeEnd = d.rangeEnd
        this.groups = d.groups || []
        this.records = d.records || []
        this.recordCount = d.recordCount || 0
      } catch (e) {
        this.$message.error('检验趋势加载失败：' + ((e && e.message) || '请稍后重试'))
      } finally {
        this.loading = false
      }
    },
    refText(row) {
      if (row.refText) return row.refText
      if (row.refLow != null && row.refHigh != null) return row.refLow + ' ~ ' + row.refHigh
      if (row.refLow != null) return '≥ ' + row.refLow
      if (row.refHigh != null) return '≤ ' + row.refHigh
      return '—'
    },
    /**
     * 销毁全部图表实例。
     *
     * 切换检验类型 / 患者前必须先销毁：两类检验的分组数与卡片数不同（肝功能 4 组 8 图、
     * 凝血 1 组 6 图），而 charts 是按 `gi-si` 索引的，不清理会把旧实例错配到新卡片上。
     */
    disposeCharts() {
      Object.values(this.charts).forEach((c) => c && c.dispose())
      this.charts = {}
    },
    renderCharts() {
      this.groups.forEach((g, gi) => {
        (g.series || []).forEach((s, si) => {
          if (s.dataStatus !== 'AVAILABLE' || !s.points || !s.points.length) return
          // v-for 内的 ref 在 Vue 3 里会被收集成数组，直接塞给 echarts.init 会报
          // 「this.dom.getContext is not a function」（卡片渲染了但图是空的）
          const raw = this.$refs['chart-' + gi + '-' + si]
          const el = Array.isArray(raw) ? raw[0] : raw
          if (!el) return
          const chart = echarts.getInstanceByDom(el) || echarts.init(el)
          this.charts[gi + '-' + si] = chart
          chart.clear()
          chart.setOption({
            backgroundColor: 'transparent',
            grid: { left: 52, right: 14, top: 12, bottom: 20 },
            tooltip: {
              trigger: 'axis',
              confine: true,
              formatter: (ps) => {
                if (!ps || !ps.length) return ''
                const p = ps[0]
                const t = fmtDateTime(p.axisValue)
                return t + '<br/>' + s.name + '：<b>' + p.value[1] + '</b>'
              }
            },
            xAxis: {
              type: 'time',
              axisLabel: { color: '#a8a29e', fontSize: 10, hideOverlap: true },
              axisLine: { lineStyle: { color: '#e7e5e4' } },
              splitLine: { show: false }
            },
            yAxis: {
              type: 'value',
              scale: true,
              axisLabel: { color: '#a8a29e', fontSize: 10 },
              splitLine: { lineStyle: { color: '#f5f5f4' } }
            },
            series: [{
              type: 'line',
              showSymbol: true,
              symbolSize: 5,
              data: s.points.map((p) => [p.time, p.value]),
              lineStyle: { width: 2, color: '#0e7490' },
              itemStyle: { color: '#0e7490' },
              markLine: refLine(s),
              areaStyle: { color: 'rgba(14,116,144,0.05)' }
            }]
          })
          chart.resize()
        })
      })
    }
  }
}

/** 有参考范围时画一条上/下限虚线（仅作事实展示，不是诊断阈值） */
function refLine(s) {
  const pts = s.points || []
  if (!pts.length) return undefined
  const withRef = pts.filter((p) => p.refLow != null || p.refHigh != null)
  if (!withRef.length) return undefined
  const lines = []
  const hi = withRef.find((p) => p.refHigh != null)
  const lo = withRef.find((p) => p.refLow != null)
  if (hi) lines.push({ yAxis: Number(hi.refHigh), lineStyle: { color: '#d6d3d1', type: 'dashed', width: 1 }, label: { show: false } })
  if (lo) lines.push({ yAxis: Number(lo.refLow), lineStyle: { color: '#d6d3d1', type: 'dashed', width: 1 }, label: { show: false } })
  return { silent: true, symbol: 'none', data: lines }
}
</script>

<style scoped>
.page { padding: 16px 20px 28px; }
.head { display: flex; align-items: flex-start; gap: 14px; flex-wrap: wrap; margin-bottom: 10px; }
.back { border: none; background: none; color: #b45309; cursor: pointer; font-size: 13px; }
.back:hover { text-decoration: underline; }
.head-main { flex: 1; min-width: 220px; }
.head-main h2 { margin: 0; font-size: 18px; color: #1c1917; }
.sub { margin: 4px 0 0; font-size: 12px; color: #78716c; }
.dot { margin: 0 4px; color: #d6d3d1; }
.head-right { display: flex; align-items: center; gap: 8px; }
.hint { display: flex; align-items: center; gap: 5px; margin: 0 0 12px; font-size: 11px; color: #a8a29e; }
.groups { display: flex; flex-direction: column; gap: 12px; }
.group-card { background: #fff; border: 1px solid #eeece8; border-radius: 10px; padding: 12px 14px; }
.group-title { font-size: 13px; font-weight: 600; color: #1c1917; margin-bottom: 8px; }
.group-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(320px, 1fr)); gap: 10px; }
.chart-card { border: 1px solid #f5f4f1; border-radius: 8px; padding: 8px 10px; }
.chart-head { display: flex; align-items: baseline; justify-content: space-between; }
.chart-title { font-size: 12px; font-weight: 600; color: #292524; }
.chart { width: 100%; height: 170px; }
.state { height: 170px; display: flex; align-items: center; justify-content: center; font-size: 12px; color: #a8a29e; }
.table-block { margin-top: 14px; background: #fff; border: 1px solid #eeece8; border-radius: 10px; padding: 12px 14px; }
.block-head { display: flex; align-items: baseline; gap: 10px; margin-bottom: 8px; }
.block-title { font-size: 13px; font-weight: 600; color: #1c1917; }
.block-note { font-size: 11px; color: #a8a29e; }
.foot-note { margin: 8px 0 0; font-size: 10px; color: #a8a29e; }
</style>
