<template>
  <div class="apache2-overview">
    <!-- 顶部筛选 -->
    <div class="filter-bar">
      <span class="page-title">APACHE II 评分总览</span>
      <div class="filters">
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          value-format="YYYY-MM-DD"
          :clearable="false"
          style="width: 260px"
        />
        <el-button type="primary" @click="loadData">
          <el-icon><Search /></el-icon> 查询
        </el-button>
        <el-button type="warning" :loading="autoGenerating" @click="manualAutoGenerate">
          自动补全在科患者评分
        </el-button>
        <span class="depart-tag" v-if="departName">科室：{{ departName }}</span>
      </div>
    </div>

    <el-empty v-if="departCodeInvalid" :description="departCodeInvalidText" />

    <template v-else>
      <!-- 统计卡片 -->
      <div class="stat-cards">
        <div class="stat-card">
          <div class="stat-label">评分记录数</div>
          <div class="stat-value">{{ summary.totalCount }}</div>
          <div class="stat-sub">当前时间范围</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">平均总分</div>
          <div class="stat-value">{{ fmt2(summary.avgScore) }}</div>
          <div class="stat-sub">APACHE II 总分</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">平均死亡率</div>
          <div class="stat-value">{{ fmt2(summary.avgMortality) }}%</div>
          <div class="stat-sub">预测院内死亡率</div>
        </div>
        <div class="stat-card stat-card-warning">
          <div class="stat-label">高危患者数</div>
          <div class="stat-value">{{ summary.highRiskCount }}</div>
          <div class="stat-sub">总分 ≥ 20 分</div>
        </div>
      </div>

      <!-- 评分分布图表 -->
      <div class="chart-row">
        <div class="chart-box">
          <div class="chart-title">APACHE II 评分分布</div>
          <div ref="distributionChartRef" class="chart-container" v-show="summary.totalCount > 0"></div>
          <div v-if="!summary.totalCount" class="chart-empty">当前时间范围内暂无评分数据</div>
        </div>
      </div>

      <!-- 患者评分列表 -->
      <div class="table-box">
        <div class="table-title">患者评分列表（点击行展开详情）</div>
        <el-table
          ref="tableRef"
          v-loading="loading"
          :data="records"
          style="width: 100%"
          row-key="id"
          border
          @row-click="handleRowClick"
        >
          <el-table-column type="expand">
            <template #default="{ row }">
              <div class="detail-panel">
                <div class="detail-section">
                  <div class="detail-title">评分汇总</div>
                  <div class="detail-scores">
                    <div class="score-item"><span class="score-label">A 年龄分</span><span class="score-val">{{ row.ageScore }}</span></div>
                    <div class="score-item"><span class="score-label">B 慢性健康分</span><span class="score-val">{{ row.chronicScore }}</span></div>
                    <div class="score-item"><span class="score-label">C GCS分</span><span class="score-val">{{ row.gcsScore }}</span></div>
                    <div class="score-item"><span class="score-label">D 急性生理分</span><span class="score-val">{{ row.physiologyScore }}</span></div>
                    <div class="score-item score-total"><span class="score-label">总分</span><span class="score-val">{{ row.totalScore }}</span></div>
                    <div class="score-item score-mortality"><span class="score-label">预测死亡率</span><span class="score-val">{{ fmt2(row.mortalityRate) }}%</span></div>
                  </div>
                </div>
                <div class="detail-section">
                  <div class="detail-title">评分信息</div>
                  <div class="detail-info">
                    <div><span>评分来源：</span>{{ scoreTypeText(row.scoreType) }}</div>
                    <div><span>评分时间：</span>{{ row.scoreTime }}</div>
                    <div><span>疾病分类：</span>{{ diagnosisTypeText(row.diagnosisType) }}</div>
                    <div><span>创建人：</span>{{ row.createBy || '—' }}</div>
                  </div>
                </div>
                <div class="detail-actions">
                  <el-button size="small" type="primary" @click.stop="goToScorePage(row)">
                    查看/编辑评分
                  </el-button>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="patientName" label="患者姓名" width="100" />
          <el-table-column prop="inHospitalNo" label="住院号" width="140" />
          <el-table-column prop="scoreType" label="评分来源" width="100">
            <template #default="{ row }">{{ scoreTypeText(row.scoreType) }}</template>
          </el-table-column>
          <el-table-column prop="ageScore" label="A年龄" width="70" align="center" />
          <el-table-column prop="chronicScore" label="B慢性" width="70" align="center" />
          <el-table-column prop="gcsScore" label="C GCS" width="70" align="center" />
          <el-table-column prop="physiologyScore" label="D生理" width="70" align="center" />
          <el-table-column prop="totalScore" label="总分" width="80" align="center">
            <template #default="{ row }">
              <span :class="getTotalScoreClass(row.totalScore)">{{ row.totalScore }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="mortalityRate" label="死亡率" width="90" align="center">
            <template #default="{ row }">{{ fmt2(row.mortalityRate) }}%</template>
          </el-table-column>
          <el-table-column prop="scoreTime" label="评分时间" width="170" />
        </el-table>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import request from '../api/request'
import { appendExternalContext } from '../utils/external'
import * as echarts from '../utils/echarts'

const route = useRoute()
const router = useRouter()
// ICU 外链模板未被替换的占位符（如 ${departCode}）会原样带进 query，必须按“无效参数”处理，
// 否则会以一个不存在的科室去查询，表面“成功”但永远返回空数据。
const RAW_PLACEHOLDER = /\$\{[^}]*\}/
const pickQuery = (v) => {
  const s = String(v == null ? '' : v).trim()
  return s && !RAW_PLACEHOLDER.test(s) ? s : ''
}
const departCode = ref(pickQuery(route.query.departCode))
const departName = ref(pickQuery(route.query.departName))
const departCodeInvalid = computed(() => !departCode.value)
const departCodeInvalidText = computed(() => {
  if (RAW_PLACEHOLDER.test(String(route.query.departCode || ''))) {
    return '外链科室参数未被 ICU 系统替换（仍为 ${departCode}），请在 ICU 外链配置中确认已传入科室编码'
  }
  return '缺少科室权限参数，请通过外链访问'
})

