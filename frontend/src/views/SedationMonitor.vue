<template>
  <div class="sedation-page qb-theme">
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
        <TimeRangeBar granule="datetime" :default-preset="'24h'" @change="onRangeChange" />
        <el-button :icon="Refresh" size="small" :loading="loading" @click="loadData">刷新</el-button>
      </div>
    </section>

    <div v-loading="loading" class="page-body">
      <!-- 加载失败 / 缺少患者 -->
      <el-empty v-if="!loading && loadError" :description="loadError" class="load-error">
        <el-button @click="goBack">返回工作台</el-button>
        <el-button type="primary" @click="loadData">重试</el-button>
      </el-empty>

      <template v-if="patient">
        <div class="window-bar">
          <span>数据窗口：{{ fmtDateTime(window.startTime) }} 至 {{ fmtDateTime(window.endTime) }}</span>
          <span class="window-src">数据来自重症系统正式评分文书（只读，不与监测镜像合并）</span>
        </div>

        <!-- 数值量表：NRS / VAS / CPOT / RASS，各自独立坐标，禁止合并 -->
        <div class="scale-grid">
          <!-- NRS -->
          <div class="scale-card">
            <div class="scale-head">
              <span class="scale-title">NRS 疼痛评分</span>
              <span :class="['scale-badge', statusClass(nrs.dataStatus)]">{{ statusText(nrs.dataStatus) }}</span>
            </div>
            <div class="scale-main">
              <div class="latest">
                <div class="latest-value">{{ latestScore(nrs) }}</div>
                <div class="latest-sub">
                  <span>分 · 0-10</span>
                  <span v-if="nrs.latest" class="latest-time">{{ fmtShort(nrs.latest.time) }}</span>
                </div>
              </div>
              <div class="mini-stats">
                <div><label>次数</label><b>{{ nrs.count || 0 }}</b></div>
                <div><label>最低</label><b>{{ nrs.min != null ? nrs.min : '—' }}</b></div>
                <div><label>最高</label><b>{{ nrs.max != null ? nrs.max : '—' }}</b></div>
              </div>
            </div>
            <div class="chart-wrap">
              <div v-if="hasPlot('nrs')" ref="nrsChart" class="chart"></div>
              <div v-else :class="['chart-empty', { 'is-error': nrs.dataStatus === 'UNKNOWN' }]">
                <template v-if="nrs.dataStatus === 'UNKNOWN'">
                  数据暂不可用
                  <el-button link type="primary" @click="loadData">点击重试</el-button>
                </template>
                <template v-else>窗口内暂无 NRS 评估记录</template>
              </div>
            </div>
            <div v-if="nrs.dataStatus === 'PARTIAL'" class="partial-hint">部分记录分项缺失</div>
            <div class="scale-note">{{ nrs.note }}</div>
          </div>

          <!-- VAS（2024版 ICU-09 认可的疼痛评估工具；源文书接入后出数） -->
          <div class="scale-card">
            <div class="scale-head">
              <span class="scale-title">VAS 疼痛评分</span>
              <span :class="['scale-badge', statusClass(vas.dataStatus)]">{{ statusText(vas.dataStatus) }}</span>
            </div>
            <div class="scale-main">
              <div class="latest">
                <div class="latest-value">{{ latestScore(vas) }}</div>
                <div class="latest-sub">
                  <span>分 · 0-10</span>
                  <span v-if="vas.latest" class="latest-time">{{ fmtShort(vas.latest.time) }}</span>
                </div>
              </div>
              <div class="mini-stats">
                <div><label>次数</label><b>{{ vas.count || 0 }}</b></div>
                <div><label>最低</label><b>{{ vas.min != null ? vas.min : '—' }}</b></div>
                <div><label>最高</label><b>{{ vas.max != null ? vas.max : '—' }}</b></div>
              </div>
            </div>
            <div class="chart-wrap">
              <div v-if="hasPlot('vas')" ref="vasChart" class="chart"></div>
              <div v-else :class="['chart-empty', { 'is-error': vas.dataStatus === 'UNKNOWN' }]">
                <template v-if="vas.dataStatus === 'UNKNOWN'">
                  数据暂不可用
                  <el-button link type="primary" @click="loadData">点击重试</el-button>
                </template>
                <template v-else>窗口内暂无 VAS 评估记录</template>
              </div>
            </div>
            <div class="scale-note">{{ vas.note }}</div>
          </div>

          <!-- CPOT -->
          <div class="scale-card">
            <div class="scale-head">
              <span class="scale-title">CPOT 疼痛评分</span>
              <span :class="['scale-badge', statusClass(cpot.dataStatus)]">{{ statusText(cpot.dataStatus) }}</span>
            </div>
            <div class="scale-main">
              <div class="latest">
                <div class="latest-value">{{ latestScore(cpot) }}</div>
                <div class="latest-sub">
                  <span>分 · 0-8 · {{ cpot.latest && cpot.latest.ventilated ? '机械通气口径' : '可发声口径' }}</span>
                  <span v-if="cpot.latest" class="latest-time">{{ fmtShort(cpot.latest.time) }}</span>
                </div>
              </div>
              <div class="mini-stats">
                <div><label>次数</label><b>{{ cpot.count || 0 }}</b></div>
                <div><label>最低</label><b>{{ cpot.min != null ? cpot.min : '—' }}</b></div>
                <div><label>最高</label><b>{{ cpot.max != null ? cpot.max : '—' }}</b></div>
              </div>
            </div>
            <div class="chart-wrap">
              <div v-if="hasPlot('cpot')" ref="cpotChart" class="chart"></div>
              <div v-else :class="['chart-empty', { 'is-error': cpot.dataStatus === 'UNKNOWN' }]">
                <template v-if="cpot.dataStatus === 'UNKNOWN'">
                  数据暂不可用
                  <el-button link type="primary" @click="loadData">点击重试</el-button>
                </template>
                <template v-else>窗口内暂无 CPOT 评估记录</template>
              </div>
            </div>
            <div v-if="cpot.dataStatus === 'PARTIAL'" class="partial-hint">部分记录分项缺失</div>
            <div class="scale-note">{{ cpot.note }}</div>
          </div>

          <!-- RASS -->
          <div class="scale-card">
            <div class="scale-head">
              <span class="scale-title">RASS 镇静评分</span>
              <span :class="['scale-badge', statusClass(rass.dataStatus)]">{{ statusText(rass.dataStatus) }}</span>
            </div>
            <div class="scale-main">
              <div class="latest">
                <div class="latest-value">{{ latestScore(rass) }}</div>
                <div class="latest-sub">
                  <span>{{ rass.latest && rass.latest.levelText ? rass.latest.levelText : '分级 -5～+4' }}</span>
                  <span v-if="rass.latest" class="latest-time">{{ fmtShort(rass.latest.time) }}</span>
                </div>
              </div>
              <div class="mini-stats">
                <div><label>次数</label><b>{{ rass.count || 0 }}</b></div>
                <div><label>最低</label><b>{{ rass.min != null ? rass.min : '—' }}</b></div>
                <div><label>最高</label><b>{{ rass.max != null ? rass.max : '—' }}</b></div>
              </div>
            </div>
            <div class="chart-wrap">
              <div v-if="hasPlot('rass')" ref="rassChart" class="chart"></div>
              <div v-else :class="['chart-empty', { 'is-error': rass.dataStatus === 'UNKNOWN' }]">
                <template v-if="rass.dataStatus === 'UNKNOWN'">
                  数据暂不可用
                  <el-button link type="primary" @click="loadData">点击重试</el-button>
                </template>
                <template v-else>窗口内暂无 RASS 评估记录</template>
              </div>
            </div>
            <div v-if="rass.dataStatus === 'PARTIAL'" class="partial-hint">部分记录分项缺失</div>
            <div class="scale-note">{{ rass.note }}</div>
          </div>
        </div>

        <!-- CAM-ICU：分类事件，不求均值 -->
        <div class="scale-card cam-card">
          <div class="scale-head">
            <span class="scale-title">CAM-ICU 谵妄评估（分类记录，不计算均值）</span>
            <span :class="['scale-badge', statusClass(cam.dataStatus)]">{{ statusText(cam.dataStatus) }}</span>
          </div>
          <div class="cam-summary nice-scroll">
            <span class="cam-chip total">总评估 <b>{{ cam.total || 0 }}</b></span>
            <span class="cam-chip positive">谵妄阳性 <b>{{ cam.positive || 0 }}</b></span>
            <span class="cam-chip negative">阴性 <b>{{ cam.negative || 0 }}</b></span>
            <span class="cam-chip warning">建议核查 <b>{{ cam.utaSuspect || 0 }}</b></span>
            <span class="cam-chip info">配对RASS缺失 <b>{{ cam.pairUnknown || 0 }}</b></span>
            <span class="cam-chip info">无法识别 <b>{{ cam.unparsed || 0 }}</b></span>
          </div>
          <div class="chart-wrap">
            <div v-if="cam.points && cam.points.length" class="cam-events nice-scroll">
              <div v-for="(c, i) in camEvents" :key="i" class="cam-event">
                <span class="cam-time">{{ fmtDateTime(c.time) }}</span>
                <span :class="['cam-tag', camTagClass(c.derivedStatus)]">{{ camTagText(c.derivedStatus) }}</span>
                <span class="cam-raw">源结果：{{ c.rawResult || '—' }}</span>
                <span class="cam-pair">
                  配对RASS：
                  <template v-if="c.pairedRass != null">{{ c.pairedRass }}（{{ fmtShort(c.pairedRassTime) }}）</template>
                  <template v-else>缺失</template>
                </span>
                <span class="cam-hint" v-if="c.hint">{{ c.hint }}</span>
                <span class="cam-staff">{{ c.staffName || '—' }}</span>
              </div>
            </div>
            <div v-else :class="['chart-empty', { 'is-error': cam.dataStatus === 'UNKNOWN' }]">
              <template v-if="cam.dataStatus === 'UNKNOWN'">
                数据暂不可用
                <el-button link type="primary" @click="loadData">点击重试</el-button>
              </template>
              <template v-else>窗口内暂无 CAM-ICU 评估记录</template>
            </div>
          </div>
          <div class="scale-note">{{ cam.note }}</div>
        </div>

        <!-- 镇痛药使用与评分关联：同窗口的阿片类执行医嘱 -->
        <div class="scale-card">
          <div class="scale-head">
            <span class="scale-title">镇痛药使用（阿片类执行医嘱，与评分同窗口）</span>
            <span class="scale-badge st-ok">{{ analgesia.length }}</span>
          </div>
          <div v-if="analgesiaError" class="scale-note" style="color:#b91c1c">{{ analgesiaError }}</div>
          <el-table v-if="analgesia.length" :data="analgesia" size="small" stripe max-height="300">
            <el-table-column prop="drug_name" label="药品" min-width="130" show-overflow-tooltip />
            <el-table-column label="单次剂量" width="100">
              <template #default="{ row }">
                {{ row.drug_one_dosage != null ? row.drug_one_dosage + (row.drug_one_dosage_unit || '') : '—' }}
              </template>
            </el-table-column>
            <el-table-column prop="spec" label="规格" min-width="110" show-overflow-tooltip />
            <el-table-column label="泵速" width="90">
              <template #default="{ row }">
                {{ row.drug_now_speed != null ? row.drug_now_speed + ' ' + (row.drug_speed_unit || '') : '—' }}
              </template>
            </el-table-column>
            <el-table-column label="开始" width="140">
              <template #default="{ row }">{{ fmtDateTime(row.start_time) }}</template>
            </el-table-column>
            <el-table-column label="结束" width="140">
              <template #default="{ row }">{{ row.end_time ? fmtDateTime(row.end_time) : '进行中' }}</template>
            </el-table-column>
            <el-table-column prop="advice_name" label="医嘱" min-width="130" show-overflow-tooltip />
          </el-table>
          <div v-else class="chart-empty">窗口内无阿片类镇痛药执行记录（芬太尼/吗啡/地佐辛等）</div>
          <div class="scale-note">与上方 NRS / CPOT 评分同时间窗对照阅读：用药后评分应下降；本块仅列执行事实，不做镇痛效果判定。</div>
        </div>

        <!-- 评估明细：可追溯下钻 -->
        <div class="scale-card detail-card">
          <div class="scale-head">
            <span class="scale-title">评估明细</span>
            <el-radio-group v-model="detailFilter" size="small">
              <el-radio-button label="all">全部</el-radio-button>
              <el-radio-button label="nrs">NRS</el-radio-button>
              <el-radio-button label="vas">VAS</el-radio-button>
              <el-radio-button label="cpot">CPOT</el-radio-button>
              <el-radio-button label="rass">RASS</el-radio-button>
              <el-radio-button label="cam">CAM-ICU</el-radio-button>
            </el-radio-group>
          </div>
          <el-table :data="filteredRows" size="small" class="detail-table" stripe height="340">
            <el-table-column label="评估时间" prop="timeText" width="150" />
            <el-table-column label="量表" prop="scaleName" width="110" />
            <el-table-column label="结果" width="120">
              <template #default="{ row }">
                <span v-if="row.scale === 'cam'">{{ row.rawResult || '—' }}</span>
                <span v-else>{{ row.score != null ? row.score : '—' }} 分</span>
              </template>
            </el-table-column>
            <el-table-column label="分级 / 状态" width="150">
              <template #default="{ row }">
                <span v-if="row.scale === 'cam'" :class="['cam-tag', camTagClass(row.derivedStatus)]">
                  {{ camTagText(row.derivedStatus) }}
                </span>
                <span v-else>{{ row.levelText || '—' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="评估人" prop="staffName" width="110" />
            <el-table-column label="分项 / 备注" prop="detail" show-overflow-tooltip />
          </el-table>
        </div>
      </template>
    </div>
  </div>
</template>

<script>
import * as echarts from '../utils/echarts'
import { ArrowLeft, Refresh } from '@element-plus/icons-vue'
import { fetchSedationMonitor, fetchAnalgesia } from '../api/workbench'
import TimeRangeBar from '../components/TimeRangeBar.vue'
import { setCurrentPatient } from '../utils/patientContext'

const SCALE_META = {
  nrs: { name: 'NRS 疼痛', min: 0, max: 10, color: '#ea580c' },
  vas: { name: 'VAS 疼痛', min: 0, max: 10, color: '#f97316' },
  cpot: { name: 'CPOT 疼痛', min: 0, max: 8, color: '#d97706' },
  rass: { name: 'RASS 镇静', min: -5, max: 4, color: '#57534e' }
}

const EMPTY_BLOCK = () => ({ dataStatus: 'NOT_RECORDED', latest: null, points: [], count: 0, min: null, max: null, note: '' })

export default {
  name: 'SedationMonitor',
  components: { ArrowLeft, Refresh, TimeRangeBar },
  data() {
    return {
      hours: 24,
      customRange: [null, null],
      loading: false,
      loadError: '',
      patient: null,
      window: { startTime: '', endTime: '' },
      nrs: EMPTY_BLOCK(),
      vas: EMPTY_BLOCK(),
      cpot: EMPTY_BLOCK(),
      rass: EMPTY_BLOCK(),
      cam: { dataStatus: 'NOT_RECORDED', points: [], total: 0, positive: 0, negative: 0, utaSuspect: 0, pairUnknown: 0, unparsed: 0, note: '' },
      analgesia: [],
      analgesiaError: '',
      detailFilter: 'all',
      charts: {}
    }
  },
  computed: {
    /** 合并四量表明细，按时间倒序，保证每条可追溯到时间/评估人/原始分项 */
    rows() {
      const out = []
      const pushNum = (scale, block) => {
        ;(block.points || []).forEach((p) => {
          out.push({
            scale,
            scaleName: SCALE_META[scale].name,
            time: p.time,
            timeText: this.fmtDateTime(p.time),
            score: p.score,
            levelText: p.levelText || '',
            staffName: p.staffName || '—',
            rawResult: '',
            derivedStatus: '',
            detail: this.numDetail(scale, p)
          })
        })
      }
      pushNum('nrs', this.nrs)
      pushNum('vas', this.vas)
      pushNum('cpot', this.cpot)
      pushNum('rass', this.rass)
      ;(this.cam.points || []).forEach((c) => {
        out.push({
          scale: 'cam',
          scaleName: 'CAM-ICU 谵妄',
          time: c.time,
          timeText: this.fmtDateTime(c.time),
          score: null,
          levelText: '',
          staffName: c.staffName || '—',
          rawResult: c.rawResult || '',
          derivedStatus: c.derivedStatus,
          detail: this.camDetail(c)
        })
      })
      return out.sort((a, b) => String(b.time).localeCompare(String(a.time)))
    },
    filteredRows() {
      if (this.detailFilter === 'all') return this.rows
      return this.rows.filter((r) => r.scale === this.detailFilter)
    },
    /** CAM 事件最新在上 */
    camEvents() {
      return [...(this.cam.points || [])].sort((a, b) => String(b.time).localeCompare(String(a.time)))
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
        const data = await fetchSedationMonitor(patientId, options)
        if (!data || !data.patient) {
          this.loadError = '患者不存在或已出科'
          return
        }
        this.patient = data.patient
        this.window = data.window || { startTime: '', endTime: '' }
        // 镇痛药执行医嘱与评分同窗口对照；失败不阻塞主块（独立提示）
        this.loadAnalgesia(patientId, options)
        this.nrs = data.nrs || EMPTY_BLOCK()
        this.vas = data.vas || EMPTY_BLOCK()
        this.cpot = data.cpot || EMPTY_BLOCK()
        this.rass = data.rass || EMPTY_BLOCK()
        this.cam = data.cam || this.cam
        // 写入全局患者上下文，侧边栏切到其他单患者页时患者不丢
        setCurrentPatient(data.patient)
        // 画图与模板重渲染单独隔离：即使某个量表图 echarts 渲染抛错，
        // 已返回的统计卡/明细数据仍正常展示，不再整页白屏；同时打印完整堆栈便于定位。
        try {
          await this.$nextTick()
          this.renderAllCharts()
        } catch (chartErr) {
          console.error('[SedationMonitor] 图表渲染失败（数据已正常加载）', chartErr)
        }
      } catch (e) {
        console.error('[SedationMonitor] loadData failed', e)
        this.loadError = e.message || '网络异常'
      } finally {
        this.loading = false
      }
    },
    goBack() {
      this.$router.push('/page/patient-workbench')
    },
    /** 镇痛药执行医嘱（窗口与评分一致）；静默失败，仅区块内提示 */
    async loadAnalgesia(patientId, options) {
      this.analgesiaError = ''
      try {
        const list = await fetchAnalgesia(patientId, options)
        this.analgesia = Array.isArray(list) ? list : []
      } catch (e) {
        this.analgesia = []
        this.analgesiaError = e.message || '镇痛药查询失败'
      }
    },
    /** 统一时间区间条：快捷档回退 hours，自定义走 start/end（后端要 ISO 'T'） */
    onRangeChange(p) {
      if (p.preset) {
        this.hours = p.hours || 24
        this.customRange = [null, null]
      } else if (p.start && p.end) {
        this.hours = null
        this.customRange = [p.start.replace(' ', 'T'), p.end.replace(' ', 'T')]
      } else {
        this.hours = 24
        this.customRange = [null, null]
      }
      this.loadData()
    },
    handleResize() {
      Object.values(this.charts).forEach((c) => { if (c) c.resize() })
    },

    // ---------------- 图表 ----------------
    hasPlot(key) {
      const block = this[key]
      if (!block || block.dataStatus === 'UNKNOWN') return false
      return (block.points || []).some((p) => p.score != null)
    },
    renderAllCharts() {
      ;['nrs', 'vas', 'cpot', 'rass'].forEach((k) => {
        try {
          this.renderScale(k)
        } catch (e) {
          console.error('[SedationMonitor] renderScale ' + k + ' failed', e)
        }
      })
    },
    renderScale(key) {
      const block = this[key]
      const el = this.$refs[key + 'Chart']
      if (!el || !block) return
      const pts = (block.points || []).filter((p) => p.score != null)
      if (!pts.length) return
      const meta = SCALE_META[key]
      // 同 NeuroMonitor.getChart：charts[key] 里的实例可能绑在已被替换的旧节点上
      // （切换时间范围会重建图表区 DOM），对旧实例 setOption 会在 echarts 内部抛
      //「reading 'getAxis'」，结果是坐标轴与 tooltip 停留在旧数据、悬浮无反应。
      // 以 getInstanceByDom 的结果为准，节点换了就重新 init 并 dispose 旧实例。
      const attached = echarts.getInstanceByDom(el)
      const chart = attached || echarts.init(el)
      this.charts[key] = chart
      // clear() 必须在 setOption 之前：切换时间范围时数据没变、DOM 也没重建，
      // 但上一轮 setOption 留下的 axis/series 关联会坏掉，导致本次渲染抛
      //「Cannot read properties of undefined (reading 'getAxis')」/「reading 'type'」
      // —— 表现为坐标轴和 tooltip 停留在旧数据、鼠标悬浮无反应。
      // clear() 后再 setOption，等价于 notMerge 但不触发组件重建的内部问题。
      chart.clear()
      const seriesData = pts.map((p, idx) => ({
        value: [this.toDate(p.time), p.score],
        point: p,
        symbolSize: idx === pts.length - 1 ? 8 : 5
      }))
      chart.setOption({
        backgroundColor: 'transparent',
        animationDuration: 300,
        grid: { left: 34, right: 14, top: 14, bottom: 22 },
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
            const level = pt.levelText ? `<br/>分级：<b>${pt.levelText}</b>` : ''
            const staff = pt.staffName ? `<br/>评估人：${pt.staffName}` : ''
            return `${this.fmtDateTime(pt.time)}<br/>${meta.name}：<b>${pt.score}</b>${level}${staff}`
          }
        },
        xAxis: {
          type: 'time',
          // hideOverlap + interval:auto：窗口拉长（如近7天/30天而数据只有入科这两天）时
          // 标签会挤成一团糊住图表轴，让 echarts 自动稀疏 / 隐藏重叠标签
          axisLabel: { color: '#a8a29e', fontSize: 10, hideOverlap: true, interval: 'auto', formatter: (v) => this.fmtShort(v) },
          axisLine: { lineStyle: { color: '#e7e5e4' } },
          splitLine: { show: false },
          axisTick: { show: false }
        },
        yAxis: {
          type: 'value',
          min: meta.min,
          max: meta.max,
          interval: key === 'rass' ? 1 : null,
          axisLabel: { color: '#a8a29e', fontSize: 10 },
          splitLine: { lineStyle: { color: '#f5f5f4' } }
        },
        series: [{
          type: 'line',
          data: seriesData,
          smooth: false,
          connectNulls: false,
          symbol: 'circle',
          lineStyle: { width: 2, color: meta.color },
          itemStyle: { color: meta.color },
          markLine: key === 'rass' ? {
            silent: true,
            symbol: 'none',
            data: [{ yAxis: 0, lineStyle: { color: '#d6d3d1', type: 'dashed', width: 1 } }]
          } : undefined
        }]
      })
      chart.resize()
    },

    // ---------------- 明细文案 ----------------
    numDetail(scale, p) {
      const audited = p.audited ? '已审核' : '未审核'
      if (scale === 'cpot' && p.dimensions) {
        const d = p.dimensions
        const parts = []
        const ventLabel = p.ventilated ? '通气顺应性' : '发声反应'
        if (d.face != null) parts.push(`面部${d.face}`)
        if (d.movement != null) parts.push(`体动${d.movement}`)
        if (d.muscle != null) parts.push(`肌紧张${d.muscle}`)
        if (d.ventOrVocal != null) parts.push(`${ventLabel}${d.ventOrVocal}`)
        if (d.item60 != null) parts.push(`选项码${d.item60}`)
        return parts.join('，') + `（${audited}）`
      }
      return audited
    },
    camDetail(c) {
      const parts = []
      if (c.hint) parts.push(c.hint)
      if (c.pairedRass != null) parts.push(`配对RASS ${c.pairedRass}（${this.fmtShort(c.pairedRassTime)}）`)
      else parts.push('配对RASS缺失')
      parts.push(c.audited ? '已审核' : '未审核')
      return parts.join('；')
    },

    // ---------------- 状态与标签 ----------------
    latestScore(block) {
      return block && block.latest && block.latest.score != null ? block.latest.score : '—'
    },
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
    camTagText(s) {
      return ({
        POSITIVE: '谵妄阳性',
        NEGATIVE: '阴性',
        UTA_SUSPECT: '建议核查·深镇静',
        PAIR_UNKNOWN: '配对RASS缺失',
        UNPARSED: '无法识别'
      })[s] || '未识别'
    },
    camTagClass(s) {
      if (s === 'POSITIVE') return 'cam-positive'
      if (s === 'NEGATIVE') return 'cam-negative'
      if (s === 'UTA_SUSPECT') return 'cam-warning'
      return 'cam-muted'
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
.sedation-page {
  padding: 16px 20px 28px;
  min-height: 100vh;
  background: #fafaf9;
}

/* 顶部 */
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

/* 卡片通用 */
.scale-card {
  background: #fff;
  border: 1px solid #e7e5e4;
  border-radius: 12px;
  padding: 14px 16px;
  box-shadow: 0 1px 2px rgba(28, 25, 23, 0.03);
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

/* 三量表栅格 */
.scale-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin-bottom: 12px;
}
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
.chart-empty {
  height: 150px; display: flex; flex-direction: column; align-items: center; justify-content: center;
  gap: 6px; font-size: 12px; color: #a8a29e; background: #fafaf9;
  border: 1px dashed #e7e5e4; border-radius: 8px;
}
.chart-empty.is-error { color: #b91c1c; background: #fef2f2; border-color: #fecaca; }
.partial-hint { font-size: 11px; color: #b45309; margin-top: 6px; }
.scale-note { font-size: 11px; color: #a8a29e; line-height: 1.5; margin-top: 8px; border-top: 1px solid #f5f5f4; padding-top: 8px; }

/* CAM */
.cam-card { margin-bottom: 12px; }
.cam-summary { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 10px; }
.cam-chip {
  font-size: 12px; padding: 4px 11px; border-radius: 999px; border: 1px solid #e7e5e4;
  background: #fafaf9; color: #57534e;
}
.cam-chip b { font-weight: 700; margin-left: 2px; }
.cam-chip.total { background: #fff7ed; border-color: #fed7aa; color: #c2410c; }
.cam-chip.positive { background: #fcebeb; border-color: #f5b4b4; color: #b91c1c; }
.cam-chip.negative { background: #f0f9f3; border-color: #b8e3c6; color: #15803d; }
.cam-chip.warning { background: #fef5e4; border-color: #fbd9a8; color: #b45309; }
.cam-chip.info { background: #f5f5f4; border-color: #e7e5e4; color: #78716c; }
.cam-events { max-height: 218px; overflow-y: auto; display: flex; flex-direction: column; gap: 6px; }
.cam-event {
  display: flex; align-items: center; gap: 10px; flex-wrap: wrap;
  font-size: 12px; color: #57534e; padding: 7px 10px;
  background: #fafaf9; border: 1px solid #f0eeec; border-radius: 8px;
}
.cam-time { color: #44403c; font-variant-numeric: tabular-nums; min-width: 128px; }
.cam-raw { color: #44403c; }
.cam-pair { color: #78716c; }
.cam-hint { color: #b45309; }
.cam-staff { margin-left: auto; color: #a8a29e; }
.cam-tag { font-size: 11px; padding: 2px 9px; border-radius: 999px; white-space: nowrap; }
.cam-positive { background: #fcebeb; color: #b91c1c; }
.cam-negative { background: #f0f9f3; color: #15803d; }
.cam-warning { background: #fef5e4; color: #b45309; }
.cam-muted { background: #f5f5f4; color: #78716c; }

/* 明细表 */
.detail-card .detail-table { width: 100%; }

/* 不显眼的细滚动条 */
.nice-scroll { scrollbar-width: thin; scrollbar-color: #d6d3d1 transparent; }
.nice-scroll::-webkit-scrollbar { width: 6px; height: 6px; }
.nice-scroll::-webkit-scrollbar-thumb { background: #d6d3d1; border-radius: 3px; }
.nice-scroll::-webkit-scrollbar-track { background: transparent; }

.load-error { padding: 60px 0; }

/* 响应式：窄屏堆叠 */
@media (max-width: 1100px) {
  .scale-grid { grid-template-columns: 1fr; }
}
</style>
