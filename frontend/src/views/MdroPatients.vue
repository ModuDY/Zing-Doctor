<template>
  <div class="mdro-patients">
    <div class="filter-bar">
      <div class="filter-left">
        <el-button @click="goBack">
          <el-icon><ArrowLeft /></el-icon> 返回总览
        </el-button>
        <el-select v-model="selectedDepartCode" placeholder="选择科室" style="width: 180px" @change="loadData">
          <el-option
            v-for="dept in departments"
            :key="dept.org_code"
            :label="dept.depart_name"
            :value="dept.org_code"
          />
        </el-select>
        <el-date-picker
          v-model="dateRange"
          type="monthrange"
          range-separator="至"
          start-placeholder="开始月份"
          end-placeholder="结束月份"
          value-format="YYYY-MM"
          :clearable="false"
          @change="loadData"
        />
        <el-button type="primary" @click="loadData" :loading="loading">
          <el-icon><Refresh /></el-icon> 刷新
        </el-button>
      </div>
      <div class="filter-right">
        <el-input v-model="searchKeyword" placeholder="搜索患者姓名/住院号" clearable style="width: 220px;" @input="filterPatients" />
        <el-checkbox v-model="onlyHighRisk" @change="filterPatients">仅看高风险细菌</el-checkbox>
      </div>
    </div>

    <!-- 统计摘要 -->
    <div class="summary-bar">
      <div class="summary-item">
        <span class="label">培养阳性患者</span>
        <span class="value">{{ filteredPatients.length }}</span>
        <span class="unit">人</span>
      </div>
      <div class="summary-item">
        <span class="label">检出细菌种类</span>
        <span class="value">{{ bacteriaSpeciesCount }}</span>
        <span class="unit">种</span>
      </div>
      <div class="summary-item danger">
        <span class="label">高风险细菌患者</span>
        <span class="value">{{ highRiskPatientCount }}</span>
        <span class="unit">人</span>
      </div>
      <div class="summary-item">
        <span class="label">统计周期</span>
        <span class="value text">{{ dateRangeText }}</span>
      </div>
    </div>

    <!-- 患者列表 -->
    <div class="patient-list" v-loading="loading">
      <div v-for="patient in filteredPatients" :key="patient.inHospitalNo" class="patient-card" :class="{ expanded: isExpanded(patient) }">
        <div class="patient-header" @click="toggleExpand(patient)">
          <div class="patient-info">
            <div class="patient-top-row">
              <span class="patient-name">{{ patient.name || '--' }}</span>
              <span class="patient-gender-age">{{ patient.gender || '--' }} / {{ patient.age || '--' }}岁</span>
              <el-tag size="small" :type="patient.isInDepart === 1 ? 'success' : 'info'">
                {{ patient.isInDepart === 1 ? '在科' : '已出科' }}
              </el-tag>
              <el-tag v-if="patient.hasHighRiskBacteria === 1" size="small" type="danger" effect="dark">
                高风险细菌
              </el-tag>
            </div>
            <div class="patient-bottom-row">
              <span class="patient-field">住院号: {{ patient.inHospitalNo || '--' }}</span>
              <span class="patient-field">{{ patient.department || '--' }} {{ patient.bedNo || '' }}</span>
              <span class="patient-field">入科: {{ formatTime(patient.inDepartTime) }}</span>
              <span class="patient-field">出科: {{ patient.outDepartTime ? formatTime(patient.outDepartTime) : '在科' }}</span>
            </div>
          </div>
          <div class="patient-stats">
            <div class="stat">
              <span class="stat-label">在科天数</span>
              <span class="stat-value">{{ patient.inDepartDays || 0 }}</span>
            </div>
            <div class="stat">
              <span class="stat-label">送检次数</span>
              <span class="stat-value">{{ patient.cultureCount || 0 }}</span>
            </div>
            <div class="stat success">
              <span class="stat-label">阳性次数</span>
              <span class="stat-value">{{ patient.positiveCount || 0 }}</span>
            </div>
            <div class="stat warning">
              <span class="stat-label">阳性率</span>
              <span class="stat-value">{{ patient.positiveRate ?? 0 }}%</span>
            </div>
            <div class="stat info">
              <span class="stat-label">细菌种类</span>
              <span class="stat-value">{{ patient.bacteriaSpeciesCount || 0 }}</span>
            </div>
            <div class="expand-icon">
              <el-icon :class="{ rotated: isExpanded(patient) }"><ArrowDown /></el-icon>
            </div>
          </div>
        </div>

        <!-- 展开内容：细菌明细 -->
        <div v-if="isExpanded(patient)" class="patient-detail">
          <div class="detail-header">
            <span class="detail-title">检出细菌明细</span>
            <div class="detail-actions">
              <el-button size="small" type="primary" @click.stop="goDecision(patient)">
                <el-icon><Document /></el-icon> 经验性治疗决策
              </el-button>
              <el-button size="small" type="success" @click.stop="goPkpd(patient)">
                <el-icon><FirstAidKit /></el-icon> PK/PD剂量优化
              </el-button>
            </div>
          </div>
          <div v-if="patient.highRiskBacteriaNames" class="high-risk-tip">
            <el-icon><Warning /></el-icon>
            <span>高风险细菌: {{ patient.highRiskBacteriaNames }}</span>
          </div>
          <el-table :data="patient.bacteriaList || []" size="small" border stripe>
            <el-table-column prop="bacteriaName" label="细菌名称" min-width="180">
              <template #default="{ row }">
                <span>{{ row.bacteriaName }}</span>
                <el-tag v-if="row.isHighRisk === 1" size="small" type="danger" effect="plain" style="margin-left: 8px;">高风险</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="bacteriaClassName" label="细菌分类" width="120" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="getClassTagType(row.bacteriaClass)">{{ row.bacteriaClassName || '其他' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="detectCount" label="检出次数" width="100" align="center" />
            <el-table-column prop="specimenTypes" label="标本类型" min-width="150" />
            <el-table-column prop="firstDetectTime" label="首次检出" width="170" align="center">
              <template #default="{ row }">{{ formatTime(row.firstDetectTime) }}</template>
            </el-table-column>
            <el-table-column prop="lastDetectTime" label="最近检出" width="170" align="center">
              <template #default="{ row }">{{ formatTime(row.lastDetectTime) }}</template>
            </el-table-column>
          </el-table>
        </div>
      </div>

      <el-empty v-if="!loading && filteredPatients.length === 0" description="暂无培养阳性患者数据" />
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Refresh, ArrowDown, Document, FirstAidKit, Warning } from '@element-plus/icons-vue'
import request from '../api/request'

const router = useRouter()
const loading = ref(false)
const patients = ref([])
const departments = ref([])
const selectedDepartCode = ref('20070131')
const dateRange = ref([])
const searchKeyword = ref('')
const onlyHighRisk = ref(false)
const expandedIds = ref(new Set())

const dateRangeText = computed(() => {
  if (!dateRange.value || dateRange.value.length < 2) return '--'
  return `${dateRange.value[0]} ~ ${dateRange.value[1]}`
})

const filteredPatients = computed(() => {
  let list = patients.value
  if (searchKeyword.value) {
    const kw = searchKeyword.value.toLowerCase()
    list = list.filter(p =>
      (p.name && p.name.toLowerCase().includes(kw)) ||
      (p.inHospitalNo && p.inHospitalNo.includes(kw))
    )
  }
  if (onlyHighRisk.value) {
    list = list.filter(p => p.hasHighRiskBacteria === 1)
  }
  return list
})

const bacteriaSpeciesCount = computed(() => {
  const set = new Set()
  patients.value.forEach(p => {
    (p.bacteriaList || []).forEach(b => set.add(b.bacteriaName))
  })
  return set.size
})

const highRiskPatientCount = computed(() => {
  return patients.value.filter(p => p.hasHighRiskBacteria === 1).length
})

function initDefaultDate() {
  const now = new Date()
  const current = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
  dateRange.value = [current, current]
}

function getTimeRange() {
  if (!dateRange.value || dateRange.value.length < 2) return null
  const [startMonth, endMonth] = dateRange.value
  const [sy, sm] = startMonth.split('-').map(Number)
  const [ey, em] = endMonth.split('-').map(Number)
  const startTime = `${sy}-${String(sm).padStart(2, '0')}-01 00:00:00`
  const endDate = new Date(ey, em, 1)
  const endTime = `${endDate.getFullYear()}-${String(endDate.getMonth() + 1).padStart(2, '0')}-01 00:00:00`
  return { startTime, endTime }
}

async function loadData() {
  const range = getTimeRange()
  if (!range) return
  loading.value = true
  try {
    const params = { ...range, departCode: selectedDepartCode.value || '' }
    const res = await request.get('/antibiotic/mdro/patients', { params })
    patients.value = res || []
    expandedIds.value.clear()
  } catch (e) {
    ElMessage.error('数据加载失败: ' + (e.message || e))
  } finally {
    loading.value = false
  }
}

async function loadDepartments() {
  try {
    const res = await request.get('/antibiotic/mdro/departments')
    departments.value = res || []
  } catch (e) {
    console.error('加载科室列表失败', e)
  }
}

function isExpanded(patient) {
  return expandedIds.value.has(patient.inHospitalNo)
}

function toggleExpand(patient) {
  const key = patient.inHospitalNo
  if (expandedIds.value.has(key)) {
    expandedIds.value.delete(key)
  } else {
    expandedIds.value.add(key)
  }
}

function filterPatients() {
  // computed 自动处理
}

function formatTime(time) {
  if (!time || time === 'null' || time === '') return '--'
  return time.substring(0, 16).replace('T', ' ')
}

function getClassTagType(bacteriaClass) {
  if (bacteriaClass === 'gram_positive') return 'success'
  if (bacteriaClass === 'gram_negative') return 'primary'
  if (bacteriaClass === 'fungi') return 'warning'
  return 'info'
}

function goBack() {
  router.push('/page/abx-mdro')
}

function goDecision(patient) {
  router.push({ path: '/page/abx-decision', query: { inHospitalNo: patient.inHospitalNo } })
}

function goPkpd(patient) {
  router.push({ path: '/page/abx-pkpd', query: { inHospitalNo: patient.inHospitalNo } })
}

onMounted(() => {
  initDefaultDate()
  loadDepartments()
  loadData()
})
</script>

<style scoped>
.mdro-patients {
  padding: 16px;
  background: #f5f7fa;
  min-height: 100vh;
}

.filter-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  flex-wrap: wrap;
  gap: 12px;
}

