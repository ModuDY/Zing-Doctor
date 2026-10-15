<template>
  <div class="infection-watch qb-theme">
    <section class="page-header">
      <div class="patient-line">
        <span class="p-name">{{ patientName || '未指定患者' }}</span>
        <span v-if="bedNo" class="p-bed">{{ bedNo }} 床</span>
        <span v-if="inHospitalNo" class="p-no">住院号 {{ inHospitalNo }}</span>
      </div>
      <div class="header-right">
        <el-radio-group v-model="hours" size="small" @change="load">
          <el-radio-button :label="6">6h</el-radio-button>
          <el-radio-button :label="24">24h</el-radio-button>
          <el-radio-button :label="72">3天</el-radio-button>
          <el-radio-button :label="168">7天</el-radio-button>
        </el-radio-group>
        <el-button size="small" :loading="loading" @click="load">刷新</el-button>
      </div>
    </section>

    <div v-loading="loading" class="page-body">
      <el-empty v-if="!patientId" description="缺少 patientId 参数，请从患者工作台进入" />
      <template v-else>
        <!-- 体温：发热是院感最基础的信号，单独成块 -->
        <section class="watch-card">
          <div class="card-head">
            <h2>体温</h2>
            <span :class="['st-badge', temp ? 'st-ok' : '']">{{ temp ? '有数据' : '窗口内无记录' }}</span>
          </div>
          <div v-if="temp" ref="tempChart" class="chart temp-chart"></div>
          <div v-else class="chart-empty">窗口内无体温记录</div>
        </section>

        <!-- 感染指标：WBC / PCT / CRP / IL-6 -->
        <section class="watch-card">
          <div class="card-head">
            <h2>感染指标（WBC / PCT / CRP / IL-6）</h2>
            <span v-if="labRecordCount" class="muted">共 {{ labRecordCount }} 条</span>
          </div>
          <div v-if="labSeries.length" ref="labChart" class="chart lab-chart"></div>
          <div v-else class="chart-empty">窗口内无感染指标检验记录</div>
          <el-table v-if="labRecords.length" :data="labRecords" size="small" stripe max-height="300" style="margin-top: 12px">
            <el-table-column label="时间" width="150">
              <template #default="{ row }">{{ fmt(row.time) }}</template>
            </el-table-column>
            <el-table-column prop="itemName" label="项目" min-width="160" show-overflow-tooltip />
            <el-table-column prop="value" label="结果" width="90" />
            <el-table-column label="参考范围" min-width="120">
              <template #default="{ row }">
                {{ row.refText || (row.refLow != null && row.refHigh != null ? row.refLow + ' ~ ' + row.refHigh : '—') }}
              </template>
            </el-table-column>
          </el-table>
        </section>

        <p class="foot-note">
          感染指标按项目名称匹配（生产库同一项目存在多套编码）；尿液 / 体液标本白细胞已排除，
          仅取血液口径。本页只呈现事实与趋势，不由趋势直接输出「院感 / 肺炎」等诊断结论 ——
          确诊请走「临床分析 → 导管相关感染确诊」等医生录入流程。
        </p>
      </template>
    </div>
  </div>
</template>

<script>
import * as echarts from '../utils/echarts'
import { fetchVitals, fetchLabTrend } from '../api/workbench'

const LAB_COLORS = ['#2563eb', '#dc2626', '#059669', '#d97706', '#7c3aed']

