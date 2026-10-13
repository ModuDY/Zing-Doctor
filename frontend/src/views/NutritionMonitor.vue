<template>
  <div class="nutrition-page qb-theme">
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
            <span class="meta-item" v-if="patient.departName">{{ patient.departName }}</span>
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

      <template v-if="patient && !loadError">
        <div class="window-bar">
          <span>数据窗口：{{ fmtDateTime(window.startTime) }} 至 {{ fmtDateTime(window.endTime) }}</span>
        </div>

        <!-- 汇总 -->
        <div class="summary-grid">
          <div class="sum-card primary">
            <div class="sum-label">估算总能量</div>
            <div class="sum-value">{{ fmtNum(totals.totalKcal) }}<span class="unit"> kcal</span></div>
          </div>
          <div class="sum-card">
            <div class="sum-label">估算总蛋白质</div>
            <div class="sum-value">{{ fmtNum(totals.totalProteinG) }}<span class="unit"> g</span></div>
          </div>
          <div class="sum-card enteral">
            <div class="sum-label">肠内（{{ fmtNum(totals.enteralMl) }} ml）</div>
            <div class="sum-value">{{ fmtNum(totals.enteralKcal) }}<span class="unit"> kcal</span></div>
            <div class="sum-sub">蛋白 {{ fmtNum(totals.enteralProteinG) }} g</div>
          </div>
          <div class="sum-card parenteral">
            <div class="sum-label">肠外（{{ fmtNum(totals.parenteralMl) }} ml）</div>
            <div class="sum-value">{{ fmtNum(totals.parenteralKcal) }}<span class="unit"> kcal</span></div>
            <div class="sum-sub">蛋白 {{ fmtNum(totals.parenteralProteinG) }} g</div>
          </div>
        </div>

        <!-- 状态提示 -->
        <div v-if="dataStatus === 'UNKNOWN'" class="state-banner error">
          数据暂不可用 <el-button link type="primary" @click="loadData">点击重试</el-button>
        </div>
        <div v-else-if="dataStatus === 'NOT_RECORDED'" class="state-banner">
          窗口内未识别到营养制剂医嘱
        </div>

        <!-- 明细表 -->
        <div v-if="orders.length" class="table-card">
          <el-table :data="orders" size="small" stripe>
            <el-table-column label="制剂" min-width="180">
              <template #default="s">
                <div class="ord-name">{{ s.row.formulaName }}</div>
                <div class="ord-method" v-if="s.row.methodName">{{ s.row.methodName }}</div>
              </template>
            </el-table-column>
            <el-table-column label="途径" width="90">
              <template #default="s">
                <span :class="['route-pill', s.row.routeType === 'ENTERAL' ? 'ent' : 'par']">
                  {{ s.row.routeType === 'ENTERAL' ? '肠内' : '肠外' }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="给入 ml" width="100" align="right">
              <template #default="s">{{ fmtNum(s.row.givenMl) }}</template>
            </el-table-column>
            <el-table-column label="能量 kcal" width="100" align="right">
              <template #default="s">{{ fmtNum(s.row.kcal) }}</template>
            </el-table-column>
            <el-table-column label="蛋白 g" width="90" align="right">
              <template #default="s">{{ fmtNum(s.row.proteinG) }}</template>
            </el-table-column>
            <el-table-column label="执行时间" min-width="150">
              <template #default="s">
                <div class="ord-time">{{ fmtShort(s.row.startTime) }}</div>
                <div class="ord-method" v-if="s.row.bagVolume">袋规 {{ fmtNum(s.row.bagVolume) }} ml</div>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <div class="note-box" v-if="note">{{ note }}</div>
      </template>
    </div>
  </div>
</template>

<script>
import { ArrowLeft, Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { fetchNutritionMonitor } from '../api/workbench'
import { setCurrentPatient } from '../utils/patientContext'

export default {
  name: 'NutritionMonitor',
  components: { ArrowLeft, Refresh },
  data() {
    return {
      loading: false,
      loadError: '',
      patient: null,
      window: { startTime: '', endTime: '' },
      hours: 24,
      customRange: [null, null],
      defaultTime: new Date(2000, 0, 1, 0, 0, 0),
      totals: {
        enteralMl: 0, enteralKcal: 0, enteralProteinG: 0,
        parenteralMl: 0, parenteralKcal: 0, parenteralProteinG: 0,
        totalKcal: 0, totalProteinG: 0
      },
      orders: [],
      dataStatus: '',
      note: ''
    }
  },
  mounted() {
    this.loadData()
  },
  methods: {
    fmtNum(v) {
      if (v == null || v === '') return '—'
      const n = Number(v)
      if (Number.isNaN(n)) return '—'
      return n.toLocaleString('zh-CN', { maximumFractionDigits: 1 })
    },
    fmtDateTime(s) {
      if (!s) return '—'
      return String(s).replace('T', ' ').slice(0, 16)
    },
    fmtShort(s) {
      if (!s) return '—'
      return String(s).replace('T', ' ').slice(5, 16)
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
        const data = await fetchNutritionMonitor(patientId, options)
        if (!data || !data.header) {
          this.loadError = '患者不存在或已出科'
          return
        }
        this.patient = data.header
        this.window = data.window || { startTime: '', endTime: '' }
        this.totals = data.totals || this.totals
        this.orders = data.orders || []
        this.dataStatus = data.dataStatus || ''
        this.note = data.note || ''
        setCurrentPatient(data.header)
      } catch (e) {
        console.error('[NutritionMonitor] loadData failed', e)
        this.loadError = e.message || '网络异常'
      } finally {
        this.loading = false
      }
    }
  }
}
</script>

<style scoped>
.nutrition-page { padding: 16px 20px; }
.page-header { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; flex-wrap: wrap; }
.back-btn { padding: 0; }
.patient-name { font-size: 22px; margin: 4px 0 6px; color: #292524; }
.patient-meta { display: flex; gap: 10px; align-items: center; font-size: 12px; color: #78716c; }
.meta-pill.bed { background: #ffedd5; color: #c2410c; border-radius: 10px; padding: 1px 8px; }
.header-right { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
.range-picker { width: 320px; }
.window-bar { margin: 12px 0; font-size: 12px; color: #78716c; }
.summary-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; margin-bottom: 14px; }
.sum-card { background: #fff; border: 1px solid #e7e5e4; border-radius: 10px; padding: 14px 16px; }
.sum-card.primary { background: #fff7ed; border-color: #fed7aa; }
.sum-label { font-size: 12px; color: #78716c; }
.sum-value { font-size: 26px; font-weight: 600; color: #292524; margin-top: 4px; }
.sum-value .unit { font-size: 13px; font-weight: 400; color: #a8a29e; }
.sum-sub { font-size: 12px; color: #78716c; margin-top: 2px; }
.state-banner { background: #fafaf9; border: 1px dashed #d6d3d1; border-radius: 8px; padding: 10px 14px; color: #78716c; margin-bottom: 12px; font-size: 13px; }
.state-banner.error { background: #fef2f2; border-color: #fecaca; color: #b91c1c; }
.table-card { background: #fff; border: 1px solid #e7e5e4; border-radius: 10px; padding: 4px 8px; }
.ord-name { font-size: 13px; color: #292524; }
.ord-method, .ord-time { font-size: 11px; color: #a8a29e; margin-top: 2px; }
.route-pill { border-radius: 10px; padding: 1px 8px; font-size: 11px; }
.route-pill.ent { background: #dcfce7; color: #15803d; }
.route-pill.par { background: #ede9fe; color: #6d28d9; }
.note-box { margin-top: 12px; font-size: 12px; color: #a8a29e; line-height: 1.6; }
.load-error { padding: 30px 0; }
</style>
