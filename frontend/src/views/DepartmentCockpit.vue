<template>
  <div class="cockpit-page">
    <section class="cockpit-head">
      <div class="head-left">
        <h2 class="page-title">科室驾驶舱</h2>
        <p class="page-sub">{{ scopeText }} · 数据日期 {{ summary.date }} <span class="dot">·</span>更新于 {{ updatedAt }}</p>
      </div>
      <div class="head-right">
        <el-radio-group v-model="days" size="small" @change="load">
          <el-radio-button :label="30">近 30 天</el-radio-button>
          <el-radio-button :label="7">近 7 天</el-radio-button>
          <el-radio-button :label="90">近 90 天</el-radio-button>
        </el-radio-group>
        <el-button :icon="Refresh" size="small" :loading="loading" @click="load">刷新</el-button>
        <el-button :icon="InfoFilled" size="small" text @click="notesVisible = true">口径说明</el-button>
      </div>
    </section>
    <section class="kpi-row">
      <StatCard label="当前在科患者" :block="summary.inCount" unit="人" />
      <StatCard label="危重患者" :block="summary.criticalCount" unit="人" :sub="ratioText" sub-warn tone="up" />
      <StatCard label="今日入科 / 转入" :block="summary.todayInCount" unit="人" tone="up" />
      <StatCard label="今日转出 / 出科" :block="summary.todayOutCount" unit="人" tone="down" />
      <StatCard label="平均 ICU 住院日" :block="summary.avgStayDays" unit="天" :sub="avgStaySub" />
      <StatCard label="床位使用率" :block="summary.bedUsage" value-text="待接数据" />
    </section>
    <section class="main-grid">
      <div class="col">
        <div class="panel">
          <div class="panel-head">
            <span class="panel-title">SOFA 评分分布</span>
            <span class="panel-note">按患者去重</span>
          </div>
          <div class="panel-body">
            <div v-if="distUnknown(summary.sofaDist)" class="state-box small"><div class="state-title">{{ blockNote(summary.sofaDist) }}</div></div>
            <div v-else-if="!distPoints(summary.sofaDist).length" class="state-box small"><div class="state-title">{{ blockNote(summary.sofaDist) || '周期内无评分记录' }}</div></div>
            <template v-else>
              <div ref="sofaChart" class="donut-chart"></div>
              <div class="dist-total">参与患者 <b>{{ distTotal(summary.sofaDist) }}</b> 人</div>
            </template>
          </div>
        </div>
        <div class="panel">
          <div class="panel-head">
            <span class="panel-title">APACHE II 评分分布</span>
            <span class="panel-note">按患者去重</span>
          </div>
          <div class="panel-body">
            <div v-if="distUnknown(summary.apache2Dist)" class="state-box small"><div class="state-title">{{ blockNote(summary.apache2Dist) }}</div></div>
            <div v-else-if="!distPoints(summary.apache2Dist).length" class="state-box small"><div class="state-title">{{ blockNote(summary.apache2Dist) || '周期内无评分记录' }}</div></div>
            <template v-else>
              <div ref="apacheChart" class="donut-chart"></div>
              <div class="dist-total">参与患者 <b>{{ distTotal(summary.apache2Dist) }}</b> 人</div>
            </template>
          </div>
        </div>
      </div>
      <div class="col col-main">
        <div class="panel">
          <div class="panel-head">
            <span class="panel-title">患者流转（近 {{ days }} 天）</span>
            <span class="panel-note">左闭右开 · 住院号去重</span>
          </div>
          <div class="panel-body">
            <div v-if="flowUnknown" class="state-box">
              <div class="state-title">{{ flowNote }}</div>
              <el-button size="small" :loading="loading" @click="load">重试</el-button>
            </div>
            <div v-else-if="!flowPoints.length" class="state-box"><div class="state-title">区间内暂无入出科记录</div></div>
            <div v-else ref="flowChart" class="flow-chart"></div>
          </div>
        </div>
        <div class="panel">
          <div class="panel-head">
            <span class="panel-title">当前器官支持</span>
            <span class="panel-note">与患者工作台同口径</span>
          </div>
          <div class="panel-body organ-list">
            <div class="organ-item" v-for="o in organItems" :key="o.label">
              <span class="organ-label">{{ o.label }}</span>
              <div class="organ-bar"><div class="organ-bar-fill" :style="{ width: o.percent + '%', background: o.color }"></div></div>
              <span class="organ-num">{{ o.value }}<i>人</i></span>
            </div>
            <p class="organ-foot">只表达患者支持状态，不代表设备台数或可用库存</p>
          </div>
        </div>
      </div>
      <div class="col">
        <div class="panel">
          <div class="panel-head">
            <span class="panel-title">本月质控指标</span>
            <span class="panel-note">复用质控中台</span>
          </div>
          <div class="panel-body">
            <div v-if="qualityUnknown" class="state-box small"><div class="state-title">{{ blockNote(summary.qualityCards) }}</div></div>
            <div v-else class="q-list">
              <div class="q-card" v-for="q in qualityCards" :key="q.code">
                <div class="q-name">{{ q.name }}</div>
                <div class="q-value-row">
                  <template v-if="q.calcStatus === 'OK' && q.value != null">
                    <span class="q-num">{{ q.value }}</span>
                    <span class="q-unit">{{ q.unit }}</span>
                    <span v-if="q.denominator" class="q-frac">{{ q.numerator }}/{{ q.denominator }}</span>
                  </template>
                  <span v-else-if="q.calcStatus === 'PENDING_SOURCE'" class="q-state pending">待接数据源</span>
                  <span v-else-if="q.calcStatus === 'NO_DATA'" class="q-state">本月无数据</span>
                  <span v-else-if="q.calcStatus === 'MANUAL'" class="q-state">人工填报</span>
                  <span v-else class="q-state err">{{ q.note || '暂不可用' }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
        <div class="panel">
          <div class="panel-head"><span class="panel-title">后续阶段</span></div>
          <div class="panel-body road-body">
            <div class="road-item"><span class="road-tag next">阶段 2</span><span class="road-name">质控 12 个月趋势</span></div>
            <div class="road-item"><span class="road-tag">阶段 3</span><span class="road-name">设备与人力</span></div>
          </div>
        </div>
      </div>
    </section>
    <el-dialog v-model="notesVisible" title="口径说明" width="640px">
      <ul class="notes-list">
        <li v-for="(n, i) in summary.notes" :key="i">{{ n }}</li>
      </ul>
      <p class="notes-foot">与患者工作台 / 出科统计 / 质控中台同一套口径；床位使用率因床位数据源未接入暂不计算。</p>
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

// 分布配色：由浅到深，与项目暖色主调统一
const SOFA_COLORS = ['#a3b18a', '#d9c58b', '#e8b04b', '#e08a3c', '#d1603d', '#a4303f']
const APACHE_COLORS = ['#93c5fd', '#60a5fa', '#3b82f6', '#1d4ed8']

export default {
  name: 'DepartmentCockpit',
  components: { Refresh, InfoFilled, StatCard },
  data() {
    return {
      days: 30,
      loading: false,
      notesVisible: false,
      updatedAt: '',
      summary: { date: '', departCode: '', departName: '', notes: [] },
      charts: {}
    }
  },
  computed: {
    departCode() {
      return currentDepart.departCode || ''
    },
    scopeText() {
      if (this.summary.departName && this.summary.departName !== '全院') {
        return '科室：' + this.summary.departName
      }
      return this.departCode ? ('科室：' + this.departCode) : '全院（未限定科室）'
    },
    ratioText() {
      const r = this.summary.criticalRatio
      if (!r || r.status !== 'OK' || r.value == null) return ''
      return '占在科 ' + (r.value * 100).toFixed(1) + '%'
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
      const b = (key) => {
        const x = this.summary[key]
        return x && x.status === 'OK' && x.value != null ? x.value : 0
      }
      const vent = b('ventilatedCount')
      const crrt = b('crrtCount')
      const ecmo = b('ecmoCount')
      const inCount = this.summary.inCount && this.summary.inCount.value != null
        ? this.summary.inCount.value : 0
      const pct = (v) => inCount > 0 ? Math.min(100, Math.round((v / inCount) * 100)) : 0
      return [
        { label: '机械通气', value: vent, percent: pct(vent), color: '#b45309' },
        { label: 'CRRT', value: crrt, percent: pct(crrt), color: '#0e7490' },
        { label: 'ECMO', value: ecmo, percent: pct(ecmo), color: '#7c3aed' }
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
        this.renderDonut('sofaChart', this.distPoints(this.summary.sofaDist), SOFA_COLORS, 'SOFA')
        this.renderDonut('apacheChart', this.distPoints(this.summary.apache2Dist), APACHE_COLORS, 'APACHE II')
      })
    }
  },  created() {
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
    distUnknown(block) {
      return block && block.status === 'UNKNOWN'
    },
    blockNote(block) {
      return (block && block.note) || ''
    },
    getChart(key, el) {
      const chart = echarts.getInstanceByDom(el) || echarts.init(el)
      this.charts[key] = chart
      // clear() 再 setOption：复用实例时 notMerge 会坏掉 axis 关联（tooltip 失效根因）
      chart.clear()
      return chart
    },    renderFlow() {
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
            itemStyle: { color: '#d97706', borderRadius: [3, 3, 0, 0] }
          },
          {
            name: '出科', type: 'bar', barMaxWidth: 14,
            data: this.flowPoints.map((x) => x.outCount),
            itemStyle: { color: '#0891b2', borderRadius: [3, 3, 0, 0] }
          }
        ]
      })
      chart.resize()
    },
    renderDonut(key, buckets, colors, name) {
      const el = this.$refs[key]
      if (!el || !buckets.length) return
      const chart = this.getChart(key, el)
      const total = buckets.reduce((s, b) => s + (b.count || 0), 0)
      chart.setOption({
        backgroundColor: 'transparent',
        animationDuration: 300,
        tooltip: {
          trigger: 'item',
          confine: true,
          formatter: (pt) => name + ' ' + pt.name + '<br/><b>' + pt.value + '</b> 人（' + pt.percent + '%）'
        },
        legend: {
          orient: 'horizontal', bottom: 0, left: 'center',
          itemWidth: 10, itemHeight: 10, itemGap: 8,
          textStyle: { color: '#78716c', fontSize: 10 }
        },
        series: [{
          type: 'pie',
          radius: ['52%', '78%'],
          center: ['50%', '42%'],
          avoidLabelOverlap: true,
          label: { show: false },
          emphasis: { scale: true, scaleSize: 4, label: { show: false } },
          data: buckets.map((b, i) => ({
            name: b.label, value: b.count,
            itemStyle: { color: colors[i % colors.length] }
          }))
        }],
        graphic: [{
          type: 'text', left: 'center', top: '36%',
          style: { text: String(total), fontSize: 22, fontWeight: 700, fill: '#1c1917', textAlign: 'center' }
        }, {
          type: 'text', left: 'center', top: '49%',
          style: { text: '患者总数', fontSize: 10, fill: '#a8a29e', textAlign: 'center' }
        }]
      })
      chart.resize()
    }
  }
}
</script>
<style scoped>
.cockpit-page { padding: 16px 20px 28px; background: #f7f6f3; min-height: calc(100vh - 56px); }
.cockpit-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 14px; flex-wrap: wrap; margin-bottom: 14px; }
.page-title { margin: 0; font-size: 19px; font-weight: 700; color: #1c1917; letter-spacing: .5px; }
.page-sub { margin: 4px 0 0; font-size: 12px; color: #78716c; }
.dot { margin: 0 4px; color: #d6d3d1; }
.head-right { display: flex; align-items: center; gap: 8px; }
.kpi-row { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 10px; }
.main-grid { display: grid; grid-template-columns: 300px minmax(0, 1fr) 300px; gap: 12px; margin-top: 12px; align-items: start; }
@media (max-width: 1200px) { .main-grid { grid-template-columns: 1fr 1fr; } .col-main { order: -1; grid-column: 1 / -1; } }
@media (max-width: 760px) { .main-grid { grid-template-columns: 1fr; } }
.col { display: flex; flex-direction: column; gap: 12px; min-width: 0; }
.panel { background: #fff; border: 1px solid #eeece8; border-radius: 12px; overflow: hidden; transition: box-shadow .2s ease; }
.panel:hover { box-shadow: 0 6px 18px rgba(28, 25, 23, .07); }
.panel-head { display: flex; align-items: center; justify-content: space-between; gap: 8px; padding: 11px 14px; border-bottom: 1px solid #f5f4f1; position: relative; }
.panel-head::before { content: ''; position: absolute; left: 0; top: 10px; bottom: 10px; width: 3px; border-radius: 0 2px 2px 0; background: linear-gradient(180deg, #d97706, #b45309); }
.panel-title { font-size: 13px; font-weight: 600; color: #1c1917; }
.panel-note { font-size: 10px; color: #a8a29e; }
.panel-body { padding: 12px 14px; }
.donut-chart { width: 100%; height: 200px; }
.dist-total { text-align: center; font-size: 11px; color: #a8a29e; margin-top: -2px; }
.dist-total b { color: #b45309; font-size: 13px; }
.flow-chart { width: 100%; height: 280px; }
.organ-list { display: flex; flex-direction: column; gap: 12px; }
.organ-item { display: grid; grid-template-columns: 64px 1fr 52px; align-items: center; gap: 10px; }
.organ-label { font-size: 12px; color: #57534e; }
.organ-bar { height: 8px; border-radius: 4px; background: #f5f4f1; overflow: hidden; }
.organ-bar-fill { height: 100%; border-radius: 4px; transition: width .4s ease; }
.organ-num { font-size: 15px; font-weight: 700; color: #1c1917; text-align: right; font-variant-numeric: tabular-nums; }
.organ-num i { font-style: normal; font-size: 10px; color: #a8a29e; margin-left: 2px; font-weight: 400; }
.organ-foot { margin: 2px 0 0; font-size: 10px; color: #a8a29e; }
.q-list { display: flex; flex-direction: column; gap: 8px; }
.q-card { border: 1px solid #f5f4f1; border-radius: 8px; padding: 8px 10px; background: #fdfcfa; }
.q-name { font-size: 11px; color: #78716c; line-height: 1.4; }
.q-value-row { margin-top: 4px; display: flex; align-items: baseline; gap: 6px; flex-wrap: wrap; }
.q-num { font-size: 19px; font-weight: 700; color: #b45309; font-variant-numeric: tabular-nums; }
.q-unit { font-size: 10px; color: #a8a29e; }
.q-frac { font-size: 10px; color: #a8a29e; font-variant-numeric: tabular-nums; }
.q-state { font-size: 11px; color: #a8a29e; }
.q-state.pending { color: #d97706; }
.q-state.err { color: #b91c1c; }
.state-box { display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 8px; min-height: 160px; }
.state-box.small { min-height: 120px; }
.state-title { font-size: 12px; color: #a8a29e; }
.road-body { display: flex; flex-direction: column; gap: 8px; }
.road-item { display: flex; align-items: center; gap: 8px; border: 1px dashed #e7e5e4; border-radius: 8px; padding: 8px 10px; }
.road-tag { font-size: 10px; padding: 1px 6px; border-radius: 4px; background: #f5f5f4; color: #78716c; white-space: nowrap; }
.road-tag.next { background: #e0f2fe; color: #075985; }
.notes-list { margin: 0; padding-left: 18px; font-size: 12px; color: #57534e; line-height: 2; }
.notes-foot { margin: 10px 0 0; font-size: 11px; color: #a8a29e; }
</style>