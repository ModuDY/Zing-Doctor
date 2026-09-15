<template>
  <div class="pkpd-page abx-theme">
    <div v-if="!inHospitalNo && !patientId" class="warn-bar">
      <el-alert type="warning" :closable="false" show-icon
                title="缺少患者参数"
                description="本页面需通过外链（携带 patientId 或 ICU 外链 inHospitalNo）进入" />
    </div>

    <div v-else v-loading="loading" class="page-body">
      <!-- 患者信息横条 -->
      <div class="card patient-bar">
        <div class="p-cell"><label>姓名</label><b>{{ data.patient?.name || '—' }}</b></div>
        <div class="p-cell"><label>住院号</label><b>{{ data.patient?.patientNo || '—' }}</b></div>
        <div class="p-cell"><label>年龄 / 性别</label><b>{{ data.patient?.age || '—' }} / {{ data.patient?.gender || '—' }}</b></div>
        <div class="p-cell"><label>床位</label><b>{{ data.patient?.bedNo || '—' }}</b></div>
        <div class="p-cell-group" v-if="data.renal">
          <div class="p-cell">
            <label>肾功能分级</label>
            <el-tag :type="renalTagType(data.renal.renalStage)" size="small" effect="dark">{{ data.renal.renalStageText || '—' }}</el-tag>
          </div>
          <div class="p-cell">
            <label>CrCl</label>
            <b>{{ data.renal.crcl || '—' }} mL/min</b>
          </div>
        </div>
        <div class="p-cell-group" v-if="data.nutrition">
          <div class="p-cell">
            <label>体重</label>
            <b>{{ data.nutrition.weight || '—' }} kg</b>
          </div>
          <div class="p-cell">
            <label>BMI</label>
            <b :class="{ 'warn-text': data.nutrition.obese }">{{ data.nutrition.bmi || '—' }}</b>
          </div>
        </div>
        <div class="p-cell p-cell-grow">
          <label>特殊状态</label>
          <div class="flag-list" v-if="data.specialStatus && data.specialStatus.flags && data.specialStatus.flags.length">
            <el-tag v-for="(f, i) in data.specialStatus.flags" :key="i" size="small" effect="plain" type="warning" class="flag-tag">{{ f }}</el-tag>
          </div>
          <b v-else>—</b>
        </div>
      </div>

      <!-- 主体三栏 -->
      <div class="main-grid">
        <!-- 左栏：肾功能评估 + 营养状态 + 肝功能 -->
        <div class="col-left">
          <!-- 肾功能评估 -->
          <div class="card">
            <div class="block-title"><span class="dot dot-blue"></span>肾功能评估</div>
            <div v-if="data.renal" class="renal-content">
              <div class="renal-big">
                <div class="renal-num" :class="'renal-' + data.renal.renalStage">{{ data.renal.crcl || '—' }}</div>
                <div class="renal-unit">mL/min</div>
                <div class="renal-label">肌酐清除率（Cockcroft-Gault）</div>
              </div>
              <div class="renal-info-row">
                <div class="renal-info-item">
                  <span class="ri-label">eGFR</span>
                  <span class="ri-value">{{ data.renal.egfr || '—' }} mL/min/1.73m²</span>
                </div>
                <div class="renal-info-item">
                  <span class="ri-label">最新肌酐</span>
                  <span class="ri-value">{{ data.renal.latestCreatinine || '—' }} μmol/L</span>
                </div>
                <div class="renal-info-item">
                  <span class="ri-label">KDIGO 分级</span>
                  <span class="ri-value">{{ data.renal.renalStageText || '—' }}</span>
                </div>
              </div>
              <!-- 肌酐趋势图 -->
              <div class="renal-chart" ref="creatinineChart" v-if="data.renal.creatinineTrend && data.renal.creatinineTrend.length"></div>
              <div class="empty-chart" v-else>暂无肌酐趋势数据</div>
            </div>
            <el-empty v-else description="暂无肾功能数据" :image-size="60" />
          </div>

          <!-- 营养状态 -->
          <div class="card">
            <div class="block-title"><span class="dot dot-green"></span>营养状态</div>
            <div v-if="data.nutrition" class="nutrition-content">
              <div class="nutrition-grid">
                <div class="nutrition-item">
                  <span class="ni-label">实际体重</span>
                  <span class="ni-value">{{ data.nutrition.weight || '—' }} kg</span>
                </div>
                <div class="nutrition-item">
                  <span class="ni-label">理想体重(IBW)</span>
                  <span class="ni-value">{{ data.nutrition.ibw || '—' }} kg</span>
                </div>
                <div class="nutrition-item">
                  <span class="ni-label">调整体重(AdjBW)</span>
                  <span class="ni-value">{{ data.nutrition.adjbw || '—' }} kg</span>
                </div>
                <div class="nutrition-item">
                  <span class="ni-label">BMI</span>
                  <span class="ni-value" :class="{ 'warn-text': data.nutrition.obese }">{{ data.nutrition.bmi || '—' }}</span>
                </div>
              </div>
              <div class="obese-alert" v-if="data.nutrition.obese">
                <el-icon><Warning /></el-icon>
                <span>肥胖患者（BMI {{ data.nutrition.bmi }}），氨基糖苷类/万古霉素等建议按调整体重计算剂量</span>
              </div>
              <div class="low-weight-alert" v-if="data.nutrition.lowWeight">
                <el-icon><InfoFilled /></el-icon>
                <span>低体重患者（BMI {{ data.nutrition.bmi }}），需注意按实际体重计算，避免剂量不足</span>
              </div>
            </div>
            <el-empty v-else description="暂无营养数据" :image-size="60" />
          </div>

          <!-- 肝功能评估 -->
          <div class="card">
            <div class="block-title">
              <span class="dot dot-orange"></span>肝功能评估
              <el-tag v-if="data.liver && data.liver.abnormal" size="small" type="warning" style="margin-left:8px">异常</el-tag>
            </div>
            <div v-if="data.liver" class="liver-content">
              <div class="liver-grid">
                <div class="liver-item">
                  <span class="li-label">总胆红素</span>
                  <span class="li-value">{{ data.liver.totalBilirubin || '—' }} μmol/L</span>
                </div>
                <div class="liver-item">
                  <span class="li-label">直接胆红素</span>
                  <span class="li-value">{{ data.liver.directBilirubin || '—' }} μmol/L</span>
                </div>
                <div class="liver-item">
                  <span class="li-label">白蛋白</span>
                  <span class="li-value" :class="{ 'warn-text': data.liver.hypoalbuminemia }">{{ data.liver.albumin || '—' }} g/L</span>
                </div>
                <div class="liver-item">
                  <span class="li-label">INR</span>
                  <span class="li-value">{{ data.liver.inr || '—' }}</span>
                </div>
              </div>
              <div class="liver-note" v-if="data.liver.abnormalText">
                <el-icon><InfoFilled /></el-icon>
                <span>{{ data.liver.abnormalText }}</span>
              </div>
            </div>
            <el-empty v-else description="暂无肝功能数据" :image-size="60" />
          </div>
        </div>

        <!-- 中栏：当前抗菌药物 PK/PD 分析 -->
        <div class="col-mid">
          <div class="card">
            <div class="block-title">
              <span class="dot dot-cyan"></span>当前抗菌药物 PK/PD 分析
              <el-tag size="small" type="info" style="margin-left:8px">{{ data.drugAnalysis ? data.drugAnalysis.length : 0 }} 种</el-tag>
            </div>
            <div v-if="data.drugAnalysis && data.drugAnalysis.length" class="abx-pkpd-list">
              <div v-for="(drug, idx) in data.drugAnalysis" :key="idx" class="abx-pkpd-item">
                <div class="abx-pkpd-header">
                  <span class="abx-pkpd-name">{{ drug.drugName }}</span>
                  <el-tag :type="pkpdTypeTag(drug.pkpdType)" size="small" effect="dark">{{ drug.pkpdTypeText }}</el-tag>
                </div>
                <div class="abx-pkpd-detail">
                  <div class="abx-pkpd-row">
                    <span class="abx-pkpd-k">PK/PD 目标</span>
                    <span class="abx-pkpd-v">{{ drug.targetParam }} {{ drug.targetValue }}</span>
                  </div>
                  <div class="abx-pkpd-row">
                    <span class="abx-pkpd-k">当前方案</span>
                    <span class="abx-pkpd-v">{{ drug.currentInterval || '—' }} · {{ drug.infusionMethod || '—' }}</span>
                  </div>
                  <div class="abx-pkpd-row">
                    <span class="abx-pkpd-k">开始时间</span>
                    <span class="abx-pkpd-v">{{ drug.startTime || '—' }}</span>
                  </div>
                  <div class="abx-pkpd-row">
                    <span class="abx-pkpd-k">蛋白结合率</span>
                    <span class="abx-pkpd-v" :class="{ 'warn-text': drug.highProteinBinding }">
                      {{ drug.proteinBinding }}%{{ drug.highProteinBinding ? '（高蛋白结合率）' : '' }}
                    </span>
                  </div>
                  <div class="abx-pkpd-row">
                    <span class="abx-pkpd-k">清除途径</span>
                    <span class="abx-pkpd-v">{{ drug.clearanceRouteText }}</span>
                  </div>
                  <div class="abx-pkpd-row" v-if="drug.tdmRequired">
                    <span class="abx-pkpd-k">TDM</span>
                    <span class="abx-pkpd-v"><el-tag size="small" type="warning" effect="plain">需监测血药浓度</el-tag></span>
                  </div>
                  <div class="abx-pkpd-row" v-if="!drug.knowledgeMatched">
                    <span class="abx-pkpd-k">知识库</span>
                    <span class="abx-pkpd-v" style="color:#78716c">未匹配到药物知识库，仅展示医嘱信息</span>
                  </div>
                </div>
                <div class="abx-pkpd-remark" v-if="drug.remark">
                  <el-icon><InfoFilled /></el-icon>
                  <span>{{ drug.remark }}</span>
                </div>
              </div>
            </div>
            <el-empty v-else description="暂无当前抗菌药物" :image-size="60" />
          </div>
        </div>

        <!-- 右栏：剂量优化建议 + TDM 目标值 -->
        <div class="col-right">
          <!-- 剂量优化建议 -->
          <div class="card">
            <div class="block-title"><span class="dot dot-red"></span>剂量优化建议</div>
            <div v-if="data.recommendations && data.recommendations.length" class="rec-list">
              <div v-for="(rec, idx) in data.recommendations" :key="idx" class="rec-item">
                <div class="rec-header">
                  <span class="rec-name">{{ rec.drugName }}</span>
                  <el-tag :type="rec.needAdjustment ? 'danger' : 'success'" size="small" effect="dark">
                    {{ rec.needAdjustment ? '需调整' : '常规剂量' }}
                  </el-tag>
                </div>
                <div class="rec-dose" v-if="rec.recommendedDose">{{ rec.recommendedDose }}</div>
                <div class="rec-reason" v-if="rec.adjustmentReason">
                  <el-icon><InfoFilled /></el-icon>
                  <span>{{ rec.adjustmentReason }}</span>
                </div>
                <!-- 肾功能剂量调整参考表 -->
                <div class="renal-dose-table" v-if="rec.renalDoseTable && rec.renalDoseTable.length">
                  <div class="r-table-title">肾功能剂量参考</div>
                  <table class="r-table">
                    <thead>
                      <tr><th>CrCl (mL/min)</th><th>推荐剂量</th></tr>
                    </thead>
                    <tbody>
                      <tr v-for="(row, i) in rec.renalDoseTable" :key="i">
                        <td>{{ row.crclRange }}</td>
                        <td>{{ row.dose }}</td>
                      </tr>
                    </tbody>
                  </table>
                </div>
              </div>
            </div>
            <el-empty v-else description="暂无剂量建议" :image-size="60" />
          </div>

          <!-- TDM 目标值 -->
          <div class="card">
            <div class="block-title"><span class="dot dot-purple"></span>TDM 目标值指引</div>
            <div v-if="data.tdmTargets && data.tdmTargets.length" class="tdm-list">
              <div v-for="(tdm, idx) in data.tdmTargets" :key="idx" class="tdm-item">
                <div class="tdm-header">
                  <span class="tdm-name">{{ tdm.drugName }}</span>
                  <el-tag size="small" type="warning" effect="plain">需 TDM</el-tag>
                </div>
                <div class="tdm-targets">
                  <div class="tdm-target">
                    <span class="tdm-label">常规目标</span>
                    <span class="tdm-value">{{ tdm.standardTarget }}</span>
                  </div>
                  <div class="tdm-target" v-if="tdm.severeTarget">
                    <span class="tdm-label">严重感染</span>
                    <span class="tdm-value">{{ tdm.severeTarget }}</span>
                  </div>
                  <div class="tdm-target" v-if="tdm.toxicityThreshold">
                    <span class="tdm-label">毒性阈值</span>
                    <span class="tdm-value warn-text">{{ tdm.toxicityThreshold }}</span>
                  </div>
                </div>
                <div class="tdm-timing">
                  <el-icon><Clock /></el-icon>
                  <span>监测时机：{{ tdm.samplingNote || tdm.monitorTiming }}</span>
                </div>
              </div>
            </div>
            <div v-else class="tdm-empty">
              <el-icon><InfoFilled /></el-icon>
              <span>当前抗菌药物暂无需常规 TDM 监测（如治疗反应不佳或肾功能急剧变化，建议结合临床评估）</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 底部：药物相互作用提醒 -->
      <div class="card interaction-bar" v-if="data.interactionAlerts && data.interactionAlerts.length">
        <div class="block-title"><span class="dot dot-red"></span>药物相互作用与安全提醒</div>
        <div class="interaction-list">
          <div v-for="(alert, idx) in data.interactionAlerts" :key="idx" class="interaction-item">
            <el-icon><Warning /></el-icon>
            <span>{{ alert }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, nextTick, watch } from 'vue'
