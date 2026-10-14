<template>
  <div class="cockpit-page">
    <header class="cockpit-hero">
      <div class="hero-radar" aria-hidden="true">
        <span class="radar-ring ring-one"></span>
        <span class="radar-ring ring-two"></span>
        <span class="radar-sweep"></span>
        <span class="radar-core"></span>
        <svg class="hero-wave" viewBox="0 0 420 80" preserveAspectRatio="none">
          <path class="wave-shadow" d="M0 43 L74 43 L91 42 L104 18 L119 65 L136 43 L204 43 L221 42 L234 27 L247 55 L261 43 L420 43" />
          <path class="wave-line" d="M0 43 L74 43 L91 42 L104 18 L119 65 L136 43 L204 43 L221 42 L234 27 L247 55 L261 43 L420 43" />
        </svg>
      </div>
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
      <span v-if="bedDataPending" class="data-gap"><i></i>床位资源暂不可用</span>
    </div>

    <section class="kpi-row" aria-label="科室核心指标">
      <StatCard class="snapshot-card census-card" label="当前在科患者" :block="summary.inCount" unit="人" />
      <StatCard class="snapshot-card critical-card" label="器官支持患者" :block="summary.criticalCount" unit="人" :sub="ratioText" sub-warn />
      <StatCard class="snapshot-card admission-card" label="今日入科 / 转入" :block="summary.todayInCount" unit="人" />
      <StatCard class="snapshot-card discharge-card" label="今日转出 / 出科" :block="summary.todayOutCount" unit="人" />
      <StatCard class="snapshot-card stay-card" :label="'近 ' + days + ' 天平均 ICU 住院日'" :block="summary.avgStayDays" unit="天" :sub="avgStaySub" />
      <StatCard class="snapshot-card bed-card" label="当前床位使用率" :block="bedUsageRateBlock" unit="%" :sub="bedUsageSub" :sub-warn="bedUsagePercent >= 90" />
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

    <section class="resource-grid" aria-label="床位与设备资源">
      <article class="panel bed-panel">
        <div class="panel-head">
          <div><span class="panel-kicker">BED CAPACITY</span><h2 class="panel-title">床位负荷</h2></div>
          <div class="panel-context">当前实时快照 <span>·</span> 有效开放床位</div>
        </div>
        <div v-if="!bedOverview" class="state-box small"><div class="state-title">{{ blockNote(summary.bedUsage) || '床位资源暂无数据' }}</div></div>
        <div v-else class="bed-body">
          <div class="bed-gauge" :style="{ '--bed-rate': bedUsagePercent + '%' }">
            <div class="bed-gauge-inner"><b>{{ bedUsagePercent.toFixed(1) }}</b><span>%</span><small>床位使用率</small></div>
          </div>
          <div class="bed-metrics">
            <div><span>开放床位</span><b>{{ bedOverview.openBeds }}</b><i>张</i></div>
            <div><span>当前占用</span><b>{{ bedOverview.occupiedBeds }}</b><i>张</i></div>
            <div :class="{ alert: bedOverview.availableBeds <= 1 }"><span>可用床位</span><b>{{ bedOverview.availableBeds }}</b><i>张</i></div>
            <div><span>未匹配床位患者</span><b>{{ bedOverview.unassignedPatients }}</b><i>人</i></div>
          </div>
        </div>
        <div class="resource-foot">占用仅统计床号能匹配有效开放床位的当前在科患者，避免无效床号造成使用率超过 100%。</div>
      </article>

      <article class="panel device-panel">
        <div class="panel-head">
          <div><span class="panel-kicker">DEVICE CONNECTIVITY</span><h2 class="panel-title">设备接入态势</h2></div>
          <div class="panel-context">配置 / 患者绑定 / 数据接收</div>
        </div>
        <div v-if="!deviceRows.length" class="state-box small"><div class="state-title">{{ blockNote(summary.deviceOverview) || '设备配置暂无数据' }}</div></div>
        <div v-else class="device-list">
          <div v-for="d in deviceRows" :key="d.type || d.label" class="device-row">
            <div class="device-name"><span class="device-pulse" :class="{ warn: d.nonReceiving > 0 }"></span><b>{{ d.label }}</b><small>{{ d.latestUpdateText }}</small></div>
            <div class="device-track"><i :style="{ width: d.receivingRate + '%' }"></i></div>
            <div class="device-stats"><span><b>{{ d.configured }}</b> 配置</span><span><b>{{ d.bound }}</b> 绑定</span><span class="receiving"><b>{{ d.receiving }}</b> 接收</span><span :class="{ warn: d.nonReceiving > 0 }"><b>{{ d.nonReceiving }}</b> 非接收</span></div>
          </div>
        </div>
        <div class="resource-foot">“接收/非接收”来自采集链路状态，不代表设备维修、库存或临床可用性；CRRT 未接采集套件会列入非接收。</div>
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
      return this.summary.bedUsage && this.summary.bedUsage.status !== 'OK'
    },
    bedOverview() {
      const block = this.summary.bedUsage
      return block && block.status === 'OK' ? block.value : null
    },
    bedUsagePercent() {
      const rate = this.bedOverview && Number(this.bedOverview.occupancyRate)
      return Number.isFinite(rate) ? Math.max(0, Math.min(100, rate * 100)) : 0
    },
    bedUsageRateBlock() {
      const block = this.summary.bedUsage
      if (!block || block.status !== 'OK' || !block.value) {
        return block || { status: 'UNKNOWN', value: null, note: '床位资源未加载' }
      }
      return { status: 'OK', value: this.bedUsagePercent.toFixed(1) }
    },
    bedUsageSub() {
      if (!this.bedOverview) return ''
      return `${this.bedOverview.occupiedBeds}/${this.bedOverview.openBeds} 张 · 可用 ${this.bedOverview.availableBeds} 张`
    },
    deviceRows() {
      const block = this.summary.deviceOverview
      const rows = block && block.status === 'OK' && Array.isArray(block.value) ? block.value : []
      return rows.map((row) => {
        const configured = Number(row.configured) || 0
        const receiving = Number(row.receiving) || 0
        return {
          ...row,
          configured,
          receiving,
          bound: Number(row.bound) || 0,
          nonReceiving: Math.max(0, configured - receiving),
          receivingRate: configured > 0 ? Math.min(100, (receiving / configured) * 100) : 0,
          latestUpdateText: row.latestUpdate ? String(row.latestUpdate).slice(5, 16) + ' 更新' : '无更新时间'
        }
      })
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
  mounted() {
    // 大屏锁的是视口高度：窗口一变，趋势图容器就跟着变。ECharts 不会自己感知
    // 容器尺寸变化，不显式 resize 就会把图形留在旧尺寸上（被裁切或留白）。
    this.handleResize = () => {
      Object.values(this.charts).forEach((c) => { if (c) c.resize() })
    }
    window.addEventListener('resize', this.handleResize)
  },
  beforeUnmount() {
    window.removeEventListener('resize', this.handleResize)
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
        legend: { top: 0, right: 0, itemWidth: 12, itemHeight: 8, textStyle: { color: '#77989b', fontSize: 11 } },
        tooltip: {
          trigger: 'axis',
          confine: true,
          backgroundColor: 'rgba(8,27,35,0.96)',
          borderColor: 'rgba(103,220,208,.3)',
          textStyle: { color: '#dff6f2', fontSize: 11 },
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
          axisLabel: { color: '#58777c', fontSize: 10, hideOverlap: true, interval: 'auto' },
          axisLine: { lineStyle: { color: 'rgba(107,166,165,.18)' } },
          axisTick: { show: false }
        },
        yAxis: {
          type: 'value',
          minInterval: 1,
          axisLabel: { color: '#58777c', fontSize: 10 },
          splitLine: { lineStyle: { color: 'rgba(107,166,165,.1)' } }
        },
        series: [
          {
            name: '入科', type: 'bar', barMaxWidth: 14,
            data: this.flowPoints.map((x) => x.inCount),
            itemStyle: { color: '#ffae57', shadowBlur: 10, shadowColor: 'rgba(255,174,87,.25)', borderRadius: [3, 3, 0, 0] }
          },
          {
            name: '出科', type: 'bar', barMaxWidth: 14,
            data: this.flowPoints.map((x) => x.outCount),
            itemStyle: { color: '#57d0cf', shadowBlur: 10, shadowColor: 'rgba(87,208,207,.25)', borderRadius: [3, 3, 0, 0] }
          }
        ]
      })
      chart.resize()
    }
  }
}
</script>
<style scoped>
.cockpit-page { --cyan: #52d7d2; --cyan-soft: #8ff4df; --amber: #ffae57; --red: #ff766f; --panel: rgba(13, 37, 47, .94); min-height: calc(100vh - 56px); padding: 18px 22px 34px; overflow: hidden; color: #eaf7f5; background: radial-gradient(circle at 82% 3%, rgba(37, 126, 128, .22), transparent 30%), radial-gradient(circle at 12% 36%, rgba(40, 103, 119, .13), transparent 30%), linear-gradient(145deg, #071820 0%, #0b222b 48%, #091c25 100%); }
.cockpit-hero { position: relative; display: grid; grid-template-columns: minmax(260px, 1fr) auto auto; align-items: center; gap: 26px; min-height: 124px; padding: 22px 26px; overflow: hidden; border-radius: 16px; background: linear-gradient(118deg, #19363f 0%, #28515a 64%, #3a696b 100%); box-shadow: 0 12px 28px rgba(24, 56, 64, .18); }
.cockpit-hero { border: 1px solid rgba(126, 231, 218, .2); background: linear-gradient(110deg, rgba(20, 64, 73, .98), rgba(17, 54, 66, .96) 48%, rgba(24, 82, 83, .92)); box-shadow: 0 18px 42px rgba(0, 0, 0, .3), inset 0 1px 0 rgba(255, 255, 255, .08); }
.cockpit-hero::before { content: ''; position: absolute; inset: 0; opacity: .13; background-image: linear-gradient(rgba(111, 220, 211, .22) 1px, transparent 1px), linear-gradient(90deg, rgba(111, 220, 211, .22) 1px, transparent 1px); background-size: 30px 30px; mask-image: linear-gradient(90deg, transparent, #000 40%, #000); }
.cockpit-hero::after { content: ''; position: absolute; right: -45px; top: -120px; width: 390px; height: 350px; border: 1px solid rgba(113, 239, 218, .18); border-radius: 50%; box-shadow: 0 0 0 30px rgba(97, 229, 210, .025), 0 0 0 62px rgba(97, 229, 210, .02), inset 0 0 55px rgba(76, 217, 202, .05); pointer-events: none; }
.hero-radar { position: absolute; right: 242px; top: 50%; width: 170px; height: 170px; transform: translateY(-50%); opacity: .82; pointer-events: none; }
.radar-ring { position: absolute; inset: 20px; border: 1px solid rgba(114, 238, 217, .18); border-radius: 50%; }.radar-ring.ring-two { inset: 47px; border-color: rgba(114, 238, 217, .28); }
.radar-sweep { position: absolute; inset: 20px; overflow: hidden; border-radius: 50%; background: conic-gradient(from 20deg, transparent 0 78%, rgba(93, 238, 215, .18) 90%, rgba(114, 244, 220, .55)); animation: radar-spin 6s linear infinite; }
.radar-core { position: absolute; left: 50%; top: 50%; width: 8px; height: 8px; border-radius: 50%; background: #9ff6df; box-shadow: 0 0 0 5px rgba(126, 239, 216, .12), 0 0 18px #62e3cf; transform: translate(-50%, -50%); }
.hero-wave { position: absolute; left: -260px; top: 57px; width: 420px; height: 60px; opacity: .5; }.wave-line, .wave-shadow { fill: none; vector-effect: non-scaling-stroke; }.wave-shadow { stroke: rgba(64, 238, 214, .25); stroke-width: 6; filter: blur(5px); }.wave-line { stroke: #83f5d9; stroke-width: 1.5; stroke-dasharray: 560; stroke-dashoffset: 560; animation: wave-draw 4s linear infinite; }
@keyframes radar-spin { to { transform: rotate(360deg); } } @keyframes wave-draw { 45%, 100% { stroke-dashoffset: -560; } }
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
.period-strip { display: flex; align-items: center; flex-wrap: wrap; gap: 14px; margin: 12px 2px 14px; color: #77949a; font-size: 10px; }
.period-label { color: #b8d5d4; font-weight: 700; letter-spacing: .5px; }
.period-item { display: inline-flex; align-items: center; gap: 5px; }
.period-dot { width: 6px; height: 6px; border-radius: 50%; }
.period-dot.today { background: #d28754; }.period-dot.trend { background: #5e9ca0; }.period-dot.quality { background: #7d82ae; }
.data-gap { display: inline-flex; align-items: center; gap: 5px; margin-left: auto; color: #a36b35; }
.data-gap i { width: 6px; height: 6px; border: 1px solid #cf934e; border-radius: 50%; }
.kpi-row { display: grid; grid-template-columns: repeat(6, minmax(0, 1fr)); gap: 11px; }
.kpi-row :deep(.snapshot-card) { position: relative; min-height: 105px; padding: 16px 18px 14px; overflow: hidden; border: 1px solid rgba(110, 202, 195, .16); border-radius: 13px; background: linear-gradient(145deg, rgba(19, 53, 64, .96), rgba(11, 35, 45, .94)); box-shadow: 0 8px 24px rgba(0, 0, 0, .18), inset 0 1px 0 rgba(255, 255, 255, .035); transition: transform .25s ease, border-color .25s ease, box-shadow .25s ease; }
.kpi-row :deep(.snapshot-card:hover) { z-index: 2; border-color: rgba(100, 234, 215, .42); box-shadow: 0 14px 30px rgba(0, 0, 0, .3), 0 0 24px rgba(58, 214, 196, .08); transform: translateY(-4px); }
.kpi-row :deep(.snapshot-card)::before { content: ''; position: absolute; left: 0; top: 18px; bottom: 18px; width: 3px; border-radius: 0 4px 4px 0; background: #61cfc2; box-shadow: 0 0 12px currentColor; }
.kpi-row :deep(.snapshot-card)::after { position: absolute; right: 13px; top: 10px; color: rgba(159, 235, 222, .16); font-size: 26px; font-weight: 800; letter-spacing: -2px; }.kpi-row :deep(.census-card)::after { content: '01'; }.kpi-row :deep(.critical-card)::after { content: '02'; }.kpi-row :deep(.admission-card)::after { content: '03'; }.kpi-row :deep(.discharge-card)::after { content: '04'; }.kpi-row :deep(.stay-card)::after { content: '05'; }.kpi-row :deep(.bed-card)::after { content: '06'; }
.kpi-row :deep(.critical-card)::before { background: #c87863; }.kpi-row :deep(.admission-card)::before { background: #d18b4c; }.kpi-row :deep(.discharge-card)::before { background: #5c9b9b; }.kpi-row :deep(.stay-card)::before { background: #8189ad; }.kpi-row :deep(.bed-card)::before { background: #e1a85d; }
.kpi-row :deep(.card-label) { color: #83a4a8; font-size: 11px; letter-spacing: .3px; }.kpi-row :deep(.num) { color: #effffc; font-size: 30px; text-shadow: 0 0 20px rgba(119, 242, 220, .17); }.kpi-row :deep(.unit) { color: #759398; }.kpi-row :deep(.card-sub) { color: #78969a; }
.primary-grid { display: grid; grid-template-columns: minmax(0, 1.45fr) minmax(330px, .75fr); gap: 13px; margin-top: 14px; align-items: stretch; }
.resource-grid { display: grid; grid-template-columns: minmax(330px, .72fr) minmax(0, 1.28fr); gap: 13px; margin-top: 13px; }
.secondary-grid { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) minmax(260px, .86fr); gap: 13px; margin-top: 13px; align-items: stretch; }
.panel { position: relative; min-width: 0; overflow: hidden; border: 1px solid rgba(100, 185, 181, .15); border-radius: 14px; background: var(--panel); box-shadow: 0 12px 30px rgba(0, 0, 0, .2), inset 0 1px 0 rgba(255, 255, 255, .025); }
.panel::after { content: ''; position: absolute; left: 0; top: 0; width: 46px; height: 1px; background: var(--cyan); box-shadow: 0 0 13px var(--cyan); }
.panel-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; min-height: 61px; padding: 12px 16px 10px; border-bottom: 1px solid rgba(119, 185, 181, .1); background: linear-gradient(90deg, rgba(39, 101, 105, .1), transparent); }
.panel-kicker { display: block; margin-bottom: 4px; color: #4f7f84; font-size: 9px; font-weight: 700; letter-spacing: 1.4px; }
.panel-title { margin: 0; color: #eaf8f6; font-size: 14px; font-weight: 700; line-height: 1.2; }
.panel-context { color: #64868a; font-size: 10px; white-space: nowrap; }.panel-context span { margin: 0 3px; color: #46666b; }.panel-link { color: #72d8cd; font-size: 11px; text-decoration: none; }.panel-link span { margin-left: 3px; font-size: 14px; }
.panel-body { padding: 12px 16px; }.flow-panel, .quality-panel { min-height: 356px; }.flow-summary { display: flex; gap: 34px; padding: 14px 17px 0; }.flow-summary > div { display: flex; align-items: baseline; gap: 4px; }.flow-summary span { color: #698d91; font-size: 10px; }.flow-summary b { margin-left: 4px; color: #dff7f3; font-size: 23px; font-weight: 700; font-variant-numeric: tabular-nums; }.flow-summary i { color: #5f8085; font-size: 10px; font-style: normal; }.flow-summary .net-positive b { color: #6fe0bd; }.flow-summary .net-negative b { color: #ff7d75; }
.flow-body { padding-top: 5px; }.flow-chart { width: 100%; height: 268px; }
.q-list { display: flex; flex-direction: column; gap: 7px; padding: 10px 14px 0; }.q-card { display: flex; align-items: center; justify-content: space-between; gap: 10px; min-height: 43px; padding: 7px 10px; border: 1px solid rgba(112, 186, 181, .11); border-radius: 8px; background: linear-gradient(90deg, rgba(34, 78, 86, .48), rgba(17, 48, 58, .38)); }.q-main { min-width: 0; }.q-name { overflow: hidden; color: #b6cece; font-size: 11px; line-height: 1.35; text-overflow: ellipsis; white-space: nowrap; }.q-sub { margin-top: 3px; overflow: hidden; color: #5f8085; font-size: 9px; text-overflow: ellipsis; white-space: nowrap; }.q-result { flex-shrink: 0; text-align: right; }.q-result b { color: var(--amber); font-size: 20px; font-weight: 700; text-shadow: 0 0 14px rgba(255, 174, 87, .18); font-variant-numeric: tabular-nums; }.q-result i { margin-left: 3px; color: #68898c; font-size: 9px; font-style: normal; }.q-state { color: #6e8d91; font-size: 10px; }.q-state.pending { color: #d79a55; }.quality-foot { margin: 11px 14px 13px; padding-top: 9px; border-top: 1px solid rgba(120, 180, 180, .1); color: #58777c; font-size: 9px; line-height: 1.5; }
.bed-body { display: grid; grid-template-columns: 142px 1fr; align-items: center; gap: 18px; min-height: 184px; padding: 18px 20px 8px; }
.bed-gauge { --bed-rate: 0%; position: relative; width: 132px; height: 132px; border-radius: 50%; background: conic-gradient(#ffae57 0 var(--bed-rate), rgba(108, 169, 169, .12) var(--bed-rate) 100%); box-shadow: 0 0 28px rgba(255, 174, 87, .1); }
.bed-gauge::before { content: ''; position: absolute; inset: 9px; border: 1px solid rgba(139, 210, 203, .12); border-radius: 50%; background: #0e2a34; box-shadow: inset 0 0 24px rgba(0, 0, 0, .28); }
.bed-gauge-inner { position: absolute; inset: 0; z-index: 1; display: flex; align-items: baseline; justify-content: center; padding-top: 41px; color: #f4fffd; }
.bed-gauge-inner b { font-size: 31px; line-height: 1; font-variant-numeric: tabular-nums; text-shadow: 0 0 18px rgba(255, 174, 87, .22); }.bed-gauge-inner span { margin-left: 2px; color: #c69660; font-size: 11px; }.bed-gauge-inner small { position: absolute; top: 80px; color: #698e91; font-size: 10px; font-weight: 400; }
.bed-metrics { display: grid; grid-template-columns: 1fr 1fr; gap: 9px; }.bed-metrics > div { padding: 10px 11px; border: 1px solid rgba(111, 184, 181, .1); border-radius: 8px; background: rgba(24, 61, 70, .42); }.bed-metrics span { display: block; margin-bottom: 4px; color: #668a8e; font-size: 9px; }.bed-metrics b { color: #dff7f3; font-size: 20px; font-variant-numeric: tabular-nums; }.bed-metrics i { margin-left: 3px; color: #66868a; font-size: 9px; font-style: normal; }.bed-metrics .alert b { color: #ffae57; text-shadow: 0 0 12px rgba(255, 174, 87, .18); }
.device-list { display: flex; min-height: 184px; flex-direction: column; justify-content: center; gap: 12px; padding: 16px 18px 8px; }.device-row { display: grid; grid-template-columns: 115px minmax(90px, 1fr) minmax(280px, 1.15fr); align-items: center; gap: 14px; min-height: 45px; }.device-name { position: relative; padding-left: 13px; }.device-name b { display: block; color: #d7edeb; font-size: 12px; }.device-name small { display: block; margin-top: 4px; color: #55767a; font-size: 8px; }.device-pulse { position: absolute; left: 0; top: 3px; width: 6px; height: 6px; border-radius: 50%; background: #64d7be; box-shadow: 0 0 0 4px rgba(100, 215, 190, .08), 0 0 9px rgba(100, 215, 190, .5); }.device-pulse.warn { background: #d49a58; box-shadow: 0 0 0 4px rgba(212, 154, 88, .08), 0 0 9px rgba(212, 154, 88, .45); }.device-track { height: 8px; overflow: hidden; border-radius: 6px; background: rgba(95, 146, 148, .13); }.device-track i { display: block; height: 100%; border-radius: inherit; background: linear-gradient(90deg, #3d8f8e, #68d9c5); box-shadow: 0 0 11px rgba(92, 220, 199, .32); }.device-stats { display: grid; grid-template-columns: repeat(4, minmax(54px, 1fr)); gap: 6px; }.device-stats span { color: #607f83; font-size: 9px; white-space: nowrap; }.device-stats b { margin-right: 2px; color: #bcd5d3; font-size: 14px; font-variant-numeric: tabular-nums; }.device-stats .receiving b { color: #69d8c0; }.device-stats .warn b { color: #e0a15d; }
.resource-foot { margin: 4px 18px 13px; padding-top: 9px; border-top: 1px solid rgba(120, 180, 180, .1); color: #58777c; font-size: 9px; line-height: 1.55; }
.score-content { display: grid; grid-template-columns: 87px 1fr; align-items: center; gap: 12px; min-height: 148px; padding: 15px 16px 17px; }.score-total { display: flex; flex-direction: column; align-items: center; justify-content: center; height: 76px; border-right: 1px solid rgba(119, 179, 179, .12); }.score-total b { color: #9af0dc; font-size: 30px; line-height: 1; text-shadow: 0 0 20px rgba(93, 230, 207, .25); }.score-total span { margin-top: 6px; color: #648489; font-size: 10px; }.score-rows { display: flex; flex-direction: column; gap: 9px; }.score-row { display: grid; grid-template-columns: 42px minmax(50px, 1fr) 25px 34px; align-items: center; gap: 7px; }.score-range { color: #78979a; font-size: 10px; }.score-track { height: 7px; overflow: hidden; border-radius: 5px; background: rgba(111, 169, 169, .12); }.score-track i { display: block; height: 100%; min-width: 2px; border-radius: inherit; box-shadow: 0 0 8px currentColor; }.score-row b { color: #bdd6d5; font-size: 11px; text-align: right; }.score-row small { color: #5d7c81; font-size: 9px; text-align: right; }
.organ-panel { min-height: 224px; }.organ-list { display: flex; flex-direction: column; gap: 14px; padding: 17px 16px 11px; }.organ-item { display: grid; grid-template-columns: 70px minmax(50px, 1fr) 47px; align-items: center; gap: 10px; }.organ-label { color: #78999c; font-size: 11px; }.organ-bar { height: 7px; overflow: hidden; border-radius: 5px; background: rgba(105, 161, 161, .12); }.organ-bar-fill { height: 100%; border-radius: inherit; box-shadow: 0 0 10px currentColor; transition: width .4s ease; }.organ-num { color: #d7eeeb; font-size: 15px; font-weight: 700; text-align: right; font-variant-numeric: tabular-nums; }.organ-num i { margin-left: 2px; color: #628287; font-size: 9px; font-style: normal; font-weight: 400; }.organ-num.value-unknown { color: #547176; }.organ-foot { margin: 0 16px 15px; padding-top: 10px; border-top: 1px solid rgba(120, 180, 180, .1); color: #58777c; font-size: 9px; line-height: 1.55; }
.state-box { display: flex; min-height: 160px; flex-direction: column; align-items: center; justify-content: center; gap: 8px; }.state-box.small { min-height: 120px; }.state-title { color: #607f84; font-size: 11px; }.notes-list { margin: 0; padding-left: 18px; color: #53686d; font-size: 12px; line-height: 2; }.notes-foot { margin: 10px 0 0; color: #9ca9aa; font-size: 11px; }
/* ==========================================================================
   大屏展示：视口锁定 + 纵向弹性分配
   --------------------------------------------------------------------------
   这个页面是给科室挂在大屏上看的，全屏时必须一屏看全 —— 一旦出现滚动条，
   最下面的器官支持/评分分布就被推到屏幕外，值班的人根本看不到。
   原来的写法是「各区块固定高度 + min-height 累加」。线上实测：整页 1348px
   （页头 170 + 范围条 14 + KPI 137 + 三组面板 358/261/290 + 内外边距），
   而 1080p 全屏可用高度只有 1080px —— 溢出 268px，必然出滚动条。

   可用高度为什么是 100vh：外壳是「左侧固定侧栏 + main-content」，内容区
   min-height:100vh 且顶部偏移为 0，没有顶栏。原代码用 calc(100vh - 56px)
   是白丢了 56px（按已不存在的顶栏算的），这里收回。

   改法：把页面锁成「视口高度」的纵向 flex 容器，三组面板按 flex 比例瓜分
   剩余高度，内部一律 min-height:0 纯弹性吃满。这样任何视口高度都不溢出，
   也不会在高分屏上留大片空白。
   min-height 兜底：视口过矮时允许滚动，总比把内容压扁到不可读要好。

   生效条件：宽 >1180px 且高 ≥1000px。实测 1920×1080 与 2560×1440 均一屏放下、
   零裁切。加高度门槛是因为矮视口下纵向真的不够分：900px 时 APACHE 面板开始被
   裁、768px 时五个面板被裁 —— 裁掉的是数字，比出滚动条更糟。所以宁可退回原来
   的文档流：要滚，但一个数字都不丢。
   （外链无身份时 App.vue 顶部那条警告条不在本模式内，它出现时会多占一行，
   属异常态告警，容忍轻微滚动。）
   ========================================================================== */
@media (min-width: 1181px) and (min-height: 1000px) {
  /* box-sizing 必须显式声明：该元素是 content-box，不写的话 height:100vh 只是
     内容盒高度，再加上下 padding 会溢出正好 22px —— 实测 docH 1102 / vh 1080。 */
  .cockpit-page { box-sizing: border-box; height: 100vh; display: flex; flex-direction: column; gap: 9px; padding: 10px 14px 12px; }

  /* 页头：124px → 约 80px。信息一条不少，但不再吃掉整屏的 1/8 */
  .cockpit-hero { flex: 0 0 auto; min-height: 0; padding: 11px 18px; gap: 16px; border-radius: 12px; }
  .hero-eyebrow { font-size: 9px; letter-spacing: 1.2px; }
  .hero-copy h1 { margin: 5px 0 3px; font-size: 21px; }
  .hero-copy p { font-size: 11px; }
  .hero-meta { min-width: 148px; padding-left: 14px; }
  .hero-radar { right: 196px; width: 122px; height: 122px; opacity: .5; }
  .hero-actions :deep(.el-radio-button__inner) { padding: 5px 9px; }
  .hero-actions :deep(.el-button) { height: 26px; padding: 0 10px; }

  /* 范围条：margin 交给父级 gap 统一管，避免和 gap 叠加出双份间距 */
  .period-strip { flex: 0 0 auto; margin: 0 2px; gap: 12px; }

  /* KPI：105px → 74px，数字 30 → 24。24px 是全屏三米外仍可辨认的下限 */
  .kpi-row { flex: 0 0 auto; gap: 9px; }
  .kpi-row :deep(.snapshot-card) { min-height: 74px; padding: 10px 13px 9px; border-radius: 11px; }
  .kpi-row :deep(.snapshot-card)::before { top: 12px; bottom: 12px; }
  .kpi-row :deep(.snapshot-card)::after { right: 11px; top: 7px; font-size: 21px; }
  .kpi-row :deep(.card-label) { font-size: 10px; }
  .kpi-row :deep(.num) { font-size: 24px; }
  .kpi-row :deep(.card-sub) { font-size: 9px; }

  /* 三组面板按 1.18 : 0.95 : 0.95 瓜分剩余高度（趋势图信息量最大，给最多） */
  .primary-grid { flex: 1.18 1 0; min-height: 0; margin-top: 0; }
  .resource-grid { flex: 0.95 1 0; min-height: 0; margin-top: 0; }
  .secondary-grid { flex: 0.95 1 0; min-height: 0; margin-top: 0; }

  /* 面板统一改纵向 flex：头部固定、内容区吃满剩余高度 */
  .panel { display: flex; flex-direction: column; min-height: 0; }
  .panel-head { flex: 0 0 auto; min-height: 42px; padding: 7px 13px; }
  .panel-body { flex: 1 1 auto; min-height: 0; padding: 8px 13px; }
  .flow-panel, .quality-panel { min-height: 0; }

  /* 患者流转：图表从固定 268px 改为吃满容器 */
  .flow-summary { flex: 0 0 auto; gap: 26px; padding: 8px 14px 0; }
  .flow-summary b { font-size: 20px; }
  .flow-body { display: flex; flex-direction: column; padding-top: 2px; }
  .flow-chart { flex: 1 1 auto; height: auto; min-height: 110px; }

  /* 质量指标：后端固定返回 6 项，两列 = 3 行；行高用 minmax(34px,1fr) 让三行
     均分面板高度，既不会挤成一长条，也不会在下方留出半屏空白。 */
  .q-list { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); grid-auto-rows: minmax(34px, 1fr); flex: 1 1 auto; min-height: 0; gap: 5px; padding: 7px 12px 0; overflow: hidden; }
  .q-card { min-height: 34px; padding: 5px 9px; }
  .q-result b { font-size: 17px; }
  .quality-foot { flex: 0 0 auto; margin: 4px 13px 8px; padding-top: 6px; font-size: 9px; }

  /* 床位 / 设备 / 评分 / 器官支持：取消固定 min-height，改为吃满容器 */
  .bed-body { flex: 1 1 auto; min-height: 0; grid-template-columns: 118px 1fr; gap: 14px; padding: 10px 15px 4px; }
  .bed-gauge { width: 104px; height: 104px; }
  .device-list { flex: 1 1 auto; min-height: 0; gap: 6px; padding: 10px 14px 4px; justify-content: stretch; }
  /* 设备按 device_type 分组，行数取决于科室实际配了哪几类（1~7 行）。
     行高设为可伸缩：行少时均分撑满，行多时按 26px 下限排紧，避免固定 34px
     在 6 行以上把最后一行挤出面板。 */
  .device-row { flex: 1 1 0; min-height: 26px; }
  .score-content { flex: 1 1 auto; min-height: 0; grid-template-columns: 74px 1fr; gap: 10px; padding: 10px 13px; }
  .score-total { height: 60px; }
  .score-total b { font-size: 25px; }
  .score-rows { gap: 7px; }
  .organ-list { flex: 1 1 auto; min-height: 0; }
  .resource-foot, .organ-foot { flex: 0 0 auto; margin: 3px 13px 8px; padding-top: 6px; font-size: 9px; }

  /* 空态/异常态：锁高布局里不该再占 160px 的固定高度，改为吃满并居中 */
  .state-box { flex: 1 1 auto; min-height: 0; }
  .state-box.small { min-height: 0; }
}

@media (max-width: 1180px) { .cockpit-hero { grid-template-columns: 1fr auto; }.hero-actions { grid-column: 1 / -1; justify-content: flex-end; }.kpi-row { grid-template-columns: repeat(3, 1fr); }.secondary-grid { grid-template-columns: 1fr 1fr; }.organ-panel { grid-column: 1 / -1; }.device-row { grid-template-columns: 96px minmax(70px, .8fr) minmax(250px, 1.2fr); gap: 10px; } }
@media (max-width: 820px) { .cockpit-page { padding: 12px; }.cockpit-hero { grid-template-columns: 1fr; gap: 14px; }.hero-meta { padding-left: 0; border-left: 0; }.hero-actions { grid-column: auto; justify-content: flex-start; flex-wrap: wrap; }.kpi-row { grid-template-columns: repeat(2, 1fr); }.primary-grid, .resource-grid, .secondary-grid { grid-template-columns: 1fr; }.organ-panel { grid-column: auto; }.panel-context { white-space: normal; text-align: right; }.device-row { grid-template-columns: 100px 1fr; }.device-stats { grid-column: 1 / -1; } }
@media (max-width: 480px) { .kpi-row { grid-template-columns: 1fr; }.period-strip { align-items: flex-start; flex-direction: column; gap: 7px; }.data-gap { margin-left: 0; }.flow-summary { gap: 14px; }.score-content { grid-template-columns: 70px 1fr; padding-left: 10px; padding-right: 10px; }.score-row { grid-template-columns: 36px minmax(40px, 1fr) 22px 31px; gap: 5px; } }
</style>