const dateRange = ref([])
const records = ref([])
const loading = ref(false)
const tableRef = ref(null)
const summary = reactive({
  totalCount: 0,
  avgScore: 0,
  avgMortality: 0,
  highRiskCount: 0,
  scoreDistribution: {}
})

const distributionChartRef = ref(null)
let distributionChart = null

/** 本地时区日期 yyyy-MM-dd：不能用 toISOString()（UTC 会整体前移一天，导致当天记录被排除） */
function fmtDate(d) {
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

onMounted(() => {
  const today = new Date()
  dateRange.value = [fmtDate(new Date(today.getFullYear(), today.getMonth(), 1)), fmtDate(today)]
  window.addEventListener('resize', handleResize)
  if (!departCodeInvalid.value) {
    loadData()
  }
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  if (distributionChart) {
    distributionChart.dispose()
    distributionChart = null
  }
})

async function loadData() {
  if (!dateRange.value || dateRange.value.length < 2) {
    ElMessage.warning('请选择时间范围')
    return
  }
  loading.value = true
  try {
    // 注意：request 响应拦截器已把后端 Result 信封 { code, message, data } 解包，
    // 这里拿到的就是 Result.data 本身，不能再判断 res.code（历史 bug：恒不等 0，导致永远提示“加载失败”）
    const data = (await request.get('/apache2/overview', {
      params: {
        departCode: departCode.value,
        startTime: dateRange.value[0] + ' 00:00:00',
        endTime: dateRange.value[1] + ' 23:59:59'
      }
    })) || {}
    summary.totalCount = data.totalCount || 0
    summary.avgScore = data.avgScore || 0
    summary.avgMortality = data.avgMortality || 0
    summary.highRiskCount = data.highRiskCount || 0
    summary.scoreDistribution = data.scoreDistribution || {}
    records.value = data.records || []
    await nextTick()
    renderDistributionChart()
  } catch (e) {
    // 全局拦截器已统一提示，这里只留诊断日志
    console.warn('APACHE II 总览加载失败: ', e && e.message)
  } finally {
    loading.value = false
  }
}

const autoGenerating = ref(false)
// 手动触发：为当前科室“在科且入科超24h、尚无评分记录”的患者自动生成评分（幂等，可重复执行）
async function manualAutoGenerate() {
  try {
    await ElMessageBox.confirm(
      '将为当前科室所有「在科且入科超过 24 小时、尚无评分记录」的患者生成一份 APACHE II 自动评分；已有记录的患者会自动跳过，可安全重复执行。是否继续？',
      '自动生成在科患者评分',
      { confirmButtonText: '开始生成', cancelButtonText: '取消', type: 'warning' }
    )
  } catch (action) {
    return // 用户取消
  }
  autoGenerating.value = true
  try {
    const res = (await request.post('/apache2/auto-generate', null, {
      params: { departCode: departCode.value || '', overHours: 24 }
    })) || {}
    ElMessage.success(`扫描 ${res.scanned || 0} 人，新增 ${res.created || 0} 份，跳过 ${res.skipped || 0} 人，失败 ${res.failed || 0} 人`)
    loadData()
  } catch (e) {
    console.warn('自动生成失败: ', e && e.message)
  } finally {
    autoGenerating.value = false
  }
}

function renderDistributionChart() {
  if (!distributionChartRef.value) return
  if (!distributionChart) {
    distributionChart = echarts.init(distributionChartRef.value)
  }
  const dist = summary.scoreDistribution || {}
  const categories = Object.keys(dist)
  const values = Object.values(dist).map((v) => Number(v) || 0)
  if (!categories.length) {
    distributionChart.clear()
    return
  }
  distributionChart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 50, right: 20, top: 30, bottom: 30 },
    xAxis: { type: 'category', data: categories, axisLabel: { color: '#666' } },
    yAxis: { type: 'value', name: '人数', axisLabel: { color: '#666' } },
    series: [{
      type: 'bar',
      data: values,
      itemStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: '#409eff' },
          { offset: 1, color: '#79bbff' }
        ]),
        borderRadius: [4, 4, 0, 0]
      },
      label: { show: true, position: 'top', color: '#666', fontSize: 12 }
    }]
  }, true)
  // 容器从 v-show 隐藏恢复显示后尺寸可能为 0，主动校正一次
  distributionChart.resize()
}