import { useRoute } from 'vue-router'
import { fetchPkpd, fetchPkpdByNo } from '../api/antibiotic'
import { Warning, InfoFilled, Clock } from '@element-plus/icons-vue'
import * as echarts from '../utils/echarts'
import '../styles/abx-theme.css'

const route = useRoute()
const loading = ref(true)
const creatinineChart = ref(null)
let chartInstance = null

const patientId = ref(route.query.patientId || route.params.patientId || '')
const inHospitalNo = ref(route.query.inHospitalNo || '')

const data = reactive({
  patient: null,
  renal: null,
  nutrition: null,
  liver: null,
  specialStatus: null,
  currentAbx: [],
  drugAnalysis: [],
  recommendations: [],
  tdmTargets: [],
  interactionAlerts: []
})

function loadData() {
  loading.value = true
  const pid = route.params.patientId || route.query.patientId
  const inNo = route.query.inHospitalNo
  const req = pid ? fetchPkpd(pid) : fetchPkpdByNo(inNo)
  req.then(res => {
    const d = res || {}
    Object.assign(data, d)
    loading.value = false
    nextTick(() => {
      renderCreatinineChart()
    })
  }).catch(() => {
    loading.value = false
  })
}

function renderCreatinineChart() {
  if (!creatinineChart.value || !data.renal || !data.renal.creatinineTrend || !data.renal.creatinineTrend.length) return
  if (chartInstance) chartInstance.dispose()
  chartInstance = echarts.init(creatinineChart.value)
  const trend = data.renal.creatinineTrend
  chartInstance.setOption({
    grid: { left: 45, right: 16, top: 16, bottom: 28 },
    tooltip: { trigger: 'axis', confine: true },
    xAxis: {
      type: 'category',
      data: trend.map(t => t.time),
      axisLabel: { color: '#78716c', fontSize: 10 }
    },
    yAxis: {
      type: 'value',
      name: 'μmol/L',
      nameTextStyle: { color: '#78716c', fontSize: 10 },
      axisLabel: { color: '#78716c', fontSize: 10 }
    },
    series: [{
      type: 'line',
      data: trend.map(t => t.value),
      smooth: true,
      symbol: 'circle',
      symbolSize: 6,
      itemStyle: { color: '#0d9488' },
      lineStyle: { width: 2, color: '#0d9488' },
      areaStyle: { color: 'rgba(13,148,136,0.12)' }
    }]
  })
}

