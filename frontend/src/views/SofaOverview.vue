<template>
  <div class="sofa-ov">
    <div class="head">
      <h2>SOFA 评分总览</h2>
      <div class="filters">
        <input class="tb-input" v-model="departCode" placeholder="科室编码（留空=全部）" />
        <input class="tb-input" type="datetime-local" v-model="rangeStart" />
        <span class="sep">~</span>
        <input class="tb-input" type="datetime-local" v-model="rangeEnd" />
        <button class="btn" @click="quickRange(7)">近7天</button>
        <button class="btn" @click="quickRange(30)">近30天</button>
        <button class="btn btn-primary" @click="load">查询</button>
      </div>
    </div>

    <div class="stat-grid">
      <div class="stat-card">
        <div class="stat-label">评分记录数</div>
        <div class="stat-value">{{ summary.totalCount || 0 }}</div>
        <div class="stat-sub">当前时间范围</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">平均总分</div>
        <div class="stat-value">{{ summary.avgScore || 0 }}</div>
        <div class="stat-sub">SOFA 总分（0~24）</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">高分患者数</div>
        <div class="stat-value">{{ summary.highRiskCount || 0 }}</div>
        <div class="stat-sub">总分 ≥ 10 分</div>
      </div>
      <div class="stat-card warn">
        <div class="stat-label">ΔSOFA 恶化预警</div>
        <div class="stat-value">{{ summary.worsenedCount || 0 }}</div>
        <div class="stat-sub">较上次升高 ≥ 2 分</div>
      </div>
    </div>

    <div class="panel">
      <div class="panel-title">总分分布</div>
      <div class="dist">
        <div class="dist-item" v-for="(v, k) in summary.scoreDistribution" :key="k">
          <div class="dist-key">{{ k }}</div>
          <div class="dist-bar"><span :style="{ width: barWidth(v) }"></span></div>
          <div class="dist-val">{{ v }}</div>
        </div>
      </div>
    </div>

    <div class="panel" v-if="summary.worsenedList && summary.worsenedList.length">
      <div class="panel-title">ΔSOFA 恶化患者（较上次升高 ≥ 2，提示器官功能恶化）</div>
      <table class="tbl">
        <thead><tr><th>住院号</th><th>姓名</th><th>床号</th><th>总分</th><th>ΔSOFA</th><th>评分时间</th></tr></thead>
        <tbody>
          <tr v-for="r in summary.worsenedList" :key="r.id">
            <td>{{ r.inHospitalNo }}</td>
            <td>{{ r.patientName || '—' }}</td>
            <td>{{ r.departCode || '—' }}</td>
            <td><b class="up">{{ r.totalScore }}</b></td>
            <td class="up">+{{ r.deltaSofa }}</td>
            <td>{{ fmtTime(r.scoreTime) }}</td>
          </tr>
        </tbody>
      </table>
    </div>

    <div class="panel">
      <div class="panel-title">评分记录<span class="count">{{ (summary.records || []).length }}</span></div>
      <table class="tbl" v-if="(summary.records || []).length">
        <thead>
          <tr><th>评分时间</th><th>住院号</th><th>姓名</th><th>总分</th><th>呼吸</th><th>凝血</th><th>肝</th><th>循环</th><th>神经</th><th>肾</th><th>Δ</th></tr>
        </thead>
        <tbody>
          <tr v-for="r in summary.records" :key="r.id">
            <td>{{ fmtTime(r.scoreTime) }}</td>
            <td>{{ r.inHospitalNo }}</td>
            <td>{{ r.patientName || '—' }}</td>
            <td><b :class="cls(r.totalScore)">{{ r.totalScore }}</b></td>
            <td>{{ r.respScore }}</td>
            <td>{{ r.coagScore }}</td>
            <td>{{ r.liverScore }}</td>
            <td>{{ r.cardioScore }}</td>
            <td>{{ r.neuroScore }}</td>
            <td>{{ r.renalScore }}</td>
            <td :class="r.deltaSofa > 0 ? 'up' : (r.deltaSofa < 0 ? 'down' : '')">
              {{ r.deltaSofa === null || r.deltaSofa === undefined ? '—' : (r.deltaSofa > 0 ? '+' : '') + r.deltaSofa }}
            </td>
          </tr>
        </tbody>
      </table>
      <div v-else class="empty">暂无评分记录</div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { fetchSofaOverview } from '../api/sofa'

