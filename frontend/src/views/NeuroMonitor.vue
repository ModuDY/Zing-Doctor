<template>
  <div class="neuro-page qb-theme">
    <!-- 顶部：返回 + 患者身份 + 时间范围 -->
    <section class="page-header">
      <div class="header-left">
        <el-button link class="back-btn" @click="goBack">
          <el-icon><ArrowLeft /></el-icon>
          返回工作台
        </el-button>
        <div class="patient-id" v-if="patient">
          <h1 class="patient-name">{{ patient.name || '—' }}</h1>
          <div class="patient-meta">
            <span class="meta-pill bed">{{ patient.bedNo || '未分配床位' }}</span>
            <span class="meta-item">住院号 {{ patient.inHospitalNo || '—' }}</span>
            <span class="meta-item" v-if="patient.departName || patient.wardName">
              {{ patient.departName || patient.wardName }}
            </span>
            <span class="meta-item" v-if="patient.icuDays != null">入科第 {{ patient.icuDays }} 天</span>
            <span class="meta-pill vent" v-if="patient.ventilated">机械通气中</span>
          </div>
        </div>
      </div>
      <div class="header-right">
        <el-radio-group v-model="hours" size="small" @change="onQuickRangeChange">
          <el-radio-button :label="24">近24小时</el-radio-button>
          <el-radio-button :label="72">近3天</el-radio-button>
          <el-radio-button :label="168">近7天</el-radio-button>
          <el-radio-button :label="720">近30天</el-radio-button>
        </el-radio-group>
        <el-date-picker
          v-model="customRange"
          type="datetimerange"
          size="small"
          class="range-picker"
          range-separator="至"
          start-placeholder="自定义开始"
          end-placeholder="自定义结束"
          value-format="YYYY-MM-DDTHH:mm:ss"
          :default-time="defaultTime"
          :clearable="true"
          @change="onCustomRangeChange"
        />
        <el-button :icon="Refresh" size="small" :loading="loading" @click="loadData">刷新</el-button>
      </div>
    </section>

    <div v-loading="loading" class="page-body">
      <el-empty v-if="!loading && loadError" :description="loadError" class="load-error">
        <el-button @click="goBack">返回工作台</el-button>
        <el-button type="primary" @click="loadData">重试</el-button>
      </el-empty>

      <template v-if="patient">
        <div class="window-bar">
          <span>数据窗口：{{ fmtDateTime(window.startTime) }} 至 {{ fmtDateTime(window.endTime) }}</span>
          <span class="window-src">GCS 来自正式评分文书；神志 / 瞳孔 / ICP 来自重症系统监测评估（只读）</span>
        </div>

        <!-- 急性脑损伤诊断识别 -->
        <div :class="['scale-card', 'diag-card', diagnosis.brainInjury ? 'is-brain-injury' : '']">
          <div class="scale-head">
            <span class="scale-title">急性脑损伤识别（ICD-10：I60–I63 脑卒中 / 颅内出血，S06 创伤性脑损伤）</span>
            <span :class="['scale-badge', diagBadgeClass]">{{ diagBadgeText }}</span>
          </div>
          <template v-if="diagnosis.dataStatus === 'UNKNOWN'">
            <div class="chart-empty is-error">
              诊断数据暂不可用
              <el-button link type="primary" @click="loadData">点击重试</el-button>
            </div>
          </template>
          <template v-else-if="diagnosis.brainInjury">
            <div class="diag-banner">
              <span class="diag-cat">{{ diagnosis.categoryName || '急性脑损伤' }}</span>
              <span class="diag-hint">已识别为急性脑损伤患者，下列神经功能监测按高危人群呈现</span>
            </div>
            <div class="diag-chips nice-scroll">
              <span v-for="(d, i) in diagnosis.items" :key="i" class="diag-chip">
                <b>{{ d.diagName || '—' }}</b>
                <span class="diag-chip-meta">
                  {{ d.diagCode || '无编码' }} · {{ d.diagTypeName || '诊断' }} · {{ fmtShort(d.diagTime) }}
                </span>
              </span>
            </div>
          </template>
          <template v-else>
            <div class="diag-none">未检索到 I60–I63 / S06 急性脑损伤诊断（已自动排除脑梗死后遗症、个人史、鉴别 / 否定性诊断）。</div>
          </template>
          <div class="scale-note" v-if="diagnosis.note">{{ diagnosis.note }}</div>
        </div>

        <!-- GCS / 神志 / ICP 三卡 -->
        <div class="scale-grid">
          <!-- GCS -->
          <div class="scale-card">
            <div class="scale-head">
              <span class="scale-title">GCS 格拉斯哥昏迷评分</span>
              <span :class="['scale-badge', statusClass(gcs.dataStatus)]">{{ statusText(gcs.dataStatus) }}</span>
            </div>
            <div class="scale-main">
              <div class="latest">
                <div class="latest-value">{{ gcs.latest ? gcs.latest.display : '—' }}</div>
                <div class="latest-sub">
                  <span>分（3–15，插管记 T）</span>
                  <span v-if="gcs.latest" class="latest-time">{{ fmtShort(gcs.latest.time) }}</span>
                </div>
              </div>
              <div class="mini-stats">
                <div><label>次数</label><b>{{ gcs.count || 0 }}</b></div>
                <div><label>E 睁眼</label><b>{{ gcs.latest && gcs.latest.e != null ? gcs.latest.e : '—' }}</b></div>
                <div><label>V 言语</label><b>{{ gcs.latest ? (gcs.latest.intubated ? 'T' : (gcs.latest.v != null ? gcs.latest.v : '—')) : '—' }}</b></div>
                <div><label>M 运动</label><b>{{ gcs.latest && gcs.latest.m != null ? gcs.latest.m : '—' }}</b></div>
              </div>
            </div>
            <div class="chart-wrap">
              <div v-if="hasGcsPlot" ref="gcsChart" class="chart"></div>
              <div v-else :class="['chart-empty', { 'is-error': gcs.dataStatus === 'UNKNOWN' }]">
                <template v-if="gcs.dataStatus === 'UNKNOWN'">
                  数据暂不可用
                  <el-button link type="primary" @click="loadData">点击重试</el-button>
                </template>
                <template v-else>窗口内暂无 GCS 评估文书</template>
              </div>
            </div>
            <div v-if="gcs.latest && gcs.latest.intubated" class="partial-hint">最新评估为气管插管 / 气切，言语项不可评，总分按 E+M 记 T，不反哺 SOFA 言语项</div>
            <div class="scale-note">{{ gcs.note }}</div>
          </div>

          <!-- 神志 -->
          <div class="scale-card">
            <div class="scale-head">
              <span class="scale-title">神志 / 意识状态</span>
              <span :class="['scale-badge', statusClass(consciousness.dataStatus)]">{{ statusText(consciousness.dataStatus) }}</span>
            </div>
            <div class="scale-main">
              <div class="latest">
                <div class="latest-value">{{ consciousness.latest ? consciousness.latest.code : '—' }}</div>
                <div class="latest-sub">
                  <span>{{ consciousness.latest ? consciousness.latest.text : '类别编码' }}</span>
                  <span v-if="consciousness.latest" class="latest-time">{{ fmtShort(consciousness.latest.time) }}</span>
                </div>
              </div>
              <div class="mini-stats">
                <div><label>评估次数</label><b>{{ consciousness.count || 0 }}</b></div>
              </div>
            </div>
            <div class="chart-wrap">
              <div v-if="hasConsciousnessPlot" ref="consciousnessChart" class="chart"></div>
              <div v-else :class="['chart-empty', { 'is-error': consciousness.dataStatus === 'UNKNOWN' }]">
                <template v-if="consciousness.dataStatus === 'UNKNOWN'">
                  数据暂不可用
                  <el-button link type="primary" @click="loadData">点击重试</el-button>
                </template>
                <template v-else>窗口内暂无神志评估记录</template>
              </div>
            </div>
            <div class="scale-note">{{ consciousness.note }}</div>
          </div>

          <!-- ICP -->
          <div class="scale-card">
            <div class="scale-head">
              <span class="scale-title">颅内压 ICP（mmHg）</span>
              <span :class="['scale-badge', statusClass(icp.dataStatus)]">{{ icpStatusText }}</span>
            </div>
            <div class="scale-main">
              <div class="latest">
                <div class="latest-value">{{ icp.latest && icp.latest.value != null ? icp.latest.value : '—' }}</div>
                <div class="latest-sub">
                  <span>mmHg · 有创监测</span>
                  <span v-if="icp.latest" class="latest-time">{{ fmtShort(icp.latest.time) }}</span>
                </div>
              </div>
              <div class="mini-stats">
                <div><label>次数</label><b>{{ icp.count || 0 }}</b></div>
                <div><label>最低</label><b>{{ icp.min != null ? icp.min : '—' }}</b></div>
                <div><label>最高</label><b>{{ icp.max != null ? icp.max : '—' }}</b></div>
              </div>
            </div>
            <div class="chart-wrap">
              <div v-if="hasIcpPlot" ref="icpChart" class="chart"></div>
              <div v-else :class="['chart-empty', icpEmptyClass]">
                <template v-if="icp.dataStatus === 'UNKNOWN'">
                  数据暂不可用
                  <el-button link type="primary" @click="loadData">点击重试</el-button>
                </template>
                <template v-else-if="icp.dataStatus === 'NOT_APPLICABLE'">非脑损伤患者，通常不进行有创 ICP 监测</template>
                <template v-else-if="icp.dataStatus === 'NOT_RECORDED'">脑损伤患者窗口内暂无 ICP 记录（无记录不等于未监测）</template>
                <template v-else>窗口内暂无 ICP 记录</template>
              </div>
            </div>
            <div class="scale-note">{{ icp.note }}</div>
          </div>
        </div>

        <!-- 双侧瞳孔 -->
        <div class="scale-card pupil-card">
          <div class="scale-head">
            <span class="scale-title">双侧瞳孔（大小 mm / 对光反射）</span>
            <span :class="['scale-badge', statusClass(pupils.dataStatus)]">{{ statusText(pupils.dataStatus) }}</span>
          </div>
          <div class="pupil-summary" v-if="pupils.latest">
            <span class="pupil-side">
              <label>左眼</label>
              <b>{{ pupils.latest.leftMm != null ? pupils.latest.leftMm + ' mm' : '—' }}</b>
              <span :class="['pupil-reflex', reflexClass(pupils.latest.leftReflex)]">{{ pupils.latest.leftReflex || '—' }}</span>
            </span>
            <span class="pupil-side">
              <label>右眼</label>
              <b>{{ pupils.latest.rightMm != null ? pupils.latest.rightMm + ' mm' : '—' }}</b>
              <span :class="['pupil-reflex', reflexClass(pupils.latest.rightReflex)]">{{ pupils.latest.rightReflex || '—' }}</span>
            </span>
            <span class="pupil-side">
              <label>左右差</label>
              <b :class="{ 'pupil-warn': pupils.latest.diffMm != null && pupils.latest.diffMm >= 1 }">
                {{ pupils.latest.diffMm != null ? pupils.latest.diffMm + ' mm' : '—' }}
              </b>
            </span>
            <span class="pupil-time">最新 {{ fmtDateTime(pupils.latest.time) }} · {{ pupils.latest.staffName || '—' }}</span>
          </div>
          <div class="chart-wrap">
            <div v-if="hasPupilPlot" ref="pupilChart" class="chart chart-tall"></div>
            <div v-else :class="['chart-empty', 'chart-tall', { 'is-error': pupils.dataStatus === 'UNKNOWN' }]">
              <template v-if="pupils.dataStatus === 'UNKNOWN'">
                数据暂不可用
                <el-button link type="primary" @click="loadData">点击重试</el-button>
              </template>
              <template v-else-if="pupils.dataStatus === 'PARTIAL'">仅采集到单侧瞳孔或部分对光反射</template>
              <template v-else>窗口内暂无瞳孔评估记录</template>
            </div>
          </div>
          <div class="scale-note">{{ pupils.note || '“不规则 / 无法观察”为非数值结果，不参与左右差与折线绘制；左右差、对光反射变化仅作事实提示，阈值预警需科室审批。' }}</div>
        </div>

        <!-- 评估明细 -->
        <div class="scale-card detail-card">
          <div class="scale-head">
            <span class="scale-title">神经功能评估明细</span>
            <el-radio-group v-model="detailFilter" size="small">
              <el-radio-button label="all">全部</el-radio-button>
              <el-radio-button label="consciousness">神志</el-radio-button>
              <el-radio-button label="pupils">瞳孔</el-radio-button>
              <el-radio-button label="gcs">GCS</el-radio-button>
              <el-radio-button label="icp">ICP</el-radio-button>
            </el-radio-group>
          </div>
          <el-table :data="filteredRows" size="small" class="detail-table" stripe height="340">
            <el-table-column label="评估时间" prop="timeText" width="150" />
            <el-table-column label="项目" prop="itemName" width="110" />
            <el-table-column label="结果" prop="result" width="220" />
            <el-table-column label="分项 / 备注" prop="detail" show-overflow-tooltip />
            <el-table-column label="评估人" prop="staffName" width="110" />
          </el-table>
        </div>
      </template>
    </div>
  </div>
