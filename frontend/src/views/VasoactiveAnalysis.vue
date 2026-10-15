<template>
  <div class="page">
    <section class="head">
      <button class="back" @click="$router.back()">← 返回</button>
      <div class="head-main">
        <h2>血管活性药分析</h2>
        <p class="sub">
          {{ patientName }} {{ bedNo ? bedNo + '床' : '' }} · {{ inHospitalNo || '—' }}
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
        <b>执行时间轴与开立在用为两条链路，分开展示不合并。</b>泵速单位取执行单登记值；
        调速步骤仅展示窗口内有记录的部分。MAP 为无创/有创两条监护通道，未合并。数据块缺失时标「暂不可用」，不虚构 0 值。
      </div>
    </div>

    <!-- 当前在用（开立链路） -->
    <section class="card">
      <div class="block-title"><span class="dot dot-blue"></span>当前在用（开立医嘱，执行中）</div>
      <el-table v-if="currentRows.length" :data="currentRows" size="small" border>
        <el-table-column prop="name" label="药名" min-width="150" />
        <el-table-column prop="drugType" label="归类" width="110">
          <template #default="{ row }"><span class="type-tag">{{ row.drugType || '其他' }}</span></template>
        </el-table-column>
        <el-table-column prop="method" label="途径" width="90" />
        <el-table-column prop="freq" label="频次" width="90" />
        <el-table-column label="单次剂量" width="110">
          <template #default="{ row }">{{ row.dosage }} {{ row.dosageUnit }}</template>
        </el-table-column>
        <el-table-column label="开始时间" width="165">
          <template #default="{ row }">{{ fmtTime(row.startTime) }}</template>
        </el-table-column>
      </el-table>
      <div v-else class="empty-line">{{ currentNote }}</div>
    </section>

    <!-- MAP / 乳酸趋势 -->
    <section class="card">
      <div class="block-title"><span class="dot dot-purple"></span>MAP 与乳酸趋势（滴定对照）</div>
      <div v-show="hasTrend" ref="trendChartRef" class="trend-chart"></div>
      <div v-if="!hasTrend" class="empty-line">{{ trendNote }}</div>
    </section>

    <!-- 执行时间轴 -->
    <section class="card">
      <div class="block-title"><span class="dot dot-green"></span>执行时间轴（执行链路）<span class="block-note">共 {{ execRows.length }} 条</span></div>
      <el-table v-if="execRows.length" :data="execRows" size="small" border row-key="rowKey">
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="steps-panel">
              <div v-if="row.steps && row.steps.length" class="steps-list">
                <div v-for="(s, i) in row.steps" :key="i" class="step-item">
                  <span class="step-time">{{ fmtTime(s.step_time) }}</span>
                  <span class="step-speed">{{ s.speed }} {{ row.speedUnit || 'ml/h' }}</span>
                  <span v-if="s.step_type_name" class="step-type">{{ s.step_type_name }}</span>
                </div>
              </div>
              <div v-else class="empty-line">窗口内无调速步骤记录</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="drugName" label="药名" min-width="150" />
        <el-table-column label="归类" width="110">
          <template #default="{ row }"><span class="type-tag">{{ row.drugType || '其他' }}</span></template>
        </el-table-column>
        <el-table-column label="起始速度" width="110" align="center">
          <template #default="{ row }">{{ row.speedStart != null ? row.speedStart + ' ' + (row.speedUnit || '') : '—' }}</template>
        </el-table-column>
        <el-table-column label="当前速度" width="110" align="center">
          <template #default="{ row }">{{ row.speedNow != null ? row.speedNow + ' ' + (row.speedUnit || '') : '—' }}</template>
        </el-table-column>
        <el-table-column label="开始" width="165">
          <template #default="{ row }">{{ fmtTime(row.startTime) }}</template>
        </el-table-column>
        <el-table-column label="结束" width="165">
          <template #default="{ row }">{{ row.endTime ? fmtTime(row.endTime) : '进行中' }}</template>
        </el-table-column>
      </el-table>
      <div v-else class="empty-line">{{ execNote }}</div>
    </section>
  </div>
</template>

<script>
import { Refresh, Warning } from '@element-plus/icons-vue'
import request from '../api/request'
import * as echarts from '../utils/echarts'

/**
 * 血管活性药分析页（只读）。
 * 数据：GET /api/vasoactive/patients/{patientId}/analysis?hours=
 * 后端把执行/开立两条链路、MAP 与乳酸各自独立降级，前端按 dataStatus 渲染「暂不可用」。
 */