.filter-left, .filter-right {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
}

.summary-bar {
  display: flex;
  gap: 16px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.summary-item {
  background: #fff;
  border-radius: 8px;
  padding: 12px 20px;
  display: flex;
  align-items: baseline;
  gap: 8px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.06);
  border-left: 4px solid #409eff;
}

.summary-item.danger { border-left-color: #f56c6c; }

.summary-item .label {
  font-size: 13px;
  color: #909399;
}

.summary-item .value {
  font-size: 22px;
  font-weight: 600;
  color: #303133;
}

.summary-item .value.text {
  font-size: 14px;
  font-weight: 400;
}

.summary-item .unit {
  font-size: 12px;
  color: #c0c4cc;
}

.patient-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.patient-card {
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.06);
  overflow: hidden;
  transition: all 0.2s;
}

.patient-card.expanded {
  box-shadow: 0 4px 16px rgba(0,0,0,0.1);
}

.patient-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  cursor: pointer;
  transition: background 0.2s;
}

.patient-header:hover {
  background: #f5f7fa;
}

.patient-info {
  flex: 1;
  min-width: 0;
}

.patient-top-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
  flex-wrap: wrap;
}

.patient-name {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.patient-gender-age {
  font-size: 13px;
  color: #909399;
}

.patient-bottom-row {
  display: flex;
  gap: 20px;
  flex-wrap: wrap;
}

.patient-field {
  font-size: 12px;
  color: #606266;
}

.patient-stats {
  display: flex;
  align-items: center;
  gap: 24px;
  padding-left: 16px;
  border-left: 1px solid #ebeef5;
}

.stat {
  text-align: center;
  min-width: 60px;
}

.stat-label {
  display: block;
  font-size: 11px;
  color: #909399;
  margin-bottom: 4px;
}

.stat-value {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.stat.success .stat-value { color: #67c23a; }
.stat.warning .stat-value { color: #e6a23c; }
.stat.info .stat-value { color: #909399; }

.expand-icon {
  color: #c0c4cc;
  transition: transform 0.2s;
}

.expand-icon .rotated {
  transform: rotate(180deg);
}

.patient-detail {
  padding: 16px;
  border-top: 1px solid #ebeef5;
  background: #fafafa;
}

.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.detail-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.detail-actions {
  display: flex;
  gap: 8px;
}

.high-risk-tip {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  background: #fef0f0;
  border-radius: 4px;
  margin-bottom: 12px;
  font-size: 13px;
  color: #f56c6c;
}

@media (max-width: 1200px) {
  .patient-stats {
    gap: 16px;
  }
  .patient-bottom-row {
    gap: 12px;
  }
}
</style>