</template>

<script>
import * as echarts from '../utils/echarts'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Refresh } from '@element-plus/icons-vue'
import { fetchNeuroMonitor } from '../api/workbench'
import { setCurrentPatient } from '../utils/patientContext'

const EMPTY_DIAG = () => ({ dataStatus: 'NOT_RECORDED', brainInjury: false, category: '', categoryName: '', items: [], note: '' })
const EMPTY_LIST = () => ({ dataStatus: 'NOT_RECORDED', latest: null, points: [], count: 0, note: '' })
const EMPTY_ICP = () => ({ dataStatus: 'NOT_RECORDED', latest: null, points: [], count: 0, min: null, max: null, note: '' })

export default {
  name: 'NeuroMonitor',
  components: { ArrowLeft, Refresh },
  data() {
    return {
      hours: 24,
      customRange: [null, null],
      defaultTime: [new Date(2000, 0, 1, 0, 0, 0), new Date(2000, 0, 1, 23, 59, 59)],
      loading: false,
      loadError: '',
      patient: null,
      window: { startTime: '', endTime: '' },
      diagnosis: EMPTY_DIAG(),
      gcs: EMPTY_LIST(),
      consciousness: EMPTY_LIST(),
      pupils: EMPTY_LIST(),
      icp: EMPTY_ICP(),
      detailFilter: 'all',
      charts: {}
    }
  },
  computed: {
    hasGcsPlot() {
      return this.gcs.dataStatus !== 'UNKNOWN' && (this.gcs.points || []).some((p) => p.total != null)
    },
    hasConsciousnessPlot() {
      return this.consciousness.dataStatus !== 'UNKNOWN' && (this.consciousness.points || []).some((p) => p.code != null)
    },
    hasIcpPlot() {
      return this.icp.dataStatus !== 'UNKNOWN' && (this.icp.points || []).some((p) => p.value != null)
    },
    hasPupilPlot() {
      return this.pupils.dataStatus !== 'UNKNOWN' &&
        (this.pupils.points || []).some((p) => p.leftMm != null || p.rightMm != null)
    },
    diagBadgeText() {
      if (this.diagnosis.dataStatus === 'UNKNOWN') return '暂不可用'
      return this.diagnosis.brainInjury ? (this.diagnosis.categoryName || '急性脑损伤') : '未命中'
    },
    diagBadgeClass() {
      if (this.diagnosis.dataStatus === 'UNKNOWN') return 'st-err'
      return this.diagnosis.brainInjury ? 'st-brain' : 'st-muted'
    },
    icpStatusText() {
      return ({
        FOUND: '已记录',
        PARTIAL: '部分',
        NOT_RECORDED: '暂无记录',
        NOT_APPLICABLE: '不适用',
        UNABLE_TO_ASSESS: '无法评估',
        UNKNOWN: '暂不可用'
      })[this.icp.dataStatus] || '暂无记录'
    },
    icpEmptyClass() {
      return {
        'is-error': this.icp.dataStatus === 'UNKNOWN',
        'is-na': this.icp.dataStatus === 'NOT_APPLICABLE'
      }
    },
    rows() {
      const out = []
      ;(this.consciousness.points || []).forEach((p) => {
        out.push({
          kind: 'consciousness',
          itemName: '神志',
          time: p.time,
          timeText: this.fmtDateTime(p.time),
          result: `${p.text || '—'}（编码 ${p.code != null ? p.code : '—'}）`,
          detail: p.mainline ? '主线昏迷分级状态' : '治疗性 / 其他意识状态，不参与昏迷加深差值',
          staffName: p.staffName || '—'
        })
      })
      ;(this.pupils.points || []).forEach((p) => {
        const l = `${p.leftMm != null ? p.leftMm + 'mm' : '—'}/${p.leftReflex || '—'}`
        const r = `${p.rightMm != null ? p.rightMm + 'mm' : '—'}/${p.rightReflex || '—'}`
        const detail = []
        if (p.unobservable) detail.push('含“不规则 / 无法观察”等非数值结果')
        if (p.diffMm != null) detail.push(`左右差 ${p.diffMm}mm`)
        if (p.leftRaw && p.leftRaw.includes('/')) detail.push(`左原始：${p.leftRaw}`)
        if (p.rightRaw && p.rightRaw.includes('/')) detail.push(`右原始：${p.rightRaw}`)
        out.push({
          kind: 'pupils',
          itemName: '瞳孔',
          time: p.time,
          timeText: this.fmtDateTime(p.time),
          result: `左 ${l}；右 ${r}`,
          detail: detail.join('；') || '双侧等大等圆对光灵敏',
          staffName: p.staffName || '—'
        })
      })
      ;(this.gcs.points || []).forEach((p) => {
        const v = p.intubated ? 'T（插管不可评）' : (p.v != null ? p.v : '—')
        out.push({
          kind: 'gcs',
          itemName: 'GCS',
          time: p.time,
          timeText: this.fmtDateTime(p.time),
          result: `${p.display || '—'} 分`,
          detail: `E睁眼 ${p.e != null ? p.e : '—'}，V言语 ${v}，M运动 ${p.m != null ? p.m : '—'}${p.audited ? '；已审核' : '；未审核'}`,
          staffName: p.staffName || '—'
        })
      })
      ;(this.icp.points || []).forEach((p) => {
        out.push({
          kind: 'icp',
          itemName: 'ICP',
          time: p.time,
          timeText: this.fmtDateTime(p.time),
          result: `${p.value != null ? p.value : '—'} mmHg`,
          detail: p.raw && String(p.raw) !== String(p.value) ? `原始值：${p.raw}` : '',
          staffName: p.staffName || '—'
        })
      })
      return out.sort((a, b) => String(b.time).localeCompare(String(a.time)))
    },
    filteredRows() {
      if (this.detailFilter === 'all') return this.rows
      return this.rows.filter((r) => r.kind === this.detailFilter)
    }
  },
  mounted() {
    this.loadData()
    window.addEventListener('resize', this.handleResize)
  },
  beforeUnmount() {
    window.removeEventListener('resize', this.handleResize)
    Object.values(this.charts).forEach((c) => { if (c) c.dispose() })
    this.charts = {}
  },
  methods: {
    async loadData() {
      const patientId = this.$route.query.patientId
      if (!patientId) {
        this.loadError = '缺少 patientId 参数，请从患者工作台选择患者后进入'
        return
      }
      this.loading = true
      this.loadError = ''
      try {
        const useCustom = this.customRange && this.customRange[0] && this.customRange[1]
        const options = useCustom
          ? { start: this.customRange[0], end: this.customRange[1] }
          : { hours: this.hours || 24 }
        const data = await fetchNeuroMonitor(patientId, options)
        if (!data || !data.patient) {
          this.loadError = '患者不存在或已出科'
          return
        }
        this.patient = data.patient
        this.window = data.window || { startTime: '', endTime: '' }
        this.diagnosis = data.diagnosis || EMPTY_DIAG()
        this.gcs = data.gcs || EMPTY_LIST()
        this.consciousness = data.consciousness || EMPTY_LIST()
        this.pupils = data.pupils || EMPTY_LIST()
        this.icp = data.icp || EMPTY_ICP()
        setCurrentPatient(data.patient)
        await this.$nextTick()
        this.renderAllCharts()
      } catch (e) {
        console.error('[NeuroMonitor] loadData failed', e)
        this.loadError = e.message || '网络异常'
      } finally {
        this.loading = false
      }
    },
    goBack() {
      this.$router.push('/page/patient-workbench')
    },
    onQuickRangeChange() {
      this.customRange = [null, null]
      this.loadData()
    },
    onCustomRangeChange(val) {
      if (!val || !val[0] || !val[1]) {
        if (!this.hours) this.hours = 24
        this.loadData()
        return
      }
      const spanMs = new Date(val[1]).getTime() - new Date(val[0]).getTime()
      if (spanMs > 30 * 24 * 3600 * 1000) {
        ElMessage.warning('自定义时间范围不能超过 30 天')
        this.customRange = [null, null]
        return
      }
      if (spanMs <= 0) {
        ElMessage.warning('结束时间必须晚于开始时间')
        this.customRange = [null, null]
        return
      }
      this.hours = null
      this.loadData()
    },
    handleResize() {
      Object.values(this.charts).forEach((c) => { if (c) c.resize() })
    },

    // ---------------- 图表 ----------------
    renderAllCharts() {
      // 单个图表渲染失败只影响该图，绝不让整页掉进致命空态（数据已取到）
      const safe = (name, fn) => {
        try {
          fn()
        } catch (e) {
          console.error(`[NeuroMonitor] 图表渲染失败: ${name}`, e)
        }
      }
      safe('gcs', () => this.renderGcs())
      safe('consciousness', () => this.renderConsciousness())
      safe('icp', () => this.renderIcp())
      safe('pupils', () => this.renderPupils())
    },
    baseGrid() {
      return { left: 34, right: 14, top: 14, bottom: 22 }
    },
    timeAxis() {
      return {
        type: 'time',
        // hideOverlap + interval:auto：窗口拉长（如近7天/30天而数据只有入科这两天）时
          // 标签会挤成一团糊住图表轴，让 echarts 自动稀疏 / 隐藏重叠标签
          axisLabel: { color: '#a8a29e', fontSize: 10, hideOverlap: true, interval: 'auto', formatter: (v) => this.fmtShort(v) },
        axisLine: { lineStyle: { color: '#e7e5e4' } },
        splitLine: { show: false },
        axisTick: { show: false }
      }
    },
    getChart(key, el) {
      // 每次以当前 DOM 上的实例为准，避免 charts[key] 里缓存的是绑定在旧节点上的实例
      const chart = echarts.getInstanceByDom(el) || echarts.init(el)
      this.charts[key] = chart
      // clear() 必须在 setOption 之前：切换时间范围时数据没变、DOM 也没重建，但上一轮
      // setOption 留下的 axis/series 关联会坏掉，导致本次渲染抛
      //「Cannot read properties of undefined (reading 'getAxis')」/「reading 'type'」
      // —— 表现为坐标轴与 tooltip 停留在旧数据、鼠标悬浮无反应。
      chart.clear()
      return chart
    },
    renderGcs() {
      const el = this.$refs.gcsChart
      if (!el || !this.hasGcsPlot) return
      const pts = (this.gcs.points || []).filter((p) => p.total != null)
      const chart = this.getChart('gcs', el)
      chart.setOption({
        backgroundColor: 'transparent',
        animationDuration: 300,
        grid: this.baseGrid(),
        tooltip: {
          trigger: 'axis',
          confine: true,
          backgroundColor: 'rgba(255,255,255,0.97)',
          borderColor: '#e7e5e4',
          textStyle: { color: '#292524', fontSize: 11 },
          formatter: (ps) => {
            if (!ps || !ps.length) return ''
            const pt = ps[0].data && ps[0].data.point
            if (!pt) return ''
            const v = pt.intubated ? 'T（插管不可评）' : pt.v
            const tub = pt.intubated ? '<br/><b style="color:#b91c1c">气管插管，言语不可评，记 T</b>' : ''
            return `${this.fmtDateTime(pt.time)}<br/>GCS 总分：<b>${pt.display}</b><br/>E ${pt.e} / V ${v} / M ${pt.m}${tub}`
          }
        },
        xAxis: this.timeAxis(),
        yAxis: {
          type: 'value', min: 3, max: 15, interval: 3,
          axisLabel: { color: '#a8a29e', fontSize: 10 },
          splitLine: { lineStyle: { color: '#f5f5f4' } }
        },
        series: [{
          type: 'line',
          data: pts.map((p, idx) => ({
            value: [this.toDate(p.time), p.total],
            point: p,
            symbolSize: idx === pts.length - 1 ? 9 : 6,
            itemStyle: { color: p.intubated ? '#dc2626' : '#ea580c' }
          })),
          smooth: false,
          connectNulls: false,
          symbol: 'circle',
          lineStyle: { width: 2, color: '#ea580c' }
        }]
      })
      chart.resize()
    },
    renderConsciousness() {
      const el = this.$refs.consciousnessChart
      if (!el || !this.hasConsciousnessPlot) return
      const pts = (this.consciousness.points || []).filter((p) => p.code != null)
      const chart = this.getChart('consciousness', el)
      chart.setOption({
        backgroundColor: 'transparent',
        animationDuration: 300,
        grid: this.baseGrid(),
        tooltip: {
          trigger: 'axis',
          confine: true,
          backgroundColor: 'rgba(255,255,255,0.97)',
          borderColor: '#e7e5e4',
          textStyle: { color: '#292524', fontSize: 11 },
          formatter: (ps) => {
            if (!ps || !ps.length) return ''
            const pt = ps[0].data && ps[0].data.point
            if (!pt) return ''
            const tag = pt.mainline ? '' : '<br/><span style="color:#b45309">治疗性/其他状态，不参与昏迷加深差值</span>'
            return `${this.fmtDateTime(pt.time)}<br/>神志：<b>${pt.text}（${pt.code}）</b>${tag}`
          }
        },
        xAxis: this.timeAxis(),
        yAxis: {
          type: 'value', min: 1, max: 10, interval: 1,
          axisLabel: { color: '#a8a29e', fontSize: 10 },
          splitLine: { lineStyle: { color: '#f5f5f4' } }
        },
        series: [{
          type: 'line',
          step: 'end',
          data: pts.map((p, idx) => ({
            value: [this.toDate(p.time), p.code],
            point: p,
            symbolSize: idx === pts.length - 1 ? 9 : 5,
            itemStyle: { color: p.mainline ? '#57534e' : '#d97706' }
          })),
          connectNulls: false,
          symbol: 'circle',
          lineStyle: { width: 2, color: '#57534e' }
        }]
      })
      chart.resize()
    },
    renderIcp() {
      const el = this.$refs.icpChart
      if (!el || !this.hasIcpPlot) return
      const pts = (this.icp.points || []).filter((p) => p.value != null)
      const chart = this.getChart('icp', el)
      chart.setOption({
        backgroundColor: 'transparent',
        animationDuration: 300,
        grid: this.baseGrid(),
        tooltip: {
          trigger: 'axis',
          confine: true,
          backgroundColor: 'rgba(255,255,255,0.97)',
          borderColor: '#e7e5e4',
          textStyle: { color: '#292524', fontSize: 11 },
          formatter: (ps) => {
            if (!ps || !ps.length) return ''
            const pt = ps[0].data && ps[0].data.point
            if (!pt) return ''
            return `${this.fmtDateTime(pt.time)}<br/>ICP：<b>${pt.value} mmHg</b>`
          }
        },
        xAxis: this.timeAxis(),
        yAxis: {
          type: 'value', min: 0,
          axisLabel: { color: '#a8a29e', fontSize: 10 },
          splitLine: { lineStyle: { color: '#f5f5f4' } }
        },
        series: [{
          type: 'line',
          data: pts.map((p, idx) => ({
            value: [this.toDate(p.time), p.value],
            point: p,
            symbolSize: idx === pts.length - 1 ? 9 : 5
          })),
          smooth: false,
          connectNulls: false,
          symbol: 'circle',
          lineStyle: { width: 2, color: '#7c3aed' },
          itemStyle: { color: '#7c3aed' }
        }]
      })
      chart.resize()
    },
    renderPupils() {
      const el = this.$refs.pupilChart
      if (!el || !this.hasPupilPlot) return
      const pts = this.pupils.points || []
      const toSeries = (field, color) => pts.map((p, idx) => ({
        value: p[field] != null ? [this.toDate(p.time), p[field]] : null,
        point: p,
        side: field,
        symbolSize: idx === pts.length - 1 ? 8 : 5
      })).filter((d) => d.value)
      const chart = this.getChart('pupils', el)
      chart.setOption({
        backgroundColor: 'transparent',
        animationDuration: 300,
        grid: { left: 34, right: 14, top: 26, bottom: 22 },
        legend: {
          data: ['左眼', '右眼'],
          top: 0, right: 8,
          textStyle: { color: '#78716c', fontSize: 11 },
          itemWidth: 14, itemHeight: 8
        },
        tooltip: {
          trigger: 'axis',
          confine: true,
          backgroundColor: 'rgba(255,255,255,0.97)',
          borderColor: '#e7e5e4',
          textStyle: { color: '#292524', fontSize: 11 },
          formatter: (ps) => {
            if (!ps || !ps.length) return ''
            const pt = ps[0].data && ps[0].data.point
            if (!pt) return ''
            return `${this.fmtDateTime(pt.time)}<br/>` +
              `左眼：<b>${pt.leftMm != null ? pt.leftMm + 'mm' : '—'}</b> / ${pt.leftReflex || '—'}<br/>` +
              `右眼：<b>${pt.rightMm != null ? pt.rightMm + 'mm' : '—'}</b> / ${pt.rightReflex || '—'}` +
              (pt.diffMm != null ? `<br/>左右差：${pt.diffMm}mm` : '')
          }
        },
        xAxis: this.timeAxis(),
        yAxis: {
          type: 'value', min: 0, max: 8, interval: 1,
          name: 'mm', nameTextStyle: { color: '#a8a29e', fontSize: 10 },
          axisLabel: { color: '#a8a29e', fontSize: 10 },
          splitLine: { lineStyle: { color: '#f5f5f4' } }
        },
        series: [
          {
            name: '左眼', type: 'line', data: toSeries('leftMm', '#ea580c'),
            connectNulls: false, symbol: 'circle',
            lineStyle: { width: 2, color: '#ea580c' }, itemStyle: { color: '#ea580c' }
          },
          {
            name: '右眼', type: 'line', data: toSeries('rightMm', '#78716c'),
            connectNulls: false, symbol: 'circle',
            lineStyle: { width: 2, color: '#78716c' }, itemStyle: { color: '#78716c' }
          }
        ]
      })
      chart.resize()
    },

    // ---------------- 状态 / 标签 ----------------
    statusText(s) {
      return ({
        FOUND: '已记录',
        PARTIAL: '部分',
        NOT_RECORDED: '暂无记录',
        UNABLE_TO_ASSESS: '无法评估',
        NOT_APPLICABLE: '不适用',
        UNKNOWN: '暂不可用'
      })[s] || '暂无记录'
    },
    statusClass(s) {
      if (s === 'FOUND') return 'st-ok'
      if (s === 'PARTIAL') return 'st-warn'
      if (s === 'UNKNOWN') return 'st-err'
      return 'st-muted'
    },
    reflexClass(r) {
      if (r === '消失') return 'reflex-gone'
      if (r === '迟钝') return 'reflex-dull'
      if (r === '灵敏') return 'reflex-ok'
      return 'reflex-na'
    },

    // ---------------- 时间 ----------------
    toDate(t) {
      const d = new Date(t)
      return isNaN(d.getTime()) ? new Date() : d
    },
    pad(n) { return String(n).padStart(2, '0') },
    fmtDateTime(t) {
      if (!t) return '—'
      const d = new Date(t)
      if (isNaN(d.getTime())) return String(t)
      return `${d.getFullYear()}-${this.pad(d.getMonth() + 1)}-${this.pad(d.getDate())} ${this.pad(d.getHours())}:${this.pad(d.getMinutes())}`
    },
    fmtShort(t) {
      if (!t) return '--:--'
      const d = new Date(t)
      if (isNaN(d.getTime())) return String(t)
      return `${this.pad(d.getMonth() + 1)}-${this.pad(d.getDate())} ${this.pad(d.getHours())}:${this.pad(d.getMinutes())}`
    }
  }
}
</script>