function renalTagType(stage) {
  if (!stage) return 'info'
  if (stage === 'G1' || stage === 'G2') return 'success'
  if (stage === 'G3a' || stage === 'G3b') return 'warning'
  return 'danger'
}

function pkpdTypeTag(type) {
  if (type === 'concentration') return 'danger'
  if (type === 'time') return 'primary'
  return 'info'
}

function abxStatusTag(code) {
  if (code === 'executing') return 'warning'
  if (code === 'completed') return 'success'
  return 'info'
}

onMounted(() => {
  loadData()
})

watch(() => route.query.inHospitalNo, () => {
  loadData()
})
</script>

<style scoped>
.pkpd-page {
  min-height: 100vh;
  background: #fafaf9;
}

.page-body {
  padding: 16px 24px 32px;
  max-width: 1920px;
  margin: 0 auto;
}
.warn-bar {
  padding: 16px 24px;
}

/* 卡片通用 */
.card {
  background: #fff;
  border-radius: 10px;
  padding: 16px 18px;
  box-shadow: 0 1px 4px rgba(28, 25, 23, 0.04);
  border: 1px solid #e7e5e4;
}
.block-title {
  font-size: 15px;
  font-weight: 600;
  color: #292524;
  margin-bottom: 12px;
  display: flex;
  align-items: center;
}
.dot { display: inline-block; width: 8px; height: 8px; border-radius: 50%; margin-right: 8px; }
.dot-blue { background: linear-gradient(180deg, #0d9488, #2dd4bf); }
.dot-cyan { background: #0891b2; }
.dot-red { background: #dc2626; }
.dot-purple { background: #8b6fd8; }
.dot-green { background: #16a34a; }
.dot-orange { background: #d97706; }
.dot-gray { background: #78716c; }

/* 患者信息横条 */
.patient-bar {
  display: flex;
  align-items: stretch;
  flex-wrap: wrap;
  padding: 12px 0;
  margin-bottom: 16px;
}
.p-cell {
  flex: 0 0 auto;
  min-width: 100px;
  padding: 4px 16px;
  border-right: 1px solid #f5f5f4;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.p-cell-grow { flex: 1 1 auto; border-right: none; }
.p-cell-group {
  display: flex;
  align-items: stretch;
  background: #fafaf9;
  border-radius: 8px;
  padding: 4px 8px;
}
.p-cell-group .p-cell {
  border-right: 1px solid #e7e5e4;
  padding: 4px 12px;
  min-width: 90px;
}
.p-cell-group .p-cell:last-child { border-right: none; }
.p-cell label { font-size: 12px; color: #78716c; }
.p-cell b { font-size: 14px; color: #44403c; }
.warn-text { color: #d97706; font-weight: 600; }

.flag-list { display: flex; flex-wrap: wrap; gap: 4px; }
.flag-tag { margin-right: 4px; }

/* 主体三栏 */
.main-grid {
  display: grid;
  grid-template-columns: 1.1fr 1.3fr 1fr;
  gap: 16px;
  margin-bottom: 16px;
}
.col-left, .col-mid, .col-right {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.col-left .card, .col-mid .card, .col-right .card { flex: 0 0 auto; }
@media (max-width: 1500px) {
  .main-grid { grid-template-columns: 1fr 1fr; }
  .col-right { grid-column: 1 / -1; flex-direction: row; }
  .col-right .card { flex: 1; }
}
@media (max-width: 1100px) {
  .main-grid { grid-template-columns: 1fr; }
  .col-right { flex-direction: column; }
}

/* 肾功能评估 */
.renal-big {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-bottom: 12px;
  padding: 12px;
  background: linear-gradient(135deg, #f0fdfa, #ccfbf1);
  border-radius: 8px;
}
.renal-num { font-size: 36px; font-weight: 700; line-height: 1; }
.renal-unit { font-size: 14px; color: #44403c; }
.renal-label { font-size: 12px; color: #78716c; margin-left: auto; align-self: flex-end; }
.renal-G1, .renal-G2 { color: #16a34a; }
.renal-G3a, .renal-G3b { color: #d97706; }
.renal-G4, .renal-G5 { color: #dc2626; }

.renal-info-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  margin-bottom: 12px;
}
.renal-info-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 8px;
  background: #fafaf9;
  border-radius: 6px;
}
.ri-label { font-size: 11px; color: #78716c; }
.ri-value { font-size: 13px; font-weight: 600; color: #44403c; }

.renal-chart { width: 100%; height: 140px; }
.empty-chart { text-align: center; color: #a8a29e; font-size: 12px; padding: 20px 0; }

/* 营养状态 */
.nutrition-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  margin-bottom: 10px;
}
.nutrition-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 8px;
  background: #fafaf9;
  border-radius: 6px;
}
.ni-label { font-size: 11px; color: #78716c; }
.ni-value { font-size: 13px; font-weight: 600; color: #44403c; }

.obese-alert, .low-weight-alert {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  padding: 8px 10px;
  border-radius: 6px;
  font-size: 12px;
  margin-top: 8px;
}
.obese-alert { background: #fef3c7; color: #92400e; }
.low-weight-alert { background: #f0fdfa; color: #0d9488; }

/* 肝功能 */
.liver-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  margin-bottom: 10px;
}
.liver-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 8px;
  background: #fafaf9;
  border-radius: 6px;
}
.li-label { font-size: 11px; color: #78716c; }
.li-value { font-size: 13px; font-weight: 600; color: #44403c; }
.liver-note {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  padding: 8px 10px;
  background: #fef3c7;
  border-radius: 6px;
  font-size: 12px;
  color: #92400e;
}

/* 抗菌药 PK/PD 分析 */
.abx-pkpd-list { display: flex; flex-direction: column; gap: 12px; }
.abx-pkpd-item {
  border: 1px solid #e7e5e4;
  border-radius: 8px;
  padding: 12px;
  background: #fafaf9;
}
.abx-pkpd-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  flex-wrap: wrap;
}
.abx-pkpd-name { font-size: 14px; font-weight: 600; color: #292524; }
.abx-pkpd-detail { display: flex; flex-direction: column; gap: 6px; }
.abx-pkpd-row {
  display: flex;
  font-size: 12px;
  line-height: 1.5;
}
.abx-pkpd-k {
  flex: 0 0 80px;
  color: #78716c;
}
.abx-pkpd-v {
  flex: 1;
  color: #44403c;
}
.abx-pkpd-remark {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  margin-top: 8px;
  padding: 6px 8px;
  background: #f0fdfa;
  border-radius: 4px;
  font-size: 12px;
  color: #0d9488;
}

/* 剂量优化建议 */
.rec-list { display: flex; flex-direction: column; gap: 12px; }
.rec-item {
  border: 1px solid #e7e5e4;
  border-radius: 8px;
  padding: 12px;
  background: #fafaf9;
}
.rec-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.rec-name { font-size: 14px; font-weight: 600; color: #292524; }
.rec-dose {
  font-size: 13px;
  font-weight: 600;
  color: #dc2626;
  margin-bottom: 6px;
  padding: 6px 8px;
  background: #fee2e2;
  border-radius: 4px;
}
.rec-reason {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  font-size: 12px;
  color: #44403c;
  line-height: 1.5;
}

.renal-dose-table { margin-top: 10px; }
.r-table-title { font-size: 12px; font-weight: 600; color: #292524; margin-bottom: 6px; }
.r-table { width: 100%; border-collapse: collapse; font-size: 11px; }
.r-table th, .r-table td {
  border: 1px solid #e7e5e4;
  padding: 4px 6px;
  text-align: left;
}
.r-table th { background: #fafaf9; color: #44403c; font-weight: 600; }
.r-table td { color: #44403c; }

/* TDM 目标值 */
.tdm-list { display: flex; flex-direction: column; gap: 12px; }
.tdm-item {
  border: 1px solid #e7e5e4;
  border-radius: 8px;
  padding: 12px;
  background: #fafaf9;
}
.tdm-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.tdm-name { font-size: 14px; font-weight: 600; color: #292524; }
.tdm-targets { display: flex; flex-direction: column; gap: 4px; margin-bottom: 8px; }
.tdm-target {
  display: flex;
  font-size: 12px;
}
.tdm-label { flex: 0 0 70px; color: #78716c; }
.tdm-value { flex: 1; color: #44403c; font-weight: 500; }
.tdm-timing {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #44403c;
  padding-top: 6px;
  border-top: 1px dashed #e7e5e4;
}
.tdm-empty {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  padding: 10px;
  background: #f5f5f4;
  border-radius: 6px;
  font-size: 12px;
  color: #78716c;
}

/* 药物相互作用 */
.interaction-bar { margin-bottom: 0; }
.interaction-list { display: flex; flex-direction: column; gap: 8px; }
.interaction-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 10px 12px;
  background: #fee2e2;
  border-radius: 6px;
  font-size: 13px;
  color: #dc2626;
  line-height: 1.5;
}
</style>
