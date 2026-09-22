<template>
  <div class="ddd-patients abx-theme">
    <div class="filter-bar">
      <div class="filter-left">
        <el-button @click="goBack">
          <el-icon><ArrowLeft /></el-icon> 返回总览
        </el-button>
        <el-select v-model="selectedDepartCode" placeholder="选择科室" style="width: 180px" popper-class="abx-popper" @change="loadData">
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
          popper-class="abx-popper"
          @change="loadData"
        />
        <el-button type="primary" :loading="loading" @click="loadData">
          <el-icon><Refresh /></el-icon> 刷新
        </el-button>
      </div>
      <div class="filter-right">
        <el-input v-model="searchKeyword" placeholder="搜索患者姓名/住院号" clearable style="width: 220px;" @input="filterPatients" />
      </div>
    </div>

    <!-- 统计摘要 -->
    <div class="summary-bar">
      <div class="summary-item">
        <span class="label">使用抗菌药物患者</span>
        <span class="value">{{ filteredPatients.length }}</span>
        <span class="unit">人</span>
      </div>
      <div class="summary-item">
        <span class="label">总 DDDs</span>
        <span class="value">{{ totalDdds }}</span>
      </div>
      <div class="summary-item">
        <span class="label">涉及药物种类</span>
        <span class="value">{{ drugKindCount }}</span>
        <span class="unit">种</span>
      </div>
      <div class="summary-item">
        <span class="label">统计周期</span>
        <span class="value text">{{ dateRangeText }}</span>
      </div>
    </div>

    <!-- 患者列表 -->
    <div class="patient-list">
      <div v-for="patient in filteredPatients" :key="patient.inHospitalNo || patient.patientId" class="patient-card" @click="toggleExpand(patient)">
        <div class="patient-header">
          <div class="patient-info">
            <div class="patient-top-row">
              <span class="patient-name">{{ patient.name || '--' }}</span>
              <span class="patient-gender-age">{{ patient.gender || '--' }} / {{ patient.age || '--' }}岁</span>
              <el-tag size="small" :type="patient.inDepart ? 'success' : 'info'">
                {{ patient.inDepart ? '在科' : '已出科' }}
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
              <span class="stat-value">{{ patient.stayDays || 0 }}</span>
            </div>
            <div class="stat">
              <span class="stat-label">用药种类</span>
              <span class="stat-value">{{ patient.drugKindCount || 0 }}</span>
            </div>
            <div class="stat highlight">
              <span class="stat-label">总 DDDs</span>
              <span class="stat-value">{{ patient.totalDdds || 0 }}</span>
            </div>
            <el-icon class="expand-icon" :class="{ expanded: expandedIds.has(patient.inHospitalNo || patient.patientId) }">
              <ArrowDown />
            </el-icon>
          </div>
        </div>

        <div class="patient-main-drugs">
          <span class="label">主要用药：</span>
          <span class="drugs">{{ patient.mainDrugs || '--' }}</span>
        </div>

        <!-- 展开的用药明细 -->
        <div v-if="expandedIds.has(patient.inHospitalNo || patient.patientId)" class="drug-detail">
          <div class="detail-title">抗菌药物使用明细</div>
          <el-table :data="patient.drugUsages" stripe size="small" max-height="300">
            <el-table-column prop="drugName" label="药品名称" min-width="140" />
            <el-table-column prop="drugClass" label="分类" width="100" />
            <el-table-column prop="manageLevel" label="管理级别" width="90">
              <template #default="{ row }">
                <el-tag :type="row.manageLevel === '特殊' ? 'danger' : row.manageLevel === '限制' ? 'warning' : 'success'" size="small">
                  {{ row.manageLevel }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="freq" label="频次" width="70" />
            <el-table-column prop="method" label="给药方式" width="110" />
            <el-table-column prop="startTime" label="开始时间" width="150" />
            <el-table-column prop="endTime" label="结束时间" width="150" />
            <el-table-column prop="useDays" label="使用天数" width="80" align="right" />
            <el-table-column prop="singleDose" label="单次剂量(g)" width="100" align="right" />
            <el-table-column prop="totalDose" label="总剂量(g)" width="100" align="right" />
            <el-table-column prop="dddValue" label="DDD值" width="80" align="right" />
            <el-table-column prop="ddds" label="DDDs" width="90" align="right">
              <template #default="{ row }">
                <span class="highlight">{{ row.ddds }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="openStaffName" label="开立医生" width="90" />
          </el-table>

          <div class="detail-actions">
            <el-button size="small" type="primary" @click.stop="goDecision(patient)">
              <el-icon><Document /></el-icon> 经验性抗感染决策
            </el-button>
            <el-button size="small" type="success" @click.stop="goPkpd(patient)">
              <el-icon><DataAnalysis /></el-icon> PK/PD剂量优化
            </el-button>
          </div>
        </div>
      </div>

      <el-empty v-if="filteredPatients.length === 0" description="暂无数据" />
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft, Refresh, ArrowDown, Document, DataAnalysis } from '@element-plus/icons-vue'
import request from '../api/request'
import '../styles/abx-theme.css'

const router = useRouter()
const loading = ref(false)
const patients = ref([])
const searchKeyword = ref('')
const dateRange = ref([])
const departments = ref([])
const selectedDepartCode = ref('20070131')
const expandedIds = reactive(new Set())

const filteredPatients = computed(() => {
  if (!searchKeyword.value) return patients.value
  const kw = searchKeyword.value.toLowerCase()
  return patients.value.filter(p =>
    (p.name || '').toLowerCase().includes(kw) ||
    (p.inHospitalNo || '').includes(kw)
  )
})

const totalDdds = computed(() => {
  return patients.value.reduce((sum, p) => sum + (Number(p.totalDdds) || 0), 0).toFixed(2)
})

const drugKindCount = computed(() => {
  const set = new Set()
  patients.value.forEach(p => (p.drugUsages || []).forEach(d => set.add(d.drugName)))
  return set.size
})

const dateRangeText = computed(() => {
  if (!dateRange.value || dateRange.value.length < 2) return '--'
  return `${dateRange.value[0]} ~ ${dateRange.value[1]}`
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
    const res = await request.get('/antibiotic/ddd/patients', { params })
    patients.value = res || []
  } catch (e) {
    console.warn('数据加载失败: ', e.message || e)
  } finally {
    loading.value = false
  }
}

async function loadDepartments() {
  try {
    const res = await request.get('/antibiotic/ddd/departments')
    departments.value = res || []
  } catch (e) {
    console.error('加载科室列表失败', e)
  }
}

function toggleExpand(patient) {
  const key = patient.inHospitalNo || patient.patientId
  if (expandedIds.has(key)) {
    expandedIds.delete(key)
  } else {
    expandedIds.add(key)
  }
}

function formatTime(timeStr) {
  if (!timeStr) return '--'
  // 支持 "2026-09-01 08:00:00" 和 "2026-09-01T08:00:00" 格式
  const t = timeStr.replace('T', ' ')
  return t.substring(0, 16) // 只显示到分钟
}

function filterPatients() {
  // computed 自动处理
}

function goBack() {
  router.push('/page/abx-ddd')
}

function goDecision(patient) {
  router.push({ path: '/page/abx-decision', query: { patientId: patient.patientId, inHospitalNo: patient.inHospitalNo } })
}

function goPkpd(patient) {
  router.push({ path: '/page/abx-pkpd', query: { patientId: patient.patientId, inHospitalNo: patient.inHospitalNo } })
}

onMounted(async () => {
  initDefaultDate()
  await loadDepartments()
  loadData()
})
</script>

<style scoped>
.ddd-patients {
  padding: 16px;
  background: #fafaf9;
  min-height: 100vh;
}

.filter-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  padding: 12px 16px;
  background: #fff;
  border: 1px solid #e7e5e4;
  border-radius: 12px;
  box-shadow: 0 1px 4px rgba(28,25,23,0.04);
}

