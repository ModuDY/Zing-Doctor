<template>
  <div class="multi-trend">
    <div v-if="!hasData" class="empty" :style="{ height: Math.max(120, panels.length * 60) + 'px' }">
      窗口内无可用趋势数据
    </div>
    <div v-show="hasData" ref="chartEl" class="chart" :style="{ height: chartHeight + 'px' }"></div>
  </div>
</template>

<script>
import * as echarts from '../utils/echarts'

/**
 * 多指标共享时间轴分面图。
 * props.series: [{ key, name, unit, color, dataStatus, points:[{time,value,refLow,refHigh}] }]
 * 每个 AVAILABLE 的指标占一个等高面板，垂直堆叠，共享同一条时间 x 轴，
 * tooltip / 框选缩放跨面板联动——用于跨指标在同一时间线上对比变化。
 */
export default {
  name: 'MultiTrendChart',
  props: {
    series: { type: Array, default: () => [] },
    panelHeight: { type: Number, default: 150 }
  },
  data() {
    return { chart: null }
  },
  computed: {
    panels() {
      return (this.series || []).filter((s) => s.dataStatus === 'AVAILABLE' && s.points && s.points.length)
    },
    hasData() {
      return this.panels.length > 0
    },
    chartHeight() {
      // 面板数 * 单面板高 + 上下留白
      return this.panels.length * this.panelHeight + 20
    }
  },
  watch: {
    series: {
      deep: true,
      handler() {
        this.$nextTick(this.render)
      }
    }
  },
  mounted() {
    this.$nextTick(this.render)
  },
  beforeUnmount() {
    if (this.chart) {
      this.chart.dispose()
      this.chart = null
    }
  },
  methods: {
    render() {
      if (!this.hasData) return
      const el = this.$refs.chartEl
      if (!el) return
      if (!this.chart) this.chart = echarts.init(el)
      const n = this.panels.length
      const gap = 18          // 面板间距
      const topPad = 8
      // 每个面板的 top 位置
      const tops = this.panels.map((_, i) => topPad + i * (this.panelHeight + gap))
      const palette = ['#b45309', '#0e7490', '#b91c1c', '#6d28d9', '#1d4ed8', '#047857', '#c2410c', '#475569']

      const grids = []
      const xAxes = []
      const yAxes = []
      const eseries = []

      this.panels.forEach((s, i) => {
        const color = s.color || palette[i % palette.length]
        grids.push({ left: 52, right: 26, top: tops[i], height: this.panelHeight })
        // 只在最下面的面板显示时间标签，其余共享同一时间轴
        xAxes.push({
          type: 'time',
          gridIndex: i,
          axisLabel: { show: i === n - 1, color: '#a8a29e', fontSize: 10, hideOverlap: true },
          axisLine: { lineStyle: { color: '#e7e5e4' } },
          axisTick: { show: i === n - 1 },
          splitLine: { show: false },
          axisPointer: { label: { show: i === n - 1 } }
        })
        yAxes.push({
          type: 'value',
          gridIndex: i,
          scale: true,
          name: (s.name || '') + (s.unit ? ' (' + s.unit + ')' : ''),
          nameGap: 30,
          nameTextStyle: { color: '#57534e', fontSize: 10, align: 'left' },
          axisLabel: { color: '#a8a29e', fontSize: 10 },
          splitLine: { lineStyle: { color: '#f5f5f4' } }
        })
        eseries.push({
          name: s.name,
          type: 'line',
          xAxisIndex: i,
          yAxisIndex: i,
          showSymbol: true,
          symbolSize: 5,
          data: s.points.map((p) => [p.time, p.value]),
          lineStyle: { width: 2, color },
          itemStyle: { color },
          markLine: refLine(s, color)
        })
      })

      this.chart.setOption({
        backgroundColor: 'transparent',
        axisPointer: { link: [{ xAxisIndex: 'all' }] },
        tooltip: {
          trigger: 'axis',
          axisPointer: { type: 'line', link: [{ xAxisIndex: 'all' }] },
          confine: true,
          formatter: (ps) => {
            if (!ps || !ps.length) return ''
            const t = String(ps[0].axisValue).slice(5, 16).replace('T', ' ')
            const rows = ps
              .map((p) => {
                const s = this.panels[p.seriesIndex]
                const v = Array.isArray(p.value) ? p.value[1] : p.value
                return (p.marker || '') + ' ' + (s ? s.name : '') + '：<b>' + v + '</b>' + (s && s.unit ? ' ' + s.unit : '')
              })
              .join('<br/>')
            return t + '<br/>' + rows
          }
        },
        grid: grids,
        xAxis: xAxes,
        yAxis: yAxes,
        series: eseries
      }, true)
      this.chart.resize()
    }
  }
}

function refLine(s, color) {
  const pts = s.points || []
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
.multi-trend { background: #fff; border: 1px solid #eeece8; border-radius: 10px; padding: 10px 12px; }
.chart { width: 100%; }
.empty { display: flex; align-items: center; justify-content: center; font-size: 12px; color: #a8a29e; }
</style>