/** 窗口尺寸变化时自适应，避免图表被拉伸变形 */
function handleResize() {
  if (distributionChart) {
    distributionChart.resize()
  }
}

/** 点击行展开/收起明细（展开列自身已有点击处理，跳过以免重复切换） */
function handleRowClick(row, column) {
  if (column && column.type === 'expand') return
  if (tableRef.value) {
    tableRef.value.toggleRowExpansion(row)
  }
}

/** 数值统一保留两位小数（空值/非数字按 0.00 显示） */
function fmt2(v) {
  const n = Number(v)
  return isNaN(n) ? '0.00' : n.toFixed(2)
}

// 与评分页保持同一口径：auto/daily=自动评分、custom=手工评分
// （admission/24h/48h 为历史遗留取值，保留原样展示；reviewed 从未落库，已移除）
function scoreTypeText(type) {
  const map = {
    auto: '自动评分',
    daily: '自动评分',
    custom: '手工评分',
    admission: '入科时',
    '24h': '24小时',
    '48h': '48小时'
  }
  return map[type] || type || '—'
}

function diagnosisTypeText(type) {
  const map = { nonoperative: '非手术类', operative: '手术类', none: '以上都不是' }
  return map[type] || type || '—'
}

function getTotalScoreClass(score) {
  if (score >= 30) return 'score-danger'
  if (score >= 20) return 'score-warning'
  if (score >= 10) return 'score-info'
  return 'score-normal'
}

