<template>
  <div>
    <!-- 有趋势数据：折线图 -->
    <div v-if="trends && trends.length" class="trend-grid">
      <div v-for="t in trends" :key="t.itemName" class="trend-item">
        <div class="trend-head">
          <span class="trend-name">{{ t.itemName }}</span>
          <span class="trend-latest">{{ latestText(t) }}</span>
        </div>
        <div :ref="(el) => setEl(t.itemName, el)" class="trend-chart"></div>
      </div>
    </div>

    <!-- 无趋势数据但有关键指标最新值：降级为最新值卡片 -->
    <div v-else-if="hasLatest" class="latest-grid">
      <div v-for="(v, k) in latestMap" :key="k" class="latest-item">
        <div class="latest-name">{{ k }}</div>
        <div class="latest-value">{{ v }}</div>
      </div>
    </div>

    <!-- 均无数据 -->
    <div v-else class="trend-empty">暂无检验数据</div>
  </div>
</template>

<script>
import * as echarts from '../utils/echarts'

const COLOR = {
  PCT: '#2d6fbf',
  WBC: '#409eff',
  CRP: '#d9606b',
  乳酸: '#67c23a',
  肌酐: '#8a7ab5'
}

export default {
  name: 'LabTrendChart',
  props: {
    trends: { type: Array, default: () => [] },
    latestMap: { type: Object, default: () => ({}) }
  },
  computed: {
    hasLatest() {
      return this.latestMap && Object.keys(this.latestMap).length > 0
    }
  },
  data() {
    return {
      charts: {},
      rendered: {},
      elMap: {}
    }
  },
  mounted() {
    this.$nextTick(() => this.renderAll())
  },
  updated() {
    // DOM 更新后兜底重绘（数据异步到达时 ref 时序不稳定的保险）
    this.$nextTick(() => this.renderAll())
  },
  watch: {
    trends() {
      this.$nextTick(() => this.renderAll())
    }
  },
  beforeUnmount() {
    Object.values(this.charts).forEach((c) => { if (c) c.dispose() })
    this.charts = {}
    this.rendered = {}
  },
  methods: {
    setEl(name, el) {
      if (el) {
        this.elMap[name] = el
        const t = (this.trends || []).find((x) => x.itemName === name)
        if (t && t.series && t.series.length) this.renderChart(t)
      }
    },
    colorOf(name) {
      return COLOR[name] || '#2d6fbf'
    },
    latestText(t) {
      if (this.latestMap && this.latestMap[t.itemName]) return this.latestMap[t.itemName]
      const s = t.series || []
      return s.length ? s[s.length - 1].value : ''
    },
    dataKey(t) {
      const s = t.series || []
      return t.itemName + '|' + s.length + '|' + (s.length ? s[0].time : '')
        + '|low=' + (t.lowLimit != null ? t.lowLimit : '')
        + '|high=' + (t.highLimit != null ? t.highLimit : '')
    },
    renderAll() {
      ;(this.trends || []).forEach((t) => this.renderChart(t))
    },
    renderChart(t) {
      const el = this.elMap[t.itemName]
      if (!el || !t || !t.series || !t.series.length) return
      const key = this.dataKey(t)
      if (this.rendered[t.itemName] === key) {
        const c = this.charts[t.itemName]
        if (c) c.resize()
        return
      }
      if (this.charts[t.itemName]) {
        this.charts[t.itemName].dispose()
        delete this.charts[t.itemName]
      }
      const chart = echarts.init(el)
      this.charts[t.itemName] = chart
      this.rendered[t.itemName] = key
      const color = this.colorOf(t.itemName)
      chart.setOption({
        backgroundColor: 'transparent',
        grid: { left: 8, right: 8, top: 12, bottom: 8 },
        tooltip: {
          trigger: 'axis',
          confine: true,
          backgroundColor: 'rgba(255,255,255,0.96)',
          borderColor: '#dce4ee',
          textStyle: { color: '#1f3a5f', fontSize: 11 },
          formatter: (ps) => {
            if (!ps || !ps.length) return ''
            const p = ps[0]
            return p.axisValue + '<br/>' + t.itemName + ': <b>' + p.value + '</b>'
          }
        },
        xAxis: {
          type: 'category',
          data: t.series.map((p) => p.time),
          axisLabel: { show: false },
          axisLine: { lineStyle: { color: '#d5e0ec' } },
          axisTick: { show: false }
        },
        yAxis: {
          type: 'value',
          scale: true,
          axisLabel: { show: false },
          splitLine: { lineStyle: { color: '#eef2f7' } }
        },
        series: [{
          type: 'line',
          data: t.series.map((p) => p.value),
          smooth: true,
          symbol: 'circle',
          symbolSize: 5,
          lineStyle: { width: 2, color },
          itemStyle: { color },
          areaStyle: {
            color: {
              type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
              colorStops: [
                { offset: 0, color: color + '44' },
                { offset: 1, color: color + '06' }
              ]
            }
          },
          markLine: {
            silent: true,
            symbol: 'none',
            data: [].concat(
              t.lowLimit != null ? [{
                yAxis: Number(t.lowLimit),
                lineStyle: { color: '#67c23a', type: 'dashed', width: 1.5 },
                label: {
                  formatter: '低限 ' + t.lowLimit,
                  color: '#67c23a',
                  fontSize: 10,
                  position: 'insideEndTop'
                }
              }] : [],
              t.highLimit != null ? [{
                yAxis: Number(t.highLimit),
                lineStyle: { color: '#f56c6c', type: 'dashed', width: 1.5 },
                label: {
                  formatter: '高限 ' + t.highLimit,
                  color: '#f56c6c',
                  fontSize: 10,
                  position: 'insideEndBottom'
                }
              }] : []
            )
          }
        }]
      })
    }
  }
}
</script>

<style scoped>
.trend-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 12px;
}
.trend-item {
  background: #f4f8fc;
  border: 1px solid #dce8f4;
  border-radius: 10px;
  padding: 8px 10px 4px;
}
.trend-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 6px;
}
.trend-name {
  font-size: 13px;
  font-weight: 700;
  color: #1f4e79;
}
.trend-latest {
  font-size: 12px;
  font-weight: 600;
  color: #2d6fbf;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.trend-chart {
  height: 90px;
  width: 100%;
}
.latest-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
  gap: 10px;
}
.latest-item {
  background: #f4f8fc;
  border: 1px solid #dce8f4;
  border-radius: 8px;
  padding: 10px 12px;
  text-align: center;
}
.latest-name {
  font-size: 12px;
  color: #6b7b8d;
}
.latest-value {
  font-size: 16px;
  font-weight: 600;
  color: #1f4e79;
  margin-top: 4px;
}
.trend-empty {
  color: #a0a8b4;
  font-size: 13px;
  padding: 12px;
}
</style>
