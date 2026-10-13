<template>
  <div class="cockpit-page">
    <!-- 顶部筛选与数据状态 -->
    <section class="cockpit-head">
      <div class="head-left">
        <h2 class="page-title">科室驾驶舱</h2>
        <p class="page-sub">
          {{ scopeText }} · 数据日期 {{ summary.date || '—' }}
          <span class="dot">·</span>更新于 {{ updatedAt || '—' }}
        </p>
      </div>
      <div class="head-right">
        <el-radio-group v-model="days" size="small" @change="load">
          <el-radio-button :label="30">近 30 天</el-radio-button>
          <el-radio-button :label="7">近 7 天</el-radio-button>
          <el-radio-button :label="90">近 90 天</el-radio-button>
        </el-radio-group>
        <el-button :icon="Refresh" size="small" :loading="loading" @click="load">刷新</el-button>
        <el-button :icon="Warning" size="small" text @click="notesVisible = true">口径说明</el-button>
      </div>
    </section>

    <!-- B. 今日科室快照 -->
    <section class="block">
      <div class="block-head">
        <h3 class="block-title">今日科室快照</h3>
        <span class="block-note">统计时点：页面刷新时刻</span>
      </div>
      <div class="card-grid">
        <StatCard label="当前在科患者" :block="summary.inCount" unit="人" />
        <StatCard label="危重患者" :block="summary.criticalCount" unit="人"
                  :sub="ratioText" sub-warn />
        <StatCard label="今日入科 / 转入" :block="summary.todayInCount" unit="人" tone="up" />
        <StatCard label="今日转出 / 出科" :block="summary.todayOutCount" unit="人" tone="down" />
        <StatCard label="机械通气" :block="summary.ventilatedCount" unit="人" />
        <StatCard label="CRRT" :block="summary.crrtCount" unit="人" />
        <StatCard label="ECMO" :block="summary.ecmoCount" unit="人" />
        <StatCard label="床位使用率" :block="summary.bedUsage" value-text="待接数据" />
      </div>
    </section>

    <!-- D. 患者流转 -->
    <section class="block">
      <div class="block-head">
        <h3 class="block-title">患者流转（近 {{ days }} 天）</h3>
        <span class="block-note">
          平均 ICU 住院日
          <b v-if="avgStayOk" class="avg-num">{{ summary.avgStayDays.value }} 天</b>
          <span v-else>{{ avgStayText }}</span>
        </span>
      </div>
      <div class="chart-wrap" v-loading="loading">
        <div v-if="flowUnknown" class="state-box">
          <div class="state-title">{{ flowNote }}</div>
          <el-button size="small" :loading="loading" @click="load">重试</el-button>
        </div>
        <div v-else-if="!flowPoints.length" class="state-box">
          <div class="state-title">区间内暂无入出科记录</div>
        </div>
        <div v-else ref="flowChart" class="chart-box"></div>
      </div>
      <p class="chart-foot">
        <el-icon><InfoFilled /></el-icon>
        入科 / 出科人数按自然日聚合、区间左闭右开，与患者出科统计同口径；同一住院号多次入科只计一次。
      </p>
    </section>

    <!-- 后续阶段预告 -->
    <section class="block roadmap">
      <div class="block-head"><h3 class="block-title">后续阶段</h3></div>
      <div class="roadmap-grid">
        <div class="road-item">
          <div class="road-tag done">已上线</div>
          <div class="road-name">科室运营快照与流转</div>
          <div class="road-desc">在科 / 危重 / 器官支持、今日入出科、近 N 天流转、平均住院日</div>
        </div>
        <div class="road-item">
          <div class="road-tag next">阶段 2</div>
          <div class="road-name">质控与评分分析</div>
          <div class="road-desc">复用质控中台指标趋势、APACHE II / SOFA 分布、脓毒症集束化月趋势</div>
        </div>
        <div class="road-item">
          <div class="road-tag">阶段 3</div>
          <div class="road-name">设备与人力资源</div>
          <div class="road-desc">设备台账状态、当班人员口径、医护床位比（数据源接入后开放）</div>
        </div>
      </div>
    </section>

    <!-- 口径说明 -->
    <el-dialog v-model="notesVisible" title="口径说明" width="620px">
      <ul class="notes-list">
        <li v-for="(n, i) in summary.notes" :key="i">{{ n }}</li>
      </ul>
      <p class="notes-foot">
        与患者工作台 / 出科统计 / 质控中台同一套口径；床位使用率因床位数据源未接入暂不计算。
      </p>
    </el-dialog>
  </div>
</template>

<script>
import { ElMessage } from 'element-plus'
import { Refresh, Warning, InfoFilled } from '@element-plus/icons-vue'
import * as echarts from '../utils/echarts'
import { fetchCockpitSummary } from '../api/workbench'
import { currentDepart } from '../utils/departContext'
import StatCard from '../components/StatCard.vue'