export default {
  name: 'InfectionWatch',
  data() {
    return {
      hours: 24,
      loading: false,
      patientName: '',
      bedNo: '',
      inHospitalNo: '',
      temp: null,          // 体温 series（key=temp）或 null
      labSeries: [],       // 可用的检验 series
      labRecords: [],
      labRecordCount: 0,
      charts: {}
    }
  },
  computed: {
    patientId() { return this.$route.query.patientId }
  },
  watch: {
    '$route.fullPath'() { this.load() }
  },
  created() { this.load() },
  beforeUnmount() {
    window.removeEventListener('resize', this.handleResize)
    Object.values(this.charts).forEach((c) => { if (c) c.dispose() })
    this.charts = {}
  },
  mounted() {
    window.addEventListener('resize', this.handleResize)
  },
  methods: {
    async load() {
      if (!this.patientId) return
      this.loading = true
      try {
        // 体温与感染指标两路独立请求：一路失败不拖垮另一路
        const [vitals, labs] = await Promise.allSettled([
          fetchVitals(this.patientId, this.hours),
          fetchLabTrend(this.patientId, 'infection', this.hours)
        ])
        if (vitals.status === 'fulfilled' && vitals.value) {
          const d = vitals.value
          this.patientName = d.patientName || ''
          this.bedNo = d.bedNo || ''
          this.inHospitalNo = d.inHospitalNo || ''
          this.temp = (d.series || []).find((s) => s.key === 'temp') || null
        } else {
          console.error('[InfectionWatch] vitals failed', vitals.reason)
          this.temp = null
        }
        if (labs.status === 'fulfilled' && labs.value) {
          const d = labs.value
          if (!this.patientName) {
            this.patientName = d.patientName || ''
            this.inHospitalNo = d.inHospitalNo || ''
          }
          this.labSeries = (d.groups || [])
            .flatMap((g) => g.series || [])
            .filter((s) => s.dataStatus === 'AVAILABLE' && s.points && s.points.length)
          this.labRecords = d.records || []
          this.labRecordCount = d.recordCount || 0
        } else {
          console.error('[InfectionWatch] labs failed', labs.reason)
          this.labSeries = []
          this.labRecords = []
          this.labRecordCount = 0
        }
        await this.$nextTick()
        this.renderCharts()
      } finally {
        this.loading = false
      }
    },
    handleResize() {
      Object.values(this.charts).forEach((c) => { if (c) c.resize() })
    },
    /** 体温单序列图 */
    renderTemp() {
      const el = this.$refs.tempChart
      if (!el || !this.temp) return
      const c = this.charts.temp && this.charts.temp.getDom() === el ? this.charts.temp : echarts.init(el)
      this.charts.temp = c
      c.setOption({
        backgroundColor: 'transparent',
        grid: { left: 44, right: 20, top: 30, bottom: 30 },
        tooltip: { trigger: 'axis', confine: true },
        xAxis: { type: 'time', axisLabel: { color: '#a8a29e', fontSize: 10, hideOverlap: true } },
        yAxis: {
          type: 'value', scale: true, name: '℃',
          nameTextStyle: { color: '#78716c', fontSize: 10 },
          axisLabel: { color: '#a8a29e', fontSize: 10 },
          splitLine: { lineStyle: { color: '#f5f5f4' } }
        },
        series: [{
          name: '体温', type: 'line', showSymbol: true, symbolSize: 5,
          data: this.temp.points.map((p) => [p.time, p.value]),
          lineStyle: { width: 2, color: '#dc2626' },
          itemStyle: { color: '#dc2626' },
          markLine: {
            silent: true, symbol: 'none',
            data: [{ yAxis: 38, label: { formatter: '38℃', color: '#b45309' } }],
            lineStyle: { color: '#f59e0b', type: 'dashed' }
          }
        }]
      }, true)
    },
    /** 感染指标多序列图：单位不同的指标各占一条 y 轴（与 VitalSigns 同套路） */
    renderLabs() {
      const el = this.$refs.labChart
      if (!el || !this.labSeries.length) return
      const c = this.charts.labs && this.charts.labs.getDom() === el ? this.charts.labs : echarts.init(el)
      this.charts.labs = c

      // 按 unit 分组分配 y 轴
      const groups = []
      this.labSeries.forEach((s) => {
        const u = (s.unit || '').trim()
        let g = groups.find((x) => x.unit === u)
        if (!g) { g = { unit: u, items: [] }; groups.push(g) }
        g.items.push(s)
      })
      const yAxes = []
      const axisOf = {}
      groups.forEach((g, gi) => {
        const isLeft = gi === 0
        yAxes.push({
          type: 'value', scale: true,
          position: isLeft ? 'left' : 'right',
          offset: isLeft ? 0 : (gi - 1) * 46,
          name: g.unit || '',
          nameTextStyle: { color: '#78716c', fontSize: 10 },
          axisLabel: { color: '#a8a29e', fontSize: 10 },
          splitLine: { lineStyle: { color: '#f5f5f4' } }
        })
        g.items.forEach((s) => { axisOf[s.name] = gi })
      })

      c.setOption({
        backgroundColor: 'transparent',
        legend: { top: 0, textStyle: { fontSize: 11, color: '#57534e' } },
        grid: { left: 50, right: 60 + Math.max(0, groups.length - 1) * 46, top: 34, bottom: 30 },
        tooltip: { trigger: 'axis', confine: true },
        xAxis: { type: 'time', axisLabel: { color: '#a8a29e', fontSize: 10, hideOverlap: true } },
        yAxis: yAxes,
        series: this.labSeries.map((s, i) => ({
          name: s.name, type: 'line', showSymbol: true, symbolSize: 5,
          yAxisIndex: axisOf[s.name] || 0,
          data: s.points.map((p) => [p.time, p.value]),
          lineStyle: { width: 2, color: LAB_COLORS[i % LAB_COLORS.length] },
          itemStyle: { color: LAB_COLORS[i % LAB_COLORS.length] }
        }))
      }, true)
    },
    renderCharts() {
      try { this.renderTemp() } catch (e) { console.error('[InfectionWatch] temp chart', e) }
      try { this.renderLabs() } catch (e) { console.error('[InfectionWatch] lab chart', e) }
    },
    fmt(t) { return (t || '').replace('T', ' ').slice(0, 16) }
  }
}
</script>

<style scoped>
.temp-chart { height: 260px; }
.lab-chart { height: 260px; }
.infection-watch { padding: 16px; }
.page-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14px; flex-wrap: wrap; gap: 10px; }
.patient-line { display: flex; align-items: baseline; gap: 10px; }
.p-name { font-size: 17px; font-weight: 700; color: #1c1917; }
.p-bed, .p-no { color: #78716c; font-size: 13px; }
.header-right { display: flex; align-items: center; gap: 10px; }
.watch-card { border: 1px solid #e7e5e4; border-radius: 12px; background: #fff; padding: 14px 18px 16px; margin-bottom: 16px; }
.card-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 10px; }
.card-head h2 { margin: 0; font-size: 14px; font-weight: 700; color: #1c1917; }
.muted { color: #a8a29e; font-size: 12px; }
.st-badge { font-size: 11px; padding: 1px 8px; border-radius: 10px; background: #f5f5f4; color: #a8a29e; }
.st-badge.st-ok { background: #f0f9f3; color: #15803d; }
.chart { width: 100%; }
.chart-empty { padding: 40px 0; text-align: center; color: #a8a29e; font-size: 13px; }
.foot-note { color: #a8a29e; font-size: 12px; margin: 4px 2px 0; }
</style>
