<template>
  <div class="cockpit-page">
    <header class="cockpit-hero">
      <div class="hero-copy">
        <div class="hero-eyebrow"><span class="live-dot"></span> ICU · 科室运行总览</div>
        <h1>{{ summary.departName ? summary.departName + ' 科室驾驶舱' : '科室驾驶舱' }}</h1>
        <p>患者负荷、器官支持与医疗质量，一屏掌握</p>
      </div>
      <div class="hero-meta">
        <div class="meta-line"><span>业务日期</span><b>{{ summary.date || '—' }}</b></div>
        <div class="meta-line"><span>页面刷新</span><b>{{ updatedAt || '尚未刷新' }}</b></div>
        <div class="meta-caption">页面取数时间，不代表 ICU 源数据更新时间</div>
      </div>
      <div class="hero-actions">
        <el-radio-group v-model="days" size="small" @change="load">
          <el-radio-button :label="7">7 天</el-radio-button>
          <el-radio-button :label="30">30 天</el-radio-button>
          <el-radio-button :label="90">90 天</el-radio-button>
        </el-radio-group>
        <el-button :icon="refreshIcon" :loading="loading" @click="load">刷新数据</el-button>
        <el-button :icon="infoIcon" text @click="notesVisible = true">口径说明</el-button>
      </div>
    </header>

    <div class="period-strip">
      <span class="period-label">统计范围</span>
      <span class="period-item"><i class="period-dot today"></i>科室快照：当前 / 今日</span>
      <span class="period-item"><i class="period-dot trend"></i>流转、评分、住院日：近 {{ days }} 天</span>
      <span class="period-item"><i class="period-dot quality"></i>质控指标：本月</span>
      <span v-if="bedDataPending" class="data-gap"><i></i>床位台账未接入</span>
    </div>

    <section class="kpi-row" aria-label="科室核心指标">
      <StatCard class="snapshot-card census-card" label="当前在科患者" :block="summary.inCount" unit="人" />
      <StatCard class="snapshot-card critical-card" label="器官支持患者" :block="summary.criticalCount" unit="人" :sub="ratioText" sub-warn />
      <StatCard class="snapshot-card admission-card" label="今日入科 / 转入" :block="summary.todayInCount" unit="人" />
      <StatCard class="snapshot-card discharge-card" label="今日转出 / 出科" :block="summary.todayOutCount" unit="人" />
      <StatCard class="snapshot-card stay-card" :label="'近 ' + days + ' 天平均 ICU 住院日'" :block="summary.avgStayDays" unit="天" :sub="avgStaySub" />
    </section>

    <section class="primary-grid">
      <article class="panel flow-panel">
        <div class="panel-head">
          <div><span class="panel-kicker">PATIENT FLOW</span><h2 class="panel-title">患者流转趋势</h2></div>
          <div class="panel-context">近 {{ days }} 天 <span>·</span> 左闭右开 <span>·</span> 住院号去重</div>
        </div>
        <div class="flow-summary">
          <div><span>区间入科</span><b>{{ flowTotals.inCount == null ? '—' : flowTotals.inCount }}</b><i>人</i></div>
          <div><span>区间出科</span><b>{{ flowTotals.outCount == null ? '—' : flowTotals.outCount }}</b><i>人</i></div>
          <div :class="flowTotals.net == null ? '' : (flowTotals.net >= 0 ? 'net-positive' : 'net-negative')"><span>净流入</span><b>{{ flowTotals.net == null ? '—' : (flowTotals.net > 0 ? '+' : '') + flowTotals.net }}</b><i>人</i></div>
        </div>
        <div class="panel-body flow-body">
          <div v-if="flowUnknown" class="state-box"><div class="state-title">{{ flowNote }}</div><el-button size="small" :loading="loading" @click="load">重试</el-button></div>
          <div v-else-if="!flowPoints.length" class="state-box"><div class="state-title">区间内暂无入出科记录</div></div>
          <div v-else ref="flowChart" class="flow-chart"></div>
        </div>
      </article>

      <article class="panel quality-panel">
        <div class="panel-head">
          <div><span class="panel-kicker">QUALITY CONTROL</span><h2 class="panel-title">本月质量指标</h2></div>
          <router-link class="panel-link" to="/page/quality-board">质控看板 <span>→</span></router-link>
        </div>
        <div v-if="qualityUnknown" class="state-box small"><div class="state-title">{{ blockNote(summary.qualityCards) }}</div></div>
        <div v-else-if="!qualityCards.length" class="state-box small"><div class="state-title">本月暂无指标结果</div></div>
        <div v-else class="q-list">
          <div v-for="q in qualityCards" :key="q.code" class="q-card">
            <div class="q-main">
              <div class="q-name">{{ q.name }}</div>
              <div class="q-sub">{{ q.calcStatus === 'OK' && q.denominator != null ? `分子/分母 ${q.numerator}/${q.denominator}` : q.note || qualityStatusText(q.calcStatus) }}</div>
            </div>
            <div class="q-result">
              <template v-if="q.calcStatus === 'OK' && q.value != null"><b>{{ formatMetric(q.value) }}</b><i>{{ q.unit }}</i></template>
              <span v-else :class="['q-state', q.calcStatus === 'PENDING_SOURCE' ? 'pending' : '']">{{ qualityStatusText(q.calcStatus) }}</span>
            </div>
          </div>
        </div>
        <div class="quality-foot">指标结果来自质控中台；目标值及上期变化请在指标详情中核对</div>
      </article>
    </section>

    <section class="secondary-grid">
      <article class="panel score-panel">
        <div class="panel-head">
          <div><span class="panel-kicker">SEVERITY PROFILE</span><h2 class="panel-title">SOFA 评分分布</h2></div>
          <div class="panel-context">近 {{ days }} 天每患者最新评分</div>
        </div>
        <div v-if="distUnknown(summary.sofaDist) || !scoreRows(summary.sofaDist).length" class="state-box small"><div class="state-title">{{ blockNote(summary.sofaDist) || '周期内无评分记录' }}</div></div>
        <div v-else class="score-content">
          <div class="score-total"><b>{{ distTotal(summary.sofaDist) }}</b><span>名患者</span></div>
          <div class="score-rows">
            <div v-for="(row, index) in scoreRows(summary.sofaDist)" :key="row.label" class="score-row">
              <span class="score-range">{{ row.label }}</span><div class="score-track"><i :style="{ width: row.percent + '%', background: sofaColors[index % sofaColors.length] }"></i></div><b>{{ row.count }}</b><small>{{ row.share }}%</small>
            </div>
          </div>
        </div>
      </article>

      <article class="panel score-panel">
        <div class="panel-head">
          <div><span class="panel-kicker">SEVERITY PROFILE</span><h2 class="panel-title">APACHE II 评分分布</h2></div>
          <div class="panel-context">近 {{ days }} 天每患者最新评分</div>
        </div>
        <div v-if="distUnknown(summary.apache2Dist) || !scoreRows(summary.apache2Dist).length" class="state-box small"><div class="state-title">{{ blockNote(summary.apache2Dist) || '周期内无评分记录' }}</div></div>
        <div v-else class="score-content">
          <div class="score-total"><b>{{ distTotal(summary.apache2Dist) }}</b><span>名患者</span></div>
          <div class="score-rows">
            <div v-for="(row, index) in scoreRows(summary.apache2Dist)" :key="row.label" class="score-row">
              <span class="score-range">{{ row.label }}</span><div class="score-track"><i :style="{ width: row.percent + '%', background: apacheColors[index % apacheColors.length] }"></i></div><b>{{ row.count }}</b><small>{{ row.share }}%</small>
            </div>
          </div>
        </div>
      </article>

      <article class="panel organ-panel">
        <div class="panel-head">
          <div><span class="panel-kicker">ORGAN SUPPORT</span><h2 class="panel-title">当前器官支持</h2></div>
          <div class="panel-context">患者数 / 当前在科</div>
        </div>
        <div class="organ-list">
          <div v-for="o in organItems" :key="o.label" class="organ-item">
            <span class="organ-label">{{ o.label }}</span>
            <div class="organ-bar"><div class="organ-bar-fill" :style="{ width: o.percent + '%', background: o.color }"></div></div>
            <span class="organ-num" :class="{ 'value-unknown': o.status !== 'OK' }">{{ o.status === 'OK' ? o.value : '—' }}<i>{{ o.status === 'OK' ? '人' : '不可用' }}</i></span>
          </div>
        </div>
        <div class="organ-foot">危重患者按器官支持患者并集计算，分类人数不可直接相加；仅代表患者支持状态，不代表设备台数或库存。</div>
      </article>
    </section>

    <el-dialog v-model="notesVisible" title="口径说明" width="640px">
      <ul class="notes-list">
        <li v-for="(n, i) in summary.notes" :key="i">{{ n }}</li>
      </ul>
      <p class="notes-foot">趋势选择只作用于患者流转、评分分布和平均住院日；核心快照按当前 / 今日统计，质量指标按本月统计。页面刷新时间不代表 ICU 源数据更新时间。</p>
    </el-dialog>
  </div>