export default {
  name: 'DepartmentCockpit',
  components: { Refresh, Warning, InfoFilled, StatCard },
  data() {
    return {
      days: 30,
      loading: false,
      notesVisible: false,
      updatedAt: '',
      summary: {
        date: '',
        departCode: '',
        departName: '',
        notes: []
      },
      flowChartInst: null
    }
  },
  computed: {
    departCode() {
      return currentDepart.departCode || ''
    },
    scopeText() {
      if (this.summary.departName && this.summary.departName !== '全院') {
        return `科室：${this.summary.departName}`
      }
      return this.departCode ? `科室：${this.departCode}` : '全院（未限定科室）'
    },
    ratioText() {
      const r = this.summary.criticalRatio
      if (!r || r.status !== 'OK' || r.value == null) return ''
      return `占在科 ${(r.value * 100).toFixed(1)}%`
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
    avgStayOk() {
      const a = this.summary.avgStayDays
      return a && a.status === 'OK' && a.value != null
    },
    avgStayText() {
      const a = this.summary.avgStayDays
      if (!a) return '—'
      if (a.status === 'EMPTY') return '区间内无已出科患者'
      if (a.status === 'UNKNOWN') return a.note || '查询失败'
      return '—'
    }
  },
  watch: {
    // 左侧栏切换科室后自动重查
    departCode() {
      this.load()
    },
    flowPoints() {
      this.renderFlow()
    }
  },
  created() {
    this.load()
  },
  beforeUnmount() {
    if (this.flowChartInst) {
      this.flowChartInst.dispose()
      this.flowChartInst = null
    }
  },
  methods: {
    async load() {
      this.loading = true
      try {
        const s = await fetchCockpitSummary(this.departCode, this.days)
        this.summary = s || {}
        this.updatedAt = new Date().toLocaleString('zh-CN', { hour12: false })
      } catch (e) {
        const msg = (e && (e.message || e.msg)) || ''
        ElMessage.error(msg.includes('权限')
          ? `无权查看该科室驾驶舱：${msg}`
          : `驾驶舱加载失败：${msg || '请稍后重试'}`)
        this.summary = { notes: [] }
      } finally {
        this.loading = false
      }
    },
    renderFlow() {
      const el = this.$refs.flowChart
      if (!el || !this.flowPoints.length) return
      // clear() 再 setOption：复用实例时 notMerge 会坏掉 axis/series 关联（tooltip 失效的根因）
      const chart = echarts.getInstanceByDom(el) || echarts.init(el)
      this.flowChartInst = chart
      chart.clear()
      chart.setOption({
        backgroundColor: 'transparent',
        animationDuration: 300,
        grid: { left: 40, right: 16, top: 24, bottom: 26 },
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
            const inP = ps.find((p) => p.seriesName === '入科')
            const outP = ps.find((p) => p.seriesName === '出科')
            return `${day}<br/>入科：<b>${inP ? inP.value : 0}</b> 人<br/>出科：<b>${outP ? outP.value : 0}</b> 人`
          }
        },
        xAxis: {
          type: 'category',
          data: this.flowPoints.map((p) => p.day),
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
            name: '入科',
            type: 'line',
            smooth: false,
            symbol: 'circle',
            symbolSize: 5,
            data: this.flowPoints.map((p) => p.inCount),
            lineStyle: { width: 2, color: '#b45309' },
            itemStyle: { color: '#b45309' },
            areaStyle: { color: 'rgba(180,83,9,0.06)' }
          },
          {
            name: '出科',
            type: 'line',
            smooth: false,
            symbol: 'circle',
            symbolSize: 5,
            data: this.flowPoints.map((p) => p.outCount),
            lineStyle: { width: 2, color: '#0e7490' },
            itemStyle: { color: '#0e7490' },
            areaStyle: { color: 'rgba(14,116,144,0.05)' }
          }
        ]
      })
      chart.resize()
    }
  }
}
</script>

<style scoped>
.cockpit-page {
  padding: 16px 20px 28px;
}
.cockpit-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
  flex-wrap: wrap;
  padding-bottom: 12px;
  border-bottom: 1px solid #f0efec;
}
.page-title {
  margin: 0;
  font-size: 18px;
  color: #1c1917;
}
.page-sub {
  margin: 4px 0 0;
  font-size: 12px;
  color: #78716c;
}
.dot {
  margin: 0 4px;
  color: #d6d3d1;
}
.head-right {
  display: flex;
  align-items: center;
  gap: 8px;
}
.block {
  margin-top: 16px;
}
.block-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 10px;
}
.block-title {
  margin: 0;
  font-size: 14px;
  color: #1c1917;
}
.block-note {
  font-size: 11px;
  color: #a8a29e;
}
.avg-num {
  color: #b45309;
  font-size: 13px;
}
.card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(158px, 1fr));
  gap: 10px;
}
.chart-wrap {
  min-height: 260px;
  background: #fff;
  border: 1px solid #f0efec;
  border-radius: 10px;
  padding: 10px 12px;
}
.chart-box {
  width: 100%;
  height: 260px;
}
.chart-foot {
  display: flex;
  align-items: center;
  gap: 5px;
  margin: 8px 2px 0;
  font-size: 11px;
  color: #a8a29e;
}
.state-box {
  height: 240px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
}
.state-title {
  font-size: 13px;
  color: #a8a29e;
}
.roadmap-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 10px;
}
.road-item {
  border: 1px dashed #e7e5e4;
  border-radius: 10px;
  padding: 12px 14px;
}
.road-item:first-child {
  border-style: solid;
  border-color: #fde68a;
  background: #fffdf7;
}
.road-tag {
  display: inline-block;
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 4px;
  background: #f5f5f4;
  color: #78716c;
}
.road-tag.done {
  background: #fef3c7;
  color: #92400e;
}
.road-tag.next {
  background: #e0f2fe;
  color: #075985;
}
.road-name {
  margin-top: 6px;
  font-size: 13px;
  font-weight: 600;
  color: #1c1917;
}
.road-desc {
  margin-top: 3px;
  font-size: 11px;
  color: #a8a29e;
  line-height: 1.6;
}
.notes-list {
  margin: 0;
  padding-left: 18px;
  font-size: 12px;
  color: #57534e;
  line-height: 2;
}
.notes-foot {
  margin: 10px 0 0;
  font-size: 11px;
  color: #a8a29e;
}
</style>
