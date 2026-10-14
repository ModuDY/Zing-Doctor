<template>
  <div class="page">
    <section class="head">
      <button class="back" @click="$router.back()">← 返回</button>
      <div class="head-main">
        <h2>生命体征趋势</h2>
        <p class="sub">
          {{ patientName }} {{ bedNo ? bedNo + '床' : '' }} · {{ inHospitalNo }}
          <span class="dot">·</span>{{ rangeText }}
        </p>
      </div>
      <div class="head-right">
        <el-radio-group v-model="hours" size="small" @change="load">
          <el-radio-button :label="6">近 6 小时</el-radio-button>
          <el-radio-button :label="24">近 24 小时</el-radio-button>
          <el-radio-button :label="72">近 3 天</el-radio-button>
          <el-radio-button :label="168">近 7 天</el-radio-button>
        </el-radio-group>
        <el-button size="small" :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      </div>
    </section>

    <p class="hint">
      <el-icon><InfoFilled /></el-icon>
      同一时间轴上下分面，鼠标指到任意时刻，所有指标同时显示数值。仅展示监测记录，不自动诊断。
    </p>

    <div v-loading="loading">
      <MultiTrendChart v-if="hasAvail" :series="availSeries" />

      <!-- 非 AVAILABLE 的指标单独列状态，不塞进图里 -->
      <div v-if="nonAvail.length" class="status-strip">
        <span v-for="s in nonAvail" :key="s.key" class="status-tag"
              :class="s.dataStatus === 'UNKNOWN' ? 'bad' : 'muted'">
          {{ s.name }}：{{ s.dataStatus === 'UNKNOWN' ? '查询失败' : '窗口内无记录' }}
        </span>
      </div>
    </div>

    <section class="table-block">
      <div class="block-head">
        <span class="block-title">原始记录</span>
        <span class="block-note">共 {{ recordCount || 0 }} 条（时间 / 项目 / 值 / 记录人）</span>
      </div>
      <el-table :data="records" size="small" max-height="380" empty-text="窗口内无记录">
        <el-table-column prop="time" label="时间" width="170" />
        <el-table-column prop="itemName" label="项目" min-width="120" />
        <el-table-column prop="value" label="值" width="90" />
        <el-table-column prop="itemCode" label="编码" width="110" />
        <el-table-column prop="staffName" label="记录人" min-width="90" />
      </el-table>
    </section>
  </div>
</template>

<script>
import { Refresh, InfoFilled } from '@element-plus/icons-vue'
import MultiTrendChart from '../components/MultiTrendChart.vue'
import { fetchVitals } from '../api/workbench'

export default {
  name: 'VitalSigns',
  components: { Refresh, InfoFilled, MultiTrendChart },
  data() {
    return {
      hours: 24,
      loading: false,
      patientName: '',
      bedNo: '',
      inHospitalNo: '',
      rangeStart: '',
      rangeEnd: '',
      series: [],
      records: [],
      recordCount: 0
    }
  },
  computed: {
    patientId() { return this.$route.query.patientId },
    availSeries() {
      return this.series.filter((s) => s.dataStatus === 'AVAILABLE' && s.points && s.points.length)
    },
    hasAvail() { return this.availSeries.length > 0 },
    nonAvail() {
      return this.series.filter((s) => s.dataStatus !== 'AVAILABLE')
    },
    rangeText() {
      if (!this.rangeStart) return ''
      const fmt = (t) => (t || '').replace('T', ' ').slice(5, 16)
      return fmt(this.rangeStart) + ' ~ ' + fmt(this.rangeEnd)
    }
  },
  created() { this.load() },
  methods: {
    async load() {
      if (!this.patientId) {
        this.$message.error('缺少 patientId 参数，请从患者工作台进入')
        return
      }
      this.loading = true
      try {
        const d = await fetchVitals(this.patientId, this.hours)
        this.patientName = d.patientName || ''
        this.bedNo = d.bedNo || ''
        this.inHospitalNo = d.inHospitalNo || ''
        this.rangeStart = d.rangeStart
        this.rangeEnd = d.rangeEnd
        this.series = d.series || []
        this.records = d.records || []
        this.recordCount = d.recordCount || 0
      } catch (e) {
        this.$message.error('生命体征加载失败：' + ((e && e.message) || '请稍后重试'))
      } finally {
        this.loading = false
      }
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
.hint { display: flex; align-items: center; gap: 5px; margin: 0 0 12px; font-size: 11px; color: #a8a29e; }
.status-strip { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 12px; }
.status-tag { font-size: 11px; padding: 3px 10px; border-radius: 999px; background: #f5f5f4; color: #78716c; }
.status-tag.bad { background: #fef2f2; color: #b91c1c; }
.table-block { margin-top: 14px; background: #fff; border: 1px solid #eeece8; border-radius: 10px; padding: 12px 14px; }
.block-head { display: flex; align-items: baseline; gap: 10px; margin-bottom: 8px; }
.block-title { font-size: 13px; font-weight: 600; color: #1c1917; }
.block-note { font-size: 11px; color: #a8a29e; }
</style>
