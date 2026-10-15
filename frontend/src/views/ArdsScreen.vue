<template>
  <div class="ards-screen qb-theme">
    <section class="filter-bar">
      <span class="title">ARDS 全院筛查</span>
      <el-date-picker
        v-model="range"
        type="daterange"
        value-format="YYYY-MM-DD"
        range-separator="~"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
        :clearable="false"
        popper-class="qb-popper"
        @change="load"
      />
      <el-input
        v-model="keyword"
        placeholder="姓名 / 住院号 / 科室过滤"
        clearable
        style="width: 220px"
      />
      <el-button :loading="loading" @click="load">查询</el-button>
      <span class="hint">筛查口径：本周期内「在院或已出科」且诊断含「急性呼吸窘迫综合征 / ARDS」的患者，不限科室清单</span>
    </section>

    <div v-loading="loading" class="page-body">
      <!-- 汇总：四个数字一眼读完 -->
      <div class="summary-row">
        <div class="sum-card"><b>{{ summary.total || 0 }}</b><span>筛查命中</span></div>
        <div class="sum-card"><b class="c-blue">{{ summary.in_department || 0 }}</b><span>在科</span></div>
        <div class="sum-card"><b>{{ summary.discharged || 0 }}</b><span>已出科</span></div>
        <div class="sum-card"><b>{{ summary.depart_count || 0 }}</b><span>涉及科室</span></div>
      </div>

      <el-table :data="filtered" size="small" border stripe>
        <el-table-column prop="patient_name" label="姓名" width="90" />
        <el-table-column prop="in_hospital_no" label="住院号" width="130" />
        <el-table-column prop="gender" label="性别" width="60" />
        <el-table-column prop="bed_code" label="床号" width="70" />
        <el-table-column label="科室" min-width="140">
          <template #default="{ row }">{{ row.ward_name || row.depart_code || '—' }}</template>
        </el-table-column>
        <el-table-column label="入科时间" width="150">
          <template #default="{ row }">{{ fmt(row.in_depart_time) }}</template>
        </el-table-column>
        <el-table-column label="出科时间" width="150">
          <template #default="{ row }">{{ row.out_depart_time ? fmt(row.out_depart_time) : '—' }}</template>
        </el-table-column>
        <el-table-column prop="stay_days" label="在科天数" width="90" align="center" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <span :class="row.in_department ? 'tag-in' : 'tag-out'">{{ row.in_department ? '在科' : '已出科' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openPatient(row)">患者页</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && !filtered.length" description="本周期内无 ARDS 诊断命中" />
      <p class="foot-note">
        诊断匹配 patient_info_diagnosis.diag_name（含「ARDS」或「急性呼吸窘迫综合征」）；
        上限 2000 条。ARDS 分级 / 肺保护通气达标分析在「ARDS 监测」页（按科室出科口径）。
      </p>
    </div>
  </div>
</template>

<script>
import request from '../api/request'

export default {
  name: 'ArdsScreen',
  data() {
    const today = new Date()
    const start = new Date(today.getTime() - 30 * 86400000)
    const iso = (d) => d.toISOString().slice(0, 10)
    return {
      range: [iso(start), iso(today)],
      keyword: '',
      loading: false,
      summary: {},
      patients: []
    }
  },
  computed: {
    filtered() {
      const k = (this.keyword || '').trim().toLowerCase()
      if (!k) return this.patients
      return this.patients.filter((p) =>
        [p.patient_name, p.in_hospital_no, p.ward_name, p.depart_code]
          .some((v) => v && String(v).toLowerCase().includes(k)))
    }
  },
  created() { this.load() },
  methods: {
    async load() {
      if (!this.range || !this.range[0] || !this.range[1]) return
      this.loading = true
      try {
        const res = await request.get('/ards/hospital-screen', {
          params: {
            startTime: this.range[0] + ' 00:00:00',
            endTime: this.range[1] + ' 23:59:59'
          },
          silentError: true
        })
        const d = res && res.summary ? res : (res && res.data) || {}
        this.summary = d.summary || {}
        this.patients = d.patients || []
      } catch (e) {
        console.error(e)
        this.summary = {}
        this.patients = []
      } finally {
        this.loading = false
      }
    },
    openPatient(row) {
      this.$router.push({ path: '/page/patient-workbench', query: { patientId: row.patient_id } })
    },
    fmt(t) { return (t || '').replace('T', ' ').slice(0, 16) }
  }
}
</script>

<style scoped>
.ards-screen { padding: 16px; }
.filter-bar { display: flex; align-items: center; gap: 12px; margin-bottom: 14px; flex-wrap: wrap; }
.title { font-size: 15px; font-weight: 700; color: #1c1917; }
.hint { color: #78716c; font-size: 12px; }
.summary-row { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 1px; background: #f0efee; border: 1px solid #e7e5e4; border-radius: 12px; overflow: hidden; margin-bottom: 14px; }
.sum-card { background: #fff; padding: 14px 18px; }
.sum-card b { display: block; font-size: 26px; font-weight: 700; color: #1c1917; font-variant-numeric: tabular-nums; }
.sum-card b.c-blue { color: #2563eb; }
.sum-card span { color: #78716c; font-size: 12px; }
.tag-in { font-size: 11px; padding: 1px 8px; border-radius: 10px; background: #eff6ff; color: #2563eb; }
.tag-out { font-size: 11px; padding: 1px 8px; border-radius: 10px; background: #f5f5f4; color: #78716c; }
.foot-note { color: #a8a29e; font-size: 12px; margin: 10px 2px 0; }
</style>
