<template>
  <div class="discharge-page">
    <!-- 无权限提示 -->
    <div v-if="!departCode" class="no-permission">
      <el-icon :size="48" color="#e6a23c"><Warning /></el-icon>
      <p class="no-perm-title">缺少科室权限参数</p>
      <p class="no-perm-desc">请通过外链访问，并在URL中携带 departCode 参数</p>
      <p class="no-perm-example">示例：/page/discharge-stats?departCode=20070131&extToken=xxx</p>
    </div>

    <template v-else>
      <!-- 顶部筛选栏 -->
      <div class="filter-bar">
        <div class="filter-item">
          <span class="filter-label">出科时间：</span>
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            :clearable="false"
            @change="loadData"
          />
        </div>
        <div class="filter-item">
          <el-button type="primary" @click="loadData">
            <el-icon><Search /></el-icon> 查询
          </el-button>
          <el-button type="success" @click="exportXlsx" :disabled="!patients.length">
            <el-icon><Download /></el-icon> 导出Excel
          </el-button>
        </div>
        <div class="filter-stat">
          科室：<span class="dept-name">{{ departName }}</span>
          <span class="divider">|</span>
          共 <span class="stat-num">{{ patients.length }}</span> 条出科记录
        </div>
      </div>

      <!-- 患者出科列表 -->
      <div class="table-wrap">
        <el-table :data="patients" border stripe style="width: 100%" :header-cell-style="{ background: '#f5f7fa', fontWeight: 600 }">
          <el-table-column type="index" label="序号" width="60" align="center" />
          <el-table-column prop="patient_name" label="患者姓名" width="100" align="center" />
          <el-table-column prop="bed_code" label="床号" width="80" align="center" />
          <el-table-column prop="in_hospital_no" label="住院号" width="150" align="center" />
          <el-table-column prop="diagnosis" label="入科诊断" width="240" show-overflow-tooltip />
          <el-table-column prop="in_depart_time" label="入科时间" width="180" align="center">
            <template #default="{ row }">{{ fmtTime(row.in_depart_time) }}</template>
          </el-table-column>
          <el-table-column prop="out_depart_time" label="出科时间" width="180" align="center">
            <template #default="{ row }">{{ fmtTime(row.out_depart_time) }}</template>
          </el-table-column>
          <el-table-column prop="out_hospital_time" label="出院时间" width="180" align="center">
            <template #default="{ row }">{{ fmtTime(row.out_hospital_time) }}</template>
          </el-table-column>
          <el-table-column prop="charge_doctor" label="主管医生" width="110" align="center" />
        </el-table>

        <div v-if="!patients.length && !loading" class="empty-tip">
          暂无出科记录
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Download, Warning } from '@element-plus/icons-vue'
import * as XLSX from 'xlsx'
import request from '../api/request'

const loading = ref(false)
const patients = ref([])
const departments = ref([])

// 从外链URL参数获取 departCode（权限控制：只能查当前科室）
const route = new URLSearchParams(window.location.search)
const departCode = ref(route.get('departCode') || '')
const departName = ref(departCode.value)  // 默认显示编码，加载科室列表后替换为名称

// 默认当月
const now = new Date()
const firstDay = new Date(now.getFullYear(), now.getMonth(), 1)
const lastDay = new Date(now.getFullYear(), now.getMonth() + 1, 0)
const dateRange = ref([
  `${firstDay.getFullYear()}-${String(firstDay.getMonth() + 1).padStart(2, '0')}-${String(firstDay.getDate()).padStart(2, '0')}`,
  `${lastDay.getFullYear()}-${String(lastDay.getMonth() + 1).padStart(2, '0')}-${String(lastDay.getDate()).padStart(2, '0')}`
])

function fmtTime(val) {
  if (!val) return '—'
  const s = String(val)
  // ISO格式 2026-09-08T06:15:00.000+00:00 → 2026-09-08 06:15
  return s.replace('T', ' ').substring(0, 16)
}