.filter-left, .filter-right {
  display: flex;
  gap: 12px;
  align-items: center;
}

.summary-bar {
  display: flex;
  gap: 24px;
  padding: 16px 20px;
  background: #fff;
  border: 1px solid #e7e5e4;
  border-radius: 12px;
  margin-bottom: 16px;
  box-shadow: 0 1px 4px rgba(28,25,23,0.04);
}

.summary-item {
  display: flex;
  align-items: baseline;
  gap: 6px;
}

.summary-item .label {
  font-size: 13px;
  color: #78716c;
}

.summary-item .value {
  font-size: 22px;
  font-weight: 700;
  color: #292524;
}

.summary-item .value.text {
  font-size: 14px;
  font-weight: 400;
}

.summary-item .unit {
  font-size: 12px;
  color: #78716c;
}

.patient-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.patient-card {
  background: #fff;
  border: 1px solid #e7e5e4;
  border-radius: 12px;
  padding: 16px 20px;
  box-shadow: 0 1px 4px rgba(28,25,23,0.04);
  cursor: pointer;
  transition: box-shadow 0.2s;
}

.patient-card:hover {
  box-shadow: 0 2px 12px rgba(0,0,0,0.1);
}

.patient-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}

.patient-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex: 1;
  min-width: 0;
}

.patient-top-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.patient-bottom-row {
  display: flex;
  align-items: center;
  gap: 16px;
}

.patient-name {
  font-size: 16px;
  font-weight: 600;
  color: #292524;
}

.patient-gender-age {
  font-size: 13px;
  color: #44403c;
}

.patient-field {
  font-size: 12px;
  color: #78716c;
}

.patient-stats {
  display: flex;
  align-items: center;
  gap: 20px;
  flex-shrink: 0;
}

.stat {
  text-align: center;
}

.stat-label {
  display: block;
  font-size: 12px;
  color: #78716c;
  margin-bottom: 2px;
}

.stat-value {
  font-size: 16px;
  font-weight: 600;
  color: #44403c;
}

.expand-icon {
  font-size: 18px;
  color: #a8a29e;
  transition: transform 0.3s;
}

.expand-icon.expanded {
  transform: rotate(180deg);
}

.patient-main-drugs {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid #f5f5f4;
  font-size: 13px;
  color: #44403c;
}

.patient-main-drugs .label {
  color: #78716c;
}

.patient-main-drugs .drugs {
  color: #0d9488;
}

.drug-detail {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px dashed #d6d3d1;
}

.detail-title {
  font-size: 14px;
  font-weight: 600;
  color: #292524;
  margin-bottom: 12px;
}

</style>
