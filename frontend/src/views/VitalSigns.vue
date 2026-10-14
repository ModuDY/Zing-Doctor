<template>
  <div class="page">
    <section class="head">
      <button class="back" @click="$router.back()">← 返回</button>
      <div class="head-main">
        <h2>生命体征趋势</h2>
        <p class="sub">
          {{ patientName }} {{ bedNo ? bedNo + '床' : '' }} · {{ inHospitalNo }}
          <span class="dot">·</span>{{ rangeText }}
        </p>
      </div>
      <div class="head-right">
        <el-radio-group v-model="hours" size="small" @change="load">
          <el-radio-button :label="6">近 6 小时</el-radio-button>
          <el-radio-button :label="24">近 24 小时</el-radio-button>
          <el-radio-button :label="72">近 3 天</el-radio-button>
          <el-radio-button :label="168">近 7 天</el-radio-button>
        </el-radio-group>
        <el-button size="small" :icon="refreshIcon" :loading="loading" @click="load">刷新</el-button>
      </div>
    </section>

    <p class="hint">
      <el-icon><InfoFilled /></el-icon>
      同一时间轴叠加对比：左轴血压(mmHg)，右轴血氧(%)与呼吸(次/分)各自独立量纲，不同单位不强行共轴。仅展示，不自动诊断。
    </p>

    <div v-if="truncated" class="truncated-tip">
      <el-icon><WarningFilled /></el-icon>
      {{ truncatedNote }}
    </div>

    <div v-loading="loading" class="panel">
      <div v-if="chartError" class="state">{{ chartError }}</div>
      <div v-else ref="chartEl" class="chart"></div>
    </div>

    <!-- 非 AVAILABLE 的指标单独列状态 -->
    <div v-if="nonAvail.length" class="status-strip">
      <span v-for="s in nonAvail" :key="s.key" class="status-tag"
            :class="s.dataStatus === 'UNKNOWN' ? 'bad' : 'muted'">
        {{ s.name }}：{{ s.dataStatus === 'UNKNOWN' ? '查询失败' : '窗口内无记录' }}
      </span>
    </div>

    <section class="table-block">
      <div class="block-head">
        <span class="block-title">原始记录</span>
        <span class="block-note">共 {{ recordCount || 0 }} 条（时间 / 项目 / 值 / 记录人）</span>
      </div>
      <el-table :data="records" size="small" max-height="380" empty-text="窗口内无记录">
        <el-table-column prop="time" label="时间" width="170" />
        <el-table-column prop="itemName" label="项目" min-width="120" />
        <el-table-column prop="value" label="值" width="90" />
        <el-table-column prop="itemCode" label="编码" width="110" />
        <el-table-column prop="staffName" label="记录人" min-width="90" />
      </el-table>
    </section>
  </div>
</template>

<script>
import { markRaw } from 'vue'
import { Refresh, InfoFilled, WarningFilled } from '@element-plus/icons-vue'
import * as echarts from '../utils/echarts'
import { fetchVitals } from '../api/workbench'

// 配色：以项目琥珀棕 #b45309 为主色，搭配临床监护常用的高区分度色，
// 每条固定一色，便于在同一时间轴上区分不同指标。
const COLORS = ['#b45309', '#0e7490', '#dc2626', '#2563eb', '#059669', '#7c3aed', '#ea580c', '#475569']

