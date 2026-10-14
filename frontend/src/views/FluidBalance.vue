<template>
  <div class="page">
    <section class="head">
      <button class="back" @click="$router.back()">← 返回</button>
      <div class="head-main">
        <h2>体液平衡分析</h2>
        <p class="sub">
          {{ patientName }} {{ bedNo ? bedNo + '床' : '' }} · {{ inHospitalNo }}
          <span class="dot">·</span>{{ rangeText }}
        </p>
      </div>
      <div class="head-right">
        <el-radio-group v-model="hours" size="small" @change="load">
          <el-radio-button :label="6">近 6 小时</el-radio-button>
          <el-radio-button :label="12">近 12 小时</el-radio-button>
          <el-radio-button :label="24">近 24 小时</el-radio-button>
          <el-radio-button :label="72">近 3 天</el-radio-button>
        </el-radio-group>
        <el-button size="small" :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      </div>
    </section>

    <div class="notice">
      <el-icon><Warning /></el-icon>
      <div>
        <b>各来源分列展示，未合并总量。</b>入量存在 I/O 记录与医嘱执行两条通道，去重关系未经核实；
        在证明已去重前不提供「净平衡」合计数。缺测时段保留空白，不补零。
      </div>
    </div>

    <div v-loading="loading" class="source-grid">
      <div v-for="s in sources" :key="s.key" class="source-card">
        <div class="source-head">
          <span class="source-name">{{ s.name }}</span>
          <span class="source-total" v-if="s.dataStatus === 'AVAILABLE' && s.total != null">
            {{ Math.round(s.total) }} <i>mL</i>
          </span>
        </div>
        <div v-if="s.dataStatus === 'UNKNOWN'" class="state">查询失败：{{ s.note || '请稍后重试' }}</div>
        <div v-else-if="s.dataStatus === 'NO_RECORD'" class="state">窗口内无记录</div>
        <div v-else :ref="'chart-' + s.key" class="chart"></div>
        <div class="source-note" v-if="s.dataStatus === 'AVAILABLE'">{{ s.note }}</div>
      </div>
    </div>

    <section class="table-block">
      <div class="block-head">
        <span class="block-title">出入量明细</span>
        <span class="block-note">共 {{ recordCount || 0 }} 条</span>
      </div>
      <el-table :data="allRows" size="small" max-height="380" empty-text="窗口内无记录">
        <el-table-column prop="time" label="时间" width="165" />
        <el-table-column label="来源" width="130">
          <template #default="{ row }">{{ sourceName(row.source) }}</template>
        </el-table-column>
        <el-table-column prop="itemName" label="项目" min-width="140" show-overflow-tooltip />
        <el-table-column prop="value" label="值" width="100" />
        <el-table-column prop="unit" label="单位" width="70" />
        <el-table-column prop="staffName" label="记录人" min-width="90" />
      </el-table>
    </section>
  </div>
</template>

<script>
import { Refresh, Warning } from '@element-plus/icons-vue'
import * as echarts from '../utils/echarts'
import { fetchFluidBalance } from '../api/workbench'