</template>
<script>
import { ElMessage } from 'element-plus'
import { Refresh, InfoFilled } from '@element-plus/icons-vue'
import * as echarts from '../utils/echarts'
import { fetchCockpitSummary } from '../api/workbench'
import { currentDepart } from '../utils/departContext'
import StatCard from '../components/StatCard.vue'

export default {
  name: 'DepartmentCockpit',
  components: { StatCard },
  data() {
    return {
      days: 30,
      loading: false,
      notesVisible: false,
      updatedAt: '',
      refreshIcon: Refresh,
      infoIcon: InfoFilled,
      summary: { date: '', departCode: '', departName: '', notes: [] },
      charts: {},
      sofaColors: ['#78a99b', '#5c9b8c', '#c3a35d', '#d18b4c', '#c96b52', '#a94442'],
      apacheColors: ['#8cb7d4', '#5e9fbd', '#3d859b', '#285c70']
    }
  },
  computed: {
    departCode() {
      return currentDepart.departCode || ''
    },
    ratioText() {
      const r = this.summary.criticalRatio
      if (!r || r.status !== 'OK' || r.value == null) return ''
      return '占在科 ' + (r.value * 100).toFixed(1) + '% · 四类支持并集'
    },
    avgStaySub() {
      const a = this.summary.avgStayDays
      if (a && a.status === 'OK' && this.summary.avgStayPatientCount != null) {
        return this.summary.avgStayPatientCount + ' 例已出科'
      }
      return ''
    },
    flowPoints() {
      const f = this.summary.dailyFlow
      return f && f.value ? f.value : []
    },
    flowTotals() {
      const f = this.summary.dailyFlow
      if (!f || f.status !== 'OK') return { inCount: null, outCount: null, net: null }
      const inCount = this.flowPoints.reduce((sum, point) => sum + (Number(point.inCount) || 0), 0)
      const outCount = this.flowPoints.reduce((sum, point) => sum + (Number(point.outCount) || 0), 0)
      return { inCount, outCount, net: inCount - outCount }
    },
    bedDataPending() {
      return this.summary.bedUsage && this.summary.bedUsage.status === 'PENDING_SOURCE'
    },
    flowUnknown() {
      const f = this.summary.dailyFlow
      return f && f.status === 'UNKNOWN'
    },
    flowNote() {
      const f = this.summary.dailyFlow
      return (f && f.note) || '患者流转查询失败'
    },
    qualityUnknown() {
      const q = this.summary.qualityCards
      return q && q.status === 'UNKNOWN'
    },
    qualityCards() {
      const q = this.summary.qualityCards
      return q && q.value ? q.value : []
    },
    organItems() {
      const block = (key) => {
        const item = this.summary[key]
        return item && item.status === 'OK' && item.value != null
          ? { value: Number(item.value) || 0, status: 'OK' }
          : { value: 0, status: item && item.status ? item.status : 'UNKNOWN' }
      }
      const vent = block('ventilatedCount')
      const vaso = block('vasopressorCount')
      const crrt = block('crrtCount')
      const ecmo = block('ecmoCount')
      const inCount = this.summary.inCount && this.summary.inCount.value != null
        ? this.summary.inCount.value : 0
      const pct = (v) => inCount > 0 ? Math.min(100, Math.round((v / inCount) * 100)) : 0
      return [
        { label: '机械通气', ...vent, percent: vent.status === 'OK' ? pct(vent.value) : 0, color: '#c8783f' },
        { label: '血管活性药', ...vaso, percent: vaso.status === 'OK' ? pct(vaso.value) : 0, color: '#b35e52' },
        { label: 'CRRT', ...crrt, percent: crrt.status === 'OK' ? pct(crrt.value) : 0, color: '#4e8d95' },
        { label: 'ECMO', ...ecmo, percent: ecmo.status === 'OK' ? pct(ecmo.value) : 0, color: '#7378a6' }
      ]
    }
  },
  watch: {
    departCode() {
      this.load()
    },
    summary() {
      this.$nextTick(() => {
        this.renderFlow()
      })
    }
  },
  created() {
    this.load()
  },
  beforeUnmount() {
    Object.values(this.charts).forEach((c) => { if (c) c.dispose() })
    this.charts = {}
  },
  methods: {
    async load() {
      this.loading = true
      try {
        const s = await fetchCockpitSummary(this.departCode, this.days)
        this.summary = s || { notes: [] }
        this.updatedAt = new Date().toLocaleString('zh-CN', { hour12: false })
      } catch (e) {
        const msg = (e && (e.message || e.msg)) || ''
        ElMessage.error(msg.includes('权限') ? ('无权查看该科室驾驶舱：' + msg) : ('驾驶舱加载失败：' + (msg || '请稍后重试')))
        this.summary = { notes: [] }
      } finally {
        this.loading = false
      }
    },
    distPoints(block) {
      return block && block.value ? block.value : []
    },
    distTotal(block) {
      return this.distPoints(block).reduce((sum, b) => sum + (b.count || 0), 0)
    },
    scoreRows(block) {
      const points = this.distPoints(block)
      const total = this.distTotal(block)
      if (!total) return []
      return points.map((point) => {
        const count = Number(point.count) || 0
        const share = (count / total) * 100
        return { ...point, count, percent: share, share: share.toFixed(1) }
      })
    },
    distUnknown(block) {
      return block && block.status === 'UNKNOWN'
    },
    blockNote(block) {
      return (block && block.note) || ''
    },
    qualityStatusText(status) {
      return ({
        PENDING_SOURCE: '待接数据源',
        NO_DATA: '本月无数据',
        MANUAL: '人工填报',
        PLACEHOLDER: '口径待实现',
        NOT_CALC: '尚未计算',
        ERROR: '计算异常'
      })[status] || '暂不可用'
    },
    formatMetric(value) {
      const number = Number(value)
      return Number.isFinite(number) ? String(Number(number.toFixed(1))) : '—'
    },
    getChart(key, el) {
      const chart = echarts.getInstanceByDom(el) || echarts.init(el)
      this.charts[key] = chart
      // clear() 再 setOption：复用实例时 notMerge 会坏掉 axis 关联（tooltip 失效根因）
      chart.clear()
      return chart
    },
    renderFlow() {
      const el = this.$refs.flowChart
      if (!el || !this.flowPoints.length) return
      const chart = this.getChart('flow', el)
      chart.setOption({
        backgroundColor: 'transparent',
        animationDuration: 300,
        grid: { left: 36, right: 12, top: 30, bottom: 26 },
        legend: { top: 0, right: 0, itemWidth: 12, itemHeight: 8, textStyle: { color: '#78716c', fontSize: 11 } },
        tooltip: {
          trigger: 'axis',
          confine: true,
          backgroundColor: 'rgba(255,255,255,0.97)',
          borderColor: '#e7e5e4',
          textStyle: { color: '#292524', fontSize: 11 },
          formatter: (ps) => {
            if (!ps || !ps.length) return ''
            const day = ps[0].axisValue
            const inP = ps.find((x) => x.seriesName === '入科')
            const outP = ps.find((x) => x.seriesName === '出科')
            return day + '<br/>入科：<b>' + (inP ? inP.value : 0) + '</b> 人<br/>出科：<b>' + (outP ? outP.value : 0) + '</b> 人'
          }
        },
        xAxis: {
          type: 'category',
          data: this.flowPoints.map((x) => x.day.slice(5)),
          axisLabel: { color: '#a8a29e', fontSize: 10, hideOverlap: true, interval: 'auto' },
          axisLine: { lineStyle: { color: '#e7e5e4' } },
          axisTick: { show: false }
        },
        yAxis: {
          type: 'value',
          minInterval: 1,
          axisLabel: { color: '#a8a29e', fontSize: 10 },
          splitLine: { lineStyle: { color: '#f5f5f4' } }
        },
        series: [
          {
            name: '入科', type: 'bar', barMaxWidth: 14,
            data: this.flowPoints.map((x) => x.inCount),
            itemStyle: { color: '#d18b4c', borderRadius: [3, 3, 0, 0] }
          },
          {
            name: '出科', type: 'bar', barMaxWidth: 14,
            data: this.flowPoints.map((x) => x.outCount),
            itemStyle: { color: '#4f9298', borderRadius: [3, 3, 0, 0] }
          }
        ]
      })
      chart.resize()
    }
  }
}
</script>
<style scoped>
.cockpit-page { min-height: calc(100vh - 56px); padding: 18px 22px 34px; background: #f3f5f4; color: #1d3035; }
.cockpit-hero { position: relative; display: grid; grid-template-columns: minmax(260px, 1fr) auto auto; align-items: center; gap: 26px; min-height: 124px; padding: 22px 26px; overflow: hidden; border-radius: 16px; background: linear-gradient(118deg, #19363f 0%, #28515a 64%, #3a696b 100%); box-shadow: 0 12px 28px rgba(24, 56, 64, .18); }
.cockpit-hero::after { content: ''; position: absolute; right: -55px; top: -100px; width: 360px; height: 300px; border: 1px solid rgba(255, 255, 255, .12); border-radius: 50%; box-shadow: 0 0 0 28px rgba(255, 255, 255, .025), 0 0 0 56px rgba(255, 255, 255, .02); transform: rotate(-18deg); pointer-events: none; }
.hero-copy, .hero-meta, .hero-actions { position: relative; z-index: 1; }
.hero-eyebrow { display: flex; align-items: center; gap: 8px; color: rgba(232, 245, 243, .68); font-size: 10px; letter-spacing: 1.5px; text-transform: uppercase; }
.live-dot { width: 7px; height: 7px; border-radius: 50%; background: #9ed2a2; box-shadow: 0 0 0 4px rgba(158, 210, 162, .14); }
.hero-copy h1 { margin: 9px 0 4px; color: #fff; font-size: 25px; line-height: 1.15; font-weight: 700; letter-spacing: .5px; }
.hero-copy p { margin: 0; color: rgba(235, 245, 243, .7); font-size: 12px; }
.hero-meta { min-width: 180px; padding-left: 20px; border-left: 1px solid rgba(255, 255, 255, .2); }
.meta-line { display: flex; justify-content: space-between; gap: 18px; color: rgba(233, 244, 242, .65); font-size: 11px; line-height: 1.9; }
.meta-line b { color: #fff; font-weight: 600; font-variant-numeric: tabular-nums; }
.meta-caption { margin-top: 5px; color: rgba(233, 244, 242, .45); font-size: 9px; white-space: nowrap; }
.hero-actions { display: flex; align-items: center; gap: 8px; }
.hero-actions :deep(.el-radio-button__inner) { padding: 7px 10px; border-color: rgba(255, 255, 255, .24); color: rgba(255, 255, 255, .78); background: rgba(255, 255, 255, .08); box-shadow: none; font-size: 11px; }
.hero-actions :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner) { color: #20414a; background: #e8f1ec; border-color: #e8f1ec; box-shadow: -1px 0 0 0 #e8f1ec; }
.hero-actions :deep(.el-button) { --el-button-bg-color: rgba(255, 255, 255, .1); --el-button-border-color: rgba(255, 255, 255, .26); --el-button-text-color: #f4faf8; --el-button-hover-bg-color: rgba(255, 255, 255, .18); --el-button-hover-border-color: rgba(255, 255, 255, .4); --el-button-hover-text-color: #fff; }
.hero-actions :deep(.el-button.is-text) { border: 0; background: transparent; }
.period-strip { display: flex; align-items: center; flex-wrap: wrap; gap: 14px; margin: 12px 2px 14px; color: #829196; font-size: 10px; }
.period-label { color: #49636a; font-weight: 700; letter-spacing: .5px; }
.period-item { display: inline-flex; align-items: center; gap: 5px; }
.period-dot { width: 6px; height: 6px; border-radius: 50%; }
.period-dot.today { background: #d28754; }.period-dot.trend { background: #5e9ca0; }.period-dot.quality { background: #7d82ae; }
.data-gap { display: inline-flex; align-items: center; gap: 5px; margin-left: auto; color: #a36b35; }
.data-gap i { width: 6px; height: 6px; border: 1px solid #cf934e; border-radius: 50%; }
.kpi-row { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 11px; }
.kpi-row :deep(.snapshot-card) { position: relative; min-height: 96px; padding: 15px 16px 13px; overflow: hidden; border: 1px solid #e2e9e7; border-radius: 12px; background: #fff; box-shadow: 0 3px 12px rgba(45, 72, 78, .055); }
.kpi-row :deep(.snapshot-card)::before { content: ''; position: absolute; left: 0; top: 0; bottom: 0; width: 3px; background: #8ab0a6; }
.kpi-row :deep(.critical-card)::before { background: #c87863; }.kpi-row :deep(.admission-card)::before { background: #d18b4c; }.kpi-row :deep(.discharge-card)::before { background: #5c9b9b; }.kpi-row :deep(.stay-card)::before { background: #8189ad; }
.kpi-row :deep(.card-label) { color: #71848a; font-size: 11px; }.kpi-row :deep(.num) { color: #20383e; font-size: 27px; }.kpi-row :deep(.tone-up .num) { color: #a65e36; }.kpi-row :deep(.tone-down .num) { color: #347d82; }.kpi-row :deep(.card-sub) { color: #8a9a9e; }
.primary-grid { display: grid; grid-template-columns: minmax(0, 1.45fr) minmax(330px, .75fr); gap: 13px; margin-top: 14px; align-items: stretch; }
.secondary-grid { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) minmax(260px, .86fr); gap: 13px; margin-top: 13px; align-items: stretch; }
.panel { min-width: 0; border: 1px solid #e0e8e6; border-radius: 13px; background: #fff; box-shadow: 0 3px 14px rgba(45, 72, 78, .045); overflow: hidden; }
.panel-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; min-height: 57px; padding: 11px 16px 10px; border-bottom: 1px solid #edf1f0; }
.panel-kicker { display: block; margin-bottom: 4px; color: #90a1a3; font-size: 9px; font-weight: 700; letter-spacing: 1.2px; }
.panel-title { margin: 0; color: #263d43; font-size: 14px; font-weight: 700; line-height: 1.2; }
.panel-context { color: #99a7aa; font-size: 10px; white-space: nowrap; }.panel-context span { margin: 0 3px; color: #c7d0ce; }.panel-link { color: #58888a; font-size: 11px; text-decoration: none; }.panel-link span { margin-left: 3px; font-size: 14px; }
.panel-body { padding: 12px 16px; }.flow-panel, .quality-panel { min-height: 356px; }.flow-summary { display: flex; gap: 34px; padding: 13px 17px 0; }.flow-summary > div { display: flex; align-items: baseline; gap: 4px; }.flow-summary span { color: #86979b; font-size: 10px; }.flow-summary b { margin-left: 4px; color: #304b51; font-size: 21px; font-weight: 700; font-variant-numeric: tabular-nums; }.flow-summary i { color: #a1adaf; font-size: 10px; font-style: normal; }.flow-summary .net-positive b { color: #4c8a78; }.flow-summary .net-negative b { color: #bd6b61; }
.flow-body { padding-top: 5px; }.flow-chart { width: 100%; height: 268px; }
.q-list { display: flex; flex-direction: column; gap: 7px; padding: 10px 14px 0; }.q-card { display: flex; align-items: center; justify-content: space-between; gap: 10px; min-height: 43px; padding: 7px 10px; border: 1px solid #edf1ef; border-radius: 8px; background: #fbfcfb; }.q-main { min-width: 0; }.q-name { overflow: hidden; color: #53686d; font-size: 11px; line-height: 1.35; text-overflow: ellipsis; white-space: nowrap; }.q-sub { margin-top: 3px; overflow: hidden; color: #a0adaf; font-size: 9px; text-overflow: ellipsis; white-space: nowrap; }.q-result { flex-shrink: 0; text-align: right; }.q-result b { color: #a96842; font-size: 19px; font-weight: 700; font-variant-numeric: tabular-nums; }.q-result i { margin-left: 3px; color: #94a1a3; font-size: 9px; font-style: normal; }.q-state { color: #9da9ab; font-size: 10px; }.q-state.pending { color: #b77a3f; }.quality-foot { margin: 11px 14px 13px; padding-top: 9px; border-top: 1px solid #f0f3f2; color: #a0adaf; font-size: 9px; line-height: 1.5; }
.score-content { display: grid; grid-template-columns: 87px 1fr; align-items: center; gap: 12px; min-height: 148px; padding: 15px 16px 17px; }.score-total { display: flex; flex-direction: column; align-items: center; justify-content: center; height: 76px; border-right: 1px solid #edf1ef; }.score-total b { color: #355f63; font-size: 27px; line-height: 1; }.score-total span { margin-top: 6px; color: #9aa8aa; font-size: 10px; }.score-rows { display: flex; flex-direction: column; gap: 9px; }.score-row { display: grid; grid-template-columns: 42px minmax(50px, 1fr) 25px 34px; align-items: center; gap: 7px; }.score-range { color: #75878b; font-size: 10px; }.score-track { height: 7px; overflow: hidden; border-radius: 5px; background: #edf2f0; }.score-track i { display: block; height: 100%; min-width: 2px; border-radius: inherit; }.score-row b { color: #4b6267; font-size: 11px; text-align: right; }.score-row small { color: #a1adaf; font-size: 9px; text-align: right; }
.organ-panel { min-height: 224px; }.organ-list { display: flex; flex-direction: column; gap: 14px; padding: 17px 16px 11px; }.organ-item { display: grid; grid-template-columns: 70px minmax(50px, 1fr) 47px; align-items: center; gap: 10px; }.organ-label { color: #607479; font-size: 11px; }.organ-bar { height: 7px; overflow: hidden; border-radius: 5px; background: #edf2f0; }.organ-bar-fill { height: 100%; border-radius: inherit; transition: width .4s ease; }.organ-num { color: #304a50; font-size: 15px; font-weight: 700; text-align: right; font-variant-numeric: tabular-nums; }.organ-num i { margin-left: 2px; color: #9aa7a9; font-size: 9px; font-style: normal; font-weight: 400; }.organ-num.value-unknown { color: #aab4b5; }.organ-foot { margin: 0 16px 15px; padding-top: 10px; border-top: 1px solid #f0f3f2; color: #a0adaf; font-size: 9px; line-height: 1.55; }
.state-box { display: flex; min-height: 160px; flex-direction: column; align-items: center; justify-content: center; gap: 8px; }.state-box.small { min-height: 120px; }.state-title { color: #9ca9aa; font-size: 11px; }.notes-list { margin: 0; padding-left: 18px; color: #53686d; font-size: 12px; line-height: 2; }.notes-foot { margin: 10px 0 0; color: #9ca9aa; font-size: 11px; }
@media (max-width: 1180px) { .cockpit-hero { grid-template-columns: 1fr auto; }.hero-actions { grid-column: 1 / -1; justify-content: flex-end; }.secondary-grid { grid-template-columns: 1fr 1fr; }.organ-panel { grid-column: 1 / -1; } }
@media (max-width: 820px) { .cockpit-page { padding: 12px; }.cockpit-hero { grid-template-columns: 1fr; gap: 14px; }.hero-meta { padding-left: 0; border-left: 0; }.hero-actions { grid-column: auto; justify-content: flex-start; flex-wrap: wrap; }.kpi-row { grid-template-columns: repeat(2, 1fr); }.primary-grid, .secondary-grid { grid-template-columns: 1fr; }.organ-panel { grid-column: auto; }.panel-context { white-space: normal; text-align: right; } }
@media (max-width: 480px) { .kpi-row { grid-template-columns: 1fr; }.period-strip { align-items: flex-start; flex-direction: column; gap: 7px; }.data-gap { margin-left: 0; }.flow-summary { gap: 14px; }.score-content { grid-template-columns: 70px 1fr; padding-left: 10px; padding-right: 10px; }.score-row { grid-template-columns: 36px minmax(40px, 1fr) 22px 31px; gap: 5px; } }
</style>
