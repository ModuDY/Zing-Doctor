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
        <el-button size="small" :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      </div>
    </section>

    <p class="hint">
      <el-icon><InfoFilled /></el-icon>
      数据回顾工具：仅展示监测记录，不自动诊断、不做早期预警评分。血压按无创/有创分列，不合并。
    </p>

    <div v-loading="loading" class="charts">
      <div v-for="s in series" :key="s.key" class="chart-card">
        <div class="chart-head">
          <span class="chart-title">{{ s.name }}</span>
          <span class="chart-meta">{{ s.unit }}{{ s.source && s.source !== '未标明' ? ' · ' + s.source : '' }}</span>
        </div>
        <div v-if="s.dataStatus === 'UNKNOWN'" class="state">查询失败：{{ s.note || '请稍后重试' }}</div>
        <div v-else-if="s.dataStatus === 'NO_RECORD'" class="state">窗口内无记录</div>
        <div v-else :ref="'chart-' + s.key" class="chart"></div>
      </div>
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
import { Refresh, InfoFilled } from '@element-plus/icons-vue'
import * as echarts from '../utils/echarts'
import { fetchVitals } from '../api/workbench'

export default {
  name: 'VitalSigns',
  components: { Refresh, InfoFilled },
  data() {
    return {
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
      charts: {}
    }
  },
  computed: {
    patientId() {
      return this.$route.query.patientId
    },
    rangeText() {
      if (!this.rangeStart) return ''
      const fmt = (t) => (t || '').replace('T', ' ').slice(5, 16)
      return fmt(this.rangeStart) + ' ~ ' + fmt(this.rangeEnd)
    }
  },
  watch: {
    series() {
      this.$nextTick(this.renderCharts)
    }
  },
  created() {
    this.load()
  },
  beforeUnmount() {
    Object.values(this.charts).forEach((c) => c && c.dispose())
    this.charts = {}
  },
  methods: {
    async load() {
      if (!this.patientId) {
        this.$message.error('缺少 patientId 参数，请从患者工作台进入')
        return
      }
      this.loading = true
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
      } catch (e) {
        this.$message.error('生命体征加载失败：' + ((e && e.message) || '请稍后重试'))
      } finally {
        this.loading = false
      }
    },
    renderCharts() {
      this.series.forEach((s) => {
        if (s.dataStatus !== 'AVAILABLE' || !s.points || !s.points.length) return
        const el = this.$refs['chart-' + s.key]
        if (!el) return
        const chart = echarts.getInstanceByDom(el) || echarts.init(el)
        this.charts[s.key] = chart
        chart.clear()
        chart.setOption({
          backgroundColor: 'transparent',
          grid: { left: 46, right: 14, top: 12, bottom: 20 },
          tooltip: {
            trigger: 'axis',
            confine: true,
            formatter: (ps) => {
              if (!ps || !ps.length) return ''
              const p = ps[0]
              const t = String(p.axisValue).slice(5, 16).replace('T', ' ')
              return t + '<br/>' + s.name + '：<b>' + p.value[1] + '</b> ' + (s.unit || '')
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
            lineStyle: { width: 2, color: '#b45309' },
            itemStyle: { color: '#b45309' },
            areaStyle: { color: 'rgba(180,83,9,0.06)' }
          }]
        })
        chart.resize()
      })
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
.charts { display: grid; grid-template-columns: repeat(auto-fill, minmax(340px, 1fr)); gap: 12px; }
.chart-card { background: #fff; border: 1px solid #eeece8; border-radius: 10px; padding: 10px 12px; }
.chart-head { display: flex; align-items: baseline; justify-content: space-between; margin-bottom: 4px; }
.chart-title { font-size: 13px; font-weight: 600; color: #1c1917; }
.chart-meta { font-size: 10px; color: #a8a29e; }
.chart { width: 100%; height: 180px; }
.state { height: 180px; display: flex; align-items: center; justify-content: center; font-size: 12px; color: #a8a29e; }
.table-block { margin-top: 14px; background: #fff; border: 1px solid #eeece8; border-radius: 10px; padding: 12px 14px; }
.block-head { display: flex; align-items: baseline; gap: 10px; margin-bottom: 8px; }
.block-title { font-size: 13px; font-weight: 600; color: #1c1917; }
.block-note { font-size: 11px; color: #a8a29e; }
</style>