export default {
  name: 'VitalSigns',
  components: { Refresh, InfoFilled, WarningFilled },
  data() {
    return {
      // 图标必须作为实例属性暴露给模板：`:icon="Refresh"` 是属性查找，而不是组件解析，
      // 只写进 components 是拿不到的（Vue 会警告 Refresh is not defined on instance，
      // 表现为刷新按钮的图标位置空白）。其它页面同样用 refreshIcon 这种写法。
      // 图标是组件对象，必须 markRaw：否则被 Vue 变成响应式对象，触发
      // “Vue received a Component that was made a reactive object” 警告。
      refreshIcon: markRaw(Refresh),
      hours: 24,
      loading: false,
      patientName: '',
      bedNo: '',
      inHospitalNo: '',
      rangeStart: '',
      rangeEnd: '',
      series: [],
      records: [],
      recordCount: 0,
      truncated: false,
      truncatedNote: '',
      chart: null,
      chartError: ''
    }
  },
  computed: {
    patientId() { return this.$route.query.patientId },
    availSeries() {
      return this.series.filter((s) => s.dataStatus === 'AVAILABLE' && s.points && s.points.length)
    },
    nonAvail() {
      return this.series.filter((s) => s.dataStatus !== 'AVAILABLE')
    },
    rangeText() {
      if (!this.rangeStart) return ''
      const fmt = (t) => (t || '').replace('T', ' ').slice(5, 16)
      return fmt(this.rangeStart) + ' ~ ' + fmt(this.rangeEnd)
    }
  },
  watch: {
    availSeries: {
      deep: true,
      handler() { this.$nextTick(this.renderChart) }
    }
  },
  created() { this.load() },
  beforeUnmount() {
    if (this.chart) { this.chart.dispose(); this.chart = null }
  },
  methods: {
    async load() {
      if (!this.patientId) {
        this.$message.error('缺少 patientId 参数，请从患者工作台进入')
        return
      }
      this.loading = true
      this.chartError = ''
      try {
        const d = await fetchVitals(this.patientId, this.hours)
        this.patientName = d.patientName || ''
        this.bedNo = d.bedNo || ''
        this.inHospitalNo = d.inHospitalNo || ''
        this.rangeStart = d.rangeStart
        this.rangeEnd = d.rangeEnd
        this.series = d.series || []
        this.records = d.records || []
        this.recordCount = d.recordCount || 0
        this.truncated = !!d.truncated
        this.truncatedNote = d.truncatedNote || ''
        if (!this.availSeries.length) this.chartError = '窗口内无可用趋势数据'
      } catch (e) {
        this.$message.error('生命体征加载失败：' + ((e && e.message) || '请稍后重试'))
      } finally {
        this.loading = false
      }
    },
    // 按 unit 分组分配 y 轴：mmHg 左轴；其余每个不同 unit 一个右轴（offset 错开）
    buildAxes(list) {
      const groups = [] // [{unit, axisIndex}]
      list.forEach((s) => {
        const u = (s.unit || '').trim()
        let g = groups.find((x) => x.unit === u)
        if (!g) { g = { unit: u, items: [] }; groups.push(g) }
        g.items.push(s)
      })
      return groups
    },
    renderChart() {
      const el = this.$refs.chartEl
      if (!el || !this.availSeries.length) return
      // chartEl 因 v-if(chartError) 可能被销毁重建；DOM 变了必须 dispose 旧实例重新 init，
      // 否则 setOption 还写在脱离文档的旧 canvas 上，表现为“切时间后图没了”。
      if (this.chart && this.chart.getDom() !== el) {
        this.chart.dispose()
        this.chart = null
      }
      // 实例由 utils/echarts.js 统一 markRaw，放进 data() 也不会被 Vue 代理。
      if (!this.chart) this.chart = echarts.init(el)

      const groups = this.buildAxes(this.availSeries)
      // 第一个组（通常 mmHg）放左轴；其余依次放右轴，offset 错开
      const yAxes = []
      const axisOfSeries = {}
      groups.forEach((g, gi) => {
        const isLeft = gi === 0
        yAxes.push({
          type: 'value',
          scale: true,
          position: isLeft ? 'left' : 'right',
          offset: isLeft ? 0 : (gi - 1) * 46,
          name: g.unit || '',
          nameTextStyle: { color: '#78716c', fontSize: 10 },
          axisLabel: { color: '#a8a29e', fontSize: 10 },
          splitLine: { lineStyle: { color: '#f5f5f4' } }
        })
        g.items.forEach((s) => { axisOfSeries[s.key] = gi })
      })

      const eseries = this.availSeries.map((s, i) => ({
        name: s.name,
        type: 'line',
        yAxisIndex: axisOfSeries[s.key],
        showSymbol: true,
        symbolSize: 5,
        data: s.points.map((p) => [p.time, p.value]),
        lineStyle: { width: 2, color: COLORS[i % COLORS.length] },
        itemStyle: { color: COLORS[i % COLORS.length] }
      }))

      this.chart.setOption({
        backgroundColor: 'transparent',
        legend: {
          top: 0,
          data: this.availSeries.map((s) => s.name),
          textStyle: { fontSize: 11, color: '#57534e' }
        },
        grid: { left: 50, right: 60 + (groups.length - 1) * 46, top: 34, bottom: 30 },
        tooltip: {
          trigger: 'axis',
          axisPointer: { type: 'line' },
          confine: true
        },
        xAxis: {
          type: 'time',
          axisLabel: { color: '#a8a29e', fontSize: 10, hideOverlap: true },
          axisLine: { lineStyle: { color: '#e7e5e4' } }
        },
        yAxis: yAxes,
        series: eseries
      }, true)
      // 不要在这里补 resize()：数据窗口变了不等于容器尺寸变了，而 resize() 与上面的
      // setOption 在同一 tick 内连跑两次更新时，ECharts 会先 resetOption('media') 重建
      // 序列模型，新模型尚未回填坐标系就被渲染 → LineView.render 读 coordSys.type 抛
      // TypeError（每次切时间窗一条未捕获异常，线上实测）。
    }
  }
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
.truncated-tip { display: flex; align-items: center; gap: 6px; margin: 0 0 10px; padding: 8px 12px; font-size: 12px; color: #92400e; background: #fffbeb; border: 1px solid #fde68a; border-radius: 8px; }
.panel { background: #fff; border: 1px solid #eeece8; border-radius: 10px; padding: 10px 12px; }
.chart { width: 100%; height: 420px; }
.state { height: 420px; display: flex; align-items: center; justify-content: center; font-size: 12px; color: #a8a29e; }
.status-strip { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 12px; }
.status-tag { font-size: 11px; padding: 3px 10px; border-radius: 999px; background: #f5f5f4; color: #78716c; }
.status-tag.bad { background: #fef2f2; color: #b91c1c; }
.table-block { margin-top: 14px; background: #fff; border: 1px solid #eeece8; border-radius: 10px; padding: 12px 14px; }
.block-head { display: flex; align-items: baseline; gap: 10px; margin-bottom: 8px; }
.block-title { font-size: 13px; font-weight: 600; color: #1c1917; }
.block-note { font-size: 11px; color: #a8a29e; }
</style>