<style scoped>
.neuro-page {
  padding: 16px 20px 28px;
  min-height: 100vh;
  background: #fafaf9;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  margin-bottom: 14px;
}
.header-left { display: flex; align-items: center; gap: 14px; }
.back-btn { padding: 0; color: #c2410c; font-weight: 600; }
.patient-name { margin: 0; font-size: 20px; font-weight: 700; color: #1c1917; }
.patient-meta { display: flex; align-items: center; gap: 8px; margin-top: 4px; flex-wrap: wrap; }
.meta-item { font-size: 12px; color: #78716c; }
.meta-pill {
  font-size: 12px; padding: 2px 9px; border-radius: 999px;
  border: 1px solid #e7e5e4; background: #fff; color: #57534e;
}
.meta-pill.bed { background: #fff7ed; border-color: #fed7aa; color: #c2410c; font-weight: 600; }
.meta-pill.vent { background: #fef3c7; border-color: #fbd9a8; color: #b45309; }
.header-right { display: flex; align-items: center; gap: 10px; }
.range-picker { width: 330px; }

.window-bar {
  display: flex; align-items: center; justify-content: space-between; gap: 12px;
  font-size: 12px; color: #78716c; background: #fff;
  border: 1px solid #e7e5e4; border-radius: 8px; padding: 7px 12px; margin-bottom: 12px;
}
.window-src { color: #a8a29e; }

.scale-card {
  background: #fff;
  border: 1px solid #e7e5e4;
  border-radius: 12px;
  padding: 14px 16px;
  box-shadow: 0 1px 2px rgba(28, 25, 23, 0.03);
  margin-bottom: 12px;
}
.scale-head {
  display: flex; align-items: center; justify-content: space-between; gap: 8px; margin-bottom: 10px;
}
.scale-title { font-size: 15px; font-weight: 700; color: #1c1917; }
.scale-badge {
  font-size: 11px; padding: 2px 9px; border-radius: 999px; border: 1px solid transparent; white-space: nowrap;
}
.st-ok { background: #f0f9f3; color: #15803d; border-color: #b8e3c6; }
.st-warn { background: #fef5e4; color: #b45309; border-color: #fbd9a8; }
.st-err { background: #fcebeb; color: #b91c1c; border-color: #f5b4b4; }
.st-muted { background: #f5f5f4; color: #78716c; border-color: #e7e5e4; }
.st-brain { background: #fcebeb; color: #b91c1c; border-color: #f5b4b4; font-weight: 700; }

.scale-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin-bottom: 0;
}
.scale-grid .scale-card { margin-bottom: 12px; }
.scale-main { display: flex; align-items: flex-end; justify-content: space-between; gap: 10px; margin-bottom: 8px; }
.latest-value { font-size: 30px; font-weight: 700; color: #ea580c; line-height: 1.1; }
.latest-sub { display: flex; flex-direction: column; gap: 2px; font-size: 11px; color: #78716c; margin-top: 2px; }
.latest-time { color: #a8a29e; }
.mini-stats { display: flex; gap: 12px; text-align: right; }
.mini-stats > div { display: flex; flex-direction: column; }
.mini-stats label { font-size: 10px; color: #a8a29e; }
.mini-stats b { font-size: 14px; color: #44403c; font-weight: 600; }

.chart-wrap { min-height: 150px; }
.chart { width: 100%; height: 150px; }
.chart-tall { height: 190px; }
.chart-empty {
  height: 150px; display: flex; flex-direction: column; align-items: center; justify-content: center;
  gap: 6px; font-size: 12px; color: #a8a29e; background: #fafaf9;
  border: 1px dashed #e7e5e4; border-radius: 8px; padding: 0 16px; text-align: center;
}
.chart-tall.chart-empty { height: 190px; }
.chart-empty.is-error { color: #b91c1c; background: #fef2f2; border-color: #fecaca; }
.chart-empty.is-na { color: #78716c; background: #f5f5f4; }
.partial-hint { font-size: 11px; color: #b91c1c; margin-top: 6px; line-height: 1.5; }
.scale-note { font-size: 11px; color: #a8a29e; line-height: 1.5; margin-top: 8px; border-top: 1px solid #f5f5f4; padding-top: 8px; }

/* 诊断卡 */
.diag-card.is-brain-injury { border-color: #f5b4b4; background: #fffdfc; }
.diag-banner { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; margin-bottom: 10px; }
.diag-cat {
  font-size: 14px; font-weight: 700; color: #b91c1c;
  background: #fcebeb; border: 1px solid #f5b4b4; border-radius: 999px; padding: 4px 14px;
}
.diag-hint { font-size: 12px; color: #78716c; }
.diag-chips { display: flex; flex-wrap: wrap; gap: 8px; max-height: 132px; overflow-y: auto; }
.diag-chip {
  display: flex; flex-direction: column; gap: 2px;
  font-size: 13px; color: #292524; background: #fafaf9;
  border: 1px solid #f0eeec; border-radius: 8px; padding: 7px 11px;
}
.diag-chip b { font-weight: 600; }
.diag-chip-meta { font-size: 11px; color: #a8a29e; }
.diag-none { font-size: 13px; color: #57534e; background: #fafaf9; border: 1px dashed #e7e5e4; border-radius: 8px; padding: 12px 14px; }

/* 瞳孔卡 */
.pupil-summary { display: flex; align-items: center; gap: 22px; flex-wrap: wrap; margin-bottom: 10px; }
.pupil-side { display: flex; align-items: center; gap: 8px; font-size: 13px; color: #44403c; }
.pupil-side label { font-size: 12px; color: #a8a29e; }
.pupil-side b { font-size: 16px; font-weight: 700; color: #1c1917; }
.pupil-warn { color: #b91c1c !important; }
.pupil-reflex { font-size: 12px; padding: 2px 9px; border-radius: 999px; border: 1px solid transparent; }
.reflex-ok { background: #f0f9f3; color: #15803d; border-color: #b8e3c6; }
.reflex-dull { background: #fef5e4; color: #b45309; border-color: #fbd9a8; }
.reflex-gone { background: #fcebeb; color: #b91c1c; border-color: #f5b4b4; }
.reflex-na { background: #f5f5f4; color: #78716c; border-color: #e7e5e4; }
.pupil-time { margin-left: auto; font-size: 12px; color: #a8a29e; }

.detail-card .detail-table { width: 100%; }

.nice-scroll { scrollbar-width: thin; scrollbar-color: #d6d3d1 transparent; }
.nice-scroll::-webkit-scrollbar { width: 6px; height: 6px; }
.nice-scroll::-webkit-scrollbar-thumb { background: #d6d3d1; border-radius: 3px; }
.nice-scroll::-webkit-scrollbar-track { background: transparent; }

.load-error { padding: 60px 0; }

@media (max-width: 1200px) {
  .scale-grid { grid-template-columns: 1fr; }
}
</style>