export default {
  name: 'VasoactiveAnalysisPage',
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
      currentRows: [],
      currentNote: '暂无在用血管活性药',
      execRows: [],
      execNote: '窗口内无执行记录',
      mapPoints: [],
      lacPoints: [],
      chart: null
    }
  },
  computed: {
    patientId() {
      return this.$route.query.patientId
    },
    rangeText() {
      if (!this.rangeStart || !this.rangeEnd) return ''
      return `${this.rangeStart.slice(5, 16).replace('T', ' ')} ～ ${this.rangeEnd.slice(5, 16).replace('T', ' ')}`
    },
    hasTrend() {
      return this.mapPoints.length > 0 || this.lacPoints.length > 0
    },
    trendNote() {
      if (!this.mapPoints.length && !this.lacPoints.length) {
        return '窗口内无 MAP / 乳酸记录（或数据源暂不可用）'
      }
      return ''
    }
  },
  async mounted() {
    if (!this.patientId) {
      this.execNote = this.currentNote = '缺少患者参数（patientId），请从患者工作台进入'
      return
    }
    await this.load()
  },
  beforeUnmount() {
    if (this.chart) {
      this.chart.dispose()
      this.chart = null
    }
  },
  methods: {
    async load() {
      this.loading = true
      try {
        const d = (await request.get(`/vasoactive/patients/${this.patientId}/analysis`, {
          params: { hours: this.hours }
        })) || {}
        this.patientName = d.patientName || ''
        this.bedNo = d.bedNo || ''
        this.inHospitalNo = d.inHospitalNo || ''
        this.rangeStart = d.rangeStart || ''
        this.rangeEnd = d.rangeEnd || ''

        const cur = d.currentAdvice || {}
        this.currentRows = cur.dataStatus === 'FOUND' ? (cur.rows || []) : []
        this.currentNote = cur.dataStatus === 'UNKNOWN'
          ? '在用医嘱数据暂不可用'
          : (cur.note || '暂无在用血管活性药')

        const exec = d.executions || {}
        const rows = exec.dataStatus === 'FOUND' ? (exec.rows || []) : []
        rows.forEach((r, i) => { r.rowKey = `${r.executeId}-${i}` })
        this.execRows = rows
        this.execNote = exec.dataStatus === 'UNKNOWN'
          ? '执行医嘱数据暂不可用'
          : (exec.note || '窗口内无执行记录')

        this.mapPoints = (d.mapTrend && d.mapTrend.points) || []
        this.lacPoints = (d.lactateTrend && d.lactateTrend.points) || []
        await this.$nextTick()
        this.renderTrend()
      } catch (e) {
        this.$message.error('血管活性药分析加载失败：' + ((e && e.message) || '请稍后重试'))
      } finally {
        this.loading = false
      }
    },
    /** MAP（左轴 mmHg）+ 乳酸（右轴 mmol/L）双轴折线 */
    renderTrend() {
      if (!this.$refs.trendChartRef) return
      if (!this.hasTrend) {
        if (this.chart) { this.chart.dispose(); this.chart = null }
        return
      }
      if (!this.chart) {
        this.chart = echarts.init(this.$refs.trendChartRef)
      }
      const times = [...new Set([
        ...this.mapPoints.map((p) => p.time),
        ...this.lacPoints.map((p) => p.time)
      ])].sort()
      const fmtT = (t) => (t || '').slice(5, 16).replace('T', ' ')
      const lastOf = (t, points) => {
        const hit = points.filter((p) => p.time === t)
        return hit.length ? Number(hit[hit.length - 1].value) : null
      }
      this.chart.setOption({
        grid: { left: 48, right: 48, top: 36, bottom: 56 },
        tooltip: { trigger: 'axis' },
        legend: { data: ['MAP (mmHg)', '乳酸 (mmol/L)'] },
        xAxis: { type: 'category', data: times.map(fmtT) },
        yAxis: [
          { type: 'value', name: 'mmHg', scale: true },
          { type: 'value', name: 'mmol/L', scale: true, splitLine: { show: false } }
        ],
        series: [
          { name: 'MAP (mmHg)', type: 'line', data: times.map((t) => lastOf(t, this.mapPoints)), connectNulls: true, showSymbol: true },
          { name: '乳酸 (mmol/L)', type: 'line', yAxisIndex: 1, data: times.map((t) => lastOf(t, this.lacPoints)), connectNulls: true, showSymbol: true }
        ]
      }, true)
      this.chart.resize()
    },
    fmtTime(t) {
      return t ? String(t).slice(0, 19).replace('T', ' ') : '—'
    }
  }
}
</script>

<style scoped>
.page { padding: 16px 20px; }
.head { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; }
.back { border: 1px solid #d6d3d1; background: #fff; border-radius: 8px; padding: 6px 12px; cursor: pointer; }
.head-main h2 { margin: 0 0 4px; font-size: 20px; }
.sub { margin: 0; color: #78716c; font-size: 13px; }
.dot { margin: 0 6px; color: #d6d3d1; }
.notice {
  display: flex; gap: 8px; align-items: flex-start;
  background: #fffbeb; border: 1px solid #fde68a; border-radius: 8px;
  padding: 10px 14px; margin: 12px 0; font-size: 13px; color: #92400e;
}
.card {
  background: #fff; border: 1px solid #e7e5e4; border-radius: 10px;
  padding: 14px 16px; margin-bottom: 14px;
}
.block-title { font-weight: 700; font-size: 14px; margin-bottom: 10px; display: flex; align-items: center; gap: 6px; }
.block-title .dot { width: 8px; height: 8px; border-radius: 50%; display: inline-block; margin: 0; }
.dot-blue { background: #2563eb; }
.dot-purple { background: #7c3aed; }
.dot-green { background: #059669; }
.block-note { font-weight: 400; color: #a8a29e; font-size: 12px; margin-left: 6px; }
.trend-chart { width: 100%; height: 320px; }
.empty-line { color: #a8a29e; font-size: 13px; padding: 12px 0; }
.type-tag {
  background: #f1f5f9; border-radius: 4px; padding: 1px 6px; font-size: 12px; color: #475569;
}
.steps-panel { padding: 4px 12px 8px 32px; }
.step-item { display: flex; gap: 14px; font-size: 12px; color: #57534e; padding: 2px 0; }
.step-time { color: #a8a29e; font-variant-numeric: tabular-nums; }
.step-speed { font-weight: 600; }
.step-type { color: #94a3b8; }
</style>