async function loadData() {
  if (!departCode.value) {
    ElMessage.warning('缺少科室权限参数')
    return
  }
  if (!dateRange.value || dateRange.value.length < 2) {
    ElMessage.warning('请选择出科时间范围')
    return
  }
  loading.value = true
  try {
    const startTime = `${dateRange.value[0]} 00:00:00`
    const endTime = `${dateRange.value[1]} 23:59:59`
    const data = await request.get('/handover/discharge-list', {
      params: { startTime, endTime, departCode: departCode.value }
    })
    patients.value = data || []
  } catch (e) {
    console.error('加载出科统计失败', e)
    ElMessage.error('加载失败: ' + (e.message || e))
  } finally {
    loading.value = false
  }
}

function exportXlsx() {
  if (!patients.value || patients.value.length === 0) {
    ElMessage.warning('暂无数据可导出')
    return
  }
  // 表头
  const headers = ['患者姓名', '床号', '住院号', '入科诊断', '入科时间', '出科时间', '出院时间', '主管医生']
  // 数据行
  const rows = patients.value.map(p => [
    p.patient_name || '',
    p.bed_code || '',
    p.in_hospital_no || '',
    p.diagnosis || '',
    fmtTime(p.in_depart_time),
    fmtTime(p.out_depart_time),
    fmtTime(p.out_hospital_time),
    p.charge_doctor || ''
  ])
  // 创建工作簿
  const wb = XLSX.utils.book_new()
  const ws = XLSX.utils.aoa_to_sheet([headers, ...rows])
  // 设置列宽
  ws['!cols'] = [
    {wch: 12}, {wch: 8}, {wch: 18}, {wch: 30},
    {wch: 18}, {wch: 18}, {wch: 18}, {wch: 12}
  ]
  XLSX.utils.book_append_sheet(wb, ws, '患者出科统计')
  // 导出文件
  const filename = `患者出科统计_${departName.value}_${dateRange.value[0].replace(/-/g, '')}_${dateRange.value[1].replace(/-/g, '')}.xlsx`
  XLSX.writeFile(wb, filename)
  ElMessage.success('导出成功')
}

/** 加载科室列表，根据 departCode 关联 org_code 查找 depart_name */
async function loadDepartments() {
  try {
    const data = await request.get('/handover/departments')
    if (data && Array.isArray(data)) {
      departments.value = data
      const dept = data.find(d => d.org_code === departCode.value)
      if (dept && dept.depart_name) {
        departName.value = dept.depart_name
      }
    }
  } catch (e) {
    console.error('加载科室列表失败', e)
  }
}

onMounted(async () => {
  if (departCode.value) {
    await loadDepartments()
    loadData()
  }
})
</script>

<style scoped>
.discharge-page {
  padding: 16px;
  background: #f0f2f5;
  min-height: 100vh;
}

.no-permission {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 120px 20px;
  text-align: center;
}

.no-perm-title {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
  margin: 16px 0 8px;
}

.no-perm-desc {
  font-size: 14px;
  color: #909399;
  margin: 0 0 8px;
}

.no-perm-example {
  font-size: 12px;
  color: #c0c4cc;
  font-family: monospace;
  background: #f5f7fa;
  padding: 8px 12px;
  border-radius: 4px;
  margin: 0;
}

.filter-bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 16px;
  background: #fff;
  padding: 16px 20px;
  border-radius: 8px;
  margin-bottom: 16px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
}

.filter-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.filter-label {
  font-size: 14px;
  color: #606266;
  white-space: nowrap;
}

.filter-stat {
  margin-left: auto;
  font-size: 14px;
  color: #909399;
}

.dept-name {
  color: #409eff;
  font-weight: 600;
}

.divider {
  margin: 0 12px;
  color: #dcdfe6;
}

.stat-num {
  color: #409eff;
  font-weight: 600;
  font-size: 18px;
  margin: 0 4px;
}

.table-wrap {
  background: #fff;
  border-radius: 8px;
  padding: 16px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
}

.empty-tip {
  text-align: center;
  padding: 60px 0;
  color: #909399;
  font-size: 14px;
}
</style>
