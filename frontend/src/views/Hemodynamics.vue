<template>
  <div class="hemo-page qb-theme">
    <section class="page-header">
      <div class="patient-line">
        <span class="p-name">{{ patientName || '未指定患者' }}</span>
        <span v-if="inHospitalNo" class="p-no">住院号 {{ inHospitalNo }}</span>
      </div>
      <el-button size="small" :loading="loading" @click="load">刷新</el-button>
    </section>

    <div v-loading="loading" class="page-body">
      <el-empty v-if="!patientId" description="缺少 patientId 参数，请从患者工作台进入" />
      <template v-else>
        <!-- ================= 血流动力学 / 心功能 ================= -->
        <section class="watch-card">
          <div class="card-head"><h2>血流动力学与心功能（手工录入）</h2></div>
          <el-alert
            title="源系统无 CO/CVP/EF 结构化数据，由医护按测量设备录入；SVR = (MAP − CVP)/CO × 80 由系统自动计算。CVP 请录 mmHg（cmH2O 需 ÷1.36 换算）。"
            type="info" :closable="false" style="margin-bottom: 12px" />

          <el-form inline size="small" class="entry-form">
            <el-form-item label="测量时间">
              <el-date-picker v-model="hemoForm.measureTime" type="datetime"
                value-format="YYYY-MM-DD HH:mm:ss" style="width: 170px" />
            </el-form-item>
            <el-form-item label="SBP">
              <el-input-number v-model="hemoForm.sbp" :min="0" :max="300" :controls="false" style="width: 80px" />
            </el-form-item>
            <el-form-item label="DBP">
              <el-input-number v-model="hemoForm.dbp" :min="0" :max="200" :controls="false" style="width: 80px" />
            </el-form-item>
            <el-form-item label="MAP">
              <el-input-number v-model="hemoForm.mapVal" :min="0" :max="200" :controls="false" style="width: 80px" placeholder="自动" />
            </el-form-item>
            <el-form-item label="CVP">
              <el-input-number v-model="hemoForm.cvp" :min="-10" :max="40" :controls="false" style="width: 80px" />
            </el-form-item>
            <el-form-item label="CO">
              <el-input-number v-model="hemoForm.co" :min="0" :max="30" :precision="2" :controls="false" style="width: 80px" />
            </el-form-item>
            <el-form-item label="EF%">
              <el-input-number v-model="hemoForm.ef" :min="0" :max="100" :controls="false" style="width: 80px" />
            </el-form-item>
            <el-form-item label="方式">
              <el-select v-model="hemoForm.method" style="width: 110px" clearable>
                <el-option label="PiCCO" value="PiCCO" />
                <el-option label="Swan-Ganz" value="Swan-Ganz" />
                <el-option label="床旁超声" value="床旁超声" />
                <el-option label="其他" value="其他" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="saveHemo">{{ hemoForm.id ? '更新' : '保存' }}</el-button>
              <el-button v-if="hemoForm.id" @click="resetHemoForm">取消编辑</el-button>
            </el-form-item>
          </el-form>

          <div v-if="hemoRows.length" ref="hemoChart" class="chart hemo-chart"></div>
          <div v-else class="chart-empty">暂无录入记录</div>

          <el-table v-if="hemoRows.length" :data="hemoRows" size="small" stripe max-height="280" style="margin-top: 10px">
            <el-table-column label="测量时间" width="150">
              <template #default="{ row }">{{ fmt(row.measureTime) }}</template>
            </el-table-column>
            <el-table-column prop="sbp" label="SBP" width="60" />
            <el-table-column prop="dbp" label="DBP" width="60" />
            <el-table-column prop="mapVal" label="MAP" width="60" />
            <el-table-column prop="cvp" label="CVP" width="60" />
            <el-table-column prop="co" label="CO" width="60" />
            <el-table-column prop="svr" label="SVR" width="80" />
            <el-table-column prop="ef" label="EF%" width="60" />
            <el-table-column prop="method" label="方式" width="90" />
            <el-table-column label="操作" width="110" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="editHemo(row)">编辑</el-button>
                <el-button link type="danger" size="small" @click="delHemo(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="scale-note">MAP 缺省按 DBP + (SBP−DBP)/3 补算；EF 来自心脏超声，仅展示，不做心衰结论。</div>
        </section>

        <!-- ================= ADL（Barthel 指数） ================= -->
        <section class="watch-card">
          <div class="card-head"><h2>ADL 评估（Barthel 指数，满分 100）</h2></div>
          <el-alert
            title="源系统无护士 ADL 结构化文书，由医护按 Barthel 十项录入，总分系统自动求和。分级：≥60 轻度依赖 / 41~60 中度 / 20~40 重度 / <20 完全依赖。"
            type="info" :closable="false" style="margin-bottom: 12px" />

          <el-form inline size="small" class="entry-form">
            <el-form-item label="评估时间">
              <el-date-picker v-model="adlForm.adlTime" type="datetime"
                value-format="YYYY-MM-DD HH:mm:ss" style="width: 170px" />
            </el-form-item>
            <el-form-item v-for="it in ADL_ITEMS" :key="it.key" :label="it.label">
              <el-select v-model="adlForm[it.key]" style="width: 78px" clearable>
                <el-option v-for="s in it.scores" :key="s" :label="s" :value="s" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <span class="adl-total">总分 <b>{{ adlPreview ?? '—' }}</b>/100</span>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="saveAdl">{{ adlForm.id ? '更新' : '保存' }}</el-button>
              <el-button v-if="adlForm.id" @click="resetAdlForm">取消编辑</el-button>
            </el-form-item>
          </el-form>

          <div v-if="adlRows.length" ref="adlChart" class="chart adl-chart"></div>
          <div v-else class="chart-empty">暂无 ADL 评估记录</div>

          <el-table v-if="adlRows.length" :data="adlRows" size="small" stripe max-height="240" style="margin-top: 10px">
            <el-table-column label="评估时间" width="150">
              <template #default="{ row }">{{ fmt(row.adlTime) }}</template>
            </el-table-column>
            <el-table-column prop="totalScore" label="总分" width="70" align="center" />
            <el-table-column label="分级" width="90" align="center">
              <template #default="{ row }">
                <span :class="['grade-tag', gradeClass(row.totalScore)]">{{ gradeText(row.totalScore) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="110" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="editAdl(row)">编辑</el-button>
                <el-button link type="danger" size="small" @click="delAdl(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </section>
      </template>
    </div>
  </div>
</template>

<script>
import * as echarts from '../utils/echarts'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../api/request'

const ADL_ITEMS = [
  { key: 'feeding', label: '进食', scores: [0, 5, 10] },
  { key: 'bathing', label: '洗澡', scores: [0, 5] },
  { key: 'grooming', label: '修饰', scores: [0, 5] },
  { key: 'dressing', label: '穿衣', scores: [0, 5, 10] },
  { key: 'bowels', label: '大便', scores: [0, 5, 10] },
  { key: 'bladder', label: '小便', scores: [0, 5, 10] },
  { key: 'toilet', label: '如厕', scores: [0, 5, 10] },
  { key: 'transfer', label: '转移', scores: [0, 5, 10, 15] },
  { key: 'mobility', label: '行走', scores: [0, 5, 10, 15] },
  { key: 'stairs', label: '楼梯', scores: [0, 5, 10] }
]

const emptyHemo = () => ({ id: null, measureTime: '', sbp: undefined, dbp: undefined, mapVal: undefined, cvp: undefined, co: undefined, ef: undefined, method: '', remark: '' })
const emptyAdl = () => {
  const o = { id: null, adlTime: '' }
  ADL_ITEMS.forEach((it) => { o[it.key] = undefined })
  return o
}

export default {
  name: 'HemodynamicsPage',
  data() {
    return {
      ADL_ITEMS,
      loading: false,
      patientName: '',
      inHospitalNo: '',
      hemoForm: emptyHemo(),
      adlForm: emptyAdl(),
      hemoRows: [],
      adlRows: [],
      charts: {}
    }
  },
  computed: {
    patientId() { return this.$route.query.patientId },
    adlPreview() {
      const vals = ADL_ITEMS.map((it) => this.adlForm[it.key]).filter((v) => v != null)
      return vals.length ? vals.reduce((a, b) => a + b, 0) : null
    }
  },
  watch: { '$route.fullPath'() { this.load() } },
  created() { this.load() },
  beforeUnmount() {
    window.removeEventListener('resize', this.handleResize)
    Object.values(this.charts).forEach((c) => { if (c) c.dispose() })
    this.charts = {}
  },
  mounted() { window.addEventListener('resize', this.handleResize) },
  methods: {
    async load() {
      if (!this.patientId) return
      this.loading = true
      try {
        const [hemo, adl] = await Promise.allSettled([
          request.get(`/hemodynamics/patients/${this.patientId}/records`, { silentError: true }),
          request.get(`/adl/patients/${this.patientId}/records`, { silentError: true })
        ])
        if (hemo.status === 'fulfilled' && Array.isArray(hemo.value)) {
          this.hemoRows = hemo.value
          if (hemo.value.length && !this.patientName) {
            this.patientName = hemo.value[0].patientName || ''
            this.inHospitalNo = hemo.value[0].inHospitalNo || ''
          }
        } else if (hemo.status === 'rejected') { console.error(hemo.reason) }
        if (adl.status === 'fulfilled' && Array.isArray(adl.value)) {
          this.adlRows = adl.value
          if (adl.value.length && !this.patientName) {
            this.patientName = adl.value[0].patientName || ''
            this.inHospitalNo = adl.value[0].inHospitalNo || ''
          }
        } else if (adl.status === 'rejected') { console.error(adl.reason) }
        await this.$nextTick()
        this.renderCharts()
      } finally {
        this.loading = false
      }
    },
    handleResize() {
      Object.values(this.charts).forEach((c) => { if (c) c.resize() })
    },
    // ---------------- 血流动力学 ----------------
    resetHemoForm() { this.hemoForm = emptyHemo() },
    editHemo(row) {
      this.hemoForm = {
        id: row.id, measureTime: row.measureTime || '', sbp: row.sbp, dbp: row.dbp,
        mapVal: row.mapVal, cvp: row.cvp, co: row.co, ef: row.ef, method: row.method || '', remark: row.remark || ''
      }
    },
    async saveHemo() {
      if (!this.hemoForm.measureTime) { ElMessage.warning('请选择测量时间'); return }
      const hasValue = ['sbp', 'dbp', 'mapVal', 'cvp', 'co', 'ef'].some((k) => this.hemoForm[k] != null)
      if (!hasValue) { ElMessage.warning('请至少录入一项测量值'); return }
      try {
        await request.post('/hemodynamics/save', { ...this.hemoForm, patientId: this.patientId })
        ElMessage.success('已保存')
        this.resetHemoForm()
        await this.load()
      } catch (e) { /* 拦截器已提示 */ }
    },
    async delHemo(row) {
      try {
        await ElMessageBox.confirm('确定删除该条测量记录？（历史可追溯，逻辑删除）', '删除记录', {
          confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning'
        })
      } catch (e) { return }
      try {
        await request.delete(`/hemodynamics/records/${row.id}`, { params: { patientId: this.patientId } })
        ElMessage.success('已删除')
        await this.load()
      } catch (e) { /* 拦截器已提示 */ }
    },
    // ---------------- ADL ----------------
    resetAdlForm() { this.adlForm = emptyAdl() },
    editAdl(row) {
      const o = { id: row.id, adlTime: row.adlTime || '' }
      ADL_ITEMS.forEach((it) => { o[it.key] = row[it.key] })
      this.adlForm = o
    },
    async saveAdl() {
      if (!this.adlForm.adlTime) { ElMessage.warning('请选择评估时间'); return }
      const hasValue = ADL_ITEMS.some((it) => this.adlForm[it.key] != null)
      if (!hasValue) { ElMessage.warning('请至少评估一项'); return }
      try {
        await request.post('/adl/save', { ...this.adlForm, patientId: this.patientId })
        ElMessage.success('已保存')
        this.resetAdlForm()
        await this.load()
      } catch (e) { /* 拦截器已提示 */ }
    },
    async delAdl(row) {
      try {
        await ElMessageBox.confirm('确定删除该条 ADL 评估？（逻辑删除，历史可追溯）', '删除评估', {
          confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning'
        })
      } catch (e) { return }
      try {
        await request.delete(`/adl/records/${row.id}`, { params: { patientId: this.patientId } })
        ElMessage.success('已删除')
        await this.load()
      } catch (e) { /* 拦截器已提示 */ }
    },
    // ---------------- 图表 ----------------
    renderCharts() {
      try { this.renderHemoChart() } catch (e) { console.error('[Hemodynamics] hemo chart', e) }
      try { this.renderAdlChart() } catch (e) { console.error('[Hemodynamics] adl chart', e) }
    },
    renderHemoChart() {
      const el = this.$refs.hemoChart
      if (!el || !this.hemoRows.length) return
      const c = this.charts.hemo && this.charts.hemo.getDom() === el ? this.charts.hemo : echarts.init(el)
      this.charts.hemo = c
      const series = [
        { name: 'MAP', key: 'mapVal', color: '#dc2626', yAxisIndex: 0 },
        { name: 'CVP', key: 'cvp', color: '#d97706', yAxisIndex: 0 },
        { name: 'CO', key: 'co', color: '#2563eb', yAxisIndex: 1 },
        { name: 'SVR', key: 'svr', color: '#059669', yAxisIndex: 2 },
        { name: 'EF%', key: 'ef', color: '#7c3aed', yAxisIndex: 2 }
      ].filter((s) => this.hemoRows.some((r) => r[s.key] != null))
      c.setOption({
        backgroundColor: 'transparent',
        legend: { top: 0, textStyle: { fontSize: 11, color: '#57534e' } },
        grid: { left: 46, right: 130, top: 34, bottom: 30 },
        tooltip: { trigger: 'axis', confine: true },
        xAxis: { type: 'time', axisLabel: { color: '#a8a29e', fontSize: 10, hideOverlap: true } },
        yAxis: [
          { type: 'value', scale: true, name: 'mmHg', nameTextStyle: { color: '#78716c', fontSize: 10 }, axisLabel: { color: '#a8a29e', fontSize: 10 }, splitLine: { lineStyle: { color: '#f5f5f4' } } },
          { type: 'value', scale: true, name: 'L/min', position: 'right', nameTextStyle: { color: '#78716c', fontSize: 10 }, axisLabel: { color: '#a8a29e', fontSize: 10 }, splitLine: { show: false } },
          { type: 'value', scale: true, position: 'right', offset: 46, nameTextStyle: { color: '#78716c', fontSize: 10 }, axisLabel: { color: '#a8a29e', fontSize: 10 }, splitLine: { show: false } }
        ],
        series: series.map((s) => ({
          name: s.name, type: 'line', showSymbol: true, symbolSize: 5,
          yAxisIndex: s.yAxisIndex,
          data: this.hemoRows.filter((r) => r[s.key] != null).map((r) => [r.measureTime, r[s.key]]),
          lineStyle: { width: 2, color: s.color }, itemStyle: { color: s.color }
        }))
      }, true)
    },
    renderAdlChart() {
      const el = this.$refs.adlChart
      if (!el || !this.adlRows.length) return
      const c = this.charts.adl && this.charts.adl.getDom() === el ? this.charts.adl : echarts.init(el)
      this.charts.adl = c
      c.setOption({
        backgroundColor: 'transparent',
        grid: { left: 40, right: 20, top: 30, bottom: 30 },
        tooltip: { trigger: 'axis', confine: true },
        xAxis: { type: 'time', axisLabel: { color: '#a8a29e', fontSize: 10, hideOverlap: true } },
        yAxis: {
          type: 'value', min: 0, max: 100, name: '分',
          nameTextStyle: { color: '#78716c', fontSize: 10 },
          axisLabel: { color: '#a8a29e', fontSize: 10 },
          splitLine: { lineStyle: { color: '#f5f5f4' } }
        },
        series: [{
          name: 'ADL 总分', type: 'line', showSymbol: true, symbolSize: 6,
          data: this.adlRows.filter((r) => r.totalScore != null).map((r) => [r.adlTime, r.totalScore]),
          lineStyle: { width: 2, color: '#2563eb' }, itemStyle: { color: '#2563eb' },
          areaStyle: { color: 'rgba(37, 99, 235, .06)' },
          markLine: {
            silent: true, symbol: 'none',
            data: [20, 40, 60].map((y) => ({
              yAxis: y,
              lineStyle: { color: '#e7e5e4', type: 'dashed' },
              label: { formatter: String(y), color: '#a8a29e' }
            }))
          }
        }]
      }, true)
    },
    gradeText(total) {
      if (total == null) return '—'
      if (total >= 60) return '轻度依赖'
      if (total >= 41) return '中度依赖'
      if (total >= 20) return '重度依赖'
      return '完全依赖'
    },
    gradeClass(total) {
      if (total == null) return ''
      if (total >= 60) return 'g-mild'
      if (total >= 41) return 'g-mid'
      if (total >= 20) return 'g-sev'
      return 'g-full'
    },
    fmt(t) { return (t || '').replace('T', ' ').slice(0, 16) }
  }
}
</script>

<style scoped>
.hemo-page { padding: 16px; }
.page-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14px; }
.patient-line { display: flex; align-items: baseline; gap: 10px; }
.p-name { font-size: 17px; font-weight: 700; color: #1c1917; }
.p-no { color: #78716c; font-size: 13px; }
.watch-card { border: 1px solid #e7e5e4; border-radius: 12px; background: #fff; padding: 14px 18px 16px; margin-bottom: 16px; }
.card-head { margin-bottom: 10px; }
.card-head h2 { margin: 0; font-size: 14px; font-weight: 700; color: #1c1917; }
.entry-form :deep(.el-form-item) { margin-bottom: 8px; }
.chart { width: 100%; }
.hemo-chart { height: 280px; }
.adl-chart { height: 220px; }
.chart-empty { padding: 30px 0; text-align: center; color: #a8a29e; font-size: 13px; }
.scale-note { color: #a8a29e; font-size: 12px; margin-top: 8px; }
.adl-total { color: #1c1917; font-size: 13px; }
.adl-total b { font-size: 18px; color: #2563eb; }
.grade-tag { font-size: 11px; padding: 1px 8px; border-radius: 10px; }
.g-mild { background: #f0f9f3; color: #15803d; }
.g-mid { background: #fffbeb; color: #b45309; }
.g-sev { background: #fef2f2; color: #dc2626; }
.g-full { background: #450a0a; color: #fff; }
</style>