function goToScorePage(row) {
  const url = `/page/apache2-score?inHospitalNo=${encodeURIComponent(row.inHospitalNo || '')}` +
    `&patientName=${encodeURIComponent(row.patientName || '')}` +
    `&departCode=${encodeURIComponent(departCode.value)}` +
    `&recordId=${encodeURIComponent(row.id)}`
  // 当前页面跳转，sessionStorage 上下文自然继承
  router.push(appendExternalContext(url))
}
</script>

<style scoped>
.apache2-overview {
  padding: 16px;
  background: #f5f7fa;
  min-height: 100vh;
}
.filter-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  background: #fff;
  padding: 14px 20px;
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.04);
}
.page-title {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}
.filters {
  display: flex;
  align-items: center;
  gap: 12px;
}
.depart-tag {
  color: #909399;
  font-size: 13px;
}
.stat-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 16px;
}
.stat-card {
  background: #fff;
  padding: 20px;
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.04);
  border-left: 4px solid #409eff;
}
.stat-card-warning {
  border-left-color: #f56c6c;
}
.stat-label {
  font-size: 13px;
  color: #909399;
  margin-bottom: 8px;
}
.stat-value {
  font-size: 32px;
  font-weight: 700;
  color: #303133;
  line-height: 1.2;
}
.stat-card-warning .stat-value {
  color: #f56c6c;
}
.stat-sub {
  font-size: 12px;
  color: #c0c4cc;
  margin-top: 6px;
}
.chart-row {
  display: grid;
  grid-template-columns: 1fr;
  gap: 16px;
  margin-bottom: 16px;
}
.chart-box {
  background: #fff;
  padding: 16px 20px;
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.04);
}
.chart-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 12px;
}
.chart-container {
  height: 280px;
  width: 100%;
}
.chart-empty {
  height: 280px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #909399;
  font-size: 13px;
  background: #fafafa;
  border-radius: 4px;
}
:deep(.el-table__row) {
  cursor: pointer;
}
.table-box {
  background: #fff;
  padding: 16px 20px;
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.04);
}
.table-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 12px;
}
.detail-panel {
  padding: 16px;
  background: #fafafa;
  border-radius: 6px;
}
.detail-section {
  margin-bottom: 16px;
}
.detail-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 10px;
  padding-left: 8px;
  border-left: 3px solid #409eff;
}
.detail-scores {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 12px;
}
.score-item {
  background: #fff;
  padding: 12px;
  border-radius: 6px;
  text-align: center;
  border: 1px solid #ebeef5;
}
.score-label {
  display: block;
  font-size: 12px;
  color: #909399;
  margin-bottom: 6px;
}
.score-val {
  font-size: 22px;
  font-weight: 700;
  color: #303133;
}
.score-total {
  background: #ecf5ff;
  border-color: #409eff;
}
.score-total .score-val {
  color: #409eff;
}
.score-mortality {
  background: #fef0f0;
  border-color: #f56c6c;
}
.score-mortality .score-val {
  color: #f56c6c;
}
.detail-info {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px;
  font-size: 13px;
  color: #606266;
}
.detail-info span {
  color: #909399;
}
.detail-actions {
  text-align: right;
  margin-top: 12px;
}
.score-danger {
  color: #f56c6c;
  font-weight: 700;
}
.score-warning {
  color: #e6a23c;
  font-weight: 700;
}
.score-info {
  color: #409eff;
  font-weight: 600;
}
.score-normal {
  color: #67c23a;
  font-weight: 600;
}
</style>