export default {
  name: 'FluidBalance',
  components: { Refresh, Warning },
  data() {
    return {
      hours: 24,
      loading: false,
      patientName: '',
      bedNo: '',
      inHospitalNo: '',
      rangeStart: '',
      rangeEnd: '',
      sources: [],
      recordCount: 0,
      charts: {}
    }
  },
  computed: {
    patientId() {
      return this.$route.query.patientId
    },
    allRows() {
      const rows = []
      this.sources.forEach((s) => (s.rows || []).forEach((r) => rows.push(r)))
      return rows.sort((a, b) => String(b.time).localeCompare(String(a.time)))
    },
    rangeText() {
      if (!this.rangeStart) return ''
      const fmt = (t) => (t || '').replace('T', ' ').slice(5, 16)
      return fmt(this.rangeStart) + ' ~ ' + fmt(this.rangeEnd)
    }
  },
  watch: {
    sources() {
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
        const d = await fetchFluidBalance(this.patientId, this.hours)
        this.patientName = d.patientName || ''
        this.bedNo = d.bedNo || ''
        this.inHospitalNo = d.inHospitalNo || ''
        this.rangeStart = d.rangeStart
        this.rangeEnd = d.rangeEnd
        this.sources = d.sources || []
        this.recordCount = d.recordCount || 0
      } catch (e) {
        this.$message.error('体液平衡加载失败：' + ((e && e.message) || '请稍后重试'))
      } finally {
        this.loading = false
      }
    },
    sourceName(key) {
      const m = { output: '出量', urine: '尿量', advice_in: '医嘱入量', other_in: '其他入量' }
      return m[key] || key
    },
    renderCharts() {
      this.sources.forEach((s) => {
        if (s.dataStatus !== 'AVAILABLE' || !s.buckets || !s.buckets.length) return
        // v-for 内的 ref 在 Vue 3 里会被收集成数组，直接塞给 echarts.init 会报
        // 「this.dom.getContext is not a function」（卡片渲染了但图是空的）
        const raw = this.$refs['chart-' + s.key]
        const el = Array.isArray(raw) ? raw[0] : raw
        if (!el) return
        const chart = echarts.getInstanceByDom(el) || echarts.init(el)
        this.charts[s.key] = chart
        chart.clear()
        chart.setOption({
          backgroundColor: 'transparent',
          grid: { left: 46, right: 12, top: 12, bottom: 20 },
          tooltip: {
            trigger: 'axis',
            confine: true,
            formatter: (ps) => {
              if (!ps || !ps.length) return ''
              return ps[0].axisValue + '<br/>' + s.name + '：<b>' + ps[0].value + '</b> mL'
            }
          },
          xAxis: {
            type: 'category',
            data: s.buckets.map((b) => b.hour),
            axisLabel: { color: '#a8a29e', fontSize: 10, hideOverlap: true, interval: 'auto', rotate: 45 },
            axisLine: { lineStyle: { color: '#e7e5e4' } }
          },
          yAxis: {
            type: 'value',
            axisLabel: { color: '#a8a29e', fontSize: 10 },
            splitLine: { lineStyle: { color: '#f5f5f4' } }
          },
          series: [{
            type: 'bar',
            barMaxWidth: 16,
            data: s.buckets.map((b) => b.value),
            itemStyle: { color: s.key === 'output' || s.key === 'urine' ? '#0e7490' : '#b45309', borderRadius: [3, 3, 0, 0] }
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
.notice { display: flex; gap: 8px; align-items: flex-start; background: #fffbeb; border: 1px solid #fde68a; border-radius: 8px; padding: 10px 12px; font-size: 11px; color: #92400e; line-height: 1.7; margin-bottom: 12px; }
.source-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(340px, 1fr)); gap: 12px; }
.source-card { background: #fff; border: 1px solid #eeece8; border-radius: 10px; padding: 10px 12px; }
.source-head { display: flex; align-items: baseline; justify-content: space-between; margin-bottom: 4px; }
.source-name { font-size: 13px; font-weight: 600; color: #1c1917; }
.source-total { font-size: 18px; font-weight: 700; color: #b45309; font-variant-numeric: tabular-nums; }
.source-total i { font-style: normal; font-size: 10px; color: #a8a29e; margin-left: 2px; }
.chart { width: 100%; height: 170px; }
.state { height: 170px; display: flex; align-items: center; justify-content: center; font-size: 12px; color: #a8a29e; }
.source-note { margin-top: 4px; font-size: 10px; color: #a8a29e; }
.table-block { margin-top: 14px; background: #fff; border: 1px solid #eeece8; border-radius: 10px; padding: 12px 14px; }
.block-head { display: flex; align-items: baseline; gap: 10px; margin-bottom: 8px; }
.block-title { font-size: 13px; font-weight: 600; color: #1c1917; }
.block-note { font-size: 11px; color: #a8a29e; }
</style>