const route = useRoute()
const departCode = ref(route.query.departCode || '')
const rangeStart = ref('')
const rangeEnd = ref('')
const summary = reactive({ totalCount: 0, avgScore: 0, highRiskCount: 0, worsenedCount: 0, worsenedList: [], scoreDistribution: {}, records: [] })

onMounted(() => {
  quickRange(7)
})

function toLocalInput(d) {
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}`
}

function toBackend(v) {
  if (!v) return ''
  return v.replace('T', ' ') + (v.length === 16 ? ':00' : '')
}

function quickRange(days) {
  const now = new Date()
  rangeEnd.value = toLocalInput(now)
  rangeStart.value = toLocalInput(new Date(now.getTime() - days * 24 * 3600 * 1000))
  load()
}

function fmtTime(t) {
  if (!t) return '—'
  let s = String(t)
  if (s.includes('T')) s = s.replace('T', ' ')
  return s.length > 16 ? s.slice(0, 16) : s
}

function cls(s) {
  if (s >= 10) return 'up'
  if (s >= 6) return 'mid'
  return 'low'
}

function barWidth(v) {
  const list = Object.values(summary.scoreDistribution || {})
  const max = Math.max(1, ...list)
  return Math.round(((v || 0) / max) * 100) + '%'
}

async function load() {
  try {
    const res = await fetchSofaOverview(departCode.value, toBackend(rangeStart.value), toBackend(rangeEnd.value))
    if (!res) return
    summary.totalCount = res.totalCount || 0
    summary.avgScore = res.avgScore || 0
    summary.highRiskCount = res.highRiskCount || 0
    summary.worsenedCount = res.worsenedCount || 0
    summary.worsenedList = res.worsenedList || []
    summary.scoreDistribution = res.scoreDistribution || {}
    summary.records = res.records || []
  } catch (e) {
    console.warn('总览查询失败：', e.message || e)
  }
}
</script>

<style scoped>
.sofa-ov { padding: 16px 20px 40px; background: #f5f7fa; min-height: 100vh; }
.head { display: flex; align-items: center; gap: 16px; flex-wrap: wrap; margin-bottom: 14px; }
.head h2 { margin: 0; font-size: 18px; color: #303133; }
.filters { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-left: auto; }
.tb-input { height: 30px; padding: 0 8px; border: 1px solid #dcdfe6; border-radius: 4px; font-size: 13px; }
.sep { color: #909399; }
.btn { height: 30px; padding: 0 12px; border: 1px solid #dcdfe6; background: #fff; border-radius: 4px; font-size: 13px; cursor: pointer; }
.btn-primary { background: #409eff; border-color: #409eff; color: #fff; }
.stat-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px; margin-bottom: 16px; }
.stat-card { background: #fff; border-radius: 8px; padding: 14px 18px; box-shadow: 0 1px 4px rgba(0,0,0,.04); }
.stat-card.warn { border-left: 4px solid #f56c6c; }
.stat-label { font-size: 12px; color: #909399; }
.stat-value { font-size: 30px; font-weight: 700; color: #303133; }
.stat-sub { font-size: 12px; color: #c0c4cc; }
.panel { background: #fff; border-radius: 8px; padding: 14px 16px; margin-bottom: 16px; }
.panel-title { font-weight: 600; font-size: 14px; color: #303133; margin-bottom: 10px; display: flex; align-items: center; gap: 8px; }
.count { background: #ecf5ff; color: #409eff; padding: 1px 8px; border-radius: 10px; font-size: 12px; font-weight: 400; }
.dist-item { display: flex; align-items: center; gap: 10px; margin-bottom: 6px; font-size: 13px; }
.dist-key { width: 60px; color: #606266; }
.dist-bar { flex: 1; height: 14px; background: #f4f4f5; border-radius: 7px; overflow: hidden; }
.dist-bar span { display: block; height: 100%; background: linear-gradient(90deg, #66b1ff, #409eff); }
.dist-val { width: 40px; text-align: right; color: #303133; font-weight: 600; }
.tbl { width: 100%; border-collapse: collapse; font-size: 13px; }
.tbl th, .tbl td { padding: 8px 10px; border-bottom: 1px solid #ebeef5; text-align: left; }
.tbl th { background: #f5f7fa; color: #606266; font-weight: 500; }
.empty { text-align: center; color: #c0c4cc; padding: 24px 0; font-size: 13px; }
.up { color: #f56c6c; }
.down { color: #67c23a; }
.mid { color: #e6a23c; }
.low { color: #67c23a; }
</style>
